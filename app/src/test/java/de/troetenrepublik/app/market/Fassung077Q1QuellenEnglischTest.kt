package de.troetenrepublik.app.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

// Englischsprachige Quellen 0.77: Eintrag je Quelle (Name, https-Adresse, Kategorie, Sprache), ausgeschaltet bis zur Wahl,
// und ein Lesetest je Feed-Form mit kurzen, selbst geschriebenen Beispielen.
class Fassung077Q1QuellenEnglischTest {

    private val neu = Rss.ENGLISCH_NEU

    private fun quellen(deutsch: Boolean) = Rss.eingebaut(deutsch).filter { it.schluessel in neu }

    @Test
    fun jedeNeueQuelleIstVollstaendigEingetragen() {
        for (deutsch in listOf(true, false)) {
            val liste = quellen(deutsch)
            assertEquals(neu.toSet(), liste.map { it.schluessel }.toSet())
            for (q in liste) {
                assertTrue(q.schluessel, q.name.isNotBlank())
                assertTrue(q.schluessel, q.kurz.isNotBlank())
                assertTrue(q.schluessel + " " + q.adresse, q.adresse.startsWith("https://") && Rss.adresseGueltig(q.adresse))
                assertTrue(q.schluessel, q.art == Rss.Art.RSS)
                // Anbieter mit eigener Unterkategorie je Feed: Presse, Notenbanken, Behoerden
                assertTrue(q.schluessel, q.anbieter in setOf(Rss.Anbieter.PRESSE, Rss.Anbieter.NOTENBANKEN, Rss.Anbieter.BEHOERDEN))
                assertEquals(q.schluessel, q.unter)
            }
        }
    }

    @Test
    fun schluesselSindEindeutigUndKategorienPassen() {
        val alle = Rss.eingebaut(true)
        assertEquals(alle.size, alle.map { it.schluessel }.toSet().size)
        assertEquals(alle.size, alle.map { it.adresse }.toSet().size)
        val k = quellen(true).associate { it.schluessel to it.kategorie }
        assertEquals(Rss.Kategorie.ZENTRALBANKEN, k["boc"])
        assertEquals(Rss.Kategorie.ZENTRALBANKEN, k["ezb-blog"])
        assertEquals(Rss.Kategorie.ZENTRALBANKEN, k["fed-reden"])
        assertEquals(Rss.Kategorie.ROHSTOFFE_DEVISEN, k["eia-presse"])
        assertEquals(Rss.Kategorie.ROHSTOFFE_DEVISEN, k["doe"])
        assertEquals(Rss.Kategorie.KONJUNKTUR, k["cbc"])
        assertEquals(Rss.Anbieter.NOTENBANKEN, quellen(true).first { it.schluessel == "boc" }.anbieter)
        assertEquals(Rss.Anbieter.BEHOERDEN, quellen(true).first { it.schluessel == "doe" }.anbieter)
    }

    @Test
    fun neueQuellenSindAusgeschaltetBisZurWahl() {
        val aus = Rss.ausgeschaltet("")
        for (s in neu) assertTrue(s, s in aus)
        // Eine bestehende Auswahl waechst nicht still: wer vorher etwas abgeschaltet hat, sieht die neuen weiter aus
        val alt = Rss.ausgeschaltet("bbc,ezb")
        assertTrue(neu.all { it in alt })
        assertTrue("bbc" in alt && "ezb" in alt)
        // Einschalten gilt nur fuer die gewaehlte Quelle
        val cbc = Rss.schalten("", "cbc", true)
        assertFalse("cbc" in Rss.ausgeschaltet(cbc))
        assertTrue("cna" in Rss.ausgeschaltet(cbc))
        assertTrue("cbc" in Rss.ausgeschaltet(Rss.schalten(cbc, "cbc", false)))
    }

    // Die Schlagzeilen gelten als englisch (Wichtig.englisch), auch wo der Feed nur einen kurzen Titel liefert
    @Test
    fun spracheEnglisch() {
        val beispiele = mapOf(
            "cbc" to ("Bank of Canada says rates will stay on hold" to "https://www.cbc.ca/news/business/x-1.1"),
            "cna" to ("Oil rises as the dollar falls" to "https://www.channelnewsasia.com/business/x-1"),
            "hindustan-times" to ("Sensex ends the week at a new high" to "https://www.hindustantimes.com/business/x.html"),
            "boc" to ("Governor speaks on the outlook for inflation" to "https://www.bankofcanada.ca/2026/11/x/"),
            "ezb-blog" to ("How the euro area is coping with the price shock" to "https://www.ecb.europa.eu/press/blog/x.en.html"),
            "fed-reden" to ("Governor on the labor market and the outlook" to "https://www.federalreserve.gov/newsevents/speech/x.htm"),
            "eia-presse" to ("EIA expects the winter fuel bill to rise" to "https://www.eia.gov/pressroom/releases/press1.php"),
            "doe" to ("Department announces a loan for the grid" to "https://www.energy.gov/articles/x"),
        )
        for ((s, t) in beispiele) {
            assertTrue(s, Wichtig.englisch(Rss.Eintrag(titel = t.first, link = t.second, zeit = 1L, quellen = setOf(s))))
        }
    }

