package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import java.util.TreeMap
import kotlin.math.abs
import kotlin.math.sqrt

// Indikatoren der Wertpapierseite aus Tagesschlusskursen. Sie werden auf der langen Tagesreihe gerechnet und auf
// die Punkte des Charts gelegt, damit die 200-Tage-Linie im Jahreschart von Anfang an da ist.
object Indikatoren {

    // Gleitender Durchschnitt ueber n Werte, null solange noch keine n Werte da sind.
    fun sma(werte: List<Double>, n: Int): List<Double?> {
        val aus = ArrayList<Double?>(werte.size)
        var summe = 0.0
        for (i in werte.indices) {
            summe += werte[i]
            if (i >= n) summe -= werte[i - n]
            aus += if (i >= n - 1) summe / n else null
        }
        return aus
    }

    // RSI nach Wilder ueber n Tage, 0 bis 100. Null fuer die ersten n Werte.
    fun rsi(werte: List<Double>, n: Int = 14): List<Double?> {
        val aus = ArrayList<Double?>(werte.size)
        if (werte.size <= n) return werte.map { null }
        var auf = 0.0
        var ab = 0.0
        aus += null
        for (i in 1..n) {
            val d = werte[i] - werte[i - 1]
            if (d > 0) auf += d else ab -= d
            if (i < n) aus += null
        }
        auf /= n
        ab /= n
        aus += wert(auf, ab)
        for (i in n + 1 until werte.size) {
            val d = werte[i] - werte[i - 1]
            auf = (auf * (n - 1) + maxOf(d, 0.0)) / n
            ab = (ab * (n - 1) + maxOf(-d, 0.0)) / n
            aus += wert(auf, ab)
        }
        return aus
    }

    private fun wert(auf: Double, ab: Double): Double = if (ab <= 0) 100.0 else 100.0 - 100.0 / (1.0 + auf / ab)

    // Exponentieller Durchschnitt ueber n Werte, beginnt mit dem einfachen Durchschnitt der ersten n.
    fun ema(werte: List<Double>, n: Int): List<Double?> {
        if (werte.size < n) return werte.map { null }
        val k = 2.0 / (n + 1)
        val aus = ArrayList<Double?>(werte.size)
        var e = 0.0
        for (i in werte.indices) {
            when {
                i < n - 1 -> aus += null
                i == n - 1 -> { e = werte.subList(0, n).average(); aus += e }
                else -> { e = werte[i] * k + e * (1 - k); aus += e }
            }
        }
        return aus
    }

    // Bollinger-Baender: Durchschnitt ueber n Tage plus und minus k Standardabweichungen.
    data class Baender(val mitte: List<Double?>, val oben: List<Double?>, val unten: List<Double?>)

    fun bollinger(werte: List<Double>, n: Int = 20, k: Double = 2.0): Baender {
        val mitte = sma(werte, n)
        val oben = ArrayList<Double?>(werte.size)
        val unten = ArrayList<Double?>(werte.size)
        for (i in werte.indices) {
            val m = mitte[i]
            if (m == null) { oben += null; unten += null; continue }
            var q = 0.0
            for (j in i - n + 1..i) q += (werte[j] - m) * (werte[j] - m)
            val sd = sqrt(q / n)
            oben += m + k * sd
            unten += m - k * sd
        }
        return Baender(mitte, oben, unten)
    }

    // MACD: Abstand der Durchschnitte ueber 12 und 26 Tage, Signallinie ueber 9 Tage und Abstand dazu.
    data class Macd(val linie: List<Double?>, val signal: List<Double?>, val histogramm: List<Double?>)

    fun macd(werte: List<Double>, schnell: Int = 12, langsam: Int = 26, glaettung: Int = 9): Macd {
        val a = ema(werte, schnell)
        val b = ema(werte, langsam)
        val linie = werte.indices.map { i -> val x = a[i]; val y = b[i]; if (x != null && y != null) x - y else null }
        val start = linie.indexOfFirst { it != null }
        val signal: List<Double?> = if (start < 0) linie.map { null } else List(start) { null } + ema(linie.drop(start).map { it!! }, glaettung)
        val hist = linie.indices.map { i -> val x = linie[i]; val s = signal[i]; if (x != null && s != null) x - s else null }
        return Macd(linie, signal, hist)
    }

