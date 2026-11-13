package de.troetenrepublik.app.ui

import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import de.troetenrepublik.app.plattform.mausWischen
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.engine.Testdepot
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import de.troetenrepublik.app.engine.Diversifizieren
import de.troetenrepublik.app.engine.HistorieRechner
import de.troetenrepublik.app.engine.Kategorien
import de.troetenrepublik.app.engine.KategorienStand
import de.troetenrepublik.app.engine.PortfolioTools
import de.troetenrepublik.app.engine.Pricing
import de.troetenrepublik.app.engine.Trading
import de.troetenrepublik.app.engine.Umschichtschwelle
import de.troetenrepublik.app.engine.Waehrungsschock
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
enum class PortfolioTool { PERFORMANCE, CHECK, FINDER, WEALTH, SPARPLAN, WITHDRAWAL, CRASH, SCENARIO, DIVIDENDS, DIVERSIFICATION, REBALANCE, STILLHALTER, AUFFAELLIG, STUFEN, HAEUSER, OPTIONS, MARKT, HEBEL, GRENZE, BACKTEST, LANGSICHT, ROTATION, FAKTOREN }

// Seiten im Depot-Check in dieser Reihenfolge. In der Werkzeugliste steht jede auch als eigene Zeile.
internal val CHECK_SEITEN = listOf(PortfolioTool.CHECK, PortfolioTool.DIVERSIFICATION, PortfolioTool.REBALANCE, PortfolioTool.CRASH, PortfolioTool.SCENARIO)

