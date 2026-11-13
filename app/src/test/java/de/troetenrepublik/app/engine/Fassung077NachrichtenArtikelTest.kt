package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Erwaehnte Aktien am Artikelende (0.77).
class Fassung077NachrichtenArtikelTest {
    private val katalog = listOf(
        "AAPL" to "Apple Inc.",
        "MSFT" to "Microsoft Corporation",
        "NVDA" to "NVIDIA Corporation",
        "GOOGL" to "Alphabet Inc. Class A",
        "GOOG" to "Alphabet Inc. Class C",
        "TSLA" to "Tesla, Inc.",
    )

    @Test
    fun nameInTitelUndText() {
        val r = NachrichtEinordnung.erwaehnt(listOf("Microsoft steigt", "Nvidia liefert mehr Chips"), katalog)
        assertEquals(listOf("MSFT", "NVDA"), r.map { it.first })
    }

    @Test
    fun bekanntesKuerzelStehtVorn() {
        val r = NachrichtEinordnung.erwaehnt(listOf("Microsoft steigt"), katalog, bekannt = listOf("TSLA"))
        assertEquals(listOf("TSLA", "MSFT"), r.map { it.first })
    }

    @Test
    fun gleicheFirmaNurEinmalUndOhneTrefferLeer() {
        val r = NachrichtEinordnung.erwaehnt(listOf("Alphabet hebt Prognose, Alphabet kauft zu"), katalog)
        assertEquals(1, r.size)
        assertTrue(NachrichtEinordnung.erwaehnt(listOf("Wetter bleibt mild"), katalog).isEmpty())
    }

    @Test
    fun hoechstzahlGilt() {
        val r = NachrichtEinordnung.erwaehnt(listOf("Apple Microsoft Nvidia Tesla"), katalog, hoechstens = 2)
        assertEquals(2, r.size)
    }
}
