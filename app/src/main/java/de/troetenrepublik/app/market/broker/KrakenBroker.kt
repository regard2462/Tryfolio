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
import de.troetenrepublik.app.plattform.Signatur
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// Kraken (https://www.kraken.com, Doku https://docs.kraken.com/api/): Kryptowerte gegen Euro oder Dollar. Private
// Abfragen werden signiert: API-Sign = Base64(HMAC-SHA512(Base64-dekodiertes Secret, Pfad + SHA256(nonce + Formular))).
// Kraken hat kein Papierkonto. Die Kontowaehrung ist Euro, wenn das Konto Euro haelt, sonst Dollar; Werte heissen in
// der App wie bei Yahoo (BTC-EUR). Einstandskurse liefert Kraken nicht, die App rechnet sie aus den letzten Trades.
@OptIn(ExperimentalEncodingApi::class)
class KrakenBroker(
    private val api: MarketApi,
    private val schluessel: String,
    private val geheim: String,
) : Broker {
    private companion object {
        // Nur an diese Hosts gehen Anfragen mit dem Schluessel (siehe BrokerNetz).
        val HOSTS = setOf("api.kraken.com")
    }

    override val art = BrokerArt.KRAKEN
    override val live = true

    private val basis = "https://api.kraken.com"
    private val nonce = AtomicLong(System.currentTimeMillis() * 1000)

    // Handelspaar bei Kraken: Schluessel (XXBTZEUR), Kurzname fuer Orders (XBTEUR), Basis und Gegenwaehrung (BTC, EUR).
    private data class Paar(val key: String, val alt: String, val basis: String, val gegen: String, val nachkomma: Int)

    @Volatile private var paare: Map<String, Paar> = emptyMap()          // App-Symbol (BTC-EUR) -> Paar
    @Volatile private var paarKeys: Map<String, Paar> = emptyMap()       // XXBTZEUR / XBTEUR -> Paar
    @Volatile private var assets: Map<String, String> = emptyMap()       // XXBT -> BTC
    @Volatile private var paareZeit = 0L
    @Volatile private var waehrung = "EUR"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    // Kraken-Kuerzel in das uebliche: XBT -> BTC, XDG -> DOGE, ZEUR -> EUR.
    private fun ueblich(k: String): String {
        assets[k]?.let { return it }
        val kurz = if (k.length == 4 && (k.startsWith("X") || k.startsWith("Z"))) k.drop(1) else k
        return when (kurz) { "XBT" -> "BTC"; "XDG" -> "DOGE"; else -> kurz }
    }

    private fun ergebnis(a: MarketApi.Antwort): JsonElement {
        if (a.code == 0) throw BrokerFehler("NETZ")
        val o = BrokerJson.lesen(a.text).obj() ?: throw BrokerJson.fehler(a)
        val fehler = o["error"].arr().mapNotNull { (it as? JsonPrimitive)?.content }
        if (fehler.isNotEmpty()) {
            val text = fehler.joinToString("; ")
            throw if (fehler.any { it.startsWith("EAPI:Invalid key") || it.startsWith("EAPI:Invalid signature") || it.startsWith("EGeneral:Permission") })
                BrokerFehler("SCHLUESSEL", text) else BrokerFehler("ABGELEHNT", text)
        }
        return o["result"] ?: JsonObject(emptyMap())
    }

    private suspend fun oeffentlich(pfad: String): JsonElement = ergebnis(BrokerNetz.anfrage(basis + pfad, HOSTS))

    private suspend fun privat(methode: String, felder: List<Pair<String, String>> = emptyList()): JsonElement {
        val pfad = "/0/private/$methode"
        val n = nonce.updateAndGet { maxOf(it + 1, System.currentTimeMillis() * 1000) }.toString()
        val form = (listOf("nonce" to n) + felder).joinToString("&") { (k, v) -> enc(k) + "=" + enc(v) }
        val sha = Signatur.sha256((n + form).toByteArray(Charsets.UTF_8))
        val sig = Signatur.hmac("HmacSHA512", Base64.decode(geheim.trim()), pfad.toByteArray(Charsets.UTF_8) + sha)
        val kopf = mapOf("API-Key" to schluessel.trim(), "API-Sign" to Base64.encode(sig))
        return ergebnis(BrokerNetz.anfrage(basis + pfad, HOSTS, kopf, methode = "POST", formText = form))
    }

    private suspend fun paareLaden() {
        if (paare.isNotEmpty() && System.currentTimeMillis() - paareZeit < 24 * 3_600_000L) return
        assets = runCatching {
            oeffentlich("/0/public/Assets").obj().orEmpty().mapNotNull { (k, v) -> v.obj().text("altname")?.let { k to (if (it == "XBT") "BTC" else if (it == "XDG") "DOGE" else it) } }.toMap()
        }.getOrDefault(assets)
        val p = HashMap<String, Paar>()
        val k = HashMap<String, Paar>()
        for ((key, v) in oeffentlich("/0/public/AssetPairs").obj().orEmpty()) {
            val o = v.obj() ?: continue
            if (o.text("status") != null && o.text("status") != "online") continue
            val gegen = ueblich(o.text("quote") ?: continue)
            if (gegen != "EUR" && gegen != "USD") continue
            val b = ueblich(o.text("base") ?: continue)
            val paar = Paar(key, o.text("altname") ?: key, b, gegen, o.zahl("pair_decimals")?.toInt() ?: 2)
            p["$b-$gegen"] = paar
            k[key] = paar
            k[paar.alt] = paar
        }
        if (p.isNotEmpty()) { paare = p; paarKeys = k; paareZeit = System.currentTimeMillis() }
    }

    override fun fuehrt(symbol: String): Boolean {
        val p = paare
        if (p.isNotEmpty()) return p.containsKey(symbol)
        return symbol.endsWith("-EUR") || symbol.endsWith("-USD")
    }

    override fun ordertypen(symbol: String): Set<OrderType> = setOf(OrderType.MARKET, OrderType.LIMIT, OrderType.STOP, OrderType.TRAILING_STOP)

    override fun bruchstuecke(symbol: String): Boolean = true

    override suspend fun stand(): BrokerStand {
        paareLaden()
        val saldo = privat("Balance").obj().orEmpty().mapNotNull { (k, v) ->
            (v as? JsonPrimitive)?.content?.toDoubleOrNull()?.let { ueblich(k.substringBefore('.')) to it }
        }.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
        waehrung = if ((saldo["EUR"] ?: 0.0) > 0 || (saldo["USD"] ?: 0.0) <= 0) "EUR" else "USD"
        val cash = saldo[waehrung] ?: 0.0
        val gehalten = saldo.filter { (a, m) -> a != "EUR" && a != "USD" && m > 1e-10 && paare.containsKey("$a-$waehrung") }

        // Trades der letzten Zeit: fuer Umsaetze und den Einstand
        val trades = runCatching { privat("TradesHistory").obj()?.get("trades").obj().orEmpty() }.getOrDefault(emptyMap())
        val umsaetze = ArrayList<BrokerUmsatz>()
        val kosten = HashMap<String, Pair<Double, Double>>() // Asset -> Menge, Kosten
        for ((id, v) in trades.entries.sortedBy { it.value.obj().zahl("time") ?: 0.0 }) {
            val o = v.obj() ?: continue
            val paar = paarKeys[o.text("pair") ?: continue] ?: continue
            val menge = o.zahl("vol") ?: continue
            val kurs = o.zahl("price") ?: continue
            val kauf = o.text("type") == "buy"
            val gebuehr = o.zahl("fee") ?: 0.0
            val betrag = o.zahl("cost") ?: (menge * kurs)
            umsaetze += BrokerUmsatz(id, ((o.zahl("time") ?: 0.0) * 1000).toLong(), if (kauf) TxType.BUY else TxType.SELL,
                "${paar.basis}-${paar.gegen}", menge, kurs, if (kauf) -betrag - gebuehr else betrag - gebuehr, paar.gegen, gebuehr)
            if (paar.gegen == waehrung) {
                val (m, k) = kosten[paar.basis] ?: (0.0 to 0.0)
                kosten[paar.basis] = if (kauf) (m + menge) to (k + betrag + gebuehr)
                else if (m > 0) (m - menge).coerceAtLeast(0.0) to k * ((m - menge).coerceAtLeast(0.0) / m) else 0.0 to 0.0
            }
        }
        // Ein- und Auszahlungen
        runCatching { privat("Ledgers").obj()?.get("ledger").obj().orEmpty() }.getOrDefault(emptyMap()).forEach { (id, v) ->
            val o = v.obj() ?: return@forEach
            val typ = when (o.text("type")) { "deposit" -> TxType.DEPOSIT; "withdrawal" -> TxType.WITHDRAWAL; "staking", "earn" -> TxType.INTEREST; else -> return@forEach }
            val asset = ueblich(o.text("asset")?.substringBefore('.') ?: return@forEach)
            val menge = o.zahl("amount") ?: 0.0
            val zeit = ((o.zahl("time") ?: 0.0) * 1000).toLong()
            if (asset == "EUR" || asset == "USD") umsaetze += BrokerUmsatz(id, zeit, typ, null, 0.0, 0.0, menge, asset, o.zahl("fee") ?: 0.0)
            else umsaetze += BrokerUmsatz(id, zeit, typ, "$asset-$waehrung", kotlin.math.abs(menge), 0.0, 0.0, waehrung, text = asset)
        }

        // Fehlender Einstand (Einzahlung von Krypto, aeltere Kaeufe): aktueller Kurs
        val kurse = runCatching { kurse(gehalten.keys.map { "$it-$waehrung" }) }.getOrDefault(emptyMap())
        val positionen = gehalten.map { (a, menge) ->
            val s = "$a-$waehrung"
            val (m, k) = kosten[a] ?: (0.0 to 0.0)
            val jetzt = kurse[s]?.price ?: 0.0
            val bekannt = minOf(m, menge)
            val einstand = if (menge > 0) (bekannt * (if (m > 0) k / m else jetzt) + (menge - bekannt) * jetzt) / menge else 0.0
            BrokerPosition(s, a, menge, einstand, waehrung, AssetType.CRYPTO)
        }

        val orders = ArrayList<BrokerOrder>()
        runCatching { privat("OpenOrders").obj()?.get("open").obj().orEmpty() }.getOrDefault(emptyMap()).forEach { (id, v) -> order(id, v.obj())?.let { orders += it } }
        runCatching { privat("ClosedOrders").obj()?.get("closed").obj().orEmpty() }.getOrDefault(emptyMap()).forEach { (id, v) -> order(id, v.obj())?.let { orders += it } }
        return BrokerStand(waehrung, cash, cash, positionen, orders.sortedByDescending { it.erstellt }, umsaetze, "Kraken")
    }

    private fun order(id: String, o: JsonObject?): BrokerOrder? {
        o ?: return null
        val d = o["descr"].obj()
        val paar = paarKeys[d.text("pair") ?: return null] ?: return null
        val typ = when (d.text("ordertype")) { "limit" -> OrderType.LIMIT; "stop-loss" -> OrderType.STOP; "trailing-stop" -> OrderType.TRAILING_STOP; else -> OrderType.MARKET }
        val status = when (o.text("status")) { "closed" -> OrderStatus.FILLED; "canceled" -> OrderStatus.CANCELLED; "expired" -> OrderStatus.EXPIRED; else -> OrderStatus.OPEN }
        val preis = d.text("price")?.trimStart('+', '-')?.removeSuffix("%")?.toDoubleOrNull()
        return BrokerOrder(
            id = id, symbol = "${paar.basis}-${paar.gegen}", name = paar.basis,
            seite = if (d.text("type") == "sell") Side.SELL else Side.BUY, typ = typ, menge = o.zahl("vol") ?: 0.0,
            limit = preis.takeIf { typ == OrderType.LIMIT }, stop = preis.takeIf { typ == OrderType.STOP },
            trailPct = preis.takeIf { typ == OrderType.TRAILING_STOP && d.text("price")?.endsWith("%") == true },
            status = status, erstellt = ((o.zahl("opentm") ?: 0.0) * 1000).toLong(),
            ausgefuehrt = o.zahl("closetm")?.let { (it * 1000).toLong() }?.takeIf { status == OrderStatus.FILLED },
            kurs = o.zahl("price")?.takeIf { it > 0 }, waehrung = paar.gegen, gueltigkeit = Validity.GTC,
            ausgefuehrteMenge = o.zahl("vol_exec") ?: 0.0,
        )
    }

    override suspend fun order(auftrag: Auftrag): String {
        paareLaden()
        val paar = paare[auftrag.symbol] ?: throw BrokerFehler("ABGELEHNT", auftrag.symbol)
        fun preis(v: Double) = "%.${paar.nachkomma}f".format(java.util.Locale.ROOT, v)
        val felder = buildList {
            add("pair" to paar.alt)
            add("type" to if (auftrag.seite == Side.SELL) "sell" else "buy")
            add("ordertype" to when (auftrag.typ) { OrderType.LIMIT -> "limit"; OrderType.STOP -> "stop-loss"; OrderType.TRAILING_STOP -> "trailing-stop"; else -> "market" })
            add("volume" to "%.8f".format(java.util.Locale.ROOT, auftrag.menge))
            when (auftrag.typ) {
                OrderType.LIMIT, OrderType.STOP -> add("price" to preis(auftrag.preis ?: throw BrokerFehler("ABGELEHNT", "price")))
                OrderType.TRAILING_STOP -> add("price" to "+" + "%.2f".format(java.util.Locale.ROOT, auftrag.trailPct ?: 1.0) + "%")
                else -> {}
            }
        }
        return privat("AddOrder", felder).obj()?.get("txid").arr().firstOrNull()?.let { (it as? JsonPrimitive)?.content }.orEmpty()
    }

    // AmendOrder: Menge und Preise einer offenen Order; die Gueltigkeit laesst Kraken dabei nicht aendern
    override fun aenderbar(symbol: String) = Aenderbar(menge = true, preis = true, gueltigkeit = false)

    override suspend fun aendern(id: String, auftrag: Auftrag) {
        paareLaden()
        val paar = paare[auftrag.symbol] ?: throw BrokerFehler("ABGELEHNT", auftrag.symbol)
        fun preis(v: Double) = "%.${paar.nachkomma}f".format(java.util.Locale.ROOT, v)
        privat("AmendOrder", buildList {
            add("txid" to id)
            add("order_qty" to "%.8f".format(java.util.Locale.ROOT, auftrag.menge))
            when (auftrag.typ) {
                OrderType.LIMIT -> add("limit_price" to preis(auftrag.preis ?: throw BrokerFehler("ABGELEHNT", "price")))
                OrderType.STOP -> add("trigger_price" to preis(auftrag.preis ?: throw BrokerFehler("ABGELEHNT", "price")))
                OrderType.TRAILING_STOP -> add("trigger_price" to "+" + "%.2f".format(java.util.Locale.ROOT, auftrag.trailPct ?: 1.0) + "%")
                else -> {}
            }
        })
    }

    override suspend fun stornieren(id: String) {
        privat("CancelOrder", listOf("txid" to id))
    }

    override suspend fun kurse(symbole: List<String>): Map<String, Quote> {
        paareLaden()
        val gesucht = symbole.mapNotNull { s -> paare[s]?.let { s to it } }
        if (gesucht.isEmpty()) return emptyMap()
        val jetzt = System.currentTimeMillis()
        val aus = HashMap<String, Quote>()
        for (teil in gesucht.chunked(40)) {
            val r = oeffentlich("/0/public/Ticker?pair=" + teil.joinToString(",") { it.second.alt }).obj().orEmpty()
            for ((s, paar) in teil) {
                val o = (r[paar.key] ?: r[paar.alt]).obj() ?: continue
                fun erster(k: String) = (o[k].arr().firstOrNull() as? JsonPrimitive)?.content?.toDoubleOrNull()
                val preis = erster("c") ?: continue
                val eroeffnung = (o["o"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: preis
                aus[s] = Quote(
                    symbol = s, name = paar.basis, price = preis, prevClose = eroeffnung, open = eroeffnung,
                    dayHigh = erster("h") ?: preis, dayLow = erster("l") ?: preis, volume = erster("v") ?: 0.0,
                    currency = paar.gegen, nativeCurrency = paar.gegen, nativePrice = preis, exchange = "Kraken",
                    marketState = "REGULAR", updatedAt = jetzt, regularPrice = preis, marketTime = jetzt,
                )
            }
        }
        return aus
    }

    override suspend fun kerzen(symbol: String, range: Range): Series? {
        paareLaden()
        val paar = paare[symbol] ?: return null
        // Kraken liefert hoechstens 720 Kerzen je Abfrage
        val (minuten, tage) = when (range) { Range.D1 -> 5 to 1; Range.W1 -> 15 to 7; Range.M1 -> 60 to 30; Range.M3 -> 240 to 92; Range.M6 -> 1440 to 184; Range.Y1 -> 1440 to 366; Range.MAX -> 10080 to 3650 }
        val seit = System.currentTimeMillis() / 1000 - tage * 86_400L
        val r = oeffentlich("/0/public/OHLC?pair=${paar.alt}&interval=$minuten&since=$seit").obj().orEmpty()
        val liste = (r[paar.key] ?: r[paar.alt] ?: r.entries.firstOrNull { it.key != "last" }?.value) as? JsonArray ?: return Series(emptyList())
        return Series(liste.mapNotNull { e ->
            val a = e as? JsonArray ?: return@mapNotNull null
            fun z(i: Int) = (a.getOrNull(i) as? JsonPrimitive)?.content?.toDoubleOrNull()
            Candle(((z(0) ?: return@mapNotNull null) * 1000).toLong(), z(1) ?: 0.0, z(2) ?: 0.0, z(3) ?: 0.0, z(4) ?: return@mapNotNull null, z(6) ?: 0.0)
        })
    }

    override suspend fun suche(text: String): List<MarketApi.SearchHit> {
        val q = text.trim().uppercase()
        if (q.isEmpty()) return emptyList()
        paareLaden()
        val bevorzugt = waehrung
        return paare.entries.asSequence()
            .filter { (s, p) -> p.basis.startsWith(q) || s.startsWith(q) || p.alt.startsWith(q) }
            .sortedWith(compareBy({ it.value.basis != q }, { it.value.gegen != bevorzugt }, { it.key.length }))
            .take(30)
            .map { (s, p) -> MarketApi.SearchHit(s, "${p.basis} in ${p.gegen}", "CRYPTOCURRENCY", "Kraken") }
            .toList()
    }
}
