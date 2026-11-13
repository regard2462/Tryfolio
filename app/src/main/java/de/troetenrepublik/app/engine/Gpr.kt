package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

// Geopolitical Risk Index (Caldara und Iacoviello, CC BY): Anteil der Zeitungsartikel eines Monats, die geopolitische
// Spannungen, Drohungen und Handlungen nennen. Die Datei indikatoren/gpr.json (tools/daten/gpr.py) traegt die Werte der
// Quelle; Einordnung und Rang rechnet dieser Baustein. 100 entspricht dem Mittel der Jahre 1985 bis 2019.
//
// Schnittstelle fuer andere Auswertungen: lesen(text) liefert die Reihen h (historisch ab 1900, drei Zeitungen), g (ab 1985,
// zehn Zeitungen), t (Drohungen), d (Handlungen) und je Land lm (Monate ab 1985) und lj (Jahresmittel ab 1900).
object Gpr {

    const val DATEI = "indikatoren/gpr.json"
    const val SEITE = "https://www.matteoiacoviello.com/gpr.htm"

    // Laenderindex: Jahresmittel historisch
    class Jahre(val von: Int, val werte: List<Double?>) {
        val bis: Int get() = von + werte.size - 1
    }

    class Datei(
        val stand: String, val zitat: String,
        val h: Monatsreihe, val g: Monatsreihe, val t: Monatsreihe, val d: Monatsreihe,
        val lm: Map<String, Monatsreihe>, val lj: Map<String, Jahre>,
    )

    fun lesen(text: String): Datei? = runCatching {
        val w = Json.parseToJsonElement(text).jsonObject
        val stand = (w["stand"] as? JsonPrimitive)?.content?.takeIf { Alter.tag(it) != null } ?: return null
        val zitat = ((w["quelle"] as? JsonObject)?.get("zitat") as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: return null
        val h = Monatsreihe.lesen(w["h"] as? JsonObject) ?: return null
        val g = Monatsreihe.lesen(w["g"] as? JsonObject) ?: return null
        val t = Monatsreihe.lesen(w["t"] as? JsonObject) ?: return null
        val d = Monatsreihe.lesen(w["d"] as? JsonObject) ?: return null
        val lm = LinkedHashMap<String, Monatsreihe>()
        (w["lm"] as? JsonObject)?.forEach { (k, v) -> lm[k] = Monatsreihe.lesen(v as? JsonObject) ?: return null }
        val lj = LinkedHashMap<String, Jahre>()
        (w["lj"] as? JsonObject)?.forEach { (k, v) ->
            val (a, ws) = Monatsreihe.lesenJahre(v as? JsonObject) ?: return null
            lj[k] = Jahre(a, ws)
        }
        if (h.werte.size < 120 || g.werte.size < 120) null else Datei(stand, zitat, h, g, t, d, lm, lj)
    }.getOrNull()

    // Lage des letzten Monats: Wert, Rang gegen die eigene Reihe, hoechster Stand seit
    class Lage(val monat: Int, val wert: Double, val perzentil: Double, val stufe: MonatsreiheRechnung.Stufe, val hoechsterSeit: Int?)

    // Einordnung einer Reihe nach dem Rang ihres letzten Werts gegen alle Monate der Reihe
    fun lage(r: Monatsreihe): Lage? {
        val (m, v) = r.letzter() ?: return null
        val p = MonatsreiheRechnung.perzentil(r, v) ?: return null
        return Lage(m, v, p, MonatsreiheRechnung.stufe(p), MonatsreiheRechnung.hoechsterSeit(r, m))
    }

    // Die Laender der Quelle, die zu einem Gebiet gehoeren, zuerst die mit der staerksten Lage (hoechster Rang)
    fun laenderLage(d: Datei, iso: List<String>, maximal: Int = 2): List<Pair<String, Lage>> =
        iso.mapNotNull { k -> d.lm[k]?.let { r -> lage(r)?.let { k to it } } }.sortedByDescending { it.second.perzentil }.take(maximal)
}
