package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Skala der Bewegung zu den Zahlen: Lage von Band und Marke, Stauchung.
class Fassung077LTest {
    @Test fun nahe_marke_bleibt_linear() {
        val s = BewegungsSkala.skala(2.0, 3.0)
        assertFalse(s.gestaucht)
        assertEquals(3.0 * 1.15, s.halb, 1e-9)
        assertEquals(2.0 / s.halb, s.lage(2.0), 1e-9)
        assertEquals(3.0 / s.halb, s.lage(3.0), 1e-9)
    }

    @Test fun ferne_marke_staucht_das_band_bleibt_lesbar() {
        val s = BewegungsSkala.skala(1.5, -12.6)
        assertTrue(s.gestaucht)
        assertEquals(BewegungsSkala.MIN_BAND, s.lage(1.5), 1e-9)
        assertEquals(-BewegungsSkala.MIN_BAND, s.lage(-1.5), 1e-9)
        // Marke liegt klar ausserhalb des Bands und noch innerhalb der Achse
        val m = s.lage(-12.6)
        assertTrue(m < -BewegungsSkala.MIN_BAND && m > -1.0)
    }

    @Test fun lage_ist_monoton_und_gespiegelt() {
        val s = BewegungsSkala.skala(1.5, 40.0)
        var vor = 0.0
        for (w in listOf(0.5, 1.5, 3.0, 10.0, 30.0, 46.0, 80.0)) {
            val l = s.lage(w)
            assertTrue(l >= vor)
            assertTrue(l <= 1.0)
            assertEquals(-l, s.lage(-w), 1e-12)
            vor = l
        }
        assertEquals(0.0, s.lage(0.0), 1e-12)
        assertEquals(1.0, s.lage(s.halb), 1e-9)
    }

    @Test fun ohne_marke_zeigt_das_band_breit() {
        val s = BewegungsSkala.skala(1.5, null)
        assertFalse(s.gestaucht)
        assertEquals(0.8, s.bandAnteil, 1e-9)
    }

    @Test fun marke_im_band_staucht_nicht() {
        val s = BewegungsSkala.skala(4.0, 1.0)
        assertFalse(s.gestaucht)
        assertTrue(s.lage(1.0) < s.lage(4.0))
    }

    @Test fun abweichung_vom_erwarteten_gewinn() {
        assertEquals(-29.629, BewegungsSkala.abweichung(1.90, 2.70)!!, 1e-2)
        assertEquals(-50.0, BewegungsSkala.abweichung(-1.5, -1.0)!!, 1e-9)
        assertNull(BewegungsSkala.abweichung(1.0, 0.0))
    }
}
