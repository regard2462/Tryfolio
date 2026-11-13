package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

// Effizienzgrenze des eigenen Depots (0.78): Mittelwert-Varianz-Rechnung ohne Leerverkaeufe (Gewichte 0 bis 100 %,
// Summe 100 %). Wochenrenditen und Kovarianz kommen aus Risikobeitrag; die Rendite je Wert ist die Rendite pro Jahr
// ueber die gemeinsamen Wochen, die Rendite eines Depots das gewichtete Mittel daraus.
// Verfahren: Fuer einen Risikopreis lambda >= 0 wird 1/2 w'Sw - lambda * mu'w ueber alle zulaessigen Gewichte minimiert
// (projizierter Gradient mit Beschleunigung, Projektion auf den Simplex). lambda = 0 ergibt die geringste Schwankung,
// wachsendes lambda laeuft die Grenze nach oben bis zum Wert mit der hoechsten Rendite. Fester Start, kein Zufall.
// Alles stammt aus vergangenen Kursen: Die Grenze ist an den Zeitraum angepasst und keine Empfehlung.
object Effizienzgrenze {
    // Wie Risikobeitrag: Wochen im Jahr
    private const val PERIODEN_JE_JAHR = 52.0

    const val MIN_WERTE = 2
    // So viele Werte gehen hoechstens ein (die groessten)
    const val MAX_WERTE = 15

    // Stuetzstellen des Risikopreises fuer die Grenze
    private const val STUFEN = 60
    private const val SCHRITTE = 4000
    private const val GENAU = 1e-11

    // Schwankung und Rendite pro Jahr (Anteile) mit den Gewichten dazu
    class Punkt(val schwankung: Double, val rendite: Double, val gewichte: DoubleArray)

    class Ergebnis(
        // Von der geringsten Schwankung bis zur hoechsten Rendite, Schwankung und Rendite steigen
        val grenze: List<Punkt>,
        val depot: Punkt,
        val minimal: Punkt,
        // Hoechste Rendite je Schwankung (Sharpe-Quote ohne Zinsabzug); null, wenn keine Mischung eine positive Rendite hatte
        val sharpe: Punkt?,
        val gleich: Punkt,
        // Punkt der Grenze mit der Rendite des Depots; null, wenn das Depot unter der Rendite von minimal liegt
        val gleicheRendite: Punkt?,
        // Die einzelnen Werte
        val einzeln: List<Punkt>,
        val wochen: Int,
    )

    // Rendite pro Jahr je Reihe aus den Wochenrenditen (logarithmiert), wie eine Rendite pro Jahr aus Anfang und Ende.
    fun erwartung(r: List<DoubleArray>): DoubleArray =
        DoubleArray(r.size) { i -> if (r[i].isEmpty()) 0.0 else exp(r[i].average() * PERIODEN_JE_JAHR) - 1.0 }

    // Naechster Punkt mit Gewichten von 0 bis 1 und Summe 1.
    fun aufSimplex(v: DoubleArray): DoubleArray {
        val u = v.sortedArrayDescending()
        var summe = 0.0
        var theta = 0.0
        for (i in u.indices) {
            summe += u[i]
            val t = (summe - 1.0) / (i + 1)
            if (u[i] - t > 0.0) theta = t
        }
        return DoubleArray(v.size) { maxOf(v[it] - theta, 0.0) }
    }

    private fun mal(cov: Array<DoubleArray>, w: DoubleArray): DoubleArray = DoubleArray(w.size) { i -> var s = 0.0; for (j in w.indices) s += cov[i][j] * w[j]; s }

    private fun skalar(a: DoubleArray, b: DoubleArray): Double { var s = 0.0; for (i in a.indices) s += a[i] * b[i]; return s }

