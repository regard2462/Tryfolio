package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Investorenbriefe.Art
import de.troetenrepublik.app.engine.Investorenbriefe.Beleg
import de.troetenrepublik.app.engine.Investorenbriefe.Eigen
import de.troetenrepublik.app.engine.Investorenbriefe.F13
import de.troetenrepublik.app.engine.Investorenbriefe.Fundstelle
import de.troetenrepublik.app.engine.Investorenbriefe.Lage
import de.troetenrepublik.app.engine.Investorenbriefe.Passt
import de.troetenrepublik.app.engine.Investorenbriefe.Suche
import de.troetenrepublik.app.market.Briefabrufe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// Was Investoren schreiben (5.92): Leser, Auszug, Laenge, Richtung, Zuordnung zum Namen und Einordnung. Alle Daten
// sind selbst gebaut nach dem Aufbau der Quellen.
class Fassung592BriefeTest {

    private fun nennt(name: String, symbol: String, text: String): Boolean {
        val s = Suche(name, symbol)
        val k = s.kontext(text)
        return Investorenbriefe.saetze(text).any { s.nennt(it, k) }
    }

    private fun kein(name: String, symbol: String, text: String) = assertFalse("$name: $text", nennt(name, symbol, text))

    // ------------------------------------------------------------ Uebersichtsseiten

    @Test fun neuesterBriefVonDerUebersicht() {
        val a = Investorenbriefe.absender("montaka")!!
        val html = """
            <a href="https://montaka.com/quarterly-update-q2-2026/">Q2 2026</a>
            <a href="https://montaka.com/quarterly-update-q1-2026/">Q1 2026</a>
            <a href="https://montaka.com/quarterly-update-q4-2025/">Q4 2025</a>
        """
        assertEquals(listOf("https://montaka.com/quarterly-update-q2-2026/"), Investorenbriefe.links(html, a))
    }

    @Test fun dreiNeuesteKommentareUndRelativeLinks() {
        val se = Investorenbriefe.absender("southeastern")!!
        val html = listOf("2q26-partners", "3q26-partners", "3q26-small-cap", "1q26-global", "3q26-global")
            .joinToString("\n") { "<a href=\"https://southeasternasset.com/commentary/$it-fund-commentary/\">x</a>" }
        val l = Investorenbriefe.links(html, se)
        assertEquals(3, l.size)
        assertTrue(l.all { it.contains("3q26") })

        val gen = Investorenbriefe.absender("generation")!!
        val g = "<a href=\"/media/4eadeaih/generation-im-q2-2026-global-equity-investor-letter.pdf\">Q2</a>" +
            "<a href=\"/media/ddwbmlos/generation-im-q3-2026-global-equity-investor-letter.pdf\">Q3</a>"
        assertEquals(listOf("https://www.generationim.com/media/ddwbmlos/generation-im-q3-2026-global-equity-investor-letter.pdf"), Investorenbriefe.links(g, gen))

        val him = Investorenbriefe.absender("himalaya")!!
        val h = "<a href=\"/files/Letter-Chinese.pdf\">c</a><a href=\"/files/Talk-2026.pdf\">e</a>"
        assertEquals(listOf("https://www.himcap.com/files/Talk-2026.pdf"), Investorenbriefe.links(h, him))

        // Feste Adressen ohne Uebersicht
        assertEquals(3, Investorenbriefe.links("", Investorenbriefe.absender("weitz")!!).size)
    }

    @Test fun pdfAufDerBriefseite() {
        val tw = Investorenbriefe.absender("tweedy")!!
        val html = "<a href=\"https://www.tweedyfunds.com/wp-content/uploads/sites/10/2026/09/FundCommentary-Q2-2026-FINAL.pdf\">PDF</a>"
        assertEquals("https://www.tweedyfunds.com/wp-content/uploads/sites/10/2026/09/FundCommentary-Q2-2026-FINAL.pdf", Investorenbriefe.pdfLink(html, tw))
        assertNull(Investorenbriefe.pdfLink(html, Investorenbriefe.absender("montaka")!!))
    }

