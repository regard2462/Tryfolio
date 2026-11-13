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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.MutableState
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
import de.troetenrepublik.app.engine.Risikoregeln
import de.troetenrepublik.app.engine.RueckblickZeitraum
import de.troetenrepublik.app.engine.Bewertungsblick
import de.troetenrepublik.app.engine.Tax
import de.troetenrepublik.app.engine.Zeitraum
import de.troetenrepublik.app.engine.Vergleichsrechnung
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.Portfolioverlauf
import de.troetenrepublik.app.market.Symbols
import kotlin.math.abs
import kotlinx.coroutines.launch

// Werkzeuge am Ende der Wertpapierseite. Sie rechnen mit den echten Tagesschlusskursen des Werts
// (bis zu zehn Jahre, in Euro) und seinen Kennzahlen.
enum class StockTool { LUMP_SUM, POSITION_SIZE, SELL_TAX, HEBEL }

// Seit 5.92: Einmalanlage und Sparplan im Rueckblick in einem Blatt (LUMP_SUM mit Umschalter), der faire Wert steht
// bei der Bewertung, Schwankung und Rueckgaenge unter „Wie tief kann es fallen“. Wer den Wert haelt, sieht den
// Verkauf mit Steuer zuerst. Seit 0.77 klappt der Rueckblick unter dem Chart auf (Knopf „Rückblick“) und steht nicht mehr hier.
@Suppress("UNUSED_PARAMETER")
fun toolsFor(type: AssetType, holds: Boolean, profile: MarketApi.Profile?): List<StockTool> = buildList {
    if (holds) add(StockTool.SELL_TAX)
    if (type.directlyTradable) add(StockTool.POSITION_SIZE)
    // Hebel-Simulator (0.78): fuer jeden Wert mit Tageskursen
    add(StockTool.HEBEL)
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
fun StockToolSheet(
    state: AppState, tool: StockTool, symbol: String, name: String, profile: MarketApi.Profile?, onDismiss: () -> Unit,
    onSteuer: (() -> Unit)? = null,
    // Positionsgroesse: Kaufmaske mit Stueckzahl und Stoppkurs oeffnen
    onOrder: ((stueck: Double, stopp: Double, ziel: Double) -> Unit)? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = blattZustandHier(),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            // Der Rueckblick zeichnet seine Ueberschrift selbst, mit Info-Symbol
            if (tool != StockTool.LUMP_SUM) {
                Text(S.toolTitle(tool.name), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp))
                Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(8.dp))
            }
            when (tool) {
                StockTool.LUMP_SUM -> RueckblickReiter(state, symbol, name)
                StockTool.POSITION_SIZE -> PositionSizeTool(state, symbol, onOrder)
                StockTool.SELL_TAX -> SellTaxTool(state, symbol, onSteuer)
                StockTool.HEBEL -> HebelTool(state, symbol)
            }
        }
    }
}

// Bausteine

// Tageskurse eines Werts: kerzen null, solange noch geladen wird. [0.75] ohneNetz, wenn die Reihe leer blieb, weil der
// Abruf keine Antwort bekam (dann "Keine Verbindung" statt "keine Daten"); neuLaden fragt erneut an.
private class Tageskurse(val kerzen: List<Candle>?, val ohneNetz: Boolean, val neuLaden: () -> Unit)

@Composable
private fun rememberDaily(state: AppState, symbol: String): Tageskurse {
    var daily by remember(symbol) { mutableStateOf<List<Candle>?>(null) }
    var ohneNetz by remember(symbol) { mutableStateOf(false) }
    var versuch by remember(symbol) { mutableIntStateOf(0) }
    LaunchedEffect(symbol, versuch) {
        daily = null
        val d = runCatching { state.market.daily(symbol) }.getOrDefault(emptyList())
        ohneNetz = d.isEmpty() && state.market.ohneVerbindung()
        daily = d
    }
    return Tageskurse(daily, ohneNetz) { versuch++ }
}

