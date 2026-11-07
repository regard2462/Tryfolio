package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Fruehindikator.Phase as P
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.YearMonth

// Konjunkturphase aus IWF-Wachstum (Trend = Median der letzten zehn Jahre ohne 2020), Vorrang des OECD-Fruehindikators,
// Anteil der Laender im Abschwung, Handelszeiten mit Freitag und US-Sommerzeit.
class KonjunkturphaseTest {
    private fun daten(vararg laender: Pair<String, Map<Int, Double>>, bip: Map<String, Double> = emptyMap()) = Imf.Datensatz(
        0L, mapOf(Imf.WACHSTUM to laender.toMap(), Imf.BIP to bip.mapValues { mapOf(2026 to it.value) }),
    )

    private val basis = mapOf(2020 to -8.0, 2021 to 6.0, 2022 to 3.0, 2023 to 2.0, 2024 to 2.0, 2025 to 2.0)

    @Test
    fun trendIstMedianOhne2020() {
        val d = daten("AAA" to basis)
        // 2021 bis 2025: 6, 3, 2, 2, 2 -> Median 2, 2020 (-8) bleibt draussen
        assertEquals(2.0, Konjunkturphase.trend(d, "AAA", 2026)!!, 1e-9)
        assertNull(Konjunkturphase.trend(daten("AAA" to mapOf(2024 to 2.0, 2025 to 2.0)), "AAA", 2026))
    }

    @Test
    fun langesFensterNimmtNurZehnJahre() {
        // 2000 bis 2025 vorhanden: nur 2016 bis 2025 zaehlen (ohne 2020), 2010 mit 9.0 liegt davor
        val reihe = (2000..2025).associateWith { if (it < 2016) 9.0 else if (it == 2020) -8.0 else 2.0 } + mapOf(2026 to 2.5, 2027 to 3.0)
        val d = daten("AAA" to reihe)
        assertEquals(2.0, Konjunkturphase.trend(d, "AAA", 2026)!!, 1e-9)
        assertEquals(P.AUFSCHWUNG, Konjunkturphase.ausImf(d, "AAA", 2026))
        // fuer ein altes Jahr zaehlt dessen eigenes Fenster
        assertEquals(9.0, Konjunkturphase.trend(d, "AAA", 2012)!!, 1e-9)
    }

    @Test
    fun vierPhasenAusImf() {
        assertEquals(P.ABSCHWAECHUNG, Konjunkturphase.ausImf(daten("AAA" to basis + mapOf(2026 to 3.0, 2027 to 2.5)), "AAA", 2026))
        assertEquals(P.AUFSCHWUNG, Konjunkturphase.ausImf(daten("AAA" to basis + mapOf(2026 to 3.0, 2027 to 3.5)), "AAA", 2026))
        assertEquals(P.ERHOLUNG, Konjunkturphase.ausImf(daten("AAA" to basis + mapOf(2026 to 1.0, 2027 to 1.5)), "AAA", 2026))
        assertEquals(P.ABSCHWUNG, Konjunkturphase.ausImf(daten("AAA" to basis + mapOf(2026 to -1.0, 2027 to -2.0)), "AAA", 2026))
        // ohne Prognose zaehlt das Vorjahr: 1.0 nach 2.0 faellt
        assertEquals(P.ABSCHWUNG, Konjunkturphase.ausImf(daten("AAA" to basis + mapOf(2026 to 1.0)), "AAA", 2026))
        assertNull(Konjunkturphase.ausImf(daten("AAA" to basis), "AAA", 2026))
        assertNull(Konjunkturphase.ausImf(null, "AAA", 2026))
    }

    @Test
    fun fruehindikatorHatVorrang() {
        val imf = daten("DEU" to basis + mapOf(2026 to 3.0, 2027 to 3.5), "XXX" to basis + mapOf(2026 to 1.0, 2027 to 1.5))
        val cli = Fruehindikator.Datensatz(0, mapOf("DEU" to listOf(Fruehindikator.Punkt("2026-08", 99.8), Fruehindikator.Punkt("2026-09", 99.5))))
        val ym = YearMonth.of(2026, 10)
        val de = Konjunkturphase.von(cli, imf, "DEU", ym, 2026)!!
        assertTrue(de.ausCli)
        assertEquals(P.ABSCHWUNG, de.phase)
        assertEquals("2026-09", de.monat)
        val x = Konjunkturphase.von(cli, imf, "XXX", ym, 2026)!!
        assertEquals(false, x.ausCli)
        assertEquals(P.ERHOLUNG, x.phase)
        assertEquals(2026, x.jahr)
        assertNull(Konjunkturphase.von(cli, imf, "YYY", ym, 2026))
    }

    @Test
    fun weltAnteilImAbschwung() {
        val imf = daten(
            "AAA" to basis + mapOf(2026 to -1.0, 2027 to -2.0),
            "BBB" to basis + mapOf(2026 to 3.0, 2027 to 3.5),
            "CCC" to mapOf(2025 to 2.0),
            bip = mapOf("AAA" to 100.0, "BBB" to 300.0, "CCC" to 50.0),
        )
        val w = Konjunkturphase.welt(null, imf, YearMonth.of(2026, 10), 2026)
        assertEquals(1, w.abschwung)
        assertEquals(2, w.mitPhase)
        assertEquals(0.25, w.bipAnteil!!, 1e-9)
        assertEquals(0, Konjunkturphase.welt(null, null, YearMonth.of(2026, 10), 2026).mitPhase)
    }

    @Test
    fun handelszeitenMitFreitagUndUsSommerzeit() {
        // B3 im Winter bis 17:55, im Sommer bis 16:55 (Ortszeit)
        assertEquals(java.time.LocalTime.of(17, 55), Boersenzeiten.status("BR", Instant.parse("2026-11-04T14:00:00Z"))!!.wechsel.toLocalTime())
        assertEquals(java.time.LocalTime.of(16, 55), Boersenzeiten.status("BR", Instant.parse("2026-07-08T14:00:00Z"))!!.wechsel.toLocalTime())
        // Tel Aviv: Montag bis Donnerstag 17:25, freitags 13:50
        assertEquals(java.time.LocalTime.of(17, 25), Boersenzeiten.status("IL", Instant.parse("2026-11-04T09:00:00Z"))!!.wechsel.toLocalTime())
        assertEquals(java.time.LocalTime.of(13, 50), Boersenzeiten.status("IL", Instant.parse("2026-11-06T09:00:00Z"))!!.wechsel.toLocalTime())
        // Indonesien freitags mit frueherer Pause
        assertEquals(java.time.LocalTime.of(11, 30), Boersenzeiten.status("ID", Instant.parse("2026-11-06T03:00:00Z"))!!.wechsel.toLocalTime())
        assertTrue(Boersenzeiten.NACH_LAND.size >= 70)
    }

    @Test
    fun indexTabelleEinmaligUndMitEtfErsatz() {
        val alle = de.troetenrepublik.app.market.GlobusIndizes.NACH_LAND
        assertEquals(alle.size, alle.values.map { it.symbol }.toSet().size)
        assertTrue(alle.size >= 49)
        assertEquals(false, alle["DE"]!!.etf)
        assertEquals(true, alle["CL"]!!.etf)
        assertEquals(alle.values.map { it.symbol }, de.troetenrepublik.app.market.GlobusIndizes.SYMBOLE)
    }
}
