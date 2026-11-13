package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.StrukturTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Eigentuemer und Rechte: Deckblatt, Hinweise, Schedule 13G und 13D, Absicht, Hauptversammlung, Verteilung, Rechte, Buendel
// und Texte. Auszuege sind kurz und selbst formuliert.
class StrukturTest {

    private fun ctx(id: String, glied: String?) =
        "<xbrli:context id=\"$id\"><xbrli:entity>" +
            (if (glied != null) "<xbrli:segment><xbrldi:explicitMember dimension=\"us-gaap:StatementClassOfStockAxis\">$glied</xbrldi:explicitMember></xbrli:segment>" else "") +
            "</xbrli:entity></xbrli:context>"

    @Test
    fun deckblattMehrereKlassen() {
        val html = ctx("c-1", "us-gaap:CommonClassAMember") + ctx("c-2", "us-gaap:CommonClassBMember") +
            "<ix:nonNumeric contextRef=\"c-1\" name=\"dei:Security12bTitle\">Class&#160;A Common Stock, \$0.001 par value</ix:nonNumeric>" +
            "<ix:nonNumeric contextRef=\"c-1\" name=\"dei:TradingSymbol\">ABC</ix:nonNumeric>" +
            "<ix:nonFraction unitRef=\"shares\" contextRef=\"c-1\" name=\"dei:EntityCommonStockSharesOutstanding\" scale=\"6\" format=\"ixt:num-dot-decimal\">5,888</ix:nonFraction>" +
            "<ix:nonFraction unitRef=\"shares\" contextRef=\"c-2\" name=\"dei:EntityCommonStockSharesOutstanding\" scale=\"0\">1,200</ix:nonFraction>"
        val d = StrukturRegeln.deckblatt(html)
        assertEquals(1, d.titel.size)
        assertEquals("Class A Common Stock, \$0.001 par value", d.titel[0].titel)
        assertEquals("ABC", d.titel[0].kuerzel)
        assertEquals(5.888e9, d.anzahl.first { it.klasse == "A" }.stueck, 1.0)
        assertEquals(1200.0, d.anzahl.first { it.klasse == "B" }.stueck, 1e-9)
        val g = StrukturRegeln.gattungen(d, emptySet())
        assertEquals(listOf("A", "B"), g.map { it.id })
        assertFalse(g[1].boerse)
        // Mehrere Klassen: die Zahl der Stimmen bleibt offen
        assertNull(g[0].stimmen)
        assertEquals(1200.0, g[1].aktien!!, 1e-9)
    }

    @Test
    fun deckblattEineGattungHatEineStimme() {
        val html = ctx("c-1", null) +
            "<ix:nonNumeric contextRef=\"c-1\" name=\"dei:Security12bTitle\">Common Stock, \$0.01 par value</ix:nonNumeric>" +
            "<ix:nonNumeric contextRef=\"c-1\" name=\"dei:TradingSymbol\">XYZ</ix:nonNumeric>" +
            "<ix:nonNumeric contextRef=\"c-2\" name=\"dei:Security12bTitle\">2.5% Senior Notes due 2030</ix:nonNumeric>" +
            "<ix:nonFraction contextRef=\"c-1\" name=\"dei:EntityCommonStockSharesOutstanding\" scale=\"0\">100,000,000</ix:nonFraction>"
        // Ohne gelesenen Standardsatz bleibt die Zahl der Stimmen offen
        val ohne = StrukturRegeln.gattungen(StrukturRegeln.deckblatt(html), emptySet())
        assertEquals(1, ohne.size)
        assertNull(ohne[0].stimmen)
        assertEquals(1e8, ohne[0].aktien!!, 1e-9)
        // Mit dem Satz "entitled to one vote" im Dokument gilt der Normalfall
        val g = StrukturRegeln.gattungen(StrukturRegeln.deckblatt(html), setOf(Struktur.Flag.EINE_STIMME))
        assertEquals(1.0, g[0].stimmen!!, 1e-9)
        // Mit Hinweis auf Mehrfachstimmrechte bleibt die Zahl offen
        val h = StrukturRegeln.gattungen(StrukturRegeln.deckblatt(html), setOf(Struktur.Flag.EINE_STIMME, Struktur.Flag.MEHRSTIMMEN))
        assertNull(h[0].stimmen)
    }

