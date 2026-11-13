package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Finanzen (0.68): Quartalsbeschriftung und Tabellenaufbau
class Fassung068FinanzenTest {

    @Test
    fun periodeAusDatum() {
        assertEquals("Q3 2026", FinanzTabelle.periode("2026-09-30"))
        assertEquals("Q1 2025", FinanzTabelle.periode("2025-01-31"))
        assertEquals("Q2 2025", FinanzTabelle.periode("2025-06-30"))
        assertEquals("Q4 2025", FinanzTabelle.periode("2025-12-31"))
    }

    @Test
    fun periodeAusLaengeremText() {
        assertEquals("Q4 2024", FinanzTabelle.periode("bis 2024-12-31"))
    }

    @Test
    fun periodeAusQuartalsschreibweise() {
        assertEquals("Q3 2026", FinanzTabelle.periode("3Q2026"))
    }

    @Test
    fun periodeJahrUndSonstiges() {
        assertEquals("2025", FinanzTabelle.periode("2025"))
        assertEquals("Hallo", FinanzTabelle.periode("  Hallo  "))
    }

    @Test
    fun periodeMonatDreizehnBleibtUnveraendert() {
        assertEquals("2026-13-30", FinanzTabelle.periode("2026-13-30"))
    }

    @Test
    fun spaltenPassenGenau() {
        assertTrue(FinanzTabelle.spaltenPassen(listOf(100, 50, 50), 16, 232))
    }

    @Test
    fun spaltenPassenNicht() {
        assertFalse(FinanzTabelle.spaltenPassen(listOf(100, 50, 50), 16, 231))
    }

    @Test
    fun spaltenPassenLeereListe() {
        assertTrue(FinanzTabelle.spaltenPassen(emptyList(), 16, 0))
    }

    @Test
    fun rasterZweiSpalten() {
        assertEquals(2, FinanzTabelle.rasterSpalten(100, 16, 216))
    }

    @Test
    fun rasterEineSpalte() {
        assertEquals(1, FinanzTabelle.rasterSpalten(100, 16, 215))
    }

    @Test
    fun rasterZuSchmalErgibtEins() {
        assertEquals(1, FinanzTabelle.rasterSpalten(100, 16, 50))
    }

    @Test
    fun rasterMaxDrei() {
        assertEquals(3, FinanzTabelle.rasterSpalten(100, 16, 332, max = 3))
        assertEquals(2, FinanzTabelle.rasterSpalten(100, 16, 332))
    }
}
