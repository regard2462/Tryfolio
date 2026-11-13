package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Treemap.Rechteck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Fassung077CTest {
    private val rand = 4.5f
    private val textB = 60f
    private val textH = 14f
    private val oben = rand + textH + 2 * rand // wie anordnen: Rand plus Kopfzeile plus Rand
    private val sichtB = 360f
    private val sichtH = 600f

    // Gruppen im Layout der Stufe s: Spalten ueber die Breite
    private fun gruppen(s: Float) = listOf(
        Rechteck(0f, 0f, 200f * s, 300f * s), Rechteck(200f * s, 0f, 160f * s, 300f * s), Rechteck(0f, 300f * s, 360f * s, 300f * s),
    )

    private fun pruefe(s: Float, z: Float, vx: Float, vy: Float) {
        val k = z / s
        for (g in gruppen(s)) {
            val gz = HeatmapZoomLage.flaeche(g, k, vx, vy)
            val pos = HeatmapZoomLage.kopfPosition(gz, textB, textH, rand, 4f, sichtB, sichtH)
            if (pos != null) {
                // Text liegt ganz im Ausschnitt und in der eigenen gezoomten Gruppe, oben
                assertTrue(pos.first >= 0f && pos.first + textB <= sichtB)
                assertTrue(pos.second >= 0f && pos.second + textH <= sichtH)
                assertTrue(pos.first >= gz.x && pos.first + textB <= gz.x + gz.w)
                assertEquals(gz.y + rand, pos.second, 0.001f)
            }
            // Kacheln beginnen direkt unter der Kopfzeile, ohne Luecke und ohne Ueberdeckung
            val innen = Rechteck(g.x + rand, g.y + oben, g.w - 2 * rand, g.h - oben - rand)
            val kachel = HeatmapZoomLage.kachel(innen, g, rand, oben, k, vx, vy)
            assertEquals(gz.y + oben, kachel.y, 0.001f)
            assertEquals(gz.x + rand, kachel.x, 0.001f)
            assertEquals(gz.x + gz.w - rand, kachel.x + kachel.w, 0.01f)
            assertEquals(gz.y + gz.h - rand, kachel.y + kachel.h, 0.01f)
            assertTrue(kachel.y >= gz.y + rand + textH)
        }
    }

    @Test fun zoomEinsIstUnveraendert() {
        val g = Rechteck(10f, 20f, 200f, 300f)
        val t = Rechteck(g.x + rand, g.y + oben, 50f, 40f)
        val r = HeatmapZoomLage.kachel(t, g, rand, oben, 1f, 0f, 0f)
        assertEquals(t.x, r.x, 0.001f); assertEquals(t.y, r.y, 0.001f)
        assertEquals(t.w, r.w, 0.001f); assertEquals(t.h, r.h, 0.001f)
    }

    @Test fun zweifachUndVierfachObenLinks() {
        pruefe(2f, 2f, 0f, 0f)
        pruefe(4f, 4f, 0f, 0f)
    }

    @Test fun zwischenStufenWachstDieLueckeNicht() {
        // Stufe 2, Zoom 2.99: Layout der Stufe, Kopfzeile bleibt bei gleicher Hoehe
        pruefe(2f, 2.99f, -100f, -50f)
        pruefe(3f, 4.5f, -300f, -200f)
    }

    @Test fun verschobenNieAusserhalbDesAusschnitts() {
        for (z in listOf(2f, 4f)) for (vx in listOf(0f, -37f, -200f, -(360f * z - sichtB))) for (vy in listOf(0f, -11f, -150f, -(600f * z - sichtH))) pruefe(z, z, vx, vy)
    }

    @Test fun amRandRuecktDerNameInDenSichtbarenTeil() {
        val gz = Rechteck(-50f, 10f, 200f, 100f)
        val pos = HeatmapZoomLage.kopfPosition(gz, textB, textH, rand, 4f, sichtB, sichtH)
        assertNotNull(pos)
        assertEquals(4f, pos!!.first, 0.001f)
        // sichtbarer Teil der Gruppe zu schmal: Name bleibt weg
        assertNull(HeatmapZoomLage.kopfPosition(Rechteck(-170f, 10f, 200f, 100f), textB, textH, rand, 4f, sichtB, sichtH))
        assertNull(HeatmapZoomLage.kopfPosition(Rechteck(330f, 10f, 200f, 100f), textB, textH, rand, 4f, sichtB, sichtH))
    }

    @Test fun obenAngeschnittenBleibtWeg() {
        assertNull(HeatmapZoomLage.kopfPosition(Rechteck(10f, -8f, 200f, 100f), textB, textH, rand, 4f, sichtB, sichtH))
        assertNull(HeatmapZoomLage.kopfPosition(Rechteck(10f, sichtH - 10f, 200f, 100f), textB, textH, rand, 4f, sichtB, sichtH))
    }
}
