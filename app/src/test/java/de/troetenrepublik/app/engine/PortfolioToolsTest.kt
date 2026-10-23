package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

// Rechenwerk der Werkzeuge in Analytics.
class PortfolioToolsTest {

    private val day = 86_400_000L
    private val t0 = 1_700_000_000_000L

    private fun c(t: Long, close: Double) = Candle(t, close, close, close, close)

    // Tagesreihe ab start mit den Schlusskursen closes, ein Punkt je Tag.
    private fun daily(start: Long, vararg closes: Double) = closes.mapIndexed { i, v -> c(start + i * day, v) }

    // Marktvergleich

    @Test
    fun flowsComeFromTradesAtMarketValue() {
        val acc = Account(transactions = listOf(
            Transaction("1", t0, TxType.BUY, "A", amount = -1001.0, qty = 10.0, price = 100.0, symbol = "A", fee = 1.0, key = "A"),
            Transaction("2", t0 + day, TxType.BUY, "B", amount = 0.0, qty = 5.0, price = 20.0, symbol = "B", key = "B", tag = "TRANSFER_IN", via = "IMPORT"),
            Transaction("3", t0 + 2 * day, TxType.SELL, "A", amount = 540.0, qty = 5.0, price = 110.0, symbol = "A", tax = 9.0, fee = 1.0, key = "A"),
            Transaction("4", t0 + 3 * day, TxType.DIVIDEND, "A", amount = 7.36, tax = 2.64, symbol = "A", key = "A"),
            Transaction("5", t0 + 4 * day, TxType.KNOCKOUT, "KO", amount = 0.05, qty = 50.0, symbol = "X", key = "ko-1"),
            Transaction("6", t0 + 5 * day, TxType.DEPOSIT, "", amount = 5000.0),
        ))
        val f = PortfolioTools.flows(acc)
        assertEquals(4, f.size)
        assertEquals(1000.0, f[0].amount, 1e-9)          // Kurswert ohne Gebuehr
        assertEquals(100.0, f[1].amount, 1e-9)           // Import zum Einstand
        assertEquals(-550.0, f[2].amount, 1e-9)          // Verkauf zum Kurswert vor Steuer
        assertEquals(-0.05, f[3].amount, 1e-9)           // Knock-out-Rueckzahlung
        assertEquals("ko-1", f[3].key)
        assertEquals(10.0, PortfolioTools.dividendsGross(acc), 1e-9)
    }

    @Test
    fun replayBuysAndSellsTheSameAmounts() {
        val prices = daily(t0, 100.0, 120.0, 150.0, 200.0)
        val flows = listOf(PortfolioTools.Flow(t0 + 1000, 1000.0, "A"), PortfolioTools.Flow(t0 + 2 * day + 1000, -600.0, "A"))
        val r = PortfolioTools.replay(flows, prices, priceNow = 200.0)!!
        // 10 Anteile zu 100, davon 4 zu 150 verkauft, 6 bleiben und sind 200 wert
        assertEquals(1200.0, r.value, 1e-9)
        assertEquals(600.0, r.proceeds, 1e-9)
        assertEquals(1000.0, r.invested, 1e-9)
        assertEquals(1800.0, r.total, 1e-9)
    }

    @Test
    fun replaySellsAtMostEverything() {
        val prices = daily(t0, 100.0, 50.0)
        val flows = listOf(PortfolioTools.Flow(t0, 1000.0, "A"), PortfolioTools.Flow(t0 + day, -5000.0, "A"))
        val r = PortfolioTools.replay(flows, prices, priceNow = 80.0)!!
        assertEquals(0.0, r.value, 1e-9)
        assertEquals(500.0, r.proceeds, 1e-9)
    }

    @Test
    fun replayNeedsPricesFromTheFirstPurchase() {
        val prices = daily(t0 + 30 * day, 100.0, 101.0)
        assertNull(PortfolioTools.replay(listOf(PortfolioTools.Flow(t0, 1000.0, "A")), prices, 101.0))
        assertNotNull(PortfolioTools.replay(listOf(PortfolioTools.Flow(t0 + 25 * day, 1000.0, "A")), prices, 101.0))
    }

