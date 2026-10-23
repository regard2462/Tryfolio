package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import de.troetenrepublik.app.plattform.horizontalScrollMaus
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.Analysis
import de.troetenrepublik.app.engine.Tax
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.Symbols
import kotlin.math.abs

// Werkzeuge am Ende der Wertpapierseite. Sie rechnen mit den echten Tagesschlusskursen des Werts
// (bis zu zehn Jahre, in Euro), seinen Dividenden und Kennzahlen.
enum class StockTool { LUMP_SUM, SAVINGS_PLAN, RISK, DIVIDENDS, FAIR_VALUE, POSITION_SIZE, SELL_TAX }

fun toolsFor(type: AssetType, holds: Boolean, profile: MarketApi.Profile?): List<StockTool> = buildList {
    add(StockTool.LUMP_SUM)
    add(StockTool.SAVINGS_PLAN)
    add(StockTool.RISK)
    if (type == AssetType.STOCK || type == AssetType.ETF) add(StockTool.DIVIDENDS)
    if (type == AssetType.STOCK && (profile?.eps ?: 0.0) > 0) add(StockTool.FAIR_VALUE)
    if (type.directlyTradable) add(StockTool.POSITION_SIZE)
    if (holds) add(StockTool.SELL_TAX)
}

@Composable
fun StockToolsSection(tools: List<StockTool>, onOpen: (StockTool) -> Unit) {
    SectionHeader(S.toolsTitle)
    tools.forEach { t -> NavRow(S.toolTitle(t.name), S.toolSubtitle(t.name)) { onOpen(t) } }
}

private const val WORLD_ETF = "EUNL.DE"
private const val YEAR_MS = 365L * 86_400_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockToolSheet(state: AppState, tool: StockTool, symbol: String, name: String, profile: MarketApi.Profile?, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Text(S.toolTitle(tool.name), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp))
            Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(8.dp))
            when (tool) {
                StockTool.LUMP_SUM -> LumpSumTool(state, symbol)
                StockTool.SAVINGS_PLAN -> SavingsPlanTool(state, symbol)
                StockTool.RISK -> RiskTool(state, symbol)
                StockTool.DIVIDENDS -> DividendTool(state, symbol)
                StockTool.FAIR_VALUE -> FairValueTool(state, symbol, profile)
                StockTool.POSITION_SIZE -> PositionSizeTool(state, symbol)
                StockTool.SELL_TAX -> SellTaxTool(state, symbol)
            }
        }
    }
}

// Bausteine

// null, solange noch geladen wird.
@Composable
private fun rememberDaily(state: AppState, symbol: String): List<Candle>? {
    var daily by remember(symbol) { mutableStateOf<List<Candle>?>(null) }
    LaunchedEffect(symbol) { daily = runCatching { state.market.daily(symbol) }.getOrDefault(emptyList()) }
    return daily
}

@Composable
private fun <T> ChoiceRow(label: String, options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 6.dp))
    Row(Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (l, v) -> Chip(l, v == selected) { onSelect(v) } }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
}

@Composable
private fun showsNoData(daily: List<Candle>?): Boolean {
    if (daily == null) { Note(S.toolLoading); return true }
    if (daily.isEmpty()) { Note(S.toolNoData); return true }
    return false
}

private fun years(daily: List<Candle>): Double = if (daily.isEmpty()) 0.0 else (System.currentTimeMillis() - daily.first().t) / YEAR_MS.toDouble()

// 1, 3, 5, 10 Jahre, soweit Kurse vorliegen, sonst Max.
private fun periodOptions(daily: List<Candle>): List<Pair<String, Int>> {
    val have = years(daily)
    val base = listOf(1, 3, 5, 10).filter { it <= have + 0.05 }.map { S.toolYears(it) to it }
    return if (base.isEmpty() || have < 9.9) base + (S.toolMax to 0) else base
}

private fun startFor(daily: List<Candle>, yearsBack: Int): Long =
    if (yearsBack <= 0) daily.firstOrNull()?.t ?: 0L else System.currentTimeMillis() - yearsBack * YEAR_MS

private fun signed(v: Double): String = (if (v >= 0) "+" else "−") + Fmt.pct(abs(v))

