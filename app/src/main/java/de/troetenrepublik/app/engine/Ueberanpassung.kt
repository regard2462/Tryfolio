package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

// Pruefungen gegen Ueberanpassung im Strategietest (0.78), nach den veroeffentlichten Formeln:
// - Wahrscheinlichkeit der Ueberanpassung (PBO) mit kombinatorisch symmetrischer Kreuzvalidierung (Bailey, Borwein,
//   Lopez de Prado, Zhu 2017)
// - Mindestlaufzeit zur Probabilistic Sharpe Ratio (Bailey und Lopez de Prado 2012)
// - Test auf ueberlegene Vorhersagekraft (SPA, Hansen 2005) mit stationaerer Blockziehung (Politis und Romano 1994)
// Alles mit festem Startwert wiederholbar.
object Ueberanpassung {

    // ------------------------------------------------------------ PBO

    // Zahl der Abschnitte; C(12, 6) = 924 Kombinationen bleiben auf dem Handy schnell.
    const val BLOECKE = 12
    private const val MIN_BLOCKTAGE = 21

    // Anzahl, Summe und Summe der Quadrate der Tagesrenditen je Abschnitt; daraus folgt die Sharpe-Quote jeder
    // Vereinigung von Abschnitten, ohne die Renditen aufzubewahren.
    class Blockwerte(val anzahl: IntArray, val summe: DoubleArray, val quadrate: DoubleArray)

    // Abschnitt j umfasst die Tage j * T / S bis (j + 1) * T / S (ausschliesslich).
    fun blockwerte(r: DoubleArray, bloecke: Int): Blockwerte {
        val t = r.size
        val a = IntArray(bloecke)
        val s = DoubleArray(bloecke)
        val q = DoubleArray(bloecke)
        for (j in 0 until bloecke) {
            val von = (j.toLong() * t / bloecke).toInt()
            val bis = ((j + 1).toLong() * t / bloecke).toInt()
            for (i in von until bis) {
                s[j] += r[i]
                q[j] += r[i] * r[i]
            }
            a[j] = bis - von
        }
        return Blockwerte(a, s, q)
    }

    // Groesste gerade Abschnittszahl bis BLOECKE mit mindestens MIN_BLOCKTAGE Tagen je Abschnitt, mindestens 4; sonst null.
    fun bloecke(tage: Int): Int? {
        var s = BLOECKE
        while (s >= 4 && tage / s < MIN_BLOCKTAGE) s -= 2
        return if (s >= 4) s else null
    }

    // wahrscheinlichkeit: Anteil der Kombinationen mit Logit hoechstens null. logits: je Kombination
    // ln(w / (1 - w)), w = Rang der im Lernteil besten Variante im Pruefteil durch (Varianten + 1).
    class Pbo(val wahrscheinlichkeit: Double, val kombinationen: Int, val varianten: Int, val bloecke: Int, val logits: DoubleArray)

    // Jede Haelfte der Abschnitte ist einmal Lernteil, die andere Pruefteil. Im Lernteil gewinnt die Variante mit der
    // hoechsten Sharpe-Quote (bei Gleichstand die erste), im Pruefteil zaehlt ihr Rang (1 = schlechteste, Gleichstand
    // zaehlt halb). Null bei weniger als zwei Varianten oder ungerader Abschnittszahl.
    fun pbo(werte: List<Blockwerte>): Pbo? {
        val nv = werte.size
        if (nv < 2) return null
        val s = werte[0].anzahl.size
        if (s < 2 || s % 2 != 0 || s > 30 || werte.any { it.anzahl.size != s }) return null
        val lern = DoubleArray(nv)
        val pruef = DoubleArray(nv)
        val logits = ArrayList<Double>()
        var ueber = 0
        for (maske in 0 until (1 shl s)) {
            if (maske.countOneBits() != s / 2) continue
            for (k in 0 until nv) {
                lern[k] = sharpe(werte[k], maske, true)
                pruef[k] = sharpe(werte[k], maske, false)
            }
            var beste = 0
            for (k in 1 until nv) if (lern[k] > lern[beste]) beste = k
            var tiefer = 0
            var gleich = 0
            for (k in 0 until nv) {
                if (k == beste) continue
                if (pruef[k] < pruef[beste]) tiefer++ else if (pruef[k] == pruef[beste]) gleich++
            }
            val w = (1.0 + tiefer + gleich / 2.0) / (nv + 1)
            val l = ln(w / (1.0 - w))
            logits += l
            if (l <= 0.0) ueber++
        }
        return Pbo(ueber.toDouble() / logits.size, logits.size, nv, s, logits.toDoubleArray())
    }

