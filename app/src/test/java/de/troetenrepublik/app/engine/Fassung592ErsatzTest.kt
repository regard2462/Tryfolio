package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Politik
import de.troetenrepublik.app.market.SenatSpiegel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// Fassung 5.92: Ersatzquellen (Senatsspiegel, CAPE, Margin-Kredite, Lieferketten, Fracht,
// Bahn, Halbleiter, Hacker News, Mastodon), ohne Netz mit kurzen Beispielen.
class Fassung592ErsatzTest {

    private fun tag(s: String) = LocalDate.parse(s).toEpochDay()

    // ------------------------------------------------------------ Senatsspiegel

    private val bekannt = listOf(
        Politik.SenatMeldung("0541be4f-4f96-4d84-8d6f-d349f218bb2a", "John", "Boozman", eingang = "2026-10-07"),
        Politik.SenatMeldung("11111111-2222-3333-4444-555555555555", "James Conley", "Justice, II", eingang = "2026-09-28"),
        Politik.SenatMeldung("22222222-2222-3333-4444-555555555555", "Angus S", "King, Jr.", eingang = "2026-08-01"),
    )

    private val spiegel = """
        [{"id":"senate_7dfdae57-9401-4bf1-9059-85d1e5ee4a84_t0","source_id":"senate_efd","transaction_date":"2026-09-25","filing_date":"2026-10-21",
          "owner":"Joint","ticker":"APTV","asset_name":"Aptiv PLC Ordinary Shares","asset_type":"Stock","transaction_type":"Sale (Partial)",
          "amount_range_low":1001,"amount_range_high":15000,"amount_range_label":"${'$'}1,001 - ${'$'}15,000","comment":null,
          "filer_id":"senate_john_boozman","filer_name":"John Boozman","chamber":"senate",
          "doc_url":"https://efdsearch.senate.gov/search/view/ptr/7dfdae57-9401-4bf1-9059-85d1e5ee4a84/","ret_since":-1.8},
         {"id":"senate_7dfdae57-9401-4bf1-9059-85d1e5ee4a84_t1","source_id":"senate_efd","transaction_date":"2026-09-24","filing_date":"2026-10-21",
          "owner":"Spouse","ticker":"MU","asset_name":"Micron Technology, Inc. - Common Stock","asset_type":"Stock","transaction_type":"Purchase",
          "amount_range_low":15001,"amount_range_high":50000,"filer_id":"senate_john_boozman","filer_name":"John Boozman","chamber":"senate",
          "doc_url":"https://efdsearch.senate.gov/search/view/ptr/7dfdae57-9401-4bf1-9059-85d1e5ee4a84/"},
         {"id":"senate_6aa5ccb2-ff27-491a-9314-778b9ab37964_t0","source_id":"senate_efd","transaction_date":"2026-09-08","filing_date":"2026-10-22",
          "owner":"Self","ticker":"--","asset_name":"Williams Companies\n      Option Type: Call Strike price: ${'$'}80.00\n   Expires: 2026-12-18",
          "asset_type":"Stock Option","transaction_type":"Sale (Full)","amount_range_low":50000001,"amount_range_high":null,
          "amount_range_label":"Over ${'$'}50,000,000","filer_id":"senate_alan_armstrong","filer_name":"Alan Armstrong","chamber":"senate"},
         {"id":"senate_33333333-2222-3333-4444-555555555555_t0","source_id":"senate_efd","transaction_date":"2026-09-01","filing_date":"2026-10-10",
          "owner":"Self","ticker":"WMT","asset_name":"Walmart","asset_type":"Stock","transaction_type":"Purchase","amount_range_low":1001,
          "amount_range_high":15000,"filer_id":"senate_jamesconley_justiceii","filer_name":"James Conley Justice","chamber":"senate"},
         {"id":"senate_0541be4f-4f96-4d84-8d6f-d349f218bb2a_t0","source_id":"senate_efd","transaction_date":"2026-09-16","filing_date":"2026-10-07",
          "owner":"Self","ticker":"DELL","asset_name":"Dell","asset_type":"Stock","transaction_type":"Purchase","amount_range_low":1001,
          "amount_range_high":15000,"filer_id":"senate_john_boozman","filer_name":"John Boozman","chamber":"senate"},
         {"id":"house_1","source_id":"house_clerk","transaction_date":"2026-09-01","filing_date":"2026-10-20","owner":null,"ticker":"AAPL",
          "asset_name":"Apple","asset_type":"Stock","transaction_type":"Purchase","amount_range_low":1001,"amount_range_high":15000,
          "filer_id":"house_x","filer_name":"X Y","chamber":"house"}]
    """.trimIndent()

