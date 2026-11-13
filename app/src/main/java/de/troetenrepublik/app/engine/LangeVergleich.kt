package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.LangeReihen.Ergebnis
import de.troetenrepublik.app.engine.LangeReihen.Haltedauer
import kotlin.math.ln

// Hilfen fuer den Vergleich mehrerer Mischungen im Werkzeug „Lange Sicht“: Auswahl, gemeinsamer Zeitraum, Haltedauern,
// logarithmische Skala und Ausweichen der Linienbeschriftungen. Reine Rechnung ohne Oberflaeche.
object LangeVergleich {

    // So viele Mischungen lassen sich gleichzeitig vergleichen
    const val MAX = 3

    // Vorgabe beim ersten Oeffnen: reine Aktien gegen 60/40, soweit vorhanden
    val VORGABE = listOf("A100", "A60")

    fun standard(gueltig: List<String>): List<String> = VORGABE.filter { it in gueltig }.ifEmpty { gueltig.take(1) }

    // Gemerkte Auswahl (Namen, mit Komma getrennt): nur gueltige, ohne Doppelte, hoechstens MAX; leer ergibt die Vorgabe.
    fun lesen(text: String, gueltig: List<String>): List<String> {
        val l = text.split(',').map { it.trim() }.filter { it in gueltig }.distinct().takeLast(MAX)
        return l.ifEmpty { standard(gueltig) }
    }

    fun schreiben(auswahl: List<String>): String = auswahl.joinToString(",")

    // Tipp auf eine Mischung: gewaehlte wird abgewaehlt (die letzte bleibt), neue kommt dazu; bei MAX Mischungen ersetzt
    // sie die aelteste Wahl. Die Reihenfolge ist die Reihenfolge der Wahl.
    fun umschalten(aktuell: List<String>, name: String): List<String> = when {
        name in aktuell -> if (aktuell.size > 1) aktuell - name else aktuell
        aktuell.size >= MAX -> aktuell.drop(aktuell.size - MAX + 1) + name
        else -> aktuell + name
    }

    // Alle Ergebnisse auf den gemeinsamen Zeitraum zugeschnitten (spaetester Start bis frueheste Ende), damit alle Linien
    // am selben Punkt mit 1 beginnen. null ohne gemeinsame Jahre. Eine einzelne Mischung bleibt unveraendert.
    fun zuschneiden(es: List<Ergebnis>): List<Ergebnis>? {
        if (es.isEmpty()) return null
        if (es.size == 1) return es
        val von = es.maxOf { it.von }
        val bis = es.minOf { it.bis }
        if (von > bis) return null
        return es.map { LangeReihen.zuschnitt(it, von, bis) ?: return null }
    }

    // Haltedauern, die jede Mischung hat (in der Reihenfolge von HALTEDAUERN)
    fun gemeinsameDauern(es: List<Ergebnis>): List<Int> =
        LangeReihen.HALTEDAUERN.filter { d -> es.all { e -> e.haltedauern.any { it.jahre == d } } }

    fun halte(e: Ergebnis, dauer: Int): Haltedauer? = e.haltedauern.firstOrNull { it.jahre == dauer }

    // Gewuenschte Dauer, sonst die mittlere der vorhandenen
    fun waehleDauer(verfuegbar: List<Int>, gewuenscht: Int): Int? =
        if (gewuenscht in verfuegbar) gewuenscht else verfuegbar.getOrNull(verfuegbar.size / 2)

    // Logarithmische Skala ueber alle Verlaeufe: n(v) liegt zwischen 0 (tiefster Wert) und 1 (hoechster)
    class LogSkala(val tief: Double, val spanne: Double) {
        fun n(v: Double): Double = (ln(v) - tief) / spanne
    }

    fun logSkala(verlaeufe: List<DoubleArray>): LogSkala? {
        val alle = verlaeufe.flatMap { v -> v.filter { it > 0.0 }.map { ln(it) } }
        if (alle.isEmpty()) return null
        val tief = alle.min()
        val spanne = alle.max() - tief
        return if (spanne > 0.0) LogSkala(tief, spanne) else null
    }

    // Senkrechte Lage (Anteil der Hoehe, 0 oben) eines normierten Werts mit Rand oben und unten
    fun yBruch(n: Double, rand: Float): Float = rand + (1f - 2f * rand) * (1f - n.toFloat())

    // Obere Kante je Beschriftung, sodass sich keine ueberlappen und alle innerhalb 0 bis `gesamt` bleiben. mitten: gewuenschte
    // Mitte, hoehen: Hoehe je Beschriftung. Die Reihenfolge der Ausgabe entspricht der Eingabe; die Rangfolge der Mitten bleibt.
    fun ausweichen(mitten: List<Float>, hoehen: List<Float>, gesamt: Float, luecke: Float = 2f): List<Float> {
        val n = mitten.size
        val ord = (0 until n).sortedBy { mitten[it] }
        val oben = FloatArray(n)
        var grenze = 0f
        for (i in ord) {
            oben[i] = maxOf(mitten[i] - hoehen[i] / 2f, grenze)
            grenze = oben[i] + hoehen[i] + luecke
        }
        var unten = gesamt
        for (i in ord.asReversed()) {
            oben[i] = minOf(oben[i], unten - hoehen[i])
            unten = oben[i] - luecke
        }
        return oben.map { maxOf(0f, it) }
    }
}
