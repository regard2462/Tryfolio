package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.HeatmapBeschriftung.Masse
import de.troetenrepublik.app.engine.HeatmapBeschriftung.Stufe
import de.troetenrepublik.app.engine.HeatmapBeschriftung.Text
import org.junit.Assert.assertEquals
import org.junit.Test

// Gleich grosse Kacheln werden gleich beschriftet, unabhaengig von der Laenge des Kuerzels.
class Fassung077S2HeatmapTest {
    private val innen = 8f
    private val bezug = Masse(
        kuerzelGross = Text(1f, 17f), kuerzelKlein = Text(1f, 13f), prozent = listOf(Text(40f, 14f), Text(32f, 14f)),
        bezugGross = 40f, bezugKlein = 30f, bezugProzent = 40f,
    )

    private fun masse(gross: Float, klein: Float, prozent: List<Float> = listOf(40f, 32f)) =
        bezug.copy(kuerzelGross = Text(gross, 17f), kuerzelKlein = Text(klein, 13f), prozent = prozent.map { Text(it, 14f) })

    @Test fun kurzesUndLangesKuerzelGleicheKachel() {
        for (breite in listOf(20f, 30f, 38f, 45f, 48f, 52f, 60f, 80f)) {
            val kurz = HeatmapBeschriftung.waehlen(breite, 50f, innen, masse(10f, 8f), false)
            val lang = HeatmapBeschriftung.waehlen(breite, 50f, innen, masse(40f, 30f), false)
            assertEquals("Breite $breite", lang, kurz)
        }
    }

    @Test fun kurzesKuerzelBrauchtDenPlatzDesUeblichen() {
        // 44 breit: innen 36, "V" (10) passt, AMZN (40) und die Prozentzahl nicht
        assertEquals(Stufe.KUERZEL, HeatmapBeschriftung.waehlen(44f, 50f, innen, masse(10f, 8f), false).stufe)
        // 36 breit: innen 28, auch das kleine Bezugskuerzel (30) passt nicht
        assertEquals(Stufe.NICHTS, HeatmapBeschriftung.waehlen(36f, 50f, innen, masse(10f, 8f), false).stufe)
    }

    @Test fun ohneBezugWieVorher() {
        val m = Masse(Text(10f, 17f), Text(8f, 13f), listOf(Text(40f, 14f)))
        assertEquals(Stufe.KUERZEL_PROZENT, HeatmapBeschriftung.waehlen(50f, 50f, innen, m, false).stufe)
        assertEquals(Stufe.NICHTS, HeatmapBeschriftung.waehlen(15f, 50f, innen, m, false).stufe)
    }

    @Test fun prozentErscheintNurWennDieUeblichePassend() {
        // Prozentzahl "+3,1 %" ist schmal (20), die uebliche (40) passt nicht: nur Kuerzel
        val schmal = masse(40f, 30f, listOf(20f))
        val w = HeatmapBeschriftung.waehlen(45f, 50f, innen, schmal, false)
        assertEquals(Stufe.KUERZEL, w.stufe)
        // gleiche Kachel mit der ueblichen Zahl: ebenso
        assertEquals(Stufe.KUERZEL, HeatmapBeschriftung.waehlen(45f, 50f, innen, masse(40f, 30f, listOf(40f, 32f)), false).stufe)
    }

    @Test fun breitereZahlFaelltAufEineStelleZurueck() {
        // "-12,34 %" (55) passt nicht, "-12,3 %" (45) und das Bezugsmass (40) schon
        val m = masse(40f, 30f, listOf(55f, 45f))
        val w = HeatmapBeschriftung.waehlen(60f, 50f, innen, m, false)
        assertEquals(Stufe.KUERZEL_PROZENT, w.stufe)
        assertEquals(1, w.prozent)
    }
}
