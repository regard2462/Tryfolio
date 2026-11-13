package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.MITTEILUNG_ALLE
import de.troetenrepublik.app.data.MITTEILUNG_AUS
import de.troetenrepublik.app.data.MITTEILUNG_EIGENE
import de.troetenrepublik.app.data.MITTEILUNG_EIGENE_WICHTIGE
import de.troetenrepublik.app.data.MITTEILUNG_QUELLEN
import de.troetenrepublik.app.data.MITTEILUNG_WICHTIGE
import de.troetenrepublik.app.market.Rss
import org.junit.Assert.*
import org.junit.Test

// Tests fuer eigene News-Feeds (5.96): Speichertext, Adresspruefung, Quellen, Zusammenfuehren und Mitteilungen.
class Fassung596FeedTest {

    // Adresse ist in Ordnung und wird zu erwartet bereinigt.
    private fun ok(eingabe: String, erwartet: String) {
        val a = EigenerFeed.adresse(eingabe)
        assertNull(a.fehler)
        assertEquals(erwartet, a.adresse)
    }

    // Adresse wird mit diesem Fehler abgelehnt.
    private fun fehler(eingabe: String, erwartet: EigenerFeed.Fehler) {
        val a = EigenerFeed.adresse(eingabe)
        assertNull(a.adresse)
        assertEquals(erwartet, a.fehler)
    }

    // Speichertext schreiben und zuruecklesen: Reihenfolge und Felder bleiben, Tab im Namen wird Leerzeichen.
    @Test
    fun speichertextHinUndZurueck() {
        val feeds = listOf(
            EigenerFeed.Feed("f1", "Wirtschaft", listOf("ntv", "tagesschau-wirtschaft"), listOf(EigenerFeed.Zugang("Mein Blog", "https://beispiel.de/feed"))),
            EigenerFeed.Feed("f2", "Nur Zugaenge", zugaenge = listOf(EigenerFeed.Zugang("Zweiter Blog", "https://blog.beispiel.de/rss"))),
        )
        assertEquals(feeds, EigenerFeed.lesen(EigenerFeed.text(feeds)))
        assertEquals("F\tf3\tA B\t1", EigenerFeed.text(listOf(EigenerFeed.Feed("f3", "A\tB"))))
        assertEquals(listOf(EigenerFeed.Feed("f3", "A B")), EigenerFeed.lesen("F\tf3\tA B\n"))
    }

    // lesen verzeiht kaputte Zeilen, Q vor dem ersten F, doppelte Kennung, leeren Namen und ungueltige Zugaenge.
    @Test
    fun lesenRobust() {
        assertEquals(emptyList<EigenerFeed.Feed>(), EigenerFeed.lesen(""))
        val text = "Q\tntv\n" +                                     // vor dem ersten F, keine Wirkung
            "Muell\n" +                                             // unbekannte Zeile
            "F\tf1\tWirtschaft\n" +
            "Q\tntv\n" +
            "Q\tsz\n" +
            "Q\tntv\n" +                                            // Dublette entfaellt
            "Z\tBlog\tftp://beispiel.de/feed\n" +                   // Schema nicht erlaubt
            "Z\tMit Token\thttps://beispiel.de/feed?token=abc\n" +  // Zugangsdaten in der Adresse
            "Z\tNur Titel\n" +                                      // kaputte Zeile
            "Z\tGut\thttps://gut.de/feed\n" +
            "F\tf1\tZweite\n" +                                     // doppelte Kennung, der erste gewinnt
            "Q\tbbc\n" +                                            // gehoert zum verworfenen zweiten F
            "F\tf3\t\n" +                                           // leerer Name
            "F\t\tOhne Kennung\n"                                   // leere Kennung
        val erwartet = listOf(
            EigenerFeed.Feed("f1", "Wirtschaft", listOf("ntv", "sz"), listOf(EigenerFeed.Zugang("Gut", "https://gut.de/feed"))),
        )
        assertEquals(erwartet, EigenerFeed.lesen(text))
    }

