package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Derivative
import de.troetenrepublik.app.data.DerivativeKind
import de.troetenrepublik.app.data.Einzahlplan
import de.troetenrepublik.app.data.Order
import de.troetenrepublik.app.data.OrderStatus
import de.troetenrepublik.app.data.OrderType
import de.troetenrepublik.app.data.PlanInterval
import de.troetenrepublik.app.data.Position
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.data.SavingsPlan
import de.troetenrepublik.app.data.Side
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.data.Validity
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.TimeZone

// Orders und Sparplaene, die in einer Pause der App faellig waren, werden mit Kerzen nachgeholt. Der 2. Maerz 2026 ist
// ein Montag; gehandelt wird werktags von 7:30 bis 23 Uhr Berliner Zeit.
class Fassung078Z9NachholenTest {
    private val zone = java.time.ZoneId.of("Europe/Berlin")
    private var zoneVorher: TimeZone? = null
    private var zeitenVorher: MarketHours.Zeiten? = null

    @Before fun vor() {
        zoneVorher = TimeZone.getDefault()
        zeitenVorher = MarketHours.zeiten
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
        MarketHours.zeiten = MarketHours.DEUTSCHLAND
    }

    @After fun nach() {
        TimeZone.setDefault(zoneVorher)
        zeitenVorher?.let { MarketHours.zeiten = it }
    }

    private fun zeit(tag: String, stunde: Int, minute: Int = 0): Long =
        LocalDate.parse(tag).atTime(stunde, minute).atZone(zone).toInstant().toEpochMilli()

    private fun kerze(t: Long, auf: Double, hoch: Double, tief: Double, schluss: Double) = Candle(t, auf, hoch, tief, schluss)
    private fun reihe(vararg k: Candle, symbol: String = "TEST") = mapOf(symbol to Nachholen.Reihe(k.toList()))

    private val aufgabe = zeit("2026-03-02", 10)
    private val seit = zeit("2026-03-02", 10, 1)
    private val jetzt = zeit("2026-03-04", 12)

    private fun order(
        seite: Side, art: OrderType, marke: Double, stueck: Double = 10.0, id: String = "o1", symbol: String = "TEST",
        key: String = symbol, gueltig: Validity = Validity.GTC, produkt: Derivative? = null,
    ) = Order(id, key, "Test", symbol, seite, art, stueck, marke, createdAt = aufgabe, validity = gueltig, derivative = produkt)

    private fun konto(cash: Double, vararg orders: Order, positionen: List<Position> = emptyList()): Account =
        orders.fold(Account(cash = cash, positions = positionen, letzterLauf = seit)) { a, o -> Trading.placeOrder(a, o) }

    private fun bestand(symbol: String = "TEST", stueck: Double = 10.0, einstand: Double = 100.0) =
        Position(symbol, "Test", symbol, stueck, einstand, aufgabe - 86_400_000L)

    private fun lauf(a: Account, reihen: Map<String, Nachholen.Reihe>, bis: Long = jetzt) = Nachholen.ausfuehren(a, seit, bis, reihen)

    // ------------------------------------------------------------ Limit

    @Test fun limitKaufErreichtUndWiederDarueber() {
        val t = zeit("2026-03-02", 14, 5)
        val a = lauf(konto(10_000.0, order(Side.BUY, OrderType.LIMIT, 95.0)), reihe(
            kerze(zeit("2026-03-02", 14), 100.0, 101.0, 99.0, 100.0),
            kerze(t, 99.0, 99.0, 94.0, 96.0),
            kerze(zeit("2026-03-02", 14, 10), 97.0, 100.0, 97.0, 100.0),
        ))
        val o = a.orders.single()
        assertEquals(OrderStatus.FILLED, o.status)
        // Zeitstempel der Kerze an Order, Buchung, Position und Kaufposten
        assertEquals(t, o.executedAt)
        assertEquals(95.0, o.executedPrice, 1e-9)
        val p = a.positions.single()
        assertEquals(10.0, p.qty, 1e-9)
        assertEquals(95.0, p.avgPrice, 1e-9)
        assertEquals(t, p.openedAt)
        assertEquals(t, p.lots.single().ts)
        val kauf = a.transactions.single { it.type == TxType.BUY }
        assertEquals(t, kauf.ts)
        assertEquals(Trading.VIA_ORDER, kauf.via)
        assertTrue(a.transactions.all { it.ts == t })
        assertEquals(10_000.0 - 950.0 - Trading.FEE, a.cash, 1e-9)
        // Der laufende Takt haette beim Oeffnen nichts getan: der Kurs steht wieder ueber dem Limit
        val live = Trading.runOrders(konto(10_000.0, order(Side.BUY, OrderType.LIMIT, 95.0)), mapOf("TEST" to Quote("TEST", price = 100.0, updatedAt = jetzt)), jetzt)
        assertEquals(OrderStatus.OPEN, live.orders.single().status)
    }

