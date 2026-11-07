package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import de.troetenrepublik.app.plattform.horizontalScrollMaus
import de.troetenrepublik.app.plattform.mausWischen
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.Analysis
import de.troetenrepublik.app.engine.Tax
import de.troetenrepublik.app.engine.Vergleichsrechnung
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.Portfolioverlauf
import de.troetenrepublik.app.market.Symbols
import kotlin.math.abs
import kotlinx.coroutines.launch

// Werkzeuge am Ende der Wertpapierseite. Sie rechnen mit den echten Tagesschlusskursen des Werts
// (bis zu zehn Jahre, in Euro) und seinen Kennzahlen.
enum class StockTool { LUMP_SUM, SAVINGS_PLAN, RISK, FAIR_VALUE, POSITION_SIZE, SELL_TAX }

// Seit 5.92: Einmalanlage und Sparplan im Rueckblick in einem Blatt (LUMP_SUM mit Umschalter), der faire Wert steht
// bei der Bewertung, Schwankung und Rueckgaenge unter „Wie tief kann es fallen“. Wer den Wert haelt, sieht den
// Verkauf mit Steuer zuerst.
@Suppress("UNUSED_PARAMETER")
fun toolsFor(type: AssetType, holds: Boolean, profile: MarketApi.Profile?): List<StockTool> = buildList {
    if (holds) add(StockTool.SELL_TAX)
    add(StockTool.LUMP_SUM)
    if (type.directlyTradable) add(StockTool.POSITION_SIZE)
}

@Composable
fun StockToolsSection(tools: List<StockTool>, onOpen: (StockTool) -> Unit) {
    SectionHeader(S.toolsTitle)
    tools.forEach { t ->
        if (t == StockTool.LUMP_SUM) NavRow(S.wzRueckblick, S.wzRueckblickUnter) { onOpen(t) }
        else NavRow(S.toolTitle(t.name), S.toolSubtitle(t.name)) { onOpen(t) }
    }
}

private const val WORLD_ETF = "EUNL.DE"
private const val YEAR_MS = 365L * 86_400_000L

