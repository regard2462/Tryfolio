package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Abrufkontingent
import de.troetenrepublik.app.market.Investegate
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.NachrichtenApis
import de.troetenrepublik.app.market.NasdaqNordic
import de.troetenrepublik.app.market.NewsRepository
import de.troetenrepublik.app.market.Rss
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

// Tests der neuen Quellen der Fassung 5.95: VX-Terminkurve der Cboe, Investegate, Nasdaq Nordic, die neuen
// Feeds mit ihren Grenzen sowie EODHD-Kontingent und EODHD-Leser. Alle Zeiten nachgerechnet: London hat am
// 3. November 2026 GMT, Berlin CET, am 6. August 2026 Sommerzeit.
class Fassung595QuellenTest {

    // VX-Terminkurve: nur Produkt VX, Monatskontrakte erkannt, Kurve, Verhaeltnis und Form
    @Test
    fun vxKurve() {
        val tag = LocalDate.of(2026, 11, 2)
        assertEquals("https://www.cboe.com/us/futures/market_statistics/settlement/csv?dt=2026-11-02", VxKurve.adresse(tag))
        val csv = """
Product,Symbol,Expiration Date,Price
VX,VX44/X6,2026-11-04,20.3752
VX,VX45/X6,2026-11-11,20.3752
VX,VX/X6,2026-11-18,20.3752
VX,VX47/X6,2026-11-24,20.3752
VX,VX48/Z6,2026-12-01,20.3752
VX,VX49/Z6,2026-12-09,20.3752
VX,VX/Z6,2026-12-16,19.8438
VX,VX/F7,2027-01-20,20.3798
VX,VX/G7,2027-02-17,20.6025
VX,VX/H7,2027-03-17,20.7343
VXM,VXM/X6,2026-11-18,20.3752
VXM,VXM/Z6,2026-12-16,19.8438
"""
        val stand = VxKurve.lesen(csv, tag)!!
        assertEquals(tag, stand.tag)
        // nur VX, VXM faellt weg; fuenf Monatskontrakte, fuenf Wochenkontrakte
        assertEquals(10, stand.kontrakte.size)
        assertEquals(5, stand.kontrakte.count { it.monat })
        assertEquals("VX/X6", stand.kontrakte.first { it.monat }.symbol)
        val kurve = VxKurve.kurve(stand)
        assertEquals(tag.toEpochDay(), kurve.tag)
        assertEquals(5, kurve.punkte.size)
        assertEquals(Marktkurven.Punkt(16, 20.3752), kurve.punkte[0])
        assertEquals(Marktkurven.Punkt(44, 19.8438), kurve.punkte[1])
        assertEquals(Marktkurven.Punkt(79, 20.3798), kurve.punkte[2])
        // die ersten zwei Monatskontrakte mit mehr als sieben Tagen Restlaufzeit
        val q = VxKurve.verhaeltnis(stand)!!
        assertEquals(20.3752 / 19.8438, q, 1e-12)
        assertEquals(Marktkurven.VixForm.BACKWARDATION, VxKurve.form(q))
        // leere Abrechnungsdatei (Feiertag) und Datei ohne VX-Zeile
        assertNull(VxKurve.lesen("", tag))
        assertNull(VxKurve.lesen("Product,Symbol,Expiration Date,Price\r\nVXM,VXM/Z6,2026-12-16,19.8438\r\n", tag))
    }

    // Investegate: Kuerzel aus Yahoo-Symbolen
    @Test
    fun investegateTicker() {
        assertEquals("BT.A", Investegate.ticker("BT-A.L"))
        assertEquals("LSEG", Investegate.ticker("LSEG.L"))
        assertEquals("FCIT", Investegate.ticker("FCIT.L"))
        assertNull(Investegate.ticker("AAPL"))
        assertNull(Investegate.ticker("SAP.DE"))
    }

