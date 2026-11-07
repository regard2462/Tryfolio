package de.troetenrepublik.app.engine

import androidx.compose.ui.unit.sp
import de.troetenrepublik.app.ui.stichpunkte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 5.93: Stichpunkte eingerueckt. In Systemmitteilungen steht der Punkt am Rand, in der App als
// haengender Einzug (restLine groesser als firstLine).
class Fassung593StichpunkteTest {

    @Test fun zeilenKennenKopfUndPunkte() {
        assertEquals(
            listOf(false to "Kopf", true to "eins", true to "zwei"),
            Stichpunkte.zeilen("Kopf\n• eins\n• zwei"),
        )
    }

    @Test fun mitteilungSetztPunktAnDenRand() {
        assertEquals("•\u00A0a\nB", Stichpunkte.fuerMitteilung("• a\nB"))
    }

    @Test fun anzeigeHaeltFolgezeilenUnterDemText() {
        val a = stichpunkte("• lang\nX", 18.sp)
        assertTrue(a.text.startsWith("•"))
        val einzug = a.paragraphStyles.first().item.textIndent!!
        assertTrue(einzug.restLine > einzug.firstLine)
    }
}