// Blau fuer den Wert, die Vergleichsziele bekommen die weiteren Farben der Kategorien-Palette.
private val FARBE_AKTIE = Kategorie.blau

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
            Text(if (tool == StockTool.LUMP_SUM) S.wzRueckblick else S.toolTitle(tool.name), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp))
            Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(8.dp))
            when (tool) {
                StockTool.LUMP_SUM -> RueckblickReiter(state, symbol)
                StockTool.SAVINGS_PLAN -> RueckblickReiter(state, symbol, startSeite = 1)
                StockTool.RISK -> RiskTool(state, symbol)
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

// Dividenden des Werts (EUR je Stueck); null, solange noch geladen wird.
@Composable
private fun rememberDividenden(state: AppState, symbol: String): List<MarketApi.DividendEvent>? {
    var ev by remember(symbol) { mutableStateOf<List<MarketApi.DividendEvent>?>(null) }
    LaunchedEffect(symbol) { ev = runCatching { state.market.dividendHistory(symbol) }.getOrDefault(emptyList()) }
    return ev
}

// Gesamtrendite des Werts: Dividenden wieder angelegt (nicht fuer den Weltindex, der thesauriert selbst).
private fun mitErtraegen(d: List<Candle>, divs: List<MarketApi.DividendEvent>?, symbol: String): List<Candle> =
    if (symbol == WORLD_ETF) d else Analysis.thesaurierend(d, divs ?: emptyList())

// Aktueller Kurs wie die angepasste Reihe: der Faktor der letzten Kerze gilt auch fuer den Live-Kurs.
private fun kursMitErtraegen(roh: List<Candle>, ang: List<Candle>, live: Double?): Double? {
    val l = live?.takeIf { it > 0 && it.isFinite() } ?: return null
    val f = if (roh.isNotEmpty() && roh.last().close > 0) ang.last().close / roh.last().close else 1.0
    return l * f
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
            drawLine(grid.copy(alpha = 0.6f), Offset(0f, y(it)), Offset(size.width, y(it)), strokeWidth = 1.dp.toPx())
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

// Einmalanlage und Sparplan als zwei wischbare Seiten mit Pille. Der Reiter laedt Kurse und Dividenden
// einmal und haelt Auswahl und Auswahlblatt der Vergleichsziele fuer beide Seiten (5.96).
@Composable
private fun RueckblickReiter(state: AppState, symbol: String, startSeite: Int = 0) {
    val pager = rememberPagerState(initialPage = startSeite) { 2 }
    val scope = rememberCoroutineScope()
    val titel = listOf(S.wzEinmal, S.copyMonthly)
    val daily = rememberDaily(state, symbol)
    val divs = rememberDividenden(state, symbol)
    var zieleText by rememberSaveable(symbol) { mutableStateOf(VergleichZiele.start(symbol).joinToString("\n")) }
    val ziele = zieleText.lines().filter { it.isNotBlank() }
    fun setzen(neu: List<String>) {
        zieleText = neu.joinToString("\n")
        VergleichZiele.zuletzt = neu
    }
    // Gesamtrendite-Kerzen als Basis fuer die Regeln des Strategie-Labors; der Weltindex-ETF thesauriert selbst
    val basis = remember(daily, divs) { if (daily != null && (divs != null || symbol == WORLD_ETF)) mitErtraegen(daily, divs, symbol) else null }
    val daten = rememberVergleichsDaten(state, ziele, basis)
    var blatt by remember { mutableStateOf(false) }
    MitlaufendeReiter(2, pager, { titel[it] }) { scope.launch { pager.animateScrollToPage(it) } }
    // Seiten laden lazy wie bei den anderen Pagern des Blatts; die Auswahl bleibt je Seite gespeichert
    HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().mausWischen(pager), verticalAlignment = Alignment.Top) { page ->
        Column(Modifier.fillMaxWidth()) {
            if (page == 0) LumpSumTool(state, symbol, daily, divs, ziele, daten, { setzen(ziele - it) }, { blatt = true })
            else SavingsPlanTool(state, symbol, daily, divs, ziele, daten, { setzen(ziele - it) }, { blatt = true })
        }
    }
    if (blatt) VergleichZielBlatt(
        state, ziele,
        // die Aktie selbst und Massstaebe mit demselben Wertpapier sind kein Vergleichsziel
        setOf("a:$symbol") + Portfolioverlauf.MASSSTAEBE.filter { it.symbol == symbol }.map { "m:" + it.id },
        max = Vergleichsrechnung.MAX_ZIELE, laborSymbol = symbol,
        onDismiss = { blatt = false },
        onToggle = { k -> setzen(if (k in ziele) ziele - k else if (ziele.size >= Vergleichsrechnung.MAX_ZIELE) ziele else ziele + k) },
    )
}

// Einmalanlage im Rueckblick

@Composable
private fun LumpSumTool(
    state: AppState, symbol: String, daily: List<Candle>?, divs: List<MarketApi.DividendEvent>?,
    ziele: List<String>, daten: Map<String, VergleichZiele.Daten>, onZiele: (String) -> Unit, onVergleichen: () -> Unit,
) {
    val quotes by state.quotes.collectAsStateWithLifecycle()
    var amount by rememberSaveable { mutableDoubleStateOf(1_000.0) }
    var period by rememberSaveable { mutableIntStateOf(5) }
    if (showsNoData(daily)) return
    // Dividenden abwarten, sonst zeigt die Kurve kurz die unverzerrte Kursrendite
    if (symbol != WORLD_ETF && divs == null) { Note(S.toolLoading); return }
    val d = daily!!
    val options = periodOptions(d)
    val chosen = if (options.any { it.second == period }) period else options.lastOrNull { it.second > 0 }?.second ?: 0
    ChoiceRow(S.amount, listOf(500.0, 1_000.0, 5_000.0, 10_000.0).map { Fmt.euro(it, 0) to it }, amount) { amount = it }
    ToolZeit(S.widgetZeitraum, options, chosen) { period = it }
    val dg = mitErtraegen(d, divs, symbol)
    val jetzt = System.currentTimeMillis()
    val gewuenscht = startFor(d, chosen)
    val ok = okZiele(ziele, daten)
    val reihen = listOf(dg.map { it.t to it.close }) + ok.mapNotNull { daten[it]?.reihe }
    // Ohne gemeinsamen Zeitraum von mindestens MIN_TAGE rechnet nur die Aktie; die Ziele zeigen „zu kurzer Verlauf“
    val gemeinsam = Vergleichsrechnung.gemeinsam(reihen, gewuenscht, jetzt)
    val sp = gemeinsam ?: Vergleichsrechnung.Spanne(gewuenscht, jetzt)
    val r = Analysis.lumpSum(
        dg.filter { it.t <= sp.bis }, amount, sp.von,
        if (sp.bis >= jetzt) kursMitErtraegen(d, dg, quotes[symbol]?.price) else null, sp.bis,
    ) ?: run { Note(S.toolNoData); return }
    // jedes Ziel kauft am selben Tag wie die Aktie
    val e = if (gemeinsam == null) emptyList() else ok.mapNotNull { k ->
        val reihe = daten[k]?.reihe ?: return@mapNotNull null
        Vergleichsrechnung.einmal(reihe, amount, Vergleichsrechnung.Spanne(r.start.t, sp.bis))?.let { k to it }
    }
    val signal = LocalSignal.current
    Spacer(Modifier.height(8.dp))
    ToolChart(
        listOf(r.path to FARBE_AKTIE) + e.map { (k, x) -> x.path to Kategorie.farbe(ziele.indexOf(k) + 1) },
        baseline = amount,
        legend = listOf(Symbols.name(symbol).take(24) to FARBE_AKTIE),
    )
    ZielChips(state, ziele, daten, onZiele, onVergleichen)
    KeyValueRow(S.toolBoughtOn(Fmt.date(r.start.t), Fmt.priceEuro(r.start.close)), "${Fmt.qty(r.units)} ${S.pieces}")
    KeyValueRow(S.importValueToday, Fmt.euro(r.value))
    KeyValueRow(S.result, "${Fmt.signedEuro(r.gain)}  ${signed(r.pct)}", valueColor = signal.forChange(r.gain))
    r.perYear?.let { KeyValueRow(S.toolPerYear, signed(it)) }
    KeyValueRow(S.maxDrawdown, Fmt.pct(abs(r.maxDrawdown)))
    if (ziele.isNotEmpty()) ZielZeilen(
        state, ziele, daten,
        { k -> e.firstOrNull { it.first == k }?.second?.let { p -> Triple(p.value, p.pct, p.gain) } },
        S.vgZeitraum(Fmt.date(r.start.t), if (sp.bis >= jetzt) S.vgHeute else Fmt.date(sp.bis)),
        if (gemeinsam == null) null else begrenzerText(state, symbol, ok, reihen, gewuenscht, sp.von),
    )
    Note(hinweis(S.toolNoCosts, ziele))
}

// Sparplan im Rueckblick

@Composable
private fun SavingsPlanTool(
    state: AppState, symbol: String, daily: List<Candle>?, divs: List<MarketApi.DividendEvent>?,
    ziele: List<String>, daten: Map<String, VergleichZiele.Daten>, onZiele: (String) -> Unit, onVergleichen: () -> Unit,
) {
    val quotes by state.quotes.collectAsStateWithLifecycle()
    var monthly by rememberSaveable { mutableDoubleStateOf(100.0) }
    var period by rememberSaveable { mutableIntStateOf(5) }
    if (showsNoData(daily)) return
    // Dividenden abwarten, sonst zeigt die Kurve kurz die unverzerrte Kursrendite
    if (symbol != WORLD_ETF && divs == null) { Note(S.toolLoading); return }
    val d = daily!!
    val options = periodOptions(d)
    val chosen = if (options.any { it.second == period }) period else options.lastOrNull { it.second > 0 }?.second ?: 0
    ChoiceRow(S.toolMonthly, listOf(25.0, 50.0, 100.0, 250.0, 500.0).map { Fmt.euro(it, 0) to it }, monthly) { monthly = it }
    ToolZeit(S.widgetZeitraum, options, chosen) { period = it }
    val dg = mitErtraegen(d, divs, symbol)
    val jetzt = System.currentTimeMillis()
    val gewuenscht = startFor(d, chosen)
    // gemeinsamer Zeitraum, damit jede Linie dieselben Monate und Raten bekommt
    val ok = okZiele(ziele, daten)
    val reihen = listOf(dg.map { it.t to it.close }) + ok.mapNotNull { daten[it]?.reihe }
    // Ohne gemeinsamen Zeitraum von mindestens MIN_TAGE rechnet nur die Aktie; die Ziele zeigen „zu kurzer Verlauf“
    val gemeinsam = Vergleichsrechnung.gemeinsam(reihen, gewuenscht, jetzt)
    val sp = gemeinsam ?: Vergleichsrechnung.Spanne(gewuenscht, jetzt)
    val r = Analysis.savingsPlan(
        dg.filter { it.t <= sp.bis }, monthly, sp.von,
        if (sp.bis >= jetzt) kursMitErtraegen(d, dg, quotes[symbol]?.price) else null, sp.bis,
    ) ?: run { Note(S.toolNoData); return }
    val e = if (gemeinsam == null) emptyList() else ok.mapNotNull { k ->
        val reihe = daten[k]?.reihe ?: return@mapNotNull null
        Vergleichsrechnung.sparplan(reihe, monthly, sp)?.let { k to it }
    }
    val signal = LocalSignal.current
    val grey = MaterialTheme.colorScheme.onSurfaceVariant
    Spacer(Modifier.height(8.dp))
    ToolChart(
        listOf(r.path to FARBE_AKTIE) + e.map { (k, x) -> x.path to Kategorie.farbe(ziele.indexOf(k) + 1) } + listOf(r.investedPath to grey),
        legend = listOf(Symbols.name(symbol).take(24) to FARBE_AKTIE, S.toolInvested to grey),
    )
    ZielChips(state, ziele, daten, onZiele, onVergleichen)
    KeyValueRow(S.toolInvested, "${Fmt.euro(r.invested)} · ${r.executions} ${S.toolExecutions}")
    KeyValueRow(S.importValueToday, Fmt.euro(r.value))
    KeyValueRow(S.result, "${Fmt.signedEuro(r.gain)}  ${signed(r.pct)}", valueColor = signal.forChange(r.gain))
    r.perYear?.let { KeyValueRow(S.toolPerYearMoneyWeighted, signed(it)) }
    KeyValueRow(S.toolAverageCost, Fmt.priceEuro(r.averageCost))
    if (ziele.isNotEmpty()) ZielZeilen(
        state, ziele, daten,
        { k -> e.firstOrNull { it.first == k }?.second?.let { p -> Triple(p.value, p.pct, p.gain) } },
        S.vgZeitraum(Fmt.date(sp.von), if (sp.bis >= jetzt) S.vgHeute else Fmt.date(sp.bis)),
        if (gemeinsam == null) null else begrenzerText(state, symbol, ok, reihen, gewuenscht, sp.von),
    )
    Note(hinweis(if (ziele.isEmpty()) S.vgPlanNote else S.vgPlanNote + " " + S.vgGleicheRaten, ziele))
}

// Ziele mit ausreichend Verlauf fuer den gemeinsamen Zeitraum.
private fun okZiele(ziele: List<String>, daten: Map<String, VergleichZiele.Daten>): List<String> =
    ziele.filter { k -> daten[k]?.let { d -> Vergleichsrechnung.status(d.reihe, d.geschaefte) == Vergleichsrechnung.Status.OK } == true }

// Hinweis auf die Reihe, die den gemeinsamen Zeitraum nach hinten schiebt (Index 0 ist die Aktie).
private fun begrenzerText(state: AppState, symbol: String, ok: List<String>, reihen: List<List<Pair<Long, Double>>>, gewuenscht: Long, von: Long): String? {
    val b = Vergleichsrechnung.begrenzer(reihen, gewuenscht) ?: return null
    return S.vgKuerzer(if (b == 0) Symbols.name(symbol) else VergleichZiele.name(state, ok[b - 1]), Fmt.date(von))
}

// Schlusshinweis: Weltnote beim MSCI, Waehrungshinweis bei Depot-, Personen- und Investorverlaeufen,
// Laborhinweis bei Regeln aus dem Strategie-Labor.
private fun hinweis(grund: String, ziele: List<String>): String {
    var t = grund
    if ("m:msci" in ziele) t += " " + S.toolWorldNote
    if (ziele.any { it.startsWith("d:") || it.startsWith("i:") || it.startsWith("k:") || it.startsWith("n:") || it.startsWith("r:") }) t += " " + S.vgWaehrungHinweis
    if (ziele.any { it.startsWith("l:") }) t += " " + S.vgLaborHinweis
    return t
}

// Gewaehlte Ziele als Chips zum Entfernen, dahinter der Knopf zur Auswahl.
@Composable
private fun ZielChips(state: AppState, ziele: List<String>, daten: Map<String, VergleichZiele.Daten>, onZiele: (String) -> Unit, onVergleichen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        ziele.forEachIndexed { i, k ->
            ZielChip(Kategorie.farbe(i + 1), VergleichZiele.name(state, k) + (if (k !in daten) " …" else "")) { onZiele(k) }
        }
        Chip("+ " + S.pvCompare, active = false) { onVergleichen() }
    }
}

