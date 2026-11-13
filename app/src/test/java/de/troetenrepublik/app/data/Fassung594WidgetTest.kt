package de.troetenrepublik.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Widgets (5.94): frei belegbarer Schnellzugriff und Ziel einer wichtigen Schlagzeile.
class Fassung594WidgetTest {

    @Test
    fun schnellzieleWerdenAngenommen() {
        WidgetDaten.SCHNELL_AUSWAHL.forEach { assertEquals(it, WidgetZiel.pruefen(it)) }
        assertNull(WidgetZiel.pruefen("einstellungen"))
    }

    @Test
    fun nachrichtenzielIstKurz() {
        val z = WidgetZiel.nachricht("https://example.org/artikel/sehr-lange-adresse-mit-vielen-teilen?x=1")
        assertEquals(z, WidgetZiel.pruefen(z))
    }
}
