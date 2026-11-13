package de.troetenrepublik.app.market

import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.data.Series
import de.troetenrepublik.app.engine.Splits

// [0.73] Geordnete Liste von Quellen fuer dieselben Werte: Jede Quelle wiederholt ihre Abrufe selbst (MoexQuelle: drei
// Versuche); liefert sie danach nichts, kommt die naechste an die Reihe. Eine leere Antwort gilt als Ausfall. Der zuletzt
// gueltige Stand bleibt in der Quelle selbst erhalten (Quote.marketTime nennt seine Zeit), die Kette zeichnet nur auf, welche
// Quelle zuletzt geantwortet hat. Neue Quellen fuer dasselbe Praefix kommen einfach hinter die vorhandenen.
class KursQuellenKette(private val quellen: List<KursQuelle>) : KursQuelle {
    init {
        require(quellen.isNotEmpty()) { "Eine Kette braucht mindestens eine Quelle" }
        require(quellen.map { it.praefix }.distinct().size == 1) { "Alle Quellen einer Kette tragen dasselbe Praefix" }
    }

    override val praefix: String = quellen.first().praefix
    override val waehrung: String = quellen.first().waehrung

    // Index der Quelle, die zuletzt geantwortet hat (0 = erste)
    @Volatile var zuletztAntwortete: Int = 0
        private set

    private suspend fun <T> erste(leer: (T) -> Boolean, abruf: suspend (KursQuelle) -> T): T? {
        quellen.forEachIndexed { i, q ->
            // Ein Abbruch (Seite verlassen) fragt die naechste Quelle nicht mehr
            val r = try { abruf(q) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null } ?: return@forEachIndexed
            if (!leer(r)) { zuletztAntwortete = i; return r }
        }
        return null
    }

    override suspend fun kurse(kennungen: List<String>): Map<String, Quote> =
        erste<Map<String, Quote>>({ it.isEmpty() }) { it.kurse(kennungen) }.orEmpty()

    override suspend fun kerzen(kennung: String, range: String, interval: String): Series =
        erste<Series>({ it.candles.isEmpty() }) { it.kerzen(kennung, range, interval) } ?: Series(emptyList())

    override suspend fun kerzenZwischen(kennung: String, vonMs: Long, bisMs: Long, interval: String): Series =
        erste<Series>({ it.candles.isEmpty() }) { it.kerzenZwischen(kennung, vonMs, bisMs, interval) } ?: Series(emptyList())

    override suspend fun dividenden(kennung: String): List<MarketApi.DividendEvent> =
        erste<List<MarketApi.DividendEvent>>({ it.isEmpty() }) { it.dividenden(kennung) }.orEmpty()

    override suspend fun splits(kennung: String): List<Splits.Split> =
        erste<List<Splits.Split>>({ it.isEmpty() }) { it.splits(kennung) }.orEmpty()

    // Treffer aller Quellen, doppelte Kuerzel einmal
    override suspend fun suche(text: String): List<MarketApi.SearchHit> =
        quellen.flatMap { q -> runCatching { q.suche(text) }.getOrDefault(emptyList()) }.distinctBy { it.symbol }

    override val antwortetNicht: Boolean get() = quellen.all { it.antwortetNicht }

    override suspend fun wechselkurse(): Map<String, Double> =
        erste<Map<String, Double>>({ it.isEmpty() }) { it.wechselkurse() }.orEmpty()
}
