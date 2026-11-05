package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Rss
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// MarketWatch (5.95): kein automatisches Auslesen. Die Seite laedt als Original, Text nur nach dem Laden auf Tipp.
class Fassung595MarketWatchTest {
    private val link = "https://www.marketwatch.com/story/beispiel-123"

    @Test
    fun marketWatchOhneLesemodus() {
        assertTrue(Rss.ohneLesemodus(link))
        assertTrue(Rss.ohneLesemodus("https://news.google.com/rss/articles/abc", "MarketWatch"))
        assertFalse(Lesemodus.automatisch(link))
        assertFalse(Lesemodus.automatisch("https://news.google.com/rss/articles/abc", "MarketWatch"))
        assertTrue(Lesemodus.automatisch("https://www.example.com/artikel"))
    }

    @Test
    fun textErstNachDemLadenAufTipp() {
        // Waehrend die Seite laedt: kein Knopf und kein automatisches Lesen
        assertEquals(Lesemodus.Knopf.KEINER, Lesemodus.knopf(webAnsicht = true, adresse = true, original = true, geladen = false,
            text = false, automatisch = Lesemodus.automatisch(link), laedt = false, liest = false, gelesen = false))
        // Nach dem Laden: „Nur Text anzeigen“, der Tipp liest aus der geladenen Seite
        assertEquals(Lesemodus.Knopf.NUR_TEXT, Lesemodus.knopf(webAnsicht = true, adresse = true, original = true, geladen = true,
            text = false, automatisch = Lesemodus.automatisch(link), laedt = false, liest = false, gelesen = false))
        assertTrue(Lesemodus.ausSeiteLesen(text = false, geladen = true))
    }
}
