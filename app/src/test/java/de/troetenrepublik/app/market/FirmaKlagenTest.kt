package de.troetenrepublik.app.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// Klagen, Urteile und Behörden: Rolle aus dem Aktennamen, Lesen der Antworten von CourtListener, EU-Gerichten, CMA und
// Justizministerium (selbst formulierte Ausschnitte), Zuordnung nur bei eindeutigem Namen.
class FirmaKlagenTest {

    @Test fun rolleAusDemAktennamen() {
        assertEquals(Klagen.Rolle.BEKLAGTER, Klagen.rolle("Boeing Co", "Smith v. Boeing Co."))
        assertEquals(Klagen.Rolle.KLAEGER, Klagen.rolle("Boeing Co", "Boeing Co. v. Airbus"))
        assertEquals(Klagen.Rolle.BEKLAGTER, Klagen.rolle("Boeing Co", "In re Boeing Co. Securities Litigation"))
        assertEquals(Klagen.Rolle.KLAEGER, Klagen.rolle("Qualcomm Inc", "Qualcomm, Inc. and Qualcomm Europe, Inc. v European Commission"))
        assertNull(Klagen.rolle("Apple Inc.", "Smith v. Apple Valley Foods"))
        assertNull(Klagen.rolle("Boeing Co", "Smith v. Jones"))
        assertNull(Klagen.rolle("Boeing Co", "Boeing"))
    }

    @Test fun nurEindeutigeNamen() {
        assertTrue(Klagen.eindeutig("Apple Inc."))
        assertFalse(Klagen.eindeutig("Target Corporation"))
        assertNull(Klagen.clAdresse("Target Corporation", false))
        assertNull(Klagen.euAdresse("Target Corporation", LocalDate.of(2016, 1, 1)))
        assertNull(Klagen.cmaAdresse("Target Corporation"))
        assertNull(Klagen.dojAdresse("Target Corporation"))
        assertEquals("Boeing", Klagen.suchname("The Boeing Company"))
    }

    @Test fun adressen() {
        val cl = Klagen.clAdresse("Boeing Co", false)!!
        assertTrue(cl.startsWith(Klagen.CL_SUCHE + "?type=r&q="))
        assertTrue(cl.contains("caseName%3A%22Boeing%22"))
        assertTrue(Klagen.clAdresse("Boeing Co", true)!!.contains("type=o"))
        val eu = Klagen.euAdresse("Boeing Co", LocalDate.of(2016, 1, 1))!!
        assertTrue(eu.startsWith(Klagen.EU_SPARQL + "?query="))
        assertTrue(eu.contains("boeing"))
        assertTrue(eu.contains("2016-01-01"))
        assertTrue(Klagen.cmaAdresse("Boeing Co")!!.contains("filter_document_type=cma_case"))
        assertTrue(Klagen.dojAdresse("Boeing Co")!!.contains("parameters%5Btitle%5D=Boeing"))
    }

    private val cl = """
        {"count":3,"next":"https://www.courtlistener.com/api/rest/v4/search/?cursor=abc&type=r","previous":null,"results":[
        {"caseName":"Smith v. Boeing Co.","court":"District Court, N.D. Illinois","court_citation_string":"N.D. Ill.","dateFiled":"2023-04-05","dateTerminated":null,"docket_absolute_url":"/docket/1/smith-v-boeing/","suitNature":"850 Securities/Commodities"},
        {"caseName":"Boeing Co. v. Airbus","court":"Court X","dateFiled":"2019-01-02","dateTerminated":"2020-02-03","docket_absolute_url":"/docket/2/b-v-a/"},
        {"caseName":"Smith v. Apple Valley Foods","dateFiled":"2022-01-01","docket_absolute_url":"/docket/3/x/"}]}"""

