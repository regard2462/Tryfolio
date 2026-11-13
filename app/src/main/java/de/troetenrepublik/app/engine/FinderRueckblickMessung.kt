package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Aktienfinder.Bereich
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import kotlin.math.pow
import kotlin.math.sqrt

// Messung des Rueckblick-Tests (5.92): Rendite der bestbewerteten Aktien gegen MSCI World, S&P 500 und den Schnitt
// des Universums, Rang-Korrelation (Spearman-IC) zwischen Punktzahl und spaeterer Rendite, Spannweite der Fuenftel,
// monatliches Umschichten mit Kosten, Jahr fuer Jahr, und das fortlaufende Lernen der Gewichte (Walk-forward).
// Die Zusammenfassung (nur Kennzahlen) liegt als assets/indikatoren/finder_rueckblick.json bei.
object FinderRueckblickMessung {

    // Horizonte in Monaten; 1 Monat fuer das monatliche Umschichten.
    val HORIZONTE = listOf(3, 6, 12)

    // Kaufen und halten ohne Umschichten, Monate.
    val HALTEN = listOf(36, 60)
    const val MONAT = 1
    const val TOP = 20
    const val TOP_KLEIN = 10

    // Mindestzahl an Werten fuer eine Rang-Korrelation; ein Bereich braucht mehr, weil oft Daten fehlen.
    const val MIN_IC = 10
    const val MIN_BEREICH = 30

    // Gelernt wird mit sechs Monaten, geprueft ab 2019 (Jahre davor nur zum Lernen).
    const val LERN_MONATE = 6
    const val PRUEF_AB = 2019

    // Ein Bereich braucht so viele Stichtage mit Rang-Korrelation, sonst behaelt er sein Standardgewicht.
    const val MIN_LERN = 12

    // Wie stark die Rang-Korrelation das Gewicht verschiebt: IC 0,05 hebt es um die Haelfte, -0,1 setzt es auf null.
    const val LERN_STAERKE = 10.0

    // Kosten beim Umschichten: Spread fuer Aktien wie in der App (Trading.Spread), je ausgetauschtem Anteil einmal ganz.
    val SPREAD_BPS = Trading.Spread().stockBps

    // Ein Wert am Stichtag: Punkte je Bereich aus der Engine und die spaetere Rendite je Horizont (null = offen).
    data class Wert(
        val symbol: String, val bereiche: Map<Bereich, Double?>, val rendite: Map<Int, Double?>,
        // Punkte je Kennzahl (Engine), fuer die Einzelmessung
        val kennzahlen: Map<Aktienfinder.Kennzahl, Double> = emptyMap(),
    )

    // Ein Stichtag: bewertete Werte, Renditen der Vergleiche je Horizont und die Zahl der Mitglieder laut Liste
    // (soll), von denen werte.size Kurse hatten.
    data class Stichtag(val tag: Long, val werte: List<Wert>, val msci: Map<Int, Double?>, val spx: Map<Int, Double?>, val soll: Int) {
        val jahr: Int get() = LocalDate.ofEpochDay(tag).year
    }

    // ------------------------------------------------------------ Statistik

    fun mittel(x: List<Double>): Double? = if (x.isEmpty()) null else x.sum() / x.size

    // Durchschnittliche Raenge (gleiche Werte teilen sich den Rang), ab 1.
    fun raenge(x: List<Double>): DoubleArray {
        val idx = x.indices.sortedBy { x[it] }
        val r = DoubleArray(x.size)
        var i = 0
        while (i < idx.size) {
            var j = i
            while (j + 1 < idx.size && x[idx[j + 1]] == x[idx[i]]) j++
            val rang = (i + j) / 2.0 + 1.0
            for (k in i..j) r[idx[k]] = rang
            i = j + 1
        }
        return r
    }

