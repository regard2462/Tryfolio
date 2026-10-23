package de.troetenrepublik.app.market

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.CompanyInfo
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.data.Series
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

// Kursdaten von Yahoo Finance, Stammdaten von Wikidata und Wikipedia, Einlagesatz vom Datenportal der EZB.
// Der Quote-Endpunkt verlangt Sitzungs-Cookie, crumb-Token und Browser-User-Agent. Schlaegt das fehl,
// liefert der Chart-Endpunkt im meta-Block dieselben Eckdaten als Rueckfallebene.
class MarketApi {

    companion object {
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        private const val ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        private const val Q1 = "https://query1.finance.yahoo.com"
        private const val Q2 = "https://query2.finance.yahoo.com"
        // Groesste Antwort in Bytes, die get liest.
        private const val MAX_ANTWORT = 16_000_000

        // Offene Kennung fuer Wikipedia und Behoerden (SEC, Clerk of the House): nur App und Fassung,
        // nichts ueber die Person.
        private val WIKI_UA = "Tryfolio/" + Aktualisierung.FASSUNG + " (open-source practice depot)"

        // Sammelmeldungen wie Terminkalender und Ueberblicke von dpa-AFX, die Yahoo jedem genannten Unternehmen zuordnet.
        private val SAMMELMELDUNG = Regex(
            "^(tages|wochen|monats|jahres)vorschau\\b|\\btermine (am|bis|vom|im)\\b|^dpa-afx[- ]?(überblick|börsentag)|" +
                "börsentag auf einen blick|^(earnings|economic|dividend) calendar\\b|^week ahead\\b|^(the )?day ahead\\b",
            RegexOption.IGNORE_CASE,
        )

        fun istSammelmeldung(titel: String): Boolean = SAMMELMELDUNG.containsMatchIn(titel.trim())
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    private val cookies = mutableMapOf<String, MutableList<Cookie>>()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, list: List<Cookie>) = synchronized(cookies) {
                val host = url.topPrivateDomain() ?: url.host
                val store = cookies.getOrPut(host) { mutableListOf() }
                for (c in list) {
                    store.removeAll { it.name == c.name }
                    store += c
                }
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(cookies) {
                val host = url.topPrivateDomain() ?: url.host
                cookies[host]?.filter { it.expiresAt > System.currentTimeMillis() && it.matches(url) } ?: emptyList()
            }
        })
        .addInterceptor { chain ->
            val host = chain.request().url.host
            val wiki = host.endsWith("wikipedia.org") || host.endsWith("wikidata.org") || host.endsWith("wikimedia.org") ||
                host.endsWith("sec.gov") || host.endsWith("house.gov") || host.endsWith("polymarket.com") || host.endsWith("kalshi.com") ||
                host.endsWith("guardianapis.com") || host.endsWith("finnhub.io") || host.endsWith("alphavantage.co") || host.endsWith("deepl.com") ||
                host.endsWith("github.com") || host.endsWith("reddit.com") || host.endsWith("usaspending.gov") || host.endsWith("finra.org") ||
                host.endsWith("fec.gov") || host.endsWith("texas.gov") || host.endsWith("uspto.gov")
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", chain.request().header("User-Agent") ?: if (wiki) WIKI_UA else UA)
                    // Ein gesetzter Accept-Kopf bleibt, Nachrichtenfeeds fragen nach XML
                    .header("Accept", chain.request().header("Accept") ?: if (wiki) "application/json" else ACCEPT)
                    .header("Accept-Language", "de-DE,de;q=0.9,en;q=0.8")
                    .build()
            )
        }
        .addNetworkInterceptor(Zertifikatssperre.pruefung)
        .build()

    @Volatile private var crumb: String? = null

    private suspend fun get(url: String): String? = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(url).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resume(null)
            }
            override fun onResponse(call: Call, response: Response) {
                val body = response.use { r ->
                    // Hoechstens 16 MB, damit eine feindliche Antwort nicht den Speicher fuellt
                    try {
                        val b = r.body
                        if (!r.isSuccessful || b == null) null
                        else lesenBis(b.byteStream(), MAX_ANTWORT + 1).takeIf { it.size <= MAX_ANTWORT }
                            ?.toString(b.contentType()?.charset() ?: Charsets.UTF_8)
                    } catch (_: IOException) { null }
                }
                if (continuation.isActive) continuation.resume(body)
            }
        })
    }

    // Cookie und crumb, nur einmal je Sitzung noetig.
    private suspend fun ensureCrumb(): String? {
        crumb?.let { return it }
        get("https://finance.yahoo.com/")
        get("https://fc.yahoo.com/")
        val c = get("$Q1/v1/test/getcrumb")?.trim()
        if (!c.isNullOrEmpty() && c.length < 40 && !c.startsWith("<")) crumb = c
        return crumb
    }

    private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

    // ------------------------------------------------------------ Kurse

    suspend fun quotes(symbols: List<String>): Map<String, Quote> {
        if (symbols.isEmpty()) return emptyMap()
        val viaBatch = batchQuotes(symbols)
        val missing = symbols.filter { it !in viaBatch }
        if (missing.isEmpty()) return viaBatch
        val fallback = mutableMapOf<String, Quote>()
        for (s in missing) chartQuote(s)?.let { fallback[s] = it }
        return viaBatch + fallback
    }

    // Kennzahlen fuer den Depotfinder je Kuerzel: KGV, KBV, Gewinn je Aktie (letzte zwoelf Monate und erwartet),
    // Buchwert je Aktie und Dividendenrendite. Bis 100 Kuerzel je Abfrage.
    suspend fun kennzahlen(symbols: List<String>): Map<String, Map<String, Double>> {
        if (symbols.isEmpty()) return emptyMap()
        val c = ensureCrumb() ?: return emptyMap()
        val body = get("$Q1/v7/finance/quote?format=json&symbols=${symbols.take(100).joinToString(",") { enc(it) }}&crumb=${enc(c)}")
            ?: run { crumb = null; return emptyMap() }
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return emptyMap()
        val res = root["quoteResponse"]?.jsonObject?.get("result") as? JsonArray ?: return emptyMap()
        val felder = listOf("trailingPE", "priceToBook", "epsTrailingTwelveMonths", "epsForward", "bookValue", "trailingAnnualDividendYield")
        return res.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val s = o["symbol"]?.jsonPrimitive?.content ?: return@mapNotNull null
            s to felder.mapNotNull { f -> (o[f] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()?.let { f to it } }.toMap()
        }.toMap()
    }

    // Ohne Einzelabruf fehlender Werte, fuer lange Listen wie die Entdecken-Seiten.
    suspend fun quotesBatchOnly(symbols: List<String>): Map<String, Quote> = if (symbols.isEmpty()) emptyMap() else batchQuotes(symbols)

    private suspend fun batchQuotes(symbols: List<String>): Map<String, Quote> {
        val c = ensureCrumb() ?: return emptyMap()
        val list = symbols.joinToString(",") { enc(it) }
        val url = "$Q1/v7/finance/quote?format=json&symbols=$list&crumb=${enc(c)}"
        val body = get(url) ?: run { crumb = null; return emptyMap() }
        val parsed = runCatching { json.decodeFromString<QuoteEnvelope>(body) }.getOrNull() ?: return emptyMap()
        val now = System.currentTimeMillis()
        return parsed.quoteResponse?.result.orEmpty().mapNotNull { it.toQuote(now) }.associateBy { it.symbol }
    }

    // Eckdaten aus dem Chart-Endpunkt, funktioniert auch ohne crumb.
    suspend fun chartQuote(symbol: String): Quote? {
        val body = get("$Q1/v8/finance/chart/${enc(symbol)}?interval=1d&range=5d") ?: return null
        val env = runCatching { json.decodeFromString<ChartEnvelope>(body) }.getOrNull() ?: return null
        val res = env.chart?.result?.firstOrNull() ?: return null
        val m = res.meta ?: return null
        val price = m.regularMarketPrice ?: return null
        // chartPreviousClose ist der Schluss vor dem Fenster, bei fuenf Tagen fast eine Woche alt.
        // Daher der vorletzte Tagesschluss aus den Kerzen.
        val schluesse = res.indicators?.quote?.firstOrNull()?.close?.filterNotNull().orEmpty()
        val prev = m.previousClose ?: schluesse.dropLast(1).lastOrNull() ?: m.chartPreviousClose ?: price
        return Quote(
            symbol = m.symbol ?: symbol,
            name = m.longName ?: m.shortName ?: Symbols.name(symbol),
            price = price,
            prevClose = prev,
            dayHigh = m.regularMarketDayHigh ?: 0.0,
            dayLow = m.regularMarketDayLow ?: 0.0,
            weekHigh52 = m.fiftyTwoWeekHigh ?: 0.0,
            weekLow52 = m.fiftyTwoWeekLow ?: 0.0,
            volume = m.regularMarketVolume ?: 0.0,
            nativeCurrency = m.currency ?: "EUR",
            nativePrice = price,
            regularPrice = price,
            exchange = m.exchangeName ?: "",
            marketState = "UNKNOWN",
            updatedAt = System.currentTimeMillis(),
            marketTime = (m.regularMarketTime ?: 0L) * 1000L,
        )
    }

    // ------------------------------------------------------------ Historie

    // Kursreihe einschliesslich vor- und nachboerslichem Handel. Fuer Intraday-Zeitraeume liefert
    // das Handelsfenster des Tages den Rahmen der Zeitachse.
    suspend fun candles(symbol: String, range: String, interval: String, prePost: Boolean = true): Series =
        chart("$Q1/v8/finance/chart/${enc(symbol)}?interval=$interval&range=$range&includePrePost=$prePost")

    // Zeitpunkte in Sekunden seit 1970. Anders als bei range=max bleibt die Aufloesung erhalten,
    // bei max liefert Yahoo Wochenwerte nur als Quartalswerte.
    suspend fun candlesBetween(symbol: String, fromSec: Long, toSec: Long, interval: String = "1d"): Series =
        chart("$Q1/v8/finance/chart/${enc(symbol)}?interval=$interval&period1=$fromSec&period2=$toSec&includePrePost=false")

    // Bis zu 20 Werte in einer Abfrage. Werte ohne Antwort fehlen in der Karte.
    suspend fun schlusskurse(symbols: List<String>, range: String, interval: String = "1d"): Map<String, List<Candle>> {
        if (symbols.isEmpty()) return emptyMap()
        val liste = symbols.take(20).joinToString(",") { enc(it) }
        val body = get("$Q1/v7/finance/spark?symbols=$liste&range=$range&interval=${enc(interval)}") ?: return emptyMap()
        val env = runCatching { json.decodeFromString<SparkEnvelope>(body) }.getOrNull() ?: return emptyMap()
        val aus = HashMap<String, List<Candle>>()
        for (r in env.spark?.result.orEmpty()) {
            val d = r.response?.firstOrNull() ?: continue
            val ts = d.timestamp ?: continue
            val schluss = d.indicators?.quote?.firstOrNull()?.close ?: continue
            val kerzen = ts.indices.mapNotNull { i -> schluss.getOrNull(i)?.let { c -> Candle(ts[i] * 1000L, c, c, c, c) } }
            if (kerzen.isNotEmpty() && r.symbol.isNotBlank()) aus[r.symbol] = kerzen
        }
        return aus
    }

    // Erwaehnung auf Reddit laut ApeWisdom: Rang, Erwaehnungen und Upvotes der letzten 24 Stunden und davor.
    data class Erwaehnung(
        val rang: Int, val ticker: String, val name: String, val erwaehnungen: Int, val upvotes: Int,
        val rangVorher: Int?, val erwaehnungenVorher: Int?,
    )

    // Meisterwaehnte Aktien auf Reddit ueber die freie Schnittstelle von ApeWisdom (https://apewisdom.io/api/).
    // 100 Werte je Seite, nach Erwaehnungen der letzten 24 Stunden sortiert. Leer, wenn der Dienst nicht antwortet.
    suspend fun apeWisdom(filter: String = "all-stocks"): List<Erwaehnung> {
        val body = get("https://apewisdom.io/api/v1.0/filter/${enc(filter)}/page/1") ?: return emptyList()
        val wurzel = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return emptyList()
        val liste = wurzel["results"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        fun kotlinx.serialization.json.JsonObject.zahl(k: String): Int? =
            (this[k] as? kotlinx.serialization.json.JsonPrimitive)?.content?.trim()?.toDoubleOrNull()?.toInt()
        fun kotlinx.serialization.json.JsonObject.text(k: String): String =
            (this[k] as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it != "null" }.orEmpty()
        return liste.mapNotNull { e ->
            val o = e as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            val ticker = o.text("ticker").trim().uppercase().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            Erwaehnung(o.zahl("rank") ?: 0, ticker, unescape(o.text("name")), o.zahl("mentions") ?: 0, o.zahl("upvotes") ?: 0,
                o.zahl("rank_24h_ago"), o.zahl("mentions_24h_ago"))
        }
    }

    // Bei Yahoo Finance gerade viel aufgerufene Werte einer Region.
    suspend fun trending(region: String, anzahl: Int = 25): List<String> {
        val body = get("$Q1/v1/finance/trending/${enc(region)}?count=$anzahl") ?: return emptyList()
        return yahooSymbole(body)
    }

    // Vordefinierte Listen von Yahoo Finance wie most_actives, day_gainers oder day_losers.
    suspend fun screener(id: String, anzahl: Int = 25): List<String> {
        val c = ensureCrumb()
        val body = get("$Q1/v1/finance/screener/predefined/saved?scrIds=${enc(id)}&count=$anzahl&formatted=false" + (c?.let { "&crumb=${enc(it)}" } ?: ""))
            ?: return emptyList()
        return yahooSymbole(body)
    }

    private fun yahooSymbole(body: String): List<String> = runCatching {
        val r = (json.parseToJsonElement(body).jsonObject["finance"]?.jsonObject?.get("result") as? JsonArray)?.firstOrNull() as? JsonObject
        (r?.get("quotes") as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.get("symbol")?.jsonPrimitive?.content?.takeIf { s -> s.isNotBlank() } }
    }.getOrDefault(emptyList())

    // Taegliche Aufrufe eines Wikipedia-Artikels ohne Bots ueber die Wikimedia-Schnittstelle, aelteste zuerst.
    // Die Daten sind frei (CC0) und etwa einen Tag verzoegert. artikel ist der Titel wie in der Adresse.
    suspend fun wikiAufrufe(sprache: String, artikel: String, tage: Int = 60): List<Int> {
        fun tag(ms: Long) = java.time.LocalDate.ofEpochDay(Math.floorDiv(ms, 86_400_000L)).let { "%04d%02d%02d".format(it.year, it.monthValue, it.dayOfMonth) }
        val jetzt = System.currentTimeMillis()
        val bis = tag(jetzt - 86_400_000L)
        val von = tag(jetzt - tage * 86_400_000L)
        val titel = enc(java.net.URLDecoder.decode(artikel, "UTF-8").replace(' ', '_'))
        val body = get("https://wikimedia.org/api/rest_v1/metrics/pageviews/per-article/${enc(sprache)}.wikipedia/all-access/user/$titel/daily/$von/$bis")
            ?: return emptyList()
        return runCatching {
            (json.parseToJsonElement(body).jsonObject["items"] as? JsonArray).orEmpty().mapNotNull {
                (it as? JsonObject)?.get("views")?.jsonPrimitive?.content?.toDoubleOrNull()?.toInt()
            }
        }.getOrDefault(emptyList())
    }

    private suspend fun chart(url: String): Series {
        val body = get(url) ?: return Series(emptyList())
        val env = runCatching { json.decodeFromString<ChartEnvelope>(body) }.getOrNull() ?: return Series(emptyList())
        val res = env.chart?.result?.firstOrNull() ?: return Series(emptyList())
        val ts = res.timestamp ?: return Series(emptyList())
        val q = res.indicators?.quote?.firstOrNull() ?: return Series(emptyList())
        val out = ArrayList<Candle>(ts.size)
        for (i in ts.indices) {
            val close = q.close?.getOrNull(i) ?: continue
            out += Candle(
                t = ts[i] * 1000L,
                open = q.open?.getOrNull(i) ?: close,
                high = q.high?.getOrNull(i) ?: close,
                low = q.low?.getOrNull(i) ?: close,
                close = close,
                volume = q.volume?.getOrNull(i) ?: 0.0,
            )
        }
        val period = res.meta?.currentTradingPeriod
        val start = (period?.pre?.start ?: period?.regular?.start ?: 0L) * 1000L
        val end = (period?.post?.end ?: period?.regular?.end ?: 0L) * 1000L
        return Series(out, start, end)
    }

    // ------------------------------------------------------------ Dividenden

    // Ein Ausschuettungstermin: Ex-Tag und Betrag je Stueck in der Handelswaehrung.
    data class DividendEvent(val exDate: Long, val amount: Double)

    // Dividenden der letzten Monate aus dem Chart-Endpunkt (events=div).
    // Yahoo liefert nur vergangene Ex-Tage; kuenftige Termine stehen im Kalender des Profils.
    suspend fun dividends(symbol: String, range: String = "1y"): List<DividendEvent> {
        val body = get("$Q1/v8/finance/chart/${enc(symbol)}?interval=1d&range=$range&events=div") ?: return emptyList()
        val env = runCatching { json.decodeFromString<ChartEnvelope>(body) }.getOrNull() ?: return emptyList()
        val divs = env.chart?.result?.firstOrNull()?.events?.dividends ?: return emptyList()
        return divs.values.mapNotNull { d ->
            val ts = d.date ?: return@mapNotNull null
            val amt = d.amount ?: return@mapNotNull null
            if (amt <= 0) null else DividendEvent(ts * 1000L, amt)
        }.sortedBy { it.exDate }
    }

    // ------------------------------------------------------------ Stammdaten, Kennzahlen, Finanzen

    // Ein Berichtszeitraum der Finanzen: Umsatz und Gewinn in der Berichtswaehrung.
    data class FinPeriod(val label: String, val endDate: Long, val revenue: Double, val earnings: Double,
                         val grossProfit: Double = 0.0, val operatingIncome: Double = 0.0)

    data class EpsPeriod(val label: String, val actual: Double, val estimate: Double)

    data class Profile(
        val financialCurrency: String = "",
        val description: String = "",
        val sector: String = "",
        val industry: String = "",
        val country: String = "",
        val website: String = "",
        val employees: Int = 0,
        val targetPrice: Double = 0.0,
        val targetLow: Double = 0.0,
        val targetHigh: Double = 0.0,
        val recommendation: String = "",
        val recommendationMean: Double = 0.0,
        val analystCount: Int = 0,
        val eps: Double = 0.0,
        val forwardPe: Double = 0.0,
        val priceToBook: Double = 0.0,
        val beta: Double = 0.0,
        val dividendYield: Double = 0.0,
        val dividendRate: Double = 0.0,
        val payoutRatio: Double = 0.0,
        val enterpriseValue: Double = 0.0,
        val revenue: Double = 0.0,
        val ebitda: Double = 0.0,
        val netIncome: Double = 0.0,
        val revenueGrowth: Double = 0.0,
        val earningsGrowth: Double = 0.0,
        val grossMargin: Double = 0.0,
        val operatingMargin: Double = 0.0,
        val profitMargin: Double = 0.0,
        val totalCash: Double = 0.0,
        val totalDebt: Double = 0.0,
        val freeCashflow: Double = 0.0,
        val operatingCashflow: Double = 0.0,
        val returnOnEquity: Double = 0.0,
        val totalAssets: Double = 0.0,
        val totalLiabilities: Double = 0.0,
        val equity: Double = 0.0,
        val earningsDate: Long = 0L,
        val earningsEstimate: Double = 0.0,
        val exDividendDate: Long = 0L,
        val dividendDate: Long = 0L,
        val yearly: List<FinPeriod> = emptyList(),
        val quarterly: List<FinPeriod> = emptyList(),
        val epsHistory: List<EpsPeriod> = emptyList(),
        val shortName: String = "",
        val longName: String = "",
    )

    // Braucht denselben crumb wie die Sammelabfrage, ohne ihn bleibt das Profil leer.
    suspend fun profile(symbol: String): Profile? {
        val c = ensureCrumb() ?: return null
        val modules = "assetProfile,financialData,defaultKeyStatistics,summaryDetail,calendarEvents,earnings,earningsHistory," +
            "incomeStatementHistory,incomeStatementHistoryQuarterly,balanceSheetHistory,cashflowStatementHistory,price"
        val body = get("$Q1/v10/finance/quoteSummary/${enc(symbol)}?modules=$modules&crumb=${enc(c)}")
            ?: run { crumb = null; return null }
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val r = root["quoteSummary"]?.jsonObject?.get("result")?.let { it as? JsonArray }?.firstOrNull()?.jsonObject ?: return null

        fun obj(name: String): JsonObject? = r[name] as? JsonObject
        fun raw(o: JsonObject?, key: String): Double = when (val v = o?.get(key)) {
            null -> 0.0
            is JsonObject -> v["raw"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            else -> v.jsonPrimitive.content.toDoubleOrNull() ?: 0.0
        }
        fun str(o: JsonObject?, key: String): String = (o?.get(key) as? JsonElement)?.let { e ->
            if (e is JsonObject) e["fmt"]?.jsonPrimitive?.content else e.jsonPrimitive.content
        }.orEmpty().takeIf { it != "null" }.orEmpty()

        val ap = obj("assetProfile")
        val fin = obj("financialData")
        val key = obj("defaultKeyStatistics")
        val sum = obj("summaryDetail")
        val cal = obj("calendarEvents")
        val earn = obj("earnings")
        val price = obj("price")

        fun periods(arr: JsonElement?): List<FinPeriod> = (arr as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val label = o["date"]?.jsonPrimitive?.content ?: return@mapNotNull null
            FinPeriod(label, 0L, raw(o, "revenue"), raw(o, "earnings"))
        }
        val chart = earn?.get("financialsChart") as? JsonObject
        var yearly = periods(chart?.get("yearly"))
        var quarterly = periods(chart?.get("quarterly"))

        // Ausfuehrlichere Gewinn- und Verlustrechnung, wenn vorhanden
        fun statements(module: String): List<FinPeriod> =
            ((obj(module)?.get("incomeStatementHistory")) as? JsonArray).orEmpty().mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val end = raw(o, "endDate").toLong() * 1000L
                if (end <= 0) return@mapNotNull null
                FinPeriod(str(o, "endDate"), end, raw(o, "totalRevenue"), raw(o, "netIncome"), raw(o, "grossProfit"), raw(o, "operatingIncome"))
            }
        val isY = statements("incomeStatementHistory")
        val isQ = statements("incomeStatementHistoryQuarterly")
        if (yearly.isEmpty() && isY.isNotEmpty()) yearly = isY.sortedBy { it.endDate }.map { it.copy(label = it.label.take(4)) }
        else if (isY.isNotEmpty()) yearly = yearly.map { y -> isY.firstOrNull { it.label.startsWith(y.label) }?.let { y.copy(endDate = it.endDate, grossProfit = it.grossProfit, operatingIncome = it.operatingIncome) } ?: y }
        if (quarterly.isEmpty() && isQ.isNotEmpty()) quarterly = isQ.sortedBy { it.endDate }

        val eps = ((obj("earningsHistory")?.get("history")) as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            EpsPeriod(str(o, "quarter"), raw(o, "epsActual"), raw(o, "epsEstimate"))
        }
        val bs = ((obj("balanceSheetHistory")?.get("balanceSheetStatements")) as? JsonArray)?.firstOrNull() as? JsonObject
        val cf = ((obj("cashflowStatementHistory")?.get("cashflowStatements")) as? JsonArray)?.firstOrNull() as? JsonObject
        val earningsDates = (cal?.get("earnings") as? JsonObject)?.get("earningsDate") as? JsonArray
        val earningsDate = (earningsDates?.firstOrNull() as? JsonObject)?.let { raw(it, "raw").takeIf { v -> v > 0 } ?: it["raw"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0 } ?: 0.0
        val earningsAvg = ((cal?.get("earnings") as? JsonObject)?.get("earningsAverage"))?.let { e -> if (e is JsonObject) raw(e, "raw") else e.jsonPrimitive.content.toDoubleOrNull() ?: 0.0 } ?: 0.0

        return Profile(
            financialCurrency = str(fin, "financialCurrency").ifBlank { str(earn, "financialCurrency") },
            description = str(ap, "longBusinessSummary"),
            sector = str(ap, "sector"),
            industry = str(ap, "industry"),
            country = str(ap, "country"),
            website = str(ap, "website"),
            employees = raw(ap, "fullTimeEmployees").toInt(),
            targetPrice = raw(fin, "targetMeanPrice"),
            targetLow = raw(fin, "targetLowPrice"),
            targetHigh = raw(fin, "targetHighPrice"),
            recommendation = str(fin, "recommendationKey"),
            recommendationMean = raw(fin, "recommendationMean"),
            analystCount = raw(fin, "numberOfAnalystOpinions").toInt(),
            eps = raw(key, "trailingEps"),
            forwardPe = raw(key, "forwardPE"),
            priceToBook = raw(key, "priceToBook"),
            beta = raw(key, "beta"),
            dividendYield = raw(sum, "dividendYield") * 100.0,
            dividendRate = raw(sum, "dividendRate"),
            payoutRatio = raw(sum, "payoutRatio") * 100.0,
            enterpriseValue = raw(key, "enterpriseValue"),
            revenue = raw(fin, "totalRevenue"),
            ebitda = raw(fin, "ebitda"),
            netIncome = yearly.lastOrNull()?.earnings ?: 0.0,
            revenueGrowth = raw(fin, "revenueGrowth") * 100.0,
            earningsGrowth = raw(fin, "earningsGrowth") * 100.0,
            grossMargin = raw(fin, "grossMargins") * 100.0,
            operatingMargin = raw(fin, "operatingMargins") * 100.0,
            profitMargin = raw(fin, "profitMargins") * 100.0,
            totalCash = raw(fin, "totalCash"),
            totalDebt = raw(fin, "totalDebt"),
            freeCashflow = raw(fin, "freeCashflow"),
            operatingCashflow = raw(fin, "operatingCashflow"),
            returnOnEquity = raw(fin, "returnOnEquity") * 100.0,
            totalAssets = raw(bs, "totalAssets"),
            totalLiabilities = raw(bs, "totalLiab"),
            equity = raw(bs, "totalStockholderEquity"),
            earningsDate = earningsDate.toLong() * 1000L,
            earningsEstimate = earningsAvg,
            exDividendDate = raw(cal, "exDividendDate").toLong() * 1000L,
            dividendDate = raw(cal, "dividendDate").toLong() * 1000L,
            yearly = yearly,
            quarterly = quarterly,
            epsHistory = eps,
            shortName = str(price, "shortName"),
            longName = str(price, "longName"),
        )
    }

    // ------------------------------------------------------------ Nachrichten

    data class NewsItem(val title: String, val link: String, val published: Long, val summary: String = "", val source: String = "") {
        val id: String get() = link.ifBlank { title }
    }

    private val itemRegex = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL)
    private val titleRegex = Regex("<title>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?</title>", RegexOption.DOT_MATCHES_ALL)
    private val linkRegex = Regex("<link>(.*?)</link>", RegexOption.DOT_MATCHES_ALL)
    private val dateRegex = Regex("<pubDate>(.*?)</pubDate>", RegexOption.DOT_MATCHES_ALL)
    private val descRegex = Regex("<description>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?</description>", RegexOption.DOT_MATCHES_ALL)

    // Die Kodierung liest Rss.text aus der XML-Kopfzeile. Nur erfolgreiche Antworten, hoechstens 2 MB.
    // Es geht nichts mit, was die Person kennzeichnet.
    suspend fun feedBytes(url: String): ByteArray? =
        bytes(url, "application/rss+xml, application/atom+xml, application/xml, text/xml;q=0.9, */*;q=0.8", 2_000_000)

    // HTTP-Status (0 ohne Verbindung) und Text, auch bei Fehlern. kopf: Kopfzeilen mit Namen in Kleinbuchstaben.
    data class Antwort(val code: Int, val text: String?, val kopf: Map<String, String> = emptyMap()) {
        val ok: Boolean get() = code in 200..299 && text != null
    }

    // GET, mit formular als POST (application/x-www-form-urlencoded), kopf fuer zusaetzliche Kopfzeilen.
    // Fuer Broker auch mit anderer methode, einem JSON-Koerper json oder einem fertig kodierten Formular formText.
    suspend fun anfrage(
        url: String, kopf: Map<String, String> = emptyMap(), formular: List<Pair<String, String>>? = null, max: Int = 4_000_000,
        methode: String? = null, json: String? = null, formText: String? = null,
    ): Antwort =
        suspendCancellableCoroutine { continuation ->
            val request = runCatching {
                val b = Request.Builder().url(url).header("Accept", "application/json")
                kopf.forEach { (k, v) -> b.header(k, v) }
                val koerper = when {
                    formular != null -> okhttp3.FormBody.Builder().apply { formular.forEach { (k, v) -> add(k, v) } }.build()
                    json != null -> json.toRequestBody("application/json; charset=utf-8".toMediaType())
                    // Fertig kodiertes Formular, wenn die Signatur ueber genau diesen Text gerechnet wird (Kraken)
                    formText != null -> formText.toRequestBody("application/x-www-form-urlencoded".toMediaType())
                    else -> null
                }
                val m = methode ?: if (koerper != null) "POST" else "GET"
                b.method(m, koerper ?: if (m == "POST" || m == "PUT" || m == "PATCH") ByteArray(0).toRequestBody(null) else null)
                b.build()
            }.getOrNull()
            if (request == null) {
                continuation.resume(Antwort(0, null))
                return@suspendCancellableCoroutine
            }
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resume(Antwort(0, null))
                }
                override fun onResponse(call: Call, response: Response) {
                    val a = response.use { r ->
                        try { Antwort(r.code, r.body?.byteStream()?.use { s -> lesenBis(s, max) }?.toString(Charsets.UTF_8)) } catch (_: IOException) { Antwort(r.code, null) }
                    }
                    if (continuation.isActive) continuation.resume(a)
                }
            })
        }

    // Nur erfolgreiche Antworten, hoechstens max Bytes.
    suspend fun datei(url: String, max: Int = 8_000_000): ByteArray? = bytes(url, "*/*", max)

    private suspend fun bytes(url: String, accept: String, max: Int): ByteArray? = suspendCancellableCoroutine { continuation ->
        val request = runCatching {
            Request.Builder().url(url).header("Accept", accept).build()
        }.getOrNull()
        if (request == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resume(null)
            }
            override fun onResponse(call: Call, response: Response) {
                val body = response.use { r ->
                    try {
                        if (!r.isSuccessful) null
                        else r.body?.byteStream()?.use { s -> lesenBis(s, max) }
                    } catch (_: IOException) { null }
                }
                if (continuation.isActive) continuation.resume(body)
            }
        })
    }

    // readNBytes gibt es erst ab Android 13.
    private fun lesenBis(s: java.io.InputStream, max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16_384)
        while (out.size() < max) {
            val n = s.read(buf, 0, minOf(buf.size, max - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    // Schlagzeilen aus dem oeffentlichen RSS-Feed in der gewuenschten Sprache, braucht keinen crumb.
    suspend fun news(symbol: String, lang: String = "de", limit: Int = 10): List<NewsItem> {
        val region = if (lang == "de") "region=DE&lang=de-DE" else "region=US&lang=en-US"
        val xml = get("https://feeds.finance.yahoo.com/rss/2.0/headline?s=${enc(symbol)}&$region") ?: return emptyList()
        val fmt = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", java.util.Locale.ENGLISH)
        return itemRegex.findAll(xml).mapNotNull { m ->
            val block = m.groupValues[1]
            val title = titleRegex.find(block)?.groupValues?.get(1)?.trim()?.let(::unescape) ?: return@mapNotNull null
            if (title.isBlank() || istSammelmeldung(title)) return@mapNotNull null
            val link = linkRegex.find(block)?.groupValues?.get(1)?.trim().orEmpty().substringBefore("?.tsrc")
            val ts = dateRegex.find(block)?.groupValues?.get(1)?.trim()
                ?.let { runCatching { fmt.parse(it)?.time }.getOrNull() } ?: 0L
            val desc = descRegex.find(block)?.groupValues?.get(1)?.trim()?.let(::unescape)?.replace(Regex("<[^>]+>"), "")?.trim().orEmpty()
            val source = runCatching { java.net.URI(link).host.removePrefix("www.") }.getOrDefault("")
            NewsItem(title, link, ts ?: 0L, desc, source)
        }.take(limit).toList()
    }

    private fun unescape(s: String): String = s
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'").replace("&nbsp;", " ")

    // ------------------------------------------------------------ Suche

    suspend fun search(query: String): List<SearchHit> {
        if (query.isBlank()) return emptyList()
        val body = get("$Q2/v1/finance/search?q=${enc(query)}&quotesCount=15&newsCount=0&listsCount=0&enableFuzzyQuery=false")
            ?: return emptyList()
        val env = runCatching { json.decodeFromString<SearchEnvelope>(body) }.getOrNull() ?: return emptyList()
        return env.quotes.orEmpty().mapNotNull { hit ->
            val sym = hit.symbol ?: return@mapNotNull null
            val type = hit.quoteType?.uppercase() ?: ""
            if (type !in setOf("EQUITY", "ETF", "CRYPTOCURRENCY", "INDEX", "MUTUALFUND")) return@mapNotNull null
            SearchHit(sym, hit.longname ?: hit.shortname ?: sym, type, hit.exchDisp ?: "", hit.sector ?: "", hit.industry ?: "")
        }
    }

    data class SearchHit(val symbol: String, val name: String, val type: String, val exchange: String, val sector: String = "", val industry: String = "")

    // Alle Notierungen einer Aktie ueber den Yahoo-Lookup ohne crumb, etwa fuer die deutsche Zweitnotiz.
    // Liefert Symbol, Boersenkuerzel wie FRA oder STU und letzten Kurs.
    suspend fun lookup(query: String): List<Zweitnotiz.Kandidat> {
        if (query.isBlank()) return emptyList()
        val body = get("$Q1/v1/finance/lookup?query=${enc(query)}&type=equity&count=50&start=0") ?: return emptyList()
        val env = runCatching { json.decodeFromString<LookupEnvelope>(body) }.getOrNull() ?: return emptyList()
        return env.finance?.result.orEmpty().flatMap { it.documents.orEmpty() }.mapNotNull { d ->
            val s = d.symbol ?: return@mapNotNull null
            Zweitnotiz.Kandidat(s, d.exchange.orEmpty(), d.regularMarketPrice?.raw ?: 0.0)
        }
    }

    @Serializable
    private data class LookupEnvelope(@SerialName("finance") val finance: LookupFinance? = null)

    @Serializable
    private data class LookupFinance(@SerialName("result") val result: List<LookupResult>? = null)

    @Serializable
    private data class LookupResult(@SerialName("documents") val documents: List<LookupDoc>? = null)

    @Serializable
    private data class LookupDoc(
        @SerialName("symbol") val symbol: String? = null,
        @SerialName("exchange") val exchange: String? = null,
        @SerialName("regularMarketPrice") val regularMarketPrice: RawValue? = null,
    )

    @Serializable
    private data class RawValue(@SerialName("raw") val raw: Double? = null)

    // ------------------------------------------------------------ Optionsumsatz (Long/Short)

    // day ist der Zeitpunkt des letzten Handels in ms.
    data class OptionVolumes(val calls: Long, val puts: Long, val day: Long) {
        val total: Long get() = calls + puts

        // Calls zaehlen als Long, Puts als Short.
        val longPct: Double get() = if (total > 0) calls * 100.0 / total else 0.0
    }

    // Summe der Calls und Puts an US-Boersen ueber die naechsten expiries Faelligkeiten, dort liegt fast der
    // ganze Umsatz. Braucht den crumb.
    suspend fun optionVolumes(symbol: String, expiries: Int = 4): OptionVolumes? {
        val c = ensureCrumb() ?: return null
        val first = get("$Q1/v7/finance/options/${enc(symbol)}?crumb=${enc(c)}") ?: run { crumb = null; return null }
        val (dates, erste) = parseOptionChain(first) ?: return null
        val teile = arrayListOf(erste)
        for (d in dates.drop(1).take(expiries - 1)) {
            val body = get("$Q1/v7/finance/options/${enc(symbol)}?date=$d&crumb=${enc(c)}") ?: break
            teile += parseOptionChain(body)?.second ?: break
        }
        return sumOptionVolumes(teile).takeIf { it.total > 0 }
    }

    // Kontrakte der naechsten faelligkeiten Termine mit Basispreis, Kursen, Umsatz, offenen Kontrakten und
    // impliziter Volatilitaet, dazu der Kurs des Basiswerts. Braucht den crumb.
    suspend fun optionskette(symbol: String, faelligkeiten: Int = 6): Pair<Double, List<de.troetenrepublik.app.engine.Optionsanalyse.Kontrakt>>? {
        val c = ensureCrumb() ?: return null
        val erste = get("$Q1/v7/finance/options/${enc(symbol)}?crumb=${enc(c)}") ?: run { crumb = null; return null }
        fun lesen(body: String): Triple<Double, List<Long>, List<de.troetenrepublik.app.engine.Optionsanalyse.Kontrakt>>? {
            val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
            val r = (root["optionChain"]?.jsonObject?.get("result") as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
            val kurs = (r["quote"] as? JsonObject)?.get("regularMarketPrice")?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            val termine = (r["expirationDates"] as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.content.toLongOrNull() }
            val o = (r["options"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return Triple(kurs, termine, emptyList())
            fun zahl(k: JsonObject, f: String) = (k[f] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()
            fun liste(key: String, call: Boolean) = (o[key] as? JsonArray).orEmpty().mapNotNull { e ->
                val k = e as? JsonObject ?: return@mapNotNull null
                val strike = zahl(k, "strike") ?: return@mapNotNull null
                val verfall = zahl(k, "expiration")?.toLong() ?: return@mapNotNull null
                de.troetenrepublik.app.engine.Optionsanalyse.Kontrakt(
                    call, strike, verfall, zahl(k, "lastPrice") ?: 0.0, zahl(k, "bid") ?: 0.0, zahl(k, "ask") ?: 0.0,
                    zahl(k, "volume")?.toLong() ?: 0L, zahl(k, "openInterest")?.toLong() ?: 0L, zahl(k, "impliedVolatility") ?: 0.0,
                )
            }
            return Triple(kurs, termine, liste("calls", true) + liste("puts", false))
        }
        val (kurs, termine, kontrakte) = lesen(erste) ?: return null
        val alle = ArrayList(kontrakte)
        for (d in termine.drop(1).take(faelligkeiten - 1)) {
            val body = get("$Q1/v7/finance/options/${enc(symbol)}?date=$d&crumb=${enc(c)}") ?: break
            alle += lesen(body)?.third ?: break
        }
        return if (alle.isEmpty() || kurs <= 0) null else kurs to alle
    }

    // Alle Faelligkeitstermine und die Kontrakte der abgefragten Faelligkeit.
    internal fun parseOptionChain(body: String): Pair<List<Long>, List<OptionTrade>>? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val r = (root["optionChain"]?.jsonObject?.get("result") as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        val dates = (r["expirationDates"] as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.content.toLongOrNull() }
        val o = (r["options"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return dates to emptyList()
        fun trades(key: String, call: Boolean): List<OptionTrade> = (o[key] as? JsonArray).orEmpty().mapNotNull { e ->
            val k = e as? JsonObject ?: return@mapNotNull null
            val vol = k["volume"]?.jsonPrimitive?.content?.toDoubleOrNull()?.toLong() ?: return@mapNotNull null
            val t = k["lastTradeDate"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@mapNotNull null
            if (vol <= 0) null else OptionTrade(call, vol, t * 1000L)
        }
        return dates to (trades("calls", true) + trades("puts", false))
    }

    // time ist der Zeitpunkt des letzten Handels in ms.
    data class OptionTrade(val call: Boolean, val volume: Long, val time: Long)

    // Nur Kontrakte vom Tag des juengsten Handels zaehlen, aeltere tragen noch den Umsatz eines frueheren Tages.
    internal fun sumOptionVolumes(teile: List<List<OptionTrade>>): OptionVolumes {
        val alle = teile.flatten()
        val juengster = alle.maxOfOrNull { it.time } ?: return OptionVolumes(0, 0, 0)
        val heute = alle.filter { juengster - it.time < 18 * 3_600_000L }
        return OptionVolumes(heute.filter { it.call }.sumOf { it.volume }, heute.filter { !it.call }.sumOf { it.volume }, juengster)
    }

    // ------------------------------------------------------------ EZB-Einlagesatz

    data class EcbRate(val ratePct: Double, val since: String)

    // Einlagefazilitaet aus dem oeffentlichen Datenportal der EZB, CSV ohne Schluessel.
    suspend fun ecbDepositRate(): EcbRate? {
        val csv = get("https://data-api.ecb.europa.eu/service/data/FM/D.U2.EUR.4F.KR.DFR.LEV?startPeriod=2020-01-01&format=csvdata")
            ?: return null
        val lines = csv.lines().filter { it.isNotBlank() }
        if (lines.size < 2) return null
        val header = lines[0].split(",")
        val iPeriod = header.indexOf("TIME_PERIOD"); val iValue = header.indexOf("OBS_VALUE")
        if (iPeriod < 0 || iValue < 0) return null
        var rate: Double? = null; var since = ""
        for (l in lines.drop(1)) {
            val cols = l.split(",")
            val v = cols.getOrNull(iValue)?.toDoubleOrNull() ?: continue
            val p = cols.getOrNull(iPeriod) ?: continue
            if (rate == null || v != rate) { rate = v; since = p }
        }
        return rate?.let { EcbRate(it, since) }
    }

    // ------------------------------------------------------------ Wikidata / Wikipedia

    // Stammdaten zu einem Unternehmen: Wikidata liefert ISIN (P946), Logo (P154), Website (P856)
    // und die Artikeltitel; Wikipedia liefert die Kurzbeschreibung in Deutsch und Englisch.
    suspend fun companyInfo(symbol: String, name: String): CompanyInfo {
        val now = System.currentTimeMillis()
        val candidates = nameCandidates(name)
        var entity: JsonObject? = null
        for (q in candidates) {
            val body = get("https://www.wikidata.org/w/api.php?action=wbsearchentities&search=${enc(q)}&language=en&limit=5&format=json&type=item")
                ?: continue
            val hits = runCatching { json.parseToJsonElement(body).jsonObject["search"] as? JsonArray }.getOrNull().orEmpty()
            for (h in hits.take(4)) {
                val id = (h as? JsonObject)?.get("id")?.jsonPrimitive?.content ?: continue
                val e = get("https://www.wikidata.org/w/api.php?action=wbgetentities&ids=$id&props=claims|sitelinks&format=json")
                    ?.let { runCatching { json.parseToJsonElement(it).jsonObject["entities"]?.jsonObject?.get(id)?.jsonObject }.getOrNull() }
                    ?: continue
                val claims = e["claims"] as? JsonObject
                if (claims?.containsKey("P946") == true || claims?.containsKey("P249") == true) { entity = e; break }
            }
            if (entity != null) break
        }
        val e = entity ?: return CompanyInfo(symbol, fetchedAt = now, missing = true)
        val claims = e["claims"] as? JsonObject
        fun first(prop: String): String? = ((claims?.get(prop) as? JsonArray)?.firstOrNull() as? JsonObject)
            ?.get("mainsnak")?.jsonObject?.get("datavalue")?.jsonObject?.get("value")?.let { v ->
                if (v is JsonObject) v["text"]?.jsonPrimitive?.content else v.jsonPrimitive.content
            }
        val isin = first("P946").orEmpty()
        val logo = first("P154")?.let { "https://commons.wikimedia.org/wiki/Special:FilePath/${enc(it.replace(' ', '_'))}?width=256" }.orEmpty()
        val site = first("P856").orEmpty()
        val links = e["sitelinks"] as? JsonObject
        val de = (links?.get("dewiki") as? JsonObject)?.get("title")?.jsonPrimitive?.content
        val en = (links?.get("enwiki") as? JsonObject)?.get("title")?.jsonPrimitive?.content
        val descDe = de?.let { wikiSummary("de", it) }.orEmpty()
        val descEn = en?.let { wikiSummary("en", it) }.orEmpty()
        return CompanyInfo(
            symbol, isin, logo, site, descDe, descEn, now, missing = false,
            articleDe = de?.let { wikiArticle("de", it) }.orEmpty(), articleEn = en?.let { wikiArticle("en", it) }.orEmpty(),
        )
    }

    fun wikiArticle(lang: String, title: String): String = "https://$lang.wikipedia.org/wiki/${enc(title.replace(' ', '_'))}"

    // Erster Absatz eines Wikipedia-Artikels.
    suspend fun wikiSummary(lang: String, title: String): String? {
        val body = get("https://$lang.wikipedia.org/api/rest_v1/page/summary/${enc(title.replace(' ', '_'))}") ?: return null
        return runCatching { json.parseToJsonElement(body).jsonObject["extract"]?.jsonPrimitive?.content }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    // Suchbegriffe: voller Name, ohne Rechtsform, erstes Wort.
    private fun nameCandidates(name: String): List<String> {
        val legal = Regex("\\b(Inc\\.?|Corp\\.?|Corporation|Co\\.?|Ltd\\.?|Limited|plc|PLC|AG|SE|N\\.?V\\.?|S\\.?A\\.?|SA|A/S|ASA|AB|Oyj|S\\.?p\\.?A\\.?|KGaA|GmbH|Holdings?|Group|Vz\\.?|St\\.?|\\(The\\)|The)\\b\\.?", RegexOption.IGNORE_CASE)
        val clean = name.replace(legal, " ").replace(Regex("[,&]"), " ").replace(Regex("\\s+"), " ").trim()
        val out = linkedSetOf<String>()
        if (name.isNotBlank()) out += name.trim()
        if (clean.isNotBlank()) out += clean
        clean.split(" ").firstOrNull()?.takeIf { it.length >= 3 }?.let { out += it }
        return out.toList()
    }

    // ------------------------------------------------------------ JSON-Modelle

    @Serializable
    private data class QuoteEnvelope(@SerialName("quoteResponse") val quoteResponse: QuoteResult? = null)

    @Serializable
    private data class QuoteResult(@SerialName("result") val result: List<YahooQuote>? = null)

    @Serializable
    private data class YahooQuote(
        @SerialName("symbol") val symbol: String? = null,
        @SerialName("shortName") val shortName: String? = null,
        @SerialName("longName") val longName: String? = null,
        @SerialName("currency") val currency: String? = null,
        @SerialName("exchange") val exchange: String? = null,
        @SerialName("fullExchangeName") val fullExchangeName: String? = null,
        @SerialName("marketState") val marketState: String? = null,
        @SerialName("regularMarketPrice") val price: Double? = null,
        @SerialName("regularMarketPreviousClose") val prevClose: Double? = null,
        @SerialName("regularMarketOpen") val open: Double? = null,
        @SerialName("regularMarketDayHigh") val dayHigh: Double? = null,
        @SerialName("regularMarketDayLow") val dayLow: Double? = null,
        @SerialName("regularMarketVolume") val volume: Double? = null,
        @SerialName("averageDailyVolume10Day") val avgVolume10: Double? = null,
        @SerialName("fiftyTwoWeekHigh") val high52: Double? = null,
        @SerialName("fiftyTwoWeekLow") val low52: Double? = null,
        @SerialName("marketCap") val marketCap: Double? = null,
        @SerialName("trailingPE") val pe: Double? = null,
        @SerialName("preMarketPrice") val preMarketPrice: Double? = null,
        @SerialName("postMarketPrice") val postMarketPrice: Double? = null,
        @SerialName("regularMarketTime") val regularTime: Long? = null,
        @SerialName("preMarketTime") val preTime: Long? = null,
        @SerialName("postMarketTime") val postTime: Long? = null,
    ) {
        fun toQuote(now: Long): Quote? {
            val s = symbol ?: return null
            val regular = price ?: return null
            // Vor- und nachboerslich zaehlt der zuletzt gehandelte Kurs.
            val pre = marketState.equals("PRE", true) || marketState.equals("PREPRE", true)
            val post = marketState.equals("POST", true) || marketState.equals("POSTPOST", true)
            val p = when {
                post -> postMarketPrice?.takeIf { it > 0 } ?: regular
                pre -> preMarketPrice?.takeIf { it > 0 } ?: regular
                else -> regular
            }
            // Zeitpunkt des verwendeten Kurses in Sekunden
            val zeit = when {
                post && (postMarketPrice ?: 0.0) > 0 -> postTime ?: regularTime
                pre && (preMarketPrice ?: 0.0) > 0 -> preTime ?: regularTime
                else -> regularTime
            }
            // Vor Handelsbeginn in den USA ist der letzte regulaere Schluss die Bezugsgroesse. regularMarketPreviousClose
            // ist dann noch der Schluss davor, sonst zaehlte die Tagesaenderung die Bewegung des Vortags doppelt.
            val reference = if (pre) regular else (prevClose ?: regular)
            return Quote(
                symbol = s,
                name = longName ?: shortName ?: Symbols.name(s),
                price = p,
                prevClose = reference,
                open = open ?: 0.0,
                dayHigh = dayHigh ?: 0.0,
                dayLow = dayLow ?: 0.0,
                weekHigh52 = high52 ?: 0.0,
                weekLow52 = low52 ?: 0.0,
                marketCap = marketCap ?: 0.0,
                peRatio = pe ?: 0.0,
                volume = volume ?: 0.0,
                avgVolume10 = avgVolume10 ?: 0.0,
                nativeCurrency = currency ?: "EUR",
                nativePrice = p,
                regularPrice = regular,
                exchange = fullExchangeName ?: exchange ?: "",
                marketState = marketState ?: "",
                updatedAt = now,
                marketTime = (zeit ?: 0L) * 1000L,
            )
        }
    }

    @Serializable
    private data class SparkEnvelope(@SerialName("spark") val spark: SparkResult? = null)

    @Serializable
    private data class SparkResult(@SerialName("result") val result: List<SparkWert>? = null)

    @Serializable
    private data class SparkWert(@SerialName("symbol") val symbol: String = "", @SerialName("response") val response: List<ChartData>? = null)

    @Serializable
    private data class ChartEnvelope(@SerialName("chart") val chart: ChartResult? = null)

    @Serializable
    private data class ChartResult(@SerialName("result") val result: List<ChartData>? = null)

    @Serializable
    private data class ChartData(
        @SerialName("meta") val meta: ChartMeta? = null,
        @SerialName("timestamp") val timestamp: List<Long>? = null,
        @SerialName("indicators") val indicators: Indicators? = null,
        @SerialName("events") val events: ChartEvents? = null,
    )

    @Serializable
    private data class ChartEvents(@SerialName("dividends") val dividends: Map<String, DividendJson>? = null)

    @Serializable
    private data class DividendJson(
        @SerialName("amount") val amount: Double? = null,
        @SerialName("date") val date: Long? = null,
    )

    @Serializable
    private data class TradingPeriod(@SerialName("start") val start: Long? = null, @SerialName("end") val end: Long? = null)

    @Serializable
    private data class TradingPeriods(
        @SerialName("pre") val pre: TradingPeriod? = null,
        @SerialName("regular") val regular: TradingPeriod? = null,
        @SerialName("post") val post: TradingPeriod? = null,
    )

    @Serializable
    private data class ChartMeta(
        @SerialName("symbol") val symbol: String? = null,
        @SerialName("currency") val currency: String? = null,
        @SerialName("exchangeName") val exchangeName: String? = null,
        @SerialName("shortName") val shortName: String? = null,
        @SerialName("longName") val longName: String? = null,
        @SerialName("regularMarketPrice") val regularMarketPrice: Double? = null,
        @SerialName("chartPreviousClose") val chartPreviousClose: Double? = null,
        @SerialName("previousClose") val previousClose: Double? = null,
        @SerialName("regularMarketDayHigh") val regularMarketDayHigh: Double? = null,
        @SerialName("regularMarketDayLow") val regularMarketDayLow: Double? = null,
        @SerialName("regularMarketVolume") val regularMarketVolume: Double? = null,
        @SerialName("regularMarketTime") val regularMarketTime: Long? = null,
        @SerialName("fiftyTwoWeekHigh") val fiftyTwoWeekHigh: Double? = null,
        @SerialName("fiftyTwoWeekLow") val fiftyTwoWeekLow: Double? = null,
        @SerialName("currentTradingPeriod") val currentTradingPeriod: TradingPeriods? = null,
    )

    @Serializable
    private data class Indicators(@SerialName("quote") val quote: List<Ohlc>? = null)

    @Serializable
    private data class Ohlc(
        @SerialName("open") val open: List<Double?>? = null,
        @SerialName("high") val high: List<Double?>? = null,
        @SerialName("low") val low: List<Double?>? = null,
        @SerialName("close") val close: List<Double?>? = null,
        @SerialName("volume") val volume: List<Double?>? = null,
    )

    @Serializable
    private data class SearchEnvelope(@SerialName("quotes") val quotes: List<SearchQuote>? = null)

    @Serializable
    private data class SearchQuote(
        @SerialName("symbol") val symbol: String? = null,
        @SerialName("shortname") val shortname: String? = null,
        @SerialName("longname") val longname: String? = null,
        @SerialName("quoteType") val quoteType: String? = null,
        @SerialName("exchDisp") val exchDisp: String? = null,
        @SerialName("sector") val sector: String? = null,
        @SerialName("industry") val industry: String? = null,
    )
}
