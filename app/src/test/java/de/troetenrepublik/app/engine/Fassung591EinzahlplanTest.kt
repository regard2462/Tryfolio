package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

// Fassung 5.91: monatliche virtuelle Einzahlung (Monatswechsel, Nachholen verpasster Monate, Pause).
class Fassung591EinzahlplanTest {

    private fun ms(j: Int, m: Int, t: Int, h: Int = 12): Long = LocalDate.of(j, m, t).atTime(h, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun konto(betrag: Double, tag: Int, angelegt: Long) = Account(einzahlplan = Einzahlplaene.neu(betrag, tag, angelegt))

    private fun einzahlungen(a: Account) = a.transactions.filter { it.type == TxType.DEPOSIT && it.via == "PLAN" }

    @Test fun ersterTermin() {
        // Angelegt vor dem Tag: noch in diesem Monat
        var a = konto(200.0, 15, ms(2026, 10, 5))
        a = Einzahlplaene.ausfuehren(a, ms(2026, 10, 14))
        assertTrue(einzahlungen(a).isEmpty())
        a = Einzahlplaene.ausfuehren(a, ms(2026, 10, 15, 0))
        assertEquals(1, einzahlungen(a).size)
        assertEquals(200.0, a.cash, 1e-9)
        assertEquals(200.0, a.deposited, 1e-9)
        assertEquals("DEPOSIT", einzahlungen(a)[0].tag)
        // Am selben Tag und im selben Monat nicht noch einmal
        a = Einzahlplaene.ausfuehren(a, ms(2026, 10, 15, 18))
        a = Einzahlplaene.ausfuehren(a, ms(2026, 10, 31))
        assertEquals(1, einzahlungen(a).size)
        // Angelegt am Tag selbst oder danach: erst naechsten Monat
        var b = konto(50.0, 15, ms(2026, 10, 15))
        b = Einzahlplaene.ausfuehren(b, ms(2026, 10, 20))
        assertTrue(einzahlungen(b).isEmpty())
        assertEquals(ms(2026, 11, 15, 0), Einzahlplaene.naechste(b.einzahlplan!!, ms(2026, 10, 20)))
    }

    @Test fun monatswechsel() {
        var a = konto(100.0, 1, ms(2026, 11, 20))
        a = Einzahlplaene.ausfuehren(a, ms(2026, 11, 30))
        assertTrue(einzahlungen(a).isEmpty())
        // Jahreswechsel
        a = Einzahlplaene.ausfuehren(a, ms(2026, 12, 1, 0))
        a = Einzahlplaene.ausfuehren(a, ms(2027, 1, 1, 0))
        assertEquals(2, einzahlungen(a).size)
        assertEquals(2027 * 12 + 1, a.einzahlplan!!.letzterMonat)
        assertEquals(2, a.einzahlplan!!.ausfuehrungen)
        assertEquals(200.0, a.einzahlplan!!.summe, 1e-9)
    }

    @Test fun kurzeMonate() {
        // Tag 31: im Februar am 28.
        var a = konto(10.0, 31, ms(2027, 1, 31, 18))
        a = Einzahlplaene.ausfuehren(a, ms(2027, 2, 27))
        assertTrue(einzahlungen(a).isEmpty())
        a = Einzahlplaene.ausfuehren(a, ms(2027, 2, 28))
        assertEquals(1, einzahlungen(a).size)
    }

    @Test fun verpassteMonateEinmalNachholen() {
        var a = konto(100.0, 10, ms(2026, 6, 1))
        // App lange nicht offen: Juni, Juli, August und September je einmal
        a = Einzahlplaene.ausfuehren(a, ms(2026, 9, 12))
        assertEquals(4, einzahlungen(a).size)
        assertEquals(400.0, a.cash, 1e-9)
        // Weitere Takte buchen nichts doppelt
        repeat(5) { a = Einzahlplaene.ausfuehren(a, ms(2026, 9, 20)) }
        assertEquals(4, einzahlungen(a).size)
        // Vor dem Tag im Oktober noch nichts, danach genau einer
        a = Einzahlplaene.ausfuehren(a, ms(2026, 10, 9))
        assertEquals(4, einzahlungen(a).size)
        a = Einzahlplaene.ausfuehren(a, ms(2026, 10, 10))
        assertEquals(5, einzahlungen(a).size)
        // Sehr lange Pause: hoechstens MAX_NACHHOLEN Monate
        var b = konto(1.0, 1, ms(2020, 1, 15))
        b = Einzahlplaene.ausfuehren(b, ms(2026, 1, 15))
        assertEquals(Einzahlplaene.MAX_NACHHOLEN, einzahlungen(b).size)
    }

    @Test fun pause() {
        var a = konto(100.0, 5, ms(2026, 6, 1))
        a = Einzahlplaene.ausfuehren(a, ms(2026, 6, 5))
        assertEquals(1, einzahlungen(a).size)
        a = a.copy(einzahlplan = a.einzahlplan!!.copy(pausiert = true))
        a = Einzahlplaene.ausfuehren(a, ms(2026, 9, 6))
        assertEquals(1, einzahlungen(a).size)
        // Fortsetzen am 6. September: Pausenmonate entfallen, naechste Buchung im Oktober
        a = a.copy(einzahlplan = Einzahlplaene.fortsetzen(a.einzahlplan!!, ms(2026, 9, 6)))
        a = Einzahlplaene.ausfuehren(a, ms(2026, 9, 7))
        assertEquals(1, einzahlungen(a).size)
        a = Einzahlplaene.ausfuehren(a, ms(2026, 10, 5))
        assertEquals(2, einzahlungen(a).size)
        assertEquals(0L, Einzahlplaene.naechste(a.einzahlplan!!.copy(pausiert = true), ms(2026, 10, 5)))
        // Ohne Plan bleibt das Konto, wie es ist
        val leer = Account()
        assertNull(leer.einzahlplan)
        assertTrue(Einzahlplaene.ausfuehren(leer, ms(2026, 10, 5)) === leer)
    }
}
