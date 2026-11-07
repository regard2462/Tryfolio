package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.Fruehwarnung.Ampel
import de.troetenrepublik.app.market.Markterwartung
import de.troetenrepublik.app.market.Prognose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 5.92: Maerkte (Blasen-Detektor, Zinserwartung, Sektorrotation, Branchendepot, Sorgen-Barometer,
// Was der Markt erwartet).
class Fassung592MaerkteTest {

    private val tag = 86_400_000L

    // Tageskurse ab einem festen Tag, jeder Kalendertag ein Kurs.
    private fun kurse(werte: List<Double>, start: Long = 1_700_000_000_000L): List<Candle> =
        werte.mapIndexed { i, v -> Candle(start + i * tag, v, v, v, v) }

    @Test fun blasenSchwellen() {
        assertEquals(Ampel.GRUEN, Blasen.kgv(16.0)!!.ampel)
        assertEquals(Ampel.GELB, Blasen.kgv(22.0)!!.ampel)
        assertEquals(Ampel.ROT, Blasen.kgv(25.0)!!.ampel)
        assertNull(Blasen.kgv(-3.0))
        assertNull(Blasen.kgv(null))
        // Wilshire 60.000 Punkte (etwa 60 Billionen Dollar) bei 30 Billionen BIP: 200 %
        val b = Blasen.buffett(60_000.0, 30e12)!!
        assertEquals(200.0, b.wert, 1e-9)
        assertEquals(Ampel.ROT, b.ampel)
        assertEquals(Ampel.GRUEN, Blasen.buffett(25_000.0, 30e12)!!.ampel)
        // KGV 25 heisst 4 % Gewinnrendite; bei 5 % Anleihezins minus einen Punkt
        val p = Blasen.praemie(25.0, 5.0)!!
        assertEquals(-1.0, p.wert, 1e-9)
        assertEquals(Ampel.ROT, p.ampel)
        assertEquals(Ampel.GRUEN, Blasen.praemie(12.5, 4.0)!!.ampel)
        // Gewinnwachstum: viel Wachstum ist gruen (spricht gegen eine Blase)
        assertEquals(Ampel.GRUEN, Blasen.gewinn(12.0)!!.ampel)
        assertEquals(Ampel.ROT, Blasen.gewinn(1.0)!!.ampel)
        assertEquals(Ampel.ROT, Blasen.breite(20.0, 5.0)!!.ampel)
        assertEquals(Ampel.GRUEN, Blasen.breite(10.0, 12.0)!!.ampel)
    }

    @Test fun blasenKonzentrationUndGewinne() {
        // Zehn Riesen mit je 100, 90 kleine mit je 10: 1000 von 1900
        val w = List(10) { 100.0 } + List(90) { 10.0 }
        val k = Blasen.konzentration(w)!!
        assertEquals(1000.0 / 1900.0 * 100, k.wert, 1e-9)
        assertEquals(Ampel.ROT, k.ampel)
        assertNull(Blasen.konzentration(List(20) { 1.0 }))
        // Alle mit 10 % mehr Gewinn erwartet; Verlustbringer zaehlen nicht
        val g = List(60) { Blasen.Gewinne(1000.0 + it, 50.0, 2.0, 2.2) } + Blasen.Gewinne(5000.0, 10.0, -1.0, 3.0)
        assertEquals(10.0, Blasen.gewinnwachstum(g)!!, 1e-9)
        assertNull(Blasen.gewinnwachstum(g.take(10)))
        // Eine absurde Erwartung zaehlt hoechstens doppelt
        val ausreisser = List(60) { Blasen.Gewinne(1000.0, 50.0, 2.0, 2.0) } + Blasen.Gewinne(1000.0, 50.0, 2.0, 200.0)
        assertEquals(1.0 / 61 * 100, Blasen.gewinnwachstum(ausreisser)!!, 1e-9)
    }

    @Test fun blasenRangUndGesamt() {
        // Steigende Reihe: die letzte Woche so hoch wie nie
        assertEquals(100.0, Blasen.rang(List(100) { it })!!, 1e-9)
        // Fallende Reihe: so wenig wie nie
        assertEquals(0.0, Blasen.rang(List(100) { 1000 - it })!!, 1e-9)
        assertNull(Blasen.rang(List(30) { it }))
        val rot = Blasen.Indikator("a", 1.0, Ampel.ROT)
        val gruen = Blasen.Indikator("b", 1.0, Ampel.GRUEN)
        val gelb = Blasen.Indikator("c", 1.0, Ampel.GELB)
        assertEquals(Blasen.Stufe.HOCH, Blasen.gesamt(listOf(rot, rot, gelb)))
        assertEquals(Blasen.Stufe.ERHOEHT, Blasen.gesamt(listOf(rot, gruen, gelb)))
        assertEquals(Blasen.Stufe.GERING, Blasen.gesamt(listOf(gruen, gruen, gelb)))
        assertNull(Blasen.gesamt(listOf(rot, rot)))
    }

    @Test fun blasenSektorUndBip() {
        // Ein Jahr lang stetig auf das 2,5-Fache: 150 % im Jahr und weit ueber der 200-Tage-Linie
        val heiss = kurse((0..400).map { 100.0 * Math.pow(2.5, it / 365.0) })
        val s = Blasen.sektor("SMH", heiss)!!
        assertTrue(s.jahr > 140)
        assertTrue(s.zu200 > 20)
        assertEquals(Ampel.ROT, s.ampel)
        val ruhig = Blasen.sektor("XLU", kurse(List(400) { 50.0 }))!!
        assertEquals(Ampel.GRUEN, ruhig.ampel)
        assertNull(Blasen.sektor("X", kurse(List(100) { 1.0 })))
        val text = """[{"page":1},[{"date":"2025","value":30769700000000},{"date":"2024","value":29298013000000},{"date":"2026","value":null}]]"""
        assertEquals(2025 to 30769700000000.0, Blasen.bipLesen(text))
        assertNull(Blasen.bipLesen("kaputt"))
    }

    @Test fun zinserwartungKontrakte() {
        val k = Zinserwartung.kontrakte(2026, 11, 3)
        assertEquals(listOf("ZQX26.CBT", "ZQZ26.CBT", "ZQF27.CBT"), k.map { it.symbol })
        assertEquals(2027 to 1, k[2].jahr to k[2].monat)
        val alle = Zinserwartung.kontrakte(2026, 10)
        val kurse = alle.mapIndexed { i, c -> c.symbol to (96.10 - i * 0.05) }.toMap()
        val pfad = Zinserwartung.pfad(alle, kurse + ("ZQV26.CBT" to 150.0))
        // Kurs 150 ist ein Fehler und faellt weg
        assertEquals(alle.size - 1, pfad.size)
        val e = Zinserwartung.erwartung(pfad)!!
        assertEquals(100 - 96.05, e.heute.zins, 1e-9)
        assertEquals(e.heute.zins + 0.15, e.in3!!.zins, 1e-9)
        // Nach zwoelf Monaten 0,6 Punkte hoeher: 2,4 Schritte zu je 0,25
        assertEquals(2.4, e.schritte!!, 1e-9)
        assertNull(Zinserwartung.erwartung(pfad.take(1)))
    }

    @Test fun sektorRotationUndGrund() {
        val start = 1_700_000_000_000L
        // Drei Monate: Halbleiter und Software erst stark, im letzten Monat schwach; Versorger und Basiskonsum umgekehrt
        fun reihe(vorher: Double, zuletzt: Double) = kurse((0..120).map { i -> if (i <= 90) 100 * (1 + vorher * i / 90) else 100 * (1 + vorher) * (1 + zuletzt * (i - 90) / 30) }, start)
        val k = Branchentrends.BRANCHEN.associate { b ->
            b.symbol to when (b.symbol) {
                "SMH", "IGV" -> reihe(0.20, -0.08)
                "XLU", "XLP" -> reihe(-0.05, 0.06)
                else -> reihe(0.02, 0.0)
            }
        }
        val r = Branchentrends.rotation(k, vix = 3.0, zins = 0.0)!!
        assertEquals(setOf("XLU", "XLP"), r.nach.map { it.symbol }.toSet())
        assertEquals(setOf("SMH", "IGV"), r.aus.map { it.symbol }.toSet())
        assertEquals(Branchentrends.Grund.SICHERHEIT, r.grund)
        // Umgekehrt: Risikofreude
        val b = Branchentrends.BRANCHEN.associateBy { it.symbol }
        assertEquals(Branchentrends.Grund.RISIKOFREUDE, Branchentrends.grund(listOf(b["SMH"]!!, b["XLY"]!!), listOf(b["XLU"]!!, b["XLP"]!!), null, null))
        // Aus Rohstoffen und Halbleitern in Defensive: Sicherheit, auch ohne steigenden VIX
        assertEquals(Branchentrends.Grund.SICHERHEIT, Branchentrends.grund(listOf(b["XLU"]!!, b["KIE"]!!, b["XLP"]!!), listOf(b["GDX"]!!, b["SMH"]!!, b["XME"]!!), 0.0, -0.3))
        assertEquals(Branchentrends.Grund.ABSICHERUNG, Branchentrends.grund(listOf(b["GDX"]!!), emptyList(), 4.0, null))
        assertEquals(Branchentrends.Grund.ZINSANSTIEG, Branchentrends.grund(listOf(b["KBE"]!!, b["XLI"]!!, b["SMH"]!!), emptyList(), 0.0, 0.4))
        // Alle gleich: keine Umschichtung
        val gleich = Branchentrends.BRANCHEN.associate { it.symbol to reihe(0.02, 0.01) }
        val keine = Branchentrends.rotation(gleich)!!
        assertTrue(keine.nach.isEmpty() && keine.aus.isEmpty())
        assertNull(Branchentrends.rotation(emptyMap()))
    }

    @Test fun branchenDepot() {
        val k = kurse((0..40).map { 100.0 + it })
        val d = Branchentrends.depot(k, 30)
        assertEquals(1000.0, d.first().second, 1e-9)
        assertEquals(1000.0 * 140 / 110, d.last().second, 1e-9)
        assertTrue(Branchentrends.depot(k, 365).isEmpty())
        assertEquals(2, Branchentrends.depot(k, 1).size)
    }

    @Test fun sorgenBarometer() {
        // Ein Jahr wenig gelesen, die letzte Woche sehr viel: extreme Angst
        val a = List(358) { 300 } + List(7) { 3000 }
        val b = List(358) { 200 } + List(7) { 900 }
        val s = Stimmungsbarometer.sorgen(listOf(a, b), 1_800_000_000_000L)!!
        assertTrue(s.wert < 5)
        assertEquals(Stimmungsbarometer.Stufe.EXTREME_ANGST, s.stufe)
        assertTrue(s.verlauf.isNotEmpty())
        assertEquals(1_800_000_000_000L, s.verlauf.last().first)
        // Ruhige letzte Woche nach viel Sorge: Gier-Seite
        val ruhig = Stimmungsbarometer.sorgen(listOf(List(200) { 1000 } + List(7) { 10 }), 0L)!!
        assertTrue(ruhig.wert > 95)
        assertNull(Stimmungsbarometer.sorgen(listOf(List(30) { 1 }), 0L))
    }

    private fun markt(titel: String, p: Double) =
        Prognose.Markt(Prognose.Quelle.POLYMARKET, titel, titel, titel, listOf(Prognose.Seite("Yes", p, p, p), Prognose.Seite("No", 1 - p, 1 - p, 1 - p)), null, 1.0, 0L, true)

    private fun ereignis(titel: String, ende: Long, volumen: Double, maerkte: List<Prognose.Markt>) =
        Prognose.Ereignis(Prognose.Quelle.POLYMARKET, titel, titel, "", "", "", "", volumen, 0.0, ende, maerkte, "")

    @Test fun markterwartungWahl() {
        val fed = Markterwartung.THEMEN.first { it.schluessel == "fed" }
        val liste = listOf(
            ereignis("Fed Decision in December?", 300, 10.0, listOf(markt("No change", 0.7))),
            ereignis("Fed Decision in October?", 200, 5.0, listOf(markt("No change", 0.9))),
            ereignis("Fed Decision in September?", 50, 99.0, listOf(markt("No change", 1.0))),
            ereignis("How many dissent at the Fed meeting?", 100, 1000.0, listOf(markt("2", 0.5))),
        )
        // Abgelaufene fallen weg, dann das naechste
        assertEquals("Fed Decision in October?", Markterwartung.waehlen(fed, liste, 150)!!.titel)
        val iran = Markterwartung.THEMEN.first { it.schluessel == "iran" }
        val l2 = listOf(ereignis("US-Iran deal?", 500, 10.0, listOf(markt("Yes", 0.2))), ereignis("Will the U.S. invade Iran?", 600, 80.0, listOf(markt("Yes", 0.1))))
        assertEquals("Will the U.S. invade Iran?", Markterwartung.waehlen(iran, l2, 0)!!.titel)
        assertNull(Markterwartung.waehlen(iran, emptyList(), 0))
    }

    @Test fun markterwartungAusgaenge() {
        // Ausgaenge schliessen sich aus: die wahrscheinlichsten, ohne die unter 3 %
        val e = ereignis("Fed", 1, 1.0, listOf(markt("25 bps cut", 0.72), markt("No change", 0.25), markt("50 bps cut", 0.02), markt("Hike", 0.01)))
        assertEquals(listOf("25 bps cut", "No change"), Markterwartung.ausgaenge(e).map { it.first })
        // Schwellen (Summe weit ueber 1): die nahe 50 %, nach Titel
        val oel = ereignis("WTI", 1, 1.0, listOf(markt("↑ 80", 0.95), markt("↑ 90", 0.55), markt("↑ 100", 0.2), markt("↓ 70", 0.4), markt("↓ 60", 0.97)))
        assertEquals(listOf("↑ 100", "↑ 90", "↓ 70"), Markterwartung.ausgaenge(oel).map { it.first })
        val ja = ereignis("US recession by end of 2026?", 1, 1.0, listOf(markt("", 0.12)))
        assertEquals(listOf("Yes" to 0.12), Markterwartung.ausgaenge(ja))
    }

    @Test fun zeitraeumeDerHeatmapPassenZuDenBranchen() {
        // Die Heatmap nutzt die Zeitraeume der Branchen in derselben Reihenfolge wie die Zeitleiste
        assertEquals(listOf(1, 7, 30, 91, 182, 365), Branchentrends.ZEITRAEUME)
        assertNotNull(Branchentrends.veraenderung(kurse(List(400) { 1.0 + it }), 365))
    }
}
