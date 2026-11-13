package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.Backup
import de.troetenrepublik.app.data.Derivative
import de.troetenrepublik.app.data.DerivativeKind
import de.troetenrepublik.app.data.DepotRegeln
import de.troetenrepublik.app.data.Einzahlplan
import de.troetenrepublik.app.data.Lot
import de.troetenrepublik.app.data.Order
import de.troetenrepublik.app.data.OrderStatus
import de.troetenrepublik.app.data.OrderType
import de.troetenrepublik.app.data.Position
import de.troetenrepublik.app.data.PriceAlert
import de.troetenrepublik.app.data.SavingsPlan
import de.troetenrepublik.app.data.Settings
import de.troetenrepublik.app.data.Side
import de.troetenrepublik.app.data.Snapshot
import de.troetenrepublik.app.data.These
import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.data.WatchMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneOffset

// Was nach einem Import stimmen muss (Export fuer Portfolio Performance, Leistungs-Werkzeug) und die Rundreise der Depotdatei.
class ImportFolgenTest {

    private val jetzt = 1_900_000_000_000L
    private val kuerzel = mapOf("DE0000000001" to "BSP.DE", "IE0000000003" to "ETF.DE", "BTC" to "BTC-EUR")
    private val isinJe = mapOf("BSP.DE" to "DE0000000001", "ETF.DE" to "IE0000000003")

    private fun res(name: String) = javaClass.getResourceAsStream("/import/$name")!!.readBytes().toString(Charsets.UTF_8)

    private fun trKonto() = TradeRepublicImport.konto(TradeRepublicImport.lesen(res("tr_export.csv")), kuerzel, Account(), jetzt).konto

    // ------------------------------------------------------------ Export fuer Portfolio Performance

    private val typenEn = setOf("Deposit", "Withdrawal", "Buy", "Sell", "Dividend", "Interest", "Interest Charge", "Fees", "Fees Refund", "Taxes", "Tax Refund")
    private val typenDe = setOf("Einlage", "Entnahme", "Kauf", "Verkauf", "Dividende", "Zinsen", "Zinsbelastung", "Gebühren", "Gebührenerstattung", "Steuern", "Steuerrückerstattung")

    // Liest die Datei, wie Portfolio Performance Kontoumsaetze liest: Guthaben und Stuecke je Wertpapier.
    private class Hauptbuch(val cash: Double, val stueck: Map<String, Double>, val typen: Set<String>, val zeilen: Int)

    private fun lesen(csv: String, deutsch: Boolean): Hauptbuch {
        val trenner = if (deutsch) ';' else ','
        val z = TradeRepublicImport.csv(csv, trenner)
        val kopf = z[0]
        fun i(n: String) = kopf.indexOf(n)
        val iTyp = i(if (deutsch) "Typ" else "Type"); val iWert = i(if (deutsch) "Wert" else "Value")
        val iStueck = i(if (deutsch) "Stück" else "Shares"); val iIsin = i("ISIN")
        val iTicker = i(if (deutsch) "Ticker-Symbol" else "Ticker Symbol"); val iName = i(if (deutsch) "Wertpapiername" else "Security Name")
        fun zahl(s: String) = s.replace(',', '.').toDouble()
        var cash = 0.0
        val stueck = HashMap<String, Double>()
        val typen = HashSet<String>()
        for (r in z.drop(1)) {
            val typ = r[iTyp]; typen += typ
            val wert = zahl(r[iWert])
            val menge = r[iStueck].takeIf { it.isNotBlank() }?.let { zahl(it) } ?: 0.0
            val schluessel = r[iIsin].ifBlank { r[iTicker] }.ifBlank { r[iName] }
            when (typ) {
                "Deposit", "Einlage", "Dividend", "Dividende", "Interest", "Zinsen", "Fees Refund", "Gebührenerstattung", "Tax Refund", "Steuerrückerstattung" -> cash += wert
                "Withdrawal", "Entnahme", "Interest Charge", "Zinsbelastung", "Fees", "Gebühren", "Taxes", "Steuern" -> cash -= wert
                "Buy", "Kauf" -> { cash -= wert; stueck[schluessel] = (stueck[schluessel] ?: 0.0) + menge }
                "Sell", "Verkauf" -> { cash += wert; stueck[schluessel] = (stueck[schluessel] ?: 0.0) - menge }
                else -> throw AssertionError("Unbekannte Art $typ")
            }
        }
        return Hauptbuch(cash, stueck, typen, z.size - 1)
    }

