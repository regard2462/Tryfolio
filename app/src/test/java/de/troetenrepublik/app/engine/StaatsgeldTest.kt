package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.StaatsgeldTexte
import de.troetenrepublik.app.market.FirmaName
import de.troetenrepublik.app.market.Staatsgeld
import de.troetenrepublik.app.market.Staatsgeld.Art
import de.troetenrepublik.app.market.Staatsgeld.Quelle
import de.troetenrepublik.app.ui.staatsgeldUsdJe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// Staatsgeld: Leser (USAspending, TED, Contracts Finder, EU-Haushalt), Zuordnung der Namen und Auswertung. Auszuege sind kurz
// und selbst formuliert.
class StaatsgeldTest {

    private val lockheed = FirmaName.schluessel(listOf("Lockheed Martin"))

    @Test
    fun usaMutterWirdGewaehlt() {
        val text = """{"results":[
            {"id":"a-C","name":"SIKORSKY AIRCRAFT CORPORATION","recipient_level":"C","amount":9.0e9},
            {"id":"b-P","name":"LOCKHEED MARTIN CORP","recipient_level":"P","amount":5.0e10},
            {"id":"c-C","name":"LOCKHEED MARTIN CORPORATION","recipient_level":"C","amount":1.5e10},
            {"id":"d-R","name":"LOCKHEED MARTIN ENERGY LOAN BORROWER LLC","recipient_level":"R","amount":0.0},
            {"id":"e-R","name":"LOCKHEED MARTIN CORPORATION","recipient_level":"R","amount":1.0e6}]}"""
        val liste = Staatsgeld.usaEmpfaengerLesen(text)
        assertEquals(5, liste.size)
        // Mutter mit dem hoechsten Betrag; Toechter unter eigenem Namen und fremde Zusaetze zaehlen nicht
        assertEquals("b-P", Staatsgeld.usaEmpfaengerWaehlen(liste, lockheed)?.id)
        assertNull(Staatsgeld.usaEmpfaengerWaehlen(liste, FirmaName.schluessel(listOf("Sikorsky"))))
        assertNull(Staatsgeld.usaEmpfaengerWaehlen(liste, emptyList()))
        assertTrue(Staatsgeld.usaEmpfaengerLesen("kein json").isEmpty())
    }

    @Test
    fun haushaltsmonateZuKalendermonaten() {
        // Haushaltsjahr beginnt im Oktober: Monat 1 ist der Oktober des Vorjahres, Monat 12 der September
        assertEquals(2025 to 10, Staatsgeld.kalender(2026, 1))
        assertEquals(2025 to 12, Staatsgeld.kalender(2026, 3))
        assertEquals(2026 to 1, Staatsgeld.kalender(2026, 4))
        assertEquals(2025 to 9, Staatsgeld.kalender(2025, 12))
    }

    @Test
    fun usaVerlaufNachArt() {
        val text = """{"group":"month","results":[
            {"aggregated_amount":1,"time_period":{"fiscal_year":"2025","month":"12"},"Contract_Obligations":100.0,"Direct_Obligations":0,"Grant_Obligations":5.0,"Idv_Obligations":0,"Loan_Obligations":0,"Other_Obligations":0},
            {"aggregated_amount":1,"time_period":{"fiscal_year":"2026","month":"4"},"Contract_Obligations":40.0,"Direct_Obligations":2.0,"Grant_Obligations":3.0,"Idv_Obligations":0,"Loan_Obligations":900.0,"Other_Obligations":1.0},
            {"aggregated_amount":0,"time_period":{"fiscal_year":"2026","month":"2"},"Contract_Obligations":0,"Direct_Obligations":0,"Grant_Obligations":0,"Loan_Obligations":0,"Other_Obligations":0}]}"""
        val p = Staatsgeld.usaVerlaufLesen(text)
        // Nullwerte entfallen
        assertEquals(5, p.size)
        val sep = p.filter { it.jahr == 2025 && it.monat == 9 }
        assertEquals(100.0, sep.first { it.art == Art.AUFTRAG }.betrag, 1e-9)
        assertEquals(5.0, sep.first { it.art == Art.ZUSCHUSS }.betrag, 1e-9)
        val jan = p.filter { it.jahr == 2026 && it.monat == 1 }
        // Zuschuesse mit Direktzahlungen und sonstigen Hilfen
        assertEquals(6.0, jan.first { it.art == Art.ZUSCHUSS }.betrag, 1e-9)
        assertEquals(900.0, jan.first { it.art == Art.KREDIT }.betrag, 1e-9)
        assertTrue(p.all { it.quelle == Quelle.USA && it.waehrung == "USD" })
        assertTrue(Staatsgeld.usaVerlaufLesen("{").isEmpty())
    }

