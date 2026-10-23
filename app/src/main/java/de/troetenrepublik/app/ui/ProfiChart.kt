package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Range
import de.troetenrepublik.app.data.Series
import de.troetenrepublik.app.engine.ChartIndikatoren
import de.troetenrepublik.app.engine.ChartZeichnungen
import de.troetenrepublik.app.engine.Indikatoren
import de.troetenrepublik.app.engine.MarketHours
import de.troetenrepublik.app.engine.Zeichnung
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.MarketRepository
import de.troetenrepublik.app.plattform.horizontalScrollMaus
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.TreeMap
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

// Farben der Indikatorlinien (Platz wie in ChartIndikatoren.Linie.farbe).
private val LINIEN_FARBEN = listOf(
    Color(0xFFFFB300), Color(0xFF42A5F5), Color(0xFFAB47BC), Color(0xFF26C6DA), Color(0xFFEC407A), Color(0xFF8D9DFF),
    Color(0xFF9CCC65), Color(0xFFFF7043), Color(0xFFBDBDBD), Color(0xFF00C853), Color(0xFFFF5252),
)

// Farben der Vergleichswerte nach dem eigenen, der in der Farbe der Kurslinie steht.
private val VERGLEICH_FARBEN = listOf(
    Color(0xFF42A5F5), Color(0xFFAB47BC), Color(0xFFFFB300), Color(0xFF26C6DA), Color(0xFFEC407A), Color(0xFF9CCC65), Color(0xFFFF7043),
)

private val EMOJIS = listOf("🚀", "📈", "📉", "💰", "🔥", "⚠️", "✅", "❌", "👀", "💎", "🐂", "🐻", "🎯", "📌", "⭐", "🤔")

// Hoechstens so viele Werte im Vergleich, der eigene eingeschlossen.
private const val VERGLEICH_MAX = 8

private enum class Werkzeug(val key: String) { KEINES(""), LINIE(ChartZeichnungen.LINIE), WAAGERECHT(ChartZeichnungen.WAAGERECHT), NOTIZ(ChartZeichnungen.NOTIZ), EMOJI(ChartZeichnungen.EMOJI) }

