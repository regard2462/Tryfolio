package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.WidgetDaten
import de.troetenrepublik.app.data.WidgetZiel
import de.troetenrepublik.app.ui.MAERKTE_ROUTE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Startseite und Navigation: der Schnellzugriff fuehrt zur Seite Maerkte, aeltere Widgets mit der Heatmap gelten weiter.
class Fassung591StartseiteTest {
    @Test fun schnellzugriff() {
        assertTrue(MAERKTE_ROUTE in WidgetDaten.SCHNELL_ZIELE)
        assertFalse("heatmap" in WidgetDaten.SCHNELL_ZIELE)
        assertEquals(4, WidgetDaten.SCHNELL_ZIELE.size)
        assertEquals(MAERKTE_ROUTE, WidgetZiel.pruefen(MAERKTE_ROUTE))
        // Ziel aelterer Widgets bis zum naechsten Auffrischen
        assertEquals("heatmap", WidgetZiel.pruefen("heatmap"))
        assertNull(WidgetZiel.pruefen("maerkte/x"))
        assertNull(WidgetZiel.pruefen("statistics"))
    }
}