    @Test fun limitKaufNichtErreichtBleibtOffen() {
        // Der Briefkurs zaehlt: 95,00 im Tief liegt mit Spanne ueber dem Limit
        val a = lauf(konto(10_000.0, order(Side.BUY, OrderType.LIMIT, 95.0)), reihe(kerze(zeit("2026-03-02", 14), 100.0, 101.0, 95.0, 100.0)))
        assertEquals(OrderStatus.OPEN, a.orders.single().status)
        assertTrue(a.positions.isEmpty())
    }

    @Test fun kurslueckeUnterDasLimit() {
        val a = lauf(konto(10_000.0, order(Side.BUY, OrderType.LIMIT, 95.0)), reihe(kerze(zeit("2026-03-03", 9), 90.0, 93.0, 89.0, 92.0)))
        // Eroeffnung unter dem Limit: zur Eroeffnung, mit Spanne
        assertEquals(Pricing.ask(90.0, 20.0), a.orders.single().executedPrice, 1e-9)
        assertEquals(zeit("2026-03-03", 9), a.orders.single().executedAt)
    }

    @Test fun limitVerkaufMitUndOhneLuecke() {
        val start = konto(10.0, order(Side.SELL, OrderType.LIMIT, 105.0), positionen = listOf(bestand()))
        val a = lauf(start, reihe(kerze(zeit("2026-03-02", 15), 100.0, 106.0, 99.0, 101.0)))
        assertEquals(OrderStatus.FILLED, a.orders.single().status)
        assertEquals(105.0, a.orders.single().executedPrice, 1e-9)
        assertTrue(a.positions.isEmpty())
        val b = lauf(start, reihe(kerze(zeit("2026-03-03", 9), 110.0, 111.0, 108.0, 109.0)))
        assertEquals(Pricing.bid(110.0, 20.0), b.orders.single().executedPrice, 1e-9)
    }

    // ------------------------------------------------------------ Stopp

    @Test fun stoppVerkaufMitLuecke() {
        val start = konto(10.0, order(Side.SELL, OrderType.STOP, 95.0), positionen = listOf(bestand()))
        // Im Handel durchlaufen: zum Stopp, mit Spanne
        val a = lauf(start, reihe(kerze(zeit("2026-03-02", 15), 96.0, 97.0, 94.0, 96.5)))
        assertEquals(Pricing.bid(95.0, 20.0), a.orders.single().executedPrice, 1e-9)
        // Eroeffnung unter dem Stopp: die Luecke geht zu Lasten des Nutzers
        val b = lauf(start, reihe(kerze(zeit("2026-03-03", 9), 90.0, 92.0, 88.0, 91.0)))
        assertEquals(OrderStatus.FILLED, b.orders.single().status)
        assertEquals(Pricing.bid(90.0, 20.0), b.orders.single().executedPrice, 1e-9)
        assertEquals(zeit("2026-03-03", 9), b.transactions.single { it.type == TxType.SELL }.ts)
        // Nicht erreicht
        val c = lauf(start, reihe(kerze(zeit("2026-03-02", 15), 96.0, 99.0, 95.5, 98.0)))
        assertEquals(OrderStatus.OPEN, c.orders.single().status)
    }

    @Test fun stoppKaufSpiegelbildlich() {
        val start = konto(10_000.0, order(Side.BUY, OrderType.STOP, 105.0))
        val a = lauf(start, reihe(kerze(zeit("2026-03-02", 15), 104.0, 106.0, 103.0, 105.5)))
        assertEquals(Pricing.ask(105.0, 20.0), a.orders.single().executedPrice, 1e-9)
        val b = lauf(start, reihe(kerze(zeit("2026-03-03", 9), 110.0, 112.0, 109.0, 111.0)))
        assertEquals(Pricing.ask(110.0, 20.0), b.orders.single().executedPrice, 1e-9)
    }

