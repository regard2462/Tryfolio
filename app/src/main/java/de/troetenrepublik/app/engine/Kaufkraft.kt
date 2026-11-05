package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.YearMonth
import java.time.ZoneOffset

// Kaufkraft: eine Depotkurve um die Inflation bereinigt. Euro mit dem Harmonisierten Verbraucherpreisindex des
// Euroraums (EZB Data Portal, Datensatz HICP, seit Februar 2026 Nachfolger von ICP), Dollar mit dem US-Verbraucherpreisindex
// (CPI-U ohne Saisonbereinigung, FRED der Federal Reserve Bank of St. Louis, sonst die BLS-Schnittstelle).
// Monatswerte gelten zur Monatsmitte und werden dazwischen linear auf die Tage verteilt; nach dem letzten Monat bleibt
// dessen Wert stehen.
object Kaufkraft {

    private const val DAY = 86_400_000L

    const val EZB = "https://data-api.ecb.europa.eu/service/data/HICP/M.U2.N.000000.4D0.INX?format=csvdata&startPeriod=2000-01&detail=dataonly"
    const val FRED = "https://fred.stlouisfed.org/graph/fredgraph.csv?id=CPIAUCNS&cosd=2000-01-01"
    // Ohne Schluessel liefert die BLS-Schnittstelle die letzten drei Jahre.
    const val BLS = "https://api.bls.gov/publicAPI/v1/timeseries/data/CUUR0000SA0"

    // Ein Monatswert, m im Format 2026-09.
    @Serializable
    data class Monat(val m: String, val w: Double)

    // Gespeicherte Reihe mit dem Zeitpunkt des letzten erfolgreichen Abrufs; bls: letzter Abruf bei der BLS (0: nie),
    // fuer den Pflichtsatz mit Abrufdatum.
    @Serializable
    data class Reihe(val stand: Long = 0L, val monate: List<Monat> = emptyList(), val bls: Long = 0L)

    @Serializable
    data class Datei(val eur: Reihe = Reihe(), val usd: Reihe = Reihe())

    // Hoechstens einmal am Tag neu laden.
    fun veraltet(r: Reihe, jetzt: Long): Boolean = r.monate.isEmpty() || jetzt - r.stand >= DAY || jetzt < r.stand

    // Eine CSV-Zeile mit Feldern in Anfuehrungszeichen (Kommentare der EZB enthalten Kommas).
    private fun felder(zeile: String): List<String> {
        val aus = ArrayList<String>()
        val sb = StringBuilder()
        var inZitat = false
        var i = 0
        while (i < zeile.length) {
            val c = zeile[i]
            when {
                c == '"' && inZitat && i + 1 < zeile.length && zeile[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> inZitat = !inZitat
                c == ',' && !inZitat -> { aus += sb.toString(); sb.setLength(0) }
                else -> sb.append(c)
            }
            i++
        }
        aus += sb.toString()
        return aus
    }

    private fun gueltig(m: String): Boolean = Regex("""\d{4}-\d{2}""").matches(m)

    // CSV des EZB Data Portal: Spalten TIME_PERIOD (2026-09) und OBS_VALUE.
    fun ezbLesen(csv: String): List<Monat> {
        val zeilen = csv.lines().filter { it.isNotBlank() }
        if (zeilen.size < 2) return emptyList()
        val kopf = felder(zeilen[0]).map { it.trim() }
        val iZeit = kopf.indexOf("TIME_PERIOD")
        val iWert = kopf.indexOf("OBS_VALUE")
        if (iZeit < 0 || iWert < 0) return emptyList()
        return zeilen.drop(1).mapNotNull { z ->
            val f = felder(z)
            val m = f.getOrNull(iZeit)?.trim()?.takeIf(::gueltig) ?: return@mapNotNull null
            val w = f.getOrNull(iWert)?.trim()?.toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() } ?: return@mapNotNull null
            Monat(m, w)
        }.distinctBy { it.m }.sortedBy { it.m }
    }

