package de.troetenrepublik.app.engine

import kotlin.math.abs

// Rechnen fuer die Ebenen des Globus ohne Oberflaeche: Rangliste und Laenderanteile des Depots.
object GlobusEbenen {
    class Rang(val code: String, val wert: Double)

    // Punkt auf dem Globus (Ebene Engpaesse): Kennung, Ort in Grad, Farbe als ARGB
    class Punkt(val id: String, val lon: Double, val lat: Double, val farbe: Int)

    // Die n hoechsten und die n niedrigsten Werte; bei weniger als 2n Werten teilen sich beide Listen die Werte ohne Doppelte.
    fun rangliste(werte: List<Rang>, n: Int = 5): Pair<List<Rang>, List<Rang>> {
        val sortiert = werte.filter { it.wert.isFinite() }.sortedByDescending { it.wert }
        val oben = sortiert.take(n)
        val unten = sortiert.drop(oben.size).takeLast(n).reversed()
        return oben to unten
    }

    // Anteile des Depotwerts je Land (alpha-2, Summe aller Positionen = 1). unzuordenbar: Anteil ohne Land (ETFs, Krypto).
    class Anteile(val nachLand: Map<String, Double>, val unzuordenbar: Double, val summe: Double) {
        val leer: Boolean get() = summe <= 0.0
        // Land mit dem groessten Anteil
        val groesstes: Map.Entry<String, Double>? get() = nachLand.entries.maxByOrNull { it.value }
    }

    // Ein Land ab diesem Anteil gilt als Klumpen.
    const val KLUMPEN = 0.5

    fun anteile(posten: List<Pair<String, Double>>, land: (String) -> String?): Anteile {
        val summe = posten.sumOf { abs(it.second) }
        if (summe <= 0.0) return Anteile(emptyMap(), 0.0, 0.0)
        val je = HashMap<String, Double>()
        var ohne = 0.0
        for ((symbol, wert) in posten) {
            val w = abs(wert)
            val l = land(symbol)?.takeIf { it.isNotBlank() }
            if (l == null) ohne += w else je[l] = (je[l] ?: 0.0) + w
        }
        return Anteile(je.mapValues { it.value / summe }, ohne / summe, summe)
    }
}
