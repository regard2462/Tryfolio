package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 0.76 (q76h): Long/Short-Verteilung aus Tagesberichten der Eurex, der TAIFEX und der JPX. Feste Beispieldaten
// im Aufbau der echten Dateien (gekuerzt).
class Fassung076Q76hTest {

    // ---------------------------------------------------------------- Eurex

    private val produktSeite = """<meta name="twitter:title" content="Nestl" />
<meta name="twitter:description" content="Trade Nestl (NESN) on Eurex. Find key contract specifications, trading hours, and pricing details for ISIN CH0038863350." />"""

    @Test fun eurexProduktCodeAusDerBeschreibung() {
        assertEquals("NESN", EurexOptionen.produktCode(produktSeite, "CH0038863350"))
        assertEquals("NESN", EurexOptionen.produktCode(produktSeite, "ch0038863350"))
    }

    @Test fun eurexProduktCodeNurFuerDieEigeneIsin() {
        assertNull(EurexOptionen.produktCode(produktSeite, "DE0007236101"))
        assertNull(EurexOptionen.produktCode("<html>Error 404</html>", "CH0038863350"))
    }

    @Test fun eurexProduktUrlOhneSonderzeichen() {
        assertEquals("https://www.eurex.com/ex-en/markets/productSearch/product!CH0038863350", EurexOptionen.produktUrl("CH0038863350 /?"))
    }

    @Test fun eurexBerichtUrlNimmtDenJuengsten() {
        val seite = """<a href="https://www.eurex.com/resource/blob/5694732/ac2fa9a3dee6d460af0d0c113be25827/data/sr20261106.txt">06</a>
            <a href="https://www.eurex.com/resource/blob/5740720/2eb04eec7c760433e728c90ca44f7852/data/sr20261110.txt">10</a>
            <a href="https://www.eurex.com/resource/blob/5714444/70c6c6e70146cf0ee97b87e9711bc91c/data/sr20261109.txt">09</a>"""
        assertEquals("https://www.eurex.com/resource/blob/5740720/2eb04eec7c760433e728c90ca44f7852/data/sr20261110.txt", EurexOptionen.berichtUrl(seite))
        assertNull(EurexOptionen.berichtUrl("<html></html>"))
    }

    private val bericht = """        Trading Quantities at 10-Nov-2026 16:09:18.18   Calls:    868171, Puts:   1092932

     Trading and Position Summary per Product (Options)

Product  Number of         Trade Volume           Open Interest          Number of Trades
 Name     Series         Call         Put        Call         Put        Call         Put
-------------------------------------------------------------------------------------------
1CRC         1044           0           0           0           0           0           0
NESN          977        1707        2003      448853      697811         131         108
SAP          1221        1925        2816      219386      291712          96         149
SIE           985        2490        4413      113025      255786         103         159
ZZZ            12           3           4          50          60           1           2
-----------------------------------------------------------------------------------------------
-----------------------------   SUM OF OPTIONS PRODUCTS   -------------------------------------
-----------------------------------------------------------------------------------------------
                                 868171     1092932    1000000     2000000
     Trading and Position Summary per Product (Futures)

Product  Number of   Trade Volume   Open Interest    Number of Trades
---------------------------------------------------------------------------
FDAX          800       10000        0
"""

    @Test fun eurexBerichtLiestDieProdukte() {
        val k = EurexOptionen.lesen(bericht)
        assertEquals(5, k.size)
        assertEquals(OptionsZahlen(1707, 2003, 448853, 697811), k["NESN"])
        assertEquals(OptionsZahlen(2490, 4413, 113025, 255786), k["SIE"])
        assertNull(k["FDAX"])
    }

    @Test fun eurexBerichtAbgeschnittenLiestDenAnfang() {
        val k = EurexOptionen.lesen(bericht.substring(0, bericht.indexOf("SAP") + 40))
        assertEquals(OptionsZahlen(1707, 2003, 448853, 697811), k["NESN"])
    }

    @Test fun eurexKeinBerichtLiefertNichts() {
        assertTrue(EurexOptionen.lesen("<html>Error</html>").isEmpty())
        assertTrue(EurexOptionen.lesen("").isEmpty())
    }

    // ---------------------------------------------------------------- Auswahl Umsatz oder offene Kontrakte

    @Test fun umsatzGenuegt() {
        val w = OptionsZahlen(1707, 2003, 10, 10).wahl()
        assertEquals((1707L to 2003L) to false, w)
    }

    @Test fun zuWenigUmsatzNimmtOffeneKontrakte() {
        val w = OptionsZahlen(3, 4, 50, 60).wahl()
        assertEquals((50L to 60L) to true, w)
    }

    @Test fun beidesZuKleinGibtNichts() {
        assertNull(OptionsZahlen(3, 4, 40, 50).wahl())
        assertNull(OptionsZahlen(0, 0, 0, 0).wahl())
    }

    // ---------------------------------------------------------------- TAIFEX

    @Test fun taifexCodeAusDemKuerzel() {
        assertEquals("2317", TaifexOptionen.code("2317.TW"))
        assertEquals("006205", TaifexOptionen.code("006205.TW"))
        assertEquals("6488", TaifexOptionen.code("6488.TWO"))
        assertNull(TaifexOptionen.code("7974.T"))
        assertNull(TaifexOptionen.code("AAPL"))
    }