    @Test
    fun hinterlegungsscheinMitVerhaeltnis() {
        val html = ctx("c-1", null) +
            "<ix:nonNumeric contextRef=\"c-1\" name=\"dei:Security12bTitle\">American Depositary Shares, each representing eight Class A ordinary shares</ix:nonNumeric>" +
            "<ix:nonNumeric contextRef=\"c-1\" name=\"dei:TradingSymbol\">ZZZ</ix:nonNumeric>"
        val g = StrukturRegeln.gattungen(StrukturRegeln.deckblatt(html), setOf(Struktur.Flag.ADR))
        assertEquals(Struktur.GArt.ADS, g[0].art)
        assertEquals(8.0, g[0].ads!!, 1e-9)
        assertFalse(g[0].wirtschaftlich)
    }

    @Test
    fun hinweiseAusDemText() {
        val s = StrukturRegeln.hinweise("Each share of Class B has ten votes per share. The Class C is non-voting. We rely on variable interest entities. American Depositary Shares. A golden share exists.")
        assertEquals(setOf(Struktur.Flag.MEHRSTIMMEN, Struktur.Flag.OHNE_STIMME, Struktur.Flag.VIE, Struktur.Flag.ADR, Struktur.Flag.GOLDEN), s)
        assertTrue(StrukturRegeln.hinweise("Plain common stock only.").isEmpty())
        assertTrue(Struktur.Flag.EINE_STIMME in StrukturRegeln.hinweise("Each holder of common stock is entitled to one vote per share."))
        assertTrue(Struktur.Flag.MEHRSTIMMEN in StrukturRegeln.hinweise("Class B carries five votes per share."))
    }

    @Test
    fun schedule13gUnd13dXml() {
        val g = StrukturRegeln.meldung13("<x><securitiesClassTitle>Common Stock</securitiesClassTitle><reportingPersonName>The Vanguard Group</reportingPersonName><classPercent>7.5</classPercent></x>")!!
        assertEquals("The Vanguard Group", g.name)
        assertEquals(7.5, g.prozent, 1e-9)
        // Ausstieg: Anteil null
        assertNull(StrukturRegeln.meldung13("<x><reportingPersonName>A</reportingPersonName><classPercent>0</classPercent></x>"))
        val d = StrukturRegeln.meldung13(
            "<x><dateOfEvent>11/10/2026</dateOfEvent><reportingPersonName>Fonds X LP</reportingPersonName><percentOfClass>4.9</percentOfClass>" +
                "<item4><transactionPurpose>Item 4 text.</transactionPurpose></item4></x>",
        )!!
        assertEquals("2026-11-10", d.ereignis)
        assertEquals(4.9, d.prozent, 1e-9)
        assertEquals("Item 4 text.", d.zweck)
    }

    @Test
    fun treffer13JeMelder() {
        val json = "{\"hits\":{\"hits\":[" +
            "{\"_source\":{\"ciks\":[\"0001326801\",\"0000315066\"],\"display_names\":[\"Meta Platforms, Inc.  (META)  (CIK 0001326801)\",\"FMR LLC  (CIK 0000315066)\"],\"adsh\":\"0000315066-26-002069\",\"file_date\":\"2026-08-06\"}}," +
            "{\"_source\":{\"ciks\":[\"0001326801\",\"0000102909\"],\"display_names\":[\"Meta Platforms, Inc.  (META)  (CIK 0001326801)\",\"VANGUARD GROUP INC  (CIK 0000102909)\"],\"adsh\":\"0000102909-26-001886\",\"file_date\":\"2026-03-27\"}}," +
            "{\"_source\":{\"ciks\":[\"0001326801\",\"0000102909\"],\"display_names\":[\"Meta Platforms, Inc.  (META)  (CIK 0001326801)\",\"VANGUARD GROUP INC  (CIK 0000102909)\"],\"adsh\":\"0000102909-25-000100\",\"file_date\":\"2025-02-01\"}}" +
            "]}}"
        val je = StrukturRegeln.treffer13(json, "0001326801")
        assertEquals(2, je.size)
        assertEquals("FMR LLC", je[0].melder)
        assertEquals("Vanguard Group Inc", je[1].melder)
        assertEquals("0000102909-26-001886", je[1].adsh)
        assertEquals(3, StrukturRegeln.treffer13(json, "0001326801", alle = true).size)
    }

