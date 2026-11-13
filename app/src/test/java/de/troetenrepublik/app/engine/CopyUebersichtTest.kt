package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.CopyUebersicht.Bestand
import de.troetenrepublik.app.engine.CopyUebersicht.Geschaeft
import de.troetenrepublik.app.market.Investoren
import de.troetenrepublik.app.market.SecInsider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Uebersicht je Gruppe: Positionen, Meistgehandeltes, Einstiegskurs und Abschlag mit kleinen festen Daten.
class CopyUebersichtTest {

    private val T = CopyUebersicht.TAG
    private val jetzt = 1_000 * T

    private fun g(person: String, symbol: String, kauf: Boolean, tageVorJetzt: Int, betrag: Double, preis: Double? = null) =
        Geschaeft(person, person.uppercase(), symbol, kauf, jetzt - tageVorJetzt * T, betrag, preis)

    @Test
    fun positionenSummierenKaeufeMinusVerkaeufe() {
        val l = listOf(
            g("a", "X", true, 50, 10_000.0), g("a", "X", false, 40, 4_000.0),
            g("b", "X", true, 30, 2_000.0),
            g("c", "Y", true, 20, 5_000.0),
        )
        val p = CopyUebersicht.positionen(l)
        assertEquals(listOf("X", "Y"), p.map { it.symbol })
        assertEquals(8_000.0, p[0].wert, 1e-9)
        assertEquals(2, p[0].personen)
        // groesster Halter zuerst
        assertEquals("a", p[0].halter[0].person)
        assertEquals(6_000.0, p[0].halter[0].wert, 1e-9)
        assertEquals(5_000.0, p[1].wert, 1e-9)
    }

    @Test
    fun verkaufOhneVorherigenKaufDrueckNichtInsMinus() {
        // Verkauf vor den Daten: spaeterer Kauf zaehlt voll
        val l = listOf(g("a", "X", false, 60, 9_000.0), g("a", "X", true, 30, 3_000.0))
        assertEquals(3_000.0, CopyUebersicht.positionen(l).single().wert, 1e-9)
        // vollstaendig verkauft: keine Position
        val weg = listOf(g("a", "X", true, 60, 3_000.0), g("a", "X", false, 30, 5_000.0))
        assertTrue(CopyUebersicht.positionen(weg).isEmpty())
    }

    @Test
    fun gemeldeterBestandGehtVor() {
        val l = listOf(g("a", "X", true, 10, 1_000.0))
        val p = CopyUebersicht.positionen(l, listOf(Bestand("a", "A", "X", 50_000.0), Bestand("b", "B", "X", 20_000.0)))
        assertEquals(70_000.0, p.single().wert, 1e-9)
        assertEquals(2, p.single().personen)
        assertEquals(1, CopyUebersicht.positionen(l, listOf(Bestand("b", "B", "Y", 0.0))).size)
    }

    @Test
    fun haelterAufDreiBegrenzt() {
        val l = (1..5).map { g("p$it", "X", true, 10, it * 1_000.0) }
        val p = CopyUebersicht.positionen(l).single()
        assertEquals(5, p.personen)
        assertEquals(listOf("p5", "p4", "p3"), p.halter.map { it.person })
    }

    @Test
    fun meistgehandeltNachZeitraumUndGetrennt() {
        val l = listOf(
            g("a", "X", true, 10, 1_000.0), g("b", "X", true, 20, 3_000.0), g("a", "X", false, 25, 500.0),
            g("a", "X", true, 200, 9_999.0),
            g("c", "Y", false, 5, 7_000.0),
        )
        val d30 = CopyUebersicht.meistgehandelt(l, jetzt, 30)
        assertEquals(listOf("X", "Y"), d30.map { it.symbol })
        val x = d30[0]
        assertEquals(2, x.kaeufe)
        assertEquals(1, x.verkaeufe)
        assertEquals(4_000.0, x.kaufVolumen, 1e-9)
        assertEquals(500.0, x.verkaufVolumen, 1e-9)
        assertEquals(2, x.personen)
        // nach Volumen vorn: Y mit 7.000 vor X mit 4.500
        assertEquals("Y", CopyUebersicht.meistgehandelt(l, jetzt, 30, CopyUebersicht.Ordnung.VOLUMEN)[0].symbol)
        // grosser Zeitraum nimmt das alte Geschaeft mit
        assertEquals(3, CopyUebersicht.meistgehandelt(l, jetzt, 365).first { it.symbol == "X" }.kaeufe)
        // leerer Zeitraum
        assertTrue(CopyUebersicht.meistgehandelt(l, jetzt, 1).isEmpty())
    }

