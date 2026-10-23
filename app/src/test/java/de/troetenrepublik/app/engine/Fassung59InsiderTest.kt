package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.EuInsider
import de.troetenrepublik.app.market.InsiderQuellen
import de.troetenrepublik.app.market.InsiderWelt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 5.9: Insidergeschaefte aus Frankreich, den Niederlanden, Grossbritannien, der Schweiz, Indien, Hongkong
// und Australien (Leser mit gekuerzten echten Vorlagen).
class Fassung59InsiderTest {
    @Test fun hilfen() {
        assertEquals("2026-10-21", InsiderQuellen.tag("21 octobre 2026"))
        assertEquals("2026-10-20", InsiderQuellen.tag("20 oct 2026"))
        assertEquals("2026-10-22", InsiderQuellen.tag("22/10/2026"))
        assertEquals("2026-08-03", InsiderQuellen.tag("August 3, 2026"))
        assertEquals("", InsiderQuellen.tag("kein Datum"))
        assertEquals(1624.0, InsiderQuellen.zahl("1.624,00")!!, 1e-9)
        assertEquals(13771.0, InsiderQuellen.zahl("13 771.0000")!!, 1e-9)
        assertEquals(60000.0, InsiderQuellen.zahl("60,000")!!, 1e-9)
        assertEquals(8.6438, InsiderQuellen.zahl("8.6438")!!, 1e-9)
        assertEquals("S", InsiderQuellen.rolle("Président Directeur Général"))
        assertEquals("A", InsiderQuellen.rolle("Non-Executive Director"))
        assertEquals("A", InsiderQuellen.rolle("Membre du conseil d'administration"))
        assertEquals("N", InsiderQuellen.rolle("Spouse and PCA of the CFO"))
        assertEquals("F", InsiderQuellen.rolle("General Counsel"))
        assertTrue(InsiderQuellen.istAktie("Ordinary shares of 0.5p each"))
        assertTrue(!InsiderQuellen.istAktie("Restricted share right"))
    }

    @Test fun frankreich() {
        val z = listOf(
            "2026DD1145750", "FR0000063935 - DD194416", "NOM /FONCTION DE LA PERSONNE EXERCANT DES RESPONSABILITES DIRIGEANTES OU DE LA",
            "PERSONNE ETROITEMENT LIEE :", "SOPARFI SAS personne morale liée à Jacques DUPONT, Président", "COORDONNEES DE LEMETTEUR",
            "NOM : BEISPIEL SA", "DETAIL DE LA TRANSACTION", "DATE DE LA TRANSACTION : 21 octobre 2026", "LIEU DE LA TRANSACTION : Euronext Paris",
            "NATURE DE LA TRANSACTION : Acquisition", "DESCRIPTION DE LINSTRUMENT FINANCIER : Action",
            "CODE DIDENTIFICATION DE LINSTRUMENT FINANCIER : FR0000063935", "INFORMATION DETAILLEE PAR OPERATION", "PRIX UNITAIRE : 8.6438 Euro",
            "VOLUME : 13 771.0000", "INFORMATIONS AGREGEES", "PRIX : 8.6438 Euro", "VOLUME : 13 771.0000",
            "DACTIONS GRATUITES OU DE PERFORMANCES : NON", "DATE DE RECEPTION DE LA NOTIFICATION : 22 octobre 2026",
            "Individual notification relating to transactions", "DATE OF THE TRANSACTION : 21 October 2026",
        )
        val g = InsiderQuellen.amfLesen(z, InsiderQuellen.AmfMeldung("2026DD1145750", "2026-10-22", "x", "BEISPIEL")).single()
        assertEquals("BEISPIEL SA", g.e)
        assertEquals("SOPARFI SAS personne morale liée à Jacques DUPONT", g.p)
        assertEquals("Jacques DUPONT", g.fuer)
        assertEquals("N", g.r)
        assertEquals("K", g.art)
        assertEquals("Aktie", g.i)
        assertEquals("2026-10-21", g.d)
        assertEquals(13771.0 * 8.6438, g.wert, 1e-6)
        assertEquals("EUR", g.w)
    }

