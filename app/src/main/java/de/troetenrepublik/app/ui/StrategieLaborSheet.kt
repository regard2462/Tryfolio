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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.runtime.snapshots.SnapshotStateMap
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
import de.troetenrepublik.app.engine.Handelsstil
import de.troetenrepublik.app.engine.StrategieLabor
import de.troetenrepublik.app.engine.Taktik
import de.troetenrepublik.app.engine.Zeitraum
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.engine.Rhythmus16
import de.troetenrepublik.app.market.Symbols
import de.troetenrepublik.app.plattform.mausWischen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val JAHR_MS = 365L * 86_400_000L

// Name einer Regel oder Variante.
internal fun regelName(r: StrategieLabor.Regel): String = S.slRegel(r.art.name, r.a, r.b, r.n)

// Kurzer Name einer Regel fuer die Liste; der volle Name steht in der Einzelansicht und im Menue (0.68).
internal fun regelKurzName(r: StrategieLabor.Regel): String = S.slRegelKurz(r.art.name, r.a, r.b, r.n)

// Rendite pro Jahr mit Vorzeichen, Strich ohne Wert.
private fun proJahr(v: Double?): String = v?.let { Fmt.signedPct(it * 100, 1) } ?: "–"

// Feste Farbe je Regel aus der Kategorie-Palette; Halten ist grau (letzter Eintrag). Die Zusatzregeln nehmen die
// helleren Toene, damit sich keine Farbe wiederholt; eine Variante (Trendfilter, Monatsende) traegt die Farbe ihrer Regel.
@Composable
internal fun regelFarbe(r: StrategieLabor.Regel): Color {
    if (r.art == StrategieLabor.Art.HALTEN) return Kategorie.farben.last()
    val i = StrategieLabor.REGELN.indexOf(r.copy(trend = 0, monatlich = false))
    return when {
        i < 1 -> MaterialTheme.colorScheme.onSurface
        i >= ERSTE_ZUSATZREGEL -> Kategorie.froh[ZUSATZFARBEN[(i - ERSTE_ZUSATZREGEL) % ZUSATZFARBEN.size]]
        else -> Kategorie.farbe(i - 1)
    }
}

private val ERSTE_ZUSATZREGEL = StrategieLabor.REGELN.size - StrategieLabor.ZUSATZ.size

// Indigo, Orange, Tuerkis, Rosa, Gold aus den helleren Toenen
private val ZUSATZFARBEN = listOf(6, 1, 3, 4, 5)

// Name eines Ausstiegs.
private fun ausstiegName(a: StrategieLabor.Ausstieg): String = S.slAusstiegName(a.art.name, a.wert, StrategieLabor.ZIEL_STOPP)

// Wahl in der Einzelansicht, die beim Wischen zwischen den Zeitraeumen bleibt: Ausstieg, Trendfilter, Monatsende.
// reihen: Kursreihen der anderen Werte, je Wert hoechstens einmal geladen, solange das Blatt offen ist.
private class LaborWahl(val state: AppState, val symbol: String) {
    var ausstieg by mutableStateOf(StrategieLabor.REGELAUSSTIEG)
    var trendfilter by mutableStateOf(false)
    var monatsende by mutableStateOf(false)
    var sparplan by mutableStateOf(false)
    val reihen = HashMap<String, List<Candle>>()
}

// Kursreihe des Tests: Tageskerzen in Euro, mit wieder angelegten Dividenden, soweit abrufbar. [0.75] ohneNetz: leer,
// weil der Abruf keine Antwort bekam (dann "Keine Verbindung" statt "zu wenige Kurse").
// fruehBis: erster Tag (seit 1970) aus dem gewoehnlichen Kursweg, wenn fruehe Bitcoin-Kurse vorangestellt sind (BtcFrueh).
// taktik: Signalwert und Ausweichwert auf denselben Handelstagen wie kerzen, kennung: ihre Kuerzel (fuer Zwischenspeicher).
private class Reihe(
    val kerzen: List<Candle>, val mitDividenden: Boolean, val ohneNetz: Boolean = false, val fruehBis: Long? = null,
    val taktik: Taktik.Reihen? = null, val kennung: String = "",
)

// Fruehe Bitcoin-Kurse aus der mitgelieferten Datei, einmal gelesen
private val btcFrueh by lazy {
    runCatching { de.troetenrepublik.app.engine.BtcFrueh.lesen(de.troetenrepublik.app.market.Daten.text(de.troetenrepublik.app.engine.BtcFrueh.DATEI)) }.getOrNull()
}

// Daten einer Rechnung; je Rechnung eigene, weil die Zwischenspeicher der Indikatoren nicht fuer mehrere Faeden taugen.
internal fun laborDaten(k: List<Candle>, taktik: Taktik.Reihen?): StrategieLabor.Daten = taktik?.daten(k.size) ?: StrategieLabor.Daten(k)

// Kursreihe eines Werts wie im Strategietest, fuer die Rotation: Kerzen und ob der Abruf ohne Antwort blieb.
internal suspend fun ladeTestKerzen(state: AppState, symbol: String): Pair<List<Candle>, Boolean> = ladeReihe(state, symbol).let { it.kerzen to it.ohneNetz }

