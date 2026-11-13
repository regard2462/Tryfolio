package de.troetenrepublik.app.engine

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

// Rechenhilfen fuer Diagrammachsen: runde Teilstriche auf dem Datenbereich und Auswahl der Beschriftungen ohne
// Ueberlappung.
object Achsen {
    class Bereich(val von: Double, val bis: Double, val ticks: List<Double>)

    // Anteil des groesseren Betrags, unter dem ein Wert als nahe null gilt (dann steht die Null mit auf der Achse)
    const val NAHE_NULL = 0.25

    // Achse ueber dem Datenbereich min..max mit etwas Rand und runden Teilstrichen (1, 2, 2.5, 5 mal Zehnerpotenz, nicht
    // unter minSchritt). Die Null kommt nur dazu, wenn Werte um null liegen oder der naechste Wert nahe null ist.
    fun bereich(min: Double, max: Double, ziele: Int = 4, minSchritt: Double = 0.0, mitNull: Boolean = true): Bereich {
        var lo = minOf(min, max)
        var hi = maxOf(min, max)
        if (mitNull) {
            if (lo > 0 && lo <= NAHE_NULL * hi) lo = 0.0
            if (hi < 0 && abs(hi) <= NAHE_NULL * abs(lo)) hi = 0.0
        }
        if (hi - lo < 1e-12) {
            val d = maxOf(abs(lo) * 0.05, minSchritt, 1e-6)
            lo -= d
            hi += d
        }
        val rand = (hi - lo) * 0.06
        // Wo die Null die Grenze ist, bleibt sie die Grenze
        val von0 = lo == 0.0
        val bis0 = hi == 0.0
        val schritt = schritt((hi + (if (bis0) 0.0 else rand)) - (lo - (if (von0) 0.0 else rand)), ziele).coerceAtLeast(minSchritt)
        val von = floor((if (von0) lo else lo - rand) / schritt + 1e-9) * schritt
        val bis = ceil((if (bis0) hi else hi + rand) / schritt - 1e-9) * schritt
        val n = Math.round((bis - von) / schritt).toInt()
        return Bereich(von, bis, (0..n).map { Math.round((von + it * schritt) / schritt) * schritt })
    }

    // Runder Schritt, der die Spanne in etwa ziele Teile teilt
    fun schritt(spanne: Double, ziele: Int): Double {
        val roh = spanne / ziele.coerceAtLeast(1)
        val zehner = 10.0.pow(floor(log10(roh)))
        val f = roh / zehner
        val basis = when {
            f <= 1.0 -> 1.0
            f <= 2.0 -> 2.0
            f <= 2.5 -> 2.5
            f <= 5.0 -> 5.0
            else -> 10.0
        }
        return basis * zehner
    }

    // Beschriftungen an den senkrechten Stellen pos (Pixel, Mitte der Beschriftung), jede hoch hoehe. In der Reihenfolge
    // prioritaet (Indizes, wichtigste zuerst) bleibt eine Beschriftung, wenn sie keine schon gewaehlte um weniger als
    // hoehe plus luft beruehrt. Ergebnis: die behaltenen Indizes.
    fun ohneUeberlappung(pos: List<Float>, prioritaet: List<Int>, hoehe: Float, luft: Float = 0f): Set<Int> {
        val behalten = LinkedHashSet<Int>()
        for (i in prioritaet) {
            val p = pos.getOrNull(i) ?: continue
            if (behalten.none { abs(pos[it] - p) < hoehe + luft }) behalten += i
        }
        return behalten
    }
}
