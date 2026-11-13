package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Fruehwarnung.Ampel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// Rueckblick „Haette man es sehen koennen?“ [0.73]: Wie standen die Warnzeichen der App vor den grossen Einbruechen
// eines Index? Jedes Zeichen wird je Handelstag der Vergangenheit mit den Regeln und Schwellen der App berechnet
// (Fruehwarnung, Blasen, Fruehindikator), und zwar nur mit Werten, die an diesem Tag schon veroeffentlicht waren
// (Reihe.verzug). Gebaut wird die Datei mit :desktop:warnzeichenRueckblick, mitgeliefert als
// assets/indikatoren/warnzeichen_rueckblick.json; die Anzeige liest sie ueber WarnRueckblick.datei().
// Alle Tage sind Tage seit 1970.
object WarnRueckblick {

    const val TAG_MS = 86_400_000L

    // Ein Einbruch ist ein Rueckgang vom Hoch um mindestens 20 %, ein Rueckgang um 10 bis unter 20 %.
    const val EINBRUCH = 20.0
    const val RUECKGANG = 10.0

    // Fenster vor einem Einbruch, in dem ein Zeichen auf Warnung gestanden haben muss (Tage), und die Pause ohne
    // Warnung, ab der eine Warnphase als beendet gilt.
    const val FENSTER = 365
    const val RUHE = 183

    // Stichtage vor dem Hoch in Monaten
    val STICHTAGE = listOf(12, 6, 3, 1)

    // Kosten je Wechsel zwischen Index und Bargeld (Anteil des Vermoegens); Steuern fehlen.
    const val KOSTEN_WECHSEL = 0.001

    // ------------------------------------------------------------ Schluessel

    const val ZINSKURVE = "zinskurve"
    const val ZINSKURVE_2J = "zinskurve_2j"
    const val SAHM = "sahm"
    const val VIX = "vix"
    const val VIX_STRUKTUR = "vix_struktur"
    const val TREND = "trend"
    const val STRESS = "stress"
    const val CAPE = "cape"
    const val KGV = "kgv"
    const val PRAEMIE = "praemie"
    const val HITZE = "hitze"
    const val FRUEHINDIKATOR = "cli"
    const val OEL = "oel"
    const val STIMMUNG_VIX = "stimmung_vix"
    const val STIMMUNG_SICHER = "stimmung_sicher"
    const val HOCHZINS = "hochzins"
    const val MARGIN = "margin"
    const val BUFFETT = "buffett"
    const val ERSTANTRAEGE = "erstantraege"
    const val ZUSAMMEN = "zusammen"

    // Veroeffentlichungsverzug je Reihe in Tagen nach dem Messtag (Monatswerte tragen den ersten Tag des Monats): Arbeitsmarkt
    // etwa einen Monat nach Monatsende, CAPE einen Monat Preise plus einen Monat Gewinne, OECD-Fruehindikator rund zwei Monate,
    // Shiller-Gewinne ein Quartal plus Berichtszeit, NFCI am Mittwoch nach der Woche, Oel am naechsten Tag.
    const val VERZUG_ARBEIT = 38
    const val VERZUG_CAPE = 62
    const val VERZUG_GEWINN = 120
    const val VERZUG_CLI = 70
    const val VERZUG_NFCI = 5
    const val VERZUG_OEL = 2

    // Die Zeichen, die die App heute ueber FRED laedt, kommen im Rueckblick von den Herausgebern der Reihen (FRED antwortet
    // Skripten nicht). Kreditaufschlag nach Gilchrist und Zakrajsek (Fed, Monatsmittel mit dem ersten Tag des Monats, bekannt
    // nach Monatsende), Finanzierungsrechnung Z.1 der Fed (Quartalsende, erscheint gut zehn Wochen danach), BIP des BEA
    // (Quartalsende, erste Schaetzung rund vier Wochen danach), Erstantraege des US-Arbeitsministeriums (Woche bis Samstag,
    // veroeffentlicht am Donnerstag danach).
    const val VERZUG_KREDIT = 35
    const val VERZUG_Z1 = 72
    const val VERZUG_BIP = 30
    const val VERZUG_ANTRAEGE = 5

    // Der Hochzins-Spread (ICE BofA) steht bei FRED nur fuer drei Jahre. Ersatz ist der Kreditaufschlag nach Gilchrist und
    // Zakrajsek ab 1973. Er liegt seit 1997 im Median bei 1,9 Punkten (Streuung 1,0), der Hochzins-Spread bei etwa 4,7
    // (Streuung etwa 2,5): Mit dem Faktor 2,5 gelten die Schwellen der App unveraendert.
    const val KREDIT_FAKTOR = 2.5

    // Buffett-Indikator: Die App teilt den Wilshire 5000 (Punkte als Milliarden Dollar) durch das BIP. Die Tagesreihe des
    // Wilshire beginnt 1989; ab dann rechnet der Rueckblick mit ihr. Davor steht der Boersenwert aus der Finanzierungsrechnung
    // der Fed (nur nicht-finanzielle Unternehmen, deshalb niedriger), mit dem Verhaeltnis beider Reihen an den ersten
    // gemeinsamen Quartalsenden auf die Hoehe des Wilshire gebracht. So gelten die Schwellen der App fuer die ganze Reihe.
    const val ANSCHLUSS_QUARTALE = 8

