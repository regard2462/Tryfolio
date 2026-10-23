package de.troetenrepublik.app.ui

import de.troetenrepublik.app.plattform.mausWischen
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import de.troetenrepublik.app.plattform.horizontalScrollMaus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Statistics
import de.troetenrepublik.app.i18n.S
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

object StatColors {
    val income = Color(0xFF5AC85A)
    val expenses = Color(0xFF7A47E8)
    val investments = Color(0xFF1E5EFF)

    fun forId(id: String): Color = when (id) {
        "INCOME" -> income; "EXPENSES" -> expenses; "INVESTMENTS" -> investments
        else -> Color(0xFF6E7075)
    }

    // Abstufung innerhalb einer Kategorie: erste Blase kraeftig, weitere dunkler.
    fun shade(base: Color, index: Int): Color {
        val f = (1f - 0.18f * index).coerceAtLeast(0.4f)
        return Color(base.red * f, base.green * f, base.blue * f, 1f)
    }
}

// Statistik je Zeitraum mit Blasen nach Betrag, aufschluesselbar bis zum einzelnen Wertpapier, und Vergleich
// mit dem Vorzeitraum. Die Zeitraeume sind Seiten, zwischen denen man waagerecht wischt.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsSheet(
    state: AppState,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
    // Zeitraum (Seite) beim Oeffnen; ohne Angabe der laufende Monat. onSeite meldet jeden Wechsel (siehe Wiederkehr).
    seite: Int? = null,
    onSeite: (Int) -> Unit = {},
    sofort: Boolean = false,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = blattZustand(sofort),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        StatisticsContent(state, onOpen, Modifier, seite, onSeite)
    }
}

// Bild der Statistik unter einer aus ihr geoeffneten Seite (siehe BlattBild).
@Composable
fun StatistikBild(state: AppState, seite: Int?) = StatisticsContent(state, onOpen = {}, modifier = Modifier, seite = seite, onSeite = {})

@Composable
private fun StatisticsContent(state: AppState, onOpen: (String) -> Unit, modifier: Modifier, seite: Int? = null, onSeite: (Int) -> Unit = {}) {
    val account by state.account.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()
    val months = remember(account.transactions.size) { Statistics.months(account.transactions, now) }
    val year = YearMonth.now().year
    val periods = remember(months, year) {
        months.map<YearMonth, Statistics.Period> { Statistics.Period.Month(it) } + Statistics.Period.Year(year) + Statistics.Period.All
    }
    val start = periods.indexOf(Statistics.Period.Month(YearMonth.now())).takeIf { it >= 0 } ?: (periods.size - 3).coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = (seite ?: start).coerceIn(0, (periods.size - 1).coerceAtLeast(0))) { periods.size }
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { onSeite(it) } }
    val scope = rememberCoroutineScope()

    // Die Leiste folgt der Seite: der gewaehlte Zeitraum bleibt sichtbar
    val scroll = rememberScrollState()
    val chipX = remember { mutableStateMapOf<Int, Int>() }
    val density = LocalDensity.current
    LaunchedEffect(pager.currentPage, chipX.size) {
        val x = chipX[pager.currentPage] ?: return@LaunchedEffect
        val target = (x - with(density) { 60.dp.roundToPx() }).coerceIn(0, scroll.maxValue)
        scroll.animateScrollTo(target)
    }

    Column(Modifier.fillMaxWidth()) {
        Text(S.statistics, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = 20.dp, top = 4.dp))
        Text(
            S.statisticsIntro, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        // Zeitraum: Monate, dann Jahr und Gesamt
        Row(Modifier.fillMaxWidth().horizontalScrollMaus(scroll).padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            periods.forEachIndexed { i, p ->
                val label = when (p) {
                    is Statistics.Period.Month -> Fmt.monthChip(p.ym)
                    is Statistics.Period.Year -> S.year + " ${p.year}"
                    is Statistics.Period.All -> S.allTime
                }
                Box(Modifier.onGloballyPositioned { chipX[i] = it.positionInParent().x.roundToInt() }) {
                    PeriodChip(label, pager.currentPage == i) { scope.launch { pager.animateScrollToPage(i) } }
                }
            }
        }
        HorizontalPager(state = pager, beyondViewportPageCount = 1, modifier = Modifier.fillMaxWidth().weight(1f, fill = false).mausWischen(pager).then(modifier)) { page ->
            PeriodPage(account.transactions, periods[page], months, onOpen)
        }
    }
}

