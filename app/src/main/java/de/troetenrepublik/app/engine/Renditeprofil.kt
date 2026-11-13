package de.troetenrepublik.app.engine

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.pow

// Zusatzkennzahlen zum Depotverlauf: die groessten Rueckgaenge mit Beginn, Tief und Erholung, Monatsrenditen gegen einen
// Vergleichsindex (Aufwaerts- und Abwaertsanteil), Omega und Gewinn zu Schmerz. Reihen sind Zeitpunkt (ms) und Stand
// (Index oder Kurs, groesser null), aufsteigend.
object Renditeprofil {

    private const val TAG = 86_400_000L

    // Ein Rueckgang: letzter Hoechststand (beginn), tiefster Punkt (tief) und der erste Punkt, der den alten Hoechststand
    // wieder erreicht (erholung, null solange er darunter liegt). tiefe als positiver Anteil.
    data class Phase(val beginn: Long, val tief: Long, val erholung: Long?, val tiefe: Double) {
        val tageBisTief: Long get() = (tief - beginn) / TAG
        val tageBisErholung: Long? get() = erholung?.let { (it - tief) / TAG }
        // Dauer vom Hoechststand bis zur Erholung, sonst bis zum Ende der Reihe (ende)
        fun tageGesamt(ende: Long): Long = ((erholung ?: ende) - beginn) / TAG
    }

    // Alle Rueckgaenge ab der Schwelle mindest (Anteil), tiefste zuerst, hoechstens anzahl.
    fun rueckgaenge(index: List<Pair<Long, Double>>, anzahl: Int = 5, mindest: Double = 0.01): List<Phase> {
        val p = index.filter { it.second.isFinite() && it.second > 0 }
        if (p.size < 2) return emptyList()
        val aus = ArrayList<Phase>()
        var hochZeit = p.first().first
        var hoch = p.first().second
        var tiefZeit = hochZeit
        var tief = hoch
        fun schliessen(erholung: Long?) {
            val tiefe = 1 - tief / hoch
            if (tiefe >= mindest) aus += Phase(hochZeit, tiefZeit, erholung, tiefe)
        }
        for (i in 1 until p.size) {
            val (t, v) = p[i]
            if (v >= hoch) {
                if (tief < hoch) schliessen(t)
                hochZeit = t; hoch = v; tiefZeit = t; tief = v
            } else if (v < tief) {
                tiefZeit = t; tief = v
            }
        }
        if (tief < hoch) schliessen(null)
        return aus.sortedByDescending { it.tiefe }.take(anzahl)
    }

    // Renditen je Kalendermonat aus der Reihe und im selben Abschnitt aus dem Vergleich (letzter Stand bis zum selben
    // Zeitpunkt). Der erste und der letzte Monat sind so lang wie die Reihe reicht. Abschnitte ohne Vergleichskurs fehlen.
    fun monatsPaare(index: List<Pair<Long, Double>>, vergleich: List<Pair<Long, Double>>, zone: ZoneId = ZoneId.systemDefault()): List<Pair<Double, Double>> {
        val p = index.filter { it.second.isFinite() && it.second > 0 }.sortedBy { it.first }
        val v = vergleich.filter { it.second.isFinite() && it.second > 0 }.sortedBy { it.first }
        if (p.size < 2 || v.isEmpty()) return emptyList()
        fun monat(t: Long): Int = Instant.ofEpochMilli(t).atZone(zone).let { it.year * 12 + it.monthValue }
        // Grenzen: erster Punkt, letzter Punkt jedes Monats
        val grenzen = ArrayList<Pair<Long, Double>>()
        grenzen += p.first()
        for (i in 1 until p.size) {
            if (monat(p[i].first) != monat(p[i - 1].first) && p[i - 1].first > grenzen.last().first) grenzen += p[i - 1]
        }
        if (p.last().first > grenzen.last().first) grenzen += p.last()
        val zeiten = LongArray(v.size) { v[it].first }
        fun stand(t: Long): Double? {
            var lo = 0
            var hi = zeiten.size - 1
            var pos = -1
            while (lo <= hi) {
                val m = (lo + hi) ushr 1
                if (zeiten[m] <= t) { pos = m; lo = m + 1 } else hi = m - 1
            }
            return if (pos >= 0) v[pos].second else null
        }
        val aus = ArrayList<Pair<Double, Double>>()
        for (i in 1 until grenzen.size) {
            val a = stand(grenzen[i - 1].first) ?: continue
            val b = stand(grenzen[i].first) ?: continue
            aus += (grenzen[i].second / grenzen[i - 1].second - 1) to (b / a - 1)
        }
        return aus
    }

    // Anteil an den Bewegungen des Vergleichs: mittlere Monatsrendite (geometrisch) in dessen steigenden Monaten geteilt
    // durch seine eigene, ebenso fuer fallende Monate. 1 heisst gleich stark; null bei weniger als mindest Monaten.
    data class Anteile(val aufwaerts: Double?, val abwaerts: Double?, val monateAuf: Int, val monateAb: Int)

    fun anteile(paare: List<Pair<Double, Double>>, mindest: Int = 3): Anteile {
        fun geo(r: List<Double>): Double = r.fold(1.0) { a, x -> a * (1 + x) }.pow(1.0 / r.size) - 1
        fun teil(l: List<Pair<Double, Double>>): Double? {
            if (l.size < mindest) return null
            val nenner = geo(l.map { it.second })
            return if (abs(nenner) < 1e-9) null else geo(l.map { it.first }) / nenner
        }
        val auf = paare.filter { it.second > 0 }
        val ab = paare.filter { it.second < 0 }
        return Anteile(teil(auf), teil(ab), auf.size, ab.size)
    }

    // Omega: Summe der Renditen ueber der Schwelle geteilt durch die Summe der Fehlbetraege darunter. null ohne Fehlbetrag
    // oder bei weniger als mindest Renditen.
    fun omega(renditen: List<Double>, schwelle: Double = 0.0, mindest: Int = 6): Double? {
        if (renditen.size < mindest) return null
        val gewinn = renditen.sumOf { maxOf(it - schwelle, 0.0) }
        val verlust = renditen.sumOf { maxOf(schwelle - it, 0.0) }
        return if (verlust < 1e-12) null else gewinn / verlust
    }

    // Gewinn zu Schmerz: Summe aller Monatsrenditen geteilt durch den Betrag der Summe der Verlustmonate.
    fun gewinnZuSchmerz(renditen: List<Double>, mindest: Int = 6): Double? {
        if (renditen.size < mindest) return null
        val schmerz = -renditen.filter { it < 0 }.sum()
        return if (schmerz < 1e-12) null else renditen.sum() / schmerz
    }
}