    // Namen der Eingangsreihen
    const val R_KURVE = "kurve"
    const val R_KURVE_2J = "kurve2j"
    const val R_ARBEIT = "arbeit"
    const val R_VIX = "vix"
    const val R_VIX3M = "vix3m"
    const val R_INDEX = "index"
    const val R_SP = "sp500"
    const val R_NFCI = "nfci"
    const val R_CAPE = "cape"
    const val R_GEWINN = "gewinn"
    const val R_ZINS10 = "zins10"
    const val R_CLI = "cli"
    const val R_OEL = "oel"
    const val R_ANLEIHEN = "anleihen"
    const val R_KREDIT = "kredit"
    const val R_MARGIN = "margin"
    const val R_AKTIEN_WERT = "aktienwert"
    const val R_WILSHIRE = "wilshire"
    const val R_BIP = "bip"
    const val R_ANTRAEGE = "antraege"

    // Zeichen, die in der Zusammenschau zaehlen (ohne Doppelungen wie die zweite Zinskurve, das KGV und den Buffett-Indikator
    // neben dem CAPE oder die Erstantraege neben der Sahm-Regel).
    val ZAEHLT = listOf(ZINSKURVE, SAHM, VIX, TREND, STRESS, CAPE, FRUEHINDIKATOR, HITZE, PRAEMIE, OEL, STIMMUNG_VIX, HOCHZINS, MARGIN)

    // Zusammenschau ab so vielen gleichzeitigen Warnungen; ab MIN_VERFUEGBAR Zeichen mit Daten.
    val ZUSAMMEN_AB = listOf(2, 3, 4)
    const val MIN_VERFUEGBAR = 4

    // ------------------------------------------------------------ Reihen mit Veroeffentlichungsverzug

    // Eine Messreihe: Messtag, Wert, und wie viele Tage nach dem Messtag der Wert erst bekannt war. maxAlter: wie alt der
    // letzte bekannte Wert hoechstens sein darf (Tage seit Veroeffentlichung), sonst gilt „keine Daten“.
    class Reihe(val tage: LongArray, val werte: DoubleArray, val verzug: Int = 0, val maxAlter: Int = 10) {
        init { require(tage.size == werte.size) }

        // Zahl der Werte, die am Tag t schon bekannt waren
        fun anzahl(t: Long): Int {
            var lo = 0
            var hi = tage.size
            while (lo < hi) {
                val m = (lo + hi) ushr 1
                if (tage[m] + verzug <= t) lo = m + 1 else hi = m
            }
            return lo
        }

        // Anzahl der bekannten Werte, null wenn keiner oder der letzte zu alt ist
        fun aktuell(t: Long): Int? {
            val n = anzahl(t)
            return if (n == 0 || t - (tage[n - 1] + verzug) > maxAlter) null else n
        }

        // Die letzten k bekannten Werte als (Zeit in ms, Wert) fuer die Regeln der App
        fun paare(n: Int, k: Int): List<Pair<Long, Double>> = (max(0, n - k) until n).map { tage[it] * TAG_MS to werte[it] }
    }

    class Quellen(val reihen: Map<String, Reihe>)

    // Differenz zweier Reihen an Tagen, an denen beide einen Wert haben.
    fun differenz(a: Reihe, b: Reihe, verzug: Int = 0, maxAlter: Int = 10): Reihe {
        val bm = HashMap<Long, Double>()
        for (i in b.tage.indices) bm[b.tage[i]] = b.werte[i]
        val t = ArrayList<Long>()
        val w = ArrayList<Double>()
        for (i in a.tage.indices) bm[a.tage[i]]?.let { t += a.tage[i]; w += a.werte[i] - it }
        return Reihe(t.toLongArray(), w.toDoubleArray(), verzug, maxAlter)
    }

    // ------------------------------------------------------------ Zeichen: Regeln der App je Tag

    // Ampel als Zahl: 0 keine Daten, 1 gruen, 2 gelb, 3 rot
    private fun code(a: Ampel?): Int = when (a) { null -> 0; Ampel.GRUEN -> 1; Ampel.GELB -> 2; Ampel.ROT -> 3 }

    class Zeichen(
        val id: String, val gruppe: String, val warnAb: Int, val verzug: Int, val ersatz: Boolean = false,
        val revidiert: Boolean = false, val eigeneRegel: Boolean = false, val regel: (Quellen, Long) -> Int,
    )

    internal fun einfach(q: Quellen, name: String, t: Long): Pair<Reihe, Int>? {
        val r = q.reihen[name] ?: return null
        val n = r.aktuell(t) ?: return null
        return r to n
    }

    // Mittel der letzten 200 Schlusskurse bis zum Tag t: Abstand des Kurses in Prozent, null bei weniger als 200 Kursen
    internal fun abstand200(r: Reihe, n: Int): Double? {
        if (n < 200) return null
        var s = 0.0
        for (i in n - 200 until n) s += r.werte[i]
        return (r.werte[n - 1] / (s / 200) - 1) * 100
    }

