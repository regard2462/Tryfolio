package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.Order
import de.troetenrepublik.app.data.OrderStatus
import de.troetenrepublik.app.data.OrderType
import de.troetenrepublik.app.data.Side

// Stopp-Verkauf mit Gewinnziel (0.78): zwei Verkaufsorders ueber dieselben Stuecke, ein Stopp unter und ein Limit ueber
// dem Kurs, ueber Order.paar verknuepft. Wird eine ausgefuehrt, ist die andere gestrichen. Koennten beide in derselben
// Kerze ausgeloest haben, zaehlt der Stopp. Orders ohne paar (auch alle aus aelteren Fassungen) bleiben einzeln.
object OrderPaar {
    // Grund der gestrichenen Order
    const val GRUND = "LINKED"

    // null, wenn das Paar so aufgegeben werden kann. kurs: aktueller Kurs in Euro (0 = unbekannt).
    fun fehler(stopp: Order, ziel: Order, kurs: Double = 0.0): String? = when {
        stopp.side != Side.SELL || ziel.side != Side.SELL || stopp.type != OrderType.STOP || ziel.type != OrderType.LIMIT -> Err.INVALID_LIMIT
        stopp.key != ziel.key || stopp.qty != ziel.qty -> Err.INVALID_QTY
        else -> Trading.orderError(stopp, kurs) ?: Trading.orderError(ziel, kurs)
            ?: Err.INVALID_LIMIT.takeIf { ziel.limitPrice <= stopp.limitPrice || (kurs > 0 && ziel.limitPrice <= kurs) }
    }

    // Beide Orders verknuepft anlegen, der Stopp zuerst. Bei einem Fehler bleibt das Konto, wie es ist.
    fun anlegen(account: Account, stopp: Order, ziel: Order): Account {
        if (fehler(stopp, ziel) != null || stopp.id == ziel.id) return account
        if (account.orders.any { it.id == stopp.id || it.id == ziel.id }) return account
        return Trading.placeOrder(Trading.placeOrder(account, stopp.copy(paar = ziel.id)), ziel.copy(paar = stopp.id))
    }

    // Die noch offene Gegenorder; null ohne Paar.
    fun partner(orders: List<Order>, o: Order): Order? =
        if (o.paar.isEmpty()) null else orders.firstOrNull { it.id == o.paar && it.status == OrderStatus.OPEN && it.paar == o.id }

    // Nach der Ausfuehrung der Order id: die offene Gegenorder streichen.
    fun partnerStreichen(account: Account, id: String, now: Long): Account {
        val o = account.orders.firstOrNull { it.id == id } ?: return account
        val p = partner(account.orders, o) ?: return account
        return account.copy(orders = account.orders.map {
            if (it.id == p.id) it.copy(status = OrderStatus.CANCELLED, executedAt = now, reason = GRUND) else it
        })
    }

    // Nachholen: das Ziel wartet, wenn der verknuepfte Stopp in derselben Kerze erreicht ist (tief: Tief der Kerze).
    fun stoppGehtVor(orders: List<Order>, o: Order, tief: Double): Boolean {
        if (o.type != OrderType.LIMIT) return false
        val p = partner(orders, o) ?: return false
        return p.type == OrderType.STOP && tief <= p.limitPrice
    }

    // Geaenderte Stueckzahl einer Order auf die offene Gegenorder uebertragen.
    fun stueckAngleichen(account: Account, id: String): Account {
        val o = account.orders.firstOrNull { it.id == id } ?: return account
        val p = partner(account.orders, o) ?: return account
        if (p.qty == o.qty) return account
        return account.copy(orders = account.orders.map { if (it.id == p.id) it.copy(qty = o.qty) else it })
    }
}