    // speichern ersetzt bei gleicher Kennung an derselben Stelle, haengt neue hinten an und begrenzt die Zahl.
    @Test
    fun speichernErsetztUndBegrenzt() {
        val f1 = EigenerFeed.Feed("f1", "Eins", listOf("ntv"))
        val f2 = EigenerFeed.Feed("f2", "Zwei", zugaenge = listOf(EigenerFeed.Zugang("Blog", "https://beispiel.de/feed")))
        val feeds = listOf(f1, f2)
        assertEquals(listOf(EigenerFeed.Feed("f1", "Eins neu", listOf("sz")), f2), EigenerFeed.speichern(feeds, EigenerFeed.Feed("f1", "Eins neu", listOf("sz"))))
        val f3 = EigenerFeed.Feed("f3", "Drei", listOf("bbc"))
        assertEquals(listOf(f1, f2, f3), EigenerFeed.speichern(feeds, f3))
        assertEquals(feeds, EigenerFeed.speichern(feeds, EigenerFeed.Feed("f4", "Ohne Quelle")))
        val voll = (1..EigenerFeed.MAX_FEEDS).map { EigenerFeed.Feed("k$it", "Feed $it", listOf("ntv")) }
        assertEquals(voll, EigenerFeed.speichern(voll, EigenerFeed.Feed("neu", "Zu viel", listOf("sz"))))
        assertEquals(listOf(f2), EigenerFeed.entfernen(feeds, "f1"))
    }

    // neueKennung haengt Zaehlnummern an, bis die Kennung frei ist.
    @Test
    fun neueKennungEindeutig() {
        val feeds = listOf(EigenerFeed.Feed("f1", "Eins"), EigenerFeed.Feed("f2", "Zwei"))
        assertEquals("f3", EigenerFeed.neueKennung(feeds, 3L))
        val mitF3 = feeds + EigenerFeed.Feed("f3", "Drei")
        assertEquals("f3-2", EigenerFeed.neueKennung(mitF3, 3L))
        assertEquals("f3-3", EigenerFeed.neueKennung(mitF3 + EigenerFeed.Feed("f3-2", "Noch einer"), 3L))
    }

    // adresse bereinigt gueltige Eingaben und lehnt andere mit Fehler ab.
    @Test
    fun adressen() {
        ok("https://www.n-tv.de/wirtschaft/rss", "https://www.n-tv.de/wirtschaft/rss")
        ok("HTTPS://WWW.Tagesschau.DE/wirtschaft/index~rss2.xml#oben", "https://www.tagesschau.de/wirtschaft/index~rss2.xml")
        ok("www.n-tv.de/wirtschaft/rss", "https://www.n-tv.de/wirtschaft/rss")
        ok("beispiel.de:8080/feed", "https://beispiel.de:8080/feed")
        ok("https://beispiel.de/rss?kategorie=wirtschaft", "https://beispiel.de/rss?kategorie=wirtschaft")
        ok("https://bücher.de/feed", "https://bücher.de/feed")
        fehler("", EigenerFeed.Fehler.LEER)
        fehler("https://", EigenerFeed.Fehler.LEER)
        fehler("ftp://beispiel.de/feed", EigenerFeed.Fehler.SCHEMA)
        fehler("feed://beispiel.de/rss", EigenerFeed.Fehler.SCHEMA)
        fehler("javascript:alert(1)", EigenerFeed.Fehler.SCHEMA)
        fehler("file:///etc/passwd", EigenerFeed.Fehler.SCHEMA)
        fehler("https://nutzer:geheim@beispiel.de/feed", EigenerFeed.Fehler.ZUGANGSDATEN)
        fehler("https://beispiel.de/feed?token=abc", EigenerFeed.Fehler.ZUGANGSDATEN)
        fehler("https://beispiel.de/rss?ApiKey=1", EigenerFeed.Fehler.ZUGANGSDATEN)
        fehler("https://beispiel/feed", EigenerFeed.Fehler.UNGUELTIG)
        fehler("https://bei spiel.de/x", EigenerFeed.Fehler.UNGUELTIG)
        fehler("https://-a.de/x", EigenerFeed.Fehler.UNGUELTIG)
        fehler("https://beispiel.de:99999/x", EigenerFeed.Fehler.UNGUELTIG)
        fehler("https://beispiel.de./x", EigenerFeed.Fehler.UNGUELTIG)
    }

