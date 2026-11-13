package de.troetenrepublik.app.ui

import de.troetenrepublik.app.i18n.HinweisTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Textdurchgang 0.78: Hinweise zeigen eine Kernzeile, die Methode steht hinter dem Info-Symbol.
class Fassung078U60HinweisTest {

    @Test
    fun kurzerTextStehtGanzDa() {
        assertNull(hinweisTeilen("Stand letzter Handelstag, offene Kontrakte vom Vortag.", null))
    }

    @Test
    fun ersteZeileWirdKernzeile() {
        val t = hinweisTeilen("Historisch und hypothetisch, nicht tatsächlich erzielt.\n\n• Eins\n• Zwei", null)!!
        assertEquals("Historisch und hypothetisch, nicht tatsächlich erzielt.", t.first)
        assertTrue(t.second.startsWith("• Eins"))
    }

    @Test
    fun ersterSatzWirdKernzeile() {
        val text = "Jeder Handelstag ist ein Startzeitpunkt. Kasten: mittlere Hälfte der Ergebnisse. Strich: Median. Linie: schlechtestes bis bestes Ergebnis. Die Zeiträume überlappen sich."
        val t = hinweisTeilen(text, null)!!
        assertEquals("Jeder Handelstag ist ein Startzeitpunkt.", t.first)
        assertFalse(t.second.startsWith("Jeder"))
    }

    @Test
    fun nurStichpunkteHabenKeineKernzeile() {
        val t = hinweisTeilen("• Eins\n• Zwei", null)!!
        assertNull(t.first)
    }

    @Test
    fun ausdruecklicheKernzeileBleibtUndDerVolleTextStehtImBlatt() {
        val text = "Rückrechnung auf Tagesschlusskursen. Historisch und hypothetisch. Steuern sind nicht eingerechnet."
        val t = hinweisTeilen(text, "Kern")!!
        assertEquals("Kern", t.first)
        assertEquals(text, t.second)
    }

    @Test
    fun hinweisTexteGleichInBeidenSprachen() {
        assertEquals(HinweisTexte.schluessel(true), HinweisTexte.schluessel(false))
        listOf(true, false).forEach { de ->
            HinweisTexte.schluessel(de).forEach { k ->
                val s = HinweisTexte.text(de, k)
                assertNotNull(k, s)
                assertTrue(k, s.isNotBlank())
                assertFalse(k, s.any { it in "–—" })
            }
        }
    }
}
