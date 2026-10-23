package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.market.Praesident
import de.troetenrepublik.app.market.TruthArchiv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Vorberechneter Verlauf des Praesidenten und das Truth-Social-Archiv; alle Daten sind erfunden.
class Fassung38Test {

    private val tag = 86_400_000L

    private fun kerzen(vararg werte: Pair<Long, Double>) = werte.map { (d, c) -> Candle(d * tag + 14 * 3_600_000L, c, c, c, c) }

    private val datei = """
        {"format":1,"stand":"2026-10-17","stichtag":"2026-07-31","tage":[20664,20665,20666],"werte":[100.0,101.5,110.0],
         "marken":[{"t":20664,"kauf":true,"n":3,"w":[{"k":"AAA","g":250000},{"k":"BBB","g":8000}]}],
         "bestand":[{"k":"AAA","g":0.5},{"k":"BBB","g":0.3},{"k":"CCC","g":0.2}],
         "anleihen":0.6,"zahlen":{"geschaefte":10,"jahresmeldung":6,"einzelmeldungen":4,"eingerechnet":7,"anleihen":2,"ohneKuerzel":1,"werte":3},
         "unbekannt":"wird ignoriert"}
    """.trimIndent()

    @Test
    fun praesidentLesen() {
        val d = Praesident.lesen(datei)
        assertNotNull(d)
        d!!
        assertEquals(3, d.tage.size)
        assertEquals(0.6, d.anleihen, 1e-9)
        assertEquals(7, d.zahlen.eingerechnet)
        assertEquals(0, d.zahlen.ohneKurs)
        assertEquals(20666L * tag, Praesident.reihe(d).last().first)
        // Marken als Geschaefte fuer die Kauf- und Verkaufsmarken der Seite
        val h = Praesident.handel(d)
        assertEquals(listOf("AAA", "BBB"), h.map { it.kuerzel })
        assertTrue(h.all { it.kauf && it.t == 20664L * tag })
        // falsches Format oder ungleiche Laengen: keine Datei
        assertNull(Praesident.lesen(datei.replace("\"format\":1", "\"format\":2")))
        assertNull(Praesident.lesen(datei.replace("[100.0,101.5,110.0]", "[100.0,101.5]")))
        assertNull(Praesident.lesen("kein json"))
    }

    @Test
    fun praesidentFortschreiben() {
        val d = Praesident.lesen(datei)!!
        val stich = 20666L
        // AAA verdoppelt sich am Tag danach, BBB bleibt, CCC hat keine Kurse: Index = 110 * (0,5*2 + 0,3*1) / 0,8
        val kurse = mapOf(
            "AAA" to kerzen(stich - 1 to 9.0, stich to 10.0, stich + 1 to 20.0, stich + 3 to 15.0),
            "BBB" to kerzen(stich to 50.0, stich + 1 to 50.0),
        )
        val r = Praesident.fortschreiben(d, kurse)
        assertEquals(listOf((stich + 1) * tag, (stich + 3) * tag), r.map { it.first })
        assertEquals(110.0 * 1.3 / 0.8, r[0].second, 1e-9)
        // am dritten Tag ohne Kurs fuer BBB gilt dessen letzter Kurs
        assertEquals(110.0 * (0.5 * 1.5 + 0.3) / 0.8, r[1].second, 1e-9)
        // ohne Kurse kein Fortschreiben
        assertTrue(Praesident.fortschreiben(d, emptyMap()).isEmpty())
        // Werte fuer den Abruf: die groessten, bis 95 Prozent des Bestands (hier alle drei)
        assertEquals(listOf("AAA", "BBB", "CCC"), Praesident.fortschreibWerte(d))
        assertEquals(listOf("AAA"), Praesident.fortschreibWerte(d, deckung = 0.4))
        // Zeitraum des Abrufs enthaelt den Stichtag
        assertEquals("3mo", Praesident.zeitraum(stich, stich + 60))
        assertEquals("6mo", Praesident.zeitraum(stich, stich + 120))
        assertEquals("1y", Praesident.zeitraum(stich, stich + 300))
        assertEquals("2y", Praesident.zeitraum(stich, stich + 500))
    }

