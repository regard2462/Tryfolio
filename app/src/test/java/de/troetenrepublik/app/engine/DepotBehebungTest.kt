package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Derivative
import de.troetenrepublik.app.data.DerivativeKind
import de.troetenrepublik.app.data.Einzahlplan
import de.troetenrepublik.app.data.Lot
import de.troetenrepublik.app.data.Order
import de.troetenrepublik.app.data.OrderStatus
import de.troetenrepublik.app.data.OrderType
import de.troetenrepublik.app.data.Position
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.data.Side
import de.troetenrepublik.app.data.TaxState
import de.troetenrepublik.app.data.These
import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.data.Validity
import de.troetenrepublik.app.ui.Fmt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

// Behebungen am Depot: Ergebnis nach Auszahlung, tagesgueltige Orders, Wertpapierart, Dividenden am Ex-Tag, Verlauf von
// Faktor-Zertifikaten, Rundungsreste im Guthaben, Zusammenfuehren, Statistik und die kleinen Befunde. Die Beispiele
// stammen aus dem Fehlerbericht und sind von Hand nachgerechnet.
class DepotBehebungTest {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private fun at(date: String, hour: Int = 12, minute: Int = 0): Long =
        LocalDate.parse(date).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    private fun utc(date: String, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(LocalDate.parse(date), java.time.LocalTime.of(hour, minute)).toInstant(ZoneOffset.UTC).toEpochMilli()

    // Mittwoch, 4. Maerz 2026
    private val now = at("2026-03-04")

    // H1: Eine Auszahlung senkt das Ergebnis nicht

    @Test fun auszahlungLaesstDasErgebnisUnveraendert() {
        // 50.000 Euro Start, nichts gekauft, 10.000 ausgezahlt: Gesamtvermoegen 40.000, Ergebnis 0 (nicht -10.000)
        var acc = Trading.fresh(now)
        acc = Trading.withdraw(acc, 10_000.0, now).account
        val v = Trading.value(acc, emptyMap(), now)
        assertEquals(40_000.0, v.total, 1e-9)
        assertEquals(0.0, v.profit, 1e-9)
        assertEquals(0.0, v.profitPct, 1e-9)
        assertEquals(0.0, Trading.snapshot(acc, v.total, now).history.last().profit!!, 1e-9)
    }

    @Test fun ergebnisZaehltAusgezahltesMit() {
        // Vermoegen 45.000, davon 10.000 schon ausgezahlt, 50.000 eingezahlt: Gewinn 5.000 = 10 Prozent
        val acc = Account(cash = 45_000.0, deposited = 50_000.0, withdrawn = 10_000.0)
        val v = Trading.value(acc, emptyMap(), now)
        assertEquals(5_000.0, v.profit, 1e-9)
        assertEquals(10.0, v.profitPct, 1e-9)
    }

    @Test fun uebernommenePositionenSindKapitalUndAuszahlungenZaehlenTrotzdem() {
        // Eingezahlt 1.000, uebernommene Position mit Einstand 4.000, 500 ausgezahlt, Wert heute 4.700 + 800 Guthaben
        val pos = Position("AAA", "A", "AAA", 10.0, 400.0, at("2025-01-02"))
        val acc = Account(cash = 800.0, deposited = 1_000.0, withdrawn = 500.0, transferredIn = 4_000.0, positions = listOf(pos))
        val v = Trading.value(acc, mapOf("AAA" to Quote("AAA", price = 470.0, prevClose = 470.0, updatedAt = now)), now)
        assertEquals(5_500.0, v.total, 1e-9)
        // 5.500 + 500 - 5.000 = 1.000
        assertEquals(1_000.0, v.profit, 1e-9)
        assertEquals(20.0, v.profitPct, 1e-9)
    }

    // H2: Tagesgueltige Orders ausserhalb der Handelszeit gelten bis zum Schluss des naechsten Handelstags

    private fun tagesOrder(symbol: String, created: Long) =
        Order("o", symbol, symbol, symbol, Side.BUY, OrderType.LIMIT, 1.0, 50.0, createdAt = created, validity = Validity.DAY)

    @Test fun tagesorderAmSamstagLebtBisMontagSchluss() {
        val sa = at("2026-03-07", 14)
        val acc = Trading.placeOrder(Account(cash = 100.0), tagesOrder("SAP.DE", sa))
        val bis = acc.orders.single().expiresAt
        assertEquals(at("2026-03-09", 23), bis)
        // Montagvormittag lebt sie noch
        assertEquals(OrderStatus.OPEN, Trading.expireOrders(acc, at("2026-03-09", 10)).orders.single().status)
        assertEquals(OrderStatus.OPEN, Trading.expireOrders(acc, at("2026-03-07", 23, 30)).orders.single().status)
        assertEquals(OrderStatus.EXPIRED, Trading.expireOrders(acc, bis).orders.single().status)
    }

    @Test fun tagesorderNachHandelsschlussAmFreitagLebtBisMontag() {
        val acc = Trading.placeOrder(Account(cash = 100.0), tagesOrder("SAP.DE", at("2026-03-06", 23, 30)))
        assertEquals(at("2026-03-09", 23), acc.orders.single().expiresAt)
    }

    @Test fun tagesorderVorDerEroeffnungGiltHeute() {
        val acc = Trading.placeOrder(Account(cash = 100.0), tagesOrder("SAP.DE", at("2026-03-04", 6)))
        assertEquals(at("2026-03-04", 23), acc.orders.single().expiresAt)
    }

    @Test fun tagesorderAnEinemFeiertagGiltBisZumNaechstenHandelstag() {
        // Freitag, 1. Mai 2026: kein Handel, naechster Handelstag ist der Montag
        val acc = Trading.placeOrder(Account(cash = 100.0), tagesOrder("SAP.DE", at("2026-05-01", 12)))
        assertEquals(at("2026-05-04", 23), acc.orders.single().expiresAt)
    }

    @Test fun tagesorderWaehrendDerHandelszeitBleibtBeimHeutigenSchluss() {
        val acc = Trading.placeOrder(Account(cash = 100.0), tagesOrder("SAP.DE", now))
        assertEquals(at("2026-03-04", 23), acc.orders.single().expiresAt)
    }

    // M1: Die Art eines Werts kennt auch Werte ausserhalb des Katalogs

    @Test fun aktieAusDerSucheNutztDenAktienVerlusttopf() {
        // Topf Aktien 1.000, Verkauf mit 1.000 Gewinn: keine Steuer (vorher 263,75)
        val pos = Position("ZZSUCHE", "Such-Aktie", "ZZSUCHE", 10.0, 100.0, at("2025-01-02"))
        assertEquals(Tax.Kind.STOCK, Trading.taxKind(pos))
        val acc = Account(cash = 0.0, positions = listOf(pos), tax = TaxState(year = 2026, lossPotStocks = 1_000.0))
        val r = Trading.sell(acc, "ZZSUCHE", 10.0, 200.0, now, fee = 0.0, allowance = 0.0)
        assertTrue(r.ok)
        assertEquals(0.0, r.account.transactions.single { it.type == TxType.SELL }.tax, 1e-9)
        assertEquals(2_000.0, r.account.cash, 1e-9)
        assertEquals(0.0, r.account.tax.lossPotStocks, 1e-9)
    }

    @Test fun kryptoAusDerSucheFuelltAmWochenende() {
        val sa = at("2026-03-07", 14)
        val order = Order("o", "FOOXX-USD", "Foo", "FOOXX-USD", Side.BUY, OrderType.LIMIT, 1.0, 100.0, createdAt = sa, validity = Validity.DAY)
        val acc = Trading.placeOrder(Account(cash = 1_000.0), order)
        // Krypto handelt rund um die Uhr: tagesgueltig heisst 24 Stunden
        assertEquals(sa + 86_400_000L, acc.orders.single().expiresAt)
        val q = mapOf("FOOXX-USD" to Quote("FOOXX-USD", price = 90.0, prevClose = 90.0, updatedAt = sa))
        assertEquals(OrderStatus.FILLED, Trading.runOrders(acc, q, sa).orders.single().status)
    }

    // M2: Kauf am Ex-Tag nach der Eroeffnung gibt keine Dividende

    private fun dividendenKonto(kauf: Long) = Account(
        cash = 0.0,
        positions = listOf(Position("SAP.DE", "SAP", "SAP.DE", 100.0, 100.0, kauf)),
        transactions = listOf(Transaction("b", kauf, TxType.BUY, "SAP", qty = 100.0, price = 100.0, symbol = "SAP.DE", key = "SAP.DE", tag = "BUY")),
        tax = TaxState(year = 2026),
    )

    @Test fun kaufAmExTagHatKeinenAnspruch() {
        // Yahoo stempelt den Ex-Tag mit der Eroeffnung (07:00 UTC); gekauft um 06:30 UTC am selben Tag
        val ex = utc("2026-05-06", 7)
        val acc = Trading.bookDividends(dividendenKonto(utc("2026-05-06", 6, 30)), listOf(Trading.Dividend("SAP.DE", ex, 2.5)), ex + 4 * 86_400_000L)
        assertTrue(acc.transactions.none { it.type == TxType.DIVIDEND })
    }

    @Test fun kaufVorDemExTagGibtDieDividende() {
        val ex = utc("2026-05-06", 7)
        val acc = Trading.bookDividends(dividendenKonto(utc("2026-05-05", 12)), listOf(Trading.Dividend("SAP.DE", ex, 2.5)), ex + 4 * 86_400_000L)
        val d = acc.transactions.single { it.type == TxType.DIVIDEND }
        assertEquals(100.0, d.qty, 1e-9)
        assertEquals(250.0, d.amount + d.tax, 1e-9)
    }

    // M3: Faktor-Zertifikat, Verlauf mit Kerzen mehrerer Tage

    @Test fun faktorVerlaufBeginntAmErstenTagBeimHebelErgebnis() {
        val d = Derivative(id = "f5", underlying = "X", underlyingName = "X", kind = DerivativeKind.FACTOR_LONG, factor = 5.0, factorPrice = 100.0, factorRef = 100.0)
        fun k(t: Long, c: Double) = Candle(t, c, c, c, c)
        val kerzen = listOf(k(utc("2026-03-02", 10), 100.0), k(utc("2026-03-02", 12), 102.0), k(utc("2026-03-03", 10), 102.0), k(utc("2026-03-03", 12), 103.0))
        val out = DerivatVerlauf.faktor(d, kerzen, 100.0, ZoneOffset.UTC, 0.0, Pricing.RATE)
        val kosten = Pricing.finanzierungJeTag(5.0, Pricing.RATE)
        fun h(kurs: Double, ref: Double) = Pricing.hebel(5.0, kurs / ref - 1.0)
        // Handrechnung: Ende Tag 1 = 100, Ende Tag 0 = 100 / (kosten * hebel(103; 102)), Start = Ende Tag 0 / hebel(102; 100)
        val ende0 = 100.0 / (kosten * h(103.0, 102.0))
        val start = ende0 / h(102.0, 100.0)
        assertEquals(start, out[0].close, 1e-9)
        assertEquals(ende0, out[1].close, 1e-9)
        assertEquals(ende0 * kosten, out[2].close, 1e-9)
        assertEquals(100.0, out[3].close, 1e-9)
    }

    // M7: Prozent je Abschnitt beziehen Zukaeufe ein

    @Test fun prozentJeMonatMitZukauf() {
        val tag = Depotzerlegung.TAG
        val montag = LocalDate.of(2026, 10, 5).toEpochDay() * tag
        // A: 1.000 vor dem Monat; B: am 3. Tag 9.000 gekauft, danach 9.450 (+450). Bezug 1.000 + 9.000 = 10.000: 4,5 Prozent
        val zufluesse = mapOf("A" to montag to 1_000.0, "B" to (montag + 2 * tag) to 9_000.0)
        val tage = Depotzerlegung.tage(montag, montag + 3 * tag, listOf("A", "B"), zufluesse) { k, t ->
            when (k) {
                "A" -> 1_000.0
                else -> if (t < montag + 2 * tag) 0.0 else if (t == montag + 2 * tag) 9_000.0 else 9_450.0
            }
        }
        val monat = Depotzerlegung.abschnitte(tage.drop(1), Depotzerlegung.Raster.MONAT).single()
        assertEquals(450.0, monat.summe, 1e-9)
        assertEquals(1_000.0, monat.wertVorher, 1e-9)
        assertEquals(4.5, monat.prozent!!, 1e-9)
    }

    // M9: Guthaben nach Buchungen ohne Rundungsreste

    @Test fun kaufNachBetragLaesstKeinNegativesGuthabenUebrig() {
        // Der Bericht fand das in 4,8 Prozent der Faelle (Guthaben um -1e-12)
        var negativ = 0
        for (i in 0 until 20_000) {
            val cash = 1_000.0 + (i * 7_919L % 90_000L) + (i % 97) / 97.0
            val price = 10.0 + (i * 104_729L % 99_000L) / 100.0 + (i % 13) / 7.0
            val qty = (cash - Trading.FEE) / price
            val r = Trading.buy(Account(cash = cash), "AAA", "A", "AAA", qty, price, now = now)
            if (r.ok && r.account.cash < 0.0) negativ++
            if (r.ok) assertTrue("Rest ${r.account.cash}", r.account.cash == 0.0 || r.account.cash > 0.0)
        }
        assertEquals(0, negativ)
    }

    // M11: Zusammenfuehren verliert keine Derivate und keine Einstellungen

    private fun faktor(id: String) = Derivative(id = id, underlying = "NVDA", underlyingName = "NVIDIA", kind = DerivativeKind.FACTOR_LONG, factor = 5.0, factorPrice = 100.0, factorRef = 100.0)

    @Test fun gleichesDerivatInBeidenDepotsWirdAddiert() {
        val d = faktor("F5-NVDA")
        val a = Account(cash = 100.0, positions = listOf(Position(d.id, "Faktor 5x Long NVDA", "NVDA", 10.0, 100.0, 1L, d)))
        val b = Account(cash = 50.0, positions = listOf(Position(d.id, "Faktor 5x Long NVDA", "NVDA", 30.0, 80.0, 2L, d)))
        val m = Zusammenfuehren.konten(a, b)
        val p = m.positions.single()
        assertEquals(40.0, p.qty, 1e-9)
        // (10 * 100 + 30 * 80) / 40 = 85
        assertEquals(85.0, p.avgPrice, 1e-9)
        assertNotNull(p.derivative)
    }

    @Test fun ausgeknocktesProduktBleibtAusgeknockt() {
        val lebend = Position("KO1", "KO", "NVDA", 5.0, 2.0, 1L, faktor("KO1"))
        val tot = Position("KO1", "KO", "NVDA", 5.0, 2.0, 2L, faktor("KO1").copy(knockedOutAt = 99L, restwert = 0.01))
        assertTrue(Zusammenfuehren.positionen(listOf(lebend), listOf(tot)).single().isKnockedOut)
        assertTrue(Zusammenfuehren.positionen(listOf(tot), listOf(lebend)).single().isKnockedOut)
    }

    @Test fun planWiederanlageThesenUndNotizenGehenNichtVerloren() {
        val alt = Account(notizen = mapOf("AAA" to "alt", "BBB" to "nur alt"), thesen = mapOf("AAA" to These(ausstieg = "unter 50", obergrenze = 20.0)))
        val neu = Account(
            einzahlplan = Einzahlplan(100.0, 2), wiederanlage = mapOf("AAA" to true),
            notizen = mapOf("AAA" to "neu", "CCC" to "nur neu"),
            thesen = mapOf("AAA" to These(ausstieg = "Quartal verfehlt", obergrenze = 10.0), "CCC" to These(ausstieg = "x")),
        )
        val m = Zusammenfuehren.konten(alt, neu)
        assertEquals(100.0, m.einzahlplan!!.betrag, 1e-9)
        assertEquals(true, m.wiederanlage["AAA"])
        assertEquals("alt\n\nneu", m.notizen["AAA"])
        assertEquals("nur alt", m.notizen["BBB"])
        assertEquals("nur neu", m.notizen["CCC"])
        assertEquals("unter 50\nQuartal verfehlt", m.thesen["AAA"]!!.ausstieg)
        assertEquals(10.0, m.thesen["AAA"]!!.obergrenze, 1e-9)
        assertEquals("x", m.thesen["CCC"]!!.ausstieg)
    }

    // K1: Rueckzahlung mit Steuererstattung zaehlt nicht doppelt

    @Test fun rueckzahlungMitErstattungImNetto() {
        // Knock-out: Rueckzahlung 0,10 Euro, Erstattung 30 Euro (Steuer -30): Betrag auf dem Konto 30,10. Dazu 100 Euro
        // Gebuehren: auf dem Konto bleiben -69,90 (vorher zaehlte die Erstattung doppelt und die Rechnung ergab -39,90).
        val tx = Transaction("k", at("2026-03-03"), TxType.KNOCKOUT, "KO", amount = 30.1, qty = 100.0, price = 0.001, tax = -30.0, symbol = "NVDA", key = "KO1", tag = "KO_SETTLE")
        val gebuehr = Transaction("g", at("2026-03-03", 13), TxType.FEE, "", amount = -100.0)
        val baum = Statistics.tree(listOf(tx, gebuehr), Statistics.Period.All, zone)
        assertEquals(-69.9, Statistics.net(baum), 1e-9)
        assertEquals(30.1, Statistics.find(baum, "INCOME")!!.amount, 1e-9)
        assertEquals(100.0, Statistics.find(baum, "EXPENSES")!!.amount, 1e-9)
    }

    @Test fun stromgroessenDerAuswertungNutzenDenBruttobetrag() {
        val tx = Transaction("k", at("2026-03-03"), TxType.EXPIRY, "OS", amount = 6.0, qty = 10.0, price = 1.0, tax = 4.0, symbol = "NVDA", key = "OS1", tag = "EXPIRY")
        val f = PortfolioTools.flows(Account(transactions = listOf(tx))).single()
        // Rueckzahlung 10 x 1 Euro, die Steuer von 4 gehoert nicht zum Geldfluss
        assertEquals(-10.0, f.amount, 1e-9)
    }

    // K4: Tageskerzen von ASX und NZX

    @Test fun kerzenTagDerAsxZaehltZumFolgendenTag() {
        val montag = LocalDate.of(2026, 3, 2).toEpochDay()
        // ASX stempelt die Montagssitzung (Sommerzeit) mit 23:00 UTC am Sonntag
        assertEquals(montag, Tagesrueckblick.kerzenTag(utc("2026-03-01", 23)))
        // Amerika (14:30 UTC), Europa (07:00 UTC) und Asien (00:00 UTC) bleiben am eigenen Tag
        assertEquals(montag, Tagesrueckblick.kerzenTag(utc("2026-03-02", 14, 30)))
        assertEquals(montag, Tagesrueckblick.kerzenTag(utc("2026-03-02", 7)))
        assertEquals(montag, Tagesrueckblick.kerzenTag(utc("2026-03-02", 0)))
    }

    // K6: kleine Stueckzahlen

    @Test fun kleineStueckzahlenBleibenLesbar() {
        assertTrue(Fmt.qty(0.000278).replace(',', '.').startsWith("0.000278"))
        assertTrue(Fmt.qty(0.00004).replace(',', '.').startsWith("0.00004"))
        assertEquals("10", Fmt.qty(10.0))
        assertTrue(Fmt.qty(2.5).replace(',', '.') == "2.5")
    }

    @Test fun betragNahBeiNullZeigtKeinMinus() {
        assertFalse(Fmt.euro(-1e-12).contains("-") || Fmt.euro(-1e-12).contains("\u2212"))
        assertTrue(Fmt.euro(-0.01).contains("\u2212") || Fmt.euro(-0.01).contains("-"))
    }

    // K7: Tagesbewegung bei heute gekauften und verkauften Stuecken

    @Test fun heuteGekaufteStueckeZaehlenAbIhremKaufkurs() {
        val gekauftUm = at("2026-03-04", 10)
        val neu = Position("AAA", "A", "AAA", 10.0, 105.0, gekauftUm, lots = listOf(Lot(10.0, 105.0, gekauftUm)))
        val alt = Position("BBB", "B", "BBB", 10.0, 90.0, at("2026-01-02"))
        val q = mapOf(
            "AAA" to Quote("AAA", price = 105.0, prevClose = 100.0, updatedAt = now),
            "BBB" to Quote("BBB", price = 105.0, prevClose = 100.0, updatedAt = now),
        )
        val v = Trading.value(Account(positions = listOf(neu, alt)), q, now)
        assertEquals(0.0, v.positions.first { it.key == "AAA" }.dayChange, 1e-9)
        assertEquals(50.0, v.positions.first { it.key == "BBB" }.dayChange, 1e-9)
        assertEquals(50.0, v.dayChange, 1e-9)
    }

    @Test fun heuteVerkaufteStueckeZaehlenMit() {
        // 10 Stueck zum Vortagesschluss 100 gehalten, heute zu 106 verkauft: +60; 10 Stueck Rest 5 Euro gestiegen: +50
        val rest = Position("AAA", "A", "AAA", 10.0, 90.0, at("2026-01-02"))
        val verkauf = Transaction("s", at("2026-03-04", 11), TxType.SELL, "A", qty = 10.0, price = 106.0, symbol = "AAA", key = "AAA", tag = "SELL")
        val q = mapOf("AAA" to Quote("AAA", price = 105.0, prevClose = 100.0, updatedAt = now))
        val v = Trading.value(Account(positions = listOf(rest), transactions = listOf(verkauf)), q, now)
        assertEquals(110.0, v.dayChange, 1e-9)
    }

    // K8: Reserviertes Guthaben steht Kaeufen nicht zur Verfuegung

    @Test fun marktkaufNutztReserviertesGuthabenNicht() {
        // 1.000 Guthaben, Limit-Kauf 5 x 150 reserviert 751; ein Kauf ueber 301 passt nicht mehr
        val order = Order("o", "SAP.DE", "SAP", "SAP.DE", Side.BUY, OrderType.LIMIT, 5.0, 150.0, createdAt = now)
        val acc = Account(cash = 1_000.0, orders = listOf(order))
        assertEquals(Err.NOT_ENOUGH_CASH, Trading.buy(acc, "SAP.DE", "SAP", "SAP.DE", 3.0, 100.0, now = now).error)
        assertTrue(Trading.buy(acc, "SAP.DE", "SAP", "SAP.DE", 2.0, 100.0, now = now).ok)
        // Die Order selbst darf das Guthaben verbrauchen
        val q = mapOf("SAP.DE" to Quote("SAP.DE", price = 100.0, prevClose = 100.0, updatedAt = now))
        assertEquals(OrderStatus.FILLED, Trading.runOrders(acc, q, now).orders.single().status)
    }

    // K9: Beim Kuerzen bleiben Kaeufe und Einzahlungen

    @Test fun kuerzenLaesstZuerstGebuehrenFallen() {
        val liste = buildList {
            add(Transaction("dep", 0L, TxType.DEPOSIT, "", amount = 50_000.0))
            add(Transaction("buy", 1L, TxType.BUY, "A", qty = 1.0, price = 100.0, symbol = "A", key = "A"))
            for (i in 0 until Trading.MAX_BUCHUNGEN) add(Transaction("fee$i", 10L + i, TxType.FEE, "", amount = -1.0))
        }
        val k = Trading.kuerzen(liste)
        assertEquals(Trading.MAX_BUCHUNGEN, k.size)
        assertTrue(k.any { it.id == "dep" })
        assertTrue(k.any { it.id == "buy" })
        assertFalse(k.any { it.id == "fee0" })
        assertFalse(k.any { it.id == "fee1" })
        assertTrue(k.any { it.id == "fee2" })
    }

    // K10: Haltefrist endet mit Ablauf des Jahrestags

    @Test fun amJahrestagIstNochKeinJahrVergangen() {
        val kauf = at("2025-03-01", 10)
        assertFalse(Trading.ueberEinJahr(kauf, at("2026-03-01", 15)))
        assertTrue(Trading.ueberEinJahr(kauf, at("2026-03-02", 9)))
        assertFalse(Trading.ueberEinJahr(kauf, at("2026-02-28", 23)))
    }
}
