package de.troetenrepublik.app.market

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.market.AchtK.Art
import de.troetenrepublik.app.market.AchtK.Rolle
import de.troetenrepublik.app.market.AchtK.VorgangsArt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// Warnzeichen und Uebernahmen aus den 8-K-Meldungen [0.77]: Meldungslisten (data.sec.gov/submissions) und Dokumente
// echter Meldungen, gekuerzt (Super Micro Computer, Boeing, Juniper Networks, Merus).
class Fassung077Ad4AchtKTest {

    // Super Micro Computer: Auszug der Liste mit Pruefer- und Notierungsmeldungen (2024 bis 2025)
    private val smciListe = """{"name":"Super Micro Computer, Inc.","filings":{"recent":{"accessionNumber":["0001375365-25-000007","0001375365-24-000045","0001375365-24-000044","0001375365-24-000042","0001375365-24-000040","0001375365-24-000036","0001375365-24-000033","0001375365-24-000029","0001375365-24-000023"],"filingDate":["2025-02-26","2024-12-06","2024-12-02","2024-11-20","2024-11-18","2024-10-30","2024-09-20","2024-08-19","2024-05-09"],"form":["8-K","8-K","8-K","8-K","8-K","8-K","8-K","8-K","8-K"],"items":["3.01,5.08,8.01,9.01","3.01,9.01","5.02,8.01,9.01","1.01,2.03,3.01,9.01","4.01,8.01,9.01","4.01,9.01","3.01,9.01","5.02,7.01,9.01","3.01,9.01"],"primaryDocument":["smci-20250221.htm","smci-20241206.htm","smci-20241127.htm","smci-20241114.htm","smci-20241118.htm","smci-20241024.htm","smci-20240917.htm","smci-20240815.htm","smci-20240503.htm"]}}}"""

    // Merus: Ankuendigung (8-K 1.01), Angebot (SC TO-T, SC 14D9), Fusionsunterlagen (DEFM14A) und Vollzug (8-K 2.01)
    private val merusListe = """{"name":"Merus N.V.","filings":{"recent":{"accessionNumber":["0001193125-25-335951","0001193125-25-335916","0001193125-25-316841","0001193125-25-316835","0001193125-25-312750","0001193125-25-312721","0001193125-25-276008","0001193125-25-259490","0001193125-25-244299","0001193125-25-244292","0001193125-25-230186","0000947871-25-000868","0001193125-25-223423","0001193125-25-221809","0001193125-25-221801"],"filingDate":["2025-12-30","2025-12-30","2025-12-12","2025-12-12","2025-12-09","2025-12-09","2025-11-12","2025-10-31","2025-10-21","2025-10-21","2025-10-03","2025-09-29","2025-09-29","2025-09-29","2025-09-29"],"form":["8-K","SC TO-T/A","8-K","SC TO-T/A","SC TO-T/A","8-K","DEFM14A","SC TO-T/A","SC 14D9","SC TO-T","SC14D9C","SC TO-C","SC14D9C","SC14D9C","8-K"],"items":["2.01,3.01,3.03,5.02,9.01","","2.01,3.03,5.01,5.02,9.01","","","5.07","","","","","","","","","1.01,7.01,9.01"],"primaryDocument":["d80117d8k.htm","d71706dsctota.htm","d29730d8k.htm","d887380dsctota.htm","d82200dsctota.htm","d109012d8k.htm","d54485ddefm14a.htm","d78292dsctota.htm","d97785dsc14d9.htm","d15006dsctot.htm","d23483dsc14d9c.htm","ss5386579_sctoc.htm","d945316dsc14d9c.htm","d941875dsc14d9c.htm","d94293d8k.htm"]}}}"""

    private fun html(vararg absaetze: String) = "<html><head><title>8-K</title></head><body>" + absaetze.joinToString("") { "<p>$it</p>" } + "</body></html>"