    // RSS 2.0 mit CDATA und US-Zeitzone (CBC, Hindustan Times, Fed): Titel, Link, Zeit, Anriss ohne Auszeichnung
    @Test
    fun lesenRssMitZeitzonenKuerzel() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?><rss version="2.0"><channel><title>Beispiel</title>
            <item><title><![CDATA[Rates stay on hold]]></title><link><![CDATA[https://example.org/a?cmp=rss]]></link>
            <description><![CDATA[<p>Short <b>summary</b> of the story.</p>]]></description>
            <pubDate>Wed, 11 Nov 2026 12:01:59 EST</pubDate></item></channel></rss>"""
        val e = Rss.lesen(xml, "CBC News", Rss.Kategorie.KONJUNKTUR, schluessel = "cbc").single()
        assertEquals("Rates stay on hold", e.titel)
        assertEquals("https://example.org/a?cmp=rss", e.link)
        assertEquals(Instant.parse("2026-11-11T17:01:59Z").toEpochMilli(), e.zeit)
        assertEquals("Short summary of the story.", e.zusammenfassung)
        assertEquals("CBC News", e.quelle)
        assertEquals(setOf("cbc"), e.quellen)
    }

    // RSS mit Zeit nur als dc:date (Bank of Canada)
    @Test
    fun lesenRssMitDcDate() {
        val xml = """<rss version="2.0" xmlns:dc="http://purl.org/dc/elements/1.1/"><channel>
            <item><title>Policy rate announcement</title><link>https://example.org/2026/10/pr/</link>
            <description>The rate is set at a fixed level.</description><dc:date>2026-10-28T14:00:00+00:00</dc:date></item></channel></rss>"""
        val e = Rss.lesen(xml, "Bank of Canada", Rss.Kategorie.ZENTRALBANKEN, schluessel = "boc").single()
        assertEquals(Instant.parse("2026-10-28T14:00:00Z").toEpochMilli(), e.zeit)
        assertEquals("The rate is set at a fixed level.", e.zusammenfassung)
    }

    // Relative Links gegen die Adresse des Feeds (EIA), Zeitzone als Kuerzel EST
    @Test
    fun lesenRelativeLinks() {
        val xml = """<rss version="2.0"><channel><item><title>Winter outlook</title><link>/pressroom/releases/press1.php</link>
            <description>Short text.</description><pubDate>Tue, 10 Nov 2026 12:00:00 EST</pubDate></item></channel></rss>"""
        val q = Rss.eingebaut(true).first { it.schluessel == "eia-presse" }
        val e = Rss.lesen(xml, q.name, q.kategorie, schluessel = q.schluessel, basis = q.adresse).single()
        assertEquals("https://www.eia.gov/pressroom/releases/press1.php", e.link)
        assertEquals(Instant.parse("2026-11-10T17:00:00Z").toEpochMilli(), e.zeit)
    }

    // Doppelter Schraegstrich im Pfad (EZB-Blog) und Titel ohne Anriss
    @Test
    fun lesenDoppelterSchraegstrich() {
        val xml = """<rss version="2.0"><channel><item><title>An example post</title>
            <link>https://www.ecb.europa.eu//press/blog/date/2026/html/post.en.html</link>
            <pubDate>Wed, 11 Nov 2026 11:00:00 +0100</pubDate></item></channel></rss>"""
        val e = Rss.lesen(xml, "ECB Blog", Rss.Kategorie.ZENTRALBANKEN, schluessel = "ezb-blog").single()
        assertEquals("https://www.ecb.europa.eu/press/blog/date/2026/html/post.en.html", e.link)
        assertEquals("", e.zusammenfassung)
        assertEquals(Instant.parse("2026-11-11T10:00:00Z").toEpochMilli(), e.zeit)
    }

    // Langer Anriss wird auf 400 Zeichen begrenzt, Auszeichnung entfernt
    @Test
    fun lesenLangerAnrissBleibtKurz() {
        val lang = "word ".repeat(300)
        val xml = """<rss version="2.0"><channel><item><title>Long item</title><link>https://example.org/long</link>
            <description><![CDATA[<p>$lang</p>]]></description><pubDate>Wed, 11 Nov 2026 22:38:54 GMT</pubDate></item></channel></rss>"""
        val e = Rss.lesen(xml, "CBC News", Rss.Kategorie.KONJUNKTUR, schluessel = "cbc").single()
        assertTrue(e.zusammenfassung.length <= 400)
        assertFalse(e.zusammenfassung.contains('<'))
    }

    @Test
    fun zusatzIstFreiVonDoppelten() {
        val eingebaut = Rss.eingebaut(true)
        val zusatz = NachrichtenZusatz.QUELLEN.map { it.schluessel }
        assertEquals(zusatz.size, zusatz.toSet().size)
        assertEquals(eingebaut.size, eingebaut.map { it.schluessel }.toSet().size)
    }
}
