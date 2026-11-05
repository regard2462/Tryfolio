package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import kotlin.math.E
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

// Strategie-Labor (seit 5.91): einfache Signalregeln auf Tageskerzen im Rueckblick, mit Spread je Seite und
// Ordergebuehr, verglichen mit Kaufen und Halten. Das Signal entsteht am Tagesschluss, gehandelt wird zur
// Eroeffnung des naechsten Handelstags; so kennt keine Regel den Kurs, zu dem sie handelt. Die Indikatoren
// laufen ueber die ganze Reihe, damit sie zu Beginn des Zeitraums schon eingeschwungen sind. Ohne Steuern.
object StrategieLabor {

    const val START = 10_000.0
    const val GEBUEHR = 1.0

    // Anteil der Kerzen im Lernzeitraum, der Rest ist der Pruefzeitraum.
    const val LERNANTEIL = 0.7

    private const val JAHR_MS = 365.25 * 86_400_000.0

    enum class Art { HALTEN, SMA, KREUZ, RSI, MACD, DONCHIAN, BOLLINGER }

    // Parameter je Art. SMA: Laenge a. KREUZ: kurzer Durchschnitt a, langer b. RSI: Laenge n, Kauf unter a,
    // Verkauf ueber b. DONCHIAN: Kauf ueber dem Hoch der letzten a Tage, Verkauf unter dem Tief der letzten b Tage.
    // BOLLINGER: Baender ueber a Tage mit b Standardabweichungen, Kauf unter dem unteren Band, Verkauf ueber der Mitte.
    data class Regel(val art: Art, val a: Int = 0, val b: Int = 0, val n: Int = 0)

    // Die Regeln des Labors in fester Reihenfolge, Kaufen und Halten zuerst.
    val REGELN = listOf(
        Regel(Art.HALTEN),
        Regel(Art.SMA, 200),
        Regel(Art.KREUZ, 50, 200),
        Regel(Art.RSI, 30, 70, 14),
        Regel(Art.MACD),
        Regel(Art.DONCHIAN, 20, 10),
        Regel(Art.BOLLINGER, 20, 2),
    )

    // spreadBps: ganzer Abstand von Geld- und Briefkurs in Basispunkten, wie in den Einstellungen.
    data class Kosten(val spreadBps: Double = 0.0, val gebuehr: Double = GEBUEHR)

    // renditeJahr, maxRueckgang, treffer und imMarkt als Anteil (0,1 = 10 %). renditen: Veraenderung des
    // Depotwerts je Handelstag. sharpe je Handelstag (Mittel durch Standardabweichung, ohne Zins).
    data class Ergebnis(
        val endwert: Double,
        val renditeJahr: Double?,
        val maxRueckgang: Double,
        val trades: Int,
        val treffer: Double?,
        val imMarkt: Double,
        val verlauf: List<Pair<Long, Double>>,
        val renditen: List<Double>,
        val sharpe: Double,
        val jahre: Double,
    )

    // Kurse mit zwischengespeicherten Indikatoren, damit viele Varianten schnell rechnen.
    class Daten(val kerzen: List<Candle>) {
        val schluss: List<Double> = kerzen.map { it.close }
        val hoch: List<Double> = kerzen.map { if (it.high.isFinite() && it.high > 0) it.high else it.close }
        val tief: List<Double> = kerzen.map { if (it.low.isFinite() && it.low > 0) it.low else it.close }
        private val smaSpeicher = HashMap<Int, List<Double?>>()
        private val rsiSpeicher = HashMap<Int, List<Double?>>()
        private val donchianSpeicher = HashMap<Int, Indikatoren.Baender>()
        private val bollingerSpeicher = HashMap<Pair<Int, Int>, Indikatoren.Baender>()
        val macd: Indikatoren.Macd by lazy { Indikatoren.macd(schluss) }

