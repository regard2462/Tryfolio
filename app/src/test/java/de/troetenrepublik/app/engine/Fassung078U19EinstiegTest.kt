package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.CopyUebersicht.Geschaeft
import de.troetenrepublik.app.market.SecInsider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Einstiegskurs mit Kaeufen und Verkaeufen (0.78): Verkaeufe mindern den Bestand, nach vollstaendigem Verkauf beginnt
// der Einstieg neu; bei Insidern zaehlen nur Kaeufe und Verkaeufe am Markt.
class Fassung078U19EinstiegTest {

    private val T = CopyUebersicht.TAG
    private val jetzt = 1_000 * T

    private fun g(kauf: Boolean, tageVorJetzt: Int, betrag: Double, preis: Double? = null, person: String = "a", symbol: String = "X") =
        Geschaeft(person, person.uppercase(), symbol, kauf, jetzt - tageVorJetzt * T, betrag, preis)

    // Kurse: Tag 900 = 10, Tag 950 = 20, heute (Tag 1000) = 15
    private val kurse = KongressAuswertung.Kurse.aus(mapOf(900L to 10.0, 950L to 20.0, 1000L to 15.0))
    private fun kurs(s: String, t: Long): Double? = if (s == "X") kurse.am(t) else null
    private fun heute(s: String): Double? = if (s == "X") kurse.letzter else null
    private val ohneSplit: (String, Long, Long) -> Double = { _, _, _ -> 1.0 }

    private fun rechne(l: List<Geschaeft>, aktuell: (String) -> Double? = ::heute) = CopyUebersicht.einstaende(l, ::kurs, aktuell, ohneSplit)

    @Test
    fun teilverkaufLaesstEinstiegUndMindertBestand() {
        // Kauf 3.000 bei 10, Verkauf 1.200 ohne Preis: 40 % weg, Einstieg bleibt 10, Rest 1.800
        val e = rechne(listOf(g(true, 100, 3_000.0), g(false, 60, 1_200.0))).single()
        assertEquals(10.0, e.einstieg, 1e-9)
        assertEquals(1_800.0, e.betrag, 1e-9)
        assertEquals(1, e.kaeufe)
        assertEquals(0.5, e.abstand, 1e-9)
    }

    @Test
    fun kaufNachTeilverkaufWiegtGegenDenRest() {
        // Kauf 3.000 bei 10, Verkauf 2.000 (Rest 1.000 bei 10), Kauf 1.000 bei 20: (1000*10 + 1000*20) / 2000 = 15
        val e = rechne(listOf(g(true, 100, 3_000.0), g(false, 70, 2_000.0), g(true, 50, 1_000.0))).single()
        assertEquals(15.0, e.einstieg, 1e-9)
        assertEquals(2_000.0, e.betrag, 1e-9)
        assertEquals(2, e.kaeufe)
        assertEquals(jetzt - 100 * T, e.erstes)
        assertEquals(jetzt - 50 * T, e.letztes)
        // heute 15: genau am Einstieg, nicht darunter
        assertTrue(CopyUebersicht.unterEinstieg(listOf(e)).isEmpty())
    }

    @Test
    fun allesVerkauftFaelltWeg() {
        assertTrue(rechne(listOf(g(true, 100, 3_000.0), g(false, 60, 3_000.0))).isEmpty())
        // mehr verkauft als gekauft (Bestand aus der Zeit vor den Daten): ebenfalls weg
        assertTrue(rechne(listOf(g(true, 100, 3_000.0), g(false, 60, 9_000.0))).isEmpty())
    }

    @Test
    fun neukaufNachKomplettverkaufBeginntNeu() {
        // Kauf bei 10, alles verkauft, Kauf bei 20: Einstieg 20 statt des Mittels 12,5; heute 15, also 25 % darunter
        val e = rechne(listOf(g(true, 100, 3_000.0), g(false, 70, 3_000.0), g(true, 50, 1_000.0))).single()
        assertEquals(20.0, e.einstieg, 1e-9)
        assertEquals(1_000.0, e.betrag, 1e-9)
        assertEquals(1, e.kaeufe)
        assertEquals(jetzt - 50 * T, e.erstes)
        assertEquals(-0.25, e.abstand, 1e-9)
        assertEquals(listOf("X"), CopyUebersicht.unterEinstieg(listOf(e)).map { it.symbol })
    }