    @Test fun alleAbsenderHostsStehenImBuendel() {
        for (a in Investorenbriefe.ABSENDER) {
            for (u in listOfNotNull(a.seite) + a.fest) {
                assertEquals(u, "investorenbriefe", Quellenbuendel.quelleZu(java.net.URI(u).host))
            }
        }
        assertEquals("investorenbriefe", Quellenbuendel.quelleZu("www.brownadvisory.com"))
        assertEquals("sec", Quellenbuendel.quelleZu("efts.sec.gov"))
    }

    // ------------------------------------------------------------ Text

    @Test fun seitenTextOhneSkriptUndNavigation() {
        val html = "<html><head><script>var x = 'Microsoft';</script><style>p{}</style></head><body><nav>Menu Microsoft</nav>" +
            "<p>We added to Microsoft&nbsp;in the quarter.</p><table><tr><td>Microsoft Corp.</td><td>5.2%</td></tr></table>" +
            "<footer>Copyright</footer></body></html>"
        val t = Investorenbriefe.htmlText(html)
        assertFalse(t.contains("var x"))
        assertFalse(t.contains("Menu"))
        assertFalse(t.contains("Copyright"))
        assertTrue(t.contains("We added to Microsoft in the quarter."))
        assertTrue(t.lines().any { it == "Microsoft Corp.\t5.2%" })
    }

    @Test fun pdfZeilenWerdenAbsaetze() {
        val t = Investorenbriefe.pdfText(listOf("We continue to like the busi-", "ness of Microsoft.", "3", "• Item one"))
        assertTrue(t.startsWith("We continue to like the business of Microsoft."))
        assertTrue(t.lines().last().startsWith("•"))
        assertFalse(t.contains(" 3 "))
    }

    @Test fun saetzeMitAbkuerzungen() {
        val s = Investorenbriefe.saetze("Microsoft Corp. grew sales in the U.S. by 12%. We added to the position. Is it cheap? Yes.")
        assertEquals(4, s.size)
        assertEquals("Microsoft Corp. grew sales in the U.S. by 12%.", s[0])
    }

    @Test fun fliesstextGegenTabelle() {
        assertTrue(Investorenbriefe.fliesstext("Microsoft was the largest contributor to performance in the quarter as cloud demand grew."))
        assertFalse(Investorenbriefe.fliesstext("Microsoft Corp. 5.2% Alphabet Inc. 4.8% Amazon.com 4.1% Meta 3.9%"))
        assertFalse(Investorenbriefe.fliesstext("Top Ten Holdings"))
    }

    // ------------------------------------------------------------ Name

    @Test fun namensformen() {
        assertEquals("Microsoft", Investorenbriefe.grundname("Microsoft Corporation"))
        assertEquals("Meta Platforms", Investorenbriefe.grundname("Meta Platforms, Inc."))
        assertEquals("Alphabet", Investorenbriefe.grundname("Alphabet Inc. Class A"))
        assertEquals("Coca-Cola", Investorenbriefe.grundname("The Coca-Cola Company"))
        assertEquals("Amazon", Investorenbriefe.grundname("Amazon.com, Inc."))
        assertEquals("Siemens", Investorenbriefe.grundname("Siemens Aktiengesellschaft"))
        assertEquals("Novo Nordisk", Investorenbriefe.grundname("Novo Nordisk A/S"))
    }

    @Test fun zuordnungBeiNamensvarianten() {
        // Rechtsform im Text oder nicht
        assertTrue(nennt("Microsoft Corporation", "MSFT", "We added to Microsoft Corp. during the quarter."))
        assertTrue(nennt("Microsoft Corporation", "MSFT", "Shares of Microsoft rose after strong cloud results."))
        // Kurzname aus mehrteiligem Namen
        assertTrue(nennt("Meta Platforms, Inc.", "META", "Meta grew advertising revenue strongly this year."))
        // Anderer Name
        assertTrue(nennt("Alphabet Inc. Class A", "GOOGL", "Google remains one of our largest holdings."))
        // Schreibweise in Grossbuchstaben im Namen, normal im Text
        assertTrue(nennt("NVIDIA Corporation", "NVDA", "Nvidia continues to dominate accelerated computing."))
        // Kuerzel mit Boerse
        assertTrue(nennt("Target Corporation", "TGT", "Target (NYSE: TGT) disappointed us this quarter."))
        // Kuerzel in Klammern
        assertTrue(nennt("Intuitive Surgical, Inc.", "ISRG", "Intuitive Surgical (ISRG) keeps gaining share."))
        // Deutsche Aktie mit Boersenzusatz im Kuerzel
        assertTrue(nennt("SAP SE", "SAP.DE", "SAP raised its cloud outlook again."))
    }