    @Test fun courtListenerAkten() {
        val s = Klagen.clLesen(cl, "Boeing Co", false)
        assertEquals(2, s.faelle.size)
        assertEquals("https://www.courtlistener.com/api/rest/v4/search/?cursor=abc&type=r", s.weiter)
        val a = s.faelle[0]
        assertEquals(Klagen.Rolle.BEKLAGTER, a.rolle)
        assertEquals(Klagen.Stand.OFFEN, a.stand)
        assertEquals(Klagen.Gebiet.WERTPAPIER, a.gebiet)
        assertEquals("N.D. Ill.", a.stelle)
        assertEquals("https://www.courtlistener.com/docket/1/smith-v-boeing/", a.link)
        assertEquals(Klagen.Art.VERFAHREN, a.art)
        val b = s.faelle[1]
        assertEquals(Klagen.Rolle.KLAEGER, b.rolle)
        assertEquals(Klagen.Stand.ABGESCHLOSSEN, b.stand)
        assertEquals("Court X", b.stelle)
        assertEquals(2019, b.jahr)
        assertEquals(1, Klagen.Ergebnis(s.faelle).offeneBund)
    }

    @Test fun courtListenerUrteileUndFremdeAdresse() {
        val s = Klagen.clLesen(cl.replace("https://www.courtlistener.com/api", "https://example.org/api"), "Boeing Co", true)
        assertNull(s.weiter)
        assertTrue(s.faelle.all { it.art == Klagen.Art.URTEIL && it.stand == Klagen.Stand.ABGESCHLOSSEN })
        assertEquals(0, Klagen.clLesen("kein json", "Boeing Co", false).faelle.size)
    }

    private val eu = """
        {"results":{"bindings":[
        {"celex":{"value":"62023TJ1129"},"t":{"value":"Judgment of the General Court (Second Chamber) of 10 December 2025.#Intel Corporation Inc. v European Commission.#Competition – Abuse of dominant position – Microprocessors market#Case T-1129/23."},"d":{"value":"2025-12-10"}},
        {"celex":{"value":"62023TJ1129"},"t":{"value":"Judgment of the General Court (Second Chamber) of 10 December 2025.#Intel Corporation Inc. v European Commission.#Competition – Abuse of dominant position#Case T-1129/23."},"d":{"value":"2025-12-10"}},
        {"celex":{"value":"62023TA1129"},"t":{"value":"Case T-1129/23: Judgment of the General Court of 10 December 2025 – Intel Corporation v Commission (Competition)"},"d":{"value":"2025-12-10"}},
        {"celex":{"value":"62021TJ0001"},"t":{"value":"Judgment of the General Court of 1 March 2023.#Intel Corporation Inc. v European Commission.#Access to documents – Regulation (EC) No 1049/2001#Case T-1/21."},"d":{"value":"2023-03-01"}},
        {"celex":{"value":"62019CJ0466"},"t":{"value":"Judgment of the Court of 28 January 2021.#European Commission v Intel Corporation Inc..#Appeal – Competition – Information request#Case C-466/19 P."},"d":{"value":"2021-01-28"}}
        ]}}"""

    @Test fun euUrteile() {
        val f = Klagen.euLesen(eu, "Intel Corp.")
        // Doppelte Meldung, Hinweisblatt (TA) und Urteil zu Dokumentenzugang entfallen
        assertEquals(2, f.size)
        assertEquals("EuG", f[0].stelle)
        assertEquals(Klagen.Rolle.KLAEGER, f[0].rolle)
        assertEquals(Klagen.Gebiet.KARTELL, f[0].gebiet)
        assertEquals(Klagen.Art.URTEIL, f[0].art)
        assertEquals("https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:62023TJ1129", f[0].link)
        assertEquals("EuGH", f[1].stelle)
        assertEquals(Klagen.Rolle.BEKLAGTER, f[1].rolle)
        assertEquals(0, Klagen.euLesen(eu, "Qualcomm Inc").size)
    }

