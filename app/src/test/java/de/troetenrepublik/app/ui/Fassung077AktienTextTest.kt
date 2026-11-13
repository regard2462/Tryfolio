package de.troetenrepublik.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

// Aktienzahlen im Bilanzblatt (0.77): Zwei Werte, die gerundet gleich aussaehen, stehen in Millionen mit einer Einheit.
class Fassung077AktienTextTest {
    @Test
    fun einheitStehtEinmal() {
        val jetzt = aktienText(1_801_511_485.0, 1_800_000_000.0)
        val vorher = aktienText(1_800_000_000.0, 1_801_511_485.0)
        for (t in listOf(jetzt, vorher)) {
            // Zahl, festes Leerzeichen, Einheit: keine zweite Zahl aus der Kurzform von einer Million
            assertEquals(2, t.split(' ', (0x00A0).toChar()).size)
            assertFalse(t, t.contains("1,00") || t.contains("1.00 "))
        }
        assertFalse(jetzt == vorher)
    }
}