    @Test
    fun verkaufVorDemErstenKaufBleibtOhneWirkung() {
        val e = rechne(listOf(g(false, 120, 9_000.0), g(true, 100, 3_000.0))).single()
        assertEquals(10.0, e.einstieg, 1e-9)
        assertEquals(3_000.0, e.betrag, 1e-9)
    }

    @Test
    fun amSelbenTagKaufVorVerkauf() {
        // Verkauf steht in der Liste zuerst, zaehlt aber nach dem Kauf desselben Tages
        val e = rechne(listOf(g(false, 100, 1_000.0), g(true, 100, 4_000.0))).single()
        assertEquals(3_000.0, e.betrag, 1e-9)
    }

    @Test
    fun verkaufMitPreisZaehltInStueck() {
        // 100 Stueck zu 10 gekauft, 50 Stueck zu 20 verkauft (gleicher Betrag): die Haelfte bleibt, Einstieg 10
        val halb = rechne(listOf(g(true, 100, 1_000.0, 10.0), g(false, 50, 1_000.0, 20.0))).single()
        assertEquals(10.0, halb.einstieg, 1e-9)
        assertEquals(500.0, halb.betrag, 1e-9)
        // 100 Stueck zu 10 gekauft, 100 Stueck zu 5 verkauft (halber Betrag): nichts bleibt. Ohne Schlusskurse, damit
        // der Preis 5 nicht als unerklaert gilt
        assertTrue(CopyUebersicht.einstiege(listOf(g(true, 100, 1_000.0, 10.0), g(false, 50, 500.0, 5.0)), { _, _ -> null }, ::heute, ohneSplit).gehalten.isEmpty())
    }

    @Test
    fun verkaufMitPreisNachSplitUmgerechnet() {
        // 100 Stueck zu 40 vor einem Split 1:4 (heute 400 Stueck zu 10), danach 200 Stueck zu 20 verkauft: die Haelfte bleibt
        val splitTag = jetzt - 80 * T
        val faktor: (String, Long, Long) -> Double = { _, nach, _ -> if (nach < splitTag) 4.0 else 1.0 }
        val l = listOf(g(true, 100, 4_000.0, 40.0), g(false, 50, 4_000.0, 20.0))
        val e = CopyUebersicht.einstaende(l, ::kurs, ::heute, faktor).single()
        assertEquals(10.0, e.einstieg, 1e-9)
        assertEquals(2_000.0, e.betrag, 1e-9)
    }

    @Test
    fun personenUndAktienGetrennt() {
        val l = listOf(
            g(true, 100, 3_000.0, person = "a"), g(false, 60, 3_000.0, person = "a"),
            g(true, 50, 1_000.0, person = "b"),
        )
        val e = rechne(l).single()
        assertEquals("b", e.person)
        assertEquals(20.0, e.einstieg, 1e-9)
        // Gruppe: nur b zaehlt, heute 15 gegen 20
        val a = CopyUebersicht.abschlaege(listOf(e)).single()
        assertEquals(1, a.kaeufer)
        assertEquals(-0.25, a.abstand, 1e-9)
    }

    @Test
    fun kaufOhneKursZaehltZumBestandNichtZumEinstieg() {
        // Kauf vor dem ersten Kurs (2.000) und Kauf bei 20 (1.000); Verkauf 1.500 nimmt die Haelfte von beidem
        val e = rechne(listOf(g(true, 500, 2_000.0), g(true, 50, 1_000.0), g(false, 20, 1_500.0))).single()
        assertEquals(20.0, e.einstieg, 1e-9)
        assertEquals(500.0, e.betrag, 1e-9)
        assertEquals(1, e.kaeufe)
    }

