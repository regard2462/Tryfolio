package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Optionsanalyse.Kontrakt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun <T : Any> assertNotNull(v: T?): T = v ?: throw AssertionError("null")

class Fassung078ITest {
    private val jetzt = 1_800_000_000L
    private val tag = 86_400L

    private fun k(call: Boolean, strike: Double, umsatz: Long, offen: Long, mitte: Double = 2.0, tage: Int = 20) =
        Kontrakt(call, strike, jetzt + tage * tag, mitte, mitte - 0.05, mitte + 0.05, umsatz, offen, 0.3)

    // ------------------------------------------------------------ Auffaellige Kontrakte

    @Test fun auffaelligNachVerhaeltnis() {
        val kette = listOf(
            k(true, 105.0, 3000, 500), k(true, 110.0, 900, 100), k(false, 95.0, 600, 200),
            k(true, 100.0, 5000, 4000), // Verhaeltnis 1,25, zu niedrig
            k(false, 90.0, 50, 1),      // unter dem Mindestvolumen
        )
        val w = assertNotNull(AuffaelligeKontrakte.auswerten(100.0, kette, 500, AuffaelligeKontrakte.Art.ALLE, jetzt))
        assertEquals(listOf(110.0, 105.0, 95.0), w.zeilen.map { it.kontrakt.strike })
        assertEquals(9.0, w.zeilen[0].verhaeltnis, 1e-9)
        assertEquals(0.10, w.zeilen[0].abstand, 1e-9)
        // Praemie: Volumen x Mitte x 100
        assertEquals(900 * 2.0 * 100, w.zeilen[0].praemie, 1e-9)
        // Call- und Put-Umsatz zaehlen alle Kontrakte der Kette
        assertEquals(3000L + 900 + 5000, w.callUmsatz)
        assertEquals(600L + 50, w.putUmsatz)
    }

    @Test fun auffaelligFilter() {
        val kette = listOf(k(true, 105.0, 3000, 500), k(false, 95.0, 600, 200))
        val calls = assertNotNull(AuffaelligeKontrakte.auswerten(100.0, kette, 100, AuffaelligeKontrakte.Art.CALLS, jetzt))
        assertTrue(calls.zeilen.all { it.kontrakt.call })
        val puts = assertNotNull(AuffaelligeKontrakte.auswerten(100.0, kette, 100, AuffaelligeKontrakte.Art.PUTS, jetzt))
        assertTrue(puts.zeilen.none { it.kontrakt.call })
        assertNull(AuffaelligeKontrakte.auswerten(100.0, kette, 5000, AuffaelligeKontrakte.Art.ALLE, jetzt))
        assertNull(AuffaelligeKontrakte.auswerten(0.0, kette, 100, AuffaelligeKontrakte.Art.ALLE, jetzt))
    }

    @Test fun auffaelligOhneOffenesInteresseUndAbgelaufen() {
        // Offen 0 ergibt keinen Faktor (neu), solange die Kette sonst offene Kontrakte nennt; ein seit mehr als
        // einem Tag abgelaufener Kontrakt faellt weg
        val kette = listOf(k(true, 105.0, 400, 0), k(true, 100.0, 50, 500), k(false, 95.0, 50, 500), k(true, 110.0, 9000, 10, tage = -3))
        val w = assertNotNull(AuffaelligeKontrakte.auswerten(100.0, kette, 100, AuffaelligeKontrakte.Art.ALLE, jetzt))
        assertEquals(listOf(105.0), w.zeilen.map { it.kontrakt.strike })
        assertTrue(w.zeilen[0].neu)
        assertEquals(0.0, w.zeilen[0].verhaeltnis, 1e-9)
    }

    @Test fun auffaelligHoechstensDreiJeWert() {
        val kette = (1..6).map { k(true, 100.0 + it, 1000L * it, 10) }
        val w = assertNotNull(AuffaelligeKontrakte.auswerten(100.0, kette, 100, AuffaelligeKontrakte.Art.ALLE, jetzt))
        assertEquals(listOf(106.0, 105.0, 104.0), w.zeilen.map { it.kontrakt.strike })
    }

    // ------------------------------------------------------------ Nasdaq-Griechen

