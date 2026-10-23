package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.data.Derivative
import de.troetenrepublik.app.data.DerivativeKind
import de.troetenrepublik.app.data.isFactor
import de.troetenrepublik.app.data.isKnockout
import de.troetenrepublik.app.data.isOption
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

// Erzeugt die handelbaren Hebelprodukte zu einem Basiswert.
// Basispreise und Schwellen stehen in der Handelswaehrung des Basiswerts.
object Derivatives {

    const val MIN_LEVERAGE = 1
    // Bei Hebel 1000 liegt die Schwelle noch 0,08 % vom Kurs entfernt.
    const val MAX_CUSTOM_LEVERAGE = 1000
    const val MAX_LEVERAGE = 200

    const val MIN_FACTOR = 2
    const val MAX_FACTOR = 20

    // Standardprodukte ab Hebel 2, bei Hebel 1 laege der Basispreis bei null. Eigene Hebel duerfen ab 1 liegen.
    val LEVERAGES: List<Double> = (2..MAX_LEVERAGE).map { it.toDouble() }
    val FACTORS: List<Double> = (MIN_FACTOR..MAX_FACTOR).map { it.toDouble() }

    // Alle Hebelprodukte begibt die App selbst als einziger Emittent, nach dem Vorbild echter Emittenten.
    const val HOUSE = "Tryfolio"

    // Emittent gespeicherter Produkte aus der Zeit vor der Umbenennung
    private const val HOUSE_ALT = "Trötenrepublik"

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")

    fun clampLeverage(v: Double): Double = v.coerceIn(MIN_LEVERAGE.toDouble(), MAX_CUSTOM_LEVERAGE.toDouble())

    fun defaultVol(type: AssetType): Double = when (type) {
        AssetType.CRYPTO -> 0.65
        AssetType.INDEX -> 0.18
        AssetType.ETF -> 0.20
        AssetType.STOCK -> 0.35
        AssetType.COMMODITY -> 0.28
        AssetType.CURRENCY -> 0.09
    }

    // Bezugsverhaeltnis so, dass der Produktpreis etwa zwischen 0,50 EUR und 20 EUR liegt.
    fun ratioFor(innerValue: Double): Double {
        if (innerValue <= 0) return 1.0
        val target = 5.0
        val raw = innerValue / target
        if (raw <= 0) return 1.0
        val exp = floor(log10(raw))
        val mantissa = raw / 10.0.pow(exp)
        // Nur Verhaeltnisse 1, 2 und 5. Zehnerschritte allein liessen den Preis zwischen
        // benachbarten Hebeln um das Zehnfache springen.
        val nice = when {
            mantissa < 1.5 -> 1.0
            mantissa < 3.5 -> 2.0
            mantissa < 7.5 -> 5.0
            else -> 10.0
        }
        return (nice * 10.0.pow(exp)).coerceIn(0.001, 10_000.0)
    }

    private fun round(v: Double, digits: Int): Double {
        val f = 10.0.pow(digits)
        return kotlin.math.round(v * f) / f
    }

    private fun digitsFor(spot: Double): Int = when {
        spot >= 1000 -> 1
        spot >= 100 -> 2
        spot >= 10 -> 2
        spot >= 1 -> 3
        else -> 4
    }

    // Zwischen Hebel n und n+1 liegen nur spot / (n * (n+1)) Kurseinheiten.
    // Zu grobes Runden liesse benachbarte Hebel auf denselben Basispreis fallen.
    private fun strikeDigits(spot: Double, lev: Double): Int {
        val gap = spot / (lev * (lev + 1.0))
        val needed = if (gap > 0) ceil(-log10(gap)).toInt() + 1 else 4
        return needed.coerceIn(digitsFor(spot), 8)
    }

    // Knock-outs

    fun knockouts(
        symbol: String,
        name: String,
        spot: Double,
        ccy: String,
        long: Boolean,
        levels: List<Double> = LEVERAGES,
        now: Long = System.currentTimeMillis(),
    ): List<Derivative> {
        if (spot <= 0) return emptyList()
        return levels.map { knockout(symbol, name, spot, ccy, long, it, now) }.distinctBy { it.id }
    }

