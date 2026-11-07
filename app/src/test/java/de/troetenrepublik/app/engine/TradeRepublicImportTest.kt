package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Transaktionsexport von Trade Republic; erfundene Beispielzeilen im Format der Datei.
class TradeRepublicImportTest {

    private val kopf = "\"datetime\",\"date\",\"account_type\",\"category\",\"type\",\"asset_class\",\"name\",\"symbol\",\"shares\",\"price\",\"amount\",\"fee\",\"tax\",\"currency\",\"original_amount\",\"original_currency\",\"fx_rate\",\"description\",\"transaction_id\",\"counterparty_name\",\"counterparty_iban\",\"payment_reference\",\"mcc_code\""

    private fun z(t: String, kat: String, typ: String, klasse: String, name: String, sym: String, stueck: String, preis: String, betrag: String, geb: String = "", steuer: String = "", id: String) =
        "\"$t\",\"${t.take(10)}\",\"DEFAULT\",\"$kat\",\"$typ\",\"$klasse\",\"$name\",\"$sym\",\"$stueck\",\"$preis\",\"$betrag\",\"$geb\",\"$steuer\",\"EUR\",\"\",\"\",\"\",\"Text, mit Komma\",\"$id\",\"\",\"\",\"\",\"\""

    private val datei = listOf(
        kopf,
        z("2025-01-02T09:00:00Z", "CASH", "TRANSFER_INSTANT_INBOUND", "", "", "", "", "", "1000.00", id = "a"),
        z("2025-01-03T10:00:00Z", "TRADING", "BUY", "STOCK", "Beispiel AG", "DE0000000001", "10.0", "50.0", "-500.00", "-1.00", id = "b"),
        z("2025-01-04T10:00:00Z", "TRADING", "BUY", "STOCK", "Beispiel AG", "DE0000000001", "10.0", "70.0", "-700.00", "-1.00", id = "c").let { it },
        z("2025-01-05T10:00:00Z", "TRADING", "SELL", "STOCK", "Beispiel AG", "DE0000000001", "-5.0", "80.0", "400.00", "-1.00", "-2.50", id = "d"),
        z("2025-01-06T10:00:00Z", "TRADING", "BUY", "DERIVATIVE", "Long 100 €", "DE000XX00001", "20", "1.50", "-30.00", "-1.00", id = "e"),
        z("2025-01-08T10:00:00Z", "CORPORATE_ACTION", "WARRANT_EXERCISE", "DERIVATIVE", "Long 100 €", "DE000XX00001", "-20", "", "", id = "f"),
        z("2025-01-09T10:00:00Z", "CASH", "TILG", "DERIVATIVE", "Long 100 €", "DE000XX00001", "", "", "0.20", id = "g"),
        z("2025-01-10T10:00:00Z", "TRADING", "BUY", "CRYPTO", "Ethereum", "XF000ETH0019", "0.1", "3000", "-300.00", "-1.00", id = "h"),
        z("2025-01-11T10:00:00Z", "CASH", "INTEREST_PAYMENT", "", "", "", "", "", "2.00", "", "-0.50", id = "i"),
        z("2025-01-12T10:00:00Z", "CASH", "TRANSFER_INSTANT_OUTBOUND", "", "", "", "", "", "-50.00", id = "j"),
        // Tausch: neue Stuecke stehen in der Datei vor den abgehenden
        z("2025-01-13T10:00:00Z", "CORPORATE_ACTION", "EXCHANGE", "STOCK", "Neu AG", "CA0000000002", "30", "", "", id = "k"),
        z("2025-01-13T10:00:01Z", "CORPORATE_ACTION", "EXCHANGE", "STOCK", "Beispiel AG", "DE0000000001", "-15", "", "", id = "l"),
    ).joinToString("\n")

    private val kuerzel = mapOf("DE0000000001" to "BSP.DE", "CA0000000002" to "NEU.TO", "ETH" to "ETH-EUR")

    @Test
    fun erkenntDieDatei() {
        assertTrue(TradeRepublicImport.erkennen(datei))
        assertFalse(TradeRepublicImport.erkennen("{\"app\":\"troetenrepublik\"}"))
    }

    @Test
    fun csvMitKommaImFeld() {
        val z = TradeRepublicImport.lesen(datei)
        assertEquals(12, z.size)
        assertEquals("Text, mit Komma", z[0].text)
        assertEquals("ETH", TradeRepublicImport.kryptoKuerzel("XF000ETH0019"))
        assertEquals("MELANIA", TradeRepublicImport.kryptoKuerzel("XF00MELANIA2"))
    }

    @Test
    fun depotUndBargeld() {
        val e = TradeRepublicImport.konto(TradeRepublicImport.lesen(datei), kuerzel, Account(watchlist = listOf("SAP.DE")), 1_800_000_000_000L)
        val k = e.konto
        // 1000 - 501 - 701 + 400 - 1 - 2,50 - 31 + 0,20 - 301 + 1,50 - 50
        assertEquals(1000 - 501 - 701 + 400 - 1 - 2.5 - 31 + 0.2 - 301 + 1.5 - 50, k.cash, 1e-9)
        assertEquals(1000.0, k.deposited, 1e-9)
        assertEquals(50.0, k.withdrawn, 1e-9)
        assertEquals(listOf("SAP.DE"), k.watchlist)
        // Beispiel AG ganz getauscht: 15 Stueck zu 60 (Durchschnitt) = 900 Einstand fuer 30 neue Stueck
        val neu = k.positions.single { it.symbol == "NEU.TO" }
        assertEquals(30.0, neu.qty, 1e-9)
        assertEquals(30.0, neu.avgPrice, 1e-9)
        assertTrue(k.positions.none { it.symbol == "BSP.DE" })
        assertEquals(0.1, k.positions.single { it.symbol == "ETH-EUR" }.qty, 1e-9)
        // Verkauf: Gewinn gegen Durchschnitt 60, Steuer gezahlt
        val verkauf = k.transactions.single { it.type == TxType.SELL && it.tag == "SELL" }
        assertEquals(100.0, verkauf.realized, 1e-9)
        assertEquals(2.5, verkauf.tax, 1e-9)
        assertEquals(397.5, verkauf.amount, 1e-9)
        // Derivat: Ausuebung mit Rueckzahlung zu einer Buchung, ohne Kurs bei Yahoo
        val faellig = k.transactions.single { it.type == TxType.EXPIRY }
        assertEquals(0.20, faellig.amount, 1e-9)
        assertEquals(0.01, faellig.price, 1e-9)
        assertEquals(TradeRepublicImport.OHNE_KURS + "DE000XX00001", faellig.symbol)
        assertEquals(0.2 - 30.0, faellig.realized, 1e-9)
        assertEquals(1.5, k.transactions.single { it.type == TxType.INTEREST }.amount, 1e-9)
        assertTrue(e.ohneKurs.isEmpty())
        assertTrue(e.ohneKauf.isEmpty())
    }

    @Test
    fun offeneWerteOhneKursWerdenGemeldet() {
        val e = TradeRepublicImport.konto(TradeRepublicImport.lesen(datei), kuerzel - "CA0000000002", Account(), 1_800_000_000_000L)
        assertEquals(listOf("Neu AG" to 30.0), e.ohneKurs)
    }
}