    @Test
    fun cashGrowsWithTheRate() {
        val year = (365.25 * day).toLong()
        val r = PortfolioTools.replayCash(listOf(PortfolioTools.Flow(t0, 1000.0, "A")), ratePct = 5.0, now = t0 + year)!!
        assertEquals(1050.0, r.value, 0.01)
    }

    @Test
    fun moneyWeightedReturnPerYear() {
        val year = (365.25 * day).toLong()
        val one = PortfolioTools.moneyWeighted(listOf(PortfolioTools.Flow(t0, 1000.0, "A")), 1100.0, t0 + year)!!
        assertEquals(0.10, one, 1e-6)
        // Zweite Einzahlung nach einem Jahr: 1000 x 1,1^2 + 1000 x 1,1 = 2310
        val two = PortfolioTools.moneyWeighted(
            listOf(PortfolioTools.Flow(t0, 1000.0, "A"), PortfolioTools.Flow(t0 + year, 1000.0, "A")), 2310.0, t0 + 2 * year)!!
        assertEquals(0.10, two, 1e-6)
    }

    @Test
    fun timeWeightedIgnoresPurchases() {
        // +10 %, dann Kauf ueber 100, dann wieder +10 %: zusammen +21 %
        val values = listOf(t0 to 100.0, t0 + day to 110.0, t0 + 2 * day to 231.0)
        val flows = listOf(t0 - day to 100.0, t0 + day + 1000 to 100.0)
        val twr = PortfolioTools.timeWeighted(values, flows)
        assertEquals(0.0, twr[0].second, 1e-9)
        assertEquals(10.0, twr[1].second, 1e-9)
        assertEquals(21.0, twr[2].second, 1e-9)
        // Verkauf: Wert faellt um den Erloes, die Kurve nicht
        val sold = PortfolioTools.timeWeighted(listOf(t0 to 200.0, t0 + day to 100.0), listOf(t0 + 1000 to -100.0))
        assertEquals(0.0, sold[1].second, 1e-9)
    }

    @Test
    fun indexedStartsAtZero() {
        val s = daily(t0, 100.0, 110.0, 121.0)
        val r = PortfolioTools.indexed(s.map { it.t }, s)!!
        assertEquals(listOf(0.0, 10.0, 21.0), r.map { Math.round(it.second * 1e6) / 1e6 })
        assertNull(PortfolioTools.indexed(listOf(t0 - day), s))
    }

    // Simulationen

    @Test
    fun projectionWithoutVolatilityIsCompoundInterest() {
        val p = PortfolioTools.project(start = 1000.0, monthly = 0.0, years = 10, returnPct = 7.0, volPct = 0.0, paths = 50)
        assertEquals(1000.0 * 1.07.pow(10), p.p50[10], 1e-6)
        assertEquals(p.p10[10], p.p90[10], 1e-6)
        // Sparplan ohne Rendite: alles Eingezahlte, nicht mehr
        val s = PortfolioTools.project(start = 0.0, monthly = 100.0, years = 5, returnPct = 0.0, volPct = 0.0, paths = 20)
        assertEquals(6000.0, s.p50[5], 1e-6)
        assertEquals(6000.0, s.invested[5], 1e-6)
    }

    @Test
    fun projectionMedianFollowsTheChosenReturn() {
        val p = PortfolioTools.project(start = 10_000.0, monthly = 0.0, years = 20, returnPct = 6.0, volPct = 15.0, paths = 4000)
        val expected = 10_000.0 * 1.06.pow(20)
        assertTrue("median ${p.p50[20]} vs $expected", kotlin.math.abs(p.p50[20] / expected - 1.0) < 0.05)
        assertTrue(p.p10[20] < p.p25[20] && p.p25[20] < p.p50[20] && p.p50[20] < p.p75[20] && p.p75[20] < p.p90[20])
        // Gleicher Zufall: dasselbe Ergebnis
        val again = PortfolioTools.project(start = 10_000.0, monthly = 0.0, years = 20, returnPct = 6.0, volPct = 15.0, paths = 4000)
        assertEquals(p.p50[20], again.p50[20], 1e-9)
    }