    // Kategorieseite: Zeit in London, Firma mit Kuerzel aus dem Text, Quelle RNS, Titel und Link
    @Test
    fun investegateListe() {
        val html = """
<html><body><table><thead></thead><tbody>
<tr>
                <td>03 Nov 2026 03:31 PM</td>
                <td>
                    <div class="text-center"><a class="regulatory source-RNS" title="supplier: Regulatory News Service (Regulatory)" href="https://www.investegate.co.uk/source/RNS">RNS</a></div>
                </td>
                <td>
                                        <div class="align-items-center d-flex"><div class="mr-2"><a href="https://www.investegate.co.uk/company/FCIT"><img src="https://assets.investegate.co.uk/public/assets/images/icoChart.gif" title="Factsheet" alt="Factsheet"></a></div>
                        <div><a href="https://www.investegate.co.uk/company/FCIT">F&amp;C Investment Trust (FCIT)</a></div></div>
                </td>
                <td>
                    <a class="announcement-link" href="https://www.investegate.co.uk/announcement/rns/f-c-investment-trust--fcit/director-pdmr-shareholding/9856570">Director/PDMR Shareholding</a>
                </td>
            </tr>
<tr>
                <td>03 Nov 2026 03:31 PM</td>
                <td>
                    <div class="text-center"><a class="regulatory source-RNS" title="supplier: Regulatory News Service (Regulatory)" href="https://www.investegate.co.uk/source/RNS">RNS</a></div>
                </td>
                <td>
                                        <div class="align-items-center d-flex"><div class="mr-2"><a href="https://www.investegate.co.uk/company/FCIT"><img src="https://assets.investegate.co.uk/public/assets/images/icoChart.gif" title="Factsheet" alt="Factsheet"></a></div>
                        <div><a href="https://www.investegate.co.uk/company/FCIT">F&amp;C Investment Trust (FCIT)</a></div></div>
                </td>
                <td>
                    <a class="announcement-link" href="https://www.investegate.co.uk/announcement/rns/f-c-investment-trust--fcit/director-pdmr-shareholding/9856569">Director/PDMR Shareholding</a>
                </td>
            </tr>
</tbody></table></body></html>
"""
        val liste = Investegate.listeLesen(html)
        assertEquals(2, liste.size)
        val m = liste[0]
        // 15:31 Londoner Zeit am 3. November 2026 ist GMT
        assertEquals(1793719860000L, m.zeit)
        assertEquals("F&C Investment Trust", m.firma)
        assertEquals("FCIT", m.kuerzel)
        assertEquals("RNS", m.quelle)
        assertEquals("Director/PDMR Shareholding", m.titel)
        assertEquals("https://www.investegate.co.uk/announcement/rns/f-c-investment-trust--fcit/director-pdmr-shareholding/9856570", m.link)
    }

    // Firmenseite: nur Dealing-Titel, Zeit aus Datum und Uhrzeit, Firma aus dem Kuerzel
    @Test
    fun investegateFirma() {
        val html = """
<html><body><table><tbody>
<tr>
            <td>03 Nov 2026</td>
            <td>03:30 PM</td>
            <td>
                <div class="text-center">
                    <a class="regulatory source-RNS" title="supplier: Regulatory News Service (Regulatory)" href="https://www.investegate.co.uk/source/RNS">RNS</a>
                </div>
            </td>
            <td>
                <a class="announcement-link" href="https://www.investegate.co.uk/announcement/rns/london-stock-exchange-group--lseg/director-pdmr-shareholding/9856567">Director/PDMR Shareholding</a>
            </td>
        </tr>
<tr>
            <td>02 Nov 2026</td>
            <td>04:00 PM</td>
            <td>
                <div class="text-center">
                    <a class="regulatory source-RNS" title="supplier: Regulatory News Service (Regulatory)" href="https://www.investegate.co.uk/source/RNS">RNS</a>
                </div>
            </td>
            <td>
                <a class="announcement-link" href="https://www.investegate.co.uk/announcement/rns/london-stock-exchange-group--lseg/transaction-in-own-shares/9854173">Transaction in Own Shares</a>
            </td>
        </tr>
<tr>
            <td>02 Nov 2026</td>
            <td>12:00 PM</td>
            <td>
                <div class="text-center">
                    <a class="regulatory source-RNS" title="supplier: Regulatory News Service (Regulatory)" href="https://www.investegate.co.uk/source/RNS">RNS</a>
                </div>
            </td>
            <td>
                <a class="announcement-link" href="https://www.investegate.co.uk/announcement/rns/london-stock-exchange-group--lseg/total-voting-rights/9853593">Total Voting Rights</a>
            </td>
        </tr>
</tbody></table></body></html>
"""
        val liste = Investegate.firmaLesen(html, "LSEG")
        assertEquals(1, liste.size)
        assertEquals("Director/PDMR Shareholding", liste[0].titel)
        assertEquals("LSEG", liste[0].firma)
        assertEquals("LSEG", liste[0].kuerzel)
        // 15:30 Londoner Zeit am 3. November 2026
        assertEquals(1793719800000L, liste[0].zeit)
        assertTrue(Investegate.istDealing("Director/PDMR Shareholding"))
        assertTrue(Investegate.istDealing("Dealings in Securities by Director"))
        assertFalse(Investegate.istDealing("Director Declaration"))
        assertFalse(Investegate.istDealing("Transaction in Own Shares"))
        assertFalse(Investegate.istDealing("Total Voting Rights"))
        assertFalse(Investegate.istDealing("Directorate change"))
        assertFalse(Investegate.istDealing("Director appointments"))
    }