    private val cma = """
        {"results":[
        {"case_state":["open"],"case_type":["digital-markets-unit"],"link":"/cma-cases/googles-mobile-platform","opened_date":"2025-01-23","public_timestamp":"2026-11-05T13:29:57Z","title":"Google’s mobile platform"},
        {"case_state":["closed"],"case_type":["mergers"],"link":"/cma-cases/google-beatthatquote-com","outcome_type":"mergers-phase-1-clearance","public_timestamp":"2011-07-01T09:00:00Z","title":"Google / BeatThatQuote.com"},
        {"case_state":["closed"],"case_type":["ca98-and-civil-cartels"],"link":"/cma-cases/googled-farm","opened_date":"2020-01-01","title":"Googled Farm investigation"},
        {"case_state":["closed"],"case_type":["consumer-enforcement"],"link":"/cma-cases/google-reviews","opened_date":"2019-05-01","title":"Online reviews: Google and others"}]}"""

    @Test fun cmaFaelle() {
        val f = Klagen.cmaLesen(cma, "Google LLC")
        // Freigegebene Fusion und ähnlicher Name entfallen
        assertEquals(listOf("Google’s mobile platform", "Online reviews: Google and others"), f.map { it.titel })
        assertEquals(Klagen.Stand.OFFEN, f[0].stand)
        assertEquals(Klagen.Gebiet.KARTELL, f[0].gebiet)
        assertEquals("2025-01-23", f[0].datum)
        assertEquals("https://www.gov.uk/cma-cases/googles-mobile-platform", f[0].link)
        assertEquals(Klagen.Gebiet.VERBRAUCHER, f[1].gebiet)
        assertEquals(Klagen.Stand.ABGESCHLOSSEN, f[1].stand)
    }

    @Test fun titelNennenDenNamenAlsWortfolge() {
        assertTrue(Klagen.titelNennt("Google LLC", "Google / BeatThatQuote.com"))
        assertFalse(Klagen.titelNennt("Google LLC", "Googled Farm investigation"))
        assertFalse(Klagen.titelNennt("Target Corporation", "Target market study"))
        assertTrue(Klagen.titelNennt("Johnson & Johnson", "Johnson & Johnson / Actelion"))
        assertFalse(Klagen.titelNennt("Johnson & Johnson", "Johnson Matthey"))
    }

    @Test fun justizministeriumNurMitteilungenUeberDieFirma() {
        assertTrue(Klagen.dojPasst("Boeing Co", "Boeing Agrees to Pay $2.5 Billion to Resolve Criminal Charge"))
        assertTrue(Klagen.dojPasst("Boeing Co", "Boeing Co. Pleads Guilty to Fraud"))
        assertTrue(Klagen.dojPasst("Boeing Co", "Justice Department Sues Boeing Over Safety Violations"))
        assertFalse(Klagen.dojPasst("Boeing Co", "Boeing Engineer Sentenced for Theft of Trade Secrets"))
        assertFalse(Klagen.dojPasst("Boeing Co", "Former Boeing Engineer Sentenced for Economic Espionage"))
        assertFalse(Klagen.dojPasst("Boeing Co", "Boeing Supplier Agrees to Pay $1 Million"))
        assertFalse(Klagen.dojPasst("Boeing Co", "Boeing Announces New Partnership"))
        assertFalse(Klagen.dojPasst("Target Corporation", "Target Agrees to Pay $1 Million"))
    }

    @Test fun betragAusTitel() {
        assertEquals(2.2e9, Klagen.betragAusTitel("To Pay More Than $2.2 Billion To Resolve")!!, 1.0)
        assertEquals(18e6, Klagen.betragAusTitel("Pays Government \$18 Million to Settle")!!, 1.0)
        assertEquals(50_000.0, Klagen.betragAusTitel("Fined \$50,000")!!, 0.1)
        assertEquals(3e6, Klagen.betragAusTitel("Pays \$1 Million and \$3 Million")!!, 1.0)
        assertNull(Klagen.betragAusTitel("Pleads Guilty to Fraud"))
        assertEquals(29.5e6, Klagen.betragAusTitel("Agrees to Pay \$29.5M to Resolve Alleged Employment Discrimination")!!, 1.0)
        assertEquals(1.2e9, Klagen.betragAusTitel("Pays \$1.2B Penalty")!!, 1.0)
        assertEquals(500e3, Klagen.betragAusTitel("Fined \$500K")!!, 0.1)
        assertEquals(30.0, Klagen.betragAusTitel("Pays \$30 More")!!, 0.01)
    }

