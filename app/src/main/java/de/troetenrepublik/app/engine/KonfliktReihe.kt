package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

// Bewaffnete Konflikte je Land und Jahr seit 1946 nach dem UCDP/PRIO Armed Conflict Dataset (CC BY 4.0), gebuendelt in
// globus/konflikte-reihe.json (tools/daten/ucdp_reihe.py). Ergaenzt konflikte.json, das nur das letzte Jahr kennt.
// Stufe: 0 kein Konflikt im Jahr, 1 = 25 bis 999 Gefechtstote, 2 = ab 1000 (Krieg).
//
// Schnittstelle fuer andere Auswertungen: lesen(text).laender[ISO3] liefert Stufe und Zahl der Konflikte je Jahr ab von.
object KonfliktReihe {

    const val DATEI = "globus/konflikte-reihe.json"
    const val SEITE = "https://ucdp.uu.se/downloads/"

    class Land(val stufen: List<Int>, val anzahl: List<Int>)

    class Datei(val stand: String, val version: String, val von: Int, val bis: Int, val laender: Map<String, Land>)

    fun lesen(text: String): Datei? = runCatching {
        val w = Json.parseToJsonElement(text).jsonObject
        val stand = (w["stand"] as? JsonPrimitive)?.content?.takeIf { Alter.tag(it) != null } ?: return null
        val version = (w["v"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: return null
        val von = (w["a"] as? JsonPrimitive)?.intOrNull ?: return null
        val bis = (w["bis"] as? JsonPrimitive)?.intOrNull ?: return null
        val n = bis - von + 1
        if (n < 10) return null
        val aus = LinkedHashMap<String, Land>()
        (w["l"] as? JsonObject)?.forEach { (k, v) ->
            val o = v as? JsonObject ?: return null
            val s = (o["s"] as? JsonPrimitive)?.content ?: return null
            if (s.length != n || s.any { it !in '0'..'2' }) return null
            val a = (o["n"] as? JsonArray)?.map { (it as? JsonPrimitive)?.intOrNull ?: return null } ?: return null
            if (a.size != n) return null
            aus[k] = Land(s.map { it - '0' }, a)
        }
        if (aus.isEmpty()) null else Datei(stand, version, von, bis, aus)
    }.getOrNull()

    // Kennzahlen eines Landes
    // jahreMitKonflikt: Jahre mit Stufe 1 oder 2; kriegsjahre: Jahre mit Stufe 2; aktivSeit: Beginn der Serie ohne Pause bis
    // zum letzten Jahr der Daten (null, wenn im letzten Jahr kein Konflikt war); laengste: Dauer, erstes und letztes Jahr der
    // laengsten Serie ohne Pause; letztesJahr: letztes Jahr mit Konflikt, wenn keiner mehr laeuft
    class Kennzahlen(
        val jahre: Int, val jahreMitKonflikt: Int, val kriegsjahre: Int, val aktivSeit: Int?,
        val laengste: Triple<Int, Int, Int>?, val letztesJahr: Int?,
    )

    fun kennzahlen(d: Datei, l: Land): Kennzahlen {
        val n = d.bis - d.von + 1
        val mit = l.stufen.count { it > 0 }
        val krieg = l.stufen.count { it == 2 }
        var aktivSeit: Int? = null
        if (l.stufen.last() > 0) {
            var i = n - 1
            while (i > 0 && l.stufen[i - 1] > 0) i--
            aktivSeit = d.von + i
        }
        // Laengste Serie; bei Gleichstand die juengere
        var best: Triple<Int, Int, Int>? = null
        var start = -1
        for (i in 0..n) {
            val aktiv = i < n && l.stufen[i] > 0
            if (aktiv && start < 0) start = i
            if (!aktiv && start >= 0) {
                val dauer = i - start
                val b = best
                if (b == null || dauer >= b.first) best = Triple(dauer, d.von + start, d.von + i - 1)
                start = -1
            }
        }
        val letztes = l.stufen.indexOfLast { it > 0 }.takeIf { it >= 0 }?.let { d.von + it }
        return Kennzahlen(n, mit, krieg, aktivSeit, best, if (aktivSeit == null) letztes else null)
    }
}
