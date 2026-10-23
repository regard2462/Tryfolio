package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Quote

// Einen nicht umgerechneten Preis in fremder Waehrung nie als EUR ausgeben.
object ExchangeRates {
    fun rate(currency: String, rates: Map<String, Double>): Double? {
        val value = when (currency) {
            "EUR" -> 1.0
            "GBp", "GBX" -> rates["GBP"]?.times(100)
            // US-Cent: Yahoo notiert einige Agrar-Futures in USX
            "USd", "USX" -> rates["USD"]?.times(100)
            else -> rates[currency]
        }
        return value?.takeIf { it.isFinite() && it > 0 }
    }

    fun toEur(q: Quote, rates: Map<String, Double>): Quote? {
        val rate = rate(q.nativeCurrency, rates) ?: return null
        if (!q.price.isFinite() || q.price <= 0) return null
        return q.copy(
            price = q.price / rate, prevClose = q.prevClose / rate,
            open = q.open / rate, dayHigh = q.dayHigh / rate, dayLow = q.dayLow / rate,
            weekHigh52 = q.weekHigh52 / rate, weekLow52 = q.weekLow52 / rate,
            marketCap = q.marketCap / rate, currency = "EUR", nativePrice = q.price,
        )
    }
}