    @Test fun niederlande() {
        val html = """<span class="cc-em--detail-list__label">Notifiable</span>
            <span class="cc-em--detail-list__value"><span>Leysen T.</span></span>
            <span class="cc-em--detail-list__label">Issuing institution</span><span class="cc-em--detail-list__value"><span>Beispiel N.V.</span></span>
            <span class="cc-em--detail-list__label">Position/Status</span><span class="cc-em--detail-list__value"><span>Chief Executive Officer</span></span>
            <span class="cc-em--detail-list__label">Transaction</span><span class="cc-em--detail-list__value"><span>20 oct 2026</span></span>
            <h2 class="cc-em--table__title">Transactions</h2><table><tbody><tr>
            <td><span class="cc-mobile-title">Instrument type</span>Gewoon aandeel</td><td><span class="cc-mobile-title">ISIN</span>NL0000000001</td>
            <td><span class="cc-mobile-title">Transaction category</span>Verwerving</td><td><span class="cc-mobile-title">Transaction type</span>Verwerving</td>
            <td><span class="cc-mobile-title">Stock option program</span>Nee</td><td><span class="cc-mobile-title">Trading place</span>XAMS</td>
            <td><span class="cc-mobile-title">Price</span>108,60</td><td><span class="cc-mobile-title">Quantity</span>3.256,00</td><td><span class="cc-mobile-title">Unit</span>EUR</td>
            </tr></tbody></table><h2 class="cc-em--table__title">Aggregated information</h2>"""
        val g = InsiderQuellen.afmDetailLesen(html, "20261020ABCD").single()
        assertEquals("Leysen T.", g.p)
        assertEquals("S", g.r)
        assertEquals("K", g.art)
        assertEquals(3256.0, g.n, 1e-9)
        assertEquals(108.6, g.preis, 1e-9)
        assertEquals("2026-10-20", g.v)
        val nahe = InsiderQuellen.afmDetailLesen(html.replace("<span class=\"cc-em--detail-list__label\">Position/Status</span>",
            "<span class=\"cc-em--detail-list__label\">Closely affiliated</span><span class=\"cc-em--detail-list__value\"><span>Sawiris N.</span></span><span class=\"cc-em--detail-list__label\">Position/Status</span>"), "20261020ABCD").single()
        assertEquals("Sawiris N.", nahe.fuer)
        assertEquals("N", nahe.r)
        val liste = """<a href="/en/x/details?id=20261021221A5371-2BE7" class="cc-table-link-cta" style="color: #000; "> 21 oct 2026</a>"""
        assertEquals(listOf("20261021221A5371-2BE7" to "2026-10-21"), InsiderQuellen.afmListeLesen(liste))
    }