// Reihe des gehandelten Werts, mit Signalwert oder Ausweichwert alle auf gemeinsamen Handelstagen.
private suspend fun ladeMitTaktik(state: AppState, symbol: String, signal: String?, ausweich: String?): Reihe {
    val haupt = ladeReihe(state, symbol)
    if ((signal == null && ausweich == null) || haupt.kerzen.size < 300) return haupt
    val s = signal?.let { ladeReihe(state, it) }
    val a = ausweich?.let { ladeReihe(state, it) }
    if (s?.ohneNetz == true || a?.ohneNetz == true) return Reihe(emptyList(), false, ohneNetz = true)
    val t = withContext(Dispatchers.Default) { Taktik.reihen(haupt.kerzen, s?.kerzen, a?.kerzen) }
    return Reihe(t.handel, haupt.mitDividenden, fruehBis = haupt.fruehBis, taktik = t, kennung = "$signal|$ausweich")
}

// Bevorzugt die ganze Kursgeschichte (in Euro zum heutigen Kurs); fehlt sie, gelten die letzten zehn Jahre.
private suspend fun ladeReihe(state: AppState, symbol: String): Reihe {
    val lang = runCatching { state.market.gesamtHistorieEuro(symbol) }.getOrDefault(emptyList())
    if (lang.size >= 300) {
        // Bitcoin: Tage vor dem ersten eigenen Kurs aus der mitgelieferten Datei, zum selben festen Kurs in Euro
        if (symbol == de.troetenrepublik.app.engine.BtcFrueh.SYMBOL) {
            val faktor = de.troetenrepublik.app.engine.BtcFrueh.faktor(runCatching { state.market.gesamtHistorie(symbol) }.getOrDefault(emptyList()), lang)
            val r = faktor?.let { de.troetenrepublik.app.engine.BtcFrueh.verlaengern(symbol, lang, btcFrueh, it) }
            if (r?.uebernahme != null) return Reihe(r.kerzen, false, fruehBis = r.uebernahme)
        }
        val divs = runCatching { state.market.gesamtDividenden(symbol) }.getOrDefault(emptyList())
        return Reihe(StrategieLabor.ertragsreihe(lang, divs), divs.isNotEmpty())
    }
    val kurz = state.dailyCloses(symbol)
    if (kurz.isEmpty() && lang.isEmpty() && state.market.ohneVerbindung()) return Reihe(emptyList(), false, ohneNetz = true)
    val divs = runCatching { state.market.dividendHistory(symbol) }.getOrDefault(emptyList())
    return Reihe(StrategieLabor.ertragsreihe(kurz, divs), divs.isNotEmpty())
}

// Strategie-Labor (seit 5.91): Signalregeln auf Tageskerzen der letzten Jahre gegen Kaufen und Halten, mit Spread aus
// den Einstellungen und 1 Euro je Order. Darunter die Backtest-Lotterie: viele Varianten im Lernzeitraum, die beste
// im Pruefzeitraum und die Deflated Sharpe Ratio. Seit 5.93 Zeitraeume als Zeitleiste, gekoppelt an einen wischbaren
// Pager (1, 3, 5, 10 Jahre, Max, eigener Zeitraum) und feste Farben je Regel.
// Jede Linie der Uebersicht laesst sich ueber ihren Punkt aus- und einblenden (5.96).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
// startRegel: beim Oeffnen gewaehlte Regel (Verweis aus "Wirkt das?"); ohne die Vorgabe der Seite.
fun StrategieLaborSheet(state: AppState, symbol: String, name: String, onDismiss: () -> Unit, startRegel: StrategieLabor.Regel? = null) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    var reihe by remember(symbol) { mutableStateOf<Reihe?>(null) }
    var versuch by remember(symbol) { mutableStateOf(0) }
    // Signalwert und Ausweichwert (0.78); ohne Wahl rechnet der Test wie zuvor
    var signalWert by remember(symbol) { mutableStateOf<String?>(null) }
    var ausweichWert by remember(symbol) { mutableStateOf<String?>(null) }
    var rotation by remember { mutableStateOf(false) }
    LaunchedEffect(symbol, versuch, signalWert, ausweichWert) { reihe = null; reihe = ladeMitTaktik(state, symbol, signalWert, ausweichWert) }
    val offen = remember { mutableStateMapOf<String, Boolean>() }
    var gewaehlt by remember { mutableStateOf(startRegel ?: StrategieLabor.REGELN[1]) }
    var familie by remember { mutableStateOf(StrategieLabor.Familie.ALLE) }
    // Die Zusatzregeln sind anfangs ausgeblendet, damit Chart und Liste bleiben, wie sie waren
    var aus by remember { mutableStateOf(StrategieLabor.ZUSATZ - setOfNotNull(startRegel)) }
    val wahl = remember(symbol) { LaborWahl(state, symbol) }
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
            HinweisInfo(S.slErklaerung, kurz = hinweisText("hist"))
            TaktikEinstellung(state, symbol, signalWert, ausweichWert, offen, { signalWert = it }, { ausweichWert = it })
            val r = reihe
            when {
                r == null -> ToolNote(S.toolLoading)
                r.ohneNetz -> EmptyHint(S.ohneVerbindung, S.erneutVersuchen to { versuch++ })
                r.kerzen.size < 300 -> ToolNote(
                    if (signalWert == null && ausweichWert == null) S.slZuWenig
                    else taktikText("tk.zuwenig", listOfNotNull(signalWert, ausweichWert).joinToString(", ") { wertName(it) }),
                )
                // Die Glocke gilt dem Wert, auf dem das Signal entsteht
                else -> androidx.compose.runtime.CompositionLocalProvider(
                    LocalRegelGlocke provides RegelGlockeZiel(state, signalWert ?: symbol, signalWert?.let { wertName(it) } ?: name),
                ) {
                    LaborInhalt(symbol, r, gewaehlt, { gewaehlt = it }, familie, { familie = it }, aus, { aus = StrategieLabor.umschalten(aus, it) }, kosten, offen, wahl)
                }
            }
            Trennlinie()
            NavRow(S.ptTitle(PortfolioTool.ROTATION.name), S.ptSubtitle(PortfolioTool.ROTATION.name)) { rotation = true }
        }
    }
    if (rotation) RotationBlatt(state) { rotation = false }
}