// Erweiterter Chart: Linie, Kerzen oder Heikin-Ashi an jedem Handelsplatz, den Yahoo fuer den Wert kennt.
// Vergleich in Prozent seit Beginn des Zeitraums, Zeichnungen werden je Wert gemerkt.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfiChartScreen(state: AppState, symbol: String, onBack: () -> Unit) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val quotes by state.quotes.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val signal = LocalSignal.current
    val name = kurzName(symbol, quotes[symbol]?.name.orEmpty())

    var range by rememberSaveable { mutableStateOf(Range.M3) }
    var eigenVon by rememberSaveable { mutableLongStateOf(0L) }
    var eigenBis by rememberSaveable { mutableLongStateOf(0L) }
    var zeitraumWahl by remember { mutableStateOf(false) }
    val eigen = eigenVon > 0
    // Handelsplatz: das Symbol der gewaehlten Notierung; Vergleich: weitere Symbole, durch Komma getrennt
    var platz by rememberSaveable(symbol) { mutableStateOf(symbol) }
    var platzName by rememberSaveable(symbol) { mutableStateOf("") }
    var vergleichText by rememberSaveable(symbol) { mutableStateOf("") }
    val vergleich = vergleichText.split(',').filter { it.isNotBlank() }
    val art = settings.chartArt
    val aktiv = settings.chartIndikatoren.split(',').mapNotNull { ChartIndikatoren.Indikator.von(it.trim()) }.toSet()

    var werkzeug by remember { mutableStateOf(Werkzeug.KEINES) }
    var linienStart by remember { mutableStateOf<Pair<Long, Double>?>(null) }
    var emoji by remember { mutableStateOf(EMOJIS.first()) }
    var notizOrt by remember { mutableStateOf<Pair<Long, Double>?>(null) }
    var allesLoeschen by remember { mutableStateOf(false) }
    var blatt by remember { mutableStateOf("") }   // "", "indikatoren", "platz", "vergleich", "emoji"
    var scrub by remember { mutableStateOf<Int?>(null) }

    var reihe by remember(platz) { mutableStateOf<Series?>(null) }
    LaunchedEffect(platz, range, eigenVon, eigenBis) {
        reihe = null; scrub = null
        reihe = if (eigenVon > 0) state.zeitraum(platz, eigenVon, eigenBis) else state.series(platz, range)
    }
    var vergleichsReihen by remember { mutableStateOf<Map<String, Series>>(emptyMap()) }
    LaunchedEffect(vergleichText, range, eigenVon, eigenBis) {
        vergleichsReihen = coroutineScope {
            vergleich.map { s ->
                async { s to runCatching { if (eigenVon > 0) state.zeitraum(s, eigenVon, eigenBis) else state.series(s, range) }.getOrDefault(Series(emptyList())) }
            }.awaitAll().toMap()
        }
    }

    val alleZeichnungen = remember(settings.chartZeichnungen) { ChartZeichnungen.lesen(settings.chartZeichnungen) }
    val zeichnungen = alleZeichnungen[symbol].orEmpty()
    fun zeichnungenSetzen(neu: List<Zeichnung>) {
        val text = ChartZeichnungen.schreiben(ChartZeichnungen.setzen(alleZeichnungen, symbol, neu))
        scope.launch { state.settings.update { it.copy(chartZeichnungen = text) } }
    }
    fun indikatorUmschalten(i: ChartIndikatoren.Indikator) {
        val neu = if (i in aktiv) aktiv - i else aktiv + i
        val text = ChartIndikatoren.Indikator.entries.filter { it in neu }.joinToString(",") { it.key }
        scope.launch { state.settings.update { it.copy(chartIndikatoren = text) } }
    }

    val kerzen = reihe?.candles.orEmpty()
    val anzeige = remember(kerzen, art) { if (art == "heikin") Indikatoren.heikinAshi(kerzen) else kerzen }
    val tagVon = remember { { t: Long -> java.time.Instant.ofEpochMilli(t).atZone(MarketHours.zone).toLocalDate().toEpochDay() } }
    val ergebnisse = remember(kerzen, aktiv) { aktiv.associateWith { ChartIndikatoren.rechnen(it, kerzen, tagVon) } }
    val vergleichAn = vergleich.isNotEmpty()
    val prozent = remember(kerzen, vergleichsReihen, vergleichText) {
        if (!vergleichAn || kerzen.isEmpty()) null else {
            val basis = kerzen.first().close
            listOf(kerzen.map { if (basis > 0) (it.close / basis - 1) * 100 else null }) + vergleich.map { s -> prozentAuf(kerzen, vergleichsReihen[s]) }
        }
    }

    val liste = rememberScrollState()
    val dismiss = rememberSwipeDownDismiss(onBack)
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { liste.value == 0 }
            .verticalScroll(liste),
    ) {
        ScreenHeader(name, listOfNotNull(S.chartProfi, platzName.ifBlank { null }).joinToString(" · "), onBack)

        // Kurs am Fadenkreuz oder zuletzt: Eroeffnung, Hoch, Tief, Schluss und Veraenderung im Zeitraum
        val gezeigt = scrub?.let { anzeige.getOrNull(it) } ?: anzeige.lastOrNull()
        val erste = kerzen.firstOrNull()?.close ?: 0.0
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (gezeigt != null) {
                val roh = scrub?.let { kerzen.getOrNull(it) } ?: kerzen.last()
                Column(Modifier.weight(1f)) {
                    Text(Fmt.priceEuro(roh.close), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        S.chartOhlc.zip(listOf(gezeigt.open, gezeigt.high, gezeigt.low, gezeigt.close)).joinToString("  ") { (k, v) -> "$k ${preisText(v)}" },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (erste > 0) Text(
                        Fmt.signedPct((roh.close / erste - 1) * 100), style = MaterialTheme.typography.titleSmall,
                        color = if (roh.close >= erste) signal.up else signal.down,
                    )
                    Text(
                        if (eigen && scrub == null) zeitraumText(eigenVon, eigenBis) else if (range.intraday || eigen) Fmt.dateTime(roh.t) else Fmt.date(roh.t),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    )
                }
            }
        }

        RangeSelector(
            ranges = Range.entries, selected = if (eigen) null else range,
            onSelect = { range = it; eigenVon = 0L; eigenBis = 0L },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            trailing = { ZeitraumKnopf(aktiv = eigen) { zeitraumWahl = true } },
        )

        Box(Modifier.fillMaxWidth().height(340.dp).padding(start = 8.dp), contentAlignment = Alignment.Center) {
            when {
                reihe == null -> CircularProgressIndicator()
                kerzen.size < 2 -> Text(S.chartKeineDaten, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> KursFlaeche(
                    kerzen = kerzen, anzeige = anzeige, art = art,
                    ueberlagerung = ergebnisse.filterKeys { it.ort == ChartIndikatoren.Ort.KURS }.values.flatMap { it.linien },
                    prozent = prozent, eigeneFarbe = signal.line,
                    zeichnungen = if (vergleichAn) emptyList() else zeichnungen, entwurf = linienStart,
                    scrub = scrub, onScrub = { scrub = it },
                    onTipp = { t, p ->
                        // Im Vergleich (Prozent statt Kurs) wird nicht gezeichnet
                        when (if (vergleichAn) Werkzeug.KEINES else werkzeug) {
                            Werkzeug.KEINES -> scrub = null
                            Werkzeug.LINIE -> {
                                val start = linienStart
                                if (start == null) linienStart = t to p else {
                                    zeichnungenSetzen(zeichnungen + Zeichnung(ChartZeichnungen.LINIE, start.first, start.second, t, p))
                                    linienStart = null; werkzeug = Werkzeug.KEINES
                                }
                            }
                            Werkzeug.WAAGERECHT -> { zeichnungenSetzen(zeichnungen + Zeichnung(ChartZeichnungen.WAAGERECHT, t, p)); werkzeug = Werkzeug.KEINES }
                            Werkzeug.NOTIZ -> notizOrt = t to p
                            Werkzeug.EMOJI -> { zeichnungenSetzen(zeichnungen + Zeichnung(ChartZeichnungen.EMOJI, t, p, text = emoji)); werkzeug = Werkzeug.KEINES }
                        }
                    },
                    onLang = { i -> zeichnungenSetzen(zeichnungen.filterIndexed { k, _ -> k != i }) },
                    zeichnen = !vergleichAn && werkzeug != Werkzeug.KEINES,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Vergleich: Legende mit der Veraenderung jedes Werts
        if (prozent != null) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                (listOf(platz) + vergleich).forEachIndexed { i, s ->
                    val farbe = if (i == 0) signal.line else VERGLEICH_FARBEN[(i - 1) % VERGLEICH_FARBEN.size]
                    val letzter = prozent.getOrNull(i)?.lastOrNull { it != null }
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(farbe))
                        Spacer(Modifier.width(8.dp))
                        NamenText(kurzName(s, quotes[s]?.name.orEmpty()), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(letzter?.let { Fmt.signedPct(it) } ?: "–", style = MaterialTheme.typography.bodyMedium)
                        if (i > 0) Text(
                            "  ✕", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clip(CircleShape).clickable { vergleichText = (vergleich - s).joinToString(",") }.padding(4.dp),
                        )
                    }
                }
                Text(S.chartVergleichHinweis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }

        if (kerzen.size >= 2) {
            ChartIndikatoren.Indikator.entries.filter { it.ort == ChartIndikatoren.Ort.UNTEN && it in aktiv }.forEach { i ->
                val e = ergebnisse[i] ?: return@forEach
                UnterFeld(S.chartIndikatorName(i.key), e, kerzen, scrub, Modifier.fillMaxWidth().height(110.dp).padding(start = 8.dp, top = 6.dp))
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("kerzen", "linie", "heikin").forEach { a ->
                FilterPill(S.chartArt(a), active = art == a) { scope.launch { state.settings.update { it.copy(chartArt = a) } } }
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterPill(S.indButton, trailing = aktiv.size.takeIf { it > 0 }?.toString(), chevron = true) { blatt = "indikatoren" }
            FilterPill(S.chartHandelsplatz, trailing = platzName.ifBlank { null }, chevron = true) { blatt = "platz" }
            FilterPill(S.chartVergleich, trailing = vergleich.size.takeIf { it > 0 }?.let { (it + 1).toString() }, chevron = true) { blatt = "vergleich" }
        }

        if (!vergleichAn) {
            Row(
                Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(Werkzeug.LINIE, Werkzeug.WAAGERECHT, Werkzeug.NOTIZ).forEach { w ->
                    FilterPill(S.chartWerkzeug(w.key), active = werkzeug == w) {
                        werkzeug = if (werkzeug == w) Werkzeug.KEINES else w; linienStart = null
                    }
                }
                FilterPill(S.chartWerkzeug(ChartZeichnungen.EMOJI) + " " + emoji, active = werkzeug == Werkzeug.EMOJI) { blatt = "emoji" }
                if (zeichnungen.isNotEmpty()) {
                    FilterPill(S.chartWerkzeug("zurueck")) { zeichnungenSetzen(zeichnungen.dropLast(1)) }
                    FilterPill(S.chartWerkzeug("loeschen")) { allesLoeschen = true }
                }
            }
            Text(
                if (werkzeug != Werkzeug.KEINES) S.chartWerkzeugHinweis(werkzeug.key, linienStart != null) else if (zeichnungen.isNotEmpty()) S.chartLangDruecken else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        Spacer(Modifier.height(40.dp))
    }

    if (zeitraumWahl) ZeitraumDialog(eigenVon, eigenBis, onDismiss = { zeitraumWahl = false }) { von, bis ->
        eigenVon = minOf(von, bis); eigenBis = maxOf(von, bis); zeitraumWahl = false
    }
    notizOrt?.let { (t, p) ->
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { notizOrt = null },
            title = { Text(S.chartNotiz) },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it.take(80) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), shape = RoundedCornerShape(Radius.field),
                )
            },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = {
                    zeichnungenSetzen(zeichnungen + Zeichnung(ChartZeichnungen.NOTIZ, t, p, text = text.trim()))
                    notizOrt = null; werkzeug = Werkzeug.KEINES
                }) { Text(S.save) }
            },
            dismissButton = { TextButton(onClick = { notizOrt = null }) { Text(S.cancel) } },
        )
    }
    if (allesLoeschen) AlertDialog(
        onDismissRequest = { allesLoeschen = false },
        text = { Text(S.chartAllesLoeschen(name)) },
        confirmButton = { TextButton(onClick = { zeichnungenSetzen(emptyList()); allesLoeschen = false }) { Text(S.delete) } },
        dismissButton = { TextButton(onClick = { allesLoeschen = false }) { Text(S.cancel) } },
    )
    if (blatt.isNotEmpty()) ModalBottomSheet(
        onDismissRequest = { blatt = "" },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        when (blatt) {
            "indikatoren" -> IndikatorWahl(aktiv, ::indikatorUmschalten)
            "platz" -> PlatzWahl(state, symbol, platz) { p -> platz = p.symbol; platzName = if (p.symbol == symbol) "" else p.boerse; blatt = "" }
            "vergleich" -> VergleichWahl(state, listOf(platz) + vergleich) { s ->
                if (s !in vergleich && s != platz) {
                    if (vergleich.size + 1 >= VERGLEICH_MAX) state.showToast(S.chartVergleichVoll) else vergleichText = (vergleich + s).joinToString(",")
                }
                blatt = ""
            }
            else -> EmojiWahl { e -> emoji = e; werkzeug = Werkzeug.EMOJI; linienStart = null; blatt = "" }
        }
    }
}