    // Einzelmeldung: Titel, Text nur aus dem template, Tabellen als Zellenzeilen, Tracker weg, END bleibt
    @Test
    fun investegateMeldung() {
        val html = """
<html><head><style>body{color:red}</style></head><body>
<h1 id="main-title">Director/PDMR Shareholding</h1>
<p class="summary">Zusammenfassung der Meldung ausserhalb des template</p>
<template shadowrootmode="open">
<style>.rn-1{font-weight:bold}</style>
<body><img src="https://tracker.live.rns-distribution.com/track.live-rns/6364186_d9353a075fe0b610698e373da828b7e4.png" width="1" height="1" /><div style="text-align:left;">  <div>London Stock Exchange Group PLC</div>  <div>03 November 2026</div>  <div>&#160;</div></div>
<p class="rn-1"><span class="rn-2">NOTIFICATION OF TRANSACTIONS OF DIRECTORS, PERSONS DISCHARGING MANAGERIAL RESPONSIBILITIES OR CONNECTED PERSONS</span></p>
<table cellspacing="0" cellpadding="0" class="TableGrid rn-3"><tbody>
<tr class="rn-6">
<td class="rn-7"><p class="rn-5"><span>a)</span></p></td>
<td class="rn-8"><p class="rn-5"><span>Name</span></p></td>
<td class="rn-9"><p class="rn-5"><span>David Schwimmer</span></p></td>
</tr>
<tr style="height:44.6pt">
<td class="rn-7"><p class="rn-5"><span>c)</span></p></td>
<td class="rn-8"><p class="rn-5"><span>Price(s) and volume(s)</span></p></td>
<td class="rn-9"><p class="rn-5"><span class="rn-15">&#160;</span></p>
<table cellspacing="0" cellpadding="0" class="TableGrid rn-18"><tbody>
<tr><td class="rn-19"><p class="rn-20"><span class="rn-21">Price(s)</span></p></td>
<td class="rn-22"><p class="rn-20"><span class="rn-21">Volume(s)</span></p></td></tr>
<tr><td class="rn-23"><p class="rn-20"><span class="rn-21">&#163;65.91 per share</span></p></td>
<td class="rn-24"><p class="rn-20"><span class="rn-21">276 ordinary shares</span></p></td></tr>
</tbody></table>
<p class="rn-5"></p></td>
</tr>
<tr class="rn-4">
<td class="rn-7"><p class="rn-5"><span>e)</span></p></td>
<td class="rn-8"><p class="rn-5"><span>Date of the transaction</span></p></td>
<td class="rn-9"><p class="rn-5"><span>30 October 2026</span></p></td>
</tr>
</tbody></table>
<p>This information is provided by RNS, the news service of the London Stock Exchange. RNS is approved by the Financial Conduct Authority to act as a Primary Information Provider in the United Kingdom. T</p>
<p>END</p>
</template>
</body></html>
"""
        val bogen = Investegate.meldungLesen(html)!!
        assertEquals("Director/PDMR Shareholding", bogen.titel)
        val zeilen = bogen.zeilen
        assertTrue(zeilen.contains(listOf("London Stock Exchange Group PLC")))
        assertTrue(zeilen.contains(listOf("03 November 2026")))
        assertTrue(zeilen.contains(listOf("a)", "Name", "David Schwimmer")))
        // Tabelle in einer Zelle: erst die aeussere Zeile, dann die innere Tabelle
        assertTrue(zeilen.contains(listOf("c)", "Price(s) and volume(s)")))
        assertTrue(zeilen.contains(listOf("Price(s)", "Volume(s)")))
        assertTrue(zeilen.contains(listOf("£65.91 per share", "276 ordinary shares")))
        assertTrue(zeilen.contains(listOf("e)", "Date of the transaction", "30 October 2026")))
        assertTrue(zeilen.contains(listOf("END")))
        val flach = zeilen.joinToString("\n") { it.joinToString(" | ") }
        assertTrue(flach.contains("This information is provided by RNS"))
        assertFalse(flach.contains("tracker.live.rns-distribution.com"))
        assertFalse(flach.contains("6364186"))
        assertFalse(flach.contains("ausserhalb"))
        assertFalse(flach.contains("color:red"))
    }