    @Test fun dojFirmaAlsGeschaedigteZaehltNicht() {
        assertFalse(Klagen.dojPasst("Lockheed Martin Corp", "Chief Financial Officer of Mission Support Alliance Agrees to Pay \$124,440 for Allegedly Accepting Kickbacks from Lockheed Martin"))
        assertFalse(Klagen.dojPasst("Boeing Co", "Man Pleads Guilty to Defrauding Boeing"))
        assertTrue(Klagen.dojPasst("Lockheed Martin Corp", "Lockheed Martin Agrees To Pay \$4.4 Million To Resolve Claims"))
    }

    private val doj = """
        {"results":[
        {"title":"Johnson &amp; Johnson To Pay More Than ${'$'}2.2 Billion To Resolve Fraud And Misbranding Allegations","date":"1383566400","url":"https://www.justice.gov/usao-edpa/pr/a","component":[{"name":"USAO - Pennsylvania, Eastern"}]},
        {"title":"Johnson &amp; Johnson To Pay More Than ${'$'}2.2 Billion To Resolve Fraud And Misbranding Allegations","date":"1383566400","url":"https://www.justice.gov/usao-ndca/pr/a","component":[{"name":"USAO - California, Northern"}]},
        {"title":"Former Johnson &amp; Johnson Engineer Sentenced for Theft","date":"1400000000","url":"https://www.justice.gov/pr/c","component":[]},
        {"title":"Notice of Court Proceedings - United States v. Daniel Johnson &amp; Nicole Holland","date":"1538049600","url":"https://www.justice.gov/pr/d","component":[]},
        {"title":"Johnson &amp; Johnson Subsidiary Acclarent Inc. Pays Government ${'$'}18 Million to Settle False Claims Act Allegations","date":"1469188800","url":"https://www.justice.gov/pr/e","component":[{"name":"Civil Division"}]}]}"""

    @Test fun justizministeriumListe() {
        val f = Klagen.dojLesen(doj, "Johnson & Johnson", LocalDate.of(2010, 1, 1))
        assertEquals(2, f.size)
        assertEquals("Johnson & Johnson To Pay More Than \$2.2 Billion To Resolve Fraud And Misbranding Allegations", f[0].titel)
        assertEquals(2013, f[0].jahr)
        assertEquals(2.2e9, f[0].betrag!!, 1.0)
        assertEquals(Klagen.Gebiet.PRODUKT, f[0].gebiet)
        assertEquals(Klagen.Rolle.BEKLAGTER, f[0].rolle)
        assertEquals(Klagen.Stand.ABGESCHLOSSEN, f[1].stand)
        assertEquals(2016, f[1].jahr)
        assertEquals(18e6, f[1].betrag!!, 1.0)
        // Vor dem Stichtag
        assertEquals(1, Klagen.dojLesen(doj, "Johnson & Johnson", LocalDate.of(2016, 1, 1)).size)
        // Summe der letzten fünf Jahre gerechnet vom Stichtag
        val e = Klagen.Ergebnis(f)
        assertEquals(2.218e9, e.strafen(LocalDate.of(2017, 1, 1)), 1.0)
        assertEquals(18e6, e.strafen(LocalDate.of(2019, 1, 1)), 1.0)
        assertEquals(0.0, e.strafen(LocalDate.of(2023, 1, 1)), 0.1)
    }

    private fun fall(q: Klagen.Quelle, art: Klagen.Art, datum: String, stand: Klagen.Stand, betrag: Double? = null, link: String = "x$datum") =
        Klagen.Fall(q, art, "T$datum", "S", Klagen.Gebiet.SONSTIGE, Klagen.Rolle.UNBEKANNT, datum, stand, betrag, link)

