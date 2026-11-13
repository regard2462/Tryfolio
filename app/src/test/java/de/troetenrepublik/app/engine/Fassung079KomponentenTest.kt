package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Komponenten.Komponente
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Fassung079KomponentenTest {
    private fun k(s: String, a: Double, c: Double? = 1.0) = Komponente(s, s, a, c)

    @Test fun sortiertNachGewichtOhneGewichtloses() {
        val l = Komponenten.sortiert(listOf(k("A", 0.1), k("B", 0.3), k("C", 0.0), k("D", Double.NaN), k("E", -0.2)))
        assertEquals(listOf("B", "A"), l.map { it.symbol })
    }

    @Test fun indexOhneRestWennAllesGezeigtWird() {
        val l = listOf(k("A", 0.5), k("B", 0.3), k("C", 0.2))
        val kacheln = Komponenten.kacheln(l, etf = false, uebrigeName = "Übrige")
        assertEquals(listOf("A", "B", "C"), kacheln.map { it.name })
        assertTrue(kacheln.none { it.rest })
    }

    @Test fun indexSammeltAbgeschnittenesInEineRestkachel() {
        val l = listOf(k("A", 0.5), k("B", 0.3), k("C", 0.1), k("D", 0.1))
        val kacheln = Komponenten.kacheln(l, etf = false, uebrigeName = "Übrige", hoechstens = 2)
        assertEquals(3, kacheln.size)
        val rest = kacheln.last()
        assertTrue(rest.rest)
        assertEquals("Übrige", rest.name)
        assertEquals(0.2, rest.gewicht, 1e-9)
        assertNull(rest.symbol)
        assertNull(rest.aenderung)
    }

    @Test fun etfMitZehnPositionenBekommtRestkachelMitRestgewicht() {
        val l = (1..10).map { k("P$it", 0.04) }
        val kacheln = Komponenten.kacheln(l, etf = true, uebrigeName = "Other")
        assertEquals(11, kacheln.size)
        assertEquals(0.6, kacheln.last().gewicht, 1e-9)
        assertTrue(kacheln.last().rest)
    }

    @Test fun etfOhneBekanntenRestHatKeineRestkachel() {
        val voll = listOf(k("A", 0.6), k("B", 0.4))
        assertFalse(Komponenten.kacheln(voll, etf = true, uebrigeName = "x").any { it.rest })
        val ueber = listOf(k("A", 0.7), k("B", 0.5))
        assertFalse(Komponenten.kacheln(ueber, etf = true, uebrigeName = "x").any { it.rest })
    }

    @Test fun kleinerRestUnterSchwelleFaelltWeg() {
        val l = listOf(k("A", 0.6), k("B", 0.397), k("C", 0.003))
        assertFalse(Komponenten.kacheln(l, etf = false, uebrigeName = "x", hoechstens = 2).any { it.rest })
    }

    @Test fun aenderungOhneKursBleibtNull() {
        val kacheln = Komponenten.kacheln(listOf(k("A", 0.5, null), k("B", 0.5, 2.0)), etf = false, uebrigeName = "x")
        assertNull(kacheln[0].aenderung)
        assertEquals(2.0, kacheln[1].aenderung!!, 1e-9)
    }

    @Test fun leereListeGibtKeineKacheln() {
        assertTrue(Komponenten.kacheln(emptyList(), etf = true, uebrigeName = "x").isEmpty())
    }
}