// Werte einer anderen Reihe auf die Zeitpunkte von kerzen gelegt, in Prozent seit ihrem ersten Wert im Zeitraum.
private fun prozentAuf(kerzen: List<Candle>, andere: Series?): List<Double?> {
    val k = andere?.candles.orEmpty()
    if (k.isEmpty()) return kerzen.map { null }
    val nachZeit = TreeMap<Long, Double>().apply { k.forEach { put(it.t, it.close) } }
    val werte = kerzen.map { c -> nachZeit.floorEntry(c.t)?.value }
    val basis = werte.firstOrNull { it != null && it > 0 } ?: return kerzen.map { null }
    return werte.map { v -> v?.let { (it / basis - 1) * 100 } }
}

// Kurs fuer die Achse und die Kopfzeile: mehr Stellen bei kleinen Kursen.
private fun preisText(v: Double): String = Fmt.num(v, when { abs(v) >= 1000 -> 0; abs(v) >= 1 -> 2; else -> 4 })

// Runde Schrittweite fuer etwa anzahl Hilfslinien zwischen lo und hi.
private fun schritt(lo: Double, hi: Double, anzahl: Int = 4): Double {
    val roh = (hi - lo) / anzahl
    if (!(roh > 0)) return 1.0
    val zehner = 10.0.pow(floor(log10(roh)))
    val f = roh / zehner
    return zehner * when { f < 1.5 -> 1.0; f < 2.25 -> 2.0; f < 3.5 -> 2.5; f < 7.5 -> 5.0; else -> 10.0 }
}

