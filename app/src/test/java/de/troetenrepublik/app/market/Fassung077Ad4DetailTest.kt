package de.troetenrepublik.app.market

import de.troetenrepublik.app.market.AchtK.Art
import de.troetenrepublik.app.market.AchtK.Detail
import de.troetenrepublik.app.market.AchtK.Ereignis
import de.troetenrepublik.app.market.AchtK.Grund
import de.troetenrepublik.app.market.AchtK.Rolle
import de.troetenrepublik.app.market.AchtK.Unterart
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Angaben aus dem Text der 8-K [0.77]: Notierungsmeldungen (Super Micro Computer, Femasys, Immunic, Vince Holding),
// Pruefer, Korrektur, Fuehrung; gekuerzte Ausschnitte echter Meldungen. Insolvenz und Abschreibung nutzen einen
// kurzen eigenen Beispieltext.
class Fassung077Ad4DetailTest {

    private fun html(vararg absaetze: String) = "<html><head><title>8-K</title></head><body>" + absaetze.joinToString("") { "<p>$it</p>" } + "</body></html>"

    private val titel301 = "<b>Item 3.01</b> Notice of Delisting or Failure to Satisfy a Continued Listing Rule or Standard; Transfer of Listing"

    // Super Micro, 8-K vom 20.11.2024
    private val smci301Regel = html(
        titel301,
        "On November 19, 2024, the Company received a notification letter (the &#8220;Letter&#8221;) from the Listing Qualifications Department of the Nasdaq Stock Market LLC (&#8220;Nasdaq&#8221;) indicating that the Company is not in compliance with Nasdaq Listing Rule 5250(c)(1), as a result of the Company&#8217;s delay in filing its Quarterly Report on Form 10-Q for the period ended September 30, 2024 (the &#8220;Q1 2025 Form 10-Q&#8221;) and its continued delay in filing its Annual Report on Form 10-K for the period ending June 30, 2024 (the &#8220;2024 Form 10-K&#8221;). The 2024 Form 10-K was due on August 29, 2024 and the Q1 2025 Form 10-Q was due on November 12, 2024. The Company submitted a compliance plan to Nasdaq on November 18, 2024 (the &#8220;Plan&#8221;). The Letter has no immediate effect on the listing or trading of the Company&#8217;s common stock on the NASDAQ Global Select Market.",
        "<b>Item 9.01</b> Financial Statements and Exhibits",
    )

    // Super Micro, 8-K vom 6.12.2024: Ausnahme bis 25.2.2025
    private val smci301Frist = html(
        titel301,
        "On December 6, 2024, Super Micro Computer, Inc. (the &#8220;Company&#8221;) received a letter (the &#8220;Letter&#8221;) from the Nasdaq Stock Market (&#8220;Nasdaq&#8221;) informing the Company that Nasdaq has granted the Company&#8217;s request for an exception to Nasdaq&#8217;s Listing Rule 5250(c)(1) through February 25, 2025. The exception gives the Company until February 25, 2025 to file its Annual Report on Form 10-K for the fiscal year ended June 30, 2024, its Quarterly Report on Form 10-Q for the period ended September 30, 2024 and any other required filings. The Company&#8217;s common stock will remain listed on the Nasdaq Global Select Market during the exception period. In the event the Company does not file all required reports by February 25, 2025, Nasdaq will notify the Company that its common stock will be delisted. If that were to occur, the Company would have the right to appeal such determination to a Nasdaq Hearing Panel.",
        "<b>Item 9.01</b> Financial Statements and Exhibits",
    )

    // Super Micro, 8-K vom 26.2.2025: wieder regelkonform
    private val smci301Entwarnung = html(
        titel301 + ".",
        "On February 25, 2025, the Company received a notification letter from Nasdaq indicating that the Company now complies with Nasdaq listing rule 5250(c)(1) which requires timely filing of reports with the U.S. Securities and Exchange Commission (the &#8220; SEC &#8221;) and the matter is now closed. Prior to its filing on February 25, 2025, the Company had received a notification letter from Nasdaq on February 21, 2025, stating that the Company was not in compliance with Nasdaq listing rule 5250(c)(1) as a result of the Company&#8217;s delay in filing its Q2 FY2025 Form 10-Q and its continued delay in filing its FY2024 Form 10-K and Q1 FY2025 Form 10-Q.",
        "<b>Item 5.08</b> Shareholder Director Nominations",
    )