    @Test
    fun absichtDesAktivisten() {
        assertEquals(listOf(Struktur.Absicht.BOARD), StrukturAktion.absicht("The Reporting Person intends to nominate two director candidates for election to the Board of Directors."))
        assertEquals(
            listOf(Struktur.Absicht.BETEILIGUNG),
            StrukturAktion.absicht("The shares were acquired for investment purposes. The Reporting Person has no present plans or proposals that relate to the matters in Item 4."),
        )
        val v = StrukturAktion.absicht("The Reporting Person delivered a letter to the Issuer demanding that it explore strategic alternatives including a sale of the Issuer and a spin-off of its consumer division.")
        assertTrue(Struktur.Absicht.VERKAUF in v)
        assertTrue(Struktur.Absicht.ABSPALTUNG in v)
        assertTrue(Struktur.Absicht.STRATEGIE in v)
        assertTrue(Struktur.Absicht.RUECKKAUF in StrukturAktion.absicht("The Reporting Person proposes that the Issuer increase its share repurchase program."))
        assertFalse(StrukturAktion.aktiv(listOf(Struktur.Absicht.BETEILIGUNG)))
    }

    @Test
    fun aktivistMitVerlauf() {
        val neu = Struktur.Verlauf("2026-10-01", 4.9, listOf(Struktur.Absicht.BOARD), 1)
        val alt = Struktur.Verlauf("2026-06-01", 3.0, listOf(Struktur.Absicht.BETEILIGUNG), 0)
        val a = StrukturAktion.aktivist("Fonds X", listOf(neu, alt))!!
        assertEquals(4.9, a.aktuell.prozent!!, 1e-9)
        assertEquals(1, a.verlauf.size)
        assertNull(StrukturAktion.aktivist("Fonds Y", listOf(alt)))
    }

    @Test
    fun hauptversammlungAus8K() {
        val html = "<p>Item 5.07 Submission of Matters to a Vote of Security Holders.</p><p>Proposal 1 - Election of Directors</p>" +
            "<table><tr><td>Nominee</td><td>For</td><td>Withheld</td><td>Broker Non-Votes</td></tr>" +
            "<tr><td>Jane Doe</td><td>900</td><td>100</td><td>50</td></tr><tr><td>John Roe</td><td>500</td><td>500</td><td>50</td></tr></table>" +
            "<p>Proposal 3 - Advisory vote on executive compensation</p>" +
            "<table><tr><td>For</td><td>Against</td><td>Abstain</td><td>Broker Non-Votes</td></tr><tr><td>Result</td><td>710</td><td>290</td><td>10</td><td>0</td></tr></table>" +
            "<p>Proposal 5 - Shareholder proposal regarding reporting</p>" +
            "<table><tr><td>For</td><td>Against</td><td>Abstain</td><td>Broker Non-Votes</td></tr><tr><td>Result</td><td>300</td><td>700</td><td>0</td><td>0</td></tr></table>" +
            "<p>Item 9.01 Exhibits</p>"
        val b = StrukturAktion.beschluesse(html)
        assertEquals(4, b.size)
        assertEquals(listOf("Jane Doe", "John Roe"), b.filter { it.art == Struktur.BArt.WAHL }.map { it.titel })
        assertEquals(90.0, b[0].zustimmung!!, 1e-9)
        val verg = b.first { it.art == Struktur.BArt.VERGUETUNG }
        assertEquals(71.0, verg.zustimmung!!, 1e-9)
        assertEquals(10.0, verg.enthaltung, 1e-9)
        val antrag = b.first { it.art == Struktur.BArt.ANTRAG }
        assertEquals(30.0, antrag.zustimmung!!, 1e-9)
        val k = StrukturAktion.kern(Struktur.Versammlung("2026-05-20", b))
        assertEquals(71.0, k.verguetung!!, 1e-9)
        assertEquals(0, k.angenommen)
        assertEquals(1, k.abgelehnt)
        assertEquals(1, k.schwach)
        assertTrue(StrukturAktion.auffaellig(b[1]))
        assertFalse(StrukturAktion.auffaellig(verg))
        assertTrue(StrukturAktion.auffaellig(Struktur.Beschluss("x", Struktur.BArt.ANTRAG, 600.0, 400.0)))
    }