@Composable
private fun LaborInhalt(
    symbol: String,
    reihe: Reihe,
    gewaehlt: StrategieLabor.Regel, onRegel: (StrategieLabor.Regel) -> Unit,
    familie: StrategieLabor.Familie, onFamilie: (StrategieLabor.Familie) -> Unit,
    aus: Set<StrategieLabor.Regel>, onUmschalten: (StrategieLabor.Regel) -> Unit,
    kosten: StrategieLabor.Kosten,
    offen: SnapshotStateMap<String, Boolean>,
    wahl: LaborWahl,
) {
    val k = reihe.kerzen
    // Werte ueber 0 sind Jahre, 0 ist Max, -1 ist Eigener
    val vorhanden = (k.last().t - k.first().t) / JAHR_MS.toDouble()
    val zeiten = remember(k) {
        val feste = listOf(1, 3, 5, 10, 20).filter { it <= vorhanden + 0.05 }
        feste + (if (vorhanden > (feste.lastOrNull() ?: 0) + 0.5) listOf(0) else emptyList()) + (-1)
    }
    val pager = rememberPagerState(initialPage = zeiten.indexOf(10).takeIf { it >= 0 } ?: (zeiten.size - 2).coerceAtLeast(0)) { zeiten.size }
    val scope = rememberCoroutineScope()
    // Vorgabe die letzten zwei Jahre, damit die Seite sofort Ergebnisse zeigt; ohne sie war sie so kurz, dass das
    // Blatt auf die halbe Hoehe sprang (5.94). Tage seit 1970 wie im Zeitraum-Dialog.
    var eigenVon by remember(k) { mutableStateOf(maxOf(k.first().t, k.last().t - 2 * JAHR_MS) / Zeitraum.TAG) }
    var eigenBis by remember(k) { mutableStateOf(k.last().t / Zeitraum.TAG) }
    var dialog by remember { mutableStateOf(false) }

    Text(S.widgetZeitraum, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp))
    ZeitLeiste(
        zeiten.map { z -> when (z) { -1 -> S.slEigen; 0 -> S.toolMax; else -> S.toolYearsKurz(z) } },
        selected = pager.currentPage.coerceIn(0, (zeiten.size - 1).coerceAtLeast(0)),
        onSelect = { scope.launch { pager.animateScrollToPage(it) } },
        modifier = Modifier.padding(horizontal = 12.dp),
    )
    Text(
        S.slKosten(Fmt.pct(kosten.spreadBps / 200.0, 2), reihe.mitDividenden), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 10.dp),
    )
    reihe.fruehBis?.let { tag -> HinweisInfo(forschungText("btc.hinweis", Fmt.date(tag * 86_400_000L + 43_200_000L))) }
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
                    if (teil == null) ToolNote(S.slZuWenig) else Column { ZeitErgebnis(teil.first, teil.second, reihe.taktik, gewaehlt, onRegel, familie, onFamilie, aus, onUmschalten, kosten, offen, wahl) }
                }
            }
        } else {
            val ab = if (jahre == 0) 0 else StrategieLabor.abIndex(k, k.last().t - jahre * JAHR_MS)
            // Die Seite des Pagers stapelt wie eine Box: ohne Spalte laegen alle Teile uebereinander
            Column { ZeitErgebnis(k, ab, reihe.taktik, gewaehlt, onRegel, familie, onFamilie, aus, onUmschalten, kosten, offen, wahl) }
        }
    }
    PruefAbschnitt(S.slWf, S.slWfText, offen, "walkforward", taktikText("pa.wf")) {
        WalkForwardInhalt(symbol, k, kosten, reihe.mitDividenden, reihe.taktik, reihe.kennung)
    }
    if (reihe.fruehBis != null) {
        QuellenAnhang(listOf(QuelleLink(de.troetenrepublik.app.engine.BtcFrueh.QUELLE_NAME, de.troetenrepublik.app.engine.BtcFrueh.QUELLE_ADRESSE)))
    }
    StrategieQuellen()
    if (dialog) ZeitraumDialog(eigenVon, eigenBis, onDismiss = { dialog = false }) { von, bis ->
        eigenVon = minOf(von, bis)
        eigenBis = maxOf(von, bis)
        dialog = false
    }
}