    // ------------------------------------------------------------ Trailing-Stopp

    @Test fun trailingStoppLoestNichtDurchDasEigeneHochAus() {
        val trailing = Trading.trailingStop(order(Side.SELL, OrderType.TRAILING_STOP, 0.0).copy(trailPct = 5.0), 100.0)
        assertEquals(95.0, trailing.limitPrice, 1e-9)
        val start = konto(10.0, trailing, positionen = listOf(bestand()))
        // Hoch 110 zoege den Stopp auf 104,50, das Tief 96 laege darunter: ausgeloest wird trotzdem nicht, denn der
        // bisherige Stopp bei 95 wurde nicht erreicht
        val erste = kerze(zeit("2026-03-02", 15), 100.0, 110.0, 96.0, 108.0)
        val a = lauf(start, reihe(erste))
        assertEquals(OrderStatus.OPEN, a.orders.single().status)
        assertEquals(110.0, a.orders.single().trailRef, 1e-9)
        assertEquals(104.5, a.orders.single().limitPrice, 1e-9)
        // Die naechste Kerze erreicht den nachgezogenen Stopp
        val t2 = zeit("2026-03-02", 15, 5)
        val b = lauf(start, reihe(erste, kerze(t2, 108.0, 109.0, 104.0, 105.0)))
        assertEquals(OrderStatus.FILLED, b.orders.single().status)
        assertEquals(t2, b.orders.single().executedAt)
        assertEquals(Pricing.bid(104.5, 20.0), b.orders.single().executedPrice, 1e-9)
    }

    @Test fun trailingStoppBisherigerStoppZuerst() {
        val trailing = Trading.trailingStop(order(Side.SELL, OrderType.TRAILING_STOP, 0.0).copy(trailPct = 5.0), 100.0)
        val start = konto(10.0, trailing, positionen = listOf(bestand()))
        // Tief 94 erreicht den bisherigen Stopp 95, auch wenn die Kerze danach hoeher schliesst
        val a = lauf(start, reihe(kerze(zeit("2026-03-02", 15), 99.0, 112.0, 94.0, 111.0)))
        assertEquals(OrderStatus.FILLED, a.orders.single().status)
        assertEquals(Pricing.bid(95.0, 20.0), a.orders.single().executedPrice, 1e-9)
    }

    // ------------------------------------------------------------ Gueltigkeit, Handelszeiten

    @Test fun tagesorderVerfaellt() {
        val tagesorder = order(Side.BUY, OrderType.LIMIT, 95.0, gueltig = Validity.DAY)
        val start = konto(10_000.0, tagesorder)
        val ablauf = start.orders.single().expiresAt
        assertEquals(zeit("2026-03-02", 23), ablauf)
        // Am Tag der Aufgabe nicht erreicht, am naechsten Tag schon: die Order ist dann verfallen
        val morgen = reihe(kerze(zeit("2026-03-02", 16), 100.0, 101.0, 99.0, 100.0), kerze(zeit("2026-03-03", 10), 96.0, 96.0, 90.0, 91.0))
        val a = lauf(start, morgen)
        assertEquals(OrderStatus.EXPIRED, a.orders.single().status)
        assertEquals(ablauf, a.orders.single().executedAt)
        assertTrue(a.positions.isEmpty())
        assertEquals(10_000.0, a.cash, 1e-9)
        // Dieselben Kerzen mit einer unbefristeten Order
        assertEquals(OrderStatus.FILLED, lauf(konto(10_000.0, order(Side.BUY, OrderType.LIMIT, 95.0)), morgen).orders.single().status)
        // Am Tag der Aufgabe erreicht: ausgefuehrt
        val heute = lauf(start, reihe(kerze(zeit("2026-03-02", 16), 96.0, 96.0, 90.0, 91.0)))
        assertEquals(OrderStatus.FILLED, heute.orders.single().status)
    }