// [0.75] Leere Reihe wegen fehlender Verbindung: Hinweis mit Knopf fuer einen neuen Versuch; true, wenn er steht.
@Composable
private fun zeigtOhneNetz(t: Tageskurse): Boolean {
    if (t.kerzen?.isEmpty() != true || !t.ohneNetz) return false
    EmptyHint(S.ohneVerbindung, S.erneutVersuchen to t.neuLaden)
    return true
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
private fun <T> ChoiceRow(label: String, options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) =
    ToolChoice(label, options, selected, onSelect)

@Composable
private fun Note(text: String) {
    AufklappText(text, Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
}

// Erklaerungen und Annahmen: Kernzeile, der Rest hinter dem Info-Symbol statt als Textblock unter dem Ergebnis.
@Composable
private fun Erklaerung(text: String) {
    HinweisInfo(text)
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
    val base = listOf(1, 3, 5, 10).filter { it <= have + 0.05 }.map { S.toolYearsKurz(it) to it }
    return if (base.isEmpty() || have < 9.9) base + (S.toolMax to 0) else base
}

private fun startFor(daily: List<Candle>, yearsBack: Int): Long =
    if (yearsBack <= 0) daily.firstOrNull()?.t ?: 0L else System.currentTimeMillis() - yearsBack * YEAR_MS

private fun signed(v: Double): String = (if (v >= 0) "+" else "−") + Fmt.pct(abs(v))

// Zeitraum des Rueckblicks je Seite: feste Laenge in Tagen (0 = Max) oder eigener Zeitraum als Tage seit 1970.
private class RbZeit(
    val tage: MutableIntState, val eigen: MutableState<Boolean>, val von: MutableLongState, val bis: MutableLongState,
) {
    fun beginn(d: List<Candle>, jetzt: Long): Long =
        RueckblickZeitraum.beginn(d.firstOrNull()?.t ?: 0L, jetzt, tage.intValue, if (eigen.value) von.longValue else null)
    fun ende(jetzt: Long): Long = RueckblickZeitraum.ende(jetzt, if (eigen.value) bis.longValue else null)
}

@Composable
private fun rememberRbZeit(): RbZeit {
    val tage = rememberSaveable { mutableIntStateOf(1826) }
    val eigen = rememberSaveable { mutableStateOf(false) }
    val von = rememberSaveable { mutableLongStateOf(0L) }
    val bis = rememberSaveable { mutableLongStateOf(0L) }
    return remember(tage, eigen, von, bis) { RbZeit(tage, eigen, von, bis) }
}

// Zeitleiste wie beim Kurschart: Laengen nach vorhandener Geschichte, Max immer, rechts der eigene Zeitraum.
@Composable
private fun RueckblickZeitwahl(d: List<Candle>, zeit: RbZeit) {
    val geschichte = (System.currentTimeMillis() - d.first().t) / Zeitraum.TAG
    val angebot = RueckblickZeitraum.angebot(geschichte)
    val gewaehlt = if (zeit.tage.intValue in angebot) zeit.tage.intValue else 0
    var dialog by remember { mutableStateOf(false) }
    Text(S.widgetZeitraum, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp))
    ZeitLeiste(
        angebot.map { S.pvRange(it) }, selected = if (zeit.eigen.value) -1 else angebot.indexOf(gewaehlt),
        onSelect = { zeit.tage.intValue = angebot[it]; zeit.eigen.value = false },
        modifier = Modifier.padding(horizontal = 12.dp),
        trailing = { ZeitraumKnopf(zeit.eigen.value) { dialog = true } },
    )
    if (dialog) ZeitraumDialog(zeit.von.longValue, zeit.bis.longValue, onDismiss = { dialog = false }) { v, b ->
        zeit.von.longValue = minOf(v, b); zeit.bis.longValue = maxOf(v, b); zeit.eigen.value = true; dialog = false
    }
}

// Rueckblick und Vergleich unter dem Chart der Wertpapierseite (0.77): derselbe Inhalt wie im Blatt, ohne den Namen.
@Composable
internal fun RueckblickEingebettet(state: AppState, symbol: String, name: String) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) { RueckblickReiter(state, symbol, name, eingebettet = true) }
}

