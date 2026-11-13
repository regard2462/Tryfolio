@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package de.troetenrepublik.app.ui

import de.troetenrepublik.app.plattform.mausWischen
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.engine.MarketHours
import de.troetenrepublik.app.engine.TradeCalendar
import de.troetenrepublik.app.engine.Trading
import de.troetenrepublik.app.engine.Zeitraum
import de.troetenrepublik.app.plattform.ZurueckTaste
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Metric
import de.troetenrepublik.app.data.OrderStatus
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.data.Range
import de.troetenrepublik.app.data.Snapshot
import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.ValuedPosition
import de.troetenrepublik.app.data.WatchMeta
import de.troetenrepublik.app.data.WatchMetric
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Symbols
import kotlinx.coroutines.launch
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToInt

// Depotuebersicht: Wert, Verlauf, Positionen, darunter die Kacheln.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: AppState,
    onOpen: (String) -> Unit,
    onOpenDerivative: (String) -> Unit,
    onOpenActivity: () -> Unit,
    onSearch: () -> Unit,
    onOpenOrders: () -> Unit = onOpenActivity,
    onOpenImport: () -> Unit = {},
    onOpenNews: () -> Unit = {},
    onOpenCopy: () -> Unit = {},
    onOpenPredict: () -> Unit = {},
    onOpenKnowledge: () -> Unit = {},
    onOpenAnalytics: () -> Unit = {},
    onOpenMaerkte: () -> Unit = {},
    onOpenFinder: () -> Unit = {},
    onTransfer: (String) -> Unit = {},
    onProfileNavigate: (String) -> Unit = {},
) {
    val account by state.account.collectAsStateWithLifecycle()
    val valuation by state.valuation.collectAsStateWithLifecycle()
    val quotes by state.quotes.collectAsStateWithLifecycle()
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val signal = LocalSignal.current
    val scope = rememberCoroutineScope()
    // Leeres Depot (etwa nach der Neuinstallation): Sicherung oder Broker-Export einlesen
    val portfolioImport = rememberPortfolioImport(state)

    // Portfolio, Cash und Profil liegen in einer Reihe: Ein Wisch von rechts nach links fuehrt von
    // Portfolio zu Cash und von Cash zum Profil; dort ist Schluss. Zurueck geht es von links nach rechts.
    // Die Kopfzeile und die schwebenden Knoepfe gehoeren zu Portfolio und Cash.
    val pager = rememberPagerState(initialPage = HomePage.PORTFOLIO) { HomePage.COUNT }
    val portfolioList = rememberLazyListState()
    val cashList = rememberLazyListState()
    val profileList = rememberLazyListState()
    var range by rememberSaveable { mutableStateOf(Range.D1) }
    // Eigener Zeitraum: ganze Tage seit 1970, 0 = es gilt ein fester Zeitraum
    var eigenVon by rememberSaveable { mutableLongStateOf(0L) }
    var eigenBis by rememberSaveable { mutableLongStateOf(0L) }
    var zeitraumWahl by remember { mutableStateOf(false) }
    val eigen = eigenVon > 0
    // Handelskalender statt Chart (Kalendersymbol rechts neben „Max“), mit dem gezeigten Monat
    var kalender by rememberSaveable { mutableStateOf(false) }
    var kalenderMonatText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val kalenderMonat = remember(kalenderMonatText) { runCatching { YearMonth.parse(kalenderMonatText) }.getOrElse { YearMonth.now() } }
    val hatKalender = remember(account.transactions) {
        account.transactions.any { TradeCalendar.schliesst(it) || it.type == TxType.DIVIDEND }
    }
    // Ohne Positionen gibt es keine Kurve; dann zeigt die Startseite gleich den Kalender
    val kalenderAn = kalender || (valuation.positions.isEmpty() && hatKalender)
    val kalenderDaten = if (kalenderAn) rememberKalenderDaten(state, account) else null
    // Ein Tipp auf die Zahl neben dem Depotwert wechselt zwischen Prozent und Euro, immer fuer den gewaehlten Zeitraum.
    var absolute by rememberSaveable { mutableStateOf(false) }
    var scrub by remember { mutableStateOf<Candle?>(null) }
    var metricSheet by remember { mutableStateOf(false) }
    var watchSheet by rememberSaveable { mutableStateOf(false) }
    // Scrolllage im Watchlist-Blatt, solange es fuer eine aus ihm geoeffnete Seite zu ist
    var watchLage by remember { mutableStateOf(WatchlistLage()) }
    val watchRueck = rememberWiederkehr()
    val txRueck = rememberWiederkehr()
    // Buchung aus der Liste des Cash-Reiters, geoeffnet als Blatt
    var txOffen by remember { mutableStateOf<Transaction?>(null) }

    val loading by state.market.loading.collectAsStateWithLifecycle()
    // Karte „News“: Zahl der Schlagzeilen seit dem letzten Besuch der Nachrichtenseite
    val newsStand by state.news.stand.collectAsStateWithLifecycle()
    val newsNeu = remember(newsStand, settings.newsGelesenBis) {
        if (newsStand.geladenAm == 0L) null else newsStand.eintraege.count { it.zeit > settings.newsGelesenBis }
    }
    val saveError by state.store.error.collectAsStateWithLifecycle()

    // Verlauf aus echten Kursen und dem Bestand zu jedem Zeitpunkt. Der Schluessel enthaelt die
    // Stueckzahlen, damit ein Kauf die Kurve sofort neu berechnet; der letzte Punkt kommt aus dem Live-Kurs.
    val holdingsKey = remember(account.positions, account.transactions.size) {
        account.positions.joinToString("|") { "${it.key}:${it.qty}" } + "#" + account.transactions.size
    }
    var loaded by remember { mutableStateOf<String?>(null) }
    var marketSeries by remember { mutableStateOf(AppState.PortfolioSeries(emptyList())) }
    val ladeSchluessel = "$holdingsKey|$range|$eigenVon|$eigenBis"
    LaunchedEffect(ladeSchluessel) {
        loaded = null
        scrub = null
        marketSeries = if (eigenVon > 0) state.portfolioZeitraum(eigenVon, eigenBis) else state.portfolioSeries(range)
        loaded = ladeSchluessel
    }
    val seriesReady = loaded == ladeSchluessel

    val completeQuotes = account.positions.all { it.isKnockedOut || quotes[it.symbol]?.price?.let { price -> price.isFinite() && price > 0 } == true }

    // Rueckfallebene: die mitgeschriebenen Messpunkte, nur wenn sich aus der Kurshistorie nichts
    // bilden liess und erst, wenn das feststeht.
    val recorded = remember(account.history, range, eigenVon, eigenBis) {
        if (eigenVon > 0) {
            val (von, bis) = Zeitraum.grenzen(eigenVon, eigenBis, System.currentTimeMillis())
            historyFor(account.history, Range.MAX).filter { it.ts in von..bis }
        } else historyFor(account.history, range)
    }
    val series = remember(marketSeries, recorded, seriesReady, valuation.securities, completeQuotes) {
        if (!seriesReady) return@remember emptyList()
        val base = if (marketSeries.candles.size >= 2) marketSeries.candles
        else recorded.mapNotNull { snap -> snap.securities?.let { v -> Candle(snap.ts, v, v, v, v) } }
        if (base.size < 2 || !completeQuotes) return@remember base
        // Der letzte Punkt ist der aktuelle Wert, damit die Kurve bei der Kopfzahl endet. Ausserhalb des Depotfensters
        // (abends, nachts, am Wochenende) bleibt der letzte Kurs des Fensters stehen, denn ausserboerslicher Handel
        // gehoert nicht in die Kurve.
        val now = System.currentTimeMillis()
        if (!MarketHours.imDepotFenster(now)) return@remember base
        // Ein eigener Zeitraum, der vor heute endet, endet auch mit dem Wert dieses Tages
        if (eigenVon > 0 && Zeitraum.grenzen(eigenVon, eigenBis, now).second < now) return@remember base
        base.dropLast(1) + Candle(now, valuation.securities, valuation.securities, valuation.securities, valuation.securities)
    }
    // Die Kurve fuellt die ganze Breite, auch wenn das Depot juenger ist als der Zeitraum.
    val startFraction = 0f
    val domain = if (eigen) null else dayAxis(range, marketSeries.dayStart, marketSeries.dayEnd, series)

    // "Heute" misst gegen den Vortagesschluss, alle anderen Zeitraeume gegen den ersten Punkt der Kurve.
    val reference = if (range == Range.D1 && !eigen && completeQuotes && valuation.positions.isNotEmpty()) valuation.securities - valuation.dayChange
        else series.firstOrNull()?.close ?: valuation.securities
    val comparable = completeQuotes && (series.size >= 2 || (range == Range.D1 && !eigen))
    // Veraenderung eines Depotwerts gegen den Referenzwert. Den Wert am Fadenkreuz lesen erst Kopfzeile
    // und Chart: Beim Ziehen entstehen nur diese beiden Teile neu, nicht die ganze Startseite.
    fun periodDiffOf(value: Double) = if (comparable) value - reference else 0.0

    val density = LocalDensity.current
    var headerHeightPx by remember { mutableIntStateOf(with(density) { 66.dp.roundToPx() }) }
    val headerHeight = with(density) { headerHeightPx.toDp() }
    // Die Kopfzeile scrollt mit der jeweiligen Liste weg; beim Wischen wird zwischen beiden Listen gemittelt.
    fun listShift(list: LazyListState): Float =
        if (list.firstVisibleItemIndex > 0) -headerHeightPx.toFloat()
        else -list.firstVisibleItemScrollOffset.toFloat().coerceAtMost(headerHeightPx.toFloat())
    var widthPx by remember { mutableIntStateOf(0) }
    // Die Wischposition wird nur beim Anordnen und Zeichnen gelesen (Lambdas), nie beim Aufbau:
    // So baut sich die Startseite waehrend des Wischens nicht bei jedem Bild neu auf.
    fun position(): Float = pager.currentPage + pager.currentPageOffsetFraction
    val onCash by remember { derivedStateOf { HomePage.cashShare(position()) > 0.5f } }

    // Das Profil fuehrt zurueck dorthin, woher man kam (Portfolio oder Cash). Wer es verlaesst, findet
    // es beim naechsten Mal wieder oben vor; so landet der Profilkreis immer an seinem Platz.
    var herkunft by rememberSaveable { mutableIntStateOf(HomePage.PORTFOLIO) }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect {
            if (it != HomePage.PROFILE) {
                herkunft = it
                if (profileList.firstVisibleItemIndex > 0 || profileList.firstVisibleItemScrollOffset > 0) profileList.scrollToItem(0)
            }
        }
    }
    fun goTo(page: Int) { scope.launch { pager.animateScrollToPage(page) } }
    fun openProfile() { herkunft = pager.settledPage.takeIf { it != HomePage.PROFILE } ?: herkunft; goTo(HomePage.PROFILE) }

    ZurueckTaste(enabled = pager.currentPage == HomePage.PROFILE) { goTo(herkunft) }

    Box(Modifier.fillMaxSize().onSizeChanged { widthPx = it.width }) {
      // Schon ein Drittel des Wegs genuegt fuer den Seitenwechsel. Alle drei Seiten bleiben aufgebaut,
      // damit beim Wischen keine Seite erst entstehen muss und der Zug stockt.
      HorizontalPager(
          state = pager,
          modifier = Modifier.fillMaxSize().mausWischen(pager),
          beyondViewportPageCount = HomePage.COUNT - 1,
          flingBehavior = PagerDefaults.flingBehavior(state = pager, snapPositionalThreshold = 0.3f),
      ) { page ->
        when (page) {
        HomePage.PROFILE -> ProfileScreen(
            state = state,
            onOpen = onOpen,
            onNavigate = onProfileNavigate,
            onTransfer = { onTransfer("") },
            listState = profileList,
            // Waehrend des Wechsels zeigt der wandernde Kreis oben das Profilbild; erst am Ziel uebernimmt die Seite
            avatarShown = { position() >= HomePage.PROFILE - HomePage.AVATAR_EPS },
        )
        HomePage.CASH -> {
          LazyColumn(Modifier.fillMaxSize(), state = cashList) {
            item { Spacer(Modifier.height(headerHeight)) }
            cashTab(state, account, settings.hideBalances, onOpenActivity, onTransfer, onOpen, onTx = { txOffen = it },
                waehrung = Modifier.waehrungWechsel(state))
          }
        }
        else -> {
          LazyColumn(Modifier.fillMaxSize(), state = portfolioList) {
            item { Spacer(Modifier.height(headerHeight)) }

                item {
                    val selected = scrub
                    val shown = selected?.close ?: valuation.securities
                    // Im Kalender steht neben dem Depotwert das realisierte Ergebnis des gezeigten Monats
                    val monatsSumme = kalenderDaten?.let { TradeCalendar.monat(it.tage, kalenderMonat) }
                    val periodDiff = monatsSumme?.realisiert ?: periodDiffOf(shown)
                    val periodPct = if (reference > 0) periodDiff / reference * 100.0 else 0.0
                    val headerText = if (monatsSumme != null || absolute) Fmt.money(abs(periodDiff)) else Fmt.pct(abs(periodPct))
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            SchrumpfText(
                                if (settings.hideBalances) "•••••• " + Fmt.symbol else Fmt.money(shown),
                                style = MaterialTheme.typography.displayMedium,
                                modifier = Modifier.weight(1f, fill = false).waehrungWechsel(state),
                            )
                            Spacer(Modifier.width(14.dp))
                            ChangeValue(
                                change = periodDiff,
                                text = if (settings.hideBalances) "••••" else headerText,
                                // Der Kopfwert bleibt neutral; die Richtung traegt allein das Dreieck. Das realisierte
                                // Ergebnis im Kalender ist gruen oder rot wie die Tage darunter.
                                colored = monatsSumme != null,
                                style = MaterialTheme.typography.titleSmall,
                                triangleWidth = 10.dp,
                                modifier = Modifier
                                    .textKlick(8.dp, enabled = selected == null && monatsSumme == null, role = Role.Button) { absolute = !absolute }
                                    .semantics { contentDescription = S.switchMetric }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                            )
                        }
                        PunktText(
                            when {
                                monatsSumme != null -> S.calRealizedIn(monatsName(kalenderMonat))
                                selected != null -> Fmt.dateTime(selected.t)
                                eigen -> eigenZeitraumText(eigenVon, eigenBis)
                                else -> S.rangeCaption(range)
                            } + (if (Fmt.anzeigeWaehrung != "EUR") " · " + S.fxRate("EUR", Fmt.anzeigeWaehrung, Fmt.num(Fmt.anzeigeJeEuro, 4)) else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }

                // Nur bei Speicherfehlern; Kursausfaelle zeigt die App nicht als Hinweis an
                if (saveError != null) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(S.error(saveError ?: "", ""),
                                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else IconButton(onClick = { scope.launch { state.market.refresh() } }) {
                                Icon(Icons.Filled.Refresh, S.reloadPrices)
                            }
                        }
                    }
                }

                if (valuation.positions.isNotEmpty() || hatKalender) {
                    item {
                        val selected = scrub
                        val lineColor = signal.lineFor(periodDiffOf(selected?.close ?: valuation.securities))
                        RangeSelector(
                            ranges = Range.entries,
                            // Ein eigener Zeitraum entsteht per langem Druck auf „Max“ und haelt „Max“ hervorgehoben
                            selected = if (kalenderAn) null else if (eigen) Range.MAX else range,
                            onSelect = { range = it; eigenVon = 0L; eigenBis = 0L; scrub = null; kalender = false },
                            onLongPress = if (valuation.positions.isNotEmpty()) ({ r -> if (r == Range.MAX) zeitraumWahl = true }) else null,
                            modifier = Modifier.padding(horizontal = 12.dp).padding(top = 20.dp, bottom = 6.dp),
                            trailing = {
                                // Kalendersymbol rechts neben „Max“ wechselt in den Handelskalender
                                CalendarToggle(active = kalenderAn) { kalender = !kalenderAn; scrub = null }
                            },
                        )
                        val daten = kalenderDaten
                        if (daten != null) {
                            TradeCalendarView(
                                daten = daten,
                                monat = kalenderMonat,
                                onMonat = { kalenderMonatText = it.toString() },
                                hideBalances = settings.hideBalances,
                                state = state,
                                onOpen = onOpen,
                            )
                        } else Box(Modifier.fillMaxWidth().height(215.dp)) {
                            PriceChart(
                                candles = series,
                                modifier = Modifier.fillMaxWidth().height(215.dp),
                                lineColor = lineColor,
                                baseline = reference,
                                onScrub = { scrub = it },
                                emptyLabel = "",
                                startFraction = startFraction,
                                domain = domain,
                            )
                            ChartCrosshair(
                                candles = series,
                                selected = selected,
                                color = lineColor,
                                baseline = reference,
                                modifier = Modifier.fillMaxWidth().height(215.dp),
                                startFraction = startFraction,
                                domain = domain,
                            )
                        }
                    }
                }

                item {
                    SectionHeader(S.investments, trailing = S.metricShort(settings.metric), onTrailingClick = { metricSheet = true }, unten = 2.dp)
                }

                if (valuation.positions.isEmpty()) {
                    item {
                        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp)) {
                            FloatingPill(S.importPortfolio, modifier = Modifier.fillMaxWidth(), filled = false, plus = true, onClick = portfolioImport)
                        }
                    }
                } else {
                    // Gruppen: Aktien und ETFs, Krypto, Derivate
                    val groups = valuation.positions.groupBy { groupOf(it) }
                    listOf("STOCKS" to S.groupStocksEtfs, "CRYPTO" to S.crypto, "DERIVATIVES" to S.derivatives).forEach { (g, label) ->
                        val members = groups[g] ?: return@forEach
                        item(key = "group-$g") {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 6.dp, bottom = 2.dp),
                            )
                        }
                        items(members, key = { "position-${it.key}" }) { vp ->
                            val p = vp.position
                            val ko = p.isKnockedOut
                            AssetRow(
                                state = state,
                                symbol = p.symbol,
                                name = if (p.isDerivative) positionsName(p) else kurzName(p.symbol, p.name),
                                subtitle = when {
                                    ko -> S.knockedOut
                                    settings.hideBalances -> "•••• " + Fmt.symbol
                                    else -> Fmt.money(vp.value)
                                },
                                subtitleColor = if (ko) signal.down else null,
                                // Rechts unter dem Verlust der Tag der Rueckzahlung, weil die Zweitzeile dafuer zu kurz ist
                                rightCaption = p.derivative?.takeIf { ko }?.let { S.koRefundOn(Fmt.dayMonth(it.knockedOutAt + Trading.KO_SETTLE_MS)) },
                                change = if (ko) -1.0 else metricChange(vp, settings.metric),
                                changeText = when {
                                    settings.hideBalances && settings.metric.isAbsolute -> "••••"
                                    ko -> if (settings.metric.isAbsolute) Fmt.money(vp.invested) else Fmt.pct(100.0)
                                    else -> metricText(vp, settings.metric)
                                },
                                onValueClick = { state.cycleMetric() },
                                onClick = { if (p.isDerivative) onOpenDerivative(p.key) else onOpen(p.symbol) },
                            )
                        }
                    }
                }

                // Kacheln unter den Positionen, zwei je Reihe, alle als eigene Kachel
                item(key = "kacheln") {
                    val open = account.orders.count { it.status == OrderStatus.OPEN }
                    val wetten = account.wetten.count { it.offen }
                    val gefolgt = state.gefolgt(settings).size
                    Column(Modifier.fillMaxWidth()) {
                        FlowRow(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 2,
                        ) {
                            val k = Modifier.weight(1f)
                            KachelKompakt(S.watchlist, account.watchlist.size.takeIf { it > 0 }?.toString(), plus = account.watchlist.isEmpty(), modifier = k, state = state, widget = de.troetenrepublik.app.data.WidgetKarten.WERTE) { watchSheet = true }
                            KachelKompakt(S.news, newsNeu?.toString() ?: "", modifier = k, state = state, widget = de.troetenrepublik.app.data.WidgetKarten.NEWS, onClick = onOpenNews)
                            KachelKompakt(S.maerkteTitel, "", modifier = k, state = state, widget = de.troetenrepublik.app.data.WidgetKarten.MAERKTE, onClick = onOpenMaerkte)
                            KachelKompakt(S.analytics, "", modifier = k, state = state, widget = de.troetenrepublik.app.data.WidgetKarten.ANALYTICS, onClick = onOpenAnalytics)
                            KachelKompakt(S.afTitel, "", modifier = k, state = state, widget = de.troetenrepublik.app.data.WidgetKarten.AKTIENFINDER, onClick = onOpenFinder)
                            KachelKompakt(S.copyTitle, gefolgt.takeIf { it > 0 }?.toString() ?: "", modifier = k, state = state, widget = de.troetenrepublik.app.data.WidgetKarten.COPYTRADING, onClick = onOpenCopy)
                            KachelKompakt(S.pmTitle, wetten.takeIf { it > 0 }?.toString() ?: "", modifier = k, state = state, widget = de.troetenrepublik.app.data.WidgetKarten.PROGNOSE, onClick = onOpenPredict)
                            KachelKompakt(S.knowledge, "", modifier = k, state = state, widget = de.troetenrepublik.app.data.WidgetKarten.WISSEN, onClick = onOpenKnowledge)
                            // Die Sparplaene liegen als Reiter auf der Seite Orders
                            KachelKompakt(S.orders, open.takeIf { it > 0 }?.toString() ?: "", modifier = k, state = state, widget = de.troetenrepublik.app.data.WidgetKarten.ORDERS, onClick = onOpenOrders)
                            KachelKompakt(S.importPosition, null, plus = true, modifier = k, onClick = onOpenImport)
                        }
                    }
                }

            item { Spacer(Modifier.height(110.dp)) }
          }
        }
        }
      }

      // Kopfzeile: Portfolio | Cash, rechts daneben der Profilkreis (siehe unten).
      fun headerShiftY(pos: Float): Float {
          val f = pos.coerceIn(0f, 1f)
          return listShift(portfolioList) + (listShift(cashList) - listShift(portfolioList)) * f
      }
      Row(
          Modifier
              .fillMaxWidth()
              .offset {
                  val pos = position()
                  IntOffset(HomePage.headerShiftX(pos, widthPx), headerShiftY(pos).roundToInt())
              }
              .onSizeChanged { if (it.height > 0) headerHeightPx = it.height }
              .padding(start = 20.dp, end = 16.dp, top = 18.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
          val active = MaterialTheme.colorScheme.onSurface
          val inactive = MaterialTheme.colorScheme.onSurfaceVariant
          // Die Farben der Titel folgen dem Finger; gelesen wird die Position erst beim Zeichnen
          BasicText(
              S.portfolio,
              style = MaterialTheme.typography.headlineMedium,
              color = { lerp(active, inactive, HomePage.cashShare(position())) },
              modifier = Modifier.heightIn(min = 48.dp)
                  .selectable(pager.currentPage == HomePage.PORTFOLIO, interactionSource = null, indication = Tippflaeche(TippForm.TEXT, 0.dp, voll = true), role = Role.Tab) { goTo(HomePage.PORTFOLIO) }
                  .padding(top = 8.dp),
          )
          Spacer(Modifier.width(16.dp))
          BasicText(
              S.cash,
              style = MaterialTheme.typography.headlineMedium,
              color = { lerp(inactive, active, HomePage.cashShare(position())) },
              modifier = Modifier.heightIn(min = 48.dp)
                  .selectable(pager.currentPage == HomePage.CASH, interactionSource = null, indication = Tippflaeche(TippForm.TEXT, 0.dp, voll = true), role = Role.Tab) { goTo(HomePage.CASH) }
                  .padding(top = 8.dp),
          )
      }

      // Schwebende Aktionen in Daumenreichweite: auf Portfolio „Handeln“ (oeffnet die Suche zum Kaufen),
      // auf Cash Investieren und Ueberweisen
      Row(
          Modifier
              .align(Alignment.BottomCenter)
              .fillMaxWidth()
              .offset { IntOffset(HomePage.headerShiftX(position(), widthPx), 0) }
              .padding(horizontal = 20.dp, vertical = 24.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
          FloatingPill(
              label = if (onCash) S.invest else S.handeln,
              icon = if (onCash) Icons.AutoMirrored.Filled.TrendingUp else Icons.Filled.Search,
              filled = true,
              modifier = Modifier.weight(1f),
          ) { onSearch() }
          if (onCash) FloatingPill(label = S.transfer, icon = Icons.Filled.SwapVert, filled = true, modifier = Modifier.weight(1f)) { onTransfer("") }
      }

      // Profilkreis: klein oben rechts auf Portfolio und Cash. Auf dem Weg von Cash zum Profil waechst er
      // mit dem Finger und gleitet an seinen Platz oben links im Profil; dort uebernimmt der Kreis der
      // Profilseite, der mit der Liste scrollt. Gerechnet wird nur beim Zeichnen (Lambda), nicht beim Aufbau.
      val avatarActive by remember { derivedStateOf { position() < HomePage.PROFILE - 0.5f } }
      ProfileAvatar(
          initial = settings.ownerName.take(1).ifBlank { "T" }.uppercase(),
          modifier = Modifier
              .graphicsLayer {
                  val pos = position()
                  transformOrigin = TransformOrigin(0f, 0f)
                  if (pos >= HomePage.PROFILE - HomePage.AVATAR_EPS) {
                      // Am Ziel: unsichtbar und aus dem Weg, damit er keine Beruehrung abfaengt
                      alpha = 0f
                      translationY = -100_000f
                  } else {
                      val small = HomePage.AVATAR_SMALL.toPx()
                      val large = HomePage.AVATAR_LARGE.toPx()
                      val scroll = if (profileList.firstVisibleItemIndex > 0) -3 * large else -profileList.firstVisibleItemScrollOffset.toFloat()
                      val f = HomePage.avatarFrame(
                          t = HomePage.avatarProgress(pos),
                          fromX = widthPx - HomePage.AVATAR_END.toPx() - small,
                          fromY = HomePage.AVATAR_TOP_SMALL.toPx() + headerShiftY(pos),
                          fromSize = small,
                          toX = HomePage.AVATAR_START.toPx(),
                          toY = HomePage.AVATAR_TOP_LARGE.toPx() + scroll,
                          toSize = large,
                      )
                      alpha = 1f
                      translationX = f.x
                      translationY = f.y
                      scaleX = f.scale
                      scaleY = f.scale
                  }
              }
              .then(if (avatarActive) Modifier.semantics { contentDescription = S.openProfile } else Modifier.clearAndSetSemantics { }),
          onClick = { if (position() < HomePage.PROFILE - 0.5f) openProfile() },
      )
    }

    if (zeitraumWahl) ZeitraumDialog(eigenVon, eigenBis, onDismiss = { zeitraumWahl = false }) { von, bis ->
        eigenVon = minOf(von, bis); eigenBis = maxOf(von, bis); scrub = null; kalender = false; zeitraumWahl = false
    }
    if (metricSheet) {
        ModalBottomSheet(
            onDismissRequest = { metricSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
        ) {
            Text(
                S.metricTitle,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 12.dp),
            )
            Metric.entries.forEach { m ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { state.setMetric(m); metricSheet = false }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(S.metricLabel(m), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (m == settings.metric) CheckMark(size = 17.dp)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (watchSheet) {
        WiederkehrBlatt("watchlist", watchRueck) { WatchlistSheet(
            state = state,
            onDismiss = { watchSheet = false; watchLage = WatchlistLage(); watchRueck.vergessen() },
            onOpen = { watchRueck.verlassen(); onOpen(it) },
            onAdd = { watchRueck.verlassen(); onSearch() },
            sofort = watchRueck.sofort,
            lage = watchLage,
        ) }
        BlattBild(watchRueck, "watchlist") { WatchlistBild(state, watchLage) }
    }

    txOffen?.let { tx ->
        if (!txRueck.wartet) TransactionSheet(state, tx, onDismiss = { txOffen = null; txRueck.vergessen() }, sofort = txRueck.sofort, onOpen = {
            if (tx.key.isNotBlank() && tx.key != tx.symbol) { txRueck.verlassen(); onOpenDerivative(tx.key) }
            else if (tx.symbol.isNotBlank()) { txRueck.verlassen(); onOpen(tx.symbol) }
        })
        BlattBild(txRueck, "buchung") { TransactionInhalt(state, tx, onOpen = {}) }
    }
}

// Kennzahl der Watchlist: Tagestrend oder Entwicklung seit dem Hinzufuegen.
fun watchChange(q: Quote, meta: WatchMeta?, m: WatchMetric): Double? = when (m) {
    WatchMetric.TODAY_PCT -> q.changePct
    WatchMetric.TODAY_ABS -> q.change
    WatchMetric.SINCE_ADDED_PCT -> meta?.takeIf { it.price > 0 }?.let { (q.price / it.price - 1.0) * 100.0 }
    WatchMetric.SINCE_ADDED_ABS -> meta?.takeIf { it.price > 0 }?.let { q.price - it.price }
    WatchMetric.SINCE_FIRST_PCT -> meta?.takeIf { it.ersterKurs > 0 }?.let { (q.price / it.ersterKurs - 1.0) * 100.0 }
    WatchMetric.SINCE_FIRST_ABS -> meta?.takeIf { it.ersterKurs > 0 }?.let { q.price - it.ersterKurs }
}

// ------------------------------------------------------------ Cash-Reiter

private fun androidx.compose.foundation.lazy.LazyListScope.cashTab(
    state: AppState,
    account: Account,
    hide: Boolean,
    onOpenActivity: () -> Unit,
    onTransfer: (String) -> Unit,
    onOpen: (String) -> Unit,
    onTx: (Transaction) -> Unit,
    waehrung: Modifier = Modifier,
) {
    item {
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 26.dp, bottom = 18.dp)) {
            Text(if (hide) "•••••• " + Fmt.symbol else Fmt.money(account.cash), style = MaterialTheme.typography.displayMedium, modifier = waehrung)
            if (Fmt.anzeigeWaehrung != "EUR") Text(S.fxRate("EUR", Fmt.anzeigeWaehrung, Fmt.num(Fmt.anzeigeJeEuro, 4)), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            Text(
                if (account.reserved > 0) S.reservedForOrders(Fmt.money(account.reserved))
                else if (account.gebunden > 0.005) S.shFreiZeile(if (hide) "••••" else Fmt.money(account.frei)) else S.available,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
            )
            // Guthaben, das verkaufte Optionen binden (0.77)
            if (account.gebunden > 0.005) Text(
                S.shGebundenZeile(if (hide) "••••" else Fmt.money(account.gebunden)),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 2.dp),
            )
        }
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FloatingPill(label = S.deposit, filled = false, modifier = Modifier.weight(1f)) { onTransfer("deposit") }
            FloatingPill(label = S.withdraw, filled = false, modifier = Modifier.weight(1f)) { onTransfer("withdraw") }
        }
        Spacer(Modifier.height(8.dp))
    }
    // Statistik direkt im Reiter: Zeitraeume zum Wischen, Blasen nach Betrag und Kategorien
    item(key = "statistik") { StatistikEingebettet(state, onOpen) }
    item { SectionHeader(S.activity, chevron = true, onClick = onOpenActivity) }
    if (account.transactions.isEmpty()) item { EmptyHint(S.noTransactions, S.deposit to { onTransfer("deposit") }) }
    // Jede Buchung oeffnet ihr Blatt mit Einzelheiten und Abrechnung, wie in der Aktivitaet
    items(account.transactions.sortedByDescending { it.ts }.take(12), key = { "cash-${it.id}" }) { tx -> TimelineRow(tx) { onTx(tx) } }
    item { Spacer(Modifier.height(120.dp)) }
}

// ------------------------------------------------------------ Hilfen

private fun groupOf(vp: ValuedPosition): String = when {
    vp.position.derivative != null -> "DERIVATIVES"
    Symbols.typeOf(vp.position.symbol) == AssetType.CRYPTO -> "CRYPTO"
    else -> "STOCKS"
}

fun metricChange(vp: ValuedPosition, m: Metric): Double = when (m) {
    Metric.SINCE_BUY_PCT -> vp.profitPct
    Metric.SINCE_BUY_ABS -> vp.profit
    Metric.TODAY_PCT -> vp.dayChangePct
    Metric.TODAY_ABS -> vp.dayChange
}

fun metricText(vp: ValuedPosition, m: Metric): String {
    val v = abs(metricChange(vp, m))
    return if (m.isAbsolute) Fmt.money(v) else Fmt.pct(v)
}

// Rueckfallebene: die lokal mitgeschriebenen Messpunkte. Sie kommen nur zum Einsatz, wenn sich
// aus der Kurshistorie der gehaltenen Werte keine Kurve bilden laesst.
private fun historyFor(history: List<Snapshot>, range: Range): List<Snapshot> {
    val now = System.currentTimeMillis()
    val cutoff = when (range) {
        Range.D1 -> now - 86_400_000L
        Range.W1 -> now - 7 * 86_400_000L
        Range.M1 -> now - 30L * 86_400_000L
        Range.M3 -> now - 91L * 86_400_000L
        Range.M6 -> now - 182L * 86_400_000L
        Range.Y1 -> now - 365L * 86_400_000L
        Range.MAX -> 0L
    }
    // Alte Messpunkte mit dem Gesamtvermoegen statt des Wertpapierwerts und Punkte ausserhalb des Zeitraums weglassen.
    val slice = history.filter { it.ts >= cutoff && it.securities != null && it.securities.isFinite() }
    if (slice.size <= 240) return slice
    val step = (slice.size + 239) / 240
    return slice.filterIndexed { i, _ -> i % step == 0 || i == slice.lastIndex }
}

// Die drei Seiten der Startseite in einer Reihe.
internal object HomePage {
    const val PORTFOLIO = 0
    const val CASH = 1
    const val PROFILE = 2
    const val COUNT = 3

    // Anteil von Cash an der Hervorhebung der Titel: 0 = Portfolio, 1 = Cash (und dahinter).
    fun cashShare(position: Float): Float = position.coerceIn(0f, 1f)

    // Waagerechter Versatz von Kopfzeile und Knoepfen: Zwischen Portfolio und Cash stehen sie still,
    // auf dem Weg zum Profil gleiten sie mit Cash nach links hinaus.
    fun headerShiftX(position: Float, widthPx: Int): Int = (-(position - CASH).coerceIn(0f, 1f) * widthPx).roundToInt()

    // ---- Profilkreis: klein in der Kopfzeile (rechts), im Profil in seiner gewohnten Groesse (links oben)
    val AVATAR_SMALL = 44.dp
    val AVATAR_LARGE = 56.dp
    // Abstand des kleinen Kreises vom rechten Rand und von oben (Kopfzeile: 18 dp Rand, Zeile 48 dp hoch).
    val AVATAR_END = 16.dp
    val AVATAR_TOP_SMALL = 20.dp
    // Platz des Kreises im Profil; seine Mitte liegt auf derselben Hoehe wie die des kleinen Kreises (42 dp).
    val AVATAR_START = 20.dp
    val AVATAR_TOP_LARGE = 14.dp
    // Ab hier gilt das Profil als erreicht; der wandernde Kreis uebergibt an den der Profilseite.
    const val AVATAR_EPS = 0.001f

    // Fortschritt der Verwandlung: 0 auf Portfolio und Cash, 1 im Profil; dazwischen folgt er dem Finger.
    fun avatarProgress(position: Float): Float = (position - CASH).coerceIn(0f, 1f)

    // Lage (linke obere Ecke) und Massstab des Kreises, bezogen auf seine volle Groesse toSize.
    data class AvatarFrame(val x: Float, val y: Float, val scale: Float)

    fun avatarFrame(t: Float, fromX: Float, fromY: Float, fromSize: Float, toX: Float, toY: Float, toSize: Float): AvatarFrame {
        val k = t.coerceIn(0f, 1f)
        val size = fromSize + (toSize - fromSize) * k
        return AvatarFrame(fromX + (toX - fromX) * k, fromY + (toY - fromY) * k, if (toSize > 0f) size / toSize else 1f)
    }
}

// Profilkreis in voller Groesse mit dem Anfangsbuchstaben; die Kopfzeile zeigt ihn verkleinert (siehe HomePage).
@Composable
internal fun ProfileAvatar(initial: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(HomePage.AVATAR_LARGE)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, style = MaterialTheme.typography.titleLarge)
    }
}

// Eigener Zeitraum mit „bis“ statt Strich (EN „to“)
private fun eigenZeitraumText(vonTag: Long, bisTag: Long): String {
    fun tag(t: Long) = Fmt.date(t * Zeitraum.TAG + Zeitraum.TAG / 2)
    return if (vonTag == bisTag) tag(vonTag) else S.copyRange(tag(minOf(vonTag, bisTag)), tag(maxOf(vonTag, bisTag)))
}
