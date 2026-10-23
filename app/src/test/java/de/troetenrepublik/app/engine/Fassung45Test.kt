package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// ATR, OBV, Ichimoku und Parabolic SAR mit Deutung.
class Fassung45Test {

    @Test
    fun atrMisstDieTagesspanne() {
        // Jeden Tag 2 hoch und tief um den Schluss, keine Luecken: ATR 4
        val schluss = List(40) { 100.0 }
        val a = Indikatoren.atr(schluss.map { it + 2 }, schluss.map { it - 2 }, schluss)
        assertNull(a[12])
        assertEquals(4.0, a.last()!!, 1e-9)
        // Luecke zum Vortag zaehlt mit
        val mitLuecke = Indikatoren.atr(listOf(101.0, 111.0), listOf(99.0, 109.0), listOf(100.0, 110.0), n = 1)
        assertEquals(11.0, mitLuecke[1]!!, 1e-9)
    }

    @Test
    fun obvZaehltUmsatzNachRichtung() {
        val o = Indikatoren.obv(listOf(10.0, 11.0, 10.5, 10.5, 12.0), listOf(5.0, 100.0, 40.0, 70.0, 10.0))
        assertEquals(listOf(0.0, 100.0, 60.0, 60.0, 70.0), o)
    }

    @Test
    fun ichimokuUndSarImAufwaertstrend() {
        val schluss = (0 until 120).map { 100.0 + it }
        val hoch = schluss.map { it + 1 }
        val tief = schluss.map { it - 1 }
        val ich = Indikatoren.ichimoku(hoch, tief)
        assertNull(ich.spanB[70])
        assertNotNull(ich.spanB.last())
        // Mitte der letzten 9 Tage: (max Hoch + min Tief) / 2
        assertEquals((220.0 + 210.0) / 2, ich.tenkan.last()!!, 1e-9)
        val sar = Indikatoren.sar(hoch, tief)
        assertTrue(sar.aufwaerts.last())
        assertTrue(sar.wert.last()!! < tief.last())
        val d = Indikatoren.deuten(schluss.indices.map { 20_000L + it }, schluss, hoch, tief, List(120) { 1000.0 })
        assertEquals("ueber", d.first { it.art == "ichimoku" }.lage)
        assertEquals("aufwaerts", d.first { it.art == "sar" }.lage)
        assertEquals("bestaetigt_auf", d.first { it.art == "obv" }.lage)
        assertTrue(d.first { it.art == "atr" }.lage in setOf("normal", "ruhig"))
    }

    @Test
    fun sarWechseltBeiTrendbruch() {
        val schluss = (0 until 40).map { 100.0 + it } + (0 until 20).map { 139.0 - 3.0 * it }
        val sar = Indikatoren.sar(schluss.map { it + 1 }, schluss.map { it - 1 })
        assertFalse(sar.aufwaerts.last())
        assertTrue(sar.wert.last()!! > schluss.last())
        // ohne Umsaetze keine OBV-Deutung
        val d = Indikatoren.deuten(schluss.indices.map { 20_000L + it }, schluss, schluss.map { it + 1 }, schluss.map { it - 1 }, List(60) { 0.0 })
        assertTrue(d.none { it.art == "obv" })
        assertEquals("abwaerts", d.first { it.art == "sar" }.lage)
    }
}
