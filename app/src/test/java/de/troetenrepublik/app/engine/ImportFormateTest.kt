package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.Position
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.engine.BrokerExport.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Je Format eine vollstaendige Musterdatei (erfundene Vorgaenge im Aufbau der echten Exporte) unter test/resources/import.
// Erwartet werden Bestand, Guthaben, eingezahltes Kapital, Ergebnis, Steuern und Gebuehren, nachgerechnet von Hand.
class ImportFormateTest {

    private val jetzt = 1_900_000_000_000L
    private val kuerzel = mapOf(
        "DE0000000001" to "BSP.DE", "IE0000000003" to "ETF.DE", "US0000000002" to "USB", "BTC" to "BTC-EUR", "ETH" to "ETH-EUR",
        "MSFT" to "MSFT", "ABC" to "ABC",
    )

    private fun res(name: String) = javaClass.getResourceAsStream("/import/$name")!!.readBytes().toString(Charsets.UTF_8)

    private fun lesen(format: Format, text: String) = BrokerExport.lesenMitBericht(format, text)

    private fun konto(format: Format, text: String): Pair<TradeRepublicImport.Ergebnis, BrokerExport.Bericht> {
        val (z, b) = lesen(format, text)
        return TradeRepublicImport.konto(z, kuerzel, Account(), jetzt, format.anzeige, format.praefix) to b
    }

    private fun summeBetraege(e: TradeRepublicImport.Ergebnis) = e.konto.transactions.sumOf { it.amount }

    // Das Guthaben ist die Summe der Buchungsbetraege: darauf rechnet das Leistungs-Werkzeug zurueck.
    private fun pruefeSumme(e: TradeRepublicImport.Ergebnis) = assertEquals(e.konto.cash, summeBetraege(e), 0.011)

    private fun pos(e: TradeRepublicImport.Ergebnis, symbol: String): Position = e.konto.positions.single { it.key == symbol }

    // ------------------------------------------------------------ Trade Republic

    @Test(timeout = 20_000)
    fun tradeRepublic() {
        val text = res("tr_export.csv")
        assertEquals(Format.TRADE_REPUBLIC, BrokerExport.erkennen(text))
        val (e, b) = konto(Format.TRADE_REPUBLIC, text)
        val k = e.konto
        assertEquals(0, b.ungelesen)
        // 2000 - 501 - 100 + (240 - 1 - 3,5) + (12 - 3,17) + (1,5 - 0,4) - 25,4 + 3,5 - 1,23 - (9,88 + 1) - 100 = 1510,42
        assertEquals(1510.42, k.cash, 1e-6)
        pruefeSumme(e)
        assertEquals(2000.0, k.deposited, 1e-9)
        assertEquals(125.4, k.withdrawn, 1e-9)
        // Beispiel AG: 10 gekauft, 4 verkauft, Split bringt 6 ohne Einstand dazu
        val bsp = pos(e, "BSP.DE")
        assertEquals(12.0, bsp.qty, 1e-9)
        assertEquals(25.0, bsp.avgPrice, 1e-9)
        assertEquals(2.5, pos(e, "ETF.DE").qty, 1e-9)
        assertEquals(0.00012345, pos(e, "BTC-EUR").qty, 1e-12)
        assertEquals(15, k.transactions.size)
        assertEquals(40.0, k.transactions.single { it.type == TxType.SELL }.realized, 1e-9)
        assertEquals(3.5, k.transactions.single { it.type == TxType.SELL }.tax, 1e-9)
        assertEquals(3, k.transactions.count { it.type == TxType.FEE })
        assertEquals(8.83, k.transactions.single { it.type == TxType.DIVIDEND }.amount, 1e-9)
        assertEquals(3.17, k.transactions.single { it.type == TxType.DIVIDEND }.tax, 1e-9)
        // Steuerkorrektur (+3,50) und Vorabpauschale (nur Steuer, -1,23) stehen als Buchung da
        assertEquals(listOf(-1.23, 3.5), k.transactions.filter { it.type == TxType.TAX }.map { it.amount }.sorted())
        assertEquals(1.1, k.transactions.single { it.type == TxType.INTEREST }.amount, 1e-9)
        assertTrue(k.transactions.all { it.id.startsWith("tr-") })
        assertEquals(k.transactions.size, k.transactions.map { it.id }.toSet().size)
    }