    @Test fun grossbritannien() {
        val html = """<table><tr><td>1.</td><td>Details of PDMR / person closely associated with them ('PCA')</td></tr>
            <tr><td>a)</td><td>Name</td><td>Paul Rawson</td></tr><tr><td>2.</td><td>Reason for notification</td></tr>
            <tr><td>a)</td><td>Position / status</td><td>Non-Executive Director</td></tr>
            <tr><td>3.</td><td>Details of the issuer</td></tr><tr><td>a)</td><td>Name</td><td>Beispiel PLC</td></tr>
            <tr><td>b)</td><td>LEI</td><td>213800ACO9GDDBM7DS35</td></tr>
            <tr><td>c)</td><td>Description of the financial instrument</td><td>Ordinary shares of 0.5p each<br>ISIN: GB00BYQDPD80</td></tr>
            <tr><td>d)</td><td>Nature of the transaction</td><td>Sale of Ordinary Shares</td></tr>
            <tr><td>e)</td><td>Price(s) and volume(s)</td><td>Price(s)</td><td>Volume(s)</td></tr><tr><td>1,700p</td><td>60,000</td></tr>
            <tr><td>f)</td><td>Aggregated information</td><td>N/A</td></tr>
            <tr><td>g)</td><td>Date of the transaction</td><td>22 October 2026</td></tr><tr><td>h)</td><td>Place of the transaction</td><td>LSE</td></tr></table>"""
        val g = InsiderQuellen.nsmLesen(html, InsiderQuellen.NsmMeldung("id1", "BEISPIEL PLC", "2026-10-23", "x")).single()
        assertEquals("GB00BYQDPD80", g.isin)
        assertEquals("Paul Rawson", g.p)
        assertEquals("Beispiel PLC", g.e)
        assertEquals("A", g.r)
        assertEquals("V", g.art)
        assertEquals("Aktie", g.i)
        assertEquals(17.0, g.preis, 1e-9)
        assertEquals(60000.0, g.n, 1e-9)
        assertEquals("2026-10-22", g.d)
        // Nummerierte Zeilen und Plaene: Nummern sind keine Preise, Kauf im Mitarbeiterplan ist Sonstiges
        val plan = """<table><tr><td>1</td><td>Details of the person discharging managerial responsibilities</td></tr>
            <tr><td>a)</td><td>Name</td><td>Giles Wilson</td></tr><tr><td>a)</td><td>Position/status</td><td>PDMR (Chief Financial Officer)</td></tr>
            <tr><td>a)</td><td>Description of the financial instrument</td><td>Ordinary shares of GBP 0.01</td><td>ISIN: GB00BL6NGV24</td></tr>
            <tr><td>b)</td><td>Nature of the transaction</td><td>1. Purchase of Partnership Shares under the SIP</td></tr>
            <tr><td>c)</td><td>Price(s) and volume(s)</td><td>Price(s)</td><td>Volume(s)</td></tr>
            <tr><td>1.</td><td>64.30p</td><td>233</td></tr><tr><td>d)</td><td>Aggregated information</td><td>n/a</td></tr>
            <tr><td>e)</td><td>Date of the transaction</td><td>13 May 2026</td></tr></table>"""
        val sip = InsiderQuellen.nsmLesen(plan, InsiderQuellen.NsmMeldung("id2", "DR. MARTENS PLC", "2026-05-14", "x")).single()
        assertEquals("S", sip.art)
        assertEquals(233.0, sip.n, 1e-9)
        // Bei Sonstigem zeigt die App keinen Betrag
        assertEquals(0.0, sip.preis, 1e-9)
        assertEquals("S", sip.r)
        // Datumsspalte ohne Jahr und zerteilte Zahlen („2 50“, „1 5 September“)
        val tabelle = """<table><tr><td>1</td><td>Details of the person discharging managerial responsibilities</td></tr>
            <tr><td>a)</td><td>Name</td><td>Jane Beispiel</td></tr><tr><td>b)</td><td>Position/status</td><td>Chief Executive Officer</td></tr>
            <tr><td>a)</td><td>Description of the financial instrument</td><td>Ordinary shares of 2.5p each</td><td>ISIN: GB00BYW0RN01</td></tr>
            <tr><td>b)</td><td>Nature of the transaction</td><td>Purchase of shares</td></tr>
            <tr><td>c)</td><td>Price(s) and volume(s)</td><td>Price(s)</td><td>Volume(s)</td><td>Date</td></tr>
            <tr><td>$4.42</td><td>250</td><td>11 September</td></tr><tr><td>$ 4.17</td><td>2 50</td><td>1 5 September</td></tr>
            <tr><td>d)</td><td>Aggregated information</td><td>Aggregated volume</td><td>5 00</td></tr>
            <tr><td>e)</td><td>Date of the transaction</td><td>11 September 2026</td></tr></table>"""
        val t = InsiderQuellen.nsmLesen(tabelle, InsiderQuellen.NsmMeldung("id3", "BEISPIEL PLC", "2026-09-16", "x")).single()
        assertEquals("K", t.art)
        assertEquals(500.0, t.n, 1e-9)
        assertEquals((4.42 * 250 + 4.17 * 250) / 500, t.preis, 1e-9)
        assertEquals("USD", t.w)
        // Waehrungscode im Preisabschnitt (Zweitnotiz in Johannesburg)
        val zar = tabelle.replace("$4.42", "60.91").replace("$ 4.17", "60.91").replace("Aggregated volume", "Price ZAR 60.91")
        assertEquals("ZAR", InsiderQuellen.nsmLesen(zar, InsiderQuellen.NsmMeldung("id4", "BEISPIEL PLC", "2026-09-16", "x")).single().w)
        // Kanadische Dollar und Pence nur im Summenabschnitt
        val cad = tabelle.replace("$4.42", "C$2.59").replace("$ 4.17", "C$2.60")
        assertEquals("CAD", InsiderQuellen.nsmLesen(cad, InsiderQuellen.NsmMeldung("id5", "BEISPIEL PLC", "2026-09-16", "x")).single().w)
        val pence = tabelle.replace("$4.42", "97.90").replace("$ 4.17", "97.50").replace("Aggregated volume", "Price (GBP)</td><td>97.70p")
        assertEquals(0.977, InsiderQuellen.nsmLesen(pence, InsiderQuellen.NsmMeldung("id6", "BEISPIEL PLC", "2026-09-16", "x")).single().preis, 1e-9)
        // Pence nur aus der Summe erkennbar; Verkauf und Kauf derselben Person am selben Tag ist Sonstiges
        val summe = tabelle.replace("$4.42", "61.45").replace("$ 4.17", "61.45").replace("Aggregated volume", "Total</td><td>£307.25")
        assertEquals(0.6145, InsiderQuellen.nsmLesen(summe, InsiderQuellen.NsmMeldung("id7", "BEISPIEL PLC", "2026-09-16", "x")).single().preis, 1e-9)
        val zwei = InsiderQuellen.nsmLesen(tabelle + tabelle.replace("Purchase of shares", "Sale of shares"), InsiderQuellen.NsmMeldung("id8", "BEISPIEL PLC", "2026-09-16", "x"))
        assertEquals(listOf("S", "S"), zwei.map { it.art })
        // Fliesstext statt Tabelle
        val satz = tabelle.replace(Regex("<tr><td>\\$4.42.*?1 5 September</td></tr>"), "<tr><td>Shares bought: 50,000 shares at 209.5 pence each</td></tr>")
        val s = InsiderQuellen.nsmLesen(satz, InsiderQuellen.NsmMeldung("id9", "BEISPIEL PLC", "2026-09-16", "x")).single()
        assertEquals(50000.0, s.n, 1e-9)
        assertEquals(2.095, s.preis, 1e-9)
        // Vor- und Nachname getrennt, Punkt als Tausendertrennzeichen, zerteilte Nachkommastelle
        val felder = tabelle.replace("<td>Name</td><td>Jane Beispiel</td>", "<td>First Name</td><td>Ian</td></tr><tr><td>b)</td><td>Last Name</td><td>Wilcock</td>")
            .replace("$ 4.17", "$4 .17")
        val f = InsiderQuellen.nsmLesen(felder, InsiderQuellen.NsmMeldung("id10", "BEISPIEL PLC", "2026-09-16", "x")).single()
        assertEquals("Ian Wilcock", f.p)
        assertEquals((4.42 * 250 + 4.17 * 250) / 500, f.preis, 1e-9)
        val punkt = tabelle.replace(Regex("<tr><td>\\$4.42.*?1 5 September</td></tr>"), "<tr><td>£7.7824</td><td>4.900</td></tr>").replace("5 00", "4,900</td><td>£38,133.76")
        assertEquals(4900.0, InsiderQuellen.nsmLesen(punkt, InsiderQuellen.NsmMeldung("id11", "BEISPIEL PLC", "2026-09-16", "x")).single().n, 1e-9)
        // Summenspalte mit Leerzeichen als Tausendertrennzeichen
        val gdr = tabelle.replace(Regex("<tr><td>\\$4.42.*?1 5 September</td></tr>"), "<tr><td>8.20 USD</td><td>6097</td><td>49 995.40</td></tr>")
        val g2 = InsiderQuellen.nsmLesen(gdr, InsiderQuellen.NsmMeldung("id12", "BEISPIEL PLC", "2026-09-16", "x")).single()
        assertEquals(6097.0, g2.n, 1e-9)
        assertEquals(8.2, g2.preis, 1e-9)
        // Abgetrennte Namensendung, kurze Woerter bleiben getrennt
        assertEquals("Geoffrey Wilding", InsiderQuellen.ganz("Geoffr ey Wilding"))
        assertEquals("Chair of the Board", InsiderQuellen.ganz("Chair of the Board"))
        assertEquals("Maria de Souza", InsiderQuellen.ganz("Maria de Souza"))
    }

