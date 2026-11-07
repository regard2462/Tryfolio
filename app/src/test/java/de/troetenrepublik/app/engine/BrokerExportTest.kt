package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.engine.BrokerExport.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Erfundene Beispielzeilen im Format der jeweiligen Datei.
class BrokerExportTest {

    private val jetzt = 1_900_000_000_000L
    private val kuerzel = mapOf("DE0000000001" to "BSP.DE", "US0000000002" to "USB", "BTC" to "BTC-EUR", "ABC" to "ABC")

    private fun konto(format: Format, text: String) =
        TradeRepublicImport.konto(BrokerExport.lesen(format, text), kuerzel, Account(), jetzt, format.anzeige, format.praefix)

    @Test
    fun zahlenUndDaten() {
        assertEquals(1234.56, BrokerExport.zahl("1.234,56", true)!!, 1e-9)
        assertEquals(1234.56, BrokerExport.zahl("1,234.56", false)!!, 1e-9)
        assertEquals(-30.93, BrokerExport.zahl("-\$30.93", false)!!, 1e-9)
        assertEquals(1197.08, BrokerExport.zahl("USD 1197.08", false)!!, 1e-9)
        assertEquals(-2491.875, BrokerExport.zahl("-2.491,875", true)!!, 1e-9)
        assertEquals(12.5, BrokerExport.zahl("12,5", false)!!, 1e-9)
        assertNull(BrokerExport.zahl("Not available", false))
        val tag = BrokerExport.zeit("2025-01-31")
        assertEquals(tag, BrokerExport.zeit("31.01.2025"))
        assertEquals(tag, BrokerExport.zeit("31-01-2025"))
        assertEquals(tag, BrokerExport.zeit("31/01/2025"))
        assertEquals(java.time.Instant.parse("2025-01-31T12:00:00Z").toEpochMilli(), tag)
        assertEquals(java.time.Instant.parse("2025-01-31T14:30:03Z").toEpochMilli(), BrokerExport.zeit("2025-01-31 14:30:03.613"))
        assertEquals(java.time.Instant.parse("2025-01-31T09:05:00Z").toEpochMilli(), BrokerExport.zeit("31-01-2025", "09:05"))
        assertTrue(BrokerExport.istIsin("DE0000000001"))
        assertTrue(!BrokerExport.istIsin("AAPL"))
    }

    @Test
    fun unbekannt() {
        assertNull(BrokerExport.erkennen("{\"app\":\"troetenrepublik\"}"))
        assertNull(BrokerExport.erkennen("a;b;c\n1;2;3"))
    }

    @Test
    fun scalable() {
        val datei = """
            date;time;status;reference;description;assetType;type;isin;shares;price;amount;fee;tax;currency
            2025-01-02;10:00:00;Executed;"SCAL1";"Einzahlung";Cash;Deposit;;;;2.000,00;0,00;0,00;EUR
            2025-01-03;11:00:00;Executed;"SCAL2";"Beispiel AG";Security;Buy;DE0000000001;10;50,00;-500,00;0,99;0,00;EUR
            2025-01-04;11:00:00;Executed;"SCAL3";"Beispiel AG";Security;Savings plan;DE0000000001;10;70,00;-700,00;0,00;0,00;EUR
            2025-01-05;11:00:00;Cancelled;"SCAL4";"Beispiel AG";Security;Buy;DE0000000001;0;70,00;0,00;0,00;0,00;EUR
            2025-01-06;12:00:00;Executed;"SCAL5";"Beispiel AG";Security;Sell;DE0000000001;5;80,00;400,00;0,99;2,50;EUR
            2025-01-07;02:00:00;Executed;"SCAL6";"Beispiel AG";Security;Distribution;DE0000000001;;;10,00;0,00;2,64;EUR
        """.trimIndent()
        assertEquals(Format.SCALABLE, BrokerExport.erkennen(datei))
        val e = konto(Format.SCALABLE, datei)
        assertEquals("Scalable Capital", e.quelle)
        // 2000 - 500 - 0,99 - 700 + 400 - 0,99 - 2,50 + 10 - 2,64
        assertEquals(1202.88, e.konto.cash, 1e-6)
        val p = e.konto.positions.single()
        assertEquals("BSP.DE", p.symbol)
        assertEquals(15.0, p.qty, 1e-9)
        assertEquals(60.0, p.avgPrice, 1e-9)
        assertEquals(2000.0, e.konto.deposited, 1e-9)
        assertEquals(1, e.konto.transactions.count { it.type == TxType.DIVIDEND })
        assertTrue(e.konto.transactions.all { it.id.startsWith("sc-") })
    }

