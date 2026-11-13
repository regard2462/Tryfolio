package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.GlobusGesamt.Art
import de.troetenrepublik.app.engine.GlobusGesamt.Bereich
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// [Globus Gesamtbild 0.73] Bereiche, Schwellen, Mindestzahl der Bereiche und Rangfolge mit festen Beispieldaten
class GlobusGesamtTest {
    private class Land(
        val code: String, val kopf: Double? = null, val wachstum: Double? = null, val inflation: Double? = null, val schulden: Double? = null,
        val saldo: Double? = null, val arbeitslos: Double? = null, val leistung: Double? = null,
    )

    // Wachstum steht für 2024 bis 2028, alles andere für 2026
    private fun satz(vararg laender: Land): Imf.Datensatz {
        val werte = HashMap<String, HashMap<String, Map<Int, Double>>>()
        fun einzeln(kennung: String, code: String, v: Double?) { if (v != null) werte.getOrPut(kennung) { HashMap() }[code] = mapOf(2026 to v) }
        for (l in laender) {
            einzeln(Imf.BIP_KOPF_KKP, l.code, l.kopf)
            l.wachstum?.let { w -> werte.getOrPut(Imf.WACHSTUM) { HashMap() }[l.code] = (2024..2028).associateWith { w } }
            einzeln(Imf.INFLATION, l.code, l.inflation)
            einzeln(Imf.SCHULDEN, l.code, l.schulden)
            einzeln(Imf.HAUSHALT, l.code, l.saldo)
            einzeln(Imf.ARBEITSLOS, l.code, l.arbeitslos)
            einzeln(Imf.LEISTUNG, l.code, l.leistung)
        }
        return Imf.Datensatz(0L, werte, 3)
    }

    private fun punkte(l: Land, b: Bereich, zusatz: GlobusGesamt.Zusatz? = null): Double? =
        GlobusGesamt.bereiche(satz(l), l.code, 2026, zusatz).firstOrNull { it.bereich == b }?.punkte

    private fun sicher(konflikt: Int = 0, eu: Int = 0, us: Int = 0, reise: Int = 0) =
        GlobusGesamt.Zusatz(sicherheit = GlobusGesamt.Sicherheit(konflikt, eu, us, reise))

    @Test
    fun ohneZahlenNull() {
        assertNull(GlobusGesamt.rechnen(satz(), "DEU", 2026))
        assertNull(GlobusGesamt.rechnen(satz(Land("DEU")), "DEU", 2026, sicher()))
    }

    @Test
    fun wohlstandSchwellen() {
        assertEquals(0.0, punkte(Land("X", kopf = 1500.0), Bereich.WOHLSTAND)!!, 1e-9)
        assertEquals(100.0, punkte(Land("X", kopf = 100000.0), Bereich.WOHLSTAND)!!, 1e-9)
        // logarithmische Mitte
        assertEquals(50.0, punkte(Land("X", kopf = Math.sqrt(1500.0 * 100000.0)), Bereich.WOHLSTAND)!!, 1e-6)
        // Mittel mit der Lebenserwartung: 55 Jahre 0, 85 Jahre 100
        val leben = GlobusGesamt.Zusatz(lebenserwartung = 85.0)
        assertEquals(50.0, punkte(Land("X", kopf = 1500.0), Bereich.WOHLSTAND, leben)!!, 1e-9)
        assertEquals(0.0, punkte(Land("X"), Bereich.WOHLSTAND, GlobusGesamt.Zusatz(lebenserwartung = 55.0))!!, 1e-9)
        assertEquals(50.0, punkte(Land("X"), Bereich.WOHLSTAND, GlobusGesamt.Zusatz(lebenserwartung = 70.0))!!, 1e-9)
    }

    private fun mitWachstum(w: Double) = Land("X", wachstum = w, kopf = 10000.0)

