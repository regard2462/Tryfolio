package de.troetenrepublik.app.ui

import de.troetenrepublik.app.plattform.mausWischen
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.lerp
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import kotlinx.coroutines.withContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.data.ValuedPosition
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Symbols
import kotlin.math.abs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private enum class SortBy { VALUE, NAME, PROFIT }

// Analytics: Reiter Assets und Positionen mit Donut und sortierbarer Aufschluesselung, dazu Gesamtrendite, groesster
// Rueckgang und groesste Position. Der dritte Reiter enthaelt Werkzeuge fuer das ganze Depot, siehe PortfolioToolSheet.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsSheet(
    state: AppState,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
    // 0 Assets, 1 Positionen, 2 Werkzeuge. onSeite meldet jeden Wechsel, damit das Blatt dort wiederkommt.
    seite: Int = 0,
    onSeite: (Int) -> Unit = {},
    // PortfolioTool-Name oder WERKZEUG_VERLAUF, bleibt gemerkt, solange eine Seite darueber liegt.
    werkzeug: String? = null,
    onWerkzeug: (String?) -> Unit = {},
    // Ganz offen statt hereinzugleiten, bei der Rueckkehr aus einer Seite (siehe Wiederkehr).
    sofort: Boolean = false,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = blattZustand(sofort),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        AnalyticsContent(state, onOpen, Modifier, seite, onSeite, werkzeug, onWerkzeug, sofort)
    }
}

// Depotverlauf als eigenes Blatt ueber Analytics.
const val WERKZEUG_VERLAUF = "VERLAUF"

// Bild des Analytics-Blatts unter einer daraus geoeffneten Seite. Beim Wegwischen der Seite erscheint darunter das Blatt,
// zu dem es zurueckgeht, nicht das Depot (siehe BlattBild).
@Composable
fun ColumnScope.AnalyticsBild(state: AppState, seite: Int, werkzeug: String?) {
    val tool = werkzeug?.let { w -> PortfolioTool.entries.firstOrNull { it.name == w } }
    when {
        werkzeug == WERKZEUG_VERLAUF -> DepotVerlaufScreen(state, onBack = {}, onOpen = {}, imBlatt = true)
        tool != null -> PortfolioToolInhalt(state, tool, onOpen = {})
        else -> AnalyticsContent(state, onOpen = {}, modifier = Modifier, seite = seite)
    }
}