    // Rang-Korrelation nach Spearman (Pearson der Raenge); null unter MIN_IC Paaren oder ohne Streuung.
    fun spearman(a: List<Double>, b: List<Double>, min: Int = MIN_IC): Double? {
        if (a.size != b.size || a.size < min) return null
        val ra = raenge(a)
        val rb = raenge(b)
        val ma = ra.average()
        val mb = rb.average()
        var sab = 0.0
        var saa = 0.0
        var sbb = 0.0
        for (i in ra.indices) {
            sab += (ra[i] - ma) * (rb[i] - mb)
            saa += (ra[i] - ma).pow(2)
            sbb += (rb[i] - mb).pow(2)
        }
        if (saa <= 0 || sbb <= 0) return null
        return sab / sqrt(saa * sbb)
    }

    // Mittel und Standardfehler nach Newey-West: ueberlappende Zeitraeume (zwoelf Monate Rendite, jeden Monat neu
    // gemessen) haengen voneinander ab, lag Monate werden mit fallendem Gewicht beruecksichtigt.
    fun mittelUndFehler(x: List<Double>, lag: Int): Pair<Double, Double>? {
        val n = x.size
        if (n < 2) return null
        val m = x.average()
        fun gamma(k: Int): Double = (k until n).sumOf { (x[it] - m) * (x[it - k] - m) } / n
        var v = gamma(0)
        for (k in 1..minOf(lag, n - 1)) v += 2.0 * (1.0 - k / (lag + 1.0)) * gamma(k)
        return m to sqrt(maxOf(v, 0.0) / n)
    }

    // ------------------------------------------------------------ Kennzahlen je Stichtag

    // Werte nach Gesamtpunktzahl (Engine, Aktienfinder.gesamt) absteigend, bei Gleichstand nach Symbol.
    fun sortiert(s: Stichtag, gewichte: Map<Bereich, Double>): List<Wert> =
        s.werte.map { it to Aktienfinder.gesamt(it.bereiche, gewichte) }
            .sortedWith(compareByDescending<Pair<Wert, Double>> { it.second }.thenBy { it.first.symbol }).map { it.first }

    // Gleichgewichtete Rendite der besten n; null, solange der Horizont offen ist.
    fun topRendite(s: Stichtag, gewichte: Map<Bereich, Double>, n: Int, h: Int): Double? =
        mittel(sortiert(s, gewichte).take(n).mapNotNull { it.rendite[h] })

    // Gleichgewichteter Schnitt aller bewerteten Werte.
    fun universum(s: Stichtag, h: Int): Double? = mittel(s.werte.mapNotNull { it.rendite[h] })

    // Rang-Korrelation zwischen Gesamtpunktzahl und Rendite.
    fun ic(s: Stichtag, gewichte: Map<Bereich, Double>, h: Int): Double? {
        val l = s.werte.mapNotNull { w -> w.rendite[h]?.let { Aktienfinder.gesamt(w.bereiche, gewichte) to it } }
        return spearman(l.map { it.first }, l.map { it.second })
    }

    // Rang-Korrelation eines Bereichs, nur Werte mit Daten in diesem Bereich (mindestens MIN_BEREICH).
    fun bereichIc(s: Stichtag, b: Bereich, h: Int): Double? {
        val l = s.werte.mapNotNull { w -> val p = w.bereiche[b] ?: return@mapNotNull null; w.rendite[h]?.let { p to it } }
        return spearman(l.map { it.first }, l.map { it.second }, MIN_BEREICH)
    }

    // Rang-Korrelation einer einzelnen Kennzahl, nur Werte mit Daten (mindestens MIN_BEREICH).
    fun kennzahlIc(s: Stichtag, k: Aktienfinder.Kennzahl, h: Int): Double? {
        val l = s.werte.mapNotNull { w -> val p = w.kennzahlen[k] ?: return@mapNotNull null; w.rendite[h]?.let { p to it } }
        return spearman(l.map { it.first }, l.map { it.second }, MIN_BEREICH)
    }