    @Test
    fun usaKategorienOhneBetragEntfallen() {
        val text = """{"results":[{"name":"Department of Defense","amount":79.0e9},{"name":"NASA","amount":0.0},{"name":"  ","amount":5.0},{"name":"Department of Energy","amount":2.5e9}]}"""
        val l = Staatsgeld.usaKategorieLesen(text)
        assertEquals(listOf("Department of Defense", "Department of Energy"), l.map { it.first })
    }

    @Test
    fun anfragekoerper() {
        assertTrue(Staatsgeld.usaEmpfaengerKoerper("lockheed martin").contains("\"keyword\":\"lockheed martin\""))
        val v = Staatsgeld.usaVerlaufKoerper("b-P", "2021-01-01", "2026-11-14")
        assertTrue(v.contains("\"recipient_id\":\"b-P\"") && v.contains("\"group\":\"month\"") && v.contains("\"11\""))
        val t = Staatsgeld.tedKoerper("deutsche telekom", LocalDate.of(2024, 1, 1), 2)
        assertTrue(t.contains("winner-name=\\\"deutsche telekom*\\\"") && t.contains("publication-date>=20240101") && t.contains("\"page\":2"))
        val u = Staatsgeld.ukKoerper("serco", LocalDate.of(2024, 1, 1))
        assertTrue(u.contains("\"keyword\":\"serco\"") && u.contains("\"publishedFrom\":\"2024-01-01\"") && u.contains("\"statuses\":[\"Awarded\"]"))
    }

    @Test
    fun suchwortOhneRechtsform() {
        assertEquals("lockheed martin", Staatsgeld.suchwort("Lockheed Martin Corp"))
        assertEquals("deutsche telekom", Staatsgeld.suchwort("Deutsche Telekom AG"))
    }

    @Test
    fun entschluesselnHtmlZeichen() {
        assertEquals("Peterborough & \"Co\"", Staatsgeld.entschluesseln("Peterborough &amp; &quot;Co&quot;"))
    }

    @Test
    fun tedNurAlleinigeGewinnerMitBetrag() {
        val text = """{"totalNoticeCount":5,"notices":[
            {"publication-number":"1-2025","publication-date":"2025-03-14+01:00","winner-name":{"deu":["Deutsche Telekom Services GmbH"]},"total-value":839389,"tender-value-cur":["EUR"],"buyer-name":{"deu":["Bundesamt &amp; Co"]}},
            {"publication-number":"2-2025","publication-date":"2025-04-02+01:00","winner-name":{"deu":["Deutsche Telekom AG","Fremde Firma GmbH"]},"total-value":100000,"tender-value-cur":["EUR"]},
            {"publication-number":"3-2025","publication-date":"2025-05-02+01:00","winner-name":{"deu":["Deutsche Telekom AG","Deutsche Telekom AG"]},"tender-value-cur":["EUR"]},
            {"publication-number":"4-2024","publication-date":"2024-05-02+01:00","winner-name":{"deu":["Deutsche Telekom AG"],"eng":["Deutsche Telekom AG"]},"total-value":500000,"tender-value-cur":["EUR"]},
            {"publication-number":"5-2025","publication-date":"2025-06-01+01:00","winner-name":{"deu":["Deutsche Telekom AG"]},"total-value":70000}]}"""
        val s = Staatsgeld.tedLesen(text)!!
        assertEquals(5, s.gesamt)
        assertEquals(5, s.zuschlaege.size)
        assertEquals("Bundesamt & Co", s.zuschlaege[0].kaeufer)
        // Doppelte Namen aus mehreren Sprachen zaehlen einmal
        assertEquals(listOf("Deutsche Telekom AG"), s.zuschlaege[3].gewinner)
        val telekom = FirmaName.schluessel(listOf("Deutsche Telekom"))
        val p = Staatsgeld.tedPosten(s.zuschlaege, telekom)
        // Nummer 2 hat einen fremden Mitgewinner, 3 keinen Betrag, 5 keine Waehrung
        assertEquals(2, p.size)
        assertEquals(839389.0, p[0].betrag, 1e-9)
        assertEquals(3, p[0].monat)
        assertEquals("EUR", p[0].waehrung)
        assertEquals(Quelle.TED, p[0].quelle)
        // Abgeschnittene Liste: nur Jahre ab dem Grenzjahr
        assertEquals(1, Staatsgeld.tedPosten(s.zuschlaege, telekom, abJahr = 2025).size)
        assertNull(Staatsgeld.tedLesen("{\"x\":1}"))
        assertEquals("https://ted.europa.eu/en/notice/-/detail/1-2025", Staatsgeld.tedAdresse("1-2025"))
    }

