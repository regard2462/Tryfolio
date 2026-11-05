package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.BullBear.Kennzahl
import de.troetenrepublik.app.engine.BullBear.Schlagzeile
import de.troetenrepublik.app.engine.BullBear.Seite
import de.troetenrepublik.app.engine.BullBear.Thema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 5.7: Bull- und Bear-Case aus Schlagzeilen und Kennzahlen.
class Fassung57Test {
    private val jetzt = 1_792_800_000_000L

    @Test fun themenAusSchlagzeilen() {
        assertTrue(Thema.BILANZ in BullBear.themen("Super Micro delays annual report after auditor resigns"))
        assertTrue(Thema.BILANZ in BullBear.themen("Hindenburg short report accuses company of accounting manipulation"))
        assertTrue(Thema.ERMITTLUNG in BullBear.themen("DOJ opens probe into Super Micro"))
        assertTrue(Thema.KLAGE in BullBear.themen("Investors file class action lawsuit"))
        assertTrue(Thema.KI in BullBear.themen("Super Micro shares jump on AI server demand"))
        assertTrue(Thema.WARNUNG in BullBear.themen("Bayer senkt Prognose für 2026"))
        assertTrue(Thema.AUFSTUFUNG in BullBear.themen("Goldman hebt Kursziel für SAP"))
        assertTrue(BullBear.themen("Apple unveils new iPhone colors").isEmpty())
        // „sec“ nur als eigenes Wort, nicht in „second“
        assertFalse(Thema.ERMITTLUNG in BullBear.themen("Second quarter results due next week"))
    }

    @Test fun belegeJeThemaUndAlterGrenze() {
        val tag = 86_400_000L
        val punkte = BullBear.auswerten(BullBear.Fakten(), listOf(
            Schlagzeile("Class action lawsuit filed", jetzt - tag, "1"),
            Schlagzeile("Second lawsuit filed by investors", jetzt - 2 * tag, "2"),
            Schlagzeile("Old lawsuit settled", jetzt - 100 * tag, "3"),
            Schlagzeile("Company beats estimates", jetzt - tag, "4"),
        ), jetzt)
        val klage = punkte.single { it.thema == Thema.KLAGE }
        assertEquals(Seite.BEAR, klage.seite)
        assertEquals(listOf("1", "2"), klage.belege.map { it.id })
        assertEquals(Seite.BULL, punkte.single { it.thema == Thema.ZAHLEN }.seite)
    }

    @Test fun kennzahlenNachSchwellen() {
        val f = BullBear.Fakten(
            kurs = 100.0, kursziel = 130.0, empfehlung = 1.8, analysten = 20, umsatzWachstum = 0.25, gewinnMarge = -0.05,
            kasse = 5e9, schulden = 1e9, freierCashflow = -2e8, marktwert = 2e10, kgv = 60.0, kgvErwartet = 30.0, beta = 1.8,
            branche = "Technology", industrie = "Semiconductors", ergebnisse = listOf(1.0 to 0.9, 1.1 to 1.0, 1.2 to 1.0, 0.8 to 1.0), insider = -80,
        )
        val k = BullBear.auswerten(f, emptyList(), jetzt).mapNotNull { it.kennzahl }.toSet()
        assertTrue(Kennzahl.ANALYSTEN_POSITIV in k)
        assertTrue(Kennzahl.WACHSTUM in k)
        assertTrue(Kennzahl.VERLUST in k)
        assertTrue(Kennzahl.NETTOKASSE in k)
        assertTrue(Kennzahl.CASHFLOW_NEGATIV in k)
        assertTrue(Kennzahl.UEBERTROFFEN in k)
        assertTrue(Kennzahl.GEWINNE_STEIGEN in k)
        assertTrue(Kennzahl.BEWERTUNG_HOCH in k)
        assertTrue(Kennzahl.SCHWANKUNG in k)
        assertTrue(Kennzahl.ZYKLISCH in k)
        assertTrue(Kennzahl.INSIDER_VERKAUFEN in k)
        assertFalse(Kennzahl.SCHULDEN in k)
    }

    @Test fun nurSchlagzeilenZurFirma() {
        val s = listOf(
            Schlagzeile("Supermicro raises outlook on AI demand", jetzt, "1"),
            Schlagzeile("Dell unveils new AI servers after Barclays downgrade", jetzt, "2"),
            Schlagzeile("SMCI shares jump", jetzt, "3"),
            Schlagzeile("Super Micro beats estimates", jetzt, "4"),
        )
        assertEquals(listOf("1", "3", "4"), BullBear.zurFirma(s, "SMCI", listOf("super", "micro", "computer")).map { it.id })
        // Ohne Treffer bleiben alle
        assertEquals(4, BullBear.zurFirma(s, "XYZ", listOf("unbekannt")).size)
    }
}
