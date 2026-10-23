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
import de.troetenrepublik.app.market.Symbols
import de.troetenrepublik.app.market.broker.BrokerJson.arr
import de.troetenrepublik.app.market.broker.BrokerJson.obj
import de.troetenrepublik.app.market.broker.BrokerJson.text
import de.troetenrepublik.app.plattform.BrokerNetz
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.concurrent.ConcurrentHashMap

// comdirect REST API nach der Schnittstellenspezifikation (Stand April 2020) und der Swagger-Beschreibung.
// Anmeldung in fuenf Schritten: Token mit Zugangsnummer und PIN, Session abrufen, TAN-Challenge anfordern,
// Session mit der TAN aktivieren, dann der Token cd_secondary fuer Banking und Brokerage.
// Die Session-TAN gilt fuer alle Orders der Sitzung, die Validierung liefert dann TAN_FREI.
// Fuenf TAN-Challenges ohne eingeloeste TAN oder drei falsche TANs sperren das ganze Onlinebanking. Deshalb fordert
// die App eine Challenge nur in anfordern an, das die Person selbst ausloest, nie beim Start oder Abgleich.
// Der Token gilt zehn Minuten und wird per Refresh-Token verlaengert, solange die App laeuft. Laut comdirect muss das
// mit dem Beenden der App aufhoeren, danach braucht es eine neue Freigabe.
// Kurse liefert die Schnittstelle nur fuer gehaltene Positionen, daher kommen Kurse und Charts von Yahoo Finance.
// Orders gehen ueber die ISIN an einen Boersenplatz in Euro, bevorzugt Xetra. Limits in Euro.
class ComdirectBroker(
    private val api: MarketApi,
    private val clientId: String,
    private val clientSecret: String,
    private val zugang: String,
    private val pin: String,
    // Leer, wenn unbekannt.
    private val isinVon: (String) -> String,
) : Broker, Freigabe {
    private companion object {
        // Nur an diesen Host gehen Anfragen mit dem Schluessel, siehe BrokerNetz.
        val HOSTS = setOf("api.comdirect.de")
        const val BASIS = "https://api.comdirect.de/api"
        const val TOKEN = "https://api.comdirect.de/oauth/token"
        const val REVOKE = "https://api.comdirect.de/oauth/revoke"
        // Boersenplaetze fuer Orders in dieser Reihenfolge, soweit der Wert dort handelbar ist.
        val PLAETZE = listOf("xetra", "tradegate", "frankfurt", "stuttgart", "muenchen", "münchen", "berlin", "hamburg", "duesseldorf", "düsseldorf")
    }

    override val art = BrokerArt.COMDIRECT
    override val live = true
    override val preisInEuro = true
    override val freigabe: Freigabe get() = this

    // Kennung der App-Sitzung fuer den Kopf x-http-request-info, 32 Hexadezimalzeichen.
    private val sitzung = (1..32).joinToString("") { "0123456789abcdef"[kotlin.random.Random.nextInt(16)].toString() }

    private val sperre = Mutex()
    @Volatile private var erstToken: String? = null
    @Volatile private var erstBis = 0L
    @Volatile private var sessionId: String? = null
    @Volatile private var zugriff: String? = null
    @Volatile private var erneuern: String? = null
    @Volatile private var zugriffBis = 0L
    @Volatile private var freigegeben = false
    @Volatile private var depot: Pair<String, String>? = null   // depotId, Anzeige-Nummer
    @Volatile private var verrechnung: String? = null

    // Gelernte Zuordnungen ISIN zu App-Symbol.
    private val zuApp = ConcurrentHashMap<String, String>()
    private val zuIsin = ConcurrentHashMap<String, String>()
    private val katalogIsin: Map<String, String> by lazy { Symbols.all.filter { it.isin.isNotBlank() }.associate { it.isin to it.symbol } }

    override val aktiv: Boolean get() = freigegeben && zugriff != null

    // Anmeldung

    // Uhrzeit HHmmssSSS, 9 Stellen.
    private fun requestId(): String {
        val z = java.time.LocalTime.now()
        fun p(v: Int, n: Int) = v.toString().padStart(n, '0')
        return p(z.hour, 2) + p(z.minute, 2) + p(z.second, 2) + p(z.nano / 1_000_000, 3)
    }

    private fun kopf(token: String, extra: Map<String, String> = emptyMap()): Map<String, String> = mapOf(
        "Authorization" to "Bearer $token",
        "x-http-request-info" to """{"clientRequestId":{"sessionId":"$sitzung","requestId":"${requestId()}"}}""",
    ) + extra

    private data class Token(val zugriff: String, val erneuern: String?, val sekunden: Long)

    private suspend fun tokenHolen(form: List<Pair<String, String>>): Token {
        val a = BrokerNetz.anfrage(TOKEN, HOSTS, methode = "POST", formular = listOf("client_id" to clientId.trim(), "client_secret" to clientSecret.trim()) + form)
        if (!a.ok) throw fehler(a)
        val o = BrokerJson.lesen(a.text).obj() ?: throw BrokerFehler("ABGELEHNT", a.text?.take(200))
        return Token(o.text("access_token") ?: throw BrokerFehler("SCHLUESSEL"), o.text("refresh_token"), o.text("expires_in")?.toLongOrNull() ?: 599)
    }

    // Erster Token mit Zugangsnummer und PIN, dazu die Session noch ohne TAN.
    private suspend fun anmelden() {
        if (erstToken != null && System.currentTimeMillis() < erstBis - 30_000 && sessionId != null) return
        val t = tokenHolen(listOf("grant_type" to "password", "username" to zugang.trim(), "password" to pin.trim()))
        erstToken = t.zugriff
        erstBis = System.currentTimeMillis() + t.sekunden * 1000
        val a = BrokerNetz.anfrage("$BASIS/session/clients/user/v1/sessions", HOSTS, kopf(t.zugriff))
        if (!a.ok) throw fehler(a)
        val s = BrokerJson.lesen(a.text)
        sessionId = (s.arr().firstOrNull().obj() ?: s.obj()).text("identifier") ?: throw BrokerFehler("ABGELEHNT", "comdirect: keine Session")
    }

    private fun sessionKoerper(id: String) = """{"identifier":"$id","sessionTanActive":true,"activated2FA":true}"""

    override suspend fun anfordern(verfahren: String?): TanChallenge = sperre.withLock {
        anmelden()
        val id = sessionId!!
        val extra = verfahren?.let { mapOf("x-once-authentication-info" to """{"typ":"$it"}""") }.orEmpty()
        val a = BrokerNetz.anfrage("$BASIS/session/clients/user/v1/sessions/$id/validate", HOSTS, kopf(erstToken!!, extra), methode = "POST", json = sessionKoerper(id))
        if (a.code !in 200..299) throw fehler(a)
        ComdirectTexte.challenge(a.kopf["x-once-authentication-info"]) ?: throw BrokerFehler("ABGELEHNT", "comdirect: keine TAN-Challenge")
    }

    override suspend fun bestaetigen(c: TanChallenge, tan: String?) = sperre.withLock {
        val id = sessionId ?: throw BrokerFehler("FREIGABE")
        val extra = mapOf("x-once-authentication-info" to """{"id":"${c.id}"}""") +
            (tan?.trim()?.takeIf { c.verfahren != "P_TAN_PUSH" && it.isNotEmpty() }?.let { mapOf("x-once-authentication" to it) }.orEmpty())
        val a = BrokerNetz.anfrage("$BASIS/session/clients/user/v1/sessions/$id", HOSTS, kopf(erstToken!!, extra), methode = "PATCH", json = sessionKoerper(id))
        if (a.code !in 200..299) throw fehler(a)
        val t = tokenHolen(listOf("grant_type" to "cd_secondary", "token" to erstToken!!))
        zugriff = t.zugriff
        erneuern = t.erneuern
        zugriffBis = System.currentTimeMillis() + t.sekunden * 1000
        freigegeben = true
    }

    // Kurz vor Ablauf erneuert. Ohne Freigabe oder bei abgerissener Kette BrokerFehler FREIGABE.
    private suspend fun token(): String {
        if (!freigegeben) throw BrokerFehler("FREIGABE")
        val jetzt = System.currentTimeMillis()
        zugriff?.takeIf { jetzt < zugriffBis - 60_000 }?.let { return it }
        return sperre.withLock {
            zugriff?.takeIf { System.currentTimeMillis() < zugriffBis - 60_000 }?.let { return@withLock it }
            val r = erneuern ?: run { freigegeben = false; throw BrokerFehler("FREIGABE") }
            val t = try { tokenHolen(listOf("grant_type" to "refresh_token", "refresh_token" to r)) } catch (e: BrokerFehler) {
                // Abgelehnt heisst, die Sitzung ist vorbei. Ohne Netz bleibt sie bestehen, bis comdirect sie ablehnt.
                if (e.code != "NETZ") { freigegeben = false; zugriff = null; throw BrokerFehler("FREIGABE") }
                throw e
            }
            zugriff = t.zugriff
            erneuern = t.erneuern ?: r
            zugriffBis = System.currentTimeMillis() + t.sekunden * 1000
            t.zugriff
        }
    }

    override suspend fun abmelden() {
        val t = zugriff ?: erstToken ?: return
        runCatching { BrokerNetz.anfrage(REVOKE, HOSTS, kopf(t), methode = "DELETE") }
        freigegeben = false; zugriff = null; erneuern = null; erstToken = null; sessionId = null
    }

    private suspend fun anfrage(methode: String, pfad: String, json: String? = null, extra: Map<String, String> = emptyMap()): MarketApi.Antwort {
        val a = BrokerNetz.anfrage(BASIS + pfad, HOSTS, kopf(token(), extra), methode = methode, json = json)
        if (a.code == 401) { freigegeben = false; zugriff = null; throw BrokerFehler("FREIGABE") }
        return a
    }

    private suspend fun holen(pfad: String): JsonElement {
        val a = anfrage("GET", pfad)
        if (!a.ok) throw fehler(a)
        return BrokerJson.lesen(a.text) ?: throw BrokerFehler("ABGELEHNT", a.text?.take(200))
    }

    // Meldung von comdirect aus dem Koerper oder dem Kopf x-http-response-info.
    private fun fehler(a: MarketApi.Antwort): BrokerFehler {
        fun meldung(e: JsonElement?): String? = e.obj()?.get("messages").arr().mapNotNull { it.obj() }
            .firstOrNull { it.text("severity") == "ERROR" }?.text("message")
        val text = meldung(BrokerJson.lesen(a.text)) ?: meldung(BrokerJson.lesen(a.kopf["x-http-response-info"]))
            ?: BrokerJson.lesen(a.text).obj().text("error_description")
        return when {
            a.code == BrokerNetz.UNSICHER -> BrokerFehler("UNSICHER")
            a.code == 0 -> BrokerFehler("NETZ")
            a.code == 401 || (a.code == 400 && text == null) -> BrokerFehler("SCHLUESSEL", text)
            else -> BrokerFehler("ABGELEHNT", text ?: "HTTP ${a.code}")
        }
    }

    // Kuerzel

    private fun merken(isin: String, app: String) { zuApp[isin] = app; zuIsin.putIfAbsent(app, isin) }

    // Katalog der App, sonst die Yahoo-Suche mit Vorrang fuer deutsche Notierungen.
    private suspend fun appSymbol(isin: String): String? {
        if (isin.isBlank()) return null
        zuApp[isin]?.let { return it }
        katalogIsin[isin]?.let { merken(isin, it); return it }
        val treffer = runCatching { api.search(isin) }.getOrDefault(emptyList())
        val s = (treffer.firstOrNull { it.symbol.endsWith(".DE") } ?: treffer.firstOrNull { it.symbol.endsWith(".F") } ?: treffer.firstOrNull())?.symbol ?: return null
        merken(isin, s)
        return s
    }

    private fun isinFuer(symbol: String): String = zuIsin[symbol] ?: isinVon(symbol).ifBlank { Symbols.find(symbol)?.isin.orEmpty() }

    override fun fuehrt(symbol: String): Boolean = false

    override fun handelbar(symbol: String): Boolean =
        !symbol.startsWith("^") && !symbol.contains('=') && !symbol.endsWith("-USD") && !symbol.endsWith("-EUR") && isinFuer(symbol).length == 12

    override fun ordertypen(symbol: String): Set<OrderType> = setOf(OrderType.MARKET, OrderType.LIMIT, OrderType.STOP)

    override fun bruchstuecke(symbol: String): Boolean = false

    // Konto

    private suspend fun depotLaden(): Pair<String, String> {
        depot?.let { return it }
        val d = holen("/brokerage/clients/user/v3/depots").obj()?.get("values").arr().firstOrNull().obj() ?: throw BrokerFehler("ABGELEHNT", "comdirect: kein Depot")
        val p = (d.text("depotId") ?: throw BrokerFehler("ABGELEHNT", "comdirect: kein Depot")) to (d.text("depotDisplayId") ?: "")
        depot = p
        verrechnung = d.text("defaultSettlementAccountId")
        return p
    }

    private fun JsonObject?.betrag(k: String): Double? = this?.get(k).obj().text("value")?.toDoubleOrNull()?.takeIf { it.isFinite() }

    private fun zeit(s: String?): Long? = ComdirectTexte.zeit(s)

    override suspend fun stand(): BrokerStand {
        val (depotId, anzeige) = depotLaden()
        val positionen = holen("/brokerage/v3/depots/$depotId/positions?with-attr=instrument").obj()?.get("values").arr().mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val inst = o["instrument"].obj()
            val isin = inst.text("isin").orEmpty()
            val symbol = appSymbol(isin) ?: return@mapNotNull null
            val menge = o.betrag("quantity") ?: return@mapNotNull null
            val typ = if (inst?.get("staticData").obj().text("instrumentType") == "ETF") AssetType.ETF else AssetType.STOCK
            BrokerPosition(symbol, inst.text("name") ?: symbol, menge, o.betrag("purchasePrice") ?: 0.0, "EUR", typ)
        }
        val salden = holen("/banking/clients/user/v2/accounts/balances").obj()?.get("values").arr().mapNotNull { it.obj() }
        val konto = salden.firstOrNull { it.text("accountId") == verrechnung } ?: salden.firstOrNull { it["account"].obj()?.get("accountType").obj().text("key") == "CA" } ?: salden.firstOrNull()
        val cash = konto.betrag("balanceEUR") ?: konto.betrag("balance") ?: 0.0
        val kaufkraft = konto.betrag("availableCashAmountEUR") ?: konto.betrag("availableCashAmount") ?: cash
        val orders = runCatching { holen("/brokerage/depots/$depotId/v3/orders?with-attr=instrument").obj()?.get("values").arr() }.getOrDefault(kotlinx.serialization.json.JsonArray(emptyList()))
            .mapNotNull { order(it.obj()) }
        val umsaetze = runCatching { holen("/brokerage/v3/depots/$depotId/transactions?with-attr=instrument").obj()?.get("values").arr() }.getOrDefault(kotlinx.serialization.json.JsonArray(emptyList()))
            .mapNotNull { umsatz(it.obj()) }
        return BrokerStand("EUR", cash, kaufkraft, positionen, orders, umsaetze, anzeige)
    }

    private suspend fun order(o: JsonObject?): BrokerOrder? {
        o ?: return null
        val id = o.text("orderId") ?: return null
        val symbol = appSymbol(o["instrument"].obj().text("isin").orEmpty()) ?: return null
        val typ = when (o.text("orderType")) {
            "LIMIT" -> OrderType.LIMIT
            "STOP_MARKET", "STOP_LIMIT" -> OrderType.STOP
            "TRAILING_STOP_MARKET", "TRAILING_STOP_LIMIT" -> OrderType.TRAILING_STOP
            else -> OrderType.MARKET
        }
        val status = when (o.text("orderStatus")) {
            "EXECUTED", "SETTLED" -> OrderStatus.FILLED
            "CANCELLED_USER", "CANCELLED_SYSTEM", "CANCELLED_TRADE" -> OrderStatus.CANCELLED
            "EXPIRED" -> OrderStatus.EXPIRED
            else -> OrderStatus.OPEN
        }
        val ausf = o["executions"].arr().mapNotNull { it.obj() }
        val menge = o.betrag("quantity") ?: return null
        val ausgefuehrt = o.betrag("executedQuantity") ?: 0.0
        val kurs = ausf.mapNotNull { e -> e.betrag("executionPrice")?.let { p -> p to (e.betrag("executedQuantity") ?: 0.0) } }
            .takeIf { it.isNotEmpty() }?.let { l -> val m = l.sumOf { it.second }; if (m > 0) l.sumOf { it.first * it.second } / m else l.last().first }
        return BrokerOrder(
            id = "broker-$id", symbol = symbol, name = o["instrument"].obj().text("name") ?: symbol,
            seite = if (o.text("side") == "SELL") Side.SELL else Side.BUY, typ = typ, menge = menge,
            limit = o.betrag("limit"), stop = o.betrag("triggerLimit"), trailPct = null, status = status,
            erstellt = zeit(o.text("creationTimestamp")) ?: 0L,
            ausgefuehrt = ausf.mapNotNull { zeit(it.text("executionTimestamp")) }.maxOrNull(),
            kurs = kurs, waehrung = "EUR", gueltigkeit = if (o.text("validityType") == "GTD") Validity.GTC else Validity.DAY,
            ausgefuehrteMenge = ausgefuehrt,
        )
    }

    private suspend fun umsatz(o: JsonObject?): BrokerUmsatz? {
        o ?: return null
        val typ = when (o.text("transactionType")) { "BUY" -> TxType.BUY; "SELL" -> TxType.SELL; else -> return null }
        val symbol = appSymbol(o["instrument"].obj().text("isin").orEmpty())
        val menge = o.betrag("quantity") ?: 0.0
        val wert = o.betrag("transactionValue") ?: 0.0
        return BrokerUmsatz(
            id = o.text("transactionId") ?: return null,
            zeit = zeit(o.text("businessDate")) ?: zeit(o.text("bookingDate")) ?: return null,
            typ = typ, symbol = symbol, menge = menge, kurs = o.betrag("executionPrice") ?: if (menge > 0) wert / menge else 0.0,
            betrag = if (typ == TxType.BUY) -wert else wert, waehrung = "EUR",
            text = o["instrument"].obj().text("name").orEmpty(),
        )
    }

    // Orders

    private fun zahl(v: Double, stellen: Int) = java.math.BigDecimal.valueOf(v).setScale(stellen, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    private fun JsonObjectBuilder.betrag(k: String, v: Double, einheit: String) = putJsonObject(k) { put("value", zahl(v, 4)); put("unit", einheit) }

    private fun JsonObjectBuilder.gueltigkeit(g: Validity, typ: OrderType) {
        if (g == Validity.GTC && typ != OrderType.MARKET) {
            put("validityType", "GTD")
            put("validity", java.time.LocalDate.now().plusDays(360).toString())
        } else put("validityType", "GFD")
    }

    private fun comdirectTyp(t: OrderType) = when (t) { OrderType.LIMIT -> "LIMIT"; OrderType.STOP -> "STOP_MARKET"; else -> "MARKET" }

    // Boersenplatz in Euro, an dem der Wert mit diesem Ordertyp handelbar ist.
    // Bei null waehlt comdirect (Best Execution).
    private suspend fun handelsplatz(isin: String, seite: Side, typ: String): String? {
        val a = runCatching { anfrage("GET", "/brokerage/v3/orders/dimensions?isin=$isin&type=EXCHANGE&side=${seite.name}&orderType=$typ") }.getOrNull() ?: return null
        if (!a.ok) return null
        val plaetze = BrokerJson.lesen(a.text).obj()?.get("values").arr().firstOrNull().obj()?.get("venues").arr().mapNotNull { it.obj() }
            .filter { v -> v.text("type") == "EXCHANGE" && v["currencies"].arr().any { (it as? kotlinx.serialization.json.JsonPrimitive)?.content == "EUR" } }
        fun rang(v: JsonObject): Int = PLAETZE.indexOfFirst { v.text("name").orEmpty().lowercase().contains(it) }.let { if (it < 0) PLAETZE.size else it }
        return plaetze.minByOrNull { rang(it) }?.text("venueId")
    }

    // Erst Validierung, dann die Anfrage mit der Challenge-Kennung.
    // Ohne Session-TAN (nicht TAN_FREI) wird nichts gesendet.
    private suspend fun mitFreigabe(pruefen: String, koerperPruefen: String, methode: String, pfad: String, koerper: String?): MarketApi.Antwort {
        val v = anfrage("POST", pruefen, koerperPruefen)
        if (v.code !in 200..299) throw fehler(v)
        val info = BrokerJson.lesen(v.kopf["x-once-authentication-info"]).obj()
        val id = info.text("id")?.trim() ?: throw BrokerFehler("ABGELEHNT", "comdirect: keine Challenge zur Order")
        if (info.text("typ") != "TAN_FREI") throw BrokerFehler("FREIGABE")
        val a = anfrage(methode, pfad, koerper, mapOf("x-once-authentication-info" to """{"id":"$id"}"""))
        if (a.code !in 200..299) throw fehler(a)
        return a
    }

    override suspend fun order(auftrag: Auftrag): String {
        val isin = isinFuer(auftrag.symbol).takeIf { it.length == 12 } ?: throw BrokerFehler("ABGELEHNT", "ISIN unbekannt")
        val (depotId, _) = depotLaden()
        val typ = comdirectTyp(auftrag.typ)
        val platz = handelsplatz(isin, auftrag.seite, typ)
        val koerper = buildJsonObject {
            put("depotId", depotId)
            put("side", auftrag.seite.name)
            put("instrumentId", isin)
            put("orderType", typ)
            putJsonObject("quantity") { put("value", zahl(auftrag.menge, 6)); put("unit", "XXX") }
            if (platz != null) put("venueId", platz) else put("bestEx", true)
            when (auftrag.typ) {
                OrderType.LIMIT -> betrag("limit", auftrag.preis ?: throw BrokerFehler("ABGELEHNT", "Limit fehlt"), "EUR")
                OrderType.STOP -> betrag("triggerLimit", auftrag.preis ?: throw BrokerFehler("ABGELEHNT", "Stopp fehlt"), "EUR")
                else -> {}
            }
            gueltigkeit(auftrag.gueltigkeit, auftrag.typ)
        }.toString()
        val a = mitFreigabe("/brokerage/v3/orders/validation", koerper, "POST", "/brokerage/v3/orders", koerper)
        return BrokerJson.lesen(a.text).obj().text("orderId").orEmpty()
    }

    // Laut comdirect sind nur Limit und Gueltigkeit aenderbar.
    override fun aenderbar(symbol: String) = Aenderbar(menge = false, preis = true, gueltigkeit = true)

    override suspend fun aendern(id: String, auftrag: Auftrag) {
        val koerper = buildJsonObject {
            put("orderType", comdirectTyp(auftrag.typ))
            when (auftrag.typ) {
                OrderType.LIMIT -> auftrag.preis?.let { betrag("limit", it, "EUR") }
                OrderType.STOP -> auftrag.preis?.let { betrag("triggerLimit", it, "EUR") }
                else -> {}
            }
            gueltigkeit(auftrag.gueltigkeit, auftrag.typ)
        }.toString()
        val o = id.removePrefix("broker-")
        mitFreigabe("/brokerage/v3/orders/$o/validation", koerper, "PATCH", "/brokerage/v3/orders/$o", koerper)
    }

    override suspend fun stornieren(id: String) {
        val o = id.removePrefix("broker-")
        mitFreigabe("/brokerage/v3/orders/$o/validation", "{}", "DELETE", "/brokerage/v3/orders/$o", null)
    }

    // Kurse kommen von Yahoo, Suche unter Aktien, ETFs und Fonds

    override suspend fun kurse(symbole: List<String>): Map<String, Quote> = emptyMap()

    override suspend fun kerzen(symbol: String, range: Range): Series? = null

    override suspend fun suche(text: String): List<MarketApi.SearchHit> =
        runCatching { api.search(text) }.getOrDefault(emptyList()).filter { it.type in setOf("EQUITY", "ETF", "MUTUALFUND") }
}

// Was sich an den Antworten von comdirect ohne Netz lesen laesst.
internal object ComdirectTexte {
    // Format 2020-01-15T10:30:00,123456+01, auch mit +01:00 oder Z, oder nur ein Datum.
    fun zeit(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        val t = s.trim().replace(',', '.').let { x -> Regex("([+-]\\d{2})$").find(x)?.let { m -> x.substring(0, m.range.first) + m.value + ":00" } ?: x }
        return BrokerJson.zeit(t)
    }

    // TAN-Challenge aus dem Kopf x-once-authentication-info, null, wenn er fehlt oder keine Kennung hat.
    fun challenge(kopf: String?): TanChallenge? {
        val info = BrokerJson.lesen(kopf).obj() ?: return null
        val typ = info.text("typ").orEmpty()
        return TanChallenge(
            id = info.text("id")?.trim() ?: return null,
            verfahren = typ,
            bild = info.text("challenge")?.takeIf { typ == "P_TAN" },
            text = info.text("challenge")?.takeIf { typ == "M_TAN" }.orEmpty(),
            verfuegbar = info["availableTypes"].arr().mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content },
        )
    }
}
