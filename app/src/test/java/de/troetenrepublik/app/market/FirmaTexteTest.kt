package de.troetenrepublik.app.market

import de.troetenrepublik.app.i18n.FirmaTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Die Texte der Firmenbausteine gibt es in beiden Sprachen mit denselben Platzhaltern.
class FirmaTexteTest {
    @Test fun gleicheSchluesselUndPlatzhalter() {
        assertEquals(FirmaTexte.schluessel(true), FirmaTexte.schluessel(false))
        val platzhalter = Regex("\\{\\d\\}")
        for (k in FirmaTexte.schluessel(true)) {
            val de = FirmaTexte.text(true, k)
            val en = FirmaTexte.text(false, k)
            assertTrue(k, de.isNotBlank() && en.isNotBlank())
            assertEquals(k, platzhalter.findAll(de).map { it.value }.toSet(), platzhalter.findAll(en).map { it.value }.toSet())
            assertFalse(k, de.contains("—") || en.contains("—"))
        }
    }
}
