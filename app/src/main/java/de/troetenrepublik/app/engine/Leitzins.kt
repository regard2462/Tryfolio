package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate
import kotlin.math.abs

// Leitzins-Historie [0.78]: Leitzinsen der Zentralbanken aus dem Datenportal der BIS (Central bank policy rates,
// Datensatz WS_CBPOL). Die Datei globus/leitzins.json (tools/daten/leitzins.py) traegt je Land die Monatsreihe als
// Laufliste und die letzten Zinsschritte mit Tag. Neuere Tageswerte holt die App je Land nach (adresse, ergaenzen).
// Saetze in Prozent; die Datei speichert Tausendstel.
object Leitzins {

    const val DATEI = "globus/leitzins.json"

    // Hoechstzahl der Zinsschritte je Land
    const val SCHRITTE = 12

    // Beitritt zum Euroraum (Januar des Jahres), soweit nicht 1999. Bis dahin gilt die Reihe der eigenen Zentralbank.
    private val EURO_BEITRITT = mapOf(
        "GR" to 2001, "SI" to 2007, "CY" to 2008, "MT" to 2008, "SK" to 2009, "EE" to 2011, "LV" to 2014, "LT" to 2015, "HR" to 2023, "BG" to 2026,
    )

    class Schritt(val tag: LocalDate, val vorher: Double, val nachher: Double) {
        val aenderung: Double get() = nachher - vorher
    }

    // ab: erster Monat als Jahr * 12 + (Monat - 1). werte: ein Satz je Monat ab dort, null ohne Wert.
    // schritte: aelteste zuerst. letzterTag: letzter Tag mit Tageswert, null ohne Tageswerte.
    class Reihe(val ab: Int, val werte: List<Double?>, val schritte: List<Schritt>, val letzterTag: LocalDate?) {
        val bis: Int get() = ab + werte.size - 1
    }

    class Datei(val stand: String, val laender: Map<String, Reihe>)

    private fun satz(e: JsonElement?): Double? = (e as? JsonPrimitive)?.takeIf { it !is JsonNull }?.intOrNull?.div(1000.0)

    // "1999-01" zu Jahr * 12 + (Monat - 1); null bei anderer Form
    fun monat(s: String): Int? {
        if (s.length != 7 || s[4] != '-') return null
        val j = s.substring(0, 4).toIntOrNull() ?: return null
        val m = s.substring(5).toIntOrNull()?.takeIf { it in 1..12 } ?: return null
        return j * 12 + m - 1
    }

    fun lesen(text: String): Datei? = runCatching {
        val w = Json.parseToJsonElement(text).jsonObject
        val stand = (w["stand"] as? JsonPrimitive)?.content?.takeIf { Alter.tag(it) != null } ?: return null
        val l = w["l"] as? JsonObject ?: return null
        val aus = LinkedHashMap<String, Reihe>()
        for ((land, v) in l) {
            val o = v as? JsonObject ?: continue
            val ab = (o["ab"] as? JsonPrimitive)?.content?.let { monat(it) } ?: continue
            val lauf = o["w"] as? JsonArray ?: continue
            if (lauf.size % 2 != 0) continue
            val werte = ArrayList<Double?>()
            var gut = true
            for (i in 0 until lauf.size step 2) {
                val n = (lauf[i + 1] as? JsonPrimitive)?.intOrNull
                if (n == null || n <= 0 || werte.size + n > 2400) { gut = false; break }
                val s = satz(lauf[i])
                repeat(n) { werte.add(s) }
            }
            if (!gut || werte.isEmpty() || werte.all { it == null }) continue
            val schritte = (o["s"] as? JsonArray).orEmpty().mapNotNull { e ->
                val a = e as? JsonArray ?: return@mapNotNull null
                if (a.size != 3) return@mapNotNull null
                val tag = runCatching { LocalDate.parse((a[0] as JsonPrimitive).content) }.getOrNull() ?: return@mapNotNull null
                Schritt(tag, satz(a[1]) ?: return@mapNotNull null, satz(a[2]) ?: return@mapNotNull null)
            }.sortedBy { it.tag }
            val letzter = (o["t"] as? JsonPrimitive)?.content?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            aus[land] = Reihe(ab, werte, schritte, letzter)
        }
        if (aus.isEmpty()) null else Datei(stand, aus)
    }.getOrNull()