    @Test(timeout = 20_000)
    fun portfolioPerformanceBestandUndKontostandGehenAuf() {
        val k = trKonto()
        for (deutsch in listOf(false, true)) {
            val csv = PortfolioPerformance.csv(k, { isinJe[it].orEmpty() }, deutsch, ZoneOffset.UTC)
            val b = lesen(csv, deutsch)
            assertEquals(PortfolioPerformance.anzahl(k), b.zeilen)
            // Die Summe der Vorgaenge ergibt das Guthaben der App ...
            assertEquals(k.cash, b.cash, 0.011)
            // ... und den Bestand je Wertpapier
            for (p in k.positions) {
                val schluessel = isinJe[p.symbol] ?: p.symbol
                assertEquals("Bestand ${p.symbol}", p.qty, b.stueck[schluessel] ?: 0.0, 1e-8)
            }
            assertTrue(b.stueck.filterValues { it > 1e-9 }.size == k.positions.size)
            assertTrue((if (deutsch) typenDe else typenEn).containsAll(b.typen))
        }
    }

    @Test(timeout = 20_000)
    fun portfolioPerformanceKryptoMitAchtStellenUndGebuehrZurOrder() {
        val k = trKonto()
        val csv = PortfolioPerformance.csv(k, { isinJe[it].orEmpty() }, false, ZoneOffset.UTC)
        val zeilen = csv.trim().split("\r\n").drop(1)
        // 0,00012345 Bitcoin bleiben vollstaendig stehen (nicht auf sechs Stellen gerundet)
        assertTrue(zeilen.any { it.contains(",Buy,") && it.contains(",0.00012345,") })
        // Die Gebuehr der Order mit ISIN steht beim Kauf und nicht als eigene Zeile
        val kauf = zeilen.single { it.contains(",Buy,") && it.contains(",501,EUR,10,") && it.contains("DE0000000001") }
        assertTrue(kauf.contains(",1,"))
        // Auch die Gebuehr des Kryptokaufs steht bei der Order (Wert 9,88 + 1), nicht als eigene Zeile
        assertTrue(zeilen.any { it.contains(",Buy,10.88,EUR,0.00012345,") })
        assertEquals(0, zeilen.count { it.contains(",Fees,") })
    }

    @Test(timeout = 20_000)
    fun portfolioPerformanceNegativeZinsenUndDividendenkorrektur() {
        val konto = Account(transactions = listOf(
            Transaction("a", 1_000_000L, TxType.DEPOSIT, "", amount = 100.0),
            Transaction("z", 2_000_000L, TxType.INTEREST, "Zinsen", amount = -1.5, tag = "INTEREST"),
            Transaction("d", 3_000_000L, TxType.DIVIDEND, "Muster AG", amount = -2.0, symbol = "MUS.DE", key = "MUS.DE", tag = "DIVIDEND"),
        ))
        val csv = PortfolioPerformance.csv(konto, { "" }, false, ZoneOffset.UTC)
        val b = lesen(csv, false)
        // Negative Betraege behalten ihr Vorzeichen
        assertEquals(100.0 - 1.5 - 2.0, b.cash, 1e-9)
        assertTrue("Interest Charge" in b.typen)
    }

    // ------------------------------------------------------------ Leistungs-Werkzeug nach einem Import

