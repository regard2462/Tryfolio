package de.troetenrepublik.app.ui

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import de.troetenrepublik.app.plattform.mausWischen
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.HeatmapQuelle
import de.troetenrepublik.app.engine.Treemap
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Catalog
import kotlinx.coroutines.delay
import kotlin.math.abs

// Indizes in der Auswahl der Heatmap, in dieser Reihenfolge, soweit der Katalog ihre Mitglieder kennt.
private val HEATMAP_INDIZES = listOf(
    "DAX", "MDAX", "TECDAX", "SDAX", "SP500", "NDX", "DJIA", "ESTX50", "FTSE100", "CAC40", "SMI", "AEX", "IBEX35",
    "FTSEMIB", "ATX", "OMXS30", "OMXC25", "OMXH25", "OBX", "NIKKEI225",
)

// Ab dieser Tagesaenderung (Prozent) ist eine Kachel voll eingefaerbt.
private const val VOLL_PROZENT = 3.0

// Heatmap: Flaeche nach Boersenwert (Depot nach Positionswert, Watchlist gleich gross), Farbe nach Tagesaenderung,
// ab 3 % voll. Auswahl: ganzer Aktienmarkt, Depot, Watchlist und die Indizes des Katalogs, wahlweise nach Branche gruppiert.
@Composable
fun HeatmapScreen(state: AppState, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val quellen = heatmapQuellen(state)
    // Depot, Watchlist und Indizes sind Seiten, zwischen denen man wischt; die Pillen zeigen, wo man ist
    var gemerkt by rememberSaveable { mutableStateOf(HeatmapQuelle.MARKT) }
    var gruppiert by rememberSaveable { mutableStateOf(false) }
    val pager = rememberPagerState(initialPage = quellen.indexOf(gemerkt).coerceAtLeast(0)) { quellen.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager, quellen) { snapshotFlow { pager.currentPage }.collect { i -> quellen.getOrNull(i)?.let { gemerkt = it } } }
    val quelle by remember(quellen) { derivedStateOf { quellen.getOrNull(pager.currentPage) ?: HeatmapQuelle.MARKT } }
    val dismiss = rememberSwipeDownDismiss(onBack)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss)) {
        ScreenHeader(S.heatmap, S.heatmapGroesse(quelle), onBack)
        // Die weisse Pille gleitet beim Wischen mit, die Leiste rollt mit
        MitlaufendeReiter(
            quellen.size, pager,
            titel = { i -> quelleTitel(quellen[i]) },
            modifier = Modifier.padding(vertical = 6.dp),
        ) { scope.launch { pager.animateScrollToPage(it) } }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterPill(S.heatmapBranchen, active = gruppiert) { gruppiert = !gruppiert }
        }
        HorizontalPager(state = pager, beyondViewportPageCount = 1, modifier = Modifier.fillMaxWidth().weight(1f).mausWischen(pager), key = { quellen.getOrElse(it) { "$it" } }) { i ->
            HeatmapSeite(state, quellen[i], gruppiert, onOpen)
        }
        Legende(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 20.dp))
    }
}

@Composable
private fun heatmapQuellen(state: AppState): List<String> {
    val account = state.account.collectAsStateWithLifecycle()
    val valuation = state.valuation.collectAsStateWithLifecycle()
    val indizes = remember {
        val vorhanden = Catalog.get().aktien.flatMapTo(HashSet()) { it.x }
        HEATMAP_INDIZES.filter { it in vorhanden }
    }
    // Kurse aendern die Bewertung laufend; neu aufgebaut wird nur, wenn Depot oder Watchlist dazukommen oder wegfallen
    val hatDepot by remember { derivedStateOf { valuation.value.positions.any { !it.position.isDerivative } } }
    val hatWatchlist by remember { derivedStateOf { account.value.watchlist.isNotEmpty() } }
    return remember(hatDepot, hatWatchlist, indizes) {
        buildList {
            add(HeatmapQuelle.MARKT)
            if (hatDepot) add(HeatmapQuelle.DEPOT)
            if (hatWatchlist) add(HeatmapQuelle.WATCHLIST)
            addAll(indizes)
        }
    }
}

