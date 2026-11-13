package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ln

// „Im Preis?“ (5.92): ob ein fruehes Signal oder ein datiertes Ereignis schon im Preis steckt.
//
// Fruehe Signale: Aus der Geschichte wird geschaetzt, wie stark der Markt frueher auf das Signal reagierte (x das
// Signal, etwa die Abweichung des Oellagers vom Fuenfjahresschnitt, y die Antwort des Markts, etwa die Spanne der
// Terminkurve). Lineare Regression, wahlweise mit einem eigenen Achsenabschnitt je Kalendermonat, damit
// jahreszeitliche Muster (Heizsaison, Sommerbenzin) nicht als Signal zaehlen. Ausreisser in x und y werden vor der
// Schaetzung gekappt (etwa der negative Oelpreis im April 2020 oder die Erstantraege im ersten Lockdown). Heute: Welche Antwort waere bei
// diesem Signal ueblich, welche zeigt der Markt? Die Restgroesse wird gegen alle frueheren Restgroessen eingeordnet.
// Ist der Zusammenhang zu schwach oder hat die falsche Richtung, gibt es keine Einordnung.
//
// Datierte Signale (Insiderkauf, Hochstufung, Kauf eines Abgeordneten): Wie weit lief der Kurs seit dem Signal gegen
// den Heimatindex, verglichen mit dem, was solche Signale bei diesem Wert frueher im Schnitt brachten.
object Eingepreist {

    // ------------------------------------------------------------ Schaetzung

    data class Punkt(val tag: Long, val x: Double, val y: Double)

    enum class Staerke { KEINE, SCHWACH, MITTEL, DEUTLICH }

    // Bestimmtheitsmass ab dem ein Zusammenhang als schwach, mittel oder deutlich gilt; darunter keine Einordnung.
    const val R2_SCHWACH = 0.10
    const val R2_MITTEL = 0.25
    const val R2_DEUTLICH = 0.45

    // Mindestzahl gemeinsamer Wochen fuer eine Schaetzung (etwa zwei Jahre).
    const val MIN_PUNKTE = 100

    // steigung: Aenderung von y je Einheit x. achse: Achsenabschnitt je Monat (1 bis 12, ohne Saison nur 0).
    // r2: Anteil der Schwankung von y (nach Abzug der Monatsmuster), den x erklaert. reste: Restgroessen aufsteigend.
    data class Schaetzung(
        val steigung: Double,
        val achse: Map<Int, Double>,
        val r2: Double,
        val n: Int,
        val von: Long,
        val bis: Long,
        val reste: DoubleArray,
        val staerke: Staerke,
        val saison: Boolean,
        // Bereich von x, auf den gekappt wurde
        val xMin: Double = Double.NEGATIVE_INFINITY,
        val xMax: Double = Double.POSITIVE_INFINITY,
    ) {
        fun erwartet(x: Double, monat: Int): Double =
            (achse[if (saison) monat else 0] ?: achse.values.average()) + steigung * x.coerceIn(xMin, xMax)
    }

    fun staerke(r2: Double, steigung: Double, vorzeichen: Int): Staerke = when {
        vorzeichen != 0 && steigung * vorzeichen <= 0 -> Staerke.KEINE
        r2 < R2_SCHWACH -> Staerke.KEINE
        r2 < R2_MITTEL -> Staerke.SCHWACH
        r2 < R2_DEUTLICH -> Staerke.MITTEL
        else -> Staerke.DEUTLICH
    }

    private fun monat(tag: Long): Int = LocalDate.ofEpochDay(tag).monthValue

    // Wert an der Stelle p (0 bis 1) einer aufsteigend sortierten Liste.
    internal fun quantil(sortiert: DoubleArray, p: Double): Double {
        if (sortiert.isEmpty()) return Double.NaN
        val i = (p * (sortiert.size - 1)).coerceIn(0.0, (sortiert.size - 1).toDouble())
        val lo = i.toInt()
        val hi = minOf(lo + 1, sortiert.size - 1)
        return sortiert[lo] + (sortiert[hi] - sortiert[lo]) * (i - lo)
    }

