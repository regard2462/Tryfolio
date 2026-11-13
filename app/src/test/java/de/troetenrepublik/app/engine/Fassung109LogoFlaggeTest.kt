package de.troetenrepublik.app.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Fassung109LogoFlaggeTest {

    private val weiss = 0xFFFFFFFF.toInt()
    private val rot = 0xFFBC002D.toInt()

    // Quadratisches Feld, mittige Scheibe mit dem gegebenen Anteil der Seite
    private fun scheibe(n: Int, anteil: Double, farbe: Int = rot, feld: Int = weiss, mx: Double = n / 2.0): IntArray {
        val r = n * anteil / 2
        return IntArray(n * n) { i ->
            val x = i % n + 0.5; val y = i / n + 0.5
            if ((x - mx) * (x - mx) + (y - n / 2.0) * (y - n / 2.0) <= r * r) farbe else feld
        }
    }

    @Test fun japanFlaggeIstKeinSymbol() {
        val px = scheibe(100, 0.6)
        assertTrue(LogoFreistellen.flaggeMitScheibe(px, 100, 100))
        assertFalse(LogoFreistellen.einfachesSymbol(px, 100, 100))
    }

    @Test fun kleinesZeichenAufKachelBleibtSymbol() {
        val px = scheibe(100, 0.3)
        assertFalse(LogoFreistellen.flaggeMitScheibe(px, 100, 100))
        assertTrue(LogoFreistellen.einfachesSymbol(px, 100, 100))
    }

    @Test fun ausmittigeScheibeUndQuadratKeineScheibeFlagge() {
        assertFalse(LogoFreistellen.flaggeMitScheibe(scheibe(100, 0.6, mx = 70.0), 100, 100))
        val quadrat = IntArray(10_000) { i -> if (i % 100 in 20..79 && i / 100 in 20..79) rot else weiss }
        assertFalse(LogoFreistellen.flaggeMitScheibe(quadrat, 100, 100))
        assertFalse(LogoFreistellen.flaggeMitScheibe(IntArray(10_000) { weiss }, 100, 100))
    }
}