    // Minimiert 1/2 w'Sw - lambda * mu'w auf dem Simplex, beginnend bei start.
    internal fun loesen(mu: DoubleArray, cov: Array<DoubleArray>, lambda: Double, start: DoubleArray): DoubleArray {
        val n = mu.size
        // Obere Schranke fuer den groessten Eigenwert: groesste Zeilensumme der Betraege
        val schranke = (0 until n).maxOf { i -> (0 until n).sumOf { j -> abs(cov[i][j]) } }
        if (!(schranke > 0.0)) return start.copyOf()
        var x = start.copyOf()
        var y = x.copyOf()
        var t = 1.0
        repeat(SCHRITTE) {
            val g = mal(cov, y)
            val neu = aufSimplex(DoubleArray(n) { y[it] - (g[it] - lambda * mu[it]) / schranke })
            var aenderung = 0.0
            var rueck = 0.0
            for (i in 0 until n) {
                aenderung = maxOf(aenderung, abs(neu[i] - x[i]))
                rueck += (y[i] - neu[i]) * (neu[i] - x[i])
            }
            // Laeuft der Schwung gegen den Abstieg, beginnt er neu
            val tNeu = if (rueck > 0.0) 1.0 else (1.0 + sqrt(1.0 + 4.0 * t * t)) / 2.0
            val schwung = if (rueck > 0.0) 0.0 else (t - 1.0) / tNeu
            y = DoubleArray(n) { neu[it] + schwung * (neu[it] - x[it]) }
            x = neu
            t = tNeu
            if (aenderung < GENAU && rueck <= 0.0) return x
        }
        return x
    }

    private fun punkt(w: DoubleArray, mu: DoubleArray, cov: Array<DoubleArray>): Punkt =
        Punkt(sqrt(maxOf(Risikobeitrag.varianz(w, cov), 0.0)), skalar(w, mu), w)