    // Super Micro, 8-K vom 2.12.2024: Suche nach einem neuen CFO, der amtierende bleibt im Amt
    private val smci502Cfo = html(
        "<b>Item 5.02</b> Departure of Directors or Certain Officers; Election of Directors; Appointment Certain Officers; Compensatory Arrangements of Certain Officers",
        "On December 2, 2024, Super Micro Computer, Inc. (the &#8220;Company&#8221;) announced that the Special Committee (the &#8220;Special Committee&#8221;) of the Board of Directors of the Company (the &#8220;Board&#8221;) had completed its investigation and made several recommendations to the Board, including that the Company recruit a new Chief Financial Officer and appoint a Chief Accounting Officer. Effective November 27, 2024, the Board has adopted all the Special Committee&#8217;s recommendations, including that it would commence a search for a new Chief Financial Officer immediately. The Company&#8217;s current Chief Financial Officer, David Weigand, will continue to serve in that position until his successor has been appointed. The Company also announced that Kenneth Cheung has been appointed as the Company&#8217;s Senior Vice President, Chief Accounting Officer, effective as of November 27, 2024 and will serve as the Company&#8217;s principal accounting officer. Prior to Mr. Cheung&#8217;s appointment, Mr. Weigand served as both the Company&#8217;s principal financial officer and principal accounting officer.",
        "<b>Item 9.01</b> Financial Statements and Exhibits",
        "SIGNATURE Pursuant to the requirements of the Securities Exchange Act of 1934, the registrant has duly caused this report to be signed on its behalf by the undersigned hereunto duly authorized. By: /s/ Charles Liang President, Chief Executive Officer and Chairman of the Board",
    )

    // Super Micro, 8-K vom 15.3.2024: Rücktritt eines Verwaltungsrats; der CEO steht nur in der Unterschrift
    private val smci502Verwaltungsrat = html(
        "<b>Item 5.02</b> Departure of Directors or Certain Officers; Election of Directors; Appointment of Certain Officers; Compensatory Arrangements of Certain Officers",
        "(a) Departure of Director On March 11, 2024, Mr. Shiu Leung (Fred) Chan resigned as a director of Super Micro Computer, Inc. (the &#8220;Company&#8221;) in order to focus on family owned business projects. Mr. Chan served as chairman of the Nominating and Corporate Governance Committee and a member the Audit Committee. His resignation from the Board was not because of any disagreement with the Company.",
        "<b>Item 9.01</b> Financial Statements and Exhibits",
        "SIGNATURE By: /s/ Charles Liang President, Chief Executive Officer and Chairman of the Board",
    )

    // Boeing, 8-K vom 25.3.2024: der CEO tritt zurueck
    private val boeing502Ceo = html(
        "<b>Item 5.02.</b> Departure of Directors or Certain Officers; Election of Directors; Appointment of Certain Officers; Compensatory Arrangements of Certain Officers.",
        "On March 25, 2024, The Boeing Company (the &#8220;Company&#8221;) announced several decisions that occurred on March 24, 2024, involving the Company&#8217;s management and Board of Directors (the &#8220;Board&#8221;). First, David L. Calhoun decided to step down as President and Chief Executive Officer at the end of 2024. Second, Lawrence W. Kellner, independent Chair of the Board, decided not to stand for re-election at the Company&#8217;s 2024 Annual Meeting of Shareholders. In connection with this decision, the Board approved a reduction in its size, effective as of the 2024 Annual Meeting of Shareholders, and elected Steven M. Mollenkopf to replace Mr. Kellner as independent Chair of the Board, effective March 24, 2024. Third, the Board elected Stephanie F. Pope, Executive Vice President and Chief Operating Officer, to replace Stanley A. Deal as President and Chief Executive Officer, Boeing Commercial Airplanes, effective March 25, 2025.",
        "<b>Item 7.01.</b> Regulation FD Disclosure.",
    )

    // Boeing, 8-K vom 20.9.2024: der Chef einer Sparte geht, nicht der CEO der Firma
    private val boeing502Sparte = html(
        "<b>Item 5.02.</b> Departure of Directors or Certain Officers; Election of Directors; Appointment of Certain Officers; Compensatory Arrangements of Certain Officers.",
        "Theodore Colbert III ceased to serve as an elected officer of The Boeing Company and as President and Chief Executive Officer of Boeing Defense, Space &amp; Security, effective September 20, 2024.",
        "SIGNATURE Pursuant to the requirements of the Securities Exchange Act of 1934, as amended, the registrant has duly caused this report to be signed on its behalf by the undersigned.",
    )