    // Gekuerzte Antwort vom 11.11.2026 (AAPL, Verfall 18.12.2026)
    private val antwort = """{"data":{"pageTitle":"AAPL Options Greeks","table":{"asOf":null,"headers":{},"rows":[
        {"cDelta":-0.11847,"cGamma":0.00615,"cRho":-0.04500,"cTheta":-0.07954,"cVega":0.22285,"cIV":0.00000,"strike":315.00,"pDelta":0.0,"pGamma":0.0,"pRho":0.0,"pTheta":0.0,"pVega":0.0,"pIV":0.0,"url":"/market-activity/stocks/aapl/option-chain/call-put-options/aapl--261218p00315000"},
        {"cDelta":0.85964,"cGamma":0.00626,"cRho":0.27287,"cTheta":-0.13130,"cVega":0.25027,"cIV":0.00000,"strike":315.00,"pDelta":-0.11847,"pGamma":0.00615,"pRho":-0.04500,"pTheta":-0.07954,"pVega":0.22285,"pIV":0.00000,"url":"/market-activity/stocks/aapl/option-chain/call-put-options/aapl--261218c00315000"},
        {"cDelta":0.5,"cGamma":0.0,"strike":350.50,"url":"/market-activity/stocks/aapl/option-chain/call-put-options/aapl--261218c00350500"}
    ]}},"status":{"rCode":200}}"""

    @Test fun griechenLesenOrdnetCallUndPutZu() {
        val verfall = 1_797_552_000L // 18.12.2026 00:00 UTC
        val m = assertNotNull(NasdaqGriechen.lesen(antwort, verfall))
        assertEquals(0.00615, m[NasdaqGriechen.schluessel(verfall, 315.0, false)]!!, 0.0)
        assertEquals(0.00626, m[NasdaqGriechen.schluessel(verfall, 315.0, true)]!!, 0.0)
        // Gamma 0 und fehlende Werte zaehlen nicht
        assertEquals(2, m.size)
        assertNull(NasdaqGriechen.lesen("kein json", verfall))
        assertNull(NasdaqGriechen.lesen("""{"data":null,"status":{"rCode":400}}""", verfall))
        assertEquals(0, NasdaqGriechen.lesen("""{"data":{"table":{"rows":[]}}}""", verfall)!!.size)
    }

    @Test fun griechenAdresseMitTagUndKlasse() {
        assertEquals("2026-12-18", NasdaqGriechen.tagText(1_797_552_000L))
        assertEquals("2024-02-29", NasdaqGriechen.tagText(1_709_164_800L))
        assertEquals("1970-01-01", NasdaqGriechen.tagText(0))
        assertEquals(
            "https://api.nasdaq.com/api/quote/AAPL/option-chain/greeks?assetclass=stocks&date=2026-12-18",
            NasdaqGriechen.adresse("AAPL", false, 1_797_552_000L),
        )
        assertTrue(NasdaqGriechen.adresse("SPY", true, 1_797_552_000L).contains("assetclass=etf"))
    }

    // Nasdaq-Gamma ersetzt die Rechnung nur bei den Kontrakten, die es fuehrt; das Etikett folgt daraus
    @Test fun ebenenNutzenNasdaqGamma() {
        val verfall = jetzt + 30 * tag
        val kette = listOf(
            Kontrakt(true, 105.0, verfall, 1.0, 0.9, 1.1, 10, 1000, 0.3),
            Kontrakt(true, 110.0, verfall, 1.0, 0.9, 1.1, 10, 1000, 0.3),
        )
        val ohne = assertNotNull(OptionsEbenen.berechnen(100.0, kette, jetzt, 100.0))
        assertEquals(0, ohne.strikesEcht)
        assertEquals(2, ohne.strikesNaeherung)
        val mit = assertNotNull(OptionsEbenen.berechnen(100.0, kette, jetzt, 100.0, mapOf(NasdaqGriechen.schluessel(verfall, 105.0, true) to 0.02)))
        val s105 = mit.strikes.first { it.strike == 105.0 }
        assertEquals(1000 * 0.02 * 100.0 * 100.0 * 0.01, s105.gexCalls, 1e-9)
        assertEquals(1, s105.echt)
        assertEquals(0, s105.schaetz)
        assertEquals(1, mit.strikesEcht)
        assertEquals(1, mit.strikesNaeherung)
        // Der Strike ohne Nasdaq-Wert rechnet wie zuvor
        assertEquals(ohne.strikes.first { it.strike == 110.0 }.gexCalls, mit.strikes.first { it.strike == 110.0 }.gexCalls, 1e-12)
    }