    // Nasdaq Nordic: Gruppen, Suchnamen, Adresse
    @Test
    fun nasdaqGruppeUndAdresse() {
        assertEquals("NordicMainMarkets", NasdaqNordic.gruppe("DK"))
        assertEquals("NordicMainMarkets", NasdaqNordic.gruppe("FI"))
        assertEquals("NordicMainMarkets", NasdaqNordic.gruppe("IS"))
        assertEquals("BalticMainMarkets", NasdaqNordic.gruppe("EE"))
        assertEquals("BalticMainMarkets", NasdaqNordic.gruppe("LV"))
        assertEquals("BalticMainMarkets", NasdaqNordic.gruppe("LT"))
        assertNull(NasdaqNordic.gruppe("US"))
        assertEquals("Novo Nordisk", NasdaqNordic.suchname("Novo Nordisk A/S"))
        assertEquals("F-Secure", NasdaqNordic.suchname("F-Secure Oyj"))
        assertEquals("Wisa Group", NasdaqNordic.suchname("Wisa Group Oyj"))
        assertEquals("Company", NasdaqNordic.suchname("Company AB (publ)"))
        val adresse = NasdaqNordic.adresse("DK", "Novo Nordisk A/S", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))!!
        assertTrue(adresse.contains("freeText=Novo%20Nordisk"))
        assertTrue(adresse.contains("globalName=NordicMainMarkets"))
        assertTrue(adresse.contains("fromDate=2026-08-01"))
        assertTrue(adresse.contains("toDate=2026-08-31"))
        assertTrue(adresse.contains("cnscategory=Managers%27%20Transactions"))
        assertNull(NasdaqNordic.adresse("US", "Apple", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)))
    }

    // Nasdaq Nordic: beide Antwortformen, Zeiten in Europe/Berlin, Anhaenge, Namensabgleich
    @Test
    fun nasdaqLesen() {
        val objekt = """
{
  "results" : {
    "item" : [ {
      "headline" : "Evli Plc: Managers' Transactions - Pessala",
      "messageUrl" : "https://view.news.eu.nasdaq.com/view?id=bc0a2c18dac86d0a7ba85e19a513b9345&lang=en&src=listed",
      "releaseTime" : "2026-11-03 14:30:00",
      "market" : "Main Market, Helsinki",
      "attachment" : [ ],
      "company" : "Evli Oyj"
    }, {
      "headline" : "F-Secure Corporation - Managers' transactions - Laaksonen",
      "messageUrl" : "https://view.news.eu.nasdaq.com/view?id=be393cf1166cfa5d0ab4ccd302fb80c09&lang=en&src=listed",
      "releaseTime" : "2026-11-03 13:15:00",
      "market" : "Main Market, Helsinki",
      "attachment" : [ ],
      "company" : "F-Secure Oyj"
    } ]
  }
}
"""
        val liste = NasdaqNordic.lesen(objekt)!!
        assertEquals(2, liste.size)
        assertEquals("Evli Oyj", liste[0].firma)
        assertEquals("Evli Plc: Managers' Transactions - Pessala", liste[0].titel)
        assertEquals("Main Market, Helsinki", liste[0].markt)
        assertTrue(liste[0].link.startsWith("https://view.news.eu.nasdaq.com/view?id=bc0a2c18"))
        // 14:30 Berliner Zeit am 3. November 2026 (CET) ist 13:30 UTC
        assertEquals(1793712600000L, liste[0].zeit)
        val frei = """
[
 {
  "headline": "Novo Nordisk A/S: Trading in Novo Nordisk shares by board members, executives and associated persons",
  "messageUrl": "https://view.news.eu.nasdaq.com/view?id=ba95a705b3eb34aba9d58eb5878baf2fe&lang=en&src=listed",
  "releaseTime": "2026-08-06 09:18:15",
  "market": "Main Market, Copenhagen",
  "attachment": [
   {
    "mimetype": "application/pdf",
    "fileName": "CA260806-insider-trading.pdf",
    "attachmentUrl": "https://attachment.news.eu.nasdaq.com/a5a05776854a7150a3b81e1584fa4ea06"
   }
  ],
  "company": "Novo Nordisk A/S"
 }
]
"""
        val nordisch = NasdaqNordic.lesen(frei)!!
        assertEquals(1, nordisch.size)
        val m = nordisch[0]
        // 6. August 2026: Berlin hat Sommerzeit, 09:18:15 ist 07:18:15 UTC
        assertEquals(1786000695000L, m.zeit)
        assertEquals("Novo Nordisk A/S", m.firma)
        assertEquals("Main Market, Copenhagen", m.markt)
        assertEquals(1, m.anhaenge.size)
        assertEquals("CA260806-insider-trading.pdf", m.anhaenge[0].first)
        assertEquals("https://attachment.news.eu.nasdaq.com/a5a05776854a7150a3b81e1584fa4ea06", m.anhaenge[0].second)
        assertTrue(NasdaqNordic.passt(m, "Novo Nordisk A/S"))
        assertTrue(NasdaqNordic.passt(m, "Novo Nordisk"))
        assertFalse(NasdaqNordic.passt(m, "F-Secure Oyj"))
        assertNull(NasdaqNordic.lesen("{ kaputt"))
        // nur ein gueltiges leeres Array ergibt eine leere Liste
        assertTrue(NasdaqNordic.lesen("[]")!!.isEmpty())
    }

    // Zeitzone Z wie GMT, Hosts und Namen ohne Lesemodus, ohneLesemodus fuer DER AKTIONAER, nur Ansicht
    @Test
    fun rssGrenzen() {
        assertEquals(1793714400000L, Rss.zeit("Tue, 03 Nov 2026 14:00:00 Z"))
        assertEquals(1793714400000L, Rss.zeit("Tue, 03 Nov 2026 14:00:00 GMT"))
        assertTrue(Rss.keinLesemodus("https://www.derstandard.at/story/3000000347029/x", ""))
        assertTrue(Rss.keinLesemodus("https://de.derstandard.at/story/1", ""))
        assertTrue(Rss.keinLesemodus("https://www.nasdaq.com/articles/us-job-openings", ""))
        assertTrue(Rss.keinLesemodus("https://example.org/x", "derStandard.at"))
        assertFalse(Rss.keinLesemodus("https://www.tagesschau.de/x", "tagesschau.de"))
        assertTrue(Rss.ohneLesemodus("https://www.deraktionaer.de/artikel/aktien/ferrari-aktie-20432038.html", ""))
        assertTrue(Rss.nurAnsicht(Rss.Eintrag("n", "https://www.nasdaq.com/articles/x", 0L, quelle = "Nasdaq")))
        assertTrue(Rss.nurAnsicht(Rss.Eintrag("s", "https://www.derstandard.at/story/1", 0L, quelle = "derStandard.at")))
        assertFalse(Rss.nurAnsicht(Rss.Eintrag("f", "https://www.tagesschau.de/x", 0L, quelle = "tagesschau.de")))
    }

    // Drittel-Begrenzung von derStandard.at: Abruf je Quelle und fertige Anzeigeliste
    @Test
    fun rssDrittel() {
        val quellen = Rss.eingebaut(deutsch = true)
        val standard = quellen.first { it.schluessel == "derstandard" }
        assertEquals("derStandard.at", standard.name)
        assertEquals(Rss.Kategorie.KONJUNKTUR, standard.kategorie)
        assertTrue(quellen.first { it.schluessel == "nasdaq-maerkte" }.browserKennung)
        assertEquals(30 * 60_000L, quellen.first { it.schluessel == "aktionaer" }.abstandMs)
        val neun = (1..9).map { Rss.Eintrag("t$it", "https://www.derstandard.at/story/$it", 0L, quelle = "derStandard.at") }
        val drei = Rss.begrenzt(standard, neun)
        assertEquals(3, drei.size)
        assertEquals("https://www.derstandard.at/story/1", drei.first().link)
        assertEquals(9, Rss.begrenzt(quellen.first { it.schluessel == "tagesschau-boerse" }, neun).size)
        // zwei andere Eintraege erlauben hoechstens einen STANDARD-Eintrag, den neuesten
        val std = (1..5).map { Rss.Eintrag("s$it", "https://www.derstandard.at/story/s$it", 0L, quelle = "derStandard.at") }
        val fremd = listOf(
            Rss.Eintrag("f1", "https://www.tagesschau.de/1", 1L, quelle = "tagesschau.de"),
            Rss.Eintrag("f2", "https://www.tagesschau.de/2", 2L, quelle = "tagesschau.de"))
        val anzeige = Rss.drittel(listOf(std[0], fremd[0], std[1], std[2], fremd[1], std[3], std[4]))
        assertEquals(3, anzeige.size)
        assertEquals(1, anzeige.count { it.quelle == "derStandard.at" })
        assertEquals("https://www.derstandard.at/story/s1", anzeige.first { it.quelle == "derStandard.at" }.link)
        assertTrue(Rss.drittel(std).isEmpty())
        assertFalse(Rss.unterkategorien(Rss.Anbieter.PRESSE, quellen).any { it.schluessel == "derstandard" })
    }

    // derStandard-Feed: Titel, Zeit mit Z, Zusammenfassung ohne Bild
    @Test
    fun standardFeed() {
        val xml = """
<?xml version="1.0" encoding="utf-8"?><rss version="2.0"><channel><title>Wirtschaft - derStandard.at</title><link>https://www.derstandard.at/wirtschaft</link><description>Lesen Sie jetzt umfassende Nachrichten</description><language>de-de</language>
<item><guid isPermaLink="true">https://www.derstandard.at/story/3000000347029/fuenf-millionen-notfallsgeld-und-ein-jagdgewehr-benko-steht-wieder-vor-gericht?ref=rss</guid><link>https://www.derstandard.at/story/3000000347029/fuenf-millionen-notfallsgeld-und-ein-jagdgewehr-benko-steht-wieder-vor-gericht?ref=rss</link><title>Fünf Millionen Notfallgeld und ein Jagdgewehr: Benko steht wieder vor Gericht</title><description>&lt;img style="float: right" src="https://i.ds.at/4IToZA/rs:fill:150:0/plain/lido-images/2026/10/02/d3aff714.jpg"&gt;Hat René Benko Signa-Investor Hans Peter Haselsteiner um fünf Millionen Euro betrogen? Um diese Fragen geht es im neuen Strafprozess</description><pubDate>Tue, 03 Nov 2026 14:00:00 Z</pubDate></item>
</channel></rss>
"""
        val liste = Rss.lesen(xml, "derStandard.at", Rss.Kategorie.KONJUNKTUR, schluessel = "derstandard")
        assertEquals(1, liste.size)
        val e = liste[0]
        assertEquals("Fünf Millionen Notfallgeld und ein Jagdgewehr: Benko steht wieder vor Gericht", e.titel)
        assertEquals(1793714400000L, e.zeit)
        assertTrue(e.zusammenfassung.startsWith("Hat René Benko Signa-Investor"))
        assertTrue(Rss.nurAnsicht(e))
        assertTrue(Rss.keinLesemodus(e.link, e.quelle))
    }

    // Nasdaq-Feed: Anriss gepolstert und mit Dollarbetrag, getrimmt und zusammengefasst
    @Test
    fun nasdaqFeed() {
        val xml = """
<?xml version="1.0" encoding="utf-8"?>
<rss version="2.0"><channel><title>Nasdaq Markets Feed</title></channel>
<item>
   <title>Company ABC Prices Units at ${'$'}0.575</title>
   <link>https://www.nasdaq.com/press-release/company-abc-prices-units</link>
   <description>
        (GlobeNewswire) - Company ABC has priced its offering of units at ${'$'}0.575 per unit, with plenty of whitespace around the text.
    </description>
   <pubDate>Tue, 03 Nov 2026 15:17:52 +0000</pubDate>
</item>
</rss>
"""
        val liste = Rss.lesen(xml, "Nasdaq", Rss.Kategorie.MAERKTE, schluessel = "nasdaq-maerkte")
        assertEquals(1, liste.size)
        val e = liste[0]
        assertEquals("(GlobeNewswire) - Company ABC has priced its offering of units at ${'$'}0.575 per unit, with plenty of whitespace around the text.", e.zusammenfassung)
        assertTrue(Rss.nurAnsicht(e))
    }

    // Lesemodus ohne Erlaubnis: kein Lesen, kein Nur-Text-Knopf, im Textzustand nur zurueck zum Original
    @Test
    fun lesemodusOhneErlaubnis() {
        assertEquals(Lesemodus.Knopf.KEINER,
            Lesemodus.knopf(webAnsicht = true, adresse = true, original = true, geladen = true, text = false, automatisch = false, laedt = false, liest = false, gelesen = false, erlaubt = false))
        assertEquals(Lesemodus.Knopf.KEINER,
            Lesemodus.knopf(webAnsicht = true, adresse = true, original = true, geladen = true, text = true, automatisch = false, laedt = false, liest = true, gelesen = true, erlaubt = false))
        assertEquals(Lesemodus.Knopf.ORIGINAL,
            Lesemodus.knopf(webAnsicht = true, adresse = true, original = false, geladen = true, text = true, automatisch = false, laedt = false, liest = false, gelesen = false, erlaubt = false))
    }

    // EODHD-Kontingent: fuenf je Abfrage, vier am UTC-Kalendertag, am naechsten Tag wieder frei
    @Test
    fun eodhdKontingent() {
        // 23:59 UTC, je drei Sekunden Abstand (Mindestabstand zwei Sekunden), vier Buchungen passen vor Mitternacht
        var jetzt = 20760L * 86_400_000L + 86_340_000L
        val k = Abrufkontingent(null) { jetzt }
        repeat(4) {
            assertTrue(k.erlaubt(Abrufkontingent.EODHD, Abrufkontingent.EODHD_KOSTEN))
            k.buchen(Abrufkontingent.EODHD, Abrufkontingent.EODHD_KOSTEN)
            jetzt += 3_000L
        }
        assertFalse(k.erlaubt(Abrufkontingent.EODHD, Abrufkontingent.EODHD_KOSTEN))
        // 00:01 UTC des naechsten Tages
        jetzt = 20761L * 86_400_000L + 60_000L
        assertTrue(k.erlaubt(Abrufkontingent.EODHD, Abrufkontingent.EODHD_KOSTEN))
        // Gegenfall: vier Buchungen ab 00:01 UTC, um 23:58 UTC desselben Tages ist nichts mehr frei
        val gegen = Abrufkontingent(null) { jetzt }
        repeat(4) {
            gegen.buchen(Abrufkontingent.EODHD, Abrufkontingent.EODHD_KOSTEN)
            jetzt += 3_000L
        }
        jetzt = 20761L * 86_400_000L + 86_280_000L
        assertEquals(20L to 20L, gegen.stand(Abrufkontingent.EODHD))
        assertFalse(gegen.erlaubt(Abrufkontingent.EODHD, Abrufkontingent.EODHD_KOSTEN))
    }

    // EODHD: Ticker-Abbildung, Adresse und Nachrichtenleser samt Fehlerobjekt
    @Test
    fun eodhd() {
        assertEquals("AAPL.US", NachrichtenApis.eodhdTicker("AAPL"))
        assertEquals("SAP.XETRA", NachrichtenApis.eodhdTicker("SAP.DE"))
        assertEquals("SAP.F", NachrichtenApis.eodhdTicker("SAP.F"))
        assertEquals("BT-A.LSE", NachrichtenApis.eodhdTicker("BT-A.L"))
        assertNull(NachrichtenApis.eodhdTicker("^GSPC"))
        assertEquals("BTC-USD.CC", NachrichtenApis.eodhdTicker("BTC-USD"))
        assertEquals("BTC-EUR.CC", NachrichtenApis.eodhdTicker("BTC-EUR"))
        assertEquals("EURUSD.FOREX", NachrichtenApis.eodhdTicker("EURUSD=X"))
        assertEquals("BRK-B.US", NachrichtenApis.eodhdTicker("BRK-B"))
        assertTrue(NachrichtenApis.eodhdAdresse("AAPL.US", "schluessel123").startsWith("https://eodhd.com/api/news?s=AAPL.US&limit=20&offset=0&api_token=schluessel123&fmt=json"))
        val probe = """[{"date":"2026-11-03T15:31:46+00:00","title":"Apple’s ’unprecedented’ memory warning adds fuel to wobbly Micron, SK hynix trade","content":"Investing.com -- Apple (NASDAQ:AAPL) may have delivered one of the clearest warnings yet that the global memory squeeze has further to run.\n\nOn its earnings call Monday, Apple CEO John Ternus described memory pricing as \"unprecedented\".","link":"https://www.investing.com/news/stock-market-news/memory-warning","symbols":"NASDAQ:AAPL","tags":"stocks,technology","sentiment":"Neutral"}]"""
        val e = NachrichtenApis.eodhdLesen(probe)
        assertNull(e.meldung)
        assertEquals(1, e.eintraege.size)
        val x = e.eintraege[0]
        assertEquals("Apple’s ’unprecedented’ memory warning adds fuel to wobbly Micron, SK hynix trade", x.titel)
        assertEquals(1793719906000L, x.zeit)
        assertEquals("https://www.investing.com/news/stock-market-news/memory-warning", x.link)
        assertEquals("EODHD", x.quelle)
        assertEquals(2, x.absaetze.size)
        assertTrue(x.zusammenfassung.startsWith("Investing.com -- Apple (NASDAQ:AAPL)"))
        // Fehlerobjekt ohne Eintraege, ohne den Schluessel zu nennen
        val fehler = NachrichtenApis.eodhdLesen("""{"error":"limit exceeded, try again tomorrow"}""")
        assertTrue(fehler.eintraege.isEmpty())
        assertEquals("limit exceeded, try again tomorrow", fehler.meldung)
        assertEquals("token invalid", NachrichtenApis.eodhdLesen("""{"message":"token invalid"}""").meldung)
    }

    // STANDARD-Erkennung ueber Schluessel, Namen und Rechner; Drittel und Nur Ansicht mit Google-Eintraegen
    @Test
    fun standardErkennung() {
        val google = Rss.Eintrag("t", "https://news.google.com/rss/articles/abc", 0L, herausgeber = "DER STANDARD", quelle = "Google News", quellen = setOf("google-wirtschaft"))
        assertTrue(Rss.istStandard(google))
        assertTrue(Rss.istStandard(google.copy(quellen = setOf("derstandard"))))
        assertTrue(Rss.istStandard(Rss.Eintrag("d", "https://www.derstandard.at/story/1", 0L, quelle = "derStandard.at")))
        assertFalse(Rss.istStandard(Rss.Eintrag("f", "https://www.tagesschau.de/x", 0L, quelle = "tagesschau.de")))
        // zwei Google-STANDARD-Eintraege und zwei fremde ergeben einen STANDARD-Eintrag
        val zweiter = google.copy(titel = "t2", link = "https://news.google.com/rss/articles/def")
        val fremd = listOf(
            Rss.Eintrag("f1", "https://www.tagesschau.de/1", 1L, quelle = "tagesschau.de"),
            Rss.Eintrag("f2", "https://www.tagesschau.de/2", 2L, quelle = "tagesschau.de"))
        val anzeige = Rss.drittel(listOf(google, zweiter, fremd[0], fremd[1]))
        assertEquals(3, anzeige.size)
        assertEquals(1, anzeige.count { it.herausgeber == "DER STANDARD" })
        assertTrue(Rss.nurAnsicht(google))
        assertTrue(Rss.nurAnsicht(Rss.Eintrag("n", "https://news.google.com/rss/articles/nas", 0L, herausgeber = "Nasdaq", quelle = "Google News")))
        assertFalse(Rss.nurAnsicht(Rss.Eintrag("r", "https://news.google.com/rss/articles/reu", 0L, herausgeber = "Reuters", quelle = "Google News")))
    }

    // Gemerkte: ohne Lesemodus und Guardian aelter als 24 Stunden fallen weg
    @Test
    fun gemerktGueltig() {
        val jetzt = System.currentTimeMillis()
        val standard = Rss.Eintrag("s", "https://www.derstandard.at/story/1", 0L, quelle = "derStandard.at")
        val nasdaq = Rss.Eintrag("n", "https://www.nasdaq.com/articles/x", 0L, quelle = "Nasdaq")
        val tagesschau = Rss.Eintrag("t", "https://www.tagesschau.de/x", 0L, quelle = "tagesschau.de")
        val guardian = Rss.Eintrag("g", "https://www.theguardian.com/business/x", 0L, quelle = "The Guardian")
        assertEquals(listOf(tagesschau), Rss.gemerktGueltig(listOf(standard, nasdaq, tagesschau, guardian.copy(gemerktAm = jetzt - 25 * 3_600_000L)), jetzt))
        val frisch = guardian.copy(gemerktAm = jetzt - 3_600_000L)
        assertEquals(listOf(tagesschau, frisch), Rss.gemerktGueltig(listOf(standard, nasdaq, tagesschau, frisch), jetzt))
    }

    // Auftrag: Quellen in ohneAbruf laufen nur ueber den Zwischenspeicher
    @Test
    fun ohneAbruf() {
        val auftrag = NewsRepository.Auftrag(Rss.eingebaut(deutsch = true), true, ohneAbruf = Rss.NUR_ANSICHT)
        assertFalse(auftrag.abrufen(auftrag.quellen.first { it.schluessel == "derstandard" }))
        assertFalse(auftrag.abrufen(auftrag.quellen.first { it.schluessel == "nasdaq-maerkte" }))
        assertTrue(auftrag.abrufen(auftrag.quellen.first { it.schluessel == "tagesschau-boerse" }))
        val alles = NewsRepository.Auftrag(Rss.eingebaut(deutsch = true), true)
        assertTrue(alles.quellen.all { q -> alles.abrufen(q) })
    }

    // Nur echte Antworten kosten Kontingent
    @Test
    fun eodhdGebucht() {
        assertFalse(NachrichtenApis.eodhdGebucht(null))
        assertFalse(NachrichtenApis.eodhdGebucht(MarketApi.Antwort(0, null)))
        assertTrue(NachrichtenApis.eodhdGebucht(MarketApi.Antwort(200, "[]")))
        assertTrue(NachrichtenApis.eodhdGebucht(MarketApi.Antwort(429, "{}")))
    }
}
