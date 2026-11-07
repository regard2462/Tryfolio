package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Fruehwarnung.Ampel
import de.troetenrepublik.app.market.Abrufkontingent
import de.troetenrepublik.app.market.InsiderWelt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

// Fassung 5.92: weitere Quellen (Cboe Put/Call, Drewry, AAR, WSTS, Boursa Kuwait, CourtListener, Bluesky), ohne Netz
// mit kurzen, frei erfundenen Beispielen im Aufbau der Quellen.
class Fassung592WeitereQuellenTest {

    private fun tag(s: String) = LocalDate.parse(s).toEpochDay()

    // ------------------------------------------------------------ Handelstage der US-Boersen

    @Test
    fun ostern_und_feiertage() {
        assertEquals(LocalDate.of(2026, 4, 5), WeitereQuellen.ostern(2026))
        assertEquals(LocalDate.of(2025, 4, 20), WeitereQuellen.ostern(2025))
        assertTrue(WeitereQuellen.usFeiertag(LocalDate.of(2026, 4, 3)))   // Karfreitag
        assertTrue(WeitereQuellen.usFeiertag(LocalDate.of(2026, 11, 26))) // Thanksgiving
        assertTrue(WeitereQuellen.usFeiertag(LocalDate.of(2026, 7, 3)))   // 4. Juli am Samstag, frei am Freitag
        assertTrue(WeitereQuellen.usFeiertag(LocalDate.of(2026, 5, 25)))  // Memorial Day
        assertFalse(WeitereQuellen.usFeiertag(LocalDate.of(2026, 11, 27)))
        assertFalse(WeitereQuellen.usHandelstag(LocalDate.of(2026, 10, 31))) // Samstag
    }

    @Test
    fun cboe_nimmt_letzten_sicheren_handelstag() {
        assertEquals(LocalDate.of(2026, 10, 27), WeitereQuellen.cboeTag(LocalDateTime.of(2026, 10, 28, 10, 0)))
        assertEquals(LocalDate.of(2026, 10, 28), WeitereQuellen.cboeTag(LocalDateTime.of(2026, 10, 28, 22, 30)))
        // Montag: Freitag davor; Freitag nach Thanksgiving: Mittwoch davor
        assertEquals(LocalDate.of(2026, 11, 27), WeitereQuellen.cboeTag(LocalDateTime.of(2026, 11, 30, 9, 0)))
        assertEquals(LocalDate.of(2026, 11, 25), WeitereQuellen.cboeTag(LocalDateTime.of(2026, 11, 27, 12, 0)))
        assertEquals("https://cdn.cboe.com/data/us/options/market_statistics/daily/2026-10-27_daily_options",
            WeitereQuellen.cboeAdresse(LocalDate.of(2026, 10, 27)))
    }

    @Test
    fun put_call_lesen() {
        val json = """{"ratios":[{"name":"TOTAL PUT/CALL RATIO","value":"0.91"},{"name":"INDEX PUT/CALL RATIO","value":"1.12"},
            {"name":"EQUITY PUT/CALL RATIO","value":"0.62"},{"name":"OEX PUT/CALL RATIO","value":"0.00"}],
            "SUM OF ALL PRODUCTS":[{"name":"VOLUME","call":100,"put":91,"total":191}]}"""
        val p = WeitereQuellen.putCallLesen(json, LocalDate.of(2026, 10, 27))!!
        assertEquals(0.91, p.gesamt!!, 1e-9)
        assertEquals(0.62, p.aktien!!, 1e-9)
        assertEquals(1.12, p.index!!, 1e-9)
        assertEquals(tag("2026-10-27"), p.tag)
        assertNull(WeitereQuellen.putCallLesen("""{"ratios":[]}""", LocalDate.of(2026, 10, 27)))
        assertNull(WeitereQuellen.putCallLesen("<html>Access Denied</html>", LocalDate.of(2026, 10, 27)))
        assertEquals(0, WeitereQuellen.putCallLage(0.42))
        assertEquals(1, WeitereQuellen.putCallLage(0.62))
        assertEquals(2, WeitereQuellen.putCallLage(0.95))
    }