    @Test
    fun vorratssaetzeMachenKeinenAktivisten() {
        val vorrat = "The Reporting Persons may from time to time consider a merger, a sale of the Issuer, a spin-off, share repurchases, changes to the board of directors and nominating directors, " +
            "including, without limitation, strategic alternatives. The Reporting Persons reserve the right to take any or all of these actions."
        assertEquals(listOf(Struktur.Absicht.BETEILIGUNG), StrukturAktion.absicht(vorrat))
        // Dieselben Stichwoerter in einer konkreten Handlung zaehlen
        assertEquals(listOf(Struktur.Absicht.BOARD), StrukturAktion.absicht("The Reporting Person has nominated three candidates for election to the Board of Directors."))
        assertTrue(Struktur.Absicht.STRATEGIE in StrukturAktion.absicht("The parties entered into a cooperation agreement on governance matters."))
        // Konkreter Satz neben dem Vorratssatz: nur der konkrete zaehlt
        val gemischt = StrukturAktion.absicht(vorrat + " On October 1 the Reporting Person delivered a letter urging a share repurchase.")
        assertEquals(listOf(Struktur.Absicht.RUECKKAUF), gemischt)
    }

    @Test
    fun klassenspaltenAusDerTabelle() {
        val html = "<p>Security Ownership of Certain Beneficial Owners and Management. Beneficial ownership.</p><table>" +
            "<tr><td>Name</td><td colspan=\"2\">Class A Shares</td><td colspan=\"2\">Class B Shares</td><td>% of Total Voting Power</td></tr>" +
            "<tr><td></td><td>Shares</td><td>%</td><td>Shares</td><td>%</td><td></td></tr>" +
            "<tr><td>Larry Founder</td><td>1,000</td><td>*</td><td>500,000</td><td>46.5</td><td>27.4</td></tr>" +
            "<tr><td>Sergey Founder</td><td>2,000</td><td>*</td><td>400,000</td><td>42.9</td><td>25.3</td></tr>" +
            "<tr><td>All executive officers and directors as a group</td><td>3,000</td><td>*</td><td>900,000</td><td>89.4</td><td>60.0</td></tr></table>"
        val zeilen = IpoProspekt.eignerSpalten(html)!!
        assertEquals(3, zeilen.size)
        val gattungen = listOf(Struktur.Gattung("A", "Class A", stimmen = 1.0, aktien = 800.0), Struktur.Gattung("B", "Class B", stimmen = 10.0, aktien = 200.0))
        val h = StrukturRegeln.halterAusSpalten(zeilen, gattungen, 0)!!
        assertEquals("Larry Founder", h[0].name)
        assertEquals("B", h[0].gattung)
        assertEquals(46.5, h[0].klasse!!, 1e-9)
        assertEquals(27.4, h[0].stimmen!!, 1e-9)
        assertEquals(Struktur.Art.GRUPPE, h[2].art)
        assertEquals(60.0, h[2].stimmen!!, 1e-9)
        // Kapital aus dem Anteil an der Klasse: 46,5 % von 20 % aller Aktien
        val v = StrukturRegeln.verteilung(gattungen)
        assertEquals(9.3, StrukturRegeln.halterAnteile(h[0], v).first!!, 1e-9)
        // Ohne erkennbare Spalten bleibt es beim einfachen Leser
        assertNull(StrukturRegeln.halterAusSpalten(listOf(IpoProspekt.SpaltenZeile("X", true, false, listOf("percent of shares" to 5.0))), gattungen, 0))
    }

    @Test
    fun mehrereKlassenAddierenSichImAnteil() {
        val gattungen = listOf(Struktur.Gattung("A", "A", stimmen = 1.0, aktien = 800.0), Struktur.Gattung("B", "B", stimmen = 10.0, aktien = 200.0))
        val h = Struktur.Halter("Fonds", Struktur.Art.FONDS, klassen = mapOf("A" to 50.0, "B" to 10.0))
        val v = StrukturRegeln.verteilung(gattungen)
        assertEquals(42.0, StrukturRegeln.halterAnteile(h, v).first!!, 1e-9)
        // Stimmen: 50 % von 800 plus 10 % von 2000 gegen 2800
        assertEquals((400.0 + 200.0) * 100.0 / 2800.0, StrukturRegeln.halterAnteile(h, v).second!!, 1e-9)
    }

