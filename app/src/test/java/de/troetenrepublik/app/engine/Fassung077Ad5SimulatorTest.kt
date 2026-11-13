package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.InsiderSimulator.Betrag
import de.troetenrepublik.app.engine.InsiderSimulator.Je
import de.troetenrepublik.app.engine.InsiderSimulator.Regeln
import de.troetenrepublik.app.market.SecInsider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

// [0.77 AD5] Insider-Simulator: Cluster nach verstellbaren Regeln und ihre Rendite gegen den S&P 500.
class Fassung077Ad5SimulatorTest {

    private fun kauf(k: String, person: String, tag: String, wert: Double, rolle: String = "S") =
        SecInsider.Geschaeft(a = "$k-$person-$tag", k = k, e = "$k Inc.", p = person, r = rolle, kauf = true, d = tag, f = tag, wert = wert)

    // Drei Kaeufer in acht Tagen, dann ein einzelner Kauf einen Monat spaeter
    private val xyz = listOf(
        kauf("XYZ", "A1", "2025-07-01", 20_000.0), kauf("XYZ", "A2", "2025-07-05", 30_000.0),
        kauf("XYZ", "A3", "2025-07-08", 40_000.0, rolle = "N"), kauf("XYZ", "A4", "2025-08-01", 10_000.0),
    )

    private fun tage(k: List<InsiderSimulator.Cl>) = k.map { it.tag }

    @Test(timeout = 20_000)
    fun standardregelFindetDenClusterAmMeldetagDesDrittenKaeufers() {
        val c = InsiderSimulator.cluster(xyz, InsiderSimulator.STANDARD)
        assertEquals(listOf("2025-07-08"), tage(c))
        assertEquals(3, c.single().insider)
        assertEquals(90_000.0, c.single().betrag, 0.0)
        assertTrue(InsiderSimulator.STANDARD.standard)
    }

    @Test(timeout = 20_000)
    fun mindestzahlUndFenster() {
        // zwei Insider: der Cluster entsteht schon am 5. Juli, der 8. Juli liegt in der Sperre von 90 Tagen
        assertEquals(listOf("2025-07-05"), tage(InsiderSimulator.cluster(xyz, Regeln(mindest = 2))))
        assertEquals(listOf("2025-07-01"), tage(InsiderSimulator.cluster(xyz, Regeln(mindest = 1))))
        // fuenf Tage Fenster: am 8. Juli nur zwei Kaeufer (5. und 8.)
        assertTrue(InsiderSimulator.cluster(xyz, Regeln(fenster = 5)).isEmpty())
        assertEquals(listOf("2025-07-05"), tage(InsiderSimulator.cluster(xyz, Regeln(mindest = 2, fenster = 5))))
        assertTrue(InsiderSimulator.cluster(xyz, Regeln(mindest = 4)).isEmpty())
    }

    @Test(timeout = 20_000)
    fun rolleBetragJeKaufUndJeCluster() {
        // nur Fuehrung: der Grossaktionaer (N) faellt weg, bei drei Kaeufern bleibt kein Cluster
        assertTrue(InsiderSimulator.cluster(xyz, Regeln(nurFuehrung = true)).isEmpty())
        assertEquals(listOf("2025-07-05"), tage(InsiderSimulator.cluster(xyz, Regeln(mindest = 2, nurFuehrung = true))))
        // je Kauf ab 25.000: der Kauf vom 1. Juli zaehlt nicht
        assertTrue(InsiderSimulator.cluster(xyz, Regeln(betrag = Betrag(Je.KAUF, 25_000.0))).isEmpty())
        assertEquals(listOf("2025-07-08"), tage(InsiderSimulator.cluster(xyz, Regeln(mindest = 2, betrag = Betrag(Je.KAUF, 25_000.0)))))
        // je Cluster: Summe der Kaeufe im Fenster
        assertEquals(listOf("2025-07-08"), tage(InsiderSimulator.cluster(xyz, Regeln(betrag = Betrag(Je.CLUSTER, 90_000.0)))))
        assertTrue(InsiderSimulator.cluster(xyz, Regeln(betrag = Betrag(Je.CLUSTER, 250_000.0))).isEmpty())
    }

    // Kurse vom 1. Juni 2025 an, jeden Tag: Aktie 100 + t, Index 1000 + 2 t
    private val t0 = LocalDate.parse("2025-06-01").toEpochDay()

    private fun reihe(tage: Int, start: Double, steigung: Double) =
        KongressAuswertung.Kurse.aus((0 until tage).associate { (t0 + it) to start + steigung * it })

    private val aktie = reihe(500, 100.0, 1.0)
    private val index = reihe(500, 1000.0, 2.0)

    private fun rechnen(r: Regeln, kurse: (String) -> KongressAuswertung.Kurse? = { aktie }, ix: KongressAuswertung.Kurse? = index) =
        InsiderSimulator.rechnen(InsiderSimulator.cluster(xyz, r), kurse, ix, r, "2026-09-30", "2025-04-01")

