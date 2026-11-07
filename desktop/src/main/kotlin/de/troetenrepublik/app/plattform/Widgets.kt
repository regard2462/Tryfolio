package de.troetenrepublik.app.plattform

import de.troetenrepublik.app.data.WidgetDaten

// Widgets auf dem Startbildschirm gibt es nur unter Android; hier ohne Wirkung.
object Widgets {
    val verfuegbar: Boolean = false

    fun aktualisieren(daten: WidgetDaten) {}

    fun vorhanden(): Boolean = false

    fun einzelSymbole(): List<String> = emptyList()

    fun einzelAnheften(symbol: String): Boolean = false

    const val ART_DEPOT = "depot"
    const val ART_EINZEL = "einzelwert"
    const val ART_DIVIDENDEN = "dividenden"
    const val ART_MAERKTE = "maerkte"
    const val ART_STIMMUNG = "stimmung"
    const val ART_NEWS = "news"
    const val ART_HEATMAP = "heatmap"
    const val ART_GLOBUS = "globus"

    fun arten(): Set<String> = emptySet()

    fun listenSymbole(): List<String> = emptyList()

    fun newsArten(): Set<String> = emptySet()

    fun barometerGewaehlt(schluessel: String): Boolean = false

    fun depotZeitraeume(): Set<String> = emptySet()

    fun heatmapAuswahl(): List<Pair<String, Int>> = emptyList()

    fun globusModi(): Set<String> = emptySet()
}