    // Nasdaq-Gamma passt zur eigenen Rechnung: AAPL 348,51 USD, Verfall in 37 Tagen, Call 350 mit Gamma 0,01528 bei
    // einem Mittelkurs von 10,35 USD (Abruf vom 11.11.2026)
    @Test fun nasdaqGammaPasstZuBlackScholes() {
        val t = 37.0 / 365
        var lo = 0.01
        var hi = 3.0
        repeat(60) {
            val m = (lo + hi) / 2
            if (Pricing.blackScholes(348.51, 350.0, t, m, true).price > 10.35) hi = m else lo = m
        }
        val g = Pricing.blackScholes(348.51, 350.0, t, lo, true).gamma
        assertEquals(0.01528, g, 0.01528 * 0.03)
    }

    // ------------------------------------------------------------ Analystenstufen

    private val ms = 1_800_000_000_000L
    private val tagMs = 86_400_000L

    private fun u(haus: String, vor: Int, aktion: String, nach: String, von: String) =
        Analystenbilanz.Urteil(haus, ms - vor * tagMs, nach, von, aktion, 120.0, 100.0)

    @Test fun stufenNurNeueMitRichtung() {
        val urteile = listOf(
            u("A", 1, "up", "Buy", "Hold"), u("B", 3, "down", "Sell", "Hold"), u("C", 5, "main", "Buy", "Buy"),
            u("D", 9, "up", "Buy", "Hold"), u("E", 2, "init", "Hold", ""), u("F", 6, "init", "Buy", ""),
        )
        val e = AnalystenStufen.ordnen(AnalystenStufen.neue("XYZ", urteile, ms))
        assertEquals(listOf("A", "B", "F"), e.map { it.haus })
        assertEquals(2, AnalystenStufen.hoch(e))
        assertEquals(1, AnalystenStufen.runter(e))
        assertEquals("XYZ", e[0].symbol)
        assertFalse(e.any { it.haus == "D" })
    }

    // ------------------------------------------------------------ Pressemitteilung

    @Test fun einreichungenNurItem202() {
        val text = """{"filings":{"recent":{
            "form":["8-K","8-K","10-Q","8-K","8-K"],
            "items":["5.02","2.02,9.01","","2.02,9.01","2.02"],
            "filingDate":["2026-11-01","2026-10-30","2026-10-29","2026-07-31","2026-04-30"],
            "accessionNumber":["0000320193-26-000100","0000320193-26-000090","0000320193-26-000089","0000320193-26-000070","0000320193-26-000040"],
            "primaryDocument":["a.htm","b.htm","c.htm","d.htm","e.htm"]}}}"""
        val l = Zahlenmitteilung.einreichungen(text)
        assertEquals(listOf("0000320193-26-000090", "0000320193-26-000070"), l.map { it.acc })
        assertEquals("2026-10-30", l[0].tag)
        assertEquals(3, Zahlenmitteilung.einreichungen(text, 5).size)
        assertTrue(Zahlenmitteilung.einreichungen("kaputt").isEmpty())
    }

    @Test fun exhibitDateiGefunden() {
        val index = """{"directory":{"item":[{"name":"0000320193-26-000090-index.htm"},{"name":"aapl-20261030.htm"},{"name":"a8-kex991q4fy26.htm"},{"name":"aapl-20261030.xsd"}]}}"""
        assertEquals("a8-kex991q4fy26.htm", Zahlenmitteilung.exhibitDatei(index))
        assertEquals("ex99-1.htm", Zahlenmitteilung.exhibitDatei("""{"x":[{"name":"main.htm"},{"name":"ex99-1.htm"}]}"""))
        assertEquals("ex-99.htm", Zahlenmitteilung.exhibitDatei("""{"x":[{"name":"main.htm"},{"name":"ex-99.htm"}]}"""))
        assertNull(Zahlenmitteilung.exhibitDatei("""{"x":[{"name":"main.htm"}]}"""))
    }

