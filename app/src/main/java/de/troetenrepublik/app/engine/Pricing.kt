package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Derivative
import de.troetenrepublik.app.data.DerivativeKind
import de.troetenrepublik.app.data.isFactor
import de.troetenrepublik.app.data.isKnockout
import de.troetenrepublik.app.data.isOption
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

// Bewertung der Hebelprodukte aus dem Live-Kurs des Basiswerts. Knock-out: innerer Wert durch Bezugsverhaeltnis,
// Faktor-Zertifikat: taegliche Hebelwirkung, Optionsschein: Black-Scholes mit impliziter Volatilitaet.
// Basispreis und Schwelle in Handelswaehrung, fx ist EUR je Einheit dieser Waehrung, spot immer in EUR.
object Pricing {

    // Zinssatz fuer Finanzierung und Abzinsung, wenn kein aktueller Satz uebergeben wird.
    const val RATE = 0.025

    const val MIN_PRICE = 0.001

    // Restwert je Stueck nach einem Knock-out, so zahlt es der Emittent zurueck.
    const val KO_RESIDUAL = 0.001

    private const val DAY_MS = 86_400_000.0
    private const val YEAR_MS = 365.0 * DAY_MS

    // ------------------------------------------------------------ Normalverteilung

    // Standardnormalverteilung, Abramowitz/Stegun 26.2.17 (Fehler < 7.5e-8).
    fun normCdf(x: Double): Double {
        val a1 = 0.254829592; val a2 = -0.284496736; val a3 = 1.421413741
        val a4 = -1.453152027; val a5 = 1.061405429; val p = 0.3275911
        val sign = if (x < 0) -1.0 else 1.0
        val z = abs(x) / sqrt(2.0)
        val t = 1.0 / (1.0 + p * z)
        val y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * exp(-z * z)
        return 0.5 * (1.0 + sign * y)
    }

    private fun normPdf(x: Double): Double = exp(-x * x / 2.0) / sqrt(2.0 * Math.PI)

    // ------------------------------------------------------------ Black-Scholes

    data class Greeks(val price: Double, val delta: Double, val gamma: Double, val vega: Double, val theta: Double)

    // s Kurs des Basiswerts, k Basispreis, t Restlaufzeit in Jahren,
    // vol Volatilitaet p. a., call true = Call, r risikoloser Zins.
    fun blackScholes(s: Double, k: Double, t: Double, vol: Double, call: Boolean, r: Double = RATE): Greeks {
        if (s <= 0 || k <= 0) return Greeks(0.0, 0.0, 0.0, 0.0, 0.0)
        // Abgelaufen: nur noch innerer Wert
        if (t <= 0.0 || vol <= 0.0) {
            val intrinsic = if (call) max(0.0, s - k) else max(0.0, k - s)
            val delta = if (call) (if (s > k) 1.0 else 0.0) else (if (s < k) -1.0 else 0.0)
            return Greeks(intrinsic, delta, 0.0, 0.0, 0.0)
        }
        val sqrtT = sqrt(t)
        val d1 = (ln(s / k) + (r + vol * vol / 2.0) * t) / (vol * sqrtT)
        val d2 = d1 - vol * sqrtT
        val disc = exp(-r * t)
        val price: Double
        val delta: Double
        val theta: Double
        if (call) {
            price = s * normCdf(d1) - k * disc * normCdf(d2)
            delta = normCdf(d1)
            theta = (-s * normPdf(d1) * vol / (2 * sqrtT) - r * k * disc * normCdf(d2)) / 365.0
        } else {
            price = k * disc * normCdf(-d2) - s * normCdf(-d1)
            delta = normCdf(d1) - 1.0
            theta = (-s * normPdf(d1) * vol / (2 * sqrtT) + r * k * disc * normCdf(-d2)) / 365.0
        }
        val gamma = normPdf(d1) / (s * vol * sqrtT)
        val vega = s * normPdf(d1) * sqrtT / 100.0
        return Greeks(max(0.0, price), delta, gamma, vega, theta)
    }

