package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.engine.FlexAbgleich.Art
import de.troetenrepublik.app.i18n.FlexTexte
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Flex-Bericht: erfundene Ausschnitte nach der Beschreibung des Dienstes, ohne echte Konten, Namen oder Papiere.
class Fassung078FlexTest {

    private val jetzt = BrokerExport.zeit("2025-03-01")
    private val kuerzel = mapOf("DE0000000001" to "BSP.DE", "US0000000002" to "USB", "IE0000000003" to "FND.DE")

    private fun bericht(inhalt: String, basis: String = "EUR") = """
        <FlexQueryResponse queryName="Abgleich" type="AF">
        <FlexStatements count="1">
        <FlexStatement accountId="X0000000" fromDate="20250101" toDate="20250131" period="LastMonth" whenGenerated="20250201;061500">
        <AccountInformation accountId="X0000000" currency="$basis" />
        $inhalt
        </FlexStatement>
        </FlexStatements>
        </FlexQueryResponse>
    """.trimIndent()

    private val positionen = """
        <OpenPositions>
        <OpenPosition accountId="X0000000" currency="EUR" fxRateToBase="1" assetCategory="STK" symbol="BSP" description="BEISPIEL AG" conid="11" isin="DE0000000001" multiplier="1" position="10" markPrice="55" costBasisPrice="50.1" costBasisMoney="501" levelOfDetail="SUMMARY" />
        <OpenPosition accountId="X0000000" currency="USD" fxRateToBase="0.9" assetCategory="STK" symbol="USB" description="US BEISPIEL &amp; CO" conid="12" isin="US0000000002" multiplier="1" position="4" markPrice="110" costBasisPrice="100" costBasisMoney="400" levelOfDetail="SUMMARY" />
        <OpenPosition accountId="X0000000" currency="USD" fxRateToBase="0.9" assetCategory="OPT" symbol="USB   250620C00120000" description="USB 20JUN25 120 C" conid="13" multiplier="100" strike="120" expiry="20250620" putCall="C" underlyingSymbol="USB" position="-1" markPrice="2.5" costBasisPrice="3" costBasisMoney="-300" levelOfDetail="SUMMARY" />
        <OpenPosition accountId="X0000000" currency="EUR" fxRateToBase="1" assetCategory="STK" symbol="OHN" description="OHNE KENNUNG SE" conid="14" isin="" multiplier="1" position="7" markPrice="9" costBasisPrice="8" costBasisMoney="56" levelOfDetail="SUMMARY" />
        </OpenPositions>
    """.trimIndent()

    private val geschaefte = """
        <Trades>
        <Trade accountId="X0000000" currency="EUR" fxRateToBase="1" assetCategory="STK" symbol="BSP" description="BEISPIEL AG" conid="11" isin="DE0000000001" multiplier="1" tradeID="9001" dateTime="20250110;093001" quantity="10" tradePrice="50" proceeds="-500" ibCommission="-1" ibCommissionCurrency="EUR" taxes="0" buySell="BUY" levelOfDetail="EXECUTION" />
        <Trade accountId="X0000000" currency="USD" fxRateToBase="0.9" assetCategory="STK" symbol="USB" description="US BEISPIEL &amp; CO" conid="12" isin="US0000000002" multiplier="1" tradeID="9002" dateTime="20250115;153000" quantity="4" tradePrice="100" proceeds="-400" ibCommission="-1" ibCommissionCurrency="USD" taxes="0" buySell="BUY" levelOfDetail="EXECUTION" />
        <Trade accountId="X0000000" currency="USD" fxRateToBase="0.9" assetCategory="STK" symbol="USB" description="US BEISPIEL &amp; CO" conid="12" isin="US0000000002" multiplier="1" tradeID="9002" dateTime="20250115;153000" quantity="4" tradePrice="100" proceeds="-400" buySell="BUY" levelOfDetail="CLOSED_LOT" />
        <Trade accountId="X0000000" currency="USD" fxRateToBase="0.9" assetCategory="OPT" symbol="USB   250620C00120000" description="USB 20JUN25 120 C" conid="13" multiplier="100" strike="120" expiry="20250620" putCall="C" underlyingSymbol="USB" tradeID="9003" dateTime="20250116;160000" quantity="-1" tradePrice="3" proceeds="300" ibCommission="-1" buySell="SELL" levelOfDetail="EXECUTION" />
        <Trade accountId="X0000000" currency="USD" fxRateToBase="0.9" assetCategory="CASH" symbol="EUR.USD" description="EUR.USD" conid="15" tradeID="9004" dateTime="20250114;100000" quantity="-500" tradePrice="1.11" proceeds="555" ibCommission="-2" buySell="SELL" levelOfDetail="EXECUTION" />
        </Trades>
    """.trimIndent()

