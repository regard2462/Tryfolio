package de.troetenrepublik.app.engine

import de.troetenrepublik.app.ui.beschriftungsText
import org.junit.Assert.assertEquals
import org.junit.Test

// Tests zum Feinschliff (5.96): Beschriftungen halten Zahl und Einheit zusammen.
class Fassung596FeinschliffTest {

    private val nbsp = "\u00A0"

    // Zahl und folgendes Wort sowie „S&P 500“ werden mit geschuetztem Leerzeichen verbunden, sonst bleibt alles gleich
    @Test
    fun beschriftung() {
        assertEquals("Meldungen (12${nbsp}Monate)", beschriftungsText("Meldungen (12 Monate)"))
        assertEquals("Filings (12${nbsp}months)", beschriftungsText("Filings (12 months)"))
        assertEquals("besser als S&P${nbsp}500", beschriftungsText("besser als S&P 500"))
        assertEquals("in 3${nbsp}Monaten", beschriftungsText("in 3 Monaten"))
        assertEquals("Käufe (geschätzt)", beschriftungsText("Käufe (geschätzt)"))
        // Zahl vor Prozentzeichen oder Satzzeichen bleibt trennbar
        assertEquals("3 % mehr", beschriftungsText("3 % mehr"))
        assertEquals("", beschriftungsText(""))
    }
}
