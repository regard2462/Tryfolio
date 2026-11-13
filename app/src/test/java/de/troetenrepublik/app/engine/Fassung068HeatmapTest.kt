package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.HeatmapBeschriftung.Masse
import de.troetenrepublik.app.engine.HeatmapBeschriftung.Stufe
import de.troetenrepublik.app.engine.HeatmapBeschriftung.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Fassung068HeatmapTest {
    private val masse = Masse(
        kuerzelGross = Text(30f, 17f), kuerzelKlein = Text(24f, 13f),
        prozent = listOf(Text(40f, 14f), Text(32f, 14f)), name = Text(80f, 34f), nameWort = 60f,
    )
    private val innen = 8f

    @Test fun kuerzelOhneBoersenendung() {
        assertEquals("SAP", HeatmapBeschriftung.kuerzel("SAP.DE"))
        assertEquals("BRK", HeatmapBeschriftung.kuerzel("BRK-B"))
        assertEquals("AAPL", HeatmapBeschriftung.kuerzel("AAPL"))
        assertEquals("0700", HeatmapBeschriftung.kuerzel("0700.HK"))
    }

    @Test fun ohneZoomNieDerName() {
        val w = HeatmapBeschriftung.waehlen(100f, 60f, innen, masse, false)
        assertEquals(Stufe.KUERZEL_PROZENT, w.stufe)
        assertEquals(0, w.prozent)
    }

    @Test fun mitZoomDerName() {
        val w = HeatmapBeschriftung.waehlen(100f, 60f, innen, masse, true)
        assertEquals(Stufe.NAME_PROZENT, w.stufe)
        assertEquals(0, w.prozent)
    }

    @Test fun nameZuHoch() {
        val w = HeatmapBeschriftung.waehlen(100f, 40f, innen, masse, true)
        assertEquals(Stufe.KUERZEL_PROZENT, w.stufe)
        assertEquals(0, w.prozent)
    }

    @Test fun prozentMitEinerNachkommastelle() {
        val w = HeatmapBeschriftung.waehlen(44f, 40f, innen, masse, false)
        assertEquals(Stufe.KUERZEL_PROZENT, w.stufe)
        assertEquals(1, w.prozent)
    }

    @Test fun nurKleinesKuerzel() {
        val w = HeatmapBeschriftung.waehlen(36f, 40f, innen, masse, false)
        assertEquals(Stufe.KUERZEL, w.stufe)
    }

    @Test fun zuKleinFuerAlles() {
        val w = HeatmapBeschriftung.waehlen(30f, 18f, innen, masse, false)
        assertEquals(Stufe.NICHTS, w.stufe)
    }

    @Test fun ohneNameBleibtKuerzel() {
        val w = HeatmapBeschriftung.waehlen(100f, 60f, innen, masse.copy(name = null), true)
        assertEquals(Stufe.KUERZEL_PROZENT, w.stufe)
    }

    @Test fun zuBreitesWortWirdNichtZerteilt() {
        val w = HeatmapBeschriftung.waehlen(100f, 60f, innen, masse.copy(nameWort = 95f), true)
        assertEquals(Stufe.KUERZEL_PROZENT, w.stufe)
    }

    @Test fun ohneProzentNurKleinesKuerzel() {
        val w = HeatmapBeschriftung.waehlen(100f, 60f, innen, masse.copy(prozent = emptyList()), true)
        assertEquals(Stufe.KUERZEL, w.stufe)
    }

    @Test fun anteilStaerke() {
        assertEquals(0f, HeatmapBeschriftung.anteil(null, 3.0), 1e-4f)
        assertEquals(0f, HeatmapBeschriftung.anteil(0.01, 3.0), 1e-4f)
        assertEquals(1f, HeatmapBeschriftung.anteil(3.0, 3.0), 1e-4f)
        assertEquals(-0.65f, HeatmapBeschriftung.anteil(-1.5, 3.0), 1e-4f)
        assertEquals(1f, HeatmapBeschriftung.anteil(30.0, 3.0), 1e-4f)
    }

    @Test fun mischenRaender() {
        val von = 0xFF336699.toInt()
        val nach = 0xFFCC9933.toInt()
        assertEquals(von, HeatmapBeschriftung.mischen(von, nach, 0f))
        assertEquals(nach, HeatmapBeschriftung.mischen(von, nach, 1f))
    }

    @Test fun mischenMitteIstGrau() {
        val c = HeatmapBeschriftung.mischen(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0.5f)
        val r = (c shr 16) and 255
        val g = (c shr 8) and 255
        val b = c and 255
        assertEquals(r.toDouble(), g.toDouble(), 1.0)
        assertEquals(g.toDouble(), b.toDouble(), 1.0)
        assertTrue(r > 0x50)
        assertTrue(r < 0xB0)
        assertEquals(0xFF, (c ushr 24) and 255)
    }

    @Test fun farbeNachAenderung() {
        val hoch = 0xFF22AA55.toInt()
        val runter = 0xFFCC3344.toInt()
        val neutral = 0xFF2E2F30.toInt()
        assertEquals(neutral, HeatmapBeschriftung.farbe(null, 3.0, hoch, runter, neutral))
        assertEquals(hoch, HeatmapBeschriftung.farbe(5.0, 3.0, hoch, runter, neutral))
        assertEquals(runter, HeatmapBeschriftung.farbe(-5.0, 3.0, hoch, runter, neutral))
    }

    @Test fun widgetZielDerHeatmap() {
        for (q in listOf("MARKT", "DEPOT", "WATCHLIST", "SP500", "NIKKEI225")) {
            val ziel = de.troetenrepublik.app.data.WidgetZiel.heatmap(q, 5)
            assertEquals(ziel, de.troetenrepublik.app.data.WidgetZiel.pruefen(ziel))
        }
        assertEquals(null, de.troetenrepublik.app.data.WidgetZiel.pruefen("heatmap/DAX/9"))
        assertEquals(null, de.troetenrepublik.app.data.WidgetZiel.pruefen("heatmap/../0"))
    }

    @Test fun schriftFarbe() {
        assertTrue(HeatmapBeschriftung.dunkleSchrift(0xFFFFFFFF.toInt()))
        assertFalse(HeatmapBeschriftung.dunkleSchrift(0xFF000000.toInt()))
        assertFalse(HeatmapBeschriftung.dunkleSchrift(0xFF2E2F30.toInt()))
        assertTrue(HeatmapBeschriftung.dunkleSchrift(0xFFB5B7BA.toInt()))
    }
}
