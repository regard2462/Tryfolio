package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Suche (0.79): Kurznamen fuer „Beliebt“ und der ausschaltbare Suchverlauf.
class Fassung079SucheTest {
    @Test
    fun allgemeineEndwoerterFallenWeg() {
        assertEquals(listOf("Micron Technology", "Micron"), PopulaerKurzname.kandidaten(listOf("Micron Technology")))
        assertEquals(listOf("Super Micro Computer", "Super Micro"), PopulaerKurzname.kandidaten(listOf("Super Micro Computer")))
    }

    @Test
    fun andereNamenBleibenGanz() {
        assertEquals(listOf("Heidelberg Materials"), PopulaerKurzname.kandidaten(listOf("Heidelberg Materials")))
        assertEquals(listOf("Commerzbank"), PopulaerKurzname.kandidaten(listOf("Commerzbank")))
    }

    @Test
    fun uebergebeneFassungenStehenVorn() {
        assertEquals(listOf("Acme Corp", "Acme"), PopulaerKurzname.kandidaten(listOf("Acme Corp", "Acme")))
        assertEquals(listOf("Acme Holdings Group", "Acme Holdings", "Acme"), PopulaerKurzname.kandidaten(listOf("Acme Holdings Group")))
    }

    @Test
    fun waehltDieLaengsteFassungDiePasst() {
        val k = PopulaerKurzname.kandidaten(listOf("Micron Technology"))
        assertEquals("Micron Technology", PopulaerKurzname.waehlen(k) { it.length <= 20 })
        assertEquals("Micron", PopulaerKurzname.waehlen(k) { it.length <= 10 })
        assertNull(PopulaerKurzname.waehlen(k) { it.length <= 3 })
    }

    @Test
    fun rasterNurWennJederNamePasst() {
        val namen = listOf(PopulaerKurzname.kandidaten(listOf("Micron Technology")), PopulaerKurzname.kandidaten(listOf("Heidelberg Materials")))
        assertTrue(PopulaerKurzname.alsRaster(namen) { it.length <= 20 })
        assertFalse(PopulaerKurzname.alsRaster(namen) { it.length <= 10 })
        assertFalse(PopulaerKurzname.alsRaster(emptyList()) { true })
    }

    @Test
    fun verlaufAusMerktNichts() {
        val liste = Suchverlauf.merken(emptyList(), "SAP.DE", "SAP", 1_000L)
        assertEquals(2, Suchverlauf.merkenOderNichts(false, liste, "BMW.DE", "BMW", 2_000L).size)
        assertTrue(Suchverlauf.merkenOderNichts(true, liste, "BMW.DE", "BMW", 2_000L).isEmpty())
    }

    @Test
    fun ausschaltenLoeschtDenVerlauf() {
        val text = Suchverlauf.text(Suchverlauf.merken(emptyList(), "SAP.DE", "SAP", 1_000L))
        assertEquals("", Suchverlauf.nachSchalter(true, text))
        assertEquals(text, Suchverlauf.nachSchalter(false, text))
    }
}
