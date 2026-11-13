package de.troetenrepublik.app.engine

import java.time.LocalDate

// Zeitraum und Summen der Insidergeschaefte (seit 5.93): Die Karten zeigen ein Jahr, die Betraege stehen in der
// Anzeigewaehrung des Nutzers.
object InsiderBetraege {

    // Erster Tag des Zeitraums (Vorgabe ein Jahr) als ISO-Datum, vergleichbar mit dem Feld d der Geschaefte.
    fun abTag(heute: LocalDate, tage: Int = 365): String = heute.minusDays(tage.toLong()).toString()

    // Summe der bereits umgerechneten Betraege in genau einer Waehrung; was ohne Kurs in seiner eigenen Waehrung
    // bleibt, faellt aus der Summe heraus.
    fun summe(betraege: List<Pair<Double, String>>, ziel: String): Double = betraege.filter { it.second == ziel }.sumOf { it.first }

    // Zahl der Betraege, die ohne Kurs in ihrer eigenen Waehrung blieben und deshalb in summe fehlen.
    fun ohneKurs(betraege: List<Pair<Double, String>>, ziel: String): Int = betraege.count { it.second != ziel }
}