// Kerzen in gleichem Abstand, Luecken wie Nacht und Wochenende fallen weg, die Zeit dazwischen gleitet.
private class Achse(val kerzen: List<Candle>, val breite: Float) {
    val n = kerzen.size
    val schritt = if (n > 0) breite / n else breite
    fun x(i: Int): Float = (i + 0.5f) * schritt
    fun index(x: Float): Int = (x / schritt).toInt().coerceIn(0, (n - 1).coerceAtLeast(0))
    fun xVon(t: Long): Float {
        if (n == 0) return 0f
        if (n == 1) return x(0)
        if (t <= kerzen[0].t) return x(0) + (t - kerzen[0].t).toFloat() / (kerzen[1].t - kerzen[0].t).coerceAtLeast(1) * schritt
        if (t >= kerzen[n - 1].t) return x(n - 1) + (t - kerzen[n - 1].t).toFloat() / (kerzen[n - 1].t - kerzen[n - 2].t).coerceAtLeast(1) * schritt
        var lo = 0
        var hi = n - 1
        while (hi - lo > 1) { val m = (lo + hi) ushr 1; if (kerzen[m].t <= t) lo = m else hi = m }
        return x(lo) + (t - kerzen[lo].t).toFloat() / (kerzen[hi].t - kerzen[lo].t).coerceAtLeast(1) * schritt
    }
}

private class Skala(val lo: Double, val hi: Double, val oben: Float, val hoehe: Float) {
    fun y(v: Double): Float = oben + ((hi - v) / (hi - lo)).toFloat() * hoehe
    fun wert(y: Float): Double = hi - (y - oben) / hoehe * (hi - lo)
}

// Spanne aller Werte mit etwas Luft oben und unten.
private fun spanne(werte: Sequence<Double>, luft: Double = 0.06): Pair<Double, Double> {
    var lo = Double.MAX_VALUE
    var hi = -Double.MAX_VALUE
    for (v in werte) if (v.isFinite()) { if (v < lo) lo = v; if (v > hi) hi = v }
    if (lo > hi) return 0.0 to 1.0
    if (hi - lo < 1e-9) { val m = abs(hi).coerceAtLeast(1.0) * 0.01; return lo - m to hi + m }
    val d = (hi - lo) * luft
    return lo - d to hi + d
}

private fun DrawScope.linie(werte: List<Double?>, achse: Achse, skala: Skala, farbe: Color, breite: Float) {
    val pfad = Path()
    var offen = false
    werte.forEachIndexed { i, v ->
        if (v == null || !v.isFinite()) { offen = false; return@forEachIndexed }
        val x = achse.x(i); val y = skala.y(v)
        if (offen) pfad.lineTo(x, y) else { pfad.moveTo(x, y); offen = true }
    }
    drawPath(pfad, farbe, style = Stroke(width = breite))
}

