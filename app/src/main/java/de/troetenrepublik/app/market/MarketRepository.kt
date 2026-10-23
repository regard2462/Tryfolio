package de.troetenrepublik.app.market

import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.data.Range
import de.troetenrepublik.app.data.Series
import de.troetenrepublik.app.engine.ExchangeRates
import de.troetenrepublik.app.engine.MarketHours
import de.troetenrepublik.app.engine.Ticks
import de.troetenrepublik.app.engine.Zeitraum
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.abs

// Haelt die Live-Kurse. Alle Preise werden auf EUR umgerechnet, damit Depot und Orders in einer
// Waehrung rechnen; der Originalkurs bleibt in Quote.nativePrice.
// Xetra-Werte (.DE) kommen wenn moeglich von Frankfurt (.F), das von 8 bis 22 Uhr handelt, Xetra nur bis 17:30.
// Findet sich keine Frankfurter Notierung, wird nach einer halben Stunde erneut geprueft.
// Auslaendische Aktien bekommen eine deutsche Zweitnotiz: ausserhalb der Handelszeit ihres Heimatplatzes gilt
// deren juengerer Kurs, und der Tageschart ergaenzt die Zeit davor und danach. Die Zuordnung steht in zweitDatei.
class MarketRepository(private val api: MarketApi, private val scope: CoroutineScope, private val zweitDatei: File? = null) {

    private val _quotes = MutableStateFlow<Map<String, Quote>>(emptyMap())
    val quotes: StateFlow<Map<String, Quote>> = _quotes.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    // Fehlercode: null, "UNREACHABLE", "FX_MISSING", "PARTIAL".
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Symbole, die im Hintergrund aktuell gehalten werden.
    private val tracked = MutableStateFlow<Set<String>>(emptySet())

    // Einheiten der Fremdwaehrung je 1 EUR.
    private val fx = MutableStateFlow(mapOf("EUR" to 1.0))

    // Fuer die Umrechnung fremder Betraege.
    val fxKurse: StateFlow<Map<String, Double>> = fx.asStateFlow()

    // 0 = noch unbekannt.
    val usdJeEuro: StateFlow<Double> = fx.map { it["USD"] ?: 0.0 }.stateIn(scope, SharingStarted.Eagerly, 0.0)

    // Devisenkurse laden, falls noch keine oder nur alte da sind, und den Dollarkurs liefern.
    suspend fun dollarKurs(): Double? {
        runCatching { refreshFx() }
        return fx.value["USD"]?.takeIf { it > 0 }
    }

    private val candleCache = mutableMapOf<String, Pair<Long, Series>>()

    // In den Kerzenspeicher legen (unter candleLock); bei mehr als 300 Eintraegen fallen die aeltesten weg.
    private fun kerzenMerken(key: String, data: Series) {
        candleCache[key] = System.currentTimeMillis() to data
        if (candleCache.size > 300) candleCache.entries.sortedBy { it.value.first }.take(100).map { it.key }.forEach { candleCache.remove(it) }
    }
    private val candleLock = Mutex()

    // App-Symbol -> Datensymbol (z. B. SAP.DE -> SAP.F); Wert gleich Schluessel = keine Umleitung.
    private val alias = java.util.concurrent.ConcurrentHashMap<String, String>()

    // Wann die Frankfurter Notierung eines Xetra-Werts zuletzt geprueft wurde (ms).
    private val aliasZeit = java.util.concurrent.ConcurrentHashMap<String, Long>()

    // Auslaendische Aktie -> deutsche Zweitnotiz (z. B. NOVO-B.CO -> NOV.F); leer = keine gefunden.
    private val zweit = java.util.concurrent.ConcurrentHashMap<String, ZweitEintrag>()
    private val zweitSuche = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val zweitJson = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class ZweitEintrag(val z: String, val t: Long)

