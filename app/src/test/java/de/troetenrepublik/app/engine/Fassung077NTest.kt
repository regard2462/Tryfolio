package de.troetenrepublik.app.engine

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class Fassung077NTest {
    @Test fun aufklappenErstAbVierZeilen() {
        assertFalse(ListenKern.lohnt(3))
        assertTrue(ListenKern.lohnt(4))
    }

    @Test fun nettoUndRichtung() {
        assertEquals(40.0, ListenKern.netto(100.0, 60.0))
        assertNull(ListenKern.netto(null, 60.0))
        assertEquals(1, ListenKern.richtung(5.0))
        assertEquals(-1, ListenKern.richtung(-5.0))
        assertEquals(0, ListenKern.richtung(0.001))
        assertEquals(0, ListenKern.richtung(null))
    }

    @Test fun groessterAnteil() {
        assertEquals(12.5, ListenKern.groesster(listOf(3.0, 12.5, 7.0)))
        assertNull(ListenKern.groesster(emptyList()))
    }

    @Test fun prozentOhneVergleichswert() {
        assertEquals(40.0, ListenKern.prozent(140.0, 100.0)!!, 1e-9)
        assertNull(ListenKern.prozent(10.0, 0.0))
        assertNull(ListenKern.prozent(10.0, null))
    }

    @Test fun trendKernNimmtGewichtigsteQuelle() {
        val k = ListenKern.trend(
            listOf(
                ListenKern.TrendQuelle(0, 300.0, 5.0),
                ListenKern.TrendQuelle(0, 40.0, 900.0),
                ListenKern.TrendQuelle(1, null, 5000.0),
            ),
        )!!
        assertEquals(40.0, k.prozent!!, 1e-9)
        assertEquals(3, k.quellen)
    }

    @Test fun trendKernOhneVeraenderung() {
        val k = ListenKern.trend(listOf(ListenKern.TrendQuelle(0, null, 1.0)))!!
        assertNull(k.prozent)
        assertEquals(1, k.quellen)
        assertNull(ListenKern.trend(emptyList()))
    }
}
