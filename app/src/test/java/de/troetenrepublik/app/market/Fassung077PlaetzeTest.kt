package de.troetenrepublik.app.market

import de.troetenrepublik.app.data.Candle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Tageschart (0.77): Luecken an der Zweitnotiz fuellen die Kurse der anderen deutschen Handelsplaetze.
class Fassung077PlaetzeTest {
    private fun k(minute: Int, kurs: Double) = Candle(minute * 60_000L, kurs, kurs, kurs, kurs)

    @Test
    fun schwesternAnDeutschenPlaetzen() {
        assertEquals(listOf("MS51.DE", "MS51.SG", "MS51.MU", "MS51.DU", "MS51.HM", "MS51.HA", "MS51.BE"), Zweitnotiz.schwestern("MS51.F"))
        assertTrue(Zweitnotiz.schwestern("SMCI").isEmpty())
        assertTrue(Zweitnotiz.schwestern(".F").isEmpty())
    }

    @Test
    fun plaetzeFuellenDieLuecke() {
        // Frankfurt: zwei Umsaetze in zwei Stunden; Stuttgart dazwischen dicht
        val frankfurt = listOf(k(486, 25.80), k(592, 26.38))
        val stuttgart = listOf(k(482, 25.80), k(510, 25.84), k(520, 26.00), k(544, 26.10), k(554, 26.38), k(592, 26.40))
        val r = Zweitnotiz.vereine(frankfurt, listOf(stuttgart))
        assertEquals(7, r.size)
        assertTrue(r.zipWithNext().all { (a, b) -> a.t < b.t })
        // Vorher lagen 106 Minuten zwischen zwei Kursen, jetzt hoechstens 38
        assertTrue(r.zipWithNext().all { (a, b) -> b.t - a.t <= 38 * 60_000L })
        // Gleicher Zeitschritt an zwei Plaetzen: mittlerer Kurs
        assertEquals(26.39, r.last().close, 1e-9)
    }

    @Test
    fun andereAktieBleibtWeg() {
        val haupt = listOf(k(486, 25.80), k(592, 26.38))
        val fremd = listOf(k(500, 5.10), k(520, 5.12))
        assertEquals(haupt.map { it.close }, Zweitnotiz.vereine(haupt, listOf(fremd, emptyList())).map { it.close })
        // Ohne Kurse am Hauptplatz zaehlt der Platz mit den meisten Kursen
        val a = listOf(k(500, 10.0), k(510, 10.1), k(520, 10.2))
        assertEquals(3, Zweitnotiz.vereine(emptyList(), listOf(a, fremd)).size)
    }
}