    @Test
    fun wachstumSchwellenUndFenster() {
        assertEquals(0.0, punkte(mitWachstum(-2.0), Bereich.WACHSTUM)!!, 1e-9)
        assertEquals(100.0, punkte(mitWachstum(7.0), Bereich.WACHSTUM)!!, 1e-9)
        assertEquals(100.0, punkte(mitWachstum(20.0), Bereich.WACHSTUM)!!, 1e-9)
        assertEquals(50.0, punkte(mitWachstum(2.5), Bereich.WACHSTUM)!!, 1e-9)
        // Mittel der fuenf Jahre von jahr-2 bis jahr+2
        val d = Imf.Datensatz(0L, mapOf(Imf.WACHSTUM to mapOf("X" to mapOf(2024 to 7.0, 2025 to 7.0, 2026 to -2.0, 2027 to -2.0, 2028 to 7.0, 2029 to -50.0))), 3)
        assertEquals((7.0 + 7.0 - 2.0 - 2.0 + 7.0) / 5, GlobusGesamt.bereiche(d, "X", 2026).single().zutaten[0].wert, 1e-9)
        // weniger als drei Jahre: kein Bereich
        val knapp = Imf.Datensatz(0L, mapOf(Imf.WACHSTUM to mapOf("X" to mapOf(2025 to 3.0, 2026 to 3.0))), 3)
        assertTrue(GlobusGesamt.bereiche(knapp, "X", 2026).isEmpty())
    }

    @Test
    fun stabilitaetSchwellen() {
        fun st(l: Land) = punkte(l, Bereich.STABILITAET)
        // Inflation: Abstand zu 2 %
        assertEquals(100.0, st(Land("X", inflation = 2.0, schulden = 30.0))!!, 1e-9)
        assertEquals(0.0, st(Land("X", inflation = 15.0, schulden = 150.0))!!, 1e-9)
        assertEquals(0.0, st(Land("X", inflation = -11.0, schulden = 150.0))!!, 1e-9)
        assertEquals(50.0, st(Land("X", inflation = 8.5, schulden = 90.0))!!, 1e-9)
        // Schulden 30 % bis 150 %, Haushaltssaldo 0 bis -8 %
        assertEquals(50.0, st(Land("X", schulden = 90.0, saldo = -4.0))!!, 1e-9)
        // je Wert
        assertEquals(100.0, st(Land("X", saldo = 0.0, arbeitslos = 3.0))!!, 1e-9)
        assertEquals(0.0, st(Land("X", saldo = -8.0, arbeitslos = 18.0))!!, 1e-9)
        assertEquals(50.0, st(Land("X", leistung = -5.0, arbeitslos = 10.5))!!, 1e-9)
        // nur ein Wert: kein Bereich
        assertNull(st(Land("X", inflation = 2.0, kopf = 1000.0)))
    }

    @Test
    fun regierungMittelAbDreiIndikatoren() {
        val l = Land("X", kopf = 10000.0, wachstum = 3.0)
        assertEquals(40.0, punkte(l, Bereich.REGIERUNG, GlobusGesamt.Zusatz(wgi = mapOf("PV" to 20.0, "GE" to 40.0, "RL" to 60.0)))!!, 1e-9)
        assertNull(punkte(l, Bereich.REGIERUNG, GlobusGesamt.Zusatz(wgi = mapOf("PV" to 20.0, "GE" to 40.0))))
    }

