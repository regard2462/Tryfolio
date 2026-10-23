package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Derivative
import de.troetenrepublik.app.data.DerivativeKind
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.data.Order
import de.troetenrepublik.app.data.OrderStatus
import de.troetenrepublik.app.data.OrderType
import de.troetenrepublik.app.data.Position
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.data.Side
import de.troetenrepublik.app.data.TaxState
import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.Strings
import de.troetenrepublik.app.ui.Fmt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

class Features14Test {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private fun at(date: String, hour: Int = 12, minute: Int = 0): Long =
        LocalDate.parse(date).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    // Mittwoch, 4. Maerz 2026, 12:00, Boerse offen
    private val now = at("2026-03-04")

    // US-Wert: 200 USD = 170 EUR, also fx 0,85 EUR je USD.
    private fun usQuote(usd: Double, at: Long = now, prevUsd: Double = usd) =
        Quote("NVDA", "NVIDIA", price = usd * 0.85, prevClose = prevUsd * 0.85, nativeCurrency = "USD", nativePrice = usd, updatedAt = at, marketState = "REGULAR")

    private fun eurQuote(price: Double, at: Long = now) =
        Quote("SAP.DE", "SAP", price = price, prevClose = price, nativeCurrency = "EUR", nativePrice = price, updatedAt = at, marketState = "REGULAR")

    // Derivate in Handelswaehrung

    @Test fun knockoutStrikeIsInNativeCurrencyAndPricedInEuro() {
        val d = Derivatives.knockout("NVDA", "NVIDIA", spot = 200.0, ccy = "USD", long = true, leverage = 10.0, now = now)
        assertEquals("USD", d.ccy)
        assertEquals(180.0, d.strike, 0.5)                       // 200 * (1 - 1/10)
        assertTrue(d.barrier > d.strike)
        val q = usQuote(200.0)
        val price = Pricing.price(d, q.price, now, q.fx)
        // innerer Wert 20 USD = 17 EUR, geteilt durch Bezugsverhaeltnis
        assertEquals(17.0 / d.ratio, price, 0.05 / d.ratio + 0.2)
        val lev = Pricing.leverage(d, q.price, now, q.fx)
        assertEquals(10.0, lev, 0.6)
    }

    @Test fun customLeverageWithDecimalsCreatesDistinctProduct() {
        val a = Derivatives.knockout("SAP.DE", "SAP", 100.0, "EUR", true, 37.5, now)
        val b = Derivatives.knockout("SAP.DE", "SAP", 100.0, "EUR", true, 37.0, now)
        assertTrue(a.id != b.id)
        assertEquals(37.5, a.nominalLeverage, 1e-9)
        assertEquals(1.0, Derivatives.knockout("SAP.DE", "SAP", 100.0, "EUR", true, 0.2, now).nominalLeverage, 1e-9)
        // Eigene Hebel duerfen ueber die Liste (200) hinausgehen, bis 1000; die Schwelle bleibt vor dem Kurs
        val big = Derivatives.knockout("SAP.DE", "SAP", 100.0, "EUR", true, 999.0, now)
        assertEquals(999.0, big.nominalLeverage, 1e-9)
        assertTrue(big.strike < big.barrier && big.barrier < 100.0)
        assertFalse(Pricing.isKnockedOut(big, 100.0))
        assertEquals(1000.0, Derivatives.knockout("SAP.DE", "SAP", 100.0, "EUR", true, 5000.0, now).nominalLeverage, 1e-9)
        val short = Derivatives.knockout("SAP.DE", "SAP", 100.0, "EUR", false, 1000.0, now)
        assertTrue(short.barrier > 100.0 && !Pricing.isKnockedOut(short, 100.0))
        assertTrue(Pricing.leverage(big, 100.0, now) > 500.0)
    }

