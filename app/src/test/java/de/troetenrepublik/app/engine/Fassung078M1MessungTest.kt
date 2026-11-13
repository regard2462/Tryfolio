package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

// Gemessene Staerken und Aussagen „in N von M“: Rezessionswahrscheinlichkeit der New York Fed gegen die Einbrueche des
// S&P 500 und gegen die Rezessionen, als Eingang des Gesamtausblicks; Kreditanstieg gegen Finanzkrisen.
class Fassung078M1MessungTest {

    private fun asset(name: String) = listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }
    private fun text(name: String) = asset(name).readText(Charsets.UTF_8)
    private fun m(j: Int, mo: Int) = RezessionNyFed.monat(j, mo)
    private fun tag(j: Int, mo: Int, t: Int) = LocalDate.of(j, mo, t).toEpochDay().toInt()

    // Reihe ab 2000-01 mit 10 %, erste zwoelf Monate ohne Wert; ueber 30 % gesehen in 2002-03..2002-05 (Zeilen 2003-03..2003-05)
    private fun reihe(): RezessionNyFed.Reihe {
        val von = m(2000, 1)
        val p = MutableList<Double?>(m(2009, 12) - von + 1) { 10.0 }
        for (i in 0 until 12) p[i] = null
        for (k in m(2003, 3)..m(2003, 5)) p[k - von] = 45.0
        return RezessionNyFed.Reihe(von, p, listOf(m(2003, 1) to m(2003, 6), m(2006, 1) to m(2006, 3)), m(2008, 12), m(2008, 12))
    }

    @Test fun zeigteImFenster() {
        val r = reihe()
        // Fenster vor 2003-01: gesehen 2002-01..2002-12, darin 2002-03..2002-05 ueber 30
        assertEquals(true, NyFedMessung.zeigte(r, m(2003, 1), 30.0))
        // Fenster vor 2002-03: gesehen 2001-03..2002-02, noch nicht
        assertEquals(false, NyFedMessung.zeigte(r, m(2002, 3), 30.0))
        // Erster Monat mit Warnung im Fenster ist 2002-04, letzter 2003-05
        assertEquals(true, NyFedMessung.zeigte(r, m(2002, 4), 30.0))
        assertEquals(true, NyFedMessung.zeigte(r, m(2003, 5), 30.0))
        assertEquals(false, NyFedMessung.zeigte(r, m(2003, 6), 30.0))
        // Genau auf der Schwelle zaehlt nicht
        assertEquals(false, NyFedMessung.zeigte(r, m(2003, 1), 45.0))
        // Vor 1999-06 fehlen Werte
        assertNull(NyFedMessung.zeigte(r, m(1999, 6), 30.0))
    }

    @Test fun grundquoteUndTreffer() {
        val r = reihe()
        // Gesehen wird von 2000-01 bis 2008-12; volle Fenster haben die Monate 2001-01 bis 2009-01 (97), 14 davon mit Warnung
        assertEquals(14.0 / 97, NyFedMessung.grundquote(r, 30.0)!!, 1e-12)
        val e = NyFedMessung.einbrueche(r, listOf(tag(2002, 12, 15), tag(2006, 7, 3), tag(1999, 3, 1)), 30.0)!!
        assertEquals(1, e.treffer)
        assertEquals(2, e.von)
        assertEquals(2002, e.abJahr)
        assertEquals(14.0 / 97, e.grund, 1e-12)
        val n = NyFedMessung.rezessionen(r, 30.0)!!
        assertEquals(1, n.treffer)
        assertEquals(2, n.von)
        assertNull(NyFedMessung.einbrueche(r, listOf(tag(1999, 3, 1)), 30.0))
    }

    @Test fun lageAusDemWert() {
        assertEquals(Tendenz.Richtung.BEARISH, NyFedMessung.lage(30.1).richtung)
        assertEquals(Tendenz.Richtung.NEUTRAL, NyFedMessung.lage(30.0).richtung)
        assertEquals(Tendenz.Richtung.NEUTRAL, NyFedMessung.lage(2.0).richtung)
        assertEquals(Tendenz.AKTIEN, NyFedMessung.lage(50.0).ziel)
    }

    @Test fun stufeAusMessung() {
        // unter fuenf Faellen keine Messung, unter zehn hoechstens Stufe 2
        assertNull(AusblickStaerke.ausMessung(4, 4, 0.1))
        assertEquals(AusblickStaerke.Staerke(2, true), AusblickStaerke.ausMessung(9, 9, 0.1))
        assertEquals(AusblickStaerke.Staerke(4, true), AusblickStaerke.ausMessung(10, 10, 0.1))
        assertEquals(AusblickStaerke.Staerke(0, true), AusblickStaerke.ausMessung(3, 10, 0.3))
        assertEquals(AusblickStaerke.Staerke(0, true), AusblickStaerke.ausMessung(2, 10, 0.3))
        assertEquals(AusblickStaerke.Staerke(1, true), AusblickStaerke.ausMessung(33, 100, 0.3))
        assertEquals(AusblickStaerke.Staerke(2, true), AusblickStaerke.ausMessung(4, 10, 0.3))
        assertEquals(AusblickStaerke.Staerke(3, true), AusblickStaerke.ausMessung(5, 10, 0.3))
    }

    // ------------------------------------------------------------ mitgelieferte Dateien

    private val nyFed: RezessionNyFed.Reihe by lazy { RezessionNyFed.lesen(text(RezessionNyFed.DATEI))!! }
    private val warn: WarnRueckblick.Datei by lazy { WarnRueckblick.lesen(text("indikatoren/warnzeichen_rueckblick.json"))!! }

    @Test fun nyFedGegenEinbruecheDesSp500() {
        val hochs = NyFedMessung.hochs(warn)
        // Zwoelf Einbrueche ab 20 % seit 1928, zehn davon mit Werten der Reihe davor
        assertEquals(12, hochs.size)
        val e = NyFedMessung.einbrueche(nyFed, hochs)!!
        assertEquals(30.0, e.schwelle, 0.0)
        assertEquals(10, e.von)
        assertEquals(4, e.treffer)
        assertEquals(1961, e.abJahr)
        assertEquals(0.30, e.grund, 0.01)
        // Nachgezaehlt: Hochs, vor denen der gesehene Wert in den zwoelf Monaten davor ueber 30 % lag
        val treffer = hochs.count { h ->
            val d = LocalDate.ofEpochDay(h.toLong())
            val k = m(d.year, d.monthValue)
            val w = (k - 12 until k).map { nyFed.wert(it + 12) }
            w.none { it == null } && w.any { it!! > 30.0 }
        }
        assertEquals(4, treffer)
    }

    @Test fun nyFedGegenRezessionen() {
        val n = NyFedMessung.rezessionen(nyFed)!!
        assertEquals(9, n.von)
        assertEquals(8, n.treffer)
        // dieselbe Zaehlung wie die Trefferbilanz der Anzeige
        val b = RezessionNyFed.bilanz(nyFed)!!
        assertEquals(b.treffer, n.treffer)
        assertEquals(b.von, n.von)
    }

    @Test fun staerkeImAusblickGemessen() {
        // 4 von 10 gegen eine Grundquote von 30 %: Vorsprung 10 Punkte, Stufe 2
        assertEquals(AusblickStaerke.Staerke(2, true), AusblickStaerke.fuerSignal(NyFedMessung.SCHLUESSEL, null, warn, nyFed))
        // Ohne Reihe oder ohne Rueckblick bleibt die Stufe gesetzt
        val gesetzt = AusblickStaerke.fuerSignal(NyFedMessung.SCHLUESSEL, null, warn, null)
        assertEquals(false, gesetzt.gemessen)
        assertEquals(false, AusblickStaerke.fuerSignal(NyFedMessung.SCHLUESSEL, null, null, nyFed).gemessen)
        // Das Gewicht im Gesamtausblick entspricht der gemessenen Stufe
        assertEquals(2, gesetzt.stufe)
        // Andere Signale bleiben, wie sie waren
        assertEquals(false, AusblickStaerke.fuerSignal("rezession", null, warn, nyFed).gemessen)
    }

    @Test fun eingangImGesamtausblick() {
        val q = Ausblickgesamt.quelle(NyFedMessung.SCHLUESSEL)
        assertEquals(setOf(Ausblickgesamt.Horizont.MITTEL), q.horizonte)
        assertEquals("fruehsignale", Ausblickgesamt.abschnitt(NyFedMessung.SCHLUESSEL))
        assertEquals(Ausblickgesamt.quelle("rezession").gruppe, q.gruppe)
        assertNotNull(q.gruppe)
        fun b(k: String, r: Tendenz.Richtung) = Ausblickgesamt.Beitrag(k, k, Tendenz.AKTIEN, r)
        // Wetten und Zinskurve zaehlen als eine Einheit; allein traegt die Zinskurve ihr eigenes Gewicht
        val beide = Ausblickgesamt.einheiten(listOf(b("rezession", Tendenz.Richtung.BULLISH), b(NyFedMessung.SCHLUESSEL, Tendenz.Richtung.BEARISH)))
        assertEquals(1, beide.size)
        assertEquals(2, beide[0].mitglieder)
        assertEquals(1.2, beide[0].gewicht, 1e-9)
        assertEquals((1.2 - 0.6) / 1.8, beide[0].wert, 1e-9)
        val allein = Ausblickgesamt.einheiten(listOf(b(NyFedMessung.SCHLUESSEL, Tendenz.Richtung.BEARISH)))
        assertEquals(0.6, allein[0].gewicht, 1e-9)
        assertEquals(Tendenz.Richtung.BEARISH, allein[0].richtung)
        // Heutiger Wert der Datei
        val (_, p) = RezessionNyFed.aktuell(nyFed)!!
        assertEquals(if (p > 30.0) Tendenz.Richtung.BEARISH else Tendenz.Richtung.NEUTRAL, NyFedMessung.lage(p).richtung)
    }

    // ------------------------------------------------------------ Kreditanstieg

    private fun land(a3: String, von: Int, kredit: List<Double?>, krisen: List<Int>) =
        HistorieJst.Land(a3, von, emptyMap(), kredit, krisen)

    @Test fun kreditBeispiel() {
        // Land A: Kredit steigt von 2000 bis 2005 um 25 Punkte, danach flach; Krise 2008
        val a = land("AAA", 2000, listOf(50.0, 55.0, 60.0, 65.0, 70.0, 75.0, 75.0, 75.0, 75.0, 75.0, 75.0, null, 80.0, 80.0, 80.0, 80.0), listOf(2008))
        assertEquals(25.0, KreditMessung.anstieg(a, 2005)!!, 1e-9)
        assertEquals(20.0, KreditMessung.anstieg(a, 2006)!!, 1e-9)
        assertEquals(15.0, KreditMessung.anstieg(a, 2007)!!, 1e-9)
        assertNull(KreditMessung.anstieg(a, 2004))
        assertNull(KreditMessung.anstieg(a, 2011))
        assertNull(KreditMessung.anstieg(a, 2016))
        val d = HistorieJst.Datei("2026-11-12", 2015, mapOf("AAA" to a), emptyList(), "")
        val b = KreditMessung.bilanz(d)!!
        // Gezaehlt werden 2005 bis 2010 (2011 und 2016 ohne Wert, ab 2011 fehlen Folgejahre bis 2015)
        assertEquals(6, b.alle)
        assertEquals(2005, b.von)
        assertEquals(2010, b.bis)
        // Anstieg ab 20 Punkten: 2005 und 2006, beide mit der Krise 2008 in den fuenf Jahren danach
        assertEquals(2, b.faelle)
        assertEquals(2, b.mitKrise)
        // Ueber alle Jahre: 2005, 2006, 2007 sehen 2008 in den Folgejahren, 2008 bis 2010 nicht (das Krisenjahr selbst zaehlt nicht)
        assertEquals(3, b.alleMitKrise)
        assertEquals(1.0, b.quote, 1e-9)
        assertEquals(0.5, b.grund, 1e-9)
        assertEquals(1, b.laender)
    }

    @Test fun kreditAusDerDatei() {
        val d = HistorieJst.lesen(text(HistorieJst.DATEI))!!
        val b = KreditMessung.bilanz(d)!!
        assertEquals(158, b.faelle)
        assertEquals(49, b.mitKrise)
        assertEquals(2206, b.alle)
        assertEquals(0.31, b.quote, 0.005)
        assertEquals(0.155, b.grund, 0.005)
        assertEquals(18, b.laender)
        assertTrue(b.bis <= d.ende - KreditMessung.JAHRE)
        // Nachgezaehlt
        var faelle = 0
        var mit = 0
        for (l in d.laender.values) for (i in 5 until l.kredit.size) {
            val jahr = l.von + i
            if (jahr + 5 > d.ende) continue
            val x = l.kredit[i] ?: continue
            val y = l.kredit[i - 5] ?: continue
            if (x - y >= 20.0) { faelle++; if ((1..5).any { (jahr + it) in l.krisen }) mit++ }
        }
        assertEquals(faelle, b.faelle)
        assertEquals(mit, b.mitKrise)
    }
}
