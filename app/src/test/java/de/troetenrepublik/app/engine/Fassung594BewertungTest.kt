package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Fassung 5.94: Korrekturen an Bewertung, Bilanzqualitaet und Analysten aus der Fachpruefung.
class Fassung594BewertungTest {

    @Test fun veraenderungBeiNegativemVorjahr() {
        assertEquals(150.0, Bewertungsblick.veraenderung(5.0, -10.0)!!, 1e-9)
        assertEquals(50.0, Bewertungsblick.veraenderung(-5.0, -10.0)!!, 1e-9)
        assertEquals(-20.0, Bewertungsblick.veraenderung(8.0, 10.0)!!, 1e-9)
    }

    @Test fun pegEinsIstNeutral() {
        assertEquals(50.0, Aktienfinder.pegPunkte(1.0), 1e-9)
        assertEquals(70.0, Aktienfinder.pegPunkte(0.5), 1e-9)
    }

    private val jahr = Qualitaet.Jahr(
        ende = "2025-12-31", bilanzsumme = 1000.0, verbindlichkeiten = 600.0, betriebskapital = 100.0,
        gewinnruecklagen = 200.0, ebit = 80.0, umsatz = 1500.0,
    )

    @Test fun altmanFuerHerstellerUndAndere() {
        val h = Qualitaet.altman(jahr, 800.0, Qualitaet.Fassung.HERSTELLER)!!
        assertEquals(1.2 * 0.1 + 1.4 * 0.2 + 3.3 * 0.08 + 0.6 * 800 / 600 + 1.5, h.z, 1e-9)
        val a = Qualitaet.altman(jahr, null, Qualitaet.Fassung.ANDERE)!!
        assertEquals(6.56 * 0.1 + 3.26 * 0.2 + 6.72 * 0.08 + 1.05 * 400 / 600, a.z, 1e-9)
        assertEquals(Qualitaet.Zone.GRAU, a.zone)
        assertEquals(Qualitaet.Zone.SICHER, Qualitaet.zone(2.7, Qualitaet.Fassung.ANDERE))
        assertEquals(Qualitaet.Zone.GRAU, Qualitaet.zone(2.7))
        assertNull(Qualitaet.altman(jahr, null, Qualitaet.Fassung.HERSTELLER))
    }

    @Test fun fassungNachBranche() {
        assertEquals(Qualitaet.Fassung.HERSTELLER, Qualitaet.fassung("Industrials", "Specialty Industrial Machinery"))
        assertEquals(Qualitaet.Fassung.ANDERE, Qualitaet.fassung("Technology", "Software - Infrastructure"))
        assertEquals(Qualitaet.Fassung.ANDERE, Qualitaet.fassung("Consumer Cyclical", "Internet Retail"))
        assertEquals(Qualitaet.Fassung.ANDERE, Qualitaet.fassung("Utilities", "Utilities - Regulated Electric"))
        assertNull(Qualitaet.fassung("Real Estate", "REIT - Industrial"))
        assertNull(Qualitaet.fassung("Financial Services", "Banks - Regional"))
        assertEquals(Qualitaet.Fassung.HERSTELLER, Qualitaet.fassungFinder("MASCHINEN"))
        assertEquals(Qualitaet.Fassung.ANDERE, Qualitaet.fassungFinder("SOFTWARE"))
        assertNull(Qualitaet.fassungFinder("IMMOBILIEN"))
    }

    @Test fun sellZaehltEinfach() {
        assertEquals(-1, Analystenbilanz.stufe("Sell"))
        assertEquals(-2, Analystenbilanz.stufe("Strong Sell"))
        assertEquals(1, Analystenbilanz.stufe("Buy"))
    }
}