    @Test(timeout = 20_000)
    fun tradeRepublicVarianten() {
        val basis = konto(Format.TRADE_REPUBLIC, res("tr_export.csv")).first.konto
        val text = res("tr_export.csv")
        // BOM, Unix-Zeilenenden, geaenderte Spaltenreihenfolge, neue Spalte vorn
        val varianten = listOf(
            "﻿" + text.replace("\r\n", "\n"),
            umgestellt(text, ','),
            umgestellt("﻿" + text, ','),
        )
        for (v in varianten) {
            assertEquals(Format.TRADE_REPUBLIC, BrokerExport.erkennen(v))
            val k = konto(Format.TRADE_REPUBLIC, v).first.konto
            assertEquals(basis.cash, k.cash, 1e-9)
            assertEquals(basis.transactions.size, k.transactions.size)
            assertEquals(basis.positions.map { it.key to it.qty }, k.positions.map { it.key to it.qty })
        }
    }

    // ------------------------------------------------------------ Scalable Capital

    @Test(timeout = 20_000)
    fun scalable() {
        val text = res("scalable_export.csv")
        assertEquals(Format.SCALABLE, BrokerExport.erkennen(text))
        val (e, b) = konto(Format.SCALABLE, text)
        val k = e.konto
        // Die Umbuchung von Stuecken steht in keiner bekannten Art und wird gemeldet, die stornierte Order nicht
        assertEquals(1, b.ungelesen)
        assertTrue(b.beispiele.single().contains("Security transfer"))
        // 2000 - 500,99 - 100 + (240 - 0,99 - 3,5) + (12 - 3,17) + (1,5 - 0,4) - 4 - 20,5 - 100
        assertEquals(1519.95, k.cash, 1e-6)
        pruefeSumme(e)
        assertEquals(2000.0, k.deposited, 1e-9)
        assertEquals(100.0, k.withdrawn, 1e-9)
        assertEquals(6.0, pos(e, "BSP.DE").qty, 1e-9)
        assertEquals(50.0, pos(e, "BSP.DE").avgPrice, 1e-9)
        // Sparplan-Ausfuehrungen mit Bruchstuecken: 2,5 zu 40 und 0,5 zu 41
        assertEquals(3.0, pos(e, "ETF.DE").qty, 1e-9)
        assertEquals(120.5 / 3, pos(e, "ETF.DE").avgPrice, 1e-9)
        assertEquals(40.0, k.transactions.single { it.type == TxType.SELL }.realized, 1e-9)
        assertEquals(2, k.transactions.count { it.type == TxType.FEE && it.tag == "FEE" && it.amount == -0.99 })
    }

    @Test(timeout = 20_000)
    fun scalableVarianten() {
        val text = res("scalable_export.csv")
        val basis = konto(Format.SCALABLE, text).first.konto
        val varianten = listOf(
            "﻿" + text.replace("\n", "\r\n"),
            umgestellt(text, ';'),
            // Dezimalpunkt und Tausenderkomma wie in der englischen Oberflaeche
            text.replace("2.000,00", "2,000.00").replace(Regex("(\\d),(\\d\\d)(?=;)"), "$1.$2").replace("2,5;", "2.5;").replace("0,5;", "0.5;"),
        )
        for (v in varianten) {
            assertEquals(Format.SCALABLE, BrokerExport.erkennen(v))
            val k = konto(Format.SCALABLE, v).first.konto
            assertEquals(basis.cash, k.cash, 1e-9)
            assertEquals(basis.positions.map { it.key to it.qty }, k.positions.map { it.key to it.qty })
        }
    }

    // ------------------------------------------------------------ Trading 212

