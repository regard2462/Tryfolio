package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import de.troetenrepublik.app.i18n.LangeSichtTexte
import de.troetenrepublik.app.i18n.TaktikTexte
import de.troetenrepublik.app.ui.istHinweisblock
import de.troetenrepublik.app.ui.ersteZeile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.pow

// Fassung 0.78 (u50): Vergleichstabelle der anderen Rueckblicke rechnet im selben Zeitraum wie die Kernzahl,
// Dativ nach "nach", kurze Notizen und Hinweisblock mit erster Zeile.
class Fassung078U50Test {

    private val ms = 86_400_000L
    private val ohneKosten = StrategieLabor.Kosten(0.0, 0.0)

    private fun werktage(n: Int): List<LocalDate> {
        val aus = ArrayList<LocalDate>()
        var d = LocalDate.of(2018, 1, 1)
        while (aus.size < n) {
            if (d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY) aus += d
            d = d.plusDays(1)
        }
        return aus
    }

    private fun wachstum(n: Int, jeTag: Double): List<Candle> {
        val tage = werktage(n)
        val schluss = (0 until n).map { 100.0 * (1.0 + jeTag).pow(it) }
        return schluss.mapIndexed { i, c ->
            val o = if (i == 0) c else schluss[i - 1]
            Candle(tage[i].toEpochDay() * ms, o, maxOf(o, c), minOf(o, c), c)
        }
    }

    private fun steigend(n: Int = 1200) = Rotation.daten(listOf(wachstum(n, 0.001), wachstum(n, 0.0006), wachstum(n, 0.0003)), null)

    @Test fun zeileDesGewaehltenRueckblicksZeigtDieRenditeDerHauptrechnung() {
        val d = steigend()
        for (m in Rotation.MONATE) {
            val w = Rotation.Wahl(monate = m, halten = 1)
            val haupt = Rotation.hauptbericht(d, 0, ohneKosten, w)
            assertNotNull(haupt)
            val zeile = Rotation.rueckblicke(d, 0, ohneKosten, w).first { it.first == m }.second
            assertEquals(haupt!!.von, zeile.von)
            assertEquals(haupt.gesamt.renditeJahr!!, zeile.gesamt.renditeJahr!!, 0.0)
            assertEquals(haupt.pruef.renditeJahr!!, zeile.pruef.renditeJahr!!, 0.0)
            assertEquals(haupt.haltenGesamt.renditeJahr!!, zeile.haltenGesamt.renditeJahr!!, 0.0)
        }
    }

    @Test fun kaufenUndHaltenIstBeiSteigendemKursNichtNull() {
        val d = steigend()
        val w = Rotation.Wahl(monate = 6, halten = 1)
        val zeilen = Rotation.rueckblicke(d, 0, ohneKosten, w)
        assertTrue(zeilen.isNotEmpty())
        for ((_, b) in zeilen) {
            assertTrue(b.haltenGesamt.renditeJahr!! > 0.05)
            assertTrue(b.haltenPruef.renditeJahr!! > 0.05)
            assertTrue(b.haltenGesamt.endwert > StrategieLabor.START)
        }
        // Alle Zeilen teilen sich Zeitraum und Kaufen und Halten
        assertEquals(1, zeilen.map { it.second.von }.distinct().size)
        assertEquals(1, zeilen.map { it.second.haltenGesamt.endwert }.distinct().size)
    }

    @Test fun hauptrechnungStartetAmGemeinsamenTag() {
        val d = steigend()
        val w = Rotation.Wahl(monate = 3, halten = 1)
        assertEquals(Rotation.ersterTag(d, w.copy(monate = 12)), Rotation.gemeinsamerStart(d, w))
        assertEquals(Rotation.gemeinsamerStart(d, w), Rotation.hauptbericht(d, 0, ohneKosten, w)!!.von)
    }

    @Test fun jahreStehenNachNachImDativ() {
        assertEquals("10 Jahren", De.toolYearsDativ(10))
        assertEquals("1 Jahr", De.toolYearsDativ(1))
        assertEquals("10 years", En.toolYearsDativ(10))
        assertEquals("1 year", En.toolYearsDativ(1))
        assertEquals("Realer Verlust nach 10 Jahren", LangeSichtTexte.text(true, "t.verlust", listOf(De.toolYearsDativ(10))))
        assertEquals("Realer Verlust nach 1 Jahr", LangeSichtTexte.text(true, "t.verlust", listOf(De.toolYearsDativ(1))))
        assertEquals("Real loss after 10 years", LangeSichtTexte.text(false, "t.verlust", listOf(En.toolYearsDativ(10))))
        assertTrue(LangeSichtTexte.text(true, "rueckgang.erholt", listOf("1990", "1992", "1995", De.toolYearsDativ(5))).endsWith("nach 5 Jahren"))
    }

    @Test fun notizWiederholtDieUnterzeileNicht() {
        for (deutsch in listOf(true, false)) {
            val kurz = TaktikTexte.text(deutsch, "vt.andere.kurz", emptyList())
            val info = TaktikTexte.text(deutsch, "rot.andere.info", emptyList())
            assertTrue(info.isNotBlank())
            assertFalse(info.startsWith(kurz))
            assertFalse(info.contains(kurz))
            assertFalse(info.contains('—') || info.contains('–') || info.contains('?'))
            assertTrue(TaktikTexte.text(deutsch, "vt.gesamt", emptyList()).startsWith("p. a."))
            assertTrue(TaktikTexte.text(deutsch, "vt.pruefzeit", emptyList()).startsWith("p. a."))
        }
        assertFalse(De.slAndereText.startsWith("Dieselbe Regel"))
        assertFalse(En.slWfText.startsWith("Instead of one fixed split, the window moves"))
        assertFalse(De.slWfText.contains("wandert das Fenster durch die Geschichte"))
    }

    @Test fun hinweisblockZeigtZugeklapptNurDieErsteZeile() {
        val text = "Historisch und hypothetisch, nicht tatsächlich erzielt.\n" + "• Am letzten Handelstag jedes Monats zählt die Rendite im Rückblick.\n".repeat(5)
        assertTrue(istHinweisblock(text))
        assertEquals("Historisch und hypothetisch, nicht tatsächlich erzielt.", ersteZeile(text).first)
        assertFalse(istHinweisblock("Ein Satz. Noch ein Satz, ganz ohne Stichpunkte."))
        assertFalse(istHinweisblock("• Punkt eins\n• Punkt zwei"))
        assertFalse(istHinweisblock("Kopf\nFließtext ohne Punkt"))
        // Kurze Hinweisblöcke bleiben ganz (Regel: kurzer Text wird nicht eingeklappt)
        assertEquals("", ersteZeile("Kopf\n• Punkt\n").second)
        for (deutsch in listOf(true, false)) {
            val m = TaktikTexte.text(deutsch, "rot.methode", listOf("0,10 %"))
            assertTrue(istHinweisblock(m))
        }
    }

    @Test fun kopfBreiteBleibtImRahmen() {
        assertEquals(12f, Vergleich.PUNKT_DP)
        assertTrue(Vergleich.spaltenBreite(10f) >= Vergleich.MIN_SPALTE_DP)
    }
}