    // eingebautZu erkennt eingebaute Quellen ueber Schema, www und Schraegstrich hinweg.
    @Test
    fun eingebautErkannt() {
        val quellen = Rss.eingebaut(true)
        val ntv = EigenerFeed.eingebautZu("http://n-tv.de/wirtschaft/rss/", quellen)
        assertNotNull(ntv)
        assertEquals("ntv", ntv!!.schluessel)
        assertNull(EigenerFeed.eingebautZu("https://fremd.example.org/feed", quellen))
    }

    // quellen legt je Adresse nur eine Quelle an; Adressen eingebauter Quellen bleiben einfach.
    @Test
    fun quellenOhneDoppelteAdresse() {
        val vorhandene = listOf(Rss.Quelle("ntv", "n-tv", "https://www.n-tv.de/wirtschaft/rss", Rss.Kategorie.MAERKTE, Rss.Anbieter.PRESSE))
        val feeds = listOf(
            EigenerFeed.Feed("f1", "Eins", zugaenge = listOf(EigenerFeed.Zugang("Blog", "https://beispiel.de/feed"))),
            EigenerFeed.Feed("f2", "Zwei", zugaenge = listOf(EigenerFeed.Zugang("Dasselbe", "https://beispiel.de/feed"))),
            EigenerFeed.Feed("f3", "Drei", zugaenge = listOf(EigenerFeed.Zugang("N-TV direkt", "http://n-tv.de/wirtschaft/rss/"))),
        )
        val zugaenge = EigenerFeed.quellen(feeds, vorhandene)
        assertEquals(1, zugaenge.size)
        val q = zugaenge.first()
        assertEquals("feed:https://beispiel.de/feed", q.schluessel)
        assertEquals("Blog", q.name)
        assertEquals(Rss.Kategorie.EIGENE, q.kategorie)
        assertEquals(Rss.Anbieter.EIGENE, q.anbieter)
        assertTrue(EigenerFeed.istZugang(q))
        val alle = vorhandene + zugaenge
        assertEquals(setOf("feed:https://beispiel.de/feed"), EigenerFeed.schluessel(feeds[0], alle))
        assertEquals(setOf("feed:https://beispiel.de/feed"), EigenerFeed.schluessel(feeds[1], alle))
        assertEquals(setOf("ntv"), EigenerFeed.schluessel(feeds[2], alle))
    }

    // eintraege fuehrt die Meldungen der gewaehlten Quellen ohne Dubletten und nach Zeit sortiert zusammen.
    @Test
    fun zusammenfuehrenOhneDubletten() {
        val feed = EigenerFeed.Feed("f1", "Eins", listOf("ntv", "tagesschau-wirtschaft"), listOf(EigenerFeed.Zugang("Blog", "https://beispiel.de/feed")))
        val zins = Rss.Eintrag("Zinsentscheid", "https://www.n-tv.de/zins.html", 3000L, quellen = setOf("ntv"))
        val doppeltLink = Rss.Eintrag("Dieselbe Meldung", "https://www.n-tv.de/zins.html", 2900L, quellen = setOf("tagesschau-wirtschaft"))
        val oel = Rss.Eintrag("Ölpreis steigt", "https://beispiel.de/a", 2800L, quellen = setOf("feed:https://beispiel.de/feed"))
        val doppeltTitel = Rss.Eintrag("ölpreis steigt", "https://beispiel.de/b", 2750L, quellen = setOf("feed:https://beispiel.de/feed"))
        val fremd = Rss.Eintrag("Fremde Meldung", "https://www.bbc.co.uk/x", 3200L, quellen = setOf("bbc"))
        val ohneZeit = Rss.Eintrag("Ohne Zeitstempel", "https://www.tagesschau.de/x", 0L, quellen = setOf("tagesschau-wirtschaft"))
        val alle = listOf(fremd, zins, doppeltLink, oel, doppeltTitel, ohneZeit)
        // Neueste zuerst, Dubletten einmal, ohne Zeit am Ende
        assertEquals(listOf(zins, oel, ohneZeit), EigenerFeed.eintraege(alle, feed, emptyList()))
    }