    // Letzter Wert von r am Tag t oder davor, hoechstens `alter` Tage alt (nach Veroeffentlichung)
    internal fun letzterWert(r: Reihe?, t: Long, alter: Int = r?.maxAlter ?: 0): Double? {
        val x = r ?: return null
        val n = x.anzahl(t)
        if (n == 0 || t - (x.tage[n - 1] + x.verzug) > alter) return null
        return x.werte[n - 1]
    }

    // Kurs-Gewinn-Verhaeltnis: Kurs des S&P 500 am Tag t durch den zuletzt bekannten Gewinn (Shiller)
    internal fun kgvWert(q: Quellen, t: Long): Double? {
        val kurs = letzterWert(q.reihen[R_SP], t) ?: return null
        val gewinn = letzterWert(q.reihen[R_GEWINN], t) ?: return null
        return if (gewinn > 0) kurs / gewinn else null
    }

    // Margin-Kredite: Wachstum des letzten bekannten Quartals gegen das Quartal ein Jahr davor, in Prozent
    internal fun marginWachstum(q: Quellen, t: Long): Double? {
        val (r, n) = einfach(q, R_MARGIN, t) ?: return null
        val vor = lowerBound(r.tage, r.tage[n - 1] - 370)
        if (vor >= n - 1 || r.tage[vor] > r.tage[n - 1] - 360 || r.werte[vor] <= 0) return null
        return (r.werte[n - 1] / r.werte[vor] - 1) * 100
    }

    // Verhaeltnis Wilshire 5000 (Punkte) zum Boersenwert der Finanzierungsrechnung (Millionen Dollar, hier in Milliarden) an
    // den ersten ANSCHLUSS_QUARTALE Quartalsenden, an denen beide Reihen einen Wert haben: der Median. Null bei weniger
    // gemeinsamen Quartalen.
    fun anschlussFaktor(z1: Reihe, wilshire: Reihe): Double? {
        val v = ArrayList<Double>()
        for (i in z1.tage.indices) {
            if (v.size >= ANSCHLUSS_QUARTALE) break
            val n = lowerBound(wilshire.tage, z1.tage[i] + 1)
            if (n == 0 || z1.tage[i] - wilshire.tage[n - 1] > 10 || z1.werte[i] <= 0 || wilshire.werte[n - 1] <= 0) continue
            v += wilshire.werte[n - 1] / (z1.werte[i] / 1e3)
        }
        if (v.size < ANSCHLUSS_QUARTALE) return null
        v.sort()
        return (v[v.size / 2] + v[(v.size - 1) / 2]) / 2
    }

    // Boersenwert der Finanzierungsrechnung auf der Hoehe des Wilshire 5000; ohne gemeinsame Quartale unveraendert
    fun angeschlossen(z1: Reihe, wilshire: Reihe?): Reihe {
        val f = wilshire?.let { anschlussFaktor(z1, it) } ?: return z1
        return Reihe(z1.tage, DoubleArray(z1.werte.size) { z1.werte[it] * f }, z1.verzug, z1.maxAlter)
    }

    // Buffett-Indikator am Tag t wie in der App: Wilshire 5000 des Tages geteilt durch das letzte bekannte BIP. Vor dem
    // Beginn der Wilshire-Reihe der Boersenwert der US-Unternehmen zum letzten bekannten Quartalsende (angeschlossen, siehe
    // oben), mit dem S&P 500 bis zum Tag t fortgeschrieben. Rueckgabe: (Wert in Milliarden Dollar, BIP in Dollar).
    internal fun buffettWerte(q: Quellen, t: Long): Pair<Double, Double>? {
        val bip = letzterWert(q.reihen[R_BIP], t) ?: return null
        letzterWert(q.reihen[R_WILSHIRE], t)?.let { return it to bip * 1e6 }
        val (a, na) = einfach(q, R_AKTIEN_WERT, t) ?: return null
        val sp = q.reihen[R_SP] ?: return null
        val heute = letzterWert(sp, t) ?: return null
        val nq = sp.anzahl(a.tage[na - 1])
        if (nq == 0 || a.tage[na - 1] - sp.tage[nq - 1] > 10) return null
        val wert = a.werte[na - 1] * heute / sp.werte[nq - 1]
        // Z.1 und BEA in Millionen Dollar
        return wert / 1e3 to bip * 1e6
    }

    // Rang wie Stimmungsbarometer.vixBarometer: Anteil der Tage der letzten 260, an denen der VIX mindestens so hoch war
    private fun vixRang(r: Reihe, n: Int): Double? {
        if (n < 100) return null
        val x = r.werte[n - 1]
        val von = max(0, n - 260)
        var c = 0
        for (i in von until n) if (r.werte[i] >= x) c++
        return 100.0 * c / (n - von)
    }