    // ------------------------------------------------------------ Drewry

    private fun drewry(satz: String) = """<html><head><title>Drewry - World Container Index - 15 Oct</title><script>var x = "increased 99% to $1";</script></head>
        <body><h2>Our detailed assessment for Thursday, 15 Oct 2026</h2><p>The Drewry World Container Index (WCI), a composite of
        eight routes, $satz, driven by rates on the Transpacific. Rates from Shanghai to Rotterdam rose 7% to $3,000 per 40ft.</p></body></html>"""

    @Test
    fun drewry_lesen() {
        val w = WeitereQuellen.wciLesen(drewry("increased 3% to &#36;4,250 per 40ft container"))!!
        assertEquals(tag("2026-10-15"), w.tag)
        assertEquals(4250.0, w.dollar, 1e-9)
        assertEquals(3.0, w.woche, 1e-9)
        val f = WeitereQuellen.wciLesen(drewry("edged down 1.5% to \$2,100 per 40ft container"))!!
        assertEquals(-1.5, f.woche, 1e-9)
        assertEquals(2100.0, f.dollar, 1e-9)
        val g = WeitereQuellen.wciLesen(drewry("remained stable at \$3,333 per 40ft container"))!!
        assertEquals(0.0, g.woche, 1e-9)
        assertNull(WeitereQuellen.wciLesen("<html><body>Page moved</body></html>"))
        assertEquals(Ampel.GRUEN, WeitereQuellen.wciAmpel(3.0))
        assertEquals(Ampel.GELB, WeitereQuellen.wciAmpel(-7.0))
        assertEquals(Ampel.ROT, WeitereQuellen.wciAmpel(20.0))
    }

    // ------------------------------------------------------------ AAR

    @Test
    fun aar_lesen() {
        val rss = """<?xml version="1.0"?><rss><channel><title>Weekly Rail Traffic</title>
            <item><title>AAR Reports Weekly Rail Traffic for the Week Ending October 17, 2026</title><pubDate>Wed, 21 Oct 2026 16:00:00 +0000</pubDate>
            <content:encoded><![CDATA[<p>WASHINGTON &#8211; For this week, total U.S. weekly rail traffic was 500,100 carloads and intermodal units, down 1.2 percent compared with the same week last year.</p>
            <p>Total carloads for the week ending October 17 were 220,000 carloads, down 3.5 percent compared with the same week in 2025, while U.S. weekly intermodal volume was 280,100 containers and trailers, up 0.8 percent compared to 2025.</p>]]></content:encoded></item>
            <item><title>AAR Reports Weekly Rail Traffic for the Week Ending October 10, 2026</title></item></channel></rss>"""
        val b = WeitereQuellen.aarLesen(rss)!!
        assertEquals(tag("2026-10-17"), b.ende)
        assertEquals(500_100L, b.einheiten)
        assertEquals(-1.2, b.gesamt, 1e-9)
        assertEquals(-3.5, b.wagen!!, 1e-9)
        assertEquals(0.8, b.container!!, 1e-9)
        assertEquals(Ampel.GELB, WeitereQuellen.vorjahrAmpel(b.gesamt))
        assertNull(WeitereQuellen.aarLesen("<rss><channel></channel></rss>"))
    }

    // ------------------------------------------------------------ WSTS

