package de.troetenrepublik.app.engine

// Nummern fuer Mitteilungen. Android ersetzt eine Mitteilung mit derselben Nummer; ein Zaehler, der in jedem neuen
// Prozess bei der gleichen Zahl beginnt, liesse neue Mitteilungen aeltere ungelesene ueberschreiben. Darum beginnt die
// Zaehlung bei der aktuellen Sekunde und steigt je Mitteilung um eins.
object Mitteilungsnummer {

    private const val OBERGRENZE = 2_000_000_000L

    // Naechste Nummer nach letzte (0 = noch keine in diesem Prozess), jetztSek: Sekunden seit 1970.
    fun naechste(letzte: Int, jetztSek: Long): Int {
        val basis = jetztSek.coerceAtLeast(1L) % OBERGRENZE
        val weiter = letzte.toLong() + 1
        return (if (weiter > basis && weiter < OBERGRENZE) weiter else basis).toInt()
    }
}
