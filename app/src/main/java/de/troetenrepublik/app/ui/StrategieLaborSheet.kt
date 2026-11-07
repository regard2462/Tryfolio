package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.StrategieLabor
import de.troetenrepublik.app.engine.Zeitraum
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.plattform.mausWischen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val JAHR_MS = 365L * 86_400_000L

// Name einer Regel oder Variante.
internal fun regelName(r: StrategieLabor.Regel): String = S.slRegel(r.art.name, r.a, r.b, r.n)

// Kurzer Name einer Regel fuer die Liste; der volle Name steht in der Einzelansicht und im Menue (0.68).
internal fun regelKurzName(r: StrategieLabor.Regel): String = S.slRegelKurz(r.art.name, r.a, r.b, r.n)

// Rendite pro Jahr mit Vorzeichen, Strich ohne Wert.
private fun proJahr(v: Double?): String = v?.let { Fmt.signedPct(it * 100, 1) } ?: "—"

// Feste Farbe je Regel aus der Kategorie-Palette; Halten ist grau (letzter Eintrag).
@Composable
private fun regelFarbe(r: StrategieLabor.Regel): Color {
    if (r.art == StrategieLabor.Art.HALTEN) return Kategorie.farben.last()
    val i = StrategieLabor.REGELN.indexOf(r)
    return if (i < 1) MaterialTheme.colorScheme.onSurface else Kategorie.farbe(i - 1)
}

// Strategie-Labor (seit 5.91): Signalregeln auf Tageskerzen der letzten Jahre gegen Kaufen und Halten, mit Spread aus
// den Einstellungen und 1 Euro je Order. Darunter die Backtest-Lotterie: viele Varianten im Lernzeitraum, die beste
// im Pruefzeitraum und die Deflated Sharpe Ratio. Seit 5.93 Zeitraeume als Zeitleiste, gekoppelt an einen wischbaren
// Pager (1, 3, 5, 10 Jahre, Max, eigener Zeitraum) und feste Farben je Regel.
// Jede Linie der Uebersicht laesst sich ueber ihren Punkt aus- und einblenden (5.96).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrategieLaborSheet(state: AppState, symbol: String, name: String, onDismiss: () -> Unit) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    var kerzen by remember(symbol) { mutableStateOf<List<Candle>?>(null) }
    LaunchedEffect(symbol) { kerzen = state.dailyCloses(symbol) }
    var gewaehlt by remember { mutableStateOf(StrategieLabor.REGELN[1]) }
    var familie by remember { mutableStateOf(StrategieLabor.Familie.ALLE) }
    var aus by remember { mutableStateOf(emptySet<StrategieLabor.Regel>()) }
    val kosten = StrategieLabor.Kosten(settings.spreadStockBps.toDouble())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Text(S.slTitel, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp))
            Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
            ToolNote(S.slErklaerung)
            val k = kerzen
            when {
                k == null -> ToolNote(S.toolLoading)
                k.size < 300 -> ToolNote(S.slZuWenig)
                else -> LaborInhalt(k, gewaehlt, { gewaehlt = it }, familie, { familie = it }, aus, { aus = StrategieLabor.umschalten(aus, it) }, kosten)
            }
        }
    }
}

