package de.troetenrepublik.app.engine

import kotlin.math.abs

// Kernaussagen eingeklappter Listen auf der Wertpapierseite (0.77): ab wann sich Aufklappen lohnt, Richtung und
// Netto einer Meldungsliste, groesster Anteil und die staerkste Veraenderung der Trends.
object ListenKern {
    // Mehr als drei Zeilen: ab hier lohnt das Aufklappen, darunter steht die Liste gleich da.
    const val LOHNT_AB = 3

    fun lohnt(zeilen: Int): Boolean = zeilen > LOHNT_AB

    // Kauf minus Verkauf; ohne eine der beiden Summen gibt es kein Netto.
    fun netto(kauf: Double?, verkauf: Double?): Double? = if (kauf == null || verkauf == null) null else kauf - verkauf

    // 1 steigend, -1 fallend, 0 gleich oder unbekannt; Rundungsreste zaehlen als gleich.
    fun richtung(wert: Double?): Int = when {
        wert == null || !wert.isFinite() || abs(wert) < 0.005 -> 0
        wert > 0 -> 1
        else -> -1
    }

    fun groesster(anteile: List<Double>): Double? = anteile.filter { it.isFinite() }.maxOrNull()

    // Veraenderung in Prozent; ohne Vergleichswert (null oder nicht positiv) keine Angabe.
    fun prozent(aktuell: Double, vorher: Double?): Double? =
        if (vorher == null || vorher <= 0.0) null else (aktuell / vorher - 1) * 100

    // Eine Quelle der Trends: Einheit (0 Erwaehnungen, 1 Aufrufe), Veraenderung zur Vorwoche in Prozent und Gewicht
    // (Zahl dahinter), damit die Kernaussage von der Quelle mit den meisten Zahlen kommt.
    class TrendQuelle(val einheit: Int, val prozent: Double?, val gewicht: Double)

    class Trend(val prozent: Double?, val einheit: Int, val quellen: Int)

    // Kernaussage der Trends: Veraenderung der gewichtigsten Quelle mit Vergleichswert, sonst nur die Zahl der Quellen.
    fun trend(quellen: List<TrendQuelle>): Trend? {
        if (quellen.isEmpty()) return null
        val q = quellen.filter { it.prozent != null && it.prozent.isFinite() }.maxByOrNull { it.gewicht }
        return Trend(q?.prozent, q?.einheit ?: 0, quellen.size)
    }
}