    // Rang wie Stimmungsbarometer.sicherBarometer: Aktien minus Anleihen ueber 20 Tage gegen die letzten 260 Tage
    private fun sicherRang(q: Quellen, t: Long): Double? {
        val a = q.reihen[R_SP] ?: return null
        val b = q.reihen[R_ANLEIHEN] ?: return null
        val bm = HashMap<Long, Double>()
        for (i in b.tage.indices) if (b.tage[i] + b.verzug <= t) bm[b.tage[i]] = b.werte[i]
        val na = a.anzahl(t)
        // gemeinsame Tage, die letzten 260 + 20 + 1 reichen
        val tg = ArrayList<Long>()
        val ka = ArrayList<Double>()
        val kb = ArrayList<Double>()
        var i = na - 1
        while (i >= 0 && tg.size < 281) {
            bm[a.tage[i]]?.let { tg += a.tage[i]; ka += a.werte[i]; kb += it }
            i--
        }
        if (tg.size < 141) return null
        tg.reverse(); ka.reverse(); kb.reverse()
        if (t - tg.last() > 10) return null
        val d = (20 until tg.size).map { j -> (ka[j] / ka[j - 20] - 1) * 100 - (kb[j] / kb[j - 20] - 1) * 100 }
        val von = max(0, d.size - 260)
        val x = d.last()
        return 100.0 * (von until d.size).count { d[it] <= x } / (d.size - von)
    }

    fun zeichenListe(trendSchluessel: String = "trend_sp500"): List<Zeichen> = listOf(
        Zeichen(ZINSKURVE, "kurve", 2, 0) { q, t ->
            val (r, n) = einfach(q, R_KURVE, t) ?: return@Zeichen 0
            val von = lowerBound(r.tage, r.tage[n - 1] - 549)
            code(Fruehwarnung.zinskurve(r.paare(n, n - von))?.ampel)
        },
        Zeichen(ZINSKURVE_2J, "kurve", 2, 0) { q, t ->
            val (r, n) = einfach(q, R_KURVE_2J, t) ?: return@Zeichen 0
            val von = lowerBound(r.tage, r.tage[n - 1] - 549)
            code(Fruehwarnung.zinskurve(r.paare(n, n - von))?.ampel)
        },
        Zeichen(SAHM, "arbeit", 3, VERZUG_ARBEIT, revidiert = true) { q, t ->
            val (r, n) = einfach(q, R_ARBEIT, t) ?: return@Zeichen 0
            code(Fruehwarnung.sahm(r.paare(n, 30))?.ampel)
        },
        Zeichen(VIX, "schwankung", 3, 0) { q, t ->
            val (r, n) = einfach(q, R_VIX, t) ?: return@Zeichen 0
            code(Fruehwarnung.vix(r.paare(n, 1))?.ampel)
        },
        Zeichen(VIX_STRUKTUR, "schwankung", 3, 0) { q, t ->
            val (a, na) = einfach(q, R_VIX, t) ?: return@Zeichen 0
            val (b, nb) = einfach(q, R_VIX3M, t) ?: return@Zeichen 0
            val z = Fruehwarnung.vixStruktur(a.paare(na, 8), b.paare(nb, 8)) ?: return@Zeichen 0
            if (t - z.verlauf.last().first / TAG_MS > 7) 0 else code(z.ampel)
        },
        Zeichen(TREND, "trend", 3, 0) { q, t ->
            val (r, n) = einfach(q, R_INDEX, t) ?: return@Zeichen 0
            if (n < 200) 0 else code(Fruehwarnung.trend(trendSchluessel, r.paare(n, 200))?.ampel)
        },
        Zeichen(STRESS, "stress", 3, VERZUG_NFCI, ersatz = true, revidiert = true) { q, t ->
            val (r, n) = einfach(q, R_NFCI, t) ?: return@Zeichen 0
            code(Fruehwarnung.stress(r.paare(n, 1))?.ampel)
        },
        Zeichen(CAPE, "bewertung", 3, VERZUG_CAPE, revidiert = true) { q, t ->
            val (r, n) = einfach(q, R_CAPE, t) ?: return@Zeichen 0
            code(Blasen.cape(Ersatzquellen.Cape(r.werte[n - 1], 2000, 1, 0.0, 0.0))?.ampel)
        },
        Zeichen(KGV, "bewertung", 3, VERZUG_GEWINN, revidiert = true) { q, t -> code(Blasen.kgv(kgvWert(q, t))?.ampel) },
        Zeichen(PRAEMIE, "bewertung", 3, VERZUG_GEWINN, revidiert = true) { q, t ->
            code(Blasen.praemie(kgvWert(q, t), letzterWert(q.reihen[R_ZINS10], t))?.ampel)
        },
        Zeichen(HITZE, "trend", 3, 0) { q, t ->
            val (r, n) = einfach(q, R_SP, t) ?: return@Zeichen 0
            code(Blasen.trend(abstand200(r, n))?.ampel)
        },
        Zeichen(FRUEHINDIKATOR, "konjunktur", 3, VERZUG_CLI, revidiert = true) { q, t ->
            val (r, n) = einfach(q, R_CLI, t) ?: return@Zeichen 0
            if (n < 2) 0 else when (Fruehindikator.phase(r.werte[n - 1], r.werte[n - 2])) {
                Fruehindikator.Phase.ABSCHWUNG -> 3
                Fruehindikator.Phase.ABSCHWAECHUNG -> 2
                else -> 1
            }
        },
        // Eigene Regel (die App hat kein Zeichen fuer den Oelpreis): Rohoelpreis gegen den Preis vor einem Jahr, ab +50 %
        // rot, ab +30 % gelb.
        Zeichen(OEL, "oel", 3, VERZUG_OEL, eigeneRegel = true) { q, t ->
            val (r, n) = einfach(q, R_OEL, t) ?: return@Zeichen 0
            val vor = lowerBound(r.tage, r.tage[n - 1] - 365)
            if (vor >= n - 1 || r.tage[vor] < r.tage[n - 1] - 380 || r.werte[vor] <= 0) 0
            else (r.werte[n - 1] / r.werte[vor] - 1).let { if (it >= 0.5) 3 else if (it >= 0.3) 2 else 1 }
        },
        Zeichen(STIMMUNG_VIX, "stimmung", 3, 0) { q, t ->
            val (r, n) = einfach(q, R_VIX, t) ?: return@Zeichen 0
            code(Blasen.stimmung(vixRang(r, n))?.ampel)
        },
        Zeichen(STIMMUNG_SICHER, "stimmung", 3, 0) { q, t -> code(Blasen.stimmung(sicherRang(q, t))?.ampel) },
        // Regel der App (Fruehwarnung.hochzins) auf den umgerechneten Kreditaufschlag: Wert und Tief der letzten 52 Wochen
        Zeichen(HOCHZINS, "kredit", 3, VERZUG_KREDIT, ersatz = true, revidiert = true) { q, t ->
            val (r, n) = einfach(q, R_KREDIT, t) ?: return@Zeichen 0
            val von = lowerBound(r.tage, r.tage[n - 1] - 364)
            code(Fruehwarnung.hochzins(r.paare(n, n - von).map { it.first to it.second * KREDIT_FAKTOR })?.ampel)
        },
        Zeichen(MARGIN, "hebel", 3, VERZUG_Z1, revidiert = true) { q, t ->
            val w = marginWachstum(q, t) ?: return@Zeichen 0
            code(Blasen.margin(Ersatzquellen.Margin(t, 0.0, w))?.ampel)
        },
        Zeichen(BUFFETT, "bewertung", 3, VERZUG_Z1, ersatz = true, revidiert = true) { q, t ->
            val (wert, bip) = buffettWerte(q, t) ?: return@Zeichen 0
            code(Blasen.buffett(wert, bip)?.ampel)
        },
        // Regel der App (Fruehindikatoren.erstantraege): Vierwochenschnitt gegen sein Tief der letzten 52 Wochen
        Zeichen(ERSTANTRAEGE, "arbeit", 3, VERZUG_ANTRAEGE, revidiert = true) { q, t ->
            val (r, n) = einfach(q, R_ANTRAEGE, t) ?: return@Zeichen 0
            code(Fruehindikatoren.erstantraege(r.paare(n, 60))?.ampel)
        },
    )

