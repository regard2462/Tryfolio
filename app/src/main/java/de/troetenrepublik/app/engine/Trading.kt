package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Derivative
import de.troetenrepublik.app.data.Order
import de.troetenrepublik.app.data.OrderStatus
import de.troetenrepublik.app.data.OrderType
import de.troetenrepublik.app.data.PlanInterval
import de.troetenrepublik.app.data.Position
import de.troetenrepublik.app.data.PriceAlert
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.data.SavingsPlan
import de.troetenrepublik.app.data.Side
import de.troetenrepublik.app.data.Snapshot
import de.troetenrepublik.app.data.TaxState
import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.data.Validity
import de.troetenrepublik.app.data.Valuation
import de.troetenrepublik.app.data.ValuedPosition
import de.troetenrepublik.app.data.WatchMeta
import de.troetenrepublik.app.data.isFactor
import de.troetenrepublik.app.data.isKnockout
import de.troetenrepublik.app.data.isOption
import de.troetenrepublik.app.market.Symbols
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// Die Oberflaeche uebersetzt die Fehlercodes in die gewaehlte Sprache.
object Err {
    const val INVALID_QTY = "INVALID_QTY"
    const val NO_PRICE = "NO_PRICE"
    const val INVALID_FEE = "INVALID_FEE"
    const val NOT_TRADABLE = "NOT_TRADABLE"
    const val WHOLE_PIECES = "WHOLE_PIECES"
    const val TOO_LARGE = "TOO_LARGE"
    const val NOT_ENOUGH_CASH = "NOT_ENOUGH_CASH"
    const val NOT_IN_DEPOT = "NOT_IN_DEPOT"
    const val NOT_ENOUGH_PIECES = "NOT_ENOUGH_PIECES"
    const val FEE_NOT_COVERED = "FEE_NOT_COVERED"
    const val INVALID_LIMIT = "INVALID_LIMIT"
    const val INVALID_TRAIL = "INVALID_TRAIL"
    const val MISSING_SECURITY = "MISSING_SECURITY"
    const val INVALID_AMOUNT = "INVALID_AMOUNT"
    const val ONLY_AVAILABLE = "ONLY_AVAILABLE"      // extra: verfuegbarer Betrag
    const val EXPIRED = "EXPIRED"
    const val PRODUCT_GONE = "PRODUCT_GONE"
    const val INVALID_DATE = "INVALID_DATE"
    const val ORDER_CLOSED = "ORDER_CLOSED"
}

data class TradeResult(
    val account: Account,
    val error: String? = null,
    val filled: Order? = null,
    // Zusatzwert zum Fehler, etwa der verfuegbare Betrag.
    val extra: Double = 0.0,
) {
    val ok: Boolean get() = error == null
}

// Handelsbuch als reine Funktionen auf Account, ohne Android-Abhaengigkeiten, damit die Rechnung testbar bleibt.
// Buchungen tragen maschinenlesbare Felder, die Texte entstehen nicht hier.
object Trading {

    const val FEE = 1.0

    const val START_CASH = 50_000.0

    const val MAX_HISTORY = 4000

    // Nach einem Knock-out bleibt das Produkt zwei Tage sichtbar, dann zahlt der Emittent den Restwert.
    const val KO_SETTLE_MS = 2L * 86_400_000L

    private const val MAX_TX = 3000

    private val seq = java.util.concurrent.atomic.AtomicLong()
    fun newId(prefix: String, now: Long): String = "$prefix-$now-${seq.getAndIncrement()}"

    // Kurse

    // Basispunkte: Basiswerte enger, Hebelprodukte weiter.
    data class Spread(val stockBps: Double = 20.0, val derivativeBps: Double = 50.0) {
        fun forProduct(derivative: Derivative?): Double = if (derivative == null) stockBps else derivativeBps
    }

    // Bei Basiswerten der Boersenkurs, bei Derivaten der aus dem Basiswert berechnete Wert.
    fun priceOf(key: String, symbol: String, derivative: Derivative?, quotes: Map<String, Quote>, now: Long, rate: Double = Pricing.RATE): Double {
        val q = quotes[symbol]
        val spot = q?.price ?: 0.0
        if (!spot.isFinite() || spot <= 0.0) return if (derivative?.isKnockedOut == true) Pricing.KO_RESIDUAL else 0.0
        return if (derivative == null) spot else Pricing.price(derivative, spot, now, q?.fx ?: 1.0, rate)
    }

    // Gekauft wird zum Briefkurs, verkauft zum Geldkurs. buying null liefert den Mittelkurs fuer die Depotbewertung.
    fun tradePrice(
        symbol: String,
        derivative: Derivative?,
        quotes: Map<String, Quote>,
        now: Long,
        spread: Spread,
        buying: Boolean?,
        rate: Double = Pricing.RATE,
    ): Double {
        val mid = priceOf(derivative?.id ?: symbol, symbol, derivative, quotes, now, rate)
        if (mid <= 0.0) return 0.0
        return Pricing.withSpread(mid, spread.forProduct(derivative), buying)
    }

    private fun prevPriceOf(position: Position, quotes: Map<String, Quote>, now: Long, rate: Double): Double {
        val q = quotes[position.symbol] ?: return 0.0
        val d = position.derivative ?: return q.prevClose
        if (d.isKnockedOut) return Pricing.KO_RESIDUAL
        if (q.prevClose <= 0) return 0.0
        return if (d.kind.isFactor) {
            // Faktor-Zertifikate starten jeden Tag bei ihrem Referenzstand.
            if (d.factorPrice > 0) d.factorPrice else Pricing.price(d, q.prevClose, now, q.fx, rate)
        } else {
            Pricing.price(d, q.prevClose, now, q.fx, rate)
        }
    }

    fun isQuoteFresh(quote: Quote?, now: Long): Boolean = quote != null &&
        quote.price.isFinite() && quote.price > 0 && quote.updatedAt > 0 &&
        now - quote.updatedAt in 0L..300_000L

