package de.troetenrepublik.app.engine

// Interesse an einem Unternehmen aus den taeglichen Aufrufen seines Wikipedia-Artikels, als frei nutzbarer
// Ersatz fuer Google Trends. Durchschnitt der letzten sieben Tage gegen die vier Wochen davor.
object Interesse {

    data class Wert(val proTag: Double, val veraenderung: Double)

    // aufrufe aelteste zuerst. null bei weniger als 35 Tagen oder ohne Aufrufe im Vergleichszeitraum.
    fun berechnen(aufrufe: List<Int>): Wert? {
        if (aufrufe.size < 35) return null
        val zuletzt = aufrufe.takeLast(7).average()
        val davor = aufrufe.dropLast(7).takeLast(28).average()
        if (davor <= 0) return null
        return Wert(zuletzt, (zuletzt / davor - 1) * 100)
    }
}
