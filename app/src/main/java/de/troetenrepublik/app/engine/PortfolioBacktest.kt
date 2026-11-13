package de.troetenrepublik.app.engine

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// Rueckrechnung eines Portfolios aus mehreren Werten mit festen Zielgewichten (0.78). Reine Rechnung ohne Netz:
// Eingabe sind Tagesschlusskurse je Wert in Euro (Zeit in ms, Kurs), Zielgewichte, eine Umschichtregel, Startbetrag
// und Zahlungen; Ausgabe sind Wertreihe, zeitgewichteter Index, Geschaefte und Kennzahlen.
// Ablauf je Handelstag (jeder Tag, an dem mindestens ein Wert einen Kurs hat; fehlende Kurse gelten vom letzten Tag
// fort): Kurse fortschreiben, faellige Zahlungen nach Zielgewichten anlegen oder anteilig entnehmen, dann pruefen, ob
// umgeschichtet wird. Der Zeitraum ist der gemeinsame aller Werte mit Gewicht.
// Kosten je Kauf und Verkauf: halber Spread vom Betrag und eine feste Gebuehr je Order, wie im Strategietest.
object PortfolioBacktest {

    const val TAG = 86_400_000L
    private const val JAHR_TAGE = 365.25

    // Endet ein Wert hoechstens so viele Tage vor dem spaetesten, gilt er als laufend (Feiertage, andere Boersen).
    const val ENDE_TOLERANZ = 8L

    // Umschichten nach Kalender: alle monate Monate ab dem Starttag, 0 = nie.
    enum class Takt(val monate: Int) { NIE(0), JAEHRLICH(12), HALBJAEHRLICH(6), QUARTAL(3), MONATLICH(1) }

    // band: Abweichung vom Zielgewicht als Anteil (0,05 = 5 Prozentpunkte), ab der das ganze Portfolio umgeschichtet
    // wird; mit Band zaehlt der Takt nicht. Kleine Bausteine haben wie in Umschichtschwelle ein engeres Band.
    data class Regel(val takt: Takt = Takt.JAEHRLICH, val band: Double? = null)

    data class Kosten(val spreadBps: Double = 0.0, val gebuehr: Double = 0.0)

    // betrag positiv: Einzahlung, negativ: Entnahme.
    data class Zahlung(val t: Long, val betrag: Double)

    data class Eingabe(
        val kurse: List<List<Pair<Long, Double>>>,
        val gewichte: List<Double>,
        val regel: Regel = Regel(),
        val start: Double = 10_000.0,
        val zahlungen: List<Zahlung> = emptyList(),
        val kosten: Kosten = Kosten(),
        // Grenzen des Zeitraums in ms; ohne gilt der ganze gemeinsame Zeitraum.
        val von: Long? = null,
        val bis: Long? = null,
    )

    // wert: Stelle in der Eingabe. betrag positiv: Kauf, negativ: Verkauf.
    data class Geschaeft(val t: Long, val wert: Int, val betrag: Double, val kosten: Double, val umschichtung: Boolean)

    data class Ergebnis(
        // Depotwert am Ende jedes Handelstags.
        val verlauf: List<Pair<Long, Double>>,
        // Zeitgewichteter Index: 1 am Starttag vor Kosten; Zahlungen veraendern ihn nicht, Kosten schon.
        val index: List<Pair<Long, Double>>,
        val geschaefte: List<Geschaeft>,
        val umschichtungen: Int,
        val kosten: Double,
        // Gebuchte Zahlungen samt Startbetrag.
        val zahlungen: List<Zahlung>,
    ) {
        val endwert: Double get() = verlauf.last().second
        val eingezahlt: Double get() = zahlungen.sumOf { it.betrag }
    }

    // Kalender ohne Zeitzone: Tage seit 1970 nach Jahr, Monat, Tag und zurueck (proleptisch gregorianisch).

