package de.troetenrepublik.app.engine

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

// Monatsreihe mit lueckenloser Zeitachse: Monate werden als Zahl jahr * 12 + (monat - 1) gefuehrt. Gemeinsame Grundlage fuer
// den Geopolitical Risk Index und die Unsicherheitsindizes (indikatoren/gpr.json, indikatoren/epu.json).
class Monatsreihe(val von: Int, val werte: List<Double?>) {
    val bis: Int get() = von + werte.size - 1
    fun wert(monat: Int): Double? = werte.getOrNull(monat - von)

    // Letzter Monat mit Wert und dieser Wert
    fun letzter(): Pair<Int, Double>? {
        for (i in werte.indices.reversed()) werte[i]?.let { return (von + i) to it }
        return null
    }

    companion object {
        fun monat(jahr: Int, monat: Int): Int = jahr * 12 + (monat - 1)
        fun jahr(m: Int): Int = Math.floorDiv(m, 12)
        fun monatImJahr(m: Int): Int = Math.floorMod(m, 12) + 1

        // "JJJJ-MM" zum Monat; null bei anderer Form
        fun lesenMonat(text: String): Int? {
            val m = Regex("^(\\d{4})-(\\d{2})$").find(text.trim()) ?: return null
            val mo = m.groupValues[2].toInt()
            if (mo !in 1..12) return null
            return monat(m.groupValues[1].toInt(), mo)
        }

        // "10/2026"
        fun marke(m: Int): String = monatImJahr(m).toString().padStart(2, '0') + "/" + jahr(m)

        // {"a": "JJJJ-MM", "w": [Wert oder null, ...]}
        fun lesen(o: JsonObject?): Monatsreihe? {
            o ?: return null
            val von = lesenMonat((o["a"] as? JsonPrimitive)?.content ?: return null) ?: return null
            val w = (o["w"] as? JsonArray)?.map { x -> if (x is JsonNull) null else ((x as? JsonPrimitive)?.doubleOrNull ?: return null) } ?: return null
            if (w.isEmpty() || w.any { it != null && (!it.isFinite() || it < 0.0) }) return null
            return Monatsreihe(von, w)
        }

        // {"a": Jahr, "w": [...]} fuer Jahresreihen
        fun lesenJahre(o: JsonObject?): Pair<Int, List<Double?>>? {
            o ?: return null
            val von = (o["a"] as? JsonPrimitive)?.intOrNull ?: return null
            val w = (o["w"] as? JsonArray)?.map { x -> if (x is JsonNull) null else ((x as? JsonPrimitive)?.doubleOrNull ?: return null) } ?: return null
            if (w.isEmpty()) return null
            return von to w
        }
    }
}

// Einordnung eines Monatswerts gegen die eigene Reihe
object MonatsreiheRechnung {

    // Letzter Monat vor `monat`, dessen Wert mindestens so hoch war wie der Wert von `monat`; null, wenn es keinen gab
    // (hoechster Stand der ganzen Reihe).
    fun hoechsterSeit(r: Monatsreihe, monat: Int? = null): Int? {
        val ziel = monat ?: r.letzter()?.first ?: return null
        val v = r.wert(ziel) ?: return null
        var m = ziel - 1
        while (m >= r.von) {
            val x = r.wert(m)
            if (x != null && x >= v) return m
            m--
        }
        return null
    }

    // Anteil der Monate (0 bis 100) in [ab, bis], deren Wert unter dem gegebenen liegt
    fun perzentil(r: Monatsreihe, wert: Double, ab: Int = r.von, bis: Int = r.bis): Double? {
        var n = 0
        var kleiner = 0
        for (m in maxOf(ab, r.von)..minOf(bis, r.bis)) {
            val x = r.wert(m) ?: continue
            n++
            if (x < wert) kleiner++
        }
        return if (n < 12) null else kleiner * 100.0 / n
    }

    fun mittel(r: Monatsreihe, ab: Int, bis: Int): Double? {
        val w = (maxOf(ab, r.von)..minOf(bis, r.bis)).mapNotNull { r.wert(it) }
        return if (w.size < 6) null else w.average()
    }

    // Mittel der letzten n Monate bis einschliesslich `bis`
    fun gleitend(r: Monatsreihe, bis: Int, n: Int): Double? = mittel(r, bis - n + 1, bis)

    // Jahresmittel; ein Jahr zaehlt ab sechs Monatswerten
    fun jahresmittel(r: Monatsreihe): List<Pair<Int, Double>> {
        val je = LinkedHashMap<Int, MutableList<Double>>()
        for (m in r.von..r.bis) r.wert(m)?.let { je.getOrPut(Monatsreihe.jahr(m)) { ArrayList() } += it }
        return je.filter { it.value.size >= 6 }.map { it.key to it.value.average() }
    }

    // Punkte (Zeit in Jahren mit Monatsbruchteil, Wert) ab einem Monat, jeweils in der Mitte des Monats
    fun punkte(r: Monatsreihe, ab: Int = r.von): List<Pair<Double, Double>> =
        (maxOf(ab, r.von)..r.bis).mapNotNull { m -> r.wert(m)?.let { (m + 0.5) / 12.0 to it } }

    // Einordnung des letzten Werts nach dem Rang in der Reihe
    enum class Stufe { NIEDRIG, NORMAL, ERHOEHT, HOCH }

    fun stufe(perzentil: Double): Stufe = when {
        perzentil < 25.0 -> Stufe.NIEDRIG
        perzentil < 75.0 -> Stufe.NORMAL
        perzentil < 95.0 -> Stufe.ERHOEHT
        else -> Stufe.HOCH
    }
}
