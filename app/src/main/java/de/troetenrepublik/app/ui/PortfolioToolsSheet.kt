package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import de.troetenrepublik.app.plattform.mausWischen
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.PlanInterval
import de.troetenrepublik.app.data.Range
import de.troetenrepublik.app.data.SavingsPlan
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.data.ValuedPosition
import de.troetenrepublik.app.engine.Analysis
import de.troetenrepublik.app.engine.PortfolioTools
import de.troetenrepublik.app.engine.Pricing
import de.troetenrepublik.app.engine.Tax
import de.troetenrepublik.app.engine.Trading
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Catalog
import de.troetenrepublik.app.market.Region
import de.troetenrepublik.app.market.Symbols
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// Werkzeuge in Analytics, die mit dem ganzen Depot rechnen.
enum class PortfolioTool { PERFORMANCE, CHECK, FINDER, WEALTH, WITHDRAWAL, CRASH, DIVIDENDS, DIVERSIFICATION, REBALANCE }

// Seiten im Depot-Check in dieser Reihenfolge. Sie stehen nicht einzeln in der Liste.
internal val CHECK_SEITEN = listOf(PortfolioTool.CHECK, PortfolioTool.DIVERSIFICATION, PortfolioTool.REBALANCE, PortfolioTool.CRASH)

internal val LISTEN_WERKZEUGE = PortfolioTool.entries.filter { it == PortfolioTool.CHECK || it !in CHECK_SEITEN }

// Dritte Seite von Analytics: eine Zeile je Werkzeug mit Beschreibung.
@Composable
fun PortfolioToolsList(onDepotVerlauf: (() -> Unit)? = null, onOpen: (PortfolioTool) -> Unit) {
    Spacer(Modifier.height(12.dp))
    // Depotverlauf ist ein eigenes Blatt, steht hier aber als weiteres Werkzeug
    onDepotVerlauf?.let { NavRow(S.dvTitle, S.dvSubtitle, onClick = it) }
    LISTEN_WERKZEUGE.forEach { t ->
        NavRow(S.ptTitle(t.name), S.ptSubtitle(t.name)) {
            // Aus der Liste geoeffnet beginnt der Depot-Check vorn
            if (t == PortfolioTool.CHECK) letzteCheckSeite = -1
            onOpen(t)
        }
    }
}

// Depotverlauf als Blatt, das sich wie die Werkzeuge nach unten wegwischen laesst.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepotVerlaufBlatt(state: AppState, onDismiss: () -> Unit, onOpen: (String) -> Unit, sofort: Boolean = false) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = blattZustand(sofort),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        DepotVerlaufScreen(state, onBack = onDismiss, onOpen = onOpen, imBlatt = true)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioToolSheet(state: AppState, tool: PortfolioTool, onDismiss: () -> Unit, onOpen: (String) -> Unit, sofort: Boolean = false) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = blattZustand(sofort),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        PortfolioToolInhalt(state, tool, onOpen)
    }
}

// Auch als Bild unter einer daraus geoeffneten Seite (siehe Wiederkehr).
@Composable
fun PortfolioToolInhalt(state: AppState, tool: PortfolioTool, onOpen: (String) -> Unit) {
    if (tool in CHECK_SEITEN) { DepotCheckSeiten(state, onOpen, CHECK_SEITEN.indexOf(tool)); return }
    run {
        // Immer bildschirmhoch, damit das Blatt beim Laden nicht auf halber Hoehe stehen bleibt
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Text(S.ptTitle(tool.name), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp))
            Text(S.ptSubtitle(tool.name), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(4.dp))
            when (tool) {
                PortfolioTool.PERFORMANCE -> PerformanceTool(state)
                PortfolioTool.FINDER -> DepotfinderTool(state, onOpen)
                PortfolioTool.WEALTH -> WealthTool(state)
                PortfolioTool.WITHDRAWAL -> WithdrawalTool(state)
                PortfolioTool.DIVIDENDS -> DividendsTool(state, onOpen)
                else -> {}
            }
        }
    }
}

// Damit der Depot-Check nach einem geoeffneten Wert wieder auf derselben Seite steht.
internal var letzteCheckSeite = -1

// Depot-Check mit Faustregeln, Streuung, Umschichten und Crash-Test als Seiten zum Wischen.
@Composable
private fun DepotCheckSeiten(state: AppState, onOpen: (String) -> Unit, start: Int) {
    val pager = rememberPagerState(initialPage = (if (start == 0 && letzteCheckSeite >= 0) letzteCheckSeite else start).coerceIn(0, CHECK_SEITEN.size - 1)) { CHECK_SEITEN.size }
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { letzteCheckSeite = it } }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        Text(S.ptTitle(PortfolioTool.CHECK.name), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp))
        MitlaufendeReiter(
            anzahl = CHECK_SEITEN.size,
            pager = pager,
            titel = { i -> if (i == 0) S.ptCheckRules else S.ptTitle(CHECK_SEITEN[i].name) },
            modifier = Modifier.padding(vertical = 12.dp),
        ) { i -> scope.launch { pager.animateScrollToPage(i) } }
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().mausWischen(pager), beyondViewportPageCount = 1) { seite ->
            val tool = CHECK_SEITEN[seite]
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
                Text(if (tool == PortfolioTool.CHECK) S.ptCheckRulesSub else S.ptSubtitle(tool.name), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(4.dp))
                when (tool) {
                    PortfolioTool.DIVERSIFICATION -> DiversificationTool(state, onOpen)
                    PortfolioTool.REBALANCE -> RebalanceTool(state, onOpen)
                    PortfolioTool.CRASH -> CrashTool(state, onOpen)
                    else -> DepotCheckTool(state)
                }
            }
        }
    }
}

// Hilfen

private const val DAY_MS = 86_400_000L
private const val YEAR_MS = 365.25 * 86_400_000.0

// Inflation fuer heutige Kaufkraft: das Ziel der EZB.
private const val INFLATION = 2.0