    init {
        // Gemerkte Zuordnungen laden; gefundene gelten 30 Tage, erfolglose Suchen werden nach einem Tag wiederholt
        runCatching {
            val f = zweitDatei?.takeIf { it.exists() } ?: return@runCatching
            zweit.putAll(zweitJson.decodeFromString<Map<String, ZweitEintrag>>(f.readText()))
        }
        // Zweitnotizen sind am Kurs gepruefte Notierungen derselben Aktie, also auch fuer das Logo gleich
        zweit.forEach { (s, e) -> if (e.z.isNotEmpty()) Gleichwerte.verbinden(setOf(s, e.z), merken = false) }
    }

    // Notierungen derselben Aktie, die Yahoo ohne Verzoegerung liefert: hoechstens zwei aus Gleichwerte, nicht an
    // einem deutschen Platz. Nur fuer verzoegerte Werte und fuer Werte mit Zweitnotiz, die sonst stehen bleiben.
    private fun frischQuellen(symbol: String): List<String> {
        val fam = Gleichwerte.familien.value[symbol] ?: return emptyList()
        val daten = dataSymbol(symbol)
        return fam.filter { it != symbol && it != daten && !Zweitnotiz.verzoegert(it) }.sorted().take(2)
    }

    // Zuletzt genutzte frischere Notierung je Wert (Kuerzel und Rohkurs), fuer den Tageschart.
    private val frischGenutzt = java.util.concurrent.ConcurrentHashMap<String, Quote>()

    private fun frischAnwenden(key: String, q: Quote, roh: Map<String, Quote>): Quote {
        val andere = frischQuellen(key).mapNotNull { m -> roh[m]?.let { r -> ExchangeRates.toEur(r, fx.value)?.let { r to it } } }
        if (andere.isEmpty()) { frischGenutzt.remove(key); return q }
        val neu = Zweitnotiz.frischer(q, andere.map { it.second })
        val quelle = andere.firstOrNull { it.second.marketTime == neu.marketTime && neu !== q }?.first
        if (quelle != null) frischGenutzt[key] = quelle else frischGenutzt.remove(key)
        return neu
    }

    fun zweitnotiz(symbol: String): String? = zweit[symbol]?.z?.takeIf { it.isNotEmpty() }

    private fun zweitFaellig(symbol: String, now: Long): Boolean {
        if (!Zweitnotiz.braucht(symbol, Symbols.typeOf(symbol))) return false
        val e = zweit[symbol] ?: return true
        return now - e.t > if (e.z.isEmpty()) 86_400_000L else 30L * 86_400_000L
    }

    // Fehlende Zweitnotizen im Hintergrund suchen; eur sind die Heimatkurse in EUR (zur Pruefung des Kurses).
    private fun zweitSuchen(symbols: List<String>, eur: Map<String, Quote>) {
        val now = System.currentTimeMillis()
        val offen = symbols.filter { zweitFaellig(it, now) && eur[it] != null && zweitSuche.add(it) }
        if (offen.isEmpty()) return
        scope.launch {
            var geaendert = false
            try {
                for (s in offen) {
                    val heimat = eur[s] ?: continue
                    val begriff = Zweitnotiz.suchbegriff(Symbols.name(s)).ifBlank { s }
                    val treffer = runCatching { api.lookup(begriff) }.getOrDefault(emptyList())
                    // Ohne Antwort (kein Netz) nichts merken, beim naechsten Abruf erneut versuchen
                    if (treffer.isEmpty()) continue
                    val gefunden = Zweitnotiz.waehleNotierung(treffer, heimat.price).orEmpty()
                    zweit[s] = ZweitEintrag(gefunden, System.currentTimeMillis())
                    if (gefunden.isNotEmpty()) Gleichwerte.verbinden(setOf(s, gefunden), merken = false)
                    geaendert = true
                }
            } finally {
                zweitSuche.removeAll(offen.toSet())
                if (geaendert) runCatching { zweitDatei?.writeText(zweitJson.encodeToString(HashMap(zweit))) }
            }
        }
    }