    @Test
    fun form4NurKaufUndVerkaufAmMarkt() {
        fun zeile(code: String, tag: Int, stueck: Int, preis: String, nach: Int) = "<nonDerivativeTransaction><transactionDate><value>2026-03-0$tag</value></transactionDate>" +
            "<transactionCoding><transactionCode>$code</transactionCode></transactionCoding>" +
            "<transactionAmounts><transactionShares><value>${stueck * 100}</value></transactionShares>" +
            "<transactionPricePerShare><value>$preis</value></transactionPricePerShare></transactionAmounts>" +
            "<postTransactionAmounts><sharesOwnedFollowingTransaction><value>$nach</value></sharesOwnedFollowingTransaction></postTransactionAmounts>" +
            "<ownershipNature><directOrIndirectOwnership><value>D</value></directOrIndirectOwnership></ownershipNature></nonDerivativeTransaction>"
        val xml = "<ownershipDocument><issuer><issuerCik>0000000001</issuerCik><issuerName>A Corp</issuerName><issuerTradingSymbol>AAA</issuerTradingSymbol></issuer>" +
            "<reportingOwner><reportingOwnerId><rptOwnerName>Meier Anna</rptOwnerName></reportingOwnerId>" +
            "<reportingOwnerRelationship><isDirector>0</isDirector><isOfficer>1</isOfficer><officerTitle>CEO</officerTitle></reportingOwnerRelationship></reportingOwner>" +
            "<nonDerivativeTable>" +
            // Zuteilung (A), Optionsausuebung (M), Schenkung (G), Steuerabzug (F): keine Kaeufe am Markt
            zeile("A", 1, 1, "0", 1_000) + zeile("M", 1, 2, "3", 1_200) + zeile("G", 2, 3, "0", 900) + zeile("F", 2, 4, "30", 500) +
            zeile("P", 3, 5, "20", 1_000) + zeile("S", 5, 2, "40", 800) +
            "</nonDerivativeTable></ownershipDocument>"
        val l = SecInsider.form4(xml, "0000000001-26-000001", "2026-03-06")
        assertEquals(2, l.size)
        val kauf = l.single { it.kauf }
        assertEquals(500.0, kauf.n, 1e-9)
        assertEquals(20.0, kauf.preis, 1e-9)
        val verkauf = l.single { !it.kauf }
        assertEquals(200.0, verkauf.n, 1e-9)
        // Einstieg nur aus dem Kauf: 500 Stueck zu 20, davon 200 verkauft; heute 15
        val (trades, _) = CopyUebersicht.ausInsidern(l)
        val e = CopyUebersicht.einstaende(trades, { _, _ -> null }, { 15.0 }, ohneSplit).single()
        assertEquals(20.0, e.einstieg, 1e-9)
        assertEquals(6_000.0, e.betrag, 1e-9)
        assertEquals(1, e.kaeufe)
        assertEquals(-0.25, e.abstand, 1e-9)
    }

    @Test
    fun insiderGeschaeftAusRegister() {
        val k = CopyUebersicht.insiderGeschaeft(" Anna Meier ", "SAP.DE", true, false, "2026-02-03", 50_000.0, 200.0, "EUR", "EUR")
        assertNotNull(k)
        assertEquals("anna meier", k!!.person)
        assertEquals("Anna Meier", k.name)
        assertEquals("SAP.DE", k.symbol)
        assertTrue(k.kauf)
        assertEquals(CopyUebersicht.tag("2026-02-03"), k.datum)
        assertEquals(200.0, k.preis!!, 1e-9)
        // ohne Preis bleibt der Betrag, der Kurs kommt dann vom Tag
        assertNull(CopyUebersicht.insiderGeschaeft("Anna Meier", "SAP.DE", false, true, "2026-02-03", 50_000.0, null, "EUR", "EUR")!!.preis)
        // andere Waehrung als die Kurse, weder Kauf noch Verkauf, ohne Person, ohne Betrag, ohne Tag: nichts
        assertNull(CopyUebersicht.insiderGeschaeft("Anna Meier", "SAP.DE", true, false, "2026-02-03", 50_000.0, 200.0, "USD", "EUR"))
        assertNull(CopyUebersicht.insiderGeschaeft("Anna Meier", "SAP.DE", true, false, "2026-02-03", 50_000.0, 200.0, "", ""))
        assertNull(CopyUebersicht.insiderGeschaeft("Anna Meier", "SAP.DE", false, false, "2026-02-03", 50_000.0, 200.0, "EUR", "EUR"))
        assertNull(CopyUebersicht.insiderGeschaeft("", "SAP.DE", true, false, "2026-02-03", 50_000.0, 200.0, "EUR", "EUR"))
        assertNull(CopyUebersicht.insiderGeschaeft("Anna Meier", "SAP.DE", true, false, "2026-02-03", null, 200.0, "EUR", "EUR"))
        assertNull(CopyUebersicht.insiderGeschaeft("Anna Meier", "SAP.DE", true, false, "", 50_000.0, 200.0, "EUR", "EUR"))
    }

