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
import de.troetenrepublik.app.market.broker.BrokerJson.wahr
import de.troetenrepublik.app.market.broker.BrokerJson.zahl
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URLEncoder

// Alpaca (https://alpaca.markets, Doku https://docs.alpaca.markets): US-Aktien, ETFs und Kryptowerte, mit
// Papierkonto. Anmeldung mit Schluessel-ID und Secret in den Kopfzeilen APCA-API-KEY-ID und APCA-API-SECRET-KEY.
// Kurse kommen aus dem kostenlosen IEX-Feed (eine US-Boerse, nicht der Gesamtmarkt), Kryptokurse von Alpaca selbst.
// Symbole: US-Aktien wie bei Yahoo (AAPL), Aktiengattungen mit Punkt statt Bindestrich (BRK.B gegen BRK-B), Krypto mit
// Schraegstrich (BTC/USD gegen BTC-USD).
class AlpacaBroker(
    private val api: MarketApi,
    private val schluessel: String,
    private val geheim: String,
    override val live: Boolean,
) : Broker {
    private companion object {
        // Nur an diese Hosts gehen Anfragen mit dem Schluessel (siehe BrokerNetz).
        val HOSTS = setOf("api.alpaca.markets", "paper-api.alpaca.markets", "data.alpaca.markets")
    }

    override val art = BrokerArt.ALPACA

    private val handel = if (live) "https://api.alpaca.markets" else "https://paper-api.alpaca.markets"
    private val daten = "https://data.alpaca.markets"
    private val kopf = mapOf("APCA-API-KEY-ID" to schluessel.trim(), "APCA-API-SECRET-KEY" to geheim.trim())

    private data class Wert(val symbol: String, val name: String, val krypto: Boolean, val bruch: Boolean)

    @Volatile private var werte: Map<String, Wert> = emptyMap()
    @Volatile private var werteZeit = 0L
    @Volatile private var offen: Pair<Long, Boolean>? = null

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private suspend fun holen(url: String): kotlinx.serialization.json.JsonElement {
        val a = BrokerNetz.anfrage(url, HOSTS, kopf)
        if (!a.ok) throw BrokerJson.fehler(a)
        return BrokerJson.lesen(a.text) ?: throw BrokerFehler("ABGELEHNT", a.text?.take(200))
    }

    // Symbole

    fun alpacaSymbol(app: String): String = when {
        app.endsWith("-USD") -> app.removeSuffix("-USD") + "/USD"
        else -> app.replace('-', '.')
    }

    fun appSymbol(alpaca: String): String = when {
        alpaca.contains('/') -> alpaca.replace('/', '-')
        // Krypto-Positionen meldet Alpaca ohne Schraegstrich (BTCUSD)
        alpaca.endsWith("USD") && werte[alpaca.removeSuffix("USD") + "/USD"] != null -> alpaca.removeSuffix("USD") + "-USD"
        else -> alpaca.replace('.', '-')
    }

    private suspend fun werteLaden() {
        if (werte.isNotEmpty() && System.currentTimeMillis() - werteZeit < 24 * 3_600_000L) return
        val liste = holen("$handel/v2/assets?status=active").arr()
        val m = HashMap<String, Wert>(liste.size)
        for (e in liste) {
            val o = e.obj() ?: continue
            if (!o.wahr("tradable")) continue
            val s = o.text("symbol") ?: continue
            m[s] = Wert(s, o.text("name").orEmpty(), o.text("class") == "crypto", o.wahr("fractionable"))
        }
        if (m.isNotEmpty()) { werte = m; werteZeit = System.currentTimeMillis() }
    }

    override fun fuehrt(symbol: String): Boolean {
        if (symbol.startsWith("^") || symbol.endsWith("=X") || symbol.endsWith("=F")) return false
        val w = werte
        if (w.isNotEmpty()) return w.containsKey(alpacaSymbol(symbol))
        // Vor dem ersten Laden: US-Kuerzel ohne Boersenendung und Krypto in Dollar
        return symbol.endsWith("-USD") || !symbol.contains('.')
    }

    override fun ordertypen(symbol: String): Set<OrderType> =
        if (symbol.endsWith("-USD")) setOf(OrderType.MARKET, OrderType.LIMIT, OrderType.STOP)
        else setOf(OrderType.MARKET, OrderType.LIMIT, OrderType.STOP, OrderType.TRAILING_STOP)

    override fun bruchstuecke(symbol: String): Boolean = werte[alpacaSymbol(symbol)]?.let { it.bruch || it.krypto } ?: symbol.endsWith("-USD")

    // Konto

    override suspend fun stand(): BrokerStand {
        val konto = holen("$handel/v2/account").obj()
        runCatching { werteLaden() }
        val positionen = holen("$handel/v2/positions").arr().mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val s = o.text("symbol") ?: return@mapNotNull null
            val app = appSymbol(s)
            val krypto = o.text("asset_class") == "crypto"
            BrokerPosition(
                symbol = app, name = werte[alpacaSymbol(app)]?.name ?: app,
                menge = (o.zahl("qty") ?: 0.0) * (if (o.text("side") == "short") -1 else 1),
                einstand = o.zahl("avg_entry_price") ?: 0.0, waehrung = "USD",
                art = if (krypto) AssetType.CRYPTO else AssetType.STOCK,
            )
        }
        val orders = holen("$handel/v2/orders?status=all&limit=200&direction=desc&nested=false").arr().mapNotNull { e -> order(e.obj() ?: return@mapNotNull null) }
        val umsaetze = runCatching { umsaetze() }.getOrDefault(emptyList())
        return BrokerStand(
            waehrung = konto.text("currency") ?: "USD",
            cash = konto.zahl("cash") ?: 0.0,
            kaufkraft = konto.zahl("buying_power") ?: konto.zahl("cash") ?: 0.0,
            positionen = positionen, orders = orders, umsaetze = umsaetze,
            konto = konto.text("account_number").orEmpty(),
        )
    }

    private fun order(o: kotlinx.serialization.json.JsonObject): BrokerOrder? {
        val s = o.text("symbol") ?: return null
        val app = appSymbol(s)
        val typ = when (o.text("order_type") ?: o.text("type")) {
            "limit" -> OrderType.LIMIT; "stop", "stop_limit" -> OrderType.STOP; "trailing_stop" -> OrderType.TRAILING_STOP; else -> OrderType.MARKET
        }
        val status = when (o.text("status")) {
            "filled" -> OrderStatus.FILLED
            "canceled", "replaced", "pending_cancel", "done_for_day" -> OrderStatus.CANCELLED
            "expired" -> OrderStatus.EXPIRED
            "rejected", "suspended", "stopped" -> OrderStatus.REJECTED
            else -> OrderStatus.OPEN
        }
        val menge = o.zahl("qty") ?: o.zahl("filled_qty") ?: 0.0
        return BrokerOrder(
            id = o.text("id") ?: return null, symbol = app, name = werte[s]?.name ?: app,
            seite = if (o.text("side") == "sell") Side.SELL else Side.BUY, typ = typ, menge = menge,
            limit = o.zahl("limit_price"), stop = o.zahl("stop_price") ?: o.zahl("hwm")?.takeIf { typ == OrderType.TRAILING_STOP },
            trailPct = o.zahl("trail_percent"), status = status,
            erstellt = BrokerJson.zeit(o.text("created_at")) ?: 0L,
            ausgefuehrt = BrokerJson.zeit(o.text("filled_at")),
            kurs = o.zahl("filled_avg_price"), waehrung = "USD",
            gueltigkeit = if (o.text("time_in_force") == "gtc") Validity.GTC else Validity.DAY,
            ausgefuehrteMenge = o.zahl("filled_qty") ?: 0.0,
        )
    }

    private suspend fun umsaetze(): List<BrokerUmsatz> =
        holen("$handel/v2/account/activities?activity_types=FILL,DIV,DIVNRA,CSD,CSW,FEE,INT,JNLC&direction=desc&page_size=100").arr().mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val id = o.text("id") ?: return@mapNotNull null
            val art = o.text("activity_type")
            val symbol = o.text("symbol")?.let { appSymbol(it) }
            if (art == "FILL") {
                val menge = o.zahl("qty") ?: 0.0
                val kurs = o.zahl("price") ?: 0.0
                val kauf = o.text("side") != "sell"
                BrokerUmsatz(
                    id, BrokerJson.zeit(o.text("transaction_time")) ?: 0L, if (kauf) TxType.BUY else TxType.SELL,
                    symbol, menge, kurs, if (kauf) -menge * kurs else menge * kurs, "USD",
                )
            } else {
                val typ = when (art) {
                    "DIV", "DIVNRA" -> TxType.DIVIDEND; "CSD" -> TxType.DEPOSIT; "CSW" -> TxType.WITHDRAWAL
                    "FEE" -> TxType.FEE; "INT" -> TxType.INTEREST
                    else -> if ((o.zahl("net_amount") ?: 0.0) >= 0) TxType.DEPOSIT else TxType.WITHDRAWAL
                }
                BrokerUmsatz(
                    id, BrokerJson.zeit(o.text("date")) ?: 0L, typ, symbol, o.zahl("qty") ?: 0.0, o.zahl("per_share_amount") ?: 0.0,
                    o.zahl("net_amount") ?: 0.0, "USD", text = o.text("description").orEmpty(),
                )
            }
        }

    override suspend fun order(auftrag: Auftrag): String {
        val krypto = auftrag.symbol.endsWith("-USD")
        val koerper = buildJsonObject {
            // Eigene Kennung je Order: Alpaca nimmt dieselbe Kennung kein zweites Mal an
            put("client_order_id", auftrag.kennung)
            put("symbol", alpacaSymbol(auftrag.symbol))
            put("qty", zahlText(auftrag.menge))
            put("side", if (auftrag.seite == Side.SELL) "sell" else "buy")
            put("type", when (auftrag.typ) {
                OrderType.MARKET -> "market"; OrderType.LIMIT -> "limit"; OrderType.STOP -> "stop"; OrderType.TRAILING_STOP -> "trailing_stop"
            })
            // Bruchstuecke nur als Tagesorder; Krypto nur gtc oder ioc
            put("time_in_force", when {
                krypto -> "gtc"
                auftrag.menge % 1.0 != 0.0 -> "day"
                auftrag.gueltigkeit == Validity.GTC -> "gtc"
                else -> "day"
            })
            when (auftrag.typ) {
                OrderType.LIMIT -> auftrag.preis?.let { put("limit_price", preisText(it)) }
                OrderType.STOP -> auftrag.preis?.let { put("stop_price", preisText(it)) }
                OrderType.TRAILING_STOP -> auftrag.trailPct?.let { put("trail_percent", zahlText(it)) }
                OrderType.MARKET -> {}
            }
        }
        val a = BrokerNetz.anfrage("$handel/v2/orders", HOSTS, kopf, methode = "POST", json = koerper.toString())
        if (!a.ok) throw BrokerJson.fehler(a)
        return BrokerJson.lesen(a.text).obj().text("id") ?: ""
    }

    override suspend fun stornieren(id: String) {
        val a = BrokerNetz.anfrage("$handel/v2/orders/${enc(id.removePrefix("broker-"))}", HOSTS, kopf, methode = "DELETE")
        if (a.code !in 200..299) throw BrokerJson.fehler(a)
    }

    // Ersetzen einer offenen Order (PATCH): Menge, Preise, Abstand und Gueltigkeit; Krypto bleibt gtc
    override fun aenderbar(symbol: String) = Aenderbar(menge = true, preis = true, gueltigkeit = !symbol.endsWith("-USD"))

    override suspend fun aendern(id: String, auftrag: Auftrag) {
        val koerper = buildJsonObject {
            put("qty", zahlText(auftrag.menge))
            if (!auftrag.symbol.endsWith("-USD")) {
                put("time_in_force", if (auftrag.menge % 1.0 == 0.0 && auftrag.gueltigkeit == Validity.GTC) "gtc" else "day")
            }
            when (auftrag.typ) {
                OrderType.LIMIT -> auftrag.preis?.let { put("limit_price", preisText(it)) }
                OrderType.STOP -> auftrag.preis?.let { put("stop_price", preisText(it)) }
                OrderType.TRAILING_STOP -> auftrag.trailPct?.let { put("trail", zahlText(it)) }
                OrderType.MARKET -> {}
            }
            put("client_order_id", auftrag.kennung)
        }
        val a = BrokerNetz.anfrage("$handel/v2/orders/${enc(id.removePrefix("broker-"))}", HOSTS, kopf, methode = "PATCH", json = koerper.toString())
        if (!a.ok) throw BrokerJson.fehler(a)
    }

    private fun zahlText(v: Double): String = java.math.BigDecimal.valueOf(v).setScale(9, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    // Alpaca nimmt bei Kursen ab 1 $ nur zwei Nachkommastellen, darunter vier.
    private fun preisText(v: Double): String = java.math.BigDecimal.valueOf(v).setScale(if (v >= 1.0) 2 else 4, java.math.RoundingMode.HALF_UP).toPlainString()

    // Kurse

    private suspend fun boerseOffen(): Boolean {
        val jetzt = System.currentTimeMillis()
        offen?.takeIf { jetzt - it.first < 60_000L }?.let { return it.second }
        val o = runCatching { holen("$handel/v2/clock").obj() }.getOrNull()
        val auf = o.wahr("is_open")
        if (o != null) offen = jetzt to auf
        return auf
    }

    override suspend fun kurse(symbole: List<String>): Map<String, Quote> {
        if (symbole.isEmpty()) return emptyMap()
        val aus = HashMap<String, Quote>()
        val jetzt = System.currentTimeMillis()
        val (krypto, aktien) = symbole.partition { it.endsWith("-USD") }
        if (aktien.isNotEmpty()) {
            val auf = boerseOffen()
            for (teil in aktien.chunked(100)) {
                val o = holen("$daten/v2/stocks/snapshots?feed=iex&symbols=" + teil.joinToString(",") { enc(alpacaSymbol(it)) }).obj() ?: continue
                for (s in teil) schnappschuss(s, o[alpacaSymbol(s)].obj(), if (auf) "REGULAR" else "CLOSED", jetzt)?.let { aus[s] = it }
            }
        }
        if (krypto.isNotEmpty()) {
            val o = holen("$daten/v1beta3/crypto/us/snapshots?symbols=" + krypto.joinToString(",") { enc(alpacaSymbol(it)) }).obj()
                ?.get("snapshots").obj()
            for (s in krypto) schnappschuss(s, o?.get(alpacaSymbol(s)).obj(), "REGULAR", jetzt)?.let { aus[s] = it }
        }
        return aus
    }

    private fun schnappschuss(symbol: String, o: kotlinx.serialization.json.JsonObject?, zustand: String, jetzt: Long): Quote? {
        o ?: return null
        val handel = o["latestTrade"].obj()
        val tag = o["dailyBar"].obj()
        val vortag = o["prevDailyBar"].obj()
        val preis = handel.zahl("p") ?: tag.zahl("c") ?: return null
        return Quote(
            symbol = symbol, name = werte[alpacaSymbol(symbol)]?.name.orEmpty(), price = preis,
            prevClose = vortag.zahl("c") ?: preis, open = tag.zahl("o") ?: preis,
            dayHigh = tag.zahl("h") ?: preis, dayLow = tag.zahl("l") ?: preis, volume = tag.zahl("v") ?: 0.0,
            currency = "USD", nativeCurrency = "USD", nativePrice = preis,
            exchange = if (symbol.endsWith("-USD")) "Alpaca Crypto" else "IEX", marketState = zustand,
            updatedAt = jetzt, regularPrice = preis, marketTime = BrokerJson.zeit(handel.text("t")) ?: jetzt,
        )
    }

    override suspend fun kerzen(symbol: String, range: Range): Series? {
        val jetzt = System.currentTimeMillis()
        val (zeitraum, tage) = when (range) {
            Range.D1 -> "5Min" to 4; Range.W1 -> "30Min" to 8; Range.M1 -> "1Hour" to 32; Range.M3 -> "1Day" to 95; Range.M6 -> "1Day" to 186
            Range.Y1 -> "1Day" to 370; Range.MAX -> "1Week" to 3660
        }
        val start = java.time.Instant.ofEpochMilli(jetzt - tage * 86_400_000L).toString()
        val a = alpacaSymbol(symbol)
        val url = if (symbol.endsWith("-USD")) "$daten/v1beta3/crypto/us/bars?symbols=${enc(a)}&timeframe=$zeitraum&start=${enc(start)}&limit=10000"
        else "$daten/v2/stocks/bars?symbols=${enc(a)}&timeframe=$zeitraum&start=${enc(start)}&limit=10000&feed=iex&adjustment=all"
        val liste = holen(url).obj()?.get("bars").obj()?.get(a).arr()
        var kerzen = liste.mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            Candle(BrokerJson.zeit(o.text("t")) ?: return@mapNotNull null, o.zahl("o") ?: 0.0, o.zahl("h") ?: 0.0, o.zahl("l") ?: 0.0, o.zahl("c") ?: return@mapNotNull null, o.zahl("v") ?: 0.0)
        }
        // Tageschart: nur der letzte Handelstag
        if (range == Range.D1 && kerzen.isNotEmpty()) {
            val letzter = kerzen.last().t
            kerzen = kerzen.filter { letzter - it.t < 20 * 3_600_000L }
        }
        return Series(kerzen)
    }

    override suspend fun suche(text: String): List<MarketApi.SearchHit> {
        val q = text.trim()
        if (q.isEmpty()) return emptyList()
        runCatching { werteLaden() }
        val gross = q.uppercase()
        val klein = q.lowercase()
        return werte.values.asSequence()
            .map { w ->
                val s = w.symbol.uppercase()
                val rang = when {
                    s == gross || s == "$gross/USD" -> 0
                    s.startsWith(gross) -> 1
                    w.name.lowercase().startsWith(klein) -> 2
                    w.name.lowercase().contains(klein) -> 3
                    else -> 9
                }
                rang to w
            }
            .filter { it.first < 9 }
            .sortedWith(compareBy({ it.first }, { it.second.symbol.length }))
            .take(30)
            .map { (_, w) -> MarketApi.SearchHit(appSymbol(w.symbol), w.name.ifBlank { w.symbol }, if (w.krypto) "CRYPTOCURRENCY" else "EQUITY", if (w.krypto) "Alpaca Crypto" else "Alpaca") }
            .toList()
    }
}