    @Test fun jahreUndSortierung() {
        val heute = LocalDate.of(2026, 11, 13)
        val f = listOf(
            fall(Klagen.Quelle.COURTLISTENER, Klagen.Art.VERFAHREN, "2025-02-01", Klagen.Stand.OFFEN),
            fall(Klagen.Quelle.CMA, Klagen.Art.BEHOERDE, "2025-06-01", Klagen.Stand.ABGESCHLOSSEN),
            fall(Klagen.Quelle.DOJ, Klagen.Art.BEHOERDE, "2023-06-01", Klagen.Stand.ABGESCHLOSSEN, 5e6),
            fall(Klagen.Quelle.EU, Klagen.Art.URTEIL, "2022-06-01", Klagen.Stand.ABGESCHLOSSEN),
            fall(Klagen.Quelle.DOJ, Klagen.Art.BEHOERDE, "2010-06-01", Klagen.Stand.ABGESCHLOSSEN, 9e9),
        )
        val j = Klagen.neuJeJahr(f, listOf(2023, 2024), heute)
        assertEquals(10, j.size)
        assertEquals(2017, j.first().first)
        assertEquals(2026, j.last().first)
        assertEquals(2, j.first { it.first == 2025 }.second)
        assertEquals(2, j.first { it.first == 2023 }.second)
        // Urteile zählen nicht als neue Verfahren
        assertEquals(0, j.first { it.first == 2022 }.second)
        val s = Klagen.strafenJeJahr(listOf(2023 to 5e6, 2023 to 1e6, 2024 to 2e6, 2005 to 7e9), heute)
        assertEquals(10, s.size)
        assertEquals(6e6, s.first { it.first == 2023 }.second, 0.1)
        assertEquals(0.0, s.first { it.first == 2025 }.second, 0.1)
        val e = Klagen.Ergebnis(f)
        assertEquals(1, e.offeneBund)
        // Offen zuerst, dann der höchste Betrag
        assertEquals(listOf("T2025-02-01", "T2010-06-01", "T2023-06-01"), e.sortiert.take(3).map { it.titel })
        // Nur die DOJ-Mitteilung von 2023 liegt in den letzten fünf Jahren
        assertEquals(5e6, e.strafen(heute), 0.1)
    }

    @Test fun zusammenfuehrenOhneDoppelte() {
        val a = Klagen.Ergebnis(listOf(fall(Klagen.Quelle.EU, Klagen.Art.URTEIL, "2022-06-01", Klagen.Stand.ABGESCHLOSSEN, link = "l1")))
        val b = Klagen.Ergebnis(
            listOf(fall(Klagen.Quelle.EU, Klagen.Art.URTEIL, "2022-06-01", Klagen.Stand.ABGESCHLOSSEN, link = "l1"), fall(Klagen.Quelle.CMA, Klagen.Art.BEHOERDE, "2024-01-01", Klagen.Stand.OFFEN, link = "l2")),
            mehr = true,
        )
        val z = Klagen.zusammen(a, b)!!
        assertEquals(2, z.faelle.size)
        assertTrue(z.mehr)
        assertNull(Klagen.zusammen(null, null))
        assertNotNull(Klagen.zusammen(a, null))
    }

    @Test fun sachgebiete() {
        assertEquals(Klagen.Gebiet.WERTPAPIER, Klagen.gebiet("850 Securities/Commodities"))
        assertEquals(Klagen.Gebiet.KARTELL, Klagen.gebiet("410 Antitrust"))
        assertEquals(Klagen.Gebiet.SCHUTZRECHTE, Klagen.gebiet("830 Patent"))
        assertEquals(Klagen.Gebiet.PRODUKT, Klagen.gebiet("365 Personal Inj. Prod. Liability"))
        assertEquals(Klagen.Gebiet.ARBEIT, Klagen.gebiet("710 Fair Labor Standards Act"))
        assertEquals(Klagen.Gebiet.UMWELT, Klagen.gebiet("893 Environmental Matters"))
        assertEquals(Klagen.Gebiet.SONSTIGE, Klagen.gebiet("Contract dispute"))
    }
}
