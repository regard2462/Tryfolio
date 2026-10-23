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
import de.troetenrepublik.app.market.broker.BrokerJson.arr
import de.troetenrepublik.app.market.broker.BrokerJson.obj
import de.troetenrepublik.app.market.broker.BrokerJson.text
import de.troetenrepublik.app.market.broker.BrokerJson.zahl
import de.troetenrepublik.app.plattform.BrokerNetz
import de.troetenrepublik.app.plattform.Signatur
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// Robinhood Crypto Trading API (https://docs.robinhood.com/crypto/trading/): Kryptowerte gegen Dollar, nur fuer
// Kunden in den USA. Fuer Aktien gibt es keine offizielle Schnittstelle. Jede Anfrage wird mit Ed25519 signiert,
// Nachricht = API-Schluessel + Zeitstempel in Sekunden + Pfad mit Abfrage + Methode + Koerper. Kopfzeilen: x-api-key,
// x-timestamp, x-signature (Base64). Den privaten Schluessel erzeugt der Nutzer, im Portal liegt nur der oeffentliche.
// Kurse und Charts kommen von Yahoo, da Robinhood nur Geld- und Briefkurse ohne Vortagesschluss und Verlauf liefert.
// Einstandskurse rechnet die App aus den ausgefuehrten Orders.
@OptIn(ExperimentalEncodingApi::class)
class RobinhoodBroker(
    private val schluessel: String,
    privat: String,
) : Broker {
    private companion object {
        // Nur an diese Hosts gehen Anfragen mit dem Schluessel, siehe BrokerNetz.
        val HOSTS = setOf("trading.robinhood.com")
        const val BASIS = "https://trading.robinhood.com"
    }

    override val art = BrokerArt.ROBINHOOD
    override val live = true

    // Privater Schluessel: 32 Byte. Manche Werkzeuge speichern 64, Schluessel und oeffentlichen Teil.
    private val seed: ByteArray = runCatching { Base64.decode(privat.trim()) }.getOrDefault(ByteArray(0)).let { if (it.size == 64) it.copyOf(32) else it }

    @Volatile private var paare: Map<String, JsonObject> = emptyMap()

    private suspend fun anfrage(pfad: String, methode: String = "GET", koerper: String? = null): kotlinx.serialization.json.JsonElement? {
        if (seed.size != 32) throw BrokerFehler("SCHLUESSEL", "private key must be 32 bytes (Base64)")
        val zeit = (System.currentTimeMillis() / 1000).toString()
        val nachricht = schluessel.trim() + zeit + pfad + methode + (koerper ?: "")
        val sig = Base64.encode(Signatur.ed25519(seed, nachricht.toByteArray(Charsets.UTF_8)))
        val kopf = mapOf("x-api-key" to schluessel.trim(), "x-timestamp" to zeit, "x-signature" to sig)
        val a = BrokerNetz.anfrage(BASIS + pfad, HOSTS, kopf, methode = methode, json = koerper)
        if (a.code !in 200..299) throw BrokerJson.fehler(a)
        return BrokerJson.lesen(a.text)
    }

    // Folgt den Seiten ueber results und next.
    private suspend fun alle(pfad: String, seiten: Int = 5): List<JsonObject> {
        val aus = ArrayList<JsonObject>()
        var p: String? = pfad
        var n = 0
        while (p != null && n++ < seiten) {
            val o = anfrage(p).obj()
            o?.get("results").arr().mapNotNullTo(aus) { it.obj() }
            p = o.text("next")?.removePrefix(BASIS)?.takeIf { it.startsWith("/") }
        }
        return aus
    }

    private suspend fun paareLaden() {
        if (paare.isNotEmpty()) return
        paare = alle("/api/v1/crypto/trading/trading_pairs/", 10).filter { it.text("status") == null || it.text("status") == "tradable" }
            .mapNotNull { o -> o.text("symbol")?.let { it to o } }.toMap()
    }

    override fun fuehrt(symbol: String): Boolean = false

    override fun handelbar(symbol: String): Boolean = paare[symbol] != null || (paare.isEmpty() && symbol.endsWith("-USD"))

    override fun ordertypen(symbol: String): Set<OrderType> = setOf(OrderType.MARKET, OrderType.LIMIT, OrderType.STOP)

    override fun bruchstuecke(symbol: String): Boolean = true

    override suspend fun stand(): BrokerStand {
        val konto = anfrage("/api/v1/crypto/trading/accounts/").obj()
        runCatching { paareLaden() }
        val cash = konto.zahl("buying_power") ?: 0.0
        val roh = alle("/api/v1/crypto/trading/orders/")
        // Einstand aus den ausgefuehrten Orders, aelteste zuerst.
        val kosten = HashMap<String, Pair<Double, Double>>()
        val umsaetze = ArrayList<BrokerUmsatz>()
        for (o in roh.sortedBy { BrokerJson.zeit(it.text("created_at")) ?: 0L }) {
            val s = o.text("symbol") ?: continue
            val asset = s.substringBefore('-')
            val kauf = o.text("side") == "buy"
            for ((i, e) in o["executions"].arr().withIndex()) {
                val x = e.obj() ?: continue
                val m = x.zahl("quantity") ?: continue
                val k = x.zahl("effective_price") ?: continue
                val (alt, wert) = kosten[asset] ?: (0.0 to 0.0)
                kosten[asset] = if (kauf) (alt + m) to (wert + m * k) else if (alt > 0) (alt - m).coerceAtLeast(0.0) to wert * ((alt - m).coerceAtLeast(0.0) / alt) else 0.0 to 0.0
                umsaetze += BrokerUmsatz("${o.text("id")}-$i", BrokerJson.zeit(x.text("timestamp")) ?: 0L, if (kauf) TxType.BUY else TxType.SELL,
                    s, m, k, if (kauf) -m * k else m * k, "USD")
            }
        }
        val positionen = alle("/api/v1/crypto/trading/holdings/").mapNotNull { h ->
            val a = h.text("asset_code") ?: return@mapNotNull null
            val menge = h.zahl("total_quantity") ?: return@mapNotNull null
            if (menge <= 1e-12) return@mapNotNull null
            val (m, k) = kosten[a] ?: (0.0 to 0.0)
            BrokerPosition("$a-USD", a, menge, if (m > 0) k / m else 0.0, "USD", AssetType.CRYPTO)
        }
        val orders = roh.mapNotNull { order(it) }
        return BrokerStand("USD", cash, cash, positionen, orders, umsaetze, konto.text("account_number").orEmpty())
    }

    private fun order(o: JsonObject): BrokerOrder? {
        val s = o.text("symbol") ?: return null
        val typ = when (o.text("type")) { "limit" -> OrderType.LIMIT; "stop_loss", "stop_limit" -> OrderType.STOP; else -> OrderType.MARKET }
        val cfg = (o["limit_order_config"] ?: o["stop_loss_order_config"] ?: o["stop_limit_order_config"] ?: o["market_order_config"]).obj()
        val status = when (o.text("state")) {
            "filled" -> OrderStatus.FILLED; "canceled" -> OrderStatus.CANCELLED; "failed" -> OrderStatus.REJECTED; else -> OrderStatus.OPEN
        }
        return BrokerOrder(
            id = o.text("id") ?: return null, symbol = s, name = s.substringBefore('-'),
            seite = if (o.text("side") == "sell") Side.SELL else Side.BUY, typ = typ,
            menge = cfg.zahl("asset_quantity") ?: o.zahl("filled_asset_quantity") ?: 0.0,
            limit = cfg.zahl("limit_price"), stop = cfg.zahl("stop_price"), trailPct = null, status = status,
            erstellt = BrokerJson.zeit(o.text("created_at")) ?: 0L,
            ausgefuehrt = BrokerJson.zeit(o.text("updated_at")).takeIf { status == OrderStatus.FILLED },
            kurs = o.zahl("average_price"), waehrung = "USD",
            gueltigkeit = if (cfg.text("time_in_force") == "gfd") Validity.DAY else Validity.GTC,
            ausgefuehrteMenge = o.zahl("filled_asset_quantity") ?: 0.0,
        )
    }

    // Robinhood verlangt eine UUID Version 4 als client_order_id.
    private fun uuid(): String {
        val b = kotlin.random.Random.nextBytes(16)
        b[6] = ((b[6].toInt() and 0x0f) or 0x40).toByte()
        b[8] = ((b[8].toInt() and 0x3f) or 0x80).toByte()
        val h = b.joinToString("") { "%02x".format(it) }
        return "${h.substring(0, 8)}-${h.substring(8, 12)}-${h.substring(12, 16)}-${h.substring(16, 20)}-${h.substring(20)}"
    }

    override suspend fun order(auftrag: Auftrag): String {
        paareLaden()
        val paar = paare[auftrag.symbol] ?: throw BrokerFehler("ABGELEHNT", auftrag.symbol)
        val schritt = paar.zahl("asset_increment") ?: 1e-8
        val stellen = maxOf(0, -kotlin.math.floor(kotlin.math.log10(schritt)).toInt())
        val menge = "%.${stellen}f".format(java.util.Locale.ROOT, kotlin.math.floor(auftrag.menge / schritt + 1e-9) * schritt)
        val preisSchritt = paar.zahl("quote_increment") ?: 0.01
        val preisStellen = maxOf(0, -kotlin.math.floor(kotlin.math.log10(preisSchritt)).toInt())
        fun preis(v: Double) = "%.${preisStellen}f".format(java.util.Locale.ROOT, v)
        val koerper = buildJsonObject {
            put("client_order_id", uuid())
            put("side", if (auftrag.seite == Side.SELL) "sell" else "buy")
            put("symbol", auftrag.symbol)
            when (auftrag.typ) {
                OrderType.LIMIT -> {
                    put("type", "limit")
                    putJsonObject("limit_order_config") {
                        put("asset_quantity", menge); put("limit_price", preis(auftrag.preis ?: throw BrokerFehler("ABGELEHNT", "price")))
                        put("time_in_force", if (auftrag.gueltigkeit == Validity.DAY) "gfd" else "gtc")
                    }
                }
                OrderType.STOP -> {
                    put("type", "stop_loss")
                    putJsonObject("stop_loss_order_config") {
                        put("asset_quantity", menge); put("stop_price", preis(auftrag.preis ?: throw BrokerFehler("ABGELEHNT", "price")))
                        put("time_in_force", if (auftrag.gueltigkeit == Validity.DAY) "gfd" else "gtc")
                    }
                }
                else -> {
                    put("type", "market")
                    putJsonObject("market_order_config") { put("asset_quantity", menge) }
                }
            }
        }
        return anfrage("/api/v1/crypto/trading/orders/", "POST", koerper.toString()).obj().text("id").orEmpty()
    }

    override suspend fun stornieren(id: String) {
        anfrage("/api/v1/crypto/trading/orders/${java.net.URLEncoder.encode(id, "UTF-8")}/cancel/", "POST")
    }

    override suspend fun kurse(symbole: List<String>): Map<String, Quote> = emptyMap()

    override suspend fun kerzen(symbol: String, range: Range): Series? = null

    override suspend fun suche(text: String): List<MarketApi.SearchHit> {
        val q = text.trim().uppercase()
        if (q.isEmpty()) return emptyList()
        paareLaden()
        return paare.keys.filter { it.startsWith(q) || it.substringBefore('-').contains(q) }
            .sortedWith(compareBy({ !it.startsWith("$q-") }, { it.length })).take(30)
            .map { MarketApi.SearchHit(it, it.substringBefore('-') + " in USD", "CRYPTOCURRENCY", "Robinhood") }
    }
}
