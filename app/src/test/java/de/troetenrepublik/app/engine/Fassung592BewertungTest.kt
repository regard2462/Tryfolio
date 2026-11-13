package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Bewertungsblick.Ampel
import de.troetenrepublik.app.engine.Bewertungsblick.Grund
import de.troetenrepublik.app.engine.Qualitaet.Punkt
import de.troetenrepublik.app.engine.Qualitaet.Pruefung
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 5.92: Ampeln fuer Bewertung, Bilanz und Wachstum, Richtung der Leerverkaeufe, Zahlen gegen die Erwartung.
class Fassung592BewertungTest {

    @Test fun bewertungsampel() {
        assertEquals(Ampel.GRUEN, Bewertungsblick.bewertung(12.0, 15.0, null).ampel)
        assertEquals(Ampel.GELB, Bewertungsblick.bewertung(12.0, 9.0, null).ampel)
        assertEquals(Ampel.ROT, Bewertungsblick.bewertung(12.0, 2.0, 20.0).ampel)
        // Ohne bisheriges Wachstum zaehlt das erwartete
        val e = Bewertungsblick.bewertung(12.0, null, 13.0)
        assertEquals(Ampel.GRUEN, e.ampel)
        assertEquals(13.0, e.vergleich!!, 1e-9)
        // Ohne Vergleich nur das noetige Wachstum
        assertEquals(Ampel.GRUEN, Bewertungsblick.bewertung(2.0, null, null).ampel)
        assertEquals(Ampel.GELB, Bewertungsblick.bewertung(8.0, null, null).ampel)
        assertEquals(Ampel.ROT, Bewertungsblick.bewertung(15.0, null, null).ampel)
    }

    @Test fun fairerWertPasstZumEingepreistenWachstum() {
        val kgv = 30.0
        val noetig = ReverseDcf.wachstum(kgv, Bewertungsblick.RENDITE, Bewertungsblick.ZIEL_KGV, Bewertungsblick.JAHRE)!!.wachstum
        // Beim eingepreisten Wachstum ist der faire Wert der Kurs
        assertEquals(150.0, Bewertungsblick.fairerWert(150.0, kgv, noetig)!!, 1e-6)
        assertTrue(Bewertungsblick.fairerWert(150.0, kgv, noetig + 5)!! > 150.0)
        assertTrue(Bewertungsblick.fairerWert(150.0, kgv, noetig - 5)!! < 150.0)
        // Gleich dem fairen Wert der Werkzeuge bei gleichen Annahmen
        val f = Analysis.fairValue(eps = 5.0, price = 150.0, growth = 9.0, years = 10, exitPe = 15.0, required = 8.0)!!
        assertEquals(f.fair, Bewertungsblick.fairerWert(150.0, kgv, 9.0)!!, 1e-6)
        assertNull(Bewertungsblick.fairerWert(150.0, -3.0, 9.0))
        // Annahme begrenzt
        assertEquals(25.0, Bewertungsblick.annahme(60.0)!!, 1e-9)
        assertEquals(0.0, Bewertungsblick.annahme(-8.0)!!, 1e-9)
        assertNull(Bewertungsblick.annahme(null))
    }

    private fun piotroski(vararg erfuellt: Pair<Punkt, Boolean?>): Qualitaet.Piotroski {
        val gesetzt = erfuellt.toMap()
        return Qualitaet.Piotroski("2025-12-31", "2024-12-31", Punkt.entries.map { Pruefung(it, gesetzt[it] ?: true) })
    }

    private fun altman(zone: Qualitaet.Zone) = Qualitaet.Altman(
        when (zone) { Qualitaet.Zone.SICHER -> 4.0; Qualitaet.Zone.GRAU -> 2.5; Qualitaet.Zone.GEFAEHRDET -> 1.0 }, zone, 0.0, 0.0, 0.0, 0.0, 0.0,
    )

