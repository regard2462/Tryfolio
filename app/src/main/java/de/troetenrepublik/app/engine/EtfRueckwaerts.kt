package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

// Rueckwaertssuche [0.77]: in welchen ETFs des Katalogs eine Aktie steckt, mit ihrem Gewicht im ETF. Grundlage ist die
// Datei etf-rueckwaerts.json (tools/daten/etf_rueckwaerts.py), je ETF nur die groessten Positionen der Anbieterangabe.
object EtfRueckwaerts {

    const val DATEI = "etf-rueckwaerts.json"

    // Ein ETF mit dem Gewicht der Aktie darin (Prozent).
    class Treffer(val etf: String, val name: String, val gewicht: Double)

    // stand: Tag der Datei (JJJJ-MM-TT), tiefe: Zahl der Positionen je ETF, die die Quelle liefert.
    class Daten(val stand: String, val tiefe: Int, internal val nachAktie: Map<String, List<Treffer>>) {
        val anzahlAktien: Int get() = nachAktie.size
    }

    // Etwas anderes als ein gueltiges Buendel liefert null.
    fun lesen(text: String): Daten? = runCatching {
        val wurzel = Json.parseToJsonElement(text).jsonObject
        val stand = (wurzel["stand"] as? JsonPrimitive)?.content?.takeIf { Alter.tag(it) != null } ?: return null
        val tiefe = (wurzel["tiefe"] as? JsonPrimitive)?.intOrNull ?: 0
        val etfs = (wurzel["etfs"] as? JsonArray)?.map { e ->
            val a = e as JsonArray
            (a[0] as JsonPrimitive).content to (a[1] as JsonPrimitive).content
        } ?: return null
        val aktien = wurzel["aktien"] as? JsonObject ?: return null
        val aus = HashMap<String, List<Treffer>>(aktien.size * 2)
        for ((k, v) in aktien) {
            val l = (v as JsonArray).mapNotNull { p ->
                val a = p as JsonArray
                val i = (a[0] as JsonPrimitive).intOrNull ?: return@mapNotNull null
                val g = (a[1] as JsonPrimitive).doubleOrNull ?: return@mapNotNull null
                val e = etfs.getOrNull(i) ?: return@mapNotNull null
                if (g > 0) Treffer(e.first, e.second, g) else null
            }.sortedByDescending { it.gewicht }
            if (l.isNotEmpty()) aus[k.uppercase()] = l
        }
        if (aus.isEmpty()) null else Daten(stand, tiefe, aus)
    }.getOrNull()

    // ETFs der Aktie, das groesste Gewicht zuerst; US-Klassen wie BRK-B stehen bei Yahoo mit Bindestrich, bei anderen
    // Quellen mit Punkt.
    fun finden(d: Daten, symbol: String): List<Treffer> {
        val s = symbol.uppercase()
        return d.nachAktie[s] ?: d.nachAktie[s.replace('.', '-')] ?: emptyList()
    }
}
