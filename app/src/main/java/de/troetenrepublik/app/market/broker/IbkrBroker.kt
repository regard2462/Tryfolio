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
import de.troetenrepublik.app.market.broker.BrokerJson.wahr
import de.troetenrepublik.app.market.broker.BrokerJson.zahl
import de.troetenrepublik.app.plattform.BrokerNetz
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

// Interactive Brokers ueber das Client Portal Gateway, ein Programm von IBKR, das der Nutzer auf seinem Rechner
// startet. Angemeldet wird im Browser an der Adresse des Gateways (mit zweitem Faktor). Die App spricht es per HTTPS
// an, lokal oder im Heimnetz. Das Zertifikat ist selbst signiert: Die App nimmt nur das an, dessen Fingerabdruck
// der Nutzer beim Verbinden bestaetigt hat (pin). Die Sitzung haelt das Gateway, die App haelt sie mit /tickle wach.
// Kurse und Charts kommen von Yahoo Finance, weil Kurse ueber die Schnittstelle ein Marktdaten-Abo brauchen.
// Gehandelt wird ueber die Kontrakt-ID (conid), gefunden ueber Kuerzel und Boerse.
class IbkrBroker(
    private val api: MarketApi,
    adresse: String,
    private val pin: String,
    override val live: Boolean,
) : Broker {
    companion object {
        // Vereinheitlicht auf https, Host und Port (Vorgabe 5000); null, wenn die Adresse unbrauchbar ist.
        fun adresse(eingabe: String): String? {
            var a = eingabe.trim().removeSuffix("/")
            if (a.isEmpty()) return null
            if (a.startsWith("http://", true)) a = "https://" + a.substring(7)
            if (!a.startsWith("https://", true)) a = "https://$a"
            val ohne = a.substring(8).substringBefore('/')
            if (ohne.isBlank() || ohne.any { it.isWhitespace() }) return null
            val mitPort = if (ohne.substringAfterLast(']', ohne).contains(':')) ohne else "$ohne:5000"
            return "https://$mitPort"
        }

        // Host einer vereinheitlichten Adresse (ohne Port, IPv6 ohne Klammern).
        fun host(adresse: String): String {
            val rest = adresse.removePrefix("https://").substringBefore('/')
            return (if (rest.startsWith("[")) rest.substringAfter('[').substringBefore(']') else rest.substringBeforeLast(':')).lowercase()
        }

        // Boersen von IBKR zur Endung des Kuerzels bei Yahoo.
        private val ENDUNG = mapOf(
            "IBIS" to ".DE", "IBIS2" to ".DE", "XETRA" to ".DE", "FWB" to ".F", "FWB2" to ".F", "SWB" to ".SG", "TGATE" to ".DE", "GETTEX" to ".DE",
            "LSE" to ".L", "LSEETF" to ".L", "SBF" to ".PA", "AEB" to ".AS", "ENEXT.BE" to ".BR", "EBS" to ".SW", "BVME" to ".MI",
            "BM" to ".MC", "VSE" to ".VI", "SFB" to ".ST", "OSE" to ".OL", "TSE" to ".TO", "ASX" to ".AX",
        )
        private val US = setOf("NASDAQ", "NYSE", "ARCA", "AMEX", "BATS", "ISLAND", "NMS", "IEX", "SMART")
    }

    override val art = BrokerArt.IBKR

    private val basis = (adresse(adresse) ?: "https://localhost:5000") + "/v1/api"
    private val anmeldeSeite = adresse(adresse) ?: "https://localhost:5000"
    private val hosts = setOf(host(anmeldeSeite))

    @Volatile private var konto: String? = null
    @Volatile private var kontoWaehrung = "USD"
    @Volatile private var kontenGeladen = false
    @Volatile private var hinweise: List<String> = emptyList()

    override fun letzteHinweise(): List<String> = hinweise
    private val zuConid = ConcurrentHashMap<String, Long>()
    private val zuApp = ConcurrentHashMap<Long, String>()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private suspend fun roh(methode: String, pfad: String, json: String? = null): MarketApi.Antwort =
        BrokerNetz.anfrage(basis + pfad, hosts, methode = methode, json = json, pin = pin)

    private suspend fun holen(pfad: String): JsonElement {
        val a = roh("GET", pfad)
        if (!a.ok) throw fehler(a)
        return BrokerJson.lesen(a.text) ?: throw BrokerFehler("ABGELEHNT", a.text?.take(200))
    }

    private fun fehler(a: MarketApi.Antwort): BrokerFehler {
        val o = BrokerJson.lesen(a.text).obj()
        return when {
            a.code == BrokerNetz.UNSICHER -> BrokerFehler("UNSICHER")
            // Keine Verbindung oder ein anderes Zertifikat als das bestaetigte
            a.code == 0 -> BrokerFehler("NETZ")
            a.code == 401 -> BrokerFehler("GATEWAY", anmeldeSeite)
            else -> BrokerFehler("ABGELEHNT", o.text("error") ?: o.text("message") ?: "HTTP ${a.code}")
        }
    }

    // Ohne Anmeldung ein Fehler mit dem Hinweis auf die Anmeldung im Browser am Gateway.
    private suspend fun angemeldet() {
        runCatching { roh("POST", "/tickle") }
        val s = holen("/iserver/auth/status").obj()
        if (!s.wahr("authenticated")) throw BrokerFehler("GATEWAY", anmeldeSeite)
    }

    private suspend fun kontoId(): String {
        konto?.let { return it }
        // /iserver/accounts muss vor Orders und Orderbuch einmal abgerufen werden
        if (!kontenGeladen) { runCatching { holen("/iserver/accounts") }; kontenGeladen = true }
        val k = holen("/portfolio/accounts").arr().firstOrNull().obj() ?: throw BrokerFehler("ABGELEHNT", "IBKR: kein Konto")
        val id = k.text("accountId") ?: k.text("id") ?: throw BrokerFehler("ABGELEHNT", "IBKR: kein Konto")
        kontoWaehrung = k.text("currency") ?: "USD"
        konto = id
        return id
    }

    // ------------------------------------------------------------ Kuerzel

    // Yahoo-Kuerzel zu einem Kontrakt: US-Werte direkt, sonst mit der Endung der Boerse.
    private fun appSymbol(conid: Long, ticker: String?, boerse: String?): String? {
        zuApp[conid]?.let { return it }
        val t = ticker?.trim()?.replace(' ', '-')?.takeIf { it.isNotEmpty() } ?: return null
        val b = boerse?.uppercase().orEmpty()
        val s = when {
            b.isEmpty() || b in US -> t
            else -> ENDUNG[b]?.let { t + it } ?: return null
        }
        zuApp[conid] = s
        zuConid.putIfAbsent(s, conid)
        return s
    }

    // Kontrakt-ID zu einem App-Kuerzel ueber die Aktiensuche von IBKR (Kuerzel und Boerse).
    private suspend fun conid(symbol: String): Long {
        zuConid[symbol]?.let { return it }
        val punkt = symbol.lastIndexOf('.')
        val endung = if (punkt > 0) symbol.substring(punkt) else ""
        val ticker = (if (punkt > 0) symbol.substring(0, punkt) else symbol).replace('-', ' ')
        val boersen = if (endung.isEmpty()) US else ENDUNG.filterValues { it == endung }.keys
        if (boersen.isEmpty()) throw BrokerFehler("ABGELEHNT", "IBKR: $symbol")
        val r = holen("/trsrv/stocks?symbols=${enc(ticker)}").obj()
        val treffer = (r?.get(ticker) ?: r?.values?.firstOrNull()).arr().mapNotNull { it.obj() }
        for (t in treffer) for (k in t["contracts"].arr().mapNotNull { it.obj() }) {
            val c = k.text("conid")?.toLongOrNull() ?: continue
            val b = k.text("exchange")?.uppercase().orEmpty()
            if (b in boersen || (endung.isEmpty() && k.wahr("isUS"))) {
                zuConid[symbol] = c
                zuApp.putIfAbsent(c, symbol)
                return c
            }
        }
        throw BrokerFehler("ABGELEHNT", "IBKR: $symbol")
    }

    override fun fuehrt(symbol: String): Boolean = false

    override fun handelbar(symbol: String): Boolean {
        if (symbol.startsWith("^") || symbol.contains('=') || symbol.endsWith("-USD") || symbol.endsWith("-EUR")) return false
        val punkt = symbol.lastIndexOf('.')
        return punkt < 0 || symbol.substring(punkt) in ENDUNG.values
    }

    override fun ordertypen(symbol: String): Set<OrderType> = setOf(OrderType.MARKET, OrderType.LIMIT, OrderType.STOP, OrderType.TRAILING_STOP)

    override fun bruchstuecke(symbol: String): Boolean = false

    // ------------------------------------------------------------ Konto

    override suspend fun stand(): BrokerStand {
        angemeldet()
        val k = kontoId()
        val positionen = ArrayList<BrokerPosition>()
        for (seite in 0 until 10) {
            val liste = holen("/portfolio/$k/positions/$seite").arr().mapNotNull { it.obj() }
            for (p in liste) {
                val menge = p.zahl("position") ?: continue
                if (menge == 0.0) continue
                val klasse = p.text("assetClass").orEmpty()
                if (klasse != "STK" && klasse != "ETF" && klasse != "FUND") continue
                val conid = p.text("conid")?.toLongOrNull() ?: continue
                val symbol = appSymbol(conid, p.text("ticker") ?: p.text("contractDesc"), p.text("listingExchange")) ?: continue
                positionen += BrokerPosition(
                    symbol, p.text("name") ?: p.text("contractDesc") ?: symbol, menge, p.zahl("avgPrice") ?: p.zahl("avgCost") ?: 0.0,
                    p.text("currency") ?: kontoWaehrung, if (klasse == "ETF") AssetType.ETF else AssetType.STOCK,
                )
            }
            if (liste.size < 100) break
        }
        val summe = runCatching { holen("/portfolio/$k/summary").obj() }.getOrNull()
        fun betrag(key: String) = summe?.get(key).obj().zahl("amount")
        val ledger = runCatching { holen("/portfolio/$k/ledger").obj() }.getOrNull()
        val cash = ledger?.get("BASE").obj().zahl("cashbalance") ?: betrag("totalcashvalue") ?: 0.0
        val kaufkraft = betrag("availablefunds") ?: cash
        val orders = runCatching { holen("/iserver/account/orders?force=true").obj()?.get("orders").arr() }
            .getOrDefault(kotlinx.serialization.json.JsonArray(emptyList())).mapNotNull { order(it.obj()) }
        val umsaetze = runCatching { holen("/iserver/account/trades").arr() }
            .getOrDefault(kotlinx.serialization.json.JsonArray(emptyList())).mapNotNull { umsatz(it.obj()) }
        return BrokerStand(kontoWaehrung, cash, kaufkraft, positionen, orders, umsaetze, k)
    }

    private fun order(o: JsonObject?): BrokerOrder? {
        o ?: return null
        val id = o.text("orderId") ?: return null
        val conid = o.text("conid")?.toLongOrNull() ?: return null
        val symbol = appSymbol(conid, o.text("ticker"), o.text("listingExchange")) ?: return null
        val typ = when (o.text("orderType")?.lowercase()) {
            "limit", "lmt" -> OrderType.LIMIT
            "stop", "stp" -> OrderType.STOP
            "trail", "trailing stop" -> OrderType.TRAILING_STOP
            else -> OrderType.MARKET
        }
        val status = when (o.text("status")?.lowercase()) {
            "filled" -> OrderStatus.FILLED
            "cancelled", "inactive" -> OrderStatus.CANCELLED
            else -> OrderStatus.OPEN
        }
        return BrokerOrder(
            id = "broker-$id", symbol = symbol, name = o.text("companyName") ?: symbol,
            seite = if (o.text("side")?.uppercase()?.startsWith("S") == true) Side.SELL else Side.BUY, typ = typ,
            menge = o.zahl("totalSize") ?: ((o.zahl("filledQuantity") ?: 0.0) + (o.zahl("remainingQuantity") ?: 0.0)),
            limit = if (typ == OrderType.LIMIT) o.zahl("price") else null,
            stop = if (typ == OrderType.STOP || typ == OrderType.TRAILING_STOP) o.zahl("auxPrice") ?: o.zahl("price") else null,
            trailPct = null, status = status, erstellt = 0L,
            ausgefuehrt = o.text("lastExecutionTime_r")?.toLongOrNull()?.takeIf { status == OrderStatus.FILLED },
            kurs = o.zahl("avgPrice"), waehrung = o.text("cashCcy") ?: kontoWaehrung,
            gueltigkeit = if (o.text("timeInForce")?.uppercase() == "GTC") Validity.GTC else Validity.DAY,
            ausgefuehrteMenge = o.zahl("filledQuantity") ?: 0.0,
        )
    }

    private fun umsatz(o: JsonObject?): BrokerUmsatz? {
        o ?: return null
        val conid = o.text("conid")?.toLongOrNull()
        val symbol = conid?.let { zuApp[it] } ?: o.text("symbol")
        val kauf = o.text("side")?.uppercase()?.startsWith("B") == true
        val menge = o.zahl("size") ?: return null
        val kurs = o.zahl("price") ?: return null
        val netto = o.zahl("net_amount") ?: (menge * kurs)
        return BrokerUmsatz(
            id = o.text("execution_id") ?: return null, zeit = o.text("trade_time_r")?.toLongOrNull() ?: return null,
            typ = if (kauf) TxType.BUY else TxType.SELL, symbol = symbol, menge = menge, kurs = kurs,
            betrag = if (kauf) -kotlin.math.abs(netto) else kotlin.math.abs(netto), waehrung = o.text("currency") ?: kontoWaehrung,
            gebuehr = kotlin.math.abs(o.zahl("commission") ?: 0.0), text = o.text("company_name").orEmpty(),
        )
    }

    // ------------------------------------------------------------ Orders

    private fun koerper(auftrag: Auftrag, conid: Long): JsonObject = buildJsonObject {
        put("conid", conid)
        put("orderType", when (auftrag.typ) { OrderType.LIMIT -> "LMT"; OrderType.STOP -> "STP"; OrderType.TRAILING_STOP -> "TRAIL"; else -> "MKT" })
        put("side", auftrag.seite.name)
        if (auftrag.menge % 1.0 == 0.0) put("quantity", auftrag.menge.toLong()) else put("quantity", auftrag.menge)
        put("tif", if (auftrag.gueltigkeit == Validity.GTC && auftrag.typ != OrderType.MARKET) "GTC" else "DAY")
        put("cOID", auftrag.kennung)
        when (auftrag.typ) {
            OrderType.LIMIT, OrderType.STOP -> auftrag.preis?.let { put("price", it) }
            OrderType.TRAILING_STOP -> { put("trailingAmt", auftrag.trailPct ?: 1.0); put("trailingType", "%") }
            else -> {}
        }
    }

    // IBKR antwortet auf eine Order mit der Order-Nummer oder mit Rueckfragen (Warnungen, etwa ohne Marktdaten oder
    // bei grossem Abstand zum Kurs). Der Nutzer hat die Order schon bestaetigt; Rueckfragen werden bejaht
    // (hoechstens fuenf), ihre Texte kommen mit der Nummer zurueck.
    private suspend fun bestaetigt(erste: MarketApi.Antwort): Pair<String, List<String>> {
        var a = erste
        val hinweise = ArrayList<String>()
        repeat(6) {
            if (!a.ok) throw fehler(a)
            val e = BrokerJson.lesen(a.text)
            val o = e.arr().firstOrNull().obj() ?: e.obj()
            o.text("error")?.let { throw BrokerFehler("ABGELEHNT", it) }
            o.text("order_id")?.let { return it to hinweise }
            val frage = o.text("id") ?: throw BrokerFehler("ABGELEHNT", a.text?.take(200))
            o?.get("message").arr().mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.forEach { hinweise += it }
            a = roh("POST", "/iserver/reply/${enc(frage)}", """{"confirmed":true}""")
        }
        throw BrokerFehler("ABGELEHNT", "IBKR: " + hinweise.lastOrNull().orEmpty())
    }

    override suspend fun order(auftrag: Auftrag): String {
        angemeldet()
        val k = kontoId()
        val c = conid(auftrag.symbol)
        val (id, h) = bestaetigt(roh("POST", "/iserver/account/$k/orders", """{"orders":[${koerper(auftrag, c)}]}"""))
        hinweise = h
        return id
    }

    override fun aenderbar(symbol: String) = Aenderbar(menge = true, preis = true, gueltigkeit = true)

    override suspend fun aendern(id: String, auftrag: Auftrag) {
        angemeldet()
        val k = kontoId()
        val c = conid(auftrag.symbol)
        hinweise = bestaetigt(roh("POST", "/iserver/account/$k/order/${enc(id.removePrefix("broker-"))}", koerper(auftrag, c).toString())).second
    }

    override suspend fun stornieren(id: String) {
        val k = kontoId()
        val a = roh("DELETE", "/iserver/account/$k/order/${enc(id.removePrefix("broker-"))}")
        if (!a.ok) throw fehler(a)
    }

    // ------------------------------------------------------------ Kurse: von Yahoo

    override suspend fun kurse(symbole: List<String>): Map<String, Quote> = emptyMap()

    override suspend fun kerzen(symbol: String, range: Range): Series? = null

    override suspend fun suche(text: String): List<MarketApi.SearchHit> =
        runCatching { api.search(text) }.getOrDefault(emptyList()).filter { it.type in setOf("EQUITY", "ETF") && handelbar(it.symbol) }
}