    @Test fun keineTrefferBeiGleichlautendenWoertern() {
        // Gewoehnliche Woerter klein geschrieben
        kein("Apple Inc.", "AAPL", "An apple a day keeps the doctor away, as the saying goes in markets.")
        // Mehrdeutiger Name am Satzanfang ohne sichere Nennung
        kein("Target Corporation", "TGT", "Target prices for the sector remain elevated across the board.")
        kein("Block, Inc.", "XYZ", "Block trades dominated the closing auction on the last day.")
        kein("Shell plc", "SHEL", "Shell companies are often used to hide ownership from investors.")
        // Kurze Abkuerzung nur in Grossbuchstaben
        kein("SAP SE", "SAP.DE", "The sap of the tree is collected each spring by the farmers.")
        // Erstes Wort allein bezeichnet nichts
        kein("Deutsche Telekom AG", "DTE.DE", "Deutsche Bank reported weaker trading results this quarter.")
        // Wortteil mit Bindestrich
        kein("Meta Platforms, Inc.", "META", "A meta-analysis of studies shows mixed results for value.")
        // Abkuerzung in Klammern, die kein Kuerzel ist
        kein("Gartner, Inc.", "IT", "Spending on information technology (IT) rose by ten percent in the period.")
    }

    @Test fun mehrdeutigerNameMitSichererNennung() {
        val text = "Target Corporation (NYSE: TGT) is a new holding. Target has improved its supply chain over the year."
        val s = Suche("Target Corporation", "TGT")
        assertTrue(s.kontext(text))
        assertTrue(s.nennt("Target has improved its supply chain over the year.", true))
    }

    // ------------------------------------------------------------ Auszug und Richtung

    @Test fun auszugHoechstensZweiSaetzeUndFuenfzigWoerter() {
        val saetze = listOf(
            "Microsoft was our largest contributor in the quarter.",
            "Cloud revenue grew faster than we expected.",
            "We also reviewed many other ideas during the period and found few of them attractive enough to buy.",
        )
        val a = Investorenbriefe.auszug(saetze, 0, null)
        assertEquals("Microsoft was our largest contributor in the quarter. Cloud revenue grew faster than we expected.", a)
        val lang = (1..90).joinToString(" ") { if (it == 45) "Microsoft" else "word$it" } + "."
        val k = Investorenbriefe.auszug(listOf(lang), 0, lang.indexOf("Microsoft").let { it until it + 9 })
        assertTrue(k.contains("Microsoft"))
        assertTrue(Investorenbriefe.woerterZahl(k) <= 50)
        assertFalse(k.contains("…"))
        assertTrue(Investorenbriefe.auszugStueck(listOf(lang), 0, lang.indexOf("Microsoft").let { it until it + 9 }).gekuerzt)
        assertFalse(Investorenbriefe.auszugStueck(saetze, 0, null).gekuerzt)
    }

    @Test fun richtungGrobGeschaetzt() {
        assertEquals(1, Investorenbriefe.richtung("Microsoft was a strong contributor and remains attractive."))
        assertEquals(-1, Investorenbriefe.richtung("Intel was the largest detractor after disappointing results."))
        assertEquals(-1, Investorenbriefe.richtung("We do not find the shares attractive at this price."))
        assertEquals(0, Investorenbriefe.richtung("The company reports its results in November."))
        assertEquals(1, Investorenbriefe.richtung("SAP hat stark zugelegt und bleibt für uns attraktiv."))
        assertEquals(-1, Investorenbriefe.richtung("Die Aktie hat enttäuscht, wir haben sie verkauft."))
    }