    // art: sma50, sma200, bb, rsi, macd. stufe: 1 aufwaerts, -1 abwaerts, 0 neutral, 2 Vorsicht (weit gelaufen).
    // wert: Abstand zum Durchschnitt in Prozent, RSI-Wert, Lage in den Baendern (0 unteres, 1 oberes Band) oder Abstand
    // des MACD zur Signallinie. kreuzTag: Tagesnummer des letzten Kreuzens zweier Linien, kreuzAuf die Richtung.
    data class Deutung(
        val art: String,
        val stufe: Int,
        val lage: String,
        val wert: Double,
        val kreuzTag: Long? = null,
        val kreuzAuf: Boolean = false,
        // Bollinger: Baender ungewoehnlich eng, ruhige Phase.
        val eng: Boolean = false,
    )

    enum class Tendenz { POSITIV, NEGATIV, NEUTRAL, GEMISCHT }

    // Uebliche Lesart: POSITIV eher steigend, NEGATIV eher fallend, NEUTRAL ohne Richtung (ATR immer).
    // GEMISCHT: weit gelaufen (ausserhalb der Baender, ueberkauft, ueberverkauft) oder Kurs und Umsatz widersprechen
    // sich (OBV). Eine Gegenbewegung ist dann moeglich, der Trend kann aber auch weiterlaufen.
    fun tendenz(d: Deutung): Tendenz = if (d.art == "atr") Tendenz.NEUTRAL else when (d.stufe) {
        1 -> Tendenz.POSITIV
        -1 -> Tendenz.NEGATIV
        2 -> Tendenz.GEMISCHT
        else -> Tendenz.NEUTRAL
    }

    // Wie viele Handelstage ein Kreuzen der Durchschnitte zurueckliegen darf, damit es genannt wird.
    const val KREUZ_FENSTER = 60

    private fun letztesKreuzen(a: List<Double?>, b: List<Double?>, fenster: Int): Pair<Int, Boolean>? {
        val ende = a.size - 1
        for (i in ende downTo maxOf(1, ende - fenster + 1)) {
            val a0 = a[i - 1] ?: return null
            val b0 = b[i - 1] ?: return null
            val a1 = a[i] ?: continue
            val b1 = b[i] ?: continue
            if (a0 - b0 <= 0 && a1 - b1 > 0) return i to true
            if (a0 - b0 >= 0 && a1 - b1 < 0) return i to false
        }
        return null
    }