    private val geld = """
        <CashTransactions>
        <CashTransaction accountId="X0000000" currency="EUR" fxRateToBase="1" description="CASH RECEIPTS / ELECTRONIC FUND TRANSFERS" dateTime="20250105" amount="2000" type="Deposits/Withdrawals" transactionID="7001" levelOfDetail="DETAIL" />
        <CashTransaction accountId="X0000000" currency="USD" fxRateToBase="0.9" assetCategory="STK" symbol="USB" description="USB CASH DIVIDEND USD 0.50 PER SHARE" conid="12" isin="US0000000002" dateTime="20250120;202000" amount="2" type="Dividends" transactionID="7002" levelOfDetail="DETAIL" />
        <CashTransaction accountId="X0000000" currency="USD" fxRateToBase="0.9" assetCategory="STK" symbol="USB" description="USB CASH DIVIDEND - US TAX" conid="12" isin="US0000000002" dateTime="20250120;202000" amount="-0.3" type="Withholding Tax" transactionID="7003" levelOfDetail="DETAIL" />
        <CashTransaction accountId="X0000000" currency="EUR" fxRateToBase="1" description="EUR CREDIT INT FOR JAN-2025" dateTime="20250131" amount="1.5" type="Broker Interest Received" transactionID="7004" levelOfDetail="DETAIL" />
        <CashTransaction accountId="X0000000" currency="USD" fxRateToBase="0.9" description="USB CASH DIVIDEND" dateTime="20250120" amount="2" type="Dividends" levelOfDetail="SUMMARY" />
        </CashTransactions>
        <CashReport>
        <CashReportCurrency accountId="X0000000" currency="BASE_SUMMARY" startingCash="0" endingCash="1141.03" />
        <CashReportCurrency accountId="X0000000" currency="EUR" startingCash="0" endingCash="1000.5" />
        </CashReport>
    """.trimIndent()

    @Test
    fun antwortenDesDienstes() {
        val ok = FlexBericht.antwort("<FlexStatementResponse timestamp=\"01 February, 2025 06:15 AM EST\"><Status>Success</Status><ReferenceCode>1234567890</ReferenceCode><Url>https://gdcdyn.interactivebrokers.com/AccountManagement/FlexWebService/GetStatement</Url></FlexStatementResponse>")!!
        assertTrue(ok.ok)
        assertEquals("1234567890", ok.referenz)
        val fehler = FlexBericht.antwort("<FlexStatementResponse timestamp=\"x\">\n<Status>Fail</Status>\n<ErrorCode>1012</ErrorCode>\n<ErrorMessage>Token has expired.</ErrorMessage>\n</FlexStatementResponse>")!!
        assertFalse(fehler.ok)
        assertEquals(1012, fehler.fehler)
        assertEquals(FlexBericht.Fehlerart.TOKEN_ABGELAUFEN, FlexBericht.fehlerart(1012))
        assertEquals(FlexBericht.Fehlerart.TOKEN_UNGUELTIG, FlexBericht.fehlerart(1015))
        assertEquals(FlexBericht.Fehlerart.ABFRAGE, FlexBericht.fehlerart(1014))
        assertEquals(FlexBericht.Fehlerart.ADRESSE_GESPERRT, FlexBericht.fehlerart(1013))
        assertEquals(FlexBericht.Fehlerart.ZU_VIELE, FlexBericht.fehlerart(1018))
        listOf(1001, 1004, 1005, 1006, 1007, 1008, 1009, 1019, 1021).forEach { assertEquals(FlexBericht.Fehlerart.WARTEN, FlexBericht.fehlerart(it)) }
        assertEquals(FlexBericht.Fehlerart.SONST, FlexBericht.fehlerart(1020))
        assertNull(FlexBericht.antwort(bericht("")))
    }

