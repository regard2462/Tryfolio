package de.troetenrepublik.app.engine

import kotlin.math.abs

// Kapitalkennzahlen aus dem letzten Geschaeftsjahr: Kurs je operativem Cashflow, Zinsdeckung, ROIC und Owner Earnings
// nach Buffett (vereinfacht). Reine Rechnungen ohne Abruf, Betraege in der Berichtswaehrung, Prozentwerte als Prozent
// (5,0 = 5 %).
object Kapitalkennzahlen {

    data class Ergebnis(val kursCashflow: Double?, val zinsdeckung: Double?, val roic: Double?, val ownerRendite: Double?)

    // Boersenwert geteilt durch den operativen Cashflow; nur mit beiden endlichen, positiven Werten
    fun kursCashflow(boersenwert: Double?, cashflow: Double?): Double? {
        if (boersenwert == null || cashflow == null || !boersenwert.isFinite() || !cashflow.isFinite()) return null
        if (!(boersenwert > 0) || !(cashflow > 0)) return null
        return boersenwert / cashflow
    }

    // Gewinn vor Zinsen und Steuern geteilt durch den Zinsaufwand desselben Jahres; ohne Zinsangabe null (Apple etwa
    // meldet den Zinsaufwand seit 2024 nicht mehr)
    fun zinsdeckung(j: Qualitaet.Jahr): Double? {
        val zins = j.zinsaufwand ?: return null
        val ebit = j.ebit ?: return null
        if (!(zins > 0)) return null
        return ebit / zins
    }

    // Gewinn vor Zinsen nach Steuern geteilt durch das investierte Kapital, in Prozent
    fun roic(j: Qualitaet.Jahr): Double? {
        val ebit = j.ebit ?: return null
        val satz = j.steuersatz ?: return null
        val kapital = j.investiertesKapital ?: return null
        if (satz < 0.0 || satz > 1.0) return null
        if (!(kapital > 0)) return null
        return ebit * (1.0 - satz) / kapital * 100.0
    }

    // Operativer Cashflow minus Investitionen in Anlagen (capex ist negativ gemeldet, das Vorzeichen egal); ohne capex
    // der freie Cashflow
    fun ownerEarnings(j: Qualitaet.Jahr): Double? {
        val cf = j.cashflow
        val capex = j.capex
        if (cf != null && capex != null) return cf - abs(capex)
        return j.freierCashflow
    }

    // Owner Earnings geteilt durch den Boersenwert, in Prozent
    fun ownerRendite(oe: Double?, boersenwert: Double?): Double? {
        if (oe == null || boersenwert == null || !boersenwert.isFinite() || !(boersenwert > 0)) return null
        return oe / boersenwert * 100.0
    }

    // Alle vier Kennzahlen fuer das letzte Jahr; ohne Jahr null. Der Cashflow der letzten zwoelf Monate geht vor dem
    // Jahreswert.
    fun berechne(jahre: List<Qualitaet.Jahr>, boersenwert: Double?): Ergebnis? {
        val j = jahre.lastOrNull() ?: return null
        val kc = kursCashflow(boersenwert, j.cashflowLaufend ?: j.cashflow)
        val oe = ownerEarnings(j)
        return Ergebnis(kc, zinsdeckung(j), roic(j), ownerRendite(oe, boersenwert))
    }
}
