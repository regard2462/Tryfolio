package de.troetenrepublik.app.engine

// [0.73] Waehrungsschock im Crash-Test: Die Heimatwaehrung steigt oder faellt um schock gegen jede andere Waehrung.
// Gerechnet wird nur die Umrechnung: Ein Wert in Fremdwaehrung bleibt dort gleich und ist in Heimatwaehrung
// 1 / (1 + schock) mal so viel wert. Die Reaktion der Unternehmen (Auslandsumsaetze, Preise) ist nicht Teil davon.
object Waehrungsschock {

    // Pence, US-Cent, Agorot und Cent wie in ExchangeRates als Pfund, Dollar, Schekel und Rand.
    fun code(waehrung: String): String = when (waehrung) {
        "GBp", "GBX" -> "GBP"
        "USd", "USX" -> "USD"
        "ILA" -> "ILS"
        "ZAc", "ZAC" -> "ZAR"
        else -> waehrung
    }

    // Anteile eines Postens je Waehrung (Summe 1). Fonds mit Laenderverteilung etf werden nach der Waehrung ihrer Laender
    // aufgeloest (DepotStreuung.waehrungen), alles andere zaehlt in seiner Handelswaehrung.
    fun verteilung(symbol: String, handel: String, etf: (String) -> Map<String, Double>?): Map<String, Double> {
        val roh = DepotStreuung.waehrungen(listOf(Triple(symbol, 1.0, code(handel))), etf)
        val summe = roh.values.sum()
        return if (summe > 0.0) roh.mapValues { it.value / summe } else mapOf(code(handel) to 1.0)
    }

    // Anteil einer Verteilung ausserhalb der Heimatwaehrung.
    fun fremdanteil(verteilung: Map<String, Double>, heim: String): Double {
        val summe = verteilung.values.sum()
        if (summe <= 0.0) return 0.0
        val h = code(heim)
        return verteilung.filterKeys { it != h }.values.sum() / summe
    }

    // Aenderung des Werts eines Fremdwaehrungsanteils in Heimatwaehrung (Anteil): Steigt die Heimatwaehrung um 10 %
    // (schock 0,1), ist ein Wert in Fremdwaehrung 9,09 % weniger wert; bei schock -0,1 ist er 11,11 % mehr wert.
    fun umrechnung(schock: Double): Double = 1.0 / (1.0 + schock.coerceAtLeast(-0.95)) - 1.0

    // Ergebnis je Posten (bewegung, Anteil) und der Anteil der Fremdwaehrung am Gesamtwert.
    class Ergebnis(val bewegung: List<Double>, val fremdanteil: Double)

    // werte: Wert je Posten; verteilungen: Waehrungsanteile je Posten (wie verteilung).
    fun rechnen(werte: List<Double>, verteilungen: List<Map<String, Double>>, heim: String, schock: Double): Ergebnis {
        require(werte.size == verteilungen.size)
        val u = umrechnung(schock)
        val anteile = verteilungen.map { fremdanteil(it, heim) }
        val summe = werte.sumOf { it.coerceAtLeast(0.0) }
        val fremd = if (summe > 0.0) werte.indices.sumOf { werte[it].coerceAtLeast(0.0) * anteile[it] } / summe else 0.0
        return Ergebnis(anteile.map { it * u }, fremd)
    }
}