    // Bestes Fuenftel minus schlechtestes Fuenftel.
    fun spanne(s: Stichtag, gewichte: Map<Bereich, Double>, h: Int): Double? {
        val l = sortiert(s, gewichte).filter { it.rendite[h] != null }
        val f = l.size / 5
        if (f < 2) return null
        val oben = mittel(l.take(f).map { it.rendite[h]!! }) ?: return null
        val unten = mittel(l.takeLast(f).map { it.rendite[h]!! }) ?: return null
        return oben - unten
    }

    // ------------------------------------------------------------ Zusammenfassung

    @Serializable
    data class Horizont(
        val monate: Int, val stichtage: Int,
        // Mittlere Rendite je Zeitraum (Anteil, 0,05 = 5 %)
        val top10: Double, val top20: Double, val msci: Double, val spx: Double, val universum: Double,
        // Mehrrendite der Top 20 gegen den MSCI World mit Standardfehler
        val mehr: Double, val mehrSe: Double,
        // Anteil der Stichtage, an denen die Top 20 den MSCI World schlugen
        val treffer: Double,
        val ic: Double, val icSe: Double,
        val spanne: Double, val spanneSe: Double,
    )

    // Kaufen und halten vom ersten Stichtag bis heute (5.93): Renditen sind Gesamtrenditen als Anteil, anzahl ist die
    // Zahl der gekauften Werte mit Rendite.
    @Serializable
    data class HaltenHeute(
        val von: String, val bis: String, val jahre: Double,
        val top20: Double, val msci: Double, val spx: Double, val universum: Double,
        val anzahl: Int,
    )

    @Serializable
    data class BereichWert(val bereich: String, val monate: Int, val ic: Double, val se: Double, val stichtage: Int, val ab: String)

    @Serializable
    data class JahrWert(val jahr: Int, val monate: Int, val top20: Double, val top20Netto: Double, val msci: Double, val spx: Double, val universum: Double)

    @Serializable
    data class Strategie(
        val monate: Int,
        // Rendite pro Jahr (Anteil) bei monatlichem Umschichten
        val top20: Double, val top20Netto: Double, val msci: Double, val spx: Double, val universum: Double,
        // Mittlerer ausgetauschter Anteil je Monat und Kosten pro Jahr
        val umschlag: Double, val kostenJahr: Double,
        // Groesster Rueckstand gegen den MSCI World (Anteil) mit Beginn und Tiefpunkt
        val rueckstand: Double, val rueckstandVon: String, val rueckstandBis: String,
        // Groesster Verlust der Top 20 selbst
        val tiefstand: Double,
    )

    @Serializable
    data class Lernen(
        val monate: Int, val ab: Int, val stichtage: Int, val jahre: Int,
        val icStandard: Double, val icGelernt: Double, val icDiff: Double, val icDiffSe: Double,
        val mehrStandard: Double, val mehrGelernt: Double, val mehrDiff: Double, val mehrDiffSe: Double,
        // Jahre, in denen die gelernten Gewichte bei IC und Mehrrendite vorne lagen
        val jahreBesser: Int,
        // Gelernte Gewichte aus allen Stichtagen bis zum Schluss und je Pruefjahr
        val gewichte: Map<String, Double>,
        val jeJahr: Map<Int, Map<String, Double>>,
        val klarBesser: Boolean,
    )

    // Abdeckung eines Stichtags: Mitglieder laut Liste und davon bewertete Werte (mit Kurs am Stichtag).
    @Serializable
    data class Abdeckung(val tag: String, val mitglieder: Int, val gemessen: Int) {
        val anteil: Double get() = if (mitglieder > 0) (gemessen.toDouble() / mitglieder).coerceAtMost(1.0) else 0.0
    }