// Einmalanlage und Sparplan als zwei wischbare Seiten mit Pille. Der Reiter laedt Kurse und Dividenden
// einmal und haelt Auswahl und Auswahlblatt der Vergleichsziele fuer beide Seiten (5.96).
@Composable
private fun RueckblickReiter(state: AppState, symbol: String, name: String, eingebettet: Boolean = false) {
    val pager = rememberPagerState(initialPage = 0) { 2 }
    val scope = rememberCoroutineScope()
    val titel = listOf(S.wzEinmal, S.copyMonthly)
    var zieleText by rememberSaveable(symbol) { mutableStateOf(VergleichZiele.start(symbol).joinToString("\n")) }
    val ziele = zieleText.lines().filter { it.isNotBlank() }
    // Die Erklaerungen stehen als Kernzeile „historisch und hypothetisch“ mit Info-Symbol unter dem Ergebnis jeder Seite
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 4.dp)) {
        Text(S.wzRueckblick, style = if (eingebettet) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall)
        if (!eingebettet) Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(8.dp))
    val tageskurse = rememberDaily(state, symbol)
    if (zeigtOhneNetz(tageskurse)) return
    val daily = tageskurse.kerzen
    val divs = rememberDividenden(state, symbol)
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
    val zeit = rememberRbZeit()
    if (showsNoData(daily)) return
    // Dividenden abwarten, sonst zeigt die Kurve kurz die unverzerrte Kursrendite
    if (symbol != WORLD_ETF && divs == null) { Note(S.toolLoading); return }
    val d = daily!!
    ChoiceRow(S.amount, listOf(500.0, 1_000.0, 5_000.0, 10_000.0).map { Fmt.euro(it, 0) to it }, amount) { amount = it }
    RueckblickZeitwahl(d, zeit)
    val dg = mitErtraegen(d, divs, symbol)
    val jetzt = System.currentTimeMillis()
    val gewuenscht = zeit.beginn(d, jetzt)
    val bisMax = zeit.ende(jetzt)
    val ok = okZiele(ziele, daten)
    val reihen = listOf(dg.map { it.t to it.close }) + ok.mapNotNull { daten[it]?.reihe }
    // Ohne gemeinsamen Zeitraum von mindestens MIN_TAGE rechnet nur die Aktie; die Ziele zeigen „zu kurzer Verlauf“
    val gemeinsam = Vergleichsrechnung.gemeinsam(reihen, gewuenscht, bisMax)
    val sp = gemeinsam ?: Vergleichsrechnung.Spanne(gewuenscht, bisMax)
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
    VergleichsChart(
        linien = listOf(Linie(Symbols.name(symbol), r.path, FARBE_AKTIE)) +
            e.map { (k, x) -> Linie(VergleichZiele.name(state, k), x.path, Kategorie.farbe(ziele.indexOf(k) + 1)) },
        format = { Fmt.euro(it, 0) },
        hoehe = 190.dp,
        nullLinie = amount,
        wertText = { Fmt.euro(it) },
        zusatz = { _, _, v -> signed((v / amount - 1) * 100) to signal.forChange(v - amount) },
    )
    ZielChips(state, ziele, daten, onZiele, onVergleichen)
    KeyValueRow(S.toolBoughtOn(Fmt.date(r.start.t), Fmt.priceEuro(r.start.close)), "${Fmt.qty(r.units)} ${S.pieces}")
    KeyValueRow(S.importValueToday, Fmt.euro(r.value))
    KeyValueRow(S.result, "${Fmt.signedEuro(r.gain)}  ${signed(r.pct)}", valueColor = signal.forChange(r.gain))
    r.perYear?.let { KeyValueRow(S.toolPerYear, signed(it)) }
    KeyValueRow(S.maxDrawdown, Fmt.signedPct(-abs(r.maxDrawdown)))
    if (ziele.isNotEmpty()) ZielZeilen(
        state, ziele, daten,
        { k -> e.firstOrNull { it.first == k }?.second?.let { p -> Triple(p.value, p.pct, p.gain) } },
        S.vgZeitraum(Fmt.date(r.start.t), if (sp.bis >= jetzt) S.vgHeute else Fmt.date(sp.bis)),
        if (gemeinsam == null) null else begrenzerText(state, symbol, ok, reihen, gewuenscht, sp.von),
    )
    HinweisInfo(hinweis(S.toolNoCosts, ziele), kurz = hinweisText("hist"))
}

// Sparplan im Rueckblick