    private fun alles(l: List<Geschaeft>) = CopyUebersicht.einstiege(l, ::kurs, ::heute, ohneSplit)

    @Test
    fun teilverkaufBleibtMitAnteil() {
        // Kauf 3.000 bei 10, Verkauf 1.200 an Tag 940: 40 % reduziert, weiter gehalten, nichts unter verkauft
        val e = alles(listOf(g(true, 100, 3_000.0), g(false, 60, 1_200.0)))
        val h = e.gehalten.single()
        assertEquals(0.4, h.reduziert, 1e-9)
        assertEquals(jetzt - 60 * T, h.verkauftAm)
        assertEquals(false, h.verkauft)
        assertNull(h.verkaufskurs)
        assertTrue(e.verkauft.isEmpty())
        // ohne Verkauf: kein Anteil
        assertEquals(0.0, alles(listOf(g(true, 100, 3_000.0))).gehalten.single().reduziert, 1e-9)
        // zwei Teilverkaeufe und ein Zukauf: 3.000 + 1.000 gekauft, Rest (3.000 * 0,5 + 1.000) * 0,8 = 2.000, also 50 %
        val m = alles(listOf(g(true, 100, 3_000.0), g(false, 80, 1_500.0), g(true, 50, 1_000.0), g(false, 20, 500.0))).gehalten.single()
        assertEquals(0.5, m.reduziert, 1e-9)
        assertEquals(2_000.0, m.betrag, 1e-9)
    }

    @Test
    fun komplettverkaufBleibtSichtbar() {
        // Kauf 3.000 bei 10, an Tag 940 alles verkauft: nicht mehr gehalten, aber unter verkauft mit 100 %
        val e = alles(listOf(g(true, 100, 3_000.0), g(false, 60, 3_000.0)))
        assertTrue(e.gehalten.isEmpty())
        val v = e.verkauft.single()
        assertTrue(v.verkauft)
        assertEquals(1.0, v.reduziert, 1e-9)
        assertEquals(jetzt - 60 * T, v.verkauftAm)
        // letzter Einstiegskurs 10, heute 15: 50 % darueber; ohne Preis in der Meldung kein Verkaufskurs
        assertEquals(10.0, v.einstieg, 1e-9)
        assertEquals(0.5, v.abstand, 1e-9)
        assertNull(v.verkaufskurs)
        // zaehlt weder in „unter Einstiegskurs“ noch in den Gruppenschnitt
        assertTrue(CopyUebersicht.unterEinstieg(e.verkauft).isEmpty())
        assertTrue(CopyUebersicht.abschlaege(e.verkauft).isEmpty())
        // einstaende liefert weiter nur Gehaltenes
        assertTrue(rechne(listOf(g(true, 100, 3_000.0), g(false, 60, 3_000.0))).isEmpty())
    }

    @Test
    fun komplettverkaufMitPreisHatVerkaufskurs() {
        // 100 Stueck zu 20 gekauft; 40 Stueck zu 22 und 60 Stueck zu 24 verkauft: Verkaufskurs (880 + 1440) / 100 = 23,2
        val e = alles(listOf(g(true, 50, 2_000.0, 20.0), g(false, 30, 880.0, 22.0), g(false, 10, 1_440.0, 24.0)))
        assertTrue(e.gehalten.isEmpty())
        val v = e.verkauft.single()
        assertEquals(20.0, v.einstieg, 1e-9)
        assertEquals(23.2, v.verkaufskurs!!, 1e-9)
        assertEquals(jetzt - 10 * T, v.verkauftAm)
        // heute 15 gegen Einstieg 20
        assertEquals(-0.25, v.abstand, 1e-9)
    }