    @Serializable
    data class Datei(
        val stand: String = "",
        val von: String = "",
        val bis: String = "",
        val stichtage: Int = 0,
        // Mittlere Zahl bewerteter Werte je Stichtag und mittlerer Anteil der Mitglieder ohne Kurse bei Yahoo
        val werte: Int = 0,
        val fehlend: Double = 0.0,
        // Gepruefte Bereiche und ihre Gewichte im Test
        val bereiche: List<String> = emptyList(),
        val gewichte: Map<String, Double> = emptyMap(),
        // Anteil der Stichtage, an denen ein Bereich bei mindestens der Haelfte der Werte Daten hatte
        val datenlage: Map<String, Double> = emptyMap(),
        // Mit SEC-Abschluessen aus companyfacts.zip
        val mitFakten: Boolean = false,
        val horizonte: List<Horizont> = emptyList(),
        val pruefzeit: List<Horizont> = emptyList(),
        val ics: List<BereichWert> = emptyList(),
        // Einzelmessung der Kennzahlen des Kurstrends (JAHR, LINIE200, LINIE50, RSI)
        val kennzahlen: List<BereichWert> = emptyList(),
        val jahre: List<JahrWert> = emptyList(),
        val strategie: Strategie? = null,
        val lernen: Lernen? = null,
        // Kaufen und halten (5.93): gleiche Kennzahlen wie die Horizonte
        val halten: List<Horizont> = emptyList(),
        val haltenHeute: HaltenHeute? = null,
        // Abdeckung je Stichtag und Stand der Mitgliederliste (leer bei Dateien von vor der Liste)
        val abdeckung: List<Abdeckung> = emptyList(),
        val mitgliederStand: String = "",
        // Zufallsdepots aus denselben Werten und Monaten wie das monatliche Umschichten (0.78), null bei aelteren Dateien
        val zufall: Zufallsdepots.Verteilung? = null,
        // Universum der Messung: leer fuer den S&P 500, sonst etwa ndx100
        val universum: String = "",
    ) {
        fun horizont(monate: Int): Horizont? = horizonte.firstOrNull { it.monate == monate }

        // Mittlerer Anteil der damaligen Mitglieder, die gemessen wurden; null ohne Abdeckung je Stichtag.
        fun abdeckungSchnitt(): Double? = abdeckung.filter { it.mitglieder > 0 }.takeIf { it.isNotEmpty() }?.map { it.anteil }?.average()

        // Dasselbe je Jahr, nach Jahr aufsteigend.
        fun abdeckungJeJahr(): List<Pair<Int, Double>> =
            abdeckung.filter { it.mitglieder > 0 }.groupBy { it.tag.take(4).toInt() }.toSortedMap().map { (j, l) -> j to l.map { it.anteil }.average() }

        // Entstand die Messung mit diesen Gewichten? Die Engine rechnet Gewichte anteilig, deshalb zaehlt das Verhaeltnis.
        // Eine Datei ohne Gewichte (aelterer Stand) gilt als mit frueherer Gewichtung gemessen.
        fun passtZuGewichten(aktuell: Map<String, Double>): Boolean = gewichteGleich(gewichte, aktuell)
    }

    fun gewichteGleich(a: Map<String, Double>, b: Map<String, Double>): Boolean {
        val x = a.filterValues { it > 0 }
        val y = b.filterValues { it > 0 }
        if (x.isEmpty() || x.keys != y.keys) return false
        val sx = x.values.sum()
        val sy = y.values.sum()
        return x.all { (k, v) -> kotlin.math.abs(v / sx - y.getValue(k) / sy) < 1e-9 }
    }

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    fun lesen(text: String): Datei? = runCatching { json.decodeFromString(Datei.serializer(), text) }.getOrNull()

    fun schreiben(d: Datei): String = json.encodeToString(Datei.serializer(), d)