    @Test fun schweiz() {
        val json = """{"status":"Ok","totalCount":3,"itemList":[
            {"correctorId":"","obligorRelatedPartyInd":"","transactionSize":1500.0,"transactionAmountPerSecurityCHF":35.3,"obligorFunctionCode":"1","transactionAmountCHF":52950.0,
             "notificationSubmitter":"Beispiel AG","ISIN":"CH0000000001","transactionConditions":"","transactionDate":20261021,"correcteeId":"","notificationId":"A1","buySellIndicator":"1","securityTypeCode":"7"},
            {"obligorRelatedPartyInd":"I","transactionSize":100.0,"transactionAmountPerSecurityCHF":5.4,"obligorFunctionCode":"2","transactionAmountCHF":540.0,
             "notificationSubmitter":"Beispiel AG","ISIN":"CH0000000001","transactionConditions":"AusÃ¼bung von Anrechten","transactionDate":20261022,"correcteeId":"","notificationId":"A2","buySellIndicator":"1","securityTypeCode":"7"},
            {"transactionSize":10.0,"obligorFunctionCode":"2","notificationSubmitter":"Beispiel AG","ISIN":"CH0000000001","transactionDate":20261022,"correcteeId":"A1","notificationId":"A3","buySellIndicator":"2","securityTypeCode":"7"}]}"""
        val (total, liste) = InsiderWelt.sixLesen(json)
        assertEquals(3, total)
        assertEquals(listOf("A2", "A3"), liste.map { it.id })
        val a2 = liste.first()
        assertEquals("Ausübung von Anrechten", a2.t)
        assertEquals("S", a2.art)
        assertEquals("N", a2.r)
        assertEquals("V", liste[1].art)
        assertEquals("A", liste[1].r)
        // Ohne Namen zaehlt jede Meldung als eigene Person
        val t = EuInsider.trend(listOf(liste[1], liste[1].copy(id = "A4"), liste[1].copy(id = "A5", art = "K")).map { it.copy(wert = 10_000.0) }, "2026-01-01")!!
        assertEquals(2, t.verkaeufer)
        assertEquals(1, t.kaeufer)
    }