    private val refreshLock = Mutex()
    private val fxLock = Mutex()
    private var pollingInterval = 15_000L
    private var pollJob: kotlinx.coroutines.Job? = null

    fun track(symbols: Collection<String>) {
        if (symbols.isEmpty()) return
        tracked.update { it + symbols.filter { symbol -> symbol.isNotBlank() } }
    }

    fun setTracked(symbols: Collection<String>) {
        tracked.value = symbols.filter { it.isNotBlank() }.toSet()
    }

    fun dataSymbol(symbol: String): String = alias[symbol] ?: symbol

    // Startet die Aktualisierungsschleife. Ausserhalb der Handelszeiten reicht ein langsamer Takt.
    @Synchronized
    fun startPolling(intervalMs: Long = 15_000L) {
        val safeInterval = intervalMs.coerceIn(5_000L, 300_000L)
        if (pollJob?.isActive == true && pollingInterval == safeInterval) return
        pollJob?.cancel()
        pollingInterval = safeInterval
        pollJob = scope.launch {
            while (isActive) {
                try { refresh() } catch (e: CancellationException) { throw e } catch (_: Exception) { _error.value = "UNREACHABLE" }
                val open = _quotes.value.isEmpty() || _quotes.value.values.any { it.isOpen || it.isExtendedHours || it.marketState == "UNKNOWN" }
                delay(if (open) safeInterval else safeInterval * 4)
            }
        }
    }

    @Synchronized
    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    // Verbundener Broker: liefert Kurse, Charts und Suche fuer die Werte, die er fuehrt; null heisst Yahoo.
    @Volatile var kursquelle: de.troetenrepublik.app.market.broker.Kursquelle? = null
        set(value) {
            field = value
            scope.launch { candleLock.withLock { candleCache.clear() } }
            _quotes.value = emptyMap()
        }

    // Kurse vom Broker holen und einsortieren; zurueck kommen die Symbole, die er nicht fuehrt.
    private suspend fun brokerKurse(symbols: List<String>): List<String> {
        val q = kursquelle ?: return symbols
        val beim = symbols.filter { q.fuehrt(it) }
        if (beim.isEmpty()) return symbols
        refreshFx()
        val roh = try { q.kurse(beim) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyMap() }
        val eur = roh.mapNotNull { (s, k) -> ExchangeRates.toEur(k.copy(symbol = s), fx.value)?.let { s to it } }.toMap()
        if (eur.isNotEmpty()) _quotes.update { it + eur }
        _error.value = if (roh.isEmpty()) "UNREACHABLE" else null
        return symbols - beim.toSet()
    }

