package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.DepotTexte
import de.troetenrepublik.app.i18n.StrategieTexte
import de.troetenrepublik.app.ui.hinweisTeilen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// 0.79, Durchsicht von Performance, Depot-Werkzeugen, Plan und Politik: neue Textschluessel in beiden Sprachen, Kernzeile
// und Erklaerung der Verkaufsbilanz und der Phasenzaehler-Regel.
class Fassung079U72cTest {

    @Test
    fun neueDepotTexteInBeidenSprachen() {
        for (k in listOf("st.etfEuro", "wl.prozentStart")) {
            val de = DepotTexte.text(true, k, emptyList())
            val en = DepotTexte.text(false, k, emptyList())
            assertTrue(k, de.isNotBlank() && en.isNotBlank())
            assertNotEquals(k, de, en)
        }
    }

    @Test
    fun verkaufsbilanzHinweisTeiltInKernzeileUndErklaerung() {
        val text = StrategieTexte.text(true, "vk.hint", listOf("5", "8")) + "\n" + StrategieTexte.text(true, "vk.steuer", emptyList())
        val teile = hinweisTeilen(text, null)!!
        assertEquals("5 von 8 mit Gewinn", teile.first)
        assertEquals("Ergebnisse vor Steuern.", teile.second)
    }

    @Test
    fun kurzerHinweisMitKernzeileStehtHinterInfo() {
        val teile = hinweisTeilen("Wechselkurse, Dividenden und Käufe in der Krise sind nicht eingerechnet.", "Modellrechnung")!!
        assertEquals("Modellrechnung", teile.first)
        assertTrue(teile.second.startsWith("Wechselkurse"))
    }
}