    @Test
    fun targetProbabilityFallsWithHigherTargets() {
        fun prob(target: Double) = PortfolioTools.project(50_000.0, 500.0, 25, 6.0, 15.0, target = target, paths = 800).targetProbability
        val a = prob(200_000.0)
        val b = prob(500_000.0)
        val c = prob(2_000_000.0)
        assertTrue("$a $b $c", a > b && b > c)
        val reach = PortfolioTools.project(50_000.0, 500.0, 25, 6.0, 0.0, target = 100_000.0, paths = 10)
        assertEquals(1.0, reach.targetProbability, 1e-9)
        assertNotNull(reach.targetYears)
    }

    @Test
    fun inflationTurnsValuesIntoTodaysMoney() {
        val p = PortfolioTools.project(1000.0, 0.0, 10, returnPct = 2.0, volPct = 0.0, inflationPct = 2.0, paths = 10)
        assertEquals(1000.0, p.p50[10], 1e-6)
    }

    @Test
    fun withdrawalLastsExactlyAsLongAsTheMoney() {
        // Ohne Rendite und Schwankung reichen 12.000 EUR fuer 10 Jahre knapp unter 100 EUR im Monat
        val ok = PortfolioTools.withdrawal(12_000.0, 99.0, 10, 0.0, 0.0, 0.0, indexed = false, paths = 20)
        assertEquals(1.0, ok.success, 1e-9)
        assertNull(ok.depletionYears)
        val short = PortfolioTools.withdrawal(12_000.0, 110.0, 10, 0.0, 0.0, 0.0, indexed = false, paths = 20)
        assertEquals(0.0, short.success, 1e-9)
        assertEquals(9.0, short.depletionYears!!, 0.1)
        assertTrue("safe ${ok.safeMonthly}", ok.safeMonthly in 99.0..100.0)
    }

    @Test
    fun withdrawalSucceedsLessWithHigherRates() {
        fun ok(rate: Double) = PortfolioTools.withdrawal(500_000.0, rate, 30, 5.0, 12.0, 2.0, indexed = true, paths = 600).success
        val a = ok(1_500.0)
        val b = ok(2_500.0)
        val c = ok(4_000.0)
        assertTrue("$a $b $c", a >= b && b >= c && a > c)
    }

    // Crash-Test

    @Test
    fun windowReturnAndRecovery() {
        val s = daily(t0, 100.0, 80.0, 60.0, 70.0, 90.0, 101.0, 105.0)
        assertEquals(-0.4, PortfolioTools.windowReturn(s, t0, t0 + 2 * day)!!, 1e-9)
        assertEquals(3L, PortfolioTools.recoveryDays(s, t0, t0 + 2 * day))
        // Reihe beginnt zu spaet
        assertNull(PortfolioTools.windowReturn(s, t0 - 30 * day, t0 + day))
        assertNull(PortfolioTools.recoveryDays(daily(t0, 100.0, 50.0, 60.0), t0, t0 + day))
    }

    @Test
    fun crisesAreInOrderAndCoverTheirWindow() {
        val list = PortfolioTools.CRASHES
        assertEquals(6, list.size)
        assertTrue(list.all { it.from < it.to })
        assertTrue(list.zipWithNext().all { (a, b) -> a.to < b.from })
        // Stichzeit am Tagesende: Die Tageskerze der New Yorker Boerse (Eroeffnung 14:30 UTC) zaehlt mit
        val covid = list.first { it.key == "COVID" }
        val open19 = java.time.Instant.parse("2020-02-19T14:30:00Z").toEpochMilli()
        val open20 = java.time.Instant.parse("2020-02-20T14:30:00Z").toEpochMilli()
        assertTrue(covid.from in open19 until open20)
        val sp = listOf(c(open19 - day, 3370.29), c(open19, 3386.15), c(java.time.Instant.parse("2020-03-23T13:30:00Z").toEpochMilli(), 2237.40))
        assertEquals(-0.339, PortfolioTools.windowReturn(sp, covid.from, covid.to)!!, 0.001)
    }

