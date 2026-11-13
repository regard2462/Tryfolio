package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.HistorieJst.Art
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// Historische Annahmen fuer die Rechner [0.78]: reale Jahresrenditen aus HistorieJst (ein Land oder die Welt als
// gleichgewichteter Schnitt aller Laender mit Wert im Jahr) als Voreinstellung fuer Rendite und Schwankung, die Bandbreite
// ueber alle Zeitraeume gleicher Laenge und der Modus „historische Folgen“: Statt Zufallszahlen laeuft jede lueckenlose
// Folge echter Jahre einmal durch (ein Verlauf je Startjahr). Alles real, also in heutiger Kaufkraft.
object HistorieRechner {

    const val WELT = "WELT"

    // Welt: ein Jahr zaehlt ab so vielen Laendern mit Wert
    const val MIN_LAENDER = 8

    // Eine Basis taugt ab so vielen Jahren mit Wert, der Modus „historische Folgen“ ab so vielen Startjahren
    const val MIN_JAHRE = 60
    const val MIN_FAELLE = 20

    // Laender der Einstellungen (ISO 2) mit Aktienrenditen in der Quelle
    val A3: Map<String, String> = mapOf(
        "AU" to "AUS", "BE" to "BEL", "CH" to "CHE", "DE" to "DEU", "DK" to "DNK", "ES" to "ESP", "FI" to "FIN", "FR" to "FRA",
        "GB" to "GBR", "IT" to "ITA", "JP" to "JPN", "NL" to "NLD", "NO" to "NOR", "PT" to "PRT", "SE" to "SWE", "US" to "USA",
    )

    // Reale Jahresrenditen (Anteil) je Jahr mit Wert, Jahre aufsteigend. laender: groesste Zahl der Laender in einem Jahr.
    class Basis(val kennung: String, val jahre: IntArray, val renditen: DoubleArray, val laender: Int) {
        init { require(jahre.size == renditen.size) }
        val erstes: Int get() = jahre.first()
        val letztes: Int get() = jahre.last()
    }

    // Welt: je Jahr das Mittel der Renditen aller Laender mit Wert, ab MIN_LAENDER Laendern. null unter MIN_JAHRE Jahren.
    fun welt(d: HistorieJst.Datei, art: Art = Art.AKTIEN): Basis? {
        val jahre = ArrayList<Int>()
        val werte = ArrayList<Double>()
        var meiste = 0
        val erstes = d.laender.values.minOfOrNull { it.von } ?: return null
        for (j in erstes..d.ende) {
            var summe = 0.0
            var n = 0
            for (l in d.laender.values) {
                val x = l.reihen[art]?.getOrNull(j - l.von) ?: continue
                summe += x
                n++
            }
            if (n < MIN_LAENDER) continue
            jahre += j
            werte += summe / n
            meiste = max(meiste, n)
        }
        return if (jahre.size < MIN_JAHRE) null else Basis(WELT, jahre.toIntArray(), werte.toDoubleArray(), meiste)
    }

    // Ein Land (A3 der Quelle). null ohne Reihe oder unter MIN_JAHRE Jahren mit Wert.
    fun land(d: HistorieJst.Datei, a3: String, art: Art = Art.AKTIEN): Basis? {
        val l = d.laender[a3] ?: return null
        val r = l.reihen[art] ?: return null
        val jahre = ArrayList<Int>()
        val werte = ArrayList<Double>()
        for (i in r.indices) {
            val x = r[i] ?: continue
            if (x <= -1.0) continue
            jahre += l.von + i
            werte += x
        }
        return if (jahre.size < MIN_JAHRE) null else Basis(a3, jahre.toIntArray(), werte.toDoubleArray(), 1)
    }

    // rendite: geometrisches Mittel je Jahr in Prozent; schwankung: Streuung der logarithmierten Jahresrenditen in Prozent
    // (passt zu den lognormalen Monatsschritten der Rechner).
    class Kennzahlen(val rendite: Double, val schwankung: Double, val erstes: Int, val letztes: Int, val jahre: Int)

    fun kennzahlen(b: Basis): Kennzahlen {
        val l = DoubleArray(b.renditen.size) { ln(1.0 + b.renditen[it]) }
        val mittel = l.average()
        val varianz = if (l.size > 1) l.sumOf { (it - mittel) * (it - mittel) } / (l.size - 1) else 0.0
        return Kennzahlen((exp(mittel) - 1.0) * 100.0, sqrt(varianz) * 100.0, b.erstes, b.letztes, b.jahre.size)
    }

    // Plaetze in der Basis, ab denen n Jahre ohne Luecke folgen
    fun starts(b: Basis, n: Int): IntArray {
        if (n < 1) return IntArray(0)
        val aus = ArrayList<Int>()
        for (i in 0..b.jahre.size - n) if (b.jahre[i + n - 1] - b.jahre[i] == n - 1) aus += i
        return aus.toIntArray()
    }