// Runde Zielbetraege ohne ,0, etwa 100 Tsd. EUR oder EUR100k.
private fun roundAmount(v: Double): String = Fmt.compactEuro(v).replace(Regex("""[,.]0(?=\D|$)"""), "")

// Ab 10.000 EUR kompakt, darunter ohne Nachkommastellen.
private fun shortMoney(v: Double): String = if (abs(v) >= 10_000.0) Fmt.compactEuro(v) else Fmt.euro(v, 0)

private fun signedPct(v: Double, digits: Int = 1): String = (if (v > 0.00001) "+" else if (v < -0.00001) "−" else "") + Fmt.pct(abs(v), digits)

// Aufrunden auf 1, 2 oder 5 mal eine Zehnerpotenz.
private fun niceCeil(v: Double): Double {
    if (v <= 0) return 1.0
    val p = 10.0.pow(kotlin.math.floor(log10(v)))
    val m = v / p
    return p * when { m <= 1.0 -> 1.0; m <= 2.0 -> 2.0; m <= 5.0 -> 5.0; else -> 10.0 }
}

private fun roundTo(v: Double, step: Double): Double = if (step <= 0) v else Math.round(v / step) * step

// Monatliche Summe aller laufenden Sparplaene.
private fun monthlyFromPlans(plans: List<SavingsPlan>): Double = plans.filter { !it.paused }.sumOf { p ->
    p.amount * when (p.interval) {
        PlanInterval.WEEKLY -> 52.0 / 12.0
        PlanInterval.BIWEEKLY -> 2.0
        PlanInterval.MONTHLY -> 1.0
        PlanInterval.QUARTERLY -> 1.0 / 3.0
    }
}

