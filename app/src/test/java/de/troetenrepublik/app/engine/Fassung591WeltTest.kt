package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.InsiderWelt
import de.troetenrepublik.app.market.RegierungOge
import de.troetenrepublik.app.market.RegisterImport
import de.troetenrepublik.app.market.RegisterImportWelt
import de.troetenrepublik.app.market.SecFormen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 5.91: Regierungsmitglieder ueber OGE und Weisses Haus (278-T), SEC Form 144 und Schedule 13D/13G,
// Sri Lanka (CSE), Oman (MCD) und selbst importierte Meldungen aus Aegypten, Nigeria, Kuwait und Katar.
// Die Beispiele sind gekuerzte echte Vorlagen; die SEC-Beispiele folgen dem dokumentierten XML-Format.
class Fassung591WeltTest {

    @Test fun ogeListe() {
        val text = """{"draw":1,"recordsTotal":16798,"recordsFiltered":329,"data":[
            {"type":"<a href='https:\/\/extapps2.oge.gov\/201\/Presiden.nsf\/PAS+Index\/026D1034BC6C312785258E9A002DB7CB\/${'$'}FILE\/Scott-A-Kupor-09.21.2026-278T.pdf'>278 Transaction<\/a>","name":"Kupor, Scott A","agency":"Office of Personnel Management","title":"Director","level":"Level II","docDate":"2026-10-24T04:19:21","amended":""},
            {"type":"<a href='https:\/\/extapps2.oge.gov\/201\/Presiden.nsf\/PAS+Index\/0BDCCCE7DE13AEFA85258E8A002DE2B5\/${'$'}FILE\/Donald-J-Trump.09.17.2026-278T.pdf'>278 Transaction<\/a>","name":"Trump, Donald J","agency":"White House Office","title":"President","level":"n\/a","docDate":"2026-10-01T04:00:00","amended":""},
            {"type":"<a href='https:\/\/extapps2.oge.gov\/201\/Presiden.nsf\/PAS+Index\/11111111AAAA\/${'$'}FILE\/Alt-278T.pdf'>278 Transaction<\/a>","name":"Abbott, James","agency":"FLRA","title":"Member","level":"n\/a","docDate":"2020-12-15T03:15:54","amended":""},
            {"type":"<a href='https:\/\/extapps2.oge.gov\/201\/Presiden.nsf\/PAS+Index\/0933B220C51FFFB485258E52002DD2D9\/${'$'}FILE\/Kevin-Warsh-06.25.2026-278T.pdf'>278 Transaction<\/a>","name":"Warsh, Kevin","agency":"Federal Reserve System Board of Governors","title":"Governor & Chairman","level":"Level I","docDate":"2026-08-13T04:20:30","amended":""}
        ]}"""
        val (gesamt, liste) = RegierungOge.ogeListeLesen(text)
        assertEquals(329, gesamt)
        assertEquals(listOf("Kupor, Scott A", "Warsh, Kevin"), liste.map { it.name })
        assertEquals("2026-10-24", liste[0].tag)
        assertTrue(liste[0].pdf.endsWith("Scott-A-Kupor-09.21.2026-278T.pdf"))
        assertEquals("kupor-scott", RegierungOge.kennung("Kupor, Scott A"))
        assertEquals("Scott Kupor", RegierungOge.anzeige("Kupor, Scott A"))
        assertEquals("Kevin Warsh", RegierungOge.anzeige("Warsh, Kevin"))

        val wh = """<a href="https://www.whitehouse.gov/wp-content/uploads/2025/11/Borg-Jared-Periodic-Transaction-Report-05.08.25.pdf">x</a>
            <a href="https://www.whitehouse.gov/wp-content/uploads/2025/11/President-Donald-J.-Trump-Periodic-Transaction-Report-11.14.25.pdf">y</a>
            <a href="https://www.whitehouse.gov/wp-content/uploads/2025/11/Lyon-Andrew-Periodic-Transaction-Report-–-06.08.25.pdf">z</a>
            <a href="https://www.whitehouse.gov/wp-content/uploads/2025/07/Howard-W.-Lutnick-Limited-Waiver.pdf">w</a>"""
        val w = RegierungOge.weissesHausLesen(wh)
        assertEquals(listOf("Borg, Jared", "Lyon, Andrew"), w.map { it.name })
        assertEquals("2025-05-08", w[0].tag)
        assertEquals("2025-06-08", w[1].tag)

        // Einarbeiten: eine Person je Kennung, Meldungen einmal, gelesene bleiben
        var d = RegierungOge.einarbeiten(RegierungOge.Datei(), liste + w, 1L)
        assertEquals(4, d.personen.size)
        d = RegierungOge.eintragen(d, "kupor-scott", liste[0].pdf, RegierungOge.Bericht("Kupor, Scott Aaron", "Director", "2026-09-21", emptyList()))
        d = RegierungOge.einarbeiten(d, liste, 2L)
        val kupor = d.personen.first { it.id == "kupor-scott" }
        assertEquals(1, kupor.meldungen.size)
        assertTrue(kupor.meldungen[0].gelesen)
        assertEquals("Office of Personnel Management", kupor.behoerde)
    }

