package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Derivative
import de.troetenrepublik.app.data.DerivativeKind
import de.troetenrepublik.app.data.Position
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.data.Series
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.data.WatchMetric
import de.troetenrepublik.app.market.Zweitnotiz
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

// Depotfenster (werktags 8 bis 22 Uhr), Ausduennen des Tagescharts, Verlauf ausgeknockter Produkte
// bis zum Knock-out, modellierter Verlauf von Faktor-Zertifikaten, sofortiges Ausbuchen, Watchlist seit dem
// ersten Hinzufuegen und der Morgen mit einem einzigen deutschen Kurs.
class Fassung23Test {

    private val zone = ZoneId.of("Europe/Berlin")
    private fun ts(d: Int, h: Int, min: Int = 0) = LocalDateTime.of(2026, 10, d, h, min).atZone(zone).toInstant().toEpochMilli()
    private fun kerze(t: Long, close: Double) = Candle(t, close, close, close, close)

    // ------------------------------------------------------------ Depotfenster

    @Test
    fun depotWindowIsWeekdaysEightToTen() {
        // Dienstag, 6. Oktober 2026
        assertFalse(MarketHours.imDepotFenster(ts(6, 7, 59)))
        assertTrue(MarketHours.imDepotFenster(ts(6, 8)))
        assertTrue(MarketHours.imDepotFenster(ts(6, 15, 30)))
        assertTrue(MarketHours.imDepotFenster(ts(6, 21, 59)))
        assertFalse(MarketHours.imDepotFenster(ts(6, 22)))
        assertFalse(MarketHours.imDepotFenster(ts(6, 23, 30)))
        // Samstag und Sonntag
        assertFalse(MarketHours.imDepotFenster(ts(10, 12)))
        assertFalse(MarketHours.imDepotFenster(ts(11, 12)))
        val (start, ende) = MarketHours.depotFenster(ts(6, 15))
        assertEquals(ts(6, 8), start)
        assertEquals(ts(6, 22), ende)
    }

    @Test
    fun thinningKeepsLastPointOfEachFiveMinuteSlotAndTheLastPoint() {
        val t0 = ts(6, 8)
        val alle = (0..10).map { kerze(t0 + it * 2 * 60_000L, 100.0 + it) }   // 8:00 bis 8:20 alle zwei Minuten
        val raster = Ticks.ausduennen(alle, 5 * 60_000L)
        assertEquals(listOf(4, 8, 14, 18, 20).map { t0 + it * 60_000L }, raster.map { it.t })
        assertEquals(alle.last(), raster.last())
        // Kurze Reihen bleiben unveraendert
        val zwei = alle.take(2)
        assertSame(zwei, Ticks.ausduennen(zwei, 5 * 60_000L))
    }

    // ------------------------------------------------------------ Verlauf ausgeknockter Produkte

    private val ko = Derivative(
        id = "ko-1", underlying = "ALV.DE", underlyingName = "Allianz", kind = DerivativeKind.KO_LONG,
        strike = 90.0, barrier = 95.0, ratio = 1.0, createdAt = 0L, ccy = "EUR",
    )

    @Test
    fun knockedOutProductShowsTheDayUntilTheKnockOutThenTheResidual() {
        val kurse = listOf(kerze(ts(7, 9), 100.0), kerze(ts(7, 11), 97.0), kerze(ts(7, 13, 14), 94.0), kerze(ts(7, 15), 96.0))
        val tot = ko.copy(knockedOutAt = ts(7, 13, 14))
        val verlauf = DerivatVerlauf.bewertet(tot, kurse, 1.0, Pricing.RATE)
        assertEquals(listOf(ts(7, 9), ts(7, 11), ts(7, 13, 14)), verlauf.map { it.t })
        assertEquals(10.0, verlauf[0].close, 1e-9)
        assertEquals(7.0, verlauf[1].close, 1e-9)
        assertEquals(Pricing.KO_RESIDUAL, verlauf.last().close, 1e-12)
        // Lebendes Produkt: jeder Kurs bewertet, am Kurs unter der Schwelle der Restwert
        val lebend = DerivatVerlauf.bewertet(ko, kurse, 1.0, Pricing.RATE)
        assertEquals(4, lebend.size)
        assertEquals(Pricing.KO_RESIDUAL, lebend[2].close, 1e-12)
        assertEquals(6.0, lebend[3].close, 1e-9)
        // Knock-out vor dem Zeitraum: nichts zu zeigen
        assertTrue(DerivatVerlauf.bewertet(ko.copy(knockedOutAt = ts(6, 12)), kurse, 1.0, Pricing.RATE).isEmpty())
    }