    @Test fun eigeneAngabeNaheDerNennung() {
        fun e(satz: String, name: String = "Microsoft"): Eigen? {
            val s = Suche(name, "X")
            return Investorenbriefe.eigeneAngabe(satz, s.stellen(satz, true))
        }
        assertEquals(Eigen.KAUF, e("We sold Intel and bought Microsoft during the quarter."))
        assertEquals(Eigen.VERKAUF, e("We exited our position in Microsoft after a strong run."))
        assertEquals(Eigen.VERRINGERT, e("We trimmed Microsoft on valuation after strong performance."))
        assertEquals(Eigen.SHORT, e("We remain short Microsoft through a short position in the shares."))
        assertEquals(Eigen.HAELT, e("Microsoft was our largest contributor to performance this year."))
        assertNull(e("Microsoft reports earnings next week according to the calendar."))
    }

    // 0.77: als nicht gehalten gekennzeichnete Nennungen sind keine eigene Position
    @Test fun nichtGehalteneAktie() {
        fun e(satz: String, name: String = "Microsoft"): Eigen? {
            val s = Suche(name, "X")
            return Investorenbriefe.eigeneAngabe(satz, s.stellen(satz, true))
        }
        assertNull(e("By contrast, the largest relative detractors in the quarter were Intel, Microsoft (not owned), and Oracle."))
        assertNull(e("We do not own Microsoft, which was a detractor to relative returns."))
        // Die anderen Namen der Aufzaehlung bleiben eigene Positionen
        assertEquals(Eigen.HAELT, e("By contrast, the largest relative detractors in the quarter were Intel, Microsoft (not owned), and Oracle.", "Oracle"))
        val b = Investorenbriefe.Brief(
            "polen", "https://www.polencapital.com/perspectives/focus-growth-commentary", "Q3 2026", 0L,
            "Focus Growth Commentary\nThird quarter 2026\n" +
                "By contrast, the largest relative detractors in the quarter were Intel, Microsoft (not owned), and Oracle.",
        )
        val f = Investorenbriefe.fundstelle(b, Suche("Microsoft Corporation", "MSFT"), "Polen Capital")
        assertNotNull(f)
        assertEquals("", f!!.eigen)
        assertEquals(0, f.richtung)
        assertEquals(-1, Investorenbriefe.fundstelle(b, Suche("Oracle Corporation", "ORCL"), "Polen Capital")!!.richtung)
    }

    @Test fun quartalAusLinkUndText() {
        assertEquals("Q2 2026", Investorenbriefe.quartal("fund-commentary-q2-2026")?.text)
        assertEquals("Q3 2026", Investorenbriefe.quartal("3q26-partners-fund-commentary")?.text)
        assertEquals("Q2 2026", Investorenbriefe.quartal("Quarterly Letter - Jun26.pdf")?.text)
        assertEquals("Q3 2026", Investorenbriefe.quartal("For the third quarter of 2026 the fund rose", mitJahr = false)?.text)
        assertEquals("Q3 2026", Investorenbriefe.quartal("As of September 30, 2026", mitJahr = false)?.text)
        assertEquals("Q3 2026", Investorenbriefe.quartal("Bericht zum 3. Quartal 2026", mitJahr = false)?.text)
        assertEquals("2025", Investorenbriefe.quartal("giverny-capital-annual-letter-2025-1.pdf")?.text)
        assertNull(Investorenbriefe.quartal("no date here", mitJahr = false))
    }

    // ------------------------------------------------------------ Fundstelle

    private val brief = Investorenbriefe.Brief(
        "polen", "https://www.polencapital.com/perspectives/focus-growth-commentary", "Q3 2026", 0L,
        "Focus Growth Commentary\nThird quarter 2026\n" +
            "Markets rose in the quarter. We added to our existing position in Microsoft, whose cloud business keeps growing strongly. " +
            "We think the shares remain attractive.\n" +
            "Top Holdings\nMicrosoft Corp. 8.1%\nAmazon.com 6.0%\n" +
            "Please contact us to learn more. Subscribe to our newsletter today.",
    )