    // Knock-out zu einem frei gewaehlten Hebel. spot ist der Kurs in der Handelswaehrung ccy.
    fun knockout(
        symbol: String,
        name: String,
        spot: Double,
        ccy: String,
        long: Boolean,
        leverage: Double,
        now: Long = System.currentTimeMillis(),
    ): Derivative {
        val lev = round(clampLeverage(leverage), 2)
        val dig = strikeDigits(spot, lev)
        // Hebel = Spot / (Spot - Strike), also Strike = Spot * (1 -+ 1/Hebel).
        // Mindestens 1,05, sonst laege der Basispreis bei Hebeln nahe 1 bei null.
        val eff = max(lev, 1.05)
        val strike = if (long) spot * (1.0 - 1.0 / eff) else spot * (1.0 + 1.0 / eff)
        val s = round(strike, dig)
        // Die Schwelle liegt knapp vor dem Basispreis. Der Puffer schrumpft mit dem Hebel,
        // sonst laege er bei hohem Hebel weiter vom Kurs entfernt als der Basispreis.
        val buffer = (0.005 / lev * 5.0).coerceIn(0.0002, 0.005)
        val barrier = round(if (long) s * (1.0 + buffer) else s * (1.0 - buffer), dig)
        val inner = if (long) spot - s else s - spot
        val ratio = ratioFor(inner)
        val kind = if (long) DerivativeKind.KO_LONG else DerivativeKind.KO_SHORT
        return Derivative(
            id = idOf(kind, symbol, s, 0L, ratio, tag = fmtFactor(lev)),
            underlying = symbol,
            underlyingName = name,
            kind = kind,
            strike = s,
            barrier = barrier,
            ratio = ratio,
            createdAt = now,
            ccy = ccy,
            issuer = HOUSE,
            nominalLeverage = lev,
        )
    }

    // Faktoren

    fun factors(
        symbol: String,
        name: String,
        spot: Double,
        levels: List<Double> = FACTORS,
        now: Long = System.currentTimeMillis(),
    ): List<Derivative> {
        if (spot <= 0) return emptyList()
        return levels.flatMap { f -> listOf(f.coerceIn(1.0, MAX_FACTOR.toDouble()), -f.coerceIn(1.0, MAX_FACTOR.toDouble())) }
            .map { f -> factor(symbol, name, spot, f, now) }
            .distinctBy { it.id }
    }

    // Positiver Faktor ist Long, negativer Short. Notiert in EUR.
    fun factor(
        symbol: String,
        name: String,
        spot: Double,
        factor: Double,
        now: Long = System.currentTimeMillis(),
    ): Derivative {
        val kind = if (factor > 0) DerivativeKind.FACTOR_LONG else DerivativeKind.FACTOR_SHORT
        return Derivative(
            id = idOf(kind, symbol, abs(factor), 0L, 1.0),
            underlying = symbol,
            underlyingName = name,
            kind = kind,
            factor = factor,
            ratio = 1.0,
            factorPrice = 100.0,   // Emissionspreis in EUR
            factorRef = spot,
            createdAt = now,
            ccy = "EUR",
            issuer = HOUSE,
            nominalLeverage = abs(factor),
        )
    }

    // Optionsscheine