    @Test fun factorsStopAtTwenty() {
        val all = Derivatives.factors("SAP.DE", "SAP", 100.0, now = now)
        assertTrue(all.all { abs(it.factor) <= Derivatives.MAX_FACTOR })
        assertTrue(all.any { it.factor == 20.0 } && all.any { it.factor == -20.0 })
        assertEquals(20, Derivatives.MAX_FACTOR)
    }

    @Test fun warrantsHaveIssuersMaturitiesAndDeltaRange() {
        val calls = Derivatives.warrants("NVDA", "NVIDIA", 200.0, "USD", 0.4, call = true, now = now)
        val puts = Derivatives.warrants("NVDA", "NVIDIA", 200.0, "USD", 0.4, call = false, now = now)
        assertTrue(calls.size > 500)
        // Einziger Emittent ist das Haus; die Kennungen tragen sein Kuerzel
        assertTrue(calls.all { it.kind == DerivativeKind.CALL && it.ccy == "USD" && it.expiry > now && it.issuer == Derivatives.HOUSE })
        assertTrue(calls.all { Derivatives.isinFor(it).startsWith("DE000TR") && Derivatives.isinFor(it).length == 12 })
        assertTrue(calls.map { it.expiry }.distinct().size >= 20)
        val q = usQuote(200.0)
        val deltas = calls.map { Pricing.greeks(it, q.price, now, q.fx)!!.delta }
        assertTrue(deltas.all { it in 0.0..1.0 })
        assertTrue(deltas.any { it < 0.1 } && deltas.any { it > 0.9 })
        val putDeltas = puts.map { Pricing.greeks(it, q.price, now, q.fx)!!.delta }
        assertTrue(putDeltas.all { it in -1.0..0.0 })
        // Strikes sind glatte Betraege in Handelswaehrung, von etwa der Haelfte bis zum Zweieinhalbfachen
        assertTrue(calls.all { it.strike >= 100.0 && it.strike <= 505.0 })
        assertTrue(calls.all { abs(it.strike / 5.0 - Math.round(it.strike / 5.0)) < 1e-6 })
        // Kennungen eindeutig
        assertEquals(calls.size, calls.map { it.id }.distinct().size)
        // Bezugsverhaeltnis 10 Scheine je Aktie: ein Schein am Geld kostet einen Bruchteil des Kurses
        val atm = calls.filter { it.strike == 200.0 }.maxBy { it.expiry }
        assertEquals(10.0, atm.ratio, 1e-9)
        val atmPrice = Pricing.price(atm, q.price, now, q.fx)
        assertTrue("Preis $atmPrice", atmPrice > 0.2 && atmPrice < 10.0)
    }

    @Test fun warrantLabelUsesNativeCurrency() {
        val d = Derivatives.warrants("NVDA", "NVIDIA", 200.0, "USD", 0.4, true, now).first { it.strike == 250.0 }
        assertEquals("Call 250,00 $", Derivatives.label(d, java.util.Locale.GERMANY))
        assertEquals("Call $250.00", Derivatives.label(d, java.util.Locale.UK))
    }

    // Knock-out: sichtbar, dann abgerechnet

