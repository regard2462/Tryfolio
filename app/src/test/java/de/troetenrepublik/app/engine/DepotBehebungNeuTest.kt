package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.Backup
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Derivative
import de.troetenrepublik.app.data.DerivativeKind
import de.troetenrepublik.app.data.Order
import de.troetenrepublik.app.data.OrderType
import de.troetenrepublik.app.data.Position
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.data.SavingsPlan
import de.troetenrepublik.app.data.Settings
import de.troetenrepublik.app.data.Side
import de.troetenrepublik.app.data.TaxState
import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.pow

// Behebungen am Depot, die neue Parameter oder Funktionen brauchen: Tageswechsel der Faktor-Zertifikate je Produkt,
// Verfall und Knock-out nach Tagen ohne App, Stopp-Orders, Sparplan nach der Pause, Splits uebernommener Depots,
// Rueckgang des Gesamtvermoegens, Wechselkurs des jeweiligen Tages und Sicherungen mit Rundungsresten.
class DepotBehebungNeuTest {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private val tag = 86_400_000L
    private fun at(date: String, hour: Int = 12, minute: Int = 0): Long =
        LocalDate.parse(date).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    private fun utc(date: String, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(LocalDate.parse(date), java.time.LocalTime.of(hour, minute)).toInstant(ZoneOffset.UTC).toEpochMilli()

    private val now = at("2026-03-04")
    private val heute = now / tag

    private fun faktor3(refTag: Long) = Derivative(
        id = "f3", underlying = "X", underlyingName = "X", kind = DerivativeKind.FACTOR_LONG, factor = 3.0,
        factorPrice = 100.0, factorRef = 100.0, factorRefTag = refTag, createdAt = at("2026-01-01"),
    )
    private fun konto(d: Derivative, openedAt: Long = now - 10 * tag, lastDay: Long = 0L) =
        Account(positions = listOf(Position(d.id, "F", "X", 1.0, 100.0, openedAt, d)), lastMaintainDay = lastDay)
    private fun kurs(preis: Double, t: Long = now) = mapOf("X" to Quote("X", price = preis, prevClose = preis, updatedAt = t))

    // M4: Tageswechsel je Produkt

    @Test fun tageswechselGehtOhneFrischenKursNichtVerloren() {
        val d = faktor3(heute - 1)
        val a1 = Trading.maintain(konto(d), kurs(105.0, now - 3_600_000L), now, dayChanged = true)
        assertEquals(d, a1.positions.single().derivative)
        // Spaeter am Tag mit frischem Kurs und verbrauchtem Tageshinweis: der Wechsel wird nachgeholt
        val a2 = Trading.maintain(a1, kurs(105.0), now + 60_000L, dayChanged = false)
        val r = a2.positions.single().derivative!!
        assertEquals(100.0 * 1.15 * Pricing.finanzierungJeTag(3.0, Pricing.RATE), r.factorPrice, 1e-9)
        assertEquals(105.0, r.factorRef, 1e-9)
        assertEquals(heute, r.factorRefTag)
    }

    @Test fun verpassteTageWerdenMitDenTagesschluessenGerollt() {
        // Montag +5 Prozent, Dienstag -5 Prozent, App zu: 3x Long rollt taeglich, 1,15 * 0,85 = 0,9775 (nicht 0,9925)
        val s = heute - 3
        val kerzen = listOf(
            Candle(s * tag + 12 * 3_600_000L, 100.0, 100.0, 100.0, 100.0),
            Candle((s + 1) * tag + 12 * 3_600_000L, 105.0, 105.0, 105.0, 105.0),
            Candle((s + 2) * tag + 12 * 3_600_000L, 99.75, 99.75, 99.75, 99.75),
        )
        val d = faktor3(s)
        val a = Trading.maintain(konto(d), kurs(99.75), now, dayChanged = true, schluesse = mapOf("X" to kerzen))
        val r = a.positions.single().derivative!!
        val kosten = Pricing.finanzierungJeTag(3.0, Pricing.RATE)
        assertEquals(100.0 * kosten.pow(3) * 1.15 * 0.85, r.factorPrice, 1e-9)
        assertEquals(99.75, r.factorRef, 1e-9)
        assertEquals(heute, r.factorRefTag)
    }

    @Test fun schluesseWerdenNurBeiVerpasstenTagenGebraucht() {
        assertEquals(listOf("X"), Trading.schluesseNoetig(konto(faktor3(heute - 3)), now))
        assertTrue(Trading.schluesseNoetig(konto(faktor3(heute - 1)), now).isEmpty())
        assertTrue(Trading.schluesseNoetig(konto(faktor3(0L)), now).isEmpty())
    }

    // M8: Verfall und Knock-out nach Tagen ohne App

    private fun schein() = Derivative(
        id = "c100", underlying = "X", underlyingName = "X", kind = DerivativeKind.CALL, strike = 100.0, ratio = 1.0,
        expiry = at("2026-03-06", 17, 30), createdAt = at("2026-01-01"),
    )

    @Test fun optionsscheinZaehltMitDemSchlussDesVerfallstags() {
        // Verfall Freitag, Basiswert Freitag 95, am Montag beim Oeffnen 108: der Schein verfaellt wertlos
        val montag = at("2026-03-09", 10)
        val kerzen = listOf(
            Candle(utc("2026-03-05", 8), 99.0, 99.0, 99.0, 99.0),
            Candle(utc("2026-03-06", 8), 95.0, 95.0, 95.0, 95.0),
            Candle(utc("2026-03-09", 8), 108.0, 108.0, 108.0, 108.0),
        )
        val acc = Account(positions = listOf(Position("c100", "Call", "X", 10.0, 1.0, at("2026-03-02"), schein())), tax = TaxState(year = 2026))
        val mitVerlauf = Trading.maintain(acc, kurs(108.0, montag), montag, schluesse = mapOf("X" to kerzen))
        val tx = mitVerlauf.transactions.single()
        assertEquals("EXPIRY_WORTHLESS", tx.tag)
        assertEquals(0.0, tx.price, 1e-9)
        // Ohne Verlauf bleibt es beim Kurs des Oeffnens
        val ohne = Trading.maintain(acc, kurs(108.0, montag), montag)
        assertEquals(8.0, ohne.transactions.single().price, 1e-9)
    }

    @Test fun knockoutAnEinemTagOhneLaufendeAppWirdErkannt() {
        val ko = Derivative(
            id = "ko90", underlying = "X", underlyingName = "X", kind = DerivativeKind.KO_LONG, strike = 80.0, barrier = 90.0, ratio = 1.0,
            createdAt = at("2026-01-01"),
        )
        val freitag = at("2026-03-06", 10)
        val mittwoch = utc("2026-03-04", 8)
        val kerzen = listOf(
            Candle(utc("2026-03-03", 8), 100.0, 100.0, 98.0, 99.0),
            Candle(mittwoch, 95.0, 96.0, 85.0, 94.0),
            Candle(utc("2026-03-05", 8), 94.0, 96.0, 93.0, 95.0),
        )
        val acc = Account(
            positions = listOf(Position("ko90", "KO", "X", 10.0, 5.0, at("2026-03-02"), ko)),
            lastMaintainDay = at("2026-03-03", 20) / tag,
        )
        val a = Trading.maintain(acc, kurs(95.0, freitag), freitag, schluesse = mapOf("X" to kerzen))
        val d = a.positions.single().derivative!!
        assertTrue(d.isKnockedOut)
        assertEquals(mittwoch, d.knockedOutAt)
        // Eroeffnung 95 liegt ueber der Schwelle: Restwert = innerer Wert an der Schwelle (90 - 80)
        assertEquals(10.0, d.restwert, 1e-9)
        // Ohne Verlauf bleibt das Produkt am Leben
        assertFalse(Trading.maintain(acc, kurs(95.0, freitag), freitag).positions.single().isKnockedOut)
    }

    // M12: Stopp-Orders auf der falschen Seite

    private fun stopp(side: Side, preis: Double) =
        Order("o", "SAP.DE", "SAP", "SAP.DE", side, OrderType.STOP, 1.0, preis, createdAt = now)

    @Test fun stoppAufDerFalschenSeiteWirdAbgelehnt() {
        assertEquals(Err.INVALID_STOP, Trading.orderError(stopp(Side.SELL, 210.0), 200.0))
        assertNull(Trading.orderError(stopp(Side.SELL, 190.0), 200.0))
        assertEquals(Err.INVALID_STOP, Trading.orderError(stopp(Side.BUY, 190.0), 200.0))
        assertNull(Trading.orderError(stopp(Side.BUY, 210.0), 200.0))
        // Ohne Kurs und bei Limit-Orders gibt es nichts zu pruefen
        assertNull(Trading.orderError(stopp(Side.SELL, 210.0)))
        assertNull(Trading.orderError(stopp(Side.SELL, 210.0).copy(type = OrderType.LIMIT), 200.0))
    }

    @Test fun beimAendernGiltDieselbeRegel() {
        val acc = Trading.placeOrder(Account(cash = 1_000.0), stopp(Side.BUY, 210.0))
        assertEquals(Err.INVALID_STOP, Trading.orderAendern(acc, "o", 1.0, 190.0, 0.0, acc.orders.single().validity, now, 200.0).error)
        assertTrue(Trading.orderAendern(acc, "o", 1.0, 220.0, 0.0, acc.orders.single().validity, now, 200.0).ok)
    }

    // M10: Sparplan nach der Pause

    private fun plan(lastRun: Long) = SavingsPlan("p", "SAP.DE", "SAP", 100.0, 2, lastRun = lastRun, createdAt = at("2026-01-01"), paused = true)

    @Test fun sparplanNachPauseKauftNichtSofort() {
        // Pause Januar bis April, Fortsetzen am 20.04.: naechster Termin ist der 02.05.
        val p = Trading.fortsetzen(plan(at("2026-01-02", 9)), at("2026-04-20"))
        assertFalse(p.paused)
        assertFalse(Trading.planDue(p, at("2026-04-20", 13)))
        assertFalse(Trading.planDue(p, at("2026-05-01", 12)))
        assertTrue(Trading.planDue(p, at("2026-05-02", 10)))
    }

    @Test fun sparplanVorDemTerminBehaeltDenAprilTermin() {
        val p = Trading.fortsetzen(plan(at("2026-01-02", 9)), at("2026-04-01", 9))
        assertEquals(at("2026-01-02", 9), p.lastRun)
        assertTrue(Trading.planDue(p, at("2026-04-02", 10)))
    }

    @Test fun sparplanOhneFaelligenTerminBleibtUnveraendert() {
        val p = Trading.fortsetzen(plan(at("2026-04-02", 9)), at("2026-04-20"))
        assertEquals(at("2026-04-02", 9), p.lastRun)
        assertFalse(Trading.planDue(p, at("2026-04-20", 13)))
    }

    // M6: Splits uebernommener Depots in der Zerlegung

    @Test fun zerlegungKenntFremdeSplits() {
        val d0 = LocalDate.of(2026, 5, 4).toEpochDay() * tag
        val d1 = d0 + tag
        // Uebernommen: 10 Stueck vor dem 10:1-Split, danach 100 (Buchung des Brokers am Splittag, Preis 0)
        val kauf = Transaction("k", d0 + 10 * 3_600_000L, TxType.BUY, "A", qty = 10.0, price = 500.0, symbol = "AAA", key = "AAA", tag = "TRANSFER_IN", via = "IMPORT_TR")
        val split = Transaction("c", d1 + 6 * 3_600_000L, TxType.BUY, "A", qty = 90.0, price = 0.0, symbol = "AAA", key = "AAA", tag = "CORPORATE", via = "IMPORT_TR")
        val acc = Account(positions = listOf(Position("AAA", "A", "AAA", 100.0, 50.0, d0, importedAt = d1)), transactions = listOf(kauf, split))
        val extern = mapOf("AAA" to listOf(Splits.Split(d1, 10.0, 1.0)))
        assertEquals(100.0, Depotzerlegung.bestand(acc, d1 - 1, extern)["AAA"]!!, 1e-9)
        assertEquals(100.0, Depotzerlegung.bestand(acc, d1 + 20 * 3_600_000L, extern)["AAA"]!!, 1e-9)
        // Ohne Split-Angabe wie bisher: vor dem Split zaehlen nur die 10 Stueck
        assertEquals(10.0, Depotzerlegung.bestand(acc, d1 - 1)["AAA"]!!, 1e-9)
    }

    // M5: Rueckgang des Gesamtvermoegens bei laufenden Einzahlungen

    @Test fun rueckgangBleibtAufDasDamaligeKapitalBezogen() {
        // 10.000 -> 9.000 (-10 %), dann 90.000 eingezahlt (99.000), Ende 100.000: der Rueckgang bleibt -10 Prozent
        val verlauf = listOf(0L to 10_000.0, 1L to 9_000.0, 2L to 99_000.0, 3L to 100_000.0)
        assertEquals(-10.0, Performanceanalyse.maxRueckgangProzent(verlauf, listOf(2L to 90_000.0)), 1e-9)
        assertEquals(-25.0, Performanceanalyse.maxRueckgangProzent(listOf(0L to 100.0, 1L to 120.0, 2L to 90.0, 3L to 110.0), emptyList()), 1e-9)
        assertEquals(0.0, Performanceanalyse.maxRueckgangProzent(listOf(0L to 100.0, 1L to 110.0), emptyList()), 1e-9)
    }

    // H3: Wechselkurs des jeweiligen Tages

    @Test fun verlaufRechnetMitDemKursDesKaufTags() {
        // 10 MSFT zu 400 Dollar, EURUSD beim Kauf 1,05, heute 1,17, Kurs unveraendert. Heute umgerechnete Kerzen: 400 / 1,17
        val d0 = LocalDate.of(2026, 1, 5).toEpochDay() * tag
        val verlauf = listOf(Candle(d0, 1.05, 1.05, 1.05, 1.05), Candle(d0 + 30 * tag, 1.10, 1.10, 1.10, 1.10), Candle(d0 + 60 * tag, 1.17, 1.17, 1.17, 1.17))
        fun eur(t: Long) = Candle(t, 400 / 1.17, 400 / 1.17, 400 / 1.17, 400 / 1.17)
        val kerzen = listOf(eur(d0 + 12 * 3_600_000L), eur(d0 + 30 * tag + 12 * 3_600_000L), eur(d0 + 60 * tag + 12 * 3_600_000L))
        val a = ExchangeRates.mitTageskurs(kerzen, 1.17, verlauf)
        assertEquals(400 / 1.05, a[0].close, 1e-9)
        assertEquals(400 / 1.10, a[1].close, 1e-9)
        assertEquals(400 / 1.17, a[2].close, 1e-9)
        // Einstand 3.809,52 Euro, Wert heute 3.418,80: Kurve und Position zeigen dieselben -10,3 Prozent
        val einstand = 10 * 400 / 1.05
        assertEquals(10 * a.last().close / einstand - 1.0, 10 * a.last().close / (10 * a.first().close) - 1.0, 1e-12)
        assertEquals(-0.1026, a.last().close / a.first().close - 1.0, 1e-4)
    }

    @Test fun ohneWechselkursVerlaufBleibenDieKerzenGleich() {
        val kerzen = listOf(Candle(1_000L, 1.0, 2.0, 0.5, 1.5))
        assertEquals(kerzen, ExchangeRates.mitTageskurs(kerzen, 1.17, emptyList()))
        assertEquals(kerzen, ExchangeRates.mitTageskurs(kerzen, 0.0, listOf(Candle(0L, 1.0, 1.0, 1.0, 1.0), Candle(5L, 1.0, 1.0, 1.0, 1.0))))
    }

    @Test fun basisWaehrungFuehrtPenceUndCentZusammen() {
        assertNull(ExchangeRates.basisWaehrung("EUR"))
        assertNull(ExchangeRates.basisWaehrung(""))
        assertEquals("GBP", ExchangeRates.basisWaehrung("GBp"))
        assertEquals("GBP", ExchangeRates.basisWaehrung("GBX"))
        assertEquals("USD", ExchangeRates.basisWaehrung("USd"))
        assertEquals("ILS", ExchangeRates.basisWaehrung("ILA"))
        assertEquals("ZAR", ExchangeRates.basisWaehrung("ZAc"))
        assertEquals("JPY", ExchangeRates.basisWaehrung("JPY"))
    }

    // M9: Sicherungen mit Rundungsresten

    @Test fun sicherungMitKleinstemNegativemGuthabenLaesstSichLesen() {
        val text = Backup.export(Account(cash = -3.6e-12, deposited = 100.0), Settings(), now)
        val p = (Backup.parse(text) as Backup.Outcome.Ok).payload
        assertEquals(0.0, p.account.cash, 0.0)
        assertEquals(100.0, p.account.deposited, 1e-9)
        assertTrue(Backup.parse(Backup.export(Account(cash = -1.0), Settings(), now)) is Backup.Outcome.Error)
    }

    @Test fun kasseBehandeltKleinstbetraegeAlsNull() {
        assertEquals(0.0, Trading.kasse(-1e-12), 0.0)
        assertEquals(0.0, Trading.kasse(0.004), 0.0)
        assertEquals(0.01, Trading.kasse(0.01), 0.0)
        assertEquals(-5.0, Trading.kasse(-5.0), 0.0)
    }
}