    // Erster Index mit tage[i] >= ziel
    fun lowerBound(tage: LongArray, ziel: Long): Int {
        var lo = 0
        var hi = tage.size
        while (lo < hi) {
            val m = (lo + hi) ushr 1
            if (tage[m] < ziel) lo = m + 1 else hi = m
        }
        return lo
    }

    // Zustand eines Zeichens an jedem Handelstag des Index (0 keine Daten, 1 bis 3 wie die Ampel)
    fun zustand(z: Zeichen, q: Quellen, tage: LongArray): IntArray = IntArray(tage.size) { z.regel(q, tage[it]) }

    // ------------------------------------------------------------ Ereignisse

    @Serializable
    data class Ereignis(
        val stufe: Int, val hoch: Int, val tief: Int, val tiefe: Double, val erholt: Int? = null,
        val stand: Map<String, String> = emptyMap(),
    ) {
        // Monate vom Hoch bis zum Tief
        val monate: Int get() = ((tief - hoch) / 30.4375).toInt()
    }

    // Rueckgaenge vom Hoch: je Hoch ein Ereignis, wenn der tiefste Stand bis zum naechsten Hoch mindestens `min` Prozent
    // darunter lag. Ein Ereignis mit Tiefe ab EINBRUCH hat stufe 20, mit Tiefe von RUECKGANG bis darunter stufe 10.
    fun ereignisse(tage: LongArray, kurs: DoubleArray): List<Ereignis> {
        if (tage.isEmpty()) return emptyList()
        val aus = ArrayList<Ereignis>()
        var hoch = 0
        var tief = 0
        fun abschliessen(erholt: Int?) {
            val tiefe = (1 - kurs[tief] / kurs[hoch]) * 100
            if (tiefe >= RUECKGANG) aus += Ereignis(if (tiefe >= EINBRUCH) 20 else 10, tage[hoch].toInt(), tage[tief].toInt(), tiefe, erholt)
        }
        for (i in 1 until tage.size) {
            if (kurs[i] >= kurs[hoch]) {
                abschliessen(tage[i].toInt())
                hoch = i; tief = i
            } else if (kurs[i] < kurs[tief]) tief = i
        }
        abschliessen(null)
        return aus
    }

