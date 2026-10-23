package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.Position
import de.troetenrepublik.app.data.Snapshot
import de.troetenrepublik.app.data.TaxState

// Zwei Depots zusammenfuehren.
// Guthaben, Einzahlungen und Auszahlungen addieren sich, gleiche Aktien werden zu einer Position mit
// gewichtetem Einstand, Buchungen, Orders, Sparplaene, Preisalarme und Wetten kommen dazu (gleiche Kennung nur
// einmal), die Merkliste wird vereinigt und der Depotverlauf zur Summe beider Verlaeufe.
object Zusammenfuehren {

    fun konten(alt: Account, neu: Account): Account = alt.copy(
        cash = alt.cash + neu.cash,
        deposited = alt.deposited + neu.deposited,
        withdrawn = alt.withdrawn + neu.withdrawn,
        transferredIn = alt.transferredIn + neu.transferredIn,
        interestAccrued = alt.interestAccrued + neu.interestAccrued,
        positions = positionen(alt.positions, neu.positions),
        orders = vereinigt(alt.orders, neu.orders) { it.id },
        transactions = vereinigt(alt.transactions, neu.transactions) { it.id }.sortedBy { it.ts },
        plans = vereinigt(alt.plans, neu.plans) { it.id },
        alerts = vereinigt(alt.alerts, neu.alerts) { it.id },
        wetten = vereinigt(alt.wetten, neu.wetten) { it.id },
        watchlist = (alt.watchlist + neu.watchlist).distinct(),
        watchMeta = neu.watchMeta + alt.watchMeta,
        notizen = neu.notizen + alt.notizen,
        history = verlauf(alt.history, neu.history),
        tax = steuer(alt.tax, neu.tax),
        createdAt = listOf(alt.createdAt, neu.createdAt).filter { it > 0 }.minOrNull() ?: 0L,
    )

    private fun <T> vereinigt(a: List<T>, b: List<T>, id: (T) -> String): List<T> {
        val da = a.map(id).toHashSet()
        return a + b.filter { id(it) !in da }
    }

    // Gleiche Aktie (gleicher Schluessel, kein Derivat): Stueck addiert, Einstand nach Stueck gewichtet.
    fun positionen(a: List<Position>, b: List<Position>): List<Position> {
        val aus = a.toMutableList()
        for (p in b) {
            val i = aus.indexOfFirst { it.key == p.key }
            if (i < 0) { aus += p; continue }
            val q = aus[i]
            if (q.derivative != null || p.derivative != null) continue
            val stueck = q.qty + p.qty
            aus[i] = q.copy(
                qty = stueck,
                avgPrice = if (stueck > 0) (q.qty * q.avgPrice + p.qty * p.avgPrice) / stueck else q.avgPrice,
                openedAt = minOf(q.openedAt, p.openedAt),
                importedAt = listOf(q.importedAt, p.importedAt).filter { it > 0 }.minOrNull() ?: 0L,
            )
        }
        return aus
    }

    // Summe zweier Depotverlaeufe: zu jedem Zeitpunkt der letzte Wert beider Depots (vor dem ersten: 0).
    fun verlauf(a: List<Snapshot>, b: List<Snapshot>): List<Snapshot> {
        if (a.isEmpty()) return b
        if (b.isEmpty()) return a
        val x = a.sortedBy { it.ts }
        val y = b.sortedBy { it.ts }
        val aus = ArrayList<Snapshot>(x.size + y.size)
        var i = 0
        var j = 0
        var la: Snapshot? = null
        var lb: Snapshot? = null
        while (i < x.size || j < y.size) {
            val t = minOf(x.getOrNull(i)?.ts ?: Long.MAX_VALUE, y.getOrNull(j)?.ts ?: Long.MAX_VALUE)
            while (i < x.size && x[i].ts == t) la = x[i++]
            while (j < y.size && y[j].ts == t) lb = y[j++]
            fun summe(f: (Snapshot) -> Double?): Double? {
                val u = la?.let(f)
                val v = lb?.let(f)
                return if ((la != null && u == null) || (lb != null && v == null)) null else (u ?: 0.0) + (v ?: 0.0)
            }
            aus += Snapshot(t, (la?.value ?: 0.0) + (lb?.value ?: 0.0), summe { it.securities }, summe { it.profit })
        }
        return aus
    }

    // Steuertoepfe desselben Jahres addieren; sonst gilt der Stand des juengeren Jahres.
    fun steuer(a: TaxState, b: TaxState): TaxState = when {
        a.year == b.year -> TaxState(
            a.year, a.allowanceUsed + b.allowanceUsed, a.lossPotStocks + b.lossPotStocks, a.lossPotGeneral + b.lossPotGeneral,
            a.taxPaid + b.taxPaid, a.gainsStocks + b.gainsStocks, a.gainsGeneral + b.gainsGeneral,
        )
        b.year > a.year -> b
        else -> a
    }
}
