package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.TaxState
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// Abgeltungsteuer: 25 % Kapitalertragsteuer plus 5,5 % Soli darauf, zusammen 26,375 %. Vorher werden die
// Verlusttoepfe und der Freistellungsauftrag (1.000 EUR im Jahr, zusammen veranlagte Ehepaare 2.000 EUR) angerechnet.
// Verluste aus Aktienverkaeufen mindern nur Aktiengewinne, alle anderen Verluste alle Ertraege
// einschliesslich Zinsen und Dividenden. Ein Verlust wird zuerst mit den Ertraegen des laufenden Jahres verrechnet,
// die schon gezahlte Steuer kommt zurueck (§ 43a Abs. 3 EStG). Kirchensteuer wird nicht nachgebildet.
object Tax {

    // Werte des laufenden Jahres aus den Steuerwerten (steuerwerte.json), fuer Jahre ohne Eintrag die des juengsten davor.
    val RATE: Double get() = Steuerwerte.jetzt().satz
    val MAX_ALLOWANCE: Double get() = Steuerwerte.jetzt().pauschbetrag
    val MAX_ALLOWANCE_JOINT: Double get() = Steuerwerte.jetzt().pauschbetragPaar

    // Entscheidet ueber den Verlusttopf.
    enum class Kind { STOCK, GENERAL }

    // tax ist bei einer Erstattung negativ.
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
        if (amount < 0) return verlust(s, -amount, kind)

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
        val werte = Steuerwerte.fuer(s.year)
        val allowanceLeft = max(0.0, min(allowance, werte.pauschbetragPaar) - s.allowanceUsed)
        if (taxable > 0 && allowanceLeft > 0) {
            val use = min(allowanceLeft, taxable)
            s = if (kind == Kind.STOCK) s.copy(allowanceUsed = s.allowanceUsed + use, allowanceStocks = s.allowanceStocks + use)
            else s.copy(allowanceUsed = s.allowanceUsed + use, allowanceGeneral = s.allowanceGeneral + use)
            taxable -= use
        }
        val tax = steuer(taxable, werte)
        s = s.copy(
            taxPaid = s.taxPaid + tax,
            gainsStocks = if (kind == Kind.STOCK) s.gainsStocks + amount else s.gainsStocks,
            gainsGeneral = if (kind == Kind.GENERAL) s.gainsGeneral + amount else s.gainsGeneral,
            taxedStocks = if (kind == Kind.STOCK) s.taxedStocks + taxable else s.taxedStocks,
            taxedGeneral = if (kind == Kind.GENERAL) s.taxedGeneral + taxable else s.taxedGeneral,
        )
        return Result(s, tax, amount - tax)
    }

    // Ein Verlust mindert zuerst die versteuerten Ertraege des Jahres (Steuer zurueck), dann den genutzten
    // Freibetrag, der Rest geht in den Topf. Aktienverluste nur gegen Aktiengewinne, sonstige Verluste zuerst
    // gegen sonstige Ertraege, dann gegen Aktiengewinne.
    private fun verlust(start: TaxState, loss: Double, kind: Kind): Result {
        var s = start
        val werte = Steuerwerte.fuer(start.year)
        var rest = loss
        var verrechnet = 0.0
        if (kind == Kind.GENERAL) {
            val u = min(rest, s.taxedGeneral)
            s = s.copy(taxedGeneral = s.taxedGeneral - u); rest -= u; verrechnet += u
        }
        run {
            val u = min(rest, s.taxedStocks)
            s = s.copy(taxedStocks = s.taxedStocks - u); rest -= u; verrechnet += u
        }
        if (kind == Kind.GENERAL) {
            val u = min(rest, s.allowanceGeneral)
            s = s.copy(allowanceGeneral = s.allowanceGeneral - u, allowanceUsed = max(0.0, s.allowanceUsed - u)); rest -= u
        }
        run {
            val u = min(rest, s.allowanceStocks)
            s = s.copy(allowanceStocks = s.allowanceStocks - u, allowanceUsed = max(0.0, s.allowanceUsed - u)); rest -= u
        }
        val erstattung = min(max(0.0, s.taxPaid), steuer(verrechnet, werte))
        s = s.copy(taxPaid = s.taxPaid - erstattung)
        if (rest > 0) {
            s = if (kind == Kind.STOCK) s.copy(lossPotStocks = s.lossPotStocks + rest)
            else s.copy(lossPotGeneral = s.lossPotGeneral + rest)
        }
        return Result(s, -erstattung, -loss + erstattung)
    }

    // Kapitalertragsteuer und Soli je fuer sich auf volle Cent abgerundet (§ 4 SolZG).
    fun steuer(base: Double, werte: Steuerwerte.Jahr = Steuerwerte.jetzt()): Double {
        if (!base.isFinite() || base <= 0.0) return 0.0
        val kest = abrunden(base * werte.abgeltung / 100.0)
        return kest + abrunden(kest * werte.soli / 100.0)
    }

    fun yearOf(ts: Long): Int =
        java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.of("Europe/Berlin")).year

    private fun abrunden(v: Double): Double = floor(v * 100.0 + 1e-7) / 100.0
}
