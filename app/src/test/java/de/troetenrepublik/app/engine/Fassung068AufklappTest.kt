package de.troetenrepublik.app.engine

import de.troetenrepublik.app.ui.ersteZeile
import de.troetenrepublik.app.ui.ersterSatz
import de.troetenrepublik.app.ui.zugeklappt
import org.junit.Assert.*
import org.junit.Test

// Tests fuer die Blende an aufklappbaren Texten (0.68).
class Fassung068AufklappTest {

    // Der sichtbare Anfang ist ein echter, kuerzerer Anfang des Textes ohne Auslassungszeichen.
    @Test
    fun anfangIstEchterAnfang() {
        val text = "Dieser erste Satz ist deutlich laenger als vierzig Zeichen und steht allein am Anfang. " +
            "Weiterer Satz mit Inhalt. ".repeat(10)
        val anfang = zugeklappt(ersterSatz(text).first)
        assertTrue(text.startsWith(anfang))
        assertFalse(anfang.contains("…"))
        assertTrue(anfang.length < text.length)
    }

    // Bei einer Liste bleibt der Kopf ohne Zeilenumbruch.
    @Test
    fun listenkopfOhneUmbruch() {
        val kopf = zugeklappt(ersteZeile("Kopf\n" + "• Punkt mit etwas Text\n".repeat(10)).first)
        assertFalse(kopf.contains("\n"))
    }

    // Leerraum am Ende entfaellt.
    @Test
    fun leerraumAmEndeEntfaellt() {
        assertEquals("Text", zugeklappt("Text \n "))
    }
}