    @Test
    fun ukNurEinLieferantMitBetrag() {
        val text = """{"hitCount":5,"noticeList":[
            {"item":{"id":"g1","noticeIdentifier":"N1","organisationName":"Kent County Council","awardedSupplier":"Serco Group plc","awardedValue":9142634,"awardedDate":"2025-12-19T00:00:00+00:00","publishedDate":"2025-12-20T10:00:00+00:00"}},
            {"item":{"id":"g1b","noticeIdentifier":"N1","organisationName":"Kent County Council","awardedSupplier":"Serco Group plc","awardedValue":9142634,"awardedDate":"2025-12-19T00:00:00+00:00"}},
            {"item":{"id":"g2","noticeIdentifier":"N2","organisationName":"DWP","awardedSupplier":"Maximus UK Services Limited,Serco Limited","awardedValue":2769913640,"awardedDate":"2025-12-22T00:00:00+00:00"}},
            {"item":{"id":"g3","noticeIdentifier":"N3","organisationName":"Home Office","awardedSupplier":"Serco Limited","awardedValue":1,"awardedDate":"2026-11-09T00:00:00+00:00"}},
            {"item":{"id":"g4","noticeIdentifier":"N4","organisationName":"Plymouth City Council","awardedSupplier":"Delt Shared Services Ltd","awardedValue":120878,"awardedDate":"2025-01-20T00:00:00+00:00"}},
            {"item":{"id":"g5","noticeIdentifier":"N5","organisationName":"Redcar &amp; Cleveland","awardedSupplier":"Serco Ltd","awardedValue":250000,"publishedDate":"2025-03-28T15:00:00+01:00"}}]}"""
        val l = Staatsgeld.ukLesen(text)
        assertEquals(6, l.size)
        assertEquals("Redcar & Cleveland", l.last().kaeufer)
        val p = Staatsgeld.ukPosten(l, FirmaName.schluessel(listOf("Serco")))
        // N1 einmal; N2 mehrere Lieferanten; N3 Platzhalterbetrag; N4 fremde Firma
        assertEquals(listOf(9142634.0, 250000.0), p.map { it.betrag })
        assertEquals(12, p[0].monat)
        assertEquals(3, p[1].monat)
        assertTrue(p.all { it.quelle == Quelle.UK && it.waehrung == "GBP" && it.art == Art.AUFTRAG })
        assertEquals("https://www.contractsfinder.service.gov.uk/notice/g1", Staatsgeld.ukAdresse("g1"))
    }

    private val euText = """{"format":1,"gebiet":"EU","stand":"2026-11-14","jahr0":2022,"jahre":3,"firmen":[
        {"y":["SIE.DE"],"n":"Siemens","f":[0,1000000,2500000],"p":[["Horizon Europe",2000000],["Digital Europe",500000]]},
        {"n":"Alpha Beta Gamma","f":[10,20,30],"p":[]},
        {"n":"Alpha Beta Gamma AG","f":[1,2,3],"p":[]},
        {"n":"Falsch","f":[1,2],"p":[]}]}"""