// Dritte Seite von Analytics: Gruppen mit je einer Zeile je Werkzeug (Gruppen und Reihenfolge in WerkzeugListe.kt).
@Composable
fun PortfolioToolsList(state: AppState? = null, onDepotVerlauf: (() -> Unit)? = null, onSeite: ((String) -> Unit)? = null, onOpen: (PortfolioTool) -> Unit) {
    Spacer(Modifier.height(4.dp))
    // Langes Druecken auf ein Werkzeug legt das Widget dazu an (nur Android); Werkzeuge ohne Widget bleiben ohne.
    @Composable
    fun mitWidget(vorgabe: de.troetenrepublik.app.data.WidgetVorgabe.Vorgabe?, inhalt: @Composable () -> Unit) {
        if (state != null) WidgetLangDruck(state, vorgabe) { inhalt() } else inhalt()
    }
    WERKZEUG_GRUPPEN.forEach { (gruppe, schluessel) ->
        // Depotverlauf ist ein eigenes Blatt, steht hier aber als weiteres Werkzeug; ohne Aufrufer entfaellt er
        val sichtbar = schluessel.filter { (it != WZ_VERLAUF || onDepotVerlauf != null) }
        if (sichtbar.isEmpty()) return@forEach
        WerkzeugGruppe(gruppe())
        sichtbar.forEachIndexed { i, k ->
            // Trennlinie nur zwischen zwei Werkzeugen einer Gruppe
            if (i > 0) Trennlinie()
            if (k == WZ_VERLAUF) {
                mitWidget(de.troetenrepublik.app.data.WidgetVorgabe.depotverlauf()) { WerkzeugZeile(k, S.pvTitle, S.dvSubtitle) { onDepotVerlauf?.invoke() } }
            } else {
                val t = PortfolioTool.valueOf(k)
                mitWidget(de.troetenrepublik.app.data.WidgetVorgabe.werkzeug(t.name)) {
                    WerkzeugZeile(k, S.ptTitle(k), werkzeugUnterzeile(k)) {
                        // Aus der Liste geoeffnet beginnt der Depot-Check auf der gewaehlten Seite
                        if (t in CHECK_SEITEN) letzteCheckSeite = -1
                        // Der Aktienfinder ist die Seite hinter der Kachel der Startseite
                        if (t == PortfolioTool.FINDER && onSeite != null) onSeite(AKTIENFINDER_ROUTE) else onOpen(t)
                    }
                }
            }
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
    // Lange Liste: eigene LazyColumn mit Titel und Unterzeile als erstem Eintrag
    if (tool == PortfolioTool.STUFEN) { StufenSeite(state, onOpen); return }
    run {
        // Immer bildschirmhoch, damit das Blatt beim Laden nicht auf halber Hoehe stehen bleibt
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Text(S.ptTitle(tool.name), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp))
            Text(S.ptSubtitle(tool.name), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(4.dp))
            when (tool) {
                PortfolioTool.PERFORMANCE -> PerformanceTool(state, onOpen)
                PortfolioTool.WEALTH -> WealthTool(state)
                PortfolioTool.SPARPLAN -> SparplanTool(state)
                PortfolioTool.WITHDRAWAL -> EntnahmeplanTool(state)
                PortfolioTool.DIVIDENDS -> DividendsTool(state, onOpen)
                PortfolioTool.OPTIONS -> OptionsrechnerTool(state, onDerivat = { onOpen(DERIVAT_ROUTE + it) })
                PortfolioTool.STILLHALTER -> StillhalterTool(state, onOpen)
                PortfolioTool.AUFFAELLIG -> AuffaelligeTool(state, onOpen)
                PortfolioTool.HAEUSER -> HaeuserRanglisteTool(state)
                PortfolioTool.MARKT -> MarktSchlagenTool(onOpen)
                PortfolioTool.HEBEL -> HebelTool(state)
                PortfolioTool.GRENZE -> GrenzeTool(state)
                PortfolioTool.BACKTEST -> BacktestTool(state, onOpen)
                PortfolioTool.LANGSICHT -> LangeSichtTool()
                PortfolioTool.ROTATION -> RotationTool(state, onOpen)
                PortfolioTool.FAKTOREN -> FaktorTool(state)
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
    val pager = rememberPagerState(initialPage = (if (letzteCheckSeite >= 0) letzteCheckSeite else start).coerceIn(0, CHECK_SEITEN.size - 1)) { CHECK_SEITEN.size }
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { letzteCheckSeite = it } }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        Text(S.ptTitle(PortfolioTool.CHECK.name), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp))
        val wm = rememberReiterWidgetMenue(state) { i -> de.troetenrepublik.app.data.WidgetVorgabe.werkzeug(CHECK_SEITEN[i].name) }
        MitlaufendeReiter(
            anzahl = CHECK_SEITEN.size,
            pager = pager,
            langDruecken = wm.langDruecken,
            menue = { i -> wm.menue(i) },
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
                    PortfolioTool.DIVERSIFICATION -> DiversificationTool(state, onOpen) {
                        scope.launch { pager.animateScrollToPage(CHECK_SEITEN.indexOf(PortfolioTool.REBALANCE)) }
                    }
                    PortfolioTool.REBALANCE -> RebalanceTool(state, onOpen)
                    PortfolioTool.CRASH -> CrashTool(state, onOpen)
                    PortfolioTool.SCENARIO -> SzenarienTool(state, onOpen) {
                        // Der Waehrungsschock steht eingeklappt unten im Crash-Test
                        aufklappWunsch = CRASH_WAEHRUNG
                        scope.launch { pager.animateScrollToPage(CHECK_SEITEN.indexOf(PortfolioTool.CRASH)) }
                    }
                    else -> DepotCheckTool(state, onOpen) { ziel -> scope.launch { pager.animateScrollToPage(CHECK_SEITEN.indexOf(ziel)) } }
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

private fun signedPct(v: Double, digits: Int = 1): String = (if (v > 0.00001) "+" else if (v < -0.00001) "−" else "") + Fmt.pct(abs(v), digits)

// Abstand zum Vergleich in Prozentpunkten mit Vorzeichen, bei sehr kleinem Abstand ohne.
internal fun punkteVorzeichen(gap: Double): String = (if (gap > 0.05) "+" else if (gap < -0.05) "−" else "") + Fmt.num(abs(gap), 1)

// Aufrunden auf 1, 2 oder 5 mal eine Zehnerpotenz.
private fun niceCeil(v: Double): Double {
    if (v <= 0) return 1.0
    val p = 10.0.pow(kotlin.math.floor(log10(v)))
    val m = v / p
    return p * when { m <= 1.0 -> 1.0; m <= 2.0 -> 2.0; m <= 5.0 -> 5.0; else -> 10.0 }
}

private fun roundTo(v: Double, step: Double): Double = if (step <= 0) v else Math.round(v / step) * step

// Eine Position behaelt ihre Farbe, auch wenn sich die Reihenfolge nach Wert aendert (Farbe nach Schluessel, nicht nach Rang).
@Composable
internal fun positionsFarbe(schluessel: String): Color = AnteilsFarben.folge((schluessel.hashCode() % 7 + 7) % 7)

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
        Beschriftung(S.ptYourDepot, style = MaterialTheme.typography.bodySmall, color = grey, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Beschriftung(bench, style = MaterialTheme.typography.bodySmall, color = grey, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
    rows.forEach { (label, a, b) ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Beschriftung(label, style = MaterialTheme.typography.bodyMedium, color = grey, modifier = Modifier.weight(1.2f))
            // Werte der Vergleichsspalten bleiben in einer Zeile
            Text(a, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
            Text(b, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
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

// Die Indizes sind dieselben wie in der Kurve des Depotverlaufs (Portfolioverlauf.MASSSTAEBE), dazu Gold, Bitcoin, Tagesgeld.
private val BENCHES = de.troetenrepublik.app.market.Portfolioverlauf.MASSSTAEBE.map { Bench(it.schluessel, it.symbol) } +
    listOf(Bench("GOLD", "4GLD.DE"), Bench("BITCOIN", "BTC-EUR"), Bench("CASH", null))

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
    var benchKey by rememberSaveable { mutableStateOf("MSCI_WORLD") }
    val flows = remember(account.transactions) { PortfolioTools.flows(account) }
    if (flows.none { it.amount > 0 }) return

    // Abgeschlossene Verkaeufe: Trefferquote, Gewinnfaktor, mittlerer Gewinn und Verlust
    // Zaehlregel wie im Anlegerprofil und im Handelskalender (Verkaufsbilanz)
    val verkaeufe = remember(account.transactions.size) { de.troetenrepublik.app.engine.Verkaufsbilanz.auswerten(account.transactions) }
    verkaeufe?.let { v ->
        SectionHeader(S.ptClosedSales)
        val sig = LocalSignal.current
        // Trefferquote als Balken, durchschnittlicher Gewinn und Verlust als zwei Balken im selben Massstab
        ShareBar(S.slTreffer, Fmt.pct(v.trefferquote * 100, 0), v.trefferquote, sig.up)
        v.gewinnfaktor?.let { InfoWertZeile(S.slGewinnfaktor, Fmt.num(it, 2), S.slGewinnfaktor, S.st("vk.faktor.info")) }
        val groesse = maxOf(v.mittelGewinn ?: 0.0, abs(v.mittelVerlust ?: 0.0), 0.01)
        v.mittelGewinn?.let { ShareBar(S.st("vk.gewinn"), Fmt.euro(it), it / groesse, sig.up) }
        v.mittelVerlust?.let { ShareBar(S.st("vk.verlust"), Fmt.euro(it), abs(it) / groesse, sig.down) }
        KeyValueRow(S.st("vk.je"), Fmt.euro(v.erwartung), valueColor = sig.forChange(v.erwartung))
        HinweisInfo(S.st("vk.hint", listOf(v.gewinne.toString(), v.anzahl.toString())) + "\n" + S.st("vk.steuer"))
        GeschaefteAuswertung(account)
    }

    // Jeder Kauf und Verkauf am selben Tag mit einem Index nachgespielt, die Indizes seitenweise wischbar
    SectionHeader(S.ptIndexTitle)
    val pager = rememberPagerState(initialPage = BENCHES.indexOfFirst { it.key == benchKey }.coerceAtLeast(0)) { BENCHES.size }
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { benchKey = BENCHES[it.coerceIn(0, BENCHES.size - 1)].key } }
    val scope = rememberCoroutineScope()
    MitlaufendeReiter(BENCHES.size, pager, { i -> S.ptBenchName(BENCHES[i].key) }, Modifier.padding(top = 8.dp, bottom = 4.dp)) { i ->
        scope.launch { pager.animateScrollToPage(i) }
    }
    HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().mausWischen(pager), verticalAlignment = Alignment.Top) { seite ->
        Column(Modifier.fillMaxWidth()) { IndexVergleich(state, BENCHES[seite], flows, onOpen) }
    }
}

// Eine Seite des Indexvergleichs: dasselbe Geld im gewaehlten Vergleich, ganzes Depot und je Wert.
@Composable
private fun IndexVergleich(state: AppState, bench: Bench, flows: List<PortfolioTools.Flow>, onOpen: (String) -> Unit) {
    val account = depotKonto(state)
    val valuation = depotBewertung(state)
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val signal = LocalSignal.current
    val name = S.ptBenchName(bench.key)

    // Mit demselben Geld: jede Buchung des Depots im Vergleich nachgespielt
    var money by remember { mutableStateOf<MoneyResult?>(null) }
    LaunchedEffect(bench.key, account.transactions.size) {
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
    // Depot und Vergleich als zwei Balken im selben Massstab, das Depot in der Farbe seines Vorsprungs oder Rueckstands
    val diff = depotTotal - r.total
    val groesster = maxOf(depotTotal, r.total, 1.0)
    ShareBar(S.ptDepotToday, Fmt.euro(depotTotal), depotTotal / groesster, signal.forChange(diff))
    ShareBar(S.ptWith(name), Fmt.euro(r.total), r.total / groesster, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
    KeyValueRow(S.ptDifference, Fmt.signedEuro(diff), valueColor = signal.forChange(diff))
    val now = System.currentTimeMillis()
    // Zinsfuss aller Zahlungen zu ihren Zeitpunkten: Kaeufe hinein, Verkaufserloese und Dividenden heraus, am Ende
    // nur der heutige Bestand. Die Vergleichsanlage mit ihren tatsaechlich erzielten Verkaufsbetraegen.
    val divFlows = account.transactions.filter { it.type == de.troetenrepublik.app.data.TxType.DIVIDEND }
        .map { PortfolioTools.Flow(it.ts, -(it.amount + it.tax), it.posKey) }
    val depotFlows = (flows + divFlows).sortedBy { it.t }
    val start = flows.first { it.amount > 0 }.t
    val proJahr = now - start >= PortfolioTools.JAHR_MS
    val periode = if (proJahr) PortfolioTools.JAHR_MS.toDouble() else (now - start).toDouble()
    val a = PortfolioTools.moneyWeighted(depotFlows, valuation.securities, now, periode)
    val bb = PortfolioTools.moneyWeighted(r.flows.ifEmpty { flows }, r.value, now, periode)
    if (a != null && bb != null) CompareRows(name, listOf(Triple(if (proJahr) S.dp("dv.geldgewichtet") else S.result, signedPct(a * 100), signedPct(bb * 100))))
    // Methode und Begriffe hinter dem Info-Symbol, davor die Kernzeile historisch und hypothetisch
    HinweisInfo(
        S.ptSameMoneyNote + (if (bench.symbol == null) " " + S.ptCashNote(Fmt.pct(settings.interestPct)) else "") +
            "\n\n" + S.ptDepotToday + ": " + S.ptDepotTodayHint,
        kurz = hinweisText("hist"),
    )

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
    rows.forEachIndexed { zeile, (vp, own, ref) ->
        val gap = own - ref
        val points = punkteVorzeichen(gap)
        if (zeile > 0) Trennlinie()
        Row(
            Modifier.fillMaxWidth().clickable { onOpen(vp.position.symbol) }.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InstrumentLogo(vp.position.symbol, vp.position.name, size = 36)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                NamenText(vp.position.name, style = MaterialTheme.typography.titleSmall)
                Text("$name ${signedPct(ref)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (gap >= 0) S.ptGapBeats(points) else S.ptGapTrails(points), style = MaterialTheme.typography.bodySmall,
                    color = signal.forChange(gap))
            }
            Spacer(Modifier.width(10.dp))
            ChangeValue(own, signedPct(own), style = MaterialTheme.typography.titleSmall)
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
    // [0.78] Historische Annahmen (ui/HistorischeAnnahmen.kt): 0 eigene Regler, 1 Welt, 2 Land der Einstellungen
    val hist = rememberHistBasen(state)
    var annahme by rememberSaveable { mutableIntStateOf(0) }
    var folgen by rememberSaveable { mutableStateOf(false) }
    val basis = hist?.basis(annahme)
    val kenn = remember(basis) { basis?.let { HistorieRechner.kennzahlen(it) } }
    val retEff = kenn?.let { HistorieRechner.nominal(it.rendite, INFLATION) } ?: ret.toDouble()
    val volEff = kenn?.schwankung ?: vol.toDouble()
    val folgenAn = remember(basis, folgen, years) {
        basis != null && folgen && HistorieRechner.starts(basis, years.roundToInt()).size >= HistorieRechner.MIN_FAELLE
    }
    // Eigener Startwert, vorbelegt mit dem Wert der Wertpapiere
    var eigenerStart by rememberSaveable { mutableFloatStateOf(roundTo(valuation.securities, 1_000.0).toFloat().coerceIn(0f, 2_000_000f)) }
    val start = when (startMode) { 0 -> valuation.securities; 1 -> valuation.total; 3 -> eigenerStart.toDouble(); else -> 0.0 }
    val startKey = (start / 100.0).roundToLong()

    // Ergebnis steht oben, die Einstellungen darunter
    val einstellungen: @Composable () -> Unit = {
        // Feste Felder in zwei Spalten, der Betrag steht je Feld in einer eigenen Zeile und bricht nie um
        val wp = Fmt.compactEuro(valuation.securities)
        val ges = Fmt.compactEuro(valuation.total)
        ToolFelder(S.ptStartValue, listOf(
            S.ptStartSecurities(wp).replace(wp, "\n$wp").trim() to 0,
            S.ptStartTotal(ges).replace(ges, "\n$ges").trim() to 1,
            S.ptStartZero to 2,
            S.ptStartEigen to 3,
        ), startMode, spalten = 2) { startMode = it; scrub = null }
        if (startMode == 3) {
            SliderRow(S.ptStartValue, Fmt.euro(eigenerStart.toDouble(), 0), eigenerStart, 0f..2_000_000f, 1_000f) { eigenerStart = it; scrub = null }
        }
        SliderRow(S.ptMonthlySaving, Fmt.euro(monthly.toDouble(), 0), monthly, 0f..3000f, 25f) { monthly = it; scrub = null }
        SliderRow(S.ptHorizon, S.toolYears(years.roundToInt()), years, 1f..50f, 1f) { years = it; scrub = null }
        HistAnnahmenWahl(hist, annahme) { annahme = it; scrub = null }
        if (hist != null && basis != null) HistAnnahmenZeilen(hist, basis, years.roundToInt(), folgen) { folgen = it; scrub = null }
        else {
            SliderRow(S.ptPerYear, Fmt.pct(ret.toDouble(), 1), ret, 0f..12f, 0.5f) { ret = it }
            SliderRow(S.toolVolatility, Fmt.pct(vol.toDouble(), 0), vol, 0f..40f, 1f) { vol = it }
        }
        ToolChoice(S.ptRaise, listOf(0f, 1f, 2f, 3f, 5f).map { Fmt.pct(it.toDouble(), 0) to it }, raise) { raise = it }
        // Historische Renditen sind real: die Betraege stehen dann immer in heutiger Kaufkraft
        if (basis == null) ToggleRow(S.ptRealTerms(Fmt.pct(INFLATION, 0)), real) { real = it }
        ToolChoice(S.ptTarget, listOf(100_000.0, 250_000.0, 500_000.0, 1_000_000.0, 2_000_000.0, 5_000_000.0).map { roundAmount(it) to it }, target) { target = it }
    }

    var result by remember { mutableStateOf<PortfolioTools.Projection?>(null) }
    var aufbau by remember { mutableStateOf<HistorieRechner.Aufbau?>(null) }
    LaunchedEffect(startKey, monthly, years, retEff, volEff, raise, real, target, basis, folgenAn) {
        val a = if (folgenAn && basis != null) withContext(Dispatchers.Default) {
            HistorieRechner.aufbau(basis, startKey * 100.0, monthly.toDouble(), years.roundToInt(), raise.toDouble(), INFLATION, target)
        } else null
        val r = a?.projektion ?: withContext(Dispatchers.Default) {
            PortfolioTools.project(
                start = startKey * 100.0, monthly = monthly.toDouble(), years = years.roundToInt(), returnPct = retEff,
                volPct = volEff, raisePct = raise.toDouble(), inflationPct = if (real || basis != null) INFLATION else 0.0, target = target,
                check = { ensureActive() },
            )
        }
        aufbau = a
        result = r
    }
    val r = result ?: run { einstellungen(); ToolNote(S.toolLoading); return }
    val end = r.years
    val i = scrub?.coerceIn(0, end) ?: end
    Headline(S.ptInYears(i), Fmt.euro(r.p50[i], 0))
    KeyValueRow(S.ptWeak, Fmt.euro(r.p10[i], 0))
    KeyValueRow(S.ptStrong, Fmt.euro(r.p90[i], 0))
    einstellungen()
    val color = MaterialTheme.colorScheme.onSurface
    FanChart(
        median = r.p50, outer = r.p10 to r.p90, inner = r.p25 to r.p75, reference = r.invested, color = color,
        startLabel = S.today, endLabel = S.ptInYears(end),
        modifier = Modifier.fillMaxWidth().height(210.dp).padding(horizontal = 20.dp, vertical = 10.dp),
        selected = scrub, onScrub = { scrub = it },
    )
    ChartLegend(listOf(S.ptMedian to color, S.ptBandLabel to color.copy(alpha = 0.35f), S.ptPaidIn to MaterialTheme.colorScheme.onSurfaceVariant))
    KeyValueRow(S.ptPaidIn, Fmt.euro(r.invested[end], 0))
    KeyValueRow(S.ptGrowth, Fmt.euro(r.p50[end] - r.invested[end], 0))
    val a = aufbau
    KeyValueRow(
        S.ptTargetReached(roundAmount(target)),
        if (a != null) S.hrVon(a.erreicht, a.faelle) else S.ptTargetChance(Fmt.pct(r.targetProbability * 100.0, 0)),
    )
    KeyValueRow(S.ptTargetMedian, r.targetYears?.let { S.ptTargetWhen(ceil(it).toInt().coerceAtLeast(1)) } ?: S.ptTargetNever)
    if (a != null) {
        KeyValueRow(S.hrStartjahre, Fmt.num(a.faelle.toDouble(), 0), hint = S.histZeitraum(a.ersterStart, a.letzterStart))
        KeyValueRow(S.hrSchlechtStart(a.schlechtStart), Fmt.euro(a.schlechtEnde, 0))
        KeyValueRow(S.hrBestStart(a.bestStart), Fmt.euro(a.bestEnde, 0))
    }
    if (hist != null && basis != null) {
        HinweisInfo(histNote(hist, basis, a?.let { HistFolgen(it.ersterStart, it.letzterStart, end) }, S.hrRateInflation(Fmt.pct(INFLATION, 0))), kurz = S.hrNoteEnde)
    } else HinweisInfo(S.ptWealthNote, kurz = hinweisText("annahmen"))

    // [0.72] Schwankungsrisiko des heutigen Werts nach einem Jahr, aus derselben Monatsschritt-Rechnung wie oben.
    // Die Schwankung kommt aus dem Verlauf des echten Depots (Tagesrenditen der letzten drei Jahre, ohne Ein- und
    // Auszahlungen), solange der Verlauf dafuer reicht; sonst gilt der Regler.
    // [0.75] Ein fremdes Depot (Person) hat keinen eigenen Verlauf: Dort zaehlen die Kurse seines heutigen Bestands
    // (die 20 groessten Werte, Wochenrenditen der letzten drei Jahre).
    val fremd = LocalFremdesDepot.current
    val bestandSchluessel = valuation.positions.joinToString("|") { it.position.symbol }
    val depotVol by produceState<Double?>(null, account.transactions.size, account.positions.size, bestandSchluessel) {
        value = null
        value = runCatching {
            val jetzt = System.currentTimeMillis()
            val seit = jetzt - 3 * 365L * DAY_MS
            if (fremd != null) {
                val gruppen = valuation.positions.filter { !it.position.isDerivative && it.value > 0 }.groupBy { it.position.symbol }
                    .map { (s, l) -> s to l.sumOf { it.value } }.sortedByDescending { it.second }.take(20)
                val reihen = coroutineScope { gruppen.map { g -> async { state.dailyCloses(g.first) } }.awaitAll() }
                val mit = gruppen.indices.filter { i -> reihen[i].count { it.t >= seit } >= 120 }
                withContext(Dispatchers.Default) { PortfolioTools.bestandsSchwankung(mit.map { gruppen[it].second }, mit.map { reihen[it] }, seit) }
            } else {
                val parts = state.portfolioParts(Range.MAX)
                var werte = parts?.raw?.candles?.map { it.t to it.close }.orEmpty()
                if (valuation.positions.isNotEmpty() && (werte.lastOrNull()?.first ?: 0L) < jetzt - 60_000L) werte = werte + (jetzt to valuation.securities)
                withContext(Dispatchers.Default) {
                    de.troetenrepublik.app.engine.Performanceanalyse.berechnen(werte, account.transactions, account.cash, seit, jetzt)?.volatilitaet
                }
            }?.takeIf { it > 0.0 && it.isFinite() }?.times(100.0)?.coerceIn(1.0, 80.0)
        }.getOrNull()
    }
    var varEs by remember { mutableStateOf<PortfolioTools.VarEs?>(null) }
    LaunchedEffect(startKey, retEff, volEff, depotVol) {
        varEs = withContext(Dispatchers.Default) {
            PortfolioTools.varEs(startKey * 100.0, retEff, depotVol ?: volEff, 1, check = { ensureActive() })
        }
    }
    varEs?.let { v ->
        SectionHeader(S.ptVarTitle)
        val basis = startKey * 100.0
        // Ein Wert je Zeile (Euro), die Spanne als Balken darunter
        InfoWertZeile(
            S.ptVar(Fmt.pct(95.0, 0)), Fmt.signedEuro(-v.varAnteil * basis),
            S.ptVar(Fmt.pct(95.0, 0)), S.ptVarHint, LocalSignal.current.down,
        )
        ChangeBar(S.dp("wl.prozentStart"), -v.varAnteil, signedPct(-v.varAnteil * 100.0), LocalSignal.current.down, full = 0.6)
        InfoWertZeile(
            S.ptEs(Fmt.pct(95.0, 0)), Fmt.signedEuro(-v.esAnteil * basis),
            S.ptEs(Fmt.pct(95.0, 0)), S.ptEsHint, LocalSignal.current.down,
        )
        ChangeBar(S.dp("wl.prozentStart"), -v.esAnteil, signedPct(-v.esAnteil * 100.0), LocalSignal.current.down, full = 0.6)
        KeyValueRow(S.ptMedian, Fmt.signedEuro(v.medianAnteil * basis), valueColor = LocalSignal.current.forChange(v.medianAnteil))
        val dv = depotVol
        HinweisInfo(
            if (fremd != null && dv != null) S.ptVarNoteBestand(v.paths, Fmt.pct(dv, 0)) else S.ptVarNote(v.paths, dv?.let { Fmt.pct(it, 0) }),
            kurz = hinweisText("annahmen"),
        )
    }
    if (basis != null) QuellenAnhang(listOf(histQuelle()))
}

// Crash-Test

internal const val REFERENCE = "^GSPC"

// Tages- und Wochenreihen je Basiswert und fuer den S&P 500, dazu die Betas.
internal class CrashData(
    val refDaily: List<Candle>,
    val refLong: List<Candle>,
    val daily: Map<String, List<Candle>>,
    val long: Map<String, List<Candle>>,
    val beta: Map<String, Double>,
)

internal fun defaultBeta(symbol: String): Double = when (Symbols.typeOf(symbol)) {
    AssetType.CRYPTO -> 1.5
    AssetType.COMMODITY -> 0.3
    else -> 1.0
}

// Echt, wenn es Kurse gibt, sonst ueber das Beta geschaetzt.
internal fun moveIn(symbol: String, crash: PortfolioTools.Crash, data: CrashData, refMove: Double): Pair<Double, Boolean> {
    val own = PortfolioTools.windowReturn(data.daily[symbol].orEmpty(), crash.from, crash.to)
        ?: PortfolioTools.windowReturn(data.long[symbol].orEmpty(), crash.from, crash.to)
    if (own != null) return own to false
    val b = (data.beta[symbol] ?: defaultBeta(symbol)).coerceIn(0.0, 3.0)
    return (exp(b * ln(1.0 + refMove)) - 1.0) to true
}

// Wert einer Position, wenn sich ihr Basiswert um move veraendert. Derivate werden neu bewertet.
internal fun shocked(state: AppState, vp: ValuedPosition, move: Double, now: Long): Double {
    val d = vp.position.derivative ?: return vp.value * (1.0 + move)
    if (d.isKnockedOut) return vp.value
    val q = state.quotes.value[vp.position.symbol] ?: return (vp.value * (1.0 + move)).coerceAtLeast(0.0)
    val price = Pricing.price(d, q.price * (1.0 + move), now, q.fx, state.riskFree)
    return if (price.isFinite()) vp.qty * price else vp.value
}

// Crash-Test mit Wahl zwischen dem echten Depot und einem eigenen Testdepot. Bei fremden Depots entfaellt die Wahl.
@Composable
private fun CrashTool(state: AppState, onOpen: (String) -> Unit) {
    if (LocalFremdesDepot.current != null) { CrashRechnung(state, onOpen); return }
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val eigenes by state.valuation.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var test by rememberSaveable { mutableStateOf(false) }
    ToolChoice(null, listOf(S.ptCrashMein to false, S.ptCrashTest to true), test) { test = it }
    if (!test) { CrashRechnung(state, onOpen); return }
    val eintraege = remember(settings.crashTestdepot) { Testdepot.laden(settings.crashTestdepot) }
    val aendern: (List<Testdepot.Eintrag>) -> Unit = { neu ->
        scope.launch { state.settings.update { it.copy(crashTestdepot = Testdepot.speichern(neu)) } }
    }
    TestdepotEditor(state, eintraege, eigenes.positions, aendern)
    if (eintraege.isEmpty()) { ToolNote(S.ptTestLeer); return }
    val bewertung = Testdepot.bewertung(eintraege)
    val fremd = FremdesDepot(S.ptCrashTest, bewertung, Account(positions = bewertung.positions.map { it.position }), heim = "EUR")
    CompositionLocalProvider(LocalFremdesDepot provides fremd) { CrashRechnung(state, onOpen, S.ptCrashTest) }
}

@Composable
private fun CrashRechnung(state: AppState, onOpen: (String) -> Unit, titel: String = S.ptYourDepot) {
    val valuation = depotBewertung(state)
    val signal = LocalSignal.current
    val items = valuation.positions.filter { it.value > 0 }
    if (items.isEmpty()) { ToolNote(S.ptNoPositions); return }
    val symbols = remember(items.map { it.position.symbol }.distinct().sorted().joinToString("|")) { items.map { it.position.symbol }.distinct() }
    var data by remember { mutableStateOf<CrashData?>(null) }
    var versuch by remember { mutableIntStateOf(0) }
    LaunchedEffect(symbols, versuch) {
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
    val cd = data ?: run { ToolNote(S.toolLoading); return }
    val now = System.currentTimeMillis()
    val total = items.sumOf { it.value }
    val grey = MaterialTheme.colorScheme.onSurfaceVariant
    // [0.75] Ohne Kurse des S&P 500 fehlen die Krisen; ohne Verbindung steht das da, mit Knopf fuer einen neuen Versuch
    if (cd.refDaily.isEmpty() && cd.refLong.isEmpty() && state.market.ohneVerbindung()) { EmptyHint(S.ohneVerbindung, S.erneutVersuchen to { versuch++ }); return }
    // Erholung ueber die fruehe Reihe und die Tagesreihe zusammen, damit sie auch nach dem Ende der fruehen Reihe zaehlt
    val refGesamt = remember(cd) { PortfolioTools.zusammengesetzt(cd.refLong, cd.refDaily) }
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
            var schaetzInfo by remember(crash.key) { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(S.ptCrashName(crash.key), style = MaterialTheme.typography.titleMedium)
                if (estimated > 0) {
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Outlined.Info, S.infoErklaerung, tint = grey,
                        modifier = Modifier.size(18.dp).textKlick(10.dp) { schaetzInfo = true },
                    )
                }
            }
            if (schaetzInfo) MarktInfoBlatt(S.ptCrashName(crash.key), S.ptEstimated(estimated, items.size)) { schaetzInfo = false }
            Text(S.ptFromTo(Fmt.date(crash.from), Fmt.date(crash.to)), style = MaterialTheme.typography.bodySmall, color = grey,
                modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(6.dp))
            ChangeBar(titel, depotMove, signedPct(depotMove * 100), signal.forChange(depotMove))
            ChangeBar(S.ptReference, refMove, signedPct(refMove * 100), grey)
            KeyValueRow(S.ptCrashVerlust, Fmt.signedMoney(after - total), valueColor = signal.forChange(after - total))
            worst?.takeIf { items.size > 1 }?.let { (vp, ch) ->
                NamenWertZeile(S.ptCrashAmStaerksten, positionsKandidaten(vp.position.symbol, vp.position.name), signedPct(ch * 100), wertFarbe = signal.forChange(ch))
            }
            val rec = PortfolioTools.recoveryDays(refGesamt, crash.from, crash.to)
            KeyValueRow(S.ptCrashErholung, rec?.let { S.ptDurationKurz(it) } ?: S.ptCrashErholungOffen)
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
    ChangeBar(S.ptEstimatedDepot, move, signedPct(move * 100), signal.forChange(move), full = 0.6)
    KeyValueRow(S.ptDifference, Fmt.signedMoney(after - total), valueColor = signal.forChange(move))
    // [0.75] Werte ohne gemessenes Beta (zu kurze Kursreihe oder keine Verbindung) folgen einem Richtwert; das steht da
    val crashSymbole = items.map { it.position.symbol }.distinct()
    val ohneBeta = crashSymbole.count { cd.beta[it] == null }
    if (ohneBeta > 0) ToolNote(S.ptBetaRichtwert(ohneBeta, crashSymbole.size))
    var alleCrash by remember { mutableStateOf(false) }
    val crashSortiert = lines.sortedBy { it.third - it.first.value }
    (if (alleCrash) crashSortiert else crashSortiert.take(10)).forEachIndexed { zeile, (vp, b, v) ->
        val ch = if (vp.value > 0) v / vp.value - 1.0 else 0.0
        if (zeile > 0) Trennlinie()
        NamensZeile(vp.position.name, signedPct(ch * 100), hint = "Beta ${Fmt.num(b, 2)}", valueColor = signal.forChange(ch),
            onClick = { onOpen(vp.position.symbol) })
    }
    if (crashSortiert.size > 10) ListenPfeil(alleCrash, label = if (alleCrash) S.less else "${S.more} (${crashSortiert.size - 10})") { alleCrash = !alleCrash }
    // [0.73] Heimatwaehrung steigt oder faellt: nur die Umrechnung der Positionen in Fremdwaehrung
    AufklappAbschnitt(CRASH_WAEHRUNG, S.ptFxTitel, S.ptFxInfo) { WaehrungsschockInhalt(state, items, onOpen) }
    ErklaerungAufklappen(S.ptCrashInfo, Modifier.padding(top = 8.dp), titel = S.ptCrashInfoTitel)
    HinweisInfo(S.ptCrashNote, kurz = hinweisText("modell"))
}

// Schluessel des Abschnitts Waehrungsschock im Crash-Test; „Was waere, wenn“ fuehrt beim Dollar dorthin.
internal const val CRASH_WAEHRUNG = "crash-waehrung"

@Composable
private fun WaehrungsschockInhalt(state: AppState, items: List<ValuedPosition>, onOpen: (String) -> Unit) {
    val signal = LocalSignal.current
    val quotes by state.quotes.collectAsStateWithLifecycle()
    // Heimatwaehrung des Depots: beim eigenen der Euro, bei fremden Depots deren Waehrung (wie im Depot-Check)
    val heim = Waehrungsschock.code(LocalFremdesDepot.current?.heim ?: "EUR")
    var steigt by rememberSaveable { mutableStateOf(true) }
    var prozent by rememberSaveable { mutableFloatStateOf(10f) }
    val schluessel = items.joinToString("|") { it.position.symbol }
    // Laender der ETFs aus dem gezeigten Depot (bei einer Person aus ihrem Bestand)
    val etf by produceState<Map<String, Map<String, Double>>?>(null, schluessel) {
        value = de.troetenrepublik.app.market.ohneAbbruch { state.etfLaender(items) }.getOrNull()?.first ?: emptyMap()
    }
    val name = S.waehrungName(heim)
    ToolChoice(null, listOf(S.ptFxSteigt(name) to true, S.ptFxFaellt(name) to false), steigt) { steigt = it }
    SliderRow(S.ptFxUm, Fmt.pct(prozent.toDouble(), 0), prozent, 5f..30f, 5f) { prozent = it }
    val verteilung = etf ?: run { ToolNote(S.toolLoading); return }
    val total = items.sumOf { it.value }
    val schock = (if (steigt) 1 else -1) * prozent / 100.0
    val verteilungen = items.map { vp ->
        val s = vp.position.symbol
        Waehrungsschock.verteilung(s, quotes[s]?.nativeCurrency?.takeIf { it.isNotBlank() } ?: Symbols.find(s)?.currency ?: "EUR") { k -> verteilung[k] }
    }
    val e = Waehrungsschock.rechnen(items.map { it.value }, verteilungen, heim, schock)
    if (e.fremdanteil < 0.0005) { ToolNote(S.ptFxKeine(name)); return }
    val now = System.currentTimeMillis()
    val nachher = items.mapIndexed { i, vp -> shocked(state, vp, e.bewegung[i], now) }
    val move = if (total > 0) nachher.sum() / total - 1.0 else 0.0
    ChangeBar(S.ptEstimatedDepot, move, signedPct(move * 100), signal.forChange(move), full = 0.3)
    KeyValueRow(S.ptDifference, Fmt.signedMoney(nachher.sum() - total), valueColor = signal.forChange(move))
    ShareBar(S.ptFxFremd, Fmt.pct(e.fremdanteil * 100, 0), e.fremdanteil, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
    var alleFx by remember { mutableStateOf(false) }
    val fxSortiert = items.indices.sortedBy { nachher[it] - items[it].value }
    var fxZeile = 0
    (if (alleFx) fxSortiert else fxSortiert.take(10)).forEach { i ->
        val vp = items[i]
        val ch = if (vp.value > 0) nachher[i] / vp.value - 1.0 else 0.0
        if (abs(ch) < 0.00005) return@forEach
        if (fxZeile++ > 0) Trennlinie()
        NamensZeile(vp.position.name, signedPct(ch * 100), hint = S.ptFxZeile(Fmt.pct(Waehrungsschock.fremdanteil(verteilungen[i], heim) * 100, 0)),
            valueColor = signal.forChange(ch), onClick = { onOpen(vp.position.symbol) })
    }
    if (fxSortiert.size > 10) ListenPfeil(alleFx, label = if (alleFx) S.less else "${S.more} (${fxSortiert.size - 10})") { alleFx = !alleFx }
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
            holdings.map { vp -> async { dividendenQuelle(state, vp.key, vp.position.name, vp.position.symbol, vp.qty) } }.awaitAll()
        }
        expected = PortfolioTools.expectedDividends(sources, now)
    }
    val list = expected ?: run { ToolNote(S.toolLoading); return }
    val gross = list.sumOf { it.amount }
    if (gross < 0.01) {
        ToolNote(S.ptDivNone)
        if (received > 0) KeyValueRow(S.ptDivReceived, Fmt.euro(received))
        HinweisInfo(S.ptDivNote)
        return
    }
    // Stand des laufenden Steuerjahres (im Januar zaehlt der Freibetrag des Vorjahres nicht mehr); ETF-Ertraege nach
    // Teilfreistellung, Verlusttopf der sonstigen Ertraege angerechnet.
    // [0.75] Ein fremdes Depot (Person in den USA) zahlt keine deutsche Steuer und hat nicht den Sparerpauschbetrag des
    // eigenen Kontos: dort brutto, so beschriftet.
    // [0.78] Zahltag und Betrag nach Steuern je Zahlung wie im Handelskalender (Dividendenerwartung)
    val fremd = LocalFremdesDepot.current != null
    val zahlungen = remember(list, fremd, account.tax, settings.allowance) {
        if (fremd) de.troetenrepublik.app.engine.Dividendenerwartung.zahlungen(list, null)
        else de.troetenrepublik.app.engine.Dividendenerwartung.fuerKonto(list, account.tax, settings.allowance, System.currentTimeMillis())
    }
    val net = zahlungen.sumOf { it.netto }
    Headline(S.ptDivExpected, Fmt.euro(net), if (fremd) S.ptDivBrutto else S.dp("div.vorSteuern", listOf(Fmt.euro(gross))))
    // Wiederanlegen gilt nur fuer das eigene Uebungsdepot (seit 5.92)
    if (LocalFremdesDepot.current == null && wiederanlageMoeglich(state)) {
        val scope = rememberCoroutineScope()
        ToggleRow(S.wlGlobal, settings.divWiederanlage, S.wlGlobalHinweis) { v -> scope.launch { state.settings.update { it.copy(divWiederanlage = v) } } }
    }
    val zone = ZoneId.systemDefault()
    val first = YearMonth.now()
    val months = (0 until 12).map { first.plusMonths(it.toLong()) }
    val sums = months.map { ym -> zahlungen.filter { YearMonth.from(Instant.ofEpochMilli(it.zahltag).atZone(zone)) == ym }.sumOf { it.netto } }
    BarChart(sums, months.map { monthLabel(it) }, MaterialTheme.colorScheme.onSurface, Modifier.fillMaxWidth().height(160.dp).padding(horizontal = 20.dp, vertical = 12.dp))
    KeyValueRow(if (fremd) S.ptDivPerMonthBrutto else S.ptDivPerMonth, Fmt.euro(net / 12.0))
    if (valuation.securities > 0) KeyValueRow(S.ptDivYield, Fmt.pct(gross / valuation.securities * 100.0))
    val invested = valuation.positions.filter { !it.position.isDerivative }.sumOf { it.invested }
    if (invested > 0) KeyValueRow(S.ptDivYieldOnCost, Fmt.pct(gross / invested * 100.0))
    if (received > 0) KeyValueRow(S.ptDivReceived, Fmt.euro(received))

    SectionHeader(S.ptDivNext)
    var alleZahlungen by remember { mutableStateOf(false) }
    (if (alleZahlungen) zahlungen else zahlungen.take(6)).forEachIndexed { zeile, e ->
        if (zeile > 0) Trennlinie()
        NamensZeile(e.name, Fmt.euro(e.netto), hint = Fmt.dayMonth(e.zahltag))
    }
    if (zahlungen.size > 6) ListenPfeil(alleZahlungen, label = if (alleZahlungen) S.less else "${S.more} (${zahlungen.size - 6})") { alleZahlungen = !alleZahlungen }

    SectionHeader(S.ptDivTop)
    val byKey = zahlungen.groupBy { it.key }.map { (k, v) -> Triple(k, v.first().name, v) }.sortedByDescending { t -> t.third.sumOf { it.netto } }
    byKey.forEach { (k, n, v) ->
        val sum = v.sumOf { it.netto }
        val symbol = holdings.firstOrNull { it.key == k }?.position?.symbol ?: k
        ShareBar("$n (${S.ptDivTimes(v.size)})", Fmt.euro(sum), if (net > 0) sum / net else 0.0, positionsFarbe(k)) { onOpen(symbol) }
    }
    // Dividendentermine aller Werte stehen bei den Bewegungen der Maerkte
    if (!fremd) NavRow(S.nkDivTitel, S.dp("div.termineUnter")) { onOpen(SEITEN_PRAEFIX + "discover/dividends") }
    HinweisInfo(if (fremd) S.ptDivNoteBrutto else S.ptDivNote, kurz = hinweisText("annahmen"))
}

// Streuung

@Composable
private fun DiversificationTool(state: AppState, onOpen: (String) -> Unit, onUmschichten: () -> Unit = {}) {
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
    NamenWertZeile(S.largestPosition, positionsKandidaten(largest.position.symbol, largest.position.name), Fmt.pct(c.largest * 100.0, 1))
    // Anteil der groessten Positionen als Balken
    if (items.size > 3) ShareBar(S.ptTop3, Fmt.pct(c.top3 * 100.0, 1), c.top3, AnteilsFarben.folge(0))
    KeyValueRow(S.ptEffective, S.ptEffectiveValue(Fmt.num(c.effective, 1)))
    // [0.72] Klumpenrisiko: Anteil der zehn groessten Positionen und Herfindahl-Index
    if (items.size > 10) ShareBar(S.ptTop10, Fmt.pct(c.top10 * 100.0, 1), c.top10, AnteilsFarben.folge(0))
    InfoWertZeile(S.ptHhi, Fmt.num(c.hhi, 0), S.ptHhi, S.ptHhiHint, AnteilsFarben.herfindahl(c.hhi))

    fun industryOf(vp: ValuedPosition): String = brancheVon(vp)

    // Anteile je Gruppe als Balken; hinter den ersten Gruppen steht „Andere“, mit dem Pfeil klappt die ganze Liste auf
    @Composable
    fun groups(title: String, label: (ValuedPosition) -> String, color: Color, limit: Int = 8) {
        SectionHeader(title)
        var alle by remember(title) { mutableStateOf(false) }
        val g = items.groupBy(label).map { (k, v) -> k to v.sumOf { it.value } }.sortedByDescending { it.second }
        val gekuerzt = g.size > limit + 1
        val shown = if (gekuerzt && !alle) g.take(limit) + (S.ptOthers to g.drop(limit).sumOf { it.second }) else g
        shown.forEachIndexed { i, (k, v) -> ShareBar(k, Fmt.pct(v / total * 100.0, 1), v / total, if (k == S.ptOthers && !alle) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else AnteilsFarben.folge(i)) }
        if (gekuerzt) ListenPfeil(alle, label = if (alle) S.less else "${S.more} (${g.size - limit})") { alle = !alle }
    }

    // Regionen wie im Globus „Mein Depot“ (0.78): Aktien nach Taetigkeitsland samt eigener Laenderwahl, ETFs nach den
    // Laendern ihres Index. Anteile am zugeordneten Teil des Depots.
    val fremdesDepot = LocalFremdesDepot.current != null
    val laenderSchluessel = items.sortedByDescending { it.value }.joinToString("|") { "${it.position.symbol}:${it.value.toLong()}" }
    var laender by remember { mutableStateOf<LaenderBilanz?>(null) }
    val herkunftWahl = state.account.collectAsStateWithLifecycle().value.herkunftWahl
    LaunchedEffect(laenderSchluessel, herkunftWahl) {
        laender = null
        val herkunft = de.troetenrepublik.app.market.ohneAbbruch { state.depotHerkunft(items) }.getOrNull() ?: emptyMap()
        val etf = de.troetenrepublik.app.market.ohneAbbruch { state.etfLaender(items) }.getOrNull()
        val imf = de.troetenrepublik.app.market.ohneAbbruch { state.imfDaten() }.getOrNull()
        val karte = de.troetenrepublik.app.market.ohneAbbruch { state.globusKarte() }.getOrNull()
        laender = withContext(Dispatchers.Default) {
            val sitz = katalogLaender(catalog)
            val anteile = de.troetenrepublik.app.engine.DepotStreuung.anteile(
                items.map { Triple(it.position.symbol, it.value, etf?.first?.containsKey(it.position.symbol) == true || Symbols.typeOf(it.position.symbol) == AssetType.ETF) },
                { s -> herkunft[s]?.land ?: sitz[s] },
                if (etf != null) { s -> etf.first[s] } else null,
            )
            val bilanz = de.troetenrepublik.app.engine.DepotStreuung.bilanz(anteile, etf?.second.orEmpty(), de.troetenrepublik.app.engine.Regionen.aktuell)
            val jahr = java.time.LocalDate.now().year
            val deutsch = de.troetenrepublik.app.i18n.I18n.locale.language == "de"
            // Anteil am Welt-BIP in laufenden Dollar: BIP des Landes durch die Summe aller Laender des IWF-Datensatzes
            val bipAnteil = imf?.let { de.troetenrepublik.app.engine.DepotStreuung.bipAnteile(it, jahr) }.orEmpty()
            // Depotanteil und BIP-Anteil je Land; a2 aus der Streuung, a3 fuer die IWF-Reihe. Laender ohne IWF-Wert
            // stehen gesammelt in der Restzeile.
            var ohneBip = 0.0
            val zeilen = anteile.nachLand.entries.mapNotNull { (a2, anteil) ->
                val land = karte?.nachA2(a2)
                val bip = land?.let { bipAnteil[it.a3] }
                if (land == null || bip == null) { ohneBip += anteil; return@mapNotNull null }
                LandZeile(land.name(deutsch), anteil, bip)
            }.sortedByDescending { it.depot }
            LaenderBilanz(
                zeilen, anteile.unzuordenbar, ohneBip,
                bilanz?.regionen?.entries?.sortedByDescending { it.value }?.map { it.key to it.value }.orEmpty(),
            )
        }
    }
    val lb = laender
    SectionHeader(S.regions)
    if (lb == null) ToolNote(S.toolLoading)
    else if (lb.regionen.isEmpty()) ToolNote(S.toolNoData)
    else {
        lb.regionen.forEach { (r, a) -> ShareBar(S.globusBilanzRegion(r), Fmt.pct(a * 100.0, 1), a, AnteilsFarben.region(r)) }
        if (lb.ohneLand > 0.005) ToolNote(S.globusBilanzZugeordnet(Fmt.pct((1 - lb.ohneLand) * 100, 0)))
    }
    groups(S.industries, { industryOf(it) }, MaterialTheme.colorScheme.onSurface)
    // Waehrungen: Fonds mit Laenderverteilung nach der Waehrung ihrer Laender aufgeloest, sonst Handelswaehrung
    val depotSchluessel = items.joinToString("|") { "${it.position.symbol}:${it.value.toLong()}" }
    val etfVerteilung by produceState<Map<String, Map<String, Double>>?>(null, depotSchluessel) {
        value = de.troetenrepublik.app.market.ohneAbbruch { state.etfLaender(items) }.getOrNull()?.first
    }
    SectionHeader(S.ptCurrencies)
    val waehrungen = remember(depotSchluessel, etfVerteilung, quotes) {
        de.troetenrepublik.app.engine.DepotStreuung.waehrungen(
            items.map { vp ->
                Triple(vp.position.symbol, vp.value, (quotes[vp.position.symbol]?.nativeCurrency?.takeIf { it.isNotBlank() } ?: Symbols.find(vp.position.symbol)?.currency ?: "EUR").let { de.troetenrepublik.app.engine.ExchangeRates.basisWaehrung(it) ?: "EUR" })
            },
        ) { s -> etfVerteilung?.get(s) }.entries.sortedByDescending { it.value }
    }
    var alleWaehrungen by remember { mutableStateOf(false) }
    val wGekuerzt = waehrungen.size > 9
    val wGezeigt = if (wGekuerzt && !alleWaehrungen) waehrungen.take(8).map { it.key to it.value } + (S.ptOthers to waehrungen.drop(8).sumOf { it.value }) else waehrungen.map { it.key to it.value }
    wGezeigt.forEachIndexed { i, (k, v) -> ShareBar(k, Fmt.pct(v / total * 100.0, 1), v / total, if (k == S.ptOthers && !alleWaehrungen) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else AnteilsFarben.folge(i)) }
    if (wGekuerzt) ListenPfeil(alleWaehrungen, label = if (alleWaehrungen) S.less else "${S.more} (${waehrungen.size - 8})") { alleWaehrungen = !alleWaehrungen }

    // [0.72] Laender des Depots (mit ETF-Blick, wie der Globus) gegen den Anteil am Welt-BIP nach Zahlen des IWF,
    // eingeklappt; die Karte dazu steht im Globus
    AufklappAbschnitt("streuung-laender-bip", S.ptCountriesBip, S.ptCountriesBipNote) {
        if (lb == null) {
            ToolNote(S.toolLoading)
        } else if (lb.zeilen.isEmpty()) {
            ToolNote(S.toolNoData)
        } else {
            // Anteile im Massstab 0 bis 100 %, nicht im Verhaeltnis zum groessten Land
            val skala = 1.0
            var alleLaender by remember { mutableStateOf(false) }
            (if (alleLaender) lb.zeilen else lb.zeilen.take(12)).forEach { z -> LandGegenBip(z, skala) }
            val weitere = lb.zeilen.drop(12).sumOf { it.depot }
            if (!alleLaender && weitere > 0.005) KeyValueRow(S.ptOthers, Fmt.pct(weitere * 100.0, 1))
            if (lb.zeilen.size > 12) ListenPfeil(alleLaender, label = if (alleLaender) S.less else "${S.more} (${lb.zeilen.size - 12})") { alleLaender = !alleLaender }
            if (lb.ohneBip > 0.005) KeyValueRow(S.ptOhneBip, Fmt.pct(lb.ohneBip * 100.0, 1))
            if (lb.ohneLand > 0.005) KeyValueRow(S.ptOhneLand, Fmt.pct(lb.ohneLand * 100.0, 1))
        }
    }
    if (!fremdesDepot) NavRow(S.dp("st.globus"), S.dp("st.globusUnter")) { onOpen(SEITEN_PRAEFIX + "globus/depot") }

    // Laufende Kosten der ETFs aus derselben Quelle und Rechnung wie die Faustregel im Depot-Check (0.78)
    val etfPosten = items.filter { !it.position.isDerivative && Symbols.typeOf(it.position.symbol) == AssetType.ETF }
    val etfSymbole = etfPosten.map { it.position.symbol }.distinct()
    val ter by produceState(emptyMap<String, Double>(), etfSymbole) { value = etfKosten(state, etfSymbole) }
    de.troetenrepublik.app.engine.DepotCheck.fondskosten(etfPosten.mapNotNull { vp -> ter[vp.position.symbol]?.let { vp.value to it } })?.let { f ->
        KeyValueRow(S.ptEtfCosts, Fmt.pct(f.ter, 2))
        KeyValueRow(S.dp("st.etfEuro"), Fmt.euro(f.kosten, 0))
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
            HinweisInfo(S.ptCorrelationNote, kurz = hinweisText("gleichlauf"))
        }
    }
    // [0.73] Rendite und Schwankung der Positionen im Streudiagramm
    RenditeRisikoStreuung(state, items)
    // Anteil jeder Position an der Schwankung des Depots (seit 5.91)
    RisikobeitragTeil(state, items, onUmschichten)
    HinweisInfo(S.ptDiversificationNote)
}

// Branche einer Position fuer die Streuung und das Diversifizieren.
private fun brancheVon(vp: ValuedPosition): String {
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

// Auswahl beim Diversifizieren im Umschichten.
private enum class DivArt { GLEICH, RISIKO, LAENDER, BRANCHEN, MAX5, MAX10, MAX20 }

private fun divName(a: DivArt): String = when (a) {
    DivArt.GLEICH -> S.divGleich
    DivArt.RISIKO -> S.divRisiko
    DivArt.LAENDER -> S.divLaenderBip
    DivArt.BRANCHEN -> S.divBranchen
    DivArt.MAX5 -> S.divHoechstens(5)
    DivArt.MAX10 -> S.divHoechstens(10)
    DivArt.MAX20 -> S.divHoechstens(20)
}

// Laenderverteilung je Positionsschluessel (alpha-2 zu Anteil) wie in der Streuung: Aktien nach Herkunft oder Katalog,
// Fonds nach ihrer Laenderverteilung.
private suspend fun divLaender(state: AppState, items: List<ValuedPosition>): Map<String, Map<String, Double>> {
    val catalog = Catalog.get()
    val herkunft = de.troetenrepublik.app.market.ohneAbbruch { state.depotHerkunft(items) }.getOrNull() ?: emptyMap()
    val etf = de.troetenrepublik.app.market.ohneAbbruch { state.etfLaender(items) }.getOrNull()
    return items.associate { vp ->
        val sym = vp.position.symbol
        val verteilung = etf?.first?.get(sym)
        vp.key to when {
            verteilung != null -> verteilung
            Symbols.typeOf(sym) == AssetType.ETF -> emptyMap()
            else -> (herkunft[sym]?.land ?: catalog.aktien.firstOrNull { it.s == sym }?.c?.takeIf { c -> c.isNotBlank() })
                ?.let { mapOf(it to 1.0) } ?: emptyMap()
        }
    }
}

// Welt-BIP-Anteil je Land (alpha-2) fuer die gegebenen Laender; Laender ohne IWF-Wert fehlen.
private suspend fun divBip(state: AppState, laender: Set<String>): Map<String, Double> {
    val imf = de.troetenrepublik.app.market.ohneAbbruch { state.imfDaten() }.getOrNull() ?: return emptyMap()
    val karte = de.troetenrepublik.app.market.ohneAbbruch { state.globusKarte() }.getOrNull() ?: return emptyMap()
    val anteil = de.troetenrepublik.app.engine.DepotStreuung.bipAnteile(imf, java.time.LocalDate.now().year)
    return laender.mapNotNull { a2 -> karte.nachA2(a2)?.let { l -> anteil[l.a3]?.let { a2 to it } } }.toMap()
}

// Umschichten

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RebalanceTool(state: AppState, onOpen: (String) -> Unit) {
    val valuation = depotBewertung(state)
    val signal = LocalSignal.current
    val items = valuation.positions.filter { !it.position.isDerivative && it.value > 0 }.sortedByDescending { it.value }
    if (items.size < 2) { ToolNote(S.ptNeedTwo); return }
    // [0.75] Zielgewichte je Positionsschluessel, gemerkt fuer die Menge der Positionen: Aendert sich nur die
    // Reihenfolge nach Wert (Kurse laufen weiter), bleiben die eingestellten Gewichte stehen.
    val keys = PortfolioTools.positionsMenge(items.map { it.key })
    val total = items.sumOf { it.value }
    // [0.76] Gespeicherte Zielgewichte und Kategorien
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val kstand = remember(settings.kategorien) { Kategorien.lesen(settings.kategorien) }
    val weightMap = remember(keys, kstand.positionsziele.isNotEmpty()) {
        mutableStateMapOf<String, Float>().apply {
            Kategorien.startGewichte(kstand, items.map { it.key to it.value }).forEach { (k, v) -> put(k, v.toFloat()) }
        }
    }
    val weights = items.map { weightMap[it.key] ?: 0f }
    // Ziele gelten als gesetzt, sobald sie gespeichert sind oder hier geaendert wurden; vorher stehen sie auf dem heutigen Stand
    var eigeneZiele by rememberSaveable { mutableStateOf(false) }
    val zieleGesetzt = eigeneZiele || kstand.positionsziele.isNotEmpty()
    // Vorschlag mit gleichem Risikoanteil aus der Streuung einmal uebernehmen
    LaunchedEffect(keys, risikoVorschlag) {
        val v = risikoVorschlag ?: return@LaunchedEffect
        items.forEach { vp -> v[vp.key]?.let { weightMap[vp.key] = (it * 100.0).toFloat() } }
        eigeneZiele = true
        risikoVorschlag = null
    }
    var sell by rememberSaveable { mutableStateOf(true) }
    var money by rememberSaveable { mutableDoubleStateOf(1_000.0) }
    // Bargeld und Steuer gibt es nur im eigenen Depot; ein fremdes (Person) hat hier weder Guthaben noch Einstand
    val fremd = LocalFremdesDepot.current != null
    val cash = if (fremd) 0.0 else state.withdrawable()

    var jeKat by rememberSaveable { mutableStateOf(false) }
    ToolFelder(null, listOf(S.katJeWert to false, S.katJeKategorie to true), jeKat, spalten = 2) { jeKat = it }
    ToolFelder(null, listOf(S.ptModeRebalance to true, S.ptModeBuyOnly to false), sell, spalten = 2) { sell = it }
    // Schwelle: erst umschichten, wenn ein Baustein weiter vom Ziel liegt (Umschichtschwelle)
    var schwelle by rememberSaveable { mutableStateOf(false) }
    if (sell) ToolFelder(null, listOf(S.umJede to false, S.umAbSchwelle to true), schwelle, spalten = 2) { schwelle = it }
    val mitSchwelle = sell && schwelle
    if (!sell) {
        val options = listOf(500.0, 1_000.0, 2_500.0, 5_000.0, 10_000.0).map { Fmt.euro(it, 0) to it } +
            (if (cash >= 1.0) listOf(S.ptCash(Fmt.compactEuro(cash)) to roundTo(cash, 0.01)) else emptyList())
        ToolChoice(S.ptNewMoney, options, money) { money = it }
    }
    if (jeKat) { RebalanceKategorien(state, items, kstand, sell, if (sell) 0.0 else money, fremd, mitSchwelle, onOpen); return }
    val bausteine = if (mitSchwelle) Umschichtschwelle.anteile(items.map { it.value }, weights.map { it.toDouble() }) else emptyList()
    val rahmen = Umschichtschwelle.imRahmen(bausteine)
    val noetig = Umschichtschwelle.noetig(bausteine)
    var blatt by remember { mutableStateOf<String?>(null) }
    if (!zieleGesetzt) KernKeinZiel { blatt = items.first().key }
    else if (mitSchwelle) SchwellenKern(noetig)
    val scope = rememberCoroutineScope()
    var divOffen by remember { mutableStateOf(false) }
    var divStatus by remember { mutableStateOf<String?>(null) }
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Chip(S.ptEqualWeights, false) { divStatus = null; eigeneZiele = true; items.forEach { weightMap[it.key] = (100.0 / items.size).toFloat() } }
        Chip(S.ptCurrentWeights, false) { divStatus = null; eigeneZiele = true; items.forEach { vp -> weightMap[vp.key] = (vp.value / total * 100.0).toFloat() } }
        Chip(S.divTitel, false) { divOffen = true }
        Chip(S.ptSaveTargets, false) {
            divStatus = null
            eigeneZiele = true
            val ziele = items.associate { it.key to (weightMap[it.key] ?: 0f).toDouble() }
            scope.launch { state.settings.update { it.copy(kategorien = Kategorien.schreiben(Kategorien.zieleSpeichern(Kategorien.lesen(it.kategorien), ziele))) } }
        }
        if (kstand.positionsziele.isNotEmpty()) Chip(S.ptSavedTargets, false) {
            divStatus = null
            eigeneZiele = true
            Kategorien.startGewichte(kstand, items.map { it.key to it.value }).forEach { (k, v) -> weightMap[k] = v.toFloat() }
        }
    }
    divStatus?.let { ToolNote(it) }
    if (divOffen) AlertDialog(
        onDismissRequest = { divOffen = false },
        title = { Text(S.divTitel) },
        text = {
            Column {
                DivArt.entries.forEach { art ->
                    Text(
                        divName(art), style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable {
                            divOffen = false
                            scope.launch {
                                divStatus = S.divLaedt
                                val werte = items.map { Diversifizieren.Wert(it.key, it.value, branche = brancheVon(it)) }
                                val ziel: Map<String, Double>? = when (art) {
                                    DivArt.GLEICH -> Diversifizieren.gleich(werte)
                                    DivArt.BRANCHEN -> Diversifizieren.branchen(werte)
                                    DivArt.MAX5 -> Diversifizieren.hoechstens(werte, 0.05)
                                    DivArt.MAX10 -> Diversifizieren.hoechstens(werte, 0.10)
                                    DivArt.MAX20 -> Diversifizieren.hoechstens(werte, 0.20)
                                    DivArt.RISIKO -> de.troetenrepublik.app.market.ohneAbbruch { risikoGleichgewicht(state, items) }.getOrNull()
                                    DivArt.LAENDER -> {
                                        val land = divLaender(state, items)
                                        val bip = divBip(state, land.values.flatMap { it.keys }.toSet())
                                        withContext(Dispatchers.Default) {
                                            Diversifizieren.laenderBip(items.map { Diversifizieren.Wert(it.key, it.value, land[it.key].orEmpty()) }, bip)
                                        }
                                    }
                                }
                                if (ziel == null || ziel.isEmpty()) divStatus = S.divKeineDaten
                                else {
                                    items.forEach { weightMap[it.key] = ((ziel[it.key] ?: 0.0) * 100.0).toFloat() }
                                    eigeneZiele = true
                                    divStatus = null
                                }
                            }
                        }.padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { divOffen = false }) { Text(S.cancel) } },
    )
    val newMoney = if (sell) 0.0 else money
    val trades = PortfolioTools.rebalance(items.map { it.key to it.value }, weights.map { it.toDouble() }, newMoney, sell)
        .let { if (mitSchwelle) Umschichtschwelle.orders(it, noetig) else it }
    // Beim Umschichten gelten Reste unter 0,1 % des Depots (mindestens 1 EUR) als passend: die Gewichte stammen vom
    // Oeffnen, die Kurse laufen weiter. Beim reinen Kaufen wird nur das Neugeld verteilt, dort zaehlt jeder Euro.
    val klein = if (sell) max(1.0, total * 0.001) else 1.0
    val sumW = weights.sum().takeIf { it > 0f } ?: 1f
    SectionHeader(S.ptTargetWeights)
    items.forEachIndexed { i, vp ->
        val t = trades[i]
        val now = vp.value / total * 100.0
        val goal = weights[i] / sumW * 100.0
        // Innerhalb der Schwelle: gedaempft, mit Vermerk
        val imRahmen = mitSchwelle && rahmen.getOrNull(i) == true
        val auftrag = abs(t.amount) >= klein
        val gedaempft = MaterialTheme.colorScheme.onSurfaceVariant
        val (text, color) = when {
            !zieleGesetzt -> S.umKeinZiel to gedaempft
            t.amount >= klein -> S.ptBuyAmount(Fmt.euro(t.amount, 0)) to if (imRahmen) gedaempft else signal.up
            t.amount <= -klein -> S.ptSellAmount(Fmt.euro(-t.amount, 0)) to if (imRahmen) gedaempft else signal.down
            imRahmen -> S.umRahmen to gedaempft
            else -> S.ptOnTarget to gedaempft
        }
        if (i > 0) Trennlinie()
        ZielZeile(
            kopf = { m -> Column(m) {
                NamenText(vp.position.name, style = MaterialTheme.typography.titleSmall)
                if (zieleGesetzt && imRahmen && auftrag) Text(S.umRahmen, style = MaterialTheme.typography.bodySmall, color = gedaempft)
            } },
            logo = { InstrumentLogo(vp.position.symbol, vp.position.name, size = 32) },
            rechts = text, rechtsFarbe = color, ist = now, ziel = if (zieleGesetzt) goal else null,
            balkenFarbe = positionsFarbe(vp.key), onClick = { blatt = vp.key },
        )
    }
    if (zieleGesetzt) {
        val summe = weights.sumOf { it.toDouble() }
        KeyValueRow(S.katSumme, Fmt.pct(summe, 1), valueColor = if (abs(summe - 100.0) < 0.15) signal.up else signal.warn)
    }
    blatt?.let { k ->
        val vp = items.first { it.key == k }
        val a = vp.value / total * 100.0
        ZielBlatt(
            vp.position.name, a, if (zieleGesetzt) weights[items.indexOf(vp)] / sumW * 100.0 else null,
            onSet = { v ->
                val neu = Kategorien.verteilen(items.associate { it.key to (weightMap[it.key] ?: 0f) / sumW * 100.0 }, k, v)
                neu.forEach { (key, z) -> weightMap[key] = z.toFloat() }
                eigeneZiele = true
            },
            onDismiss = { blatt = null },
        )
    }
    if (zieleGesetzt) {
        RebalanceSumme(state, items, trades, klein, weights.map { it.toDouble() }, fremd)
        StuecklisteUmschichten(items, trades, klein, onOpen)
    }
}

// Kernaussage ohne gesetzte Ziele; ein Tipp oeffnet das Eingabeblatt der ersten Zeile.
@Composable
private fun KernKeinZiel(onClick: () -> Unit) {
    Text(
        S.umNochKeine, style = MaterialTheme.typography.titleMedium, color = LocalSignal.current.link,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 4.dp),
    )
}

// Kernzeile zur Schwelle: ob ein Baustein ausserhalb liegt.
@Composable
private fun SchwellenKern(noetig: Boolean) {
    val signal = LocalSignal.current
    Text(
        if (noetig) S.umNoetig else S.umAllesImRahmen, style = MaterialTheme.typography.titleMedium, color = if (noetig) signal.warn else signal.up,
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 4.dp),
    )
}

// [0.76] Umschichten je Kategorie: Ist und Ziel je Kategorie, Orders je Position, darunter das Bearbeiten.
@Composable
private fun RebalanceKategorien(state: AppState, items: List<ValuedPosition>, gespeichert: KategorienStand, sell: Boolean, neuesGeld: Double, fremd: Boolean, mitSchwelle: Boolean, onOpen: (String) -> Unit) {
    val signal = LocalSignal.current
    val scope = rememberCoroutineScope()
    val total = items.sumOf { it.value }
    // Ohne gespeicherten Stand gilt der Startvorschlag nach Anlageklasse und wird beim ersten Oeffnen gespeichert.
    val vorschlag = remember(items.map { it.key }) { Kategorien.vorschlag(items.map { Kategorien.Wert(it.key, it.value, S.assetClass(classOf(it))) }) }
    val stand = if (gespeichert.vorgeschlagen) gespeichert else vorschlag
    LaunchedEffect(gespeichert.vorgeschlagen) {
        if (!gespeichert.vorgeschlagen) state.settings.update { s ->
            val alt = Kategorien.lesen(s.kategorien)
            if (alt.vorgeschlagen) s else s.copy(kategorien = Kategorien.schreiben(vorschlag.copy(positionsziele = alt.positionsziele)))
        }
    }
    var bearbeiten by rememberSaveable { mutableStateOf(false) }
    val aendern: ((KategorienStand) -> KategorienStand) -> Unit = { f ->
        scope.launch {
            state.settings.update { s ->
                val alt = Kategorien.lesen(s.kategorien)
                s.copy(kategorien = Kategorien.schreiben(f(if (alt.vorgeschlagen) alt else vorschlag.copy(positionsziele = alt.positionsziele))))
            }
        }
    }
    val plan = Kategorien.plan(stand, items.map { it.key to it.value }, neuesGeld, sell, S.katOhne)
    // Schwelle: Bausteine sind die Kategorien mit Ziel
    val noetig = !mitSchwelle || Umschichtschwelle.noetig(plan.zeilen.filter { it.id != null }.map { it.ist to it.ziel })
    val trades = if (mitSchwelle) Umschichtschwelle.orders(plan.orders, noetig) else plan.orders
    val klein = if (sell) max(1.0, total * 0.001) else 1.0
    val zieleGesetzt = stand.zieleGesetzt
    var blatt by remember { mutableStateOf<String?>(null) }
    val ersteId = plan.zeilen.firstOrNull { it.id != null }?.id
    if (!zieleGesetzt) { if (ersteId != null) KernKeinZiel { blatt = ersteId } else Text(S.umNochKeine, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 4.dp)) }
    else if (mitSchwelle) SchwellenKern(noetig)
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp)) {
        Chip(S.katBearbeiten, bearbeiten) { bearbeiten = !bearbeiten }
    }
    SectionHeader(S.katTitel)
    plan.zeilen.forEachIndexed { zi, z ->
        // Vorzeichen in Handelsrichtung wie bei den Orders: zu viel = Minus (verkaufen, rot), zu wenig = Plus (kaufen, gruen)
        val euro = -z.abweichungEuro
        val imRahmen = mitSchwelle && z.id != null && Umschichtschwelle.imRahmen(z.ist, z.ziel)
        val farbe = when {
            z.id == null || imRahmen || abs(euro) < klein -> MaterialTheme.colorScheme.onSurfaceVariant
            euro > 0 -> signal.up
            else -> signal.down
        }
        val rechts = when {
            !zieleGesetzt || z.id == null -> S.umKeinZiel
            imRahmen -> S.umRahmen
            // Abstand zum Ziel in Euro, in Handelsrichtung; der Anteil steht darunter als Ist und Ziel
            else -> (if (euro >= 1) "+" else if (euro <= -1) "−" else "") + Fmt.euro(abs(euro), 0)
        }
        if (zi > 0) Trennlinie()
        ZielZeile(
            kopf = { m -> Text(z.name.ifBlank { "–" }, style = MaterialTheme.typography.titleSmall, modifier = m) },
            logo = null, rechts = rechts, rechtsFarbe = if (zieleGesetzt) farbe else MaterialTheme.colorScheme.onSurfaceVariant,
            ist = z.ist * 100.0, ziel = if (zieleGesetzt && z.id != null) z.ziel * 100.0 else null,
            balkenFarbe = AnteilsFarben.folge(zi), onClick = { if (z.id != null) blatt = z.id },
        )
    }
    if (zieleGesetzt) KeyValueRow(S.katSumme, Fmt.pct(Kategorien.summeZiele(stand), 1), valueColor = if (abs(Kategorien.summeZiele(stand) - 100.0) < 0.15) signal.up else signal.warn)
    blatt?.let { id ->
        plan.zeilen.firstOrNull { it.id == id }?.let { z ->
            ZielBlatt(
                z.name.ifBlank { "–" }, z.ist * 100.0, if (zieleGesetzt) stand.kategorien.firstOrNull { it.id == id }?.ziel else null,
                onSet = { v -> aendern { s -> Kategorien.zielVerteilen(s, id, v) } },
                onDismiss = { blatt = null },
            )
        }
    }
    SectionHeader(S.katPositionen)
    items.forEachIndexed { i, vp ->
        val t = trades[i]
        val (text, color) = when {
            !zieleGesetzt -> S.umKeinZiel to MaterialTheme.colorScheme.onSurfaceVariant
            t.amount >= klein -> S.ptBuyAmount(Fmt.euro(t.amount, 0)) to signal.up
            t.amount <= -klein -> S.ptSellAmount(Fmt.euro(-t.amount, 0)) to signal.down
            else -> S.ptOnTarget to MaterialTheme.colorScheme.onSurfaceVariant
        }
        if (i > 0) Trennlinie()
        Row(
            Modifier.fillMaxWidth().clickable { onOpen(vp.position.symbol) }.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InstrumentLogo(vp.position.symbol, vp.position.name, size = 32)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                NamenText(vp.position.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    stand.kategorien.firstOrNull { it.id == stand.zuordnung[vp.key] }?.name?.ifBlank { null } ?: S.katOhne,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.titleSmall, color = color, textAlign = TextAlign.End, maxLines = 1, softWrap = false)
        }
    }
    if (zieleGesetzt) {
        RebalanceSumme(state, items, trades, klein, trades.map { it.target }, fremd)
        StuecklisteUmschichten(items, trades, klein, onOpen)
    }
    if (bearbeiten) KategorienEditor(stand, items, aendern)
}

// Kategorien anlegen, umbenennen, Ziel aendern, loeschen und Positionen zuordnen.
@Composable
private fun KategorienEditor(stand: KategorienStand, items: List<ValuedPosition>, aendern: ((KategorienStand) -> KategorienStand) -> Unit) {
    SectionHeader(S.katBearbeiten)
    stand.kategorien.forEach { k ->
        var name by remember(k.id) { mutableStateOf(k.name) }
        var ziel by remember(k.id) { mutableStateOf(zahlText(k.ziel)) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = name, onValueChange = { v -> name = v.take(40); aendern { s -> Kategorien.umbenennen(s, k.id, name) } },
                singleLine = true, label = { Text(S.katName) }, shape = RoundedCornerShape(Radius.field), modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = ziel,
                onValueChange = { v ->
                    ziel = v.filter { it.isDigit() || it == ',' || it == '.' }.take(6)
                    val z = ziel.replace(',', '.').toDoubleOrNull() ?: 0.0
                    aendern { s -> Kategorien.zielSetzen(s, k.id, z) }
                },
                singleLine = true, label = { Text(S.katZiel) }, shape = RoundedCornerShape(Radius.field), modifier = Modifier.width(110.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Filled.Close, S.delete, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.textKlick(12.dp) { aendern { s -> Kategorien.loeschen(s, k.id) } }.padding(horizontal = 12.dp, vertical = 8.dp).size(24.dp),
            )
        }
    }
    KeyValueRow(S.katSumme, Fmt.pct(Kategorien.summeZiele(stand), 1))
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        Chip(S.katNeu, false) { aendern { s -> Kategorien.neu(s, S.katEine + " " + (s.kategorien.size + 1), 0.0) } }
    }
    SectionHeader(S.katPositionen)
    items.forEach { vp ->
        var offen by remember { mutableStateOf(false) }
        val aktuell = stand.kategorien.firstOrNull { it.id == stand.zuordnung[vp.key] }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            NamenText(vp.position.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.widthIn(max = 160.dp)) {
                Text(
                    aktuell?.name?.ifBlank { null } ?: S.katOhne, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End,
                    modifier = Modifier.textKlick(0.dp) { offen = true }.padding(vertical = 4.dp),
                )
                DropdownMenu(expanded = offen, onDismissRequest = { offen = false }, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    stand.kategorien.forEach { k ->
                        DropdownMenuItem(text = { Text(k.name.ifBlank { "–" }) }, onClick = { offen = false; aendern { s -> Kategorien.zuordnen(s, vp.key, k.id) } })
                    }
                    DropdownMenuItem(text = { Text(S.katOhne) }, onClick = { offen = false; aendern { s -> Kategorien.zuordnen(s, vp.key, null) } })
                }
            }
        }
    }
}

private fun zahlText(z: Double): String = if (z == kotlin.math.floor(z)) z.toLong().toString() else z.toString().replace('.', Fmt.decimalSep)

// Summen unter den Orders: Kaeufe, Verkaeufe, Orders mit Gebuehren, Steuer, Abweichung vorher und nachher.
@Composable
private fun RebalanceSumme(state: AppState, items: List<ValuedPosition>, trades: List<PortfolioTools.Trade>, klein: Double, zielGewichte: List<Double>, fremd: Boolean) {
    val buys = trades.filter { it.amount >= klein }.sumOf { it.amount }
    val sells = -trades.filter { it.amount <= -klein }.sumOf { it.amount }
    val orders = trades.count { abs(it.amount) >= klein }
    val tax = if (fremd) 0.0 else trades.filter { it.amount <= -klein }.sumOf { t ->
        val vp = items.first { it.key == t.key }
        val qty = if (vp.price > 0) min(vp.qty, -t.amount / vp.price) else 0.0
        if (qty > 0) state.previewSell(t.key, qty)?.tax ?: 0.0 else 0.0
    }
    val before = PortfolioTools.deviation(items.map { it.value }, zielGewichte)
    val afterValues = items.mapIndexed { i, vp -> vp.value + (trades[i].amount.takeIf { abs(it) >= klein } ?: 0.0) }
    val after = PortfolioTools.deviation(afterValues, zielGewichte)
    Spacer(Modifier.height(8.dp))
    KeyValueRow(S.ptBuys, Fmt.euro(buys, 0))
    KeyValueRow(S.ptSells, Fmt.euro(sells, 0))
    KeyValueRow(S.ptOrdersFees(orders, Fmt.euro(orders * Trading.FEE, 0)), "")
    if (tax > 0) KeyValueRow(S.ptTaxEstimate, Fmt.euro(tax))
    KeyValueRow(S.ptDeviation, S.st("an.wechsel", listOf(Fmt.pct(before * 100.0, 1), Fmt.pct(after * 100.0, 1))))
}

// [0.72] Laender des Depots gegen ihren Anteil am Welt-BIP
private class LandZeile(val name: String, val depot: Double, val bip: Double)

// regionen: Anteile am zugeordneten Teil des Depots, groesste zuerst
private class LaenderBilanz(
    val zeilen: List<LandZeile>, val ohneLand: Double, val ohneBip: Double,
    val regionen: List<Pair<de.troetenrepublik.app.engine.DepotStreuung.Region, Double>> = emptyList(),
)

// Sitzland je Kuerzel aus dem Katalog, dieselben Listen wie beim Globus „Mein Depot“.
internal fun katalogLaender(k: Catalog.Data): Map<String, String> {
    val aus = HashMap<String, String>()
    k.aktien.forEach { if (it.c.isNotBlank()) aus[it.s] = it.c }
    k.basiswerte.forEach { if (it.c.isNotBlank()) aus.putIfAbsent(it.s, it.c) }
    k.ipos.abgeschlossen.forEach { if (it.c.isNotBlank()) aus.putIfAbsent(it.s, it.c) }
    return aus
}

// Laufende Kosten (TER in Prozent pro Jahr) je ETF: aus der Durchschau, sonst aus dem Katalog. Eine Quelle fuer die
// Faustregel im Depot-Check und die Zeile auf der Seite Streuung.
internal suspend fun etfKosten(state: AppState, symbole: List<String>): Map<String, Double> {
    val katalog = withContext(Dispatchers.IO) { Catalog.get() }
    return symbole.mapNotNull { s ->
        val ter = de.troetenrepublik.app.market.ohneAbbruch { state.etfBestand(s) }.getOrNull()?.ter ?: katalog.etfs.firstOrNull { it.s == s }?.ter
        ter?.let { s to it }
    }.toMap()
}

// Zeile mit einem Namen, der umbrechen darf, und einem Wert, der nie umbricht; hint steht klein unter dem Namen.
@Composable
private fun NamensZeile(name: String, wert: String, hint: String? = null, valueColor: Color? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (hint != null) Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Text(wert, style = MaterialTheme.typography.bodyMedium, color = valueColor ?: MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false)
    }
}