    // Kennzahlen je Horizont ueber die gegebenen Stichtage.
    fun horizont(st: List<Stichtag>, gewichte: Map<Bereich, Double>, h: Int): Horizont? {
        data class Z(val t10: Double, val t20: Double, val msci: Double, val spx: Double, val uni: Double, val ic: Double?, val sp: Double?)
        val z = st.mapNotNull { s ->
            val msci = s.msci[h] ?: return@mapNotNull null
            val spx = s.spx[h] ?: return@mapNotNull null
            Z(topRendite(s, gewichte, TOP_KLEIN, h) ?: return@mapNotNull null, topRendite(s, gewichte, TOP, h) ?: return@mapNotNull null,
                msci, spx, universum(s, h) ?: return@mapNotNull null, ic(s, gewichte, h), spanne(s, gewichte, h))
        }
        if (z.size < 2) return null
        val lag = h
        val mehr = mittelUndFehler(z.map { it.t20 - it.msci }, lag)!!
        val ic = mittelUndFehler(z.mapNotNull { it.ic }, lag) ?: (0.0 to 0.0)
        val sp = mittelUndFehler(z.mapNotNull { it.sp }, lag) ?: (0.0 to 0.0)
        return Horizont(
            h, z.size, z.map { it.t10 }.average(), z.map { it.t20 }.average(), z.map { it.msci }.average(), z.map { it.spx }.average(),
            z.map { it.uni }.average(), mehr.first, mehr.second, z.count { it.t20 > it.msci }.toDouble() / z.size,
            ic.first, ic.second, sp.first, sp.second,
        )
    }

    // Rang-Korrelation eines Bereichs ueber die Stichtage; ab: erster Stichtag mit Werten.
    fun bereichWert(st: List<Stichtag>, b: Bereich, h: Int): BereichWert? {
        val l = st.mapNotNull { s -> bereichIc(s, b, h)?.let { s.tag to it } }
        val (m, se) = mittelUndFehler(l.map { it.second }, h) ?: return null
        return BereichWert(b.name, h, m, se, l.size, FinderRueckblick.datum(l.first().first))
    }

    // Wie bereichWert, fuer eine einzelne Kennzahl (bereich traegt ihren Namen).
    fun kennzahlWert(st: List<Stichtag>, k: Aktienfinder.Kennzahl, h: Int): BereichWert? {
        val l = st.mapNotNull { s -> kennzahlIc(s, k, h)?.let { s.tag to it } }
        val (m, se) = mittelUndFehler(l.map { it.second }, h) ?: return null
        return BereichWert(k.name, h, m, se, l.size, FinderRueckblick.datum(l.first().first))
    }

    // Monatliches Umschichten in die Top 20: Renditen je Monat brutto, netto (Spread auf den ausgetauschten Anteil) und
    // die Vergleiche. Ergebnis je Stichtag mit der Rendite des Folgemonats.
    data class Monat(val tag: Long, val top: Double, val netto: Double, val msci: Double, val spx: Double, val uni: Double, val umschlag: Double)

    fun monate(st: List<Stichtag>, gewichte: Map<Bereich, Double>): List<Monat> {
        val aus = ArrayList<Monat>()
        var vorher: Set<String>? = null
        for (s in st.sortedBy { it.tag }) {
            val msci = s.msci[MONAT] ?: continue
            val spx = s.spx[MONAT] ?: continue
            val top = sortiert(s, gewichte).take(TOP)
            val r = mittel(top.mapNotNull { it.rendite[MONAT] }) ?: continue
            val uni = universum(s, MONAT) ?: continue
            val neu = top.map { it.symbol }.toSet()
            // Erster Monat: alles kaufen (halber Spread), danach nur Ausgetauschtes (ganzer Spread: verkaufen und kaufen)
            val umschlag = vorher?.let { v -> neu.count { it !in v }.toDouble() / neu.size } ?: 1.0
            val kosten = if (vorher == null) SPREAD_BPS / 20_000.0 else umschlag * SPREAD_BPS / 10_000.0
            aus += Monat(s.tag, r, (1 + r) * (1 - kosten) - 1, msci, spx, uni, umschlag)
            vorher = neu
        }
        return aus
    }

