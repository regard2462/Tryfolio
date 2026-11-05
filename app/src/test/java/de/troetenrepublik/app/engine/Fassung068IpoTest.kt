package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.IpoKalender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Rechenteile der Boersengaenge (0.68): Spanne, Lage, Zeitleiste, Entwicklung seit Ausgabe, Verlauf ab Erstnotiz.
class Fassung068IpoTest {

    private val tagMs = 86_400_000L

    @Test
    fun spanneLiestSpanneUndEinzelpreis() {
        assertEquals(14.0 to 16.0, IpoBewertung.spanne("14.00-16.00"))
        assertEquals(14.0 to 16.0, IpoBewertung.spanne("14.00–16.00"))
        assertEquals(14.0 to 16.0, IpoBewertung.spanne("$14.00 - $16.00"))
        assertEquals(16.0 to 16.0, IpoBewertung.spanne("16.00"))
        assertEquals(14.0 to 16.0, IpoBewertung.spanne("16.00-14.00"))
        assertNull(IpoBewertung.spanne("TBD"))
        assertNull(IpoBewertung.spanne(null))
    }

    @Test
    fun lageAnteilInDerSpanne() {
        assertEquals(0.5, IpoBewertung.lage(15.0, 14.0, 16.0)!!, 1e-9)
        assertEquals(0.0, IpoBewertung.lage(10.0, 14.0, 16.0)!!, 1e-9)
        assertEquals(1.0, IpoBewertung.lage(20.0, 14.0, 16.0)!!, 1e-9)
        assertNull(IpoBewertung.lage(null, 14.0, 16.0))
        assertNull(IpoBewertung.lage(15.0, 16.0, 16.0))
    }

    @Test
    fun zeitleisteAlleStatus() {
        val heute = 20_000L
        val e = IpoBewertung.zeitleiste(IpoKalender.Status.EINGEREICHT, heute - 3, heute)
        assertEquals(IpoBewertung.Zeitleiste(0, 0, false), e)
        val a = IpoBewertung.zeitleiste(IpoKalender.Status.ANGEKUENDIGT, heute + 5, heute)
        assertEquals(IpoBewertung.Zeitleiste(0, 1, false), a)
        val p = IpoBewertung.zeitleiste(IpoKalender.Status.PREIS, heute - 1, heute)
        assertEquals(IpoBewertung.Zeitleiste(2, 2, false), p)
        val z = IpoBewertung.zeitleiste(IpoKalender.Status.ZURUECK, heute - 10, heute)
        assertEquals(IpoBewertung.Zeitleiste(1, 1, true), z)
        assertTrue(z.zurueck)
    }

    @Test
    fun zeitleisteOhneTagUndPreisInDerZukunft() {
        val heute = 20_000L
        assertEquals(-1, IpoBewertung.zeitleiste(IpoKalender.Status.EINGEREICHT, null, heute).datumBei)
        assertEquals(-1, IpoBewertung.zeitleiste(IpoKalender.Status.ANGEKUENDIGT, null, heute).datumBei)
        val zukunft = IpoBewertung.zeitleiste(IpoKalender.Status.PREIS, heute + 1, heute)
        assertEquals(IpoBewertung.Zeitleiste(1, 1, false), zukunft)
        val gleich = IpoBewertung.zeitleiste(IpoKalender.Status.PREIS, heute, heute)
        assertEquals(2, gleich.erreicht)
        assertFalse(gleich.zurueck)
    }

    @Test
    fun seitAusgabeProzent() {
        assertEquals(25.0, IpoBewertung.seitAusgabe(20.0, 25.0)!!, 1e-9)
        assertEquals(-50.0, IpoBewertung.seitAusgabe(20.0, 10.0)!!, 1e-9)
        assertNull(IpoBewertung.seitAusgabe(null, 10.0))
        assertNull(IpoBewertung.seitAusgabe(20.0, null))
        assertNull(IpoBewertung.seitAusgabe(0.0, 10.0))
        assertNull(IpoBewertung.seitAusgabe(20.0, 0.0))
        assertNull(IpoBewertung.seitAusgabe(20.0, Double.NaN))
    }

    @Test
    fun verlaufAbErstnotiz() {
        val punkte = listOf(
            (101L * tagMs + 5) to 12.0,
            (99L * tagMs) to 9.0,
            (100L * tagMs) to 10.0,
            (102L * tagMs) to Double.NaN,
            (103L * tagMs) to -1.0,
            (104L * tagMs) to 14.0,
        )
        assertEquals(listOf(10.0, 12.0, 14.0), IpoBewertung.verlaufAb(punkte, 100L))
        assertTrue(IpoBewertung.verlaufAb(punkte, null).isEmpty())
        val viele = (1..300).map { (it * tagMs) to it.toDouble() }
        val v = IpoBewertung.verlaufAb(viele, 1L)
        assertEquals(250, v.size)
        assertEquals(300.0, v.last(), 0.0)
    }
}
