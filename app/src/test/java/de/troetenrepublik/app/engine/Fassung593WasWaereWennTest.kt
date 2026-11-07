package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.market.MarketApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

// Fassung 5.93: Was-waere-wenn gegen den Weltindex. Dividenden des Werts werden am Ex-Tag zum Schlusskurs
// wieder angelegt (Gesamtrendite), der thesaurierende Weltindex braucht keine Anpassung.
class Fassung593WasWaereWennTest {

    private val tag = 86_400_000L

    // Kerzen aus Schlusskursen, je ein Tag Abstand.
    private fun kerzen(vararg closes: Double) = closes.mapIndexed { i, c -> Candle(i * tag, c, c, c, c) }

    @Test fun ohneDividendenUnveraendert() {
        val d = kerzen(100.0, 101.0, 99.0, 102.0)
        assertEquals(d, Analysis.thesaurierend(d, emptyList()))
    }

    @Test fun eineDividende() {
        val d = kerzen(100.0, 98.0, 100.0, 105.0)
        val r = Analysis.thesaurierend(d, listOf(MarketApi.DividendEvent(2 * tag, 10.0)))
        assertEquals(100.0, r[0].close, 1e-9)
        assertEquals(98.0, r[1].close, 1e-9)
        assertEquals(110.0, r[2].close, 1e-9)
        assertEquals(105.0 * 1.1, r[3].close, 1e-9)
    }

    @Test fun gesamtrendite() {
        val d = kerzen(100.0, 100.0, 100.0, 100.0)
        val ang = Analysis.thesaurierend(d, listOf(MarketApi.DividendEvent(2 * tag, 10.0)))
        val mit = Analysis.lumpSum(ang, 1_000.0, 0, null, d.last().t)!!
        val ohne = Analysis.lumpSum(d, 1_000.0, 0, null, d.last().t)!!
        assertEquals(1_100.0, mit.value, 1e-6)
        assertEquals(1_000.0, ohne.value, 1e-6)
    }

    @Test fun zweiDividendenKumulativ() {
        val d = kerzen(100.0, 100.0, 100.0, 100.0, 100.0)
        val r = Analysis.thesaurierend(d, listOf(MarketApi.DividendEvent(1 * tag, 10.0), MarketApi.DividendEvent(3 * tag, 5.0)))
        assertEquals(100.0, r[0].close, 1e-9)
        assertEquals(100.0 * 1.1, r[1].close, 1e-9)
        assertEquals(100.0 * 1.1, r[2].close, 1e-9)
        assertEquals(100.0 * 1.1 * 1.05, r[3].close, 1e-9)
        assertEquals(100.0 * 1.1 * 1.05, r[4].close, 1e-9)
    }

    @Test fun exTagVorDerErstenKerzeZaehltNicht() {
        val d = kerzen(100.0, 100.0, 100.0)
        assertEquals(d, Analysis.thesaurierend(d, listOf(MarketApi.DividendEvent(-1 * tag, 10.0))))
    }

    @Test fun betragNullOderNegativIgnoriert() {
        val d = kerzen(100.0, 100.0, 100.0)
        assertEquals(d, Analysis.thesaurierend(d, listOf(MarketApi.DividendEvent(1 * tag, 0.0), MarketApi.DividendEvent(1 * tag, -5.0))))
    }

    @Test fun sparplan() {
        val d = kerzen(*(0..90).map { 100.0 }.toDoubleArray())
        val ang = Analysis.thesaurierend(d, listOf(MarketApi.DividendEvent(30 * tag, 10.0)))
        val now = 90 * tag
        val mit = Analysis.savingsPlan(ang, 100.0, 0, null, now, ZoneOffset.UTC)!!
        val ohne = Analysis.savingsPlan(d, 100.0, 0, null, now, ZoneOffset.UTC)!!
        assertEquals(ohne.invested, mit.invested, 1e-9)
        assertTrue(mit.value > ohne.value)
    }
}