    // tage: Tagesnummern, werte: Schlusskurse, aufsteigend. Fehlt ein Wert, fehlt die Deutung.
    fun deuten(
        tage: List<Long>, werte: List<Double>, hoch: List<Double>? = null, tief: List<Double>? = null, umsatz: List<Double>? = null,
    ): List<Deutung> {
        if (werte.size < 30 || tage.size != werte.size) return emptyList()
        val kurs = werte.last()
        val aus = ArrayList<Deutung>()
        val s50 = sma(werte, 50)
        val s200 = sma(werte, 200)
        s50.last()?.let { m ->
            val ab = (kurs / m - 1) * 100
            aus += Deutung("sma50", if (ab >= 0) 1 else -1, if (ab >= 0) "ueber" else "unter", ab)
        }
        s200.last()?.let { m ->
            val ab = (kurs / m - 1) * 100
            val kreuz = letztesKreuzen(s50, s200, KREUZ_FENSTER)
            aus += Deutung("sma200", if (ab >= 0) 1 else -1, if (ab >= 0) "ueber" else "unter", ab,
                kreuz?.let { tage[it.first] }, kreuz?.second ?: false)
        }
        val bb = bollinger(werte)
        val o = bb.oben.last()
        val u = bb.unten.last()
        if (o != null && u != null && o > u) {
            val lage = (kurs - u) / (o - u)
            val breiten = bb.oben.indices.mapNotNull { i ->
                val a = bb.oben[i]; val b = bb.unten[i]; val m = bb.mitte[i]
                if (a != null && b != null && m != null && m > 0) (a - b) / m else null
            }
            val vergleich = breiten.takeLast(120).sorted()
            val eng = vergleich.size >= 60 && breiten.last() < vergleich[vergleich.size / 5]
            val (stufe, text) = when {
                lage > 1.0 -> 2 to "oben"
                lage >= 0.8 -> 1 to "nahe_oben"
                lage < 0.0 -> 2 to "unten"
                lage <= 0.2 -> -1 to "nahe_unten"
                else -> 0 to "mitte"
            }
            aus += Deutung("bb", stufe, text, lage, eng = eng)
        }
        rsi(werte, 14).last()?.let { r ->
            aus += when {
                r >= 70 -> Deutung("rsi", 2, "ueberkauft", r)
                r <= 30 -> Deutung("rsi", 2, "ueberverkauft", r)
                else -> Deutung("rsi", 0, "neutral", r)
            }
        }
        val m = macd(werte)
        m.histogramm.last()?.let { h ->
            val kreuz = letztesKreuzen(m.linie, m.signal, 20)
            aus += Deutung("macd", if (h >= 0) 1 else -1, if (h >= 0) "steigt" else "faellt", h,
                kreuz?.let { tage[it.first] }, kreuz?.second ?: false)
        }
        if (hoch != null && tief != null && hoch.size == werte.size && tief.size == werte.size) {
            val s = stochastik(hoch, tief, werte)
            s.k.last()?.let { k ->
                val kreuz = letztesKreuzen(s.k, s.d, 10)
                aus += when {
                    k >= 80 -> Deutung("stoch", 2, "ueberkauft", k, kreuz?.let { tage[it.first] }, kreuz?.second ?: false)
                    k <= 20 -> Deutung("stoch", 2, "ueberverkauft", k, kreuz?.let { tage[it.first] }, kreuz?.second ?: false)
                    else -> Deutung("stoch", 0, "neutral", k, kreuz?.let { tage[it.first] }, kreuz?.second ?: false)
                }
            }
            // ATR in Prozent des Kurses, verglichen mit dem ueblichen Wert des letzten Jahres.
            val a = atr(hoch, tief, werte)
            val prozent = werte.indices.map { i -> a[i]?.let { it / werte[i] * 100 } }
            prozent.last()?.let { p ->
                val vergleich = prozent.takeLast(250).filterNotNull().sorted()
                val median = vergleich[vergleich.size / 2]
                val r = if (median > 0) p / median else 1.0
                aus += when {
                    r >= 1.3 -> Deutung("atr", 2, "unruhig", p)
                    r <= 0.75 -> Deutung("atr", 0, "ruhig", p)
                    else -> Deutung("atr", 0, "normal", p)
                }
            }
            val ich = ichimoku(hoch, tief)
            val sa = ich.spanA.last()
            val sb = ich.spanB.last()
            if (sa != null && sb != null) {
                val kreuz = letztesKreuzen(ich.tenkan, ich.kijun, 20)
                val oben = maxOf(sa, sb)
                val unten = minOf(sa, sb)
                val (stufe, text) = when { kurs > oben -> 1 to "ueber"; kurs < unten -> -1 to "unter"; else -> 0 to "in" }
                val ab = if (kurs > oben) (kurs / oben - 1) * 100 else if (kurs < unten) (kurs / unten - 1) * 100 else 0.0
                aus += Deutung("ichimoku", stufe, text, ab, kreuz?.let { tage[it.first] }, kreuz?.second ?: false)
            }
            val sr = sar(hoch, tief)
            sr.wert.last()?.let { w ->
                val auf = sr.aufwaerts.last()
                var j = sr.aufwaerts.size - 1
                while (j > 0 && sr.aufwaerts[j - 1] == auf) j--
                aus += Deutung("sar", if (auf) 1 else -1, if (auf) "aufwaerts" else "abwaerts", (kurs / w - 1) * 100, if (j > 0) tage[j] else null, auf)
            }
        }
        // OBV nur, wenn an den meisten der letzten 60 Tage Umsaetze vorliegen.
        if (umsatz != null && umsatz.size == werte.size && werte.size > 21 && umsatz.takeLast(60).count { it > 0 } >= 40) {
            val o = obv(werte, umsatz)
            val n = werte.size - 1
            val kursAuf = werte[n] >= werte[n - 20]
            val obvAuf = o[n] >= o[n - 20]
            val p = (werte[n] / werte[n - 20] - 1) * 100
            aus += when {
                // Kaum Kursbewegung: kein Trend, OBV zeigt nur, auf welcher Seite mehr gehandelt wurde.
                abs(p) < 2.0 -> Deutung("obv", 0, if (obvAuf) "seitwaerts_auf" else "seitwaerts_ab", p)
                kursAuf && obvAuf -> Deutung("obv", 1, "bestaetigt_auf", p)
                !kursAuf && !obvAuf -> Deutung("obv", -1, "bestaetigt_ab", p)
                kursAuf -> Deutung("obv", 2, "schwach_auf", p)
                else -> Deutung("obv", 2, "schwach_ab", p)
            }
        }
        return aus
    }

