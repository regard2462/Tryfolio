package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Rss
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DurchbruecheTest {

    // Gekuerzte echte Antworten (06.11.2026): Google-News-Suche zu Quanten und Halbleitern, ScienceDaily
    private val googleQuanten = """<rss version="2.0"><channel><title>quantum - Google News</title>
<item><title>Oak Ridge teams up with IBM, AMD to launch quantum error correction platform - FedScoop</title><link>https://news.google.com/rss/articles/a1</link><pubDate>Thu, 05 Nov 2026 13:01:51 GMT</pubDate><source url="https://fedscoop.com">FedScoop</source></item>
<item><title>IBM, AMD and Oak Ridge join forces to launch quantum computing error-correction platform - SiliconANGLE</title><link>https://news.google.com/rss/articles/a2</link><pubDate>Thu, 05 Nov 2026 13:00:00 GMT</pubDate><source url="https://siliconangle.com">SiliconANGLE</source></item>
<item><title>IBM, AMD, And Oak Ridge Unveil Quantum Error Correction Platform - The Next Platform</title><link>https://news.google.com/rss/articles/a3</link><pubDate>Fri, 06 Nov 2026 22:58:31 GMT</pubDate><source url="https://nextplatform.com">The Next Platform</source></item>
<item><title>Scientists overcome a hidden obstacle to more powerful quantum computers - Science Daily</title><link>https://news.google.com/rss/articles/a4</link><pubDate>Sun, 25 Oct 2026 07:00:00 GMT</pubDate><source url="https://sciencedaily.com">Science Daily</source></item>
</channel></rss>"""

    private val googleChips = """<rss version="2.0"><channel><title>chip - Google News</title>
<item><title>Semiconductor breakthrough overcomes major challenge in capturing solar energy - EurekAlert!</title><link>https://news.google.com/rss/articles/b1</link><pubDate>Wed, 28 Oct 2026 19:53:44 GMT</pubDate><source url="https://eurekalert.org">EurekAlert!</source></item>
<item><title>SMH Returned 44.09% Over the Past Year. First Trust's Chip Fund Returned 67.35%. - 24/7 Wall St.</title><link>https://news.google.com/rss/articles/b2</link><pubDate>Mon, 02 Nov 2026 22:33:00 GMT</pubDate><source url="https://247wallst.com">24/7 Wall St.</source></item>
<item><title>VanEck launches Europe's first China semiconductor ETF - Yahoo Finance UK</title><link>https://news.google.com/rss/articles/b3</link><pubDate>Thu, 29 Oct 2026 08:00:26 GMT</pubDate><source url="https://uk.finance.yahoo.com">Yahoo Finance UK</source></item>
<item><title>Obituary information for Chip Grady - Chapman Funerals</title><link>https://news.google.com/rss/articles/b4</link><pubDate>Sat, 24 Oct 2026 07:00:00 GMT</pubDate><source url="https://chapman.com">Chapman Funerals</source></item>
<item><title>Edwards Vacuum formally opens its first U.S. semiconductor pump plant - The Business Journals</title><link>https://news.google.com/rss/articles/b5</link><pubDate>Thu, 29 Oct 2026 19:01:00 GMT</pubDate><source url="https://bizjournals.com">The Business Journals</source></item>
</channel></rss>"""

    private val googleBatterie = """<rss version="2.0"><channel><title>battery - Google News</title>
<item><title>Sunset Park Blaze Kills 6 As Lithium-ion Battery Fire Crisis Rages On - Streetsblog New York City</title><link>https://news.google.com/rss/articles/c1</link><pubDate>Thu, 05 Nov 2026 17:55:00 GMT</pubDate><source url="https://nyc.streetsblog.org">Streetsblog New York City</source></item>
<item><title>A New Kind of Battery Is Taking Off. This One Stores Blistering Heat. - The New York Times</title><link>https://news.google.com/rss/articles/c2</link><pubDate>Thu, 29 Oct 2026 07:00:00 GMT</pubDate><source url="https://nytimes.com">The New York Times</source></item>
</channel></rss>"""

    private val scienceDaily = """<rss version="2.0"><channel><title>Latest Science News -- ScienceDaily</title>
<item><title>An anti-aging drug may help protect the brain before Alzheimer&#039;s symptoms begin</title><link>https://www.sciencedaily.com/releases/2026/11/261105001.htm</link><pubDate>Thu, 05 Nov 2026 20:11:19 EST</pubDate></item>
<item><title>MIT scientists break a 20-year rule and unlock never-before-seen crystal structures</title><link>https://www.sciencedaily.com/releases/2026/11/261104002.htm</link><pubDate>Wed, 04 Nov 2026 07:23:36 EST</pubDate></item>
<item><title>NASA's Swift telescope falls to Earth after nearly 22 years in space</title><link>https://www.sciencedaily.com/releases/2026/11/261106003.htm</link><pubDate>Fri, 06 Nov 2026 02:12:55 EST</pubDate></item>
<item><title>Don't miss: Meteor showers, fireballs, and a supermoon light up November's night sky</title><link>https://www.sciencedaily.com/releases/2026/11/261106004.htm</link><pubDate>Fri, 06 Nov 2026 03:21:08 EST</pubDate></item>
</channel></rss>"""

    private val jetzt = Rss.zeit("Sat, 07 Nov 2026 00:00:00 GMT")

    private fun roh(): List<Durchbrueche.Roh> {
        fun google(xml: String, thema: String) =
            Rss.lesen(xml, "Google News", Rss.Kategorie.UNTERNEHMEN, google = true).map { Durchbrueche.Roh(thema, it.titel, it.link, it.zeit, it.herausgeber) }
        val feed = Rss.lesen(scienceDaily, "ScienceDaily", Rss.Kategorie.UNTERNEHMEN).map { Durchbrueche.Roh(null, it.titel, it.link, it.zeit, "ScienceDaily") }
        return google(googleQuanten, "quanten") + google(googleChips, "halbleiter") + google(googleBatterie, "batterie") + feed
    }

    @Test
    fun kursUndTodesanzeigenFallenWeg() {
        val titel = Durchbrueche.filtern(roh(), jetzt).map { it.titel }
        assertTrue(titel.none { it.contains("SMH Returned") })
        assertTrue(titel.none { it.contains("ETF") })
        assertTrue(titel.none { it.contains("Obituary") })
        assertTrue(titel.any { it.startsWith("Semiconductor breakthrough") })
    }

    @Test
    fun brandMeldungenBeiBatterienFallenWeg() {
        val titel = Durchbrueche.filtern(roh(), jetzt).filter { it.thema == "batterie" }.map { it.titel }
        assertEquals(1, titel.size)
        assertTrue(titel[0].startsWith("A New Kind of Battery"))
    }

    @Test
    fun dublettenBleibenEinmalUndDieNeuesteGewinnt() {
        val q = Durchbrueche.filtern(roh(), jetzt).filter { it.thema == "quanten" }
        // Drei Titel zur selben Geschichte, dazu die Meldung von Ende Oktober
        assertEquals(2, q.size)
        assertTrue(q[0].titel.startsWith("IBM, AMD, And Oak Ridge Unveil"))
        assertTrue(q[1].titel.startsWith("Scientists overcome"))
    }

    @Test
    fun feedmeldungenBekommenEinThemaNachStichwoertern() {
        val liste = Durchbrueche.filtern(roh(), jetzt)
        assertEquals("medizin", liste.first { it.titel.startsWith("An anti-aging drug") }.thema)
        assertEquals("materialien", liste.first { it.titel.startsWith("MIT scientists") }.thema)
        assertEquals("raumfahrt", Durchbrueche.zuordnen("Starship reaches orbit again")?.id)
        assertNotNull(Durchbrueche.zuordnen("Scientists build a new quantum sensor"))
        // Ohne Stichwort kein Thema
        assertNull(Durchbrueche.zuordnen("Don't miss: Meteor showers light up the night sky"))
        assertTrue(liste.none { it.titel.startsWith("Don't miss") })
    }

    @Test
    fun themenOhneMeldungFallenWegUndFrischestesStehtOben() {
        val gruppen = Durchbrueche.gruppieren(Durchbrueche.filtern(roh(), jetzt), jetzt)
        val ids = gruppen.map { it.thema.id }
        assertFalse("klima" in ids)
        assertEquals("quanten", ids.first())
        assertTrue(gruppen.all { it.meldungen.size <= Durchbrueche.JE_THEMA })
        // Nach 20 Tagen ist alles zu alt
        assertTrue(Durchbrueche.gruppieren(Durchbrueche.filtern(roh(), jetzt), jetzt + 20 * 86_400_000L).isEmpty())
    }

    @Test
    fun alterInMinutenStundenTagen() {
        assertEquals(Durchbrueche.Einheit.MINUTEN, Durchbrueche.alter(jetzt - 5 * 60_000L, jetzt).einheit)
        val h = Durchbrueche.alter(jetzt - 3 * 3_600_000L - 100, jetzt)
        assertEquals(3L, h.wert)
        assertEquals(Durchbrueche.Einheit.STUNDEN, h.einheit)
        assertEquals(Durchbrueche.Einheit.TAGE, Durchbrueche.alter(jetzt - 2 * 86_400_000L, jetzt).einheit)
    }

    @Test
    fun speicherKreislauf() {
        val liste = Durchbrueche.filtern(roh(), jetzt)
        val text = Durchbrueche.schreiben(Durchbrueche.Stand(jetzt, liste))
        val zurueck = Durchbrueche.lesenGespeichert(text)
        assertNotNull(zurueck)
        assertEquals(liste.size, zurueck!!.liste.size)
        assertTrue(Durchbrueche.gueltig(zurueck.abgerufen, jetzt + 1000))
        assertFalse(Durchbrueche.gueltig(zurueck.abgerufen, jetzt + Durchbrueche.GUELTIG_MS))
    }

    @Test
    fun suchadresseHatZeitfensterUndKeineBoersenwoerter() {
        val s = Durchbrueche.suchbegriff(Durchbrueche.THEMEN[0], true)
        assertTrue(s.endsWith("when:14d"))
        assertTrue(s.contains("-stock"))
    }
}