@Composable
private fun SavingsPlanTool(
    state: AppState, symbol: String, daily: List<Candle>?, divs: List<MarketApi.DividendEvent>?,
    ziele: List<String>, daten: Map<String, VergleichZiele.Daten>, onZiele: (String) -> Unit, onVergleichen: () -> Unit,
) {
    val quotes by state.quotes.collectAsStateWithLifecycle()
    var monthly by rememberSaveable { mutableDoubleStateOf(100.0) }
    val zeit = rememberRbZeit()
    if (showsNoData(daily)) return
    // Dividenden abwarten, sonst zeigt die Kurve kurz die unverzerrte Kursrendite
    if (symbol != WORLD_ETF && divs == null) { Note(S.toolLoading); return }
    val d = daily!!
    ChoiceRow(S.toolMonthly, listOf(25.0, 50.0, 100.0, 250.0, 500.0).map { Fmt.euro(it, 0) to it }, monthly) { monthly = it }
    RueckblickZeitwahl(d, zeit)
    val dg = mitErtraegen(d, divs, symbol)
    val jetzt = System.currentTimeMillis()
    val gewuenscht = zeit.beginn(d, jetzt)
    val bisMax = zeit.ende(jetzt)
    // gemeinsamer Zeitraum, damit jede Linie dieselben Monate und Raten bekommt
    val ok = okZiele(ziele, daten)
    val reihen = listOf(dg.map { it.t to it.close }) + ok.mapNotNull { daten[it]?.reihe }
    // Ohne gemeinsamen Zeitraum von mindestens MIN_TAGE rechnet nur die Aktie; die Ziele zeigen „zu kurzer Verlauf“
    val gemeinsam = Vergleichsrechnung.gemeinsam(reihen, gewuenscht, bisMax)
    val sp = gemeinsam ?: Vergleichsrechnung.Spanne(gewuenscht, bisMax)
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
    VergleichsChart(
        linien = listOf(Linie(Symbols.name(symbol), r.path, FARBE_AKTIE)) +
            e.map { (k, x) -> Linie(VergleichZiele.name(state, k), x.path, Kategorie.farbe(ziele.indexOf(k) + 1)) } +
            listOf(Linie(S.toolInvested, r.investedPath, grey)),
        format = { Fmt.euro(it, 0) },
        hoehe = 190.dp,
        wertText = { Fmt.euro(it) },
        // Veraenderung gegenueber dem bis dahin eingezahlten Betrag; die Linie der Einzahlungen hat keine
        zusatz = { l, t, v ->
            val eingezahlt = wertAm(r.investedPath, t)
            if (l.farbe == grey || eingezahlt == null || eingezahlt <= 0) null
            else signed((v / eingezahlt - 1) * 100) to signal.forChange(v - eingezahlt)
        },
    )
    ZielChips(state, ziele, daten, onZiele, onVergleichen)
    KeyValueRow(S.toolInvested, Fmt.euro(r.invested))
    KeyValueRow(S.toolExecutions, r.executions.toString())
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
    HinweisInfo(hinweis(if (ziele.isEmpty()) S.vgPlanNote else S.vgPlanNote + "\n" + S.vgGleicheRaten, ziele), kurz = hinweisText("hist"))
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
    val teile = grund.split("\n").toMutableList()
    if ("m:msci" in ziele) teile += S.toolWorldNote
    if (ziele.any { it.startsWith("d:") || it.startsWith("i:") || it.startsWith("k:") || it.startsWith("n:") || it.startsWith("r:") }) teile += S.vgWaehrungHinweis
    if (ziele.any { it.startsWith("l:") }) teile += S.vgLaborHinweis
    return teile.joinToString("\n") { "• $it" }
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

// In der Karte „Rendite und Risiko“ (5.91); die Schwankung steht in den Kennzahlen.
@Composable
internal fun RiskTool(state: AppState, symbol: String, kompakt: Boolean = false) {
    val tageskurse = rememberDaily(state, symbol)
    val daily = tageskurse.kerzen
    val world = rememberDaily(state, WORLD_ETF).kerzen
    // -1 = noch nichts gewaehlt: der laengste Zeitraum, passend zu den Zeilen darueber, die ueber alle Kurse rechnen
    var period by remember { mutableIntStateOf(-1) }
    if (zeigtOhneNetz(tageskurse)) return
    if (showsNoData(daily)) return
    val d = daily!!
    val options = periodOptions(d)
    val chosen = if (options.any { it.second == period }) period else options.lastOrNull()?.second ?: 0
    ToolZeit(S.widgetZeitraum, options, chosen) { period = it }
    val tradingDays = if (Symbols.typeOf(symbol) == AssetType.CRYPTO) 365 else 252
    val from = startFor(d, chosen)
    val r = Analysis.risk(d, from, tradingDays) ?: run { Note(S.toolNoData); return }
    val w = world?.takeIf { symbol != WORLD_ETF && it.isNotEmpty() }?.let { Analysis.risk(it, from, 252) }
    val signal = LocalSignal.current
    Spacer(Modifier.height(8.dp))
    VerlaufDiagramm(r.drawdownPath, signal.down, { Fmt.signedPct(it, 1) }, Modifier.padding(horizontal = 20.dp), fuellen = true)
    // In der Karte „Rendite und Risiko“ stehen Rueckgang und Erholung schon darueber
    if (!kompakt) {
        KeyValueRow(S.maxDrawdown, Fmt.signedPct(-abs(r.maxDrawdown)))
        w?.let { KeyValueRow(S.toolWorldEtfShort, Fmt.signedPct(-abs(it.maxDrawdown))) }
        KeyValueRow(S.toolDrawdownPeriod, S.toolDrawdownRange(Fmt.date(r.peakAt), Fmt.date(r.troughAt)))
        KeyValueRow(S.toolRecovered, r.recoveredAt?.let { Fmt.date(it) } ?: S.toolNotRecovered)
    }
    r.worstDay?.let { KeyValueRow(S.toolWorstDay, signed(it.second), valueColor = signal.down, hint = Fmt.date(it.first)) }
    r.bestDay?.let { KeyValueRow(S.toolBestDay, signed(it.second), valueColor = signal.up, hint = Fmt.date(it.first)) }
    KeyValueRow(S.toolUpDays, Fmt.pct(r.upDays, 0))
    if (!kompakt) Erklaerung(S.toolRiskNote)
}

// Fairer Wert

@Composable
internal fun FairValueTool(state: AppState, symbol: String, profile: MarketApi.Profile?) {
    val quotes by state.quotes.collectAsStateWithLifecycle()
    val fxKurse by state.market.fxKurse.collectAsStateWithLifecycle()
    val q = quotes[symbol]
    // Vorgaben wie die Bewertungszeile der Seite: bisheriges (sonst erwartetes) Gewinnwachstum, zehn Jahre, KGV der
    // Branche oder 15, 8 % Rendite. Solange nichts gewaehlt ist, gelten sie.
    var branche by remember(symbol) { mutableStateOf<Double?>(null) }
    LaunchedEffect(symbol) { branche = branchenKgv(state, symbol) }
    var growthWahl by remember(symbol) { mutableStateOf<Float?>(null) }
    var years by remember { mutableIntStateOf(Bewertungsblick.JAHRE) }
    var exitWahl by remember(symbol) { mutableStateOf<Float?>(null) }
    var required by remember { mutableDoubleStateOf(Bewertungsblick.RENDITE) }
    if (q == null || profile == null || q.price <= 0) { Note(S.toolNoData); return }
    // Kurs in der Handelswaehrung; der Gewinn je Aktie aus Kurs und KGV wie in der Bewertungszeile, damit er in
    // derselben Waehrung steht wie der Kurs. Anzeige in der Waehrung der App.
    val handel = q.nativePrice > 0
    val nativePrice = if (handel) q.nativePrice else q.price
    val waehrung = if (handel) q.nativeCurrency else "EUR"
    val eps = Bewertungsblick.kgv(profile.trailingPe, q.peRatio)?.let { nativePrice / it } ?: profile.eps
    if (eps <= 0) { Note(S.toolNoData); return }
    val ziel = Fmt.anzeigeWaehrung
    val seite = remember(ziel, fxKurse) { SeitenWaehrung(ziel, fxKurse) }
    val lage = remember(profile, q.peRatio, branche) { bewertungslage(profile, q, branche) }
    val growth = growthWahl ?: (lage?.let { Bewertungsblick.annahme(it.bisher ?: it.erwartet) } ?: 10.0).toFloat()
    val exitPe = exitWahl ?: Bewertungsblick.zielKgv(branche).toFloat()
    SliderRow(S.toolGrowth, Fmt.pct(growth.toDouble(), 0), growth, 0f..25f, 1f) { growthWahl = it }
    ChoiceRow(S.widgetZeitraum, listOf(5, 10).map { S.toolYearsKurz(it) to it }, years) { years = it }
    SliderRow(S.toolExitPe, Fmt.num(exitPe.toDouble(), 0) + "×", exitPe, 8f..35f, 1f) { exitWahl = it }
    ChoiceRow(S.toolRequired, listOf(6.0, 8.0, 10.0, 12.0).map { Fmt.pct(it, 0) to it }, required) { required = it }
    val r = Analysis.fairValue(eps, nativePrice, growth.toDouble(), years, exitPe.toDouble(), required) ?: run { Note(S.toolNoData); return }
    Spacer(Modifier.height(10.dp))
    KeyValueRow(S.toolEpsNow, seite.preis(eps, waehrung))
    KeyValueRow(S.toolPeNow, Fmt.num(r.peNow, 1))
    KeyValueRow(S.toolEpsIn(years), seite.preis(r.epsFuture, waehrung))
    KeyValueRow(S.toolPriceIn(years), seite.preis(r.priceFuture, waehrung))
    // Fairer Wert gegen aktuellen Kurs als zwei Balken auf derselben Skala
    val skala = maxOf(r.fair, nativePrice).takeIf { it > 0 } ?: 1.0
    ShareBar(S.toolFairToday, seite.preis(r.fair, waehrung), r.fair / skala, AnteilsFarben.folge(0))
    ShareBar(
        S.toolCurrentPrice,
        seite.preis(nativePrice, waehrung) + "  " + if (r.premium >= 0) S.toolAbove(Fmt.pct(r.premium, 0)) else S.toolBelow(Fmt.pct(abs(r.premium), 0)),
        nativePrice / skala, LocalSignal.current.forChange(-r.premium),
    )
    KeyValueRow(S.toolImpliedReturn, signed(r.impliedReturn))
    Erklaerung(S.toolFairNote)
}

// Positionsgroesse

@Composable
private fun PositionSizeTool(state: AppState, symbol: String, onOrder: ((stueck: Double, stopp: Double, ziel: Double) -> Unit)? = null) {
    val quotes by state.quotes.collectAsStateWithLifecycle()
    val valuation by state.valuation.collectAsStateWithLifecycle()
    val account by state.account.collectAsStateWithLifecycle()
    // [0.78] Eigene Regeln als Vorgabe: Hoechstverlust je Geschaeft und hoechster Anteil einer Position
    val regeln = account.depotRegeln
    val risiken = listOf(0.5, 1.0, 2.0, 3.0)
    val anteile = listOf(0.0, 10.0, 20.0, 30.0, 50.0)
    var risk by remember { mutableDoubleStateOf(regeln.maxVerlustGeschaeft.takeIf { it in risiken } ?: 1.0) }
    var stop by remember { mutableDoubleStateOf(8.0) }
    var maxAnteil by remember { mutableDoubleStateOf(regeln.maxPosition.takeIf { it in anteile } ?: 0.0) }
    var zielMal by remember { mutableDoubleStateOf(0.0) }
    val price = quotes[symbol]?.price ?: 0.0
    if (price <= 0 || valuation.total <= 0) { Note(S.toolNoData); return }
    ChoiceRow(S.toolRiskPerTrade, risiken.map { Fmt.pct(it, if (it % 1.0 == 0.0) 0 else 1) to it }, risk) { risk = it }
    ChoiceRow(S.toolStopDistance, listOf(3.0, 5.0, 8.0, 10.0, 15.0, 20.0).map { Fmt.pct(it, 0) to it }, stop) { stop = it }
    ChoiceRow(S.ri("anteil"), anteile.map { (if (it == 0.0) S.thAus else Fmt.pct(it, 0)) to it }, maxAnteil) { maxAnteil = it }
    ChoiceRow(S.ri("ziel"), listOf(0.0, 1.0, 1.5, 2.0, 3.0).map { (if (it == 0.0) S.thAus else S.ri("zielMal", listOf(Fmt.num(it, if (it % 1.0 == 0.0) 0 else 1)))) to it }, zielMal) { zielMal = it }
    val whole = Symbols.typeOf(symbol) != AssetType.CRYPTO
    // Kleinster Wert aus Hoechstverlust, Anteil am Depot und Guthaben nach dem Puffer der eigenen Regel
    val bisher = valuation.positions.filter { it.key == symbol }.sumOf { it.value }
    val r = Risikoregeln.groesse(valuation.total, price, risk, stop, maxAnteil, bisher, account.frei, regeln.minBargeld, whole) ?: run { Note(S.toolNoData); return }
    Spacer(Modifier.height(10.dp))
    KeyValueRow(S.toolDepotValue, Fmt.euro(valuation.total))
    KeyValueRow(S.toolStopPrice, Fmt.priceEuro(r.stoppKurs))
    TrendStopp(state, symbol, price) { stop = it }
    AtrStopp(state, symbol, price) { stop = it }
    KeyValueRow(S.toolLossPerUnit, Fmt.priceEuro(r.verlustJeStueck))
    KeyValueRow(S.toolMaxLoss, Fmt.euro(r.hoechstverlust))
    KeyValueRow(
        S.piecesLabel, Fmt.qty(r.stueck),
        hint = if (r.grenze == Risikoregeln.Grenze.BARGELD && regeln.minBargeld > 0) S.ri("grenze.PUFFER", listOf(Fmt.pct(regeln.minBargeld, 0))) else S.ri("grenze." + r.grenze.name),
    )
    // [0.75] Schon ein Stueck riskiert mehr als erlaubt: das steht da, mit dem Risiko eines Stuecks
    if (r.einStueckZuViel) Note(
        if (r.grenze == Risikoregeln.Grenze.RISIKO) S.toolEinStueckZuViel(Fmt.priceEuro(r.verlustJeStueck), Fmt.euro(r.hoechstverlust)) else S.ri("zuViel." + r.grenze.name),
    )
    KeyValueRow(S.toolCapital, Fmt.euro(r.kapital))
    ShareBar(S.toolDepotShare, Fmt.pct(r.anteil, 0), r.anteil / 100.0, AnteilsFarben.folge(0))
    // [0.78] Ziel als Vielfaches des Risikos, mit der Trefferquote zum Ausgleich
    val ziel = if (zielMal > 0) Risikoregeln.ziel(price, r.stoppKurs, zielMal) else null
    if (ziel != null) {
        KeyValueRow(S.ri("ziel"), Fmt.priceEuro(ziel), hint = Risikoregeln.trefferquote(zielMal)?.let { S.ri("crvZeile", listOf(Fmt.num(zielMal, 1), Fmt.pct(it * 100.0, 0))) })
        Erklaerung(S.ri("quoteHinweis"))
    }
    Erklaerung(S.toolPositionNote)
    if (onOrder != null && r.stueck > 0) NavRow(S.st("op.vorbereiten")) { onOrder(r.stueck, r.stoppKurs, ziel ?: 0.0) }
}

// Verkauf mit Steuer

@Composable
private fun SellTaxTool(state: AppState, symbol: String, onSteuer: (() -> Unit)?) {
    val account by state.account.collectAsStateWithLifecycle()
    val pos = account.positions.firstOrNull { it.key == symbol && it.derivative == null } ?: run { Note(S.toolNoData); return }
    var share by rememberSaveable { mutableDoubleStateOf(100.0) }
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
    val erloes = tx.qty * tx.price
    if (tx.tax > 0 && erloes > 0) ShareBar(S.taxSection, Fmt.euro(-tx.tax), tx.tax / erloes, signal.down)
    else KeyValueRow(S.taxSection, Fmt.euro(-tx.tax))
    KeyValueRow(S.feeLabel, Fmt.euro(-tx.fee))
    KeyValueRow(S.docCredit, Fmt.euro(tx.amount - tx.fee))
    Erklaerung(S.toolSellNote)
    if (onSteuer != null) NavRow(S.taxOverview, onClick = onSteuer)
}
