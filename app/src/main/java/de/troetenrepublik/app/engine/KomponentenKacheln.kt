package de.troetenrepublik.app.engine

// Komponenten eines Index oder ETFs fuer Liste und Heatmap (0.79). Reine Rechnung: welche Kacheln die Heatmap bekommt
// und wie gross die Restkachel ist.
object Komponenten {
    // anteil: Anteil am Index (Summe 1) oder am Fonds; aenderung: Tagesaenderung in Prozent, null ohne Kurs.
    data class Komponente(val symbol: String, val name: String, val anteil: Double, val aenderung: Double?)

    // Eine Kachel der Heatmap. rest: die graue Sammelkachel der uebrigen Positionen (ohne Symbol, ohne Aenderung).
    data class Kachel(val symbol: String?, val name: String, val gewicht: Double, val aenderung: Double?, val rest: Boolean = false)

    // So viele Komponenten bekommen eine eigene Kachel; kleinere Werte gehen in die Restkachel
    const val KACHELN_HOECHSTENS = 60

    // Eine Restkachel gibt es ab diesem Anteil (0,5 %)
    const val REST_AB = 0.005

    // Nach Gewicht absteigend, ohne Werte ohne Gewicht
    fun sortiert(liste: List<Komponente>): List<Komponente> =
        liste.filter { it.anteil.isFinite() && it.anteil > 0 }.sortedByDescending { it.anteil }

    // Anteil des Fonds, der in den genannten Positionen fehlt: nur bekannt, wenn die Summe ein Teil des Ganzen ist
    // (nicht schon voll und nicht ueber 100 %, etwa bei doppelt gelisteten Positionen).
    fun restAnteil(summe: Double): Double =
        if (!summe.isFinite() || summe <= 0 || summe >= EtfStreuung.VOLLSTAENDIG_AB) 0.0 else 1.0 - summe

    // Kacheln der Heatmap: die groessten hoechstens Werte, dazu bei ETFs der unbekannte Rest und bei beiden die
    // abgeschnittenen kleinen Werte als eine Restkachel mit ihrem Gewicht. Die Restkachel steht am Ende.
    fun kacheln(liste: List<Komponente>, etf: Boolean, uebrigeName: String, hoechstens: Int = KACHELN_HOECHSTENS): List<Kachel> {
        val alle = sortiert(liste)
        if (alle.isEmpty()) return emptyList()
        val gezeigt = alle.take(hoechstens)
        val abgeschnitten = alle.drop(hoechstens).sumOf { it.anteil }
        val unbekannt = if (etf) restAnteil(alle.sumOf { it.anteil }) else 0.0
        val rest = abgeschnitten + unbekannt
        val kacheln = gezeigt.map { Kachel(it.symbol, it.name, it.anteil, it.aenderung) }
        return if (rest >= REST_AB) kacheln + Kachel(null, uebrigeName, rest, null, rest = true) else kacheln
    }
}