    @Test fun fundstelleImBrief() {
        val f = Investorenbriefe.fundstelle(brief, Suche("Microsoft Corporation", "MSFT"), "Polen Capital")
        assertNotNull(f)
        f!!
        assertTrue(f.auszug.startsWith("We added to our existing position in Microsoft"))
        assertTrue(Investorenbriefe.woerterZahl(f.auszug) <= 50)
        assertEquals(1, f.richtung)
        assertEquals("KAUF", f.eigen)
        assertTrue(f.bestand)
        assertTrue(f.werbung)
        assertEquals("Q3 2026", f.quartal)
        // Eine Aktie, die nur in der Bestandsliste steht, ergibt keine Fundstelle
        assertNull(Investorenbriefe.fundstelle(brief, Suche("Amazon.com, Inc.", "AMZN"), "Polen Capital"))
        // Und eine, die nicht vorkommt, auch nicht
        assertNull(Investorenbriefe.fundstelle(brief, Suche("Apple Inc.", "AAPL"), "Polen Capital"))
    }

    // ------------------------------------------------------------ Einordnung

    private fun fs(richtung: Int, eigen: String = "", bestand: Boolean = false, werbung: Boolean = false, absender: String = "a", schluessel: Int = 20263) =
        Fundstelle(absender, absender, "https://x.example/$absender", "Q3 2026", schluessel, "Text", richtung, eigen, bestand, werbung)

    @Test fun positivUeberEigenePosition() {
        val e = Investorenbriefe.einordnung(fs(1), Art.VERMOEGENSVERWALTER, F13.HAELT, false, false)
        assertEquals(listOf(Beleg.F13_HAELT), e.belege)
        assertEquals(Lage.LONG, e.lage)
        assertEquals(Passt.EIGENE_POSITIV, e.passt)
        assertTrue(e.inEigenerSache)
    }

    @Test fun eigeneAngabeGehtVor13F() {
        // Der Brief ist neuer als der 13F-Stand
        val e = Investorenbriefe.einordnung(fs(-1, eigen = "VERKAUF"), Art.HEDGEFONDS, F13.HAELT, false, false)
        assertEquals(listOf(Beleg.F13_HAELT, Beleg.BRIEF_VERKAUF), e.belege)
        assertEquals(Lage.VERKAUFT, e.lage)
        assertEquals(Passt.NACH_VERKAUF_KRITISCH, e.passt)
        assertFalse(e.inEigenerSache)
    }

    @Test fun shortUndNegativ() {
        val e = Investorenbriefe.einordnung(fs(-1, eigen = "SHORT"), Art.HEDGEFONDS, null, false, false)
        assertEquals(Lage.SHORT, e.lage)
        assertEquals(Passt.SHORT_NEGATIV, e.passt)
        assertTrue(e.inEigenerSache)
    }

    @Test fun ohnePositionNurDas() {
        val e = Investorenbriefe.einordnung(fs(1), Art.FONDSGESELLSCHAFT, null, false, false)
        assertEquals(listOf(Beleg.UNBEKANNT), e.belege)
        assertEquals(Lage.UNBEKANNT, e.lage)
        assertNull(e.passt)
        // 13F gemeldet, die Aktie steht nicht darin: genau das, ohne Satz zur Passung
        val k = Investorenbriefe.einordnung(fs(-1), Art.HOLDING, F13.KEINE, false, false)
        assertEquals(listOf(Beleg.F13_KEINE), k.belege)
        assertNull(k.passt)
    }

    @Test fun bestandslisteUndFirmaSelbst() {
        val b = Investorenbriefe.einordnung(fs(0, bestand = true), Art.FONDSGESELLSCHAFT, null, false, false)
        assertEquals(listOf(Beleg.BESTANDSLISTE), b.belege)
        assertEquals(Lage.LONG, b.lage)
        assertNull(b.passt)
        val f = Investorenbriefe.einordnung(fs(1), Art.HOLDING, F13.HAELT, false, true)
        assertEquals(Beleg.FIRMA_SELBST, f.belege.first())
        assertEquals(Passt.FIRMA_SELBST, f.passt)
        val w = Investorenbriefe.einordnung(fs(1, werbung = true), Art.FONDSGESELLSCHAFT, null, true, false)
        assertTrue(w.werbung)
        assertEquals(listOf(Beleg.SCHEDULE13), w.belege)
    }