    // Volatilitaetslaecheln: weit aus dem Geld liegende Basispreise werden mit etwas hoeherer
    // Volatilitaet bewertet, Puts unterhalb des Kurses zusaetzlich (Absicherungsnachfrage).
    fun smileVol(base: Double, spot: Double, strike: Double, call: Boolean): Double {
        if (spot <= 0 || strike <= 0) return base
        val m = ln(strike / spot)
        val skew = if (!call && m < 0) 0.25 * abs(m) else 0.0
        return (base * (1.0 + 0.12 * abs(m)) + base * skew).coerceIn(0.05, 3.0)
    }

    // ------------------------------------------------------------ Produktpreis

    fun strikeEur(d: Derivative, fx: Double): Double = d.strike * fx

    fun barrierEur(d: Derivative, fx: Double): Double = d.barrier * fx

    // Preis beim Basiswertkurs spot in EUR. Faktor-Zertifikate brauchen den fortgeschriebenen Stand aus d.
    // Ein ausgeknocktes Produkt notiert zum Restwert, bis es zurueckgezahlt wird.
    fun price(d: Derivative, spot: Double, now: Long = System.currentTimeMillis(), fx: Double = 1.0, rate: Double = RATE): Double {
        if (d.isKnockedOut) return KO_RESIDUAL
        if (spot <= 0.0) return 0.0
        return when {
            d.kind.isKnockout -> {
                if (isKnockedOut(d, spot, fx)) KO_RESIDUAL
                else {
                    val k = strikeEur(d, fx)
                    val inner = if (d.kind == DerivativeKind.KO_LONG) spot - k else k - spot
                    max(KO_RESIDUAL, inner / d.ratio)
                }
            }
            d.kind.isFactor -> factorPrice(d, spot)
            d.kind.isOption -> {
                val t = if (d.expiry <= 0L) 1.0 else max(0.0, (d.expiry - now) / YEAR_MS)
                blackScholes(spot, strikeEur(d, fx), t, d.vol, d.kind == DerivativeKind.CALL, rate).price / d.ratio
            }
            else -> 0.0
        }
    }

    fun greeks(d: Derivative, spot: Double, now: Long = System.currentTimeMillis(), fx: Double = 1.0, rate: Double = RATE): Greeks? {
        if (!d.kind.isOption || spot <= 0) return null
        val t = if (d.expiry <= 0L) 1.0 else max(0.0, (d.expiry - now) / YEAR_MS)
        val g = blackScholes(spot, strikeEur(d, fx), t, d.vol, d.kind == DerivativeKind.CALL, rate)
        return g.copy(price = g.price / d.ratio, vega = g.vega / d.ratio, theta = g.theta / d.ratio)
    }

    fun isKnockedOut(d: Derivative, spot: Double, fx: Double = 1.0): Boolean = when (d.kind) {
        DerivativeKind.KO_LONG -> spot <= barrierEur(d, fx)
        DerivativeKind.KO_SHORT -> spot >= barrierEur(d, fx)
        else -> false
    }

    // Taeglich wird der Hebel auf die Tagesrendite angewendet. factorRef ist der Referenzkurs des Tages,
    // factorPrice der Stand zu diesem Kurs.
    fun factorPrice(d: Derivative, spot: Double): Double {
        val ref = if (d.factorRef > 0) d.factorRef else spot
        val base = if (d.factorPrice > 0) d.factorPrice else 1.0
        val ret = spot / ref - 1.0
        return max(MIN_PRICE, base * (1.0 + d.factor * ret))
    }

    // Tageswechsel oder Intraday-Reset. Ohne Reset koennte der Wert bei starken Bewegungen negativ werden,
    // wie bei echten Faktor-Zertifikaten loest ein Verlust von 85 % einen Reset aus.
    fun rollFactor(d: Derivative, spot: Double, dayChanged: Boolean, rate: Double = RATE): Derivative {
        if (!d.kind.isFactor || spot <= 0) return d
        val ref = if (d.factorRef > 0) d.factorRef else spot
        val base = if (d.factorPrice > 0) d.factorPrice else 1.0
        val ret = spot / ref - 1.0
        val levered = d.factor * ret
        val resetNeeded = levered <= -0.85
        if (!resetNeeded && !dayChanged) return d
        var p = max(MIN_PRICE, base * (1.0 + levered))
        // Finanzierungskosten: Faktor-Zertifikate kosten je Tag ein wenig Substanz
        if (dayChanged) p = max(MIN_PRICE, p * (1.0 - abs(d.factor) * rate / 365.0))
        return d.copy(factorPrice = p, factorRef = spot)
    }

