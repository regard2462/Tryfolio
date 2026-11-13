package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.PlanInterval

// Vorgaben des Sparplan-Assistenten: Tag und Uhrzeit sind vorbelegt und lassen sich in der Uebersicht aendern.
object SparplanVorgabe {
    // Ausfuehrung am 2. des Monats, ohne feste Uhrzeit (sobald ein Kurs da ist)
    const val TAG = 2
    const val UHRZEIT = -1

    // Waehlbare Ausfuehrungstage je Intervall; woechentlich und zweiwoechentlich haben keinen waehlbaren Tag.
    fun tage(interval: PlanInterval): List<Int> = when (interval) {
        PlanInterval.MONTHLY -> listOf(2, 9, 16, 23)
        PlanInterval.QUARTERLY -> listOf(2, 16)
        PlanInterval.WEEKLY, PlanInterval.BIWEEKLY -> emptyList()
    }

    // Tag nach einem Wechsel des Intervalls: bleibt, wenn er dort waehlbar ist, sonst die Vorgabe.
    fun tagFuer(interval: PlanInterval, tag: Int): Int = if (tag in tage(interval)) tag else TAG
}
