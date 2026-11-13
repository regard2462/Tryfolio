package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.UniversumTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

// Fassung 0.78 U40: Mitglieder des Nasdaq-100 seit 2007 (jmccarrell/n100tickers, MIT) als zweites Universum des
// Finder-Rueckblicks. Die Datei hat denselben Aufbau wie die Liste des S&P 500.
class Fassung078U40Ndx100Test {

    private fun t(d: String) = LocalDate.parse(d).toEpochDay()

    private val datei = listOf("src/main/assets/indizes/ndx100_mitglieder.json", "../app/src/main/assets/indizes/ndx100_mitglieder.json")
        .map { File(it) }.first { it.exists() }

    @Test
    fun mitgelieferteListe() {
        val text = datei.readText(Charsets.UTF_8)
        // MIT: Urheberzeile und Erlaubnistext liegen der Datei bei
        assertTrue(text.contains("Copyright (c) 2021 - 2025 Jeff McCarrell"))
        assertTrue(text.contains("The above copyright notice and this permission notice shall be included"))
        assertTrue(text.contains("https://github.com/jmccarrell/n100tickers"))
        assertTrue("Datei zu gross: ${datei.length()}", datei.length() < 100_000)
        val m = FinderRueckblick.mitgliederLesen(text)!!
        assertEquals(t("2007-01-01"), m.beginn)
        assertTrue(m.stand >= "2026-10-09")
        // Zu jedem Monatsende zwischen 99 und 110 Mitglieder (zweite Aktiengattungen zaehlen einzeln)
        var tag = LocalDate.of(2007, 1, 31)
        while (tag.toString() <= m.stand) {
            val n = m.zum(tag.toEpochDay()).size
            assertTrue("$tag: $n", n in 99..110)
            tag = tag.plusMonths(1).withDayOfMonth(1).plusMonths(1).minusDays(1)
        }
        // Je Kuerzel ueberschneiden sich die Abschnitte nicht
        m.abschnitte.groupBy { it.kuerzel }.values.forEach { l ->
            l.sortedBy { it.ab }.zipWithNext().forEach { (a, b) -> assertTrue("${a.kuerzel}", a.bis != null && a.bis!! <= b.ab) }
        }
    }

    @Test
    fun mitgliederVonDamals() {
        val m = FinderRueckblick.mitgliederLesen(datei.readText(Charsets.UTF_8))!!
        fun am(d: String) = m.zum(t(d)).map { it.kuerzel }.toSet()
        // Aenderung zum 1. Februar 2007 laut Liste: CMVT scheidet aus, LOGI kommt hinzu
        assertTrue("CMVT" in am("2007-01-31") && "LOGI" !in am("2007-01-31"))
        assertTrue("CMVT" !in am("2007-02-01") && "LOGI" in am("2007-02-01"))
        // Heute ausgeschiedene Werte zaehlen an ihrem Tag mit
        assertTrue("YHOO" in am("2015-06-30"))
        assertFalse("YHOO" in am("2026-10-30"))
        // Ohne Aufnahmetag vor 2007: jedes Kuerzel behaelt sein eigenes
        assertTrue(m.seit.isEmpty())
        val y = FinderRueckblick.yahooJeAbschnitt(m)
        assertEquals(m.abschnitte.size, y.size)
        val (soll, messbar) = FinderRueckblick.zumStichtag(m, y, { true }, t("2020-12-31"))
        assertTrue(soll in 99..110)
        assertTrue("AAPL" in messbar)
    }

    @Test
    fun texteInBeidenSprachen() {
        assertEquals(UniversumTexte.schluessel(true), UniversumTexte.schluessel(false))
        for (k in UniversumTexte.schluessel(true)) for (d in listOf(true, false)) {
            val text = UniversumTexte.text(d, k, listOf("1", "2", "3"))
            assertTrue(k, text.isNotBlank())
            assertFalse(k, text.contains("{"))
            listOf("—", "–", "…").forEach { assertFalse("$k: $it", text.contains(it)) }
        }
    }
}