    @Test
    fun zeitraumLaeuftNachMeldetagWennGesetzt() {
        val l = listOf(Geschaeft("i:x", "X", "AAA", true, jetzt - 120 * T, 100.0, 10.0, gemeldet = jetzt - 10 * T))
        assertEquals(1, CopyUebersicht.meistgehandelt(l, jetzt, 30).size)
    }

    // Kurse: Tag 900 = 10, Tag 950 = 20, heute (Tag 1000) = 15
    private val kurse = mapOf("X" to mapOf(900L to 10.0, 950L to 20.0, 1000L to 15.0), "Y" to mapOf(900L to 10.0, 1000L to 12.0))
    private fun kurs(s: String, t: Long): Double? = kurse[s]?.let { KongressAuswertung.Kurse.aus(it).am(t) }
    private fun heute(s: String): Double? = kurse[s]?.let { KongressAuswertung.Kurse.aus(it).letzter }

    @Test
    fun einstiegskursGewichtetNachBetrag() {
        // Kaeufe an Tag 900 (Kurs 10, Betrag 3.000) und Tag 950 (Kurs 20, Betrag 1.000): (3000*10 + 1000*20) / 4000 = 12,5
        val l = listOf(g("a", "X", true, 100, 3_000.0), g("a", "X", true, 50, 1_000.0))
        val e = CopyUebersicht.einstaende(l, ::kurs, ::heute).single()
        assertEquals(12.5, e.einstieg, 1e-9)
        assertEquals(15.0, e.aktuell, 1e-9)
        assertEquals(15.0 / 12.5 - 1, e.abstand, 1e-9)
        assertEquals(2, e.kaeufe)
        assertEquals(4_000.0, e.betrag, 1e-9)
        assertEquals(jetzt - 100 * T, e.erstes)
        assertEquals(jetzt - 50 * T, e.letztes)
        // ueber dem Einstieg: nicht in der Liste „unter“
        assertTrue(CopyUebersicht.unterEinstieg(listOf(e)).isEmpty())
    }

    @Test
    fun unterEinstiegNurDarunterGroessterAbschlagZuerst() {
        // X: Einstieg an Tag 950 bei 20, heute 15 (-25 %); Y: Einstieg bei 10, heute 12 (+20 %)
        val l = listOf(g("a", "X", true, 50, 1_000.0), g("a", "Y", true, 100, 1_000.0), g("b", "Y", true, 100, 500.0))
        val u = CopyUebersicht.unterEinstieg(CopyUebersicht.einstaende(l, ::kurs, ::heute))
        assertEquals(listOf("X"), u.map { it.symbol })
        assertEquals(-0.25, u[0].abstand, 1e-9)
    }

    @Test
    fun verkaufteWerteZaehlenNichtMehr() {
        val l = listOf(g("a", "X", true, 50, 1_000.0), g("a", "X", false, 10, 1_000.0))
        assertTrue(CopyUebersicht.einstaende(l, ::kurs, ::heute).isEmpty())
        // teilweise verkauft: bleibt
        val teil = listOf(g("a", "X", true, 50, 1_000.0), g("a", "X", false, 10, 400.0))
        assertEquals(1, CopyUebersicht.einstaende(teil, ::kurs, ::heute).size)
    }

    @Test
    fun ohneKursOderMitEigenemPreis() {
        // Kauf vor dem ersten Kurs: ohne Einstieg
        assertTrue(CopyUebersicht.einstaende(listOf(g("a", "X", true, 500, 1_000.0)), ::kurs, ::heute).isEmpty())
        // unbekannte Aktie
        assertTrue(CopyUebersicht.einstaende(listOf(g("a", "Z", true, 50, 1_000.0)), ::kurs, ::heute).isEmpty())
        // eigener Preis geht vor dem Schlusskurs: 24 statt 20
        val e = CopyUebersicht.einstaende(listOf(g("a", "X", true, 50, 1_000.0, preis = 24.0)), ::kurs, ::heute).single()
        assertEquals(24.0, e.einstieg, 1e-9)
        assertEquals(15.0 / 24.0 - 1, e.abstand, 1e-9)
    }

