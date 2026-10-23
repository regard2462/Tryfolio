package de.troetenrepublik.app.engine

import java.util.TreeMap
import kotlin.math.max
import kotlin.math.sqrt

// Auswertungen ueber alle Abgeordneten und Senatoren: die Strategie „Kongress kauft“ und die Rangliste.
//
// Kongress kauft: Jede Woche ein neues Depot aus den Aktien, deren Kauf in den letzten 30 Tagen gemeldet wurde,
// gewichtet nach der Mitte der gemeldeten Spannen, hoechstens 10 % je Aktie. Gerechnet wird ab dem Tag der Meldung,
// nicht ab dem Geschaeft: so haette man es tatsaechlich nachbilden koennen.
//
// Rangliste: Fuer jeden Kauf der letzten zwoelf Monate die Entwicklung der Aktie vom Tag des Geschaefts bis heute
// gegen den S&P 500 im selben Zeitraum, gewichtet nach der Mitte der Spanne.
object KongressAuswertung {

    const val TAG = 86_400_000L

    data class Handel(
        val person: String,
        val name: String,
        // H Repraesentantenhaus, S Senat.
        val gruppe: Char,
        val symbol: String,
        val kauf: Boolean,
        val datum: Long,
        val gemeldet: Long,
        val betrag: Double,
    )

    // Schlusskurse aufsteigend nach Tag (Tagesnummer seit 1970), in Feldern statt Objekten: bei ueber tausend Aktien
    // reicht sonst der Speicher eines Telefons nicht.
    class Kurse(private val tage: IntArray, private val werte: FloatArray) {
        val leer: Boolean get() = tage.isEmpty()

        // Kurs am Tag oder am letzten Tag davor; null vor dem ersten Kurs.
        fun am(ms: Long): Double? {
            val tag = Math.floorDiv(ms, TAG).toInt()
            var lo = 0
            var hi = tage.size - 1
            var treffer = -1
            while (lo <= hi) {
                val mitte = (lo + hi) ushr 1
                if (tage[mitte] <= tag) { treffer = mitte; lo = mitte + 1 } else hi = mitte - 1
            }
            return if (treffer < 0) null else werte[treffer].toDouble()
        }

        val letzter: Double? get() = if (werte.isEmpty()) null else werte.last().toDouble()

        companion object {
            fun aus(karte: Map<Long, Double>): Kurse {
                val s = TreeMap(karte)
                return Kurse(s.keys.map { it.toInt() }.toIntArray(), s.values.map { it.toFloat() }.toFloatArray())
            }
        }
    }

    data class Strategie(
        val verlauf: List<Pair<Long, Double>>,
        val vergleich: List<Pair<Long, Double>>,
        val gewichte: List<Pair<String, Double>>,
        val renditePa: Double?,
        val vergleichPa: Double?,
        val schwankungPa: Double?,
        val maxVerlust: Double,
    )

    const val FENSTER_TAGE = 30

    // Seit Beginn: Jeder gemeldete Kauf bleibt im Depot, bis dieselbe Person verkauft.
    const val MAX_TAGE = 100_000

    // Waehlbare Zeitraeume, aus deren Kaeufen das Depot besteht.
    val FENSTER = listOf(30, 90, 180, 365, MAX_TAGE)
    const val HOECHSTENS = 0.10

    // Gewichte zum Zeitpunkt bis: je Person und Aktie die Kaeufe, deren Meldung in (bis - tage, bis] liegt, abzueglich
    // der Verkaeufe derselben Person, die bis dahin gemeldet wurden (nie unter null); nur Aktien mit Kurs.
    fun gewichte(handel: List<Handel>, bis: Long, tage: Int = FENSTER_TAGE, hatKurs: (String) -> Boolean): List<Pair<String, Double>> {
        val ab = bis - tage.toLong() * TAG
        val sortiert = if ((1 until handel.size).all { handel[it - 1].gemeldet <= handel[it].gemeldet }) handel else handel.sortedBy { it.gemeldet }
        val bestand = HashMap<Pair<String, String>, Double>()
        for (h in sortiert) {
            if (h.gemeldet > bis) break
            val k = h.person to h.symbol
            if (h.kauf) { if (h.gemeldet > ab) bestand.merge(k, h.betrag, Double::plus) }
            else bestand[k]?.let { b -> if (b - h.betrag > 0) bestand[k] = b - h.betrag else bestand.remove(k) }
        }
        val summen = HashMap<String, Double>()
        for ((k, b) in bestand) if (b > 0 && hatKurs(k.second)) summen.merge(k.second, b, Double::plus)
        return deckeln(summen)
    }