    // Grenzen fuer das Kappen: das engere von 1. bis 99. Perzentil und Quartilen plus dreifachem Quartilsabstand.
    // Allein die Perzentile reichen nicht, wenn ein Ausnahmejahr mehr als ein Prozent der Wochen stellt.
    internal fun grenzen(sortiert: DoubleArray): Pair<Double, Double> {
        val q1 = quantil(sortiert, 0.25)
        val q3 = quantil(sortiert, 0.75)
        val iqr = q3 - q1
        return maxOf(quantil(sortiert, 0.01), q1 - 3 * iqr) to minOf(quantil(sortiert, 0.99), q3 + 3 * iqr)
    }

    // Lineare Regression von y auf x, mit saison je Monat eigener Achsenabschnitt (gemeinsame Steigung). vorzeichen:
    // erwartete Richtung des Zusammenhangs (+1, -1 oder 0 fuer egal); die falsche Richtung gilt als kein Zusammenhang.
    fun schaetzen(punkte: List<Punkt>, saison: Boolean, vorzeichen: Int, minPunkte: Int = MIN_PUNKTE): Schaetzung? {
        val p = punkte.filter { it.x.isFinite() && it.y.isFinite() }.sortedBy { it.tag }
        if (p.size < minPunkte) return null
        val ys = p.map { it.y }.sorted().toDoubleArray()
        val (lo, hi) = grenzen(ys)
        val y = DoubleArray(p.size) { p[it].y.coerceIn(lo, hi) }
        val xs = p.map { it.x }.sorted().toDoubleArray()
        val (xLo, xHi) = grenzen(xs)
        val x = DoubleArray(p.size) { p[it].x.coerceIn(xLo, xHi) }
        val gruppe = IntArray(p.size) { if (saison) monat(p[it].tag) else 0 }
        val mx = HashMap<Int, Double>()
        val my = HashMap<Int, Double>()
        val zahl = HashMap<Int, Int>()
        for (i in p.indices) {
            val g = gruppe[i]
            mx[g] = (mx[g] ?: 0.0) + x[i]
            my[g] = (my[g] ?: 0.0) + y[i]
            zahl[g] = (zahl[g] ?: 0) + 1
        }
        for (g in zahl.keys) { mx[g] = mx[g]!! / zahl[g]!!; my[g] = my[g]!! / zahl[g]!! }
        var sxy = 0.0
        var sxx = 0.0
        var syy = 0.0
        for (i in p.indices) {
            val dx = x[i] - mx[gruppe[i]]!!
            val dy = y[i] - my[gruppe[i]]!!
            sxy += dx * dy; sxx += dx * dx; syy += dy * dy
        }
        if (sxx <= 0 || syy <= 0) return null
        val b = sxy / sxx
        val achse = zahl.keys.associateWith { my[it]!! - b * mx[it]!! }
        val reste = DoubleArray(p.size) { y[it] - (achse[gruppe[it]]!! + b * x[it]) }
        val r2 = (1 - reste.sumOf { it * it } / syy).coerceIn(0.0, 1.0)
        reste.sort()
        return Schaetzung(b, achse, r2, p.size, p.first().tag, p.last().tag, reste, staerke(r2, b, vorzeichen), saison, xLo, xHi)
    }

    // ------------------------------------------------------------ Einordnung heute

    // Wie der Markt im Vergleich zum Signal steht: passend, hoeher oder tiefer als ueblich; UNKLAR ohne Zusammenhang.
    enum class Urteil { PASST, HOEHER, TIEFER, UNKLAR }

    // perzentil: Anteil der frueheren Restgroessen unter der heutigen (0 bis 100).
    data class Einordnung(
        val x: Double,
        val y: Double,
        val erwartet: Double,
        val rest: Double,
        val perzentil: Double,
        val urteil: Urteil,
        // null ohne verlaessliche Einordnung
        val ampel: Fruehwarnung.Ampel?,
        val schaetzung: Schaetzung,
        val tag: Long,
    )

    // Grenzen fuer das Urteil: zwischen 20. und 80. Perzentil passt es, ausserhalb 5 bis 95 ist es ungewoehnlich (rot).
    fun einordnen(s: Schaetzung, x: Double, y: Double, tag: Long): Einordnung {
        val erwartet = s.erwartet(x, monat(tag))
        val rest = y - erwartet
        val unter = s.reste.count { it < rest }
        val gleich = s.reste.count { it == rest }
        val perzentil = 100.0 * (unter + gleich / 2.0) / s.reste.size
        val urteil = when {
            s.staerke == Staerke.KEINE -> Urteil.UNKLAR
            perzentil < 20 -> Urteil.TIEFER
            perzentil > 80 -> Urteil.HOEHER
            else -> Urteil.PASST
        }
        val ampel = when {
            urteil == Urteil.UNKLAR -> null
            perzentil < 5 || perzentil > 95 -> Fruehwarnung.Ampel.ROT
            urteil == Urteil.PASST -> Fruehwarnung.Ampel.GRUEN
            else -> Fruehwarnung.Ampel.GELB
        }
        return Einordnung(x, y, erwartet, rest, perzentil, urteil, ampel, s, tag)
    }

