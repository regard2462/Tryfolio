package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable

// Gespeicherte Staende der Reddit-Zaehlung. Ein Stand von vor etwa 24 Stunden zaehlt genau die 24 Stunden, die den
// Vergleich bilden, auch wenn die Schnittstelle bei einem lauten Subreddit nicht zwei Tage zurueckreicht.
object RedditVorstand {

    // zeit: Zeitpunkt der Zaehlung in Millisekunden; zahlen: Erwaehnungen in den 24 Stunden davor je Kuerzel.
    @Serializable
    data class Stand(val zeit: Long, val zahlen: Map<String, Int>)

    @Serializable
    data class Vorstand(val staende: List<Stand> = emptyList())

    private const val STUNDE = 3_600_000L

    // Hoechstens alle zwei Stunden ein neuer Stand, drei Tage aufbewahrt.
    private const val ABSTAND = 2 * STUNDE
    private const val BEHALTEN = 72 * STUNDE

    // Ein Stand dient als Vergleich, wenn er zwischen 21 und 28 Stunden alt ist; der naechste an 24 Stunden gewinnt.
    private const val FRUEHESTENS = 21 * STUNDE
    private const val SPAETESTENS = 28 * STUNDE
    private const val ZIEL = 24 * STUNDE

    fun merken(v: Vorstand, jetzt: Long, zahlen: Map<String, Int>): Vorstand {
        val behalten = v.staende.filter { jetzt - it.zeit in 0 until BEHALTEN }
        if (behalten.any { jetzt - it.zeit < ABSTAND }) return Vorstand(behalten)
        return Vorstand(behalten + Stand(jetzt, zahlen))
    }

    // Zahlen des Stands von vor etwa 24 Stunden; null, wenn keiner so alt ist.
    fun vorher(v: Vorstand, jetzt: Long): Map<String, Int>? =
        v.staende.filter { jetzt - it.zeit in FRUEHESTENS..SPAETESTENS }.minByOrNull { kotlin.math.abs(jetzt - it.zeit - ZIEL) }?.zahlen
}