    @Test fun ausserhalbDerHandelszeitenUndVorDerAufgabe() {
        val start = konto(10_000.0, order(Side.BUY, OrderType.LIMIT, 95.0))
        // Samstag und nachts um 3 Uhr wird nicht gehandelt
        val a = lauf(start, reihe(kerze(zeit("2026-03-03", 3), 90.0, 91.0, 89.0, 90.0)), bis = zeit("2026-03-09", 12))
        assertEquals(OrderStatus.OPEN, a.orders.single().status)
        val b = lauf(start, reihe(kerze(zeit("2026-03-07", 12), 90.0, 91.0, 89.0, 90.0)), bis = zeit("2026-03-09", 12))
        assertEquals(OrderStatus.OPEN, b.orders.single().status)
        // Kerzen vor dem letzten Lauf zaehlen nicht
        val c = lauf(start, reihe(kerze(zeit("2026-03-02", 9, 55), 90.0, 91.0, 89.0, 90.0), kerze(zeit("2026-03-02", 10), 90.0, 91.0, 89.0, 90.0)))
        assertEquals(OrderStatus.OPEN, c.orders.single().status)
    }

    // ------------------------------------------------------------ Reihenfolge und Guthaben

    @Test fun verkaufBezahltSpaeterenKaufNichtUmgekehrt() {
        fun start() = konto(
            5.0,
            order(Side.SELL, OrderType.LIMIT, 110.0, id = "vk", symbol = "AAA"),
            order(Side.BUY, OrderType.LIMIT, 50.0, stueck = 5.0, id = "kf", symbol = "BBB"),
            positionen = listOf(bestand("AAA")),
        )
        val frueh = zeit("2026-03-02", 11)
        val spaet = zeit("2026-03-02", 14)
        val hochA = { t: Long -> kerze(t, 109.0, 112.0, 109.0, 111.0) }
        val tiefB = { t: Long -> kerze(t, 51.0, 51.0, 48.0, 49.0) }
        // Verkauf um 11 Uhr, Kauf um 14 Uhr: der Erloes reicht
        val a = lauf(start(), mapOf("BBB" to Nachholen.Reihe(listOf(tiefB(spaet))), "AAA" to Nachholen.Reihe(listOf(hochA(frueh)))))
        assertEquals(OrderStatus.FILLED, a.orders.first { it.id == "vk" }.status)
        assertEquals(OrderStatus.FILLED, a.orders.first { it.id == "kf" }.status)
        assertEquals(spaet, a.positions.single().openedAt)
        // Kauf um 11 Uhr, Verkauf um 14 Uhr: abgelehnt mit dem Zeitstempel der Kerze
        val b = lauf(start(), mapOf("AAA" to Nachholen.Reihe(listOf(hochA(spaet))), "BBB" to Nachholen.Reihe(listOf(tiefB(frueh)))))
        val kauf = b.orders.first { it.id == "kf" }
        assertEquals(OrderStatus.REJECTED, kauf.status)
        assertEquals(Err.NOT_ENOUGH_CASH, kauf.reason)
        assertEquals(frueh, kauf.executedAt)
        assertEquals(OrderStatus.FILLED, b.orders.first { it.id == "vk" }.status)
        assertTrue(b.positions.isEmpty())
        // Dieselbe Kerze: der Verkauf bezahlt den Kauf nicht
        val c = lauf(start(), mapOf("AAA" to Nachholen.Reihe(listOf(hochA(frueh))), "BBB" to Nachholen.Reihe(listOf(tiefB(frueh)))))
        assertEquals(OrderStatus.REJECTED, c.orders.first { it.id == "kf" }.status)
        assertEquals(OrderStatus.FILLED, c.orders.first { it.id == "vk" }.status)
    }

    @Test fun guthabenUndBestandReichenNicht() {
        val t = zeit("2026-03-02", 14)
        val a = lauf(konto(100.0, order(Side.BUY, OrderType.LIMIT, 95.0)), reihe(kerze(t, 96.0, 96.0, 94.0, 95.0)))
        assertEquals(OrderStatus.REJECTED, a.orders.single().status)
        assertEquals(Err.NOT_ENOUGH_CASH, a.orders.single().reason)
        assertEquals(t, a.orders.single().executedAt)
        assertEquals(100.0, a.cash, 1e-9)
        assertTrue(a.transactions.isEmpty())
        val b = lauf(konto(100.0, order(Side.SELL, OrderType.STOP, 95.0)), reihe(kerze(t, 96.0, 96.0, 94.0, 95.0)))
        assertEquals(OrderStatus.REJECTED, b.orders.single().status)
        assertEquals(Err.NOT_IN_DEPOT, b.orders.single().reason)
    }