private fun quelleTitel(q: String): String = when (q) {
    HeatmapQuelle.MARKT -> S.heatmapMarkt; HeatmapQuelle.DEPOT -> S.heatmapDepot; HeatmapQuelle.WATCHLIST -> S.watchlist
    else -> Catalog.indexName(q)
}

// Heatmap als dritte Seite im Watchlist-Blatt, mit einem Pager wie Copytrading; die Nachbarquelle ist vorgeladen.
// An der ersten Quelle nimmt der Pager des Blatts einen Wisch nach rechts (zurueck zu den Trends).
@Composable
fun HeatmapReiter(state: AppState, onOpen: (String) -> Unit) {
    val quellen = heatmapQuellen(state)
    var gemerkt by rememberSaveable { mutableStateOf(HeatmapQuelle.MARKT) }
    var gruppiert by rememberSaveable { mutableStateOf(false) }
    val pager = rememberPagerState(initialPage = quellen.indexOf(gemerkt).coerceAtLeast(0)) { quellen.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager, quellen) { snapshotFlow { pager.currentPage }.collect { i -> quellen.getOrNull(i)?.let { gemerkt = it } } }
    Column(Modifier.fillMaxSize()) {
        HeatmapGroesse(quellen, pager)
        MitlaufendeReiter(quellen.size, pager, titel = { i -> quelleTitel(quellen[i]) }, modifier = Modifier.padding(vertical = 6.dp)) {
            scope.launch { pager.animateScrollToPage(it) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterPill(S.heatmapBranchen, active = gruppiert) { gruppiert = !gruppiert }
        }
        HorizontalPager(state = pager, beyondViewportPageCount = 1, modifier = Modifier.fillMaxWidth().weight(1f).mausWischen(pager), key = { quellen.getOrElse(it) { "$it" } }) { i ->
            HeatmapSeite(state, quellen[i], gruppiert, onOpen)
        }
        Legende(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 20.dp))
    }
}

