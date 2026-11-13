package de.troetenrepublik.app.engine

// Entdecken je Branche im Aktienfinder (0.77): die Werte des gewaehlten Markts nach Branche gruppiert, je Branche nach
// Gesamtpunktzahl geordnet. Kleine Branchen mit weniger als MIN_WERTE Werten bilden zusammen eine Gruppe (Schluessel
// SAMMEL), wenn sie mindestens MIN_WERTE ergeben, sonst fallen sie weg. Reine Rechnung.
object FinderBranchen {

    const val MIN_WERTE = 5

    // Schluessel der zusammengefassten kleinen Branchen.
    const val SAMMEL = ""

    // Alle Werte der Gruppe, beste zuerst; staerke ist der Schnitt der drei besten (Reihenfolge der Gruppen).
    class Gruppe(val branche: String, val werte: List<Aktienfinder.Ergebnis>) {
        val staerke: Double = werte.take(3).map { it.gesamt }.average()
    }

    fun gruppen(liste: List<Aktienfinder.Ergebnis>): List<Gruppe> {
        val je = liste.filter { it.daten.branche.isNotBlank() }.groupBy { it.daten.branche }
        val (gross, klein) = je.entries.partition { it.value.size >= MIN_WERTE }
        val rest = klein.flatMap { it.value }
        val alle = gross.map { it.key to it.value } + if (rest.size >= MIN_WERTE) listOf(SAMMEL to rest) else emptyList()
        return alle.map { (b, l) -> Gruppe(b, l.sortedWith(compareByDescending<Aktienfinder.Ergebnis> { it.gesamt }.thenBy { it.symbol })) }
            .sortedWith(compareByDescending<Gruppe> { it.staerke }.thenBy { it.branche })
    }

    // Den staerksten guten Grund eines Werts; ohne guten Grund der erste.
    fun grund(e: Aktienfinder.Ergebnis): Aktienfinder.Grund? = e.gruende.firstOrNull { it.gut } ?: e.gruende.firstOrNull()
}
