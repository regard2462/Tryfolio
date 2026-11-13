package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.SeitenFehler.Art
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Fassung077N1SeitenFehlerTest {
    @Test
    fun statusWirdZurArtZugeordnet() {
        for (s in listOf(401, 403, 451, 400, 406)) assertEquals(s.toString(), Art.ABGELEHNT, SeitenFehler.art(s))
        assertEquals(Art.WEG, SeitenFehler.art(404))
        assertEquals(Art.WEG, SeitenFehler.art(410))
        assertEquals(Art.ZU_OFT, SeitenFehler.art(429))
        assertEquals(Art.NICHT_ERREICHBAR, SeitenFehler.art(500))
        assertEquals(Art.NICHT_ERREICHBAR, SeitenFehler.art(503))
        assertEquals(Art.LADEFEHLER, SeitenFehler.art(0))
    }

    @Test
    fun erfolgUndUmleitungSindKeinFehler() {
        for (s in listOf(200, 204, 301, 304, 399, 600, -1)) assertNull(s.toString(), SeitenFehler.art(s))
        assertFalse(SeitenFehler.melden(200, "https://www.example.org/a"))
        assertFalse(SeitenFehler.melden(302, "https://www.example.org/a"))
    }

    @Test
    fun zeitungsseiteMeldetImmer() {
        assertTrue(SeitenFehler.melden(403, "https://www.example.org/artikel"))
        assertTrue(SeitenFehler.melden(503, "https://www.example.org/artikel"))
        assertTrue(SeitenFehler.melden(0, "https://www.example.org/artikel"))
        assertTrue(SeitenFehler.melden(403, null))
    }

    @Test
    fun zwischenseitenMeldenNurLadefehler() {
        assertFalse(SeitenFehler.melden(403, "https://news.google.com/rss/articles/CBMi"))
        assertFalse(SeitenFehler.melden(429, "https://consent.google.com/m?continue=https://news.google.com"))
        assertTrue(SeitenFehler.melden(0, "https://news.google.com/rss/articles/CBMi"))
    }

    @Test
    fun vpnZeileNurBei403() {
        assertTrue(SeitenFehler.mitVpnZeile(403))
        for (s in listOf(401, 404, 429, 451, 503, 0)) assertFalse(SeitenFehler.mitVpnZeile(s))
    }
}