    @Test
    fun liestAbschnitte() {
        val b = FlexBericht.lesen(bericht(positionen + geschaefte + geld))!!
        assertEquals("EUR", b.basis)
        assertEquals(BrokerExport.zeit("2025-01-01"), b.von)
        assertEquals(BrokerExport.zeit("2025-01-31"), b.bis)
        assertEquals(BrokerExport.zeit("2025-02-01", "06:15:00"), b.erzeugt)
        val p = b.positionen!!
        assertEquals(4, p.size)
        assertEquals("US BEISPIEL & CO", p[1].papier.name)
        assertEquals(4.0, p[1].stueck, 1e-9)
        assertEquals(0.9, b.inEuro("USD", p[1].fx)!!, 1e-9)
        val option = p[2].papier
        assertTrue(option.option)
        assertEquals(100.0, option.multiplikator, 1e-9)
        assertEquals(120.0, option.basispreis!!, 1e-9)
        assertEquals("C", option.putCall)
        assertEquals("USB", option.basiswert)
        assertEquals(BrokerExport.zeit("2025-06-20"), option.verfall)
        assertEquals(-1.0, p[2].stueck, 1e-9)
        // Nur Ausfuehrungen, die Zeile mit den geschlossenen Posten zaehlt nicht
        assertEquals(4, b.geschaefte!!.size)
        // Zusammenfassende Zeile der Geldbewegungen zaehlt nicht
        assertEquals(4, b.geld!!.size)
        assertEquals(1141.03, b.bargeldEuro!!, 1e-9)
        assertNull(FlexBericht.lesen("<html><body>Wartung</body></html>"))
    }

    @Test
    fun fehlendeAbschnitteSindNull() {
        val b = FlexBericht.lesen(bericht("<OpenPositions />"))!!
        assertNotNull(b.positionen)
        assertTrue(b.positionen!!.isEmpty())
        assertNull(b.geschaefte)
        assertNull(b.geld)
        assertNull(b.bargeld)
        assertNull(b.bargeldEuro)
    }

    @Test
    fun kaufpostenWerdenSummiert() {
        val b = FlexBericht.lesen(bericht("""
            <OpenPositions>
            <OpenPosition currency="EUR" fxRateToBase="1" assetCategory="STK" symbol="BSP" description="BEISPIEL AG" conid="11" isin="DE0000000001" position="6" costBasisPrice="40" costBasisMoney="240" levelOfDetail="LOT" openDateTime="20240105;100000" />
            <OpenPosition currency="EUR" fxRateToBase="1" assetCategory="STK" symbol="BSP" description="BEISPIEL AG" conid="11" isin="DE0000000001" position="4" costBasisPrice="60" costBasisMoney="240" levelOfDetail="LOT" openDateTime="20240305;100000" />
            </OpenPositions>
        """.trimIndent()))!!
        val p = b.positionen!!.single()
        assertEquals(10.0, p.stueck, 1e-9)
        assertEquals(480.0, p.einstand!!, 1e-9)
        assertEquals(48.0, p.einstandJeStueck!!, 1e-9)
        assertEquals(BrokerExport.zeit("2024-01-05", "10:00:00"), p.eroeffnet)
    }

    @Test
    fun zeilenInEuro() {
        val b = FlexBericht.lesen(bericht(geschaefte + geld))!!
        val lese = BrokerExport.Bericht()
        val nicht = ArrayList<FlexBericht.Offen>()
        val z = FlexBericht.zeilen(b, lese, nicht)
        assertEquals(0, lese.ungelesen)
        assertEquals(0, lese.ohneWechselkurs)
        // Option steht unter nicht, das Devisengeschaeft entfaellt
        assertEquals(listOf(FlexBericht.Offen("USB 20JUN25 120 C", -1.0)), nicht)
        assertEquals(listOf("DEPOSIT", "BUY", "BUY", "DIVIDEND", "TAX", "INTEREST"), z.map { it.typ })
        val us = z[2]
        assertEquals("US0000000002", us.symbol)
        assertEquals(-360.0, us.betrag, 1e-9)
        assertEquals(90.0, us.preis!!, 1e-9)
        assertEquals(-0.9, us.gebuehr, 1e-9)
        assertEquals("t9002", us.id)
        assertEquals(1.8, z[3].betrag, 1e-9)
        assertEquals(-0.27, z[4].betrag, 1e-9)
        assertEquals("c7003", z[4].id)
    }