    @Test
    fun sicherheitAbzuege() {
        fun si(z: GlobusGesamt.Zusatz) = punkte(Land("X", kopf = 10000.0, wachstum = 3.0), Bereich.SICHERHEIT, z)!!
        assertEquals(100.0, si(sicher()), 1e-9)
        assertEquals(40.0, si(sicher(konflikt = 2)), 1e-9)
        assertEquals(70.0, si(sicher(konflikt = 1)), 1e-9)
        assertEquals(85.0, si(sicher(eu = 2)), 1e-9)
        assertEquals(85.0, si(sicher(us = 1)), 1e-9)
        assertEquals(70.0, si(sicher(reise = 4)), 1e-9)
        assertEquals(85.0, si(sicher(reise = 3)), 1e-9)
        assertEquals(95.0, si(sicher(reise = 2)), 1e-9)
        assertEquals(100.0, si(sicher(reise = 1)), 1e-9)
        // alles zusammen faellt nicht unter 0
        assertEquals(0.0, si(sicher(konflikt = 2, eu = 1, us = 1, reise = 4)), 1e-9)
        // ohne geladene Quellen fehlt der Bereich
        assertNull(punkte(Land("X", kopf = 10000.0, wachstum = 3.0), Bereich.SICHERHEIT, GlobusGesamt.zusatz(null, emptyMap(), null, null, null, null)))
        assertNotNull(punkte(Land("X", kopf = 10000.0, wachstum = 3.0), Bereich.SICHERHEIT, GlobusGesamt.zusatz(null, emptyMap(), 0, 0, 0, null)))
    }

    @Test
    fun gesamtIstGewichtetUndFehlendeBereicheZaehlenHoechstensSiebzig() {
        val d = satz(Land("X", kopf = 100000.0, wachstum = 7.0, schulden = 30.0, saldo = 0.0))
        // Wohlstand 100, Wachstum 100, Stabilitaet 100, Sicherheit 40; Regierungsfuehrung fehlt: Mittel der anderen 84, gedeckelt auf 70
        val e = GlobusGesamt.rechnen(d, "X", 2026, sicher(konflikt = 2))!!
        assertEquals(4, e.bereiche.size)
        assertEquals((25 * 100 + 10 * 100 + 20 * 100 + 20 * 40 + 25 * 70) / 100.0, e.punkte, 1e-9)
        // Bei einem Mittel unter 70 gilt das Mittel selbst: wie auf die vorhandenen Bereiche umgerechnet
        val schwach = GlobusGesamt.rechnen(satz(Land("X", kopf = 1500.0, wachstum = -2.0, schulden = 150.0, saldo = -8.0)), "X", 2026, sicher(konflikt = 2))!!
        assertEquals(800.0 / 75, schwach.punkte, 1e-9)
        // alle fuenf Bereiche: gewichtetes Mittel ohne Fuellung
        val wgi = GlobusGesamt.Zusatz(lebenserwartung = 85.0, wgi = mapOf("PV" to 80.0, "GE" to 80.0, "RL" to 80.0), sicherheit = GlobusGesamt.Sicherheit(0, 0, 0, 0))
        val voll = GlobusGesamt.rechnen(satz(Land("X", kopf = 100000.0, wachstum = 7.0, schulden = 30.0, saldo = 0.0)), "X", 2026, wgi)!!
        assertEquals(5, voll.bereiche.size)
        assertEquals((25 * 100 + 10 * 100 + 20 * 100 + 25 * 80 + 20 * 100) / 100.0, voll.punkte, 1e-9)
        // nur Wachstum und Sicherheit: zwei Bereiche, keine Punkte
        assertNull(GlobusGesamt.rechnen(satz(Land("X", wachstum = 5.0)), "X", 2026, sicher()))
        // nur Regierungsfuehrung und Sicherheit: kein Bereich der ersten drei, keine Punkte
        val ohneErste = GlobusGesamt.Zusatz(wgi = mapOf("PV" to 50.0, "GE" to 50.0, "RL" to 50.0), sicherheit = GlobusGesamt.Sicherheit(0, 0, 0, 0))
        assertNull(GlobusGesamt.rechnen(satz(Land("X")), "X", 2026, ohneErste))
        // mit genau drei Bereichen hoechstens 70 Punkte, auch bei lauter Spitzenwerten
        val spitze = GlobusGesamt.Zusatz(lebenserwartung = 85.0, wgi = mapOf("PV" to 100.0, "GE" to 100.0, "RL" to 100.0), sicherheit = GlobusGesamt.Sicherheit(0, 0, 0, 0))
        assertEquals(70.0, GlobusGesamt.rechnen(satz(Land("X")), "X", 2026, spitze)!!.punkte, 1e-9)
        // Wohlstand (nur Lebenserwartung), Regierungsfuehrung und Sicherheit reichen: Laender ganz ohne IWF-Zahlen
        val leben = GlobusGesamt.Zusatz(lebenserwartung = 72.0, wgi = mapOf("PV" to 5.0, "GE" to 5.0, "RL" to 5.0), sicherheit = GlobusGesamt.Sicherheit(2, 1, 1, 4))
        assertNotNull(GlobusGesamt.rechnen(satz(Land("X")), "X", 2026, leben))
    }