    @Test
    fun euHaushaltLesenUndZuordnen() {
        val d = Staatsgeld.euLesen(euText)!!
        assertEquals(2022, d.jahr0)
        // Zeile mit falscher Laenge wird verworfen
        assertEquals(setOf("y:SIE.DE", "alphabetagamma"), d.firmen.keys)
        val s = Staatsgeld.euFuer(d, listOf("Siemens AG"), "SIE.DE")
        assertNotNull(s)
        assertEquals(listOf(0.0, 1000000.0, 2500000.0), s!!.betraege)
        assertEquals("Horizon Europe", s.programme.first().first)
        // ohne Kuerzel ueber den Namen, nur wenn eindeutig
        assertNotNull(Staatsgeld.euFuer(d, listOf("Alpha Beta Gamma"), "XYZ"))
        assertNull(Staatsgeld.euFuer(d, listOf("Unbekannt Firma"), "XYZ"))
        assertNull(Staatsgeld.euFuer(null, listOf("Siemens"), "SIE.DE"))
        val p = Staatsgeld.euPosten(d, s)
        assertEquals(listOf(2023, 2024), p.map { it.jahr })
        assertTrue(p.all { it.quelle == Quelle.EU && it.monat == 0 && it.waehrung == "EUR" && it.art == Art.ZUSCHUSS })
        assertNull(Staatsgeld.euLesen("{\"format\":2}"))
        assertNull(Staatsgeld.euLesen("kaputt"))
    }

    @Test
    fun auswertungFensterJahreUndKredite() {
        val heute = LocalDate.of(2026, 11, 14)
        // Die letzten zwoelf vollstaendigen Monate: November 2025 bis Oktober 2026; davor November 2024 bis Oktober 2025
        val p = listOf(
            Staatsgeld.Posten(Quelle.USA, Art.AUFTRAG, 2026, 10, 600.0, "USD"),
            Staatsgeld.Posten(Quelle.USA, Art.AUFTRAG, 2025, 11, 300.0, "USD"),
            // laufender Monat zaehlt noch nicht
            Staatsgeld.Posten(Quelle.USA, Art.AUFTRAG, 2026, 11, 5000.0, "USD"),
            Staatsgeld.Posten(Quelle.USA, Art.ZUSCHUSS, 2026, 2, 100.0, "USD"),
            Staatsgeld.Posten(Quelle.USA, Art.KREDIT, 2026, 3, 7000.0, "USD"),
            Staatsgeld.Posten(Quelle.USA, Art.AUFTRAG, 2025, 5, 450.0, "USD"),
            Staatsgeld.Posten(Quelle.USA, Art.AUFTRAG, 2024, 12, 200.0, "USD"),
            Staatsgeld.Posten(Quelle.TED, Art.AUFTRAG, 2026, 1, 100.0, "EUR", "Bundesamt"),
            Staatsgeld.Posten(Quelle.TED, Art.AUFTRAG, 2026, 2, 50.0, "EUR", "Bundesamt"),
            Staatsgeld.Posten(Quelle.UK, Art.AUFTRAG, 2026, 2, 999.0, "XXX"),
            Staatsgeld.Posten(Quelle.EU, Art.ZUSCHUSS, 2025, 0, 400.0, "EUR"),
        )
        val kurs = { w: String -> when (w) { "USD" -> 1.0; "EUR" -> 2.0; else -> null } }
        val s = Staatsgeld.auswerten(p, heute, kurs, extraAuftraggeber = listOf("DoD" to 900.0), extraProgramme = listOf("Forschung" to 50.0), programmeEu = listOf("Horizon" to 70.0))!!
        // Auftraege 600 + 300 + 2 * 150 = 1200, Zuschuesse 100; Kredit nicht in der Summe
        assertEquals(1300.0, s.zwoelf, 1e-9)
        assertEquals(1200.0, s.auftraege, 1e-9)
        assertEquals(100.0, s.zuschuesse, 1e-9)
        assertEquals(7000.0, s.kredite, 1e-9)
        // Davor: 450 im Mai 2025 (Dezember 2024 liegt im Fenster davor: 200)
        assertEquals(650.0, s.vorher!!, 1e-9)
        assertEquals((1300.0 / 650.0 - 1) * 100, s.aenderung!!, 1e-9)
        // Jahre ohne das laufende, EU-Haushalt in Euro umgerechnet: 2025 = 300 + 450 + 800, 2024 = 200
        assertEquals(listOf(2024 to 200.0, 2025 to 1550.0), s.jahre)
        // Quelle ohne Kurs bleibt aus der Rechnung, die Zeilen nennen USA, TED und EU
        assertEquals(listOf(Quelle.USA, Quelle.TED, Quelle.EU), s.zeilen.map { it.quelle })
        assertEquals(2025, s.zeilen.last().jahr)
        assertEquals(800.0, s.zeilen.last().zuschuesse, 1e-9)
        // Auftraggeber: Behoerde der USA und Besteller aus TED (zwoelf Monate), nach Betrag
        assertEquals(listOf("DoD" to 900.0, "Bundesamt" to 300.0), s.auftraggeber)
        assertEquals(listOf("Horizon" to 70.0, "Forschung" to 50.0), s.programme)
    }