    @Test fun indien() {
        val xml = """<in-bse-co:Symbol contextRef="MainI">DMART</in-bse-co:Symbol><in-bse-co:NameOfTheCompany contextRef="MainI">AVENUE SUPERMARTS LIMITED</in-bse-co:NameOfTheCompany>
            <in-bse-co:DateOfFiling contextRef="MainI">2026-10-22</in-bse-co:DateOfFiling><in-bse-co:ISINCode contextRef="MainI">INE192R01011</in-bse-co:ISINCode>
            <in-bse-co:TypeOfInstrument contextRef="Disclosure1">Equity</in-bse-co:TypeOfInstrument><in-bse-co:CategoryOfPerson contextRef="Disclosure1">KMP</in-bse-co:CategoryOfPerson>
            <in-bse-co:NameOfThePerson contextRef="Disclosure1">Mahesh Nagda</in-bse-co:NameOfThePerson>
            <in-bse-co:SecuritiesAcquiredOrDisposedNumberOfSecurity contextRef="Disclosure1" unitRef="shares">300</in-bse-co:SecuritiesAcquiredOrDisposedNumberOfSecurity>
            <in-bse-co:SecuritiesAcquiredOrDisposedValueOfSecurity contextRef="Disclosure1" unitRef="INR">986200</in-bse-co:SecuritiesAcquiredOrDisposedValueOfSecurity>
            <in-bse-co:SecuritiesAcquiredOrDisposedTransactionType contextRef="Disclosure1">Sell</in-bse-co:SecuritiesAcquiredOrDisposedTransactionType>
            <in-bse-co:DateOfAllotmentAdviceOrAcquisitionOfSharesOrSaleOfSharesSpecifyFromDate contextRef="Disclosure1">2026-10-21</in-bse-co:DateOfAllotmentAdviceOrAcquisitionOfSharesOrSaleOfSharesSpecifyFromDate>
            <in-bse-co:ModeOfAcquisitionOrDisposal contextRef="Disclosure1">Market Sale</in-bse-co:ModeOfAcquisitionOrDisposal>
            <in-bse-co:TypeOfInstrument contextRef="Disclosure2">Equity</in-bse-co:TypeOfInstrument><in-bse-co:CategoryOfPerson contextRef="Disclosure2">Designated Person</in-bse-co:CategoryOfPerson>
            <in-bse-co:NameOfThePerson contextRef="Disclosure2">Santosh Singh</in-bse-co:NameOfThePerson>
            <in-bse-co:SecuritiesAcquiredOrDisposedNumberOfSecurity contextRef="Disclosure2">924</in-bse-co:SecuritiesAcquiredOrDisposedNumberOfSecurity>
            <in-bse-co:SecuritiesAcquiredOrDisposedTransactionType contextRef="Disclosure2">Pledge Invoke</in-bse-co:SecuritiesAcquiredOrDisposedTransactionType>
            <in-bse-co:ModeOfAcquisitionOrDisposal contextRef="Disclosure2">Invocation of pledge</in-bse-co:ModeOfAcquisitionOrDisposal>"""
        val l = InsiderWelt.nseXmlLesen(xml, "4037")
        assertEquals(2, l.size)
        assertEquals("V", l[0].art)
        assertEquals("S", l[0].r)
        assertEquals("INE192R01011", l[0].isin)
        assertEquals(986200.0 / 300, l[0].preis, 1e-9)
        assertEquals("S", l[1].art)
        val liste = """[{"symbol":"DMART","appId":"4037","xmlFileName":"https://nsearchives.nseindia.com/x.xml"},{"symbol":"TCS","appId":"1","xmlFileName":"https://y"}]"""
        assertEquals(listOf("4037" to "https://nsearchives.nseindia.com/x.xml"), InsiderWelt.nseListeLesen(liste, "DMART"))
        assertEquals("https://www.nseindia.com/api/corporates-pit-gg?index=equities&from_date=24-07-2026&to_date=22-10-2026", InsiderWelt.nseListe("2026-07-24", "2026-10-22"))
    }

