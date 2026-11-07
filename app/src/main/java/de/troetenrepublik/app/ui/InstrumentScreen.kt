package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import de.troetenrepublik.app.engine.Indikatoren
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import de.troetenrepublik.app.data.Candle
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

// Handelsstatus fuer die Zeile unter dem Kurs. Laeuft der Handel, steht dort nichts; nur bei
// geschlossener Boerse steht, wann sie wieder oeffnet.
fun hoursLabel(status: MarketHours.Status): String {
    val w = status.window ?: return ""
    if (status.open) return ""
    fun t(lt: java.time.LocalTime) = "${lt.hour}:${"%02d".format(lt.minute)}"
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
    val account by state.account.collectAsStateWithLifecycle()
    val valuation by state.valuation.collectAsStateWithLifecycle()
    val companies by state.companies.collectAsStateWithLifecycle()
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val signal = LocalSignal.current
    val clipboard = LocalClipboardManager.current

    val instrument = Symbols.find(symbol)
    val type = Symbols.typeOf(symbol)
    val quote = quotes[symbol]
    // Kurze Namen aus bekannten Werten und Katalog vor dem langen Yahoo-Namen („Gold Dec 26“)
    val name = instrument?.name ?: quote?.name?.ifBlank { null } ?: symbol
    val price = quote?.price ?: 0.0
    val info = companies[symbol]
    // Fremde Betraege per Tippen in Original, Dollar und Euro
    val fremd = rememberGeldanzeige(state, symbol)
    // Kurs, Kennzahlen und Position per Tippen auf den Kurs auch in der Heimatwaehrung
    val fxKurse by state.market.fxKurse.collectAsStateWithLifecycle()
    val heimatWaehrung = Heimatwaehrung.waehrung(quote?.nativeCurrency, fxKurse)
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
    // Eigener Zeitraum: ganze Tage seit 1970, 0 = es gilt ein fester Zeitraum
    var eigenVon by rememberSaveable { mutableLongStateOf(0L) }
    var eigenBis by rememberSaveable { mutableLongStateOf(0L) }
    var zeitraumWahl by remember { mutableStateOf(false) }
    val eigen = eigenVon > 0
    var series by remember(symbol) { mutableStateOf(Series(emptyList())) }
    var scrub by remember { mutableStateOf<Candle?>(null) }
    var vol by remember(symbol) { mutableStateOf(Derivatives.defaultVol(type)) }
    var sheet by remember { mutableStateOf<Side?>(null) }
    var openArticle by remember(symbol) { mutableStateOf(false) }
    var openYahoo by remember(symbol) { mutableStateOf(false) }
    var profile by remember(symbol) { mutableStateOf<MarketApi.Profile?>(null) }
    var news by remember(symbol) { mutableStateOf<List<MarketApi.NewsItem>>(emptyList()) }
    var openNews by remember { mutableStateOf<MarketApi.NewsItem?>(null) }
    // Nachrichten ueber EODHD (5.95) mit eigenem Schluessel
    var eodhdArtikel by remember(symbol) { mutableStateOf<MarketApi.NewsItem?>(null) }
    var eodhdText by remember(symbol) { mutableStateOf<List<String>>(emptyList()) }
    var showAll by remember(symbol) { mutableStateOf(false) }
    var tool by remember(symbol) { mutableStateOf<StockTool?>(null) }
    var planSheet by remember { mutableStateOf(false) }
    var alertSheet by remember { mutableStateOf(false) }
    var notizSheet by remember { mutableStateOf(false) }
    var eventsSheet by remember { mutableStateOf(false) }
    var financialsSheet by remember { mutableStateOf(false) }
    // Strategie-Labor (seit 5.91)
    var labor by remember(symbol) { mutableStateOf(false) }
    var absoluteChange by rememberSaveable { mutableStateOf(false) }
    // Chart-Indikatoren wie in 5.9: alle zehn mit Lesehilfe, je einzeln einschaltbar, Tageskurse ueber zehn Jahre als
    // Grundlage (5.92)
    val indikatoren = setOfNotNull(
        "sma50".takeIf { settings.indSma50 }, "sma200".takeIf { settings.indSma200 }, "bb".takeIf { settings.indBollinger },
        "rsi".takeIf { settings.indRsi }, "macd".takeIf { settings.indMacd }, "stoch".takeIf { settings.indStoch },
        "atr".takeIf { settings.indAtr }, "obv".takeIf { settings.indObv }, "ichimoku".takeIf { settings.indIchimoku },
        "sar".takeIf { settings.indSar },
    )
    // Eingeklappt: erst ein Tipp auf „Indikatoren“ zeigt Namen, Linien und Deutung
    var indOffen by rememberSaveable { mutableStateOf(false) }
    val indikatorScope = rememberCoroutineScope()
    fun indikatorUmschalten(k: String) {
        indikatorScope.launch {
            state.settings.update {
                when (k) {
                    "sma50" -> it.copy(indSma50 = !it.indSma50)
                    "sma200" -> it.copy(indSma200 = !it.indSma200)
                    "bb" -> it.copy(indBollinger = !it.indBollinger)
                    "rsi" -> it.copy(indRsi = !it.indRsi)
                    "stoch" -> it.copy(indStoch = !it.indStoch)
                    "atr" -> it.copy(indAtr = !it.indAtr)
                    "obv" -> it.copy(indObv = !it.indObv)
                    "ichimoku" -> it.copy(indIchimoku = !it.indIchimoku)
                    "sar" -> it.copy(indSar = !it.indSar)
                    else -> it.copy(indMacd = !it.indMacd)
                }
            }
        }
    }
    var tagesreihe by remember(symbol) { mutableStateOf<List<Candle>?>(null) }
    val indGezeigt = indOffen && indikatoren.isNotEmpty()
    LaunchedEffect(symbol, indGezeigt) { if (indGezeigt && tagesreihe == null) tagesreihe = state.dailyCloses(symbol) }
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
    LaunchedEffect(symbol, range, eigenVon, eigenBis) {
        series = Series(emptyList()); scrub = null
        series = if (eigenVon > 0) state.zeitraum(symbol, eigenVon, eigenBis) else state.series(symbol, range)
    }
    LaunchedEffect(symbol) { vol = state.volatilityFor(symbol) }
    LaunchedEffect(symbol) { profile = state.profileFor(symbol) }
    // Google sucht nur mit einem echten Namen, nicht mit dem Kuerzel
    val googleName = instrument?.name ?: quote?.name?.ifBlank { null }.orEmpty()
    LaunchedEffect(symbol, settings.sprache, googleName) { news = state.newsFor(symbol, googleName) }
    LaunchedEffect(symbol) { state.companyInfoFor(symbol, name) }

    val candles = series.candles
    val now = System.currentTimeMillis()
    val domain = if (eigen) null else dayAxis(range, series.dayStart, series.dayEnd, candles)
    val position = valuation.positions.firstOrNull { it.position.key == symbol }
    val reference = if (range == Range.D1 && !eigen) (quote?.prevClose ?: 0.0) else (candles.firstOrNull()?.close ?: 0.0)
    val beschreibung = state.descriptionFor(symbol, profile)
    val description = beschreibung.text
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
        val kgv = q?.takeIf { it.peRatio > 0 }?.let { "${Fmt.num(it.peRatio)}×" }
        val gewinn = p?.takeIf { it.eps != 0.0 }?.let { inSeite(it.eps) }?.let { (v, w) -> Fmt.price(v, Derivatives.currencySymbol(w)) }
        val dividende = p?.let { if (it.dividendYield > 0) Fmt.pct(it.dividendYield) else "—" }
        val schwankung = if (q != null) Fmt.pct(vol * 100) else null
        val hoch = q?.takeIf { it.weekHigh52 > 0 }?.let { kf.preis(it.weekHigh52) }
        val tief = q?.takeIf { it.weekLow52 > 0 }?.let { kf.preis(it.weekLow52) }
        val boersenwert = q?.takeIf { it.marketCap > 0 }?.let { kf.kompakt(it.marketCap) }
        val umsatz = q?.takeIf { it.volume > 0 }?.let { Fmt.compact(it.volume) }
        val vortag = q?.takeIf { it.prevClose > 0 }?.let { kf.preis(it.prevClose) }
        val ter = if (type == de.troetenrepublik.app.data.AssetType.ETF) de.troetenrepublik.app.market.Catalog.loaded()?.etfs?.firstOrNull { it.s == symbol }?.ter else null
        // Kernwerte je Art: Aktien immer gleich aufgebaut (fehlende als Strich), sonst nur vorhandene
        val kern = when (type) {
            de.troetenrepublik.app.data.AssetType.STOCK -> listOf(
                S.peRatio to (kgv ?: "—"), S.dividendYield to (dividende ?: "—"), S.marketCap to (boersenwert ?: "—"),
                S.high52 to (hoch ?: "—"), S.low52 to (tief ?: "—"), S.volatility1y to (schwankung ?: "—"),
            )
            de.troetenrepublik.app.data.AssetType.ETF -> listOfNotNull(
                ter?.let { S.bwTer to Fmt.pct(it, 2) }, dividende?.let { S.dividendYield to it }, boersenwert?.let { S.marketCap to it },
                hoch?.let { S.high52 to it }, tief?.let { S.low52 to it }, schwankung?.let { S.volatility1y to it }, umsatz?.let { S.volume to it },
            ).take(6)
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
        // Alle Kennzahlen: bei Aktien zuerst die des Unternehmens, fehlende als Strich
        val zweite = if (aktie) {
            listOf(
                S.low52 to (tief ?: "—"),
                S.marketCap to (boersenwert ?: "—"),
                S.forwardPe to (p?.takeIf { it.forwardPe > 0 }?.let { "${Fmt.num(it.forwardPe)}×" } ?: "—"),
                S.volume to (umsatz ?: "—"),
            )
        } else emptyList()
        val vorn = if (aktie) {
            listOf(S.enterpriseValue to (ev ?: "—"), S.peRatio to (kgv ?: "—"), S.eps to (gewinn ?: "—"), S.dividendYield to (dividende ?: "—")) + zweite
        } else emptyList()
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
            tief?.takeIf { !aktie }?.let { S.low52 to it },
            boersenwert?.takeIf { !aktie }?.let { S.marketCap to it },
            ev?.takeIf { !aktie }?.let { S.enterpriseValue to it },
            kgv?.takeIf { !aktie }?.let { S.peRatio to it },
            p?.takeIf { it.forwardPe > 0 && !aktie }?.let { S.forwardPe to "${Fmt.num(it.forwardPe)}×" },
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
            q?.takeIf { it.exchange.isNotBlank() }?.let { S.exchange to it.exchange },
        ).filterNotNull()
        kern to alle
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
    val themen = buildList {
        if (aktie || etf || type == de.troetenrepublik.app.data.AssetType.CRYPTO) add(HintergrundThema.TRENDS)
        if (aktie || etf) add(HintergrundThema.HANDELNDE)
        if (aktie) add(HintergrundThema.FUEHRUNG)
        if (aktie) add(HintergrundThema.HALTER)
        if (aktie && de.troetenrepublik.app.market.Fda.betroffen(profile?.industry.orEmpty(), profile?.sector.orEmpty())) add(HintergrundThema.FDA)
        if (aktie && usWert) add(HintergrundThema.BEHOERDEN)
        if (aktie) add(HintergrundThema.LOBBY)
    }
    var hintergrundOffen by rememberSaveable(symbol) { mutableStateOf(false) }
    var alleNews by remember(symbol) { mutableStateOf(false) }
    // Blatt einer Stimmrechtsmeldung von Investegate (5.95)
    var igMeldung by remember(symbol) { mutableStateOf<de.troetenrepublik.app.market.Investegate.Meldung?>(null) }

    // Nach unten wischen schliesst die Seite, sobald die Liste oben steht (auch an der Kopfzeile)
    val liste = rememberLazyListState()
    val dismiss = rememberSwipeDownDismiss(onBack)
    Scaffold(
        modifier = Modifier.sheetDismiss(dismiss) { !liste.canScrollBackward },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1) },
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
                            .clickable { state.toggleWatch(symbol) }
                            .semantics { contentDescription = if (watched) S.removeFromWatchlist else S.addToWatchlist },
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
        LazyColumn(Modifier.fillMaxWidth(), state = liste) {

            item {
                // Der Wert am Fadenkreuz wird erst hier gelesen: Beim Ziehen entsteht nur dieser Kopf neu.
                val selected = scrub
                val shownPrice = selected?.close ?: price
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
                        Text(if (price > 0) kf.preis(shownPrice) else "–", style = MaterialTheme.typography.displayMedium,
                            modifier = Modifier.waehrungWechsel(kursTippen))
                        Spacer(Modifier.width(12.dp))
                        ChangeValue(
                            change = diff,
                            text = if (absoluteChange) kf.geld(abs(diff)) else Fmt.pct(abs(diffPct)),
                            colored = false,
                            style = MaterialTheme.typography.titleSmall,
                            triangleWidth = 10.dp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(Radius.chip))
                                .clickable { absoluteChange = !absoluteChange }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                        )
                    }
                    // Am Fadenkreuz auch die eigenen Geschaefte dieses Tages
                    val amTag = if (selected != null && markenAn) EigeneMarken.amTag(eigene.marken, selected.t, tagVon) else emptyList()
                    Text(
                        // Kein Hinweis auf vor- oder nachboerslichen Handel, gehandelt wird ueber den ganzen Tag
                        selected?.let { s ->
                            (listOf(Fmt.dateTime(s.t)) + amTag.map { S.kcMarke(it.art.name, Fmt.qty(it.stueck), kf.geld(it.betrag), kf.preis(it.kurs)) }).joinToString(" · ")
                        } ?: if (eigen) zeitraumText(eigenVon, eigenBis) else hoursText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            item {
                RangeSelector(
                    ranges = Range.entries,
                    selected = if (eigen) null else range,
                    onSelect = { range = it; eigenVon = 0L; eigenBis = 0L; scrub = null },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                    trailing = { ZeitraumKnopf(aktiv = eigen) { zeitraumWahl = true } },
                )
                val mitIndikatoren = if (eigen) eigenBis - eigenVon >= 20 else range != Range.D1 && range != Range.W1
                val ind = if (mitIndikatoren && indOffen) indikatoren else emptySet()
                val reihen = remember(tagesreihe) { tagesreihe?.let { IndikatorReihen.aus(it) } }
                val faktor = if (reihen != null && candles.isNotEmpty() && reihen.letzterKurs > 0) candles.last().close / reihen.letzterKurs else 1.0
                val ueberlagerung = remember(candles, reihen, ind, faktor) {
                    if (reihen == null) emptyList() else listOfNotNull(
                        if ("bb" in ind) Indikatoren.aufChart(candles, reihen.bbOben, faktor) to BOLLINGER_FARBE.copy(alpha = 0.7f) else null,
                        if ("bb" in ind) Indikatoren.aufChart(candles, reihen.bbUnten, faktor) to BOLLINGER_FARBE.copy(alpha = 0.7f) else null,
                        if ("sma50" in ind) Indikatoren.aufChart(candles, reihen.sma50, faktor) to SMA50_FARBE else null,
                        if ("sma200" in ind) Indikatoren.aufChart(candles, reihen.sma200, faktor) to SMA200_FARBE else null,
                        if ("ichimoku" in ind) Indikatoren.aufChart(candles, reihen.ichimokuA, faktor) to ICHIMOKU_FARBE.copy(alpha = 0.55f) else null,
                        if ("ichimoku" in ind) Indikatoren.aufChart(candles, reihen.ichimokuB, faktor) to ICHIMOKU_FARBE.copy(alpha = 0.55f) else null,
                        if ("ichimoku" in ind) Indikatoren.aufChart(candles, reihen.ichimokuKijun, faktor) to ICHIMOKU_FARBE else null,
                        if ("sar" in ind) Indikatoren.aufChart(candles, reihen.sarUnten, faktor) to SAR_FARBE else null,
                        if ("sar" in ind) Indikatoren.aufChart(candles, reihen.sarOben, faktor) to SAR_FARBE else null,
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
                val kursMarken = if (!markenAn) emptyList() else eigene.marken.map { m ->
                    when (m.art) {
                        EigeneMarken.Art.KAUF -> KursMarke(m.t, signal.up, 1)
                        EigeneMarken.Art.VERKAUF -> KursMarke(m.t, signal.down, -1)
                        EigeneMarken.Art.DIVIDENDE -> KursMarke(m.t, grau, 0)
                    }
                }
                Box(Modifier.fillMaxWidth().height(210.dp)) {
                    PriceChart(
                        candles = candles,
                        modifier = Modifier.fillMaxWidth().height(210.dp),
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
                }
                if (ind.isNotEmpty() && reihen != null) {
                    if ("rsi" in ind) {
                        RsiPanel(
                            candles, remember(candles, reihen) { Indikatoren.aufChart(candles, reihen.rsi) }, MaterialTheme.colorScheme.onSurface,
                            Modifier.fillMaxWidth().height(64.dp).padding(top = 8.dp), domain = domain,
                        )
                    }
                    if ("stoch" in ind) {
                        RsiPanel(
                            candles, remember(candles, reihen) { Indikatoren.aufChart(candles, reihen.stochK) }, STOCH_FARBE,
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
                            candles, remember(candles, reihen) { Indikatoren.aufChart(candles, reihen.atr) }, ATR_FARBE,
                            Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp), domain = domain, beschreibung = "ATR",
                        )
                    }
                    if ("obv" in ind && reihen.obv.isNotEmpty()) {
                        LinienPanel(
                            candles, remember(candles, reihen) { Indikatoren.aufChart(candles, reihen.obv) }, OBV_FARBE,
                            Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp), domain = domain, beschreibung = "OBV",
                        )
                    }
                }
                // Kleiner Knopf „Indikatoren“ links unter dem Chart; aufgeklappt folgen die Namen aller zehn zum Ein- und
                // Ausschalten, rechts der Weg zum erweiterten Chart. Bei 1T und 1W zeichnet der Chart keine Indikatoren;
                // die Namen stehen dann gedaempft mit Hinweis da
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    IndikatorKnopf(indOffen, { indOffen = !indOffen })
                    Spacer(Modifier.weight(1f))
                    Row(
                        Modifier.clip(RoundedCornerShape(Radius.chip)).clickable { onOpenRoute("chart/$symbol") }
                            .heightIn(min = 48.dp).padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(S.chartProfi, style = MaterialTheme.typography.labelSmall, color = grau, maxLines = 1)
                    }
                }
                if (indOffen) IndikatorNamen(indikatoren, ::indikatorUmschalten, Modifier.fillMaxWidth().padding(horizontal = 14.dp), imChart = mitIndikatoren)
                if (indOffen && indikatoren.isEmpty()) Text(
                    S.p1IndWaehlen, style = MaterialTheme.typography.bodySmall, color = grau,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 6.dp),
                )
                if (indGezeigt) {
                    if (reihen == null) Text(
                        S.toolLoading, style = MaterialTheme.typography.bodySmall, color = grau,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    ) else IndikatorDeutung(reihen.deutungen, indikatoren, Modifier.padding(horizontal = 20.dp).padding(bottom = 6.dp), bilanzen = bilanzen)
                }
            }

            // Deine Position (falls gehalten) mit Einstand, Haltedauer und Sparplan, darunter eine vorhandene Notiz und
            // offene Orders dieses Werts. Preisalarm und Notiz stehen seit 5.92 als Symbole in der Kopfzeile, der
            // Sparplan ohne Position wieder als schwebender Knopf neben Kaufen.
            item(key = "position-aktionen") {
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
                    if (type.directlyTradable) ActionLineWide(S.savingsPlan) { planSheet = true }
                }
                NotizZeile(account.notizen[symbol].orEmpty()) { notizSheet = true }
                OffeneOrdersKurz(
                    account.orders.filter { it.status == de.troetenrepublik.app.data.OrderStatus.OPEN && it.symbol == symbol && it.derivative == null },
                ) { onOpenRoute("orders") }
            }

            // Anlage-These mit Prueftermin (seit 5.91)
            account.thesen[symbol]?.let { t ->
                item(key = "these") { TheseKarte(state, symbol, t, gehalten = position != null) { sheet = Side.SELL } }
            }

            if (description.isNotBlank()) {
                item(key = "beschreibung") {
                    // Ohne Pfeil an der Ueberschrift; ein kleiner Pfeil am Ende des gekuerzten Texts zeigt, dass er
                    // aufklappt (5.92)
                    SectionHeader(S.information, onClick = { showAll = !showAll })
                    GekuerzterText(description, showAll) { showAll = !showAll }
                    if (!fromWiki) {
                        // „Quelle: Yahoo Finance“ oeffnet das Firmenprofil wie eine Nachricht: der Text steht
                        // sofort da, „Originalseite anzeigen“ laedt die Profilseite, „Im Browser öffnen“ den Browser
                        QuellenZeile(S.sourceYahoo) { openYahoo = true }
                    } else if (beschreibung.artikel.isNotBlank()) {
                        // „Quelle: Wikipedia“ oeffnet den Artikel in der App, wie eine Nachricht
                        QuellenZeile(S.sourceWikipedia) { openArticle = true }
                    } else QuellenZeile(if (fromWiki) S.sourceWikipedia else S.sourceYahoo)
                }
            }

            // ETFs: was drinsteckt und wie viel davon im eigenen Depot landet
            if (type == de.troetenrepublik.app.data.AssetType.ETF) item(key = "etf-durchschau") { EtfDurchschauKarte(state, symbol) }
            // Tracking Difference (5.94): ohne freie Indexrenditen nicht selbst rechenbar, daher der Weg zu extraETF
            if (type == de.troetenrepublik.app.data.AssetType.ETF && isin.length == 12) item(key = "etf-td") {
                val uri = appLinks()
                Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(S.etfTdHinweis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
                    QuellenZeile(S.etfTdLink, onClick = { runCatching { uri.openUri("https://extraetf.com/de/etf-profile/$isin?tab=td") } })
                }
            }

            // Rohstoffe und Rohstoff-ETCs: was es ist, was den Preis bewegt, wie man anlegt
            de.troetenrepublik.app.engine.Rohstoffe.schluessel(symbol)?.let { k ->
                item(key = "rohstoff") { RohstoffKarte(symbol, k) }
            }

            // Fruehe Signale (5.92): Lager bei Oel und Gas, Duerre bei Getreide, Fluggaeste bei Fluglinien, Oellager bei
            // Oel- und Gasfirmen; ohne passendes Signal leer
            item(key = "fruehsignale") { FruehSignalKarte(state, symbol, profile?.industry.orEmpty(), onOpenRoute) }

            // Bewertung und Kennzahlen (5.92): Kernwerte, bei Aktien Ampeln fuer Bewertung, Bilanz und Wachstum, alle
            // Kennzahlen im Blatt und der Vergleich mit aehnlichen Aktien
            item(key = "kennzahlen") {
                BewertungUndKennzahlen(
                    state, symbol, name, quote, profile, aktie, kennzahlen.first, seite, kennzahlTipp, kennzahlAktion,
                    onAlle = { alleKennzahlen = true }, onFinanzen = { financialsSheet = true },
                    onVergleich = { onOpenRoute("kennzahlvergleich/$symbol") },
                )
            }

            // ETFs: Ausschuettungen kompakt unter den Kennzahlen (5.97), ohne Ausschuettung leer
            if (etf) item(key = "etf-dividende") { EtfDividende(state, symbol, profile, quote?.price ?: 0.0, inEuro(quote), seite, position?.takeIf { it.position.derivative == null }?.qty) }

            // Chancen und Risiken (5.92): Analysten, Stimmung aus Optionen und Leerverkaeufen, was dafuer und was dagegen
            // spricht; ein Tipp auf einen Punkt oeffnet die Einzelheiten
            if (aktie || etf) item(key = "chancen") {
                ChancenUndRisiken(
                    state, symbol, isin, name, quote, profile, news, aktie = aktie, usWert = usWert, euInsider = euInsider,
                    mitOptionen = usWert || aktie, seite = seite, ziel = ziel, onNews = { openNews = it }, onOpenRoute = onOpenRoute,
                )
            }

            // Was Investoren schreiben (5.92): Auszuege aus Briefen von Fondsmanagern mit Einordnung des Eigeninteresses;
            // ohne Fundstelle leer (bei US-Aktien nur der Link zur Suche)
            if (aktie) item(key = "investorenbriefe") {
                InvestorenBriefeKarte(state, symbol, profile?.longName?.ifBlank { null } ?: name, usWert, onOpenRoute)
            }

            // Ereignisse: naechste Termine mit Erwartung, letzte Zahlen gegen die Erwartung (5.92)
            profile?.let { p -> item(key = "ereignisse") { EreignisseAbschnitt(p, seite) { eventsSheet = true } } }

            // News: die drei neuesten, der Pfeil darunter klappt den Rest auf der Seite auf, duenne Linien dazwischen (5.92)
            val newsListe = news.distinctBy { it.id }
            if (newsListe.isNotEmpty()) {
                item(key = "news-kopf") { SectionHeader(S.news) }
                itemsIndexed(if (alleNews) newsListe else newsListe.take(3), key = { _, n -> n.id }) { i, n ->
                    if (i > 0) Trennlinie()
                    Column(
                        Modifier.fillMaxWidth().clickable { openNews = n }.padding(horizontal = 20.dp, vertical = 10.dp),
                    ) {
                        Text(n.title, style = MaterialTheme.typography.titleSmall.schlagzeile(), lineHeight = 22.sp)
                        PunktText(
                            listOfNotNull(n.published.takeIf { it > 0 }?.let { Fmt.relative(it) }, n.source.takeIf { it.isNotBlank() }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (newsListe.size > 3) item(key = "news-mehr") { ListenPfeil(alleNews) { alleNews = !alleNews } }
            }

            // Nachrichten ueber EODHD (5.95): eigener Schluessel, der Abschnitt laedt erst beim Aufklappen
            if (settings.keyEodhd.isNotBlank() && NachrichtenApis.eodhdTicker(symbol) != null) item(key = "eodhd") {
                EodhdAbschnitt(state, symbol) { n, absaetze -> eodhdArtikel = n; eodhdText = absaetze }
            }

            // Derivate: Long/Short-Balken aus dem Optionsumsatz, ohne Daten die Pillen (5.93)
            if (type.directlyTradable) item(key = "hebel") {
                HebelKarte(
                    state, symbol, mitOptionen = usWert || aktie,
                    onLong = { onOpenRoute("derivate/L/$symbol") }, onShort = { onOpenRoute("derivate/S/$symbol") },
                )
            }

            // Hintergrund: eingeklappt, die Karten laden erst beim Aufklappen (5.91)
            if (themen.isNotEmpty()) {
                item(key = "hintergrund") {
                    AufklappKopf(S.bwHintergrund, S.bwHintergrundUnter(themen.map { S.bwThema(it.name) }), hintergrundOffen) { hintergrundOffen = !hintergrundOffen }
                }
                if (hintergrundOffen) {
                    // Trends zum Wert: Reddit, Yahoo Finance, Wikipedia; nur mit Daten
                    if (HintergrundThema.TRENDS in themen) item(key = "wert-trends") { WertTrends(state, symbol, name, type, profile?.sector, merker = merker) }
                    // Wer den Wert handelt: Personen aus Kongress, Senat und Regierung und Investoren mit ihren Geschaeften
                    if (HintergrundThema.HANDELNDE in themen) item(key = "handelnde") { HandelndeAbschnitt(state, symbol, onOpenRoute, usWert = usWert, merker = merker) }
                    // Insider: BaFin (Deutschland), Register weiterer Laender, SEC Form 4 und Form 144/13D (USA)
                    if (HintergrundThema.FUEHRUNG in themen) {
                        item(key = "bafin") { BafinKarte(state, symbol, isin, name, merker = merker) }
                        if (euInsider) item(key = "insider-eu") { EuInsiderKarte(state, symbol, isin, name, merker = merker) }
                        if (usWert) {
                            item(key = "sec") { SecKarte(state, symbol, merker = merker) }
                            item(key = "sec-formen") { SecFormenKarte(state, symbol, merker = merker) }
                        }
                    }
                    // Groesste institutionelle Halter (5.93)
                    if (HintergrundThema.HALTER in themen) item(key = "halter") { HalterKarte(state, symbol, quote?.nativeCurrency.orEmpty(), seite, merker = merker) }
                    // Stimmrechtsmeldungen (TR-1 nach DTR 5) britischer Firmen ueber Investegate (5.95)
                    if (HintergrundThema.HALTER in themen) de.troetenrepublik.app.market.Investegate.ticker(symbol)?.let { k -> item(key = "stimmrechte-gb") { StimmrechteGbKarte(state, k) { igMeldung = it } } }
                    // Zulassungen und Rueckrufe der FDA bei Pharma-, Biotech- und Medizintechnik-Aktien
                    if (HintergrundThema.FDA in themen) item(key = "fda") { FdaKarte(state, symbol, profile?.longName?.ifBlank { null } ?: name) }
                    // Lobbying, Gesetze, Auftraege, Spenden, Entlassungen und Patente bei US-Aktien
                    if (HintergrundThema.BEHOERDEN in themen) item(key = "behoerden") { BehoerdenKarte(state, symbol, name, onOpenRoute) }
                    // Lobbyarbeit in Europa (EU-Transparenzregister, Bundestag, HATVP)
                    if (HintergrundThema.LOBBY in themen) item(key = "lobby-welt") { LobbyWeltKarte(state, symbol) }
                }
            }

            // Werkzeuge zur Analyse, darin aufklappbar „Wie tief kann es fallen“ (laedt erst beim Aufklappen), zuletzt
            // das Strategie-Labor
            item(key = "werkzeuge") {
                StockToolsSection(toolsFor(type, position != null && position.position.derivative == null, profile)) { tool = it }
            }
            item(key = "rendite-risiko") { RenditeRisiko(state, symbol, profile?.country) }
            item(key = "labor") { NavRow(S.slTitel, S.slUntertitel) { labor = true } }

            item { Spacer(Modifier.height(110.dp)) }
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
                    FloatingPill(label = S.savingsPlan, icon = Icons.Filled.Autorenew, filled = true, modifier = Modifier.weight(1f)) { planSheet = true }
                }
                if (type.directlyTradable) {
                    FloatingPill(label = S.buy, plus = true, filled = true, enabled = price > 0, modifier = Modifier.weight(1f)) { sheet = Side.BUY }
                }
            }
        }
      }
    }


    if (alertSheet) AlertsSheet(state, symbol, name, onDismiss = { alertSheet = false })
    if (notizSheet) NotizSheet(state, symbol, name, onDismiss = { notizSheet = false })
    if (zeitraumWahl) ZeitraumDialog(eigenVon, eigenBis, onDismiss = { zeitraumWahl = false }) { von, bis ->
        eigenVon = minOf(von, bis); eigenBis = maxOf(von, bis); scrub = null; zeitraumWahl = false
    }
    tool?.let { t -> StockToolSheet(state, t, symbol, name, profile, onDismiss = { tool = null }) }
    if (alleKennzahlen) {
        // Bei Aktien zuerst die weiteren Kennzahlen in Gruppen, danach Handel und Stammdaten (5.92)
        val gruppen = profile?.takeIf { aktie }?.let { p -> mehrKennzahlen(state, symbol, quote, p, seite).map { (k, z) -> S.bkGruppe(k) to z } }.orEmpty()
        AlleKennzahlenBlatt(
            name, gruppen + ((if (gruppen.isEmpty()) null else S.bkGruppe("HANDEL")) to kennzahlen.second), kennzahlTipp, kennzahlAktion,
        ) { alleKennzahlen = false }
    }
    if (labor) StrategieLaborSheet(state, symbol, name, onDismiss = { labor = false })

    if (planSheet) PlanSheet(state, symbol, name, onDismiss = { planSheet = false })

    if (eventsSheet) EventsSheet(state, symbol, name, profile, seite, onDismiss = { eventsSheet = false })

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
                    jeAktieProfilEuro = it.dividendRate * inEuro(quote),
                )
            }
        }
        FinancialsSheet(name, it, seite, guv = guv, jahre = jahre, dividende = divWerte,
            stueck = position?.takeIf { p -> p.position.derivative == null }?.qty, onDismiss = { financialsSheet = false })
    }

    openNews?.let { NewsSheet(it, onDismiss = { openNews = null }) }

    // Nachricht aus EODHD mit dem schon geholten Text
    eodhdArtikel?.let { n -> NewsSheet(n, onDismiss = { eodhdArtikel = null }, text = eodhdText.ifEmpty { null }) }

    // Stimmrechtsmeldung von Investegate mit dem schon geholten Bogen
    igMeldung?.let { m -> InvestegateMeldungBlatt(state, m, onDismiss = { igMeldung = null }) }

    // Firmenprofil bei Yahoo Finance, Quelle der englischen Beschreibung
    if (openYahoo && description.isNotBlank()) {
        NewsSheet(
            MarketApi.NewsItem(title = name, link = "https://finance.yahoo.com/quote/${java.net.URLEncoder.encode(state.market.dataSymbol(symbol), "UTF-8")}/profile/", published = 0L, source = "Yahoo Finance"),
            onDismiss = { openYahoo = false },
            actionsTop = true,
            text = description.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() },
        )
    }

    // Wikipedia-Artikel zur Beschreibung, gelesen wie eine Nachricht; „Im Browser oeffnen“ steht oben
    if (openArticle && beschreibung.artikel.isNotBlank()) {
        NewsSheet(
            MarketApi.NewsItem(title = name, link = beschreibung.artikel, published = 0L, source = "Wikipedia"),
            onDismiss = { openArticle = false },
            actionsTop = true,
            wikipedia = true,
        )
    }

    sheet?.let { side ->
        TradeSheet(
            title = name,
            subtitle = typeLabel(type),
            price = state.bidAsk(symbol).let { if (side == Side.BUY) it.second else it.first },
            side = side,
            cash = account.cash,
            ownedQty = position?.qty ?: 0.0,
            allowFractional = true,
            derivative = null,
            marketOpen = marketOpen,
            hoursHint = hoursText,
            onDismiss = { sheet = null },
            onMarket = { qty, note -> if (side == Side.BUY) state.buy(symbol, name, symbol, qty, note = note) else state.sell(symbol, qty, note) },
            onLimit = { qty, limit, type2, validity, note, trail -> state.placeOrder(symbol, name, symbol, side, type2, qty, limit, validity = validity, note = note, trailPct = trail) },
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
        if (onClick != null) ChevronRight(height = 13.dp)
    }
}