private fun DrawScope.hilfslinien(skala: Skala, breite: Float, messer: TextMeasurer, farbe: Color, text: (Double) -> String) {
    val st = schritt(skala.lo, skala.hi)
    var v = kotlin.math.ceil(skala.lo / st) * st
    val stil = TextStyle(fontSize = 10.sp)
    while (v <= skala.hi) {
        val y = skala.y(v)
        drawLine(farbe.copy(alpha = 0.18f), Offset(0f, y), Offset(breite, y), strokeWidth = 1f)
        val t = messer.measure(text(v), stil)
        drawText(t, color = farbe, topLeft = Offset(breite + 4 * density, y - t.size.height / 2f))
        v += st
    }
}

@Composable
private fun KursFlaeche(
    kerzen: List<Candle>,
    anzeige: List<Candle>,
    art: String,
    ueberlagerung: List<ChartIndikatoren.Linie>,
    prozent: List<List<Double?>>?,
    eigeneFarbe: Color,
    zeichnungen: List<Zeichnung>,
    entwurf: Pair<Long, Double>?,
    scrub: Int?,
    onScrub: (Int?) -> Unit,
    onTipp: (Long, Double) -> Unit,
    onLang: (Int) -> Unit,
    zeichnen: Boolean,
    modifier: Modifier,
) {
    val signal = LocalSignal.current
    val messer = rememberTextMeasurer()
    val achsenFarbe = MaterialTheme.colorScheme.onSurfaceVariant
    val notizGrund = MaterialTheme.colorScheme.surfaceContainerHighest
    val notizSchrift = MaterialTheme.colorScheme.onSurface
    val dichte = LocalDensity.current
    val tipp by rememberUpdatedState(onTipp)
    val lang by rememberUpdatedState(onLang)
    val wisch by rememberUpdatedState(onScrub)
    val aktuelleZeichnungen by rememberUpdatedState(zeichnungen)
    BoxWithConstraints(modifier) {
        val rechts = with(dichte) { 52.dp.toPx() }
        val unten = with(dichte) { 18.dp.toPx() }
        val breite = (constraints.maxWidth - rechts).coerceAtLeast(1f)
        val hoehe = (constraints.maxHeight - unten).coerceAtLeast(1f)
        val achse = remember(kerzen, breite) { Achse(kerzen, breite) }
        val skala = remember(anzeige, ueberlagerung, prozent, zeichnungen, art, hoehe) {
            val werte = if (prozent != null) prozent.asSequence().flatten().filterNotNull()
            else (if (art == "linie") anzeige.asSequence().map { it.close } else anzeige.asSequence().flatMap { sequenceOf(it.high, it.low) }) +
                ueberlagerung.asSequence().flatMap { it.werte.asSequence() }.filterNotNull()
            val (lo, hi) = spanne(werte)
            Skala(lo, hi, 0f, hoehe)
        }
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(achse, skala, zeichnen) {
                    detectTapGestures(
                        onTap = { p -> if (p.x <= breite && p.y <= hoehe) tipp(kerzen[achse.index(p.x)].t, skala.wert(p.y)) },
                        onLongPress = { p ->
                            // Naechste Zeichnung im Umkreis von 28 dp entfernen
                            val grenze = 28 * density
                            var beste = -1
                            var abstand = Float.MAX_VALUE
                            aktuelleZeichnungen.forEachIndexed { i, z ->
                                val d = when (z.art) {
                                    ChartZeichnungen.WAAGERECHT -> abs(p.y - skala.y(z.p1))
                                    ChartZeichnungen.LINIE -> strecke(p, Offset(achse.xVon(z.t1), skala.y(z.p1)), Offset(achse.xVon(z.t2), skala.y(z.p2)))
                                    else -> (p - Offset(achse.xVon(z.t1), skala.y(z.p1))).getDistance()
                                }
                                if (d < abstand) { abstand = d; beste = i }
                            }
                            if (beste >= 0 && abstand <= grenze) lang(beste)
                        },
                    )
                }
                .pointerInput(achse, zeichnen) {
                    if (zeichnen) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { wisch(achse.index(it.x)) },
                        onDragEnd = { wisch(null) },
                        onDragCancel = { wisch(null) },
                    ) { change, _ -> wisch(achse.index(change.position.x)) }
                },
        ) {
            hilfslinien(skala, breite, messer, achsenFarbe) { v -> if (prozent != null) Fmt.signedPct(v, if (abs(skala.hi - skala.lo) < 10) 1 else 0) else preisText(v) }
            clipRect(0f, 0f, breite, hoehe) {
                if (prozent != null) {
                    if (skala.lo < 0 && skala.hi > 0) drawLine(achsenFarbe.copy(alpha = 0.5f), Offset(0f, skala.y(0.0)), Offset(breite, skala.y(0.0)), 1.5f)
                    prozent.forEachIndexed { i, werte ->
                        linie(werte, achse, skala, if (i == 0) eigeneFarbe else VERGLEICH_FARBEN[(i - 1) % VERGLEICH_FARBEN.size], (if (i == 0) 2.5f else 2f) * density)
                    }
                } else {
                    if (art == "linie") linie(anzeige.map { it.close }, achse, skala, eigeneFarbe, 2.5f * density)
                    else {
                        val koerper = (achse.schritt * 0.7f).coerceAtLeast(1f)
                        anzeige.forEachIndexed { i, c ->
                            val farbe = if (c.close >= c.open) signal.up else signal.down
                            val x = achse.x(i)
                            drawLine(farbe, Offset(x, skala.y(c.high)), Offset(x, skala.y(c.low)), strokeWidth = maxOf(1f, density * 0.8f))
                            val o = skala.y(maxOf(c.open, c.close))
                            val u = skala.y(minOf(c.open, c.close))
                            drawRect(farbe, Offset(x - koerper / 2, o), Size(koerper, maxOf(u - o, 1f)))
                        }
                    }
                    ueberlagerung.forEach { l ->
                        val farbe = LINIEN_FARBEN.getOrElse(l.farbe) { LINIEN_FARBEN[0] }
                        if (l.art == ChartIndikatoren.Art.PUNKTE) l.werte.forEachIndexed { i, v ->
                            if (v != null) drawCircle(farbe, 1.8f * density, Offset(achse.x(i), skala.y(v)))
                        } else linie(l.werte, achse, skala, farbe.copy(alpha = 0.9f), 1.5f * density)
                    }
                    val gelb = LINIEN_FARBEN[0]
                    zeichnungen.forEach { z ->
                        when (z.art) {
                            ChartZeichnungen.LINIE -> {
                                val a = Offset(achse.xVon(z.t1), skala.y(z.p1)); val b = Offset(achse.xVon(z.t2), skala.y(z.p2))
                                drawLine(gelb, a, b, strokeWidth = 2f * density)
                                drawCircle(gelb, 3f * density, a); drawCircle(gelb, 3f * density, b)
                            }
                            ChartZeichnungen.WAAGERECHT -> {
                                val y = skala.y(z.p1)
                                drawLine(gelb, Offset(0f, y), Offset(breite, y), strokeWidth = 1.5f * density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                                val t = messer.measure(preisText(z.p1), TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold))
                                drawText(t, color = gelb, topLeft = Offset(4 * density, y - t.size.height - 2 * density))
                            }
                            ChartZeichnungen.NOTIZ -> {
                                val p = Offset(achse.xVon(z.t1), skala.y(z.p1))
                                val t = messer.measure(z.text, TextStyle(fontSize = 11.sp), maxLines = 2, constraints = androidx.compose.ui.unit.Constraints(maxWidth = (160 * density).toInt()))
                                val pad = 5 * density
                                val links = (p.x - t.size.width / 2f - pad).coerceIn(0f, (breite - t.size.width - 2 * pad).coerceAtLeast(0f))
                                val oben = (p.y - t.size.height - 2 * pad - 6 * density).coerceAtLeast(0f)
                                drawLine(gelb, p, Offset(p.x, oben + t.size.height + 2 * pad), strokeWidth = 1f * density)
                                drawCircle(gelb, 3f * density, p)
                                drawRoundRect(notizGrund, Offset(links, oben), Size(t.size.width + 2 * pad, t.size.height + 2 * pad), CornerRadius(6 * density))
                                drawText(t, color = notizSchrift, topLeft = Offset(links + pad, oben + pad))
                            }
                            else -> {
                                val t = messer.measure(z.text, TextStyle(fontSize = 20.sp))
                                drawText(t, topLeft = Offset(achse.xVon(z.t1) - t.size.width / 2f, skala.y(z.p1) - t.size.height / 2f))
                            }
                        }
                    }
                    entwurf?.let { (t, p) -> drawCircle(gelb, 4f * density, Offset(achse.xVon(t), skala.y(p))) }
                }
                scrub?.let { i ->
                    val x = achse.x(i)
                    drawLine(achsenFarbe.copy(alpha = 0.7f), Offset(x, 0f), Offset(x, hoehe), strokeWidth = 1f * density)
                    if (prozent == null) anzeige.getOrNull(i)?.let { c ->
                        val y = skala.y(c.close)
                        drawLine(achsenFarbe.copy(alpha = 0.5f), Offset(0f, y), Offset(breite, y), strokeWidth = 1f * density)
                    }
                }
            }
            datumsAchse(kerzen, achse, hoehe, messer, achsenFarbe)
        }
    }
}

