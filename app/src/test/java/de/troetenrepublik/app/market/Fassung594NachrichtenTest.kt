package de.troetenrepublik.app.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Nachrichten 5.94: neue Quellen, Google News, Volltext aus dem Feed, Anriss von der Seite, Lesemodus je Seite.
class Fassung594NachrichtenTest {

    @Test
    fun neueQuellen() {
        val de = Rss.eingebaut(true)
        for (s in listOf("bbc", "ntv", "handelsblatt", "sz", "nyt", "fn-adhoc", "defiant", "decrypt", "google-wirtschaft", "bis", "bafin", "bafin-massnahmen", "eia")) {
            assertTrue(s, de.any { it.schluessel == s })
        }
        assertEquals("BBC News", de.first { it.schluessel == "bbc" }.name)
        assertTrue(de.filter { it.anbieter == Rss.Anbieter.PRESSE }.all { it.unter == it.schluessel })
        assertTrue(de.filter { it.schluessel in setOf("ezb", "fed", "snb") }.all { it.anrissVonSeite })
        assertEquals(setOf("blog-bu", "blog-damodaran", "blog-odad", "blog-klement", "blog-kommer"), de.filter { it.volltext }.map { it.schluessel }.toSet())
        assertTrue(Rss.eingebaut(false).first { it.schluessel == "google-wirtschaft" }.adresse.endsWith("hl=en-US&gl=US&ceid=US:en"))
        assertTrue(Rss.googleSuche("\"SAP\"", true).startsWith("https://news.google.com/rss/search?q=%22SAP%22&hl=de"))
    }

    @Test
    fun lesemodusJeSeite() {
        assertTrue(Rss.textErlaubt("https://www.ecb.europa.eu/press/pr/date/2026/html/x.en.html"))
        assertTrue(Rss.textErlaubt("https://www.federalreserve.gov/newsevents/pressreleases/monetary20261028a.htm"))
        assertTrue(Rss.textErlaubt("https://apps.bea.gov/news/x"))
        assertFalse(Rss.textErlaubt("https://finance.yahoo.com/news/x"))
        assertFalse(Rss.textErlaubt("https://www.cnbc.com/2026/11/03/x.html"))
        assertFalse(Rss.textErlaubt("https://notecb.europa.eu.example.com/x"))
        assertTrue(Rss.ohneLesemodus("https://www.handelsblatt.com/finanzen/x/100266746.html"))
        assertTrue(Rss.ohneLesemodus("https://news.google.com/rss/articles/abc", "Handelsblatt"))
        assertFalse(Rss.ohneLesemodus("https://news.google.com/rss/articles/abc", "WELT"))
        assertFalse(Rss.ohneLesemodus("https://www.n-tv.de/wirtschaft/x"))
    }

    @Test
    fun googleNewsOhneHerausgeberImTitel() {
        val xml = """<rss><channel><item><title>SAP-Aktie: Es geht wieder bergab - WELT</title><link>https://news.google.com/rss/articles/abc?oc=5</link>
            <pubDate>Mon, 02 Nov 2026 16:05:19 GMT</pubDate><description>&lt;a href="x"&gt;SAP-Aktie: Es geht wieder bergab&lt;/a&gt;&amp;nbsp;&lt;font&gt;WELT&lt;/font&gt;</description>
            <source url="https://www.welt.de">WELT</source></item></channel></rss>"""
        val e = Rss.lesen(xml, "Google News", Rss.Kategorie.MAERKTE, google = true).single()
        assertEquals("SAP-Aktie: Es geht wieder bergab", e.titel)
        assertEquals("WELT", e.herausgeber)
        assertEquals("", e.zusammenfassung)
        assertEquals("WELT · Google News", e.quellenText)
        // Beim Zusammenfuehren gewinnt der direkte Link
        val direkt = e.copy(link = "https://www.welt.de/wirtschaft/a1.html", herausgeber = "WELT", quelle = "WELT", quellen = setOf("welt"))
        assertEquals(direkt.link, Rss.zusammen(listOf(listOf(e.copy(zeit = 1L)), listOf(direkt.copy(zeit = 1L))), now = 2L, tage = 100_000).single().link)
    }