    suspend fun refresh() = refreshLock.withLock {
        // Werte des Brokers kommen von ihm; nur was er nicht fuehrt (Indizes, Waehrungen), weiter von Yahoo
        val symbols = brokerKurse(tracked.value.toList())
        if (symbols.isEmpty()) return@withLock
        _loading.value = true
        try {
            refreshFx()
            // Ungeprueften Xetra-Werten die Frankfurter Notierung zuordnen. Ohne Treffer nach einer halben Stunde
            // erneut, denn vor dem ersten Frankfurter Umsatz am Morgen faellt die Pruefung negativ aus.
            val now = System.currentTimeMillis()
            val unresolved = symbols.filter {
                it.endsWith(".DE") && (!alias.containsKey(it) || (alias[it] == it && now - (aliasZeit[it] ?: 0L) > 30 * 60_000L))
            }
            val zweitSymbole = symbols.mapNotNull { zweitnotiz(it) }
            val frischSymbole = symbols.flatMap { frischQuellen(it) }
            val request = (symbols.map { dataSymbol(it) } + unresolved.map { it.removeSuffix(".DE") + ".F" } + zweitSymbole + frischSymbole).distinct()
            val fetched = mutableMapOf<String, Quote>()
            // Yahoo mag keine ueberlangen Symbollisten
            request.chunked(40).forEach { chunk -> fetched += api.quotes(chunk) }
            for (s in unresolved) {
                val xetra = fetched[s] ?: continue
                val fra = fetched[s.removeSuffix(".DE") + ".F"]
                // Frankfurt gilt, wenn dort gehandelt wird: Umsatz heute oder ein Kurs der letzten fuenf Tage
                val aktiv = fra != null && (fra.volume > 0 || fra.marketTime > now - 5L * 86_400_000L)
                alias[s] = if (fra != null && aktiv && fra.price > 0 &&
                    abs(fra.price / xetra.price - 1.0) < 0.02 && fra.nativeCurrency == xetra.nativeCurrency) fra.symbol else s
                aliasZeit[s] = now
            }
            val bySymbol = symbols.mapNotNull { s -> (fetched[dataSymbol(s)] ?: fetched[s])?.let { s to it.copy(symbol = s) } }.toMap()
            if (bySymbol.isEmpty()) {
                _error.value = "UNREACHABLE"
            } else {
                val heimat = bySymbol.mapNotNull { (key, quote) ->
                    ExchangeRates.toEur(quote, fx.value)?.let { key to it }
                }.toMap()
                // Ausserhalb der Handelszeit des Heimatplatzes gilt der juengere Kurs der deutschen Zweitnotiz
                val converted = heimat.mapValues { (key, q) ->
                    val z = zweitnotiz(key)?.let { fetched[it] }?.let { ExchangeRates.toEur(it, fx.value) }
                    frischAnwenden(key, Zweitnotiz.waehle(q, z), fetched)
                }
                zweitSuchen(symbols, heimat)
                _error.value = when {
                    converted.size < bySymbol.size -> "FX_MISSING"
                    bySymbol.size < symbols.size -> "PARTIAL"
                    else -> null
                }
                _quotes.update { it + converted }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _error.value = "UNREACHABLE"
        } finally {
            _loading.value = false
        }
    }

    // Einzelnen Wert sofort nachladen, etwa fuer die Detailseite.
    suspend fun refreshOne(symbol: String): Quote? {
        refreshFx()
        if (kursquelle?.fuehrt(symbol) == true) {
            brokerKurse(listOf(symbol))
            return _quotes.value[symbol]
        }
        val z = zweitnotiz(symbol)
        val res = api.quotes((listOfNotNull(dataSymbol(symbol), z) + frischQuellen(symbol)).distinct())
        val q = res[dataSymbol(symbol)]?.copy(symbol = symbol) ?: return _quotes.value[symbol]
        val heimat = ExchangeRates.toEur(q, fx.value)
        if (heimat == null) {
            _error.value = "FX_MISSING"
            return null
        }
        val eur = frischAnwenden(symbol, Zweitnotiz.waehle(heimat, z?.let { res[it] }?.let { ExchangeRates.toEur(it, fx.value) }), res)
        _quotes.update { it + (symbol to eur) }
        zweitSuchen(listOf(symbol), mapOf(symbol to heimat))
        return eur
    }

    private suspend fun refreshFx() = fxLock.withLock {
        if (fx.value.size > 1 && System.currentTimeMillis() - lastFx < 3_600_000L) return@withLock
        val pairs = listOf(
            "EURUSD=X", "EURGBP=X", "EURCHF=X", "EURJPY=X", "EURDKK=X", "EURSEK=X", "EURNOK=X", "EURCAD=X", "EURAUD=X", "EURHKD=X",
            // fuer die Werte der Entdecken-Seiten aus Asien, Osteuropa und Lateinamerika
            "EURKRW=X", "EURTWD=X", "EURINR=X", "EURSGD=X", "EURCNY=X", "EURPLN=X", "EURCZK=X", "EURHUF=X", "EURBRL=X", "EURMXN=X", "EURZAR=X", "EURILS=X", "EURNZD=X",
        )
        val res = api.quotes(pairs)
        if (res.isEmpty()) return@withLock
        val map = fx.value.toMutableMap()
        res.values.forEach { q ->
            val cur = q.symbol.removePrefix("EUR").removeSuffix("=X")
            if (cur.isNotEmpty() && q.price.isFinite() && q.price > 0) map[cur] = q.price
        }
        fx.value = map
        lastFx = System.currentTimeMillis()
    }

    private var lastFx = 0L

    private fun convert(raw: Series, rate: Double): Series =
        if (rate == 1.0) raw else raw.copy(candles = raw.candles.map {
            Candle(it.t, it.open / rate, it.high / rate, it.low / rate, it.close / rate, it.volume)
        })

    // Kurshistorie mit kurzem Cache, damit das Umschalten der Zeitraeume fluessig bleibt.
    suspend fun series(symbol: String, range: Range): Series {
        val key = "$symbol|${range.name}"
        val ttl = if (range == Range.D1) 60_000L else if (range.intraday) 5 * 60_000L else 15 * 60_000L
        val cached = candleLock.withLock {
            candleCache[key]?.takeIf { (ts, data) ->
                System.currentTimeMillis() - ts < ttl && data.candles.isNotEmpty()
            }?.second
        }
        if (cached != null) return cached
        val quote = _quotes.value[symbol] ?: refreshOne(symbol) ?: return Series(emptyList())
        refreshFx()
        val rate = ExchangeRates.rate(quote.nativeCurrency, fx.value) ?: return Series(emptyList())
        // Kerzen vom Broker; liefert er keine (Robinhood), kommen sie von Yahoo
        kursquelle?.takeIf { it.fuehrt(symbol) }?.let { q ->
            val roh = try { q.kerzen(symbol, range) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            if (roh != null) {
                val data = convert(roh.copy(candles = roh.candles.filter { it.close.isFinite() && it.close > 0 }), rate)
                candleLock.withLock { kerzenMerken(key, data) }
                return data
            }
        }
        val raw = api.candles(dataSymbol(symbol), range.yahooRange, range.yahooInterval)
        val valid = raw.candles.filter { it.close.isFinite() && it.close > 0 }
        // Einzelne Fehlkurse, vor allem nachboerslich, ergeben sonst senkrechte Zacken in Kurs- und Depotkurve.
        val heimat = convert(raw.copy(candles = if (range.intraday) Ticks.despike(valid) else valid), rate)
        // Tageschart: die deutsche Zweitnotiz ergaenzt die Zeit vor der Eroeffnung und nach dem Schluss des Heimatplatzes
        val z = if (range == Range.D1) zweitnotiz(symbol) else null
        val gemischt = if (z == null) heimat else {
            val roh = api.candles(z, range.yahooRange, range.yahooInterval)
            val zweitKerzen = Ticks.despike(roh.candles.filter { it.close.isFinite() && it.close > 0 })
            Zweitnotiz.mische(heimat, roh.copy(candles = zweitKerzen), MarketHours.zone)
        }
        // Verzoegerte Notierung: die juengsten Minuten aus der frischeren gleichen Notierung
        val f = if (range == Range.D1) frischGenutzt[symbol] else null
        val data = if (f == null) gemischt else runCatching {
            val fr = ExchangeRates.rate(f.nativeCurrency, fx.value) ?: return@runCatching gemischt
            val roh = api.candles(f.symbol, range.yahooRange, range.yahooInterval)
            val kerzen = Ticks.despike(roh.candles.filter { it.close.isFinite() && it.close > 0 })
            Zweitnotiz.mische(gemischt, convert(roh.copy(candles = kerzen), fr), MarketHours.zone)
        }.getOrDefault(gemischt)
        candleLock.withLock { kerzenMerken(key, data) }
        return data
    }

    suspend fun candles(symbol: String, range: Range): List<Candle> = series(symbol, range).candles

    // Kurshistorie fuer einen frei gewaehlten Zeitraum, von bis bis in Millisekunden, in EUR. Vom Broker
    // kommen die Kerzen des naechstgroesseren festen Zeitraums, auf das Fenster zugeschnitten.
    suspend fun zeitraum(symbol: String, von: Long, bis: Long): Series {
        val key = "$symbol|Z|$von|$bis"
        val cached = candleLock.withLock {
            candleCache[key]?.takeIf { (ts, data) -> System.currentTimeMillis() - ts < 15 * 60_000L && data.candles.isNotEmpty() }?.second
        }
        if (cached != null) return cached
        val quote = _quotes.value[symbol] ?: refreshOne(symbol) ?: return Series(emptyList())
        refreshFx()
        val rate = ExchangeRates.rate(quote.nativeCurrency, fx.value) ?: return Series(emptyList())
        val jetzt = System.currentTimeMillis()
        val imFenster: (Candle) -> Boolean = { it.t in von..bis && it.close.isFinite() && it.close > 0 }
        val vomBroker = kursquelle?.takeIf { it.fuehrt(symbol) }?.let { q ->
            try { q.kerzen(symbol, Zeitraum.grob(von, jetzt)) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        }
        val data = if (vomBroker != null) Series(convert(vomBroker, rate).candles.filter(imFenster)) else {
            val intervall = Zeitraum.intervall(von, bis, jetzt)
            val gueltig = api.candlesBetween(dataSymbol(symbol), von / 1000, bis / 1000, intervall).candles.filter(imFenster)
            convert(Series(if (Zeitraum.intraday(intervall)) Ticks.despike(gueltig) else gueltig), rate)
        }
        candleLock.withLock { kerzenMerken(key, data) }
        return data
    }

    // Tagesschlusskurse ueber bis zu zehn Jahre in EUR, fuer den Kaufkurs beim Import. Lange zwischengespeichert,
    // weil sich die Vergangenheit nicht aendert.
    suspend fun daily(symbol: String): List<Candle> {
        val key = "$symbol|DAILY"
        val cached = candleLock.withLock {
            candleCache[key]?.takeIf { (ts, data) -> System.currentTimeMillis() - ts < 6 * 3_600_000L && data.candles.isNotEmpty() }?.second
        }
        if (cached != null) return cached.candles
        val quote = _quotes.value[symbol] ?: refreshOne(symbol) ?: return emptyList()
        refreshFx()
        val rate = ExchangeRates.rate(quote.nativeCurrency, fx.value) ?: return emptyList()
        val raw = api.candles(symbol, "10y", "1d", prePost = false)
        val data = convert(raw.copy(candles = raw.candles.filter { it.close.isFinite() && it.close > 0 }), rate)
        if (data.candles.isNotEmpty()) candleLock.withLock { kerzenMerken(key, data) }
        return data.candles
    }

    // Tagesschlusskurse fuer bis zu 20 Werte in einer Abfrage, ohne Zwischenspeicher.
    suspend fun schlusskurse(symbols: List<String>, range: String, interval: String = "1d"): Map<String, List<Candle>> = api.schlusskurse(symbols, range, interval)

    suspend fun apeWisdom(filter: String = "all-stocks"): List<MarketApi.Erwaehnung> = api.apeWisdom(filter)
    suspend fun trending(region: String): List<String> = api.trending(region)
    suspend fun screener(id: String): List<String> = api.screener(id)
    suspend fun wikiAufrufe(sprache: String, artikel: String): List<Int> = api.wikiAufrufe(sprache, artikel)

    // Tagesschlusskurse in Handelswaehrung ohne Umrechnung, etwa fuer Portfolioverlaeufe im Copytrading und EURUSD=X.
    // Sechs Jahre, sechs Stunden zwischengespeichert. Braucht keinen Kurs aus der Kursliste.
    suspend fun taeglichNativ(symbol: String): List<Candle> {
        val key = "$symbol|NATIV"
        val cached = candleLock.withLock {
            candleCache[key]?.takeIf { (ts, data) -> System.currentTimeMillis() - ts < 6 * 3_600_000L && data.candles.isNotEmpty() }?.second
        }
        if (cached != null) return cached.candles
        val raw = api.candles(symbol, "6y", "1d", prePost = false)
        val data = raw.copy(candles = raw.candles.filter { it.close.isFinite() && it.close > 0 })
        if (data.candles.isNotEmpty()) candleLock.withLock { kerzenMerken(key, data) }
        return data.candles
    }

    // Tagesschlusskurse von Dezember 1999 bis Ende 2016, in EUR zum heutigen Wechselkurs (Veraenderungen
    // in Prozent bleiben so die der Handelswaehrung). Fuer den Crash-Test mit den Krisen vor 2016 und
    // ihrer Erholung; einen halben Tag zwischengespeichert.
    suspend fun earlyDaily(symbol: String): List<Candle> {
        val key = "$symbol|EARLY"
        val cached = candleLock.withLock {
            candleCache[key]?.takeIf { (ts, data) -> System.currentTimeMillis() - ts < 12 * 3_600_000L && data.candles.isNotEmpty() }?.second
        }
        if (cached != null) return cached.candles
        val quote = _quotes.value[symbol] ?: refreshOne(symbol) ?: return emptyList()
        refreshFx()
        val rate = ExchangeRates.rate(quote.nativeCurrency, fx.value) ?: return emptyList()
        val raw = api.candlesBetween(symbol, EARLY_FROM, EARLY_TO)
        val data = convert(raw.copy(candles = raw.candles.filter { it.close.isFinite() && it.close > 0 }), rate)
        if (data.candles.isNotEmpty()) candleLock.withLock { kerzenMerken(key, data) }
        return data.candles
    }

    // Dividendentermine in EUR je Stueck.
    suspend fun dividends(symbol: String): List<MarketApi.DividendEvent> {
        val quote = _quotes.value[symbol] ?: refreshOne(symbol) ?: return emptyList()
        refreshFx()
        val rate = ExchangeRates.rate(quote.nativeCurrency, fx.value) ?: return emptyList()
        return api.dividends(symbol).map { if (rate == 1.0) it else it.copy(amount = it.amount / rate) }
    }

    // Dividenden der letzten zehn Jahre in EUR je Stueck (zum heutigen Wechselkurs).
    suspend fun dividendHistory(symbol: String): List<MarketApi.DividendEvent> {
        val quote = _quotes.value[symbol] ?: refreshOne(symbol) ?: return emptyList()
        refreshFx()
        val rate = ExchangeRates.rate(quote.nativeCurrency, fx.value) ?: return emptyList()
        return api.dividends(symbol, "10y").map { if (rate == 1.0) it else it.copy(amount = it.amount / rate) }
    }

    private val snapshotCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Quote>>()

    // Kurse fuer lange Listen der Entdecken-Seiten: eine Sammelabfrage je
    // hundert Werte, fuenf Minuten zwischengespeichert. Laufend beobachtete Werte sind frischer und haben Vorrang.
    // Ohne Sammelabfrage (kein crumb) bleibt es bei den beobachteten Werten.
    suspend fun snapshot(symbols: Collection<String>, maxAgeMs: Long = 5 * 60_000L): Map<String, Quote> {
        if (symbols.isEmpty()) return emptyMap()
        val now = System.currentTimeMillis()
        val out = HashMap<String, Quote>()
        val missing = ArrayList<String>()
        for (s in symbols) {
            val c = snapshotCache[s]
            if (c != null && now - c.first < maxAgeMs) out[s] = c.second else missing += s
        }
        if (missing.isNotEmpty()) {
            try {
                refreshFx()
                for (chunk in missing.chunked(100)) {
                    val got = api.quotesBatchOnly(chunk)
                    if (got.isEmpty()) break
                    for ((sym, q) in got) {
                        val eur = ExchangeRates.toEur(q, fx.value) ?: continue
                        snapshotCache[sym] = now to eur
                        out[sym] = eur
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
        val live = _quotes.value
        for (s in symbols) live[s]?.let { out[s] = it }
        return out
    }

    private val optionCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, MarketApi.OptionVolumes?>>()

    // Long/Short-Verteilung aus dem Optionsumsatz an US-Boersen (Calls = Long, Puts = Short), 30 Minuten
    // zwischengespeichert. Nur fuer US-Werte (Kuerzel ohne Boersenzusatz); sonst null.
    suspend fun optionVolumes(symbol: String): MarketApi.OptionVolumes? {
        val key = dataSymbol(symbol)
        if (!hasUsOptions(key)) return null
        val now = System.currentTimeMillis()
        optionCache[key]?.let { (t, v) -> if (now - t < 30 * 60_000L) return v }
        val v = try { api.optionVolumes(key) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        optionCache[key] = now to v
        return v
    }

    // Eine Notierung desselben Werts an einer Boerse; Kurs in EUR.
    data class Handelsplatz(val symbol: String, val boerse: String, val waehrung: String, val kurs: Double, val zeit: Long)

    // Notierungen von symbol an allen Boersen, die Yahoo kennt (Suche ueber den Namen). Als dieselbe Aktie gilt eine
    // Notierung, deren Kurs in Euro hoechstens 10 % vom eigenen abweicht; Hinterlegungsscheine mit anderem
    // Bezugsverhaeltnis fallen so heraus. Der Wert selbst steht vorn.
    suspend fun handelsplaetze(symbol: String): List<Handelsplatz> {
        val eigen = _quotes.value[symbol] ?: refreshOne(symbol) ?: return emptyList()
        val begriff = Zweitnotiz.suchbegriff(Symbols.name(symbol)).ifBlank { symbol }
        val kandidaten = runCatching { api.lookup(begriff) }.getOrDefault(emptyList()).map { it.symbol }.filter { it != symbol }.distinct().take(60)
        val q = snapshot(kandidaten + symbol)
        val liste = (listOf(symbol) + kandidaten).mapNotNull { s ->
            val k = (if (s == symbol) eigen else q[s]) ?: return@mapNotNull null
            if (s != symbol && (!(k.price > 0) || !(eigen.price > 0) || kotlin.math.abs(k.price / eigen.price - 1.0) > 0.10)) return@mapNotNull null
            Handelsplatz(s, k.exchange.ifBlank { s.substringAfter('.', "") }, k.nativeCurrency, k.price, k.marketTime)
        }
        return liste.take(1) + liste.drop(1).sortedBy { it.boerse }
    }

    // Suche; mit verbundenem Broker nur unter dessen handelbaren Werten.
    suspend fun search(query: String): List<MarketApi.SearchHit> = kursquelle?.suche(query) ?: api.search(query)

    // Suche immer bei Yahoo, auch mit verbundenem Broker (Zuordnung von ISIN beim Import).
    suspend fun yahooSuche(query: String): List<MarketApi.SearchHit> = api.search(query)

    suspend fun profile(symbol: String): MarketApi.Profile? = api.profile(symbol)
    suspend fun kennzahlen(symbols: List<String>): Map<String, Map<String, Double>> = api.kennzahlen(symbols)
    suspend fun optionskette(symbol: String) = api.optionskette(symbol)

    suspend fun news(symbol: String, lang: String): List<MarketApi.NewsItem> = api.news(symbol, lang)

    suspend fun ecbRate(): MarketApi.EcbRate? = api.ecbDepositRate()

    suspend fun companyInfo(symbol: String, name: String) = api.companyInfo(symbol, name)
}

// Zeitraum fuer MarketRepository.earlyDaily: 01.12.1999 bis 31.12.2016 (Sekunden seit 1970, UTC).
private const val EARLY_FROM = 944_006_400L
private const val EARLY_TO = 1_483_228_800L

// US-Aktien und US-ETFs haben bei Yahoo ein Kuerzel ohne Boersenzusatz (AAPL, NVDA, SPY).
internal fun hasUsOptions(yahooSymbol: String): Boolean = Regex("^[A-Z]{1,5}$").matches(yahooSymbol)