    @Test fun knockoutStaysVisibleWithResidualAndSettlesAfterTwoDays() {
        val d = Derivatives.knockout("SAP.DE", "SAP", 100.0, "EUR", true, 10.0, now)
        val pos = Position(d.id, "Long", "SAP.DE", 100.0, 1.0, now - 86_400_000L, d)
        val acc = Account(cash = 10.0, positions = listOf(pos), tax = TaxState(year = 2026))
        // Kurs faellt unter die Schwelle
        val hit = Trading.maintain(acc, mapOf("SAP.DE" to eurQuote(d.barrier - 1.0)), now)
        val koPos = hit.positions.single()
        assertTrue(koPos.isKnockedOut)
        assertEquals(now, koPos.derivative!!.knockedOutAt)
        assertTrue(hit.transactions.isEmpty())
        // Bewertung mit Restwert; Verlust fast vollstaendig
        val v = Trading.value(hit, mapOf("SAP.DE" to eurQuote(d.barrier - 1.0)), now)
        assertEquals(Pricing.KO_RESIDUAL * 100.0, v.positions.single().value, 1e-9)
        assertTrue(v.positions.single().profitPct < -99.0)
        // Nach einem Tag noch da, nach zwei Tagen abgerechnet
        val later1 = Trading.maintain(hit, mapOf("SAP.DE" to eurQuote(50.0, now + 86_400_000L)), now + 86_400_000L)
        assertEquals(1, later1.positions.size)
        val later2 = Trading.maintain(hit, mapOf("SAP.DE" to eurQuote(50.0, now + Trading.KO_SETTLE_MS)), now + Trading.KO_SETTLE_MS)
        assertTrue(later2.positions.isEmpty())
        val tx = later2.transactions.single()
        assertEquals(TxType.KNOCKOUT, tx.type)
        assertEquals("KO_SETTLE", tx.tag)
        assertEquals(0.1, tx.amount, 1e-9)                       // 100 x 0,001
        assertEquals(0.1 - 100.0, tx.realized, 1e-9)
        assertEquals(10.1, later2.cash, 1e-9)
        assertEquals(99.9, later2.tax.lossPotGeneral, 1e-9)      // Verlust im Topf Sonstige
    }

    @Test fun knockedOutProductCannotBeBoughtOrSold() {
        val d = Derivatives.knockout("SAP.DE", "SAP", 100.0, "EUR", true, 10.0, now).copy(knockedOutAt = now)
        val acc = Account(cash = 1000.0, positions = listOf(Position(d.id, "Long", "SAP.DE", 10.0, 1.0, now, d)))
        assertEquals(Err.NOT_TRADABLE, Trading.buy(acc, d.id, "Long", "SAP.DE", 1.0, 1.0, d, now).error)
        assertEquals(Err.NOT_TRADABLE, Trading.sell(acc, d.id, 1.0, 1.0, now).error)
    }

    // Trailing Stop

    @Test fun trailingStopFollowsHighAndTriggersOnPullback() {
        val base = Order("t", "SAP.DE", "SAP", "SAP.DE", Side.SELL, OrderType.TRAILING_STOP, 5.0, createdAt = now, trailPct = 10.0)
        val order = Trading.trailingStop(base, 100.0)
        assertEquals(90.0, order.limitPrice, 1e-9)
        var acc = Trading.placeOrder(Account(cash = 0.0, positions = listOf(Position("SAP.DE", "SAP", "SAP.DE", 5.0, 80.0, now - 1))), order)
        // Kurs steigt: Stop zieht nach
        acc = Trading.runOrders(acc, mapOf("SAP.DE" to eurQuote(120.0)), now, respectHours = false)
        assertEquals(OrderStatus.OPEN, acc.orders.single().status)
        assertEquals(108.0, acc.orders.single().limitPrice, 1e-9)
        assertEquals(120.0, acc.orders.single().trailRef, 1e-9)
        // Kurs faellt leicht: Stop bleibt
        acc = Trading.runOrders(acc, mapOf("SAP.DE" to eurQuote(112.0)), now, respectHours = false)
        assertEquals(108.0, acc.orders.single().limitPrice, 1e-9)
        assertEquals(OrderStatus.OPEN, acc.orders.single().status)
        // Kurs unterschreitet den Stop: Verkauf
        acc = Trading.runOrders(acc, mapOf("SAP.DE" to eurQuote(107.0)), now, respectHours = false)
        assertEquals(OrderStatus.FILLED, acc.orders.single().status)
        assertTrue(acc.positions.isEmpty())
        assertTrue(acc.orders.single().executedPrice <= 107.0)
    }

    @Test fun trailingStopRejectsBadDistance() {
        val o = Order("t", "SAP.DE", "SAP", "SAP.DE", Side.SELL, OrderType.TRAILING_STOP, 1.0, limitPrice = 90.0, createdAt = now, trailPct = 0.0)
        assertEquals(Err.INVALID_TRAIL, Trading.orderError(o))
        assertEquals(Err.INVALID_TRAIL, Trading.orderError(o.copy(trailPct = 75.0)))
        assertEquals(null, Trading.orderError(o.copy(trailPct = 5.0)))
    }