    @Test
    fun wsts_lesen() {
        val html = """<a class="x" href="/esraCMS/extension/media/f/WST/1234/WSTS-Historical-Billings-Report-Sep_2026.xlsx">Download</a>"""
        assertEquals("https://www.wsts.org/esraCMS/extension/media/f/WST/1234/WSTS-Historical-Billings-Report-Sep_2026.xlsx", WeitereQuellen.wstsLink(html))
        val leer = List(12) { "" }
        val blatt = listOf(
            listOf("Billings in thousands of USD"),
            listOf("") + listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"),
            listOf("2025") + leer,
            listOf("Americas") + List(12) { "1000" },
            listOf("Worldwide", "50000000", "52000000", "54000000", "55000000", "56000000", "57000000", "58000000", "60000000", "61000000", "62000000", "63000000", "64000000"),
            listOf("2026") + leer,
            listOf("Worldwide", "60000000", "61000000", "62000000", "63000000", "64000000", "65000000", "66000000", "66000000", "", "", "", ""),
        )
        val h = WeitereQuellen.wstsAusBlaettern(mapOf("Monthly Data" to blatt, "3MMA" to emptyList()))!!
        assertEquals(2026, h.jahr)
        assertEquals(8, h.monat)
        assertEquals(66.0, h.milliarden, 1e-9)
        assertEquals(10.0, h.vorjahr, 1e-9)
        assertNull(WeitereQuellen.wstsAusBlaettern(mapOf("Monthly Data" to blatt.take(5))))
    }

    // ------------------------------------------------------------ Boursa Kuwait

    private val kwFeed = """<?xml version="1.0" encoding="utf-8"?><rss version="2.0"><channel><title>Boursa Kuwait</title>
        <item><title>ABCB - Corporate Insiders Disclosure Form after Dealing</title><type>139</type><description></description>
        <link>https://www.boursakuwait.com.kw/en/news/view#BK1</link><url>https://ifsahdocs.boursakuwait.com.kw/DiscAssets/2026_00001/Boursa_HTML_en.html</url>
        <pubDate>Wed, 28 Oct 2026 10:15:00 +0300</pubDate><security>101</security></item>
        <item><title>XYZ - Financial Statements</title><type>143</type><url>https://ifsahdocs.boursakuwait.com.kw/DiscAssets/2026_00002/Boursa_HTML_en.html</url>
        <pubDate>Wed, 28 Oct 2026 11:00:00 +0300</pubDate></item>
        <item><title>BAD - Elsewhere</title><type>139</type><url>https://example.org/x.html</url></item></channel></rss>"""

    @Test
    fun kuwait_feed_und_formular() {
        val liste = InsiderWelt.kwFeedLesen(kwFeed)
        assertEquals(2, liste.size)
        val m = liste.first()
        assertEquals("ABCB", m.kuerzel)
        assertEquals(InsiderWelt.KW_INSIDER, m.art)
        assertEquals("2026-10-28", m.tag)
        val html = """<html><head><style>.a{}</style></head><body><table>
            <tr><td>Name of disclosing person </td><td>*</td><td>:</td><td>Jane Example </td></tr>
            <tr><td>Name of company in which person works </td><td>*</td><td>:</td><td>Example Bank</td></tr>
            <tr><td>Position </td><td>*</td><td>:</td><td>Board Member</td></tr>
            <tr><td>Trading type </td><td>*</td><td>: </td><td>Purchase</td><td><select><option>Select </option><option>Purchase </option><option>Sale </option></select></td></tr>
            <tr><td>Name of the security </td><td>*</td><td>:</td><td>Example Bank (K.S.C.P)</td></tr>
            <tr><td>Transaction date </td><td>*</td><td>:</td><td>27-10-2026</td></tr>
            <tr><td>Number of shares </td><td>*</td><td>:</td><td>20,000</td></tr>
            <tr><td>Price </td><td>*</td><td>:</td><td>250</td></tr>
            <tr><td>Total shares balance after the current transaction </td><td>*</td><td>:</td><td>1500000</td></tr>
            </table></body></html>"""
        val g = InsiderWelt.kwFormularLesen(html, m)!!
        assertEquals("Jane Example", g.p)
        assertEquals("K", g.art)
        assertEquals("A", g.r)
        assertEquals("2026-10-27", g.d)
        assertEquals(20_000.0, g.n, 1e-9)
        assertEquals(0.25, g.preis, 1e-9)
        assertEquals(5_000.0, g.wert, 1e-9)
        assertEquals("KWD", g.w)
        assertEquals("ABCB", g.c)
        assertEquals("KW-2026_00001", g.id)
        assertNull(InsiderWelt.kwFormularLesen("<html>Request Rejected</html>", m))
    }

