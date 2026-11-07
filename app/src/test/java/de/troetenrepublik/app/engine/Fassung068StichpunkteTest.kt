package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Test

// Fassung 0.68: Stichpunkte in Systemmitteilungen. Der Punkt steht am Rand, die Folgezeilen bekommen auf Android
// einen haengenden Einzug ueber die Bereiche der Stichpunkt-Absaetze.
class Fassung068StichpunkteTest {

    @Test fun punktOhneLeerraumDavor() {
        assertEquals("Kopf\n• eins\n• zwei", Stichpunkte.fuerMitteilung("Kopf\n  • eins\n• zwei"))
    }

    @Test fun bereicheDerPunktAbsaetze() {
        val text = Stichpunkte.fuerMitteilung("Kopf\n• eins\n• zwei")
        assertEquals(listOf(5 until 11, 12 until 18), Stichpunkte.punktAbsaetze(text))
        assertEquals("• eins", text.substring(5, 11))
        assertEquals("• zwei", text.substring(12, 18))
    }

    @Test fun ohnePunkteKeineBereiche() {
        assertEquals(emptyList<IntRange>(), Stichpunkte.punktAbsaetze("eins\nzwei"))
    }
}
