package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Derivative
import de.troetenrepublik.app.data.DerivativeKind
import de.troetenrepublik.app.data.Lot
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
    const val INVALID_STOP = "INVALID_STOP"
    const val INVALID_TRAIL = "INVALID_TRAIL"
    const val MISSING_SECURITY = "MISSING_SECURITY"
    const val INVALID_AMOUNT = "INVALID_AMOUNT"
    const val ONLY_AVAILABLE = "ONLY_AVAILABLE"      // extra: verfuegbarer Betrag
    const val EXPIRED = "EXPIRED"
    const val PRODUCT_GONE = "PRODUCT_GONE"
    const val INVALID_DATE = "INVALID_DATE"
    const val ORDER_CLOSED = "ORDER_CLOSED"
    // [0.77] Verkaufte Optionen: Deckung fehlt, Aktien durch verkaufte Calls gebunden, kein Leerverkauf von Aktien.
    const val UNGEDECKT = "UNCOVERED"
    const val GEBUNDEN = "SHARES_BOUND"
    const val LEERVERKAUF = "NO_SHORT_STOCK"
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

    // Ausloeser (Transaction.via) einer Buchung aus einer Limit-, Stopp- oder Trailing-Order.
    const val VIA_ORDER = "ORDER"

    // Guthaben nach einer Buchung: Rundungsreste unter einem halben Cent (etwa -0,000000000001 nach einem Kauf des
    // ganzen Guthabens) zaehlen als null.
    fun kasse(betrag: Double): Double = if (betrag.isFinite() && abs(betrag) < 0.005) 0.0 else betrag

    const val START_CASH = 50_000.0

    const val MAX_HISTORY = 4000

    // Nach einem Knock-out bleibt das Produkt zwei Tage sichtbar, dann zahlt der Emittent den Restwert.
    const val KO_SETTLE_MS = 2L * 86_400_000L

    // Hoechstzahl der Buchungen. Gemessen mit 20.000 Buchungen: 5,8 MB Datei, Speichern 0,13 s, Laden 0,14 s, Leistungsanalyse
    // 0,08 s. Der Import warnt vorher, wenn eine Datei darueber liegt, damit keine Geschichte still verloren geht.
    const val MAX_BUCHUNGEN = 50_000

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
        val tagStart = MarketHours.dayStart(now)
        val valued = account.positions.map { p ->
            val price = when {
                p.isKnockedOut -> Pricing.KO_RESIDUAL
                quotes[p.symbol]?.price?.let { it.isFinite() && it > 0 } == true -> priceOf(p.key, p.symbol, p.derivative, quotes, now, rate)
                else -> p.avgPrice
            }
            ValuedPosition(position = p, price = price, prevPrice = prevPriceOf(p, quotes, now, rate), dayStart = tagStart)
        }
        val (verkauft, basis) = heuteVerkauft(account, quotes, tagStart)
        return Valuation(
            positions = valued, cash = account.cash, deposited = account.capitalBase, withdrawn = account.withdrawn,
            verkauftHeute = verkauft, verkauftHeuteBasis = basis,
        )
    }

    // Bewegung der heute verkauften Aktien seit dem Vortagesschluss (Verkaufskurs minus Vortagesschluss, mal Stueck) und
    // ihr Wert zum Vortagesschluss. Werte, die heute auch gekauft wurden, und Derivate bleiben aussen vor.
    private fun heuteVerkauft(account: Account, quotes: Map<String, Quote>, tagStart: Long): Pair<Double, Double> {
        val heute = account.transactions.filter { it.ts >= tagStart }
        val gekauft = heute.filter { it.type == TxType.BUY }.map { it.posKey }.toSet()
        var aenderung = 0.0
        var basis = 0.0
        for (t in heute) {
            if (t.type != TxType.SELL || t.derivative != null || t.posKey in gekauft) continue
            val vortag = quotes[t.symbol]?.prevClose ?: continue
            if (!(vortag > 0.0) || !(t.price > 0.0)) continue
            aenderung += t.qty * (t.price - vortag)
            basis += t.qty * vortag
        }
        return aenderung to basis
    }

    // Haeufiger als alle 60 s wird nicht gespeichert.
    fun snapshot(account: Account, total: Double, now: Long, minGapMs: Long = 60_000L): Account {
        if (!total.isFinite() || total < 0.0) return account
        val last = account.history.lastOrNull()
        // Zeitstempel des letzten Punkts nicht nachziehen, sonst entstuende bei laufenden Ticks nie ein neuer Punkt.
        if (last != null && now - last.ts < minGapMs) return account
        val point = Snapshot(now, total, total - account.cash, total + account.withdrawn - account.capitalBase)
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
    // Buchungen das Produkt tragen. Stueckzahlen bleiben in heutigen Einheiten: Buchungen vor einem Split zaehlen mit
    // dem Splitverhaeltnis, weil auch die alten Kurse von Yahoo auf die neue Stueckzahl umgerechnet sind.
    // extern: Splits je Kuerzel von der Boerse (Splits.Split), fuer uebernommene Depots, deren Buchungen die Stueckzahl vor
    // dem Split noch unbereinigt tragen; ein Split mit eigener SPLIT-Buchung am selben Tag zaehlt nur einmal. Die
    // Buchungen des Brokers zum Split selbst (CORPORATE, EXCHANGE_IN/OUT am Splittag) entfallen dann.
    fun holdingsAt(account: Account, at: Long, extern: Map<String, List<Splits.Split>> = emptyMap()): Map<String, Position> {
        val qty = HashMap<String, Double>()
        val info = HashMap<String, Position>()
        for (p in account.positions) { qty[p.key] = p.qty; info[p.key] = p }
        val produkte = produkte(account)
        val tag = 86_400_000L
        val gebucht = account.transactions.filter { it.type == TxType.SPLIT && it.extra > 0 }.map { Triple(it.posKey, it.ts, it.extra) }
        val fremd = extern.flatMap { (k, l) ->
            l.filter { s -> s.verhaeltnis > 0 && gebucht.none { it.first == k && it.second / tag == s.t / tag } }.map { s -> Triple(k, s.t, s.verhaeltnis) }
        }
        val splits = gebucht + fremd
        for (t in account.transactions) {
            if (t.ts <= at) continue
            val key = t.posKey
            // Vom Broker gebuchte Stuecke des Splits sind schon in der bereinigten Stueckzahl
            if (extern.isNotEmpty() && t.tag in SPLIT_TAGS && extern[key].orEmpty().any { it.t / tag == t.ts / tag }) continue
            val faktor = if (splits.isEmpty()) 1.0 else splits.filter { it.first == key && it.second > t.ts }.fold(1.0) { f, s -> f * s.third }
            val delta = faktor * t.bestandsDelta
            if (delta == 0.0 || key.isBlank()) continue
            if (info[key] == null) {
                val produkt = if (t.key.isNotBlank() && t.key != t.symbol) produkte[key] ?: continue else null   // Derivat ohne bekanntes Produkt
                info[key] = Position(key, t.title, t.symbol.ifBlank { key }, 0.0, t.price / faktor, t.ts, produkt)
            }
            qty[key] = (qty[key] ?: 0.0) - delta
        }
        // Verkaufte Optionen stehen mit negativem Bestand
        return info.filter { (k, _) -> abs(qty[k] ?: 0.0) > 1e-9 }.mapValues { (k, p) -> p.copy(qty = qty[k]!!) }
    }

    internal val SPLIT_TAGS = setOf("CORPORATE", "EXCHANGE_IN", "EXCHANGE_OUT")

    // Je Depotschluessel der zuletzt gebuchte Stand.
    fun produkte(account: Account): Map<String, Derivative> {
        val m = HashMap<String, Derivative>()
        for (t in account.transactions) t.derivative?.let { m[t.posKey] = it }
        return m
    }

    // Auf MAX_BUCHUNGEN begrenzen, Dividendenbuchungen bleiben: bookDividends wuerde eine fehlende beim naechsten Tick
    // erneut gutschreiben. Zuerst fallen die aeltesten Buchungen weg, die Bestand und Kapitalfluesse nicht tragen
    // (Alarme, fehlgeschlagene Plaene, Gebuehren), erst dann die aeltesten uebrigen: Fehlen fruehe Kaeufe oder
    // Einzahlungen, stimmen Bestand und Guthaben frueher Zeitpunkte nicht mehr.
    fun kuerzen(tx: List<Transaction>): List<Transaction> {
        val sortiert = tx.sortedBy { it.ts }
        var ueber = sortiert.size - MAX_BUCHUNGEN
        if (ueber <= 0) return sortiert
        val weg = BooleanArray(sortiert.size)
        for ((i, t) in sortiert.withIndex()) {
            if (ueber <= 0) break
            if (t.type == TxType.ALERT || t.type == TxType.PLAN || t.type == TxType.FEE) { weg[i] = true; ueber-- }
        }
        for ((i, t) in sortiert.withIndex()) {
            if (ueber <= 0) break
            if (!weg[i] && t.type != TxType.DIVIDEND) { weg[i] = true; ueber-- }
        }
        return sortiert.filterIndexed { i, _ -> !weg[i] }
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
        // Eigene Kennung der Buchung, etwa fuer eine wiederangelegte Dividende; leer = neue Kennung.
        txId: String = "",
    ): TradeResult {
        if (!qty.isFinite() || qty <= 0.0) return TradeResult(account, Err.INVALID_QTY)
        if (!price.isFinite() || price <= 0.0) return TradeResult(account, Err.NO_PRICE)
        if (!fee.isFinite() || fee < 0) return TradeResult(account, Err.INVALID_FEE)
        if (derivative != null && !Derivatives.tradable(derivative, now)) return TradeResult(account, Err.NOT_TRADABLE)
        if (derivative != null && qty != kotlin.math.floor(qty)) return TradeResult(account, Err.WHOLE_PIECES)
        val cost = qty * price + fee
        if (!cost.isFinite()) return TradeResult(account, Err.TOO_LARGE)
        // Guthaben, das offene Kauforders beanspruchen, steht einem anderen Kauf nicht zur Verfuegung; die Order selbst
        // darf es verbrauchen. Guthaben, das verkaufte Optionen binden, steht keinem Kauf zur Verfuegung.
        val frei = (if (via == VIA_ORDER) account.cash else account.cash - account.reserved) - account.gebunden
        if (cost > frei + 1e-9) return TradeResult(account, Err.NOT_ENOUGH_CASH)

        val existing = account.positions.firstOrNull { it.key == key }
        // Verkaufte Optionen werden ueber StillhalterDepot eroeffnet und zurueckgekauft
        if (derivative?.verkauft == true || existing?.verkauft == true) return TradeResult(account, Err.NOT_TRADABLE)
        val positions = if (existing == null) {
            account.positions + Position(key, name, symbol, qty, price, now, derivative, lots = listOf(Lot(qty, price, now, fee)),
                art = if (derivative == null) Symbols.bekannteArt(symbol)?.name.orEmpty() else "")
        } else {
            val newQty = existing.qty + qty
            val newAvg = (existing.invested + qty * price) / newQty
            account.positions.map {
                if (it.key == key) it.copy(qty = newQty, avgPrice = newAvg, derivative = it.derivative ?: derivative, lots = Posten.dazu(it, Lot(qty, price, now, fee)),
                    art = it.art.ifBlank { if (derivative == null) Symbols.bekannteArt(symbol)?.name.orEmpty() else "" })
                else it
            }
        }
        val tx = Transaction(
            id = txId.ifBlank { newId("tx", now) }, ts = now, type = TxType.BUY,
            title = name, amount = -(qty * price), qty = qty, price = price, symbol = symbol, fee = fee, note = note,
            key = key, tag = "BUY", via = via, derivative = derivative,
        )
        // Die Gebuehr bucht addFee, hier nur der reine Gegenwert.
        var acc = account.copy(cash = kasse(account.cash - qty * price), positions = positions, transactions = kuerzen(account.transactions + tx))
        if (fee > 0) acc = addFee(acc, fee, now, name, symbol)
        // Ein weiteres gekauftes Bein einer Gruppe senkt hoechstens deren Bindung
        derivative?.gruppe?.takeIf { it.isNotBlank() && StillhalterDepot.bindung(acc, it) != null }?.let { g -> acc = StillhalterDepot.neuBinden(acc, g) ?: acc }
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
            account.positions + Position(symbol, name, symbol, qty, price, purchasedAt, null, importedAt = now, lots = listOf(Lot(qty, price, purchasedAt)),
                art = Symbols.bekannteArt(symbol)?.name.orEmpty())
        } else {
            val newQty = existing.qty + qty
            account.positions.map {
                if (it.key == symbol) it.copy(
                    qty = newQty,
                    avgPrice = (existing.invested + cost) / newQty,
                    openedAt = minOf(existing.openedAt, purchasedAt),
                    importedAt = now,
                    lots = Posten.dazu(existing, Lot(qty, price, purchasedAt)),
                    art = it.art.ifBlank { Symbols.bekannteArt(symbol)?.name.orEmpty() },
                ) else it
            }
        }
        val tx = Transaction(
            id = newId("tx", now), ts = purchasedAt, type = TxType.BUY,
            title = name, amount = 0.0, qty = qty, price = price, symbol = symbol, fee = 0.0,
            key = symbol, tag = "TRANSFER_IN", via = "IMPORT", importedAt = now,
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
        if (pos.isKnockedOut || pos.verkauft) return TradeResult(account, Err.NOT_TRADABLE)
        // Aktien, die verkaufte Calls decken, bleiben bis zu deren Ende im Depot
        if (pos.derivative == null && qty > pos.qty - account.gebundeneStueck(pos.symbol) + 1e-9) return TradeResult(account, Err.GEBUNDEN)

        if (!fee.isFinite() || fee < 0) return TradeResult(account, Err.INVALID_FEE)
        val proceeds = qty * price
        if (!proceeds.isFinite()) return TradeResult(account, Err.TOO_LARGE)
        if (account.cash + proceeds < fee) return TradeResult(account, Err.FEE_NOT_COVERED)
        val rest = pos.qty - qty
        // FIFO: die aeltesten Posten gehen zuerst, der Einstand der Position ist danach der Schnitt des Rests.
        val ab = Posten.abgang(Posten.von(pos), qty)
        val positions = if (rest <= 1e-9) account.positions.filter { it.key != key }
        else account.positions.map {
            if (it.key == key) it.copy(qty = rest, avgPrice = Posten.einstand(ab.rest).takeIf { e -> e > 0 } ?: it.avgPrice, lots = ab.rest) else it
        }

        val realized = proceeds - ab.kosten
        // Abgeltungsteuer auf den Gewinn nach Gebuehren. Ein Verlust holt Steuer des Jahres zurueck, der Rest geht in den Topf.
        val taxed = verkaufSteuer(account.tax, pos, ab, price, fee, allowance, now)
        val tx = Transaction(
            id = newId("tx", now), ts = now, type = TxType.SELL,
            title = pos.name, amount = proceeds - taxed.tax, qty = qty, price = price, symbol = pos.symbol,
            tax = taxed.tax, realized = realized, fee = fee, note = note,
            key = key, tag = "SELL", via = via, derivative = pos.derivative,
        )
        var acc = account.copy(
            cash = kasse(account.cash + proceeds - taxed.tax),
            positions = positions,
            transactions = kuerzen(account.transactions + tx),
            tax = taxed.state,
        )
        if (fee > 0) acc = addFee(acc, fee, now, pos.name, pos.symbol)
        // Ein gekauftes Bein, das eine verkaufte Option seiner Gruppe deckt, geht nur, wenn die Gruppe gedeckt bleibt
        pos.derivative?.gruppe?.takeIf { it.isNotBlank() }?.let { g ->
            acc = StillhalterDepot.neuBinden(acc, g) ?: return TradeResult(account, Err.UNGEDECKT)
        }
        return TradeResult(acc)
    }

    // Aktienverluste duerfen nur mit Aktiengewinnen verrechnet werden, alles andere ist sonstig.
    fun taxKind(pos: Position): Tax.Kind =
        if (pos.derivative == null && artOf(pos) == AssetType.STOCK) Tax.Kind.STOCK
        else Tax.Kind.GENERAL

    // Art einer Position: die beim Kauf gespeicherte, sonst die aus Katalog, Hinweisen und Symbolmuster.
    fun artOf(pos: Position): AssetType = pos.artOderNull ?: Symbols.typeOf(pos.symbol)

    // Positionen ohne gespeicherte Art bekommen sie, soweit Katalog oder Kursantwort sie kennen; gespeicherte Arten
    // werden fuer Handelszeiten und Orders gemerkt. Aeltere Staende bleiben sonst unveraendert.
    fun artNachbestimmen(account: Account): Account {
        var geaendert = false
        val positionen = account.positions.map { p ->
            if (p.derivative != null) return@map p
            val gespeichert = p.artOderNull
            if (gespeichert != null) { Symbols.merkeArt(p.symbol, gespeichert); return@map p }
            val bekannt = Symbols.bekannteArt(p.symbol) ?: return@map p
            geaendert = true
            p.copy(art = bekannt.name)
        }
        return if (geaendert) account.copy(positions = positionen) else account
    }

    // Steuer auf einen Verkauf: Gewinn nach FIFO abzueglich Kauf- und Verkaufsgebuehren (§ 20 Abs. 4 EStG). Bei
    // Aktien-ETFs ist ein Teil steuerfrei (Teilfreistellung, § 20 InvStG). Krypto ist ein privates
    // Veraeusserungsgeschaeft (§ 23 EStG): kein Steuerabzug, nach mehr als einem Jahr steuerfrei, sonst gehoert der
    // Gewinn in die Steuererklaerung und wird nur vermerkt.
    private fun verkaufSteuer(state: TaxState, pos: Position, ab: Posten.Abgang, preis: Double, gebuehr: Double, allowance: Double, now: Long): Tax.Result {
        val menge = ab.teile.sumOf { it.qty }
        val gewinn = menge * preis - ab.kosten - ab.gebuehren - gebuehr
        if (istKrypto(pos)) {
            val s = Tax.forYear(state, now)
            val kurz = ab.teile.filter { !ueberEinJahr(it.ts, now) }
            val anteil = if (menge > 0) kurz.sumOf { it.qty } / menge else 0.0
            val privat = kurz.sumOf { it.qty * (preis - it.price) - it.fee } - gebuehr * anteil
            return Tax.Result(s.copy(privateGains = s.privateGains + privat), 0.0, gewinn)
        }
        val quote = if (pos.derivative == null) teilfreistellung(pos.symbol, pos.name, artOf(pos)) else 0.0
        return Tax.apply(state, gewinn * (1.0 - quote), taxKind(pos), allowance, now)
    }

    fun istKrypto(pos: Position): Boolean = pos.derivative == null && artOf(pos) == AssetType.CRYPTO

    // Laenger als ein Jahr gehalten: verkauft nach dem Jahrestag des Kaufs. Die Frist endet erst mit Ablauf des
    // Jahrestags (§ 188 Abs. 2 BGB), am Jahrestag selbst gilt der Verkauf noch als innerhalb eines Jahres.
    fun ueberEinJahr(kauf: Long, verkauf: Long): Boolean {
        val zone = java.time.ZoneId.of("Europe/Berlin")
        val letzterTag = java.time.Instant.ofEpochMilli(kauf).atZone(zone).toLocalDate().plusYears(1)
        return java.time.Instant.ofEpochMilli(verkauf).atZone(zone).toLocalDate().isAfter(letzterTag)
    }

    // Steuerfreier Anteil von Fondsertraegen (Teilfreistellung): Aktien-ETFs 30 %, Mischfonds 15 %, Renten-, Geldmarkt-
    // und Rohstoff-ETFs sowie ETCs nichts. Die Fondsart steht nicht im Katalog, sie wird aus dem Namen gelesen.
    fun teilfreistellung(symbol: String, name: String, art: AssetType? = null): Double {
        if ((art ?: Symbols.typeOf(symbol)) != AssetType.ETF) return 0.0
        val n = " " + name.lowercase() + " "
        return when {
            OHNE_TEILFREISTELLUNG.any { it in n } -> 0.0
            MISCHFONDS.any { it in n } -> 0.15
            else -> 0.30
        }
    }

    private val OHNE_TEILFREISTELLUNG = listOf(
        "bond", "anleihe", "renten", "treasur", "govt", "government", "aggregate", "corporate", "money market", "geldmarkt",
        "overnight", "€str", "estr", "inflation", "gold", "silver", "silber", "commodit", "rohstoff", "bitcoin", "ethereum",
        "crypto", "krypto", " etc ",
    )
    private val MISCHFONDS = listOf("multi-asset", "multi asset", "mischfonds", "allocation", "portfolio")

    internal fun addFee(account: Account, fee: Double, now: Long, what: String, symbol: String): Account {
        val tx = Transaction(
            id = newId("fee", now), ts = now, type = TxType.FEE,
            title = what, subtitle = "", amount = -fee, tag = "FEE", symbol = "",
            note = symbol,
        )
        return account.copy(cash = kasse(account.cash - fee), transactions = kuerzen(account.transactions + tx))
    }

    // Orders

    // kurs: aktueller Kurs des Werts in Euro (0 = unbekannt). Ein Stopp-Verkauf gehoert unter, ein Stopp-Kauf ueber den
    // Kurs; auf der falschen Seite wuerde er sofort auslosen.
    fun orderError(order: Order, kurs: Double = 0.0): String? = when {
        !order.qty.isFinite() || order.qty <= 0 -> Err.INVALID_QTY
        order.type == OrderType.TRAILING_STOP && (!order.trailPct.isFinite() || order.trailPct <= 0 || order.trailPct > 50) -> Err.INVALID_TRAIL
        order.type != OrderType.MARKET && (!order.limitPrice.isFinite() || order.limitPrice <= 0) -> Err.INVALID_LIMIT
        order.key.isBlank() || order.symbol.isBlank() -> Err.MISSING_SECURITY
        order.type == OrderType.STOP && kurs > 0 && stoppAufFalscherSeite(order, kurs) -> Err.INVALID_STOP
        else -> null
    }

    // Stopp-Verkauf mit Marke ab dem Kurs oder Stopp-Kauf mit Marke bis zum Kurs.
    fun stoppAufFalscherSeite(order: Order, kurs: Double): Boolean =
        if (order.side == Side.SELL) order.limitPrice >= kurs else order.limitPrice <= kurs

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

    // Tagesgueltig endet mit dem Handelstag, sonst nach 360 Tagen. Wird die Order bei geschlossener Boerse aufgegeben
    // (abends, am Wochenende, an Feiertagen), gilt sie bis zum Schluss des naechsten Handelstags.
    fun expiryFor(order: Order): Long {
        val window = MarketHours.windowFor(Symbols.typeOf(order.symbol), order.derivative != null)
        return when (order.validity) {
            Validity.DAY -> if (window != null) MarketHours.schlussDesHandelstags(window, order.createdAt)
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
    fun orderAendern(account: Account, id: String, qty: Double, limitPrice: Double, trailPct: Double, validity: Validity, now: Long, kurs: Double = 0.0): TradeResult {
        val alt = account.orders.firstOrNull { it.id == id && it.status == OrderStatus.OPEN } ?: return TradeResult(account, Err.ORDER_CLOSED)
        var neu = when (alt.type) {
            OrderType.TRAILING_STOP -> alt.copy(
                qty = qty, trailPct = trailPct,
                limitPrice = if (alt.side == Side.SELL) alt.trailRef * (1.0 - trailPct / 100.0) else alt.trailRef * (1.0 + trailPct / 100.0),
            )
            OrderType.MARKET -> alt.copy(qty = qty)
            else -> alt.copy(qty = qty, limitPrice = limitPrice)
        }.copy(validity = validity)
        orderError(neu, kurs)?.let { return TradeResult(account, it) }
        if (validity != alt.validity) neu = neu.copy(expiresAt = expiryFor(neu.copy(createdAt = now)))
        return TradeResult(OrderPaar.stueckAngleichen(account.copy(orders = account.orders.map { if (it.id == id) neu else it }), id))
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
            // Im selben Lauf als Gegenorder eines Paars gestrichen
            if (o.paar.isNotEmpty() && acc.orders.none { it.id == o.id && it.status == OrderStatus.OPEN }) continue
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
            if (respectHours && !MarketHours.isOpen(Symbols.typeOf(o.symbol), deriv != null, now)) continue
            // Das Limit gilt fuer den ausfuehrbaren Geld- bzw. Briefkurs, die Spanne eingerechnet.
            val fillPrice = Pricing.withSpread(price, spread.forProduct(deriv), o.side == Side.BUY)
            val triggered = when (order.type) {
                OrderType.LIMIT -> if (order.side == Side.BUY) fillPrice <= order.limitPrice else fillPrice >= order.limitPrice
                OrderType.STOP, OrderType.TRAILING_STOP -> if (order.side == Side.BUY) price >= order.limitPrice else price <= order.limitPrice
                OrderType.MARKET -> true
            }
            if (!triggered) continue

            val res = if (order.side == Side.BUY)
                buy(acc, order.key, order.name, order.symbol, order.qty, fillPrice, deriv, now, note = order.note, via = VIA_ORDER)
            else
                sell(acc, order.key, order.qty, fillPrice, now, allowance = allowance, note = order.note, via = VIA_ORDER)

            acc = if (res.ok) {
                OrderPaar.partnerStreichen(res.account.copy(orders = res.account.orders.map {
                    if (it.id == o.id) it.copy(status = OrderStatus.FILLED, executedAt = now, executedPrice = fillPrice) else it
                }), o.id, now)
            } else {
                acc.copy(orders = acc.orders.map {
                    if (it.id == o.id) it.copy(status = OrderStatus.REJECTED, executedAt = now, reason = res.error ?: "") else it
                })
            }
        }
        return acc
    }

    // Knock-outs, Faktor-Roll, Verfall

    // Mindestalter eines Knock-outs, ab dem auch Tagestief und -hoch des Basiswerts als Beruehrung zaehlen.
    const val EXTREM_AB_MS = 4L * 86_400_000L

    // Schreibt Faktor-Zertifikate fort, erkennt Knock-outs und bucht faellige Produkte ab. Ein Knock-out bleibt
    // zunaechst mit dem Restwert von 0,001 EUR sichtbar, damit der Verlust erkennbar ist. Nach KO_SETTLE_MS zahlt der
    // Emittent den Restwert und die Position verschwindet. dayChanged: der Handelstag hat gewechselt.
    // schluesse: Tagesschlusskurse der Basiswerte (EUR, aufsteigend), soweit sie fuer Tage ohne laufende App gebraucht
    // werden: verpasste Faktor-Tageswechsel, Knock-outs an Tagen dazwischen und der Kurs eines Optionsscheins am Verfallstag.
    fun maintain(
        account: Account,
        quotes: Map<String, Quote>,
        now: Long = System.currentTimeMillis(),
        dayChanged: Boolean = false,
        allowance: Double = 0.0,
        rate: Double = Pricing.RATE,
        schluesse: Map<String, List<Candle>> = emptyMap(),
        spread: Spread = Spread(),
    ): Account {
        // Bindungen verkaufter Optionen folgen dem Wechselkurs
        var acc = StillhalterDepot.fxNachfuehren(account) { s -> quotes[s]?.takeIf { isQuoteFresh(it, now) }?.fx }
        val settle = mutableListOf<Position>()
        val updated = mutableListOf<Position>()
        val heuteTag = now / DAY_MS

        for (p in acc.positions) {
            val d0 = p.derivative ?: continue
            if (d0.isKnockedOut) {
                if (now - d0.knockedOutAt >= KO_SETTLE_MS) settle += p
                continue
            }
            // Faktor-Zertifikate ohne eigenen Tageszaehler (Staende von frueher): der Tageswechsel dieses Laufs gilt einmal.
            val d = if (d0.kind.isFactor && d0.factorRefTag == 0L) d0.copy(factorRefTag = if (dayChanged) heuteTag - 1 else heuteTag) else d0
            val q = quotes[p.symbol]
            if (!isQuoteFresh(q, now)) { if (d !== d0) updated += p.copy(derivative = d); continue }
            val spot = q!!.price
            if (!spot.isFinite() || spot <= 0) { if (d !== d0) updated += p.copy(derivative = d); continue }

            // Knock-out an einem vollen Handelstag zwischen zwei Laeufen der App, aus dem Tagesschluss-Verlauf erkannt.
            if (d.kind.isKnockout) {
                val beruehrt = kerzenKnockout(d, schluesse[p.symbol].orEmpty(), maxOf(p.openedAt, (acc.lastMaintainDay - 1) * DAY_MS), now, q.fx)
                if (beruehrt != null) { updated += p.copy(derivative = beruehrt); continue }
            }

            // Tagestief bzw. -hoch zaehlen nur bei Produkten, die schon einige Tage bestehen, damit kein Extrem von vor dem Kauf greift.
            val tagesextrem = d.createdAt in 1 until now - EXTREM_AB_MS && Pricing.tagesKnockout(d, q.dayLow, q.dayHigh, q.fx)
            if (d.kind.isKnockout && (Pricing.isKnockedOut(d, spot, q.fx) || tagesextrem)) {
                updated += p.copy(derivative = d.copy(knockedOutAt = now, restwert = Pricing.restwert(d, spot, q.fx)))
                continue
            }
            if (d.kind.isOption && d.expiry > 0L && d.expiry <= now) {
                settle += p
                continue
            }
            if (d.kind.isFactor) {
                val rolled = factorFortschreiben(d, spot, heuteTag, schluesse[p.symbol].orEmpty(), rate)
                if (rolled !== d0) updated += p.copy(derivative = rolled)
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

        // Gekaufte Beine vor den verkauften: deren Auszahlung steht dann fuer den Ausgleich bereit
        for (p in settle.sortedBy { it.verkauft }) acc = abrechnen(acc, p, quotes, now, allowance, schluesse, spread)
        return acc
    }

    private const val DAY_MS = 86_400_000L

    // Basiswerte, deren Tagesschlusskurse maintain braucht: Faktor-Zertifikate mit verpassten Tagen, Knock-outs nach
    // Tagen ohne Lauf und Optionsscheine, die schon laenger verfallen sind.
    fun schluesseNoetig(account: Account, now: Long): List<String> {
        val heute = now / DAY_MS
        return account.positions.mapNotNull { p ->
            val d = p.derivative ?: return@mapNotNull null
            if (d.isKnockedOut) return@mapNotNull null
            val noetig = (d.kind.isFactor && d.factorRefTag in 1 until heute - 1) ||
                (d.kind.isKnockout && account.lastMaintainDay in 1 until heute - 1) ||
                (d.kind.isOption && d.expiry in 1 until now - 3_600_000L)
            if (noetig) p.symbol else null
        }.distinct()
    }

    // Faktor-Zertifikat auf den Tag heuteTag bringen. Je verpasstem Tageswechsel (nach Tagen ohne App oder ohne frischen
    // Kurs) wirken Hebel und Finanzierung einmal, mit dem Schlusskurs des Vortags; ohne Tagesschluesse zaehlt nur ein
    // Wechsel mit dem aktuellen Kurs.
    private fun factorFortschreiben(d: Derivative, spot: Double, heuteTag: Long, kerzen: List<Candle>, rate: Double): Derivative {
        val verpasst = heuteTag - d.factorRefTag
        var cur = d
        if (verpasst >= 1) {
            var gerollt = false
            if (verpasst >= 2 && kerzen.isNotEmpty()) {
                for (k in d.factorRefTag + 1..heuteTag) {
                    val schluss = kerzen.lastOrNull { it.t < k * DAY_MS }?.close?.takeIf { it > 0 } ?: continue
                    cur = Pricing.rollFactor(cur, schluss, true, rate)
                    gerollt = true
                }
            }
            if (!gerollt) cur = Pricing.rollFactor(cur, spot, true, rate)
            // Intraday-Reset gegen den neuen Referenzkurs
            cur = Pricing.rollFactor(cur, spot, false, rate)
        } else cur = Pricing.rollFactor(cur, spot, false, rate)
        return if (cur === d && d.factorRefTag == heuteTag) d else cur.copy(factorRefTag = heuteTag)
    }

    // Erster volle Handelstag nach seit, an dem Tagestief (Long) bzw. Tageshoch (Short) die Schwelle beruehrte. Das Produkt
    // gilt ab dem Kerzenzeitpunkt als ausgeknockt, der Restwert folgt aus der Schwelle (bei einer Kurslucke aus der
    // Eroeffnung). Der heutige Tag bleibt dem Live-Kurs ueberlassen.
    internal fun kerzenKnockout(d: Derivative, kerzen: List<Candle>, seit: Long, now: Long, fx: Double): Derivative? {
        if (!d.kind.isKnockout || kerzen.isEmpty()) return null
        val b = Pricing.barrierEur(d, fx)
        val heute = now / DAY_MS
        val long = d.kind == DerivativeKind.KO_LONG
        val c = kerzen.firstOrNull { k ->
            k.t / DAY_MS > seit / DAY_MS && k.t / DAY_MS < heute &&
                (if (long) k.low > 0 && k.low <= b else k.high > 0 && k.high >= b)
        } ?: return null
        val kurs = if (long) minOf(c.open, b) else maxOf(c.open, b)
        return d.copy(knockedOutAt = minOf(c.t, now - 1), restwert = Pricing.restwert(d, kurs, fx))
    }

    // Bucht sofort aus, ohne die zwei Tage bis zur Rueckzahlung abzuwarten.
    // Nur fuer ausgeknockte Produkte und verfallene Optionsscheine.
    fun settle(account: Account, key: String, quotes: Map<String, Quote>, now: Long = System.currentTimeMillis(), allowance: Double = 0.0, schluesse: Map<String, List<Candle>> = emptyMap()): TradeResult {
        val pos = account.positions.firstOrNull { it.key == key } ?: return TradeResult(account, Err.NOT_IN_DEPOT)
        val d = pos.derivative ?: return TradeResult(account, Err.NOT_TRADABLE)
        val faellig = d.isKnockedOut || (d.kind.isOption && d.expiry > 0L && d.expiry <= now)
        if (!faellig) return TradeResult(account, Err.NOT_TRADABLE)
        return TradeResult(abrechnen(account, pos, quotes, now, allowance, schluesse))
    }

    // Restwert bzw. innerer Wert, Steuer, offene Orders storniert. Eine verkaufte Option rechnet gleich ab, nur mit
    // umgekehrtem Vorzeichen: im Geld zahlt der Verkaeufer den inneren Wert, wertlos verfallen gibt die Bindung frei.
    private fun abrechnen(
        account: Account, p: Position, quotes: Map<String, Quote>, now: Long, allowance: Double,
        schluesse: Map<String, List<Candle>> = emptyMap(), spread: Spread = Spread(),
    ): Account {
        val d = p.derivative!!
        val q = quotes[p.symbol]
        val isKo = d.kind.isKnockout
        // Ein verfallener Optionsschein zaehlt mit dem Tagesschluss des Verfallstags (sonst dem letzten davor), nicht mit
        // dem Kurs, den die App beim spaeteren Oeffnen sieht.
        val amVerfall = if (!isKo && d.expiry in 1 until now)
            schluesse[p.symbol]?.lastOrNull { it.t <= d.expiry && it.close > 0 }?.close else null
        val spot = amVerfall ?: q?.price ?: 0.0
        // Optionsschein: innerer Wert am Verfallstag. Knock-out: Restwert.
        val payout = if (isKo) d.restwert.takeIf { it > Pricing.KO_RESIDUAL } ?: Pricing.KO_RESIDUAL else max(0.0, Pricing.intrinsic(d, spot, q?.fx ?: 1.0))
        // Verkaufte Option: qty negativ, proceeds ist die Zahlung (negativ), realized Praemie minus Zahlung
        val proceeds = payout * p.qty
        val realized = proceeds - p.invested
        // Kaufgebuehren mindern den steuerpflichtigen Gewinn. Bei verkauften Optionen ist die Praemie schon versteuert,
        // die Zahlung bei Verfall ist ein negativer Ertrag.
        val taxed = if (p.verkauft) Tax.apply(account.tax, proceeds, Tax.Kind.GENERAL, allowance, now)
            else Tax.apply(account.tax, realized - Posten.von(p).sumOf { it.fee }, Tax.Kind.GENERAL, allowance, now)
        val tx = Transaction(
            id = newId("ko", now), ts = now,
            type = if (isKo) TxType.KNOCKOUT else TxType.EXPIRY,
            title = p.name,
            amount = proceeds - taxed.tax, qty = abs(p.qty), price = payout, symbol = p.symbol,
            tax = taxed.tax, realized = realized, key = p.key, derivative = d,
            tag = if (isKo) "KO_SETTLE" else (if (p.verkauft) "EXPIRY_SOLD" else "EXPIRY") + if (payout > 0) "" else "_WORTHLESS",
            // Fuer den Handelskalender: Ende mit dem Knock-out bzw. am Verfallstag.
            closedAt = (if (isKo) d.knockedOutAt else d.expiry).takeIf { it in 1 until now } ?: 0L,
        )
        var acc = account.copy(
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
        if (d.gruppe.isNotBlank()) acc = StillhalterDepot.neuBinden(acc, d.gruppe) ?: acc
        if (p.verkauft && acc.cash < 0.0) {
            val mid = q?.price ?: spot
            acc = StillhalterDepot.ausgleichen(acc, p.symbol, Pricing.withSpread(mid, spread.stockBps, false), now, allowance)
        }
        return acc
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

    // Erster Zeitpunkt bis bis, zu dem der Plan faellig ist: Beginn des Termintags, mit Uhrzeit diese. 0 = keiner.
    // Ein Termin, der schon vor der letzten Ausfuehrung offen war, liegt entsprechend frueh.
    fun planTermin(plan: SavingsPlan, bis: Long): Long {
        if (plan.paused) return 0L
        val zone = java.time.ZoneId.systemDefault()
        var tag = dateAt(if (plan.lastRun > 0) plan.lastRun else plan.createdAt)
        val ende = dateAt(bis)
        var n = 0
        while (!tag.isAfter(ende) && n++ < 800) {
            val beginn = tag.atStartOfDay(zone).toInstant().toEpochMilli()
            val schluss = tag.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
            if (planFaellig(plan, schluss)) {
                val u = plan.uhrzeit
                val t = maxOf(if (u in 0 until 24 * 60) beginn + u * 60_000L else beginn, plan.createdAt)
                return if (t <= bis) t else 0L
            }
            tag = tag.plusDays(1)
        }
        return 0L
    }

    // Plan nach einer Pause fortsetzen: Termine der Pause werden nicht nachgeholt. Waere der Plan jetzt faellig, gilt
    // der Termin als erledigt und der naechste regulaere kommt zum Zug (wie beim Einzahlplan).
    fun fortsetzen(plan: SavingsPlan, now: Long): SavingsPlan {
        val offen = plan.copy(paused = false)
        return if (planFaellig(offen, now)) offen.copy(lastRun = now) else offen
    }

    // Termine, die seit der letzten Ausfuehrung fuer den Plan faellig waren und nicht einzeln nachgeholt werden: Ein Lauf
    // fuehrt nur einen Termin aus, die davor liegenden zaehlen hier (hoechstens 60).
    fun verpassteTermine(plan: SavingsPlan, now: Long): Int {
        if (plan.lastRun <= 0L) return 0
        var cursor = plan.lastRun
        var n = 0
        var t = startOfDay(cursor) + 86_400_000L
        while (t <= now && n < 60) {
            if (planFaellig(plan.copy(lastRun = cursor, paused = false), t)) { n++; cursor = t }
            t += 86_400_000L
        }
        return maxOf(0, n - 1)
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
                val uebersprungen = verpassteTermine(plan, now)
                val vermerk = if (uebersprungen > 0) listOf(Transaction(
                    id = newId("planskip", now), ts = now, type = TxType.PLAN,
                    title = plan.name, symbol = plan.symbol, tag = "PLAN_SKIPPED", extra = uebersprungen.toDouble(),
                )) else emptyList()
                res.account.copy(
                    plans = res.account.plans.map {
                        if (it.id == plan.id) it.copy(lastRun = now, invested = it.invested + plan.amount, executions = it.executions + 1, lastFailed = 0L) else it
                    },
                    transactions = kuerzen(res.account.transactions + vermerk),
                )
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

    // via: "PLAN" bei der monatlichen Einzahlung (seit 5.91), sonst leer.
    fun deposit(account: Account, amount: Double, now: Long = System.currentTimeMillis(), opening: Boolean = false, via: String = ""): Account {
        if (!amount.isFinite() || amount <= 0 || !(account.cash + amount).isFinite() || !(account.deposited + amount).isFinite()) return account
        val tx = Transaction(id = newId("dep", now), ts = now, type = TxType.DEPOSIT, title = "", amount = amount, tag = if (opening) "OPENING" else "DEPOSIT", via = via)
        return account.copy(
            cash = account.cash + amount,
            deposited = account.deposited + amount,
            transactions = kuerzen(account.transactions + tx),
        )
    }

    // Guthaben, das offene Kauforders beanspruchen oder verkaufte Optionen binden, bleibt im Depot.
    fun withdraw(account: Account, amount: Double, now: Long = System.currentTimeMillis()): TradeResult {
        if (!amount.isFinite() || amount <= 0) return TradeResult(account, Err.INVALID_AMOUNT)
        val available = account.frei
        if (amount > available + 1e-9) return TradeResult(account, Err.ONLY_AVAILABLE, extra = max(0.0, available))
        val tx = Transaction(id = newId("wd", now), ts = now, type = TxType.WITHDRAWAL, title = "", amount = -amount, tag = "WITHDRAWAL")
        return TradeResult(account.copy(
            cash = kasse(account.cash - amount),
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
        // Einstellung „Dividenden wiederanlegen“; je Position gilt die eigene Wahl (Account.wiederanlage) vorrangig.
        wiederanlageGlobal: Boolean = false,
    ): Account {
        var acc = account
        val known = acc.transactions.asSequence().filter { it.type == TxType.DIVIDEND }.map { it.id }.toHashSet()
        for (d in dividends) {
            if (d.exDate + payDelayMs > now || d.perShare <= 0 || !d.perShare.isFinite()) continue
            val id = "div-${d.symbol}-${d.exDate / 86_400_000L}"
            if (id in known) continue
            // Vor dem Kauf der heutigen Position und bei uebernommenen Positionen vor der Uebernahme gibt es nichts.
            val heute = acc.positions.firstOrNull { it.key == d.symbol && it.derivative == null }
            // Yahoo stempelt den Ex-Tag mit der Eroeffnung der Boerse; wer am Ex-Tag kauft, hat keinen Anspruch mehr.
            val exTag = d.exDate / 86_400_000L * 86_400_000L
            if (heute != null && exTag <= maxOf(heute.openedAt, heute.importedAt)) continue
            // Anspruch hat der Bestand am Abend vor dem Ex-Tag, auch wenn er inzwischen verkauft ist.
            val pos = holdingsAt(acc, exTag - 1)[d.symbol]?.takeIf { it.derivative == null } ?: continue
            val gross = kotlin.math.round(pos.qty * d.perShare * 100.0) / 100.0
            if (gross < 0.01) continue
            // Steuerjahr ist das Jahr der Zahlung, solange es im Depot noch offen ist. Bei ETFs gilt die Teilfreistellung.
            val zufluss = d.exDate + payDelayMs
            val wann = if (Tax.yearOf(zufluss) >= acc.tax.year) zufluss else now
            val taxed = Tax.apply(acc.tax, gross * (1.0 - teilfreistellung(pos.symbol, pos.name, acc.positions.firstOrNull { it.key == pos.key }?.artOderNull)), Tax.Kind.GENERAL, allowance, wann)
            val tx = Transaction(
                id = id, ts = d.exDate + payDelayMs, type = TxType.DIVIDEND,
                title = pos.name, amount = gross - taxed.tax, qty = pos.qty, price = d.perShare,
                symbol = pos.symbol, tax = taxed.tax, key = pos.key, tag = "DIVIDEND",
                via = if (wiederanlegen(acc, pos.symbol, wiederanlageGlobal)) WIEDERANLAGE else "",
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

    // Dividenden wiederanlegen (seit 5.92)

    // Markierung einer Dividende, die wiederangelegt werden soll, und Ausloeser des Kaufs damit.
    const val WIEDERANLAGE = "REINVEST"
    const val VIA_DIVIDENDE = "DIVIDEND"

    // So lange nach der Zahlung wartet eine markierte Dividende auf einen frischen Kurs.
    const val WIEDERANLAGE_FRIST_MS = 30L * 86_400_000L

    fun wiederanlageId(dividendeId: String): String = "reinv-$dividendeId"

    // Ob die Dividenden von symbol wiederangelegt werden: eigene Wahl der Position, sonst die Einstellung.
    fun wiederanlegen(account: Account, symbol: String, global: Boolean): Boolean = account.wiederanlage[symbol] ?: global

    // Markierte Dividenden in Bruchstuecke derselben Aktie anlegen: Nettobetrag nach Steuer, ohne Ordergebuehr, zum
    // Briefkurs mit Spread wie beim Sparplan und nur mit frischem Kurs. Ohne frischen Kurs wartet die Dividende bis zum
    // naechsten Lauf, hoechstens WIEDERANLAGE_FRIST_MS. Wer den Schalter inzwischen ausgeschaltet oder die Position
    // verkauft hat, behaelt das Geld als Guthaben.
    fun dividendenWiederanlegen(
        account: Account,
        quotes: Map<String, Quote>,
        now: Long = System.currentTimeMillis(),
        spread: Spread = Spread(),
        global: Boolean = false,
    ): Account {
        val offen = account.transactions.filter {
            it.type == TxType.DIVIDEND && it.via == WIEDERANLAGE && now - it.ts in 0L..WIEDERANLAGE_FRIST_MS
        }
        if (offen.isEmpty()) return account
        val erledigt = account.transactions.asSequence().filter { it.type == TxType.BUY && it.via == VIA_DIVIDENDE }.map { it.id }.toHashSet()
        var acc = account
        for (d in offen) {
            val id = wiederanlageId(d.id)
            if (id in erledigt || !wiederanlegen(acc, d.symbol, global)) continue
            val pos = acc.positions.firstOrNull { it.key == d.key.ifBlank { d.symbol } && it.derivative == null } ?: continue
            val q = quotes[pos.symbol]
            if (!isQuoteFresh(q, now)) continue
            val price = Pricing.ask(q!!.price, spread.stockBps)
            val betrag = minOf(d.amount, acc.cash)
            if (betrag < 0.01 || !price.isFinite() || price <= 0) continue
            val res = buy(acc, pos.key, pos.name, pos.symbol, betrag / price, price, null, now, fee = 0.0, via = VIA_DIVIDENDE, txId = id)
            if (res.ok) { acc = res.account; erledigt += id }
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
            if (a.art.isNotEmpty()) return@filter false
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

    // Ergebnisse der weiteren Alarmarten (Alarmarten.pruefen) einarbeiten: Stand und Meldetag je Alarm, jede Meldung
    // als Ereignis. Alarme, die es inzwischen nicht mehr gibt, werden uebergangen. Die Alarme bleiben bestehen.
    fun artenMelden(
        account: Account, ergebnisse: Map<String, Alarmarten.Ergebnis>, kurse: Map<String, Quote>, now: Long = System.currentTimeMillis(),
    ): Pair<Account, List<PriceAlert>> {
        if (ergebnisse.isEmpty()) return account to emptyList()
        val gemeldet = ArrayList<PriceAlert>()
        val txs = ArrayList<Transaction>()
        val neu = account.alerts.map { a ->
            val e = ergebnisse[a.id] ?: return@map a
            val n = a.copy(zustand = e.neu.zustand, gemeldetAm = e.neu.gemeldetAm, merk = e.neu.merk)
            if (e.gemeldet) {
                gemeldet += n
                txs += Transaction(
                    id = newId("alert", now), ts = now, type = TxType.ALERT, title = a.name, symbol = a.symbol,
                    price = kurse[a.symbol]?.price ?: 0.0, extra = e.extra, tag = e.tag, subtitle = e.text,
                )
            }
            n
        }
        if (neu == account.alerts && txs.isEmpty()) return account to emptyList()
        return account.copy(alerts = neu, transactions = if (txs.isEmpty()) account.transactions else kuerzen(account.transactions + txs)) to gemeldet
    }

    // Handelstag eines Zeitpunkts: Tage seit 1970 in der Zeitzone der Boerse.
    fun handelstag(t: Long): Long = java.time.Instant.ofEpochMilli(t).atZone(MarketHours.zone).toLocalDate().toEpochDay()

    // Watchlist

    // Das erste Hinzufuegen bleibt in WatchMeta erhalten, fuer „seit erstem Hinzufuegen“.
    // Zeitpunkt und Kurs des letzten Hinzufuegens werden neu gesetzt.
    // liste: Index der Liste (0 = erste). Steht der Wert schon auf einer anderen Liste, bleibt sein Bezugspunkt.
    fun watch(account: Account, symbol: String, price: Double, now: Long, liste: Int = 0): Account {
        val l = liste.coerceIn(0, account.weitereListen.size)
        val ziel = account.watchlisten[l].symbole
        if (symbol in ziel) return account
        val alt = account.watchMeta[symbol]
        val meta = if (symbol in account.watchlist) account.watchMeta else if (price > 0) {
            account.watchMeta + (symbol to WatchMeta(now, price, firstAddedAt = alt?.ersterZeitpunkt ?: now, firstPrice = alt?.ersterKurs ?: price))
        } else account.watchMeta
        return Watchlisten.setze(account, l, ziel + symbol).copy(watchMeta = meta)
    }

    // Der Eintrag in watchMeta bleibt, damit ein erneutes Hinzufuegen das erste kennt. liste null = von allen Listen.
    fun unwatch(account: Account, symbol: String, liste: Int? = null): Account {
        var r = account
        for (i in account.watchlisten.indices) {
            if (liste != null && liste != i) continue
            r = Watchlisten.setze(r, i, r.watchlisten[i].symbole - symbol)
        }
        return r
    }

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