    @Test
    fun ohneRegierungskennzahlenSteigtEinLandNichtUeberdasMitteldazu() {
        // gleiches Land mit und ohne Regierungsfuehrung: ohne sie nie mehr Punkte als mit 70 fuer den fehlenden Bereich
        val l = Land("X", kopf = 100000.0, wachstum = 7.0, schulden = 30.0, saldo = 0.0)
        val ohne = GlobusGesamt.rechnen(satz(l), "X", 2026, sicher())!!.punkte
        val mit70 = GlobusGesamt.rechnen(satz(l), "X", 2026, GlobusGesamt.Zusatz(wgi = mapOf("PV" to 70.0, "GE" to 70.0, "RL" to 70.0), sicherheit = GlobusGesamt.Sicherheit(0, 0, 0, 0)))!!.punkte
        assertEquals(mit70, ohne, 1e-9)
    }

    @Test
    fun zutatenFuerDieAnzeige() {
        val d = satz(Land("X", kopf = 30000.0, wachstum = 3.0, inflation = 3.1, schulden = 82.0, saldo = -3.4, arbeitslos = 5.0, leistung = 1.0))
        val e = GlobusGesamt.rechnen(d, "X", 2026, GlobusGesamt.Zusatz(lebenserwartung = 80.0, wgi = mapOf("PV" to 60.0, "GE" to 70.0, "RL" to 80.0), sicherheit = GlobusGesamt.Sicherheit(0, 0, 0, 0)))!!
        assertEquals(listOf(Art.BIP_KOPF, Art.LEBENSERWARTUNG), e.bereich(Bereich.WOHLSTAND)!!.zutaten.map { it.art })
        assertEquals(listOf(Art.INFLATION, Art.SCHULDEN, Art.HAUSHALT, Art.ARBEITSLOS, Art.LEISTUNG), e.bereich(Bereich.STABILITAET)!!.zutaten.map { it.art })
        assertEquals(82.0, e.bereich(Bereich.STABILITAET)!!.zutaten[1].wert, 1e-9)
        assertEquals(listOf(Art.KEINE_ABZUEGE), e.bereich(Bereich.SICHERHEIT)!!.zutaten.map { it.art })
        assertEquals(2024, e.bereich(Bereich.WACHSTUM)!!.zutaten[0].von)
        assertEquals(2028, e.bereich(Bereich.WACHSTUM)!!.zutaten[0].bis)
    }

