package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Optionsanalyse.Kontrakt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 5.94: Stillhalter-Rechner fuer gedeckte Calls und Puts.
class Fassung594StillhalterTest {

    private val jetzt = 1_800_000_000L
    private fun tag(t: Int) = jetzt + t * 86_400L
    private fun k(call: Boolean, strike: Double, verfall: Long, geld: Double, iv: Double = 0.3) =
        Kontrakt(call, strike, verfall, geld, geld, geld + 0.1, 10, 100, iv)

    @Test fun verfallUndRendite() {
        val l = listOf(
            k(true, 105.0, tag(7), 1.0), k(true, 105.0, tag(35), 2.0), k(true, 110.0, tag(35), 1.0), k(true, 120.0, tag(35), 0.2),
            k(true, 95.0, tag(35), 6.0), k(false, 95.0, tag(35), 1.9), k(false, 80.0, tag(35), 0.1),
        )
        val s = Optionsanalyse.stillhalter(100.0, l, jetzt)!!
        assertEquals(tag(35), s.verfall)
        assertEquals(35, s.tage)
        // Nur aus dem Geld und hoechstens 15 % entfernt
        assertEquals(listOf(105.0, 110.0), s.calls.map { it.strike })
        assertEquals(listOf(95.0), s.puts.map { it.strike })
        val c = s.calls.first()
        assertEquals(0.02, c.rendite, 1e-12)
        assertEquals(0.02 * 365 / 35, c.jahr, 1e-12)
        assertEquals(0.07, c.ausgeuebt!!, 1e-12)
        assertEquals(98.0, c.schwelle, 1e-12)
        val p = s.puts.single()
        assertEquals(1.9 / 95, p.rendite, 1e-12)
        assertEquals(93.1, p.schwelle, 1e-12)
        // Weiter aus dem Geld: kleinere Ausuebungs-Chance
        assertTrue(s.calls[0].wahrscheinlich!! > s.calls[1].wahrscheinlich!!)
        assertTrue(c.wahrscheinlich!! in 0.2..0.5)
    }

    @Test fun ohneBrauchbareDaten() {
        assertNull(Optionsanalyse.stillhalter(100.0, listOf(k(true, 105.0, tag(5), 1.0)), jetzt))
        val s = Optionsanalyse.stillhalter(100.0, listOf(k(true, 105.0, tag(20), 1.0, iv = 0.0)), jetzt)
        assertNotNull(s)
        assertNull(s!!.calls.single().wahrscheinlich)
    }
}