// [0.78] Zusatz zur Regel 16-Wochen-Rhythmus: Stelle im Zyklus heute, naechste Wochen 13 bis 15 und der Rang dieser
// Wochen unter allen Dreiwochenfenstern, im Zeitraum und in seinen beiden Haelften (ab rund zehn Jahren).
@Composable
private fun RhythmusZusatz(k: List<Candle>, ab: Int) {
    val heute = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay()
    val stelle = Rhythmus16.woche(heute)
    val beginn = Rhythmus16.beginnGuteWochen(heute)
    // Mittag, damit der Tag in jeder Zeitzone derselbe bleibt
    fun tag(t: Long) = Fmt.date(t * 86_400_000L + 43_200_000L)
    KeyValueRow(S.ml("rk.heute"), S.ml("rk.von", listOf(stelle.toString())))
    if (stelle in Rhythmus16.INVESTIERT) KeyValueRow(S.ml("rk.ende"), tag(beginn + 20)) else KeyValueRow(S.ml("rk.beginn"), tag(beginn))
    val p = remember(k, ab) { Rhythmus16.pruefen(k.drop(ab).map { Math.floorDiv(it.t, 86_400_000L) to it.close }, heute) } ?: return
    KeyValueRow(S.ml("rk.rang"), S.ml("rk.von", listOf(p.rang.toString())))
    p.rangFrueh?.let { KeyValueRow(S.ml("rk.rang.frueh"), S.ml("rk.von", listOf(it.toString()))) }
    p.rangSpaet?.let { KeyValueRow(S.ml("rk.rang.spaet"), S.ml("rk.von", listOf(it.toString()))) }
    Text(S.ml("rh.achse"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp))
    WochenBalken(p.gesamt.jeWoche)
    HinweisInfo(S.ml("rk.regel"))
}

