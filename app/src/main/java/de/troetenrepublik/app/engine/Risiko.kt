package de.troetenrepublik.app.engine

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

// Risikokennzahlen einer Wertreihe, gerechnet mit den Renditen zwischen den Punkten (taeglich oder woechentlich).
// Die Zahl der Punkte pro Jahr ergibt sich aus der Reihe. Sharpe und Sortino messen die Rendite ueber dem
// risikofreien Zins (zins pro Jahr, 0,02 = 2 Prozent), wie im Fachgebrauch.
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

    // Ein Wert je Kalendertag (UTC), der letzte des Tages. Ein Depot mit Werten an mehreren Boersen hat sonst mehrere
    // Punkte je Tag, und jede Rendite deckte nur einen Teil des Tages ab.
    fun taeglich(reihe: List<Pair<Long, Double>>): List<Pair<Long, Double>> {
        if (reihe.size < 2) return reihe
        val aus = ArrayList<Pair<Long, Double>>(reihe.size)
        for (p in reihe.sortedBy { it.first }) {
            if (aus.isNotEmpty() && Math.floorDiv(aus.last().first, TAG.toLong()) == Math.floorDiv(p.first, TAG.toLong())) aus[aus.lastIndex] = p
            else aus += p
        }
        return aus
    }

    private fun renditen(reihe: List<Pair<Long, Double>>): List<Double> =
        (1 until reihe.size).mapNotNull { i -> if (reihe[i - 1].second > 0 && reihe[i].second > 0) reihe[i].second / reihe[i - 1].second - 1 else null }

    // Die Renditen, aus denen kennzahlen rechnet (ein Wert je Kalendertag), etwa fuer eine Verteilung samt VaR und ES.
    fun tagesrenditen(reihe: List<Pair<Long, Double>>): List<Double> = renditen(taeglich(reihe))

    internal fun quantil(sortiert: List<Double>, q: Double): Double {
        if (sortiert.size == 1) return sortiert[0]
        val pos = q * (sortiert.size - 1)
        val i = floor(pos).toInt()
        val f = pos - i
        return if (i + 1 < sortiert.size) sortiert[i] * (1 - f) + sortiert[i + 1] * f else sortiert[i]
    }

    // reihe: Zeitpunkt und Wert. Null bei weniger als 20 Renditen oder unter einem Monat.
    fun kennzahlen(eingabe: List<Pair<Long, Double>>, zins: Double = 0.0): Kennzahlen? {
        val reihe = taeglich(eingabe)
        if (reihe.size < 21) return null
        val jahre = (reihe.last().first - reihe.first().first) / (365.25 * TAG)
        if (jahre < 30 / 365.25) return null
        val r = renditen(reihe)
        if (r.size < 20) return null
        val proJahr = r.size / jahre
        val m = r.average()
        val sd = sqrt(r.sumOf { (it - m) * (it - m) } / (r.size - 1))
        val schwankung = sd * sqrt(proJahr)
        val rf = zins / proJahr
        val sharpe = if (schwankung > 1e-12) (m - rf) * proJahr / schwankung else null
        val unten = sqrt(r.sumOf { minOf(it - rf, 0.0).pow(2) } / r.size) * sqrt(proJahr)
        val sortino = if (unten > 1e-12) (m - rf) * proJahr / unten else null
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
            // Erst ab 100 Renditen stuetzt sich der 5-%-Wert auf mehr als eine Handvoll Beobachtungen.
            var95 = if (r.size >= 100) maxOf(0.0, -v95) else null,
            es95 = if (r.size >= 100 && schlecht.isNotEmpty()) maxOf(0.0, -schlecht.average()) else null,
            rueckgangTage = laengste,
            laufend = laufend,
        )
    }

    // Alpha pro Jahr (nach Jensen, mit dem Zins auf Bargeld). Fuer jeden Abschnitt der Reihe zaehlt die Veraenderung
    // des Markts im selben Abschnitt, jeweils letzter Marktwert bis zum selben Kalendertag. So trifft ein Xetra-Punkt
    // am Morgen den US-Schluss desselben Tages und keinen Tag frueher.
    fun vergleich(eingabe: List<Pair<Long, Double>>, marktEingabe: List<Pair<Long, Double>>, zins: Double = 0.0): Vergleich? {
        val reihe = taeglich(eingabe)
        val ms = taeglich(marktEingabe)
        if (reihe.size < 21 || ms.size < 2) return null
        fun tag(t: Long) = Math.floorDiv(t, TAG.toLong())
        val tage = ms.map { tag(it.first) }
        fun marktAm(t: Long): Double? {
            val i = tage.binarySearch(tag(t)).let { if (it >= 0) it else -it - 2 }
            return if (i >= 0) ms[i].second else null
        }
        val paare = ArrayList<Pair<Double, Double>>()
        for (i in 1 until reihe.size) {
            val (t0, v0) = reihe[i - 1]
            val (t1, v1) = reihe[i]
            if (tag(t0) < tage.first()) continue
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
        val rf = zins / proJahr
        return Vergleich(beta, ((ma - rf) - beta * (mb - rf)) * proJahr, kov / sqrt(va * vb))
    }

    data class Verkaeufe(val anzahl: Int, val gewinne: Int, val trefferquote: Double, val gewinnfaktor: Double?, val mittelGewinn: Double?, val mittelVerlust: Double?, val erwartung: Double)

    // Abgeschlossene Verkaeufe nach realisiertem Ergebnis; Schwelle und Mindestzahl aus Verkaufsbilanz.
    fun verkaeufe(ergebnisse: List<Double>): Verkaeufe? = bilanz(ergebnisse, Verkaufsbilanz.SCHWELLE, Verkaufsbilanz.MIN_VERKAEUFE)

    // Dieselbe Rechnung fuer beliebige Ergebnisse (Euro oder Anteil des Einsatzes), auch fuer die Geschaefte im
    // Strategietest: Betraege bis schwelle sind weder Gewinn noch Verlust, null unter mindestens Ergebnissen.
    fun bilanz(ergebnisse: List<Double>, schwelle: Double, mindestens: Int): Verkaeufe? {
        val e = ergebnisse.filter { abs(it) > schwelle }
        if (e.size < mindestens) return null
        val plus = e.filter { it > 0 }
        val minus = e.filter { it < 0 }
        val verlust = -minus.sum()
        return Verkaeufe(
            anzahl = e.size,
            gewinne = plus.size,
            trefferquote = plus.size.toDouble() / e.size,
            gewinnfaktor = if (verlust > schwelle) plus.sum() / verlust else null,
            mittelGewinn = plus.takeIf { it.isNotEmpty() }?.average(),
            mittelVerlust = minus.takeIf { it.isNotEmpty() }?.average(),
            erwartung = e.average(),
        )
    }
}
