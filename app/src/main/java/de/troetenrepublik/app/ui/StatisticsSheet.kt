package de.troetenrepublik.app.ui

import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import de.troetenrepublik.app.plattform.mausWischen
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.unit.Dp
import kotlin.math.floor
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
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
    // Aus der gemeinsamen Kategorienpalette, ohne Gewinngruen
    val income = Kategorie.farben[3]
    val expenses = Kategorie.farben[2]
    val investments = Kategorie.farben[0]

    fun forId(id: String): Color = when (id) {
        "INCOME" -> income; "EXPENSES" -> expenses; "INVESTMENTS" -> investments
        else -> Kategorie.farben[6]
    }

    // Abstufung innerhalb einer Kategorie: erste Blase kraeftig, weitere dunkler.
    fun shade(base: Color, index: Int): Color {
        val f = (1f - 0.18f * index).coerceAtLeast(0.4f)
        return Color(base.red * f, base.green * f, base.blue * f, 1f)
    }
}

// Statistik je Zeitraum mit Blasen nach Betrag, aufschluesselbar bis zum einzelnen Wertpapier, und Vergleich
// mit dem Vorzeitraum. Die Zeitraeume sind Seiten, zwischen denen man waagerecht wischt.
// Eingebettet im Cash-Reiter (seit 5.91): mit Ueberschrift als Abschnitt und Seiten ohne eigene Liste, weil sie in der
// Liste des Reiters stehen.
@Composable
fun StatistikEingebettet(state: AppState, onOpen: (String) -> Unit) =
    StatisticsContent(state, onOpen, Modifier, eingebettet = true)

