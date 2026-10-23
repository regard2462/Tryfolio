package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.market.MarketApi
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

// Rechenwerk der Werkzeuge auf der Wertpapierseite. Alle Funktionen arbeiten auf Tagesschluss-
// kursen in Euro und sind ohne Kosten, Steuern und Dividenden gerechnet, sofern nicht anders
// vermerkt. Ergebnisse in Prozent sind Prozentwerte (12,5 = 12,5 %).
object Analysis {

    private const val DAY = 86_400_000L
    private const val YEAR_DAYS = 365.25

    // Erster Tagesschluss am oder nach ts.
    fun closeFrom(daily: List<Candle>, ts: Long): Candle? {
        var lo = 0; var hi = daily.size - 1; var best = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (daily[mid].t >= ts) { best = mid; hi = mid - 1 } else lo = mid + 1
        }
        return if (best >= 0) daily[best] else null
    }

    // Groesster Rueckgang vom jeweiligen Hoch in Prozent (negativ oder 0).
    fun maxDrawdown(values: List<Double>): Double {
        var peak = Double.NEGATIVE_INFINITY
        var worst = 0.0
        for (v in values) {
            if (v > peak) peak = v
            if (peak > 0) worst = minOf(worst, (v / peak - 1.0) * 100.0)
        }
        return worst
    }

    // Einmalanlage

    data class LumpSum(
        val start: Candle,
        val units: Double,
        val value: Double,
        val gain: Double,
        val pct: Double,
        // Rendite pro Jahr in Prozent; null bei weniger als einem Jahr.
        val perYear: Double?,
        val maxDrawdown: Double,
        val path: List<Pair<Long, Double>>,
    )

    fun lumpSum(daily: List<Candle>, amount: Double, from: Long, priceNow: Double? = null, now: Long = System.currentTimeMillis()): LumpSum? {
        if (amount <= 0 || daily.isEmpty()) return null
        val start = closeFrom(daily, from) ?: return null
        if (start.close <= 0) return null
        val units = amount / start.close
        val tail = daily.filter { it.t >= start.t }
        val last = priceNow?.takeIf { it > 0 && it.isFinite() } ?: tail.last().close
        val value = units * last
        val years = (now - start.t) / (YEAR_DAYS * DAY)
        val perYear = if (years >= 1.0) ((value / amount).pow(1.0 / years) - 1.0) * 100.0 else null
        return LumpSum(
            start = start, units = units, value = value, gain = value - amount,
            pct = (value / amount - 1.0) * 100.0, perYear = perYear,
            maxDrawdown = maxDrawdown(tail.map { it.close }),
            path = tail.map { it.t to units * it.close } + (now to value),
        )
    }

    // Sparplan

    data class Plan(
        val invested: Double,
        val units: Double,
        val value: Double,
        val gain: Double,
        val pct: Double,
        val averageCost: Double,
        val executions: Int,
        // Geldgewichtete Rendite pro Jahr in Prozent; null bei weniger als einem Jahr.
        val perYear: Double?,
        val path: List<Pair<Long, Double>>,
        val investedPath: List<Pair<Long, Double>>,
    )

    // Monatliche Ausfuehrung am ersten Handelstag jedes Monats ab from.
    fun savingsPlan(
        daily: List<Candle>,
        monthly: Double,
        from: Long,
        priceNow: Double? = null,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Plan? {
        if (monthly <= 0 || daily.isEmpty()) return null
        val first = closeFrom(daily, from) ?: return null
        var month = YearMonth.from(Instant.ofEpochMilli(first.t).atZone(zone))
        val end = YearMonth.from(Instant.ofEpochMilli(now).atZone(zone))
        val buys = mutableListOf<Candle>()
        while (!month.isAfter(end)) {
            val ts = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val c = closeFrom(daily, maxOf(ts, first.t))
            if (c != null && buys.none { it.t == c.t } && c.t <= now) buys += c
            month = month.plusMonths(1)
        }
        if (buys.isEmpty()) return null
        var units = 0.0
        var invested = 0.0
        val path = mutableListOf<Pair<Long, Double>>()
        val investedPath = mutableListOf<Pair<Long, Double>>()
        var next = 0
        for (c in daily) {
            if (c.t < buys.first().t) continue
            while (next < buys.size && buys[next].t <= c.t) {
                units += monthly / buys[next].close
                invested += monthly
                next++
            }
            path += c.t to units * c.close
            investedPath += c.t to invested
        }
        val last = priceNow?.takeIf { it > 0 && it.isFinite() } ?: daily.last().close
        val value = units * last
        path += now to value
        investedPath += now to invested
        val years = (now - buys.first().t) / (YEAR_DAYS * DAY)
        val perYear = if (years >= 1.0) irr(buys.map { it.t to monthly }, value, now) else null
        return Plan(
            invested = invested, units = units, value = value, gain = value - invested,
            pct = if (invested > 0) (value / invested - 1.0) * 100.0 else 0.0,
            averageCost = if (units > 0) invested / units else 0.0,
            executions = buys.size, perYear = perYear, path = path, investedPath = investedPath,
        )
    }

    // Geldgewichtete Rendite pro Jahr in Prozent: der Zinssatz, zu dem die Einzahlungen flows
    // bis at genau auf value anwachsen. Bisektion zwischen -99 % und +1000 %.
    fun irr(flows: List<Pair<Long, Double>>, value: Double, at: Long): Double? {
        if (flows.isEmpty() || value <= 0) return null
        fun fv(r: Double) = flows.sumOf { (t, a) -> a * (1.0 + r).pow((at - t) / (YEAR_DAYS * DAY)) }
        var lo = -0.99; var hi = 10.0
        if (fv(lo) > value || fv(hi) < value) return null
        repeat(200) {
            val mid = (lo + hi) / 2
            if (fv(mid) < value) lo = mid else hi = mid
        }
        return (lo + hi) / 2 * 100.0
    }

    // Schwankung und Rueckgaenge

    data class Risk(
        // Schwankung pro Jahr (annualisierte Standardabweichung der Tagesrenditen) in Prozent.
        val volatility: Double,
        val maxDrawdown: Double,
        val peakAt: Long,
        val troughAt: Long,
        // Tag, an dem das alte Hoch wieder erreicht wurde; null = noch nicht erholt.
        val recoveredAt: Long?,
        val worstDay: Pair<Long, Double>?,
        val bestDay: Pair<Long, Double>?,
        // Anteil der Tage mit Kursplus in Prozent.
        val upDays: Double,
        val drawdownPath: List<Pair<Long, Double>>,
    )

    fun risk(daily: List<Candle>, from: Long, tradingDays: Int = 252): Risk? {
        val slice = daily.filter { it.t >= from && it.close > 0 }
        if (slice.size < 20) return null
        val returns = slice.zipWithNext { a, b -> b.t to ln(b.close / a.close) }
        val mean = returns.sumOf { it.second } / returns.size
        val variance = returns.sumOf { (it.second - mean).pow(2) } / (returns.size - 1).coerceAtLeast(1)
        val vol = sqrt(variance) * sqrt(tradingDays.toDouble()) * 100.0
        var peak = slice.first(); var peakAt = peak.t
        var worst = 0.0; var worstPeakAt = peak.t; var worstTroughAt = peak.t
        val dd = mutableListOf<Pair<Long, Double>>()
        for (c in slice) {
            if (c.close > peak.close) { peak = c; peakAt = c.t }
            val d = (c.close / peak.close - 1.0) * 100.0
            dd += c.t to d
            if (d < worst) { worst = d; worstPeakAt = peakAt; worstTroughAt = c.t }
        }
        val peakLevel = slice.first { it.t == worstPeakAt }.close
        val recovered = slice.firstOrNull { it.t > worstTroughAt && it.close >= peakLevel }?.t
        val simple = slice.zipWithNext { a, b -> b.t to (b.close / a.close - 1.0) * 100.0 }
        return Risk(
            volatility = vol, maxDrawdown = worst, peakAt = worstPeakAt, troughAt = worstTroughAt,
            recoveredAt = if (worst < 0) recovered else null,
            worstDay = simple.minByOrNull { it.second }, bestDay = simple.maxByOrNull { it.second },
            upDays = simple.count { it.second > 0 } * 100.0 / simple.size,
            drawdownPath = dd,
        )
    }

    // Dividenden

    data class Dividends(
        // Summe je Aktie der letzten 12 Monate in EUR.
        val trailing: Double,
        // Anzahl Zahlungen der letzten 12 Monate.
        val payments: Int,
        // Summe je Kalenderjahr, aufsteigend.
        val byYear: List<Pair<Int, Double>>,
        // Wachstum pro Jahr der vollen Jahressummen ueber bis zu fuenf Jahre in Prozent.
        val growth: Double?,
    )

    fun dividends(events: List<MarketApi.DividendEvent>, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Dividends {
        val trailing = events.filter { it.exDate > now - 365 * DAY && it.exDate <= now }
        val byYear = events.groupBy { Instant.ofEpochMilli(it.exDate).atZone(zone).year }
            .mapValues { (_, v) -> v.sumOf { it.amount } }.toSortedMap().toList()
        val thisYear = Instant.ofEpochMilli(now).atZone(zone).year
        val full = byYear.filter { it.first < thisYear }
        val growth = if (full.size >= 2) {
            val window = full.takeLast(6)
            val a = window.first(); val b = window.last()
            val n = b.first - a.first
            if (a.second > 0 && n > 0) ((b.second / a.second).pow(1.0 / n) - 1.0) * 100.0 else null
        } else null
        return Dividends(trailing.sumOf { it.amount }, trailing.size, byYear, growth)
    }

    // Steuer auf Kapitalertraege fuer gross, soweit er den verbleibenden Freibetrag uebersteigt.
    fun taxOn(gross: Double, allowanceLeft: Double): Double =
        (gross - allowanceLeft.coerceAtLeast(0.0)).coerceAtLeast(0.0) * Tax.RATE

    // Fairer Wert

    data class FairValue(
        val peNow: Double,
        val epsFuture: Double,
        val priceFuture: Double,
        // Heutiger Wert: Kurs in years Jahren, abgezinst mit dem Renditeanspruch.
        val fair: Double,
        // Abstand des aktuellen Kurses zum fairen Wert in Prozent (positiv = Kurs liegt darueber).
        val premium: Double,
        // Rendite pro Jahr bei Kauf zum aktuellen Kurs, wenn die Annahmen eintreffen.
        val impliedReturn: Double,
    )

    // Alle Werte in derselben Waehrung; growth und required in Prozent.
    fun fairValue(eps: Double, price: Double, growth: Double, years: Int, exitPe: Double, required: Double): FairValue? {
        if (eps <= 0 || price <= 0 || years <= 0 || exitPe <= 0) return null
        val epsFuture = eps * (1.0 + growth / 100.0).pow(years)
        val priceFuture = epsFuture * exitPe
        val fair = priceFuture / (1.0 + required / 100.0).pow(years)
        return FairValue(
            peNow = price / eps, epsFuture = epsFuture, priceFuture = priceFuture, fair = fair,
            premium = (price / fair - 1.0) * 100.0,
            impliedReturn = ((priceFuture / price).pow(1.0 / years) - 1.0) * 100.0,
        )
    }

    // Positionsgroesse

    data class PositionSize(
        val stopPrice: Double,
        val lossPerUnit: Double,
        val maxLoss: Double,
        val units: Double,
        val capital: Double,
        // Anteil des Kapitaleinsatzes am Depotwert in Prozent.
        val shareOfDepot: Double,
    )

    // riskPct und stopPct in Prozent; wholeUnits rundet auf ganze Stueck ab.
    fun positionSize(depot: Double, price: Double, riskPct: Double, stopPct: Double, wholeUnits: Boolean): PositionSize? {
        if (depot <= 0 || price <= 0 || riskPct <= 0 || stopPct <= 0 || stopPct >= 100) return null
        val stop = price * (1.0 - stopPct / 100.0)
        val lossPerUnit = price - stop
        val maxLoss = depot * riskPct / 100.0
        val raw = maxLoss / lossPerUnit
        val units = if (wholeUnits) floor(raw) else floor(raw * 10_000.0) / 10_000.0
        val capital = units * price
        return PositionSize(stop, lossPerUnit, maxLoss, units, capital, capital / depot * 100.0)
    }

    // Veraenderung von from zu to in Prozent.
    fun change(from: Double, to: Double): Double = if (abs(from) < 1e-12) 0.0 else (to / from - 1.0) * 100.0
}
