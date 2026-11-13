package de.troetenrepublik.app

import kotlin.math.abs
import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.Appearance
import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.data.Backup
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.CompanyInfo
import de.troetenrepublik.app.data.Derivative
import de.troetenrepublik.app.data.DerivativeKind
import de.troetenrepublik.app.data.isLong
import de.troetenrepublik.app.data.isOption
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.data.Metric
import de.troetenrepublik.app.data.Order
import de.troetenrepublik.app.data.OrderStatus
import de.troetenrepublik.app.data.OrderType
import de.troetenrepublik.app.data.PlanInterval
import de.troetenrepublik.app.data.Position
import de.troetenrepublik.app.data.PriceAlert
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.data.Range
import de.troetenrepublik.app.data.SavingsPlan
import de.troetenrepublik.app.data.Series
import de.troetenrepublik.app.data.Settings
import de.troetenrepublik.app.data.SettingsStore
import de.troetenrepublik.app.data.Side
import de.troetenrepublik.app.data.Store
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.data.Validity
import de.troetenrepublik.app.data.Valuation
import de.troetenrepublik.app.data.WatchMetric
import de.troetenrepublik.app.engine.DerivatVerlauf
import de.troetenrepublik.app.engine.EigenerFeed
import de.troetenrepublik.app.engine.Err
import de.troetenrepublik.app.engine.Lautschrift
import de.troetenrepublik.app.engine.Derivatives
import de.troetenrepublik.app.engine.MarketHours
import de.troetenrepublik.app.engine.Pricing
import de.troetenrepublik.app.engine.StillhalterDepot
import de.troetenrepublik.app.engine.Ticks
import de.troetenrepublik.app.engine.Trading
import de.troetenrepublik.app.engine.Watchlisten
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.plattform.Tresor
import de.troetenrepublik.app.market.MarketRepository
import de.troetenrepublik.app.market.NewsRepository
import de.troetenrepublik.app.market.CopyRepository
import de.troetenrepublik.app.market.Investoren
import de.troetenrepublik.app.market.Kongress
import de.troetenrepublik.app.plattform.Notifier
import de.troetenrepublik.app.market.Rss
import de.troetenrepublik.app.market.Symbols
import de.troetenrepublik.app.ui.Fmt
import de.troetenrepublik.app.ui.kurzName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

// Bindeglied zwischen Konto, Kursen und Oberflaeche: bewertet das Depot laufend neu, fuehrt
// faellige Orders und Sparplaene aus, schreibt Faktor-Zertifikate fort, bucht Zinsen und
// Dividenden, prueft Preisalarme, wickelt Knock-outs ab und stellt die Handelsfunktionen bereit.
class AppState(
    val store: Store,
    val market: MarketRepository,
    val settings: SettingsStore,
    private val scope: CoroutineScope,
    private val notifier: Notifier? = null,
    // Nachrichtenfeeds; ohne Angabe ein eigener, der dieselbe Kursquelle nutzt.
    val news: NewsRepository = NewsRepository(MarketApi()),
    // Copytrading: Meldungen von Abgeordneten und Investoren; legt seine Daten im Ordner der App ab.
    val copy: CopyRepository? = null,
    // Prediction Markets: Polymarket und Kalshi.
    val prognose: de.troetenrepublik.app.market.PrognoseRepository = de.troetenrepublik.app.market.PrognoseRepository(MarketApi()),
    // Uebersetzung der Prediction Markets mit DeepL.
    val uebersetzer: de.troetenrepublik.app.market.Uebersetzer = de.troetenrepublik.app.market.Uebersetzer(MarketApi(), news.kontingent, null),
) {

    // Der app-weite Scope fuer Hintergrundauftraege, die den Bildschirm ueberdauern (etwa den Aktienfinder).
    internal val hintergrundScope: CoroutineScope get() = scope

    // Abrufkontingente der Dienste mit eigenem Schluessel.
    val kontingent: de.troetenrepublik.app.market.Abrufkontingent get() = news.kontingent

    // Reddit-API mit eigenem Schluessel: Beitraege zu einem Wert und Erwaehnungen in deutschen Subreddits.
    val reddit: de.troetenrepublik.app.market.RedditApi by lazy { de.troetenrepublik.app.market.RedditApi(MarketApi(), news.kontingent) }

    // Behoerdendaten zu US-Firmen: Lobbying, Auftraege, Leerverkaeufe, Spenden, Entlassungen, Patente.
    val behoerden: de.troetenrepublik.app.market.BehoerdenRepository by lazy { de.troetenrepublik.app.market.BehoerdenRepository(MarketApi(), news.kontingent) }

    // Offene US-Register je Firma: klinische Studien, Fahrzeug- und Produktrueckrufe.
    val register: de.troetenrepublik.app.market.RegisterAbruf by lazy { de.troetenrepublik.app.market.RegisterAbruf(MarketApi()) }

    init {
        de.troetenrepublik.app.ui.PmTexte.karte.putAll(uebersetzer.bekannt())
        // Firmendaten fuer die Logos aller Listen und gemerkte Notierungen derselben Aktie
        de.troetenrepublik.app.ui.Logos.firmen = store.companies
        de.troetenrepublik.app.ui.Logos.familienLaden()
        de.troetenrepublik.app.ui.Logos.familieSuchen = { s, n -> familieSuchen(s, n) }
    }

    val account: StateFlow<Account> = store.account
    val quotes: StateFlow<Map<String, Quote>> = market.quotes
    val companies: StateFlow<Map<String, CompanyInfo>> = store.companies

    val settingsFlow: StateFlow<Settings> =
        settings.flow.catch { emit(Settings()) }.stateIn(scope, SharingStarted.Eagerly, Settings())

    // 5 U.S.C. 13107 (seit 5.94): Abrufe von Haus, Senat und Regierung erst nach der einmaligen Bestaetigung
    @Volatile private var ok13107Stand = false

    init {
        prognose.metaculusToken = { settingsFlow.value.keyMetaculus }
        market.kursRueckfall({ settingsFlow.value.keyMassive }, news.kontingent, { settingsFlow.value.keyTiingo }, { settingsFlow.value.keyTwelveData })
        de.troetenrepublik.app.market.EigeneKurse.einrichten(java.io.File(store.ordner, "kursdateien"))
        copy?.freigabe13107 = { ok13107Stand }
        scope.launch { settingsFlow.collect { ok13107Stand = it.ok13107 } }
        scope.launch { settingsFlow.collect {
            de.troetenrepublik.app.ui.LogoAnzeige.farbig = it.logosFarbig
            de.troetenrepublik.app.ui.LogoAnzeige.rund = it.logoRund
            de.troetenrepublik.app.ui.LogoAnzeige.trennlinien = it.trennlinien
            de.troetenrepublik.app.ui.LogoAnzeige.hintergrundEntfernen = it.logoHintergrundEntfernen
        } }
    }

    suspend fun bestaetige13107() {
        settings.update { it.copy(ok13107 = true) }
        ok13107Stand = true
        scope.launch { runCatching { copy?.verzeichnis(neu = true) }; runCatching { senatSpiegelLaden(erzwingen = true) } }
    }

    // Investegate (5.95): Abrufe erst nach der einmaligen Bestaetigung als Privatanleger
    @Volatile private var okInvestegateStand = false

    init {
        scope.launch { settingsFlow.collect { okInvestegateStand = it.okInvestegate } }
    }

    fun newsWichtigEnglischSetzen(an: Boolean) {
        scope.launch { settings.update { it.copy(newsWichtigEnglisch = an) } }
    }

    fun newsWichtigFarbigSetzen(an: Boolean) {
        scope.launch { settings.update { it.copy(newsWichtigFarbig = an) } }
    }

    // Nur englische Quellen: danach neu laden, damit die Schlagzeilen der anderen sofort verschwinden.
    fun newsNurEnglischSetzen(an: Boolean) {
        scope.launch {
            settings.update { it.copy(newsNurEnglisch = an) }
            runCatching { newsLaden() }
        }
    }

    suspend fun bestaetigeInvestegate() {
        settings.update { it.copy(okInvestegate = true) }
        okInvestegateStand = true
    }

    init {
        scope.launch {
            // Land, Waehrung und Handelszeiten: gelten sofort, auch fuer Orders im Hintergrund
            settingsFlow.map { s -> listOf(s.land, s.handelszeiten, s.handelVon, s.handelBis) }.distinctUntilChanged().collect {
                val s = settingsFlow.value
                MarketHours.einstellen(s.land, s.handelszeiten, s.handelVon, s.handelBis)
                de.troetenrepublik.app.engine.Regionen.aktuell = s.land.ifBlank { de.troetenrepublik.app.engine.Regionen.DE.code }
            }
        }
        scope.launch {
            settingsFlow.map { it.waehrung to it.waehrungJeEuro }.distinctUntilChanged().collect { (w, kurs) ->
                if (Fmt.basis != w) { Fmt.basis = w; Fmt.basisJeEuro = 0.0 }
                if (Fmt.basisJeEuro <= 0) Fmt.basisJeEuro = market.fxKurse.value[w]?.takeIf { it > 0 } ?: kurs.takeIf { it > 0 } ?: if (w == "EUR") 1.0 else 0.0
                if (w != "EUR" && w != "USD" && kurs <= 0) runCatching { market.dollarKurs() }
            }
        }
        scope.launch {
            // Frische Wechselkurse: Kurs der gewaehlten Waehrung fuer die Anzeige, der letzte fuer den naechsten Start gemerkt
            market.fxKurse.collect { m ->
                val w = settingsFlow.value.waehrung
                val k = if (w == "EUR") 1.0 else m[w] ?: return@collect
                if (k <= 0) return@collect
                if (Fmt.basis == w) Fmt.basisJeEuro = k
                m["USD"]?.takeIf { it > 0 && Fmt.usdJeEuro <= 0 }?.let { Fmt.usdJeEuro = it }
                if (w != "EUR" && abs(settingsFlow.value.waehrungJeEuro / k - 1) > 0.002) runCatching { settings.update { it.copy(waehrungJeEuro = k) } }
            }
        }
    }

    // ------------------------------------------------------------ Widgets

    // Seite, die ein Tipp auf ein Widget oeffnen soll; die Oberflaeche oeffnet sie, sobald die App entsperrt ist
    private val _sprungziel = MutableStateFlow<String?>(null)
    val sprungziel: StateFlow<String?> = _sprungziel.asStateFlow()

    // Nur bekannte Ziele (de.troetenrepublik.app.data.WidgetZiel.pruefen); alles andere wird verworfen.
    fun springeZu(ziel: String?) {
        de.troetenrepublik.app.data.WidgetZiel.pruefen(ziel)?.let { _sprungziel.value = it }
    }

    fun sprungzielErledigt() { _sprungziel.value = null }

    // Werte fuer das Widget „Werte“ mit der Watchlist als Quelle: die ausgewaehlten, sonst die ganze Watchlist
    // (die Liste im Widget rollt seit 5.4).
    fun widgetSymbole(): List<String> {
        val wl = account.value.watchlist
        val gewaehlt = settingsFlow.value.widgetWerte.split(',').map { it.trim() }.filter { it.isNotEmpty() && it in wl }
        return gewaehlt.ifEmpty { wl }
    }

    // Einzelwert fuer Widgets ohne eigene Wahl: der erste der Watchlist, sonst die groesste Aktie im Depot.
    internal fun widgetEinzelStandard(): String =
        account.value.watchlist.firstOrNull()
            ?: account.value.positions.filter { !it.isDerivative }.maxByOrNull { it.invested }?.symbol.orEmpty()

    // Einzelwerte, die auf dem Startbildschirm liegen, und der Standardwert (nur mit Einzelwert-Widget).
    private fun widgetEinzelSymbole(arten: Set<String> = de.troetenrepublik.app.plattform.Widgets.arten()): List<String> =
        if (de.troetenrepublik.app.plattform.Widgets.ART_EINZEL !in arten) emptyList()
        else (de.troetenrepublik.app.plattform.Widgets.einzelSymbole() + widgetEinzelStandard()).filter { it.isNotBlank() }.distinct().take(12)

    // Zur Auswahl im Einzelwert-Widget: Positionen (ohne Derivate) und Watchlist.
    fun widgetEinzelAuswahl(): List<Pair<String, String>> {
        val acc = account.value
        val q = quotes.value
        val symbole = (acc.positions.filter { !it.isDerivative }.map { it.symbol } + acc.watchlist).distinct()
        return symbole.map { it to kurzName(it, q[it]?.name ?: acc.positions.firstOrNull { p -> p.symbol == it }?.name.orEmpty()) }
    }

    // Tagesverlaeufe und erwartete Dividenden fuer die Widgets, im Hintergrund nachgeladen
    @Volatile private var widgetKurven: Map<String, List<Double>> = emptyMap()
    @Volatile private var widgetKurvenAm = 0L
    @Volatile private var widgetDividenden: List<de.troetenrepublik.app.engine.PortfolioTools.ExpectedDividend>? = null
    // Kurse fuer das Widget „Märkte“, hoechstens alle fuenf Minuten
    @Volatile private var widgetMaerkte: Map<String, Quote> = emptyMap()
    @Volatile private var widgetDividendenAm = 0L
    @Volatile private var widgetDividendenBestand = ""
    // Stimmungsbarometer und Schlagzeilen zu den Positionen (Symbol, Name, Meldung), seit 5.4
    @Volatile private var widgetStimmung: Stimmungen? = null
    // Gesamtstimmung fuer das Widget (5.95): Zeitpunkt der Rechnung und Wert, Stand zusaetzlich auf dem Geraet
    @Volatile private var widgetGesamt: Pair<Long, Double>? = null
    // Seit 0.77: Sorgen und Put/Call fuer Widgets, die sie waehlen
    @Volatile private var widgetSorgen: de.troetenrepublik.app.engine.Stimmungsbarometer.Barometer? = null
    @Volatile private var widgetPutCall: de.troetenrepublik.app.engine.WeitereQuellen.PutCall? = null
    @Volatile private var widgetNews: List<Triple<String, String, de.troetenrepublik.app.market.MarketApi.NewsItem>>? = null
    @Volatile private var widgetNewsAm = 0L
    @Volatile private var widgetNewsBestand = ""
    // Wichtigste Schlagzeilen fuer das News-Widget (5.94), hoechstens alle 30 Minuten neu
    @Volatile private var widgetWichtig: List<Rss.Eintrag>? = null
    @Volatile private var widgetWichtigAm = 0L
    // Kategorie je Schlagzeile der Wichtigsten (Etikett in der Zeile des Widgets)
    @Volatile private var widgetWichtigKat: Map<String, de.troetenrepublik.app.engine.WichtigThemen.Kat> = emptyMap()
    // Seit 0.68: Schlagzeilen je Inhalt der News-Widgets ausser NEWS_DEPOT, dazu die Inhalte beim Laden
    @Volatile private var widgetNewsListen: Map<String, List<Rss.Eintrag>>? = null
    @Volatile private var widgetNewsListenArten: Set<String> = emptySet()
    @Volatile private var widgetNewsEnglisch = false
    private val widgetLadeLock = kotlinx.coroutines.sync.Mutex()
    // Heatmaps der Widgets (0.68) je Schluessel mit Ladezeit und Bestand (Depot, Watchlist): am Tag hoechstens alle
    // 30 Minuten, laengere Zeitraeume alle drei Stunden, bei geaendertem Bestand sofort
    @Volatile private var widgetHeatmaps: Map<String, Triple<Long, String, List<HeatmapWert>>> = emptyMap()

    // Bestaende fuer die erwarteten Dividenden: Aktien und ETFs ohne Derivate, wie im Dividendenkalender.
    private fun widgetDividendenBestaende(): List<Position> = account.value.positions.filter {
        !it.isDerivative && Symbols.typeOf(it.symbol).let { t -> t == AssetType.STOCK || t == AssetType.ETF }
    }

    // Tagesverlaeufe (Depot und Einzelwerte, hoechstens alle fuenf Minuten oder bei neuer Auswahl) und erwartete
    // Dividenden (alle sechs Stunden oder bei geaendertem Bestand) fuer die Widgets laden. Mit App-Sperre nichts.
    private suspend fun widgetZusatzLaden() = widgetLadeLock.withLock {
        if (store.sperre.aktiv || store.gesperrt.value) return@withLock
        val arten = de.troetenrepublik.app.plattform.Widgets.arten()
        if (arten.isEmpty()) return@withLock
        val jetzt = System.currentTimeMillis()
        val einzel = widgetEinzelSymbole(arten)
        val depot = de.troetenrepublik.app.plattform.Widgets.ART_DEPOT in arten && account.value.positions.isNotEmpty()
        // Verlaeufe der Depot-Widgets: heute immer, seit Jahresbeginn und seit Kauf nur, wenn ein Widget sie zeigt
        val zeiten = if (depot) de.troetenrepublik.app.plattform.Widgets.depotZeitraeume() else emptySet()
        val depotKurven = listOfNotNull(
            WIDGET_DEPOT,
            WIDGET_DEPOT_JAHR.takeIf { de.troetenrepublik.app.data.WidgetDaten.ZEIT_JAHR in zeiten },
            WIDGET_DEPOT_KAUF.takeIf { de.troetenrepublik.app.data.WidgetDaten.ZEIT_KAUF in zeiten },
        ).takeIf { depot }.orEmpty()
        val fehlt = !widgetKurven.keys.containsAll(einzel + depotKurven)
        if ((depot || einzel.isNotEmpty()) && (jetzt - widgetKurvenAm > 5 * 60_000L || fehlt)) {
            val k = HashMap<String, List<Double>>()
            if (depot) {
                runCatching { portfolioSeries(Range.D1).candles.map { it.close } }.getOrNull()
                    ?.let { k[WIDGET_DEPOT] = de.troetenrepublik.app.data.WidgetZiel.ausduennen(it) }
                if (WIDGET_DEPOT_JAHR in depotKurven) {
                    val heute = java.time.LocalDate.now(java.time.ZoneId.systemDefault())
                    val jahrStart = heute.withDayOfYear(1)
                    val jahr = runCatching { portfolioZeitraum(jahrStart.toEpochDay(), heute.toEpochDay()).candles.map { it.close } }.getOrNull().orEmpty()
                        .takeIf { it.size >= 2 }
                        // Ohne Verlauf des Jahres (zu wenige Kerzen): der Verlauf seit dem ersten Kauf, soweit er im Jahr liegt
                        ?: runCatching {
                            val von = jahrStart.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                            portfolioSeries(Range.MAX).candles.filter { it.t >= von }.map { it.close }
                        }.getOrNull()
                    jahr?.takeIf { it.size >= 2 }?.let { k[WIDGET_DEPOT_JAHR] = de.troetenrepublik.app.data.WidgetZiel.ausduennen(it) }
                }
                if (WIDGET_DEPOT_KAUF in depotKurven) {
                    runCatching { portfolioSeries(Range.MAX).candles.map { it.close } }.getOrNull()
                        ?.let { k[WIDGET_DEPOT_KAUF] = de.troetenrepublik.app.data.WidgetZiel.ausduennen(it) }
                }
            }
            for (sym in einzel) {
                runCatching { market.series(sym, Range.D1).candles.map { it.close } }.getOrNull()
                    ?.let { k[sym] = de.troetenrepublik.app.data.WidgetZiel.ausduennen(it) }
            }
            widgetKurven = k
            widgetKurvenAm = jetzt
        }
        if (de.troetenrepublik.app.plattform.Widgets.ART_MAERKTE in arten) {
            runCatching { market.snapshot(WIDGET_MAERKTE.map { it.first }, 5 * 60_000L) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { widgetMaerkte = it }
        }
        if (de.troetenrepublik.app.plattform.Widgets.ART_STIMMUNG in arten) {
            runCatching { stimmungen() }.getOrNull()?.let { widgetStimmung = it }
            val w = de.troetenrepublik.app.plattform.Widgets
            // Gesamtstimmung nur, wenn ein Widget sie zeigt oder sie bei Aktien USA fuer CNN einspringt
            if (w.barometerGewaehlt(de.troetenrepublik.app.data.WidgetDaten.STIMMUNG_GESAMT) ||
                (widgetStimmung?.cnn == null && w.barometerGewaehlt(de.troetenrepublik.app.data.WidgetDaten.STIMMUNG_AKTIEN))) runCatching { widgetGesamtLaden() }
            // Sorgen und Put/Call nur, wenn ein Widget sie zeigt (Auswahl oder "Alle"); die anderen Barometer liefert stimmungen()
            if (w.barometerGewaehlt(de.troetenrepublik.app.data.WidgetDaten.STIMMUNG_SORGEN)) runCatching { sorgenBarometer() }.getOrNull()?.let { widgetSorgen = it }
            if (w.barometerGewaehlt(de.troetenrepublik.app.data.WidgetDaten.STIMMUNG_PUTCALL)) putCall()?.let { widgetPutCall = it }
        }
        if (de.troetenrepublik.app.plattform.Widgets.ART_NEWS in arten) {
            // Schlagzeilen der sechs groessten Positionen ohne Derivate, hoechstens alle 30 Minuten oder bei neuem Bestand
            val positionen = account.value.positions.filter { !it.isDerivative && !it.isKnockedOut }.sortedByDescending { it.invested }.take(6)
            val sprache = settingsFlow.value.sprache.code
            val schluessel = positionen.joinToString("|") { it.symbol } + "|" + sprache
            if (schluessel != widgetNewsBestand || jetzt - widgetNewsAm > 30 * 60_000L || widgetNews == null) {
                // Dieselben Schlagzeilen wie auf der Wertpapierseite (Yahoo und Google), gestaffelt geholt
                val alle = positionen.chunked(de.troetenrepublik.app.engine.WertNachrichten.werteJeGruppe(6)).flatMap { gruppe ->
                    coroutineScope {
                        gruppe.map { p ->
                            async {
                                runCatching { newsFor(p.symbol, newsName(p.symbol, p.name)) }.getOrDefault(emptyList()).map { Triple(p.symbol, kurzName(p.symbol, p.name), it) }
                            }
                        }.awaitAll().flatten()
                    }
                }
                widgetNews = alle.distinctBy { it.third.title.trim().lowercase() }.sortedByDescending { it.third.published }.take(20)
                widgetNewsAm = jetzt
                widgetNewsBestand = schluessel
            }
            // Wichtigste, alle Schlagzeilen und eigene Feeds (5.94, 0.68): die Feeds einmal laden, hoechstens alle
            // 30 Minuten oder wenn ein Widget einen anderen Inhalt zeigt
            val newsArten = de.troetenrepublik.app.plattform.Widgets.newsArten() - de.troetenrepublik.app.data.WidgetDaten.NEWS_DEPOT
            val nurEnglisch = settingsFlow.value.wichtigNurEnglisch
            if (newsArten.isNotEmpty() && (widgetNewsListen == null || newsArten != widgetNewsListenArten || nurEnglisch != widgetNewsEnglisch ||
                    jetzt - widgetWichtigAm > 30 * 60_000L)) {
                val s = settingsFlow.value
                runCatching { news.laden(NewsRepository.Auftrag(newsQuellen(s), s.sprache == Lang.DE, ohneAbruf = Rss.NUR_ANSICHT, nurEnglisch = s.nurEnglischeQuellen)) }
                // laden wirft ohne Netz nicht; geladen heisst: irgendeine Quelle (auch der Zwischenspeicher) hat etwas geliefert
                val geladen = news.stand.value.eintraege.isNotEmpty()
                val eintraege = news.stand.value.eintraege.filter { it.zeit in 1..(jetzt + 5 * 60_000L) && jetzt - it.zeit < 2 * 86_400_000L && !Rss.nurAnsicht(it) }
                val listen = HashMap<String, List<Rss.Eintrag>>()
                if (de.troetenrepublik.app.data.WidgetDaten.NEWS_WICHTIG in newsArten) {
                    // dieselbe Auswahl wie der Reiter „Wichtigste“, je Geschichte eine Schlagzeile
                    val konto = account.value
                    val depot = de.troetenrepublik.app.market.Wichtig.depotBegriffe(
                        konto.positions.map { it.name } + konto.watchlist.mapNotNull { Symbols.find(it)?.name },
                        konto.positions.map { it.symbol } + konto.watchlist,
                    )
                    // Themen im Wechsel der Kategorien, wie im Reiter (engine/WichtigThemen)
                    val themen = withContext(Dispatchers.Default) { de.troetenrepublik.app.engine.WichtigThemen.themen(eintraege, depot, nurEnglisch).take(20) }
                    widgetWichtigKat = themen.associate { it.kopf.id to it.kat }
                    listen[de.troetenrepublik.app.data.WidgetDaten.NEWS_WICHTIG] = themen.map { it.kopf }
                }
                val quellen = newsQuellen(s)
                val feeds = newsFeeds(s)
                for (art in newsArten) {
                    val liste = when {
                        art == de.troetenrepublik.app.data.WidgetDaten.NEWS_ALLE -> eintraege
                        art == de.troetenrepublik.app.data.WidgetDaten.NEWS_GEMERKT -> newsGemerkt(s)
                        art.startsWith(de.troetenrepublik.app.data.WidgetDaten.NEWS_ANBIETER) ->
                            runCatching { Rss.Anbieter.valueOf(art.removePrefix(de.troetenrepublik.app.data.WidgetDaten.NEWS_ANBIETER)) }.getOrNull()
                                ?.let { a -> Rss.filter(eintraege, a, quellen.filterNot { EigenerFeed.istZugang(it) }) }
                        art.startsWith(de.troetenrepublik.app.data.WidgetDaten.NEWS_FEED) ->
                            feeds.firstOrNull { de.troetenrepublik.app.data.WidgetDaten.newsFeed(it.kennung) == art }?.let { EigenerFeed.eintraege(eintraege, it, quellen) }
                        else -> null
                    } ?: continue
                    listen[art] = liste.sortedByDescending { it.zeit }.distinctBy { it.titel.trim().lowercase() }.take(20)
                }
                // Ohne Netz und ohne alten Stand beim naechsten Auffrischen erneut versuchen
                if (geladen || eintraege.isNotEmpty()) {
                    widgetWichtig = listen[de.troetenrepublik.app.data.WidgetDaten.NEWS_WICHTIG]
                    widgetNewsListen = listen
                    widgetNewsListenArten = newsArten
                    widgetNewsEnglisch = nurEnglisch
                    widgetWichtigAm = jetzt
                }
            }
        }
        if (de.troetenrepublik.app.plattform.Widgets.ART_HEATMAP in arten) runCatching { widgetHeatmapsLaden(jetzt) }
        // Globus (Boerse alle 30 Minuten, IWF-Daten einen Tag)
        if (de.troetenrepublik.app.plattform.Widgets.ART_GLOBUS in arten) runCatching { widgetGlobusLaden(jetzt) }
        // Karten der Kategorie-Widgets (0.73): je Art mit eigener Frist, aus den ohnehin zwischengespeicherten Abrufen
        runCatching { widgetKartenLaden(de.troetenrepublik.app.plattform.Widgets.kartenSchluessel(), jetzt) }.onFailure { if (it is CancellationException) throw it }
        if (de.troetenrepublik.app.plattform.Widgets.ART_DIVIDENDEN !in arten) return@withLock
        val bestaende = widgetDividendenBestaende()
        val schluessel = bestaende.joinToString("|") { "${it.key}:${it.qty}" }
        if (schluessel != widgetDividendenBestand || jetzt - widgetDividendenAm > 6 * 3_600_000L) {
            val quellen = coroutineScope {
                bestaende.map { p ->
                    async {
                        // Mit angekuendigtem Ex-Tag und Zahltag wie im Dividendenkalender
                        de.troetenrepublik.app.ui.dividendenQuelle(this@AppState, p.key, kurzName(p.symbol, p.name), p.symbol, p.qty)
                    }
                }.awaitAll()
            }
            widgetDividenden = de.troetenrepublik.app.engine.PortfolioTools.expectedDividends(quellen, jetzt)
            widgetDividendenAm = jetzt
            widgetDividendenBestand = schluessel
        }
    }

    // Werte des Globus-Widgets (0.69) je Einfaerbung und Land (alpha-3), nur fuer die Einfaerbungen, die ein Widget zeigt.
    @Volatile private var widgetGlobus: Map<String, Map<String, Double>> = emptyMap()
    @Volatile private var widgetGlobusAm = 0L

    private suspend fun widgetGlobusLaden(jetzt: Long) {
        val modi = de.troetenrepublik.app.plattform.Widgets.globusModi()
        val karte = globusKarte() ?: return
        val boerse = de.troetenrepublik.app.engine.GlobusModus.BOERSE.name
        val neu = HashMap(widgetGlobus)
        fun rund(w: Double) = kotlin.math.round(w * 100) / 100
        if (boerse in modi && (boerse !in neu || jetzt - widgetGlobusAm > 25 * 60_000L)) {
            val indizes = runCatching { globusIndizes(warteDirekt = true) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrDefault(emptyMap())
            if (indizes.isNotEmpty()) {
                neu[boerse] = indizes.mapNotNull { (a2, w) -> karte.nachA2(a2)?.let { it.a3 to rund(w) } }.toMap()
                widgetGlobusAm = jetzt
            }
        }
        if (modi.any { it != boerse }) {
            val d = runCatching { imfDaten() }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull() ?: imfGespeichert()
            if (d != null) {
                val jahr = java.time.LocalDate.now().year
                for (m in listOf(de.troetenrepublik.app.engine.GlobusModus.WACHSTUM, de.troetenrepublik.app.engine.GlobusModus.AUSBLICK, de.troetenrepublik.app.engine.GlobusModus.INFLATION)) {
                    if (m.name !in modi) continue
                    neu[m.name] = karte.laender.mapNotNull { l ->
                        val z = de.troetenrepublik.app.engine.Imf.land(d, l.a3, jahr)
                        val w = when (m) {
                            de.troetenrepublik.app.engine.GlobusModus.WACHSTUM -> z.wachstum
                            de.troetenrepublik.app.engine.GlobusModus.AUSBLICK -> z.ausblick
                            else -> z.inflation
                        }
                        w?.takeIf { it.isFinite() }?.let { l.a3 to rund(it) }
                    }.toMap()
                }
            }
        }
        widgetGlobus = neu
    }

    // Heatmaps fuer die Widgets (0.68): nur Quellen und Zeitraeume, die ein Widget zeigt. Eine Heatmap wird neu geladen,
    // wenn sie aelter als ihre Frist ist oder sich der Bestand geaendert hat; ohne Antwort bleibt die alte. Leer ist bei
    // Depot und Watchlist ein Ergebnis, bei Markt und Indizes ein Fehler.
    private suspend fun widgetHeatmapsLaden(jetzt: Long) {
        val alt = widgetHeatmaps
        val neu = HashMap<String, Triple<Long, String, List<HeatmapWert>>>()
        for ((quelle, zeitraum) in de.troetenrepublik.app.plattform.Widgets.heatmapAuswahl()) {
            val schluessel = de.troetenrepublik.app.data.WidgetDaten.heatmapSchluessel(quelle, zeitraum)
            val vorher = alt[schluessel]
            // Eine Minute Spielraum, damit die Pruefung alle 30 Minuten die Tagesheatmap jedes Mal auffrischt
            val frist = (if (zeitraum == 0) 30 * 60_000L else 3 * 3_600_000L) - 60_000L
            val eigen = quelle == HeatmapQuelle.DEPOT || quelle == HeatmapQuelle.WATCHLIST
            val bestand = when (quelle) {
                HeatmapQuelle.DEPOT -> account.value.positions.filter { !it.isDerivative }.map { it.symbol }.distinct().sorted().joinToString(",")
                HeatmapQuelle.WATCHLIST -> account.value.watchlist.sorted().joinToString(",")
                else -> ""
            }
            if (vorher != null && vorher.second == bestand && jetzt - vorher.first < frist) { neu[schluessel] = vorher; continue }
            val tage = de.troetenrepublik.app.engine.Branchentrends.ZEITRAEUME.getOrElse(zeitraum) { 1 }
            val werte = runCatching { heatmap(quelle, tage) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()
            when {
                werte != null && (eigen || werte.isNotEmpty() || vorher == null) -> neu[schluessel] = Triple(jetzt, bestand, werte)
                vorher != null -> neu[schluessel] = vorher
            }
        }
        widgetHeatmaps = neu
    }

    // Heatmaps fuer WidgetDaten: Anteile statt Betraegen (auch beim Depot), Titel aus Quelle und Zeitraum.
    private fun widgetHeatmapDaten(): Map<String, de.troetenrepublik.app.data.WidgetHeatmap> = widgetHeatmaps.mapValues { (schluessel, eintrag) ->
        val quelle = schluessel.substringBefore('|')
        val zeitraum = schluessel.substringAfter('|').toIntOrNull()?.coerceIn(0, 5) ?: 0
        val werte = eintrag.third
        val summe = werte.sumOf { w -> w.gewicht.takeIf { it.isFinite() && it > 0 } ?: 0.0 }.takeIf { it > 0 } ?: 1.0
        de.troetenrepublik.app.data.WidgetHeatmap(
            titel = de.troetenrepublik.app.ui.heatmapQuelleTitel(quelle) + " · " + S.rangeCaption(de.troetenrepublik.app.ui.HEATMAP_ZEITRAEUME[zeitraum]),
            kacheln = werte.map { w ->
                de.troetenrepublik.app.data.WidgetKachel(
                    // Im Widget die kuerzeste Beschriftung: bei Zahlenkuerzeln das erste Wort des Namens
                    de.troetenrepublik.app.engine.HeatmapBeschriftung.kandidaten(w.symbol, w.name).lastOrNull().orEmpty(),
                    (w.gewicht.takeIf { it.isFinite() && it > 0 } ?: 0.0) / summe, w.aenderung?.takeIf { it.isFinite() },
                    w.aenderung?.takeIf { it.isFinite() }?.let { Fmt.signedPct(it, 2) }.orEmpty(),
                    w.aenderung?.takeIf { it.isFinite() }?.let { Fmt.signedPct(it, 1) }.orEmpty(),
                )
            },
            voll = de.troetenrepublik.app.engine.HeatmapBeschriftung.VOLL_PROZENT.getOrElse(zeitraum) { 3.0 },
            ziel = de.troetenrepublik.app.data.WidgetZiel.heatmap(quelle, zeitraum),
            hinweis = when (quelle) {
                HeatmapQuelle.DEPOT -> S.widgetKeinePositionen
                HeatmapQuelle.WATCHLIST -> S.widgetLeer
                else -> S.heatmapLeer
            },
        )
    }

    // Inhalt der Widgets, fertig formatiert; mit App-Sperre ohne Betraege; null, solange Kurse fehlen (sonst stuende 0 da).
    fun widgetDaten(): de.troetenrepublik.app.data.WidgetDaten? {
        val titel = de.troetenrepublik.app.data.WidgetDaten(
            titelDepot = S.portfolio, titelWerte = S.widgetWerte, titelBestand = S.widgetBestand, titelMover = S.widgetMover,
            titelDividenden = S.widgetDividenden, schnell = listOf(S.search, S.orders, S.maerkteTitel, S.news),
            schnellNamen = widgetSchnellNamen(),
            titelMaerkte = S.maerkteTitel,
        )
        if (store.sperre.aktiv || store.gesperrt.value) {
            if (store.sperre.aktiv) widgetKartenVergessen()
            return titel.copy(hinweis = S.widgetGesperrt, gesperrt = true)
        }
        val s = settingsFlow.value
        val q = quotes.value
        val acc = account.value
        if (acc.positions.any { !it.isKnockedOut && (q[it.symbol]?.price ?: 0.0) <= 0 }) return null
        val v = Trading.value(acc, q, rate = (s.interestPct / 100.0).coerceIn(0.0, 0.2))
        val verdeckt = s.hideBalances
        val tag = v.dayChange
        val basis = v.securities - tag
        val pct = if (basis > 0) tag / basis * 100 else 0.0
        val kurven = widgetKurven

        // Tagesaenderung je Stueck als Betrag (seit 5.4 fuer „Betrag statt Prozent“)
        fun stueckBetrag(k: Quote): String = if (k.prevClose > 0) Fmt.signedMoney(k.price - k.prevClose) else ""
        fun wert(sym: String): de.troetenrepublik.app.data.WidgetWert? {
            val k = q[sym]?.takeIf { it.price > 0 } ?: return null
            return de.troetenrepublik.app.data.WidgetWert(
                kurzName(sym, k.name), Fmt.price(k.price), Fmt.signedPct(k.changePct), k.changePct >= 0,
                de.troetenrepublik.app.data.WidgetZiel.instrument(sym), betrag = stueckBetrag(k), pct = k.changePct,
            )
        }
        fun zeile(p: de.troetenrepublik.app.data.ValuedPosition, rechts: String) = de.troetenrepublik.app.data.WidgetWert(
            if (p.position.isDerivative) de.troetenrepublik.app.ui.positionsName(p.position) else kurzName(p.position.symbol, p.position.name), rechts,
            Fmt.signedPct(p.dayChangePct), p.dayChangePct >= 0,
            if (p.position.isDerivative) de.troetenrepublik.app.data.WidgetZiel.derivat(p.key) else de.troetenrepublik.app.data.WidgetZiel.instrument(p.position.symbol),
            // Je Stueck aus Sicht des Inhabers: bei verkauften Optionen mit umgekehrtem Vorzeichen
            betrag = if (p.prevPrice > 0) Fmt.signedMoney((p.price - p.prevPrice) * (if (p.position.verkauft) -1 else 1)) else "", pct = p.dayChangePct,
        )

        val werte = widgetSymbole().mapNotNull { wert(it) }
        // Verkaufte Optionen zaehlen mit ihrem negativen Wert mit
        val aktiv = v.positions.filter { !it.position.isKnockedOut && it.value != 0.0 }
        val bestand = aktiv.sortedByDescending { it.value }.map { zeile(it, Fmt.price(it.price)) }
        // Top & Flop: Positionen mit Vortageskurs, nach Tagesaenderung in Prozent
        val bewegt = aktiv.filter { it.prevPrice > 0 }
        val rechts: (de.troetenrepublik.app.data.ValuedPosition) -> String = { if (verdeckt) "" else Fmt.signedMoney(it.dayChange) }
        val gewinner = bewegt.filter { it.dayChangePct > 0.0 }.sortedByDescending { it.dayChangePct }.take(3).map { zeile(it, rechts(it)) }
        val verlierer = bewegt.filter { it.dayChangePct < 0.0 }.sortedBy { it.dayChangePct }.take(3).map { zeile(it, rechts(it)) }
        val einzel = widgetEinzelSymbole().mapNotNull { sym -> wert(sym)?.let { sym to it.copy(kurve = kurven[sym].orEmpty()) } }.toMap()
        // Dividenden: erhalten im laufenden Jahr (Buchungen), erwartet in den naechsten zwoelf Monaten
        val zone = java.time.ZoneId.systemDefault()
        val jahr = java.time.Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone).year
        val erhalten = acc.transactions.filter { it.type == TxType.DIVIDEND && java.time.Instant.ofEpochMilli(it.ts).atZone(zone).year == jahr }.sumOf { it.amount }
        // Erwartet am Zahltag und nach Steuern wie im Dividendenkalender und im Handelskalender (Dividendenerwartung)
        val erwartet = widgetDividenden?.let { de.troetenrepublik.app.engine.Dividendenerwartung.fuerKonto(it, acc.tax, s.allowance, System.currentTimeMillis()) }
        val betrag: (Double) -> String = { if (verdeckt) "•••• " + Fmt.symbol else Fmt.money(it) }
        val divHinweis = when {
            widgetDividendenBestaende().isEmpty() -> S.widgetDivKeinBestand
            erwartet == null -> S.toolLoading
            erwartet.isEmpty() -> S.widgetDivKeine
            else -> ""
        }
        return titel.copy(
            stand = S.widgetStand(Fmt.time(System.currentTimeMillis())),
            hinweis = if (werte.isEmpty()) S.widgetLeer else "",
            depotWert = if (verdeckt) "•••• " + Fmt.symbol else Fmt.money(v.securities),
            depotWertKurz = if (verdeckt) "•••• " + Fmt.symbol else Fmt.money(v.securities, 0),
            depotAenderung = (if (verdeckt) "" else Fmt.signedMoney(tag) + " · ") + Fmt.signedPct(pct),
            depotHoch = tag >= 0,
            werte = werte,
            depotKurve = if (acc.positions.isEmpty()) emptyList() else kurven[WIDGET_DEPOT].orEmpty(),
            bestand = bestand,
            bestandHinweis = if (bestand.isEmpty()) S.widgetKeinePositionen else "",
            einzel = einzel,
            einzelStandard = widgetEinzelStandard(),
            gewinner = gewinner,
            verlierer = verlierer,
            moverHinweis = when {
                aktiv.isEmpty() -> S.widgetKeinePositionen
                gewinner.isEmpty() && verlierer.isEmpty() -> S.widgetMoverRuhig
                else -> ""
            },
            divErhaltenText = S.widgetDivErhalten(jahr),
            divErhalten = betrag(erhalten),
            divErwartetText = S.widgetDivErwartet,
            divErwartet = erwartet?.let { l -> betrag(l.sumOf { it.netto }) } ?: "–",
            divNaechste = erwartet.orEmpty().take(4).map { e ->
                de.troetenrepublik.app.data.WidgetWert("${Fmt.dayMonth(e.zahltag)} · ${e.name}", betrag(e.netto), "", true)
            },
            divHinweis = divHinweis,
            maerkte = WIDGET_MAERKTE.mapNotNull { (sym, name) ->
                val k = (widgetMaerkte[sym] ?: q[sym])?.takeIf { it.price > 0 } ?: return@mapNotNull null
                de.troetenrepublik.app.data.WidgetWert(name, Fmt.price(k.price), Fmt.signedPct(k.changePct), k.changePct >= 0,
                    de.troetenrepublik.app.data.WidgetZiel.instrument(sym), betrag = stueckBetrag(k), pct = k.changePct)
            },
            depotZeitraeume = widgetZeitraeume(aktiv, tag, pct, verdeckt, acc.positions.isNotEmpty()),
            listenWerte = de.troetenrepublik.app.plattform.Widgets.listenSymbole().mapNotNull { sym -> wert(sym)?.let { sym to it } }.toMap(),
            titelStimmung = S.widgetStimmung,
            stimmung = widgetStimmungen(),
            titelNews = S.widgetNewsTitel,
            news = widgetNews.orEmpty().map { (sym, name, n) ->
                de.troetenrepublik.app.data.WidgetNachricht(
                    n.title, listOfNotNull(name, n.published.takeIf { it > 0 }?.let { Fmt.relative(it) }).joinToString(" · "),
                    de.troetenrepublik.app.data.WidgetZiel.instrument(sym),
                )
            },
            newsHinweis = when {
                acc.positions.none { !it.isDerivative } -> S.widgetKeinePositionen
                widgetNews == null -> S.toolLoading
                else -> S.widgetNewsKeine
            },
            titelNewsWichtig = S.widgetNewsWichtigTitel,
            newsWichtig = widgetWichtig.orEmpty().map { e ->
                de.troetenrepublik.app.data.WidgetNachricht(
                    e.titel, listOfNotNull(
                        widgetWichtigKat[e.id]?.let { de.troetenrepublik.app.i18n.WichtigTexte.kat(settingsFlow.value.sprache == Lang.DE, it) },
                        e.quelle.ifBlank { null }, e.zeit.takeIf { it > 0 }?.let { Fmt.relative(it) },
                    ).joinToString(" · "),
                    de.troetenrepublik.app.data.WidgetZiel.nachricht(e.id),
                )
            },
            newsWichtigHinweis = if (widgetWichtig == null) S.toolLoading else S.widgetNewsWichtigKeine,
            newsListen = widgetNewsListen.orEmpty().filterKeys { it != de.troetenrepublik.app.data.WidgetDaten.NEWS_WICHTIG }.mapValues { (_, l) ->
                l.map { e ->
                    de.troetenrepublik.app.data.WidgetNachricht(
                        e.titel, listOfNotNull(e.quelle.ifBlank { null }, e.zeit.takeIf { it > 0 }?.let { Fmt.relative(it) }).joinToString(" · "),
                        de.troetenrepublik.app.data.WidgetZiel.nachricht(e.id),
                    )
                }
            },
            newsListenTitel = buildMap {
                put(de.troetenrepublik.app.data.WidgetDaten.NEWS_ALLE, S.widgetNewsAlleTitel)
                put(de.troetenrepublik.app.data.WidgetDaten.NEWS_GEMERKT, S.newsAnbieter(Rss.Anbieter.GEMERKT))
                Rss.Anbieter.entries.forEach { put(de.troetenrepublik.app.data.WidgetDaten.newsAnbieter(it.name), S.newsAnbieter(it)) }
                newsFeeds().forEach { put(de.troetenrepublik.app.data.WidgetDaten.newsFeed(it.kennung), it.name) }
            },
            newsListenLaedt = S.toolLoading,
            newsListenKeine = S.widgetNewsListeKeine,
            heatmaps = widgetHeatmapDaten(),
            heatmapHinweis = S.toolLoading,
            globus = widgetGlobus,
            globusTitel = mapOf(
                "BOERSE" to S.globusBoerse, "WACHSTUM" to S.globusWachstum, "AUSBLICK" to S.globusAusblick, "INFLATION" to S.globusInflation,
            ),
            karten = widgetKartenGespeichert() + widgetKartenKonto() + widgetKartenAusKonto(acc, v, verdeckt, System.currentTimeMillis(), rueckgangStand(acc), s.kategorien, q, etfLaenderEigen),
            kartenHinweis = S.trLaedt,
        )
    }

    // Beschriftung je Ziel des Schnellzugriffs (5.94).
    private fun widgetSchnellNamen(): Map<String, String> = mapOf(
        "search" to S.search, "orders" to S.orders, "maerkte" to S.maerkteTitel, "news" to S.news, "heatmap" to S.heatmap,
        "activity" to S.activity, "plans" to S.plans, "rueckblick" to S.rbTitel, "depotverlauf" to S.widgetSchnellVerlauf,
    )

    // Depot im Widget je Zeitraum: heute (Tagesaenderung), seit Jahresbeginn (Depotkurve wie in der App, ohne Geldfluesse)
    // und seit Kauf (Wert der offenen Positionen gegen ihren Einstand). Ohne Verlauf fehlt der Jahreswert.
    private fun widgetZeitraeume(
        aktiv: List<de.troetenrepublik.app.data.ValuedPosition>, tag: Double, tagPct: Double, verdeckt: Boolean, mitKurve: Boolean,
    ): Map<String, de.troetenrepublik.app.data.WidgetZeitraum> {
        val kurven = widgetKurven
        fun betrag(v: Double) = if (verdeckt) "" else Fmt.signedMoney(v)
        val m = HashMap<String, de.troetenrepublik.app.data.WidgetZeitraum>()
        m[de.troetenrepublik.app.data.WidgetDaten.ZEIT_HEUTE] = de.troetenrepublik.app.data.WidgetZeitraum(
            S.today, betrag(tag), Fmt.signedPct(tagPct), tag >= 0, if (mitKurve) kurven[WIDGET_DEPOT].orEmpty() else emptyList(),
        )
        kurven[WIDGET_DEPOT_JAHR]?.takeIf { mitKurve && it.size >= 2 && it.first() > 0 }?.let { k ->
            val d = k.last() - k.first()
            m[de.troetenrepublik.app.data.WidgetDaten.ZEIT_JAHR] = de.troetenrepublik.app.data.WidgetZeitraum(S.widgetZeitJahr, betrag(d), Fmt.signedPct((k.last() / k.first() - 1) * 100), d >= 0, k)
        }
        val einstand = aktiv.sumOf { it.invested }
        if (einstand > 0) {
            val d = aktiv.sumOf { it.value } - einstand
            m[de.troetenrepublik.app.data.WidgetDaten.ZEIT_KAUF] = de.troetenrepublik.app.data.WidgetZeitraum(
                S.widgetZeitKauf, betrag(d), Fmt.signedPct(d / einstand * 100), d >= 0, kurven[WIDGET_DEPOT_KAUF].orEmpty(),
            )
        }
        return m
    }

    // Gesamtstimmung fuer das Widget (5.95) auf dem Geraet, der Prozess endet zwischen zwei Laeufen oft
    private val widgetGesamtDatei by lazy { de.troetenrepublik.app.plattform.SichereDatei(java.io.File(store.ordner, "gesamtstimmung.txt")) }

    // Zehn Minuten Pause zwischen zwei Rechenversuchen, auch wenn kein Wert kommt
    @Volatile private var widgetGesamtVersuch = 0L

    // Gesamtstimmung fuer das Widget (5.95) holen: Stand vom Geraet lesen, nur wenn aelter als drei Stunden neu rechnen
    private suspend fun widgetGesamtLaden() {
        val jetzt = System.currentTimeMillis()
        widgetGesamt?.takeIf { jetzt - it.first < 3 * 3_600_000L }?.let { return }
        if (widgetGesamt == null) {
            // Inhalt der Datei: zeitMs;wert
            val inhalt = withContext(Dispatchers.IO) { runCatching { widgetGesamtDatei.lesen().toString(Charsets.UTF_8) }.getOrNull() }
            val teile = inhalt?.split(';')
            val zeit = teile?.getOrNull(0)?.trim()?.toLongOrNull()
            val wert = teile?.getOrNull(1)?.trim()?.toDoubleOrNull()
            if (zeit != null && wert != null) {
                widgetGesamt = zeit to wert
                if (jetzt - zeit < 3 * 3_600_000L) return
            }
        }
        if (jetzt - widgetGesamtVersuch < 600_000L) return
        widgetGesamtVersuch = jetzt
        val geladen = runCatching { gesamtstimmungMitStand(mitBreite = false) }.getOrNull() ?: return
        val (stand, e) = geladen
        val altStand = widgetGesamt?.first
        // Ein alter Wert bekommt keinen neuen Zeitpunkt, die Datei behaelt dann ihren Stand
        if (jetzt - stand >= 3 * 3_600_000L || (altStand != null && altStand >= stand)) return
        widgetGesamt = stand to e.wert
        withContext(Dispatchers.IO) { runCatching { widgetGesamtDatei.schreiben((stand.toString() + ";" + e.wert).toByteArray(Charsets.UTF_8)) } }
    }

    // Stimmungsbarometer fuer das Widget (5.95): Aktien (CNN, sonst die Gesamtstimmung, sonst der eigene Index),
    // Krypto (alternative.me), die Gesamtstimmung und seit 0.77 alle weiteren Barometer der App als eigene Auswahl. art waehlt Beschriftung und Anbieter.
    // Werte aelter als 24 Stunden bleiben weg.
    private fun widgetStimmungen(): Map<String, de.troetenrepublik.app.data.WidgetStimmung> {
        val s = widgetStimmung
        val g = widgetGesamt?.takeIf { System.currentTimeMillis() - it.first < 24 * 3_600_000L }
        val sorgen = widgetSorgen ?: s?.sorgen
        val putCall = widgetPutCall?.let { de.troetenrepublik.app.engine.Stimmungsbarometer.putCallBarometer(it) }
        if (s == null && g == null && sorgen == null && putCall == null) return emptyMap()
        fun eintrag(art: String, wert: Double, stufe: de.troetenrepublik.app.engine.Stimmungsbarometer.Stufe): de.troetenrepublik.app.data.WidgetStimmung =
            de.troetenrepublik.app.data.WidgetStimmung(S.sbKachel(art), kotlin.math.round(wert).toInt().coerceIn(0, 100), S.sbStufe(stufe.ordinal), stufe.ordinal, S.sbAnbieter(art))
        // Art, Wert und Stufe gehoeren zusammen, damit Barometer und Gesamtstimmung gleich behandelt werden
        val aktien = s?.cnn?.let { Triple("cnn", it.wert, it.stufe) } ?: g?.let { Triple("gesamt", it.second, de.troetenrepublik.app.engine.Stimmungsbarometer.stufe(it.second)) } ?: s?.aktien?.let { Triple("aktien", it.wert, it.stufe) }
        val aus = LinkedHashMap<String, de.troetenrepublik.app.data.WidgetStimmung>()
        g?.let { aus[de.troetenrepublik.app.data.WidgetDaten.STIMMUNG_GESAMT] = eintrag("gesamt", it.second, de.troetenrepublik.app.engine.Stimmungsbarometer.stufe(it.second)) }
        aktien?.let { (art, wert, stufe) -> aus[de.troetenrepublik.app.data.WidgetDaten.STIMMUNG_AKTIEN] = eintrag(art, wert, stufe) }
        s?.aaii?.let { aus[de.troetenrepublik.app.data.WidgetDaten.STIMMUNG_AAII] = eintrag("aaii", it.barometer.wert, it.barometer.stufe) }
        s?.krypto?.let { aus[de.troetenrepublik.app.data.WidgetDaten.STIMMUNG_KRYPTO] = eintrag("krypto", it.wert, it.stufe) }
        s?.wsb?.let { aus[de.troetenrepublik.app.data.WidgetDaten.STIMMUNG_WSB] = eintrag("wsb", it.barometer.wert, it.barometer.stufe) }
        sorgen?.let { aus[de.troetenrepublik.app.data.WidgetDaten.STIMMUNG_SORGEN] = eintrag("sorgen", it.wert, it.stufe) }
        s?.vix?.let { aus[de.troetenrepublik.app.data.WidgetDaten.STIMMUNG_VIX] = eintrag("vix", it.wert, it.stufe) }
        s?.sicher?.let { aus[de.troetenrepublik.app.data.WidgetDaten.STIMMUNG_SICHER] = eintrag("sicher", it.wert, it.stufe) }
        putCall?.let { aus[de.troetenrepublik.app.data.WidgetDaten.STIMMUNG_PUTCALL] = eintrag("putcall", it.wert, it.stufe) }
        return aus
    }

    // Werte des Widgets „Märkte“ mit dem Namen, der im Widget steht.
    private val WIDGET_MAERKTE = listOf(
        "^GDAXI" to "DAX", "^GSPC" to "S&P 500", "^NDX" to "Nasdaq 100", "^STOXX50E" to "Euro Stoxx 50", "GC=F" to "Gold", "BTC-EUR" to "Bitcoin",
    )

    // Widgets neu zeichnen, wenn der Inhalt vollstaendig ist (auch beim Platzieren eines Widgets).
    fun widgetsSchieben() {
        if (!de.troetenrepublik.app.plattform.Widgets.verfuegbar) return
        runCatching { widgetDaten()?.let { de.troetenrepublik.app.plattform.Widgets.aktualisieren(it) } }
    }

    // Beim Platzieren oder Einstellen eines Widgets: Tagesverlaeufe und Dividenden nachladen (wenn alt) und neu zeichnen;
    // laeuft die App nicht im Vordergrund, vorher auch die Kurse holen.
    fun widgetsNachladen() {
        if (!de.troetenrepublik.app.plattform.Widgets.verfuegbar) return
        scope.launch {
            if (!foreground) runCatching { widgetsAuffrischen() }
            else {
                runCatching { widgetZusatzLaden() }
                widgetsSchieben()
            }
        }
    }

    // Fuer die Pruefung im Hintergrund: Kurse der Positionen und Widget-Werte holen, dann die Widgets neu zeichnen.
    suspend fun widgetsAuffrischen() {
        if (!store.sperre.aktiv && !store.gesperrt.value) {
            market.track(account.value.positions.map { it.symbol } + widgetSymbole() + widgetEinzelSymbole() + de.troetenrepublik.app.plattform.Widgets.listenSymbole())
            runCatching { market.refresh() }
            runCatching { widgetZusatzLaden() }
        }
        widgetsSchieben()
    }

    // ------------------------------------------------------------ App-Sperre

    // Die Oberflaeche ist gesperrt: beim Start mit eingerichteter Sperre und nach einer Minute im Hintergrund.
    private val _gesperrt = MutableStateFlow(store.sperre.aktiv)
    val gesperrt: StateFlow<Boolean> = _gesperrt.asStateFlow()
    private val _sperrArt = MutableStateFlow(store.sperre.art)

    // PIN (de.troetenrepublik.app.data.Sperre.PIN) oder Passwort; null ohne Sperre.
    val sperrArt: StateFlow<String?> = _sperrArt.asStateFlow()
    @Volatile private var fehlversuche = store.sperre.fehlversuche
    @Volatile private var wartenBis = store.sperre.wartenBis
    @Volatile private var imHintergrundSeit = 0L

    // Noch so lange warten (ms), bevor die naechste Eingabe geprueft wird.
    fun sperrWartezeit(): Long = (wartenBis - System.currentTimeMillis()).coerceAtLeast(0)

    // Mit PIN oder Passwort entsperren; null bei Erfolg, sonst die Meldung. Ab dem fuenften Fehlversuch waechst die Wartezeit.
    suspend fun entsperren(geheimnis: String): String? {
        val warten = sperrWartezeit()
        if (warten > 0) return S.sperreWarten((warten + 999) / 1000)
        val k = withContext(Dispatchers.Default) { runCatching { store.sperre.oeffnen(geheimnis) }.getOrNull() }
        if (k == null) {
            fehlversuche++
            if (fehlversuche >= 5) wartenBis = System.currentTimeMillis() + 30_000L * (1L shl minOf(fehlversuche - 5, 5))
            store.sperre.fehlversucheMerken(fehlversuche, wartenBis)
            return S.sperreFalsch(fehlversuche)
        }
        if (!store.entsperren(k)) return S.sperreDatenFehlen
        fehlversuche = 0
        wartenBis = 0L
        store.sperre.fehlversucheMerken(0, 0L)
        // Rueckblicke jetzt entschluesselt neu lesen
        _rueckblicke.value = null
        _gesperrt.value = false
        syncTracked()
        scope.launch { runCatching { tresorBereinigen() } }
        scope.launch { runCatching { flex.beimStart() } }
        widgetsSchieben()
        return null
    }

    // Sperre einschalten: Datenschluessel verpacken, dann das Konto verschluesselt speichern.
    suspend fun sperreEinrichten(geheimnis: String, art: String): String? {
        if (!de.troetenrepublik.app.plattform.Krypto.verfuegbar) return S.sperreNurApp
        if (store.gesperrt.value) return S.sperreDatenFehlen
        val k = store.datenschluessel ?: de.troetenrepublik.app.plattform.Krypto.zufall(32)
        try {
            withContext(Dispatchers.Default) { store.sperre.setzen(geheimnis, art, k) }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { return S.exportFailed }
        store.schuetzen(k)
        runCatching { rueckblickNeuVerpacken() }
        _sperrArt.value = art
        widgetsSchieben()
        _toast.value = S.sperreEingerichtet
        return null
    }

    // PIN oder Passwort aendern: nur der Datenschluessel wird neu verpackt.
    suspend fun sperreAendern(alt: String, neu: String, art: String): String? {
        val k = withContext(Dispatchers.Default) { runCatching { store.sperre.oeffnen(alt) }.getOrNull() } ?: return S.sperreFalsch(0)
        try {
            withContext(Dispatchers.Default) { store.sperre.setzen(neu, art, k) }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { return S.exportFailed }
        _sperrArt.value = art
        _toast.value = S.sperreGeaendert
        return null
    }

    // Ausstehende Aenderungen sofort schreiben (Desktop beim Schliessen).
    suspend fun sichern() = store.sichern()

    // Sperre ausschalten: erst unverschluesselt speichern, dann die Sperre loeschen.
    suspend fun sperreAusschalten(geheimnis: String): String? {
        withContext(Dispatchers.Default) { runCatching { store.sperre.oeffnen(geheimnis) }.getOrNull() } ?: return S.sperreFalsch(0)
        if (!store.freigeben()) return S.sperreSchreibFehler
        runCatching { rueckblickNeuVerpacken() }
        store.sperre.entfernen()
        _sperrArt.value = null
        widgetsSchieben()
        _toast.value = S.sperreAusgeschaltet
        return null
    }

    // PIN vergessen: verschluesselte Daten loeschen, neues Uebungsdepot, Sperre aus.
    suspend fun sperreZuruecksetzen() {
        store.verwerfen()
        store.sperre.entfernen()
        _sperrArt.value = null
        fehlversuche = 0
        wartenBis = 0L
        _gesperrt.value = false
        syncTracked()
    }

    // Ende der Einrichtung: Startguthaben in Euro buchen, Name, Land, Waehrung und Handelszeiten merken.
    fun einrichten(e: de.troetenrepublik.app.ui.Einrichtung) {
        reset(e.kapitalEuro, announce = false)
        scope.launch {
            // Ein PIN-Eintrag in den Einstellungen wird hier geloescht.
            // Nach der Einrichtung einer neuen Installation folgt einmal die Kurzanleitung.
            settings.update {
                it.copy(
                    onboarded = true, ownerName = e.name, guidePending = true, land = e.land, waehrung = e.waehrung,
                    handelszeiten = e.handelszeiten, handelVon = e.handelVon, handelBis = e.handelBis, anzeigeStufe = 0,
                )
            }
        }
    }

    // Land, Waehrung und Handelszeiten setzen (Einrichtung und Profil).
    fun regionSetzen(land: String, waehrung: String, handelszeiten: String, von: Int, bis: Int) {
        scope.launch {
            settings.update { it.copy(land = land, waehrung = waehrung, handelszeiten = handelszeiten, handelVon = von, handelBis = bis) }
            if (waehrung != "EUR") runCatching { market.dollarKurs() }
        }
    }

    // Risikoloser Zins fuer die Bewertung von Optionsscheinen: der Einlagesatz.
    val riskFree: Double get() = (settingsFlow.value.interestPct / 100.0).coerceIn(0.0, 0.2)

    // Aktuelle Kurse offener Wetten je Wette; ohne Eintrag gilt der gespeicherte Kurs.
    private val _wettKurse = MutableStateFlow<Map<String, Double>>(emptyMap())
    val wettKurse: StateFlow<Map<String, Double>> = _wettKurse.asStateFlow()

    val valuation: StateFlow<Valuation> =
        combine(store.account, market.quotes, settingsFlow, _wettKurse) { acc, q, s, wk ->
            Trading.value(acc, q, rate = (s.interestPct / 100.0).coerceIn(0.0, 0.2))
                .copy(wetten = acc.wetten.filter { it.offen }.sumOf { it.anteile * (wk[it.id] ?: it.kurs) })
        }.stateIn(scope, SharingStarted.Eagerly, Valuation())

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast

    fun clearToast() { _toast.value = null }

    // Anzeige auf die naechste Waehrung schalten, in der Reihenfolge der Einstellung (Fmt.stufen; Vorgabe: gewaehlte,
    // US-Dollar, Euro, wieder die gewaehlte). Ohne Kurs wird er zuerst geladen; gelingt das nicht, wird die Waehrung
    // uebersprungen.
    fun waehrungUmschalten() {
        scope.launch {
            val stufen = Fmt.stufen
            var n = (Fmt.stufe + 1) % stufen.size
            if (stufen[n] == "USD" && Fmt.usdJeEuro <= 0) {
                val k = runCatching { market.dollarKurs() }.getOrNull()
                if (k != null) Fmt.usdJeEuro = k else { _toast.value = S.usdUnavailable; n = (n + 1) % stufen.size }
            } else if (Fmt.jeEuro(stufen[n]) <= 0) {
                runCatching { market.dollarKurs() }
                Fmt.kurse = market.fxKurse.value
                if (Fmt.jeEuro(stufen[n]) <= 0) n = (n + 1) % stufen.size
            }
            waehrungStufe(n)
        }
    }

    // Stufe der Anzeige setzen und merken (0 = gewaehlte Waehrung).
    fun waehrungStufe(n: Int) {
        Fmt.stufe = n
        val kurs = Fmt.usdJeEuro
        scope.launch { settings.update { it.copy(anzeigeStufe = n, anzeigeUsd = Fmt.stufen.getOrNull(n) == "USD", usdJeEuro = if (kurs > 0) kurs else it.usdJeEuro) } }
    }

    // 5.92: Waehrungen beim Antippen und alle Wechselkurse fuer die Anzeige
    init {
        scope.launch {
            settingsFlow.map { it.waehrungFolge }.distinctUntilChanged().collect {
                Fmt.folge = de.troetenrepublik.app.engine.Waehrungsfolge.lesen(it)
            }
        }
        scope.launch { market.fxKurse.collect { Fmt.kurse = it } }
    }

    // Eigene Folge speichern; die Anzeige springt auf die erste Stufe der neuen Folge.
    fun waehrungFolgeSetzen(folge: List<String>) {
        val text = de.troetenrepublik.app.engine.Waehrungsfolge.schreiben(folge)
        Fmt.folge = de.troetenrepublik.app.engine.Waehrungsfolge.lesen(text)
        Fmt.stufe = 0
        scope.launch {
            settings.update { it.copy(waehrungFolge = text, anzeigeStufe = 0) }
            if (Fmt.stufen.any { Fmt.jeEuro(it) <= 0 }) runCatching { market.dollarKurs() }
        }
    }

    // Neue gewaehlte Waehrung: in einer eigenen Folge kommt sie nach vorn, wenn sie fehlt; angezeigt wird sie sofort.
    fun hauptwaehrungSetzen(waehrung: String) {
        val s = settingsFlow.value
        val folge = de.troetenrepublik.app.engine.Waehrungsfolge.mitBasis(de.troetenrepublik.app.engine.Waehrungsfolge.lesen(s.waehrungFolge), waehrung)
        val stufe = de.troetenrepublik.app.engine.Waehrungsfolge.stufen(folge, waehrung).indexOf(waehrung).coerceAtLeast(0)
        regionSetzen(s.land.ifBlank { de.troetenrepublik.app.engine.Regionen.DE.code }, waehrung, s.handelszeiten, s.handelVon, s.handelBis)
        Fmt.folge = folge
        Fmt.stufe = stufe
        scope.launch { settings.update { it.copy(waehrungFolge = de.troetenrepublik.app.engine.Waehrungsfolge.schreiben(folge), anzeigeStufe = stufe) } }
    }

    // 5.92: Dividenden einer Position wiederanlegen (true/false) oder der Einstellung folgen (null).
    fun wiederanlageSetzen(symbol: String, an: Boolean?) {
        store.update { a -> a.copy(wiederanlage = if (an == null) a.wiederanlage - symbol else a.wiederanlage + (symbol to an)) }
    }

    fun showToast(msg: String) { _toast.value = msg }

    private val started = AtomicBoolean(false)
    @Volatile private var foreground = false
    val imVordergrund: Boolean get() = foreground

    fun start() {
        if (!started.compareAndSet(false, true)) return
        // Quellen-Buendel und Gesundheitszaehler der Datenquellen (seit 5.92)
        de.troetenrepublik.app.market.Quellen.starten(store.ordner, scope)
        // Gepflegte Dateien aus dem signierten Datenbuendel (hoechstens einmal am Tag)
        de.troetenrepublik.app.market.Daten.starten(store.ordner, scope)
        // Letzter Stand der Prognoselisten fuer sofortiges Anzeigen beim Oeffnen
        prognose.ordner = java.io.File(store.ordner, "prognose")
        syncTracked()
        scope.launch {
            while (isActive) {
                if (foreground) runCatching { tick() }
                delay(5_000)
            }
        }
        scope.launch {
            store.account.collect { acc ->
                syncTracked()
                // Den groessten Rueckgang aus der Kurve rechnen, damit auch das Widget ihn kennt
                val jetzt = System.currentTimeMillis()
                if (acc.positions.isNotEmpty() && rueckgangStand(acc) == null && jetzt - rueckgangVersuch > 600_000L) {
                    rueckgangVersuch = jetzt
                    scope.launch { runCatching { maxRueckgang() } }
                }
            }
        }
        // Widgets auf dem Startbildschirm: solange die App laeuft, alle 30 Sekunden mit den aktuellen Kursen; Tagesverlaeufe
        // und Dividenden laedt die App nach, wenn sie alt sind
        scope.launch {
            while (isActive) {
                if (foreground) {
                    runCatching { widgetZusatzLaden() }
                    widgetsSchieben()
                }
                delay(30_000)
            }
        }
        scope.launch { runCatching { tresorBereinigen() } }
        scope.launch { runCatching { schluesselUebernehmen() }; runCatching { secKontaktVorbelegen() } }
        scope.launch { runCatching { flex.beimStart() } }
        scope.launch {
            settingsFlow.map { it.refreshSeconds }.distinctUntilChanged().collect { seconds ->
                if (foreground) market.startPolling(seconds * 1000L)
            }
        }
        // Ein Klick auf eine Mitteilung mit Ziel (Tagesrueckblick) oeffnet die Seite (Desktop; Android traegt das Ziel selbst)
        notifier?.beiZiel = { springeZu(it) }
        scope.launch {
            settingsFlow.map { it.notifications }.distinctUntilChanged().collect { notifier?.enabled = it }
        }
        scope.launch {
            settingsFlow.map { de.troetenrepublik.app.engine.Mitteilungsarten.gesperrteKanaele(it) }.distinctUntilChanged().collect { notifier?.gesperrt = it }
        }
        scope.launch {
            // Stufe der Anzeige (gewaehlte Waehrung, Dollar, Euro) und zuletzt bekannter Dollarkurs aus den Einstellungen
            settingsFlow.map { it.anzeigeStufe to it.usdJeEuro }.distinctUntilChanged().collect { (stufe, kurs) ->
                Fmt.stufe = stufe
                if (Fmt.usdJeEuro <= 0 && kurs > 0) Fmt.usdJeEuro = kurs
            }
        }
        scope.launch {
            // Frischer Dollarkurs; der letzte wird fuer den naechsten Start gemerkt
            market.usdJeEuro.collect { k ->
                if (k > 0) {
                    Fmt.usdJeEuro = k
                    if (abs(settingsFlow.value.usdJeEuro - k) > 1e-4) runCatching { settings.update { it.copy(usdJeEuro = k) } }
                }
            }
        }
        scope.launch {
            settingsFlow.map { it.sprache }.distinctUntilChanged().collect { lang ->
                I18n.lang = lang
                notifier?.refreshChannelNames()
            }
        }
        scope.launch {
            // Schlagzeilen fuer die Karte „News“ (Zahl der neuen) und Mitteilungen: beim Start und alle fuenf Minuten im Vordergrund
            while (isActive) {
                if (foreground) runCatching { newsLaden(ohneAbruf = Rss.NUR_ANSICHT); newsMelden(); newsWertMelden() }
                // Tagesrueckblick am Abend
                if (foreground) runCatching { rueckblickPruefen() }
                if (foreground) runCatching { weltMeldungPruefen() }
                delay(5 * 60_000L)
            }
        }
        scope.launch {
            // Tagesrueckblick zur gewaehlten Uhrzeit, auch im Hintergrund, solange die App laeuft
            settingsFlow.map { it.tagesrueckblick to it.rueckblickMinute }.distinctUntilChanged().collectLatest { (an, ab) ->
                if (!an) return@collectLatest
                while (true) {
                    val ziel = de.troetenrepublik.app.engine.Tagesrueckblick.naechsteAusfuehrung(System.currentTimeMillis(), ab)
                    // In kurzen Schritten warten, damit es nach einem Ruhezustand stimmt
                    while (System.currentTimeMillis() < ziel) delay(minOf(60_000L, ziel - System.currentTimeMillis()))
                    for (versuch in 0 until 6) {
                        runCatching { rueckblickPruefen() }
                        if (de.troetenrepublik.app.engine.Tagesrueckblick.faellig(System.currentTimeMillis(), rueckblickeLaden(), ab = ab, maerkte = rueckblickMaerkte()) == null) break
                        delay(10 * 60_000L)
                    }
                }
            }
        }
        scope.launch {
            // Copytrading: neue Meldungen gefolgter Abgeordneter und Investoren, beim Start und alle sechs Stunden
            delay(20_000)
            while (isActive) {
                if (foreground) {
                    runCatching { copyPruefen() }
                    delay(CopyRepository.VERZEICHNIS_MS)
                } else delay(60_000L)
            }
        }
        scope.launch {
            // Neue Fassung der App: einmal am Tag; jede Stunde nachsehen, ob die Abfrage faellig ist
            delay(30_000)
            while (isActive) {
                if (foreground) runCatching { updatePruefen() }
                delay(60 * 60_000L)
            }
        }
        scope.launch {
            // Prediction Markets: Kurse und Aufloesung offener Wetten, beim Start und alle fuenf Minuten
            delay(15_000)
            while (isActive) {
                if (foreground && store.account.value.wetten.any { it.offen }) runCatching { wettenPruefen() }
                delay(5 * 60_000L)
            }
        }
        scope.launch {
            // Firmendaten (Website, Logo) der Depotwerte und der Merkliste nachladen, damit auch noch nie geoeffnete
            // Werte in den Listen ihr Logo zeigen; nacheinander und mit Abstand
            delay(25_000)
            val a = store.account.value
            val werte = (a.positions.map { it.symbol to it.name } + a.watchlist.map { it to (Symbols.find(it)?.name ?: it) })
                .distinctBy { it.first }.filter { (s, _) -> store.companies.value[s] == null && Symbols.typeOf(s) == AssetType.STOCK }
            for ((s, n) in werte.take(40)) {
                if (!isActive) break
                if (foreground) runCatching { companyInfoFor(s, n) }
                delay(1_500)
            }
            // Verzoegerte deutsche Notierungen im Depot und auf der Merkliste: gleiche Notierungen mit Echtzeitkursen
            // suchen, damit Kurs und Tageschart nicht eine Viertelstunde hinterherlaufen
            val verzoegert = (a.positions.map { it.symbol to it.name } + a.watchlist.map { it to (Symbols.find(it)?.name ?: it) })
                .distinctBy { it.first }.filter { (s, _) -> de.troetenrepublik.app.market.Zweitnotiz.verzoegert(s) && Symbols.typeOf(s) == AssetType.STOCK }
            for ((s, n) in verzoegert.take(40)) {
                if (!isActive) break
                if (foreground) runCatching { familieFinden(s, quotes.value[s]?.name?.takeIf { it.isNotBlank() } ?: n) }
            }
        }
        scope.launch {
            // EZB-Einlagesatz einmal am Tag nachladen
            while (isActive) {
                if (foreground) runCatching { refreshEcb() }
                delay(6 * 3_600_000L)
            }
        }
    }

    fun resume() {
        foreground = true
        // App-Sperre: nach einer Minute im Hintergrund wieder sperren (die Daten bleiben im Speicher entschluesselt)
        val weg = imHintergrundSeit
        if (store.sperre.aktiv && weg > 0 && System.currentTimeMillis() - weg > SPERRE_NACH_MS) _gesperrt.value = true
        market.startPolling(settingsFlow.value.refreshSeconds * 1000L)
        // Beim Start ist die App noch nicht im Vordergrund, wenn die Tagesschleife anlaeuft
        scope.launch { runCatching { refreshEcb() } }
        // Schluessel, die bei gesperrtem Geraet nicht lesbar waren, jetzt erneut lesen und alte Klartexte uebernehmen
        scope.launch { runCatching { settings.tresorNeuLesen(); schluesselUebernehmen() } }
    }

    fun pause() {
        foreground = false
        widgetsSchieben()
        imHintergrundSeit = System.currentTimeMillis()
        market.stopPolling()
    }

    // Preisalarme bei geschlossener App (Android, etwa alle 30 Minuten mit den Nachrichten): nur wenn Alarme gesetzt
    // sind, mit den Kursen einer Sammelabfrage (hoechstens eine Minute alt). Im Vordergrund prueft der Takt.
    suspend fun alarmeImHintergrund() {
        if (foreground || store.gesperrt.value) return
        val s = runCatching { settings.flow.first() }.getOrDefault(settingsFlow.value)
        if (!s.notifications || s.broker.isNotBlank()) return
        val symbole = account.value.alerts.map { it.symbol }.filter { it.isNotBlank() }.distinct()
        if (symbole.isEmpty()) return
        val q = runCatching { market.snapshot(symbole, 60_000L) }.getOrDefault(emptyMap())
        if (q.isEmpty()) return
        val jetzt = System.currentTimeMillis()
        var vorher: Account? = null
        var nachher: Account? = null
        store.update { acc ->
            vorher = acc
            Trading.checkAlerts(acc, q, jetzt).first.also { nachher = it }
        }
        I18n.lang = s.sprache
        val v = vorher
        val n = nachher
        if (v != null && n != null) announce(v, n)
        alarmartenPruefen(jetzt, q, takt = false)
    }

    // Weitere Alarmarten (0.76): Schnitt, Volumen, Ex-Tag, Insider-Kauf. Hoechstens alle zwei Minuten, mit den
    // Kerzen und dem Profil aus dem Zwischenspeicher; ohne Meldung bleibt die Buchung unberuehrt.
    private var alarmartenZuletzt = 0L
    private val alarmartenLaeuft = java.util.concurrent.atomic.AtomicBoolean(false)
    private val insiderAlarmAbruf = ConcurrentHashMap<String, Long>()

    private suspend fun alarmartenPruefen(now: Long, q: Map<String, Quote>, takt: Boolean = true) {
        val liste = account.value.alerts.filter { de.troetenrepublik.app.engine.Alarmarten.istArt(it) && it.art != de.troetenrepublik.app.engine.Alarmarten.NEWS }
        if (liste.isEmpty() || q.isEmpty()) return
        if (takt && now - alarmartenZuletzt < 120_000L) return
        if (!alarmartenLaeuft.compareAndSet(false, true)) return
        try {
            alarmartenZuletzt = now
            val heute = Trading.handelstag(now)
            val ergebnisse = HashMap<String, de.troetenrepublik.app.engine.Alarmarten.Ergebnis>()
            for (a in liste) {
                val quote = q[a.symbol]?.takeIf { Trading.isQuoteFresh(it, now) } ?: continue
                val daten = runCatching { alarmDaten(a, quote, now, heute) }.getOrNull() ?: continue
                ergebnisse[a.id] = de.troetenrepublik.app.engine.Alarmarten.pruefen(a, daten)
            }
            if (ergebnisse.isEmpty()) return
            var vorher: Account? = null
            var nachher: Account? = null
            store.update { acc ->
                vorher = acc
                Trading.artenMelden(acc, ergebnisse, q, now).first.also { nachher = it }
            }
            val v = vorher
            val n = nachher
            if (v != null && n != null) announce(v, n)
        } finally {
            alarmartenLaeuft.set(false)
        }
    }

    // Abrufe fuer Analysten und 8-K-Warnzeichen: nach einem Versuch (auch einem gescheiterten) frueestens in 30 Minuten wieder
    private val artAbruf = ConcurrentHashMap<String, Long>()

    private fun abrufErlaubt(a: PriceAlert, now: Long): Boolean {
        val k = a.art + "|" + a.symbol
        if (now - (artAbruf[k] ?: 0L) < 30 * 60_000L) return false
        artAbruf[k] = now
        return true
    }

    private suspend fun alarmDaten(a: PriceAlert, quote: Quote, now: Long, heute: Long): de.troetenrepublik.app.engine.Alarmarten.Daten {
        var schnitt: Double? = null
        var normal: Double? = null
        var exTag: Long? = null
        var insider = emptyList<de.troetenrepublik.app.market.SecInsider.Geschaeft>()
        var quartalTag: Long? = null
        var analyst: List<de.troetenrepublik.app.engine.Analystenbilanz.Urteil>? = null
        var achtK: List<String>? = null
        var regelLage: de.troetenrepublik.app.engine.RegelGlocke.Lage? = null
        // Regel des Strategietests: hoechstens einmal je Handelstag und Wert, aus den zwischengespeicherten Tageskerzen
        // (bis zehn Jahre); die Kerze von heute zaehlt noch nicht
        if (de.troetenrepublik.app.engine.RegelGlocke.istArt(a.art) && a.gemeldetAm != heute && abrufErlaubt(a, now)) {
            de.troetenrepublik.app.engine.RegelGlocke.regel(a.art)?.let { r ->
                val kerzen = dailyCloses(a.symbol).filter { Trading.handelstag(it.t) < heute }
                regelLage = withContext(Dispatchers.Default) { de.troetenrepublik.app.engine.RegelGlocke.lage(kerzen, r) }
            }
        }
        when (a.art) {
            de.troetenrepublik.app.engine.Alarmarten.SCHNITT, de.troetenrepublik.app.engine.Alarmarten.VOLUMEN -> {
                // Abgeschlossene Tage: die Kerze von heute zaehlt noch nicht
                val kerzen = market.candles(a.symbol, Range.Y1).filter { Trading.handelstag(it.t) < heute }
                schnitt = de.troetenrepublik.app.engine.Alarmarten.schnitt(kerzen.map { it.close })
                normal = de.troetenrepublik.app.engine.Alarmarten.normalVolumen(kerzen)
            }
            de.troetenrepublik.app.engine.Alarmarten.EXDIV ->
                exTag = profileFor(a.symbol)?.exDividendDate?.takeIf { it > 0 }?.let { it / 86_400_000L }
            de.troetenrepublik.app.engine.Alarmarten.INSIDER, de.troetenrepublik.app.engine.Alarmarten.INSIDER_VK -> {
                if (settingsFlow.value.secKontakt.isNotBlank() && now - (insiderAlarmAbruf[a.symbol] ?: 0L) > 30 * 60_000L) {
                    insiderAlarmAbruf[a.symbol] = now
                    runCatching { secLaden(symbol = a.symbol) }
                }
                insider = _sec.value?.geschaefte.orEmpty()
            }
            // Quartalszahlen: Termin aus dem Profil im Zwischenspeicher, wie der Ex-Tag
            de.troetenrepublik.app.engine.Alarmarten.QUARTAL ->
                quartalTag = profileFor(a.symbol)?.earningsDate?.takeIf { it > 0 }?.let { it / 86_400_000L }
            // Analystenurteile und 8-K-Warnzeichen: hoechstens einmal je Handelstag und Wert (gemeldetAm ist der Tag der
            // letzten Pruefung), die Abrufe haben zusaetzlich ihre eigenen Zwischenspeicher
            de.troetenrepublik.app.engine.Alarmarten.ANALYST -> if (a.gemeldetAm != heute && abrufErlaubt(a, now)) {
                analyst = runCatching { market.analysten(a.symbol) }.getOrNull()?.urteile
            }
            de.troetenrepublik.app.engine.Alarmarten.ACHTK -> if (a.gemeldetAm != heute && abrufErlaubt(a, now)) {
                val kennung = de.troetenrepublik.app.market.SecInsider.kennung(settingsFlow.value.secKontakt)
                if (kennung != null && de.troetenrepublik.app.ui.istUsAktie(a.symbol, quote.nativeCurrency)) {
                    val cik = _sec.value?.geschaefte?.firstOrNull { it.k.equals(a.symbol, true) }?.c?.ifBlank { null }
                    achtK = runCatching { de.troetenrepublik.app.market.AchtKAbruf.laden(a.symbol, kennung, cik, java.time.LocalDate.now(java.time.ZoneOffset.UTC)) }.getOrNull()
                        ?.warnzeichen?.filter { de.troetenrepublik.app.market.AchtK.zaehlt(it) }?.map { it.tag + "|" + it.accession }
                }
            }
        }
        return de.troetenrepublik.app.engine.Alarmarten.Daten(
            kurs = quote.price, volumen = quote.volume, schnitt50 = schnitt, normalVolumen = normal, exTag = exTag, insider = insider,
            heute = heute, land = de.troetenrepublik.app.engine.Alarmarten.land(a.symbol),
            aktuell = quote.marketTime > 0 && Trading.handelstag(quote.marketTime) == heute,
            hoch52 = quote.weekHigh52, tief52 = quote.weekLow52, tagesHoch = quote.dayHigh, tagesTief = quote.dayLow,
            quartalTag = quartalTag, analyst = analyst, achtK = achtK, regelLage = regelLage,
        )
    }

    private fun syncTracked() {
        val acc = account.value
        val symbols = buildSet {
            addAll(acc.positions.map { it.symbol })
            addAll(acc.watchlist)
            addAll(acc.orders.filter { it.status == OrderStatus.OPEN }.map { it.symbol })
            addAll(acc.plans.map { it.symbol })
            addAll(acc.alerts.map { it.symbol })
            addAll(Symbols.defaultWatchlist)
        }
        market.track(symbols)
    }

    // ------------------------------------------------------------ EZB-Zins

    suspend fun refreshEcb(force: Boolean = false) {
        val s = settingsFlow.value
        if (!force && s.ecbBps > 0 && System.currentTimeMillis() - s.ecbFetchedAt < 20 * 3_600_000L) return
        val r = market.ecbRate() ?: return
        val bps = kotlin.math.round(r.ratePct * 100).toInt()
        settings.update { it.copy(ecbBps = bps, ecbFetchedAt = System.currentTimeMillis(), ecbSince = r.since) }
    }

    // ------------------------------------------------------------ Taktgeber

    private val dividendCache = ConcurrentHashMap<String, Pair<Long, List<MarketApi.DividendEvent>>>()

    // Laeuft alle paar Sekunden: Orders, Knock-outs, Sparplaene, Zinsen, Dividenden, Alarme, Verlauf.
    private suspend fun tick() {
        // Verschluesselt und noch nicht entsperrt: nichts buchen, nichts weiterzaehlen
        if (store.gesperrt.value) return
        // Solange broker gefuellt ist (Bereinigung beim Start noch offen), bucht das Uebungsdepot nicht.
        if (settingsFlow.value.broker.isNotBlank()) return
        val q = quotes.value
        if (q.isEmpty()) return
        // Die Art eines Werts (Aktie, Fonds) kommt aus dem Katalog; Orders und Verkaeufe brauchen sie von Beginn an.
        if (de.troetenrepublik.app.market.Catalog.loaded() == null) withContext(Dispatchers.IO) { de.troetenrepublik.app.market.Catalog.get() }
        val now = System.currentTimeMillis()
        val s = settingsFlow.value
        val today = now / 86_400_000L
        val sp = Trading.Spread(s.spreadStockBps.toDouble(), s.spreadDerivativeBps.toDouble())
        val rate = riskFree
        val dividends = collectDividends(now)
        val splits = collectSplits(now)
        // Tagesschluesse fuer Tage ohne laufende App (Faktor-Tageswechsel, Knock-out, Verfall von Optionsscheinen)
        val schluesse = Trading.schluesseNoetig(account.value, now).associateWith { dailyCloses(it) }.filterValues { it.isNotEmpty() }
        // Nach einer Pause die verpasste Zeit mit Kerzen nachspielen. Ohne frischen Kurs (kein Netz) wartet das, und der
        // Takt bucht solange weder Einzahlung noch Orders noch Sparplaene.
        val netz = q.values.any { Trading.isQuoteFresh(it, now) }
        val pause = de.troetenrepublik.app.engine.Nachholen.noetig(account.value, now)
        val nachhol = if (pause && netz) nachholDaten(account.value, now, q) else null
        val wartet = pause && !netz

        val before = account.value
        store.update { acc ->
            val lastDay = acc.lastMaintainDay.takeIf { it > 0 } ?: s.lastMaintainDay
            val lastInterest = acc.lastInterestDay.takeIf { it > 0 } ?: s.lastInterestDay
            var a = acc
            for ((sym, liste) in splits) for (sp in liste) {
                a = de.troetenrepublik.app.engine.Splits.anwenden(a, sym, q[sym]?.name ?: sym, sp, Trading.newId("tx", now))
            }
            a = Trading.artNachbestimmen(a)
            // Vor maintain: Hebelprodukte tragen noch den Stand des letzten Laufs
            if (nachhol != null && a.letzterLauf == nachhol.seit) {
                a = de.troetenrepublik.app.engine.Nachholen.ausfuehren(a, nachhol.seit, now, nachhol.reihen, sp, s.allowance, rate, nachhol.fx)
            }
            a = Trading.maintain(a, q, now, lastDay in 1 until today, allowance = s.allowance, rate = rate, schluesse = schluesse, spread = sp)
            if (!wartet) {
                // Monatliche Einzahlung vor Orders und Sparplaenen, damit das Geld am selben Tag schon bereitsteht
                a = de.troetenrepublik.app.engine.Einzahlplaene.ausfuehren(a, now)
                a = Trading.runOrders(a, q, now, sp, allowance = s.allowance, rate = rate)
                a = Trading.runPlans(a, q, now, sp)
            }
            if (lastInterest in 1 until today) {
                a = Trading.accrueInterest(a, s.interestPct, (today - lastInterest).toInt())
                // Gutschrift beim ersten Lauf in einem neuen Monat
                if (monthKey(lastInterest * 86_400_000L) < monthKey(now)) {
                    a = Trading.creditInterest(a, s.interestPct, now, s.allowance)
                }
            }
            if (dividends.isNotEmpty()) a = Trading.bookDividends(a, dividends, now, s.allowance, wiederanlageGlobal = s.divWiederanlage)
            a = Trading.dividendenWiederanlegen(a, q, now, sp, s.divWiederanlage)
            val (withAlerts, _) = Trading.checkAlerts(a, q, now)
            a = Trading.backfillWatchMeta(withAlerts, q, now)
            // Marker und Guthaben zusammen speichern, damit nach einem Neustart keine Zinsen doppelt gebucht werden.
            a = a.copy(lastMaintainDay = today, lastInterestDay = today)
            // Letzter Lauf mit frischen Kursen, hoechstens einmal je Minute gemerkt
            if (a.letzterLauf == 0L || (netz && now - a.letzterLauf >= 60_000L)) a = a.copy(letzterLauf = now)
            val hasAllQuotes = a.positions.all { p -> p.isKnockedOut || Trading.isQuoteFresh(q[p.symbol], now) }
            if (hasAllQuotes) a = Trading.snapshot(a, Trading.value(a, q, now, rate).total, now)
            a
        }
        merkeVerschwundene(before, account.value)
        announce(before, account.value)
        thesenErinnern(now)
        alarmartenPruefen(now, q)
    }

    private class Nachhol(val seit: Long, val reihen: Map<String, de.troetenrepublik.app.engine.Nachholen.Reihe>, val fx: Map<String, Double>)

    // Kerzen seit dem letzten Lauf fuer die Werte mit offener Order oder laufendem Sparplan, ein Abruf je Wert. Ein Wert
    // mit Split in der Pause oder ohne Auskunft dazu bleibt aussen vor: Limit und Stueckzahl passen sonst nicht zu den
    // Kursen. Aktien und Fonds zum Wechselkurs des jeweiligen Tages, Basiswerte von Hebelprodukten zum heutigen, weil
    // Basispreis und Schwelle mit ihm umgerechnet werden.
    private suspend fun nachholDaten(acc: Account, now: Long, q: Map<String, Quote>): Nachhol {
        val seit = acc.letzterLauf
        val intervall = de.troetenrepublik.app.engine.Nachholen.intervall(seit, now)
        val hebel = acc.orders.filter { it.status == OrderStatus.OPEN && it.derivative != null }.mapTo(HashSet()) { it.symbol }
        val gleichzeitig = kotlinx.coroutines.sync.Semaphore(4)
        val reihen = coroutineScope {
            de.troetenrepublik.app.engine.Nachholen.symbole(acc).map { sym ->
                async {
                    gleichzeitig.withPermit {
                        val splits = splitCache[sym]?.takeIf { it.first >= now - 24 * 3_600_000L }?.second
                            ?: (try { market.splits(sym) } catch (e: CancellationException) { throw e } catch (_: Exception) { null })?.also { splitCache[sym] = now to it }
                            ?: return@withPermit null
                        if (splits.any { it.t > seit - 86_400_000L && it.t <= now }) return@withPermit null
                        val roh = try { market.kerzenZwischen(sym, seit, now, intervall) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
                        if (roh.isEmpty()) return@withPermit null
                        val kerzen = if (sym in hebel) roh
                            else try { market.mitDamaligemKurs(sym, roh) } catch (e: CancellationException) { throw e } catch (_: Exception) { roh }
                        sym to de.troetenrepublik.app.engine.Nachholen.Reihe(kerzen, de.troetenrepublik.app.engine.Nachholen.tageskerzen(kerzen, intervall))
                    }
                }
            }.awaitAll().filterNotNull().toMap()
        }
        return Nachhol(seit, reihen, q.mapValues { it.value.fx })
    }

    // Zusatz der Mitteilung zu einer Buchung, die aus der Zeit ohne laufende App stammt: wann sie ausgefuehrt wurde.
    private fun nachgeholtAm(ts: Long): String =
        if (ts in 1 until System.currentTimeMillis() - de.troetenrepublik.app.engine.Nachholen.MIN_LUECKE_MS) " " + S.nhAm(Fmt.dateTime(ts)) else ""

    // Produkte, die das Depot verlassen haben (Verkauf, Rueckzahlung), bleiben der Detailseite bekannt;
    // sonst faende sie das Produkt nicht mehr.
    private fun merkeVerschwundene(before: Account, after: Account) {
        if (before === after) return
        val bleibt = after.positions.mapTo(HashSet()) { it.key }
        before.positions.forEach { p -> if (p.key !in bleibt) p.derivative?.let { derivativeRegistry[it.id] = it } }
    }

    private val splitCache = ConcurrentHashMap<String, Pair<Long, List<de.troetenrepublik.app.engine.Splits.Split>>>()

    private val etfCache = ConcurrentHashMap<String, Pair<Long, de.troetenrepublik.app.engine.Durchschau.Bestand?>>()

    // Bestand eines ETFs fuer die Durchschau, je Fonds hoechstens einmal am Tag abgefragt. Ohne Antwort (null) nur
    // 30 Minuten gemerkt, ein Fehler gar nicht, ein Abbruch laeuft durch.
    suspend fun etfBestand(symbol: String): de.troetenrepublik.app.engine.Durchschau.Bestand? {
        val jetzt = System.currentTimeMillis()
        etfCache[symbol]?.takeIf { jetzt - it.first < if (it.second == null) 30 * 60_000L else 24 * 3_600_000L }?.let { return it.second }
        val b = try { market.etfBestand(symbol) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) {
            return etfCache[symbol]?.second
        }
        etfCache[symbol] = jetzt to b
        b?.ter?.let { terMerken(symbol, it, jetzt) }
        return b
    }

    // Laufende Kosten je Fonds ueber die Zeit (5.94), damit eine Erhoehung auffaellt; in ter-verlauf.json.
    private val terJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private val terListe = kotlinx.serialization.builtins.MapSerializer(
        kotlinx.serialization.serializer<String>(), de.troetenrepublik.app.engine.DepotCheck.TerStand.serializer(),
    )
    private val terStaende: MutableMap<String, de.troetenrepublik.app.engine.DepotCheck.TerStand> by lazy {
        runCatching {
            terJson.decodeFromString(terListe, de.troetenrepublik.app.plattform.SichereDatei(java.io.File(store.ordner, "ter-verlauf.json")).lesen().toString(Charsets.UTF_8))
        }.getOrDefault(emptyMap()).toMutableMap()
    }

    // Vorige TER eines Fonds, wenn sie im letzten Jahr gestiegen ist.
    fun terVorher(symbol: String): Double? = synchronized(terStaende) {
        de.troetenrepublik.app.engine.DepotCheck.terGestiegen(terStaende[symbol], System.currentTimeMillis())
    }

    private val terSchreibLock = kotlinx.coroutines.sync.Mutex()

    private suspend fun terMerken(symbol: String, ter: Double, jetzt: Long) {
        val (alt, neu) = synchronized(terStaende) {
            val a = terStaende[symbol]
            val n = de.troetenrepublik.app.engine.DepotCheck.terAbgleich(a, ter, jetzt)
            if (n != a) terStaende[symbol] = n
            a to n
        }
        if (neu == alt) return
        // Kopie und Schreiben unter einer Sperre, damit ein aelterer Stand keinen neueren ueberschreibt
        terSchreibLock.withLock {
            val kopie = synchronized(terStaende) { terStaende.toMap() }
            withContext(Dispatchers.IO) {
                runCatching { de.troetenrepublik.app.plattform.SichereDatei(java.io.File(store.ordner, "ter-verlauf.json")).schreiben(terJson.encodeToString(terListe, kopie).toByteArray()) }
            }
        }
        // Mitteilung nur bei einer Erhoehung und nur fuer Fonds im Depot
        val vorher = neu.vorher ?: return
        if (neu.ter <= vorher || !settingsFlow.value.notifications) return
        val position = account.value.positions.firstOrNull { it.symbol == symbol && !it.isDerivative } ?: return
        notifier?.notify(
            Notifier.CHANNEL_ALERTS, S.terTeurerTitel,
            mitKurszeile(S.terTeurerText(position.name, Fmt.pct(vorher, 2), Fmt.pct(neu.ter, 2)), kursZeile(symbol)),
        )
    }

    // Wie viel vom Depot in einzelnen Firmen steckt, direkt und ueber die gehaltenen ETFs.
    suspend fun depotDurchschau(): List<de.troetenrepublik.app.engine.Durchschau.Wirkung> {
        val pos = valuation.value.positions.filter { !it.position.isDerivative && it.value > 0 }
        val summe = pos.sumOf { it.value }
        if (summe <= 0) return emptyList()
        val gewichte = pos.groupBy { it.position.symbol }.mapValues { (_, l) -> l.sumOf { it.value } / summe }
        val etfs = gewichte.keys.filter { Symbols.typeOf(it) == de.troetenrepublik.app.data.AssetType.ETF }
        val bestaende = etfs.mapNotNull { s -> etfBestand(s)?.let { s to it } }.toMap()
        return de.troetenrepublik.app.engine.Durchschau.wirkung(gewichte, bestaende, pos.associate { it.position.symbol to it.position.name })
    }

    // [Globus Depot 0.70] Sitz, Taetigkeitsland und Boerse der Aktien im Depot (Ebene Mein Depot). Ergebnis je Wert 30 Tage in
    // herkunft.json zwischengespeichert; Profile (Yahoo) nur fuer Werte, bei denen der Sitz allein nicht reicht.
    private val herkunftDatei by lazy { de.troetenrepublik.app.plattform.SichereDatei(java.io.File(store.ordner, "herkunft.json")) }
    private val herkunftListe = kotlinx.serialization.builtins.MapSerializer(
        kotlinx.serialization.serializer<String>(), de.troetenrepublik.app.engine.Taetigkeit.Eintrag.serializer(),
    )
    private val herkunftSpeicher: MutableMap<String, de.troetenrepublik.app.engine.Taetigkeit.Eintrag> by lazy {
        runCatching { terJson.decodeFromString(herkunftListe, herkunftDatei.lesen().toString(Charsets.UTF_8)) }.getOrDefault(emptyMap()).toMutableMap()
    }

    // [0.73] Eigene Wahl des Landes je Aktie; land null oder leer stellt die automatische Zuordnung wieder her.
    fun herkunftWaehlen(symbol: String, land: String?) {
        store.update { it.copy(herkunftWahl = if (land.isNullOrBlank()) it.herkunftWahl - symbol else it.herkunftWahl + (symbol to land)) }
    }

    // Profilabrufe, die scheiterten, werden eine Stunde lang nicht wiederholt
    private val herkunftFehlAm = HashMap<String, Long>()

    // positionen: fuer die Werkzeuge mit einem fremden Depot (Personen) dessen Bestand, sonst das eigene Depot.
    suspend fun depotHerkunft(positionen: List<de.troetenrepublik.app.data.ValuedPosition> = valuation.value.positions): Map<String, de.troetenrepublik.app.engine.Taetigkeit.Herkunft> {
        val T = de.troetenrepublik.app.engine.Taetigkeit
        val dateiText = de.troetenrepublik.app.market.Daten.text("globus/taetigkeit.json")
        val datei = withContext(Dispatchers.Default) { T.lesenDatei(dateiText) }
        val zentralen = withContext(Dispatchers.Default) { T.lesenZentralen(dateiText) }
        val wahl = account.value.herkunftWahl
        val symbole = positionen.filter { it.value > 0 && Symbols.typeOf(it.position.symbol) == de.troetenrepublik.app.data.AssetType.STOCK }
            .map { it.position.symbol }.distinct()
        if (symbole.isEmpty()) return emptyMap()
        val katalog = withContext(Dispatchers.IO) { de.troetenrepublik.app.market.Catalog.get() }
        val karte = globusKarte() ?: return emptyMap()
        val namen = de.troetenrepublik.app.engine.Taetigkeit.Namen(karte.laender.filter { it.a2.isNotEmpty() }.associate { it.en to it.a2 })
        val jetzt = System.currentTimeMillis()
        var geaendert = false
        val aus = HashMap<String, de.troetenrepublik.app.engine.Taetigkeit.Herkunft>()
        for (s in symbole) {
            val k = katalog.aktien.firstOrNull { it.s == s }
            val e = synchronized(herkunftSpeicher) { herkunftSpeicher[s] }?.takeIf { jetzt - it.z < 30 * 86_400_000L }
            if (e != null) { aus[s] = T.veredeln(de.troetenrepublik.app.engine.Taetigkeit.Herkunft(e.s, e.t, T.boerse(s)), s, datei, wahl[s], zentralen); continue }
            val katSitz = k?.c?.takeIf { it.isNotBlank() } ?: if (de.troetenrepublik.app.market.KursQuellen.istExtern(s)) T.boerse(s) else null
            // Katalogwert mit festem Sitz und ohne Rohstoffbezug: kein Profil noetig
            val ressourceKat = T.ressource(null, k?.i)
            val brauchtProfil = (katSitz == null || katSitz in T.HOLDING_SITZE || ressourceKat) && s !in datei && s !in zentralen && s !in wahl
            val p = if (brauchtProfil && jetzt - (herkunftFehlAm[s] ?: 0L) > 3_600_000L) profileFor(s) else null
            if (p == null && brauchtProfil) {
                herkunftFehlAm[s] = jetzt
                aus[s] = T.veredeln(de.troetenrepublik.app.engine.Taetigkeit.Herkunft(katSitz, null, T.boerse(s)), s, datei, wahl[s], zentralen)
                continue
            }
            val sitz = katSitz ?: p?.country?.let { namen.code(it) }
            val roh = T.herkunft(sitz, s, p?.description.orEmpty(), T.ressource(p?.sector, k?.i), namen)
            aus[s] = T.veredeln(roh, s, datei, wahl[s], zentralen)
            synchronized(herkunftSpeicher) { herkunftSpeicher[s] = de.troetenrepublik.app.engine.Taetigkeit.Eintrag(roh.sitz, roh.taetig, jetzt) }
            geaendert = true
        }
        if (geaendert) withContext(Dispatchers.IO) {
            runCatching { herkunftDatei.schreiben(terJson.encodeToString(herkunftListe, synchronized(herkunftSpeicher) { herkunftSpeicher.toMap() }).toByteArray()) }
        }
        return aus
    }

    // [Globus Depot 0.70] Laender der ETFs im Depot: Verteilung je ETF-Symbol (nach Index) und Weltindex zum Vergleich
    // positionen: wie bei depotHerkunft der Bestand eines fremden Depots, sonst das eigene.
    suspend fun etfLaender(positionen: List<de.troetenrepublik.app.data.ValuedPosition> = valuation.value.positions): Pair<Map<String, Map<String, Double>>, Map<String, Double>> {
        val E = de.troetenrepublik.app.engine.EtfLaender
        val daten = withContext(Dispatchers.Default) { E.lesen(de.troetenrepublik.app.market.Daten.text("globus/etf-laender.json")) }
        val katalog = withContext(Dispatchers.IO) { de.troetenrepublik.app.market.Catalog.get() }
        val je = HashMap<String, Map<String, Double>>()
        for (p in positionen) {
            val s = p.position.symbol
            if (Symbols.typeOf(s) != de.troetenrepublik.app.data.AssetType.ETF || s in je) continue
            val c = katalog.etfs.firstOrNull { it.s == s }
            val key = E.schluessel(c?.n ?: p.position.name, c?.x, c?.r, c?.i, daten)
            daten.verteilung(key)?.let { je[s] = it }
        }
        if (positionen === valuation.value.positions) etfLaenderEigen = je
        return je to daten.weltAnteile
    }

    // Laender der Fonds des eigenen Depots vom letzten Aufruf, fuer das Widget des Depot-Checks (ohne Abruf).
    @Volatile
    internal var etfLaenderEigen: Map<String, Map<String, Double>> = emptyMap()

    // Aktiensplits aller Werte, an denen etwas haengt (Aktien, Basiswerte von Derivaten, offene Orders, Alarme,
    // Watchlist), hoechstens einmal am Tag je Wert abgefragt, drei Werte je Takt. Nur noch nicht gebuchte Splits.
    private suspend fun collectSplits(now: Long): Map<String, List<de.troetenrepublik.app.engine.Splits.Split>> {
        val acc = account.value
        val symbols = (acc.positions.map { it.derivative?.underlying ?: it.symbol } +
            acc.orders.filter { it.status == OrderStatus.OPEN }.map { it.derivative?.underlying ?: it.symbol } +
            acc.alerts.map { it.symbol } + acc.watchlist).filter { it.isNotBlank() }.distinct()
        if (symbols.isEmpty()) return emptyMap()
        val stale = symbols.filter { (splitCache[it]?.first ?: 0L) < now - 24 * 3_600_000L }.take(3)
        for (sym in stale) {
            val list = runCatching { market.splits(sym) }.getOrNull() ?: continue
            splitCache[sym] = now to list
        }
        return symbols.associateWith { sym ->
            splitCache[sym]?.second.orEmpty().filter { it.t <= now && !de.troetenrepublik.app.engine.Splits.gebucht(acc, sym, it) }
        }.filterValues { it.isNotEmpty() }
    }

    // Dividendentermine der gehaltenen Werte, hoechstens alle zwoelf Stunden je Wert abgefragt.
    private suspend fun collectDividends(now: Long): List<Trading.Dividend> {
        val symbols = account.value.positions.filter { it.derivative == null }.map { it.symbol }.distinct()
        if (symbols.isEmpty()) return emptyList()
        val stale = symbols.filter { s -> dividendCache[s]?.let { dividendeVeraltet(it, now) } ?: true }.take(3)
        for (sym in stale) {
            // Ein Fehler wird nicht gemerkt (naechster Takt versucht es wieder), ein Abbruch laeuft durch
            val list = try { market.dividends(sym) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { continue }
            dividendCache[sym] = now to list
        }
        return symbols.flatMap { sym -> dividendCache[sym]?.second.orEmpty().map { Trading.Dividend(sym, it.exDate, it.amount) } }
    }

    // Kurszeile fuer Mitteilungen zu genau einem Wert: Kurs und Veraenderung heute aus dem Kurs, den die App ohnehin hat
    // (kein Abruf). Ohne frischen Kurs oder Vortagesschluss null; nurVeraenderung, wenn der Kurs schon im Text steht.
    private fun kursZeile(symbol: String, bezeichnung: String? = null, nurVeraenderung: Boolean = false, q: Quote? = quotes.value[symbol]): String? {
        val d = kursDaten(q) ?: return null
        val deu = I18n.lang == Lang.DE
        return if (nurVeraenderung) de.troetenrepublik.app.engine.Kursmeldung.nurVeraenderung(d.second, deu, S.orHeute)
        else de.troetenrepublik.app.engine.Kursmeldung.zeileProzent(Fmt.anzeige(d.first), d.second, Fmt.symbol, deu, S.orHeute, bezeichnung)
    }

    // Kurs (Euro) und Veraenderung von heute (Prozent, wie die Nachrichtenliste sie zeigt); null, wenn der Kurs fehlt,
    // keinen Vortagesschluss hat oder heute noch keine Bewegung da ist. Die Zeile nennt "heute": Die Bewegung der letzten
    // Sitzung stuende dort falsch.
    private fun kursDaten(q: Quote?, jetzt: Long = System.currentTimeMillis()): Pair<Double, Double>? {
        if (q == null || !(q.price > 0.0) || !(q.prevClose > 0.0)) return null
        val heute = q.heutePct(jetzt) ?: return null
        return q.price to heute
    }

    // Bei Derivaten gehoert die Zeile zum Basiswert und nennt ihn
    private fun basiswert(t: de.troetenrepublik.app.data.Transaction): String? = if (t.key.isNotEmpty() && t.key != t.symbol) de.troetenrepublik.app.ui.kurzName(t.symbol, "") else null

    private fun mitKurszeile(text: String, zeile: String?): String = if (zeile == null) text else text + "\n" + zeile

    // Aktien aus dem Katalog fuer die Zuordnung Schlagzeile zu Wert (dieselbe Erkennung wie die Nachrichtenliste)
    private val newsKatalog by lazy { de.troetenrepublik.app.market.Catalog.get().aktien.map { it.s to it.n } }

    // Kurszeile zu einer Schlagzeile: ein genannter Wert mit Name, Kurs und Veraenderung, mehrere (hoechstens drei) als
    // Kuerzel mit Veraenderung. Fehlt der Kurs, wird er einmal je Wert nachgeladen (zwischengespeicherter Abruf der App).
    private suspend fun newsKursZeile(titel: String): String? {
        val firmen = runCatching { de.troetenrepublik.app.engine.NachrichtEinordnung.firmenIn(titel, newsKatalog, 3) }.getOrNull().orEmpty()
        if (firmen.isEmpty()) return null
        val jetzt = System.currentTimeMillis()
        val daten = firmen.mapNotNull { (sym, name) ->
            var q = quotes.value[sym]
            if (q == null || jetzt - q.updatedAt > 600_000L) {
                q = runCatching { kotlinx.coroutines.withTimeoutOrNull(8_000L) { market.refreshOne(sym) } }.getOrNull() ?: q
            }
            kursDaten(q, jetzt)?.let { Triple(sym, name, it) }
        }
        val deu = I18n.lang == Lang.DE
        val k = de.troetenrepublik.app.engine.Kursmeldung
        return when (daten.size) {
            0 -> null
            1 -> daten[0].let { (sym, name, d) -> k.zeileProzent(Fmt.anzeige(d.first), d.second, Fmt.symbol, deu, S.orHeute, de.troetenrepublik.app.ui.kurzName(sym, name)) }
            else -> k.mehrere(daten.map { (sym, _, d) -> sym to d.second }, deu)
        }
    }

    // Neue Treffer eines Finder-Alarms als Mitteilung (5.94); ein Tipp oeffnet den Aktienfinder.
    fun finderAlarmMelden(titel: String, text: String) {
        if (!settingsFlow.value.notifications) return
        notifier?.notify(Notifier.CHANNEL_ALERTS, titel, text, ziel = "aktienfinder")
    }

    // Was sich zwischen zwei Kontostaenden getan hat, als Systemmitteilung.
    private fun announce(before: Account, after: Account) {
        val n = notifier ?: return
        if (before === after) return
        val beforeTx = before.transactions.map { it.id }.toHashSet()
        val fresh = after.transactions.filter { it.id !in beforeTx }
        for (t in fresh) {
            when (t.type) {
                TxType.DIVIDEND -> n.notify(Notifier.CHANNEL_CASH, S.calLegendReceived, mitKurszeile(S.notifDividendText(t.title, Fmt.euro(t.amount)), kursZeile(t.symbol)))
                TxType.INTEREST -> n.notify(Notifier.CHANNEL_CASH, S.notifInterest, S.notifInterestText(Fmt.euro(t.amount)))
                TxType.KNOCKOUT -> n.notify(Notifier.CHANNEL_TRADES, S.notifKnockoutSettled, mitKurszeile(S.notifKnockoutSettledText(t.title, Fmt.euro(t.amount)), kursZeile(t.symbol, basiswert(t))))
                TxType.EXPIRY -> n.notify(Notifier.CHANNEL_TRADES, S.maturity, mitKurszeile(
                    if (t.verkaufteOption) S.shVerfallText(t.title, Fmt.euro(-t.amount), t.amount == 0.0) else S.notifExpiryText(t.title, Fmt.euro(t.amount)),
                    kursZeile(t.symbol, basiswert(t)),
                ))
                TxType.ALERT -> n.notify(Notifier.CHANNEL_ALERTS, if (t.tag in NEUE_ALARM_TAGS) t.title else S.notifAlert(t.title),
                    when (t.tag) {
                        in NEUE_ALARM_TAGS -> mitKurszeile(
                            S.alertNeuText(t.tag, if (t.tag in de.troetenrepublik.app.engine.Alarmarten.TEXT_TAGS) t.subtitle else Fmt.alertExtra(t.tag, t.extra), Fmt.priceEuro(t.price)),
                            kursZeile(t.symbol, nurVeraenderung = true),
                        )
                        "ALERT_MOVE" -> S.notifAlertMoveText(Fmt.signedPct(t.extra), Fmt.priceEuro(t.price))
                        // Der Kurs steht schon im Text, ergaenzt wird nur die Veraenderung heute
                        "ALERT_SMA_UP", "ALERT_SMA_DOWN", "ALERT_VOLUME", "ALERT_EXDIV", "ALERT_INSIDER" ->
                            mitKurszeile(S.alertArtText(t.tag, Fmt.alertExtra(t.tag, t.extra), Fmt.priceEuro(t.price)), kursZeile(t.symbol, nurVeraenderung = true))
                        else -> mitKurszeile(S.notifAlertText(Fmt.priceEuro(t.price), Fmt.priceEuro(t.extra)), kursZeile(t.symbol, nurVeraenderung = true))
                    })
                TxType.PLAN -> if (t.tag == "PLAN_FAILED") n.notify(Notifier.CHANNEL_TRADES, S.notifPlanFailed, mitKurszeile(S.notifPlanFailedText(t.title) + nachgeholtAm(t.ts), kursZeile(t.symbol)))
                TxType.DEPOSIT -> if (t.via == "PLAN") n.notify(Notifier.CHANNEL_CASH, S.ezMitteilung, S.ezMitteilungText(Fmt.euro(t.amount)))
                TxType.BUY -> if (t.via == "PLAN") n.notify(Notifier.CHANNEL_TRADES, S.notifPlanDone, mitKurszeile(S.notifPlanDoneText(t.title, Fmt.euro(-t.amount)) + nachgeholtAm(t.ts), kursZeile(t.symbol, basiswert(t))))
                TxType.BET -> if (t.tag == "BET_WIN" || t.tag == "BET_LOSS" || t.tag == "BET_SETTLE") {
                    n.notify(Notifier.CHANNEL_TRADES, S.pmNotifTitle(t.tag), S.pmNotifText(t.title, Fmt.euro(t.amount)))
                }
                else -> {}
            }
        }
        // Frisch ausgeknockte Produkte
        val koBefore = before.positions.filter { it.isKnockedOut }.map { it.key }.toSet()
        after.positions.filter { it.isKnockedOut && it.key !in koBefore }.forEach { p ->
            n.notify(Notifier.CHANNEL_TRADES, S.knockOut, mitKurszeile(S.notifKnockoutText(p.name), kursZeile(p.symbol, de.troetenrepublik.app.ui.kurzName(p.symbol, ""))))
        }
        val filledBefore = before.orders.filter { it.status == OrderStatus.FILLED }.map { it.id }.toSet()
        after.orders.filter { it.status == OrderStatus.FILLED && it.id !in filledBefore }.forEach { o ->
            n.notify(Notifier.CHANNEL_TRADES, S.notifOrder, S.notifOrderText(if (o.side == Side.BUY) S.buy else S.sell, Fmt.qty(o.qty), o.name, Fmt.priceEuro(o.executedPrice)) + nachgeholtAm(o.executedAt) +
                (kursZeile(o.symbol, if (o.key != o.symbol) de.troetenrepublik.app.ui.kurzName(o.symbol, "") else null, nurVeraenderung = o.key == o.symbol)?.let { "\n" + it } ?: ""))
        }
    }

    private fun monthKey(ts: Long): Int =
        java.time.Instant.ofEpochMilli(ts).atZone(MarketHours.zone).let { it.year * 12 + it.monthValue }

    // ------------------------------------------------------------ Copytrading

    // Bekannte Investoren mit ihren 13F-Meldungen (mitgeliefert).
    val investoren: List<Investoren.Investor> by lazy {
        runCatching { Investoren.lesen(de.troetenrepublik.app.market.Daten.text("copytrading/investoren.json")) }.getOrDefault(emptyList())
    }

    // Gesuchte 13F-Melder [0.77]: Auswahl aus EDGAR, auf dem Geraet gespeichert (hoechstens 30).
    private val melderDatei by lazy { de.troetenrepublik.app.plattform.SichereDatei(java.io.File(store.ordner, "melder13f.json")) }
    private val _melder13f by lazy {
        MutableStateFlow(runCatching { de.troetenrepublik.app.market.Edgar13f.lesen(melderDatei.lesen().toString(Charsets.UTF_8)) }.getOrDefault(emptyList()))
    }
    val melder13f: StateFlow<List<Investoren.Investor>> get() = _melder13f

    // Investor aus der mitgelieferten Liste oder aus den gesuchten Meldern.
    fun investorMit(id: String): Investoren.Investor? = investoren.firstOrNull { it.id == id } ?: _melder13f.value.firstOrNull { it.id == id }

    // CUSIP zu Kuerzel aus dem 13F-Gesamtbild und den mitgelieferten Bestaenden (diese gehen vor); nur sichere
    // Zuordnungen, sonst bleibt die Position ohne Kuerzel.
    private val cusipKuerzel: Map<String, String> by lazy {
        de.troetenrepublik.app.market.Gesamt13f.aktuell?.cusip.orEmpty() +
            investoren.flatMap { it.quartale.flatMap { q -> q.positionen } }.filter { !it.kuerzel.isNullOrBlank() && it.cusip.isNotBlank() }.associate { it.cusip to it.kuerzel!! }
    }

    enum class SucheStatus { OK, OHNE_KONTAKT, FEHLER }
    data class Suche13f(val treffer: List<de.troetenrepublik.app.market.Edgar13f.Melder>, val status: SucheStatus)

    private val secTaktLock = kotlinx.coroutines.sync.Mutex()
    private var secTaktLetzter = 0L

    // Hoechstens fuenf Abrufe je Sekunde bei der SEC.
    private suspend fun secTakt() = secTaktLock.withLock {
        val warte = 220L - (System.currentTimeMillis() - secTaktLetzter)
        if (warte > 0) delay(warte)
        secTaktLetzter = System.currentTimeMillis()
    }

    // Melder von 13F-HR nach Namen (Volltextsuche der SEC, letzte 500 Tage).
    suspend fun melderSuchen(name: String): Suche13f = withContext(Dispatchers.Default) {
        val kennung = de.troetenrepublik.app.market.SecInsider.kennung(settingsFlow.value.secKontakt)
            ?: return@withContext Suche13f(emptyList(), SucheStatus.OHNE_KONTAKT)
        val heute = de.troetenrepublik.app.engine.Uhr.heute()
        secTakt()
        val a = runCatching {
            secNetz.anfrage(de.troetenrepublik.app.market.Edgar13f.suchAdresse(name, heute.minusDays(500).toString(), heute.toString()), mapOf("User-Agent" to kennung), max = 3_000_000)
        }.getOrNull()
        if (a == null || !a.ok) Suche13f(emptyList(), SucheStatus.FEHLER) else Suche13f(de.troetenrepublik.app.market.Edgar13f.treffer(a.text.orEmpty(), wort = name), SucheStatus.OK)
    }

    // Die beiden juengsten 13F-HR eines Melders lesen und speichern; liefert die Kennung des Investors, null bei Fehlern.
    suspend fun melder13fLaden(melder: de.troetenrepublik.app.market.Edgar13f.Melder): String? = withContext(Dispatchers.Default) {
        val kennung = de.troetenrepublik.app.market.SecInsider.kennung(settingsFlow.value.secKontakt) ?: return@withContext null
        val kopf = mapOf("User-Agent" to kennung, "Accept" to "*/*")
        suspend fun text(url: String, max: Int): String? {
            secTakt()
            return runCatching { secNetz.anfrage(url, kopf, max = max).takeIf { it.ok }?.text }.getOrNull()
        }
        val cik = melder.cik
        val subs = text(Investoren.secAdresse(cik), 4_000_000) ?: return@withContext null
        val liste = de.troetenrepublik.app.market.Edgar13f.meldungen(subs).take(2)
        if (liste.isEmpty()) return@withContext null
        val quartale = ArrayList<Investoren.Quartal>()
        for ((i, m) in liste.withIndex()) {
            val q = runCatching {
                val index = text(de.troetenrepublik.app.market.SecInsider.verzeichnisAdresse(cik, m.accession), 1_000_000) ?: return@runCatching null
                val datei = de.troetenrepublik.app.market.Edgar13f.tabellenDatei(index) ?: return@runCatching null
                val xml = text(de.troetenrepublik.app.market.SecInsider.archivAdresse(cik, m.accession, datei), 16_000_000) ?: return@runCatching null
                de.troetenrepublik.app.market.Edgar13f.quartal(m.stichtag, m.eingang, de.troetenrepublik.app.market.Edgar13f.positionen(xml, m.stichtag) { cusipKuerzel[it] })
            }.getOrNull()
            if (q == null || q.positionen.isEmpty()) { if (i == 0) return@withContext null else break }
            quartale += q
        }
        val inv = de.troetenrepublik.app.market.Edgar13f.investor(de.troetenrepublik.app.market.Edgar13f.lesbar(de.troetenrepublik.app.market.Edgar13f.name(subs) ?: melder.name), cik, quartale)
        _melder13f.update { alt -> (alt.filterNot { it.id == inv.id } + inv).takeLast(30) }
        withContext(Dispatchers.IO) { runCatching { melderDatei.schreiben(de.troetenrepublik.app.market.Edgar13f.schreiben(_melder13f.value).toByteArray(Charsets.UTF_8)) } }
        inv.id
    }

    private val senatMitgebracht: de.troetenrepublik.app.market.Politik.SenatDatei? by lazy {
        runCatching { de.troetenrepublik.app.market.Politik.senat(de.troetenrepublik.app.plattform.Plattform.asset("copytrading/senat.json").decodeToString()) }.getOrNull()
    }

    // Senat: mitgebrachter Stand plus neuere Meldungen aus dem offenen Spiegel (seit 5.92, market/SenatSpiegel.kt).
    // senatFluss aendert sich, sobald neue Meldungen da sind, damit die Seiten neu zeichnen.
    private val _senatFluss = MutableStateFlow<de.troetenrepublik.app.market.Politik.SenatDatei?>(null)
    val senatFluss: StateFlow<de.troetenrepublik.app.market.Politik.SenatDatei?> = _senatFluss.asStateFlow()
    val senat: de.troetenrepublik.app.market.Politik.SenatDatei? get() = _senatFluss.value ?: senatMitgebracht

    // Zeit des letzten Abrufs beim Spiegel (0: nie) und Zahl der Meldungen von dort.
    private val _senatSpiegel = MutableStateFlow(0L to 0)
    val senatSpiegel: StateFlow<Pair<Long, Int>> = _senatSpiegel.asStateFlow()
    private val senatSpiegelSperre = kotlinx.coroutines.sync.Mutex()
    private val senatSpiegelDatei by lazy { de.troetenrepublik.app.plattform.SichereDatei(java.io.File(store.ordner, "senat-spiegel.json")) }
    @Volatile private var senatSpiegelGelesen = false

    // Neuere Senatsmeldungen: erst der gespeicherte Stand, dann hoechstens alle zwoelf Stunden der Spiegel.
    suspend fun senatSpiegelLaden(erzwingen: Boolean = false) = senatSpiegelSperre.withLock {
        val sp = de.troetenrepublik.app.market.SenatSpiegel
        val basis = senatMitgebracht
        if (!senatSpiegelGelesen) {
            senatSpiegelGelesen = true
            withContext(Dispatchers.IO) { runCatching { sp.standLesen(senatSpiegelDatei.lesen().toString(Charsets.UTF_8)) }.getOrNull() }?.let { st ->
                _senatFluss.value = sp.zusammen(basis, st.meldungen)
                _senatSpiegel.value = st.am to st.meldungen.size
            }
        }
        val jetzt = System.currentTimeMillis()
        if (!erzwingen && jetzt - _senatSpiegel.value.first < 12 * 3_600_000L) return@withLock
        if (!ok13107Stand) return@withLock
        val neu = runCatching { ersatzNetz.senat(basis?.meldungen.orEmpty()) }.getOrNull() ?: return@withLock
        _senatFluss.value = sp.zusammen(basis, neu)
        _senatSpiegel.value = jetzt to neu.size
        withContext(Dispatchers.IO) { runCatching { senatSpiegelDatei.schreiben(sp.standText(de.troetenrepublik.app.market.SenatSpiegel.Stand(jetzt, neu)).toByteArray(Charsets.UTF_8)) } }
    }

    // Geschaefte des Praesidenten und Bestand des Vizepraesidenten.
    val regierung: de.troetenrepublik.app.market.Politik.RDatei? by lazy {
        runCatching { de.troetenrepublik.app.market.Politik.regierung(de.troetenrepublik.app.plattform.Plattform.asset("copytrading/regierung.json").decodeToString()) }.getOrNull()
    }

    // Meldungen bei der OGE, die im mitgelieferten Stand noch fehlen, je Person (seit 5.9). Geprueft wird beim
    // Oeffnen der Seite hoechstens einmal am Tag und mit Glocke im Hintergrund.
    private val _regierungNeu = MutableStateFlow<Map<String, List<de.troetenrepublik.app.market.Politik.OgeMeldung>>>(emptyMap())
    val regierungNeu: StateFlow<Map<String, List<de.troetenrepublik.app.market.Politik.OgeMeldung>>> = _regierungNeu.asStateFlow()
    private val regierungZeit = java.util.concurrent.ConcurrentHashMap<String, Long>()

    suspend fun regierungPruefen(id: String, erzwingen: Boolean = false): List<de.troetenrepublik.app.market.Politik.OgeMeldung>? {
        val jetzt = System.currentTimeMillis()
        if (!ok13107Stand || (!erzwingen && jetzt - (regierungZeit[id] ?: 0L) < 24 * 3_600_000L)) return _regierungNeu.value[id]
        val p = regierung?.personen?.firstOrNull { it.id == id } ?: return null
        val teile = p.name.trim().split(' ')
        val oge = withContext(Dispatchers.IO) {
            runCatching { (copy ?: return@runCatching null).ogeMeldungen(teile.last(), teile.first()) }.getOrNull()
        } ?: return null
        regierungZeit[id] = jetzt
        val neu = de.troetenrepublik.app.market.Politik.ogeNeu(oge, p)
        _regierungNeu.value = _regierungNeu.value + (id to neu)
        return neu
    }

    // Vorberechneter Verlauf des Praesidenten.
    val praesident: de.troetenrepublik.app.market.Praesident.Datei? by lazy {
        runCatching { de.troetenrepublik.app.market.Praesident.lesen(de.troetenrepublik.app.plattform.Plattform.asset("copytrading/praesident.json").decodeToString()) }.getOrNull()
    }

    val bilder: de.troetenrepublik.app.market.Personenbilder? by lazy {
        runCatching { de.troetenrepublik.app.market.Personenbilder.lesen(de.troetenrepublik.app.plattform.Plattform.asset("copytrading/bilder.json").decodeToString()) }.getOrNull()
    }

    // Kongressmitglieder (5.96, engine/KongressBestand.kt): mitgelieferter Stand copytrading/kongress.json, darueber
    // der gespeicherte. Hoechstens einmal am Tag prueft die App die Liste bei unitedstates/congress-legislators; wer
    // dort fehlt, steht unter den Ehemaligen. Ohne Antwort bleibt der letzte Stand.
    private val kongressMitgebracht: de.troetenrepublik.app.engine.KongressBestand.Bestand? by lazy {
        runCatching { de.troetenrepublik.app.engine.KongressBestand.lesen(de.troetenrepublik.app.plattform.Plattform.asset("copytrading/kongress.json").decodeToString()) }.getOrNull()
    }

    private val _kongressBestand = MutableStateFlow<de.troetenrepublik.app.engine.KongressBestand.Bestand?>(null)
    val kongressBestand: StateFlow<de.troetenrepublik.app.engine.KongressBestand.Bestand?> = _kongressBestand.asStateFlow()
    private val kongressDatei by lazy { de.troetenrepublik.app.plattform.SichereDatei(java.io.File(store.ordner, "kongress-mitglieder.json")) }
    private val kongressSperre = kotlinx.coroutines.sync.Mutex()
    @Volatile private var kongressGelesen = false
    @Volatile private var kongressVersuch = 0L
    @Volatile private var kongressVerzeichnisFuer: Pair<de.troetenrepublik.app.engine.KongressBestand.Bestand, de.troetenrepublik.app.engine.KongressBestand.Verzeichnis>? = null

    fun kongressMitglieder(): de.troetenrepublik.app.engine.KongressBestand.Bestand? = _kongressBestand.value ?: kongressMitgebracht

    // Verzeichnis zum aktuellen Bestand, zwischengespeichert solange dasselbe Objekt gilt.
    fun kongressVerzeichnis(): de.troetenrepublik.app.engine.KongressBestand.Verzeichnis? {
        val b = kongressMitglieder() ?: return null
        kongressVerzeichnisFuer?.let { if (it.first === b) return it.second }
        return de.troetenrepublik.app.engine.KongressBestand.Verzeichnis(b).also { kongressVerzeichnisFuer = b to it }
    }

    // Hoechstens einmal am Tag die Mitgliederliste pruefen; ohne Antwort bleibt der letzte Stand. Laeuft im Bereich
    // der App, damit ein Seitenwechsel die Pruefung nicht abbricht.
    fun kongressPruefen() {
        scope.launch { runCatching { kongressPruefenJetzt() } }
    }

    private suspend fun kongressPruefenJetzt() = kongressSperre.withLock {
        if (!kongressGelesen) {
            val gespeichert = withContext(Dispatchers.IO) { runCatching { de.troetenrepublik.app.engine.KongressBestand.lesen(kongressDatei.lesen().toString(Charsets.UTF_8)) } }.getOrNull()
            withContext(Dispatchers.Default) { de.troetenrepublik.app.engine.KongressBestand.zusammen(gespeichert, kongressMitgebracht) }?.let { kongressSetzen(it) }
            kongressGelesen = true
        }
        val alt = kongressMitglieder() ?: return@withLock
        val jetzt = System.currentTimeMillis()
        if (jetzt - alt.geprueft < 24 * 3_600_000L || jetzt - kongressVersuch < 6 * 3_600_000L) return@withLock
        kongressVersuch = jetzt
        val heute = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString()
        val (neu, ausschuesse) = runCatching { ersatzNetz.kongressMitglieder(heute) }.getOrNull() ?: return@withLock
        if (!de.troetenrepublik.app.engine.KongressBestand.plausibel(neu, alt)) {
            de.troetenrepublik.app.market.Quellen.drift("kongress")
            return@withLock
        }
        val b = de.troetenrepublik.app.engine.KongressBestand.abgleich(alt, neu, heute, ausschuesse).copy(geprueft = jetzt)
        kongressSetzen(b)
        withContext(Dispatchers.IO) { runCatching { kongressDatei.schreiben(de.troetenrepublik.app.engine.KongressBestand.schreiben(b).toByteArray(Charsets.UTF_8)) } }
    }

    // Verzeichnis abseits der Oberflaeche aufbauen und mit dem Bestand zusammen bereitstellen
    private suspend fun kongressSetzen(b: de.troetenrepublik.app.engine.KongressBestand.Bestand) {
        val v = withContext(Dispatchers.Default) { de.troetenrepublik.app.engine.KongressBestand.Verzeichnis(b) }
        kongressVerzeichnisFuer = b to v
        _kongressBestand.value = b
    }

    // Alle Meldungen des Repraesentantenhauses seit 2024 (mitgeliefert).
    val haus: de.troetenrepublik.app.market.HausDaten.Stand? by lazy {
        runCatching { de.troetenrepublik.app.market.HausDaten.lesen(de.troetenrepublik.app.plattform.Plattform.asset("copytrading/haus.json").decodeToString()) }.getOrNull()
    }

    // Copytrading und die Wertpapierseiten nehmen die mitgelieferten Meldungen, statt sie neu zu laden
    init { copy?.mitgeliefert = { id -> haus?.geschaefte?.get(id) } }

    // Alle Meldungen des Repraesentantenhauses der letzten zwoelf Monate, die weder mitgeliefert noch schon gelesen
    // sind, einmal lesen und auf dem Geraet merken. fortschritt meldet gelesen und gesamt.
    data class Lesestand(val gelesen: Int, val gesamt: Int, val fertig: Boolean)

    private val _meldungenStand = MutableStateFlow<Lesestand?>(null)
    val meldungenStand: StateFlow<Lesestand?> = _meldungenStand.asStateFlow()

    // Startet das Lesen im Hintergrund der App; laeuft weiter, wenn die Anleitung geschlossen wird.
    fun alleMeldungenStarten() {
        if (_meldungenStand.value?.fertig == false) return
        _meldungenStand.value = Lesestand(0, 0, false)
        scope.launch {
            runCatching { alleMeldungenLesen { a, b -> _meldungenStand.value = Lesestand(a, b, false) } }
            val s = _meldungenStand.value
            _meldungenStand.value = Lesestand(s?.gelesen ?: 0, s?.gesamt ?: 0, true)
        }
    }

    // Meldungen der letzten zwoelf Monate, die weder mitgeliefert noch schon gelesen sind.
    private suspend fun offeneMeldungen(r: de.troetenrepublik.app.market.CopyRepository): List<de.troetenrepublik.app.market.Kongress.Meldung> {
        val jetzt = System.currentTimeMillis()
        return r.stand.value.meldungen.filter { it.elektronisch && it.datum >= jetzt - 365L * 86_400_000L && r.gemerkt(it) == null }
    }

    // Zahl der noch ungelesenen Meldungen ohne Netz; null, solange das Verzeichnis nie geladen wurde.
    suspend fun meldungenOffen(): Int? {
        val r = copy ?: return null
        if (r.stand.value.meldungen.isEmpty()) return null
        return offeneMeldungen(r).size
    }

    suspend fun alleMeldungenLesen(fortschritt: (Int, Int) -> Unit) {
        val r = copy ?: return
        runCatching { r.verzeichnis() }
        val offen = offeneMeldungen(r)
        var fertig = 0
        fortschritt(0, offen.size)
        offen.chunked(12).forEach { teil ->
            runCatching { r.geschaefte(teil) }
            fertig += teil.size
            fortschritt(fertig, offen.size)
        }
    }

    // Vermoegen laut neuester Jahresmeldung je Abgeordnetem, Schluessel wie Kongress.Meldung.person.
    val vermoegen: Map<String, de.troetenrepublik.app.market.Vermoegen.E> by lazy {
        runCatching { de.troetenrepublik.app.market.Vermoegen.lesen(de.troetenrepublik.app.plattform.Plattform.asset("copytrading/vermoegen.json").decodeToString()) }
            .getOrNull()?.personen?.associateBy { it.person }.orEmpty()
    }

    // Geschaefte aller Abgeordneten und Senatoren mit Kursen, fuer „Kongress kauft“ und die Rangliste. Einmal geladen
    // bleibt das sechs Stunden im Speicher.
    data class KongressDaten(
        val handel: List<de.troetenrepublik.app.engine.KongressAuswertung.Handel>,
        val kurse: Map<String, de.troetenrepublik.app.engine.KongressAuswertung.Kurse>,
        val index: de.troetenrepublik.app.engine.KongressAuswertung.Kurse?,
        val am: Long,
    )

    private val kongressLock = kotlinx.coroutines.sync.Mutex()
    @Volatile private var kongressGemerkt: KongressDaten? = null

    fun kongressFertig(): KongressDaten? = kongressGemerkt?.takeIf { System.currentTimeMillis() - it.am < 6 * 3_600_000L }

    @Volatile private var kongressHandelGemerkt: Pair<Long, List<de.troetenrepublik.app.engine.KongressAuswertung.Handel>>? = null

    // Nur die Geschaefte, ohne Kurse (0.76): fuer Auswertungen, die keine Kurse brauchen. Sechs Stunden im Speicher.
    suspend fun kongressHandel(): List<de.troetenrepublik.app.engine.KongressAuswertung.Handel> = kongressLock.withLock {
        kongressFertig()?.let { return@withLock it.handel }
        kongressHandelGemerkt?.takeIf { System.currentTimeMillis() - it.first < 6 * 3_600_000L }?.let { return@withLock it.second }
        val l = kongressHandelLaden()
        kongressHandelGemerkt = System.currentTimeMillis() to l
        l
    }

    private suspend fun kongressHandelLaden(): List<de.troetenrepublik.app.engine.KongressAuswertung.Handel> {
        val handel = ArrayList<de.troetenrepublik.app.engine.KongressAuswertung.Handel>()
        fun dazu(person: String, name: String, gruppe: Char, gemeldet: Long, g: de.troetenrepublik.app.market.Kongress.Geschaeft) {
            val k = g.kuerzel ?: return
            if (g.typ != "ST" || !(g.kauf || g.verkauf) || g.mitte <= 0) return
            handel += de.troetenrepublik.app.engine.KongressAuswertung.Handel(person, name, gruppe, de.troetenrepublik.app.ui.CopyRouten.symbol(k), g.kauf, g.datum, gemeldet.takeIf { it > 0 } ?: g.gemeldet, g.mitte, g.inhaber)
        }
        val h = haus
        val bekannt = HashSet<String>()
        h?.meldungen?.forEach { m ->
            bekannt += m.docId
            val g = h.geschaefte[m.docId] ?: copy?.papier?.geschaefte(m.docId) ?: return@forEach
            g.forEach { dazu("k:" + m.person, m.name, 'H', m.datum, it) }
        }
        // Neuere Meldungen aus dem Verzeichnis der App, hoechstens 150
        copy?.let { r ->
            runCatching { r.verzeichnis() }
            val neu = r.stand.value.meldungen.filter { it.docId !in bekannt && r.lesbar(it) }.take(150)
            runCatching { r.geschaefte(neu) }.getOrDefault(emptyList()).forEach { (m, g) -> g?.forEach { dazu("k:" + m.person, m.name, 'H', m.datum, it) } }
        }
        senat?.meldungen?.forEach { sm ->
            if (sm.papier) return@forEach
            val t = de.troetenrepublik.app.market.Politik.tag(sm.eingang)
            sm.geschaefte.forEach { dazu("n:" + sm.person, sm.name, 'S', t, de.troetenrepublik.app.market.Politik.geschaeft(it, t)) }
        }
        return handel
    }

    suspend fun kongressDaten(fortschritt: (Float) -> Unit): KongressDaten? = kongressLock.withLock {
        kongressFertig()?.let { return@withLock it }
        val handel = kongressHandelGemerkt?.takeIf { System.currentTimeMillis() - it.first < 6 * 3_600_000L }?.second ?: kongressHandelLaden()
        fortschritt(0.1f)
        // Tagesschlusskurse (wie bei „Unter Einstiegskurs“, gebuendelt und auf dem Geraet zwischengespeichert), je Aktie
        // vom fruehesten Kauf an
        val bedarf = HashMap<String, Long>()
        for (x in handel) if (x.kauf) listOf(x.datum, x.gemeldet).filter { it > 0 }.minOrNull()?.let { bedarf.merge(x.symbol, it) { a, b -> minOf(a, b) } }
        bedarf["^GSPC"] = bedarf.values.minOrNull() ?: System.currentTimeMillis()
        val kurse = de.troetenrepublik.app.ui.CopyKurse.laden(this, bedarf) { fortschritt(0.1f + 0.9f * it) }.kurse
        val d = KongressDaten(handel, kurse.filterKeys { it != "^GSPC" }, kurse["^GSPC"], System.currentTimeMillis())
        kongressGemerkt = d
        d
    }

    // Zuletzt gerechnete Rangliste auf dem Geraet (seit 5.92), damit die Seite sofort etwas zeigt.
    private val ranglisteDatei by lazy { de.troetenrepublik.app.plattform.SichereDatei(java.io.File(store.ordner, "rangliste.json")) }

    fun ranglisteGespeichert(): Pair<Long, List<de.troetenrepublik.app.engine.KongressAuswertung.Rang>>? =
        runCatching { de.troetenrepublik.app.engine.RanglisteSpeicher.lesen(ranglisteDatei.lesen().toString(Charsets.UTF_8)) }.getOrNull()

    fun ranglisteSpeichern(am: Long, raenge: List<de.troetenrepublik.app.engine.KongressAuswertung.Rang>) {
        runCatching { ranglisteDatei.schreiben(de.troetenrepublik.app.engine.RanglisteSpeicher.schreiben(am, raenge).toByteArray(Charsets.UTF_8)) }
    }

    // Portfolioverlaeufe der Investoren aus allen 13F-Meldungen der letzten fuenf Jahre.
    val verlaeufe: de.troetenrepublik.app.market.Portfolioverlauf.Datei by lazy {
        runCatching { de.troetenrepublik.app.market.Portfolioverlauf.lesen(de.troetenrepublik.app.plattform.Plattform.asset("copytrading/verlauf.json").decodeToString()) }
            .getOrDefault(de.troetenrepublik.app.market.Portfolioverlauf.Datei(0L, emptyMap()))
    }

    // Neuere 13F-Meldungen bei der SEC je Investor (id zu Stichtag und Meldetag), sobald die App sie gesehen hat.
    private val _secNeuer = MutableStateFlow<Map<String, Pair<String, String>>>(emptyMap())
    val secNeuer: StateFlow<Map<String, Pair<String, String>>> = _secNeuer.asStateFlow()

    fun gefolgt(s: Settings = settingsFlow.value): Set<String> = s.copyGefolgt.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    private fun gesehen(s: Settings): Map<String, String> = s.copyGesehen.lines().mapNotNull { z ->
        val t = z.split('\t'); if (t.size == 2 && t[0].isNotBlank()) t[0] to t[1] else null
    }.toMap()

    private fun gesehenText(m: Map<String, String>) = m.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}\t${it.value}" }

    // Stand, ab dem neue Meldungen gelten: juengste Meldung der Person bzw. Stichtag des Investors.
    private fun aktuellerStand(key: String): String? = when {
        key.startsWith("k:") -> copy?.stand?.value?.meldungen?.filter { it.person == key.removePrefix("k:") }?.maxByOrNull { it.datum }?.docId
        key.startsWith("i:") -> investorMit(key.removePrefix("i:"))?.let { inv -> _secNeuer.value[inv.id]?.first ?: inv.aktuell?.stichtag }
        key.startsWith("n:") -> senatNeueste(key.removePrefix("n:"))?.id
        else -> null
    }

    // Juengste Senatsmeldung einer Person (mitgebracht oder aus dem Spiegel).
    private fun senatNeueste(person: String) =
        senat?.meldungen?.filter { it.person == person }?.maxByOrNull { de.troetenrepublik.app.market.Politik.tag(it.eingang) }

    // Personen mit Glocke: Mitteilungen bei neuen Meldungen.
    fun glocken(s: Settings = settingsFlow.value): Set<String> = s.copyGlocke.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    // Glocke fuer eine Person an- oder ausschalten; ab jetzt zaehlen nur neuere Meldungen.
    fun glocke(key: String, an: Boolean) {
        scope.launch {
            // Ist das Verzeichnis noch leer (kurz nach dem Start), erst laden: sonst fehlt der Ausgangsstand und die erste Pruefung merkt ihn still
            if (an && key.startsWith("k:") && aktuellerStand(key) == null) runCatching { copy?.verzeichnis() }
            settings.update { s ->
                val g = glocken(s).toMutableSet()
                val ges = gesehen(s).toMutableMap()
                if (an) { g += key; aktuellerStand(key)?.let { ges[key] = it } } else { g -= key; if (key !in gefolgt(s)) ges -= key }
                s.copy(copyGlocke = g.sorted().joinToString("\n"), copyGesehen = gesehenText(ges), copyGlockeUebernommen = true)
            }
        }
    }

    private val copyPruefLock = kotlinx.coroutines.sync.Mutex()
    private val updatePruefLock = kotlinx.coroutines.sync.Mutex()
    private val updateApi by lazy { MarketApi() }

    // Einer Person (k:<Schluessel>) oder einem Investor (i:<id>) folgen oder nicht mehr folgen.
    fun folgen(key: String, an: Boolean) {
        scope.launch {
            settings.update { s ->
                val g = gefolgt(s).toMutableSet()
                val ges = gesehen(s).toMutableMap()
                if (an) { g += key; aktuellerStand(key)?.let { ges[key] = it } } else { g -= key; ges -= key }
                s.copy(copyGefolgt = g.sorted().joinToString("\n"), copyGesehen = gesehenText(ges))
            }
        }
    }

    // ------------------------------------------------------------ Prediction Markets

    // Uebungswette platzieren: betrag Euro auf Seite seite von Markt m zum Briefkurs.
    suspend fun wetteKaufen(e: de.troetenrepublik.app.market.Prognose.Ereignis, m: de.troetenrepublik.app.market.Prognose.Markt, seite: Int, betrag: Double): Boolean {
        val s = m.seiten.getOrNull(seite) ?: return false
        // Nur zu einem frischen Kurs eines offenen Marktes kaufen; sonst ablehnen
        val frisch = runCatching { prognose.markt(m.quelle, m.id) }.getOrNull()
        val (frischerKurs, kaufFehler) = de.troetenrepublik.app.engine.Wetten.kaufKurs(frisch, seite)
        if (kaufFehler != null || frischerKurs == null) {
            _toast.value = S.pmFehler(kaufFehler?.name ?: "")
            return false
        }
        val kurs = frischerKurs
        val jetzt = System.currentTimeMillis()
        val vorlage = de.troetenrepublik.app.data.Wette(
            id = Trading.newId("pm", jetzt), quelle = m.quelle.name, markt = m.id, ereignis = e.id, titel = e.titel,
            frage = if (e.binaer) "" else m.titel, seite = seite, seitenName = s.name, anteile = 0.0, einstand = 0.0,
            kurs = kurs, kursAm = jetzt, eroeffnet = jetzt, ende = m.ende, token = s.token, serie = m.serie, bild = e.bild,
        )
        var erg: de.troetenrepublik.app.engine.Wetten.Ergebnis? = null
        store.update { acc -> de.troetenrepublik.app.engine.Wetten.kaufen(acc, vorlage, betrag, kurs, jetzt, Trading.newId("tx", jetzt)).also { erg = it }.account }
        val r = erg ?: return false
        if (r.ok) {
            r.wette?.let { w -> _wettKurse.update { it + (w.id to (frisch?.seiten?.getOrNull(seite)?.preis ?: s.preis)) } }
            _toast.value = S.pmBought(Fmt.euro(betrag), de.troetenrepublik.app.ui.pmSeite(s.name))
        } else {
            _toast.value = S.pmFehler(r.fehler?.name ?: "")
        }
        return r.ok
    }

    // Frischer Geld- und Mittelkurs der gewetteten Seite fuer den Verkaufsdialog; bei aufgeloestem Markt zweimal die
    // Auszahlung je Anteil. null, wenn der Markt nicht erreichbar ist.
    suspend fun wetteAngebot(id: String): Pair<Double, Double>? {
        val w = store.account.value.wetten.firstOrNull { it.id == id && it.offen } ?: return null
        val quelle = runCatching { de.troetenrepublik.app.market.Prognose.Quelle.valueOf(w.quelle) }.getOrNull() ?: return null
        val m = runCatching { prognose.markt(quelle, w.markt) }.getOrNull() ?: return null
        m.auszahlung?.let { a -> val je = if (w.seite == 0) a else 1.0 - a; return je to je }
        val s = m.seiten.getOrNull(w.seite) ?: return null
        return s.verkauf to s.preis
    }

    // Alle Anteile einer offenen Wette zum aktuellen Geldkurs verkaufen; ist der Markt schon aufgeloest, wird ausgezahlt.
    // Ohne frischen Kurs wird nichts verkauft.
    suspend fun wetteVerkaufen(id: String): Boolean {
        val w = store.account.value.wetten.firstOrNull { it.id == id && it.offen } ?: return false
        val quelle = runCatching { de.troetenrepublik.app.market.Prognose.Quelle.valueOf(w.quelle) }.getOrNull() ?: return false
        val m = runCatching { prognose.markt(quelle, w.markt) }.getOrNull()
        if (m == null || (m.auszahlung == null && m.seiten.getOrNull(w.seite) == null)) {
            _toast.value = S.pmFehler("NETZ")
            return false
        }
        val jetzt = System.currentTimeMillis()
        val auszahlung = m.auszahlung
        var erg: de.troetenrepublik.app.engine.Wetten.Ergebnis? = null
        if (auszahlung != null) {
            val je = if (w.seite == 0) auszahlung else 1.0 - auszahlung
            store.update { acc -> de.troetenrepublik.app.engine.Wetten.aufloesen(acc, id, je, jetzt, Trading.newId("tx", jetzt)).also { erg = it }.account }
        } else {
            val kurs = m.seiten[w.seite].verkauf
            store.update { acc -> de.troetenrepublik.app.engine.Wetten.verkaufen(acc, id, kurs, jetzt, Trading.newId("tx", jetzt)).also { erg = it }.account }
        }
        val r = erg ?: return false
        _toast.value = if (r.ok) S.pmSold(Fmt.euro(r.wette?.erloes ?: 0.0)) else S.pmFehler(r.fehler?.name ?: "")
        return r.ok
    }

    // Kurse offener Wetten aktualisieren und aufgeloeste Maerkte auszahlen.
    suspend fun wettenPruefen() {
        val offen = store.account.value.wetten.filter { it.offen }
        if (offen.isEmpty()) return
        val kurse = HashMap<String, Double>()
        for (w in offen) {
            val quelle = runCatching { de.troetenrepublik.app.market.Prognose.Quelle.valueOf(w.quelle) }.getOrNull() ?: continue
            val m = runCatching { prognose.markt(quelle, w.markt) }.getOrNull() ?: continue
            val auszahlung = m.auszahlung
            if (auszahlung != null) {
                val je = if (w.seite == 0) auszahlung else 1.0 - auszahlung
                val jetzt = System.currentTimeMillis()
                store.update { acc -> de.troetenrepublik.app.engine.Wetten.aufloesen(acc, w.id, je, jetzt, Trading.newId("tx", jetzt)).account }
            } else {
                m.seiten.getOrNull(w.seite)?.preis?.let { kurse[w.id] = it }
            }
        }
        if (kurse.isNotEmpty()) {
            _wettKurse.update { it + kurse }
            val jetzt = System.currentTimeMillis()
            store.update { acc -> de.troetenrepublik.app.engine.Wetten.kurseSetzen(acc, kurse, jetzt) }
        }
    }

    // Neue Meldungen der Personen mit Glocke pruefen und melden, auch im Hintergrund (Android alle sechs Stunden):
    // Repraesentantenhaus ueber das Verzeichnis des Clerk, Investoren ueber die SEC, der Praesident ueber die
    // Liste der Meldungen auf whitehouse.gov, Senatoren ueber den mitgelieferten Stand und den offenen Spiegel.
    suspend fun copyPruefen() = copyPruefLock.withLock {
        // Im Hintergrund ist der Zustand der Einstellungen noch nicht geladen: direkt lesen
        val s = runCatching { settings.flow.first() }.getOrDefault(settingsFlow.value)
        if (!s.notifications) return@withLock
        ok13107Stand = s.ok13107
        I18n.lang = s.sprache
        val folge = glocken(s)
        if (folge.isEmpty()) return@withLock
        val repo = copy ?: return@withLock
        val ges = gesehen(s).toMutableMap()
        if (folge.any { it.startsWith("k:") }) runCatching { repo.verzeichnis() }
        if (folge.any { it.startsWith("n:") }) runCatching { senatSpiegelLaden() }
        for (key in folge) {
            if (key.startsWith("k:")) {
                val person = key.removePrefix("k:")
                // Neueste zuerst; am selben Tag entscheidet die Kennung, damit die Reihenfolge stabil bleibt
                val liste = de.troetenrepublik.app.market.Kongress.neuesteZuerst(repo.stand.value.meldungen.filter { it.person == person })
                val neueste = liste.firstOrNull() ?: continue
                val alt = ges[key]
                if (alt == null) { ges[key] = neueste.docId; continue }
                if (alt == neueste.docId) continue
                // Alles vor der zuletzt gesehenen Meldung ist neu; ist sie nicht mehr im Verzeichnis (nur laufendes und
                // Vorjahr), gilt allein die juengste als neu
                val neu = de.troetenrepublik.app.market.Kongress.neueSeit(liste, alt)
                for (m in neu.reversed()) {
                    val g = repo.geschaefte(m).orEmpty()
                    val text = if (g.isEmpty()) S.copyNotifPaper else S.copyNotifTrades(g.size, g.take(3).joinToString(", ") { (if (it.kauf) S.vdKaeufe1 else if (it.verkauf) S.copySell else S.copyExchange) + " " + (it.kuerzel ?: it.wert) })
                    notifier?.notify(Notifier.CHANNEL_COPY, S.copyNotifTitle(m.name), mitKurszeile(text, g.singleOrNull()?.kuerzel?.let { kursZeile(it) }))
                }
                ges[key] = neueste.docId
            } else if (key.startsWith("i:")) {
                val inv = investorMit(key.removePrefix("i:")) ?: continue
                val kennung = de.troetenrepublik.app.market.SecInsider.kennung(s.secKontakt) ?: continue
                val sec = runCatching { repo.secNeueste(inv.cik, kennung) }.getOrNull() ?: continue
                val bekannt = inv.aktuell?.stichtag ?: ""
                if (sec.first > bekannt) _secNeuer.update { it + (inv.id to sec) }
                val alt = ges[key] ?: bekannt
                if (sec.first > alt) {
                    notifier?.notify(Notifier.CHANNEL_COPY, S.copyNotifTitle(inv.name), S.copyNotif13f(Fmt.date(isoTag(sec.first)), Fmt.date(isoTag(sec.second))))
                    ges[key] = sec.first
                }
            } else if (key.startsWith("n:")) {
                // Senat: neue Meldungen kommen ueber den offenen Spiegel oder mit der Fassung der App
                val person = key.removePrefix("n:")
                val neueste = senatNeueste(person) ?: continue
                val alt = ges[key]
                if (alt == null) { ges[key] = neueste.id; continue }
                if (alt == neueste.id) continue
                val altTag = senat?.meldungen?.firstOrNull { it.id == alt }?.let { de.troetenrepublik.app.market.Politik.tag(it.eingang) } ?: 0L
                val neu = senat?.meldungen.orEmpty().filter { it.person == person && de.troetenrepublik.app.market.Politik.tag(it.eingang) > altTag }
                val g = neu.flatMap { m -> m.geschaefte.map { de.troetenrepublik.app.market.Politik.geschaeft(it, de.troetenrepublik.app.market.Politik.tag(m.eingang)) } }
                if (neu.isNotEmpty()) {
                    val text = if (g.isEmpty()) S.copyNotifPaper else S.copyNotifTrades(g.size, g.take(3).joinToString(", ") { (if (it.kauf) S.vdKaeufe1 else if (it.verkauf) S.copySell else S.copyExchange) + " " + (it.kuerzel ?: it.wert) })
                    notifier?.notify(Notifier.CHANNEL_COPY, S.copyNotifTitle(neueste.name), mitKurszeile(text, g.singleOrNull()?.kuerzel?.let { kursZeile(it) }))
                }
                ges[key] = neueste.id
            } else if (key.startsWith("r:")) {
                // Regierung: neue Transaktionsmeldungen und Jahresberichte erscheinen zuerst bei der OGE (seit 5.9),
                // gemerkt wird die Kennung der neuesten Datei
                val p = regierung?.personen?.firstOrNull { it.id == key.removePrefix("r:") } ?: continue
                val neu = regierungPruefen(p.id, erzwingen = true) ?: continue
                val neueste = neu.firstOrNull()?.let { de.troetenrepublik.app.market.Politik.ogeKennung(it.pdf) } ?: "-"
                val alt = ges[key]
                if (alt != null && alt != neueste && neu.isNotEmpty()) {
                    val seit = neu.takeWhile { de.troetenrepublik.app.market.Politik.ogeKennung(it.pdf) != alt }
                    notifier?.notify(Notifier.CHANNEL_COPY, S.copyNotifTitle(p.name), S.copyNotifOge(seit.size.coerceAtLeast(1)))
                }
                ges[key] = neueste
            }
        }
        // Nur die geaenderten Eintraege zurueckschreiben: wer waehrend der Pruefung eine Glocke setzt oder loescht, verliert nichts
        val ursprung = gesehen(s)
        settings.update { st ->
            val jetzt = gesehen(st).toMutableMap()
            val gueltig = glocken(st) + gefolgt(st)
            for ((k, v) in ges) if (ursprung[k] != v && k in gueltig) jetzt[k] = v
            st.copy(copyGesehen = gesehenText(jetzt))
        }
    }

    // Neue Fassung der App: fragt hoechstens einmal am Tag bei GitHub nach (nach einem Fehler nach einer
    // Stunde wieder), merkt eine neuere Fassung fuer das Profil und meldet jede neue Fassung einmal. Android ruft das
    // auch bei geschlossener App auf; im Browser gibt es nichts zu aktualisieren.
    suspend fun updatePruefen() = updatePruefLock.withLock {
        if (!de.troetenrepublik.app.plattform.Plattform.aktualisierbar) return@withLock
        val s = runCatching { settings.flow.first() }.getOrDefault(settingsFlow.value)
        // Ist der Hinweis aus, fragt die App gar nicht erst nach
        if (!s.updateHinweis) return@withLock
        val jetzt = System.currentTimeMillis()
        if (!de.troetenrepublik.app.market.Aktualisierung.faellig(jetzt, s.updateGeprueft, s.updateOk)) return@withLock
        val abfrage = de.troetenrepublik.app.market.Aktualisierung.abfragen(updateApi)
        if (abfrage == null) {
            settings.update { it.copy(updateGeprueft = jetzt, updateOk = false) }
            return@withLock
        }
        val e = de.troetenrepublik.app.market.Aktualisierung.auswerten(
            abfrage.neu, de.troetenrepublik.app.market.Aktualisierung.FASSUNG, s.updateGemeldet, s.updateHinweis && s.notifications,
        )
        if (e.melden) notifier?.notify(Notifier.CHANNEL_UPDATES, S.updNotifTitle, S.updNotifText(e.fassung), e.link)
        settings.update {
            it.copy(
                updateGeprueft = jetzt, updateOk = true, updateFassung = e.fassung, updateLink = e.link,
                updateGemeldet = if (e.melden) e.fassung else it.updateGemeldet,
            )
        }
    }

    private fun isoTag(s: String): Long = runCatching { java.time.LocalDate.parse(s).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrDefault(0L)

    // Ergebnis von nachbilden: sofort gekauft, als Limit-Order angelegt, ohne Kurs uebersprungen.
    data class Nachbildung(val gekauft: Int, val orders: Int, val ohneKurs: List<String>)

    // Ein Portfolio nachbilden: betrag Euro nach gewichte (Kuerzel zu Anteil, Summe 1) auf die Werte verteilen.
    // Ist der Handelsplatz offen, kauft die App sofort zum aktuellen Kurs; sonst legt sie eine Limit-Kauforder 2 %
    // ueber dem letzten Kurs an, die zur naechsten Eroeffnung ausgefuehrt wird. Jeder Kauf kostet die uebliche Gebuehr.
    suspend fun nachbilden(gewichte: List<Pair<String, Double>>, betrag: Double, notiz: String): Nachbildung {
        val now = System.currentTimeMillis()
        market.track(gewichte.map { it.first })
        var gekauft = 0
        var orders = 0
        val ohne = ArrayList<String>()
        for ((symbol, w) in gewichte) {
            val q = runCatching { market.refreshOne(symbol) }.getOrNull() ?: quotes.value[symbol]
            val name = de.troetenrepublik.app.ui.CopyRouten.katalogName(symbol) ?: q?.name?.takeIf { it.isNotBlank() } ?: symbol
            if (q == null || !(q.price > 0)) { ohne += symbol; continue }
            val price = Trading.tradePrice(symbol, null, quotes.value + (symbol to q), now, spread, true, riskFree)
            if (!(price > 0)) { ohne += symbol; continue }
            val qty = Math.floor(betrag * w / price * 1e4) / 1e4
            if (qty <= 0) { ohne += symbol; continue }
            if (marketOpen(symbol, null) && Trading.isQuoteFresh(q, now)) {
                var ok = false
                store.update { acc ->
                    val r = Trading.buy(acc, symbol, name, symbol, qty, price, note = notiz)
                    ok = r.ok
                    r.account
                }
                if (ok) gekauft++ else ohne += symbol
            } else {
                val order = Order(
                    id = Trading.newId("ord", now + orders), key = symbol, name = name, symbol = symbol,
                    side = Side.BUY, type = OrderType.LIMIT, qty = qty, limitPrice = Math.round(price * 1.02 * 100) / 100.0,
                    createdAt = now, validity = Validity.GTC, note = notiz,
                )
                if (Trading.orderError(order) == null) { store.update { Trading.placeOrder(it, order) }; orders++ } else ohne += symbol
            }
        }
        _toast.value = S.copyDone(gekauft, orders)
        return Nachbildung(gekauft, orders, ohne)
    }

    // Sparplaene nach den Gewichten einer Strategie: monatlich Euro im Monat, aufgeteilt, am tag.
    fun sparplaeneAnlegen(gewichte: List<Pair<String, Double>>, monatlich: Double, tag: Int): Int {
        var n = 0
        for ((symbol, w) in gewichte) {
            val betrag = Math.round(monatlich * w * 100) / 100.0
            if (betrag < 1.0) continue
            if (addPlan(symbol, de.troetenrepublik.app.ui.CopyRouten.katalogName(symbol) ?: symbol, betrag, tag)) n++
        }
        _toast.value = S.copyPlansDone(n)
        return n
    }

    // ------------------------------------------------------------ Nachrichten

    // Quellen fuer die Nachrichtenseite: die eingeschalteten festen und alle eigenen Feeds, dazu die Zugaenge der eigenen Feeds (5.96).
    fun newsQuellen(s: Settings = settingsFlow.value): List<Rss.Quelle> {
        val aus = s.quellenAus
        // Nur englische Quellen (0.78): feste Quellen anderer Sprachen fallen weg, eigene Feeds und Zugaenge bleiben
        val nurEn = s.nurEnglischeQuellen
        val feste = Rss.nurEnglisch(Rss.eingebaut(s.sprache == Lang.DE), nurEn).filter { it.schluessel !in aus && Rss.hierLesbar(it) }
        val eigene = s.eigeneFeeds.map { (name, url) -> Rss.eigene(name, url) }
        val mitSchluessel = Rss.nurEnglisch(Rss.mitSchluessel(s.keyGuardian, s.keyFinnhub, s.keyAlphaVantage), nurEn).filter { it.schluessel !in aus }
        val basis = feste + mitSchluessel + eigene
        return basis + EigenerFeed.quellen(newsFeeds(s), basis)
    }

    // Text eines Guardian-Artikels ueber die Content API; null ohne Schluessel oder Kontingent.
    suspend fun guardianArtikel(link: String) = news.guardianArtikel(link, settingsFlow.value.keyGuardian)

    // Schluessel eines Dienstes setzen: guardian, finnhub, alphavantage, deepl, reddit und reddit_secret.
    fun schluesselSetzen(dienst: String, wert: String) {
        // Neuer Bluesky-Zugang (5.92): alte Sitzung und Meldung verwerfen
        if (dienst == de.troetenrepublik.app.ui.BSKY_HANDLE || dienst == de.troetenrepublik.app.ui.BSKY_PASSWORT) weitere.blueskyVergessen()
        scope.launch {
            settings.update { s ->
                when (dienst) {
                    de.troetenrepublik.app.market.Abrufkontingent.GUARDIAN -> s.copy(keyGuardian = wert.trim())
                    de.troetenrepublik.app.market.Abrufkontingent.FINNHUB -> s.copy(keyFinnhub = wert.trim())
                    de.troetenrepublik.app.market.Abrufkontingent.ALPHAVANTAGE -> s.copy(keyAlphaVantage = wert.trim())
                    de.troetenrepublik.app.market.Abrufkontingent.DEEPL -> s.copy(keyDeepl = wert.trim())
                    de.troetenrepublik.app.market.Abrufkontingent.FEC -> s.copy(keyFec = wert.trim())
                    de.troetenrepublik.app.market.Abrufkontingent.USPTO -> s.copy(keyUspto = wert.trim())
                    de.troetenrepublik.app.market.Abrufkontingent.DOL -> s.copy(keyDol = wert.trim())
                    de.troetenrepublik.app.market.Abrufkontingent.REDDIT -> s.copy(keyRedditId = wert.trim())
                    de.troetenrepublik.app.ui.REDDIT_SECRET -> s.copy(keyRedditSecret = wert.trim())
                    de.troetenrepublik.app.ui.AGSI_SCHLUESSEL -> s.copy(keyAgsi = wert.trim())
                    // Nur die Zeichen eines Tokens, sonst bleibt der alte Wert
                    de.troetenrepublik.app.market.Abrufkontingent.ENTSOE -> s.copy(keyEntsoe = wert.trim().takeIf { it.isEmpty() || de.troetenrepublik.app.engine.StromMarkt.tokenGueltig(it) } ?: s.keyEntsoe)
                    // Nur die Zeichen eines Tokens, sonst bleibt der alte Wert
                    de.troetenrepublik.app.ui.METACULUS_TOKEN -> s.copy(keyMetaculus = wert.trim().takeIf { it.isEmpty() || de.troetenrepublik.app.market.PrognoseWeitere.tokenGueltig(it) } ?: s.keyMetaculus)
                    // 5.92 Weitere Quellen: nur ein gueltiges Handle und nur ein App-Passwort, nie ein anderes Passwort
                    de.troetenrepublik.app.market.Abrufkontingent.COURTLISTENER -> s.copy(keyCourtListener = wert.trim())
                    de.troetenrepublik.app.market.Abrufkontingent.FINRA -> s.copy(keyFinraId = wert.trim())
                    de.troetenrepublik.app.ui.FINRA_SECRET -> s.copy(keyFinraSecret = wert.trim())
                    // [0.77] Erklaer-Assistent: Anbieter, Basisadresse (nur eine brauchbare), Modell und Schluessel
                    de.troetenrepublik.app.ui.ERK_ANBIETER -> s.copy(erklaererAnbieter = if (wert == de.troetenrepublik.app.engine.Erklaerer.OPENAI) wert else de.troetenrepublik.app.engine.Erklaerer.ANTHROPIC)
                    de.troetenrepublik.app.ui.ERK_ADRESSE -> s.copy(erklaererAdresse = wert.trim().takeIf { it.isEmpty() || de.troetenrepublik.app.engine.Erklaerer.basisadresse(it) != null } ?: s.erklaererAdresse)
                    de.troetenrepublik.app.ui.ERK_MODELL -> s.copy(erklaererModell = wert.trim().take(80))
                    de.troetenrepublik.app.ui.ERK_SCHLUESSEL -> s.copy(keyErklaerer = wert.trim())
                    // [0.78] Flex-Bericht: nur Werte in der Form, die der Abruf annimmt
                    de.troetenrepublik.app.ui.FLEX_TOKEN -> if (wert.isBlank() || de.troetenrepublik.app.engine.FlexAbruf.tokenGueltig(wert)) s.copy(flexToken = wert.trim()) else s
                    de.troetenrepublik.app.ui.FLEX_ABFRAGE -> if (wert.isBlank() || de.troetenrepublik.app.engine.FlexAbruf.nummerGueltig(wert)) s.copy(flexAbfrage = wert.trim()) else s
                    de.troetenrepublik.app.market.Abrufkontingent.EODHD -> s.copy(keyEodhd = wert.trim())
                    de.troetenrepublik.app.market.Abrufkontingent.MASSIVE -> s.copy(keyMassive = wert.trim())
                    de.troetenrepublik.app.market.Abrufkontingent.TIINGO -> s.copy(keyTiingo = wert.trim())
                    de.troetenrepublik.app.market.Abrufkontingent.TWELVEDATA -> s.copy(keyTwelveData = wert.trim())
                    de.troetenrepublik.app.ui.BSKY_HANDLE -> s.copy(bskyHandle = wert.trim().let { de.troetenrepublik.app.engine.WeitereQuellen.handleBereinigt(it) ?: if (it.isEmpty()) "" else s.bskyHandle })
                    de.troetenrepublik.app.ui.BSKY_PASSWORT -> s.copy(bskyAppPasswort = wert.trim().takeIf { it.isEmpty() || de.troetenrepublik.app.engine.WeitereQuellen.appPasswortGueltig(it) } ?: s.bskyAppPasswort)
                    // Nur eine gueltige oeffentliche Adresse wird gespeichert, nie ein privater Schluessel
                    de.troetenrepublik.app.ui.SEC_KONTAKT -> s.copy(secKontakt = wert.trim().takeIf { it.isEmpty() || de.troetenrepublik.app.market.SecInsider.kennung(it) != null } ?: s.secKontakt)
                    de.troetenrepublik.app.ui.POLY_ADRESSE -> s.copy(polyAdresse = wert.trim().takeIf { it.isEmpty() || de.troetenrepublik.app.market.Prognose.adresseGueltig(it) }?.lowercase() ?: s.polyAdresse)
                    else -> s
                }
            }
        }
    }

    // [0.77] Erklaer-Assistent: gewaehlter Anbieter mit Schluessel; der Knopf "Erklaeren" steht nur, wenn die Wahl vollstaendig ist.
    fun erklaererEinstellung(s: de.troetenrepublik.app.data.Settings = settingsFlow.value) =
        de.troetenrepublik.app.engine.Erklaerer.Einstellung(s.erklaererAnbieter, s.erklaererAdresse, s.erklaererModell, s.keyErklaerer)

    private val erklaerNetz by lazy { MarketApi() }

    // Eine Runde des Erklaer-Gespraechs. Der Schluessel geht nur an den gewaehlten Anbieter und steht in keiner Meldung.
    suspend fun erklaeren(nachrichten: List<de.troetenrepublik.app.engine.Erklaerer.Nachricht>): de.troetenrepublik.app.engine.Erklaerer.Ergebnis {
        val s = settingsFlow.value
        return de.troetenrepublik.app.engine.Erklaerer.fragen(
            { a ->
                val r = erklaerNetz.anfrage(a.url, a.kopf, json = a.json, methode = "POST", max = 400_000, lesezeitS = 90)
                de.troetenrepublik.app.engine.Erklaerer.Roh(r.code, r.text)
            },
            erklaererEinstellung(s), s.sprache == Lang.DE, nachrichten,
        )
    }

    // Marktinhalte der Prediction Markets ins Deutsche uebersetzen: nur bei deutscher Oberflaeche und mit
    // DeepL-Schluessel. Texte, die das Woerterbuch schon kennt (etwa Yes und No), kosten keine Zeichen.
    suspend fun pmUebersetzen(texte: Collection<String>) {
        val s = settingsFlow.value
        de.troetenrepublik.app.ui.PmTexte.aktiv = s.sprache == Lang.DE && s.keyDeepl.isNotBlank()
        if (!de.troetenrepublik.app.ui.PmTexte.aktiv) return
        val offen = texte.filter { t -> t.isNotBlank() && t !in de.troetenrepublik.app.ui.PmTexte.karte && !de.troetenrepublik.app.ui.pmFesterBegriff(t) }
        if (offen.isEmpty()) return
        val neu = runCatching { uebersetzer.deutsch(offen, s.keyDeepl) }.getOrDefault(emptyMap())
        if (neu.isNotEmpty()) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { de.troetenrepublik.app.ui.PmTexte.karte.putAll(neu) }
    }

    // Beschreibung eines Werts mit DeepL uebersetzen (ziel "DE" oder "EN-US"); null ohne Schluessel oder bei Fehlern.
    fun beschreibungUebersetzt(text: String, ziel: String): String? = uebersetzer.gemerkt(text, ziel)

    suspend fun beschreibungUebersetzen(text: String, ziel: String): String? =
        runCatching { uebersetzer.uebersetzen(text, ziel, settingsFlow.value.keyDeepl) }.getOrNull()

    // Schlagzeilen laden; neu fragt alle Feeds neu ab.
    suspend fun newsLaden(neu: Boolean = false, ohneAbruf: Set<String> = emptySet()) {
        val s = settingsFlow.value
        news.laden(NewsRepository.Auftrag(newsQuellen(s), s.sprache == Lang.DE, ohneAbruf, s.nurEnglischeQuellen), neu)
    }

    private val newsMeldeLock = kotlinx.coroutines.sync.Mutex()

    // Mitteilungen zu neuen Schlagzeilen einstellen: de.troetenrepublik.app.data.MITTEILUNG_WICHTIGE, _ALLE oder _AUS.
    fun newsMitteilungSetzen(stufe: String) {
        scope.launch { settings.update { it.copy(newsMitteilung = stufe) } }
    }

    // Kennungen der schon gemeldeten Schlagzeilen mit dem Zeitpunkt der Meldung, 24 Stunden gemerkt (je Zeile Zeit, Tab,
    // Kennung); null, wenn es noch keine Datei gibt.
    private val newsGemeldetDatei by lazy { de.troetenrepublik.app.plattform.SichereDatei(java.io.File(store.ordner, "news-gemeldet.txt")) }

    private fun newsGemeldetLesen(now: Long): Map<String, Long>? {
        val roh = runCatching { newsGemeldetDatei.lesen() }.getOrNull() ?: return null
        val klar = runCatching { store.entpacken(roh) }.getOrNull() ?: return null
        return klar.toString(Charsets.UTF_8).lines().mapNotNull { z ->
            val t = z.split('\t', limit = 2)
            val zeit = t.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            if (t.size < 2 || t[1].isBlank() || now - zeit > 86_400_000L) null else t[1] to zeit
        }.toMap()
    }

    private fun newsGemeldetSchreiben(m: Map<String, Long>) {
        if (store.gesperrtFuer(runCatching { newsGemeldetDatei.lesen() }.getOrNull())) return
        val text = m.entries.sortedBy { it.value }.takeLast(3000).joinToString("\n") { "${it.value}\t" + it.key.replace('\t', ' ').replace('\n', ' ') }
        runCatching { newsGemeldetDatei.schreiben(store.verpacken(text.toByteArray(Charsets.UTF_8))) }
    }

    // Neue Schlagzeilen melden: alle oder nur die wichtigen, die auch die Nachrichtenseite markiert (gleiche Punkte,
    // Zuschlag fuer Werte aus Depot und Merkliste). Neu ist, was nach der letzten Meldung und dem letzten Oeffnen der
    // Nachrichtenseite erschienen ist, hoechstens zwoelf Stunden alt; beim ersten Mal wird nur der Stand gemerkt. Mehr als
    // drei auf einmal kommen als eine Sammelmitteilung. laden: Feeds vorher abrufen (bei geschlossener App).
    suspend fun newsMelden(laden: Boolean = false) = newsMeldeLock.withLock {
        // Im Hintergrund ist der Zustand der Einstellungen noch nicht geladen: direkt lesen
        val s = runCatching { settings.flow.first() }.getOrDefault(settingsFlow.value)
        if (!s.notifications || s.newsMitteilung == de.troetenrepublik.app.data.MITTEILUNG_AUS) return@withLock
        if (laden) { news.laden(NewsRepository.Auftrag(newsQuellen(s), s.sprache == Lang.DE, ohneAbruf = Rss.NUR_ANSICHT, nurEnglisch = s.nurEnglischeQuellen)); newsGeladenAm = System.currentTimeMillis() }
        val eintraege = news.stand.value.eintraege.filter { it.zeit > 0 && !Rss.nurAnsicht(it) }
        if (eintraege.isEmpty()) return@withLock
        val now = System.currentTimeMillis()
        val juengste = eintraege.maxOf { it.zeit }.coerceAtMost(now)
        // Neu ist, was noch nie gemeldet wurde: Feeds laden zu verschiedenen Zeiten, eine spaet gelieferte Meldung mit
        // aelterer Zeit darf nicht hinter der Zeit der zuletzt gemeldeten verschwinden
        var bekannt = newsGemeldetLesen(now)
        if (bekannt == null) {
            // Erster Lauf: was vorliegt, gilt als gemeldet; bei einer Marke aus frueheren Fassungen nur, was bis zu ihr erschien
            val grenze = if (s.newsGemeldetBis <= 0L) Long.MAX_VALUE else s.newsGemeldetBis
            bekannt = eintraege.filter { it.zeit <= grenze }.associate { it.id to now }
            newsGemeldetSchreiben(bekannt)
            if (s.newsGemeldetBis <= 0L) {
                settings.update { it.copy(newsGemeldetBis = juengste) }
                return@withLock
            }
        }
        val frisch = Rss.zuMelden(eintraege, s.newsGelesenBis, now, schon = bekannt.keys)
        if (frisch.isEmpty()) return@withLock
        newsGemeldetSchreiben(bekannt + frisch.associate { it.id to now })
        settings.update { it.copy(newsGemeldetBis = maxOf(it.newsGemeldetBis, frisch.maxOf { e -> e.zeit }.coerceAtMost(now))) }
        // Auswahl nach der Stufe (5.96): wichtige, alle, eigene Feeds mit oder ohne wichtige, gewaehlte Quellen
        val stufe = s.newsMitteilung
        val wichtige = if (!EigenerFeed.brauchtWichtige(stufe)) emptySet() else {
            val konto = account.value
            val depot = de.troetenrepublik.app.market.Wichtig.depotBegriffe(
                konto.positions.map { it.name } + konto.watchlist.mapNotNull { Symbols.find(it)?.name },
                konto.positions.map { it.symbol } + konto.watchlist,
            )
            Rss.wichtige(eintraege, depot, s.wichtigNurEnglisch)
        }
        // Was gleich danach eine Glocke „wichtige Meldungen“ zu einem Wert meldet, kommt nicht noch einmal ueber den
        // News-Kanal. Nur, wenn die Glocken auch wirklich melden.
        val glockenTitel = if (s.mitteilungAlarme && s.broker.isBlank() && !store.gesperrt.value) {
            newsGlockenErgebnisse(s, eintraege, now).values.filter { it.gemeldet }.map { it.text }
        } else emptyList()
        val melden = de.troetenrepublik.app.engine.Wertglocke.ohneGlockenNews(
            EigenerFeed.zuMelden(frisch, stufe, wichtige, newsFeeds(s), newsQuellen(s), EigenerFeed.meldeQuellen(s.newsMeldeQuellen)), glockenTitel,
        )
        // Dieselbe Geschichte nur einmal (5.94): weder zweimal in dieser Runde noch, wenn sie in den letzten 24 Stunden
        // schon in anderer Formulierung gemeldet wurde
        val vorher = s.newsGemeldeteTitel.lines().mapNotNull { z ->
            val t = z.split('\t', limit = 2)
            val zeit = t.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            if (now - zeit > 86_400_000L) null else zeit to t.getOrElse(1) { "" }
        }
        val neu = ArrayList<Rss.Eintrag>()
        for (e in melden) {
            if (vorher.any { de.troetenrepublik.app.market.Wichtig.gleicheGeschichte(it.second, e.titel) }) continue
            if (neu.any { de.troetenrepublik.app.market.Wichtig.gleicheGeschichte(it.titel, e.titel) }) continue
            neu += e
        }
        if (neu.isEmpty()) return@withLock
        settings.update { st ->
            st.copy(newsGemeldeteTitel = (vorher.map { "${it.first}\t${it.second}" } + neu.map { "$now\t" + it.titel.replace('\t', ' ').replace('\n', ' ') })
                .takeLast(60).joinToString("\n"))
        }
        val n = notifier ?: return@withLock
        // Bei geschlossener App steht die Sprache noch nicht fest
        I18n.lang = s.sprache
        // Ein Tipp oeffnet die Nachricht in der App
        if (neu.size <= 3) neu.forEach { e ->
            n.notify(Notifier.CHANNEL_NEWS, e.quelle.ifBlank { S.news }, mitKurszeile(e.titel, newsKursZeile(e.titel)), null, de.troetenrepublik.app.data.WidgetZiel.nachricht(e.id))
        } else {
            n.notify(Notifier.CHANNEL_NEWS, S.newsNotifMany(neu.size),
                neu.takeLast(3).reversed().joinToString("\n") { "• " + it.titel }, null, "news")
        }
    }

    @Volatile private var newsGeladenAm = 0L

    // Glocke je Wert, Art "wichtige Meldungen": die Schlagzeilen, die die Nachrichtenseite als wichtig einstuft und die den
    // Wert nennen. Nutzt die geladenen Schlagzeilen, ohne Alarm dieser Art ruft sie keine Feeds ab. laden: Feeds vorher
    // abrufen (bei geschlossener App), hoechstens alle zwei Minuten. Jeder Alarm meldet je Geschichte einmal.
    suspend fun newsWertMelden(laden: Boolean = false) = newsMeldeLock.withLock {
        val alarme = account.value.alerts.filter { it.art == de.troetenrepublik.app.engine.Alarmarten.NEWS }
        if (alarme.isEmpty()) return@withLock
        val s = runCatching { settings.flow.first() }.getOrDefault(settingsFlow.value)
        if (!s.notifications || s.broker.isNotBlank() || store.gesperrt.value) return@withLock
        val jetzt = System.currentTimeMillis()
        if (laden && jetzt - newsGeladenAm > 120_000L) {
            news.laden(NewsRepository.Auftrag(newsQuellen(s), s.sprache == Lang.DE, ohneAbruf = Rss.NUR_ANSICHT, nurEnglisch = s.nurEnglischeQuellen))
            newsGeladenAm = System.currentTimeMillis()
        }
        val eintraege = news.stand.value.eintraege.filter { it.zeit > 0 && !Rss.nurAnsicht(it) }
        if (eintraege.isEmpty()) return@withLock
        val ergebnisse = newsGlockenErgebnisse(s, eintraege, jetzt)
        if (ergebnisse.isEmpty()) return@withLock
        I18n.lang = s.sprache
        var vorher: Account? = null
        var nachher: Account? = null
        store.update { acc ->
            vorher = acc
            Trading.artenMelden(acc, ergebnisse, quotes.value, jetzt).first.also { nachher = it }
        }
        val v = vorher
        val n = nachher
        if (v != null && n != null) announce(v, n)
    }

    // Pruefung der Glocken „wichtige Meldungen“ gegen die geladenen Schlagzeilen: je Alarm mit Treffern das Ergebnis.
    // newsMelden nutzt sie, um dieselbe Nachricht nicht zusaetzlich ueber den News-Kanal zu melden.
    private suspend fun newsGlockenErgebnisse(s: Settings, eintraege: List<Rss.Eintrag>, jetzt: Long): Map<String, de.troetenrepublik.app.engine.Alarmarten.Ergebnis> {
        val konto = account.value
        val alarme = konto.alerts.filter { it.art == de.troetenrepublik.app.engine.Alarmarten.NEWS }
        if (alarme.isEmpty()) return emptyMap()
        val depot = de.troetenrepublik.app.market.Wichtig.depotBegriffe(
            konto.positions.map { it.name } + konto.watchlist.mapNotNull { Symbols.find(it)?.name } + alarme.map { it.name },
            konto.positions.map { it.symbol } + konto.watchlist + alarme.map { it.symbol },
        )
        val wichtige = withContext(Dispatchers.Default) { Rss.wichtige(eintraege, depot, s.wichtigNurEnglisch) }
        val ergebnisse = HashMap<String, de.troetenrepublik.app.engine.Alarmarten.Ergebnis>()
        val heute = Trading.handelstag(jetzt)
        for (a in alarme) {
            val begriffe = de.troetenrepublik.app.market.Wichtig.depotBegriffe(listOf(a.name), listOf(a.symbol))
            val treffer = de.troetenrepublik.app.engine.Wertglocke.newsTreffer(eintraege, wichtige, begriffe, jetzt)
            if (treffer.isEmpty()) continue
            val daten = de.troetenrepublik.app.engine.Alarmarten.Daten(
                kurs = 0.0, volumen = 0.0, schnitt50 = null, normalVolumen = null, exTag = null, insider = emptyList(),
                heute = heute, land = "", aktuell = false, news = treffer,
            )
            ergebnisse[a.id] = de.troetenrepublik.app.engine.Alarmarten.pruefen(a, daten)
        }
        return ergebnisse
    }

    // Nachrichtenseite geoeffnet: alles bis jetzt gilt als gelesen.
    fun newsGelesen(bis: Long = System.currentTimeMillis()) {
        scope.launch { settings.update { it.copy(newsGelesenBis = bis) } }
    }

    // Gemerkte Schlagzeilen (neueste Merkung zuerst).
    fun newsGemerkt(s: Settings = settingsFlow.value): List<Rss.Eintrag> = Rss.gemerktGueltig(Rss.gemerktLesen(s.newsGemerkt), System.currentTimeMillis())

    // Schlagzeile merken oder nicht mehr merken.
    fun newsMerken(e: Rss.Eintrag) {
        if (Rss.nurAnsicht(e)) return
        scope.launch { settings.update { s -> s.copy(newsGemerkt = Rss.gemerktText(Rss.umschalten(Rss.gemerktGueltig(Rss.gemerktLesen(s.newsGemerkt), System.currentTimeMillis()), e))) } }
    }

    // Suchverlauf (0.68): aus der Suche geoeffneter Wert, einzelner Eintrag entfernen, alles loeschen.
    fun suchverlaufMerken(symbol: String, name: String) {
        scope.launch { settings.update { s -> s.copy(suchverlauf = de.troetenrepublik.app.engine.Suchverlauf.text(de.troetenrepublik.app.engine.Suchverlauf.merkenOderNichts(s.suchverlaufAus, de.troetenrepublik.app.engine.Suchverlauf.lesen(s.suchverlauf), symbol, name, System.currentTimeMillis()))) } }
    }

    // Verlauf ganz aus oder wieder an; beim Ausschalten wird der vorhandene Verlauf geloescht.
    fun suchverlaufAusSetzen(aus: Boolean) {
        scope.launch { settings.update { s -> s.copy(suchverlaufAus = aus, suchverlauf = de.troetenrepublik.app.engine.Suchverlauf.nachSchalter(aus, s.suchverlauf)) } }
    }

    fun suchverlaufEntfernen(symbol: String) {
        scope.launch { settings.update { s -> s.copy(suchverlauf = de.troetenrepublik.app.engine.Suchverlauf.text(de.troetenrepublik.app.engine.Suchverlauf.entfernen(de.troetenrepublik.app.engine.Suchverlauf.lesen(s.suchverlauf), symbol))) } }
    }

    fun suchverlaufLeeren() {
        scope.launch { settings.update { s -> s.copy(suchverlauf = "") } }
    }

    // Markierung des Handelskalenders ein- oder ausblenden; die Auswahl bleibt gespeichert.
    fun kalenderArtSchalten(art: de.troetenrepublik.app.engine.TradeCalendar.Art, an: Boolean) {
        scope.launch { settings.update { s -> s.copy(kalenderAus = de.troetenrepublik.app.engine.TradeCalendar.Art.schalten(s.kalenderAus, art, an)) } }
    }

    // Eine feste Quelle (oder „depot“) ein- oder ausschalten.
    fun newsQuelleSchalten(schluessel: String, an: Boolean) {
        scope.launch {
            settings.update { s ->
                s.copy(newsAus = Rss.schalten(s.newsAus, schluessel, an))
            }
            runCatching { newsLaden() }
        }
    }

    // Frueher im Quellenblatt eingetragene Adresse entfernen; neue eigene Feeds entstehen nur im Feed-Blatt.
    fun newsFeedEntfernen(url: String) {
        scope.launch {
            settings.update { s -> s.copy(newsEigene = s.eigeneFeeds.filter { it.second != url }.joinToString("\n") { "${it.first}\t${it.second}" }) }
            runCatching { newsLaden() }
        }
    }

    // 5.96 Feed: eigene Feeds als Kategorien der Nachrichtenseite
    fun newsFeeds(s: Settings = settingsFlow.value): List<EigenerFeed.Feed> = EigenerFeed.lesen(s.newsFeedKategorien)

    // Eigenen Feed anlegen oder aendern (gleiche Kennung), danach neu laden.
    fun newsFeedSpeichern(feed: EigenerFeed.Feed) { scope.launch { settings.update { s -> s.copy(newsFeedKategorien = EigenerFeed.text(EigenerFeed.speichern(EigenerFeed.lesen(s.newsFeedKategorien), feed))) }; runCatching { newsLaden() } } }

    // Eigenen Feed loeschen, danach neu laden.
    fun newsFeedLoeschen(kennung: String) { scope.launch { settings.update { s -> s.copy(newsFeedKategorien = EigenerFeed.text(EigenerFeed.entfernen(EigenerFeed.lesen(s.newsFeedKategorien), kennung))) }; runCatching { newsLaden() } } }

    // Zugang eines eigenen Feeds pruefen (Abruf mit der Kennung der App).
    suspend fun newsZugangPruefen(adresse: String): NewsRepository.Pruefung = news.pruefenFeed(adresse)

    // Quelle fuer Mitteilungen bei MITTEILUNG_QUELLEN an- oder abwaehlen (5.96).
    fun newsMeldeQuelleSchalten(schluessel: String, an: Boolean) {
        scope.launch {
            settings.update { s ->
                val alt = EigenerFeed.meldeQuellen(s.newsMeldeQuellen)
                s.copy(newsMeldeQuellen = EigenerFeed.meldeQuellenText(if (an) alt + schluessel else alt - schluessel))
            }
        }
    }

    // Mitteilungen fuer einen eigenen Feed an oder aus (5.96).
    fun newsFeedMelden(kennung: String, an: Boolean) {
        scope.launch {
            settings.update { s -> s.copy(newsFeedKategorien = EigenerFeed.text(EigenerFeed.lesen(s.newsFeedKategorien).map { if (it.kennung == kennung) it.copy(melden = an) else it })) }
        }
    }

    // ------------------------------------------------------------ Handel

    val spread: Trading.Spread
        get() = settingsFlow.value.let {
            Trading.Spread(it.spreadStockBps.toDouble(), it.spreadDerivativeBps.toDouble())
        }

    // Mittelkurs (fuer Anzeige und Depotbewertung).
    fun priceOf(key: String, symbol: String, derivative: Derivative?): Double =
        Trading.priceOf(key, symbol, derivative, quotes.value, System.currentTimeMillis(), riskFree)

    // Geld- und Briefkurs eines Postens.
    fun bidAsk(symbol: String, derivative: Derivative? = null): Pair<Double, Double> {
        val now = System.currentTimeMillis()
        return Trading.tradePrice(symbol, derivative, quotes.value, now, spread, false, riskFree) to
            Trading.tradePrice(symbol, derivative, quotes.value, now, spread, true, riskFree)
    }

    fun marketOpen(symbol: String, derivative: Derivative?): Boolean =
        MarketHours.isOpen(Symbols.typeOf(symbol), derivative != null)

    private fun errorText(code: String?, extra: Double = 0.0): String? =
        code?.let { S.error(it, if (extra != 0.0) Fmt.euro(extra) else "") }

    fun buy(key: String, name: String, symbol: String, qty: Double, derivative: Derivative? = null, note: String = ""): Boolean {
        if (!Trading.isQuoteFresh(quotes.value[symbol], System.currentTimeMillis())) {
            _toast.value = S.refreshFirst; return false
        }
        if (!marketOpen(symbol, derivative)) {
            _toast.value = S.marketClosedOrders; return false
        }
        val price = Trading.tradePrice(symbol, derivative, quotes.value, System.currentTimeMillis(), spread, true, riskFree)
        var msg: String? = null
        var success = false
        store.update { acc ->
            val r = Trading.buy(acc, key, name, symbol, qty, price, derivative, note = note)
            success = r.ok
            msg = errorText(r.error, r.extra) ?: S.buyDone(Fmt.qty(qty), Fmt.priceEuro(price))
            r.account
        }
        _toast.value = msg
        return success
    }

    // Optionsrechner: Kurs des Basiswerts bereitstellen, null ohne Kurs.
    suspend fun kursFuer(symbol: String): Quote? {
        quotes.value[symbol]?.takeIf { Trading.isQuoteFresh(it, System.currentTimeMillis()) }?.let { return it }
        runCatching { market.track(listOf(symbol)); market.refreshOne(symbol) }
        return quotes.value[symbol]
    }

    // Vorbelegte Stueckzahl fuer die Kaufseite eines Scheins aus dem Optionsrechner, einmal abholbar.
    private val kaufMengen = ConcurrentHashMap<String, Int>()
    fun kaufMengeMerken(id: String, stueck: Int) { kaufMengen[id] = stueck }
    fun kaufMengeNehmen(id: String): Int = kaufMengen.remove(id) ?: 0

    // qty: Stueck, verkaufte Optionen negativ.
    class KaufTeil(val key: String, val name: String, val symbol: String, val qty: Double, val derivative: Derivative?)

    // Verkauf durchrechnen, ohne zu buchen: gleicher Kurs, gleiche Gebuehr, gleiche Steuerlogik wie
    // ein echter Verkauf. Liefert die Buchung, die entstehen wuerde, oder null.
    fun previewSell(key: String, qty: Double): de.troetenrepublik.app.data.Transaction? {
        val acc = account.value
        val pos = acc.positions.firstOrNull { it.key == key } ?: return null
        val now = System.currentTimeMillis()
        val price = Trading.tradePrice(pos.symbol, pos.derivative, quotes.value, now, spread, false, riskFree)
        if (!price.isFinite() || price <= 0) return null
        val r = Trading.sell(acc, key, qty, price, now = now, allowance = settingsFlow.value.allowance)
        return if (r.ok) r.account.transactions.lastOrNull { it.type == TxType.SELL } else null
    }

    fun sell(key: String, qty: Double, note: String = ""): Boolean {
        val pos = account.value.positions.firstOrNull { it.key == key } ?: run {
            _toast.value = S.error("NOT_IN_DEPOT", ""); return false
        }
        if (!Trading.isQuoteFresh(quotes.value[pos.symbol], System.currentTimeMillis())) {
            _toast.value = S.refreshFirst; return false
        }
        if (!marketOpen(pos.symbol, pos.derivative)) {
            _toast.value = S.marketClosedOrders; return false
        }
        val price = Trading.tradePrice(pos.symbol, pos.derivative, quotes.value, System.currentTimeMillis(), spread, false, riskFree)
        var msg: String? = null
        var success = false
        // Das Produkt bleibt der Detailseite bekannt, auch wenn die Position mit dem Verkauf verschwindet
        pos.derivative?.let { derivativeRegistry[it.id] = it }
        store.update { acc ->
            val r = Trading.sell(acc, key, qty, price, allowance = settingsFlow.value.allowance, note = note)
            success = r.ok
            msg = errorText(r.error, r.extra) ?: S.sellDone(Fmt.qty(qty), Fmt.priceEuro(price))
            r.account
        }
        _toast.value = msg
        return success
    }

    // Ausgeknocktes Produkt sofort ausbuchen: Restwert gutschreiben, statt zwei Tage zu warten.
    fun settleNow(key: String): Boolean {
        val pos = account.value.positions.firstOrNull { it.key == key } ?: run {
            _toast.value = S.error("NOT_IN_DEPOT", ""); return false
        }
        pos.derivative?.let { derivativeRegistry[it.id] = it }
        var msg: String? = null
        var success = false
        store.update { acc ->
            val r = Trading.settle(acc, key, quotes.value, allowance = settingsFlow.value.allowance)
            success = r.ok
            // Restwert mit allen Stellen (0,003000 €), als Betrag mit zwei Stellen stuende dort 0,00 €
            msg = errorText(r.error, r.extra) ?: S.settledNow(Fmt.priceEuro(Pricing.KO_RESIDUAL * pos.qty))
            r.account
        }
        _toast.value = msg
        return success
    }

    fun placeOrder(
        key: String, name: String, symbol: String, side: Side, type: OrderType,
        qty: Double, limitPrice: Double, derivative: Derivative? = null,
        validity: Validity = Validity.GTC, note: String = "", trailPct: Double = 0.0,
    ): Boolean {
        val now = System.currentTimeMillis()
        var order = Order(
            id = Trading.newId("ord", now), key = key, name = name, symbol = symbol,
            side = side, type = type, qty = qty, limitPrice = limitPrice,
            createdAt = now, derivative = derivative, validity = validity, note = note, trailPct = trailPct,
        )
        if (type == OrderType.TRAILING_STOP) {
            val current = priceOf(key, symbol, derivative)
            if (current <= 0) { _toast.value = S.refreshFirst; return false }
            order = Trading.trailingStop(order, current)
        }
        val kurs = if (type == OrderType.STOP) priceOf(key, symbol, derivative) else 0.0
        Trading.orderError(order, kurs)?.let { _toast.value = S.error(it, ""); return false }
        store.update { Trading.placeOrder(it, order) }
        _toast.value = when (type) {
            OrderType.LIMIT -> S.limitPlaced
            OrderType.TRAILING_STOP -> S.trailingPlaced
            else -> S.stopPlaced
        }
        return true
    }

    // Stopp-Verkauf mit Gewinnziel: zwei verknuepfte Orders, die Ausfuehrung der einen streicht die andere (OrderPaar).
    fun stoppMitZiel(
        key: String, name: String, symbol: String, qty: Double, stopp: Double, ziel: Double, derivative: Derivative? = null,
        validity: Validity = Validity.GTC, note: String = "",
    ): Boolean {
        val now = System.currentTimeMillis()
        val a = Order(
            id = Trading.newId("ord", now), key = key, name = name, symbol = symbol, side = Side.SELL, type = OrderType.STOP,
            qty = qty, limitPrice = stopp, createdAt = now, derivative = derivative, validity = validity, note = note,
        )
        val b = a.copy(id = a.id + "-z", type = OrderType.LIMIT, limitPrice = ziel)
        de.troetenrepublik.app.engine.OrderPaar.fehler(a, b, priceOf(key, symbol, derivative))?.let { _toast.value = S.error(it, ""); return false }
        store.update { de.troetenrepublik.app.engine.OrderPaar.anlegen(it, a, b) }
        _toast.value = S.stopPlaced
        return true
    }

    fun cancelOrder(id: String) {
        store.update { Trading.cancelOrder(it, id) }
        _toast.value = S.orderCancelled
    }

    // Offene Order aendern: Stueckzahl, Limit- bzw. Stoppreis (Euro), Trailing-Abstand und Gueltigkeit.
    fun orderAendern(id: String, qty: Double, limitPrice: Double, trailPct: Double, validity: Validity): Boolean {
        val alt = account.value.orders.firstOrNull { it.id == id && it.status == OrderStatus.OPEN }
            ?: run { _toast.value = S.error(Err.ORDER_CLOSED, ""); return false }
        var msg: String? = null
        var ok = false
        store.update { acc ->
            val kurs = if (alt.type == OrderType.STOP) priceOf(alt.key, alt.symbol, alt.derivative) else 0.0
            val r = Trading.orderAendern(acc, id, qty, limitPrice, trailPct, validity, System.currentTimeMillis(), kurs)
            ok = r.ok
            msg = errorText(r.error, r.extra) ?: S.orderGeaendert
            r.account
        }
        _toast.value = msg
        return ok
    }

    // Welche Watchlist im Blatt offen ist; neue Werte kommen beim einfachen Umschalten dorthin.
    private val _watchAktiv = MutableStateFlow(0)
    val watchAktiv: StateFlow<Int> = _watchAktiv
    fun setWatchAktiv(i: Int) { _watchAktiv.value = i.coerceIn(0, account.value.weitereListen.size) }

    // Symbol, fuer das die Auswahl der Listen offen ist (null = zu).
    private val _watchWahl = MutableStateFlow<String?>(null)
    val watchWahl: StateFlow<String?> = _watchWahl
    fun watchWahlSchliessen() { _watchWahl.value = null }

    fun toggleWatch(symbol: String) {
        val watched = symbol in account.value.watchlist
        val price = quotes.value[symbol]?.price ?: 0.0
        val liste = watchAktiv.value
        store.update { acc ->
            if (watched) Trading.unwatch(acc, symbol) else Trading.watch(acc, symbol, price, System.currentTimeMillis(), liste)
        }
        _toast.value = if (watched) S.watchRemoved else S.watchAdded
    }

    // Stern oder Plus: bei einer Liste wie bisher umschalten, bei mehreren die Auswahl der Listen zeigen.
    fun watchTippen(symbol: String) {
        if (account.value.weitereListen.isEmpty()) toggleWatch(symbol) else _watchWahl.value = symbol
    }

    fun watchWaehlen(symbol: String, wahl: Set<Int>) {
        val price = quotes.value[symbol]?.price ?: 0.0
        store.update { Watchlisten.waehlen(it, symbol, price, System.currentTimeMillis(), wahl) }
        _watchWahl.value = null
        _toast.value = if (wahl.isEmpty()) S.watchRemoved else S.watchAdded
    }

    fun watchlisteNeu(name: String) {
        if (name.isBlank()) return
        store.update { Watchlisten.neu(it, name) }
        _watchAktiv.value = account.value.weitereListen.size
    }

    fun watchlisteUmbenennen(i: Int, name: String) { store.update { Watchlisten.umbenennen(it, i, name) } }

    fun watchlisteLoeschen(i: Int) {
        store.update { Watchlisten.loeschen(it, i) }
        _watchAktiv.value = _watchAktiv.value.coerceIn(0, account.value.weitereListen.size)
    }

    fun isWatched(symbol: String): Boolean = symbol in account.value.watchlist
    val mehrereWatchlisten: Boolean get() = account.value.weitereListen.isNotEmpty()

    // Langes Druecken auf einen Wert: Einzelwert-Widget mit diesem Wert anlegen (nur Android).
    val widgetAnheftbar: Boolean get() = de.troetenrepublik.app.plattform.Widgets.verfuegbar
    fun alsWidget(symbol: String): Boolean = de.troetenrepublik.app.plattform.Widgets.einzelAnheften(symbol).also { if (!it) _toast.value = S.widgetManuell(S.widgetEinzelName) }

    // Langes Druecken auf eine Kategorie: das Widget dazu anlegen (art aus WidgetKarten.ANLEGBAR). Kann der Startbildschirm
    // das nicht, sagt die Meldung den Weg ueber den Startbildschirm; die Uebersicht der Widgets steht unter Profil.
    // einstellung: Voreinstellung des Widgets (WidgetVorgabe), etwa der Reiter, an dem das lange Druecken stattfand.
    fun alsKategorieWidget(art: String, einstellung: Map<String, String> = emptyMap()): Boolean =
        de.troetenrepublik.app.plattform.Widgets.anheften(art, einstellung).also { if (!it) _toast.value = S.widgetManuell(de.troetenrepublik.app.data.WidgetKarten.titel(art)) }

    fun cycleMetric() { scope.launch { settings.update { it.copy(metric = it.metric.next) } } }
    fun setMetric(m: Metric) { scope.launch { settings.update { it.copy(metric = m) } } }
    // Naechste Kennzahl der Watchlist; „seit erstem Hinzufuegen“ nur, wenn ein Wert wieder aufgenommen wurde.
    fun cycleWatchMetric() {
        val acc = account.value
        val mitErstem = acc.watchlist.any { acc.watchMeta[it]?.wiederAufgenommen == true }
        scope.launch { settings.update { it.copy(watchMetric = it.watchMetric.next(mitErstem)) } }
    }
    fun setWatchMetric(m: WatchMetric) { scope.launch { settings.update { it.copy(watchMetric = m) } } }
    // Feste Sprache waehlen; mit auto folgt die App wieder dem System.
    fun setLanguage(l: Lang, auto: Boolean = false) {
        I18n.lang = if (auto) Lang.system() else l
        scope.launch { settings.update { it.copy(language = l, languageAuto = auto) } }
    }
    fun setAppearance(a: Appearance) { scope.launch { settings.update { it.copy(appearance = a) } } }
    fun setLogosFarbig(farbig: Boolean) {
        de.troetenrepublik.app.ui.LogoAnzeige.farbig = farbig
        scope.launch { settings.update { it.copy(logosFarbig = farbig) } }
    }
    fun setLogoRund(rund: Boolean) {
        de.troetenrepublik.app.ui.LogoAnzeige.rund = rund
        scope.launch { settings.update { it.copy(logoRund = rund) } }
    }
    fun setLogoHintergrundEntfernen(entfernen: Boolean) {
        de.troetenrepublik.app.ui.LogoAnzeige.hintergrundEntfernen = entfernen
        scope.launch { settings.update { it.copy(logoHintergrundEntfernen = entfernen) } }
    }
    fun setTrennlinien(an: Boolean) {
        de.troetenrepublik.app.ui.LogoAnzeige.trennlinien = an
        scope.launch { settings.update { it.copy(trennlinien = an) } }
    }

    // ------------------------------------------------------------ Cash

    fun deposit(amount: Double) {
        if (!amount.isFinite() || amount <= 0) { _toast.value = S.invalidAmount; return }
        store.update { Trading.deposit(it, amount) }
        _toast.value = S.depositedToast(Fmt.euro(amount))
    }

    fun withdraw(amount: Double): Boolean {
        var ok = false
        var msg: String? = null
        store.update { acc ->
            val r = Trading.withdraw(acc, amount)
            ok = r.ok
            msg = errorText(r.error, r.extra) ?: S.withdrawnToast(Fmt.euro(amount))
            r.account
        }
        _toast.value = msg
        return ok
    }

    // Verfuegbar fuer Auszahlungen: Guthaben abzueglich reservierter Betraege offener Kauforders.
    fun withdrawable(): Double = account.value.frei.coerceAtLeast(0.0)

    // ------------------------------------------------------------ Sparplaene

    fun addPlan(symbol: String, name: String, amount: Double, day: Int, interval: PlanInterval = PlanInterval.MONTHLY, uhrzeit: Int = -1): Boolean {
        if (!amount.isFinite() || amount < 1.0 || amount > 10_000.0 || day !in 1..31 || symbol.isBlank()) {
            _toast.value = S.invalidPlan; return false
        }
        val now = System.currentTimeMillis()
        store.update {
            it.copy(plans = it.plans + SavingsPlan(Trading.newId("plan", now), symbol, name, amount, day, createdAt = now, interval = interval,
                uhrzeit = uhrzeit.takeIf { u -> u in 0 until 24 * 60 } ?: -1))
        }
        _toast.value = S.planCreated
        return true
    }

    fun updatePlan(id: String, amount: Double? = null, interval: PlanInterval? = null, day: Int? = null, paused: Boolean? = null, uhrzeit: Int? = null) {
        store.update { acc ->
            acc.copy(plans = acc.plans.map { p ->
                if (p.id != id) p else p.copy(
                    amount = amount ?: p.amount,
                    interval = interval ?: p.interval,
                    dayOfMonth = day ?: p.dayOfMonth,
                    paused = paused ?: p.paused,
                    uhrzeit = uhrzeit?.let { u -> if (u in 0 until 24 * 60) u else -1 } ?: p.uhrzeit,
                ).let { neu -> if (p.paused && !neu.paused) Trading.fortsetzen(neu, System.currentTimeMillis()) else neu }
            })
        }
        _toast.value = when {
            paused == true -> S.planPaused
            paused == false -> S.planResumed
            else -> S.planChanged
        }
    }

    fun removePlan(id: String) {
        store.update { it.copy(plans = it.plans.filter { p -> p.id != id }) }
        _toast.value = S.planDeleted
    }

    // ------------------------------------------------------------ Preisalarme

    fun addAlert(symbol: String, name: String, target: Double): Boolean {
        val price = quotes.value[symbol]?.price ?: 0.0
        if (!target.isFinite() || target <= 0 || price <= 0) { _toast.value = S.invalidPrice; return false }
        if (kotlin.math.abs(target - price) < 1e-9) { _toast.value = S.targetEqualsPrice; return false }
        val now = System.currentTimeMillis()
        store.update {
            it.copy(alerts = it.alerts + PriceAlert(Trading.newId("alert", now), symbol, name, target, above = target > price, createdAt = now, referencePrice = price))
        }
        _toast.value = S.alertSet
        return true
    }

    // Alarm auf die Tagesbewegung: meldet, sobald sich der Kurs an einem Tag um mindestens prozent bewegt.
    fun addMoveAlert(symbol: String, name: String, prozent: Double): Boolean {
        if (!prozent.isFinite() || prozent < 0.5 || prozent > 50) { _toast.value = S.alertMoveUngueltig; return false }
        val now = System.currentTimeMillis()
        val price = quotes.value[symbol]?.price ?: 0.0
        store.update {
            it.copy(alerts = it.alerts + PriceAlert(Trading.newId("alert", now), symbol, name, 0.0, above = true, createdAt = now,
                referencePrice = price, tagesProzent = prozent))
        }
        _toast.value = S.alertSet
        return true
    }

    // Alarm weiterer Art (siehe Alarmarten); je Wert und Art nur einer.
    fun addArtAlert(symbol: String, name: String, art: String): Boolean {
        if (art !in de.troetenrepublik.app.engine.Alarmarten.ALLE) return false
        if (account.value.alerts.any { it.symbol == symbol && it.art == art }) { _toast.value = S.alertSet; return true }
        val now = System.currentTimeMillis()
        val price = quotes.value[symbol]?.price ?: 0.0
        store.update {
            it.copy(alerts = it.alerts + PriceAlert(Trading.newId("alert", now), symbol, name, 0.0, above = true, createdAt = now, referencePrice = price, art = art))
        }
        _toast.value = S.alertSet
        return true
    }

    fun removeAlert(id: String) {
        store.update { it.copy(alerts = it.alerts.filter { a -> a.id != id }) }
    }

    // Glocke je Wert: Arten (Alarmarten.ALLE, Wertglocke.BEWEGUNG) ein- oder ausschalten, in einer Buchung und ohne Hinweis.
    fun wertArtenSetzen(symbol: String, name: String, arten: Collection<String>, an: Boolean) {
        if (symbol.isBlank() || arten.isEmpty()) return
        val now = System.currentTimeMillis()
        val kurs = quotes.value[symbol]?.price ?: 0.0
        store.update { acc ->
            var n = 0
            acc.copy(alerts = de.troetenrepublik.app.engine.Wertglocke.umlegen(acc.alerts, symbol, arten, an) { art ->
                PriceAlert(
                    Trading.newId("alert", now) + "-" + n++, symbol, name, 0.0, above = true, createdAt = now, referencePrice = kurs,
                    art = art.takeIf { it != de.troetenrepublik.app.engine.Wertglocke.BEWEGUNG }.orEmpty(),
                    tagesProzent = if (art == de.troetenrepublik.app.engine.Wertglocke.BEWEGUNG) de.troetenrepublik.app.engine.Wertglocke.BEWEGUNG_STANDARD else 0.0,
                )
            })
        }
    }

    // Schwelle der Tagesbewegung eines Werts setzen (schaltet die Art ein, wenn sie fehlt).
    fun bewegungSetzen(symbol: String, name: String, prozent: Double) {
        if (symbol.isBlank() || !prozent.isFinite() || prozent < 0.5 || prozent > 50) return
        val now = System.currentTimeMillis()
        val kurs = quotes.value[symbol]?.price ?: 0.0
        store.update { acc ->
            acc.copy(alerts = de.troetenrepublik.app.engine.Wertglocke.bewegungSetzen(acc.alerts, symbol, prozent) {
                PriceAlert(Trading.newId("alert", now), symbol, name, 0.0, above = true, createdAt = now, referencePrice = kurs)
            })
        }
    }

    // Alle Alarme eines Werts entfernen (Glocke aus).
    fun glockeAus(symbol: String) {
        store.update { it.copy(alerts = it.alerts.filter { a -> a.symbol != symbol }) }
    }

    // ------------------------------------------------------------ BaFin: Eigengeschaefte und Stimmrechte (seit 5.5)

    private val bafinDatei by lazy { de.troetenrepublik.app.plattform.SichereDatei(java.io.File(store.ordner, "bafin.json")) }
    private val bafinLock = kotlinx.coroutines.sync.Mutex()
    private val bafinNetz by lazy { MarketApi() }
    private val _bafin = MutableStateFlow<de.troetenrepublik.app.market.Bafin.Datei?>(null)

    // Mitgelieferter Stand, vereint mit den selbst geholten Meldungen; null, solange nicht geladen.
    val bafin: StateFlow<de.troetenrepublik.app.market.Bafin.Datei?> = _bafin.asStateFlow()

    // Eigengeschaefte zaehlen zwoelf Monate, wie in der Datenbank der BaFin. Etwas laenger aufbewahrt, weil die Karte
    // seit 5.93 ein Jahr zeigt und der Stand in UTC gerechnet wird.
    private fun bafinAbTag(): String = de.troetenrepublik.app.engine.Uhr.heute().minusDays(400).toString()

    private fun bafinStand(): de.troetenrepublik.app.market.Bafin.Datei {
        _bafin.value?.let { return it }
        val beigelegt = runCatching { de.troetenrepublik.app.market.Bafin.lesen(de.troetenrepublik.app.plattform.Plattform.asset("copytrading/bafin.json").decodeToString()) }.getOrNull()
            ?: de.troetenrepublik.app.market.Bafin.Datei()
        val eigen = runCatching { de.troetenrepublik.app.market.Bafin.lesen(bafinDatei.lesen().toString(Charsets.UTF_8)) }.getOrNull()
        val d = beigelegt.copy(
            eigengeschaefte = de.troetenrepublik.app.market.Bafin.vereinen(beigelegt.eigengeschaefte, eigen?.eigengeschaefte.orEmpty(), bafinAbTag()),
            stand = maxOf(beigelegt.stand, eigen?.stand.orEmpty()),
            abgerufen = eigen?.abgerufen ?: 0L,
            // Luecke aus dem Abruf auf dem Geraet; ein neuerer mitgelieferter Stand schliesst sie
            lueckeVon = eigen?.lueckeVon.orEmpty().takeIf { eigen?.lueckeBis.orEmpty() > beigelegt.stand }.orEmpty(),
            lueckeBis = eigen?.lueckeBis.orEmpty().takeIf { it > beigelegt.stand }.orEmpty(),
        )
        _bafin.value = d
        return d
    }

    // Stand laden und hoechstens alle zwoelf Stunden (neu: sofort) die Meldungen des letzten Monats bei der BaFin
    // nachholen; mit netz = false nur den Stand laden. Ohne Netz bleibt der bisherige Stand.
    // Rechnen (Vereinen, Filtern, Lesen der Antworten) ausserhalb des Hauptthreads, damit die Seite beim Laden nicht ruckelt (0.68)
    suspend fun bafinLaden(neu: Boolean = false, netz: Boolean = true): de.troetenrepublik.app.market.Bafin.Datei = withContext(Dispatchers.Default) { bafinLock.withLock {
        val d = withContext(Dispatchers.IO) { bafinStand() }
        val jetzt = System.currentTimeMillis()
        if (!netz || (!neu && jetzt - d.abgerufen < 12 * 3_600_000L)) return@withLock d
        val frischRoh = runCatching { bafinAbrufen(d) }.getOrNull()
        de.troetenrepublik.app.market.Quellen.inhaltGelesen("bafin", frischRoh != null)
        val frisch = frischRoh ?: return@withLock d
        val liste = de.troetenrepublik.app.market.Bafin.vereinen(d.eigengeschaefte, frisch, bafinAbTag())
        val heute = de.troetenrepublik.app.engine.Uhr.heute().toString()
        // Die BaFin gibt die Meldungen des letzten Monats her; was davor liegt, bleibt als Luecke stehen
        val (neuStand, luecke) = de.troetenrepublik.app.engine.Luecke.nachAbruf(d.stand, d.luecke(), de.troetenrepublik.app.engine.Uhr.heute().minusDays(31).toString(), heute)
        val n = d.copy(eigengeschaefte = liste, stand = maxOf(d.stand, neuStand), abgerufen = jetzt, lueckeVon = luecke?.von.orEmpty(), lueckeBis = luecke?.bis.orEmpty())
        _bafin.value = n
        withContext(Dispatchers.IO) {
            runCatching { bafinDatei.schreiben(de.troetenrepublik.app.market.Bafin.schreiben(de.troetenrepublik.app.market.Bafin.Datei(stand = n.stand, eigengeschaefte = liste, abgerufen = jetzt, lueckeVon = n.lueckeVon, lueckeBis = n.lueckeBis)).toByteArray(Charsets.UTF_8)) }
        }
        n
    }
    }

    // Meldungen des letzten Monats: Suchseiten, fuer jede noch unbekannte Kennung die Seite der Meldung, Preis und
    // Volumen nur fuer neue Meldungen. Nacheinander mit kurzer Pause, die Datenbank hat keine Schnittstelle fuer
    // Massenabrufe.
    private suspend fun bafinAbrufen(d: de.troetenrepublik.app.market.Bafin.Datei): List<de.troetenrepublik.app.market.Bafin.Geschaeft>? {
        val kopf = mapOf("Accept" to "text/html")
        suspend fun seite(url: String): String? { delay(250); return bafinNetz.anfrage(url, kopf).takeIf { it.ok }?.text }
        val erste = seite(de.troetenrepublik.app.market.Bafin.suchAdresse(1)) ?: return null
        val personen = LinkedHashSet(de.troetenrepublik.app.market.Bafin.personen(erste))
        for (s in 2..de.troetenrepublik.app.market.Bafin.letzteSeite(erste)) seite(de.troetenrepublik.app.market.Bafin.suchAdresse(s))?.let { personen += de.troetenrepublik.app.market.Bafin.personen(it) }
        val bekannt = d.eigengeschaefte.associateBy { it.id }
        val bekannteKennungen = d.eigengeschaefte.mapTo(HashSet()) { it.pid }
        val aus = ArrayList<de.troetenrepublik.app.market.Bafin.Geschaeft>()
        for (p in personen) {
            if (p in bekannteKennungen) continue
            val html = seite(de.troetenrepublik.app.market.Bafin.personAdresse(p)) ?: continue
            for ((emittent, g) in de.troetenrepublik.app.market.Bafin.meldungen(html, p)) {
                val alt = bekannt[g.id]
                aus += if (alt?.vol != null) g.copy(k = alt.k, preis = alt.preis, vol = alt.vol, w = alt.w)
                else seite(de.troetenrepublik.app.market.Bafin.detailAdresse(emittent, g.id))?.let { de.troetenrepublik.app.market.Bafin.einzelheiten(it, g) } ?: g
            }
        }
        return aus
    }

    private val isinSymbole = ConcurrentHashMap<String, String>()

    // Boersenkuerzel zu einer ISIN: bekannt, aus Katalog und Firmendaten, sonst Yahoo-Suche (deutsche Notierung zuerst).
    // Kennt Yahoo die ISIN nicht (etwa manche ADR), bildet die Kennungssuche sie ueber OpenFIGI ab.
    suspend fun symbolFuerIsin(isin: String, bekannt: String? = null): String? {
        bekannt?.takeIf { it.isNotBlank() }?.let { return it }
        if (isin.isBlank()) return null
        isinSymbole[isin]?.let { return it }
        val s = Symbols.all.firstOrNull { it.isin == isin }?.symbol
            ?: companies.value.values.firstOrNull { it.isin == isin }?.symbol
            ?: runCatching { market.yahooSuche(isin) }.getOrDefault(emptyList()).let { h ->
                h.firstOrNull { it.symbol.endsWith(".DE") } ?: h.firstOrNull { it.symbol.endsWith(".F") } ?: h.firstOrNull()
            }?.symbol
            ?: runCatching { market.kennungSuche(isin, false, settingsFlow.value.land) }.onFailure { if (it is CancellationException) throw it }
                .getOrDefault(emptyList()).firstOrNull()?.symbol
        s?.let { isinSymbole[isin] = it }
        return s
    }

    // ------------------------------------------------------------ FDA: Zulassungen und Rueckrufe (seit 5.9)

    private val _fda = MutableStateFlow<Map<String, de.troetenrepublik.app.market.Fda.Ergebnis>>(emptyMap())

    // Je Symbol die zuletzt geholten Zulassungen und Rueckrufe der letzten zwoelf Monate.
    val fda: StateFlow<Map<String, de.troetenrepublik.app.market.Fda.Ergebnis>> = _fda.asStateFlow()

    // Zulassungen und Rueckrufe einer Firma, hoechstens einmal am Tag je Symbol (bis zu zehn kleine Abrufe).
    suspend fun fdaLaden(symbol: String, firma: String): de.troetenrepublik.app.market.Fda.Ergebnis? {
        val jetzt = System.currentTimeMillis()
        _fda.value[symbol]?.takeIf { jetzt - it.abgerufen < 24 * 3_600_000L }?.let { return it }
        val namen = de.troetenrepublik.app.market.Fda.namen(firma).filter { it.isNotBlank() }
        if (namen.isEmpty()) return null
        val heute = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
        val von = heute.minusDays(365).toString()
        val bis = heute.toString()
        val netz = MarketApi()
        suspend fun text(url: String) = runCatching { netz.anfrage(url).let { if (it.code == 404) "{}" else it.takeIf { a -> a.ok }?.text } }.getOrNull()
        val e = coroutineScope {
            val zul = namen.map { it.substringBefore(' ') }.distinct().take(4).map { wort ->
                async { text(de.troetenrepublik.app.market.Fda.zulassungAdresse(wort, von, bis))?.let { de.troetenrepublik.app.market.Fda.zulassungenLesen(it, namen, von, bis) } }
            }
            val rr = namen.take(3).flatMap { n -> listOf(false, true).map { g -> async { text(de.troetenrepublik.app.market.Fda.rueckrufAdresse(g, n, von, bis))?.let { de.troetenrepublik.app.market.Fda.rueckrufeLesen(it, namen, g) } } } }
            val z = zul.awaitAll()
            val r = rr.awaitAll()
            // Ohne eine einzige Antwort nichts merken, damit es spaeter noch einmal versucht wird
            if (z.all { it == null } && r.all { it == null }) null
            else de.troetenrepublik.app.market.Fda.Ergebnis(
                z.filterNotNull().flatten().distinctBy { it.marke.ifBlank { it.nummer } + it.tag + it.art }.sortedByDescending { it.tag },
                r.filterNotNull().flatten().distinctBy { it.nummer }.sortedByDescending { it.tag }, jetzt,
            )
        } ?: return _fda.value[symbol]
        _fda.value = _fda.value + (symbol to e)
        return e
    }

    // ------------------------------------------------------------ Leerverkaufspositionen je Land (FR seit 5.8, SE und GB seit 5.9)

    private fun lpDatei(land: String) = de.troetenrepublik.app.plattform.SichereDatei(java.io.File(store.ordner, "leerpositionen_${land.lowercase()}.json"))
    private val lpLock = kotlinx.coroutines.sync.Mutex()
    private val lpNetz by lazy { MarketApi() }
    private val _leerpositionen = MutableStateFlow<Map<String, de.troetenrepublik.app.market.Leerpositionen.Datei>>(emptyMap())

    // Je Land der mitgelieferte oder zuletzt geholte Stand, der neuere gewinnt; leer, solange nichts geladen ist.
    val leerpositionen: StateFlow<Map<String, de.troetenrepublik.app.market.Leerpositionen.Datei>> = _leerpositionen.asStateFlow()

    private fun lpStand(land: String): de.troetenrepublik.app.market.Leerpositionen.Datei {
        _leerpositionen.value[land]?.let { return it }
        val beigelegt = runCatching { de.troetenrepublik.app.market.Leerpositionen.lesen(de.troetenrepublik.app.plattform.Plattform.asset("copytrading/leerpositionen_${land.lowercase()}.json").decodeToString()) }.getOrNull()
            ?: de.troetenrepublik.app.market.Leerpositionen.Datei(land = land)
        val eigen = runCatching { de.troetenrepublik.app.market.Leerpositionen.lesen(lpDatei(land).lesen().toString(Charsets.UTF_8)) }.getOrNull()
        val d = (if (eigen != null && eigen.stand >= beigelegt.stand) eigen else beigelegt.copy(abgerufen = eigen?.abgerufen ?: 0L)).copy(land = land)
        _leerpositionen.value = _leerpositionen.value + (land to d)
        return d
    }

    // Stand eines Landes laden und hoechstens einmal am Tag neu holen: Frankreich die ganze Liste der AMF (einige MB),
    // Schweden die offenen Positionen der FI, Grossbritannien die aktuelle und die fruehere Summenliste der FCA.
    suspend fun lpLaden(land: String, netz: Boolean = true): de.troetenrepublik.app.market.Leerpositionen.Datei = lpLock.withLock {
        val d = withContext(Dispatchers.IO) { lpStand(land) }
        val jetzt = System.currentTimeMillis()
        if (!netz || jetzt - d.abgerufen < 24 * 3_600_000L) return@withLock d
        val heute = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
        val titel: Map<String, de.troetenrepublik.app.market.Leerpositionen.Titel> = when (land) {
            "FR" -> runCatching { lpNetz.datei(de.troetenrepublik.app.market.Leerpositionen.ADRESSE, max = 40_000_000) }.getOrNull()
                ?.let { withContext(Dispatchers.Default) { de.troetenrepublik.app.market.Leerpositionen.auswerten(it.decodeToString(), heute) } }.orEmpty()
            "SE" -> runCatching { lpNetz.datei(de.troetenrepublik.app.market.Leerpositionen.FI_AKTUELL, max = 10_000_000) }.getOrNull()
                ?.let { roh -> withContext(Dispatchers.Default) { de.troetenrepublik.app.market.Leerpositionen.fiAktuell(roh).takeIf { it.isNotEmpty() }?.let { de.troetenrepublik.app.market.Leerpositionen.fortschreiben(d.titel, it, heute) } } }.orEmpty()
            else -> emptyMap()
        }
        val n = if (titel.isEmpty()) d.copy(abgerufen = jetzt) else de.troetenrepublik.app.market.Leerpositionen.Datei(stand = heute.toString(), titel = titel, abgerufen = jetzt, land = land)
        _leerpositionen.value = _leerpositionen.value + (land to n)
        withContext(Dispatchers.IO) { runCatching { lpDatei(land).schreiben(de.troetenrepublik.app.market.Leerpositionen.schreiben(n).toByteArray(Charsets.UTF_8)) } }
        n
    }

    // ------------------------------------------------------------ Insidergeschaefte weiterer Laender (SE seit 5.8, weitere seit 5.9)

    private fun euDatei(land: String) = de.troetenrepublik.app.plattform.SichereDatei(java.io.File(store.ordner, "insider_${land.lowercase()}.json"))
    private val euLock = kotlinx.coroutines.sync.Mutex()
    private val euNetz by lazy { MarketApi() }
    private val _euInsider = MutableStateFlow<Map<String, de.troetenrepublik.app.market.EuInsider.Datei>>(emptyMap())

    // Je Land der mitgelieferte Stand, vereint mit selbst geholten Meldungen; leer, solange nichts geladen ist.
    val euInsider: StateFlow<Map<String, de.troetenrepublik.app.market.EuInsider.Datei>> = _euInsider.asStateFlow()

    private fun euStand(land: String): de.troetenrepublik.app.market.EuInsider.Datei {
        _euInsider.value[land]?.let { return it }
        val beigelegt = runCatching { de.troetenrepublik.app.market.EuInsider.lesen(de.troetenrepublik.app.plattform.Plattform.asset("copytrading/insider_${land.lowercase()}.json").decodeToString()) }.getOrNull()
            ?: de.troetenrepublik.app.market.EuInsider.Datei(land = land)
        val eigen = runCatching { de.troetenrepublik.app.market.EuInsider.lesen(euDatei(land).lesen().toString(Charsets.UTF_8)) }.getOrNull()
        val d = beigelegt.copy(
            geschaefte = de.troetenrepublik.app.market.EuInsider.vereinen(beigelegt.geschaefte, eigen?.geschaefte.orEmpty(), secAbTag()),
            stand = maxOf(beigelegt.stand, eigen?.stand.orEmpty()),
            abgerufen = eigen?.abgerufen ?: 0L,
            land = land,
            gelesen = (beigelegt.gelesen + eigen?.gelesen.orEmpty()).distinct(),
            lueckeVon = eigen?.lueckeVon.orEmpty().takeIf { eigen?.lueckeBis.orEmpty() > beigelegt.stand }.orEmpty(),
            lueckeBis = eigen?.lueckeBis.orEmpty().takeIf { it > beigelegt.stand }.orEmpty(),
        )
        _euInsider.value = _euInsider.value + (land to d)
        return d
    }

    // Neue Meldungen eines Landes seit von; null, wenn die Quelle nicht erreichbar war.
    private suspend fun euFrisch(land: String, d: de.troetenrepublik.app.market.EuInsider.Datei, von: java.time.LocalDate, heute: java.time.LocalDate): List<de.troetenrepublik.app.market.EuInsider.Geschaeft>? {
        val q = de.troetenrepublik.app.market.InsiderQuellen.netz(euNetz)
        val bekannt = d.gelesen.toHashSet()
        return runCatching {
            when (land) {
                "SE" -> euNetz.datei(de.troetenrepublik.app.market.EuInsider.exportAdresse(von.toString(), heute.toString()))
                    ?.let { roh -> withContext(Dispatchers.Default) { de.troetenrepublik.app.market.EuInsider.schwedenLesen(de.troetenrepublik.app.market.EuInsider.text(roh)) } }
                "FR" -> de.troetenrepublik.app.market.InsiderQuellen.amfHolen(q, von.toString(), heute.toString(), bekannt, max = 150)
                "NL" -> de.troetenrepublik.app.market.InsiderQuellen.afmHolen(q, von.toString(), bekannt, max = 150)
                "CH" -> de.troetenrepublik.app.market.InsiderWelt.sixHolen(q, von.toString(), heute.toString())
                else -> null
            }
        }.getOrNull()
    }

    // Stand eines Landes laden und hoechstens alle zwoelf Stunden die Meldungen seit dem Stand nachholen.
    // Rechnen (Vereinen, Filtern, Lesen der Antworten) ausserhalb des Hauptthreads, damit die Seite beim Laden nicht ruckelt (0.68)
    suspend fun euLaden(land: String, netz: Boolean = true): de.troetenrepublik.app.market.EuInsider.Datei = withContext(Dispatchers.Default) { euLock.withLock {
        val d = withContext(Dispatchers.IO) { euStand(land) }
        val jetzt = System.currentTimeMillis()
        if (!netz || jetzt - d.abgerufen < 12 * 3_600_000L) return@withLock d
        val heute = de.troetenrepublik.app.engine.Uhr.heute()
        val von = if (d.stand.isBlank() && land == "CH") heute.minusDays(365)
            else runCatching { java.time.LocalDate.parse(d.stand).minusDays(3) }.getOrNull()?.takeIf { it.isAfter(heute.minusDays(60)) } ?: heute.minusDays(30)
        val frisch = euFrisch(land, d, von, heute)
        // Antwort der Quelle lesbar oder nicht (Seite Datenquellen: zuletzt gueltig geholt)
        de.troetenrepublik.app.market.Quellen.inhaltGelesen("insider-" + land.lowercase(), frisch != null)
        if (frisch == null) return@withLock d.also { _euInsider.value = _euInsider.value + (land to it.copy(abgerufen = jetzt)) }
        val liste = de.troetenrepublik.app.market.EuInsider.vereinen(d.geschaefte, frisch.filter { it.isin.isNotEmpty() }, secAbTag())
        val gelesen = (d.gelesen + frisch.map { it.id }.filter { it.isNotEmpty() }).distinct().takeLast(30_000)
        // Der Abruf deckt von von bis heute ab; liegt der mitgelieferte Stand weiter zurueck, bleibt eine Luecke
        val (neuStand, luecke) = de.troetenrepublik.app.engine.Luecke.nachAbruf(d.stand, d.luecke(), von.toString(), heute.toString())
        val n = d.copy(geschaefte = liste, stand = maxOf(d.stand, neuStand), abgerufen = jetzt, gelesen = gelesen, lueckeVon = luecke?.von.orEmpty(), lueckeBis = luecke?.bis.orEmpty())
        _euInsider.value = _euInsider.value + (land to n)
        withContext(Dispatchers.IO) {
            runCatching { euDatei(land).schreiben(de.troetenrepublik.app.market.EuInsider.schreiben(n).toByteArray(Charsets.UTF_8)) }
        }
        n
    }
    }

    // ------------------------------------------------------------ Insidergeschaefte je Aktie: Indien und Hongkong (seit 5.9), Australien nur ueber Import

    private val _weltInsider = MutableStateFlow<Map<String, Pair<String, List<de.troetenrepublik.app.market.EuInsider.Geschaeft>>>>(emptyMap())
    private val weltZeit = java.util.concurrent.ConcurrentHashMap<String, Long>()

    // Je Symbol Land und Geschaefte: aus den auf dem Geraet gespeicherten Meldungen (selbst importiert oder je Aktie
    // geholt); neu geholt wird hoechstens alle zwoelf Stunden und, nach einem Import, nur seit dessen Stand.
    val weltInsider: StateFlow<Map<String, Pair<String, List<de.troetenrepublik.app.market.EuInsider.Geschaeft>>>> = _weltInsider.asStateFlow()

    // Vom Nutzer heruntergeladene Registerdaten uebernehmen (BSE, Consob, umgewandelte SEDI-Berichte); sie bleiben
    // auf dem Geraet. Liefert die Meldung fuer den Hinweis.
    suspend fun registerImport(daten: ByteArray): String {
        val heute = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
        return when (val e = withContext(Dispatchers.Default) { de.troetenrepublik.app.market.RegisterImport.erkennen(daten, heute) }) {
            is de.troetenrepublik.app.market.RegisterImport.Ergebnis.Insider -> euLock.withLock {
                val akt = withContext(Dispatchers.IO) { euStand(e.land) }
                val n = akt.copy(
                    geschaefte = de.troetenrepublik.app.market.EuInsider.vereinen(akt.geschaefte, e.geschaefte, secAbTag()),
                    stand = maxOf(akt.stand, e.stand), land = e.land,
                )
                _euInsider.value = _euInsider.value + (e.land to n)
                weltZeit.clear()
                withContext(Dispatchers.IO) { runCatching { euDatei(e.land).schreiben(de.troetenrepublik.app.market.EuInsider.schreiben(n).toByteArray(Charsets.UTF_8)) } }
                S.imInsider(e.geschaefte.size, e.land)
            }
            is de.troetenrepublik.app.market.RegisterImport.Ergebnis.Leer -> lpLock.withLock {
                val neu = e.datei.copy(abgerufen = System.currentTimeMillis())
                // Bundesanzeiger: eine Liste der aktuellen Positionen schreibt den vorhandenen Verlauf fort, eine Datei mit
                // frueheren Meldungen ersetzt nur die Aktien, die sie enthaelt
                val d = if (neu.land != "DE") neu else {
                    val akt = withContext(Dispatchers.IO) { lpStand("DE") }
                    val offen = e.offen
                    if (offen != null) neu.copy(titel = de.troetenrepublik.app.market.Leerpositionen.fortschreiben(neu.titel + akt.titel, offen, heute), stand = maxOf(akt.stand, neu.stand))
                    else neu.copy(titel = akt.titel + neu.titel, stand = maxOf(akt.stand, neu.stand))
                }
                _leerpositionen.value = _leerpositionen.value + (d.land to d)
                withContext(Dispatchers.IO) { runCatching { lpDatei(d.land).schreiben(de.troetenrepublik.app.market.Leerpositionen.schreiben(d).toByteArray(Charsets.UTF_8)) } }
                S.imLeer(d.titel.size, d.land)
            }
            // FCA (5.94): aktuelle und fruehere Liste als selbst geladene CSV; beide bleiben roh gespeichert, damit die
            // zuletzt geladene Fassung jeder Liste zusammen ausgewertet wird
            is de.troetenrepublik.app.market.RegisterImport.Ergebnis.Fca -> lpLock.withLock {
                val datei = de.troetenrepublik.app.plattform.SichereDatei(java.io.File(store.ordner, if (e.historisch) "fca-historisch.csv" else "fca-aktuell.csv"))
                val andere = de.troetenrepublik.app.plattform.SichereDatei(java.io.File(store.ordner, if (e.historisch) "fca-aktuell.csv" else "fca-historisch.csv"))
                withContext(Dispatchers.IO) { runCatching { datei.schreiben(e.text.toByteArray(Charsets.UTF_8)) } }
                val zweite = withContext(Dispatchers.IO) { runCatching { andere.lesen().toString(Charsets.UTF_8) }.getOrNull() }
                val aktuell = if (e.historisch) zweite.orEmpty() else e.text
                val historisch = if (e.historisch) e.text else zweite
                val titel = withContext(Dispatchers.Default) { de.troetenrepublik.app.market.Leerpositionen.fcaAuswerten(aktuell, historisch, heute) }
                val d = de.troetenrepublik.app.market.Leerpositionen.Datei(stand = heute.toString(), titel = titel, abgerufen = System.currentTimeMillis(), land = "GB")
                _leerpositionen.value = _leerpositionen.value + ("GB" to d)
                withContext(Dispatchers.IO) { runCatching { lpDatei("GB").schreiben(de.troetenrepublik.app.market.Leerpositionen.schreiben(d).toByteArray(Charsets.UTF_8)) } }
                S.imLeer(d.titel.size, "GB")
            }
            is de.troetenrepublik.app.market.RegisterImport.Ergebnis.Unbekannt -> if (e.pdf) S.imPdf else S.imUnbekannt
        }
    }

    // Rechnen (Vereinen, Filtern, Lesen der Antworten) ausserhalb des Hauptthreads, damit die Seite beim Laden nicht ruckelt (0.68)
    suspend fun weltLaden(symbol: String, isin: String) = withContext(Dispatchers.Default) { weltLadenRechnen(symbol, isin) }

    private suspend fun weltLadenRechnen(symbol: String, isin: String) {
        val (land, code) = de.troetenrepublik.app.market.InsiderWelt.ort(symbol, isin) ?: return
        val firma = runCatching { profileFor(symbol)?.longName }.getOrNull().orEmpty()
        fun eigene(d: de.troetenrepublik.app.market.EuInsider.Datei) =
            d.geschaefte.filter { de.troetenrepublik.app.market.InsiderWelt.passt(it, land, code, isin, firma) }
        val d = euLock.withLock { withContext(Dispatchers.IO) { euStand(land) } }
        _weltInsider.update { it + (symbol to (land to eigene(d))) }
        val jetzt = System.currentTimeMillis()
        if (jetzt - (weltZeit[symbol] ?: 0L) < 12 * 3_600_000L) return
        weltZeit[symbol] = jetzt
        val heute = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
        // Europa (seit 5.91): beim ersten Mal ein Jahr, danach seit dem letzten Abruf dieser Aktie minus drei Tage
        val europa = land in de.troetenrepublik.app.market.InsiderEuropa.LAENDER
        val ab = runCatching { java.time.LocalDate.parse(if (europa) d.je[symbol].orEmpty() else d.stand).minusDays(3) }.getOrNull()
        val e = withContext(Dispatchers.IO) {
            val netz = de.troetenrepublik.app.market.InsiderQuellen.netz(MarketApi())
            if (europa) runCatching { de.troetenrepublik.app.market.InsiderEuropa.holen(netz, land, code, isin, firma, heute, ab, d.gelesen.toHashSet()) }.getOrNull()?.let { land to it }
            else runCatching { de.troetenrepublik.app.market.InsiderWelt.holen(netz, symbol, isin, firma, heute, ab) }.getOrNull()
        }
        if (e == null) { weltZeit.remove(symbol); return }
        // Dasselbe Geschaeft kann an NSE und BSE gemeldet sein: Person, Tag, Menge und Art gleich gilt als doppelt
        fun doppelt(g: de.troetenrepublik.app.market.EuInsider.Geschaeft) = "${de.troetenrepublik.app.market.InsiderWelt.name(g.p)}|${g.d}|${g.n}|${g.art}"
        val n = euLock.withLock {
            val akt = withContext(Dispatchers.IO) { euStand(land) }
            val bekannt = akt.geschaefte.map { doppelt(it) }.toHashSet()
            // Oman nennt keinen Handelstag: dieselbe Zeile an einem spaeteren Abruf erkennt die Kennung
            val ids = akt.geschaefte.mapTo(HashSet()) { it.id }
            val neu = e.second.filter { doppelt(it) !in bekannt && (it.id.isEmpty() || it.id !in ids) }.distinctBy { if (it.d.isEmpty()) it.id else doppelt(it) }
            val n = akt.copy(
                geschaefte = de.troetenrepublik.app.market.EuInsider.vereinen(akt.geschaefte, neu, secAbTag()),
                gelesen = (akt.gelesen + e.second.map { it.id }.filter { it.isNotEmpty() }).distinct().takeLast(30_000),
                je = if (europa) akt.je + (symbol to heute.toString()) else akt.je,
            )
            _euInsider.value = _euInsider.value + (land to n)
            withContext(Dispatchers.IO) {
                runCatching { euDatei(land).schreiben(de.troetenrepublik.app.market.EuInsider.schreiben(n).toByteArray(Charsets.UTF_8)) }
            }
            n
        }
        _weltInsider.update { it + (symbol to (land to eigene(n))) }
    }

    // ------------------------------------------------------------ SEC: Insidergeschaefte an US-Boersen (seit 5.6)

    private val secDatei by lazy { de.troetenrepublik.app.plattform.SichereDatei(java.io.File(store.ordner, "sec.json")) }
    private val secLock = kotlinx.coroutines.sync.Mutex()
    private val secNetz by lazy { MarketApi() }
    private val _sec = MutableStateFlow<de.troetenrepublik.app.market.SecInsider.Datei?>(null)

    // Mitgelieferter Stand, vereint mit selbst geholten Meldungen; null, solange nicht geladen.
    val sec: StateFlow<de.troetenrepublik.app.market.SecInsider.Datei?> = _sec.asStateFlow()

    // US-Meldungen zaehlen 18 Monate, wie die mitgelieferten Datensaetze.
    private fun secAbTag(): String = de.troetenrepublik.app.engine.Uhr.heute().minusDays(550).toString()

    private fun secStand(): de.troetenrepublik.app.market.SecInsider.Datei {
        _sec.value?.let { return it }
        val beigelegt = runCatching { de.troetenrepublik.app.market.SecInsider.lesen(de.troetenrepublik.app.plattform.Plattform.asset("copytrading/sec.json").decodeToString()) }.getOrNull()
            ?: de.troetenrepublik.app.market.SecInsider.Datei()
        val eigen = runCatching { de.troetenrepublik.app.market.SecInsider.lesen(secDatei.lesen().toString(Charsets.UTF_8)) }.getOrNull()
        val d = beigelegt.copy(
            geschaefte = de.troetenrepublik.app.market.SecInsider.vereinen(beigelegt.geschaefte, eigen?.geschaefte.orEmpty(), secAbTag()),
            stand = maxOf(beigelegt.stand, eigen?.stand.orEmpty()),
            abgerufen = eigen?.abgerufen ?: 0L,
            lueckeVon = eigen?.lueckeVon.orEmpty().takeIf { eigen?.lueckeBis.orEmpty() > beigelegt.stand }.orEmpty(),
            lueckeBis = eigen?.lueckeBis.orEmpty().takeIf { it > beigelegt.stand }.orEmpty(),
            standJe = eigen?.standJe.orEmpty(),
        )
        _sec.value = d
        return d
    }

    // Stand laden; mit Kontaktadresse unter Zugaenge hoechstens alle sechs Stunden (neu: sofort) die neuesten
    // Form-4-Meldungen der SEC nachholen, mit symbol zusaetzlich die Meldungen dieses Werts.
    // Nur der Stand auf dem Geraet (mitgeliefert und zuletzt gespeichert), ohne Abruf: fuer den Bull- und Bear-Case,
    // solange der Abschnitt Hintergrund der Wertpapierseite zu ist (5.91).
    suspend fun secOhneNetz() { secLock.withLock { withContext(Dispatchers.IO) { secStand() } } }

    private val secIpoLock = kotlinx.coroutines.sync.Mutex()
    private var secIpoCache: Pair<Long, List<de.troetenrepublik.app.market.SecIpo.Eintrag>>? = null

    // Neue Boersenprospekte und Preisfestsetzungen der letzten fuenf Handelstage aus dem Tagesindex der SEC;
    // ohne Kontaktadresse leer, drei Stunden zwischengespeichert.
    suspend fun secIpos(): List<de.troetenrepublik.app.market.SecIpo.Eintrag> = secIpoLock.withLock {
        val kennung = de.troetenrepublik.app.market.SecInsider.kennung(settingsFlow.value.secKontakt) ?: return@withLock emptyList()
        val jetzt = System.currentTimeMillis()
        secIpoCache?.takeIf { jetzt - it.first < 3 * 3_600_000L }?.let { return@withLock it.second }
        val kopf = mapOf("User-Agent" to kennung, "Accept" to "*/*")
        val heute = java.time.LocalDate.now(java.time.ZoneId.of("America/New_York"))
        val alle = mutableListOf<de.troetenrepublik.app.market.SecIpo.Eintrag>()
        var geholt = 0
        for (tag in de.troetenrepublik.app.market.SecInsider.letzteHandelstage(heute, 5)) {
            delay(150)
            val t = runCatching { secNetz.anfrage(de.troetenrepublik.app.market.SecInsider.tagesIndex(tag), kopf).takeIf { it.ok }?.text }.getOrNull() ?: continue
            geholt++
            alle += de.troetenrepublik.app.market.SecIpo.lesen(t)
        }
        val liste = alle.distinctBy { it.datei }.sortedWith(compareByDescending<de.troetenrepublik.app.market.SecIpo.Eintrag> { it.tag }.thenBy { it.name })
        if (geholt > 0) secIpoCache = jetzt to liste
        liste
    }

    // ------------------------------------------------------------ Kalender der Nasdaq (5.95), schluesselfrei

    // Abrufe mit Zwischenspeicher je Adresse, Pause nach Fehlern und Mindestabstand
    private val nasdaqAbruf by lazy { de.troetenrepublik.app.market.NasdaqAbruf({ stimmungNetz.anfrage(it, de.troetenrepublik.app.market.NasdaqKalender.KOPF) }, sperre = de.troetenrepublik.app.market.AbrufAblage) }

    // true, solange nach Fehlern keine Anfrage an die Nasdaq geht
    fun nasdaqPausiert(): Boolean = nasdaqAbruf.pausiert()

    // Griechen der Nasdaq (0.78): eigener Abrufer, damit ein Fehlschlag die Kalender nicht pausiert
    private val nasdaqGriechenAbruf by lazy { de.troetenrepublik.app.market.NasdaqAbruf({ stimmungNetz.anfrage(it, de.troetenrepublik.app.market.NasdaqKalender.KOPF) }, sperre = de.troetenrepublik.app.market.AbrufAblage) }

    // Gamma je Kontrakt fuer die Verfaelle der Kette mit offenem Interesse (hoechstens sechs Abrufe, 30 Minuten
    // zwischengespeichert); leer ohne Antwort. Die US-Notierung zaehlt, wenn die Kette ueber sie laeuft.
    suspend fun nasdaqGammas(
        symbol: String, kontrakte: List<de.troetenrepublik.app.engine.Optionsanalyse.Kontrakt>,
    ): Map<de.troetenrepublik.app.engine.NasdaqGriechen.Schluessel, Double> {
        val ticker = (market.optionsNotierung(symbol) ?: symbol).takeIf { it.isNotBlank() && it.none { c -> c == '.' || c == '-' || c == '^' || c == '=' } }
            ?: return emptyMap()
        val etf = de.troetenrepublik.app.market.Symbols.typeOf(symbol) == de.troetenrepublik.app.data.AssetType.ETF
        val jetztS = System.currentTimeMillis() / 1000
        val verfaelle = kontrakte.filter { it.offen > 0 && it.verfall > jetztS - 86_400L }.map { it.verfall }.distinct().sorted().take(6)
        val aus = HashMap<de.troetenrepublik.app.engine.NasdaqGriechen.Schluessel, Double>()
        for (v in verfaelle) {
            val m = de.troetenrepublik.app.market.ohneAbbruch {
                nasdaqGriechenAbruf.holen(
                    de.troetenrepublik.app.engine.NasdaqGriechen.adresse(ticker, etf, v),
                    { _ -> 30 * 60_000L },
                    { t -> de.troetenrepublik.app.engine.NasdaqGriechen.lesen(t, v) },
                )
            }.getOrNull() ?: continue
            aus += m
        }
        return aus
    }

    // Boersengaenge der Nasdaq: Folgemonat, dieser und die zwei Vormonate, je Monat ein Abruf, am Ende zusammengefuehrt;
    // null, wenn kein Monat lesbar war
    suspend fun nasdaqIpos(): List<de.troetenrepublik.app.market.IpoKalender.Angebot>? {
        val heute = java.time.LocalDate.now(java.time.ZoneId.of("America/New_York"))
        val listen = de.troetenrepublik.app.engine.IpoUebersicht.abrufMonate(heute).mapNotNull { monat ->
            nasdaqAbruf.holen(
                de.troetenrepublik.app.market.NasdaqKalender.ipoAdresse(monat),
                { _ -> de.troetenrepublik.app.market.NasdaqKalender.MONAT_HALTBAR_MS },
                de.troetenrepublik.app.market.NasdaqKalender::ipoLesen,
            )
        }
        if (listen.isEmpty()) return null
        return de.troetenrepublik.app.market.IpoKalender.zusammen(listen.flatten())
    }

    // Dividenden (Ex-Tag) je Tag; am Wochenende leer ohne Abruf
    suspend fun nasdaqDividenden(tage: List<java.time.LocalDate>): Map<java.time.LocalDate, List<de.troetenrepublik.app.market.NasdaqKalender.Dividende>?> =
        tage.associateWith { tag ->
            if (tag.dayOfWeek == java.time.DayOfWeek.SATURDAY || tag.dayOfWeek == java.time.DayOfWeek.SUNDAY) emptyList()
            else nasdaqAbruf.holen(
                de.troetenrepublik.app.market.NasdaqKalender.dividendenAdresse(tag),
                { ab -> de.troetenrepublik.app.market.NasdaqKalender.haltbarMs(tag, ab) },
                de.troetenrepublik.app.market.NasdaqKalender::dividendenLesen,
            )
        }

    // Letzte Zahlentermine eines US-Werts mit Tag, Gewinn und Erwartung (0.76); null ohne Antwort
    suspend fun nasdaqUeberraschung(symbol: String): List<de.troetenrepublik.app.market.NasdaqKalender.Ueberraschung>? {
        if ('.' in symbol) return null
        return nasdaqAbruf.holen(
            de.troetenrepublik.app.market.NasdaqKalender.ueberraschungAdresse(symbol),
            { de.troetenrepublik.app.market.NasdaqKalender.UEBERRASCHUNG_HALTBAR_MS },
            de.troetenrepublik.app.market.NasdaqKalender::ueberraschungLesen,
        )
    }

    // Kerzen und Optionskette fuer die Einordnung der Bewegung zu den Zahlen (0.76), je Wert gemerkt
    private val zahlenKerzenCache = ConcurrentHashMap<String, Pair<Long, List<Candle>>>()
    suspend fun zahlenKerzen(symbol: String): List<Candle> {
        val jetzt = System.currentTimeMillis()
        zahlenKerzenCache[symbol]?.takeIf { jetzt - it.first < 6 * 3_600_000L }?.let { return it.second }
        val l = runCatching { market.zeitraum(symbol, jetzt - 1000L * 86_400_000L, jetzt).candles }.getOrDefault(emptyList())
        if (l.isNotEmpty()) zahlenKerzenCache[symbol] = jetzt to l
        return l
    }

    // Wirtschaftstermine je Tag, auch am Wochenende (Feiertage); der Endpunkt liefert die Termine des Vortags,
    // darum Abruf mit T+1
    suspend fun nasdaqWirtschaft(tage: List<java.time.LocalDate>): Map<java.time.LocalDate, List<de.troetenrepublik.app.market.NasdaqKalender.Wirtschaftstermin>?> =
        tage.associateWith { tag ->
            nasdaqAbruf.holen(
                de.troetenrepublik.app.market.NasdaqKalender.wirtschaftAdresse(tag),
                { ab -> de.troetenrepublik.app.market.NasdaqKalender.haltbarMs(tag, ab) },
            ) { text ->
                de.troetenrepublik.app.market.NasdaqKalender.wirtschaftLesen(text, tag)
            }
        }

    // [Wirkungskette]
    // Aussagen zu den Wirtschaftsmeldungen der letzten beiden Handelstage (und des laufenden); Termine kommen aus dem
    // Zwischenspeicher der Nasdaq-Abrufe. Leer ohne Meldungen.
    suspend fun wirkungAussagen(): List<de.troetenrepublik.app.engine.Wirkungskette.Aussage> {
        val heute = java.time.LocalDate.now(java.time.ZoneId.of("America/New_York"))
        val tage = ArrayList<java.time.LocalDate>()
        var t = heute
        while (tage.size < 2) {
            if (t.dayOfWeek != java.time.DayOfWeek.SATURDAY && t.dayOfWeek != java.time.DayOfWeek.SUNDAY) tage += t
            t = t.minusDays(1)
        }
        // Der Montag in Asien beginnt am Sonntagabend in New York: Termine aus Japan und China stehen unter dem Sonntag
        val sonntage = tage.filter { it.dayOfWeek == java.time.DayOfWeek.MONDAY }.map { it.minusDays(1) } +
            listOfNotNull(heute.takeIf { it.dayOfWeek == java.time.DayOfWeek.SUNDAY })
        val liste = nasdaqWirtschaft(tage + sonntage)
        return de.troetenrepublik.app.engine.Wirkungskette.aussagen(liste.entries.associate { it.key.toEpochDay() to it.value.orEmpty() })
    }

    // Aussage eines einzelnen Tages (Tagesrueckblick); nur fuer Tage der letzten zehn Tage
    suspend fun wirkungAussagenTag(tag: java.time.LocalDate): List<de.troetenrepublik.app.engine.Wirkungskette.Aussage> {
        val heute = java.time.LocalDate.now(java.time.ZoneId.of("America/New_York"))
        if (tag.isAfter(heute) || tag.isBefore(heute.minusDays(10)) ||
            tag.dayOfWeek == java.time.DayOfWeek.SATURDAY || tag.dayOfWeek == java.time.DayOfWeek.SUNDAY) return emptyList()
        val liste = nasdaqWirtschaft(listOf(tag))[tag].orEmpty()
        return de.troetenrepublik.app.engine.Wirkungskette.aussagen(mapOf(tag.toEpochDay() to liste))
    }

    @Volatile private var wirkungKurseCache: Triple<Long, Set<String>, Map<String, List<de.troetenrepublik.app.data.Candle>>>? = null

    // Tagesschluesse fuer die Gegenprobe: Kuerzel der Regionen plus der Zins-Futures, 15 Minuten gemerkt
    suspend fun wirkungKurse(regionen: Set<de.troetenrepublik.app.engine.Wirkungskette.Region>): Map<String, List<de.troetenrepublik.app.data.Candle>> {
        val we = de.troetenrepublik.app.engine.Wirkungskette
        val now = System.currentTimeMillis()
        val heute = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
        val noetig = regionen.flatMap { we.symbole(it) }.toSet() +
            (if (de.troetenrepublik.app.engine.Wirkungskette.Region.US in regionen) we.zinsKontrakte(heute.year, heute.monthValue) else emptyList())
        wirkungKurseCache?.takeIf { now - it.first < 15 * 60_000L && it.second.containsAll(noetig) }?.let { return it.third }
        val neu = HashMap<String, List<de.troetenrepublik.app.data.Candle>>()
        for (gruppe in noetig.toList().chunked(20)) neu.putAll(schlusskurse(gruppe, "1mo"))
        if (neu.isNotEmpty()) wirkungKurseCache = Triple(now, noetig, neu)
        return neu.ifEmpty { wirkungKurseCache?.third.orEmpty() }
    }

    // kuenftige Splits (Liste unabhaengig vom Abfragetag)
    suspend fun nasdaqSplits(): List<de.troetenrepublik.app.market.NasdaqKalender.Split>? {
        val heute = java.time.LocalDate.now(java.time.ZoneId.of("America/New_York"))
        return nasdaqAbruf.holen(
            de.troetenrepublik.app.market.NasdaqKalender.splitsAdresse(heute),
            { _ -> de.troetenrepublik.app.market.NasdaqKalender.MONAT_HALTBAR_MS },
            de.troetenrepublik.app.market.NasdaqKalender::splitsLesen,
        )
    }

    // Quartalszahlen der Nasdaq je Tag; am Wochenende leer ohne Abruf
    private suspend fun nasdaqZahlen(tage: List<java.time.LocalDate>): Map<java.time.LocalDate, List<de.troetenrepublik.app.market.Quartalszahlen.Termin>?> =
        tage.associateWith { tag ->
            if (tag.dayOfWeek == java.time.DayOfWeek.SATURDAY || tag.dayOfWeek == java.time.DayOfWeek.SUNDAY) emptyList()
            else nasdaqAbruf.holen(
                de.troetenrepublik.app.market.NasdaqKalender.zahlenAdresse(tag),
                { ab -> de.troetenrepublik.app.market.NasdaqKalender.haltbarMs(tag, ab) },
            ) { text ->
                de.troetenrepublik.app.market.NasdaqKalender.zahlenLesen(text, tag)
            }
        }

    // ------------------------------------------------------------ Boersengaenge

    // Boersengaenge (5.95): Nasdaq schluesselfrei als Hauptquelle, Finnhub mit dem Schluessel des Nutzers ergaenzt;
    // null nur, wenn beide nichts liefern. Antworten sechs Stunden gespeichert, geholt beim Oeffnen der Ansicht.
    private val ipoLock = kotlinx.coroutines.sync.Mutex()
    @Volatile private var ipoCache: Pair<Long, List<de.troetenrepublik.app.market.IpoKalender.Angebot>>? = null
    private val kalenderKopf = mapOf("Accept" to "application/json")

    // Kalender von Finnhub mit dem Schluessel des Nutzers: 75 Tage zurueck bis 60 voraus in einem Abruf, sechs
    // Stunden gespeichert. Null ohne Schluessel oder wenn noch nie etwas ankam.
    private suspend fun finnhubIpos(neu: Boolean): List<de.troetenrepublik.app.market.IpoKalender.Angebot>? = ipoLock.withLock {
        val schluessel = settingsFlow.value.keyFinnhub.trim().ifEmpty { null } ?: return@withLock null
        val jetzt = System.currentTimeMillis()
        ipoCache?.takeIf { !neu && jetzt - it.first < 6 * 3_600_000L }?.let { return@withLock it.second }
        val kontingent = news.kontingent
        if (!kontingent.erlaubt(de.troetenrepublik.app.market.Abrufkontingent.FINNHUB)) return@withLock ipoCache?.second
        kontingent.buchen(de.troetenrepublik.app.market.Abrufkontingent.FINNHUB)
        val heute = java.time.LocalDate.now(java.time.ZoneId.of("America/New_York"))
        val a = de.troetenrepublik.app.market.ohneAbbruch {
            stimmungNetz.anfrage(de.troetenrepublik.app.market.IpoKalender.finnhubAdresse(heute.minusDays(75), heute.plusDays(60), schluessel), kalenderKopf)
        }.getOrNull()
        val liste = a?.takeIf { it.ok }?.text?.let(de.troetenrepublik.app.market.IpoKalender::finnhubLesen) ?: return@withLock ipoCache?.second
        ipoCache = jetzt to liste
        liste
    }

    // beide Quellen je Angebot zusammenfuehren
    suspend fun ipoKalender(neu: Boolean = false): List<de.troetenrepublik.app.market.IpoKalender.Angebot>? =
        de.troetenrepublik.app.market.NasdaqKalender.ipoZusammen(
            de.troetenrepublik.app.market.ohneAbbruch { nasdaqIpos() }.getOrNull(),
            de.troetenrepublik.app.market.ohneAbbruch { finnhubIpos(neu) }.getOrNull(),
        )

    // CIK eines Angebots mit Kuerzel aus der Kuerzelliste der SEC (nur mit Kontaktadresse), fuer die Prospekte.
    suspend fun ipoCik(symbol: String?): String? {
        val s = symbol?.takeIf { it.isNotBlank() } ?: return null
        val kennung = de.troetenrepublik.app.market.SecInsider.kennung(settingsFlow.value.secKontakt) ?: return null
        val t = secKuerzel ?: runCatching {
            secNetz.anfrage(de.troetenrepublik.app.market.SecFormen.KUERZEL, mapOf("User-Agent" to kennung, "Accept" to "*/*")).takeIf { it.ok }?.text
        }.getOrNull()?.also { secKuerzel = it }
        return t?.let { de.troetenrepublik.app.market.SecFormen.cikAusKuerzeln(it, s) }
    }

    // Anbieter des Ergebniskalenders nach den Schluesseln unter Zugaenge; null ohne Schluessel.
    fun zahlenAnbieter(): String? = settingsFlow.value.let { de.troetenrepublik.app.market.Quartalszahlen.anbieter(it.keyFinnhub, it.keyAlphaVantage) }

    // Quartalszahlen je Handelstag (5.95): Nasdaq schluesselfrei als Grundlage (je Tag ein Abruf), ergaenzt um
    // Finnhub oder Alpha Vantage mit dem Schluessel des Nutzers, je Symbol gemischt. Ohne Antwort null je Tag.
    private val zahlenLock = kotlinx.coroutines.sync.Mutex()
    private val zahlenWochen = java.util.concurrent.ConcurrentHashMap<Pair<java.time.LocalDate, java.time.LocalDate>, Triple<Long, java.time.LocalDate, List<de.troetenrepublik.app.market.Quartalszahlen.Termin>>>()
    @Volatile private var zahlenAlpha: Pair<Long, List<de.troetenrepublik.app.market.Quartalszahlen.Termin>>? = null

    // Schluessel-Anbieter: Finnhub mit einem Abruf fuer alle Tage (kommende und heutige sechs Stunden gespeichert,
    // vergangene einen Tag, wenn nach ihrem Ende geholt), ersatzweise Alpha Vantage mit einer CSV fuer drei Monate
    // (zwoelf Stunden gespeichert, nur heute und spaeter, vergangene Tage bleiben null).
    private suspend fun quartalszahlenSchluessel(tage: List<java.time.LocalDate>, neu: Boolean = false): Map<java.time.LocalDate, List<de.troetenrepublik.app.market.Quartalszahlen.Termin>?> =
        zahlenLock.withLock {
            val leer = tage.associateWith { null as List<de.troetenrepublik.app.market.Quartalszahlen.Termin>? }
            if (tage.isEmpty()) return@withLock leer
            val qz = de.troetenrepublik.app.market.Quartalszahlen
            val ko = de.troetenrepublik.app.market.Abrufkontingent
            val s = settingsFlow.value
            val jetzt = System.currentTimeMillis()
            val heute = java.time.LocalDate.now(java.time.ZoneId.of("America/New_York"))
            val von = tage.min()
            val bis = tage.max()
            fun verteilen(l: List<de.troetenrepublik.app.market.Quartalszahlen.Termin>, ab: java.time.LocalDate = von) =
                tage.associateWith { t -> if (t < ab) null else l.filter { it.tag == t.toEpochDay() }.distinctBy { it.symbol } }
            s.keyFinnhub.trim().ifEmpty { null }?.let { k ->
                val alt = zahlenWochen[von to bis]
                val frisch = alt?.takeIf { (am, abruftag, _) ->
                    !neu && when {
                        bis >= heute -> jetzt - am < 6 * 3_600_000L
                        abruftag > bis -> jetzt - am < 24 * 3_600_000L
                        else -> false
                    }
                }
                val l = frisch?.third ?: run {
                    if (!news.kontingent.erlaubt(ko.FINNHUB)) null else {
                        news.kontingent.buchen(ko.FINNHUB)
                        de.troetenrepublik.app.market.ohneAbbruch { stimmungNetz.anfrage(qz.finnhubAdresse(von, bis, k), kalenderKopf) }.getOrNull()
                            ?.takeIf { it.ok }?.text?.let(qz::finnhubLesen)?.also { zahlenWochen[von to bis] = Triple(jetzt, heute, it) }
                    }
                } ?: alt?.third
                if (l != null) return@withLock verteilen(l)
            }
            s.keyAlphaVantage.trim().ifEmpty { null }?.let { k ->
                if (bis < heute) return@withLock leer
                val alt = zahlenAlpha
                val l = alt?.takeIf { !neu && jetzt - it.first < 12 * 3_600_000L }?.second ?: run {
                    if (!news.kontingent.erlaubt(ko.ALPHAVANTAGE)) null else {
                        news.kontingent.buchen(ko.ALPHAVANTAGE)
                        de.troetenrepublik.app.market.ohneAbbruch { stimmungNetz.anfrage(qz.alphaAdresse(k), mapOf("Accept" to "text/csv")) }.getOrNull()
                            ?.takeIf { it.ok }?.text?.let(qz::alphaLesen)?.also { zahlenAlpha = jetzt to it }
                    }
                } ?: alt?.second
                if (l != null) return@withLock verteilen(l, heute)
            }
            leer
        }

    // Nasdaq und Schluessel-Anbieter je Tag mischen; ohne beide null je Tag
    suspend fun quartalszahlen(tage: List<java.time.LocalDate>, neu: Boolean = false): Map<java.time.LocalDate, List<de.troetenrepublik.app.market.Quartalszahlen.Termin>?> {
        if (tage.isEmpty()) return emptyMap()
        val nasdaq = de.troetenrepublik.app.market.ohneAbbruch { nasdaqZahlen(tage) }.getOrNull()
        val schluessel = de.troetenrepublik.app.market.ohneAbbruch { quartalszahlenSchluessel(tage, neu) }.getOrNull()
        return tage.associateWith { tag ->
            de.troetenrepublik.app.market.NasdaqKalender.zahlenMischen(nasdaq?.get(tag), schluessel?.get(tag))
        }
    }

    // Termine der Aktien aus Depot und Merkliste, die der Ergebniskalender nicht fuehrt (Werte ausserhalb der USA,
    // ohne Schluessel alle), aus dem Termin im Kursabruf; nur Tage aus tage. Datum und Zeitpunkt nach der Uhr des Heimatplatzes samt
    // Sommerzeit am Tag des Termins; bei unbekannter Boerse nach dem heutigen Versatz und ohne Zeitpunkt. Nennt Yahoo den
    // Tag nur als Schaetzung, bleibt die Uhrzeit offen; sonst wird der genaue Zeitpunkt gemerkt (ausser Mitternacht).
    suspend fun eigeneZahlentermine(tage: List<java.time.LocalDate>, bekannt: Set<String>): List<de.troetenrepublik.app.market.Quartalszahlen.Termin> {
        val konto = account.value
        val namen = konto.positions.filter { !it.isDerivative }.associate { it.symbol to it.name }
        val symbole = (namen.keys + konto.watchlist).distinct()
            .filter { it !in bekannt && Symbols.typeOf(it) == de.troetenrepublik.app.data.AssetType.STOCK }
        if (symbole.isEmpty() || tage.isEmpty()) return emptyList()
        val werte = kennzahlen(symbole, listOf("earningsTimestamp", "earningsTimestampStart", "gmtOffSetMilliseconds", "isEarningsDateEstimate"))
        val erlaubt = tage.map { it.toEpochDay() }.toSet()
        return werte.mapNotNull { (s, k) ->
            val sek = listOfNotNull(k["earningsTimestamp"], k["earningsTimestampStart"]).filter { it > 0 }.minOrNull() ?: return@mapNotNull null
            val zeitpunkt = java.time.Instant.ofEpochMilli((sek * 1000).toLong())
            val platz = de.troetenrepublik.app.market.Quartalszahlen.platz(s)
            val ort = if (platz != null) zeitpunkt.atZone(platz.zone).toLocalDateTime()
            else zeitpunkt.plusMillis((k["gmtOffSetMilliseconds"] ?: 0.0).toLong()).atZone(java.time.ZoneOffset.UTC).toLocalDateTime()
            val tag = ort.toLocalDate().toEpochDay()
            if (tag !in erlaubt) return@mapNotNull null
            val geschaetzt = k["isEarningsDateEstimate"] == 1.0
            val zeit = if (geschaetzt) de.troetenrepublik.app.market.Quartalszahlen.Zeit.OFFEN
            else platz?.let { de.troetenrepublik.app.market.Quartalszahlen.zeitAus(ort.toLocalTime(), it.oeffnet, it.schliesst) }
                ?: de.troetenrepublik.app.market.Quartalszahlen.Zeit.OFFEN
            de.troetenrepublik.app.market.Quartalszahlen.Termin(
                symbol = s,
                name = Symbols.find(s)?.name ?: namen[s] ?: s,
                tag = tag,
                zeit = zeit,
                eigen = true,
                zeitpunkt = zeitpunkt.toEpochMilli().takeIf { platz != null && zeit != de.troetenrepublik.app.market.Quartalszahlen.Zeit.OFFEN },
                geschaetzt = geschaetzt,
            )
        }
    }

    // Prospekte einer Firma bei der SEC (Liste der Einreichungen); null ohne Kontaktadresse unter Zugaenge.
    suspend fun ipoDokumente(cik: String): List<de.troetenrepublik.app.market.IpoKalender.Dokument>? {
        val kennung = de.troetenrepublik.app.market.SecInsider.kennung(settingsFlow.value.secKontakt) ?: return null
        val a = runCatching {
            secNetz.anfrage(de.troetenrepublik.app.market.IpoKalender.submissions(cik), mapOf("User-Agent" to kennung, "Accept" to "application/json"))
        }.getOrNull()
        return a?.takeIf { it.ok }?.text?.let { de.troetenrepublik.app.market.IpoKalender.dokumenteLesen(cik, it) }.orEmpty()
    }

    // CIK eines Angebots ohne Kuerzel ueber die Firmensuche der SEC nach dem Namen; nur bei genau einem Treffer mit
    // passendem Namen. Je Name einen Tag im Speicher, auch der Fehlschlag.
    private val ipoCikJeName = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, String>>()

    suspend fun ipoCikNachName(name: String, gleich: (String) -> Boolean): String? {
        val kennung = de.troetenrepublik.app.market.SecInsider.kennung(settingsFlow.value.secKontakt) ?: return null
        val jetzt = System.currentTimeMillis()
        ipoCikJeName[name]?.takeIf { jetzt - it.first < 24 * 3_600_000L }?.let { return it.second.ifEmpty { null } }
        delay(300)
        val a = runCatching {
            secNetz.anfrage(de.troetenrepublik.app.market.IpoKalender.firmenSuche(name), mapOf("User-Agent" to kennung, "Accept" to "application/atom+xml, */*"), max = 400_000)
        }.getOrNull()?.takeIf { it.ok }?.text ?: return null
        val cik = de.troetenrepublik.app.market.IpoKalender.cikAusSuche(a, gleich)
        ipoCikJeName[name] = jetzt to cik.orEmpty()
        return cik
    }

    // Auszug aus dem Prospekt (Anteilseigner, Banken, Sperrfrist): ein Abruf des Dokuments, je Dokument einen Tag im
    // Speicher. null ohne Kontaktadresse, ohne Prospekt oder ohne Antwort.
    private val ipoProspekte = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, de.troetenrepublik.app.engine.IpoProspekt.Auszug>>()
    private val ipoProspektLock = kotlinx.coroutines.sync.Mutex()

    suspend fun ipoProspekt(doks: List<de.troetenrepublik.app.market.IpoKalender.Dokument>): de.troetenrepublik.app.engine.IpoProspekt.Auszug? = ipoProspektLock.withLock {
        val kennung = de.troetenrepublik.app.market.SecInsider.kennung(settingsFlow.value.secKontakt) ?: return@withLock null
        val dok = de.troetenrepublik.app.engine.IpoProspekt.wahl(doks) ?: return@withLock null
        val jetzt = System.currentTimeMillis()
        ipoProspekte[dok.link]?.takeIf { jetzt - it.first < 24 * 3_600_000L }?.let { return@withLock it.second }
        delay(300)
        // Gestreamt gelesen: Prospekte sind bis ueber 15 MB gross und die gesuchten Abschnitte liegen weit hinten.
        // Im Speicher bleiben nur die Fundstellen.
        val html = runCatching {
            secNetz.strom(dok.link, mapOf("User-Agent" to kennung, "Accept" to "text/html, */*")) { de.troetenrepublik.app.engine.IpoProspekt.verdichten(it) }
        }.getOrNull() ?: return@withLock null
        val aus = withContext(Dispatchers.Default) { de.troetenrepublik.app.engine.IpoProspekt.lesen(html) }
        ipoProspekte[dok.link] = jetzt to aus
        aus
    }

    // Kurs anderer Boersengaenge nach dem Start: Kalendermonate der Nasdaq zwei Jahre zurueck (eigener Abrufer, damit
    // ein Fehlschlag den Kalender nicht pausiert) und Tageskurse je Kuerzel. Je Art (Mantelgesellschaft oder nicht)
    // zwoelf Stunden im Speicher; leer ohne Daten.
    private val nasdaqRueckblickAbruf by lazy { de.troetenrepublik.app.market.NasdaqAbruf({ stimmungNetz.anfrage(it, de.troetenrepublik.app.market.NasdaqKalender.KOPF) }, sperre = de.troetenrepublik.app.market.AbrufAblage) }
    private val ipoVerlaeufe = java.util.concurrent.ConcurrentHashMap<Boolean, Pair<Long, List<de.troetenrepublik.app.engine.IpoBlatt.Frist>>>()
    private val ipoVerlaufLock = kotlinx.coroutines.sync.Mutex()

    // Kalender der Boersengaenge mit den Monaten vom dritten Vormonat zurueck bis monate vor heute (fuer die Liste der
    // Sperrfristen, IpoSperrfristAbruf.kt); die aelteren Monate liegen 30 Tage im Speicher.
    suspend fun ipoKalenderZurueck(monate: Int): List<de.troetenrepublik.app.market.IpoKalender.Angebot> {
        val heute = java.time.LocalDate.now(java.time.ZoneId.of("America/New_York"))
        val neu = de.troetenrepublik.app.market.ohneAbbruch { nasdaqIpos() }.getOrNull().orEmpty()
        val alt = de.troetenrepublik.app.engine.IpoUebersicht.rueckblickMonate(heute, monate).mapNotNull { monat ->
            nasdaqRueckblickAbruf.holen(
                de.troetenrepublik.app.market.NasdaqKalender.ipoAdresse(monat),
                { _ -> 30 * 24 * 3_600_000L },
                de.troetenrepublik.app.market.NasdaqKalender::ipoLesen,
            )
        }.flatten()
        return de.troetenrepublik.app.market.IpoKalender.zusammen(neu + alt)
    }

    suspend fun ipoVerlauf(spac: Boolean): List<de.troetenrepublik.app.engine.IpoBlatt.Frist> = ipoVerlaufLock.withLock {
        val jetzt = System.currentTimeMillis()
        ipoVerlaeufe[spac]?.takeIf { jetzt - it.first < 12 * 3_600_000L }?.let { return@withLock it.second }
        val heute = java.time.LocalDate.now(java.time.ZoneId.of("America/New_York"))
        val heuteTag = heute.toEpochDay()
        val neu = de.troetenrepublik.app.market.ohneAbbruch { nasdaqIpos() }.getOrNull().orEmpty()
        val alt = de.troetenrepublik.app.engine.IpoUebersicht.rueckblickMonate(heute).mapNotNull { monat ->
            nasdaqRueckblickAbruf.holen(
                de.troetenrepublik.app.market.NasdaqKalender.ipoAdresse(monat),
                { _ -> 30 * 24 * 3_600_000L },
                de.troetenrepublik.app.market.NasdaqKalender::ipoLesen,
            )
        }.flatten()
        val gestartet = de.troetenrepublik.app.market.IpoKalender.zusammen(neu + alt).filter {
            it.status == de.troetenrepublik.app.market.IpoKalender.Status.PREIS && it.spac == spac && it.symbol != null && it.tag != null &&
                it.tag <= heuteTag - de.troetenrepublik.app.engine.IpoBlatt.FRISTEN.first() && (it.preisZahl ?: 0.0) > 0
        }.distinctBy { it.symbol }
        if (gestartet.size < de.troetenrepublik.app.engine.IpoBlatt.MIN_VERLAUF) return@withLock emptyList()
        val kerzen = HashMap<String, List<Candle>>()
        for (teil in gestartet.mapNotNull { it.symbol }.chunked(20)) {
            delay(150)
            kerzen += schlusskurse(teil, "2y")
        }
        val aus = withContext(Dispatchers.Default) {
            de.troetenrepublik.app.engine.IpoBlatt.verlauf(
                gestartet.map { de.troetenrepublik.app.engine.IpoBlatt.Gestartet(it.tag!!, it.preisZahl!!, kerzen[it.symbol].orEmpty()) }, heuteTag,
            )
        }
        if (aus.isNotEmpty()) ipoVerlaeufe[spac] = jetzt to aus
        aus
    }

    // Pressemitteilung zu den Zahlen (0.78): letztes 8-K mit Item 2.02 samt Vorquartal, je Aktie 12 Stunden im Speicher.
    // status: OHNE_KONTAKT (keine Kontaktadresse unter Zugaenge), KEIN_SEC, KEINE (keine Mitteilung gefunden), OK.
    class ZahlenMitteilung(
        val status: Status, val tag: String = "", val link: String = "",
        val jetzt: de.troetenrepublik.app.engine.Zahlenmitteilung.Mitteilung? = null,
        val vorher: de.troetenrepublik.app.engine.Zahlenmitteilung.Mitteilung? = null,
    ) { enum class Status { OHNE_KONTAKT, KEIN_SEC, KEINE, OK } }

    private val zahlenMitteilungen = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, ZahlenMitteilung>>()
    private val zahlenMitteilungLock = kotlinx.coroutines.sync.Mutex()

    suspend fun zahlenMitteilung(symbol: String): ZahlenMitteilung = zahlenMitteilungLock.withLock {
        val kennung = de.troetenrepublik.app.market.SecInsider.kennung(settingsFlow.value.secKontakt)
            ?: return@withLock ZahlenMitteilung(ZahlenMitteilung.Status.OHNE_KONTAKT)
        val jetzt = System.currentTimeMillis()
        zahlenMitteilungen[symbol]?.takeIf { jetzt - it.first < 12 * 3_600_000L }?.let { return@withLock it.second }
        val kopf = mapOf("User-Agent" to kennung, "Accept" to "*/*")
        suspend fun text(url: String, max: Int): String? {
            delay(300)
            return runCatching { secNetz.anfrage(url, kopf, max = max).takeIf { it.ok }?.text }.getOrNull()
        }
        val cik = ipoCik(symbol) ?: return@withLock ZahlenMitteilung(ZahlenMitteilung.Status.KEIN_SEC)
        val subs = text(de.troetenrepublik.app.market.IpoKalender.submissions(cik), 4_000_000)
            ?: return@withLock ZahlenMitteilung(ZahlenMitteilung.Status.KEINE)
        val liste = de.troetenrepublik.app.engine.Zahlenmitteilung.einreichungen(subs)
        suspend fun lesen(e: de.troetenrepublik.app.engine.Zahlenmitteilung.Einreichung): Pair<String, de.troetenrepublik.app.engine.Zahlenmitteilung.Mitteilung>? {
            val index = text(de.troetenrepublik.app.market.SecInsider.verzeichnisAdresse(cik, e.acc), 1_000_000) ?: return null
            val datei = de.troetenrepublik.app.engine.Zahlenmitteilung.exhibitDatei(index) ?: return null
            val adresse = de.troetenrepublik.app.market.SecInsider.archivAdresse(cik, e.acc, datei)
            val html = text(adresse, 3_000_000) ?: return null
            return adresse to de.troetenrepublik.app.engine.Zahlenmitteilung.lesen(html)
        }
        val neu = liste.firstOrNull()?.let { e -> lesen(e)?.let { e to it } }
            ?: return@withLock ZahlenMitteilung(ZahlenMitteilung.Status.KEINE)
        val vorher = liste.getOrNull(1)?.let { lesen(it)?.second }
        val aus = ZahlenMitteilung(ZahlenMitteilung.Status.OK, neu.first.tag, neu.second.first, neu.second.second, vorher)
        zahlenMitteilungen[symbol] = jetzt to aus
        aus
    }

    // Rechnen (Vereinen, Filtern, Lesen der Antworten) ausserhalb des Hauptthreads, damit die Seite beim Laden nicht ruckelt (0.68)
    suspend fun secLaden(neu: Boolean = false, symbol: String? = null, netz: Boolean = true): de.troetenrepublik.app.market.SecInsider.Datei = withContext(Dispatchers.Default) { secLock.withLock {
        val d = withContext(Dispatchers.IO) { secStand() }
        if (!netz) return@withLock d
        val kennung = de.troetenrepublik.app.market.SecInsider.kennung(settingsFlow.value.secKontakt)
            ?: return@withLock if (symbol != null) nasdaqInsiderErgaenzen(d, symbol) else d
        val jetzt = System.currentTimeMillis()
        val allgemein = neu || jetzt - d.abgerufen > 6 * 3_600_000L
        if (!allgemein && symbol == null) return@withLock d
        val abruf = runCatching { secAbrufen(d, kennung, allgemein, symbol) }.getOrNull()
        // Tagesabruf ohne einen lesbaren Tagesindex gilt als nicht gelesen
        if (allgemein) de.troetenrepublik.app.market.Quellen.inhaltGelesen("sec", abruf?.abdeckung != null)
        // Antwortet die SEC nicht lesbar, springt fuer den geoeffneten Wert der Rueckfall der Nasdaq ein
        if (abruf == null || (symbol != null && !abruf.symbolGelesen && abruf.abdeckung == null)) return@withLock if (symbol != null) nasdaqInsiderErgaenzen(d, symbol) else d
        val frisch = abruf.aus
        val abdeckung = abruf.abdeckung
        val heute = de.troetenrepublik.app.engine.Uhr.heute().toString()
        // Stand des geoeffneten Werts: Tag, an dem die SEC-Liste seiner Meldungen gelesen wurde
        val standJe = if (symbol != null && abruf.symbolGelesen) d.standJe + (symbol.trim().uppercase() to de.troetenrepublik.app.market.SecInsider.Nq(heute)) else d.standJe
        if (frisch.isEmpty() && !allgemein && standJe == d.standJe) return@withLock d
        val liste = de.troetenrepublik.app.market.SecInsider.vereinen(d.geschaefte, frisch, secAbTag())
        // Stand und Luecke nur nach einem Tagesabruf: abdeckung ist der aelteste, bis der juengste Tag, den er erfasst hat
        val (neuStand, luecke) = if (allgemein && abdeckung != null) de.troetenrepublik.app.engine.Luecke.nachAbruf(d.stand, d.luecke(), abdeckung, abruf.bis ?: heute) else d.stand to d.luecke()
        val n = d.copy(
            geschaefte = liste, stand = if (allgemein && abdeckung != null) maxOf(d.stand, neuStand) else d.stand,
            abgerufen = if (allgemein) jetzt else d.abgerufen, lueckeVon = luecke?.von.orEmpty(), lueckeBis = luecke?.bis.orEmpty(), standJe = standJe,
        )
        _sec.value = n
        withContext(Dispatchers.IO) {
            runCatching { secDatei.schreiben(de.troetenrepublik.app.market.SecInsider.schreiben(de.troetenrepublik.app.market.SecInsider.Datei(stand = n.stand, geschaefte = liste, abgerufen = n.abgerufen, lueckeVon = n.lueckeVon, lueckeBis = n.lueckeBis, standJe = n.standJe)).toByteArray(Charsets.UTF_8)) }
        }
        n
    }
    }

    // Eigener Abrufer fuer die Insider-Meldungen der Nasdaq, damit ein Fehlschlag die Kalender nicht pausiert
    private val nasdaqInsiderAbruf by lazy { de.troetenrepublik.app.market.NasdaqAbruf({ stimmungNetz.anfrage(it, de.troetenrepublik.app.market.NasdaqKalender.KOPF) }, sperre = de.troetenrepublik.app.market.AbrufAblage) }

    // Schluesselfreier Rueckfall (0.79) fuer einen US-Wert, wenn die SEC nicht abgerufen wird (keine Kontaktadresse) oder nichts
    // lieferte: Kaeufe und Verkaeufe der Nasdaq ab dem Stand der vorhandenen Meldungen, vereint mit diesen; die Seite zeigt den
    // neuen Stand je Kuerzel. Ohne Antwort bleibt alles, wie es war. Aufruf nur unter secLock.
    private suspend fun nasdaqInsiderErgaenzen(d: de.troetenrepublik.app.market.SecInsider.Datei, symbol: String): de.troetenrepublik.app.market.SecInsider.Datei {
        val ticker = symbol.trim().uppercase()
        if (!de.troetenrepublik.app.market.NasdaqInsider.abrufbar(ticker)) return d
        val antwort = runCatching {
            nasdaqInsiderAbruf.holen(
                de.troetenrepublik.app.market.NasdaqInsider.adresse(ticker),
                { _ -> de.troetenrepublik.app.market.NasdaqInsider.HALTBAR_MS },
                de.troetenrepublik.app.market.NasdaqInsider::lesen,
            )
        }.getOrNull() ?: return d
        val heute = de.troetenrepublik.app.engine.Uhr.heute().toString()
        val vorhanden = d.geschaefte.firstOrNull { it.k.equals(ticker, true) }
        val ab = de.troetenrepublik.app.market.NasdaqInsider.abTag(d.stand, d.luecke())
        val erg = de.troetenrepublik.app.market.NasdaqInsider.auswerten(antwort, ticker, vorhanden?.e.orEmpty(), vorhanden?.c.orEmpty(), ab)
        val liste = de.troetenrepublik.app.market.SecInsider.vereinen(d.geschaefte, erg.geschaefte, secAbTag())
        val stand = de.troetenrepublik.app.market.SecInsider.Nq(heute, erg.lueckeVon, erg.lueckeBis)
        if (liste == d.geschaefte && d.standJe[ticker] == stand) return d
        val n = d.copy(geschaefte = liste, standJe = d.standJe + (ticker to stand))
        _sec.value = n
        withContext(Dispatchers.IO) {
            runCatching { secDatei.schreiben(de.troetenrepublik.app.market.SecInsider.schreiben(de.troetenrepublik.app.market.SecInsider.Datei(stand = n.stand, geschaefte = liste, abgerufen = n.abgerufen, lueckeVon = n.lueckeVon, lueckeBis = n.lueckeBis, standJe = n.standJe)).toByteArray(Charsets.UTF_8)) }
        }
        return n
    }

    // Ergebnis eines Abrufs: Meldungen, aeltester und juengster erfasster Tag des Tagesabrufs (null ohne Tagesabruf) und ob die
    // Liste des Emittenten gelesen wurde.
    private class SecAbruf(
        val aus: List<de.troetenrepublik.app.market.SecInsider.Geschaeft>, val abdeckung: String?, val bis: String?, val symbolGelesen: Boolean,
    )

    // Neueste Meldungen (Tagesverzeichnisse) und, mit symbol, die Meldungen dieses Werts aus seiner Liste bei data.sec.gov.
    // Nur unbekannte Meldungen, nacheinander mit Pause (die SEC erlaubt zehn Abrufe je Sekunde). Tagesverzeichnisse von alt nach neu,
    // nur Emittenten, die die App braucht (SecInsider.gesuchteCiks), hoechstens ABRUF_BUDGET Meldungen je Abruf: der Stand rueckt
    // Tag fuer Tag vor, ein Verzeichnis mit 1.700 Zeilen blockiert ihn nicht mehr.
    private suspend fun secAbrufen(d: de.troetenrepublik.app.market.SecInsider.Datei, kennung: String, allgemein: Boolean, symbol: String?): SecAbruf {
        val kopf = mapOf("User-Agent" to kennung, "Accept" to "*/*")
        suspend fun text(url: String): String? { delay(150); return secNetz.anfrage(url, kopf).takeIf { it.ok }?.text }
        val bekannt = d.geschaefte.mapTo(HashSet()) { it.a }
        val offen = LinkedHashMap<String, String>()
        var abdeckung: String? = null
        var bis: String? = null
        var symbolGelesen = false
        if (allgemein) {
            // Tagesverzeichnisse der letzten Handelstage: drei, bei altem Stand bis zu 40 (seit dem Stand). Fehlt der neueste
            // (404, Tag noch offen), weiter; fehlt ein aelterer, endet die Abdeckung davor.
            val heute = java.time.LocalDate.now(java.time.ZoneId.of("America/New_York"))
            val seit = runCatching { (heute.toEpochDay() - java.time.LocalDate.parse(d.stand).toEpochDay()).toInt() }.getOrDefault(0)
            val tage = de.troetenrepublik.app.market.SecInsider.letzteHandelstage(heute, (seit * 5 / 7 + 3).coerceIn(3, 40)).reversed()
            val gesucht = de.troetenrepublik.app.market.SecInsider.gesuchteCiks(d.geschaefte, symbol, de.troetenrepublik.app.engine.Uhr.heute().toString())
            val lauf = de.troetenrepublik.app.market.SecInsider.Vorlauf()
            for ((i, tag) in tage.withIndex()) {
                val idx = text(de.troetenrepublik.app.market.SecInsider.tagesIndex(tag))
                if (idx == null) { if (i == tage.lastIndex) continue else break }
                if (!lauf.tag(tag.toString(), de.troetenrepublik.app.market.SecInsider.tagesIndexLesen(idx), gesucht, bekannt)) break
            }
            offen.putAll(lauf.abrufen)
            abdeckung = lauf.ab
            bis = lauf.bis
        }
        if (symbol != null) {
            val cik = d.geschaefte.firstOrNull { it.k.equals(symbol, true) }?.c?.takeIf { it.isNotBlank() } ?: ipoCik(symbol)
            if (!cik.isNullOrBlank()) {
                text("https://data.sec.gov/submissions/CIK" + cik.padStart(10, '0') + ".json")?.let { j ->
                    val o = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(j).jsonObject }.getOrNull()
                    val r = o?.get("filings")?.jsonObject?.get("recent")?.jsonObject
                    val formen = r?.get("form")?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
                    val nummern = r?.get("accessionNumber")?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
                    if (r != null) symbolGelesen = true
                    formen.indices.filter { formen[it] == "4" }.take(40).forEach { i -> nummern.getOrNull(i)?.let { if (it !in bekannt) offen[it] = cik } }
                }
            }
        }
        val aus = ArrayList<de.troetenrepublik.app.market.SecInsider.Geschaeft>()
        for ((a, cik) in offen.entries.take(de.troetenrepublik.app.market.SecInsider.ABRUF_BUDGET + 40)) {
            val index = text(de.troetenrepublik.app.market.SecInsider.verzeichnisAdresse(cik, a)) ?: continue
            val datei = de.troetenrepublik.app.market.SecInsider.xmlDatei(index) ?: continue
            val xml = text(de.troetenrepublik.app.market.SecInsider.archivAdresse(cik, a, datei)) ?: continue
            aus += de.troetenrepublik.app.market.SecInsider.form4(xml, a)
        }
        return SecAbruf(aus, abdeckung, bis, symbolGelesen)
    }

    // ------------------------------------------------------------ SEC: Form 144 und Schedule 13D/13G je Aktie (seit 5.91)

    private val secFormenDatei by lazy { de.troetenrepublik.app.plattform.SichereDatei(java.io.File(store.ordner, "sec_formen.json")) }
    private val secFormenLock = kotlinx.coroutines.sync.Mutex()
    private val _secFormen = MutableStateFlow<Map<String, de.troetenrepublik.app.market.SecFormen.Stand>>(emptyMap())
    @Volatile private var secFormenGeladen = false
    @Volatile private var secKuerzel: String? = null

    // Je Symbol angekuendigte Verkaeufe und Grossaktionaere, auf dem Geraet gespeichert.
    val secFormen: StateFlow<Map<String, de.troetenrepublik.app.market.SecFormen.Stand>> = _secFormen.asStateFlow()

    // Mit Kontaktadresse unter Zugaenge hoechstens alle zwoelf Stunden je Aktie: Liste der Meldungen des Emittenten,
    // dann nur ungelesene Form 144 (120 Tage, hoechstens 15) und Schedule 13D/13G (drei Jahre, hoechstens 20).
    // Rechnen (Vereinen, Filtern, Lesen der Antworten) ausserhalb des Hauptthreads, damit die Seite beim Laden nicht ruckelt (0.68)
    suspend fun secFormenLaden(symbol: String) = withContext(Dispatchers.Default) { secFormenLock.withLock {
        if (!secFormenGeladen) {
            secFormenGeladen = true
            withContext(Dispatchers.IO) { runCatching { de.troetenrepublik.app.market.SecFormen.lesen(secFormenDatei.lesen().toString(Charsets.UTF_8)) }.getOrNull() }
                ?.let { _secFormen.value = it.werte + _secFormen.value }
        }
        val kennung = de.troetenrepublik.app.market.SecInsider.kennung(settingsFlow.value.secKontakt) ?: return@withLock
        val alt = _secFormen.value[symbol] ?: de.troetenrepublik.app.market.SecFormen.Stand()
        val jetzt = System.currentTimeMillis()
        if (jetzt - alt.abgerufen < 12 * 3_600_000L) return@withLock
        val kopf = mapOf("User-Agent" to kennung, "Accept" to "*/*")
        suspend fun text(url: String): String? { delay(150); return runCatching { secNetz.anfrage(url, kopf).takeIf { it.ok }?.text }.getOrNull() }
        val cik = withContext(Dispatchers.IO) { secStand() }.geschaefte.firstOrNull { it.k.equals(symbol, true) }?.c?.ifBlank { null }
            ?: (secKuerzel ?: text(de.troetenrepublik.app.market.SecFormen.KUERZEL)?.also { secKuerzel = it })?.let { de.troetenrepublik.app.market.SecFormen.cikAusKuerzeln(it, symbol) }
            ?: return@withLock
        val liste = text(de.troetenrepublik.app.market.SecFormen.submissionsAdresse(cik))?.let { de.troetenrepublik.app.market.SecFormen.eintraege(it) } ?: return@withLock
        val heute = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
        val bekannt = alt.gelesen.toHashSet()
        val f144 = liste.filter { it.form.startsWith("144") && it.eingang >= heute.minusDays(120).toString() && it.accession !in bekannt }.take(15)
        val f13 = liste.filter { it.form.startsWith("SCHEDULE 13") && it.eingang >= heute.minusDays(1100).toString() && it.accession !in bekannt }.take(20)
        val verkaeufe = ArrayList<de.troetenrepublik.app.market.SecFormen.Verkauf>()
        val halter = ArrayList<de.troetenrepublik.app.market.SecFormen.Halter>()
        val gelesen = ArrayList<String>()
        for (e in f144 + f13) {
            val xml = text(de.troetenrepublik.app.market.SecInsider.archivAdresse(cik, e.accession, e.datei)) ?: continue
            gelesen += e.accession
            withContext(Dispatchers.Default) {
                if (e.form.startsWith("144")) verkaeufe += de.troetenrepublik.app.market.SecFormen.form144(xml, e.accession, e.eingang)
                else halter += de.troetenrepublik.app.market.SecFormen.schedule13(xml, e.form, e.accession, e.eingang)
            }
        }
        val grenze = heute.minusDays(365).toString()
        val n = de.troetenrepublik.app.market.SecFormen.Stand(
            abgerufen = jetzt,
            verkaeufe = (alt.verkaeufe + verkaeufe).filter { it.gemeldet >= grenze || it.tag >= grenze },
            halter = alt.halter + halter,
            gelesen = (alt.gelesen + gelesen).distinct().takeLast(600),
        )
        _secFormen.value = _secFormen.value + (symbol to n)
        withContext(Dispatchers.IO) {
            runCatching { secFormenDatei.schreiben(de.troetenrepublik.app.market.SecFormen.schreiben(de.troetenrepublik.app.market.SecFormen.Datei(_secFormen.value)).toByteArray(Charsets.UTF_8)) }
        }
    }
    }

    // ------------------------------------------------------------ Was Investoren schreiben (5.92, market/Briefabrufe.kt)

    private val briefNetz by lazy { de.troetenrepublik.app.market.Briefabrufe(MarketApi(), store.ordner) }

    // Briefe der Absender; faellige werden nachgeholt, sonst der Stand auf dem Geraet.
    suspend fun investorenbriefe(netz: Boolean = true): List<de.troetenrepublik.app.engine.Investorenbriefe.Brief> =
        runCatching { if (netz) briefNetz.briefe() else briefNetz.gespeichert() }.getOrDefault(emptyList())

    // Fundstellen in Fondsberichten bei der SEC; nur mit Kontaktadresse unter Zugaenge, sonst nichts.
    suspend fun investorenbriefeSec(symbol: String, name: String): List<de.troetenrepublik.app.engine.Investorenbriefe.Fundstelle> {
        val kennung = de.troetenrepublik.app.market.SecInsider.kennung(settingsFlow.value.secKontakt) ?: return emptyList()
        return runCatching { briefNetz.sec(symbol, name, kennung) }.getOrDefault(emptyList())
    }

    // Segmente, Regionen und Grosskunden aus dem juengsten Jahresbericht bei der SEC sowie die Konzernmutter aus GLEIF
    // (0.74, market/Firmenberichte.kt); je Wert auf dem Geraet gespeichert, Abruf erst beim Oeffnen der Karte.
    val firmenberichte: de.troetenrepublik.app.market.Firmenberichte by lazy { neueFirmenberichte() }

    // ------------------------------------------------------------ Regierung: weitere Mitglieder ueber OGE und Weisses Haus (seit 5.91)

    private val ogeDatei by lazy { de.troetenrepublik.app.plattform.SichereDatei(java.io.File(store.ordner, "regierung_oge.json")) }
    private val ogeLock = kotlinx.coroutines.sync.Mutex()
    private val ogeNetz by lazy { MarketApi() }
    private val _ogeRegierung = MutableStateFlow<de.troetenrepublik.app.market.RegierungOge.Datei?>(null)

    // Personen mit verlinkten 278-T und ihre gelesenen Geschaefte; nur auf dem Geraet, fuer den eigenen Gebrauch.
    val ogeRegierung: StateFlow<de.troetenrepublik.app.market.RegierungOge.Datei?> = _ogeRegierung.asStateFlow()

    private fun ogeStand(): de.troetenrepublik.app.market.RegierungOge.Datei {
        _ogeRegierung.value?.let { return it }
        val d = runCatching { de.troetenrepublik.app.market.RegierungOge.lesen(ogeDatei.lesen().toString(Charsets.UTF_8)) }.getOrNull()
            ?: de.troetenrepublik.app.market.RegierungOge.Datei()
        _ogeRegierung.value = d
        return d
    }

    // Liste hoechstens einmal am Tag neu (Seiten der OGE zu je 100, die Seite des Weissen Hauses), danach ungelesene
    // PDFs nacheinander mit Pause: mit id alle dieser Person, sonst die 25 neuesten. Ohne netz nur der Stand. Seit 5.92
    // auch die Vermoegensmeldungen (278e) der Personen der Liste.
    suspend fun ogeLaden(id: String? = null, netz: Boolean = true): de.troetenrepublik.app.market.RegierungOge.Datei = ogeLock.withLock {
        var d = withContext(Dispatchers.IO) { ogeStand() }
        if (!netz) return@withLock d
        val jetzt = System.currentTimeMillis()
        var geaendert = false
        var whSeite: String? = null
        if (jetzt - d.abgerufen > 24 * 3_600_000L) {
            val eintraege = ArrayList<de.troetenrepublik.app.market.RegierungOge.Eintrag>()
            var erreicht = false
            var start = 0
            while (start < 1000) {
                val text = runCatching { ogeNetz.anfrage(de.troetenrepublik.app.market.RegierungOge.ogeListe(start)).takeIf { it.ok }?.text }.getOrNull() ?: break
                val (gesamt, liste) = de.troetenrepublik.app.market.RegierungOge.ogeListeLesen(text)
                erreicht = true
                eintraege += liste
                start += 100
                if (start >= gesamt) break
                delay(300)
            }
            whSeite = runCatching { ogeNetz.anfrage(de.troetenrepublik.app.market.RegierungOge.WEISSES_HAUS, mapOf("Accept" to "text/html")).takeIf { it.ok }?.text }.getOrNull()
            whSeite?.let { erreicht = true; eintraege += de.troetenrepublik.app.market.RegierungOge.weissesHausLesen(it) }
            if (erreicht) { d = de.troetenrepublik.app.market.RegierungOge.einarbeiten(d, eintraege, jetzt); geaendert = true }
        }
        // Vermoegensmeldungen (seit 5.92): verlinkte 278e der OGE und des Weissen Hauses, ebenfalls hoechstens einmal am Tag
        if (d.personen.isNotEmpty() && jetzt - d.vAbgerufen > 24 * 3_600_000L) {
            val vListe = ArrayList<de.troetenrepublik.app.market.RegierungOge.VEintrag>()
            var erreicht = false
            for (begriff in de.troetenrepublik.app.market.RegierungOge.ogeVermoegenSuchen(java.time.LocalDate.now().year)) {
                var start = 0
                while (start < 500) {
                    delay(300)
                    val text = runCatching { ogeNetz.anfrage(de.troetenrepublik.app.market.RegierungOge.ogeSuche(begriff, start)).takeIf { it.ok }?.text }.getOrNull() ?: break
                    val (gesamt, liste) = de.troetenrepublik.app.market.RegierungOge.ogeVermoegenLesen(text)
                    erreicht = true
                    vListe += liste
                    start += 100
                    if (start >= gesamt) break
                }
            }
            (whSeite ?: runCatching { ogeNetz.anfrage(de.troetenrepublik.app.market.RegierungOge.WEISSES_HAUS, mapOf("Accept" to "text/html")).takeIf { it.ok }?.text }.getOrNull())
                ?.let { erreicht = true; vListe += de.troetenrepublik.app.market.RegierungOge.weissesHausVermoegen(it) }
            if (erreicht) {
                d = de.troetenrepublik.app.market.RegierungOge.vermoegenEinarbeiten(d, vListe).copy(vAbgerufen = jetzt)
                geaendert = true
            }
        }
        val offen = d.personen.filter { id == null || it.id == id }.flatMap { p -> p.meldungen.filter { !it.gelesen }.map { p.id to it } }
            .sortedByDescending { it.second.tag }.take(if (id != null) 40 else 25)
        for ((pid, m) in offen) {
            delay(250)
            val roh = runCatching { ogeNetz.datei(m.pdf, 8_000_000) }.getOrNull() ?: continue
            val b = withContext(Dispatchers.Default) { de.troetenrepublik.app.market.RegierungOge.berichtLesen(de.troetenrepublik.app.market.PdfText.zeilen(roh)) }
            d = de.troetenrepublik.app.market.RegierungOge.eintragen(d, pid, m.pdf, b)
            _ogeRegierung.value = d
            geaendert = true
        }
        // Je Person die neueste Vermoegensmeldung lesen (ist sie leer oder gescannt, die naechstaeltere); in der Liste
        // hoechstens 12 je Aufruf in der Reihenfolge der Liste, auf der Seite einer Person bis zu drei
        repeat(if (id != null) 3 else 1) {
            val vOffen = d.personen.filter { id == null || it.id == id }
                .mapNotNull { p -> de.troetenrepublik.app.market.RegierungOge.vermoegenOffen(p)?.let { p.id to it } }
                .take(if (id != null) 1 else 12)
            for ((pid, m) in vOffen) {
                delay(250)
                val roh = runCatching { ogeNetz.datei(m.pdf, 16_000_000) }.getOrNull() ?: continue
                val b = withContext(Dispatchers.Default) { de.troetenrepublik.app.market.RegierungOge.vermoegenLesen(de.troetenrepublik.app.market.PdfText.zeilen(roh)) }
                d = de.troetenrepublik.app.market.RegierungOge.vermoegenEintragen(d, pid, m.pdf, b)
                _ogeRegierung.value = d
                geaendert = true
            }
        }
        _ogeRegierung.value = d
        if (geaendert) withContext(Dispatchers.IO) {
            runCatching { ogeDatei.schreiben(de.troetenrepublik.app.market.RegierungOge.schreiben(d).toByteArray(Charsets.UTF_8)) }
        }
        d
    }

    // ------------------------------------------------------------ Notizen

    // Notiz zu einem Wert setzen; leer loescht sie.
    fun notizSetzen(symbol: String, text: String) {
        val t = text.trim().take(de.troetenrepublik.app.data.NOTIZ_MAX)
        store.update { it.copy(notizen = if (t.isEmpty()) it.notizen - symbol else it.notizen + (symbol to t)) }
    }

    // ------------------------------------------------------------ Anlage-These und eigene Regeln (seit 5.91)

    // Nach einem Kauf oder einer Kauforder: These speichern (Kaufgrund in die Notiz des Werts) und zaehlen.
    fun theseNachKauf(symbol: String, eingabe: de.troetenrepublik.app.engine.Anlagethese.Eingabe?, regelbruch: Boolean) {
        val now = System.currentTimeMillis()
        store.update { acc ->
            var a = acc
            if (eingabe != null) {
                val grund = eingabe.grund.trim()
                if (grund.isNotEmpty()) {
                    val alt = a.notizen[symbol].orEmpty()
                    val neu = if (alt.isBlank()) grund else if (alt.contains(grund)) alt else "$alt\n\n$grund"
                    a = a.copy(notizen = a.notizen + (symbol to neu.take(de.troetenrepublik.app.data.NOTIZ_MAX)))
                }
                val t = de.troetenrepublik.app.data.These(
                    ausstieg = eingabe.ausstieg.trim().take(500), obergrenze = eingabe.obergrenze, pruefAm = eingabe.pruefAm, seit = now,
                )
                a = a.copy(thesen = a.thesen + (symbol to t))
            }
            val z = a.theseZaehler
            a.copy(theseZaehler = z.copy(
                kaeufe = z.kaeufe + 1,
                mitThese = z.mitThese + if (eingabe != null) 1 else 0,
                regelbrueche = z.regelbrueche + if (regelbruch) 1 else 0,
            ))
        }
    }

    // „Grund gilt noch“: naechster Prueftermin in drei Monaten.
    fun theseBestaetigen(symbol: String) {
        val now = System.currentTimeMillis()
        store.update { a ->
            val t = a.thesen[symbol] ?: return@update a
            a.copy(
                thesen = a.thesen + (symbol to de.troetenrepublik.app.engine.Anlagethese.bestaetigen(t, now)),
                theseZaehler = a.theseZaehler.copy(bestaetigt = a.theseZaehler.bestaetigt + 1),
            )
        }
        _toast.value = S.thBestaetigtToast
    }

    // „Verkaufen erwaegen“: gezaehlt; der Termin ruht, bis er bestaetigt oder die These entfernt wird.
    fun theseErwogen(symbol: String) {
        store.update { a ->
            if (symbol !in a.thesen) a
            else a.copy(theseZaehler = a.theseZaehler.copy(erwogen = a.theseZaehler.erwogen + 1))
        }
    }

    fun theseEntfernen(symbol: String) {
        store.update { it.copy(thesen = it.thesen - symbol) }
    }

    fun depotRegelnSetzen(r: de.troetenrepublik.app.data.DepotRegeln) {
        store.update { it.copy(depotRegeln = r) }
    }

    // Warnungen der eigenen Regeln zu einem Kauf von betrag Euro; key ist bei Derivaten die Produktkennung.
    fun kaufWarnungen(key: String, betrag: Double, obergrenze: Double, hebel: Boolean): List<de.troetenrepublik.app.engine.Anlagethese.Befund> {
        val acc = account.value
        if (!acc.depotRegeln.aktiv && obergrenze <= 0) return emptyList()
        val v = valuation.value
        val position = v.positions.filter { it.key == key }.sumOf { it.value }
        return de.troetenrepublik.app.engine.Anlagethese.kaufPruefen(acc.depotRegeln, obergrenze, v.securities + v.cash, v.cash, position, betrag, hebel)
    }

    // Erinnert einmal je Prueftermin an gehaltene Werte mit These; ein Tipp oeffnet den Wert.
    private fun thesenErinnern(now: Long) {
        val acc = account.value
        if (acc.thesen.isEmpty()) return
        val gehalten = acc.positions.filter { !it.isDerivative }.mapTo(HashSet()) { it.symbol }
        val faellig = de.troetenrepublik.app.engine.Anlagethese.faellig(acc.thesen, gehalten, now)
        if (faellig.isEmpty()) return
        store.update { a -> a.copy(thesen = a.thesen.mapValues { (s, t) -> if (s in faellig) t.copy(gemeldetAm = now) else t }) }
        val n = notifier ?: return
        faellig.forEach { s ->
            val name = acc.positions.firstOrNull { it.symbol == s && !it.isDerivative }?.name ?: s
            n.notify(Notifier.CHANNEL_ALERTS, S.thFrage, mitKurszeile(S.thMitteilung(name), kursZeile(s)), ziel = de.troetenrepublik.app.data.WidgetZiel.instrument(s))
        }
    }

    // ------------------------------------------------------------ Monatliche Einzahlung (seit 5.91)

    // Anlegen oder aendern; nur im Uebungsdepot.
    fun einzahlplanSetzen(betrag: Double, tag: Int): Boolean {
        if (!betrag.isFinite() || betrag <= 0) { _toast.value = S.invalidAmount; return false }
        val now = System.currentTimeMillis()
        store.update { a ->
            val alt = a.einzahlplan
            a.copy(einzahlplan = if (alt == null) de.troetenrepublik.app.engine.Einzahlplaene.neu(betrag, tag, now)
                else de.troetenrepublik.app.engine.Einzahlplaene.aendern(alt, betrag, tag, now))
        }
        _toast.value = S.ezGespeichert
        return true
    }

    fun einzahlplanPausieren(pausiert: Boolean) {
        val now = System.currentTimeMillis()
        store.update { a ->
            val p = a.einzahlplan ?: return@update a
            a.copy(einzahlplan = if (pausiert) p.copy(pausiert = true) else de.troetenrepublik.app.engine.Einzahlplaene.fortsetzen(p, now))
        }
    }

    fun einzahlplanLoeschen() {
        store.update { it.copy(einzahlplan = null) }
    }

    // ------------------------------------------------------------ Konto

    // Neues Depot; announce = false bei der Einrichtung, dort gibt es nichts zurueckzusetzen.
    fun reset(capital: Double = Trading.START_CASH, announce: Boolean = true) {
        store.reset(capital)
        if (announce) _toast.value = S.accountReset
    }

    // Export eines Brokers lesen: Zeilen umrechnen und jedem Wert ein Yahoo-Kuerzel zuordnen (Katalog der App,
    // sonst Yahoo-Suche nach der ISIN, deutsche Notierung bevorzugt; Krypto als BTC-EUR). Liefert eine Vorschau,
    // ohne etwas zu aendern; null, wenn das Format unbekannt ist oder die Datei keine Zeilen hat.
    suspend fun brokerExportLesen(text: String): de.troetenrepublik.app.engine.TradeRepublicImport.Ergebnis? {
        val tr = de.troetenrepublik.app.engine.TradeRepublicImport
        val format = de.troetenrepublik.app.engine.BrokerExport.erkennen(text) ?: return null
        val (zeilen, bericht) = withContext(Dispatchers.Default) { de.troetenrepublik.app.engine.BrokerExport.lesenMitBericht(format, text) }
        // Eine Datei ohne lesbare Zeile ist ein Fehler, auch wenn sie erkannt wurde
        if (zeilen.isEmpty()) return null
        val katalog = Symbols.all.filter { it.isin.isNotBlank() }.associate { it.isin to it.symbol }
        val gleichzeitig = kotlinx.coroutines.sync.Semaphore(6)
        val kuerzel = coroutineScope {
            tr.zuzuordnen(zeilen).map { (k, klasse) ->
                async {
                    gleichzeitig.withPermit {
                        val s = if (klasse == "CRYPTO") "$k-EUR".takeIf { c -> runCatching { market.snapshot(listOf(c))[c]?.price ?: 0.0 }.getOrDefault(0.0) > 0 }
                        // Boersenkuerzel statt ISIN (Revolut, teils IBKR und Trading 212):
                        // das Kuerzel selbst, wenn es einen Kurs hat
                        else if (!de.troetenrepublik.app.engine.BrokerExport.istIsin(k)) k.uppercase().takeIf { c -> runCatching { market.snapshot(listOf(c))[c]?.price ?: 0.0 }.getOrDefault(0.0) > 0 }
                            ?: runCatching { market.yahooSuche(k) }.getOrDefault(emptyList()).firstOrNull { it.symbol.substringBefore('.').equals(k, true) }?.symbol
                        else katalog[k] ?: symbolFuerIsin(k)
                        s?.let { k to it }
                    }
                }
            }.awaitAll().filterNotNull().toMap()
        }
        return withContext(Dispatchers.Default) {
            tr.vorschau(zeilen, kuerzel, account.value, System.currentTimeMillis(), format.anzeige, format.praefix)
                .copy(ungelesen = bericht.ungelesen, ungelesenBeispiele = bericht.beispiele.toList(), ohneWechselkurs = bericht.ohneWechselkurs, ungeprueft = format.ungeprueft)
        }
    }

    // Gelesenen Broker-Export uebernehmen: ersetzt das Depot oder kommt zum aktuellen dazu.
    fun brokerExportUebernehmen(e: de.troetenrepublik.app.engine.TradeRepublicImport.Ergebnis, zusammenfuehren: Boolean) {
        val alt = store.account.value
        val neu = when {
            !zusammenfuehren -> e.konto
            e.ergaenzung != null -> e.ergaenzung
            else -> de.troetenrepublik.app.engine.Zusammenfuehren.konten(alt, e.konto)
        }
        // Verkaufte Optionen bleiben gedeckt: kein Zusammenfuehren, das gebundene Aktien abgibt
        if (!StillhalterDepot.aktienGedeckt(neu)) { _toast.value = S.error(Err.GEBUNDEN, ""); return }
        store.replace(neu)
        partsCache.clear()
        syncTracked()
        _toast.value = S.trImportiert(e.quelle, if (zusammenfuehren) neu.transactions.size - alt.transactions.size else e.konto.transactions.size)
    }

    // [0.78] Abgleich mit dem Flex-Bericht von Interactive Brokers (FlexLauf)
    val flex by lazy { FlexLauf(this, scope) }

    // Bestaetigten Vorschlag eines Abgleichs buchen.
    internal fun abgleichUebernehmen(neu: Account): Boolean {
        if (!StillhalterDepot.aktienGedeckt(neu)) { _toast.value = S.error(Err.GEBUNDEN, ""); return false }
        store.replace(neu)
        partsCache.clear()
        syncTracked()
        return true
    }

    fun exportJson(): String = Backup.export(account.value, settingsFlow.value, companies = companies.value)

    // Depot aus einer Datei uebernehmen; liefert eine Fehlermeldung oder null. zusammenfuehren: die
    // Datei kommt zum aktuellen Depot dazu, die Einstellungen bleiben; sonst ersetzt sie das Depot.
    // Firmendaten aus einer Sicherung: Logos nur von Wikimedia, Adressen nur https, damit keine Zaehlpixel hineinkommen.
    private fun firmaBereinigt(c: CompanyInfo) = c.copy(
        logoUrl = c.logoUrl.takeIf { it.startsWith("https://upload.wikimedia.org/") || it.startsWith("https://commons.wikimedia.org/") } ?: "",
        website = c.website.takeIf { it.startsWith("https://") } ?: "",
    )

    suspend fun importJson(text: String, zusammenfuehren: Boolean = false): String? {
        val parsed = Backup.parse(text)
        return when (parsed) {
            is Backup.Outcome.Error -> S.importError(parsed.code, parsed.extra)
            is Backup.Outcome.Ok -> {
                val p = parsed.payload
                if (zusammenfuehren) {
                    store.replace(de.troetenrepublik.app.engine.Zusammenfuehren.konten(store.account.value, p.account))
                    partsCache.clear()
                    p.companies.values.forEach { if (companies.value[it.symbol] == null) store.putCompany(firmaBereinigt(it)) }
                    _toast.value = S.depotMerged
                    return null
                }
                store.replace(p.account)
                partsCache.clear()
                p.companies.values.forEach { store.putCompany(firmaBereinigt(it)) }
                settings.update { s ->
                    s.copy(
                        ownerName = p.settings.ownerName.ifBlank { s.ownerName },
                        interestBps = p.settings.interestBps,
                        interestAuto = p.settings.interestAuto,
                        spreadStockBps = p.settings.spreadStockBps,
                        spreadDerivativeBps = p.settings.spreadDerivativeBps,
                        allowance = p.settings.allowance,
                        taxId = p.settings.taxId.ifBlank { s.taxId },
                        land = p.settings.land.ifBlank { s.land },
                        kategorien = p.settings.kategorien.ifBlank { s.kategorien },
                        metric = runCatching { Metric.valueOf(p.settings.metric) }.getOrDefault(s.metric),
                    )
                }
                _toast.value = S.depotImported
                null
            }
        }
    }

    // ------------------------------------------------------------ Derivate

    // Die Hebelprodukte werden aus dem jeweils aktuellen Kurs erzeugt. Damit die Detailseite ein
    // Produkt nach seiner Kennung wiederfindet, werden die erzeugten Produkte hier gemerkt.
    // Produkte im Depot haben Vorrang, weil sie ihren eigenen Stand mitfuehren (Faktor-Zertifikate).
    private val derivativeRegistry = ConcurrentHashMap<String, Derivative>()

    // Optionsschein zu einer gemeldeten Option: gleicher Basiswert, Call oder Put, Basispreis und Verfall. Gibt
    // die Kennung fuer die Derivateseite zurueck, null ohne Kurs des Basiswerts.
    suspend fun derivatFuerOption(symbol: String, call: Boolean, strike: Double, verfallTag: Long): String? {
        runCatching { market.track(listOf(symbol)); market.refreshOne(symbol) }
        val q = quotes.value[symbol] ?: return null
        val spot = q.nativePrice.takeIf { it > 0 } ?: return null
        val name = q.name.ifBlank { Symbols.find(symbol)?.name ?: symbol }
        val d = Derivatives.warrantFuer(symbol, name, spot, q.nativeCurrency.ifBlank { "USD" }, volatilityFor(symbol), call, strike, verfallTag)
        registerDerivatives(listOf(d))
        return d.id
    }

    fun registerDerivatives(list: List<Derivative>) {
        list.forEach { derivativeRegistry[it.id] = it }
    }

    fun findDerivative(id: String): Derivative? =
        account.value.positions.firstOrNull { it.key == id }?.derivative
            ?: account.value.orders.firstOrNull { it.key == id }?.derivative ?: derivativeRegistry[id]

    // Knock-out zu einem frei gewaehlten Hebel neu begeben und merken.
    fun customKnockout(symbol: String, long: Boolean, leverage: Double): Derivative? {
        val q = quotes.value[symbol] ?: return null
        if (q.nativePrice <= 0) return null
        val d = Derivatives.knockout(symbol, q.name.ifBlank { Symbols.name(symbol) }, q.nativePrice, q.nativeCurrency, long, leverage)
        derivativeRegistry[d.id] = d
        return d
    }

    // Stammdaten und Schlagzeilen, je Wert einmal geladen
    private val profileCache = ConcurrentHashMap<String, MarketApi.Profile>()
    private val halterCache = ConcurrentHashMap<String, de.troetenrepublik.app.market.Halter.Daten>()
    private val newsCache = ConcurrentHashMap<String, List<MarketApi.NewsItem>>()
    private val newsKurz = ConcurrentHashMap<String, Pair<Long, List<MarketApi.NewsItem>>>()
    private val companyLock = ConcurrentHashMap<String, Boolean>()

    private val standJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; allowSpecialFloatingPointValues = true }

    // Stammdaten und Kennzahlen aus dem Speicher ohne Abruf: aus dieser Sitzung (frisch), sonst vom letzten Start (alt).
    suspend fun profileGespeichert(symbol: String): de.troetenrepublik.app.market.StandRegel.Gespeichert<MarketApi.Profile>? {
        profileCache[symbol]?.let { return de.troetenrepublik.app.market.StandRegel.Gespeichert(it, System.currentTimeMillis(), true) }
        return withContext(Dispatchers.IO) {
            val e = market.datenGespeichert("profil|$symbol") ?: return@withContext null
            runCatching { standJson.decodeFromString<MarketApi.Profile>(e.wert) }.getOrNull()
                ?.let { de.troetenrepublik.app.market.StandRegel.Gespeichert(it, e.t, false) }
        }
    }

    suspend fun profileFor(symbol: String): MarketApi.Profile? {
        profileCache[symbol]?.let { return it }
        val roh = runCatching { market.profile(symbol) }.getOrNull()
        val p = if (roh == null || profilLuecken(roh)) runCatching { profilVonGleichen(symbol, roh) }.getOrNull() ?: roh else roh
        p ?: return null
        profileCache[symbol] = p
        runCatching { market.datenMerken("profil|$symbol", standJson.encodeToString(p)) }
        return p
    }

    // Heutige Kennzahlen einer echten Aktie fuer die Beispielzeilen des Finanzwissens: Depot, dann Watchlist, dann grosse
    // Indexwerte. Ohne Netz bleibt der Stand leer und die Lektionen zeigen keine Zeile.
    suspend fun lernbeispieleLaden() {
        val konto = account.value
        val kandidaten = (konto.positions.filter { it.derivative == null }.map { it.symbol } + konto.watchlist + de.troetenrepublik.app.engine.Lernbeispiele.ERSATZ)
            .distinct().filter { de.troetenrepublik.app.market.Symbols.typeOf(it) == de.troetenrepublik.app.data.AssetType.STOCK }.take(6)
        val aktien = ArrayList<de.troetenrepublik.app.engine.Lernbeispiele.Aktie>()
        for ((i, s) in kandidaten.withIndex()) {
            val p = profileFor(s) ?: continue
            val kgv = de.troetenrepublik.app.engine.Bewertungsblick.kgv(p.trailingPe, quotes.value[s]?.peRatio)
            val vol = if (i < 3) historischeVol(s)?.times(100.0) else null
            aktien += de.troetenrepublik.app.engine.Lernbeispiele.Aktie(
                p.shortName.ifBlank { p.longName }, kgv, p.dividendYield, vol, p.beta.takeIf { it != 0.0 },
                p.profitMargin.takeIf { it != 0.0 },
            )
        }
        de.troetenrepublik.app.engine.Lernbeispiele.setzen(de.troetenrepublik.app.engine.Lernbeispiele.zusammenstellen(aktien))
    }

    // Groesste institutionelle Halter, je Wert einmal geladen (5.93); eine verpasste Antwort wird nicht gemerkt
    suspend fun halterFor(symbol: String): de.troetenrepublik.app.market.Halter.Daten? {
        halterCache[symbol]?.let { return it }
        val d = runCatching { market.halter(symbol) }.getOrNull() ?: return null
        halterCache[symbol] = d
        return d
    }

    private fun ohneAnalysten(p: MarketApi.Profile) = p.analystCount == 0 && p.targetPrice <= 0
    private fun profilLuecken(p: MarketApi.Profile) = ohneAnalysten(p) || (p.yearly.isEmpty() && p.revenue <= 0) || p.description.isBlank()

    // Fehlende Angaben im Profil von symbol aus einer anderen Notierung derselben Aktie ergaenzen: Yahoo
    // fuehrt Analystenurteile, Finanzen und Beschreibung oft nur beim Heimatplatz, etwa bei NOVO-B.CO, nicht bei
    // anderen Notierungen von Novo Nordisk. Das Kursziel wird in die Waehrung dieser Notierung umgerechnet.
    private suspend fun profilVonGleichen(symbol: String, eigen: MarketApi.Profile?): MarketApi.Profile? {
        val name = quotes.value[symbol]?.name?.takeIf { it.isNotBlank() } ?: Symbols.name(symbol)
        val andere = (familieFinden(symbol, name) - symbol).toList()
        if (andere.isEmpty()) return null
        val kurse = market.snapshot(andere + symbol)
        val eigenQ = quotes.value[symbol] ?: kurse[symbol]
        val fx = market.fxKurse.value
        var bestes: Pair<MarketApi.Profile, Double?>? = null
        for (m in andere.take(5)) {
            val mp = profileCache[m] ?: runCatching { market.profile(m) }.getOrNull() ?: continue
            profileCache.putIfAbsent(m, mp)
            val mq = kurse[m]
            val faktor = if (mq != null && eigenQ != null) {
                val von = de.troetenrepublik.app.engine.ExchangeRates.rate(mq.nativeCurrency, fx)
                val nach = de.troetenrepublik.app.engine.ExchangeRates.rate(eigenQ.nativeCurrency, fx)
                if (von != null && nach != null) nach / von else null
            } else null
            // die Notierung mit den meisten Einschaetzungen (meist der Heimatplatz), umrechenbar vor nicht umrechenbar
            val besser = bestes == null || (faktor != null && bestes.second == null) ||
                ((faktor != null) == (bestes.second != null) && mp.analystCount > bestes.first.analystCount)
            if (besser) bestes = mp to faktor
        }
        val (mp, faktor) = bestes ?: return null
        var p = eigen ?: mp.copy(targetPrice = 0.0, targetLow = 0.0, targetHigh = 0.0, recommendation = "", recommendationMean = 0.0, analystCount = 0)
        if (ohneAnalysten(p) && !ohneAnalysten(mp) && faktor != null) {
            p = p.copy(
                recommendation = mp.recommendation, recommendationMean = mp.recommendationMean, analystCount = mp.analystCount,
                targetPrice = mp.targetPrice * faktor, targetLow = mp.targetLow * faktor, targetHigh = mp.targetHigh * faktor,
            )
        }
        if (p.yearly.isEmpty() && p.revenue <= 0 && (mp.yearly.isNotEmpty() || mp.revenue > 0)) {
            p = p.copy(
                financialCurrency = mp.financialCurrency, yearly = mp.yearly, quarterly = mp.quarterly, revenue = mp.revenue,
                ebitda = mp.ebitda, netIncome = mp.netIncome, revenueGrowth = mp.revenueGrowth, earningsGrowth = mp.earningsGrowth,
                grossMargin = mp.grossMargin, operatingMargin = mp.operatingMargin, profitMargin = mp.profitMargin,
            )
        }
        if (p.description.isBlank() && mp.description.isNotBlank()) {
            p = p.copy(description = mp.description, sector = p.sector.ifBlank { mp.sector }, industry = p.industry.ifBlank { mp.industry },
                country = p.country.ifBlank { mp.country }, website = p.website.ifBlank { mp.website }, employees = if (p.employees > 0) p.employees else mp.employees)
        }
        if (p.earningsDate <= 0 && mp.earningsDate > 0) p = p.copy(earningsDate = mp.earningsDate)
        return p
    }

    // Schlagzeilen zu einem Wert: Yahoo nach Kuerzel, mit Namen dazu Google News (5.94), neueste zuerst, ohne Doppelte.
    // Schlagzeilen aus dem Speicher ohne Abruf; gleiche Sitzung = frisch, letzter Start = alt.
    suspend fun newsGespeichert(symbol: String, name: String = ""): de.troetenrepublik.app.market.StandRegel.Gespeichert<List<MarketApi.NewsItem>>? {
        val key = "$symbol|${settingsFlow.value.sprache.code}|$name"
        newsCache[key]?.let { return de.troetenrepublik.app.market.StandRegel.Gespeichert(it, System.currentTimeMillis(), true) }
        newsKurz[key]?.let { (t, liste) -> if (System.currentTimeMillis() - t < 10 * 60_000L) return de.troetenrepublik.app.market.StandRegel.Gespeichert(liste, t, true) }
        return withContext(Dispatchers.IO) {
            val e = market.datenGespeichert("news|$key") ?: return@withContext null
            runCatching { standJson.decodeFromString<List<MarketApi.NewsItem>>(e.wert) }.getOrNull()?.takeIf { it.isNotEmpty() }
                ?.let { de.troetenrepublik.app.market.StandRegel.Gespeichert(it, e.t, false) }
        }
    }

    // Name fuer die Google-Suche zu einem Wert, wie auf der Wertpapierseite: Katalogname, sonst der mitgegebene
    fun newsName(symbol: String, ersatz: String = ""): String = de.troetenrepublik.app.market.Symbols.find(symbol)?.name ?: ersatz

    suspend fun newsFor(symbol: String, name: String = ""): List<MarketApi.NewsItem> {
        val sprache = settingsFlow.value.sprache.code
        val key = "$symbol|$sprache|$name"
        newsCache[key]?.let { return it }
        newsKurz[key]?.let { (t, liste) -> if (System.currentTimeMillis() - t < 10 * 60_000L) return liste }
        fun <T> ohneAbbruch(r: Result<T>): Result<T> = r.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
        val (yahoo, google) = coroutineScope {
            val y = async { ohneAbbruch(runCatching { market.news(symbol, sprache) }).getOrDefault(emptyList()) }
            val g = async { if (name.isBlank()) emptyList() else ohneAbbruch(runCatching { market.googleNews(name, sprache) }).getOrDefault(emptyList()) }
            y.await() to g.await()
        }
        val n = de.troetenrepublik.app.engine.WertNachrichten.zusammen(yahoo, google, { it.title }, { it.published })
        // Vollstaendige Ergebnisse bleiben fuer die Sitzung stehen, unvollstaendige zehn Minuten, leere gar nicht (offline)
        if (n.isNotEmpty()) runCatching { market.datenMerken("news|$key", standJson.encodeToString(n)) }
        if (yahoo.isNotEmpty() && (name.isBlank() || google.isNotEmpty())) newsCache[key] = n
        else if (n.isNotEmpty()) newsKurz[key] = System.currentTimeMillis() to n
        return n
    }

    private val familieSchlange = kotlinx.coroutines.sync.Mutex()

    // Andere Notierungen derselben Aktie suchen, wenn ein Wert kein Logo hat: Yahoo-Suche nach dem Namen,
    // dazu die Kurse; als gleich gelten nur Treffer mit gleichem Namen und passendem Euro-Kurs (Gleichwerte).
    // Nacheinander mit Abstand; eine erfolglose Suche wird erst nach 14 Tagen wiederholt.
    private fun familieSuchen(symbol: String, name: String) {
        scope.launch { if (foreground) familieFinden(symbol, name) }
    }

    // Bekannte oder neu gesuchte Notierungen derselben Aktie (mit symbol selbst).
    suspend fun familieFinden(symbol: String, name: String): Set<String> {
        de.troetenrepublik.app.market.Gleichwerte.familien.value[symbol]?.let { return it }
        if (Symbols.typeOf(symbol) != AssetType.STOCK || name.isBlank() || name == symbol) return setOf(symbol)
        val heute = System.currentTimeMillis() / 86_400_000L
        de.troetenrepublik.app.ui.Logos.gesucht(symbol)?.let { if (heute - it < 14) return setOf(symbol) }
        familieSchlange.withLock {
            de.troetenrepublik.app.market.Gleichwerte.familien.value[symbol]?.let { return it }
            runCatching {
                val begriff = de.troetenrepublik.app.market.Zweitnotiz.suchbegriff(name).ifBlank { return@runCatching }
                val treffer = market.search(begriff).filter { it.name.isNotBlank() }.map { it.symbol to it.name }
                val alle = (treffer + (symbol to name)).distinctBy { it.first }.take(20)
                if (alle.size < 2) return@runCatching
                val kurse = market.snapshot(alle.map { it.first })
                val gruppen = de.troetenrepublik.app.market.Gleichwerte.gruppen(alle) { kurse[it]?.price }
                gruppen.forEach { de.troetenrepublik.app.market.Gleichwerte.verbinden(it) }
                if (gruppen.none { symbol in it }) de.troetenrepublik.app.ui.Logos.gesuchtMerken(symbol, heute)
            }
            delay(1_000)
        }
        return de.troetenrepublik.app.market.Gleichwerte.familien.value[symbol] ?: setOf(symbol)
    }

    // Stammdaten aus Wikidata/Wikipedia: einmal je Wert, dann gespeichert. Fehlversuche werden
    // eine Woche lang nicht wiederholt.
    suspend fun companyInfoFor(symbol: String, name: String): CompanyInfo? {
        val known = companies.value[symbol]
        val now = System.currentTimeMillis()
        // Stammdaten von vor ARTIKEL_SEIT kennen die Adresse ihres Wikipedia-Artikels nicht: einmal nachladen
        val ohneArtikel = known != null && !known.missing && known.articleDe.isBlank() && known.articleEn.isBlank() &&
            (known.descriptionDe.isNotBlank() || known.descriptionEn.isNotBlank()) && known.fetchedAt < ARTIKEL_SEIT
        // Ohne Eintrag gespeichert vor WIKI_SEIT: damals zaehlte auch ein abgewiesener Abruf (429) als "nichts gefunden"
        val fehlgeschlagen = known != null && known.missing && known.fetchedAt < WIKI_SEIT
        // Artikel bekannt, Beschreibung fehlt (Zusammenfassung kam nicht an): nach sechs Stunden erneut
        val unvollstaendig = known != null && !known.missing && now - known.fetchedAt > 6 * 3_600_000L &&
            ((known.articleDe.isNotBlank() && known.descriptionDe.isBlank()) || (known.articleEn.isNotBlank() && known.descriptionEn.isBlank()))
        if (known != null && !ohneArtikel && !fehlgeschlagen && !unvollstaendig && (!known.missing || now - known.fetchedAt < 7 * 86_400_000L)) return known
        // Nach einem Abruf ohne Antwort Ruhe (5 Minuten, dann 30 Minuten, 2 und 12 Stunden), auch nach einem Neustart der App;
        // ebenso solange Wikimedia den Abruf abgelehnt hat. Die gespeicherte Beschreibung bleibt in der Zeit sichtbar.
        val sperreSchluessel = "firma:$symbol"
        if (de.troetenrepublik.app.market.AbrufAblage.gesperrt(sperreSchluessel) ||
            de.troetenrepublik.app.market.AbrufAblage.gesperrt(de.troetenrepublik.app.market.MarketApi.WIKI_SPERRE)) return known
        if (Symbols.typeOf(symbol) != AssetType.STOCK) return known
        if (companyLock.putIfAbsent(symbol, true) != null) return known
        try {
            val curated = Symbols.find(symbol)?.isin.orEmpty()
            val info = runCatching { market.companyInfo(symbol, name, curated.ifBlank { known?.isin.orEmpty() }) }.getOrNull()
            if (info == null) { de.troetenrepublik.app.market.AbrufAblage.ablehnung(sperreSchluessel, 0); return known }
            de.troetenrepublik.app.market.AbrufAblage.erfolg(sperreSchluessel)
            // Kuratierte ISIN hat Vorrang; Vorhandenes (Beschreibung, Artikel) bleibt, wenn der neue Abruf es nicht liefert
            val neu = if (curated.isNotBlank()) info.copy(isin = curated) else info
            val merged = de.troetenrepublik.app.engine.Abrufgedaechtnis.firmaBehalten(neu, known)
            store.putCompany(merged)
            return merged
        } finally {
            companyLock.remove(symbol)
        }
    }

    // ISIN eines Werts: kuratiert, sonst aus den Stammdaten.
    fun isinFor(symbol: String): String =
        Symbols.find(symbol)?.isin?.takeIf { it.isNotBlank() } ?: companies.value[symbol]?.isin.orEmpty()

    // Beschreibung eines Werts: Text, ob er von Wikipedia stammt, die Adresse des Artikels (leer ohne) und die Sprache
    // des Textes ("de" oder "en"; die Profilbeschreibung der Kursquelle ist englisch).
    data class Beschreibung(val text: String, val wikipedia: Boolean, val artikel: String = "", val sprache: String = "en")

    // Alle gespeicherten Wikipedia-Beschreibungen eines Werts (englisch und deutsch, ohne Doppelte); leer ohne Stammdaten
    fun beschreibungenFuer(symbol: String): String = beschreibungenVon(companies.value[symbol])

    private fun beschreibungenVon(info: CompanyInfo?): String =
        listOfNotNull(info?.descriptionEn, info?.descriptionDe).filter { it.isNotBlank() }.distinct().joinToString(" ")

    // Beschreibung in der gewaehlten Sprache. Deutsch: deutsche Wikipedia, sonst englische Wikipedia, sonst die
    // Profilbeschreibung. Englisch: englische Wikipedia, sonst die Profilbeschreibung, sonst die deutsche Wikipedia.
    fun descriptionFor(symbol: String, profile: MarketApi.Profile?): Beschreibung {
        val info = companies.value[symbol]
        val de = settingsFlow.value.sprache == Lang.DE
        val deWiki = info?.descriptionDe?.takeIf { it.isNotBlank() }?.let { Beschreibung(Lautschrift.ohne(it), true, info?.articleDe.orEmpty(), "de") }
        val enWiki = info?.descriptionEn?.takeIf { it.isNotBlank() }?.let { Beschreibung(Lautschrift.ohne(it), true, info?.articleEn.orEmpty(), "en") }
        val profil = profile?.description.orEmpty().takeIf { it.isNotBlank() }?.let { Beschreibung(it, false, "", "en") }
        return (if (de) deWiki ?: enWiki ?: profil else enWiki ?: profil ?: deWiki) ?: Beschreibung("", false)
    }

    private val volCache = ConcurrentHashMap<String, Double>()

    suspend fun volatilityFor(symbol: String): Double {
        volCache[symbol]?.let { return it }
        val type = Symbols.typeOf(symbol)
        val fallback = Derivatives.defaultVol(type)
        val candles = market.candles(symbol, Range.Y1)
        val v = Pricing.historicalVol(candles.map { it.close }, fallback, tageProJahr(type))
        volCache[symbol] = v
        return v
    }

    // Schwankung im letzten Jahr nur aus echten Kursen, ohne Ersatzwert; null bei zu wenigen Daten.
    suspend fun historischeVol(symbol: String): Double? {
        val type = Symbols.typeOf(symbol)
        val candles = market.candles(symbol, Range.Y1)
        return Pricing.historicalVol(candles.map { it.close }, Double.NaN, tageProJahr(type)).takeIf { it.isFinite() }
    }

    private fun tageProJahr(type: de.troetenrepublik.app.data.AssetType): Double =
        if (type == de.troetenrepublik.app.data.AssetType.CRYPTO) 365.0 else 252.0

    suspend fun series(symbol: String, range: Range): Series = market.series(symbol, range)

    suspend fun candles(symbol: String, range: Range): List<Candle> = market.candles(symbol, range)

    // So viele Aktien zeigt die Heatmap fuer den ganzen Markt; kleinere waeren nur noch Striche.
    internal val MARKT_KACHELN = 500

    // Ein Wert der Heatmap: Gewicht fuer die Flaeche, Tagesaenderung in Prozent (null = kein Kurs), Branche.
    // kgv: Kurs-Gewinn-Verhaeltnis aus derselben Sammelabfrage, null ohne Angabe oder bei Verlust
    data class HeatmapWert(val symbol: String, val name: String, val gewicht: Double, val aenderung: Double?, val branche: String, val region: String = "", val kgv: Double? = null)

    // Werte fuer die Heatmap. quelle: "MARKT" (die MARKT_KACHELN groessten Aktien des Katalogs weltweit),
    // "DEPOT" (Flaeche nach Positionswert), "WATCHLIST" (alle gleich gross) oder ein Index-Schluessel des Katalogs wie
    // "DAX" (Flaeche nach Boersenwert). Kurse aus der Sammelabfrage.
    suspend fun heatmap(quelle: String): List<HeatmapWert> {
        val branche = { s: String -> de.troetenrepublik.app.market.Catalog.industryOf(s).orEmpty() }
        val region = { s: String -> heatmapRegion(s) }
        return when (quelle) {
            HeatmapQuelle.DEPOT -> {
                val pos = valuation.value.positions.filter { !it.position.isDerivative && it.value > 0 }
                val q = market.snapshot(pos.map { it.position.symbol }, 2 * 60_000L)
                ohneDoppelte(pos.groupBy { it.position.symbol }.map { (s, liste) ->
                    val p = liste.first().position
                    HeatmapWert(s, p.name.ifBlank { s }, liste.sumOf { it.value }, q[s]?.takeIf { it.prevClose > 0 }?.changePct, branche(s), region(s), q[s]?.peRatio?.takeIf { it > 0 })
                }, q, summieren = true)
            }
            HeatmapQuelle.WATCHLIST -> {
                val syms = account.value.watchlist
                val q = market.snapshot(syms, 2 * 60_000L)
                ohneDoppelte(syms.map { s -> HeatmapWert(s, Symbols.find(s)?.name ?: q[s]?.name?.ifBlank { null } ?: s, 1.0, q[s]?.takeIf { it.prevClose > 0 }?.sitzungPct, branche(s), region(s), q[s]?.peRatio?.takeIf { it > 0 }) }, q, summieren = false)
            }
            else -> {
                val alle = de.troetenrepublik.app.market.Catalog.get().aktien
                val mitglieder = if (quelle == HeatmapQuelle.MARKT) alle else alle.filter { quelle in it.x }
                val q = market.snapshot(mitglieder.map { it.s }, 2 * 60_000L)
                val werte = ohneDoppelte(mitglieder.mapNotNull { m ->
                    val k = q[m.s]?.takeIf { it.marketCap > 0 } ?: return@mapNotNull null
                    HeatmapWert(m.s, m.n, k.marketCap, k.takeIf { it.prevClose > 0 }?.sitzungPct, m.i, de.troetenrepublik.app.market.Region.ofCountry(m.c), k.peRatio.takeIf { it > 0 })
                }, q, summieren = false)
                if (quelle == HeatmapQuelle.MARKT) werte.sortedByDescending { it.gewicht }.take(MARKT_KACHELN) else werte
            }
        }
    }

    // Dieselbe Firma nur einmal (5.94), fuer alle Quellen: zwei Notierungen wie SK Hynix in Seoul und als SKHY an der
    // Nasdaq oder Novo Nordisk in Kopenhagen und als ADR, zwei Gattungen wie Alphabet A und C. Erkannt nach
    // GleicheFirma.zusammenfuehren (Namensstamm, ISIN, Kennung, Kurzname mit fast gleichem Boersenwert), damit
    // gleichnamige andere Firmen bleiben. Es bleibt die Hauptnotierung; im Depot zaehlen beide Positionen zusammen.
    private fun ohneDoppelte(werte: List<HeatmapWert>, q: Map<String, de.troetenrepublik.app.data.Quote>, summieren: Boolean): List<HeatmapWert> {
        if (werte.size < 2) return werte
        val gf = de.troetenrepublik.app.engine.GleicheFirma
        val laender = de.troetenrepublik.app.market.Catalog.get().aktien.associate { it.s to it.c.ifBlank { null } }
        val notierungen = werte.map { de.troetenrepublik.app.engine.GleicheFirma.Notierung(it.symbol, it.name, laender[it.symbol], q[it.symbol]?.marketCap, q[it.symbol]?.price, isin = isinFor(it.symbol)) }
        return gf.zusammenfuehren(notierungen).map { f ->
            if (f.mitglieder.size == 1) return@map werte[f.haupt]
            if (summieren) werte[f.haupt].copy(gewicht = f.mitglieder.sumOf { werte[it].gewicht }) else werte[f.haupt]
        }
    }

    // Weltregion eines Werts fuer die Heatmap: Aktien nach Sitzland im Katalog, ETFs nach ihrer Anlageregion, sonst
    // nach dem Boersenkuerzel (ohne Endung US, .DE Deutschland); Krypto eigene Gruppe.
    private fun heatmapRegion(s: String): String {
        val k = de.troetenrepublik.app.market.Catalog.get()
        k.aktien.firstOrNull { it.s == s }?.c?.takeIf { it.isNotBlank() }?.let { return de.troetenrepublik.app.market.Region.ofCountry(it) }
        k.etfs.firstOrNull { it.s == s }?.r?.takeIf { it.isNotBlank() }?.let { return it }
        val typ = de.troetenrepublik.app.market.Symbols.typeOf(s)
        return when {
            typ == de.troetenrepublik.app.data.AssetType.CRYPTO -> "KRYPTO"
            s.endsWith(".DE") || s.endsWith(".F") -> "DEUTSCHLAND"
            !s.contains('.') && !s.contains('-') && !s.startsWith("^") -> "NORDAMERIKA"
            else -> "GLOBAL"
        }
    }

    // Kursverlauf eines Werts in einem frei gewaehlten Zeitraum: ganze Tage vonTag bis bisTag.
    suspend fun zeitraum(symbol: String, vonTag: Long, bisTag: Long): Series {
        val (von, bis) = de.troetenrepublik.app.engine.Zeitraum.grenzen(vonTag, bisTag, System.currentTimeMillis())
        return market.zeitraum(symbol, von, bis)
    }

    // Tagesschlusskurse 1999 bis 2016 (EUR zum heutigen Kurs), fuer die Krisen vor 2016 im Crash-Test.
    suspend fun longHistory(symbol: String): List<Candle> = runCatching { market.earlyDaily(symbol) }.getOrDefault(emptyList())

    // [0.72] Monatsschlusskurse seit Ende 2014 (Handelswaehrung), fuer den Sparplan-Rueckblick.
    suspend fun monatsKurse(symbol: String): List<Candle> = runCatching { market.monatsKurse(symbol) }.getOrDefault(emptyList())

    // Tagesschlusskurse in Handelswaehrung ueber sechs Jahre, leer ohne Daten.
    suspend fun kurseNativ(symbol: String): List<Candle> = runCatching { market.taeglichNativ(symbol) }.getOrDefault(emptyList())

    // Tagesschlusskurse fuer bis zu 20 Werte in einer Abfrage.
    suspend fun schlusskurse(symbols: List<String>, range: String, interval: String = "1d"): Map<String, List<Candle>> =
        runCatching { market.schlusskurse(symbols, range, interval) }.getOrDefault(emptyMap())

    // Kennzahlen fuer den Depotfinder, je 100 Kuerzel eine Abfrage.
    suspend fun kennzahlen(symbols: List<String>, zusatz: List<String> = emptyList()): Map<String, Map<String, Double>> {
        val aus = HashMap<String, Map<String, Double>>()
        for (t in symbols.chunked(100)) runCatching { market.kennzahlen(t, zusatz) }.getOrNull()?.let { aus.putAll(it) }
        return aus
    }

    private val kennzahlCache = ConcurrentHashMap<String, Pair<Long, Pair<de.troetenrepublik.app.engine.Aktienkennzahlen.Ergebnis?, String?>>>()

    // Rendite und Risiko eines Werts (de.troetenrepublik.app.engine.Aktienkennzahlen) mit dem Namen des
    // Vergleichsindex; sechs Stunden zwischengespeichert. Die Tageskurse sind zum heutigen Kurs umgerechnet, die
    // Renditen entsprechen also denen in der Handelswaehrung.
    suspend fun aktienkennzahlen(symbol: String, land: String?): Pair<de.troetenrepublik.app.engine.Aktienkennzahlen.Ergebnis?, String?> {
        val now = System.currentTimeMillis()
        kennzahlCache[symbol]?.takeIf { now - it.first < 6 * 3_600_000L }?.let { return it.second }
        val idx = de.troetenrepublik.app.engine.Aktienkennzahlen.index(symbol, land)
        val kurse = dailyCloses(symbol)
        val index = idx?.let { dailyCloses(it.first).takeIf { k -> k.size > 60 } }
        val e = de.troetenrepublik.app.engine.Aktienkennzahlen.berechnen(kurse, index)
        val aus = e to idx?.second?.takeIf { index != null }
        if (e != null) kennzahlCache[symbol] = now to aus
        return aus
    }

    // 5.91 Bilanzqualitaet, Kennzahlenvergleich, Kaufkraft

    private val abschlussCache = ConcurrentHashMap<String, Pair<Long, List<de.troetenrepublik.app.engine.Qualitaet.Jahr>>>()

    // Jahresabschluesse eines Werts fuer Piotroski und Altman, zwoelf Stunden zwischengespeichert. Fuehrt Yahoo sie
    // nur beim Heimatplatz, kommen sie von einer bekannten anderen Notierung derselben Aktie. Leer ohne Daten.
    suspend fun jahresabschluesse(symbol: String): List<de.troetenrepublik.app.engine.Qualitaet.Jahr> = jahresabschluesseOderNull(symbol).orEmpty()

    private val guvCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, de.troetenrepublik.app.engine.Sankey.Guv>>()

    // Gewinnrechnung des juengsten Geschaeftsjahrs fuer das Sankey-Diagramm (5.94), zwoelf Stunden gespeichert.
    suspend fun guv(symbol: String): de.troetenrepublik.app.engine.Sankey.Guv? {
        val now = System.currentTimeMillis()
        guvCache[symbol]?.takeIf { now - it.first < 12 * 3_600_000L }?.let { return it.second }
        val text = runCatching { market.jahresabschluesse(market.dataSymbol(symbol), de.troetenrepublik.app.engine.Sankey.GUV_FELDER) }.getOrNull()
            ?: return guvCache[symbol]?.second
        val g = withContext(Dispatchers.Default) { de.troetenrepublik.app.engine.Sankey.guvLesen(text) } ?: return guvCache[symbol]?.second
        guvCache[symbol] = now to g
        return g
    }

    // Wie jahresabschluesse, aber null, wenn keine Antwort kam (Netz, Pause der Quelle); leer, wenn Yahoo keine fuehrt.
    suspend fun jahresabschluesseOderNull(symbol: String): List<de.troetenrepublik.app.engine.Qualitaet.Jahr>? {
        val now = System.currentTimeMillis()
        abschlussCache[symbol]?.takeIf { now - it.first < 12 * 3_600_000L }?.let { return it.second }
        val q = de.troetenrepublik.app.engine.Qualitaet
        val kandidaten = listOf(symbol) + (de.troetenrepublik.app.market.Gleichwerte.familien.value[symbol].orEmpty() - symbol).take(3)
        var jahre: List<de.troetenrepublik.app.engine.Qualitaet.Jahr>? = null
        for (s in kandidaten) {
            val text = runCatching { market.jahresabschluesse(market.dataSymbol(s), q.FELDER) }.getOrNull() ?: continue
            val j = withContext(Dispatchers.Default) { q.lesen(text) }
            if (jahre == null || j.size >= 2) jahre = j
            if (j.size >= 2) break
        }
        val aus = jahre ?: return abschlussCache[symbol]?.second
        abschlussCache[symbol] = now to aus
        return aus
    }

    private val aehnlichCache = ConcurrentHashMap<String, List<String>>()

    // Aehnliche Werte laut Yahoo fuer den Kennzahlenvergleich, je Sitzung einmal geladen.
    suspend fun aehnliche(symbol: String): List<String> {
        aehnlichCache[symbol]?.let { return it }
        val l = runCatching { market.aehnliche(market.dataSymbol(symbol)) }.getOrDefault(emptyList())
        if (l.isNotEmpty()) aehnlichCache[symbol] = l
        return l
    }

    // Globus (0.69): Umrisse aus dem Asset, Wirtschaftsdaten des IWF (einen Tag in imf-weo.json gespeichert, ohne Netz der
    // letzte Stand) und die Leitindizes je Land. Alles erst beim Oeffnen des Reiters.
    @Volatile private var globusKarteStand: de.troetenrepublik.app.engine.GlobusKarte? = null

    // Schon gelesene Karte, ohne Lesen (null vor dem ersten globusKarte())
    fun globusKarteBekannt(): de.troetenrepublik.app.engine.GlobusKarte? = globusKarteStand

    suspend fun globusKarte(): de.troetenrepublik.app.engine.GlobusKarte? {
        globusKarteStand?.let { return it }
        val k = withContext(Dispatchers.Default) {
            de.troetenrepublik.app.engine.GlobusLesen.lesen(de.troetenrepublik.app.plattform.Plattform.asset("globus/laender.json").decodeToString())
        }
        globusKarteStand = k
        return k
    }

    private val imfDatei by lazy { de.troetenrepublik.app.plattform.SichereDatei(java.io.File(store.ordner, "imf-weo.json")) }
    private val imfLock = kotlinx.coroutines.sync.Mutex()
    @Volatile private var imfStand: de.troetenrepublik.app.engine.Imf.Datensatz? = null
    @Volatile private var imfFehlerAm = 0L
    @Volatile private var imfFehlschlaege = 0

    // Stand des letzten Abrufs (null, solange nie etwas geladen wurde).
    fun imfGespeichert(): de.troetenrepublik.app.engine.Imf.Datensatz? = imfStand?.let { ersatzAnwenden(it) }

    // [Globus Gesamtbild 0.73] IWF-Daten mit Rueckfallkette (Weltbank, UN, Factbook) fuer Reihen, die der IWF fuer ein Land nicht
    // fuehrt. Der Zugriff ist fuer alle Stellen derselbe; was aus den Weltbank-Abrufen schon im Speicher liegt, ist dabei.
    suspend fun imfDaten(erzwingen: Boolean = false): de.troetenrepublik.app.engine.Imf.Datensatz? = imfDatenRoh(erzwingen)?.let { ersatzAnwenden(it) }

    // Wie imfDaten, holt vorher die Weltbank-Reihen der Rueckfallkette (je eine Woche gespeichert, ohne Netz der letzte Stand).
    suspend fun imfDatenVoll(erzwingen: Boolean = false): de.troetenrepublik.app.engine.Imf.Datensatz? {
        val roh = imfDatenRoh(erzwingen) ?: return null
        runCatching { weltbankViele(de.troetenrepublik.app.engine.ImfErsatz.WELTBANK_KENNUNGEN) }.onFailure { if (it is CancellationException) throw it }
        ersatzVersion++
        return ersatzAnwenden(roh)
    }

    @Volatile private var ersatzVersion = 0
    @Volatile private var ersatzMerk: Triple<de.troetenrepublik.app.engine.Imf.Datensatz, Int, de.troetenrepublik.app.engine.Imf.Datensatz>? = null
    private val ersatzDatei by lazy {
        runCatching { de.troetenrepublik.app.engine.ImfErsatz.dateiLesen(de.troetenrepublik.app.market.Daten.text(de.troetenrepublik.app.engine.ImfErsatz.DATEI)) }.getOrNull()
    }

    private fun ersatzAnwenden(d: de.troetenrepublik.app.engine.Imf.Datensatz): de.troetenrepublik.app.engine.Imf.Datensatz {
        ersatzMerk?.let { if (it.first === d && it.second == ersatzVersion) return it.third }
        val laender = globusKarteStand?.laender?.mapTo(HashSet()) { it.a3 } ?: return d
        val er = de.troetenrepublik.app.engine.ImfErsatz
        val neu = er.anwenden(d, laender, er.WELTBANK_KENNUNGEN.associateWith { weltbankStand[it] }, ersatzDatei, java.time.LocalDate.now().year)
        ersatzMerk = Triple(d, ersatzVersion, neu)
        return neu
    }

    private suspend fun imfDatenRoh(erzwingen: Boolean = false): de.troetenrepublik.app.engine.Imf.Datensatz? = imfLock.withLock {
        val imf = de.troetenrepublik.app.engine.Imf
        val jetzt = System.currentTimeMillis()
        val alt = imfStand ?: withContext(Dispatchers.IO) {
            runCatching { imf.lesenGespeichert(imfDatei.lesen().toString(Charsets.UTF_8)) }.getOrNull()
        }
        imfStand = alt
        val pause = if (erzwingen) 10_000L else imf.wartezeit(imfFehlschlaege)
        val erneuern = imf.veraltet(alt, jetzt) || (alt != null && imf.unvollstaendig(alt))
        if (!erneuern || (imfFehlschlaege > 0 && jetzt - imfFehlerAm < pause)) return@withLock alt
        val karte = globusKarte() ?: return@withLock alt
        val codes = karte.laender.mapTo(HashSet()) { it.a3 }
        val text = runCatching { stimmungNetz.anfrage(imf.ADRESSE, max = 8_000_000).takeIf { it.ok }?.text }.getOrNull()
        val neu = text?.let { t -> withContext(Dispatchers.Default) { imf.lesen(t, jetzt, codes) } }
        if (neu == null) { imfFehlerAm = jetzt; imfFehlschlaege++; return@withLock alt }
        // Fehlt in der Antwort eine Reihe, wird frueher erneut versucht als nach einem Tag
        if (imf.unvollstaendig(neu)) { imfFehlerAm = jetzt; imfFehlschlaege++ } else imfFehlschlaege = 0
        imfStand = neu
        withContext(Dispatchers.IO) { runCatching { imfDatei.schreiben(imf.schreiben(neu).toByteArray(Charsets.UTF_8)) } }
        neu
    }

    // [Globus Boersen 0.73] Stand des Leitindex je Land (Kuerzel alpha-2) mit Herkunft und Kursdatum: Yahoo sofort, die direkten
    // Boersen laufen im Hintergrund nach (IndexLader); der Zwischenspeicher haelt auch alte Staende mit Datum.
    private val indexLader by lazy {
        val datei = de.troetenrepublik.app.plattform.SichereDatei(
            java.io.File(java.io.File(store.ordner, "globus-markt").apply { mkdirs() }, "index-stand.txt"),
        )
        de.troetenrepublik.app.market.IndexLader(
            scope = scope,
            heute = { java.time.LocalDate.now() },
            jetzt = { System.currentTimeMillis() },
            lesen = { withContext(Dispatchers.IO) { runCatching { datei.lesen().toString(Charsets.UTF_8) }.getOrNull() } },
            schreiben = { t -> withContext(Dispatchers.IO) { datei.schreiben(t.toByteArray(Charsets.UTF_8)) } },
            holen = { q ->
                stimmungNetz.anfrage(
                    q.url, mapOf("Accept" to "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8"),
                    formular = q.formular, max = q.max, json = q.json,
                ).takeIf { it.ok }?.text
            },
        )
    }

    // Gueltiger Stand je Land, wird laufend ergaenzt; die Oberflaeche faerbt nur, was frisch ist (IndexStand.frisch)
    val globusStaende: StateFlow<Map<String, de.troetenrepublik.app.market.IndexStand.Wert>> get() = indexLader.staende

    // Laender, deren Boerse mehrfach nicht geantwortet hat (Netzfilter, VPN, Ausfall)
    val globusStumm: StateFlow<Set<String>> get() = indexLader.stumm

    // Tagesaenderung des Leitindex je Land in Prozent, nur frische Werte; Laender ohne frischen Kurs fehlen.
    suspend fun globusIndizes(warteDirekt: Boolean = false): Map<String, Double> {
        val gi = de.troetenrepublik.app.market.GlobusIndizes
        indexLader.laden()
        val kurse = runCatching { kotlinx.coroutines.withTimeoutOrNull(25_000L) { market.kurseRoh(gi.SYMBOLE) } ?: emptyMap() }.onFailure { if (it is CancellationException) throw it }.getOrDefault(emptyMap())
        val jetzt = System.currentTimeMillis()
        val heute = java.time.LocalDate.now()
        val yahoo = gi.NACH_LAND.mapNotNull { (land, idx) ->
            val k = kurse[idx.symbol] ?: return@mapNotNull null
            val tag = if (k.marketTime > 0) java.time.Instant.ofEpochMilli(k.marketTime).atZone(java.time.ZoneOffset.UTC).toLocalDate().takeIf { !it.isAfter(heute) } ?: heute else heute
            de.troetenrepublik.app.market.IndexStand.Wert(land, if (idx.etf) "yahoo-etf" else "yahoo", k.sitzungPct, k.regularPrice.takeIf { it > 0 } ?: k.price, tag, jetzt)
        }
        indexLader.eintragen(yahoo)
        indexLader.anstossen()
        // Das Widget wartet (hoechstens 30 s) auf die direkten Boersen; die Ebene zeigt sofort und fuellt nach
        if (warteDirekt) kotlinx.coroutines.withTimeoutOrNull(30_000L) { indexLader.warteJob()?.join() }
        return indexLader.prozente()
    }

    // [Globus Boersen 0.73] Selbstabruf eines Index: die Person hat die Seite im Browser der App geladen und uebernimmt sie.
    // Rueckgabe false, wenn die Seite keinen lesbaren Indexwert zeigt.
    suspend fun indexSelbstUebernehmen(id: String, html: String): Boolean {
        val q = de.troetenrepublik.app.market.IndexSelbst.QUELLEN.firstOrNull { it.id == id } ?: return false
        val w = withContext(Dispatchers.Default) {
            de.troetenrepublik.app.market.IndexSelbst.lesen(q, html, System.currentTimeMillis(), java.time.LocalDate.now())
        } ?: return false
        indexLader.eintragen(listOf(w))
        return true
    }

    // Globus-Laenderblatt (0.69): je Abschnitt ein eigener Block.
    // Gemeinsam: eine Weltbank-Kennzahl fuer alle Laender, eine Woche in weltbank/<kennung>.json gespeichert, ohne Netz
    // der letzte Stand. quelle 2 = Entwicklungsindikatoren, 3 = Governance-Indikatoren.
    // Eine Sperre je Kennung: verschiedene Reihen laden gleichzeitig, dieselbe Reihe nur einmal.
    private val weltbankSperren = ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()
    private val weltbankParallel = kotlinx.coroutines.sync.Semaphore(6)
    private val weltbankStand = ConcurrentHashMap<String, de.troetenrepublik.app.engine.Weltbank.Reihe>()
    private val weltbankFehlerAm = ConcurrentHashMap<String, Long>()

    // Mehrere Reihen auf einmal, hoechstens sechs Abrufe gleichzeitig.
    suspend fun weltbankViele(kennungen: Collection<String>, quelle: Int = 2): Map<String, de.troetenrepublik.app.engine.Weltbank.Reihe?> =
        kotlinx.coroutines.coroutineScope {
            kennungen.distinct().map { k ->
                async { k to runCatching { weltbank(k, quelle) }.onFailure { if (it is CancellationException) throw it }.getOrNull() }
            }.map { it.await() }.toMap()
        }

    suspend fun weltbank(kennung: String, quelle: Int = 2): de.troetenrepublik.app.engine.Weltbank.Reihe? = weltbankSperren.getOrPut(kennung) { kotlinx.coroutines.sync.Mutex() }.withLock {
        val wb = de.troetenrepublik.app.engine.Weltbank
        val jetzt = System.currentTimeMillis()
        val datei = de.troetenrepublik.app.plattform.SichereDatei(java.io.File(java.io.File(store.ordner, "weltbank").apply { mkdirs() }, kennung.replace(Regex("[^A-Za-z0-9._]"), "_") + ".json"))
        val alt = weltbankStand[kennung] ?: withContext(Dispatchers.IO) { runCatching { wb.lesenGespeichert(datei.lesen().toString(Charsets.UTF_8)) }.getOrNull() }
        if (alt != null) weltbankStand[kennung] = alt
        if (!wb.veraltet(alt, jetzt) || jetzt - (weltbankFehlerAm[kennung] ?: 0L) < 3_600_000L) return@withLock alt
        val text = weltbankParallel.withPermit { runCatching { stimmungNetz.anfrage(wb.adresse(kennung, quelle), max = 4_000_000).takeIf { it.ok }?.text }.getOrNull() }
        val neu = text?.let { t -> withContext(Dispatchers.Default) { wb.lesen(kennung, t, jetzt) } }
        if (neu == null) { weltbankFehlerAm[kennung] = jetzt; return@withLock alt }
        weltbankStand[kennung] = neu
        withContext(Dispatchers.IO) { runCatching { datei.schreiben(wb.schreiben(neu).toByteArray(Charsets.UTF_8)) } }
        neu
    }

    // [Globus Wirtschaft]
    private val marktFehlerAm = ConcurrentHashMap<String, Long>()

    // Kurzfristiger Zins der Handelswaehrung im Verlauf fuer den Hebel-Simulator (Tageswerte der letzten elf Jahre, ein
    // Tag gespeichert): Tagesgeldsatz in Dollar (FRED) und Euro (FRED, EZB), sonst oder ohne Antwort der Leitzins der
    // BIZ. Tag seit 1970 und Prozent pro Jahr je Aenderung; leer ohne Antwort und ohne frueheren Stand.
    // abJahr: frueherer Beginn der Reihe (Krisen vor zehn Jahren); gespeichert wird unter eigenem Schluessel.
    suspend fun finanzierungszins(waehrung: String, abJahr: Int? = null): de.troetenrepublik.app.engine.HebelSimulator.Zinsdaten {
        val h = de.troetenrepublik.app.engine.HebelSimulator
        val ab = abJahr ?: (java.time.LocalDate.now(java.time.ZoneOffset.UTC).year - 11)
        suspend fun holen(q: de.troetenrepublik.app.engine.HebelSimulator.Zinsquelle): List<Pair<Long, Double>> =
            runCatching { globusMarktText(if (abJahr == null) q.schluessel else q.schluessel + "-" + abJahr, q.adresse(ab), 24 * 3_600_000L, 2_000_000, q::kuerzen) }
                .onFailure { if (it is CancellationException) throw it }.getOrNull()?.let(h::zinsLesen).orEmpty()
        val folge = h.tagesgeld(waehrung)
        val tagesgeld = folge.map { it to holen(it) }.filter { it.second.isNotEmpty() }
        // Nur mit der juengsten Reihe der Folge: Eine eingestellte Reihe allein liefe mit ihrem letzten Wert bis heute
        if (tagesgeld.isNotEmpty() && tagesgeld.last().first === folge.last()) return de.troetenrepublik.app.engine.HebelSimulator.Zinsdaten(h.verketten(tagesgeld.map { it.second }), tagesgeld.map { it.first })
        val leit = h.leitzins(waehrung) ?: return de.troetenrepublik.app.engine.HebelSimulator.Zinsdaten(emptyList(), emptyList())
        val reihe = holen(leit)
        return de.troetenrepublik.app.engine.HebelSimulator.Zinsdaten(reihe, if (reihe.isEmpty()) emptyList() else listOf(leit))
    }

    // Text einer freien Quelle, gekuerzt gespeichert und gueltigMs lang wiederverwendet; ohne Netz der letzte Stand.
    private suspend fun globusMarktText(
        schluessel: String, url: String, gueltigMs: Long, max: Int = 1_000_000, kuerzen: (String) -> String?,
    ): String? {
        val datei = de.troetenrepublik.app.plattform.SichereDatei(java.io.File(java.io.File(store.ordner, "globus-markt").apply { mkdirs() }, "$schluessel.txt"))
        // FRED verbietet Speichern und Zwischenspeichern: kein Stand auf dem Geraet, ein frueher gespeicherter wird entfernt
        if (de.troetenrepublik.app.engine.FredRegeln.istFred(url)) {
            withContext(Dispatchers.IO) { runCatching { java.io.File(java.io.File(store.ordner, "globus-markt"), "$schluessel.txt").delete() } }
            val text = runCatching {
                de.troetenrepublik.app.market.FredAbruf.geteilt(url) { stimmungNetz.anfrage(url, mapOf("Accept" to "*/*"), max = max).takeIf { it.ok }?.text }
            }.onFailure { if (it is CancellationException) throw it }.getOrNull()
            return text?.let { t -> withContext(Dispatchers.Default) { kuerzen(t) } }
        }
        val jetzt = System.currentTimeMillis()
        val gespeichert = withContext(Dispatchers.IO) { runCatching { datei.lesen().toString(Charsets.UTF_8) }.getOrNull() }
        val stand = gespeichert?.substringBefore('\n')?.toLongOrNull()
        val inhalt = if (stand != null) gespeichert.substringAfter('\n', "") else null
        if (stand != null && jetzt >= stand && jetzt - stand < gueltigMs) return inhalt
        if (jetzt - (marktFehlerAm[schluessel] ?: 0L) < 3_600_000L) return inhalt
        val text = runCatching { stimmungNetz.anfrage(url, mapOf("Accept" to "*/*"), max = max).takeIf { it.ok }?.text }
            .onFailure { if (it is CancellationException) throw it }.getOrNull()
        val klein = text?.let { t -> withContext(Dispatchers.Default) { kuerzen(t) } }
        if (klein == null) { marktFehlerAm[schluessel] = jetzt; return inhalt }
        withContext(Dispatchers.IO) { runCatching { datei.schreiben("$jetzt\n$klein".toByteArray(Charsets.UTF_8)) } }
        return klein
    }

    // Wirtschaftsstruktur eines Landes (ISO alpha-3) aus den Weltbank-Reihen; fehlende Werte fehlen.
    suspend fun globusStruktur(a3: String): de.troetenrepublik.app.engine.LaenderMarkt.Struktur {
        val werte = LinkedHashMap<de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl, de.troetenrepublik.app.engine.Weltbank.Wert>()
        val ueblich = HashMap<de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl, Int?>()
        val liste = de.troetenrepublik.app.engine.LaenderMarkt.STRUKTUR + de.troetenrepublik.app.engine.LaenderMarkt.ZINS_WELTBANK
        val reihen = weltbankViele(liste.map { it.weltbank!! })
        for (k in liste) {
            val r = reihen[k.weltbank!!] ?: continue
            r.werte[a3]?.let { werte[k] = it }
            ueblich[k] = de.troetenrepublik.app.engine.LaenderMarkt.ueblichesJahr(r)
        }
        return de.troetenrepublik.app.engine.LaenderMarkt.Struktur(werte, ueblich)
    }

    // Zinsprojektionen der Fed: Sitzung, die juenger ist als die bekannte, von der Seite der Federal Reserve. Der
    // Sitzungskalender wird einen Tag gespeichert, eine gelesene Seite ein Jahr; null ohne neue Sitzung oder ohne Antwort.
    suspend fun fedPunkteNeu(bekannt: java.time.LocalDate): de.troetenrepublik.app.engine.FedPunkte.Sitzung? {
        val fp = de.troetenrepublik.app.engine.FedPunkte
        val name = globusMarktText("fed-kalender", fp.KALENDER, 24 * 3_600_000L) { fp.neuesteSeite(it)?.second } ?: return null
        val tag = fp.tagAus(name)?.takeIf { it > bekannt } ?: return null
        val text = globusMarktText("fed-punkte-$tag", fp.BASIS + name, 365L * 24 * 3_600_000L) { seite ->
            fp.seiteLesen(seite, tag)?.let { fp.schreiben(tag.toString(), listOf(it)) }
        } ?: return null
        return fp.lesen(text)?.sitzungen?.firstOrNull()
    }

    // Tageswerte des Leitzinses eines BIS-Gebiets seit dem Ende der gebuendelten Reihe (ein Tag gespeichert); null ohne Antwort.
    suspend fun globusLeitzinsTage(gebiet: String, reihe: de.troetenrepublik.app.engine.Leitzins.Reihe): List<Pair<java.time.LocalDate, Double>>? {
        val lz = de.troetenrepublik.app.engine.Leitzins
        return globusMarktText("bis-tage-$gebiet-${reihe.bis}", lz.adresse(gebiet, reihe), 24 * 3_600_000L, 400_000) { lz.tageKuerzen(it, gebiet) }
            ?.let { lz.tageLesen(it, gebiet) }?.takeIf { it.isNotEmpty() }
    }

    // Markt eines Landes: Leitzins (BIS, ein Tag gespeichert), Rendite der Zehnjaehrigen, Waehrung und Laender-ETF.
    suspend fun globusMarkt(a2: String): de.troetenrepublik.app.engine.LaenderMarkt.Markt = kotlinx.coroutines.coroutineScope {
        val tag = 24 * 3_600_000L
        suspend fun <T> sicher(block: suspend () -> T?): T? = runCatching { block() }.onFailure { if (it is CancellationException) throw it }.getOrNull()
        val zins = async {
            sicher {
                globusMarktText("bis-leitzins", de.troetenrepublik.app.engine.LaenderMarkt.BIS_ADRESSE, tag, 200_000) { t -> t.takeIf { de.troetenrepublik.app.engine.LaenderMarkt.zinsLesen(it).isNotEmpty() } }
                    ?.let { de.troetenrepublik.app.engine.LaenderMarkt.zinsFuer(a2, de.troetenrepublik.app.engine.LaenderMarkt.zinsLesen(it)) }
            }
        }
        val rendite = async {
            sicher<Pair<de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl, de.troetenrepublik.app.engine.LaenderMarkt.Rendite>> {
                when {
                    a2 == "US" -> market.kurseRoh(listOf("^TNX"))["^TNX"]?.price?.takeIf { it > 0 && it < 30 }
                        ?.let { de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.RENDITE_USA to de.troetenrepublik.app.engine.LaenderMarkt.Rendite(null, it) }
                    a2 in de.troetenrepublik.app.engine.LaenderMarkt.EURO -> globusMarktText("ezb-rendite", de.troetenrepublik.app.engine.LaenderMarkt.EZB_ADRESSE, tag, 400_000, de.troetenrepublik.app.engine.LaenderMarkt::ezbKuerzen)
                        ?.let { de.troetenrepublik.app.engine.LaenderMarkt.ezbLesen(it)[a2] }?.let { de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.RENDITE_EZB to it }
                    a2 in de.troetenrepublik.app.engine.LaenderMarkt.FRED_LAENDER -> globusMarktText("fred-$a2", de.troetenrepublik.app.engine.LaenderMarkt.fredAdresse(a2, java.time.LocalDate.now().minusMonths(18)), tag, 200_000, de.troetenrepublik.app.engine.LaenderMarkt::fredKuerzen)
                        ?.let { de.troetenrepublik.app.engine.LaenderMarkt.fredLesen(it) }
                        ?.takeIf { it.monat != null && it.monat >= java.time.YearMonth.now().minusMonths(de.troetenrepublik.app.engine.LaenderMarkt.FRED_MAX_MONATE) }
                        ?.let { de.troetenrepublik.app.engine.LaenderMarkt.Kennzahl.RENDITE_FRED to it }
                    else -> null
                }
            }
        }
        // Waehrung zur Heimatwaehrung des Nutzers (wie "Wert der Waehrung"), gerechnet aus den Euro-Kursen beider
        val lm = de.troetenrepublik.app.engine.LaenderMarkt
        val landCode = lm.waehrungsCode(a2)
        val gegen = lm.vergleichsCode(landCode, lm.waehrungsCode(de.troetenrepublik.app.engine.Regionen.aktuell))
        val waehrungen = if (gegen == null) emptyList() else listOfNotNull(landCode, gegen).filter { it != "EUR" }
        val etfs = de.troetenrepublik.app.engine.LaenderMarkt.ETF[a2].orEmpty()
        val symbole = waehrungen.map { lm.kursSymbol(it) } + etfs.map { it.symbol }
        val reihen = async { if (symbole.isEmpty()) emptyMap() else sicher { schlusskurse(symbole, "1y") }.orEmpty() }
        val tagesKurse = async { if (etfs.isEmpty()) emptyMap() else sicher { market.kurseRoh(etfs.map { it.symbol }) }.orEmpty() }
        val r = reihen.await()
        val q = tagesKurse.await()
        // Fehlt eine der noetigen Reihen, gibt es keinen Kurs
        val kurse = if (gegen == null || waehrungen.any { r[lm.kursSymbol(it)] == null }) emptyList()
        else lm.kreuzkurs(landCode?.takeIf { it != "EUR" }?.let { r[lm.kursSymbol(it)] }, gegen.takeIf { it != "EUR" }?.let { r[lm.kursSymbol(it)] })
        de.troetenrepublik.app.engine.LaenderMarkt.Markt(
            zins = zins.await(),
            rendite = rendite.await(),
            kurs = kurse.lastOrNull()?.second,
            kursJahr = lm.kreuzVeraenderung(kurse),
            gegen = gegen ?: landCode ?: "EUR",
            etfs = etfs.mapNotNull { e ->
                val tg = q[e.symbol]?.sitzungPct
                val jr = r[e.symbol]?.let { de.troetenrepublik.app.engine.LaenderMarkt.kursVeraenderung(it) }
                if (tg == null && jr == null) null else de.troetenrepublik.app.engine.LaenderMarkt.EtfStand(e, tg, jr)
            },
        )
    }

    // [Globus Trend und Ebenen]

    // Fruehindikator (OECD CLI) je Land: DBnomics, bei altem Stand zusaetzlich FRED; ein Tag in globus-cli.json gespeichert.
    private val cliDatei by lazy { de.troetenrepublik.app.plattform.SichereDatei(java.io.File(store.ordner, "globus-cli.json")) }
    private val cliLock = kotlinx.coroutines.sync.Mutex()
    @Volatile private var cliStand: de.troetenrepublik.app.engine.Fruehindikator.Datensatz? = null
    @Volatile private var cliFehlerAm = 0L

    fun fruehindikatorGespeichert(): de.troetenrepublik.app.engine.Fruehindikator.Datensatz? = cliStand

    suspend fun fruehindikatorDaten(): de.troetenrepublik.app.engine.Fruehindikator.Datensatz? = cliLock.withLock {
        val fi = de.troetenrepublik.app.engine.Fruehindikator
        val jetzt = System.currentTimeMillis()
        val alt = cliStand ?: withContext(Dispatchers.IO) {
            runCatching { fi.lesenGespeichert(cliDatei.lesen().toString(Charsets.UTF_8)) }.getOrNull()
        }
        cliStand = alt
        if (!fi.veraltet(alt, jetzt) || jetzt - cliFehlerAm < 3_600_000L) return@withLock alt
        val karte = globusKarte() ?: return@withLock alt
        val codes = karte.laender.mapTo(HashSet()) { it.a3 }
        val monat = java.time.YearMonth.now(java.time.ZoneOffset.UTC)
        val db = runCatching { stimmungNetz.anfrage(fi.DBNOMICS_ADRESSE, max = 4_000_000).takeIf { it.ok }?.text }.getOrNull()
            ?.let { t -> withContext(Dispatchers.Default) { fi.lesenDbnomics(t, codes) } }
        var laender = db
        var ausFred = false
        if (laender == null || !fi.frisch(laender, monat)) {
            val fred = runCatching { de.troetenrepublik.app.market.FredAbruf.geteilt(fi.fredAdresse()) { stimmungNetz.anfrage(fi.fredAdresse(), mapOf("Accept" to "text/csv"), max = 3_000_000).takeIf { it.ok }?.text } }.getOrNull()
                ?.let { t -> withContext(Dispatchers.Default) { fi.lesenFred(t) } }
            ausFred = fred != null
            laender = if (laender == null) fred else if (fred == null) laender else fi.zusammen(laender, fred)
        }
        if (laender == null) { cliFehlerAm = jetzt; return@withLock alt }
        val neu = de.troetenrepublik.app.engine.Fruehindikator.Datensatz(jetzt, laender)
        // Mit Werten von FRED (Bedingungen: nicht speichern) bleibt der Stand weder im Speicher noch auf dem Geraet
        if (ausFred) return@withLock neu
        cliStand = neu
        withContext(Dispatchers.IO) { runCatching { cliDatei.schreiben(fi.schreiben(neu).toByteArray(Charsets.UTF_8)) } }
        neu
    }

    // Wirtschaftsstimmung (ESI) der EU-Laender, Eurostat; ein Tag in globus-esi.json gespeichert. Schluessel: alpha-2.
    private val esiDatei by lazy { de.troetenrepublik.app.plattform.SichereDatei(java.io.File(store.ordner, "globus-esi.json")) }
    private val esiLock = kotlinx.coroutines.sync.Mutex()
    @Volatile private var esiStand: de.troetenrepublik.app.engine.Fruehindikator.Datensatz? = null
    @Volatile private var esiFehlerAm = 0L

    suspend fun esiDaten(): de.troetenrepublik.app.engine.Fruehindikator.Datensatz? = esiLock.withLock {
        val fi = de.troetenrepublik.app.engine.Fruehindikator
        val jetzt = System.currentTimeMillis()
        val alt = esiStand ?: withContext(Dispatchers.IO) {
            runCatching { fi.lesenGespeichert(esiDatei.lesen().toString(Charsets.UTF_8)) }.getOrNull()
        }
        esiStand = alt
        if (!fi.veraltet(alt, jetzt) || jetzt - esiFehlerAm < 3_600_000L) return@withLock alt
        val von = java.time.YearMonth.now(java.time.ZoneOffset.UTC).minusMonths(5)
        val laender = runCatching { stimmungNetz.anfrage(fi.esiAdresse(von), max = 1_000_000).takeIf { it.ok }?.text }.getOrNull()
            ?.let { t -> withContext(Dispatchers.Default) { fi.lesenEsi(t) } }
        if (laender == null) { esiFehlerAm = jetzt; return@withLock alt }
        val neu = de.troetenrepublik.app.engine.Fruehindikator.Datensatz(jetzt, laender)
        esiStand = neu
        withContext(Dispatchers.IO) { runCatching { esiDatei.schreiben(fi.schreiben(neu).toByteArray(Charsets.UTF_8)) } }
        neu
    }

    // US-Rezessionszeichen von FRED (Zinskurve, Sahm-Regel, Wahrscheinlichkeit). FRED verbietet das Speichern und
    // Zwischenspeichern: bei jedem Aufruf neu, nichts im Speicher und nichts auf dem Geraet (eine fruehere Datei wird
    // entfernt). Nach einem Fehlschlag ruht der Abruf eine Stunde.
    @Volatile private var rezFehlerAm = 0L

    fun rezessionGespeichert(): de.troetenrepublik.app.engine.WeltRezession.Daten? = null

    suspend fun rezessionsDaten(): de.troetenrepublik.app.engine.WeltRezession.Daten? = de.troetenrepublik.app.market.FredAbruf.geteilt("rezession") {
        val wr = de.troetenrepublik.app.engine.WeltRezession
        val jetzt = System.currentTimeMillis()
        withContext(Dispatchers.IO) { runCatching { java.io.File(store.ordner, "globus-rezession.json").delete() } }
        if (jetzt - rezFehlerAm < 3_600_000L) return@geteilt null
        val von = java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusYears(1)
        suspend fun reihe(id: String) = runCatching { stimmungNetz.anfrage(wr.adresse(id, von), mapOf("Accept" to "text/csv")).takeIf { it.ok }?.text?.let(wr::letzter) }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()
        val neu = wr.zusammen(de.troetenrepublik.app.engine.WeltRezession.Daten(jetzt, reihe(wr.ZINSKURVE), reihe(wr.SAHM), reihe(wr.WAHRSCHEINLICHKEIT)), null)
        if (neu.leer) { rezFehlerAm = jetzt; return@geteilt null }
        neu
    }

    // [Globus Risiken]

    // Abrufe fuer den Abschnitt Risiken: je Quelle eine Datei risiken/<name>.json ("<Abrufzeit in ms>\n<Inhalt>"), Inhalt ist der
    // schon gelesene Stand. Ohne Netz oder bei Fehlern der letzte Stand; nach einem Fehlschlag erst nach fehlerPauseMs wieder.
    private val risikoLocks = ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()
    private val risikoFehlerAm = ConcurrentHashMap<String, Long>()
    private val risikoBuendelStand = ConcurrentHashMap<String, Any>()

    private suspend fun <T : Any> risikoHolen(
        name: String, gueltigMs: Long, adresse: String, max: Int, ser: kotlinx.serialization.KSerializer<T>,
        fehlerPauseMs: Long = 3_600_000L, kopf: Map<String, String> = emptyMap(), lesen: (String, Long) -> T?,
    ): T? = risikoLocks.getOrPut(name) { kotlinx.coroutines.sync.Mutex() }.withLock {
        val ls = de.troetenrepublik.app.engine.Laenderrisiken
        val datei = de.troetenrepublik.app.plattform.SichereDatei(java.io.File(java.io.File(store.ordner, "risiken").apply { mkdirs() }, "$name.json"))
        val jetzt = System.currentTimeMillis()
        val alt = withContext(Dispatchers.IO) { runCatching { datei.lesen().toString(Charsets.UTF_8) }.getOrNull() }
        val zeit = alt?.substringBefore('\n')?.toLongOrNull()
        val altStand = if (alt != null && zeit != null) ls.lesen(ser, alt.substringAfter('\n')) else null
        if (altStand != null && zeit != null && jetzt >= zeit && jetzt - zeit < gueltigMs) return@withLock altStand
        if (jetzt - (risikoFehlerAm[name] ?: 0L) < fehlerPauseMs) return@withLock altStand
        val text = runCatching { stimmungNetz.anfrage(adresse, kopf, max = max).takeIf { it.ok }?.text }.getOrNull()
        val neu = text?.let { t -> withContext(Dispatchers.Default) { lesen(t, jetzt) } }
        // Antwort da, aber nicht lesbar: Formaenderung der Quelle (DRIFT) und der Inhalt gilt nicht als frisch geholt
        if (text != null) de.troetenrepublik.app.market.Quellen.inhaltGelesen("risiko-$name", neu != null)
        if (text != null && neu == null) de.troetenrepublik.app.engine.Quellenbuendel.quelleZu(adresse.substringAfter("://").substringBefore('/'))?.let { de.troetenrepublik.app.market.Quellen.drift(it) }
        if (neu == null) { risikoFehlerAm[name] = jetzt; return@withLock altStand }
        withContext(Dispatchers.IO) { runCatching { datei.schreiben(("$jetzt\n" + ls.schreiben(ser, neu)).toByteArray(Charsets.UTF_8)) } }
        neu
    }

    suspend fun risikoUs() = risikoHolen(
        "us-reisehinweise", de.troetenrepublik.app.engine.Reisehinweise.US_GUELTIG_MS, de.troetenrepublik.app.engine.Reisehinweise.US_ADRESSE, 2_000_000,
        de.troetenrepublik.app.engine.Reisehinweise.UsStand.serializer(), lesen = de.troetenrepublik.app.engine.Reisehinweise::usLesen,
    )

    // Auswaertiges Amt: ein Versuch am Tag. Antwortet die Schnittstelle mit der Bot-Pruefung, bleibt es beim Link (null).
    suspend fun risikoAa() = risikoHolen(
        "aa-reisewarnungen", de.troetenrepublik.app.engine.Reisehinweise.US_GUELTIG_MS, de.troetenrepublik.app.engine.Reisehinweise.AA_ADRESSE, 2_000_000,
        de.troetenrepublik.app.engine.Reisehinweise.AaStand.serializer(), fehlerPauseMs = 24 * 3_600_000L, lesen = de.troetenrepublik.app.engine.Reisehinweise::aaLesen,
    )

    suspend fun risikoOpenSanctions() = risikoHolen(
        "opensanctions", de.troetenrepublik.app.engine.Sanktionen.OS_GUELTIG_MS, de.troetenrepublik.app.engine.Sanktionen.OS_ADRESSE, 1_000_000,
        de.troetenrepublik.app.engine.Sanktionen.OsStand.serializer(), lesen = de.troetenrepublik.app.engine.Sanktionen::osLesen,
    )

    suspend fun risikoRatings() = risikoHolen(
        "kreditratings", de.troetenrepublik.app.engine.Kreditratings.GUELTIG_MS, de.troetenrepublik.app.engine.Kreditratings.ADRESSE, 3_000_000,
        de.troetenrepublik.app.engine.Kreditratings.Stand.serializer(),
    ) { t, jetzt -> de.troetenrepublik.app.engine.Kreditratings.wikitext(t)?.let { de.troetenrepublik.app.engine.Kreditratings.lesen(it, jetzt) } }

    suspend fun risikoKatastrophen() = risikoHolen(
        "gdacs", de.troetenrepublik.app.engine.Katastrophen.GUELTIG_MS, de.troetenrepublik.app.engine.Katastrophen.ADRESSE, 2_000_000,
        de.troetenrepublik.app.engine.Katastrophen.Stand.serializer(), lesen = de.troetenrepublik.app.engine.Katastrophen::lesen,
    )

    suspend fun risikoWahlen() = risikoHolen(
        "wahlen", de.troetenrepublik.app.engine.Wahlen.GUELTIG_MS, de.troetenrepublik.app.engine.Wahlen.ADRESSE, 3_000_000,
        de.troetenrepublik.app.engine.Wahlen.Stand.serializer(),
    ) { t, jetzt -> de.troetenrepublik.app.engine.Kreditratings.wikitext(t)?.let { de.troetenrepublik.app.engine.Wahlen.lesen(it, jetzt) } }

    // Gebuendelte Dateien (assets/globus), einmal gelesen.
    suspend fun risikoSanktionenListe(): de.troetenrepublik.app.engine.Sanktionen.Liste? = risikoBuendel("sanktionen.json") { de.troetenrepublik.app.engine.Sanktionen.listeLesen(it) }
    suspend fun risikoKonflikte(): de.troetenrepublik.app.engine.Konflikte.Stand? = risikoBuendel("konflikte.json") { de.troetenrepublik.app.engine.Konflikte.lesen(it) }

    // [Globus Gesamtbild 0.73] Zahlen des Gesamtbilds, die nicht aus dem IWF kommen, je Land (alpha-3): Lebenserwartung und
    // Regierungsfuehrung (Weltbank, je eine Woche gespeichert), Konflikte und Sanktionen (gebuendelt), Reisehinweise der
    // USA (ein Tag gespeichert). Zuerst steht der gebuendelte Teil, danach kommt der Rest dazu; ohne Netz gilt der letzte
    // gespeicherte Stand der Quellen. `zwischen` meldet jeden Zwischenstand.
    @Volatile private var gesamtZusatzStand: Map<String, de.troetenrepublik.app.engine.GlobusGesamt.Zusatz>? = null
    @Volatile private var gesamtZusatzUm = 0L

    fun globusGesamtGespeichert(): Map<String, de.troetenrepublik.app.engine.GlobusGesamt.Zusatz>? = gesamtZusatzStand

    suspend fun globusGesamtZusatz(zwischen: (Map<String, de.troetenrepublik.app.engine.GlobusGesamt.Zusatz>) -> Unit = {}): Map<String, de.troetenrepublik.app.engine.GlobusGesamt.Zusatz> {
        val ge = de.troetenrepublik.app.engine.GlobusGesamt
        val jetzt = System.currentTimeMillis()
        gesamtZusatzStand?.let { if (jetzt - gesamtZusatzUm < 12 * 3_600_000L && jetzt >= gesamtZusatzUm) { zwischen(it); return it } }
        val karte = globusKarte() ?: return gesamtZusatzStand.orEmpty()
        val konflikte = risikoKonflikte()
        val sanktionen = risikoSanktionenListe()
        val laender = karte.laender.map { it.a3 }.distinct().mapNotNull { karte.nachA3(it) }
        fun bauen(
            wgi: Map<String, de.troetenrepublik.app.engine.Weltbank.Reihe?>, leben: de.troetenrepublik.app.engine.Weltbank.Reihe?,
            us: de.troetenrepublik.app.engine.Reisehinweise.UsStand?,
        ): Map<String, de.troetenrepublik.app.engine.GlobusGesamt.Zusatz> = laender.associate { l ->
            val perz = LinkedHashMap<String, Double>()
            for (k in de.troetenrepublik.app.engine.Laenderrisiken.WGI) {
                wgi[de.troetenrepublik.app.engine.Laenderrisiken.wgiKennung(k)]?.let { r ->
                    de.troetenrepublik.app.engine.Laenderrisiken.perzentil(r, l.a3)?.let { perz[k] = it.perzentil.toDouble() }
                }
            }
            l.a3 to ge.zusatz(
                leben?.werte?.get(l.a3)?.wert, perz,
                konflikte?.let { de.troetenrepublik.app.engine.Konflikte.imLand(it, l).maxOfOrNull { k -> k.stufe } ?: 0 },
                sanktionen?.let { s -> l.a2.takeIf { it.isNotEmpty() }?.let { s.eu[it]?.size } ?: 0 },
                sanktionen?.let { s -> l.a2.takeIf { it.isNotEmpty() }?.let { s.us[it]?.size } ?: 0 },
                us?.let { de.troetenrepublik.app.engine.Reisehinweise.usStufe(it, l) },
            )
        }
        // Erst der gebuendelte Teil, falls noch nichts da ist
        if (gesamtZusatzStand == null) {
            val vorab = withContext(Dispatchers.Default) { bauen(emptyMap(), null, null) }
            gesamtZusatzStand = vorab
            zwischen(vorab)
        }
        val wb = de.troetenrepublik.app.engine.Laenderrisiken.WGI.map { de.troetenrepublik.app.engine.Laenderrisiken.wgiKennung(it) }
        val (wgi, leben, us) = kotlinx.coroutines.coroutineScope {
            val a = async { weltbankViele(wb, quelle = 3) }
            val b = async { runCatching { weltbank("SP.DYN.LE00.IN") }.onFailure { if (it is CancellationException) throw it }.getOrNull() }
            val c = async { runCatching { risikoUs() }.onFailure { if (it is CancellationException) throw it }.getOrNull() }
            Triple(a.await(), b.await(), c.await())
        }
        val fertig = withContext(Dispatchers.Default) { bauen(wgi, leben, us) }
        gesamtZusatzStand = fertig
        // Fehlt ein Teil, kommt der naechste Aufruf wieder an die Quellen
        if (wgi.values.any { it != null } && leben != null && us != null) gesamtZusatzUm = jetzt
        zwischen(fertig)
        return fertig
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> risikoBuendel(datei: String, lesen: (String) -> T?): T? {
        risikoBuendelStand[datei]?.let { return it as T }
        val x = withContext(Dispatchers.Default) { runCatching { lesen(de.troetenrepublik.app.market.Daten.text("globus/$datei")) }.getOrNull() }
        if (x != null) risikoBuendelStand[datei] = x
        return x
    }

    // [Globus Unternehmen]
    // Landesdaten, Wikipedia-Auszug und groesste Unternehmen eines Landes (Laenderblatt). Gebuendelt: assets/globus/
    // laenderdaten.json, wiki-wirtschaft.json, firmen.json, firmen-privat.json. Live: Wikipedia-Zusammenfassung (eine Woche
    // gespeichert) und Boersenwerte (15 Minuten).
    private val globusJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private val globusAssets = ConcurrentHashMap<String, String>()
    private fun globusAsset(name: String): String = globusAssets.getOrPut(name) {
        runCatching { de.troetenrepublik.app.market.Daten.text("globus/$name") }.getOrDefault("")
    }

    private val globusBuendel by lazy { de.troetenrepublik.app.engine.LaenderFirmen.buendel(globusAsset("firmen.json")) }
    private val globusStaat by lazy { de.troetenrepublik.app.engine.LaenderFirmen.staatSymbole(globusAsset("firmen.json")) }
    private val globusPrivat by lazy { de.troetenrepublik.app.engine.LaenderFirmen.privat(globusAsset("firmen-privat.json")) }

    // Landesdaten (Stand-Datum, Eintrag des Landes) in der Sprache der App; null ohne Eintrag.
    fun globusLandesdaten(a2: String): Pair<String, de.troetenrepublik.app.engine.LaenderDaten.Eintrag>? {
        val deutsch = settingsFlow.value.sprache == Lang.DE
        val d = de.troetenrepublik.app.engine.LaenderDaten.lesen(globusAsset("laenderdaten.json"), deutsch)?.let { de.troetenrepublik.app.engine.LaenderDaten.gealtert(it, deutsch, de.troetenrepublik.app.engine.Uhr.jetzt()) } ?: return null
        return d.laender[a2]?.let { d.stand to it }
    }

    // Wikipedia-Auszug zur Wirtschaft eines Landes: Titel des Artikels, Auszug (auf etwa 600 Zeichen gekuerzt), Adresse des
    // Artikels und Adresse des Laenderartikels; null ohne Titel oder ohne Antwort und ohne gespeicherten Stand.
    @kotlinx.serialization.Serializable
    data class GlobusWiki(val titel: String, val text: String, val artikel: String, val landTitel: String, val landArtikel: String)

    private val globusWikiStand = ConcurrentHashMap<String, Pair<Long, GlobusWiki?>>()

    // [0.78] Reihenfolge: eigener Wirtschaftsartikel in der Sprache der App, sonst der Abschnitt Wirtschaft des
    // Laenderartikels, sonst kein Text (dann bleibt nur der Verweis auf den Laenderartikel). Leitet der Wirtschaftsartikel
    // auf den Laenderartikel weiter, zaehlt er als fehlend.
    suspend fun globusWiki(a2: String): GlobusWiki? {
        val ld = de.troetenrepublik.app.engine.LaenderDaten
        val deutsch = settingsFlow.value.sprache == Lang.DE
        val titel = ld.wikiTitelEigen(globusAsset("wiki-wirtschaft.json"), a2, deutsch) ?: return null
        val land = titel.land
        if (titel.wirtschaft == null && land == null) return null
        val schluessel = (if (deutsch) "d_" else "e_") + a2
        val jetzt = System.currentTimeMillis()
        globusWikiStand[schluessel]?.takeIf { jetzt - it.first < ld.GUELTIG_MS }?.let { return it.second }
        val datei = de.troetenrepublik.app.plattform.SichereDatei(java.io.File(java.io.File(store.ordner, "globus").apply { mkdirs() }, "wikiw_$schluessel.json"))
        val gespeichert = withContext(Dispatchers.IO) { runCatching { datei.lesen().toString(Charsets.UTF_8) }.getOrNull() }
        val alt = gespeichert?.let { g -> runCatching { globusJson.decodeFromString(GlobusWikiSpeicher.serializer(), g) }.getOrNull() }
        if (alt != null && jetzt - alt.abgerufen < ld.GUELTIG_MS) return alt.wiki.also { globusWikiStand[schluessel] = alt.abgerufen to it }
        val landTitel = land?.titel.orEmpty()
        val landArtikel = land?.let { ld.artikelAdresse(it.sprache, it.titel) }.orEmpty()
        var neu: GlobusWiki? = null
        titel.wirtschaft?.let { w ->
            val antwort = runCatching { stimmungNetz.anfrage(ld.auszugAdresse(w.sprache, w.titel), max = 400_000) }.getOrNull()
            // Ohne Verbindung bleibt der alte Stand; eine fehlende Seite (404) fuehrt zum Abschnitt des Laenderartikels
            if (antwort == null || antwort.code == 0 || antwort.code >= 500 || antwort.code == 429 || antwort.code == 403) return alt?.wiki
            val text = antwort.text?.takeIf { antwort.ok }
            // Eine Fehlerseite statt der Daten ersetzt den alten Stand nicht
            if (de.troetenrepublik.app.engine.Abrufgedaechtnis.htmlStattDaten(text)) return alt?.wiki
            val auszug = text?.let { ld.auszug(it) }
            if (auszug != null && ld.istWirtschaftsartikel(ld.auszugTitel(text), w.titel, land?.titel)) {
                neu = GlobusWiki(w.titel, ld.kuerzen(auszug.text, 600), auszug.adresse.ifBlank { ld.artikelAdresse(w.sprache, w.titel) }, landTitel, landArtikel)
            }
        }
        if (neu == null && land != null) {
            val antwort = runCatching { stimmungNetz.anfrage(ld.textAdresse(land.sprache, land.titel), max = 2_000_000) }.getOrNull()
            if (antwort == null || !antwort.ok) return alt?.wiki
            val a = antwort.text?.let { ld.seite(it) }?.let { s -> ld.abschnitt(s.text, listOfNotNull(s.abschnitt) + ld.wirtschaftsNamen(land.sprache)) }
            if (a != null) neu = GlobusWiki(land.titel, ld.kuerzen(a.text, 600), ld.abschnittAdresse(land.sprache, land.titel, a.kopf), landTitel, landArtikel)
        }
        val ergebnis = neu ?: GlobusWiki("", "", "", landTitel, landArtikel)
        globusWikiStand[schluessel] = jetzt to ergebnis
        withContext(Dispatchers.IO) { runCatching { datei.schreiben(globusJson.encodeToString(GlobusWikiSpeicher.serializer(), GlobusWikiSpeicher(jetzt, ergebnis)).toByteArray(Charsets.UTF_8)) } }
        return ergebnis
    }

    @kotlinx.serialization.Serializable
    private class GlobusWikiSpeicher(val abgerufen: Long, val wiki: GlobusWiki)

    // Gelistete Firmen eines Landes: Rangliste (zehn groesste nach Boersenwert in Euro), die Branchenanteile und ob die
    // Boersenwerte fehlen (dann steht die Reihenfolge des Buendels). Kurse 15 Minuten zwischengespeichert.
    data class GlobusFirmen(
        val zeilen: List<de.troetenrepublik.app.engine.LaenderFirmen.Zeile>,
        val branchen: List<de.troetenrepublik.app.engine.LaenderFirmen.Anteil>?,
        val branchenFirmen: Int,
        val ohneWerte: Boolean,
        // Boersen, von deren Seiten Werte der angezeigten Zeilen stammen (Quellenzeile)
        val quellen: List<String> = emptyList(),
        // Woher die angezeigten Werte kommen: Yahoo Finance, Moskauer Boerse, Wikidata/Wikipedia (Wert mit Jahr)
        val yahoo: Boolean = false,
        val moex: Boolean = false,
        val wikidata: Boolean = false,
        // Gesamter Boersenwert der Boerse in Euro und Name der Boerse, wenn die selbst geladene Seite nur einen Gesamtwert zeigt
        val gesamt: Pair<Double, String>? = null,
    )

    private val globusFirmenStand = ConcurrentHashMap<String, Pair<Long, GlobusFirmen>>()

    // [0.78] Zuletzt geladener Stand ohne Abruf (auch aelter als die Frist), damit ein wiederkehrendes Blatt sofort vollstaendig ist
    fun globusFirmenGemerkt(a2: String): GlobusFirmen? = globusFirmenStand[a2]?.second

    fun globusWikiGemerkt(a2: String): GlobusWiki? = globusWikiStand[(if (settingsFlow.value.sprache == Lang.DE) "d_" else "e_") + a2]?.second

    suspend fun globusFirmen(a2: String): GlobusFirmen? {
        val jetzt = System.currentTimeMillis()
        globusFirmenStand[a2]?.takeIf { jetzt - it.first < 15 * 60_000L }?.let { return it.second }
        val lf = de.troetenrepublik.app.engine.LaenderFirmen
        val kandidaten = withContext(Dispatchers.Default) {
            lf.kandidaten(a2, globusBuendel[a2].orEmpty(), de.troetenrepublik.app.market.Catalog.get().aktien, globusStaat).take(600)
        }
        val bf = de.troetenrepublik.app.engine.Boersenfeeds
        if (kandidaten.isEmpty() && bf.quellenFuer(a2).isEmpty()) return null
        val q = runCatching { market.snapshot(kandidaten.map { it.symbol }.filter { it.isNotBlank() }, 15 * 60_000L) }.getOrDefault(emptyMap())
        val kurse = HashMap<String, de.troetenrepublik.app.engine.LaenderFirmen.Kurs>()
        for (k in kandidaten) q[k.symbol]?.takeIf { it.marketCap > 0 }?.let { kurse[k.schluessel] = de.troetenrepublik.app.engine.LaenderFirmen.Kurs(it.marketCap, it.takeIf { x -> x.prevClose > 0 }?.sitzungPct, kgv = it.peRatio.takeIf { x -> x > 0 }) }
        // Firmen ohne Yahoo-Kuerzel: Boersenwert von der Moskauer Boerse (live), sonst aus Wikidata mit Jahr, in Euro
        val ohneKuerzel = kandidaten.filter { it.symbol.isBlank() }
        // Woher der Wert einer Zeile stammt (Schluessel der Kursliste): Name der Boerse, "moex" fuer die Moskauer Boerse
        val kurseQuelle = HashMap<String, String>()
        if (ohneKuerzel.isNotEmpty()) {
            val moex = if (ohneKuerzel.any { it.kennung.isNotBlank() && !it.kennung.contains(':') }) globusMoex() else null
            val kurseFx = globusFx(ohneKuerzel.mapNotNull { it.waehrung.takeIf { w -> w.isNotBlank() } }.toSet() + (if (moex != null) setOf(de.troetenrepublik.app.engine.Moex.WAEHRUNG) else emptySet()))
            for (k in ohneKuerzel) {
                val live = moex?.get(k.kennung)?.takeIf { k.kennung.isNotBlank() && !k.kennung.contains(':') }
                val eur = if (live != null) kurseFx[de.troetenrepublik.app.engine.Moex.WAEHRUNG]?.let { r -> live / r }?.let { it to null as Int? }
                else if (k.wert > 0 && k.waehrung.isNotBlank()) kurseFx[k.waehrung]?.let { r -> (k.wert / r) to (k.jahr.takeIf { it > 0 }) } else null
                if (eur != null && eur.first.isFinite() && eur.first > 0) {
                    kurse[k.schluessel] = de.troetenrepublik.app.engine.LaenderFirmen.Kurs(eur.first, null, eur.second)
                    if (live != null) kurseQuelle[k.schluessel] = "moex" else if (a2 == "MT" && eur.second == null) kurseQuelle[k.schluessel] = "Malta Stock Exchange"
                }
            }
        }
        // Boersen mit eigener Seite: Colombo, Karatschi, Casablanca, BRVM, Uganda, Zagreb, Ljubljana, Vietnam (HOSE, HNX, UPCoM), Zypern, Trinidad,
        // Baku, Lima, Kasachstan (ein Abruf je Tag bzw. mehrere nacheinander), Dhaka und Chittagong (je Firmenseite)
        val extras = ArrayList<de.troetenrepublik.app.engine.LaenderFirmen.Kandidat>()
        val neu = ArrayList<Triple<de.troetenrepublik.app.engine.Boersenfeeds.Notierung, Double, String>>()
        suspend fun verarbeite(feed: List<de.troetenrepublik.app.engine.Boersenfeeds.Notierung>, quelle: String) {
            val fxF = globusFx(feed.map { it.waehrung }.toSet())
            for (n in feed) {
                val eur = fxF[n.waehrung]?.let { r -> n.wert / r } ?: continue
                val k = kandidaten.firstOrNull { bf.passt(it.kennung, it.name, n) }
                if (k == null) { neu += Triple(n, eur, quelle); continue }
                if (k.schluessel in kurse && kurse[k.schluessel]?.jahr == null) continue
                kurse[k.schluessel] = de.troetenrepublik.app.engine.LaenderFirmen.Kurs(eur, null)
                kurseQuelle[k.schluessel] = quelle
            }
        }
        for (feedQuelle in bf.quellenFuer(a2)) {
            val feed = globusFeed(feedQuelle, kandidaten)?.filter { it.land == a2 }.orEmpty()
            if (feed.isNotEmpty()) verarbeite(feed, feedQuelle.name)
        }
        // Selbstabruf: Seiten, die die Person selbst im Browser der App geoeffnet und uebernommen hat (eine Woche gueltig)
        val selbstLink = de.troetenrepublik.app.engine.Boersenlinks.selbstFuer(a2)
        var gesamt: Pair<Double, String>? = null
        if (selbstLink != null) globusSelbstStand(a2)?.let { s ->
            val fxS = globusFx(setOf(s.waehrung) + s.firmen.map { it.waehrung })
            if (s.firmen.isNotEmpty()) verarbeite(s.firmen.filter { it.land == a2 }, selbstLink.name)
            val g = s.gesamt
            if (g != null && s.firmen.isEmpty()) fxS[s.waehrung]?.let { r -> gesamt = (g / r) to selbstLink.name }
        }
        for ((n, eur, fq) in neu.sortedByDescending { it.second }.take(12)) {
            val k = de.troetenrepublik.app.engine.LaenderFirmen.Kandidat("", bf.schoen(n.name), n.branche, false, fq)
            extras += k
            kurse[k.schluessel] = de.troetenrepublik.app.engine.LaenderFirmen.Kurs(eur, null)
            kurseQuelle[k.schluessel] = fq
        }
        if (a2 == "BD") {
            val bd = globusBangladesch(kandidaten)
            if (bd.isNotEmpty()) {
                val r = globusFx(setOf(bf.WAEHRUNG_BD))[bf.WAEHRUNG_BD]
                if (r != null) for (k in kandidaten) bd[k.kennung]?.let { v -> kurse[k.schluessel] = de.troetenrepublik.app.engine.LaenderFirmen.Kurs(v / r, null) }
                for (k in kandidaten) if (bd[k.kennung] != null) kurseQuelle[k.schluessel] = if (k.kennung.startsWith("CSE:")) bf.CHITTAGONG_NAME else bf.DHAKA_NAME
            }
        }
        // Malta: monatlich gebuendelt (Stand Monatsende), siehe kurseQuelle in der ersten Schleife
        val alle = kandidaten + extras
        val e = withContext(Dispatchers.Default) {
            val isin = alle.mapNotNull { k -> k.symbol.takeIf { it.isNotBlank() }?.let { s -> isinFor(s).takeIf { it.isNotBlank() }?.let { s to it } } }.toMap()
            val zeilen = lf.rangliste(a2, alle, kurse, isin = isin)
            val branchen = lf.branchen(a2, alle, kurse, isin)
            // Quellenzeile nur aus den angezeigten Zeilen: Boersen, Yahoo, Moskauer Boerse, Wikidata
            val mit = zeilen.filter { it.boersenwert != null }
            val quellen = mit.mapNotNull { kurseQuelle[it.kandidat.schluessel]?.takeIf { s -> s != "moex" } }.distinct()
            GlobusFirmen(
                zeilen, branchen, alle.count { (kurse[it.schluessel]?.boersenwert ?: 0.0) > 0.0 }, kurse.isEmpty(), quellen,
                yahoo = mit.any { it.kandidat.symbol.isNotBlank() },
                moex = mit.any { kurseQuelle[it.kandidat.schluessel] == "moex" },
                wikidata = mit.any { it.jahr != null },
                gesamt = gesamt,
            )
        }
        // Ohne Netz nicht zwischenspeichern, damit der naechste Versuch neu fragt
        if (q.isNotEmpty() || kandidaten.none { it.symbol.isNotBlank() }) globusFirmenStand[a2] = jetzt to e
        return e
    }

    // Wechselkurse (Einheiten je Euro) fuer die genannten ISO-Codes: die der Kursabfrage, fehlende als Yahoo-Paare EURxxx=X, eine Stunde gespeichert.
    private val globusFxStand = ConcurrentHashMap<String, Pair<Long, Double>>()

    private suspend fun globusFx(codes: Set<String>): Map<String, Double> {
        val jetzt = System.currentTimeMillis()
        val aus = HashMap<String, Double>()
        aus["EUR"] = 1.0
        val basis = market.fxKurse.value
        val fehlt = ArrayList<String>()
        for (c in codes) {
            if (c == "XOF" || c == "XAF") { aus[c] = 655.957; continue }
            val r = de.troetenrepublik.app.engine.ExchangeRates.rate(c, basis) ?: globusFxStand[c]?.takeIf { jetzt - it.first < 3_600_000L }?.second
            if (r != null) aus[c] = r else fehlt += c
        }
        if (fehlt.isNotEmpty()) runCatching { market.kurseRoh(fehlt.map { "EUR$it=X" }, 60 * 60_000L) }.getOrNull()?.forEach { (sym, k) ->
            val c = sym.removePrefix("EUR").removeSuffix("=X")
            if (k.price.isFinite() && k.price > 0) { aus[c] = k.price; globusFxStand[c] = jetzt to k.price }
        }
        return aus
    }

    // Selbstabruf einer Boersenseite: Die Person hat die Seite im Browser der App geladen (und eine Pruefung selbst geloest) und uebernimmt sie;
    // der SeitenLeser liest Tabelle oder Gesamtwert, das Ergebnis steht eine Woche in globus/selbst_<land>.txt. Rueckgabe: Zahl der gelesenen Firmen,
    // -1 ohne jeden Wert (weder Firmen noch Gesamtwert).
    suspend fun globusSelbstUebernehmen(a2: String, html: String): Int {
        val link = de.troetenrepublik.app.engine.Boersenlinks.selbstFuer(a2) ?: return -1
        val e = withContext(Dispatchers.Default) {
            de.troetenrepublik.app.engine.SeitenLeser.lesen(html, a2, link.waehrung, "SELBST")
        }
        if (e.firmen.isEmpty() && e.gesamt == null) return -1
        val jetzt = System.currentTimeMillis()
        val datei = de.troetenrepublik.app.plattform.SichereDatei(java.io.File(java.io.File(store.ordner, "globus").apply { mkdirs() }, "selbst_$a2.txt"))
        withContext(Dispatchers.IO) { runCatching { datei.schreiben(de.troetenrepublik.app.engine.SeitenLeser.speichern(jetzt, e).toByteArray(Charsets.UTF_8)) } }
        globusFirmenStand.remove(a2)
        return e.firmen.size
    }

    private suspend fun globusSelbstStand(a2: String): de.troetenrepublik.app.engine.SeitenLeser.Ergebnis? {
        val datei = de.troetenrepublik.app.plattform.SichereDatei(java.io.File(java.io.File(store.ordner, "globus").apply { mkdirs() }, "selbst_$a2.txt"))
        val text = withContext(Dispatchers.IO) { runCatching { datei.lesen().toString(Charsets.UTF_8) }.getOrNull() } ?: return null
        val (zeit, e) = de.troetenrepublik.app.engine.SeitenLeser.laden(text) ?: return null
        return e.takeIf { System.currentTimeMillis() - zeit < de.troetenrepublik.app.engine.Boersenlinks.SELBST_GUELTIG_MS }
    }

    // Boersenwerte der Moskauer Boerse (ISS, ohne Anmeldung): ein Abruf fuer alle Aktien, ein Tag in globus/moex.txt gespeichert
    // (erste Zeile Zeitpunkt, dann die Antwort); ohne Netz der letzte Stand.
    @Volatile private var globusMoexStand: Pair<Long, Map<String, Double>>? = null

    // [0.78] Name einer Firma mit Kuerzel einer weiteren Kursquelle ("MOEX:GAZP") aus der Firmenliste des Laenderblatts;
    // null fuer jedes andere Kuerzel. Steht auch da, wenn die Kursquelle nicht antwortet.
    fun firmenListeName(symbol: String): String? {
        val (_, kennung) = de.troetenrepublik.app.market.KursQuellen.teile(symbol) ?: return null
        return globusBuendel.entries.firstNotNullOfOrNull { (a2, liste) ->
            liste.firstOrNull { it.kennung == kennung && de.troetenrepublik.app.market.KursQuellen.symbolFuerLand(a2, it.kennung) == symbol }?.name
        }
    }

    // [0.73] Suche in der Firmenliste des Laenderblatts: Firmen mit Kuerzel an einer weiteren Kursquelle (russische Firmen an der
    // Moskauer Boerse), die die Boersenabfrage mit lateinischen Namen nicht findet.
    fun firmenListeSuche(text: String): List<de.troetenrepublik.app.market.MarketApi.SearchHit> {
        val q = text.trim()
        if (q.length < 2) return emptyList()
        return globusBuendel.flatMap { (a2, liste) ->
            liste.mapNotNull { k ->
                val symbol = de.troetenrepublik.app.market.KursQuellen.symbolFuerLand(a2, k.kennung) ?: return@mapNotNull null
                if (!k.name.contains(q, ignoreCase = true) && !k.kennung.equals(q, ignoreCase = true)) return@mapNotNull null
                de.troetenrepublik.app.market.MarketApi.SearchHit(symbol, k.name, "EQUITY", S.boerseMoskau, "", S.industryName(k.branche).takeIf { k.branche.isNotBlank() }.orEmpty())
            }
        }.distinctBy { it.symbol }.take(6)
    }

    // [0.73] Firmenangaben aus Wikidata je Name, 30 Tage in globus/firma_wd.json gespeichert (auch "nichts gefunden").
    @kotlinx.serialization.Serializable
    class FirmaWdEintrag(val z: Long = 0L, val a: de.troetenrepublik.app.market.FirmaWikidata.Angaben? = null)

    private val firmaWdStand = ConcurrentHashMap<String, FirmaWdEintrag>()
    private val firmaWdLock = kotlinx.coroutines.sync.Mutex()
    private val firmaWdDatei by lazy { de.troetenrepublik.app.plattform.SichereDatei(java.io.File(java.io.File(store.ordner, "globus").apply { mkdirs() }, "firma_wd.json")) }
    private val firmaWdListe = kotlinx.serialization.builtins.MapSerializer(kotlinx.serialization.serializer<String>(), FirmaWdEintrag.serializer())

    suspend fun firmaWikidata(name: String): de.troetenrepublik.app.market.FirmaWikidata.Angaben? = firmaWdLock.withLock {
        val jetzt = System.currentTimeMillis()
        if (firmaWdStand.isEmpty()) {
            val g = withContext(Dispatchers.IO) { runCatching { firmaWdDatei.lesen().toString(Charsets.UTF_8) }.getOrNull() }
            g?.let { runCatching { globusJson.decodeFromString(firmaWdListe, it) }.getOrNull() }?.let { firmaWdStand.putAll(it) }
        }
        val sprache = if (settingsFlow.value.sprache == Lang.DE) "de" else "en"
        val schluessel = "$sprache|$name"
        firmaWdStand[schluessel]?.takeIf { jetzt - it.z < 30 * 86_400_000L }?.let { return@withLock it.a }
        // Ein Fehlschlag (kein Netz) wird nicht gespeichert; "nichts gefunden" schon
        val netz = runCatching { market.firmaWikidata(name, sprache) }
        if (netz.isFailure) return@withLock null
        firmaWdStand[schluessel] = FirmaWdEintrag(jetzt, netz.getOrNull())
        withContext(Dispatchers.IO) { runCatching { firmaWdDatei.schreiben(globusJson.encodeToString(firmaWdListe, firmaWdStand.toMap()).toByteArray()) } }
        netz.getOrNull()
    }

    // Wikipedia-Auszug zu einer Firma nach ihrem Namen (ein Abruf je Name und Sprache im Speicher); null ohne Artikel
    private val firmaAuszugStand = ConcurrentHashMap<String, String>()

    suspend fun firmaAuszug(name: String): String? {
        val sprache = if (settingsFlow.value.sprache == Lang.DE) "de" else "en"
        val schluessel = "$sprache|$name"
        firmaAuszugStand[schluessel]?.let { return it.takeIf { t -> t.isNotEmpty() } }
        val text = runCatching { market.wikiAuszug(sprache, name) }.getOrNull()
        // Weiterleitungen und Begriffsklaerungen liefern kurze oder allgemeine Texte: nur ein Auszug, der den Namen nennt
        val gut = text?.takeIf { it.length >= 60 && it.contains(name.substringBefore(' '), ignoreCase = true) }
        if (text != null) firmaAuszugStand[schluessel] = gut.orEmpty()
        return gut
    }

    private suspend fun globusMoex(): Map<String, Double>? {
        val mx = de.troetenrepublik.app.engine.Moex
        val jetzt = System.currentTimeMillis()
        globusMoexStand?.takeIf { jetzt - it.first < mx.GUELTIG_MS }?.let { return it.second }
        val datei = de.troetenrepublik.app.plattform.SichereDatei(java.io.File(java.io.File(store.ordner, "globus").apply { mkdirs() }, "moex.txt"))
        val gespeichert = withContext(Dispatchers.IO) { runCatching { datei.lesen().toString(Charsets.UTF_8) }.getOrNull() }
        val zeit = gespeichert?.substringBefore('\n')?.toLongOrNull()
        val alt = gespeichert?.substringAfter('\n', "")?.takeIf { zeit != null && it.isNotBlank() }?.let { mx.lesen(it) }?.takeIf { it.isNotEmpty() }
        if (alt != null && zeit != null && jetzt - zeit < mx.GUELTIG_MS) return alt.also { globusMoexStand = zeit to it }
        val text = runCatching { stimmungNetz.anfrage(mx.ADRESSE, max = 2_000_000).takeIf { it.ok }?.text }.getOrNull()
        val neu = text?.let { mx.lesen(it) }?.takeIf { it.isNotEmpty() }
        if (neu == null) return alt
        globusMoexStand = jetzt to neu
        withContext(Dispatchers.IO) { runCatching { datei.schreiben(("$jetzt\n" + text).toByteArray(Charsets.UTF_8)) } }
        return neu
    }

    // Tagesabruf einer Boersenseite: ein Abruf (oder bei mehrstufigen Quellen mehrere nacheinander), ein Tag in globus/feed_<id>.txt gespeichert
    // (Zeitpunkt, Semikolon, Notierungen), ohne Netz der letzte Stand. Gleichzeitige Abrufe derselben Quelle teilen sich eine Sperre.
    // Ist eine mehrstufige Quelle erst teilweise geladen (Lima), gilt der Stand nur 15 Minuten.
    private val globusFeedStand = ConcurrentHashMap<String, Pair<Long, List<de.troetenrepublik.app.engine.Boersenfeeds.Notierung>>>()
    private val globusFeedLocks = ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()

    private suspend fun globusFeed(
        q: de.troetenrepublik.app.engine.Boersenfeeds.Quelle, kandidaten: List<de.troetenrepublik.app.engine.LaenderFirmen.Kandidat> = emptyList(),
    ): List<de.troetenrepublik.app.engine.Boersenfeeds.Notierung>? = globusFeedLocks.getOrPut(q.id) { kotlinx.coroutines.sync.Mutex() }.withLock {
        val bf = de.troetenrepublik.app.engine.Boersenfeeds
        val bm = de.troetenrepublik.app.engine.BoersenfeedsMehr
        val jetzt = System.currentTimeMillis()
        val tag = 24 * 3_600_000L
        globusFeedStand[q.id]?.takeIf { jetzt - it.first < tag }?.let { return@withLock it.second }
        val datei = de.troetenrepublik.app.plattform.SichereDatei(java.io.File(java.io.File(store.ordner, "globus").apply { mkdirs() }, "feed_" + q.id + ".txt"))
        val gespeichert = withContext(Dispatchers.IO) { runCatching { datei.lesen().toString(Charsets.UTF_8) }.getOrNull() }
        val zeit = gespeichert?.substringBefore(';')?.toLongOrNull()
        val alt = gespeichert?.takeIf { zeit != null }?.substringAfter(';')?.let { bf.laden(it) }?.takeIf { it.isNotEmpty() }
        if (alt != null && zeit != null && jetzt - zeit < tag) { globusFeedStand[q.id] = zeit to alt; return@withLock alt }
        var vollstaendig = true
        val neu = if (q.mehrstufig) {
            val r = when (q.id) {
                bm.ZAGREB.id, bm.LJUBLJANA.id -> globusZseLjse(q)
                bm.HOSE.id -> globusHose()
                bm.HNX.id, bm.UPCOM.id -> globusHanoi(q)
                bm.LIMA.id -> globusLima(kandidaten)
                else -> null
            }
            vollstaendig = r?.second ?: true
            r?.first?.takeIf { it.isNotEmpty() }
        } else {
            val kopf = if (q.html) mapOf("Accept" to "text/html") else emptyMap()
            val text = runCatching { stimmungNetz.anfrage(q.adresse, kopf = kopf, max = 4_000_000, methode = if (q.post) "POST" else null).takeIf { it.ok }?.text }.getOrNull()
            text?.let { t ->
                withContext(Dispatchers.Default) {
                    when (q.id) {
                        bf.COLOMBO.id -> bf.colombo(t)
                        bf.KARATSCHI.id -> bf.karatschi(t)
                        bf.CASABLANCA.id -> bf.casablanca(t)
                        bf.BRVM.id -> bf.brvm(t)
                        bf.UGANDA.id -> bf.uganda(t)
                        bm.ZYPERN.id -> bm.zypern(t)
                        bm.TRINIDAD.id -> bm.trinidad(t)
                        bm.BAKU.id -> bm.baku(t)
                        bm.KASE.id -> bm.kase(t)
                        else -> emptyList()
                    }
                }
            }?.takeIf { it.isNotEmpty() }
        }
        if (neu == null) return@withLock alt
        val stand = if (vollstaendig) jetzt else jetzt - tag + 15 * 60_000L
        globusFeedStand[q.id] = stand to neu
        withContext(Dispatchers.IO) { runCatching { datei.schreiben((stand.toString() + ";" + bf.speichern(neu)).toByteArray(Charsets.UTF_8)) } }
        neu
    }

    // Einzelner Abruf einer Boerse fuer die mehrstufigen Quellen; null ohne Netz oder bei Fehlern
    private suspend fun globusHole(
        url: String, kopf: Map<String, String> = emptyMap(), formular: List<Pair<String, String>>? = null, methode: String? = null,
        json: String? = null, max: Int = 4_000_000,
    ): String? = runCatching { stimmungNetz.anfrage(url, kopf = kopf, formular = formular, max = max, methode = methode, json = json).takeIf { it.ok }?.text }.getOrNull()

    // Aktienzahlen einer Boerse, einen Monat in globus/aktien_<id>.txt gespeichert (erste Zeile Zeitpunkt); ohne Netz der letzte Stand
    private suspend fun globusAktienzahlen(id: String, hole: suspend () -> List<de.troetenrepublik.app.engine.BoersenfeedsMehr.Aktie>?): List<de.troetenrepublik.app.engine.BoersenfeedsMehr.Aktie>? {
        val bm = de.troetenrepublik.app.engine.BoersenfeedsMehr
        val jetzt = System.currentTimeMillis()
        val monat = 30 * 24 * 3_600_000L
        val datei = de.troetenrepublik.app.plattform.SichereDatei(java.io.File(java.io.File(store.ordner, "globus").apply { mkdirs() }, "aktien_$id.txt"))
        val gespeichert = withContext(Dispatchers.IO) { runCatching { datei.lesen().toString(Charsets.UTF_8) }.getOrNull() }
        val zeit = gespeichert?.substringBefore('\n')?.toLongOrNull()
        val alt = gespeichert?.substringAfter('\n', "")?.takeIf { zeit != null }?.let { bm.aktienLaden(it) }?.takeIf { it.isNotEmpty() }
        if (alt != null && zeit != null && jetzt - zeit < monat) return alt
        val neu = hole()?.takeIf { it.isNotEmpty() } ?: return alt
        withContext(Dispatchers.IO) { runCatching { datei.schreiben(("$jetzt\n" + bm.aktienSpeichern(neu)).toByteArray(Charsets.UTF_8)) } }
        return neu
    }

    // Zagreb und Ljubljana: Aktienzahl (securities, ein Monat) mal Schlusskurs (price-list). Der letzte Handelstag und der davor, damit
    // Aktien ohne Handel am letzten Tag nicht fehlen; ein Tag ohne Liste (Wochenende, Feiertag) wird uebersprungen.
    private suspend fun globusZseLjse(q: de.troetenrepublik.app.engine.Boersenfeeds.Quelle): Pair<List<de.troetenrepublik.app.engine.Boersenfeeds.Notierung>, Boolean>? {
        val bm = de.troetenrepublik.app.engine.BoersenfeedsMehr
        val zagreb = q.id == bm.ZAGREB.id
        val aktien = globusAktienzahlen(q.id) { globusHole(if (zagreb) bm.zseWertpapiere() else bm.ljseWertpapiere(), max = 3_000_000)?.let { bm.zseAktien(it) } } ?: return null
        var tag = java.time.LocalDate.now(java.time.ZoneId.of(if (zagreb) "Europe/Zagreb" else "Europe/Ljubljana"))
        val preise = HashMap<String, Double>()
        var gefunden = 0
        var versuche = 0
        while (gefunden < 2 && versuche < 8) {
            val p = globusHole(if (zagreb) bm.zsePreisliste(tag.toString()) else bm.ljsePreisliste(tag.toString()), max = 1_000_000)?.let { bm.zsePreise(it) }.orEmpty()
            if (p.isNotEmpty()) { for ((k, v) in p) preise.putIfAbsent(k, v); gefunden++ }
            tag = tag.minusDays(1)
            versuche++
        }
        return bm.malAnzahl(aktien, preise, if (zagreb) "ZSE" else "LJSE", "EUR", if (zagreb) "HR" else "SI") to true
    }

    // Ho-Chi-Minh-Stadt: Aktienzahl (neun Seiten, ein Monat) mal Schlusskurs des letzten Handelstags (quote-report); Abrufe nacheinander
    private suspend fun globusHose(): Pair<List<de.troetenrepublik.app.engine.Boersenfeeds.Notierung>, Boolean>? {
        val bm = de.troetenrepublik.app.engine.BoersenfeedsMehr
        val aktien = globusAktienzahlen(bm.HOSE.id) {
            val aus = ArrayList<de.troetenrepublik.app.engine.BoersenfeedsMehr.Aktie>()
            var ganz = false
            for (seite in 1..15) {
                val t = globusHole(bm.hoseAktienAdresse(seite), max = 1_000_000) ?: break
                val n = bm.hoseSeitenGroesse(t)
                aus += bm.hoseAktien(t)
                if (n < 50) { ganz = n > 0 || seite > 1; break }
                delay(500)
            }
            aus.takeIf { ganz }
        } ?: return null
        var tag = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
        var preise = emptyMap<String, Double>()
        for (versuch in 0 until 6) {
            preise = globusHole(bm.hoseKurseAdresse(tag.toString()), methode = "POST", max = 2_000_000)?.let { bm.hoseKurse(it) }.orEmpty()
            if (preise.isNotEmpty()) break
            tag = tag.minusDays(1)
            delay(500)
        }
        return bm.malAnzahl(aktien, preise, "HOSE", "VND", "VN") to true
    }

    // Hanoi (HNX) und UPCoM: Emittentenliste mit Aktienzahl (ein Monat) und Kurse in acht Abrufen nach Anfangsbuchstaben, eine Sekunde Pause
    private suspend fun globusHanoi(q: de.troetenrepublik.app.engine.Boersenfeeds.Quelle): Pair<List<de.troetenrepublik.app.engine.Boersenfeeds.Notierung>, Boolean>? {
        val bm = de.troetenrepublik.app.engine.BoersenfeedsMehr
        val hnx = q.id == bm.HNX.id
        val ajax = mapOf("X-Requested-With" to "XMLHttpRequest")
        val aktien = globusAktienzahlen(q.id) {
            globusHole(if (hnx) bm.HNX_AKTIEN else bm.UPCOM_AKTIEN, kopf = ajax, formular = bm.hnxFormular(if (hnx) "" else "UC"), max = 3_000_000)?.let { bm.hnxAktien(it) }
        } ?: return null
        val kurse = ArrayList<de.troetenrepublik.app.engine.BoersenfeedsMehr.HnxKurs>()
        var ganz = true
        for ((i, b) in bm.HNX_BUCHSTABEN.withIndex()) {
            if (i > 0) delay(1000)
            val t = globusHole(bm.HNX_KURSE, kopf = ajax, formular = bm.hnxKursFormular(if (hnx) "LIS" else "UPC", b), max = 2_000_000)
            if (t == null) { ganz = false; continue }
            kurse += bm.hnxKurse(t)
        }
        return bm.hnxMalAnzahl(aktien, kurse, if (hnx) "HNX" else "UPCOM", "VN") to ganz
    }

    // Lima: Kurse einmal (stock-quote/market), Aktienzahl je Emittent (issuers/<Code>, eine Sekunde Pause, hoechstens 15 je Aufruf, die Kandidaten
    // des Landes zuerst, dann liquide Segmente nach Umsatz); die Emittenten stehen einen Monat in globus/emittenten_bvl.txt (erste Zeile Beginn).
    // Zweiter Wert: alle Emittenten mit Kurs sind erfasst.
    private val globusLimaLimit = 15

    private suspend fun globusLima(kandidaten: List<de.troetenrepublik.app.engine.LaenderFirmen.Kandidat>): Pair<List<de.troetenrepublik.app.engine.Boersenfeeds.Notierung>, Boolean>? {
        val bm = de.troetenrepublik.app.engine.BoersenfeedsMehr
        val kurse = globusHole(bm.LIMA_KURSE, methode = "POST", json = "{}", max = 3_000_000)?.let { bm.peruKurse(it) }.orEmpty()
        val jetzt = System.currentTimeMillis()
        val datei = de.troetenrepublik.app.plattform.SichereDatei(java.io.File(java.io.File(store.ordner, "globus").apply { mkdirs() }, "emittenten_bvl.txt"))
        val gespeichert = withContext(Dispatchers.IO) { runCatching { datei.lesen().toString(Charsets.UTF_8) }.getOrNull() }
        var beginn = gespeichert?.substringBefore('\n')?.toLongOrNull()
        var emitt: Map<String, de.troetenrepublik.app.engine.BoersenfeedsMehr.PeruEmittent> =
            gespeichert?.substringAfter('\n', "")?.takeIf { beginn != null }?.let { bm.emittentenLaden(it) }.orEmpty()
        if (beginn == null || jetzt - beginn > 30 * 24 * 3_600_000L) { beginn = jetzt; if (kurse.isNotEmpty()) emitt = emptyMap() }
        if (kurse.isEmpty()) return bm.peruWerte(emptyList(), emitt) to true
        val kennungen = kandidaten.map { it.kennung }.toSet()
        val umsatz = kurse.groupBy { it.firma }.mapValues { (_, l) -> l.sumOf { it.umsatz } }
        val liquide = kurse.groupBy { it.firma }.mapValues { (_, l) -> l.any { it.segment != "RV2" } }
        val offen = umsatz.keys.filter { it !in emitt }
            .sortedWith(compareByDescending<String> { "BVL:$it" in kennungen }.thenByDescending { liquide[it] == true }.thenByDescending { umsatz[it] ?: 0.0 })
        val neu = emitt.toMutableMap()
        var geholt = 0
        for (f in offen.take(globusLimaLimit)) {
            if (geholt > 0) delay(1000)
            val e = globusHole(bm.limaEmittent(f), max = 3_000_000)?.let { bm.peruEmittent(it) }
            geholt++
            if (e != null) neu[f] = e
        }
        if (geholt > 0) withContext(Dispatchers.IO) { runCatching { datei.schreiben(("$beginn\n" + bm.emittentenSpeichern(neu)).toByteArray(Charsets.UTF_8)) } }
        return bm.peruWerte(kurse, neu) to (offen.size <= globusLimaLimit)
    }

    // Dhaka und Chittagong: je Firmenseite ein Abruf, nur fuer die ersten zehn Firmen mit Kuerzel ("DSE:ACI", "CSE:ICB") und nur, wenn
    // der gespeicherte Wert aelter als ein Tag ist; nacheinander mit Pause, gespeichert in globus/bd.txt (Kuerzel, Taka, Zeitpunkt).
    private val globusBdLock = kotlinx.coroutines.sync.Mutex()

    private suspend fun globusBangladesch(kandidaten: List<de.troetenrepublik.app.engine.LaenderFirmen.Kandidat>): Map<String, Double> = globusBdLock.withLock {
        val bf = de.troetenrepublik.app.engine.Boersenfeeds
        val jetzt = System.currentTimeMillis()
        val tag = 24 * 3_600_000L
        val datei = de.troetenrepublik.app.plattform.SichereDatei(java.io.File(java.io.File(store.ordner, "globus").apply { mkdirs() }, "bd.txt"))
        val stand = HashMap<String, Pair<Double, Long>>()
        withContext(Dispatchers.IO) { runCatching { datei.lesen().toString(Charsets.UTF_8) }.getOrNull() }?.lines()?.forEach { z ->
            val f = z.split(';')
            if (f.size == 3) stand[f[0]] = (f[1].toDoubleOrNull() ?: return@forEach) to (f[2].toLongOrNull() ?: return@forEach)
        }
        var geaendert = false
        for (k in kandidaten.filter { it.kennung.startsWith("DSE:") || it.kennung.startsWith("CSE:") }.take(10)) {
            if (stand[k.kennung]?.let { jetzt - it.second < tag } == true) continue
            val code = k.kennung.substringAfter(':')
            val adr = if (k.kennung.startsWith("DSE:")) bf.dhakaAdresse(code) else bf.chittagongAdresse(code)
            val t = runCatching { stimmungNetz.anfrage(adr, kopf = mapOf("Accept" to "text/html"), max = 4_000_000).takeIf { it.ok }?.text }.getOrNull()
            val v = t?.let { if (k.kennung.startsWith("DSE:")) bf.dhaka(it) else bf.chittagong(it) }
            if (v != null) { stand[k.kennung] = v to jetzt; geaendert = true }
            delay(400)
        }
        if (geaendert) withContext(Dispatchers.IO) { runCatching { datei.schreiben(stand.entries.joinToString(System.lineSeparator()) { it.key + ";" + it.value.first.toLong() + ";" + it.value.second }.toByteArray(Charsets.UTF_8)) } }
        stand.filter { (_, v) -> jetzt - v.second < 7 * tag }.mapValues { it.value.first }
    }

    // Nicht gelistete Firmen eines Landes (von Hand geprueft) mit dem Stand-Datum der Datei.
    fun globusPrivateFirmen(a2: String): List<de.troetenrepublik.app.engine.LaenderFirmen.Privat> =
        globusPrivat[a2].orEmpty()

    // Gibt es fuer das Land Kandidaten (Katalog oder Buendel)? Entscheidet, ob der Abschnitt beim Laden schon da ist.
    fun globusHatFirmen(a2: String): Boolean =
        globusBuendel[a2]?.isNotEmpty() == true || de.troetenrepublik.app.market.Catalog.get().aktien.any { it.c == a2 } || de.troetenrepublik.app.engine.Boersenfeeds.quellenFuer(a2).isNotEmpty()

    fun globusPrivatStand(): String =
        Regex("\"stand\":\"([^\"]*)\"").find(globusAsset("firmen-privat.json"))?.groupValues?.get(1).orEmpty()

    // [Globus Mehr]
    // Gebuendelte Dateien (Handel, Steckbrief), einmal aus dem Asset gelesen.
    @Volatile private var mehrHandel: Map<String, de.troetenrepublik.app.engine.LaenderZusatz.Handel>? = null
    @Volatile private var mehrSteckbrief: Map<String, de.troetenrepublik.app.engine.LaenderZusatz.Steckbrief>? = null

    suspend fun globusHandel(): Map<String, de.troetenrepublik.app.engine.LaenderZusatz.Handel> = mehrHandel ?: withContext(Dispatchers.Default) {
        de.troetenrepublik.app.engine.LaenderZusatz.handelLesen(runCatching { de.troetenrepublik.app.market.Daten.text("globus/handel.json") }.getOrDefault(""))
    }.also { mehrHandel = it }

    suspend fun globusSteckbrief(): Map<String, de.troetenrepublik.app.engine.LaenderZusatz.Steckbrief> = mehrSteckbrief ?: withContext(Dispatchers.Default) {
        de.troetenrepublik.app.engine.LaenderZusatz.steckbriefLesen(runCatching { de.troetenrepublik.app.market.Daten.text("globus/steckbrief.json") }.getOrDefault(""))
    }.also { mehrSteckbrief = it }

    private val mehrOrdner by lazy { java.io.File(store.ordner, "globus-mehr").apply { mkdirs() } }
    private val mehrFehlerAm = ConcurrentHashMap<String, Long>()

    // Naechste gesetzliche Feiertage (Nager.Date), einen Tag gespeichert, ohne Netz der letzte Stand.
    // Laender ohne Daten (404) merken sich "[]".
    suspend fun globusFeiertage(a2: String): List<de.troetenrepublik.app.engine.LaenderZusatz.Feiertag> {
        val lm = de.troetenrepublik.app.engine.LaenderZusatz
        val jetzt = System.currentTimeMillis()
        val datei = de.troetenrepublik.app.plattform.SichereDatei(java.io.File(mehrOrdner, "feiertage_" + a2.replace(Regex("[^A-Za-z]"), "_") + ".json"))
        val alt = withContext(Dispatchers.IO) { runCatching { lm.rohLesen(datei.lesen().toString(Charsets.UTF_8)) }.getOrNull() }
        var roh = alt
        if (alt == null || !lm.gueltig(alt.abgerufen, jetzt, lm.FEIERTAGE_MS)) {
            val schluessel = "feiertage$a2"
            if (jetzt - (mehrFehlerAm[schluessel] ?: 0L) >= 3_600_000L) {
                val a = runCatching { stimmungNetz.anfrage("https://date.nager.at/api/v3/NextPublicHolidays/$a2", max = 300_000) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()
                val text = when {
                    a == null -> null
                    a.ok -> a.text
                    a.code == 404 || a.code == 204 -> "[]"
                    else -> null
                }
                if (text == null) mehrFehlerAm[schluessel] = jetzt
                else {
                    roh = de.troetenrepublik.app.engine.LaenderZusatz.Roh(jetzt, text)
                    withContext(Dispatchers.IO) { runCatching { datei.schreiben(lm.rohSchreiben(roh!!).toByteArray(Charsets.UTF_8)) } }
                }
            }
        }
        return roh?.let { lm.feiertage(it.text, java.time.LocalDate.now()) }.orEmpty()
    }

    // Die neuesten Schlagzeilen zum Land (Google-News-Suche nach Landesname und Wirtschaft in der App-Sprache), 30 Minuten
    // gespeichert, ohne Netz der letzte Stand. Nur Titel, Link, Zeit und Herausgeber.
    suspend fun globusMeldungen(a2: String, landName: String): List<de.troetenrepublik.app.engine.LaenderZusatz.Meldung> {
        val lm = de.troetenrepublik.app.engine.LaenderZusatz
        val jetzt = System.currentTimeMillis()
        val deutsch = settingsFlow.value.sprache.code == "de"
        val schluessel = "meldungen${a2}_${if (deutsch) "de" else "en"}"
        val datei = de.troetenrepublik.app.plattform.SichereDatei(java.io.File(mehrOrdner, schluessel.replace(Regex("[^A-Za-z_]"), "_") + ".json"))
        val alt = withContext(Dispatchers.IO) { runCatching { lm.meldungenLesen(datei.lesen().toString(Charsets.UTF_8)) }.getOrNull() }
        if (alt != null && lm.gueltig(alt.abgerufen, jetzt, lm.MELDUNGEN_MS)) return alt.liste
        if (jetzt - (mehrFehlerAm[schluessel] ?: 0L) < 600_000L) return alt?.liste.orEmpty()
        val text = runCatching {
            stimmungNetz.anfrage(de.troetenrepublik.app.market.Rss.googleSuche(lm.suchbegriff(landName, deutsch), deutsch), max = 2_000_000).takeIf { it.ok }?.text
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()
        val neu = text?.let { t ->
            withContext(Dispatchers.Default) {
                lm.neueste(de.troetenrepublik.app.market.Rss.lesen(t, "Google News", de.troetenrepublik.app.market.Rss.Kategorie.KONJUNKTUR, google = true).map {
                    de.troetenrepublik.app.engine.LaenderZusatz.Meldung(it.titel, it.link, it.zeit, it.herausgeber)
                })
            }
        }
        if (neu == null || neu.isEmpty()) { mehrFehlerAm[schluessel] = jetzt; return alt?.liste.orEmpty() }
        withContext(Dispatchers.IO) { runCatching { datei.schreiben(lm.meldungenSchreiben(de.troetenrepublik.app.engine.LaenderZusatz.Meldungen(jetzt, neu)).toByteArray(Charsets.UTF_8)) } }
        return neu
    }

    // [Durchbrueche]
    private val durchbruecheSpeicher = ConcurrentHashMap<String, de.troetenrepublik.app.engine.Durchbrueche.Stand>()
    private val durchbruecheFehlerAm = ConcurrentHashMap<String, Long>()
    private val durchbruecheSperre = kotlinx.coroutines.sync.Mutex()
    private val durchbruecheParallel = kotlinx.coroutines.sync.Semaphore(3)

    // Letzter Stand im Speicher, damit die Ansicht beim erneuten Sichtbarwerden nicht leer anfaengt.
    fun durchbruecheStand(): List<de.troetenrepublik.app.engine.Durchbrueche.Meldung>? =
        durchbruecheSpeicher[if (settingsFlow.value.sprache.code == "de") "de" else "en"]?.liste

    // Aktualisierung der Durchbrueche im Hintergrund: die Lage zeigt den letzten Stand und wartet nicht auf die vielen Abrufe.
    fun durchbruecheImHintergrund() { scope.launch { runCatching { durchbrueche() } } }

    // Durchbrueche je Thema: Google-News-Suche je Thema (deutsch zusaetzlich englisch) und vier Wissenschaftsfeeds, hoechstens
    // drei Abrufe gleichzeitig, eine Stunde gespeichert, ohne Netz der letzte Stand. Nur Titel, Link, Zeit und Herausgeber.
    suspend fun durchbrueche(): List<de.troetenrepublik.app.engine.Durchbrueche.Meldung>? {
        val db = de.troetenrepublik.app.engine.Durchbrueche
        val deutsch = settingsFlow.value.sprache.code == "de"
        val schluessel = if (deutsch) "de" else "en"
        return durchbruecheSperre.withLock {
            val jetzt = System.currentTimeMillis()
            val datei = de.troetenrepublik.app.plattform.SichereDatei(java.io.File(mehrOrdner, "durchbrueche3_$schluessel.json"))
            val alt = durchbruecheSpeicher[schluessel]
                ?: withContext(Dispatchers.IO) { runCatching { db.lesenGespeichert(datei.lesen().toString(Charsets.UTF_8)) }.getOrNull() }?.also { durchbruecheSpeicher[schluessel] = it }
            if (alt != null && db.gueltig(alt.abgerufen, jetzt)) return@withLock alt.liste
            if (jetzt - (durchbruecheFehlerAm[schluessel] ?: 0L) < 600_000L) return@withLock alt?.liste
            val kopf = mapOf("Accept" to "application/rss+xml, application/xml, text/xml, */*")
            val rss = de.troetenrepublik.app.market.Rss
            suspend fun holen(url: String): String? = durchbruecheParallel.withPermit {
                runCatching { stimmungNetz.anfrage(url, kopf, max = 2_000_000).takeIf { it.ok }?.text }
                    .onFailure { if (it is CancellationException) throw it }.getOrNull()
            }
            val gescheitert = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
            var antworten = 0
            val rohe = kotlinx.coroutines.coroutineScope {
                val suchen = db.THEMEN.flatMap { t ->
                    (if (deutsch) listOf(true, false) else listOf(false)).map { sprache ->
                        async {
                            val text = holen(rss.googleSuche(db.suchbegriff(t, sprache), sprache))
                            if (text == null) { gescheitert += t.id; emptyList() } else {
                                synchronized(this@AppState) { antworten++ }
                                withContext(Dispatchers.Default) {
                                    rss.lesen(text, "Google News", de.troetenrepublik.app.market.Rss.Kategorie.UNTERNEHMEN, google = true).map { de.troetenrepublik.app.engine.Durchbrueche.Roh(t.id, it.titel, it.link, it.zeit, it.herausgeber) }
                                }
                            }
                        }
                    }
                }
                val feeds = db.FEEDS.map { f ->
                    async {
                        val text = holen(f.adresse) ?: return@async emptyList()
                        synchronized(this@AppState) { antworten++ }
                        withContext(Dispatchers.Default) {
                            rss.lesen(text, f.name, de.troetenrepublik.app.market.Rss.Kategorie.UNTERNEHMEN).map { de.troetenrepublik.app.engine.Durchbrueche.Roh(null, it.titel, it.link, it.zeit, f.name) }
                        }
                    }
                }
                (suchen + feeds).flatMap { it.await() }
            }
            if (antworten == 0) { durchbruecheFehlerAm[schluessel] = jetzt; return@withLock alt?.liste }
            val frisch = withContext(Dispatchers.Default) { db.filtern(rohe, jetzt) }
            // Themen ohne neue Meldung fragen zusaetzlich die weite Suche (ein Jahr) ab; deren aeltere Treffer erscheinen als „Zuletzt“
            val weite = kotlinx.coroutines.coroutineScope {
                db.ohneNeue(frisch).filter { it.id !in gescheitert }.flatMap { t ->
                    (if (deutsch) listOf(true, false) else listOf(false)).map { sprache ->
                        async {
                            val text = holen(rss.googleSuche(db.suchbegriffWeit(t, sprache), sprache)) ?: return@async emptyList()
                            withContext(Dispatchers.Default) {
                                rss.lesen(text, "Google News", de.troetenrepublik.app.market.Rss.Kategorie.UNTERNEHMEN, google = true).map { de.troetenrepublik.app.engine.Durchbrueche.Roh(t.id, it.titel, it.link, it.zeit, it.herausgeber) }
                            }
                        }
                    }
                }.flatMap { it.await() }
            }
            val aelter = withContext(Dispatchers.Default) { db.aeltere(weite, jetzt, frisch) }
            val neu = (frisch + aelter).sortedByDescending { it.zeit }
            // Themen, bei denen eine Suche scheiterte, behalten ihre letzten Meldungen zusaetzlich zu den neuen
            val liste = db.zusammenfuehren(neu, alt?.liste.orEmpty(), gescheitert, jetzt)
            val stand = de.troetenrepublik.app.engine.Durchbrueche.Stand(jetzt, liste)
            durchbruecheSpeicher[schluessel] = stand
            withContext(Dispatchers.IO) { runCatching { datei.schreiben(db.schreiben(stand).toByteArray(Charsets.UTF_8)) } }
            liste
        }
    }

    // [Durchbrueche 0.70]
    // Kuratierter Inhalt (assets/durchbrueche/inhalt.json), einmal gelesen.
    private var durchbruecheInhaltStand: de.troetenrepublik.app.engine.Durchbrueche.Inhalt? = null
    fun durchbruecheInhaltBekannt(): de.troetenrepublik.app.engine.Durchbrueche.Inhalt? = durchbruecheInhaltStand

    suspend fun durchbruecheInhalt(): de.troetenrepublik.app.engine.Durchbrueche.Inhalt? {
        durchbruecheInhaltStand?.let { return it }
        val x = withContext(Dispatchers.Default) {
            runCatching { de.troetenrepublik.app.engine.Durchbrueche.lesenInhalt(de.troetenrepublik.app.market.Daten.text(de.troetenrepublik.app.engine.Durchbrueche.INHALT_DATEI)) }.getOrNull()
        }
        if (x != null) durchbruecheInhaltStand = x
        return x
    }

    // Tageskurse der Fonds eines geoeffneten Themas, fuenf Minuten zwischengespeichert; fehlende Werte fehlen.
    suspend fun durchbruchKurse(symbole: List<String>): Map<String, Quote> = runCatching { market.kurseRoh(symbole) }
        .onFailure { if (it is CancellationException) throw it }.getOrDefault(emptyMap())
    // [Engpaesse]
    // Durchfahrten an den Engpaessen des Seehandels (IMF PortWatch, ein Tag gespeichert), ENSO (NOAA, eine Woche) und
    // Schlagzeilen je Engpass (Google News, eine Stunde). Abgerufen wird erst, wenn die Ebene oder der Kasten zu sehen ist;
    // ohne Netz oder bei Fehlern der letzte Stand.
    private val engpassOrdner by lazy { java.io.File(store.ordner, "risiken").apply { mkdirs() } }

    // Gespeicherter Stand ohne Netz (fuer die erste Anzeige)
    suspend fun engpassGespeichert(): de.troetenrepublik.app.engine.Engpaesse.Uebersicht? = withContext(Dispatchers.IO) {
        runCatching {
            de.troetenrepublik.app.engine.Laenderrisiken.lesen(
                de.troetenrepublik.app.engine.Engpaesse.Uebersicht.serializer(),
                de.troetenrepublik.app.plattform.SichereDatei(java.io.File(engpassOrdner, "engpass-uebersicht.json")).lesen().toString(Charsets.UTF_8),
            )
        }.getOrNull()
    }

    // Zwei Abrufe: die letzten Tage aller Engpaesse, dann das Vorjahresfenster um deren juengsten Tag. Fehlt das
    // Vorjahr (der Dienst begrenzt die Abrufe), bleibt der Stand ohne Vergleich und wird nach 30 Minuten neu versucht.
    suspend fun engpassUebersicht(): de.troetenrepublik.app.engine.Engpaesse.Uebersicht? = risikoLocks.getOrPut("engpass-uebersicht") { kotlinx.coroutines.sync.Mutex() }.withLock {
        val e = de.troetenrepublik.app.engine.Engpaesse
        val jetzt = System.currentTimeMillis()
        val alt = engpassGespeichert()
        if (alt != null && jetzt >= alt.abgerufen && jetzt - alt.abgerufen < e.GUELTIG_MS) return@withLock alt
        if (jetzt - (risikoFehlerAm["engpass-uebersicht"] ?: 0L) < 1_800_000L) return@withLock alt
        suspend fun holen(adresse: String) = runCatching { stimmungNetz.anfrage(adresse, max = 3_000_000).takeIf { it.ok }?.text }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()
            ?.let { t -> withContext(Dispatchers.Default) { e.lesen(t) } }
        val neu = holen(e.adresseNeu(java.time.LocalDate.now(java.time.ZoneOffset.UTC)))
        val letzter = neu?.maxOfOrNull { it.tag.t }
        val vorjahr = if (letzter != null) holen(e.adresseVorjahr(letzter)) else null
        val stand = neu?.let { n -> withContext(Dispatchers.Default) { e.uebersicht(n, vorjahr, if (vorjahr == null) jetzt - e.GUELTIG_MS + 1_800_000L else jetzt) } }
        if (stand == null) { risikoFehlerAm["engpass-uebersicht"] = jetzt; return@withLock alt }
        if (vorjahr == null && alt != null && alt.lage.values.any { it.vn != null }) { risikoFehlerAm["engpass-uebersicht"] = jetzt; return@withLock alt }
        withContext(Dispatchers.IO) {
            runCatching {
                de.troetenrepublik.app.plattform.SichereDatei(java.io.File(engpassOrdner, "engpass-uebersicht.json"))
                    .schreiben(de.troetenrepublik.app.engine.Laenderrisiken.schreiben(de.troetenrepublik.app.engine.Engpaesse.Uebersicht.serializer(), stand).toByteArray(Charsets.UTF_8))
            }
        }
        stand
    }

    // Ein Jahr Durchfahrten eines Engpasses
    suspend fun engpassVerlauf(id: String): de.troetenrepublik.app.engine.Engpaesse.Verlauf? = risikoHolen(
        "engpass-$id".replace(Regex("[^A-Za-z0-9-]"), "_"), de.troetenrepublik.app.engine.Engpaesse.GUELTIG_MS,
        de.troetenrepublik.app.engine.Engpaesse.adresseVerlauf(id, java.time.LocalDate.now(java.time.ZoneOffset.UTC)), 2_000_000,
        de.troetenrepublik.app.engine.Engpaesse.Verlauf.serializer(), fehlerPauseMs = 1_800_000L,
    ) { t, jetzt -> de.troetenrepublik.app.engine.Engpaesse.lesen(t)?.let { de.troetenrepublik.app.engine.Engpaesse.verlauf(it, id, jetzt) } }

    suspend fun ensoStatus() = risikoHolen(
        "enso-status", de.troetenrepublik.app.engine.Enso.GUELTIG_MS, de.troetenrepublik.app.engine.Enso.ADRESSE_STATUS, 1_000_000,
        de.troetenrepublik.app.engine.Enso.Status.serializer(), lesen = de.troetenrepublik.app.engine.Enso::statusLesen,
    )

    suspend fun ensoIndex() = risikoHolen(
        "enso-index", de.troetenrepublik.app.engine.Enso.GUELTIG_MS, de.troetenrepublik.app.engine.Enso.ADRESSE_INDEX, 1_000_000,
        de.troetenrepublik.app.engine.Enso.Index.serializer(), lesen = de.troetenrepublik.app.engine.Enso::indexLesen,
    )

    // Spannungsgebiete: Berichtsdichte je Region (GDELT), sechs Stunden gespeichert. GDELT erlaubt eine Anfrage je fuenf
    // Sekunden, darum laufen die Abrufe nacheinander; die Oberflaeche ruft die Gebiete der Reihe nach auf und zeigt jedes,
    // sobald es da ist.
    private val gdeltSperre = kotlinx.coroutines.sync.Mutex()
    @Volatile private var gdeltLetzter = 0L

    // Gespeicherter Wert eines Spannungsgebiets ohne Abruf; null, wenn keiner da oder aelter als zwei Tage
    fun spannungGespeichert(id: String): de.troetenrepublik.app.engine.Spannungen.Wert? {
        val datei = de.troetenrepublik.app.plattform.SichereDatei(java.io.File(engpassOrdner, "spannung-$id.json"))
        val w = runCatching { de.troetenrepublik.app.engine.Laenderrisiken.lesen(de.troetenrepublik.app.engine.Spannungen.Wert.serializer(), datei.lesen().toString(Charsets.UTF_8)) }.getOrNull() ?: return null
        return w.takeIf { System.currentTimeMillis() - it.abgerufen < 2 * 86_400_000L }
    }

    suspend fun spannungWert(g: de.troetenrepublik.app.engine.Spannungen.Gebiet): de.troetenrepublik.app.engine.Spannungen.Wert? = gdeltSperre.withLock {
        val sp = de.troetenrepublik.app.engine.Spannungen
        val name = "spannung-" + g.id
        val datei = de.troetenrepublik.app.plattform.SichereDatei(java.io.File(engpassOrdner, "$name.json"))
        val jetzt = System.currentTimeMillis()
        val alt = withContext(Dispatchers.IO) {
            runCatching { de.troetenrepublik.app.engine.Laenderrisiken.lesen(de.troetenrepublik.app.engine.Spannungen.Wert.serializer(), datei.lesen().toString(Charsets.UTF_8)) }.getOrNull()
        }
        if (alt != null && jetzt >= alt.abgerufen && jetzt - alt.abgerufen < sp.GUELTIG_MS) return@withLock alt
        if (jetzt - (risikoFehlerAm[name] ?: 0L) < 900_000L) return@withLock alt
        val warte = sp.ABSTAND_MS - (System.currentTimeMillis() - gdeltLetzter)
        if (warte > 0) kotlinx.coroutines.delay(warte)
        val text = runCatching { stimmungNetz.anfrage(sp.adresse(g), max = 1_000_000).takeIf { it.ok }?.text }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()
        gdeltLetzter = System.currentTimeMillis()
        val neu = text?.let { t -> withContext(Dispatchers.Default) { sp.lesen(t)?.let { sp.bewerten(g.id, it, jetzt) } } }
        if (neu == null) { risikoFehlerAm[name] = jetzt; return@withLock alt }
        withContext(Dispatchers.IO) {
            runCatching { datei.schreiben(de.troetenrepublik.app.engine.Laenderrisiken.schreiben(de.troetenrepublik.app.engine.Spannungen.Wert.serializer(), neu).toByteArray(Charsets.UTF_8)) }
        }
        neu
    }

    // Tonlage der Berichte ueber eine Firma (GDELT, Modus timelinetone): sechs Stunden gespeichert, nacheinander mit demselben
    // Abstand von fuenf Sekunden wie die Spannungsgebiete. Ein Abruf je Firma; bei Fehlern gilt der alte Stand.
    suspend fun firmaTon(symbol: String, suche: String): de.troetenrepublik.app.engine.Ton.Wert? = gdeltSperre.withLock {
        val t = de.troetenrepublik.app.engine.Ton
        val name = "ton-" + symbol.replace(Regex("[^A-Za-z0-9_]"), "_")
        val datei = de.troetenrepublik.app.plattform.SichereDatei(java.io.File(engpassOrdner, "$name.json"))
        val jetzt = System.currentTimeMillis()
        val alt = withContext(Dispatchers.IO) {
            runCatching { de.troetenrepublik.app.engine.Laenderrisiken.lesen(de.troetenrepublik.app.engine.Ton.Wert.serializer(), datei.lesen().toString(Charsets.UTF_8)) }.getOrNull()
        }
        if (alt != null && jetzt >= alt.abgerufen && jetzt - alt.abgerufen < t.GUELTIG_MS) return@withLock alt
        if (jetzt - (risikoFehlerAm[name] ?: 0L) < 900_000L) return@withLock alt
        val warte = de.troetenrepublik.app.engine.Spannungen.ABSTAND_MS - (System.currentTimeMillis() - gdeltLetzter)
        if (warte > 0) kotlinx.coroutines.delay(warte)
        val text = runCatching { stimmungNetz.anfrage(t.adresse(suche), max = 1_000_000).takeIf { it.ok }?.text }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()
        gdeltLetzter = System.currentTimeMillis()
        val tage = text?.let { x -> withContext(Dispatchers.Default) { t.lesen(x) } }
        val neu = tage?.takeIf { it.size >= t.MIND_DAVOR + t.MIND_NEU }?.let { t.wert(it, jetzt) }
        if (neu == null) { risikoFehlerAm[name] = jetzt; return@withLock alt }
        withContext(Dispatchers.IO) {
            runCatching { datei.schreiben(de.troetenrepublik.app.engine.Laenderrisiken.schreiben(de.troetenrepublik.app.engine.Ton.Wert.serializer(), neu).toByteArray(Charsets.UTF_8)) }
        }
        neu
    }

    // Schlagzeilen zu einem Engpass: Google-News-Suche in der App-Sprache, eine Stunde gespeichert, nur Titel, Link, Zeit
    // und Herausgeber.
    suspend fun engpassMeldungen(id: String, begriff: String): List<de.troetenrepublik.app.engine.LaenderZusatz.Meldung> {
        val lm = de.troetenrepublik.app.engine.LaenderZusatz
        val jetzt = System.currentTimeMillis()
        val deutsch = settingsFlow.value.sprache.code == "de"
        val schluessel = "engpass${id}_${if (deutsch) "de" else "en"}"
        val datei = de.troetenrepublik.app.plattform.SichereDatei(java.io.File(mehrOrdner, schluessel.replace(Regex("[^A-Za-z0-9_]"), "_") + ".json"))
        val alt = withContext(Dispatchers.IO) { runCatching { lm.meldungenLesen(datei.lesen().toString(Charsets.UTF_8)) }.getOrNull() }
        if (alt != null && lm.gueltig(alt.abgerufen, jetzt, 3_600_000L)) return alt.liste
        if (jetzt - (mehrFehlerAm[schluessel] ?: 0L) < 600_000L) return alt?.liste.orEmpty()
        val text = runCatching {
            stimmungNetz.anfrage(de.troetenrepublik.app.market.Rss.googleSuche(begriff, deutsch), max = 2_000_000).takeIf { it.ok }?.text
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()
        val neu = text?.let { t ->
            withContext(Dispatchers.Default) {
                lm.neueste(de.troetenrepublik.app.market.Rss.lesen(t, "Google News", de.troetenrepublik.app.market.Rss.Kategorie.KONJUNKTUR, google = true).map {
                    de.troetenrepublik.app.engine.LaenderZusatz.Meldung(it.titel, it.link, it.zeit, it.herausgeber)
                })
            }
        }
        if (neu == null || neu.isEmpty()) { mehrFehlerAm[schluessel] = jetzt; return alt?.liste.orEmpty() }
        withContext(Dispatchers.IO) { runCatching { datei.schreiben(lm.meldungenSchreiben(de.troetenrepublik.app.engine.LaenderZusatz.Meldungen(jetzt, neu)).toByteArray(Charsets.UTF_8)) } }
        return neu
    }

    // Aeltere Schlagzeilen einer Art (Zahlen, Analyst, Uebernahme, Kapitalmassnahme) bis 30 Tage zurueck: Google-News-Suche
    // nach den Stichworten der Art in vier Zeitabschnitten, eine Stunde gespeichert, ohne Netz der letzte Stand.
    suspend fun newsArchiv(art: de.troetenrepublik.app.engine.NachrichtArt.Art): List<Rss.Eintrag> {
        val nl = de.troetenrepublik.app.engine.NachrichtenListe
        val lm = de.troetenrepublik.app.engine.LaenderZusatz
        val jetzt = System.currentTimeMillis()
        val deutsch = settingsFlow.value.sprache.code == "de"
        val schluessel = "archiv${art.name}_${if (deutsch) "de" else "en"}"
        val datei = de.troetenrepublik.app.plattform.SichereDatei(java.io.File(mehrOrdner, schluessel.replace(Regex("[^A-Za-z_]"), "_") + ".json"))
        val alt = withContext(Dispatchers.IO) { runCatching { lm.meldungenLesen(datei.lesen().toString(Charsets.UTF_8)) }.getOrNull() }
        if (alt != null && lm.gueltig(alt.abgerufen, jetzt, 3_600_000L)) return nl.archivEintraege(alt.liste, art, jetzt)
        if (jetzt - (mehrFehlerAm[schluessel] ?: 0L) < 600_000L) return alt?.let { nl.archivEintraege(it.liste, art, jetzt) }.orEmpty()
        val gelesen = ArrayList<de.troetenrepublik.app.engine.LaenderZusatz.Meldung>()
        for (adresse in nl.archivAdressen(art, deutsch, java.time.LocalDate.now())) {
            val text = runCatching { stimmungNetz.anfrage(adresse, max = 2_000_000).takeIf { it.ok }?.text }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull() ?: continue
            withContext(Dispatchers.Default) {
                Rss.lesen(text, "Google News", Rss.Kategorie.UNTERNEHMEN, google = true).forEach { gelesen += nl.alsMeldung(it) }
            }
            kotlinx.coroutines.delay(300)
        }
        val liste = nl.archivEintraege(gelesen, art, jetzt)
        if (liste.isEmpty()) { mehrFehlerAm[schluessel] = jetzt; return alt?.let { nl.archivEintraege(it.liste, art, jetzt) }.orEmpty() }
        withContext(Dispatchers.IO) { runCatching { datei.schreiben(lm.meldungenSchreiben(de.troetenrepublik.app.engine.LaenderZusatz.Meldungen(jetzt, liste.map { nl.alsMeldung(it) })).toByteArray(Charsets.UTF_8)) } }
        return liste
    }

    // Juengster Bericht zu einem Boersengang: Google-News-Suche nach Firmenname und IPO in der App-Sprache, nur
    // Schlagzeilen, die die Firma nennen; eine Stunde gespeichert, ohne Netz der letzte Stand. null ohne Treffer.
    suspend fun ipoBericht(name: String): de.troetenrepublik.app.engine.LaenderZusatz.Meldung? {
        val iu = de.troetenrepublik.app.engine.IpoUebersicht
        val lm = de.troetenrepublik.app.engine.LaenderZusatz
        val kern = iu.firmenKern(name)
        if (kern.length < 3) return null
        val jetzt = System.currentTimeMillis()
        val deutsch = settingsFlow.value.sprache.code == "de"
        val schluessel = "ipobericht_" + kern.lowercase().replace(Regex("[^a-z0-9]"), "_") + "_" + kern.lowercase().hashCode().toUInt() + "_" + (if (deutsch) "de" else "en")
        val datei = de.troetenrepublik.app.plattform.SichereDatei(java.io.File(mehrOrdner, schluessel + ".json"))
        val alt = withContext(Dispatchers.IO) { runCatching { lm.meldungenLesen(datei.lesen().toString(Charsets.UTF_8)) }.getOrNull() }
        if (alt != null && lm.gueltig(alt.abgerufen, jetzt, iu.BERICHT_HALTBAR_MS)) return iu.juengste(alt.liste, name)
        if (jetzt - (mehrFehlerAm[schluessel] ?: 0L) < 600_000L) return alt?.let { iu.juengste(it.liste, name) }
        val text = runCatching {
            stimmungNetz.anfrage(Rss.googleSuche(iu.suchtext(name), deutsch), max = 2_000_000).takeIf { it.ok }?.text
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()
        if (text == null) { mehrFehlerAm[schluessel] = jetzt; return alt?.let { iu.juengste(it.liste, name) } }
        val liste = withContext(Dispatchers.Default) {
            Rss.lesen(text, "Google News", Rss.Kategorie.UNTERNEHMEN, google = true)
                .map { de.troetenrepublik.app.engine.LaenderZusatz.Meldung(it.titel, it.link, it.zeit, it.herausgeber) }
                .filter { iu.nameTrifft(it.titel, name) }
                .sortedByDescending { it.zeit }.take(10)
        }
        withContext(Dispatchers.IO) { runCatching { datei.schreiben(lm.meldungenSchreiben(de.troetenrepublik.app.engine.LaenderZusatz.Meldungen(jetzt, liste)).toByteArray(Charsets.UTF_8)) } }
        return iu.juengste(liste, name)
    }

    private var depotNewsCache: Triple<String, Long, List<Rss.Eintrag>>? = null

    // Nachrichten je Wert aus dem Depot (die der Wertpapierseite, Yahoo und Google), die 20 groessten Positionen ohne
    // Derivate, 30 Minuten gespeichert. Hoechstens vier Abrufe zugleich, also zwei Werte je Gruppe.
    suspend fun depotNachrichten(): List<Rss.Eintrag> {
        val positionen = account.value.positions.filter { !it.isDerivative && !it.isKnockedOut }.sortedByDescending { it.invested }.take(20)
        if (positionen.isEmpty()) return emptyList()
        val sprache = settingsFlow.value.sprache.code
        val schluessel = positionen.joinToString("|") { it.symbol } + "|" + sprache
        val jetzt = System.currentTimeMillis()
        depotNewsCache?.takeIf { it.first == schluessel && jetzt - it.second < 30 * 60_000L }?.let { return it.third }
        val alle = positionen.chunked(de.troetenrepublik.app.engine.WertNachrichten.werteJeGruppe()).flatMap { gruppe ->
            coroutineScope {
                gruppe.map { p -> async { runCatching { newsFor(p.symbol, newsName(p.symbol, p.name)) }.getOrDefault(emptyList()) } }.awaitAll().flatten()
            }
        }
        val liste = alle.map { de.troetenrepublik.app.engine.WertNachrichten.eintrag(it) }
        if (liste.isNotEmpty()) depotNewsCache = Triple(schluessel, jetzt, liste)
        return liste
    }

    private var redditCache: Pair<Long, List<MarketApi.Erwaehnung>>? = null
    private var branchenCache: Pair<Long, Map<String, List<Candle>>>? = null

    private val redditCaches = ConcurrentHashMap<String, Pair<Long, List<MarketApi.Erwaehnung>>>()

    fun redditAktiv(s: de.troetenrepublik.app.data.Settings = settingsFlow.value): Boolean = s.keyRedditId.isNotBlank()

    private val redditBeitragCache = ConcurrentHashMap<String, Pair<Long, List<de.troetenrepublik.app.engine.RedditAuswertung.Beitrag>>>()

    // Neueste Reddit-Beitraege zu einem Wert: Suche nach Kuerzel und Name in den
    // deutschen und einigen englischen Boersen-Subreddits im letzten Monat, 15 Minuten zwischengespeichert. null ohne
    // Zugang oder ohne Antwort.
    suspend fun redditBeitraege(symbol: String, name: String): List<de.troetenrepublik.app.engine.RedditAuswertung.Beitrag>? {
        val s = settingsFlow.value
        if (!redditAktiv(s)) return null
        val now = System.currentTimeMillis()
        redditBeitragCache[symbol]?.takeIf { now - it.first < 15 * 60_000L }?.let { return it.second }
        val ra = de.troetenrepublik.app.engine.RedditAuswertung
        val url = de.troetenrepublik.app.market.RedditApi.suchAdresse(ra.DEUTSCHE_SUBS + ra.ENGLISCHE_SUBS, de.troetenrepublik.app.market.RedditApi.frage(symbol, name))
        val roh = reddit.liste(url, s.keyRedditId, s.keyRedditSecret) ?: return redditBeitragCache[symbol]?.second
        // Nur Beitraege, die den Wert nennen; die Suche von Reddit findet auch Teilwoerter
        val k = de.troetenrepublik.app.engine.RedditAuswertung.Sucher(de.troetenrepublik.app.engine.RedditAuswertung.Kandidat(symbol, name))
        val liste = withContext(Dispatchers.Default) { roh.filter { k.findet(it.titel + "\n" + it.text) }.sortedByDescending { it.zeit } }
        redditBeitragCache[symbol] = now to liste
        return liste
    }

    // Zuletzt gelesene Beitraege der deutschen Subreddits.
    @Volatile private var redditDeutschBeitraege: List<de.troetenrepublik.app.engine.RedditAuswertung.Beitrag> = emptyList()

    // Alle Stimmungsbarometer; null, wo die Quelle nicht antwortet.
    // aktien: eigener Angst-Gier-Index (5.94); cnn: Fear & Greed von CNN.
    data class Stimmungen(
        val aktien: de.troetenrepublik.app.engine.Stimmungsbarometer.Barometer?,
        val krypto: de.troetenrepublik.app.engine.Stimmungsbarometer.Barometer?,
        val wsb: de.troetenrepublik.app.engine.Stimmungsbarometer.RedditBarometer?,
        // Sorgen auf Wikipedia (seit 5.92).
        val sorgen: de.troetenrepublik.app.engine.Stimmungsbarometer.Barometer? = null,
        // Schwankung des VIX gegenueber dem letzten Jahr (seit 5.93).
        val vix: de.troetenrepublik.app.engine.Stimmungsbarometer.Barometer? = null,
        // Sichere Haefen: Aktien gegen Staatsanleihen (seit 5.93).
        val sicher: de.troetenrepublik.app.engine.Stimmungsbarometer.Barometer? = null,
        val cnn: de.troetenrepublik.app.engine.Stimmungsbarometer.Barometer? = null,
        // AAII-Umfrage unter US-Privatanlegern.
        val aaii: de.troetenrepublik.app.engine.Stimmungsbarometer.AaiiBarometer? = null,
    ) {
        // Fuer Widget und Blasen: CNN, sonst der eigene Index
        val aktienBarometer get() = cnn ?: aktien
    }

    private val stimmungNetz by lazy { MarketApi() }

    // Fruehe Signale aus oeffentlichen Daten (5.92): Oel- und Gaslager, Erstantraege, Fluggaeste, Duerre, mit eigenem
    // Schluessel die EU-Gasspeicher. Laedt nur die Quellen der gefragten Schluessel.
    private val fruehNetz by lazy { de.troetenrepublik.app.market.FruehSignale(stimmungNetz) }
    suspend fun fruehSignale(schluessel: Collection<String>, neu: Boolean = false): Map<String, de.troetenrepublik.app.engine.Fruehindikatoren.Signal> =
        fruehNetz.signale(schluessel, settingsFlow.value.keyAgsi, neu)

    // Ob die fruehen Signale schon im Preis stecken (5.92), je Schluessel; getreide: Kuerzel fuer die Duerre.
    suspend fun fruehImPreis(schluessel: Collection<String>, getreide: String? = null, neu: Boolean = false): Map<String, de.troetenrepublik.app.engine.Fruehindikatoren.ImPreis> =
        fruehNetz.imPreis(schluessel, settingsFlow.value.keyAgsi, getreide, neu)

    // Strom in Europa: Day-Ahead-Preise je Land mit dem eigenen Token von ENTSO-E, Erzeugung und Verbrauch in Deutschland
    // aus SMARD ohne Schluessel. Beides liegt zuletzt abgerufen im Ordner "strom" (market/StromAbruf.kt).
    private val stromNetz by lazy { de.troetenrepublik.app.market.StromAbruf(stimmungNetz, java.io.File(store.ordner, "strom"), kontingent) }
    fun stromPreiseGespeichert(a2: String) = stromNetz.gespeichertePreise(a2)
    fun stromErzeugungGespeichert() = stromNetz.gespeicherteErzeugung()
    suspend fun stromPreise(a2: String, neu: Boolean = false) = stromNetz.preise(a2, settingsFlow.value.keyEntsoe, neu)
    suspend fun stromErzeugung(neu: Boolean = false) = stromNetz.erzeugung(neu)

    @Volatile private var stimmungCache: Pair<Long, Stimmungen>? = null

    // Fear & Greed von CNN und eigener Angst-Gier-Index (Aktien), alternative.me (Krypto), AAII-Umfrage, die Stimmung auf
    // r/wallstreetbets (Tradestie), sichere Haefen und Schwankung; 30 Minuten zwischengespeichert. Ein Dienst, der nicht
    // antwortet, behaelt seinen letzten Wert.
    suspend fun stimmungen(neu: Boolean = false): Stimmungen {
        val now = System.currentTimeMillis()
        stimmungCache?.takeIf { !neu && now - it.first < 30 * 60_000L }?.let { return it.second }
        val sb = de.troetenrepublik.app.engine.Stimmungsbarometer
        val alt = stimmungCache?.second
        val s = coroutineScope {
            // Kurse fuer Index, sichere Haefen und Schwankung in einem Abruf (Yahoo), dazu der Finanzstress von FRED
            val kurse = async {
                runCatching { market.schlusskurse(listOf("^GSPC", "IEF", "^VIX", "RSP", "SPY"), "2y") }.getOrDefault(emptyMap())
                    .mapValues { (_, l) -> l.map { it.t to it.close } }
            }
            val stress = async {
                runCatching {
                    val adresse = de.troetenrepublik.app.engine.Fruehwarnung.stressAdresse(java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusYears(2))
                    de.troetenrepublik.app.market.FredAbruf.geteilt(adresse) { stimmungNetz.anfrage(adresse, mapOf("Accept" to "text/csv")) }
                        .takeIf { it.ok }?.text?.let(de.troetenrepublik.app.engine.Fruehindikatoren::fredLesen)?.map { (tag, w) -> tag * 86_400_000L to w }
                }.getOrNull().orEmpty()
            }
            val aktien = async {
                val k = kurse.await()
                val g = k["^GSPC"].orEmpty()
                sb.angstGier(mapOf(
                    "momentum" to sb.rangReihe(sb.abstandZurLinie(g), gier = true),
                    "breite" to sb.rangReihe(sb.vorsprung(k["RSP"].orEmpty(), k["SPY"].orEmpty()), gier = true),
                    "vix" to sb.rangReihe(k["^VIX"].orEmpty(), gier = false),
                    "stress" to sb.rangReihe(stress.await(), gier = false, fenster = 52, mindestens = 52),
                    "sicher" to sb.rangReihe(sb.vorsprung(g, k["IEF"].orEmpty()), gier = true),
                ))
            }
            // CNN mit der eigenen Seite als Referer und BROWSER_UA
            val cnn = async {
                stimmungNetz.anfrage(sb.CNN, mapOf("Referer" to sb.CNN_SEITE, "User-Agent" to MarketApi.BROWSER_UA)).text?.let(sb::cnnLesen)
            }
            val aaii = async {
                stimmungNetz.anfrage(de.troetenrepublik.app.engine.Fruehwarnung.AAII, mapOf("Accept" to "text/html")).text
                    ?.let { sb.aaiiBarometer(de.troetenrepublik.app.engine.Fruehwarnung.aaiiLesen(it, now)) }
            }
            val krypto = async { stimmungNetz.anfrage(sb.kryptoAdresse(de.troetenrepublik.app.engine.KryptoArchiv.limit(now))).text?.let(sb::kryptoLesen) }
            val wsb = async {
                val roh = stimmungNetz.anfrage(sb.WSB).text?.let(sb::wsbLesen) ?: return@async null
                // Woerter heraus, Gattungen einer Firma zusammen (5.94)
                val werte = sb.wsbBereinigt(roh) { t -> de.troetenrepublik.app.market.Symbols.find(t)?.name }
                // Kurse der meistbesprochenen Werte fuer die Aenderung ueber fuenf Handelstage (seit 5.93), seit 5.94 fuer
                // alle Werte, die in den drei Listen stehen koennen
                val gezeigt = werte.take(25).map { it.ticker }
                // Bereich "1mo" wie bei der Heatmap; Werte ohne Antwort laesst schlusskurse einzeln weg; eine Abfrage nimmt hoechstens 20 Werte
                val kurse = if (gezeigt.isEmpty()) emptyMap() else runCatching {
                    val alle = HashMap<String, List<de.troetenrepublik.app.data.Candle>>()
                    for (gruppe in gezeigt.chunked(20)) alle.putAll(market.schlusskurse(gruppe, "1mo"))
                    alle
                }.getOrDefault(emptyMap())
                // Schluessel gross wie die Ticker aus wsbLesen
                val aenderungen = kurse.mapNotNull { (t, l) -> sb.aenderung(l.map { it.t to it.close })?.let { t.uppercase() to it } }.toMap()
                sb.wsbMitLage(werte, aenderungen, now)
            }
            // Sichere Haefen: S&P 500 gegen US-Staatsanleihen (IEF) von Yahoo (seit 5.93)
            val sicher = async { kurse.await().let { k -> sb.sicherBarometer(k["^GSPC"].orEmpty(), k["IEF"].orEmpty()) } }
            // Schwankungsbarometer aus dem VIX von Yahoo (seit 5.93)
            val vix = async { kurse.await()["^VIX"]?.let(sb::vixBarometer) }
            Stimmungen(
                runCatching { aktien.await() }.getOrNull() ?: alt?.aktien,
                runCatching { krypto.await() }.getOrNull() ?: alt?.krypto,
                runCatching { wsb.await() }.getOrNull() ?: alt?.wsb,
                vix = runCatching { vix.await() }.getOrNull() ?: alt?.vix,
                sicher = runCatching { sicher.await() }.getOrNull() ?: alt?.sicher,
                cnn = runCatching { cnn.await() }.getOrNull() ?: alt?.cnn,
                aaii = runCatching { aaii.await() }.getOrNull() ?: alt?.aaii,
            )
        }
        if (s.aktien != null || s.krypto != null || s.wsb != null || s.vix != null || s.sicher != null || s.cnn != null || s.aaii != null) stimmungCache = now to s
        return s
    }

    @Volatile private var fruehwarnCache: Pair<Long, List<de.troetenrepublik.app.engine.Fruehwarnung.Zeichen>>? = null

    // Fruehwarnzeichen: US-Zinskurve vom US-Finanzministerium (dieses und voriges Jahr), Arbeitslosenquote
    // der BLS fuer die Sahm-Regel, VIX, VIX3M, S&P 500 und DAX von Yahoo; sechs Stunden zwischengespeichert. Zeichen ohne
    // Daten fehlen.
    @Volatile private var positionCache: Pair<Long, List<de.troetenrepublik.app.engine.Positionierung.Lage>>? = null

    // Abrufe einzelner Maerkte der Positionierung, zwoelf Stunden und nur fuer dieselbe Schluesselmenge gueltig
    @Volatile private var positionTeilCache: Pair<Long, Pair<Set<String>, List<de.troetenrepublik.app.engine.Positionierung.Lage>>>? = null

    // Positionierung grosser Spekulanten an den Terminmaerkten (CFTC, woechentlich), zwoelf Stunden zwischengespeichert.
    // nur: wenn gesetzt, werden nur diese Maerkte gebraucht; ein frischer Vollbestand wird gefiltert, sonst fuellt ein
    // eigener Zwischenspeicher, positionCache bleibt dabei unberuehrt.
    suspend fun positionierung(neu: Boolean = false, nur: Set<String>? = null): List<de.troetenrepublik.app.engine.Positionierung.Lage> {
        val now = System.currentTimeMillis()
        if (nur == null) {
            positionCache?.takeIf { !neu && now - it.first < 12 * 3_600_000L }?.let { return it.second }
        } else {
            positionCache?.takeIf { !neu && now - it.first < 12 * 3_600_000L }?.let { return it.second.filter { l -> l.schluessel in nur } }
            positionTeilCache?.takeIf { !neu && now - it.first < 12 * 3_600_000L && it.second.first == nur }?.let { return it.second.second }
        }
        // Seit 0.78: Archiv aus dem Datenbuendel und nur die neuen Wochen in einer Abfrage (HistorieAbruf.kt)
        val liste = positionierungLaden(nur)
        if (liste.isNotEmpty()) {
            if (nur == null) positionCache = now to liste
            else positionTeilCache = now to (nur to liste)
        }
        val alt = if (nur == null) positionCache?.second else positionTeilCache?.second?.second
        return liste.ifEmpty { alt.orEmpty() }
    }

    @Volatile private var phasenCache: Pair<Long, List<de.troetenrepublik.app.engine.Marktphase.Phase>>? = null

    // Bullen- oder Baerenmarkt der grossen Indizes aus zehn Jahren Tagesschlusskursen, sechs Stunden zwischengespeichert.
    suspend fun marktphase(neu: Boolean = false): List<de.troetenrepublik.app.engine.Marktphase.Phase> {
        val now = System.currentTimeMillis()
        phasenCache?.takeIf { !neu && now - it.first < 6 * 3_600_000L }?.let { return it.second }
        val symbole = de.troetenrepublik.app.engine.Marktphase.INDIZES.map { it.second }
        val kurse = runCatching { market.schlusskurse(symbole, "10y") }.getOrDefault(emptyMap())
            .takeIf { it.size >= symbole.size / 2 } ?: runCatching { market.schlusskurse(symbole, "5y") }.getOrDefault(emptyMap())
        val liste = de.troetenrepublik.app.engine.Marktphase.INDIZES.mapNotNull { (k, s) -> kurse[s]?.let { l -> de.troetenrepublik.app.engine.Marktphase.phase(k, l.map { it.t to it.close }) } }
        if (liste.isNotEmpty()) phasenCache = now to liste
        return liste.ifEmpty { phasenCache?.second.orEmpty() }
    }

    // Fruehwarnzeichen des letzten Abrufs, ohne neu zu laden
    fun fruehwarnungBekannt(): List<de.troetenrepublik.app.engine.Fruehwarnung.Zeichen> = fruehwarnCache?.second.orEmpty()

    suspend fun fruehwarnung(neu: Boolean = false): List<de.troetenrepublik.app.engine.Fruehwarnung.Zeichen> {
        val now = System.currentTimeMillis()
        val fw = de.troetenrepublik.app.engine.Fruehwarnung
        // FRED verbietet das Zwischenspeichern: Stress und Hochzins werden bei jedem Aufruf neu geholt, der Speicher
        // haelt nur die Zeichen anderer Quellen
        fruehwarnCache?.takeIf { !neu && now - it.first < 6 * 3_600_000L }?.let { c ->
            val fred = fredWarnzeichen(fw)
            return fw.ALLE.mapNotNull { s -> c.second.firstOrNull { it.schluessel == s } ?: fred.firstOrNull { it.schluessel == s } }
        }
        val jahr = fw.jahrMonat(now).first
        val zeichen = coroutineScope {
            val kurve = async { treasuryTexte(jahr, neu).flatMap(fw::treasuryLesen).sortedBy { it.first } }
            val arbeit = async {
                val ko = de.troetenrepublik.app.market.Abrufkontingent
                if (!kontingent.erlaubt(ko.BLS)) emptyList() else {
                    kontingent.buchen(ko.BLS)
                    stimmungNetz.anfrage(fw.BLS).text?.let(fw::blsLesen).orEmpty()
                }
            }
            val kurse = async { runCatching { market.schlusskurse(listOf("^VIX", "^VIX3M", "^GSPC", "^GDAXI"), "2y") }.getOrDefault(emptyMap()) }
            val fredZeichen = async { fredWarnzeichen(fw) }
            val k = kurse.await().mapValues { (_, l) -> l.map { it.t to it.close } }
            listOfNotNull(
                runCatching { fw.zinskurve(kurve.await()) }.getOrNull(),
                runCatching { fw.sahm(arbeit.await())?.copy(abgerufen = now) }.getOrNull(),
                k["^VIX"]?.let(fw::vix),
                k["^VIX"]?.let { v -> k["^VIX3M"]?.let { fw.vixStruktur(v, it) } },
                k["^VIX"]?.let { v -> k["^GSPC"]?.let { fw.vixAbstand(v, it) } },
                k["^GSPC"]?.let { fw.trend(fw.TREND_SP500, it) },
                k["^GDAXI"]?.let { fw.trend(fw.TREND_DAX, it) },
            ) + fredZeichen.await()
        }
        // Was diesmal fehlt, bleibt vom letzten Abruf
        val alt = fruehwarnCache?.second.orEmpty()
        val zusammen = fw.ALLE.mapNotNull { s -> zeichen.firstOrNull { it.schluessel == s } ?: alt.firstOrNull { it.schluessel == s } }
        if (zusammen.isNotEmpty()) fruehwarnCache = now to zusammen.filter { it.schluessel !in FRED_WARNZEICHEN }
        return zusammen
    }

    // Warnzeichen aus FRED-Reihen (Finanzstress, Hochzinsaufschlag): nur fuer die Dauer des Aufrufs, nie gespeichert
    private val FRED_WARNZEICHEN = setOf(de.troetenrepublik.app.engine.Fruehwarnung.STRESS, de.troetenrepublik.app.engine.Fruehwarnung.HOCHZINS)

    private suspend fun fredWarnzeichen(fw: de.troetenrepublik.app.engine.Fruehwarnung): List<de.troetenrepublik.app.engine.Fruehwarnung.Zeichen> = coroutineScope {
        val von = java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusYears(2)
        suspend fun reihe(adresse: String): List<Pair<Long, Double>> = runCatching {
            de.troetenrepublik.app.market.FredAbruf.geteilt(adresse) { stimmungNetz.anfrage(adresse, mapOf("Accept" to "text/csv")) }
                .takeIf { it.ok }?.text?.let(de.troetenrepublik.app.engine.Fruehindikatoren::fredLesen)?.map { (tag, w) -> tag * 86_400_000L to w }
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull().orEmpty()
        val stress = async { reihe(fw.stressAdresse(von)) }
        val hochzins = async { reihe(fw.hochzinsAdresse(von)) }
        listOfNotNull(
            runCatching { fw.stress(stress.await()) }.getOrNull(),
            runCatching { fw.hochzins(hochzins.await()) }.getOrNull(),
        )
    }

    // ------------------------------------------------------------ 5.91 Marktkurven und Kurschart

    private val treasuryLock = kotlinx.coroutines.sync.Mutex()
    @Volatile private var treasuryCache: Pair<Long, List<String>>? = null

    // CSV des US-Finanzministeriums fuer dieses und die zwei vorigen Jahre (die Zinskurven-Regel schaut 18 Monate
    // zurueck), gemeinsam fuer Fruehwarnzeichen und Zinskurve; sechs Stunden zwischengespeichert.
    private suspend fun treasuryTexte(jahr: Int, neu: Boolean = false): List<String> = treasuryLock.withLock {
        val now = System.currentTimeMillis()
        treasuryCache?.takeIf { !neu && now - it.first < 6 * 3_600_000L }?.let { return@withLock it.second }
        val fw = de.troetenrepublik.app.engine.Fruehwarnung
        val texte = coroutineScope {
            listOf(jahr - 2, jahr - 1, jahr).map { j -> async { runCatching { stimmungNetz.anfrage(fw.treasuryAdresse(j), mapOf("Accept" to "text/csv")).text }.getOrNull() } }.awaitAll()
        }
        if (texte.all { it != null }) treasuryCache = now to texte.filterNotNull()
        texte.filterNotNull().ifEmpty { treasuryCache?.second.orEmpty() }
    }

    private val zinsDatei by lazy { de.troetenrepublik.app.plattform.SichereDatei(java.io.File(store.ordner, "zinskurven.json")) }
    private val zinsLock = kotlinx.coroutines.sync.Mutex()
    @Volatile private var zinsCache: de.troetenrepublik.app.engine.Marktkurven.Zinskurven? = null
    @Volatile private var bundesbankVersuch = 0L

    // Zinskurven der USA (dieselbe Abfrage wie die Fruehwarnzeichen) und des Euroraums (EZB, eine Abfrage fuer alle
    // Laufzeiten ueber 400 Tage), je heute, vor einem Monat und vor einem Jahr. Hoechstens einmal am Tag neu geholt;
    // fehlt eine Seite, bleibt ihr letzter Stand und der naechste Aufruf versucht es wieder. Fehlt nur die Bundesbank,
    // wird allein sie nachgeholt, hoechstens einmal je Stunde.
    suspend fun zinskurven(neu: Boolean = false): de.troetenrepublik.app.engine.Marktkurven.Zinskurven = zinsLock.withLock {
        val mk = de.troetenrepublik.app.engine.Marktkurven
        val alt = zinsCache ?: withContext(Dispatchers.IO) { runCatching { mk.lesen(zinsDatei.lesen().toString(Charsets.UTF_8)) }.getOrNull() }
            ?.also { zinsCache = it }
        val now = System.currentTimeMillis()
        val frisch = if (!neu && alt != null && alt.usa != null && alt.euro != null && now - alt.abgerufen < 24 * 3_600_000L) alt else null
        if (frisch != null && (frisch.deutschland != null || now - bundesbankVersuch < 3_600_000L)) return@withLock frisch
        bundesbankVersuch = now
        val heute = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
        val (usa, euro) = if (frisch != null) null to null else coroutineScope {
            val u = async { runCatching { treasuryTexte(heute.year, neu).let { t -> withContext(Dispatchers.Default) { mk.vergleich(t.flatMap(mk::treasuryKurven)) } } }.getOrNull() }
            val e = async {
                runCatching {
                    stimmungNetz.anfrage(mk.ezbAdresse(heute.minusDays(400).toString()), mapOf("Accept" to "text/csv")).takeIf { it.ok }?.text
                        ?.let { t -> withContext(Dispatchers.Default) { mk.vergleich(mk.ezbKurven(t)) } }
                }.getOrNull()
            }
            u.await() to e.await()
        }
        // Bundeswertpapiere (5.94): Statistik der Deutschen Bundesbank, ebenfalls 400 Tage
        val deutschland = runCatching {
            stimmungNetz.anfrage(mk.bundesbankAdresse(heute.minusDays(400).toString()), mapOf("Accept" to "text/csv")).takeIf { it.ok }?.text
                ?.let { t -> withContext(Dispatchers.Default) { mk.vergleich(mk.bundesbankKurven(t)) } }
        }.getOrNull()
        val z = de.troetenrepublik.app.engine.Marktkurven.Zinskurven(
            if (usa != null && euro != null) now else alt?.abgerufen ?: 0L, usa ?: alt?.usa, euro ?: alt?.euro, deutschland ?: alt?.deutschland,
        )
        zinsCache = z
        if (usa != null || euro != null || deutschland != null) withContext(Dispatchers.IO) { runCatching { zinsDatei.schreiben(mk.schreiben(z).toByteArray(Charsets.UTF_8)) } }
        z
    }

    @Volatile private var vixKurvenCache: Pair<Long, Pair<de.troetenrepublik.app.engine.Marktkurven.Kurve, de.troetenrepublik.app.engine.Marktkurven.Kurve?>>? = null

    // VIX-Kurve von einem Tag bis einem Jahr (Yahoo), heute und vor einem Monat; 30 Minuten zwischengespeichert.
    suspend fun vixKurven(neu: Boolean = false): Pair<de.troetenrepublik.app.engine.Marktkurven.Kurve, de.troetenrepublik.app.engine.Marktkurven.Kurve?>? {
        val now = System.currentTimeMillis()
        vixKurvenCache?.takeIf { !neu && now - it.first < 30 * 60_000L }?.let { return it.second }
        val mk = de.troetenrepublik.app.engine.Marktkurven
        val kurse = runCatching { market.schlusskurse(mk.VIX_INDIZES.map { it.first }, "3mo") }.getOrDefault(emptyMap())
        val k = mk.vixKurven(kurse.mapValues { (_, l) -> l.map { it.t to it.close } })
        if (k != null) vixKurvenCache = now to k
        return k ?: vixKurvenCache?.second
    }

    // ------------------------------------------------------------ 5.92 Maerkte: Ausblick, Blasen, Heatmap-Zeitraum

    private val wikiLock = kotlinx.coroutines.sync.Mutex()
    private val wikiReihenCache = ConcurrentHashMap<String, Pair<Long, List<Int>>>()
    @Volatile private var wikiLetzte = 0L

    // Taegliche Aufrufe englischer Wikipedia-Artikel ueber ein Jahr, je Artikel zwoelf Stunden zwischengespeichert.
    // Wikimedia begrenzt die Abfragen streng: nacheinander mit Pause, ein leeres Ergebnis behaelt den letzten Stand.
    private suspend fun wikiReihen(artikel: List<String>): Map<String, List<Int>> = wikiLock.withLock {
        val now = System.currentTimeMillis()
        val aus = HashMap<String, List<Int>>()
        for (a in artikel) {
            val alt = wikiReihenCache[a]
            if (alt != null && now - alt.first < 12 * 3_600_000L) { aus[a] = alt.second; continue }
            val warten = wikiLetzte + 1_500L - System.currentTimeMillis()
            if (warten > 0) kotlinx.coroutines.delay(warten)
            wikiLetzte = System.currentTimeMillis()
            val neu = runCatching { stimmungNetz.wikiAufrufe("en", a, 365) }.getOrDefault(emptyList())
            if (neu.size >= 60) { wikiReihenCache[a] = now to neu; aus[a] = neu } else alt?.let { aus[a] = it.second }
        }
        aus
    }

    @Volatile private var sorgenCache: Pair<Long, de.troetenrepublik.app.engine.Stimmungsbarometer.Barometer>? = null

    // Sorgen-Barometer aus Wikipedia (Rezession, Boersencrash), zwoelf Stunden zwischengespeichert; null ohne Daten.
    suspend fun sorgenBarometer(): de.troetenrepublik.app.engine.Stimmungsbarometer.Barometer? {
        val now = System.currentTimeMillis()
        sorgenCache?.takeIf { now - it.first < 12 * 3_600_000L }?.let { return it.second }
        val sb = de.troetenrepublik.app.engine.Stimmungsbarometer
        val reihen = wikiReihen(sb.SORGEN_ARTIKEL)
        // Die Reihen enden gestern (Wikimedia ist etwa einen Tag verzoegert)
        val b = sb.sorgen(sb.SORGEN_ARTIKEL.mapNotNull { reihen[it] }, (now / 86_400_000L - 1) * 86_400_000L)
        if (b != null) sorgenCache = now to b
        return b ?: sorgenCache?.second
    }

    @Volatile private var zinsErwartungCache: Pair<Long, de.troetenrepublik.app.engine.Zinserwartung.Erwartung>? = null

    // Erwarteter US-Leitzins aus den Fed-Funds-Futures (Yahoo) fuer gut ein Jahr, 30 Minuten zwischengespeichert.
    suspend fun zinsErwartung(): de.troetenrepublik.app.engine.Zinserwartung.Erwartung? {
        val now = System.currentTimeMillis()
        zinsErwartungCache?.takeIf { now - it.first < 30 * 60_000L }?.let { return it.second }
        val ze = de.troetenrepublik.app.engine.Zinserwartung
        val heute = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
        val k = ze.kontrakte(heute.year, heute.monthValue)
        val kurse = schlusskurse(k.map { it.symbol }, "5d").mapValues { (_, l) -> l.lastOrNull()?.close ?: Double.NaN }
        val e = ze.erwartung(ze.pfad(k, kurse))
        if (e != null) zinsErwartungCache = now to e
        return e ?: zinsErwartungCache?.second
    }

    // Ein Thema von „Was der Markt erwartet“ mit dem gewaehlten Ereignis bei Polymarket.
    data class Erwartet(val thema: de.troetenrepublik.app.market.Markterwartung.Thema, val ereignis: de.troetenrepublik.app.market.Prognose.Ereignis)

    @Volatile private var erwartetCache: Pair<Long, List<Erwartet>>? = null

    // Was der Markt zu Zinsen, Rezession, Inflation, Oel, Nahost und Wahlen erwartet: je Thema eine Suche bei Polymarket,
    // hoechstens drei gleichzeitig; 15 Minuten zwischengespeichert. Themen ohne passendes Ereignis fehlen.
    suspend fun marktErwartungen(): List<Erwartet> {
        val now = System.currentTimeMillis()
        erwartetCache?.takeIf { now - it.first < 15 * 60_000L }?.let { return it.second }
        val me = de.troetenrepublik.app.market.Markterwartung
        val aus = ArrayList<Erwartet>()
        for (gruppe in me.THEMEN.chunked(3)) {
            val treffer = coroutineScope {
                gruppe.map { t ->
                    async {
                        runCatching { prognose.suche(de.troetenrepublik.app.market.Prognose.Quelle.POLYMARKET, t.suche) }.getOrNull()
                            ?.let { me.waehlen(t, it, now) }?.let { Erwartet(t, it) }
                    }
                }.awaitAll()
            }
            aus += treffer.filterNotNull()
        }
        if (aus.isNotEmpty()) erwartetCache = now to aus
        return aus.ifEmpty { erwartetCache?.second.orEmpty() }
    }

    // Blasen-Detektor: Messgroessen und Branchen, Stand der Abfrage, Jahr des BIP (fuer den Buffett-Indikator).
    // verlaeufe: Zeitreihe je Messgroesse, soweit die Quelle sie mitliefert, als (Zeit in ms, Wert) fuer die Karte.
    data class BlasenLage(
        val indikatoren: List<de.troetenrepublik.app.engine.Blasen.Indikator>,
        val sektoren: List<de.troetenrepublik.app.engine.Blasen.Sektor>,
        val bipJahr: Int?,
        val stand: Long,
        val verlaeufe: Map<String, List<Pair<Long, Double>>> = emptyMap(),
    )

    @Volatile private var blasenCache: Pair<Long, BlasenLage>? = null
    // Jahr, Quartalsbeginn (Tag seit 1970, nur von FRED) und Dollar
    @Volatile private var bipCache: Pair<Long, Triple<Int, Long?, Double>>? = null

    // Alle Messgroessen des Blasen-Detektors, sechs Stunden zwischengespeichert. Was nicht antwortet, fehlt.
    suspend fun blasen(neu: Boolean = false): BlasenLage {
        val now = System.currentTimeMillis()
        blasenCache?.takeIf { !neu && now - it.first < 6 * 3_600_000L }?.let { return it.second }
        val bl = de.troetenrepublik.app.engine.Blasen
        val bt = de.troetenrepublik.app.engine.Branchentrends
        // Eine Gattung je Firma: Alphabet, Fox und News Corp stehen mit zwei Kuerzeln im Katalog, Yahoo nennt bei beiden den
        // Boersenwert der ganzen Firma
        val sp500 = runCatching {
            val aktien = de.troetenrepublik.app.market.Catalog.get().aktien.filter { "SP500" in it.x }
            bl.jeFirma(aktien.map { it.s }, aktien.associate { it.s to it.n }, aktien.associate { it.s to it.c })
        }.getOrDefault(emptyList())
        val lage = coroutineScope {
            val quotes = async { runCatching { market.snapshot(listOf("SPY", "^W5000", "^TNX"), 10 * 60_000L) }.getOrDefault(emptyMap()) }
            val jahr = async { schlusskurse(listOf("SPY", "RSP", "IPO"), "1y") }
            val phasen = async { runCatching { marktphase() }.getOrDefault(emptyList()) }
            val stimmung = async { runCatching { stimmungen() }.getOrNull() }
            val mitglieder = async { runCatching { market.snapshot(sp500, 10 * 60_000L) }.getOrDefault(emptyMap()) }
            val gewinne = async { kennzahlen(sp500) }
            val branchen = async { runCatching { branchenKurse() }.getOrDefault(emptyMap()) }
            // FRED (GDP) wird nicht gespeichert; der Zwischenspeicher haelt nur den Wert der zweiten Quelle
            val bip = async {
                runCatching {
                    val adresse = de.troetenrepublik.app.engine.Ersatzquellen.fredAdresse("GDP", java.time.LocalDate.now().minusYears(2))
                    de.troetenrepublik.app.market.FredAbruf.geteilt(adresse) { stimmungNetz.anfrage(adresse) }
                        .takeIf { it.ok }?.text?.let(bl::bipFredLesen)?.let { (t, d) -> Triple(java.time.LocalDate.ofEpochDay(t).year, t as Long?, d) }
                }.getOrNull()
                    ?: bipCache?.takeIf { now - it.first < 7 * 86_400_000L }?.second
                    ?: runCatching {
                        stimmungNetz.anfrage(bl.BIP).takeIf { it.ok }?.text?.let(bl::bipLesen)?.let { Triple(it.first, null, it.second) }
                    }.getOrNull()?.also { bipCache = now to it }
                    ?: bipCache?.second
            }
            val blase = async { wikiReihen(listOf(bl.BLASEN_ARTIKEL))[bl.BLASEN_ARTIKEL] }
            // Ersatzquellen (seit 5.92): Shiller-KGV und Margin-Kredite
            val cape = async { runCatching { ersatzNetz.cape() }.getOrNull() }
            val margin = async { runCatching { ersatzNetz.margin() }.getOrNull() }
            val q = quotes.await()
            val j = jahr.await()
            val pe = q["SPY"]?.peRatio?.takeIf { it > 0 }
            val m = mitglieder.await()
            val k = gewinne.await()
            val b = bip.await()
            val werte = sp500.mapNotNull { s ->
                val qq = m[s] ?: return@mapNotNull null
                val kk = k[s] ?: return@mapNotNull null
                de.troetenrepublik.app.engine.Blasen.Gewinne(qq.marketCap, qq.price, kk["epsTrailingTwelveMonths"] ?: return@mapNotNull null, kk["epsForward"] ?: return@mapNotNull null)
            }
            val liste = listOfNotNull(
                bl.cape(cape.await()),
                bl.margin(margin.await()),
                bl.kgv(pe),
                bl.buffett(q["^W5000"]?.let { it.nativePrice.takeIf { p -> p > 0 } ?: it.price }, b?.third, b?.second),
                bl.trend(phasen.await().firstOrNull { it.schluessel == "sp500" }?.zur200),
                bl.konzentration(sp500.mapNotNull { m[it]?.marketCap }),
                bl.ipo(j["IPO"]?.let { bt.veraenderung(it, 365) }),
                bl.stimmung(stimmung.await()?.aktienBarometer?.wert),
                blase.await()?.let { bl.erwaehnung(it) },
                bl.gewinn(bl.gewinnwachstum(werte)),
                bl.praemie(pe, q["^TNX"]?.let { it.nativePrice.takeIf { p -> p > 0 } ?: it.price }),
                bl.breite(j["SPY"]?.let { bt.veraenderung(it, 365) }, j["RSP"]?.let { bt.veraenderung(it, 365) }),
            )
            val br = branchen.await()
            val sektoren = bt.BRANCHEN.mapNotNull { x -> br[x.symbol]?.let { bl.sektor(x.symbol, it) } }.sortedByDescending { it.hitze }
            // Verlaeufe fuer die Karte je Messgroesse: CAPE seit 1881, Margin-Wachstum je Quartal, Stimmung ein Jahr
            val tagMs = 86_400_000L
            val verlaeufe = mapOf(
                bl.CAPE to cape.await()?.reihe?.map { it.first * tagMs to it.second }.orEmpty(),
                bl.MARGIN to margin.await()?.verlauf?.map { it.first * tagMs to it.second }.orEmpty(),
                bl.STIMMUNG to stimmung.await()?.aktienBarometer?.verlauf.orEmpty(),
            ).filterValues { it.size >= 2 }
            BlasenLage(liste, sektoren, b?.first, now, verlaeufe)
        }
        if (lage.indikatoren.size >= 3) blasenCache = now to lage
        return if (lage.indikatoren.isEmpty()) blasenCache?.second ?: lage else lage
    }

    // Bodenzeichen (0.78) aus den Groessen des Blasen-Detektors (KGV, Renditeabstand), dem S&P 500 der Marktphasen und
    // dem Schlusskurs des VIX. Was fehlt, fehlt.
    suspend fun bodenzeichen(lage: BlasenLage): List<de.troetenrepublik.app.engine.Boden.Zeichen> {
        val bl = de.troetenrepublik.app.engine.Blasen
        val index = runCatching { marktphase() }.getOrDefault(emptyList()).firstOrNull { it.schluessel == "sp500" }?.verlauf.orEmpty()
        // VIX: derselbe Schlusskurs wie im Fruehwarnzeichen; ohne Schlusskurse der laufende Kurs
        val vix = fruehwarnCache?.second?.firstOrNull { it.schluessel == de.troetenrepublik.app.engine.Fruehwarnung.VIX }?.wert
            ?: runCatching { market.schlusskurse(listOf("^VIX"), "2y") }.getOrDefault(emptyMap())["^VIX"]?.lastOrNull()?.close?.takeIf { it > 0 }
            ?: runCatching { market.snapshot(listOf("^VIX"), 10 * 60_000L) }.getOrDefault(emptyMap())["^VIX"]
                ?.let { it.nativePrice.takeIf { p -> p > 0 } ?: it.price }
        return de.troetenrepublik.app.engine.Boden.zeichen(
            lage.indikatoren.firstOrNull { it.schluessel == bl.KGV }?.wert, lage.indikatoren.firstOrNull { it.schluessel == bl.PRAEMIE }?.wert, index, vix,
        )
    }

    // Verbraucherpreise des Euroraums je Monat (HVPI, Datenportal der EZB), aus demselben Abruf und Speicher wie beim
    // Vierer-Indikator; leer ohne Daten.
    suspend fun verbraucherpreiseEuro(): List<de.troetenrepublik.app.engine.Kaufkraft.Monat> {
        val v = de.troetenrepublik.app.engine.Vierer
        val text = globusMarktText("vierer-hvpi", de.troetenrepublik.app.engine.Kaufkraft.EZB, 24 * 3_600_000L) { t ->
            de.troetenrepublik.app.engine.Kaufkraft.ezbLesen(t).takeIf { it.size >= 36 }?.let(v::indexText)
        } ?: return emptyList()
        return text.lines().mapNotNull { z ->
            val f = z.split(',')
            val w = f.getOrNull(1)?.trim()?.toDoubleOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            de.troetenrepublik.app.engine.Kaufkraft.Monat(f[0].trim(), w)
        }.filter { Regex("""\d{4}-\d{2}""").matches(it.m) }.sortedBy { it.m }
    }

    @Volatile private var viererCache: Pair<Long, de.troetenrepublik.app.engine.Vierer.Ergebnis>? = null

    // Vierer-Indikator (0.78): Einlagesatz und Verbraucherpreise des Euroraums vom Datenportal der EZB (je einmal am Tag,
    // gekuerzt gespeichert) und Dollar je Euro aus zehn Jahren Tagesschlusskursen; sechs Stunden zwischengespeichert.
    suspend fun vierer(): de.troetenrepublik.app.engine.Vierer.Ergebnis? {
        val now = System.currentTimeMillis()
        viererCache?.takeIf { now - it.first < 6 * 3_600_000L }?.let { return it.second }
        val v = de.troetenrepublik.app.engine.Vierer
        val e = coroutineScope {
            val zins = async {
                globusMarktText("vierer-zins", v.ZINS_ADRESSE, 24 * 3_600_000L, 2_000_000) { t -> v.zinsLesen(t).takeIf { it.size >= 2 }?.let(v::stufenText) }
            }
            val preise = async {
                globusMarktText("vierer-hvpi", de.troetenrepublik.app.engine.Kaufkraft.EZB, 24 * 3_600_000L) { t ->
                    de.troetenrepublik.app.engine.Kaufkraft.ezbLesen(t).takeIf { it.size >= 36 }?.let(v::indexText)
                }
            }
            val kurse = async { schlusskurse(listOf(v.EURUSD), "10y")[v.EURUSD].orEmpty().filter { it.close > 0 }.map { Math.floorDiv(it.t, 86_400_000L) to it.close } }
            val stufen = zins.await()?.let(v::stufenLesen).orEmpty()
            val index = preise.await()?.let(v::indexLesen).orEmpty()
            val k = kurse.await()
            if (stufen.size < 2 || index.isEmpty() || k.isEmpty()) null
            else withContext(Dispatchers.Default) { v.rechnen(stufen, index, k, Math.floorDiv(now, 86_400_000L)) }
        }
        if (e != null) viererCache = now to e
        return e ?: viererCache?.second
    }

    // Ersatzquellen (seit 5.92, market/Ersatzabrufe.kt): Lieferketten, Fracht, Bahn und Halbleiter fuer den Ausblick,
    // Erwaehnungen auf Hacker News und Mastodon fuer die Seite eines Werts, CAPE und Margin-Kredite fuer die Blasen.
    private val ersatzNetz by lazy { de.troetenrepublik.app.market.Ersatzabrufe(stimmungNetz) }

    suspend fun ersatzSignale(neu: Boolean = false): Map<String, de.troetenrepublik.app.engine.Ersatzquellen.Signal> =
        runCatching { ersatzNetz.signale(neu) }.getOrDefault(emptyMap())

    suspend fun netzErwaehnungen(name: String): de.troetenrepublik.app.market.Ersatzabrufe.Netz? =
        runCatching { ersatzNetz.netz(name) }.getOrNull()

    // Weitere Quellen (seit 5.92, market/WeitereAbrufe.kt): Containerfracht, US-Bahnen und
    // Halbleiterumsaetze ohne Schluessel, Bundesrichter mit eigenem CourtListener-Token, Bluesky mit eigenem App-Passwort.
    val weitere by lazy { de.troetenrepublik.app.market.WeitereAbrufe(stimmungNetz) { kontingent } }

    // Hintergrund der Kryptowerte (0.78, market/KryptoAbrufe.kt): Finanzierungssatz, offene Kontrakte, Stablecoins,
    // gebundenes Kapital; ohne Schluessel, nur im Arbeitsspeicher.
    val krypto by lazy { de.troetenrepublik.app.market.KryptoAbrufe(stimmungNetz) }

    // Gemeldete Beteiligungen aus dem Register des britischen Unterhauses (0.78, market/UkRegisterAbruf.kt)
    val ukRegister by lazy { de.troetenrepublik.app.market.UkRegisterAbruf(stimmungNetz) }

    // Lagerbestaende der LME: Monatsdateien auf dem Geraet, Abruf erst beim Oeffnen der Stelle.
    val lmeLager by lazy { de.troetenrepublik.app.market.LmeLagerAbruf(MarketApi(), java.io.File(store.ordner, "lme")) }

    suspend fun lmeLagerStand(): de.troetenrepublik.app.market.LmeLagerAbruf.Ergebnis? =
        try { lmeLager.laden() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }

    // Lagerbestaende der COMEX: Dateien, die die Person selbst geladen und geoeffnet hat, je Metall im Ordner comex.
    val comexLager by lazy { de.troetenrepublik.app.market.ComexLagerAbruf(java.io.File(store.ordner, "comex")) }

    suspend fun comexLagerStand(): de.troetenrepublik.app.engine.ComexLager.Stand? =
        try { de.troetenrepublik.app.engine.ComexLager.auswerten(comexLager.laden()) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }

    suspend fun putCall(): de.troetenrepublik.app.engine.WeitereQuellen.PutCall? = runCatching { weitere.putCall() }.getOrNull()

    // Archiv des Put/Call-Verhaeltnisses der Aktienoptionen (Cboe), nur im Arbeitsspeicher
    suspend fun putCallArchiv(): de.troetenrepublik.app.engine.PutCallRueckblick.Archiv? = runCatching { weitere.putCallArchiv() }.getOrNull()

    // VX-Terminkurve der Cboe (5.95) fuer das Blatt der Marktkurven
    suspend fun vxKurve(): de.troetenrepublik.app.engine.VxKurve.Stand? =
        try { weitere.vx() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }

    // ------------------------------------------------------------ 5.95 Quellen

    // Director Dealings und Stimmrechtsmeldungen aus Grossbritannien ueber Investegate: Abrufe nur nach der
    // einmaligen Bestaetigung als Privatanleger, Ergebnisse nur im Arbeitsspeicher, nach einem Fehlschlag zehn
    // Minuten Pause je Quelle. investegateFirmen speichert alle Meldungen der Firmenseite.
    private val investegateLock = kotlinx.coroutines.sync.Mutex()
    private var investegateVersuch = 0L
    private var investegateLetzter = 0L
    private val investegateSeiten = HashMap<Int, Pair<Long, List<de.troetenrepublik.app.market.Investegate.Meldung>>>()
    private val investegateFirmen = LinkedHashMap<String, Pair<Long, List<de.troetenrepublik.app.market.Investegate.Meldung>>>()
    private val investegateBoegen = LinkedHashMap<String, Pair<Long, de.troetenrepublik.app.market.Investegate.Bogen>>()
    private val investegateTr1Speicher = LinkedHashMap<String, Pair<Long, de.troetenrepublik.app.market.Tr1.Meldung?>>()

    // Holt eine Seite von Investegate; nur ok-Antworten, sonst Pause und null. Zwischen zwei Abrufen liegt
    // mindestens eine Sekunde Abstand.
    private suspend fun investegateHolen(url: String): String? {
        if (System.currentTimeMillis() - investegateVersuch < 600_000L) return null
        val rest = 1_000L - (System.currentTimeMillis() - investegateLetzter)
        if (rest > 0) kotlinx.coroutines.delay(rest)
        investegateLetzter = System.currentTimeMillis()
        val a = try { stimmungNetz.anfrage(url, mapOf("Accept" to "text/html")) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
        if (a == null || !a.ok) { investegateVersuch = System.currentTimeMillis(); return null }
        return a.text
    }

    // Uebersicht der Director Dealings, Seiten 1 bis 3, je Seite 15 Minuten zwischengespeichert
    suspend fun investegateListe(seite: Int): List<de.troetenrepublik.app.market.Investegate.Meldung>? = investegateLock.withLock {
        val ig = de.troetenrepublik.app.market.Investegate
        if (!okInvestegateStand || seite !in 1..3) return@withLock null
        val jetzt = System.currentTimeMillis()
        investegateSeiten[seite]?.takeIf { jetzt - it.first < 15 * 60_000L }?.let { return@withLock it.second }
        val html = investegateHolen(ig.listeAdresse(seite)) ?: return@withLock investegateSeiten[seite]?.second
        val liste = runCatching { ig.listeLesen(html) }.getOrNull()
        if (liste == null) {
            investegateVersuch = System.currentTimeMillis()
            return@withLock investegateSeiten[seite]?.second
        }
        investegateSeiten[seite] = jetzt to liste
        liste
    }

    // Alle Meldungen einer Firmenseite, je Kuerzel 30 Minuten zwischengespeichert, hoechstens 30 Kuerzel (aelteste
    // raus). Ohne eigene Sperre, nur innerhalb der Investegate-Sperre nutzen.
    private suspend fun investegateFirmaAlle(kuerzel: String): List<de.troetenrepublik.app.market.Investegate.Meldung>? {
        val ig = de.troetenrepublik.app.market.Investegate
        val jetzt = System.currentTimeMillis()
        investegateFirmen[kuerzel]?.takeIf { jetzt - it.first < 30 * 60_000L }?.let { return it.second }
        val html = investegateHolen(ig.firmaAdresse(kuerzel)) ?: return investegateFirmen[kuerzel]?.second
        val liste = runCatching { ig.firmaAlleLesen(html, kuerzel) }.getOrNull()
        if (liste == null) {
            investegateVersuch = System.currentTimeMillis()
            return investegateFirmen[kuerzel]?.second
        }
        investegateFirmen[kuerzel] = jetzt to liste
        while (investegateFirmen.size > 30) investegateFirmen.remove(investegateFirmen.keys.first())
        return liste
    }

    // Dealing-Meldungen einer Firma aus allen Meldungen der Firmenseite
    suspend fun investegateFirma(kuerzel: String): List<de.troetenrepublik.app.market.Investegate.Meldung>? = investegateLock.withLock {
        val ig = de.troetenrepublik.app.market.Investegate
        if (!okInvestegateStand) return@withLock null
        investegateFirmaAlle(kuerzel)?.filter { ig.istDealing(it.titel) }
    }

    // Stimmrechtsmeldungen (TR-1) einer Firma aus allen Meldungen der Firmenseite, neueste zuerst
    suspend fun investegateStimmrechte(kuerzel: String): List<de.troetenrepublik.app.market.Tr1.Eintrag>? = investegateLock.withLock {
        if (!okInvestegateStand) return@withLock null
        investegateFirmaAlle(kuerzel)
            ?.filter { de.troetenrepublik.app.market.Tr1.istStimmrechte(it.titel) }
            ?.sortedByDescending { it.zeit }
            ?.map { de.troetenrepublik.app.market.Tr1.Eintrag(it.zeit, it.titel, it.quelle, it.link) }
    }

    // Eine Meldung als Bogen, nur Links von Investegate selbst, 60 Minuten zwischengespeichert, hoechstens 20
    suspend fun investegateMeldung(link: String): de.troetenrepublik.app.market.Investegate.Bogen? = investegateLock.withLock {
        val ig = de.troetenrepublik.app.market.Investegate
        if (!okInvestegateStand || !link.startsWith(ig.BASIS + "/announcement/")) return@withLock null
        val jetzt = System.currentTimeMillis()
        investegateBoegen[link]?.takeIf { jetzt - it.first < 60 * 60_000L }?.let { return@withLock it.second }
        val html = investegateHolen(link) ?: return@withLock investegateBoegen[link]?.second
        val bogen = runCatching { ig.meldungLesen(html) }.getOrNull()
        if (bogen == null) {
            investegateVersuch = System.currentTimeMillis()
            return@withLock investegateBoegen[link]?.second
        }
        investegateBoegen[link] = jetzt to bogen
        while (investegateBoegen.size > 20) investegateBoegen.remove(investegateBoegen.keys.first())
        bogen
    }

    // Ein TR-1-Formular als gelesene Meldung, nur Links von Investegate selbst, nur mit Bestaetigung, 60 Minuten
    // zwischengespeichert, hoechstens 20 Eintraege wie die Boegen (aelteste raus). Ein nicht lesbares Formular bleibt als null
    // gespeichert. Der Abruf legt zugleich den Bogen fuer das Blatt der Meldung ab.
    suspend fun investegateTr1(link: String): de.troetenrepublik.app.market.Tr1.Meldung? = investegateLock.withLock {
        val ig = de.troetenrepublik.app.market.Investegate
        if (!okInvestegateStand || !link.startsWith(ig.BASIS + "/announcement/")) return@withLock null
        val jetzt = System.currentTimeMillis()
        investegateTr1Speicher[link]?.takeIf { jetzt - it.first < 60 * 60_000L }?.let { return@withLock it.second }
        val html = investegateHolen(link) ?: return@withLock null
        // Lesen ausserhalb des Hauptfadens
        val (bogenNeu, meldung) = withContext(Dispatchers.Default) {
            runCatching { ig.meldungLesen(html) }.getOrNull() to runCatching { de.troetenrepublik.app.market.Tr1.lesen(html) }.getOrNull()
        }
        bogenNeu?.let { bogen ->
            investegateBoegen[link] = jetzt to bogen
            while (investegateBoegen.size > 20) investegateBoegen.remove(investegateBoegen.keys.first())
        }
        investegateTr1Speicher[link] = jetzt to meldung
        while (investegateTr1Speicher.size > 20) investegateTr1Speicher.remove(investegateTr1Speicher.keys.first())
        meldung
    }

    // Managers Transactions der Nasdaq-Nordic-Laender (Daenemark, Finnland, Island, Baltikum): letzte 365 Tage je
    // Firma, 60 Minuten zwischengespeichert, nach einem Fehlschlag zehn Minuten Pause
    private val nordicLock = kotlinx.coroutines.sync.Mutex()
    private var nordicVersuch = 0L
    private val nordicSpeicher = HashMap<String, Pair<Long, List<de.troetenrepublik.app.market.NasdaqNordic.Meldung>>>()

    suspend fun nordicMeldungen(land: String, name: String): List<de.troetenrepublik.app.market.NasdaqNordic.Meldung>? = nordicLock.withLock {
        val nn = de.troetenrepublik.app.market.NasdaqNordic
        val jetzt = System.currentTimeMillis()
        val schluessel = "$land|$name"
        nordicSpeicher[schluessel]?.takeIf { jetzt - it.first < 60 * 60_000L }?.let { return@withLock it.second }
        val alt = nordicSpeicher[schluessel]?.second
        if (jetzt - nordicVersuch < 600_000L) return@withLock alt
        val heute = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Berlin"))
        val url = nn.adresse(land, name, heute.minusDays(365), heute) ?: return@withLock alt
        val antwort = try { stimmungNetz.anfrage(url, mapOf("Accept" to "application/json")) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
        val liste = antwort?.takeIf { it.ok }?.text?.let { text -> nn.lesen(text) }
        if (liste == null) {
            nordicVersuch = jetzt
            return@withLock alt
        }
        val treffer = liste.filter { nn.passt(it, name) }
        nordicSpeicher[schluessel] = jetzt to treffer
        treffer
    }

    // Meldungen der MNB (Ungarn) je Firma als Links, letzte 365 Tage, 60 Minuten zwischengespeichert, nach einem
    // Fehlschlag zehn Minuten Pause. Abgerufen wird nur die Trefferliste.
    private val mnbLock = kotlinx.coroutines.sync.Mutex()
    private var mnbVersuch = 0L
    private val mnbSpeicher = HashMap<String, Pair<Long, List<de.troetenrepublik.app.market.InsiderEuropa.MnbMeldung>>>()

    suspend fun mnbMeldungen(name: String): List<de.troetenrepublik.app.market.InsiderEuropa.MnbMeldung>? = mnbLock.withLock {
        val jetzt = System.currentTimeMillis()
        mnbSpeicher[name]?.takeIf { jetzt - it.first < 60 * 60_000L }?.let { return@withLock it.second }
        val alt = mnbSpeicher[name]?.second
        if (jetzt - mnbVersuch < 600_000L) return@withLock alt
        val von = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Budapest")).minusDays(365).toString()
        val liste = withContext(Dispatchers.IO) {
            val netz = de.troetenrepublik.app.market.InsiderQuellen.netz(MarketApi())
            try { de.troetenrepublik.app.market.InsiderEuropa.mnbMeldungen(netz, name, von) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
        }
        if (liste == null) {
            mnbVersuch = jetzt
            return@withLock alt
        }
        mnbSpeicher[name] = jetzt to liste
        liste
    }

    // Nachrichten von EODHD mit eigenem Schluessel, ein Abruf kostet 5 der 20 Calls am Tag; das Ergebnis bleibt
    // eine Stunde im Arbeitsspeicher, ein erneutes Oeffnen kostet kein Kontingent. Der Schluessel geht nur an EODHD.
    private val eodhdLock = kotlinx.coroutines.sync.Mutex()
    private var eodhdVersuch = 0L
    private val eodhdSpeicher = HashMap<String, Pair<Long, de.troetenrepublik.app.market.NachrichtenApis.Ergebnis>>()

    suspend fun eodhdNews(symbol: String): de.troetenrepublik.app.market.NachrichtenApis.Ergebnis? = eodhdLock.withLock {
        val api = de.troetenrepublik.app.market.NachrichtenApis
        val ko = de.troetenrepublik.app.market.Abrufkontingent
        val schluessel = settingsFlow.value.keyEodhd.trim()
        val ticker = api.eodhdTicker(symbol)
        if (schluessel.isEmpty() || ticker == null) return@withLock null
        val jetzt = System.currentTimeMillis()
        eodhdSpeicher[ticker]?.takeIf { jetzt - it.first < 60 * 60_000L }?.let { return@withLock it.second }
        if (!news.kontingent.erlaubt(ko.EODHD, ko.EODHD_KOSTEN)) return@withLock de.troetenrepublik.app.market.NachrichtenApis.Ergebnis(emptyList(), NewsRepository.LIMIT)
        if (jetzt - eodhdVersuch < 600_000L) return@withLock null
        val antwort = try { stimmungNetz.anfrage(api.eodhdAdresse(ticker, schluessel), mapOf("Accept" to "application/json")) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
        // Gebucht wird nur bei einer Antwort
        if (api.eodhdGebucht(antwort)) news.kontingent.buchen(ko.EODHD, ko.EODHD_KOSTEN)
        val text = antwort?.text ?: run { eodhdVersuch = jetzt; return@withLock null }
        val ergebnis = runCatching { api.eodhdLesen(text) }.getOrNull()?.takeIf { it.meldung != "?" } ?: run { eodhdVersuch = jetzt; return@withLock null }
        if (antwort.ok) {
            eodhdSpeicher[ticker] = jetzt to ergebnis
        } else {
            eodhdVersuch = jetzt
        }
        ergebnis
    }

    // ------------------------------------------------------------ 5.95 Gesamtstimmung

    // Zeitpunkt des letzten erfolgreichen UMCSENT-Abrufs, fuer das FRED-Zitat mit Abrufdatum
    @Volatile var umichAbruf = 0L
        private set

    // Verfall je Schluessel in Tagen, Vorgabe sieben Tage; Z.1-Werte sind bis rund 250 Tage alt, bevor die naechste Ausgabe kommt
    private val ZUSATZ_TAGE = mapOf("breite200" to 3L, "hochtief" to 3L, "vvix" to 7L, "move" to 7L, "umich" to 100L, "margin" to 280L)

    // Zwischenspeicher, Rechenpause und Sperre je Aufrufer, Oberflaeche und Widget getrennt
    private class ZusatzStand {
        @Volatile var cache: Pair<Long, Map<String, de.troetenrepublik.app.engine.Gesamtstimmung.Wert>>? = null
        @Volatile var versuch = 0L
        val sperre = kotlinx.coroutines.sync.Mutex()
    }

    private val zusatzOberflaeche = ZusatzStand()
    private val zusatzWidget = ZusatzStand()

    // Entfernt Werte, deren Verfall abgelaufen ist
    private fun zusatzFrisch(m: Map<String, de.troetenrepublik.app.engine.Gesamtstimmung.Wert>): Map<String, de.troetenrepublik.app.engine.Gesamtstimmung.Wert> =
        m.filter { (s, w) -> System.currentTimeMillis() - w.stand <= (ZUSATZ_TAGE[s] ?: 7L) * 86_400_000L }

    // Bestandteile aus FRED-Reihen: FRED verbietet das Zwischenspeichern, sie kommen bei jedem Aufruf frisch
    private val FRED_ZUSATZ = setOf("umich", "margin")

    private suspend fun fredZusatz(): Map<String, de.troetenrepublik.app.engine.Gesamtstimmung.Wert> = coroutineScope {
        val gs = de.troetenrepublik.app.engine.Gesamtstimmung
        val umichRoh = async {
            runCatching {
                val adresse = de.troetenrepublik.app.engine.Ersatzquellen.fredAdresse("UMCSENT", java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusYears(11))
                de.troetenrepublik.app.market.FredAbruf.geteilt(adresse) { stimmungNetz.anfrage(adresse, mapOf("Accept" to "text/csv")) }
                    .takeIf { it.ok }?.text?.let(de.troetenrepublik.app.engine.Fruehindikatoren::fredLesen)?.map { (tag, w) -> tag * 86_400_000L to w }
            }.getOrNull().orEmpty()
        }
        val marginWert = async { runCatching { ersatzNetz.margin() }.getOrNull()?.let(gs::margin) }
        val aus = HashMap<String, de.troetenrepublik.app.engine.Gesamtstimmung.Wert>()
        gs.ausReihe(umichRoh.await(), gier = true, fenster = 120, mindestens = 36)?.let { aus["umich"] = it }
        marginWert.await()?.let { aus["margin"] = it }
        val gefiltert = zusatzFrisch(aus)
        if ("umich" in gefiltert) umichAbruf = System.currentTimeMillis()
        gefiltert
    }

    // Weitere Bestandteile der Gesamtstimmung, eine Stunde zwischengespeichert; was nicht antwortet, behaelt seinen letzten Wert
    suspend fun stimmungZusatz(neu: Boolean = false, mitBreite: Boolean = true): Map<String, de.troetenrepublik.app.engine.Gesamtstimmung.Wert> {
        val eigener = if (mitBreite) zusatzOberflaeche else zusatzWidget
        val anderer = if (mitBreite) zusatzWidget else zusatzOberflaeche
        return eigener.sperre.withLock {
            val gs = de.troetenrepublik.app.engine.Gesamtstimmung
            val alt = eigener.cache
            if (!neu && alt != null && System.currentTimeMillis() - alt.first < 3_600_000L) return@withLock zusatzFrisch(alt.second) + fredZusatz()
            if (!neu && System.currentTimeMillis() - eigener.versuch < 600_000L) return@withLock zusatzFrisch(alt?.second.orEmpty()) + fredZusatz()
            val frisch = HashMap<String, de.troetenrepublik.app.engine.Gesamtstimmung.Wert>()
            coroutineScope {
                val kurse = async { runCatching { schlusskurse(listOf("^VVIX", "^MOVE"), "2y") }.getOrDefault(emptyMap()) }
                val kennz = async {
                    runCatching {
                        // Kennzahlen aus dem Zwischenspeicher des Aktienfinders, nur bei mitBreite mit Nachladen
                        val sp500 = de.troetenrepublik.app.market.Catalog.get().aktien.filter { "SP500" in it.x }.map { it.s }
                        val (k, kStand) = sp500Kennzahlen(sp500, holen = mitBreite)
                        Triple(k, kStand, sp500.size)
                    }.getOrNull()
                }
                val fredW = async { fredZusatz() }
                val k = kurse.await()
                k["^VVIX"]?.map { it.t to it.close }?.let { gs.ausReihe(it, gier = false, fenster = 260, mindestens = 100) }?.let { frisch["vvix"] = it }
                k["^MOVE"]?.map { it.t to it.close }?.let { gs.ausReihe(it, gier = false, fenster = 260, mindestens = 100) }?.let { frisch["move"] = it }
                // Stand der Breite ist der aelteste Zeitstempel der benutzten Kennzahlen
                kennz.await()?.let { (kennwerte, kStand, groesse) ->
                    val b = gs.breite(kennwerte.values.mapNotNull { f -> f["regularMarketPrice"]?.let { de.troetenrepublik.app.engine.Gesamtstimmung.Kurslage(it, f["twoHundredDayAverage"], f["fiftyTwoWeekHigh"], f["fiftyTwoWeekLow"]) } })
                    val min = maxOf(100, (groesse * 0.9).toInt())
                    gs.breite200(b, kStand, min)?.let { frisch["breite200"] = it }
                    gs.hochTief(b, kStand, min)?.let { frisch["hochtief"] = it }
                }
                frisch.putAll(fredW.await())
            }
            val frischGefiltert = zusatzFrisch(frisch)
            // Beide Zwischenspeicher bilden die Grundlage, je Schluessel gilt der neuere Stand, frische Werte gewinnen
            val grundlage = HashMap(zusatzFrisch(anderer.cache?.second.orEmpty()))
            for ((s, w) in zusatzFrisch(alt?.second.orEmpty())) if (grundlage[s]?.let { it.stand > w.stand } != true) grundlage[s] = w
            val ergebnis: Map<String, de.troetenrepublik.app.engine.Gesamtstimmung.Wert> = grundlage + frischGefiltert
            // FRED-Werte (Stimmung der Verbraucher, Margin-Kredite) bleiben nicht im Speicher, sie werden je Aufruf geholt
            if (frischGefiltert.isNotEmpty()) eigener.cache = System.currentTimeMillis() to ergebnis.filterKeys { it !in FRED_ZUSATZ }
            else {
                // Ohne neuen Wert nur kuerzen, der Zeitpunkt des Zwischenspeichers bleibt
                eigener.versuch = System.currentTimeMillis()
                if (alt != null) eigener.cache = alt.first to zusatzFrisch(alt.second)
            }
            ergebnis
        }
    }

    // Gesamtstimmung aus bereits geladenen Teilen; rechnet nur und laedt nichts (fuer die Oberflaeche)
    fun gesamtstimmungAus(st: Stimmungen?, zeichen: List<de.troetenrepublik.app.engine.Fruehwarnung.Zeichen>, cot: List<de.troetenrepublik.app.engine.Positionierung.Lage>, zusatz: Map<String, de.troetenrepublik.app.engine.Gesamtstimmung.Wert>): de.troetenrepublik.app.engine.Gesamtstimmung.Ergebnis? {
        val gs = de.troetenrepublik.app.engine.Gesamtstimmung
        return gs.rechnen(gs.eingaben(st?.cnn, st?.aktien, st?.vix, st?.sicher, st?.aaii?.barometer, st?.krypto, st?.wsb, st?.sorgen, zeichen, cot, zusatz))
    }

    @Volatile private var gesamtCache: Pair<Long, de.troetenrepublik.app.engine.Gesamtstimmung.Ergebnis>? = null

    private val gesamtLock = kotlinx.coroutines.sync.Mutex()

    // Zwischenspeicher der Gesamtstimmung ohne Marktbreite, nur fuer das Widget
    @Volatile private var gesamtCacheWidget: Pair<Long, de.troetenrepublik.app.engine.Gesamtstimmung.Ergebnis>? = null

    private val gesamtLockWidget = kotlinx.coroutines.sync.Mutex()

    // Gesamtstimmung mit allen Abrufen (auch fuer das Widget), 30 Minuten zwischengespeichert, sonst letzter Stand
    suspend fun gesamtstimmung(neu: Boolean = false, mitBreite: Boolean = true): de.troetenrepublik.app.engine.Gesamtstimmung.Ergebnis? = gesamtstimmungMitStand(neu, mitBreite)?.second

    // Gesamtstimmung mit dem Zeitpunkt ihrer Berechnung, damit ein alter Wert nicht neu gestempelt wird
    suspend fun gesamtstimmungMitStand(neu: Boolean = false, mitBreite: Boolean = true): Pair<Long, de.troetenrepublik.app.engine.Gesamtstimmung.Ergebnis>? = (if (mitBreite) gesamtLock else gesamtLockWidget).withLock {
        val alt = if (mitBreite) gesamtCache else gesamtCacheWidget
        if (!neu && alt != null && System.currentTimeMillis() - alt.first < 1_800_000L) return@withLock alt
        val ergebnis = coroutineScope {
            val st = async { runCatching { stimmungen(neu) }.getOrNull() }
            val sorgen = async { runCatching { sorgenBarometer() }.getOrNull() }
            val zeichen = async { runCatching { fruehwarnung(neu) }.getOrNull() }
            val cot = async { runCatching { positionierung(neu, nur = setOf("sp500", "nasdaq")) }.getOrNull() }
            val zusatz = async { runCatching { stimmungZusatz(neu, mitBreite) }.getOrNull() }
            gesamtstimmungAus(st.await()?.let { s -> sorgen.await()?.let { s.copy(sorgen = it) } ?: s }, zeichen.await().orEmpty(), cot.await().orEmpty(), zusatz.await().orEmpty())
        }
        if (ergebnis != null) {
            val eintrag = System.currentTimeMillis() to ergebnis
            if (mitBreite) gesamtCache = eintrag else gesamtCacheWidget = eintrag
            eintrag
        } else alt
    }

    data class WeitereSignale(
        val wci: de.troetenrepublik.app.engine.WeitereQuellen.Wci?,
        val bahn: de.troetenrepublik.app.engine.WeitereQuellen.Bahnwoche?,
        val halbleiter: de.troetenrepublik.app.engine.WeitereQuellen.Halbleiter?,
    )

    suspend fun weitereSignale(): WeitereSignale = kotlinx.coroutines.coroutineScope {
        val w = async { runCatching { weitere.wci() }.getOrNull() }
        val b = async { runCatching { weitere.bahn() }.getOrNull() }
        val h = async { runCatching { weitere.halbleiter() }.getOrNull() }
        WeitereSignale(w.await(), b.await(), h.await())
    }

    suspend fun richter(): de.troetenrepublik.app.market.WeitereAbrufe.Richter? =
        runCatching { weitere.richter(settingsFlow.value.keyCourtListener) }.getOrNull()

    fun blueskyAktiv(s: de.troetenrepublik.app.data.Settings = settingsFlow.value): Boolean =
        de.troetenrepublik.app.engine.WeitereQuellen.handleBereinigt(s.bskyHandle) != null && de.troetenrepublik.app.engine.WeitereQuellen.appPasswortGueltig(s.bskyAppPasswort)

    suspend fun bluesky(name: String): de.troetenrepublik.app.engine.WeitereQuellen.BlueskyWoche? {
        val s = settingsFlow.value
        if (!blueskyAktiv(s)) return null
        return runCatching { weitere.bluesky(s.bskyHandle, s.bskyAppPasswort, name) }.getOrNull()
    }

    @Volatile private var rotationCache: Pair<Long, de.troetenrepublik.app.engine.Branchentrends.Rotation?>? = null

    // Sektorrotation aus den Branchenkursen, dazu VIX und Zinsen ueber einen Monat; wie die Kurse 15 Minuten gemerkt.
    suspend fun sektorRotation(): de.troetenrepublik.app.engine.Branchentrends.Rotation? {
        val now = System.currentTimeMillis()
        rotationCache?.takeIf { now - it.first < 15 * 60_000L }?.let { return it.second }
        val bt = de.troetenrepublik.app.engine.Branchentrends
        val k = branchenKurse()
        fun monat(s: String): Double? = k[s]?.filter { it.close.isFinite() }?.sortedBy { it.t }?.let { l ->
            val ende = l.lastOrNull() ?: return@let null
            val start = l.lastOrNull { it.t <= ende.t - 30 * 86_400_000L } ?: return@let null
            ende.close - start.close
        }
        val r = bt.rotation(k, monat("^VIX"), monat("^TNX"))
        if (k.isNotEmpty()) rotationCache = now to r
        return r
    }

    private val heatmapZeitCache = ConcurrentHashMap<String, Pair<Long, Map<String, Double>>>()

    // Veraenderung in Prozent ueber tage fuer viele Werte aus Tagesschlusskursen: je 20 eine Abfrage, vier gleichzeitig;
    // 30 Minuten zwischengespeichert.
    private suspend fun zeitraumAenderungen(symbole: List<String>, tage: Int): Map<String, Double> {
        if (symbole.isEmpty()) return emptyMap()
        val schluessel = "$tage|" + symbole.sorted().joinToString(",").hashCode()
        val now = System.currentTimeMillis()
        heatmapZeitCache[schluessel]?.takeIf { now - it.first < 30 * 60_000L }?.let { return it.second }
        val bereich = when { tage <= 7 -> "1mo"; tage <= 30 -> "3mo"; tage <= 91 -> "6mo"; else -> "1y" }
        val aus = HashMap<String, Double>()
        for (gruppe in symbole.distinct().chunked(20).chunked(4)) {
            val teile = coroutineScope { gruppe.map { c -> async { schlusskurse(c, bereich) } }.awaitAll() }
            for (t in teile) for ((s, k) in t) de.troetenrepublik.app.engine.Branchentrends.veraenderung(k, tage)?.let { aus[s] = it }
        }
        if (aus.isNotEmpty()) heatmapZeitCache[schluessel] = now to aus
        return aus
    }

    @Volatile private var yahooBewegungCache: Pair<Long, List<String>>? = null

    // Die je 25 groessten Gewinner und Verlierer des Tages am US-Markt laut Yahoo, 15 Minuten zwischengespeichert.
    suspend fun yahooBewegungen(): List<String> {
        val now = System.currentTimeMillis()
        yahooBewegungCache?.takeIf { now - it.first < 15 * 60_000L }?.let { return it.second }
        val l = coroutineScope {
            val hoch = async { runCatching { market.screener("day_gainers") }.getOrDefault(emptyList()) }
            val runter = async { runCatching { market.screener("day_losers") }.getOrDefault(emptyList()) }
            (hoch.await() + runter.await()).distinct().filterNot { it.startsWith("^") }
        }
        if (l.isNotEmpty()) yahooBewegungCache = now to l
        return l.ifEmpty { yahooBewegungCache?.second.orEmpty() }
    }

    // Heatmap ueber einen laengeren Zeitraum: Flaechen wie heatmap(quelle), Farbe nach der Veraenderung ueber tage.
    suspend fun heatmap(quelle: String, tage: Int): List<HeatmapWert> {
        val heute = heatmap(quelle)
        if (tage <= 1 || heute.isEmpty()) return heute
        val aenderung = zeitraumAenderungen(heute.map { it.symbol }, tage)
        return heute.map { it.copy(aenderung = aenderung[it.symbol]) }
    }

    // Schalter im Kurschart der Wertpapierseite: eigene Marken und Linien (Standard an), erwartete Kursspanne
    // (Standard aus). Gelten fuer alle Werte, bis die App beendet wird.
    val chartEigeneMarken = MutableStateFlow(true)
    val chartKursspanne = MutableStateFlow(false)

    private val sigmaCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Pair<Double, de.troetenrepublik.app.engine.Kursspanne.Quelle>>>()

    // Erwartete Kursspanne ueber 30 Tage ab kurs: Sigma aus der impliziten Volatilitaet fuer 30 Tage (IV30 der
    // Optionskette, derselbe Wert wie im Blatt „Optionen“), sonst aus den letzten 60 Handelstagen. Sigma eine Stunde
    // zwischengespeichert.
    suspend fun kursspanne(symbol: String, kurs: Double): de.troetenrepublik.app.engine.Kursspanne.Spanne? {
        val ks = de.troetenrepublik.app.engine.Kursspanne
        val now = System.currentTimeMillis()
        val wahl = sigmaCache[symbol]?.takeIf { now - it.first < 3_600_000L }?.second ?: run {
            val iv = runCatching {
                market.optionskette(symbol)?.let { (k, l) -> de.troetenrepublik.app.engine.Optionsanalyse.auswerten(k, l).iv30 }
            }.getOrNull()
            ks.sigmaWahl(iv, null) ?: ks.sigmaWahl(null, ks.sigmaHistorisch(dailyCloses(symbol).map { it.close }, tageProJahr = ks.tageProJahr(Symbols.typeOf(symbol) == de.troetenrepublik.app.data.AssetType.CRYPTO)))
        }?.also { sigmaCache[symbol] = now to it } ?: return null
        return ks.spanne(kurs, wahl.first, wahl.second)
    }

    // Werte, nach denen in den deutschen Subreddits gezaehlt wird: Katalog, Depot und Watchlist.
    private fun redditKandidaten(): List<de.troetenrepublik.app.engine.RedditAuswertung.Kandidat> {
        val ra = de.troetenrepublik.app.engine.RedditAuswertung
        val katalog = Symbols.alle().filter { it.type == AssetType.STOCK || it.type == AssetType.ETF || it.type == AssetType.CRYPTO }
            .map { de.troetenrepublik.app.engine.RedditAuswertung.Kandidat(it.symbol, it.name) }
        val a = account.value
        val eigene = a.positions.map { de.troetenrepublik.app.engine.RedditAuswertung.Kandidat(it.symbol, it.name) } +
            a.watchlist.map { w -> de.troetenrepublik.app.engine.RedditAuswertung.Kandidat(w, Symbols.find(w)?.name ?: w) }
        return (eigene + katalog).filter { !it.symbol.startsWith("^") && !it.symbol.contains('=') }.distinctBy { it.symbol }
    }

    // Je Subreddit zuletzt gelesene Beitraege, getrennt zwischengespeichert, damit ein lautes Subreddit die anderen
    // nicht verdraengt und jedes nur so oft abgerufen wird wie noetig.
    private val redditSubCache = ConcurrentHashMap<String, Pair<Long, de.troetenrepublik.app.market.RedditApi.Verlauf>>()

    // Stunden, die die deutschen Subreddits beim letzten Zaehlen lueckenlos gedeckt haben, wenn es weniger als 24 waren;
    // sonst null. Dazu die Subreddits, die dabei nicht erreichbar waren.
    @Volatile var redditDeAbdeckung: Int? = null
        private set
    @Volatile var redditDeFehlt: List<String> = emptyList()
        private set

    private val redditVorstandDatei by lazy { de.troetenrepublik.app.plattform.SichereDatei(java.io.File(store.ordner, "redditvorstand.json")) }
    private val redditVorstandLock = kotlinx.coroutines.sync.Mutex()

    private fun redditVorstandLesen(): de.troetenrepublik.app.engine.RedditVorstand.Vorstand = runCatching {
        trendJson.decodeFromString(de.troetenrepublik.app.engine.RedditVorstand.Vorstand.serializer(), redditVorstandDatei.lesen().toString(Charsets.UTF_8))
    }.getOrNull() ?: de.troetenrepublik.app.engine.RedditVorstand.Vorstand()

    // Beitraege eines Subreddits der letzten zwei Tage (15 Minuten zwischengespeichert). Ohne neue Antwort gilt ein
    // Abruf von vor hoechstens einer Stunde.
    private suspend fun redditSub(sub: String, jetzt: Long, s: de.troetenrepublik.app.data.Settings): de.troetenrepublik.app.market.RedditApi.Verlauf? {
        redditSubCache[sub]?.takeIf { jetzt - it.first < 15 * 60_000L }?.let { return it.second }
        val v = reddit.neueBis(sub, jetzt - 2 * 86_400_000L, s.keyRedditId, s.keyRedditSecret, maxSeiten = 10)
        if (v != null) { redditSubCache[sub] = jetzt to v; return v }
        return redditSubCache[sub]?.takeIf { jetzt - it.first < 3_600_000L }?.second
    }

    // Rangliste der deutschen Boersen-Subreddits: die Beitraege der letzten 24 Stunden aus
    // r/wallstreetbetsGER, r/mauerstrassenwetten, r/Aktien und r/Finanzen, je Subreddit seitenweise gelesen,
    // darin gezaehlt, welche Werte genannt werden. Reicht die Schnittstelle bei einem lauten Subreddit nicht 24 Stunden
    // zurueck, steht der tatsaechliche Zeitraum in redditDeAbdeckung. Die Veraenderung gegenueber den 24 Stunden davor
    // kommt aus den Beitraegen oder aus dem gespeicherten Stand von gestern.
    // Im Format von ApeWisdom, damit Verlauf und Anzeige dieselben sind.
    private suspend fun redditDeutsch(): List<MarketApi.Erwaehnung> {
        val s = settingsFlow.value
        if (!redditAktiv(s)) return emptyList()
        val ra = de.troetenrepublik.app.engine.RedditAuswertung
        val jetzt = System.currentTimeMillis()
        val gelesen = ArrayList<Pair<String, de.troetenrepublik.app.market.RedditApi.Verlauf>>()
        for (sub in ra.DEUTSCHE_SUBS) redditSub(sub, jetzt, s)?.let { gelesen += sub to it }
        redditDeFehlt = ra.DEUTSCHE_SUBS.filter { sub -> gelesen.none { it.first == sub } }
        if (gelesen.isEmpty()) return emptyList()
        val beitraege = gelesen.flatMap { it.second.beitraege }
        redditDeutschBeitraege = beitraege
        // Beginn des Zeitraums, den alle Subreddits lueckenlos decken; null, wenn jede Liste bis 48 Stunden zurueck oder
        // bis zu ihrem Ende gelesen ist
        val beginn = gelesen.filter { !it.second.vollstaendig }.maxOfOrNull { it.second.aeltesteMs ?: jetzt }
        val tag = 86_400_000L
        val voll24 = beginn == null || beginn <= jetzt - tag
        redditDeAbdeckung = if (voll24) null else ((jetzt - beginn!!) / 3_600_000L).toInt()
        val vollstaendig = voll24 && redditDeFehlt.isEmpty()
        val direkt = vollstaendig && beginn == null
        val vorstand = if (vollstaendig && !direkt) redditVorstandLock.withLock { de.troetenrepublik.app.engine.RedditVorstand.vorher(redditVorstandLesen(), jetzt) } else null
        val kandidaten = redditKandidaten()
        val gezaehlt = withContext(Dispatchers.Default) { ra.zaehlen(beitraege, kandidaten, jetzt, vorstand, direkt) }
        if (vollstaendig) redditVorstandLock.withLock {
            val neu = de.troetenrepublik.app.engine.RedditVorstand.merken(redditVorstandLesen(), jetzt, gezaehlt.associate { it.symbol to it.erwaehnungen })
            redditVorstandDatei.schreiben(trendJson.encodeToString(de.troetenrepublik.app.engine.RedditVorstand.Vorstand.serializer(), neu).toByteArray(Charsets.UTF_8))
        }
        return gezaehlt.mapIndexed { i, z -> MarketApi.Erwaehnung(i + 1, z.symbol, z.name, z.erwaehnungen, z.punkte, null, z.vorher) }
    }

    // Erwaehnungen eines Werts je Stunde der letzten 24 Stunden (aeltester zuerst) aus den zuletzt gelesenen Beitraegen der
    // deutschen Subreddits; null, solange keine gelesen sind.
    fun redditDeStunden(symbol: String, name: String): List<Int>? {
        val beitraege = redditDeutschBeitraege.takeIf { it.isNotEmpty() } ?: return null
        val sucher = de.troetenrepublik.app.engine.RedditAuswertung.Sucher(de.troetenrepublik.app.engine.RedditAuswertung.Kandidat(symbol, name))
        val zeiten = beitraege.distinctBy { it.id }.filter { sucher.findet(it.titel + "\n" + it.text) }.map { it.zeit }
        return de.troetenrepublik.app.engine.Verlaufsdarstellung.koerbe(zeiten, System.currentTimeMillis(), 24, 3_600_000L)
    }

    // Meisterwaehnte Werte auf Reddit laut ApeWisdom, 20 Minuten zwischengespeichert; leer ohne Antwort.
    // filter: all-stocks, wallstreetbets, stocks, investing, all-crypto und weitere (https://apewisdom.io/api/).
    // Unter RedditAuswertung.QUELLE zaehlt die App mit eigenem Reddit-Zugang die deutschen Subreddits selbst.
    suspend fun redditTrends(filter: String = "all-stocks", neu: Boolean = false): List<MarketApi.Erwaehnung> {
        val now = System.currentTimeMillis()
        redditCaches[filter]?.takeIf { !neu && now - it.first < 20 * 60_000L }?.let { return it.second }
        val liste = if (filter == de.troetenrepublik.app.engine.RedditAuswertung.QUELLE) runCatching { redditDeutsch() }.getOrDefault(emptyList())
            else runCatching { market.apeWisdom(filter) }.getOrDefault(emptyList())
        if (liste.isNotEmpty()) {
            redditCaches[filter] = now to liste
            if (filter == de.troetenrepublik.app.engine.RedditAuswertung.QUELLE) scope.launch { runCatching { redditMelden(liste) } }
            runCatching { trendEintragen(filter, liste, now) }
        }
        return liste.ifEmpty { redditCaches[filter]?.second.orEmpty() }
    }

    // Verlauf der Reddit-Erwaehnungen: ApeWisdom nennt nur 24 Stunden, die App schreibt je Tag mit
    private val trendDatei by lazy { de.troetenrepublik.app.plattform.SichereDatei(java.io.File(store.ordner, "trends.json")) }
    private val trendJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private val trendLock = kotlinx.coroutines.sync.Mutex()
    private val _trendArchiv = MutableStateFlow<de.troetenrepublik.app.engine.Trendverlauf.Archiv?>(null)

    // Der mitgeschriebene Verlauf (beim ersten Zugriff von der Datei gelesen); leer, solange nichts mitgeschrieben ist.
    suspend fun trendArchiv(): de.troetenrepublik.app.engine.Trendverlauf.Archiv = trendLock.withLock { trendArchivLaden() }

    private fun trendArchivLaden(): de.troetenrepublik.app.engine.Trendverlauf.Archiv =
        _trendArchiv.value ?: (runCatching {
            trendJson.decodeFromString(de.troetenrepublik.app.engine.Trendverlauf.Archiv.serializer(), trendDatei.lesen().toString(Charsets.UTF_8))
        }.getOrNull() ?: de.troetenrepublik.app.engine.Trendverlauf.Archiv()).also { _trendArchiv.value = it }

    private suspend fun trendEintragen(filter: String, liste: List<MarketApi.Erwaehnung>, jetzt: Long) = trendLock.withLock {
        val neu = de.troetenrepublik.app.engine.Trendverlauf.eintragen(
            trendArchivLaden(), filter, de.troetenrepublik.app.engine.Trendverlauf.tag(jetzt),
            liste.map { de.troetenrepublik.app.engine.Trendverlauf.Messung(it.ticker, it.name, it.erwaehnungen, it.erwaehnungenVorher) }, jetzt,
        )
        _trendArchiv.value = neu
        trendDatei.schreiben(trendJson.encodeToString(de.troetenrepublik.app.engine.Trendverlauf.Archiv.serializer(), neu).toByteArray(Charsets.UTF_8))
    }

    // Tages- und Wochenrueckblick: am Abend jedes Handelstags, gesammelt in rueckblicke.json
    private val rueckblickDatei by lazy { de.troetenrepublik.app.plattform.SichereDatei(java.io.File(store.ordner, "rueckblicke.json")) }
    private val rueckblickLock = kotlinx.coroutines.sync.Mutex()
    private val _rueckblicke = MutableStateFlow<de.troetenrepublik.app.engine.Tagesrueckblick.Archiv?>(null)

    // Die gesammelten Rueckblicke, neuester zuerst; null, solange sie nicht gelesen sind (rueckblickeLaden).
    val rueckblicke: StateFlow<de.troetenrepublik.app.engine.Tagesrueckblick.Archiv?> = _rueckblicke.asStateFlow()

    suspend fun rueckblickeLaden(): de.troetenrepublik.app.engine.Tagesrueckblick.Archiv = rueckblickLock.withLock { rueckblickArchiv() }

    private fun rueckblickArchiv(): de.troetenrepublik.app.engine.Tagesrueckblick.Archiv {
        _rueckblicke.value?.let { return it }
        val roh = runCatching { rueckblickDatei.lesen() }.getOrNull()
        // Verschluesselt und noch gesperrt: leer zeigen, nichts merken, nichts schreiben
        if (store.gesperrtFuer(roh)) return de.troetenrepublik.app.engine.Tagesrueckblick.Archiv()
        val a = roh?.let { r ->
            runCatching { trendJson.decodeFromString(de.troetenrepublik.app.engine.Tagesrueckblick.Archiv.serializer(), store.entpacken(r)!!.toString(Charsets.UTF_8)) }.getOrNull()
        } ?: de.troetenrepublik.app.engine.Tagesrueckblick.Archiv()
        _rueckblicke.value = a
        return a
    }

    private fun rueckblickSpeichern(a: de.troetenrepublik.app.engine.Tagesrueckblick.Archiv) {
        if (store.gesperrtFuer(runCatching { rueckblickDatei.lesen() }.getOrNull())) return
        _rueckblicke.value = a
        rueckblickDatei.schreiben(store.verpacken(trendJson.encodeToString(de.troetenrepublik.app.engine.Tagesrueckblick.Archiv.serializer(), a).toByteArray(Charsets.UTF_8)))
    }

    // Nach Ein- oder Ausschalten der Sperre die Rueckblicke im passenden Format neu schreiben.
    private suspend fun rueckblickNeuVerpacken() = rueckblickLock.withLock { rueckblickSpeichern(rueckblickArchiv()) }

    // Maerkte der Positionen im Depot (ohne Positionen die US-Boerse): nach ihrem Schluss ist der Rueckblick endgueltig.
    private fun rueckblickMaerkte(): List<String> = de.troetenrepublik.app.engine.Tagesrueckblick.maerkteDerPositionen(
        account.value.positions.filter { !it.isDerivative && it.qty > 0 }.map { it.symbol },
    )

    // Ist ein Rueckblick faellig (werktags ab der gewaehlten Uhrzeit, morgens fuer den Vortag), wird er erstellt, gesammelt
    // und gemeldet, freitags dazu der Wochenrueckblick. Im Vordergrund alle fuenf Minuten, im Hintergrund mit den
    // Nachrichten.
    suspend fun rueckblickPruefen() {
        val s = runCatching { settings.flow.first() }.getOrDefault(settingsFlow.value)
        if (!s.tagesrueckblick || store.gesperrt.value) return
        val jetzt = System.currentTimeMillis()
        val tr = de.troetenrepublik.app.engine.Tagesrueckblick
        val maerkte = rueckblickMaerkte()
        // Vor Schluss der Maerkte gilt der Rueckblick als vorlaeufig und wird danach ersetzt, ohne zweite Mitteilung
        var ersetzt = false
        val r = rueckblickLock.withLock {
            val tag = tr.faellig(jetzt, rueckblickArchiv(), ab = s.rueckblickMinute, maerkte = maerkte) ?: return
            val vorher = rueckblickArchiv().tage.firstOrNull { it.tag == tag }
            val r = rueckblickErstellen(tag, jetzt, vorlaeufig = tr.vorlaeufigPlan(jetzt, tag, maerkte), s) ?: return
            ersetzt = vorher != null && vorher.vorlaeufig && vorher.erstellt >= tr.geplantAm(tag, s.rueckblickMinute)
            rueckblickSpeichern(tr.eintragen(rueckblickArchiv(), r))
            r
        }
        if (!s.notifications || !s.rueckblickMitteilung) return
        val n = notifier ?: return
        I18n.lang = s.sprache
        if (!ersetzt) n.notify(Notifier.CHANNEL_RUECKBLICK, S.rbMitteilungTitel(Fmt.dayMonth(de.troetenrepublik.app.engine.Tagesrueckblick.beginn(r.tag))), rueckblickKurz(r), ziel = "rueckblick")
        // Das Wochenfazit kommt erst mit dem endgueltigen Freitag
        if (!r.vorlaeufig && de.troetenrepublik.app.engine.Tagesrueckblick.wochenende(r.tag)) {
            val montag = de.troetenrepublik.app.engine.Tagesrueckblick.montag(r.tag)
            val w = de.troetenrepublik.app.engine.Tagesrueckblick.wochen(rueckblickArchiv()).firstOrNull { it.montag == montag } ?: return
            n.notify(
                Notifier.CHANNEL_RUECKBLICK,
                S.rbWochenTitel(Fmt.dayMonth(de.troetenrepublik.app.engine.Tagesrueckblick.beginn(w.montag)), Fmt.dayMonth(de.troetenrepublik.app.engine.Tagesrueckblick.beginn(w.freitag))),
                wocheKurz(w), ziel = "rueckblick",
            )
        }
    }

    // [0.73] Mitteilung "Betrifft dein Depot": hoechstens eine am Tag, nur bei Ereignissen, die genannte Werte im Depot treffen
    // (WeltMeldung). Engpaesse kommen aus dem Speicher oder dem zwischengespeicherten Abruf, Spannungsgebiete nur aus dem
    // Speicher.
    suspend fun weltMeldungPruefen() {
        val s = settingsFlow.value
        val n = notifier ?: return
        if (!s.mitteilungWelt || !s.notifications || store.gesperrt.value) return
        val engine = de.troetenrepublik.app.engine.WeltMeldung
        val jetzt = System.currentTimeMillis()
        val heute = jetzt / 86_400_000L
        val datei = de.troetenrepublik.app.plattform.SichereDatei(java.io.File(store.ordner, "welt-mitteilung.json"))
        val gemerkt = withContext(Dispatchers.IO) { runCatching { engine.lesen(datei.lesen().toString(Charsets.UTF_8)) }.getOrNull() } ?: de.troetenrepublik.app.engine.WeltMeldung.Gemerkt()
        if (gemerkt.tag == heute) return
        val (depot, _) = wirkungsBestand()
        if (depot.isEmpty()) return
        val u = runCatching { engpassGespeichert() }.getOrNull() ?: runCatching { engpassUebersicht() }.getOrNull()
        val spannungen = spannungenGespeichert()
        val m = engine.waehlen(de.troetenrepublik.app.engine.Einflussverzeichnis.geladen, engine.ereignisse(u, spannungen), depot, gemerkt, heute) ?: return
        withContext(Dispatchers.IO) { runCatching { datei.schreiben(engine.schreiben(engine.merken(gemerkt, m, heute)).toByteArray(Charsets.UTF_8)) } }
        I18n.lang = s.sprache
        val kennung = m.ereignis.groesse.substringAfter(':')
        val was = if (m.ereignis.typ == de.troetenrepublik.app.engine.Einflussverzeichnis.Typ.ENGPASS)
            S.mtWeltEngpass(S.engName(kennung), Fmt.signedPct(m.ereignis.wert, 0))
        else S.mtWeltSpannung(S.spName(kennung), Fmt.num(m.ereignis.wert, 1))
        n.notify(Notifier.CHANNEL_WELT, S.mtWelt, S.mtWeltText(was, S.mtWeltWerte(m.namen, m.rest)), ziel = "wirkung/" + m.ereignis.groesse)
    }

    // Den heutigen Rueckblick jetzt erstellen (von Hand); vor der gewaehlten Uhrzeit gilt er als vorlaeufig und wird dann ersetzt.
    suspend fun rueckblickJetzt(): Boolean {
        val s = settingsFlow.value
        val jetzt = System.currentTimeMillis()
        return rueckblickLock.withLock {
            val vorlaeufig = de.troetenrepublik.app.engine.Tagesrueckblick.vorlaeufig(jetzt, s.rueckblickMinute)
            val r = rueckblickErstellen(de.troetenrepublik.app.engine.Tagesrueckblick.tag(jetzt), jetzt, vorlaeufig, s) ?: return@withLock false
            rueckblickSpeichern(de.troetenrepublik.app.engine.Tagesrueckblick.eintragen(rueckblickArchiv(), r))
            true
        }
    }

    // Rueckblick eines Tags fuer den Handelskalender: der gespeicherte, sonst bei einem vergangenen Handelstag ein
    // nachtraeglich erstellter, der im Archiv bleibt. Null, wenn dafuer keine Kurse erreichbar sind.
    suspend fun rueckblickFuerTag(tag: Long): de.troetenrepublik.app.engine.Tagesrueckblick.Rueckblick? {
        rueckblickeLaden().tage.firstOrNull { it.tag == tag }?.let { return it }
        return rueckblickLock.withLock {
            rueckblickArchiv().tage.firstOrNull { it.tag == tag }?.let { return@withLock it }
            val r = rueckblickNachtraeglich(tag) ?: return@withLock null
            rueckblickSpeichern(de.troetenrepublik.app.engine.Tagesrueckblick.eintragen(rueckblickArchiv(), r))
            r
        }
    }

    // Aktiensplits der Werte aus uebernommenen Depots (Import), deren Buchungen vor dem Split noch die alte Stueckzahl tragen.
    // Abgerufen wird nur fuer Kuerzel mit Importbuchungen vor heute, einen Tag zwischengespeichert; ohne Netz bleibt es
    // bei dem, was schon gemerkt ist.
    private suspend fun importSplits(konto: Account): Map<String, List<de.troetenrepublik.app.engine.Splits.Split>> {
        val jetzt = System.currentTimeMillis()
        val heute = jetzt / 86_400_000L * 86_400_000L
        val symbole = konto.transactions
            .filter { (it.type == TxType.BUY || it.type == TxType.SELL) && it.via.startsWith("IMPORT") && it.ts < heute && it.symbol.isNotBlank() && !it.symbol.startsWith(de.troetenrepublik.app.engine.TradeRepublicImport.OHNE_KURS) }
            .map { it.symbol }.distinct()
        val fehlend = symbole.filter { (splitCache[it]?.first ?: 0L) < jetzt - 24 * 3_600_000L }
        if (fehlend.isNotEmpty()) {
            val gleichzeitig = kotlinx.coroutines.sync.Semaphore(4)
            coroutineScope {
                fehlend.map { sym -> async { gleichzeitig.withPermit { runCatching { market.splits(sym) }.getOrNull()?.let { splitCache[sym] = jetzt to it } } } }.awaitAll()
            }
        }
        return symbole.associateWith { splitCache[it]?.second.orEmpty() }.filterValues { it.isNotEmpty() }
    }

    // Splitlisten fuer Meldungen mit Preis und Stueckzahl in der Einheit ihres Tages (Copytrading): je Kuerzel einen Tag
    // zwischengespeichert, vier Abrufe gleichzeitig; alles Bekannte geht zugleich an SplitFaktoren. Ohne Netz bleibt der
    // Stand, wie er ist.
    suspend fun splitsHolen(symbole: Collection<String>) {
        val jetzt = System.currentTimeMillis()
        val alle = symbole.filter { it.isNotBlank() }.distinct()
        for (sym in alle) splitCache[sym]?.let { de.troetenrepublik.app.engine.SplitFaktoren.setzen(sym, it.second) }
        val fehlend = alle.filter { (splitCache[it]?.first ?: 0L) < jetzt - 24 * 3_600_000L }
        if (fehlend.isEmpty()) return
        val gleichzeitig = kotlinx.coroutines.sync.Semaphore(4)
        coroutineScope {
            fehlend.map { sym ->
                async {
                    gleichzeitig.withPermit {
                        runCatching { market.splits(sym) }.getOrNull()?.let {
                            splitCache[sym] = jetzt to it
                            de.troetenrepublik.app.engine.SplitFaktoren.setzen(sym, it)
                        }
                    }
                }
            }.awaitAll()
        }
    }

    // Kurse aus der Vergangenheit aendern sich nicht: sechs Stunden gemerkt, je Kuerzel und Zeitraum.
    private val tagesKurseCache = ConcurrentHashMap<String, Pair<Long, List<Candle>>>()

    // Tagesschlusskurse mehrerer Kuerzel, nur die fehlenden werden abgerufen (je 20 in einer Abfrage).
    private suspend fun tagesKurseGebuendelt(symbole: List<String>, zeitraum: String): Map<String, List<Candle>> {
        val jetzt = System.currentTimeMillis()
        val aus = HashMap<String, List<Candle>>()
        val fehlend = symbole.distinct().filter { sym ->
            val c = tagesKurseCache["$sym|$zeitraum"]?.takeIf { jetzt - it.first < 6 * 3_600_000L }
            if (c != null) aus[sym] = c.second
            c == null
        }
        for (gruppe in fehlend.chunked(20)) {
            val antwort = runCatching { market.schlusskurse(gruppe, zeitraum) }.getOrDefault(emptyMap())
            for ((sym, kerzen) in antwort) { tagesKurseCache["$sym|$zeitraum"] = jetzt to kerzen; aus[sym] = kerzen }
        }
        return aus
    }

    // Rueckblick fuer einen vergangenen Tag: Bestand aus den Buchungen, Tagesaenderungen aus Tageskursen, Maerkte und
    // Branchen aus den Indexkursen, Schlagzeilen nur, soweit sie lokal noch vorliegen. Gerechnet wird abseits des UI-Threads.
    private suspend fun rueckblickNachtraeglich(tag: Long): de.troetenrepublik.app.engine.Tagesrueckblick.Rueckblick? {
        val tr = de.troetenrepublik.app.engine.Tagesrueckblick
        val jetzt = System.currentTimeMillis()
        if (tag >= tr.tag(jetzt)) return null
        val zeitraum = tr.abrufZeitraum(tag, jetzt) ?: return null
        val konto = account.value
        val splits = importSplits(konto)
        val bestand = withContext(Dispatchers.Default) { tr.bestandAm(konto, tag, splits = splits) }
        val indizes = tr.MAERKTE.map { it.third } + de.troetenrepublik.app.engine.Branchentrends.BRANCHEN.map { it.symbol }
        val gleichzeitig = kotlinx.coroutines.sync.Semaphore(4)
        val (marktKurse, kurse) = coroutineScope {
            val m = async { tagesKurseGebuendelt(indizes, zeitraum) }
            val k = bestand.map { b -> async { gleichzeitig.withPermit { b.symbol to dailyCloses(b.symbol) } } }.awaitAll().toMap()
            m.await() to k
        }
        val s = settingsFlow.value
        runCatching { news.laden(NewsRepository.Auftrag(newsQuellen(s), s.sprache == Lang.DE, ohneAbruf = Rss.NUR_ANSICHT, nurEnglisch = s.nurEnglischeQuellen)) }
        val nachrichten = news.stand.value.eintraege.filter { !Rss.nurAnsicht(it) }
        return withContext(Dispatchers.Default) { tr.vergangenheit(tag, jetzt, bestand, kurse, marktKurse, nachrichten, nurEnglisch = s.wichtigNurEnglisch) }
    }

    // Daten des Tags sammeln: Maerkte und Depot aus der Sammelabfrage, Schlagzeilen der Feeds und je bewegtem Wert.
    private suspend fun rueckblickErstellen(tag: Long, jetzt: Long, vorlaeufig: Boolean, s: Settings): de.troetenrepublik.app.engine.Tagesrueckblick.Rueckblick? {
        val tr = de.troetenrepublik.app.engine.Tagesrueckblick
        runCatching { news.laden(NewsRepository.Auftrag(newsQuellen(s), s.sprache == Lang.DE, ohneAbruf = Rss.NUR_ANSICHT, nurEnglisch = s.nurEnglischeQuellen)) }
        val konto = account.value
        val einfach = konto.positions.filter { !it.isDerivative && it.qty > 0 }
        val branchenEtfs = de.troetenrepublik.app.engine.Branchentrends.BRANCHEN.map { it.symbol }
        val q = runCatching {
            market.snapshot((tr.MAERKTE.map { it.third } + einfach.map { it.symbol } + branchenEtfs + konto.watchlist).distinct(), 10 * 60_000L)
        }.getOrDefault(emptyMap())
        val markt = tr.MAERKTE.mapNotNull { (k, _, sym) -> q[sym]?.takeIf { it.prevClose > 0 }?.let { de.troetenrepublik.app.engine.Tagesrueckblick.Markt(k, it.changePct) } }
        // Ohne Kurse (kein Netz) kein Rueckblick; der naechste Versuch kommt mit der naechsten Pruefung
        if (markt.isEmpty()) return null
        val positionen = einfach.groupBy { it.symbol }.mapNotNull { (sym, l) ->
            val k = q[sym] ?: return@mapNotNull null
            val stueck = l.sumOf { it.qty }
            val name = l.first().name.ifBlank { k.name.ifBlank { sym } }
            de.troetenrepublik.app.engine.Tagesrueckblick.Position(sym, name, stueck * k.price, if (k.prevClose > 0) stueck * (k.price - k.prevClose) else 0.0,
                de.troetenrepublik.app.market.Wichtig.depotBegriffe(listOf(name), listOf(sym)))
        } + valuation.value.positions.filter { it.position.isDerivative && it.value != 0.0 }.map { vp ->
            de.troetenrepublik.app.engine.Tagesrueckblick.Position(vp.key, vp.position.name, vp.value, vp.dayChange, de.troetenrepublik.app.market.Wichtig.depotBegriffe(listOf(vp.position.name), emptyList()))
        }
        // Branchen des Tages, grosse Aktien am Markt (wie die Heatmap) und die Watchlist
        val branchen = branchenEtfs.mapNotNull { sym -> q[sym]?.takeIf { it.prevClose > 0 }?.let { de.troetenrepublik.app.engine.Tagesrueckblick.Markt(sym, it.changePct) } }
        val marktwerte = runCatching { heatmap(HeatmapQuelle.MARKT) }.getOrDefault(emptyList())
            .mapNotNull { w -> w.aenderung?.let { de.troetenrepublik.app.engine.Tagesrueckblick.Spitze(w.symbol, w.name, it) } }
        val merkliste = konto.watchlist.mapNotNull { sym ->
            val k = q[sym]?.takeIf { it.prevClose > 0 } ?: return@mapNotNull null
            de.troetenrepublik.app.engine.Tagesrueckblick.Spitze(sym, de.troetenrepublik.app.market.Symbols.find(sym)?.name ?: k.name.ifBlank { sym }, k.changePct)
        }
        // Nachrichten zum Wert fuer die staerksten Bewegungen im Depot, die Spitzen am Markt und die Watchlist
        val beweger = positionen.filter { kotlin.math.abs(it.prozent) >= tr.MIN_PROZENT }.sortedByDescending { kotlin.math.abs(it.aenderung) }.take(tr.BEWEGUNGEN)
            .map { it.symbol }.filter { s -> einfach.any { it.symbol == s } }
        val spitzen = tr.spitzen(marktwerte, hoch = true) + tr.spitzen(marktwerte, hoch = false) +
            tr.merklisteAuswahl(merkliste, positionen.map { it.symbol }.toSet())
        val eigene = kotlinx.coroutines.coroutineScope {
            (beweger + spitzen.map { it.symbol }).distinct().map { sym ->
                async {
                    sym to runCatching { newsFor(sym) }.getOrDefault(emptyList()).map { n ->
                        Rss.Eintrag(n.title, n.link, n.published, n.summary, herausgeber = n.source, quelle = n.source)
                    }
                }
            }.awaitAll().toMap()
        }
        return tr.erstellen(tag, jetzt, markt, positionen, news.stand.value.eintraege.filter { !Rss.nurAnsicht(it) }, eigene, vorlaeufig,
            branchen = branchen, marktwerte = marktwerte, merkliste = merkliste, nurEnglisch = settingsFlow.value.wichtigNurEnglisch)
    }

    // Kurzfassung fuer die Mitteilung: das Fazit des Tags, dazu die wichtigste Schlagzeile.
    private fun rueckblickKurz(r: de.troetenrepublik.app.engine.Tagesrueckblick.Rueckblick): String {
        val zeilen = mutableListOf(de.troetenrepublik.app.ui.rueckblickFazit(r, branchen = false))
        r.bewegungen.firstOrNull()?.grund?.let { zeilen += S.rbWahrscheinlichKurz(it.titel).replaceFirstChar { c -> c.uppercase() } }
        r.nachrichten.firstOrNull()?.let { if (r.bewegungen.firstOrNull()?.grund?.titel != it.titel) zeilen += "• " + it.titel }
        return zeilen.filter { it.isNotBlank() }.joinToString("\n")
    }

    private fun wocheKurz(w: de.troetenrepublik.app.engine.Tagesrueckblick.Woche): String = de.troetenrepublik.app.ui.wochenFazit(w, branchen = false)

    // Die Quellen der Trends auf Reddit (ApeWisdom-Filter): alle Aktien-Subreddits, r/wallstreetbets, Krypto.
    val trendQuellen = listOf("all-stocks", "wallstreetbets", "all-crypto")

    // Im Hintergrund (etwa alle 30 Minuten mit den Nachrichten): den Verlauf weiterschreiben, hoechstens alle sechs
    // Stunden je Quelle und nur, wenn die Trends schon einmal geoeffnet wurden (dann gibt es einen Verlauf).
    suspend fun trendsMitschreiben() {
        val a = trendArchiv()
        if (a.tage.isEmpty()) return
        val jetzt = System.currentTimeMillis()
        for (q in trendQuellen) if (jetzt - (a.abgerufen[q] ?: 0L) > 6 * 3_600_000L) runCatching { redditTrends(q, neu = true) }
        // Deutsche Subreddits mit eigenem Zugang: alle zwei Stunden, dazu der Hinweis auf viel besprochene eigene Werte
        val q = de.troetenrepublik.app.engine.RedditAuswertung.QUELLE
        if (redditAktiv() && jetzt - (a.abgerufen[q] ?: 0L) > 2 * 3_600_000L) runCatching { redditTrends(q, neu = true) }
    }

    // Mitteilung, wenn ein Wert aus Depot oder Watchlist in den deutschen Subreddits ploetzlich viel besprochen wird:
    // mindestens 5 Erwaehnungen in 24 Stunden und mindestens dreimal so viele wie in den 24 Stunden davor;
    // je Wert hoechstens einmal am Tag.
    suspend fun redditMelden(liste: List<MarketApi.Erwaehnung>) {
        val s = runCatching { settings.flow.first() }.getOrDefault(settingsFlow.value)
        if (!s.notifications || liste.isEmpty()) return
        val n = notifier ?: return
        val konto = account.value
        val eigene = (konto.positions.map { it.symbol } + konto.watchlist).map { de.troetenrepublik.app.engine.Trendverlauf.normal(it) }.toSet()
        val heute = de.troetenrepublik.app.engine.Trendverlauf.tag(System.currentTimeMillis())
        val schon = s.redditGemeldet.split(',').filter { it.endsWith(":$heute") }.map { it.substringBefore(':') }.toSet()
        val melden = liste.filter { e ->
            val k = de.troetenrepublik.app.engine.Trendverlauf.normal(e.ticker)
            val vorher = e.erwaehnungenVorher
            k in eigene && k !in schon && vorher != null && e.erwaehnungen >= 5 && e.erwaehnungen >= 3 * maxOf(vorher, 1)
        }
        if (melden.isEmpty()) return
        settings.update { it.copy(redditGemeldet = (schon + melden.map { e -> de.troetenrepublik.app.engine.Trendverlauf.normal(e.ticker) }).joinToString(",") { k -> "$k:$heute" }) }
        I18n.lang = s.sprache
        val eigeneSymbole = (konto.positions.map { it.symbol } + konto.watchlist).associateBy { de.troetenrepublik.app.engine.Trendverlauf.normal(it) }
        for (e in melden.take(3)) n.notify(Notifier.CHANNEL_NEWS, S.redditMeldungTitel(e.name.ifBlank { e.ticker }), mitKurszeile(S.redditMeldungText(e.erwaehnungen, e.erwaehnungenVorher ?: 0), eigeneSymbole[de.troetenrepublik.app.engine.Trendverlauf.normal(e.ticker)]?.let { kursZeile(it) }), null, de.troetenrepublik.app.data.WidgetZiel.instrument(e.ticker))
    }

    private var yahooTrendCache: Pair<Long, List<Quote>>? = null

    // Bei Yahoo Finance gerade gefragte Werte: die meistaufgerufenen der eigenen Region und der USA, dazu
    // die meistgehandelten US-Aktien; mit Kurs. 15 Minuten zwischengespeichert.
    suspend fun yahooTrends(): List<Quote> {
        val now = System.currentTimeMillis()
        yahooTrendCache?.takeIf { now - it.first < 15 * 60_000L }?.let { return it.second }
        val region = settingsFlow.value.land.ifBlank { "DE" }.uppercase()
        val symbole = coroutineScope {
            val eigene = async { if (region != "US") runCatching { market.trending(region) }.getOrDefault(emptyList()) else emptyList() }
            val us = async { runCatching { market.trending("US") }.getOrDefault(emptyList()) }
            val aktiv = async { runCatching { market.screener("most_actives") }.getOrDefault(emptyList()) }
            (eigene.await() + us.await() + aktiv.await().take(10)).distinct().filterNot { it.startsWith("^") }.take(30)
        }
        if (symbole.isEmpty()) return yahooTrendCache?.second.orEmpty()
        val kurse = runCatching { market.snapshot(symbole) }.getOrDefault(emptyMap())
        val liste = symbole.mapNotNull { kurse[it] }
        if (liste.isNotEmpty()) yahooTrendCache = now to liste
        return liste
    }

    // Interesse an einem Wert auf Wikipedia: Durchschnitt und Veraenderung, dazu die Aufrufe je Tag (aelteste zuerst).
    data class WikiInteresse(val wert: de.troetenrepublik.app.engine.Interesse.Wert, val aufrufe: List<Int>)

    private val wikiCache = HashMap<String, Pair<Long, WikiInteresse?>>()

    // Interesse an einem Wert auf Wikipedia: Aufrufe der letzten sieben Tage gegen
    // die vier Wochen davor, ohne Bots. Null ohne bekannten Artikel oder ohne Daten; sechs Stunden zwischengespeichert.
    suspend fun wikiInteresseFuer(symbol: String): WikiInteresse? {
        val now = System.currentTimeMillis()
        wikiCache[symbol]?.takeIf { now - it.first < 6 * 3_600_000L }?.let { return it.second }
        val info = companies.value[symbol] ?: return null
        val (sprache, url) = when {
            info.articleEn.isNotBlank() -> "en" to info.articleEn
            info.articleDe.isNotBlank() -> "de" to info.articleDe
            else -> return null
        }
        val artikel = url.substringAfter("/wiki/", "").takeIf { it.isNotBlank() } ?: return null
        val aufrufe = runCatching { market.wikiAufrufe(sprache, artikel) }.getOrNull() ?: return null
        val aus = de.troetenrepublik.app.engine.Interesse.berechnen(aufrufe)?.let { WikiInteresse(it, aufrufe) }
        wikiCache[symbol] = now to aus
        return aus
    }

    // Tagesschlusskurse der Branchen-ETFs (engine.Branchentrends) ueber ein Jahr, fuer bis zu sechs Monate;
    // 15 Minuten zwischengespeichert.
    suspend fun branchenKurse(neu: Boolean = false): Map<String, List<Candle>> {
        val now = System.currentTimeMillis()
        branchenCache?.takeIf { !neu && now - it.first < 15 * 60_000L }?.let { return it.second }
        val aus = HashMap<String, List<Candle>>()
        // Dazu VIX und zehnjaehrige US-Rendite fuer die Sektorrotation, im selben Abruf
        (de.troetenrepublik.app.engine.Branchentrends.BRANCHEN.map { it.symbol } + listOf("^VIX", "^TNX")).chunked(20).forEach { aus.putAll(schlusskurse(it, "1y")) }
        if (aus.isNotEmpty()) branchenCache = now to aus
        return aus.ifEmpty { branchenCache?.second.orEmpty() }
    }

    // Tagesschlusskurse ueber bis zu zehn Jahre (EUR), leer ohne Daten.
    suspend fun dailyCloses(symbol: String): List<Candle> = runCatching { market.daily(symbol) }.getOrDefault(emptyList())

    // Alle Tageskurse seit Beginn der Notierung (Handelswaehrung), leer ohne Daten.
    suspend fun gesamtHistorie(symbol: String): List<Candle> = runCatching { market.gesamtHistorie(symbol) }.getOrDefault(emptyList())

    // Dividendentermine eines Werts (Ex-Tage, EUR je Stueck) des letzten Jahres. Wie dividendenVerlauf: eine leere
    // Antwort nur 30 Minuten gemerkt, ein Fehler gar nicht, ein Abbruch (Seite verlassen) laeuft durch.
    suspend fun dividendsFor(symbol: String): List<MarketApi.DividendEvent> {
        val now = System.currentTimeMillis()
        dividendCache[symbol]?.takeIf { !dividendeVeraltet(it, now) }?.let { return it.second }
        val list = try { market.dividends(symbol) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) {
            return dividendCache[symbol]?.second.orEmpty()
        }
        dividendCache[symbol] = now to list
        return list
    }

    // Gemerkte Dividenden sind veraltet: nach zwoelf Stunden, eine leere Antwort schon nach 30 Minuten.
    private fun dividendeVeraltet(e: Pair<Long, List<MarketApi.DividendEvent>>, now: Long): Boolean =
        now - e.first >= if (e.second.isEmpty()) 30 * 60_000L else 12 * 3_600_000L

    // [0.75] Dividenden der letzten zwei Jahre fuer die Prognose (Werkzeug, Kalender, Widget): genug Vorlauf, um einen
    // Ex-Tag zu erkennen, der dieses Jahr spaeter liegt als im Vorjahr (PortfolioTools.expectedDividends). Gemerkt wie
    // dividendsFor.
    private val dividendPrognoseCache = ConcurrentHashMap<String, Pair<Long, List<MarketApi.DividendEvent>>>()
    suspend fun dividendenPrognose(symbol: String): List<MarketApi.DividendEvent> {
        val now = System.currentTimeMillis()
        dividendPrognoseCache[symbol]?.takeIf { !dividendeVeraltet(it, now) }?.let { return it.second }
        val list = try { market.dividends(symbol, "2y") } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) {
            return dividendPrognoseCache[symbol]?.second.orEmpty()
        }
        dividendPrognoseCache[symbol] = now to list
        return list
    }

    // Dividenden der letzten zehn Jahre (EUR je Stueck zum heutigen Kurs) fuer die kompakte Dividende, 12 Stunden gemerkt.
    private val dividendVerlaufCache = ConcurrentHashMap<String, Pair<Long, List<MarketApi.DividendEvent>>>()
    suspend fun dividendenVerlauf(symbol: String): List<MarketApi.DividendEvent> {
        val now = System.currentTimeMillis()
        // Leere Antworten nur 30 Minuten merken, dann einen neuen Versuch
        dividendVerlaufCache[symbol]?.let { (zeit, liste) ->
            if (now - zeit < if (liste.isEmpty()) 30 * 60_000L else 12 * 3_600_000L) return liste
        }
        // Ein Abbruch (Seite verlassen) wird nicht als leere Antwort gemerkt
        val list = try { market.dividendHistory(symbol) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { emptyList() }
        dividendVerlaufCache[symbol] = now to list
        return list
    }

    // Kursverlauf eines Derivats aus der Historie des Basiswerts (siehe DerivatVerlauf). Ein ausgeknocktes Produkt
    // zeigt den Tag bis zum Knock-out; Faktor-Zertifikate werden aus den Tagesrenditen des Basiswerts rueckwaerts
    // vom heutigen Preis modelliert.
    suspend fun derivativeSeries(d: Derivative, range: Range): Series {
        val base = market.series(d.underlying, range)
        if (base.candles.isEmpty()) return Series(emptyList())
        val q = quotes.value[d.underlying]
        val fx = q?.fx ?: 1.0
        val rate = riskFree
        // Der modellierte Verlauf eines Faktor-Zertifikats oder Optionsscheins gilt fuer den ganzen Zeitraum, beide haben
        // keine Schwelle. Ein Knock-out beginnt nach dem letzten Beruehren seiner Schwelle (davor waere er ausgeknockt).
        val faktor = d.kind == DerivativeKind.FACTOR_LONG || d.kind == DerivativeKind.FACTOR_SHORT
        val candles = if (faktor || d.kind.isOption) base.candles else {
            val schwelle = Pricing.barrierEur(d, fx)
            val bis = if (d.isKnockedOut) d.knockedOutAt else Long.MAX_VALUE
            val letzte = base.candles.indexOfLast { c -> c.t < bis && if (d.kind.isLong) c.close <= schwelle else c.close >= schwelle }
            base.candles.drop(letzte + 1)
        }
        val out = if (faktor) {
            val heute = if (q != null && q.price > 0) Pricing.price(d, q.price, System.currentTimeMillis(), fx, rate) else 0.0
            // Der Tageschart kennt den Vortagesschluss nicht aus den Kerzen; er kommt aus dem Kurs
            val vortag = if (range == Range.D1) (q?.prevClose ?: 0.0) else 0.0
            DerivatVerlauf.faktor(d, candles, heute, MarketHours.zone, vortag, rate)
        } else DerivatVerlauf.bewertet(d, candles, fx, rate)
        return base.copy(candles = out)
    }

    // ------------------------------------------------------------ Depotverlauf

    // Verlauf des Depots: Kurve, Anteil der Zeitachse vor dem ersten Kauf, Handelsfenster des Tages.
    data class PortfolioSeries(val candles: List<Candle>, val startFraction: Float = 0f, val dayStart: Long = 0L, val dayEnd: Long = 0L)

    // Bausteine der Depotkurve: der Rohverlauf (Summe Stueck x Kurs, springt bei Kaeufen) und die
    // Geldbewegungen des Zeitraums (Zeit, Betrag; Kauf positiv). source ist die Aufloesung der Kurse.
    data class PortfolioParts(val raw: PortfolioSeries, val flows: List<Pair<Long, Double>>, val source: Range)

    private val partsCache = ConcurrentHashMap<String, Pair<Long, PortfolioParts>>()

    // Verlauf des Depots aus echten Kursen und dem tatsaechlichen Bestand zu jedem Zeitpunkt des Zeitrasters:
    // Summe(Stueck x Kurs), der Bestand aus den Buchungen rekonstruiert. Die Kurve beginnt mit dem ersten Kauf.
    // Verkaufte Werte zaehlen fuer die Zeit, in der sie gehalten wurden; Guthaben zaehlt nicht. Ein Kauf laesst
    // die Kurve nicht springen, ein Verkauf sie nicht abstuerzen: Jeder Punkt wird um die spaeteren Netto-Zufluesse
    // ergaenzt, die Kurve endet beim heutigen Wert und beginnt beim netto investierten Betrag.
    suspend fun portfolioSeries(range: Range): PortfolioSeries {
        val parts = portfolioParts(range) ?: return PortfolioSeries(emptyList())
        return withContext(Dispatchers.Default) { parts.raw.copy(candles = Trading.flowNeutral(parts.raw.candles, parts.flows)) }
    }

    // Depotkurve in einem frei gewaehlten Zeitraum: der Verlauf des naechstgroesseren festen Zeitraums,
    // zugeschnitten auf die Tage vonTag bis bisTag. Die Kurve endet beim Wert am letzten Tag; spaetere Kaeufe und
    // Verkaeufe zaehlen nicht mit.
    suspend fun portfolioZeitraum(vonTag: Long, bisTag: Long): PortfolioSeries {
        val now = System.currentTimeMillis()
        val (von, bis) = de.troetenrepublik.app.engine.Zeitraum.grenzen(vonTag, bisTag, now)
        val parts = portfolioParts(de.troetenrepublik.app.engine.Zeitraum.grob(von, now)) ?: return PortfolioSeries(emptyList())
        return withContext(Dispatchers.Default) {
            val roh = parts.raw.candles.filter { it.t in von..bis }
            if (roh.size < 2) PortfolioSeries(emptyList())
            else PortfolioSeries(Trading.flowNeutral(roh, parts.flows.filter { it.first <= bis }))
        }
    }

    // Aufloesung der Kurse fuer einen Zeitraum: "Max" richtet sich nach dem Alter des Depots.
    private fun sourceRange(range: Range, first: Long, now: Long): Range {
        // "Max" heisst seit dem ersten Kauf: Aufloesung nach Alter des Depots, damit die Kurve die
        // ganze Breite fuellt statt als Strich am rechten Rand zu enden.
        val age = if (first > 0) now - first else 0L
        return if (range != Range.MAX) range else when {
            age <= 2L * 86_400_000L -> Range.D1
            age <= 8L * 86_400_000L -> Range.W1
            age <= 35L * 86_400_000L -> Range.M1
            age <= 370L * 86_400_000L -> Range.Y1
            else -> Range.MAX
        }
    }

    // Kursreihe eines Vergleichswerts in derselben Aufloesung wie die Depotkurve des Zeitraums (EUR).
    suspend fun benchmarkSeries(symbol: String, range: Range): List<Candle> {
        val now = System.currentTimeMillis()
        val source = sourceRange(range, Trading.firstPurchase(account.value), now)
        return runCatching { market.series(symbol, source).candles }.getOrDefault(emptyList())
    }

    // Rohverlauf und Geldbewegungen des Zeitraums; null, wenn sich kein Verlauf bilden laesst.
    suspend fun portfolioParts(range: Range): PortfolioParts? = coroutineScope {
        val acc = account.value
        val now = System.currentTimeMillis()
        val first = Trading.firstPurchase(acc)
        val source = sourceRange(range, first, now)
        val rangeStart = when (range) {
            Range.D1 -> MarketHours.dayStart(now)
            Range.W1 -> now - 7L * 86_400_000L
            Range.M1 -> now - 31L * 86_400_000L
            Range.M3 -> now - 92L * 86_400_000L
            Range.M6 -> now - 183L * 86_400_000L
            Range.Y1 -> now - 366L * 86_400_000L
            Range.MAX -> first
        }
        // Alle Schluessel, die im Zeitraum gehalten wurden: heutige Positionen plus Buchungen im Zeitraum
        val held = LinkedHashMap<String, Position>()
        acc.positions.forEach { held[it.key] = it }
        // Verkaufte Derivate zaehlen mit: mit ihrem Produkt, wenn die Buchungen es tragen, sonst zum Handelspreis.
        val produkte = Trading.produkte(acc)
        acc.transactions.filter { it.ts >= rangeStart && de.troetenrepublik.app.engine.Depotzerlegung.imDepot(it.type) }.forEach { t ->
            val key = t.posKey
            if (key.isNotBlank() && key !in held) {
                val derivat = t.key.isNotBlank() && t.key != t.symbol
                held[key] = Position(key, t.title, t.symbol.ifBlank { key }, 0.0, t.price, t.ts, if (derivat) produkte[key] else null)
            }
        }
        if (held.isEmpty()) return@coroutineScope null
        val ohneProdukt = held.values.filter { it.key != it.symbol && it.derivative == null }.mapTo(HashSet()) { it.key }

        val fingerprint = held.keys.joinToString("|") + "#" + acc.transactions.size + "#" + acc.positions.joinToString("|") { "${it.key}:${it.qty}" } + "#" + range.name
        partsCache[fingerprint]?.let { (ts, data) ->
            if (now - ts < CACHE_TTL_MS && data.raw.candles.isNotEmpty()) return@coroutineScope data
        }

        // Importierte Werte ohne Kurs (Trade Republic) zaehlen zum Handelspreis; Yahoo kennt sie nicht
        val symbols = held.values.map { it.symbol }.distinct().filterNot { it.startsWith(de.troetenrepublik.app.engine.TradeRepublicImport.OHNE_KURS) }
        val geladen: MutableMap<String, Series> = symbols
            .map { sym -> async { sym to market.series(sym, source) } }
            .awaitAll().toMap().toMutableMap()
        // Ein Abruf, der nichts lieferte (Netzfehler), wird einmal wiederholt, bevor der ganze Verlauf entfaellt
        for (sym in symbols.filter { geladen[it]?.candles.isNullOrEmpty() }) geladen[sym] = market.series(sym, source)
        val histories: Map<String, Series> = geladen
            .map { (sym, series) -> sym to (if (range == Range.MAX && first > 0) series.copy(candles = series.candles.filter { it.t >= first - 86_400_000L }) else series) }
            .filter { it.second.candles.size >= 2 }
            .toMap()
        // Werte mit nur einem Kurs (frische Emission) laufen zu ihrem Handelspreis mit, statt den Verlauf zu verwerfen
        val einKurs = geladen.filter { (sym, s) -> s.candles.size == 1 && sym !in histories }.keys
        held.values.filter { it.symbol in einKurs && it.derivative == null }.forEach { ohneProdukt += it.key }
        // Ohne Historie fuer einen heute gehaltenen Wert laesst sich nichts Verlaessliches zeigen
        if (acc.positions.any { it.symbol !in histories && it.key !in ohneProdukt && !it.isKnockedOut }) return@coroutineScope null
        // Euro-Reihen zum Wechselkurs des jeweiligen Tages: so passen Kurve, Einstand der Position und Rendite zusammen.
        // Hebelprodukte bleiben beim heutigen Kurs, ihr Basispreis wird ebenfalls zu dem gerechnet.
        val historisch: Map<String, Series> = histories.mapValues { (sym, s) -> market.mitDamaligemKurs(sym, s, source) }

        // Viele Punkte: gerechnet wird abseits des UI-Threads. Intraday zaehlt nur das Depotfenster (werktags 8 bis
        // 22 Uhr), der Tageschart wird auf fuenf Minuten ausgeduennt.
        val liveQuotes = quotes.value
        val rate = riskFree
        val fremdSplits = importSplits(acc)
        val raw = withContext(Dispatchers.Default) {
            val reihe = valueSeries(acc, held.values.toList(), histories, MAX_POINTS, liveQuotes, rate, from = if (range == Range.MAX) first else 0L,
                fenster = source.intraday, ohneProdukt = ohneProdukt, splits = fremdSplits, historisch = historisch)
            if (source == Range.D1) reihe.copy(candles = Ticks.ausduennen(reihe.candles, TAGES_RASTER_MS)) else reihe
        }
        // Geldfluesse aller Werte der Kurve; Knock-out und Verfall zahlen den Rest aus, sonst fiele der
        // Auszahlungsbetrag eines Optionsscheins am Verfallstag als Verlust aus der Kurve
        val flows = acc.transactions
            .filter { de.troetenrepublik.app.engine.Depotzerlegung.imDepot(it.type) && it.ts <= now }
            .filter { t -> held[t.posKey]?.let { it.symbol in histories || it.key in ohneProdukt } == true }
            .map { it.ts to de.troetenrepublik.app.engine.Depotzerlegung.fluss(it) }
        val result = if (raw.dayStart > 0L) PortfolioParts(raw, flows, source) else {
            // Ohne Depotfenster (nur Kurse ausserhalb, etwa Krypto am Wochenende): Tagesachse aus den Handelsfenstern,
            // die zu ihren Kursen passen; eine Reihe, fuer die Yahoo nachts schon den naechsten Tag meldet, zoege die
            // Achse sonst ueber zwei Tage und die Linie endete in der Mitte
            val fenster = histories.values.filter { it.fensterPasst }
            val dayStart = fenster.minOfOrNull { it.dayStart } ?: 0L
            val dayEnd = fenster.maxOfOrNull { it.dayEnd } ?: 0L
            PortfolioParts(raw.copy(dayStart = dayStart, dayEnd = dayEnd), flows, source)
        }
        if (result.raw.candles.size >= 2) partsCache[fingerprint] = now to result
        result
    }

    @Volatile private var rueckgangMerk: Pair<String, Double>? = null
    @Volatile private var rueckgangVersuch = 0L

    // Der zuletzt berechnete groesste Rueckgang fuer dieses Konto (Prozent, negativ); null, solange er nicht berechnet ist.
    fun rueckgangStand(acc: Account): Double? = rueckgangMerk?.takeIf { it.first == de.troetenrepublik.app.engine.Rueckgang.schluessel(acc) }?.second

    // Groesster Rueckgang des Depots fuer Analytics und Widget: der Wert aus Portfolio Performance im Zeitraum Max
    // (dieselbe Wertreihe, dieselbe Rechnung). Ohne Kurse bleibt es beim aufgezeichneten Verlauf.
    suspend fun maxRueckgang(): Double {
        val acc = account.value
        val schluessel = de.troetenrepublik.app.engine.Rueckgang.schluessel(acc)
        rueckgangMerk?.takeIf { it.first == schluessel }?.let { return it.second }
        val aufgezeichnet = withContext(Dispatchers.Default) { de.troetenrepublik.app.engine.Rueckgang.aufgezeichnet(acc) }
        val jetzt = System.currentTimeMillis()
        val werte = runCatching { de.troetenrepublik.app.ui.depotWertReihe(this, valuation.value) }.getOrDefault(emptyList())
        val kurve = withContext(Dispatchers.Default) { runCatching { de.troetenrepublik.app.engine.Rueckgang.ausKurve(werte, acc, jetzt) }.getOrNull() }
        // Ohne Kurve (kein Netz) nicht merken, damit der naechste Aufruf es noch einmal versucht
        if (kurve == null) return aufgezeichnet
        rueckgangMerk = schluessel to kurve
        return kurve
    }

    // Euro je Einheit der Waehrung eines Produkts aus den Wechselkursen. Fuer verkaufte Produkte ist der Kurs
    // ihres Basiswerts oft nicht geladen; ohne diesen Ersatz zaehlten Basispreise in Dollar oder Kronen als Euro.
    private fun euroJe(ccy: String): Double =
        de.troetenrepublik.app.engine.ExchangeRates.rate(ccy, market.fxKurse.value)?.let { 1.0 / it } ?: 1.0

    // Bewertet den Bestand entlang aller Zeitpunkte der beteiligten Reihen. Werte, deren Reihe spaeter beginnt
    // oder frueher endet, laufen mit ihrem ersten bzw. letzten Kurs mit, damit unterschiedliche Handelszeiten
    // die Kurve nicht zerreissen. Mit fenster zaehlen nur Zeitpunkte im Depotfenster (werktags 8 bis 22 Uhr,
    // MarketHours.DEPOT); die Tagesachse ist dann das Depotfenster des letzten Handelstags. Bleiben so keine
    // zwei Punkte, zaehlen alle Zeitpunkte.
    private fun valueSeries(
        account: Account,
        held: List<Position>,
        histories: Map<String, Series>,
        maxPoints: Int,
        liveQuotes: Map<String, Quote>,
        rate: Double,
        from: Long = 0L,
        to: Long = Long.MAX_VALUE,
        cashConst: Double = 0.0,
        fenster: Boolean = false,
        ohneProdukt: Set<String> = emptySet(),
        splits: Map<String, List<de.troetenrepublik.app.engine.Splits.Split>> = emptyMap(),
        // Reihen zum damaligen Wechselkurs fuer Werte ohne Hebel; fehlt ein Eintrag, gilt die Reihe aus histories.
        historisch: Map<String, Series> = emptyMap(),
    ): PortfolioSeries {
        // Derivate, deren Produkt die App nicht mehr kennt: Stueck zum letzten Handelspreis
        val handelspreise = if (ohneProdukt.isEmpty()) emptyMap() else de.troetenrepublik.app.engine.Depotzerlegung.handelspreise(account)
        val alle = histories.values.flatMap { series -> series.candles.map { it.t } }
            .filter { it in from..to }
            .distinct()
            .sorted()
        val imFenster = if (fenster) alle.filter { MarketHours.imDepotFenster(it) } else emptyList()
        val gefenstert = imFenster.size >= 2
        val stamps = if (gefenstert) imFenster else alle
        if (stamps.size < 2) return PortfolioSeries(emptyList())
        val (tagStart, tagEnde) = if (gefenstert) MarketHours.depotFenster(stamps.last()) else 0L to 0L
        val step = (stamps.size + maxPoints - 1) / maxPoints
        val grid = if (step <= 1) stamps else stamps.filterIndexed { i, _ -> i % step == 0 || i == stamps.lastIndex }

        val cursors = HashMap<String, Int>(histories.size)
        val out = ArrayList<Candle>(grid.size)
        var firstIndex = -1
        // Der Bestand aendert sich nur mit Buchungen: neu aufgebaut wird er nur, wenn seit dem letzten
        // Rasterpunkt eine Buchung liegt, statt fuer jeden der vielen Punkte.
        val bookings = account.transactions.map { it.ts }.sorted()
        var nextBooking = 0
        var holdings: Map<String, Position> = emptyMap()
        var stueckOhneProdukt: Map<String, Double> = emptyMap()
        for ((gi, t) in grid.withIndex()) {
            var booked = gi == 0
            while (nextBooking < bookings.size && bookings[nextBooking] <= t) { nextBooking++; booked = true }
            if (booked) {
                holdings = Trading.holdingsAt(account, t, splits)
                if (ohneProdukt.isNotEmpty()) stueckOhneProdukt = de.troetenrepublik.app.engine.Depotzerlegung.bestand(account, t, splits)
            }
            var value = cashConst
            var usable = true
            var any = false
            for (p in held) {
                if (p.key in ohneProdukt) {
                    val stueck = stueckOhneProdukt[p.key] ?: continue
                    val preis = de.troetenrepublik.app.engine.Depotzerlegung.preisBis(handelspreise[p.key], t) ?: continue
                    value += stueck * preis
                    any = true
                    continue
                }
                val q = holdings[p.key] ?: continue
                val series = (if (p.derivative == null) historisch[p.symbol] else null)?.candles ?: histories[p.symbol]?.candles
                if (series == null) { continue }
                var i = cursors[p.symbol] ?: 0
                while (i + 1 < series.size && series[i + 1].t <= t) i++
                cursors[p.symbol] = i
                val spot = series[i].close
                if (spot <= 0.0) { usable = false; break }
                val d = p.derivative
                val unit = when {
                    d == null -> spot
                    d.isKnockedOut && t >= d.knockedOutAt -> Pricing.KO_RESIDUAL
                    else -> Pricing.price(d.copy(knockedOutAt = 0L), spot, t, liveQuotes[p.symbol]?.fx ?: euroJe(d.ccy), rate)
                }
                value += q.qty * unit
                any = true
            }
            if (!usable || !value.isFinite()) continue
            if (!any && cashConst == 0.0) continue
            if (firstIndex < 0) firstIndex = gi
            out += Candle(t, value, value, value, value)
        }
        val start = if (firstIndex <= 0 || grid.size < 2) 0f else firstIndex.toFloat() / (grid.size - 1).toFloat()
        return PortfolioSeries(out, start, tagStart, tagEnde)
    }

    // ------------------------------------------------------------ Depotverlauf: woher Gewinne und Verluste kommen

    // Ergebnis je Tag und Position seit dem ersten Kauf. namen und symbole je Depotschluessel; geschaetzt: Derivate,
    // deren Produkt die App nicht mehr kennt, oder Werte ohne Kurse. Sie stehen bis zum Verkauf mit ihrem
    // Handelspreis im Depot, ihr Ergebnis faellt am Verkaufstag an.
    data class Zerlegung(
        val tage: List<de.troetenrepublik.app.engine.Depotzerlegung.Tag>,
        val namen: Map<String, String>,
        val symbole: Map<String, String>,
        val derivate: Set<String>,
        val geschaetzt: Set<String>,
        val imDepot: Set<String>,
    )

    private var zerlegungCache: Pair<String, Pair<Long, Zerlegung>>? = null

    suspend fun depotZerlegung(): Zerlegung? = coroutineScope {
        val acc = account.value
        val first = Trading.firstPurchase(acc).takeIf { it > 0 } ?: return@coroutineScope null
        val now = System.currentTimeMillis()
        val fingerprint = "${acc.transactions.size}#${acc.transactions.lastOrNull()?.id}#" + acc.positions.joinToString("|") { "${it.key}:${it.qty}" }
        zerlegungCache?.takeIf { it.first == fingerprint && now - it.second.first < 5 * 60_000L }?.let { return@coroutineScope it.second.second }

        // Alle Werte, die je im Depot lagen: heutige Positionen und alle gehandelten Schluessel
        val produkte = Trading.produkte(acc)
        val info = LinkedHashMap<String, Position>()
        acc.positions.forEach { info[it.key] = it }
        for (t in acc.transactions) {
            if (t.type != TxType.BUY && t.type != TxType.SELL && t.type != TxType.KNOCKOUT && t.type != TxType.EXPIRY) continue
            val k = t.posKey
            if (k.isBlank() || k in info) continue
            val derivat = t.key.isNotBlank() && t.key != t.symbol
            info[k] = Position(k, t.title, t.symbol.ifBlank { k }, 0.0, t.price, t.ts, if (derivat) produkte[k] else null)
        }
        val ohneProdukt = info.values.filter { it.key != it.symbol && it.derivative == null }.map { it.key }.toSet()
        val gleichzeitig = kotlinx.coroutines.sync.Semaphore(6)
        val kurse = info.values.filter { it.key !in ohneProdukt }.map { it.symbol }.distinct()
            .map { s -> async { gleichzeitig.acquire(); try { s to dailyCloses(s) } finally { gleichzeitig.release() } } }
            .awaitAll().filter { it.second.isNotEmpty() }.toMap()
        val geschaetzt = ohneProdukt + info.values.filter { it.key !in ohneProdukt && it.symbol !in kurse }.map { it.key }
        // Euro-Reihen zum Wechselkurs des jeweiligen Tages (nur fuer Werte ohne Hebel), wie in der Depotkurve
        val kurseDamals = kurse.mapValues { (sym, k) -> market.mitDamaligemKurs(sym, k) }
        val fremdSplits = importSplits(acc)
        val liveQuotes = quotes.value
        val rate = riskFree

        val ergebnis = withContext(Dispatchers.Default) {
            val zufluesse = de.troetenrepublik.app.engine.Depotzerlegung.zufluesse(acc)
            val preise = de.troetenrepublik.app.engine.Depotzerlegung.handelspreise(acc)
            val buchungen = acc.transactions.map { it.ts }.sorted()
            var naechste = 0
            var bestand: Map<String, Double> = emptyMap()
            var bestandTag = Long.MIN_VALUE
            val heuteTag = de.troetenrepublik.app.engine.Depotzerlegung.tagBeginn(now)
            val tage = de.troetenrepublik.app.engine.Depotzerlegung.tage(first, now, info.keys, zufluesse) { k, tag ->
                val ende = tag + de.troetenrepublik.app.engine.Depotzerlegung.TAG - 1
                if (tag != bestandTag) {
                    // Den Bestand nur neu aufbauen, wenn seit dem letzten Tag gebucht wurde
                    var gebucht = bestandTag == Long.MIN_VALUE
                    while (naechste < buchungen.size && buchungen[naechste] <= ende) { naechste++; gebucht = true }
                    if (gebucht) bestand = de.troetenrepublik.app.engine.Depotzerlegung.bestand(acc, ende, fremdSplits)
                    bestandTag = tag
                }
                val stueck = bestand[k] ?: return@tage 0.0
                val p = info[k] ?: return@tage 0.0
                val handel = { de.troetenrepublik.app.engine.Depotzerlegung.preisBis(preise[k], ende)?.let { stueck * it } }
                if (k in geschaetzt) return@tage handel()
                val d = p.derivative
                // Heute zaehlt der Live-Kurs wie in der Depotbewertung (0.77), sonst passt „Je Wertpapier“ nicht zur Kapitalbilanz
                if (tag == heuteTag) {
                    val live = Trading.priceOf(k, p.symbol, d, liveQuotes, now, rate)
                    if (live > 0 && live.isFinite()) return@tage stueck * live
                }
                val spot = priceAt((if (d == null) kurseDamals else kurse)[p.symbol].orEmpty(), ende)?.close?.takeIf { it > 0 } ?: return@tage handel()
                val einheit = when {
                    d == null -> spot
                    d.isKnockedOut && ende >= d.knockedOutAt -> Pricing.KO_RESIDUAL
                    else -> Pricing.price(d.copy(knockedOutAt = 0L), spot, ende, liveQuotes[p.symbol]?.fx ?: euroJe(d.ccy), rate)
                }
                (stueck * einheit).takeIf { it.isFinite() }
            }
            Zerlegung(
                tage = tage,
                namen = info.mapValues { it.value.name },
                symbole = info.mapValues { it.value.symbol },
                derivate = info.values.filter { it.key != it.symbol }.map { it.key }.toSet(),
                geschaetzt = geschaetzt,
                imDepot = acc.positions.map { it.key }.toSet(),
            )
        }
        zerlegungCache = fingerprint to (now to ergebnis)
        ergebnis
    }

    // ------------------------------------------------------------ Position importieren

    // Tagesschlusskurs am oder vor ts; null, wenn die Historie erst danach beginnt.
    fun priceAt(daily: List<Candle>, ts: Long): Candle? {
        if (daily.isEmpty()) return null
        var lo = 0; var hi = daily.size - 1; var best = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (daily[mid].t <= ts) { best = mid; lo = mid + 1 } else hi = mid - 1
        }
        return if (best >= 0) daily[best] else null
    }

    // Schlusskurs in EUR an einem Kalendertag (oder am letzten Handelstag davor); null ohne Historie.
    suspend fun closeOn(symbol: String, day: Long): Candle? {
        val daily = runCatching { market.daily(symbol) }.getOrDefault(emptyList())
        if (daily.isEmpty() || day < daily.first().t - 4 * 86_400_000L) return null
        return priceAt(daily, day + 86_400_000L - 1)
    }

    // Wertpapier mit Stueckzahl, Kaufkurs und Kaufdatum ins Depot uebernehmen. Kein Geldfluss;
    // Verlauf und Ergebnis rechnen ab dem Kaufdatum.
    fun importPosition(symbol: String, name: String, qty: Double, price: Double, purchasedAt: Long): Boolean {
        var ok = false
        var msg: String? = null
        store.update { acc ->
            val r = Trading.importPosition(acc, symbol, name, qty, price, purchasedAt)
            ok = r.ok
            msg = errorText(r.error, r.extra) ?: S.importDone(Fmt.qty(qty), name)
            r.account
        }
        if (ok) partsCache.clear()
        _toast.value = msg
        return ok
    }

    // ------------------------------------------------------------ Bereinigung beim Start

    // Bekannte Tresornamen loeschen und ein Depotabbild in den Einstellungen (broker) leeren: zurueck zum frischen
    // Uebungsdepot, Watchlist und Alarme bleiben. Die Tresornamen loescht die App auch ohne Eintrag in den
    // Einstellungen, damit Reste verschwinden, wenn broker schon geleert ist.
    private suspend fun tresorBereinigen() {
        if (store.gesperrt.value) return
        if (Tresor.verfuegbar) de.troetenrepublik.app.engine.TresorBereinigung.alleTresorNamen().forEach { runCatching { Tresor.schreiben(it, null) } }
        val s = settings.flow.first()
        if (s.broker.isBlank()) return
        val alt = account.value
        store.replace(Trading.fresh().copy(ersteListe = alt.ersteListe, ersteListeName = alt.ersteListeName, weitereListen = alt.weitereListen, watchMeta = alt.watchMeta, alerts = alt.alerts))
        settings.update { it.copy(broker = "") }
        syncTracked()
    }

    // [Sicherheit 0.71] Einmalig beim Start: API-Schluessel, App-Passwort und Steuer-ID aus der Einstellungsdatei in
    // den Tresor uebernehmen; das Schreiben entfernt den Klartext. Ohne verfuegbaren Tresor bleibt alles wie bisher.
    private suspend fun schluesselUebernehmen() {
        if (!Tresor.verfuegbar || store.gesperrt.value) return
        settings.update { it }
    }

    // Ohne eigene Kontaktadresse fuer die SEC einmalig eine Zufallsadresse eintragen (SecInsider.zufallsKontakt).
    private suspend fun secKontaktVorbelegen() {
        if (settingsFlow.value.secKontaktErzeugt) return
        settings.update {
            if (it.secKontaktErzeugt) it
            else if (it.secKontakt.isBlank()) it.copy(secKontakt = de.troetenrepublik.app.market.SecInsider.zufallsKontakt(), secKontaktErzeugt = true)
            else it.copy(secKontaktErzeugt = true)
        }
    }

    private companion object {
        // Buchungen der Glocke je Wert (Alarmarten seit 0.77)
        val NEUE_ALARM_TAGS = setOf(
            "ALERT_NEWS", "ALERT_INSIDER_SELL", "ALERT_HIGH52", "ALERT_LOW52", "ALERT_ANALYST_UP", "ALERT_ANALYST_DOWN", "ALERT_EARNINGS", "ALERT_8K",
            de.troetenrepublik.app.engine.RegelGlocke.TAG_KAUF, de.troetenrepublik.app.engine.RegelGlocke.TAG_VERKAUF,
        )

        // Kurshistorien aendern sich traege; der Live-Punkt wird ohnehin separat angehaengt.
        const val CACHE_TTL_MS = 60_000L

        // Schluessel des Depotverlaufs unter den Tagesverlaeufen der Widgets.
        const val WIDGET_DEPOT = "@depot"
        const val WIDGET_DEPOT_JAHR = "@depot-jahr"
        const val WIDGET_DEPOT_KAUF = "@depot-kauf"

        // So lange darf die App im Hintergrund sein, bevor die App-Sperre wieder greift.
        const val SPERRE_NACH_MS = 60_000L

        // Obergrenze der Punkte einer Depotkurve. Beim Ziehen mit dem Finger ist jeder Punkt eine Station;
        // Wochen- und Monatschart behalten so jeden Kurs der Quelle.
        const val MAX_POINTS = 1500

        // Raster des Tagescharts der Depotkurve: ein Punkt je fuenf Minuten.
        const val TAGES_RASTER_MS = 5 * 60_000L

        // Stammdaten, die vor diesem Zeitpunkt geladen wurden, kennen die Adresse ihres Wikipedia-Artikels nicht.
        val ARTIKEL_SEIT: Long = java.time.LocalDate.of(2026, 10, 7).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        // Wer vorher als "ohne Wikipedia-Eintrag" gespeichert wurde, wird einmal neu gesucht.
        val WIKI_SEIT: Long = java.time.LocalDate.of(2026, 11, 13).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    }
}

// Quellen der Heatmap ausser den Index-Schluesseln des Katalogs.
object HeatmapQuelle {
    // Der ganze Aktienmarkt: die groessten Aktien des Katalogs weltweit, Standard der Heatmap.
    const val MARKT = "MARKT"
    const val DEPOT = "DEPOT"
    const val WATCHLIST = "WATCHLIST"
}