        fun sma(n: Int): List<Double?> = smaSpeicher.getOrPut(n) { Indikatoren.sma(schluss, n) }
        fun rsi(n: Int): List<Double?> = rsiSpeicher.getOrPut(n) { Indikatoren.rsi(schluss, n) }
        fun donchian(n: Int): Indikatoren.Baender = donchianSpeicher.getOrPut(n) { Indikatoren.donchian(hoch, tief, n) }
        fun bollinger(n: Int, k: Int): Indikatoren.Baender = bollingerSpeicher.getOrPut(n to k) { Indikatoren.bollinger(schluss, n, k.toDouble()) }

        // Ausfuehrungskurs am Tag i: Eroeffnung, ohne sie der Schluss.
        fun eroeffnung(i: Int): Double = kerzen[i].open.takeIf { it.isFinite() && it > 0 } ?: kerzen[i].close
    }

    // Gewuenschte Lage nach dem Schluss jedes Tags: true = investiert.
    fun ziel(d: Daten, r: Regel): BooleanArray {
        val n = d.schluss.size
        val z = BooleanArray(n)
        var drin = false
        for (i in 0 until n) {
            val c = d.schluss[i]
            drin = when (r.art) {
                Art.HALTEN -> true
                Art.SMA -> d.sma(r.a)[i]?.let { c > it } ?: false
                Art.KREUZ -> {
                    val k = d.sma(r.a)[i]
                    val l = d.sma(r.b)[i]
                    k != null && l != null && k > l
                }
                Art.RSI -> {
                    val x = d.rsi(r.n)[i]
                    when {
                        x == null -> drin
                        !drin && x < r.a -> true
                        drin && x > r.b -> false
                        else -> drin
                    }
                }
                Art.MACD -> {
                    val m = d.macd.linie[i]
                    val s = d.macd.signal[i]
                    m != null && s != null && m > s
                }
                Art.DONCHIAN -> {
                    // Hoch und Tief der Tage davor, ohne den heutigen
                    val o = if (i > 0) d.donchian(r.a).oben[i - 1] else null
                    val u = if (i > 0) d.donchian(r.b).unten[i - 1] else null
                    when {
                        !drin && o != null && c > o -> true
                        drin && u != null && c < u -> false
                        else -> drin
                    }
                }
                Art.BOLLINGER -> {
                    val b = d.bollinger(r.a, r.b)
                    val u = b.unten[i]
                    val m = b.mitte[i]
                    when {
                        !drin && u != null && c < u -> true
                        drin && m != null && c > m -> false
                        else -> drin
                    }
                }
            }
            z[i] = drin
        }
        return z
    }

    // Spielt die Regel im Bereich von (einschliesslich) bis bis (ausschliesslich) mit frischem Startkapital durch.
    // Am Tag i gilt die Lage vom Schluss des Vortags; liegt die beim Start schon auf „investiert“, wird zur ersten
    // Eroeffnung gekauft. Eine am Ende offene Position zaehlt zum Schlusskurs und als Trade. Mit leicht=true bleiben
    // verlauf und renditen leer, damit viele Varianten gleichzeitig den Speicher nicht fuellen.
    fun simulieren(d: Daten, ziel: BooleanArray, von: Int, bis: Int, kosten: Kosten, start: Double = START, leicht: Boolean = false): Ergebnis? {
        if (von < 0 || bis > d.schluss.size || bis - von < 2) return null
        var bar = start
        var stueck = 0.0
        var einsatz = 0.0
        var trades = 0
        var gewinner = 0
        var tageDrin = 0
        val werte = DoubleArray(bis - von)
        for (i in von until bis) {
            val soll = if (i == 0) ziel[0] else ziel[i - 1]
            val p = d.eroeffnung(i)
            if (soll && stueck == 0.0) {
                val geld = bar - kosten.gebuehr
                val kurs = Pricing.ask(p, kosten.spreadBps)
                if (geld > 0 && kurs > 0) {
                    stueck = geld / kurs
                    einsatz = bar
                    bar = 0.0
                    trades++
                }
            } else if (!soll && stueck > 0) {
                bar = stueck * Pricing.bid(p, kosten.spreadBps) - kosten.gebuehr
                if (bar > einsatz) gewinner++
                stueck = 0.0
            }
            if (stueck > 0) tageDrin++
            werte[i - von] = bar + stueck * d.schluss[i]
        }
        if (stueck > 0 && stueck * Pricing.bid(d.schluss[bis - 1], kosten.spreadBps) - kosten.gebuehr > einsatz) gewinner++
        val endwert = werte.last()
        val jahre = (d.kerzen[bis - 1].t - d.kerzen[von].t) / JAHR_MS
        val renditeJahr = if (jahre < 0.25) null else if (endwert <= 0) -1.0 else (endwert / start).pow(1.0 / jahre) - 1.0
        var hoechst = start
        var rueckgang = 0.0
        val renditen = ArrayList<Double>(werte.size)
        var vorher = start
        for (w in werte) {
            hoechst = max(hoechst, w)
            if (hoechst > 0) rueckgang = max(rueckgang, 1.0 - w / hoechst)
            renditen += if (vorher > 0) w / vorher - 1.0 else 0.0
            vorher = w
        }
        return Ergebnis(
            endwert = endwert,
            renditeJahr = renditeJahr,
            maxRueckgang = rueckgang,
            trades = trades,
            treffer = if (trades > 0) gewinner.toDouble() / trades else null,
            imMarkt = tageDrin.toDouble() / werte.size,
            verlauf = if (leicht) emptyList() else (von until bis).map { d.kerzen[it].t to werte[it - von] },
            renditen = if (leicht) emptyList() else renditen,
            sharpe = sharpe(renditen),
            jahre = jahre,
        )
    }