    // Zufallsdepots: an denselben Stichtagen wie monate() je TOP zufaellige Werte des Stichtags, vor Kosten.
    fun zufall(st: List<Stichtag>, gewichte: Map<Bereich, Double>, anzahl: Int = Zufallsdepots.ANZAHL): Zufallsdepots.Verteilung? {
        val m = monate(st, gewichte)
        val jeTag = st.associateBy { it.tag }
        val renditen = m.map { x -> jeTag.getValue(x.tag).werte.mapNotNull { it.rendite[MONAT] } }
        return Zufallsdepots.verteilung(renditen, m.map { it.top }, TOP, anzahl)
    }

    private fun jahresrendite(r: List<Double>): Double =
        if (r.isEmpty()) 0.0 else r.fold(1.0) { a, x -> a * (1 + x) }.pow(12.0 / r.size) - 1.0

    fun strategie(m: List<Monat>): Strategie? {
        if (m.size < 12) return null
        // Rueckstand: tiefster Stand des Verhaeltnisses Top 20 (netto) zu MSCI World unter seinem bisherigen Hoch
        var rel = 1.0
        var hoch = 1.0
        var hochTag = m.first().tag
        var tiefst = 0.0
        var von = m.first().tag
        var bis = m.first().tag
        var wert = 1.0
        var wertHoch = 1.0
        var verlust = 0.0
        for (x in m) {
            rel *= (1 + x.netto) / (1 + x.msci)
            val ende = LocalDate.ofEpochDay(x.tag).plusMonths(1).toEpochDay()
            if (rel > hoch) { hoch = rel; hochTag = ende }
            val dd = 1 - rel / hoch
            if (dd > tiefst) { tiefst = dd; von = hochTag; bis = ende }
            wert *= 1 + x.netto
            wertHoch = maxOf(wertHoch, wert)
            verlust = maxOf(verlust, 1 - wert / wertHoch)
        }
        val umschlag = m.drop(1).map { it.umschlag }.average()
        return Strategie(
            MONAT, jahresrendite(m.map { it.top }), jahresrendite(m.map { it.netto }), jahresrendite(m.map { it.msci }),
            jahresrendite(m.map { it.spx }), jahresrendite(m.map { it.uni }), umschlag, umschlag * 12 * SPREAD_BPS / 10_000.0,
            tiefst, FinderRueckblick.datum(von), FinderRueckblick.datum(bis), verlust,
        )
    }

    // Jahr fuer Jahr: Monatsrenditen verkettet, ein Monat zaehlt zum Jahr, in dem er endet.
    fun jahre(m: List<Monat>): List<JahrWert> =
        m.groupBy { LocalDate.ofEpochDay(it.tag).plusMonths(1).year }.toSortedMap().map { (j, l) ->
            fun kette(f: (Monat) -> Double) = l.fold(1.0) { a, x -> a * (1 + f(x)) } - 1
            JahrWert(j, l.size, kette { it.top }, kette { it.netto }, kette { it.msci }, kette { it.spx }, kette { it.uni })
        }

    // ------------------------------------------------------------ Walk-forward

    // Gewichte aus der Vergangenheit lernen: je geprueftem Bereich das Standardgewicht mal (1 + LERN_STAERKE * mittlere
    // Rang-Korrelation), begrenzt auf 0 bis doppelt. Bereiche mit zu wenigen Stichtagen behalten ihr Gewicht. Die
    // Summe der geprueften Gewichte bleibt gleich, die uebrigen Bereiche bleiben aussen vor.
    fun gelernt(training: List<Stichtag>, standard: Map<Bereich, Double>, h: Int = LERN_MONATE): Map<Bereich, Double> {
        val roh = FinderRueckblick.IM_TEST.associateWith { b ->
            val ics = training.mapNotNull { bereichIc(it, b, h) }
            val g = standard[b] ?: 0.0
            if (ics.size < MIN_LERN) g else g * (1.0 + LERN_STAERKE * ics.average()).coerceIn(0.0, 2.0)
        }
        val soll = FinderRueckblick.IM_TEST.sumOf { standard[it] ?: 0.0 }
        val ist = roh.values.sum()
        if (!(ist > 0)) return standard
        return Bereich.entries.associateWith { b -> roh[b]?.let { it * soll / ist } ?: 0.0 }
    }

