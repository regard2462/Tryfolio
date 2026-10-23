package de.troetenrepublik.app.engine

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

// Risikokennzahlen einer Wertreihe, gerechnet mit den Renditen zwischen den Punkten (taeglich oder woechentlich).
// Die Zahl der Punkte pro Jahr ergibt sich aus der Reihe. Ein risikofreier Zins wird nicht abgezogen.
object Risiko {

    data class Kennzahlen(
        // Etwa 252 bei Handelstagen, 52 bei Wochen.
        val proJahr: Double,
        val sharpe: Double?,
        val sortino: Double?,
        val calmar: Double?,
        // In Prozentpunkten: Wurzel aus dem mittleren quadrierten Abstand zum letzten Hoch.
        val ulcer: Double,
        // Verlust, der in 95 Prozent der Abschnitte nicht ueberschritten wurde. Positiv, 0,02 = 2 Prozent.
        val var95: Double?,
        // Mittlerer Verlust in den schlechtesten 5 Prozent der Abschnitte, positiv.
        val es95: Double?,
        // In Tagen. Laeuft weiter, solange das alte Hoch nicht wieder erreicht ist.
        val rueckgangTage: Long,
        val laufend: Boolean,
    )

    data class Vergleich(val beta: Double, val alphaProJahr: Double, val korrelation: Double)

    private const val TAG = 86_400_000.0

    private fun renditen(reihe: List<Pair<Long, Double>>): List<Double> =
        (1 until reihe.size).mapNotNull { i -> if (reihe[i - 1].second > 0 && reihe[i].second > 0) reihe[i].second / reihe[i - 1].second - 1 else null }

    private fun quantil(sortiert: List<Double>, q: Double): Double {
        if (sortiert.size == 1) return sortiert[0]
        val pos = q * (sortiert.size - 1)
        val i = floor(pos).toInt()
        val f = pos - i
        return if (i + 1 < sortiert.size) sortiert[i] * (1 - f) + sortiert[i + 1] * f else sortiert[i]
    }

    // reihe: Zeitpunkt und Wert. Null bei weniger als 20 Renditen oder unter einem Monat.
    fun kennzahlen(reihe: List<Pair<Long, Double>>): Kennzahlen? {
        if (reihe.size < 21) return null
        val jahre = (reihe.last().first - reihe.first().first) / (365.25 * TAG)
        if (jahre < 30 / 365.25) return null
        val r = renditen(reihe)
        if (r.size < 20) return null
        val proJahr = r.size / jahre
        val m = r.average()
        val sd = sqrt(r.sumOf { (it - m) * (it - m) } / (r.size - 1))
        val schwankung = sd * sqrt(proJahr)
        val sharpe = if (schwankung > 1e-12) m * proJahr / schwankung else null
        val unten = sqrt(r.sumOf { minOf(it, 0.0).pow(2) } / r.size) * sqrt(proJahr)
        val sortino = if (unten > 1e-12) m * proJahr / unten else null
        var hoch = reihe.first().second
        var hochZeit = reihe.first().first
        var maxVerlust = 0.0
        var quadrate = 0.0
        var laengste = 0L
        for ((t, v) in reihe) {
            if (v >= hoch) {
                laengste = maxOf(laengste, ((t - hochZeit) / TAG).toLong())
                hoch = v
                hochZeit = t
            }
            val dd = v / hoch - 1
            maxVerlust = minOf(maxVerlust, dd)
            quadrate += (dd * 100).pow(2)
        }
        val offen = ((reihe.last().first - hochZeit) / TAG).toLong()
        val laufend = reihe.last().second < hoch && offen >= laengste
        laengste = maxOf(laengste, offen)
        val cagr = (reihe.last().second / reihe.first().second).pow(1 / jahre) - 1
        val calmar = if (jahre >= 0.9 && maxVerlust < -1e-9) cagr / abs(maxVerlust) else null
        val sortiert = r.sorted()
        val v95 = quantil(sortiert, 0.05)
        val schlecht = sortiert.filter { it <= v95 }
        return Kennzahlen(
            proJahr = proJahr,
            sharpe = sharpe,
            sortino = sortino,
            calmar = calmar,
            ulcer = sqrt(quadrate / reihe.size),
            var95 = if (r.size >= 40) maxOf(0.0, -v95) else null,
            es95 = if (r.size >= 40 && schlecht.isNotEmpty()) maxOf(0.0, -schlecht.average()) else null,
            rueckgangTage = laengste,
            laufend = laufend,
        )
    }

    // Alpha pro Jahr. Fuer jeden Abschnitt der Reihe zaehlt die Veraenderung des Markts im selben Abschnitt,
    // jeweils letzter Marktwert bis zum Zeitpunkt.
    fun vergleich(reihe: List<Pair<Long, Double>>, markt: List<Pair<Long, Double>>): Vergleich? {
        if (reihe.size < 21 || markt.size < 2) return null
        val ms = markt.sortedBy { it.first }
        val zeiten = ms.map { it.first }
        fun marktAm(t: Long): Double? {
            val i = zeiten.binarySearch(t).let { if (it >= 0) it else -it - 2 }
            return if (i >= 0) ms[i].second else null
        }
        val paare = ArrayList<Pair<Double, Double>>()
        for (i in 1 until reihe.size) {
            val (t0, v0) = reihe[i - 1]
            val (t1, v1) = reihe[i]
            if (t0 < ms.first().first) continue
            val m0 = marktAm(t0) ?: continue
            val m1 = marktAm(t1) ?: continue
            if (v0 <= 0 || m0 <= 0) continue
            paare += (v1 / v0 - 1) to (m1 / m0 - 1)
        }
        if (paare.size < 20) return null
        val jahre = (reihe.last().first - reihe.first().first) / (365.25 * TAG)
        val proJahr = if (jahre > 0) (reihe.size - 1) / jahre else return null
        val ra = paare.map { it.first }
        val rb = paare.map { it.second }
        val ma = ra.average()
        val mb = rb.average()
        val kov = paare.sumOf { (a, b) -> (a - ma) * (b - mb) } / (paare.size - 1)
        val vb = rb.sumOf { (it - mb).pow(2) } / (paare.size - 1)
        val va = ra.sumOf { (it - ma).pow(2) } / (paare.size - 1)
        if (vb <= 1e-14 || va <= 1e-14) return null
        val beta = kov / vb
        return Vergleich(beta, (ma - beta * mb) * proJahr, kov / sqrt(va * vb))
    }

    data class Verkaeufe(val anzahl: Int, val gewinne: Int, val trefferquote: Double, val gewinnfaktor: Double?, val mittelGewinn: Double?, val mittelVerlust: Double?, val erwartung: Double)

    // Abgeschlossene Verkaeufe nach realisiertem Ergebnis.
    fun verkaeufe(ergebnisse: List<Double>): Verkaeufe? {
        val e = ergebnisse.filter { abs(it) > 1e-9 }
        if (e.size < 3) return null
        val plus = e.filter { it > 0 }
        val minus = e.filter { it < 0 }
        val verlust = -minus.sum()
        return Verkaeufe(
            anzahl = e.size,
            gewinne = plus.size,
            trefferquote = plus.size.toDouble() / e.size,
            gewinnfaktor = if (verlust > 1e-9) plus.sum() / verlust else null,
            mittelGewinn = plus.takeIf { it.isNotEmpty() }?.average(),
            mittelVerlust = minus.takeIf { it.isNotEmpty() }?.average(),
            erwartung = e.average(),
        )
    }
}