    // Femasys, 8-K vom 18.7.2025: Mindestkurs, 180 Tage
    private val femasys301 = html(
        titel301 + ".",
        "On July 16, 2025, Femasys Inc. (the &#8220;Company&#8221;) received a notice from The Nasdaq Stock Market (&#8220;Nasdaq&#8221;) that the Company is not in compliance with Nasdaq&#8217;s Listing Rule 5550(a)(2), as the minimum bid price of the Company&#8217;s common stock has been below $1.00 per share for 30 consecutive business days (the &#8220;Minimum Bid Price Requirement&#8221;). The notification of noncompliance has no immediate effect on the listing or trading of the Company&#8217;s common stock on The Nasdaq Capital Market. The Company has 180 calendar days, or until January 12, 2026, to regain compliance with the Minimum Bid Price Requirement. In the event the Company does not regain compliance with the Minimum Bid Price Requirement by January 12, 2026, the Company may be eligible for an additional 180-calendar day compliance period.",
        "<b>Item 9.01</b> Financial Statements and Exhibits",
    )

    // Immunic, 8-K vom 6.1.2026: Wechsel in das Capital Market
    private val immunic301 = html(
        titel301 + ".",
        "On January 5, 2026, Immunic, Inc. (the &#8220;Company&#8221;) received approval (the &#8220;Approval&#8221;) from the Nasdaq Listing Qualifications Department of The Nasdaq Stock Market LLC (&#8220;Nasdaq&#8221;) that the Company&#8217;s application to transfer the listing of its common stock from the Nasdaq Global Select Market to the Nasdaq Capital Market has been approved. The common stock will be transferred to the Nasdaq Capital Market at the opening of business on January 7, 2026. As previously disclosed, on June 27, 2025, the Company received a letter from Nasdaq indicating that the Company was not in compliance with Nasdaq Listing Rule 5450(a)(1) because the closing bid price per share had closed below $1.00 for the previous 30 consecutive business days. The Company had not regained compliance with the Bid Price Rule by December 24, 2025.",
        "<b>Item 7.01</b> Regulation FD Disclosure",
    )

    // Vince Holding, 8-K vom 10.10.2025: freiwilliger Wechsel von der NYSE an die Nasdaq
    private val vince301 = html(
        titel301 + ".",
        "On October 10, 2025, Vince Holding Corp. (the &#8220;Company&#8221;) provided notice to the New York Stock Exchange (&#8220;NYSE&#8221;) that it intends to have its shares of common stock voluntarily delisted from the NYSE after the close of trading on or around October 20, 2025, and it intends to voluntarily transfer the listing of its Common Stock to The Nasdaq Stock Market LLC (&#8220;Nasdaq&#8221;), effective as of the opening of trading on or around October 21, 2025.",
        "<b>Item 7.01</b> Regulation FD Disclosure",
    )

    @Test
    fun regelNichtErfuelltMitBerichten() {
        val d = AchtKDetail.lesen(Art.DELISTING, smci301Regel, "Super Micro Computer, Inc.")!!
        assertEquals(Unterart.REGEL, d.unterart)
        assertEquals(Grund.BERICHT, d.grund)
        assertEquals(listOf("10-Q;09/2024", "10-K;2024"), d.berichte)
        assertEquals("", d.frist)
    }

    @Test
    fun delistingAngedrohtMitFrist() {
        val d = AchtKDetail.lesen(Art.DELISTING, smci301Frist, "Super Micro Computer, Inc.")!!
        assertEquals(Unterart.ANGEDROHT, d.unterart)
        assertEquals(Grund.BERICHT, d.grund)
        assertEquals("2025-02-25", d.frist)
        assertEquals(listOf("10-K;2024", "10-Q;09/2024"), d.berichte)
    }

    @Test
    fun wiederRegelkonformIstEntwarnung() {
        val d = AchtKDetail.lesen(Art.DELISTING, smci301Entwarnung, "Super Micro Computer, Inc.")!!
        assertEquals(Unterart.ENTWARNUNG, d.unterart)
        assertEquals(Grund.BERICHT, d.grund)
        assertEquals("", d.frist)
    }