    // Mittel durch Standardabweichung je Zeitschritt; 0 ohne Schwankung.
    fun sharpe(r: List<Double>): Double {
        if (r.size < 2) return 0.0
        val m = r.average()
        val sd = sqrt(r.sumOf { (it - m) * (it - m) } / (r.size - 1))
        return if (sd < 1e-12) 0.0 else m / sd
    }

    // Grenze zwischen Lern- und Pruefzeitraum im Bereich ab bis Ende.
    fun teilung(ab: Int, n: Int): Int = ab + ((n - ab) * LERNANTEIL).toInt()

    data class Bericht(val regel: Regel, val gesamt: Ergebnis, val lern: Ergebnis, val pruef: Ergebnis)

    // Alle Regeln ab Kerze ab: ganzer Zeitraum, Lernzeitraum (erste 70 %) und Pruefzeitraum (letzte 30 %),
    // jeder Teil mit frischem Startkapital.
    fun pruefen(kerzen: List<Candle>, ab: Int, kosten: Kosten, regeln: List<Regel> = REGELN): List<Bericht> {
        val d = Daten(kerzen)
        val n = kerzen.size
        if (ab < 0 || n - ab < 20) return emptyList()
        val s = teilung(ab, n)
        return regeln.mapNotNull { r ->
            val z = ziel(d, r)
            val g = simulieren(d, z, ab, n, kosten) ?: return@mapNotNull null
            val l = simulieren(d, z, ab, s, kosten) ?: return@mapNotNull null
            val p = simulieren(d, z, s, n, kosten) ?: return@mapNotNull null
            Bericht(r, g, l, p)
        }
    }

    // Backtest-Lotterie

    enum class Familie { DURCHSCHNITT, KREUZ, RSI, ALLE }

    // Varianten einer Familie: Durchschnitte 5 bis 250 Tage in Fuenferschritten, Kreuze aus kurzem (5 bis 100) und
    // langem Durchschnitt (20 bis 250, mindestens 10 Tage laenger), RSI mit 7, 14, 21 Tagen und Schwellen 10 bis 40
    // bzw. 60 bis 90.
    fun varianten(f: Familie): List<Regel> = when (f) {
        Familie.DURCHSCHNITT -> (5..250 step 5).map { Regel(Art.SMA, it) }
        Familie.KREUZ -> (5..100 step 5).flatMap { k -> (20..250 step 10).filter { it >= k + 10 }.map { l -> Regel(Art.KREUZ, k, l) } }
        Familie.RSI -> listOf(7, 14, 21).flatMap { n -> (10..40 step 5).flatMap { a -> (60..90 step 5).map { b -> Regel(Art.RSI, a, b, n) } } }
        Familie.ALLE -> varianten(Familie.DURCHSCHNITT) + varianten(Familie.KREUZ) + varianten(Familie.RSI)
    }