    @Test
    fun trading212() {
        val datei = """
            Action,Time,ISIN,Ticker,Name,Notes,ID,No. of shares,Price / share,Currency (Price / share),Exchange rate,Result,Currency (Result),Total,Currency (Total),Withholding tax,Currency (Withholding tax),Currency conversion fee,Currency (Currency conversion fee)
            Deposit,2025-01-02 09:00:00,,,,,abc,,,,,,,1000.00,"EUR",,,,
            Market buy,2025-01-03 14:30:03.613,US0000000002,USB,"Beispiel Inc",,EOF1,2.0000000000,110.00,USD,1.10,,"EUR",200.30,"EUR",,,0.30,"EUR"
            Limit sell,2025-01-04 15:00:00,US0000000002,USB,"Beispiel Inc",,EOF2,1.0000000000,132.00,USD,1.10,19.70,"EUR",119.70,"EUR",,,0.30,"EUR"
            Dividend (Ordinary),2025-01-05 10:00:00,US0000000002,USB,"Beispiel Inc",,,1.0000000000,0.85,USD,Not available,,,0.77,"EUR",0.15,USD,,
            Interest on cash,2025-01-06 10:00:00,,,,,def,,,,,,,1.23,"EUR",,,,
            Withdrawal,2025-01-07 10:00:00,,,,,ghi,,,,,,,-100.00,"EUR",,,,
            Currency conversion,2025-01-07 11:00:00,,,,,jkl,,,,,,,0.00,"EUR",,,,
        """.trimIndent()
        assertEquals(Format.TRADING212, BrokerExport.erkennen(datei))
        val e = konto(Format.TRADING212, datei)
        assertEquals(1000.0 - 200.30 + 119.70 + 0.77 + 1.23 - 100.0, e.konto.cash, 1e-6)
        val p = e.konto.positions.single()
        assertEquals(1.0, p.qty, 1e-9)
        assertEquals(100.0, p.avgPrice, 1e-9)
        val verkauf = e.konto.transactions.single { it.type == TxType.SELL }
        assertEquals(20.0, verkauf.realized, 1e-6)
        assertEquals(0.3, verkauf.fee, 1e-9)
    }

    @Test
    fun trading212ZweiDateienZusammen() {
        val kopf = "Action,Time,ISIN,Ticker,Name,No. of shares,Price / share,Currency (Price / share),Exchange rate,Total,Currency (Total)"
        val a = "$kopf\nDeposit,2025-01-02 09:00:00,,,,,,,,1000.00,EUR\nMarket buy,2025-01-03 10:00:00,US0000000002,USB,Beispiel Inc,1,100,EUR,1,100.00,EUR"
        val b = "$kopf\nMarket buy,2025-01-03 10:00:00,US0000000002,USB,Beispiel Inc,1,100,EUR,1,100.00,EUR\nMarket buy,2025-02-03 10:00:00,US0000000002,USB,Beispiel Inc,1,120,EUR,1,120.00,EUR"
        val zusammen = Zusammenfuehren.konten(konto(Format.TRADING212, a).konto, konto(Format.TRADING212, b).konto)
        // Der Kauf vom 3.1. steht in beiden Dateien und zaehlt bei den Buchungen nur einmal
        assertEquals(3, zusammen.transactions.size)
    }