    @Test
    fun mindestkursMitFrist() {
        val d = AchtKDetail.lesen(Art.DELISTING, femasys301, "Femasys Inc")!!
        assertEquals(Unterart.ANGEDROHT, d.unterart)
        assertEquals(Grund.KURS, d.grund)
        assertEquals("2026-01-12", d.frist)
        assertTrue(d.berichte.isEmpty())
    }

    @Test
    fun boersenwechselIstKeinWarnzeichen() {
        val a = AchtKDetail.lesen(Art.DELISTING, immunic301, "Immunic, Inc.")!!
        assertEquals(Unterart.WECHSEL, a.unterart)
        assertNull(a.grund)
        val b = AchtKDetail.lesen(Art.DELISTING, vince301, "Vince Holding Corp.")!!
        assertEquals(Unterart.WECHSEL, b.unterart)
    }

    @Test
    fun beschlossenOhneBedingung() {
        val d = AchtKDetail.notierung("On May 2, 2025, the Company received a letter from Nasdaq stating that the staff has determined to delist the Company's securities because of the minimum bid price. Trading will be suspended at the opening of business on May 12, 2025.")
        assertEquals(Unterart.BESCHLOSSEN, d.unterart)
        assertEquals(Grund.KURS, d.grund)
        // Bedingte Saetze zaehlen nicht als Beschluss
        assertEquals(Unterart.ANGEDROHT, AchtKDetail.notierung("Nasdaq notified the Company about the stockholders' equity requirement. If the Company fails to regain compliance, its stock will be delisted.").unterart)
    }

    @Test
    fun weitereGruende() {
        assertEquals(Grund.EIGENKAPITAL, AchtKDetail.notierung("The Company is not in compliance with Listing Rule 5550(b)(1) because its stockholders' equity is below the required $2.5 million. The Company has 45 calendar days to submit a plan.").grund)
        assertEquals("45d", AchtKDetail.notierung("The Company is not in compliance with Listing Rule 5550(b)(1) because its stockholders' equity is below the required $2.5 million. The Company has 45 calendar days to submit a plan.").frist)
        assertEquals(Grund.AUSSCHUSS, AchtKDetail.notierung("The Company no longer complies with Listing Rule 5605(c)(2) because its audit committee has fewer than three independent members.").grund)
        assertEquals(Grund.VERSAMMLUNG, AchtKDetail.notierung("The Company did not hold an annual meeting of stockholders by December 31 as required by Listing Rule 5620(a).").grund)
        assertNull(AchtKDetail.notierung("The Company received a letter from Nasdaq regarding a listing matter.").grund)
    }

    // ------------------------------------------------------------ Zaehlen

    private fun w(art: Art, d: Detail?) = AchtK.Warnzeichen(art, "2025-01-01", Rolle.KEINE, "a", "d.htm", d)

    @Test
    fun wechselUndEntwarnungZaehlenNicht() {
        val liste = listOf(
            w(Art.DELISTING, Detail(unterart = Unterart.ANGEDROHT)), w(Art.DELISTING, Detail(unterart = Unterart.WECHSEL)),
            w(Art.DELISTING, Detail(unterart = Unterart.ENTWARNUNG)), w(Art.DELISTING, null), w(Art.PRUEFER, null), w(Art.KORREKTUR, null),
        )
        assertEquals(4, AchtK.anzahl(liste))
        assertEquals(3, AchtK.schwereAnzahl(liste))
        assertFalse(AchtK.zaehlt(liste[1]))
        assertTrue(AchtK.zaehlt(liste[3]))
    }

    @Test
    fun angabenBleibenInDerDateiLesbar() {
        val d = Detail(unterart = Unterart.ANGEDROHT, grund = Grund.BERICHT, berichte = listOf("10-K;2024"), frist = "2025-02-25", ereignis = Ereignis.RUECKTRITT, nachfolge = true, betrag = 4.2e6)
        val j = Json { ignoreUnknownKeys = true }
        assertEquals(d, j.decodeFromString(Detail.serializer(), j.encodeToString(Detail.serializer(), d)))
    }

    // ------------------------------------------------------------ Pruefer, Korrektur, Fuehrung

