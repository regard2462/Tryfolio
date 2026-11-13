package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.data.Series
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// Tageschart: der Kurs oben kommt alle paar Sekunden, die Minutenkerzen nur mit dem Abruf der Reihe. Der frische Kurs
// fuehrt die letzte Kerze fort oder setzt eine neue, damit der letzte Punkt der Linie zum Kurs oben passt.
object LivePunkt {
    // Abstand zur letzten Kerze, ab dem kein Kurs mehr angehaengt wird (wie die Luecke im Tageschart).
    const val ALT_AB_MS = 20 * 60_000L

    fun anhaengen(serie: Series, quote: Quote?, jetzt: Long): List<Candle> {
        val kerzen = serie.candles
        val letzte = kerzen.lastOrNull() ?: return kerzen
        val q = quote ?: return kerzen
        val preis = q.price.takeIf { it.isFinite() && it > 0 } ?: return kerzen
        val t = min(q.marketTime.takeIf { it > 0 } ?: q.updatedAt, jetzt)
        if (t <= letzte.t) return kerzen
        // Nur im Handelsfenster des Tags; ohne Fenster hoechstens eine Stunde nach der letzten Kerze
        val fenster = serie.dayStart > 0 && serie.dayEnd > serie.dayStart
        if (fenster && (t < serie.dayStart || t > serie.dayEnd)) return kerzen
        if (!fenster && t - letzte.t > 3_600_000L) return kerzen
        // Liegt die letzte Kerze weit zurueck, ist die Reihe ein gemerkter Stand von vorher (der Abruf laeuft noch): Ein
        // angehaengter Kurs zoege einen flachen Strich mit Sprung ueber Kurse, die es gab, aber noch nicht geladen sind (0.77).
        if (t - letzte.t > ALT_AB_MS) return kerzen
        // Fehlkurse auslassen
        if (letzte.close > 0 && abs(preis / letzte.close - 1) > 0.2) return kerzen
        val schritt = if (kerzen.size >= 2) letzte.t - kerzen[kerzen.size - 2].t else 0L
        return if (schritt > 0 && t - letzte.t < schritt) {
            kerzen.dropLast(1) + letzte.copy(high = max(letzte.high, preis), low = min(letzte.low, preis), close = preis)
        } else kerzen + Candle(t, preis, preis, preis, preis)
    }
}
