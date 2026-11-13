package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable
import kotlin.math.pow
import kotlin.random.Random

// Zufallsdepots als Vergleich im Rueckblick des Aktienfinders (0.78): Zu jedem Stichtag werden aus denselben Werten
// (Universum) gleich grosse Depots zufaellig gezogen, gleichgewichtet und wie die Auswahl des Finders jeden Monat neu
// besetzt. Aus den verketteten Monaten entsteht je Zufallsdepot eine Rendite pro Jahr; die Verteilung zeigt, wie viel
// der Auswahl ueber das Universum hinaus zuzuschreiben ist. Fester Startwert: gleiche Eingaben, gleiches Ergebnis.
// Ohne Kosten, verglichen wird mit der Auswahl vor Kosten.
object Zufallsdepots {

    const val ANZAHL = 1_000
    const val STARTWERT = 78L
    // Stufen der gespeicherten Verteilung: 0 %, 5 %, ... 100 %
    const val STUFEN = 20

    // auswahl: Rendite pro Jahr der Auswahl im selben Zeitraum; besserAls: Anteil der Zufallsdepots mit weniger Rendite
    // (0 bis 1); quantile: Rendite pro Jahr an den Stufen, aufsteigend.
    @Serializable
    data class Verteilung(
        val anzahl: Int, val groesse: Int, val startwert: Long, val monate: Int, val auswahl: Double, val besserAls: Double,
        val quantile: List<Double>,
    ) {
        val median: Double get() = quantile[quantile.size / 2]
        fun quantil(anteil: Double): Double = quantile[Math.round(anteil * (quantile.size - 1)).toInt().coerceIn(0, quantile.size - 1)]
    }

    // Rendite pro Jahr je Zufallsdepot. renditen: je Monat die Monatsrenditen aller Werte des Universums. Monate mit
    // weniger Werten als groesse nehmen alle Werte.
    fun jahresrenditen(renditen: List<List<Double>>, groesse: Int, anzahl: Int = ANZAHL, startwert: Long = STARTWERT): DoubleArray {
        if (renditen.isEmpty() || groesse <= 0 || anzahl <= 0 || renditen.any { it.isEmpty() }) return DoubleArray(0)
        val zufall = Random(startwert)
        val felder = renditen.map { it.toDoubleArray() }
        val aus = DoubleArray(anzahl)
        for (d in 0 until anzahl) {
            var wert = 1.0
            for (feld in felder) {
                val n = minOf(groesse, feld.size)
                // Ziehen ohne Zuruecklegen: die ersten n Plaetze mit zufaelligen Werten aus dem Rest tauschen
                var summe = 0.0
                for (i in 0 until n) {
                    val j = i + zufall.nextInt(feld.size - i)
                    val x = feld[j]; feld[j] = feld[i]; feld[i] = x
                    summe += x
                }
                wert *= 1 + summe / n
            }
            aus[d] = if (wert > 0) wert.pow(12.0 / felder.size) - 1 else -1.0
        }
        return aus
    }

    // Verteilung der Zufallsdepots gegen die Auswahl. auswahlMonate: Monatsrenditen der Auswahl in denselben Monaten.
    // null bei weniger als zwoelf Monaten oder ungleicher Laenge.
    fun verteilung(renditen: List<List<Double>>, auswahlMonate: List<Double>, groesse: Int, anzahl: Int = ANZAHL, startwert: Long = STARTWERT): Verteilung? {
        if (renditen.size < 12 || renditen.size != auswahlMonate.size) return null
        val r = jahresrenditen(renditen, groesse, anzahl, startwert)
        if (r.isEmpty()) return null
        r.sort()
        val auswahl = auswahlMonate.fold(1.0) { a, x -> a * (1 + x) }.pow(12.0 / auswahlMonate.size) - 1
        val quantile = (0..STUFEN).map { s -> r[Math.round(s.toDouble() / STUFEN * (r.size - 1)).toInt()] }
        return Verteilung(anzahl, groesse, startwert, renditen.size, auswahl, r.count { it < auswahl }.toDouble() / r.size, quantile)
    }
}