    // ------------------------------------------------------------ Sparplaene

    private fun plan(uhrzeit: Int = -1) = SavingsPlan(
        "p1", "TEST", "Test", 100.0, 2, lastRun = zeit("2026-02-02", 10), createdAt = zeit("2026-01-10", 10),
        interval = PlanInterval.MONTHLY, invested = 100.0, executions = 1, uhrzeit = uhrzeit,
    )
    private val planSeit = zeit("2026-02-03", 12)
    private val planJetzt = zeit("2026-05-20", 12)
    // Termine am 2. Maerz (Montag), 2. April (Donnerstag) und 2. Mai (Samstag, also Montag, 4. Mai)
    private val planKerzen = listOf(
        kerze(zeit("2026-02-20", 9), 45.0, 46.0, 44.0, 45.0),
        kerze(zeit("2026-03-02", 9), 50.0, 51.0, 49.0, 50.5),
        kerze(zeit("2026-03-02", 9, 5), 51.0, 52.0, 50.0, 51.0),
        kerze(zeit("2026-03-10", 9), 55.0, 56.0, 54.0, 55.0),
        kerze(zeit("2026-04-02", 9), 40.0, 41.0, 39.0, 40.0),
        kerze(zeit("2026-04-02", 15), 42.0, 43.0, 41.0, 42.0),
        kerze(zeit("2026-04-30", 9), 70.0, 71.0, 69.0, 70.0),
        kerze(zeit("2026-05-04", 9), 80.0, 81.0, 79.0, 80.0),
        kerze(zeit("2026-05-04", 15), 82.0, 83.0, 81.0, 82.0),
        kerze(zeit("2026-05-19", 9), 85.0, 86.0, 84.0, 85.0),
    )

    private fun planLauf(a: Account) = Nachholen.ausfuehren(a.copy(letzterLauf = planSeit), planSeit, planJetzt, mapOf("TEST" to Nachholen.Reihe(planKerzen)))

    @Test fun dreiVerpassteSparplantermineEinzeln() {
        val a = planLauf(Account(cash = 1_000.0, plans = listOf(plan())))
        val kaeufe = a.transactions.filter { it.type == TxType.BUY }
        assertEquals(listOf(zeit("2026-03-02", 9), zeit("2026-04-02", 9), zeit("2026-05-04", 9)), kaeufe.map { it.ts })
        // Briefkurs der ersten Kerze ab dem Termin
        assertEquals(listOf(50.0, 40.0, 80.0).map { Pricing.ask(it, 20.0) }, kaeufe.map { it.price })
        assertTrue(kaeufe.all { it.via == "PLAN" && kotlin.math.abs(it.qty * it.price - 100.0) < 1e-9 })
        val p = a.plans.single()
        assertEquals(4, p.executions)
        assertEquals(400.0, p.invested, 1e-9)
        assertEquals(zeit("2026-05-04", 9), p.lastRun)
        assertEquals(700.0, a.cash, 1e-9)
        // Jeder Kauf ein eigener Posten mit seinem Zeitpunkt
        assertEquals(kaeufe.map { it.ts }, a.positions.single().lots.map { it.ts })
        // Kein Vermerk ueber uebersprungene Termine, und der laufende Takt hat danach nichts mehr zu tun
        assertTrue(a.transactions.none { it.tag == "PLAN_SKIPPED" })
        val danach = Trading.runPlans(a, mapOf("TEST" to Quote("TEST", price = 85.0, updatedAt = planJetzt)), planJetzt)
        assertSame(a, danach)
    }

    @Test fun terminAmWochenendeUndMitUhrzeit() {
        // 2. Mai 2026 ist ein Samstag
        assertEquals(java.time.DayOfWeek.SATURDAY, LocalDate.parse("2026-05-02").dayOfWeek)
        val a = planLauf(Account(cash = 1_000.0, plans = listOf(plan(uhrzeit = 14 * 60))))
        val kaeufe = a.transactions.filter { it.type == TxType.BUY }
        // Mit Uhrzeit 14 Uhr: am 2. Maerz gibt es danach keine Kerze mehr, also die naechste (10. Maerz); am 2. April die
        // Kerze um 15 Uhr; der Samstagstermin faellt auf Montag frueh
        assertEquals(listOf(zeit("2026-03-10", 9), zeit("2026-04-02", 15), zeit("2026-05-04", 9)), kaeufe.map { it.ts })
        assertEquals(listOf(55.0, 42.0, 80.0).map { Pricing.ask(it, 20.0) }, kaeufe.map { it.price })
    }

