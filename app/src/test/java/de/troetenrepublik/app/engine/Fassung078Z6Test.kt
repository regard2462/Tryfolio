package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.Optionsanalyse.Kontrakt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Trend aus Hoch- und Tiefpunkten, Chartformationen an kuenstlichen Kursreihen, Rollen eines verkauften Puts.
class Fassung078Z6Test {

    // Schlusskurse gerade von Stuetzpunkt zu Stuetzpunkt (Kerze, Kurs); Hoch und Tief je einen halben Punkt daneben.
    private class Reihe(val h: List<Double>, val l: List<Double>, val c: List<Double>)

    private fun reihe(vararg punkte: Pair<Int, Double>): Reihe {
        val c = ArrayList<Double>()
        punkte.toList().zipWithNext { a, b ->
            for (i in a.first until b.first) c += a.second + (b.second - a.second) * (i - a.first) / (b.first - a.first)
        }
        c += punkte.last().second
        return Reihe(c.map { it + 0.5 }, c.map { it - 0.5 }, c)
    }

    private fun gespiegelt(r: Reihe) = Reihe(r.l.map { 300 - it }, r.h.map { 300 - it }, r.c.map { 300 - it })

    private val aufwaerts = arrayOf(0 to 100.0, 15 to 80.0, 30 to 110.0, 40 to 95.0, 55 to 130.0, 65 to 115.0, 80 to 150.0)

    @Test fun zerlegungFindetHochUndTiefpunkte() {
        val r = reihe(*aufwaerts)
        val z = Markttechnik.zerlegen(r.h, r.l, r.c)
        // Ab dem ersten Tief die Wendepunkte im Wechsel (davor zaehlt hoechstens der Anfang der Reihe als Hoch)
        val p = z.punkte.filter { it.index >= 15 }
        assertEquals(listOf(15, 30, 40, 55, 65), p.map { it.index })
        assertEquals(listOf(false, true, false, true, false), p.map { it.hoch })
        assertEquals(110.5, p[1].wert, 1e-9)
        assertEquals(94.5, p[2].wert, 1e-9)
        assertEquals(80, z.kandidat?.index)
        assertTrue(z.kandidat!!.hoch)
    }

    @Test fun kleineSchwankungIstKeinPunkt() {
        // Ruecksetzer um zwei Punkte bei einer mittleren Spanne von gut einem Punkt: unter der Mindestgroesse
        val r = reihe(0 to 100.0, 15 to 80.0, 30 to 110.0, 32 to 108.0, 45 to 130.0)
        val z = Markttechnik.zerlegen(r.h, r.l, r.c)
        assertEquals(listOf(15), z.punkte.filter { !it.hoch }.map { it.index })
        assertEquals(45, z.kandidat?.index)
    }

    @Test fun aufwaertstrendMitBruchmarkeAmLetztenTief() {
        val r = reihe(*aufwaerts)
        val la = Markttechnik.lage(r.h, r.l, r.c)
        assertEquals(Markttechnik.Zustand.AUF, la.zustand)
        assertEquals(114.5, la.marke!!, 1e-9)
        assertEquals(65, la.markeIndex)
        val s = Markttechnik.stopp(r.h, r.l, r.c)!!
        assertEquals(114.5, s.kurs, 1e-9)
        assertEquals((1 - 114.5 / 150.0) * 100, s.abstandPct, 1e-9)
    }

    @Test fun korrekturUeberDemLetztenTiefLaesstDenTrendIntakt() {
        val r = reihe(*aufwaerts, 88 to 130.0)
        val la = Markttechnik.lage(r.h, r.l, r.c)
        assertEquals(Markttechnik.Zustand.AUF, la.zustand)
        assertEquals(114.5, la.marke!!, 1e-9)
    }

    @Test fun kursUnterDemLetztenTiefBrichtDenAufwaertstrend() {
        val r = reihe(*aufwaerts, 100 to 105.0)
        val la = Markttechnik.lage(r.h, r.l, r.c)
        assertEquals(Markttechnik.Zustand.AUF_GEBROCHEN, la.zustand)
        assertEquals(114.5, la.marke!!, 1e-9)
        assertNull(Markttechnik.stopp(r.h, r.l, r.c))
    }