    @Test
    fun boersen_nur_im_browser() {
        assertEquals("IL", InsiderWelt.nurBrowser("TEVA.TA", ""))
        assertEquals("ID", InsiderWelt.nurBrowser("BBCA.JK", ""))
        assertEquals("BH", InsiderWelt.nurBrowser("XYZ", "BH0000000001"))
        assertNull(InsiderWelt.nurBrowser("AAPL", "US0378331005"))
        // Namibia ohne Link
        assertNull(InsiderWelt.nurBrowser("ABC", "NA0000000001"))
        assertEquals("KW", InsiderWelt.ort("ABCB.KW", "")?.first)
    }

    // ------------------------------------------------------------ CourtListener

    @Test
    fun courtlistener_lesen() {
        val json = """{"next":null,"results":[
            {"id":11,"year":2024,"person":"https://www.courtlistener.com/api/rest/v4/people/123/","investments":[
                {"description":"Example Corp. common stock","transaction_during_reporting_period":"Buy","transaction_date":"2024-03-05","transaction_value_code":"K","redacted":false},
                {"description":"Index Fund","transaction_during_reporting_period":"","transaction_value_code":null,"redacted":false},
                {"description":"Other Inc.","transaction_during_reporting_period":"Sold (part)","transaction_date":"1750-01-01","transaction_value_code":"P1","redacted":false},
                {"description":"Hidden","transaction_during_reporting_period":"Buy","redacted":true}]},
            {"id":12,"year":2023,"person":"https://www.courtlistener.com/api/rest/v4/people/456/","investments":[
                {"description":"Bond fund","transaction_during_reporting_period":"Gift","transaction_value_code":"J"}]}]}"""
        val l = WeitereQuellen.clErklaerungenLesen(json)
        assertEquals(3, l.size)
        assertEquals(WeitereQuellen.RichterGeschaeft(123, 2024, 11, "Example Corp. common stock", "K", "2024-03-05", "K"), l[0])
        assertEquals("V", l[1].art)
        assertEquals("", l[1].tag)
        assertEquals("S", l[2].art)
        assertEquals(456L, l[2].person)
        assertEquals("Jane Q. Example Jr.", WeitereQuellen.clNameLesen("""{"id":123,"name_first":"Jane","name_middle":"Q.","name_last":"Example","name_suffix":"Jr."}"""))
        assertEquals(15_001L to 50_000L, WeitereQuellen.clSpanne("K"))
        assertEquals(50_000_001L to null, WeitereQuellen.clSpanne("P4"))
        assertNull(WeitereQuellen.clSpanne("-1"))
        assertTrue(WeitereQuellen.clErklaerungenAdresse().startsWith("https://www.courtlistener.com/api/rest/v4/financial-disclosures/?order_by=-date_created&fields="))
        assertTrue(WeitereQuellen.clErklaerungenLesen("""{"detail":"Authentication credentials were not provided."}""").isEmpty())
    }

    // ------------------------------------------------------------ Bluesky

    @Test
    fun bluesky_zugang_nur_mit_app_passwort() {
        assertTrue(WeitereQuellen.appPasswortGueltig("abcd-efgh-2345-wxyz"))
        assertFalse(WeitereQuellen.appPasswortGueltig("MeinGeheimesPasswort1!"))
        assertFalse(WeitereQuellen.appPasswortGueltig("abcd-efgh-2345"))
        assertEquals("name.bsky.social", WeitereQuellen.handleBereinigt("@Name.bsky.social"))
        assertEquals("did:plc:abcdefghijklmnopqrstuvwx", WeitereQuellen.handleBereinigt("did:plc:abcdefghijklmnopqrstuvwx"))
        // E-Mail-Adressen werden nie als Handle angenommen
        assertNull(WeitereQuellen.handleBereinigt("jemand@example.org"))
        assertNull(WeitereQuellen.handleBereinigt("ohnepunkt"))
    }