    // Ein Zeitraum mit Rendite je Jahr in Prozent; von und bis einschliesslich
    class Zeitraum(val von: Int, val bis: Int, val rendite: Double)

    // Schlechtester und bester Zeitraum von n Jahren ohne Luecke und die Zahl solcher Zeitraeume (ueberlappend)
    class Bandbreite(val jahre: Int, val schlecht: Zeitraum, val best: Zeitraum, val faelle: Int)

    fun bandbreite(b: Basis, n: Int): Bandbreite? {
        val s = starts(b, n)
        if (s.isEmpty()) return null
        fun zeitraum(i: Int): Zeitraum {
            var w = 1.0
            for (k in i until i + n) w *= 1.0 + b.renditen[k]
            return Zeitraum(b.jahre[i], b.jahre[i + n - 1], (w.pow(1.0 / n) - 1.0) * 100.0)
        }
        val alle = s.map { zeitraum(it) }
        return Bandbreite(n, alle.minByOrNull { it.rendite }!!, alle.maxByOrNull { it.rendite }!!, alle.size)
    }

    // Nominale Rendite in Prozent, die mit der Inflation dieselbe reale Rendite ergibt
    fun nominal(realPct: Double, inflationPct: Double): Double = ((1.0 + realPct / 100.0) * (1.0 + inflationPct / 100.0) - 1.0) * 100.0

    private fun perzentil(sortiert: DoubleArray, q: Double): Double {
        if (sortiert.isEmpty()) return 0.0
        val pos = q * (sortiert.size - 1)
        val i = floor(pos).toInt().coerceIn(0, sortiert.size - 1)
        val j = min(i + 1, sortiert.size - 1)
        return sortiert[i] + (sortiert[j] - sortiert[i]) * (pos - i)
    }

    // Monatliches Wachstum im Jahr mit der realen Rendite r
    private fun monat(r: Double): Double = (1.0 + r).pow(1.0 / 12.0)

    // Vermoegensaufbau ueber alle historischen Folgen: je Startjahr ein Verlauf. Die Rate fliesst am Monatsende hinein und
    // steigt jaehrlich um raisePct; mit inflationPct verliert sie wie in PortfolioTools.project an Kaufkraft. Alle Werte real.
    // erreicht: Verlaeufe, die am Ende mindestens das Ziel haben. schlecht und best: Startjahr und Endwert.
    class Aufbau(
        val projektion: PortfolioTools.Projection, val faelle: Int, val erreicht: Int,
        val ersterStart: Int, val letzterStart: Int,
        val schlechtStart: Int, val schlechtEnde: Double, val bestStart: Int, val bestEnde: Double,
    )

    fun aufbau(
        b: Basis, start: Double, monthly: Double, years: Int, raisePct: Double = 0.0, inflationPct: Double = 0.0, target: Double = 0.0,
    ): Aufbau? {
        val y = years.coerceIn(1, 60)
        val s = starts(b, y)
        if (s.size < MIN_FAELLE) return null
        val inflation = ln(1.0 + inflationPct / 100.0) / 12.0
        val n = s.size
        val jeJahr = Array(y + 1) { DoubleArray(n) }
        val zeitZumZiel = ArrayList<Double>(n)
        for (p in 0 until n) {
            var v = max(0.0, start)
            var rate = max(0.0, monthly)
            jeJahr[0][p] = v
            var treffer = if (target > 0 && v >= target) 0.0 else Double.NaN
            for (k in 0 until y) {
                val g = monat(b.renditen[s[p] + k])
                for (i in 1..12) {
                    val m = k * 12 + i
                    v = v * g + rate * exp(-inflation * m)
                    if (treffer.isNaN() && target > 0 && v >= target) treffer = m / 12.0
                }
                jeJahr[k + 1][p] = v
                rate *= 1.0 + raisePct / 100.0
            }
            if (!treffer.isNaN()) zeitZumZiel += treffer
        }
        val eingezahlt = DoubleArray(y + 1)
        run {
            var summe = max(0.0, start)
            var rate = max(0.0, monthly)
            eingezahlt[0] = summe
            for (m in 1..y * 12) {
                summe += rate * exp(-inflation * m)
                if (m % 12 == 0) { eingezahlt[m / 12] = summe; rate *= 1.0 + raisePct / 100.0 }
            }
        }
        val p10 = DoubleArray(y + 1); val p25 = DoubleArray(y + 1); val p50 = DoubleArray(y + 1)
        val p75 = DoubleArray(y + 1); val p90 = DoubleArray(y + 1)
        for (k in 0..y) {
            val sortiert = jeJahr[k].sortedArray()
            p10[k] = perzentil(sortiert, 0.10); p25[k] = perzentil(sortiert, 0.25); p50[k] = perzentil(sortiert, 0.50)
            p75[k] = perzentil(sortiert, 0.75); p90[k] = perzentil(sortiert, 0.90)
        }
        val ende = jeJahr[y]
        val erreicht = if (target > 0) ende.count { it >= target } else 0
        val noetig = (n + 1) / 2
        val haelfte = if (target > 0 && zeitZumZiel.size >= noetig) zeitZumZiel.sorted()[noetig - 1] else null
        val schlecht = ende.indices.minByOrNull { ende[it] }!!
        val best = ende.indices.maxByOrNull { ende[it] }!!
        return Aufbau(
            PortfolioTools.Projection(y, p10, p25, p50, p75, p90, eingezahlt, if (target > 0) erreicht.toDouble() / n else 0.0, haelfte),
            n, erreicht, b.jahre[s.first()], b.jahre[s.last()],
            b.jahre[s[schlecht]], ende[schlecht], b.jahre[s[best]], ende[best],
        )
    }

