package de.troetenrepublik.app.market.broker

import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.OrderStatus
import de.troetenrepublik.app.data.OrderType
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.data.Range
import de.troetenrepublik.app.data.Series
import de.troetenrepublik.app.data.Side
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.data.Validity
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.plattform.BrokerNetz
import de.troetenrepublik.app.market.broker.BrokerJson.arr
import de.troetenrepublik.app.market.broker.BrokerJson.obj
import de.troetenrepublik.app.market.broker.BrokerJson.text
import de.troetenrepublik.app.market.broker.BrokerJson.zahl
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.net.URLEncoder

// Tradier (https://tradier.com, Doku https://docs.tradier.com): US-Aktien und ETFs, Anmeldung per Access Token
// (Authorization: Bearer). Die Sandbox ist ein Testkonto mit 15 Minuten verzoegerten Kursen. Aktiengattungen mit Punkt
// statt Bindestrich (BRK.B statt BRK-B). Bruchstuecke und Trailing-Stops gibt es nicht.
// Listen mit nur einem Eintrag kommen als Objekt, leere als Text "null".
class TradierBroker(
    private val api: MarketApi,
    private val token: String,
    override val live: Boolean,
) : Broker {
    private companion object {
        // Nur an diese Hosts gehen Anfragen mit dem Schluessel, siehe BrokerNetz.
        val HOSTS = setOf("api.tradier.com", "sandbox.tradier.com")
    }

    override val art = BrokerArt.TRADIER

    private val basis = if (live) "https://api.tradier.com/v1" else "https://sandbox.tradier.com/v1"
    private val kopf = mapOf("Authorization" to "Bearer ${token.trim()}")

    @Volatile private var konto: String? = null
    @Volatile private var namen: Map<String, String> = emptyMap()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun tradier(app: String) = app.replace('-', '.')
    private fun app(tradier: String) = tradier.replace('.', '-')

    private fun liste(e: JsonElement?): List<JsonObject> = when (e) {
        is JsonArray -> e.mapNotNull { it.obj() }
        is JsonObject -> listOf(e)
        else -> emptyList()
    }

    private suspend fun holen(pfad: String): JsonObject {
        val a = BrokerNetz.anfrage(basis + pfad, HOSTS, kopf)
        if (!a.ok) throw fehler(a)
        return BrokerJson.lesen(a.text).obj() ?: throw BrokerFehler("ABGELEHNT", a.text?.take(200))
    }

    private fun fehler(a: MarketApi.Antwort): BrokerFehler {
        val e = BrokerJson.lesen(a.text).obj()?.get("errors").obj()?.get("error")
        val text = when (e) { is JsonArray -> e.joinToString("; ") { it.toString().trim('"') }; null -> null; else -> e.toString().trim('"') }
        return if (text != null && a.code !in setOf(0, 401, 403)) BrokerFehler("ABGELEHNT", text) else BrokerJson.fehler(a)
    }

    private suspend fun kontoNummer(): String {
        konto?.let { return it }
        val p = holen("/user/profile").obj()?.get("profile").obj()
        val n = liste(p?.get("account")).firstNotNullOfOrNull { it.text("account_number") } ?: throw BrokerFehler("ABGELEHNT", "no account")
        konto = n
        return n
    }

    override fun fuehrt(symbol: String): Boolean =
        !symbol.startsWith("^") && !symbol.contains('=') && !symbol.contains('.') && !symbol.endsWith("-USD") && !symbol.endsWith("-EUR")

    override fun ordertypen(symbol: String): Set<OrderType> = setOf(OrderType.MARKET, OrderType.LIMIT, OrderType.STOP)

    override fun bruchstuecke(symbol: String): Boolean = false

    override suspend fun stand(): BrokerStand {
        val n = kontoNummer()
        val b = holen("/accounts/$n/balances").obj()?.get("balances").obj()
        val kaufkraft = b?.get("margin").obj().zahl("stock_buying_power") ?: b?.get("cash").obj().zahl("cash_available") ?: b.zahl("total_cash") ?: 0.0
        val pos = liste(holen("/accounts/$n/positions")["positions"].obj()?.get("position")).mapNotNull { o ->
            val s = o.text("symbol") ?: return@mapNotNull null
            val menge = o.zahl("quantity") ?: return@mapNotNull null
            BrokerPosition(app(s), namen[app(s)] ?: app(s), menge, (o.zahl("cost_basis") ?: 0.0) / menge, "USD", AssetType.STOCK)
        }
        val orders = liste(holen("/accounts/$n/orders")["orders"].obj()?.get("order")).mapNotNull { order(it) }
        // Die Kontohistorie gibt es in der Sandbox nicht.
        val umsaetze = runCatching { liste(holen("/accounts/$n/history?limit=100")["history"].obj()?.get("event")).mapIndexedNotNull { i, e -> umsatz(e, i) } }
            .getOrDefault(emptyList())
        return BrokerStand("USD", b.zahl("total_cash") ?: 0.0, kaufkraft, pos, orders, umsaetze, n)
    }

    private fun order(o: JsonObject): BrokerOrder? {
        val s = o.text("symbol") ?: return null
        val typ = when (o.text("type")) { "limit" -> OrderType.LIMIT; "stop", "stop_limit" -> OrderType.STOP; else -> OrderType.MARKET }
        val status = when (o.text("status")) {
            "filled" -> OrderStatus.FILLED; "canceled" -> OrderStatus.CANCELLED; "expired" -> OrderStatus.EXPIRED
            "rejected", "error" -> OrderStatus.REJECTED; else -> OrderStatus.OPEN
        }
        val erstellt = BrokerJson.zeit(o.text("create_date")) ?: 0L
        return BrokerOrder(
            id = o.text("id") ?: return null, symbol = app(s), name = namen[app(s)] ?: app(s),
            seite = if (o.text("side")?.startsWith("sell") == true) Side.SELL else Side.BUY, typ = typ,
            menge = o.zahl("quantity") ?: 0.0, limit = o.zahl("price"), stop = o.zahl("stop_price") ?: o.zahl("stop"), trailPct = null,
            status = status, erstellt = erstellt,
            ausgefuehrt = if (status == OrderStatus.FILLED) BrokerJson.zeit(o.text("transaction_date")) ?: erstellt else null,
            kurs = o.zahl("avg_fill_price")?.takeIf { it > 0 }, waehrung = "USD",
            gueltigkeit = if (o.text("duration") == "gtc") Validity.GTC else Validity.DAY,
            ausgefuehrteMenge = o.zahl("exec_quantity") ?: 0.0,
        )
    }

    private fun umsatz(e: JsonObject, i: Int): BrokerUmsatz? {
        val zeit = BrokerJson.zeit(e.text("date")) ?: return null
        val betrag = e.zahl("amount") ?: 0.0
        val id = "h$zeit-$i"
        return when (e.text("type")) {
            "trade" -> {
                val t = e["trade"].obj()
                val menge = t.zahl("quantity") ?: 0.0
                BrokerUmsatz(id, zeit, if (menge < 0 || betrag > 0) TxType.SELL else TxType.BUY, t.text("symbol")?.let { app(it) },
                    kotlin.math.abs(menge), t.zahl("price") ?: 0.0, betrag, "USD", t.zahl("commission") ?: 0.0, t.text("description").orEmpty())
            }
            "dividend" -> BrokerUmsatz(id, zeit, TxType.DIVIDEND, null, 0.0, 0.0, betrag, "USD", text = e["dividend"].obj().text("description").orEmpty())
            "interest" -> BrokerUmsatz(id, zeit, TxType.INTEREST, null, 0.0, 0.0, betrag, "USD")
            "fee" -> BrokerUmsatz(id, zeit, TxType.FEE, null, 0.0, 0.0, betrag, "USD")
            "ach", "wire", "journal", "transfer" -> BrokerUmsatz(id, zeit, if (betrag >= 0) TxType.DEPOSIT else TxType.WITHDRAWAL, null, 0.0, 0.0, betrag, "USD")
            else -> null
        }
    }

    override suspend fun order(auftrag: Auftrag): String {
        val n = kontoNummer()
        val form = buildList {
            add("class" to "equity")
            add("symbol" to tradier(auftrag.symbol))
            add("side" to if (auftrag.seite == Side.SELL) "sell" else "buy")
            add("quantity" to auftrag.menge.toLong().toString())
            add("type" to when (auftrag.typ) { OrderType.LIMIT -> "limit"; OrderType.STOP -> "stop"; else -> "market" })
            add("duration" to if (auftrag.gueltigkeit == Validity.GTC && auftrag.typ != OrderType.MARKET) "gtc" else "day")
            when (auftrag.typ) {
                OrderType.LIMIT -> auftrag.preis?.let { add("price" to "%.2f".format(java.util.Locale.ROOT, it)) }
                OrderType.STOP -> auftrag.preis?.let { add("stop" to "%.2f".format(java.util.Locale.ROOT, it)) }
                else -> {}
            }
        }
        val a = BrokerNetz.anfrage("$basis/accounts/$n/orders", HOSTS, kopf, methode = "POST", formular = form)
        if (!a.ok) throw fehler(a)
        val o = BrokerJson.lesen(a.text).obj()?.get("order").obj()
        if (o.text("status") != null && o.text("status") != "ok") throw BrokerFehler("ABGELEHNT", o.text("status"))
        return o.text("id").orEmpty()
    }

    // Tradier aendert Art, Gueltigkeit und Preise einer offenen Order, nicht die Stueckzahl.
    override fun aenderbar(symbol: String) = Aenderbar(menge = false, preis = true, gueltigkeit = true)

    override suspend fun aendern(id: String, auftrag: Auftrag) {
        val form = buildList {
            add("type" to when (auftrag.typ) { OrderType.LIMIT -> "limit"; OrderType.STOP -> "stop"; else -> "market" })
            add("duration" to if (auftrag.gueltigkeit == Validity.GTC && auftrag.typ != OrderType.MARKET) "gtc" else "day")
            when (auftrag.typ) {
                OrderType.LIMIT -> auftrag.preis?.let { add("price" to "%.2f".format(java.util.Locale.ROOT, it)) }
                OrderType.STOP -> auftrag.preis?.let { add("stop" to "%.2f".format(java.util.Locale.ROOT, it)) }
                else -> {}
            }
        }
        val a = BrokerNetz.anfrage("$basis/accounts/${kontoNummer()}/orders/${enc(id)}", HOSTS, kopf, methode = "PUT", formular = form)
        if (!a.ok) throw fehler(a)
    }

    override suspend fun stornieren(id: String) {
        val a = BrokerNetz.anfrage("$basis/accounts/${kontoNummer()}/orders/${enc(id)}", HOSTS, kopf, methode = "DELETE")
        if (!a.ok) throw fehler(a)
    }

    override suspend fun kurse(symbole: List<String>): Map<String, Quote> {
        if (symbole.isEmpty()) return emptyMap()
        val zustand = runCatching { holen("/markets/clock")["clock"].obj().text("state") }.getOrNull()
        val jetzt = System.currentTimeMillis()
        val aus = HashMap<String, Quote>()
        for (teil in symbole.chunked(100)) {
            val q = holen("/markets/quotes?symbols=" + teil.joinToString(",") { enc(tradier(it)) })["quotes"].obj()?.get("quote")
            for (o in liste(q)) {
                val s = app(o.text("symbol") ?: continue)
                val preis = o.zahl("last") ?: o.zahl("prevclose") ?: continue
                o.text("description")?.let { namen = namen + (s to it) }
                aus[s] = Quote(
                    symbol = s, name = o.text("description").orEmpty(), price = preis, prevClose = o.zahl("prevclose") ?: preis,
                    open = o.zahl("open") ?: preis, dayHigh = o.zahl("high") ?: preis, dayLow = o.zahl("low") ?: preis,
                    weekHigh52 = o.zahl("week_52_high") ?: 0.0, weekLow52 = o.zahl("week_52_low") ?: 0.0, volume = o.zahl("volume") ?: 0.0,
                    currency = "USD", nativeCurrency = "USD", nativePrice = preis, exchange = "Tradier",
                    marketState = when (zustand) { "open" -> "REGULAR"; "premarket" -> "PRE"; "postmarket" -> "POST"; else -> "CLOSED" },
                    updatedAt = jetzt, regularPrice = preis, marketTime = o.zahl("trade_date")?.toLong() ?: jetzt,
                )
            }
        }
        return aus
    }

    override suspend fun kerzen(symbol: String, range: Range): Series? {
        val jetzt = System.currentTimeMillis()
        fun tag(tage: Int) = java.time.LocalDate.ofEpochDay(Math.floorDiv(jetzt, 86_400_000L) - tage).toString()
        val s = enc(tradier(symbol))
        val kerzen = when (range) {
            Range.M3, Range.M6, Range.Y1, Range.MAX -> {
                val intervall = if (range == Range.MAX) "weekly" else "daily"
                val start = tag(when (range) { Range.M3 -> 95; Range.M6 -> 186; Range.Y1 -> 370; else -> 3660 })
                liste(holen("/markets/history?symbol=$s&interval=$intervall&start=$start")["history"].obj()?.get("day")).mapNotNull { o ->
                    val t = BrokerJson.zeit(o.text("date")) ?: return@mapNotNull null
                    Candle(t, o.zahl("open") ?: 0.0, o.zahl("high") ?: 0.0, o.zahl("low") ?: 0.0, o.zahl("close") ?: return@mapNotNull null, o.zahl("volume") ?: 0.0)
                }
            }
            else -> {
                val (intervall, tage) = when (range) { Range.D1 -> "5min" to 4; Range.W1 -> "15min" to 8; else -> "15min" to 32 }
                val k = liste(holen("/markets/timesales?symbol=$s&interval=$intervall&start=${enc(tag(tage) + " 00:00")}&session_filter=all")["series"].obj()?.get("data")).mapNotNull { o ->
                    val t = o.zahl("timestamp")?.toLong()?.times(1000) ?: return@mapNotNull null
                    Candle(t, o.zahl("open") ?: 0.0, o.zahl("high") ?: 0.0, o.zahl("low") ?: 0.0, o.zahl("close") ?: o.zahl("price") ?: return@mapNotNull null, o.zahl("volume") ?: 0.0)
                }
                if (range == Range.D1 && k.isNotEmpty()) k.filter { k.last().t - it.t < 20 * 3_600_000L } else k
            }
        }
        return Series(kerzen)
    }

    override suspend fun suche(text: String): List<MarketApi.SearchHit> {
        val q = text.trim()
        if (q.isEmpty()) return emptyList()
        val o = holen("/markets/search?q=${enc(q)}&indexes=false")["securities"].obj()?.get("security")
        val treffer = liste(o).mapNotNull { e ->
            val s = e.text("symbol") ?: return@mapNotNull null
            val typ = when (e.text("type")) { "etf" -> "ETF"; "stock" -> "EQUITY"; else -> return@mapNotNull null }
            MarketApi.SearchHit(app(s), e.text("description") ?: s, typ, e.text("exchange").orEmpty())
        }
        // Ein Kuerzel wie "AAPL" findet die Volltextsuche nicht immer, dann nachschlagen.
        if (treffer.none { it.symbol.equals(q, ignoreCase = true) } && q.length <= 6 && !q.contains(' ')) {
            val l = runCatching { liste(holen("/markets/lookup?q=${enc(q)}&types=stock,etf")["securities"].obj()?.get("security")) }.getOrDefault(emptyList())
            val exakt = l.firstOrNull { it.text("symbol").equals(q, ignoreCase = true) }
            if (exakt != null) return listOf(MarketApi.SearchHit(app(exakt.text("symbol")!!), exakt.text("description").orEmpty(), if (exakt.text("type") == "etf") "ETF" else "EQUITY", exakt.text("exchange").orEmpty())) + treffer
        }
        return treffer.take(30)
    }
}
