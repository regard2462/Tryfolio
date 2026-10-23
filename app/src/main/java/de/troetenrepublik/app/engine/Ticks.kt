package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import kotlin.math.abs
import kotlin.math.min

object Ticks {
    // Entfernt ein oder zwei Punkte, die gegenueber beiden Nachbarn um mehr als threshold in dieselbe Richtung abweichen,
    // waehrend die Nachbarn nah beieinanderliegen. Yahoo liefert nachboerslich gelegentlich solche Fehlkurse ohne Umsatz,
    // etwa Apple am 25.09.2026: 341, 328, 341 Dollar. Ein Sprung ohne Rueckkehr bleibt, ebenso erster und letzter Punkt.
    fun despike(candles: List<Candle>, threshold: Double = 0.015): List<Candle> {
        if (candles.size < 3) return candles
        val keep = BooleanArray(candles.size) { true }
        var prev = 0
        var i = 1
        while (i < candles.size - 1) {
            val len = spikeLength(candles, prev, i, threshold)
            if (len > 0) {
                for (k in i until i + len) keep[k] = false
                // Der Punkt danach bestaetigt den Ausreisser und bleibt; verglichen wird weiter mit prev.
                i += len
            } else {
                prev = i
                i++
            }
        }
        return if (keep.all { it }) candles else candles.filterIndexed { k, _ -> keep[k] }
    }

    // Je Rasterfeld von schrittMs bleibt der letzte Punkt, der letzte Punkt der Reihe immer.
    fun ausduennen(candles: List<Candle>, schrittMs: Long): List<Candle> {
        if (schrittMs <= 0L || candles.size < 3) return candles
        val out = ArrayList<Candle>(candles.size / 2 + 2)
        for (i in candles.indices) {
            val c = candles[i]
            val letzter = i == candles.lastIndex
            val feld = Math.floorDiv(c.t, schrittMs)
            val naechstesFeld = if (letzter) Long.MIN_VALUE else Math.floorDiv(candles[i + 1].t, schrittMs)
            // Der Punkt bleibt, wenn der naechste schon im folgenden Rasterfeld liegt oder es der letzte ist
            if (letzter || naechstesFeld != feld) out += c
        }
        return out
    }

    // 1 oder 2, wenn ab i ein Ausreisser gegenueber Punkt prev und dem Punkt danach beginnt, sonst 0.
    private fun spikeLength(c: List<Candle>, prev: Int, i: Int, threshold: Double): Int {
        val before = c[prev].close
        if (before <= 0.0) return 0
        for (len in 1..2) {
            val next = i + len
            if (next >= c.size) return 0
            val after = c[next].close
            if (after <= 0.0) return 0
            var sign = 0
            var smallest = Double.MAX_VALUE
            var ok = true
            for (k in i until next) {
                for (ref in doubleArrayOf(before, after)) {
                    val d = c[k].close / ref - 1.0
                    val s = if (d > threshold) 1 else if (d < -threshold) -1 else 0
                    if (s == 0 || (sign != 0 && s != sign)) { ok = false; break }
                    sign = s
                    smallest = min(smallest, abs(d))
                }
                if (!ok) break
            }
            if (ok && abs(after / before - 1.0) < smallest / 4) return len
        }
        return 0
    }
}