// Erklaerung der Flaechen zur sichtbaren Quelle; liest die Seite selbst und wird allein neu gezeichnet.
@Composable
private fun HeatmapGroesse(quellen: List<String>, pager: androidx.compose.foundation.pager.PagerState) {
    val quelle by remember(quellen) { derivedStateOf { quellen.getOrNull(pager.currentPage) ?: HeatmapQuelle.MARKT } }
    Text(S.heatmapGroesse(quelle), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
}

// Eine Quelle der Heatmap: Zaehler und Kacheln; alle zwei Minuten neu, solange die Seite zu sehen ist.
@Composable
private fun HeatmapSeite(state: AppState, quelle: String, gruppiert: Boolean, onOpen: (String) -> Unit) {
    var werte by remember(quelle) { mutableStateOf<List<AppState.HeatmapWert>?>(null) }
    LaunchedEffect(quelle) {
        while (true) {
            werte = state.heatmap(quelle)
            delay(120_000L)
        }
    }
    val liste = werte
    Column(Modifier.fillMaxSize()) {
        if (liste != null && liste.isNotEmpty()) {
            Text(
                S.heatmapZaehler(liste.count { (it.aenderung ?: 0.0) > 0 }, liste.count { (it.aenderung ?: 0.0) < 0 }),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )
        }
        Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
            when {
                liste == null -> CircularProgressIndicator()
                liste.isEmpty() -> Text(S.heatmapLeer, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> HeatmapFlaeche(liste, gruppiert, onOpen, Modifier.fillMaxSize())
            }
        }
    }
}

private class Kachel(val wert: AppState.HeatmapWert, val r: Treemap.Rechteck)
private class Gruppe(val name: String, val r: Treemap.Rechteck, val kopf: Boolean)

// Flaechen fuer alle Werte; gruppiert erst die Branchen, darin die Werte unter einer Kopfzeile.
private fun anordnen(werte: List<AppState.HeatmapWert>, gruppiert: Boolean, w: Float, h: Float, kopf: Float, rand: Float): Pair<List<Kachel>, List<Gruppe>> {
    if (!gruppiert) return Treemap.anordnen(werte.map { it.gewicht }, w, h).mapIndexed { i, r -> Kachel(werte[i], r) } to emptyList()
    val branchen = werte.groupBy { it.branche }.entries.sortedByDescending { e -> e.value.sumOf { it.gewicht } }
    val flaechen = Treemap.anordnen(branchen.map { e -> e.value.sumOf { it.gewicht } }, w, h)
    val kacheln = ArrayList<Kachel>()
    val gruppen = ArrayList<Gruppe>()
    branchen.forEachIndexed { i, (branche, mitglieder) ->
        val g = flaechen[i]
        // Die Kopfzeile mit dem Namen der Branche nur, wo Platz ist
        val mitKopf = g.h > kopf * 3 && g.w > kopf * 3
        val oben = if (mitKopf) kopf else 0f
        val innen = Treemap.Rechteck(g.x + rand, g.y + rand + oben, (g.w - 2 * rand).coerceAtLeast(0f), (g.h - 2 * rand - oben).coerceAtLeast(0f))
        gruppen += Gruppe(if (branche.isBlank()) S.heatmapOhneBranche else S.industryName(branche), g, mitKopf)
        Treemap.anordnen(mitglieder.map { it.gewicht }, innen.w, innen.h, innen.x, innen.y).forEachIndexed { k, r -> kacheln += Kachel(mitglieder[k], r) }
    }
    return kacheln to gruppen
}

// Farbe nach Tagesaenderung: neutral um null, dann zunehmend gruen oder rot, ab VOLL_PROZENT voll.
private fun kachelFarbe(aenderung: Double?, hoch: Color, runter: Color, neutral: Color): Color {
    if (aenderung == null || !aenderung.isFinite() || abs(aenderung) < 0.05) return neutral
    val staerke = (abs(aenderung) / VOLL_PROZENT).coerceIn(0.0, 1.0).toFloat()
    return lerp(neutral, if (aenderung > 0) hoch else runter, 0.3f + 0.7f * staerke)
}

// Kurzes Kuerzel fuer kleine Kacheln: das Symbol ohne Boersenzusatz.
private fun kuerzel(symbol: String): String = symbol.substringBefore('.').substringBefore('-')

private class Beschriftung(val name: TextLayoutResult?, val prozent: TextLayoutResult?)

@Composable
private fun HeatmapFlaeche(werte: List<AppState.HeatmapWert>, gruppiert: Boolean, onOpen: (String) -> Unit, modifier: Modifier) {
    val signal = LocalSignal.current
    val messer = rememberTextMeasurer()
    val dichte = LocalDensity.current
    val neutral = MaterialTheme.colorScheme.surfaceContainerHighest
    val kopfFarbe = MaterialTheme.colorScheme.onSurfaceVariant
    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val kopf = with(dichte) { 16.dp.toPx() }
        val rand = with(dichte) { 1.5.dp.toPx() }
        val luecke = with(dichte) { 1.dp.toPx() }
        val (kacheln, gruppen) = remember(werte, gruppiert, w, h) { anordnen(werte, gruppiert, w, h, kopf, rand) }
        // Beschriftung nach Groesse der Kachel in festen Stufen; ein Name, der nicht passt, wird gekuerzt, nicht kleiner
        val namensStil = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        val kleinStil = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        val prozentStil = TextStyle(fontSize = 11.sp)
        val beschriftungen = remember(kacheln, dichte) {
            val gross = with(dichte) { 52.dp.toPx() } to with(dichte) { 36.dp.toPx() }
            val mittel = with(dichte) { 28.dp.toPx() } to with(dichte) { 16.dp.toPx() }
            val innen = with(dichte) { 8.dp.toPx() }
            kacheln.map { k ->
                val r = k.r
                val platz = r.w - innen
                fun passend(stil: TextStyle, kandidaten: List<String>): TextLayoutResult? =
                    kandidaten.firstNotNullOfOrNull { t -> messer.measure(t, stil, maxLines = 1, softWrap = false).takeIf { it.size.width <= platz } }
                val prozentText = k.wert.aenderung?.let { Fmt.signedPct(it, 2) }
                when {
                    r.w >= gross.first && r.h >= gross.second -> Beschriftung(
                        passend(namensStil, namensKuerzungen(k.wert.name) + kuerzel(k.wert.symbol)),
                        prozentText?.let { passend(prozentStil, listOf(it, Fmt.signedPct(k.wert.aenderung!!, 1))) },
                    )
                    r.w >= mittel.first && r.h >= mittel.second -> Beschriftung(passend(kleinStil, listOf(kuerzel(k.wert.symbol))), null)
                    else -> Beschriftung(null, null)
                }
            }
        }
        val gruppenKoepfe = remember(gruppen, dichte) {
            gruppen.map { g ->
                if (!g.kopf) null else namensKuerzungen(g.name).firstNotNullOfOrNull { t ->
                    messer.measure(t, TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, softWrap = false)
                        .takeIf { it.size.width <= g.r.w - 8 * dichte.density }
                }
            }
        }
        Canvas(
            Modifier.fillMaxSize().graphicsLayer().pointerInput(kacheln) {
                detectTapGestures { p -> kacheln.firstOrNull { (p.x to p.y) in it.r }?.let { onOpen(it.wert.symbol) } }
            },
        ) {
            gruppen.forEachIndexed { i, g ->
                gruppenKoepfe[i]?.let { t -> drawText(t, color = kopfFarbe, topLeft = Offset(g.r.x + 4 * density, g.r.y + (kopf - t.size.height) / 2)) }
            }
            kacheln.forEachIndexed { i, k ->
                val r = k.r
                if (r.w <= 2 * luecke || r.h <= 2 * luecke) return@forEachIndexed
                val farbe = kachelFarbe(k.wert.aenderung, signal.up, signal.down, neutral)
                drawRoundRect(farbe, Offset(r.x + luecke, r.y + luecke), Size(r.w - 2 * luecke, r.h - 2 * luecke), CornerRadius(3 * density))
                val b = beschriftungen.getOrNull(i) ?: return@forEachIndexed
                val schrift = if (farbe.luminance() > 0.45f) Color.Black else Color.White
                val name = b.name ?: return@forEachIndexed
                val prozent = b.prozent
                val hoehe = name.size.height + (prozent?.size?.height ?: 0)
                val y0 = r.y + (r.h - hoehe) / 2
                drawText(name, color = schrift, topLeft = Offset(r.x + (r.w - name.size.width) / 2, y0))
                if (prozent != null) drawText(prozent, color = schrift.copy(alpha = 0.9f), topLeft = Offset(r.x + (r.w - prozent.size.width) / 2, y0 + name.size.height))
            }
        }
    }
}

// Farbskala von −3 % bis +3 %.
@Composable
private fun Legende(modifier: Modifier) {
    val signal = LocalSignal.current
    val neutral = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        listOf(-3.0, -2.0, -1.0, 0.0, 1.0, 2.0, 3.0).forEach { v ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(44.dp)) {
                Box(Modifier.size(width = 40.dp, height = 10.dp).clip(RoundedCornerShape(3.dp)).background(kachelFarbe(v, signal.up, signal.down, neutral)))
                Spacer(Modifier.height(3.dp))
                Text(
                    if (v == 0.0) "0" else Fmt.signedPct(v, 0), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                )
            }
        }
    }
}