    // fehlend nennt gewaehlte Schluessel, zu denen es keine Quelle gibt.
    @Test
    fun fehlendeQuellen() {
        val feed = EigenerFeed.Feed("f1", "Eins", listOf("ntv", "sz"))
        val quellen = listOf(Rss.Quelle("ntv", "n-tv", "https://www.n-tv.de/wirtschaft/rss", Rss.Kategorie.MAERKTE, Rss.Anbieter.PRESSE))
        assertEquals(listOf("sz"), EigenerFeed.fehlend(feed, quellen))
    }

    // feedTitel nimmt den Titel des Feeds, nicht den des Bilds oder der Meldungen.
    @Test
    fun feedTitelRssUndAtom() {
        val rss = """<?xml version="1.0" encoding="UTF-8"?><rss version="2.0"><channel><title>A &amp; B</title><image><url>https://beispiel.de/logo.png</url><title>Logo</title></image><item><title>X</title></item></channel></rss>"""
        assertEquals("A & B", EigenerFeed.feedTitel(rss))
        val atom = """<?xml version="1.0" encoding="UTF-8"?><feed xmlns="http://www.w3.org/2005/Atom"><title type="text">Mein Blog</title><entry><title>Y</title></entry></feed>"""
        assertEquals("Mein Blog", EigenerFeed.feedTitel(atom))
        assertEquals("", EigenerFeed.feedTitel("""<rss version="2.0"><channel><item><title>X</title></item></channel></rss>"""))
    }

    // Auszug eines echten RSS-Feeds aus den Testressourcen.
    @Test
    fun echterRssAuszug() {
        val bytes = javaClass.classLoader!!.getResourceAsStream("feeds/fed-press-auszug.xml")!!.readBytes()
        val xml = Rss.text(bytes)
        assertEquals("FRB: Press Release - All Releases", EigenerFeed.feedTitel(xml))
        val eintraege = Rss.lesen(xml, "Fed", Rss.Kategorie.ZENTRALBANKEN, schluessel = "fed")
        assertEquals(3, eintraege.size)
        assertEquals("Federal Reserve Board announces approval of application by The Bank of Nova Scotia", eintraege[0].titel)
        assertEquals("https://www.federalreserve.gov/newsevents/pressreleases/orders20261029a.htm", eintraege[0].link)
        assertEquals(java.time.Instant.parse("2026-10-29T20:30:00Z").toEpochMilli(), eintraege[0].zeit)
        assertEquals("Federal Reserve issues FOMC statement", eintraege[1].titel)
        assertEquals(java.time.Instant.parse("2026-10-28T18:00:00Z").toEpochMilli(), eintraege[1].zeit)
    }

    // Auszug eines echten Atom-Feeds aus den Testressourcen.
    @Test
    fun echterAtomAuszug() {
        val bytes = javaClass.classLoader!!.getResourceAsStream("feeds/usgs-atom-auszug.xml")!!.readBytes()
        val xml = Rss.text(bytes)
        assertEquals("USGS Significant Earthquakes, Past Month", EigenerFeed.feedTitel(xml))
        val eintraege = Rss.lesen(xml, "USGS", Rss.Kategorie.EIGENE, schluessel = "feed:x")
        assertEquals(3, eintraege.size)
        assertEquals("M 6.4 - southeast of Easter Island", eintraege[0].titel)
        assertEquals("https://earthquake.usgs.gov/earthquakes/eventpage/us7000ttxz", eintraege[0].link)
        assertEquals(java.time.Instant.parse("2026-10-30T10:38:47.040Z").toEpochMilli(), eintraege[0].zeit)
        assertEquals("https://earthquake.usgs.gov/earthquakes/eventpage/us6000u4r9", eintraege[1].link)
    }

