package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.docs.Documents
import de.troetenrepublik.app.market.MarketApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

// Position importieren und Werkzeuge der Wertpapierseite.
class Features16Test {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private fun at(date: String, hour: Int = 12): Long =
        LocalDate.parse(date).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private val now = at("2026-09-23")
    private val bought = at("2024-03-12")

    private fun quote(price: Double) =
        Quote("AAPL", "Apple", price = price, prevClose = price, nativeCurrency = "EUR", nativePrice = price, updatedAt = now, marketState = "REGULAR")

    @Test fun importAddsPositionWithoutCashFlow() {
        val acc = Account(cash = 1_000.0, deposited = 1_000.0)
        val r = Trading.importPosition(acc, "AAPL", "Apple", 10.0, 150.0, bought, now)
        assertTrue(r.ok)
        val a = r.account
        assertEquals(1_000.0, a.cash, 1e-9)                 // Guthaben unveraendert
        assertEquals(1_500.0, a.transferredIn, 1e-9)        // Einstandswert
        val p = a.positions.single()
        assertEquals(10.0, p.qty, 1e-9)
        assertEquals(150.0, p.avgPrice, 1e-9)
        assertEquals(bought, p.openedAt)
        assertEquals(now, p.importedAt)
        val tx = a.transactions.single()
        assertEquals(TxType.BUY, tx.type)
        assertEquals("TRANSFER_IN", tx.tag)
        assertEquals("IMPORT", tx.via)
        assertEquals(0.0, tx.amount, 1e-9)
        assertEquals(bought, tx.ts)                         // Buchung traegt das Kaufdatum
    }

    @Test fun importResultIsMeasuredAgainstCostBasis() {
        val a = Trading.importPosition(Account(cash = 1_000.0, deposited = 1_000.0), "AAPL", "Apple", 10.0, 150.0, bought, now).account
        val v = Trading.value(a, mapOf("AAPL" to quote(200.0)), now)
        assertEquals(3_000.0, v.total, 1e-6)                 // 1.000 Guthaben + 10 x 200
        assertEquals(500.0, v.profit, 1e-6)                  // Gewinn nur aus dem Kursanstieg
        assertEquals(2_500.0, v.deposited, 1e-6)
    }

    @Test fun importMergesWithExistingPosition() {
        var a = Account(cash = 10_000.0, deposited = 10_000.0)
        a = Trading.buy(a, "AAPL", "Apple", "AAPL", 10.0, 200.0, now = now, fee = 0.0).account
        a = Trading.importPosition(a, "AAPL", "Apple", 10.0, 100.0, bought, now).account
        val p = a.positions.single()
        assertEquals(20.0, p.qty, 1e-9)
        assertEquals(150.0, p.avgPrice, 1e-9)
        assertEquals(bought, p.openedAt)
    }

    @Test fun importRejectsInvalidInput() {
        val acc = Account(cash = 100.0)
        assertEquals(Err.INVALID_QTY, Trading.importPosition(acc, "AAPL", "Apple", 0.0, 150.0, bought, now).error)
        assertEquals(Err.NO_PRICE, Trading.importPosition(acc, "AAPL", "Apple", 1.0, 0.0, bought, now).error)
        assertEquals(Err.INVALID_DATE, Trading.importPosition(acc, "AAPL", "Apple", 1.0, 150.0, now + 86_400_000L, now).error)
        assertEquals(Err.MISSING_SECURITY, Trading.importPosition(acc, "", "", 1.0, 150.0, bought, now).error)
    }

    @Test fun historyStartsAtPurchaseDate() {
        val a = Trading.importPosition(Account(), "AAPL", "Apple", 10.0, 150.0, bought, now).account
        assertNull(Trading.holdingsAt(a, bought - 86_400_000L)["AAPL"])
        assertEquals(10.0, Trading.holdingsAt(a, bought + 86_400_000L)["AAPL"]!!.qty, 1e-9)
        assertEquals(bought, Trading.firstPurchase(a))
    }

    @Test fun onlyDividendsAfterImportAreBooked() {
        val a = Trading.importPosition(Account(cash = 0.0), "AAPL", "Apple", 10.0, 150.0, bought, now).account
        val before = Trading.Dividend("AAPL", at("2025-05-12"), 0.25)       // nach Kauf, aber vor dem Import
        val after = Trading.Dividend("AAPL", now + 2 * 86_400_000L, 0.25)   // nach dem Import
        val booked = Trading.bookDividends(a, listOf(before, after), now + 10 * 86_400_000L)
        val divs = booked.transactions.filter { it.type == TxType.DIVIDEND }
        assertEquals(1, divs.size)
        assertEquals(2.5, divs.single().amount + divs.single().tax, 1e-9)
    }

    @Test fun importIsNotACashFlowInStatisticsOrDocuments() {
        val a = Trading.importPosition(Account(), "AAPL", "Apple", 10.0, 150.0, now - 3_600_000L, now).account
        val tree = Statistics.tree(a.transactions, Statistics.Period.All, zone)
        assertNull(Statistics.find(tree, "INVESTMENTS"))
        assertFalse(Documents.hasDocument(a.transactions.single()))
        assertNotNull(a.transactions.single().posKey)
    }