    @Test
    fun volltextAusDemFeed() {
        val rss = """<rss><channel><item><title>Beitrag</title><link>https://example.org/a</link><description>Kurz</description>
            <content:encoded><![CDATA[<p>Erster Absatz mit genug Text, damit er als richtiger Absatz zaehlt und nicht als Rest.</p><p>Zweiter Absatz, ebenso lang genug fuer die Pruefung des Lesers im Feed.</p><p>Dritter Absatz rundet den Beitrag ab, damit die Mindestlaenge sicher erreicht ist.</p>]]></content:encoded></item></channel></rss>"""
        val e = Rss.lesen(rss, "Blog", Rss.Kategorie.BLOGS, volltext = true).single()
        assertEquals(3, e.absaetze.size)
        assertTrue(e.absaetze[0].startsWith("Erster Absatz"))
        // Ohne Kennzeichen bleibt der Volltext weg
        assertTrue(Rss.lesen(rss, "Blog", Rss.Kategorie.BLOGS).single().absaetze.isEmpty())
        // Blogger: HTML als Entitaeten, Absaetze nur mit <br>
        val atom = """<feed><entry><title>Post</title><link rel="alternate" href="https://b.example/p"/><updated>2026-11-01T10:00:00Z</updated>
            <content type="html">Erste Zeile des Beitrags&lt;br /&gt;&lt;br /&gt;Zweite Zeile mit &amp;quot;Zitat&amp;quot;</content></entry></feed>"""
        val b = Rss.lesen(atom, "Blogger", Rss.Kategorie.BLOGS, volltext = true).single()
        assertEquals(listOf("Erste Zeile des Beitrags", "Zweite Zeile mit \"Zitat\""), b.absaetze)
        // Fliesstext in div/br mit einer kurzen Liste: der Fliesstext geht nicht verloren
        val gemischt = """<rss><channel><item><title>B</title><link>https://example.org/b</link><content:encoded><![CDATA[<div>""" +
            "Langer Fliesstext ohne Absatz-Tags, wie ihn Blogger schreibt. ".repeat(12) + """</div><ul><li>Punkt eins mit etwas Text, damit die Liste allein schon zweihundert Zeichen hat und den Weg ueber Absatz-Tags waehlen wuerde.</li><li>Punkt zwei, ebenfalls lang genug fuer diese Pruefung des Lesers.</li><li>Punkt drei bringt die Liste sicher ueber zweihundert Zeichen.</li></ul>]]></content:encoded></item></channel></rss>"""
        assertTrue(Rss.lesen(gemischt, "Blog", Rss.Kategorie.BLOGS, volltext = true).single().absaetze.any { it.startsWith("Langer Fliesstext") })
        // Doppelte behalten den Volltext
        val z = Rss.zusammen(listOf(listOf(e.copy(absaetze = emptyList(), zeit = 1L)), listOf(e.copy(zeit = 1L))), now = 2L, tage = 100_000)
        assertEquals(3, z.single().absaetze.size)
    }

    @Test
    fun zeichensatzTrotzFalscherAngabe() {
        val kopf = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><rss><channel><item><title>"
        val bytes = kopf.toByteArray(Charsets.US_ASCII) + "Märkte für Anleger".toByteArray(charset("windows-1252")) + "</title></item></channel></rss>".toByteArray(Charsets.US_ASCII)
        assertTrue(Rss.text(bytes).contains("Märkte für Anleger"))
        assertTrue(Rss.text(("$kopf\u00c4rger</title>").toByteArray(Charsets.UTF_8)).contains("Ärger"))
        // Abgeschnittenes Ende eines UTF-8-Feeds kippt ihn nicht
        val utf = ("$kopf Märkte €</title>").toByteArray(Charsets.UTF_8)
        assertTrue(Rss.text(utf.copyOf(utf.size - 9)).contains("Märkte"))
        // Windows-1252 mit einem zufaellig gueltigen Paar (ß vor “) kippt trotzdem um
        val gemischt = kopf.toByteArray(Charsets.US_ASCII) + "Ärger über Löhne, Größe, „ist groß“".toByteArray(charset("windows-1252")) + "</title>".toByteArray(Charsets.US_ASCII)
        assertTrue(Rss.text(gemischt).contains("Ärger über Löhne"))
        // Ersatzzeichen der Quelle selbst kippen einen richtigen UTF-8-Feed nicht
        assertTrue(Rss.text("$kopf Fed’s \uFFFD \uFFFD</title>".toByteArray(Charsets.UTF_8)).contains("Fed’s"))
    }

    @Test
    fun anrissVonDerSeite() {
        val fed = """<html><body><nav><p>Official websites use .gov A .gov website belongs to an official government organization in the United States.</p></nav>
            <h3 class="title">Federal Reserve issues FOMC statement</h3>
            <p>For release at 2:00 p.m. EDT Share --> --> --> The Federal Open Market Committee approved the following statement for release by a 10 – 2 vote:</p>
            <p>The Committee decided to maintain the target range for the federal funds rate at 3-3/4 to 4 percent, in support of the dual mandate.</p>
            <p>For media inquiries, please call the office of public affairs at the board in Washington.</p></body></html>"""
        val a = Anriss.ausHtml(fed, "Federal Reserve issues FOMC statement")
        assertTrue(a, a.startsWith("The Federal Open Market Committee approved"))
        assertTrue(a, a.contains("target range"))
        assertFalse(a.contains("For release"))
        val snb = """<p>Die SNB Schliessen Zurück Aufgaben &amp; Ziele Geldpolitik</p><h1>Zwischenbericht der Schweizerischen Nationalbank per 30. September 2026</h1>
            <p>Die Schweizerische Nationalbank weist für die ersten drei Quartale 2026 einen Gewinn von 44,2 Mrd. Franken aus.</p>
            <p>Der Gewinn auf den Fremdwährungspositionen betrug 43,3 Mrd. Franken. Auf dem Goldbestand resultierte ein Bewertungsgewinn.</p>"""
        val s = Anriss.ausHtml(snb, "Medienmitteilung - Zwischenbericht der Schweizerischen Nationalbank")
        assertTrue(s, s.startsWith("Die Schweizerische Nationalbank weist"))
        // Seite ohne Text: kein Anriss
        assertEquals("", Anriss.ausHtml("<h1>Zinssätze</h1><p>Erforderlich : Diese Cookies können nicht abgelehnt werden, da sie notwendig sind.</p>", "Datenportal - Zinssätze"))
        assertTrue(Anriss.ausHtml("<p>" + "Ein langer Satz über Geldpolitik und Zinsen. ".repeat(20) + "</p>", "x").length <= 400)
    }
}
