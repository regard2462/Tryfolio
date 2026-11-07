package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.Derivative
import de.troetenrepublik.app.data.DerivativeKind
import de.troetenrepublik.app.data.Lot
import de.troetenrepublik.app.data.Position
import de.troetenrepublik.app.data.TaxState
import de.troetenrepublik.app.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class Fassung594SteuerTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private fun at(d: String): Long = LocalDate.parse(d).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun fifoVerbrauchtDieAeltestenPostenZuerst() {
        var acc = Account(cash = 10_000.0, tax = TaxState(year = 2026))
        acc = Trading.buy(acc, "SAP.DE", "SAP", "SAP.DE", 10.0, 100.0, now = at("2026-02-01"), fee = 0.0).account
        acc = Trading.buy(acc, "SAP.DE", "SAP", "SAP.DE", 10.0, 200.0, now = at("2026-03-01"), fee = 0.0).account
        assertEquals(150.0, acc.positions.single().avgPrice, 1e-9)
        val r = Trading.sell(acc, "SAP.DE", 10.0, 200.0, at("2026-04-01"), fee = 0.0)
        val tx = r.account.transactions.first { it.type == TxType.SELL }
        // Beispiel aus § 20 Abs. 4 Satz 7 EStG: 1.000 EUR Gewinn, nicht 500
        assertEquals(1000.0, tx.realized, 1e-9)
        val rest = r.account.positions.single()
        assertEquals(200.0, rest.avgPrice, 1e-9)
        assertEquals(listOf(Lot(10.0, 200.0, at("2026-03-01"))), rest.lots)
    }

    @Test fun kaufUndVerkaufsgebuehrMindernDenGewinn() {
        var acc = Account(cash = 10_000.0, tax = TaxState(year = 2026))
        acc = Trading.buy(acc, "SAP.DE", "SAP", "SAP.DE", 10.0, 100.0, now = at("2026-02-01"), fee = 1.0).account
        val r = Trading.sell(acc, "SAP.DE", 10.0, 150.0, at("2026-04-01"), fee = 1.0)
        val tx = r.account.transactions.first { it.type == TxType.SELL }
        assertEquals(500.0, tx.realized, 1e-9)
        assertEquals(Tax.steuer(498.0), tx.tax, 1e-9)
    }

    @Test fun altePositionOhnePostenZaehltAlsEinPosten() {
        val p = Position("SAP.DE", "SAP", "SAP.DE", 5.0, 80.0, at("2025-01-01"))
        assertEquals(listOf(Lot(5.0, 80.0, at("2025-01-01"))), Posten.von(p))
    }

    @Test fun verlustImSelbenJahrHoltSteuerZurueck() {
        // Beispiel aus C1: 1.500 EUR Gewinn, Freistellungsauftrag 1.000 EUR, danach 800 EUR Verlust
        val t = at("2026-01-15")
        val gewinn = Tax.apply(TaxState(year = 2026), 1500.0, Tax.Kind.GENERAL, 1000.0, t)
        assertEquals(Tax.steuer(500.0), gewinn.tax, 1e-9)
        val verlust = Tax.apply(gewinn.state, -800.0, Tax.Kind.GENERAL, 1000.0, at("2026-03-15"))
        assertEquals(-gewinn.tax, verlust.tax, 1e-9)
        assertEquals(700.0, verlust.state.allowanceUsed, 1e-9)
        assertEquals(0.0, verlust.state.lossPotGeneral, 1e-9)
        assertEquals(0.0, verlust.state.taxPaid, 1e-9)
    }

    @Test fun aktienverlustErstattetKeineSteuerAufZinsen() {
        val zinsen = Tax.apply(TaxState(year = 2026), 300.0, Tax.Kind.GENERAL, 0.0, at("2026-01-15"))
        val verlust = Tax.apply(zinsen.state, -200.0, Tax.Kind.STOCK, 0.0, at("2026-02-15"))
        assertEquals(0.0, verlust.tax, 1e-9)
        assertEquals(200.0, verlust.state.lossPotStocks, 1e-9)
    }

    @Test fun soliWirdAbgerundet() {
        // 25 % von 500 = 125, Soli 6,875 -> 6,87
        assertEquals(131.87, Tax.steuer(500.0), 1e-9)
    }

    @Test fun aktienEtfMitTeilfreistellung() {
        assertEquals(0.30, Trading.teilfreistellung("EUNL.DE", "iShares Core MSCI World"), 1e-9)
        assertEquals(0.0, Trading.teilfreistellung("4GLD.DE", "Xetra-Gold"), 1e-9)
        assertEquals(0.0, Trading.teilfreistellung("SAP.DE", "SAP"), 1e-9)
        val acc = Account(cash = 0.0, positions = listOf(Position("EUNL.DE", "iShares Core MSCI World", "EUNL.DE", 10.0, 100.0, at("2026-01-02"))), tax = TaxState(year = 2026))
        val tx = Trading.sell(acc, "EUNL.DE", 10.0, 200.0, at("2026-05-01"), fee = 0.0).account.transactions.first { it.type == TxType.SELL }
        assertEquals(Tax.steuer(700.0), tx.tax, 1e-9)
    }

    @Test fun kryptoOhneAbzugNachEinemJahrSteuerfrei() {
        val btc = Position("BTC-EUR", "Bitcoin", "BTC-EUR", 1.0, 20_000.0, at("2025-03-01"), lots = listOf(Lot(0.5, 20_000.0, at("2025-03-01")), Lot(0.5, 40_000.0, at("2026-02-01"))))
        val acc = Account(cash = 0.0, positions = listOf(btc), tax = TaxState(year = 2026))
        val r = Trading.sell(acc, "BTC-EUR", 1.0, 50_000.0, at("2026-06-01"), fee = 0.0)
        val tx = r.account.transactions.first { it.type == TxType.SELL }
        assertEquals(0.0, tx.tax, 1e-9)
        // Nur der zweite Posten liegt unter einem Jahr: 0,5 * (50.000 - 40.000)
        assertEquals(5_000.0, r.account.tax.privateGains, 1e-9)
        assertEquals(0.0, r.account.tax.allowanceUsed, 1e-9)
    }

    @Test fun dividendeNachBestandAmExTag() {
        var acc = Account(cash = 10_000.0, tax = TaxState(year = 2026))
        acc = Trading.buy(acc, "SAP.DE", "SAP", "SAP.DE", 10.0, 100.0, now = at("2026-02-01"), fee = 0.0).account
        // Nach dem Ex-Tag, aber vor der Zahlung zur Haelfte verkauft
        acc = Trading.sell(acc, "SAP.DE", 5.0, 100.0, at("2026-05-11"), fee = 0.0).account
        val d = Trading.Dividend("SAP.DE", at("2026-05-10"), 2.0)
        val div = Trading.bookDividends(acc, listOf(d), at("2026-05-20")).transactions.first { it.type == TxType.DIVIDEND }
        assertEquals(10.0, div.qty, 1e-9)
        assertEquals(20.0, div.amount + div.tax, 1e-9)
    }

    @Test fun waehrungseinheitenUnterPfundUndDollar() {
        assertEquals("p", Derivatives.currencySymbol("GBp"))
        assertEquals("£", Derivatives.currencySymbol("GBP"))
        assertEquals("¢", Derivatives.currencySymbol("USd"))
        assertEquals(400.0, ExchangeRates.rate("ILA", mapOf("ILS" to 4.0))!!, 1e-9)
        assertEquals(2000.0, ExchangeRates.rate("ZAc", mapOf("ZAR" to 20.0))!!, 1e-9)
    }

    @Test fun shortFaktorErhaeltZinsenLongZahlt() {
        assertTrue(Pricing.finanzierungJeTag(-5.0, 0.03) > 1.0)
        assertTrue(Pricing.finanzierungJeTag(5.0, 0.03) < 1.0)
    }

    @Test fun miniFutureZahltRestwertZwischenSchwelleUndBasispreis() {
        val d = Derivative(id = "k", underlying = "SAP.DE", underlyingName = "SAP", kind = DerivativeKind.KO_LONG, strike = 80.0, barrier = 82.0, ratio = 1.0)
        // Stop-Loss-Kurs ist der erkannte Kurs, hoechstens die Schwelle
        assertEquals(1.0, Pricing.restwert(d, 81.0), 1e-9)
        assertEquals(2.0, Pricing.restwert(d, 85.0), 1e-9)
        // Kurslucke unter den Basispreis: nur der Mindestwert
        assertEquals(Pricing.KO_RESIDUAL, Pricing.restwert(d, 70.0), 1e-12)
        assertTrue(Pricing.tagesKnockout(d, dayLow = 81.5, dayHigh = 90.0))
    }

    @Test fun splitRechnetPostenUm() {
        val lots = listOf(Lot(10.0, 100.0, at("2026-01-01")), Lot(5.0, 30.0, at("2026-06-01")))
        assertEquals(listOf(Lot(40.0, 25.0, at("2026-01-01")), Lot(5.0, 30.0, at("2026-06-01"))), Posten.split(lots, 4.0, at("2026-03-01")))
    }
}
