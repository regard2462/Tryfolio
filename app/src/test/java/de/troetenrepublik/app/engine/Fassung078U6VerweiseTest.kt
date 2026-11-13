package de.troetenrepublik.app.engine

import de.troetenrepublik.app.FinderAlarm
import de.troetenrepublik.app.data.Settings
import de.troetenrepublik.app.finderAlarmeOhne
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.Rss
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Fassung078U6VerweiseTest {

    // ------------------------------------------------------------ Nachrichten zum Wert

    private fun n(titel: String, zeit: Long, link: String = "https://finance.yahoo.com/news/$zeit") =
        MarketApi.NewsItem(titel, link, zeit, "Anriss", "reuters.com")

    @Test fun zusammenNeuesteZuerstOhneDoppelte() {
        val yahoo = listOf(n("Zahlen besser als erwartet", 30), n("Neuer Vorstand", 10))
        val google = listOf(n("zahlen besser als erwartet ", 40, "https://news.google.com/rss/articles/a"), n("Werk in Texas", 20, "https://news.google.com/rss/articles/b"))
        val l = WertNachrichten.zusammen(yahoo, google, { it.title }, { it.published })
        assertEquals(listOf(30L, 20L, 10L), l.map { it.published })
        // Bei gleicher Schlagzeile bleibt der Eintrag von Yahoo
        assertTrue(l.first().link.startsWith("https://finance.yahoo.com"))
    }

    @Test fun zusammenHoechstensZwanzig() {
        val yahoo = (1..15).map { n("Y$it", it.toLong()) }
        val google = (1..15).map { n("G$it", 100L + it, "https://news.google.com/rss/articles/$it") }
        val l = WertNachrichten.zusammen(yahoo, google, { it.title }, { it.published })
        assertEquals(WertNachrichten.JE_WERT, l.size)
        assertEquals(115L, l.first().published)
    }

    @Test fun staffelungWieBisher() {
        // Depot-Reiter: vier Abrufe zugleich, je Wert zwei (Yahoo und Google)
        assertEquals(2, WertNachrichten.werteJeGruppe())
        // Widget: sechs Abrufe zugleich
        assertEquals(3, WertNachrichten.werteJeGruppe(6))
        assertEquals(1, WertNachrichten.werteJeGruppe(1))
    }

    @Test fun quelleUndEintrag() {
        assertEquals("Google News", WertNachrichten.quelle("https://news.google.com/rss/articles/abc"))
        assertEquals("Yahoo Finance", WertNachrichten.quelle("https://finance.yahoo.com/news/x"))
        val e = WertNachrichten.eintrag(n("Titel", 5))
        assertEquals("Titel", e.titel)
        assertEquals(5L, e.zeit)
        assertEquals("Anriss", e.zusammenfassung)
        assertEquals("reuters.com · Yahoo Finance", e.quellenText)
        assertEquals(setOf(Rss.Kategorie.UNTERNEHMEN), e.kategorien)
        // Dieselbe Kennung wie die Schlagzeile der Wertseite: gemerkt ist gemerkt
        assertEquals(n("Titel", 5).id, e.id)
    }

    // ------------------------------------------------------------ Briefe

    @Test fun gemeinsameAbsenderStehenInBeidenListen() {
        val news = Rss.eingebaut(true).filter { it.kategorie == Rss.Kategorie.BRIEFE }
        // Schluessel der News-Quellen und Kennungen der Wertseite bleiben, wie sie gespeichert sind
        val paare = mapOf("oaktree" to "brief-oaktree", "fairfax" to "brief-fairfax", "himalaya" to "brief-himcap", "montaka" to "brief-montaka")
        assertEquals(paare.keys, Investorenbriefe.GEMEINSAM.keys)
        for ((id, schluessel) in paare) {
            val g = Investorenbriefe.GEMEINSAM.getValue(id)
            val a = Investorenbriefe.absender(id)!!
            val q = news.first { it.schluessel == schluessel }
            assertEquals(g.name, a.name)
            assertEquals(g.seite, a.seite)
            assertEquals(g.name, q.name)
            assertEquals(g.seite, q.adresse)
        }
        // Die uebrigen Absender beider Listen sind unveraendert
        assertEquals(6, news.size)
        assertEquals(10, Investorenbriefe.ABSENDER.size)
    }

    // ------------------------------------------------------------ Glocken

    @Test fun finderAlarmAbschalten() {
        val l = listOf(FinderAlarm("USA", "WERT", listOf(0)), FinderAlarm("DAX", "WERT", listOf(0, 1)), FinderAlarm("USA", "DIVIDENDE", emptyList()))
        assertEquals(listOf("DAX|WERT", "USA|DIVIDENDE"), finderAlarmeOhne(l, "USA", "WERT").map { it.universum + "|" + it.vorlage })
        assertEquals(l, finderAlarmeOhne(l, "DAX", "DIVIDENDE"))
    }

    @Test fun glockenStelleNachDenArten() {
        // Kopf, Hauptschalter, je Art eine Zeile, dann die Ueberschrift „Glocken“
        assertEquals(2 + Mitteilungsarten.arten(true).size, Mitteilungsarten.glockenStelle(Mitteilungsarten.arten(true).size))
        assertEquals(8, Mitteilungsarten.glockenStelle(Mitteilungsarten.arten(false).size))
    }

    @Test fun nachrichtDerGlockeNichtDoppelt() {
        val a = Rss.Eintrag("Nvidia hebt Prognose nach Rekordquartal an", "https://a", 10)
        val b = Rss.Eintrag("Rekordquartal: Nvidia hebt Prognose deutlich an", "https://b", 11)
        val c = Rss.Eintrag("EZB senkt den Leitzins um 25 Basispunkte", "https://c", 12)
        val alle = listOf(a, b, c)
        // Ohne Glocke bleibt alles
        assertEquals(alle, Wertglocke.ohneGlockenNews(alle, emptyList()))
        // Die Glocke meldet a: a faellt weg, c bleibt
        val rest = Wertglocke.ohneGlockenNews(alle, listOf(a.titel))
        assertFalse(a in rest)
        assertTrue(c in rest)
        // Dieselbe Geschichte in anderer Formulierung faellt ebenfalls weg
        assertEquals(de.troetenrepublik.app.market.Wichtig.gleicheGeschichte(a.titel, b.titel), b !in rest)
    }

    // ------------------------------------------------------------ Eigene Feeds: Bestand bleibt

    @Test fun frueherEingetrageneFeedsBleibenQuellen() {
        val s = Settings(newsEigene = "Mein Blog\thttps://blog.example.org/feed\n\thttps://ohne-name.example.org/rss")
        assertEquals(listOf("Mein Blog" to "https://blog.example.org/feed", "" to "https://ohne-name.example.org/rss"), s.eigeneFeeds)
        val quellen = s.eigeneFeeds.map { (name, url) -> Rss.eigene(name, url) }
        assertEquals(listOf("eigen:https://blog.example.org/feed", "eigen:https://ohne-name.example.org/rss"), quellen.map { it.schluessel })
        assertTrue(quellen.all { it.anbieter == Rss.Anbieter.EIGENE && !EigenerFeed.istZugang(it) })
        // Im Feed-Blatt waehlbar: ein Feed mit der alten Quelle zeigt deren Schlagzeilen
        val feed = EigenerFeed.Feed("f1", "Blogs", quellen = listOf("eigen:https://blog.example.org/feed"))
        assertTrue("eigen:https://blog.example.org/feed" in EigenerFeed.schluessel(feed, quellen))
        assertTrue(EigenerFeed.fehlend(feed, quellen).isEmpty())
        // Dieselbe Adresse als Zugang eines Feeds wird nicht ein zweites Mal abgerufen
        val mitZugang = EigenerFeed.Feed("f2", "Doppelt", zugaenge = listOf(EigenerFeed.Zugang("Blog", "https://blog.example.org/feed")))
        assertTrue(EigenerFeed.quellen(listOf(mitZugang), quellen).isEmpty())
        assertEquals(setOf("eigen:https://blog.example.org/feed"), EigenerFeed.schluessel(mitZugang, quellen))
    }
}
