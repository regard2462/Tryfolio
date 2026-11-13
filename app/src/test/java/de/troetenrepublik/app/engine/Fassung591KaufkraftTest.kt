package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

// Fassung 5.91: inflationsbereinigte Depotkurve mit HICP (EZB) und US-CPI (FRED, BLS).
class Fassung591KaufkraftTest {
    private val tag = 86_400_000L
    private fun ms(y: Int, m: Int, d: Int, h: Int = 0) = LocalDate.of(y, m, d).atTime(h, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test fun quellenLesen() {
        // Kommentar mit Kommas in Anfuehrungszeichen wie beim alten ICP-Datensatz
        val ezb = "KEY,FREQ,TIME_PERIOD,OBS_VALUE,OBS_COM\n" +
            "HICP.M.U2,M,2026-08,103.69,\n" +
            "HICP.M.U2,M,2026-09,104.31,\"Hinweis, mit Komma\"\n" +
            "HICP.M.U2,M,2026-07,103.24,\n"
        val e = Kaufkraft.ezbLesen(ezb)
        assertEquals(listOf("2026-07", "2026-08", "2026-09"), e.map { it.m })
        assertEquals(104.31, e.last().w, 1e-12)
        val fred = "observation_date,CPIAUCNS\n2026-08-01,334.980\n2026-09-01,336.940\n2026-10-01,.\n"
        assertEquals(listOf("2026-08", "2026-09"), Kaufkraft.fredLesen(fred).map { it.m })
        val bls = "{\"status\":\"REQUEST_SUCCEEDED\",\"Results\":{\"series\":[{\"seriesID\":\"CUUR0000SA0\",\"data\":[" +
            "{\"year\":\"2026\",\"period\":\"M09\",\"value\":\"336.940\"},{\"year\":\"2025\",\"period\":\"M13\",\"value\":\"320.0\"}," +
            "{\"year\":\"2026\",\"period\":\"M08\",\"value\":\"334.980\"}]}]}}"
        val b = Kaufkraft.blsLesen(bls)
        assertEquals(listOf("2026-08", "2026-09"), b.map { it.m })
        assertTrue(Kaufkraft.ezbLesen("kaputt").isEmpty())
        // Neue Werte ersetzen alte gleicher Monate
        val z = Kaufkraft.zusammen(listOf(Kaufkraft.Monat("2026-07", 1.0), Kaufkraft.Monat("2026-08", 1.0)), b)
        assertEquals(listOf(1.0, 334.98, 336.94), z.map { it.w })
    }

    @Test fun tageVerteilenUndFortschreiben() {
        val idx = Kaufkraft.Index(listOf(Kaufkraft.Monat("2026-01", 100.0), Kaufkraft.Monat("2026-02", 102.0)))
        // Januar-Mitte: 1. Januar plus 15,5 Tage; Februar-Mitte: 1. Februar plus 14 Tage
        val jan = ms(2026, 1, 1) + 31 * tag / 2
        val feb = ms(2026, 2, 1) + 14 * tag
        assertEquals(100.0, idx.wert(jan)!!, 1e-12)
        assertEquals(101.0, idx.wert((jan + feb) / 2)!!, 1e-9)
        assertEquals(102.0, idx.wert(feb)!!, 1e-12)
        // Vor der Monatsmitte im ersten Monat gilt sein Wert, vor dem ersten Monat nichts
        assertEquals(100.0, idx.wert(ms(2026, 1, 2))!!, 1e-12)
        assertNull(idx.wert(ms(2025, 12, 31)))
        // Letzter Monat fortgeschrieben
        assertEquals(102.0, idx.wert(ms(2026, 10, 26))!!, 1e-12)
    }

    @Test fun realeKurveUndSatz() {
        val idx = Kaufkraft.Index(listOf(Kaufkraft.Monat("2025-01", 100.0), Kaufkraft.Monat("2026-01", 110.0)))
        val a = ms(2025, 1, 16, 12)
        val b = ms(2026, 1, 16, 12)
        val punkte = listOf(a to 100.0, b to 121.0)
        val real = Kaufkraft.real(punkte, idx)!!
        assertEquals(100.0, real.first().second, 1e-9)
        assertEquals(110.0, real.last().second, 1e-9)
        val v = Kaufkraft.vergleich(punkte, idx)!!
        assertEquals(0.21, v.nominal, 1e-9)
        assertEquals(0.10, v.inflation, 1e-9)
        assertEquals(0.10, v.real, 1e-9)
        // Punkte vor dem Index fallen weg
        assertNull(Kaufkraft.real(listOf(ms(2020, 1, 1) to 100.0, a to 101.0), idx))
    }

    @Test fun hoechstensEinmalAmTag() {
        val jetzt = ms(2026, 10, 26, 12)
        val r = Kaufkraft.Reihe(jetzt - 3_600_000L, listOf(Kaufkraft.Monat("2026-09", 1.0)))
        assertFalse(Kaufkraft.veraltet(r, jetzt))
        assertTrue(Kaufkraft.veraltet(r.copy(stand = jetzt - tag), jetzt))
        assertTrue(Kaufkraft.veraltet(Kaufkraft.Reihe(jetzt, emptyList()), jetzt))
    }
}
