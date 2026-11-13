package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import de.troetenrepublik.app.engine.ChartSprung
import de.troetenrepublik.app.engine.Indikatoren
import de.troetenrepublik.app.engine.LivePunkt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.EditNote as EditNoteLeer
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.layout
import androidx.compose.runtime.LaunchedEffect
import de.troetenrepublik.app.market.StandRegel
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.risikoWarnungen
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.data.Range
import de.troetenrepublik.app.data.Series
import de.troetenrepublik.app.data.Side
import de.troetenrepublik.app.engine.Derivatives
import de.troetenrepublik.app.engine.EigeneMarken
import de.troetenrepublik.app.engine.ExchangeRates
import de.troetenrepublik.app.engine.MarketHours
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.NachrichtenApis
import de.troetenrepublik.app.market.Symbols
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

// Handelsstatus fuer die Zeile unter dem Kurs. Laeuft der Handel, steht dort nichts (an einem verkuerzten Handelstag der
// vorzeitige Schluss); bei geschlossener Boerse steht, wann sie wieder oeffnet.
fun hoursLabel(status: MarketHours.Status): String {
    val w = status.window ?: return ""
    fun t(lt: java.time.LocalTime) = "${lt.hour}:${"%02d".format(lt.minute)}"
    if (status.open) return status.kurzSchluss?.let { S.kurzerHandelstag(t(it)) } ?: ""
    return if (status.opensToday) S.closedOpensToday(t(w.open))
    else S.closedOpensOn(status.nextOpenWeekday?.let { S.weekday(it) } ?: "", t(w.open))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstrumentScreen(
    state: AppState,
    symbol: String,
    onBack: () -> Unit,
    onOpenDerivative: (String) -> Unit,
    onOpenRoute: (String) -> Unit = {},
) {
    val quotes by state.quotes.collectAsStateWithLifecycle()
    // Kurs des letzten Starts, nur zur Anzeige bis der frische Kurs da ist; Orders und Kaeufe nehmen nur quote
    val altKurse by state.market.altKurse.collectAsStateWithLifecycle()
    val account by state.account.collectAsStateWithLifecycle()
    val valuation by state.valuation.collectAsStateWithLifecycle()
    val companies by state.companies.collectAsStateWithLifecycle()
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val signal = LocalSignal.current
    val clipboard = LocalClipboardManager.current

    val instrument = Symbols.find(symbol)
    val type = Symbols.typeOf(symbol)
    val quote = quotes[symbol]
    val altKurs = if (quote == null) altKurse[symbol] else null
    val kursQuote = quote ?: altKurs?.quote
    // Kurze Namen aus bekannten Werten und Katalog vor dem langen Yahoo-Namen („Gold Dec 26“)
    // [0.78] Firmen der Laenderliste tragen ihren Namen von dort, auch ohne Antwort der Kursquelle
    val name = instrument?.name ?: remember(symbol) { state.firmenListeName(symbol) } ?: kursQuote?.name?.ifBlank { null } ?: symbol
    // Nur der frische Kurs: Kaufen, Verkaufen, Brief und Geld rechnen damit, nie mit dem gespeicherten
    val price = quote?.price ?: 0.0
    val anzeigePreis = if (price > 0) price else altKurs?.quote?.price ?: 0.0
    val info = companies[symbol]
    // Fremde Betraege per Tippen in Original, Dollar und Euro
    val fremd = rememberGeldanzeige(state, symbol)
    // Kurs, Kennzahlen und Position per Tippen auf den Kurs auch in der Heimatwaehrung
    val fxKurse by state.market.fxKurse.collectAsStateWithLifecycle()
    val heimatWaehrung = Heimatwaehrung.waehrung(kursQuote?.nativeCurrency, fxKurse)
    var inHeimat by rememberSaveable(symbol) { mutableStateOf(false) }
    val kf = KursFormat(heimatWaehrung?.takeIf { inHeimat }, heimatWaehrung?.let { ExchangeRates.rate(it, fxKurse) })
    // Kursziel zuerst in der Anzeige der Seite (Euro, Dollar oder Heimatwaehrung), per Tippen auch in den anderen
    val ziel = rememberGeldanzeige(state, "$symbol#ziel", start = if (kf.inHeimat) 0 else if (Fmt.inUsd) 1 else 2)
    val kursTippen: () -> Unit = {
        val (stufe, heimat) = Heimatwaehrung.naechste(Fmt.stufe, Fmt.stufen.size, heimatWaehrung != null, kf.inHeimat)
        inHeimat = heimat
        if (!heimat && stufe != Fmt.stufe) {
            if (stufe == (Fmt.stufe + 1) % Fmt.stufen.size) state.waehrungUmschalten() else state.waehrungStufe(stufe)
        }
    }

    var range by rememberSaveable { mutableStateOf(Range.D1) }
    // [0.78] Kein laufender Kurs (ausgesetzt, nicht mehr gehandelt, Quelle liefert nur den alten Stand): Die Seite zeigt den
    // letzten Stand mit Datum, der Chart endet dort, Kaufen und Sparplan sind gesperrt
    val kursZeit = kursQuote?.marketTime ?: 0L
    val veraltet = de.troetenrepublik.app.market.Kursarchiv.veraltet(kursZeit, System.currentTimeMillis())
    var rangeGewaehlt by rememberSaveable { mutableStateOf(false) }
    // Ohne laufenden Kurs sagt der Tageschart nichts: einmal auf ein Jahr stellen, solange nichts gewaehlt ist
    // Kurs der zweiten Kursquelle: Schlusskurs des letzten Handelstags, ohne Kerzen innerhalb des Tages
    val eigeneDatei = kursQuote?.exchange == de.troetenrepublik.app.market.EigeneKurse.NAME
    val kursAnbieter = kursQuote?.exchange?.takeIf {
        it == de.troetenrepublik.app.market.MassiveKurse.NAME || it == de.troetenrepublik.app.market.TiingoKurse.NAME || it == de.troetenrepublik.app.market.TwelveDataKurse.NAME
    }
    val nurSchluss = kursAnbieter != null || eigeneDatei
    LaunchedEffect(veraltet, nurSchluss) { if ((veraltet || nurSchluss) && !rangeGewaehlt && range == Range.D1) range = Range.Y1 }
    // Eigener Zeitraum: ganze Tage seit 1970, 0 = es gilt ein fester Zeitraum
    var eigenVon by rememberSaveable { mutableLongStateOf(0L) }
    var eigenBis by rememberSaveable { mutableLongStateOf(0L) }
    var zeitraumWahl by remember { mutableStateOf(false) }
    val eigen = eigenVon > 0
    var series by remember(symbol) { mutableStateOf(Series(emptyList())) }
    var scrub by remember { mutableStateOf<Candle?>(null) }
    var vol by remember(symbol) { mutableStateOf(Derivatives.defaultVol(type)) }
    var sheet by remember { mutableStateOf<Side?>(null) }
    val seitenQuellen = remember(symbol) { SeitenQuellen() }
    var profile by remember(symbol) { mutableStateOf<MarketApi.Profile?>(null) }
    var news by remember(symbol) { mutableStateOf<List<MarketApi.NewsItem>>(emptyList()) }
    var openNews by remember { mutableStateOf<MarketApi.NewsItem?>(null) }
    // Nachrichten ueber EODHD (5.95) mit eigenem Schluessel
    var eodhdArtikel by remember(symbol) { mutableStateOf<MarketApi.NewsItem?>(null) }
    var eodhdText by remember(symbol) { mutableStateOf<List<String>>(emptyList()) }
    var showAll by remember(symbol) { mutableStateOf(false) }
    // Stammdaten angefragt: vorher steht bei Aktien keine Ersatzbeschreibung, sonst wechselt der Text nach einer Sekunde
    var stammdatenFertig by remember(symbol) { mutableStateOf(false) }
    var tool by remember(symbol) { mutableStateOf<StockTool?>(null) }
    var planSheet by remember { mutableStateOf(false) }
    var alertSheet by remember { mutableStateOf(false) }
    var notizSheet by remember { mutableStateOf(false) }
    var eventsSheet by remember { mutableStateOf(false) }
    // Depot-Werkzeug „Was waere, wenn“ aus der Karte „Abhaengigkeiten und Risiken“
    var szenarienBlatt by remember { mutableStateOf(false) }
    var financialsSheet by remember { mutableStateOf(false) }
    // Strategie-Labor (seit 5.91)
    var labor by remember(symbol) { mutableStateOf(false) }
    // Intraday-Rueckblick (0.78), nur fuer Aktien und ETFs mit bekanntem Hauptplatz
    var intraday by remember(symbol) { mutableStateOf(false) }
    val intradayMoeglich = remember(symbol, type) { type.directlyTradable && type != de.troetenrepublik.app.data.AssetType.CRYPTO && de.troetenrepublik.app.engine.Intraday.platz(symbol) != null }
    var absoluteChange by rememberSaveable { mutableStateOf(false) }
    // Chart-Indikatoren wie in 5.9: alle zehn mit Lesehilfe, je einzeln einschaltbar, Tageskurse ueber zehn Jahre als
    // Grundlage (5.92)
    val indikatoren = setOfNotNull(
        "sma50".takeIf { settings.indSma50 }, "sma200".takeIf { settings.indSma200 }, "bb".takeIf { settings.indBollinger },
        "rsi".takeIf { settings.indRsi }, "macd".takeIf { settings.indMacd }, "stoch".takeIf { settings.indStoch },
        "atr".takeIf { settings.indAtr }, "obv".takeIf { settings.indObv }, "ichimoku".takeIf { settings.indIchimoku },
        "sar".takeIf { settings.indSar }, "kerzen".takeIf { settings.indKerzen }, "kreuz".takeIf { settings.indKreuz },
    )
    // Eingeklappt: erst ein Tipp auf „Indikatoren“ zeigt Namen, Linien und Deutung
    var indOffen by rememberSaveable { mutableStateOf(false) }
    val indikatorScope = rememberCoroutineScope()
    fun indikatorUmschalten(k: String) {
        indikatorScope.launch {
            state.settings.update { indikatorSchalten(it, k) }
        }
    }
    var tagesreihe by remember(symbol) { mutableStateOf<List<Candle>?>(null) }
    val indGezeigt = indOffen && indikatoren.isNotEmpty()
    // Die Gesamtrichtung steht auch ohne eingeschaltete Indikatoren da, die Tagesreihe wird dafuer beim Aufklappen geladen
    LaunchedEffect(symbol, indOffen) { if (indOffen && tagesreihe == null) tagesreihe = state.dailyCloses(symbol) }
    // Trefferbilanz je Indikator ueber die ganze Kursgeschichte (seit Beginn der Notierung), im Hintergrund gerechnet und
    // je Wert zwischengespeichert; ohne lange Reihe die zehn Jahre der Anzeige
    var bilanzen by remember(symbol) { mutableStateOf(TrefferSpeicher.lesen(symbol)) }
    LaunchedEffect(symbol, indGezeigt) {
        if (!indGezeigt || bilanzen != null) return@LaunchedEffect
        val r = state.gesamtHistorie(symbol).takeIf { it.size > 250 } ?: tagesreihe ?: state.dailyCloses(symbol)
        if (r.isNotEmpty()) bilanzen = withContext(Dispatchers.Default) { TrefferSpeicher.rechnen(symbol, r) }
    }
    val marketOpen = state.marketOpen(symbol, null)
    val hoursText = hoursLabel(MarketHours.status(type, false))

    LaunchedEffect(symbol) { state.market.track(listOf(symbol)); state.market.refreshOne(symbol) }
    // Chart: zuerst der gespeicherte Stand (mit Zeitangabe, wenn er aelter als die Frist ist), dann der Abruf. Waehrend
    // des Ladens steht nur ein Platzhalter da; „Kein Verlauf“ erst nach einem Abruf ohne Daten.
    var serieLaedt by remember(symbol) { mutableStateOf(true) }
    var serieStand by remember(symbol) { mutableStateOf<Long?>(null) }
    // Eigene Kursdatei eingelesen: Kurs und Chart neu laden (ui/KursDateiImport.kt)
    var kursdateiStand by remember(symbol) { mutableStateOf(0) }
    val kursdateiEinlesen = rememberKursdateiEinlesen(state, symbol) { kursdateiStand++ }
    LaunchedEffect(kursdateiStand) { if (kursdateiStand > 0) state.market.refreshOne(symbol) }
    LaunchedEffect(symbol, range, eigenVon, eigenBis, veraltet, kursdateiStand) {
        scrub = null; serieLaedt = true; serieStand = null; series = Series(emptyList())
        // Ohne laufenden Kurs reicht der Zeitraum bis zum letzten Stand zurueck statt bis heute
        val fenster = if (veraltet && eigenVon <= 0) de.troetenrepublik.app.market.Kursarchiv.fensterTage(range.yahooRange, kursZeit) else null
        val gespeichert = if (eigenVon > 0 || fenster != null) null else state.market.serieGespeichert(symbol, range)
        StandRegel.laden(gespeichert, { System.currentTimeMillis() }, {
            (if (eigenVon > 0) state.zeitraum(symbol, eigenVon, eigenBis) else if (fenster != null) state.zeitraum(symbol, fenster.first, fenster.second) else state.series(symbol, range)).takeIf { it.candles.isNotEmpty() }
        }) { a ->
            series = a.wert ?: Series(emptyList()); serieStand = if (a.alt) a.abgerufen else null; serieLaedt = a.laedt
        }
    }
    // Tages- und Wochenchart laufen mit: im Vordergrund alle 60 s (1T) bzw. 5 min neu abrufen, solange der Wert
    // gehandelt wird; dazwischen fuehrt der Kurs oben die letzte Kerze fort (LivePunkt)
    val reiheLaeuft by rememberUpdatedState(marketOpen || System.currentTimeMillis() - (quote?.marketTime ?: 0L) < 20 * 60_000L)
    LaunchedEffect(symbol, range, eigenVon) {
        if (eigenVon > 0 || !range.intraday) return@LaunchedEffect
        while (true) {
            delay(if (range == Range.D1) 60_000L else 5 * 60_000L)
            if (!reiheLaeuft || !state.imVordergrund) continue
            runCatching { state.series(symbol, range) }.getOrNull()?.takeIf { it.candles.isNotEmpty() }?.let { series = it; serieStand = null }
        }
    }
    LaunchedEffect(symbol) { vol = state.volatilityFor(symbol) }
    // Schwankung bei den Kennzahlen nur aus gemessenen Kursen, ohne genug Kurse ein Strich; vol mit Ersatzwert
    // bleibt fuer die Preise der Derivate
    var schwankungGemessen by remember(symbol) { mutableStateOf<Double?>(null) }
    LaunchedEffect(symbol) { schwankungGemessen = runCatching { state.historischeVol(symbol) }.getOrNull() }
    // Kennzahlen und Nachrichten: der gespeicherte Stand steht sofort da; kommt keine neue Antwort, bleibt er mit Zeitangabe
    var profilStand by remember(symbol) { mutableStateOf<Long?>(null) }
    var newsStand by remember(symbol) { mutableStateOf<Long?>(null) }
    LaunchedEffect(symbol) {
        StandRegel.laden(state.profileGespeichert(symbol), { System.currentTimeMillis() }, { state.profileFor(symbol) }) { a ->
            a.wert?.let { profile = it }; profilStand = if (a.alt && !a.laedt) a.abgerufen else null
        }
    }
    // Google sucht nur mit einem echten Namen, nicht mit dem Kuerzel
    val googleName = instrument?.name ?: quote?.name?.ifBlank { null }.orEmpty()
    LaunchedEffect(symbol, settings.sprache, googleName) {
        StandRegel.laden(state.newsGespeichert(symbol, googleName), { System.currentTimeMillis() }, { state.newsFor(symbol, googleName).takeIf { it.isNotEmpty() } }) { a ->
            a.wert?.let { news = it }; newsStand = if (a.alt && !a.laedt) a.abgerufen else null
        }
    }
    LaunchedEffect(symbol) { state.companyInfoFor(symbol, name); stammdatenFertig = true }

    val candles = remember(series, quote, range, eigen, veraltet) { if (range == Range.D1 && !eigen && !veraltet) LivePunkt.anhaengen(series, quote, System.currentTimeMillis()) else series.candles }
    val now = System.currentTimeMillis()
    val domain = if (eigen || veraltet) null else dayAxis(range, series.dayStart, series.dayEnd, candles)
    val position = valuation.positions.firstOrNull { it.position.key == symbol }
    val reference = if (range == Range.D1 && !eigen && !veraltet) (kursQuote?.prevClose ?: 0.0) else (candles.firstOrNull()?.close ?: 0.0)
    val beschreibung = state.descriptionFor(symbol, profile)
    // Der englische Profiltext erscheint erst, wenn feststeht, dass Wikipedia nichts liefert
    val description = if (beschreibung.wikipedia || info != null || stammdatenFertig || type != de.troetenrepublik.app.data.AssetType.STOCK) beschreibung.text else ""
    val fromWiki = beschreibung.wikipedia
    val isin = state.isinFor(symbol)

    // Eigene Kaeufe, Verkaeufe und Dividenden als Marken, Orders, Alarme und KO-Schwellen als dezente Linien (seit
    // 5.91); der Einstand steht bei der Position
    val markenAn by state.chartEigeneMarken.collectAsStateWithLifecycle()
    val tagVon = remember { { t: Long -> java.time.Instant.ofEpochMilli(t).atZone(MarketHours.zone).toLocalDate().toEpochDay() } }
    val eigene = remember(account, symbol, fxKurse) { EigeneMarken.fuer(account, symbol, fxKurse, tagVon) }

    // Kennzahlen (5.91): sechs Kernwerte auf der Seite, alle im Blatt. Beta steht nur unter „Rendite und Risiko“.
    val aktie = type == de.troetenrepublik.app.data.AssetType.STOCK
    val kennzahlen = run {
        val (geld, brief) = state.bidAsk(symbol)
        val q = quote
        val p = profile
        val currency = p?.financialCurrency?.ifBlank { q?.nativeCurrency.orEmpty() }.orEmpty()
        // Unternehmenswert und Gewinn je Aktie in der Waehrung der Seite wie Kurs und Marktkapitalisierung
        val zielWaehrung = if (kf.inHeimat) heimatWaehrung.orEmpty() else Fmt.anzeigeWaehrung
        fun inSeite(wert: Double): Pair<Double, String> {
            if (currency.isBlank() || currency == zielWaehrung) return wert to currency.ifBlank { zielWaehrung }
            val von = ExchangeRates.rate(currency, fxKurse) ?: return wert to currency
            val nach = ExchangeRates.rate(zielWaehrung, fxKurse) ?: return wert to currency
            return wert / von * nach to zielWaehrung
        }
        val ev = p?.takeIf { it.enterpriseValue > 0 }?.let { inSeite(it.enterpriseValue) }
            ?.let { (v, w) -> Fmt.mitSymbol(Fmt.compact(v), Derivatives.currencySymbol(w)) }
        val kgv = de.troetenrepublik.app.engine.Bewertungsblick.kgv(p?.trailingPe, q?.peRatio)?.let { "${Fmt.num(it)}×" }
        val gewinn = p?.takeIf { it.eps != 0.0 }?.let { inSeite(it.eps) }?.let { (v, w) -> Fmt.price(v, Derivatives.currencySymbol(w)) }
        val dividende = p?.let { if (it.dividendYield > 0) Fmt.pct(it.dividendYield) else "–" }
        val schwankung = if (q != null) schwankungGemessen?.let { Fmt.pct(it * 100) } ?: "–" else null
        val hoch = q?.takeIf { it.weekHigh52 > 0 }?.let { kf.preis(it.weekHigh52) }
        val tief = q?.takeIf { it.weekLow52 > 0 }?.let { kf.preis(it.weekLow52) }
        val boersenwert = q?.takeIf { it.marketCap > 0 }?.let { kf.kompakt(it.marketCap) }
        val umsatz = q?.takeIf { it.volume > 0 }?.let { Fmt.compact(it.volume) }
        val vortag = q?.takeIf { it.prevClose > 0 }?.let { kf.preis(it.prevClose) }
        val ter = if (type == de.troetenrepublik.app.data.AssetType.ETF) de.troetenrepublik.app.market.Catalog.loaded()?.etfs?.firstOrNull { it.s == symbol }?.ter else null
        // Erwartetes KGV und ISIN stehen vorn bei den Kernwerten (0.77)
        val kgvErwartet = p?.let { if (it.forwardPe > 0) "${Fmt.num(it.forwardPe)}×" else if (it.forwardPe < 0) S.kgvVerlust else null }
        val isinZeile = if (isin.isNotBlank()) listOf(S.isin to isin) else emptyList()
        // Kernwerte je Art: Aktien immer gleich aufgebaut (fehlende als Strich), sonst nur vorhandene
        val kern = when (type) {
            de.troetenrepublik.app.data.AssetType.STOCK -> listOf(
                S.peRatio to (kgv ?: "–"), S.forwardPe to (kgvErwartet ?: "–"), S.dividendYield to (dividende ?: "–"),
                S.marketCap to (boersenwert ?: "–"), S.high52 to (hoch ?: "–"), S.low52 to (tief ?: "–"),
                S.volatility1y to (schwankung ?: "–"),
            ) + isinZeile
            de.troetenrepublik.app.data.AssetType.ETF -> listOfNotNull(
                ter?.let { S.bwTer to Fmt.pct(it, 2) }, dividende?.let { S.dividendYield to it }, boersenwert?.let { S.marketCap to it },
                hoch?.let { S.high52 to it }, tief?.let { S.low52 to it }, schwankung?.let { S.volatility1y to it }, umsatz?.let { S.volume to it },
            ).take(if (isinZeile.isEmpty()) 6 else 5) + isinZeile
            de.troetenrepublik.app.data.AssetType.CRYPTO -> listOfNotNull(
                boersenwert?.let { S.marketCap to it }, umsatz?.let { S.volume to it }, hoch?.let { S.high52 to it },
                tief?.let { S.low52 to it }, schwankung?.let { S.volatility1y to it }, vortag?.let { S.prevClose to it },
            )
            else -> listOfNotNull(
                vortag?.let { S.prevClose to it }, q?.takeIf { it.dayHigh > 0 }?.let { S.dayHigh to kf.preis(it.dayHigh) },
                q?.takeIf { it.dayLow > 0 }?.let { S.dayLow to kf.preis(it.dayLow) }, hoch?.let { S.high52 to it },
                tief?.let { S.low52 to it }, schwankung?.let { S.volatility1y to it },
            )
        }
        // Alle Kennzahlen: bei Aktien zuerst die des Unternehmens, fehlende als Strich. KGV, erwartetes KGV und Gewinn
        // je Aktie stehen in der Gruppe „Bewertung“, die Dividendenrendite unter „Dividende“.
        val vorn = if (aktie) {
            listOf(S.marketCap to (boersenwert ?: "–"), S.enterpriseValue to (ev ?: "–"), S.volume to (umsatz ?: "–"))
        } else emptyList()
        val einsortiert = if (aktie) {
            listOf(S.peRatio to (kgv ?: "–"), S.forwardPe to (kgvErwartet ?: "–"), S.eps to (gewinn ?: "–")) to
                listOf(S.dividendYield to (dividende ?: "–"))
        } else null
        val alle = vorn + listOf(
            ter?.let { S.bwTer to Fmt.pct(it, 2) },
            if (price > 0) S.bid to kf.preis(geld) else null,
            if (price > 0) S.ask to kf.preis(brief) else null,
            if (price > 0) S.spread to Fmt.pct(spreadPct(geld, brief)) else null,
            q?.takeIf { it.open > 0 }?.let { S.openPrice to kf.preis(it.open) },
            q?.takeIf { it.dayHigh > 0 }?.let { S.dayHigh to kf.preis(it.dayHigh) },
            q?.takeIf { it.dayLow > 0 }?.let { S.dayLow to kf.preis(it.dayLow) },
            vortag?.let { S.prevClose to it },
            hoch?.let { S.high52 to it },
            (tief ?: if (aktie) "–" else null)?.let { S.low52 to it },
            boersenwert?.takeIf { !aktie }?.let { S.marketCap to it },
            ev?.takeIf { !aktie }?.let { S.enterpriseValue to it },
            kgv?.takeIf { !aktie }?.let { S.peRatio to it },
            kgvErwartet?.takeIf { !aktie }?.let { S.forwardPe to it },
            gewinn?.takeIf { !aktie }?.let { S.eps to it },
            dividende?.takeIf { !aktie }?.let { S.dividendYield to it },
            umsatz?.takeIf { !aktie }?.let { S.volume to it },
            q?.takeIf { it.nativeCurrency != "EUR" }?.let { S.priceIn(it.nativeCurrency) to fremd.preis(it.nativePrice, it.nativeCurrency) },
            schwankung?.let { S.volatility1y to it },
            if (isin.isNotBlank()) S.isin to isin else null,
            S.kind to typeLabel(type),
            p?.takeIf { it.sector.isNotBlank() }?.let { S.sector to S.sectorName(it.sector) }
                ?: instrument?.takeIf { it.sector.isNotBlank() && !aktie }?.let { S.sector to S.sectorName(it.sector) },
            p?.takeIf { it.industry.isNotBlank() }?.let { S.industry to S.sectorName(it.industry) }
                ?: instrument?.takeIf { it.sector.isNotBlank() && aktie && p?.industry.isNullOrBlank() }?.let { S.industry to S.sectorName(it.sector) },
            p?.takeIf { it.country.isNotBlank() }?.let { S.country to S.countryName(it.country) },
            p?.takeIf { it.employees > 0 }?.let { S.employees to Fmt.num(it.employees.toDouble(), 0) },
            q?.takeIf { it.exchange.isNotBlank() }?.let { S.exchange to (if (it.exchange == de.troetenrepublik.app.market.EigeneKurse.NAME) de.troetenrepublik.app.i18n.KursDateiTexte.t("quelle") else it.exchange) },
        ).filterNotNull()
        Triple(kern, alle, einsortiert)
    }
    // Ein Tipp erklaert die Kennzahl; der Kurs in Fremdwaehrung wechselt direkt die Waehrung, die ISIN laesst sich aus
    // dem Erklaerfenster kopieren
    val kennzahlTipp: (String) -> Unit = { label -> quote?.let { if (label == S.priceIn(it.nativeCurrency)) fremd.weiter(it.nativeCurrency) } }
    val kennzahlAktion: (String) -> Pair<String, () -> Unit>? = { label ->
        if (label == S.isin && isin.isNotBlank()) S.isinKopieren to {
            clipboard.setText(AnnotatedString(isin))
            state.showToast(S.isinCopied)
        } else null
    }
    var alleKennzahlen by remember { mutableStateOf(false) }
    // Betraege aus der Berichtswaehrung in der Waehrung, in der die Seite gerade Kurse zeigt (5.92)
    val seite = SeitenWaehrung(if (kf.inHeimat) heimatWaehrung.orEmpty() else Fmt.anzeigeWaehrung, fxKurse)

    // Hintergrund (5.91): Register-, Behoerden- und Marktdaten in einem eingeklappten Abschnitt. Die Karten laden erst
    // beim Aufklappen; der Kopf nennt die Themen, die fuer diesen Wert in Frage kommen.
    val usWert = istUsAktie(symbol, quote?.nativeCurrency)
    val euInsider = aktie && (de.troetenrepublik.app.market.InsiderWelt.ort(symbol, isin) != null ||
        de.troetenrepublik.app.market.InsiderWelt.nurBrowser(symbol, isin) != null ||
        (!usWert && isin.length == 12 && !isin.startsWith("DE")))
    // Optionen und Leerverkaeufe stehen seit 5.92 unter „Chancen und Risiken“
    val etf = type == de.troetenrepublik.app.data.AssetType.ETF
    // Zustand der Hintergrund-Karten fuer diesen Wert, bleibt beim Scrollen erhalten (0.68)
    val merker = remember(symbol) { SeitenMerker() }
    merker.bereich = androidx.compose.runtime.rememberCoroutineScope()
    // [0.73] Werte weiterer Kursquellen (etwa Moskauer Boerse, Kuerzel "MOEX:GAZP") haben Kurs, Chart, Dividenden und Splits
    // von der Boerse; was Yahoo oder US-Behoerden braucht (Kennzahlen, Analysten, Waage, Hintergrund, Hebel), entfaellt still
    val extern = remember(symbol) { de.troetenrepublik.app.market.KursQuellen.istExtern(symbol) }
    // Signale (0.77): eine Zeile zwischen „Indikatoren“ und „Erweiterter Chart“, standardmaessig zu
    val signale = rememberSignale(state, symbol, isin, profile, usWert, aktie && !extern)
    var sigOffen by rememberSaveable { mutableStateOf(false) }
    // Rohstoffe mit eigenem Preis (0.78): Signale aus dem Kurs, Fuer und Wider, Terminkurve und Optionshandel ueber den ETF
    val rohstoff = remember(symbol) { de.troetenrepublik.app.engine.Rohstoffe.schluessel(symbol)?.takeIf { de.troetenrepublik.app.engine.RohstoffWaage.gruppe(it) != null } }
    // Rueckblick und Vergleich klappt unter dem Chart auf
    var vglOffen by rememberSaveable { mutableStateOf(false) }
    // Warnzeichen und Uebernahmen aus den 8-K (0.77): erst im Thema, wenn der Abruf Daten gebracht hat
    val achtkWarn by merker.zustand<Boolean>("achtk/warn") { (de.troetenrepublik.app.market.AchtKAbruf.gemerkt(symbol)?.anzahl ?: 0) > 0 }
    val achtkDeal by merker.zustand<Boolean>("achtk/deal") { de.troetenrepublik.app.market.AchtKAbruf.gemerkt(symbol)?.vorgaenge?.isNotEmpty() == true }
    val themen = if (extern) emptyList() else buildList {
        if (aktie || etf || type == de.troetenrepublik.app.data.AssetType.CRYPTO) add(HintergrundThema.TRENDS)
        // Kryptowerte (0.78): unbefristete Kontrakte und, bei Werten mit eigener Kette, das dort gebundene Kapital
        if (type == de.troetenrepublik.app.data.AssetType.CRYPTO && de.troetenrepublik.app.engine.KryptoHintergrund.basis(symbol) != null) add(HintergrundThema.FINANZIERUNG)
        if (type == de.troetenrepublik.app.data.AssetType.CRYPTO && de.troetenrepublik.app.engine.KryptoHintergrund.kette(de.troetenrepublik.app.engine.KryptoHintergrund.basis(symbol)) != null) add(HintergrundThema.KETTE)
        if (aktie || etf) add(HintergrundThema.HANDELNDE)
        if (aktie) add(HintergrundThema.FUEHRUNG)
        if (aktie) add(HintergrundThema.HALTER)
        if (aktie) add(HintergrundThema.STRUKTUR)
        if (aktie && de.troetenrepublik.app.market.Fda.betroffen(profile?.industry.orEmpty(), profile?.sector.orEmpty())) add(HintergrundThema.FDA)
        if (aktie && usWert) add(HintergrundThema.BEHOERDEN)
        if (aktie) add(HintergrundThema.LOBBY)
        if (aktie && de.troetenrepublik.app.market.Fda.betroffen(profile?.industry.orEmpty(), profile?.sector.orEmpty())) add(HintergrundThema.STUDIEN)
        if (aktie && de.troetenrepublik.app.market.Fahrzeugrueckrufe.betroffen(profile?.industry.orEmpty())) add(HintergrundThema.FAHRZEUGE)
        if (aktie && de.troetenrepublik.app.market.Produktrueckrufe.betroffen(profile?.industry.orEmpty())) add(HintergrundThema.PRODUKTE)
        if (aktie && usWert && de.troetenrepublik.app.market.Verbraucherbeschwerden.betroffen(profile?.industry.orEmpty())) add(HintergrundThema.BESCHWERDEN)
        if (aktie && usWert && achtkWarn) add(HintergrundThema.ACHTK_WARN)
        if (aktie && usWert && achtkDeal) add(HintergrundThema.ACHTK_DEAL)
    }
    var hintergrundOffen by rememberSaveable(symbol) { mutableStateOf(false) }
    // Unternehmen: Standorte, Geschichte, Auftragsbestand, Pruefer, Rechtsstreit, Entlassungen, Patente, Emissionen
    val unternehmen = rememberUnternehmenThemen(state, symbol, name, profile?.longName, aktie, usWert, extern, merker)
    val unternehmenMoeglich = rememberUnternehmenThemen(state, symbol, name, profile?.longName, aktie, usWert, extern, merker, pruefeLeer = false)
    var unternehmenOffen by rememberSaveable(symbol) { mutableStateOf(false) }
    // Kryptowerte: die Unterzeile des Hintergrunds sagt Finanzierungssatz und gebundenes Kapital, statt Themen zu zaehlen
    val kryptoKern by androidx.compose.runtime.produceState<String?>(null, symbol, de.troetenrepublik.app.i18n.I18n.lang) {
        value = if (type == de.troetenrepublik.app.data.AssetType.CRYPTO && (HintergrundThema.FINANZIERUNG in themen || HintergrundThema.KETTE in themen)) kryptoHintergrundKern(state, symbol) else null
    }
    var alleNews by remember(symbol) { mutableStateOf(false) }
    // Blatt einer Stimmrechtsmeldung von Investegate (5.95)
    var igMeldung by remember(symbol) { mutableStateOf<de.troetenrepublik.app.market.Investegate.Meldung?>(null) }

    // Nach unten wischen schliesst die Seite, sobald die Liste oben steht (auch an der Kopfzeile)
    val liste = rememberLazyListState()
    // Erster Eintrag mit Inhalt unter dem Chart: sein Abschnittstitel rueckt naeher an die Knopfzeile. Ist der Chart
    // aus dem Bild gerollt, bleibt der zuletzt gefundene gemerkt, damit beim Rollen nichts springt.
    var nachChart by remember(symbol) { mutableStateOf<Any?>(null) }
    LaunchedEffect(liste, symbol) {
        androidx.compose.runtime.snapshotFlow {
            val sichtbar = liste.layoutInfo.visibleItemsInfo
            val chart = sichtbar.firstOrNull { it.key == "chart" }
            if (chart == null) null else sichtbar.firstOrNull { it.index > chart.index && it.size > 0 }?.key
        }.collect { if (it != null) nachChart = it }
    }
    // Tipp auf die Trends-Zeile der Signale: Hintergrund und Trends aufklappen und dorthin rollen
    val trendsZeigen: () -> Unit = {
        hintergrundOffen = true
        merker.zustand<Boolean>("kern/trends") { false }.value = true
        indikatorScope.launch {
            val trendKeys = setOf("trends-kopf", "trends-kern", "trends-titel")
            var schritte = 0
            while (schritte++ < 80 && liste.layoutInfo.visibleItemsInfo.none { it.key == "hintergrund" } && liste.canScrollForward) {
                liste.scrollBy(liste.layoutInfo.viewportSize.height * 0.7f)
            }
            liste.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "hintergrund" }?.let { liste.animateScrollToItem(it.index) }
            // Die Trends laden erst, wenn der Abschnitt im Bild ist
            repeat(40) {
                liste.layoutInfo.visibleItemsInfo.firstOrNull { it.key in trendKeys }?.let { liste.animateScrollToItem(it.index); return@launch }
                kotlinx.coroutines.delay(100)
            }
        }
    }
    val abhZustand = remember(symbol) { AbhaengigkeitenZustand() }
    // Nachrichtenliste der Seite einmal je Stand der Nachrichten gefiltert, nicht bei jedem Aufbau der Liste
    val newsListe = remember(news) { news.distinctBy { it.id } }
    val newsGemerkt = rememberGemerkteNachrichten(state)
    val dismiss = rememberSwipeDownDismiss(onBack)
    Scaffold(
        modifier = Modifier.sheetDismiss(dismiss) { !liste.canScrollBackward },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { NamenText(name, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, S.back) } },
                actions = {
                    // Notiz und Preisalarm (5.92) als Symbole links neben dem Plus; gefuellt, wenn es eine Notiz bzw.
                    // aktive Alarme gibt
                    val notizDa = account.notizen[symbol].orEmpty().isNotBlank()
                    KopfSymbol(if (notizDa) Icons.Filled.EditNote else Icons.Outlined.EditNoteLeer, if (notizDa) S.notiz else S.notizHinzufuegen) { notizSheet = true }
                    val alarme = account.alerts.count { it.symbol == symbol }
                    KopfSymbol(
                        if (alarme > 0) Icons.Filled.Notifications else Icons.Outlined.NotificationsNone,
                        if (alarme > 0) S.bwPreisalarmZahl(alarme) else S.bwPreisalarm,
                    ) { alertSheet = true }
                    val watched = symbol in account.watchlist
                    Box(
                        Modifier
                            .padding(end = 5.dp)
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { state.watchTippen(symbol) }
                            .semantics { contentDescription = if (account.weitereListen.isNotEmpty()) S.wllWaehlen else if (watched) S.removeFromWatchlist else S.addToWatchlist },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (watched) CheckMark(size = 15.dp)
                            else PlusGlyph(size = 15.dp, color = MaterialTheme.colorScheme.onSurface, stroke = 2.dp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
      Box(Modifier.padding(padding).fillMaxSize()) {
       CompositionLocalProvider(LocalSeitenQuellen provides seitenQuellen) {
        LazyColumn(Modifier.fillMaxWidth(), state = liste) {

            item(key = "kopf", contentType = "kopf") {
                // Der Wert am Fadenkreuz wird erst hier gelesen: Beim Ziehen entsteht nur dieser Kopf neu.
                val selected = scrub
                val shownPrice = selected?.close ?: anzeigePreis
                val diff = if (reference > 0) shownPrice - reference else 0.0
                val diffPct = if (reference > 0) diff / reference * 100.0 else 0.0
                Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Row(Modifier.padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        InstrumentLogo(symbol, name, size = 48, info = info)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(typeLabel(type), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            NamenText(name, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(if (anzeigePreis > 0) kf.preis(shownPrice) else "–", style = MaterialTheme.typography.displayMedium,
                            modifier = Modifier.waehrungWechsel(kursTippen))
                        Spacer(Modifier.width(12.dp))
                        ChangeValue(
                            change = diff,
                            text = if (absoluteChange) kf.geld(abs(diff)) else Fmt.pct(abs(diffPct)),
                            colored = false,
                            style = MaterialTheme.typography.titleSmall,
                            triangleWidth = 10.dp,
                            modifier = Modifier
                                .textKlick(8.dp) { absoluteChange = !absoluteChange }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                        )
                    }
                    // Am Fadenkreuz auch die eigenen Geschaefte dieses Tages
                    val amTag = if (selected != null && markenAn) EigeneMarken.amTag(eigene.marken, selected.t, tagVon) else emptyList()
                    Text(
                        // Kein Hinweis auf vor- oder nachboerslichen Handel, gehandelt wird ueber den ganzen Tag
                        selected?.let { s ->
                            (listOf(Fmt.dateTime(s.t)) + amTag.map { S.kcMarke(it.art.name, Fmt.qty(it.stueck), kf.geld(it.betrag), kf.preis(it.kurs)) }).joinToString(" · ")
                        } ?: if (eigen) zeitraumText(eigenVon, eigenBis) else if (altKurs != null) standText(altKurs.abgerufen) else hoursText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    // [0.78] Kein laufender Kurs: deutlich, mit dem Datum des letzten Stands
                    if (veraltet) {
                        Text(S.kursOhneLauf, style = MaterialTheme.typography.titleSmall, color = signal.warn, modifier = Modifier.padding(top = 8.dp))
                        Text(S.kursLetzterStand(Fmt.date(kursZeit)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (nurSchluss && !veraltet) {
                        Text(de.troetenrepublik.app.i18n.KursRueckfallTexte.t("schluss", Fmt.date(kursZeit)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                        if (!eigeneDatei) LaunchedEffect(kursAnbieter) {
                            val seite = when (kursAnbieter) {
                                de.troetenrepublik.app.market.TiingoKurse.NAME -> de.troetenrepublik.app.market.TiingoKurse.SEITE
                                de.troetenrepublik.app.market.TwelveDataKurse.NAME -> de.troetenrepublik.app.market.TwelveDataKurse.SEITE
                                else -> de.troetenrepublik.app.market.MassiveKurse.SEITE
                            }
                            seitenQuellen.melden(QuelleLink(kursAnbieter ?: de.troetenrepublik.app.market.MassiveKurse.NAME, seite))
                        }
                    }
                    // Kurse oder Luecken aus einer eigenen Kursdatei: Herkunft im Quellenanhang
                    if (eigeneDatei || de.troetenrepublik.app.market.EigeneKurse.hat(symbol)) {
                        LaunchedEffect(Unit) { seitenQuellen.melden(QuelleLink(de.troetenrepublik.app.i18n.KursDateiTexte.t("quelle"))) }
                    }
                    // Antwortet die Kursquelle eines Moskauer Werts mehrfach nicht, steht einmal ein ruhiger Hinweis da
                    if (extern && (price <= 0 || candles.isEmpty()) && state.market.quelleAntwortetNicht(symbol)) Text(
                        S.kursquelleAntwortetNicht, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            item(key = "chart", contentType = "chart") {
                RangeSelector(
                    ranges = Range.entries,
                    selected = if (eigen) null else range,
                    onSelect = { range = it; rangeGewaehlt = true; eigenVon = 0L; eigenBis = 0L; scrub = null },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                    trailing = { ZeitraumKnopf(aktiv = eigen) { zeitraumWahl = true } },
                )
                val mitIndikatoren = if (eigen) eigenBis - eigenVon >= 20 else range != Range.D1 && range != Range.W1
                val ind = if (mitIndikatoren && indOffen) indikatoren else emptySet()
                val reihen = remember(tagesreihe) { tagesreihe?.let { IndikatorReihen.aus(it) } }
                val faktor = if (reihen != null && candles.isNotEmpty() && reihen.letzterKurs > 0) candles.last().close / reihen.letzterKurs else 1.0
                val dk = LocalSignal.current.dark
                val ueberlagerung = remember(candles, reihen, ind, faktor, dk) {
                    if (reihen == null) emptyList() else listOfNotNull(
                        if ("bb" in ind) Indikatoren.aufChart(candles, reihen.bbOben, faktor) to indFarbe(BOLLINGER_FARBE, dk).copy(alpha = 0.7f) else null,
                        if ("bb" in ind) Indikatoren.aufChart(candles, reihen.bbUnten, faktor) to indFarbe(BOLLINGER_FARBE, dk).copy(alpha = 0.7f) else null,
                        if ("sma50" in ind || "kreuz" in ind) Indikatoren.aufChart(candles, reihen.sma50, faktor) to indFarbe(SMA50_FARBE, dk) else null,
                        if ("sma200" in ind || "kreuz" in ind) Indikatoren.aufChart(candles, reihen.sma200, faktor) to indFarbe(SMA200_FARBE, dk) else null,
                        if ("ichimoku" in ind) Indikatoren.aufChart(candles, reihen.ichimokuA, faktor) to indFarbe(ICHIMOKU_FARBE, dk).copy(alpha = 0.55f) else null,
                        if ("ichimoku" in ind) Indikatoren.aufChart(candles, reihen.ichimokuB, faktor) to indFarbe(ICHIMOKU_FARBE, dk).copy(alpha = 0.55f) else null,
                        if ("ichimoku" in ind) Indikatoren.aufChart(candles, reihen.ichimokuKijun, faktor) to indFarbe(ICHIMOKU_FARBE, dk) else null,
                        if ("sar" in ind) Indikatoren.aufChart(candles, reihen.sarUnten, faktor) to indFarbe(SAR_FARBE, dk) else null,
                        if ("sar" in ind) Indikatoren.aufChart(candles, reihen.sarOben, faktor) to indFarbe(SAR_FARBE, dk) else null,
                    )
                }
                val grau = MaterialTheme.colorScheme.onSurfaceVariant
                // Ohne Einstand (steht bei der Position); Orders, Alarme und KO-Schwellen zeichnet der Chart dezent
                val kursLinien = if (!markenAn) emptyList() else eigene.linien.filter { it.art != EigeneMarken.LinienArt.EINSTAND }.map { l ->
                    val farbe = when (l.art) {
                        EigeneMarken.LinienArt.EINSTAND -> grau
                        EigeneMarken.LinienArt.LIMIT -> Kategorie.farbe(2)
                        EigeneMarken.LinienArt.ALARM -> signal.warn
                        EigeneMarken.LinienArt.STOPP, EigeneMarken.LinienArt.KO -> signal.down
                    }
                    KursLinie(l.preis, S.kcLinie(l.art.name) + " " + kf.preis(l.preis), farbe)
                }
                val eigeneKursMarken = if (!markenAn) emptyList() else eigene.marken.map { m ->
                    when (m.art) {
                        EigeneMarken.Art.KAUF -> KursMarke(m.t, signal.up, 1)
                        EigeneMarken.Art.VERKAUF -> KursMarke(m.t, signal.down, -1)
                        EigeneMarken.Art.DIVIDENDE -> KursMarke(m.t, grau, 0)
                    }
                }
                // Kreuzen der 50- und 200-Tage-Linie als Raute auf dem Kurs: Golden Cross gruen, Death Cross rot
                val kursMarken = if ("kreuz" in ind && reihen != null) {
                    eigeneKursMarken + reihen.kreuzungen.map { (tag, auf) -> KursMarke(tag * 86_400_000L + 43_200_000L, if (auf) signal.up else signal.down, KursMarke.KREUZ) }
                } else eigeneKursMarken
                Box(Modifier.fillMaxWidth().height(210.dp)) {
                    PriceChart(
                        candles = candles,
                        modifier = Modifier.fillMaxWidth().height(210.dp),
                        laedt = serieLaedt,
                        baseline = if (reference > 0) reference else null,
                        onScrub = { scrub = it },
                        domain = domain,
                        overlays = ueberlagerung,
                        linien = kursLinien,
                        marken = kursMarken,
                    )
                    ChartCrosshair(
                        candles = candles,
                        selected = scrub,
                        color = signal.line,
                        baseline = if (reference > 0) reference else null,
                        modifier = Modifier.fillMaxWidth().height(210.dp),
                        domain = domain,
                        overlays = ueberlagerung,
                        linien = kursLinien,
                    )
                    // Gespeicherter Chart: die Zeit des Stands steht blass in der Ecke, ohne den Aufbau zu verschieben
                    serieStand?.let {
                        Text(standText(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.TopStart).padding(start = 20.dp))
                    }
                }
                // Keine Kurse geladen: eigene Kursdatei einlesen
                if (!serieLaedt && candles.isEmpty()) KursdateiLink(kursdateiEinlesen)
                if (ind.isNotEmpty() && reihen != null) {
                    if ("rsi" in ind) {
                        RsiPanel(
                            candles, remember(candles, reihen) { Indikatoren.aufChart(candles, reihen.rsi) }, MaterialTheme.colorScheme.onSurface,
                            Modifier.fillMaxWidth().height(64.dp).padding(top = 8.dp), domain = domain,
                        )
                    }
                    if ("stoch" in ind) {
                        RsiPanel(
                            candles, remember(candles, reihen) { Indikatoren.aufChart(candles, reihen.stochK) }, indFarbe(STOCH_FARBE, dk),
                            Modifier.fillMaxWidth().height(64.dp).padding(top = 8.dp), domain = domain, grenzen = listOf(20.0, 80.0),
                        )
                    }
                    if ("macd" in ind) {
                        MacdPanel(
                            candles,
                            remember(candles, reihen) { Indikatoren.aufChart(candles, reihen.macd) },
                            remember(candles, reihen) { Indikatoren.aufChart(candles, reihen.macdSignal) },
                            remember(candles, reihen) { Indikatoren.aufChart(candles, reihen.macdHist) },
                            Modifier.fillMaxWidth().height(64.dp).padding(top = 8.dp), domain = domain,
                        )
                    }
                    if ("atr" in ind) {
                        LinienPanel(
                            candles, remember(candles, reihen) { Indikatoren.aufChart(candles, reihen.atr) }, indFarbe(ATR_FARBE, dk),
                            Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp), domain = domain, beschreibung = "ATR",
                        )
                    }
                    if ("obv" in ind && reihen.obv.isNotEmpty()) {
                        LinienPanel(
                            candles, remember(candles, reihen) { Indikatoren.aufChart(candles, reihen.obv) }, indFarbe(OBV_FARBE, dk),
                            Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp), domain = domain, beschreibung = "OBV",
                        )
                    }
                }
                // Kleiner Knopf „Indikatoren“ links unter dem Chart; aufgeklappt folgen die Namen aller zehn zum Ein- und
                // Ausschalten, rechts der Weg zum erweiterten Chart. Bei 1T und 1W zeichnet der Chart keine Indikatoren;
                // die Namen stehen dann gedaempft mit Hinweis da
                // Unter der Schrift der Knoepfe liegt nur leere Tippflaeche. Solange nichts aufgeklappt ist, zaehlt sie
                // nicht zur Hoehe der Zeile, damit der naechste Abschnitt dicht anschliesst; 4 dp unter der Schrift bleiben.
                val knopfMesser = androidx.compose.ui.text.rememberTextMeasurer()
                val knopfStil = MaterialTheme.typography.labelSmall
                val knopfDichte = androidx.compose.ui.platform.LocalDensity.current
                val knopfLeer = remember(knopfStil, knopfDichte) {
                    with(knopfDichte) { ((48.dp.roundToPx() - knopfMesser.measure("Ag", knopfStil).size.height) / 2 - 2.dp.roundToPx()).coerceAtLeast(0) }
                }
                val knopfZeileZu = !(sigOffen || vglOffen || indOffen)
                Row(
                    Modifier.fillMaxWidth()
                        .layout { messbar, grenzen ->
                            val p = messbar.measure(grenzen)
                            val weg = if (knopfZeileZu) knopfLeer else 0
                            layout(p.width, (p.height - weg).coerceAtLeast(0)) { p.place(0, 0) }
                        }
                        .padding(horizontal = 20.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IndikatorKnopf(indOffen, { indOffen = !indOffen })
                    Spacer(Modifier.weight(1f))
                    if (signale != null) { SignaleKnopf(state, symbol, signale, sigOffen) { sigOffen = !sigOffen }; Spacer(Modifier.weight(1f)) }
                    else if ((aktie && !extern) || rohstoff != null) {
                        // Ohne Signale bleibt der Knopf an seinem Platz, die Zeile sieht bei jeder Aktie gleich aus; bei Rohstoffen
                        // klappt er die Signale aus dem Kurs auf
                        Row(
                            Modifier.textKlick(4.dp) { sigOffen = !sigOffen }.heightIn(min = 48.dp).padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) { Text(S.sigTitel, style = MaterialTheme.typography.labelSmall, color = grau, maxLines = 1, softWrap = false) }
                        Spacer(Modifier.weight(1f))
                    }
                    Row(
                        Modifier.textKlick(4.dp) { vglOffen = !vglOffen }.heightIn(min = 48.dp).padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { Text(S.rvKnopf, style = MaterialTheme.typography.labelSmall, color = grau, maxLines = 1, softWrap = false) }
                    Spacer(Modifier.weight(1f))
                    Row(
                        Modifier.textKlick(4.dp) { onOpenRoute("chart/$symbol") }
                            .heightIn(min = 48.dp).padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(S.chartProfi, style = MaterialTheme.typography.labelSmall, color = grau, maxLines = 1)
                    }
                }
                if (sigOffen) signale?.let { SignaleListe(state, symbol, isin, name, quote, profile, it, usWert, euInsider, seite, type == de.troetenrepublik.app.data.AssetType.CRYPTO, onTrends = if (HintergrundThema.TRENDS in themen) trendsZeigen else null, onOpenRoute = onOpenRoute) }
                if (sigOffen && signale == null && aktie && !extern) Text(
                    S.sigKeine, style = MaterialTheme.typography.bodySmall, color = grau,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
                if (sigOffen && signale == null) rohstoff?.let { RohstoffSignaleListe(state, symbol, it) }
                if (vglOffen) RueckblickEingebettet(state, symbol, name)
                if (indOffen) IndikatorNamen(indikatoren, ::indikatorUmschalten, Modifier.fillMaxWidth().padding(horizontal = 14.dp), imChart = mitIndikatoren)
                if (indOffen) reihen?.gesamt?.let { GesamtRichtung(it, Modifier.padding(horizontal = 20.dp)) }
                if (indGezeigt) {
                    if (reihen == null) Text(
                        S.toolLoading, style = MaterialTheme.typography.bodySmall, color = grau,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    ) else IndikatorDeutung(
                        reihen.deutungen, indikatoren, Modifier.padding(horizontal = 20.dp).padding(bottom = 6.dp), bilanzen = bilanzen,
                        kerzenRoute = reihen.kerzenMarke?.let { ChartSprung.route(symbol, it) }, onRoute = onOpenRoute,
                        kerzenText = reihen.kerzenMarke?.let { S.kzImChart(S.kzName(it.muster), Fmt.date(it.bisTag * 86_400_000L + 43_200_000L)) }.orEmpty(),
                    )
                }
            }

            // Indizes und ETFs: Heatmap der Komponenten direkt unter Chart und Knopfzeile (Flaeche = Gewicht, Farbe = Tagesaenderung)
            if (type == de.troetenrepublik.app.data.AssetType.ETF || type == de.troetenrepublik.app.data.AssetType.INDEX) {
                item(key = "komponenten-heatmap", contentType = "heatmap") {
                    KomponentenHeatmap(state, symbol, merker, etf = type == de.troetenrepublik.app.data.AssetType.ETF) { onOpenRoute("instrument/$it") }
                }
            }

            // Deine Position (falls gehalten) mit Einstand, Haltedauer und Sparplan, darunter eine vorhandene Notiz und
            // offene Orders dieses Werts. Preisalarm und Notiz stehen seit 5.92 als Symbole in der Kopfzeile, der
            // Sparplan ohne Position wieder als schwebender Knopf neben Kaufen.
            item(key = "position-aktionen") { AbschnittNah(nachChart == "position-aktionen") {
                if (position != null) {
                    SectionHeader(S.yourPosition)
                    val seit = position.position.openedAt.takeIf { it > 0 }
                    StatPages(
                        statPages(
                            listOf(
                                S.quantity to "${Fmt.qty(position.qty)} ${S.pieces}",
                                S.avgPrice to kf.preis(position.position.avgPrice),
                                seit?.let { S.p1SeitKauf to S.p1Haltedauer(Fmt.date(it), ((now - it) / 86_400_000L).coerceAtLeast(0)) },
                                S.value to kf.geld(position.value),
                                S.result to "${kf.geldMitVorzeichen(position.profit)}  ${Fmt.signedPct(position.profitPct)}",
                            ),
                            perPage = 5,
                        )
                    )
                    // Unten steht bei gehaltener Position Verkaufen statt Sparplan: der Sparplan bleibt hier erreichbar
                    if (type.directlyTradable && !veraltet) ActionLineWide(S.savingsPlan) { planSheet = true }
                }
                NotizZeile(account.notizen[symbol].orEmpty()) { notizSheet = true }
                OffeneOrdersKurz(
                    account.orders.filter { it.status == de.troetenrepublik.app.data.OrderStatus.OPEN && it.symbol == symbol && it.derivative == null },
                ) { onOpenRoute("orders") }
            } }

            // Anlage-These mit Prueftermin (seit 5.91)
            account.thesen[symbol]?.let { t ->
                item(key = "these") { AbschnittNah(nachChart == "these") { TheseKarte(state, symbol, t, gehalten = position != null) { sheet = Side.SELL } } }
            }

            if (description.isNotBlank()) {
                item(key = "beschreibung") { AbschnittNah(nachChart == "beschreibung") {
                    // Ohne Pfeil an der Ueberschrift; ein kleiner Pfeil am Ende des gekuerzten Texts zeigt, dass er
                    // aufklappt (5.92)
                    SectionHeader(S.information, onClick = { showAll = !showAll })
                    // Fremdsprachiger Text und DeepL-Schluessel unter Zugaenge: ein Knopf uebersetzt den Text (je Text gemerkt)
                    val appSprache = if (settings.sprache == Lang.DE) "de" else "en"
                    val ziel = if (settings.sprache == Lang.DE) "DE" else "EN-US"
                    val uebersetzbar = settings.keyDeepl.isNotBlank() && beschreibung.sprache != appSprache
                    var uebersetzt by remember(symbol, description, ziel) { mutableStateOf<String?>(state.beschreibungUebersetzt(description, ziel)) }
                    var zeigeUebersetzt by remember(symbol, description, ziel) { mutableStateOf(uebersetzt != null) }
                    var uebersetzFehler by remember(symbol, description, ziel) { mutableStateOf(false) }
                    var uebersetzLaeuft by remember(symbol, description, ziel) { mutableStateOf(false) }
                    GekuerzterText(if (uebersetzbar && zeigeUebersetzt) uebersetzt ?: description else description, showAll) { showAll = !showAll }
                    if (uebersetzbar) {
                        UebersetzenKnopf(
                            when {
                                uebersetzFehler -> S.uebersetzenFehler
                                zeigeUebersetzt -> S.uebersetzenOriginal
                                else -> S.uebersetzen
                            },
                        ) {
                            if (uebersetzLaeuft) return@UebersetzenKnopf
                            if (zeigeUebersetzt) { zeigeUebersetzt = false; return@UebersetzenKnopf }
                            uebersetzFehler = false
                            if (uebersetzt != null) { zeigeUebersetzt = true; return@UebersetzenKnopf }
                            uebersetzLaeuft = true
                            indikatorScope.launch {
                                val t = state.beschreibungUebersetzen(description, ziel)
                                if (t == null) uebersetzFehler = true else { uebersetzt = t; zeigeUebersetzt = true }
                                uebersetzLaeuft = false
                            }
                        }
                    }
                    // Wikipedia verlangt Nennung mit Lizenz und Link auf den Artikel (CC BY-SA): Sie steht im Quellenanhang
                    // am Seitenende, mit Link auf den Artikel
                    if (fromWiki) QuellenMelden(QuelleLink(S.sourceWikipedia, beschreibung.artikel.ifBlank { null }))
                } }
            }

            // ETFs: was drinsteckt und wie viel davon im eigenen Depot landet
            if (type == de.troetenrepublik.app.data.AssetType.ETF) {
                item(key = "etf-durchschau-lader", contentType = "lader") { AbschnittNah(nachChart == "etf-durchschau-lader") { EtfDurchschauKarte(state, symbol, merker, onOpenRoute) } }
                etfDurchschauAbschnitt(merker)
            }
            // Indizes: Komponenten mit Gewicht, Tagesaenderung und Beitrag zur Bewegung
            if (type == de.troetenrepublik.app.data.AssetType.INDEX) {
                item(key = "index-komponenten-lader", contentType = "lader") { AbschnittNah(nachChart == "index-komponenten-lader") { IndexKomponentenKarte(state, symbol, merker) { onOpenRoute("instrument/$it") } } }
                indexKomponentenAbschnitt(merker)
            }
            // Abstand zum Index (0.78): Meldungen von US-Indexfonds an die SEC; ohne Meldung zum Kuerzel bleibt der Abschnitt weg
            if (type == de.troetenrepublik.app.data.AssetType.ETF) item(key = "etf-abstand") { AbschnittNah(nachChart == "etf-abstand") { EtfAbstandKarte(state, symbol) } }
            // Tracking Difference (5.94): ohne freie Indexrenditen nicht selbst rechenbar, daher die Seite von extraETF in der
            // App. Zeigt die Abstand-Karte schon den Abstand zum Index aus SEC-Daten, entfaellt die Zeile.
            if (type == de.troetenrepublik.app.data.AssetType.ETF && isin.length == 12 &&
                AbstandLader.gespeichert()?.let { de.troetenrepublik.app.engine.IndexAbstand.fuer(it, symbol) } == null
            ) item(key = "etf-td") {
                var tdOffen by remember(symbol) { mutableStateOf(false) }
                Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(S.etfTdHinweis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
                    QuellenZeile(S.etfTdLink, onClick = { tdOffen = true })
                }
                if (tdOffen) NewsSheet(
                    MarketApi.NewsItem(title = S.etfTdLink, link = "https://extraetf.com/de/etf-profile/$isin?tab=td", published = 0L, source = "extraETF"),
                    onDismiss = { tdOffen = false }, actionsTop = true,
                )
            }

            // Rohstoffe und Rohstoff-ETCs: was es ist, was den Preis bewegt, wie man anlegt
            de.troetenrepublik.app.engine.Rohstoffe.schluessel(symbol)?.let { k ->
                item(key = "rohstoff") { AbschnittNah(nachChart == "rohstoff") { RohstoffKarte(symbol, k) } }
            }

            // Fruehe Signale (5.92): Lager bei Oel und Gas, Duerre bei Getreide, Fluggaeste bei Fluglinien, Oellager bei
            // Oel- und Gasfirmen; ohne passendes Signal leer
            if (!extern) item(key = "fruehsignale") { AbschnittNah(nachChart == "fruehsignale") { FruehSignalKarte(state, symbol, profile?.industry.orEmpty(), onOpenRoute) } }
            // Rohstoffe (0.78): bei Oel die Welt neben den US-Lagern, dann Fuer und Wider aus Messgroessen
            if (rohstoff == "OEL") {
                item(key = "oelwelt-lader", contentType = "lader") { OelWeltLader(merker) }
                oelWeltAbschnitt(merker)
            }
            // Oel und Erdgas je Land (Foerderung, Verbrauch, Ausfuhr, Gasfackeln als Schaetzung)
            if (rohstoff == "OEL" || rohstoff == "GAS") {
                item(key = "energiewelt-lader", contentType = "lader") { EnergieWeltLader(merker, rohstoff!!) }
                energieWeltAbschnitt(merker, rohstoff!!)
            }
            // Goldreserven der Zentralbanken je Land (IWF)
            if (rohstoff == "GOLD") {
                item(key = "goldreserven-lader", contentType = "lader") { GoldReservenLader(merker) }
                goldReservenAbschnitt(merker)
            }
            rohstoff?.let { k ->
                item(key = "rohwaage-lader", contentType = "lader") { RohstoffWaageLader(state, symbol, k, merker) }
                rohstoffWaageAbschnitt(merker)
            }

            // Bewertung und Kennzahlen (5.92): Kernwerte, bei Aktien Ampeln fuer Bewertung, Bilanz und Wachstum, alle
            // Kennzahlen im Blatt und der Vergleich mit aehnlichen Aktien
            listOfNotNull(profilStand, newsStand).minOrNull()?.let { stand ->
                item(key = "stand-hinweis") {
                    Text(standText(stand), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                }
            }
            // [0.78] Werte weiterer Kursquellen: Kennzahlen, Bewertung und das Fuer und Wider aus den Firmendaten zum letzten
            // bekannten Stand, mit Datum
            if (veraltet && aktie && profile != null) item(key = "archiv-stand") {
                Text(S.kursKennzahlenStand(Fmt.date(kursZeit)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }
            if (!extern || profile != null) item(key = "kennzahlen") { AbschnittNah(nachChart == "kennzahlen") {
                BewertungUndKennzahlen(
                    state, symbol, name, quote, profile, aktie, kennzahlen.first, seite, kennzahlTipp, kennzahlAktion,
                    onAlle = { alleKennzahlen = true }, onFinanzen = { financialsSheet = true },
                    onVergleich = { onOpenRoute("kennzahlvergleich/$symbol") },
                    merker = merker,
                )
            } }

            // [Lange Lage] KGV gegen das KGV der US-Branche (Damodaran), nur mit Zuordnung der Branche und positivem KGV
            if (aktie && !extern && profile != null) item(key = "branche-kgv", contentType = "karte") {
                BrancheKgvKarte(profile?.industry.orEmpty(), de.troetenrepublik.app.engine.Bewertungsblick.kgv(profile?.trailingPe, quote?.peRatio), merker)
            }

            // [0.77] Erklaer-Assistent: nur mit hinterlegtem Schluessel, schickt die Kennzahlen der Seite ohne Depotangaben
            if (!extern && (aktie || etf)) item(key = "erklaeren", contentType = "knopf") {
                ErklaerKnopf(state, {
                    val q = quote
                    val nativ = (q?.nativePrice ?: 0.0) > 0
                    de.troetenrepublik.app.engine.Erklaerer.kontextWertpapier(
                        name, symbol, if (etf) "ETF" else "stock",
                        if (nativ) q?.nativePrice ?: 0.0 else q?.price ?: 0.0, if (nativ) q?.nativeCurrency.orEmpty() else q?.currency.orEmpty(),
                        q?.takeIf { it.prevClose > 0 && it.price > 0 }?.let { (it.price / it.prevClose - 1) * 100 }, profile,
                    )
                })
            }

            // ETFs: Ausschuettungen kompakt unter den Kennzahlen (5.97), ohne Ausschuettung leer
            if (etf) item(key = "etf-dividende") { EtfDividende(state, symbol, profile, quote?.price ?: 0.0, inEuro(quote), seite, position?.takeIf { it.position.derivative == null }?.qty, merker) }

            // Chancen und Risiken (5.92): Analysten, Stimmung aus Optionen und Leerverkaeufen, was dafuer und was dagegen
            // spricht; ein Tipp auf einen Punkt oeffnet die Einzelheiten
            // Lader ohne Inhalt, danach je Zeile ein Eintrag (Modell im Merker); die Dialoge stehen unten ausserhalb der Liste
            if ((aktie || etf) && (!extern || profile != null)) {
                item(key = "chancen-lader", contentType = "lader") {
                    ChancenUndRisiken(
                        state, symbol, isin, quote, profile, news, aktie = aktie, usWert = usWert,
                        mitOptionen = usWert || aktie, seite = seite, ziel = ziel, signale = signale, merker = merker, onNews = { openNews = it },
                    )
                }
                chancenAbschnitt(merker)
            }

            // Abhaengigkeiten und Risiken (0.73): Land, Rohstoffe, Einfluesse, Politik und Kursempfindlichkeit; zu beim Oeffnen
            if (!extern && (aktie || etf || type == de.troetenrepublik.app.data.AssetType.COMMODITY)) {
                item(key = "abhaengigkeiten-lader", contentType = "lader") {
                    AbhaengigkeitenKarte(state, symbol, type, profile, usWert, merker, abhZustand, onWasWaere = if (position != null) ({ szenarienBlatt = true }) else null, oeffnen = onOpenRoute)
                }
                abhaengigkeitenAbschnitt(merker)
            }

            // Verflechtungen (0.80): Zulieferer, Abnehmer, Beteiligungen, Wettbewerber, Mandate, Staatsgeld; ohne Kante keine Karte
            if (!extern && aktie) {
                item(key = "verflechtungen-lader", contentType = "lader") {
                    VerflechtungenKarte(state, symbol, name, type, merker, onOpenRoute)
                }
                verflechtungenAbschnitt(merker)
            }

            // Was Investoren schreiben (5.92): Auszuege aus Briefen von Fondsmanagern mit Einordnung des Eigeninteresses;
            // ohne Fundstelle leer (bei US-Aktien nur der Link zur Suche)
            if (aktie && !extern) {
                item(key = "investorenbriefe-lader", contentType = "lader") {
                    InvestorenBriefeKarte(state, symbol, profile?.longName?.ifBlank { null } ?: name, usWert, onOpenRoute, merker)
                }
                investorenBriefeAbschnitt(merker)
            }

            // Ereignisse: naechste Termine mit Erwartung, letzte Zahlen gegen die Erwartung (5.92)
            profile?.let { p -> item(key = "ereignisse", contentType = "karte") { EreignisseAbschnitt(state, symbol, p, seite, merker, onGlocke = { alertSheet = true }, onOpenSymbol = { onOpenRoute("instrument/$it") }) { eventsSheet = true } } }

            // [Lange Lage] Ton der Berichte ueber die Firma (GDELT): Abruf im Bereich der Seite, ohne Daten keine Karte
            if (aktie && !extern) item(key = "ton-lader", contentType = "lader") {
                TonKarte(state, symbol, name, profile?.longName, merker)
            }

            // News: die drei neuesten, der Pfeil darunter klappt den Rest auf der Seite auf, duenne Linien dazwischen (5.92)
            if (newsListe.isNotEmpty()) {
                item(key = "news-kopf", contentType = "kopf") { SectionHeader(S.news) }
                itemsIndexed(if (alleNews) newsListe else newsListe.take(3), key = { _, n -> n.id }, contentType = { _, _ -> "news" }) { i, n ->
                    if (i > 0) Trennlinie()
                    WertNachrichtZeile(state, n, newsGemerkt) { openNews = n }
                }
                if (newsListe.size > 3) item(key = "news-mehr", contentType = "pfeil") { ListenPfeil(alleNews, label = if (alleNews) S.less else "${S.more} (${newsListe.size - 3})") { alleNews = !alleNews } }
            }

            // Nachrichten ueber EODHD (5.95): eigener Schluessel, der Abschnitt laedt erst beim Aufklappen
            if (settings.keyEodhd.isNotBlank() && NachrichtenApis.eodhdTicker(symbol) != null) {
                item(key = "eodhd-lader", contentType = "lader") {
                    EodhdAbschnitt(state, symbol, { n, absaetze -> eodhdArtikel = n; eodhdText = absaetze }, merker)
                }
                eodhdAbschnitt(merker)
            }

            // Rohstoffe (0.78): Terminkurve und Optionshandel ueber den passenden ETF
            if (rohstoff != null) item(key = "rohstoff-handel") {
                RohstoffHandelKarte(state, symbol, name, quote?.nativePrice ?: 0.0, quote?.nativeCurrency?.ifBlank { null } ?: "USD", merker)
            }

            // Derivate: Long/Short-Balken aus dem Optionsumsatz, ohne Daten die Pillen (5.93)
            if (type.directlyTradable && !extern) item(key = "hebel") {
                HebelKarte(
                    state, symbol, mitOptionen = usWert || aktie,
                    onLong = { onOpenRoute("derivate/L/$symbol") }, onShort = { onOpenRoute("derivate/S/$symbol") },
                    // Verweis zum Blatt „Optionen“, das die Dialoge von Chancen und Risiken fuehren
                    onOptionen = if ((aktie || etf) && state.market.optionenMoeglich(symbol)) ({ merker.zustand<Detail?>("chancen/detail") { null }.value = Detail.OPTIONEN }) else null,
                )
            }

            // Hintergrund: eingeklappt, die Karten laden erst beim Aufklappen (5.91)
            if (themen.isNotEmpty()) {
                item(key = "hintergrund") {
                    // Lobbyarbeit Europa steht als Zeile unter „Politik und Behoerden“, wenn es dieses Thema gibt
                    val genannt = themen.filter { it != HintergrundThema.LOBBY || HintergrundThema.BEHOERDEN !in themen }
                    AufklappKopf(S.bwHintergrund, kryptoKern ?: S.bwHintergrundUnter(genannt.map { S.bwThema(it.name) }), hintergrundOffen) { hintergrundOffen = !hintergrundOffen }
                }
                if (hintergrundOffen) {
                    // Laden und Rechnen der langen Listen (Trends, Handelnde, Insider, Halter): ein Eintrag ohne Inhalt, die Zeilen
                    // kommen danach als einzelne Eintraege der Seite (Modelle im Merker)
                    item(key = "hintergrund-lader", contentType = "lader") {
                        // Trends zum Wert: Reddit, Yahoo Finance, Wikipedia; nur mit Daten
                        if (HintergrundThema.TRENDS in themen) WertTrends(state, symbol, name, type, profile?.sector, merker = merker)
                        if (HintergrundThema.FINANZIERUNG in themen) KryptoFinanzierungKarte(state, symbol, merker)
                        if (HintergrundThema.KETTE in themen) KryptoKetteKarte(state, symbol, name, merker)
                        // Wer den Wert handelt: Personen aus Kongress, Senat und Regierung und Investoren mit ihren Geschaeften
                        if (HintergrundThema.HANDELNDE in themen) HandelndeAbschnitt(state, symbol, onOpenRoute, usWert = usWert, merker = merker)
                        // Britische Abgeordnete mit gemeldeter Beteiligung an genau dieser Firma (Name samt Rechtsform)
                        if (HintergrundThema.HANDELNDE in themen && aktie) UkRegisterKarte(state, profile?.longName?.ifBlank { null }, merker)
                        // Insider: BaFin (Deutschland), Register weiterer Laender, SEC Form 4 und Form 144/13D (USA)
                        if (HintergrundThema.FUEHRUNG in themen) InsiderInhalt(state, symbol, isin, name, usWert, euInsider, onOpenRoute, merker = merker)
                        // Groesste institutionelle Halter (5.93)
                        if (HintergrundThema.HALTER in themen) HalterKarte(state, symbol, quote?.nativeCurrency.orEmpty(), seite, merker = merker)
                        // In welchen ETFs die Aktie steckt
                        // Eigentuemer und Rechte: Organigramm, Gattungen, Deine Rechte, Hauptversammlung, Aktivisten
                        if (HintergrundThema.HALTER in themen) EtfRueckwaertsKarte(symbol, merker) { onOpenRoute("instrument/$it") }
                        // Stimmrechtsmeldungen britischer Firmen (Investegate), FDA, Behoerden und Lobbyarbeit in Europa
                        if (HintergrundThema.HALTER in themen) de.troetenrepublik.app.market.Investegate.ticker(symbol)?.let { k -> StimmrechteGbKarte(state, k, { igMeldung = it }, merker) }
                        if (HintergrundThema.FDA in themen) FdaKarte(state, symbol, profile?.longName?.ifBlank { null } ?: name, merker = merker)
                        if (HintergrundThema.BEHOERDEN in themen) BehoerdenKarte(state, symbol, name, onOpenRoute, merker, industrie = profile?.industry.orEmpty())
                        if (aktie && usWert) AchtKKarte(state, symbol, merker)
                        if (HintergrundThema.LOBBY in themen) LobbyWeltKarte(state, symbol, merker)
                        if (HintergrundThema.STUDIEN in themen) StudienKarte(state, symbol, profile?.longName?.ifBlank { null } ?: name, merker)
                        if (HintergrundThema.FAHRZEUGE in themen) FahrzeugKarte(state, symbol, profile?.longName?.ifBlank { null } ?: name, merker)
                        if (HintergrundThema.PRODUKTE in themen) ProduktKarte(state, symbol, profile?.longName?.ifBlank { null } ?: name, merker)
                        if (HintergrundThema.BESCHWERDEN in themen) BeschwerdenKarte(profile?.longName?.ifBlank { null } ?: name, merker)
                    }
                    if (HintergrundThema.TRENDS in themen) wertTrendsAbschnitt(merker)
                    if (HintergrundThema.FINANZIERUNG in themen || HintergrundThema.KETTE in themen) kryptoHintergrundAbschnitte(merker)
                    if (HintergrundThema.HANDELNDE in themen) handelndeAbschnitt(merker)
                    if (HintergrundThema.HANDELNDE in themen && aktie) ukRegisterAktieAbschnitt(merker)
                    if (HintergrundThema.FUEHRUNG in themen) insiderAbschnitte(merker, state, symbol, isin, usWert, euInsider, onOpenRoute)
                    if (HintergrundThema.HALTER in themen) halterAbschnitt(merker)
                    // Grossaktionaere aus BaFin-Stimmrechten und 13D/13G (die Karten des Insider-Themas liefern sie)
                    if (HintergrundThema.HALTER in themen && HintergrundThema.FUEHRUNG in themen) grossaktionaerAbschnitte(merker, usWert)
                    // Eigener Ladeeintrag direkt vor dem Abschnitt: Die Karte laedt erst beim Aufklappen und muss ihr Modell neu melden,
                    // wenn der Abruf fertig ist; im gemeinsamen Ladeeintrag oben waere sie dann meist schon aus dem Bild verworfen
                    if (HintergrundThema.STRUKTUR in themen) item(key = "struktur-lader", contentType = "lader") {
                        StrukturKarte(state, symbol, profile?.longName?.ifBlank { null } ?: name, isin, position?.takeIf { it.position.derivative == null }?.qty, merker)
                    }
                    if (HintergrundThema.STRUKTUR in themen) strukturAbschnitt(merker)
                    if (HintergrundThema.HALTER in themen) etfRueckwaertsAbschnitt(merker)
                    // Stimmrechtsmeldungen (TR-1 nach DTR 5) britischer Firmen ueber Investegate (5.95)
                    if (HintergrundThema.HALTER in themen && de.troetenrepublik.app.market.Investegate.ticker(symbol) != null) stimmrechteGbAbschnitt(merker)
                    // Zulassungen und Rueckrufe der FDA bei Pharma-, Biotech- und Medizintechnik-Aktien
                    if (HintergrundThema.FDA in themen) fdaAbschnitte(merker)
                    // Lobbying, Gesetze, Auftraege, Spenden, Entlassungen und Patente bei US-Aktien
                    if (HintergrundThema.BEHOERDEN in themen) behoerdenAbschnitt(merker)
                    // Lobbyarbeit in Europa (EU-Transparenzregister, Bundestag, HATVP): als Zeile unter „Politik und
                    // Behoerden“, ohne dieses Thema als eigener Abschnitt
                    if (HintergrundThema.LOBBY in themen) lobbyAbschnitt(merker)
                    if (aktie && usWert) achtkAbschnitte(merker)
                    registerAbschnitte(merker, HintergrundThema.STUDIEN in themen, HintergrundThema.FAHRZEUGE in themen, HintergrundThema.PRODUKTE in themen, HintergrundThema.BESCHWERDEN in themen)
                }
            }

            // Unternehmen: eingeklappt, nennt die Themen mit Daten; jeder Baustein hat einen eigenen Ladeeintrag vor seinen Zeilen
            unternehmenAbschnitt(
                unternehmen, unternehmenMoeglich, unternehmenOffen, { unternehmenOffen = !unternehmenOffen },
                state, symbol, profile?.longName?.ifBlank { null } ?: name, name, profile?.longName, onOpenRoute, merker,
                profil = profile,
            )

            // Werkzeuge zur Analyse, darin aufklappbar „Wie tief kann es fallen“ (laedt erst beim Aufklappen), zuletzt
            // das Strategie-Labor
            item(key = "werkzeuge") {
                StockToolsSection(toolsFor(type, position != null && position.position.derivative == null, profile)) { tool = it }
            }
            item(key = "rendite-risiko-lader", contentType = "lader") { RenditeRisiko(state, symbol, profile?.country, merker) }
            renditeRisikoAbschnitt(merker)
            item(key = "labor") { NavRow(S.slTitel, S.slUntertitel) { labor = true } }
            if (intradayMoeglich) item(key = "intraday") { NavRow(intradayTitel, intradayUnter) { intraday = true } }

            item(key = "quellen") { QuellenAnhang(seitenQuellen.liste.toList()) }
            item(key = "abstand", contentType = "abstand") { Spacer(Modifier.height(110.dp)) }
        }
       }

        // Kauf- und Verkaufsknopf schweben ueber dem Inhalt, wie in der Kursansicht.
        Box(Modifier.align(Alignment.BottomCenter)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Ohne Position steht der Sparplan wieder neben Kaufen, mit Position in der Positionszeile (5.92)
                if (position != null) {
                    FloatingPill(label = S.sell, filled = false, modifier = Modifier.weight(1f)) { sheet = Side.SELL }
                } else if (type.directlyTradable) {
                    FloatingPill(label = S.savingsPlan, icon = Icons.Filled.Autorenew, filled = true, enabled = !veraltet && !(extern && price <= 0), modifier = Modifier.weight(1f)) { planSheet = true }
                }
                if (type.directlyTradable) {
                    FloatingPill(label = S.buy, plus = true, filled = true, enabled = price > 0 && !veraltet, modifier = Modifier.weight(1f)) { sheet = Side.BUY }
                }
            }
        }
      }
    }


    // Dialoge und Blaetter der Karten, deren Zeilen als Eintraege der Liste stehen
    if ((aktie || etf) && (!extern || profile != null)) ChancenDialoge(state, merker, symbol, isin, name, quote, profile, seite, usWert, euInsider, signale?.kongress, onOpenRoute)
    if (aktie && !extern) InvestorenBriefeDialoge(merker)
    if (aktie && !extern) StandorteBlatt(state, profile?.longName?.ifBlank { null } ?: name, onOpenRoute, merker)

    if (alertSheet) AlertsSheet(state, symbol, name, onDismiss = { alertSheet = false })
    if (notizSheet) NotizSheet(state, symbol, name, onDismiss = { notizSheet = false })
    if (zeitraumWahl) ZeitraumDialog(eigenVon, eigenBis, onDismiss = { zeitraumWahl = false }) { von, bis ->
        eigenVon = minOf(von, bis); eigenBis = maxOf(von, bis); scrub = null; zeitraumWahl = false
    }
    // Aus der Positionsgroesse: Kaufmaske mit Stueckzahl, Stopp und Ziel (Stueck, Stoppkurs, Zielkurs; Ziel 0 = ohne)
    var orderVorgabe by remember(symbol) { mutableStateOf<Triple<Double, Double, Double>?>(null) }
    tool?.let { t ->
        WiederkehrBlatt("werkzeug-" + t.name) { w ->
            StockToolSheet(state, t, symbol, name, profile, onDismiss = { tool = null }, onSteuer = { w.verlassen(); onOpenRoute("tax") },
                onOrder = { stueck, stopp, ziel -> orderVorgabe = Triple(stueck, stopp, ziel); tool = null; sheet = Side.BUY })
        }
    }
    if (alleKennzahlen) {
        // Bei Aktien zuerst die weiteren Kennzahlen in Gruppen, danach Handel und Stammdaten (5.92)
        val gruppen = profile?.takeIf { aktie }?.let { p -> mehrKennzahlen(state, symbol, quote, p, seite, kennzahlen.third).map { (k, z) -> S.bkGruppe(k) to z } }.orEmpty()
        AlleKennzahlenBlatt(
            name, gruppen + ((if (gruppen.isEmpty()) null else S.bkGruppe("HANDEL")) to kennzahlen.second), kennzahlTipp, kennzahlAktion,
            { alleKennzahlen = false },
        ) { if (aktie) MitarbeiterKarte(symbol, profile, name) }
    }
    if (labor) StrategieLaborSheet(state, symbol, name, onDismiss = { labor = false })
    if (intraday) IntradaySheet(state, symbol, name, onDismiss = { intraday = false })

    if (planSheet) PlanSheet(state, symbol, name, onDismiss = { planSheet = false })

    if (eventsSheet) EventsSheet(state, symbol, name, profile, seite, onGlocke = { eventsSheet = false; alertSheet = true }, onDismiss = { eventsSheet = false })
    val szenarienRueck = rememberWiederkehr()
    if (szenarienBlatt) WiederkehrBlatt("szenarien", szenarienRueck) { PortfolioToolSheet(
        state, PortfolioTool.SCENARIO, onDismiss = { szenarienBlatt = false; szenarienRueck.vergessen() },
        onOpen = { s -> szenarienRueck.verlassen(); onOpenRoute(if ('/' in s) s else "instrument/$s") }, sofort = szenarienRueck.sofort,
    ) }

    if (financialsSheet) profile?.let {
        // Gewinnrechnung fuer das Sankey-Diagramm erst beim Oeffnen (5.94)
        var guv by remember(symbol) { mutableStateOf<de.troetenrepublik.app.engine.Sankey.Guv?>(null) }
        // Bilanz und Cashflow ueber die Jahre (5.94), aus denselben Jahresabschluessen wie Piotroski und Altman
        var jahre by remember(symbol) { mutableStateOf<List<de.troetenrepublik.app.engine.Qualitaet.Jahr>>(emptyList()) }
        // Kompakte Dividende (5.97) aus der Dividendenhistorie und den Profilangaben
        var dividenden by remember(symbol) { mutableStateOf<List<de.troetenrepublik.app.market.MarketApi.DividendEvent>?>(null) }
        LaunchedEffect(symbol) { guv = state.guv(symbol) }
        LaunchedEffect(symbol) { jahre = state.jahresabschluesse(symbol) }
        LaunchedEffect(symbol) { dividenden = state.dividendenVerlauf(symbol) }
        val divWerte = remember(dividenden, it, quote?.price) {
            dividenden?.let { ev ->
                de.troetenrepublik.app.engine.DividendenUeberblick.werte(
                    ev, it.dividendYield, it.payoutRatio, it.exDividendDate, it.dividendDate, quote?.price ?: 0.0,
                    jeAktieProfilEuro = it.dividendRate * inEuro(quote), land = de.troetenrepublik.app.engine.Alarmarten.land(symbol),
                )
            }
        }
        FinancialsSheet(name, it, seite, guv = guv, jahre = jahre, dividende = divWerte, state = state,
            stueck = position?.takeIf { p -> p.position.derivative == null }?.qty,
            zusatz = { VerlaeufeKarte(state, symbol, quote, it, seite); ExportZeilen(state, symbol, it, jahre) },
            onDismiss = { financialsSheet = false })
    }

    // Dasselbe Blatt wie im News-Reiter (mit Einordnung). Aus dem Artikel zu einem anderen Wert: das Blatt wartet und
    // kommt nach dem Zurueck wieder
    openNews?.let { n ->
        NachrichtBlatt(state, remember(n) { de.troetenrepublik.app.engine.WertNachrichten.eintrag(n) }, onOpen = { s -> onOpenRoute("instrument/$s") }, symbole = listOf(symbol), ohneSymbol = symbol) { openNews = null }
    }

    // Nachricht aus EODHD mit dem schon geholten Text
    val eodhdRueck = rememberWiederkehr()
    eodhdArtikel?.let { n -> WiederkehrBlatt("eodhd", eodhdRueck) { NewsSheet(n, onDismiss = { eodhdArtikel = null; eodhdRueck.vergessen() }, text = eodhdText.ifEmpty { null }, symbole = listOf(symbol), onOpenSymbol = { s -> eodhdRueck.verlassen(); onOpenRoute("instrument/$s") }, ohneSymbol = symbol) } }

    // Stimmrechtsmeldung von Investegate mit dem schon geholten Bogen
    igMeldung?.let { m -> InvestegateMeldungBlatt(state, m, onDismiss = { igMeldung = null }) }

    sheet?.let { side ->
        TradeSheet(
            title = name,
            subtitle = typeLabel(type),
            price = state.bidAsk(symbol).let { if (side == Side.BUY) it.second else it.first },
            side = side,
            cash = account.frei.coerceAtLeast(0.0),
            ownedQty = position?.qty ?: 0.0,
            gebunden = account.gebundeneStueck(symbol),
            allowFractional = true,
            derivative = null,
            marketOpen = marketOpen,
            hoursHint = hoursText,
            onDismiss = { sheet = null; orderVorgabe = null },
            onMarket = { qty, note -> if (side == Side.BUY) state.buy(symbol, name, symbol, qty, note = note) else state.sell(symbol, qty, note) },
            onLimit = { qty, limit, type2, validity, note, trail -> state.placeOrder(symbol, name, symbol, side, type2, qty, limit, validity = validity, note = note, trailPct = trail) },
            startStueck = if (side == Side.BUY) orderVorgabe?.first ?: 0.0 else 0.0,
            stoppDanach = if (side == Side.BUY) orderVorgabe?.second ?: 0.0 else 0.0,
            zielDanach = if (side == Side.BUY) orderVorgabe?.third ?: 0.0 else 0.0,
            // Mit Ziel ueber dem Kurs als verknuepftes Paar, sonst der Stopp allein
            onStoppDanach = { qty ->
                orderVorgabe?.let {
                    if (it.third > state.bidAsk(symbol).second) state.stoppMitZiel(symbol, name, symbol, qty, it.second, it.third)
                    else state.placeOrder(symbol, name, symbol, Side.SELL, de.troetenrepublik.app.data.OrderType.STOP, qty, it.second)
                }
            },
            risikoWarnungen = { kurs, stueck, stopp, ziel -> state.risikoWarnungen(kurs, stueck, stopp, ziel).map { risikoVerstossText(it) } },
            atrPct = rememberAtrProzent(state, symbol),
            onStoppMitZiel = { qty, stopp, ziel, validity, note -> state.stoppMitZiel(symbol, name, symbol, qty, stopp, ziel, validity = validity, note = note) },
            theseErlaubt = true,
            regelWarnungen = { betrag, grenze -> state.kaufWarnungen(symbol, betrag, grenze, hebel = false).map { theseVerstossText(it) } },
            onGekauft = { these, bruch -> state.theseNachKauf(symbol, these, bruch) },
        )
    }
}

// Terminzeile mit Datumsplakette, wie sie unter "Ereignisse" steht.
@Composable
fun EventRow(
    title: String, ts: Long, subtitle: String? = null, onClick: (() -> Unit)? = null,
    // Geschaeftszahlen gegen die Erwartung: Ampel und Abweichung rechts (5.92)
    ueberraschung: de.troetenrepublik.app.engine.Bewertungsblick.Ueberraschung? = null,
    // Kleine Glocke am Termin: oeffnet das Glocken-Blatt des Werts; gefuellt, wenn die Glocke dieser Art an ist
    onGlocke: (() -> Unit)? = null,
    glockeAn: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.size(46.dp).clip(RoundedCornerShape(Radius.logo)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(Fmt.dayOfMonth(ts), style = MaterialTheme.typography.titleSmall)
            Text(Fmt.monthShort(ts).uppercase().trimEnd('.'), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle ?: Fmt.untilDay(ts), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ueberraschung?.let { u ->
            Spacer(Modifier.width(8.dp))
            AmpelPunkt(ampelZeichen(u.ampel), ampelFarbe(u.ampel))
            Spacer(Modifier.width(6.dp))
            Text(Fmt.signedPct(u.abweichung, 1), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.width(8.dp))
        }
        if (onGlocke != null) IconButton(onClick = onGlocke, modifier = Modifier.size(36.dp)) {
            Icon(
                if (glockeAn) Icons.Filled.Notifications else Icons.Outlined.NotificationsNone, S.glockeTermin,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp),
            )
        }
        if (onClick != null) ChevronRight()
    }
}

private fun spreadPct(bid: Double, ask: Double): Double =
    if (bid > 0) (ask - bid) / ((ask + bid) / 2.0) * 100.0 else 0.0

// Umrechnung von der Handelswaehrung in Euro aus Euro- und Handelskurs, 0 ohne Kurs (5.97).
private fun inEuro(q: de.troetenrepublik.app.data.Quote?): Double =
    q?.takeIf { it.nativePrice > 0 && it.price > 0 }?.let { it.price / it.nativePrice } ?: 0.0

// ETFs ohne Finanzen: Ausschuettungen als kompakter Abschnitt unter den Kennzahlen (5.97)
@Composable
private fun EtfDividende(state: AppState, symbol: String, profile: MarketApi.Profile?, kursEuro: Double, inEuro: Double, seite: SeitenWaehrung, stueck: Double?, merker: SeitenMerker) {
    // Im Merker der Seite: Beim Zurueckscrollen steht der Abschnitt sofort da, ohne Neuabruf und ohne Hoehensprung
    var events by gemerkt<List<MarketApi.DividendEvent>?>(merker, "etfdiv/events") { null }
    LaunchedEffect(symbol) { if (events == null) events = state.dividendenVerlauf(symbol) }
    val ev = events ?: return
    val w = de.troetenrepublik.app.engine.DividendenUeberblick.werte(
        ev, profile?.dividendYield ?: 0.0, 0.0, profile?.exDividendDate ?: 0L, profile?.dividendDate ?: 0L, kursEuro,
        jeAktieProfilEuro = (profile?.dividendRate ?: 0.0) * inEuro, land = de.troetenrepublik.app.engine.Alarmarten.land(symbol),
    ) ?: return
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(S.finDivTitel)
        DividendenKompakt(w, seite, stueck)
    }
}
