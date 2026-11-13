package de.troetenrepublik.app.engine

import kotlin.math.pow

// [0.78] Pruefung des 16-Wochen-Rhythmus fuer "Wirkt das?": nur in den Wochen 13 bis 15 eines fortlaufenden Zyklus aus
// 16 Wochen im Index investiert, gegen Kaufen und Halten.
// Anker: Die Woche ab Montag, 19.10.1987 (Kurssturz) gilt in der Zaehlung des Verfahrens als Woche 16; Woche 1 beginnt
// damit am Montag, 26.10.1987. Eine Woche laeuft von Montag bis Sonntag, ihre Aenderung ist der letzte Schluss der Woche
// gegen den letzten Schluss der Vorwoche. Kosten und Zinsen in der Wartezeit bleiben unberuecksichtigt.
object Rhythmus16 {
    const val LAENGE = 16
    val ANKER: Long = java.time.LocalDate.of(1987, 10, 26).toEpochDay()
    val INVESTIERT = 13..15
    // Mindestens so viele volle Wochen fuer eine Aussage (rund zehn Jahre)
    const val MINDEST_WOCHEN = 520

    // Fortlaufende Nummer der Woche seit dem Anker (auch negativ) und ihre Stelle im Zyklus (1 bis 16).
    fun wochenNummer(tag: Long): Long = Math.floorDiv(tag - ANKER, 7L)
    fun woche(tag: Long): Int = Math.floorMod(wochenNummer(tag), LAENGE.toLong()).toInt() + 1

    // strategie, halten: Faktor ueber den ganzen Zeitraum; jahr: Rendite im Jahr als Anteil. anteilZeit: Anteil der
    // Wochen im Markt. imPlus: Anteil der investierten Wochen mit Gewinn. jeWoche: Faktor je Stelle des Zyklus (16 Werte).
    data class Ergebnis(
        val von: Long, val bis: Long, val wochen: Int, val strategie: Double, val halten: Double,
        val strategieJahr: Double, val haltenJahr: Double, val anteilZeit: Double, val imPlus: Double, val jeWoche: List<Double>,
    ) {
        val beste: Int get() = jeWoche.indices.maxBy { jeWoche[it] } + 1
        val schwaechste: Int get() = jeWoche.indices.minBy { jeWoche[it] } + 1
    }

    // Montag, an dem die Wochen 13 bis 15 beginnen: der laufende Abschnitt, wenn heute darin liegt, sonst der naechste.
    fun beginnGuteWochen(heute: Long): Long {
        val n = wochenNummer(heute)
        val stelle = woche(heute)
        val versatz = if (stelle in INVESTIERT) -(stelle - INVESTIERT.first) else Math.floorMod(INVESTIERT.first - stelle, LAENGE)
        return ANKER + (n + versatz) * 7
    }

    // Rang der Wochen 13 bis 15 unter allen 16 Fenstern aus drei aufeinanderfolgenden Wochen des Zyklus (1 = staerkstes
    // Fenster). Waere der Rhythmus Zufall, laege der Rang irgendwo zwischen 1 und 16.
    fun rang(jeWoche: List<Double>): Int {
        if (jeWoche.size != LAENGE) return LAENGE
        fun fenster(start: Int) = (0 until INVESTIERT.count()).fold(1.0) { a, k -> a * jeWoche[(start - 1 + k) % LAENGE] }
        val eigen = fenster(INVESTIERT.first)
        return 1 + (1..LAENGE).count { fenster(it) > eigen }
    }

    // Bis zu diesem Rang gilt ein Fenster als eines der staerksten (oberes Viertel)
    const val VORN = 4

    enum class Urteil { BELEGT, NICHT_BELEGT }

    // rangFrueh, rangSpaet: Rang in der ersten und zweiten Haelfte der Kursreihe, null bei zu kurzer Haelfte. BELEGT nur,
    // wenn die Wochen 13 bis 15 im ganzen Zeitraum und in beiden Haelften unter den staerksten Fenstern lagen: Ein
    // Muster, das nur in einer Haelfte auftritt, ist kein Rhythmus.
    class Pruefung(val gesamt: Ergebnis, val rang: Int, val rangFrueh: Int?, val rangSpaet: Int?, val urteil: Urteil)

    fun pruefen(kurse: List<Pair<Long, Double>>, heute: Long): Pruefung? {
        val g = auswerten(kurse, heute) ?: return null
        val gut = kurse.filter { it.second > 0 && it.second.isFinite() }
        val mitte = gut.size / 2
        val frueh = if (mitte > 0) auswerten(gut.subList(0, mitte), gut[mitte].first)?.let { rang(it.jeWoche) } else null
        val spaet = if (mitte > 0) auswerten(gut.subList(mitte, gut.size), heute)?.let { rang(it.jeWoche) } else null
        val r = rang(g.jeWoche)
        val belegt = r <= VORN && frueh != null && spaet != null && frueh <= VORN && spaet <= VORN
        return Pruefung(g, r, frueh, spaet, if (belegt) Urteil.BELEGT else Urteil.NICHT_BELEGT)
    }

    // kurse: (Tag seit 1970, Schlusskurs), aufsteigend. heute: die laufende Woche zaehlt nicht mit.
    fun auswerten(kurse: List<Pair<Long, Double>>, heute: Long): Ergebnis? {
        val laufend = wochenNummer(heute)
        // Letzter Schluss je Woche
        val schluss = LinkedHashMap<Long, Pair<Long, Double>>()
        for (k in kurse) if (k.second > 0 && k.second.isFinite() && wochenNummer(k.first) < laufend) schluss[wochenNummer(k.first)] = k
        val w = schluss.entries.sortedBy { it.key }
        if (w.size < MINDEST_WOCHEN + 1) return null
        val jeWoche = DoubleArray(LAENGE) { 1.0 }
        var strategie = 1.0
        var investiert = 0
        var plus = 0
        for (i in 1 until w.size) {
            // Fehlt eine Woche (kein Handel), gehoert die Aenderung zur Woche, in der wieder gehandelt wurde
            val f = w[i].value.second / w[i - 1].value.second
            val stelle = Math.floorMod(w[i].key, LAENGE.toLong()).toInt() + 1
            jeWoche[stelle - 1] *= f
            if (stelle in INVESTIERT) {
                strategie *= f
                investiert++
                if (f > 1) plus++
            }
        }
        val von = w.first().value.first
        val bis = w.last().value.first
        val jahre = (bis - von) / 365.25
        if (jahre <= 0 || investiert == 0) return null
        val halten = w.last().value.second / w.first().value.second
        return Ergebnis(
            von, bis, w.size - 1, strategie, halten, strategie.pow(1 / jahre) - 1, halten.pow(1 / jahre) - 1,
            investiert.toDouble() / (w.size - 1), plus.toDouble() / investiert, jeWoche.toList(),
        )
    }
}
