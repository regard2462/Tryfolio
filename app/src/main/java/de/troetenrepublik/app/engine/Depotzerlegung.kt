package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import java.time.LocalDate
import kotlin.math.abs

// Woher Gewinne und Verluste des Depots kommen. Je Tag und Position das Ergebnis ohne die Geldfluesse von Kauf
// und Verkauf: Wert am Tagesende minus Wert am Vortagesende minus Netto-Kauf des Tages (Kauf positiv; Verkauf,
// Knock-out und Verfall negativ). Gebuehren, Steuern, Zinsen und Dividenden zaehlen nicht, wie in der Depotkurve.
object Depotzerlegung {

    const val TAG = 86_400_000L

    // Ergebnis eines Tages (Beginn in ms, UTC) je Depotschluessel, dazu der Wert der Wertpapiere am Ende des Vortags und
    // die Kaeufe des Tages (Zufluesse, ohne Verkaeufe).
    // wert: Wert je Schluessel am Ende des Tages, nur Bestaende ungleich null.
    data class Tag(val beginn: Long, val je: Map<String, Double>, val wertVorher: Double, val zufluss: Double = 0.0, val wert: Map<String, Double> = emptyMap())

    // Ein Abschnitt aus einem oder mehreren Tagen mit dem Ergebnis je Schluessel.
    data class Abschnitt(val beginn: Long, val ende: Long, val je: Map<String, Double>, val wertVorher: Double, val zufluss: Double = 0.0) {
        val summe: Double get() = je.values.sum()

        // Ergebnis in Prozent des Werts zu Beginn plus der Kaeufe im Abschnitt (Zufluesse zaehlen am Anfang wie in der
        // Performanceanalyse); null ohne Bestand und ohne Kaeufe.
        val prozent: Double? get() = (wertVorher + zufluss).takeIf { it > 1.0 }?.let { summe / it * 100.0 }
    }

    enum class Raster { TAG, WOCHE, MONAT, QUARTAL, JAHR }

    fun tagBeginn(t: Long): Long = Math.floorDiv(t, TAG) * TAG

    private fun richtung(type: TxType): Double = when (type) {
        TxType.BUY -> 1.0
        TxType.SELL, TxType.KNOCKOUT, TxType.EXPIRY -> -1.0
        else -> 0.0
    }

    // Buchungen, die Stueck ins Depot bringen oder herausnehmen; ihr Betrag ist ein Geldfluss der Depotkurve.
    fun imDepot(type: TxType): Boolean = richtung(type) != 0.0

    // Stueck mal Preis; Kauf positiv, Verkauf, Knock-out und Verfall negativ. Verkaufte Optionen: Verkauf zur Eroeffnung
    // negativ, Rueckkauf und Ausgleich bei Verfall positiv (Transaction.bestandsDelta).
    fun fluss(t: Transaction): Double = t.bestandsDelta * t.price

    // Netto-Kaeufe je Schluessel und Tagesbeginn: Stueck mal Preis, Kauf positiv, Verkauf negativ.
    fun zufluesse(account: Account): Map<Pair<String, Long>, Double> {
        val m = HashMap<Pair<String, Long>, Double>()
        for (t in account.transactions) {
            val k = t.posKey
            if (richtung(t.type) == 0.0 || k.isBlank()) continue
            val schluessel = k to tagBeginn(t.ts)
            m[schluessel] = (m[schluessel] ?: 0.0) + fluss(t)
        }
        return m
    }

    // Stueckzahl je Schluessel zum Zeitpunkt at aus dem heutigen Bestand und den spaeteren Buchungen (auch ohne Produkt).
    // Wie Trading.holdingsAt in heutigen Einheiten: Buchungen vor einem Split zaehlen mit dem Splitverhaeltnis.
    // extern: Splits uebernommener Depots wie in Trading.holdingsAt (Buchungen des Brokers zum Split am Splittag entfallen).
    fun bestand(account: Account, at: Long, extern: Map<String, List<Splits.Split>> = emptyMap()): Map<String, Double> {
        val q = HashMap<String, Double>()
        for (p in account.positions) q[p.key] = p.qty
        val gebucht = account.transactions.filter { it.type == TxType.SPLIT && it.extra > 0 }.map { Triple(it.posKey, it.ts, it.extra) }
        val fremd = extern.flatMap { (k, l) ->
            l.filter { s -> s.verhaeltnis > 0 && gebucht.none { it.first == k && it.second / TAG == s.t / TAG } }.map { s -> Triple(k, s.t, s.verhaeltnis) }
        }
        val splits = gebucht + fremd
        for (t in account.transactions) {
            if (t.ts <= at) continue
            val k = t.posKey
            if (richtung(t.type) == 0.0 || k.isBlank()) continue
            if (extern.isNotEmpty() && t.tag in Trading.SPLIT_TAGS && extern[k].orEmpty().any { it.t / TAG == t.ts / TAG }) continue
            val faktor = if (splits.isEmpty()) 1.0 else splits.filter { it.first == k && it.second > t.ts }.fold(1.0) { f, s -> f * s.third }
            q[k] = (q[k] ?: 0.0) - t.bestandsDelta * faktor
        }
        // Verkaufte Optionen stehen mit negativem Bestand
        return q.filterValues { abs(it) > 1e-9 }
    }