    // Juniper Networks, 8-K vom 10.1.2024: Fusionsvertrag mit HPE
    private val juniper101 = html(
        "<b>Item 1.01</b> Entry into a Material Definitive Agreement.",
        "<i>Agreement and Plan of Merger</i> On January&#160;9, 2024, Juniper Networks, Inc. (&#8220;Juniper&#8221; or the &#8220;Company&#8221;), entered into an Agreement and Plan of Merger (the &#8220;Merger Agreement&#8221;) by and among the Company, Hewlett Packard Enterprise Company, a Delaware corporation (&#8220;HPE&#8221; or &#8220;Parent&#8221;), and Jasmine Acquisition Sub, Inc., a Delaware corporation and wholly owned subsidiary of Parent (&#8220;Merger Sub&#8221;), pursuant to which Merger Sub will merge with and into the Company (the &#8220; Merger &#8221;), with the Company surviving the Merger as a wholly owned subsidiary of Parent. As a result of the Merger, each share of common stock, par value $0.00001 per share, of the Company (&#8220;Company Common Stock&#8221;) outstanding immediately prior to the effective time of the Merger will, at the Effective Time, automatically be converted into the right to receive $40.00 in cash, without interest and subject to applicable withholding taxes (the &#8220;Merger Consideration&#8221;). The aggregate equity value of the Company Common Stock acquired by Parent will be approximately $14 billion.",
        "<b>Item 7.01</b> Regulation FD Disclosure.",
    )

    // Merus, 8-K vom 29.9.2025: Transaktionsvertrag mit Genmab, Angebot zu 97 Dollar je Aktie
    private val merus101 = html(
        "<b>Item 1.01</b> Entry into a Material Definitive Agreement.",
        "<i>Transaction Agreement</i> On September 29, 2025, Merus N.V. (&#8220; Merus &#8221;), a Dutch public limited liability company ( <i>naamloze vennootschap</i> ) organized under the Laws of The Netherlands, entered into a Transaction Agreement (the &#8220; Transaction Agreement &#8221;) with Genmab A/S, a public limited liability company ( <i>Aktieselskab</i> ) organized under the Laws of Denmark (&#8220; Parent &#8221;), and Genmab Holding II B.V., a private limited liability company ( <i>besloten vennootschap met beperkte aansprakelijkheid</i> ) organized under the laws of The Netherlands and a wholly owned subsidiary of Parent (&#8220; Purchaser &#8221;).",
        "<i>Initial Offer, Duration and Expiration Time</i> Pursuant to the Transaction Agreement, Purchaser will commence a tender offer (the &#8220; Offer &#8221;) to purchase any and all of the issued and outstanding common shares, nominal value EUR 0.09 per share, in the capital of Merus (the &#8220; Common Shares &#8221;) in exchange for $97.00 per Share, in cash (the &#8220; Offer Consideration &#8221;), as it may be amended from time to time as permitted by the Transaction Agreement, without interest and subject to any applicable tax withholding.",
        "<b>Item 7.01</b> Regulation FD Disclosure.",
    )

    // Merus, 8-K vom 12.12.2025: Angebot abgelaufen, Aktien bezahlt
    private val merus201 = html(
        "<b>Item 2.01</b> Completion of Acquisition or Disposition of Assets.",
        "As previously disclosed by Merus N.V. (the &#8220;Company&#8221;), on September 29, 2025, the Company entered into a Transaction Agreement with Genmab A/S, a public limited liability company ( <i>Aktieselskab</i> ) organized under the laws of Denmark (&#8220;Parent&#8221;), and Genmab Holding II B.V., a private limited liability company and a wholly owned subsidiary of Parent (&#8220;Purchaser&#8221;), to purchase all of the issued and outstanding common shares of the Company (the &#8220;Common Shares&#8221;), in exchange for $97.00 per Common Share in cash (the &#8220;Offer Consideration&#8221;), upon the terms and subject to the conditions set forth in the offer to purchase, dated October 21, 2025. The Offer expired at 5:00 p.m., New York City time, on December 11, 2025, at which time all conditions to the Offer were satisfied or waived.",
        "<b>Item 3.03</b> Material Modification to Rights of Security Holders.",
    )