    @Test
    fun degiro() {
        val tx = """
            Datum,Uhrzeit,Produkt,ISIN,Referenzbörse,Ausführungsort,Anzahl,Kurs,,Wert in Lokalwährung,,Wert EUR,Wechselkurs,AutoFX-Gebühr,Transaktionsgebühren und/oder Fremdkosten EUR,Gesamt EUR,Order-ID,
            03-01-2025,10:12,BEISPIEL AG,DE0000000001,XET,XETA,10,"50,00",EUR,"-500,00",EUR,"-500,00",,"0,00","-2,00","-502,00",,0a1b2c3d-0000-0000-0000-000000000001
            05-01-2025,11:00,BEISPIEL AG,DE0000000001,XET,XETA,-4,"60,00",EUR,"240,00",EUR,"240,00",,"0,00","-2,00","238,00",,0a1b2c3d-0000-0000-0000-000000000002
            06-01-2025,15:30,BEISPIEL INC,US0000000002,NDQ,XNAS,2,"110,00",USD,"-220,00",USD,"-200,00","1,1","-0,50","-1,00","-201,50",,0a1b2c3d-0000-0000-0000-000000000003
        """.trimIndent()
        assertEquals(Format.DEGIRO, BrokerExport.erkennen(tx))
        val e = konto(Format.DEGIRO, tx)
        assertEquals(-502.0 + 238.0 - 201.5, e.konto.cash, 1e-6)
        assertEquals(6.0, e.konto.positions.single { it.symbol == "BSP.DE" }.qty, 1e-9)
        assertEquals(100.0, e.konto.positions.single { it.symbol == "USB" }.avgPrice, 1e-9)

        val konto = """
            Datum,Uhrzeit,Valutadatum,Produkt,ISIN,Beschreibung,FX,Änderung,,Saldo,,Order-ID
            02-01-2025,09:00,02-01-2025,,,flatex Einzahlung,,EUR,"1000,00",EUR,"1000,00",
            03-01-2025,10:12,03-01-2025,BEISPIEL AG,DE0000000001,"Kauf 10 BEISPIEL AG@50 EUR (DE0000000001)",,EUR,"-500,00",EUR,"500,00",0a1b
            03-01-2025,10:12,03-01-2025,BEISPIEL AG,DE0000000001,DEGIRO Transaktionsgebühren und/oder Kosten von Dritten,,EUR,"-2,00",EUR,"498,00",0a1b
            10-01-2025,07:00,09-01-2025,BEISPIEL AG,DE0000000001,Dividende,,EUR,"12,00",EUR,"510,00",
            10-01-2025,07:00,09-01-2025,BEISPIEL AG,DE0000000001,Dividendensteuer,,EUR,"-3,17",EUR,"506,83",
            15-01-2025,07:00,15-01-2025,BEISPIEL INC,US0000000002,Dividende,,USD,"1,10",USD,"1,10",
            16-01-2025,06:00,15-01-2025,,,Währungswechsel (Ausbuchung),"1,1",USD,"-1,10",USD,"0,00",
            16-01-2025,06:00,15-01-2025,,,Währungswechsel (Einbuchung),"1,1",EUR,"1,00",EUR,"507,83",
            31-01-2025,07:00,31-01-2025,,,DEGIRO Exchange Connection Fee 2025 (Xetra - XET),,EUR,"-2,50",EUR,"505,33",
        """.trimIndent()
        assertEquals(Format.DEGIRO_KONTO, BrokerExport.erkennen(konto))
        val k = konto(Format.DEGIRO_KONTO, konto).konto
        assertEquals(1000.0 + 12.0 - 3.17 + 1.0 - 2.5, k.cash, 1e-6)
        assertEquals(2, k.transactions.count { it.type == TxType.DIVIDEND })
        assertTrue(k.positions.isEmpty())
        val beide = Zusammenfuehren.konten(e.konto, k)
        assertEquals(1000.0 + 12.0 - 3.17 + 1.0 - 2.5 - 502.0 + 238.0 - 201.5, beide.cash, 1e-6)
    }