    @Test
    fun auswertungOhneDatenIstNull() {
        val heute = LocalDate.of(2026, 11, 14)
        assertNull(Staatsgeld.auswerten(emptyList(), heute, { 1.0 }))
        // Nur Posten ohne Kurs
        assertNull(Staatsgeld.auswerten(listOf(Staatsgeld.Posten(Quelle.UK, Art.AUFTRAG, 2026, 2, 5.0, "GBP")), heute, usd = { null }))
        // Nur alte Posten: kein Betrag in zwoelf Monaten, aber ein Jahr zum Anzeigen
        val alt = Staatsgeld.auswerten(listOf(Staatsgeld.Posten(Quelle.USA, Art.AUFTRAG, 2023, 5, 80.0, "USD")), heute, usd = { 1.0 })!!
        assertEquals(0.0, alt.zwoelf, 1e-9)
        assertNull(alt.aenderung)
        assertEquals(listOf(2023 to 80.0), alt.jahre)
    }

    @Test
    fun europaeischeRechtsformenUndFremdeNamen() {
        val telekom = FirmaName.schluessel(listOf("Deutsche Telekom"))
        assertTrue(Staatsgeld.passt("Deutsche Telekom GmbH", telekom))
        assertTrue(Staatsgeld.passt("DEUTSCHE TELEKOM AG", telekom))
        assertTrue(Staatsgeld.passt("Deutsche Telekom Services GmbH & Co. KG", telekom))
        // Eigene Unternehmen mit dem Namen als Anfang und Tochter unter fremdem Zusatz zaehlen nicht
        assertFalse(Staatsgeld.passt("Deutsche Telekom Security GmbH", telekom))
        assertFalse(Staatsgeld.passt("Telekom Austria AG", telekom))
        assertFalse(Staatsgeld.passt("Schneider und Partner Computervernetzung GmbH", telekom))
        assertFalse(Staatsgeld.passt("Deutsche Telekom GmbH", emptyList()))
    }

    @Test
    fun anteilAmUmsatz() {
        assertEquals(10.0, Staatsgeld.anteil(100.0, 1000.0)!!, 1e-9)
        assertNull(Staatsgeld.anteil(100.0, null))
        assertNull(Staatsgeld.anteil(100.0, 0.0))
        assertNull(Staatsgeld.anteil(0.0, 1000.0))
        // Mehrjahresbetraege koennen den Umsatz uebersteigen, dann kein Anteil
        assertNull(Staatsgeld.anteil(2000.0, 1000.0))
    }

    @Test
    fun dollarJeWaehrung() {
        val fx = mapOf("USD" to 1.1, "GBP" to 0.85)
        assertEquals(1.0, staatsgeldUsdJe("USD", fx)!!, 1e-9)
        assertEquals(1.1, staatsgeldUsdJe("EUR", fx)!!, 1e-9)
        assertEquals(1.1 / 0.85, staatsgeldUsdJe("GBP", fx)!!, 1e-9)
        assertNull(staatsgeldUsdJe("JPY", fx))
        assertNull(staatsgeldUsdJe("EUR", emptyMap()))
    }

    @Test
    fun texteInBeidenSprachenGleich() {
        assertEquals(StaatsgeldTexte.schluessel(true), StaatsgeldTexte.schluessel(false))
        for (k in StaatsgeldTexte.schluessel(true)) {
            for (deutsch in listOf(true, false)) {
                val t = StaatsgeldTexte.text(deutsch, k)
                assertTrue(k, t.isNotBlank())
                assertFalse(k, t.contains('—') || t.contains(';'))
            }
        }
        // Jede Quelle hat Namen und Hinweis
        for (q in Quelle.values()) {
            assertTrue(q.name, "q.${q.name}" in StaatsgeldTexte.schluessel(true))
            assertTrue(q.name, "q.${q.name}.hinweis" in StaatsgeldTexte.schluessel(true))
        }
        assertEquals("1,2 Mrd. in 12 Monaten", StaatsgeldTexte.text(true, "kern.zwoelf", listOf("1,2 Mrd.")))
        assertEquals("EU budget 2025", StaatsgeldTexte.text(false, "q.EU", listOf("2025")))
    }
}