    // Sharpe-Quote je Tag der Abschnitte in maske (drin = true) oder der uebrigen.
    private fun sharpe(w: Blockwerte, maske: Int, drin: Boolean): Double {
        var n = 0
        var su = 0.0
        var q = 0.0
        for (j in w.anzahl.indices) {
            if (((maske shr j) and 1 == 1) != drin) continue
            n += w.anzahl[j]
            su += w.summe[j]
            q += w.quadrate[j]
        }
        if (n < 2) return 0.0
        val v = (q - su * su / n) / (n - 1)
        return if (v < 1e-24) 0.0 else su / n / sqrt(v)
    }

    enum class Stufe { GERING, ERHEBLICH, HOCH }

    fun stufe(pbo: Double): Stufe = when {
        pbo < 0.2 -> Stufe.GERING
        pbo <= 0.5 -> Stufe.ERHEBLICH
        else -> Stufe.HOCH
    }

    // ------------------------------------------------------------ Mindestlaufzeit

    // Zahl der Beobachtungen, ab der eine Sharpe-Quote sr (je Beobachtung) mit der Sicherheit ueber sr0 liegt:
    // 1 + (1 - Schiefe x sr + (Woelbung - 1) / 4 x sr^2) x (z / (sr - sr0))^2, z das Quantil der Normalverteilung.
    // Woelbung ist die Kurtosis (Normalverteilung 3). Null, wenn sr nicht ueber sr0 liegt.
    fun mindestlaufzeit(sr: Double, schiefe: Double, woelbung: Double, sr0: Double = 0.0, sicherheit: Double = 0.95): Double? {
        if (!(sr > sr0)) return null
        val z = StrategieLabor.normInv(sicherheit)
        val v = max(1.0 - schiefe * sr + (woelbung - 1.0) / 4.0 * sr * sr, 0.0)
        return 1.0 + v * (z / (sr - sr0)).pow(2)
    }

    // noetigJahre: null, wenn die Sharpe-Quote nicht ueber null liegt. sharpeJahr: auf ein Jahr hochgerechnet.
    class Laufzeit(val noetigJahre: Double?, val vorhandenJahre: Double, val sharpeJahr: Double)

    // Mindestlaufzeit eines Ergebnisses des Strategietests; Beobachtungen pro Jahr aus seinen Tagesrenditen.
    fun laufzeit(e: StrategieLabor.Ergebnis): Laufzeit? {
        val r = e.renditen
        if (r.size < 30 || e.jahre <= 0) return null
        val proJahr = r.size / e.jahre
        val n = mindestlaufzeit(e.sharpe, StrategieLabor.schiefe(r), StrategieLabor.woelbung(r))
        return Laufzeit(n?.let { it / proJahr }, e.jahre, e.sharpe * sqrt(proJahr))
    }

    // ------------------------------------------------------------ SPA

    const val ZIEHUNGEN = 400
    const val SPA_BLOCK = 20
    const val SPA_GRENZE = 0.05

    // Stationaere Blockziehung: Bloecke mit gleichverteiltem Anfang und geometrisch verteilter Laenge (Mittel block),
    // im Kreis ueber das Ende hinaus, je Ziehung zusammen genau n Tage. Gespeichert werden nur Anfaenge und Laengen.
    class Ziehungen(val n: Int, val anzahl: Int, val anfang: Array<IntArray>, val laenge: Array<IntArray>)

    fun ziehungen(n: Int, anzahl: Int, block: Int, seed: Long): Ziehungen {
        val rng = PortfolioTools.Rng(seed)
        val p = 1.0 / max(block, 1)
        val ta = IntArray(max(n, 1))
        val tl = IntArray(max(n, 1))
        val anfang = Array(anzahl) { IntArray(0) }
        val laenge = Array(anzahl) { IntArray(0) }
        for (b in 0 until anzahl) {
            var t = 0
            var k = 0
            while (t < n) {
                val start = (rng.nextDouble() * n).toInt().coerceIn(0, n - 1)
                var len = 1
                if (p < 1.0) {
                    val u = 1.0 - rng.nextDouble()
                    len = 1 + (ln(u) / ln(1.0 - p)).toInt()
                }
                len = len.coerceIn(1, n - t)
                ta[k] = start
                tl[k] = len
                k++
                t += len
            }
            anfang[b] = ta.copyOf(k)
            laenge[b] = tl.copyOf(k)
        }
        return Ziehungen(n, anzahl, anfang, laenge)
    }

    // Tage der Ziehung b in ihrer Reihenfolge (fuer Pruefungen).
    fun indizes(z: Ziehungen, b: Int): IntArray {
        val aus = IntArray(z.n)
        var t = 0
        for (k in z.anfang[b].indices) for (j in 0 until z.laenge[b][k]) aus[t++] = (z.anfang[b][k] + j) % z.n
        return aus
    }