    @Test fun senatsspiegelNimmtNurNeueSenatsmeldungen() {
        val neu = SenatSpiegel.neue(spiegel, bekannt)!!
        // Bekannte Meldung und Repraesentantenhaus fallen weg, neueste zuerst
        assertEquals(listOf("6aa5ccb2-ff27-491a-9314-778b9ab37964", "7dfdae57-9401-4bf1-9059-85d1e5ee4a84", "33333333-2222-3333-4444-555555555555"), neu.map { it.id })
        val boozman = neu[1]
        // Person wie im mitgebrachten Stand, damit Glocke und Seite dieselbe sind
        assertEquals(bekannt[0].person, boozman.person)
        assertEquals("2026-10-21", boozman.eingang)
        assertEquals(2, boozman.geschaefte.size)
        val aptv = boozman.geschaefte.first { it.k == "APTV" }
        assertEquals("JT", aptv.i)
        assertEquals("T", aptv.a)
        assertEquals(1001L, aptv.von)
        assertEquals(15000L, aptv.bis)
        assertEquals("SP", boozman.geschaefte.first { it.k == "MU" }.i)
        assertEquals("K", boozman.geschaefte.first { it.k == "MU" }.a)
    }

    @Test fun senatsspiegelOrdnetNamenZuUndLiestSpannen() {
        val neu = SenatSpiegel.neue(spiegel, bekannt)!!
        // Namenszusatz: „James Conley Justice“ gehoert zu „Justice, II“
        assertEquals(bekannt[1].person, neu.first { it.id.startsWith("3333") }.person)
        // Unbekannte Person: Vor- und Nachname aus dem Namen
        val armstrong = neu.first()
        assertEquals("Armstrong", armstrong.nachname)
        assertEquals("Alan", armstrong.vorname)
        val g = armstrong.geschaefte.single()
        assertNull(g.k)
        assertEquals("V", g.a)
        assertEquals(50_000_001L, g.von)
        assertNull(g.bis)
        // Leerraum der Optionsangaben zusammengezogen, wie es Derivate.kt erwartet
        assertTrue(g.n.contains("Option Type: Call Strike price: \$80.00 Expires: 2026-12-18"))
        assertEquals("OP", Politik.typ(g.t))
    }

    @Test fun senatsspiegelUnlesbarIstNull() {
        assertNull(SenatSpiegel.neue("<html>", bekannt))
        assertEquals(emptyList<Politik.SenatMeldung>(), SenatSpiegel.neue("[]", bekannt))
    }

    @Test fun senatsspiegelZusammenfuehren() {
        val basis = Politik.SenatDatei("2026-10-15", meldungen = bekannt)
        val neu = SenatSpiegel.neue(spiegel, bekannt)!!
        val d = SenatSpiegel.zusammen(basis, neu)!!
        assertEquals("2026-10-22", d.stand)
        assertEquals(6, d.meldungen.size)
        assertEquals("6aa5ccb2-ff27-491a-9314-778b9ab37964", d.meldungen.first().id)
        // Doppelte fallen weg
        assertEquals(6, SenatSpiegel.zusammen(d, neu)!!.meldungen.size)
        // Ohne neue bleibt der Stand
        assertTrue(SenatSpiegel.zusammen(basis, emptyList()) === basis)
        // Gespeicherter Stand liest sich zurueck
        val st = SenatSpiegel.Stand(5L, neu)
        assertEquals(st, SenatSpiegel.standLesen(SenatSpiegel.standText(st)))
    }

    // ------------------------------------------------------------ Monatsreihen

    private fun monate(bis: String, n: Int, wert: (Int) -> Double): List<Pair<Long, Double>> {
        val e = LocalDate.parse(bis)
        return (n - 1 downTo 0).map { i -> e.minusMonths(i.toLong()).toEpochDay() to wert(i) }
    }

    @Test fun jahresvergleichGegenVorjahresmonat() {
        // Heute 97, vor einem Jahr 100, im Vormonat 98
        val r = monate("2026-09-01", 24) { i -> when (i) { 0 -> 97.0; 1 -> 98.0; 12 -> 100.0; else -> 99.0 } }
        val s = Ersatzquellen.jahresvergleich(Ersatzquellen.FRACHT, r)!!
        assertEquals(-3.0, s.jahr!!, 1e-9)
        assertEquals(Fruehwarnung.Ampel.ROT, s.ampel)
        assertEquals((97.0 / 98 - 1) * 100, s.vormonat!!, 1e-9)
        assertEquals(tag("2026-09-01"), s.stand)
        val gelb = Ersatzquellen.jahresvergleich(Ersatzquellen.BAHN, monate("2026-09-01", 24) { i -> if (i == 0) 99.0 else 100.0 })!!
        assertEquals(Fruehwarnung.Ampel.GELB, gelb.ampel)
        val gruen = Ersatzquellen.jahresvergleich(Ersatzquellen.HALBLEITER, monate("2026-09-01", 24) { i -> 200.0 - i })!!
        assertEquals(Fruehwarnung.Ampel.GRUEN, gruen.ampel)
        // Zu kurz oder ohne Vorjahresmonat
        assertNull(Ersatzquellen.jahresvergleich(Ersatzquellen.FRACHT, monate("2026-09-01", 12) { 1.0 }))
    }