    @Test(timeout = 20_000)
    fun leistungNachImportGegenHandrechnung() {
        fun z(t: String, typ: String, sh: String, pr: String, am: String, fee: String = "", tax: String = "", kat: String = "TRADING", kl: String = "STOCK", i: String) =
            "\"$t\",\"${t.take(10)}\",\"DEFAULT\",\"$kat\",\"$typ\",\"$kl\",\"Beispiel AG\",\"DE0000000001\",\"$sh\",\"$pr\",\"$am\",\"$fee\",\"$tax\",\"EUR\",\"\",\"\",\"\",\"\",\"$i\",\"\",\"\",\"\",\"\""
        val kopf = "\"datetime\",\"date\",\"account_type\",\"category\",\"type\",\"asset_class\",\"name\",\"symbol\",\"shares\",\"price\",\"amount\",\"fee\",\"tax\",\"currency\",\"original_amount\",\"original_currency\",\"fx_rate\",\"description\",\"transaction_id\",\"counterparty_name\",\"counterparty_iban\",\"payment_reference\",\"mcc_code\""
        val csv = listOf(
            kopf,
            z("2025-01-02T09:00:00Z", "CUSTOMER_INPAYMENT", "", "", "1000", kat = "CASH", kl = "", i = "a").replace("\"Beispiel AG\",\"DE0000000001\"", "\"\",\"\""),
            z("2025-01-03T10:00:00Z", "BUY", "10", "50", "-500", "-1", i = "b"),
            z("2025-01-10T11:00:00Z", "SELL", "-4", "60", "240", "-1", "-3.5", i = "c"),
        ).joinToString("\n")
        val konto = TradeRepublicImport.konto(TradeRepublicImport.lesen(csv), mapOf("DE0000000001" to "BSP.DE"), Account(), jetzt).konto
        assertEquals(734.5, konto.cash, 1e-9)
        val t = { s: String -> java.time.Instant.parse(s).toEpochMilli() }
        // Wert der Wertpapiere zu festen Kursen 50, 55, 60 und 65 (nach dem Verkauf sechs Stueck)
        val reihe = listOf(
            t("2025-01-03T12:00:00Z") to 500.0, t("2025-01-06T12:00:00Z") to 550.0,
            t("2025-01-10T12:00:00Z") to 360.0, t("2025-01-15T12:00:00Z") to 390.0,
        )
        val e = Performanceanalyse.berechnen(reihe, konto.transactions, konto.cash, 0L, t("2025-01-15T12:00:00Z"), ZoneOffset.UTC)!!
        // Gesamtwert 999, 1049, 1094,50, 1124,50; kein Geld von aussen im Zeitraum. Seit 0.77 beginnt der Zeitraum vor
        // dem Kauf am Tag des ersten Kurspunkts (1000 Guthaben), die Kaufgebuehr zaehlt mit
        val erwartet = 1124.5 / 1000.0 - 1
        assertEquals(erwartet, e.ttwror, 1e-9)
        assertEquals(erwartet, e.monate.getValue(YearMonth.of(2025, 1)), 1e-9)
        assertEquals(erwartet, e.jahre.getValue(2025), 1e-9)
        val izf = e.izf
        assertNotNull(izf)
        assertEquals(erwartet, izf!!, 1e-6)
        val bz = e.bilanz
        assertEquals(1000.0, bz.anfang, 1e-9)
        assertEquals(1124.5, bz.ende, 1e-9)
        assertEquals(40.0, bz.realisiert, 1e-9)
        assertEquals(2.0, bz.gebuehren, 1e-9)
        assertEquals(3.5, bz.steuern, 1e-9)
        assertEquals(0.0, bz.einlagen, 1e-9)
        // Gewinn vor Kosten: 4 x 10 realisiert + 6 x 15 nicht realisiert
        assertEquals(130.0, bz.kursgewinne, 1e-9)
        assertEquals(1124.5 - 1000.0, bz.absolut, 1e-9)
    }

    // ------------------------------------------------------------ Depotdatei