    @Test
    fun halterDerAktieMitGerechnetemAnteil() {
        val e = Struktur.Ergebnis(
            gattungen = listOf(Struktur.Gattung("A", "A", stimmen = 1.0, aktien = 800.0), Struktur.Gattung("B", "B", stimmen = 10.0, aktien = 200.0)),
            halter = listOf(
                Struktur.Halter("Gründer", Struktur.Art.PERSON, gattung = "B", klasse = 100.0, beleg = 0),
                Struktur.Halter("", Struktur.Art.STREUBESITZ, kapital = 80.0),
            ),
            belege = listOf(Struktur.Beleg("SEC DEF 14A", "https://www.sec.gov/x", "2026-04-01")),
        )
        val liste = de.troetenrepublik.app.halterAusErgebnis(e)
        assertEquals(1, liste.size)
        assertEquals(20.0, liste[0].kapital!!, 1e-9)
        assertEquals("SEC DEF 14A", liste[0].quelle!!.name)
    }

    private fun berkshire() = Struktur.Ergebnis(
        firma = "Berkshire",
        gattungen = listOf(
            Struktur.Gattung("A", "Class A", symbole = listOf("BRK-A"), stimmen = 1.0, aktien = 511820.0),
            Struktur.Gattung("B", "Class B", symbole = listOf("BRK-B"), stimmen = 0.0001, aktien = 1389605139.0, gewicht = 1.0 / 1500.0),
        ),
        halter = listOf(Struktur.Halter("Warren Buffett", Struktur.Art.PERSON, kapital = 13.7, stimmen = 30.2)),
    )

    @Test
    fun verteilungMitGewichtUndStimmen() {
        val v = StrukturRegeln.verteilung(berkshire().gattungen)
        assertEquals(35.58, v.kapital["A"]!!, 0.05)
        assertEquals(78.65, v.stimmen["A"]!!, 0.05)
        assertEquals(100.0, v.stimmen["A"]!! + v.stimmen["B"]!!, 1e-9)
        val r = StrukturRegeln.rechte(berkshire(), "BRK-B", "", 1500.0)!!
        assertEquals("B", r.gattung.id)
        assertEquals(0.0001, r.stimmenJeAktie!!, 1e-12)
        assertEquals(StrukturRegeln.Board.JA, r.board)
        assertEquals(1500.0 * (1.0 / 1500.0) * 100.0 / (511820.0 + 1389605139.0 / 1500.0), r.eigen!!.kapital!!, 1e-9)
    }

    @Test
    fun rechteMitKontrolleOhneStimmeUndNichtGeprueft() {
        val e = Struktur.Ergebnis(
            gattungen = listOf(
                Struktur.Gattung("ST", "Stammaktie", symbole = listOf("VOW.DE"), stimmen = 1.0, aktien = 295089818.0),
                Struktur.Gattung("VZ", "Vorzugsaktie", symbole = listOf("VOW3.DE"), art = Struktur.GArt.VORZUG, stimmen = 0.0, aktien = 206205445.0),
            ),
            halter = listOf(Struktur.Halter("Porsche SE", Struktur.Art.HOLDING, kapital = 31.9, stimmen = 53.3)),
        )
        val st = StrukturRegeln.rechte(e, "VOW.DE", "", null)!!
        assertEquals(StrukturRegeln.Board.NEIN_KONTROLLE, st.board)
        assertEquals("Porsche SE", st.kontrolle)
        assertEquals(100.0, st.stimmenAnteil!!, 1e-9)
        val vz = StrukturRegeln.rechte(e, "VOW3.DE", "", 10.0)!!
        assertEquals(StrukturRegeln.Board.NEIN_OHNE_STIMME, vz.board)
        assertTrue(vz.vorzugOhneStimme)
        assertEquals(0.0, vz.stimmenAnteil!!, 1e-9)
        // Ohne Stimmen je Aktie: nicht geprueft
        val offen = Struktur.Ergebnis(gattungen = listOf(Struktur.Gattung("A", "Class A", symbole = listOf("AAA")), Struktur.Gattung("B", "Class B", aktien = 5.0)))
        val r = StrukturRegeln.rechte(offen, "AAA", "", null)!!
        assertNull(r.stimmenJeAktie)
        assertNull(r.stimmenAnteil)
        assertEquals(StrukturRegeln.Board.UNBEKANNT, r.board)
    }