    // Gebiet der BIS, dessen Leitzins heute in einem Land gilt: der Euroraum fuer Eurolaender, sonst das Land selbst.
    fun gebiet(a2: String): String = if (a2 in LaenderMarkt.EURO) LaenderMarkt.EURORAUM else a2

    // Reihe eines Landes (alpha-2). Eurolaender: die eigene Reihe bis zum Beitritt, danach die des Euroraums; die
    // Zinsschritte sind die der EZB seit dem Beitritt.
    fun fuer(d: Datei, a2: String): Reihe? {
        val eigen = d.laender[a2]
        if (a2 !in LaenderMarkt.EURO) return eigen
        val euro = d.laender[LaenderMarkt.EURORAUM] ?: return eigen
        val beitritt = maxOf((EURO_BEITRITT[a2] ?: 1999) * 12, euro.ab)
        if (beitritt > euro.bis) return eigen
        val danach = euro.werte.subList(beitritt - euro.ab, euro.werte.size)
        val beitrittTag = LocalDate.of(beitritt / 12, beitritt % 12 + 1, 1)
        val schritte = euro.schritte.filter { it.tag >= beitrittTag }
        if (eigen == null || eigen.ab >= beitritt) return Reihe(beitritt, danach, schritte, euro.letzterTag)
        val davor = ArrayList<Double?>(beitritt - eigen.ab)
        for (m in eigen.ab until beitritt) davor.add(eigen.werte.getOrNull(m - eigen.ab))
        return Reihe(eigen.ab, davor + danach, schritte, euro.letzterTag)
    }

    // ---- Nachtragen aus den Tageswerten der BIS

    // Tageswerte eines Gebiets ab dem Monat nach dem Ende der Reihe
    fun adresse(gebiet: String, r: Reihe): String {
        val m = r.bis + 1
        val ab = "${m / 12}-${(m % 12 + 1).toString().padStart(2, '0')}-01"
        return "https://stats.bis.org/api/v2/data/dataflow/BIS/WS_CBPOL/1.0/D.$gebiet?startPeriod=$ab&detail=dataonly&format=csv"
    }

    // CSV der BIS (FREQ,REF_AREA,TIME_PERIOD,OBS_VALUE) zu Tageswerten eines Gebiets, aufsteigend; NaN und Leeres fehlen.
    fun tageLesen(csv: String, gebiet: String): List<Pair<LocalDate, Double>> {
        val zeilen = csv.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (zeilen.isEmpty()) return emptyList()
        val kopf = zeilen.first().split(',').map { it.trim().trim('"') }
        val iLand = kopf.indexOf("REF_AREA")
        val iTag = kopf.indexOf("TIME_PERIOD")
        val iWert = kopf.indexOf("OBS_VALUE")
        if (iLand < 0 || iTag < 0 || iWert < 0) return emptyList()
        val aus = ArrayList<Pair<LocalDate, Double>>()
        for (z in zeilen.drop(1)) {
            val t = z.split(',').map { it.trim().trim('"') }
            if (t.size <= maxOf(iLand, iTag, iWert) || t[iLand] != gebiet) continue
            val tag = runCatching { LocalDate.parse(t[iTag]) }.getOrNull() ?: continue
            val wert = t[iWert].toDoubleOrNull()?.takeIf { it.isFinite() } ?: continue
            aus.add(tag to wert)
        }
        return aus.sortedBy { it.first }
    }

    // Verkleinert eine Antwort auf die gebrauchten Spalten fuer die Speicherung; null ohne Werte.
    fun tageKuerzen(csv: String, gebiet: String): String? {
        val t = tageLesen(csv, gebiet)
        if (t.isEmpty()) return null
        return "FREQ,REF_AREA,TIME_PERIOD,OBS_VALUE\n" + t.joinToString("\n") { "D,$gebiet,${it.first},${it.second}" }
    }