    // Bestand in der Vergangenheit

    @Test fun holdingsAreReconstructedBackwardsFromTransactions() {
        val t1 = at("2026-01-10"); val t2 = at("2026-02-10"); val t3 = at("2026-03-01")
        val txs = listOf(
            Transaction("a", t1, TxType.BUY, "SAP", qty = 10.0, price = 100.0, symbol = "SAP.DE", key = "SAP.DE", tag = "BUY"),
            Transaction("b", t2, TxType.BUY, "SAP", qty = 5.0, price = 110.0, symbol = "SAP.DE", key = "SAP.DE", tag = "BUY"),
            Transaction("c", t3, TxType.SELL, "SAP", qty = 15.0, price = 120.0, symbol = "SAP.DE", key = "SAP.DE", tag = "SELL"),
            Transaction("d", t2, TxType.BUY, "Apple", qty = 2.0, price = 150.0, symbol = "AAPL", key = "AAPL", tag = "BUY"),
        )
        val acc = Account(positions = listOf(Position("AAPL", "Apple", "AAPL", 2.0, 150.0, t2)), transactions = txs)
        assertTrue(Trading.holdingsAt(acc, t1 - 1).isEmpty())
        assertEquals(10.0, Trading.holdingsAt(acc, t1 + 1)["SAP.DE"]!!.qty, 1e-9)
        val mid = Trading.holdingsAt(acc, t2 + 1)
        assertEquals(15.0, mid["SAP.DE"]!!.qty, 1e-9)
        assertEquals(2.0, mid["AAPL"]!!.qty, 1e-9)
        val after = Trading.holdingsAt(acc, t3 + 1)
        assertFalse(after.containsKey("SAP.DE"))
        assertEquals(2.0, after["AAPL"]!!.qty, 1e-9)
        assertEquals(t1, Trading.firstPurchase(acc))
    }

    // Watchlist seit Hinzufuegen

    @Test fun watchlistRemembersPriceWhenAdded() {
        var acc = Trading.watch(Account(), "SAP.DE", 100.0, now)
        assertEquals(100.0, acc.watchMeta["SAP.DE"]!!.price, 1e-9)
        acc = Trading.watch(acc, "SAP.DE", 200.0, now + 1)         // bereits drin: unveraendert
        assertEquals(100.0, acc.watchMeta["SAP.DE"]!!.price, 1e-9)
        acc = Trading.unwatch(acc, "SAP.DE")
        // Der Eintrag bleibt beim Entfernen erhalten (erstes Hinzufuegen fuer die Watchlist-Kennzahl)
        assertTrue(acc.watchlist.isEmpty() && acc.watchMeta.containsKey("SAP.DE"))
        // Alte Eintraege ohne Bezugspunkt bekommen ihn beim ersten Kurs
        val legacy = Account(watchlist = listOf("SAP.DE"))
        val filled = Trading.backfillWatchMeta(legacy, mapOf("SAP.DE" to eurQuote(123.0)), now)
        assertEquals(123.0, filled.watchMeta["SAP.DE"]!!.price, 1e-9)
    }

    // Buchungen tragen Kennungen

    @Test fun transactionsCarryTagsInsteadOfTexts() {
        val acc = Trading.fresh(now, 1000.0)
        assertEquals("OPENING", acc.transactions.single().tag)
        val bought = Trading.buy(acc, "SAP.DE", "SAP", "SAP.DE", 2.0, 100.0, now = now).account
        val buy = bought.transactions.first { it.type == TxType.BUY }
        assertEquals("BUY", buy.tag); assertEquals("SAP.DE", buy.key); assertEquals("", buy.via)
        val fee = bought.transactions.first { it.type == TxType.FEE }
        assertEquals("FEE", fee.tag)
        val wd = Trading.withdraw(bought, 50.0, now).account.transactions.last()
        assertEquals("WITHDRAWAL", wd.tag)
    }