    @Test
    fun neukaufNachKomplettverkaufZeigtBeides() {
        // Runde 1: Kauf bei 10, alles verkauft. Runde 2: Kauf bei 20, 25 % verkauft.
        val l = listOf(g(true, 100, 3_000.0), g(false, 70, 3_000.0), g(true, 50, 1_000.0), g(false, 20, 250.0))
        val e = alles(l)
        val h = e.gehalten.single()
        assertEquals(20.0, h.einstieg, 1e-9)
        assertEquals(0.25, h.reduziert, 1e-9)
        // die abgeschlossene Runde steht genau einmal unter verkauft, mit ihrem eigenen Einstieg und Verkaufstag
        val v = e.verkauft.single()
        assertEquals(10.0, v.einstieg, 1e-9)
        assertEquals(jetzt - 70 * T, v.verkauftAm)
        assertEquals(jetzt - 100 * T, v.erstes)
    }

    @Test
    fun mehrereRundenNurDieJuengsteVerkaufte() {
        // zwei abgeschlossene Runden derselben Person in derselben Aktie: es bleibt die juengste (Einstieg 20)
        val l = listOf(g(true, 100, 3_000.0), g(false, 70, 3_000.0), g(true, 50, 1_000.0), g(false, 20, 1_000.0))
        val e = alles(l)
        assertTrue(e.gehalten.isEmpty())
        val v = e.verkauft.single()
        assertEquals(20.0, v.einstieg, 1e-9)
        assertEquals(jetzt - 20 * T, v.verkauftAm)
    }

    @Test
    fun verkaufOhneKaufDavorErgibtKeinenEintrag() {
        // nur Verkaeufe (Bestand aus der Zeit vor den Daten): kein Einstiegskurs bekannt, also weder gehalten noch verkauft
        val e = alles(listOf(g(false, 60, 3_000.0)))
        assertTrue(e.gehalten.isEmpty())
        assertTrue(e.verkauft.isEmpty())
    }

    @Test
    fun ausstiegeJeAktie() {
        // a und b haben X ganz verkauft (a an Tag 940, b an Tag 980), c haelt noch
        val l = listOf(
            g(true, 100, 3_000.0, person = "a"), g(false, 60, 3_000.0, person = "a"),
            g(true, 50, 1_000.0, person = "b"), g(false, 20, 1_000.0, person = "b"),
            g(true, 50, 1_000.0, person = "c"),
        )
        val e = alles(l)
        assertEquals(listOf("c"), e.gehalten.map { it.person })
        val a = CopyUebersicht.ausstiege(e.verkauft).single()
        assertEquals("X", a.symbol)
        assertEquals(2, a.personen)
        assertEquals(jetzt - 20 * T, a.letzter)
        // Einstieg nach Betrag gewichtet: (3000 * 10 + 1000 * 20) / 4000 = 12,5; heute 15
        assertEquals(12.5, a.einstieg, 1e-9)
        assertEquals(15.0 / 12.5 - 1, a.abstand, 1e-9)
        // juengster Verkaeufer zuerst
        assertEquals(listOf("B", "A"), a.namen.map { it.first })
        // der Gruppenschnitt der Gehaltenen kennt nur c
        assertEquals(1, CopyUebersicht.abschlaege(e.gehalten).single().kaeufer)
    }

    // Verkauf mit Preis und gemeldetem Bestand davor und danach (wie Form 4)
    private fun v(tageVorJetzt: Int, betrag: Double, preis: Double?, vor: Double? = null, nach: Double? = null, person: String = "a", symbol: String = "X") =
        Geschaeft(person, person.uppercase(), symbol, false, jetzt - tageVorJetzt * T, betrag, preis, vor = vor, nach = nach)

    @Test
    fun verkaeuferOhneKaufMitRestbestand() {
        // nie am Markt gekauft: 50 Stueck zu 11 (Bestand 200 -> 150), 50 Stueck zu 22 (150 -> 100)
        val e = alles(listOf(v(60, 550.0, 11.0, 200.0, 150.0), v(20, 1_100.0, 22.0, 150.0, 100.0)))
        assertTrue(e.gehalten.isEmpty())
        assertTrue(e.verkauft.isEmpty())
        val o = e.ohneKauf.single()
        assertEquals("a", o.person)
        assertEquals(1_650.0, o.betrag, 1e-9)
        assertEquals(2, o.verkaeufe)
        assertEquals(jetzt - 20 * T, o.verkauftAm)
        // Verkaufskurs 1.650 / 100 Stueck = 16,5; heute 15; Bestand von 200 auf 100
        assertEquals(16.5, o.verkaufskurs!!, 1e-9)
        assertEquals(15.0 / 16.5 - 1, o.abstand!!, 1e-9)
        assertEquals(0.5, o.reduziert!!, 1e-9)
        // alles abgegeben: 100 %
        assertEquals(1.0, alles(listOf(v(20, 3_000.0, 30.0, 100.0, 0.0))).ohneKauf.single().reduziert!!, 1e-9)
    }