    @Test fun hongkongUndZuordnung() {
        val html = """<table><tr><td>DA20260311E00001</td><td>Elhedery Georges Bahjat</td><td>1201(L)</td><td>94,749(L)</td><td>HKD 12.7961</td><td>1,000(L)</td><td>0.00(L)</td><td>11/03/2026</td><td></td><td></td></tr>
            <tr><td>CS20260818E00002</td><td>JPMorgan Chase &amp; Co.</td><td>1316(L)</td><td>554,606,339(L)</td><td>HKD 162.3656</td><td>1(L)</td><td>5.00(L)</td><td>18/08/2026</td><td></td><td></td></tr></table>"""
        val l = InsiderWelt.hkListeLesen(html, "GB0005405286", "HSBC")
        assertEquals(2, l.size)
        assertEquals("V", l[0].art)
        assertEquals("A", l[0].r)
        assertEquals(12.7961, l[0].preis, 1e-9)
        assertEquals("2026-03-11", l[0].d)
        assertEquals("S", l[1].art)
        assertEquals("N", l[1].r)
        assertEquals("JPMorgan Chase & Co.", l[1].p)
        assertEquals("HK" to "700", InsiderWelt.boerse("0700.HK"))
        assertEquals("AU" to "BHP", InsiderWelt.boerse("BHP.AX"))
        assertEquals("IN" to "RELIANCE", InsiderWelt.boerse("RELIANCE.NS"))
        assertEquals("CA", InsiderWelt.boerse("SHOP.TO")?.first)
        assertNull(InsiderWelt.boerse("SAP.DE"))
    }

    @Test fun australien() {
        val z = listOf(
            "Name of entity", "BEISPIEL LIMITED", "Name of Director", "Mark Vassella", "Date of change", "11", "September", "2026",
            "No. of securities held prior to change", "10,000 Ordinary Shares", "Class", "Ordinary Shares", "Number acquired", "1,845 Ordinary Shares",
            "Number disposed", "Nil", "Value/Consideration", "$60.50 per share", "No. of securities held after change", "11,845 Ordinary Shares",
            "Nature of change", "On-market trade",
        )
        val g = InsiderWelt.asxLesen(z, InsiderWelt.AsxMeldung("k", "2026-09-12", "BEISPIEL LIMITED", "AU0000000001"))!!
        assertEquals("Mark Vassella", g.p)
        assertEquals("K", g.art)
        assertEquals(1845.0, g.n, 1e-9)
        assertEquals(60.5, g.preis, 1e-9)
        assertEquals("2026-09-11", g.d)
        val liste = """{"data":{"items":[{"announcementTypes":["Change of Director's Interest Notice"],"companyInfo":[{"displayName":"BEISPIEL","isin":"AU0000000001"}],
            "date":"2026-10-22T03:11:01.000Z","documentKey":"k1","headline":"Change of Director's Interest Notice","symbol":"BSP"},
            {"announcementTypes":["Change in substantial holding"],"companyInfo":[],"date":"2026-10-22T03:11:01.000Z","documentKey":"k2","headline":"x","symbol":"BSP"}]}}"""
        val m = InsiderWelt.asxListeLesen(liste).single()
        assertEquals("BSP", m.symbol)
        assertEquals("AU0000000001", m.isin)
    }