    // Anteile mit Obergrenze: Was ueber 10 % liegt, wird auf die uebrigen verteilt.
    fun deckeln(summen: Map<String, Double>, grenze: Double = HOECHSTENS): List<Pair<String, Double>> {
        val gesamt = summen.values.sum()
        if (gesamt <= 0) return emptyList()
        val w = summen.mapValues { it.value / gesamt }.toMutableMap()
        if (w.size * grenze < 1.0) return w.entries.sortedByDescending { it.value }.map { it.key to it.value }
        repeat(20) {
            val ueber = w.filterValues { it > grenze + 1e-12 }
            if (ueber.isEmpty()) return@repeat
            val rest = ueber.values.sumOf { it - grenze }
            ueber.keys.forEach { w[it] = grenze }
            val frei = w.filterValues { it < grenze - 1e-12 }
            val s = frei.values.sum()
            if (s <= 0) return@repeat
            frei.forEach { (k, v) -> w[k] = v + rest * v / s }
        }
        return w.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }

    // Woechentlicher Verlauf ab dem ersten Tag mit vollem Meldefenster, Start bei 100.
    fun strategie(handel: List<Handel>, kurse: Map<String, Kurse>, index: Kurse?, jetzt: Long, tage: Int = FENSTER_TAGE): Strategie? {
        // Kaeufe und Verkaeufe, nach Meldung sortiert
        val kaeufe = handel.filter { it.symbol in kurse }.sortedBy { it.gemeldet }
        val erste = kaeufe.filter { it.kauf }.minOfOrNull { it.gemeldet } ?: return null
        // Beginn, sobald das Fenster einmal gefuellt ist, bei langen Fenstern nach 30 Tagen
        var t = Math.floorDiv(erste, TAG) * TAG + minOf(tage, FENSTER_TAGE) * TAG
        val verlauf = ArrayList<Pair<Long, Double>>()
        val vergleich = ArrayList<Pair<Long, Double>>()
        var wert = 100.0
        var iWert = 100.0
        verlauf += t to wert
        index?.let { vergleich += t to iWert }
        val wochen = ArrayList<Double>()
        while (t + 7 * TAG <= jetzt) {
            val n = t + 7 * TAG
            val w = gewichte(kaeufe, t, tage) { s -> kurse[s]?.am(t) != null && kurse[s]?.am(n) != null }
            val r = if (w.isEmpty()) 0.0 else w.sumOf { (s, g) -> val k = kurse.getValue(s); g * (k.am(n)!! / k.am(t)!! - 1) }
            wert *= 1 + r
            wochen += r
            verlauf += n to wert
            if (index != null) {
                val a = index.am(t)
                val b = index.am(n)
                if (a != null && b != null && a > 0) iWert *= b / a
                vergleich += n to iWert
            }
            t = n
        }
        if (verlauf.size < 3) return null
        val jahre = (verlauf.last().first - verlauf.first().first) / (365.25 * TAG)
        fun pa(l: List<Pair<Long, Double>>) = if (jahre >= 0.9 && l.size > 1) Math.pow(l.last().second / l.first().second, 1 / jahre) - 1 else null
        var hoch = 0.0
        var tief = 0.0
        for ((_, v) in verlauf) { hoch = max(hoch, v); tief = minOf(tief, v / hoch - 1) }
        val m = wochen.average()
        val sd = if (wochen.size > 2) sqrt(wochen.sumOf { (it - m) * (it - m) } / (wochen.size - 1)) * sqrt(52.0) else null
        val heute = gewichte(kaeufe, jetzt, tage) { s -> kurse[s]?.letzter != null }
        return Strategie(verlauf, vergleich, heute, pa(verlauf), pa(vergleich), sd, tief)
    }

    data class Rang(
        val person: String,
        val name: String,
        val gruppe: Char,
        val kaeufe: Int,
        // Gewichteter Vorsprung der Kaeufe vor dem S&P 500 (0,05 = 5 Prozentpunkte).
        val vorsprung: Double,
        // Anteil der Kaeufe, die besser liefen als der S&P 500.
        val quote: Double,
        val volumen: Double,
    )

    const val MINDEST_KAEUFE = 3

    fun rangliste(handel: List<Handel>, kurse: Map<String, Kurse>, index: Kurse, jetzt: Long, tage: Int = 365): List<Rang> {
        val ab = jetzt - tage * TAG
        return handel.filter { it.kauf && it.datum >= ab }.groupBy { it.person }.mapNotNull { (p, l) ->
            val werte = l.mapNotNull { h ->
                val k = kurse[h.symbol] ?: return@mapNotNull null
                val a = k.am(h.datum) ?: return@mapNotNull null
                val b = k.letzter ?: return@mapNotNull null
                val ia = index.am(h.datum) ?: return@mapNotNull null
                val ib = index.letzter ?: return@mapNotNull null
                if (a <= 0 || ia <= 0) return@mapNotNull null
                Triple(h.betrag, (b / a - 1) - (ib / ia - 1), h)
            }
            if (werte.size < MINDEST_KAEUFE) return@mapNotNull null
            val summe = werte.sumOf { it.first }
            if (summe <= 0) return@mapNotNull null
            Rang(
                p, l.first().name, l.first().gruppe, werte.size,
                werte.sumOf { it.first * it.second } / summe,
                werte.count { it.second > 0 }.toDouble() / werte.size,
                summe,
            )
        }.sortedByDescending { it.vorsprung }
    }
}
