package de.troetenrepublik.app.market

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WichtigEnglischTest {
    private fun en(titel: String, link: String = "https://example.com/a") = Wichtig.englisch(Rss.Eintrag(titel, link, 1L))

    @Test
    fun spracheDerSchlagzeile() {
        assertTrue(en("Fed holds rates steady as inflation cools"))
        assertTrue(en("Nvidia shares jump after record quarter"))
        assertTrue(en("Bitcoin tops 120,000", "https://www.coindesk.com/markets/x"))
        assertFalse(en("Ölpreis steigt kräftig"))
        assertFalse(en("Die EZB senkt den Leitzins erneut"))
        assertFalse(en("XRP: Bewegung voraus", "https://www.wallstreet-online.de/nachricht/1"))
        assertFalse(en("Nvidia-Rally", "https://de.investing.com/news/1"))
    }

    @Test
    fun auswahlNurEnglisch() {
        val liste = listOf(
            Rss.Eintrag("Zinsentscheid: Fed senkt den Leitzins", "https://www.tagesschau.de/a", 1_000L, herausgeber = "tagesschau.de"),
            Rss.Eintrag("Fed cuts interest rates for the first time this year", "https://www.bbc.co.uk/a", 1_000L, herausgeber = "BBC"),
        )
        val alle = Wichtig.wichtige(liste)
        val englisch = Wichtig.wichtige(liste, nurEnglisch = true)
        assertTrue(englisch.all { id -> liste.first { it.id == id }.link.contains("bbc") })
        assertTrue(englisch.size <= alle.size)
    }
}