@Composable
private fun AnalyticsContent(
    state: AppState,
    onOpen: (String) -> Unit,
    modifier: Modifier,
    seite: Int = 0,
    onSeite: (Int) -> Unit = {},
    werkzeug: String? = null,
    onWerkzeug: (String?) -> Unit = {},
    sofort: Boolean = false,
) {
    val valuation by state.valuation.collectAsStateWithLifecycle()
    val account by state.account.collectAsStateWithLifecycle()
    val pager = rememberPagerState(initialPage = seite.coerceIn(0, 2)) { 3 }
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { onSeite(it) } }
    // Welches Werkzeug offen ist, merkt sich der Aufrufer fuer die Wiederkehr nach einer Seite
    // Der Aktienfinder ist eine eigene Seite, kein Blatt mehr
    val tool = werkzeug?.let { w -> PortfolioTool.entries.firstOrNull { it.name == w && it != PortfolioTool.FINDER } }
    // Beim Wegwischen des Depotverlaufs bleibt Analytics stehen wie bei den anderen Werkzeugen
    val verlauf = werkzeug == WERKZEUG_VERLAUF
    val scope = rememberCoroutineScope()
    var sort by remember { mutableStateOf(SortBy.VALUE) }
    val total = valuation.securities
    // Anteile beziehen sich auf das eingesetzte Volumen (Summe der Betraege). Verkaufte Optionen haben so einen positiven
    // Anteil und ein eigenes Stueck im Ring, ihr Wert bleibt negativ.
    val brutto = remember(valuation) { valuation.positions.sumOf { kotlin.math.abs(it.value) } }

    val classes = remember(valuation) {
        valuation.positions.groupBy { classOf(it) }.map { (k, v) -> k to v.sumOf { it.value } }.sortedByDescending { it.second }
    }
    // Volumen je Klasse (Summe der Betraege) fuer Anteil und Ring
    val klassenVolumen = remember(valuation) {
        valuation.positions.groupBy { classOf(it) }.mapValues { (_, v) -> v.sumOf { kotlin.math.abs(it.value) } }
    }
    val positions = remember(valuation, sort) {
        when (sort) {
            SortBy.VALUE -> valuation.positions.sortedByDescending { it.value }
            SortBy.NAME -> valuation.positions.sortedBy { it.position.name.lowercase() }
            SortBy.PROFIT -> valuation.positions.sortedByDescending { it.profitPct }
        }
    }
    // Frohere Toene als in den Datenkarten (seit 5.92)
    val palette = Kategorie.froh
    // Farbe nach Groesse der Position, damit sie beim Umsortieren gleich bleibt
    val farbeVon = remember(valuation) {
        valuation.positions.sortedByDescending { it.value }.mapIndexed { i, vp -> vp.key to palette[i % palette.size] }.toMap()
    }

    // Der Wert aus Portfolio Performance im Zeitraum Max, wie im Widget (AppState.maxRueckgang).
    var drawdown by remember { mutableStateOf(state.rueckgangStand(account) ?: de.troetenrepublik.app.engine.Rueckgang.aufgezeichnet(account)) }
    LaunchedEffect(account.transactions.size, account.positions.size, account.history.size.coerceAtMost(1)) {
        drawdown = state.maxRueckgang()
    }
    val sortLabel = when (sort) { SortBy.VALUE -> S.sortTotal; SortBy.NAME -> S.sortName; SortBy.PROFIT -> S.widgetZeitKauf }

    run {
        Column(Modifier.fillMaxWidth()) {
            // Langes Druecken auf die Ueberschrift bietet das Widget mit Rendite, Rueckgang und groesster Position an (nur Android)
            var widgetMenue by remember { mutableStateOf(false) }
            val haptik = androidx.compose.ui.platform.LocalHapticFeedback.current
            Box(
                Modifier.padding(start = 20.dp, top = 4.dp).then(
                    if (state.widgetAnheftbar) Modifier.pointerInput(Unit) {
                        detectTapGestures(onLongPress = {
                            haptik.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            widgetMenue = true
                        })
                    } else Modifier,
                ),
            ) {
                Text(S.analytics, style = MaterialTheme.typography.headlineLarge)
                if (state.widgetAnheftbar) KategorieMenue(state, de.troetenrepublik.app.data.WidgetKarten.ANALYTICS, widgetMenue) { widgetMenue = false }
            }
            val pos = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, 2f)
            // Die Pille (clip) liegt mit eigenem Innenabstand um das Wort; ohne ihn schnitten die runden Ecken die Buchstabenenden ab
            Row(Modifier.padding(start = 10.dp, top = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(S.assets, S.positions, S.toolsTitle).forEachIndexed { i, label ->
                    val active = MaterialTheme.colorScheme.onSurface
                    val inactive = MaterialTheme.colorScheme.onSurfaceVariant
                    WidgetLangDruck(state, de.troetenrepublik.app.data.WidgetVorgabe.Vorgabe(de.troetenrepublik.app.data.WidgetKarten.ANALYTICS)) {
                        Text(
                            label, style = MaterialTheme.typography.titleMedium,
                            color = lerp(inactive, active, (1f - kotlin.math.abs(pos - i)).coerceIn(0f, 1f)),
                            maxLines = 1, softWrap = false,
                            modifier = Modifier.textKlick(10.dp) { scope.launch { pager.animateScrollToPage(i) } }.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            HorizontalPager(state = pager, beyondViewportPageCount = 2, modifier = Modifier.fillMaxWidth().weight(1f, fill = false).mausWischen(pager).then(modifier)) { page ->
                val assetsPage = page == 0
                if (page == 2) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            PortfolioToolsList(
                                state, onDepotVerlauf = { onWerkzeug(WERKZEUG_VERLAUF) },
                                // Aktienfinder und Optionen sind Seiten: dieselbe wie hinter der Kachel bzw. in der Suche
                                onSeite = { onOpen(SEITEN_PRAEFIX + it) },
                            ) { onWerkzeug(it.name) }
                        }
                        item { Spacer(Modifier.height(40.dp)) }
                    }
                    return@HorizontalPager
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Spacer(Modifier.height(20.dp))
                        val slices = if (assetsPage) classes.map { klassenVolumen[it.first] ?: 0.0 } else positions.map { kotlin.math.abs(it.value) }
                        val farben = if (assetsPage) classes.indices.map { palette[it % palette.size] } else positions.map { farbeVon[it.key] ?: palette[0] }
                        Box(Modifier.fillMaxWidth().height(230.dp), contentAlignment = Alignment.Center) {
                            Donut(slices, farben, Modifier.size(210.dp))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(Fmt.money(total), style = MaterialTheme.typography.headlineSmall)
                                Text(S.total, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    item {
                        // Die Sortierung gilt nur den Positionen, die Anlageklassen stehen immer nach Groesse
                        if (assetsPage) SectionHeader(S.breakdown)
                        else SectionHeader(S.breakdown, trailing = sortLabel, onTrailingClick = { sort = SortBy.entries[(sort.ordinal + 1) % SortBy.entries.size] })
                    }
                    if (assetsPage) {
                        items(classes) { (key, value) ->
                            val i = classes.indexOfFirst { it.first == key }
                            BreakdownRow(color = palette[i % palette.size], title = S.assetClass(key), subtitle = Fmt.money(value), share = if (brutto > 0) (klassenVolumen[key] ?: 0.0) / brutto * 100 else 0.0)
                        }
                    } else {
                        items(positions, key = { it.key }) { vp ->
                            BreakdownRow(
                                color = farbeVon[vp.key] ?: palette[0], title = positionsName(vp.position), subtitle = Fmt.money(vp.value),
                                share = if (brutto > 0) kotlin.math.abs(vp.value) / brutto * 100 else 0.0,
                                change = if (sort == SortBy.PROFIT) vp.profitPct else null,
                                logo = vp.position.symbol,
                            ) { onOpen(vp.position.symbol) }
                        }
                    }
                    item {
                        SectionHeader(S.keyFigures)
                        KeyValueRow(S.positions, valuation.positions.size.toString())
                        // Einsatz: Einstand der gekauften und Praemie der verkauften Positionen
                        val einsatz = valuation.positions.sumOf { abs(it.invested) }
                        val signal = LocalSignal.current
                        val gewinn = valuation.positions.sumOf { it.profit }
                        KeyValueRow(
                            S.resultSinceBuy, Fmt.signedMoney(gewinn), valueColor = signal.forChange(gewinn),
                            hint = Fmt.signedPct(einsatz.takeIf { it > 0 }?.let { gewinn / it * 100 } ?: 0.0),
                        )
                        KeyValueRow(S.maxDrawdown, Fmt.signedPct(-abs(drawdown)), valueColor = if (drawdown != 0.0) signal.down else null, onClick = { onWerkzeug(PortfolioTool.PERFORMANCE.name) })
                        de.troetenrepublik.app.engine.Rueckgang.unvollstaendigAb(account)?.let {
                            Text(S.maxDrawdownAb(Fmt.date(it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp))
                        }
                        positions.maxByOrNull { it.value }?.let { NamenWertZeile(S.largestPosition, positionsKandidaten(it.position.symbol, it.position.name), Fmt.pct(if (brutto > 0) it.value / brutto * 100 else 0.0)) }
                        KeyValueRow(S.assetClasses, classes.size.toString())
                        Spacer(Modifier.height(40.dp))
                    }
                }
            }
        }
    }
    // Das Werkzeug bleibt gemerkt und kommt nach dem Zurueck mit Analytics wieder
    tool?.let { t -> PortfolioToolSheet(state, t, onDismiss = { onWerkzeug(null) }, onOpen = onOpen, sofort = sofort) }
    if (verlauf) DepotVerlaufBlatt(state, onDismiss = { onWerkzeug(null) }, onOpen = onOpen, sofort = sofort)
}

internal fun classOf(vp: ValuedPosition): String = when {
    vp.position.derivative != null -> "DERIVATIVES"
    else -> when (Symbols.typeOf(vp.position.symbol)) {
        AssetType.ETF -> "ETFS"
        AssetType.CRYPTO -> "CRYPTO"
        AssetType.INDEX -> "INDEX"
        else -> "STOCKS"
    }
}

// colors je Anteil in derselben Reihenfolge wie values.
@Composable
private fun Donut(values: List<Double>, colors: List<Color>, modifier: Modifier) {
    val total = values.sum()
    val empty = MaterialTheme.colorScheme.surfaceContainerHigh
    Canvas(modifier) {
        val stroke = 26.dp.toPx()
        val rect = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(stroke / 2, stroke / 2)
        if (total <= 0 || colors.isEmpty()) {
            drawArc(empty, 0f, 360f, false, topLeft, rect, style = Stroke(stroke, cap = StrokeCap.Butt))
            return@Canvas
        }
        var start = -90f
        values.forEachIndexed { i, v ->
            val sweep = (v / total * 360f).toFloat()
            drawArc(colors[i % colors.size], start, (sweep - 2f).coerceAtLeast(0.5f), false, topLeft, rect, style = Stroke(stroke, cap = StrokeCap.Butt))
            start += sweep
        }
    }
}

@Composable
private fun BreakdownRow(color: Color, title: String, subtitle: String, share: Double, change: Double? = null, logo: String? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (logo != null) InstrumentLogo(logo, title, size = 36) else Box(Modifier.size(36.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (logo != null) { Box(Modifier.size(8.dp).clip(CircleShape).background(color)); Spacer(Modifier.width(6.dp)) }
                // Immer einzeilig, lange Namen gekuerzt
                NamenText(title, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleSmall)
            }
            PunktText(subtitle)
        }
        if (change != null) MetricValue(change, Fmt.pct(abs(change)))
        else Text(Fmt.pct(share, 1), style = MaterialTheme.typography.titleSmall)
    }
}
