package de.troetenrepublik.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Ziele aus Widgets: nur bekannte Seiten, Kurslinie ausgeduennt.
class WidgetZielTest {

    @Test
    fun nurBekannteZiele() {
        WidgetDaten.SCHNELL_ZIELE.forEach { assertEquals(it, WidgetZiel.pruefen(it)) }
        assertEquals("instrument/SAP.DE", WidgetZiel.pruefen(WidgetZiel.instrument("SAP.DE")))
        assertEquals("instrument/^GDAXI", WidgetZiel.pruefen("instrument/^GDAXI"))
        assertEquals("instrument/BTC-EUR", WidgetZiel.pruefen("instrument/BTC-EUR"))
        assertEquals("derivative/ko-nvda-1", WidgetZiel.pruefen(WidgetZiel.derivat("ko-nvda-1")))
        // alles andere nicht: andere Seiten, Pfade, leere oder zu lange Symbole
        assertNull(WidgetZiel.pruefen(null))
        assertNull(WidgetZiel.pruefen(""))
        assertNull(WidgetZiel.pruefen("broker"))
        assertNull(WidgetZiel.pruefen("sperre"))
        assertNull(WidgetZiel.pruefen("instrument/"))
        assertNull(WidgetZiel.pruefen("instrument/../broker"))
        assertNull(WidgetZiel.pruefen("instrument/SAP/x"))
        assertNull(WidgetZiel.pruefen("instrument/" + "A".repeat(41)))
        assertNull(WidgetZiel.pruefen("https://example.com"))
    }

    @Test
    fun kurveAusduennen() {
        val werte = (0 until 400).map { it.toDouble() }
        val aus = WidgetZiel.ausduennen(werte, 80)
        assertEquals(80, aus.size)
        assertEquals(0.0, aus.first(), 0.0)
        assertEquals(399.0, aus.last(), 0.0)
        assertEquals(aus.sorted(), aus)
        // kurze Reihen bleiben, ungueltige Werte fallen weg
        assertEquals(listOf(1.0, 2.0), WidgetZiel.ausduennen(listOf(1.0, Double.NaN, 2.0)))
    }
}
