package de.troetenrepublik.app.ui

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Range
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Ziehen durch den Chart, Zeitachse des Tagescharts.
class ChartScrubTest {

    private fun c(t: Long, v: Double = 100.0) = Candle(t, v, v, v, v)

    // Freitag, 25.09.2026: Handelsfenster einer US-Aktie von 10:00 bis 2:00 Uhr Berliner Zeit (8:00 bis 24:00 UTC)
    private val start = 1_790_323_200_000L
    private val end = 1_790_380_800_000L
    private val day = (0 until 480).map { c(start + it * 120_000L) }

    @Test
    fun dayAxisIsTheTradingWindowAndIgnoresTheClock() {
        // Nach Handelsschluss (Samstagnachmittag) bleibt die Achse das Fenster des Freitags und haengt nicht an der
        // aktuellen Uhrzeit, sonst braeche das Ziehen ab.
        assertEquals(start to end, dayAxis(Range.D1, start, end, day))
        // Der Live-Punkt der Depotkurve liegt spaeter; die Achse bleibt dieselbe, er steht am rechten Rand.
        val withLive = day + c(end + 13 * 3_600_000L)
        assertEquals(start to end, dayAxis(Range.D1, start, end, withLive))
        assertEquals(dayAxis(Range.D1, start, end, withLive), dayAxis(Range.D1, start, end, withLive))
    }

    @Test
    fun dayChartStartsAtTheFirstPrice() {
        // Erster Kurs eine Stunde nach Beginn des Fensters (ETF an der Zweitnotiz): die Linie beginnt ganz links
        val spaet = day.drop(30)
        assertEquals(spaet.first().t to end, dayAxis(Range.D1, start, end, spaet))
    }

    @Test
    fun dayAxisOnlyForTheDayChartWithPricesInTheWindow() {
        assertNull(dayAxis(Range.W1, start, end, day))
        assertNull(dayAxis(Range.D1, 0L, end, day))
        assertNull(dayAxis(Range.D1, end, start, day))
        assertNull(dayAxis(Range.D1, start, end, day.take(1)))
        // Yahoo meldet schon das Fenster des naechsten Handelstags, die Kurse sind noch vom letzten:
        // dann gleichmaessig verteilt statt alle Punkte am linken Rand
        val nextStart = start + 3 * 86_400_000L
        assertNull(dayAxis(Range.D1, nextStart, nextStart + 57_600_000L, day))
        assertNull(dayAxis(Range.D1, start - 86_400_000L, start - 3_600_000L, day))
    }

    @Test
    fun fingerFindsTheNearestPoint() {
        val xs = floatArrayOf(0f, 10f, 20f, 30f)
        assertEquals(0, nearestX(xs, -5f))
        assertEquals(0, nearestX(xs, 4f))
        assertEquals(1, nearestX(xs, 6f))
        assertEquals(1, nearestX(xs, 15f))      // genau dazwischen: der fruehere
        assertEquals(2, nearestX(xs, 15.1f))
        assertEquals(3, nearestX(xs, 29f))
        assertEquals(3, nearestX(xs, 400f))     // rechts ueber den Rand hinaus: der letzte
        assertEquals(-1, nearestX(FloatArray(0), 3f))
        // Mehrere Punkte an derselben Stelle (am Rand festgehalten): links davon der erste, rechts davon der letzte
        val clamped = floatArrayOf(0f, 50f, 100f, 100f)
        assertEquals(2, nearestX(clamped, 99f))
        assertEquals(3, nearestX(clamped, 101f))
    }

    @Test
    fun fingerFollowsEveryPointOfTheDay() {
        // Bei 1080 Pixeln Breite liegen 480 Punkte gut 2 Pixel auseinander. Ein Zug ueber die ganze Breite
        // Pixel fuer Pixel trifft jeden Punkt der Reihe, keiner wird uebersprungen.
        val w = 1080f
        val xs = FloatArray(day.size) { ((day[it].t - start).toFloat() / (end - start)) * w }
        val hit = HashSet<Int>()
        var last = -1
        for (px in 0..1080) {
            val i = nearestX(xs, px.toFloat())
            assertTrue("rueckwaerts bei $px", i >= last)
            last = i
            hit += i
        }
        assertEquals(day.size, hit.size)
    }

    @Test
    fun crosshairFindsTheNearestPointInTime() {
        val list = listOf(c(1_000L), c(2_000L), c(4_000L))
        assertEquals(0, nearestIndex(list, 0L))
        assertEquals(1, nearestIndex(list, 2_000L))
        assertEquals(1, nearestIndex(list, 2_900L))
        assertEquals(2, nearestIndex(list, 3_100L))
        // Der Live-Punkt traegt nach neuen Kursen eine neue Uhrzeit: der alte bleibt am Ende
        assertEquals(2, nearestIndex(list, 9_000L))
        assertEquals(-1, nearestIndex(emptyList(), 5L))
    }
}
