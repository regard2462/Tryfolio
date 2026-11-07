package de.troetenrepublik.app.desktop

import de.troetenrepublik.app.plattform.WebBaustein
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// MarketWatch am Rechner: ohne offenes Fenster liest die App nichts; der Text kommt nur aus dem Artikelfenster.
class WebBausteinTest {
    @Test
    fun ohneFensterKeinText() {
        assertFalse(WebBaustein.artikelGeladen)
        assertNull(runBlocking { WebBaustein.artikelText() })
    }

    @Test
    fun marketWatchImFenster() {
        assertTrue(WebBaustein.noetig("https://www.marketwatch.com/story/beispiel-123"))
        assertFalse(WebBaustein.noetig("https://www.example.com/artikel"))
    }
}