    // Super Micro, 8-K vom 30.10.2024: Ernst & Young tritt zurueck
    private val smci401Ruecktritt = html(
        "<b>Item 4.01</b> Changes to Registrant&#8217;s Certifying Accountant",
        "On October 24, 2024, Ernst &amp; Young LLP (&#8220;EY&#8221;) sent the members of the Audit Committee a letter of resignation as the Company&#8217;s registered public accounting firm (the &#8220;Resignation Letter&#8221;). The Chair of the Audit Committee discussed with EY the reasons for EY&#8217;s resignation. The Company has begun the process of identifying a successor independent registered public accounting firm. In late July 2024, EY communicated to the Audit Committee concerns about several matters relating to governance, transparency and completeness of communications to EY, and other matters pertaining to the Company&#8217;s internal control over financial reporting. Other than as described above, during the fiscal years ended June 30, 2024 and 2023, (1) there were no &#8220;disagreements,&#8221; as defined in Item 304(a)(1)(iv) of Regulation S-K, with EY on any matter of accounting principles or practices, and (2) there were no &#8220;reportable events&#8221; as described in Item 304(a)(1)(v) of Regulation S-K.",
        "<b>Item 8.01</b> Other Events",
    )

    // Super Micro, 8-K vom 18.11.2024: BDO wird neuer Pruefer
    private val smci401Neu = html(
        "<b>Item 4.01</b> Changes in Registrant&#8217;s Certifying Accountant",
        "On November 18, 2024, the Audit Committee of the Board of Directors of Super Micro Computer, Inc. (the &#8220;Company&#8221;) appointed BDO USA, P.C. (&#8220;BDO&#8221;) as the Company&#8217;s new independent registered public accounting firm. During the fiscal years ended June 30, 2024 and 2023 and the subsequent interim periods through November 18, 2024, neither the Company nor anyone on its behalf has consulted with BDO regarding: (ii) any matter that was the subject of a &#8220;disagreement&#8221; within the meaning of Item 304(a)(1)(iv) of Regulation S-K and the related instructions; or (iii) any &#8220;reportable event&#8221; within the meaning of Item 304(a)(1)(v) of Regulation S-K.",
        "<b>Item 8.01</b> Other Events",
    )

    @Test
    fun pruefertritttZurueckMitMaengeln() {
        val d = AchtKDetail.lesen(Art.PRUEFER, smci401Ruecktritt, "Super Micro Computer, Inc.")!!
        assertEquals(Ereignis.PRUEFER_RUECKTRITT, d.ereignis)
        assertEquals("Ernst & Young", d.pruefer)
        assertTrue(d.maengel)
        assertFalse(d.streit)
    }

    @Test
    fun neuerPrueferOhneStreit() {
        val d = AchtKDetail.lesen(Art.PRUEFER, smci401Neu, "Super Micro Computer, Inc.")!!
        assertEquals(Ereignis.PRUEFER_NEU, d.ereignis)
        assertEquals("BDO", d.pruefer)
        assertFalse(d.maengel)
        assertFalse(d.streit)
    }

    // Super Micro, 8-K vom 15.11.2018 (Item 4.02): Abschluesse 2015 bis 2017 nicht mehr verlaesslich
    private val smci402 = html(
        "<b>Item 4.02(a)</b> Non-Reliance on Previously Issued Financial Statements or a Related Audit Report or Completed Interim Review",
        "On November 14, 2018, the Board of Directors of the Company, based on the recommendation of the Audit Committee and in consultation with management, determined that (i) the Company&#8217;s consolidated financial statements contained in its Annual Report on Form 10-K for the fiscal years ended June 30, 2016 and 2015, (ii) the Company&#8217;s previously issued condensed consolidated financial statements contained in its Quarterly Reports on Form 10-Q for the quarterly periods ended March 31, 2017, December 31, 2016 and September 30, 2016 should no longer be relied upon because of errors.",
        "<b>Item 9.01</b> Financial Statements and Exhibits",
    )

    @Test
    fun korrekturNenntDieJahre() {
        val d = AchtKDetail.lesen(Art.KORREKTUR, smci402, "Super Micro Computer, Inc.")!!
        assertEquals(2015, d.jahrVon)
        assertEquals(2017, d.jahrBis)
    }