// Bezeichnung, Wert des Depots, Wert des Vergleichs, darueber die Spaltenkoepfe.
@Composable
private fun CompareRows(bench: String, rows: List<Triple<String, String, String>>) {
    val grey = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp)) {
        Spacer(Modifier.weight(1.2f))
        Text(S.ptYourDepot, style = MaterialTheme.typography.bodySmall, color = grey, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(bench, style = MaterialTheme.typography.bodySmall, color = grey, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
    rows.forEach { (label, a, b) ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = grey, modifier = Modifier.weight(1.2f))
            Text(a, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            Text(b, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun Headline(label: String, value: String, sub: String? = null, valueColor: Color? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineMedium, color = valueColor ?: MaterialTheme.colorScheme.onSurface)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// Vergleich mit dem Markt

// Thesaurierende ETFs an Xetra (Dividenden stecken im Kurs), Gold, Bitcoin, Tagesgeld ohne Symbol.
private data class Bench(val key: String, val symbol: String?)

private val BENCHES = listOf(
    Bench("MSCI_WORLD", "EUNL.DE"), Bench("FTSE_ALL_WORLD", "VWCE.DE"), Bench("MSCI_ACWI", "IUSQ.DE"),
    Bench("SP500", "SXR8.DE"), Bench("NASDAQ100", "SXRV.DE"), Bench("DAX", "EXS1.DE"),
    Bench("STOXX600", "LYP6.DE"), Bench("EM", "EUNM.DE"), Bench("GOLD", "4GLD.DE"), Bench("BITCOIN", "BTC-EUR"),
    Bench("CASH", null),
)

// Ergebnis mit demselben Geld fuer das ganze Depot und je Wert. firstPrice ist der Beginn der Kursreihe.
private class MoneyResult(val total: PortfolioTools.Replay?, val perKey: Map<String, PortfolioTools.Replay>, val firstPrice: Long?)

private suspend fun loadMoney(state: AppState, bench: Bench, flows: List<PortfolioTools.Flow>, ratePct: Double): MoneyResult {
    val now = System.currentTimeMillis()
    if (bench.symbol == null) {
        val per = flows.groupBy { it.key }.mapNotNull { (k, f) -> PortfolioTools.replayCash(f, ratePct, now)?.let { k to it } }.toMap()
        return MoneyResult(PortfolioTools.replayCash(flows, ratePct, now), per, null)
    }
    val daily = state.dailyCloses(bench.symbol)
    if (daily.isEmpty()) return MoneyResult(null, emptyMap(), null)
    val price = state.quotes.value[bench.symbol]?.price?.takeIf { it > 0 && it.isFinite() } ?: daily.last().close
    val per = flows.groupBy { it.key }.mapNotNull { (k, f) -> PortfolioTools.replay(f, daily, price)?.let { k to it } }.toMap()
    return MoneyResult(PortfolioTools.replay(flows, daily, price), per, daily.first().t)
}

// Teil des Depotverlaufs: abgeschlossene Verkaeufe und dasselbe Geld im Index, ganzes Depot und je Wert.
@Composable
internal fun MarktVergleichTeile(state: AppState, onOpen: (String) -> Unit) {
    val account = depotKonto(state)
    val valuation = depotBewertung(state)
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val signal = LocalSignal.current
    var benchKey by rememberSaveable { mutableStateOf("MSCI_WORLD") }
    val bench = BENCHES.firstOrNull { it.key == benchKey } ?: BENCHES.first()
    val name = S.ptBenchName(bench.key)
    val flows = remember(account.transactions) { PortfolioTools.flows(account) }
    if (flows.none { it.amount > 0 }) return

    // Abgeschlossene Verkaeufe: Trefferquote, Gewinnfaktor, mittlerer Gewinn und Verlust
    val verkaeufe = remember(account.transactions.size) {
        de.troetenrepublik.app.engine.Risiko.verkaeufe(account.transactions.filter { it.type == de.troetenrepublik.app.data.TxType.SELL }.map { it.realized })
    }
    verkaeufe?.let { v ->
        SectionHeader(S.ptClosedSales)
        Text(
            S.ptClosedSalesLine(
                v.anzahl, v.gewinne, Fmt.pct(v.trefferquote * 100, 0), v.gewinnfaktor?.let { Fmt.num(it, 2) },
                v.mittelGewinn?.let { Fmt.euro(it) }, v.mittelVerlust?.let { Fmt.euro(it) }, Fmt.euro(v.erwartung),
            ),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
    }

    // Jeder Kauf und Verkauf am selben Tag mit einem Index nachgespielt
    SectionHeader(S.ptIndexTitle)
    ToolNote(S.ptCompareIntro)
    ToolChoice(null, BENCHES.map { S.ptBenchName(it.key) to it.key }, benchKey) { benchKey = it }

    // Mit demselben Geld: jede Buchung des Depots im Vergleich nachgespielt
    var money by remember { mutableStateOf<MoneyResult?>(null) }
    LaunchedEffect(benchKey, account.transactions.size) {
        money = null
        money = runCatching { loadMoney(state, bench, flows, settings.interestPct) }.getOrNull() ?: MoneyResult(null, emptyMap(), null)
    }
    val m = money
    if (m == null) { ToolNote(S.toolLoading); return }
    val invested = flows.filter { it.amount > 0 }.sumOf { it.amount }
    val proceeds = -flows.filter { it.amount < 0 }.sumOf { it.amount }
    val dividends = PortfolioTools.dividendsGross(account)
    val depotTotal = valuation.securities + proceeds + dividends
    val r = m.total
    if (r == null) {
        ToolNote(m.firstPrice?.let { S.ptNoPricesBefore(name, Fmt.date(it)) } ?: S.toolNoData)
        return
    }
    KeyValueRow(S.ptInvested, Fmt.euro(invested))
    KeyValueRow(S.ptDepotToday, Fmt.euro(depotTotal), hint = S.ptDepotTodayHint)
    KeyValueRow(S.ptWith(name), Fmt.euro(r.total))
    val diff = depotTotal - r.total
    KeyValueRow(S.ptDifference, Fmt.signedEuro(diff), valueColor = signal.forChange(diff))
    val now = System.currentTimeMillis()
    val buys = flows.filter { it.amount > 0 }
    if (now - buys.first().t >= 365L * DAY_MS) {
        val a = PortfolioTools.moneyWeighted(buys, depotTotal, now)
        val bb = PortfolioTools.moneyWeighted(buys, r.total, now)
        if (a != null && bb != null) CompareRows(name, listOf(Triple(S.ptPerYear, signedPct(a * 100), signedPct(bb * 100))))
    } else if (invested > 0) {
        CompareRows(name, listOf(Triple(S.ptSimpleReturn, signedPct((depotTotal / invested - 1) * 100), signedPct((r.total / invested - 1) * 100))))
    }
    ToolNote(S.ptSameMoneyNote + if (bench.symbol == null) " " + S.ptCashNote(Fmt.pct(settings.interestPct)) else "")

    // Jeder Einzelwert gegen dasselbe Geld im Vergleich
    val rows = valuation.positions.filter { !it.position.isDerivative }.mapNotNull { vp ->
        val f = flows.filter { it.key == vp.key }
        val inv = f.filter { it.amount > 0 }.sumOf { it.amount }
        val rep = m.perKey[vp.key] ?: return@mapNotNull null
        if (inv <= 0) return@mapNotNull null
        val own = vp.value - f.filter { it.amount < 0 }.sumOf { it.amount } + account.transactions
            .filter { it.type == TxType.DIVIDEND && it.posKey == vp.key }.sumOf { it.amount + it.tax }
        Triple(vp, (own / inv - 1) * 100, (rep.total / inv - 1) * 100)
    }.sortedByDescending { it.second - it.third }
    if (rows.isEmpty()) return
    SectionHeader(S.ptSingleTitle(name))
    rows.forEach { (vp, own, ref) ->
        val gap = own - ref
        Row(
            Modifier.fillMaxWidth().clickable { onOpen(vp.position.symbol) }.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InstrumentLogo(vp.position.symbol, vp.position.name, size = 36)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                NamenText(vp.position.name, style = MaterialTheme.typography.titleSmall)
                Text("$name ${signedPct(ref)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(signedPct(own), style = MaterialTheme.typography.titleSmall, color = signal.forChange(own))
                val points = (if (gap > 0.05) "+" else if (gap < -0.05) "−" else "") + Fmt.num(abs(gap), 1)
                Text("${S.ptPoints(points)} ${if (gap >= 0) S.ptBeats else S.ptTrails}",
                    style = MaterialTheme.typography.bodySmall, color = signal.forChange(gap))
            }
        }
    }
}

// Vermoegensaufbau

@Composable
private fun WealthTool(state: AppState) {
    val valuation = depotBewertung(state)
    val account = depotKonto(state)
    val signal = LocalSignal.current
    val plans = remember(account.plans) { monthlyFromPlans(account.plans) }
    var startMode by rememberSaveable { mutableIntStateOf(0) }
    var monthly by rememberSaveable { mutableFloatStateOf(if (plans > 0) roundTo(plans, 25.0).toFloat().coerceIn(0f, 3000f) else 500f) }
    var years by rememberSaveable { mutableFloatStateOf(20f) }
    var ret by rememberSaveable { mutableFloatStateOf(6f) }
    var vol by rememberSaveable { mutableFloatStateOf(15f) }
    var raise by rememberSaveable { mutableFloatStateOf(0f) }
    var real by rememberSaveable { mutableStateOf(false) }
    var target by rememberSaveable { mutableDoubleStateOf(1_000_000.0) }
    var scrub by remember { mutableStateOf<Int?>(null) }
    // Eigener Startwert, vorbelegt mit dem Wert der Wertpapiere
    var eigenerStart by rememberSaveable { mutableFloatStateOf(roundTo(valuation.securities, 1_000.0).toFloat().coerceIn(0f, 2_000_000f)) }
    val start = when (startMode) { 0 -> valuation.securities; 1 -> valuation.total; 3 -> eigenerStart.toDouble(); else -> 0.0 }
    val startKey = (start / 100.0).roundToLong()

    ToolChoice(S.ptStartValue, listOf(
        S.ptStartSecurities(Fmt.compactEuro(valuation.securities)) to 0,
        S.ptStartTotal(Fmt.compactEuro(valuation.total)) to 1,
        S.ptStartZero to 2,
        S.ptStartEigen to 3,
    ), startMode) { startMode = it; scrub = null }
    if (startMode == 3) {
        SliderRow(S.ptStartEigenWert, Fmt.euro(eigenerStart.toDouble(), 0), eigenerStart, 0f..2_000_000f, 1_000f) { eigenerStart = it; scrub = null }
    }
    SliderRow(S.ptMonthlySaving, Fmt.euro(monthly.toDouble(), 0), monthly, 0f..3000f, 25f) { monthly = it; scrub = null }
    SliderRow(S.ptHorizon, S.toolYears(years.roundToInt()), years, 1f..50f, 1f) { years = it; scrub = null }
    SliderRow(S.ptReturn, Fmt.pct(ret.toDouble(), 1), ret, 0f..12f, 0.5f) { ret = it }
    SliderRow(S.ptVolatility, Fmt.pct(vol.toDouble(), 0), vol, 0f..40f, 1f) { vol = it }
    ToolChoice(S.ptRaise, listOf(0f, 1f, 2f, 3f, 5f).map { Fmt.pct(it.toDouble(), 0) to it }, raise) { raise = it }
    ToggleRow(S.ptRealTerms(Fmt.pct(INFLATION, 0)), real) { real = it }
    ToolChoice(S.ptTarget, listOf(100_000.0, 250_000.0, 500_000.0, 1_000_000.0, 2_000_000.0, 5_000_000.0).map { roundAmount(it) to it }, target) { target = it }

    var result by remember { mutableStateOf<PortfolioTools.Projection?>(null) }
    LaunchedEffect(startKey, monthly, years, ret, vol, raise, real, target) {
        val r = withContext(Dispatchers.Default) {
            PortfolioTools.project(
                start = startKey * 100.0, monthly = monthly.toDouble(), years = years.roundToInt(), returnPct = ret.toDouble(),
                volPct = vol.toDouble(), raisePct = raise.toDouble(), inflationPct = if (real) INFLATION else 0.0, target = target,
                check = { ensureActive() },
            )
        }
        result = r
    }
    val r = result ?: run { ToolNote(S.toolLoading); return }
    val end = r.years
    val i = scrub?.coerceIn(0, end) ?: end
    Headline(S.ptInYears(i), Fmt.euro(r.p50[i], 0), S.ptReadout(S.ptMedian, shortMoney(r.p10[i]), shortMoney(r.p90[i])))
    val color = signal.link
    FanChart(
        median = r.p50, outer = r.p10 to r.p90, inner = r.p25 to r.p75, reference = r.invested, color = color,
        startLabel = S.ptToday, endLabel = S.ptInYears(end),
        modifier = Modifier.fillMaxWidth().height(210.dp).padding(horizontal = 20.dp, vertical = 10.dp),
        selected = scrub, onScrub = { scrub = it },
    )
    ChartLegend(listOf(S.ptMedian to color, S.ptBandLabel to color.copy(alpha = 0.35f), S.ptPaidIn to MaterialTheme.colorScheme.onSurfaceVariant))
    KeyValueRow(S.ptMedian, Fmt.euro(r.p50[end], 0))
    KeyValueRow(S.ptWeak, Fmt.euro(r.p10[end], 0))
    KeyValueRow(S.ptStrong, Fmt.euro(r.p90[end], 0))
    KeyValueRow(S.ptPaidIn, Fmt.euro(r.invested[end], 0))
    KeyValueRow(S.ptGrowth, Fmt.euro(r.p50[end] - r.invested[end], 0))
    KeyValueRow(S.ptTargetReached(roundAmount(target)), S.ptTargetChance(Fmt.pct(r.targetProbability * 100.0, 0)))
    KeyValueRow(S.ptTargetMedian, r.targetYears?.let { S.ptTargetWhen(ceil(it).toInt().coerceAtLeast(1)) } ?: S.ptTargetNever)
    ToolNote(S.ptWealthNote)
}

// Entnahmeplan

@Composable
private fun WithdrawalTool(state: AppState) {
    val valuation = depotBewertung(state)
    val signal = LocalSignal.current
    var startMode by rememberSaveable { mutableIntStateOf(if (valuation.securities >= 1_000) 0 else 1) }
    val start = if (startMode == 0) valuation.securities else valuation.total
    val maxRate = niceCeil(max(1_000.0, start * 0.12 / 12.0))
    val step = niceCeil(maxRate / 100.0)
    var monthly by rememberSaveable(startMode) { mutableFloatStateOf(roundTo(start * 0.04 / 12.0, step).toFloat()) }
    var years by rememberSaveable { mutableFloatStateOf(30f) }
    var ret by rememberSaveable { mutableFloatStateOf(5f) }
    var vol by rememberSaveable { mutableFloatStateOf(12f) }
    var indexed by rememberSaveable { mutableStateOf(true) }
    var scrub by remember { mutableStateOf<Int?>(null) }

    ToolChoice(S.ptStartValue, listOf(
        S.ptStartSecurities(Fmt.compactEuro(valuation.securities)) to 0,
        S.ptStartTotal(Fmt.compactEuro(valuation.total)) to 1,
    ), startMode) { startMode = it; scrub = null }
    if (start < 1.0) { ToolNote(S.ptNoStart); return }
    val rate = monthly.coerceIn(0f, maxRate.toFloat())
    SliderRow(S.ptMonthlyWithdrawal, Fmt.euro(rate.toDouble(), 0), rate, 0f..maxRate.toFloat(), step.toFloat()) { monthly = it; scrub = null }
    SliderRow(S.ptHorizon, S.toolYears(years.roundToInt()), years, 5f..60f, 1f) { years = it; scrub = null }
    SliderRow(S.ptReturn, Fmt.pct(ret.toDouble(), 1), ret, 0f..10f, 0.5f) { ret = it }
    SliderRow(S.ptVolatility, Fmt.pct(vol.toDouble(), 0), vol, 0f..30f, 1f) { vol = it }
    ToggleRow(S.ptIndexed(Fmt.pct(INFLATION, 0)), indexed) { indexed = it }

    val startKey = (start / 100.0).roundToLong()
    var result by remember { mutableStateOf<PortfolioTools.Withdrawal?>(null) }
    LaunchedEffect(startKey, rate, years, ret, vol, indexed) {
        val r = withContext(Dispatchers.Default) {
            PortfolioTools.withdrawal(
                start = startKey * 100.0, monthly = rate.toDouble(), years = years.roundToInt(), returnPct = ret.toDouble(),
                volPct = vol.toDouble(), inflationPct = INFLATION, indexed = indexed, check = { ensureActive() },
            )
        }
        result = r
    }
    val r = result ?: run { ToolNote(S.toolLoading); return }
    val end = r.years
    val okColor = when {
        r.success >= 0.9 -> signal.up
        r.success < 0.7 -> signal.down
        else -> MaterialTheme.colorScheme.onSurface
    }
    Text(
        S.ptLasts(Fmt.pct(r.success * 100.0, 0), S.toolYears(end)),
        style = MaterialTheme.typography.titleMedium, color = okColor,
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 16.dp),
    )
    val i = scrub?.coerceIn(0, end) ?: end
    Text(
        S.ptInYears(i) + ": " + S.ptReadout(Fmt.euro(r.p50[i], 0), shortMoney(r.p10[i]), shortMoney(r.p90[i])),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp),
    )
    val color = signal.link
    FanChart(
        median = r.p50, outer = r.p10 to r.p90, inner = null, reference = null, color = color,
        startLabel = S.ptToday, endLabel = S.ptInYears(end),
        modifier = Modifier.fillMaxWidth().height(200.dp).padding(horizontal = 20.dp, vertical = 10.dp),
        selected = scrub, onScrub = { scrub = it },
    )
    ChartLegend(listOf(S.ptMedian to color, S.ptBandLabel to color.copy(alpha = 0.35f)))
    val depletion = r.depletionYears
    if (depletion == null) KeyValueRow(S.ptLeftAfter(end), Fmt.euro(r.p50[end], 0))
    else KeyValueRow(S.ptUsedUpAfter, S.ptDuration((depletion * 365.25).roundToLong()))
    KeyValueRow(S.ptSafeRate, S.ptPerMonth(Fmt.euro(r.safeMonthly, 0)))
    KeyValueRow(S.ptFourPercent, S.ptPerMonth(Fmt.euro(start * 0.04 / 12.0, 0)))
    val planned = (0 until end * 12).sumOf { m -> rate.toDouble() * (if (indexed) (1.0 + INFLATION / 100.0).pow(m / 12) else 1.0) }
    KeyValueRow(S.ptPlannedTotal, Fmt.euro(planned, 0))
    ToolNote(S.ptWithdrawalNote)
}

// Crash-Test

private const val REFERENCE = "^GSPC"

// Tages- und Wochenreihen je Basiswert und fuer den S&P 500, dazu die Betas.
private class CrashData(
    val refDaily: List<Candle>,
    val refLong: List<Candle>,
    val daily: Map<String, List<Candle>>,
    val long: Map<String, List<Candle>>,
    val beta: Map<String, Double>,
)

private fun defaultBeta(symbol: String): Double = when (Symbols.typeOf(symbol)) {
    AssetType.CRYPTO -> 1.5
    AssetType.COMMODITY -> 0.3
    else -> 1.0
}

// Echt, wenn es Kurse gibt, sonst ueber das Beta geschaetzt.
private fun moveIn(symbol: String, crash: PortfolioTools.Crash, data: CrashData, refMove: Double): Pair<Double, Boolean> {
    val own = PortfolioTools.windowReturn(data.daily[symbol].orEmpty(), crash.from, crash.to)
        ?: PortfolioTools.windowReturn(data.long[symbol].orEmpty(), crash.from, crash.to)
    if (own != null) return own to false
    val b = (data.beta[symbol] ?: defaultBeta(symbol)).coerceIn(0.0, 3.0)
    return (exp(b * ln(1.0 + refMove)) - 1.0) to true
}

// Wert einer Position, wenn sich ihr Basiswert um move veraendert. Derivate werden neu bewertet.
private fun shocked(state: AppState, vp: ValuedPosition, move: Double, now: Long): Double {
    val d = vp.position.derivative ?: return vp.value * (1.0 + move)
    if (d.isKnockedOut) return vp.value
    val q = state.quotes.value[vp.position.symbol] ?: return (vp.value * (1.0 + move)).coerceAtLeast(0.0)
    val price = Pricing.price(d, q.price * (1.0 + move), now, q.fx, state.riskFree)
    return if (price.isFinite()) vp.qty * price else vp.value
}

@Composable
private fun CrashTool(state: AppState, onOpen: (String) -> Unit) {
    val valuation = depotBewertung(state)
    val signal = LocalSignal.current
    val items = valuation.positions.filter { it.value > 0 }
    if (items.isEmpty()) { ToolNote(S.ptNoPositions); return }
    val symbols = remember(items.map { it.position.symbol }.distinct().sorted().joinToString("|")) { items.map { it.position.symbol }.distinct() }
    var data by remember { mutableStateOf<CrashData?>(null) }
    LaunchedEffect(symbols) {
        data = null
        data = coroutineScope {
            val refD = async { state.dailyCloses(REFERENCE) }
            val refL = async { state.longHistory(REFERENCE) }
            val d = symbols.map { s -> s to async { state.dailyCloses(s) } }
            val l = symbols.map { s -> s to async { state.longHistory(s) } }
            val refDaily = refD.await()
            val daily = d.associate { (s, a) -> s to a.await() }
            val long = l.associate { (s, a) -> s to a.await() }
            val since = System.currentTimeMillis() - 3L * 365L * DAY_MS
            val beta = symbols.mapNotNull { s -> PortfolioTools.beta(daily[s].orEmpty(), refDaily, since)?.let { s to it } }.toMap()
            CrashData(refDaily, refL.await(), daily, long, beta)
        }
    }
    Text(S.ptCrashIntro, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp))
    val cd = data ?: run { ToolNote(S.toolLoading); return }
    val now = System.currentTimeMillis()
    val total = items.sumOf { it.value }
    val grey = MaterialTheme.colorScheme.onSurfaceVariant
    for (crash in PortfolioTools.CRASHES.asReversed()) {
        val refMove = PortfolioTools.windowReturn(cd.refDaily, crash.from, crash.to) ?: PortfolioTools.windowReturn(cd.refLong, crash.from, crash.to) ?: continue
        var after = 0.0
        var estimated = 0
        var worst: Pair<ValuedPosition, Double>? = null
        for (vp in items) {
            val (move, est) = moveIn(vp.position.symbol, crash, cd, refMove)
            if (est) estimated++
            val v = shocked(state, vp, move, now)
            after += v
            val change = if (vp.value > 0) v / vp.value - 1.0 else 0.0
            if (worst == null || change < worst.second) worst = vp to change
        }
        val depotMove = if (total > 0) after / total - 1.0 else 0.0
        Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
            Text(S.ptCrashName(crash.key), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp))
            Text(S.ptFromTo(Fmt.date(crash.from), Fmt.date(crash.to)), style = MaterialTheme.typography.bodySmall, color = grey,
                modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(6.dp))
            ChangeBar(S.ptYourDepot, depotMove, signedPct(depotMove * 100), signal.forChange(depotMove))
            ChangeBar(S.ptReference, refMove, signedPct(refMove * 100), grey)
            Text(Fmt.signedEuro(after - total), style = MaterialTheme.typography.bodyMedium, color = signal.forChange(after - total),
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 2.dp))
            worst?.takeIf { items.size > 1 }?.let { (vp, ch) ->
                Text(S.ptHardestHit(vp.position.name, signedPct(ch * 100)), style = MaterialTheme.typography.bodySmall, color = grey,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(top = 2.dp))
            }
            val rec = PortfolioTools.recoveryDays(if (cd.refDaily.isNotEmpty() && cd.refDaily.first().t <= crash.from) cd.refDaily else cd.refLong, crash.from, crash.to)
            Text(rec?.let { S.ptRecovery(S.ptDuration(it)) } ?: S.ptNotRecovered, style = MaterialTheme.typography.bodySmall, color = grey,
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 2.dp))
            if (estimated > 0) Text(S.ptEstimated(estimated, items.size), style = MaterialTheme.typography.bodySmall, color = grey,
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 2.dp))
        }
    }

    // Eigenes Szenario: der S&P 500 faellt um x %, jeder Wert folgt mit seinem Beta
    SectionHeader(S.ptCustomScenario)
    var drop by rememberSaveable { mutableFloatStateOf(20f) }
    SliderRow(S.ptMarketFalls, Fmt.pct(drop.toDouble(), 0), drop, 0f..60f, 5f) { drop = it }
    val lines = items.map { vp ->
        val b = (cd.beta[vp.position.symbol] ?: defaultBeta(vp.position.symbol)).coerceIn(0.0, 3.0)
        val move = exp(b * ln(1.0 - drop / 100.0)) - 1.0
        Triple(vp, b, shocked(state, vp, move, now))
    }
    val after = lines.sumOf { it.third }
    val move = if (total > 0) after / total - 1.0 else 0.0
    KeyValueRow(S.ptEstimatedDepot, "${signedPct(move * 100)} · ${Fmt.signedEuro(after - total)}", valueColor = signal.forChange(move))
    lines.sortedBy { it.third - it.first.value }.take(10).forEach { (vp, b, v) ->
        val ch = if (vp.value > 0) v / vp.value - 1.0 else 0.0
        KeyValueRow("${vp.position.name} · Beta ${Fmt.num(b, 2)}", signedPct(ch * 100), valueColor = signal.forChange(ch),
            onClick = { onOpen(vp.position.symbol) })
    }
    ToolNote(S.ptCrashNote)
}