    @Test fun sparplanOhneGuthabenJeTerminEinVermerk() {
        val a = planLauf(Account(cash = 150.0, plans = listOf(plan())))
        // Erster Termin gekauft, zweiter und dritter scheitern; je Termin ein Vermerk, auch wenn weitere Kerzen folgen
        assertEquals(listOf(zeit("2026-03-02", 9)), a.transactions.filter { it.type == TxType.BUY }.map { it.ts })
        val fehl = a.transactions.filter { it.tag == "PLAN_FAILED" }
        assertEquals(listOf(zeit("2026-04-02", 9), zeit("2026-05-04", 9)), fehl.map { it.ts })
        assertTrue(fehl.all { it.note == Err.NOT_ENOUGH_CASH && it.extra == 100.0 })
        assertEquals(2, a.plans.single().executions)
        assertEquals(zeit("2026-05-04", 9), a.plans.single().lastFailed)
        assertEquals(50.0, a.cash, 1e-9)
    }

    @Test fun einzahlungVorSparplanDesselbenTages() {
        val ez = Einzahlplan(betrag = 500.0, tag = 2, letzterMonat = Einzahlplaene.monat(zeit("2026-02-02", 10)), erstellt = zeit("2026-01-10", 10))
        val a = planLauf(Account(cash = 0.0, plans = listOf(plan()), einzahlplan = ez))
        val einzahlungen = a.transactions.filter { it.type == TxType.DEPOSIT }
        // Drei Monate, jeder zum Beginn seines Tages
        assertEquals(listOf(zeit("2026-03-02", 0), zeit("2026-04-02", 0), zeit("2026-05-02", 0)), einzahlungen.map { it.ts })
        assertTrue(einzahlungen.all { it.via == "PLAN" && it.amount == 500.0 })
        assertEquals(3, a.transactions.count { it.type == TxType.BUY })
        assertTrue(a.transactions.none { it.tag == "PLAN_FAILED" })
        assertEquals(Einzahlplaene.monat(zeit("2026-05-02", 10)), a.einzahlplan!!.letzterMonat)
        assertEquals(3, a.einzahlplan!!.ausfuehrungen)
        assertEquals(1_200.0, a.cash, 1e-9)
        // Der laufende Takt bucht danach keinen Monat doppelt
        assertSame(a, Einzahlplaene.ausfuehren(a, planJetzt))
    }

    @Test fun sparplanHoltNachSobaldDieEinzahlungDaIst() {
        // Einzahlung am 5., Sparplan am 2.: der Kauf scheitert am 2. und gelingt mit der ersten Kerze nach der Einzahlung
        val ez = Einzahlplan(betrag = 500.0, tag = 5, letzterMonat = Einzahlplaene.monat(zeit("2026-02-05", 10)), erstellt = zeit("2026-01-10", 10))
        val a = planLauf(Account(cash = 0.0, plans = listOf(plan()), einzahlplan = ez))
        val kaeufe = a.transactions.filter { it.type == TxType.BUY }
        assertEquals(zeit("2026-03-10", 9), kaeufe.first().ts)
        assertEquals(zeit("2026-03-02", 9), a.transactions.first { it.tag == "PLAN_FAILED" }.ts)
    }

    @Test fun planTermin() {
        assertEquals(zeit("2026-03-02", 0), Trading.planTermin(plan(), planJetzt))
        assertEquals(zeit("2026-03-02", 14), Trading.planTermin(plan(uhrzeit = 14 * 60), planJetzt))
        assertEquals(0L, Trading.planTermin(plan(), zeit("2026-02-20", 12)))
        assertEquals(0L, Trading.planTermin(plan().copy(paused = true), planJetzt))
        val woche = plan().copy(interval = PlanInterval.WEEKLY)
        assertEquals(zeit("2026-02-09", 0), Trading.planTermin(woche, planJetzt))
    }

    // ------------------------------------------------------------ Nichts zu tun

