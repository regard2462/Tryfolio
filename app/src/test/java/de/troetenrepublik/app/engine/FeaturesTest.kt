package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.Backup
import de.troetenrepublik.app.data.Order
import de.troetenrepublik.app.data.OrderStatus
import de.troetenrepublik.app.data.OrderType
import de.troetenrepublik.app.data.PlanInterval
import de.troetenrepublik.app.data.Position
import de.troetenrepublik.app.data.PriceAlert
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.data.SavingsPlan
import de.troetenrepublik.app.data.Settings
import de.troetenrepublik.app.data.Side
import de.troetenrepublik.app.data.TaxState
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.data.Validity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class FeaturesTest {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private fun at(date: String, hour: Int = 12, minute: Int = 0): Long =
        LocalDate.parse(date).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    // Mittwoch, 4. Maerz 2026, ein gewoehnlicher Handelstag
    private val now = at("2026-03-04")
    private fun quote(price: Double, at: Long = now, symbol: String = "SAP.DE") =
        mapOf(symbol to Quote(symbol, price = price, prevClose = 100.0, updatedAt = at))
    private fun stock(qty: Double = 10.0, avg: Double = 100.0, symbol: String = "SAP.DE") =
        Position(symbol, "Test", symbol, qty, avg, at("2026-01-10"))

    // Steuern

    @Test fun gainIsTaxedAfterAllowance() {
        val r = Tax.apply(TaxState(year = 2026), 1_200.0, Tax.Kind.STOCK, allowance = 1_000.0, now = now)
        assertEquals(1_000.0, r.state.allowanceUsed, 1e-9)
        assertEquals(200.0 * Tax.RATE, r.tax, 0.005)
        assertEquals(1_200.0 - r.tax, r.net, 1e-9)
    }

    @Test fun lossFillsPotAndOffsetsLaterGain() {
        var s = Tax.apply(TaxState(year = 2026), -500.0, Tax.Kind.STOCK, 0.0, now).state
        assertEquals(500.0, s.lossPotStocks, 1e-9)
        val r = Tax.apply(s, 300.0, Tax.Kind.STOCK, 0.0, now)
        assertEquals(0.0, r.tax, 1e-9)
        assertEquals(200.0, r.state.lossPotStocks, 1e-9)
    }

    @Test fun stockLossDoesNotOffsetGeneralGain() {
        val s = Tax.apply(TaxState(year = 2026), -500.0, Tax.Kind.STOCK, 0.0, now).state
        val r = Tax.apply(s, 100.0, Tax.Kind.GENERAL, 0.0, now)
        assertEquals(100.0 * Tax.RATE, r.tax, 0.005)
        assertEquals(500.0, r.state.lossPotStocks, 1e-9)
    }

    @Test fun yearChangeResetsAllowanceButKeepsPots() {
        val old = TaxState(year = 2025, allowanceUsed = 800.0, lossPotGeneral = 120.0, taxPaid = 50.0)
        val s = Tax.forYear(old, now)
        assertEquals(2026, s.year)
        assertEquals(0.0, s.allowanceUsed, 1e-9)
        assertEquals(120.0, s.lossPotGeneral, 1e-9)
        assertEquals(0.0, s.taxPaid, 1e-9)
    }

    @Test fun sellWithholdsTaxOnRealizedGain() {
        val acc = Account(cash = 0.0, positions = listOf(stock(10.0, 100.0)), tax = TaxState(year = 2026))
        val r = Trading.sell(acc, "SAP.DE", 10.0, 150.0, now, allowance = 0.0)
        assertTrue(r.ok)
        val tx = r.account.transactions.first { it.type == TxType.SELL }
        assertEquals(500.0, tx.realized, 1e-9)
        assertEquals(500.0 * Tax.RATE, tx.tax, 0.005)
        assertEquals(1500.0 - tx.tax - Trading.FEE, r.account.cash, 1e-9)
    }

    // Auszahlung

    @Test fun withdrawRespectsReservedOrders() {
        val open = Order("o", "SAP.DE", "SAP", "SAP.DE", Side.BUY, OrderType.LIMIT, 2.0, 100.0, createdAt = now)
        val acc = Account(cash = 300.0, orders = listOf(open))
        assertFalse(Trading.withdraw(acc, 150.0, now).ok)       // 300 - (200 + 1) = 99 verfuegbar
        val r = Trading.withdraw(acc, 99.0, now)
        assertTrue(r.ok)
        assertEquals(201.0, r.account.cash, 1e-9)
        assertEquals(99.0, r.account.withdrawn, 1e-9)
        assertEquals(TxType.WITHDRAWAL, r.account.transactions.single().type)
    }

    @Test fun withdrawRejectsInvalidAmounts() {
        val acc = Account(cash = 100.0)
        for (bad in listOf(0.0, -5.0, Double.NaN, Double.POSITIVE_INFINITY, 100.01)) assertFalse(Trading.withdraw(acc, bad, now).ok)
    }

    // Zinsen

    @Test fun interestAccruesDailyAndIsCreditedWithTax() {
        var acc = Account(cash = 36_500.0, tax = TaxState(year = 2026))
        acc = Trading.accrueInterest(acc, 2.0, 10)   // 36.500 * 2 % / 365 * 10 = 20 EUR
        assertEquals(20.0, acc.interestAccrued, 1e-6)
        assertEquals(36_500.0, acc.cash, 1e-9)
        val credited = Trading.creditInterest(acc, 2.0, now, allowance = 0.0)
        val tx = credited.transactions.single()
        assertEquals(TxType.INTEREST, tx.type)
        assertEquals(20.0 * Tax.RATE, tx.tax, 0.006)
        assertEquals(36_500.0 + 20.0 - tx.tax, credited.cash, 1e-9)
        assertEquals(0.0, credited.interestAccrued, 1e-9)
    }

    // Sparplaene

    @Test fun biweeklyPlanRunsOnSecondAndSixteenth() {
        val plan = SavingsPlan("p", "SAP.DE", "SAP", 50.0, 2, interval = PlanInterval.BIWEEKLY, createdAt = at("2026-01-01"))
        assertFalse(Trading.planDue(plan, at("2026-03-01")))
        assertTrue(Trading.planDue(plan, at("2026-03-02")))
        val ran = plan.copy(lastRun = at("2026-03-02"))
        assertFalse(Trading.planDue(ran, at("2026-03-10")))
        assertTrue(Trading.planDue(ran, at("2026-03-16")))
        val ran2 = ran.copy(lastRun = at("2026-03-16"))
        assertFalse(Trading.planDue(ran2, at("2026-03-31")))
        assertTrue(Trading.planDue(ran2, at("2026-04-02")))
    }

    @Test fun weeklyPlanRunsEverySevenDays() {
        val plan = SavingsPlan("p", "SAP.DE", "SAP", 50.0, 2, interval = PlanInterval.WEEKLY, createdAt = at("2026-03-01"), lastRun = at("2026-03-02"))
        assertFalse(Trading.planDue(plan, at("2026-03-08")))
        assertTrue(Trading.planDue(plan, at("2026-03-09")))
    }

    @Test fun quarterlyPlanWaitsThreeMonths() {
        val plan = SavingsPlan("p", "SAP.DE", "SAP", 50.0, 16, interval = PlanInterval.QUARTERLY, createdAt = at("2026-01-01"), lastRun = at("2026-01-16"))
        assertFalse(Trading.planDue(plan, at("2026-03-16")))
        assertTrue(Trading.planDue(plan, at("2026-04-16")))
    }

    @Test fun pausedPlanNeverRuns() {
        val plan = SavingsPlan("p", "SAP.DE", "SAP", 50.0, 2, paused = true)
        assertFalse(Trading.planDue(plan, now))
        assertEquals(0L, Trading.nextExecution(plan, now))
    }

    @Test fun planExecutionTracksInvestedAndCounts() {
        val plan = SavingsPlan("p", "SAP.DE", "SAP", 100.0, 2, createdAt = at("2026-02-01"))
        val acc = Trading.runPlans(Account(cash = 500.0, plans = listOf(plan)), quote(100.0), now)
        val p = acc.plans.single()
        assertEquals(1, p.executions)
        assertEquals(100.0, p.invested, 1e-9)
        assertEquals("PLAN", acc.transactions.single { it.type == TxType.BUY }.via)
    }

    @Test fun failedPlanIsRecordedOncePerDay() {
        val plan = SavingsPlan("p", "SAP.DE", "SAP", 100.0, 2, createdAt = at("2026-02-01"))
        var acc = Trading.runPlans(Account(cash = 10.0, plans = listOf(plan)), quote(100.0), now)
        assertEquals(1, acc.transactions.count { it.type == TxType.PLAN })
        acc = Trading.runPlans(acc, quote(100.0), now + 3_600_000L)
        assertEquals(1, acc.transactions.count { it.type == TxType.PLAN })
    }

    // Ordergueltigkeit und Handelszeiten

    @Test fun dayOrderExpiresAtTradingClose() {
        val o = Order("o", "SAP.DE", "SAP", "SAP.DE", Side.BUY, OrderType.LIMIT, 1.0, 50.0, createdAt = now, validity = Validity.DAY)
        val acc = Trading.placeOrder(Account(cash = 100.0), o)
        val expires = acc.orders.single().expiresAt
        assertEquals(at("2026-03-04", 23, 0), expires)
        assertEquals(OrderStatus.OPEN, Trading.expireOrders(acc, expires - 1).orders.single().status)
        assertEquals(OrderStatus.EXPIRED, Trading.expireOrders(acc, expires).orders.single().status)
    }

    @Test fun gtcOrderLivesForThreeHundredSixtyDays() {
        val o = Order("o", "SAP.DE", "SAP", "SAP.DE", Side.BUY, OrderType.LIMIT, 1.0, 50.0, createdAt = now, validity = Validity.GTC)
        val acc = Trading.placeOrder(Account(cash = 100.0), o)
        assertEquals(now + 360L * 86_400_000L, acc.orders.single().expiresAt)
    }

    @Test fun ordersDoNotFillOutsideTradingHours() {
        val o = Order("o", "SAP.DE", "SAP", "SAP.DE", Side.BUY, OrderType.LIMIT, 1.0, 100.0, createdAt = now)
        val acc = Trading.placeOrder(Account(cash = 200.0), o)
        val sunday = at("2026-03-08")
        assertEquals(OrderStatus.OPEN, Trading.runOrders(acc, quote(90.0, sunday), sunday).orders.single().status)
        assertEquals(OrderStatus.FILLED, Trading.runOrders(acc, quote(90.0), now).orders.single().status)
    }

    @Test fun marketHoursFollowBerlinTime() {
        assertTrue(MarketHours.isOpen(MarketHours.STOCKS, at("2026-03-04", 7, 30)))
        assertFalse(MarketHours.isOpen(MarketHours.STOCKS, at("2026-03-04", 7, 29)))
        assertFalse(MarketHours.isOpen(MarketHours.STOCKS, at("2026-03-04", 23, 0)))
        assertFalse(MarketHours.isOpen(MarketHours.DERIVATIVES, at("2026-03-04", 22, 30)))
        assertFalse(MarketHours.isOpen(MarketHours.STOCKS, at("2026-03-07", 12, 0)))   // Samstag
        assertEquals(at("2026-03-09", 7, 30), MarketHours.nextOpen(MarketHours.STOCKS, at("2026-03-07", 12, 0)))
    }

    // Dividenden

    @Test fun dividendIsBookedOnceWithTax() {
        val acc = Account(cash = 0.0, positions = listOf(stock(10.0)), tax = TaxState(year = 2026))
        val ex = at("2026-02-20")
        val divs = listOf(Trading.Dividend("SAP.DE", ex, 2.0))
        var r = Trading.bookDividends(acc, divs, now, allowance = 0.0)
        val tx = r.transactions.single { it.type == TxType.DIVIDEND }
        assertEquals(20.0 * Tax.RATE, tx.tax, 0.006)
        assertEquals(20.0 - tx.tax, r.cash, 1e-9)
        r = Trading.bookDividends(r, divs, now + 86_400_000L)
        assertEquals(1, r.transactions.count { it.type == TxType.DIVIDEND })
    }

    @Test fun dividendBeforePurchaseOrBeforePayDateIsIgnored() {
        val acc = Account(positions = listOf(stock(10.0)), tax = TaxState(year = 2026))
        assertEquals(0, Trading.bookDividends(acc, listOf(Trading.Dividend("SAP.DE", at("2025-12-01"), 2.0)), now).transactions.size)
        assertEquals(0, Trading.bookDividends(acc, listOf(Trading.Dividend("SAP.DE", now - 86_400_000L, 2.0)), now).transactions.size)
    }

    // Preisalarme

    @Test fun alertFiresOnceAndIsRemoved() {
        val alert = PriceAlert("a", "SAP.DE", "SAP", 120.0, above = true, createdAt = now)
        val acc = Account(alerts = listOf(alert))
        val (quiet, none) = Trading.checkAlerts(acc, quote(110.0), now)
        assertTrue(none.isEmpty()); assertEquals(1, quiet.alerts.size)
        val (fired, hits) = Trading.checkAlerts(acc, quote(125.0), now)
        assertEquals(1, hits.size)
        assertTrue(fired.alerts.isEmpty())
        assertEquals(TxType.ALERT, fired.transactions.single().type)
    }

    // Export und Import

    @Test fun backupRoundTripKeepsAccountAndSettings() {
        val acc = Account(cash = 123.45, positions = listOf(stock(3.0)), alerts = listOf(PriceAlert("a", "SAP.DE", "SAP", 1.0, true)),
            plans = listOf(SavingsPlan("p", "SAP.DE", "SAP", 25.0, 16, interval = PlanInterval.WEEKLY)))
        val text = Backup.export(acc, Settings(ownerName = "Alex", allowance = 801.0, interestBps = 250), now)
        val parsed = Backup.parse(text)
        assertTrue(parsed is Backup.Outcome.Ok)
        val p = (parsed as Backup.Outcome.Ok).payload
        assertEquals(acc, p.account)
        assertEquals("Alex", p.settings.ownerName)
        assertEquals(801.0, p.settings.allowance, 1e-9)
        assertEquals(Backup.FORMAT_VERSION, p.format)
    }

    @Test fun backupRejectsForeignOrBrokenFiles() {
        assertTrue(Backup.parse("{\"app\":\"other\",\"account\":{}}") is Backup.Outcome.Error)
        assertTrue(Backup.parse("kein json") is Backup.Outcome.Error)
        assertTrue(Backup.parse("{\"app\":\"troetenrepublik\",\"format\":99,\"account\":{}}") is Backup.Outcome.Error)
    }

    @Test fun backupToleratesMissingOptionalFields() {
        val parsed = Backup.parse("{\"app\":\"troetenrepublik\",\"format\":1,\"account\":{\"cash\":10.0}}")
        assertTrue(parsed is Backup.Outcome.Ok)
        assertNotNull((parsed as Backup.Outcome.Ok).payload.account)
        // Alte Sicherungen enthalten noch ein Zeitreise-Depot, es wird ignoriert.
        val alt = Backup.parse("{\"app\":\"troetenrepublik\",\"format\":2,\"account\":{\"cash\":5.0},\"timeTravel\":{\"startedAt\":1,\"simNow\":2,\"startCapital\":3.0,\"account\":{}}}")
        assertTrue(alt is Backup.Outcome.Ok)
        assertEquals(5.0, (alt as Backup.Outcome.Ok).payload.account.cash, 1e-9)
    }
}