    // Aktueller Satz laut Reihe: nach dem letzten Zinsschritt, sonst der letzte Monatswert
    fun aktuell(r: Reihe): Double? = r.schritte.lastOrNull()?.nachher ?: r.werte.lastOrNull { it != null }

    private fun gleich(a: Double, b: Double) = abs(a - b) < 0.0005

    // Reihe um Tageswerte verlaengert: Monate nach dem Ende bekommen den letzten Tageswert des Monats, Wechsel nach dem
    // letzten bekannten Tag werden Zinsschritte. Tage vor dem Ende der Reihe aendern nichts.
    fun ergaenzen(r: Reihe, tage: List<Pair<LocalDate, Double>>): Reihe {
        if (tage.isEmpty()) return r
        val werte = ArrayList(r.werte)
        val schritte = ArrayList(r.schritte)
        var satz = aktuell(r)
        var letzter = r.letzterTag
        for ((tag, wert) in tage.sortedBy { it.first }) {
            val m = tag.year * 12 + tag.monthValue - 1
            if (m > r.bis) {
                if (m - r.ab > 2400) break
                while (werte.size <= m - r.ab) werte.add(werte.last())
                werte[m - r.ab] = wert
            }
            val neu = if (letzter != null) tag > letzter else m > r.bis
            if (!neu) continue
            if (satz != null && !gleich(satz, wert)) schritte.add(Schritt(tag, satz, wert))
            satz = wert
            letzter = tag
        }
        return Reihe(r.ab, werte, schritte.takeLast(SCHRITTE), letzter)
    }

    // ---- Kernzeile und Verlauf

    // satz: aktueller Leitzins. seit: Tag des letzten Zinsschritts; ohne Zinsschritt der erste Tag des Monats, seit dem
    // die Monatsreihe den Satz zeigt (nurMonat).
    class Kern(val satz: Double, val seit: LocalDate, val nurMonat: Boolean)

    fun kern(r: Reihe): Kern? {
        val satz = aktuell(r) ?: return null
        r.schritte.lastOrNull()?.let { return Kern(satz, it.tag, false) }
        var i = r.werte.indexOfLast { it != null }
        if (i < 0) return null
        while (i > 0 && r.werte[i - 1]?.let { gleich(it, satz) } == true) i--
        val m = r.ab + i
        return Kern(satz, LocalDate.of(m / 12, m % 12 + 1, 1), true)
    }

    // Verlauf als Treppe fuer das Diagramm: (Jahr mit Bruchteil, Satz) ab dem Monat abMonat (Jahr * 12 + Monat - 1);
    // jeder Wechsel bekommt zwei Punkte am selben Ort, Luecken werden uebersprungen.
    fun treppe(r: Reihe, abMonat: Int = r.ab): List<Pair<Double, Double>> {
        val aus = ArrayList<Pair<Double, Double>>()
        var vorher: Double? = null
        for (i in maxOf(abMonat - r.ab, 0) until r.werte.size) {
            val v = r.werte[i] ?: continue
            val x = (r.ab + i) / 12.0
            if (vorher == null) aus.add(x to v)
            else if (!gleich(vorher, v)) { aus.add(x to vorher); aus.add(x to v) }
            vorher = v
        }
        if (vorher != null) aus.add((r.ab + r.werte.size) / 12.0 to vorher)
        return aus
    }

    // Erster Monat des Verlaufs: hoechstens jahre Jahre zurueck, fruehestens der erste Monat mit Wert
    fun verlaufAb(r: Reihe, jahre: Int): Int {
        val erster = r.ab + r.werte.indexOfFirst { it != null }.coerceAtLeast(0)
        return maxOf(erster, r.bis + 1 - jahre * 12)
    }

    // Hoechster und tiefster Satz ab einem Monat
    fun spanne(r: Reihe, abMonat: Int): Pair<Double, Double>? {
        val w = r.werte.drop(maxOf(abMonat - r.ab, 0)).filterNotNull()
        return if (w.isEmpty()) null else w.min() to w.max()
    }
}