// Abstand eines Punkts von einer Strecke.
private fun strecke(p: Offset, a: Offset, b: Offset): Float {
    val d = b - a
    val l2 = d.x * d.x + d.y * d.y
    if (l2 <= 0f) return (p - a).getDistance()
    val t = (((p.x - a.x) * d.x + (p.y - a.y) * d.y) / l2).coerceIn(0f, 1f)
    val q = Offset(a.x + t * d.x, a.y + t * d.y)
    return sqrt((p.x - q.x) * (p.x - q.x) + (p.y - q.y) * (p.y - q.y))
}

// Vier Zeitpunkte unter dem Chart: Uhrzeit bei Tagescharts, sonst Tag und Monat oder mit Jahr.
private fun DrawScope.datumsAchse(kerzen: List<Candle>, achse: Achse, oben: Float, messer: TextMeasurer, farbe: Color) {
    if (kerzen.size < 2) return
    val dauer = kerzen.last().t - kerzen.first().t
    val stil = TextStyle(fontSize = 10.sp)
    for (k in 0..3) {
        val i = ((kerzen.size - 1) * (k + 0.5) / 4).toInt()
        val t = kerzen[i].t
        val text = when {
            dauer < 2 * 86_400_000L -> Fmt.time(t)
            dauer < 200 * 86_400_000L -> Fmt.dayMonth(t)
            else -> Fmt.shortDate(t)
        }
        val m = messer.measure(text, stil)
        drawText(m, color = farbe, topLeft = Offset(achse.x(i) - m.size.width / 2f, oben + 3 * density))
    }
}

