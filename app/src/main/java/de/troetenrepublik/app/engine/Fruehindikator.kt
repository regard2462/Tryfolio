package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.YearMonth

// Fruehindikator je Land: OECD Composite Leading Indicator (CLI, amplitudenbereinigt, monatlich, Langzeitmittel 100).
// Quelle 1: DBnomics-Spiegel der OECD (eine Abfrage fuer alle Laender, ganze Geschichte). Die OECD selbst sperrt
// Programme mit Cloudflare und wird nicht abgefragt. Quelle 2 als Rueckgriff: FRED (OECD-Reihen <ISO3>LOLITOAASTSAM),
// ebenfalls eine Abfrage fuer alle Laender. DBnomics hinkt zeitweise Monate hinterher, dann gewinnt je Land der neuere Monat.
// Konjunkturphase nach der OECD-Einteilung: ueber 100 und steigend Expansion, ueber 100 und fallend Slowdown, unter 100
// und fallend Downturn, unter 100 und steigend Recovery. Richtung: Vergleich mit dem Vormonat.
// Dazu der Economic Sentiment Indicator (ESI) der EU-Kommission ueber Eurostat (Langzeitmittel 100).
object Fruehindikator {
    enum class Phase { AUFSCHWUNG, ABSCHWAECHUNG, ABSCHWUNG, ERHOLUNG }

    // Monat als "JJJJ-MM"
    @Serializable
    data class Punkt(val monat: String, val wert: Double)

    // Land (alpha-3 beim CLI, alpha-2 beim ESI) -> letzte Monate, aelteste zuerst
    @Serializable
    data class Datensatz(val abgerufen: Long = 0L, val laender: Map<String, List<Punkt>> = emptyMap())

    const val GUELTIG_MS = 24 * 3_600_000L
    const val BEHALTEN = 6

    // Aeltere Werte als so viele Monate zaehlen nicht mehr als aktuell.
    const val MAX_ALTER_MONATE = 6

    const val FRED_ENDUNG = "LOLITOAASTSAM"

    // Laender, fuer die FRED aktuelle OECD-CLI-Reihen fuehrt (am 06.11.2026 mit Stand 09/2026 geprueft).
    val FRED_LAENDER = listOf("AUS", "BRA", "CAN", "CHN", "DEU", "ESP", "FRA", "GBR", "IDN", "IND", "ITA", "JPN", "KOR", "MEX", "TUR", "USA", "ZAF")

    const val DBNOMICS_ADRESSE = "https://api.db.nomics.world/v22/series/OECD/DSD_STES@DF_CLI?" +
        "dimensions=%7B%22MEASURE%22%3A%5B%22LI%22%5D%2C%22ADJUSTMENT%22%3A%5B%22AA%22%5D%2C%22FREQ%22%3A%5B%22M%22%5D%2C%22TRANSFORMATION%22%3A%5B%22IX%22%5D%7D" +
        "&observations=1&limit=1000&metadata=0&format=json"

    fun fredAdresse(): String = "https://fred.stlouisfed.org/graph/fredgraph.csv?id=" + FRED_LAENDER.joinToString(",") { it + FRED_ENDUNG }

    fun esiAdresse(von: YearMonth): String =
        "https://ec.europa.eu/eurostat/api/dissemination/statistics/1.0/data/ei_bssi_m_r2?format=JSON&lang=EN&indic=BS-ESI-I&s_adj=SA&sinceTimePeriod=$von"

    // ---- Phase

    fun phase(wert: Double, vorher: Double): Phase =
        if (wert > 100.0) (if (wert >= vorher) Phase.AUFSCHWUNG else Phase.ABSCHWAECHUNG)
        else (if (wert >= vorher) Phase.ERHOLUNG else Phase.ABSCHWUNG)

    fun phase(reihe: List<Punkt>?): Phase? {
        if (reihe == null || reihe.size < 2) return null
        return phase(reihe[reihe.size - 1].wert, reihe[reihe.size - 2].wert)
    }