    // Feste Beispieldaten (gerundete Grössenordnungen echter Länder): die Rangfolge muss einleuchten
    private val beispiele = listOf(
        Land("CHE", kopf = 85000.0, wachstum = 1.6, inflation = 0.5, schulden = 38.0, saldo = 0.2, arbeitslos = 4.3, leistung = 7.0),
        Land("NOR", kopf = 95000.0, wachstum = 1.5, inflation = 2.5, schulden = 40.0, saldo = 12.0, arbeitslos = 4.0, leistung = 14.0),
        Land("DEU", kopf = 68000.0, wachstum = 1.0, inflation = 2.0, schulden = 64.0, saldo = -2.5, arbeitslos = 3.5, leistung = 5.0),
        Land("USA", kopf = 85000.0, wachstum = 2.0, inflation = 3.0, schulden = 125.0, saldo = -7.0, arbeitslos = 4.2, leistung = -4.0),
        Land("JPN", kopf = 55000.0, wachstum = 0.8, inflation = 2.0, schulden = 230.0, saldo = -3.0, arbeitslos = 2.5, leistung = 4.0),
        Land("IND", kopf = 11500.0, wachstum = 6.5, inflation = 4.0, schulden = 80.0, saldo = -7.0, arbeitslos = 4.5, leistung = -1.0),
        Land("BRA", kopf = 21000.0, wachstum = 2.0, inflation = 4.5, schulden = 90.0, saldo = -7.5, arbeitslos = 6.0, leistung = -2.5),
        Land("TUR", kopf = 41000.0, wachstum = 3.2, inflation = 30.0, schulden = 25.0, saldo = -4.0, arbeitslos = 8.5, leistung = -2.0),
        Land("ARG", kopf = 29000.0, wachstum = 3.5, inflation = 25.0, schulden = 80.0, saldo = 0.0, arbeitslos = 7.0, leistung = 0.0),
        Land("RUS", kopf = 38000.0, wachstum = 1.0, inflation = 8.0, schulden = 20.0, saldo = -2.6, arbeitslos = 2.5, leistung = 2.0),
        Land("UKR", kopf = 18000.0, wachstum = 3.0, inflation = 10.0, schulden = 100.0, saldo = -18.0, arbeitslos = 15.0, leistung = -5.0),
        Land("IRN", kopf = 17000.0, wachstum = 0.5, inflation = 40.0, schulden = 30.0, saldo = -4.0, arbeitslos = 8.0, leistung = 2.0),
        Land("SDN", kopf = 3500.0, wachstum = 2.6, inflation = 100.0, schulden = 200.0, saldo = -4.0, arbeitslos = 20.0, leistung = -8.0),
        Land("YEM", kopf = 2500.0, wachstum = 1.0, inflation = 30.0, schulden = 60.0, saldo = -5.0, leistung = -6.0),
        Land("NGA", kopf = 6500.0, wachstum = 3.2, inflation = 20.0, schulden = 55.0, saldo = -4.5, arbeitslos = 4.0, leistung = 1.0),
        Land("ZAF", kopf = 16000.0, wachstum = 1.5, inflation = 3.5, schulden = 78.0, saldo = -5.0, arbeitslos = 32.0, leistung = -1.0),
        Land("POL", kopf = 53000.0, wachstum = 3.0, inflation = 3.5, schulden = 60.0, saldo = -6.0, arbeitslos = 3.0, leistung = 0.0),
        Land("GRC", kopf = 42000.0, wachstum = 2.0, inflation = 2.5, schulden = 140.0, saldo = -1.0, arbeitslos = 9.0, leistung = -5.0),
    )