    @Test(timeout = 20_000)
    fun trading212() {
        val text = res("trading212_export.csv")
        assertEquals(Format.TRADING212, BrokerExport.erkennen(text))
        val (e, b) = konto(Format.TRADING212, text)
        val k = e.konto
        assertEquals(0, b.ungelesen)
        // 1000 - 200,30 + 119,70 + 0,77 + 0,50 + 1,23 + 0,10 + 0,25 - 12 - 5 - 100
        assertEquals(805.25, k.cash, 1e-6)
        pruefeSumme(e)
        assertEquals(1000.0, k.deposited, 1e-9)
        assertEquals(112.0, k.withdrawn, 1e-9)
        // 2 gekauft, 1 verkauft, Aktiensplit 1 -> 2 haelt den Einstand von 100 auf 2 Stuecke
        assertEquals(2.0, pos(e, "USB").qty, 1e-9)
        assertEquals(50.0, pos(e, "USB").avgPrice, 1e-9)
        assertEquals(20.0, k.transactions.single { it.type == TxType.SELL && it.tag == "SELL" }.realized, 1e-9)
        // Quellensteuer in derselben Waehrung wie der Betrag gehoert zur Dividende, die in USD nicht
        val dividenden = k.transactions.filter { it.type == TxType.DIVIDEND }.sortedBy { it.ts }
        assertEquals(listOf(0.77, 0.5), dividenden.map { it.amount })
        assertEquals(listOf(0.15, 0.0), dividenden.map { it.tax })
        // Cashback zaehlt als Ertrag, nicht als Einzahlung
        assertEquals(3, k.transactions.count { it.type == TxType.INTEREST })
        assertEquals(1, k.transactions.count { it.type == TxType.FEE && it.title == "new card cost" })
    }

    @Test(timeout = 20_000)
    fun trading212NeueSpalteUndCrLf() {
        val text = res("trading212_export.csv")
        val basis = konto(Format.TRADING212, text).first.konto
        // Neue Spalte am Ende und Windows-Zeilenenden
        val neu = text.trim().lines().mapIndexed { i, z -> z + if (i == 0) ",Merchant name" else ",x" }.joinToString("\r\n")
        for (v in listOf(neu, "﻿$neu")) {
            assertEquals(Format.TRADING212, BrokerExport.erkennen(v))
            val k = konto(Format.TRADING212, v).first.konto
            assertEquals(basis.cash, k.cash, 1e-9)
            assertEquals(basis.transactions.size, k.transactions.size)
        }
    }

    // ------------------------------------------------------------ DEGIRO

    @Test(timeout = 20_000)
    fun degiroTransaktionen() {
        val text = res("degiro_transactions.csv")
        assertEquals(Format.DEGIRO, BrokerExport.erkennen(text))
        val (e, b) = konto(Format.DEGIRO, text)
        val k = e.konto
        assertEquals(0, b.ungelesen)
        // Gesamt EUR der drei Zeilen: -502 + 238 - 201,50
        assertEquals(-465.5, k.cash, 1e-6)
        pruefeSumme(e)
        assertEquals(6.0, pos(e, "BSP.DE").qty, 1e-9)
        assertEquals(50.0, pos(e, "BSP.DE").avgPrice, 1e-9)
        assertEquals(2.0, pos(e, "USB").qty, 1e-9)
        assertEquals(100.0, pos(e, "USB").avgPrice, 1e-9)
        assertEquals(40.0, k.transactions.single { it.type == TxType.SELL }.realized, 1e-9)
        assertEquals(2.0 + 2.0 + 1.5, -k.transactions.filter { it.type == TxType.FEE }.sumOf { it.amount }, 1e-9)
    }