    @Test
    fun interactiveBrokersFlex() {
        val datei = """
            "ClientAccountID","CurrencyPrimary","FXRateToBase","AssetClass","Symbol","Description","ISIN","DateTime","TradeDate","Quantity","TradePrice","Proceeds","Taxes","IBCommission","NetCash","Buy/Sell","TransactionID","LevelOfDetail"
            "U1","EUR","1","STK","BSP","BEISPIEL AG","DE0000000001","20250103;100000","20250103","10","50","-500","0","-3","-503","BUY","1","EXECUTION"
            "U1","USD","0.9","STK","USB","BEISPIEL INC","US0000000002","20250104;150000","20250104","-0","100","0","0","0","0","SELL","2","EXECUTION"
            "U1","USD","0.9","STK","USB","BEISPIEL INC","US0000000002","20250104;150000","20250104","2","100","-200","0","-1","-201","BUY","3","EXECUTION"
            "U1","USD","0.9","STK","USB","BEISPIEL INC","US0000000002","20250104;150000","20250104","2","100","-200","0","-1","-201","BUY","3","CLOSED_LOT"
            "U1","EUR","1","CASH","EUR.USD","EUR.USD","","20250104;150000","20250104","100","1.1","-110","0","-2","-112","BUY","4","EXECUTION"
            "ClientAccountID","CurrencyPrimary","FXRateToBase","Type","SettleDate","ISIN","Description","Amount"
            "U1","EUR","1","Deposits/Withdrawals","20250102","","CASH RECEIPTS","1000"
            "U1","USD","0.9","Dividends","20250110","US0000000002","USB(US0000000002) CASH DIVIDEND USD 0.50 PER SHARE","1"
            "U1","USD","0.9","Withholding Tax","20250110","US0000000002","USB(US0000000002) CASH DIVIDEND - US TAX","-0.15"
        """.trimIndent()
        assertEquals(Format.IBKR, BrokerExport.erkennen(datei))
        val e = konto(Format.IBKR, datei)
        assertEquals(316.87, e.konto.cash, 1e-6)
        assertEquals(2, e.konto.positions.size)
        assertEquals(90.0, e.konto.positions.single { it.symbol == "USB" }.avgPrice, 1e-9)
    }

    @Test
    fun interactiveBrokersMitKopfUndDaten() {
        val datei = """
            "HEADER","TRNT","CurrencyPrimary","FXRateToBase","AssetClass","Symbol","ISIN","TradeDate","Quantity","TradePrice","Proceeds","IBCommission","Buy/Sell"
            "DATA","TRNT","EUR","1","STK","BSP","DE0000000001","20250103","10","50","-500","-1","BUY"
        """.trimIndent()
        assertEquals(Format.IBKR, BrokerExport.erkennen(datei))
        assertEquals(10.0, konto(Format.IBKR, datei).konto.positions.single().qty, 1e-9)
    }

    @Test
    fun revolut() {
        val datei = """
            Date,Ticker,Type,Quantity,Price per share,Total Amount,Currency,FX Rate
            2025-01-02T09:00:00.000Z,,CASH TOP-UP,,,"${'$'}1,100.00",USD,1.10
            2025-01-03T14:30:10.514Z,ABC,BUY - MARKET,2,${'$'}55.00,${'$'}110.00,USD,1.10
            2025-01-10T14:30:10.514Z,ABC,DIVIDEND,,,${'$'}1.10,USD,1.10
            2025-01-11T14:30:10.514Z,,CUSTODY FEE,,,-${'$'}0.11,USD,1.10
            2025-01-12T14:30:10.514Z,ABC,STOCK SPLIT,2,,${'$'}0,USD,1.10
        """.trimIndent()
        assertEquals(Format.REVOLUT, BrokerExport.erkennen(datei))
        val e = konto(Format.REVOLUT, datei)
        assertEquals(1000.0 - 100.0 + 1.0 - 0.1, e.konto.cash, 1e-6)
        val p = e.konto.positions.single()
        assertEquals("ABC", p.symbol)
        assertEquals(4.0, p.qty, 1e-9)
        assertEquals(25.0, p.avgPrice, 1e-9)
    }

