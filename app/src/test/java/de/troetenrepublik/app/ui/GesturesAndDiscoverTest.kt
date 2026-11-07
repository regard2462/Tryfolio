package de.troetenrepublik.app.ui

import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.market.Catalog
import de.troetenrepublik.app.market.Region
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Startseite, Entdecken-Seiten und Long/Short-Verteilung.
class GesturesAndDiscoverTest {

    // ------------------------------------------------------------ Portfolio, Cash, Profil in einer Reihe

    @Test
    fun homePagesFormARowWithoutWrapping() {
        assertEquals(0, HomePage.PORTFOLIO)
        assertEquals(1, HomePage.CASH)
        assertEquals(2, HomePage.PROFILE)
        // Drei Seiten, das Profil ist die letzte: dahinter geht es nicht weiter
        assertEquals(HomePage.PROFILE + 1, HomePage.COUNT)
    }

    @Test
    fun headerStaysBetweenPortfolioAndCashAndLeavesWithCash() {
        val w = 1000
        assertEquals(0, HomePage.headerShiftX(0f, w))
        assertEquals(0, HomePage.headerShiftX(0.6f, w))
        assertEquals(0, HomePage.headerShiftX(1f, w))
        assertEquals(-500, HomePage.headerShiftX(1.5f, w))
        assertEquals(-1000, HomePage.headerShiftX(2f, w))
        // Titelfarben: 0 = Portfolio hervorgehoben, 1 = Cash
        assertEquals(0.3f, HomePage.cashShare(0.3f), 0.001f)
        assertEquals(1f, HomePage.cashShare(1.7f), 0.001f)
    }

    @Test
    fun profileCircleGrowsOnTheWayToTheProfile() {
        // Zwischen Portfolio und Cash bleibt der Kreis klein, von Cash zum Profil folgt er dem Finger
        assertEquals(0f, HomePage.avatarProgress(0f), 0f)
        assertEquals(0f, HomePage.avatarProgress(1f), 0f)
        assertEquals(0.5f, HomePage.avatarProgress(1.5f), 1e-6f)
        assertEquals(1f, HomePage.avatarProgress(2f), 0f)
        val a = HomePage.avatarFrame(0f, fromX = 900f, fromY = 50f, fromSize = 110f, toX = 50f, toY = 40f, toSize = 160f)
        assertEquals(900f, a.x, 1e-4f)
        assertEquals(50f, a.y, 1e-4f)
        assertEquals(110f / 160f, a.scale, 1e-6f)
        val m = HomePage.avatarFrame(0.5f, 900f, 50f, 110f, 50f, 40f, 160f)
        assertEquals(475f, m.x, 1e-4f)
        assertEquals(45f, m.y, 1e-4f)
        assertEquals(135f / 160f, m.scale, 1e-6f)
        val b = HomePage.avatarFrame(1f, 900f, 50f, 110f, 50f, 40f, 160f)
        assertEquals(50f, b.x, 1e-4f)
        assertEquals(40f, b.y, 1e-4f)
        assertEquals(1f, b.scale, 1e-6f)
        assertTrue(HomePage.AVATAR_LARGE > HomePage.AVATAR_SMALL)
    }

    // ------------------------------------------------------------ Long/Short

    private val kette = """{"optionChain":{"result":[{"underlyingSymbol":"NVDA","expirationDates":[1790985600,1791590400],
        "options":[{"expirationDate":1790985600,
        "calls":[{"strike":180,"volume":7000,"lastTradeDate":1790712000},{"strike":190,"volume":500,"lastTradeDate":1790700000},
                 {"strike":250,"volume":900,"lastTradeDate":1790200000},{"strike":260,"lastTradeDate":1790712000}],
        "puts":[{"strike":170,"volume":2500,"lastTradeDate":1790711000},{"strike":150,"volume":0,"lastTradeDate":1790711000}]}]}],"error":null}}"""

    @Test
    fun optionChainCountsOnlyTheLastTradingDay() {
        val api = de.troetenrepublik.app.market.MarketApi()
        val (daten, handel) = api.parseOptionChain(kette)!!
        assertEquals(listOf(1790985600L, 1791590400L), daten)
        // Kontrakte ohne Umsatz fallen weg
        assertEquals(4, handel.size)
        val v = api.sumOptionVolumes(listOf(handel))
        // Der Call mit altem letzten Handel (Umsatz eines frueheren Tages) zaehlt nicht mit
        assertEquals(7500L, v.calls)
        assertEquals(2500L, v.puts)
        assertEquals(75.0, v.longPct, 0.001)
    }