    @Test(timeout = 20_000)
    fun degiroTransaktionenEnglischUndNiederlaendisch() {
        val basis = konto(Format.DEGIRO, res("degiro_transactions.csv")).first.konto
        val en = res("degiro_transactions.csv")
            .replace("Datum,Uhrzeit,Produkt,ISIN,Referenzbörse,Ausführungsort,Anzahl,Kurs,,Lokaler Wert,,Wert EUR,Wechselkurs,AutoFX-Gebühr,Transaktionsgebühren und/oder Fremdkosten EUR,Gesamt EUR,Order-ID",
                "Date,Time,Product,ISIN,Reference exchange,Venue,Quantity,Price,,Local value,,Value EUR,Exchange rate,AutoFX Fee,Transaction and/or third party fees EUR,Total EUR,Order ID")
        // Englische Dateien schreiben Dezimalpunkte
        val enPunkt = en.replace(Regex("\"(-?\\d+),(\\d+)\""), "\"$1.$2\"")
        val nl = res("degiro_transactions.csv")
            .replace("Datum,Uhrzeit,Produkt,ISIN,Referenzbörse,Ausführungsort,Anzahl,Kurs,,Lokaler Wert,,Wert EUR,Wechselkurs,AutoFX-Gebühr,Transaktionsgebühren und/oder Fremdkosten EUR,Gesamt EUR,Order-ID",
                "Datum,Tijd,Product,ISIN,Referentiebeurs,Uitvoeringsplaats,Aantal,Koers,,Lokale waarde,,Waarde EUR,Wisselkoers,AutoFX kosten,Transactiekosten en/of kosten van derden EUR,Totaal EUR,Order ID")
        for (v in listOf(enPunkt, nl)) {
            assertEquals(Format.DEGIRO, BrokerExport.erkennen(v))
            val k = konto(Format.DEGIRO, v).first.konto
            assertEquals(basis.cash, k.cash, 1e-6)
            assertEquals(basis.positions.map { it.key to it.qty }, k.positions.map { it.key to it.qty })
        }
    }

    @Test(timeout = 20_000)
    fun degiroKontoauszug() {
        val text = res("degiro_konto.csv")
        assertEquals(Format.DEGIRO_KONTO, BrokerExport.erkennen(text))
        val (e, b) = konto(Format.DEGIRO_KONTO, text)
        val k = e.konto
        // Die Zeile mit unbekanntem Sondertext wird gemeldet, Kauf-, Gebuehren- und Waehrungszeilen sind bewusst nicht dabei
        assertEquals(1, b.ungelesen)
        // 1000 + 0,77 (Dividende in USD mit dem Euro-Betrag des Wechsels) + 6 - 1,58 + 0,40 - 2,50 - 100
        assertEquals(903.09, k.cash, 1e-6)
        pruefeSumme(e)
        assertEquals(1000.0, k.deposited, 1e-9)
        assertEquals(100.0, k.withdrawn, 1e-9)
        assertEquals(listOf(0.77, 6.0), k.transactions.filter { it.type == TxType.DIVIDEND }.sortedBy { it.ts }.map { it.amount })
        assertEquals(-1.58, k.transactions.single { it.type == TxType.TAX }.amount, 1e-9)
    }

    @Test(timeout = 20_000)
    fun degiroKontoauszugMehrsprachig() {
        // Zeilen im Aufbau einer echten Datei mit gemischten Sprachen (Portugiesisch, Spanisch, Franzoesisch, Niederlaendisch)
        val text = """
            Datum,Tijd,Valutadatum,Product,ISIN,Omschrijving,FX,Mutatie,,Saldo,,Order Id
            02-01-2024,14:42,02-01-2024,BEISPIEL ETF,IE0000000003,Compra 1 BEISPIEL ETF@82,055 EUR (IE0000000003),,EUR,"-82,06",EUR,"2,54",a
            02-01-2024,14:42,02-01-2024,BEISPIEL ETF,IE0000000003,Comissões de transação DEGIRO e/ou taxas de terceiros,,EUR,-1.00,EUR,2.54,a
            03-01-2024,10:00,03-01-2024,BEISPIEL ETF,IE0000000003,Impôts sur dividende,,EUR,"-0,10",EUR,"2,44",
            03-01-2024,10:00,03-01-2024,BEISPIEL ETF,IE0000000003,Dividende,,EUR,"0,50",EUR,"2,94",
            04-01-2024,10:00,04-01-2024,,,Ingreso,,EUR,"100,00",EUR,"102,94",
            05-01-2024,10:00,05-01-2024,,,flatex terugstorting,,EUR,"-20,00",EUR,"82,94",
            06-01-2024,10:00,06-01-2024,,,Variation Fonds Monétaires (EUR),,EUR,"0,01",EUR,"82,95",
        """.trimIndent()
        assertEquals(Format.DEGIRO_KONTO, BrokerExport.erkennen(text))
        val (e, b) = konto(Format.DEGIRO_KONTO, text)
        assertEquals(0, b.ungelesen)
        assertEquals(100.0 - 20.0 + 0.5 - 0.1, e.konto.cash, 1e-9)
        assertEquals(100.0, e.konto.deposited, 1e-9)
    }