    // Statistik

    @Test fun statisticsTreeGroupsIncomeExpensesInvestments() {
        val m = at("2026-03-02")
        val txs = listOf(
            Transaction("1", m, TxType.DEPOSIT, "", amount = 1000.0, tag = "DEPOSIT"),
            Transaction("2", m, TxType.BUY, "SAP", amount = -500.0, qty = 5.0, price = 100.0, symbol = "SAP.DE", key = "SAP.DE", tag = "BUY"),
            Transaction("3", m, TxType.BUY, "Apple", amount = -100.0, qty = 1.0, price = 100.0, symbol = "AAPL", key = "AAPL", tag = "BUY", via = "PLAN"),
            Transaction("4", m, TxType.FEE, "SAP", amount = -1.0, tag = "FEE"),
            Transaction("5", m, TxType.SELL, "SAP", amount = 300.0, tax = 10.0, qty = 2.0, price = 155.0, symbol = "SAP.DE", key = "SAP.DE", tag = "SELL"),
            Transaction("6", m, TxType.DIVIDEND, "Apple", amount = 7.0, tax = 3.0, symbol = "AAPL", tag = "DIVIDEND"),
            Transaction("7", at("2026-02-10"), TxType.DEPOSIT, "", amount = 999.0, tag = "DEPOSIT"),
        )
        val root = Statistics.tree(txs, Statistics.Period.Month(java.time.YearMonth.of(2026, 3)), zone)
        val income = root.children.first { it.id == "INCOME" }
        val expenses = root.children.first { it.id == "EXPENSES" }
        val invest = root.children.first { it.id == "INVESTMENTS" }
        assertEquals(1000.0 + 310.0 + 10.0, income.amount, 1e-9)          // Einzahlung + Verkauf brutto + Dividende brutto
        assertEquals(1.0 + 13.0, expenses.amount, 1e-9)                   // Gebuehr + Steuern
        assertEquals(600.0, invest.amount, 1e-9)
        assertEquals(100.0, invest.children.first { it.id == "PLANS" }.amount, 1e-9)
        assertEquals("SAP.DE", income.children.first { it.id == "SALES" }.children.single().symbol)
        assertEquals(1320.0 - 14.0 - 600.0, Statistics.net(root), 1e-9)
        assertEquals(600.0 / 1320.0 * 100, Statistics.investRate(root), 1e-9)
        val prev = Statistics.tree(txs, Statistics.previous(Statistics.Period.Month(java.time.YearMonth.of(2026, 3)))!!, zone)
        assertEquals(999.0, Statistics.find(prev, "DEPOSITS")!!.amount, 1e-9)
    }

    @Test fun bubblePackingDoesNotOverlap() {
        val nodes = (1..7).map { Statistics.Node("n$it", it * 137.0 % 900 + 50) }
        val packed = Statistics.pack(nodes)
        assertEquals(7, packed.size)
        for (i in packed.indices) for (j in i + 1 until packed.size) {
            val d = Math.hypot(packed[i].x - packed[j].x, packed[i].y - packed[j].y)
            assertTrue("overlap $i $j", d >= packed[i].r + packed[j].r - 1e-6)
        }
        assertEquals(1.0, packed.first().r, 1e-9)                         // groesste Blase hat Radius 1
    }

    // Sprache und Formate