    // Super Micro, 8-K vom 21.6.2024: Rechenzentrums-Vertrag (kein Kauf, keine Fusion)
    private val smci101Vertrag = html(
        "<b>Item 1.01</b> Entry into a Material Definitive Agreement",
        "On June 14, 2024, Super Micro Computer, Inc. (the &#8220;Company&#8221;) entered into a Master Colocation Services Agreement (the &#8220;MCSA&#8221;) with 4701 Santa Fe, LLC (the &#8220;Supplier&#8221;) to lease certain data center space owned by the Supplier. Pursuant to the Service Order, the Company has agreed to lease 21 MW of a multi-tenanted facility from the Supplier for a term of 10 years. The Company&#8217;s aggregate financial obligation for the term of the Service Order is estimated to be $600.0 million.",
        "<b>Item 2.03</b> Creation of a Direct Financial Obligation",
    )

    private val heuteSmci = LocalDate.of(2025, 3, 1)
    private val heuteMerus = LocalDate.of(2026, 1, 15)

    // ------------------------------------------------------------ Liste

    @Test
    fun listeWirdGelesen() {
        val l = AchtK.meldungen(smciListe)
        assertEquals(9, l.size)
        assertEquals("Super Micro Computer, Inc.", AchtK.name(smciListe))
        assertEquals("0001375365-24-000040", l[4].accession)
        assertEquals(setOf("4.01", "8.01", "9.01"), l[4].items)
        assertEquals("2024-11-18", l[4].eingang)
        assertEquals("smci-20241118.htm", l[4].datei)
        assertTrue(AchtK.meldungen("kein json").isEmpty())
        assertTrue(AchtK.meldungen("{}").isEmpty())
    }

    @Test
    fun warnzeichenAusDerListe() {
        val l = AchtK.meldungen(smciListe)
        // 5.02 vom 2.12.2024 ist gelesen (kein CEO oder CFO), 5.02 vom 19.8.2024 nicht gelesen und fehlt
        val rollen = mapOf("0001375365-24-000044" to Rolle.KEINE)
        val w = AchtK.warnzeichen(l, heuteSmci, rollen)
        assertEquals(7, w.size)
        assertEquals(listOf(Art.DELISTING, Art.DELISTING, Art.DELISTING, Art.PRUEFER, Art.PRUEFER, Art.DELISTING, Art.DELISTING), w.map { it.art })
        assertEquals("2025-02-26", w.first().tag)
        assertEquals(5, w.count { it.art.schwer })
        assertEquals(2, w.count { it.art == Art.PRUEFER })
        assertTrue(w.none { it.art == Art.FUEHRUNG })
    }

    @Test
    fun unlesbaresDokumentZeigtWechselOhneRolle() {
        val l = AchtK.meldungen(smciListe)
        // Nicht lesbares Dokument: Wechsel in der Fuehrung ohne Rolle
        val w = AchtK.warnzeichen(l, heuteSmci, mapOf("0001375365-24-000044" to Rolle.UNBEKANNT, "0001375365-24-000029" to Rolle.CFO))
        val f = w.filter { it.art == Art.FUEHRUNG }
        assertEquals(2, f.size)
        assertEquals(Rolle.UNBEKANNT, f[0].rolle)
        assertEquals(Rolle.CFO, f[1].rolle)
    }

    @Test
    fun nurDieLetzten24Monate() {
        val l = AchtK.meldungen(smciListe)
        val spaeter = AchtK.warnzeichen(l, LocalDate.of(2026, 6, 1), emptyMap())
        // Fenster ab 1.6.2024: Notierungsmeldung vom 9.5.2024 faellt weg
        assertEquals(6, spaeter.size)
        assertTrue(spaeter.none { it.tag < "2024-06-01" })
    }

    @Test
    fun kandidatenFuerDieFuehrungNeuesteZuerst() {
        val l = AchtK.meldungen(smciListe)
        val k = AchtK.fuehrungsKandidaten(l, heuteSmci, emptySet(), 5)
        assertEquals(listOf("0001375365-24-000044", "0001375365-24-000029"), k.map { it.accession })
        assertEquals(1, AchtK.fuehrungsKandidaten(l, heuteSmci, setOf("0001375365-24-000044"), 5).size)
        assertEquals(1, AchtK.fuehrungsKandidaten(l, heuteSmci, emptySet(), 1).size)
    }