    // Kopf der Integrity.gov-Ausgabe, wie PdfText ihn liest.
    private val kopf = listOf(
        "Kupor, Scott Aaron - Page 1", "Periodic Transaction Report | U.S. Office of Government Ethics; 5 C.F.R. part 2634 (Updated February 2024)",
        "Periodic Transaction Report (OGE Form 278-T)", "Filer's Information", "Kupor, Scott Aaron", "Director, Office of Personnel Management",
        "Electronic Signature - I certify that the statements I have made in this form are true, complete and correct to the best of my knowledge.",
        "/s/ Kupor, Scott Aaron [electronically signed on 09/21/2026 by Kupor, Scott Aaron in Integrity.gov]", "Transactions",
    )

    @Test fun bericht278t() {
        // Zellen einzeln (OGE)
        val kupor = kopf + listOf(
            "Kupor, Scott Aaron - Page 2", "Endnotes", "#", "DESCRIPTION", "TYPE", "DATE", "NOTIFICATION", "RECEIVED OVER", "30 DAYS AGO", "AMOUNT",
            "1", "Airbnb Inc. Class A Common Stock (ABNB)", "See Endnote", "Sale", "09/21/2026", "No", "$250,001 -", "$500,000",
            "2", "Okta, Inc. (OKTA)", "See Endnote", "Sale", "09/21/2026", "No", "$1,000,001 -", "$5,000,000",
            "PART", "#", "ENDNOTE", "Transactions", "1", "Exercise of covered call option",
        )
        val b = RegierungOge.berichtLesen(kupor)!!
        assertEquals("Kupor, Scott Aaron", b.name)
        assertEquals("Director, Office of Personnel Management", b.amt)
        assertEquals("2026-09-21", b.unterschrieben)
        assertEquals(2, b.geschaefte.size)
        val g = b.geschaefte[0]
        assertEquals("ABNB", g.k)
        assertEquals("V", g.a)
        assertEquals("2026-09-21", g.d)
        assertEquals(250_001L, g.von)
        assertEquals(500_000L, g.bis)
        assertEquals("ST", g.t)
        assertEquals("OKTA", b.geschaefte[1].k)
        assertEquals(5_000_000L, b.geschaefte[1].bis)

        // Nummer und Beschreibung in einer Zeile, Tag und Hinweis zusammen (Weisses Haus), Kuerzel vorn (Fonds)
        val borg = kopf + listOf(
            "# DESCRIPTION", "TYPE", "DATE", "NOTIFICATION", "RECEIVED OVER", "30 DAYS AGO", "AMOUNT",
            "1 NVIDIA Corp. (NVDA)", "Sale", "04/10/2025 No", "$1,001 - $15,000",
            "2 Palantir Technologies Inc. Class A Common Stock", "(PLTR)", "Purchase", "04/11/2025 No", "$15,001 - $50,000",
            "3 SPY (S&P 500 SPDR ETF)", "Sale (partial)", "04/12/2025", "No", "Over $50,000,000",
            "4 Exchange Income Corp (EIF)", "Exchange", "04/13/2025", "Yes", "$1,001 - $15,000",
            "5 THSDFS LLC - Series 13", "Sale", "05/27/2026", "No", "$50,001 -", "$100,000",
            "Borg, Jared - Page 3",
            "6 Verirrt (XYZ)", "Sale", "05/27/2026", "No", "$1,001 - $15,000",
        )
        val l = RegierungOge.berichtLesen(borg)!!.geschaefte
        assertEquals(5, l.size)
        assertEquals("NVDA", l[0].k)
        assertEquals("2025-04-10", l[0].d)
        assertEquals("PLTR", l[1].k)
        assertEquals("K", l[1].a)
        assertEquals("SPY", l[2].k)
        assertEquals("T", l[2].a)
        assertEquals("EF", l[2].t)
        assertEquals(50_000_001L, l[2].von)
        assertNull(l[2].bis)
        assertEquals("Exchange Income Corp (EIF)", l[3].n)
        assertEquals("X", l[3].a)
        assertNull(l[4].k)
        assertEquals("OT", l[4].t)

        assertNull(RegierungOge.berichtLesen(listOf("Irgendein Text")))
        assertEquals(1_001L to 15_000L, RegierungOge.spanne("$1,001 - $15,000"))
        assertEquals("BRK.B", RegierungOge.kuerzel("Berkshire Hathaway Inc. Class B (BRK.B)"))
        assertNull(RegierungOge.kuerzel("Arthur Ventures IV, LP (Roth IRA)"))

        // Darstellung wie beim Kongress: ungelesene Meldungen ohne Geschaefte
        val p = RegierungOge.Person("kupor-scott", "Scott Kupor", meldungen = listOf(
            RegierungOge.Meldung("a.pdf", "2026-10-24", gelesen = true, geschaefte = l),
            RegierungOge.Meldung("b.pdf", "2026-09-01"),
        ))
        val daten = RegierungOge.daten(p)
        assertEquals(2, daten.size)
        assertEquals(5, daten[0].second!!.size)
        assertNull(daten[1].second)
        assertEquals(5, RegierungOge.geschaefte(p).size)
    }