    // ------------------------------------------------------------ Auswertung je Zeichen

    @Serializable
    data class Rechnung(val rendite: Double, val maxRueck: Double, val wechsel: Int, val ende: Double)

    @Serializable
    data class Bilanz(
        val id: String,
        val ab: Int,
        val auswertbar: Int = 0, val treffer: Int = 0, val spanneVon: Int = 0, val spanneBis: Int = 0,
        val auswertbar10: Int = 0, val treffer10: Int = 0,
        val phasen: Int = 0, val phasenTreffer: Int = 0, val fehlalarme: Int = 0, val offen: Int = 0,
        val vorlaufMittel: Double? = null, val vorlaufMin: Double? = null, val vorlaufMax: Double? = null,
        val zeitAnteil: Double = 0.0,
        val folgen: Rechnung? = null, val halten: Rechnung? = null,
        // Beginn der Kurve als Platz in kurvenTage und die Werte ab dort (Start 100)
        val kurveAb: Int = 0, val kurve: List<Double> = emptyList(),
        // Warnphasen als Paare Beginn, Ende (Tage)
        val phasenListe: List<Int> = emptyList(),
    )

    // Wilson-Spanne (95 %) in Prozent
    fun wilson(treffer: Int, n: Int): Pair<Int, Int> {
        if (n <= 0) return 0 to 0
        val z = 1.96
        val p = treffer.toDouble() / n
        val nenner = 1 + z * z / n
        val mitte = (p + z * z / (2 * n)) / nenner
        val br = z * sqrt(p * (1 - p) / n + z * z / (4.0 * n * n)) / nenner
        return Math.round(max(0.0, mitte - br) * 100).toInt() to Math.round(min(1.0, mitte + br) * 100).toInt()
    }

    // Stand am Stichtag: letzter Handelstag bis dahin; null vor dem ersten
    private fun platzBis(tage: LongArray, tag: Long): Int {
        var lo = 0
        var hi = tage.size
        while (lo < hi) {
            val m = (lo + hi) ushr 1
            if (tage[m] <= tag) lo = m + 1 else hi = m
        }
        return lo - 1
    }

    fun stichtag(hoch: Int, monate: Int): Long = LocalDate.ofEpochDay(hoch.toLong()).minusMonths(monate.toLong()).toEpochDay()

    // w Warnung, r keine Warnung, - keine Daten, fuer jeden Stichtag ein Zeichen
    fun stand(tage: LongArray, zustand: IntArray, warnAb: Int, hoch: Int): String = STICHTAGE.joinToString("") { m ->
        val i = platzBis(tage, stichtag(hoch, m))
        val s = if (i < 0) 0 else zustand[i]
        if (s == 0) "-" else if (s >= warnAb) "w" else "r"
    }

    // Phasen aus Tagen mit gesetztem Merkmal: Paare Beginn, Ende; Pausen bis `ruhe` Tage zaehlen mit. Tage vor 1970 sind
    // negativ, deshalb haelt ein eigenes Merkmal fest, ob eine Phase laeuft (kein Vorzeichen als „gesetzt“).
    fun phasen(tage: LongArray, an: BooleanArray, ruhe: Int = RUHE): List<LongArray> {
        val aus = ArrayList<LongArray>()
        var laeuft = false
        var start = 0L
        var ende = 0L
        for (i in tage.indices) if (an[i]) {
            if (laeuft && tage[i] - ende <= ruhe) ende = tage[i]
            else {
                if (laeuft) aus += longArrayOf(start, ende)
                laeuft = true; start = tage[i]; ende = tage[i]
            }
        }
        if (laeuft) aus += longArrayOf(start, ende)
        return aus
    }

    // Erster Tag mit Daten
    private fun ersterMitDaten(zustand: IntArray): Int = zustand.indexOfFirst { it != 0 }

