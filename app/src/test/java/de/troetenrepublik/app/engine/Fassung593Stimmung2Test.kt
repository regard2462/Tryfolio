package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Nachfrage nach sicheren Haefen (5.93): Rendite des Aktienindex gegen Staatsanleihen ueber 20 Handelstage, erfundene Kurse.
class Fassung593Stimmung2Test {

    private val tag = 86_400_000L

    @Test
    fun aktienWeitVorn() {
        // Staerker werdendes Wachstum: Die Rendite ueber 20 Tage steigt bis zuletzt, der Unterschied ist so gross wie nie
        var kurs = 100.0
        val aktien = (0 until 200).map { i -> if (i > 0) kurs *= 1.01 + 0.0001 * i; i * tag to kurs }
        val b = Stimmungsbarometer.sicherBarometer(aktien, (0 until 200).map { it * tag to 100.0 })!!
        assertTrue(b.wert > 95)
        assertEquals(Stimmungsbarometer.Stufe.EXTREME_GIER, b.stufe)
        assertTrue(b.teile.first().wert > 0)
    }

    @Test
    fun aktienHintenDran() {
        val b = Stimmungsbarometer.sicherBarometer(
            (0 until 200).map { it * tag to (300.0 - it) },
            (0 until 200).map { it * tag to 100.0 },
        )!!
        assertTrue(b.wert < 5)
        assertEquals(Stimmungsbarometer.Stufe.EXTREME_ANGST, b.stufe)
    }

    @Test
    fun gleichStand() {
        // Konstanter Unterschied von null: alle Tage zaehlen als kleiner oder gleich heute
        val b = Stimmungsbarometer.sicherBarometer(
            (0 until 200).map { it * tag to 100.0 },
            (0 until 200).map { it * tag to 100.0 },
        )!!
        assertEquals(100.0, b.wert, 1e-9)
    }

    @Test
    fun zuWenigGemeinsameTage() {
        assertNull(Stimmungsbarometer.sicherBarometer(
            (0 until 100).map { it * tag to (100.0 + it) },
            (0 until 100).map { it * tag to 100.0 },
        ))
        // Anleihen nur an jedem zweiten Tag, gemeinsam bleiben unter 120 Tage
        assertNull(Stimmungsbarometer.sicherBarometer(
            (0 until 200).map { it * tag to (100.0 + it) },
            (0 until 200 step 2).map { it * tag to 100.0 },
        ))
    }

    @Test
    fun verlauf() {
        val b = Stimmungsbarometer.sicherBarometer(
            (0 until 200).map { it * tag to (100.0 + it) },
            (0 until 200).map { it * tag to 100.0 },
        )!!
        assertTrue(b.verlauf.size <= 120)
        assertTrue(b.verlauf.zipWithNext().all { (a, c) -> a.first < c.first })
    }

    @Test
    fun unterschiedUeber20Tage() {
        // Bis Tag 179 konstant 100, dann linear bis 110 (Aktien) und 105 (Anleihen): Unterschied am Ende 5 Prozentpunkte
        val aktien = (0 until 200).map { i -> i * tag to if (i < 180) 100.0 else 100.0 + (i - 179) * 0.5 }
        val anleihen = (0 until 200).map { i -> i * tag to if (i < 180) 100.0 else 100.0 + (i - 179) * 0.25 }
        val b = Stimmungsbarometer.sicherBarometer(aktien, anleihen)!!
        assertEquals(5.0, b.teile.first().wert, 1e-9)
    }
}
