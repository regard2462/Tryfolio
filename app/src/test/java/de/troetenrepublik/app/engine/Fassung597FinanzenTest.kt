package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.MarketApi
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Kompakte Dividende unter Finanzen (5.97)
class Fassung597FinanzenTest {

    private val zone = ZoneOffset.UTC
    // Feste Zeit: 2026-11-04 12:00 UTC
    private val now = LocalDate.of(2026, 11, 4).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    // Zahlung mit Ex-Tag am gegebenen Tag, 00:00 UTC
    private fun ex(tag: LocalDate, betrag: Double = 1.0) =
        MarketApi.DividendEvent(tag.atStartOfDay(zone).toInstant().toEpochMilli(), betrag)

    private fun werte(
        events: List<MarketApi.DividendEvent>, renditeProfil: Double = 0.0, quoteProfil: Double = 0.0,
        exTag: Long = 0L, zahltag: Long = 0L, kursEuro: Double = 0.0,
    ) = DividendenUeberblick.werte(events, renditeProfil, quoteProfil, exTag, zahltag, kursEuro, now, zone)

    @Test fun ohneDividendeNull() {
        assertNull(werte(emptyList()))
    }

    @Test fun renditeAusProfil() {
        assertEquals(3.1, werte(emptyList(), renditeProfil = 3.1)!!.rendite!!, 1e-9)
    }

    @Test fun renditeAusKurs() {
        val ev = listOf(
            ex(LocalDate.of(2026, 2, 10), 0.5),
            ex(LocalDate.of(2026, 5, 10), 0.5),
            ex(LocalDate.of(2026, 8, 10), 0.5),
            ex(LocalDate.of(2026, 11, 2), 0.5),
        )
        val w = werte(ev, kursEuro = 100.0)!!
        assertEquals(2.0, w.jeAktieEuro!!, 1e-9)
        assertEquals(2.0, w.rendite!!, 1e-9)
    }

    @Test fun jahreOhneLaufendes() {
        val ev = (2016..2026).map { ex(LocalDate.of(it, 6, 15)) }
        val w = werte(ev)!!
        assertTrue(w.jahre.none { it.first == 2026 })
        assertEquals(8, w.jahre.size)
        assertEquals(2025, w.jahre.last().first)
    }

    @Test fun vergangenerExTagNichtNaechster() {
        val ev = listOf(ex(LocalDate.of(2026, 6, 15)))
        val exTag = now + 10 * 86_400_000L
        assertNull(werte(ev, exTag = now - 10 * 86_400_000L)!!.naechsterExTag)
        assertEquals(exTag, werte(ev, exTag = exTag)!!.naechsterExTag)
    }

    @Test fun quoteNurPositiv() {
        val ev = listOf(ex(LocalDate.of(2026, 6, 15)))
        assertNull(werte(ev, kursEuro = 100.0)!!.quote)
        assertEquals(45.0, werte(ev, kursEuro = 100.0, quoteProfil = 45.0)!!.quote!!, 1e-9)
    }

    @Test fun jeAktieAusProfilOhneHistorie() {
        val w = DividendenUeberblick.werte(emptyList(), 2.5, 0.0, 0L, 0L, 80.0, now, zone, jeAktieProfilEuro = 2.0)!!
        assertEquals(2.0, w.jeAktieEuro!!, 1e-9)
        assertEquals(2.5, w.rendite!!, 1e-9)
    }

    @Test fun jahresErtrag() {
        assertEquals(24.0, DividendenUeberblick.jahresErtrag(2.0, 12.0), 1e-9)
    }
}