    // Tendenz: in welche Richtung sich der Markt bewegen muesste, damit er wieder zum Signal passt. +1 eher long,
    // -1 eher short, 0 keine klare Richtung. Steht der Markt hoeher als ueblich, ist er eher zu teuer (short).
    // umgekehrt: bei den Zinsen heisst eine zu hohe Rendite steigende Anleihekurse, also long bei Anleihen.
    fun tendenz(e: Einordnung, umgekehrt: Boolean = false): Int {
        val r = when (e.urteil) {
            Urteil.HOEHER -> -1
            Urteil.TIEFER -> 1
            else -> 0
        }
        return if (umgekehrt) -r else r
    }

    // ------------------------------------------------------------ Reihen fuer die Schaetzung

    // Index des letzten Punkts am Tag t oder davor, hoechstens toleranz Tage frueher; -1 ohne.
    private fun bis(reihe: List<Pair<Long, Double>>, t: Long, toleranz: Int): Int {
        var lo = 0
        var hi = reihe.size
        while (lo < hi) {
            val m = (lo + hi) ushr 1
            if (reihe[m].first <= t) lo = m + 1 else hi = m
        }
        val i = lo - 1
        return if (i >= 0 && t - reihe[i].first <= toleranz) i else -1
    }

    // Wert am Tag t oder bis toleranz Tage davor.
    fun am(reihe: List<Pair<Long, Double>>, t: Long, toleranz: Int = 6): Double? = bis(reihe, t, toleranz).takeIf { it >= 0 }?.let { reihe[it].second }

    // Punkte aus zwei Reihen: zu jedem x-Tag der y-Wert am selben Tag oder bis toleranz Tage davor.
    fun paaren(x: List<Pair<Long, Double>>, y: List<Pair<Long, Double>>, toleranz: Int = 6): List<Punkt> {
        val ys = y.sortedBy { it.first }
        return x.mapNotNull { (t, a) -> am(ys, t, toleranz)?.let { Punkt(t, a, it) } }
    }

    // Abweichung jedes Stands vom Schnitt derselben Jahreszeit der fuenf Vorjahre, genau wie Fruehindikatoren.lager
    // (je Vorjahr der naechste Punkt hoechstens toleranz Tage entfernt, mindestens drei Jahre). punkte: in Punkten,
    // sonst in Prozent.
    fun abweichungen(reihe: List<Pair<Long, Double>>, punkte: Boolean = false, jahre: Int = 5, toleranz: Int = 4): List<Pair<Long, Double>> {
        val r = reihe.filter { it.second.isFinite() }.sortedBy { it.first }
        val tage = LongArray(r.size) { r[it].first }
        fun naechster(ziel: Long): Double? {
            var i = java.util.Arrays.binarySearch(tage, ziel)
            if (i < 0) i = -i - 1
            var best = -1
            // Bei gleichem Abstand der fruehere Punkt, wie minByOrNull in Fruehindikatoren.band
            for (k in intArrayOf(i - 1, i)) {
                if (k < 0 || k >= tage.size || abs(tage[k] - ziel) > toleranz) continue
                if (best < 0 || abs(tage[k] - ziel) < abs(tage[best] - ziel)) best = k
            }
            return if (best >= 0) r[best].second else null
        }
        return r.mapNotNull { (t, v) ->
            val d = LocalDate.ofEpochDay(t)
            val werte = (1..jahre).mapNotNull { j -> naechster(d.minusYears(j.toLong()).toEpochDay()) }
            if (werte.size < 3) return@mapNotNull null
            val s = werte.average()
            if (punkte) t to v - s else if (s > 0) t to (v / s - 1) * 100 else null
        }
    }