    private fun vollesDepot(): Account {
        val ko = Derivative("ko-1", "BSP.DE", "Beispiel AG", DerivativeKind.KO_LONG, strike = 40.0, barrier = 42.0, ratio = 0.1, createdAt = 5L)
        return Account(
            cash = 1234.56, deposited = 5000.0, withdrawn = 100.0, transferredIn = 250.0, interestAccrued = 0.12,
            positions = listOf(
                Position("BSP.DE", "Beispiel AG", "BSP.DE", 12.5, 49.5, 1_700_000_000_000L, importedAt = 1_760_000_000_000L,
                    lots = listOf(Lot(10.0, 50.0, 1L, 1.0), Lot(2.5, 45.0, 2L)), art = "STOCK"),
                Position("ko-1", "Turbo Long", "BSP.DE", 100.0, 0.8, 1_700_000_100_000L, ko),
            ),
            orders = listOf(Order("o1", "BSP.DE", "Beispiel AG", "BSP.DE", Side.BUY, OrderType.LIMIT, 3.0, 40.0, OrderStatus.OPEN, createdAt = 9L, note = "Limit")),
            transactions = listOf(
                Transaction("t1", 1L, TxType.DEPOSIT, "Einzahlung", amount = 5000.0, tag = "DEPOSIT"),
                Transaction("t2", 2L, TxType.BUY, "Beispiel AG", amount = -500.0, qty = 10.0, price = 50.0, symbol = "BSP.DE", key = "BSP.DE", fee = 1.0, tag = "BUY", via = "IMPORT_TR", importedAt = 7L),
                Transaction("t3", 3L, TxType.BUY, "Turbo Long", amount = -80.0, qty = 100.0, price = 0.8, symbol = "BSP.DE", key = "ko-1", derivative = ko),
            ),
            history = listOf(Snapshot(1L, 5000.0, 0.0, 0.0), Snapshot(2L, 5100.0, 500.0, 100.0)),
            ersteListe = listOf("SAP.DE", "AAPL"),
            plans = listOf(SavingsPlan("p1", "ETF.DE", "Welt ETF", 100.0, 15, invested = 300.0, executions = 3, uhrzeit = 600)),
            createdAt = 1L, lastMaintainDay = 20_000L, lastInterestDay = 20_001L,
            alerts = listOf(PriceAlert("al1", "SAP.DE", "SAP", 200.0, true, 4L, 190.0), PriceAlert("al2", "AAPL", "Apple", 0.0, true, tagesProzent = 5.0, gemeldetAm = 20_000L)),
            watchMeta = mapOf("AAPL" to WatchMeta(3L, 150.0, 2L, 140.0)),
            notizen = mapOf("BSP.DE" to "Kaufgrund: Dividende\nZweite Zeile"),
            thesen = mapOf("BSP.DE" to These("Ausstieg unter 40", 25.0, 30L, 3L, 4L)),
            depotRegeln = DepotRegeln(20.0, true, 100.0),
            einzahlplan = Einzahlplan(200.0, 1, false, 24_000, 5L, 4, 800.0),
            wiederanlage = mapOf("BSP.DE" to true, "ETF.DE" to false),
            herkunftWahl = mapOf("BSP.DE" to "DE", "ETF.DE" to "IE"),
        )
    }

    @Test(timeout = 20_000)
    fun rundreiseAllerFelder() {
        val konto = vollesDepot()
        val text = Backup.export(konto, Settings(ownerName = "Alex", interestBps = 250, allowance = 801.0, land = "DE"), 123L)
        val p = (Backup.parse(text) as Backup.Outcome.Ok).payload
        // Alle Felder, auch Derivate, Sparplaene, Orders, Thesen, Notizen, Alarme, Wahl je Wert und die Land-Zuordnung
        assertEquals(konto, p.account)
        assertEquals("Alex", p.settings.ownerName)
        assertEquals(250, p.settings.interestBps)
        assertEquals(801.0, p.settings.allowance, 0.0)
        assertEquals("DE", p.settings.land)
        // Zweite Runde aendert nichts mehr
        assertEquals(text, Backup.export(p.account, Settings(ownerName = "Alex", interestBps = 250, allowance = 801.0, land = "DE"), 123L))
    }

