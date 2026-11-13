package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import kotlin.math.roundToLong

// Stoppabstand aus der mittleren Tagesspanne (0.78): ATR ueber 20 Tage in Prozent des letzten Schlusskurses, fuer den
// Stoppvorschlag in der Positionsgroesse und den Abstand eines Trailing-Stopps. Orders bleiben in Prozent.
object AtrAbstand {
    const val TAGE = 20
    val FAKTOREN = listOf(2, 3)

    // Obergrenze des Abstands einer Trailing-Order in Prozent (Trading.orderError).
    const val MAX_PROZENT = 50.0

    // ATR in Prozent des letzten Schlusskurses; null bei zu wenigen Tageskerzen. Fehlen Hoch und Tief, zaehlt der Schluss.
    fun prozent(kerzen: List<Candle>, n: Int = TAGE): Double? {
        val l = kerzen.filter { it.close.isFinite() && it.close > 0 }.sortedBy { it.t }
        if (l.size <= n) return null
        val hoch = l.map { if (it.high.isFinite() && it.high > 0) maxOf(it.high, it.close) else it.close }
        val tief = l.map { if (it.low.isFinite() && it.low > 0) minOf(it.low, it.close) else it.close }
        val atr = Indikatoren.atr(hoch, tief, l.map { it.close }, n).last() ?: return null
        return (atr / l.last().close * 100.0).takeIf { it.isFinite() && it > 0 }
    }

    // Abstand in Prozent fuer ein Vielfaches der ATR, auf zwei Stellen; null, wenn er als Stopp nicht taugt.
    fun abstand(atrProzent: Double, faktor: Int, max: Double = 100.0): Double? {
        val v = (atrProzent * faktor * 100.0).roundToLong() / 100.0
        return v.takeIf { it > 0 && it < 100.0 && it <= max }
    }

    // Abstand als Eingabe des Ziffernfelds der Ordermaske (Komma, hoechstens zwei Stellen, ohne Nullen am Ende).
    fun eingabe(prozent: Double): String {
        val c = (prozent * 100.0).roundToLong()
        val nach = (c % 100).toString().padStart(2, '0').trimEnd('0')
        return (c / 100).toString() + if (nach.isEmpty()) "" else ",$nach"
    }
}