    @Test
    fun andereBasiswaehrungBrauchtKurse() {
        val trade = """<Trades><Trade currency="USD" fxRateToBase="1" assetCategory="STK" symbol="USB" description="US BEISPIEL" isin="US0000000002" tradeID="1" dateTime="20250115;153000" quantity="2" tradePrice="100" proceeds="-200" buySell="BUY" /></Trades>"""
        val ohne = FlexBericht.lesen(bericht(trade, basis = "USD"))!!
        val lese = BrokerExport.Bericht()
        FlexBericht.zeilen(ohne, lese)
        assertEquals(1, lese.ohneWechselkurs)
        val mit = FlexBericht.lesen(bericht(trade + """<ConversionRates><ConversionRate reportDate="20250131" fromCurrency="EUR" toCurrency="USD" rate="1.25" /></ConversionRates>""", basis = "USD"))!!
        val lese2 = BrokerExport.Bericht()
        val z = FlexBericht.zeilen(mit, lese2)
        assertEquals(0, lese2.ohneWechselkurs)
        assertEquals(-160.0, z.single().betrag, 1e-9)
    }

    @Test
    fun abgleichMitLeeremDepot() {
        val b = FlexBericht.lesen(bericht(positionen + geschaefte + geld))!!
        assertEquals(setOf("DE0000000001", "US0000000002", "OHN"), FlexAbgleich.gesucht(b).map { it.schluessel }.toSet())
        val p = FlexAbgleich.plan(b, kuerzel, Account(), jetzt)
        assertEquals(listOf(Art.NEU, Art.NEU), p.unterschiede.map { it.art })
        assertEquals(setOf("BSP.DE", "USB"), p.unterschiede.map { it.key }.toSet())
        assertEquals(10.0, p.konto.positions.first { it.key == "BSP.DE" }.qty, 1e-9)
        assertEquals(4.0, p.konto.positions.first { it.key == "USB" }.qty, 1e-9)
        // 2000 - 500 - 1 - 360 - 0,9 + 1,8 - 0,27 + 1,5
        assertEquals(1141.13, p.konto.cash, 1e-6)
        assertEquals(0.0, p.einzahlung, 1e-9)
        assertEquals(1141.03, p.bargeld!!, 1e-9)
        assertFalse(p.leer)
        assertFalse(p.ohnePositionen)
        // Option (im Bestand und als Geschaeft) und das Papier ohne ISIN stehen in der Liste, nichts davon im Depot
        assertTrue(p.offen.any { it.name == "USB 20JUN25 120 C" })
        assertTrue(p.offen.any { it.name == "OHNE KENNUNG SE" && it.stueck == 7.0 })
        assertEquals(2, p.konto.positions.size)
        assertTrue(p.konto.transactions.all { it.id.startsWith("ibf-") })
    }

    @Test
    fun zweiterAbgleichAendertNichts() {
        val b = FlexBericht.lesen(bericht(positionen + geschaefte + geld))!!
        val erster = FlexAbgleich.plan(b, kuerzel, Account(), jetzt)
        val zweiter = FlexAbgleich.plan(b, kuerzel, erster.konto, jetzt + 60_000L)
        assertTrue(zweiter.unterschiede.isEmpty())
        assertEquals(0, zweiter.buchungen)
        assertEquals(6, zweiter.bekannt)
        assertTrue(zweiter.leer)
        assertTrue(zweiter.konto === erster.konto)
    }

    @Test
    fun bestandOhneGeschaefteKommtAlsUebertrag() {
        val b = FlexBericht.lesen(bericht(positionen))!!
        val p = FlexAbgleich.plan(b, kuerzel, Account(cash = 100.0), jetzt)
        val de = p.konto.positions.first { it.key == "BSP.DE" }
        assertEquals(10.0, de.qty, 1e-9)
        assertEquals(50.1, de.avgPrice, 1e-9)
        val us = p.konto.positions.first { it.key == "USB" }
        assertEquals(90.0, us.avgPrice, 1e-9)
        // Kein Geldfluss
        assertEquals(100.0, p.konto.cash, 1e-9)
        assertEquals(501.0 + 360.0, p.konto.transferredIn, 1e-9)
        assertTrue(p.konto.transactions.all { it.tag == "TRANSFER_IN" && it.via == "IMPORT_IBF" })
    }