    @Test(timeout = 20_000)
    fun renditeGegenDenIndex() {
        val e = rechnen(InsiderSimulator.STANDARD)!!
        // Einstieg am 9. Juli (Tag 38) zu 138, nach 60 Handelstagen 198; Index 1076 und 1196
        assertEquals(198.0 / 138.0 - 1, e.rendite, 1e-4)
        assertEquals(1196.0 / 1076.0 - 1, e.index, 1e-4)
        assertEquals(1.0, e.treffer, 0.0)
        assertEquals(1, e.ausgewertet)
        assertEquals(60, e.halten)
        assertEquals(listOf(2025), e.jahre.map { it.j })
        assertEquals("XYZ", e.bester.k)
        assertEquals(21, e.kurve.size)
        assertEquals(60.0, e.kurve.last()[0], 0.0)
        assertEquals(0.0, e.kurve.first()[1], 0.0)
    }

    @Test(timeout = 20_000)
    fun haltedauerKurveUndKosten() {
        assertEquals(21, rechnen(Regeln(halten = 20))!!.kurve.size)
        val l = rechnen(Regeln(halten = 250))!!.kurve
        assertEquals(250.0, l.last()[0], 0.0)
        assertEquals(240.0, l[l.size - 2][0], 0.0)
        // ein Prozent je Kauf und Verkauf, nur bei der Aktie
        val netto = rechnen(Regeln(kosten = 1.0))!!
        assertEquals((198.0 / 138.0) * 0.99 / 1.01 - 1, netto.rendite, 1e-4)
        assertEquals(1196.0 / 1076.0 - 1, netto.index, 1e-4)
        assertTrue(netto.rendite < rechnen(InsiderSimulator.STANDARD)!!.rendite)
        assertEquals(-0.01980, InsiderSimulator.nachKosten(0.0, 0.01), 1e-4)
    }

    @Test(timeout = 20_000)
    fun ohneKurseOffenUndOhneIndex() {
        // Kurse nur 90 Tage: 60 Handelstage nach dem 9. Juli reichen, 120 nicht
        val kurz = reihe(120, 100.0, 1.0)
        val kurzIx = reihe(120, 1000.0, 2.0)
        assertNotNull(rechnen(InsiderSimulator.STANDARD, { kurz }, kurzIx))
        assertNull(rechnen(Regeln(halten = 120), { kurz }, kurzIx))
        // Aktie ohne Kurse
        assertNull(rechnen(InsiderSimulator.STANDARD, { null }))
        // ohne Index keine Rechnung
        assertNull(rechnen(InsiderSimulator.STANDARD, ix = null))
        val m = InsiderSimulator.rechnen(
            InsiderSimulator.cluster(xyz + listOf(kauf("ABC", "B1", "2025-07-02", 1.0e5)), Regeln(mindest = 1)),
            { s -> if (s == "XYZ") aktie else null }, index, Regeln(mindest = 1), "2026-09-30", "2025-04-01",
        )!!
        assertEquals(1, m.ohneKurse)
        assertEquals(1, m.ausgewertet)
        assertEquals(2, m.cluster)
    }

    @Test(timeout = 60_000)
    fun gebuendelteDatenGebenDieZahlenDerReferenzrechnung() {
        val text = listOf("src/main/assets/copytrading/sec.json", "../app/src/main/assets/copytrading/sec.json").map { File(it) }.first { it.exists() }.readText()
        val d = SecInsider.lesen(text)!!
        // Dieselben Daten, mit tools/sec/cluster_rueckschau.py (Regel 3 in 10 Tagen, Sperre 90 Tage) gezaehlt
        assertEquals(980, InsiderSimulator.cluster(d.geschaefte, InsiderSimulator.STANDARD).size)
        assertEquals(2000, InsiderSimulator.cluster(d.geschaefte, Regeln(mindest = 2)).size)
        assertEquals(5808, InsiderSimulator.cluster(d.geschaefte, Regeln(mindest = 1)).size)
        assertEquals(290, InsiderSimulator.cluster(d.geschaefte, Regeln(mindest = 5)).size)
        val zahlen = InsiderSimulator.MINDEST.map { InsiderSimulator.cluster(d.geschaefte, Regeln(mindest = it)).size }
        assertEquals(zahlen, zahlen.sortedDescending())
        val fuehrung = InsiderSimulator.cluster(d.geschaefte, Regeln(nurFuehrung = true)).size
        assertTrue(fuehrung in 1..980)
        println("Simulator: Standard 980 Cluster, nur Fuehrung $fuehrung, ab 250.000 je Cluster ${InsiderSimulator.cluster(d.geschaefte, Regeln(betrag = Betrag(Je.CLUSTER, 250_000.0))).size}")
    }
}