    // anzahl: getestete Varianten. dsr: Deflated Sharpe Ratio der besten Variante (0 bis 1), sr0: erwartetes Maximum
    // der Sharpe-Quoten aus reinem Zufall, beide je Handelstag.
    data class Lotterie(
        val anzahl: Int,
        val beste: Regel,
        val besteLern: Ergebnis,
        val bestePruef: Ergebnis,
        val haltenLern: Ergebnis,
        val haltenPruef: Ergebnis,
        val dsr: Double,
        val sr0: Double,
    )

    // Testet alle Varianten im Lernzeitraum, waehlt die mit der hoechsten Sharpe-Quote und rechnet sie im
    // Pruefzeitraum nach. Die Varianten rechnen leicht (ohne verlauf und renditen), abbruch wird je Variante
    // aufgerufen und kann den Lauf abbrechen; nur die beste Variante wird voll gerechnet.
    fun lotterie(kerzen: List<Candle>, ab: Int, kosten: Kosten, familie: Familie, abbruch: () -> Unit = {}): Lotterie? {
        val d = Daten(kerzen)
        val n = kerzen.size
        if (ab < 0 || n - ab < 60) return null
        val s = teilung(ab, n)
        val alle = varianten(familie).mapNotNull { r ->
            abbruch()
            val z = ziel(d, r)
            simulieren(d, z, ab, s, kosten, leicht = true)?.let { Triple(r, z, it) }
        }
        if (alle.isEmpty()) return null
        val (beste, z, _) = alle.maxBy { it.third.sharpe }
        val lern = simulieren(d, z, ab, s, kosten) ?: return null
        val pruef = simulieren(d, z, s, n, kosten) ?: return null
        val halten = ziel(d, Regel(Art.HALTEN))
        val hl = simulieren(d, halten, ab, s, kosten) ?: return null
        val hp = simulieren(d, halten, s, n, kosten) ?: return null
        val srs = alle.map { it.third.sharpe }
        val varianz = varianz(srs)
        val sr0 = erwartetesMaximum(srs.size, varianz)
        val dsr = deflatedSharpe(lern.sharpe, sr0, lern.renditen.size, schiefe(lern.renditen), woelbung(lern.renditen))
        return Lotterie(srs.size, beste, lern, pruef, hl, hp, dsr, sr0)
    }

    // Deflated Sharpe Ratio

    private const val EULER_MASCHERONI = 0.5772156649015329

    // Erwartetes Maximum von n Sharpe-Quoten, wenn keine Variante wirklich etwas kann (Bailey und Lopez de Prado
    // 2014): Wurzel der Varianz der Quoten mal ((1 - g) x Quantil(1 - 1/n) + g x Quantil(1 - 1/(n e))), g die
    // Euler-Mascheroni-Konstante.
    fun erwartetesMaximum(n: Int, varianz: Double): Double {
        if (n < 2 || varianz <= 0) return 0.0
        val g = EULER_MASCHERONI
        return sqrt(varianz) * ((1 - g) * normInv(1.0 - 1.0 / n) + g * normInv(1.0 - 1.0 / (n * E)))
    }

    // Wahrscheinlichkeit, dass die echte Sharpe-Quote ueber sr0 liegt, mit t Renditen, ihrer Schiefe und Woelbung
    // (Normalverteilung: 0 und 3).
    fun deflatedSharpe(sr: Double, sr0: Double, t: Int, schiefe: Double, woelbung: Double): Double {
        if (t < 2) return 0.0
        val nenner = 1.0 - schiefe * sr + (woelbung - 1.0) / 4.0 * sr * sr
        if (nenner <= 1e-12) return if (sr > sr0) 1.0 else 0.0
        return Pricing.normCdf((sr - sr0) * sqrt((t - 1).toDouble()) / sqrt(nenner))
    }

    fun varianz(x: List<Double>): Double {
        if (x.size < 2) return 0.0
        val m = x.average()
        return x.sumOf { (it - m) * (it - m) } / (x.size - 1)
    }