    // CSV von FRED: observation_date (2026-09-01) und Wert; fehlende Werte stehen als Punkt da.
    fun fredLesen(csv: String): List<Monat> =
        csv.lines().drop(1).mapNotNull { z ->
            val f = z.split(',')
            val m = f.getOrNull(0)?.trim()?.take(7)?.takeIf(::gueltig) ?: return@mapNotNull null
            val w = f.getOrNull(1)?.trim()?.toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() } ?: return@mapNotNull null
            Monat(m, w)
        }.distinctBy { it.m }.sortedBy { it.m }

    // JSON der BLS: Results.series[0].data mit year, period (M01 bis M12, M13 = Jahresschnitt) und value.
    fun blsLesen(json: String): List<Monat> {
        val wurzel = runCatching { Json.parseToJsonElement(json) as? JsonObject }.getOrNull() ?: return emptyList()
        val reihe = ((wurzel["Results"] as? JsonObject)?.get("series") as? JsonArray)?.firstOrNull() as? JsonObject ?: return emptyList()
        return (reihe["data"] as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val jahr = (o["year"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            val periode = (o["period"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            if (!Regex("M(0[1-9]|1[0-2])").matches(periode)) return@mapNotNull null
            val w = (o["value"] as? JsonPrimitive)?.content?.toDoubleOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            Monat("$jahr-${periode.drop(1)}", w)
        }.distinctBy { it.m }.sortedBy { it.m }
    }

    // Neue Monatswerte ueber die alten legen (neue gewinnen), etwa wenn nur die letzten Jahre geladen wurden.
    fun zusammen(alt: List<Monat>, neu: List<Monat>): List<Monat> =
        (alt.associateBy { it.m } + neu.associateBy { it.m }).values.sortedBy { it.m }

    // Monatsmitte in ms (UTC).
    private fun mitte(m: String): Long {
        val ym = YearMonth.parse(m)
        return ym.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() + ym.lengthOfMonth() * DAY / 2
    }

    // Stuetzpunkte (Zeit, Indexwert), aelteste zuerst.
    class Index(monate: List<Monat>) {
        private val t = monate.map { mitte(it.m) }.toLongArray()
        private val w = monate.map { it.w }.toDoubleArray()

        // Beginn des ersten Monats in ms.
        private val beginn: Long = monate.firstOrNull()?.let { YearMonth.parse(it.m).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() } ?: 0L

        val leer: Boolean get() = t.isEmpty()

        // Indexwert am Zeitpunkt ms: vor der ersten Monatsmitte (im ersten Monat) dessen Wert, vor dem ersten Monat
        // null; zwischen zwei Monatsmitten linear; nach der letzten der letzte Wert.
        fun wert(ms: Long): Double? {
            if (t.isEmpty() || ms < beginn) return null
            if (ms <= t[0]) return w[0]
            if (ms >= t[t.size - 1]) return w[w.size - 1]
            var lo = 0
            var hi = t.size - 1
            while (hi - lo > 1) {
                val mid = (lo + hi) ushr 1
                if (t[mid] <= ms) lo = mid else hi = mid
            }
            val f = (ms - t[lo]).toDouble() / (t[hi] - t[lo]).toDouble()
            return w[lo] + (w[hi] - w[lo]) * f
        }
    }

    // Reale Kurve: jeder Wert geteilt durch die Preissteigerung seit dem ersten Punkt. Punkte vor dem Beginn des
    // Index fallen weg; null, wenn weniger als zwei bleiben.
    fun real(punkte: List<Pair<Long, Double>>, index: Index): List<Pair<Long, Double>>? {
        val mit = punkte.mapNotNull { (t, v) -> index.wert(t)?.let { Triple(t, v, it) } }
        if (mit.size < 2) return null
        val basis = mit.first().third
        return mit.map { (t, v, p) -> t to v * basis / p }
    }

    // Vergleich ueber einen Zeitraum, alles als Anteil (0,05 = 5 %).
    data class Vergleich(val nominal: Double, val real: Double, val inflation: Double)

    // Vom ersten bis zum letzten Punkt der Kurve punkte (Werte wie ein Index, etwa 100 = Start).
    fun vergleich(punkte: List<Pair<Long, Double>>, index: Index): Vergleich? {
        if (punkte.size < 2) return null
        val (t0, v0) = punkte.first()
        val (t1, v1) = punkte.last()
        val p0 = index.wert(t0) ?: return null
        val p1 = index.wert(t1) ?: return null
        if (!(v0 > 0) || !(p0 > 0)) return null
        val nominal = v1 / v0 - 1.0
        val inflation = p1 / p0 - 1.0
        return Vergleich(nominal, (1.0 + nominal) / (1.0 + inflation) - 1.0, inflation)
    }
}