@Composable
private fun LaborInhalt(
    k: List<Candle>,
    gewaehlt: StrategieLabor.Regel, onRegel: (StrategieLabor.Regel) -> Unit,
    familie: StrategieLabor.Familie, onFamilie: (StrategieLabor.Familie) -> Unit,
    aus: Set<StrategieLabor.Regel>, onUmschalten: (StrategieLabor.Regel) -> Unit,
    kosten: StrategieLabor.Kosten,
) {
    // Werte ueber 0 sind Jahre, 0 ist Max, -1 ist Eigener
    val vorhanden = (k.last().t - k.first().t) / JAHR_MS.toDouble()
    val zeiten = remember(k) {
        listOf(1, 3, 5, 10).filter { it <= vorhanden + 0.05 } + (if (vorhanden < 9.9) listOf(0) else emptyList()) + (-1)
    }
    val pager = rememberPagerState(initialPage = (zeiten.size - 2).coerceAtLeast(0)) { zeiten.size }
    val scope = rememberCoroutineScope()
    // Vorgabe die letzten zwei Jahre, damit die Seite sofort Ergebnisse zeigt; ohne sie war sie so kurz, dass das
    // Blatt auf die halbe Hoehe sprang (5.94). Tage seit 1970 wie im Zeitraum-Dialog.
    var eigenVon by remember(k) { mutableStateOf(maxOf(k.first().t, k.last().t - 2 * JAHR_MS) / Zeitraum.TAG) }
    var eigenBis by remember(k) { mutableStateOf(k.last().t / Zeitraum.TAG) }
    var dialog by remember { mutableStateOf(false) }

    Text(S.widgetZeitraum, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp))
    ZeitLeiste(
        zeiten.map { z -> when (z) { -1 -> S.slEigen; 0 -> S.toolMax; else -> S.toolYears(z) } },
        selected = pager.currentPage.coerceIn(0, (zeiten.size - 1).coerceAtLeast(0)),
        onSelect = { scope.launch { pager.animateScrollToPage(it) } },
        modifier = Modifier.padding(horizontal = 12.dp),
    )
    Text(
        S.slKosten(Fmt.pct(kosten.spreadBps / 200.0, 2)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 10.dp),
    )
    HorizontalPager(state = pager, verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().mausWischen(pager)) { seite ->
        val jahre = zeiten[seite]
        if (jahre == -1) {
            Column {
                Row(Modifier.padding(horizontal = 20.dp).padding(top = 8.dp)) {
                    Chip(if (eigenVon > 0) zeitraumText(eigenVon, eigenBis) else S.zeitraumTitel, true) { dialog = true }
                }
                if (eigenVon == 0L) {
                    ToolNote(S.slEigenHinweis)
                } else {
                    val (vonMs, bisMs) = Zeitraum.grenzen(eigenVon, eigenBis, System.currentTimeMillis())
                    val teil = remember(k, eigenVon, eigenBis) { StrategieLabor.ausschnitt(k, vonMs, bisMs) }
                    if (teil == null) ToolNote(S.slZuWenig) else Column { ZeitErgebnis(teil.first, teil.second, gewaehlt, onRegel, familie, onFamilie, aus, onUmschalten, kosten) }
                }
            }
        } else {
            val ab = if (jahre == 0) 0 else StrategieLabor.abIndex(k, k.last().t - jahre * JAHR_MS)
            // Die Seite des Pagers stapelt wie eine Box: ohne Spalte laegen alle Teile uebereinander
            Column { ZeitErgebnis(k, ab, gewaehlt, onRegel, familie, onFamilie, aus, onUmschalten, kosten) }
        }
    }
    if (dialog) ZeitraumDialog(eigenVon, eigenBis, onDismiss = { dialog = false }) { von, bis ->
        eigenVon = minOf(von, bis)
        eigenBis = maxOf(von, bis)
        dialog = false
    }
}