    @Test fun abwaertstrendUndSeinBruchGespiegelt() {
        val ab = gespiegelt(reihe(*aufwaerts))
        val la = Markttechnik.lage(ab.h, ab.l, ab.c)
        assertEquals(Markttechnik.Zustand.AB, la.zustand)
        assertEquals(300 - 114.5, la.marke!!, 1e-9)
        assertNull(Markttechnik.stopp(ab.h, ab.l, ab.c))
        val bruch = gespiegelt(reihe(*aufwaerts, 100 to 105.0))
        assertEquals(Markttechnik.Zustand.AB_GEBROCHEN, Markttechnik.lage(bruch.h, bruch.l, bruch.c).zustand)
    }

    @Test fun ohneFolgeKeinTrend() {
        // Hoeheres Hoch, aber tieferes Tief
        val r = reihe(0 to 100.0, 15 to 80.0, 30 to 110.0, 45 to 70.0, 65 to 120.0)
        assertEquals(Markttechnik.Zustand.KEINER, Markttechnik.lage(r.h, r.l, r.c).zustand)
        val kurz = reihe(0 to 100.0, 5 to 101.0)
        val la = Markttechnik.lage(kurz.h, kurz.l, kurz.c)
        assertEquals(Markttechnik.Zustand.KEINER, la.zustand)
        assertNull(la.marke)
    }

    @Test fun linienFuerDenChart() {
        val r = reihe(*aufwaerts)
        val la = Markttechnik.lage(r.h, r.l, r.c)
        val li = Markttechnik.linien(la, r.c.size)
        assertEquals(r.c.size, li.verlauf.size)
        assertEquals(110.5, li.punkte[30]!!, 1e-9)
        assertNull(li.punkte[31])
        // Verlauf gerade zwischen Hoch (30) und Tief (40)
        assertEquals((110.5 + 94.5) / 2, li.verlauf[35]!!, 1e-9)
        assertEquals(150.5, li.verlauf[80]!!, 1e-9)
        assertNull(li.marke[64])
        assertEquals(114.5, li.marke[65]!!, 1e-9)
        assertEquals(114.5, li.marke[80]!!, 1e-9)
    }

    @Test fun chartIndikatorTrendLiefertDreiLinien() {
        val r = reihe(*aufwaerts)
        val kerzen = r.c.indices.map { Candle(it * 86_400_000L, r.c[it], r.h[it], r.l[it], r.c[it]) }
        val i = ChartIndikatoren.Indikator.von("trend")!!
        assertEquals(ChartIndikatoren.Ort.KURS, i.ort)
        val e = ChartIndikatoren.rechnen(i, kerzen) { it / 86_400_000L }
        assertEquals(3, e.linien.size)
        assertEquals(ChartIndikatoren.Art.PUNKTE, e.linien[1].art)
        assertEquals(114.5, e.linien[2].werte.last()!!, 1e-9)
    }

    // ------------------------------------------------------------ Formationen

    private val doppeltop = arrayOf(0 to 100.0, 20 to 80.0, 40 to 120.0, 50 to 108.0, 60 to 120.5, 75 to 95.0)
    private val sks = arrayOf(0 to 100.0, 15 to 70.0, 30 to 100.0, 38 to 88.0, 50 to 112.0, 60 to 88.0, 68 to 100.0, 85 to 70.0)

    @Test fun doppeltopErstMitBruchDerNackenlinie() {
        val r = reihe(*doppeltop)
        val t = Chartformationen.erkennen(r.h, r.l, r.c).last()
        assertEquals("doppeltop", t.art)
        assertEquals(-1, t.richtung)
        assertEquals(40, t.von)
        assertEquals(107.5, t.linie, 1e-9)
        // Erster Schluss unter 107,5
        assertEquals(68, t.bis)
        assertTrue(r.c[67] >= 107.5 && r.c[68] < 107.5)
        // Vor dem Bruch gibt es die Formation nicht
        val vorher = reihe(0 to 100.0, 20 to 80.0, 40 to 120.0, 50 to 108.0, 60 to 120.5, 66 to 110.3)
        assertTrue(Chartformationen.erkennen(vorher.h, vorher.l, vorher.c).none { it.art == "doppeltop" })
    }

    @Test fun doppeltopVerfaelltMitNeuemHochOderZuWeitemAbstand() {
        // Zweites Hoch 8 % ueber dem ersten
        val weit = reihe(0 to 100.0, 20 to 80.0, 40 to 120.0, 50 to 108.0, 62 to 130.0, 80 to 95.0)
        assertTrue(Chartformationen.erkennen(weit.h, weit.l, weit.c).none { it.art == "doppeltop" })
    }

    @Test fun doppelbodenGespiegelt() {
        val r = gespiegelt(reihe(*doppeltop))
        val t = Chartformationen.erkennen(r.h, r.l, r.c).last()
        assertEquals("doppelboden", t.art)
        assertEquals(1, t.richtung)
        assertEquals(68, t.bis)
        assertEquals(300 - 107.5, t.linie, 1e-9)
    }