    // Average True Range nach Wilder: mittlere Tagesspanne ueber n Tage, Luecken zum Vortag eingerechnet.
    fun atr(hoch: List<Double>, tief: List<Double>, schluss: List<Double>, n: Int = 14): List<Double?> {
        val tr = schluss.indices.map { i ->
            if (i == 0) hoch[0] - tief[0]
            else maxOf(hoch[i] - tief[i], abs(hoch[i] - schluss[i - 1]), abs(tief[i] - schluss[i - 1]))
        }
        if (tr.size < n) return tr.map { null }
        val aus = ArrayList<Double?>(tr.size)
        var a = 0.0
        for (i in tr.indices) {
            when {
                i < n - 1 -> aus += null
                i == n - 1 -> { a = tr.subList(0, n).average(); aus += a }
                else -> { a = (a * (n - 1) + tr[i]) / n; aus += a }
            }
        }
        return aus
    }

    // On-Balance-Volume: Umsatz an Tagen mit steigendem Schlusskurs dazu, an fallenden ab.
    fun obv(schluss: List<Double>, umsatz: List<Double>): List<Double> {
        var s = 0.0
        return schluss.indices.map { i ->
            if (i > 0) s += when {
                schluss[i] > schluss[i - 1] -> umsatz[i]
                schluss[i] < schluss[i - 1] -> -umsatz[i]
                else -> 0.0
            }
            s
        }
    }

    // Ichimoku: tenkan 9 Tage, kijun 26 Tage, je Mitte aus Hoch und Tief. Wolke spanA/spanB um 26 Tage versetzt.
    data class Ichimoku(val tenkan: List<Double?>, val kijun: List<Double?>, val spanA: List<Double?>, val spanB: List<Double?>)

    fun ichimoku(hoch: List<Double>, tief: List<Double>, kurz: Int = 9, mittel: Int = 26, lang: Int = 52): Ichimoku {
        fun mitte(n: Int): List<Double?> = hoch.indices.map { i ->
            if (i < n - 1) null else ((i - n + 1..i).maxOf { hoch[it] } + (i - n + 1..i).minOf { tief[it] }) / 2
        }
        val t = mitte(kurz)
        val k = mitte(mittel)
        val b = mitte(lang)
        val a = hoch.indices.map { i -> val x = t[i]; val y = k[i]; if (x != null && y != null) (x + y) / 2 else null }
        fun versetzt(r: List<Double?>): List<Double?> = hoch.indices.map { i -> if (i >= mittel) r[i - mittel] else null }
        return Ichimoku(t, k, versetzt(a), versetzt(b))
    }