// Der Pfad durch die Blasen gilt nur fuer diese Seite.
@Composable
private fun PeriodPage(
    transactions: List<de.troetenrepublik.app.data.Transaction>,
    period: Statistics.Period,
    months: List<YearMonth>,
    onOpen: (String) -> Unit,
) {
    var path by remember(period) { mutableStateOf(listOf<String>()) }

    val root = remember(transactions, period) { Statistics.tree(transactions, period) }
    val prevRoot = remember(transactions, period) { Statistics.previous(period)?.let { Statistics.tree(transactions, it) } }
    val current = remember(root, path) { path.fold(root as Statistics.Node?) { node, id -> node?.children?.firstOrNull { it.id == id } } ?: root }
    val prevCurrent = remember(prevRoot, path) { prevRoot?.let { r -> path.fold(r as Statistics.Node?) { node, id -> node?.children?.firstOrNull { it.id == id } } } }

    fun label(node: Statistics.Node): String = if (node.id.startsWith("SEC:")) node.name else S.statNode(node.id)
    fun colorOf(node: Statistics.Node, index: Int): Color {
        val top = path.firstOrNull() ?: node.id
        val base = StatColors.forId(top)
        return if (path.isEmpty()) StatColors.forId(node.id) else StatColors.shade(base, index)
    }

    LazyColumn(Modifier.fillMaxSize()) {
            item {
                if (path.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Crumb(S.overview, false) { path = emptyList() }
                        var acc: Statistics.Node? = root
                        path.forEachIndexed { i, id ->
                            acc = acc?.children?.firstOrNull { it.id == id }
                            Text("  ›  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Crumb(acc?.let { label(it) } ?: id, i == path.lastIndex) { path = path.take(i + 1) }
                        }
                    }
                }
                val nodes = current.children.ifEmpty { listOf(current) }
                if (current.amount <= 0) {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text(S.noStatistics, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 30.dp))
                    }
                } else {
                    BubbleField(
                        nodes = nodes,
                        color = { n, i -> colorOf(n, i) },
                        label = { label(it) },
                        modifier = Modifier.fillMaxWidth().height(340.dp).padding(horizontal = 12.dp),
                        onTap = { n ->
                            when {
                                n.children.isNotEmpty() -> path = path + n.id
                                n.symbol.isNotBlank() -> onOpen(n.symbol)
                            }
                        },
                    )
                    Text(
                        if (current.children.any { it.children.isNotEmpty() || it.symbol.isNotBlank() }) S.tapBubbleHint else "",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                val net = Statistics.net(root)
                val rate = Statistics.investRate(root)
                val monthsIn = when (val p = period) {
                    is Statistics.Period.Month -> 1
                    is Statistics.Period.Year -> if (p.year == YearMonth.now().year) YearMonth.now().monthValue else 12
                    is Statistics.Period.All -> months.size.coerceAtLeast(1)
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(S.netCashflow, Fmt.euro(net), if (net >= 0) LocalSignal.current.up else LocalSignal.current.down, Modifier.weight(1f))
                    StatCard(S.investRate, Fmt.pct(rate, 0), null, Modifier.weight(1f))
                    if (monthsIn > 1) StatCard(S.averagePerMonth, Fmt.compactEuro(root.amount / monthsIn), null, Modifier.weight(1f))
                }
            }

            item {
                Text(
                    if (path.isEmpty()) S.categories else label(current),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 6.dp),
                )
            }
            val rows = current.children.ifEmpty { listOf(current) }
            items(rows, key = { it.id }) { n ->
                val i = rows.indexOf(n)
                val prev = prevCurrent?.let { p -> if (p.id == n.id) p else Statistics.find(p, n.id) }
                val share = if (current.amount > 0 && current.children.isNotEmpty()) n.amount / current.amount * 100 else 100.0
                val delta = prev?.takeIf { it.amount > 0 }?.let { (n.amount / it.amount - 1.0) * 100.0 }
                val expense = n.id == "EXPENSES" || path.firstOrNull() == "EXPENSES"
                CategoryRow(
                    color = colorOf(n, i), title = label(n), amount = n.amount, share = share, delta = delta, count = n.count,
                    invert = expense,
                    logo = n.symbol.takeIf { it.isNotBlank() },
                    onClick = when {
                        n.children.isNotEmpty() -> { { path = path + n.id } }
                        n.symbol.isNotBlank() -> { { onOpen(n.symbol) } }
                        else -> null
                    },
                )
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
}

@Composable
private fun PeriodChip(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.titleMedium,
        color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clip(RoundedCornerShape(Radius.chip)).selectable(active, role = Role.Tab, onClick = onClick).padding(horizontal = 10.dp, vertical = 10.dp),
    )
}