    // Trefferzahlen, Warnphasen, Vorlauf und Zeitanteil eines Zeichens. ereignisse: alle ab 10 %.
    fun bilanz(id: String, tage: LongArray, zustand: IntArray, warnAb: Int, ereignisse: List<Ereignis>): Bilanz {
        val i0 = ersterMitDaten(zustand)
        if (i0 < 0) return Bilanz(id, 0)
        val ab = tage[i0]
        val letzter = tage.last()
        val warn = BooleanArray(tage.size) { zustand[it] >= warnAb }
        // Warnphasen: Unterbrechungen bis RUHE Tage zaehlen mit
        val phasen = phasen(tage, warn)
        fun warntImFenster(hoch: Int): Long? {
            val von = lowerBound(tage, hoch - FENSTER.toLong())
            val bis = platzBis(tage, hoch.toLong())
            for (i in max(von, i0)..bis) if (i >= 0 && warn[i]) return tage[i]
            return null
        }
        val e20 = ereignisse.filter { it.stufe == 20 && it.hoch - FENSTER >= ab }
        val e10 = ereignisse.filter { it.hoch - FENSTER >= ab }
        val erste = e20.mapNotNull { e -> warntImFenster(e.hoch)?.let { e.hoch - it } }
        val treffer10 = e10.count { warntImFenster(it.hoch) != null }
        // Fuer die Phasen zaehlen alle Einbrueche, auch die ohne volles Fenster an Daten
        val alle20 = ereignisse.filter { it.stufe == 20 }
        val phasenTr = phasen.count { p -> alle20.any { it.hoch >= p[0] && it.hoch <= p[1] + FENSTER } }
        val fehl = phasen.count { p -> alle20.none { it.hoch >= p[0] && it.hoch <= p[1] + FENSTER } && p[1] + FENSTER <= letzter }
        val offen = phasen.size - phasenTr - fehl
        val (sv, sb) = wilson(erste.size, e20.size)
        val ohneDaten = tage.size - i0
        val monate = erste.map { it / 30.4375 }
        return Bilanz(
            id, ab.toInt(), e20.size, erste.size, sv, sb, e10.size, treffer10, phasen.size, phasenTr, fehl, offen,
            monate.takeIf { it.isNotEmpty() }?.average(), monate.minOrNull(), monate.maxOrNull(),
            if (ohneDaten > 0) (i0 until tage.size).count { warn[it] }.toDouble() / ohneDaten else 0.0,
            phasenListe = phasen.flatMap { listOf(it[0].toInt(), it[1].toInt()) },
        )
    }

    // ------------------------------------------------------------ Gegenrechnung Warnung befolgen gegen Halten

    class Lauf(val ende: Double, val maxRueck: Double, val wechsel: Int, val kurve: DoubleArray)

    // Taegliche Rendite: Index (mit Dividendenrendite in Prozent im Jahr, falls vorhanden); Bargeld zum Zins in Prozent im
    // Jahr. Das Zeichen am Schluss von Tag i-1 bestimmt die Lage fuer Tag i (ein Tag Verzug). Kosten je Wechsel.
    fun lauf(
        tage: LongArray, kurs: DoubleArray, zins: DoubleArray, dividende: DoubleArray?, zustand: IntArray?, warnAb: Int, ab: Int,
        kosten: Double = KOSTEN_WECHSEL,
    ): Lauf {
        var wert = 100.0
        var spitze = 100.0
        var tiefster = 0.0
        var wechsel = 0
        var imIndex = true
        val kurve = DoubleArray(tage.size - ab)
        kurve[0] = wert
        for (i in ab + 1 until tage.size) {
            val warnt = zustand != null && zustand[i - 1] >= warnAb
            if (warnt == imIndex) {
                imIndex = !warnt
                wert *= 1 - kosten
                wechsel++
            }
            val dt = (tage[i] - tage[i - 1]) / 365.0
            val r = if (imIndex) kurs[i] / kurs[i - 1] - 1 + (dividende?.get(i - 1) ?: 0.0) / 100 * dt else zins[i - 1] / 100 * dt
            wert *= 1 + r
            kurve[i - ab] = wert
            if (wert > spitze) spitze = wert
            tiefster = max(tiefster, 1 - wert / spitze)
        }
        return Lauf(wert, tiefster, wechsel, kurve)
    }

    fun rechnung(l: Lauf, tage: LongArray, ab: Int): Rechnung {
        val jahre = (tage.last() - tage[ab]) / 365.25
        val rendite = if (jahre > 0 && l.ende > 0) (l.ende / 100).pow(1 / jahre) - 1 else 0.0
        return Rechnung(runden(rendite * 100, 2), runden(-l.maxRueck * 100, 1), l.wechsel, runden(l.ende, 1))
    }

    fun runden(x: Double, stellen: Int): Double {
        val f = 10.0.pow(stellen)
        return Math.round(x * f) / f
    }

    // ------------------------------------------------------------ Gesamte Messung

    // Eingabe je Index: Tage und Schlusskurse, Zins (Prozent im Jahr, je Handelstag des Index), Dividendenrendite
    // (Prozent im Jahr, je Handelstag, oder null), Schluessel der Trend-Zeile und des Fruehindikators.
    class IndexEingabe(
        val id: String, val tage: LongArray, val kurs: DoubleArray, val zins: DoubleArray, val dividende: DoubleArray?,
        val reihen: Map<String, Reihe>, val trendSchluessel: String,
    )

    @Serializable
    data class IndexErgebnis(
        val id: String, val von: Int, val bis: Int, val ereignisse: List<Ereignis>, val zeichen: List<Bilanz>,
        val kurvenTage: List<Int>, val halten: List<Double>,
    )

    @Serializable
    data class ZeichenInfo(
        val id: String, val gruppe: String, val warnAb: Int, val verzug: Int,
        val ersatz: Boolean = false, val revidiert: Boolean = false, val eigeneRegel: Boolean = false,
    )

    @Serializable
    data class Fehlt(val id: String, val grund: String)

    // Eine belegte Zeile zur Wirkungskette eines Einbruchs, je Sprache; hoch: Monat des Hochs (JJJJ-MM)
    @Serializable
    data class Kette(val hoch: String, val de: String, val en: String, val quelle: String)