    @Test(timeout = 20_000)
    fun alteDateifassungBleibtLesbar() {
        val p = (Backup.parse(res("depot_alt_5_0.json")) as Backup.Outcome.Ok).payload
        assertEquals(8123.45, p.account.cash, 1e-9)
        assertEquals(2, p.account.positions.size)
        assertEquals(DerivativeKind.KO_LONG, p.account.positions[1].derivative!!.kind)
        // Felder, die es damals nicht gab, haben Vorgaben
        assertTrue(p.account.notizen.isEmpty() && p.account.thesen.isEmpty() && p.account.herkunftWahl.isEmpty() && p.account.wiederanlage.isEmpty())
        assertEquals("", p.account.positions[0].art)
        assertTrue(p.account.positions[0].lots.isEmpty())
        assertEquals(0L, p.account.transactions[1].importedAt)
        assertEquals("SINCE_BUY_PCT", p.settings.metric)
        assertEquals("Alex", p.settings.ownerName)
        // Ein spaeterer Export derselben Daten bleibt lesbar
        val neu = (Backup.parse(Backup.export(p.account, Settings(), 1L)) as Backup.Outcome.Ok).payload
        assertEquals(p.account, neu.account)
    }

    @Test(timeout = 20_000)
    fun beschaedigteDateienGebenKlareMeldung() {
        val text = Backup.export(vollesDepot(), Settings(), 123L)
        // Abgeschnitten an mehreren Stellen
        for (teil in listOf(0.1, 0.5, 0.9, 0.999)) {
            val r = Backup.parse(text.substring(0, (text.length * teil).toInt()))
            assertTrue("Abgeschnitten bei $teil", r is Backup.Outcome.Error && r.code == "NOT_READABLE")
        }
        assertTrue(Backup.parse("") is Backup.Outcome.Error)
        assertTrue(Backup.parse("{}") is Backup.Outcome.Error)
        // Neuere Fassung der Datei
        val neuer = text.replace("\"format\": 2", "\"format\": 99")
        val n = Backup.parse(neuer)
        assertTrue(n is Backup.Outcome.Error && n.code == "NEWER" && n.extra == 99)
        // Fremde Datei und unbrauchbare Werte
        assertTrue((Backup.parse("{\"app\":\"anderes\",\"account\":{}}") as Backup.Outcome.Error).code == "FOREIGN")
        assertTrue((Backup.parse(text.replace("\"cash\": 1234.56", "\"cash\": -5.0")) as Backup.Outcome.Error).code == "INVALID_CASH")
        assertTrue((Backup.parse(text.replace("\"qty\": 12.5", "\"qty\": 0.0")) as Backup.Outcome.Error).code == "INVALID_POSITION")
    }

    @Test(timeout = 20_000)
    fun dateiMitByteOrderMarkWirdGelesen() {
        val text = Backup.export(vollesDepot(), Settings(), 123L)
        val p = Backup.parse("﻿" + text.replace("\n", "\r\n"))
        assertTrue(p is Backup.Outcome.Ok)
        assertEquals(vollesDepot(), (p as Backup.Outcome.Ok).payload.account)
    }

    @Test(timeout = 20_000)
    fun zusammenfuehrenMitDerselbenDatei() {
        // Die eigene Sicherung noch einmal zum selben Depot zusammenzufuehren, verdoppelt keine Buchung
        val a = vollesDepot()
        val z = Zusammenfuehren.konten(a, a)
        assertEquals(a.transactions.size, z.transactions.size)
        assertEquals(a.orders.size, z.orders.size)
        assertEquals(a.plans.size, z.plans.size)
        assertEquals(a.alerts.size, z.alerts.size)
    }
}
