package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.Regel079R2Texte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Regelpruefung 0.79, Teil 2: Texte in beiden Sprachen, ohne Platzhalterreste und ohne Strich als Platzhalter.
class Fassung079R2Test {

    @Test
    fun texteInBeidenSprachen() {
        assertEquals(Regel079R2Texte.schluessel(true), Regel079R2Texte.schluessel(false))
        for (k in Regel079R2Texte.schluessel(true)) for (d in listOf(true, false)) {
            val t = Regel079R2Texte.text(d, k, listOf("1", "2"))
            assertTrue(k, t.isNotBlank())
            assertFalse(k, t.contains('{'))
            assertFalse(k, t.contains('—') || t.contains(';'))
        }
        assertEquals("Neu", Regel079R2Texte.text(true, "g13.neu"))
        assertEquals("to", Regel079R2Texte.text(false, "bis"))
    }
}
