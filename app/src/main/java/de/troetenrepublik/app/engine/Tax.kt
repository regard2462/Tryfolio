package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.TaxState
import kotlin.math.max
import kotlin.math.min

// Abgeltungsteuer: 25 % Kapitalertragsteuer plus 5,5 % Soli darauf, zusammen 26,375 %. Vorher werden die
// Verlusttoepfe und der Freistellungsauftrag (hoechstens 1.000 EUR im Jahr) angerechnet.
// Verluste aus Aktienverkaeufen mindern nur Aktiengewinne, alle anderen Verluste alle Ertraege
// einschliesslich Zinsen und Dividenden. Kirchensteuer wird nicht nachgebildet.
object Tax {

    const val RATE = 0.26375
    const val MAX_ALLOWANCE = 1_000.0

    // Entscheidet ueber den Verlusttopf.
    enum class Kind { STOCK, GENERAL }

    data class Result(val state: TaxState, val tax: Double, val net: Double)

    // Zum Jahreswechsel werden Freibetrag und Summen genullt, die Verlusttoepfe bleiben.
    fun forYear(state: TaxState, now: Long): TaxState {
        val year = yearOf(now)
        if (state.year == year) return state
        return TaxState(
            year = year,
            lossPotStocks = state.lossPotStocks,
            lossPotGeneral = state.lossPotGeneral,
        )
    }

    // Verbucht einen Kapitalertrag (positiv) oder Verlust (negativ) und liefert den Steuerabzug.
    // allowance ist der eingerichtete Freistellungsauftrag in EUR (0 = keiner).
    fun apply(state: TaxState, amount: Double, kind: Kind, allowance: Double, now: Long): Result {
        var s = forYear(state, now)
        if (!amount.isFinite() || amount == 0.0) return Result(s, 0.0, amount)

        if (amount < 0) {
            s = if (kind == Kind.STOCK) s.copy(lossPotStocks = s.lossPotStocks - amount)
            else s.copy(lossPotGeneral = s.lossPotGeneral - amount)
            return Result(s, 0.0, amount)
        }

        var taxable = amount
        // Aktiengewinne duerfen beide Toepfe nutzen, sonstige Ertraege nur den allgemeinen.
        if (kind == Kind.STOCK && s.lossPotStocks > 0) {
            val use = min(s.lossPotStocks, taxable)
            s = s.copy(lossPotStocks = s.lossPotStocks - use)
            taxable -= use
        }
        if (taxable > 0 && s.lossPotGeneral > 0) {
            val use = min(s.lossPotGeneral, taxable)
            s = s.copy(lossPotGeneral = s.lossPotGeneral - use)
            taxable -= use
        }
        val allowanceLeft = max(0.0, min(allowance, MAX_ALLOWANCE) - s.allowanceUsed)
        if (taxable > 0 && allowanceLeft > 0) {
            val use = min(allowanceLeft, taxable)
            s = s.copy(allowanceUsed = s.allowanceUsed + use)
            taxable -= use
        }
        val tax = round2(taxable * RATE)
        s = s.copy(
            taxPaid = s.taxPaid + tax,
            gainsStocks = if (kind == Kind.STOCK) s.gainsStocks + amount else s.gainsStocks,
            gainsGeneral = if (kind == Kind.GENERAL) s.gainsGeneral + amount else s.gainsGeneral,
        )
        return Result(s, tax, amount - tax)
    }

    fun yearOf(ts: Long): Int =
        java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.of("Europe/Berlin")).year

    private fun round2(v: Double): Double = kotlin.math.round(v * 100.0) / 100.0
}
