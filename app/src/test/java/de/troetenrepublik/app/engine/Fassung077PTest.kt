package de.troetenrepublik.app.engine

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class Fassung077PTest {
    private val tag = 86_400_000L
    // Mittwoch, 11.11.2026 12:00 UTC
    private val mi = 1_794_398_400_000L

    @Test fun zeileMitVorzeichen() {
        assertEquals("262,30 € · +1,2 % heute", Kursmeldung.zeile(262.30, 259.20, "€", true, "heute"))
        assertEquals("€262.30 · −1.2% today", Kursmeldung.zeile(262.30, 265.50, "€", false, "today"))
    }

    @Test fun rundung() {
        assertEquals("+0,1 %", Kursmeldung.vorzeichenProzent(0.05, true))
        assertEquals("0,0 %", Kursmeldung.vorzeichenProzent(0.04, true))
        assertEquals("0,0 %", Kursmeldung.vorzeichenProzent(-0.04, true))
        assertEquals("−0,1 %", Kursmeldung.vorzeichenProzent(-0.06, true))
        assertEquals("+1.234,6 %", Kursmeldung.vorzeichenProzent(1234.56, true))
    }

    @Test fun fehlenderVortagUndKursNull() {
        assertNull(Kursmeldung.zeile(100.0, 0.0, "€", true, "heute"))
        assertNull(Kursmeldung.zeile(0.0, 100.0, "€", true, "heute"))
        assertNull(Kursmeldung.zeile(Double.NaN, 100.0, "€", true, "heute"))
        assertNull(Kursmeldung.zeileProzent(100.0, null, "€", true, "heute"))
        assertNull(Kursmeldung.nurVeraenderung(null, true, "heute"))
    }

    @Test fun bezeichnungUndNurVeraenderung() {
        assertEquals("Alphabet 259,20 € · +0,6 % heute", Kursmeldung.zeileProzent(259.20, 0.6, "€", true, "heute", "Alphabet"))
        assertEquals("+0,6 % heute", Kursmeldung.nurVeraenderung(0.6, true, "heute"))
        assertEquals("0,5000 € · +2,0 % heute", Kursmeldung.zeile(0.5, 0.49, "€", true, "heute"))
    }

    @Test fun mehrereWerte() {
        assertEquals("NVDA +1,2 % · AMD −0,8 %", Kursmeldung.mehrere(listOf("NVDA" to 1.2, "AMD" to -0.8), true))
        assertEquals("NVDA +1.2%", Kursmeldung.mehrere(listOf("NVDA" to 1.2, "AMD" to null), false))
        assertEquals("A +1,0 % · B +2,0 % · C +3,0 %", Kursmeldung.mehrere(listOf("A" to 1.0, "B" to 2.0, "C" to 3.0, "D" to 4.0), true))
        assertNull(Kursmeldung.mehrere(listOf("A" to null), true))
    }

    @Test fun kursAlter() {
        assertTrue(Kursmeldung.aktuell(mi - 3_600_000L, mi))
        assertTrue(Kursmeldung.aktuell(mi - tag, mi))
        assertFalse(Kursmeldung.aktuell(mi - 2 * tag, mi))
        assertFalse(Kursmeldung.aktuell(0L, mi))
        assertFalse(Kursmeldung.aktuell(mi + tag, mi))
        // Freitag Abend, Montag Morgen: nur ein Werktag dazwischen
        val freitag = mi + 2 * tag
        val montag = freitag + 3 * tag - 6 * 3_600_000L
        assertTrue(Kursmeldung.aktuell(freitag, montag))
        assertFalse(Kursmeldung.aktuell(freitag, montag + tag))
    }
}