    @Test fun fehlendeKerzenLassenAllesWieEsIst() {
        val start = konto(10_000.0, order(Side.BUY, OrderType.LIMIT, 95.0)).copy(plans = listOf(plan()))
        assertSame(start, lauf(start, emptyMap()))
        assertSame(start, lauf(start, mapOf("TEST" to Nachholen.Reihe(emptyList()))))
        // Kerzen nur fuer einen anderen Wert
        assertSame(start, lauf(start, reihe(kerze(zeit("2026-03-02", 14), 90.0, 91.0, 89.0, 90.0), symbol = "XYZ")))
        // Der laufende Takt holt dann wie bisher einen Termin nach und vermerkt die uebrigen
        val live = Trading.runPlans(start.copy(cash = 1_000.0, orders = emptyList()), mapOf("TEST" to Quote("TEST", price = 85.0, updatedAt = planJetzt)), planJetzt)
        assertEquals(1, live.transactions.count { it.type == TxType.BUY })
        assertEquals(2.0, live.transactions.single { it.tag == "PLAN_SKIPPED" }.extra, 1e-9)
    }

    @Test fun ohneLueckeOderOhneZeitpunktGeschiehtNichts() {
        val start = konto(10_000.0, order(Side.BUY, OrderType.LIMIT, 95.0))
        val kerzen = reihe(kerze(zeit("2026-03-02", 10, 5), 90.0, 91.0, 89.0, 90.0))
        assertSame(start, Nachholen.ausfuehren(start, 0L, jetzt, kerzen))
        assertSame(start, Nachholen.ausfuehren(start, seit, seit + 10 * 60_000L, kerzen))
        assertFalse(Nachholen.noetig(start.copy(letzterLauf = 0L), jetzt))
        assertFalse(Nachholen.noetig(start, seit + 10 * 60_000L))
        assertTrue(Nachholen.noetig(start, seit + 20 * 60_000L))
        // Ohne offene Order und ohne laufenden Sparplan
        assertFalse(Nachholen.noetig(Account(cash = 5.0, letzterLauf = seit), jetzt))
        assertFalse(Nachholen.noetig(Account(letzterLauf = seit, plans = listOf(plan().copy(paused = true))), jetzt))
        assertTrue(Nachholen.noetig(Account(letzterLauf = seit, plans = listOf(plan())), jetzt))
        assertEquals(listOf("TEST"), Nachholen.symbole(start.copy(plans = listOf(plan()))))
    }

    @Test fun alterStandOhneZeitpunkt() {
        val a = Json { ignoreUnknownKeys = true }.decodeFromString(Account.serializer(), """{"cash":5.0,"lastMaintainDay":20000}""")
        assertEquals(0L, a.letzterLauf)
        assertEquals(20_000L, a.lastMaintainDay)
    }

    // ------------------------------------------------------------ Tageskerzen, Abstand

    @Test fun tageskerzenMitTageshochUndTief() {
        val start = konto(10_000.0, order(Side.BUY, OrderType.LIMIT, 95.0))
        val bis = zeit("2026-03-06", 12)
        fun tage(vararg k: Candle) = mapOf("TEST" to Nachholen.Reihe(k.toList(), tage = true))
        // Dienstag erreicht: Zeitstempel ist der Handelsschluss
        val a = Nachholen.ausfuehren(start, seit, bis, tage(kerze(zeit("2026-03-03", 9), 100.0, 101.0, 94.0, 99.0)))
        assertEquals(OrderStatus.FILLED, a.orders.single().status)
        assertEquals(zeit("2026-03-03", 23) - 1, a.orders.single().executedAt)
        assertEquals(95.0, a.orders.single().executedPrice, 1e-9)
        // Der Tag des letzten Laufs und der heutige zaehlen nicht
        val b = Nachholen.ausfuehren(start, seit, bis, tage(kerze(zeit("2026-03-02", 9), 100.0, 101.0, 90.0, 99.0), kerze(zeit("2026-03-06", 9), 100.0, 101.0, 90.0, 99.0)))
        assertSame(start, b)
    }