    // Mittel der Abstaende einer Variante zu Kaufen und Halten und ihr Mittel in jeder Ziehung.
    class SpaZeile(val mittel: Double, val ziehung: DoubleArray)

    fun spaZeile(d: DoubleArray, z: Ziehungen): SpaZeile {
        val n = d.size
        require(n == z.n)
        val summe = DoubleArray(n + 1)
        for (i in 0 until n) summe[i + 1] = summe[i] + d[i]
        val boot = DoubleArray(z.anzahl) { b ->
            val an = z.anfang[b]
            val la = z.laenge[b]
            var s = 0.0
            for (k in an.indices) {
                val e = an[k] + la[k]
                s += if (e <= n) summe[e] - summe[an[k]] else summe[n] - summe[an[k]] + summe[e - n]
            }
            s / n
        }
        return SpaZeile(summe[n] / n, boot)
    }

    // p: Anteil der Ziehungen mit einer Teststatistik mindestens so gross wie echt. statistik: max(0, max_k
    // Wurzel(n) x Mittel_k / omega_k), omega_k aus der Streuung der Ziehungen.
    class Spa(val p: Double, val statistik: Double, val varianten: Int, val ziehungen: Int)

    // Hansens konsistente Fassung: Varianten mit deutlich negativem Mittel (unter -Wurzel(omega^2 / n x 2 ln ln n))
    // gehen mit Mittel null in die Ziehungen ein, damit klar unterlegene Varianten den Test nicht verwaessern.
    // Varianten ohne Streuung zaehlen nicht. Null ohne verwertbare Variante.
    fun spa(zeilen: List<SpaZeile>, n: Int): Spa? {
        if (zeilen.isEmpty() || n < 3) return null
        val wn = sqrt(n.toDouble())
        val b = zeilen[0].ziehung.size
        if (b == 0) return null
        val omega = DoubleArray(zeilen.size)
        val mitte = DoubleArray(zeilen.size)
        val schwelle = max(2.0 * ln(ln(n.toDouble())), 0.0)
        var t = 0.0
        var gueltig = 0
        for ((k, z) in zeilen.withIndex()) {
            var q = 0.0
            for (x in z.ziehung) {
                val a = wn * (x - z.mittel)
                q += a * a
            }
            omega[k] = sqrt(q / b)
            mitte[k] = if (z.mittel >= -sqrt(omega[k] * omega[k] / n * schwelle)) z.mittel else 0.0
            if (omega[k] > 1e-15) {
                gueltig++
                t = max(t, wn * z.mittel / omega[k])
            }
        }
        if (gueltig == 0) return null
        var mindestens = 0
        for (j in 0 until b) {
            var tb = 0.0
            for (k in zeilen.indices) if (omega[k] > 1e-15) tb = max(tb, wn * (zeilen[k].ziehung[j] - mitte[k]) / omega[k])
            if (tb >= t) mindestens++
        }
        return Spa(mindestens.toDouble() / b, t, gueltig, b)
    }

    // ------------------------------------------------------------ Strategietest

    class Pruefung(val pbo: Pbo?, val spa: Spa?)

    // PBO und SPA fuer alle Varianten einer Familie im Zeitraum ab bis Ende, mit Kosten. PBO auf den Tagesrenditen
    // der Varianten, SPA auf ihren Abstaenden zu Kaufen und Halten. Eine Variante nach der anderen, damit der Speicher
    // klein bleibt. abbruch wird je Variante aufgerufen.
    fun pruefen(
        kerzen: List<Candle>, ab: Int, kosten: StrategieLabor.Kosten, familie: StrategieLabor.Familie,
        seed: Long = 13L, abbruch: () -> Unit = {},
    ): Pruefung? {
        val n = kerzen.size
        if (ab < 0 || n - ab < 4 * MIN_BLOCKTAGE) return null
        val d = StrategieLabor.Daten(kerzen)
        val halten = StrategieLabor.simulieren(d, BooleanArray(n) { true }, ab, n, kosten) ?: return null
        val h = halten.renditen.toDoubleArray()
        val t = h.size
        val s = bloecke(t)
        val z = ziehungen(t, ZIEHUNGEN, SPA_BLOCK, seed)
        val werte = ArrayList<Blockwerte>()
        val zeilen = ArrayList<SpaZeile>()
        val abstand = DoubleArray(t)
        for (r in StrategieLabor.varianten(familie)) {
            abbruch()
            val e = StrategieLabor.simulieren(d, StrategieLabor.ziel(d, r), ab, n, kosten) ?: continue
            val x = e.renditen.toDoubleArray()
            if (x.size != t) continue
            if (s != null) werte += blockwerte(x, s)
            for (i in 0 until t) abstand[i] = x[i] - h[i]
            zeilen += spaZeile(abstand, z)
        }
        return Pruefung(pbo(werte), spa(zeilen, t))
    }
}