// Eine Zeile: Land, Balken fuer den Depotanteil, Strich beim BIP-Anteil, rechts der Abstand in Prozentpunkten.
@Composable
private fun LandGegenBip(z: LandZeile, skala: Double) {
    val signal = LocalSignal.current
    val bahn = MaterialTheme.colorScheme.surfaceContainerHigh
    val strich = MaterialTheme.colorScheme.onSurface
    // Name links und Abstand rechts in einer Zeile (der Name darf umbrechen, der Wert nie), darunter der Balken
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(z.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(punkteVorzeichen((z.depot - z.bip) * 100.0), style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false,
                color = if (z.depot >= z.bip) signal.up else signal.down, textAlign = TextAlign.End)
        }
        Canvas(Modifier.fillMaxWidth().padding(top = 4.dp).height(14.dp)) {
            val hoehe = 8.dp.toPx()
            val r = CornerRadius(hoehe / 2)
            drawRoundRect(bahn, Offset(0f, 2.dp.toPx()), Size(size.width, hoehe), r)
            val w = size.width * (z.depot / skala).toFloat().coerceIn(0f, 1f)
            if (w > 0f) drawRoundRect(signal.up.copy(alpha = 0.8f), Offset(0f, 2.dp.toPx()), Size(w, hoehe), r)
            val x = (size.width * (z.bip / skala)).toFloat().coerceIn(0f, size.width)
            drawLine(strich, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
        }
    }
}

