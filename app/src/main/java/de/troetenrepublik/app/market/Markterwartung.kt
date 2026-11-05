package de.troetenrepublik.app.market

// „Was der Markt erwartet“ (seit 5.92): zu festen Themen das passende Ereignis bei Polymarket, gesucht ueber die
// offene Suche (siehe Prognose). Die Kurse sind die Wahrscheinlichkeiten, die die Wettenden einem Ausgang geben.
object Markterwartung {

    // NAECHSTES: das Ereignis, das als erstes endet (etwa die naechste Zinssitzung); VOLUMEN: das meistgehandelte.
    enum class Wahl { NAECHSTES, VOLUMEN }

    data class Thema(val schluessel: String, val suche: String, val muster: Regex, val wahl: Wahl)

    private fun r(s: String) = Regex(s, RegexOption.IGNORE_CASE)

    // Reihenfolge der Anzeige.
    val THEMEN = listOf(
        Thema("fed", "fed decision", r("^Fed decision in"), Wahl.NAECHSTES),
        Thema("fed_jahr", "how many fed rate cuts", r("^How many Fed rate cuts in"), Wahl.NAECHSTES),
        Thema("ezb", "ECB interest rates", r("^ECB interest rates"), Wahl.NAECHSTES),
        Thema("rezession", "US recession", r("^US recession by end of"), Wahl.NAECHSTES),
        Thema("inflation", "how high will inflation get", r("^How high will inflation get"), Wahl.NAECHSTES),
        Thema("oel", "WTI crude oil hit", r("^What will WTI crude oil.*hit in"), Wahl.NAECHSTES),
        Thema("hormus", "Strait of Hormuz", r("Hormuz"), Wahl.VOLUMEN),
        Thema("iran", "Iran", r("Iran"), Wahl.VOLUMEN),
        Thema("wahlen", "midterms", r("(House|Senate|Congress|midterm)"), Wahl.VOLUMEN),
    )

    // Das Ereignis zum Thema: Titel passt, noch offen, mit Maerkten.
    fun waehlen(t: Thema, liste: List<Prognose.Ereignis>, jetzt: Long): Prognose.Ereignis? {
        val passend = liste.filter { t.muster.containsMatchIn(it.titel) && it.maerkte.isNotEmpty() && (it.ende <= 0 || it.ende > jetzt) }
        return when (t.wahl) {
            Wahl.NAECHSTES -> passend.filter { it.ende > 0 }.minByOrNull { it.ende } ?: passend.maxByOrNull { it.volumen }
            Wahl.VOLUMEN -> passend.maxByOrNull { it.volumen }
        }
    }

    // Die aussagekraeftigsten Ausgaenge mit Wahrscheinlichkeit (0 bis 1). Schliessen sich die Ausgaenge aus (Summe
    // nahe 1, etwa „keine Aenderung“ oder „Senkung um 0,25“), die wahrscheinlichsten. Sonst sind es Schwellen wie
    // „ueber 90 Dollar“, und am meisten sagen die nahe 50 %, sortiert nach Titel.
    fun ausgaenge(e: Prognose.Ereignis, n: Int = 3): List<Pair<String, Double>> {
        val m = e.maerkte.filter { it.offen && it.chance.isFinite() }
        if (m.isEmpty()) return emptyList()
        // Ja/Nein: die Frage steht schon darueber, als Ausgang die erste Seite (meist „Yes“)
        if (m.size == 1) return listOf((m[0].seiten.firstOrNull()?.name ?: m[0].titel) to m[0].chance)
        val summe = m.sumOf { it.chance }
        val wahl = if (summe in 0.8..1.25) m.sortedByDescending { it.chance }.filter { it.chance >= 0.03 }.take(n)
        else m.sortedBy { kotlin.math.abs(it.chance - 0.5) }.take(n).sortedBy { it.titel }
        return wahl.map { it.titel to it.chance }
    }
}
