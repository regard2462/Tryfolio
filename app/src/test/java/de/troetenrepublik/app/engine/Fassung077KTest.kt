package de.troetenrepublik.app.engine

import org.junit.Assert.*
import org.junit.Test

// Tests fuer den erweiterten Chart (0.77): Darstellung, Marken und Achsenzahlen, Kerzengeometrie.
class Fassung077KTest {

    @Test
    fun ohneWahlHeikinAshi() {
        assertEquals("heikin", ChartLayout.art(""))
        assertEquals("heikin", ChartLayout.art("unbekannt"))
        assertEquals("kerzen", ChartLayout.art("kerzen"))
        assertEquals("linie", ChartLayout.art("linie"))
    }

    @Test
    fun achsentextBleibtImBild() {
        // Platz da: direkt rechts neben dem Zeichenbereich
        assertEquals(304f, ChartLayout.achsenTextX(300f, 360f, 40f, 4f, 2f), 0.001f)
        // Zu breit: nach links geschoben, rechter Rand bleibt frei
        assertEquals(360f - 70f - 2f, ChartLayout.achsenTextX(300f, 360f, 70f, 4f, 2f), 0.001f)
        // Nie links aus dem Bild
        assertEquals(0f, ChartLayout.achsenTextX(10f, 20f, 80f, 4f, 2f), 0.001f)
    }

    @Test
    fun markenUeberdeckenSichNicht() {
        val m = ChartLayout.markenMitten(listOf(100f, 104f, 108f), 14f, 340f)
        val s = m.sorted()
        assertTrue(s[1] - s[0] >= 14f - 1e-3f)
        assertTrue(s[2] - s[1] >= 14f - 1e-3f)
        // Reihenfolge der Eingabe bleibt: die mittlere Soll-Lage bleibt die mittlere
        assertTrue(m[0] < m[1] && m[1] < m[2])
    }

    @Test
    fun markenAmRandBleibenImBereich() {
        val unten = ChartLayout.markenMitten(listOf(338f, 339f, 340f), 14f, 340f)
        assertTrue(unten.all { it <= 340f - 7f + 1e-3f })
        assertTrue(unten.sorted().zipWithNext().all { (a, b) -> b - a >= 14f - 1e-3f })
        val oben = ChartLayout.markenMitten(listOf(0f, 1f), 14f, 340f)
        assertTrue(oben.all { it >= 7f - 1e-3f })
        assertTrue(ChartLayout.markenMitten(emptyList(), 14f, 340f).isEmpty())
    }

    @Test
    fun einzelneMarkeBleibtStehen() {
        assertEquals(120f, ChartLayout.markenMitten(listOf(120f), 14f, 340f)[0], 0.001f)
    }

    @Test
    fun achsenzahlWeichtDerMarke() {
        val sichtbar = ChartLayout.achsenSichtbar(listOf(50f, 100f, 150f), 12f, listOf(104f), 14f)
        assertEquals(listOf(true, false, true), sichtbar)
        assertEquals(listOf(true, true), ChartLayout.achsenSichtbar(listOf(10f, 20f), 12f, emptyList(), 14f))
    }

    @Test
    fun bezeichnungNurMitPlatz() {
        assertFalse(ChartLayout.bezeichnungPasst(60f, 30f, 45f, 4f))
        assertTrue(ChartLayout.bezeichnungPasst(120f, 30f, 45f, 4f))
    }

    @Test
    fun kaufkursInDerKerze() {
        assertEquals(304.61, ChartLayout.markenKurs(304.61, 300.0, 310.0, 305.0), 1e-9)
        assertEquals(310.0, ChartLayout.markenKurs(320.0, 300.0, 310.0, 305.0), 1e-9)
        assertEquals(300.0, ChartLayout.markenKurs(290.0, 300.0, 310.0, 305.0), 1e-9)
        assertEquals(305.0, ChartLayout.markenKurs(0.0, 300.0, 310.0, 305.0), 1e-9)
        assertEquals(305.0, ChartLayout.markenKurs(Double.NaN, 300.0, 310.0, 305.0), 1e-9)
    }

    @Test
    fun koerperEtwaSiebzigProzentUndDochtMittig() {
        val dp = 2.75f
        val schritt = 10f
        val s = ChartLayout.spalte(5f, schritt, dp)
        assertEquals(7f, s.breite, 0.001f)
        // Docht ganze Pixel, mittig im Koerper
        assertEquals(s.links + s.breite / 2f, s.dochtLinks + s.dochtBreite / 2f, 0.001f)
        assertEquals(s.links, Math.rint(s.links.toDouble()).toFloat(), 0.0001f)
        assertEquals(s.dochtLinks, Math.rint(s.dochtLinks.toDouble()).toFloat(), 0.0001f)
        assertTrue(s.dochtBreite >= 1f && s.dochtBreite <= s.breite)
    }

    @Test
    fun dichteKerzenOhneLuecke() {
        val schritt = 1.37f
        var rechtsVorher: Float? = null
        for (i in 0 until 400) {
            val s = ChartLayout.spalte((i + 0.5f) * schritt, schritt, 2.75f)
            assertTrue(s.breite >= 1f)
            rechtsVorher?.let { assertTrue("Luecke bei $i", s.links <= it) }
            rechtsVorher = s.links + s.breite
        }
    }

    @Test
    fun mittlereKerzenBleibenGetrennt() {
        val a = ChartLayout.spalte(5f, 10f, 2.75f)
        val b = ChartLayout.spalte(15f, 10f, 2.75f)
        assertTrue(b.links >= a.links + a.breite)
    }

    @Test
    fun dojiIstEinStrich() {
        assertEquals(2.75f, ChartLayout.koerperHoehe(100f, 100f, 2.75f), 0.001f)
        assertEquals(30f, ChartLayout.koerperHoehe(100f, 130f, 2.75f), 0.001f)
    }
}