    private val zusaetze = mapOf(
        "CHE" to GlobusGesamt.Zusatz(84.0, wgi(85, 95, 94, 96, 95, 95), GlobusGesamt.Sicherheit(0, 0, 0, 1)),
        "NOR" to GlobusGesamt.Zusatz(83.5, wgi(90, 90, 95, 98, 95, 98), GlobusGesamt.Sicherheit(0, 0, 0, 1)),
        "DEU" to GlobusGesamt.Zusatz(81.0, wgi(70, 88, 90, 90, 90, 90), GlobusGesamt.Sicherheit(0, 0, 0, 2)),
        "USA" to GlobusGesamt.Zusatz(79.0, wgi(50, 80, 85, 80, 80, 70), GlobusGesamt.Sicherheit(0, 0, 0, 1)),
        "JPN" to GlobusGesamt.Zusatz(84.5, wgi(85, 90, 90, 90, 90, 80), GlobusGesamt.Sicherheit(0, 0, 0, 1)),
        "IND" to GlobusGesamt.Zusatz(72.0, wgi(20, 60, 50, 55, 45, 55), GlobusGesamt.Sicherheit(1, 0, 0, 2)),
        "BRA" to GlobusGesamt.Zusatz(76.0, wgi(30, 35, 45, 40, 30, 60), GlobusGesamt.Sicherheit(0, 0, 0, 2)),
        "TUR" to GlobusGesamt.Zusatz(78.0, wgi(20, 55, 45, 40, 30, 25), GlobusGesamt.Sicherheit(1, 0, 0, 3)),
        "ARG" to GlobusGesamt.Zusatz(77.0, wgi(50, 40, 40, 45, 50, 65), GlobusGesamt.Sicherheit(0, 0, 0, 1)),
        "RUS" to GlobusGesamt.Zusatz(73.0, wgi(10, 55, 20, 20, 15, 15), GlobusGesamt.Sicherheit(2, 1, 1, 4)),
        "UKR" to GlobusGesamt.Zusatz(73.0, wgi(10, 30, 30, 30, 25, 50), GlobusGesamt.Sicherheit(2, 0, 1, 4)),
        "IRN" to GlobusGesamt.Zusatz(77.0, wgi(10, 35, 10, 20, 15, 10), GlobusGesamt.Sicherheit(2, 1, 1, 4)),
        "SDN" to GlobusGesamt.Zusatz(66.0, wgi(1, 2, 2, 2, 2, 5), GlobusGesamt.Sicherheit(2, 1, 1, 4)),
        "YEM" to GlobusGesamt.Zusatz(64.0, wgi(1, 1, 1, 1, 1, 5), GlobusGesamt.Sicherheit(2, 1, 1, 4)),
        "NGA" to GlobusGesamt.Zusatz(54.0, wgi(10, 20, 20, 25, 20, 40), GlobusGesamt.Sicherheit(2, 0, 1, 3)),
        "ZAF" to GlobusGesamt.Zusatz(66.0, wgi(30, 55, 55, 55, 55, 70), GlobusGesamt.Sicherheit(0, 0, 0, 2)),
        "POL" to GlobusGesamt.Zusatz(78.0, wgi(70, 70, 80, 60, 65, 70), GlobusGesamt.Sicherheit(0, 0, 0, 1)),
        "GRC" to GlobusGesamt.Zusatz(81.5, wgi(55, 60, 65, 60, 55, 75), GlobusGesamt.Sicherheit(0, 0, 0, 2)),
    )

    private fun wgi(vararg v: Int): Map<String, Double> = Laenderrisiken.WGI.zip(v.toList()).associate { (k, x) -> k to x.toDouble() }

    @Test
    fun rangfolgeLeuchtEin() {
        val d = satz(*beispiele.toTypedArray())
        val rang = GlobusGesamt.rangliste(d, GlobusGesamt.laender(d), 2026, zusaetze)
        val reihe = rang.map { it.code }
        val p = rang.associate { it.code to it.wert }
        assertEquals(beispiele.size, rang.size)
        fun vor(a: String, b: String) = assertTrue("$a ($${p[a]}) vor $b (${p[b]})", reihe.indexOf(a) < reihe.indexOf(b))
        // stabile reiche Rechtsstaaten oben
        for (reich in listOf("CHE", "NOR", "DEU")) for (mittel in listOf("USA", "BRA", "TUR", "ARG", "IND", "NGA", "ZAF")) vor(reich, mittel)
        assertTrue(reihe.take(3).toSet() == setOf("CHE", "NOR", "DEU") || reihe.take(4).containsAll(listOf("CHE", "NOR")))
        // Mittelfeld vor den Schwellenlaendern
        for (mittel in listOf("USA", "JPN", "POL", "GRC")) for (krise in listOf("TUR", "RUS", "NGA")) vor(mittel, krise)
        vor("BRA", "TUR")
        // Kriegs- und Krisenlaender unten
        for (krieg in listOf("UKR", "IRN", "SDN", "YEM")) for (ok in listOf("CHE", "DEU", "USA", "JPN", "IND", "BRA", "TUR", "ARG", "POL")) vor(ok, krieg)
        vor("RUS", "UKR"); vor("RUS", "IRN"); vor("IRN", "SDN"); vor("UKR", "SDN")
        assertTrue(reihe.takeLast(4).toSet() == setOf("UKR", "IRN", "SDN", "YEM") || reihe.takeLast(5).containsAll(listOf("SDN", "YEM")))
        // breite Verteilung
        assertTrue(rang.first().wert > 80.0)
        assertTrue(rang.last().wert < 30.0)
        assertTrue(rang.first().wert - rang.last().wert > 50.0)
        // Platz
        assertEquals(1, GlobusGesamt.platz(rang, reihe.first()))
        assertEquals(rang.size, GlobusGesamt.platz(rang, reihe.last()))
        assertNull(GlobusGesamt.platz(rang, "XXX"))
    }

