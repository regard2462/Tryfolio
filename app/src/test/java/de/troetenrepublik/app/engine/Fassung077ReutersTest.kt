package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Settings
import de.troetenrepublik.app.market.Rss
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Fassung077ReutersTest {

    private val google = """<?xml version="1.0" encoding="UTF-8"?><rss version="2.0"><channel><title>site:reuters.com</title>
<item><title>Oil falls as demand worries grow - Reuters</title><link>https://news.google.com/rss/articles/AAA</link>
<pubDate>Wed, 11 Nov 2026 09:00:00 GMT</pubDate><source url="https://www.reuters.com">Reuters</source></item>
<item><title>Oil falls on Reuters data - Yahoo Finance</title><link>https://news.google.com/rss/articles/BBB</link>
<pubDate>Wed, 11 Nov 2026 08:00:00 GMT</pubDate><source url="https://finance.yahoo.com">Yahoo Finance</source></item>
</channel></rss>"""

    private val reuters get() = Rss.eingebaut(deutsch = false).first { it.schluessel == "reuters" }

    @Test
    fun searchRestrictedToReutersInAppLanguage() {
        assertTrue(Rss.reutersAdresse(false).contains("site%3Areuters.com") || Rss.reutersAdresse(false).contains("site:reuters.com"))
        assertTrue(Rss.reutersAdresse(false).contains("hl=en-US"))
        assertTrue(Rss.reutersAdresse(true).contains("hl=de"))
        assertEquals(Rss.reutersAdresse(true), Rss.eingebaut(deutsch = true).first { it.schluessel == "reuters" }.adresse)
        assertEquals(Rss.reutersAdresse(false), reuters.adresse)
    }

    @Test
    fun sourceIsGoogleSearchWithHalfHourCache() {
        assertTrue(reuters.google)
        assertEquals(Rss.Anbieter.REUTERS, reuters.anbieter)
        assertTrue(reuters.abstandMs >= 30 * 60_000L)
        assertTrue(reuters.adresse.startsWith("https://news.google.com/rss/search"))
    }

    @Test
    fun onlyReutersHitsRemain() {
        val roh = Rss.lesen(google, "Reuters", Rss.Kategorie.MAERKTE, google = true, schluessel = "reuters")
        assertEquals(2, roh.size)
        val nur = Rss.nurHerausgeber(roh, reuters.nurHerausgeber)
        assertEquals(listOf("Oil falls as demand worries grow"), nur.map { it.titel })
        assertEquals("Reuters", nur.single().quellenText)
    }

    @Test
    fun offByDefaultAndSwitchable() {
        assertTrue("reuters" in Settings().quellenAus)
        val an = Rss.schalten("", "reuters", true)
        assertFalse("reuters" in Rss.ausgeschaltet(an))
        assertTrue("reuters" in Rss.ausgeschaltet(Rss.schalten(an, "reuters", false)))
        assertEquals("", Rss.schalten(an, "reuters", false))
    }

    @Test
    fun otherSourcesKeepSwitching() {
        val aus = Rss.schalten("", "bbc", false)
        assertEquals(setOf("bbc") + Rss.STANDARD_AUS, Rss.ausgeschaltet(aus))
        assertEquals(Rss.STANDARD_AUS, Rss.ausgeschaltet(Rss.schalten(aus, "bbc", true)))
        val beides = Rss.schalten(Rss.schalten(aus, "reuters", true), "ezb", false)
        assertEquals(setOf("bbc", "ezb") + (Rss.STANDARD_AUS - "reuters"), Rss.ausgeschaltet(beides))
    }

    @Test
    fun headlineOnlyForReutersSourceNotYahooReutersItems() {
        assertTrue(Rss.keinLesemodus("https://news.google.com/rss/articles/AAA", "Reuters"))
        assertFalse(Rss.keinLesemodus("https://www.reuters.com/markets/x", "reuters.com · Yahoo Finance"))
        assertTrue("reuters" in Rss.NUR_ANSICHT)
    }
}