    fun schiefe(x: List<Double>): Double {
        if (x.size < 3) return 0.0
        val m = x.average()
        val s2 = x.sumOf { (it - m) * (it - m) } / x.size
        if (s2 < 1e-24) return 0.0
        return x.sumOf { (it - m).pow(3) } / x.size / s2.pow(1.5)
    }

    // Woelbung (Kurtosis), nicht die Ueberschusswoelbung: Normalverteilung = 3.
    fun woelbung(x: List<Double>): Double {
        if (x.size < 4) return 3.0
        val m = x.average()
        val s2 = x.sumOf { (it - m) * (it - m) } / x.size
        if (s2 < 1e-24) return 3.0
        return x.sumOf { (it - m).pow(4) } / x.size / (s2 * s2)
    }

    // Quantil der Standardnormalverteilung nach Acklam (Fehler unter 1,2e-9).
    fun normInv(p: Double): Double {
        if (p <= 0.0) return Double.NEGATIVE_INFINITY
        if (p >= 1.0) return Double.POSITIVE_INFINITY
        val a = doubleArrayOf(-3.969683028665376e+01, 2.209460984245205e+02, -2.759285104469687e+02, 1.383577518672690e+02, -3.066479806614716e+01, 2.506628277459239e+00)
        val b = doubleArrayOf(-5.447609879822406e+01, 1.615858368580409e+02, -1.556989798598866e+02, 6.680131188771972e+01, -1.328068155288572e+01)
        val c = doubleArrayOf(-7.784894002430293e-03, -3.223964580411365e-01, -2.400758277161838e+00, -2.549732539343734e+00, 4.374664141464968e+00, 2.938163982698783e+00)
        val e = doubleArrayOf(7.784695709041462e-03, 3.224671290700398e-01, 2.445134137142996e+00, 3.754408661907416e+00)
        val unten = 0.02425
        return when {
            p < unten -> {
                val q = sqrt(-2 * ln(p))
                (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / ((((e[0] * q + e[1]) * q + e[2]) * q + e[3]) * q + 1)
            }
            p <= 1 - unten -> {
                val q = p - 0.5
                val r = q * q
                (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q /
                    (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1)
            }
            else -> {
                val q = sqrt(-2 * ln(1 - p))
                -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / ((((e[0] * q + e[1]) * q + e[2]) * q + e[3]) * q + 1)
            }
        }
    }

    // Erste Kerze ab dem Zeitpunkt von; 0, wenn die Reihe spaeter beginnt.
    fun abIndex(kerzen: List<Candle>, von: Long): Int {
        val i = kerzen.indexOfFirst { it.t >= von }
        return if (i < 0) kerzen.size else i
    }

    // Kerzen bis zum Ende bisMs und Index der ersten Kerze ab vonMs (fuer einen eigenen Zeitraum). Null ohne Kerzen im Zeitraum.
    fun ausschnitt(kerzen: List<Candle>, vonMs: Long, bisMs: Long): Pair<List<Candle>, Int>? {
        val ende = kerzen.indexOfLast { it.t <= bisMs } + 1
        if (ende <= 0) return null
        val teil = kerzen.subList(0, ende)
        val ab = abIndex(teil, vonMs)
        return if (ab >= teil.size) null else teil to ab
    }

    // Linien im Uebersichtschart (5.96): r wechselt zwischen sichtbar und ausgeblendet.
    fun umschalten(aus: Set<Regel>, r: Regel): Set<Regel> = if (r in aus) aus - r else aus + r

    // Die sichtbaren Berichte in der Reihenfolge der Liste.
    fun sichtbare(liste: List<Bericht>, aus: Set<Regel>): List<Bericht> = liste.filter { it.regel !in aus }

    // Duennt einen Verlauf fuer den Chart auf hoechstens max Punkte aus; der letzte bleibt.
    fun ausduennen(werte: List<Double>, max: Int = 240): List<Double> {
        if (werte.size <= max) return werte
        val schritt = (werte.size - 1).toDouble() / (max - 1)
        return (0 until max).map { werte[(it * schritt).toInt().coerceAtMost(werte.size - 1)] }.dropLast(1) + werte.last()
    }
}