    // ------------------------------------------------------------ Werkzeuge auf der Wertpapierseite

    private fun c(date: String, close: Double) = at(date).let { Candle(it, close, close, close, close) }

    @Test fun lumpSumInHindsight() {
        val daily = listOf(c("2025-09-01", 100.0), c("2026-03-02", 80.0), c("2026-09-22", 150.0))
        val r = Analysis.lumpSum(daily, 1_000.0, at("2025-08-30"), priceNow = 150.0, now = now)!!
        assertEquals(10.0, r.units, 1e-9)
        assertEquals(1_500.0, r.value, 1e-9)
        assertEquals(50.0, r.pct, 1e-9)
        assertEquals(-20.0, r.maxDrawdown, 1e-9)        // 100 -> 80
        assertTrue(r.perYear!! > 45.0 && r.perYear!! < 50.0)
    }

    @Test fun savingsPlanBuysOncePerMonth() {
        val daily = listOf(c("2026-06-01", 100.0), c("2026-06-15", 90.0), c("2026-07-01", 50.0), c("2026-08-03", 100.0), c("2026-09-01", 100.0))
        val r = Analysis.savingsPlan(daily, 100.0, at("2026-06-01", 0), priceNow = 100.0, now = at("2026-09-10"), zone = zone)!!
        assertEquals(4, r.executions)                   // Juni, Juli, August, September
        assertEquals(400.0, r.invested, 1e-9)
        assertEquals(5.0, r.units, 1e-9)                // 1 + 2 + 1 + 1
        assertEquals(500.0, r.value, 1e-9)
        assertEquals(80.0, r.averageCost, 1e-9)
    }

    @Test fun moneyWeightedReturn() {
        val irr = Analysis.irr(listOf(at("2025-09-23") to 1_000.0), 1_100.0, now)!!
        assertEquals(10.0, irr, 0.05)
    }

    @Test fun riskFindsDrawdownAndRecovery() {
        val prices = (0 until 30).map { 100.0 } + listOf(90.0, 70.0, 80.0, 100.0, 105.0)
        val start = at("2026-01-02")
        val daily = prices.mapIndexed { i, p -> Candle(start + i * 86_400_000L, p, p, p, p) }
        val r = Analysis.risk(daily, start)!!
        assertEquals(-30.0, r.maxDrawdown, 1e-9)
        assertEquals(daily[31].t, r.troughAt)
        assertEquals(daily[33].t, r.recoveredAt)
        assertEquals(-22.222, r.worstDay!!.second, 0.01)
        assertTrue(r.volatility > 0)
    }

    @Test fun dividendsTrailingAndYearly() {
        val ev = listOf(
            MarketApi.DividendEvent(at("2024-05-10"), 0.50), MarketApi.DividendEvent(at("2025-05-10"), 0.55),
            MarketApi.DividendEvent(at("2025-11-10"), 0.55), MarketApi.DividendEvent(at("2026-05-10"), 0.60),
        )
        val d = Analysis.dividends(ev, now, zone)
        assertEquals(1.15, d.trailing, 1e-9)            // 11/2025 und 05/2026
        assertEquals(2, d.payments)
        assertEquals(listOf(2024 to 0.50, 2025 to 1.10, 2026 to 0.60), d.byYear)
        assertEquals(120.0, d.growth!!, 1e-6)           // 0,50 -> 1,10 in einem Jahr
        assertEquals(26.375, Analysis.taxOn(200.0, 100.0), 1e-9)
    }

    @Test fun fairValueAndPositionSize() {
        val f = Analysis.fairValue(eps = 10.0, price = 150.0, growth = 10.0, years = 5, exitPe = 15.0, required = 8.0)!!
        assertEquals(15.0, f.peNow, 1e-9)
        assertEquals(16.1051, f.epsFuture, 1e-4)
        assertEquals(164.41, f.fair, 0.05)
        assertTrue(f.premium < 0)                        // Kurs liegt unter dem fairen Wert
        val p = Analysis.positionSize(depot = 10_000.0, price = 100.0, riskPct = 1.0, stopPct = 10.0, wholeUnits = true)!!
        assertEquals(90.0, p.stopPrice, 1e-9)
        assertEquals(100.0, p.maxLoss, 1e-9)
        assertEquals(10.0, p.units, 1e-9)
        assertEquals(10.0, p.shareOfDepot, 1e-9)
    }

    @Test fun freshAccountHoldsOnlyTheStartingBalance() {
        val a = Trading.fresh(now = now, capital = 10_000.0)
        assertTrue(a.positions.isEmpty())
        assertTrue(a.watchlist.isEmpty())                 // neue Installation: keine vorbelegte Watchlist
        assertTrue(a.plans.isEmpty() && a.orders.isEmpty() && a.alerts.isEmpty())
        assertEquals(10_000.0, a.cash, 1e-9)
        assertEquals(10_000.0, a.deposited, 1e-9)
        assertEquals(listOf(TxType.DEPOSIT), a.transactions.map { it.type })
    }
}