    @Test fun form144UndSchedule13() {
        val liste = """{"cik":"320193","filings":{"recent":{
            "accessionNumber":["0001971213-26-000101","0000320193-26-000090","0000102909-26-000011","0001364742-26-000005","0000102909-24-000001"],
            "filingDate":["2026-10-20","2026-10-18","2026-02-10","2026-03-01","2024-02-13"],
            "form":["144","4","SCHEDULE 13G/A","SCHEDULE 13D","SC 13G/A"],
            "primaryDocument":["xslF144X01/primary_doc.xml","xslF345X05/wk-form4.xml","xslSCHEDULE_13G_X01/primary_doc.xml","xslSCHEDULE_13D_X01/primary_doc.xml","us0378331005_021324.txt"]}}}"""
        val e = SecFormen.eintraege(liste)
        assertEquals(listOf("144", "SCHEDULE 13G/A", "SCHEDULE 13D"), e.map { it.form })
        assertEquals("primary_doc.xml", e[0].datei)
        assertEquals("https://data.sec.gov/submissions/CIK0000320193.json", SecFormen.submissionsAdresse("320193"))
        assertEquals("320193", SecFormen.cikAusKuerzeln("""{"0":{"cik_str":320193,"ticker":"AAPL","title":"Apple Inc."},"1":{"cik_str":1067983,"ticker":"BRK-B","title":"BERKSHIRE"}}""", "AAPL"))
        assertEquals("1067983", SecFormen.cikAusKuerzeln("""{"1":{"cik_str":1067983,"ticker":"BRK-B","title":"BERKSHIRE"}}""", "BRK-B"))

        val f144 = """<?xml version="1.0" encoding="UTF-8"?>
<edgarSubmission xmlns="http://www.sec.gov/edgar/ffour" xmlns:com="http://www.sec.gov/edgar/common">
  <headerData><submissionType>144</submissionType></headerData>
  <formData>
    <issuerInfo>
      <issuerCik>0000320193</issuerCik>
      <issuerName>Beispiel Inc.</issuerName>
      <nameOfPersonForWhoseAccountTheSecuritiesAreToBeSold>Jane Q. Example</nameOfPersonForWhoseAccountTheSecuritiesAreToBeSold>
      <relationshipsToIssuer><relationshipToIssuer>Officer</relationshipToIssuer></relationshipsToIssuer>
    </issuerInfo>
    <securitiesInformation>
      <securitiesClassTitle>Common Stock</securitiesClassTitle>
      <noOfUnitsSold>25000</noOfUnitsSold>
      <aggregateMarketValue>6125000.50</aggregateMarketValue>
      <noOfUnitsOutstanding>14800000000</noOfUnitsOutstanding>
      <approxSaleDate>10/21/2026</approxSaleDate>
      <securitiesExchangeName>NASDAQ</securitiesExchangeName>
    </securitiesInformation>
    <noticeSignature><noticeDate>10/20/2026</noticeDate></noticeSignature>
  </formData>
</edgarSubmission>"""
        val v = SecFormen.form144(f144, "0001971213-26-000101", "2026-10-20")
        assertEquals(1, v.size)
        assertEquals("Jane Q. Example", v[0].p)
        assertEquals("Officer", v[0].rolle)
        assertEquals(25000.0, v[0].n, 1e-9)
        assertEquals(6125000.5, v[0].wert, 1e-6)
        assertEquals("2026-10-21", v[0].tag)
        assertEquals("2026-10-20", v[0].gemeldet)

        val g13 = """<edgarSubmission xmlns="http://www.sec.gov/edgar/schedule13g">
  <headerData><submissionType>SCHEDULE 13G/A</submissionType></headerData>
  <formData>
    <coverPageHeader><securitiesClassTitle>Common Stock</securitiesClassTitle><eventDateRequiresFilingThisStatement>12/31/2025</eventDateRequiresFilingThisStatement></coverPageHeader>
    <coverPageHeaderReportingPersonDetails>
      <reportingPersonName>Vanguard Group Inc</reportingPersonName>
      <reportingPersonBeneficiallyOwnedAggregateNumberOfShares>1350000000</reportingPersonBeneficiallyOwnedAggregateNumberOfShares>
      <classPercent>9.1</classPercent>
      <typeOfReportingPerson>IA</typeOfReportingPerson>
    </coverPageHeaderReportingPersonDetails>
  </formData>
</edgarSubmission>"""
        val h = SecFormen.schedule13(g13, "SCHEDULE 13G/A", "0000102909-26-000011", "2026-02-10")
        assertEquals(1, h.size)
        assertEquals("Vanguard Group Inc", h[0].name)
        assertEquals(9.1, h[0].anteil, 1e-9)
        assertEquals(1.35e9, h[0].n, 1.0)
        assertTrue(!h[0].aktiv)
        assertTrue(h[0].aenderung)
        assertEquals("2025-12-31", h[0].tag)

        val d13 = """<edgarSubmission xmlns="http://www.sec.gov/edgar/schedule13D">
  <headerData><submissionType>SCHEDULE 13D</submissionType></headerData>
  <formData>
    <coverPageHeader><securitiesClassTitle>Common Stock</securitiesClassTitle><dateOfEvent>02/20/2026</dateOfEvent></coverPageHeader>
    <reportingPersons>
      <reportingPersonInfo><reportingPersonName>Activist Fund LP</reportingPersonName><aggregateAmountOwned>5,200,000</aggregateAmountOwned><percentOfClass>6.3</percentOfClass></reportingPersonInfo>
      <reportingPersonInfo><reportingPersonName>Activist GP LLC</reportingPersonName><aggregateAmountOwned>5,200,000</aggregateAmountOwned><percentOfClass>6.3</percentOfClass></reportingPersonInfo>
    </reportingPersons>
    <signatureInformation><reportingPersonName>Activist Fund LP</reportingPersonName></signatureInformation>
  </formData>
</edgarSubmission>"""
        val a = SecFormen.schedule13(d13, "SCHEDULE 13D", "0001364742-26-000005", "2026-03-01")
        assertEquals(2, a.size)
        assertTrue(a.all { it.aktiv })
        assertEquals(5_200_000.0, a[0].n, 1e-9)
        assertEquals("2026-02-20", a[0].tag)

        // Je Halter die juengste Meldung, groesste zuerst; angekuendigte Verkaeufe nach Tag
        val s = SecFormen.Stand(verkaeufe = v, halter = h + a + h[0].copy(a = "alt", anteil = 7.0, gemeldet = "2025-02-10"))
        val gross = SecFormen.grossaktionaere(s)
        assertEquals(3, gross.size)
        assertEquals(9.1, gross[0].anteil, 1e-9)
        assertEquals(1, SecFormen.angekuendigt(s, "2026-08-01").size)
        assertEquals(0, SecFormen.angekuendigt(s, "2026-11-01").size)
    }

