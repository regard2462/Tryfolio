package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

// Indikatoren des erweiterten Charts.
class ChartIndikatorenTest {
    private val tag = 86_400_000L

    private fun reihe(n: Int, start: Double = 100.0, schritt: Double = 1.0) =
        List(n) { i -> val c = start + i * schritt; Candle(i * tag, c - schritt / 2, c + 0.5, c - 1.0, c, 1000.0) }

    private fun zufall(n: Int, seed: Int = 7): List<Candle> {
        val r = Random(seed)
        var c = 100.0
        return List(n) { i ->
            val o = c
            c = (c * (1 + (r.nextDouble() - 0.5) * 0.04)).coerceAtLeast(1.0)
            Candle(i * 3_600_000L, o, maxOf(o, c) * 1.005, minOf(o, c) * 0.995, c, 500.0 + r.nextDouble() * 1000)
        }
    }

    @Test fun alleIndikatorenLiefernJeKerzeEinenWert() {
        for (n in listOf(0, 1, 5, 30, 300)) {
            val k = zufall(n)
            ChartIndikatoren.Indikator.entries.forEach { i ->
                val e = ChartIndikatoren.rechnen(i, k) { it / tag }
                assertTrue("$i ohne Linie", e.linien.isNotEmpty())
                e.linien.forEach { l -> assertEquals("$i bei $n Kerzen", n, l.werte.size) }
            }
        }
    }

    @Test fun oszillatorenBleibenInIhremBereich() {
        val k = zufall(400)
        val h = k.map { it.high }; val l = k.map { it.low }; val c = k.map { it.close }; val v = k.map { it.volume }
        Indikatoren.williamsR(h, l, c).filterNotNull().forEach { assertTrue(it in -100.0..0.0) }
        Indikatoren.mfi(h, l, c, v).filterNotNull().forEach { assertTrue(it in 0.0..100.0) }
        Indikatoren.cmf(h, l, c, v).filterNotNull().forEach { assertTrue(it in -1.0..1.0) }
        Indikatoren.adx(h, l, c).let { a -> (a.adx + a.plus + a.minus).filterNotNull().forEach { assertTrue(it in 0.0..100.0) } }
        Indikatoren.aroon(h, l).let { a -> (a.auf + a.ab).filterNotNull().forEach { assertTrue(it in 0.0..100.0) } }
    }

    @Test fun steigenderKursErgibtDieErwartetenWerte() {
        val k = reihe(80)
        val h = k.map { it.high }; val l = k.map { it.low }; val c = k.map { it.close }; val v = k.map { it.volume }
        // Neues Hoch an jeder Kerze
        assertEquals(100.0, Indikatoren.aroon(h, l).auf.last()!!, 1e-9)
        assertEquals(0.0, Indikatoren.aroon(h, l).ab.last()!!, 1e-9)
        assertEquals(100.0, Indikatoren.mfi(h, l, c, v).last()!!, 1e-9)
        Indikatoren.adx(h, l, c).let { a -> assertTrue(a.plus.last()!! > a.minus.last()!!); assertTrue(a.adx.last()!! > 25) }
        assertTrue(Indikatoren.cci(h, l, c).last()!! > 0)
        // ROC: 12 Kerzen zurueck
        assertEquals((c.last() / c[c.size - 13] - 1) * 100, Indikatoren.roc(c).last()!!, 1e-9)
        assertNull(Indikatoren.roc(c)[11])
    }

    @Test fun kanaeleUmschliessenDieMitte() {
        val k = zufall(120)
        val h = k.map { it.high }; val l = k.map { it.low }; val c = k.map { it.close }
        listOf(Indikatoren.keltner(h, l, c), Indikatoren.donchian(h, l)).forEach { b ->
            b.mitte.indices.forEach { i ->
                val m = b.mitte[i] ?: return@forEach
                assertTrue(b.oben[i]!! >= m && b.unten[i]!! <= m)
            }
        }
        val d = Indikatoren.donchian(h, l)
        assertEquals(h.subList(100, 120).max(), d.oben.last()!!, 1e-12)
    }

    @Test fun vwapBeginntJedenTagNeu() {
        val k = listOf(
            Candle(0, 10.0, 10.0, 10.0, 10.0, 100.0), Candle(1, 20.0, 20.0, 20.0, 20.0, 100.0),
            Candle(tag, 30.0, 30.0, 30.0, 30.0, 50.0),
        )
        val w = Indikatoren.vwap(k) { it / tag }
        assertEquals(10.0, w[0]!!, 1e-9)
        assertEquals(15.0, w[1]!!, 1e-9)
        assertEquals(30.0, w[2]!!, 1e-9)
    }

    @Test fun heikinAshiGlaettet() {
        val k = listOf(Candle(0, 10.0, 12.0, 9.0, 11.0), Candle(1, 11.0, 13.0, 10.0, 12.0))
        val ha = Indikatoren.heikinAshi(k)
        assertEquals(10.5, ha[0].open, 1e-9)
        assertEquals(10.5, ha[0].close, 1e-9)
        assertEquals((10.5 + 10.5) / 2, ha[1].open, 1e-9)
        assertEquals(11.5, ha[1].close, 1e-9)
        assertTrue(ha.all { it.high >= maxOf(it.open, it.close) && it.low <= minOf(it.open, it.close) })
    }

    @Test fun zeichnungenUeberlebenSpeichernUndLesen() {
        val z = listOf(Zeichnung(ChartZeichnungen.LINIE, 1L, 10.0, 2L, 12.0), Zeichnung(ChartZeichnungen.EMOJI, 3L, 11.0, text = "🚀"))
        val text = ChartZeichnungen.schreiben(ChartZeichnungen.setzen(emptyMap(), "SAP.DE", z))
        assertEquals(z, ChartZeichnungen.lesen(text)["SAP.DE"])
        assertTrue(ChartZeichnungen.lesen("kaputt").isEmpty())
        assertTrue(ChartZeichnungen.setzen(ChartZeichnungen.lesen(text), "SAP.DE", emptyList()).isEmpty())
    }
}
