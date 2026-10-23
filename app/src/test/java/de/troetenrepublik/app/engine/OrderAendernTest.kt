package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.Order
import de.troetenrepublik.app.data.OrderStatus
import de.troetenrepublik.app.data.OrderType
import de.troetenrepublik.app.data.Side
import de.troetenrepublik.app.data.Validity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrderAendernTest {
    private val now = 1_772_600_000_000L
    private fun limit(validity: Validity = Validity.GTC) =
        Order("o", "SAP.DE", "SAP", "SAP.DE", Side.BUY, OrderType.LIMIT, 2.0, 150.0, createdAt = now, validity = validity)

    @Test fun limitOrderBekommtNeueMengeUndNeuenPreis() {
        val acc = Trading.placeOrder(Account(cash = 1000.0), limit())
        val ablauf = acc.orders.single().expiresAt
        val r = Trading.orderAendern(acc, "o", 3.0, 140.0, 0.0, Validity.GTC, now + 1000)
        assertNull(r.error)
        val o = r.account.orders.single()
        assertEquals(3.0, o.qty, 1e-9)
        assertEquals(140.0, o.limitPrice, 1e-9)
        // Gleiche Gueltigkeit: der Ablauf bleibt
        assertEquals(ablauf, o.expiresAt)
        assertEquals(3.0 * 140.0 + 1.0, r.account.reserved, 1e-9)
    }

    @Test fun neueGueltigkeitLaeuftAbJetzt() {
        val acc = Trading.placeOrder(Account(cash = 1000.0), limit(Validity.DAY))
        val spaeter = now + 5L * 86_400_000L
        val o = Trading.orderAendern(acc, "o", 2.0, 150.0, 0.0, Validity.GTC, spaeter).account.orders.single()
        assertEquals(Validity.GTC, o.validity)
        assertEquals(spaeter + 360L * 86_400_000L, o.expiresAt)
    }

    @Test fun trailingStopRechnetDenStoppreisVomHoechstkursNeu() {
        val o = Trading.trailingStop(Order("t", "SAP.DE", "SAP", "SAP.DE", Side.SELL, OrderType.TRAILING_STOP, 1.0, 0.0, createdAt = now, trailPct = 5.0), 200.0)
        val acc = Trading.placeOrder(Account(), o)
        val neu = Trading.orderAendern(acc, "t", 1.0, 0.0, 10.0, Validity.GTC, now).account.orders.single()
        assertEquals(10.0, neu.trailPct, 1e-9)
        assertEquals(180.0, neu.limitPrice, 1e-9)
        assertEquals(200.0, neu.trailRef, 1e-9)
    }

    @Test fun ungueltigeEingabenUndGeschlosseneOrdersBleibenUnveraendert() {
        val acc = Trading.placeOrder(Account(cash = 1000.0), limit())
        assertEquals(Err.INVALID_QTY, Trading.orderAendern(acc, "o", 0.0, 150.0, 0.0, Validity.GTC, now).error)
        assertEquals(Err.INVALID_LIMIT, Trading.orderAendern(acc, "o", 2.0, -1.0, 0.0, Validity.GTC, now).error)
        val storniert = Trading.cancelOrder(acc, "o", now)
        val r = Trading.orderAendern(storniert, "o", 5.0, 100.0, 0.0, Validity.GTC, now)
        assertEquals(Err.ORDER_CLOSED, r.error)
        assertEquals(OrderStatus.CANCELLED, r.account.orders.single().status)
        assertEquals(2.0, r.account.orders.single().qty, 1e-9)
    }
}