    @Test
    fun vollzugEinerUebernahmeIstKeinWarnzeichen() {
        val l = AchtK.meldungen(merusListe)
        // 8-K vom 30.12.2025 mit 2.01, 3.01 und 5.02 meldet das Ende der Notierung als Folge der Fusion
        assertTrue(AchtK.warnzeichen(l, heuteMerus, mapOf("0001193125-25-335951" to Rolle.CEO)).isEmpty())
    }

    // ------------------------------------------------------------ 5.02 lesen

    @Test
    fun rolleCeoAusDemText() {
        assertEquals(Rolle.CEO, AchtK.rolleLesen(boeing502Ceo, "The Boeing Company"))
    }

    @Test
    fun suchenNachNeuemCfoIstKeinAbgang() {
        assertEquals(Rolle.KEINE, AchtK.rolleLesen(smci502Cfo, "Super Micro Computer, Inc."))
    }

    @Test
    fun verwaltungsratOhneCeoCfo() {
        assertEquals(Rolle.KEINE, AchtK.rolleLesen(smci502Verwaltungsrat, "Super Micro Computer, Inc."))
    }

    @Test
    fun chefEinerSparteZaehltNicht() {
        assertEquals(Rolle.KEINE, AchtK.rolleLesen(boeing502Sparte, "The Boeing Company"))
    }

    @Test
    fun cfoUndBeidesUndAbfindungsklausel() {
        val cfo = html("<b>Item 5.02</b> Departure of Directors", "On May 1, 2025, Jane Roe notified the Company of her decision to resign as Chief Financial Officer of the Company, effective June 30, 2025.", "<b>Item 9.01</b>")
        assertEquals(Rolle.CFO, AchtK.rolleLesen(cfo, "Example Corp"))
        val beide = html("<b>Item 5.02</b> Departure", "The Company announced that John Doe, Chief Executive Officer, and Jane Roe, Chief Financial Officer, will step down.", "<b>Item 9.01</b>")
        assertEquals(Rolle.CEO_CFO, AchtK.rolleLesen(beide, "Example Corp"))
        val klausel = html("<b>Item 5.02</b> Compensatory Arrangements", "If the Chief Executive Officer resigns for good reason, he will receive severance equal to twelve months of base salary.", "<b>Item 9.01</b>")
        assertEquals(Rolle.KEINE, AchtK.rolleLesen(klausel, "Example Corp"))
    }

    @Test
    fun rolleImNachbarsatzIstUnsicher() {
        val d = html("<b>Item 5.02</b> Departure", "On May 1, 2025, John Doe resigned from his position with the Company. Mr. Doe has served as Chief Financial Officer since 2019.", "<b>Item 9.01</b>")
        assertEquals(Rolle.UNBEKANNT, AchtK.rolleLesen(d, "Example Corp"))
        assertEquals(Rolle.UNBEKANNT, AchtK.rolleLesen("", "Example Corp"))
    }

    // ------------------------------------------------------------ Uebernahmen

    @Test
    fun fusionsvertragMitGegenseiteUndPreis() {
        val d = AchtK.dealLesen(juniper101, "1.01", "Juniper Networks, Inc.")
        assertTrue(d.vereinbarung)
        assertFalse(d.abschluss)
        assertEquals("Hewlett Packard Enterprise Company", d.gegenseite)
        assertEquals(40.0, d.preis!!, 0.0)
    }

    @Test
    fun angebotMitGegenseiteUndPreis() {
        val d = AchtK.dealLesen(merus101, "1.01", "Merus N.V.")
        assertTrue(d.vereinbarung)
        assertEquals("Genmab A/S", d.gegenseite)
        assertEquals(97.0, d.preis!!, 0.0)
        val v = AchtK.dealLesen(merus201, "2.01", "Merus N.V.")
        assertTrue(v.abschluss)
        assertFalse(v.vereinbarung)
        assertEquals("Genmab A/S", v.gegenseite)
        assertEquals(97.0, v.preis!!, 0.0)
    }