    @Test
    fun longShortOnlyForUsSymbols() {
        assertTrue(de.troetenrepublik.app.market.hasUsOptions("NVDA"))
        assertTrue(de.troetenrepublik.app.market.hasUsOptions("SPY"))
        assertFalse(de.troetenrepublik.app.market.hasUsOptions("SAP.DE"))
        assertFalse(de.troetenrepublik.app.market.hasUsOptions("^GDAXI"))
        assertFalse(de.troetenrepublik.app.market.hasUsOptions("BTC-EUR"))
    }

    // ------------------------------------------------------------ Entdecken

    private fun quote(symbol: String, price: Double, prev: Double, volume: Double) =
        Quote(symbol = symbol, price = price, prevClose = prev, volume = volume)

    private val stocks = listOf(
        Catalog.Stock("AAPL", "Apple", "US", "COMPUTER", listOf("SP500", "NDX")),
        Catalog.Stock("GOOGL", "Alphabet (A)", "US", "SOFTWARE", listOf("SP500")),
        Catalog.Stock("GOOG", "Alphabet (C)", "US", "SOFTWARE", listOf("SP500")),
        Catalog.Stock("SAP.DE", "SAP", "DE", "SOFTWARE", listOf("DAX")),
        Catalog.Stock("RHM.DE", "Rheinmetall", "DE", "LUFTFAHRT", listOf("DAX")),
        Catalog.Stock("TINY", "Klein", "US", "HANDEL"),
    )

    @Test
    fun filtersCombineWithAndBetweenAndOrWithin() {
        val items = stocks.map { DiscoverItem(it.s, it.n, industry = it.i, country = it.c, region = Region.ofCountry(it.c), indices = it.x) }
        val f = DiscoverFilters(industries = setOf("SOFTWARE", "LUFTFAHRT"), countries = setOf("DE"))
        assertEquals(listOf("SAP.DE", "RHM.DE"), items.filter { f.matches(it) }.map { it.symbol })
        val idx = DiscoverFilters(indices = setOf("NDX"))
        assertEquals(listOf("AAPL"), items.filter { idx.matches(it) }.map { it.symbol })
        assertFalse(DiscoverFilters().active)
        assertTrue(f.active)
    }

    @Test
    fun moversNeedRealTurnover() {
        val q = mapOf(
            "AAPL" to quote("AAPL", 200.0, 190.0, 1e7),
            "TINY" to quote("TINY", 2.0, 1.0, 100.0),
        )
        assertEquals(listOf("AAPL"), movers(q, stocks).map { it.first.s })
    }

    @Test
    fun popularFollowsTurnoverAndKeepsTwoGermanValues() {
        // Zu wenige Kurse: feste Auswahl
        assertTrue(popularStocks(emptyMap(), stocks).isNotEmpty())
        val many = (1..30).map { Catalog.Stock("S$it", "Wert $it", "US", "HANDEL") }
        val all = stocks + many
        val q = HashMap<String, Quote>()
        many.forEachIndexed { i, s -> q[s.s] = quote(s.s, 10.0, 10.0, 1_000.0 + i) }
        q["GOOGL"] = quote("GOOGL", 100.0, 100.0, 9e6)
        q["GOOG"] = quote("GOOG", 100.0, 100.0, 8e6)
        q["SAP.DE"] = quote("SAP.DE", 200.0, 200.0, 1e5)
        q["RHM.DE"] = quote("RHM.DE", 1500.0, 1500.0, 2e4)
        val top = popularStocks(q, all).map { it.first }
        assertEquals("GOOGL", top.first())
        assertFalse("Alphabet nur einmal", "GOOG" in top)
        assertTrue("SAP.DE" in top && "RHM.DE" in top)
        assertEquals(8, top.size)
    }

    @Test
    fun catalogMapsAssetClassesAndRegions() {
        assertEquals(AssetType.COMMODITY, Catalog.typeOf("ROHSTOFF"))
        assertEquals(AssetType.CURRENCY, Catalog.typeOf("WAEHRUNG"))
        assertFalse(AssetType.COMMODITY.directlyTradable)
        assertTrue(AssetType.ETF.directlyTradable)
        assertEquals("DEUTSCHLAND", Region.ofCountry("DE"))
        assertEquals("NORDAMERIKA", Region.ofCountry("US"))
        assertEquals("EUROPA", Region.ofCountry("FR"))
        assertEquals("🇩🇪", flagOf("DE"))
    }
}