    fun datum(tag: Long): Triple<Int, Int, Int> {
        val z = tag + 719_468L
        val aera = z.floorDiv(146_097L)
        val tagDerAera = (z - aera * 146_097L).toInt()
        val jahrDerAera = (tagDerAera - tagDerAera / 1460 + tagDerAera / 36_524 - tagDerAera / 146_096) / 365
        val tagImJahr = tagDerAera - (365 * jahrDerAera + jahrDerAera / 4 - jahrDerAera / 100)
        val mp = (5 * tagImJahr + 2) / 153
        val d = tagImJahr - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        val j = jahrDerAera + aera.toInt() * 400 + if (m <= 2) 1 else 0
        return Triple(j, m, d)
    }

    fun tagVon(jahr: Int, monat: Int, tag: Int): Long {
        val j = if (monat <= 2) jahr - 1 else jahr
        val aera = j.floorDiv(400)
        val jahrDerAera = j - aera * 400
        val tagImJahr = (153 * (if (monat > 2) monat - 3 else monat + 9) + 2) / 5 + tag - 1
        val tagDerAera = jahrDerAera * 365 + jahrDerAera / 4 - jahrDerAera / 100 + tagImJahr
        return aera * 146_097L + tagDerAera - 719_468L
    }

    fun monatsLaenge(jahr: Int, monat: Int): Int = when (monat) {
        2 -> if (jahr % 4 == 0 && (jahr % 100 != 0 || jahr % 400 == 0)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    // Volle Monate von start bis tag; der Monat ist voll am selben Tag des Monats, in kuerzeren Monaten am letzten.
    fun monateSeit(start: Long, tag: Long): Int {
        val (j0, m0, d0) = datum(start)
        val (j, m, d) = datum(tag)
        val voll = d >= min(d0, monatsLaenge(j, m))
        return (j - j0) * 12 + (m - m0) - if (voll) 0 else 1
    }

    // Monatliche Zahlungen: ab einem Monat nach von am selben Tag des Monats, bis einschliesslich bis.
    fun sparrate(von: Long, bis: Long, betrag: Double): List<Zahlung> {
        if (!(betrag > 0) || bis <= von) return emptyList()
        val (j0, m0, d0) = datum(von.floorDiv(TAG))
        val aus = ArrayList<Zahlung>()
        var n = 1
        while (true) {
            val monate = j0 * 12 + (m0 - 1) + n
            val j = monate.floorDiv(12)
            val m = monate - j * 12 + 1
            val t = tagVon(j, m, min(d0, monatsLaenge(j, m))) * TAG
            if (t > bis) break
            aus += Zahlung(t, betrag)
            n++
        }
        return aus
    }

    // Kurse eines Werts als Tage und Schlusskurse, aufsteigend, je Tag der letzte gueltige Kurs.
    private class Kursreihe(val tage: LongArray, val kurse: DoubleArray)

    private fun kursreihe(reihe: List<Pair<Long, Double>>): Kursreihe {
        val sauber = reihe.filter { it.second.isFinite() && it.second > 0 }.sortedBy { it.first }
        val tage = ArrayList<Long>(sauber.size)
        val kurse = ArrayList<Double>(sauber.size)
        for ((t, k) in sauber) {
            val d = t.floorDiv(TAG)
            if (tage.isNotEmpty() && tage.last() == d) kurse[kurse.lastIndex] = k else { tage += d; kurse += k }
        }
        return Kursreihe(tage.toLongArray(), kurse.toDoubleArray())
    }

    // Gemeinsamer Zeitraum mehrerer Kursreihen in ms (erster und letzter Tag); null, wenn eine leer ist oder nichts
    // gemeinsam bleibt.
    fun zeitraum(kurse: List<List<Pair<Long, Double>>>): Pair<Long, Long>? = grenzen(kurse.map { kursreihe(it) })?.let { it.first * TAG to it.second * TAG }

    private fun grenzen(reihen: List<Kursreihe>): Pair<Long, Long>? {
        if (reihen.isEmpty() || reihen.any { it.tage.isEmpty() }) return null
        val anfang = reihen.maxOf { it.tage.first() }
        val spaet = reihen.maxOf { it.tage.last() }
        val frueh = reihen.minOf { it.tage.last() }
        val ende = if (spaet - frueh <= ENDE_TOLERANZ) spaet else frueh
        return if (ende > anfang) anfang to ende else null
    }

    // Erlaubte Abweichung eines Bausteins bei einem Band: das Band, bei kleinen Bausteinen hoechstens der Anteil
    // RELATIV ihres Ziels (wie Umschichtschwelle.schwelle fuer das feste Band von 10 Punkten).
    fun erlaubt(ziel: Double, band: Double): Double =
        if (ziel <= Umschichtschwelle.KLEIN_BIS) min(band, ziel * Umschichtschwelle.RELATIV) else band

    // Null bei unpassender Eingabe, ohne gemeinsamen Zeitraum oder mit weniger als zwei Handelstagen.
    fun rechnen(e: Eingabe): Ergebnis? {
        if (e.kurse.isEmpty() || e.kurse.size != e.gewichte.size || !(e.start > 0) || !e.start.isFinite()) return null
        if (e.gewichte.any { !it.isFinite() || it < 0 }) return null
        val summe = e.gewichte.sum()
        if (!(summe > 0)) return null
        // Nur Werte mit Gewicht zaehlen, ihre Stelle in der Eingabe bleibt fuer die Geschaefte erhalten
        val stellen = e.gewichte.indices.filter { e.gewichte[it] > 0 }
        val n = stellen.size
        val w = DoubleArray(n) { e.gewichte[stellen[it]] / summe }
        val reihen = stellen.map { kursreihe(e.kurse[it]) }
        val (gemeinsamAb, gemeinsamBis) = grenzen(reihen) ?: return null
        val anfang = maxOf(gemeinsamAb, e.von?.floorDiv(TAG) ?: Long.MIN_VALUE)
        val ende = minOf(gemeinsamBis, e.bis?.floorDiv(TAG) ?: Long.MAX_VALUE)
        if (ende <= anfang) return null
        val tage = reihen.flatMap { r -> r.tage.filter { it in anfang..ende } }.distinct().sorted()
        if (tage.size < 2) return null

        val halb = e.kosten.spreadBps / 20_000.0
        val gebuehr = e.kosten.gebuehr
        val zeiger = IntArray(n) { i -> reihen[i].tage.indexOfLast { it <= tage.first() }.coerceAtLeast(0) }
        val kurs = DoubleArray(n)
        val stueck = DoubleArray(n)
        val geschaefte = ArrayList<Geschaeft>()
        val gebucht = ArrayList<Zahlung>()
        var kostenSumme = 0.0
        var umschichtungen = 0
        fun wert(): Double { var s = 0.0; for (i in 0 until n) s += stueck[i] * kurs[i]; return s }

        // Betrag nach Zielgewichten anlegen
        fun kaufen(t: Long, betrag: Double) {
            for (i in 0 until n) {
                val b = betrag * w[i]
                if (b <= 0) continue
                val k = min(b, b * halb + gebuehr)
                stueck[i] += (b - k) / kurs[i]
                kostenSumme += k
                geschaefte += Geschaeft(t, stellen[i], b, k, false)
            }
        }

        // Betrag anteilig aus allen Werten nehmen; die Kosten des Verkaufs mindern die Auszahlung, nicht das Depot.
        // Liefert den entnommenen Betrag (hoechstens das ganze Depot).
        fun entnehmen(t: Long, betrag: Double): Double {
            val gesamt = wert()
            if (gesamt <= 0) return 0.0
            val anteil = min(1.0, betrag / gesamt)
            for (i in 0 until n) {
                val b = stueck[i] * kurs[i] * anteil
                if (b <= 0) continue
                val k = min(b, b * halb + gebuehr)
                stueck[i] *= 1 - anteil
                kostenSumme += k
                geschaefte += Geschaeft(t, stellen[i], -b, k, false)
            }
            return gesamt * anteil
        }

        // Alles auf die Zielgewichte stellen; die Kosten gehen vom Depotwert ab. Ohne noetiges Geschaeft passiert nichts.
        fun umschichten(t: Long) {
            val gesamt = wert()
            if (gesamt <= 0) return
            val schwelle = gesamt * 1e-7
            var k = 0.0
            var noetig = false
            val neu = ArrayList<Geschaeft>(n)
            for (i in 0 until n) {
                val delta = gesamt * w[i] - stueck[i] * kurs[i]
                if (abs(delta) <= schwelle) continue
                noetig = true
                val ki = abs(delta) * halb + gebuehr
                k += ki
                neu += Geschaeft(t, stellen[i], delta, ki, true)
            }
            if (!noetig) return
            val rest = maxOf(0.0, gesamt - k)
            for (i in 0 until n) stueck[i] = rest * w[i] / kurs[i]
            kostenSumme += min(k, gesamt)
            geschaefte += neu
            umschichtungen++
        }

        val zahlungen = e.zahlungen.filter { it.betrag.isFinite() && it.betrag != 0.0 }.sortedBy { it.t }
        // Zahlungen bis zum Starttag gehoeren nicht zum Zeitraum
        var z = zahlungen.indexOfFirst { it.t.floorDiv(TAG) > tage.first() }.let { if (it < 0) zahlungen.size else it }
        val verlauf = ArrayList<Pair<Long, Double>>(tage.size)
        val indexReihe = ArrayList<Pair<Long, Double>>(tage.size)
        var index = 1.0
        var vorher = 0.0
        // Kosten eines Tages wirken im Index am naechsten Tag, damit er am Starttag bei 1 beginnt
        var uebertrag = 1.0
        var periode = 0
        val band = e.regel.band?.takeIf { it.isFinite() && it >= 0 }

        for ((k, d) in tage.withIndex()) {
            for (i in 0 until n) {
                val r = reihen[i]
                while (zeiger[i] + 1 < r.tage.size && r.tage[zeiger[i] + 1] <= d) zeiger[i]++
                kurs[i] = r.kurse[zeiger[i]]
            }
            val t = d * TAG
            val offen = wert()
            var fluss = 0.0
            if (k == 0) {
                kaufen(t, e.start)
                gebucht += Zahlung(t, e.start)
                fluss = e.start
            } else {
                if (vorher > 0) index *= uebertrag * offen / vorher
                uebertrag = 1.0
                while (z < zahlungen.size && zahlungen[z].t.floorDiv(TAG) <= d) {
                    val b = zahlungen[z++].betrag
                    if (b > 0) { kaufen(t, b); gebucht += Zahlung(t, b); fluss += b }
                    else {
                        val raus = entnehmen(t, -b)
                        if (raus > 0) { gebucht += Zahlung(t, -raus); fluss -= raus }
                    }
                }
                val gesamt = wert()
                if (gesamt > 0) {
                    val faellig = if (band != null) {
                        (0 until n).any { i -> abs(stueck[i] * kurs[i] / gesamt - w[i]) > erlaubt(w[i], band) + 1e-12 }
                    } else if (e.regel.takt.monate > 0) {
                        val p = monateSeit(tage.first(), d) / e.regel.takt.monate
                        if (p > periode) { periode = p; true } else false
                    } else false
                    if (faellig) umschichten(t)
                }
            }
            val schluss = wert()
            val brutto = offen + fluss
            if (brutto > 0) uebertrag *= schluss / brutto
            vorher = schluss
            verlauf += t to schluss
            indexReihe += t to index
        }
        // Kosten des letzten Tages gehoeren noch in den Index
        if (uebertrag != 1.0 && indexReihe.size > 1) indexReihe[indexReihe.lastIndex] = indexReihe.last().first to index * uebertrag
        return Ergebnis(verlauf, indexReihe, geschaefte, umschichtungen, kostenSumme, gebucht)
    }

    // In Kaufkraft des Starttags: Werte, Index und Zahlungen durch die Preissteigerung seit dem Start geteilt.
    // preis: Preisindex zum Zeitpunkt (ms); null, wenn er am Start oder an einem Punkt fehlt.
    fun real(e: Ergebnis, preis: (Long) -> Double?): Ergebnis? {
        val p0 = preis(e.verlauf.first().first)?.takeIf { it > 0 } ?: return null
        fun faktor(t: Long): Double? = preis(t)?.takeIf { it > 0 }?.let { p0 / it }
        val verlauf = e.verlauf.map { (t, v) -> t to v * (faktor(t) ?: return null) }
        val index = e.index.map { (t, v) -> t to v * (faktor(t) ?: return null) }
        val zahlungen = e.zahlungen.map { it.copy(betrag = it.betrag * (faktor(it.t) ?: return null)) }
        return e.copy(verlauf = verlauf, index = index, zahlungen = zahlungen)
    }

    // Groesster Rueckgang vom Hoch: tiefe negativ als Anteil, von = Tag des Hochs, tief = Tag des Tiefs, bis = Tag,
    // an dem das Hoch wieder erreicht war (null, wenn noch nicht), tage = Dauer vom Hoch bis dahin oder bis zum Ende.
    data class Rueckgang(val tiefe: Double, val von: Long, val tief: Long, val bis: Long?, val tage: Long)

    fun rueckgang(reihe: List<Pair<Long, Double>>): Rueckgang? {
        if (reihe.size < 2) return null
        var hoch = reihe.first().second
        var hochZeit = reihe.first().first
        var tiefe = 0.0
        var von = hochZeit
        var tief = hochZeit
        var hochDesTiefs = hoch
        for ((t, v) in reihe) {
            if (v >= hoch) { hoch = v; hochZeit = t }
            val dd = if (hoch > 0) v / hoch - 1 else 0.0
            if (dd < tiefe) { tiefe = dd; von = hochZeit; tief = t; hochDesTiefs = hoch }
        }
        if (tiefe >= 0) return Rueckgang(0.0, von, von, von, 0)
        val bis = reihe.firstOrNull { it.first > tief && it.second >= hochDesTiefs }?.first
        return Rueckgang(tiefe, von, tief, bis, ((bis ?: reihe.last().first) - von) / TAG)
    }

    // Abstand zum bisherigen Hoch je Punkt (0 oder negativ, als Anteil).
    fun rueckgangReihe(reihe: List<Pair<Long, Double>>): List<Pair<Long, Double>> {
        var hoch = 0.0
        return reihe.map { (t, v) ->
            if (v > hoch) hoch = v
            t to if (hoch > 0) v / hoch - 1 else 0.0
        }
    }

    data class Kennzahlen(
        val endwert: Double,
        val eingezahlt: Double,
        val jahre: Double,
        // Veraenderung des Index ueber den ganzen Zeitraum.
        val gesamt: Double,
        // Zeitgewichtete Rendite pro Jahr; null unter einem Jahr.
        val zeitgewichtet: Double?,
        // Zinsfuss aller Zahlungen pro Jahr; null unter einem Jahr oder ohne eindeutige Loesung.
        val geldgewichtet: Double?,
        val schwankung: Double?,
        val rueckgang: Rueckgang?,
        val sharpe: Double?,
        val sortino: Double?,
    )

    // zins: risikofreier Zins pro Jahr als Anteil fuer Sharpe und Sortino (Risiko.kennzahlen).
    fun kennzahlen(e: Ergebnis, zins: Double = 0.0): Kennzahlen {
        val index = e.index
        val jahre = (index.last().first - index.first().first) / (JAHR_TAGE * TAG)
        val gesamt = index.last().second / index.first().second - 1
        val lang = jahre >= 0.95
        val r = Risiko.tagesrenditen(index)
        val schwankung = if (r.size >= 20 && jahre > 0) {
            val m = r.average()
            sqrt(r.sumOf { (it - m) * (it - m) } / (r.size - 1)) * sqrt(r.size / jahre)
        } else null
        val risiko = Risiko.kennzahlen(index, zins)
        val geld = if (lang) PortfolioTools.moneyWeighted(e.zahlungen.map { PortfolioTools.Flow(it.t, it.betrag, "") }, e.endwert, e.verlauf.last().first) else null
        return Kennzahlen(
            endwert = e.endwert,
            eingezahlt = e.eingezahlt,
            jahre = jahre,
            gesamt = gesamt,
            zeitgewichtet = if (lang && gesamt > -1) (1 + gesamt).pow(1 / jahre) - 1 else null,
            geldgewichtet = geld,
            schwankung = schwankung,
            rueckgang = rueckgang(index),
            sharpe = risiko?.sharpe,
            sortino = risiko?.sortino,
        )
    }

    // Rendite je Haltedauer

    val HALTEDAUERN = listOf(1, 3, 5, 10, 15, 20)

    // So viele Starttage braucht eine Haltedauer mindestens (rund ein Jahr an Startzeitpunkten).
    const val MIN_FENSTER = 250

    // Verteilung der Rendite pro Jahr ueber alle Starttage, deren Haltedauer in die Reihe passt. Die Fenster
    // ueberlappen sich. verlust: Anteil der Fenster mit negativer Rendite.
    data class Haltedauer(
        val jahre: Int, val fenster: Int, val min: Double, val q1: Double, val median: Double, val q3: Double, val max: Double, val verlust: Double,
    )

    fun haltedauer(reihe: List<Pair<Long, Double>>, jahre: Int, minFenster: Int = MIN_FENSTER): Haltedauer? {
        if (jahre <= 0 || reihe.size < 2) return null
        val dauer = (jahre * JAHR_TAGE * TAG).toLong()
        val renditen = ArrayList<Double>()
        var j = 0
        for (i in reihe.indices) {
            val (t0, v0) = reihe[i]
            val ziel = t0 + dauer
            if (ziel > reihe.last().first) break
            while (reihe[j].first < ziel) j++
            val (t1, v1) = reihe[j]
            if (v0 > 0 && v1 > 0 && t1 > t0) renditen += (v1 / v0).pow(JAHR_TAGE * TAG / (t1 - t0)) - 1
        }
        if (renditen.size < minFenster) return null
        val s = renditen.sorted()
        return Haltedauer(
            jahre, s.size, s.first(), Risiko.quantil(s, 0.25), Risiko.quantil(s, 0.5), Risiko.quantil(s, 0.75), s.last(),
            s.count { it < 0 }.toDouble() / s.size,
        )
    }

    // Alle Haltedauern, fuer die die Reihe genug Fenster hergibt.
    fun haltedauern(reihe: List<Pair<Long, Double>>, minFenster: Int = MIN_FENSTER): List<Haltedauer> =
        HALTEDAUERN.mapNotNull { haltedauer(reihe, it, minFenster) }

    // Dieselbe Mischung mit mehreren Umschichtregeln nebeneinander: nie, jaehrlich, quartalsweise und das Band.
    fun regelVergleich(e: Eingabe, band: Double): List<Pair<Regel, Ergebnis>> =
        listOf(Regel(Takt.NIE), Regel(Takt.JAEHRLICH), Regel(Takt.QUARTAL), Regel(Takt.JAEHRLICH, band))
            .mapNotNull { r -> rechnen(e.copy(regel = r))?.let { r to it } }
}