    @Test
    fun abschlagDerGruppeMitKaeufern() {
        // X: a kaufte bei 10 (Betrag 1.000), b bei 20 (Betrag 1.000): Schnitt 15, heute 15 -> kein Abschlag
        // Y: a kaufte bei 10 (3.000), b bei 10 (1.000), c bei 10 (1.000): heute 12 -> kein Abschlag
        // Z: drei Kaeufer, Einstieg 20 (Betrag 1.000) und 20 (1.000) und 10 (2.000): Schnitt 15, heute 12 -> -20 %
        val k = mapOf(
            "X" to KongressAuswertung.Kurse.aus(mapOf(900L to 10.0, 950L to 20.0, 1000L to 15.0)),
            "Z" to KongressAuswertung.Kurse.aus(mapOf(900L to 10.0, 950L to 20.0, 1000L to 12.0)),
        )
        val l = listOf(
            g("a", "X", true, 100, 1_000.0), g("b", "X", true, 50, 1_000.0),
            g("a", "Z", true, 50, 1_000.0), g("b", "Z", true, 50, 1_000.0), g("c", "Z", true, 100, 2_000.0),
        )
        val e = CopyUebersicht.einstaende(l, { s, t -> k[s]?.am(t) }, { s -> k[s]?.letzter })
        val a = CopyUebersicht.abschlaege(e)
        assertEquals(listOf("Z"), a.map { it.symbol })
        assertEquals(15.0, a[0].einstieg, 1e-9)
        assertEquals(-0.2, a[0].abstand, 1e-9)
        assertEquals(3, a[0].kaeufer)
        assertEquals(4_000.0, a[0].betrag, 1e-9)
    }

    @Test
    fun abschlaegeSortiertNachAbschlag() {
        val k = mapOf(
            "P" to KongressAuswertung.Kurse.aus(mapOf(900L to 10.0, 1000L to 9.0)),
            "Q" to KongressAuswertung.Kurse.aus(mapOf(900L to 10.0, 1000L to 5.0)),
        )
        val l = listOf(g("a", "P", true, 100, 1_000.0), g("a", "Q", true, 100, 1_000.0))
        val a = CopyUebersicht.abschlaege(CopyUebersicht.einstaende(l, { s, t -> k[s]?.am(t) }, { s -> k[s]?.letzter }))
        assertEquals(listOf("Q", "P"), a.map { it.symbol })
    }

    private fun pos(k: String, stueck: Double, wert: Double, art: String = "aktie") = Investoren.Position("N $k", k, "C$k", wert, stueck, art)

    @Test
    fun investorenAusQuartalsvergleich() {
        val neu = Investoren.Quartal("2026-06-30", "2026-08-14", 0.0, listOf(pos("A", 200.0, 4_000.0), pos("B", 100.0, 1_000.0), pos("C", 50.0, 500.0), pos("O", 10.0, 99.0, "call")))
        val alt = Investoren.Quartal("2026-03-31", "2026-05-15", 0.0, listOf(pos("A", 100.0, 1_500.0), pos("B", 100.0, 900.0), pos("D", 40.0, 800.0)))
        val inv = Investoren.Investor("x", "Xaver", "X Inc", "0000000001", "", "", listOf(neu, alt))
        val (trades, bestand) = CopyUebersicht.ausInvestoren(listOf(inv))
        // Bestand: Aktien des letzten Quartals, keine Optionen
        assertEquals(setOf("A", "B", "C"), bestand.map { it.symbol }.toSet())
        assertEquals(4_000.0, bestand.first { it.symbol == "A" }.wert, 1e-9)
        // A aufgestockt um 100 Stueck zu 20, C neu mit 50 zu 10, D verkauft (40 Stueck zu 20); B unveraendert
        val a = trades.first { it.symbol == "A" }
        assertTrue(a.kauf)
        assertEquals(2_000.0, a.betrag, 1e-9)
        assertEquals(20.0, a.preis!!, 1e-9)
        assertEquals(CopyUebersicht.tag("2026-08-14"), a.gemeldet)
        assertEquals(CopyUebersicht.tag("2026-06-30"), a.datum)
        assertEquals(500.0, trades.first { it.symbol == "C" }.betrag, 1e-9)
        val d = trades.first { it.symbol == "D" }
        assertFalse(d.kauf)
        assertEquals(800.0, d.betrag, 1e-9)
        assertTrue(trades.none { it.symbol == "B" })
    }