    @Test
    fun verkaeuferOhneKaufOhneRestbestand() {
        // Betragsspanne ohne Preis und ohne Bestand: Summe und Tag, sonst nichts Erfundenes
        val o = alles(listOf(g(false, 60, 8_000.0), g(false, 30, 2_000.0))).ohneKauf.single()
        assertEquals(10_000.0, o.betrag, 1e-9)
        assertEquals(jetzt - 30 * T, o.verkauftAm)
        assertNull(o.verkaufskurs)
        assertNull(o.abstand)
        assertNull(o.reduziert)
        // fehlt der Bestand bei einem der Verkaeufe, gibt es keinen Prozentwert
        assertNull(alles(listOf(v(60, 1_000.0, 20.0, 200.0, 150.0), v(20, 1_500.0, 30.0))).ohneKauf.single().reduziert)
        // unbekannte Aktie ohne heutigen Kurs: Eintrag bleibt, ohne Vergleich
        val z = alles(listOf(v(20, 1_500.0, 30.0, symbol = "Z"))).ohneKauf.single()
        assertEquals(30.0, z.verkaufskurs!!, 1e-9)
        assertNull(z.abstand)
    }

    @Test
    fun ohneKaufKeineDopplung() {
        // abgeschlossene Runde, danach weitere Verkaeufe: ein Eintrag mit Einstiegskurs, keiner ohne
        val runde = alles(listOf(g(true, 100, 3_000.0), g(false, 70, 3_000.0), g(false, 20, 5_000.0)))
        assertEquals(10.0, runde.verkauft.single().einstieg, 1e-9)
        assertEquals(jetzt - 20 * T, runde.verkauft.single().verkauftAm)
        assertTrue(runde.ohneKauf.isEmpty())
        // erst verkauft, dann gekauft und gehalten: nur die gehaltene Position
        val spaeter = alles(listOf(g(false, 120, 9_000.0), g(true, 100, 3_000.0)))
        assertEquals(1, spaeter.gehalten.size)
        assertTrue(spaeter.verkauft.isEmpty())
        assertTrue(spaeter.ohneKauf.isEmpty())
        // erst verkauft, dann gekauft und alles verkauft: nur die Runde
        val beides = alles(listOf(g(false, 120, 9_000.0), g(true, 100, 3_000.0), g(false, 60, 3_000.0)))
        assertEquals(1, beides.verkauft.size)
        assertTrue(beides.ohneKauf.isEmpty())
        // andere Person derselben Aktie bleibt getrennt
        val zwei = alles(listOf(g(true, 100, 3_000.0, person = "a"), g(false, 30, 2_000.0, person = "b")))
        assertEquals(listOf("a"), zwei.gehalten.map { it.person })
        assertEquals(listOf("b"), zwei.ohneKauf.map { it.person })
        // zaehlt nicht in den Gruppenschnitt und nicht in „unter Einstiegskurs“
        assertEquals(1, rechne(listOf(g(true, 100, 3_000.0, person = "a"), g(false, 30, 2_000.0, person = "b"))).size)
    }

    @Test
    fun ohneKaufNurImZeitraum() {
        val l = listOf(v(400, 1_000.0, 20.0), v(30, 1_500.0, 20.0))
        val o = CopyUebersicht.einstiege(l, ::kurs, ::heute, ohneSplit, verkaufAb = jetzt - 365 * T).ohneKauf.single()
        assertEquals(1_500.0, o.betrag, 1e-9)
        assertEquals(1, o.verkaeufe)
        assertTrue(CopyUebersicht.einstiege(listOf(v(400, 1_000.0, 20.0)), ::kurs, ::heute, ohneSplit, verkaufAb = jetzt - 365 * T).ohneKauf.isEmpty())
    }