    // Parabolic SAR nach Wilder: Stopp, der dem Trend mit wachsender Schrittweite folgt und bei Bruch die Seite wechselt.
    data class Sar(val wert: List<Double?>, val aufwaerts: List<Boolean>)

    fun sar(hoch: List<Double>, tief: List<Double>, schritt: Double = 0.02, hoechstens: Double = 0.2): Sar {
        val n = hoch.size
        if (n < 3) return Sar(List(n) { null }, List(n) { true })
        val wert = ArrayList<Double?>(n)
        val auf = ArrayList<Boolean>(n)
        var steigt = hoch[1] >= hoch[0]
        var s = if (steigt) tief[0] else hoch[0]
        var ep = if (steigt) hoch[0] else tief[0]
        var af = schritt
        wert += null
        auf += steigt
        for (i in 1 until n) {
            var neu = s + af * (ep - s)
            val v1 = i - 1
            val v2 = if (i >= 2) i - 2 else i - 1
            if (steigt) {
                neu = minOf(neu, tief[v1], tief[v2])
                if (tief[i] < neu) { steigt = false; neu = ep; ep = tief[i]; af = schritt }
                else if (hoch[i] > ep) { ep = hoch[i]; af = minOf(af + schritt, hoechstens) }
            } else {
                neu = maxOf(neu, hoch[v1], hoch[v2])
                if (hoch[i] > neu) { steigt = true; neu = ep; ep = hoch[i]; af = schritt }
                else if (tief[i] < ep) { ep = tief[i]; af = minOf(af + schritt, hoechstens) }
            }
            s = neu
            wert += s
            auf += steigt
        }
        return Sar(wert, auf)
    }

    // Stochastik: %K ist die Lage des Schlusskurses zwischen Tief und Hoch ueber n Tage, %D sein Durchschnitt.
    data class Stochastik(val k: List<Double?>, val d: List<Double?>)

    fun stochastik(hoch: List<Double>, tief: List<Double>, schluss: List<Double>, n: Int = 14, glaettung: Int = 3): Stochastik {
        val k = schluss.indices.map { i ->
            if (i < n - 1) null else {
                val h = (i - n + 1..i).maxOf { hoch[it] }
                val t = (i - n + 1..i).minOf { tief[it] }
                if (h - t <= 1e-12) 50.0 else (schluss[i] - t) / (h - t) * 100.0
            }
        }
        val start = k.indexOfFirst { it != null }
        val d: List<Double?> = if (start < 0) k.map { null } else List(start) { null } + sma(k.drop(start).map { it!! }, glaettung)
        return Stochastik(k, d)
    }

    // Erweiterter Chart

    // CCI: Abstand des typischen Kurses von seinem Durchschnitt ueber n, geteilt durch 0,015 x mittlere Abweichung.
    fun cci(hoch: List<Double>, tief: List<Double>, schluss: List<Double>, n: Int = 20): List<Double?> {
        val tp = schluss.indices.map { (hoch[it] + tief[it] + schluss[it]) / 3 }
        return tp.indices.map { i ->
            if (i < n - 1) null else {
                val f = tp.subList(i - n + 1, i + 1)
                val m = f.average()
                val abw = f.sumOf { abs(it - m) } / n
                if (abw <= 1e-12) 0.0 else (tp[i] - m) / (0.015 * abw)
            }
        }
    }

    // Williams %R: Lage des Schlusskurses unter dem Hoch der letzten n Werte, 0 am Hoch bis −100 am Tief.
    fun williamsR(hoch: List<Double>, tief: List<Double>, schluss: List<Double>, n: Int = 14): List<Double?> =
        schluss.indices.map { i ->
            if (i < n - 1) null else {
                val h = (i - n + 1..i).maxOf { hoch[it] }
                val t = (i - n + 1..i).minOf { tief[it] }
                if (h - t <= 1e-12) -50.0 else (h - schluss[i]) / (h - t) * -100.0
            }
        }

