package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

// Fassung 5.94: Korrekturen an Indikatoren, Optionen und Handelszeiten aus der Fachpruefung.
class Fassung594IndikatorTest {

    private fun utc(d: String, h: Int, m: Int = 0, zone: String = "UTC"): java.time.Instant =
        LocalDate.parse(d).atTime(h, m).atZone(ZoneId.of(zone)).toInstant()

    @Test fun lsExchangeFeiertage() {
        assertTrue(MarketHours.lsFeiertag(LocalDate.parse("2026-04-03")))
        assertTrue(MarketHours.lsFeiertag(LocalDate.parse("2026-04-06")))
        assertTrue(MarketHours.lsFeiertag(LocalDate.parse("2026-05-01")))
        assertTrue(MarketHours.lsFeiertag(LocalDate.parse("2026-12-24")))
        assertTrue(MarketHours.lsFeiertag(LocalDate.parse("2026-12-31")))
        assertFalse(MarketHours.lsFeiertag(LocalDate.parse("2026-12-30")))
        assertFalse(MarketHours.lsFeiertag(LocalDate.parse("2026-10-03")))
    }

    @Test fun usBoersenSchliessenAnThanksgiving() {
        val z = MarketHours.zeitenFuer("US", MarketHours.LAND, 0, 0)
        assertTrue(z.feiertag(LocalDate.parse("2026-11-26")))
        assertFalse(z.feiertag(LocalDate.parse("2026-11-27")))
    }

    @Test fun b3UndMexikoFolgenDerUsSommerzeit() {
        val br = Regionen.land("BR")
        val mx = Regionen.land("MX")
        // Januar: US-Winterzeit
        assertEquals(LocalTime.of(18, 0), Regionen.sitzung(br, utc("2026-01-15", 12)).second)
        assertEquals(LocalTime.of(8, 30), Regionen.sitzung(mx, utc("2026-01-15", 12)).first)
        // Juli: US-Sommerzeit
        assertEquals(LocalTime.of(17, 0), Regionen.sitzung(br, utc("2026-07-15", 12)).second)
        assertEquals(LocalTime.of(7, 30), Regionen.sitzung(mx, utc("2026-07-15", 12)).first)
        assertEquals(LocalTime.of(14, 0), Regionen.sitzung(mx, utc("2026-07-15", 12)).second)
    }

    @Test fun rsiOhneKursaenderungSteht50() {
        val r = Indikatoren.rsi(List(40) { 100.0 })
        assertEquals(50.0, r.last()!!, 1e-9)
    }

    @Test fun sarSpringtBeiUmkehrUeberDieLetztenHochs() {
        // 30 Tage steigend, dann ein Einbruch
        val hoch = (0 until 30).map { 100.0 + it } + listOf(110.0, 105.0, 100.0)
        val tief = hoch.map { it - 2.0 }
        val s = Indikatoren.sar(hoch, tief)
        val i = s.aufwaerts.indexOfFirst { !it }.let { k -> (k until hoch.size).first { j -> j > 0 && s.aufwaerts[j - 1] && !s.aufwaerts[j] } }
        assertTrue(s.wert[i]!! >= maxOf(hoch[i], hoch[i - 1]) - 1e-9)
    }

    @Test fun kryptoMit365Tagen() {
        val kurse = (0 until 120).map { 100.0 * exp(0.02 * sin(it * 1.3)) }
        val aktie = Pricing.historicalVol(kurse)
        val krypto = Pricing.historicalVol(kurse, tageProJahr = 365.0)
        assertEquals(sqrt(365.0 / 252.0), krypto / aktie, 1e-9)
    }

    @Test fun signaleZaehlenNurOhneUeberlappung() {
        val welle = (0 until 1200).map { 100.0 * exp(0.3 * sin(2 * PI * it / 30.0)) }
        val hoch = welle.map { it * 1.002 }
        val tief = welle.map { it * 0.998 }
        val umsatz = List(welle.size) { 0.0 }
        val tage = welle.indices.map { 1_500_000_000_000L + it * 86_400_000L }
        val roh = IndikatorTreffer.signale("rsi", welle, hoch, tief, umsatz)
        val b = IndikatorTreffer.bilanz("rsi", welle, hoch, tief, umsatz, tage)!!
        val h = IndikatorTreffer.TAGE
        var letzter = -h
        var erwartet = 0
        for (i in 0 until welle.size - h) if (roh[i] != 0 && i - letzter >= h) { erwartet++; letzter = i }
        assertEquals(erwartet, b.signale)
        assertTrue(b.signale < roh.take(welle.size - h).count { it != 0 })
    }
}
