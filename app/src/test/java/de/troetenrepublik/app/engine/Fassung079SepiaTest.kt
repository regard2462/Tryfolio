package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Appearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

// Sepia-Erscheinungsbild und heller Globus: Theme-Auswahl und Kontrastwerte der Farben, rein rechnerisch.
class Fassung079SepiaTest {

    private fun mind(grenze: Double, a: Int, b: Int, name: String) {
        val k = Erscheinung.kontrast(a, b)
        assertTrue("$name: Kontrast $k unter $grenze", k >= grenze)
    }

    @Test
    fun themeAuswahl() {
        for (system in listOf(false, true)) {
            assertTrue(Erscheinung.dunkel(Appearance.DARK, system))
            assertFalse(Erscheinung.dunkel(Appearance.LIGHT, system))
            assertFalse(Erscheinung.dunkel(Appearance.SEPIA, system))
        }
        assertTrue(Erscheinung.dunkel(Appearance.SYSTEM, true))
        assertFalse(Erscheinung.dunkel(Appearance.SYSTEM, false))
        assertTrue(Erscheinung.sepia(Appearance.SEPIA))
        for (a in listOf(Appearance.SYSTEM, Appearance.LIGHT, Appearance.DARK)) assertFalse(Erscheinung.sepia(a))
        assertEquals(4, Appearance.entries.size)
        assertEquals(Appearance.SEPIA, Appearance.valueOf("SEPIA"))
    }