    // Money Flow Index: RSI mit Umsatzgewichtung, 0 bis 100.
    fun mfi(hoch: List<Double>, tief: List<Double>, schluss: List<Double>, umsatz: List<Double>, n: Int = 14): List<Double?> {
        val tp = schluss.indices.map { (hoch[it] + tief[it] + schluss[it]) / 3 }
        return tp.indices.map { i ->
            if (i < n) null else {
                var auf = 0.0
                var ab = 0.0
                for (j in i - n + 1..i) {
                    val fluss = tp[j] * umsatz[j]
                    if (tp[j] > tp[j - 1]) auf += fluss else if (tp[j] < tp[j - 1]) ab += fluss
                }
                if (auf + ab <= 0) null else if (ab <= 0) 100.0 else 100.0 - 100.0 / (1.0 + auf / ab)
            }
        }
    }

    // ADX nach Wilder mit den Richtungslinien +DI und −DI, alle 0 bis 100.
    data class Adx(val adx: List<Double?>, val plus: List<Double?>, val minus: List<Double?>)

    fun adx(hoch: List<Double>, tief: List<Double>, schluss: List<Double>, n: Int = 14): Adx {
        val m = schluss.size
        val leer = Adx(List(m) { null }, List(m) { null }, List(m) { null })
        if (m <= 2 * n) return leer
        val tr = DoubleArray(m)
        val pdm = DoubleArray(m)
        val mdm = DoubleArray(m)
        for (i in 1 until m) {
            tr[i] = maxOf(hoch[i] - tief[i], abs(hoch[i] - schluss[i - 1]), abs(tief[i] - schluss[i - 1]))
            val auf = hoch[i] - hoch[i - 1]
            val ab = tief[i - 1] - tief[i]
            pdm[i] = if (auf > ab && auf > 0) auf else 0.0
            mdm[i] = if (ab > auf && ab > 0) ab else 0.0
        }
        val plus = arrayOfNulls<Double>(m)
        val minus = arrayOfNulls<Double>(m)
        val dx = arrayOfNulls<Double>(m)
        var str = (1..n).sumOf { tr[it] }
        var sp = (1..n).sumOf { pdm[it] }
        var sm = (1..n).sumOf { mdm[it] }
        for (i in n until m) {
            if (i > n) { str = str - str / n + tr[i]; sp = sp - sp / n + pdm[i]; sm = sm - sm / n + mdm[i] }
            val p = if (str > 0) 100 * sp / str else 0.0
            val q = if (str > 0) 100 * sm / str else 0.0
            plus[i] = p; minus[i] = q
            dx[i] = if (p + q > 0) 100 * abs(p - q) / (p + q) else 0.0
        }
        val adx = arrayOfNulls<Double>(m)
        var a = (n until 2 * n).sumOf { dx[it] ?: 0.0 } / n
        adx[2 * n - 1] = a
        for (i in 2 * n until m) { a = (a * (n - 1) + (dx[i] ?: 0.0)) / n; adx[i] = a }
        return Adx(adx.toList(), plus.toList(), minus.toList())
    }

    // Rate of Change: Veraenderung in Prozent gegen den Wert vor n Punkten.
    fun roc(schluss: List<Double>, n: Int = 12): List<Double?> =
        schluss.indices.map { i -> if (i < n || schluss[i - n] == 0.0) null else (schluss[i] / schluss[i - n] - 1.0) * 100.0 }

    // Aroon: wie lange Hoch bzw. Tief der letzten n Punkte zurueckliegen. 100 gerade eben, 0 vor n Punkten.
    data class Aroon(val auf: List<Double?>, val ab: List<Double?>)

    fun aroon(hoch: List<Double>, tief: List<Double>, n: Int = 25): Aroon {
        val auf = hoch.indices.map { i ->
            if (i < n) null else {
                var b = i - n
                for (j in i - n..i) if (hoch[j] >= hoch[b]) b = j
                100.0 * (n - (i - b)) / n
            }
        }
        val ab = tief.indices.map { i ->
            if (i < n) null else {
                var b = i - n
                for (j in i - n..i) if (tief[j] <= tief[b]) b = j
                100.0 * (n - (i - b)) / n
            }
        }
        return Aroon(auf, ab)
    }

