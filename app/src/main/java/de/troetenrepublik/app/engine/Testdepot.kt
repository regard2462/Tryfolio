package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Position
import de.troetenrepublik.app.data.Valuation
import de.troetenrepublik.app.data.ValuedPosition

// Eigenes Testdepot des Crash-Tests: je Wert ein Betrag in Euro, getrennt vom echten Depot.
object Testdepot {
    data class Eintrag(val symbol: String, val name: String, val betrag: Double)

    // Eine Zeile je Wert: Symbol, Betrag und Name durch Tabulator getrennt.
    fun speichern(eintraege: List<Eintrag>): String =
        eintraege.filter { it.symbol.isNotBlank() && it.betrag.isFinite() && it.betrag > 0 }
            .joinToString("\n") { it.symbol.clean() + "\t" + it.betrag.toString() + "\t" + it.name.clean() }

    fun laden(text: String): List<Eintrag> =
        text.lines().mapNotNull { zeile ->
            val teile = zeile.split('\t')
            if (teile.size < 2) return@mapNotNull null
            val symbol = teile[0].trim()
            val betrag = teile[1].trim().toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 } ?: return@mapNotNull null
            if (symbol.isEmpty()) null else Eintrag(symbol, teile.getOrNull(2)?.trim().orEmpty().ifEmpty { symbol }, betrag)
        }.distinctBy { it.symbol }

    // Startpunkt aus dem echten Depot: Wert je Basiswert in Euro, Derivate bleiben draussen.
    fun ausDepot(positionen: List<ValuedPosition>): List<Eintrag> =
        positionen.filter { it.position.derivative == null && it.value > 0 }
            .groupBy { it.position.symbol }
            .map { (symbol, liste) -> Eintrag(symbol, liste.first().position.name, runden(liste.sumOf { it.value })) }
            .sortedByDescending { it.betrag }

    // Betrag als Wert der Position (Stueck = Euro, Kurs 1), damit die Krisenrechnung dieselbe bleibt.
    fun bewertung(eintraege: List<Eintrag>): Valuation {
        val positionen = eintraege.filter { it.betrag > 0 }.map { e ->
            ValuedPosition(
                Position(key = e.symbol, name = e.name, symbol = e.symbol, qty = e.betrag, avgPrice = 1.0, openedAt = 0L),
                price = 1.0, prevPrice = 1.0,
            )
        }
        return Valuation(positions = positionen, cash = 0.0, deposited = positionen.sumOf { it.invested })
    }

    private fun runden(v: Double): Double = Math.round(v * 100.0) / 100.0

    private fun String.clean(): String = replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim()
}
