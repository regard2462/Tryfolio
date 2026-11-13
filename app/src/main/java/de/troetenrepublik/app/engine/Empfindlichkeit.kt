package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

// [Grosses Ganzes 0.73] Kursempfindlichkeit: wie die Wochenrenditen eines Werts mit grossen Einflussgroessen laufen
// (Markt, Halbleiterindex, Oel, Dollar, Zinsen, Kupfer, Gold, China), gerechnet auf dem Geraet aus Tageskerzen.
// Beschreibung der Vergangenheit, keine Vorhersage und kein Beleg fuer Ursache und Wirkung.
// Regeln:
// - Wochen aus Tageskerzen (letzter Kurs der Kalenderwoche, wie Risikobeitrag.renditen): Wochenkerzen von Yahoo sind je
//   Boerse verschieden datiert und passen nicht zusammen.
// - Der Markt kommt immer ins Modell, dazu hoechstens drei weitere Groessen, die einzeln |t| >= 2,71 erreichen
//   (Vorwaertsauswahl; sechs verwandte Groessen zugleich ergeben keine saubere Schaetzung).
// - Eine Groesse gilt als belegt, wenn |t| >= 2,71 im ganzen Zeitraum, das Vorzeichen in beiden Haelften gleich ist und
//   |t| in jeder Haelfte mindestens 2 betraegt. Wechselt das Vorzeichen, steht "wechselt"; sonst "kein belegter Zusammenhang".
object Empfindlichkeit {
    // symbol: Stellvertreter-Reihe bei Yahoo; differenz: Aenderung in Punkten statt Log-Rendite (Zinsen)
    enum class Faktor(val symbol: String, val differenz: Boolean) {
        MARKT("^GSPC", false), HALBLEITER("^SOX", false), OEL("CL=F", false), DOLLAR("DX-Y.NYB", false),
        ZINS10J("^TNX", true), KUPFER("HG=F", false), GOLD("GC=F", false), CHINA("000001.SS", false),
    }

    enum class Urteil { BELEGT, NICHT_BELEGT, WECHSELT }

    enum class Aussage { LAEUFT_MIT, LAEUFT_GEGEN, KEIN_BELEG, WECHSELT }

    // wert: Steigung (Rendite je Rendite der Groesse, bei Zinsen je Prozentpunkt); haelften: Steigung in erster und
    // zweiter Haelfte des Zeitraums; tHaelften: t-Werte dazu
    class Koeffizient(
        val faktor: Faktor, val wert: Double, val t: Double, val haelften: Pair<Double, Double>, val tHaelften: Pair<Double, Double>, val urteil: Urteil,
    )

    // von und bis: erster und letzter Tag der Wochen (Tage seit 1970); ohneBeleg: gepruefte Groessen ohne belegten Zusammenhang
    class Ergebnis(
        val wochen: Int, val von: Long, val bis: Long, val r2: Double, val r2Markt: Double, val r2Haelften: Pair<Double, Double>,
        val koeffizienten: List<Koeffizient>, val ohneBeleg: List<Faktor>,
    ) {
        val markt: Koeffizient? get() = koeffizienten.firstOrNull { it.faktor == Faktor.MARKT }
        val jahre: Double get() = wochen / 52.0
    }

    const val SCHWELLE_T = 2.71
    const val SCHWELLE_HAELFTE = 2.0
    const val MAX_WEITERE = 3
    const val MIN_WOCHEN = 104

    // Kalenderwoche (Montag bis Sonntag) eines Kerzenzeitpunkts
    private fun woche(t: Long): Long = Math.floorDiv(Tagesrueckblick.kerzenTag(t) + 3, 7L)

    // letzter Kurs je Woche seit `seit`
    private fun wochenKurse(k: List<Candle>, seit: Long): Map<Long, Double> {
        val m = HashMap<Long, Double>()
        for (c in k.sortedBy { it.t }) if (c.t >= seit && c.close > 0 && c.close.isFinite()) m[woche(c.t)] = c.close
        return m
    }

    private class Fit(val beta: DoubleArray, val t: DoubleArray, val r2: Double)

    // Kleinste Quadrate mit Achsenabschnitt auf den Zeilen von bis bis (ausschliesslich); null bei zu wenig Zeilen oder
    // singulaerer Matrix
    private fun ols(y: DoubleArray, xs: List<DoubleArray>, von: Int, bis: Int): Fit? {
        val n = bis - von
        val p = xs.size + 1
        if (n <= p + 10) return null
        fun x(j: Int, i: Int) = if (j == 0) 1.0 else xs[j - 1][i]
        val a = Array(p) { DoubleArray(p) }
        val b = DoubleArray(p)
        for (i in von until bis) {
            for (j in 0 until p) {
                val xj = x(j, i)
                b[j] += xj * y[i]
                for (k in 0 until p) a[j][k] += xj * x(k, i)
            }
        }
        val inv = invertieren(a) ?: return null
        val beta = DoubleArray(p) { j -> (0 until p).sumOf { k -> inv[j][k] * b[k] } }
        var sse = 0.0
        val mittel = (von until bis).sumOf { y[it] } / n
        var sst = 0.0
        for (i in von until bis) {
            var fit = 0.0
            for (j in 0 until p) fit += beta[j] * x(j, i)
            sse += (y[i] - fit) * (y[i] - fit)
            sst += (y[i] - mittel) * (y[i] - mittel)
        }
        val s2 = sse / (n - p)
        val t = DoubleArray(p) { j ->
            val se = sqrt((s2 * inv[j][j]).coerceAtLeast(0.0))
            if (se > 0.0) beta[j] / se else 0.0
        }
        return Fit(beta, t, if (sst > 0.0) 1.0 - sse / sst else 0.0)
    }