    @Test
    fun hinterlegungsscheinNimmtDieRechteDerAktie() {
        val e = Struktur.Ergebnis(
            gattungen = listOf(
                Struktur.Gattung("O", "Ordinary", stimmen = 1.0, aktien = 1000.0),
                Struktur.Gattung("ADS", "ADS", symbole = listOf("ZZZ"), art = Struktur.GArt.ADS, wirtschaftlich = false, ads = 8.0, basis = "O"),
            ),
            flags = listOf(Struktur.Flag.ADR, Struktur.Flag.VIE),
        )
        val r = StrukturRegeln.rechte(e, "ZZZ", "", 10.0)!!
        assertTrue(r.ads)
        assertTrue(r.vie)
        assertEquals(1.0, r.stimmenJeAktie!!, 1e-9)
        // 10 ADS sind 80 Aktien von 1000
        assertEquals(8.0, r.eigen!!.kapital!!, 1e-9)
        assertEquals(8.0, r.eigen!!.stimmen!!, 1e-9)
    }

    @Test
    fun anteileAusKlasseUndStreubesitz() {
        val e = Struktur.Ergebnis(
            gattungen = listOf(
                Struktur.Gattung("A", "A", stimmen = 1.0, aktien = 800.0),
                Struktur.Gattung("B", "B", stimmen = 10.0, aktien = 200.0),
            ),
            halter = listOf(Struktur.Halter("Gründer", Struktur.Art.PERSON, gattung = "B", klasse = 100.0)),
        )
        val v = StrukturRegeln.verteilung(e.gattungen)
        // B hat 2000 von 2800 Stimmen
        val (k, s) = StrukturRegeln.halterAnteile(e.halter[0], v)
        assertEquals(20.0, k!!, 1e-9)
        assertEquals(71.43, s!!, 0.01)
        assertEquals(StrukturRegeln.kontrolleVon(e.halter, v), "Gründer")
        val (sk, ss) = StrukturRegeln.streubesitz(e.halter, v)
        assertEquals(80.0, sk!!, 1e-9)
        assertEquals(28.57, ss!!, 0.01)
        assertTrue(StrukturRegeln.weichtAb(k, s))
        assertFalse(StrukturRegeln.weichtAb(10.0, 10.01))
    }

    @Test
    fun wikidataEigentuemerMitAnteil() {
        val json = "{\"entities\":{\"Q1\":{\"claims\":{\"P127\":[" +
            "{\"mainsnak\":{\"datavalue\":{\"value\":{\"id\":\"Q2\"}}},\"qualifiers\":{\"P1107\":[{\"datavalue\":{\"value\":{\"amount\":\"+0.25\"}}}]}}," +
            "{\"mainsnak\":{\"datavalue\":{\"value\":{\"id\":\"Q3\"}}}}," +
            "{\"mainsnak\":{\"datavalue\":{\"value\":{\"id\":\"Q4\"}}},\"qualifiers\":{\"P582\":[{}]}}" +
            "]}}}}"
        val e = StrukturRegeln.wikidataEigner(json, "Q1")
        assertEquals(listOf("Q2", "Q3"), e.map { it.id })
        assertEquals(25.0, e[0].prozent!!, 1e-9)
        assertNull(e[1].prozent)
    }

    @Test
    fun zusammenfuehrenGepflegtGegenAbruf() {
        val auto = Struktur.Ergebnis(
            firma = "Auto", gattungen = listOf(Struktur.Gattung("A", "Class A", aktien = 100.0), Struktur.Gattung("B", "Class B", aktien = 5.0)),
            halter = listOf(Struktur.Halter("Fonds", Struktur.Art.FONDS)),
        )
        val g = Struktur.Sonderfall(
            id = "x", symbole = listOf("AAA"), stand = "2026-01-01", ablauf = "2026-06-01",
            eintrag = Struktur.Ergebnis(
                firma = "Gepflegt", gattungen = listOf(Struktur.Gattung("A", "Class A", stimmen = 1.0), Struktur.Gattung("B", "Class B", stimmen = 10.0)),
                halter = listOf(Struktur.Halter("Gründer", Struktur.Art.PERSON, stimmen = 60.0)),
            ),
        )
        val z = StrukturRegeln.zusammen(g, auto, "2026-11-13")!!
        assertEquals("Gepflegt", z.firma)
        assertEquals(100.0, z.gattungen[0].aktien!!, 1e-9)
        assertEquals(10.0, z.gattungen[1].stimmen!!, 1e-9)
        assertEquals(listOf("Gründer"), z.halter.map { it.name })
        assertTrue(z.gepflegt)
        assertTrue(z.veraltet)
        assertFalse(StrukturRegeln.zusammen(g, auto, "2026-03-01")!!.veraltet)
        assertEquals(auto, StrukturRegeln.zusammen(null, auto, "2026-03-01"))
        assertTrue(StrukturRegeln.passt(g, "AAA", "", ""))
        assertFalse(StrukturRegeln.passt(g, "BBB", "", ""))
    }