    // Faelligkeiten: in den ersten Wochen kurze Laufzeiten, danach der dritte Freitag jedes Monats fuer zwei Jahre.
    fun expiries(now: Long = System.currentTimeMillis()): List<Long> {
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val out = linkedSetOf<LocalDate>()
        var d = today.plusDays(2)
        // Jeder Boersentag der naechsten drei Wochen, danach jeder Freitag bis Woche acht.
        while (d <= today.plusWeeks(3)) { if (d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY) out += d; d = d.plusDays(1) }
        var f = today.with(TemporalAdjusters.next(DayOfWeek.FRIDAY))
        while (f <= today.plusWeeks(8)) { out += f; f = f.plusWeeks(1) }
        var m = today.withDayOfMonth(1)
        repeat(26) {
            val third = m.with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.FRIDAY))
            if (third > today.plusWeeks(1)) out += third
            m = m.plusMonths(1)
        }
        return out.sorted().map { it.atTime(17, 30).atZone(zone).toInstant().toEpochMilli() }
    }

    // spot in Handelswaehrung.
    fun strikeStep(spot: Double): Double = when {
        spot >= 5000 -> 100.0
        spot >= 2000 -> 50.0
        spot >= 500 -> 10.0
        spot >= 200 -> 5.0
        spot >= 50 -> 2.5
        spot >= 20 -> 1.0
        spot >= 5 -> 0.5
        spot >= 1 -> 0.1
        else -> 0.01
    }

    // Basispreise von etwa der Haelfte bis zum Zweieinhalbfachen des Kurses.
    fun strikes(spot: Double): List<Double> {
        if (spot <= 0) return emptyList()
        val step = strikeStep(spot)
        val lo = floor(spot * 0.5 / step) * step
        val hi = ceil(spot * 2.5 / step) * step
        val out = mutableListOf<Double>()
        var k = max(step, lo)
        while (k <= hi + 1e-9) { out += round(k, 4); k += step }
        return out
    }

    // Je Basispreis und Faelligkeit ein Schein. Die implizite Volatilitaet folgt einem Laecheln um die historische.
    // spot in Handelswaehrung ccy.
    fun warrants(
        symbol: String,
        name: String,
        spot: Double,
        ccy: String,
        vol: Double,
        call: Boolean,
        now: Long = System.currentTimeMillis(),
    ): List<Derivative> {
        if (spot <= 0) return emptyList()
        val kind = if (call) DerivativeKind.CALL else DerivativeKind.PUT
        val result = ArrayList<Derivative>(3000)
        val ks = strikes(spot)
        val exps = expiries(now)
        val dig = digitsFor(spot)
        for ((ei, expiry) in exps.withIndex()) {
            // Kurze Laufzeiten haben ein engeres Basispreisraster.
            val short = ei < 15
            for ((ki, strike) in ks.withIndex()) {
                if (short && abs(strike / spot - 1.0) > 0.35) continue
                if (!short && ki % 2 == 1 && abs(strike / spot - 1.0) > 0.6) continue
                val v = Pricing.smileVol(vol, spot, strike, call)
                // Bezugsverhaeltnis nach Kurshoehe, damit der Scheinpreis handlich bleibt.
                val ratio = if (spot >= 1000) 100.0 else if (spot >= 30) 10.0 else 1.0
                result += Derivative(
                    id = idOf(kind, symbol, round(strike, dig), expiry, ratio),
                    underlying = symbol,
                    underlyingName = name,
                    kind = kind,
                    strike = round(strike, dig),
                    ratio = ratio,
                    expiry = expiry,
                    vol = round(v, 4),
                    createdAt = now,
                    ccy = ccy,
                    issuer = HOUSE,
                )
            }
        }
        return result
    }

    // Schein mit genau diesem Basispreis und Verfall, als Gegenstueck zu einer im Copytrading gemeldeten Option.
    // verfallTag ist Mitternacht UTC, gehandelt wird bis 17:30 Uhr.
    fun warrantFuer(
        symbol: String,
        name: String,
        spot: Double,
        ccy: String,
        vol: Double,
        call: Boolean,
        strike: Double,
        verfallTag: Long,
        now: Long = System.currentTimeMillis(),
    ): Derivative {
        val kind = if (call) DerivativeKind.CALL else DerivativeKind.PUT
        val tag = java.time.Instant.ofEpochMilli(verfallTag).atZone(java.time.ZoneOffset.UTC).toLocalDate()
        val expiry = tag.atTime(17, 30).atZone(zone).toInstant().toEpochMilli()
        val ratio = if (spot >= 1000) 100.0 else if (spot >= 30) 10.0 else 1.0
        val k = round(strike, maxOf(2, digitsFor(spot)))
        return Derivative(
            id = idOf(kind, symbol, k, expiry, ratio),
            underlying = symbol,
            underlyingName = name,
            kind = kind,
            strike = k,
            ratio = ratio,
            expiry = expiry,
            vol = round(Pricing.smileVol(vol, spot, k, call), 4),
            createdAt = now,
            ccy = ccy,
            issuer = HOUSE,
        )
    }

    // Kennungen und Namen

    // ISIN aus der Produktkennung, stabil fuer dasselbe Produkt. Aufbau: DE000, 6 Zeichen, Pruefziffer nach ISO 6166.
    fun isin(id: String, issuerPrefix: String = "TR"): String {
        val alphabet = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        var h = 0L
        for (c in id) h = (h * 131 + c.code) and 0x7FFF_FFFF_FFFFL
        val body = StringBuilder(issuerPrefix.take(2).uppercase().padEnd(2, 'X'))
        var v = h
        repeat(4) {
            body.append(alphabet[(v % alphabet.length).toInt()])
            v /= alphabet.length
        }
        val core = "DE000$body"
        return core + checkDigit(core)
    }

    // Pruefziffer nach ISO 6166: Buchstaben zu Zahlen, dann Luhn.
    private fun checkDigit(core: String): Int {
        val digits = StringBuilder()
        for (c in core) {
            if (c.isDigit()) digits.append(c) else digits.append(c.uppercaseChar() - 'A' + 10)
        }
        var sum = 0
        var double = true
        for (i in digits.length - 1 downTo 0) {
            var d = digits[i] - '0'
            if (double) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
            double = !double
        }
        return (10 - sum % 10) % 10
    }

    fun issuer(d: Derivative): String = d.issuer.takeUnless { it.isBlank() || it == HOUSE_ALT } ?: HOUSE

    fun isinFor(d: Derivative): String = isin(d.id, "TR")

    // Bezeichnungen sind in beiden Sprachen gleich.
    fun strategy(d: Derivative): String = when (d.kind) {
        DerivativeKind.KO_LONG, DerivativeKind.FACTOR_LONG -> "Long"
        DerivativeKind.KO_SHORT, DerivativeKind.FACTOR_SHORT -> "Short"
        DerivativeKind.CALL -> "Call"
        DerivativeKind.PUT -> "Put"
    }

    fun idOf(
        kind: DerivativeKind,
        symbol: String,
        level: Double,
        expiry: Long,
        ratio: Double,
        tag: String = "",
    ): String {
        val lvl = kotlin.math.round(level * 10000).toLong()
        val rt = kotlin.math.round(ratio * 1000).toLong()
        val exp = if (expiry > 0) expiry / 86_400_000L else 0L
        val base = "$kind|$symbol|$lvl|$exp|$rt"
        return if (tag.isBlank()) base else "$base|$tag"
    }

    // Anzeigename nach Richtung und Schwelle in der Handelswaehrung, etwa "Long 17,07 $" oder "Long Faktor 5x".
    fun label(d: Derivative, locale: java.util.Locale = java.util.Locale.GERMANY): String = when (d.kind) {
        DerivativeKind.KO_LONG -> "Long ${money(d.barrier, d.ccy, locale)}"
        DerivativeKind.KO_SHORT -> "Short ${money(d.barrier, d.ccy, locale)}"
        DerivativeKind.FACTOR_LONG -> "Long Faktor ${fmtFactor(d.factor)}x"
        DerivativeKind.FACTOR_SHORT -> "Short Faktor ${fmtFactor(abs(d.factor))}x"
        DerivativeKind.CALL -> "Call ${money(d.strike, d.ccy, locale)}"
        DerivativeKind.PUT -> "Put ${money(d.strike, d.ccy, locale)}"
    }

    fun fmtFactor(f: Double): String =
        if (f == ceil(f)) f.toInt().toString() else String.format(java.util.Locale.ROOT, "%.2f", f).trimEnd('0').trimEnd('.')

    fun currencySymbol(ccy: String): String = when (ccy.uppercase()) {
        "EUR" -> "€"; "USD" -> "$"; "GBP" -> "£"; "GBX", "GBP." -> "p"; "CHF" -> "CHF"; "JPY" -> "¥"
        "DKK" -> "kr"; "SEK" -> "kr"; "NOK" -> "kr"; "CAD" -> "C$"; "AUD" -> "A$"; "HKD" -> "HK$"
        else -> ccy
    }

    fun money(v: Double, ccy: String, locale: java.util.Locale = java.util.Locale.GERMANY): String {
        val digits = when {
            abs(v) >= 1 -> 2
            abs(v) >= 0.01 -> 4
            else -> 6
        }
        val num = String.format(locale, "%,.${digits}f", v)
        val sym = currencySymbol(ccy)
        return if (locale.language == "de") "$num $sym" else if (sym.length == 1) "$sym$num" else "$num $sym"
    }

    fun tradable(d: Derivative, now: Long): Boolean =
        !d.isKnockedOut && (d.expiry <= 0L || d.expiry > now) && !(d.kind.isOption && d.expiry > 0 && d.expiry - now < 0)

    fun isWarrant(d: Derivative): Boolean = d.kind.isOption
    fun isTurbo(d: Derivative): Boolean = d.kind.isKnockout
    fun isFactorCert(d: Derivative): Boolean = d.kind.isFactor
}
