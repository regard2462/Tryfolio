package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

// Hauspreise real seit 1975 [0.78]: International House Price Database der Federal Reserve Bank of Dallas, vierteljaehrlich,
// reale Hauspreise (RHPI) und reale verfuegbare Einkommen (RPDI) je Land. Die Datei globus/hauspreise.json
// (tools/daten/historie.py) traegt die Indexwerte in Hundertsteln. Die Anzeige nennt nur Zahlen, kein Urteil.
object Hauspreise {

    const val DATEI = "globus/hauspreise.json"

    // preis und einkommen: Indexwerte, ein Wert je Quartal ab Datei.ab
    class Land(val a3: String, val preis: List<Double>, val einkommen: List<Double>)

    // ab und bis: erstes und letztes Quartal wie "1975Q1"
    class Datei(val stand: String, val ab: String, val bis: String, val laender: Map<String, Land>, val zitat: List<String>, val hinweis: String)

    fun lesen(text: String): Datei? = runCatching {
        val w = Json.parseToJsonElement(text).jsonObject
        val stand = (w["stand"] as? JsonPrimitive)?.content?.takeIf { Alter.tag(it) != null } ?: return null
        val ab = (w["ab"] as? JsonPrimitive)?.content?.takeIf { quartal(it) != null } ?: return null
        val bis = (w["bis"] as? JsonPrimitive)?.content?.takeIf { quartal(it) != null } ?: return null
        val zitat = (w["zitat"] as? JsonArray)?.map { (it as JsonPrimitive).content }.orEmpty()
        val hinweis = (w["hinweis"] as? JsonPrimitive)?.content.orEmpty()
        val l = w["l"] as? JsonObject ?: return null
        val n = anzahl(ab, bis)
        val aus = LinkedHashMap<String, Land>()
        for ((k, v) in l) {
            val o = v.jsonObject
            val p = (o["p"] as? JsonArray)?.map { (it as JsonPrimitive).doubleOrNull?.div(100.0) ?: return null } ?: return null
            val e = (o["e"] as? JsonArray)?.map { (it as JsonPrimitive).doubleOrNull?.div(100.0) ?: return null } ?: return null
            if (p.size != n || e.size != n || p.any { it <= 0.0 } || e.any { it <= 0.0 }) return null
            aus[k] = Land(k, p, e)
        }
        if (aus.isEmpty()) null else Datei(stand, ab, bis, aus, zitat, hinweis)
    }.getOrNull()

    // "1975Q1" zu (Jahr, Quartal); null bei anderer Form
    fun quartal(s: String): Pair<Int, Int>? {
        if (s.length != 6 || s[4] != 'Q') return null
        val j = s.substring(0, 4).toIntOrNull() ?: return null
        val q = s[5].digitToIntOrNull() ?: return null
        return if (q in 1..4) j to q else null
    }

    // Zahl der Quartale von ab bis bis einschliesslich
    fun anzahl(ab: String, bis: String): Int {
        val a = quartal(ab) ?: return 0
        val b = quartal(bis) ?: return 0
        return (b.first - a.first) * 4 + (b.second - a.second) + 1
    }

    // Quartal Nummer i der Reihe (0 = erstes) als Jahr und Quartal
    fun quartalAn(d: Datei, i: Int): Pair<Int, Int> {
        val a = quartal(d.ab)!!
        val k = a.first * 4 + (a.second - 1) + i
        return (k / 4) to (k % 4 + 1)
    }

    // Kernzahlen eines Landes: aenderung = reale Veraenderung der Hauspreise vom ersten zum letzten Quartal (Prozent);
    // abweichung = Verhaeltnis Preis zu Einkommen im letzten Quartal gegen den Mittelwert aller Quartale (Prozent, positiv: darueber).
    class Kern(val aenderung: Double, val abweichung: Double, val ab: Pair<Int, Int>, val bis: Pair<Int, Int>)

    fun verhaeltnis(l: Land): List<Double> = l.preis.indices.map { l.preis[it] / l.einkommen[it] }

    fun kern(d: Datei, l: Land): Kern {
        val v = verhaeltnis(l)
        val schnitt = v.sum() / v.size
        return Kern(
            aenderung = (l.preis.last() / l.preis.first() - 1.0) * 100.0,
            abweichung = (v.last() / schnitt - 1.0) * 100.0,
            ab = quartalAn(d, 0), bis = quartalAn(d, l.preis.size - 1),
        )
    }

    // Beide Verlaeufe auf 100 im ersten Quartal gesetzt (Index in Prozent des Anfangsstands)
    fun auf100(reihe: List<Double>): List<Double> = reihe.map { it / reihe.first() * 100.0 }
}