// Zeitraum der Vergleiche und Zeile je Ziel: Wert und Prozent wie bei der Aktie, sonst der Grund.
@Composable
private fun ZielZeilen(
    state: AppState, ziele: List<String>, daten: Map<String, VergleichZiele.Daten>,
    wert: (String) -> Triple<Double, Double, Double>?, zeitraum: String, kuerzer: String?,
) {
    val signal = LocalSignal.current
    Text(
        zeitraum + (kuerzer?.let { " " + it } ?: ""),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp),
    )
    ziele.forEachIndexed { i, k ->
        val dat = daten[k]
        var text = S.vgLaedt
        var farbe: Color? = null
        if (dat != null) when (Vergleichsrechnung.status(dat.reihe, dat.geschaefte)) {
            Vergleichsrechnung.Status.OK -> {
                val w = wert(k)
                if (w == null) text = S.vgZuKurz
                else { text = "${Fmt.euro(w.first)}  ${signed(w.second)}"; farbe = signal.forChange(w.third) }
            }
            Vergleichsrechnung.Status.KEINE_DATEN -> text = S.vgKeineDaten
            Vergleichsrechnung.Status.ZU_KURZ -> text = S.vgZuKurz
            Vergleichsrechnung.Status.ZU_WENIG_GESCHAEFTE -> text = S.vgGeschaefte(dat.geschaefte ?: 0)
        }
        ZielZeile(Kategorie.farbe(i + 1), VergleichZiele.name(state, k), text, farbe)
    }
}