    fun monat(p: Punkt): YearMonth? = runCatching { YearMonth.parse(p.monat) }.getOrNull()

    // Die Reihe, wenn ihr letzter Monat nicht zu alt ist.
    fun aktuell(reihe: List<Punkt>?, jetzt: YearMonth): List<Punkt>? {
        val m = reihe?.lastOrNull()?.let(::monat) ?: return null
        return reihe.takeIf { m >= jetzt.minusMonths(MAX_ALTER_MONATE.toLong()) }
    }

    // Phase eines Landes, nur mit aktuellen Werten.
    fun phase(d: Datensatz?, land: String, jetzt: YearMonth): Phase? = phase(aktuell(d?.laender?.get(land), jetzt))

    // Wie viele der Laender mit aktuellem Wert im Abschwung stehen (unter 100 und fallend).
    data class Anteil(val abschwung: Int, val mitDaten: Int)

    fun anteilAbschwung(d: Datensatz?, jetzt: YearMonth): Anteil {
        var n = 0; var ab = 0
        for (code in d?.laender?.keys.orEmpty()) {
            val p = phase(d, code, jetzt) ?: continue
            n++
            if (p == Phase.ABSCHWUNG) ab++
        }
        return Anteil(ab, n)
    }

    // ---- Zusammenfuehren und Alter

    // Je Land die Reihe mit dem neueren letzten Monat; bei Gleichstand die erste.
    fun zusammen(a: Map<String, List<Punkt>>, b: Map<String, List<Punkt>>): Map<String, List<Punkt>> {
        val aus = LinkedHashMap<String, List<Punkt>>()
        for (code in a.keys + b.keys) {
            val x = a[code]; val y = b[code]
            aus[code] = when {
                x == null -> y!!
                y == null -> x
                (y.lastOrNull()?.monat ?: "") > (x.lastOrNull()?.monat ?: "") -> y
                else -> x
            }
        }
        return aus
    }

    // Neuester Monat ueber alle Laender
    fun neuesterMonat(laender: Map<String, List<Punkt>>): YearMonth? = laender.values.mapNotNull { it.lastOrNull()?.let(::monat) }.maxOrNull()

    fun frisch(laender: Map<String, List<Punkt>>, jetzt: YearMonth): Boolean =
        neuesterMonat(laender)?.let { it >= jetzt.minusMonths(2) } == true

    fun veraltet(d: Datensatz?, jetzt: Long): Boolean = d == null || jetzt - d.abgerufen >= GUELTIG_MS || jetzt < d.abgerufen

    private val json = Json { ignoreUnknownKeys = true }

    fun schreiben(d: Datensatz): String = json.encodeToString(Datensatz.serializer(), d)
    fun lesenGespeichert(text: String): Datensatz? = runCatching { json.decodeFromString(Datensatz.serializer(), text) }.getOrNull()
        ?.takeIf { it.laender.isNotEmpty() }

    // ---- Lesen

    // DBnomics: {"series":{"docs":[{"dimensions":{"REF_AREA":"DEU"},"period":["2026-04",...],"value":[100.8,...]},...]}}.
    // gueltig: die Laenderkuerzel (alpha-3) der Karte; Gruppen wie G7 oder A5M fallen weg. Fehlende Werte ("NA") fallen weg.
    fun lesenDbnomics(text: String, gueltig: Set<String>): Map<String, List<Punkt>>? {
        val docs = runCatching { json.parseToJsonElement(text).jsonObject["series"]?.jsonObject?.get("docs")?.jsonArray }.getOrNull() ?: return null
        val aus = LinkedHashMap<String, List<Punkt>>()
        for (e in docs) {
            val o = e as? JsonObject ?: continue
            val code = (o["dimensions"] as? JsonObject)?.get("REF_AREA")?.jsonPrimitive?.content ?: continue
            if (code !in gueltig) continue
            val perioden = o["period"] as? JsonArray ?: continue
            val werte = o["value"] as? JsonArray ?: continue
            val reihe = perioden.indices.mapNotNull { i ->
                val m = (perioden[i] as? JsonPrimitive)?.content?.take(7) ?: return@mapNotNull null
                val w = (werte.getOrNull(i) as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() } ?: return@mapNotNull null
                Punkt(m, w)
            }.sortedBy { it.monat }.takeLast(BEHALTEN)
            if (reihe.isNotEmpty()) aus[code] = reihe
        }
        return aus.takeIf { it.isNotEmpty() }
    }