@Composable
private fun ToolChart(series: List<Pair<List<Pair<Long, Double>>, Color>>, fillFirst: Boolean = false, baseline: Double? = null, legend: List<Pair<String, Color>> = emptyList()) {
    val grid = MaterialTheme.colorScheme.outline
    Canvas(Modifier.fillMaxWidth().height(170.dp).padding(horizontal = 20.dp, vertical = 8.dp)) {
        val pts = series.flatMap { it.first }
        if (pts.size < 2) return@Canvas
        val tMin = pts.minOf { it.first }.toFloat(); val tMax = pts.maxOf { it.first }.toFloat()
        var yMin = pts.minOf { it.second }; var yMax = pts.maxOf { it.second }
        baseline?.let { yMin = minOf(yMin, it); yMax = maxOf(yMax, it) }
        if (yMax - yMin < 1e-9) { yMax += 1; yMin -= 1 }
        fun x(t: Long) = (t - tMin) / (tMax - tMin).coerceAtLeast(1f) * size.width
        fun y(v: Double) = size.height - ((v - yMin) / (yMax - yMin)).toFloat() * size.height
        baseline?.let {
            drawLine(grid, Offset(0f, y(it)), Offset(size.width, y(it)), strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        }
        series.forEachIndexed { i, (s, color) ->
            if (s.size < 2) return@forEachIndexed
            val path = Path()
            s.forEachIndexed { j, (t, v) -> if (j == 0) path.moveTo(x(t), y(v)) else path.lineTo(x(t), y(v)) }
            if (i == 0 && fillFirst) {
                val fill = Path().apply { addPath(path); lineTo(x(s.last().first), y(baseline ?: yMin)); lineTo(x(s.first().first), y(baseline ?: yMin)); close() }
                drawPath(fill, color.copy(alpha = 0.16f))
            }
            drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
        }
    }
    if (legend.isNotEmpty()) {
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            legend.forEach { (label, color) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(6.dp))
                    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// Einmalanlage im Rueckblick

@Composable
private fun LumpSumTool(state: AppState, symbol: String) {
    val quotes by state.quotes.collectAsStateWithLifecycle()
    val daily = rememberDaily(state, symbol)
    val world = rememberDaily(state, WORLD_ETF)
    var amount by remember { mutableDoubleStateOf(1_000.0) }
    var period by remember { mutableIntStateOf(5) }
    if (showsNoData(daily)) return
    val d = daily!!
    val options = periodOptions(d)
    val chosen = if (options.any { it.second == period }) period else options.lastOrNull { it.second > 0 }?.second ?: 0
    ChoiceRow(S.toolAmount, listOf(500.0, 1_000.0, 5_000.0, 10_000.0).map { Fmt.euro(it, 0) to it }, amount) { amount = it }
    ToolZeit(S.toolPeriod, options, chosen) { period = it }
    val r = Analysis.lumpSum(d, amount, startFor(d, chosen), quotes[symbol]?.price) ?: run { Note(S.toolNoData); return }
    val w = world?.takeIf { symbol != WORLD_ETF && it.isNotEmpty() }?.let { Analysis.lumpSum(it, amount, r.start.t, quotes[WORLD_ETF]?.price) }
    val signal = LocalSignal.current
    val main = MaterialTheme.colorScheme.onSurface
    val grey = MaterialTheme.colorScheme.onSurfaceVariant
    Spacer(Modifier.height(8.dp))
    ToolChart(
        listOfNotNull(r.path to main, w?.let { it.path to grey }), baseline = amount,
        legend = listOfNotNull(Symbols.name(symbol).take(24) to main, w?.let { S.toolWorldEtfShort to grey }),
    )
    KeyValueRow(S.toolBoughtOn(Fmt.date(r.start.t), Fmt.priceEuro(r.start.close)), "${Fmt.qty(r.units)} ${S.pieces}")
    KeyValueRow(S.importValueToday, Fmt.euro(r.value))
    KeyValueRow(S.result, "${Fmt.signedEuro(r.gain)}  ${signed(r.pct)}", valueColor = signal.forChange(r.gain))
    r.perYear?.let { KeyValueRow(S.toolPerYear, signed(it)) }
    KeyValueRow(S.maxDrawdown, Fmt.pct(abs(r.maxDrawdown)))
    w?.let { KeyValueRow(S.toolWorldEtf, "${Fmt.euro(it.value)}  ${signed(it.pct)}") }
    Note(if (w != null) S.toolNoCosts + " " + S.toolWorldNote else S.toolNoCosts)
}

// Sparplan im Rueckblick

@Composable
private fun SavingsPlanTool(state: AppState, symbol: String) {
    val quotes by state.quotes.collectAsStateWithLifecycle()
    val daily = rememberDaily(state, symbol)
    val world = rememberDaily(state, WORLD_ETF)
    var monthly by remember { mutableDoubleStateOf(100.0) }
    var period by remember { mutableIntStateOf(5) }
    if (showsNoData(daily)) return
    val d = daily!!
    val options = periodOptions(d)
    val chosen = if (options.any { it.second == period }) period else options.lastOrNull { it.second > 0 }?.second ?: 0
    ChoiceRow(S.toolMonthly, listOf(25.0, 50.0, 100.0, 250.0, 500.0).map { Fmt.euro(it, 0) to it }, monthly) { monthly = it }
    ToolZeit(S.toolPeriod, options, chosen) { period = it }
    val r = Analysis.savingsPlan(d, monthly, startFor(d, chosen), quotes[symbol]?.price) ?: run { Note(S.toolNoData); return }
    val w = world?.takeIf { symbol != WORLD_ETF && it.isNotEmpty() }?.let { Analysis.savingsPlan(it, monthly, startFor(d, chosen), quotes[WORLD_ETF]?.price) }
    val signal = LocalSignal.current
    val main = MaterialTheme.colorScheme.onSurface
    val grey = MaterialTheme.colorScheme.onSurfaceVariant
    Spacer(Modifier.height(8.dp))
    ToolChart(listOf(r.path to main, r.investedPath to grey), legend = listOf(S.importValueToday to main, S.toolInvested to grey))
    KeyValueRow(S.toolInvested, "${Fmt.euro(r.invested)} · ${r.executions} ${S.toolExecutions}")
    KeyValueRow(S.importValueToday, Fmt.euro(r.value))
    KeyValueRow(S.result, "${Fmt.signedEuro(r.gain)}  ${signed(r.pct)}", valueColor = signal.forChange(r.gain))
    r.perYear?.let { KeyValueRow(S.toolPerYearMoneyWeighted, signed(it)) }
    KeyValueRow(S.toolAverageCost, Fmt.priceEuro(r.averageCost))
    w?.let { KeyValueRow(S.toolWorldEtf, "${Fmt.euro(it.value)}  ${signed(it.pct)}") }
    Note(S.toolPlanNote)
}

// Schwankung und Rueckgaenge

@Composable
private fun RiskTool(state: AppState, symbol: String) {
    val daily = rememberDaily(state, symbol)
    val world = rememberDaily(state, WORLD_ETF)
    var period by remember { mutableIntStateOf(3) }
    if (showsNoData(daily)) return
    val d = daily!!
    val options = periodOptions(d)
    val chosen = if (options.any { it.second == period }) period else options.lastOrNull { it.second > 0 }?.second ?: 0
    ToolZeit(S.toolPeriod, options, chosen) { period = it }
    val tradingDays = if (Symbols.typeOf(symbol) == AssetType.CRYPTO) 365 else 252
    val from = startFor(d, chosen)
    val r = Analysis.risk(d, from, tradingDays) ?: run { Note(S.toolNoData); return }
    val w = world?.takeIf { symbol != WORLD_ETF && it.isNotEmpty() }?.let { Analysis.risk(it, from, 252) }
    val signal = LocalSignal.current
    Spacer(Modifier.height(8.dp))
    ToolChart(listOf(r.drawdownPath to signal.down), fillFirst = true, baseline = 0.0)
    KeyValueRow(S.toolVolatility, Fmt.pct(r.volatility) + (w?.let { "  (${S.toolWorldEtfShort} ${Fmt.pct(it.volatility)})" } ?: ""))
    KeyValueRow(S.maxDrawdown, "−" + Fmt.pct(abs(r.maxDrawdown)) + (w?.let { "  (${S.toolWorldEtfShort} −${Fmt.pct(abs(it.maxDrawdown))})" } ?: ""))
    KeyValueRow(S.toolDrawdownPeriod, S.toolDrawdownRange(Fmt.date(r.peakAt), Fmt.date(r.troughAt)))
    KeyValueRow(S.toolRecovered, r.recoveredAt?.let { Fmt.date(it) } ?: S.toolNotRecovered)
    r.worstDay?.let { KeyValueRow(S.toolWorstDay, "${signed(it.second)} · ${Fmt.date(it.first)}", valueColor = signal.down) }
    r.bestDay?.let { KeyValueRow(S.toolBestDay, "${signed(it.second)} · ${Fmt.date(it.first)}", valueColor = signal.up) }
    KeyValueRow(S.toolUpDays, Fmt.pct(r.upDays, 0))
    Note(if (w != null) S.toolRiskNote + " " + S.toolRiskWorldNote else S.toolRiskNote)
}

// Dividenden

@Composable
private fun DividendTool(state: AppState, symbol: String) {
    val quotes by state.quotes.collectAsStateWithLifecycle()
    val account by state.account.collectAsStateWithLifecycle()
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    var events by remember(symbol) { mutableStateOf<List<MarketApi.DividendEvent>?>(null) }
    LaunchedEffect(symbol) { events = runCatching { state.market.dividendHistory(symbol) }.getOrDefault(emptyList()) }
    val held = account.positions.firstOrNull { it.key == symbol && it.derivative == null }?.qty
    var shares by remember(held) { mutableDoubleStateOf(held ?: 10.0) }
    val ev = events ?: run { Note(S.toolLoading); return }
    if (ev.isEmpty()) { Note(S.toolNoDividends); return }
    val r = Analysis.dividends(ev)
    val price = quotes[symbol]?.price ?: 0.0
    val options = buildList {
        if (held != null) add(S.toolMyHolding(Fmt.qty(held)) to held)
        listOf(10.0, 50.0, 100.0, 500.0).filter { it != held }.forEach { add(Fmt.qty(it) to it) }
    }
    ChoiceRow(S.toolShares, options, shares) { shares = it }
    Spacer(Modifier.height(8.dp))
    val bars = r.byYear.takeLast(10)
    BarChart(bars.map { it.second }, bars.map { "'" + (it.first % 100).toString().padStart(2, '0') }, LocalSignal.current.up,
        Modifier.fillMaxWidth().height(150.dp).padding(horizontal = 20.dp, vertical = 8.dp))
    KeyValueRow(S.toolDividendTrailing, S.toolPerShare(Fmt.priceEuro(r.trailing)))
    if (price > 0) KeyValueRow(S.toolDividendYield, Fmt.pct(r.trailing / price * 100.0))
    KeyValueRow(S.toolPayments, r.payments.toString())
    r.growth?.let { KeyValueRow(S.toolDividendGrowth, signed(it)) }
    val gross = r.trailing * shares
    val allowanceLeft = (minOf(settings.allowance, Tax.MAX_ALLOWANCE) - account.tax.allowanceUsed).coerceAtLeast(0.0)
    val tax = Analysis.taxOn(gross, allowanceLeft)
    KeyValueRow(S.toolGrossPerYear, Fmt.euro(gross))
    KeyValueRow(S.toolTaxApprox, Fmt.euro(-tax))
    KeyValueRow(S.toolNetPerYear, Fmt.euro(gross - tax))
    KeyValueRow(S.toolNetPerMonth, Fmt.euro((gross - tax) / 12.0))
    Note(S.toolDividendNote)
}

// Fairer Wert

@Composable
private fun FairValueTool(state: AppState, symbol: String, profile: MarketApi.Profile?) {
    val quotes by state.quotes.collectAsStateWithLifecycle()
    val q = quotes[symbol]
    val eps = profile?.eps ?: 0.0
    var growth by remember { mutableDoubleStateOf(10.0) }
    var years by remember { mutableIntStateOf(5) }
    var exitPe by remember { mutableDoubleStateOf(20.0) }
    var required by remember { mutableDoubleStateOf(8.0) }
    if (q == null || eps <= 0 || q.price <= 0) { Note(S.toolNoData); return }
    // Gewinn je Aktie und Kurs in der Handelswaehrung, Anzeige in Euro
    val nativePrice = q.nativePrice.takeIf { it > 0 } ?: q.price
    val fx = q.price / nativePrice
    ChoiceRow(S.toolGrowth, listOf(0.0, 5.0, 10.0, 15.0, 20.0, 25.0).map { Fmt.pct(it, 0) to it }, growth) { growth = it }
    ChoiceRow(S.toolPeriod, listOf(5, 10).map { S.toolYears(it) to it }, years) { years = it }
    ChoiceRow(S.toolExitPe, listOf(10.0, 15.0, 20.0, 25.0, 30.0).map { Fmt.num(it, 0) to it }, exitPe) { exitPe = it }
    ChoiceRow(S.toolRequired, listOf(6.0, 8.0, 10.0, 12.0).map { Fmt.pct(it, 0) to it }, required) { required = it }
    val r = Analysis.fairValue(eps, nativePrice, growth, years, exitPe, required) ?: run { Note(S.toolNoData); return }
    Spacer(Modifier.height(10.dp))
    KeyValueRow(S.toolEpsNow, Fmt.priceEuro(eps * fx))
    KeyValueRow(S.toolPeNow, Fmt.num(r.peNow, 1))
    KeyValueRow(S.toolEpsIn(years), Fmt.priceEuro(r.epsFuture * fx))
    KeyValueRow(S.toolPriceIn(years), Fmt.priceEuro(r.priceFuture * fx))
    KeyValueRow(S.toolFairToday, Fmt.priceEuro(r.fair * fx))
    KeyValueRow(
        S.toolCurrentPrice,
        Fmt.priceEuro(q.price) + "  " + if (r.premium >= 0) S.toolAbove(Fmt.pct(r.premium, 0)) else S.toolBelow(Fmt.pct(abs(r.premium), 0)),
        valueColor = LocalSignal.current.forChange(-r.premium),
    )
    KeyValueRow(S.toolImpliedReturn, signed(r.impliedReturn))
    Note(S.toolFairNote)
}

// Positionsgroesse

@Composable
private fun PositionSizeTool(state: AppState, symbol: String) {
    val quotes by state.quotes.collectAsStateWithLifecycle()
    val valuation by state.valuation.collectAsStateWithLifecycle()
    val account by state.account.collectAsStateWithLifecycle()
    var risk by remember { mutableDoubleStateOf(1.0) }
    var stop by remember { mutableDoubleStateOf(8.0) }
    val price = quotes[symbol]?.price ?: 0.0
    if (price <= 0 || valuation.total <= 0) { Note(S.toolNoData); return }
    ChoiceRow(S.toolRiskPerTrade, listOf(0.5, 1.0, 2.0, 3.0).map { Fmt.pct(it, if (it % 1.0 == 0.0) 0 else 1) to it }, risk) { risk = it }
    ChoiceRow(S.toolStopDistance, listOf(3.0, 5.0, 8.0, 10.0, 15.0, 20.0).map { Fmt.pct(it, 0) to it }, stop) { stop = it }
    val whole = Symbols.typeOf(symbol) != AssetType.CRYPTO
    val r = Analysis.positionSize(valuation.total, price, risk, stop, whole) ?: run { Note(S.toolNoData); return }
    Spacer(Modifier.height(10.dp))
    KeyValueRow(S.toolDepotValue, Fmt.euro(valuation.total))
    KeyValueRow(S.toolStopPrice, Fmt.priceEuro(r.stopPrice))
    KeyValueRow(S.toolLossPerUnit, Fmt.priceEuro(r.lossPerUnit))
    KeyValueRow(S.toolMaxLoss, Fmt.euro(r.maxLoss))
    KeyValueRow(S.piecesLabel, Fmt.qty(r.units))
    KeyValueRow(S.toolCapital, "${Fmt.euro(r.capital)} · ${S.toolShareOfDepot(Fmt.pct(r.shareOfDepot, 0))}")
    if (r.capital > account.cash) Note(S.toolMoreThanCash)
    Note(S.toolPositionNote)
}

// Verkauf mit Steuer

@Composable
private fun SellTaxTool(state: AppState, symbol: String) {
    val account by state.account.collectAsStateWithLifecycle()
    val pos = account.positions.firstOrNull { it.key == symbol && it.derivative == null } ?: run { Note(S.toolNoData); return }
    var share by remember { mutableDoubleStateOf(100.0) }
    ChoiceRow(S.toolSellShare, listOf(25.0, 50.0, 75.0, 100.0).map { Fmt.pct(it, 0) to it }, share) { share = it }
    val qty = if (share >= 100.0) pos.qty else pos.qty * share / 100.0
    val tx = state.previewSell(pos.key, qty) ?: run { Note(S.toolNoData); return }
    val signal = LocalSignal.current
    Spacer(Modifier.height(10.dp))
    KeyValueRow(S.piecesLabel, Fmt.qty(qty))
    KeyValueRow(S.toolSellPrice, Fmt.priceEuro(tx.price))
    KeyValueRow(S.toolProceeds, Fmt.euro(tx.qty * tx.price))
    KeyValueRow(S.importCostBasis, Fmt.euro(tx.qty * pos.avgPrice))
    KeyValueRow(S.toolRealized, Fmt.signedEuro(tx.realized), valueColor = signal.forChange(tx.realized))
    KeyValueRow(S.toolTax, Fmt.euro(-tx.tax))
    KeyValueRow(S.toolFee, Fmt.euro(-tx.fee))
    KeyValueRow(S.toolCredit, Fmt.euro(tx.amount - tx.fee))
    Note(S.toolSellNote)
}
