package de.troetenrepublik.app.ui

import de.troetenrepublik.app.engine.Netz
import de.troetenrepublik.app.engine.NetzTestHilfe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Kleines Netzbild: viele Toechter verdraengen die wenigen Zulieferer nicht.
class Fassung079MiniNetzTest {

    @Test fun zuliefererBleibenNebenVielenToechtern() {
        val h = NetzTestHilfe
        val toechter = (1..20).map { h.kante("A", Netz.Art.TOCHTER, "T$it") }
        val zul = (1..4).map { h.kante("Z$it", Netz.Art.LIEFERT, "A") }
        val g = h.netz(*(toechter + zul).toTypedArray())
        val sichtbar = toechter.map { it.nach } + zul.map { it.von }
        val ids = miniAuswahl(g, "A", sichtbar, NetzTestHilfe.HEUTE)
        assertEquals("A", ids.first())
        assertEquals(9, ids.size)
        assertEquals(4, ids.count { it.startsWith("Z") })
        assertEquals(4, ids.count { it.startsWith("T") })
    }

    @Test fun nurEineGruppeFuelltBisAcht() {
        val h = NetzTestHilfe
        val toechter = (1..20).map { h.kante("A", Netz.Art.TOCHTER, "T$it") }
        val g = h.netz(*toechter.toTypedArray())
        val ids = miniAuswahl(g, "A", toechter.map { it.nach }, NetzTestHilfe.HEUTE)
        assertEquals(9, ids.size)
        assertTrue(ids.drop(1).all { it.startsWith("T") })
    }
}
