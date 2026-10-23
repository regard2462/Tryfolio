package de.troetenrepublik.app.market.broker

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.Order
import de.troetenrepublik.app.data.OrderType
import de.troetenrepublik.app.data.Position
import de.troetenrepublik.app.data.TaxState
import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType

// Das Konto beim Broker als Depot der App. Betraege in anderer Waehrung werden zum aktuellen Kurs in Euro umgerechnet,
// kurs liefert Einheiten der Waehrung je Euro, null ohne Kurs. Vom Uebungsdepot bleiben nur Watchlist, Preisalarme
// und der Verlauf seit der Verbindung. Alles andere kommt vom Broker, damit echtes Konto und Uebungsdepot nie gemischt
// werden. Sparplaene, Wetten und die Steuerverrechnung des Uebungsdepots entfallen.
object BrokerAbbild {

    fun konto(stand: BrokerStand, alt: Account, kurs: (String) -> Double?, jetzt: Long): Account? {
        fun eur(betrag: Double, waehrung: String): Double? = kurs(waehrung)?.takeIf { it > 0 }?.let { betrag / it }
        val cash = eur(stand.cash, stand.waehrung) ?: return null
        val altePos = alt.positions.associateBy { it.key }
        val positionen = stand.positionen.filter { it.menge != 0.0 }.map { p ->
            Position(
                key = p.symbol, name = p.name.ifBlank { p.symbol }, symbol = p.symbol, qty = p.menge,
                avgPrice = eur(p.einstand, p.waehrung) ?: 0.0,
                openedAt = altePos[p.symbol]?.openedAt?.takeIf { it > 0 } ?: jetzt,
            )
        }
        val orders = stand.orders.map { o ->
            Order(
                id = "broker-" + o.id, key = o.symbol, name = o.name.ifBlank { o.symbol }, symbol = o.symbol,
                side = o.seite, type = o.typ, qty = o.menge,
                limitPrice = (if (o.typ == OrderType.LIMIT) o.limit else o.stop ?: o.limit)?.let { eur(it, o.waehrung) } ?: 0.0,
                status = o.status, createdAt = o.erstellt, executedAt = o.ausgefuehrt ?: 0L,
                executedPrice = o.kurs?.let { eur(it, o.waehrung) } ?: 0.0,
                validity = o.gueltigkeit, trailPct = o.trailPct ?: 0.0, note = "",
            )
        }
        val umsaetze = stand.umsaetze.sortedBy { it.zeit }.map { u ->
            val betrag = eur(u.betrag, u.waehrung) ?: 0.0
            Transaction(
                id = "broker-" + u.id, ts = u.zeit, type = u.typ,
                title = u.symbol?.let { s -> stand.positionen.firstOrNull { it.symbol == s }?.name?.takeIf { it.isNotBlank() } ?: s } ?: u.text,
                subtitle = u.text,
                amount = betrag, qty = u.menge, price = eur(u.kurs, u.waehrung) ?: 0.0,
                symbol = u.symbol.orEmpty(), key = u.symbol.orEmpty(), fee = eur(u.gebuehr, u.waehrung) ?: 0.0,
                tag = when (u.typ) { TxType.BUY -> "BUY"; TxType.SELL -> "SELL"; TxType.DEPOSIT -> "DEPOSIT"; TxType.WITHDRAWAL -> "WITHDRAWAL"; TxType.DIVIDEND -> "DIVIDEND"; TxType.FEE -> "FEE"; TxType.INTEREST -> "INTEREST"; else -> "" },
                via = "BROKER",
            )
        }
        // Gemessen am eingesetzten Kapital: Bargeld plus Einstandswert der Positionen
        val eingesetzt = cash + positionen.sumOf { it.qty * it.avgPrice }
        return alt.copy(
            cash = cash,
            deposited = eingesetzt,
            positions = positionen,
            orders = orders,
            // Ausgeloeste Preisalarme der App bleiben in den Umsaetzen stehen
            transactions = (umsaetze + alt.transactions.filter { it.type == TxType.ALERT }).sortedBy { it.ts },
            plans = emptyList(),
            wetten = emptyList(),
            tax = TaxState(),
            interestAccrued = 0.0,
            withdrawn = 0.0,
            transferredIn = 0.0,
        )
    }

    // Beim Verbinden wird das Uebungsdepot ersetzt, nur Watchlist und Alarme bleiben.
    fun ersetzen(stand: BrokerStand, uebung: Account, kurs: (String) -> Double?, jetzt: Long): Account? =
        konto(stand, Account(watchlist = uebung.watchlist, watchMeta = uebung.watchMeta, alerts = uebung.alerts, createdAt = jetzt), kurs, jetzt)
}
