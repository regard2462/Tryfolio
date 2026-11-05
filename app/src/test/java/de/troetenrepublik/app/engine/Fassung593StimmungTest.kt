package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Stimmung auf r/wallstreetbets mit klarer Richtung und Anlass sowie das VIX-Barometer (5.93), erfundene Werte.
class Fassung593StimmungTest {

    private fun w(ticker: String, punkte: Double, kommentare: Int) =
        Stimmungsbarometer.WsbWert(ticker, punkte >= Stimmungsbarometer.WSB_KLAR, punkte, kommentare)

    @Test
    fun ohneKlareRichtung() {
        val werte = (1..8).map { w("T$it", 0.02, 100) }
        val r = Stimmungsbarometer.wsbMitLage(werte, emptyMap(), 1L)!!
        assertTrue(r.lage!!.unklar)
        assertEquals(50.0, r.barometer.wert, 1e-9)
    }

    @Test
    fun klarSteigend() {
        val werte = listOf(
            w("A", 0.3, 120), w("B", 0.3, 120), w("C", 0.3, 120),
            w("D", -0.3, 40), w("E", -0.3, 40), w("F", -0.3, 40),
        )
        val r = Stimmungsbarometer.wsbMitLage(werte, emptyMap(), 1L)!!
        assertTrue(!r.lage!!.unklar)
        assertEquals(75.0, r.barometer.wert, 1e-9)
    }

    @Test
    fun anlassFall() {
        val werte = listOf(
            w("A", 0.3, 300), w("B", -0.3, 100), w("C", 0.3, 100),
            w("D", -0.3, 100), w("E", 0.3, 100), w("F", -0.3, 100),
        )
        val r = Stimmungsbarometer.wsbMitLage(werte, mapOf("A" to -25.0, "B" to 4.0), 1L)!!
        assertEquals("A", r.lage!!.anlass!!.ticker)
        assertEquals(-25.0, r.lage.anlass!!.aenderung, 1e-9)
        // Ein Wert mit +4 % ist kein Anlass
        val ohne = Stimmungsbarometer.wsbMitLage(werte, mapOf("B" to 4.0), 1L)!!
        assertNull(ohne.lage!!.anlass)
    }

    @Test
    fun zuWenigeKlareWerte() {
        val werte = listOf(
            w("A", 0.3, 100), w("B", -0.3, 100), w("C", 0.3, 100), w("D", -0.3, 100),
            w("E", 0.02, 10), w("F", 0.02, 10),
        )
        val r = Stimmungsbarometer.wsbMitLage(werte, emptyMap(), 1L)!!
        assertTrue(r.lage!!.unklar)
    }

    @Test
    fun ohneKommentareNull() {
        assertNull(Stimmungsbarometer.wsbMitLage(emptyList(), emptyMap(), 1L))
        assertNull(Stimmungsbarometer.wsbMitLage(listOf(w("A", 0.3, 0)), emptyMap(), 1L))
    }

    @Test
    fun aenderungUeberFuenfTage() {
        val kurse = listOf(100.0, 101.0, 102.0, 103.0, 104.0, 75.0).mapIndexed { i, k -> i.toLong() to k }
        assertEquals(-25.0, Stimmungsbarometer.aenderung(kurse)!!, 1e-9)
        assertNull(Stimmungsbarometer.aenderung(listOf(0L to 100.0, 1L to 75.0)))
    }

    @Test
    fun vixBarometerWerte() {
        val steigend = (0 until 120).map { it.toLong() to (10.0 + it) }
        val hoch = Stimmungsbarometer.vixBarometer(steigend)!!
        assertTrue(hoch.wert < 5)
        assertEquals(Stimmungsbarometer.Stufe.EXTREME_ANGST, hoch.stufe)
        assertEquals(129.0, hoch.teile.first().wert, 1e-9)
        val fallend = (0 until 120).map { it.toLong() to (130.0 - it) }
        assertEquals(100.0, Stimmungsbarometer.vixBarometer(fallend)!!.wert, 1e-9)
        assertNull(Stimmungsbarometer.vixBarometer((0 until 50).map { it.toLong() to (10.0 + it) }))
    }
}
