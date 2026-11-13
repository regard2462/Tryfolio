package de.troetenrepublik.app.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// Gemeinsame Skala fuer die Bewegung zu den Zahlen (0.77): 0 liegt in der Mitte, die uebliche Spanne ist ein Band um 0,
// die tatsaechliche Bewegung eine Marke. Liegt die Marke weit ausserhalb, bekommt das Band mindestens einen festen Teil
// der halben Breite und der Rest wird dahinter gestaucht, damit beides lesbar bleibt.
object BewegungsSkala {

    const val MIN_BAND = 0.25

    // halb: Wert am Rand der Achse, band: halbe Breite des Bands (Prozent), bandAnteil: Lage des Bandrands von 0 (Mitte)
    // bis 1 (Rand), gestaucht: hinter dem Band gilt ein anderer Massstab
    data class Skala(val halb: Double, val band: Double, val bandAnteil: Double, val gestaucht: Boolean) {
        // Lage eines Werts von -1 (linker Rand) bis 1 (rechter Rand)
        fun lage(w: Double): Double {
            val a = abs(w)
            val l = if (a <= band) bandAnteil * a / band else bandAnteil + (1.0 - bandAnteil) * min((a - band) / (halb - band), 1.0)
            return if (w < 0) -l else l
        }
    }

    // band: Betrag der breitesten Spanne in Prozent, marke: Bewegung mit Vorzeichen oder null
    fun skala(band: Double, marke: Double?): Skala {
        val b = max(band, 0.05)
        val m = abs(marke ?: 0.0)
        val halb = max(m * 1.15, b * 1.25)
        val linear = b / halb
        return if (linear >= MIN_BAND) Skala(halb, b, linear, false) else Skala(halb, b, MIN_BAND, true)
    }

    // Abweichung des Ist von der Schaetzung in Prozent, ohne brauchbare Schaetzung null
    fun abweichung(ist: Double, schaetzung: Double): Double? = Gewinnueberraschung.abweichung(ist, schaetzung)
}
