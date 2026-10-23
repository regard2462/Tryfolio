package de.troetenrepublik.app.market.broker

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.OrderStatus
import de.troetenrepublik.app.data.OrderType
import de.troetenrepublik.app.data.Position
import de.troetenrepublik.app.data.SavingsPlan
import de.troetenrepublik.app.data.Side
import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.data.Validity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Das Konto beim Broker ersetzt das Uebungsdepot; Betraege in Euro umgerechnet.
class BrokerAbbildTest {

    private val kurs: (String) -> Double? = { w -> when (w) { "EUR" -> 1.0; "USD" -> 1.25; else -> null } }

    private val stand = BrokerStand(
        waehrung = "USD", cash = 1250.0, kaufkraft = 2500.0,
        positionen = listOf(BrokerPosition("AAPL", "Apple Inc.", 10.0, 200.0, "USD")),
        orders = listOf(BrokerOrder("o1", "AAPL", "Apple Inc.", Side.BUY, OrderType.LIMIT, 2.0, 150.0, null, null, OrderStatus.OPEN, 1000L, null, null, "USD", Validity.GTC)),
        umsaetze = listOf(BrokerUmsatz("u1", 500L, TxType.BUY, "AAPL", 10.0, 200.0, -2000.0, "USD")),
    )

    @Test
    fun ersetztDasUebungsdepot() {
        val uebung = Account(
            cash = 50_000.0, deposited = 50_000.0, watchlist = listOf("SAP.DE"),
            positions = listOf(Position("SAP.DE", "SAP", "SAP.DE", 5.0, 100.0, 1L)),
            plans = listOf(SavingsPlan("p", "SAP.DE", "SAP", 50.0, 2, 0L, 0L)),
            transactions = listOf(Transaction("t", 1L, TxType.DEPOSIT, "Einzahlung", amount = 50_000.0)),
        )
        val a = BrokerAbbild.ersetzen(stand, uebung, kurs, 9_999L)!!
        assertEquals(1000.0, a.cash, 1e-9)
        assertEquals(listOf("SAP.DE"), a.watchlist)
        assertEquals(listOf("AAPL"), a.positions.map { it.symbol })
        assertEquals(160.0, a.positions[0].avgPrice, 1e-9)
        assertTrue(a.plans.isEmpty())
        assertEquals(listOf("broker-u1"), a.transactions.map { it.id })
        assertEquals(-1600.0, a.transactions[0].amount, 1e-9)
        // Limit in Euro, Order-Nummer des Brokers erkennbar
        assertEquals("broker-o1", a.orders[0].id)
        assertEquals(120.0, a.orders[0].limitPrice, 1e-9)
        // Eingesetzt: Bargeld plus Einstandswert
        assertEquals(1000.0 + 1600.0, a.deposited, 1e-9)
    }

    @Test
    fun alarmeBleibenStehen() {
        val alt = Account(transactions = listOf(Transaction("a", 700L, TxType.ALERT, "Alarm")))
        val a = BrokerAbbild.konto(stand, alt, kurs, 9_999L)!!
        assertEquals(listOf("broker-u1", "a"), a.transactions.map { it.id })
    }

    @Test
    fun ohneWechselkursKeinAbbild() {
        assertNull(BrokerAbbild.konto(stand.copy(waehrung = "CHF"), Account(), kurs, 0L))
    }
}
