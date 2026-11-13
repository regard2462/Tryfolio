package de.troetenrepublik.app.ui

import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Branchentrends
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

// Aenderung in Prozent ueber tage fuer viele Werte aus Schlusskursen (Paket q78f): je 20 Werte eine Abfrage, drei
// gleichzeitig, 30 Minuten zwischengespeichert. Fuer Themenkoerbe und die Mover ueber laengere Zeitraeume.
internal object ZeitraumKurse {
    private val cache = ConcurrentHashMap<String, Pair<Long, Map<String, Double>>>()

    suspend fun aenderungen(state: AppState, symbole: List<String>, tage: Int): Map<String, Double> {
        if (symbole.isEmpty()) return emptyMap()
        val liste = symbole.distinct()
        val schluessel = "$tage|" + liste.sorted().joinToString(",").hashCode()
        val jetzt = System.currentTimeMillis()
        cache[schluessel]?.takeIf { jetzt - it.first < 30 * 60_000L }?.let { return it.second }
        // Bis zu einem Jahr tageweise, darueber wochenweise: Spark liefert fuer zehn Jahre sonst sehr lange Reihen
        val (bereich, intervall) = when {
            tage <= 7 -> "1mo" to "1d"
            tage <= 30 -> "3mo" to "1d"
            tage <= 91 -> "6mo" to "1d"
            tage <= 182 -> "1y" to "1d"
            tage <= 365 -> "2y" to "1wk"
            else -> "10y" to "1wk"
        }
        val aus = HashMap<String, Double>()
        for (gruppe in liste.chunked(20).chunked(3)) {
            val teile = coroutineScope { gruppe.map { c -> async { state.schlusskurse(c, bereich, intervall) } }.awaitAll() }
            for (t in teile) for ((s, k) in t) Branchentrends.veraenderung(k, tage)?.let { aus[s] = it }
        }
        if (aus.isNotEmpty()) cache[schluessel] = jetzt to aus
        return aus
    }
}