// Schwankung und Rueckgaenge

// Auch in der Karte „Rendite und Risiko“ (5.91); dort ohne Schwankung, die in den Kennzahlen steht.
@Composable
internal fun RiskTool(state: AppState, symbol: String, mitSchwankung: Boolean = true) {
    val daily = rememberDaily(state, symbol)
    val world = rememberDaily(state, WORLD_ETF)
    var period by remember { mutableIntStateOf(3) }
    if (showsNoData(daily)) return
    val d = daily!!
    val options = periodOptions(d)
    val chosen = if (options.any { it.second == period }) period else options.lastOrNull { it.second > 0 }?.second ?: 0
    ToolZeit(S.widgetZeitraum, options, chosen) { period = it }
    val tradingDays = if (Symbols.typeOf(symbol) == AssetType.CRYPTO) 365 else 252
    val from = startFor(d, chosen)
    val r = Analysis.risk(d, from, tradingDays) ?: run { Note(S.toolNoData); return }
    val w = world?.takeIf { symbol != WORLD_ETF && it.isNotEmpty() }?.let { Analysis.risk(it, from, 252) }
    val signal = LocalSignal.current
    Spacer(Modifier.height(8.dp))
    ToolChart(listOf(r.drawdownPath to signal.down), fillFirst = true, baseline = 0.0)
    if (mitSchwankung) KeyValueRow(S.toolVolatility, Fmt.pct(r.volatility) + (w?.let { "  (${S.toolWorldEtfShort} ${Fmt.pct(it.volatility)})" } ?: ""))
    KeyValueRow(S.maxDrawdown, "−" + Fmt.pct(abs(r.maxDrawdown)) + (w?.let { "  (${S.toolWorldEtfShort} −${Fmt.pct(abs(it.maxDrawdown))})" } ?: ""))
    KeyValueRow(S.toolDrawdownPeriod, S.toolDrawdownRange(Fmt.date(r.peakAt), Fmt.date(r.troughAt)))
    KeyValueRow(S.toolRecovered, r.recoveredAt?.let { Fmt.date(it) } ?: S.toolNotRecovered)
    r.worstDay?.let { KeyValueRow(S.toolWorstDay, "${signed(it.second)} · ${Fmt.date(it.first)}", valueColor = signal.down) }
    r.bestDay?.let { KeyValueRow(S.toolBestDay, "${signed(it.second)} · ${Fmt.date(it.first)}", valueColor = signal.up) }
    KeyValueRow(S.toolUpDays, Fmt.pct(r.upDays, 0))
    Note(if (w != null) S.toolRiskNote + " " + S.toolRiskWorldNote else S.toolRiskNote)
}

// Fairer Wert

@Composable
internal fun FairValueTool(state: AppState, symbol: String, profile: MarketApi.Profile?) {
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
    ChoiceRow(S.widgetZeitraum, listOf(5, 10).map { S.toolYears(it) to it }, years) { years = it }
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
    KeyValueRow(S.importCostBasis, Fmt.euro(tx.qty * tx.price - tx.realized))
    KeyValueRow(S.toolRealized, Fmt.signedEuro(tx.realized), valueColor = signal.forChange(tx.realized))
    KeyValueRow(S.taxSection, Fmt.euro(-tx.tax))
    KeyValueRow(S.feeLabel, Fmt.euro(-tx.fee))
    KeyValueRow(S.docCredit, Fmt.euro(tx.amount - tx.fee))
    Note(S.toolSellNote)
}