    // Effektiver Hebel (Omega) zum aktuellen Kurs.
    fun leverage(d: Derivative, spot: Double, now: Long = System.currentTimeMillis(), fx: Double = 1.0, rate: Double = RATE): Double {
        if (spot <= 0 || d.isKnockedOut) return 0.0
        return when {
            d.kind.isFactor -> abs(d.factor)
            d.kind.isKnockout -> {
                val p = price(d, spot, now, fx, rate)
                if (p <= KO_RESIDUAL) 0.0 else spot / (p * d.ratio)
            }
            d.kind.isOption -> {
                val g = greeks(d, spot, now, fx, rate) ?: return 0.0
                if (g.price <= 0) 0.0 else abs(g.delta) * spot / (g.price * d.ratio)
            }
            else -> 0.0
        }
    }

    // Aufgeld eines Optionsscheins in Prozent: was der Basiswert bis zur Faelligkeit laufen muss.
    fun premiumPct(d: Derivative, spot: Double, now: Long, fx: Double = 1.0, rate: Double = RATE): Double {
        if (!d.kind.isOption || spot <= 0) return 0.0
        val p = price(d, spot, now, fx, rate) * d.ratio
        val k = strikeEur(d, fx)
        val breakEven = if (d.kind == DerivativeKind.CALL) k + p else k - p
        return (if (d.kind == DerivativeKind.CALL) breakEven / spot - 1.0 else 1.0 - breakEven / spot) * 100.0
    }

    // Innerer Wert je Stueck in EUR.
    fun intrinsic(d: Derivative, spot: Double, fx: Double = 1.0): Double {
        val k = strikeEur(d, fx)
        return when (d.kind) {
            DerivativeKind.CALL, DerivativeKind.KO_LONG -> max(0.0, spot - k) / d.ratio
            DerivativeKind.PUT, DerivativeKind.KO_SHORT -> max(0.0, k - spot) / d.ratio
            else -> 0.0
        }
    }

    // Die Spanne liegt symmetrisch um den Mittelkurs, bps ist die Gesamtspanne in Basispunkten (100 bp = 1 %).
    fun bid(mid: Double, bps: Double): Double = max(0.0, mid * (1.0 - bps / 20_000.0))

    fun ask(mid: Double, bps: Double): Double = max(0.0, mid * (1.0 + bps / 20_000.0))

    // Kaufen zum Brief, Verkaufen zum Geld, sonst Mittelkurs.
    fun withSpread(mid: Double, bps: Double, buying: Boolean?): Double = when (buying) {
        true -> ask(mid, bps)
        false -> bid(mid, bps)
        null -> mid
    }

    // Abstand zur Knock-out-Schwelle in Prozent.
    fun distanceToBarrier(d: Derivative, spot: Double, fx: Double = 1.0): Double {
        if (!d.kind.isKnockout || spot <= 0 || d.barrier <= 0) return 0.0
        return abs(spot - barrierEur(d, fx)) / spot * 100.0
    }

    // Grobe annualisierte Volatilitaet aus Schlusskursen, fallback bei zu wenigen Daten.
    fun historicalVol(closes: List<Double>, fallback: Double = 0.30): Double {
        val rets = closes.zipWithNext { a, b -> if (a > 0 && b > 0) ln(b / a) else 0.0 }.filter { it != 0.0 }
        if (rets.size < 20) return fallback
        val mean = rets.average()
        val variance = rets.sumOf { (it - mean).pow(2) } / (rets.size - 1)
        val daily = sqrt(variance)
        return (daily * sqrt(252.0)).coerceIn(0.05, 2.5)
    }
}