@Composable
private fun ZeitErgebnis(
    k: List<Candle>,
    ab: Int,
    taktik: Taktik.Reihen?,
    gewaehlt: StrategieLabor.Regel, onRegel: (StrategieLabor.Regel) -> Unit,
    familie: StrategieLabor.Familie, onFamilie: (StrategieLabor.Familie) -> Unit,
    aus: Set<StrategieLabor.Regel>, onUmschalten: (StrategieLabor.Regel) -> Unit,
    kosten: StrategieLabor.Kosten,
    offen: SnapshotStateMap<String, Boolean>,
    wahl: LaborWahl,
) {
    val signal = LocalSignal.current
    val berichte by produceState<List<StrategieLabor.Bericht>?>(null, k, ab, kosten, taktik) {
        value = null
        value = withContext(Dispatchers.Default) { StrategieLabor.pruefen(laborDaten(k, taktik), ab, kosten) }
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
    // Die Zusatzregeln stehen eingeklappt in einer Zeile; sichtbar bleibt, was eine Linie hat oder unten gewaehlt ist
    val mehrOffen = offen["mehrRegeln"] == true
    val zusatz = liste.count { it.regel in StrategieLabor.ZUSATZ }
    liste.filter { mehrOffen || it.regel !in StrategieLabor.ZUSATZ || it.regel !in aus || it.regel == gewaehlt }.forEach { b ->
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

    if (zusatz > 0) Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = if (mehrOffen) S.less else S.more) { offen["mehrRegeln"] = !mehrOffen }
            .padding(start = 20.dp, end = 20.dp).heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(S.slMehrRegeln(zusatz), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        AufklappPfeil(mehrOffen)
    }

    // Die gewaehlte Regel im Einzelnen, verglichen mit Kaufen und Halten. Mit Trendfilter, Monatsende oder eigenem
    // Ausstieg wird sie neu gerechnet; bis das Ergebnis da ist, steht die Regel aus der Uebersicht.
    val basis = liste.firstOrNull { it.regel == gewaehlt } ?: liste[1.coerceAtMost(liste.size - 1)]
    val istHalten = basis.regel.art == StrategieLabor.Art.HALTEN
    val regel = StrategieLabor.variante(basis.regel, wahl.trendfilter, wahl.monatsende)
    // Ohne Verkaufssignal (Kaufen und Halten, Zielvolatilitaet) gibt es weder einen eigenen Ausstieg noch etwas zu verschieben
    val ohneSignal = StrategieLabor.ohneSignal(basis.regel)
    val ausstieg = if (ohneSignal) StrategieLabor.REGELAUSSTIEG else wahl.ausstieg
    val eigen = regel != basis.regel || ausstieg != StrategieLabor.REGELAUSSTIEG
    val gerechnet by produceState<StrategieLabor.Bericht?>(null, k, ab, kosten, regel, ausstieg, taktik) {
        value = null
        if (eigen) value = withContext(Dispatchers.Default) { StrategieLabor.pruefen(laborDaten(k, taktik), ab, kosten, listOf(regel), ausstieg).firstOrNull() }
    }
    val b = if (eigen) gerechnet ?: basis else basis
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
            RegelGlockeKnopf(b.regel)
        }
        androidx.compose.material3.DropdownMenu(
            expanded = regelMenue, onDismissRequest = { regelMenue = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            // Nach Handelsstil geordnet, je Gruppe eine Kopfzeile mit Stil und ueblicher Haltedauer
            remember(liste) { Handelsstil.gruppen(liste) { it.regel.art } }.forEach { (stil, regeln) ->
                Text(
                    S.st("hs.gruppe", listOf(S.st("hs." + stil.name), S.st("hs.dauer." + stil.name))),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp).padding(top = 10.dp, bottom = 2.dp),
                )
                regeln.forEach { r ->
                    DropdownMenuItem(
                        text = { Text(regelName(r.regel)) },
                        leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(regelFarbe(r.regel))) },
                        trailingIcon = if (r.regel == basis.regel) { { CheckMark(size = 15.dp) } } else null,
                        onClick = { onRegel(r.regel); regelMenue = false },
                    )
                }
            }
        }
    }
    when (basis.regel.art) {
        StrategieLabor.Art.RSI, StrategieLabor.Art.BOLLINGER -> SchalterZeile(S.slTrendfilter, wahl.trendfilter) { wahl.trendfilter = it }
        StrategieLabor.Art.SMA -> SchalterZeile(S.slMonatsende, wahl.monatsende) { wahl.monatsende = it }
        else -> Unit
    }
    if (!ohneSignal) {
        var ausstiegMenue by remember { mutableStateOf(false) }
        Box {
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { ausstiegMenue = true }.padding(horizontal = 20.dp).heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(S.slAusstieg, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Text(ausstiegName(ausstieg), style = MaterialTheme.typography.bodyLarge, maxLines = 1, softWrap = false)
                Spacer(Modifier.width(8.dp))
                ChevronDown(width = 12.dp)
            }
            androidx.compose.material3.DropdownMenu(
                expanded = ausstiegMenue, onDismissRequest = { ausstiegMenue = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                StrategieLabor.AUSSTIEGE.forEach { a ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(ausstiegName(a)) },
                        trailingIcon = if (a == ausstieg) { { CheckMark(size = 15.dp) } } else null,
                        onClick = { wahl.ausstieg = a; ausstiegMenue = false },
                    )
                }
            }
        }
    }
    // Sparplan (0.78): Monatsrate statt Einmalanlage, eigene Grafik und Kennzahlen statt der folgenden
    SchalterZeile(S.st("sp.schalter"), wahl.sparplan) { wahl.sparplan = it }
    if (wahl.sparplan) SparplanBlock(k, ab, kosten, regel, taktik) else {
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
    val g = b.gesamt
    val hg = halten.gesamt
    // Kennzahlen je Geschaeft, gleiche Begriffe und Rechnung wie bei den eigenen Verkaeufen; ein Tipp erklaert sie
    val kz = remember(g) { StrategieLabor.kennzahlen(g.geschaefte) }
    val kh = remember(hg) { StrategieLabor.kennzahlen(hg.geschaefte) }
    var kennzahlenInfo by remember { mutableStateOf(false) }
    var trefferInfo by remember { mutableStateOf(false) }
    val zeigen = { kennzahlenInfo = true }
    fun zahl(v: Double?) = v?.let { Fmt.num(it, 2) } ?: "–"
    val grenze = b.pruef.verlauf.first().first
    val sig = LocalSignal.current
    // Vergleichstabelle: Spalten mit Farbpunkt wie im Diagramm, je Kennzahl eine Zeile
    val spalten = listOfNotNull(if (istHalten) null else VergleichSpalte(S.slDieRegel, farbeRegel), VergleichSpalte(S.slHalten, farbeHalten))
    // vz: Renditen der beiden Spalten, die nach Vorzeichen gefaerbt werden (in allen Renditezeilen gleich)
    @Composable
    fun zeile(label: String, regelW: String, h: String, onClick: (() -> Unit)? = null, hint: String? = null, vz: Pair<Double?, Double?>? = null): VergleichZeile {
        val f = vz?.let { renditeFarben(it.first, it.second) } ?: emptyList()
        return VergleichZeile(label, if (istHalten) listOf(h) else listOf(regelW, h), if (istHalten) f.drop(1) else f, hint, onClick)
    }
    VergleichTabelle(
        spalten,
        listOf(
            zeile(S.slEndwert, Fmt.euro(g.endwert, 0), Fmt.euro(hg.endwert, 0)),
            zeile(S.ptPerYear, proJahr(g.renditeJahr), proJahr(hg.renditeJahr), vz = g.renditeJahr to hg.renditeJahr),
            zeile(S.copyMaxDrawdown, "−" + Fmt.pct(g.maxRueckgang * 100, 1), "−" + Fmt.pct(hg.maxRueckgang * 100, 1)),
            zeile(S.calTrades, g.trades.toString(), hg.trades.toString()),
            zeile(S.slTreffer, g.treffer?.let { Fmt.pct(it * 100, 0) } ?: "–", hg.treffer?.let { Fmt.pct(it * 100, 0) } ?: "–", { trefferInfo = true }),
            zeile(S.slGv, zahl(kz?.verhaeltnis), zahl(kh?.verhaeltnis), zeigen),
            zeile(S.slErwartung, kz?.let { Fmt.signedPct(it.bilanz.erwartung * 100, 1) } ?: "–", kh?.let { Fmt.signedPct(it.bilanz.erwartung * 100, 1) } ?: "–", zeigen),
            zeile(S.slGewinnfaktor, zahl(kz?.bilanz?.gewinnfaktor), zahl(kh?.bilanz?.gewinnfaktor), zeigen),
            zeile(S.slHaltedauer, kz?.let { Fmt.num(it.haltedauer, 0) } ?: "–", kh?.let { Fmt.num(it.haltedauer, 0) } ?: "–", zeigen),
            zeile(S.slVerlustserie, kz?.verlustserie?.toString() ?: "–", kh?.verlustserie?.toString() ?: "–", zeigen),
            zeile(S.slImMarkt, Fmt.pct(g.imMarkt * 100, 0), Fmt.pct(hg.imMarkt * 100, 0)),
            // Lern- und Pruefzeitraum getrennt, das Datum als kleine Unterzeile
            zeile(taktikText("vt.lern"), proJahr(b.lern.renditeJahr), proJahr(halten.lern.renditeJahr), hint = taktikText("vt.lern.hint", Fmt.date(grenze)), vz = b.lern.renditeJahr to halten.lern.renditeJahr),
            zeile(
                taktikText("vt.pruef"), proJahr(b.pruef.renditeJahr), proJahr(halten.pruef.renditeJahr), hint = taktikText("vt.pruef.hint", Fmt.date(grenze)),
                vz = b.pruef.renditeJahr to halten.pruef.renditeJahr,
            ),
        ),
        info = S.slLernText, infoTitel = taktikText("vt.info.titel"),
    )
    if (kennzahlenInfo) MarktInfoBlatt(S.slKennzahlen, S.slKennzahlenText) { kennzahlenInfo = false }
    if (trefferInfo) MarktInfoBlatt(S.slTreffer, taktikText("vt.treffer")) { trefferInfo = false }
    // Handelsstil der Regel nach ihrer mittleren Haltedauer im Test, darunter die uebliche Haltedauer des Stils
    val handelsstil = remember(b.regel.art, g) { Handelsstil.von(b.regel.art, g) }
    KeyValueRow(S.st("hs.zeile"), S.st("hs." + handelsstil.name), hint = S.st("hs.dauer." + handelsstil.name))
    if (!istHalten) {
        AussageZeile(taktikText("vt.aussage.gesamt"), b.gesamt)
        AussageZeile(S.slAussagePruef, b.pruef)
        MindestlaufzeitZeile(b.gesamt)
    }
    }
    if (b.regel.art == StrategieLabor.Art.RHYTHMUS) RhythmusZusatz(taktik?.signal?.subList(0, k.size) ?: k, ab)
    HinweisInfo(S.st("hs.erklaerung"))
    S.slRegelInfo(b.regel.art.name).takeIf { it.isNotEmpty() }?.let { ToolNote(it) }
    if (ausstieg != StrategieLabor.REGELAUSSTIEG) HinweisInfo(S.slAusstiegHinweis(ausstieg.art.name, ausstieg.wert, StrategieLabor.ZIEL_STOPP))

    // Die Pruefungen darunter sind zugeklappt, ihre Erklaerung steht beim Aufklappen als erste Zeile im Abschnitt.
    // Zufallstest: die Familien als wischbare Seiten mit mitlaufenden Reitern (5.94). Jede Seite behaelt ihr Ergebnis,
    // und waehrend der Rechnung bleibt der Platz stehen, damit der Bildschirm beim Wechsel nicht springt.
    val speicher = remember(k, ab, kosten, taktik) { LotterieSpeicher(k, ab, kosten, taktik) }
    PruefAbschnitt(S.slAndere, S.slAndereText, offen, "anderewerte", taktikText("pa.anderewerte")) {
        if (istHalten) ToolNote(S.slZufallRegelWaehlen) else if (taktik != null) ToolNote(taktikText("tk.andere")) else AndereWerte(wahl, regel, ausstieg, k[ab].t, k.last().t, kosten)
    }
    PruefAbschnitt(S.slLotterie, S.slLotterieText + "\n\n" + S.slDsrText, offen, "lotterie", taktikText("pa.lotterie")) {
        val familien = StrategieLabor.Familie.entries
        val lpager = rememberPagerState(initialPage = familien.indexOf(familie).coerceAtLeast(0)) { familien.size }
        val lscope = rememberCoroutineScope()
        LaunchedEffect(lpager.currentPage) { onFamilie(familien[lpager.currentPage]) }
        // Die Parameter-Landschaft darunter teilt sich die Familienwahl
        LaunchedEffect(familie) { familien.indexOf(familie).let { if (it >= 0 && lpager.currentPage != it) lpager.scrollToPage(it) } }
        MitlaufendeReiter(familien.size, lpager, { S.slFamilie(familien[it].name) }) { lscope.launch { lpager.animateScrollToPage(it) } }
        HorizontalPager(state = lpager, verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().nestedScroll(WischenBehalten).mausWischen(lpager)) { i ->
            Column(Modifier.fillMaxWidth().heightIn(min = 340.dp)) { LotterieSeite(speicher, familien[i]) }
        }
    }
    PruefAbschnitt(S.slZufall, zufallInfo(), offen, "zufall", taktikText("pa.zufall")) {
        if (ohneSignal) ToolNote(S.slZufallRegelWaehlen) else { ZufallspfadeBlock(k, ab, kosten, regel, ausstieg, offen, taktik); ZufallBlock(k, ab, kosten, regel, taktik) }
    }
    PruefAbschnitt(S.st("sf.titel"), S.st("sf.text"), offen, "startfenster", taktikText("pa.startfenster")) { StartfensterBlock(k, kosten, regel, ausstieg, wahl.sparplan, taktik) }
    PruefAbschnitt(S.slParam, S.slParamText, offen, "parameter", taktikText("pa.parameter")) {
        var pruefzeitraum by remember { mutableStateOf(false) }
        ToolChoice(null, StrategieLabor.Familie.entries.map { S.slFamilie(it.name) to it }, familie) { onFamilie(it) }
        ToolChoice(null, listOf(S.slParamLern to false, S.slParamPruef to true), pruefzeitraum) { pruefzeitraum = it }
        if (familie == StrategieLabor.Familie.ALLE) {
            Unit
        } else {
            LaunchedEffect(speicher, familie) {
                if (speicher.ergebnisse.containsKey(familie)) return@LaunchedEffect
                delay(300)
                speicher.holen(familie)
            }
            val liste2 = speicher.ergebnisse[familie]
            when {
                liste2 == null -> ToolNote(S.slLotterieLaeuft)
                liste2.isEmpty() -> ToolNote(S.slZuWenig)
                else -> ParameterKarte(liste2.first(), familie, pruefzeitraum)
            }
        }
    }
}