    @Test
    fun investorOhneVorquartalHatNurBestand() {
        val q = Investoren.Quartal("2026-06-30", "2026-08-14", 0.0, listOf(pos("A", 10.0, 100.0)))
        val (trades, bestand) = CopyUebersicht.ausInvestoren(listOf(Investoren.Investor("x", "X", "X", "1", "", "", listOf(q))))
        assertTrue(trades.isEmpty())
        assertEquals(1, bestand.size)
    }

    @Test
    fun insiderMitPreisUndBestand() {
        fun s(d: String, kauf: Boolean, n: Double, preis: Double, nach: Double?) =
            SecInsider.Geschaeft("a$d$kauf", "AAA", "A Corp", "1", "Meier", "S", "CEO", kauf, d, d, n, preis, n * preis, nach)
        val l = listOf(s("2026-05-01", true, 100.0, 10.0, 600.0), s("2026-06-01", true, 50.0, 12.0, 650.0), s("2026-07-01", false, 10.0, 15.0, 640.0))
        val (trades, bestand) = CopyUebersicht.ausInsidern(l)
        assertEquals(3, trades.size)
        assertEquals(10.0, trades[0].preis!!, 1e-9)
        assertEquals("s:Meier", trades[0].person)
        // Bestand nach dem letzten Geschaeft: 640 Stueck zu 15
        assertEquals(9_600.0, bestand.single().wert, 1e-9)
        // Einstieg aus dem gemeldeten Preis: (1000 * 10 + 600 * 12) / 1600 = 10,75; Kurs heute 9
        val e = CopyUebersicht.einstaende(trades, { _, _ -> null }, { 9.0 }).single()
        assertEquals(10.75, e.einstieg, 1e-9)
        assertTrue(e.abstand < 0)
    }

    @Test
    fun kurseSpeicherRundlauf() {
        val e1 = KurseSpeicher.Eintrag("AAA", 123_456L, 365, KongressAuswertung.Kurse.aus(mapOf(100L to 1.5, 101L to 2.5)))
        val e2 = KurseSpeicher.Eintrag("BBB", 5L, 180, KongressAuswertung.Kurse(IntArray(0), FloatArray(0)))
        val l = KurseSpeicher.lesen(KurseSpeicher.schreiben(listOf(e1, e2)))!!
        assertEquals(listOf("AAA", "BBB"), l.map { it.symbol })
        assertEquals(123_456L, l[0].am)
        assertEquals(365, l[0].tage)
        assertEquals(2.5, l[0].kurse.letzter!!, 1e-6)
        assertEquals(1.5, l[0].kurse.am(100 * T)!!, 1e-6)
        assertTrue(l[1].kurse.leer)
        assertNull(KurseSpeicher.lesen(byteArrayOf(1, 2, 3)))
        assertNotNull(KurseSpeicher.lesen(KurseSpeicher.schreiben(emptyList())))
    }

    @Test
    fun kursBereichUndFrische() {
        assertEquals("6mo" to 180, KurseSpeicher.bereich(100))
        assertEquals("1y" to 365, KurseSpeicher.bereich(300))
        assertEquals("2y" to 730, KurseSpeicher.bereich(600))
        assertEquals("5y" to 1825, KurseSpeicher.bereich(1500))
        val e = KurseSpeicher.Eintrag("A", 1_000_000L, 365, KongressAuswertung.Kurse(IntArray(0), FloatArray(0)))
        assertTrue(KurseSpeicher.frisch(e, 1_000_000L + 3_600_000L, 180))
        // zu alt
        assertFalse(KurseSpeicher.frisch(e, 1_000_000L + 7 * 3_600_000L, 180))
        // reicht nicht weit genug zurueck
        assertFalse(KurseSpeicher.frisch(e, 1_000_000L + 3_600_000L, 730))
        assertFalse(KurseSpeicher.frisch(null, 0L, 1))
    }
}