    // ------------------------------------------------------------ gepflegtes Buendel

    private fun buendel(): Struktur.Buendel {
        val datei = java.io.File("src/main/assets/struktur/sonderfaelle.json")
        return Struktur.buendelLesen(datei.readText(Charsets.UTF_8))!!
    }

    @Test
    fun buendelIstLesbarUndBelegt() {
        val b = buendel()
        assertTrue(b.eintraege.size >= 20)
        for (s in b.eintraege) {
            val e = s.eintrag
            assertTrue(s.id, s.symbole.isNotEmpty())
            assertTrue(s.id, s.stand.isNotBlank() && s.ablauf.isNotBlank())
            assertTrue(s.id, e.belege.isNotEmpty() && e.belege.all { (it.url ?: "").startsWith("https://") })
            val n = e.belege.size
            for (g in e.gattungen) assertTrue("${s.id} ${g.id}", g.beleg in -1 until n)
            for (h in e.halter) assertTrue("${s.id} ${h.name}", h.beleg in -1 until n && (h.name.isNotBlank() || h.art == Struktur.Art.GRUPPE || h.art == Struktur.Art.STREUBESITZ))
            // Jeder Hinweistext in beiden Sprachen, ohne Gedankenstrich und Semikolon
            val texte = e.notizen + e.gattungen.flatMap { it.notizen + listOfNotNull(it.dividende, it.board) } + e.halter.mapNotNull { it.notiz }
            for (t in texte) {
                assertTrue(s.id, t.de.isNotBlank() && t.en.isNotBlank())
                assertFalse(s.id + t.de, t.de.contains('—') || t.de.contains(';'))
                assertFalse(s.id + t.en, t.en.contains('—') || t.en.contains(';'))
            }
            // Die Gattung der Seite findet sich ueber das erste Kuerzel
            assertNotNull(s.id, StrukturRegeln.rechte(e, s.symbole[0], s.isins.firstOrNull().orEmpty(), null))
        }
    }

    @Test
    fun buendelFindetDieFaelleDerAufgabe() {
        val b = buendel()
        for ((symbol, id) in listOf(
            "GOOGL" to "alphabet", "META" to "meta", "BRK-B" to "berkshire", "BRK.A" to "berkshire", "VOW3.DE" to "volkswagen", "PAH3.DE" to "porschese",
            "P911.DE" to "porscheag", "HEN3.DE" to "henkel", "SRT3.DE" to "sartorius", "FPE3.DE" to "fuchs", "BABA" to "alibaba", "PDD" to "pdd", "JD" to "jd",
            "BIDU" to "baidu", "SNAP" to "snap", "F" to "ford", "NKE" to "nike", "UAA" to "underarmour", "SPOT" to "spotify", "NOVO-B.CO" to "novonordisk",
            "RO.SW" to "roche", "ANT" to "anthropic",
        )) assertEquals(symbol, id, StrukturRegeln.gepflegt(b, symbol, "", "")?.id)
    }

    @Test
    fun kapitalNebenStimmenBeiMetaAlphabetUndPorscheAg() {
        val b = buendel().eintraege
        val z = b.first { it.id == "meta" }.eintrag.halter.first { it.name == "Mark Zuckerberg" }
        assertEquals(13.5, z.kapital!!, 1e-9)
        assertEquals(60.8, z.stimmen!!, 1e-9)
        assertEquals(5.84, b.first { it.id == "alphabet" }.eintrag.halter.first { it.name == "Larry Page" }.kapital!!, 1e-9)
        val pag = b.first { it.id == "porscheag" }.eintrag
        assertEquals(75.4, pag.halter.first { it.name == "Volkswagen AG" }.kapital!!, 1e-9)
        // Kapital der Halter ergibt hoechstens 100
        assertEquals(100.0, pag.halter.sumOf { it.kapital ?: 0.0 }, 0.05)
        assertTrue(pag.belege.any { (it.url ?: "").contains("porsche-am-kapitalmarkt") })
    }