    @Test fun sriLankaUndOman() {
        assertEquals("LK" to "JKH.N0000", InsiderWelt.ort("JKH-N0000.CM", ""))
        assertEquals("EG" to "COMI", InsiderWelt.ort("COMI.CA", ""))
        assertEquals("KW" to "NBK", InsiderWelt.ort("NBK.KW", ""))
        assertEquals("QA" to "QNBK", InsiderWelt.ort("QNBK.QA", ""))
        assertEquals("OM" to "OM0000001152", InsiderWelt.ort("XYZ", "OM0000001152"))
        assertEquals("AU" to "BHP", InsiderWelt.ort("BHP.AX", ""))
        assertNull(InsiderWelt.ort("AAPL", "US0378331005"))

        val verlauf = """{"reqCompanyAnnouncement":[
            {"id":33284,"dateOfAnnouncement":"26 Oct 2026","announcementId":39758,"announcementCategory":"DEALINGS BY DIRECTORS","company":"THE COLOMBO FORT LAND & BUILDING PLC"},
            {"id":33283,"dateOfAnnouncement":"25 Oct 2026","announcementId":39761,"announcementCategory":"CORPORATE DISCLOSURE","company":"THE COLOMBO FORT LAND & BUILDING PLC"}]}"""
        assertEquals(listOf("39758" to "2026-10-26"), InsiderWelt.cseVerlaufLesen(verlauf))

        val meldung = """{"reqBaseAnnouncement":{"id":39758,"dType":"DealingsByDirectors","dateOfAnnouncement":"26 Oct 2026","symbol":"CFLB",
            "companyName":"THE COLOMBO FORT LAND & BUILDING PLC","accTypeRelInterest":true,"accTypeOwnAcc":false,
            "dircetorsName":"Mr. Anushman Rajaratnam, Mr. S.D.R. Arudpragasam",
            "relInterestAccountName":"Oakley Investments (Pvt) Ltd - Mr. S.D.R Arudpragasam - Director/Shareholder",
            "natureOfDir":"Mr. Anushman Rajaratnam - Executive Director; Mr S.D.R. Arudpragasam - Non-Executive Director",
            "directorTransactions":[{"dirId":22809,"transType":"Acquisition","transactionDate":"20 Oct 2026","quantity":1082.0,"price":48.3},
            {"dirId":22810,"transType":"Disposal","transactionDate":"21 Oct 2026","quantity":580.0,"price":48.5}]},
            "reqAnnouncementDocs":[{"fileUrl":"cmt/announcement_portal_prod/cflb304-26.10.2026_1.pdf","baseUrl":"https://cdn.cse.lk/"}]}"""
        val g = InsiderWelt.cseMeldungLesen(meldung, "CFLB.N0000")
        assertEquals(2, g.size)
        assertEquals("K", g[0].art)
        assertEquals("V", g[1].art)
        assertEquals("2026-10-20", g[0].d)
        assertEquals("2026-10-26", g[0].v)
        assertEquals(1082 * 48.3, g[0].wert, 1e-6)
        assertEquals("Oakley Investments (Pvt) Ltd", g[0].p)
        assertEquals("N", g[0].r)
        assertEquals("LKR", g[0].w)
        assertEquals("CFLB.N0000", g[0].c)
        assertEquals("https://cdn.cse.lk/cmt/announcement_portal_prod/cflb304-26.10.2026_1.pdf", g[0].q)
        assertTrue(InsiderWelt.passt(g[0], "LK", "CFLB.N0000", "", ""))

        val mcd = """<script>${'$'}("#gridContainer").dxDataGrid({"columns":[{"dataField":"ISIN"}],"dataSource":{"store":new DevExpress.data.ArrayStore({"data":[
            {"ISIN":"OM0000001152","InstrumentName":"ENGINEERING AND INVEST","InvestorName":"محمد","SharesSellOrBought":10000,"Side":"Buy","TotalSharesOwned":10090000,"OwnershipPerc":6.727,"TradeDate":null},
            {"ISIN":"OM0000009569","InstrumentName":"JABAL REAL ESTATE FUND","InvestorName":"JABAL ASSET MANAGEMENT LLC","SharesSellOrBought":50000,"Side":"Sell","TotalSharesOwned":130565384,"OwnershipPerc":75.038,"TradeDate":null}]})}})</script>"""
        val o = InsiderWelt.mcdLesen(mcd, "OM0000009569", "2026-10-26")
        assertEquals(1, o.size)
        assertEquals("V", o[0].art)
        assertEquals(50000.0, o[0].n, 1e-9)
        assertEquals("75.038", o[0].t)
        assertEquals("2026-10-26", o[0].d)
        assertEquals("محمد", InsiderWelt.mcdLesen(mcd, "OM0000001152", "2026-10-26")[0].p)
        // Derselbe Eintrag am naechsten Tag hat dieselbe Kennung
        assertEquals(o[0].id, InsiderWelt.mcdLesen(mcd, "OM0000009569", "2026-10-27")[0].id)
    }