    // Handelspreise je Schluessel, zeitlich sortiert.
    fun handelspreise(account: Account): Map<String, List<Pair<Long, Double>>> =
        account.transactions.filter { it.price > 0 && richtung(it.type) != 0.0 && it.posKey.isNotBlank() }
            .groupBy { it.posKey }.mapValues { (_, l) -> l.sortedBy { it.ts }.map { it.ts to it.price } }

    // Letzter Handelspreis bis at; null ohne Handel.
    fun preisBis(preise: List<Pair<Long, Double>>?, at: Long): Double? = preise?.lastOrNull { it.first <= at }?.second

    // wert liefert den Wert eines Schluessels am Tagesende (Stueck mal Preis, 0 ohne Bestand) oder null ohne Kurs;
    // dann gilt der letzte bekannte Wert. wert wird Tag fuer Tag in aufsteigender Folge aufgerufen.
    fun tage(
        von: Long,
        bis: Long,
        schluessel: Collection<String>,
        zufluesse: Map<Pair<String, Long>, Double>,
        wert: (String, Long) -> Double?,
    ): List<Tag> {
        val aus = ArrayList<Tag>()
        var vorher = HashMap<String, Double>()
        var t = tagBeginn(von)
        val ende = tagBeginn(bis)
        while (t <= ende) {
            val jetzt = HashMap<String, Double>(schluessel.size * 2)
            val je = HashMap<String, Double>()
            var zufluss = 0.0
            for (k in schluessel) {
                val alt = vorher[k] ?: 0.0
                val w = wert(k, t) ?: alt
                if (w != 0.0) jetzt[k] = w
                val fluss = zufluesse[k to t] ?: 0.0
                if (fluss > 0.0) zufluss += fluss
                val e = w - alt - fluss
                if (abs(e) >= 0.005) je[k] = e
            }
            aus += Tag(t, je, vorher.values.sum(), zufluss, jetzt)
            vorher = jetzt
            t += TAG
        }
        return aus
    }

    // Wochen beginnen am Montag.
    fun abschnitte(tage: List<Tag>, raster: Raster): List<Abschnitt> {
        if (tage.isEmpty()) return emptyList()
        val gruppen = LinkedHashMap<Long, MutableList<Tag>>()
        for (t in tage) {
            val nr = t.beginn / TAG
            val k = when (raster) {
                Raster.TAG -> nr
                Raster.WOCHE -> Math.floorDiv(nr + 3, 7L)
                Raster.MONAT -> LocalDate.ofEpochDay(nr).let { it.year * 12L + it.monthValue }
                Raster.QUARTAL -> LocalDate.ofEpochDay(nr).let { it.year * 4L + (it.monthValue - 1) / 3 }
                Raster.JAHR -> LocalDate.ofEpochDay(nr).year.toLong()
            }
            gruppen.getOrPut(k) { ArrayList() } += t
        }
        return gruppen.values.map { l ->
            val je = HashMap<String, Double>()
            for (t in l) for ((k, v) in t.je) je[k] = (je[k] ?: 0.0) + v
            Abschnitt(l.first().beginn, l.last().beginn + TAG - 1, je.filterValues { abs(it) >= 0.005 }, l.first().wertVorher, l.sumOf { it.zufluss })
        }
    }

    // Die n groessten Anstiege (oder Rueckgaenge) nach Betrag, groesster zuerst.
    fun groesste(abschnitte: List<Abschnitt>, n: Int, anstieg: Boolean): List<Abschnitt> =
        abschnitte.filter { if (anstieg) it.summe >= 0.01 else it.summe <= -0.01 }
            .sortedBy { if (anstieg) -it.summe else it.summe }
            .take(n)

    // Ergebnis je Schluessel ueber alle Tage, groesster Betrag zuerst.
    fun summe(tage: List<Tag>): List<Pair<String, Double>> {
        val m = HashMap<String, Double>()
        for (t in tage) for ((k, v) in t.je) m[k] = (m[k] ?: 0.0) + v
        return m.filterValues { abs(it) >= 0.01 }.entries.sortedByDescending { abs(it.value) }.map { it.key to it.value }
    }

    // Positionen, die einen Abschnitt getragen haben: in Richtung seines Ergebnisses, groesste zuerst.
    fun ursachen(a: Abschnitt): List<Pair<String, Double>> {
        val vorzeichen = if (a.summe >= 0) 1.0 else -1.0
        return a.je.entries.sortedByDescending { it.value * vorzeichen }.map { it.key to it.value }
    }
}