    @Test
    fun geaendertUndEntfaellt() {
        val b = FlexBericht.lesen(bericht(positionen))!!
        val start = FlexAbgleich.plan(b, kuerzel, Account(), jetzt).konto
        // Eigene Position aus anderer Quelle, die der Bericht nicht kennt
        val depot = Trading.importPosition(start, "FND.DE", "Beispielfonds", 3.0, 20.0, jetzt - 86_400_000L, jetzt).account
        val spaeter = FlexBericht.lesen(bericht("""
            <OpenPositions>
            <OpenPosition currency="EUR" fxRateToBase="1" assetCategory="STK" symbol="BSP" description="BEISPIEL AG" conid="11" isin="DE0000000001" position="6" markPrice="55" costBasisPrice="50.1" costBasisMoney="300.6" levelOfDetail="SUMMARY" />
            </OpenPositions>
        """.trimIndent()))!!
        val p = FlexAbgleich.plan(spaeter, kuerzel, depot, jetzt + 1000L)
        assertEquals(listOf(Art.GEAENDERT, Art.ENTFAELLT), p.unterschiede.map { it.art })
        val g = p.unterschiede[0]
        assertEquals("BSP.DE", g.key)
        assertEquals(10.0, g.vorher, 1e-9)
        assertEquals(6.0, g.nachher, 1e-9)
        assertEquals("USB", p.unterschiede[1].key)
        // Die fremde Position bleibt
        assertEquals(3.0, p.konto.positions.first { it.key == "FND.DE" }.qty, 1e-9)
        assertNull(p.konto.positions.firstOrNull { it.key == "USB" })
        // Uebertrag aus dem Depot zum Einstand, ohne Geld
        assertEquals(depot.cash, p.konto.cash, 1e-9)
        assertEquals(2, p.konto.transactions.count { it.type == TxType.SELL && it.tag == "TRANSFER_OUT" })
    }

    @Test
    fun ohneAbschnittKeinBestandsabgleich() {
        val b = FlexBericht.lesen(bericht(positionen))!!
        val depot = FlexAbgleich.plan(b, kuerzel, Account(), jetzt).konto
        val nurGeld = FlexBericht.lesen(bericht(geld))!!
        val p = FlexAbgleich.plan(nurGeld, kuerzel, depot, jetzt + 1000L)
        assertTrue(p.ohnePositionen)
        assertTrue(p.unterschiede.isEmpty())
        assertEquals(4, p.buchungen)
    }

    @Test
    fun errechneteEinzahlungStattMinus() {
        val b = FlexBericht.lesen(bericht(positionen + geschaefte))!!
        val p = FlexAbgleich.plan(b, kuerzel, Account(), jetzt)
        // 500 + 1 + 360 + 0,9
        assertEquals(861.9, p.einzahlung, 1e-6)
        assertEquals(0.0, p.konto.cash, 1e-6)
        assertEquals(861.9, p.konto.deposited, 1e-6)
    }

    private class Dienst(val antworten: MutableList<String?>) {
        val adressen = ArrayList<String>()
        var zeit = 0L
        val pausen = ArrayList<Long>()
        val abruf = FlexAbruf(holen = { adressen += it; antworten.removeAt(0) }, uhr = { zeit }, warten = { pausen += it; zeit += it })
    }

    private fun fehler(code: Int) = "<FlexStatementResponse timestamp=\"x\"><Status>Fail</Status><ErrorCode>$code</ErrorCode><ErrorMessage>x</ErrorMessage></FlexStatementResponse>"
    private fun erfolg(url: String) = "<FlexStatementResponse timestamp=\"x\"><Status>Success</Status><ReferenceCode>555</ReferenceCode><Url>$url</Url></FlexStatementResponse>"

    @Test
    fun abrufWartetAufDenBericht() = runBlocking {
        val d = Dienst(mutableListOf(erfolg("https://gdcdyn.interactivebrokers.com/AccountManagement/FlexWebService/GetStatement"), fehler(1019), fehler(1019), bericht(positionen)))
        val e = d.abruf.bericht("0123456789abc", "424242")
        assertTrue(e is FlexAbruf.Ergebnis.Ok)
        assertEquals(4, d.adressen.size)
        assertEquals("https://ndcdyn.interactivebrokers.com/AccountManagement/FlexWebService/SendRequest?t=0123456789abc&q=424242&v=3", d.adressen[0])
        assertEquals("https://gdcdyn.interactivebrokers.com/AccountManagement/FlexWebService/GetStatement?t=0123456789abc&q=555&v=3", d.adressen[1])
        assertTrue(d.adressen.all { FlexAbruf.erlaubt(it) })
        // Vor jedem Abholen eine Pause, laenger werdend
        assertEquals(listOf(3_000L, 5_000L, 8_000L), d.pausen)
    }