@Composable
private fun ZeitErgebnis(
    k: List<Candle>,
    ab: Int,
    gewaehlt: StrategieLabor.Regel, onRegel: (StrategieLabor.Regel) -> Unit,
    familie: StrategieLabor.Familie, onFamilie: (StrategieLabor.Familie) -> Unit,
    aus: Set<StrategieLabor.Regel>, onUmschalten: (StrategieLabor.Regel) -> Unit,
    kosten: StrategieLabor.Kosten,
) {
    val signal = LocalSignal.current
    val berichte by produceState<List<StrategieLabor.Bericht>?>(null, k, ab, kosten) {
        value = null
        value = withContext(Dispatchers.Default) { StrategieLabor.pruefen(k, ab, kosten) }
    }
    val liste = berichte ?: run { ToolNote(S.toolLoading); return }
    if (liste.isEmpty()) { ToolNote(S.slZuWenig); return }
    val halten = liste.first { it.regel.art == StrategieLabor.Art.HALTEN }

    // Uebersicht: je Regel kurzer Name, Endwert und Rendite pro Jahr in festen Spalten; ein Tipp auf die Zeile blendet ihre Linie aus oder ein.
    // Welche Regel unten im Einzelnen steht, waehlt das Menue an deren Ueberschrift; sie bleibt hier hinterlegt.
    SectionHeader(S.slRegeln)
    val sichtbar = StrategieLabor.sichtbare(liste, aus)
    if (sichtbar.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(160.dp).padding(horizontal = 20.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(S.slKeineLinie, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    } else LinesChart(
        sichtbar.map { StrategieLabor.ausduennen(it.gesamt.verlauf.map { p -> p.second }) to regelFarbe(it.regel) },
        Modifier.fillMaxWidth().height(160.dp).padding(horizontal = 20.dp, vertical = 8.dp),
        baseline = StrategieLabor.START, scrubben = false,
    )
    val grau = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    // Spaltenbreiten von Endwert und Rendite je einmal messen, damit alle Zeilen buendig abschliessen (0.68).
    val messer = rememberTextMeasurer()
    val dichte = LocalDensity.current
    val stilWert = MaterialTheme.typography.titleSmall
    val stilProzent = MaterialTheme.typography.bodySmall
    val (breiteWert, breiteProzent) = remember(liste, stilWert, stilProzent, dichte) {
        var wert = 0
        var prozent = 0
        liste.forEach { b ->
            wert = maxOf(wert, messer.measure(Fmt.euro(b.gesamt.endwert, 0), stilWert, maxLines = 1, softWrap = false).size.width)
            prozent = maxOf(prozent, messer.measure(proJahr(b.gesamt.renditeJahr), stilProzent, maxLines = 1, softWrap = false).size.width)
        }
        with(dichte) { wert.toDp() to prozent.toDp() }
    }
    liste.forEach { b ->
        val aktiv = b.regel == gewaehlt
        val zeigen = b.regel !in aus
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(Radius.chip))
                .background(if (aktiv) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.background)
                .toggleable(value = zeigen, role = Role.Checkbox, onValueChange = { onUmschalten(b.regel) })
                .semantics { contentDescription = S.slLinieSchalter(regelName(b.regel)) }
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(width = 30.dp, height = 40.dp), contentAlignment = Alignment.Center) {
                if (zeigen) Box(Modifier.size(10.dp).clip(CircleShape).background(regelFarbe(b.regel)))
                else Box(Modifier.size(10.dp).border(1.5.dp, regelFarbe(b.regel), CircleShape))
            }
            Text(regelKurzName(b.regel), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 10.dp), color = if (zeigen) Color.Unspecified else grau)
            Spacer(Modifier.width(10.dp))
            Text(Fmt.euro(b.gesamt.endwert, 0), style = MaterialTheme.typography.titleSmall, color = if (zeigen) Color.Unspecified else grau, textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.width(breiteWert))
            Spacer(Modifier.width(10.dp))
            Text(
                proJahr(b.gesamt.renditeJahr), style = MaterialTheme.typography.bodySmall,
                color = if (zeigen) (b.gesamt.renditeJahr?.let { signal.forChange(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant) else grau,
                textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.width(breiteProzent),
            )
        }
    }

    // Die gewaehlte Regel im Einzelnen, verglichen mit Kaufen und Halten
    val b = liste.firstOrNull { it.regel == gewaehlt } ?: liste[1.coerceAtMost(liste.size - 1)]
    val istHalten = b.regel.art == StrategieLabor.Art.HALTEN
    var regelMenue by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button) { regelMenue = true }
                .semantics { contentDescription = S.slRegelWaehlen }
                .padding(horizontal = 20.dp).padding(top = 28.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(regelName(b.regel), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(8.dp))
            ChevronDown(width = 12.dp)
        }
        androidx.compose.material3.DropdownMenu(
            expanded = regelMenue, onDismissRequest = { regelMenue = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            liste.forEach { r ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(regelName(r.regel)) },
                    leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(regelFarbe(r.regel))) },
                    trailingIcon = if (r.regel == b.regel) { { CheckMark(size = 15.dp) } } else null,
                    onClick = { onRegel(r.regel); regelMenue = false },
                )
            }
        }
    }
    val farbeRegel = regelFarbe(b.regel)
    val farbeHalten = regelFarbe(halten.regel)
    LinesChart(
        listOfNotNull(
            StrategieLabor.ausduennen(b.gesamt.verlauf.map { it.second }) to farbeRegel,
            if (istHalten) null else StrategieLabor.ausduennen(halten.gesamt.verlauf.map { it.second }) to farbeHalten,
        ),
        Modifier.fillMaxWidth().height(160.dp).padding(horizontal = 20.dp, vertical = 8.dp),
        baseline = StrategieLabor.START, scrubben = false,
    )
    if (!istHalten) ChartLegend(listOf(S.slDieRegel to farbeRegel, S.slHalten to farbeHalten))
    @Composable fun zeile(label: String, regel: String, h: String) = KeyValueRow(label, if (istHalten) h else S.slVergleich(regel, h))
    if (!istHalten) Text(
        S.slSpalten, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 6.dp),
    )
    val g = b.gesamt
    val hg = halten.gesamt
    zeile(S.slEndwert, Fmt.euro(g.endwert, 0), Fmt.euro(hg.endwert, 0))
    zeile(S.ptPerYear, proJahr(g.renditeJahr), proJahr(hg.renditeJahr))
    zeile(S.copyMaxDrawdown, "−" + Fmt.pct(g.maxRueckgang * 100, 1), "−" + Fmt.pct(hg.maxRueckgang * 100, 1))
    zeile(S.calTrades, g.trades.toString(), hg.trades.toString())
    zeile(S.slTreffer, g.treffer?.let { Fmt.pct(it * 100, 0) } ?: "—", hg.treffer?.let { Fmt.pct(it * 100, 0) } ?: "—")
    zeile(S.slImMarkt, Fmt.pct(g.imMarkt * 100, 0), Fmt.pct(hg.imMarkt * 100, 0))

    // Lern- und Pruefzeitraum getrennt
    val grenze = b.pruef.verlauf.first().first
    zeile(S.slLern(Fmt.date(grenze)), proJahr(b.lern.renditeJahr), proJahr(halten.lern.renditeJahr))
    zeile(S.slPruef(Fmt.date(grenze)), proJahr(b.pruef.renditeJahr), proJahr(halten.pruef.renditeJahr))
    ToolNote(S.slLernText)

    // Backtest-Lotterie: die Familien als wischbare Seiten mit mitlaufenden Reitern (5.94). Jede Seite behaelt ihr
    // Ergebnis, und waehrend der Rechnung bleibt der Platz stehen, damit der Bildschirm beim Wechsel nicht springt.
    SectionHeader(S.slLotterie)
    ToolNote(S.slLotterieText)
    val familien = StrategieLabor.Familie.entries
    val lpager = rememberPagerState(initialPage = familien.indexOf(familie).coerceAtLeast(0)) { familien.size }
    val lscope = rememberCoroutineScope()
    LaunchedEffect(lpager.currentPage) { onFamilie(familien[lpager.currentPage]) }
    val ergebnisse = remember(k, ab, kosten) { mutableStateMapOf<StrategieLabor.Familie, List<StrategieLabor.Lotterie>>() }
    MitlaufendeReiter(familien.size, lpager, { S.slFamilie(familien[it].name) }) { lscope.launch { lpager.animateScrollToPage(it) } }
    HorizontalPager(state = lpager, verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().nestedScroll(WischenBehalten).mausWischen(lpager)) { i ->
        Column(Modifier.fillMaxWidth().heightIn(min = 340.dp)) { LotterieSeite(k, ab, kosten, familien[i], ergebnisse) }
    }
}

