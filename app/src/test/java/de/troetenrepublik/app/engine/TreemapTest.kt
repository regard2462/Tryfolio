package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TreemapTest {
    private fun flaeche(r: Treemap.Rechteck) = r.w.toDouble() * r.h

    @Test fun flaechenImVerhaeltnisDerGewichte() {
        val g = listOf(6.0, 6.0, 4.0, 3.0, 2.0, 2.0, 1.0)
        val r = Treemap.anordnen(g, 600f, 400f)
        val summe = g.sum()
        g.forEachIndexed { i, w -> assertEquals(w / summe * 600 * 400, flaeche(r[i]), 1.0) }
        assertEquals(600.0 * 400, r.sumOf { flaeche(it) }, 1.0)
    }

    @Test fun allesLiegtInDerFlaecheUndUeberlapptNicht() {
        val g = (1..40).map { 1000.0 / it }
        val r = Treemap.anordnen(g, 360f, 500f, x = 10f, y = 20f)
        r.forEach {
            assertTrue(it.x >= 10f - 0.01f && it.y >= 20f - 0.01f)
            assertTrue(it.x + it.w <= 370f + 0.01f && it.y + it.h <= 520f + 0.01f)
        }
        for (a in r.indices) for (b in a + 1 until r.size) {
            val ra = r[a]; val rb = r[b]
            val ueberlappung = maxOf(0f, minOf(ra.x + ra.w, rb.x + rb.w) - maxOf(ra.x, rb.x)) * maxOf(0f, minOf(ra.y + ra.h, rb.y + rb.h) - maxOf(ra.y, rb.y))
            assertTrue("Kachel $a und $b ueberlappen", ueberlappung < 0.5f)
        }
    }

    @Test fun eingabereihenfolgeBleibtUndLeereGewichteSindLeer() {
        val r = Treemap.anordnen(listOf(1.0, 0.0, 5.0, Double.NaN), 100f, 100f)
        assertTrue(flaeche(r[2]) > flaeche(r[0]))
        assertEquals(0.0, flaeche(r[1]), 0.0)
        assertEquals(0.0, flaeche(r[3]), 0.0)
    }

    @Test fun kachelnSindNichtZuSchmal() {
        val r = Treemap.anordnen(List(16) { 1.0 }, 400f, 400f)
        r.forEach { assertTrue(maxOf(it.w / it.h, it.h / it.w) < 2.01f) }
    }
}