    // ------------------------------------------------------------ Interactive Brokers

    @Test(timeout = 20_000)
    fun ibkrFlexQuery() {
        val text = res("ibkr_flex.csv")
        assertEquals(Format.IBKR, BrokerExport.erkennen(text))
        val (e, b) = konto(Format.IBKR, text)
        val k = e.konto
        // Die Option wird gemeldet, die Zeile der Ebene ORDER ist eine Doppelung der Ausfuehrung und zaehlt nicht
        assertEquals(1, b.ungelesen)
        // Die Aktie in USD ohne FXRateToBase ist als Euro uebernommen und wird gemeldet
        assertEquals(1, b.ohneWechselkurs)
        val fx = 0.9091
        val erwartet = 2000.0 - 501.0 + (-550.0 - 1.10) * fx + 239.0 - 11.0 + (10.0 - 1.5) * fx + 0.5 - 3.0 - 100.0
        assertEquals(erwartet, k.cash, 0.011)
        pruefeSumme(e)
        assertEquals(2000.0, k.deposited, 1e-9)
        assertEquals(6.0, pos(e, "BSP.DE").qty, 1e-9)
        assertEquals(5.0, pos(e, "USB").qty, 1e-9)
        assertEquals(110.0 * fx, pos(e, "USB").avgPrice, 1e-3)
        // Ohne Kurs bei Yahoo bleibt der Wert aus dem Depot, steht aber mit seinen Buchungen da
        assertEquals(listOf("OHNE KURS INC"), e.ohneKurs.map { it.first })
        assertEquals(-1.5 * fx, k.transactions.single { it.type == TxType.TAX }.amount, 1e-9)
    }

    @Test(timeout = 20_000)
    fun ibkrMitHeaderDataUndVariante() {
        val text = res("ibkr_flex.csv")
        val basis = konto(Format.IBKR, text).first.konto
        // Dasselbe mit HEADER- und DATA-Spalte vorn
        val mit = text.trim().lines().mapIndexed { i, z ->
            val vorn = if (z.startsWith("\"ClientAccountID\"")) "\"HEADER\"," else "\"DATA\","
            "$vorn\"X\",$z"
        }.joinToString("\r\n")
        val a = konto(Format.IBKR, mit).first.konto
        assertEquals(basis.cash, a.cash, 1e-9)
        assertEquals(basis.transactions.size, a.transactions.size)
        val b = konto(Format.IBKR, umgestellt(text, ',')).first.konto
        assertEquals(basis.cash, b.cash, 1e-9)
    }

    // ------------------------------------------------------------ Revolut