    // Lernen bis vor Jahr t (nur Stichtage, deren Horizont vor Jahresbeginn endete), pruefen in Jahr t, fuer alle
    // Jahre ab PRUEF_AB. Klar besser heisst: bei IC und Mehrrendite der Top 20 mehr als zwei Standardfehler vorne und
    // in mindestens zwei Dritteln der Jahre vorne.
    fun walkForward(st: List<Stichtag>, standard: Map<Bereich, Double>, h: Int = LERN_MONATE, ab: Int = PRUEF_AB): Lernen? {
        val sortiert = st.sortedBy { it.tag }
        val jahre = sortiert.map { it.jahr }.filter { it >= ab }.distinct()
        data class P(val jahr: Int, val icS: Double, val icG: Double, val mS: Double, val mG: Double)
        val punkte = ArrayList<P>()
        val jeJahr = LinkedHashMap<Int, Map<String, Double>>()
        for (j in jahre) {
            val beginn = LocalDate.of(j, 1, 1).toEpochDay()
            val training = sortiert.filter { LocalDate.ofEpochDay(it.tag).plusMonths(h.toLong()).toEpochDay() < beginn }
            val g = gelernt(training, standard, h)
            jeJahr[j] = g.filterValues { it > 0 }.mapKeys { it.key.name }
            for (s in sortiert.filter { it.jahr == j }) {
                val msci = s.msci[h] ?: continue
                val icS = ic(s, standard, h) ?: continue
                val icG = ic(s, g, h) ?: continue
                val mS = (topRendite(s, standard, TOP, h) ?: continue) - msci
                val mG = (topRendite(s, g, TOP, h) ?: continue) - msci
                punkte += P(j, icS, icG, mS, mG)
            }
        }
        if (punkte.size < 2) return null
        val icD = mittelUndFehler(punkte.map { it.icG - it.icS }, h)!!
        val mD = mittelUndFehler(punkte.map { it.mG - it.mS }, h)!!
        val besser = punkte.groupBy { it.jahr }.count { (_, l) -> l.sumOf { it.icG - it.icS } > 0 && l.sumOf { it.mG - it.mS } > 0 }
        val anzahlJahre = punkte.map { it.jahr }.distinct().size
        val klar = icD.first > 2 * icD.second && mD.first > 2 * mD.second && besser * 3 >= anzahlJahre * 2
        val alle = gelernt(sortiert.filter { it.msci[h] != null }, standard, h)
        return Lernen(
            h, ab, punkte.size, anzahlJahre,
            punkte.map { it.icS }.average(), punkte.map { it.icG }.average(), icD.first, icD.second,
            punkte.map { it.mS }.average(), punkte.map { it.mG }.average(), mD.first, mD.second,
            besser, alle.filterValues { it > 0 }.mapKeys { it.key.name }, jeJahr, klar,
        )
    }

    // Anteil der Stichtage, an denen ein Bereich bei mindestens der Haelfte der Werte Daten hatte.
    fun datenlage(st: List<Stichtag>, b: Bereich): Double =
        if (st.isEmpty()) 0.0 else st.count { s -> s.werte.isNotEmpty() && s.werte.count { it.bereiche[b] != null } * 2 >= s.werte.size }.toDouble() / st.size