    @Test fun lieferkettenNachStandardabweichungen() {
        val r = monate("2026-09-30", 24) { i -> if (i == 0) 1.28 else if (i == 12) 0.1 else 0.9 }
        val s = Ersatzquellen.lieferketten(r)!!
        assertEquals(Fruehwarnung.Ampel.GELB, s.ampel)
        assertEquals(1.18, s.jahr!!, 1e-9)
        assertEquals(0.38, s.vormonat!!, 1e-9)
        assertEquals(Fruehwarnung.Ampel.ROT, Ersatzquellen.lieferketten(monate("2026-09-30", 3) { 1.6 })!!.ampel)
        assertEquals(Fruehwarnung.Ampel.GRUEN, Ersatzquellen.lieferketten(monate("2026-09-30", 3) { -0.4 })!!.ampel)
    }

    // Blatt wie in der Datei der New York Fed: Datum als Text (fehlt in den Zahlen), Wert in Spalte 1 ab Zeile 5.
    private fun gscpiBlatt(n: Int, start: Int = 5): Map<Int, Map<Int, Double>> =
        (0 until n).associate { i -> start + i to mapOf(1 to i / 100.0) } + (1 to mapOf(3 to 7.0))

    @Test fun gscpiZaehltMonateAbJanuar1998() {
        val heute = LocalDate.parse("2026-10-28")
        // Januar 1998 bis September 2026 sind 345 Monate
        val r = Ersatzquellen.gscpiAusZahlen(listOf(emptyMap(), gscpiBlatt(345)), heute)
        assertEquals(345, r.size)
        assertEquals(tag("1998-01-31"), r.first().first)
        assertEquals(tag("2026-09-30"), r.last().first)
        assertEquals(3.44, r.last().second, 1e-9)
        // Ein Monat zu viel liegt in der Zukunft, viele zu wenig sind zu alt: dann ist die Datei anders aufgebaut
        assertTrue(Ersatzquellen.gscpiAusZahlen(listOf(gscpiBlatt(355)), heute).isEmpty())
        assertTrue(Ersatzquellen.gscpiAusZahlen(listOf(gscpiBlatt(330)), heute).isEmpty())
        // Luecke
        val luecke = gscpiBlatt(345) - 100
        assertTrue(Ersatzquellen.gscpiAusZahlen(listOf(luecke), heute).isEmpty())
    }

    // ------------------------------------------------------------ CAPE und Margin-Kredite

    @Test fun capeAusShillerDaten() {
        // Monate 1990.01 bis 2026.10, CAPE steigt von 10 auf gut 40; dazu Kopf- und Fusszeilen ohne Zahlen
        val zeilen = HashMap<Int, Map<Int, Double>>()
        var z = 8
        for (j in 1990..2026) for (m in 1..12) {
            if (j == 2026 && m > 10) break
            val d = j + m / 100.0
            zeilen[z++] = mapOf(0 to d, 1 to 100.0, 12 to 10.0 + (z - 8) * 0.07)
        }
        zeilen[z] = mapOf(0 to 2026.11)
        val c = Ersatzquellen.capeAusZahlen(listOf(mapOf(0 to mapOf(0 to 1.0)), zeilen))!!
        assertEquals(2026, c.jahr)
        assertEquals(10, c.monat)
        assertEquals(100.0, c.rang, 1e-9)
        assertEquals(tag("2026-10-01"), c.stand)
        val b = Blasen.cape(c)!!
        assertEquals(Blasen.CAPE, b.schluessel)
        assertEquals(Fruehwarnung.Ampel.ROT, b.ampel)
        assertEquals(Fruehwarnung.Ampel.GELB, Blasen.cape(c.copy(wert = 25.0))!!.ampel)
        assertEquals(Fruehwarnung.Ampel.GRUEN, Blasen.cape(c.copy(wert = 17.0))!!.ampel)
        // Zu wenige Monate
        assertNull(Ersatzquellen.capeAusZahlen(listOf(zeilen.entries.take(50).associate { it.key to it.value })))
    }

