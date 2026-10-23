package de.troetenrepublik.app.market.broker

import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.data.Range
import de.troetenrepublik.app.data.Series
import de.troetenrepublik.app.market.MarketApi

// Kurse, Charts und Suche von einem verbundenen Broker statt von Yahoo. Kurse und Kerzen kommen in der
// Handelswaehrung (Quote.nativeCurrency); in Euro rechnet wie bei Yahoo MarketRepository um.
interface Kursquelle {
    // Der Broker fuehrt den Wert (App-Symbol, etwa AAPL oder BTC-USD).
    fun fuehrt(symbol: String): Boolean

    // Aktuelle Kurse je App-Symbol; was fehlt, liefert der Broker gerade nicht.
    suspend fun kurse(symbole: List<String>): Map<String, Quote>

    // Kursverlauf in der Handelswaehrung; null, wenn der Broker keinen liefert.
    suspend fun kerzen(symbol: String, range: Range): Series?

    // Handelbare Werte beim Broker.
    suspend fun suche(text: String): List<MarketApi.SearchHit>
}