    @Test fun taifexVertraegeAusDerListe() {
        val liste = """[{"Contract":"CDO","UnderlyingStock":"TSMC","StockCode":"2330","StockName":"x","Type":"y"},
            {"Contract":"DHO","UnderlyingStock":"Hon Hai","StockCode":"2317","StockName":"x","Type":"y"}]"""
        assertEquals(mapOf("2330" to "CDO", "2317" to "DHO"), TaifexOptionen.vertraege(liste))
        assertTrue(TaifexOptionen.vertraege("<html>").isEmpty())
    }

    private fun taifexZeile(vertrag: String, art: String, umsatz: String, offen: String, sitzung: String) =
        "{\"Date\":\"20261109\",\"Contract\":\"$vertrag\",\"ContractMonth(Week)\":\"202611\",\"StrikePrice\":\"100\",\"CallPut\":\"$art\"," +
            "\"Open\":\"-\",\"High\":\"-\",\"Low\":\"-\",\"Close\":\"-\",\"Volume\":\"$umsatz\",\"SettlementPrice\":\"1\",\"OpenInterest\":\"$offen\"," +
            "\"BestBid\":\"-\",\"BestAsk\":\"-\",\"HistoricalHigh\":\"-\",\"HistoricalLow\":\"-\",\"TradingHalt\":\"\",\"TradingSession\":\"$sitzung\"}"

    private val call = "買權"
    private val put = "賣權"
    private val regulaer = "一般"
    private val nacht = "盤後"

    @Test fun taifexBerichtSummiertJeVertrag() {
        val b = "[" + listOf(
            taifexZeile("DHO", call, "10", "100", regulaer),
            taifexZeile("DHO", call, "3", "20", regulaer),
            taifexZeile("DHO", put, "6", "40", regulaer),
            taifexZeile("DHO", put, "2", "99", nacht),
            taifexZeile("CDO", call, "46", "5", regulaer),
            taifexZeile("TXO", call, "1000", "1000", regulaer),
        ).joinToString(",\n") + "]"
        val k = TaifexOptionen.lesen(b, setOf("DHO", "CDO"))
        assertEquals(OptionsZahlen(13, 8, 120, 40), k["DHO"])
        assertEquals(OptionsZahlen(46, 0, 5, 0), k["CDO"])
        assertNull(k["TXO"])
    }

    @Test fun taifexBerichtAbgeschnittenZaehltVollstaendigeZeilen() {
        val b = "[" + taifexZeile("DHO", call, "10", "100", regulaer) + ",\n" + taifexZeile("DHO", put, "6", "40", regulaer).dropLast(30)
        val k = TaifexOptionen.lesen(b, setOf("DHO"))
        assertEquals(OptionsZahlen(10, 0, 100, 0), k["DHO"])
    }

    // ---------------------------------------------------------------- JPX

    @Test fun jpxCodeAusDemKuerzel() {
        assertEquals("7974", JpxOptionen.code("7974.T"))
        assertEquals("285A", JpxOptionen.code("285A.T"))
        assertNull(JpxOptionen.code("2317.TW"))
        assertNull(JpxOptionen.code("7974"))
    }

    @Test fun jpxDateiPfadAusDemVerzeichnis() {
        val v = """{"UpdateDate":"2026/11/10 17:11","TableDatas":[
            {"TradingOverview":"Securities Options Trading Data per each Underlying","TradeDate":"20261110","File":"/automation/markets/derivatives/trading-volume/files/20261110_trade_summary_per_underlying.csv"},
            {"TradingOverview":"Securities Options Quotations","TradeDate":"20261110","File":"/automation/markets/derivatives/trading-volume/files/20261110_individual_options_quotes.csv"}]}"""
        assertEquals("/automation/markets/derivatives/trading-volume/files/20261110_individual_options_quotes.csv", JpxOptionen.dateiPfad(v))
        assertNull(JpxOptionen.dateiPfad("{}"))
    }

    private val jpxKopf = "instrument code,instrument,open price,high price,low price,close price,auction volume,auction value,j-net volume,j-net value,total volume,total value,count turnover,trade date"

    @Test fun jpxDateiSummiertCallsUndPuts() {
        val csv = listOf(
            jpxKopf,
            "324147974,7974 OOP C2612-9250,85.0,85.0,85.0,85.0,1,8500,0,0,1,8500,1,20261110",
            "327177974,7974 OOP C2703-8500,800.0,800.0,800.0,800.0,1,80000,0,0,1,80000,1,20261110",
            "224057974,7974 OOP P2612-7000,49.0,49.0,49.0,49.0,1,4900,0,0,12,4900,1,20261110",
            "323061321,1321 OOP C2611-630,7.0,7.0,7.0,7.0,30,2100,0,0,30,2100,2,20261110",
        ).joinToString("\r\n")
        val k = JpxOptionen.lesen(csv)
        assertEquals(OptionsZahlen(2, 12, 0, 0), k["7974"])
        assertEquals(OptionsZahlen(30, 0, 0, 0), k["1321"])
        assertEquals((2L to 12L) to false, k["7974"]?.wahl())
    }

    @Test fun jpxDateiOhneKopfLiefertNichts() {
        assertTrue(JpxOptionen.lesen("").isEmpty())
        assertTrue(JpxOptionen.lesen("<html>Error</html>").isEmpty())
    }
}