    @Test
    fun vertragOhneKaufOderFusionIstKeinGeschaeft() {
        val d = AchtK.dealLesen(smci101Vertrag, "1.01", "Super Micro Computer, Inc.")
        assertFalse(d.vereinbarung)
        assertFalse(d.abschluss)
        assertNull(d.gegenseite)
        assertNull(d.preis)
    }

    @Test
    fun preisNurWennEindeutig() {
        assertEquals(12.5, AchtK.preisLesen("each share will be converted into the right to receive \$12.50 in cash")!!, 0.0)
        assertNull(AchtK.preisLesen("the right to receive \$12.50 in cash and one contingent value right"))
        assertNull(AchtK.preisLesen("in exchange for \$10.00 per share or, at the holder's election, for \$11.00 per share"))
        assertNull(AchtK.preisLesen("each share will be exchanged for 0.5 shares of Parent common stock"))
        assertNull(AchtK.preisLesen("par value \$0.00001 per share"))
    }

    @Test
    fun gegenseiteNurBeiEinerPartei() {
        assertNull(AchtK.gegenseiteLesen("entered into an agreement with Alpha Corp, a Delaware corporation (\"Alpha\"), to lease space", "Beta Inc"))
        // Der Emittent selbst ist keine Gegenseite
        assertNull(AchtK.gegenseiteLesen("Beta Inc, a Delaware corporation (\"Parent\"), and Gamma Sub, Inc., a Delaware corporation (\"Merger Sub\")", "Beta Inc."))
        assertEquals("Delta Holdings Ltd", AchtK.gegenseiteLesen("with Delta Holdings Ltd, a company organized in Bermuda (\"Buyer\"), for the sale of the unit", "Beta Inc."))
    }

    @Test
    fun kandidatenZuerstVollzugDannVereinbarungNaheAnFormularen() {
        val l = AchtK.meldungen(merusListe)
        val k = AchtK.dealKandidaten(l, heuteMerus, emptySet(), 5)
        assertEquals(listOf("0001193125-25-335951", "0001193125-25-316841", "0001193125-25-221801"), k.map { it.accession })
        assertEquals(1, AchtK.dealKandidaten(l, heuteMerus, emptySet(), 1).size)
        assertEquals(2, AchtK.dealKandidaten(l, heuteMerus, setOf("0001193125-25-335951"), 5).size)
    }

    @Test
    fun kreditvertragOhneNaheFormulareBleibtUngelesen() {
        val l = listOf(
            AchtK.Meldung("a1", "8-K", "2025-06-01", setOf("1.01", "2.03", "9.01"), "kredit.htm"),
            AchtK.Meldung("a2", "8-K", "2025-06-10", setOf("1.01", "9.01"), "vertrag.htm"),
        )
        assertEquals(listOf("a2"), AchtK.dealKandidaten(l, LocalDate.of(2025, 7, 1), emptySet(), 5).map { it.accession })
    }

    @Test
    fun vorgangAusAnkuendigungAngebotUndVollzug() {
        val l = AchtK.meldungen(merusListe)
        val docs = mapOf(
            "0001193125-25-221801" to AchtK.dealLesen(merus101, "1.01", "Merus N.V."),
            "0001193125-25-316841" to AchtK.dealLesen(merus201, "2.01", "Merus N.V."),
        )
        val v = AchtK.vorgaenge(l, docs, heuteMerus)
        assertEquals(listOf(VorgangsArt.ABGESCHLOSSEN, VorgangsArt.ANGEBOT, VorgangsArt.ANGEKUENDIGT), v.map { it.art })
        assertEquals(listOf("2025-12-12", "2025-10-21", "2025-09-29"), v.map { it.tag })
        assertEquals(setOf(0), v.map { it.gruppe }.toSet())
        assertTrue(v.all { it.gegenseite == "Genmab A/S" && it.preis == 97.0 })
        // Der erste Vollzug zaehlt, nicht der zweite vom 30.12.2025; ohne gelesenes Dokument gibt es keinen
        assertEquals("0001193125-25-316841", v.first().accession)
    }

