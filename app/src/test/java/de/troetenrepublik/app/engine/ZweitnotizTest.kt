package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.data.Series
import de.troetenrepublik.app.market.Zweitnotiz
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

// Deutsche Zweitnotiz: ausserhalb der Handelszeit des Heimatplatzes zaehlt der juengere deutsche Kurs.
// Beispiel Novo Nordisk, Kopenhagen oeffnet erst um 9 Uhr.
class ZweitnotizTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private fun ts(d: Int, h: Int, min: Int = 0) = LocalDateTime.of(2026, 10, d, h, min).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun onlyForeignStocksNeedASecondListing() {
        assertTrue(Zweitnotiz.braucht("NOVO-B.CO", AssetType.STOCK))
        assertTrue(Zweitnotiz.braucht("AAPL", AssetType.STOCK))
        assertFalse(Zweitnotiz.braucht("SAP.DE", AssetType.STOCK))
        assertFalse(Zweitnotiz.braucht("SAP.F", AssetType.STOCK))
        assertFalse(Zweitnotiz.braucht("BTC-EUR", AssetType.CRYPTO))
        assertFalse(Zweitnotiz.braucht("EUNL.DE", AssetType.ETF))
        assertFalse(Zweitnotiz.braucht("^GDAXI", AssetType.INDEX))
    }

    @Test
    fun searchTermDropsShareClassAndLegalForm() {
        assertEquals("Novo Nordisk", Zweitnotiz.suchbegriff("Novo Nordisk (B)"))
        assertEquals("Alphabet", Zweitnotiz.suchbegriff("Alphabet (A)"))
        assertEquals("Apple", Zweitnotiz.suchbegriff("Apple Inc."))
        assertEquals("Umicore", Zweitnotiz.suchbegriff("Umicore SA"))
    }

    @Test
    fun picksGermanListingWithMatchingPriceAndSkipsDepositaryReceipts() {
        // Umicore: In Frankfurt notiert nur ein Hinterlegungsschein (5,10 EUR), in Muenchen die Aktie
        val umicore = listOf(
            Zweitnotiz.Kandidat("UMI.BR", "BRU", 21.10),
            Zweitnotiz.Kandidat("UMICY", "PNK", 5.87),
            Zweitnotiz.Kandidat("NVJ.F", "FRA", 5.10),
            Zweitnotiz.Kandidat("NVJP.MU", "MUN", 21.30),
        )
        assertEquals("NVJP.MU", Zweitnotiz.waehleNotierung(umicore, 21.10))
        // Novo Nordisk: Frankfurt passt und hat Vorrang vor Xetra (oeffnet erst um 9)
        val novo = listOf(
            Zweitnotiz.Kandidat("NOV.DE", "GER", 33.05),
            Zweitnotiz.Kandidat("NOV.F", "FRA", 33.10),
        )
        assertEquals("NOV.F", Zweitnotiz.waehleNotierung(novo, 33.0))
        // Alphabet: zwei Gattungen in Frankfurt, die naeherliegende gewinnt
        val alphabet = listOf(
            Zweitnotiz.Kandidat("ABEC.F", "FRA", 214.0),
            Zweitnotiz.Kandidat("ABEA.F", "FRA", 212.5),
        )
        assertEquals("ABEA.F", Zweitnotiz.waehleNotierung(alphabet, 212.4))
        assertNull(Zweitnotiz.waehleNotierung(listOf(Zweitnotiz.Kandidat("X.F", "FRA", 50.0)), 100.0))
    }

    private fun kurs(symbol: String, preis: Double, zeit: Long, zustand: String, prev: Double = 0.0, native: Double = preis) =
        Quote(symbol, price = preis, prevClose = prev, marketState = zustand, marketTime = zeit, nativePrice = native, exchange = "Frankfurt")

    @Test
    fun morningPriceComesFromGermanListingWhileHomeIsClosed() {
        // 8:30 Uhr: Kopenhagen zeigt den Schluss von gestern 17 Uhr, Frankfurt den Kurs von 8:29
        val heimat = kurs("NOVO-B.CO", 33.0, ts(6, 17), "CLOSED", prev = 32.5, native = 246.4)
        val zweit = kurs("NOV.F", 33.4, ts(7, 8, 29), "REGULAR")
        val q = Zweitnotiz.waehle(heimat, zweit)
        assertEquals(33.4, q.price, 1e-9)
        // Vortagesschluss am Heimatplatz bleibt die Bezugsgroesse; Handelswaehrung wird umgerechnet
        assertEquals(32.5, q.prevClose, 1e-9)
        assertEquals(33.4 / (33.0 / 246.4), q.nativePrice, 1e-6)
        assertTrue(q.isOpen)
        assertEquals("Frankfurt", q.secondaryExchange)
    }

    @Test
    fun homeExchangeWinsWhileItTrades() {
        val heimat = kurs("NOVO-B.CO", 33.0, ts(7, 10), "REGULAR")
        val zweit = kurs("NOV.F", 33.4, ts(7, 10, 1), "REGULAR")
        assertSame(heimat, Zweitnotiz.waehle(heimat, zweit))
    }

    @Test
    fun staleOrImplausibleGermanPriceIsIgnored() {
        val heimat = kurs("NOVO-B.CO", 33.0, ts(6, 17), "CLOSED")
        // Aelter als der Heimatkurs
        assertSame(heimat, Zweitnotiz.waehle(heimat, kurs("NOV.F", 33.4, ts(6, 16), "CLOSED")))
        // Fehlkurs: mehr als 15 % daneben
        assertSame(heimat, Zweitnotiz.waehle(heimat, kurs("NOV.F", 40.0, ts(7, 8, 29), "REGULAR")))
        assertSame(heimat, Zweitnotiz.waehle(heimat, null))
    }

    private fun reihe(vararg zeiten: Long, start: Long = 0L, ende: Long = 0L) =
        Series(zeiten.map { Candle(it, 1.0, 1.0, 1.0, 1.0) }, start, ende)

    @Test
    fun beforeHomeOpensTheDayChartShowsTheGermanListingAlone() {
        // 8:30 Uhr: Yahoo liefert fuer Kopenhagen noch den Vortag, Frankfurt den heutigen Morgen
        val heimat = reihe(ts(6, 9), ts(6, 12), ts(6, 16, 58))
        val zweit = reihe(ts(7, 8), ts(7, 8, 14), ts(7, 8, 28), start = ts(7, 8), ende = ts(7, 22))
        val m = Zweitnotiz.mische(heimat, zweit, zone)
        assertEquals(zweit.candles, m.candles)
        assertEquals(ts(7, 8), m.dayStart)
    }

    @Test
    fun sameDayGermanPricesFillBeforeOpenAndAfterClose() {
        val heimat = reihe(ts(7, 9), ts(7, 12), ts(7, 16, 58), start = ts(7, 9), ende = ts(7, 17))
        val zweit = reihe(ts(7, 8), ts(7, 8, 30), ts(7, 9, 30), ts(7, 17, 30), ts(7, 21), start = ts(7, 8), ende = ts(7, 22))
        val m = Zweitnotiz.mische(heimat, zweit, zone)
        assertEquals(listOf(ts(7, 8), ts(7, 8, 30), ts(7, 9), ts(7, 12), ts(7, 16, 58), ts(7, 17, 30), ts(7, 21)), m.candles.map { it.t })
        assertEquals(ts(7, 8), m.dayStart)
        assertEquals(ts(7, 22), m.dayEnd)
    }

    @Test
    fun atNightTheAxisIgnoresTheNextDayWindowOfTheHomeExchange() {
        // 0:40 Uhr: Apple mit nachboerslichen Kursen bis 0:38, Yahoo meldet als Fenster aber schon den naechsten Tag
        val heimat = reihe(ts(6, 10), ts(6, 18), ts(7, 0, 38), start = ts(7, 10), ende = ts(8, 2))
        val zweit = reihe(ts(6, 8), ts(6, 9), ts(6, 21, 58), start = ts(6, 8), ende = ts(6, 22))
        val m = Zweitnotiz.mische(heimat, zweit, zone)
        assertEquals(ts(6, 8), m.dayStart)
        // Der letzte Handelstag fuellt die ganze Breite: Achse bis zum letzten Kurs
        assertEquals(ts(7, 0, 38), m.dayEnd)
    }

    @Test
    fun germanListingWithoutTradesTodayChangesNothing() {
        val heimat = reihe(ts(7, 9), ts(7, 12))
        val zweit = reihe(ts(6, 8), ts(6, 21))
        assertSame(heimat, Zweitnotiz.mische(heimat, zweit, zone))
        assertSame(heimat, Zweitnotiz.mische(heimat, Series(emptyList()), zone))
    }
}