// Indikator in eigenem Feld unter dem Chart mit Namen und dem Wert am Fadenkreuz oder dem letzten.
@Composable
private fun UnterFeld(titel: String, e: ChartIndikatoren.Ergebnis, kerzen: List<Candle>, scrub: Int?, modifier: Modifier) {
    val signal = LocalSignal.current
    val messer = rememberTextMeasurer()
    val achsenFarbe = MaterialTheme.colorScheme.onSurfaceVariant
    val dichte = LocalDensity.current
    BoxWithConstraints(modifier) {
        val rechts = with(dichte) { 52.dp.toPx() }
        val kopf = with(dichte) { 16.dp.toPx() }
        val breite = (constraints.maxWidth - rechts).coerceAtLeast(1f)
        val hoehe = (constraints.maxHeight - kopf).coerceAtLeast(1f)
        val achse = remember(kerzen, breite) { Achse(kerzen, breite) }
        val skala = remember(e, hoehe) {
            val (lo, hi) = spanne(e.linien.asSequence().flatMap { it.werte.asSequence() }.filterNotNull() + e.stufen.asSequence(), 0.08)
            Skala(e.min ?: lo, e.max ?: hi, kopf, hoehe)
        }
        Canvas(Modifier.fillMaxSize()) {
            val i = scrub ?: (kerzen.size - 1)
            val wert = e.linien.firstOrNull { it.art != ChartIndikatoren.Art.BALKEN }?.werte?.getOrNull(i) ?: e.linien.firstOrNull()?.werte?.getOrNull(i)
            val kopfText = titel + (wert?.let { "  " + kompakt(it) } ?: "")
            drawText(messer.measure(kopfText, TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)), color = achsenFarbe, topLeft = Offset(0f, 0f))
            e.stufen.forEach { s ->
                val y = skala.y(s)
                drawLine(achsenFarbe.copy(alpha = 0.35f), Offset(0f, y), Offset(breite, y), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                val t = messer.measure(kompakt(s), TextStyle(fontSize = 9.sp))
                drawText(t, color = achsenFarbe, topLeft = Offset(breite + 4 * density, y - t.size.height / 2f))
            }
            if (e.stufen.isEmpty()) {
                listOf(skala.hi, skala.lo).forEach { v ->
                    val t = messer.measure(kompakt(v), TextStyle(fontSize = 9.sp))
                    drawText(t, color = achsenFarbe, topLeft = Offset(breite + 4 * density, (skala.y(v) - t.size.height / 2f).coerceIn(kopf, kopf + hoehe - t.size.height)))
                }
            }
            clipRect(0f, kopf, breite, kopf + hoehe) {
                e.linien.forEach { l ->
                    if (l.art == ChartIndikatoren.Art.BALKEN) {
                        val null0 = skala.y(maxOf(skala.lo, minOf(0.0, skala.hi)))
                        val b = (achse.schritt * 0.7f).coerceAtLeast(1f)
                        l.werte.forEachIndexed { k, v ->
                            if (v == null) return@forEachIndexed
                            val farbe = when (l.farbe) {
                                ChartIndikatoren.FARBE_RICHTUNG -> kerzen.getOrNull(k)?.let { if (it.close >= it.open) signal.up else signal.down } ?: achsenFarbe
                                ChartIndikatoren.FARBE_VORZEICHEN -> if (v >= 0) signal.up else signal.down
                                else -> LINIEN_FARBEN.getOrElse(l.farbe) { LINIEN_FARBEN[0] }
                            }.copy(alpha = 0.75f)
                            val y = skala.y(v)
                            drawRect(farbe, Offset(achse.x(k) - b / 2, minOf(y, null0)), Size(b, maxOf(abs(null0 - y), 1f)))
                        }
                    } else linie(l.werte, achse, skala, LINIEN_FARBEN.getOrElse(l.farbe) { LINIEN_FARBEN[0] }, 1.5f * density)
                }
                scrub?.let { k -> drawLine(achsenFarbe.copy(alpha = 0.7f), Offset(achse.x(k), kopf), Offset(achse.x(k), kopf + hoehe), strokeWidth = 1f * density) }
            }
        }
    }
}