    // Stand gegen den Schnitt der letzten tage Tage in Prozent (etwa der Preis gegen seinen Jahresschnitt).
    fun gegenSchnitt(reihe: List<Pair<Long, Double>>, tage: Int = 364, mindestens: Int = 30): List<Pair<Long, Double>> {
        val r = reihe.filter { it.second.isFinite() && it.second > 0 }.sortedBy { it.first }
        var anfang = 0
        var summe = 0.0
        val aus = ArrayList<Pair<Long, Double>>()
        for (i in r.indices) {
            summe += r[i].second
            while (r[anfang].first < r[i].first - tage) { summe -= r[anfang].second; anfang++ }
            val n = i - anfang + 1
            if (n >= mindestens && r[0].first <= r[i].first - tage + 14) aus += r[i].first to (r[i].second / (summe / n) - 1) * 100
        }
        return aus
    }

    // Stand minus Hoechststand der letzten tage Tage (bei Renditen in Prozentpunkten).
    fun unterHoch(reihe: List<Pair<Long, Double>>, tage: Int = 364): List<Pair<Long, Double>> {
        val r = reihe.filter { it.second.isFinite() }.sortedBy { it.first }
        val fenster = java.util.ArrayDeque<Int>()
        val aus = ArrayList<Pair<Long, Double>>()
        for (i in r.indices) {
            while (fenster.isNotEmpty() && r[fenster.peekLast()].second <= r[i].second) fenster.pollLast()
            fenster.addLast(i)
            while (r[fenster.peekFirst()].first < r[i].first - tage) fenster.pollFirst()
            if (r[0].first <= r[i].first - tage + 14) aus += r[i].first to r[i].second - r[fenster.peekFirst()].second
        }
        return aus
    }

    // Wertentwicklung von a gegen b ueber tage Tage in Prozent, je Punkt von a.
    fun relativ(a: List<Pair<Long, Double>>, b: List<Pair<Long, Double>>, tage: Int, toleranz: Int = 7): List<Pair<Long, Double>> {
        val ra = a.filter { it.second > 0 }.sortedBy { it.first }
        val rb = b.filter { it.second > 0 }.sortedBy { it.first }
        return ra.mapNotNull { (t, v) ->
            val a0 = am(ra, t - tage, toleranz) ?: return@mapNotNull null
            val b1 = am(rb, t, toleranz) ?: return@mapNotNull null
            val b0 = am(rb, t - tage, toleranz) ?: return@mapNotNull null
            t to ((v / a0) / (b1 / b0) - 1) * 100
        }
    }

    // Monatsreihe gegen denselben Monat des Vorjahrs in Prozent, je Monat mit Vorjahreswert (wie
    // Ersatzquellen.jahresvergleich).
    fun monatsVorjahr(reihe: List<Pair<Long, Double>>): List<Pair<Long, Double>> {
        val r = reihe.filter { it.second.isFinite() && it.second > 0 }.sortedBy { it.first }
        val je = r.associate { java.time.YearMonth.from(LocalDate.ofEpochDay(it.first)) to it.second }
        return r.mapNotNull { (t, v) ->
            je[java.time.YearMonth.from(LocalDate.ofEpochDay(t)).minusYears(1)]?.let { t to (v / it - 1) * 100 }
        }
    }

    // Erstantraege wie Fruehindikatoren.erstantraege fuer jede Woche: Vierwochenschnitt gegen sein Tief der letzten
    // 52 Wochen in Prozent.
    fun anstiege(reihe: List<Pair<Long, Double>>): List<Pair<Long, Double>> {
        val r = reihe.filter { it.second > 0 }.sortedBy { it.first }
        if (r.size < 56) return emptyList()
        val schnitte = (3 until r.size).map { i -> r[i].first to (i - 3..i).sumOf { r[it].second } / 4 }
        return (51 until schnitte.size).map { i ->
            val tief = (i - 51..i).minOf { schnitte[it].second }
            schnitte[i].first to (schnitte[i].second / tief - 1) * 100
        }
    }

    // Fluggaeste wie Fruehindikatoren.fluggaeste fuer jeden siebten Tag rueckwaerts vom letzten: Summe von 28 Tagen
    // gegen dieselben Wochentage ein Jahr zuvor in Prozent.
    fun jahresvergleiche(tage: List<Pair<Long, Double>>): List<Pair<Long, Double>> {
        val k = tage.filter { it.second > 0 }.associate { it.first to it.second }
        val letzter = k.keys.maxOrNull() ?: return emptyList()
        val erster = k.keys.min()
        val aus = ArrayList<Pair<Long, Double>>()
        var t = letzter
        while (t - 364 - 27 >= erster) {
            val paare = (0 until 28).mapNotNull { i -> k[t - i]?.let { a -> k[t - i - 364]?.let { b -> a to b } } }
            if (paare.size >= 25) aus += t to (paare.sumOf { it.first } / paare.sumOf { it.second } - 1) * 100
            t -= 7
        }
        return aus.reversed()
    }