    @Test fun schulterKopfSchulterUndUmgekehrt() {
        val r = reihe(*sks)
        val t = Chartformationen.erkennen(r.h, r.l, r.c).last()
        assertEquals("sks", t.art)
        assertEquals(-1, t.richtung)
        assertEquals(30, t.von)
        assertEquals(87.5, t.linie, 1e-9)
        assertTrue(r.c[t.bis] < 87.5 && r.c[t.bis - 1] >= 87.5)
        val u = gespiegelt(r)
        val tu = Chartformationen.erkennen(u.h, u.l, u.c).last()
        assertEquals("sks_umgekehrt", tu.art)
        assertEquals(1, tu.richtung)
        assertEquals(t.bis, tu.bis)
        // Kopf nicht ueber den Schultern: keine Schulter-Kopf-Schulter
        val flach = reihe(0 to 100.0, 15 to 70.0, 30 to 100.0, 38 to 88.0, 50 to 100.5, 60 to 88.0, 68 to 100.0, 85 to 70.0)
        assertTrue(Chartformationen.erkennen(flach.h, flach.l, flach.c).none { it.art == "sks" })
    }

    @Test fun symmetrischesDreieckMitAusbruchNachOben() {
        val r = reihe(0 to 100.0, 15 to 70.0, 30 to 110.0, 40 to 84.0, 50 to 106.0, 58 to 90.0, 70 to 112.0)
        val t = Chartformationen.erkennen(r.h, r.l, r.c).last()
        assertEquals("dreieck_sym", t.art)
        assertEquals(1, t.richtung)
        assertEquals(30, t.von)
        assertTrue(t.bis in 59..70)
        assertTrue(r.c[t.bis] > t.linie && r.c[t.bis - 1] <= 110.5 - 0.2 * (t.bis - 1 - 30))
    }

    @Test fun aufsteigendesUndAbsteigendesDreieck() {
        // Hochs waagerecht bei 110, Tiefs steigen; Ausbruch nach oben
        val auf = reihe(0 to 100.0, 15 to 70.0, 30 to 110.0, 40 to 84.0, 50 to 110.0, 58 to 94.0, 70 to 118.0)
        val t = Chartformationen.erkennen(auf.h, auf.l, auf.c).last()
        assertEquals("dreieck_auf", t.art)
        assertEquals(1, t.richtung)
        val ab = gespiegelt(auf)
        val u = Chartformationen.erkennen(ab.h, ab.l, ab.c).last()
        assertEquals("dreieck_ab", u.art)
        assertEquals(-1, u.richtung)
    }

    @Test fun geraderAnstiegOhneFormation() {
        val r = reihe(0 to 100.0, 120 to 220.0)
        assertTrue(Chartformationen.erkennen(r.h, r.l, r.c).isEmpty())
    }

    @Test fun deutungNurKurzNachDemDurchbruch() {
        val r = reihe(*doppeltop)
        val tage = r.c.indices.map { 20_000L + it }
        val d = Chartformationen.deutung(tage, r.h, r.l, r.c)!!
        assertEquals("formation", d.art)
        assertEquals("doppeltop", d.lage)
        assertEquals(-1, d.stufe)
        assertEquals(7.0, d.wert, 1e-9)
        assertEquals(20_068L, d.kreuzTag)
        assertEquals(Indikatoren.Tendenz.NEGATIV, Indikatoren.tendenz(d))
        // Elf Handelstage nach dem Durchbruch wird sie nicht mehr genannt
        val spaeter = reihe(*doppeltop, 79 to 94.0)
        assertNull(Chartformationen.deutung(spaeter.c.indices.map { 20_000L + it }, spaeter.h, spaeter.l, spaeter.c))
    }

    // ------------------------------------------------------------ Rollen

    private val jetzt = 20_833L * 86_400L
    private fun tag(n: Int) = jetzt / 86_400L * 86_400L + n * 86_400L
    private fun put(strike: Double, tage: Int, geld: Double, brief: Double) = Kontrakt(false, strike, tag(tage), (geld + brief) / 2, geld, brief, 10, 100, 0.4)
    private fun call(strike: Double, tage: Int, geld: Double) = Kontrakt(true, strike, tag(tage), geld, geld, geld + 0.1, 10, 100, 0.4)

