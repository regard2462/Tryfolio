package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class TradingRegressionTest {
    private val now = timestamp("2026-02-28")
    private fun timestamp(date: String) = LocalDate.parse(date).atTime(12, 0)
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun quote(price: Double, at: Long = now) = mapOf("TEST" to Quote("TEST", price = price, prevClose = 100.0, updatedAt = at))
    private fun position(qty: Double = 10.0) = Position("TEST", "Test", "TEST", qty, 100.0, now)
    private fun order(side: Side, limit: Double = 100.0) = Order(
        "order", "TEST", "Test", "TEST", side, OrderType.LIMIT, 1.0, limit, createdAt = now,
    )
    private fun buy(account: Account, qty: Double, price: Double) =
        Trading.buy(account, "TEST", "Test", "TEST", qty, price, now = now)

    @Test fun buyingChargesFeeExactlyOnce() {
        val result = buy(Account(cash = 1000.0), 2.0, 100.0)
        assertTrue(result.ok)
        assertEquals(799.0, result.account.cash, 1e-9)
        assertEquals(-201.0, result.account.transactions.sumOf { it.amount }, 1e-9)
    }

    @Test fun buyingRejectsNonFiniteQuantityAndPrice() {
        val account = Account(cash = 1000.0)
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0, 0.0)) {
            assertFalse(buy(account, bad, 100.0).ok)
            assertFalse(buy(account, 1.0, bad).ok)
        }
    }

    @Test fun buyingRejectsOverflow() {
        assertFalse(buy(Account(cash = Double.MAX_VALUE), Double.MAX_VALUE, 2.0).ok)
    }

    @Test fun sellingWithoutQuoteCannotDestroyPosition() {
        val account = Account(cash = 1000.0, positions = listOf(position()))
        for (bad in listOf(0.0, Double.NaN, Double.POSITIVE_INFINITY, -1.0)) {
            val result = Trading.sell(account, "TEST", 1.0, bad)
            assertFalse(result.ok)
            assertSame(account, result.account)
        }
    }

    @Test fun tinySaleCannotCreateNegativeCashThroughFee() {
        val account = Account(positions = listOf(position()))
        assertFalse(Trading.sell(account, "TEST", 0.001, 100.0).ok)
    }

    @Test fun wholeFractionalPositionCanBeSoldWithoutRoundingDust() {
        val account = Account(cash = 10.0, positions = listOf(position(0.123456789123)))
        val result = Trading.sell(account, "TEST", account.positions.single().qty, 100.0)
        assertTrue(result.ok)
        assertTrue(result.account.positions.isEmpty())
    }

    @Test fun limitBuyWaitsUntilAskIsWithinLimit() {
        val account = Account(cash = 1000.0, orders = listOf(order(Side.BUY)))
        assertEquals(OrderStatus.OPEN, Trading.runOrders(account, quote(100.0), now, respectHours = false).orders.single().status)
        val result = Trading.runOrders(account, quote(99.0), now, respectHours = false)
        assertEquals(OrderStatus.FILLED, result.orders.single().status)
        assertTrue(result.orders.single().executedPrice <= 100.0)
    }

    @Test fun limitSellWaitsUntilBidIsWithinLimit() {
        val account = Account(cash = 1000.0, positions = listOf(position()), orders = listOf(order(Side.SELL)))
        assertEquals(OrderStatus.OPEN, Trading.runOrders(account, quote(100.0), now, respectHours = false).orders.single().status)
        val result = Trading.runOrders(account, quote(101.0), now, respectHours = false)
        assertEquals(OrderStatus.FILLED, result.orders.single().status)
        assertTrue(result.orders.single().executedPrice >= 100.0)
    }

    @Test fun malformedOrderIsRejectedBeforeStorage() {
        val account = Account()
        assertSame(account, Trading.placeOrder(account, order(Side.BUY, Double.NaN)))
        assertSame(account, Trading.placeOrder(account, order(Side.BUY).copy(qty = -1.0)))
    }

    @Test fun duplicateOrderIdIsNotStoredTwice() {
        val o = order(Side.BUY)
        val account = Trading.placeOrder(Account(), o)
        assertEquals(1, Trading.placeOrder(account, o).orders.size)
    }

    @Test fun snapshotGrowsDespiteFiveSecondTicks() {
        var account = Account(cash = 50.0, deposited = 100.0)
        for (seconds in 0..180 step 5) {
            account = Trading.snapshot(account, 100.0 + seconds, now + seconds * 1000L)
        }
        assertEquals(4, account.history.size)
        assertEquals(now, account.history.first().ts)
        assertEquals(180.0, account.history.last().profit!!, 1e-9)
        assertEquals(230.0, account.history.last().securities!!, 1e-9)
    }

    @Test fun zeroPortfolioCanBeRecorded() {
        assertEquals(1, Trading.snapshot(Account(), 0.0, now).history.size)
    }

    @Test fun depositDoesNotIncreaseProfit() {
        val account = Account(cash = 100.0, deposited = 100.0)
        val deposited = Trading.deposit(account, 500.0, now)
        val sampled = Trading.snapshot(deposited, deposited.cash, now)
        assertEquals(0.0, sampled.history.single().profit!!, 1e-9)
        assertEquals(0.0, sampled.history.single().securities!!, 1e-9)
    }

    @Test fun missingQuotesUseCostBasisInsteadOfAFalseTotalLoss() {
        val result = Trading.value(Account(positions = listOf(position())), emptyMap(), now)
        assertEquals(1000.0, result.securities, 1e-9)
        assertEquals(0.0, result.positions.single().profit, 1e-9)
    }

    @Test fun failedSavingsPlanCanRetryAfterDeposit() {
        val plan = SavingsPlan("plan", "TEST", "Test", 100.0, 1)
        val failed = Trading.runPlans(Account(plans = listOf(plan)), quote(100.0), now)
        assertEquals(0L, failed.plans.single().lastRun)
        val result = Trading.runPlans(Trading.deposit(failed, 100.0, now), quote(100.0), now)
        assertEquals(now, result.plans.single().lastRun)
        assertEquals(0.0, result.cash, 1e-8)
        assertEquals(0, result.transactions.count { it.type == TxType.FEE })
    }

    @Test fun nextCalendarMonthRunsEvenAcrossShortFebruary() {
        val plan = SavingsPlan("plan", "TEST", "Test", 100.0, 1, lastRun = timestamp("2026-02-01"))
        val march = timestamp("2026-03-01")
        val account = Account(cash = 500.0, plans = listOf(plan))
        assertEquals(march, Trading.runPlans(account, quote(100.0, march), march).plans.single().lastRun)
    }

    @Test fun savingsPlanDoesNotRunTwiceInJanuary() {
        val jan31 = timestamp("2026-01-31")
        val plan = SavingsPlan("plan", "TEST", "Test", 100.0, 1, lastRun = timestamp("2026-01-01"))
        val account = Account(cash = 500.0, plans = listOf(plan))
        assertEquals(500.0, Trading.runPlans(account, quote(100.0, jan31), jan31).cash, 1e-9)
    }

    @Test fun planFor31stRunsAtEndOfFebruary() {
        val plan = SavingsPlan("plan", "TEST", "Test", 100.0, 31)
        val result = Trading.runPlans(Account(cash = 500.0, plans = listOf(plan)), quote(100.0), now)
        assertEquals(now, result.plans.single().lastRun)
    }

    @Test fun invalidDepositsDoNotCorruptAccount() {
        val account = Account(cash = 10.0)
        for (amount in listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 0.0)) {
            assertSame(account, Trading.deposit(account, amount))
        }
    }

    @Test fun expiredOptionPaysIntrinsicValueAtExactExpiry() {
        val derivative = Derivative("call", "TEST", "Test", DerivativeKind.CALL, strike = 90.0, ratio = 10.0, expiry = now)
        val p = position(2.0).copy(key = "call", derivative = derivative)
        val result = Trading.maintain(Account(positions = listOf(p)), quote(100.0), now)
        assertTrue(result.positions.isEmpty())
        assertEquals(2.0, result.cash, 1e-9)
        assertEquals(TxType.EXPIRY, result.transactions.single().type)
    }

    @Test fun legacySnapshotJsonRemainsReadable() {
        val account = Json.decodeFromString<Account>("""{"cash":10.0,"history":[{"ts":1000,"value":10.0}]}""")
        assertNull(account.history.single().securities)
        assertEquals(10.0, account.cash, 1e-9)
    }

    @Test fun idsAreUniqueAcrossThreads() {
        val ids = ConcurrentHashMap.newKeySet<String>()
        val pool = Executors.newFixedThreadPool(4)
        try {
            val tasks = (1..4).map { pool.submit { repeat(1000) { ids.add(Trading.newId("test", now)) } } }
            tasks.forEach { it.get() }
            assertEquals(4000, ids.size)
        } finally { pool.shutdownNow() }
    }

    @Test fun newAccountResetsMaintenanceMarkers() {
        val account = Trading.fresh(now)
        assertEquals(now / 86_400_000L, account.lastMaintainDay)
        assertEquals(now / 86_400_000L, account.lastInterestDay)
    }
    @Test fun staleQuotesDoNotExecutePendingOrders() {
        val account = Account(cash = 1000.0, orders = listOf(order(Side.BUY)))
        val result = Trading.runOrders(account, quote(99.0, now - 600_000L), now, respectHours = false)
        assertEquals(OrderStatus.OPEN, result.orders.single().status)
        assertEquals(1000.0, result.cash, 1e-9)
    }

    @Test fun historyCompactionKeepsOldestAndNewestSamples() {
        var account = Account()
        for (minute in 0..Trading.MAX_HISTORY) {
            account = Trading.snapshot(account, 100.0, now + minute * 60_000L)
        }
        assertTrue(account.history.size <= Trading.MAX_HISTORY)
        assertEquals(now, account.history.first().ts)
        assertEquals(now + Trading.MAX_HISTORY * 60_000L, account.history.last().ts)
    }

    @Test fun knockoutCancelsPendingOrderWithoutAnExistingPosition() {
        val d = Derivative("ko", "TEST", "Test", DerivativeKind.KO_LONG, strike = 90.0, barrier = 91.0)
        val o = order(Side.BUY).copy(key = "ko", derivative = d)
        val result = Trading.runOrders(Account(cash = 1000.0, orders = listOf(o)), quote(90.0), now, respectHours = false)
        assertEquals(OrderStatus.CANCELLED, result.orders.single().status)
        assertTrue(result.positions.isEmpty())
    }

}