    @Test(timeout = 20_000)
    fun revolut() {
        val text = res("revolut_invest.csv")
        assertEquals(Format.REVOLUT, BrokerExport.erkennen(text))
        val (e, b) = konto(Format.REVOLUT, text)
        val k = e.konto
        // Die Uebertragszeile zwischen den Revolut-Gesellschaften ist unbekannt und wird gemeldet
        assertEquals(1, b.ungelesen)
        // 1000 - 200 + 60 + 1 - 0,20 - 0,10 - 100 - 40
        assertEquals(720.7, k.cash, 1e-6)
        pruefeSumme(e)
        assertEquals(1000.0, k.deposited, 1e-9)
        assertEquals(100.0, k.withdrawn, 1e-9)
        // 2 gekauft, 0,5 verkauft, Split bringt 1,5 ohne Einstand dazu
        assertEquals(3.0, pos(e, "MSFT").qty, 1e-9)
        assertEquals(50.0, pos(e, "MSFT").avgPrice, 1e-9)
        assertEquals(1.0, pos(e, "ABC").qty, 1e-9)
        assertEquals(10.0, k.transactions.single { it.type == TxType.SELL }.realized, 1e-6)
        assertEquals(-0.2, k.transactions.single { it.type == TxType.TAX }.amount, 1e-9)
    }

    // ------------------------------------------------------------ ING und flatex (nur Orders)

    @Test(timeout = 20_000)
    fun ing() {
        val text = res("ing_orders.csv")
        assertEquals(Format.ING, BrokerExport.erkennen(text))
        val (e, b) = konto(Format.ING, text)
        val k = e.konto
        assertEquals(0, b.ungelesen)
        // Die stornierte Order zaehlt nicht; das Guthaben endet bei null, die Einzahlung ist aus den Orders errechnet
        assertEquals(0.0, k.cash, 1e-6)
        assertEquals(3260.0, k.deposited, 1e-6)
        assertEquals(6.0, pos(e, "BSP.DE").qty, 1e-9)
        assertEquals(2.5, pos(e, "USB").qty, 1e-9)
        assertEquals(1200.0, pos(e, "USB").avgPrice, 1e-9)
    }

    @Test(timeout = 20_000)
    fun flatex() {
        val text = res("flatex_umsaetze.csv")
        assertEquals(Format.FLATEX, BrokerExport.erkennen(text))
        val (e, b) = konto(Format.FLATEX, text)
        val k = e.konto
        assertEquals(0, b.ungelesen)
        assertEquals(240.0, k.cash, 1e-6)
        assertEquals(500.0, k.deposited, 1e-6)
        assertEquals(6.0, pos(e, "BSP.DE").qty, 1e-9)
        // Einlieferung ohne Kurs: Stuecke ohne Einstand
        assertEquals(3.0, pos(e, "USB").qty, 1e-9)
        assertEquals(0.0, pos(e, "USB").avgPrice, 1e-9)
    }

    // ------------------------------------------------------------ Bitpanda

    @Test(timeout = 20_000)
    fun bitpanda() {
        val text = res("bitpanda.csv")
        assertEquals(Format.BITPANDA, BrokerExport.erkennen(text))
        val (e, b) = konto(Format.BITPANDA, text)
        val k = e.konto
        // Der Eingang von ETH von aussen ist unbekannt und wird gemeldet
        assertEquals(1, b.ungelesen)
        // 1000 - 501,50 + 199,50 - 100
        assertEquals(598.0, k.cash, 1e-6)
        pruefeSumme(e)
        assertEquals(1000.0, k.deposited, 1e-9)
        assertEquals(100.0, k.withdrawn, 1e-9)
        val btc = pos(e, "BTC-EUR")
        assertEquals(0.00376, btc.qty, 1e-12)
        assertEquals(300.0 / 0.00376, btc.avgPrice, 1e-6)
        assertEquals(0.0025 * (80000.0 - 80000.0), k.transactions.single { it.type == TxType.SELL }.realized, 1e-9)
    }

    // ------------------------------------------------------------ nichts still uebersprungen

    @Test(timeout = 20_000)
    fun unlesbareZeilenWerdenGezaehlt() {
        // Zwei gute Zeilen, eine ohne lesbares Datum, eine mit unbekannter Art
        val text = """
            Orderart;Ordernummer;Ausführungsdatum;ISIN;Wertpapiername;Stück/Nominale;Ausführungskurs;Status
            Kauf;1;03.01.2025;DE0000000001;Beispiel AG;10;50,00;Ausgeführt
            Kauf;2;gestern;DE0000000001;Beispiel AG;10;50,00;Ausgeführt
            Tausch;3;05.01.2025;DE0000000001;Beispiel AG;10;50,00;Ausgeführt
            Verkauf;4;06.01.2025;DE0000000001;Beispiel AG;5;60,00;Ausgeführt
        """.trimIndent()
        val (z, b) = lesen(Format.ING, text)
        // Zwei Orders und die aus ihnen errechnete Einzahlung
        assertEquals(3, z.size)
        assertEquals(2, b.ungelesen)
        assertEquals(2, b.beispiele.size)
    }