    private val kette = listOf(
        put(100.0, 12, 4.8, 5.0),
        put(100.0, 30, 6.0, 6.2), put(95.0, 30, 3.5, 3.7),
        put(100.0, 47, 6.9, 7.1), put(95.0, 47, 4.6, 4.8), put(90.0, 47, 2.9, 3.1), put(105.0, 47, 9.8, 10.0),
        put(100.0, 75, 8.2, 8.5), put(95.0, 75, 5.9, 6.1), put(90.0, 75, 5.0, 5.2), put(85.0, 75, 2.6, 2.8),
        put(100.0, 110, 9.5, 9.9),
        call(100.0, 40, 2.1), call(105.0, 40, 1.0), call(95.0, 40, 4.2), call(100.0, 75, 3.4),
    )

    @Test fun druckStufen() {
        assertEquals(RollHelfer.Druck.KEINER, RollHelfer.druck(110.0, 100.0))
        assertEquals(RollHelfer.Druck.NAHE, RollHelfer.druck(103.0, 100.0))
        assertEquals(RollHelfer.Druck.NAHE, RollHelfer.druck(100.0, 100.0))
        assertEquals(RollHelfer.Druck.IM_GELD, RollHelfer.druck(99.99, 100.0))
        assertEquals(RollHelfer.Druck.KEINER, RollHelfer.druck(0.0, 100.0))
    }

    @Test fun ohneDruckKeinVorschlag() {
        assertNull(RollHelfer.rechnen(110.0, 100.0, tag(12) / 86_400L, kette, jetzt))
    }

    @Test fun rollenAufGleichenUndTieferenBasispreis() {
        val e = RollHelfer.rechnen(97.0, 100.0, tag(12) / 86_400L, kette, jetzt)!!
        assertEquals(RollHelfer.Druck.IM_GELD, e.druck)
        // Rueckkauf zum Briefkurs des verkauften Puts
        assertEquals(5.0, e.rueckkauf, 1e-9)
        // Nur Laufzeiten von 45 bis 90 Tagen, je der naechste und der fernste Termin; kein hoeherer Basispreis
        assertEquals(listOf(47, 47, 75, 75), e.vorschlaege.map { it.tage })
        assertEquals(listOf(100.0, 95.0, 100.0, 90.0), e.vorschlaege.map { it.kontrakt.strike })
        assertEquals(listOf(false, true, false, true), e.vorschlaege.map { it.tiefer })
        // Gleicher Basispreis: 6,9 - 5,0 Gutschrift
        assertEquals(1.9, e.vorschlaege[0].netto, 1e-9)
        assertEquals(6.9, e.vorschlaege[0].praemie, 1e-9)
        // 47 Tage: unter 100 bringt keiner eine Gutschrift, also der naechste darunter mit Kosten
        assertEquals(-0.4, e.vorschlaege[1].netto, 1e-9)
        // 75 Tage: 90 ist der tiefste Basispreis ohne Kosten (5,0 - 5,0), 85 kostete
        assertEquals(0.0, e.vorschlaege[3].netto, 1e-9)
        // Nach Andienung: Call ab dem Basispreis des Puts, Laufzeit nahe 45 Tagen
        assertEquals(100.0, e.call!!.strike, 1e-9)
        assertEquals(tag(40), e.call!!.verfall)
    }

    @Test fun naheAmBasispreisOhneCallHinweis() {
        val e = RollHelfer.rechnen(102.0, 100.0, tag(12) / 86_400L, kette, jetzt)!!
        assertEquals(RollHelfer.Druck.NAHE, e.druck)
        assertNull(e.call)
        assertFalse(e.vorschlaege.isEmpty())
    }

    @Test fun rueckkaufErsatzWennDerKontraktInDerKetteFehlt() {
        val ohne = kette.drop(1)
        assertNull(RollHelfer.rechnen(97.0, 100.0, tag(12) / 86_400L, ohne, jetzt))
        val e = RollHelfer.rechnen(97.0, 100.0, tag(12) / 86_400L, ohne, jetzt, 5.5)!!
        assertEquals(5.5, e.rueckkauf, 1e-9)
        assertEquals(6.9 - 5.5, e.vorschlaege[0].netto, 1e-9)
        assertNotNull(e.call)
    }

    @Test fun nurSpaetereTermineAlsDerVerkauftePut() {
        // Verkaufter Put laeuft noch 47 Tage: gerollt wird nur auf den Termin danach
        val e = RollHelfer.rechnen(97.0, 100.0, tag(47) / 86_400L, kette, jetzt)!!
        assertEquals(7.1, e.rueckkauf, 1e-9)
        assertTrue(e.vorschlaege.all { it.tage == 75 })
    }
}