    @Test fun importeWelt() {
        // Nigeria: Formular wie bei der MAR, Text wie PdfText ihn liefert (zerfallene Zahlen)
        val ngx = "Lagos, 1 st October 2026 Notification of Share Dealing by Insiders Bello A. Abdullahi Company Secretary 1. Details of the Director/Insider " +
            "a) Name Saheed Lasisi 2. Reason for the notification a) Position/status Executive Director b) Initial notification/Amendment Initial notification " +
            "3. Details of the issuer a) Name Nigerian Aviation Handling Company Plc b) Legal Entity Identifier 1 4. Details of the transaction(s): to be repeated " +
            "a) Description of the financial instrument, type of instrument Identification Code Ordinary Shares ISIN: NGNAHCO00008 b) Nature of the transaction Share Sale " +
            "c) Price(s) and volume(s) 1 9 3 , 835 shares at N 13 9 . 00 per share d) Aggregate information - Aggregated volume - Average Price 193,835 shares " +
            "N 139 . 00 per share e) Date of Transaction 23 / 0 9 /202 6 f) Place of Transaction Lagos"
        val n = RegisterImportWelt.ngx(ngx, "x")!!
        assertEquals("Saheed Lasisi", n.p)
        assertEquals("Nigerian Aviation Handling Company Plc", n.e)
        assertEquals("NGNAHCO00008", n.isin)
        assertEquals("V", n.art)
        assertEquals(193_835.0, n.n, 1e-9)
        assertEquals(139.0, n.preis, 1e-9)
        assertEquals("2026-09-23", n.d)
        assertEquals("NGN", n.w)
        val ngx2 = "RC. 2321 (Lagos, Nigeria 16 October 2026 ) NOTIFICATION OF SHARE DEALING BY INSIDERS 1. Details of the Director/Insider a) Name MR. SEHINDE ADENAGBE " +
            "2. Reason for the notification a) Position/status A Non - Executive Director of Nigerian Exchange Group Plc b) Initial notification/Amendment Initial Notification " +
            "3. Details of the issuer a) Name NIGERIAN EXCHANGE GROUP PLC b) Legal Entity Identifier 0292004857D6LH3G5764 ISIN: NGNGXGROUP09 " +
            "b) Nature of the transaction Purcha s e of Nigerian Exchange Group Shares c) Price(s) and volume(s) 40 ,000 units of shares @ N 16 8 . 9 0 per share " +
            "d) Aggregate information - Aggregated volume - Price 40 ,000 units of shares N 168 . 9 0 e) Date of Transaction 2 October , 2026 f) Place of Transaction Lagos, Nigeria"
        val n2 = RegisterImportWelt.ngx(ngx2, "y")!!
        assertEquals("K", n2.art)
        assertEquals(40_000.0, n2.n, 1e-9)
        assertEquals(168.9, n2.preis, 1e-9)
        assertEquals("2026-10-02", n2.d)
        assertEquals("A", n2.r)

        // Katar: gespeicherte Seite mit gefuellter Tabelle, ohne Original-Link
        val qse = """<html><body><table id="insiderTradesMatchingCompaniesTable"><tr><th id="Date">22 Oct 2026</th></tr></table>
            <table border="1" id="TickerTD" width="100%"><tr><td class="text-center" style="width: 25%;">Qatar National Bank</td><td class="text-center">QNBK</td>
            <td class="text-center">Max Muster</td><td class="text-center">12,000</td><td class="text-center">0</td></tr>
            <tr><td>Industries Qatar</td><td>IQCD</td><td>Erika Beispiel</td><td>0</td><td>5,500</td></tr></table></body></html>"""
        val e = RegisterImport.erkennen(qse.toByteArray(), java.time.LocalDate.of(2026, 10, 26))
        assertTrue(e is RegisterImport.Ergebnis.Insider)
        val q = (e as RegisterImport.Ergebnis.Insider).geschaefte
        assertEquals("QA", e.land)
        assertEquals(2, q.size)
        assertEquals("QNBK", q[0].c)
        assertEquals("K", q[0].art)
        assertEquals(12000.0, q[0].n, 1e-9)
        assertEquals("V", q[1].art)
        assertEquals("2026-10-22", q[0].d)
        assertEquals("", q[0].q)
        assertTrue(InsiderWelt.passt(q[0], "QA", "QNBK", "", ""))
        // Am Handy als MHTML gespeichert (quoted-printable)
        val mht = "From: <Saved by Blink>\nContent-Type: text/html\nContent-Transfer-Encoding: quoted-printable\n\n" +
            qse.replace("=", "=3D").replace("Industries Qatar", "Industries =\nQatar")
        val e2 = RegisterImport.erkennen(mht.toByteArray(), java.time.LocalDate.of(2026, 10, 26)) as RegisterImport.Ergebnis.Insider
        assertEquals(2, e2.geschaefte.size)
        assertEquals("Industries Qatar", e2.geschaefte[1].e)

        // Kuwait: einzelnes Formular als Webseite
        val kw = """<html><head><title>Boursa Kuwait</title></head><body><h2>Insider Disclosures</h2><table>
            <tr><td>Company Name</td><td>National Bank of Kuwait</td></tr><tr><td>Symbol</td><td>NBK</td></tr>
            <tr><td>Insider Name</td><td>Ahmad Example</td></tr><tr><td>Position</td><td>Board Member</td></tr>
            <tr><td>Transaction Type</td><td>Buy</td></tr><tr><td>Transaction Date</td><td>20/10/2026</td></tr>
            <tr><td>Number of Shares</td><td>100,000</td></tr><tr><td>Price</td><td>985</td></tr></table></body></html>"""
        val k = (RegisterImport.erkennen(kw.toByteArray(), java.time.LocalDate.of(2026, 10, 26)) as RegisterImport.Ergebnis.Insider)
        assertEquals("KW", k.land)
        val kg = k.geschaefte.single()
        assertEquals("Ahmad Example", kg.p)
        assertEquals("NBK", kg.c)
        assertEquals("K", kg.art)
        assertEquals("A", kg.r)
        assertEquals(0.985, kg.preis, 1e-9)
        assertEquals(98_500.0, kg.wert, 1e-6)
        assertEquals("2026-10-20", kg.d)

        // Aegypten: Tagesbericht, je Geschaeft Firma, Person, Beziehung, Art, Menge, Preis
        val egx = listOf(
            "Trading of Insiders, Major Shareholders & Their Related Parties", "Date: 22/10/2026",
            "Commercial International Bank (Egypt)", "Ahmed Example", "Board Member", "Buy", "15,000", "82.50",
            "Fawry For Banking Technology", "Example Holding Ltd", "Major Shareholder", "Sell", "1,200,000", "11.30",
        )
        val eg = RegisterImportWelt.egx(egx, "2026-10-26")
        assertEquals(2, eg.size)
        assertEquals("Commercial International Bank (Egypt)", eg[0].e)
        assertEquals("Ahmed Example", eg[0].p)
        assertEquals("K", eg[0].art)
        assertEquals(15000 * 82.5, eg[0].wert, 1e-6)
        assertEquals("2026-10-22", eg[0].d)
        assertEquals("V", eg[1].art)
        assertEquals("N", eg[1].r)

        // Unbekanntes erkennt dieser Teil nicht
        assertNull(RegisterImportWelt.erkennen("<html><body>nichts</body></html>".toByteArray(), java.time.LocalDate.of(2026, 10, 26)))
        // NGX gehoert nicht zu den Quellen
        assertEquals(12, RegisterImportWelt.QUELLEN.size)
    }
}
