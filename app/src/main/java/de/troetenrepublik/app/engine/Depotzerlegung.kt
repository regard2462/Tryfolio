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

    // Ergebnis eines Tages (Beginn in ms, UTC) je Depotschluessel, dazu der Wert der Wertpapiere am Ende des Vortags.
    data class Tag(val beginn: Long, val je: Map<String, Double>, val wertVorher: Double)

    // Ein Abschnitt aus einem oder mehreren Tagen mit dem Ergebnis je Schluessel.
    data class Abschnitt(val beginn: Long, val ende: Long, val je: Map<String, Double>, val wertVorher: Double) {
        val summe: Double get() = je.values.sum()

        // Ergebnis in Prozent des Werts zu Beginn; null ohne Bestand am Vortag.
        val prozent: Double? get() = if (wertVorher > 1.0) summe / wertVorher * 100.0 else null
    }

    enum class Raster { TAG, WOCHE, MONAT }

    fun tagBeginn(t: Long): Long = Math.floorDiv(t, TAG) * TAG

    private fun richtung(type: TxType): Double = when (type) {
        TxType.BUY -> 1.0
        TxType.SELL, TxType.KNOCKOUT, TxType.EXPIRY -> -1.0
        else -> 0.0
    }

    // Buchungen, die Stueck ins Depot bringen oder herausnehmen; ihr Betrag ist ein Geldfluss der Depotkurve.
    fun imDepot(type: TxType): Boolean = richtung(type) != 0.0

    // Stueck mal Preis; Kauf positiv, Verkauf, Knock-out und Verfall negativ.
    fun fluss(t: Transaction): Double = richtung(t.type) * t.qty * t.price

    // Netto-Kaeufe je Schluessel und Tagesbeginn: Stueck mal Preis, Kauf positiv, Verkauf negativ.
    fun zufluesse(account: Account): Map<Pair<String, Long>, Double> {
        val m = HashMap<Pair<String, Long>, Double>()
        for (t in account.transactions) {
            val r = richtung(t.type)
            val k = t.posKey
            if (r == 0.0 || k.isBlank()) continue
            val schluessel = k to tagBeginn(t.ts)
            m[schluessel] = (m[schluessel] ?: 0.0) + r * t.qty * t.price
        }
        return m
    }

    // Stueckzahl je Schluessel zum Zeitpunkt at aus dem heutigen Bestand und den spaeteren Buchungen (auch ohne Produkt).
    fun bestand(account: Account, at: Long): Map<String, Double> {
        val q = HashMap<String, Double>()
        for (p in account.positions) q[p.key] = p.qty
        for (t in account.transactions) {
            if (t.ts <= at) continue
            val r = richtung(t.type)
            val k = t.posKey
            if (r == 0.0 || k.isBlank()) continue
            q[k] = (q[k] ?: 0.0) - r * t.qty
        }
        return q.filterValues { it > 1e-9 }
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
            for (k in schluessel) {
                val alt = vorher[k] ?: 0.0
                val w = wert(k, t) ?: alt
                jetzt[k] = w
                val e = w - alt - (zufluesse[k to t] ?: 0.0)
                if (abs(e) >= 0.005) je[k] = e
            }
            aus += Tag(t, je, vorher.values.sum())
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
            }
            gruppen.getOrPut(k) { ArrayList() } += t
        }
        return gruppen.values.map { l ->
            val je = HashMap<String, Double>()
            for (t in l) for ((k, v) in t.je) je[k] = (je[k] ?: 0.0) + v
            Abschnitt(l.first().beginn, l.last().beginn + TAG - 1, je.filterValues { abs(it) >= 0.005 }, l.first().wertVorher)
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
