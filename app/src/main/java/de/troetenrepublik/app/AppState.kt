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
import de.troetenrepublik.app.engine.Err
import de.troetenrepublik.app.engine.Derivatives
import de.troetenrepublik.app.engine.MarketHours
import de.troetenrepublik.app.engine.Pricing
import de.troetenrepublik.app.engine.Ticks
import de.troetenrepublik.app.engine.Trading
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.broker.Aenderbar
import de.troetenrepublik.app.market.broker.Auftrag
import de.troetenrepublik.app.market.broker.Broker
import de.troetenrepublik.app.market.broker.BrokerAbbild
import de.troetenrepublik.app.market.broker.BrokerArt
import de.troetenrepublik.app.market.broker.BrokerFehler
import de.troetenrepublik.app.market.broker.Netzschutz
import de.troetenrepublik.app.market.broker.TanChallenge
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.flow.catch
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

    // Abrufkontingente der Dienste mit eigenem Schluessel.
    val kontingent: de.troetenrepublik.app.market.Abrufkontingent get() = news.kontingent

    // Reddit-API mit eigenem Schluessel: Beitraege zu einem Wert und Erwaehnungen in deutschen Subreddits.
    val reddit: de.troetenrepublik.app.market.RedditApi by lazy { de.troetenrepublik.app.market.RedditApi(MarketApi(), news.kontingent) }

    // Behoerdendaten zu US-Firmen: Lobbying, Auftraege, Leerverkaeufe, Spenden, Entlassungen, Patente.
    val behoerden: de.troetenrepublik.app.market.BehoerdenRepository by lazy { de.troetenrepublik.app.market.BehoerdenRepository(MarketApi(), news.kontingent) }

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
    private fun widgetEinzelStandard(): String =
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
    @Volatile private var widgetNews: List<Triple<String, String, de.troetenrepublik.app.market.MarketApi.NewsItem>>? = null
    @Volatile private var widgetNewsAm = 0L
    @Volatile private var widgetNewsBestand = ""
    private val widgetLadeLock = kotlinx.coroutines.sync.Mutex()

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
                    runCatching { portfolioZeitraum(heute.withDayOfYear(1).toEpochDay(), heute.toEpochDay()).candles.map { it.close } }.getOrNull()
                        ?.let { k[WIDGET_DEPOT_JAHR] = de.troetenrepublik.app.data.WidgetZiel.ausduennen(it) }
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
        }
        if (de.troetenrepublik.app.plattform.Widgets.ART_NEWS in arten) {
            // Schlagzeilen der sechs groessten Positionen ohne Derivate, hoechstens alle 30 Minuten oder bei neuem Bestand
            val positionen = account.value.positions.filter { !it.isDerivative && !it.isKnockedOut }.sortedByDescending { it.invested }.take(6)
            val sprache = settingsFlow.value.sprache.code
            val schluessel = positionen.joinToString("|") { it.symbol } + "|" + sprache
            if (schluessel != widgetNewsBestand || jetzt - widgetNewsAm > 30 * 60_000L || widgetNews == null) {
                val alle = coroutineScope {
                    positionen.map { p ->
                        async {
                            runCatching { market.news(p.symbol, sprache) }.getOrDefault(emptyList()).map { Triple(p.symbol, kurzName(p.symbol, p.name), it) }
                        }
                    }.awaitAll().flatten()
                }
                widgetNews = alle.distinctBy { it.third.title.trim().lowercase() }.sortedByDescending { it.third.published }.take(20)
                widgetNewsAm = jetzt
                widgetNewsBestand = schluessel
            }
        }
        if (de.troetenrepublik.app.plattform.Widgets.ART_DIVIDENDEN !in arten) return@withLock
        val bestaende = widgetDividendenBestaende()
        val schluessel = bestaende.joinToString("|") { "${it.key}:${it.qty}" }
        if (schluessel != widgetDividendenBestand || jetzt - widgetDividendenAm > 6 * 3_600_000L) {
            val quellen = coroutineScope {
                bestaende.map { p ->
                    async {
                        de.troetenrepublik.app.engine.PortfolioTools.DividendSource(
                            p.key, kurzName(p.symbol, p.name), p.qty, dividendsFor(p.symbol).map { it.exDate to it.amount },
                        )
                    }
                }.awaitAll()
            }
            widgetDividenden = de.troetenrepublik.app.engine.PortfolioTools.expectedDividends(quellen, jetzt)
            widgetDividendenAm = jetzt
            widgetDividendenBestand = schluessel
        }
    }

    // Inhalt der Widgets, fertig formatiert; mit App-Sperre ohne Betraege; null, solange Kurse fehlen (sonst stuende 0 da).
    fun widgetDaten(): de.troetenrepublik.app.data.WidgetDaten? {
        val titel = de.troetenrepublik.app.data.WidgetDaten(
            titelDepot = S.widgetDepot, titelWerte = S.widgetWerte, titelBestand = S.widgetBestand, titelMover = S.widgetMover,
            titelDividenden = S.widgetDividenden, schnell = listOf(S.widgetSuche, S.widgetOrders, S.widgetHeatmap, S.widgetNews),
            titelMaerkte = S.widgetMaerkte,
        )
        if (store.sperre.aktiv || store.gesperrt.value) return titel.copy(hinweis = S.widgetGesperrt, gesperrt = true)
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
            if (p.position.isDerivative) p.position.name else kurzName(p.position.symbol, p.position.name), rechts,
            Fmt.signedPct(p.dayChangePct), p.dayChangePct >= 0,
            if (p.position.isDerivative) de.troetenrepublik.app.data.WidgetZiel.derivat(p.key) else de.troetenrepublik.app.data.WidgetZiel.instrument(p.position.symbol),
            betrag = if (p.prevPrice > 0) Fmt.signedMoney(p.price - p.prevPrice) else "", pct = p.dayChangePct,
        )

        val werte = widgetSymbole().mapNotNull { wert(it) }
        val aktiv = v.positions.filter { !it.position.isKnockedOut && it.value > 0 }
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
        val erwartet = widgetDividenden
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
            divErwartet = erwartet?.let { l -> betrag(l.sumOf { it.amount }) } ?: "–",
            divNaechste = erwartet.orEmpty().take(4).map { e ->
                de.troetenrepublik.app.data.WidgetWert("${Fmt.dayMonth(e.date)} · ${e.name}", betrag(e.amount), "", true)
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
        )
    }

    // Depot im Widget je Zeitraum: heute (Tagesaenderung), seit Jahresbeginn (Depotkurve wie in der App, ohne Geldfluesse)
    // und seit Kauf (Wert der offenen Positionen gegen ihren Einstand). Ohne Verlauf fehlt der Jahreswert.
    private fun widgetZeitraeume(
        aktiv: List<de.troetenrepublik.app.data.ValuedPosition>, tag: Double, tagPct: Double, verdeckt: Boolean, mitKurve: Boolean,
    ): Map<String, de.troetenrepublik.app.data.WidgetZeitraum> {
        val kurven = widgetKurven
        fun betrag(v: Double) = if (verdeckt) "" else Fmt.signedMoney(v)
        val m = HashMap<String, de.troetenrepublik.app.data.WidgetZeitraum>()
        m[de.troetenrepublik.app.data.WidgetDaten.ZEIT_HEUTE] = de.troetenrepublik.app.data.WidgetZeitraum(
            S.widgetZeitHeute, betrag(tag), Fmt.signedPct(tagPct), tag >= 0, if (mitKurve) kurven[WIDGET_DEPOT].orEmpty() else emptyList(),
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

    // Stimmungsbarometer fuer das Widget: Aktien (CNN) und Krypto (alternative.me).
    private fun widgetStimmungen(): Map<String, de.troetenrepublik.app.data.WidgetStimmung> {
        val s = widgetStimmung ?: return emptyMap()
        return listOfNotNull(
            s.aktien?.let { de.troetenrepublik.app.data.WidgetDaten.STIMMUNG_AKTIEN to it },
            s.krypto?.let { de.troetenrepublik.app.data.WidgetDaten.STIMMUNG_KRYPTO to it },
        ).associate { (schluessel, b) ->
            schluessel to de.troetenrepublik.app.data.WidgetStimmung(S.sbKachel(schluessel), kotlin.math.round(b.wert).toInt().coerceIn(0, 100), S.sbStufe(b.stufe.ordinal), b.stufe.ordinal)
        }
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
        if (brokerErwartet && broker == null) scope.launch { runCatching { brokerFortsetzen() } }
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
            // Die alte PIN-Einstellung wird nicht mehr abgefragt und hier geloescht.
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

    // Anzeige auf die naechste Waehrung schalten: gewaehlte, US-Dollar, Euro, wieder die gewaehlte (Fmt.stufen).
    // Ohne Dollarkurs wird er zuerst geladen; gelingt das nicht, wird der Dollar uebersprungen.
    fun waehrungUmschalten() {
        scope.launch {
            val stufen = Fmt.stufen
            var n = (Fmt.stufe + 1) % stufen.size
            if (stufen[n] == "USD" && Fmt.usdJeEuro <= 0) {
                val k = runCatching { market.dollarKurs() }.getOrNull()
                if (k != null) Fmt.usdJeEuro = k else { _toast.value = S.usdUnavailable; n = (n + 1) % stufen.size }
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

    fun showToast(msg: String) { _toast.value = msg }

    private val started = AtomicBoolean(false)
    @Volatile private var foreground = false

    fun start() {
        if (!started.compareAndSet(false, true)) return
        syncTracked()
        scope.launch {
            while (isActive) {
                if (foreground) runCatching { tick() }
                delay(5_000)
            }
        }
        scope.launch { store.account.collect { syncTracked() } }
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
        scope.launch { runCatching { brokerFortsetzen() } }
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
                if (foreground) runCatching { newsLaden(); newsMelden() }
                // Tagesrueckblick am Abend
                if (foreground) runCatching { rueckblickPruefen() }
                delay(5 * 60_000L)
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
        // Echtes Konto eingerichtet, aber nicht verbunden (etwa weil das Geraet beim Start gesperrt war): jetzt verbinden
        if (brokerErwartet && broker == null) scope.launch { runCatching { brokerFortsetzen() } }
        market.startPolling(settingsFlow.value.refreshSeconds * 1000L)
        // Beim Start ist die App noch nicht im Vordergrund, wenn die Tagesschleife anlaeuft
        scope.launch { runCatching { refreshEcb() } }
    }

    fun pause() {
        foreground = false
        widgetsSchieben()
        imHintergrundSeit = System.currentTimeMillis()
        market.stopPolling()
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
        if (broker != null) { brokerTick(); return }
        // Echtes Konto eingerichtet, aber nicht verbunden: keine Buchungen des Uebungsdepots auf seinem Abbild
        if (brokerErwartet) return
        val q = quotes.value
        if (q.isEmpty()) return
        val now = System.currentTimeMillis()
        val s = settingsFlow.value
        val today = now / 86_400_000L
        val sp = Trading.Spread(s.spreadStockBps.toDouble(), s.spreadDerivativeBps.toDouble())
        val rate = riskFree
        val dividends = collectDividends(now)

        val before = account.value
        store.update { acc ->
            val lastDay = acc.lastMaintainDay.takeIf { it > 0 } ?: s.lastMaintainDay
            val lastInterest = acc.lastInterestDay.takeIf { it > 0 } ?: s.lastInterestDay
            var a = Trading.maintain(acc, q, now, lastDay in 1 until today, allowance = s.allowance, rate = rate)
            a = Trading.runOrders(a, q, now, sp, allowance = s.allowance, rate = rate)
            a = Trading.runPlans(a, q, now, sp)
            if (lastInterest in 1 until today) {
                a = Trading.accrueInterest(a, s.interestPct, (today - lastInterest).toInt())
                // Gutschrift beim ersten Lauf in einem neuen Monat
                if (monthKey(lastInterest * 86_400_000L) < monthKey(now)) {
                    a = Trading.creditInterest(a, s.interestPct, now, s.allowance)
                }
            }
            if (dividends.isNotEmpty()) a = Trading.bookDividends(a, dividends, now, s.allowance)
            val (withAlerts, _) = Trading.checkAlerts(a, q, now)
            a = Trading.backfillWatchMeta(withAlerts, q, now)
            // Marker und Guthaben zusammen speichern, damit nach einem Neustart keine Zinsen doppelt gebucht werden.
            a = a.copy(lastMaintainDay = today, lastInterestDay = today)
            val hasAllQuotes = a.positions.all { p -> p.isKnockedOut || Trading.isQuoteFresh(q[p.symbol], now) }
            if (hasAllQuotes) a = Trading.snapshot(a, Trading.value(a, q, now, rate).total, now)
            a
        }
        merkeVerschwundene(before, account.value)
        announce(before, account.value)
    }

    // Produkte, die das Depot verlassen haben (Verkauf, Rueckzahlung), bleiben der Detailseite bekannt;
    // sonst faende sie das Produkt nicht mehr.
    private fun merkeVerschwundene(before: Account, after: Account) {
        if (before === after) return
        val bleibt = after.positions.mapTo(HashSet()) { it.key }
        before.positions.forEach { p -> if (p.key !in bleibt) p.derivative?.let { derivativeRegistry[it.id] = it } }
    }

    // Dividendentermine der gehaltenen Werte, hoechstens alle zwoelf Stunden je Wert abgefragt.
    private suspend fun collectDividends(now: Long): List<Trading.Dividend> {
        val symbols = account.value.positions.filter { it.derivative == null }.map { it.symbol }.distinct()
        if (symbols.isEmpty()) return emptyList()
        val stale = symbols.filter { (dividendCache[it]?.first ?: 0L) < now - 12 * 3_600_000L }.take(3)
        for (sym in stale) {
            val list = runCatching { market.dividends(sym) }.getOrDefault(emptyList())
            dividendCache[sym] = now to list
        }
        return symbols.flatMap { sym -> dividendCache[sym]?.second.orEmpty().map { Trading.Dividend(sym, it.exDate, it.amount) } }
    }

    // Was sich zwischen zwei Kontostaenden getan hat, als Systemmitteilung.
    private fun announce(before: Account, after: Account) {
        val n = notifier ?: return
        if (before === after) return
        val beforeTx = before.transactions.map { it.id }.toHashSet()
        val fresh = after.transactions.filter { it.id !in beforeTx }
        for (t in fresh) {
            when (t.type) {
                TxType.DIVIDEND -> n.notify(Notifier.CHANNEL_CASH, S.notifDividend, S.notifDividendText(t.title, Fmt.euro(t.amount)))
                TxType.INTEREST -> n.notify(Notifier.CHANNEL_CASH, S.notifInterest, S.notifInterestText(Fmt.euro(t.amount)))
                TxType.KNOCKOUT -> n.notify(Notifier.CHANNEL_TRADES, S.notifKnockoutSettled, S.notifKnockoutSettledText(t.title, Fmt.euro(t.amount)))
                TxType.EXPIRY -> n.notify(Notifier.CHANNEL_TRADES, S.notifExpiry, S.notifExpiryText(t.title, Fmt.euro(t.amount)))
                TxType.ALERT -> n.notify(Notifier.CHANNEL_ALERTS, S.notifAlert(t.title),
                    if (t.tag == "ALERT_MOVE") S.notifAlertMoveText(Fmt.signedPct(t.extra), Fmt.priceEuro(t.price))
                    else S.notifAlertText(Fmt.priceEuro(t.price), Fmt.priceEuro(t.extra)))
                TxType.PLAN -> n.notify(Notifier.CHANNEL_TRADES, S.notifPlanFailed, S.notifPlanFailedText(t.title))
                TxType.BUY -> if (t.via == "PLAN") n.notify(Notifier.CHANNEL_TRADES, S.notifPlanDone, S.notifPlanDoneText(t.title, Fmt.euro(-t.amount)))
                TxType.BET -> if (t.tag == "BET_WIN" || t.tag == "BET_LOSS" || t.tag == "BET_SETTLE") {
                    n.notify(Notifier.CHANNEL_TRADES, S.pmNotifTitle(t.tag), S.pmNotifText(t.title, Fmt.euro(t.amount)))
                }
                else -> {}
            }
        }
        // Frisch ausgeknockte Produkte
        val koBefore = before.positions.filter { it.isKnockedOut }.map { it.key }.toSet()
        after.positions.filter { it.isKnockedOut && it.key !in koBefore }.forEach { p ->
            n.notify(Notifier.CHANNEL_TRADES, S.notifKnockout, S.notifKnockoutText(p.name))
        }
        val filledBefore = before.orders.filter { it.status == OrderStatus.FILLED }.map { it.id }.toSet()
        after.orders.filter { it.status == OrderStatus.FILLED && it.id !in filledBefore }.forEach { o ->
            n.notify(Notifier.CHANNEL_TRADES, S.notifOrder, S.notifOrderText(if (o.side == Side.BUY) S.buy else S.sell, Fmt.qty(o.qty), o.name, Fmt.priceEuro(o.executedPrice)))
        }
    }

    private fun monthKey(ts: Long): Int =
        java.time.Instant.ofEpochMilli(ts).atZone(MarketHours.zone).let { it.year * 12 + it.monthValue }

    // ------------------------------------------------------------ Copytrading

    // Bekannte Investoren mit ihren 13F-Meldungen (mitgeliefert).
    val investoren: List<Investoren.Investor> by lazy {
        runCatching { Investoren.lesen(de.troetenrepublik.app.plattform.Plattform.asset("copytrading/investoren.json").decodeToString()) }.getOrDefault(emptyList())
    }

    val senat: de.troetenrepublik.app.market.Politik.SenatDatei? by lazy {
        runCatching { de.troetenrepublik.app.market.Politik.senat(de.troetenrepublik.app.plattform.Plattform.asset("copytrading/senat.json").decodeToString()) }.getOrNull()
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
        if (!erzwingen && jetzt - (regierungZeit[id] ?: 0L) < 24 * 3_600_000L) return _regierungNeu.value[id]
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

    suspend fun alleMeldungenLesen(fortschritt: (Int, Int) -> Unit) {
        val r = copy ?: return
        runCatching { r.verzeichnis() }
        val jetzt = System.currentTimeMillis()
        val offen = r.stand.value.meldungen.filter { it.elektronisch && it.datum >= jetzt - 365L * 86_400_000L && r.gemerkt(it) == null }
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

    suspend fun kongressDaten(fortschritt: (Float) -> Unit): KongressDaten? = kongressLock.withLock {
        kongressFertig()?.let { return@withLock it }
        val KA = de.troetenrepublik.app.engine.KongressAuswertung
        val handel = ArrayList<de.troetenrepublik.app.engine.KongressAuswertung.Handel>()
        fun dazu(person: String, name: String, gruppe: Char, gemeldet: Long, g: de.troetenrepublik.app.market.Kongress.Geschaeft) {
            val k = g.kuerzel ?: return
            if (g.typ != "ST" || !(g.kauf || g.verkauf) || g.mitte <= 0) return
            handel += de.troetenrepublik.app.engine.KongressAuswertung.Handel(person, name, gruppe, de.troetenrepublik.app.ui.CopyRouten.symbol(k), g.kauf, g.datum, g.gemeldet.takeIf { it > 0 } ?: gemeldet, g.mitte)
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
        fortschritt(0.1f)
        val symbole = handel.filter { it.kauf }.map { it.symbol }.distinct()
        val teile = (symbole + "^GSPC").chunked(20)
        var fertig = 0
        val gleichzeitig = kotlinx.coroutines.sync.Semaphore(4)
        // Wochenkurse ueber fuenf Jahre, gleich nach jedem Abruf in Felder umgesetzt
        val kurse = java.util.concurrent.ConcurrentHashMap<String, de.troetenrepublik.app.engine.KongressAuswertung.Kurse>()
        kotlinx.coroutines.coroutineScope {
            teile.map { t ->
                async {
                    gleichzeitig.withPermit {
                        schlusskurse(t, "5y", "1wk").forEach { (s, l) ->
                            val f = l.filter { it.close > 0 }
                            if (f.isNotEmpty()) kurse[s] = de.troetenrepublik.app.engine.KongressAuswertung.Kurse(
                                IntArray(f.size) { Math.floorDiv(f[it].t, KA.TAG).toInt() }, FloatArray(f.size) { f[it].close.toFloat() },
                            )
                        }
                        synchronized(this@AppState) { fertig++ }
                        fortschritt(0.1f + 0.9f * fertig / teile.size)
                    }
                }
            }.awaitAll()
        }
        val d = KongressDaten(handel, kurse.filterKeys { it != "^GSPC" }, kurse["^GSPC"], System.currentTimeMillis())
        kongressGemerkt = d
        d
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
        key.startsWith("i:") -> investoren.firstOrNull { it.id == key.removePrefix("i:") }?.let { inv -> _secNeuer.value[inv.id]?.first ?: inv.aktuell?.stichtag }
        key.startsWith("n:") -> senatNeueste(key.removePrefix("n:"))?.id
        else -> null
    }

    // Juengste mitgelieferte Senatsmeldung einer Person.
    private fun senatNeueste(person: String) =
        senat?.meldungen?.filter { it.person == person }?.maxByOrNull { de.troetenrepublik.app.market.Politik.tag(it.eingang) }

    // Personen mit Glocke: Mitteilungen bei neuen Meldungen.
    fun glocken(s: Settings = settingsFlow.value): Set<String> = s.copyGlocke.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    // Glocke fuer eine Person an- oder ausschalten; ab jetzt zaehlen nur neuere Meldungen.
    fun glocke(key: String, an: Boolean) {
        scope.launch {
            settings.update { s ->
                val g = glocken(s).toMutableSet()
                val ges = gesehen(s).toMutableMap()
                if (an) { g += key; aktuellerStand(key)?.let { ges[key] = it } } else { g -= key; if (key !in gefolgt(s)) ges -= key }
                s.copy(copyGlocke = g.sorted().joinToString("\n"), copyGesehen = gesehenText(ges), copyGlockeUebernommen = true)
            }
        }
    }

    // Einmalige Uebernahme: Alle Gefolgten (Repraesentantenhaus, Investoren) bekommen die Glocke.
    private suspend fun glockenUebernehmen() {
        settings.update { s ->
            if (s.copyGlockeUebernommen) s
            else s.copy(
                copyGlocke = (glocken(s) + gefolgt(s).filter { it.startsWith("k:") || it.startsWith("i:") }).sorted().joinToString("\n"),
                copyGlockeUebernommen = true,
            )
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
        if (brokerGesperrt()) return false
        val s = m.seiten.getOrNull(seite) ?: return false
        // frischen Kurs holen, damit nicht zu einem veralteten Brief gekauft wird
        val frisch = runCatching { prognose.markt(m.quelle, m.id) }.getOrNull()?.takeIf { it.offen }
        val kurs = frisch?.seiten?.getOrNull(seite)?.kauf ?: s.kauf
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

    // Alle Anteile einer offenen Wette zum aktuellen Geldkurs verkaufen; ist der Markt schon aufgeloest, wird ausgezahlt.
    suspend fun wetteVerkaufen(id: String): Boolean {
        val w = store.account.value.wetten.firstOrNull { it.id == id && it.offen } ?: return false
        val quelle = runCatching { de.troetenrepublik.app.market.Prognose.Quelle.valueOf(w.quelle) }.getOrNull() ?: return false
        val m = runCatching { prognose.markt(quelle, w.markt) }.getOrNull()
        val jetzt = System.currentTimeMillis()
        val auszahlung = m?.auszahlung
        var erg: de.troetenrepublik.app.engine.Wetten.Ergebnis? = null
        if (auszahlung != null) {
            val je = if (w.seite == 0) auszahlung else 1.0 - auszahlung
            store.update { acc -> de.troetenrepublik.app.engine.Wetten.aufloesen(acc, id, je, jetzt, Trading.newId("tx", jetzt)).also { erg = it }.account }
        } else {
            val kurs = m?.seiten?.getOrNull(w.seite)?.verkauf ?: (w.kurs - 0.01).coerceAtLeast(0.0)
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
    // Liste der Meldungen auf whitehouse.gov, Senatoren ueber den mitgelieferten Stand.
    suspend fun copyPruefen() = copyPruefLock.withLock {
        glockenUebernehmen()
        // Im Hintergrund ist der Zustand der Einstellungen noch nicht geladen: direkt lesen
        val s = runCatching { settings.flow.first() }.getOrDefault(settingsFlow.value)
        if (!s.notifications) return@withLock
        I18n.lang = s.sprache
        val folge = glocken(s)
        if (folge.isEmpty()) return@withLock
        val repo = copy ?: return@withLock
        val ges = gesehen(s).toMutableMap()
        if (folge.any { it.startsWith("k:") }) runCatching { repo.verzeichnis() }
        for (key in folge) {
            if (key.startsWith("k:")) {
                val person = key.removePrefix("k:")
                val liste = repo.stand.value.meldungen.filter { it.person == person }.sortedByDescending { it.datum }
                val neueste = liste.firstOrNull() ?: continue
                val alt = ges[key]
                if (alt == null) { ges[key] = neueste.docId; continue }
                if (alt == neueste.docId) continue
                val altDatum = liste.firstOrNull { it.docId == alt }?.datum ?: 0L
                val neu = liste.filter { it.datum >= altDatum && it.docId != alt }.takeWhile { it.docId != alt }
                for (m in neu.reversed()) {
                    val g = repo.geschaefte(m).orEmpty()
                    val text = if (g.isEmpty()) S.copyNotifPaper else S.copyNotifTrades(g.size, g.take(3).joinToString(", ") { (if (it.kauf) S.copyBuy else if (it.verkauf) S.copySell else S.copyExchange) + " " + (it.kuerzel ?: it.wert) })
                    notifier?.notify(Notifier.CHANNEL_COPY, S.copyNotifTitle(m.name), text)
                }
                ges[key] = neueste.docId
            } else if (key.startsWith("i:")) {
                val inv = investoren.firstOrNull { it.id == key.removePrefix("i:") } ?: continue
                val sec = runCatching { repo.secNeueste(inv.cik) }.getOrNull() ?: continue
                val bekannt = inv.aktuell?.stichtag ?: ""
                if (sec.first > bekannt) _secNeuer.update { it + (inv.id to sec) }
                val alt = ges[key] ?: bekannt
                if (sec.first > alt) {
                    notifier?.notify(Notifier.CHANNEL_COPY, S.copyNotifTitle(inv.name), S.copyNotif13f(Fmt.date(isoTag(sec.first)), Fmt.date(isoTag(sec.second))))
                    ges[key] = sec.first
                }
            } else if (key.startsWith("n:")) {
                // Senat: das System sperrt Abrufe von ausserhalb der USA; neue Meldungen kommen mit der Fassung der App
                val person = key.removePrefix("n:")
                val neueste = senatNeueste(person) ?: continue
                val alt = ges[key]
                if (alt == null) { ges[key] = neueste.id; continue }
                if (alt == neueste.id) continue
                val altTag = senat?.meldungen?.firstOrNull { it.id == alt }?.let { de.troetenrepublik.app.market.Politik.tag(it.eingang) } ?: 0L
                val neu = senat?.meldungen.orEmpty().filter { it.person == person && de.troetenrepublik.app.market.Politik.tag(it.eingang) > altTag }
                val g = neu.flatMap { m -> m.geschaefte.map { de.troetenrepublik.app.market.Politik.geschaeft(it, de.troetenrepublik.app.market.Politik.tag(m.eingang)) } }
                if (neu.isNotEmpty()) {
                    val text = if (g.isEmpty()) S.copyNotifPaper else S.copyNotifTrades(g.size, g.take(3).joinToString(", ") { (if (it.kauf) S.copyBuy else if (it.verkauf) S.copySell else S.copyExchange) + " " + (it.kuerzel ?: it.wert) })
                    notifier?.notify(Notifier.CHANNEL_COPY, S.copyNotifTitle(neueste.name), text)
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
        settings.update { it.copy(copyGesehen = gesehenText(ges)) }
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
        if (brokerGesperrt()) return Nachbildung(0, 0, gewichte.map { it.first })
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

    // Quellen fuer die Nachrichtenseite: die eingeschalteten festen und alle eigenen Feeds.
    fun newsQuellen(s: Settings = settingsFlow.value): List<Rss.Quelle> {
        val aus = s.quellenAus
        val feste = Rss.eingebaut(s.sprache == Lang.DE).filter { it.schluessel !in aus && Rss.hierLesbar(it) }
        val eigene = s.eigeneFeeds.map { (name, url) -> Rss.eigene(name, url) }
        val mitSchluessel = Rss.mitSchluessel(s.keyGuardian, s.keyFinnhub, s.keyAlphaVantage).filter { it.schluessel !in aus }
        return feste + mitSchluessel + eigene
    }

    // Text eines Guardian-Artikels ueber die Content API; null ohne Schluessel oder Kontingent.
    suspend fun guardianArtikel(link: String) = news.guardianArtikel(link, settingsFlow.value.keyGuardian)

    // Schluessel eines Dienstes setzen: guardian, finnhub, alphavantage, deepl, reddit und reddit_secret.
    fun schluesselSetzen(dienst: String, wert: String) {
        scope.launch {
            settings.update { s ->
                when (dienst) {
                    de.troetenrepublik.app.market.Abrufkontingent.GUARDIAN -> s.copy(keyGuardian = wert.trim())
                    de.troetenrepublik.app.market.Abrufkontingent.FINNHUB -> s.copy(keyFinnhub = wert.trim())
                    de.troetenrepublik.app.market.Abrufkontingent.ALPHAVANTAGE -> s.copy(keyAlphaVantage = wert.trim())
                    de.troetenrepublik.app.market.Abrufkontingent.DEEPL -> s.copy(keyDeepl = wert.trim())
                    de.troetenrepublik.app.market.Abrufkontingent.FEC -> s.copy(keyFec = wert.trim())
                    de.troetenrepublik.app.market.Abrufkontingent.USPTO -> s.copy(keyUspto = wert.trim())
                    de.troetenrepublik.app.market.Abrufkontingent.REDDIT -> s.copy(keyRedditId = wert.trim())
                    de.troetenrepublik.app.ui.REDDIT_SECRET -> s.copy(keyRedditSecret = wert.trim())
                    // Nur eine gueltige oeffentliche Adresse wird gespeichert, nie ein privater Schluessel
                    de.troetenrepublik.app.ui.SEC_KONTAKT -> s.copy(secKontakt = wert.trim().takeIf { it.isEmpty() || de.troetenrepublik.app.market.SecInsider.kennung(it) != null } ?: s.secKontakt)
                    de.troetenrepublik.app.ui.POLY_ADRESSE -> s.copy(polyAdresse = wert.trim().takeIf { it.isEmpty() || de.troetenrepublik.app.market.Prognose.adresseGueltig(it) }?.lowercase() ?: s.polyAdresse)
                    else -> s
                }
            }
        }
    }

    // Marktinhalte der Prediction Markets ins Deutsche uebersetzen: nur bei deutscher Oberflaeche und mit
    // DeepL-Schluessel. Texte, die das Woerterbuch schon kennt (etwa Yes und No), kosten keine Zeichen.
    suspend fun pmUebersetzen(texte: Collection<String>) {
        val s = settingsFlow.value
        if (s.sprache != Lang.DE || s.keyDeepl.isBlank()) return
        val offen = texte.filter { t -> t.isNotBlank() && t !in de.troetenrepublik.app.ui.PmTexte.karte && !de.troetenrepublik.app.ui.pmFesterBegriff(t) }
        if (offen.isEmpty()) return
        val neu = runCatching { uebersetzer.deutsch(offen, s.keyDeepl) }.getOrDefault(emptyMap())
        if (neu.isNotEmpty()) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { de.troetenrepublik.app.ui.PmTexte.karte.putAll(neu) }
    }

    // Schlagzeilen laden; neu fragt alle Feeds neu ab.
    suspend fun newsLaden(neu: Boolean = false) {
        val s = settingsFlow.value
        news.laden(NewsRepository.Auftrag(newsQuellen(s), s.sprache == Lang.DE), neu)
    }

    private val newsMeldeLock = kotlinx.coroutines.sync.Mutex()

    // Mitteilungen zu neuen Schlagzeilen einstellen: de.troetenrepublik.app.data.MITTEILUNG_WICHTIGE, _ALLE oder _AUS.
    fun newsMitteilungSetzen(stufe: String) {
        scope.launch { settings.update { it.copy(newsMitteilung = stufe) } }
    }

    // Neue Schlagzeilen melden: alle oder nur die wichtigen, die auch die Nachrichtenseite markiert (gleiche Punkte,
    // Zuschlag fuer Werte aus Depot und Merkliste). Neu ist, was nach der letzten Meldung und dem letzten Oeffnen der
    // Nachrichtenseite erschienen ist, hoechstens zwoelf Stunden alt; beim ersten Mal wird nur der Stand gemerkt. Mehr als
    // drei auf einmal kommen als eine Sammelmitteilung. laden: Feeds vorher abrufen (bei geschlossener App).
    suspend fun newsMelden(laden: Boolean = false) = newsMeldeLock.withLock {
        // Im Hintergrund ist der Zustand der Einstellungen noch nicht geladen: direkt lesen
        val s = runCatching { settings.flow.first() }.getOrDefault(settingsFlow.value)
        if (!s.notifications || s.newsMitteilung == de.troetenrepublik.app.data.MITTEILUNG_AUS) return@withLock
        if (laden) news.laden(NewsRepository.Auftrag(newsQuellen(s), s.sprache == Lang.DE))
        val eintraege = news.stand.value.eintraege.filter { it.zeit > 0 }
        if (eintraege.isEmpty()) return@withLock
        val now = System.currentTimeMillis()
        val juengste = eintraege.maxOf { it.zeit }.coerceAtMost(now)
        if (s.newsGemeldetBis <= 0L) {
            settings.update { it.copy(newsGemeldetBis = juengste) }
            return@withLock
        }
        val bis = maxOf(s.newsGemeldetBis, s.newsGelesenBis)
        val frisch = Rss.zuMelden(eintraege, bis, now)
        if (frisch.isEmpty()) return@withLock
        settings.update { it.copy(newsGemeldetBis = maxOf(it.newsGemeldetBis, frisch.maxOf { e -> e.zeit }.coerceAtMost(now))) }
        val alle = s.newsMitteilung == de.troetenrepublik.app.data.MITTEILUNG_ALLE
        val melden = if (alle) frisch else {
            val konto = account.value
            val depot = de.troetenrepublik.app.market.Wichtig.depotBegriffe(
                konto.positions.map { it.name } + konto.watchlist.mapNotNull { Symbols.find(it)?.name },
                konto.positions.map { it.symbol } + konto.watchlist,
            )
            Rss.zuMelden(eintraege, bis, now, Rss.wichtige(eintraege, depot))
        }
        val n = notifier ?: return@withLock
        // Bei geschlossener App steht die Sprache noch nicht fest
        I18n.lang = s.sprache
        if (melden.size <= 3) melden.forEach { e ->
            n.notify(Notifier.CHANNEL_NEWS, if (alle) e.quelle.ifBlank { S.newsTitle } else S.newsNotifImportant(e.quelle), e.titel, e.link.ifBlank { null })
        } else {
            n.notify(Notifier.CHANNEL_NEWS, if (alle) S.newsNotifMany(melden.size) else S.newsNotifManyImportant(melden.size),
                melden.takeLast(3).reversed().joinToString("\n") { "• " + it.titel })
        }
    }

    // Nachrichtenseite geoeffnet: alles bis jetzt gilt als gelesen.
    fun newsGelesen(bis: Long = System.currentTimeMillis()) {
        scope.launch { settings.update { it.copy(newsGelesenBis = bis) } }
    }

    // Gemerkte Schlagzeilen (neueste Merkung zuerst).
    fun newsGemerkt(s: Settings = settingsFlow.value): List<Rss.Eintrag> = Rss.gemerktGueltig(Rss.gemerktLesen(s.newsGemerkt), System.currentTimeMillis())

    // Schlagzeile merken oder nicht mehr merken.
    fun newsMerken(e: Rss.Eintrag) {
        scope.launch { settings.update { s -> s.copy(newsGemerkt = Rss.gemerktText(Rss.umschalten(Rss.gemerktGueltig(Rss.gemerktLesen(s.newsGemerkt), System.currentTimeMillis()), e))) } }
    }

    // Eine feste Quelle (oder „depot“) ein- oder ausschalten.
    fun newsQuelleSchalten(schluessel: String, an: Boolean) {
        scope.launch {
            settings.update { s ->
                val aus = if (an) s.quellenAus - schluessel else s.quellenAus + schluessel
                s.copy(newsAus = aus.sorted().joinToString(","))
            }
            runCatching { newsLaden() }
        }
    }

    // Eigenen Feed pruefen und eintragen; Zahl der gefundenen Schlagzeilen, 0 = kein Feed (dann nichts eingetragen).
    suspend fun newsFeedHinzufuegen(name: String, url: String): Int {
        val adresse = url.trim()
        val n = news.pruefen(adresse)
        if (n > 0) {
            settings.update { s ->
                val ohne = s.eigeneFeeds.filter { it.second != adresse }
                val neu = ohne + (name.trim().replace('\t', ' ').replace('\n', ' ') to adresse)
                s.copy(newsEigene = neu.joinToString("\n") { "${it.first}\t${it.second}" })
            }
            runCatching { newsLaden() }
        }
        return n
    }

    fun newsFeedEntfernen(url: String) {
        scope.launch {
            settings.update { s -> s.copy(newsEigene = s.eigeneFeeds.filter { it.second != url }.joinToString("\n") { "${it.first}\t${it.second}" }) }
            runCatching { newsLaden() }
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
        if (brokerFehlt()) return false
        broker?.let { b -> return brokerAuftrag(b, Auftrag(symbol, Side.BUY, OrderType.MARKET, qty), derivative) }
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
        if (brokerFehlt()) return false
        val pos = account.value.positions.firstOrNull { it.key == key } ?: run {
            _toast.value = S.error("NOT_IN_DEPOT", ""); return false
        }
        broker?.let { b -> return brokerAuftrag(b, Auftrag(pos.symbol, Side.SELL, OrderType.MARKET, qty), pos.derivative) }
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
        if (brokerGesperrt()) return false
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
        if (brokerFehlt()) return false
        broker?.let { b ->
            // Die Ordermaske rechnet in Euro, der Broker in der Handelswaehrung
            val preis = if (type == OrderType.TRAILING_STOP) null else brokerPreis(symbol, limitPrice) ?: run { _toast.value = S.refreshFirst; return false }
            return brokerAuftrag(b, Auftrag(symbol, side, type, qty, preis, trailPct.takeIf { type == OrderType.TRAILING_STOP }, validity), derivative)
        }
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
        Trading.orderError(order)?.let { _toast.value = S.error(it, ""); return false }
        store.update { Trading.placeOrder(it, order) }
        _toast.value = when (type) {
            OrderType.LIMIT -> S.limitPlaced
            OrderType.TRAILING_STOP -> S.trailingPlaced
            else -> S.stopPlaced
        }
        return true
    }

    fun cancelOrder(id: String) {
        if (brokerFehlt()) return
        broker?.let { b ->
            scope.launch {
                _toast.value = try { b.stornieren(id.removePrefix("broker-")); S.brokerStorniert } catch (e: Exception) { S.brokerFehlerText(e.brokerCode(), e.message) }
                brokerAbgleich()
            }
            return
        }
        store.update { Trading.cancelOrder(it, id) }
        _toast.value = S.orderCancelled
    }

    // Was sich an der offenen Order o aendern laesst: im Uebungsdepot alles, beim Broker, was er kann.
    fun orderAenderbar(o: Order): Aenderbar? {
        if (o.status != OrderStatus.OPEN || o.type == OrderType.MARKET) return null
        val b = broker ?: return if (brokerErwartet) null else Aenderbar(menge = true, preis = true, gueltigkeit = true)
        return b.aenderbar(o.symbol)
    }

    // Offene Order aendern: Stueckzahl, Limit- bzw. Stoppreis (Euro), Trailing-Abstand und Gueltigkeit.
    // Beim Broker geht die Aenderung an ihn; was er nicht aendern kann, bleibt wie es war.
    fun orderAendern(id: String, qty: Double, limitPrice: Double, trailPct: Double, validity: Validity): Boolean {
        if (brokerFehlt()) return false
        val alt = account.value.orders.firstOrNull { it.id == id && it.status == OrderStatus.OPEN }
            ?: run { _toast.value = S.error(Err.ORDER_CLOSED, ""); return false }
        broker?.let { b ->
            val kann = b.aenderbar(alt.symbol) ?: run { _toast.value = S.brokerAendernNicht(b.art.anzeige); return false }
            val menge = if (kann.menge) qty else alt.qty
            val gueltig = if (kann.gueltigkeit) validity else alt.validity
            Trading.orderError(alt.copy(qty = menge, limitPrice = limitPrice, trailPct = trailPct))?.let { _toast.value = S.error(it, ""); return false }
            val preis = if (alt.type == OrderType.LIMIT || alt.type == OrderType.STOP) {
                brokerPreis(alt.symbol, limitPrice) ?: run { _toast.value = S.refreshFirst; return false }
            } else null
            val auftrag = Auftrag(alt.symbol, alt.side, alt.type, menge, preis, trailPct.takeIf { alt.type == OrderType.TRAILING_STOP }, gueltig)
            scope.launch {
                _toast.value = try { b.aendern(id.removePrefix("broker-"), auftrag); S.brokerGeaendert }
                catch (e: CancellationException) { throw e } catch (e: Exception) { S.brokerFehlerText(e.brokerCode(), e.message) }
                brokerAbgleich()
            }
            return true
        }
        var msg: String? = null
        var ok = false
        store.update { acc ->
            val r = Trading.orderAendern(acc, id, qty, limitPrice, trailPct, validity, System.currentTimeMillis())
            ok = r.ok
            msg = errorText(r.error, r.extra) ?: S.orderGeaendert
            r.account
        }
        _toast.value = msg
        return ok
    }

    fun toggleWatch(symbol: String) {
        val watched = symbol in account.value.watchlist
        val price = quotes.value[symbol]?.price ?: 0.0
        store.update { acc ->
            if (watched) Trading.unwatch(acc, symbol) else Trading.watch(acc, symbol, price, System.currentTimeMillis())
        }
        _toast.value = if (watched) S.watchRemoved else S.watchAdded
    }

    fun isWatched(symbol: String): Boolean = symbol in account.value.watchlist

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

    // ------------------------------------------------------------ Cash

    fun deposit(amount: Double) {
        if (brokerGesperrt()) return
        if (!amount.isFinite() || amount <= 0) { _toast.value = S.invalidAmount; return }
        store.update { Trading.deposit(it, amount) }
        _toast.value = S.depositedToast(Fmt.euro(amount))
    }

    fun withdraw(amount: Double): Boolean {
        if (brokerGesperrt()) return false
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
    fun withdrawable(): Double = (account.value.cash - account.value.reserved).coerceAtLeast(0.0)

    // ------------------------------------------------------------ Sparplaene

    fun addPlan(symbol: String, name: String, amount: Double, day: Int, interval: PlanInterval = PlanInterval.MONTHLY, uhrzeit: Int = -1): Boolean {
        if (brokerGesperrt()) return false
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
                )
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

    fun removeAlert(id: String) {
        store.update { it.copy(alerts = it.alerts.filter { a -> a.id != id }) }
    }

    // ------------------------------------------------------------ BaFin: Eigengeschaefte und Stimmrechte (seit 5.5)

    private val bafinDatei by lazy { de.troetenrepublik.app.plattform.SichereDatei(java.io.File(store.ordner, "bafin.json")) }
    private val bafinLock = kotlinx.coroutines.sync.Mutex()
    private val bafinNetz by lazy { MarketApi() }
    private val _bafin = MutableStateFlow<de.troetenrepublik.app.market.Bafin.Datei?>(null)

    // Mitgelieferter Stand, vereint mit den selbst geholten Meldungen; null, solange nicht geladen.
    val bafin: StateFlow<de.troetenrepublik.app.market.Bafin.Datei?> = _bafin.asStateFlow()

    // Eigengeschaefte zaehlen zwoelf Monate, wie in der Datenbank der BaFin.
    private fun bafinAbTag(): String = java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusDays(365).toString()

    private fun bafinStand(): de.troetenrepublik.app.market.Bafin.Datei {
        _bafin.value?.let { return it }
        val beigelegt = runCatching { de.troetenrepublik.app.market.Bafin.lesen(de.troetenrepublik.app.plattform.Plattform.asset("copytrading/bafin.json").decodeToString()) }.getOrNull()
            ?: de.troetenrepublik.app.market.Bafin.Datei()
        val eigen = runCatching { de.troetenrepublik.app.market.Bafin.lesen(bafinDatei.lesen().toString(Charsets.UTF_8)) }.getOrNull()
        val d = beigelegt.copy(
            eigengeschaefte = de.troetenrepublik.app.market.Bafin.vereinen(beigelegt.eigengeschaefte, eigen?.eigengeschaefte.orEmpty(), bafinAbTag()),
            stand = maxOf(beigelegt.stand, eigen?.stand.orEmpty()),
            abgerufen = eigen?.abgerufen ?: 0L,
        )
        _bafin.value = d
        return d
    }

    // Stand laden und hoechstens alle zwoelf Stunden (neu: sofort) die Meldungen des letzten Monats bei der BaFin
    // nachholen; mit netz = false nur den Stand laden. Ohne Netz bleibt der bisherige Stand.
    suspend fun bafinLaden(neu: Boolean = false, netz: Boolean = true): de.troetenrepublik.app.market.Bafin.Datei = bafinLock.withLock {
        val d = withContext(Dispatchers.IO) { bafinStand() }
        val jetzt = System.currentTimeMillis()
        if (!netz || (!neu && jetzt - d.abgerufen < 12 * 3_600_000L)) return@withLock d
        val frisch = runCatching { bafinAbrufen(d) }.getOrNull() ?: return@withLock d
        val liste = de.troetenrepublik.app.market.Bafin.vereinen(d.eigengeschaefte, frisch, bafinAbTag())
        val heute = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString()
        val n = d.copy(eigengeschaefte = liste, stand = maxOf(d.stand, heute), abgerufen = jetzt)
        _bafin.value = n
        withContext(Dispatchers.IO) {
            runCatching { bafinDatei.schreiben(de.troetenrepublik.app.market.Bafin.schreiben(de.troetenrepublik.app.market.Bafin.Datei(stand = n.stand, eigengeschaefte = liste, abgerufen = jetzt)).toByteArray(Charsets.UTF_8)) }
        }
        n
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
    suspend fun symbolFuerIsin(isin: String, bekannt: String? = null): String? {
        bekannt?.takeIf { it.isNotBlank() }?.let { return it }
        if (isin.isBlank()) return null
        isinSymbole[isin]?.let { return it }
        val s = Symbols.all.firstOrNull { it.isin == isin }?.symbol
            ?: companies.value.values.firstOrNull { it.isin == isin }?.symbol
            ?: runCatching { market.yahooSuche(isin) }.getOrDefault(emptyList()).let { h ->
                h.firstOrNull { it.symbol.endsWith(".DE") } ?: h.firstOrNull { it.symbol.endsWith(".F") } ?: h.firstOrNull()
            }?.symbol
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
            "GB" -> runCatching { lpNetz.datei(de.troetenrepublik.app.market.Leerpositionen.FCA_AKTUELL, max = 5_000_000) }.getOrNull()?.let { aktuell ->
                val historisch = runCatching { lpNetz.datei(de.troetenrepublik.app.market.Leerpositionen.FCA_HISTORISCH, max = 20_000_000) }.getOrNull()
                withContext(Dispatchers.Default) { de.troetenrepublik.app.market.Leerpositionen.fcaAuswerten(aktuell.decodeToString(), historisch?.decodeToString(), heute) }
            }.orEmpty()
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
                "GB" -> de.troetenrepublik.app.market.InsiderQuellen.nsmHolen(q, von.toString(), heute.toString(), bekannt, max = 150)
                "CH" -> de.troetenrepublik.app.market.InsiderWelt.sixHolen(q, von.toString(), heute.toString())
                else -> null
            }
        }.getOrNull()
    }

    // Stand eines Landes laden und hoechstens alle zwoelf Stunden die Meldungen seit dem Stand nachholen.
    suspend fun euLaden(land: String, netz: Boolean = true): de.troetenrepublik.app.market.EuInsider.Datei = euLock.withLock {
        val d = withContext(Dispatchers.IO) { euStand(land) }
        val jetzt = System.currentTimeMillis()
        if (!netz || jetzt - d.abgerufen < 12 * 3_600_000L) return@withLock d
        val heute = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
        val von = if (d.stand.isBlank() && land == "CH") heute.minusDays(365)
            else runCatching { java.time.LocalDate.parse(d.stand).minusDays(3) }.getOrNull()?.takeIf { it.isAfter(heute.minusDays(60)) } ?: heute.minusDays(30)
        val frisch = euFrisch(land, d, von, heute) ?: return@withLock d.also { _euInsider.value = _euInsider.value + (land to it.copy(abgerufen = jetzt)) }
        val liste = de.troetenrepublik.app.market.EuInsider.vereinen(d.geschaefte, frisch.filter { it.isin.isNotEmpty() }, secAbTag())
        val gelesen = (d.gelesen + frisch.map { it.id }.filter { it.isNotEmpty() }).distinct().takeLast(30_000)
        val n = d.copy(geschaefte = liste, stand = maxOf(d.stand, heute.toString()), abgerufen = jetzt, gelesen = gelesen)
        _euInsider.value = _euInsider.value + (land to n)
        withContext(Dispatchers.IO) {
            runCatching { euDatei(land).schreiben(de.troetenrepublik.app.market.EuInsider.schreiben(n).toByteArray(Charsets.UTF_8)) }
        }
        n
    }

    // ------------------------------------------------------------ Insidergeschaefte je Aktie: Indien, Hongkong, Australien (seit 5.9)

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
                val d = e.datei.copy(abgerufen = System.currentTimeMillis())
                _leerpositionen.value = _leerpositionen.value + (d.land to d)
                withContext(Dispatchers.IO) { runCatching { lpDatei(d.land).schreiben(de.troetenrepublik.app.market.Leerpositionen.schreiben(d).toByteArray(Charsets.UTF_8)) } }
                S.imLeer(d.titel.size, d.land)
            }
            is de.troetenrepublik.app.market.RegisterImport.Ergebnis.Unbekannt -> if (e.pdf) S.imPdf else S.imUnbekannt
        }
    }

    suspend fun weltLaden(symbol: String, isin: String) {
        val (land, code) = de.troetenrepublik.app.market.InsiderWelt.boerse(symbol) ?: return
        val firma = runCatching { profileFor(symbol)?.longName }.getOrNull().orEmpty()
        fun eigene(d: de.troetenrepublik.app.market.EuInsider.Datei) =
            d.geschaefte.filter { de.troetenrepublik.app.market.InsiderWelt.passt(it, land, code, isin, firma) }
        val d = euLock.withLock { withContext(Dispatchers.IO) { euStand(land) } }
        _weltInsider.value = _weltInsider.value + (symbol to (land to eigene(d)))
        val jetzt = System.currentTimeMillis()
        if (jetzt - (weltZeit[symbol] ?: 0L) < 12 * 3_600_000L) return
        weltZeit[symbol] = jetzt
        val heute = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
        val ab = runCatching { java.time.LocalDate.parse(d.stand).minusDays(3) }.getOrNull()
        val e = withContext(Dispatchers.IO) {
            runCatching { de.troetenrepublik.app.market.InsiderWelt.holen(de.troetenrepublik.app.market.InsiderQuellen.netz(MarketApi()), symbol, isin, firma, heute, ab) }.getOrNull()
        }
        if (e == null) { weltZeit.remove(symbol); return }
        // Dasselbe Geschaeft kann an NSE und BSE gemeldet sein: Person, Tag, Menge und Art gleich gilt als doppelt
        fun doppelt(g: de.troetenrepublik.app.market.EuInsider.Geschaeft) = "${de.troetenrepublik.app.market.InsiderWelt.name(g.p)}|${g.d}|${g.n}|${g.art}"
        val n = euLock.withLock {
            val akt = withContext(Dispatchers.IO) { euStand(land) }
            val bekannt = akt.geschaefte.map { doppelt(it) }.toHashSet()
            val neu = e.second.filter { doppelt(it) !in bekannt }
            val n = akt.copy(
                geschaefte = de.troetenrepublik.app.market.EuInsider.vereinen(akt.geschaefte, neu, secAbTag()),
                gelesen = (akt.gelesen + neu.map { it.id }.filter { it.isNotEmpty() }).distinct().takeLast(30_000),
            )
            _euInsider.value = _euInsider.value + (land to n)
            withContext(Dispatchers.IO) {
                runCatching { euDatei(land).schreiben(de.troetenrepublik.app.market.EuInsider.schreiben(n).toByteArray(Charsets.UTF_8)) }
            }
            n
        }
        _weltInsider.value = _weltInsider.value + (symbol to (land to eigene(n)))
    }

    // ------------------------------------------------------------ SEC: Insidergeschaefte an US-Boersen (seit 5.6)

    private val secDatei by lazy { de.troetenrepublik.app.plattform.SichereDatei(java.io.File(store.ordner, "sec.json")) }
    private val secLock = kotlinx.coroutines.sync.Mutex()
    private val secNetz by lazy { MarketApi() }
    private val _sec = MutableStateFlow<de.troetenrepublik.app.market.SecInsider.Datei?>(null)

    // Mitgelieferter Stand, vereint mit selbst geholten Meldungen; null, solange nicht geladen.
    val sec: StateFlow<de.troetenrepublik.app.market.SecInsider.Datei?> = _sec.asStateFlow()

    // US-Meldungen zaehlen 18 Monate, wie die mitgelieferten Datensaetze.
    private fun secAbTag(): String = java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusDays(550).toString()

    private fun secStand(): de.troetenrepublik.app.market.SecInsider.Datei {
        _sec.value?.let { return it }
        val beigelegt = runCatching { de.troetenrepublik.app.market.SecInsider.lesen(de.troetenrepublik.app.plattform.Plattform.asset("copytrading/sec.json").decodeToString()) }.getOrNull()
            ?: de.troetenrepublik.app.market.SecInsider.Datei()
        val eigen = runCatching { de.troetenrepublik.app.market.SecInsider.lesen(secDatei.lesen().toString(Charsets.UTF_8)) }.getOrNull()
        val d = beigelegt.copy(
            geschaefte = de.troetenrepublik.app.market.SecInsider.vereinen(beigelegt.geschaefte, eigen?.geschaefte.orEmpty(), secAbTag()),
            stand = maxOf(beigelegt.stand, eigen?.stand.orEmpty()),
            abgerufen = eigen?.abgerufen ?: 0L,
        )
        _sec.value = d
        return d
    }

    // Stand laden; mit Kontaktadresse unter Zugaenge hoechstens alle sechs Stunden (neu: sofort) die neuesten
    // Form-4-Meldungen der SEC nachholen, mit symbol zusaetzlich die Meldungen dieses Werts.
    suspend fun secLaden(neu: Boolean = false, symbol: String? = null): de.troetenrepublik.app.market.SecInsider.Datei = secLock.withLock {
        val d = withContext(Dispatchers.IO) { secStand() }
        val kennung = de.troetenrepublik.app.market.SecInsider.kennung(settingsFlow.value.secKontakt) ?: return@withLock d
        val jetzt = System.currentTimeMillis()
        val allgemein = neu || jetzt - d.abgerufen > 6 * 3_600_000L
        if (!allgemein && symbol == null) return@withLock d
        val frisch = runCatching { secAbrufen(d, kennung, allgemein, symbol) }.getOrNull() ?: return@withLock d
        if (frisch.isEmpty() && !allgemein) return@withLock d
        val liste = de.troetenrepublik.app.market.SecInsider.vereinen(d.geschaefte, frisch, secAbTag())
        val heute = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString()
        val n = d.copy(geschaefte = liste, stand = maxOf(d.stand, heute), abgerufen = if (allgemein) jetzt else d.abgerufen)
        _sec.value = n
        withContext(Dispatchers.IO) {
            runCatching { secDatei.schreiben(de.troetenrepublik.app.market.SecInsider.schreiben(de.troetenrepublik.app.market.SecInsider.Datei(stand = n.stand, geschaefte = liste, abgerufen = n.abgerufen)).toByteArray(Charsets.UTF_8)) }
        }
        n
    }

    // Neueste Meldungen (Atom-Feed) und, mit symbol, die Meldungen dieses Werts aus seiner Liste bei data.sec.gov.
    // Nur unbekannte Meldungen, nacheinander mit Pause (die SEC erlaubt zehn Abrufe je Sekunde).
    private suspend fun secAbrufen(d: de.troetenrepublik.app.market.SecInsider.Datei, kennung: String, allgemein: Boolean, symbol: String?): List<de.troetenrepublik.app.market.SecInsider.Geschaeft> {
        val kopf = mapOf("User-Agent" to kennung, "Accept" to "*/*")
        suspend fun text(url: String): String? { delay(150); return secNetz.anfrage(url, kopf).takeIf { it.ok }?.text }
        val bekannt = d.geschaefte.mapTo(HashSet()) { it.a }
        val offen = LinkedHashMap<String, String>()
        if (allgemein) text(de.troetenrepublik.app.market.SecInsider.NEUESTE)?.let { atom -> de.troetenrepublik.app.market.SecInsider.neuesteLesen(atom).forEach { (a, cik) -> if (a !in bekannt) offen[a] = cik } }
        if (symbol != null) {
            val cik = d.geschaefte.firstOrNull { it.k.equals(symbol, true) }?.c
            if (!cik.isNullOrBlank()) {
                text("https://data.sec.gov/submissions/CIK" + cik.padStart(10, '0') + ".json")?.let { j ->
                    val o = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(j).jsonObject }.getOrNull()
                    val r = o?.get("filings")?.jsonObject?.get("recent")?.jsonObject
                    val formen = r?.get("form")?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
                    val nummern = r?.get("accessionNumber")?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
                    formen.indices.filter { formen[it] == "4" }.take(40).forEach { i -> nummern.getOrNull(i)?.let { if (it !in bekannt) offen[it] = cik } }
                }
            }
        }
        val aus = ArrayList<de.troetenrepublik.app.market.SecInsider.Geschaeft>()
        for ((a, cik) in offen.entries.take(120)) {
            val index = text(de.troetenrepublik.app.market.SecInsider.verzeichnisAdresse(cik, a)) ?: continue
            val datei = de.troetenrepublik.app.market.SecInsider.xmlDatei(index) ?: continue
            val xml = text(de.troetenrepublik.app.market.SecInsider.archivAdresse(cik, a, datei)) ?: continue
            aus += de.troetenrepublik.app.market.SecInsider.form4(xml, a)
        }
        return aus
    }

    // ------------------------------------------------------------ Notizen

    // Notiz zu einem Wert setzen; leer loescht sie.
    fun notizSetzen(symbol: String, text: String) {
        val t = text.trim().take(de.troetenrepublik.app.data.NOTIZ_MAX)
        store.update { it.copy(notizen = if (t.isEmpty()) it.notizen - symbol else it.notizen + (symbol to t)) }
    }

    // ------------------------------------------------------------ Konto

    // Neues Depot; announce = false bei der Einrichtung, dort gibt es nichts zurueckzusetzen.
    fun reset(capital: Double = Trading.START_CASH, announce: Boolean = true) {
        if (brokerGesperrt()) return
        store.reset(capital)
        if (announce) _toast.value = S.accountReset
    }

    // Export eines Brokers lesen: Zeilen umrechnen und jedem Wert ein Yahoo-Kuerzel zuordnen (Katalog der App,
    // sonst Yahoo-Suche nach der ISIN, deutsche Notierung bevorzugt; Krypto als BTC-EUR). Liefert eine Vorschau,
    // ohne etwas zu aendern; null, wenn das Format unbekannt ist oder die Datei keine Zeilen hat.
    suspend fun brokerExportLesen(text: String): de.troetenrepublik.app.engine.TradeRepublicImport.Ergebnis? {
        val tr = de.troetenrepublik.app.engine.TradeRepublicImport
        val format = de.troetenrepublik.app.engine.BrokerExport.erkennen(text) ?: return null
        val zeilen = withContext(Dispatchers.Default) { de.troetenrepublik.app.engine.BrokerExport.lesen(format, text) }
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
                        else katalog[k] ?: companies.value.values.firstOrNull { it.isin == k }?.symbol ?: runCatching { market.yahooSuche(k) }.getOrDefault(emptyList()).let { hits ->
                            hits.firstOrNull { it.symbol.endsWith(".DE") } ?: hits.firstOrNull { it.symbol.endsWith(".F") } ?: hits.firstOrNull()
                        }?.symbol
                        s?.let { k to it }
                    }
                }
            }.awaitAll().filterNotNull().toMap()
        }
        return withContext(Dispatchers.Default) { tr.konto(zeilen, kuerzel, account.value, System.currentTimeMillis(), format.anzeige, format.praefix) }
    }

    // Gelesenen Broker-Export uebernehmen: ersetzt das Depot oder kommt zum aktuellen dazu.
    fun brokerExportUebernehmen(e: de.troetenrepublik.app.engine.TradeRepublicImport.Ergebnis, zusammenfuehren: Boolean) {
        if (brokerGesperrt()) return
        store.replace(if (zusammenfuehren) de.troetenrepublik.app.engine.Zusammenfuehren.konten(store.account.value, e.konto) else e.konto)
        syncTracked()
        _toast.value = S.trImportiert(e.quelle, e.konto.transactions.size)
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
        if (broker != null) return S.brokerNurUebung
        val parsed = Backup.parse(text)
        return when (parsed) {
            is Backup.Outcome.Error -> S.importError(parsed.code, parsed.extra)
            is Backup.Outcome.Ok -> {
                val p = parsed.payload
                if (zusammenfuehren) {
                    store.replace(de.troetenrepublik.app.engine.Zusammenfuehren.konten(store.account.value, p.account))
                    p.companies.values.forEach { if (companies.value[it.symbol] == null) store.putCompany(firmaBereinigt(it)) }
                    _toast.value = S.depotMerged
                    return null
                }
                store.replace(p.account)
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
    private val newsCache = ConcurrentHashMap<String, List<MarketApi.NewsItem>>()
    private val companyLock = ConcurrentHashMap<String, Boolean>()

    suspend fun profileFor(symbol: String): MarketApi.Profile? {
        profileCache[symbol]?.let { return it }
        val roh = runCatching { market.profile(symbol) }.getOrNull()
        val p = if (roh == null || profilLuecken(roh)) runCatching { profilVonGleichen(symbol, roh) }.getOrNull() ?: roh else roh
        p ?: return null
        profileCache[symbol] = p
        return p
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

    suspend fun newsFor(symbol: String): List<MarketApi.NewsItem> {
        val key = "$symbol|${settingsFlow.value.sprache.code}"
        newsCache[key]?.let { return it }
        val n = market.news(symbol, settingsFlow.value.sprache.code)
        if (n.isNotEmpty()) newsCache[key] = n
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
        if (known != null && !ohneArtikel && (!known.missing || now - known.fetchedAt < 7 * 86_400_000L)) return known
        if (Symbols.typeOf(symbol) != AssetType.STOCK) return known
        if (companyLock.putIfAbsent(symbol, true) != null) return known
        try {
            val info = runCatching { market.companyInfo(symbol, name) }.getOrNull() ?: return known
            // Kuratierte ISIN hat Vorrang
            val curated = Symbols.find(symbol)?.isin.orEmpty()
            val merged = if (curated.isNotBlank()) info.copy(isin = curated) else info
            store.putCompany(merged)
            return merged
        } finally {
            companyLock.remove(symbol)
        }
    }

    // ISIN eines Werts: kuratiert, sonst aus den Stammdaten.
    fun isinFor(symbol: String): String =
        Symbols.find(symbol)?.isin?.takeIf { it.isNotBlank() } ?: companies.value[symbol]?.isin.orEmpty()

    // Beschreibung eines Werts: Text, ob er von Wikipedia stammt, und die Adresse des Artikels (leer ohne).
    data class Beschreibung(val text: String, val wikipedia: Boolean, val artikel: String = "")

    // Beschreibung in der gewaehlten Sprache: Wikipedia zuerst, sonst die englische Zusammenfassung.
    fun descriptionFor(symbol: String, profile: MarketApi.Profile?): Beschreibung {
        val info = companies.value[symbol]
        val de = settingsFlow.value.sprache == Lang.DE
        val (text, artikel) = when {
            info == null -> null to ""
            de && info.descriptionDe.isNotBlank() -> info.descriptionDe to info.articleDe
            !de && info.descriptionEn.isNotBlank() -> info.descriptionEn to info.articleEn
            info.descriptionEn.isNotBlank() -> info.descriptionEn to info.articleEn
            info.descriptionDe.isNotBlank() -> info.descriptionDe to info.articleDe
            else -> null to ""
        }
        if (text != null) return Beschreibung(text, true, artikel)
        return Beschreibung(profile?.description.orEmpty(), false)
    }

    private val volCache = ConcurrentHashMap<String, Double>()

    suspend fun volatilityFor(symbol: String): Double {
        volCache[symbol]?.let { return it }
        val type = Symbols.typeOf(symbol)
        val fallback = Derivatives.defaultVol(type)
        val candles = market.candles(symbol, Range.Y1)
        val v = Pricing.historicalVol(candles.map { it.close }, fallback)
        volCache[symbol] = v
        return v
    }

    suspend fun series(symbol: String, range: Range): Series = market.series(symbol, range)

    suspend fun candles(symbol: String, range: Range): List<Candle> = market.candles(symbol, range)

    // So viele Aktien zeigt die Heatmap fuer den ganzen Markt; kleinere waeren nur noch Striche.
    private val MARKT_KACHELN = 500

    // Ein Wert der Heatmap: Gewicht fuer die Flaeche, Tagesaenderung in Prozent (null = kein Kurs), Branche.
    data class HeatmapWert(val symbol: String, val name: String, val gewicht: Double, val aenderung: Double?, val branche: String)

    // Werte fuer die Heatmap. quelle: "MARKT" (die MARKT_KACHELN groessten Aktien des Katalogs weltweit),
    // "DEPOT" (Flaeche nach Positionswert), "WATCHLIST" (alle gleich gross) oder ein Index-Schluessel des Katalogs wie
    // "DAX" (Flaeche nach Boersenwert). Kurse aus der Sammelabfrage.
    suspend fun heatmap(quelle: String): List<HeatmapWert> {
        val branche = { s: String -> de.troetenrepublik.app.market.Catalog.industryOf(s).orEmpty() }
        return when (quelle) {
            HeatmapQuelle.DEPOT -> {
                val pos = valuation.value.positions.filter { !it.position.isDerivative && it.value > 0 }
                val q = market.snapshot(pos.map { it.position.symbol }, 2 * 60_000L)
                pos.groupBy { it.position.symbol }.map { (s, liste) ->
                    val p = liste.first().position
                    HeatmapWert(s, p.name.ifBlank { s }, liste.sumOf { it.value }, q[s]?.takeIf { it.prevClose > 0 }?.changePct, branche(s))
                }
            }
            HeatmapQuelle.WATCHLIST -> {
                val syms = account.value.watchlist
                val q = market.snapshot(syms, 2 * 60_000L)
                syms.map { s -> HeatmapWert(s, q[s]?.name?.ifBlank { null } ?: s, 1.0, q[s]?.takeIf { it.prevClose > 0 }?.changePct, branche(s)) }
            }
            else -> {
                val alle = de.troetenrepublik.app.market.Catalog.get().aktien
                val mitglieder = if (quelle == HeatmapQuelle.MARKT) alle else alle.filter { quelle in it.x }
                val q = market.snapshot(mitglieder.map { it.s }, 2 * 60_000L)
                val werte = mitglieder.mapNotNull { m ->
                    val k = q[m.s]?.takeIf { it.marketCap > 0 } ?: return@mapNotNull null
                    HeatmapWert(m.s, m.n, k.marketCap, k.takeIf { it.prevClose > 0 }?.changePct, m.i)
                }
                if (quelle == HeatmapQuelle.MARKT) werte.sortedByDescending { it.gewicht }.take(MARKT_KACHELN) else werte
            }
        }
    }

    // Kursverlauf eines Werts in einem frei gewaehlten Zeitraum: ganze Tage vonTag bis bisTag.
    suspend fun zeitraum(symbol: String, vonTag: Long, bisTag: Long): Series {
        val (von, bis) = de.troetenrepublik.app.engine.Zeitraum.grenzen(vonTag, bisTag, System.currentTimeMillis())
        return market.zeitraum(symbol, von, bis)
    }

    // Tagesschlusskurse 1999 bis 2016 (EUR zum heutigen Kurs), fuer die Krisen vor 2016 im Crash-Test.
    suspend fun longHistory(symbol: String): List<Candle> = runCatching { market.earlyDaily(symbol) }.getOrDefault(emptyList())

    // Tagesschlusskurse in Handelswaehrung ueber sechs Jahre, leer ohne Daten.
    suspend fun kurseNativ(symbol: String): List<Candle> = runCatching { market.taeglichNativ(symbol) }.getOrDefault(emptyList())

    // Tagesschlusskurse fuer bis zu 20 Werte in einer Abfrage.
    suspend fun schlusskurse(symbols: List<String>, range: String, interval: String = "1d"): Map<String, List<Candle>> =
        runCatching { market.schlusskurse(symbols, range, interval) }.getOrDefault(emptyMap())

    // Kennzahlen fuer den Depotfinder, je 100 Kuerzel eine Abfrage.
    suspend fun kennzahlen(symbols: List<String>): Map<String, Map<String, Double>> {
        val aus = HashMap<String, Map<String, Double>>()
        for (t in symbols.chunked(100)) runCatching { market.kennzahlen(t) }.getOrNull()?.let { aus.putAll(it) }
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

    // Zuletzt gelesene Beitraege der deutschen Subreddits, fuer das Stimmungsbarometer.
    @Volatile private var redditDeutschBeitraege: List<de.troetenrepublik.app.engine.RedditAuswertung.Beitrag> = emptyList()

    // Alle Stimmungsbarometer; null, wo die Quelle nicht antwortet oder kein Zugang eingetragen ist.
    data class Stimmungen(
        val aktien: de.troetenrepublik.app.engine.Stimmungsbarometer.Barometer?,
        val krypto: de.troetenrepublik.app.engine.Stimmungsbarometer.Barometer?,
        val wsb: de.troetenrepublik.app.engine.Stimmungsbarometer.RedditBarometer?,
        val deutsch: de.troetenrepublik.app.engine.Stimmungsbarometer.Barometer?,
        // AAII-Umfrage unter US-Privatanlegern.
        val aaii: de.troetenrepublik.app.engine.Stimmungsbarometer.AaiiBarometer? = null,
    )

    private val stimmungNetz by lazy { MarketApi() }
    @Volatile private var stimmungCache: Pair<Long, Stimmungen>? = null

    // Fear & Greed von CNN (Aktien) und alternative.me (Krypto), die Stimmung auf r/wallstreetbets (Tradestie) und mit
    // eigenem Reddit-Zugang die der deutschen Subreddits; 30 Minuten zwischengespeichert. Ein Dienst, der nicht
    // antwortet, behaelt seinen letzten Wert.
    suspend fun stimmungen(neu: Boolean = false): Stimmungen {
        val now = System.currentTimeMillis()
        stimmungCache?.takeIf { !neu && now - it.first < 30 * 60_000L }?.let { return it.second }
        val sb = de.troetenrepublik.app.engine.Stimmungsbarometer
        val alt = stimmungCache?.second
        val s = coroutineScope {
            val aktien = async { stimmungNetz.anfrage(sb.CNN, mapOf("Referer" to sb.CNN_SEITE)).text?.let(sb::cnnLesen) }
            val krypto = async { stimmungNetz.anfrage(sb.KRYPTO).text?.let(sb::kryptoLesen) }
            val wsb = async { stimmungNetz.anfrage(sb.WSB).text?.let { sb.wsbBarometer(sb.wsbLesen(it), now) } }
            val aaii = async {
                stimmungNetz.anfrage(de.troetenrepublik.app.engine.Fruehwarnung.AAII, mapOf("Accept" to "text/html")).text
                    ?.let { sb.aaiiBarometer(de.troetenrepublik.app.engine.Fruehwarnung.aaiiLesen(it, now)) }
            }
            val deutsch = async {
                if (!redditAktiv()) null else {
                    if (redditDeutschBeitraege.isEmpty()) runCatching { redditTrends(de.troetenrepublik.app.engine.RedditAuswertung.QUELLE) }
                    sb.beitragsBarometer(de.troetenrepublik.app.engine.RedditAuswertung.stimmung(redditDeutschBeitraege), now)
                }
            }
            Stimmungen(
                runCatching { aktien.await() }.getOrNull() ?: alt?.aktien,
                runCatching { krypto.await() }.getOrNull() ?: alt?.krypto,
                runCatching { wsb.await() }.getOrNull() ?: alt?.wsb,
                runCatching { deutsch.await() }.getOrNull() ?: alt?.deutsch?.takeIf { redditAktiv() },
                runCatching { aaii.await() }.getOrNull() ?: alt?.aaii,
            )
        }
        if (s.aktien != null || s.krypto != null || s.wsb != null) stimmungCache = now to s
        return s
    }

    @Volatile private var fruehwarnCache: Pair<Long, List<de.troetenrepublik.app.engine.Fruehwarnung.Zeichen>>? = null

    // Fruehwarnzeichen: US-Zinskurve vom US-Finanzministerium (dieses und voriges Jahr), Arbeitslosenquote
    // der BLS fuer die Sahm-Regel, VIX, VIX3M, S&P 500 und DAX von Yahoo; sechs Stunden zwischengespeichert. Zeichen ohne
    // Daten fehlen.
    @Volatile private var positionCache: Pair<Long, List<de.troetenrepublik.app.engine.Positionierung.Lage>>? = null

    // Positionierung grosser Spekulanten an den Terminmaerkten (CFTC, woechentlich), zwoelf Stunden zwischengespeichert.
    suspend fun positionierung(neu: Boolean = false): List<de.troetenrepublik.app.engine.Positionierung.Lage> {
        val now = System.currentTimeMillis()
        positionCache?.takeIf { !neu && now - it.first < 12 * 3_600_000L }?.let { return it.second }
        val netz = MarketApi()
        val liste = coroutineScope {
            de.troetenrepublik.app.engine.Positionierung.MAERKTE.map { (schluessel, code) ->
                async {
                    runCatching { netz.anfrage(de.troetenrepublik.app.engine.Positionierung.adresse(code), mapOf("Accept" to "application/json")).takeIf { it.ok }?.text }.getOrNull()
                        ?.let { de.troetenrepublik.app.engine.Positionierung.lage(schluessel, de.troetenrepublik.app.engine.Positionierung.lesen(it)) }
                }
            }.awaitAll().filterNotNull()
        }
        if (liste.isNotEmpty()) positionCache = now to liste
        return liste.ifEmpty { positionCache?.second.orEmpty() }
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

    suspend fun fruehwarnung(neu: Boolean = false): List<de.troetenrepublik.app.engine.Fruehwarnung.Zeichen> {
        val now = System.currentTimeMillis()
        fruehwarnCache?.takeIf { !neu && now - it.first < 6 * 3_600_000L }?.let { return it.second }
        val fw = de.troetenrepublik.app.engine.Fruehwarnung
        val jahr = fw.jahrMonat(now).first
        val zeichen = coroutineScope {
            val kurve = async {
                listOf(jahr - 1, jahr).map { j -> async { stimmungNetz.anfrage(fw.treasuryAdresse(j), mapOf("Accept" to "text/csv")).text?.let(fw::treasuryLesen).orEmpty() } }
                    .awaitAll().flatten().sortedBy { it.first }
            }
            val arbeit = async { stimmungNetz.anfrage(fw.BLS).text?.let(fw::blsLesen).orEmpty() }
            val kurse = async { runCatching { market.schlusskurse(listOf("^VIX", "^VIX3M", "^GSPC", "^GDAXI"), "2y") }.getOrDefault(emptyMap()) }
            val k = kurse.await().mapValues { (_, l) -> l.map { it.t to it.close } }
            listOfNotNull(
                runCatching { fw.zinskurve(kurve.await()) }.getOrNull(),
                runCatching { fw.sahm(arbeit.await()) }.getOrNull(),
                k["^VIX"]?.let(fw::vix),
                k["^VIX"]?.let { v -> k["^VIX3M"]?.let { fw.vixStruktur(v, it) } },
                k["^GSPC"]?.let { fw.trend(fw.TREND_SP500, it) },
                k["^GDAXI"]?.let { fw.trend(fw.TREND_DAX, it) },
            )
        }
        // Was diesmal fehlt, bleibt vom letzten Abruf
        val alt = fruehwarnCache?.second.orEmpty()
        val zusammen = fw.ALLE.mapNotNull { s -> zeichen.firstOrNull { it.schluessel == s } ?: alt.firstOrNull { it.schluessel == s } }
        if (zusammen.isNotEmpty()) fruehwarnCache = now to zusammen
        return zusammen
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

    // Rangliste der deutschen Boersen-Subreddits: die neuesten Beitraege aus
    // r/wallstreetbetsGER, r/mauerstrassenwetten, r/Aktien und r/Finanzen, darin gezaehlt, welche Werte genannt werden.
    // Im Format von ApeWisdom, damit Verlauf und Anzeige dieselben sind.
    private suspend fun redditDeutsch(): List<MarketApi.Erwaehnung> {
        val s = settingsFlow.value
        if (!redditAktiv(s)) return emptyList()
        val ra = de.troetenrepublik.app.engine.RedditAuswertung
        val beitraege = reddit.liste(de.troetenrepublik.app.market.RedditApi.neuAdresse(ra.DEUTSCHE_SUBS), s.keyRedditId, s.keyRedditSecret)
            ?: return emptyList()
        redditDeutschBeitraege = beitraege
        val kandidaten = redditKandidaten()
        return withContext(Dispatchers.Default) {
            ra.zaehlen(beitraege, kandidaten, System.currentTimeMillis()).mapIndexed { i, z ->
                MarketApi.Erwaehnung(i + 1, z.symbol, z.name, z.erwaehnungen, z.punkte, null, z.vorher)
            }
        }
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

    // Ist ein Rueckblick faellig (werktags ab 22:10 Uhr, fuer den Vortag bis 8 Uhr), wird er erstellt, gesammelt
    // und gemeldet, freitags dazu der Wochenrueckblick. Im Vordergrund alle fuenf Minuten, im Hintergrund mit den
    // Nachrichten.
    suspend fun rueckblickPruefen() {
        val s = runCatching { settings.flow.first() }.getOrDefault(settingsFlow.value)
        if (!s.tagesrueckblick || store.gesperrt.value) return
        val jetzt = System.currentTimeMillis()
        val r = rueckblickLock.withLock {
            val tag = de.troetenrepublik.app.engine.Tagesrueckblick.faellig(jetzt, rueckblickArchiv()) ?: return
            val r = rueckblickErstellen(tag, jetzt, vorlaeufig = false, s) ?: return
            rueckblickSpeichern(de.troetenrepublik.app.engine.Tagesrueckblick.eintragen(rueckblickArchiv(), r))
            r
        }
        if (!s.notifications) return
        val n = notifier ?: return
        I18n.lang = s.sprache
        n.notify(Notifier.CHANNEL_RUECKBLICK, S.rbMitteilungTitel(Fmt.dayMonth(de.troetenrepublik.app.engine.Tagesrueckblick.beginn(r.tag))), rueckblickKurz(r), ziel = "rueckblick")
        if (de.troetenrepublik.app.engine.Tagesrueckblick.wochenende(r.tag)) {
            val montag = de.troetenrepublik.app.engine.Tagesrueckblick.montag(r.tag)
            val w = de.troetenrepublik.app.engine.Tagesrueckblick.wochen(rueckblickArchiv()).firstOrNull { it.montag == montag } ?: return
            n.notify(
                Notifier.CHANNEL_RUECKBLICK,
                S.rbWochenTitel(Fmt.dayMonth(de.troetenrepublik.app.engine.Tagesrueckblick.beginn(w.montag)), Fmt.dayMonth(de.troetenrepublik.app.engine.Tagesrueckblick.beginn(w.freitag))),
                wocheKurz(w), ziel = "rueckblick",
            )
        }
    }

    // Den heutigen Rueckblick jetzt erstellen (von Hand); vor 22:10 Uhr gilt er als vorlaeufig und wird abends ersetzt.
    suspend fun rueckblickJetzt(): Boolean {
        val s = settingsFlow.value
        val jetzt = System.currentTimeMillis()
        return rueckblickLock.withLock {
            val z = java.time.Instant.ofEpochMilli(jetzt).atZone(java.time.ZoneId.systemDefault())
            val vorlaeufig = z.hour * 60 + z.minute < de.troetenrepublik.app.engine.Tagesrueckblick.AB_MINUTE
            val r = rueckblickErstellen(de.troetenrepublik.app.engine.Tagesrueckblick.tag(jetzt), jetzt, vorlaeufig, s) ?: return@withLock false
            rueckblickSpeichern(de.troetenrepublik.app.engine.Tagesrueckblick.eintragen(rueckblickArchiv(), r))
            true
        }
    }

    // Daten des Tags sammeln: Maerkte und Depot aus der Sammelabfrage, Schlagzeilen der Feeds und je bewegtem Wert.
    private suspend fun rueckblickErstellen(tag: Long, jetzt: Long, vorlaeufig: Boolean, s: Settings): de.troetenrepublik.app.engine.Tagesrueckblick.Rueckblick? {
        val tr = de.troetenrepublik.app.engine.Tagesrueckblick
        runCatching { news.laden(NewsRepository.Auftrag(newsQuellen(s), s.sprache == Lang.DE)) }
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
        } + valuation.value.positions.filter { it.position.isDerivative && it.value > 0 }.map { vp ->
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
        return tr.erstellen(tag, jetzt, markt, positionen, news.stand.value.eintraege, eigene, vorlaeufig,
            branchen = branchen, marktwerte = marktwerte, merkliste = merkliste)
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
        for (e in melden.take(3)) n.notify(Notifier.CHANNEL_NEWS, S.redditMeldungTitel(e.name.ifBlank { e.ticker }), S.redditMeldungText(e.erwaehnungen, e.erwaehnungenVorher ?: 0), null, de.troetenrepublik.app.data.WidgetZiel.instrument(e.ticker))
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
        de.troetenrepublik.app.engine.Branchentrends.BRANCHEN.map { it.symbol }.chunked(20).forEach { aus.putAll(schlusskurse(it, "1y")) }
        if (aus.isNotEmpty()) branchenCache = now to aus
        return aus.ifEmpty { branchenCache?.second.orEmpty() }
    }

    // Tagesschlusskurse ueber bis zu zehn Jahre (EUR), leer ohne Daten.
    suspend fun dailyCloses(symbol: String): List<Candle> = runCatching { market.daily(symbol) }.getOrDefault(emptyList())

    // Dividendentermine eines Werts (Ex-Tage, EUR je Stueck), fuer Kalender und Prognose.
    suspend fun dividendsFor(symbol: String): List<MarketApi.DividendEvent> {
        val now = System.currentTimeMillis()
        dividendCache[symbol]?.takeIf { now - it.first < 12 * 3_600_000L }?.let { return it.second }
        val list = runCatching { market.dividends(symbol) }.getOrDefault(emptyList())
        dividendCache[symbol] = now to list
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
        val histories: Map<String, Series> = symbols
            .map { sym -> async { sym to market.series(sym, source) } }
            .awaitAll()
            .map { (sym, series) -> sym to (if (range == Range.MAX && first > 0) series.copy(candles = series.candles.filter { it.t >= first - 86_400_000L }) else series) }
            .filter { it.second.candles.size >= 2 }
            .toMap()
        // Ohne Historie fuer einen heute gehaltenen Wert laesst sich nichts Verlaessliches zeigen
        if (acc.positions.any { it.symbol !in histories && !it.isKnockedOut }) return@coroutineScope null

        // Viele Punkte: gerechnet wird abseits des UI-Threads. Intraday zaehlt nur das Depotfenster (werktags 8 bis
        // 22 Uhr), der Tageschart wird auf fuenf Minuten ausgeduennt.
        val liveQuotes = quotes.value
        val rate = riskFree
        val raw = withContext(Dispatchers.Default) {
            val reihe = valueSeries(acc, held.values.toList(), histories, MAX_POINTS, liveQuotes, rate, from = if (range == Range.MAX) first else 0L,
                fenster = source.intraday, ohneProdukt = ohneProdukt)
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
                holdings = Trading.holdingsAt(account, t)
                if (ohneProdukt.isNotEmpty()) stueckOhneProdukt = de.troetenrepublik.app.engine.Depotzerlegung.bestand(account, t)
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
                val series = histories[p.symbol]?.candles
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
        val liveQuotes = quotes.value
        val rate = riskFree

        val ergebnis = withContext(Dispatchers.Default) {
            val zufluesse = de.troetenrepublik.app.engine.Depotzerlegung.zufluesse(acc)
            val preise = de.troetenrepublik.app.engine.Depotzerlegung.handelspreise(acc)
            val buchungen = acc.transactions.map { it.ts }.sorted()
            var naechste = 0
            var bestand: Map<String, Double> = emptyMap()
            var bestandTag = Long.MIN_VALUE
            val tage = de.troetenrepublik.app.engine.Depotzerlegung.tage(first, now, info.keys, zufluesse) { k, tag ->
                val ende = tag + de.troetenrepublik.app.engine.Depotzerlegung.TAG - 1
                if (tag != bestandTag) {
                    // Den Bestand nur neu aufbauen, wenn seit dem letzten Tag gebucht wurde
                    var gebucht = bestandTag == Long.MIN_VALUE
                    while (naechste < buchungen.size && buchungen[naechste] <= ende) { naechste++; gebucht = true }
                    if (gebucht) bestand = de.troetenrepublik.app.engine.Depotzerlegung.bestand(acc, ende)
                    bestandTag = tag
                }
                val stueck = bestand[k] ?: return@tage 0.0
                val p = info[k] ?: return@tage 0.0
                val handel = { de.troetenrepublik.app.engine.Depotzerlegung.preisBis(preise[k], ende)?.let { stueck * it } }
                if (k in geschaetzt) return@tage handel()
                val spot = priceAt(kurse[p.symbol].orEmpty(), ende)?.close?.takeIf { it > 0 } ?: return@tage handel()
                val d = p.derivative
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
        if (brokerGesperrt()) return false
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

    // ------------------------------------------------------------ Broker

    // Stand der Verbindung zum Broker fuer die Anzeige.
    data class BrokerZustand(
        val art: BrokerArt? = null,
        val live: Boolean = false,
        val konto: String = "",
        val waehrung: String = "",
        val kaufkraft: Double = 0.0,
        val zuletzt: Long = 0L,
        val fehler: String? = null,
    ) {
        val verbunden: Boolean get() = art != null
    }

    private val _broker = MutableStateFlow(BrokerZustand())
    val brokerZustand: StateFlow<BrokerZustand> = _broker.asStateFlow()
    private val _brokerOhneSchluessel = MutableStateFlow(false)

    // Freigabe einer Broker-Sitzung mit TAN. noetig: Der Broker wartet auf die Freigabe (vor dem
    // ersten Verbinden oder nach einem Neustart). angefordert und falsch zaehlen je App-Sitzung; die App bleibt mit 3
    // Anforderungen und 2 falschen TANs unter den Grenzen, ab denen comdirect das Onlinebanking sperrt (5 und 3).
    data class FreigabeZustand(
        val art: BrokerArt? = null,
        val noetig: Boolean = false,
        val challenge: TanChallenge? = null,
        val angefordert: Int = 0,
        val falsch: Int = 0,
        val laeuft: Boolean = false,
        val fehler: String? = null,
    )

    private val _freigabe = MutableStateFlow(FreigabeZustand())
    val brokerFreigabe: StateFlow<FreigabeZustand> = _freigabe.asStateFlow()

    // Broker vor dem ersten Verbinden, der noch auf die TAN-Freigabe wartet, mit Kontoart und Schluesseln.
    private var wartend: Triple<Broker, Boolean, Map<String, String>>? = null

    // Ein Broker ist eingerichtet, seine Schluessel lassen sich auf diesem Geraet aber nicht lesen, etwa
    // nachdem die Bildschirmsperre entfernt wurde. Dann hilft nur neu verbinden oder trennen.
    val brokerOhneSchluessel: StateFlow<Boolean> = _brokerOhneSchluessel.asStateFlow()

    // Verbundener Broker; null = Uebungsdepot.
    @Volatile var broker: Broker? = null
        private set

    private val brokerLock = kotlinx.coroutines.sync.Mutex()
    @Volatile private var brokerLetzterAbgleich = 0L
    @Volatile private var letzteOrder: Pair<Long, String>? = null

    private fun Exception.brokerCode(): String = (this as? BrokerFehler)?.code ?: "NETZ"

    private fun tresorName(art: BrokerArt, feld: String) = "broker_${art.name.lowercase()}_$feld"

    // Gespeicherte Schluessel eines Brokers, leer wenn keine.
    private fun brokerFelder(art: BrokerArt): Map<String, String> =
        art.gespeichert.associateWith { Tresor.lesen(tresorName(art, it)).orEmpty() }

    // Einheiten einer Waehrung je Euro, wie bei der Umrechnung der Kurse.
    private fun jeEuro(waehrung: String): Double? =
        if (waehrung == "EUR") 1.0 else de.troetenrepublik.app.engine.ExchangeRates.rate(waehrung, market.fxKurse.value)

    // Ein Preis aus der Ordermaske (Euro) in der Handelswaehrung des Werts.
    private fun brokerPreis(symbol: String, eur: Double): Double? {
        if (broker?.preisInEuro == true) return eur
        val w = quotes.value[symbol]?.nativeCurrency ?: return null
        return jeEuro(w)?.let { eur * it }
    }

    // Im echten Konto nicht moeglich (etwa Einzahlen, Sparplaene, Wetten, Import): Hinweis und true.
    private fun brokerGesperrt(): Boolean {
        if (freigabeFehlt()) return true
        if (broker == null && !brokerErwartet) return false
        _toast.value = if (broker != null) S.brokerNurUebung else if (_brokerOhneSchluessel.value) S.brokerSchluesselFehlen else S.brokerVerbindet
        return true
    }

    // Ein echtes Konto ist eingerichtet. Ist es gerade nicht verbunden (Schluessel noch nicht lesbar, kein Netz),
    // steht im Depot trotzdem das Abbild des echten Kontos: Dann darf nichts davon wie im Uebungsdepot gebucht werden.
    private val brokerErwartet: Boolean get() = settingsFlow.value.broker.isNotBlank()

    // Order im echten Konto, das gerade nicht verbunden ist: nicht moeglich.
    private fun brokerFehlt(): Boolean {
        if (freigabeFehlt()) return true
        if (broker != null || !brokerErwartet) return false
        _toast.value = if (_brokerOhneSchluessel.value) S.brokerSchluesselFehlen else S.brokerVerbindet
        scope.launch { runCatching { brokerFortsetzen() } }
        return true
    }

    // Der Broker braucht eine TAN-Freigabe, die noch fehlt: Hinweis und true.
    private fun freigabeFehlt(): Boolean {
        val b = broker ?: return false
        val f = b.freigabe ?: return false
        if (f.aktiv) return false
        _freigabe.update { it.copy(art = b.art, noetig = true) }
        _toast.value = S.frNoetig(b.art.anzeige)
        return true
    }

    // Eine TAN-Challenge anfordern, nur per Knopf; verfahren P_TAN_PUSH, P_TAN, M_TAN oder null.
    fun freigabeAnfordern(verfahren: String?) {
        val b = broker ?: wartend?.first ?: return
        val f = b.freigabe ?: return
        val z = _freigabe.value
        if (z.laeuft) return
        if (z.angefordert >= FREIGABE_ANFORDERUNGEN) { _freigabe.update { it.copy(fehler = S.frGrenze) }; return }
        _freigabe.update { it.copy(art = b.art, noetig = true, laeuft = true, fehler = null) }
        scope.launch {
            try {
                val c = f.anfordern(verfahren)
                _freigabe.update { it.copy(challenge = c, angefordert = it.angefordert + 1, laeuft = false) }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                _freigabe.update { it.copy(laeuft = false, fehler = S.brokerFehlerText(e.brokerCode(), e.message)) }
            }
        }
    }

    // Die angeforderte Challenge einloesen (tan bei photoTAN und mobileTAN), danach verbinden bzw. abgleichen.
    fun freigabeBestaetigen(tan: String?) {
        val b = broker ?: wartend?.first ?: return
        val f = b.freigabe ?: return
        val z = _freigabe.value
        val c = z.challenge ?: return
        if (z.laeuft) return
        val mitTan = c.verfahren != "P_TAN_PUSH"
        if (mitTan && z.falsch >= FREIGABE_FALSCH) { _freigabe.update { it.copy(fehler = S.frZweimalFalsch) }; return }
        _freigabe.update { it.copy(laeuft = true, fehler = null) }
        scope.launch {
            try {
                f.bestaetigen(c, tan)
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                // Bei photoTAN-Push heisst eine Ablehnung meist nur: noch nicht in der App freigegeben
                val code = e.brokerCode()
                _freigabe.update {
                    val falsch = if (mitTan && code == "ABGELEHNT") it.falsch + 1 else it.falsch
                    it.copy(laeuft = false, falsch = falsch, fehler = if (falsch >= FREIGABE_FALSCH) S.frZweimalFalsch else S.brokerFehlerText(code, e.message))
                }
                return@launch
            }
            _freigabe.value = FreigabeZustand()
            val w = wartend
            if (w != null && broker == null) {
                wartend = null
                val art = w.first.art
                brokerAbschliessen(w.first, art, w.second, w.third)?.let { meldung ->
                    _freigabe.value = FreigabeZustand(art = art, noetig = true, fehler = meldung)
                    wartend = w
                }
            } else brokerAbgleich()
        }
    }

    // Fingerabdruck des Zertifikats, das das IBKR-Gateway unter adresse zeigt (zum Bestaetigen); null ohne Verbindung.
    suspend fun gatewayZertifikat(adresse: String): String? =
        de.troetenrepublik.app.market.broker.IbkrBroker.adresse(adresse)?.let { de.troetenrepublik.app.plattform.BrokerNetz.zertifikat(it) }

    // Verbinden abbrechen, solange die Freigabe noch fehlt: Das Uebungsdepot bleibt, nichts wird gespeichert.
    fun freigabeAbbrechen() {
        val w = wartend ?: return
        wartend = null
        _freigabe.value = FreigabeZustand()
        scope.launch { runCatching { w.first.abmelden() } }
    }

    // Beim Start eine gespeicherte Verbindung wieder aufnehmen.
    private suspend fun brokerFortsetzen() {
        if (broker != null || store.gesperrt.value) return
        val s = settings.flow.first()
        val art = BrokerArt.entries.firstOrNull { it.name == s.broker } ?: return
        val felder = if (Tresor.verfuegbar) brokerFelder(art) else emptyMap()
        // Nichts loeschen: Bei gesperrtem Geraet sind die Schluessel nur voruebergehend nicht lesbar
        _brokerOhneSchluessel.value = felder.isEmpty() || felder.values.any { it.isBlank() }
        if (_brokerOhneSchluessel.value) return
        val b = art.erstellen(MarketApi(), felder, s.brokerLive, ::isinFor)
        broker = b
        market.kursquelle = b
        Netzschutz.brokerVerbunden = true
        _broker.value = BrokerZustand(art, s.brokerLive)
        syncTracked()
        // Mit TAN-Freigabe (comdirect): nicht von selbst anmelden, erst wenn der Nutzer die TAN anfordert
        if (b.freigabe != null) { _freigabe.update { it.copy(art = art, noetig = true) }; return }
        brokerAbgleich()
    }

    // Mit einem Broker verbinden: Schluessel pruefen, Konto laden und das Uebungsdepot vollstaendig ersetzen (nur
    // Watchlist und Alarme bleiben). Liefert eine Fehlermeldung oder null.
    suspend fun brokerVerbinden(art: BrokerArt, live: Boolean, felder: Map<String, String>): String? {
        if (!Tresor.verfuegbar) return S.brokerKeinTresor
        val werte = felder.mapValues { it.value.trim() }
        if (art.gespeichert.any { werte[it].isNullOrBlank() }) return S.brokerFelderFehlen
        val b = art.erstellen(MarketApi(), werte, live, ::isinFor)
        // Mit TAN-Freigabe (comdirect): erst die Freigabe, dann wie sonst weiter (freigabeBestaetigen)
        if (b.freigabe != null) {
            wartend = Triple(b, live, werte)
            _freigabe.value = FreigabeZustand(art = art, noetig = true)
            return null
        }
        return brokerAbschliessen(b, art, live, werte)
    }

    // Konto laden, das Uebungsdepot ersetzen und die Schluessel ablegen. Liefert eine Fehlermeldung oder null.
    private suspend fun brokerAbschliessen(b: Broker, art: BrokerArt, live: Boolean, werte: Map<String, String>): String? {
        val stand = try { b.stand() } catch (e: CancellationException) { throw e } catch (e: Exception) { return S.brokerFehlerText(e.brokerCode(), e.message) }
        runCatching { market.dollarKurs() }
        val jetzt = System.currentTimeMillis()
        val neu = BrokerAbbild.ersetzen(stand, account.value, ::jeEuro, jetzt) ?: return S.brokerKeinKurs(stand.waehrung)
        werte.forEach { (k, v) -> Tresor.schreiben(tresorName(art, k), v) }
        settings.update { it.copy(broker = art.name, brokerLive = live, brokerZustimmung = jetzt) }
        broker = b
        market.kursquelle = b
        Netzschutz.brokerVerbunden = true
        store.replace(neu)
        brokerLetzterAbgleich = jetzt
        _broker.value = BrokerZustand(art, live, stand.konto, stand.waehrung, stand.kaufkraft, jetzt)
        syncTracked()
        _brokerOhneSchluessel.value = false
        _toast.value = S.brokerVerbundenToast(art.anzeige)
        return null
    }

    // Verbindung trennen: Schluessel loeschen und mit einem frischen Uebungsdepot weitermachen (Watchlist und Alarme
    // bleiben). Ein vorher gesichertes Uebungsdepot laesst sich danach wieder importieren.
    fun brokerTrennen() {
        val art = broker?.art ?: BrokerArt.entries.firstOrNull { it.name == settingsFlow.value.broker }
        // Sitzung beim Broker beenden (comdirect: Token widerrufen)
        (broker ?: wartend?.first)?.let { alt -> scope.launch { runCatching { alt.abmelden() } } }
        wartend = null
        _freigabe.value = FreigabeZustand()
        broker = null
        market.kursquelle = null
        Netzschutz.brokerVerbunden = false
        art?.gespeichert?.forEach { Tresor.schreiben(tresorName(art, it), null) }
        val alt = account.value
        store.replace(Trading.fresh().copy(watchlist = alt.watchlist, watchMeta = alt.watchMeta, alerts = alt.alerts))
        _broker.value = BrokerZustand()
        _brokerOhneSchluessel.value = false
        scope.launch { settings.update { it.copy(broker = "", brokerLive = false) } }
        syncTracked()
        _toast.value = S.brokerGetrennt
    }

    // Konto beim Broker abfragen und ins Depot uebernehmen.
    suspend fun brokerAbgleich() {
        val b = broker ?: return
        if (store.gesperrt.value) return
        // Ohne Freigabe nichts abfragen (und nie von selbst eine TAN anfordern)
        if (b.freigabe?.aktiv == false) { _freigabe.update { it.copy(art = b.art, noetig = true) }; return }
        brokerLock.withLock {
            val stand = try { b.stand() } catch (e: CancellationException) { throw e } catch (e: Exception) {
                if (e.brokerCode() == "FREIGABE") _freigabe.update { it.copy(art = b.art, noetig = true) }
                _broker.update { it.copy(fehler = S.brokerFehlerText(e.brokerCode(), e.message)) }
                return
            }
            if (broker !== b) return
            if (jeEuro(stand.waehrung) == null) runCatching { market.dollarKurs() }
            val jetzt = System.currentTimeMillis()
            store.update { alt -> BrokerAbbild.konto(stand, alt, ::jeEuro, jetzt) ?: alt }
            brokerLetzterAbgleich = jetzt
            _broker.update { it.copy(konto = stand.konto, waehrung = stand.waehrung, kaufkraft = stand.kaufkraft, zuletzt = jetzt, fehler = null) }
        }
    }

    // Taktgeber im echten Konto: alle 15 Sekunden abgleichen; Alarme, Watchlist-Merkwerte und Verlauf wie sonst.
    private suspend fun brokerTick() {
        val now = System.currentTimeMillis()
        if (now - brokerLetzterAbgleich > 15_000L) brokerAbgleich()
        val q = quotes.value
        if (q.isEmpty()) return
        val before = account.value
        store.update { acc ->
            var a = Trading.checkAlerts(acc, q, now).first
            a = Trading.backfillWatchMeta(a, q, now)
            if (a.positions.all { p -> Trading.isQuoteFresh(q[p.symbol], now) }) a = Trading.snapshot(a, Trading.value(a, q, now, riskFree).total, now)
            a
        }
        announce(before, account.value)
    }

    // Order an den Broker; die Antwort kommt als Hinweis, das Depot beim naechsten Abgleich.
    private fun brokerAuftrag(b: Broker, auftrag: Auftrag, derivative: Derivative?): Boolean {
        if (derivative != null) { _toast.value = S.brokerKeineDerivate; return false }
        if (!b.handelbar(auftrag.symbol)) { _toast.value = S.brokerFuehrtNicht(b.art.anzeige); return false }
        if (auftrag.typ !in b.ordertypen(auftrag.symbol)) { _toast.value = S.brokerOrdertypFehlt; return false }
        if (auftrag.menge % 1.0 != 0.0 && !b.bruchstuecke(auftrag.symbol)) { _toast.value = S.brokerKeineBruchstuecke; return false }
        // Dieselbe Order binnen 20 Sekunden ein zweites Mal (Doppeltipp, Wiederholen ohne Bestaetigung): nicht senden
        val jetzt = System.currentTimeMillis()
        val gleich = "${auftrag.symbol}|${auftrag.seite}|${auftrag.typ}|${auftrag.menge}|${auftrag.preis}|${auftrag.trailPct}"
        letzteOrder?.let { (t, g) -> if (g == gleich && jetzt - t < 20_000L) { _toast.value = S.brokerFehlerText("DOPPELT", null); return false } }
        letzteOrder = jetzt to gleich
        scope.launch {
            _toast.value = try {
                b.order(auftrag)
                S.brokerGesendet + b.letzteHinweise().joinToString("") { " $it" }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                S.brokerFehlerText(e.brokerCode(), e.message)
            }
            delay(1_500)
            brokerAbgleich()
        }
        return true
    }

    private companion object {
        // Kurshistorien aendern sich traege; der Live-Punkt wird ohnehin separat angehaengt.
        const val CACHE_TTL_MS = 60_000L

        // Hoechstens so viele TAN-Anforderungen und falsche TANs je App-Sitzung (comdirect sperrt ab 5 bzw. 3).
        const val FREIGABE_ANFORDERUNGEN = 3

        // Schluessel des Depotverlaufs unter den Tagesverlaeufen der Widgets.
        const val WIDGET_DEPOT = "@depot"
        const val WIDGET_DEPOT_JAHR = "@depot-jahr"
        const val WIDGET_DEPOT_KAUF = "@depot-kauf"

        // So lange darf die App im Hintergrund sein, bevor die App-Sperre wieder greift.
        const val SPERRE_NACH_MS = 60_000L
        const val FREIGABE_FALSCH = 2

        // Obergrenze der Punkte einer Depotkurve. Beim Ziehen mit dem Finger ist jeder Punkt eine Station;
        // Wochen- und Monatschart behalten so jeden Kurs der Quelle.
        const val MAX_POINTS = 1500

        // Raster des Tagescharts der Depotkurve: ein Punkt je fuenf Minuten.
        const val TAGES_RASTER_MS = 5 * 60_000L

        // Stammdaten, die vor diesem Zeitpunkt geladen wurden, kennen die Adresse ihres Wikipedia-Artikels nicht.
        val ARTIKEL_SEIT: Long = java.time.LocalDate.of(2026, 10, 7).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    }
}

// Quellen der Heatmap ausser den Index-Schluesseln des Katalogs.
object HeatmapQuelle {
    // Der ganze Aktienmarkt: die groessten Aktien des Katalogs weltweit, Standard der Heatmap.
    const val MARKT = "MARKT"
    const val DEPOT = "DEPOT"
    const val WATCHLIST = "WATCHLIST"
}
