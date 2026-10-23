package de.troetenrepublik.app.market.broker

import de.troetenrepublik.app.data.AssetType
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
import de.troetenrepublik.app.market.Symbols
import de.troetenrepublik.app.market.broker.BrokerJson.arr
import de.troetenrepublik.app.market.broker.BrokerJson.obj
import de.troetenrepublik.app.market.broker.BrokerJson.text
import de.troetenrepublik.app.market.broker.BrokerJson.zahl
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// Trading 212 (https://www.trading212.com, Doku https://docs.trading212.com): Aktien und ETFs im Invest-Konto, mit
// Demokonto. Die Schnittstelle ist in der Beta. Anmeldung mit Schluessel und Secret (HTTP Basic). Kurse und Charts
// liefert Trading 212 nicht; sie kommen weiter von Yahoo Finance. Deshalb ordnet die App die Kuerzel von Trading 212
// (AAPL_US_EQ, SAPd_EQ, VUSAl_EQ) den Yahoo-Kuerzeln zu: US-Werte direkt, sonst ueber die ISIN (Katalog der App,
// sonst die Yahoo-Suche nach der ISIN).
// Die Schnittstelle begrenzt die Abfragen streng (Kontostand etwa alle fuenf Sekunden, Instrumentenliste etwa einmal
// pro Minute); die App fragt deshalb sparsam und haelt die Instrumentenliste einen Tag lang.
@OptIn(ExperimentalEncodingApi::class)
class Trading212Broker(
    private val api: MarketApi,
    private val schluessel: String,
    private val geheim: String,
    override val live: Boolean,
) : Broker {
    private companion object {
        // Nur an diese Hosts gehen Anfragen mit dem Schluessel (siehe BrokerNetz).
        val HOSTS = setOf("live.trading212.com", "demo.trading212.com")
    }

    override val art = BrokerArt.TRADING212

    private val basis = if (live) "https://live.trading212.com/api/v0" else "https://demo.trading212.com/api/v0"
    private val kopf = mapOf("Authorization" to "Basic " + Base64.encode("${schluessel.trim()}:${geheim.trim()}".toByteArray(Charsets.UTF_8)))

    private data class Instrument(val ticker: String, val name: String, val isin: String, val waehrung: String, val etf: Boolean)

    @Volatile private var instrumente: Map<String, Instrument> = emptyMap()
    @Volatile private var nachIsin: Map<String, List<Instrument>> = emptyMap()
    @Volatile private var instrumenteZeit = 0L
    @Volatile private var kontoWaehrung = "EUR"

    // Gelernte Zuordnungen in beide Richtungen.
    private val zuApp = ConcurrentHashMap<String, String>()
    private val zuT212 = ConcurrentHashMap<String, String>()

    private val katalogIsin: Map<String, String> by lazy {
        Symbols.all.filter { it.isin.isNotBlank() }.associate { it.isin to it.symbol }
    }

    private suspend fun holen(pfad: String): JsonElement {
        val a = BrokerNetz.anfrage(basis + pfad, HOSTS, kopf, max = 30_000_000)
        if (!a.ok) throw fehler(a)
        return BrokerJson.lesen(a.text) ?: throw BrokerFehler("ABGELEHNT", a.text?.take(200))
    }

    private fun fehler(a: MarketApi.Antwort): BrokerFehler = when (a.code) {
        429 -> BrokerFehler("ABGELEHNT", "Trading 212: too many requests, please wait a moment")
        else -> BrokerJson.fehler(a)
    }

    private suspend fun instrumenteLaden() {
        if (instrumente.isNotEmpty() && System.currentTimeMillis() - instrumenteZeit < 24 * 3_600_000L) return
        val m = HashMap<String, Instrument>()
        for (e in holen("/equity/metadata/instruments").arr()) {
            val o = e.obj() ?: continue
            val t = o.text("ticker") ?: continue
            m[t] = Instrument(t, o.text("name") ?: o.text("shortName") ?: t, o.text("isin").orEmpty(), o.text("currencyCode") ?: "EUR", o.text("type") == "ETF")
        }
        if (m.isNotEmpty()) {
            instrumente = m
            nachIsin = m.values.filter { it.isin.isNotBlank() }.groupBy { it.isin }
            instrumenteZeit = System.currentTimeMillis()
        }
    }

    // Kuerzel

    // Ohne Netz bekannte Zuordnung: US-Werte direkt, sonst ueber den Katalog der App.
    private fun appSchnell(t: Instrument): String? {
        zuApp[t.ticker]?.let { return it }
        if (t.ticker.endsWith("_US_EQ")) return t.ticker.removeSuffix("_US_EQ").replace('.', '-')
        return katalogIsin[t.isin]
    }

    // Yahoo-Kuerzel zu einem Instrument; notfalls ueber die Yahoo-Suche nach der ISIN.
    private suspend fun appSymbol(t: Instrument): String? {
        appSchnell(t)?.let { merken(t.ticker, it); return it }
        if (t.isin.isBlank()) return null
        val treffer = runCatching { api.search(t.isin) }.getOrDefault(emptyList())
        val endung = when {
            t.ticker.endsWith("d_EQ") -> ".DE"; t.ticker.endsWith("l_EQ") -> ".L"; t.ticker.endsWith("p_EQ") -> ".PA"
            t.ticker.endsWith("a_EQ") -> ".AS"; t.waehrung == "EUR" -> ".DE"; else -> null
        }
        val s = (endung?.let { e -> treffer.firstOrNull { it.symbol.endsWith(e) } } ?: treffer.firstOrNull())?.symbol ?: return null
        merken(t.ticker, s)
        return s
    }

    private fun merken(t212: String, app: String) {
        zuApp[t212] = app
        zuT212.putIfAbsent(app, t212)
    }

    // Trading-212-Kuerzel zu einem App-Kuerzel; null, wenn es keins gibt.
    private fun t212(app: String): String? {
        zuT212[app]?.let { return it }
        val i = instrumente
        if (!app.contains('.') && !app.contains('^') && !app.contains('=')) i["${app.replace('-', '.')}_US_EQ"]?.let { return it.ticker }
        val isin = Symbols.find(app)?.isin?.takeIf { it.isNotBlank() } ?: return null
        val kandidaten = nachIsin[isin].orEmpty()
        val bevorzugt = when {
            app.endsWith(".DE") || app.endsWith(".F") -> kandidaten.firstOrNull { it.ticker.endsWith("d_EQ") }
            app.endsWith(".L") -> kandidaten.firstOrNull { it.ticker.endsWith("l_EQ") }
            else -> null
        } ?: kandidaten.firstOrNull { it.waehrung == "EUR" } ?: kandidaten.firstOrNull()
        return bevorzugt?.ticker?.also { merken(it, app) }
    }

    override fun fuehrt(symbol: String): Boolean = false

    override fun handelbar(symbol: String): Boolean = t212(symbol) != null

    override fun ordertypen(symbol: String): Set<OrderType> = setOf(OrderType.MARKET, OrderType.LIMIT, OrderType.STOP)

    override fun bruchstuecke(symbol: String): Boolean = true

    // Konto

    override suspend fun stand(): BrokerStand {
        instrumenteLaden()
        // Neue Schnittstelle (Kontouebersicht), sonst die aeltere (Bargeld und Kontodaten getrennt)
        val (waehrung, cash, nummer) = runCatching {
            val o = holen("/equity/account/summary").obj()
            val c = o?.get("cash").obj()
            Triple(o.text("currency") ?: o.text("currencyCode") ?: "EUR", c.zahl("availableToTrade") ?: c.zahl("free") ?: 0.0, o.text("id").orEmpty())
        }.getOrElse {
            val info = holen("/equity/account/info").obj()
            val c = holen("/equity/account/cash").obj()
            Triple(info.text("currencyCode") ?: "EUR", c.zahl("free") ?: 0.0, info.text("id").orEmpty())
        }
        kontoWaehrung = waehrung

        val roh = runCatching { holen("/equity/positions").arr() }.getOrElse { holen("/equity/portfolio").arr() }
        val positionen = roh.mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val inst = o["instrument"].obj()
            val t = o.text("ticker") ?: inst.text("ticker") ?: return@mapNotNull null
            val i = instrumente[t] ?: Instrument(t, inst.text("name") ?: t, inst.text("isin").orEmpty(), inst.text("currency") ?: "EUR", false)
            val menge = o.zahl("quantity") ?: return@mapNotNull null
            if (menge == 0.0) return@mapNotNull null
            val app = appSymbol(i) ?: t
            // Einstand moeglichst in Kontowaehrung (walletImpact), sonst in der Waehrung des Werts
            val kostenKonto = o["walletImpact"].obj().zahl("totalCost")
            val (einstand, w) = if (kostenKonto != null) kostenKonto / menge to waehrung
            else (o.zahl("averagePricePaid") ?: o.zahl("averagePrice") ?: 0.0) to i.waehrung
            BrokerPosition(app, i.name, menge, einstand, w, if (i.etf) AssetType.ETF else AssetType.STOCK)
        }

        val offen = runCatching { holen("/equity/orders").arr().mapNotNull { e -> order(e.obj(), offen = true) } }.getOrDefault(emptyList())
        val verlauf = runCatching { holen("/equity/history/orders?limit=50").obj()?.get("items").arr() }.getOrNull().orEmpty()
        val erledigt = verlauf.mapNotNull { e -> order(e.obj(), offen = false) }
        val umsaetze = ArrayList<BrokerUmsatz>()
        for (e in verlauf) umsatzAusOrder(e.obj())?.let { umsaetze += it }
        runCatching { holen("/equity/history/transactions?limit=50").obj()?.get("items").arr() }.getOrNull().orEmpty().forEach { e ->
            val o = e.obj() ?: return@forEach
            val betrag = o.zahl("amount") ?: return@forEach
            val typ = when (o.text("type")) { "DEPOSIT" -> TxType.DEPOSIT; "WITHDRAW", "WITHDRAWAL" -> TxType.WITHDRAWAL; "FEE" -> TxType.FEE; else -> if (betrag >= 0) TxType.DEPOSIT else TxType.WITHDRAWAL }
            umsaetze += BrokerUmsatz(o.text("reference") ?: "t${o.text("dateTime")}", BrokerJson.zeit(o.text("dateTime")) ?: 0L, typ, null, 0.0, 0.0, betrag, o.text("currency") ?: waehrung)
        }
        runCatching { holen("/equity/history/dividends?limit=50").obj()?.get("items").arr() }.getOrNull().orEmpty().forEach { e ->
            val o = e.obj() ?: return@forEach
            val t = o.text("ticker")
            umsaetze += BrokerUmsatz(o.text("reference") ?: "d${o.text("paidOn")}$t", BrokerJson.zeit(o.text("paidOn")) ?: 0L, TxType.DIVIDEND,
                t?.let { instrumente[it]?.let { i -> appSchnell(i) } ?: zuApp[it] }, o.zahl("quantity") ?: 0.0, o.zahl("grossAmountPerShare") ?: 0.0,
                o.zahl("amount") ?: 0.0, o.text("currency") ?: waehrung)
        }
        return BrokerStand(waehrung, cash, cash, positionen, (offen + erledigt).distinctBy { it.id }, umsaetze, nummer)
    }

    private suspend fun order(o: JsonObject?, offen: Boolean): BrokerOrder? {
        o ?: return null
        val ord = o["order"].obj() ?: o
        val fill = o["fill"].obj()
        val t = ord.text("ticker") ?: ord["instrument"].obj().text("ticker") ?: return null
        val i = instrumente[t]
        val app = i?.let { appSymbol(it) } ?: zuApp[t] ?: t
        val menge = ord.zahl("quantity") ?: ord.zahl("orderedQuantity") ?: ord.zahl("filledQuantity") ?: 0.0
        val seite = when { ord.text("side") == "SELL" || menge < 0 -> Side.SELL; else -> Side.BUY }
        val typ = when (ord.text("type")) { "LIMIT" -> OrderType.LIMIT; "STOP", "STOP_LIMIT" -> OrderType.STOP; else -> OrderType.MARKET }
        val status = when (ord.text("status")) {
            "FILLED" -> OrderStatus.FILLED; "CANCELLED", "CANCELLING" -> OrderStatus.CANCELLED; "REJECTED" -> OrderStatus.REJECTED
            else -> if (offen) OrderStatus.OPEN else OrderStatus.CANCELLED
        }
        val erstellt = BrokerJson.zeit(ord.text("createdAt") ?: ord.text("creationTime") ?: ord.text("dateCreated")) ?: 0L
        return BrokerOrder(
            id = ord.text("id") ?: return null, symbol = app, name = i?.name ?: app, seite = seite, typ = typ,
            menge = kotlin.math.abs(menge), limit = ord.zahl("limitPrice"), stop = ord.zahl("stopPrice"), trailPct = null, status = status,
            erstellt = erstellt,
            ausgefuehrt = (BrokerJson.zeit(fill.text("filledAt")) ?: BrokerJson.zeit(ord.text("dateExecuted"))).takeIf { status == OrderStatus.FILLED },
            kurs = fill.zahl("price") ?: ord.zahl("fillPrice"), waehrung = i?.waehrung ?: kontoWaehrung,
            gueltigkeit = if (ord.text("timeValidity") == "GOOD_TILL_CANCEL") Validity.GTC else Validity.DAY,
            ausgefuehrteMenge = kotlin.math.abs(fill.zahl("quantity") ?: ord.zahl("filledQuantity") ?: 0.0),
        )
    }

    private fun umsatzAusOrder(o: JsonObject?): BrokerUmsatz? {
        o ?: return null
        val ord = o["order"].obj() ?: o
        val fill = o["fill"].obj()
        if ((ord.text("status") ?: "FILLED") != "FILLED") return null
        val t = ord.text("ticker") ?: ord["instrument"].obj().text("ticker") ?: return null
        val i = instrumente[t]
        val menge = fill.zahl("quantity") ?: ord.zahl("filledQuantity") ?: return null
        val kurs = fill.zahl("price") ?: ord.zahl("fillPrice") ?: return null
        val verkauf = ord.text("side") == "SELL" || menge < 0
        val zeit = BrokerJson.zeit(fill.text("filledAt") ?: ord.text("dateExecuted") ?: ord.text("dateModified")) ?: return null
        val w = i?.waehrung ?: kontoWaehrung
        val m = kotlin.math.abs(menge)
        return BrokerUmsatz("f" + (fill.text("id") ?: ord.text("fillId") ?: ord.text("id")), zeit, if (verkauf) TxType.SELL else TxType.BUY,
            i?.let { appSchnell(it) } ?: zuApp[t] ?: t, m, kurs, if (verkauf) m * kurs else -m * kurs, w)
    }

    override suspend fun order(auftrag: Auftrag): String {
        instrumenteLaden()
        val t = t212(auftrag.symbol) ?: throw BrokerFehler("ABGELEHNT", auftrag.symbol)
        val menge = if (auftrag.seite == Side.SELL) -auftrag.menge else auftrag.menge
        val gueltig = if (auftrag.gueltigkeit == Validity.GTC) "GOOD_TILL_CANCEL" else "DAY"
        val (pfad, koerper) = when (auftrag.typ) {
            OrderType.LIMIT -> "limit" to buildJsonObject { put("ticker", t); put("quantity", menge); put("limitPrice", auftrag.preis ?: 0.0); put("timeValidity", gueltig) }
            OrderType.STOP -> "stop" to buildJsonObject { put("ticker", t); put("quantity", menge); put("stopPrice", auftrag.preis ?: 0.0); put("timeValidity", gueltig) }
            else -> "market" to buildJsonObject { put("ticker", t); put("quantity", menge) }
        }
        val a = BrokerNetz.anfrage("$basis/equity/orders/$pfad", HOSTS, kopf, methode = "POST", json = koerper.toString())
        if (!a.ok) throw fehler(a)
        return BrokerJson.lesen(a.text).obj().text("id").orEmpty()
    }

    override suspend fun stornieren(id: String) {
        val a = BrokerNetz.anfrage("$basis/equity/orders/${URLEncoder.encode(id, "UTF-8")}", HOSTS, kopf, methode = "DELETE")
        if (a.code !in 200..299) throw fehler(a)
    }

    // Kurse kommen von Yahoo

    override suspend fun kurse(symbole: List<String>): Map<String, Quote> = emptyMap()

    override suspend fun kerzen(symbol: String, range: Range): Series? = null

    override suspend fun suche(text: String): List<MarketApi.SearchHit> {
        val q = text.trim()
        if (q.isEmpty()) return emptyList()
        instrumenteLaden()
        val gross = q.uppercase()
        val klein = q.lowercase()
        val auswahl = instrumente.values.asSequence()
            .map { i ->
                val kurz = i.ticker.substringBefore('_').uppercase()
                val rang = when {
                    i.isin.equals(gross, true) || kurz == gross -> 0
                    kurz.startsWith(gross) -> 1
                    i.name.lowercase().startsWith(klein) -> 2
                    i.name.lowercase().contains(klein) -> 3
                    else -> 9
                }
                rang to i
            }
            .filter { it.first < 9 }
            .sortedWith(compareBy({ it.first }, { it.second.waehrung != kontoWaehrung }, { it.second.name.length }))
            .take(20).map { it.second }.toList()
        // Yahoo-Kuerzel zuordnen; was sich nicht zuordnen laesst, hat keine Kurse und bleibt weg
        val treffer = coroutineScope { auswahl.map { i -> async { appSymbol(i)?.let { s -> i to s } } }.awaitAll() }.filterNotNull()
        return treffer.distinctBy { it.second }.map { (i, s) ->
            MarketApi.SearchHit(s, i.name, if (i.etf) "ETF" else "EQUITY", "Trading 212 · " + i.ticker.substringBefore('_') + " · " + i.waehrung)
        }
    }
}