    @Test fun shillerLinkAusDerSeite() {
        val html = """<a href="//img1.wsimg.com/blobby/go/abc/downloads/def/ie_data.xls?ver=17&amp;x=1">Data</a> <a href="//img1.wsimg.com/x/Fig3-1.xls">"""
        assertEquals("https://img1.wsimg.com/blobby/go/abc/downloads/def/ie_data.xls?ver=17&x=1", Ersatzquellen.shillerLink(html))
        assertNull(Ersatzquellen.shillerLink("<html></html>"))
    }

    @Test fun marginWachstumGegenVorjahr() {
        val r = listOf(tag("2025-04-01") to 506_422.0, tag("2025-07-01") to 566_101.0, tag("2026-01-01") to 622_199.0, tag("2026-04-01") to 742_321.0)
        val m = Ersatzquellen.margin(r)!!
        assertEquals((742_321.0 / 506_422 - 1) * 100, m.wachstum, 1e-9)
        assertEquals(742.321, m.milliarden, 1e-9)
        val b = Blasen.margin(m)!!
        assertEquals(Fruehwarnung.Ampel.ROT, b.ampel)
        assertEquals(Fruehwarnung.Ampel.GELB, Blasen.margin(m.copy(wachstum = 25.0))!!.ampel)
        assertEquals(Fruehwarnung.Ampel.GRUEN, Blasen.margin(m.copy(wachstum = 5.0))!!.ampel)
        assertNull(Ersatzquellen.margin(r.takeLast(2)))
        assertTrue(Blasen.MARGIN in Blasen.DAFUER && Blasen.CAPE in Blasen.DAFUER)
    }

    // ------------------------------------------------------------ Hacker News und Mastodon

    @Test fun suchnameUndHashtag() {
        assertEquals("Alphabet", Ersatzquellen.suchname("Alphabet Inc. Class A"))
        assertEquals("NVIDIA", Ersatzquellen.suchname("NVIDIA Corporation"))
        assertEquals("Micron Technology", Ersatzquellen.suchname("Micron Technology, Inc."))
        assertEquals("Siemens", Ersatzquellen.suchname("Siemens AG"))
        assertEquals("nvidia", Ersatzquellen.hashtag("NVIDIA Corporation"))
        assertNull(Ersatzquellen.hashtag("3M"))
    }

    @Test fun hackerNewsUndMastodonLesen() {
        val hn = """{"exhaustive":{"nbHits":true},"hits":[],"nbHits":42,"page":0}"""
        assertEquals(42, Ersatzquellen.hnTreffer(hn))
        assertNull(Ersatzquellen.hnTreffer("x"))
        val h = Ersatzquellen.hackerNews(42, 221)
        assertEquals(44.75, h.schnittVorher, 1e-9)
        assertEquals((42 / 44.75 - 1) * 100, h.veraenderung!!, 1e-9)
        assertNull(Ersatzquellen.hackerNews(0, 0).veraenderung)
        val m = """{"id":"6299","name":"nvidia","history":[{"day":"1793145600","accounts":"13","uses":"14"},{"day":"1793059200","accounts":"31","uses":"50"}]}"""
        val v = Ersatzquellen.mastodonVerlauf(m)
        assertEquals(listOf(50, 14), v.map { it.beitraege })
        assertEquals(1793059200L / 86_400, v.first().tag)
        assertTrue(Ersatzquellen.mastodonVerlauf("[]").isEmpty())
        assertTrue(Ersatzquellen.hnAdresse("Micron Technology", 1L).contains("query=%22Micron+Technology%22"))
    }

    // ------------------------------------------------------------ Hosts der Selbstreparatur

    @Test fun neueHostsHabenEineQuelle() {
        assertEquals("uswirtschaft", Quellenbuendel.quelleZu("www.newyorkfed.org"))
        assertEquals("uswirtschaft", Quellenbuendel.quelleZu("shillerdata.com"))
        assertEquals("uswirtschaft", Quellenbuendel.quelleZu("img1.wsimg.com"))
        assertEquals("uswirtschaft", Quellenbuendel.quelleZu("fred.stlouisfed.org"))
        assertEquals("erwaehnungen", Quellenbuendel.quelleZu("hn.algolia.com"))
        assertEquals("erwaehnungen", Quellenbuendel.quelleZu("mastodon.social"))
        assertEquals("github", Quellenbuendel.quelleZu("cdn.jsdelivr.net"))
        assertNotNull(Quellenbuendel.quelleZu("raw.githubusercontent.com"))
    }
}
