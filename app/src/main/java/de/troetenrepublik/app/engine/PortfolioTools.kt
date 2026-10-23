package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.TxType
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// Rechenwerk der Werkzeuge in Analytics: Vergleich des Depots mit einem Markt, Vermoegensaufbau und
// Entnahmeplan (Monte-Carlo-Simulation), Crash-Test mit historischen Krisen, Dividendenkalender,
// Streuung und Umschichten. Alle Betraege in Euro; Renditen als Anteil (0,05 = 5 %), sofern der Name
// nicht auf "Pct" endet (dann Prozentwerte: 5,0 = 5 %).
object PortfolioTools {

    private const val DAY = 86_400_000L
    private const val YEAR_DAYS = 365.25

    // Letzter Schlusskurs am oder vor ts; null, wenn die Reihe erst danach beginnt.
    fun closeAt(series: List<Candle>, ts: Long): Double? {
        var lo = 0; var hi = series.size - 1; var best = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (series[mid].t <= ts) { best = mid; lo = mid + 1 } else hi = mid - 1
        }
        return if (best >= 0) series[best].close else null
    }

    // Marktvergleich

    // Geldbewegung zwischen Konto und Wertpapieren: positiv = Kauf, negativ = Verkauf oder Rueckzahlung.
    data class Flow(val t: Long, val amount: Double, val key: String)

    // Alle Geldbewegungen des Depots aus den Buchungen, zu Kurswerten ohne Gebuehren und Steuern.
    // Uebernommene Positionen zaehlen mit ihrem Einstandswert am Kaufdatum, Knock-out-Rueckzahlungen
    // und Faelligkeiten wie Verkaeufe.
    fun flows(account: Account): List<Flow> = account.transactions.mapNotNull { tx ->
        when (tx.type) {
            TxType.BUY -> Flow(tx.ts, tx.qty * tx.price, tx.posKey)
            TxType.SELL -> Flow(tx.ts, -tx.qty * tx.price, tx.posKey)
            TxType.KNOCKOUT, TxType.EXPIRY -> if (tx.amount > 0) Flow(tx.ts, -tx.amount, tx.posKey) else null
            else -> null
        }
    }.filter { it.amount.isFinite() && abs(it.amount) >= 0.005 }.sortedBy { it.t }

    // Summe der Dividenden vor Steuern seit since.
    fun dividendsGross(account: Account, since: Long = 0L): Double =
        account.transactions.filter { it.type == TxType.DIVIDEND && it.ts >= since }.sumOf { it.amount + it.tax }

    // Ergebnis, wenn dieselben Geldbewegungen in eine Vergleichsanlage geflossen waeren.
    data class Replay(
        // Wert des Vergleichsbestands heute.
        val value: Double,
        // Summe dessen, was dort bei Verkaeufen herausgenommen wurde.
        val proceeds: Double,
        val invested: Double,
    ) {
        val total: Double get() = value + proceeds
    }

    // Spielt flows in einer Vergleichsanlage mit den Tagesschlusskursen daily (EUR, aufsteigend)
    // nach: Jeder Kauf kauft dort fuer denselben Betrag, jeder Verkauf verkauft fuer denselben Betrag,
    // hoechstens den ganzen Bestand. Bewertet wird mit priceNow. null, wenn die Kursreihe erst mehr
    // als eine Woche nach der ersten Buchung beginnt.
    fun replay(flows: List<Flow>, daily: List<Candle>, priceNow: Double): Replay? {
        if (flows.isEmpty() || daily.isEmpty() || !(priceNow > 0.0) || !priceNow.isFinite()) return null
        if (flows.first().t < daily.first().t - 7 * DAY) return null
        var units = 0.0
        var proceeds = 0.0
        var invested = 0.0
        for (f in flows) {
            val p = closeAt(daily, f.t) ?: daily.first().close
            if (!(p > 0.0)) return null
            if (f.amount > 0) {
                units += f.amount / p
                invested += f.amount
            } else {
                val sold = min(-f.amount, units * p)
                units = max(0.0, units - sold / p)
                proceeds += sold
            }
        }
        return Replay(units * priceNow, proceeds, invested)
    }

    // Dieselben Geldbewegungen auf einem Tagesgeldkonto mit festem Zins ratePct (taeglich verzinst).
    // Verkaeufe entnehmen hoechstens das vorhandene Guthaben.
    fun replayCash(flows: List<Flow>, ratePct: Double, now: Long): Replay? {
        if (flows.isEmpty()) return null
        val daily = ln(1.0 + ratePct / 100.0) / YEAR_DAYS
        var balance = 0.0
        var proceeds = 0.0
        var invested = 0.0
        var t = flows.first().t
        for (f in flows) {
            balance *= exp(daily * (f.t - t) / DAY.toDouble())
            t = f.t
            if (f.amount > 0) { balance += f.amount; invested += f.amount }
            else { val sold = min(-f.amount, balance); balance -= sold; proceeds += sold }
        }
        balance *= exp(daily * (now - t).coerceAtLeast(0L) / DAY.toDouble())
        return Replay(balance, proceeds, invested)
    }

    // Geldgewichtete Rendite pro Jahr als Anteil: der Zins, zu dem die Bewegungen flows (positiv =
    // eingezahlt, negativ = entnommen) bis at genau auf terminal anwachsen. Bisektion zwischen
    // -99 % und +1000 %; null ohne eindeutige Loesung.
    fun moneyWeighted(flows: List<Flow>, terminal: Double, at: Long): Double? {
        if (flows.isEmpty() || !terminal.isFinite()) return null
        fun gap(r: Double) = flows.sumOf { f -> f.amount * (1.0 + r).pow((at - f.t) / (YEAR_DAYS * DAY)) } - terminal
        var lo = -0.99
        var hi = 10.0
        val gLo = gap(lo)
        val gHi = gap(hi)
        if (!gLo.isFinite() || !gHi.isFinite() || gLo * gHi > 0) return null
        repeat(200) {
            val mid = (lo + hi) / 2
            if (gap(mid) * gLo > 0) lo = mid else hi = mid
        }
        return (lo + hi) / 2
    }

    // Zeitgewichtete Wertentwicklung je Punkt in Prozent: Kaeufe und Verkaeufe veraendern sie nicht,
    // nur die Kursbewegung zaehlt. values ist der Depotwert je Zeitpunkt (mit Spruengen bei Kaeufen),
    // flows sind die Geldbewegungen (Zeit, Betrag; Kauf positiv). Der erste Punkt ist 0 %.
    fun timeWeighted(values: List<Pair<Long, Double>>, flows: List<Pair<Long, Double>>): List<Pair<Long, Double>> {
        val sorted = flows.sortedBy { it.first }
        val out = ArrayList<Pair<Long, Double>>(values.size)
        var index = 1.0
        var prev: Double? = null
        var fi = 0
        for ((t, v) in values) {
            var f = 0.0
            while (fi < sorted.size && sorted[fi].first <= t) {
                if (prev != null) f += sorted[fi].second
                fi++
            }
            val base = (prev ?: 0.0) + f
            if (prev != null && base > 1e-9 && v.isFinite() && v >= 0.0) index *= v / base
            out += t to (index - 1.0) * 100.0
            if (v.isFinite()) prev = v
        }
        return out
    }

    // Vergleichskurve in Prozent seit dem ersten Zeitpunkt von times; null, wenn es dort keinen Kurs gibt.
    fun indexed(times: List<Long>, series: List<Candle>): List<Pair<Long, Double>>? {
        if (times.isEmpty()) return null
        val base = closeAt(series, times.first()) ?: return null
        if (!(base > 0.0)) return null
        return times.map { t -> t to ((closeAt(series, t) ?: base) / base - 1.0) * 100.0 }
    }

    // Zufall

    // Schneller, wiederholbarer Zufall (xorshift64*) mit Normalverteilung (Polarmethode).
    class Rng(seed: Long) {
        private var s = if (seed == 0L) -0x61c8864680b583ebL else seed
        private var spare = Double.NaN

        fun nextLong(): Long {
            var x = s
            x = x xor (x ushr 12)
            x = x xor (x shl 25)
            x = x xor (x ushr 27)
            s = x
            return x * 2685821657736338717L
        }

        fun nextDouble(): Double = (nextLong() ushr 11) * (1.0 / (1L shl 53))

        fun gaussian(): Double {
            if (!spare.isNaN()) { val v = spare; spare = Double.NaN; return v }
            var u: Double
            var v: Double
            var q: Double
            do {
                u = nextDouble() * 2.0 - 1.0
                v = nextDouble() * 2.0 - 1.0
                q = u * u + v * v
            } while (q >= 1.0 || q == 0.0)
            val m = sqrt(-2.0 * ln(q) / q)
            spare = v * m
            return u * m
        }
    }

    private fun percentile(sorted: DoubleArray, q: Double): Double {
        if (sorted.isEmpty()) return 0.0
        val pos = q * (sorted.size - 1)
        val i = floor(pos).toInt().coerceIn(0, sorted.size - 1)
        val j = min(i + 1, sorted.size - 1)
        return sorted[i] + (sorted[j] - sorted[i]) * (pos - i)
    }

    private fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val s = values.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    // Vermoegensaufbau

    // Ergebnis der Simulation je Jahr 0 bis years: Perzentile des Depotwerts und die Summe der
    // Einzahlungen. Mit Inflation stehen alle Werte in heutiger Kaufkraft.
    class Projection(
        val years: Int,
        val p10: DoubleArray,
        val p25: DoubleArray,
        val p50: DoubleArray,
        val p75: DoubleArray,
        val p90: DoubleArray,
        val invested: DoubleArray,
        // Anteil der Verlaeufe, die am Ende mindestens das Ziel erreichen (0 bis 1); 0 ohne Ziel.
        val targetProbability: Double,
        // Nach wie vielen Jahren die Haelfte der Verlaeufe das Ziel erreicht hat; null = nicht im Zeitraum.
        val targetYears: Double?,
    )

    // Monte-Carlo-Simulation in Monatsschritten mit lognormalen Renditen. returnPct ist die Rendite
    // pro Jahr im mittleren Verlauf (so waechst eine Einmalanlage im Median genau mit diesem Satz),
    // volPct die Schwankung pro Jahr. Die Rate monthly fliesst am Monatsende hinein und steigt
    // jedes Jahr um raisePct. check wird regelmaessig aufgerufen (Abbruch einer laufenden Rechnung).
    fun project(
        start: Double,
        monthly: Double,
        years: Int,
        returnPct: Double,
        volPct: Double,
        raisePct: Double = 0.0,
        inflationPct: Double = 0.0,
        target: Double = 0.0,
        paths: Int = 1000,
        seed: Long = 42L,
        check: () -> Unit = {},
    ): Projection {
        val y = years.coerceIn(1, 60)
        val months = y * 12
        val mu = ln(1.0 + returnPct / 100.0) / 12.0
        val sd = max(0.0, volPct) / 100.0 / sqrt(12.0)
        val inflation = ln(1.0 + inflationPct / 100.0) / 12.0
        val atYear = Array(y + 1) { DoubleArray(paths) }
        val reached = ArrayList<Double>(paths)
        val rng = Rng(seed)
        for (p in 0 until paths) {
            if (p % 50 == 0) check()
            var v = max(0.0, start)
            var rate = max(0.0, monthly)
            atYear[0][p] = v
            var hit = if (target > 0 && v >= target) 0.0 else Double.NaN
            for (m in 1..months) {
                v = v * exp(mu + sd * rng.gaussian()) + rate
                val real = v * exp(-inflation * m)
                if (hit.isNaN() && target > 0 && real >= target) hit = m / 12.0
                if (m % 12 == 0) {
                    atYear[m / 12][p] = real
                    rate *= 1.0 + raisePct / 100.0
                }
            }
            if (!hit.isNaN()) reached += hit
        }
        val invested = DoubleArray(y + 1)
        run {
            var sum = max(0.0, start)
            var rate = max(0.0, monthly)
            invested[0] = sum
            for (m in 1..months) {
                sum += rate * exp(-inflation * m)
                if (m % 12 == 0) { invested[m / 12] = sum; rate *= 1.0 + raisePct / 100.0 }
            }
        }
        val p10 = DoubleArray(y + 1); val p25 = DoubleArray(y + 1); val p50 = DoubleArray(y + 1)
        val p75 = DoubleArray(y + 1); val p90 = DoubleArray(y + 1)
        for (k in 0..y) {
            val s = atYear[k].sortedArray()
            p10[k] = percentile(s, 0.10); p25[k] = percentile(s, 0.25); p50[k] = percentile(s, 0.50)
            p75[k] = percentile(s, 0.75); p90[k] = percentile(s, 0.90)
        }
        val probability = if (target > 0) atYear[y].count { it >= target }.toDouble() / paths else 0.0
        // Zeitpunkt, zu dem die Haelfte aller Verlaeufe das Ziel erreicht hat (Median ueber alle, nie erreicht = unendlich)
        val need = (paths + 1) / 2
        val half = if (target > 0 && reached.size >= need) reached.sorted()[need - 1] else null
        return Projection(y, p10, p25, p50, p75, p90, invested, probability, half)
    }

    // Entnahmeplan

    class Withdrawal(
        val years: Int,
        // Restvermoegen je Jahr (10-, 50-, 90-%-Punkt), mit Inflation in heutiger Kaufkraft.
        val p10: DoubleArray,
        val p50: DoubleArray,
        val p90: DoubleArray,
        // Anteil der Verlaeufe, in denen das Geld bis zum Ende reicht (0 bis 1).
        val success: Double,
        // Nach wie vielen Jahren im mittleren Verlauf nichts mehr uebrig ist; null = reicht.
        val depletionYears: Double?,
        // Hoechste Monatsrate, die in 90 % der Verlaeufe bis zum Ende reicht.
        val safeMonthly: Double,
    )

    // Entnahmeplan: Zu Monatsbeginn wird monthly entnommen (mit indexed jaehrlich um die Inflation
    // erhoeht), der Rest schwankt wie in project. Die Zufallszahlen werden einmal gezogen und fuer
    // alle Raten verwendet; so steigt die Erfolgsquote verlaesslich, wenn die Rate sinkt.
    fun withdrawal(
        start: Double,
        monthly: Double,
        years: Int,
        returnPct: Double,
        volPct: Double,
        inflationPct: Double,
        indexed: Boolean,
        paths: Int = 800,
        seed: Long = 7L,
        check: () -> Unit = {},
    ): Withdrawal {
        val y = years.coerceIn(1, 60)
        val months = y * 12
        val mu = ln(1.0 + returnPct / 100.0) / 12.0
        val sd = max(0.0, volPct) / 100.0 / sqrt(12.0)
        val inflation = ln(1.0 + inflationPct / 100.0) / 12.0
        val growth = FloatArray(paths * months)
        val rng = Rng(seed)
        for (i in growth.indices) growth[i] = exp(mu + sd * rng.gaussian()).toFloat()
        check()
        val rates = DoubleArray(months) { m -> if (indexed) exp(inflation * 12.0 * (m / 12)) else 1.0 }

        // Anteil der Verlaeufe, die mit der Rate w bis zum Ende reichen.
        fun successFor(w: Double): Double {
            var ok = 0
            for (p in 0 until paths) {
                var v = start
                val base = p * months
                var alive = true
                for (m in 0 until months) {
                    v -= w * rates[m]
                    if (v <= 0.0) { alive = false; break }
                    v *= growth[base + m]
                }
                if (alive) ok++
            }
            return ok.toDouble() / paths
        }

        val atYear = Array(y + 1) { DoubleArray(paths) }
        val empty = ArrayList<Double>()
        var ok = 0
        for (p in 0 until paths) {
            if (p % 100 == 0) check()
            var v = start
            atYear[0][p] = v
            val base = p * months
            var gone = Double.NaN
            for (m in 0 until months) {
                if (gone.isNaN()) {
                    v -= monthly * rates[m]
                    if (v <= 0.0) { v = 0.0; gone = m / 12.0 } else v *= growth[base + m]
                }
                if ((m + 1) % 12 == 0) atYear[(m + 1) / 12][p] = v * exp(-inflation * (m + 1))
            }
            if (gone.isNaN()) ok++ else empty += gone
        }
        val p10 = DoubleArray(y + 1); val p50 = DoubleArray(y + 1); val p90 = DoubleArray(y + 1)
        for (k in 0..y) {
            val s = atYear[k].sortedArray()
            p10[k] = percentile(s, 0.10); p50[k] = percentile(s, 0.50); p90[k] = percentile(s, 0.90)
        }
        // Median der Zeit bis zum leeren Depot ueber alle Verlaeufe (reicht = unendlich)
        val need = (paths + 1) / 2
        val depletion = if (empty.size >= need) empty.sorted()[need - 1] else null
        // Hoechste Rate mit 90 % Erfolg: Bisektion zwischen 0 und dem, was ohne Ertrag in einem Monat weg waere
        var lo = 0.0
        var hi = max(1.0, start)
        var steps = 0
        while (start > 0 && hi - lo > 0.5 && steps++ < 40) {
            check()
            val mid = (lo + hi) / 2
            if (successFor(mid) >= 0.9) lo = mid else hi = mid
        }
        return Withdrawal(y, p10, p50, p90, ok.toDouble() / paths, depletion, if (start > 0) lo else 0.0)
    }

    // Crash-Test

    // Historische Krise: vom Hoch from bis zum Tief to des S&P 500 (Schlusskurse).
    data class Crash(val key: String, val from: Long, val to: Long)

    // Stichzeit eines Tages: 23 Uhr UTC. Die Tageskerzen tragen den Zeitpunkt der Eroeffnung (Xetra
    // 7 oder 8 Uhr UTC, New York 13:30 oder 14:30 Uhr UTC); so zaehlt der Schluss genau dieses Tages.
    private fun day(y: Int, m: Int, d: Int): Long = LocalDate.of(y, m, d).atTime(23, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    val CRASHES = listOf(
        Crash("DOTCOM", day(2000, 3, 24), day(2002, 10, 9)),
        Crash("FINANCIAL", day(2007, 10, 9), day(2009, 3, 9)),
        Crash("EURO", day(2011, 4, 29), day(2011, 10, 3)),
        Crash("COVID", day(2020, 2, 19), day(2020, 3, 23)),
        Crash("RATES", day(2022, 1, 3), day(2022, 10, 12)),
        Crash("TARIFFS", day(2025, 2, 19), day(2025, 4, 8)),
    )

    // Kursveraenderung (Anteil) ueber ein Fenster; null, wenn die Reihe das Fenster nicht abdeckt.
    fun windowReturn(series: List<Candle>, from: Long, to: Long): Double? {
        if (series.size < 2 || series.first().t > from + 7 * DAY || series.last().t < to - 7 * DAY) return null
        val a = closeAt(series, from) ?: return null
        val b = closeAt(series, to) ?: return null
        return if (a > 0.0 && b.isFinite()) b / a - 1.0 else null
    }

    // Wie lange es nach dem Tief to dauerte, bis der Stand vom Hoch from wieder erreicht war (Tage); null = noch nicht.
    fun recoveryDays(series: List<Candle>, from: Long, to: Long): Long? {
        val peak = closeAt(series, from) ?: return null
        val back = series.firstOrNull { it.t > to && it.close >= peak } ?: return null
        return (back.t - to) / DAY
    }

    // Letzter Schluss je Kalenderwoche ab since (Schluessel: Wochennummer seit 1970).
    private fun weekly(series: List<Candle>, since: Long): Map<Long, Double> {
        val out = HashMap<Long, Double>()
        for (c in series) if (c.t >= since && c.close > 0) out[Math.floorDiv(c.t / DAY + 3, 7L)] = c.close
        return out
    }

    // Beta gegen market aus Wochenrenditen seit since: wie stark der Wert im Schnitt mitgeht,
    // wenn der Markt sich bewegt (1 = gleich stark). Wochen gleichen die unterschiedlichen
    // Handelszeiten in Europa und den USA aus. null bei weniger als 26 gemeinsamen Wochen.
    fun beta(asset: List<Candle>, market: List<Candle>, since: Long): Double? {
        val a = weekly(asset, since)
        val m = weekly(market, since)
        val weeks = a.keys.intersect(m.keys).sorted()
        val ra = ArrayList<Double>()
        val rm = ArrayList<Double>()
        for (i in 1 until weeks.size) {
            if (weeks[i] != weeks[i - 1] + 1) continue
            ra += ln(a[weeks[i]]!! / a[weeks[i - 1]]!!)
            rm += ln(m[weeks[i]]!! / m[weeks[i - 1]]!!)
        }
        if (ra.size < 26) return null
        val ma = ra.average()
        val mm = rm.average()
        var cov = 0.0
        var v = 0.0
        for (i in ra.indices) { cov += (ra[i] - ma) * (rm[i] - mm); v += (rm[i] - mm).pow(2) }
        return if (v > 0) cov / v else null
    }

    // Dividendenkalender

    // Erwartete Zahlung: Ex-Tag im kommenden Jahr und Betrag fuer den ganzen Bestand (EUR, vor Steuern).
    data class ExpectedDividend(val key: String, val name: String, val date: Long, val amount: Double)

    // Bestand fuer den Kalender: Schluessel, Name, Stueck und die Ex-Tage mit Betrag je Stueck (EUR).
    data class DividendSource(val key: String, val name: String, val qty: Double, val events: List<Pair<Long, Double>>)

    // Schreibt die Ausschuettungen der letzten zwoelf Monate um ein Jahr fort: dieselben Termine, dieselben
    // Betraege je Stueck, mal heutigem Bestand. Nur Termine in den naechsten zwoelf Monaten.
    fun expectedDividends(sources: List<DividendSource>, now: Long): List<ExpectedDividend> =
        sources.flatMap { s ->
            s.events.filter { (t, a) -> t > now - 365 * DAY && t <= now && a > 0 && a.isFinite() }
                .map { (t, a) -> ExpectedDividend(s.key, s.name, t + 365 * DAY, a * s.qty) }
        }.filter { it.date > now && it.date <= now + 366 * DAY && it.amount >= 0.005 }.sortedBy { it.date }

    // Streuung

    data class Concentration(
        // Groesster Anteil (0 bis 1).
        val largest: Double,
        // Anteil der drei groessten Positionen.
        val top3: Double,
        // Wie viele gleich grosse Positionen dieselbe Streuung haetten (1 / Summe der quadrierten Anteile).
        val effective: Double,
    )

    fun concentration(values: List<Double>): Concentration? {
        val v = values.filter { it > 0 && it.isFinite() }
        val total = v.sum()
        if (v.isEmpty() || total <= 0) return null
        val w = v.map { it / total }.sortedDescending()
        return Concentration(w.first(), w.take(3).sum(), 1.0 / w.sumOf { it * it })
    }

    // Korrelation der Tagesrenditen zweier Reihen an gemeinsamen Handelstagen seit since; null bei weniger als 40 Tagen.
    fun correlation(a: List<Candle>, b: List<Candle>, since: Long): Double? {
        val ma = HashMap<Long, Double>()
        for (c in a) if (c.t >= since && c.close > 0) ma[c.t / DAY] = c.close
        val days = b.filter { it.t >= since && it.close > 0 && ma.containsKey(it.t / DAY) }.map { it.t / DAY to it.close }
        if (days.size < 41) return null
        val x = ArrayList<Double>(days.size)
        val y = ArrayList<Double>(days.size)
        for (i in 1 until days.size) {
            val (d0, b0) = days[i - 1]
            val (d1, b1) = days[i]
            x += ln(ma[d1]!! / ma[d0]!!)
            y += ln(b1 / b0)
        }
        val mx = x.average()
        val my = y.average()
        var sxy = 0.0; var sxx = 0.0; var syy = 0.0
        for (i in x.indices) {
            val dx = x[i] - mx; val dy = y[i] - my
            sxy += dx * dy; sxx += dx * dx; syy += dy * dy
        }
        return if (sxx > 0 && syy > 0) (sxy / sqrt(sxx * syy)).coerceIn(-1.0, 1.0) else null
    }

    // Umschichten

    // Eine Zeile des Umschichtens: heutiger Wert, Zielwert und der Auftrag (positiv = kaufen, negativ = verkaufen).
    data class Trade(val key: String, val value: Double, val target: Double, val amount: Double)

    // Auftraege, um das Depot auf die Gewichte weights (beliebige nichtnegative Zahlen, werden auf
    // 100 % gebracht) zu bringen. Mit sell wird umgeschichtet; ohne wird nur mit newMoney zugekauft,
    // und zwar dort, wo am meisten fehlt (reicht das Geld nicht, anteilig).
    fun rebalance(values: List<Pair<String, Double>>, weights: List<Double>, newMoney: Double, sell: Boolean): List<Trade> {
        require(values.size == weights.size)
        val sumW = weights.sumOf { max(0.0, it) }
        if (values.isEmpty() || sumW <= 0) return values.map { (k, v) -> Trade(k, v, v, 0.0) }
        val w = weights.map { max(0.0, it) / sumW }
        val money = max(0.0, newMoney)
        val total = values.sumOf { it.second } + money
        val targets = w.map { it * total }
        if (sell) return values.mapIndexed { i, (k, v) -> Trade(k, v, targets[i], targets[i] - v) }
        val need = values.mapIndexed { i, (_, v) -> max(0.0, targets[i] - v) }
        val sumNeed = need.sum()
        val scale = if (sumNeed > money && sumNeed > 0) money / sumNeed else 1.0
        return values.mapIndexed { i, (k, v) -> Trade(k, v, targets[i], need[i] * scale) }
    }

    // Abweichung vom Ziel: welcher Anteil des Depots am falschen Platz liegt (0 bis 1).
    fun deviation(values: List<Double>, weights: List<Double>): Double {
        val total = values.sum()
        val sumW = weights.sumOf { max(0.0, it) }
        if (total <= 0 || sumW <= 0 || values.size != weights.size) return 0.0
        return values.indices.sumOf { i -> abs(values[i] / total - max(0.0, weights[i]) / sumW) } / 2.0
    }
}