    // Bei Atom zaehlt der alternate-Link, auch wenn replies-, edit- und self-Links zuerst stehen.
    @Test
    fun atomLinkAlternateVorKommentaren() {
        val atom = """<?xml version="1.0" encoding="UTF-8"?><feed xmlns="http://www.w3.org/2005/Atom"><title>Beispielblog</title>""" +
            """<entry><title>Erster Beitrag</title>""" +
            """<link rel='replies' type='application/atom+xml' href='https://beispiel.blogspot.com/feeds/1/comments/default' title='Kommentare'/>""" +
            """<link rel='edit' type='application/atom+xml' href='https://www.blogger.com/feeds/1/posts/default/1'/>""" +
            """<link rel='self' type='application/atom+xml' href='https://www.blogger.com/feeds/1/posts/default/1'/>""" +
            """<link rel='alternate' type='text/html' href='https://beispiel.blogspot.com/2026/11/beitrag.html'/>""" +
            """</entry>""" +
            """<entry><title>Zweiter Beitrag</title><link href="https://example.org/a"/></entry>""" +
            """</feed>"""
        val eintraege = Rss.lesen(atom, "Blog", Rss.Kategorie.EIGENE)
        assertEquals(2, eintraege.size)
        assertEquals("https://beispiel.blogspot.com/2026/11/beitrag.html", eintraege[0].link)
        assertEquals("https://example.org/a", eintraege[1].link)
    }