// Dieselbe Regel mit demselben Ausstieg und Zeitraum auf den anderen Werten aus Depot und Watchlist, je Wert einzeln:
// oben, bei wie vielen sie Kaufen und Halten schlaegt, darunter der Vorsprung je Wert in Prozentpunkten pro Jahr. Die
// Kurse kommen nacheinander und erst, wenn der Abschnitt offen ist.
@Composable
private fun AndereWerte(wahl: LaborWahl, regel: StrategieLabor.Regel, ausstieg: StrategieLabor.Ausstieg, vonMs: Long, bisMs: Long, kosten: StrategieLabor.Kosten) {
    val signal = LocalSignal.current
    val konto by wahl.state.account.collectAsStateWithLifecycle()
    val symbole = remember(konto.positions, konto.watchlist, wahl.symbol) {
        StrategieLabor.andereWerte(konto.positions.filter { !it.isDerivative }.map { it.symbol }, konto.watchlist, wahl.symbol)
    }
    if (symbole.isEmpty()) { ToolNote(S.slAndereKeine); return }
    val zeilen = remember(symbole, regel, ausstieg, vonMs, bisMs, kosten) { mutableStateListOf<Pair<String, Double>>() }
    var fertig by remember(symbole, regel, ausstieg, vonMs, bisMs, kosten) { mutableStateOf(false) }
    LaunchedEffect(symbole, regel, ausstieg, vonMs, bisMs, kosten) {
        // Kurz warten: schnelles Umschalten startet keine Abrufe
        delay(300)
        for (s in symbole) {
            val kerzen = wahl.reihen[s] ?: ladeReihe(wahl.state, s).kerzen.also { if (it.size >= 300) wahl.reihen[s] = it }
            val v = withContext(Dispatchers.Default) { StrategieLabor.vorsprung(kerzen, vonMs, bisMs, regel, ausstieg, kosten) }
            if (v != null) zeilen += s to v
        }
        fertig = true
    }
    when {
        !fertig -> ToolNote(S.toolLoading)
        zeilen.isEmpty() -> { ToolNote(S.slAndereKeine); return }
        else -> Text(
            S.slAndereKern(StrategieLabor.schlaegtHalten(zeilen.map { it.second }), zeilen.size), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 4.dp),
        )
    }
    if (zeilen.isNotEmpty()) Text(
        S.slAndereSpalte, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 6.dp),
    )
    // Vorsprung je Wert als mittiger Balken (rechts vor Kaufen und Halten, links dahinter), Wert rechts in fester Spalte
    val skala = (zeilen.maxOfOrNull { kotlin.math.abs(it.second) } ?: 0.0).times(100).coerceAtLeast(1.0)
    zeilen.forEach { (s, v) ->
        AbweichungsZeile(Symbols.name(s), null, v * 100, skala, Fmt.signedPunkte(v * 100, 1), signal.forChange(v))
    }
}

