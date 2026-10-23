package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Rss
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime

class RssTest {

    private fun ts(s: String) = ZonedDateTime.parse(s).toInstant().toEpochMilli()

    // Aufbau wie der Feed der Federal Reserve: Link in CDATA, einstellige Tageszahl, „GMT“, Beschreibung gleich Titel.
    private val fed = """<?xml version="1.0" encoding="utf-8"?><rss version="2.0"><channel><title>FRB: Press Releases</title>
<item><title>Minutes of the Federal Open Market Committee, September 15-16, 2026</title>
<link><![CDATA[https://www.federalreserve.gov/newsevents/pressreleases/monetary20261007a.htm]]></link>
<description><![CDATA[Minutes of the Federal Open Market Committee, September 15-16, 2026]]></description>
<category>Monetary Policy</category><pubDate>Wed, 7 Oct 2026 18:00:00 GMT</pubDate></item>
</channel></rss>"""

    @Test
    fun readsRssWithCdataLinksAndRfcDates() {
        val e = Rss.lesen(fed, "Federal Reserve", Rss.Kategorie.ZENTRALBANKEN).single()
        assertEquals("Minutes of the Federal Open Market Committee, September 15-16, 2026", e.titel)
        assertEquals("https://www.federalreserve.gov/newsevents/pressreleases/monetary20261007a.htm", e.link)
        assertEquals(ts("2026-10-07T18:00:00Z"), e.zeit)
        assertEquals("", e.zusammenfassung)          // gleich dem Titel: weggelassen
        assertEquals("Federal Reserve", e.quellenText)
        assertEquals(setOf(Rss.Kategorie.ZENTRALBANKEN), e.kategorien)
    }

    @Test
    fun yahooItemsNamePublisherAndDropTrackingSuffix() {
        val xml = """<rss><channel><item><description>Robinhood added &#36;25 million in Bitcoin.</description>
<guid isPermaLink="false">592a</guid><link>https://beincrypto.com/robinhood-buys-bitcoin/?.tsrc=rss</link>
<pubDate>Wed, 07 Oct 2026 20:46:56 +0000</pubDate><title>Robinhood Buys &amp;#36;25 Million in Bitcoin. Why Now?</title></item></channel></rss>"""
        val e = Rss.lesen(xml, "Yahoo Finance", Rss.Kategorie.KRYPTO, herausgeberAusLink = true).single()
        assertEquals("https://beincrypto.com/robinhood-buys-bitcoin/", e.link)
        assertEquals("beincrypto.com · Yahoo Finance", e.quellenText)
        assertEquals("Robinhood added \$25 million in Bitcoin.", e.zusammenfassung)
        assertEquals(ts("2026-10-07T20:46:56Z"), e.zeit)
    }

    @Test
    fun encodingComesFromTheXmlHeader() {
        val xml = """<?xml version="1.0" encoding="ISO-8859-1"?><rss><channel><item><title>Auftragseingang gestiegen: Öl, Süßwaren</title>
<link>https://www.destatis.de/DE/Presse/x.html</link><pubDate>Wed, 7 Oct 2026 08:00:00 +0200</pubDate></item></channel></rss>"""
        val text = Rss.text(xml.toByteArray(Charsets.ISO_8859_1))
        val e = Rss.lesen(text, "Destatis", Rss.Kategorie.KONJUNKTUR).single()
        assertEquals("Auftragseingang gestiegen: Öl, Süßwaren", e.titel)
        assertEquals(ts("2026-10-07T06:00:00Z"), e.zeit)
        // Ohne Angabe gilt UTF-8, eine BOM wird entfernt.
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "<rss>ä</rss>".toByteArray(Charsets.UTF_8)
        assertEquals("<rss>ä</rss>", Rss.text(bom))
    }

    @Test
    fun readsAtomFeeds() {
        val atom = """<feed xmlns="http://www.w3.org/2005/Atom"><title>Blog</title>
<entry><title type="html">Zinsentscheid &lt;b&gt;heute&lt;/b&gt;</title><link rel="alternate" href="https://example.org/a"/>
<updated>2026-10-07T12:30:00+02:00</updated><summary>Kurzfassung</summary></entry></feed>"""
        val e = Rss.lesen(atom, "Blog", Rss.Kategorie.EIGENE).single()
        assertEquals("Zinsentscheid heute", e.titel)
        assertEquals("https://example.org/a", e.link)
        assertEquals(ts("2026-10-07T10:30:00Z"), e.zeit)
        assertEquals("Kurzfassung", e.zusammenfassung)
    }

    private fun e(titel: String, link: String, zeit: Long, k: Rss.Kategorie, quelle: String) =
        Rss.Eintrag(titel, link, zeit, kategorien = setOf(k), quellen = setOf(quelle), quelle = "Yahoo Finance")

