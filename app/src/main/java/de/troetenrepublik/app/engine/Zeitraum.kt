package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Range

// Frei gewaehlter Zeitraum im Chart. vonTag und bisTag sind Tage seit dem 1.1.1970 (UTC), wie sie die
// Datumsauswahl liefert.
object Zeitraum {
    const val TAG = 86_400_000L

    // Beginn und Ende in Millisekunden; das Ende ist der Schluss des letzten Tages, hoechstens jetzt.
    fun grenzen(vonTag: Long, bisTag: Long, jetzt: Long): Pair<Long, Long> {
        val von = minOf(vonTag, bisTag) * TAG
        val bis = minOf((maxOf(vonTag, bisTag) + 1) * TAG, jetzt)
        return von to maxOf(bis, von + 1)
    }

    // Yahoo liefert 15 und 30 Minuten nur fuer die letzten 60 Tage, Stunden fuer zwei Jahre.
    // Ziel sind einige hundert Punkte je Abruf.
    fun intervall(von: Long, bis: Long, jetzt: Long): String {
        val spanne = bis - von
        val alter = jetzt - von
        return when {
            spanne <= 8 * TAG && alter <= 58 * TAG -> "15m"
            spanne <= 40 * TAG && alter <= 58 * TAG -> "30m"
            spanne <= 100 * TAG && alter <= 720 * TAG -> "1h"
            spanne <= 3 * 366 * TAG -> "1d"
            else -> "1wk"
        }
    }

    fun intraday(intervall: String): Boolean = intervall.endsWith("m") || intervall.endsWith("h")

    // Kleinster feste Zeitraum, dessen Kerzen bis von zurueckreichen (fuer Broker und die Depotkurve).
    fun grob(von: Long, jetzt: Long): Range {
        val alter = jetzt - von
        return when {
            alter <= 6 * TAG -> Range.W1
            alter <= 29 * TAG -> Range.M1
            alter <= 88 * TAG -> Range.M3
            alter <= 180 * TAG -> Range.M6
            alter <= 362 * TAG -> Range.Y1
            else -> Range.MAX
        }
    }
}
