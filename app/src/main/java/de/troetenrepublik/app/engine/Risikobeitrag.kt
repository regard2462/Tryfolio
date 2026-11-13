package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

// Risikobeitrag je Position: Aus den Wochenrenditen entsteht die Kovarianzmatrix, daraus der Anteil jeder Position an
// der Schwankung des Depots (Gewicht mal Grenzbeitrag geteilt durch die Depotvarianz). Die Anteile ergeben zusammen
// 100 %. Dazu ein Vorschlag mit gleichem Risikoanteil je Position (Risikoparitaet). Gewichte als Anteil (0,05 = 5 %).
object Risikobeitrag {

    private const val DAY = 86_400_000L
    // Wochen im Jahr. Mit Wochen gilt dieselbe Zahl fuer Aktien und Krypto, und Boersen mit verschiedenen
    // Schlusszeiten passen zusammen.
    private const val PERIODEN_JE_JAHR = 52.0

    // Weniger gemeinsame Wochen reichen nicht fuer eine Schaetzung.
    const val MIN_TAGE = 26

    // Log-Renditen je Reihe zwischen den Kalenderwochen (letzter Kurs der Woche, Wochen ab Montag), in denen alle
    // Reihen einen Kurs haben, seit since. null bei weniger als MIN_TAGE gemeinsamen Renditen.
    fun renditen(reihen: List<List<Candle>>, since: Long): List<DoubleArray>? {
        if (reihen.isEmpty()) return null
        val karten = reihen.map { r ->
            val m = HashMap<Long, Double>()
            for (c in r.sortedBy { it.t }) if (c.t >= since && c.close > 0 && c.close.isFinite()) m[Math.floorDiv(Tagesrueckblick.kerzenTag(c.t) + 3, 7L)] = c.close
            m
        }
        val tage = karten.map { it.keys as Set<Long> }.reduce { a, b -> a.intersect(b) }.sorted()
        if (tage.size < MIN_TAGE + 1) return null
        return karten.map { m -> DoubleArray(tage.size - 1) { i -> ln(m.getValue(tage[i + 1]) / m.getValue(tage[i])) } }
    }

    // Kovarianzmatrix der Renditen, aufs Jahr hochgerechnet (mal 52 Wochen).
    fun kovarianz(r: List<DoubleArray>): Array<DoubleArray> {
        val n = r.size
        val t = r.minOfOrNull { it.size } ?: 0
        val mittel = DoubleArray(n) { i -> if (t == 0) 0.0 else (0 until t).sumOf { r[i][it] } / t }
        return Array(n) { i ->
            DoubleArray(n) { j ->
                if (t < 2) 0.0 else (0 until t).sumOf { k -> (r[i][k] - mittel[i]) * (r[j][k] - mittel[j]) } / (t - 1) * PERIODEN_JE_JAHR
            }
        }
    }

    // Varianz des Depots mit den Gewichten w.
    fun varianz(w: DoubleArray, cov: Array<DoubleArray>): Double {
        var v = 0.0
        for (i in w.indices) for (j in w.indices) v += w[i] * w[j] * cov[i][j]
        return v
    }

    // Anteil jeder Position an der Depotvarianz (Summe 1); null ohne Schwankung.
    fun anteile(w: DoubleArray, cov: Array<DoubleArray>): DoubleArray? {
        val v = varianz(w, cov)
        if (!(v > 1e-18) || !v.isFinite()) return null
        return DoubleArray(w.size) { i -> w[i] * w.indices.sumOf { j -> cov[i][j] * w[j] } / v }
    }

    // Gewichte mit gleichem Risikoanteil je Position, iterativ (zyklischer Koordinatenabstieg): Je Position wird
    // die Gleichung x_i * (Sigma x)_i = 1/n geloest, bis sich nichts mehr aendert. Ergebnis auf 100 % gebracht.
    // null, wenn eine Position keine Schwankung hat oder die Rechnung nicht zur Ruhe kommt.
    fun risikoparitaet(cov: Array<DoubleArray>, schritte: Int = 500): DoubleArray? {
        val n = cov.size
        if (n == 0 || (0 until n).any { !(cov[it][it] > 1e-18) }) return null
        val b = 1.0 / n
        val x = DoubleArray(n) { 1.0 / sqrt(cov[it][it]) }
        repeat(schritte) {
            var aenderung = 0.0
            for (i in 0 until n) {
                var c = 0.0
                for (j in 0 until n) if (j != i) c += cov[i][j] * x[j]
                val neu = (-c + sqrt(c * c + 4.0 * cov[i][i] * b)) / (2.0 * cov[i][i])
                aenderung = maxOf(aenderung, abs(neu - x[i]) / maxOf(abs(x[i]), 1e-12))
                x[i] = neu
            }
            if (aenderung < 1e-10) {
                val s = x.sum()
                return if (s > 0 && x.all { it.isFinite() && it > 0 }) DoubleArray(n) { x[it] / s } else null
            }
        }
        return null
    }

    // Ergebnis fuer die Anzeige: Gewichte, Risikoanteile, Schwankung des Depots pro Jahr (Anteil) und, falls
    // berechenbar, der Vorschlag mit gleichem Risikoanteil.
    class Ergebnis(val gewichte: DoubleArray, val risiko: DoubleArray, val schwankung: Double, val paritaet: DoubleArray?)

    // Werte mit Kursreihen reihen und Betraegen werte (gleiche Reihenfolge).
    fun berechnen(werte: List<Double>, reihen: List<List<Candle>>, since: Long): Ergebnis? {
        if (werte.size != reihen.size || werte.size < 2) return null
        val summe = werte.sum()
        if (!(summe > 0)) return null
        val r = renditen(reihen, since) ?: return null
        val cov = kovarianz(r)
        val w = DoubleArray(werte.size) { werte[it] / summe }
        val a = anteile(w, cov) ?: return null
        return Ergebnis(w, a, sqrt(varianz(w, cov)), risikoparitaet(cov))
    }

    // Die Position, deren Risikoanteil ihr Gewicht am deutlichsten uebersteigt (mindestens das 1,5-Fache und
    // 5 Punkte mehr); null, wenn keine heraussticht.
    fun auffaellig(e: Ergebnis): Int? =
        e.gewichte.indices.filter { e.risiko[it] >= e.gewichte[it] * 1.5 && e.risiko[it] - e.gewichte[it] >= 0.05 }
            .maxByOrNull { e.risiko[it] - e.gewichte[it] }
}