    @Test(timeout = 20_000)
    fun leereDateiOhneZeilen() {
        val (z, b) = lesen(Format.SCALABLE, "date;time;status;reference;description;assetType;type;isin;shares;price;amount;fee;tax;currency")
        assertTrue(z.isEmpty())
        assertEquals(0, b.ungelesen)
    }

    // ------------------------------------------------------------ Zahlen und Daten

    @Test(timeout = 20_000)
    fun zahlenFormate() {
        assertEquals(1234.5, BrokerExport.zahl("1'234.50", false)!!, 1e-9)
        assertEquals(1234.5, BrokerExport.zahl("1 234,50", true)!!, 1e-9)
        assertEquals(-12.5, BrokerExport.zahl("(12,5)", true)!!, 1e-9)
        assertEquals(-12.5, BrokerExport.zahl("12,5-", true)!!, 1e-9)
        assertEquals(1234567.89, BrokerExport.zahl("1.234.567,89", true)!!, 1e-9)
        assertEquals(1234567.89, BrokerExport.zahl("1,234,567.89", false)!!, 1e-9)
        assertEquals(0.00012345, BrokerExport.zahl("0.00012345", false)!!, 1e-12)
        assertEquals(5.0, BrokerExport.zahl("5 %", true)!!, 1e-9)
        assertNull(BrokerExport.zahl("", true))
        assertNull(BrokerExport.zahl("n/a", true))
    }

    @Test(timeout = 20_000)
    fun datumsFormate() {
        val z = java.time.Instant.parse("2025-01-31T14:30:03Z").toEpochMilli()
        assertEquals(z, BrokerExport.zeit("2025-01-31T14:30:03Z"))
        assertEquals(z, BrokerExport.zeit("2025-01-31 14:30:03"))
        assertEquals(z, BrokerExport.zeit("31.01.2025 14:30:03"))
        assertEquals(z, BrokerExport.zeit("31/01/2025", "14:30:03"))
        // 31 als Monat gibt es nicht: nicht lesbar statt falsch gelesen
        assertEquals(0L, BrokerExport.zeit("01/31/2025"))
        assertEquals(0L, BrokerExport.zeit("gestern"))
    }

    // ------------------------------------------------------------ doppelter Import, Zusammenfuehren

    private val trZeilen get() = TradeRepublicImport.lesen(res("tr_export.csv"))

    @Test(timeout = 20_000)
    fun derselbeExportZweimalBuchtNichtDoppelt() {
        val z = trZeilen
        val erst = TradeRepublicImport.konto(z, kuerzel, Account(), jetzt).konto
        val zweit = TradeRepublicImport.vorschau(z, kuerzel, erst, jetzt)
        assertEquals(z.size, zweit.bekannt)
        val erg = zweit.ergaenzung!!
        assertEquals(erst.cash, erg.cash, 1e-9)
        assertEquals(erst.deposited, erg.deposited, 1e-9)
        assertEquals(erst.transactions.size, erg.transactions.size)
        assertEquals(erst.positions.map { it.key to it.qty }, erg.positions.map { it.key to it.qty })
    }

