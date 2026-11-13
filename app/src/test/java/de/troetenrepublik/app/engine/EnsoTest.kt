package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// ENSO: Status aus der Diskussion des NOAA CPC und der Index RONI.
class EnsoTest {
    // Gekuerzter echter Ausschnitt der Seite ensodisc.shtml vom 7.11.2026
    private val seite = """<tr><td><p align="center"><font face="verdana,arial,serif" size="3">
<strong>ENSO Alert System Status: </font>
<a href="./enso-alert-readme.shtml" class="homepagelinks"><font face="verdana,arial,serif" size="2">
<span style="color:red">El Ni&ntilde;o Advisory</span></strong></font></a></p></td></tr>
<tr><td><u>Synopsis:</u>&nbsp;<strong>El Ni&ntilde;o continues to strengthen, with a strong-to-very strong El Ni&ntilde;o likely through January-March 2027.</strong>"""

    private val roni = """SEAS   YR  ANOM
DJF  1950 -1.19
JFM  1950 -1.08
MJJ  2026  1.14
JJA  2026  1.36
JAS  2026  1.69
ASO  2026  1.97
"""

    @Test
    fun statusElNinoAdvisory() {
        assertEquals("EL_NINO_ADVISORY", Enso.statusLesen(seite, 1L)!!.zustand)
    }

    @Test
    fun statusAusAnderenStufen() {
        fun s(t: String) = Enso.statusLesen("<strong>ENSO Alert System Status: </strong><span>$t</span> Synopsis: El Ni&ntilde;o", 1L)?.zustand
        assertEquals("LA_NINA_WATCH", s("La Ni&ntilde;a Watch"))
        assertEquals("EL_NINO_WATCH", s("El Ni&ntilde;o Watch"))
        assertEquals("NEUTRAL", s("Not Active"))
        assertNull(Enso.statusLesen("<html>nichts</html>", 1L))
    }

    @Test
    fun indexLesen() {
        val i = Enso.indexLesen(roni, 1L)!!
        assertEquals(6, i.punkte.size)
        assertEquals("ASO", i.punkte.last().saison)
        assertEquals(2026, i.punkte.last().jahr)
        assertEquals(1.97, i.punkte.last().wert, 1e-9)
        assertNull(Enso.indexLesen("nur Kopf", 1L))
    }

    @Test
    fun staerkeStufen() {
        assertEquals(4, Enso.staerke(1.97 + 0.03))
        assertEquals(3, Enso.staerke(1.5))
        assertEquals(2, Enso.staerke(1.0))
        assertEquals(1, Enso.staerke(0.5))
        assertEquals(0, Enso.staerke(0.49))
        assertEquals(-3, Enso.staerke(-1.6))
    }
}