// Rechnet die Lotterie je Familie hoechstens einmal; Rechnungen laufen nacheinander, damit ein Handy nicht mehrere
// gleichzeitig stemmt. Eine leere Liste heisst: zu wenige Kurse.
private class LotterieSpeicher(val k: List<Candle>, val ab: Int, val kosten: StrategieLabor.Kosten, val taktik: Taktik.Reihen? = null) {
    val ergebnisse = mutableStateMapOf<StrategieLabor.Familie, List<StrategieLabor.Lotterie>>()
    private val sperre = Mutex()

    suspend fun holen(fam: StrategieLabor.Familie) {
        if (ergebnisse.containsKey(fam)) return
        sperre.withLock {
            if (ergebnisse.containsKey(fam)) return
            ergebnisse[fam] = withContext(Dispatchers.Default) { listOfNotNull(StrategieLabor.lotterie(laborDaten(k, taktik), ab, kosten, fam) { ensureActive() }) }
        }
    }
}

// Verteilung der um zufaellige Tage verschobenen Regel und die Lage der echten Regel darin.
@Composable
private fun ZufallBlock(k: List<Candle>, ab: Int, kosten: StrategieLabor.Kosten, regel: StrategieLabor.Regel, taktik: Taktik.Reihen?) {
    val ergebnis by produceState<List<StrategieLabor.Zufallsvergleich>?>(null, k, ab, kosten, regel, taktik) {
        value = null
        // Kurz warten: schnelles Umschalten zwischen Regeln startet keine Berechnung
        delay(300)
        value = withContext(Dispatchers.Default) {
            val d = laborDaten(k, taktik)
            listOfNotNull(StrategieLabor.zufallsvergleich(d, StrategieLabor.ziel(d, regel), ab, k.size, kosten) { ensureActive() })
        }
    }
    val liste = ergebnis ?: run { ToolNote(S.slLotterieLaeuft); return }
    if (liste.isEmpty()) { ToolNote(S.slZuWenig); return }
    val z = liste.first()
    Text(
        regelName(regel), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp),
    )
    val marken = listOfNotNull(
        HistMarke(z.regelJahr, S.slDieRegel, regelFarbe(regel)),
        z.haltenJahr?.let { HistMarke(it, S.slHaltenKurz, regelFarbe(StrategieLabor.REGELN.first())) },
    )
    Histogramm(
        z.verteilung, marken, { Fmt.signedPct(it * 100, 0) }, S.slZufall,
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
    KeyValueRow(S.slZufallAnteil, Fmt.pct(z.anteilBesser * 100, 0))
    Text(
        S.slZufallErgebnis(((1.0 - z.anteilBesser) * 100).roundToInt()),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp),
    )
}