    @Test
    fun summenDerBuendelHalterSindHoechstens100() {
        for (s in buendel().eintraege) {
            val v = StrukturRegeln.verteilung(s.eintrag.gattungen)
            val ohneGruppe = s.eintrag.halter.filter { it.art != Struktur.Art.GRUPPE }
            val k = ohneGruppe.sumOf { StrukturRegeln.halterAnteile(it, v).first ?: 0.0 }
            val st = ohneGruppe.sumOf { StrukturRegeln.halterAnteile(it, v).second ?: 0.0 }
            assertTrue("${s.id} Kapital $k", k <= 100.5)
            assertTrue("${s.id} Stimmen $st", st <= 100.5)
        }
    }

    @Test
    fun berkshireBStimmrechtIstBelegt() {
        val b = buendel().eintraege.first { it.id == "berkshire" }.eintrag
        val r = StrukturRegeln.rechte(b, "BRK-B", "", null)!!
        assertEquals(0.0001, r.stimmenJeAktie!!, 1e-12)
        assertEquals(StrukturRegeln.Board.JA, r.board)
    }

    @Test
    fun volkswagenVorzugOhneStimmeUndPorscheKontrolliert() {
        val e = buendel().eintraege.first { it.id == "volkswagen" }.eintrag
        val vz = StrukturRegeln.rechte(e, "VOW3.DE", "", null)!!
        assertTrue(vz.vorzugOhneStimme)
        val st = StrukturRegeln.rechte(e, "VOW.DE", "", null)!!
        assertEquals("Porsche Automobil Holding SE", st.kontrolle)
    }

    @Test
    fun anthropicKlasseFHatDieHaelfte() {
        val e = buendel().eintraege.first { it.id == "anthropic" }.eintrag
        val v = StrukturRegeln.verteilung(e.gattungen)
        assertEquals(50.1, v.stimmen["F"]!!, 1e-9)
        assertEquals(49.9, v.stimmen["A"]!!, 1e-9)
        assertEquals(0.0, v.stimmen["N"]!!, 1e-9)
        assertEquals(44.5, v.kapital["N"]!!, 0.05)
        assertEquals(StrukturRegeln.Board.NEIN_KONTROLLE, StrukturRegeln.rechte(e, "ANT", "", null)!!.board)
    }

    // ------------------------------------------------------------ Texte

    @Test
    fun textePassenInBeideSprachen() {
        assertEquals(StrukturTexte.schluessel(true), StrukturTexte.schluessel(false))
        for (k in StrukturTexte.schluessel(true)) {
            assertTrue(k, StrukturTexte.text(true, k).isNotBlank())
            assertTrue(k, StrukturTexte.text(false, k).isNotBlank())
            assertFalse(k, StrukturTexte.text(true, k).let { it.contains('—') || it.contains(';') })
            assertFalse(k, StrukturTexte.text(false, k).let { it.contains('—') || it.contains(';') })
        }
        val schluessel = StrukturTexte.schluessel(true)
        for (a in listOf("PERSON", "FAMILIE", "STIFTUNG", "HOLDING", "STAAT", "FONDS", "INSTITUTIONEN", "STREUBESITZ", "GRUPPE", "MUTTER", "TRUST")) assertTrue(a, "art.$a" in schluessel)
        for (a in listOf("STAMM", "VORZUG", "ADS", "GENUSS", "ZERTIFIKAT")) assertTrue(a, "gart.$a" in schluessel)
        for (a in listOf("BOARD", "VERKAUF", "ABSPALTUNG", "RUECKKAUF", "STRATEGIE", "BETEILIGUNG")) assertTrue(a, "akt.$a" in schluessel)
        for (a in listOf("WAHL", "VERGUETUNG", "PRUEFER", "ANTRAG", "SONST")) assertTrue(a, "hv.$a" in schluessel)
        for (a in listOf("MEHRSTIMMEN", "OHNE_STIMME", "GOLDEN")) assertTrue(a, "flag.$a" in schluessel)
        assertEquals("Eigentümer und Rechte", StrukturTexte.text(true, "titel"))
        assertEquals("Ownership and rights", StrukturTexte.text(false, "titel"))
        assertEquals("Aktivist Fonds X mit 4,9 %, fordert Board-Sitze", StrukturTexte.text(true, "kern.aktivist", listOf("Fonds X", "4,9 %", "fordert Board-Sitze")))
    }
}