    // ------------------------------------------------------------ Faktor-Zertifikat

    private val faktor3 = Derivative(
        id = "f3", underlying = "AAPL", underlyingName = "Apple", kind = DerivativeKind.FACTOR_LONG, factor = 3.0, createdAt = 0L,
    )

    @Test
    fun factorPathAppliesDailyLeverageBackwardsFromTodaysPrice() {
        // Tag 1: 100, 100; Tag 2: 105, 110; Tag 3: 99. Heutiger Preis 7.
        val kurse = listOf(
            kerze(ts(5, 10), 100.0), kerze(ts(5, 16), 100.0),
            kerze(ts(6, 10), 105.0), kerze(ts(6, 16), 110.0),
            kerze(ts(7, 10), 99.0),
        )
        val v = DerivatVerlauf.faktor(faktor3, kurse, heute = 7.0, zone = zone, rate = 0.0)
        assertEquals(kurse.map { it.t }, v.map { it.t })
        // Tag 3: 1 + 3 * (99/110 - 1) = 0,7 -> Stand Ende Tag 2 = 10; Tag 2: 1 + 3 * (110/100 - 1) = 1,3 -> Ende Tag 1 = 7,6923
        assertEquals(7.0, v[4].close, 1e-9)
        assertEquals(10.0, v[3].close, 1e-9)
        assertEquals(10.0 / 1.3 * 1.15, v[2].close, 1e-9)     // 105: Rendite 5 %, mal 3
        assertEquals(10.0 / 1.3, v[1].close, 1e-9)
        assertEquals(10.0 / 1.3, v[0].close, 1e-9)            // Tag 1 ohne Vortagesschluss: ab dem ersten Kurs
        // Mit Finanzierungskosten liegen die frueheren Staende hoeher (die Kosten gehen taeglich ab)
        val mitKosten = DerivatVerlauf.faktor(faktor3, kurse, heute = 7.0, zone = zone, rate = 0.025)
        assertEquals(7.0, mitKosten[4].close, 1e-9)
        assertTrue(mitKosten[0].close > v[0].close)
        // Tageschart mit Vortagesschluss: die Rendite des Tages zaehlt ab dem Vortagesschluss
        val tag = DerivatVerlauf.faktor(faktor3, listOf(kerze(ts(7, 9), 102.0), kerze(ts(7, 12), 104.0)), heute = 1.12, zone = zone, vortagesschluss = 100.0, rate = 0.0)
        assertEquals(1.12, tag[1].close, 1e-9)                // 1 + 3 * 4 % = 1,12 bei Vortagesstand 1
        assertEquals(1.06, tag[0].close, 1e-9)
        // Kein Faktorprodukt oder keine Kurse: leer
        assertTrue(DerivatVerlauf.faktor(ko, kurse, 7.0, zone).isEmpty())
        assertTrue(DerivatVerlauf.faktor(faktor3, emptyList(), 7.0, zone).isEmpty())
    }

    // ------------------------------------------------------------ Sofort ausbuchen

