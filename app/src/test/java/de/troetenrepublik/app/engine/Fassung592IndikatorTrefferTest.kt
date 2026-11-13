package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

// Fassung 5.92: Trefferbilanz der Chart-Indikatoren mit kuenstlichen Kursreihen.
class Fassung592IndikatorTrefferTest {

    private val tag = 86_400_000L
    private val beginn = 1_500_000_000_000L

    // Kerzen aus Schlusskursen; Eroeffnung = Schluss des Vortags.
    private fun kerzen(schluss: List<Double>, umsatz: Double = 0.0): List<Candle> = schluss.mapIndexed { i, c ->
        val o = if (i == 0) c else schluss[i - 1]
        Candle(beginn + i * tag, o, maxOf(o, c) * 1.002, minOf(o, c) * 0.998, c, umsatz)
    }

    // Ruhige Welle mit 200 Tagen Laenge: Kreuzt der Kurs den Schnitt, laeuft er noch lange in diese Richtung.
    private val welle = (0 until 2520).map { 100.0 * exp(0.3 * sin(2 * PI * it / 200.0)) }

    @Test fun kreuzenDesDurchschnitts() {
        // 60 Tage flach, dann ein Sprung nach oben und spaeter einer nach unten
        val s = List(60) { 100.0 } + List(40) { 110.0 } + List(40) { 90.0 }
        val h = s.map { it * 1.001 }
        val t = s.map { it * 0.999 }
        val sig = IndikatorTreffer.signale("sma50", s, h, t, List(s.size) { 0.0 })
        assertEquals(1, sig[60])
        assertEquals(-1, sig[100])
        assertEquals(2, sig.count { it != 0 })
    }

    @Test fun welleTrifftUndIstErkennbar() {
        val b = IndikatorTreffer.alle(kerzen(welle), listOf("sma50", "macd"))
        assertNotNull(b["sma50"])
        val s50 = b["sma50"]!!
        assertTrue("Signale ${s50.signale}", s50.signale in 20..30)
        assertEquals(1.0, s50.treffer!!, 1e-9)
        // Auf und ab etwa gleich oft: ohne Signal rund die Haelfte
        assertTrue("Vergleich ${s50.vergleich}", s50.vergleich!! in 0.4..0.6)
        assertEquals(IndikatorTreffer.Stufe.ERKENNBAR, s50.stufe)
        // ab ist der erste Tag mit einem Signal, nicht der Beginn der Reihe: der Schnitt braucht 50 Tage Anlauf
        val start = Math.floorDiv(beginn, tag)
        assertTrue("ab ${s50.ab - start}", s50.ab - start in 50..150)
        assertTrue(b["macd"]!!.signale > 0)
    }

    @Test fun stufen() {
        assertEquals(IndikatorTreffer.Stufe.GERING, IndikatorTreffer.stufe(0.56, 0.54, 100))
        assertEquals(IndikatorTreffer.Stufe.ERKENNBAR, IndikatorTreffer.stufe(0.70, 0.54, 100))
        assertEquals(IndikatorTreffer.Stufe.UMGEKEHRT, IndikatorTreffer.stufe(0.35, 0.54, 100))
        // Wenige Signale: 10 Prozentpunkte reichen nicht
        assertEquals(IndikatorTreffer.Stufe.GERING, IndikatorTreffer.stufe(0.64, 0.54, 12))
        assertEquals(IndikatorTreffer.Stufe.ZU_WENIG, IndikatorTreffer.stufe(1.0, 0.5, 4))
    }

    @Test fun zuWenigeSignale() {
        // Stetig steigend: der Kurs kreuzt den 200-Tage-Schnitt nie
        val s = (0 until 600).map { 100.0 + it * 0.1 }
        val b = IndikatorTreffer.alle(kerzen(s), listOf("sma200"))["sma200"]!!
        assertEquals(0, b.signale)
        assertNull(b.treffer)
        assertEquals(IndikatorTreffer.Stufe.ZU_WENIG, b.stufe)
    }

    @Test fun rsiVerlaesstDieZone() {
        // Langer Fall drueckt den RSI unter 30, die Erholung hebt ihn wieder darueber: ein Signal fuer steigende Kurse
        val s = (0 until 40).map { 100.0 - it * 1.0 } + (1..40).map { 61.0 + it * 1.0 }
        val sig = IndikatorTreffer.signale("rsi", s, s, s, List(s.size) { 0.0 })
        val i = sig.indexOfFirst { it != 0 }
        assertTrue("Signal bei $i", i in 40..50)
        assertEquals(1, sig[i])
    }

    @Test fun obvBrauchtUmsatz() {
        val ohne = IndikatorTreffer.alle(kerzen(welle), listOf("obv"))
        assertTrue(ohne.isEmpty())
        val mit = IndikatorTreffer.alle(kerzen(welle, umsatz = 1_000.0), listOf("obv"))
        assertTrue(mit["obv"]!!.signale > 0)
    }

    @Test fun atrWarntBeiUnruhe() {
        // Zwei ruhige Jahre, dann starke Ausschlaege: eine Warnung zu Beginn der Unruhe, danach keine weitere
        val r = java.util.Random(7)
        val ruhig = (0 until 500).map { 100.0 * (1 + 0.003 * r.nextGaussian()) }
        val wild = (0 until 60).map { 100.0 * (1 + 0.05 * r.nextGaussian()) }
        val s = ruhig + wild
        val k = kerzen(s)
        val sig = IndikatorTreffer.signale("atr", k.map { it.close }, k.map { it.high }, k.map { it.low }, List(s.size) { 0.0 })
        val warnungen = sig.withIndex().filter { it.value != 0 }.map { it.index }
        assertTrue("Warnungen $warnungen", warnungen.any { it in 500..520 } && warnungen.none { it > 520 })
    }

    @Test fun kurzeReiheOhneBilanz() {
        assertTrue(IndikatorTreffer.alle(kerzen(List(40) { 100.0 }), listOf("sma50")).isEmpty())
    }
}