    @Serializable
    data class Datei(
        val stand: String, val datenBis: String, val kosten: Double, val zeichen: List<ZeichenInfo>, val fehlend: List<Fehlt>,
        val indizes: List<IndexErgebnis>, val ketten: List<Kette> = emptyList(), val version: Int = 1,
    )

    // Alle Zustandsreihen eines Index: die Zeichen der App und die Zusammenschau. Rueckgabe: Zeichen -> (warnAb, Zustand).
    fun zustaende(e: IndexEingabe, liste: List<Zeichen>): Map<String, Pair<Int, IntArray>> {
        val q = Quellen(e.reihen)
        val aus = LinkedHashMap<String, Pair<Int, IntArray>>()
        for (z in liste) aus[z.id] = z.warnAb to zustand(z, q, e.tage)
        for (k in ZUSAMMEN_AB) {
            val zahlen = IntArray(e.tage.size) { i ->
                val mit = ZAEHLT.mapNotNull { id -> aus[id]?.second?.get(i) }.filter { it != 0 }
                if (mit.size < MIN_VERFUEGBAR) 0 else if (ZAEHLT.count { id -> (aus[id]?.let { (w, s) -> s[i] >= w }) == true } >= k) 3 else 1
            }
            aus["${ZUSAMMEN}_$k"] = 3 to zahlen
        }
        return aus
    }

    fun indexErgebnis(e: IndexEingabe, liste: List<Zeichen> = zeichenListe(e.trendSchluessel)): IndexErgebnis {
        val z = zustaende(e, liste)
        val ev = ereignisse(e.tage, e.kurs)
        // Zinsreihe beginnt spaeter als die Kurse: Rechnung erst ab dem ersten Tag mit Zins
        val zinsAb = e.zins.indexOfFirst { it.isFinite() && !it.isNaN() && it > -100 }.let { if (it < 0) 0 else it }
        val schritt = 63
        val kurvenIdx = (0 until e.tage.size step schritt).toMutableList().also { if (it.last() != e.tage.size - 1) it += e.tage.size - 1 }
        val halten = lauf(e.tage, e.kurs, e.zins, e.dividende, null, 3, 0).kurve
        val bilanzen = z.map { (id, p) ->
            val (warnAb, zu) = p
            val b = bilanz(id, e.tage, zu, warnAb, ev)
            val i0 = ersterMitDaten(zu)
            if (i0 < 0) b else {
                val ab = max(i0, zinsAb)
                val l = lauf(e.tage, e.kurs, e.zins, e.dividende, zu, warnAb, ab)
                val h = lauf(e.tage, e.kurs, e.zins, e.dividende, null, warnAb, ab)
                val platz = kurvenIdx.indexOfFirst { it >= ab }
                b.copy(
                    folgen = rechnung(l, e.tage, ab), halten = rechnung(h, e.tage, ab), kurveAb = platz.coerceAtLeast(0),
                    kurve = kurvenIdx.filter { it >= ab }.map { runden(l.kurve[it - ab], 1) },
                )
            }
        }
        val mitStand = ev.map { v -> v.copy(stand = z.mapValues { (_, p) -> stand(e.tage, p.second, p.first, v.hoch) }) }
        return IndexErgebnis(e.id, e.tage.first().toInt(), e.tage.last().toInt(), mitStand, bilanzen, kurvenIdx.map { e.tage[it].toInt() }, kurvenIdx.map { runden(halten[it], 1) })
    }

    // ------------------------------------------------------------ Datei lesen

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun lesen(text: String): Datei? = runCatching { json.decodeFromString(Datei.serializer(), text) }.getOrNull()?.takeIf { it.indizes.isNotEmpty() }
    fun schreiben(d: Datei): String = json.encodeToString(Datei.serializer(), d)

    // Einzige Zugriffsstelle auf die mitgelieferte Datei; spaeter ueber das signierte Datenbuendel erneuerbar.
    val datei: Datei? by lazy {
        runCatching { lesen(de.troetenrepublik.app.market.Daten.text("indikatoren/warnzeichen_rueckblick.json")) }.getOrNull()
    }

    // ------------------------------------------------------------ Anzeigehilfen

    // Index mit der Bilanz zu einem Zeichen (in einer Datei ohne Index null)
    fun bilanz(d: Datei?, index: String, zeichen: String): Bilanz? =
        d?.indizes?.firstOrNull { it.id == index }?.zeichen?.firstOrNull { it.id == zeichen }?.takeIf { it.auswertbar > 0 }

    // Wie viele der Einbrueche mit Daten das Zeichen vorher zeigte, und wie viele Warnphasen ohne Einbruch blieben
    data class Kurz(val treffer: Int, val von: Int, val fehlalarme: Int)

    fun kurz(b: Bilanz): Kurz = Kurz(b.treffer, b.auswertbar, b.fehlalarme)

    // Warnt der Zustand (Ampel als Ordnungszahl 0 gruen, 1 gelb, 2 rot) nach der Schwelle des Zeichens?
    fun warnt(zeichen: ZeichenInfo, ampelOrdinal: Int): Boolean = ampelOrdinal + 1 >= zeichen.warnAb
}