    // Kaufen und halten (5.93): Top 20 am ersten Stichtag gleich gewichtet gekauft und bis heute gehalten, daneben
    // MSCI World, S&P 500 und der Schnitt des Universums im selben Zeitraum. Null, wenn einer der Werte fehlt.
    fun haltenHeute(st: List<Stichtag>, gewichte: Map<Bereich, Double>, heute: Long): HaltenHeute? {
        val s = st.minByOrNull { it.tag } ?: return null
        val msci = s.msci[FinderRueckblick.BIS_HEUTE] ?: return null
        val spx = s.spx[FinderRueckblick.BIS_HEUTE] ?: return null
        val top = sortiert(s, gewichte).take(TOP).mapNotNull { it.rendite[FinderRueckblick.BIS_HEUTE] }
        if (top.isEmpty()) return null
        val uni = universum(s, FinderRueckblick.BIS_HEUTE) ?: return null
        return HaltenHeute(
            FinderRueckblick.datum(s.tag), FinderRueckblick.datum(heute), (heute - s.tag) / 365.25,
            mittel(top)!!, msci, spx, uni, top.size,
        )
    }

    // Abdeckung je Stichtag: soll ist die Zahl der Mitglieder laut Liste, gemessen die der bewerteten Werte.
    fun abdeckung(st: List<Stichtag>): List<Abdeckung> =
        st.sortedBy { it.tag }.map { Abdeckung(FinderRueckblick.datum(it.tag), it.soll, minOf(it.werte.size, it.soll)) }

    // Alles zusammen. stand: Datum des Laufs. heute: letzter Tag mit Kursen, fuer kaufen und halten bis heute.
    // mitgliederStand: Stand der Mitgliederliste, aus der soll je Stichtag stammt.
    fun zusammenfassen(
        st: List<Stichtag>, gewichte: Map<Bereich, Double>, stand: String, mitFakten: Boolean, heute: Long? = null, mitgliederStand: String = "",
        universum: String = "",
    ): Datei {
        val s = st.sortedBy { it.tag }
        if (s.isEmpty()) return Datei(stand = stand)
        val m = monate(s, gewichte)
        val pruef = s.filter { it.jahr >= PRUEF_AB }
        val abdeckung = abdeckung(s)
        return Datei(
            stand = stand,
            von = FinderRueckblick.datum(s.first().tag),
            bis = FinderRueckblick.datum(s.last().tag),
            stichtage = s.size,
            werte = Math.round(s.map { it.werte.size }.average()).toInt(),
            fehlend = abdeckung.filter { it.mitglieder > 0 }.map { 1.0 - it.anteil }.takeIf { it.isNotEmpty() }?.average() ?: 0.0,
            abdeckung = abdeckung,
            mitgliederStand = mitgliederStand,
            bereiche = FinderRueckblick.IM_TEST.map { it.name },
            gewichte = gewichte.filterValues { it > 0 }.mapKeys { it.key.name },
            datenlage = FinderRueckblick.IM_TEST.associate { it.name to datenlage(s, it) },
            mitFakten = mitFakten,
            horizonte = HORIZONTE.mapNotNull { horizont(s, gewichte, it) },
            pruefzeit = HORIZONTE.mapNotNull { horizont(pruef, gewichte, it) },
            ics = FinderRueckblick.IM_TEST.flatMap { b -> HORIZONTE.mapNotNull { bereichWert(s, b, it) } },
            kennzahlen = FinderRueckblick.MESS_KENNZAHLEN.flatMap { k -> HORIZONTE.mapNotNull { kennzahlWert(s, k, it) } },
            jahre = jahre(m),
            strategie = strategie(m),
            lernen = walkForward(s, gewichte),
            halten = HALTEN.mapNotNull { horizont(s, gewichte, it) },
            haltenHeute = heute?.let { haltenHeute(s, gewichte, it) },
            zufall = zufall(s, gewichte),
            universum = universum,
        )
    }
}