    @Test fun registerImport() {
        // BSE-Gesamtliste: nur Kaeufe und Verkaeufe an der Boerse, Pfaendungen fallen weg
        val csv = "Security Code,Security Name,Name of Person,Category of person,Type of Securities held Prior to acquisition/Disposed),Number of Securities held Prior to acquisition/Disposed,%   of  Securities held Prior to acquisition/Disposed,Type of Securities Acquired/Disposed/Pledge etc.,Number of Securities Acquired/Disposed/Pledge etc.,Value  of Securities Acquired/Disposed/Pledge etc,Transaction Type ( Buy/Sale/Pledge/Revoke/Invoke),Type of Securities held Post  acquisition/Disposed/Pledge  etc,Number of Securities held Post  acquisition/Disposed/Pledge etc,Post-Transaction % of Shareholding,Date of acquisition of shares/sale of shares/Date of Allotment(From date),Date of acquisition of shares/sale of shares/Date of Allotment( To date  ),Date of Intimation to Company,Mode of Acquisition,Trading in Derivative - Type of Contract,Derivative Contract  Specification,Derivatives - Buy Value (Notional),Derivatives - Buy-Number of Units(Contracts*lot size),Derivatives - Sales Value (Notional),Derivatives - Sales-Number of Units(Contracts * lot size),Exchange on which the Trade was executed,Reported to Exchange\n" +
            "590003,Karur Vysya Bank Ltd,G Rajasekaran,Promoter,Equity,1982319,0.21,Equity,2000,769230.00,Disposal,-,1980319,0.20,21 Oct 2026,21 Oct 2026,22 Oct 2026,Market Sale,,,,,,,NSE,22 Oct 2026\n" +
            "590003,Karur Vysya Bank Ltd,G Rajasekaran,Promoter,Equity,0,0,Equity,5000,0,Pledge,-,0,0,21 Oct 2026,21 Oct 2026,22 Oct 2026,Creation Of Pledge,,,,,,,NA,22 Oct 2026\n"
        val e = de.troetenrepublik.app.market.RegisterImport.erkennen(csv.toByteArray(), java.time.LocalDate.of(2026, 10, 23))
        assertTrue(e is de.troetenrepublik.app.market.RegisterImport.Ergebnis.Insider)
        val g = (e as de.troetenrepublik.app.market.RegisterImport.Ergebnis.Insider).geschaefte.single()
        assertEquals("V", g.art)
        assertEquals(384.615, g.preis, 1e-3)
        assertEquals("2026-10-21", g.d)
        assertEquals("590003", g.c)
        assertEquals("IN", e.land)
        // PDF wird nicht gelesen, Unbekanntes gemeldet
        assertTrue((de.troetenrepublik.app.market.RegisterImport.erkennen("%PDF-1.4".toByteArray(), java.time.LocalDate.now()) as de.troetenrepublik.app.market.RegisterImport.Ergebnis.Unbekannt).pdf)
    }

    @Test fun landesstaende() {
        // Zuordnung: Kennung, ISIN, in Indien der Name (BSE-Liste ohne ISIN)
        val bse = EuInsider.Geschaeft(e = "Karur Vysya Bank Ltd", isin = "", p = "G Rajasekaran", d = "2026-10-21", c = "590003")
        assertTrue(InsiderWelt.passt(bse, "IN", "KARURVYSYA", "INE036D01028", "Karur Vysya Bank Limited"))
        assertTrue(InsiderWelt.passt(bse, "IN", "590003", "", ""))
        assertTrue(!InsiderWelt.passt(bse, "IN", "SBIN", "INE062A01020", "State Bank of India"))
        val hk = EuInsider.Geschaeft(e = "Tencent", isin = "", p = "Ma Huateng", d = "2026-10-21", c = "700")
        assertTrue(InsiderWelt.passt(hk, "HK", "700", "", ""))
        assertTrue(!InsiderWelt.passt(hk, "HK", "7000", "", "Tencent"))
        assertEquals("IN" to "590003", InsiderWelt.boerse("590003.BO"))
        assertEquals("karur vysya bank", InsiderWelt.name("The Karur Vysya Bank Ltd."))
    }
}