    // Super Micro, 8-K vom 15.10.2026: neuer CFO, der bisherige geht sofort
    private val smci502Cfo = html(
        "<b>Item 5.02</b> Departure of Directors or Certain Officers; Election of Directors; Appointment of Certain Officers; Compensatory Arrangements of Certain Officers",
        "<i>Chief Financial Officer Transition</i> On October 14, 2026, the Board of Directors of Super Micro Computer, Inc. (the &#8220;Company&#8221;) appointed Mark Long as Chief Financial Officer of the Company, effective immediately, at which time David Weigand ceased serving in that role. Mr. Long, age 59, has served since 2019 as a principal of Prime Impact Capital, LLC, an early-stage investment and advisory firm.",
        "<b>Item 9.01</b> Financial Statements and Exhibits",
    )

    // Boeing, 8-K vom 25.3.2024: der CEO tritt zurueck, fuer ihn ist kein Nachfolger genannt
    private val boeing502 = html(
        "<b>Item 5.02.</b> Departure of Directors or Certain Officers; Election of Directors; Appointment of Certain Officers; Compensatory Arrangements of Certain Officers.",
        "On March 25, 2024, The Boeing Company (the &#8220;Company&#8221;) announced several decisions that occurred on March 24, 2024, involving the Company&#8217;s management and Board of Directors (the &#8220;Board&#8221;). First, David L. Calhoun decided to step down as President and Chief Executive Officer at the end of 2024. Second, Lawrence W. Kellner, independent Chair of the Board, decided not to stand for re-election at the Company&#8217;s 2024 Annual Meeting of Shareholders. Third, the Board elected Stephanie F. Pope, Executive Vice President and Chief Operating Officer, to replace Stanley A. Deal as President and Chief Executive Officer, Boeing Commercial Airplanes, effective March 25, 2024.",
        "<b>Item 7.01.</b> Regulation FD Disclosure.",
    )

    // Eigenes Beispiel: Ruecktritt nur vom Sitz im Board ist kein Abgang aus dem Amt
    private val nurBoard502 = html(
        "<b>Item 5.02</b> Departure of Directors or Certain Officers; Election of Directors; Appointment of Certain Officers; Compensatory Arrangements of Certain Officers",
        "On January 16, 2025, Jane Miller, who served as interim Chief Executive Officer of Example Corp. (the &#8220;Company&#8221;) until December 31, 2024, resigned as a Class II director of the Company, effective January 16, 2025.",
        "<b>Item 9.01</b> Financial Statements and Exhibits",
    )

    private val berufung502 = html(
        "<b>Item 5.02</b> Departure of Directors or Certain Officers; Election of Directors; Appointment of Certain Officers; Compensatory Arrangements of Certain Officers",
        "On and effective as of January 16, 2025, the Board of Directors of Example Corp. (the &#8220;Company&#8221;) appointed John Smith, the Company's Chief Executive Officer and President, as a Class II director of the Company to serve until the 2027 annual meeting of stockholders and until his successor is duly elected or qualified, or until his prior death, resignation, retirement, disqualification or removal.",
        "Mr. Smith has served as the Company's CEO since January 1, 2025.",
        "<b>Item 9.01</b> Financial Statements and Exhibits",
    )

    @Test
    fun berufungMitFormelZurAmtsdauerIstKeinAbgang() {
        assertEquals(Rolle.KEINE, AchtK.rolleLesen(berufung502, "Example Corp."))
        assertEquals("", AchtKDetail.lesen(Art.FUEHRUNG, berufung502, "Example Corp.")?.person.orEmpty())
        // Ein echter Ruecktritt daneben bleibt erkannt
        assertEquals(Rolle.CEO, AchtK.rolleLesen(amtUndBoard502, "Example Corp."))
    }

    private val amtUndBoard502 = html(
        "<b>Item 5.02</b> Departure of Directors or Certain Officers; Election of Directors; Appointment of Certain Officers; Compensatory Arrangements of Certain Officers",
        "On January 16, 2025, Jane Miller resigned as Chief Executive Officer of Example Corp. (the &#8220;Company&#8221;) and as a Class II director, effective January 16, 2025.",
        "<b>Item 9.01</b> Financial Statements and Exhibits",
    )

    @Test
    fun ruecktrittNurVomBoardIstKeinAbgangDesCeo() {
        assertEquals(Rolle.KEINE, AchtK.rolleLesen(nurBoard502, "Example Corp."))
        assertEquals("", AchtKDetail.lesen(Art.FUEHRUNG, nurBoard502, "Example Corp.")?.person.orEmpty())
        assertEquals(Rolle.CEO, AchtK.rolleLesen(amtUndBoard502, "Example Corp."))
        assertEquals("Jane Miller", AchtKDetail.lesen(Art.FUEHRUNG, amtUndBoard502, "Example Corp.")!!.person)
    }