// Dividendenkalender

private fun monthLabel(ym: YearMonth): String =
    ym.format(DateTimeFormatter.ofPattern("MMM", de.troetenrepublik.app.i18n.I18n.locale)).replace(".", "").take(3)

@Composable
private fun DividendsTool(state: AppState, onOpen: (String) -> Unit) {
    val valuation = depotBewertung(state)
    val account = depotKonto(state)
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val signal = LocalSignal.current
    val holdings = valuation.positions.filter {
        !it.position.isDerivative && Symbols.typeOf(it.position.symbol).let { t -> t == AssetType.STOCK || t == AssetType.ETF }
    }
    val received = account.transactions.filter { it.type == TxType.DIVIDEND }.sumOf { it.amount }
    if (holdings.isEmpty()) { ToolNote(S.ptNoPositions); return }
    val key = holdings.joinToString("|") { "${it.key}:${it.qty}" }
    var expected by remember { mutableStateOf<List<PortfolioTools.ExpectedDividend>?>(null) }
    LaunchedEffect(key) {
        expected = null
        val now = System.currentTimeMillis()
        val sources = coroutineScope {
            holdings.map { vp ->
                async {
                    PortfolioTools.DividendSource(vp.key, vp.position.name, vp.qty,
                        state.dividendsFor(vp.position.symbol).map { it.exDate to it.amount })
                }
            }.awaitAll()
        }
        expected = PortfolioTools.expectedDividends(sources, now)
    }
    val list = expected ?: run { ToolNote(S.toolLoading); return }
    val gross = list.sumOf { it.amount }
    if (gross < 0.01) {
        ToolNote(S.ptDivNone)
        if (received > 0) KeyValueRow(S.ptDivReceived, Fmt.euro(received))
        ToolNote(S.ptDivNote)
        return
    }
    val allowanceLeft = (min(settings.allowance, Tax.MAX_ALLOWANCE) - account.tax.allowanceUsed).coerceAtLeast(0.0)
    val net = gross - Analysis.taxOn(gross, allowanceLeft)
    Headline(S.ptDivExpected, Fmt.euro(gross), S.ptDivAfterTax(Fmt.euro(net)))
    val zone = ZoneId.systemDefault()
    val first = YearMonth.now()
    val months = (0 until 12).map { first.plusMonths(it.toLong()) }
    val sums = months.map { ym -> list.filter { YearMonth.from(Instant.ofEpochMilli(it.date).atZone(zone)) == ym }.sumOf { it.amount } }
    BarChart(sums, months.map { monthLabel(it) }, signal.up, Modifier.fillMaxWidth().height(160.dp).padding(horizontal = 20.dp, vertical = 12.dp))
    KeyValueRow(S.ptDivPerMonth, Fmt.euro(net / 12.0))
    if (valuation.securities > 0) KeyValueRow(S.ptDivYield, Fmt.pct(gross / valuation.securities * 100.0))
    val invested = valuation.positions.filter { !it.position.isDerivative }.sumOf { it.invested }
    if (invested > 0) KeyValueRow(S.ptDivYieldOnCost, Fmt.pct(gross / invested * 100.0))
    if (received > 0) KeyValueRow(S.ptDivReceived, Fmt.euro(received))

    SectionHeader(S.ptDivNext)
    list.take(6).forEach { e -> KeyValueRow("${Fmt.dayMonth(e.date)} · ${e.name}", Fmt.euro(e.amount)) }

    SectionHeader(S.ptDivTop)
    val byKey = list.groupBy { it.key }.map { (k, v) -> Triple(k, v.first().name, v) }.sortedByDescending { t -> t.third.sumOf { it.amount } }
    byKey.forEach { (k, n, v) ->
        val sum = v.sumOf { it.amount }
        val symbol = holdings.firstOrNull { it.key == k }?.position?.symbol ?: k
        ShareBar("$n · ${S.ptDivTimes(v.size)}", Fmt.euro(sum), sum / gross, signal.up) { onOpen(symbol) }
    }
    ToolNote(S.ptDivNote)
}