// [0.72] Sparplan-Rueckblick: was dieselbe monatliche Rate in einer Anlage seit einem Jahr gebracht haette.
private val SPARPLAN_ANLAGEN = listOf(
    Bench("MSCI_WORLD", "EUNL.DE"), Bench("FTSE_ALL_WORLD", "VWCE.DE"), Bench("SP500", "SXR8.DE"),
    Bench("DAX", "EXS1.DE"), Bench("STOXX600", "XSX6.DE"), Bench("GOLD", "4GLD.DE"), Bench("BITCOIN", "BTC-EUR"),
)

@Composable
private fun SparplanTool(state: AppState) {
    val signal = LocalSignal.current
    var anlageKey by rememberSaveable { mutableStateOf("MSCI_WORLD") }
    var rate by rememberSaveable { mutableFloatStateOf(200f) }
    val heutesJahr = remember { java.time.LocalDate.now().year }
    val jahre = remember(heutesJahr) { listOf(heutesJahr - 10, heutesJahr - 8, heutesJahr - 5, heutesJahr - 3, heutesJahr - 1).distinct() }
    var abJahr by rememberSaveable { mutableIntStateOf(jahre.first()) }

    // Ergebnis steht oben, die Einstellungen darunter
    val einstellungen: @Composable () -> Unit = {
        ToolChoice(S.ptSparAnlage, SPARPLAN_ANLAGEN.map { S.ptBenchName(it.key) to it.key }, anlageKey) { anlageKey = it }
        ToolChoice(S.ptSparSeit, jahre.map { "$it" to it }, abJahr) { abJahr = it }
        SliderRow(S.ptMonthlySaving, Fmt.euro(rate.toDouble(), 0), rate, 25f..2000f, 25f) { rate = it }
    }

    var ergebnis by remember { mutableStateOf<Analysis.PlanMitErtraegen?>(null) }
    var leer by remember { mutableStateOf(false) }
    // Kurse und Ausschuettungen der Anlage fuer die Abschnitte zum Einstieg
    var reihe by remember { mutableStateOf<Pair<List<Candle>, List<de.troetenrepublik.app.market.MarketApi.DividendEvent>>?>(null) }
    val einstieg = rememberEinstiegWahl()
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val anlage = SPARPLAN_ANLAGEN.first { it.key == anlageKey }
    LaunchedEffect(anlageKey, abJahr, rate) {
        ergebnis = null; leer = false; reihe = null
        val symbol = anlage.symbol ?: return@LaunchedEffect
        // Tageskurse und Ausschuettungen wie im Vergleich je Wertpapier
        val (daily, divs) = coroutineScope {
            val k = async { state.dailyCloses(symbol) }
            val d = async { de.troetenrepublik.app.market.ohneAbbruch { state.market.dividendHistory(symbol) }.getOrNull() ?: emptyList() }
            k.await() to d.await()
        }
        if (daily.isEmpty()) { leer = true; return@LaunchedEffect }
        reihe = daily to divs
        val price = state.quotes.value[symbol]?.price?.takeIf { it > 0 && it.isFinite() }
        val ab = java.time.LocalDate.of(abJahr, 1, 1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        ergebnis = withContext(Dispatchers.Default) { Analysis.sparplanMitErtraegen(daily, divs, rate.toDouble(), ab, price) }
        if (ergebnis == null) leer = true
    }
    val e = ergebnis ?: run { einstellungen(); ToolNote(if (leer) S.ptSparKeineDaten else S.toolLoading); return }
    val r = e.plan
    val name = S.ptBenchName(anlageKey)
    // Beginnt die Kursreihe spaeter als gewaehlt, steht hier der tatsaechliche Beginn
    val beginn = YearMonth.from(Instant.ofEpochMilli(e.beginn).atZone(java.time.ZoneId.systemDefault()))
    Headline(S.ptSparValue, Fmt.euro(r.value, 0), S.ptSparMonths(r.executions, name, Fmt.monthYear(beginn)))
    einstellungen()
    KeyValueRow(S.ptInvested, Fmt.euro(r.invested, 0))
    KeyValueRow(S.ptGrowth, Fmt.signedEuro(r.gain, 0), valueColor = signal.forChange(r.gain))
    // Geldgewichtete Rendite aller Monatsraten bis heute, ab einem Jahr
    r.perYear?.let { KeyValueRow(S.ptSparIrr, signedPct(it)) }
    KeyValueRow(S.ptSparLump, Fmt.euro(e.einmal, 0), hint = S.ptSparLumpHint)
    if (e.einmal > 0 && r.value > 0) Text(
        if (r.value >= e.einmal) S.ptSparLumpSparplan(Fmt.signedEuro(r.value - e.einmal, 0)) else S.ptSparLumpEinmal(Fmt.signedEuro(e.einmal - r.value, 0)),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
    )
    VergleichsChart(
        linien = listOf(
            Linie(S.ptInvested, r.investedPath, MaterialTheme.colorScheme.onSurfaceVariant),
            Linie(name, r.path, MaterialTheme.colorScheme.primary),
        ),
        format = { Fmt.euro(it, 0) },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        datumKopf = false,
    )
    HinweisInfo(S.ptSparNote, kurz = hinweisText("hist"))
    reihe?.let { (daily, divs) -> EinstiegAbschnitte(name, daily, divs, rate.toDouble(), settings.spreadStockBps.toDouble(), einstieg) }
}

// Ist (gefuellt) und Ziel (Marke) als Balken: die Marke steht beim Zielanteil, ohne Ziel bleibt nur der Ist-Balken.
@Composable
private fun ZielBalken(ist: Double, ziel: Double?, farbe: Color, modifier: Modifier = Modifier) {
    val spur = MaterialTheme.colorScheme.surfaceVariant
    val marke = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val h = 6.dp.toPx()
        val y = (size.height - h) / 2f
        val r = CornerRadius(h / 2f, h / 2f)
        drawRoundRect(spur, topLeft = Offset(0f, y), size = Size(size.width, h), cornerRadius = r)
        val w = (size.width * (ist / 100.0).coerceIn(0.0, 1.0)).toFloat()
        if (w > 0f) drawRoundRect(farbe, topLeft = Offset(0f, y), size = Size(w.coerceAtLeast(h), h), cornerRadius = r)
        if (ziel != null) {
            val x = (size.width * (ziel / 100.0).coerceIn(0.0, 1.0)).toFloat().coerceIn(1.5.dp.toPx(), size.width - 1.5.dp.toPx())
            drawLine(marke, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.5.dp.toPx())
        }
    }
}