    // ------------------------------------------------------------ Terminkurve

    // Spanne zwischen naechstem und spaeterem Kontrakt auf ein Jahr gerechnet in Prozent (log): positiv heisst, der
    // nahe Kontrakt ist teurer (Backwardation, typisch bei knappem Lager), negativ heisst Contango.
    fun spanne(nah: Double, fern: Double, monate: Int): Double? =
        if (nah > 0 && fern > 0 && monate > 0) ln(nah / fern) * 1200.0 / monate else null

    // Monatskennbuchstaben der Terminboersen, Januar bis Dezember.
    private const val MONATE = "FGHJKMNQUVXZ"

    // Ein Kontrakt mit Liefermonat und Kuerzel bei Yahoo, etwa CLZ26.NYM.
    data class Kontrakt(val jahr: Int, val monat: Int, val symbol: String) {
        val nummer: Int get() = jahr * 12 + monat - 1
    }

    // Kandidaten ab dem laufenden Monat; die abgelaufenen fallen spaeter weg, weil Yahoo keine frischen Kurse hat.
    fun kontrakte(wurzel: String, boerse: String, jahr: Int, monat: Int, anzahl: Int = 7): List<Kontrakt> = (0 until anzahl).map { i ->
        val m0 = monat - 1 + i
        val j = jahr + m0 / 12
        val m = m0 % 12 + 1
        Kontrakt(j, m, wurzel + MONATE[m - 1] + (j % 100).toString().padStart(2, '0') + "." + boerse)
    }

    // Die vier naechsten handelbaren Kontrakte mit ihrem letzten Schlusskurs. Ein Kontrakt zaehlt nur, wenn sein
    // letzter Kurs hoechstens vier Tage hinter dem juengsten aller Kandidaten liegt (sonst ist er abgelaufen).
    fun kurve(kandidaten: List<Kontrakt>, kerzen: Map<String, List<Candle>>, anzahl: Int = 4): List<Pair<Kontrakt, Double>> {
        val letzte = kandidaten.mapNotNull { k -> kerzen[k.symbol]?.lastOrNull { it.close.isFinite() && it.close > 0 }?.let { k to it } }
        val neueste = letzte.maxOfOrNull { it.second.t } ?: return emptyList()
        return letzte.filter { neueste - it.second.t <= 4 * 86_400_000L }.sortedBy { it.first.nummer }.take(anzahl).map { it.first to it.second.close }
    }

    // Spanne der heutigen Kurve vom ersten zum letzten Kontrakt; null bei weniger als vier Kontrakten.
    fun kurvenSpanne(k: List<Pair<Kontrakt, Double>>): Double? {
        if (k.size < 4) return null
        return spanne(k.first().second, k.last().second, k.last().first.nummer - k.first().first.nummer)
    }

    // Spannen aus einer Tabelle der EIA (Tag, Kontrakte 1 bis 4): Kontrakt 1 gegen 4, drei Monate auseinander.
    fun spannen(tabelle: List<Pair<Long, DoubleArray>>): List<Pair<Long, Double>> =
        tabelle.mapNotNull { (t, k) -> if (k.size < 4) null else spanne(k[0], k[3], 3)?.let { t to it } }

    // ------------------------------------------------------------ Datierte Signale

    enum class Lauf { GROSSTEIL, TEIL, WENIG, GEGEN, OHNE_VORSPRUNG }

    // mehr: Rendite gegen den Index seit dem Signal (Anteil, 0,18 = 18 % besser). schnitt: was solche Signale bei
    // diesem Wert frueher im Schnitt in Signalrichtung brachten (Anteil), n Zahl der Faelle; null ohne genug Faelle.
    data class Gelaufen(val mehr: Double, val tage: Int, val schnitt: Double?, val n: Int, val lauf: Lauf, val ampel: Fruehwarnung.Ampel?, val richtung: Int)

    // Mindestzahl frueherer Faelle fuer einen eigenen Vergleichswert.
    const val MIN_FAELLE = 5

    // Richtwert ohne eigene Vergleichsfaelle: so viel Vorsprung gilt als „Grossteil eingepreist“ (Anteil).
    const val RICHTWERT = 0.10