    private fun mitteilung(umsatz: String, eps: String, gewinn: String) = """
        <html><head><style>p{color:red}</style></head><body>
        <p>CUPERTINO, California Apple today announced financial results for its fiscal 2026 fourth quarter &amp; year.</p>
        <p>The Company posted quarterly revenue of &#36;102.5 billion, up 6 percent from the year ago quarter and well above plan.</p>
        <table>
        <tr><td></td><td colspan="2">Three Months Ended</td></tr>
        <tr><td>Net sales:</td><td></td><td></td></tr>
        <tr><td>Products</td><td>&#36;</td><td>80,000</td><td>&#36;</td><td>77,000</td></tr>
        <tr><td>Total net sales</td><td>&#36;</td><td>$umsatz</td><td>&#36;</td><td>94,930</td></tr>
        <tr><td>Operating income</td><td>&#160;</td><td>32,427</td><td></td><td>29,591</td></tr>
        <tr><td>Net income</td><td>&#36;</td><td>$gewinn</td><td>&#36;</td><td>14,736</td></tr>
        <tr><td>Earnings per share:</td></tr>
        <tr><td>Basic</td><td>&#36;</td><td>1.66</td></tr>
        <tr><td>Diluted</td><td>&#36;</td><td>$eps</td><td>&#36;</td><td>0.97</td></tr>
        <tr><td>Shares used in computing earnings per share:</td></tr>
        <tr><td>Diluted</td><td>15,242,853</td><td>15,343,783</td></tr>
        <tr><td>Net cash used in operating activities</td><td>(1,234</td><td>)</td></tr>
        <tr><td>Guidance for next quarter</td><td>between 85 and 90 billion</td></tr>
        </table>
        <p>Forward-looking statements contain risks and uncertainties that could cause actual results to differ.</p>
        </body></html>"""

    @Test fun mitteilungLesenKennzahlenUndText() {
        val m = Zahlenmitteilung.lesen(mitteilung("102,466", "1.64", "27,466"))
        val nach = m.kennzahlen.associateBy { it.art }
        assertEquals("102,466", nach[Zahlenmitteilung.Art.UMSATZ]!!.wert)
        assertEquals("Total net sales", nach[Zahlenmitteilung.Art.UMSATZ]!!.label)
        assertEquals(32427.0, nach[Zahlenmitteilung.Art.BETRIEB]!!.zahl, 0.0)
        assertEquals("27,466", nach[Zahlenmitteilung.Art.GEWINN]!!.wert)
        // Gewinn je Aktie aus der Zeile Diluted unter Earnings per share, nicht die Aktienzahl
        assertEquals(1.64, nach[Zahlenmitteilung.Art.EPS]!!.zahl, 0.0)
        // Negativ in Klammern, die Klammer darf in der Nachbarzelle stehen
        assertEquals(-1234.0, nach[Zahlenmitteilung.Art.CASH]!!.zahl, 0.0)
        assertEquals(1, m.ausblick.size)
        assertEquals("between 85 and 90 billion", m.ausblick[0].werte)
        // Text ohne Tabelle, Entitaeten aufgeloest, Haftungsabsatz beendet den Text
        assertEquals(2, m.absaetze.size)
        assertTrue(m.absaetze[0].contains("fourth quarter & year"))
        assertTrue(m.absaetze[1].contains("$102.5 billion"))
    }

    @Test fun vergleichZumVorquartal() {
        val jetztM = Zahlenmitteilung.lesen(mitteilung("102,466", "1.64", "27,466"))
        val vorM = Zahlenmitteilung.lesen(mitteilung("94,930", "1.46", "0"))
        val v = Zahlenmitteilung.vergleichen(jetztM.kennzahlen, vorM.kennzahlen).associateBy { it.kennzahl.art }
        assertEquals((102466.0 - 94930) / 94930 * 100, v[Zahlenmitteilung.Art.UMSATZ]!!.aenderung!!, 1e-9)
        assertEquals((1.64 - 1.46) / 1.46 * 100, v[Zahlenmitteilung.Art.EPS]!!.aenderung!!, 1e-9)
        // Vorwert 0: keine Aenderung, aber der Vorwert wird gezeigt
        assertNull(v[Zahlenmitteilung.Art.GEWINN]!!.aenderung)
        assertEquals("0", v[Zahlenmitteilung.Art.GEWINN]!!.vorher!!.wert)
        // Ohne Vorquartal bleibt alles leer
        assertTrue(Zahlenmitteilung.vergleichen(jetztM.kennzahlen, null).all { it.vorher == null && it.aenderung == null })
    }

    @Test fun zahlenZellen() {
        assertEquals(-1234.0, Zahlenmitteilung.zahl("(1,234)")!!.second, 0.0)
        assertEquals(-5.5, Zahlenmitteilung.zahl("-5.5")!!.second, 0.0)
        assertEquals(1.64, Zahlenmitteilung.zahl("$ 1.64")!!.second, 0.0)
        assertNull(Zahlenmitteilung.zahl("n/a"))
        assertNull(Zahlenmitteilung.zahl("$"))
    }
}