    // Bewertung

    fun value(account: Account, quotes: Map<String, Quote>, now: Long = System.currentTimeMillis(), rate: Double = Pricing.RATE): Valuation {
        val valued = account.positions.map { p ->
            val price = when {
                p.isKnockedOut -> Pricing.KO_RESIDUAL
                quotes[p.symbol]?.price?.let { it.isFinite() && it > 0 } == true -> priceOf(p.key, p.symbol, p.derivative, quotes, now, rate)
                else -> p.avgPrice
            }
            ValuedPosition(position = p, price = price, prevPrice = prevPriceOf(p, quotes, now, rate))
        }
        return Valuation(positions = valued, cash = account.cash, deposited = account.capitalBase)
    }

    // Haeufiger als alle 60 s wird nicht gespeichert.
    fun snapshot(account: Account, total: Double, now: Long, minGapMs: Long = 60_000L): Account {
        if (!total.isFinite() || total < 0.0) return account
        val last = account.history.lastOrNull()
        // Zeitstempel des letzten Punkts nicht nachziehen, sonst entstuende bei laufenden Ticks nie ein neuer Punkt.
        if (last != null && now - last.ts < minGapMs) return account
        val point = Snapshot(now, total, total - account.cash, total - account.capitalBase)
        val all = account.history + point
        val history = if (all.size <= MAX_HISTORY) all else {
            val split = all.size / 2
            all.take(split).filterIndexed { index, _ -> index % 2 == 0 } + all.drop(split)
        }
        return account.copy(history = history)
    }

    // Bestand in der Vergangenheit

    // Rueckwaerts aus den Buchungen: heutiger Bestand minus spaetere Kaeufe plus spaetere Verkaeufe. So zaehlen auch
    // verkaufte Werte fuer die Zeit, in der sie gehalten wurden. Nicht mehr gehaltene Derivate zaehlen nur, wenn ihre
    // Buchungen das Produkt tragen.
    fun holdingsAt(account: Account, at: Long): Map<String, Position> {
        val qty = HashMap<String, Double>()
        val info = HashMap<String, Position>()
        for (p in account.positions) { qty[p.key] = p.qty; info[p.key] = p }
        val produkte = produkte(account)
        for (t in account.transactions) {
            if (t.ts <= at) continue
            val key = t.posKey
            val delta = when (t.type) {
                TxType.BUY -> t.qty
                TxType.SELL, TxType.KNOCKOUT, TxType.EXPIRY -> -t.qty
                else -> 0.0
            }
            if (delta == 0.0 || key.isBlank()) continue
            if (info[key] == null) {
                val produkt = if (t.key.isNotBlank() && t.key != t.symbol) produkte[key] ?: continue else null   // Derivat ohne bekanntes Produkt
                info[key] = Position(key, t.title, t.symbol.ifBlank { key }, 0.0, t.price, t.ts, produkt)
            }
            qty[key] = (qty[key] ?: 0.0) - delta
        }
        return info.filter { (k, _) -> (qty[k] ?: 0.0) > 1e-9 }.mapValues { (k, p) -> p.copy(qty = qty[k]!!) }
    }

    // Je Depotschluessel der zuletzt gebuchte Stand.
    fun produkte(account: Account): Map<String, Derivative> {
        val m = HashMap<String, Derivative>()
        for (t in account.transactions) t.derivative?.let { m[t.posKey] = it }
        return m
    }

    // Auf MAX_TX begrenzen, Dividendenbuchungen bleiben: bookDividends wuerde eine fehlende beim naechsten Tick
    // erneut gutschreiben. Es fallen also die aeltesten anderen Buchungen weg.
    fun kuerzen(tx: List<Transaction>): List<Transaction> {
        val sortiert = tx.sortedBy { it.ts }
        var ueber = sortiert.size - MAX_TX
        if (ueber <= 0) return sortiert
        return sortiert.filter { t -> if (ueber > 0 && t.type != TxType.DIVIDEND) { ueber--; false } else true }
    }