    @Test
    fun ingMitErrechneterEinzahlung() {
        val datei = """
            Ausgeführte Orders vom 31.01.2025 10:00;Kunde;Beispiel
            Depotnummer;0000000000

            Orderart;Ausführungsdatum;Ordernummer;ISIN;Wertpapiername;Stück/Nominale;Einheitskennzeichen;Limit;Start Buy/Stop Loss;Abstand;Limittyp;Ausführungskurs;Währung;Status
            Kauf;03.01.2025;1;DE0000000001;BEISPIEL AG;10;Stück;;;;Billigst;50,00;EUR;Ausgeführt
            Kauf;04.01.2025;2;DE0000000001;BEISPIEL AG;10;Stück;;;;Billigst;70,00;EUR;Storniert
            Verkauf;05.01.2025;3;DE0000000001;BEISPIEL AG;4;Stück;;;;Bestens;60,00;EUR;Ausgeführt
        """.trimIndent()
        assertEquals(Format.ING, BrokerExport.erkennen(datei))
        val e = konto(Format.ING, datei)
        assertEquals(240.0, e.konto.cash, 1e-6)
        assertEquals(500.0, e.konto.deposited, 1e-6)
        assertEquals(6.0, e.konto.positions.single().qty, 1e-9)
    }

    @Test
    fun flatex() {
        val datei = """
            Nummer;Buchtag;Valuta;ISIN;Bezeichnung;Nominal;;Buchungsinformationen;TA-Nr.;Kurs;;Depot
            1;03.01.2025;07.01.2025;DE0000000001;BEISPIEL AG;10;Stk.;Ausführung ORDER Kauf DE0000000001 1;11;50,00;EUR;Depot
            2;05.01.2025;07.01.2025;DE0000000001;BEISPIEL AG;-4;Stk.;Ausführung ORDER Verkauf DE0000000001 2;12;60,00;EUR;Depot
            3;06.01.2025;06.01.2025;US0000000002;BEISPIEL INC;3;Stk.;WP-Eingang US0000000002;13;;;Depot
            ;;;;;;;;;;;
        """.trimIndent()
        assertEquals(Format.FLATEX, BrokerExport.erkennen(datei))
        val e = konto(Format.FLATEX, datei)
        assertEquals(6.0, e.konto.positions.single { it.symbol == "BSP.DE" }.qty, 1e-9)
        assertEquals(3.0, e.konto.positions.single { it.symbol == "USB" }.qty, 1e-9)
        assertEquals(240.0, e.konto.cash, 1e-6)
    }

    @Test
    fun bitpanda() {
        val datei = """
            Disclaimer: Beispiel
            Name: Beispiel
            Transaction ID,Timestamp,Transaction Type,In/Out,Amount Fiat,Fiat,Amount Asset,Asset,Asset market price,Asset market price currency,Asset class,Product ID,Fee,Fee asset,Spread,Spread Currency,Tax Fiat
            T1,2025-01-02T10:00:00+01:00,deposit,incoming,1000.00,EUR,1000.00,EUR,-,-,Fiat,-,0.00,EUR,-,-,0.00
            T2,2025-01-03T10:00:00+01:00,buy,outgoing,500.00,EUR,0.01000000,BTC,50000.00,EUR,Cryptocurrency,1,-,-,-,-,0.00
            T3,2025-01-04T10:00:00+01:00,transfer,incoming,0.00,EUR,0.00100000,BTC,-,-,Cryptocurrency,1,-,-,-,-,0.00
            T4,2025-01-05T10:00:00+01:00,sell,incoming,300.00,EUR,0.00500000,BTC,60000.00,EUR,Cryptocurrency,1,-,-,-,-,0.00
        """.trimIndent()
        assertEquals(Format.BITPANDA, BrokerExport.erkennen(datei))
        val e = konto(Format.BITPANDA, datei)
        assertEquals(800.0, e.konto.cash, 1e-6)
        val p = e.konto.positions.single()
        assertEquals("BTC-EUR", p.symbol)
        assertEquals(0.005, p.qty, 1e-12)
    }

    @Test
    fun tradeRepublicBleibtErkannt() {
        val kopf = "\"datetime\",\"date\",\"account_type\",\"category\",\"type\",\"asset_class\",\"name\",\"symbol\",\"shares\",\"price\",\"amount\",\"fee\",\"tax\",\"currency\""
        assertEquals(Format.TRADE_REPUBLIC, BrokerExport.erkennen("$kopf\n"))
    }
}
