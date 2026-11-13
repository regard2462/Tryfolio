package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.data.Series
import org.junit.Assert.assertEquals
import org.junit.Test

class LivePunktTest {
    private val min = 60_000L
    private val start = 1_000_000_000_000L
    private fun k(i: Int, c: Double) = Candle(start + i * 2 * min, c, c, c, c)
    private val serie = Series(listOf(k(0, 100.0), k(1, 101.0), k(2, 102.0)), start - 60 * min, start + 600 * min)
    private fun q(p: Double, t: Long) = Quote(symbol = "X", name = "X", price = p, marketTime = t, updatedAt = t)

    @Test fun neuerPunktNachDerLetztenKerze() {
        val t = start + 10 * min
        val r = LivePunkt.anhaengen(serie, q(103.5, t), t + 1000)
        assertEquals(4, r.size)
        assertEquals(t, r.last().t)
        assertEquals(103.5, r.last().close, 1e-9)
    }

    @Test fun innerhalbDerLetztenKerzeWirdSieFortgefuehrt() {
        val t = start + 4 * min + 30_000
        val r = LivePunkt.anhaengen(serie, q(99.0, t), t)
        assertEquals(3, r.size)
        assertEquals(99.0, r.last().close, 1e-9)
        assertEquals(99.0, r.last().low, 1e-9)
        assertEquals(102.0, r.last().high, 1e-9)
    }

    @Test fun altOderAusserhalbBleibtUnveraendert() {
        assertEquals(serie.candles, LivePunkt.anhaengen(serie, q(103.0, start + 2 * min), start + 3 * min))
        assertEquals(serie.candles, LivePunkt.anhaengen(serie, q(103.0, start + 700 * min), start + 700 * min))
        assertEquals(serie.candles, LivePunkt.anhaengen(serie, null, start + 10 * min))
        assertEquals(serie.candles, LivePunkt.anhaengen(serie, q(160.0, start + 10 * min), start + 10 * min))
    }

    // Gemerkter Stand von vorher: kein flacher Strich mit Sprung bis zum Kurs von jetzt (0.77)
    @Test fun alteReiheBekommtKeinenPunkt() {
        val t = start + 4 * min + LivePunkt.ALT_AB_MS + min
        assertEquals(serie.candles, LivePunkt.anhaengen(serie, q(103.0, t), t))
        assertEquals(4, LivePunkt.anhaengen(serie, q(103.0, t - 2 * min), t).size)
    }

    @Test fun zeitHoechstensJetzt() {
        val r = LivePunkt.anhaengen(serie, q(103.0, start + 50 * min), start + 20 * min)
        assertEquals(start + 20 * min, r.last().t)
    }
}