// Zeile Aussagekraft: die Stufe als Wert in ihrer Farbe, darunter nur die Fakten (Geschaefte und Jahre); ein Tipp zeigt die Grenzen.
@Composable
internal fun AussageZeile(label: String, e: StrategieLabor.Ergebnis) {
    val signal = LocalSignal.current
    val stufe = StrategieLabor.aussage(e)
    val jahre = Fmt.num(e.jahre, 1)
    var info by remember { mutableStateOf(false) }
    val farbe = when (stufe) {
        StrategieLabor.Aussage.ZU_WENIG -> signal.warn
        StrategieLabor.Aussage.BEGRENZT -> MaterialTheme.colorScheme.onSurfaceVariant
        StrategieLabor.Aussage.BRAUCHBAR -> signal.up
    }
    KeyValueRow(label, S.slAussageStufe(stufe.name), valueColor = farbe, onClick = { info = true }, hint = taktikText("vt.fakten", e.trades.toString(), jahre))
    if (info) MarktInfoBlatt(S.slAussage, S.slAussageText(e.trades, jahre)) { info = false }
}

// Aufklappbarer Abschnitt einer Pruefung, kompakt wie die uebrigen eingeklappten Abschnitte: Titel, darunter eine
// Zeile mit der Kernaussage, rechts der Pfeil. Die Erklaerung steht beim Aufklappen als erste Zeile im Abschnitt.
// Der Zustand liegt ausserhalb, damit er beim Wischen zwischen den Zeitraeumen erhalten bleibt.
@Composable
internal fun PruefAbschnitt(titel: String, info: String, offen: SnapshotStateMap<String, Boolean>, schluessel: String, kurz: String = "", inhalt: @Composable () -> Unit) {
    val auf = offen[schluessel] == true
    EinklappKopf(titel, kurz, auf) { offen[schluessel] = !auf }
    if (auf) {
        if (info.isNotBlank()) ToolNote(info)
        inhalt()
    }
}

@Composable
private fun LotterieSeite(speicher: LotterieSpeicher, fam: StrategieLabor.Familie) {
    val signal = LocalSignal.current
    LaunchedEffect(speicher, fam) {
        if (speicher.ergebnisse.containsKey(fam)) return@LaunchedEffect
        // Kurz warten: schnelles Wischen ueber mehrere Seiten startet keine Berechnung
        delay(300)
        speicher.holen(fam)
    }
    val liste2 = speicher.ergebnisse[fam] ?: run { ToolNote(S.slLotterieLaeuft); return }
    if (liste2.isEmpty()) { ToolNote(S.slZuWenig); return }
    val l = liste2.first()
    Spacer(Modifier.height(6.dp))
    KeyValueRow(S.slVarianten, l.anzahl.toString())
    KeyValueRow(S.slBeste, regelName(l.beste))
    VergleichTabelle(
        listOf(VergleichSpalte(taktikText("vt.beste"), regelFarbe(l.beste)), VergleichSpalte(S.slHaltenKurz, regelFarbe(StrategieLabor.REGELN.first()))),
        listOf(
            VergleichZeile(
                taktikText("vt.lern"), listOf(proJahr(l.besteLern.renditeJahr), proJahr(l.haltenLern.renditeJahr)),
                renditeFarben(l.besteLern.renditeJahr, l.haltenLern.renditeJahr),
            ),
            VergleichZeile(
                taktikText("vt.pruef"), listOf(proJahr(l.bestePruef.renditeJahr), proJahr(l.haltenPruef.renditeJahr)),
                renditeFarben(l.bestePruef.renditeJahr, l.haltenPruef.renditeJahr),
            ),
        ),
    )
    KeyValueRow(S.slDsr, Fmt.pct(l.dsr * 100, 0))
    UeberanpassungZeilen(speicher.k, speicher.ab, speicher.kosten, fam)
    AussageZeile(S.slAussagePruef, l.bestePruef)
    Text(
        S.slLotterieErgebnis(l.bestePruef.endwert >= l.haltenPruef.endwert, l.dsr >= 0.95, l.anzahl, Fmt.pct(l.dsr * 100, 0)),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp),
    )
}