    @Test(timeout = 20_000)
    fun laengererZeitraumErgaenztNurDasNeue() {
        val z = trZeilen
        // Erster Export bis zum Verkauf, zweiter Export mit allem
        val frueh = z.filter { it.zeit < java.time.Instant.parse("2025-01-12T00:00:00Z").toEpochMilli() }
        val erst = TradeRepublicImport.konto(frueh, kuerzel, Account(), jetzt).konto
        val voll = TradeRepublicImport.konto(z, kuerzel, Account(), jetzt).konto
        val schritt = TradeRepublicImport.vorschau(z, kuerzel, erst, jetzt)
        assertEquals(frueh.size, schritt.bekannt)
        val erg = schritt.ergaenzung!!
        assertEquals(voll.cash, erg.cash, 1e-6)
        assertEquals(voll.deposited, erg.deposited, 1e-9)
        assertEquals(voll.withdrawn, erg.withdrawn, 1e-9)
        assertEquals(voll.transactions.size, erg.transactions.size)
        assertEquals(voll.positions.associate { it.key to it.qty }, erg.positions.associate { it.key to it.qty })
        assertEquals(voll.positions.single { it.key == "BSP.DE" }.avgPrice, erg.positions.single { it.key == "BSP.DE" }.avgPrice, 1e-9)
    }

    @Test(timeout = 20_000)
    fun ohneBekannteZeilenKeineErgaenzung() {
        val e = TradeRepublicImport.vorschau(trZeilen, kuerzel, Account(), jetzt)
        assertEquals(0, e.bekannt)
        assertNull(e.ergaenzung)
    }

    @Test(timeout = 20_000)
    fun importInDepotMitBestand() {
        val vorher = Account(
            cash = 500.0, deposited = 500.0,
            positions = listOf(Position("BSP.DE", "Beispiel AG", "BSP.DE", 3.0, 40.0, 1_700_000_000_000L)),
        )
        val e = TradeRepublicImport.konto(trZeilen, kuerzel, Account(), jetzt)
        val zusammen = Zusammenfuehren.konten(vorher, e.konto)
        assertEquals(500.0 + 1510.42, zusammen.cash, 1e-6)
        assertEquals(2500.0, zusammen.deposited, 1e-9)
        val p = zusammen.positions.single { it.key == "BSP.DE" }
        assertEquals(15.0, p.qty, 1e-9)
        assertEquals((3 * 40.0 + 12 * 25.0) / 15, p.avgPrice, 1e-9)
        assertNotNull(zusammen.positions.singleOrNull { it.key == "ETF.DE" })
    }

    @Test(timeout = 20_000)
    fun kennungenBleibenBeiGeaendertemExport() {
        fun ids(f: Format, t: String) = BrokerExport.lesen(f, t).map { it.id }.sorted()
        val sc = res("scalable_export.csv")
        assertEquals(ids(Format.SCALABLE, sc), ids(Format.SCALABLE, umgestellt(sc, ';')))
        val t = res("trading212_export.csv")
        val neu = t.trim().lines().mapIndexed { i, z -> z + if (i == 0) ",Merchant name" else ",x" }.joinToString("\n")
        assertEquals(ids(Format.TRADING212, t), ids(Format.TRADING212, neu))
        val ib = res("ibkr_flex.csv")
        assertEquals(ids(Format.IBKR, ib), ids(Format.IBKR, umgestellt(ib, ',')))
        // Zwei gleiche Vorgaenge in einer Datei bleiben zwei Buchungen
        val doppelt = BrokerExport.lesen(Format.FLATEX, res("flatex_umsaetze.csv") + "03.01.2025;DE0000000001;Beispiel AG;10;Kauf;111;50,00\n")
        // vier Orders und die aus ihnen errechnete Einzahlung
        assertEquals(5, doppelt.size)
        assertEquals(5, doppelt.map { it.id }.toSet().size)
    }

    // Kehrt die Spalten um und setzt eine neue Spalte nach vorn.
    private fun umgestellt(text: String, trenner: Char): String {
        fun feld(s: String) = if (s.any { it == trenner || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
        val z = TradeRepublicImport.csv(text.trimStart(Char(0xFEFF)), trenner)
        return z.mapIndexed { i, r -> (listOf(if (i == 0) "neue_spalte" else "x") + r.reversed()).joinToString(trenner.toString()) { feld(it) } }.joinToString("\n")
    }
}
