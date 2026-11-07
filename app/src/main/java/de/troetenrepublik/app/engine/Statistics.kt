package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

// Einnahmen, Ausgaben und Investments eines Zeitraums als Baum, den die Oberflaeche Stufe fuer Stufe aufklappt.
object Statistics {

    // Knoten im Baum: Kennung fuer die Beschriftung, Betrag, Kinder. symbol fuehrt zur Wertpapierseite.
    data class Node(
        val id: String,
        val amount: Double,
        val children: List<Node> = emptyList(),
        val symbol: String = "",
        val name: String = "",
        val count: Int = 0,
    )

    sealed class Period {
        data class Month(val ym: YearMonth) : Period()
        data class Year(val year: Int) : Period()
        object All : Period()
    }

    fun inPeriod(ts: Long, p: Period, zone: ZoneId = ZoneId.systemDefault()): Boolean = when (p) {
        is Period.All -> true
        is Period.Year -> Instant.ofEpochMilli(ts).atZone(zone).year == p.year
        is Period.Month -> YearMonth.from(Instant.ofEpochMilli(ts).atZone(zone)) == p.ym
    }

    // Vorheriger Zeitraum gleicher Laenge, fuer den Vergleich.
    fun previous(p: Period): Period? = when (p) {
        is Period.All -> null
        is Period.Year -> Period.Year(p.year - 1)
        is Period.Month -> Period.Month(p.ym.minusMonths(1))
    }