private fun spreadPct(bid: Double, ask: Double): Double =
    if (bid > 0) (ask - bid) / ((ask + bid) / 2.0) * 100.0 else 0.0

// Umrechnung von der Handelswaehrung in Euro aus Euro- und Handelskurs, 0 ohne Kurs (5.97).
private fun inEuro(q: de.troetenrepublik.app.data.Quote?): Double =
    q?.takeIf { it.nativePrice > 0 && it.price > 0 }?.let { it.price / it.nativePrice } ?: 0.0

// ETFs ohne Finanzen: Ausschuettungen als kompakter Abschnitt unter den Kennzahlen (5.97)
@Composable
private fun EtfDividende(state: AppState, symbol: String, profile: MarketApi.Profile?, kursEuro: Double, inEuro: Double, seite: SeitenWaehrung, stueck: Double?) {
    var events by remember(symbol) { mutableStateOf<List<MarketApi.DividendEvent>?>(null) }
    LaunchedEffect(symbol) { events = state.dividendenVerlauf(symbol) }
    val ev = events ?: return
    val w = de.troetenrepublik.app.engine.DividendenUeberblick.werte(
        ev, profile?.dividendYield ?: 0.0, 0.0, profile?.exDividendDate ?: 0L, profile?.dividendDate ?: 0L, kursEuro,
        jeAktieProfilEuro = (profile?.dividendRate ?: 0.0) * inEuro,
    ) ?: return
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(S.finDivTitel)
        DividendenKompakt(w, seite, stueck)
    }
}