    private fun tagVon(ms: Long) = Math.floorDiv(ms, 86_400_000L)

    // Index der ersten Kerze am Tag von ms oder danach; -1 ohne.
    private fun abTag(k: List<Candle>, ms: Long): Int {
        val d = tagVon(ms)
        var lo = 0
        var hi = k.size
        while (lo < hi) {
            val m = (lo + hi) ushr 1
            if (tagVon(k[m].t) < d) lo = m + 1 else hi = m
        }
        return if (lo < k.size) lo else -1
    }

    // Index der letzten Kerze am Tag von ms oder davor; -1 ohne.
    private fun bisTag(k: List<Candle>, ms: Long): Int {
        val d = tagVon(ms)
        var lo = 0
        var hi = k.size
        while (lo < hi) {
            val m = (lo + hi) ushr 1
            if (tagVon(k[m].t) <= d) lo = m + 1 else hi = m
        }
        return lo - 1
    }

    // Rendite gegen den Index vom Schluss am Signaltag (oder dem naechsten Handelstag) bis zum letzten Kurs; ohne Index
    // die einfache Rendite. Dazu die Zahl der Handelstage.
    fun seit(kurse: List<Candle>, index: List<Candle>?, zeit: Long): Pair<Double, Int>? {
        val i = abTag(kurse, zeit)
        if (i < 0) return null
        val j = kurse.size - 1
        val a = kurse[i].close
        val b = kurse[j].close
        if (!(a > 0) || !(b > 0)) return null
        if (index.isNullOrEmpty()) return (b / a - 1) to (j - i)
        val ia = bisTag(index, kurse[i].t)
        val ib = bisTag(index, kurse[j].t)
        if (ia < 0 || ib < 0 || ib < ia) return null
        val x = index[ia].close
        val y = index[ib].close
        if (!(x > 0) || !(y > 0)) return null
        return ((b / a) / (y / x) - 1) to (j - i)
    }

    // Was fruehere Signale bei diesem Wert nach tage Handelstagen gegen den Index brachten (je Signal ein Wert, nur
    // verstrichene). Signale am selben Tag zaehlen einmal.
    fun frueher(kurse: List<Candle>, index: List<Candle>?, zeiten: List<Long>, tage: Int = 126): List<Double> =
        zeiten.distinctBy { tagVon(it) }.mapNotNull { Analystenbilanz.mehrRendite(kurse, index, it, tage) }

    // Einordnung eines datierten Signals. richtung +1 fuer Kauf und Hochstufung, -1 fuer Abstufung und Verkauf.
    // frueher: Renditen gegen den Index nach frueheren Signalen derselben Art (ohne Richtung). richtwert gilt, wenn es
    // weniger als MIN_FAELLE fruehere Faelle gibt.
    fun gelaufen(kurse: List<Candle>, index: List<Candle>?, zeit: Long, richtung: Int, frueher: List<Double> = emptyList(), richtwert: Double = RICHTWERT): Gelaufen? {
        val (mehr, tage) = seit(kurse, index, zeit) ?: return null
        val r = if (richtung < 0) -1 else 1
        val m = mehr * r
        val schnitt = frueher.takeIf { it.size >= MIN_FAELLE }?.map { it * r }?.average()
        val lauf = when {
            m <= -0.03 -> Lauf.GEGEN
            schnitt != null && schnitt <= 0.005 -> Lauf.OHNE_VORSPRUNG
            else -> {
                val ziel = schnitt ?: richtwert
                when {
                    m >= 0.8 * ziel -> Lauf.GROSSTEIL
                    m >= 0.3 * ziel -> Lauf.TEIL
                    else -> Lauf.WENIG
                }
            }
        }
        val ampel = when (lauf) {
            Lauf.GROSSTEIL -> Fruehwarnung.Ampel.GRUEN
            Lauf.TEIL, Lauf.WENIG -> Fruehwarnung.Ampel.GELB
            Lauf.GEGEN -> Fruehwarnung.Ampel.ROT
            Lauf.OHNE_VORSPRUNG -> null
        }
        return Gelaufen(mehr, tage, schnitt, frueher.size, lauf, ampel, r)
    }

    // Tendenz bei datierten Signalen: Richtung des Signals, solange erst ein Teil gelaufen ist.
    fun tendenz(g: Gelaufen): Int = when (g.lauf) {
        Lauf.TEIL, Lauf.WENIG -> g.richtung
        else -> 0
    }
}