// Streuung

@Composable
private fun DiversificationTool(state: AppState, onOpen: (String) -> Unit) {
    val catalog = remember { Catalog.get() }
    val valuation = depotBewertung(state)
    val quotes by state.quotes.collectAsStateWithLifecycle()
    val signal = LocalSignal.current
    val items = valuation.positions.filter { it.value > 0 }
    if (items.isEmpty()) { ToolNote(S.ptNoPositions); return }
    val total = items.sumOf { it.value }
    val c = PortfolioTools.concentration(items.map { it.value }) ?: return
    val largest = items.maxBy { it.value }
    Spacer(Modifier.height(8.dp))
    KeyValueRow(S.largestPosition, "${largest.position.name} · ${Fmt.pct(c.largest * 100.0, 1)}")
    if (items.size > 3) KeyValueRow(S.ptTop3, Fmt.pct(c.top3 * 100.0, 1))
    KeyValueRow(S.ptEffective, S.ptEffectiveValue(Fmt.num(c.effective, 1)))

    fun regionOf(vp: ValuedPosition): String {
        val s = vp.position.symbol
        val type = Symbols.typeOf(s)
        return when {
            vp.position.isDerivative -> "DERIVATIVES"
            type == AssetType.CRYPTO -> "CRYPTO"
            type == AssetType.ETF -> catalog.etfs.firstOrNull { it.s == s }?.r?.takeIf { it.isNotBlank() } ?: "OTHER"
            else -> catalog.aktien.firstOrNull { it.s == s }?.c?.takeIf { it.isNotBlank() }?.let { Region.ofCountry(it) } ?: when {
                s.endsWith(".DE") || s.endsWith(".F") -> "DEUTSCHLAND"
                type == AssetType.STOCK && !s.contains('.') && !s.contains('-') && !s.startsWith("^") -> "NORDAMERIKA"
                else -> "OTHER"
            }
        }
    }

    fun industryOf(vp: ValuedPosition): String {
        val s = vp.position.symbol
        val type = Symbols.typeOf(s)
        return when {
            vp.position.isDerivative -> S.ptGroup("DERIVATIVES")
            type == AssetType.CRYPTO -> S.ptGroup("CRYPTO")
            else -> Catalog.industryOf(s)?.let { S.industryName(it) }
                ?: Symbols.find(s)?.sector?.takeIf { it.isNotBlank() && type == AssetType.STOCK }?.let { S.sectorName(it) }
                ?: if (type == AssetType.ETF) S.ptGroup("ETFS") else S.ptGroup("OTHER")
        }
    }

    @Composable
    fun groups(title: String, label: (ValuedPosition) -> String, color: Color, limit: Int = 8) {
        SectionHeader(title)
        val g = items.groupBy(label).map { (k, v) -> k to v.sumOf { it.value } }.sortedByDescending { it.second }
        val shown = if (g.size > limit + 1) g.take(limit) + (S.ptOthers to g.drop(limit).sumOf { it.second }) else g
        shown.forEach { (k, v) -> ShareBar(k, Fmt.pct(v / total * 100.0, 1), v / total, color) }
    }

    groups(S.regions, { vp -> regionOf(vp).let { k -> if (k in Region.ALL) S.regionName(k) else S.ptGroup(k) } }, signal.link)
    groups(S.industries, { industryOf(it) }, signal.up)
    groups(S.ptCurrencies, { vp -> quotes[vp.position.symbol]?.nativeCurrency?.takeIf { it.isNotBlank() } ?: Symbols.find(vp.position.symbol)?.currency ?: "EUR" },
        MaterialTheme.colorScheme.onSurfaceVariant)

    // ETF-Kosten laut Katalog
    val etfs = items.mapNotNull { vp -> catalog.etfs.firstOrNull { it.s == vp.position.symbol }?.ter?.let { vp to it } }
    if (etfs.isNotEmpty()) {
        val value = etfs.sumOf { it.first.value }
        val cost = etfs.sumOf { it.first.value * it.second / 100.0 }
        KeyValueRow(S.ptEtfCosts, S.ptEtfCostValue(Fmt.pct(cost / value * 100.0, 2), Fmt.euro(cost, 0)))
    }

    // Gleichlauf der groessten Positionen ohne Derivate
    val top = items.filter { !it.position.isDerivative }.sortedByDescending { it.value }.distinctBy { it.position.symbol }.take(8)
    if (top.size >= 2) {
        SectionHeader(S.ptCorrelation)
        var matrix by remember { mutableStateOf<Array<Array<Double?>>?>(null) }
        LaunchedEffect(top.joinToString("|") { it.position.symbol }) {
            matrix = null
            val series = coroutineScope { top.map { vp -> async { state.dailyCloses(vp.position.symbol) } }.awaitAll() }
            val since = System.currentTimeMillis() - 365L * DAY_MS
            matrix = withContext(Dispatchers.Default) {
                Array(top.size) { i -> Array(top.size) { j -> if (i == j) 1.0 else PortfolioTools.correlation(series[i], series[j], since) } }
            }
        }
        val mx = matrix
        if (mx == null) ToolNote(S.toolLoading) else {
            val labels = top.map { it.position.symbol.substringBefore('.').substringBefore('-').removePrefix("^") }
            CorrelationGrid(labels, { i, j -> mx[i][j] },
                Modifier.fillMaxWidth().height((22 + 40 * top.size).dp.coerceAtMost(380.dp)).padding(horizontal = 20.dp, vertical = 8.dp))
            val pairs = (0 until top.size).flatMap { i -> (i + 1 until top.size).mapNotNull { j -> mx[i][j] } }
            if (pairs.isNotEmpty()) KeyValueRow(S.ptAverage, Fmt.num(pairs.average(), 2))
            ToolNote(S.ptCorrelationNote)
        }
    }
    ToolNote(S.ptDiversificationNote)
}