    // Macht eine Wertkurve unabhaengig von Kaeufen und Verkaeufen: Zu jedem Punkt kommen die spaeteren Netto-Zufluesse
    // dazu (flows: Zeitpunkt zu Betrag, Kauf positiv). Der letzte Punkt bleibt der echte Wert, der erste ist der netto
    // investierte Betrag. Faellt der Anfang dabei auf oder unter null, bleibt die Kurve unveraendert.
    fun flowNeutral(candles: List<Candle>, flows: List<Pair<Long, Double>>): List<Candle> {
        if (candles.isEmpty() || flows.isEmpty()) return candles
        // Zufluesse nach Zeit sortiert und von hinten aufsummiert: afterFrom k ist die Summe ab dem k-ten.
        val sorted = flows.sortedBy { it.first }
        val times = LongArray(sorted.size) { sorted[it].first }
        val afterFrom = DoubleArray(sorted.size + 1)
        for (k in sorted.indices.reversed()) afterFrom[k] = afterFrom[k + 1] + sorted[k].second
        val adjusted = candles.map { c ->
            // Erster Zufluss nach dem Zeitpunkt des Punkts.
            var lo = 0
            var hi = times.size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (times[mid] <= c.t) lo = mid + 1 else hi = mid
            }
            val after = afterFrom[lo]
            if (after == 0.0) c else c.copy(open = c.open + after, high = c.high + after, low = c.low + after, close = c.close + after)
        }
        return if (adjusted.first().close > 0.0) adjusted else candles
    }

    // Erster Kauf, der zum heutigen oder frueheren Bestand fuehrte. 0 = nie gekauft.
    fun firstPurchase(account: Account): Long {
        val firstTx = account.transactions.filter { it.type == TxType.BUY }.minOfOrNull { it.ts } ?: 0L
        val firstPos = account.positions.minOfOrNull { it.openedAt } ?: 0L
        return listOf(firstTx, firstPos).filter { it > 0 }.minOrNull() ?: 0L
    }

    // Handel

    fun buy(
        account: Account,
        key: String,
        name: String,
        symbol: String,
        qty: Double,
        price: Double,
        derivative: Derivative? = null,
        now: Long = System.currentTimeMillis(),
        fee: Double = FEE,
        via: String = "",
        note: String = "",
    ): TradeResult {
        if (!qty.isFinite() || qty <= 0.0) return TradeResult(account, Err.INVALID_QTY)
        if (!price.isFinite() || price <= 0.0) return TradeResult(account, Err.NO_PRICE)
        if (!fee.isFinite() || fee < 0) return TradeResult(account, Err.INVALID_FEE)
        if (derivative != null && !Derivatives.tradable(derivative, now)) return TradeResult(account, Err.NOT_TRADABLE)
        if (derivative != null && qty != kotlin.math.floor(qty)) return TradeResult(account, Err.WHOLE_PIECES)
        val cost = qty * price + fee
        if (!cost.isFinite()) return TradeResult(account, Err.TOO_LARGE)
        if (cost > account.cash + 1e-9) return TradeResult(account, Err.NOT_ENOUGH_CASH)

        val existing = account.positions.firstOrNull { it.key == key }
        val positions = if (existing == null) {
            account.positions + Position(key, name, symbol, qty, price, now, derivative)
        } else {
            val newQty = existing.qty + qty
            val newAvg = (existing.invested + qty * price) / newQty
            account.positions.map {
                if (it.key == key) it.copy(qty = newQty, avgPrice = newAvg, derivative = it.derivative ?: derivative)
                else it
            }
        }
        val tx = Transaction(
            id = newId("tx", now), ts = now, type = TxType.BUY,
            title = name, amount = -(qty * price), qty = qty, price = price, symbol = symbol, fee = fee, note = note,
            key = key, tag = "BUY", via = via, derivative = derivative,
        )
        // Die Gebuehr bucht addFee, hier nur der reine Gegenwert.
        var acc = account.copy(cash = account.cash - qty * price, positions = positions, transactions = kuerzen(account.transactions + tx))
        if (fee > 0) acc = addFee(acc, fee, now, name, symbol)
        return TradeResult(acc)
    }

    // Import eines anderswo gekauften Wertpapiers. Es fliesst kein Geld, der Einstandswert erhoeht die Bezugsgroesse
    // fuer das Ergebnis. Die Buchung traegt das Kaufdatum, damit Verlauf und „seit Kauf“ ab dort rechnen.
    // Dividenden gibt es erst ab der Uebernahme.
    fun importPosition(
        account: Account,
        symbol: String,
        name: String,
        qty: Double,
        price: Double,
        purchasedAt: Long,
        now: Long = System.currentTimeMillis(),
    ): TradeResult {
        if (symbol.isBlank()) return TradeResult(account, Err.MISSING_SECURITY)
        if (!qty.isFinite() || qty <= 0.0) return TradeResult(account, Err.INVALID_QTY)
        if (!price.isFinite() || price <= 0.0) return TradeResult(account, Err.NO_PRICE)
        if (purchasedAt <= 0L || purchasedAt > now) return TradeResult(account, Err.INVALID_DATE)
        val cost = qty * price
        if (!cost.isFinite()) return TradeResult(account, Err.TOO_LARGE)
        val existing = account.positions.firstOrNull { it.key == symbol }
        if (existing?.derivative != null) return TradeResult(account, Err.NOT_TRADABLE)
        val positions = if (existing == null) {
            account.positions + Position(symbol, name, symbol, qty, price, purchasedAt, null, importedAt = now)
        } else {
            val newQty = existing.qty + qty
            account.positions.map {
                if (it.key == symbol) it.copy(
                    qty = newQty,
                    avgPrice = (existing.invested + cost) / newQty,
                    openedAt = minOf(existing.openedAt, purchasedAt),
                    importedAt = now,
                ) else it
            }
        }
        val tx = Transaction(
            id = newId("tx", now), ts = purchasedAt, type = TxType.BUY,
            title = name, amount = 0.0, qty = qty, price = price, symbol = symbol, fee = 0.0,
            key = symbol, tag = "TRANSFER_IN", via = "IMPORT",
        )
        return TradeResult(account.copy(
            positions = positions,
            transactions = kuerzen(account.transactions + tx),
            transferredIn = account.transferredIn + cost,
        ))
    }

    fun sell(
        account: Account,
        key: String,
        qty: Double,
        price: Double,
        now: Long = System.currentTimeMillis(),
        fee: Double = FEE,
        allowance: Double = 0.0,
        note: String = "",
        via: String = "",
    ): TradeResult {
        val pos = account.positions.firstOrNull { it.key == key }
            ?: return TradeResult(account, Err.NOT_IN_DEPOT)
        if (!qty.isFinite() || qty <= 0.0) return TradeResult(account, Err.INVALID_QTY)
        if (qty > pos.qty + 1e-9) return TradeResult(account, Err.NOT_ENOUGH_PIECES)
        if (!price.isFinite() || price <= 0.0) return TradeResult(account, Err.NO_PRICE)
        if (pos.isKnockedOut) return TradeResult(account, Err.NOT_TRADABLE)

        if (!fee.isFinite() || fee < 0) return TradeResult(account, Err.INVALID_FEE)
        val proceeds = qty * price
        if (!proceeds.isFinite()) return TradeResult(account, Err.TOO_LARGE)
        if (account.cash + proceeds < fee) return TradeResult(account, Err.FEE_NOT_COVERED)
        val rest = pos.qty - qty
        val positions = if (rest <= 1e-9) account.positions.filter { it.key != key }
        else account.positions.map { if (it.key == key) it.copy(qty = rest) else it }

        val realized = qty * (price - pos.avgPrice)
        // Abgeltungsteuer auf den realisierten Gewinn. Verluste wandern in den Verrechnungstopf.
        val taxed = Tax.apply(account.tax, realized, taxKind(pos), allowance, now)
        val tx = Transaction(
            id = newId("tx", now), ts = now, type = TxType.SELL,
            title = pos.name, amount = proceeds - taxed.tax, qty = qty, price = price, symbol = pos.symbol,
            tax = taxed.tax, realized = realized, fee = fee, note = note,
            key = key, tag = "SELL", via = via, derivative = pos.derivative,
        )
        var acc = account.copy(
            cash = account.cash + proceeds - taxed.tax,
            positions = positions,
            transactions = kuerzen(account.transactions + tx),
            tax = taxed.state,
        )
        if (fee > 0) acc = addFee(acc, fee, now, pos.name, pos.symbol)
        return TradeResult(acc)
    }

    // Aktienverluste duerfen nur mit Aktiengewinnen verrechnet werden, alles andere ist sonstig.
    fun taxKind(pos: Position): Tax.Kind =
        if (pos.derivative == null && Symbols.find(pos.symbol)?.type == AssetType.STOCK) Tax.Kind.STOCK
        else Tax.Kind.GENERAL

    private fun addFee(account: Account, fee: Double, now: Long, what: String, symbol: String): Account {
        val tx = Transaction(
            id = newId("fee", now), ts = now, type = TxType.FEE,
            title = what, subtitle = "", amount = -fee, tag = "FEE", symbol = "",
            note = symbol,
        )
        return account.copy(cash = account.cash - fee, transactions = kuerzen(account.transactions + tx))
    }

    // Orders

    fun orderError(order: Order): String? = when {
        !order.qty.isFinite() || order.qty <= 0 -> Err.INVALID_QTY
        order.type == OrderType.TRAILING_STOP && (!order.trailPct.isFinite() || order.trailPct <= 0 || order.trailPct > 50) -> Err.INVALID_TRAIL
        order.type != OrderType.MARKET && (!order.limitPrice.isFinite() || order.limitPrice <= 0) -> Err.INVALID_LIMIT
        order.key.isBlank() || order.symbol.isBlank() -> Err.MISSING_SECURITY
        else -> null
    }

    fun placeOrder(account: Account, order: Order): Account {
        if (orderError(order) != null || account.orders.any { it.id == order.id }) return account
        val expires = if (order.expiresAt > 0) order.expiresAt else expiryFor(order)
        return account.copy(orders = account.orders + order.copy(expiresAt = expires))
    }

    // Stoppreis aus aktuellem Kurs und Abstand.
    fun trailingStop(order: Order, currentPrice: Double): Order {
        val stop = if (order.side == Side.SELL) currentPrice * (1.0 - order.trailPct / 100.0) else currentPrice * (1.0 + order.trailPct / 100.0)
        return order.copy(type = OrderType.TRAILING_STOP, trailRef = currentPrice, limitPrice = stop)
    }

    // Tagesgueltig endet mit dem Handelstag, sonst nach 360 Tagen.
    fun expiryFor(order: Order): Long {
        val window = MarketHours.windowFor(Symbols.find(order.symbol)?.type, order.derivative != null)
        return when (order.validity) {
            Validity.DAY -> if (window != null) MarketHours.endOfTradingDay(window, order.createdAt)
                else order.createdAt + 86_400_000L
            Validity.GTC -> order.createdAt + 360L * 86_400_000L
        }
    }

    fun expireOrders(account: Account, now: Long = System.currentTimeMillis()): Account {
        if (account.orders.none { it.status == OrderStatus.OPEN && it.expiresAt in 1..now }) return account
        return account.copy(orders = account.orders.map {
            if (it.status == OrderStatus.OPEN && it.expiresAt in 1..now)
                it.copy(status = OrderStatus.EXPIRED, executedAt = now, reason = Err.EXPIRED)
            else it
        })
    }

    // Ein Trailing-Stop rechnet seinen Stoppreis mit dem neuen Abstand vom bisherigen Hoechst- bzw. Tiefstkurs neu.
    // Eine geaenderte Gueltigkeit laeuft ab now. Art, Richtung und Wert bleiben.
    fun orderAendern(account: Account, id: String, qty: Double, limitPrice: Double, trailPct: Double, validity: Validity, now: Long): TradeResult {
        val alt = account.orders.firstOrNull { it.id == id && it.status == OrderStatus.OPEN } ?: return TradeResult(account, Err.ORDER_CLOSED)
        var neu = when (alt.type) {
            OrderType.TRAILING_STOP -> alt.copy(
                qty = qty, trailPct = trailPct,
                limitPrice = if (alt.side == Side.SELL) alt.trailRef * (1.0 - trailPct / 100.0) else alt.trailRef * (1.0 + trailPct / 100.0),
            )
            OrderType.MARKET -> alt.copy(qty = qty)
            else -> alt.copy(qty = qty, limitPrice = limitPrice)
        }.copy(validity = validity)
        orderError(neu)?.let { return TradeResult(account, it) }
        if (validity != alt.validity) neu = neu.copy(expiresAt = expiryFor(neu.copy(createdAt = now)))
        return TradeResult(account.copy(orders = account.orders.map { if (it.id == id) neu else it }))
    }

    fun cancelOrder(account: Account, id: String, now: Long = System.currentTimeMillis()): Account =
        account.copy(orders = account.orders.map {
            if (it.id == id && it.status == OrderStatus.OPEN) it.copy(status = OrderStatus.CANCELLED, executedAt = now) else it
        })

    // Fuehrt offene Limit-, Stop- und Trailing-Orders aus, wenn die Kurse es erlauben. Market-Orders laufen sofort
    // beim Aufgeben, nicht hier. Trailing-Stops ziehen ihren Stoppreis bei jedem Lauf nach.
    fun runOrders(
        account: Account,
        quotes: Map<String, Quote>,
        now: Long = System.currentTimeMillis(),
        spread: Spread = Spread(),
        allowance: Double = 0.0,
        respectHours: Boolean = true,
        rate: Double = Pricing.RATE,
    ): Account {
        var acc = expireOrders(account, now)
        val open = acc.orders.filter { it.status == OrderStatus.OPEN }
        for (o in open) {
            val q = quotes[o.symbol]
            if (!isQuoteFresh(q, now)) continue
            val derivative = o.derivative
            if (derivative != null && (derivative.isKnockedOut ||
                    (derivative.expiry > 0 && derivative.expiry <= now) ||
                    (derivative.kind.isKnockout && Pricing.isKnockedOut(derivative, q!!.price, q.fx)) ||
                    acc.positions.firstOrNull { it.key == o.key }?.isKnockedOut == true)) {
                acc = acc.copy(orders = acc.orders.map {
                    if (it.id == o.id) it.copy(status = OrderStatus.CANCELLED, executedAt = now, reason = Err.PRODUCT_GONE) else it
                })
                continue
            }
            // Kurs und Stoppreis werden auch ausserhalb der Handelszeiten nachgezogen, ausgefuehrt wird nur waehrend.
            // Bei gehaltenen Hebelprodukten gilt der tagesaktuelle Stand der Position, nicht der bei Orderaufgabe.
            val deriv = acc.positions.firstOrNull { it.key == o.key }?.derivative ?: o.derivative
            val price = priceOf(o.key, o.symbol, deriv, quotes, now, rate)
            if (!price.isFinite() || price <= 0.0) continue
            var order = o
            if (o.type == OrderType.TRAILING_STOP) {
                val ref = when {
                    o.trailRef <= 0 -> price
                    o.side == Side.SELL -> max(o.trailRef, price)
                    else -> min(o.trailRef, price)
                }
                val stop = if (o.side == Side.SELL) ref * (1.0 - o.trailPct / 100.0) else ref * (1.0 + o.trailPct / 100.0)
                if (ref != o.trailRef || abs(stop - o.limitPrice) > 1e-9) {
                    order = o.copy(trailRef = ref, limitPrice = stop)
                    acc = acc.copy(orders = acc.orders.map { if (it.id == o.id) order else it })
                }
            }
            if (respectHours && !MarketHours.isOpen(Symbols.find(o.symbol)?.type, deriv != null, now)) continue
            // Das Limit gilt fuer den ausfuehrbaren Geld- bzw. Briefkurs, die Spanne eingerechnet.
            val fillPrice = Pricing.withSpread(price, spread.forProduct(deriv), o.side == Side.BUY)
            val triggered = when (order.type) {
                OrderType.LIMIT -> if (order.side == Side.BUY) fillPrice <= order.limitPrice else fillPrice >= order.limitPrice
                OrderType.STOP, OrderType.TRAILING_STOP -> if (order.side == Side.BUY) price >= order.limitPrice else price <= order.limitPrice
                OrderType.MARKET -> true
            }
            if (!triggered) continue

            val res = if (order.side == Side.BUY)
                buy(acc, order.key, order.name, order.symbol, order.qty, fillPrice, deriv, now, note = order.note, via = "ORDER")
            else
                sell(acc, order.key, order.qty, fillPrice, now, allowance = allowance, note = order.note, via = "ORDER")

            acc = if (res.ok) {
                res.account.copy(orders = res.account.orders.map {
                    if (it.id == o.id) it.copy(status = OrderStatus.FILLED, executedAt = now, executedPrice = fillPrice) else it
                })
            } else {
                acc.copy(orders = acc.orders.map {
                    if (it.id == o.id) it.copy(status = OrderStatus.REJECTED, executedAt = now, reason = res.error ?: "") else it
                })
            }
        }
        return acc
    }

    // Knock-outs, Faktor-Roll, Verfall

    // Schreibt Faktor-Zertifikate fort, erkennt Knock-outs und bucht faellige Produkte ab. Ein Knock-out bleibt
    // zunaechst mit dem Restwert von 0,001 EUR sichtbar, damit der Verlust erkennbar ist. Nach KO_SETTLE_MS zahlt der
    // Emittent den Restwert und die Position verschwindet. dayChanged: der Handelstag hat gewechselt.
    fun maintain(
        account: Account,
        quotes: Map<String, Quote>,
        now: Long = System.currentTimeMillis(),
        dayChanged: Boolean = false,
        allowance: Double = 0.0,
        rate: Double = Pricing.RATE,
    ): Account {
        var acc = account
        val settle = mutableListOf<Position>()
        val updated = mutableListOf<Position>()

        for (p in acc.positions) {
            val d = p.derivative ?: continue
            if (d.isKnockedOut) {
                if (now - d.knockedOutAt >= KO_SETTLE_MS) settle += p
                continue
            }
            val q = quotes[p.symbol]
            if (!isQuoteFresh(q, now)) continue
            val spot = q!!.price
            if (!spot.isFinite() || spot <= 0) continue

            if (d.kind.isKnockout && Pricing.isKnockedOut(d, spot, q.fx)) {
                updated += p.copy(derivative = d.copy(knockedOutAt = now))
                continue
            }
            if (d.kind.isOption && d.expiry > 0L && d.expiry <= now) {
                settle += p
                continue
            }
            if (d.kind.isFactor) {
                val rolled = Pricing.rollFactor(d, spot, dayChanged, rate)
                if (rolled !== d) updated += p.copy(derivative = rolled)
            }
        }

        if (updated.isNotEmpty()) {
            acc = acc.copy(positions = acc.positions.map { p -> updated.firstOrNull { it.key == p.key } ?: p })
            // Offene Orders auf ein ausgeknocktes Produkt verfallen sofort.
            val gone = updated.filter { it.isKnockedOut }.map { it.key }.toSet()
            if (gone.isNotEmpty()) acc = acc.copy(orders = acc.orders.map {
                if (it.key in gone && it.status == OrderStatus.OPEN) it.copy(status = OrderStatus.CANCELLED, executedAt = now, reason = Err.PRODUCT_GONE) else it
            })
        }

        for (p in settle) acc = abrechnen(acc, p, quotes, now, allowance)
        return acc
    }

    // Bucht sofort aus, ohne die zwei Tage bis zur Rueckzahlung abzuwarten.
    // Nur fuer ausgeknockte Produkte und verfallene Optionsscheine.
    fun settle(account: Account, key: String, quotes: Map<String, Quote>, now: Long = System.currentTimeMillis(), allowance: Double = 0.0): TradeResult {
        val pos = account.positions.firstOrNull { it.key == key } ?: return TradeResult(account, Err.NOT_IN_DEPOT)
        val d = pos.derivative ?: return TradeResult(account, Err.NOT_TRADABLE)
        val faellig = d.isKnockedOut || (d.kind.isOption && d.expiry > 0L && d.expiry <= now)
        if (!faellig) return TradeResult(account, Err.NOT_TRADABLE)
        return TradeResult(abrechnen(account, pos, quotes, now, allowance))
    }

    // Restwert bzw. innerer Wert, Steuer, offene Orders storniert.
    private fun abrechnen(account: Account, p: Position, quotes: Map<String, Quote>, now: Long, allowance: Double): Account {
        val d = p.derivative!!
        val q = quotes[p.symbol]
        val spot = q?.price ?: 0.0
        val isKo = d.kind.isKnockout
        // Optionsschein: innerer Wert am Verfallstag. Knock-out: Restwert.
        val payout = if (isKo) Pricing.KO_RESIDUAL else max(0.0, Pricing.intrinsic(d, spot, q?.fx ?: 1.0))
        val proceeds = payout * p.qty
        val realized = proceeds - p.invested
        val taxed = Tax.apply(account.tax, realized, Tax.Kind.GENERAL, allowance, now)
        val tx = Transaction(
            id = newId("ko", now), ts = now,
            type = if (isKo) TxType.KNOCKOUT else TxType.EXPIRY,
            title = p.name,
            amount = proceeds - taxed.tax, qty = p.qty, price = payout, symbol = p.symbol,
            tax = taxed.tax, realized = realized, key = p.key, derivative = d,
            tag = if (isKo) "KO_SETTLE" else if (payout > 0) "EXPIRY" else "EXPIRY_WORTHLESS",
            // Fuer den Handelskalender: Ende mit dem Knock-out bzw. am Verfallstag.
            closedAt = (if (isKo) d.knockedOutAt else d.expiry).takeIf { it in 1 until now } ?: 0L,
        )
        return account.copy(
            cash = account.cash + proceeds - taxed.tax,
            positions = account.positions.filter { it.key != p.key },
            transactions = kuerzen(account.transactions + tx),
            tax = taxed.state,
            orders = account.orders.map {
                if (it.key == p.key && it.status == OrderStatus.OPEN)
                    it.copy(status = OrderStatus.CANCELLED, executedAt = now, reason = Err.PRODUCT_GONE)
                else it
            },
        )
    }

    // Sparplaene

    // Monatlich und vierteljaehrlich am eingestellten Tag (2. oder 16.), zweiwoechentlich am 2. und 16.,
    // woechentlich alle sieben Tage. Mit Uhrzeit erst ab dieser, ein verpasster Termin sofort.
    fun planDue(plan: SavingsPlan, now: Long): Boolean {
        if (!planFaellig(plan, now)) return false
        val u = plan.uhrzeit
        if (u !in 0 until 24 * 60) return true
        if (minuteAt(now) >= u) return true
        // Vor der Uhrzeit nur, wenn der Termin schon vorher lag, etwa nach Tagen ohne App.
        return planFaellig(plan, startOfDay(now) - 1)
    }

    // Nur nach dem Datum, ohne Uhrzeit.
    private fun planFaellig(plan: SavingsPlan, now: Long): Boolean {
        if (plan.paused) return false
        if (!plan.amount.isFinite() || plan.amount <= 0) return false
        if (now < plan.createdAt) return false
        val date = dateAt(now)
        val day = date.dayOfMonth
        // Ein neuer Plan laeuft erst zum naechsten Termin. Bis zur ersten Ausfuehrung zaehlt der Anlagetag als Bezug.
        val ref = if (plan.lastRun > 0) plan.lastRun else plan.createdAt
        val fresh = plan.lastRun == 0L
        return when (plan.interval) {
            PlanInterval.WEEKLY -> dayNumber(now) - dayNumber(ref) >= 7
            PlanInterval.BIWEEKLY -> {
                val slot = if (day >= 16) 16 else if (day >= 2) 2 else return false
                val refSlot = if (fresh && dateAt(ref).dayOfMonth < 2) 0 else slotOf(ref)
                monthKey(ref) < monthKey(now) || (monthKey(ref) == monthKey(now) && refSlot < slot)
            }
            PlanInterval.MONTHLY -> {
                val dueDay = minOf(plan.dayOfMonth.coerceIn(1, 31), date.lengthOfMonth())
                day >= dueDay && (monthKey(ref) < monthKey(now) ||
                    (fresh && monthKey(ref) == monthKey(now) && dateAt(ref).dayOfMonth < dueDay))
            }
            PlanInterval.QUARTERLY -> {
                val dueDay = minOf(plan.dayOfMonth.coerceIn(1, 31), date.lengthOfMonth())
                day >= dueDay && (if (fresh) (monthKey(ref) < monthKey(now) || (monthKey(ref) == monthKey(now) && dateAt(ref).dayOfMonth < dueDay))
                    else monthKey(now) - monthKey(ref) >= 3)
            }
        }
    }

    fun nextExecution(plan: SavingsPlan, now: Long): Long {
        if (plan.paused) return 0L
        var t = now
        val limit = now + 400L * 86_400_000L
        // Tageweise vorlaufen, bis der Plan faellig ist. Sekundengenau ist unnoetig.
        val probe = plan.copy(lastRun = if (plan.lastRun == 0L) 0L else plan.lastRun)
        while (t < limit) {
            if (planFaellig(probe, t) && (plan.lastRun == 0L || dayNumber(t) > dayNumber(plan.lastRun))) {
                // Mit Uhrzeit an diesem Tag zu ihr, ist sie heute schon vorbei, sofort.
                if (plan.uhrzeit !in 0 until 24 * 60) return t
                return maxOf(now, startOfDay(t) + plan.uhrzeit * 60_000L)
            }
            t += 86_400_000L
        }
        return 0L
    }

    private fun slotOf(ts: Long): Int = if (dateAt(ts).dayOfMonth >= 16) 16 else 2

    private fun dayNumber(ts: Long): Long = dateAt(ts).toEpochDay()

    // Ohne Fremdkostenpauschale.
    fun runPlans(
        account: Account,
        quotes: Map<String, Quote>,
        now: Long = System.currentTimeMillis(),
        spread: Spread = Spread(),
    ): Account {
        var acc = account
        for (plan in acc.plans) {
            if (!planDue(plan, now)) continue
            if (!isQuoteFresh(quotes[plan.symbol], now)) continue
            val mid = quotes[plan.symbol]?.price ?: continue
            if (!mid.isFinite() || mid <= 0) continue
            val price = Pricing.ask(mid, spread.stockBps)
            val qty = plan.amount / price
            val res = buy(acc, plan.symbol, plan.name, plan.symbol, qty, price, null, now, fee = 0.0, via = "PLAN")
            acc = if (res.ok) {
                res.account.copy(plans = res.account.plans.map {
                    if (it.id == plan.id) it.copy(lastRun = now, invested = it.invested + plan.amount, executions = it.executions + 1, lastFailed = 0L) else it
                })
            } else {
                // Fehlschlag hoechstens einmal am Tag vermerken. Der Plan bleibt faellig und wird wiederholt.
                if (plan.lastFailed > 0 && dayNumber(plan.lastFailed) == dayNumber(now)) acc
                else {
                    val tx = Transaction(
                        id = newId("plan", now), ts = now, type = TxType.PLAN,
                        title = plan.name, symbol = plan.symbol, tag = "PLAN_FAILED", note = res.error ?: Err.NOT_ENOUGH_CASH,
                        extra = plan.amount,
                    )
                    acc.copy(
                        plans = acc.plans.map { if (it.id == plan.id) it.copy(lastFailed = now) else it },
                        transactions = kuerzen(acc.transactions + tx),
                    )
                }
            }
        }
        return acc
    }

    // Konto

    fun deposit(account: Account, amount: Double, now: Long = System.currentTimeMillis(), opening: Boolean = false): Account {
        if (!amount.isFinite() || amount <= 0 || !(account.cash + amount).isFinite() || !(account.deposited + amount).isFinite()) return account
        val tx = Transaction(id = newId("dep", now), ts = now, type = TxType.DEPOSIT, title = "", amount = amount, tag = if (opening) "OPENING" else "DEPOSIT")
        return account.copy(
            cash = account.cash + amount,
            deposited = account.deposited + amount,
            transactions = kuerzen(account.transactions + tx),
        )
    }

    // Guthaben, das offene Kauforders beanspruchen, bleibt reserviert.
    fun withdraw(account: Account, amount: Double, now: Long = System.currentTimeMillis()): TradeResult {
        if (!amount.isFinite() || amount <= 0) return TradeResult(account, Err.INVALID_AMOUNT)
        val available = account.cash - account.reserved
        if (amount > available + 1e-9) return TradeResult(account, Err.ONLY_AVAILABLE, extra = max(0.0, available))
        val tx = Transaction(id = newId("wd", now), ts = now, type = TxType.WITHDRAWAL, title = "", amount = -amount, tag = "WITHDRAWAL")
        return TradeResult(account.copy(
            cash = account.cash - amount,
            withdrawn = account.withdrawn + amount,
            transactions = kuerzen(account.transactions + tx),
        ))
    }

    // Zinsen taeglich aufsammeln, gebucht wird einmal im Monat.
    fun accrueInterest(account: Account, ratePct: Double, days: Int): Account {
        if (!ratePct.isFinite() || ratePct <= 0 || account.cash <= 0 || days <= 0) return account
        val amount = account.cash * (ratePct / 100.0) * days / 365.0
        if (!amount.isFinite() || amount <= 0) return account
        return account.copy(interestAccrued = account.interestAccrued + amount)
    }

    // Monatsende: aufgelaufene Zinsen gutschreiben, Abgeltungsteuer einbehalten.
    fun creditInterest(account: Account, ratePct: Double, now: Long, allowance: Double = 0.0): Account {
        val gross = kotlin.math.round(account.interestAccrued * 100.0) / 100.0
        if (gross < 0.01) return account.copy(interestAccrued = 0.0)
        val taxed = Tax.apply(account.tax, gross, Tax.Kind.GENERAL, allowance, now)
        val tx = Transaction(
            id = newId("int", now), ts = now, type = TxType.INTEREST,
            title = "", amount = gross - taxed.tax, tax = taxed.tax, tag = "INTEREST", extra = ratePct,
        )
        return account.copy(
            cash = account.cash + gross - taxed.tax,
            interestAccrued = 0.0,
            tax = taxed.state,
            transactions = kuerzen(account.transactions + tx),
        )
    }

    data class Dividend(val symbol: String, val exDate: Long, val perShare: Double)

    // Fuer jede gehaltene Aktie oder jeden ETF, deren Ex-Tag nach dem Kauf und vor now liegt. Gezahlt wird kurz
    // nach dem Ex-Tag. Die Buchung traegt den Ex-Tag in der Kennung, damit sie nur einmal entsteht.
    fun bookDividends(
        account: Account,
        dividends: List<Dividend>,
        now: Long = System.currentTimeMillis(),
        allowance: Double = 0.0,
        payDelayMs: Long = 3L * 86_400_000L,
    ): Account {
        var acc = account
        val known = acc.transactions.asSequence().filter { it.type == TxType.DIVIDEND }.map { it.id }.toHashSet()
        for (d in dividends) {
            val pos = acc.positions.firstOrNull { it.key == d.symbol && it.derivative == null } ?: continue
            // Bei uebernommenen Positionen zaehlen nur Dividenden nach der Uebernahme.
            if (d.exDate <= maxOf(pos.openedAt, pos.importedAt) || d.exDate + payDelayMs > now) continue
            if (d.perShare <= 0 || !d.perShare.isFinite()) continue
            val id = "div-${d.symbol}-${d.exDate / 86_400_000L}"
            if (id in known) continue
            val gross = kotlin.math.round(pos.qty * d.perShare * 100.0) / 100.0
            if (gross < 0.01) continue
            val taxed = Tax.apply(acc.tax, gross, Tax.Kind.GENERAL, allowance, now)
            val tx = Transaction(
                id = id, ts = d.exDate + payDelayMs, type = TxType.DIVIDEND,
                title = pos.name, amount = gross - taxed.tax, qty = pos.qty, price = d.perShare,
                symbol = pos.symbol, tax = taxed.tax, key = pos.key, tag = "DIVIDEND",
            )
            acc = acc.copy(
                cash = acc.cash + gross - taxed.tax,
                tax = taxed.state,
                transactions = kuerzen(acc.transactions + tx),
            )
            known += id
        }
        return acc
    }

    // Preisalarme pruefen und jede Meldung als Ereignis vermerken. Kursmarken melden einmal und verschwinden.
    // Tagesalarme (tagesProzent) melden hoechstens einmal je Handelstag und bleiben; sie zaehlen nur mit einem Kurs
    // vom heutigen Handelstag, sonst kaeme die Bewegung des Vortags am Morgen ein zweites Mal.
    fun checkAlerts(account: Account, quotes: Map<String, Quote>, now: Long = System.currentTimeMillis()): Pair<Account, List<PriceAlert>> {
        if (account.alerts.isEmpty()) return account to emptyList()
        val heute = handelstag(now)
        val fired = account.alerts.filter { a ->
            val q = quotes[a.symbol] ?: return@filter false
            if (!isQuoteFresh(q, now)) return@filter false
            if (a.tagesProzent > 0) {
                q.prevClose > 0 && q.marketTime > 0 && handelstag(q.marketTime) == heute && a.gemeldetAm != heute &&
                    abs(q.changePct) >= a.tagesProzent
            } else if (a.above) q.price >= a.target else q.price <= a.target
        }
        if (fired.isEmpty()) return account to emptyList()
        val txs = fired.map { a ->
            val q = quotes[a.symbol]
            val price = q?.price ?: a.target
            if (a.tagesProzent > 0) Transaction(
                id = newId("alert", now), ts = now, type = TxType.ALERT,
                title = a.name, symbol = a.symbol, price = price, extra = q?.changePct ?: 0.0, tag = "ALERT_MOVE",
            ) else Transaction(
                id = newId("alert", now), ts = now, type = TxType.ALERT,
                title = a.name, symbol = a.symbol, price = price, extra = a.target,
                tag = if (a.above) "ALERT_ABOVE" else "ALERT_BELOW",
            )
        }
        val ids = fired.map { it.id }.toSet()
        return account.copy(
            alerts = account.alerts.mapNotNull { a ->
                when {
                    a.id !in ids -> a
                    a.tagesProzent > 0 -> a.copy(gemeldetAm = heute)
                    else -> null
                }
            },
            transactions = kuerzen(account.transactions + txs),
        ) to fired
    }

    // Handelstag eines Zeitpunkts: Tage seit 1970 in der Zeitzone der Boerse.
    fun handelstag(t: Long): Long = java.time.Instant.ofEpochMilli(t).atZone(MarketHours.zone).toLocalDate().toEpochDay()

    // Watchlist

    // Das erste Hinzufuegen bleibt in WatchMeta erhalten, fuer „seit erstem Hinzufuegen“.
    // Zeitpunkt und Kurs des letzten Hinzufuegens werden neu gesetzt.
    fun watch(account: Account, symbol: String, price: Double, now: Long): Account {
        if (symbol in account.watchlist) return account
        val alt = account.watchMeta[symbol]
        val meta = if (price > 0) {
            account.watchMeta + (symbol to WatchMeta(now, price, firstAddedAt = alt?.ersterZeitpunkt ?: now, firstPrice = alt?.ersterKurs ?: price))
        } else account.watchMeta
        return account.copy(watchlist = account.watchlist + symbol, watchMeta = meta)
    }

    // Der Eintrag in watchMeta bleibt, damit ein erneutes Hinzufuegen das erste kennt.
    fun unwatch(account: Account, symbol: String): Account =
        account.copy(watchlist = account.watchlist - symbol)

    // Eintraege aus aelteren Fassungen bekommen beim ersten Kurs ihren Bezugspunkt.
    fun backfillWatchMeta(account: Account, quotes: Map<String, Quote>, now: Long): Account {
        val missing = account.watchlist.filter { it !in account.watchMeta && (quotes[it]?.price ?: 0.0) > 0 }
        if (missing.isEmpty()) return account
        return account.copy(watchMeta = account.watchMeta + missing.associateWith { WatchMeta(now, quotes[it]!!.price) })
    }

    private fun dateAt(ts: Long): java.time.LocalDate =
        java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault()).toLocalDate()

    private fun monthKey(ts: Long): Int = dateAt(ts).let { it.year * 12 + it.monthValue }

    private fun minuteAt(ts: Long): Int =
        java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault()).let { it.hour * 60 + it.minute }

    private fun startOfDay(ts: Long): Long =
        dateAt(ts).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun fresh(now: Long = System.currentTimeMillis(), capital: Double = START_CASH): Account =
        deposit(
            Account(
                createdAt = now,
                lastMaintainDay = now / 86_400_000L,
                lastInterestDay = now / 86_400_000L,
                tax = TaxState(year = Tax.yearOf(now)),
            ),
            capital, now, opening = true,
        )
}