    @Test
    fun abrufHaeltGrenzenEin() = runBlocking {
        // Der Bericht wird nie fertig: der Abruf gibt nach wenigen Versuchen auf
        val d = Dienst(MutableList<String?>(20) { if (it == 0) erfolg("") else fehler(1019) })
        val e = d.abruf.bericht("0123456789abc", "424242")
        assertEquals(FlexAbruf.Ergebnis.Fehler(FlexBericht.Fehlerart.WARTEN, 1019), e)
        assertTrue(d.adressen.size <= 8)
        // Zu viele Anfragen: sofort Schluss, keine Wiederholung
        val voll = Dienst(mutableListOf(fehler(1018), fehler(1018)))
        assertEquals(FlexAbruf.Ergebnis.Fehler(FlexBericht.Fehlerart.ZU_VIELE, 1018), voll.abruf.bericht("0123456789abc", "424242"))
        assertEquals(1, voll.adressen.size)
        // Takt: nie zwei Anfragen in derselben Sekunde, hoechstens acht je Minute
        val zeiten = ArrayList<Long>()
        var t = 0L
        val schnell = FlexAbruf(holen = { zeiten += t; fehler(1015) }, uhr = { t }, warten = { t += it })
        repeat(12) { schnell.bericht("0123456789abc", "424242") }
        assertTrue(zeiten.zipWithNext().all { (a, b) -> b - a >= 1_000L })
        assertTrue(zeiten.indices.all { i -> zeiten.count { it >= zeiten[i] && it < zeiten[i] + 60_000L } <= 8 })
    }

    @Test
    fun tokenGehtNurAnDenBroker() = runBlocking {
        // Fremde Adresse zum Abholen in der Antwort: der Abruf bleibt beim festen Rechner
        val d = Dienst(mutableListOf(erfolg("https://beispiel.invalid/AccountManagement/FlexWebService/GetStatement"), bericht(positionen)))
        d.abruf.bericht("0123456789abc", "424242")
        assertTrue(d.adressen[1].startsWith("https://ndcdyn.interactivebrokers.com/"))
        assertFalse(FlexAbruf.erlaubt("http://ndcdyn.interactivebrokers.com/x"))
        assertFalse(FlexAbruf.erlaubt("https://interactivebrokers.com.beispiel.invalid/x"))
        assertFalse(FlexAbruf.erlaubt("https://ndcdyn.interactivebrokers.com@beispiel.invalid/x"))
        assertFalse(FlexAbruf.erlaubt("https://beispiel.invalid/?x=.interactivebrokers.com"))
        assertTrue(FlexAbruf.erlaubt("https://gdcdyn.interactivebrokers.com/AccountManagement/FlexWebService/GetStatement"))
        // Eingaben, die die Adresse veraendern koennten, gehen nicht hinaus
        val leer = Dienst(mutableListOf())
        assertEquals(FlexAbruf.Ergebnis.Eingabe, leer.abruf.bericht("abc&q=1", "424242"))
        assertEquals(FlexAbruf.Ergebnis.Eingabe, leer.abruf.bericht("0123456789abc", "42/../x"))
        assertTrue(leer.adressen.isEmpty())
        // Ohne Antwort
        val stumm = Dienst(mutableListOf(null))
        assertEquals(FlexAbruf.Ergebnis.KeineAntwort, stumm.abruf.bericht("0123456789abc", "424242"))
    }

    @Test
    fun tresorNamenKollidierenNichtMitDerBereinigung() {
        // Die Bereinigung beim Start loescht alle Namen mit broker_; der Flex-Zugang liegt unter schluessel_flex_
        assertTrue(TresorBereinigung.alleTresorNamen().none { "flex" in it })
    }

    @Test
    fun texteInBeidenSprachen() {
        assertEquals(FlexTexte.schluessel(true), FlexTexte.schluessel(false))
        FlexBericht.Fehlerart.entries.forEach { assertTrue(FlexTexte.text(true, "f." + it.name).isNotBlank()) }
        Art.entries.forEach { assertTrue(FlexTexte.text(false, "art." + it.name).isNotBlank()); assertTrue(FlexTexte.text(true, "n." + it.name).isNotBlank()) }
        // Keine Gedankenstriche und keine Dreiecke als Textzeichen
        listOf(true, false).forEach { de -> FlexTexte.schluessel(de).forEach { k -> assertFalse(k, FlexTexte.text(de, k).any { it in "–—▲▼▶△▽" }) } }
    }
}