    // Gauss-Jordan mit Spaltenpivot; null, wenn die Matrix singulaer ist
    private fun invertieren(m: Array<DoubleArray>): Array<DoubleArray>? {
        val n = m.size
        val a = Array(n) { i -> DoubleArray(2 * n) { j -> if (j < n) m[i][j] else if (j - n == i) 1.0 else 0.0 } }
        for (c in 0 until n) {
            var p = c
            for (r in c + 1 until n) if (abs(a[r][c]) > abs(a[p][c])) p = r
            if (abs(a[p][c]) < 1e-14) return null
            val tmp = a[c]; a[c] = a[p]; a[p] = tmp
            val d = a[c][c]
            for (j in 0 until 2 * n) a[c][j] /= d
            for (r in 0 until n) if (r != c) {
                val f = a[r][c]
                if (f != 0.0) for (j in 0 until 2 * n) a[r][j] -= f * a[c][j]
            }
        }
        return Array(n) { i -> DoubleArray(n) { j -> a[i][n + j] } }
    }

    private fun vorzeichen(v: Double) = if (v > 0) 1 else if (v < 0) -1 else 0

    // Wochenrenditen aus Tageskerzen; Markt plus hoechstens drei weitere Groessen aus `weitere` (Vorwaertsauswahl).
    // null bei weniger als MIN_WOCHEN gemeinsamen Wochen oder ohne Marktreihe.
    fun rechnen(wert: List<Candle>, markt: List<Candle>, weitere: Map<Faktor, List<Candle>>, seit: Long): Ergebnis? {
        val reihen = LinkedHashMap<Faktor, Map<Long, Double>>()
        reihen[Faktor.MARKT] = wochenKurse(markt, seit)
        for ((f, k) in weitere) if (f != Faktor.MARKT) reihen[f] = wochenKurse(k, seit)
        val w = wochenKurse(wert, seit)
        var gemeinsam: Set<Long> = w.keys
        for (r in reihen.values) gemeinsam = gemeinsam.intersect(r.keys)
        val wochen = gemeinsam.sorted()
        if (wochen.size < MIN_WOCHEN + 1) return null
        val n = wochen.size - 1
        fun rendite(m: Map<Long, Double>, diff: Boolean) = DoubleArray(n) { i ->
            val a = m.getValue(wochen[i]); val b = m.getValue(wochen[i + 1])
            if (diff) b - a else ln(b / a)
        }
        val y = rendite(w, false)
        val x = reihen.mapValues { (f, m) -> rendite(m, f.differenz) }
        val mitte = n / 2

        // Vorwaertsauswahl: Markt immer, dann die Groesse mit dem groessten |t|, solange sie die Schwelle erreicht
        val modell = arrayListOf(Faktor.MARKT)
        val kandidaten = reihen.keys.filter { it != Faktor.MARKT }
        repeat(MAX_WEITERE) {
            var bester: Faktor? = null
            var bestT = 0.0
            for (k in kandidaten) {
                if (k in modell) continue
                val f = ols(y, (modell + k).map { x.getValue(it) }, 0, n) ?: continue
                val t = abs(f.t[modell.size + 1])
                if (t > bestT) { bestT = t; bester = k }
            }
            if (bester != null && bestT >= SCHWELLE_T) modell += bester
        }

        val xs = modell.map { x.getValue(it) }
        val voll = ols(y, xs, 0, n) ?: return null
        val h1 = ols(y, xs, 0, mitte) ?: return null
        val h2 = ols(y, xs, mitte, n) ?: return null
        val nurMarkt = ols(y, listOf(x.getValue(Faktor.MARKT)), 0, n) ?: return null
        val koeff = modell.mapIndexed { i, f ->
            val b = voll.beta[i + 1]; val t = voll.t[i + 1]
            val b1 = h1.beta[i + 1]; val b2 = h2.beta[i + 1]
            val t1 = h1.t[i + 1]; val t2 = h2.t[i + 1]
            val urteil = when {
                vorzeichen(b1) != vorzeichen(b2) -> Urteil.WECHSELT
                abs(t) >= SCHWELLE_T && abs(t1) >= SCHWELLE_HAELFTE && abs(t2) >= SCHWELLE_HAELFTE -> Urteil.BELEGT
                else -> Urteil.NICHT_BELEGT
            }
            Koeffizient(f, b, t, b1 to b2, t1 to t2, urteil)
        }
        val von = wochen.first() * 7 - 3
        val bis = wochen.last() * 7 + 3
        return Ergebnis(n, von, bis, voll.r2, nurMarkt.r2, h1.r2 to h2.r2, koeff, kandidaten.filter { it !in modell })
    }

    fun aussage(k: Koeffizient): Aussage = when (k.urteil) {
        Urteil.BELEGT -> if (k.wert > 0) Aussage.LAEUFT_MIT else Aussage.LAEUFT_GEGEN
        Urteil.WECHSELT -> Aussage.WECHSELT
        Urteil.NICHT_BELEGT -> Aussage.KEIN_BELEG
    }

    // Belegte Zusammenhaenge ausser dem Markt
    fun belegt(e: Ergebnis): List<Koeffizient> = e.koeffizienten.filter { it.faktor != Faktor.MARKT && it.urteil == Urteil.BELEGT }
}