    @Test fun bilanzampel() {
        // Neun von neun, sichere Zone: gruen, gute Gruende zuerst, hoechstens drei
        val stark = Bewertungsblick.bilanz(piotroski(), altman(Qualitaet.Zone.SICHER))!!
        assertEquals(Ampel.GRUEN, stark.ampel)
        assertEquals(listOf(Grund.PROFITABEL, Grund.SCHULDEN_SINKEN, Grund.GEWINNE_STEIGEN), stark.gruende)
        // Schwach (drei von neun): rot, schlechte Gruende zuerst
        val schwach = Bewertungsblick.bilanz(
            piotroski(Punkt.RENDITE to false, Punkt.CASHFLOW to false, Punkt.RENDITE_STEIGT to false, Punkt.SCHULDEN_SINKEN to false,
                Punkt.LIQUIDITAET_STEIGT to false, Punkt.KEINE_NEUEN_AKTIEN to false),
            null,
        )!!
        assertEquals(Ampel.ROT, schwach.ampel)
        assertEquals(listOf(Grund.VERLUST, Grund.SCHULDEN_STEIGEN, Grund.GEWINNE_SINKEN), schwach.gruende)
        // Gefaehrdete Zone macht rot, auch bei gutem F-Score
        val gefaehrdet = Bewertungsblick.bilanz(piotroski(), altman(Qualitaet.Zone.GEFAEHRDET))!!
        assertEquals(Ampel.ROT, gefaehrdet.ampel)
        assertEquals(Grund.PLEITE_RISIKO, gefaehrdet.gruende.first())
        // Mittel: gelb, ein guter Punkt, dann die schlechten
        val mittel = Bewertungsblick.bilanz(
            piotroski(Punkt.SCHULDEN_SINKEN to false, Punkt.RENDITE_STEIGT to false, Punkt.LIQUIDITAET_STEIGT to false, Punkt.MARGE_STEIGT to false),
            altman(Qualitaet.Zone.GRAU),
        )!!
        assertEquals(Ampel.GELB, mittel.ampel)
        assertEquals(listOf(Grund.PROFITABEL, Grund.SCHULDEN_STEIGEN, Grund.GEWINNE_SINKEN), mittel.gruende)
        // Nur Altman
        assertEquals(Ampel.GRUEN, Bewertungsblick.bilanz(null, altman(Qualitaet.Zone.SICHER))!!.ampel)
        assertEquals(Ampel.GELB, Bewertungsblick.bilanz(null, altman(Qualitaet.Zone.GRAU))!!.ampel)
        assertNull(Bewertungsblick.bilanz(null, null))
    }

    @Test fun wachstumsampel() {
        assertEquals(Ampel.GRUEN, Bewertungsblick.wachstum(10.0, 5.0))
        assertEquals(Ampel.GRUEN, Bewertungsblick.wachstum(10.0, null))
        assertEquals(Ampel.GELB, Bewertungsblick.wachstum(2.0, 1.0))
        assertEquals(Ampel.GELB, Bewertungsblick.wachstum(10.0, -5.0))
        assertEquals(Ampel.ROT, Bewertungsblick.wachstum(-5.0, 3.0))
        assertEquals(Ampel.ROT, Bewertungsblick.wachstum(10.0, -20.0))
        assertNull(Bewertungsblick.wachstum(null, null))
        assertEquals(10.0, Bewertungsblick.veraenderung(110.0, 100.0)!!, 1e-9)
        assertNull(Bewertungsblick.veraenderung(110.0, 0.0))
        assertNull(Bewertungsblick.veraenderung(null, 100.0))
    }

    @Test fun leerverkaufstrend() {
        assertEquals(Bewertungsblick.Trend.STEIGEND, Bewertungsblick.leerTrend(131_591_865.0, 120_000_000.0))
        assertEquals(Bewertungsblick.Trend.FALLEND, Bewertungsblick.leerTrend(0.9, 1.2))
        assertEquals(Bewertungsblick.Trend.GLEICH, Bewertungsblick.leerTrend(1.02, 1.0))
        assertNull(Bewertungsblick.leerTrend(1.0, null))
        assertNull(Bewertungsblick.leerTrend(1.0, 0.0))
    }

    @Test fun zahlenGegenErwartung() {
        val besser = Bewertungsblick.ueberraschung(1.85, 1.77)!!
        assertEquals(Ampel.GRUEN, besser.ampel)
        assertEquals((1.85 - 1.77) / 1.77 * 100, besser.abweichung, 1e-9)
        assertEquals(Ampel.GELB, Bewertungsblick.ueberraschung(1.0, 1.01)!!.ampel)
        val schlechter = Bewertungsblick.ueberraschung(0.9, 1.0)!!
        assertEquals(Ampel.ROT, schlechter.ampel)
        assertEquals(-10.0, schlechter.abweichung, 1e-9)
        // Erwarteter Verlust, kleinerer Verlust eingetreten: besser
        val verlust = Bewertungsblick.ueberraschung(-0.4, -0.5)!!
        assertEquals(Ampel.GRUEN, verlust.ampel)
        assertEquals(20.0, verlust.abweichung, 1e-9)
        assertNull(Bewertungsblick.ueberraschung(1.0, 0.0))
    }
}
