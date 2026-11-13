package de.troetenrepublik.app.engine

// Relatives Volumen: Volumen von heute durch das Mittel der letzten zehn Handelstage. Eine Rechnung fuer Bewegungen,
// Radar, Finder und die Wertglocke "Doppeltes Volumen"; die Schwellen bleiben bei den Stellen, die sie anwenden.
object RelativesVolumen {
    const val TAGE = 10

    // null ohne Volumen von heute oder ohne Schnitt
    fun wert(heute: Double, schnitt: Double): Double? =
        if (schnitt > 0 && heute > 0 && heute.isFinite() && schnitt.isFinite()) heute / schnitt else null

    // Mittel der letzten zehn abgeschlossenen Tage mit Umsatz; null, wenn weniger da sind.
    fun schnitt(volumina: List<Double>): Double? {
        val v = volumina.filter { it > 0 && it.isFinite() }.takeLast(TAGE)
        return if (v.size < TAGE) null else v.average()
    }
}