@Composable
private fun Crumb(label: String, current: Boolean, onClick: () -> Unit) {
    Text(
        label, style = MaterialTheme.typography.labelMedium,
        color = if (current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clip(RoundedCornerShape(Radius.chip)).clickable(onClick = onClick).padding(vertical = 6.dp, horizontal = 2.dp),
    )
}

@Composable
private fun StatCard(title: String, value: String, color: Color?, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(Radius.card)).background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp)) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, color = color ?: MaterialTheme.colorScheme.onSurface)
    }
}

// Blasen nach Betrag in Kreispackung, mit Einblendanimation und Antippen.
@Composable
private fun BubbleField(
    nodes: List<Statistics.Node>,
    color: (Statistics.Node, Int) -> Color,
    label: (Statistics.Node) -> String,
    modifier: Modifier,
    onTap: (Statistics.Node) -> Unit,
) {
    val packed = remember(nodes) { Statistics.pack(nodes) }
    BoxWithConstraints(modifier) {
        val w = maxWidth; val h = maxHeight
        val minX = packed.minOf { it.x - it.r }; val maxX = packed.maxOf { it.x + it.r }
        val minY = packed.minOf { it.y - it.r }; val maxY = packed.maxOf { it.y + it.r }
        val scale = minOf(w.value / (maxX - minX).coerceAtLeast(0.1), h.value / (maxY - minY).coerceAtLeast(0.1)).toFloat()
        val cx = (minX + maxX) / 2; val cy = (minY + maxY) / 2
        packed.forEachIndexed { i, b ->
            val grow by animateFloatAsState(1f, animationSpec = tween(350, delayMillis = i * 40), label = "grow")
            val appear = remember { mutableStateOf(0f) }
            LaunchedEffect(b.node.id) { appear.value = 1f }
            val g by animateFloatAsState(appear.value, animationSpec = tween(400, delayMillis = i * 50), label = "appear")
            val r = (b.r * scale * g * grow).dp
            val x = (w.value / 2 + (b.x - cx) * scale).dp - r
            val y = (h.value / 2 + (b.y - cy) * scale).dp - r
            val c = color(b.node, nodes.indexOf(b.node))
            Column(
                Modifier
                    .offset(x = x, y = y)
                    .size(r * 2)
                    .clip(CircleShape)
                    .background(c)
                    .clickable { onTap(b.node) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val big = r.value >= 44
                if (r.value >= 26) {
                    SchrumpfText(
                        Fmt.compactEuro(b.node.amount),
                        color = Color.Black,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = (if (big) 20 else 14).sp),
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
                // Mittlere Kreise bekommen die Beschriftung kleiner, damit kein Kreis namenlos bleibt
                if (r.value >= 30) {
                    SchrumpfText(
                        label(b.node), color = Color.Black.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = (if (big) 12 else 10).sp, lineHeight = (if (big) 16 else 12).sp),
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(color: Color, title: String, amount: Double, share: Double, delta: Double?, count: Int, logo: String?, invert: Boolean = false, onClick: (() -> Unit)?) {
    val signal = LocalSignal.current
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (logo != null) InstrumentLogo(logo, title, size = 36) else Box(Modifier.size(28.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            NamenText(title, style = MaterialTheme.typography.titleSmall)
            Text(
                Fmt.euro(amount) + (if (count > 0) " · " + S.bookingsCount(count) else ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Fmt.pct(share, 0), style = MaterialTheme.typography.titleSmall)
            if (delta != null) {
                // Bei Ausgaben ist ein Anstieg schlecht (rot); Vervielfachungen werden gedeckelt angezeigt
                val tone = signal.forChange(if (invert) -delta else delta)
                val text = if (abs(delta) > 999) "> " + Fmt.pct(999.0, 0) else Fmt.pct(abs(delta), 0)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DirectionTriangle(delta, width = 8.dp, color = tone)
                    Spacer(Modifier.width(4.dp))
                    Text(text, style = MaterialTheme.typography.labelSmall, color = tone)
                }
            }
        }
        if (onClick != null) { Spacer(Modifier.width(10.dp)); ChevronRight(height = 13.dp) }
    }
}

private fun Double.roundTo(d: Int): Double { val f = Math.pow(10.0, d.toDouble()); return (this * f).roundToInt() / f }