    // gewichte: heutige Gewichte (Summe 1), mu: Rendite pro Jahr je Wert, cov: Kovarianz pro Jahr. Null bei weniger
    // als zwei Werten, unpassenden Groessen oder ohne Schwankung.
    fun rechnen(gewichte: DoubleArray, mu: DoubleArray, cov: Array<DoubleArray>): Ergebnis? {
        val n = mu.size
        if (n < MIN_WERTE || gewichte.size != n || cov.size != n || cov.any { it.size != n }) return null
        if (mu.any { !it.isFinite() } || cov.any { z -> z.any { !it.isFinite() } }) return null
        val spur = (0 until n).sumOf { cov[it][it] }
        if (!(spur > 1e-14)) return null
        // Winziger Zuschlag auf der Diagonale: eindeutige Loesung auch bei fast gleichen Werten
        val s = Array(n) { i -> DoubleArray(n) { j -> cov[i][j] + if (i == j) spur / n * 1e-9 else 0.0 } }
        val gleichGewichte = DoubleArray(n) { 1.0 / n }
        val best = (0 until n).maxByOrNull { mu[it] }!!
        val spanne = mu.max() - mu.min()

        val minimalW = loesen(mu, s, 0.0, gleichGewichte)
        val pfad = ArrayList<Pair<Double, DoubleArray>>()
        pfad += 0.0 to minimalW
        if (spanne > 1e-9) {
            // Risikopreise geometrisch um die Groessenordnung Varianz durch Renditespanne
            val mitte = spur / n / spanne
            var w = minimalW
            for (k in 0 until STUFEN) {
                val lambda = mitte * exp(ln(1e-3) + (ln(1e3) - ln(1e-3)) * k / (STUFEN - 1))
                w = loesen(mu, s, lambda, w)
                pfad += lambda to w
                if (w[best] > 1.0 - 1e-7) break
            }
        }
        // Grenze: Schwankung und Rendite steigen, doppelte Punkte fallen weg, am Ende der Wert mit der hoechsten Rendite
        val grenze = ArrayList<Punkt>()
        for ((_, w) in pfad) {
            val p = punkt(w, mu, cov)
            val l = grenze.lastOrNull()
            if (l == null || (p.rendite > l.rendite + 1e-9 && p.schwankung > l.schwankung - 1e-12)) grenze += p
        }
        val spitze = punkt(DoubleArray(n) { if (it == best) 1.0 else 0.0 }, mu, cov)
        if (spitze.rendite > grenze.last().rendite + 1e-9 && spitze.schwankung >= grenze.last().schwankung) grenze += spitze
        val minimal = grenze.first()

        fun zuLambda(lambda: Double, start: DoubleArray) = punkt(loesen(mu, s, lambda, start), mu, cov)
        fun quote(p: Punkt) = if (p.schwankung > 1e-12) p.rendite / p.schwankung else Double.NEGATIVE_INFINITY

        // Hoechste Rendite je Schwankung: bester Punkt des Pfads, zwischen seinen Nachbarn verfeinert
        var sharpe = pfad.map { punkt(it.second, mu, cov) }.plus(spitze).maxByOrNull { quote(it) }!!
        val iBest = pfad.indexOfFirst { quote(punkt(it.second, mu, cov)) >= quote(sharpe) - 1e-15 }
        if (iBest >= 0 && pfad.size > 1) {
            var lo = pfad[maxOf(iBest - 1, 0)].first
            var hi = pfad[minOf(iBest + 1, pfad.lastIndex)].first
            val start = pfad[iBest].second
            repeat(30) {
                val a = lo + (hi - lo) / 3.0
                val b = hi - (hi - lo) / 3.0
                val pa = zuLambda(a, start)
                val pb = zuLambda(b, start)
                if (quote(pa) < quote(pb)) lo = a else hi = b
                val besser = if (quote(pa) >= quote(pb)) pa else pb
                if (quote(besser) > quote(sharpe)) sharpe = besser
            }
        }

        val summe = gewichte.sum()
        if (!(summe > 0.0) || gewichte.any { !it.isFinite() || it < 0.0 }) return null
        val depot = punkt(DoubleArray(n) { gewichte[it] / summe }, mu, cov)

        // Punkt der Grenze mit der Rendite des Depots: Die Rendite steigt mit dem Risikopreis, also Halbierung
        var gleiche: Punkt? = null
        if (depot.rendite > minimal.rendite + 1e-9 && pfad.size > 1) {
            val renditen = pfad.map { skalar(it.second, mu) }
            val i = renditen.indexOfFirst { it >= depot.rendite }
            if (i < 0) {
                // Ueber dem letzten Pfadpunkt: Mischung mit dem Wert der hoechsten Rendite
                val letzter = pfad.last().second
                val rl = renditen.last()
                val anteil = if (spitze.rendite - rl > 1e-12) ((depot.rendite - rl) / (spitze.rendite - rl)).coerceIn(0.0, 1.0) else 1.0
                gleiche = punkt(DoubleArray(n) { letzter[it] * (1 - anteil) + (if (it == best) anteil else 0.0) }, mu, cov)
            } else if (i > 0) {
                var lo = pfad[i - 1].first
                var hi = pfad[i].first
                var w = pfad[i - 1].second
                var treffer = punkt(pfad[i].second, mu, cov)
                repeat(40) {
                    val m = (lo + hi) / 2.0
                    w = loesen(mu, s, m, w)
                    val p = punkt(w, mu, cov)
                    if (p.rendite >= depot.rendite) { hi = m; treffer = p } else lo = m
                }
                // An einem Knick springt die Rendite: Rest ueber die Mischung der beiden Seiten
                val unten = zuLambda(lo, w)
                gleiche = if (treffer.rendite - depot.rendite > 1e-7 && treffer.rendite - unten.rendite > 1e-12) {
                    val anteil = ((depot.rendite - unten.rendite) / (treffer.rendite - unten.rendite)).coerceIn(0.0, 1.0)
                    punkt(DoubleArray(n) { unten.gewichte[it] * (1 - anteil) + treffer.gewichte[it] * anteil }, mu, cov)
                } else treffer
            }
            // Das Depot selbst ist zulaessig; schlechter als das Depot kann die Grenze nicht sein
            if (gleiche != null && gleiche.schwankung > depot.schwankung) gleiche = depot
        }

        return Ergebnis(
            grenze = grenze, depot = depot, minimal = minimal, sharpe = sharpe.takeIf { quote(it) > 0.0 },
            gleich = punkt(gleichGewichte, mu, cov), gleicheRendite = gleiche,
            einzeln = (0 until n).map { i -> punkt(DoubleArray(n) { if (it == i) 1.0 else 0.0 }, mu, cov) }, wochen = 0,
        )
    }

    // Werte mit Kursreihen reihen und Betraegen werte (gleiche Reihenfolge), Kurse seit since.
    fun berechnen(werte: List<Double>, reihen: List<List<Candle>>, since: Long): Ergebnis? {
        if (werte.size != reihen.size || werte.size < MIN_WERTE || werte.size > MAX_WERTE) return null
        val r = Risikobeitrag.renditen(reihen, since) ?: return null
        val e = rechnen(werte.toDoubleArray(), erwartung(r), Risikobeitrag.kovarianz(r)) ?: return null
        return Ergebnis(e.grenze, e.depot, e.minimal, e.sharpe, e.gleich, e.gleicheRendite, e.einzeln, r.first().size)
    }
}