// Kurze Zahl fuer Indikatorwerte: Tausender, Millionen und Milliarden abgekuerzt.
private fun kompakt(v: Double): String = when {
    abs(v) >= 1e9 -> Fmt.num(v / 1e9, 1) + " Mrd"
    abs(v) >= 1e6 -> Fmt.num(v / 1e6, 1) + " Mio"
    abs(v) >= 1e4 -> Fmt.num(v / 1e3, 0) + " Tsd"
    abs(v) >= 100 -> Fmt.num(v, 0)
    abs(v) >= 1 -> Fmt.num(v, 1)
    else -> Fmt.num(v, 3)
}

@Composable
private fun IndikatorWahl(aktiv: Set<ChartIndikatoren.Indikator>, umschalten: (ChartIndikatoren.Indikator) -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
        listOf(ChartIndikatoren.Ort.KURS to S.chartIndikatorenKurs, ChartIndikatoren.Ort.UNTEN to S.chartIndikatorenUnten).forEach { (ort, titel) ->
            SectionHeader(titel)
            ChartIndikatoren.Indikator.entries.filter { it.ort == ort }.forEach { i ->
                Row(
                    Modifier.fillMaxWidth().clickable { umschalten(i) }.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = i in aktiv, onCheckedChange = { umschalten(i) })
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(S.chartIndikatorName(i.key), style = MaterialTheme.typography.bodyMedium)
                        Text(S.chartIndikatorInfo(i.key), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlatzWahl(state: AppState, symbol: String, gewaehlt: String, onWahl: (MarketRepository.Handelsplatz) -> Unit) {
    var plaetze by remember(symbol) { mutableStateOf<List<MarketRepository.Handelsplatz>?>(null) }
    LaunchedEffect(symbol) { plaetze = state.market.handelsplaetze(symbol) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
        SectionHeader(S.chartHandelsplatz)
        val liste = plaetze
        when {
            liste == null -> Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(S.chartHandelsplatzSuche, style = MaterialTheme.typography.bodyMedium)
            }
            else -> {
                liste.forEach { p ->
                    OptionRow(
                        p.boerse.ifBlank { p.symbol },
                        listOfNotNull(p.symbol, p.waehrung.takeIf { it.isNotBlank() }, Fmt.priceEuro(p.kurs), p.zeit.takeIf { it > 0 }?.let { Fmt.dateTime(it) }).joinToString(" · "),
                        selected = p.symbol == gewaehlt,
                    ) { onWahl(p) }
                }
                if (liste.size <= 1) Text(
                    S.chartHandelsplatzLeer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
    }
}

// Vorschlaege im Vergleich: wichtige Indizes und ein Weltindex-ETF.
private val VERGLEICH_VORSCHLAEGE = listOf("^GDAXI" to "DAX", "^GSPC" to "S&P 500", "^IXIC" to "Nasdaq", "^STOXX50E" to "Euro Stoxx 50", "EUNL.DE" to "MSCI World", "BTC-EUR" to "Bitcoin")

@Composable
private fun VergleichWahl(state: AppState, schon: List<String>, onWahl: (String) -> Unit) {
    var suche by remember { mutableStateOf("") }
    var treffer by remember { mutableStateOf<List<MarketApi.SearchHit>>(emptyList()) }
    LaunchedEffect(suche) {
        if (suche.isBlank()) { treffer = emptyList(); return@LaunchedEffect }
        delay(300)
        treffer = runCatching { state.market.yahooSuche(suche.trim()) }.getOrDefault(emptyList())
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
        SectionHeader(S.chartVergleichTitel)
        OutlinedTextField(
            value = suche, onValueChange = { suche = it.take(40) }, singleLine = true, label = { Text(S.chartVergleichSuche) },
            shape = RoundedCornerShape(Radius.field), modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        )
        if (suche.isBlank()) {
            Row(
                Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VERGLEICH_VORSCHLAEGE.filter { it.first !in schon }.forEach { (s, n) -> FilterPill(n) { onWahl(s) } }
            }
        }
        treffer.filter { it.symbol !in schon }.forEach { h ->
            Row(
                Modifier.fillMaxWidth().clickable { onWahl(h.symbol) }.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    NamenText(h.name.ifBlank { h.symbol }, style = MaterialTheme.typography.bodyMedium)
                    Text(listOf(h.symbol, h.exchange).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
        Text(S.chartVergleichHinweis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
    }
}

@Composable
private fun EmojiWahl(onWahl: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
        EMOJIS.chunked(8).forEach { reihe ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                reihe.forEach { e ->
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).clickable { onWahl(e) }, contentAlignment = Alignment.Center) {
                        Text(e, fontSize = 22.sp)
                    }
                }
            }
        }
    }
}
