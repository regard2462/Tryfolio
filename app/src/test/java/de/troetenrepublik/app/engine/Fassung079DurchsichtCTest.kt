package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.Durchsicht079CTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Durchsicht 0.79, Teil C: Texte in beiden Sprachen, ohne Platzhalterreste.
class Fassung079DurchsichtCTest {

    @Test
    fun texteInBeidenSprachen() {
        assertEquals(Durchsicht079CTexte.schluessel(true), Durchsicht079CTexte.schluessel(false))
        for (k in Durchsicht079CTexte.schluessel(true)) for (d in listOf(true, false)) {
            val t = Durchsicht079CTexte.text(d, k, listOf("1,2 %"))
            assertTrue(k, t.isNotBlank())
            assertFalse(k, t.contains('{'))
            assertFalse(k, t.contains('—') || t.contains(';'))
        }
        assertEquals("Insider 1,2 %", Durchsicht079CTexte.text(true, "hl.insider", listOf("1,2 %")))
        assertEquals("Other 1,2 %", Durchsicht079CTexte.text(false, "hl.rest", listOf("1,2 %")))
    }
}