    @Test
    fun kontrastRechnung() {
        assertEquals(21.0, Erscheinung.kontrast(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 1e-9)
        assertEquals(1.0, Erscheinung.kontrast(0xFF336699.toInt(), 0xFF336699.toInt()), 1e-9)
    }

    @Test
    fun signalfarbenHellAufWeissUndFlaeche() {
        for (grund in listOf(Erscheinung.HELL_GRUND, Erscheinung.HELL_FLAECHE)) {
            mind(4.5, Erscheinung.HELL_GRUEN, grund, "Gruen hell")
            mind(4.5, Erscheinung.HELL_ROT, grund, "Rot hell")
            mind(4.5, Erscheinung.HELL_LINK, grund, "Link hell")
            mind(4.5, Erscheinung.HELL_WARN, grund, "Warn hell")
            mind(4.5, Erscheinung.HELL_TEXT2, grund, "Zweitschrift hell")
        }
        mind(3.0, Erscheinung.HELL_RAND, Erscheinung.HELL_GRUND, "Rand hell")
    }

    @Test
    fun signalfarbenSepiaAufPapierUndFlaeche() {
        for (grund in listOf(Erscheinung.SEPIA_GRUND, Erscheinung.SEPIA_FLAECHE)) {
            mind(4.5, Erscheinung.SEPIA_GRUEN, grund, "Gruen Sepia")
            mind(4.5, Erscheinung.SEPIA_ROT, grund, "Rot Sepia")
            mind(4.5, Erscheinung.SEPIA_LINK, grund, "Link Sepia")
            mind(4.5, Erscheinung.SEPIA_WARN, grund, "Warn Sepia")
            mind(4.5, Erscheinung.SEPIA_TEXT2, grund, "Zweitschrift Sepia")
            mind(7.0, Erscheinung.SEPIA_TEXT, grund, "Schrift Sepia")
            mind(7.0, Erscheinung.SEPIA_AKZENT, grund, "Akzent Sepia")
        }
        mind(3.0, Erscheinung.SEPIA_RAND, Erscheinung.SEPIA_GRUND, "Rand Sepia")
        // Papierton und Flaechen bleiben unterscheidbar, aber ruhig
        val papier = Erscheinung.kontrast(Erscheinung.SEPIA_GRUND, Erscheinung.SEPIA_FLAECHE)
        assertTrue(papier > 1.05 && papier < 1.5)
        // Gruen und Rot unterscheiden sich im Sepia-Design deutlich im Farbton, nicht nur in der Helligkeit
        val g = Erscheinung.SEPIA_GRUEN; val r = Erscheinung.SEPIA_ROT
        assertTrue(((g shr 8) and 0xFF) > ((g shr 16) and 0xFF))
        assertTrue(((r shr 16) and 0xFF) > ((r shr 8) and 0xFF))
    }

    @Test
    fun globusPaletteAuswahl() {
        assertSame(GlobusPalette.DUNKEL, GlobusPalette.von(dunkel = true, sepia = false))
        assertSame(GlobusPalette.DUNKEL, GlobusPalette.von(dunkel = true, sepia = true))
        assertSame(GlobusPalette.HELL, GlobusPalette.von(dunkel = false, sepia = false))
        assertSame(GlobusPalette.SEPIA, GlobusPalette.von(dunkel = false, sepia = true))
        // Dunkel bleibt, wie es war
        assertEquals(GlobusFarbe.HINTERGRUND, GlobusPalette.DUNKEL.hintergrund)
        assertEquals(GlobusFarbe.KUGEL, GlobusPalette.DUNKEL.kugel)
        assertEquals(GlobusFarbe.OHNE_DATEN, GlobusPalette.DUNKEL.ohneDaten)
    }

    @Test
    fun heller_Globus_ist_hell_und_lesbar() {
        for ((name, p) in listOf("hell" to GlobusPalette.HELL, "sepia" to GlobusPalette.SEPIA)) {
            // Ozean und Grund hell
            assertTrue("$name Ozean", Erscheinung.leuchtdichte(p.kugel) > 0.5)
            assertTrue("$name Grund", Erscheinung.leuchtdichte(p.hintergrund) > 0.6)
            // Beschriftung auf Ozean und Land, Punkte ohne Vergleich auf dem Ozean
            mind(4.5, p.schrift, p.kugel, "$name Schrift auf Ozean")
            mind(4.5, p.schrift, p.ohneDaten, "$name Schrift auf Land")
            mind(3.0, p.punktOhne, p.kugel, "$name Punkt auf Ozean")
            mind(4.5, p.ladeSchrift, p.hintergrund, "$name Ladeschrift")
            // Grenzen heben sich vom Land ab, Land vom Ozean
            mind(2.0, p.grenze, p.ohneDaten, "$name Grenze")
            mind(1.1, p.ohneDaten, p.kugel, "$name Land gegen Ozean")
            // Schrift und Umriss sind Gegenpole
            assertTrue(Erscheinung.leuchtdichte(p.schriftRand) > Erscheinung.leuchtdichte(p.schrift))
        }
        // Dunkel: weisse Schrift mit dunklem Umriss
        assertTrue(Erscheinung.leuchtdichte(GlobusPalette.DUNKEL.schrift) > Erscheinung.leuchtdichte(GlobusPalette.DUNKEL.schriftRand))
    }

    @Test
    fun laenderfarbenFolgenDerPalette() {
        val up = Erscheinung.HELL_GRUEN; val down = Erscheinung.HELL_ROT; val neutral = 0xFFB7791F.toInt()
        // Ohne Wert: Farbe fuer Land ohne Daten der Palette; ohne Palette wie bisher
        assertEquals(GlobusFarbe.OHNE_DATEN, GlobusFarbe.farbe(GlobusModus.BOERSE, null, up, down, neutral))
        assertEquals(GlobusPalette.HELL.ohneDaten, GlobusFarbe.farbe(GlobusModus.BOERSE, null, up, down, neutral, GlobusPalette.HELL))
        assertEquals(GlobusPalette.SEPIA.ohneDaten, GlobusFarbe.farbe(GlobusModus.BOERSE, Double.NaN, up, down, neutral, GlobusPalette.SEPIA))
        // Starker Anstieg: auf dem hellen Globus die Signalfarbe, klar vom Ozean und von Land ohne Daten verschieden
        val stark = GlobusFarbe.farbe(GlobusModus.BOERSE, 10.0, up, down, neutral, GlobusPalette.HELL)
        assertEquals(up, stark)
        mind(3.0, stark, GlobusPalette.HELL.kugel, "Gruen auf Ozean")
        // Kleiner Wert hebt sich vom Land ohne Daten ab
        val klein = GlobusFarbe.farbe(GlobusModus.BOERSE, 0.0, up, down, neutral, GlobusPalette.HELL)
        assertNotEquals(GlobusPalette.HELL.ohneDaten, klein)
        // Handel: geschlossene Boerse in der Nullfarbe der Palette
        assertEquals(GlobusPalette.SEPIA.nullwert, GlobusFarbe.handel(false, up, GlobusPalette.SEPIA))
        assertEquals(GlobusFarbe.OHNE_DATEN, GlobusFarbe.handel(null, up))
        // Engpassstelle ohne Vergleich in der Punktfarbe der Palette
        assertEquals(GlobusPalette.HELL.punktOhne, GlobusFarbe.engpass(null, up, down, GlobusPalette.HELL))
        assertEquals(GlobusFarbe.PUNKT_OHNE, GlobusFarbe.engpass(null, up, down))
        // Depotanteil ohne Anteil: Land ohne Daten
        assertEquals(GlobusPalette.SEPIA.ohneDaten, GlobusFarbe.anteil(null, neutral, GlobusPalette.SEPIA))
    }
}
