package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

// Fassung 5.92: allgemeine Trefferbilanz der Indikatoren ueber viele Werte.
class Fassung592IndikatorAllgemeinTest {
    private fun b(signale: Int, richtig: Int, vergleich: Double?) =
        IndikatorTreffer.Bilanz("rsi", signale, richtig, if (signale > 0) richtig.toDouble() / signale else null, vergleich, IndikatorTreffer.Stufe.GERING, 0L)

    @Test fun zusammenfassenGewichtetNachSignalen() {
        val e = IndikatorAllgemein.zusammenfassen(listOf(b(100, 60, 0.5), b(300, 150, 0.6)))!!
        assertEquals(400, e.signale)
        assertEquals(210, e.richtig)
        assertEquals(0.525, e.treffer, 1e-9)
        // (0,5 * 100 + 0,6 * 300) / 400
        assertEquals(0.575, e.vergleich, 1e-9)
    }

    @Test fun ohneSignaleNichts() {
        assertNull(IndikatorAllgemein.zusammenfassen(listOf(b(0, 0, null))))
        assertNull(IndikatorAllgemein.zusammenfassen(emptyList()))
    }

    @Test fun dateiLesenUndSchreiben() {
        val d = IndikatorAllgemein.Datei("2026-10-28", 159, 1950, 20, mapOf("rsi" to IndikatorAllgemein.Eintrag(10, 6, 0.6, 0.5)))
        val zurueck = IndikatorAllgemein.lesen(IndikatorAllgemein.schreiben(d))
        assertNotNull(zurueck)
        assertEquals(d, zurueck)
    }
}
