package de.troetenrepublik.app.ui

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.Faktorzerlegung.Faktor
import de.troetenrepublik.app.engine.HebelSimulator
import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import de.troetenrepublik.app.i18n.HebelGrenzeTexte
import de.troetenrepublik.app.i18n.I18n
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.pow

// Zeitraeume der Portfolio Performance nach Alter des Depots, Faktoren in Alltagssprache, Hebel als Short und in den Krisen, Namen der Werkzeuge.
class Fassung078U65Test {

    private val zone = ZoneOffset.UTC
    private val jetzt = LocalDate.of(2026, 11, 13).atTime(12, 0).toInstant(zone).toEpochMilli()

    private fun ms(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun zeitraeumeNurSoWeitDasDepotReicht() {
        assertEquals(listOf("MAX"), perfVerfuegbar(null, jetzt, zone))
        // Depot seit zwei Monaten: nur Gesamt
        assertEquals(listOf("MAX"), perfVerfuegbar(ms(LocalDate.of(2026, 9, 21)), jetzt, zone))
        // Seit Februar dieses Jahres: kein „Seit 1.1.“, kein Jahr
        assertEquals(listOf("MAX"), perfVerfuegbar(ms(LocalDate.of(2026, 2, 1)), jetzt, zone))
        // Vor dem 1.1., aber unter einem Jahr
        assertEquals(listOf("YTD", "MAX"), perfVerfuegbar(ms(LocalDate.of(2025, 12, 1)), jetzt, zone))
        assertEquals(listOf("YTD", "Y1", "MAX"), perfVerfuegbar(ms(LocalDate.of(2024, 6, 1)), jetzt, zone))
        assertEquals(listOf("YTD", "Y1", "Y3", "MAX"), perfVerfuegbar(ms(LocalDate.of(2022, 6, 1)), jetzt, zone))
        assertEquals(listOf("YTD", "Y1", "Y3", "Y5", "MAX"), perfVerfuegbar(ms(LocalDate.of(2019, 1, 1)), jetzt, zone))
    }

    @Test
    fun zeitraumleisteInKurzform() {
        for (s in listOf(De, En)) {
            assertEquals(s.toolYearsKurz(1), s.ppfZeitraum("Y1"))
            assertEquals(s.toolYearsKurz(3), s.ppfZeitraum("Y3"))
            assertEquals(s.toolYearsKurz(5), s.ppfZeitraum("Y5"))
        }
        assertEquals("3J", De.ppfZeitraum("Y3"))
        assertEquals("Gesamt", De.ppfZeitraum("MAX"))
    }

    @Test
    fun faktorenInAlltagssprache() {
        val alt = I18n.lang
        try {
            I18n.lang = Lang.DE
            assertEquals("schwankt wie der Markt", fkWort(Faktor.MARKT, 1.02))
            assertEquals("schwankt stärker als der Markt", fkWort(Faktor.MARKT, 1.4))
            assertEquals("schwankt weniger als der Markt", fkWort(Faktor.MARKT, 0.5))
            assertEquals("eher große Firmen", fkWort(Faktor.GROESSE, -0.46))
            assertEquals("eher kleine Firmen", fkWort(Faktor.GROESSE, 0.3))
            assertEquals("neutral", fkWort(Faktor.WERT, 0.02))
            assertEquals("eher profitable Firmen", fkWort(Faktor.QUALITAET, 0.4))
            assertEquals("eher Verlierer der letzten 12 Monate", fkWort(Faktor.MOMENTUM, -0.3))
            I18n.lang = Lang.EN
            assertEquals("fluctuates like the market", fkWort(Faktor.MARKT, 1.0))
            assertEquals("neutral", fkWort(Faktor.GROESSE, 0.0))
        } finally {
            I18n.lang = alt
        }
    }

    @Test
    fun hebelTexteShortUndKrisen() {
        assertEquals("−3× Short", HebelGrenzeTexte.text(true, "h.malShort", listOf("−3")))
        assertEquals("−3× short", HebelGrenzeTexte.text(false, "h.malShort", listOf("−3")))
        assertEquals("Totalverlust bei einem Tagesverlust von", HebelGrenzeTexte.text(true, "h.schwelle.long"))
        assertEquals("größter im Zeitraum: 2,3 %", HebelGrenzeTexte.text(true, "h.schwelle.hint.long", listOf("2,3 %")))
        assertEquals("2000 bis 2002", HebelGrenzeTexte.text(true, "h.krisen.zeit", listOf("2000", "2002")))
        assertTrue(HebelGrenzeTexte.text(true, "h.krisen.note", listOf("1.000 €")).contains("Historisch und hypothetisch"))
        assertTrue(HebelGrenzeTexte.text(false, "h.krisen.note", listOf("€1,000")).contains("Historical and hypothetical"))
    }

    // Wochentage von bis, Kurs aus der Funktion des Tages; Kerzen mit dem Stand der Eroeffnung (13:30 UTC wie New York)
    private fun serie(von: LocalDate, bis: LocalDate, kurs: (LocalDate) -> Double): List<Candle> =
        generateSequence(von) { it.plusDays(1) }.takeWhile { !it.isAfter(bis) }.filter { it.dayOfWeek.value < 6 }.map {
            val c = kurs(it)
            Candle(it.atTime(13, 30).toInstant(zone).toEpochMilli(), c, c, c, c)
        }.toList()

    private val start = LocalDate.of(1999, 1, 1)
    // Index: gleichmaessiger Anstieg, am 12.3.2020 ein Tag mit minus 30 Prozent
    private fun indexKurs(d: LocalDate): Double =
        100.0 * 1.0003.pow((d.toEpochDay() - start.toEpochDay()).toDouble()) * (if (d >= LocalDate.of(2020, 3, 12) && d < LocalDate.of(2020, 3, 24)) 0.7 else 1.0)

    @Test
    fun krisenRechnenMitDemIndexWoDerWertFehlt() {
        val index = serie(start, LocalDate.of(2026, 11, 13), ::indexKurs)
        val wert = serie(LocalDate.of(2019, 1, 1), LocalDate.of(2026, 11, 13)) { d -> 50.0 + (d.toEpochDay() - LocalDate.of(2019, 1, 1).toEpochDay()) * 0.01 }
        val a = HebelSimulator.Annahmen(aufschlag = 0.0, kosten = 0.0, zinsFest = 0.0)
        val k = HebelSimulator.krisen(wert, index, 5.0, a).associateBy { it.schluessel }
        assertEquals(HebelSimulator.KRISEN.toSet(), k.keys)
        // Dotcom und Finanzkrise liegen vor dem Wert: Kurse des Index
        assertTrue(k.getValue("DOTCOM").aus)
        assertTrue(k.getValue("FINANCIAL").aus)
        // Corona und Zinswende rechnen mit dem Wert
        assertFalse(k.getValue("COVID").aus)
        assertFalse(k.getValue("RATES").aus)
        // Gleichmaessiger Anstieg ohne Kosten: der Hebel gewinnt mehr als der Kurs
        assertTrue(k.getValue("DOTCOM").ergebnis.endMit > k.getValue("DOTCOM").ergebnis.endOhne)
        assertNull(k.getValue("DOTCOM").ergebnis.totalverlustAm)
    }

    @Test
    fun krisenMitTotalverlust() {
        val index = serie(start, LocalDate.of(2026, 11, 13), ::indexKurs)
        val a = HebelSimulator.Annahmen(aufschlag = 0.0, kosten = 0.0, zinsFest = 0.0)
        val covid = HebelSimulator.krisen(emptyList(), index, 5.0, a).first { it.schluessel == "COVID" }
        // Minus 30 Prozent an einem Tag, mal fuenf: weg
        assertNotNull(covid.ergebnis.totalverlustAm)
        assertEquals(0.0, covid.ergebnis.endMit, 0.0)
        assertTrue(covid.ergebnis.endOhne < 0.8)
        // Short gewinnt im selben Fenster
        val kurz = HebelSimulator.krisen(emptyList(), index, -1.0, a).first { it.schluessel == "COVID" }
        assertTrue(kurz.ergebnis.endMit > 1.0)
    }

    @Test
    fun krisenReiheNurWoDieReiheDasFensterDeckt() {
        val kurz = serie(LocalDate.of(2021, 1, 1), LocalDate.of(2026, 11, 13), ::indexKurs)
        val crash = de.troetenrepublik.app.engine.PortfolioTools.CRASHES.first { it.key == "DOTCOM" }
        assertNull(HebelSimulator.krisenReihe(kurz, crash.from, crash.to))
        val lang = serie(start, LocalDate.of(2026, 11, 13), ::indexKurs)
        val r = HebelSimulator.krisenReihe(lang, crash.from, crash.to)
        assertNotNull(r)
        // Erster Punkt ist der Hochtag, letzter der Tieftag
        assertEquals(LocalDate.of(2000, 3, 24), java.time.Instant.ofEpochMilli(r!!.first().first).atZone(zone).toLocalDate())
        assertEquals(LocalDate.of(2002, 10, 9), java.time.Instant.ofEpochMilli(r.last().first).atZone(zone).toLocalDate())
    }

    @Test
    fun werkzeugNamenUndAbgrenzung() {
        val name = PortfolioTool.LANGSICHT.name
        assertEquals("Börsengeschichte", De.ptTitle(name))
        assertEquals("Market history", En.ptTitle(name))
        // Der Entnahmeplan nennt die Boersengeschichte nicht mehr, sonst waere der Name doppelt belegt
        for (s in listOf(De, En)) {
            assertFalse(s.ptSubtitle("WITHDRAWAL").contains("Börsengeschichte"))
            assertFalse(s.ptSubtitle("WITHDRAWAL").contains("market history", ignoreCase = true))
        }
        // Jede Unterzeile ist eine Aussage ohne Frage und ohne Gedankenstrich
        for (k in PortfolioTool.entries) for (s in listOf(De, En)) {
            val t = s.ptSubtitle(k.name)
            assertFalse("$k", t.contains('?') || t.contains('–') || t.contains('—'))
        }
        assertTrue(De.ptSubtitle("BACKTEST").contains("Tageskurse"))
        assertTrue(De.ptSubtitle("LANGSICHT").contains("Jahreswerte"))
        assertTrue(De.dvSubtitle.contains("Ein- und Auszahlungen"))
    }
}