@Composable
private fun StatisticsContent(state: AppState, onOpen: (String) -> Unit, modifier: Modifier, seite: Int? = null, onSeite: (Int) -> Unit = {}, eingebettet: Boolean = false) {
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

    Column(Modifier.fillMaxWidth()) {
        if (eingebettet) SectionHeader(S.statistics)
        else Text(S.statistics, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 8.dp))
        // Zeitraum: Monate, dann Jahr und Gesamt. Die weisse Pille wandert beim Wischen mit (seit 5.92).
        MitlaufendeReiter(
            anzahl = periods.size,
            stand = { pager.currentPage + pager.currentPageOffsetFraction },
            titel = { i ->
                when (val p = periods[i]) {
                    is Statistics.Period.Month -> Fmt.monthChip(p.ym)
                    is Statistics.Period.Year -> S.year + " ${p.year}"
                    is Statistics.Period.All -> S.allTime
                }
            },
            modifier = Modifier.padding(vertical = 10.dp),
            klein = true,
        ) { i -> scope.launch { pager.animateScrollToPage(i) } }
        // Natuerliche Hoehe jeder Seite in Pixeln; die Pagerhoehe interpoliert beim Wischen dazwischen (0.68)
        val hoehen = remember { mutableStateMapOf<Int, Int>() }
        // Oben ausgerichtet: eingebettet sind die Seiten verschieden hoch
        HorizontalPager(
            state = pager, beyondViewportPageCount = 1, verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth().then(
                if (eingebettet) Modifier.clipToBounds().layout { messbar, grenzen ->
                    val gemessen = messbar.measure(grenzen)
                    // Hoehe zwischen aktueller und Nachbarseite nach Wischfortschritt, hoechstens die gemessene Hoehe
                    val stand = pager.currentPage + pager.currentPageOffsetFraction
                    val a = floor(stand).toInt(); val f = stand - a
                    val ha = hoehen[a]; val hb = hoehen[a + 1]
                    val hoehe = when {
                        ha == null -> gemessen.height
                        hb == null || f == 0f -> ha
                        else -> (ha + (hb - ha) * f).roundToInt()
                    }.coerceIn(0, gemessen.height)
                    layout(gemessen.width, hoehe) { gemessen.place(0, 0) }
                } else Modifier.weight(1f, fill = false)
            ).mausWischen(pager).then(modifier),
        ) { page ->
            if (eingebettet) Box(Modifier.fillMaxWidth().onSizeChanged { hoehen[page] = it.height }) {
                PeriodPage(account.transactions, periods[page], months, onOpen, eingebettet)
            } else PeriodPage(account.transactions, periods[page], months, onOpen, eingebettet)
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
    // Ohne eigene Liste, wenn die Seite in einer anderen Liste steht (Cash-Reiter)
    eingebettet: Boolean = false,
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

    // Die Teile der Seite; sie stehen in der eigenen Liste oder eingebettet untereinander
    val blasen: @Composable () -> Unit = {
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
            Box(Modifier.fillMaxWidth().height(if (eingebettet) 120.dp else 200.dp), contentAlignment = Alignment.Center) {
                Text(S.noStatistics, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 30.dp))
            }
        } else {
            BubbleField(
                nodes = nodes,
                color = { n, i -> colorOf(n, i) },
                label = { label(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                minHoehe = if (eingebettet) 150.dp else 170.dp,
                maxHoehe = if (eingebettet) 280.dp else 340.dp,
                onTap = { n ->
                    when {
                        n.children.isNotEmpty() -> path = path + n.id
                        n.symbol.isNotBlank() -> onOpen(n.symbol)
                    }
                },
            )
        }
    }
    val karten: @Composable () -> Unit = {
        val net = Statistics.net(root)
        val rate = Statistics.investRate(root)
        val monthsIn = when (val p = period) {
            is Statistics.Period.Month -> 1
            is Statistics.Period.Year -> if (p.year == YearMonth.now().year) YearMonth.now().monthValue else 12
            is Statistics.Period.All -> months.size.coerceAtLeast(1)
        }
        val karten = buildList {
            add(Triple(S.netCashflow, Fmt.euro(net), if (net >= 0) LocalSignal.current.up else LocalSignal.current.down))
            add(Triple(S.investRate, Fmt.pct(rate, 0), null))
            if (monthsIn > 1) add(Triple(S.averagePerMonth, Fmt.compactEuro(root.amount / monthsIn), null))
        }
        StatKarten(karten, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp))
    }
    val titel: @Composable () -> Unit = {
        Text(
            if (path.isEmpty()) S.categories else label(current),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 6.dp),
        )
    }
    val rows = current.children.ifEmpty { listOf(current) }
    val zeile: @Composable (Statistics.Node) -> Unit = { n ->
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

    if (eingebettet) {
        Column(Modifier.fillMaxWidth()) {
            blasen()
            // Ohne Betraege weder Kennzahlen noch eine leere Kategorienliste
            if (root.amount > 0) {
                karten()
                titel()
                rows.forEach { n -> key(n.id) { zeile(n) } }
            }
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item { blasen() }
        item { karten() }
        item { titel() }
        items(rows, key = { it.id }) { n -> zeile(n) }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun Crumb(label: String, current: Boolean, onClick: () -> Unit) {
    Text(
        label, style = MaterialTheme.typography.labelMedium,
        color = if (current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 2.dp).textKnopf(onClick = onClick),
    )
}

// Titel und Wert bleiben einzeilig und werden bei Bedarf kleiner, statt mitten im Wort umzubrechen (seit 5.92).
@Composable
private fun StatCard(title: String, value: String, color: Color?, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(Radius.card)).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 12.dp, vertical = 14.dp)) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, color = color ?: MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false)
    }
}

// Kacheln nebeneinander, wenn jeder Titel und Wert in voller Groesse in seine Spalte passt, sonst untereinander
// ueber die volle Breite. So bleibt die Schrift immer gleich gross.
@Composable
private fun StatKarten(karten: List<Triple<String, String, Color?>>, modifier: Modifier) {
    val messer = rememberTextMeasurer()
    val titelStil = MaterialTheme.typography.labelSmall
    val wertStil = MaterialTheme.typography.titleSmall
    BoxWithConstraints(modifier) {
        val dichte = LocalDensity.current
        val abstand = 10.dp
        val spalte = (maxWidth - abstand * (karten.size - 1)) / karten.size - 24.dp
        val breit = with(dichte) {
            karten.maxOf { (t, w, _) ->
                maxOf(
                    messer.measure(t, titelStil, softWrap = false, maxLines = 1).size.width,
                    messer.measure(w, wertStil, softWrap = false, maxLines = 1).size.width,
                )
            }.toDp()
        }
        if (breit <= spalte) {
            // Gleich hohe Kacheln
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(abstand)) {
                karten.forEach { (t, w, c) -> StatCard(t, w, c, Modifier.weight(1f).fillMaxHeight()) }
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(abstand)) {
                karten.forEach { (t, w, c) -> StatCard(t, w, c, Modifier.fillMaxWidth()) }
            }
        }
    }
}

