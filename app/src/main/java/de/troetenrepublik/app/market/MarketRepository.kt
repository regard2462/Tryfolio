package de.troetenrepublik.app.market

import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.data.Range
import de.troetenrepublik.app.data.Series
import de.troetenrepublik.app.engine.ExchangeRates
import de.troetenrepublik.app.engine.EurexOptionen
import de.troetenrepublik.app.engine.HkexOptionen
import de.troetenrepublik.app.engine.JpxOptionen
import de.troetenrepublik.app.engine.OptionsZahlen
import de.troetenrepublik.app.engine.TaifexOptionen
import de.troetenrepublik.app.engine.MarketHours
import de.troetenrepublik.app.engine.Ticks
import de.troetenrepublik.app.engine.Zeitraum
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
class MarketRepository(
    private val api: MarketApi,
    private val scope: CoroutineScope,
    private val zweitDatei: File? = null,
    // Ordner fuer den gespeicherten Stand (Kerzen, Kurse, Stammdaten); null = nur Arbeitsspeicher
    standOrdner: File? = null,
) {

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

    // Gespeicherter Stand auf dem Geraet: ueberbrueckt nur die Wartezeit nach dem Oeffnen, ersetzt nie den Abruf.
    private val kerzenStand = standOrdner?.let { o -> StandSpeicher((0 until 8).map { SichereStandDatei(File(o, "kerzen$it.txt")) }, 800_000, 40) }
    private val kurseStand = standOrdner?.let { o -> StandSpeicher(listOf(SichereStandDatei(File(o, "kurse.txt"))), 600_000, 400) }
    private val datenStand = standOrdner?.let { o -> StandSpeicher((0 until 2).map { SichereStandDatei(File(o, "daten$it.txt")) }, 1_500_000, 150) }
    // Tageswerte der 30-Tage-Volatilitaet je Symbol (eine Zeile je Symbol, hoechstens 400 Tage)
    private val ivStand = standOrdner?.let { o -> StandSpeicher((0 until 2).map { SichereStandDatei(File(o, "iv$it.txt")) }, 1_500_000, 150) }
    private val standJson = Json { ignoreUnknownKeys = true; allowSpecialFloatingPointValues = true }
    private var standJob: kotlinx.coroutines.Job? = null

    // Geaenderte Dateien einige Sekunden nach der letzten Aenderung im Hintergrund schreiben.
    private fun standSichern() {
        val alle = listOfNotNull(kerzenStand, kurseStand, datenStand, ivStand)
        if (alle.isEmpty()) return
        synchronized(this) {
            if (standJob?.isActive == true) return
            standJob = scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                var runden = 0
                do {
                    delay(4_000)
                    alle.forEach { runCatching { it.sichern() } }
                } while (alle.any { it.offen() } && ++runden < 5)
            }
        }
    }

    // Summen des gespeicherten Stands (Zeichen, Eintraege) fuer Bericht und Tests
    fun standGroesse(): Pair<Int, Int> {
        val alle = listOfNotNull(kerzenStand, kurseStand, datenStand, ivStand)
        return alle.sumOf { it.zeichen() } to alle.sumOf { it.eintraege() }
    }

    // Sofort schreiben (Tests, Beenden)
    fun standJetztSichern() { listOfNotNull(kerzenStand, kurseStand, datenStand, ivStand).forEach { runCatching { it.sichern() } } }

    fun ivVerlauf(symbol: String): String? = ivStand?.lesen(symbol)?.wert
    fun ivMerken(symbol: String, text: String) { ivStand?.schreiben(symbol, text); standSichern() }

    // Gespeicherter Text mit Abrufzeit fuer Stammdaten, Nachrichten und aehnliches
    fun datenGespeichert(key: String): StandSpeicher.Eintrag? = datenStand?.lesen(key)
    fun datenMerken(key: String, text: String) { datenStand?.schreiben(key, text); standSichern() }

    private fun serienFrist(range: Range): Long = if (range == Range.D1) 60_000L else if (range.intraday) 5 * 60_000L else 15 * 60_000L

    private fun kerzenAusStand(key: String): Pair<Long, Series>? {
        val st = kerzenStand ?: return null
        val e = st.lesen(key) ?: return null
        val serie = SerieCodec.lesen(e.wert)?.takeIf { it.candles.isNotEmpty() }
        if (serie == null) { st.entfernen(key); return null }
        return e.t to serie
    }

    // Kursreihe aus dem Speicher ohne Abruf, auch ausserhalb der Frist (dann frisch = false); null ohne Eintrag.
    suspend fun serieGespeichert(symbol: String, range: Range): StandRegel.Gespeichert<Series>? {
        val key = "$symbol|${range.name}"
        val e = candleLock.withLock { candleCache[key] }?.takeIf { it.second.candles.isNotEmpty() } ?: kerzenAusStand(key) ?: return null
        val jetzt = System.currentTimeMillis()
        return StandRegel.Gespeichert(e.second, e.first, StandRegel.frisch(e.first, serienFrist(range), jetzt))
    }

    // Kurse aus dem Speicher des letzten Starts. Sie stehen nur hier und nie in quotes: Orders, Kaeufe, Alarme und
    // Bewertungen rechnen mit quotes, also nur mit Kursen dieser Sitzung. Ein Wert verschwindet hier, sobald sein
    // frischer Kurs da ist.
    data class AltKurs(val quote: Quote, val abgerufen: Long)

    private val gemerkteKurse = MutableStateFlow<Map<String, AltKurs>>(emptyMap())
    val altKurse: StateFlow<Map<String, AltKurs>> =
        kotlinx.coroutines.flow.combine(_quotes, gemerkteKurse) { frisch, alt -> if (alt.isEmpty()) alt else alt.filterKeys { it !in frisch } }
            .stateIn(scope, SharingStarted.Eagerly, emptyMap())
    private var letzteKurseSicherung = 0L

    private fun kurseMerken(kurse: Map<String, Quote>) {
        val st = kurseStand ?: return
        val jetzt = System.currentTimeMillis()
        // Hoechstens einmal je Minute, denn die Kurse kommen alle paar Sekunden
        if (jetzt - letzteKurseSicherung < 60_000L) return
        letzteKurseSicherung = jetzt
        kurse.forEach { (k, q) ->
            if (q.price.isFinite() && q.price > 0) runCatching { st.schreiben(k, standJson.encodeToString(q), jetzt) }
        }
        standSichern()
    }

    init {
        val st = kurseStand
        if (st != null) scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val geladen = runCatching {
                st.alle().mapNotNull { (k, e) ->
                    runCatching { k to AltKurs(standJson.decodeFromString<Quote>(e.wert), e.t) }.getOrNull()
                        ?.takeIf { it.second.quote.price > 0 }
                }.toMap()
            }.getOrDefault(emptyMap())
            gemerkteKurse.value = geladen
        }
    }

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
        return now - e.t > Zweitnotiz.fristFuer(e.z)
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
                    val begriff = Zweitnotiz.suchbegriff(wertName(s, heimat.name) ?: s).ifBlank { s }
                    val treffer = runCatching { api.lookupAntwort(begriff) }.getOrNull()
                    // Ohne Antwort (kein Netz) nichts merken, beim naechsten Abruf erneut versuchen
                    if (treffer.isNullOrEmpty()) continue
                    val gefunden = Zweitnotiz.waehleNotierung(treffer, heimat.price).orEmpty()
                    zweit[s] = ZweitEintrag(Zweitnotiz.beibehalten(zweit[s]?.z.orEmpty(), gefunden), System.currentTimeMillis())
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

    suspend fun refresh() = refreshLock.withLock {
        val symbols = tracked.value.toList()
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
                kurseMerken(converted)
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
        kurseMerken(mapOf(symbol to eur))
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
        // Rubel und andere Waehrungen der weiteren Kursquellen (Fixing der Zentralbank, frei und ohne Anmeldung)
        val extra = runCatching { api.externeWechselkurse() }.getOrDefault(emptyMap())
        if (res.isEmpty() && extra.isEmpty()) return@withLock
        val map = fx.value.toMutableMap()
        extra.forEach { (cur, v) -> if (v.isFinite() && v > 0) map[cur] = v }
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
        val ttl = serienFrist(range)
        // Im Arbeitsspeicher, sonst im gespeicherten Stand; beides gilt nur innerhalb der Frist
        val cached = candleLock.withLock {
            (candleCache[key] ?: kerzenAusStand(key)?.also { candleCache[key] = it })?.takeIf { (ts, data) ->
                System.currentTimeMillis() - ts < ttl && data.candles.isNotEmpty()
            }?.second
        }
        if (cached != null) return cached
        val quote = _quotes.value[symbol] ?: refreshOne(symbol) ?: return Series(emptyList())
        refreshFx()
        val rate = ExchangeRates.rate(quote.nativeCurrency, fx.value) ?: return Series(emptyList())
        val raw = api.candles(dataSymbol(symbol), range.yahooRange, range.yahooInterval)
        val valid = raw.candles.filter { it.close.isFinite() && it.close > 0 }
        // Einzelne Fehlkurse, vor allem nachboerslich, ergeben sonst senkrechte Zacken in Kurs- und Depotkurve.
        val heimat = convert(raw.copy(candles = if (range.intraday) Ticks.despike(valid) else valid), rate)
        // Tageschart: die deutsche Zweitnotiz ergaenzt die Zeit vor der Eroeffnung und nach dem Schluss des Heimatplatzes
        val z = if (range == Range.D1) zweitnotiz(symbol) else null
        val gemischt = if (z == null) heimat else {
            val roh = api.candles(z, range.yahooRange, range.yahooInterval)
            // Luecken mit echten Kursen fuellen: dieselbe Aktie an den anderen deutschen Handelsplaetzen (0.77)
            val weitere = kotlinx.coroutines.coroutineScope {
                Zweitnotiz.schwestern(z).map { s -> async { runCatching { api.candles(s, range.yahooRange, range.yahooInterval).candles }.getOrNull() } }.awaitAll()
            }.filterNotNull()
            val zweitKerzen = Ticks.despike(Zweitnotiz.vereine(roh.candles, weitere))
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
        if (data.candles.isNotEmpty()) kerzenStand?.let { runCatching { it.schreiben(key, SerieCodec.kodieren(data)) }; standSichern() }
        return data
    }

    suspend fun candles(symbol: String, range: Range): List<Candle> = series(symbol, range).candles

    // [0.78] 5-Minuten-Kerzen der Hauptsitzung ueber rund 60 Tage in Handelswaehrung, fuer den Intraday-Rueckblick.
    // Ein Abruf je Wert und Kalendertag, danach aus dem Zwischenspeicher.
    suspend fun intraday5m(symbol: String): List<Candle> {
        val key = "$symbol|INTRA5"
        val heute = MarketHours.dayStart(System.currentTimeMillis())
        val cached = candleLock.withLock {
            candleCache[key]?.takeIf { (ts, data) -> MarketHours.dayStart(ts) == heute && data.candles.isNotEmpty() }?.second
        }
        if (cached != null) return cached.candles
        val raw = api.candles(dataSymbol(symbol), "60d", "5m", prePost = false)
        val data = Series(raw.candles.filter { it.close.isFinite() && it.close > 0 })
        if (data.candles.isNotEmpty()) candleLock.withLock { kerzenMerken(key, data) }
        return data.candles
    }

    // Kurshistorie fuer einen frei gewaehlten Zeitraum, von bis bis in Millisekunden, in EUR.
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
        val intervall = Zeitraum.intervall(von, bis, jetzt)
        val gueltig = api.candlesBetween(dataSymbol(symbol), von / 1000, bis / 1000, intervall).candles.filter(imFenster)
        val data = convert(Series(if (Zeitraum.intraday(intervall)) Ticks.despike(gueltig) else gueltig), rate)
        candleLock.withLock { kerzenMerken(key, data) }
        return data
    }

    // Kerzen in EUR zum heutigen Wechselkurs von von bis bis (ms) mit festem Abstand, ein Abruf ohne Zwischenspeicher:
    // fuer Orders und Sparplaene, die nach einer Pause der App nachgeholt werden. Leer, wenn nichts kommt.
    suspend fun kerzenZwischen(symbol: String, von: Long, bis: Long, intervall: String): List<Candle> {
        val quote = _quotes.value[symbol] ?: refreshOne(symbol) ?: return emptyList()
        refreshFx()
        val rate = ExchangeRates.rate(quote.nativeCurrency, fx.value) ?: return emptyList()
        val gueltig = api.candlesBetween(dataSymbol(symbol), von / 1000, bis / 1000 + 1, intervall).candles
            .filter { it.t in von..bis && it.close.isFinite() && it.close > 0 }
        return convert(Series(if (Zeitraum.intraday(intervall)) Ticks.despike(gueltig) else gueltig), rate).candles
    }

    private val fxVerlaufLock = Mutex()
    private val fxVerlaufFehler = HashMap<String, Long>()

    // Tagesschlusskurse des Paars EUR<waehrung>=X (Einheiten je Euro) der letzten zehn Jahre, sechs Stunden
    // zwischengespeichert, je Waehrung nur ein Abruf. Leer, wenn Yahoo nichts liefert; ein Fehlschlag wird zehn Minuten
    // lang nicht wiederholt.
    private suspend fun wechselkursVerlauf(waehrung: String): List<Candle> = fxVerlaufLock.withLock {
        val key = "EUR$waehrung=X|FXH"
        val jetzt = System.currentTimeMillis()
        candleLock.withLock {
            candleCache[key]?.takeIf { (ts, data) -> jetzt - ts < 6 * 3_600_000L && data.candles.isNotEmpty() }?.second?.candles
        }?.let { return@withLock it }
        if (jetzt - (fxVerlaufFehler[waehrung] ?: 0L) < 10 * 60_000L) return@withLock emptyList()
        val roh = runCatching { api.candles("EUR$waehrung=X", "10y", "1d", prePost = false) }.getOrNull()
        val data = roh?.copy(candles = roh.candles.filter { it.close.isFinite() && it.close > 0 })
        if (data == null || data.candles.size < 2) { fxVerlaufFehler[waehrung] = jetzt; return@withLock emptyList() }
        candleLock.withLock { kerzenMerken(key, data) }
        data.candles
    }

    // Kerzen eines Werts, die in Euro zum heutigen Kurs vorliegen (series, daily), auf den Wechselkurs des jeweiligen
    // Tages bringen, wie ihn auch die Buchungen tragen (Kauf in Euro zum damaligen Kurs). Fuer Verlauf und Zerlegung des
    // Depots. Euro-Werte, der Tageschart und fehlende Wechselkurs-Historie bleiben unveraendert.
    suspend fun mitDamaligemKurs(symbol: String, kerzen: List<Candle>): List<Candle> {
        if (kerzen.isEmpty()) return kerzen
        val waehrung = ExchangeRates.basisWaehrung(_quotes.value[symbol]?.nativeCurrency.orEmpty()) ?: return kerzen
        val heute = fx.value[waehrung] ?: return kerzen
        val verlauf = wechselkursVerlauf(waehrung)
        return ExchangeRates.mitTageskurs(kerzen, heute, verlauf)
    }

    suspend fun mitDamaligemKurs(symbol: String, serie: Series, range: Range): Series =
        if (range == Range.D1) serie else serie.copy(candles = mitDamaligemKurs(symbol, serie.candles))

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

    // Alle Tagesschlusskurse seit Beginn der Notierung in Handelswaehrung (bei Apple seit 1980), fuer die langfristige
    // Trefferbilanz der Indikatoren. Yahoo liefert mit range=max nur Monatskerzen, deshalb ueber period1=0. Einen Tag
    // zwischengespeichert, weil sich die Vergangenheit nicht aendert.
    suspend fun gesamtHistorie(symbol: String): List<Candle> {
        val key = "$symbol|GESAMT"
        val cached = candleLock.withLock {
            candleCache[key]?.takeIf { (ts, data) -> System.currentTimeMillis() - ts < 24 * 3_600_000L && data.candles.isNotEmpty() }?.second
        }
        if (cached != null) return cached.candles
        val raw = api.candlesBetween(symbol, 0L, System.currentTimeMillis() / 1000)
        val data = raw.copy(candles = raw.candles.filter { it.close.isFinite() && it.close > 0 })
        if (data.candles.isNotEmpty()) candleLock.withLock { kerzenMerken(key, data) }
        return data.candles
    }

    // Wie gesamtHistorie, aber in Euro zum heutigen Kurs (wie daily). Einen Tag zwischengespeichert, leer ohne Kurs
    // oder Wechselkurs.
    suspend fun gesamtHistorieEuro(symbol: String): List<Candle> {
        val key = "$symbol|GESAMTEUR"
        val cached = candleLock.withLock {
            candleCache[key]?.takeIf { (ts, data) -> System.currentTimeMillis() - ts < 24 * 3_600_000L && data.candles.isNotEmpty() }?.second
        }
        if (cached != null) return cached.candles
        val roh = gesamtHistorie(symbol)
        if (roh.isEmpty()) return emptyList()
        val quote = _quotes.value[symbol] ?: refreshOne(symbol) ?: return emptyList()
        refreshFx()
        val rate = ExchangeRates.rate(quote.nativeCurrency, fx.value) ?: return emptyList()
        val data = convert(Series(roh), rate)
        candleLock.withLock { kerzenMerken(key, data) }
        return data.candles
    }

    // Alle Dividenden seit Beginn der Notierung in Euro je Stueck (zum heutigen Wechselkurs), passend zu gesamtHistorieEuro.
    suspend fun gesamtDividenden(symbol: String): List<MarketApi.DividendEvent> {
        val quote = _quotes.value[symbol] ?: refreshOne(symbol) ?: return emptyList()
        refreshFx()
        val rate = ExchangeRates.rate(quote.nativeCurrency, fx.value) ?: return emptyList()
        return api.dividendsGesamt(symbol, System.currentTimeMillis() / 1000).map { if (rate == 1.0) it else it.copy(amount = it.amount / rate) }
    }

    // Verteilung und Verlauf der Analystenurteile (5.92), ohne Zwischenspeicher.
    suspend fun analysten(symbol: String): MarketApi.Analystenverlauf? = api.analysten(dataSymbol(symbol))

    // Groesste institutionelle Halter einer Aktie (5.93), ohne Zwischenspeicher.
    suspend fun halter(symbol: String): Halter.Daten? = api.halter(dataSymbol(symbol))

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

    // Tagesschlusskurse von Dezember 1999 bis mindestens Ende 2016, in EUR zum heutigen Wechselkurs (Veraenderungen
    // in Prozent bleiben so die der Handelswaehrung). Fuer den Crash-Test mit den Krisen vor der Tagesreihe (daily,
    // rollend zehn Jahre) und ihrer Erholung; das Ende wandert mit, damit sich beide Reihen immer ueberlappen
    // (PortfolioTools.fruehesEnde). Einen halben Tag zwischengespeichert.
    suspend fun earlyDaily(symbol: String): List<Candle> {
        val key = "$symbol|EARLY"
        val cached = candleLock.withLock {
            candleCache[key]?.takeIf { (ts, data) -> System.currentTimeMillis() - ts < 12 * 3_600_000L && data.candles.isNotEmpty() }?.second
        }
        if (cached != null) return cached.candles
        val quote = _quotes.value[symbol] ?: refreshOne(symbol) ?: return emptyList()
        refreshFx()
        val rate = ExchangeRates.rate(quote.nativeCurrency, fx.value) ?: return emptyList()
        val raw = api.candlesBetween(symbol, EARLY_FROM, de.troetenrepublik.app.engine.PortfolioTools.fruehesEnde(System.currentTimeMillis()) / 1000L)
        val data = convert(raw.copy(candles = raw.candles.filter { it.close.isFinite() && it.close > 0 }), rate)
        if (data.candles.isNotEmpty()) candleLock.withLock { kerzenMerken(key, data) }
        return data.candles
    }

    // [0.72] Monatsschlusskurse seit Ende 2014 in Handelswaehrung (eine Abfrage statt tausender Tageskurse), fuer den
    // Sparplan-Rueckblick. Der Zeitstempel einer Monatskerze ist der Monatsanfang, der Schluss der Monatsstand.
    // Zwolf Stunden zwischengespeichert.
    suspend fun monatsKurse(symbol: String): List<Candle> {
        val key = "$symbol|MONAT"
        val cached = candleLock.withLock {
            candleCache[key]?.takeIf { (ts, data) -> System.currentTimeMillis() - ts < 12 * 3_600_000L && data.candles.isNotEmpty() }?.second
        }
        if (cached != null) return cached.candles
        val raw = api.candlesBetween(symbol, MONAT_AB, System.currentTimeMillis() / 1000L, interval = "1mo")
        val data = raw.copy(candles = raw.candles.filter { it.close.isFinite() && it.close > 0 })
        if (data.candles.isNotEmpty()) candleLock.withLock { kerzenMerken(key, data) }
        return data.candles
    }

    // Bestand eines Fonds (ETF-Durchschau); null ohne Daten.
    suspend fun etfBestand(symbol: String): de.troetenrepublik.app.engine.Durchschau.Bestand? = api.etfBestand(symbol)

    // Aktiensplits der letzten zehn Jahre; null, wenn Yahoo nicht antwortet.
    suspend fun splits(symbol: String, bereich: String = "10y"): List<de.troetenrepublik.app.engine.Splits.Split>? = api.splits(symbol, bereich)

    // Dividendentermine in EUR je Stueck (Yahoo-Zeitraum range, sonst ein Jahr).
    suspend fun dividends(symbol: String, range: String = "1y"): List<MarketApi.DividendEvent> {
        val quote = _quotes.value[symbol] ?: refreshOne(symbol) ?: return emptyList()
        refreshFx()
        val rate = ExchangeRates.rate(quote.nativeCurrency, fx.value) ?: return emptyList()
        return api.dividends(symbol, range).map { if (rate == 1.0) it else it.copy(amount = it.amount / rate) }
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

    private val rohCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Quote>>()

    // Kurse in der Landeswaehrung der Notierung, ohne Umrechnung (fuer Prozentaenderungen, etwa die Leitindizes des
    // Globus): eine Sammelabfrage, fuenf Minuten zwischengespeichert. Fehlende Werte fehlen auch im Ergebnis.
    suspend fun kurseRoh(symbols: Collection<String>, maxAgeMs: Long = 5 * 60_000L): Map<String, Quote> {
        if (symbols.isEmpty()) return emptyMap()
        val now = System.currentTimeMillis()
        val out = HashMap<String, Quote>()
        val fehlt = ArrayList<String>()
        for (s in symbols) {
            val c = rohCache[s]
            if (c != null && now - c.first < maxAgeMs) out[s] = c.second else fehlt += s
        }
        if (fehlt.isNotEmpty()) {
            try {
                for ((sym, q) in api.quotes(fehlt)) {
                    if (q.price <= 0 || q.prevClose <= 0) continue
                    rohCache[sym] = now to q
                    out[sym] = q
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
        return out
    }

    private val optionCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, MarketApi.OptionVolumes?>>()

    // US-Notierung je Symbol mit Zeitpunkt ("" = keine gefunden): Treffer gelten einen Tag, „keine“ 30 Minuten.
    // Eine leere Trefferliste (Netzfehler, Sperre) wird nicht gemerkt.
    private val usNotizen = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, String>>()

    private fun usNotiz(symbol: String): String? {
        val e = usNotizen[symbol] ?: return null
        val dauer = if (e.second.isEmpty()) 30 * 60_000L else 24 * 3_600_000L
        if (System.currentTimeMillis() - e.first < dauer) return e.second
        usNotizen.remove(symbol, e)
        return null
    }

    // Name eines Werts fuer Namenssuchen: der Katalogname, sonst der einer bekannten gleichen Notierung, sonst
    // kursName beziehungsweise der Yahoo-Name aus dem Kurs.
    private fun wertName(symbol: String, kursName: String? = null): String? {
        Symbols.find(symbol)?.name?.let { return it }
        Gleichwerte.familien.value[symbol]?.firstNotNullOfOrNull { Symbols.find(it)?.name }?.let { return it }
        val name = kursName ?: _quotes.value[symbol]?.name ?: return null
        return name.takeIf { it.isNotEmpty() && it != symbol }
    }

    // Kuerzel, unter dem es US-Optionen geben kann: das eigene bei US-Werten, sonst die US-Notierung derselben
    // Firma (etwa SAP fuer SAP.DE oder NVO fuer Novo Nordisk), gesucht ueber den Namen (5.94). Treffer gelten
    // einen Tag, „keine“ 30 Minuten; ohne Namen oder bei Netzfehler wird nichts gemerkt.
    private suspend fun optionsKuerzel(symbol: String): String? {
        val key = dataSymbol(symbol)
        if (hasUsOptions(key)) return key
        // Nur Aktien haben eine US-Notierung derselben Firma; Indizes, Fonds, Krypto und Rohstoffe nicht
        if (!sucheUsNotiz(symbol)) return null
        usNotiz(symbol)?.let { return it.ifEmpty { null } }
        val name = wertName(symbol)
            ?: try { refreshOne(symbol)?.name } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        if (name.isNullOrEmpty() || name == symbol) return null
        // Jeden Suchbegriff der Reihe nach fragen; ohne Antwort (Netzfehler) nichts merken, erst wenn alle
        // beantwortet wurden und keiner passt, „keine“
        val gefunden = Zweitnotiz.usSuchen(name) {
            try { api.lookupAntwort(it) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        }
        if (gefunden == null) return null
        usNotizen[symbol] = System.currentTimeMillis() to gefunden
        return gefunden.ifEmpty { null }
    }

    private fun sucheUsNotiz(symbol: String): Boolean = Symbols.typeOf(symbol) == de.troetenrepublik.app.data.AssetType.STOCK

    // Long/Short-Verteilung aus dem Optionsumsatz (Calls = Long, Puts = Short), 30 Minuten zwischengespeichert.
    // Zuerst US-Boersen; auslaendische Aktien nehmen ihre US-Notierung. Hat der Wert dort keine Optionen, gilt der
    // Umsatz der Aktienoptionen an der Euronext, gefunden ueber die ISIN (0.76). Ein Netzfehler wird nicht
    // gemerkt, wohl aber eine echte leere Antwort.
    suspend fun optionVolumes(symbol: String, isin: String = ""): MarketApi.OptionVolumes? {
        usOptionVolumes(symbol)?.let { return it }
        val eigen = dataSymbol(symbol)
        return when {
            HkexOptionen.code(eigen) != null -> hkexVolumes(symbol)
            TaifexOptionen.code(eigen) != null -> taifexVolumes(eigen)
            JpxOptionen.code(eigen) != null -> jpxVolumes(eigen)
            ohneOptionsquelle(eigen) -> null
            else -> euronextVolumes(symbol, isin) ?: eurexVolumes(symbol, isin)
        }
    }

    // Boersen, fuer die es keine freie Quelle mit Calls und Puts je Aktie gibt (Korea: KRX verlangt eine Anmeldung;
    // Indien: die NSE untersagt den automatischen Abruf, die BSE hat so gut wie keinen Umsatz in Aktienoptionen).
    private fun ohneOptionsquelle(yahooSymbol: String): Boolean {
        val s = yahooSymbol.uppercase()
        return s.endsWith(".KS") || s.endsWith(".KQ") || s.endsWith(".NS") || s.endsWith(".BO")
    }

    private suspend fun usOptionVolumes(symbol: String): MarketApi.OptionVolumes? {
        val key = optionsKuerzel(symbol) ?: return null
        val now = System.currentTimeMillis()
        optionCache[key]?.let { (t, v) -> if (now - t < 30 * 60_000L) return v }
        val v = try { api.optionVolumes(key) } catch (e: CancellationException) { throw e } catch (_: Exception) { return null }
        optionCache[key] = now to v
        return v
    }

    // Klasse der Euronext-Aktienoptionen je ISIN mit Zeitpunkt (null = keine): Treffer und „keine“ gelten sechs Stunden.
    private val euronextKlassen = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, de.troetenrepublik.app.engine.EuronextOptionen.Klasse?>>()
    private val euronextCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, MarketApi.OptionVolumes?>>()
    // Letzter Umsatz mit Treffer: Euronext zaehlt den Tagesumsatz ab Handelsbeginn neu, vorher bleibt der von gestern.
    private val euronextLetzte = java.util.concurrent.ConcurrentHashMap<String, MarketApi.OptionVolumes>()

    private val hkexCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, MarketApi.OptionVolumes?>>()

    // Aktien aus Hongkong: Optionsumsatz des juengsten Berichtstags der HKEX (heute, sonst bis zu fuenf Tage zurueck).
    // Ein Netzfehler wird nicht gemerkt; fehlt der Basiswert im Bericht, hat er keine Optionen.
    private suspend fun hkexVolumes(symbol: String): MarketApi.OptionVolumes? {
        val code = HkexOptionen.code(dataSymbol(symbol)) ?: return null
        val now = System.currentTimeMillis()
        hkexCache[code]?.let { (t, v) -> if (now - t < 30 * 60_000L) return v }
        val heute = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Hong_Kong"))
        for (n in 0 until HkexOptionen.TAGE) {
            val tag = heute.minusDays(n.toLong())
            val a = try { api.hkexBericht(tag.year, tag.monthValue, tag.dayOfMonth) } catch (e: CancellationException) { throw e } catch (_: Exception) { return null }
            if (a.code == 0) return null
            if (!a.ok) continue
            val summen = HkexOptionen.lesen(a.text.orEmpty(), code)
            val v = summen?.takeIf { it.first + it.second >= HkexOptionen.MINDESTUMSATZ }?.let { MarketApi.OptionVolumes(it.first, it.second, now, MarketApi.Herkunft.HKEX) }
            hkexCache[code] = now to v
            return v
        }
        return null
    }

    private val berichtSperre = Mutex()
    private val tagesFrist = 3 * 3_600_000L

    // Eurex: Produktkuerzel je ISIN (Treffer 24 Stunden, "keins" 6 Stunden) und der Bericht des Tages (3 Stunden).
    private val eurexCodes = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, String?>>()
    @Volatile private var eurexTag: Pair<Long, Map<String, OptionsZahlen>>? = null

    private fun eurexCodeFrisch(i: String, now: Long): Pair<Long, String?>? =
        eurexCodes[i]?.takeIf { now - it.first < (if (it.second != null) 24 else 6) * 3_600_000L }

    private suspend fun eurexCode(i: String): String? {
        val now = System.currentTimeMillis()
        eurexCodeFrisch(i, now)?.let { return it.second }
        val a = try { api.eurexProdukt(i) } catch (e: CancellationException) { throw e } catch (_: Exception) { return null }
        if (a.code == 404) { eurexCodes[i] = now to null; return null }
        if (!a.ok) return null
        val c = EurexOptionen.produktCode(a.text.orEmpty(), i)
        eurexCodes[i] = now to c
        return c
    }

    private suspend fun eurexBerichtHolen(): Map<String, OptionsZahlen>? {
        val now = System.currentTimeMillis()
        eurexTag?.let { if (now - it.first < tagesFrist) return it.second }
        return berichtSperre.withLock {
            eurexTag?.let { if (System.currentTimeMillis() - it.first < tagesFrist) return@withLock it.second }
            try {
                val seite = api.eurexBerichteSeite() ?: return@withLock null
                val url = EurexOptionen.berichtUrl(seite) ?: return@withLock null
                val karte = EurexOptionen.lesen(api.eurexBericht(url) ?: return@withLock null)
                if (karte.isEmpty()) return@withLock null
                eurexTag = System.currentTimeMillis() to karte
                karte
            } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        }
    }

    private fun tagesWert(z: OptionsZahlen?, herkunft: MarketApi.Herkunft): MarketApi.OptionVolumes? =
        z?.wahl()?.let { (zahlen, offen) -> MarketApi.OptionVolumes(zahlen.first, zahlen.second, System.currentTimeMillis(), herkunft, offen) }

    // Aktien mit Optionen an der Eurex ohne US-Optionen und ohne Euronext-Optionen (Deutschland, Schweiz u. a.).
    private suspend fun eurexVolumes(symbol: String, isin: String): MarketApi.OptionVolumes? {
        val i = optionsIsin(symbol, isin)
        if (!euronextMoeglich(symbol, i)) return null
        val code = eurexCode(i) ?: return null
        return tagesWert(eurexBerichtHolen()?.get(code), MarketApi.Herkunft.EUREX)
    }

    // Taiwan: Vertragskuerzel je Aktiencode (24 Stunden) und Summen je Vertrag aus dem Tagesbericht (3 Stunden).
    // Der grosse Bericht wird nur geholt, wenn die Aktie in der Liste steht.
    @Volatile private var taifexListe: Pair<Long, Map<String, String>>? = null
    @Volatile private var taifexTag: Pair<Long, Map<String, OptionsZahlen>>? = null

    private suspend fun taifexVertraege(): Map<String, String>? {
        val now = System.currentTimeMillis()
        taifexListe?.let { if (now - it.first < 24 * 3_600_000L) return it.second }
        val karte = try { api.taifexListe()?.let { TaifexOptionen.vertraege(it) } } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        if (karte.isNullOrEmpty()) return null
        taifexListe = now to karte
        return karte
    }

    private suspend fun taifexBerichtHolen(vertraege: Set<String>): Map<String, OptionsZahlen>? {
        val now = System.currentTimeMillis()
        taifexTag?.let { if (now - it.first < tagesFrist) return it.second }
        return berichtSperre.withLock {
            taifexTag?.let { if (System.currentTimeMillis() - it.first < tagesFrist) return@withLock it.second }
            try {
                val karte = TaifexOptionen.lesen(api.taifexBericht() ?: return@withLock null, vertraege)
                if (karte.isEmpty()) return@withLock null
                taifexTag = System.currentTimeMillis() to karte
                karte
            } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        }
    }

    private suspend fun taifexVolumes(yahoo: String): MarketApi.OptionVolumes? {
        val code = TaifexOptionen.code(yahoo) ?: return null
        val vertraege = taifexVertraege() ?: return null
        val vertrag = vertraege[code] ?: return null
        return tagesWert(taifexBerichtHolen(vertraege.values.toSet())?.get(vertrag), MarketApi.Herkunft.TAIFEX)
    }

    // Japan: Kurs-CSV der Aktienoptionen der JPX, Summen je Wertpapiercode, eine Stunde.
    @Volatile private var jpxTag: Pair<Long, Map<String, OptionsZahlen>>? = null

    private suspend fun jpxKarte(): Map<String, OptionsZahlen>? {
        val now = System.currentTimeMillis()
        jpxTag?.let { if (now - it.first < 3_600_000L) return it.second }
        return berichtSperre.withLock {
            jpxTag?.let { if (System.currentTimeMillis() - it.first < 3_600_000L) return@withLock it.second }
            try {
                val pfad = api.jpxVerzeichnis()?.let { JpxOptionen.dateiPfad(it) } ?: return@withLock null
                val k = JpxOptionen.lesen(api.jpxDatei(pfad) ?: return@withLock null)
                if (k.isEmpty()) return@withLock null
                jpxTag = System.currentTimeMillis() to k
                k
            } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        }
    }

    private suspend fun jpxVolumes(yahoo: String): MarketApi.OptionVolumes? {
        val code = JpxOptionen.code(yahoo) ?: return null
        return tagesWert(jpxKarte()?.get(code), MarketApi.Herkunft.JPX)
    }

    // ISIN des Werts: die uebergebene, sonst die kuratierte; leer, wenn keine bekannt ist.
    private fun optionsIsin(symbol: String, isin: String): String =
        isin.takeIf { it.length == 12 } ?: Symbols.find(symbol)?.isin?.takeIf { it.length == 12 }.orEmpty()

    // Nur Aktien mit nicht-US-Heimat kommen in Frage; US-Werte ohne Optionen haben auch an der Euronext keine.
    private fun euronextMoeglich(symbol: String, isin: String): Boolean =
        isin.isNotEmpty() && !isin.startsWith("US") && sucheUsNotiz(symbol)

    private suspend fun euronextVolumes(symbol: String, isin: String): MarketApi.OptionVolumes? {
        val i = optionsIsin(symbol, isin)
        if (!euronextMoeglich(symbol, i)) return null
        val now = System.currentTimeMillis()
        euronextCache[i]?.let { (t, v) -> if (now - t < 30 * 60_000L) return v }
        val klasse = euronextKlassen[i]?.takeIf { now - it.first < 6 * 3_600_000L }?.second ?: run {
            if (euronextKlassen[i]?.let { now - it.first < 6 * 3_600_000L } == true) return null
            val antwort = try { api.euronextSuche(i) } catch (e: CancellationException) { throw e } catch (_: Exception) { null } ?: return null
            val k = de.troetenrepublik.app.engine.EuronextOptionen.klasse(antwort)
            euronextKlassen[i] = now to k
            k ?: return null
        }
        val heute = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Paris"))
        val html = try { api.euronextTabellen(klasse, heute.year, heute.monthValue) } catch (e: CancellationException) { throw e } catch (_: Exception) { null } ?: return null
        val summen = de.troetenrepublik.app.engine.EuronextOptionen.summen(html) ?: return null
        val neu = summen.takeIf { it.first + it.second >= de.troetenrepublik.app.engine.EuronextOptionen.MINDESTUMSATZ }
            ?.let { MarketApi.OptionVolumes(it.first, it.second, now, MarketApi.Herkunft.EURONEXT) }
        if (neu != null) euronextLetzte[i] = neu
        val v = neu ?: euronextLetzte[i]?.takeIf { now - it.day < 18 * 3_600_000L }
        euronextCache[i] = now to v
        return v
    }

    // Ob der Wert selbst ein US-Optionskuerzel ist, ohne Suche nach einer US-Notierung.
    fun optionenDirekt(symbol: String): Boolean = hasUsOptions(dataSymbol(symbol))

    // Nur der Zwischenspeicher, ohne Abruf: liefert Treffer (auch "keine Daten" als null in Pair) oder nichts.
    // Gibt Pair(gueltig, wert) zurueck, wenn ein frischer Eintrag (unter 30 Minuten) existiert, sonst null.
    fun optionVolumesCached(symbol: String, isin: String = ""): Pair<Boolean, MarketApi.OptionVolumes?>? {
        val eigen = dataSymbol(symbol)
        if (!hasUsOptions(eigen) && !sucheUsNotiz(symbol)) return Pair(true, null)
        val us: Pair<Boolean, MarketApi.OptionVolumes?>? = run {
            val key = if (hasUsOptions(eigen)) eigen else when (val u = usNotiz(symbol)) {
                null -> return@run null
                "" -> return@run Pair(true, null)
                else -> u
            }
            val e = optionCache[key] ?: return@run null
            if (System.currentTimeMillis() - e.first < 30 * 60_000L) Pair(true, e.second) else null
        }
        if (us == null || us.second != null) return us
        // Keine US-Optionen: die Boerse der Aktie, soweit schon gefragt
        val jetzt = System.currentTimeMillis()
        HkexOptionen.code(eigen)?.let { c ->
            val e = hkexCache[c] ?: return null
            return if (jetzt - e.first < 30 * 60_000L) Pair(true, e.second) else null
        }
        TaifexOptionen.code(eigen)?.let { c ->
            val liste = taifexListe?.takeIf { jetzt - it.first < 24 * 3_600_000L } ?: return null
            val vertrag = liste.second[c] ?: return us
            val tag = taifexTag?.takeIf { jetzt - it.first < tagesFrist } ?: return null
            return Pair(true, tagesWert(tag.second[vertrag], MarketApi.Herkunft.TAIFEX))
        }
        JpxOptionen.code(eigen)?.let { c ->
            val tag = jpxTag?.takeIf { jetzt - it.first < 3_600_000L } ?: return null
            return Pair(true, tagesWert(tag.second[c], MarketApi.Herkunft.JPX))
        }
        if (ohneOptionsquelle(eigen)) return us
        val i = optionsIsin(symbol, isin)
        if (i.isEmpty()) return null
        if (!euronextMoeglich(symbol, i)) return us
        val klasse = euronextKlassen[i]?.takeIf { jetzt - it.first < 6 * 3_600_000L } ?: return null
        if (klasse.second != null) {
            val e = euronextCache[i] ?: return null
            if (jetzt - e.first >= 30 * 60_000L) return null
            if (e.second != null) return Pair(true, e.second)
        }
        // Keine Euronext-Optionen oder kein Umsatz dort: die Eurex
        val code = eurexCodeFrisch(i, jetzt) ?: return null
        val c = code.second ?: return us
        val tag = eurexTag?.takeIf { jetzt - it.first < tagesFrist } ?: return null
        return Pair(true, tagesWert(tag.second[c], MarketApi.Herkunft.EUREX))
    }

    // Eine Notierung desselben Werts an einer Boerse; Kurs in EUR.
    data class Handelsplatz(val symbol: String, val boerse: String, val waehrung: String, val kurs: Double, val zeit: Long)

    // Notierungen von symbol an allen Boersen, die Yahoo kennt (Suche ueber den Namen). Als dieselbe Aktie gilt eine
    // Notierung, deren Kurs in Euro hoechstens 10 % vom eigenen abweicht; Hinterlegungsscheine mit anderem
    // Bezugsverhaeltnis fallen so heraus. Der Wert selbst steht vorn.
    suspend fun handelsplaetze(symbol: String): List<Handelsplatz> {
        val eigen = _quotes.value[symbol] ?: refreshOne(symbol) ?: return emptyList()
        val begriff = Zweitnotiz.suchbegriff(wertName(symbol, eigen.name) ?: symbol).ifBlank { symbol }
        val kandidaten = runCatching { api.lookup(begriff) }.getOrDefault(emptyList()).map { it.symbol }.filter { it != symbol }.distinct().take(60)
        val q = snapshot(kandidaten + symbol)
        val liste = (listOf(symbol) + kandidaten).mapNotNull { s ->
            val k = (if (s == symbol) eigen else q[s]) ?: return@mapNotNull null
            if (s != symbol && (!(k.price > 0) || !(eigen.price > 0) || kotlin.math.abs(k.price / eigen.price - 1.0) > 0.10)) return@mapNotNull null
            Handelsplatz(s, k.exchange.ifBlank { s.substringAfter('.', "") }, k.nativeCurrency, k.price, k.marketTime)
        }
        return liste.take(1) + liste.drop(1).sortedBy { it.boerse }
    }

    // Suche bei Yahoo.
    suspend fun search(query: String): List<MarketApi.SearchHit> = api.search(query)

    // Suche immer bei Yahoo (Zuordnung von ISIN beim Import).
    suspend fun yahooSuche(query: String): List<MarketApi.SearchHit> = api.search(query)

    private val kennungCache = java.util.concurrent.ConcurrentHashMap<String, List<MarketApi.SearchHit>>()

    // Suche nach ISIN oder WKN. ISIN fragt zuerst Yahoo; findet Yahoo nichts (WKN immer, manche ISIN), bildet OpenFIGI
    // die Kennung auf Boersenkuerzel ab, und jedes Kuerzel wird bei Yahoo bestaetigt. Die Plaetze stehen geordnet:
    // Heimatboerse zum land, Hauptboerse der ISIN, US, Rest. Nur Treffer werden zwischengespeichert.
    suspend fun kennungSuche(kennung: String, wkn: Boolean, land: String): List<MarketApi.SearchHit> {
        val schluessel = kennung + "|" + land
        kennungCache[schluessel]?.let { return it }
        val yahoo = if (wkn) emptyList() else api.search(kennung)
        var erg = yahoo
        if (erg.isEmpty() || wkn) {
            val figi = api.figi(de.troetenrepublik.app.engine.WertsucheFigi.anfrage(wkn, kennung))
                ?.let { de.troetenrepublik.app.engine.WertsucheFigi.lesen(it) }.orEmpty()
            val kandidaten = de.troetenrepublik.app.engine.WertsucheFigi.kandidaten(figi, kennung, land)
            val bestaetigt = kotlinx.coroutines.coroutineScope {
                kandidaten.map { k -> async { api.search(k).firstOrNull { it.symbol.equals(k, ignoreCase = true) } } }.awaitAll().filterNotNull()
            }
            erg = if (bestaetigt.isNotEmpty() || !wkn) bestaetigt + yahoo
            // WKN ohne Abbildung: die Yahoo-Suche mit der Zeichenfolge als Kuerzel (etwa sechsstellige asiatische Codes)
            else api.search(kennung)
        }
        val geordnet = de.troetenrepublik.app.engine.Wertsuche.plaetzeOrdnen(erg.distinctBy { it.symbol }, kennung, land)
        if (geordnet.isNotEmpty()) kennungCache[schluessel] = geordnet
        return geordnet
    }

    suspend fun profile(symbol: String): MarketApi.Profile? = api.profile(symbol)

    // Erster Absatz eines Wikipedia-Artikels zu einem Titel (null, wenn es keinen gibt)
    suspend fun wikiAuszug(sprache: String, titel: String): String? = api.wikiSummary(sprache, titel)

    // Werte weiterer Kursquellen: antwortet die Quelle mehrfach nicht (Netzfilter, VPN, Stoerung)?
    fun quelleAntwortetNicht(symbol: String): Boolean = api.externAusfall(symbol)

    // Zweite Kursquelle hinter der Hauptquelle: Schluessel des Nutzers und das gemeinsame Abrufkontingent
    fun kursRueckfall(schluessel: () -> String, kontingent: Abrufkontingent, tiingoKey: () -> String = { "" }, twelveKey: () -> String = { "" }) =
        api.rueckfallEinrichten(schluessel, kontingent, tiingoKey, twelveKey)

    // Letzte Meldung der zweiten Kursquelle (Limit, abgelehnter Schluessel) fuer die Seite Zugaenge
    val kursRueckfallMeldung: String? get() = api.rueckfall.meldung
    val tiingoMeldung: String? get() = api.tiingo.meldung
    val twelveMeldung: String? get() = api.twelve.meldung

    suspend fun firmaWikidata(name: String, sprache: String): FirmaWikidata.Angaben? = api.firmaWikidata(name, sprache)
    suspend fun kennzahlen(symbols: List<String>, zusatz: List<String> = emptyList()): Map<String, Map<String, Double>> = api.kennzahlen(symbols, zusatz)
    // Optionskette des Werts: Kuerzel ohne Boersenzusatz (US-Werte auch wie "BRK-B", Indizes) direkt, sonst ueber die
    // US-Notierung derselben Firma.
    suspend fun optionskette(symbol: String): Pair<Double, List<de.troetenrepublik.app.engine.Optionsanalyse.Kontrakt>>? {
        val ticker = if ('.' !in symbol) symbol else optionsKuerzel(symbol) ?: return null
        val (kurs, kontrakte) = api.optionskette(ticker) ?: return null
        return kurs to nasdaqErgaenzt(symbol, ticker, kurs, kontrakte)
    }

    // Alle gelisteten Verfallstermine (Sekunden) der Optionskette, je Sitzung einmal geladen.
    private val optionsTermineCache = java.util.concurrent.ConcurrentHashMap<String, List<Long>>()
    private val optionsTerminCache = java.util.concurrent.ConcurrentHashMap<String, List<de.troetenrepublik.app.engine.Optionsanalyse.Kontrakt>>()

    suspend fun optionsTermine(symbol: String): List<Long>? {
        val ticker = if ('.' !in symbol) symbol else optionsKuerzel(symbol) ?: return null
        optionsTermineCache[ticker]?.let { return it }
        val t = api.optionsTermine(ticker) ?: return null
        optionsTermineCache[ticker] = t
        return t
    }

    // Kontrakte eines Verfallstermins, ein Abruf je Termin und Sitzung; ohne Antwort null.
    suspend fun optionsTermin(symbol: String, datum: Long): List<de.troetenrepublik.app.engine.Optionsanalyse.Kontrakt>? {
        val ticker = if ('.' !in symbol) symbol else optionsKuerzel(symbol) ?: return null
        val key = "$ticker|$datum"
        optionsTerminCache[key]?.let { return it }
        val k = api.optionsTermin(ticker, datum) ?: return null
        optionsTerminCache[key] = k
        return k
    }

    // Ob es fuer den Wert eine Optionskette geben kann, ohne Abruf: Kuerzel ohne Boersenzusatz oder gemerkte US-Notierung.
    fun optionenMoeglich(symbol: String): Boolean {
        if (symbol.any { it == '-' || it == '=' }) return false
        return '.' !in symbol || optionsNotierung(symbol) != null
    }

    // Eigener Abrufer, damit ein Fehlschlag weder Kalender noch Griechen pausiert
    private val nasdaqKetteAbruf by lazy { NasdaqAbruf({ api.anfrage(it, NasdaqKalender.KOPF) }, sperre = AbrufAblage) }

    // Fehlen der Kette offene Kontrakte oder Geld und Brief (NasdaqKette.luecke), fuellt sie die Kette der Nasdaq,
    // ein Abruf fuer alle Verfaelle, 30 Minuten zwischengespeichert. Ohne Antwort bleibt die Kette, wie sie ist.
    private suspend fun nasdaqErgaenzt(
        symbol: String, ticker: String, kurs: Double, kontrakte: List<de.troetenrepublik.app.engine.Optionsanalyse.Kontrakt>,
    ): List<de.troetenrepublik.app.engine.Optionsanalyse.Kontrakt> {
        if (!de.troetenrepublik.app.engine.NasdaqKette.luecke(kontrakte)) return kontrakte
        if (ticker.isBlank() || ticker.any { it == '.' || it == '-' || it == '^' || it == '=' }) return kontrakte
        val jetztS = System.currentTimeMillis() / 1000
        val verfaelle = kontrakte.map { it.verfall }.filter { it > jetztS - 86_400L }
        val von = verfaelle.minOrNull() ?: return kontrakte
        val bis = verfaelle.max()
        val etf = Symbols.typeOf(symbol) == de.troetenrepublik.app.data.AssetType.ETF || Symbols.typeOf(ticker) == de.troetenrepublik.app.data.AssetType.ETF
        // Die Nasdaq fuehrt jeden Wert nur unter einer Anlageklasse; die andere antwortet leer
        for (klasse in listOf(etf, !etf)) {
            val m = ohneAbbruch {
                nasdaqKetteAbruf.holen(de.troetenrepublik.app.engine.NasdaqKette.adresse(ticker, klasse, von, bis), { _ -> 30 * 60_000L }, de.troetenrepublik.app.engine.NasdaqKette::lesen)
            }.getOrNull() ?: return kontrakte
            if (m.isNotEmpty()) return de.troetenrepublik.app.engine.NasdaqKette.ergaenzen(kurs, kontrakte, m, jetztS)
        }
        return kontrakte
    }

    // Die gemerkte US-Notierung des Symbols, wenn sie sich vom Symbol unterscheidet (ohne Abruf).
    fun optionsNotierung(symbol: String): String? {
        val u = usNotiz(symbol) ?: return null
        return u.takeIf { it.isNotEmpty() && it != symbol }
    }

    suspend fun news(symbol: String, lang: String): List<MarketApi.NewsItem> = api.news(symbol, lang)

    // [0.75] true, wenn der juengste Abruf ohne Antwort blieb; trennt in den Werkzeugen "keine Verbindung" von "keine Daten".
    fun ohneVerbindung(): Boolean = api.ohneVerbindung()

    // Google News zum Namen eines Werts, ohne Rechtsform (Zweitnotiz.suchbegriff), in der Sprache der App.
    suspend fun googleNews(name: String, lang: String): List<MarketApi.NewsItem> {
        val begriff = Zweitnotiz.suchbegriff(name).ifBlank { return emptyList() }
        val deutsch = lang == "de"
        val bytes = api.feedBytes(Rss.googleSuche("\"$begriff\"", deutsch)) ?: return emptyList()
        return Rss.lesen(Rss.text(bytes), "Google News", Rss.Kategorie.UNTERNEHMEN, google = true).take(10)
            .map { MarketApi.NewsItem(it.titel, it.link, it.zeit, "", it.herausgeber) }
    }

    suspend fun ecbRate(): MarketApi.EcbRate? = api.ecbDepositRate()

    suspend fun companyInfo(symbol: String, name: String, isin: String = "") = api.companyInfo(symbol, name, isin)

    // Jahresabschluesse fuer Piotroski und Altman, aehnliche Werte fuer den Kennzahlenvergleich (seit 5.91).
    suspend fun jahresabschluesse(symbol: String, felder: List<String>): String? = api.jahresabschluesse(symbol, felder)
    suspend fun aehnliche(symbol: String): List<String> = api.aehnliche(symbol)
}

// Beginn fuer MarketRepository.earlyDaily: 01.12.1999 (Sekunden seit 1970, UTC); das Ende rechnet PortfolioTools.fruehesEnde.
private const val EARLY_FROM = 944_006_400L

// [0.72] Beginn der Monatsschluesse fuer den Sparplan-Rueckblick: 01.12.2014.
private const val MONAT_AB = 1_417_724_800L

// US-Aktien und US-ETFs haben bei Yahoo ein Kuerzel ohne Boersenzusatz (AAPL, NVDA, SPY).
internal fun hasUsOptions(yahooSymbol: String): Boolean = Regex("^[A-Z]{1,5}$").matches(yahooSymbol)
