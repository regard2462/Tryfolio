package de.troetenrepublik.app.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// Schwelle fuers Umschichten: Erst wenn ein Baustein (Position oder Kategorie) weiter vom Ziel liegt als die Schwelle,
// wird umgeschichtet, dann das ganze Depot. Anteile am Depot von 0 bis 1.
object Umschichtschwelle {

    // Abweichung in Prozentpunkten, ab der umgeschichtet wird.
    const val PUNKTE = 0.10

    // Kleine Bausteine: Ziel bis zu diesem Anteil.
    const val KLEIN_BIS = 0.20

    // Kleine Bausteine: Abweichung im Verhaeltnis zum Ziel.
    const val RELATIV = 0.50

    // Erlaubte Abweichung eines Bausteins mit dem Zielanteil ziel: 10 Punkte, bei kleinen Bausteinen 10 Punkte oder
    // die Haelfte des Ziels, je nachdem, was kleiner ist.
    fun schwelle(ziel: Double): Double {
        val z = max(0.0, ziel)
        return if (z <= KLEIN_BIS) min(PUNKTE, z * RELATIV) else PUNKTE
    }

    fun imRahmen(ist: Double, ziel: Double): Boolean = abs(ist - ziel) <= schwelle(ziel) + 1e-9

    // Ist- und Zielanteile je Baustein aus Werten und Gewichten (beliebige nichtnegative Zahlen). Leer, wenn die
    // Listen nicht zusammenpassen oder eine Summe fehlt.
    fun anteile(werte: List<Double>, gewichte: List<Double>): List<Pair<Double, Double>> {
        val summe = werte.sumOf { max(0.0, it) }
        val sumW = gewichte.sumOf { max(0.0, it) }
        if (werte.size != gewichte.size || summe <= 0 || sumW <= 0) return emptyList()
        return werte.indices.map { i -> max(0.0, werte[i]) / summe to max(0.0, gewichte[i]) / sumW }
    }

    // Je Baustein (Ist, Ziel): liegt er im Rahmen.
    fun imRahmen(bausteine: List<Pair<Double, Double>>): List<Boolean> = bausteine.map { (ist, ziel) -> imRahmen(ist, ziel) }

    // Umschichten ist noetig, sobald ein Baustein ausserhalb der Schwelle liegt.
    fun noetig(bausteine: List<Pair<Double, Double>>): Boolean = bausteine.any { (ist, ziel) -> !imRahmen(ist, ziel) }

    // Auftraege mit Schwelle: unveraendert, wenn umgeschichtet wird, sonst keiner.
    fun orders(trades: List<PortfolioTools.Trade>, noetig: Boolean): List<PortfolioTools.Trade> =
        if (noetig) trades else trades.map { it.copy(amount = 0.0) }
}