    @Test
    fun bluesky_sitzung_und_suche() {
        val s = WeitereQuellen.sitzungLesen("""{"did":"did:plc:x","handle":"name.bsky.social","accessJwt":"a.b.c","refreshJwt":"r.s.t",
            "didDoc":{"service":[{"id":"#atproto_pds","type":"AtprotoPersonalDataServer","serviceEndpoint":"https://pilz.us-east.host.bsky.network"}]}}""")!!
        assertEquals("a.b.c", s.zugriff)
        assertEquals("https://pilz.us-east.host.bsky.network", s.pds)
        assertEquals("https://bsky.social", WeitereQuellen.sitzungLesen("""{"accessJwt":"a","refreshJwt":"b"}""")!!.pds)
        assertNull(WeitereQuellen.sitzungLesen("""{"error":"AuthenticationRequired"}"""))
        val url = WeitereQuellen.suchAdresse(s.pds, "Example", Instant.parse("2026-10-21T00:00:00Z"), Instant.parse("2026-10-28T00:00:00Z"), "c1")
        assertTrue(url.startsWith("https://pilz.us-east.host.bsky.network/xrpc/app.bsky.feed.searchPosts?q=%22Example%22&sort=latest&limit=100"))
        assertTrue(url.contains("since=2026-10-21T00:00:00Z") && url.endsWith("&cursor=c1"))
        val seite = WeitereQuellen.suchseiteLesen("""{"posts":[{"uri":"at://x/1"},{"uri":"at://x/2"}],"cursor":"2"}""")!!
        assertEquals(2, seite.beitraege)
        assertEquals("2", seite.cursor)
        assertNull(WeitereQuellen.suchseiteLesen("""{"posts":[],"cursor":"9"}""")!!.cursor)
        assertEquals(50.0, WeitereQuellen.BlueskyWoche(30, 20, false).veraenderung!!, 1e-9)
        assertNull(WeitereQuellen.BlueskyWoche(300, 20, true).veraenderung)
    }

    // ------------------------------------------------------------ Hosts und Kontingente

    @Test
    fun hosts_und_kontingente() {
        assertEquals("stimmung", Quellenbuendel.quelleZu("cdn.cboe.com"))
        assertEquals("stimmung", Quellenbuendel.quelleZu("api.alternative.me"))
        assertEquals("fruehsignale", Quellenbuendel.quelleZu("www.drewry.co.uk"))
        assertEquals("fruehsignale", Quellenbuendel.quelleZu("www.aar.org"))
        assertEquals("fruehsignale", Quellenbuendel.quelleZu("www.wsts.org"))
        assertEquals("welt", Quellenbuendel.quelleZu("rss.boursakuwait.com.kw"))
        assertEquals("usbehoerden", Quellenbuendel.quelleZu("www.courtlistener.com"))
        assertEquals("erwaehnungen", Quellenbuendel.quelleZu("pilz.us-east.host.bsky.network"))
        var jetzt = 1_000_000L
        val k = Abrufkontingent(null) { jetzt }
        repeat(4) { assertTrue(k.erlaubt(Abrufkontingent.COURTLISTENER)); k.buchen(Abrufkontingent.COURTLISTENER) }
        assertFalse(k.erlaubt(Abrufkontingent.COURTLISTENER))
        jetzt += 61_000L
        assertTrue(k.erlaubt(Abrufkontingent.COURTLISTENER))
        assertEquals(100L, k.stand(Abrufkontingent.COURTLISTENER).second)
    }
}