    @Test
    fun mergesDuplicatesNewestFirstAndDropsOldOnes() {
        val now = ts("2026-10-07T20:00:00Z")
        val a = e("Apple steigt", "https://x.com/a", now - 60_000, Rss.Kategorie.MAERKTE, "yahoo-maerkte")
        val aUnternehmen = e("Apple steigt", "https://x.com/a", now - 60_000, Rss.Kategorie.UNTERNEHMEN, "yahoo-unternehmen")
        val gleicherTitel = e("APPLE STEIGT", "https://y.com/anders", now - 50_000, Rss.Kategorie.UNTERNEHMEN, "cnbc-top")
        val alt = e("Vor zehn Tagen", "https://x.com/alt", now - 10 * 86_400_000L, Rss.Kategorie.MAERKTE, "yahoo-maerkte")
        val neuer = e("Fed tagt", "https://x.com/fed", now - 10_000, Rss.Kategorie.ZENTRALBANKEN, "fed")
        val ohneZeit = e("Ohne Zeit", "https://x.com/o", 0L, Rss.Kategorie.KRYPTO, "yahoo-krypto")
        val zukunft = e("Zeitzone falsch", "https://x.com/z", now + 3_600_000L, Rss.Kategorie.KONJUNKTUR, "destatis")
        val fantasie = e("Web-TV", "https://x.com/tv", now + 400 * 86_400_000L, Rss.Kategorie.ZENTRALBANKEN, "snb")
        val alle = Rss.zusammen(listOf(listOf(a, alt, ohneZeit), listOf(aUnternehmen, gleicherTitel, neuer, zukunft, fantasie)), now)
        assertEquals(listOf("Zeitzone falsch", "Fed tagt", "Apple steigt", "Ohne Zeit"), alle.map { it.titel })
        assertEquals(now, alle[0].zeit)
        val apple = alle.first { it.titel == "Apple steigt" }
        assertEquals(setOf(Rss.Kategorie.MAERKTE, Rss.Kategorie.UNTERNEHMEN), apple.kategorien)
        assertEquals(setOf("yahoo-maerkte", "yahoo-unternehmen", "cnbc-top"), apple.quellen)
        val q = Rss.eingebaut(deutsch = true)
        assertEquals(4, Rss.filter(alle, Rss.Anbieter.ALLE, q).size)
        assertEquals(listOf("Apple steigt"), Rss.filter(alle, Rss.Anbieter.CNBC, q).map { it.titel })
        assertEquals(listOf("Apple steigt", "Ohne Zeit"), Rss.filter(alle, Rss.Anbieter.YAHOO, q).map { it.titel })
        assertEquals(listOf("Ohne Zeit"), Rss.filter(alle, Rss.Anbieter.YAHOO, q, unter = "KRYPTO").map { it.titel })
        assertEquals(listOf("Fed tagt"), Rss.filter(alle, Rss.Anbieter.NOTENBANKEN, q).map { it.titel })
        assertEquals(listOf("Zeitzone falsch"), Rss.filter(alle, Rss.Anbieter.BEHOERDEN, q, unter = "destatis").map { it.titel })
        assertTrue(Rss.filter(alle, Rss.Anbieter.MARKETWATCH, q).isEmpty())
        assertEquals(listOf("Fed tagt"), Rss.suche(alle, "fed").map { it.titel })
        assertEquals(listOf("Apple steigt"), Rss.suche(alle, "STEIGT").map { it.titel })
        assertEquals(alle, Rss.suche(alle, "  "))
    }

    @Test
    fun builtInSourcesAndAddressCheck() {
        val de = Rss.eingebaut(deutsch = true)
        assertTrue(de.any { it.kategorie == Rss.Kategorie.ZENTRALBANKEN && it.name == "EZB" })
        assertTrue(de.first { it.schluessel == "yahoo-krypto" }.adresse.contains("s=BTC-USD,ETH-USD"))
        assertTrue(de.first { it.schluessel == "yahoo-maerkte" }.adresse.endsWith("region=DE&lang=de-DE"))
        assertTrue(Rss.eingebaut(deutsch = false).first { it.schluessel == "yahoo-maerkte" }.adresse.endsWith("region=US&lang=en-US"))
        assertTrue(Rss.yahoo(listOf("^GDAXI", "GC=F"), true).contains("s=%5EGDAXI,GC%3DF"))
        assertTrue(Rss.adresseGueltig("https://example.org/feed.xml"))
        assertTrue(Rss.adresseGueltig("http://example.org/rss?x=1"))
        assertFalse(Rss.adresseGueltig("example.org/feed"))
        assertFalse(Rss.adresseGueltig("https://"))
        assertFalse(Rss.adresseGueltig("ftp://example.org/feed"))
    }