// Umschichten

@Composable
private fun RebalanceTool(state: AppState, onOpen: (String) -> Unit) {
    val valuation = depotBewertung(state)
    val signal = LocalSignal.current
    val items = valuation.positions.filter { !it.position.isDerivative && it.value > 0 }.sortedByDescending { it.value }
    if (items.size < 2) { ToolNote(S.ptNeedTwo); return }
    val keys = items.joinToString("|") { it.key }
    val total = items.sumOf { it.value }
    val weights = remember(keys) { mutableStateListOf<Float>().apply { addAll(items.map { (it.value / total * 100.0).toFloat() }) } }
    var sell by rememberSaveable { mutableStateOf(true) }
    var money by rememberSaveable { mutableDoubleStateOf(1_000.0) }
    val cash = state.withdrawable()

    ToolChoice(null, listOf(S.ptModeRebalance to true, S.ptModeBuyOnly to false), sell) { sell = it }
    if (!sell) {
        val options = listOf(500.0, 1_000.0, 2_500.0, 5_000.0, 10_000.0).map { Fmt.euro(it, 0) to it } +
            (if (cash >= 1.0) listOf(S.ptCash(Fmt.compactEuro(cash)) to roundTo(cash, 0.01)) else emptyList())
        ToolChoice(S.ptNewMoney, options, money) { money = it }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(S.ptEqualWeights, false) { for (i in weights.indices) weights[i] = 50f }
        Chip(S.ptCurrentWeights, false) { items.forEachIndexed { i, vp -> weights[i] = (vp.value / total * 100.0).toFloat() } }
    }
    val newMoney = if (sell) 0.0 else money
    val trades = PortfolioTools.rebalance(items.map { it.key to it.value }, weights.map { it.toDouble() }, newMoney, sell)
    val sumW = weights.sum().takeIf { it > 0f } ?: 1f
    SectionHeader(S.ptTargetWeights)
    items.forEachIndexed { i, vp ->
        val t = trades[i]
        val now = vp.value / total * 100.0
        val goal = weights[i] / sumW * 100.0
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(vp.position.symbol) }.padding(start = 20.dp, end = 20.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InstrumentLogo(vp.position.symbol, vp.position.name, size = 32)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    NamenText(vp.position.name, style = MaterialTheme.typography.titleSmall)
                    Text("${Fmt.pct(now, 1)} → ${Fmt.pct(goal, 1)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val (text, color) = when {
                    t.amount >= 1.0 -> S.ptBuyAmount(Fmt.euro(t.amount, 0)) to signal.up
                    t.amount <= -1.0 -> S.ptSellAmount(Fmt.euro(-t.amount, 0)) to signal.down
                    else -> S.ptOnTarget to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(text, style = MaterialTheme.typography.titleSmall, color = color, textAlign = TextAlign.End)
            }
            ToolSlider(weights[i], { weights[i] = it }, 0f..100f, modifier = Modifier.padding(horizontal = 20.dp))
        }
    }
    val buys = trades.filter { it.amount >= 1.0 }.sumOf { it.amount }
    val sells = -trades.filter { it.amount <= -1.0 }.sumOf { it.amount }
    val orders = trades.count { abs(it.amount) >= 1.0 }
    val tax = trades.filter { it.amount <= -1.0 }.sumOf { t ->
        val vp = items.first { it.key == t.key }
        val qty = if (vp.price > 0) min(vp.qty, -t.amount / vp.price) else 0.0
        if (qty > 0) state.previewSell(t.key, qty)?.tax ?: 0.0 else 0.0
    }
    val before = PortfolioTools.deviation(items.map { it.value }, weights.map { it.toDouble() })
    val afterValues = items.mapIndexed { i, vp -> vp.value + trades[i].amount }
    val after = PortfolioTools.deviation(afterValues, weights.map { it.toDouble() })
    Spacer(Modifier.height(8.dp))
    KeyValueRow(S.ptBuys, Fmt.euro(buys, 0))
    KeyValueRow(S.ptSells, Fmt.euro(sells, 0))
    KeyValueRow(S.ptOrdersFees(orders, Fmt.euro(orders * Trading.FEE, 0)), "")
    if (tax > 0) KeyValueRow(S.ptTaxEstimate, Fmt.euro(tax))
    KeyValueRow(S.ptDeviation, "${Fmt.pct(before * 100.0, 1)} → ${Fmt.pct(after * 100.0, 1)}")
    ToolNote(S.ptRebalanceNote)
}