    private val feed = """<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:truth="https://truthsocial.com/ns" xmlns:media="http://search.yahoo.com/mrss/" xmlns:archive="https://www.trumpstruth.org/rss-extensions">
<channel><title><![CDATA[Trump’s Truth - Latest Posts]]></title>
<item>
  <title><![CDATA[Best in History!]]></title>
  <link>https://www.trumpstruth.org/statuses/42396</link>
  <description><![CDATA[<p>Best in History!</p><p>Second line &amp; more</p><p><a href="https://truth-archive.us-iad-1.linodeobjects.com/attachments/1/a.jpg">View image</a></p>]]></description>
  <pubDate>Sat, 17 Oct 2026 04:11:12 +0000</pubDate>
  <truth:originalUrl>https://truthsocial.com/@realDonaldTrump/117454292438977074</truth:originalUrl>
  <archive:postType>original</archive:postType>
  <archive:removed>false</archive:removed>
  <media:content url="https://truth-archive.us-iad-1.linodeobjects.com/attachments/1/a.jpg" medium="image">
    <media:thumbnail url="https://static-assets-1.truthsocial.com/x/small/a.jpg" />
  </media:content>
</item>
<item>
  <title><![CDATA[Quote post from October 17, 2026]]></title>
  <link>https://www.trumpstruth.org/statuses/42394</link>
  <description><![CDATA[<p><span><br />RT: https://truthsocial.com/users/realDonaldTrump/statuses/117453447477979909</span></p><p>Quoted <a href="https://www.trumpstruth.org/statuses/42395">Donald J. Trump</a></p><blockquote><p><a href="https://truth-archive.us-iad-1.linodeobjects.com/attachments/2/b.mov">View video</a></p></blockquote>]]></description>
  <pubDate>Sat, 17 Oct 2026 00:37:02 +0000</pubDate>
  <archive:postType>quote</archive:postType>
  <archive:removed>true</archive:removed>
  <media:content url="https://truth-archive.us-iad-1.linodeobjects.com/attachments/2/b.mov" medium="video"></media:content>
</item>
<item>
  <title><![CDATA[Repost]]></title>
  <link>https://www.trumpstruth.org/statuses/42390</link>
  <description><![CDATA[<p>RT @someone: Great</p>]]></description>
  <pubDate>Fri, 16 Oct 2026 22:00:00 +0000</pubDate>
  <archive:postType>repost</archive:postType>
</item>
<item>
  <title>Fremder Link</title>
  <link>https://truthsocial.com/@realDonaldTrump/1</link>
  <description>nicht aus dem Archiv</description>
</item>
</channel></rss>"""

    @Test
    fun truthArchivLesen() {
        val l = TruthArchiv.lesen(feed)
        // der Eintrag mit Link auf truthsocial.com faellt weg: es zaehlen nur Archivseiten
        assertEquals(3, l.size)
        val a = l[0]
        assertEquals("https://www.trumpstruth.org/statuses/42396", a.link)
        assertEquals("Best in History!\nSecond line & more", a.text)
        assertEquals(listOf("https://truth-archive.us-iad-1.linodeobjects.com/attachments/1/a.jpg"), a.bilder)
        assertEquals(TruthArchiv.Art.EIGEN, a.art)
        assertFalse(a.geloescht)
        assertTrue(a.zeit > 0)
        // Zitat: Adresse von truthsocial.com und „View video“ stehen nicht im Text, das Video zaehlt
        val z = l[1]
        assertEquals(TruthArchiv.Art.ZITAT, z.art)
        assertTrue(z.geloescht)
        assertEquals(1, z.videos)
        assertTrue(z.bilder.isEmpty())
        assertFalse(z.text.contains("truthsocial"))
        assertFalse(z.text.contains("View video"))
        assertTrue(z.text.contains("Quoted Donald J. Trump"))
        assertEquals(TruthArchiv.Art.WEITERGETEILT, l[2].art)
        // Adresse des Feeds: Anzahl begrenzt, Tag als Ende
        assertEquals("https://www.trumpstruth.org/feed?limit=500", TruthArchiv.adresse(900))
        assertEquals("https://www.trumpstruth.org/feed?limit=100&end_date=2026-10-01", TruthArchiv.adresse(100, "2026-10-01"))
        // Zusammenfuehren ohne Doppelte, neueste zuerst
        val zusammen = TruthArchiv.zusammen(l.drop(1), l)
        assertEquals(3, zusammen.size)
        assertEquals(l[0].link, zusammen[0].link)
    }
}