    @Test
    fun betaFromWeeklyReturns() {
        // Wochenschluesse ueber zwei Jahre; der Wert bewegt sich doppelt so stark wie der Markt (in Log-Renditen)
        val week = 7 * day
        val start = 1_600_000_000_000L
        val market = (0 until 104).map { k -> c(start + k * week, 100.0 * exp(0.02 * sin(k * 0.7) + 0.001 * k)) }
        val asset = market.map { c(it.t, 50.0 * (it.close / 100.0).pow(2.0)) }
        assertEquals(2.0, PortfolioTools.beta(asset, market, start)!!, 1e-6)
        assertNull(PortfolioTools.beta(asset.take(10), market.take(10), start))
    }

    // Dividenden, Streuung, Umschichten

    @Test
    fun dividendsRepeatOneYearLater() {
        val now = t0
        val src = listOf(PortfolioTools.DividendSource("A", "Alpha", 10.0, listOf(
            now - 400 * day to 1.0,     // aelter als ein Jahr: faellt weg
            now - 300 * day to 0.5,
            now - 30 * day to 0.6,
        )))
        val e = PortfolioTools.expectedDividends(src, now)
        assertEquals(2, e.size)
        assertEquals(now + 65 * day, e[0].date)
        assertEquals(5.0, e[0].amount, 1e-9)
        assertEquals(6.0, e[1].amount, 1e-9)
    }

    @Test
    fun concentrationAndEffectiveNumber() {
        val c = PortfolioTools.concentration(listOf(50.0, 30.0, 20.0))!!
        assertEquals(0.5, c.largest, 1e-9)
        assertEquals(1.0, c.top3, 1e-9)
        assertEquals(1.0 / (0.25 + 0.09 + 0.04), c.effective, 1e-9)
        assertEquals(4.0, PortfolioTools.concentration(listOf(1.0, 1.0, 1.0, 1.0))!!.effective, 1e-9)
    }

    @Test
    fun correlationOfDailyReturns() {
        val a = (0 until 60).map { i -> c(t0 + i * day, 100.0 * exp(0.01 * sin(i * 1.3))) }
        val same = a.map { c(it.t, it.close * 3.0) }
        val inverse = a.map { c(it.t, 1_000.0 / it.close) }
        assertEquals(1.0, PortfolioTools.correlation(a, same, t0)!!, 1e-9)
        assertEquals(-1.0, PortfolioTools.correlation(a, inverse, t0)!!, 1e-9)
        assertNull(PortfolioTools.correlation(a.take(20), same.take(20), t0))
    }

    @Test
    fun rebalanceWithSalesReachesTheTargets() {
        val values = listOf("A" to 700.0, "B" to 300.0)
        val t = PortfolioTools.rebalance(values, listOf(1.0, 1.0), 0.0, sell = true)
        assertEquals(-200.0, t[0].amount, 1e-9)
        assertEquals(200.0, t[1].amount, 1e-9)
        assertEquals(0.0, t.sumOf { it.amount }, 1e-9)
        assertEquals(0.0, PortfolioTools.deviation(values.mapIndexed { i, v -> v.second + t[i].amount }, listOf(1.0, 1.0)), 1e-9)
        assertEquals(0.2, PortfolioTools.deviation(values.map { it.second }, listOf(1.0, 1.0)), 1e-9)
    }

    @Test
    fun rebalanceBuyOnlyFillsTheGaps() {
        val values = listOf("A" to 700.0, "B" to 300.0)
        // 200 EUR reichen nicht ganz bis 50/50 (dafuer braeuchte es 400): alles geht zu B
        val small = PortfolioTools.rebalance(values, listOf(1.0, 1.0), 200.0, sell = false)
        assertEquals(0.0, small[0].amount, 1e-9)
        assertEquals(200.0, small[1].amount, 1e-9)
        // 1.000 EUR: Ziel 1.000 je Wert, A braucht 300, B braucht 700
        val big = PortfolioTools.rebalance(values, listOf(1.0, 1.0), 1000.0, sell = false)
        assertEquals(300.0, big[0].amount, 1e-9)
        assertEquals(700.0, big[1].amount, 1e-9)
        assertTrue(big.all { it.amount >= 0 })
    }
}