    @Test
    fun ohneGeleseneDokumenteBleibenFormulareUndEinVorgang() {
        val l = AchtK.meldungen(merusListe)
        val v = AchtK.vorgaenge(l, emptyMap(), heuteMerus)
        // Angebot aus SC TO-T und SC 14D9; das Fusionsformular vom 12.11. folgt dem Angebot und kuendigt nichts an
        assertEquals(listOf(VorgangsArt.ANGEBOT), v.map { it.art })
        assertEquals("2025-10-21", v.single().tag)
        assertTrue(v.all { it.gegenseite == null && it.preis == null })
    }

    @Test
    fun getrennteGeschaefteSindGetrennteVorgaenge() {
        val l = listOf(
            AchtK.Meldung("n1", "425", "2025-03-03", emptySet(), "a.htm"),
            AchtK.Meldung("n2", "425", "2025-03-20", emptySet(), "b.htm"),
            AchtK.Meldung("n3", "SC TO-T", "2025-11-01", emptySet(), "c.htm"),
        )
        val v = AchtK.vorgaenge(l, emptyMap(), LocalDate.of(2026, 1, 1))
        assertEquals(setOf(0, 1), v.map { it.gruppe }.toSet())
        assertEquals(listOf(VorgangsArt.ANGEBOT, VorgangsArt.ANGEKUENDIGT), v.map { it.art })
        assertEquals("2025-03-03", v.last().tag)
        // Zu alt: 25 Monate vorher
        assertTrue(AchtK.vorgaenge(l, emptyMap(), LocalDate.of(2027, 12, 1)).isEmpty())
    }

    // ------------------------------------------------------------ Insider und Kurs

    private fun verkauf(p: String, d: String, wert: Double, kauf: Boolean = false, k: String = "SMCI") =
        SecInsider.Geschaeft(a = "$p$d", k = k, e = "", p = p, kauf = kauf, d = d, wert = wert)

    @Test
    fun insiderverkaeufeInDenSechzigTagenDavor() {
        val g = listOf(
            verkauf("Alice Roe", "2024-10-15", 1_000_000.0),
            verkauf("Alice Roe", "2024-10-20", 500_000.0),
            verkauf("Bob Doe", "2024-09-20", 2_500_000.0),
            verkauf("Carl Poe", "2024-08-01", 9_000_000.0),
            verkauf("Dora Zoe", "2024-10-10", 700_000.0, kauf = true),
            verkauf("Emil Joe", "2024-10-12", 800_000.0, k = "AAPL"),
            verkauf("Fred Moe", "2024-11-05", 600_000.0),
        )
        val d = AchtK.insiderDavor(g, "SMCI", "2024-10-30")!!
        assertEquals(2, d.verkaeufer)
        assertEquals(4_000_000.0, d.summe, 0.0)
        assertNull(AchtK.insiderDavor(g, "SMCI", "2023-01-10"))
        assertNull(AchtK.insiderDavor(emptyList(), "SMCI", "2024-10-30"))
    }

    private fun kerzen(vararg schluss: Double): List<Candle> {
        val start = LocalDate.of(2025, 9, 22).toEpochDay()
        return schluss.mapIndexed { i, c -> Candle((start + i) * 86_400_000L + 48_600_000L, c, c, c, c) }
    }

    @Test
    fun kursreaktionAmMeldetagUndNachFuenfHandelstagen() {
        // 22.9. bis 4.10.: Meldung am 29.9. (Index 7), Schluss davor 100, fuenf Handelstage danach Index 12
        val k = kerzen(100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 110.0, 112.0, 113.0, 111.0, 114.0, 115.0)
        val r = AchtK.reaktion(k, "2025-09-29")!!
        assertEquals(10.0, r.tag0, 1e-9)
        assertEquals(15.0, r.tag5!!, 1e-9)
    }

    @Test
    fun kursreaktionOhneGenugKurseOderVortag() {
        val k = kerzen(100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 110.0, 112.0)
        val r = AchtK.reaktion(k, "2025-09-29")!!
        assertEquals(10.0, r.tag0, 1e-9)
        assertNull(r.tag5)
        // Erster Kurs hat keinen Vortag, Datum nach den Kursen
        assertNull(AchtK.reaktion(k, "2025-09-22"))
        assertNull(AchtK.reaktion(k, "2025-12-01"))
        assertNull(AchtK.reaktion(emptyList(), "2025-09-29"))
        assertNotNull(AchtK.reaktion(k, "2025-09-28"))
    }
}