    @Test
    fun settleNowBooksTheResidualAndClosesThePositionAtTheKnockOut() {
        val now = ts(7, 18)
        val tot = ko.copy(knockedOutAt = ts(7, 13, 14))
        val konto = Trading.fresh(ts(1, 9), 1_000.0).let { acc ->
            acc.copy(positions = acc.positions + Position("ko-1", "Long 90", "ALV.DE", 2.0, 4.0, ts(6, 10), tot))
        }
        val quotes = mapOf("ALV.DE" to Quote("ALV.DE", price = 96.0, nativePrice = 96.0, marketState = "REGULAR", updatedAt = now, marketTime = now))
        val r = Trading.settle(konto, "ko-1", quotes, now)
        assertTrue(r.ok)
        assertTrue(r.account.positions.none { it.key == "ko-1" })
        assertEquals(1_000.0 + 2 * Pricing.KO_RESIDUAL, r.account.cash, 1e-9)
        val tx = r.account.transactions.last()
        assertEquals(TxType.KNOCKOUT, tx.type)
        assertEquals(ts(7, 13, 14), tx.closedAt)
        assertEquals(2 * Pricing.KO_RESIDUAL - 8.0, tx.realized, 1e-9)
        // Ein lebendes Produkt laesst sich nicht ausbuchen, ein fremder Schluessel auch nicht
        val lebend = konto.copy(positions = konto.positions.map { if (it.key == "ko-1") it.copy(derivative = ko) else it })
        assertEquals(Err.NOT_TRADABLE, Trading.settle(lebend, "ko-1", quotes, now).error)
        assertEquals(Err.NOT_IN_DEPOT, Trading.settle(konto, "x", quotes, now).error)
    }

    // ------------------------------------------------------------ Watchlist seit erstem Hinzufuegen

    @Test
    fun watchlistRemembersTheFirstAddition() {
        var acc = Trading.fresh(ts(1, 9))
        acc = Trading.watch(acc, "AAPL", 10.0, ts(2, 9))
        val erst = acc.watchMeta.getValue("AAPL")
        assertFalse(erst.wiederAufgenommen)
        assertEquals(ts(2, 9), erst.ersterZeitpunkt)
        acc = Trading.unwatch(acc, "AAPL")
        assertTrue("AAPL" !in acc.watchlist)
        assertTrue(acc.watchMeta.containsKey("AAPL"))
        acc = Trading.watch(acc, "AAPL", 12.0, ts(5, 9))
        val wieder = acc.watchMeta.getValue("AAPL")
        assertTrue(wieder.wiederAufgenommen)
        assertEquals(ts(5, 9), wieder.addedAt)
        assertEquals(12.0, wieder.price, 1e-9)
        assertEquals(ts(2, 9), wieder.firstAddedAt)
        assertEquals(10.0, wieder.firstPrice, 1e-9)
        // Aeltere Eintraege ohne die neuen Felder: erstes gleich letztes Hinzufuegen
        val alt = de.troetenrepublik.app.data.WatchMeta(ts(3, 9), 11.0)
        assertEquals(ts(3, 9), alt.ersterZeitpunkt)
        assertEquals(11.0, alt.ersterKurs, 1e-9)
        assertFalse(alt.wiederAufgenommen)
    }

    @Test
    fun watchMetricSkipsSinceFirstUnlessSomethingWasReAdded() {
        assertEquals(WatchMetric.TODAY_PCT, WatchMetric.SINCE_ADDED_ABS.next(false))
        assertEquals(WatchMetric.SINCE_FIRST_PCT, WatchMetric.SINCE_ADDED_ABS.next(true))
        assertEquals(WatchMetric.SINCE_FIRST_ABS, WatchMetric.SINCE_FIRST_PCT.next(true))
        assertEquals(WatchMetric.TODAY_PCT, WatchMetric.SINCE_FIRST_ABS.next(true))
        assertTrue(WatchMetric.SINCE_FIRST_ABS.isAbsolute)
        assertTrue(WatchMetric.SINCE_FIRST_PCT.isSinceFirst)
        assertFalse(WatchMetric.SINCE_ADDED_PCT.isSinceFirst)
    }

    // ------------------------------------------------------------ Morgen mit einem einzigen deutschen Kurs

    @Test
    fun aSingleGermanMorningPriceKeepsYesterdaysChart() {
        val heimat = Series(listOf(kerze(ts(6, 9), 1.0), kerze(ts(6, 12), 1.1), kerze(ts(6, 16, 58), 1.2)), ts(6, 9), ts(6, 17))
        val einer = Series(listOf(kerze(ts(7, 8, 5), 1.25)), ts(7, 8), ts(7, 22))
        assertSame(heimat, Zweitnotiz.mische(heimat, einer, zone))
        val zwei = Series(listOf(kerze(ts(7, 8, 5), 1.25), kerze(ts(7, 8, 31), 1.26)), ts(7, 8), ts(7, 22))
        assertEquals(zwei.candles, Zweitnotiz.mische(heimat, zwei, zone).candles)
    }
}
