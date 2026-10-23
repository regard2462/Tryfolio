package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

// Regressionstests: Einzelausreisser in Intraday-Kursen, Depotkurve in voller Aufloesung.
class TicksTest {

    private fun series(vararg closes: Double) = closes.mapIndexed { i, v -> Candle(i * 120_000L, v, v, v, v) }

    @Test
    fun appleAfterHoursTicksAreRemoved() {
        // Yahoo, Apple am 25.09.2026 nachboerslich (UTC 21:24 bis 21:36): zwei Fehlkurse ohne Umsatz
        val raw = series(341.20, 341.10, 328.58, 341.30, 341.25, 341.19, 327.31, 341.138, 341.10)
        val clean = Ticks.despike(raw)
        assertEquals(listOf(341.20, 341.10, 341.30, 341.25, 341.19, 341.138, 341.10), clean.map { it.close })
        // Die uebrigen Punkte behalten ihre Uhrzeit
        assertEquals(listOf(0L, 1L, 3L, 4L, 5L, 7L, 8L).map { it * 120_000L }, clean.map { it.t })
    }

    @Test
    fun twoPointOutliersInBothDirections() {
        assertEquals(listOf(100.0, 100.1, 100.0), Ticks.despike(series(100.0, 104.0, 104.2, 100.1, 100.0)).map { it.close })
        assertEquals(listOf(50.0, 50.05, 50.1), Ticks.despike(series(50.0, 47.0, 50.05, 50.1)).map { it.close })
    }

    @Test
    fun realMovesStay() {
        // Sprung, der bleibt (Nachricht, Eroeffnung): kein Ausreisser
        val jump = series(100.0, 100.2, 106.0, 106.3, 106.1)
        assertSame(jump, Ticks.despike(jump))
        // Normales Rauschen unter der Schwelle
        val noise = series(100.0, 100.8, 99.6, 100.9, 99.9, 100.4)
        assertSame(noise, Ticks.despike(noise))
        // Zacke, nach der der Kurs nicht zurueckkommt, sondern auf halbem Weg bleibt
        val half = series(100.0, 104.0, 102.0, 102.1)
        assertSame(half, Ticks.despike(half))
        // Drei Punkte weit weg sind eine Bewegung, keine Einzelausreisser
        val plateau = series(100.0, 104.0, 104.1, 104.0, 100.0)
        assertSame(plateau, Ticks.despike(plateau))
        // Erster und letzter Punkt bleiben immer (der letzte ist oft der Live-Kurs)
        val ends = series(90.0, 100.0, 100.1, 100.0, 108.0)
        assertSame(ends, Ticks.despike(ends))
        assertEquals(2, Ticks.despike(series(1.0, 200.0)).size)
    }

    @Test
    fun flowNeutralMatchesTheDirectSum() {
        // Die sortierte Summe der spaeteren Zufluesse liefert dasselbe wie die Summe je Punkt
        val rnd = Random(21)
        repeat(50) {
            val candles = (0 until 300).map { i -> val v = 5_000.0 + rnd.nextDouble(-800.0, 800.0); Candle(i * 60_000L, v, v, v, v) }
            val flows = (0 until rnd.nextInt(1, 40)).map { rnd.nextLong(-600_000L, 19_000_000L) to rnd.nextDouble(-1_500.0, 2_000.0) }
            val direct = candles.map { c -> flows.sumOf { (ts, f) -> if (ts > c.t) f else 0.0 } }
            val adjusted = Trading.flowNeutral(candles, flows)
            if (candles.first().close + direct.first() > 0.0) {
                for (k in candles.indices) assertEquals(candles[k].close + direct[k], adjusted[k].close, 1e-6)
            } else {
                assertSame(candles, adjusted)
            }
        }
        // Zufluss genau zum Zeitpunkt eines Punkts zaehlt erst danach
        val c = listOf(Candle(1L, 10.0, 10.0, 10.0, 10.0), Candle(2L, 20.0, 20.0, 20.0, 20.0))
        val out = Trading.flowNeutral(c, listOf(2L to 10.0, 1L to 5.0))
        assertEquals(20.0, out[0].close, 1e-9)
        assertEquals(20.0, out[1].close, 1e-9)
        assertTrue(out[1] === c[1])
    }
}