    @Test fun zusammenfassungJeAbsender() {
        val l = listOf(
            fs(1, absender = "a", schluessel = 20263), fs(-1, absender = "a", schluessel = 20262),
            fs(-1, absender = "b"), fs(0, absender = "c"), fs(1, eigen = "HAELT", absender = "d"),
        ).map { it to Investorenbriefe.einordnung(it, Art.FONDSGESELLSCHAFT, null, false, false) }
        val z = Investorenbriefe.zusammenfassung(l)
        assertEquals(4, z.absender)
        assertEquals(2, z.positiv)
        assertEquals(1, z.negativ)
        assertEquals(1, z.neutral)
        assertEquals(1, z.eigeneSache)
    }

    @Test fun alteFundstellenFallenWeg() {
        val l = Investorenbriefe.aktuelle(listOf(fs(1, schluessel = 20243), fs(1, schluessel = 20263), fs(1, schluessel = 0)), LocalDate.of(2026, 10, 28))
        assertEquals(listOf(20263, 0), l.map { it.schluessel })
    }

    // ------------------------------------------------------------ SEC

    @Test fun volltextsucheDerSec() {
        val json = """{"took":5,"hits":{"total":{"value":2,"relation":"eq"},"hits":[
            {"_index":"edgar_file","_id":"0001829126-26-009832:growthfund_ncsrs.htm","_score":7.6,"_source":{"ciks":["0000806857"],
             "period_ending":"2026-06-30","display_names":["GABELLI GROWTH FUND  (CIK 0000806857)"],"root_forms":["N-CSRS"],
             "file_date":"2026-09-08","form":"N-CSRS","adsh":"0001829126-26-009832","file_type":"N-CSRS"}},
            {"_index":"edgar_file","_id":"0001829126-26-009832:growthfund_ncsrs.htm","_score":7.0,"_source":{"ciks":["0000806857"],
             "display_names":["GABELLI GROWTH FUND  (CIK 0000806857)"],"form":"N-CSRS","adsh":"0001829126-26-009832"}},
            {"_index":"edgar_file","_id":"kaputt","_source":{"ciks":[]}}
        ]}}"""
        val t = Investorenbriefe.secTreffer(json)
        assertEquals(1, t.size)
        assertEquals("Gabelli Growth Fund", t[0].name)
        assertEquals("https://www.sec.gov/Archives/edgar/data/806857/000182912626009832/growthfund_ncsrs.htm", t[0].adresse)
        assertEquals("2026-06-30", t[0].stichtag)
        assertTrue(Investorenbriefe.secTreffer("<html>").isEmpty())
        val u = Investorenbriefe.secAdresse("Microsoft", "performance", "2026-04-11", "2026-10-28")
        assertTrue(u.startsWith("https://efts.sec.gov/LATEST/search-index?q=%22Microsoft%22%20NEAR%2815%29%20performance"))
        assertTrue(u.contains("&forms=N-CSR,N-CSRS"))
        assertEquals("JP Morgan Mutual Fund Investment Trust", Investorenbriefe.fondsName("JP MORGAN MUTUAL FUND INVESTMENT TRUST (CIK 0000803747)"))
    }

    @Test fun kontaktadresseNurAnDieSec() {
        assertTrue(Briefabrufe.beiSec("https://efts.sec.gov/LATEST/search-index?q=x"))
        assertTrue(Briefabrufe.beiSec("https://www.sec.gov/Archives/edgar/data/1/2/a.htm"))
        assertFalse(Briefabrufe.beiSec("https://sec.gov.example.com/x"))
        assertFalse(Briefabrufe.beiSec("https://notsec.gov/x"))
        assertFalse(Briefabrufe.beiSec("http://www.sec.gov/x"))
        assertFalse(Briefabrufe.KENNUNG.contains("@"))
    }
}