    // Chaikin Money Flow: Lage der Schlusskurse in ihrer Spanne, mit dem Umsatz gewichtet, −1 bis 1.
    fun cmf(hoch: List<Double>, tief: List<Double>, schluss: List<Double>, umsatz: List<Double>, n: Int = 20): List<Double?> {
        val mfv = schluss.indices.map { i ->
            val spanne = hoch[i] - tief[i]
            if (spanne <= 1e-12) 0.0 else ((schluss[i] - tief[i]) - (hoch[i] - schluss[i])) / spanne * umsatz[i]
        }
        return schluss.indices.map { i ->
            if (i < n - 1) null else {
                val u = (i - n + 1..i).sumOf { umsatz[it] }
                if (u <= 0) null else (i - n + 1..i).sumOf { mfv[it] } / u
            }
        }
    }

    // Keltner-Kanal: EMA ueber n plus und minus k x ATR ueber atrN.
    fun keltner(hoch: List<Double>, tief: List<Double>, schluss: List<Double>, n: Int = 20, k: Double = 2.0, atrN: Int = 10): Baender {
        val mitte = ema(schluss, n)
        val a = atr(hoch, tief, schluss, atrN)
        val oben = mitte.indices.map { i -> val m = mitte[i]; val x = a[i]; if (m != null && x != null) m + k * x else null }
        val unten = mitte.indices.map { i -> val m = mitte[i]; val x = a[i]; if (m != null && x != null) m - k * x else null }
        return Baender(mitte, oben, unten)
    }

    // Donchian-Kanal: hoechstes Hoch und tiefstes Tief der letzten n Punkte, dazu die Mitte.
    fun donchian(hoch: List<Double>, tief: List<Double>, n: Int = 20): Baender {
        val oben = hoch.indices.map { i -> if (i < n - 1) null else (i - n + 1..i).maxOf { hoch[it] } }
        val unten = tief.indices.map { i -> if (i < n - 1) null else (i - n + 1..i).minOf { tief[it] } }
        val mitte = oben.indices.map { i -> val o = oben[i]; val u = unten[i]; if (o != null && u != null) (o + u) / 2 else null }
        return Baender(mitte, oben, unten)
    }

    // VWAP: mit dem Umsatz gewichteter Durchschnittskurs, beginnt an jedem Handelstag neu.
    fun vwap(kerzen: List<Candle>, tagVon: (Long) -> Long): List<Double?> {
        var tag = Long.MIN_VALUE
        var summe = 0.0
        var menge = 0.0
        return kerzen.map { c ->
            val d = tagVon(c.t)
            if (d != tag) { tag = d; summe = 0.0; menge = 0.0 }
            summe += (c.high + c.low + c.close) / 3 * c.volume
            menge += c.volume
            if (menge > 0) summe / menge else null
        }
    }

    // Heikin-Ashi: geglaettete Kerzen, in denen Trends deutlicher werden.
    fun heikinAshi(kerzen: List<Candle>): List<Candle> {
        val aus = ArrayList<Candle>(kerzen.size)
        for ((i, c) in kerzen.withIndex()) {
            val schluss = (c.open + c.high + c.low + c.close) / 4
            val auf = if (i == 0) (c.open + c.close) / 2 else (aus[i - 1].open + aus[i - 1].close) / 2
            aus += Candle(c.t, auf, maxOf(c.high, auf, schluss), minOf(c.low, auf, schluss), schluss, c.volume)
        }
        return aus
    }

    // Legt eine Tagesreihe (Tagesnummer zu Wert) auf die Chartpunkte, je Punkt der Wert des Tags oder des letzten
    // Handelstags davor. faktor gleicht eine andere Waehrung oder Umrechnung der Kurse an.
    fun aufChart(candles: List<Candle>, tage: TreeMap<Long, Double>, faktor: Double = 1.0): List<Double?> =
        candles.map { c -> tage.floorEntry(Math.floorDiv(c.t, 86_400_000L))?.value?.times(faktor) }
}
