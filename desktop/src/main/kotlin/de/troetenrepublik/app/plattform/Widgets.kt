package de.troetenrepublik.app.plattform

import de.troetenrepublik.app.data.WidgetDaten

// Widgets auf dem Startbildschirm gibt es nur unter Android; hier ohne Wirkung.
object Widgets {
    val verfuegbar: Boolean = false

    fun aktualisieren(daten: WidgetDaten) {}

    fun vorhanden(): Boolean = false

    fun einzelSymbole(): List<String> = emptyList()

    const val ART_DEPOT = "depot"
    const val ART_EINZEL = "einzelwert"
    const val ART_DIVIDENDEN = "dividenden"
    const val ART_MAERKTE = "maerkte"
    const val ART_STIMMUNG = "stimmung"
    const val ART_NEWS = "news"

    fun arten(): Set<String> = emptySet()

    fun listenSymbole(): List<String> = emptyList()

    fun depotZeitraeume(): Set<String> = emptySet()
}