    @Test
    fun abgabenJeAktie() {
        val l = listOf(
            v(60, 500.0, 10.0, 200.0, 150.0, person = "a"), v(20, 2_000.0, 20.0, 100.0, 0.0, person = "b"),
            v(10, 500.0, null, person = "c", symbol = "Y"),
        )
        val a = CopyUebersicht.abgaben(alles(l).ohneKauf)
        assertEquals(listOf("X", "Y"), a.map { it.symbol })
        assertEquals(2_500.0, a[0].betrag, 1e-9)
        assertEquals(2, a[0].personen)
        assertEquals(jetzt - 20 * T, a[0].letzter)
        // 50 Stueck zu 10 und 100 Stueck zu 20: 2.500 / 150; heute 15
        assertEquals(2_500.0 / 150, a[0].verkaufskurs!!, 1e-9)
        assertEquals(15.0 / (2_500.0 / 150) - 1, a[0].abstand!!, 1e-9)
        // groesster Verkaeufer zuerst, mit gemeldeter Minderung
        assertEquals(listOf("B", "A"), a[0].namen.map { it.first })
        assertEquals(1.0, a[0].namen[0].third!!, 1e-9)
        assertEquals(0.25, a[0].namen[1].third!!, 1e-9)
        // ohne Preis: kein Verkaufskurs, kein Vergleich
        assertNull(a[1].verkaufskurs)
        assertNull(a[1].abstand)
    }

    @Test
    fun form4VerkaufOhneKaufNurCodeS() {
        fun zeile(code: String, tag: Int, stueck: Int, preis: String, nach: Int) = "<nonDerivativeTransaction><transactionDate><value>2026-03-0$tag</value></transactionDate>" +
            "<transactionCoding><transactionCode>$code</transactionCode></transactionCoding>" +
            "<transactionAmounts><transactionShares><value>$stueck</value></transactionShares>" +
            "<transactionPricePerShare><value>$preis</value></transactionPricePerShare></transactionAmounts>" +
            "<postTransactionAmounts><sharesOwnedFollowingTransaction><value>$nach</value></sharesOwnedFollowingTransaction></postTransactionAmounts>" +
            "<ownershipNature><directOrIndirectOwnership><value>D</value></directOrIndirectOwnership></ownershipNature></nonDerivativeTransaction>"
        val xml = "<ownershipDocument><issuer><issuerCik>0000000001</issuerCik><issuerName>A Corp</issuerName><issuerTradingSymbol>AAA</issuerTradingSymbol></issuer>" +
            "<reportingOwner><reportingOwnerId><rptOwnerName>Meier Anna</rptOwnerName></reportingOwnerId>" +
            "<reportingOwnerRelationship><isDirector>0</isDirector><isOfficer>1</isOfficer><officerTitle>CEO</officerTitle></reportingOwnerRelationship></reportingOwner>" +
            "<nonDerivativeTable>" +
            // Zuteilung (A), Ausuebung (M), Abgabe fuer Steuern (F), Schenkung (G): keine Verkaeufe am Markt
            zeile("A", 1, 500, "0", 1_500) + zeile("M", 1, 300, "3", 1_800) + zeile("F", 2, 400, "30", 1_400) + zeile("G", 2, 400, "0", 1_000) +
            zeile("S", 3, 200, "40", 800) +
            "</nonDerivativeTable></ownershipDocument>"
        val l = SecInsider.form4(xml, "0000000001-26-000002", "2026-03-04")
        val s = l.single()
        assertEquals(false, s.kauf)
        assertEquals(200.0, s.n, 1e-9)
        assertEquals(8_000.0, s.wert, 1e-9)
        val (trades, _) = CopyUebersicht.ausInsidern(l)
        val e = CopyUebersicht.einstiege(trades, { _, _ -> null }, { 50.0 }, ohneSplit)
        assertTrue(e.gehalten.isEmpty())
        assertTrue(e.verkauft.isEmpty())
        val o = e.ohneKauf.single()
        // nur der Verkauf am Markt: 200 Stueck zu 40, Bestand von 1.000 auf 800
        assertEquals(8_000.0, o.betrag, 1e-9)
        assertEquals(40.0, o.verkaufskurs!!, 1e-9)
        assertEquals(0.25, o.abstand!!, 1e-9)
        assertEquals(0.2, o.reduziert!!, 1e-9)
    }