    @Test fun portfolioCurveIgnoresPurchasesAndSales() {
        fun c(t: Long, v: Double) = Candle(t, v, v, v, v)
        // 1.000 investiert, auf 1.100 gestiegen, dann Verkauf von 600 dazwischen, Rest 500
        val raw = listOf(c(1, 1000.0), c(2, 1100.0), c(3, 500.0))
        val adjusted = Trading.flowNeutral(raw, listOf(3L to -600.0))
        assertEquals(400.0, adjusted[0].close, 1e-9)   // netto investiert: 1.000 - 600
        assertEquals(500.0, adjusted[1].close, 1e-9)
        assertEquals(500.0, adjusted[2].close, 1e-9)   // Ende bleibt der echte Wert
        assertEquals(100.0, adjusted.last().close - adjusted.first().close, 1e-9)
        // Kauf innerhalb des Zeitraums: kein Sprung
        val bought = Trading.flowNeutral(listOf(c(1, 1000.0), c(2, 2000.0), c(3, 2100.0)), listOf(2L to 1000.0))
        assertEquals(2000.0, bought[0].close, 1e-9)
        assertEquals(2100.0, bought[2].close, 1e-9)
        // Ohne Zufluesse unveraendert; Anfang unter null -> unveraendert
        assertEquals(raw, Trading.flowNeutral(raw, emptyList()))
        assertEquals(raw, Trading.flowNeutral(raw, listOf(2L to -5000.0)))
    }

    @Test fun bothLanguagesProvideEveryText() {
        for (s in listOf<Strings>(De, En)) {
            val props = Strings::class.java.methods.filter { it.name.startsWith("get") && it.parameterCount == 0 && it.returnType == String::class.java }
            for (p in props) {
                val v = p.invoke(s) as String
                assertTrue("${p.name} leer in ${s.locale}", v.isNotBlank() || p.name == "getNotAvailable")
            }
        }
        assertEquals("Heute", De.rangeCaption(de.troetenrepublik.app.data.Range.D1))
        assertEquals("Today", En.rangeCaption(de.troetenrepublik.app.data.Range.D1))
        assertEquals("Halbleiter", De.sectorName("Semiconductors"))
        assertEquals("Semiconductors", En.sectorName("Semiconductors"))
        assertEquals("Vereinigte Staaten", De.countryName("United States"))
    }

    @Test fun formatsFollowLanguage() {
        I18n.lang = Lang.DE
        assertEquals("1.234,56 €", Fmt.money(1234.56))
        assertEquals("12,34 %", Fmt.pct(12.34))
        assertEquals(',', Fmt.decimalSep)
        I18n.lang = Lang.EN
        assertEquals("€1,234.56", Fmt.money(1234.56))
        assertEquals("12.34%", Fmt.pct(12.34))
        assertEquals("−€5.00", Fmt.money(-5.0))
        assertEquals('.', Fmt.decimalSep)
        I18n.lang = Lang.DE
    }

    @Test fun settlementDocumentIsLocalized() {
        val tx = Transaction("x", now, TxType.SELL, "SAP", amount = 290.0, qty = 2.0, price = 150.0, symbol = "SAP.DE", tax = 10.0, realized = 40.0, fee = 1.0, key = "SAP.DE", tag = "SELL")
        I18n.lang = Lang.DE
        val docDe = de.troetenrepublik.app.docs.Documents.settlement(tx, "Alex")
        assertEquals("Wertpapierabrechnung", docDe.title)
        assertTrue(docDe.lines.any { it.first == "Realisiertes Ergebnis" })
        I18n.lang = Lang.EN
        val docEn = de.troetenrepublik.app.docs.Documents.settlement(tx, "Alex")
        assertEquals("Securities settlement", docEn.title)
        assertTrue(docEn.lines.any { it.first == "Realised result" })
        I18n.lang = Lang.DE
    }

    @Test fun marketHoursStatusIsStructured() {
        val s = MarketHours.status(de.troetenrepublik.app.data.AssetType.STOCK, false, at("2026-03-07", 12, 0))   // Samstag
        assertFalse(s.open)
        assertNotNull(s.window)
        assertEquals(java.time.DayOfWeek.MONDAY, s.nextOpenWeekday)
        assertFalse(s.opensToday)
        assertTrue(MarketHours.status(de.troetenrepublik.app.data.AssetType.CRYPTO, false, now).open)
    }
}