    // Entnahmeplan ueber alle historischen Folgen: zu Monatsbeginn wird monthly entnommen (mit indexed haelt die Entnahme
    // ihre Kaufkraft, sonst verliert sie mit inflationPct wie in PortfolioTools.withdrawal), der Rest waechst mit der realen
    // Rendite des Jahres. reicht: Verlaeufe, in denen das Geld bis zum Ende reicht. schlecht: Startjahr mit dem fruehesten
    // Ende (bei mehreren ohne Ende das mit dem kleinsten Rest) und nach wie vielen Jahren dort nichts mehr uebrig war.
    class Entnahme(
        val plan: PortfolioTools.Withdrawal, val faelle: Int, val reicht: Int,
        val ersterStart: Int, val letzterStart: Int,
        val schlechtStart: Int, val schlechtLeerNach: Double?, val schlechtEnde: Double,
    )

    fun entnahme(b: Basis, start: Double, monthly: Double, years: Int, inflationPct: Double, indexed: Boolean): Entnahme? {
        val y = years.coerceIn(1, 60)
        val s = starts(b, y)
        if (s.size < MIN_FAELLE) return null
        val monate = y * 12
        val n = s.size
        val inflation = ln(1.0 + inflationPct / 100.0) / 12.0
        // Kaufkraft der Entnahme im Monat m (0 = erster): mit Anpassung springt sie jedes Jahr zurueck auf den Anfangswert
        val raten = DoubleArray(monate) { m -> exp(-inflation * m) * (if (indexed) exp(inflation * 12.0 * (m / 12)) else 1.0) }
        val wachstum = Array(n) { p -> DoubleArray(y) { k -> monat(b.renditen[s[p] + k]) } }

        fun anteil(w: Double): Double {
            var ok = 0
            for (p in 0 until n) {
                var v = start
                var lebt = true
                for (m in 0 until monate) {
                    v -= w * raten[m]
                    if (v <= 0.0) { lebt = false; break }
                    v *= wachstum[p][m / 12]
                }
                if (lebt) ok++
            }
            return ok.toDouble() / n
        }

        val jeJahr = Array(y + 1) { DoubleArray(n) }
        val leerNach = DoubleArray(n) { Double.NaN }
        for (p in 0 until n) {
            var v = start
            jeJahr[0][p] = v
            for (m in 0 until monate) {
                if (leerNach[p].isNaN()) {
                    v -= monthly * raten[m]
                    if (v <= 0.0) { v = 0.0; leerNach[p] = m / 12.0 } else v *= wachstum[p][m / 12]
                }
                if ((m + 1) % 12 == 0) jeJahr[(m + 1) / 12][p] = v
            }
        }
        val p10 = DoubleArray(y + 1); val p50 = DoubleArray(y + 1); val p90 = DoubleArray(y + 1)
        for (k in 0..y) {
            val sortiert = jeJahr[k].sortedArray()
            p10[k] = perzentil(sortiert, 0.10); p50[k] = perzentil(sortiert, 0.50); p90[k] = perzentil(sortiert, 0.90)
        }
        val leer = leerNach.filter { !it.isNaN() }
        val reicht = n - leer.size
        val noetig = (n + 1) / 2
        val mitte = if (leer.size >= noetig) leer.sorted()[noetig - 1] else null
        var lo = 0.0
        var hi = max(1.0, start)
        var schritte = 0
        while (start > 0 && hi - lo > 0.5 && schritte++ < 40) {
            val mid = (lo + hi) / 2
            if (anteil(mid) >= 0.9) lo = mid else hi = mid
        }
        val ende = jeJahr[y]
        val schlecht = (0 until n).minWithOrNull(compareBy<Int> { if (leerNach[it].isNaN()) Double.MAX_VALUE else leerNach[it] }.thenBy { ende[it] })!!
        return Entnahme(
            PortfolioTools.Withdrawal(y, p10, p50, p90, reicht.toDouble() / n, mitte, if (start > 0) lo else 0.0),
            n, reicht, b.jahre[s.first()], b.jahre[s.last()],
            b.jahre[s[schlecht]], leerNach[schlecht].takeIf { !it.isNaN() }, ende[schlecht],
        )
    }
}