// Zeile der Zielliste: Name (optional mit Logo) und Aussage rechts, darunter der Balken Ist mit Marke Ziel, darunter
// links der Ist-Anteil und rechts das Ziel oder "kein Ziel". Ein Tipp oeffnet das Eingabeblatt fuers Ziel.
@Composable
private fun ZielZeile(
    kopf: @Composable (Modifier) -> Unit, logo: (@Composable () -> Unit)?, rechts: String, rechtsFarbe: Color,
    ist: Double, ziel: Double?, balkenFarbe: Color, onClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (logo != null) { logo(); Spacer(Modifier.width(12.dp)) }
            kopf(Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(rechts, style = MaterialTheme.typography.titleSmall, color = rechtsFarbe, textAlign = TextAlign.End, maxLines = 1, softWrap = false)
        }
        ZielBalken(ist, ziel, balkenFarbe, Modifier.padding(top = 6.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(Fmt.pct(ist, 1), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
            Spacer(Modifier.weight(1f))
            Text(
                if (ziel == null) S.umKeinZiel else S.umZiel + " " + Fmt.pct(ziel, 1),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false,
            )
        }
    }
}

// Eingabeblatt fuer ein Ziel in Prozent: Regler und Zahl; jede Aenderung gilt sofort.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZielBlatt(titel: String, ist: Double, ziel: Double?, onSet: (Double) -> Unit, onDismiss: () -> Unit) {
    var wert by remember { mutableDoubleStateOf((ziel ?: ist).coerceIn(0.0, 100.0)) }
    var text by remember { mutableStateOf(zahlText(roundTo(wert, 0.1))) }
    fun uebernehmen(v: Double) { wert = v.coerceIn(0.0, 100.0); onSet(roundTo(wert, 0.1)) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(titel, style = MaterialTheme.typography.titleLarge)
            Text(S.umJetzt + " " + Fmt.pct(ist, 1), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ToolSlider(wert.toFloat(), { v -> uebernehmen(v.toDouble()); text = zahlText(roundTo(wert, 0.1)) }, 0f..100f, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { v ->
                        text = v.filter { it.isDigit() || it == ',' || it == '.' }.take(5)
                        text.replace(',', '.').toDoubleOrNull()?.let { uebernehmen(it) }
                    },
                    singleLine = true, label = { Text(S.umZiel + " %") }, shape = RoundedCornerShape(Radius.field),
                    modifier = Modifier.width(110.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(S.ok) }
            }
        }
    }
}