    // zuMelden waehlt nach der Stufe aus, die Reihenfolge von frisch bleibt.
    @Test
    fun meldenNachStufe() {
        val feeds = listOf(
            EigenerFeed.Feed("f1", "Eins", zugaenge = listOf(EigenerFeed.Zugang("Blog", "https://eins.de/feed"))),
            EigenerFeed.Feed("f2", "Zwei", zugaenge = listOf(EigenerFeed.Zugang("Anderer Blog", "https://zwei.de/feed")), melden = false),
        )
        val quellen = listOf(
            Rss.Quelle("ntv", "n-tv", "https://www.n-tv.de/wirtschaft/rss", Rss.Kategorie.MAERKTE, Rss.Anbieter.PRESSE),
            Rss.Quelle("feed:https://eins.de/feed", "Blog", "https://eins.de/feed", Rss.Kategorie.EIGENE, Rss.Anbieter.EIGENE),
            Rss.Quelle("feed:https://zwei.de/feed", "Anderer Blog", "https://zwei.de/feed", Rss.Kategorie.EIGENE, Rss.Anbieter.EIGENE),
        )
        val wichtig = Rss.Eintrag("Wichtige Meldung", "https://www.n-tv.de/a", 100L, quellen = setOf("ntv"))
        val unwichtig = Rss.Eintrag("Ganz normale Meldung", "https://www.n-tv.de/b", 90L, quellen = setOf("ntv"))
        val eigener = Rss.Eintrag("Aus dem eigenen Feed", "https://eins.de/c", 80L, quellen = setOf("feed:https://eins.de/feed"))
        val ohneMeldung = Rss.Eintrag("Aus dem Feed ohne Mitteilungen", "https://zwei.de/d", 70L, quellen = setOf("feed:https://zwei.de/feed"))
        val fremd = Rss.Eintrag("Wichtige fremde Meldung", "https://www.bbc.co.uk/e", 60L, quellen = setOf("bbc"))
        val frisch = listOf(wichtig, unwichtig, eigener, ohneMeldung, fremd)
        val wichtige = setOf(wichtig.id, fremd.id)
        assertTrue(EigenerFeed.brauchtWichtige(MITTEILUNG_WICHTIGE))
        assertTrue(EigenerFeed.brauchtWichtige(MITTEILUNG_EIGENE_WICHTIGE))
        assertTrue(EigenerFeed.brauchtWichtige("xyz"))
        assertFalse(EigenerFeed.brauchtWichtige(MITTEILUNG_ALLE))
        assertFalse(EigenerFeed.brauchtWichtige(MITTEILUNG_AUS))
        assertFalse(EigenerFeed.brauchtWichtige(MITTEILUNG_EIGENE))
        assertFalse(EigenerFeed.brauchtWichtige(MITTEILUNG_QUELLEN))
        assertEquals(emptyList<Rss.Eintrag>(), EigenerFeed.zuMelden(frisch, MITTEILUNG_AUS, wichtige, feeds, quellen, emptySet()))
        assertEquals(frisch, EigenerFeed.zuMelden(frisch, MITTEILUNG_ALLE, wichtige, feeds, quellen, emptySet()))
        assertEquals(listOf(wichtig, fremd), EigenerFeed.zuMelden(frisch, MITTEILUNG_WICHTIGE, wichtige, feeds, quellen, emptySet()))
        assertEquals(listOf(eigener), EigenerFeed.zuMelden(frisch, MITTEILUNG_EIGENE, wichtige, feeds, quellen, emptySet()))
        assertEquals(listOf(wichtig, eigener, fremd), EigenerFeed.zuMelden(frisch, MITTEILUNG_EIGENE_WICHTIGE, wichtige, feeds, quellen, emptySet()))
        assertEquals(listOf(fremd), EigenerFeed.zuMelden(frisch, MITTEILUNG_QUELLEN, wichtige, feeds, quellen, setOf("bbc")))
        assertEquals(listOf(wichtig, fremd), EigenerFeed.zuMelden(frisch, "xyz", wichtige, feeds, quellen, emptySet()))
    }

    // meldeQuellen und meldeQuellenText verlieren Schluessel mit Komma nicht.
    @Test
    fun meldeQuellenHinUndZurueck() {
        val text = EigenerFeed.meldeQuellenText(listOf("ntv", "feed:https://beispiel.de/rss?a=1,2", "sz"))
        assertEquals("feed:https://beispiel.de/rss?a=1,2\nntv\nsz", text)
        assertEquals(setOf("ntv", "feed:https://beispiel.de/rss?a=1,2", "sz"), EigenerFeed.meldeQuellen(text))
        assertEquals(setOf("ntv", "sz"), EigenerFeed.meldeQuellen(" ntv \n\nsz\n"))
        assertEquals(emptySet<String>(), EigenerFeed.meldeQuellen(""))
    }

    // Das Melden-Feld steht als viertes Feld in der F-Zeile, Zeilen ohne Feld gelten als melden = true.
    @Test
    fun meldenFeldImSpeichertext() {
        val stumm = EigenerFeed.Feed("f1", "Stumm", listOf("ntv"), melden = false)
        assertEquals("F\tf1\tStumm\t0\nQ\tntv", EigenerFeed.text(listOf(stumm)))
        assertEquals(listOf(stumm), EigenerFeed.lesen(EigenerFeed.text(listOf(stumm))))
        assertEquals(listOf(EigenerFeed.Feed("k", "Name", listOf("ntv"))), EigenerFeed.lesen("F\tk\tName\nQ\tntv\n"))
    }

    // Vorgabe der Mitteilungen bleibt unveraendert.
    @Test
    fun vorgabeUnveraendert() {
        assertEquals(MITTEILUNG_WICHTIGE, de.troetenrepublik.app.data.Settings().newsMitteilung)
        assertEquals("", de.troetenrepublik.app.data.Settings().newsMeldeQuellen)
    }
}
