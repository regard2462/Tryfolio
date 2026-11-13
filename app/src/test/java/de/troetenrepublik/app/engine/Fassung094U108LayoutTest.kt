package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

// Anordnung: zwei Kanten einer Firma laufen nie in genau entgegengesetzte Richtungen (sonst sieht es aus wie eine Linie durch die
// Firma), und die kleine Ansicht stellt die Firma in die Mitte, Kanten zur Firma links, Kanten von ihr weg rechts, ohne Ueberlappung.
class Fassung094U108LayoutTest {

    private fun kasten(id: String, b: Float = 100f, h: Float = 30f) = NetzLayout.Kasten(id, b, h)

    // Winkel in Grad zwischen den Kanten von c zu a und von c zu b
    private fun winkel(p: Map<String, NetzLayout.Punkt>, c: String, a: String, b: String): Double {
        val ax = (p.getValue(a).x - p.getValue(c).x).toDouble(); val ay = (p.getValue(a).y - p.getValue(c).y).toDouble()
        val bx = (p.getValue(b).x - p.getValue(c).x).toDouble(); val bq = (p.getValue(b).y - p.getValue(c).y).toDouble()
        val k = (ax * bx + ay * bq) / (sqrt(ax * ax + ay * ay) * sqrt(bx * bx + bq * bq))
        return Math.toDegrees(acos(k.coerceIn(-1.0, 1.0)))
    }

    private fun ueberlappt(k: List<NetzLayout.Kasten>, p: Map<String, NetzLayout.Punkt>): Boolean {
        for (i in k.indices) for (j in i + 1 until k.size) {
            val a = k[i]; val b = k[j]
            if (abs(p.getValue(a.id).x - p.getValue(b.id).x) < (a.breite + b.breite) / 2f &&
                abs(p.getValue(a.id).y - p.getValue(b.id).y) < (a.hoehe + b.hoehe) / 2f) return true
        }
        return false
    }

    @Test fun zweiNachbarnStehenNieGenauGegenueber() {
        val k = listOf(kasten("firma"), kasten("oben"), kasten("unten"))
        val p = NetzLayout.berechnen(k, listOf("oben" to "firma", "firma" to "unten"), mitte = "firma")
        assertTrue("Winkel ${winkel(p, "firma", "oben", "unten")}", winkel(p, "firma", "oben", "unten") < 165.0)
        assertTrue(!ueberlappt(k, p))
    }

    @Test fun auchOhneMitteUndMitFremdenKnoten() {
        val k = listOf(kasten("a"), kasten("b"), kasten("c"), kasten("d", 120f, 44f))
        val kanten = listOf("a" to "b", "b" to "c", "c" to "d")
        val p = NetzLayout.berechnen(k, kanten)
        assertTrue(winkel(p, "b", "a", "c") < 165.0)
        assertTrue(winkel(p, "c", "b", "d") < 165.0)
        assertTrue(!ueberlappt(k, p))
    }

    @Test fun dreiNachbarnKeinPaarGerade() {
        val k = listOf(kasten("m"), kasten("a"), kasten("b"), kasten("c"))
        val p = NetzLayout.berechnen(k, listOf("a" to "m", "m" to "b", "m" to "c"), mitte = "m")
        for ((x, y) in listOf("a" to "b", "a" to "c", "b" to "c")) assertTrue("$x$y ${winkel(p, "m", x, y)}", winkel(p, "m", x, y) < 170.0)
    }

    @Test fun gleicheEingabeGleichesBildAuchMitDerKorrektur() {
        val k = listOf(kasten("firma"), kasten("oben"), kasten("unten"))
        val kanten = listOf("oben" to "firma", "firma" to "unten")
        val a = NetzLayout.berechnen(k, kanten, mitte = "firma")
        val b = NetzLayout.berechnen(k, kanten, mitte = "firma")
        for (id in a.keys) { assertEquals(a.getValue(id).x, b.getValue(id).x, 0f); assertEquals(a.getValue(id).y, b.getValue(id).y, 0f) }
    }

    // ------------------------------------------------------------ kleine Ansicht

    @Test fun faecherSetztKantenZurFirmaLinksUndKantenVonIhrWegRechts() {
        val k = listOf(kasten("firma", 80f, 30f), kasten("z1", 120f, 40f), kasten("z2", 120f, 60f), kasten("a1", 120f, 40f), kasten("w", 90f, 30f))
        val kanten = listOf("z1" to "firma", "z2" to "firma", "firma" to "a1", "firma" to "w")
        val p = NetzLayout.faecher(k, kanten, "firma", luecke = 26f, abstand = 10f)
        assertEquals(5, p.size)
        assertEquals(0f, p.getValue("firma").x, 0f)
        assertEquals(0f, p.getValue("firma").y, 0f)
        assertTrue(p.getValue("z1").x < 0f && p.getValue("z2").x < 0f)
        assertTrue(p.getValue("a1").x > 0f && p.getValue("w").x > 0f)
        // Platz fuer die Linien zwischen Spalte und Firma
        assertTrue(p.getValue("z1").x + 120f / 2f <= -40f - 26f + 0.01f)
        assertTrue(p.getValue("a1").x - 120f / 2f >= 40f + 26f - 0.01f)
        assertTrue(!ueberlappt(k, p))
        // Spalten stehen untereinander, ohne sich zu beruehren
        assertTrue(abs(p.getValue("z1").y - p.getValue("z2").y) >= (40f + 60f) / 2f + 10f - 0.01f)
    }

    @Test fun faecherGleichtSchieflageAus() {
        val k = listOf(kasten("firma")) + (1..6).map { kasten("z$it") }
        val kanten = (1..6).map { "z$it" to "firma" }
        val p = NetzLayout.faecher(k, kanten, "firma", luecke = 20f)
        val links = (1..6).count { p.getValue("z$it").x < 0f }
        val rechts = 6 - links
        assertEquals(3, links)
        assertEquals(3, rechts)
        assertTrue(!ueberlappt(k, p))
    }

    @Test fun faecherOhneNachbarnUndOhneFirma() {
        assertEquals(1, NetzLayout.faecher(listOf(kasten("firma")), emptyList(), "firma", 20f).size)
        assertTrue(NetzLayout.faecher(listOf(kasten("x")), emptyList(), "firma", 20f).isEmpty())
    }

    @Test fun faecherSpaltenLiegenGanzNebenDerFirma() {
        val k = listOf(kasten("firma", 80f, 30f)) + (1..8).map { kasten("n$it", 110f, 36f + it) }
        val kanten = (1..4).map { "n$it" to "firma" } + (5..8).map { "firma" to "n$it" }
        val p = NetzLayout.faecher(k, kanten, "firma", luecke = 26f)
        for (i in 1..4) assertTrue(p.getValue("n$i").x + 55f <= -40f - 26f + 0.01f)
        for (i in 5..8) assertTrue(p.getValue("n$i").x - 55f >= 40f + 26f - 0.01f)
        assertTrue(!ueberlappt(k, p))
        // Die Spalten sind um die Firma zentriert
        val oben = (1..4).minOf { p.getValue("n$it").y - (36f + it) / 2f }
        val unten = (1..4).maxOf { p.getValue("n$it").y + (36f + it) / 2f }
        assertEquals(0f, oben + unten, 0.01f)
    }
}