    @Test
    fun folgemeldungZumSelbenAbgangZaehltEinmal() {
        fun w(tag: String, acc: String, rolle: Rolle, d: AchtK.Detail?) = AchtK.Warnzeichen(Art.FUEHRUNG, tag, rolle, acc, "a.htm", d)
        val liste = listOf(
            w("2026-05-26", "c", Rolle.CFO, AchtK.Detail(person = "Sam Baker", wirksam = "2026-06-22", nachfolge = true)),
            w("2026-03-11", "b", Rolle.CFO, AchtK.Detail(person = "Liz Example", wirksam = "2026-03-27", nachfolge = true)),
            w("2026-02-05", "a", Rolle.CFO, AchtK.Detail(wirksam = "2026-03-27", nachfolge = false)),
            w("2025-01-17", "z", Rolle.CEO, AchtK.Detail(wirksam = "2026-03-27")),
            w("2024-12-01", "y", Rolle.CFO, null),
        )
        val aus = AchtK.ohneFolgemeldungen(liste)
        assertEquals(listOf("c", "a", "z", "y"), aus.map { it.accession })
        assertEquals("Liz Example", aus[1].detail!!.person)
        assertEquals(true, aus[1].detail!!.nachfolge)
        assertEquals("2026-02-05", aus[1].tag)
    }

    @Test
    fun cfoWechselMitNameSofortUndNachfolger() {
        assertEquals(Rolle.CFO, AchtK.rolleLesen(smci502Cfo, "Super Micro Computer, Inc."))
        val d = AchtKDetail.lesen(Art.FUEHRUNG, smci502Cfo, "Super Micro Computer, Inc.")!!
        assertEquals("David Weigand", d.person)
        assertEquals(Ereignis.ABGANG, d.ereignis)
        assertTrue(d.sofort)
        assertEquals(true, d.nachfolge)
    }

    @Test
    fun ceoRuecktrittOhneNachfolgerFuerDenCeo() {
        val d = AchtKDetail.lesen(Art.FUEHRUNG, boeing502, "The Boeing Company")!!
        assertEquals("David L. Calhoun", d.person)
        assertEquals(Ereignis.RUECKTRITT, d.ereignis)
        assertFalse(d.sofort)
        assertEquals(false, d.nachfolge)
    }

    @Test
    fun insolvenzUndAbschreibungAusKurzemBeispiel() {
        val i = html("<b>Item 1.03</b> Bankruptcy or Receivership", "On May 1, 2025, the Company filed voluntary petitions for relief under Chapter 11 of Title 11 of the United States Code.")
        assertEquals(11, AchtKDetail.lesen(Art.INSOLVENZ, i, "Example Corp")!!.kapitel)
        val a = html("<b>Item 2.06</b> Material Impairments", "The Company concluded that a non-cash charge for impairment of goodwill of approximately \$4.2 million is required.")
        assertEquals(4.2e6, AchtKDetail.lesen(Art.ABSCHREIBUNG, a, "Example Corp")!!.betrag, 1.0)
        assertNull(AchtKDetail.lesen(Art.VERBINDLICHKEIT, a, "Example Corp"))
    }

    @Test
    fun warnzeichenTragenDieAngaben() {
        val liste = AchtK.meldungen("""{"name":"X","filings":{"recent":{"accessionNumber":["1","2"],"filingDate":["2025-02-26","2024-12-06"],"form":["8-K","8-K"],"items":["3.01","3.01"],"primaryDocument":["a.htm","b.htm"]}}}""")
        val d = mapOf(AchtK.detailKey("1", Art.DELISTING) to Detail(unterart = Unterart.ENTWARNUNG), AchtK.detailKey("2", Art.DELISTING) to Detail(unterart = Unterart.ANGEDROHT))
        val w = AchtK.warnzeichen(liste, java.time.LocalDate.of(2025, 3, 1), emptyMap(), d)
        assertEquals(Unterart.ENTWARNUNG, w[0].detail?.unterart)
        assertEquals(1, AchtK.anzahl(w))
    }
}