    @Test
    fun usZoneAbbreviationsRdfItemsAndDatePrefixes() {
        // BEA: Zeit mit „EDT“
        assertEquals(ts("2026-10-06T12:30:00Z"), Rss.zeit("Tue, 06 Oct 2026 08:30:00 EDT"))
        assertEquals(ts("2026-10-06T13:30:00Z"), Rss.zeit("Tue, 06 Oct 2026 08:30:00 EST"))
        // SNB: RDF-Eintraege mit Attribut, Zeit in dc:date, Datum vor dem Titel
        val snb = """<rdf:RDF><item rdf:about="https://www.snb.ch/x"><title>2026-10-07 - Antoine Martin: The SNB and its monetary policy</title>
<link>https://www.snb.ch/de/x</link><dc:date>2026-10-07T12:15:00Z</dc:date></item></rdf:RDF>"""
        val e = Rss.lesen(snb, "Schweizerische Nationalbank", Rss.Kategorie.ZENTRALBANKEN).single()
        assertEquals("Antoine Martin: The SNB and its monetary policy", e.titel)
        assertEquals(ts("2026-10-07T12:15:00Z"), e.zeit)
    }

    @Test
    fun savedHeadlinesRoundTripAndToggle() {
        val a = Rss.Eintrag("Titel mit\tTab", "https://x.com/a", 1000L, herausgeber = "x.com", quelle = "Yahoo Finance", kategorien = setOf(Rss.Kategorie.MAERKTE), quellen = setOf("yahoo-maerkte"))
        val b = Rss.Eintrag("Zweiter", "https://x.com/b", 2000L, quelle = "EZB")
        var liste = Rss.umschalten(emptyList(), a)
        liste = Rss.umschalten(liste, b)
        assertEquals(listOf("https://x.com/b", "https://x.com/a"), liste.map { it.link })
        val zurueck = Rss.gemerktLesen(Rss.gemerktText(liste))
        assertEquals(listOf("Zweiter", "Titel mit Tab"), zurueck.map { it.titel })
        assertEquals("x.com · Yahoo Finance", zurueck[1].quellenText)
        assertEquals(1000L, zurueck[1].zeit)
        assertEquals(setOf(Rss.Kategorie.GEMERKT), zurueck[1].kategorien)
        // Noch einmal umschalten entfernt.
        assertEquals(listOf("https://x.com/b"), Rss.umschalten(zurueck, a).map { it.link })
        assertTrue(Rss.gemerktLesen("kaputt\nnoch\tkaputt").isEmpty())
    }

    @Test
    fun everyProviderHasSourcesAndSubcategories() {
        val q = Rss.eingebaut(deutsch = true)
        // Dienste mit eigenem Schluessel kommen erst mit dem Schluessel dazu.
        val mitSchluessel = setOf(Rss.Anbieter.GUARDIAN, Rss.Anbieter.FINNHUB, Rss.Anbieter.ALPHAVANTAGE)
        val feste = Rss.Anbieter.entries - setOf(Rss.Anbieter.ALLE, Rss.Anbieter.EIGENE, Rss.Anbieter.GEMERKT) - mitSchluessel
        for (a in feste) assertTrue(a.name, q.any { it.anbieter == a })
        for (a in mitSchluessel) assertTrue(a.name, Rss.mitSchluessel("g", "f", "a").any { it.anbieter == a })
        // Themen bei Yahoo, Haeuser bei den Notenbanken, keine bei MarketWatch.
        assertEquals(listOf("MAERKTE", "UNTERNEHMEN", "ROHSTOFFE_DEVISEN", "KRYPTO"), Rss.unterkategorien(Rss.Anbieter.YAHOO, q).map { it.unter })
        assertEquals(listOf("EZB", "Fed", "Bank of England", "SNB"), Rss.unterkategorien(Rss.Anbieter.NOTENBANKEN, q).map { it.kurz })
        assertEquals(1, Rss.unterkategorien(Rss.Anbieter.MARKETWATCH, q).size)
        assertEquals("ECB", Rss.eingebaut(deutsch = false).first { it.schluessel == "ezb" }.kurz)
        // Eigene Feeds bilden je eine Unterkategorie.
        val eigen = Rss.eigene("", "https://example.org/feed.xml")
        assertEquals(Rss.Anbieter.EIGENE, eigen.anbieter)
        assertEquals("example.org", eigen.kurz)
        assertEquals(eigen.schluessel, eigen.unter)
    }

    @Test
    fun everyFixedCategoryHasAtLeastOneSource() {
        val de = Rss.eingebaut(deutsch = true)
        val fest = listOf(Rss.Kategorie.MAERKTE, Rss.Kategorie.UNTERNEHMEN, Rss.Kategorie.KONJUNKTUR, Rss.Kategorie.ZENTRALBANKEN, Rss.Kategorie.ROHSTOFFE_DEVISEN, Rss.Kategorie.KRYPTO)
        for (k in fest) assertTrue(k.name, de.any { it.kategorie == k })
        assertEquals(de.size, de.map { it.schluessel }.toSet().size)
        assertTrue(de.all { it.adresse.startsWith("https://") })
        // tagesschau.de erlaubt hoechstens 60 Abrufe je Stunde: zwei Feeds mit je fuenf Minuten Abstand.
        assertTrue(de.filter { it.adresse.contains("tagesschau.de") }.all { it.abstandMs >= 5 * 60_000L })
    }
}