@Composable
private fun LotterieSeite(
    k: List<Candle>, ab: Int, kosten: StrategieLabor.Kosten, fam: StrategieLabor.Familie,
    ergebnisse: MutableMap<StrategieLabor.Familie, List<StrategieLabor.Lotterie>>,
) {
    val signal = LocalSignal.current
    LaunchedEffect(k, ab, kosten, fam) {
        if (ergebnisse.containsKey(fam)) return@LaunchedEffect
        // Kurz warten: schnelles Wischen ueber mehrere Seiten startet keine Berechnung
        delay(300)
        ergebnisse[fam] = withContext(Dispatchers.Default) { listOfNotNull(StrategieLabor.lotterie(k, ab, kosten, fam) { ensureActive() }) }
    }
    val liste2 = ergebnisse[fam] ?: run { ToolNote(S.slLotterieLaeuft); return }
    if (liste2.isEmpty()) { ToolNote(S.slZuWenig); return }
    val l = liste2.first()
    Spacer(Modifier.height(6.dp))
    KeyValueRow(S.slVarianten, l.anzahl.toString())
    KeyValueRow(S.slBeste, regelName(l.beste))
    KeyValueRow(S.slBesteLern, S.slVergleich(proJahr(l.besteLern.renditeJahr), proJahr(l.haltenLern.renditeJahr)))
    KeyValueRow(
        S.slBestePruef, S.slVergleich(proJahr(l.bestePruef.renditeJahr), proJahr(l.haltenPruef.renditeJahr)),
        valueColor = if (l.bestePruef.endwert >= l.haltenPruef.endwert) signal.up else signal.down,
    )
    KeyValueRow(S.slDsr, Fmt.pct(l.dsr * 100, 0))
    Text(
        S.slLotterieErgebnis(l.bestePruef.endwert >= l.haltenPruef.endwert, l.dsr >= 0.95, l.anzahl, Fmt.pct(l.dsr * 100, 0)),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp),
    )
    ToolNote(S.slDsrText)
}