    @Test
    fun unerklaerterPreisNimmtSchlusskurs() {
        // Meldung nennt 2.000 je Stueck (alte Stueckelung vor einer unbekannten Zusammenlegung 100:1), Schlusskurs des
        // Tages 20, keine Splitdaten: Einstieg 20 statt 2.000, der Betrag bleibt
        val e = rechne(listOf(g(true, 50, 10_000.0, 2_000.0))).single()
        assertEquals(20.0, e.einstieg, 1e-9)
        assertEquals(10_000.0, e.betrag, 1e-9)
        // heute 15: 25 % darunter statt 99 %
        assertEquals(-0.25, e.abstand, 1e-9)
        // bekannte Zusammenlegung 100:1 danach (aus 100 Stueck damals wird 1): Preis 0,2 entspricht heute 20
        val zusammen: (String, Long, Long) -> Double = { _, _, _ -> 0.01 }
        val z = CopyUebersicht.einstaende(listOf(g(true, 50, 1_000.0, 0.2)), ::kurs, ::heute, zusammen).single()
        assertEquals(20.0, z.einstieg, 1e-9)
        assertEquals(1_000.0, z.betrag, 1e-9)
        // mehrere Zusammenlegungen, von denen die Liste nur eine kennt: Preis bleibt unerklaert, Schlusskurs gilt
        val teil: (String, Long, Long) -> Double = { _, _, _ -> 0.1 }
        val t = CopyUebersicht.einstaende(listOf(g(true, 50, 1_000.0, 0.02)), ::kurs, ::heute, teil).single()
        assertEquals(20.0, t.einstieg, 1e-9)
        // kleine Abweichung (Kauf im Tagesverlauf): der gemeldete Preis bleibt
        assertEquals(22.0, rechne(listOf(g(true, 50, 1_000.0, 22.0))).single().einstieg, 1e-9)
    }

    @Test
    fun unpruefbarerPreisZaehltNicht() {
        // Kauf vor dem ersten Kurs (Tag 500), Preis 2.000: ohne Splitdaten faellt der Kauf aus der Rechnung
        val l = listOf(g(true, 500, 10_000.0, 2_000.0))
        assertTrue(CopyUebersicht.einstiege(l, ::kurs, ::heute, ohneSplit, splitBekannt = { false }).gehalten.isEmpty())
        // mit Splitdaten gilt der umgerechnete Preis
        assertEquals(2_000.0, CopyUebersicht.einstiege(l, ::kurs, ::heute, ohneSplit, splitBekannt = { true }).gehalten.single().einstieg, 1e-9)
        // ein zweiter, pruefbarer Kauf traegt den Einstieg allein; der unpruefbare zaehlt weiter zum Bestand
        val zwei = CopyUebersicht.einstiege(l + g(true, 50, 1_000.0, 20.0), ::kurs, ::heute, ohneSplit, splitBekannt = { false }).gehalten.single()
        assertEquals(20.0, zwei.einstieg, 1e-9)
        assertEquals(1, zwei.kaeufe)
        // echter Absturz bleibt sichtbar: Kauf bei 20 (Schlusskurs 20), heute 0,2
        val absturz = CopyUebersicht.einstaende(listOf(g(true, 50, 1_000.0, 20.0)), ::kurs, { 0.2 }, ohneSplit).single()
        assertEquals(-0.99, absturz.abstand, 1e-9)
    }

    @Test
    fun unerklaerterVerkaufspreis() {
        // Verkauf ohne Kauf davor mit Preis 3.000 bei Schlusskurs 20 (Tag 980): Verkaufskurs 20, Summe bleibt
        val o = alles(listOf(v(20, 30_000.0, 3_000.0))).ohneKauf.single()
        assertEquals(20.0, o.verkaufskurs!!, 1e-9)
        assertEquals(30_000.0, o.betrag, 1e-9)
        assertEquals(-0.25, o.abstand!!, 1e-9)
    }
}
