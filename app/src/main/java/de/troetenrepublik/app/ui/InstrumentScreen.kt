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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
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
import de.troetenrepublik.app.engine.ExchangeRates
import de.troetenrepublik.app.engine.MarketHours
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.Symbols
import kotlin.math.abs
import kotlinx.coroutines.launch
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
    var optionVolumes by remember(symbol) { mutableStateOf<MarketApi.OptionVolumes?>(null) }
    LaunchedEffect(symbol) { optionVolumes = state.market.optionVolumes(symbol) }
    var sheet by remember { mutableStateOf<Side?>(null) }
    var openArticle by remember(symbol) { mutableStateOf(false) }
    var openYahoo by remember(symbol) { mutableStateOf(false) }
    var profile by remember(symbol) { mutableStateOf<MarketApi.Profile?>(null) }
    var news by remember(symbol) { mutableStateOf<List<MarketApi.NewsItem>>(emptyList()) }
    var openNews by remember { mutableStateOf<MarketApi.NewsItem?>(null) }
    var showAll by remember(symbol) { mutableStateOf(false) }
    var tool by remember(symbol) { mutableStateOf<StockTool?>(null) }
    var planSheet by remember { mutableStateOf(false) }
    var alertSheet by remember { mutableStateOf(false) }
    var notizSheet by remember { mutableStateOf(false) }
    var eventsSheet by remember { mutableStateOf(false) }
    var financialsSheet by remember { mutableStateOf(false) }
    var absoluteChange by rememberSaveable { mutableStateOf(false) }
    // Chart-Indikatoren, Tageskurse ueber zehn Jahre als Grundlage
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
    val marketOpen = state.marketOpen(symbol, null)
    val hoursText = hoursLabel(MarketHours.status(type, false))

    LaunchedEffect(symbol) { state.market.track(listOf(symbol)); state.market.refreshOne(symbol) }
    LaunchedEffect(symbol, range, eigenVon, eigenBis) {
        series = Series(emptyList()); scrub = null
        series = if (eigenVon > 0) state.zeitraum(symbol, eigenVon, eigenBis) else state.series(symbol, range)
    }
    LaunchedEffect(symbol) { vol = state.volatilityFor(symbol) }
    LaunchedEffect(symbol) { profile = state.profileFor(symbol) }
    LaunchedEffect(symbol, settings.sprache) { news = state.newsFor(symbol) }
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
                    val watched = symbol in account.watchlist
                    Box(
                        Modifier
                            .padding(end = 12.dp)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { state.toggleWatch(symbol) }
                            .semantics { contentDescription = if (watched) S.removeFromWatchlist else S.addToWatchlist },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (watched) CheckMark(size = 15.dp)
                        else PlusGlyph(size = 15.dp, color = MaterialTheme.colorScheme.onSurface, stroke = 2.dp)
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
                    Text(
                        // Kein Hinweis auf vor- oder nachboerslichen Handel, gehandelt wird ueber den ganzen Tag
                        selected?.let { Fmt.dateTime(it.t) } ?: if (eigen) zeitraumText(eigenVon, eigenBis) else hoursText,
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
                Box(Modifier.fillMaxWidth().height(210.dp)) {
                    PriceChart(
                        candles = candles,
                        modifier = Modifier.fillMaxWidth().height(210.dp),
                        baseline = if (reference > 0) reference else null,
                        onScrub = { scrub = it },
                        domain = domain,
                        overlays = ueberlagerung,
                    )
                    ChartCrosshair(
                        candles = candles,
                        selected = scrub,
                        color = signal.line,
                        baseline = if (reference > 0) reference else null,
                        modifier = Modifier.fillMaxWidth().height(210.dp),
                        domain = domain,
                        overlays = ueberlagerung,
                    )
                }
                if (ind.isNotEmpty()) {
                    if ("rsi" in ind && reihen != null) {
                        RsiPanel(
                            candles, remember(candles, reihen) { Indikatoren.aufChart(candles, reihen.rsi) }, MaterialTheme.colorScheme.onSurface,
                            Modifier.fillMaxWidth().height(64.dp).padding(top = 8.dp), domain = domain,
                        )
                    }
                    if ("stoch" in ind && reihen != null) {
                        RsiPanel(
                            candles, remember(candles, reihen) { Indikatoren.aufChart(candles, reihen.stochK) }, STOCH_FARBE,
                            Modifier.fillMaxWidth().height(64.dp).padding(top = 8.dp), domain = domain, grenzen = listOf(20.0, 80.0),
                        )
                    }
                    if ("macd" in ind && reihen != null) {
                        MacdPanel(
                            candles,
                            remember(candles, reihen) { Indikatoren.aufChart(candles, reihen.macd) },
                            remember(candles, reihen) { Indikatoren.aufChart(candles, reihen.macdSignal) },
                            remember(candles, reihen) { Indikatoren.aufChart(candles, reihen.macdHist) },
                            Modifier.fillMaxWidth().height(64.dp).padding(top = 8.dp), domain = domain,
                        )
                    }
                    if ("atr" in ind && reihen != null) {
                        LinienPanel(
                            candles, remember(candles, reihen) { Indikatoren.aufChart(candles, reihen.atr) }, ATR_FARBE,
                            Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp), domain = domain, beschreibung = "ATR",
                        )
                    }
                    if ("obv" in ind && reihen != null && reihen.obv.isNotEmpty()) {
                        LinienPanel(
                            candles, remember(candles, reihen) { Indikatoren.aufChart(candles, reihen.obv) }, OBV_FARBE,
                            Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp), domain = domain, beschreibung = "OBV",
                        )
                    }
                }
                // Kleiner Knopf „Indikatoren“ links unter dem Chart; die Deutung gilt fuer jeden Zeitraum. Rechts
                // daneben der Weg zum erweiterten Chart
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp), verticalAlignment = Alignment.Top) {
                    IndikatorKnopf(indOffen, { indOffen = !indOffen }, indikatoren, ::indikatorUmschalten, Modifier.weight(1f))
                    Text(
                        S.chartProfi, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f), maxLines = 1,
                        modifier = Modifier.clip(RoundedCornerShape(Radius.chip)).clickable { onOpenRoute("chart/$symbol") }.padding(horizontal = 4.dp, vertical = 6.dp),
                    )
                }
                if (indGezeigt) {
                    if (reihen == null) Text(
                        S.toolLoading, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    ) else IndikatorDeutung(reihen.deutungen, indikatoren, Modifier.padding(horizontal = 20.dp).padding(bottom = 6.dp))
                }
            }

            if (position != null) {
                item {
                    SectionHeader(S.yourPosition)
                    StatPages(
                        statPages(
                            listOf(
                                S.quantity to "${Fmt.qty(position.qty)} ${S.pieces}",
                                S.avgPrice to kf.preis(position.position.avgPrice),
                                S.value to kf.geld(position.value),
                                S.result to "${kf.geldMitVorzeichen(position.profit)}  ${Fmt.signedPct(position.profitPct)}",
                            )
                        )
                    )
                    // Unten steht bei gehaltener Position Verkaufen statt Sparplan: der Sparplan bleibt hier erreichbar
                    if (type.directlyTradable) ActionLineWide(S.savingsPlan) { planSheet = true }
                }
            }

            if (description.isNotBlank()) {
                item {
                    SectionHeader(S.information, chevron = true, onClick = { showAll = !showAll })
                    Text(
                        description,
                        modifier = Modifier.fillMaxWidth().clickable { showAll = !showAll }.padding(horizontal = 20.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = if (showAll) 200 else 3,
                        overflow = TextOverflow.Clip,
                        lineHeight = 24.sp,
                    )
                    if (!fromWiki) {
                        // „Quelle: Yahoo Finance“ oeffnet das Firmenprofil wie eine Nachricht: der Text steht
                        // sofort da, „Originalseite anzeigen“ laedt die Profilseite, „Im Browser öffnen“ den Browser
                        Row(
                            Modifier.clickable { openYahoo = true }.padding(horizontal = 20.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(S.sourceYahoo, style = MaterialTheme.typography.labelSmall, color = signal.link)
                            Spacer(Modifier.width(5.dp))
                            ChevronRight(height = 10.dp, color = signal.link)
                        }
                    } else if (beschreibung.artikel.isNotBlank()) {
                        // „Quelle: Wikipedia“ oeffnet den Artikel in der App, wie eine Nachricht
                        Row(
                            Modifier.clickable { openArticle = true }.padding(horizontal = 20.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(S.sourceWikipedia, style = MaterialTheme.typography.labelSmall, color = signal.link)
                            Spacer(Modifier.width(5.dp))
                            ChevronRight(height = 10.dp, color = signal.link)
                        }
                    } else Text(
                        if (fromWiki) S.sourceWikipedia else S.sourceYahoo,
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                }
            }

            item {
                val (geld, brief) = state.bidAsk(symbol)
                val q = quote
                val p = profile
                val currency = p?.financialCurrency?.ifBlank { q?.nativeCurrency.orEmpty() }.orEmpty()
                SectionHeader(S.keyStats)
                val aktie = type == de.troetenrepublik.app.data.AssetType.STOCK
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
                // Bei Aktien stehen die Kennzahlen des Unternehmens auf der ersten Seite. Fehlende stehen als Strich da,
                // damit die Seite immer gleich aufgebaut ist
                // Die zweite Seite bei Aktien: Jahrestief, Boersenwert, erwartetes KGV und Volumen
                val zweite = if (aktie) {
                    listOf(
                        S.low52 to (q?.takeIf { it.weekLow52 > 0 }?.let { kf.preis(it.weekLow52) } ?: "—"),
                        S.marketCap to (q?.takeIf { it.marketCap > 0 }?.let { kf.kompakt(it.marketCap) } ?: "—"),
                        S.forwardPe to (p?.takeIf { it.forwardPe > 0 }?.let { "${Fmt.num(it.forwardPe)}×" } ?: "—"),
                        S.volume to (q?.takeIf { it.volume > 0 }?.let { Fmt.compact(it.volume) } ?: "—"),
                    )
                } else emptyList()
                val vorn = if (aktie) {
                    listOf(S.enterpriseValue to (ev ?: "—"), S.peRatio to (kgv ?: "—"), S.eps to (gewinn ?: "—"), S.dividendYield to (dividende ?: "—")) + zweite
                } else emptyList()
                val rows = vorn + listOf(
                    if (price > 0) S.bid to kf.preis(geld) else null,
                    if (price > 0) S.ask to kf.preis(brief) else null,
                    if (price > 0) S.spread to Fmt.pct(spreadPct(geld, brief)) else null,
                    q?.takeIf { it.open > 0 }?.let { S.openPrice to kf.preis(it.open) },
                    q?.takeIf { it.dayHigh > 0 }?.let { S.dayHigh to kf.preis(it.dayHigh) },
                    q?.takeIf { it.dayLow > 0 }?.let { S.dayLow to kf.preis(it.dayLow) },
                    q?.takeIf { it.prevClose > 0 }?.let { S.prevClose to kf.preis(it.prevClose) },
                    q?.takeIf { it.weekHigh52 > 0 }?.let { S.high52 to kf.preis(it.weekHigh52) },
                    q?.takeIf { it.weekLow52 > 0 && !aktie }?.let { S.low52 to kf.preis(it.weekLow52) },
                    q?.takeIf { it.marketCap > 0 && !aktie }?.let { S.marketCap to kf.kompakt(it.marketCap) },
                    ev?.takeIf { !aktie }?.let { S.enterpriseValue to it },
                    kgv?.takeIf { !aktie }?.let { S.peRatio to it },
                    p?.takeIf { it.forwardPe > 0 && !aktie }?.let { S.forwardPe to "${Fmt.num(it.forwardPe)}×" },
                    gewinn?.takeIf { !aktie }?.let { S.eps to it },
                    dividende?.takeIf { !aktie }?.let { S.dividendYield to it },
                    q?.takeIf { it.volume > 0 && !aktie }?.let { S.volume to Fmt.compact(it.volume) },
                    q?.takeIf { it.nativeCurrency != "EUR" }?.let { S.priceIn(it.nativeCurrency) to fremd.preis(it.nativePrice, it.nativeCurrency) },
                    if (q != null) S.volatility1y to Fmt.pct(vol * 100) else null,
                    p?.takeIf { it.beta != 0.0 }?.let { S.beta to Fmt.num(it.beta) },
                    if (isin.isNotBlank()) S.isin to isin else null,
                    S.kind to typeLabel(type),
                    p?.takeIf { it.sector.isNotBlank() }?.let { S.sector to S.sectorName(it.sector) }
                        ?: instrument?.takeIf { it.sector.isNotBlank() && type != de.troetenrepublik.app.data.AssetType.STOCK }?.let { S.sector to S.sectorName(it.sector) },
                    p?.takeIf { it.industry.isNotBlank() }?.let { S.industry to S.sectorName(it.industry) }
                        ?: instrument?.takeIf { it.sector.isNotBlank() && type == de.troetenrepublik.app.data.AssetType.STOCK && p?.industry.isNullOrBlank() }?.let { S.industry to S.sectorName(it.sector) },
                    p?.takeIf { it.country.isNotBlank() }?.let { S.country to S.countryName(it.country) },
                    p?.takeIf { it.employees > 0 }?.let { S.employees to Fmt.num(it.employees.toDouble(), 0) },
                    q?.takeIf { it.exchange.isNotBlank() }?.let { S.exchange to it.exchange },
                )
                // Ein Tipp erklaert die Kennzahl; der Kurs in Fremdwaehrung wechselt direkt die Waehrung, die ISIN laesst sich
                // aus dem Erklaerfenster kopieren
                StatPages(statPages(rows), onRowClick = { label ->
                    q?.let { if (label == S.priceIn(it.nativeCurrency)) fremd.weiter(it.nativeCurrency) }
                }, aktion = { label ->
                    if (label == S.isin && isin.isNotBlank()) S.isinKopieren to {
                        clipboard.setText(AnnotatedString(isin))
                        state.showToast(S.isinCopied)
                    } else null
                })
                if (isin.isNotBlank()) {
                    Text(
                        "${S.isin}: $isin",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { clipboard.setText(AnnotatedString(isin)); state.showToast(S.isinCopied) }.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                }
            }

            // Rendite und Risiko aus zehn Jahren Kursen, Beta und Korrelation zum Index der Heimatboerse
            item(key = "rendite-risiko") { RenditeRisiko(state, symbol, profile?.country) }

            item {
                Row(
                    Modifier.fillMaxWidth().clickable { alertSheet = true }.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(S.priceAlerts, style = MaterialTheme.typography.titleSmall, color = signal.link)
                    Spacer(Modifier.width(6.dp))
                    ChevronRight(height = 13.dp, color = signal.link)
                    Spacer(Modifier.weight(1f))
                    val n = account.alerts.count { it.symbol == symbol }
                    if (n > 0) Text(S.nActive(n), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Eigene Notiz zum Wert, etwa die Kaufbegruendung
            item(key = "notiz") {
                val notiz = account.notizen[symbol].orEmpty()
                Column(Modifier.fillMaxWidth().clickable { notizSheet = true }.padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (notiz.isBlank()) S.notizHinzufuegen else S.notiz, style = MaterialTheme.typography.titleSmall, color = signal.link)
                        Spacer(Modifier.width(6.dp))
                        ChevronRight(height = 13.dp, color = signal.link)
                    }
                    if (notiz.isNotBlank()) {
                        Text(notiz, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }

            if (news.isNotEmpty()) {
                item { SectionHeader(S.news) }
                items(news.distinctBy { it.id }.take(6), key = { it.id }) { n ->
                    Column(
                        Modifier.fillMaxWidth().clickable { openNews = n }.padding(horizontal = 20.dp, vertical = 10.dp),
                    ) {
                        Text(n.title, style = MaterialTheme.typography.titleSmall, lineHeight = 22.sp)
                        Text(
                            listOfNotNull(n.published.takeIf { it > 0 }?.let { Fmt.relative(it) }, n.source.takeIf { it.isNotBlank() }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Bull- und Bear-Case aus Schlagzeilen und Kennzahlen (seit 5.7)
            if (type == de.troetenrepublik.app.data.AssetType.STOCK) {
                item(key = "bullbear") { BullBearKarte(state, symbol, isin, quote, profile, news) { openNews = it } }
            }

            profile?.let { p ->
                val upcoming = listOfNotNull(
                    p.earningsDate.takeIf { it > 0 }?.let { S.eventEarnings to it },
                    p.exDividendDate.takeIf { it > now - 86_400_000L }?.let { S.eventExDividend to it },
                    p.dividendDate.takeIf { it > now - 86_400_000L }?.let { S.eventDividendPayment to it },
                ).sortedBy { it.second }
                if (upcoming.isNotEmpty() || p.epsHistory.isNotEmpty()) {
                    item {
                        SectionHeader(S.events, chevron = true, onClick = { eventsSheet = true })
                        upcoming.take(2).forEach { (title, ts) -> EventRow(title, ts, onClick = { eventsSheet = true }) }
                    }
                }
            }

            profile?.takeIf { it.targetPrice > 0 || it.recommendation.isNotBlank() }?.let { p ->
                item {
                    SectionHeader(S.insights)
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(Radius.card))
                            .background(MaterialTheme.colorScheme.surfaceContainer).padding(18.dp),
                    ) {
                        Text(S.analysts, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        val zielWaehrung = quote?.nativeCurrency ?: ""
                        Text(
                            buildString {
                                append(S.recommendation(p.recommendation))
                                if (p.analystCount > 0) append(S.fromEstimates(p.analystCount))
                                if (p.targetPrice > 0) append(S.targetPrice(ziel.preis(p.targetPrice, zielWaehrung)))
                            },
                            style = MaterialTheme.typography.titleMedium,
                            lineHeight = 26.sp,
                            modifier = if (p.targetPrice > 0 && zielWaehrung.isNotBlank()) Modifier.clickable(interactionSource = null, indication = null, onClickLabel = S.switchCurrency) { ziel.weiter(zielWaehrung) } else Modifier,
                        )
                        if (p.targetPrice > 0 && price > 0) {
                            Spacer(Modifier.height(12.dp))
                            val nativePrice = quote?.nativePrice ?: 0.0
                            val potential = if (nativePrice > 0) (p.targetPrice / nativePrice - 1.0) * 100.0 else 0.0
                            MetricValue(potential, "${Fmt.pct(abs(potential))} ${S.toTarget}")
                        }
                    }
                }
            }

            profile?.takeIf { it.revenue > 0 || it.yearly.isNotEmpty() }?.let { p ->
                item {
                    SectionHeader(S.financials, chevron = true, trailing = S.yearly, onClick = { financialsSheet = true }, onTrailingClick = { financialsSheet = true })
                    val latest = p.yearly.lastOrNull()
                    val prev = p.yearly.dropLast(1).lastOrNull()
                    val fw = p.financialCurrency
                    FinanceRow(S.revenue, fremd.kompakt(latest?.revenue?.takeIf { it > 0 } ?: p.revenue, fw), growth(latest?.revenue, prev?.revenue) ?: p.revenueGrowth.takeIf { it != 0.0 }, { fremd.weiter(fw) }) { financialsSheet = true }
                    if (p.ebitda > 0) FinanceRow(S.ebitda, fremd.kompakt(p.ebitda, fw), null, { fremd.weiter(fw) }) { financialsSheet = true }
                    if ((latest?.earnings ?: 0.0) != 0.0) FinanceRow(S.netIncome, fremd.kompakt(latest!!.earnings, fw), growth(latest.earnings, prev?.earnings), { fremd.weiter(fw) }) { financialsSheet = true }
                }
            }

            // Derivate und Optionen
            if (type.directlyTradable) item {
                SectionHeader(S.derivatives, chevron = true, onClick = { onOpenRoute("derivate/L/$symbol") })
                // Long/Short aus dem Optionsumsatz an US-Boersen (nur US-Werte); ein Tipp auf den Balken
                // oeffnet die Long- bzw. Short-Produkte
                optionVolumes?.let {
                    LongShortBars(it.longPct, S.longShortCaption, onLong = { onOpenRoute("derivate/L/$symbol") }, onShort = { onOpenRoute("derivate/S/$symbol") })
                }
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenRoute("derivate/L/$symbol") }.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(S.derivativesSubtitle, style = MaterialTheme.typography.titleSmall)
                        Text(S.leverageRange(Derivatives.MAX_CUSTOM_LEVERAGE, Derivatives.MAX_FACTOR), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ChevronRight(height = 15.dp)
                }
                // Optionsanalyse: nur bei Werten mit US-Optionen, sonst bleibt der Abschnitt weg
                OptionenKarte(state, symbol, quote?.nativeCurrency?.ifBlank { null } ?: "USD", vol.takeIf { it > 0 })
            }


            // Trends zum Wert: Reddit, Yahoo Finance, Wikipedia; nur mit Daten
            item(key = "wert-trends") { WertTrends(state, symbol, name, type) }

            // Wer den Wert handelt: Personen mit ihren Geschaeften
            if (type == de.troetenrepublik.app.data.AssetType.STOCK || type == de.troetenrepublik.app.data.AssetType.ETF) {
                item(key = "handelnde") { HandelndeAbschnitt(state, symbol, onOpenRoute) }
            }

            // Eigengeschaefte der Fuehrung und Grossaktionaere deutscher Emittenten (BaFin)
            if (type == de.troetenrepublik.app.data.AssetType.STOCK) {
                item(key = "bafin") { BafinKarte(state, symbol, isin, name) }
            }

            // Zulassungen und Rueckrufe der FDA bei Pharma-, Biotech- und Medizintechnik-Aktien (seit 5.9)
            if (type == de.troetenrepublik.app.data.AssetType.STOCK && de.troetenrepublik.app.market.Fda.betroffen(profile?.industry.orEmpty(), profile?.sector.orEmpty())) {
                item(key = "fda") { FdaKarte(state, symbol, profile?.longName?.ifBlank { null } ?: name) }
            }

            // Offene Leerverkaufspositionen (AMF seit 5.8, FI und FCA seit 5.9)
            if (type == de.troetenrepublik.app.data.AssetType.STOCK && isin.length == 12 && !isin.startsWith("US")) {
                item(key = "leer-fr") { LeerpositionenKarte(state, isin) }
            }

            // Insidergeschaefte und Insider-Trend aus den Registern weiterer Laender (Schweden seit 5.8, weitere seit 5.9)
            if (type == de.troetenrepublik.app.data.AssetType.STOCK && (de.troetenrepublik.app.market.InsiderWelt.boerse(symbol) != null ||
                    (!istUsAktie(symbol, quote?.nativeCurrency) && isin.length == 12 && !isin.startsWith("DE")))) {
                item(key = "insider-eu") { EuInsiderKarte(state, symbol, isin) }
            }

            // Insidergeschaefte und Insider-Trend bei US-Aktien (SEC Form 4)
            if (type == de.troetenrepublik.app.data.AssetType.STOCK && istUsAktie(symbol, quote?.nativeCurrency)) {
                item(key = "sec") { SecKarte(state, symbol) }
            }

            // Lobbying, Gesetze, Auftraege, Spenden, Leerverkaeufe, Entlassungen und Patente bei US-Aktien
            if (type == de.troetenrepublik.app.data.AssetType.STOCK && istUsAktie(symbol, quote?.nativeCurrency)) {
                item(key = "behoerden") { BehoerdenKarte(state, symbol, name, onOpenRoute) }
            }

            // Lobbyarbeit in Europa (EU-Transparenzregister, Bundestag, HATVP), fuer Aktien aus allen Laendern
            if (type == de.troetenrepublik.app.data.AssetType.STOCK) {
                item(key = "lobby-welt") { LobbyWeltKarte(state, symbol) }
            }

            // Werkzeuge zur Analyse, ganz unten
            item {
                StockToolsSection(toolsFor(type, position != null && position.position.derivative == null, profile)) { tool = it }
            }

            item { Spacer(Modifier.height(110.dp)) }
        }

        // Kauf- und Sparplanknopf schweben ueber dem Inhalt, wie in der Kursansicht.
        Box(Modifier.align(Alignment.BottomCenter)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
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

    if (planSheet) PlanSheet(state, symbol, name, onDismiss = { planSheet = false })

    if (eventsSheet) EventsSheet(state, symbol, name, profile, onDismiss = { eventsSheet = false })

    if (financialsSheet) profile?.let { FinancialsSheet(name, it, onDismiss = { financialsSheet = false }) }

    openNews?.let { NewsSheet(it, onDismiss = { openNews = null }) }

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
        )
    }
}

private fun growth(now: Double?, before: Double?): Double? =
    if (now != null && before != null && before != 0.0) (now / abs(before) - 1.0) * 100.0 else null

// Terminzeile mit Datumsplakette, wie sie unter "Ereignisse" steht.
@Composable
fun EventRow(title: String, ts: Long, subtitle: String? = null, onClick: (() -> Unit)? = null) {
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
        if (onClick != null) ChevronRight(height = 13.dp)
    }
}

@Composable
private fun FinanceRow(label: String, wertText: String, growth: Double?, onWert: () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(wertText, style = MaterialTheme.typography.titleSmall, modifier = Modifier.clickable(interactionSource = null, indication = null, onClickLabel = S.switchCurrency, onClick = onWert))
        if (growth != null && growth != 0.0) {
            Spacer(Modifier.width(12.dp))
            MetricValue(growth, Fmt.pct(abs(growth)))
        }
    }
}

private fun spreadPct(bid: Double, ask: Double): Double =
    if (bid > 0) (ask - bid) / ((ask + bid) / 2.0) * 100.0 else 0.0
