package de.troetenrepublik.app.engine

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

// Erwartete Kursspanne (seit 5.91): Kurs mal exp(plus/minus Sigma mal Wurzel aus t/365) fuer eine Standardabweichung.
// In etwa zwei von drei Faellen landet der Kurs nach t Tagen in dieser Spanne, wenn die Schwankung so bleibt; die
// Richtung sagt sie nicht voraus. Sigma aus der impliziten Volatilitaet der Optionen, sonst aus der Schwankung der
// letzten 60 Handelstage.
object Kursspanne {

    enum class Quelle { IMPLIZIT, HISTORISCH }

    // Grenzen nach tage Kalendertagen ab heute.
    data class Punkt(val tage: Int, val unten: Double, val oben: Double)

    data class Spanne(val kurs: Double, val sigma: Double, val quelle: Quelle, val punkte: List<Punkt>) {
        val unten: Double get() = punkte.last().unten
        val oben: Double get() = punkte.last().oben
        val tage: Int get() = punkte.last().tage
    }

    // Jaehrliche Schwankung aus den letzten tage Tagesrenditen (logarithmisch, 252 Handelstage im Jahr, bei
    // Kryptowerten 365); null bei weniger als 20 Renditen.
    fun sigmaHistorisch(schluss: List<Double>, tage: Int = 60, tageProJahr: Double = 252.0): Double? {
        val r = schluss.takeLast(tage + 1).zipWithNext { a, b -> if (a > 0 && b > 0) ln(b / a) else null }.filterNotNull()
        if (r.size < 20) return null
        val m = r.average()
        val v = r.sumOf { (it - m) * (it - m) } / (r.size - 1)
        return (sqrt(v) * sqrt(tageProJahr)).takeIf { it.isFinite() && it > 0 }
    }

    // Implizite Volatilitaet, wenn es sie in brauchbarer Hoehe gibt, sonst die historische.
    fun sigmaWahl(implizit: Double?, historisch: Double?): Pair<Double, Quelle>? = when {
        implizit != null && implizit in 0.01..5.0 -> implizit to Quelle.IMPLIZIT
        historisch != null && historisch > 0 -> historisch to Quelle.HISTORISCH
        else -> null
    }

    // Spanne fuer jeden Kalendertag von 0 bis tage.
    fun spanne(kurs: Double, sigma: Double, quelle: Quelle, tage: Int = 30): Spanne? {
        if (!(kurs > 0) || !(sigma > 0) || tage < 1) return null
        val punkte = (0..tage).map { t ->
            val s = sigma * sqrt(t / 365.0)
            Punkt(t, kurs * exp(-s), kurs * exp(s))
        }
        return Spanne(kurs, sigma, quelle, punkte)
    }
}
