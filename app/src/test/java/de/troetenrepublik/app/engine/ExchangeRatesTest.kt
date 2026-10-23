package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Quote
import org.junit.Assert.*
import org.junit.Test

class ExchangeRatesTest {
    @Test fun missingRateDoesNotRelabelDollarsAsEuro() {
        assertNull(ExchangeRates.toEur(Quote("TEST", price = 110.0, nativeCurrency = "USD"), emptyMap()))
    }
    @Test fun dollarsConvertAllDisplayedPriceFields() {
        val q = Quote("TEST", price = 110.0, prevClose = 99.0, nativeCurrency = "USD", dayHigh = 121.0)
        val eur = ExchangeRates.toEur(q, mapOf("USD" to 1.1))!!
        assertEquals(100.0, eur.price, 1e-9)
        assertEquals(90.0, eur.prevClose, 1e-9)
        assertEquals(110.0, eur.dayHigh, 1e-9)
        assertEquals(110.0, eur.nativePrice, 1e-9)
        assertEquals("USD", eur.nativeCurrency)
        assertEquals("EUR", eur.currency)
    }
    @Test fun londonPenceUsesHundredTimesPoundRate() {
        for (currency in listOf("GBp", "GBX")) {
            val q = Quote("TEST", price = 8500.0, nativeCurrency = currency)
            assertEquals(100.0, ExchangeRates.toEur(q, mapOf("GBP" to 0.85))!!.price, 1e-9)
        }
    }
    @Test fun euroNeedsNoForeignExchangeFeed() {
        assertEquals(100.0, ExchangeRates.toEur(Quote("TEST", price = 100.0), emptyMap())!!.price, 1e-9)
    }
    @Test fun invalidRateNeverProducesInfiniteMoney() {
        for (rate in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertNull(ExchangeRates.rate("USD", mapOf("USD" to rate)))
        }
    }
    @Test fun invalidQuotesAreRejected() {
        for (price in listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.0, -1.0)) {
            assertNull(ExchangeRates.toEur(Quote("TEST", price = price), emptyMap()))
        }
    }
}
