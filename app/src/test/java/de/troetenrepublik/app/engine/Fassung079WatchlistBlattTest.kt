package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Seite des Pagers und Watchlist im Watchlist-Blatt.
class Fassung079WatchlistBlattTest {

    @Test
    fun aktivBleibtInDenGrenzen() {
        assertEquals(0, WatchBlatt.aktiv(-3, 2))
        assertEquals(1, WatchBlatt.aktiv(1, 2))
        assertEquals(1, WatchBlatt.aktiv(7, 2))
        assertEquals(0, WatchBlatt.aktiv(5, 0))
    }

    @Test
    fun plusIstKeineSeite() {
        assertEquals(0, WatchBlatt.seiteFuerReiter(0, 2))
        assertEquals(1, WatchBlatt.seiteFuerReiter(1, 2))
        assertNull(WatchBlatt.seiteFuerReiter(2, 2))
        assertNull(WatchBlatt.seiteFuerReiter(-1, 2))
    }

    @Test
    fun langsamRastetAufDerNaehestenSeiteEin() {
        assertEquals(0, WatchBlatt.zielSeite(0, 0.3f, 0f, 3))
        assertEquals(1, WatchBlatt.zielSeite(0, 0.6f, 0f, 3))
        assertEquals(1, WatchBlatt.zielSeite(1, -0.3f, 0f, 3))
        assertEquals(0, WatchBlatt.zielSeite(1, -0.6f, 0f, 3))
    }

    @Test
    fun schnellesWischenSpringtZurNachbarseite() {
        assertEquals(1, WatchBlatt.zielSeite(0, 0.1f, 900f, 3))
        assertEquals(1, WatchBlatt.zielSeite(1, -0.1f, 900f, 3))
        assertEquals(1, WatchBlatt.zielSeite(1, 0.1f, -900f, 3))
        assertEquals(0, WatchBlatt.zielSeite(1, -0.1f, -900f, 3))
        assertEquals(2, WatchBlatt.zielSeite(1, 0f, 900f, 3))
        assertEquals(0, WatchBlatt.zielSeite(1, 0f, -900f, 3))
    }

    @Test
    fun nieUeberDieEndenHinaus() {
        assertEquals(0, WatchBlatt.zielSeite(0, 0f, -900f, 3))
        assertEquals(2, WatchBlatt.zielSeite(2, 0f, 900f, 3))
        assertEquals(0, WatchBlatt.zielSeite(0, -0.4f, 0f, 1))
        assertEquals(0, WatchBlatt.zielSeite(0, 0f, 900f, 0))
    }
}