// Blasen nach Betrag in Kreispackung, mit Einblendanimation und Antippen.
@Composable
private fun BubbleField(
    nodes: List<Statistics.Node>,
    color: (Statistics.Node, Int) -> Color,
    label: (Statistics.Node) -> String,
    modifier: Modifier,
    minHoehe: Dp,
    maxHoehe: Dp,
    onTap: (Statistics.Node) -> Unit,
) {
    val packed = remember(nodes) { Statistics.pack(nodes) }
    BoxWithConstraints(modifier) {
        if (packed.isEmpty()) return@BoxWithConstraints
        val minX = packed.minOf { it.x - it.r }; val maxX = packed.maxOf { it.x + it.r }
        val minY = packed.minOf { it.y - it.r }; val maxY = packed.maxOf { it.y + it.r }
        // Hoehe folgt dem Seitenverhaeltnis der Huelle, weich animiert gegen Spruenge beim Pfadwechsel (0.68)
        val verhaeltnis = ((maxY - minY) / (maxX - minX).coerceAtLeast(0.1)).toFloat()
        val hoehe by animateDpAsState((maxWidth * verhaeltnis).coerceIn(minHoehe, maxHoehe), animationSpec = tween(300), label = "hoehe")
        // Anker-Box legt die Hoehe der Blasenflaeche fest, die Kreise fuellen sie mit w und h
        Box(Modifier.fillMaxWidth().height(hoehe))
        val w = maxWidth; val h = hoehe
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
                // Innenrand nach Groesse: Text bleibt innerhalb der Kreissehne und wird eher kleiner als abgeschnitten
                val rand = (r * 0.2f).coerceAtLeast(5.dp)
                if (r.value >= 26) {
                    SchrumpfText(
                        Fmt.compactEuro(b.node.amount),
                        color = aufFarbe(c),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = (if (big) 20 else 14).sp),
                        modifier = Modifier.padding(horizontal = rand),
                    )
                }
                // Mittlere Kreise bekommen die Beschriftung kleiner, damit kein Kreis namenlos bleibt
                if (r.value >= 30) {
                    SchrumpfText(
                        label(b.node), color = aufFarbe(c),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = (if (big) 12 else 10).sp, lineHeight = (if (big) 16 else 12).sp),
                        modifier = Modifier.padding(horizontal = rand),
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
            PunktText(
                Fmt.euro(amount) + (if (count > 0) " · " + S.bookingsCount(count) else ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Anteil an der Summe als Balken in der Farbe der Kategorie (wie die Blase darueber)
            LageBalken((share / 100.0).coerceIn(0.0, 1.0), color, Modifier.padding(top = 6.dp))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Fmt.pct(share, 0), style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
            if (delta != null) {
                // Bei Ausgaben ist ein Anstieg schlecht (rot); Vervielfachungen werden gedeckelt angezeigt
                val tone = signal.forChange(if (invert) -delta else delta)
                val text = if (abs(delta) > 999) "> " + Fmt.pct(999.0, 0) else Fmt.pct(abs(delta), 0)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DirectionTriangle(delta, width = 8.dp, color = tone)
                    Spacer(Modifier.width(4.dp))
                    Text(text, style = MaterialTheme.typography.labelSmall, color = tone, maxLines = 1, softWrap = false)
                }
            }
        }
        if (onClick != null) { Spacer(Modifier.width(10.dp)); AufklappPfeil(false) }
    }
}

private fun Double.roundTo(d: Int): Double { val f = Math.pow(10.0, d.toDouble()); return (this * f).roundToInt() / f }
