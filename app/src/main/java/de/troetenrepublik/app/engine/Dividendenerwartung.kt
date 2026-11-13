package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.TaxState
import kotlin.math.min

// Erwartete Dividenden mit Zahltag und Betrag nach Steuern (0.78), fuer Dividendenkalender und Handelskalender gleich:
// Die Zahlungen stehen in der Reihenfolge ihrer Zahltage; jede verbraucht zuerst den Verlusttopf der sonstigen Ertraege,
// dann den verbleibenden Freistellungsauftrag des laufenden Jahres, der Rest wird mit dem Abgeltungsteuersatz belastet.
// ETF-Ertraege zaehlen nach Teilfreistellung. Die Summe der Steuer ist dieselbe wie auf die Jahressumme gerechnet.
object Dividendenerwartung {

    data class Zahlung(val key: String, val name: String, val exTag: Long, val zahltag: Long, val brutto: Double, val netto: Double)

    // freibetragRest null: ohne deutsche Steuer (fremdes Depot), netto gleich brutto.
    fun zahlungen(
        erwartet: List<PortfolioTools.ExpectedDividend>,
        freibetragRest: Double?,
        verlusttopf: Double = 0.0,
        teilfrei: (PortfolioTools.ExpectedDividend) -> Double = { Trading.teilfreistellung(it.key, it.name) },
    ): List<Zahlung> {
        var frei = (freibetragRest ?: 0.0).coerceAtLeast(0.0)
        var topf = verlusttopf.coerceAtLeast(0.0)
        return erwartet.sortedBy { it.zahltag }.map { e ->
            if (freibetragRest == null) return@map Zahlung(e.key, e.name, e.date, e.zahltag, e.amount, e.amount)
            var pflichtig = e.amount * (1.0 - teilfrei(e))
            val ausTopf = min(topf, pflichtig)
            topf -= ausTopf
            pflichtig -= ausTopf
            val ausFrei = min(frei, pflichtig)
            frei -= ausFrei
            pflichtig -= ausFrei
            Zahlung(e.key, e.name, e.date, e.zahltag, e.amount, e.amount - pflichtig * Tax.RATE)
        }
    }

    // Mit dem Steuerstand des Kontos: Freistellungsauftrag (hoechstens der fuer Paare) abzueglich des im laufenden Jahr
    // verbrauchten, Verlusttopf der sonstigen Ertraege.
    fun fuerKonto(erwartet: List<PortfolioTools.ExpectedDividend>, steuer: TaxState, freibetrag: Double, now: Long): List<Zahlung> {
        val jahr = Tax.forYear(steuer, now)
        val rest = (min(freibetrag, Tax.MAX_ALLOWANCE_JOINT) - jahr.allowanceUsed).coerceAtLeast(0.0)
        return zahlungen(erwartet, rest, jahr.lossPotGeneral)
    }
}