    @Test fun abstandUndTageskerzen() {
        val tag = 86_400_000L
        assertEquals("5m", Nachholen.intervall(0L, 20 * 60_000L))
        assertEquals("5m", Nachholen.intervall(0L, 55 * tag))
        assertEquals("1h", Nachholen.intervall(0L, 56 * tag))
        assertEquals("1h", Nachholen.intervall(0L, 700 * tag))
        assertEquals("1d", Nachholen.intervall(0L, 701 * tag))
        val minuten = (0 until 10).map { kerze(it * 300_000L, 1.0, 1.0, 1.0, 1.0) }
        val tage = (0 until 10).map { kerze(it * tag, 1.0, 1.0, 1.0, 1.0) }
        assertFalse(Nachholen.tageskerzen(minuten, "5m"))
        assertTrue(Nachholen.tageskerzen(tage, "5m"))
        assertTrue(Nachholen.tageskerzen(emptyList(), "1d"))
        assertFalse(Nachholen.tageskerzen(emptyList(), "1h"))
    }

    // ------------------------------------------------------------ Hebelprodukte

    private val turbo = Derivative("KO1", "TEST", "Test", DerivativeKind.KO_LONG, strike = 80.0, barrier = 85.0, ratio = 1.0, createdAt = aufgabe - 86_400_000L)
    private fun turboKonto(o: Order) = konto(10.0, o, positionen = listOf(Position("KO1", "Turbo", "TEST", 10.0, 20.0, aufgabe - 86_400_000L, turbo)))

    @Test fun knockOutProduktOhneBeruehrungDerSchwelle() {
        // Kurs des Produkts: Basiswert minus Basispreis. Limit 30 ist bei 111 im Basiswert mit Spanne erreicht
        val a = lauf(turboKonto(order(Side.SELL, OrderType.LIMIT, 30.0, key = "KO1", produkt = turbo)), reihe(kerze(zeit("2026-03-02", 15), 105.0, 111.0, 104.0, 110.0)))
        assertEquals(OrderStatus.FILLED, a.orders.single().status)
        assertEquals(30.0, a.orders.single().executedPrice, 1e-9)
        assertTrue(a.positions.isEmpty())
        // Stopp bei 12: Basiswert 91 unterschreitet ihn, die Schwelle 85 bleibt unberuehrt
        val b = lauf(turboKonto(order(Side.SELL, OrderType.STOP, 12.0, key = "KO1", produkt = turbo)), reihe(kerze(zeit("2026-03-02", 15), 95.0, 96.0, 91.0, 92.0)))
        assertEquals(Pricing.bid(12.0, 50.0), b.orders.single().executedPrice, 1e-9)
    }

    @Test fun knockOutInDerPauseBleibtBeimHeutigenVerhalten() {
        val start = turboKonto(order(Side.SELL, OrderType.LIMIT, 30.0, key = "KO1", produkt = turbo))
        // Die erste Kerze beruehrt die Schwelle: ab da wird fuer das Produkt nichts mehr nachgeholt
        val a = lauf(start, reihe(kerze(zeit("2026-03-02", 15), 90.0, 91.0, 84.0, 90.0), kerze(zeit("2026-03-02", 16), 105.0, 115.0, 104.0, 110.0)))
        assertSame(start, a)
    }

    @Test fun faktorZertifikatNurAmTagDesLetztenStands() {
        val tagNr = seit / 86_400_000L
        fun faktor(refTag: Long) = Derivative("F1", "TEST", "Test", DerivativeKind.FACTOR_LONG, factor = 3.0, factorPrice = 10.0, factorRef = 100.0, factorRefTag = refTag, createdAt = aufgabe - 86_400_000L)
        fun start(d: Derivative) = konto(10.0, order(Side.SELL, OrderType.STOP, 9.0, key = "F1", produkt = d), positionen = listOf(Position("F1", "Faktor", "TEST", 10.0, 10.0, aufgabe - 86_400_000L, d)))
        // Basiswert faellt um 4 %: Stand 10 * (1 - 0,12) = 8,80, der Stopp bei 9 ist erreicht
        val k = kerze(zeit("2026-03-02", 15), 99.0, 99.0, 96.0, 97.0)
        val a = lauf(start(faktor(tagNr)), reihe(k))
        assertEquals(OrderStatus.FILLED, a.orders.single().status)
        assertEquals(Pricing.bid(9.0, 50.0), a.orders.single().executedPrice, 1e-9)
        // Stand von einem frueheren Tag: der Tageswechsel fehlt, die Order bleibt offen
        val alt = start(faktor(tagNr - 1))
        assertSame(alt, lauf(alt, reihe(k)))
    }
}
