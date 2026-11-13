package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.EinstiegVergleich.Kosten
import de.troetenrepublik.app.i18n.EinstiegTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

// Sparplan-Rueckblick (0.78): Einmalanlage gegen Raten ueber alle Startmonate, Ruecksetzer kaufen gegen stur sparen.
class Fassung078U27EinstiegTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private fun am(datum: String): Long = LocalDate.parse(datum).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private fun kerze(datum: String, kurs: Double) = Candle(am(datum), kurs, kurs, kurs, kurs)
    private val frei = Kosten(0.0, 0.0, 0.0)

    @Test
    fun monatsendenNimmtLetztenKursUndFuelltLuecken() {
        val m = EinstiegVergleich.monatsenden(
            listOf(kerze("2024-01-10", 10.0), kerze("2024-01-31", 11.0), kerze("2024-02-15", 12.0), kerze("2024-05-02", 15.0), kerze("2024-05-03", 0.0)),
            zone,
        )
        // Januar, Februar, Maerz und April mit dem Stand vom Februar, Mai
        assertEquals(listOf(11.0, 12.0, 12.0, 12.0, 15.0), m.map { it.kurs })
        assertEquals(am("2024-01-31"), m.first().t)
        assertTrue(EinstiegVergleich.monatsenden(emptyList(), zone).isEmpty())
    }

    @Test
    fun einmalGegenRatenVonHandGerechnet() {
        val kurse = listOf(100.0, 200.0, 400.0) + List(12) { 400.0 }
        val v = EinstiegVergleich.einmalGegenRaten(kurse, 200.0, 2, 2, frei)!!
        assertEquals(13, v.faelle.size)
        val erster = v.faelle.first()
        // 2 Stueck gegen 1 + 0,5 Stueck, bewertet zu 400
        assertEquals(800.0, erster.einmal, 1e-9)
        assertEquals(600.0, erster.raten, 1e-9)
        assertEquals(100.0 / 3, erster.vorsprung, 1e-9)
        assertEquals(0, v.bester.start)
        assertEquals(2, v.vorn)
        // Gleichstand bei festem Kurs zaehlt nicht als vorn
        assertEquals(0.0, v.median, 1e-9)
    }

    @Test
    fun steigendeKurseEinmalVornFallendeRatenVorn() {
        val hoch = List(40) { 100.0 * Math.pow(1.01, it.toDouble()) }
        val runter = hoch.reversed()
        val a = EinstiegVergleich.einmalGegenRaten(hoch, 1200.0, 12)!!
        assertEquals(28, a.faelle.size)
        assertEquals(28, a.vorn)
        assertEquals(100.0, a.anteil, 1e-9)
        assertTrue(a.mittel > 0 && a.median > 0)
        val b = EinstiegVergleich.einmalGegenRaten(runter, 1200.0, 12)!!
        assertEquals(0, b.vorn)
        // Bargeld daempft den Rueckgang der Raten
        assertTrue(b.rueckgangRaten > b.rueckgangEinmal)
        assertTrue(b.rueckgangEinmal < 0)
    }

    @Test
    fun kostenEinmalOrderRatenSparplan() {
        val flach = List(20) { 50.0 }
        val v = EinstiegVergleich.einmalGegenRaten(flach, 600.0, 6, 6, Kosten(0.0, 1.0, 0.0))!!
        // Eine Ordergebuehr von 1 auf 600, die Raten kosten nichts
        assertEquals(599.0, v.faelle.first().einmal, 1e-9)
        assertEquals(600.0, v.faelle.first().raten, 1e-9)
        assertEquals(0, v.vorn)
        // Spanne von 100 Basispunkten: Kauf 0,5 % ueber dem Kurs
        assertEquals(100.0 / 50.25, EinstiegVergleich.stueck(100.0, 50.0, 0.0, 100.0), 1e-9)
        assertEquals(0.0, EinstiegVergleich.stueck(1.0, 50.0, 1.0, 0.0), 0.0)
    }

    @Test
    fun zuKurzeReiheErgibtNichts() {
        assertNull(EinstiegVergleich.einmalGegenRaten(List(20) { 10.0 }, 1200.0, 12))
        assertNull(EinstiegVergleich.einmalGegenRaten(List(40) { 10.0 }, 1200.0, 12, 6))
        assertNotNull(EinstiegVergleich.einmalGegenRaten(List(24) { 10.0 }, 1200.0, 12))
        assertNull(EinstiegVergleich.ruecksetzerRollierend(List(40) { 10.0 }, 100.0, 0.1, 12, 36))
    }

    @Test
    fun laengererHorizontZaehltWenigerStartmonate() {
        val kurse = List(100) { 100.0 + it }
        assertEquals(88, EinstiegVergleich.einmalGegenRaten(kurse, 1200.0, 12)!!.faelle.size)
        assertEquals(40, EinstiegVergleich.einmalGegenRaten(kurse, 1200.0, 12, 60)!!.faelle.size)
    }

    @Test
    fun histogrammHatNullAlsGrenze() {
        val werte = listOf(-7.2, -3.1, -0.4, 0.3, 0.6, 2.2, 4.9, 12.5)
        val h = EinstiegVergleich.histogramm(werte)!!
        assertEquals(werte.size, h.klassen.sumOf { it.anzahl })
        assertTrue(h.klassen.any { Math.abs(it.von) < 1e-9 })
        assertEquals(3, h.klassen.filter { it.von < 0 }.sumOf { it.anzahl })
        assertTrue(h.klassen.size in 8..24)
        assertNull(EinstiegVergleich.histogramm(emptyList()))
        assertEquals(3, EinstiegVergleich.histogramm(listOf(1.0, 1.0, 1.0))!!.klassen.sumOf { it.anzahl })
    }

    @Test
    fun ruecksetzerVonHandGerechnet() {
        val kurse = listOf(100.0, 100.0, 80.0, 100.0, 100.0)
        val hoch = EinstiegVergleich.hochs(kurse)
        val l = EinstiegVergleich.ruecksetzer(kurse, hoch, 100.0, 0.1, null, frei)!!
        // stur: 1 + 1 + 1,25 + 1 Stueck; wartend: 300 zu 80, die vierte Rate bleibt liegen
        assertEquals(425.0, l.endStur, 1e-9)
        assertEquals(475.0, l.endWarten, 1e-9)
        assertEquals(400.0, l.eingezahlt, 1e-9)
        assertEquals(1, l.kaeufe)
        assertEquals(100.0, l.restBar, 1e-9)
        // Wartezeiten der vier Raten: 2, 1, 0 und 1 Monat
        assertEquals(1.0, l.wartenMittel, 1e-9)
        assertEquals(2, l.wartenLaengste)
        assertEquals(5, l.verlaufStur.size)
        assertEquals(listOf(100.0, 200.0, 0.0, 100.0, 100.0), l.bargeld)
        assertEquals(l.endWarten, l.verlaufWarten.last(), 1e-9)
    }

    @Test
    fun hoechstwartezeitErzwingtKauf() {
        val kurse = listOf(100.0, 100.0, 80.0, 100.0, 100.0)
        val hoch = EinstiegVergleich.hochs(kurse)
        val l = EinstiegVergleich.ruecksetzer(kurse, hoch, 100.0, 0.1, 1, frei)!!
        // Kauf im zweiten Monat (aelteste Rate liegt einen Monat), dann der Ruecksetzer
        assertEquals(2, l.kaeufe)
        assertEquals(425.0, l.endWarten, 1e-9)
        assertEquals(1, l.wartenLaengste)
        // Ohne Wartezeit kauft der Wartende wie der sture Sparer
        val sofort = EinstiegVergleich.ruecksetzer(kurse, hoch, 100.0, 0.1, 0, frei)!!
        assertEquals(sofort.endStur, sofort.endWarten, 1e-9)
        assertEquals(0.0, sofort.wartenMittel, 0.0)
    }

    @Test
    fun steigenderKursOhneRuecksetzerBleibtBargeld() {
        val kurse = List(30) { 100.0 + it }
        val l = EinstiegVergleich.ruecksetzer(kurse, EinstiegVergleich.hochs(kurse), 50.0, 0.1, null, frei)!!
        assertEquals(0, l.kaeufe)
        assertEquals(l.eingezahlt, l.endWarten, 1e-9)
        assertEquals(29, l.wartenLaengste)
        assertTrue(l.vorsprung < 0)
        val r = EinstiegVergleich.ruecksetzerRollierend(kurse, 50.0, 0.1, null, 12, frei)!!
        assertEquals(18, r.zeitraeume)
        assertEquals(0, r.vorn)
        assertTrue(r.median < 0)
    }

    @Test
    fun hochZaehltAuchVorDemStart() {
        // Hoch 100 liegt vor dem Startmonat; ab Monat 2 steht der Kurs 20 % darunter
        val kurse = listOf(100.0, 80.0, 80.0, 80.0, 80.0)
        val l = EinstiegVergleich.ruecksetzer(kurse, EinstiegVergleich.hochs(kurse), 100.0, 0.15, null, frei, von = 1)!!
        assertEquals(3, l.kaeufe)
        assertEquals(l.endStur, l.endWarten, 1e-9)
    }

    @Test
    fun ungueltigeEingabenErgebenNull() {
        val kurse = listOf(1.0, 2.0, 3.0)
        val hoch = EinstiegVergleich.hochs(kurse)
        assertNull(EinstiegVergleich.ruecksetzer(kurse, hoch, 0.0, 0.1, null))
        assertNull(EinstiegVergleich.ruecksetzer(kurse, hoch, 10.0, 0.1, null, von = 2, bis = 2))
        assertNull(EinstiegVergleich.ruecksetzer(kurse, hoch.dropLast(1), 10.0, 0.1, null))
        assertNull(EinstiegVergleich.einmalGegenRaten(List(40) { 1.0 }, 0.0, 12))
    }

    @Test
    fun horizontNachLaengeDerReihe() {
        assertEquals(120, EinstiegVergleich.horizont(132))
        assertEquals(60, EinstiegVergleich.horizont(121))
        assertEquals(36, EinstiegVergleich.horizont(60))
        assertNull(EinstiegVergleich.horizont(40))
    }

    @Test
    fun medianGeradeUndUngerade() {
        assertEquals(2.0, EinstiegVergleich.median(listOf(3.0, 1.0, 2.0)), 0.0)
        assertEquals(2.5, EinstiegVergleich.median(listOf(4.0, 1.0, 2.0, 3.0)), 0.0)
    }

    @Test
    fun texteInBeidenSprachenOhneGedankenstrich() {
        assertEquals(EinstiegTexte.schluessel(true), EinstiegTexte.schluessel(false))
        for (deutsch in listOf(true, false)) EinstiegTexte.schluessel(deutsch).forEach { k ->
            val t = EinstiegTexte.text(deutsch, k)
            assertTrue(k, t.isNotBlank())
            assertFalse(k, t.contains('–') || t.contains('—') || t.contains('?'))
        }
        assertEquals("Einmalanlage lag in 7 von 10 Zeiträumen vorn", EinstiegTexte.text(true, "einmal.satz", listOf("7", "10")))
        assertTrue(EinstiegTexte.text(true, "hinweis").startsWith("Historisch und hypothetisch"))
        assertTrue(EinstiegTexte.text(true, "hinweis").contains("unverzinst"))
    }
}