    // FRED-CSV mit mehreren Reihen: Kopf "observation_date,DEULOLITOAASTSAM,...", dann Monatsersten und Werte (leer = fehlt).
    fun lesenFred(csv: String): Map<String, List<Punkt>>? {
        val zeilen = csv.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (zeilen.size < 2) return null
        val kopf = zeilen[0].split(',').map { it.trim() }
        val codes = kopf.map { c -> if (c.endsWith(FRED_ENDUNG) && c.length == 3 + FRED_ENDUNG.length) c.take(3) else null }
        val spalten = HashMap<String, MutableList<Punkt>>()
        for (z in zeilen.drop(1)) {
            val f = z.split(',')
            val monat = f.getOrNull(0)?.trim()?.take(7)?.takeIf { it.length == 7 && it[4] == '-' } ?: continue
            for (i in 1 until minOf(f.size, codes.size)) {
                val c = codes[i] ?: continue
                val w = f[i].trim().toDoubleOrNull()?.takeIf { it.isFinite() } ?: continue
                spalten.getOrPut(c) { ArrayList() } += Punkt(monat, w)
            }
        }
        val aus = spalten.mapValues { (_, l) -> l.distinctBy { it.monat }.sortedBy { it.monat }.takeLast(BEHALTEN) }.filterValues { it.isNotEmpty() }
        return aus.takeIf { it.isNotEmpty() }
    }

    // Eurostat ESI (JSON-stat 2.0): Dimensionen id/size, Laender unter dimension.geo.category.index, Werte flach nach Index.
    // Ergebnis je Land (alpha-2, Griechenland EL -> GR); Gruppen wie EU27_2020 oder EA20 fallen weg.
    fun lesenEsi(text: String): Map<String, List<Punkt>>? {
        val w = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val ids = (w["id"] as? JsonArray)?.map { it.jsonPrimitive.content } ?: return null
        val groessen = (w["size"] as? JsonArray)?.map { it.jsonPrimitive.intOrNull ?: return null } ?: return null
        val dim = w["dimension"] as? JsonObject ?: return null
        val werte = w["value"] as? JsonObject ?: return null
        fun index(name: String): Map<String, Int>? =
            ((dim[name] as? JsonObject)?.get("category") as? JsonObject)?.get("index")?.let { it as? JsonObject }
                ?.mapValues { it.value.jsonPrimitive.intOrNull ?: return null }
        val geo = index("geo") ?: return null
        val zeit = index("time") ?: return null
        val gi = ids.indexOf("geo"); val ti = ids.indexOf("time")
        if (gi < 0 || ti < 0 || ids.size != groessen.size) return null
        val schritt = IntArray(ids.size)
        var s = 1
        for (i in ids.indices.reversed()) { schritt[i] = s; s *= groessen[i] }
        val monate = zeit.entries.sortedBy { it.value }
        val aus = LinkedHashMap<String, List<Punkt>>()
        for ((code, g) in geo) {
            val land = if (code == "EL") "GR" else code
            if (land.length != 2 || land.startsWith("EA") || land == "EU") continue
            val reihe = monate.mapNotNull { (m, t) ->
                val v = (werte[(g * schritt[gi] + t * schritt[ti]).toString()] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
                Punkt(m, v)
            }
            if (reihe.isNotEmpty()) aus[land] = reihe.takeLast(BEHALTEN)
        }
        return aus.takeIf { it.isNotEmpty() }
    }
}