    // Verfuegbare Monate: vom ersten Buchungsmonat bis heute, hoechstens 24.
    fun months(transactions: List<Transaction>, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<YearMonth> {
        val current = YearMonth.from(Instant.ofEpochMilli(now).atZone(zone))
        val first = transactions.minOfOrNull { it.ts }?.let { YearMonth.from(Instant.ofEpochMilli(it).atZone(zone)) } ?: current
        val out = mutableListOf<YearMonth>()
        var m = current
        while (m >= first && out.size < 24) { out += m; m = m.minusMonths(1) }
        return out.reversed()
    }

    // INCOME: SALES je Wertpapier, DEPOSITS, EARNINGS (DIVIDENDS und INTEREST), REFUNDS
    // EXPENSES: FEES, TAXES, WITHDRAWALS
    // INVESTMENTS: PURCHASES und PLANS je Wertpapier
    fun tree(transactions: List<Transaction>, period: Period, zone: ZoneId = ZoneId.systemDefault()): Node {
        val txs = transactions.filter { inPeriod(it.ts, period, zone) }

        fun bySecurity(id: String, list: List<Transaction>, amountOf: (Transaction) -> Double): Node {
            val groups = list.groupBy { it.symbol.ifBlank { it.title } }
            val children = groups.map { (sym, l) ->
                Node("SEC:$sym", l.sumOf(amountOf), symbol = l.first().symbol, name = l.first().title, count = l.size)
            }.filter { it.amount > 0 }.sortedByDescending { it.amount }
            return Node(id, children.sumOf { it.amount }, children, count = list.size)
        }

        val sales = bySecurity("SALES", txs.filter { it.type == TxType.SELL }) { it.amount + it.tax }
        val deposits = txs.filter { it.type == TxType.DEPOSIT }.let { Node("DEPOSITS", it.sumOf { t -> t.amount }, count = it.size) }
        val dividends = bySecurity("DIVIDENDS", txs.filter { it.type == TxType.DIVIDEND }) { it.amount + it.tax }
        val interest = txs.filter { it.type == TxType.INTEREST }.let { Node("INTEREST", it.sumOf { t -> t.amount + t.tax }, count = it.size) }
        val earnings = Node("EARNINGS", dividends.amount + interest.amount, listOf(dividends, interest).filter { it.amount > 0 }, count = dividends.count + interest.count)
        val refunds = bySecurity("REFUNDS", txs.filter { (it.type == TxType.KNOCKOUT || it.type == TxType.EXPIRY) && it.amount > 0 }) { it.amount }
        val income = Node("INCOME", sales.amount + deposits.amount + earnings.amount + refunds.amount,
            listOf(sales, deposits, earnings, refunds).filter { it.amount > 0 })

        val fees = txs.filter { it.type == TxType.FEE }.let { Node("FEES", -it.sumOf { t -> t.amount }, count = it.size) }
        val taxes = Node("TAXES", txs.sumOf { it.tax }, count = txs.count { it.tax > 0 })
        val withdrawals = txs.filter { it.type == TxType.WITHDRAWAL }.let { Node("WITHDRAWALS", -it.sumOf { t -> t.amount }, count = it.size) }
        val expenses = Node("EXPENSES", fees.amount + taxes.amount + withdrawals.amount, listOf(fees, taxes, withdrawals).filter { it.amount > 0 })

        // Uebernommene Positionen sind kein Geldfluss und zaehlen hier nicht mit
        val buys = txs.filter { it.type == TxType.BUY && it.tag != "TRANSFER_IN" }
        val plans = bySecurity("PLANS", buys.filter { it.via == "PLAN" || it.subtitle.startsWith("Sparplan") }) { -it.amount }
        val purchases = bySecurity("PURCHASES", buys.filter { !(it.via == "PLAN" || it.subtitle.startsWith("Sparplan")) }) { -it.amount }
        val investments = Node("INVESTMENTS", purchases.amount + plans.amount, listOf(purchases, plans).filter { it.amount > 0 })

        return Node("ROOT", income.amount + expenses.amount + investments.amount, listOf(income, expenses, investments).filter { it.amount > 0 })
    }

    fun net(root: Node): Double =
        (root.children.firstOrNull { it.id == "INCOME" }?.amount ?: 0.0) -
            (root.children.firstOrNull { it.id == "EXPENSES" }?.amount ?: 0.0) -
            (root.children.firstOrNull { it.id == "INVESTMENTS" }?.amount ?: 0.0)

    // Sparquote in Prozent der Einnahmen.
    fun investRate(root: Node): Double {
        val income = root.children.firstOrNull { it.id == "INCOME" }?.amount ?: 0.0
        val inv = root.children.firstOrNull { it.id == "INVESTMENTS" }?.amount ?: 0.0
        return if (income > 0) inv / income * 100.0 else 0.0
    }

    fun find(root: Node, id: String): Node? {
        if (root.id == id) return root
        for (c in root.children) find(c, id)?.let { return it }
        return null
    }

    // Kreispackung: die groesste Blase in die Mitte, die uebrigen auf einer Spirale an die erste freie Stelle.
    // Die Flaeche ist proportional zum Betrag, mit Mindestradius fuer die Beschriftung.
    // Ergebnis in Einheiten, die der Aufrufer auf seine Flaeche skaliert.
    data class Bubble(val node: Node, val x: Double, val y: Double, val r: Double)

    fun pack(nodes: List<Node>, minR: Double = 0.22, maxR: Double = 1.0): List<Bubble> {
        val sorted = nodes.filter { it.amount > 0 }.sortedByDescending { it.amount }
        if (sorted.isEmpty()) return emptyList()
        val maxAmount = sorted.first().amount
        val radii = sorted.map { n -> (Math.sqrt(n.amount / maxAmount) * maxR).coerceAtLeast(minR) }
        val placed = mutableListOf<Bubble>()
        placed += Bubble(sorted[0], 0.0, 0.0, radii[0])
        for (i in 1 until sorted.size) {
            val r = radii[i]
            var best: Bubble? = null
            var angle = 0.0
            var dist = 0.0
            while (best == null && dist < 20) {
                val x = Math.cos(angle) * dist
                val y = Math.sin(angle) * dist
                val free = placed.all { b -> Math.hypot(b.x - x, b.y - y) >= b.r + r + 0.03 }
                if (free) best = Bubble(sorted[i], x, y, r)
                angle += 0.25
                dist += 0.012
            }
            placed += best ?: Bubble(sorted[i], dist, 0.0, r)
        }
        // Schwerpunkt in den Ursprung
        val cx = placed.sumOf { it.x } / placed.size
        val cy = placed.sumOf { it.y } / placed.size
        return placed.map { it.copy(x = it.x - cx, y = it.y - cy) }
    }
}