    @Test
    fun ranglisteZaehltJedesLandEinmalUndLaesstDieWeltAus() {
        val d = Imf.Datensatz(0L, mapOf(Imf.WACHSTUM to mapOf("DEU" to (2024..2028).associateWith { 1.0 }, Imf.WELT to (2024..2028).associateWith { 3.0 }), Imf.BIP_KOPF_KKP to mapOf("DEU" to mapOf(2026 to 60000.0))), 3)
        assertEquals(setOf("DEU"), GlobusGesamt.laender(d))
        val r = GlobusGesamt.rangliste(d, listOf("DEU", "DEU", "FRA"), 2026, mapOf("DEU" to sicher(), "FRA" to sicher()))
        assertEquals(listOf("DEU"), r.map { it.code })
    }

    // Konflikte aus dem gebuendelten Datensatz: USA und Grossbritannien bekommen den Jemen-Eintrag nicht zugeschrieben
    @Test
    fun konflikteNurImLandOderBeiAngriff() {
        val f = listOf("src/main/assets/globus", "../app/src/main/assets/globus").map { File(it) }.first { it.exists() }
        val karte = GlobusLesen.lesen(File(f, "laender.json").readText())!!
        val s = Konflikte.lesen(File(f, "konflikte.json").readText())!!
        fun anzahl(a3: String) = Konflikte.imLand(s, karte.nachA3(a3)!!).size
        assertEquals(1, Konflikte.fuer(s, karte.nachA3("USA")!!).size)
        assertEquals(0, anzahl("USA"))
        assertEquals(0, anzahl("GBR"))
        // Jemen, Russland, Ukraine, Israel und Iran bleiben betroffen, mit der schwersten Stufe
        for (a3 in listOf("YEM", "RUS", "UKR", "ISR", "IRN", "SDN")) assertTrue(a3, anzahl(a3) > 0)
        assertEquals(Konflikte.fuer(s, karte.nachA3("YEM")!!).size, anzahl("YEM"))
        assertEquals(2, Konflikte.imLand(s, karte.nachA3("RUS")!!).maxOf { it.stufe })
    }

    // Rangliste nur Staaten: 193 UN-Mitglieder plus Taiwan, Kosovo, Palaestina und Vatikan, alle auf der Karte vorhanden
    @Test(timeout = 20_000)
    fun staatenZaehlenUndSindAufDerKarte() {
        assertEquals(197, GlobusGesamt.STAATEN.size)
        val f = listOf("src/main/assets/globus/laender.json", "../app/src/main/assets/globus/laender.json").map { File(it) }.first { it.exists() }
        val karte = GlobusLesen.lesen(f.readText())!!
        for (a3 in GlobusGesamt.STAATEN) assertNotNull(a3, karte.nachA3(a3))
        for (a3 in listOf("BMU", "CYM", "MAC", "HKG", "PRI", "GRL")) assertTrue(a3, !GlobusGesamt.istStaat(a3))
        for (a3 in listOf("DEU", "TWN", "KOS", "UVK", "WBG", "VAT")) assertTrue(a3, GlobusGesamt.istStaat(a3))
        val rang = listOf("DEU", "BMU", "MAC", "TWN").map { GlobusEbenen.Rang(it, 50.0) }
        assertEquals(listOf("DEU", "TWN"), GlobusGesamt.nurStaaten(rang).map { it.code })
    }
}
