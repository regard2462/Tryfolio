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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.mutableFloatStateOf
import de.troetenrepublik.app.plattform.ZurueckTaste
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
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
import de.troetenrepublik.app.data.Range
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.unit.Constraints
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Catalog
import kotlinx.coroutines.delay
import kotlin.math.abs
import de.troetenrepublik.app.engine.HeatmapBeschriftung
import de.troetenrepublik.app.engine.HeatmapGruppenName
import de.troetenrepublik.app.engine.HeatmapZoomLage
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign

// Indizes in der Auswahl der Heatmap, in dieser Reihenfolge, soweit der Katalog ihre Mitglieder kennt.
private val HEATMAP_INDIZES = listOf(
    "DAX", "MDAX", "TECDAX", "SDAX", "SP500", "NDX", "DJIA", "ESTX50", "FTSE100", "CAC40", "SMI", "AEX", "IBEX35",
    "FTSEMIB", "ATX", "OMXS30", "OMXC25", "OMXH25", "OBX", "NIKKEI225",
)

// Indizes der Heatmap, die der Katalog kennt (auch fuer die Einstellung des Widgets).
fun heatmapIndizes(): List<String> {
    val vorhanden = Catalog.get().aktien.flatMapTo(HashSet()) { it.x }
    return HEATMAP_INDIZES.filter { it in vorhanden }
}

// Zeitraeume der Heatmap (seit 5.92) wie beim Depotchart, in Kalendertagen wie Branchentrends.ZEITRAEUME; auch fuer das Widget.
val HEATMAP_ZEITRAEUME = listOf(Range.D1, Range.W1, Range.M1, Range.M3, Range.M6, Range.Y1)

// Heatmap: Flaeche nach Boersenwert (Depot nach Positionswert, Watchlist gleich gross), Farbe nach der Aenderung im
// gewaehlten Zeitraum (1T bis 1J). Auswahl: ganzer Aktienmarkt, Depot, Watchlist und die Indizes des Katalogs, wahlweise
// nach Branche oder nach Weltregion gruppiert. startQuelle und startZeitraum: die Auswahl, mit der ein Tipp auf das
// Widget die Seite oeffnet.
@Composable
fun HeatmapScreen(state: AppState, onBack: () -> Unit, onOpen: (String) -> Unit, startQuelle: String? = null, startZeitraum: Int = 0) {
    val quellen = heatmapQuellen(state)
    // Depot, Watchlist und Indizes sind Seiten, zwischen denen man wischt; die Pillen zeigen, wo man ist
    var gemerkt by rememberSaveable { mutableStateOf(startQuelle ?: HeatmapQuelle.MARKT) }
    var startOffen by rememberSaveable { mutableStateOf(startQuelle) }
    var gruppiert by rememberSaveable { mutableStateOf(HeatmapGruppe.KEINE) }
    var zeitraum by rememberSaveable { mutableIntStateOf(startZeitraum.coerceIn(0, HEATMAP_ZEITRAEUME.size - 1)) }
    val pager = rememberPagerState(initialPage = quellen.indexOf(gemerkt).coerceAtLeast(0)) { quellen.size }
    val scope = rememberCoroutineScope()
    // Die Quelle aus dem Widget anspringen, sobald sie in der Auswahl steht
    LaunchedEffect(quellen) {
        val z = startOffen ?: return@LaunchedEffect
        val i = quellen.indexOf(z)
        if (i >= 0) { pager.scrollToPage(i); startOffen = null }
    }
    // Wischt man selbst, gilt die eigene Wahl
    LaunchedEffect(pager) { snapshotFlow { pager.isScrollInProgress }.collect { if (it) startOffen = null } }
    LaunchedEffect(pager, quellen) { snapshotFlow { pager.currentPage }.collect { i -> if (startOffen == null) quellen.getOrNull(i)?.let { gemerkt = it } } }
    val dismiss = rememberSwipeDownDismiss(onBack)
    // Quellen, deren Flaeche gerade hineingezoomt ist; nur dort gehoert das Ziehen der Heatmap
    val gezoomt = remember { mutableStateMapOf<String, Boolean>() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss)) {
        ScreenHeader(S.heatmap, null, onBack)
        // Die weisse Pille gleitet beim Wischen mit, die Leiste rollt mit
        val wm = rememberReiterWidgetMenue(state) { i -> quellen.getOrNull(i)?.let { de.troetenrepublik.app.data.WidgetVorgabe.heatmap(it, zeitraum) } }
        MitlaufendeReiter(
            quellen.size, pager,
            titel = { i -> heatmapQuelleTitel(quellen[i]) },
            modifier = Modifier.padding(vertical = 6.dp),
            langDruecken = wm.langDruecken, menue = { i -> wm.menue(i) },
        ) { scope.launch { pager.animateScrollToPage(it) } }
        GruppenPillen(gruppiert) { gruppiert = it }
        ZeitraumWahl(zeitraum) { zeitraum = it }
        HorizontalPager(state = pager, beyondViewportPageCount = 1, modifier = Modifier.fillMaxWidth().weight(1f).wischSperre { gezoomt[quellen.getOrNull(pager.currentPage)] == true }.mausWischen(pager), key = { quellen.getOrElse(it) { "$it" } }) { i ->
            HeatmapSeite(state, quellen[i], gruppiert, zeitraum, onOpen) { gezoomt[quellen[i]] = it }
        }
        Legende(zeitraum, Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 20.dp))
    }
}

// Zeitraum wie beim Depotchart, ohne eigene Ueberschrift.
@Composable
private fun ZeitraumWahl(zeitraum: Int, onWahl: (Int) -> Unit) {
    RangeSelector(
        HEATMAP_ZEITRAEUME, selected = HEATMAP_ZEITRAEUME.getOrNull(zeitraum),
        onSelect = { r -> onWahl(HEATMAP_ZEITRAEUME.indexOf(r).coerceAtLeast(0)) },
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

@Composable
private fun heatmapQuellen(state: AppState): List<String> {
    val account = state.account.collectAsStateWithLifecycle()
    val valuation = state.valuation.collectAsStateWithLifecycle()
    val indizes = remember { heatmapIndizes() }
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

// Name einer Quelle der Heatmap, auch fuer das Widget.
fun heatmapQuelleTitel(q: String): String = when (q) {
    HeatmapQuelle.MARKT -> S.heatmapMarkt; HeatmapQuelle.DEPOT -> S.heatmapDepot; HeatmapQuelle.WATCHLIST -> S.watchlist
    else -> Catalog.indexName(q)
}

// Heatmap als Reiter der Seite Maerkte; die Nachbarquelle ist vorgeladen. Quellen wechseln nur per Tipp auf den Reiter,
// ein Wisch geht an den Pager der Seite (zum Nachbarreiter Globus, 0.69).
@Composable
fun HeatmapReiter(state: AppState, onOpen: (String) -> Unit) {
    val quellen = heatmapQuellen(state)
    var gemerkt by rememberSaveable { mutableStateOf(HeatmapQuelle.MARKT) }
    var gruppiert by rememberSaveable { mutableStateOf(HeatmapGruppe.KEINE) }
    var zeitraum by rememberSaveable { mutableIntStateOf(0) }
    val pager = rememberPagerState(initialPage = quellen.indexOf(gemerkt).coerceAtLeast(0)) { quellen.size }
    val scope = rememberCoroutineScope()
    val gezoomt = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(pager, quellen) { snapshotFlow { pager.currentPage }.collect { i -> quellen.getOrNull(i)?.let { gemerkt = it } } }
    Column(Modifier.fillMaxSize()) {
        val wm = rememberReiterWidgetMenue(state) { i -> quellen.getOrNull(i)?.let { de.troetenrepublik.app.data.WidgetVorgabe.heatmap(it, zeitraum) } }
        MitlaufendeReiter(quellen.size, pager, titel = { i -> heatmapQuelleTitel(quellen[i]) }, modifier = Modifier.padding(vertical = 6.dp), langDruecken = wm.langDruecken, menue = { i -> wm.menue(i) }) {
            scope.launch { pager.animateScrollToPage(it) }
        }
        GruppenPillen(gruppiert) { gruppiert = it }
        ZeitraumWahl(zeitraum) { zeitraum = it }
        HorizontalPager(state = pager, beyondViewportPageCount = 1, userScrollEnabled = false, modifier = Modifier.fillMaxWidth().weight(1f).wischSperre { gezoomt[quellen.getOrNull(pager.currentPage)] == true }, key = { quellen.getOrElse(it) { "$it" } }) { i ->
            HeatmapSeite(state, quellen[i], gruppiert, zeitraum, onOpen) { gezoomt[quellen[i]] = it }
        }
        Legende(zeitraum, Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 20.dp))
    }
}

// Eine Quelle der Heatmap: Zaehler und Kacheln; alle zwei Minuten neu, solange die Seite zu sehen ist. Laengere
// Zeitraeume rechnet AppState aus Tagesschlusskursen und merkt sie sich eine halbe Stunde.
@Composable
private fun HeatmapSeite(state: AppState, quelle: String, gruppiert: String, zeitraum: Int, onOpen: (String) -> Unit, onZoom: (Boolean) -> Unit) {
    var werte by remember(quelle, zeitraum) { mutableStateOf<List<AppState.HeatmapWert>?>(null) }
    val tage = de.troetenrepublik.app.engine.Branchentrends.ZEITRAEUME.getOrElse(zeitraum) { 1 }
    LaunchedEffect(quelle, zeitraum) {
        while (true) {
            werte = state.heatmap(quelle, tage)
            delay(120_000L)
        }
    }
    // Grossansicht (5.94): eine angetippte Branche oder Region allein, zurueck mit dem Knopf oder der Zuruecktaste
    var fokus by remember(quelle, gruppiert) { mutableStateOf<Pair<String, String>?>(null) }
    ZurueckTaste(enabled = fokus != null) { fokus = null }
    val alle = werte
    val liste = fokus?.let { (schluessel, _) -> alle?.filter { gruppenSchluessel(it, gruppiert) == schluessel } } ?: alle
    Column(Modifier.fillMaxSize()) {
        fokus?.let { (_, titel) ->
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { fokus = null }.padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChevronRight(color = LocalSignal.current.link, modifier = Modifier.rotate(180f))
                Spacer(Modifier.width(4.dp))
                Text(S.heatmapAlle, style = MaterialTheme.typography.labelLarge, color = LocalSignal.current.link, maxLines = 1, softWrap = false)
                Spacer(Modifier.width(10.dp))
                NamenText(titel, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            }
        }
        if (liste != null && liste.isNotEmpty()) {
            val steigen = liste.count { (it.aenderung ?: 0.0) > 0 }
            val fallen = liste.count { (it.aenderung ?: 0.0) < 0 }
            val sig = LocalSignal.current
            val grau = MaterialTheme.colorScheme.onSurfaceVariant
            Row(Modifier.padding(horizontal = 20.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                DirectionTriangle(1.0, color = sig.up)
                Spacer(Modifier.width(4.dp))
                Text(S.heatmapSteigen(steigen), style = MaterialTheme.typography.bodySmall, color = sig.up, maxLines = 1, softWrap = false)
                Spacer(Modifier.width(14.dp))
                DirectionTriangle(-1.0, color = sig.down)
                Spacer(Modifier.width(4.dp))
                Text(S.heatmapFallen(fallen), style = MaterialTheme.typography.bodySmall, color = sig.down, maxLines = 1, softWrap = false)
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
            when {
                liste == null -> CircularProgressIndicator()
                liste.isEmpty() -> Text(S.heatmapLeer, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> HeatmapFlaeche(
                    liste, if (fokus != null) HeatmapGruppe.KEINE else gruppiert, HeatmapBeschriftung.VOLL_PROZENT.getOrElse(zeitraum) { 3.0 }, onOpen,
                    onGruppe = { schluessel, titel -> fokus = schluessel to titel }, modifier = Modifier.fillMaxSize(), state = state, onZoom = onZoom,
                )
            }
        }
        if (liste != null && fokus == null) IndexBeitragKarte(state, quelle, liste, onOpen)
    }
}

// Schluessel der Gruppe eines Werts: Branche oder Region.
private fun gruppenSchluessel(w: AppState.HeatmapWert, gruppiert: String): String =
    if (gruppiert == HeatmapGruppe.REGION) w.region else w.branche

// Hoechster Zoom und die Stufen, fuer die die Beschriftung neu gesetzt wird (dazwischen gilt die naechstkleinere).
private const val MAX_ZOOM = 8f
private val ZOOM_STUFEN = listOf(1f, 1.5f, 2f, 3f, 4f, 6f, 8f)

// Gruppierung der Flaechen: keine, nach Branche oder nach Weltregion.
private object HeatmapGruppe {
    const val KEINE = ""
    const val BRANCHE = "BRANCHE"
    const val REGION = "REGION"
}

// Zwei Pillen, hoechstens eine aktiv; ein zweiter Tipp hebt die Gruppierung auf.
@Composable
private fun GruppenPillen(gruppiert: String, onWahl: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        GruppenPille(S.heatmapBranchen, gruppiert == HeatmapGruppe.BRANCHE) {
            onWahl(if (gruppiert == HeatmapGruppe.BRANCHE) HeatmapGruppe.KEINE else HeatmapGruppe.BRANCHE)
        }
        GruppenPille(S.heatmapRegionen, gruppiert == HeatmapGruppe.REGION) {
            onWahl(if (gruppiert == HeatmapGruppe.REGION) HeatmapGruppe.KEINE else HeatmapGruppe.REGION)
        }
    }
}

// Gleiche Groesse und Schrift wie die Pillen der Quellen darueber (Reiterleiste: titleSmall, 18 und 11 dp Innenabstand).
@Composable
private fun GruppenPille(label: String, aktiv: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(Radius.chip)).background(auswahlChipFlaeche(aktiv))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 18.dp, vertical = 11.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = auswahlChipSchrift(aktiv), maxLines = 1, softWrap = false)
    }
}

// r liegt im Layout-Massstab der Zoomstufe (HeatmapZoomLage); g ist die Gruppe der Kachel, -1 ohne Gruppen
private class Kachel(val wert: AppState.HeatmapWert, val r: Treemap.Rechteck, val g: Int)
// oben: Abstand vom Gruppenanfang bis zu den Kacheln in Pixeln (Rand plus Kopfzeile)
private class Gruppe(val schluessel: String, val name: String, val kurz: String, val r: Treemap.Rechteck, val kopf: TextLayoutResult?, val oben: Float)

// Flaechen fuer alle Werte; gruppiert erst die Branchen oder Regionen, darin die Werte unter einer Kopfzeile. kopf
// liefert zu Name, kurzer Form, Breite und Hoehe der Gruppe die gesetzte Kopfzeile oder null, wenn kein Name ganz
// hineinpasst; solche Gruppen bekommen ihren Namen, sobald die Zoomstufe es erlaubt (Layout je Stufe).
private fun anordnen(
    werte: List<AppState.HeatmapWert>, gruppiert: String, w: Float, h: Float, rand: Float,
    kopf: (String, String, Float, Float) -> TextLayoutResult?,
): Pair<List<Kachel>, List<Gruppe>> {
    if (gruppiert == HeatmapGruppe.KEINE) return Treemap.anordnen(werte.map { it.gewicht }, w, h).mapIndexed { i, r -> Kachel(werte[i], r, -1) } to emptyList()
    val nachRegion = gruppiert == HeatmapGruppe.REGION
    val branchen = werte.groupBy { if (nachRegion) it.region else it.branche }.entries.sortedByDescending { e -> e.value.sumOf { it.gewicht } }
    val flaechen = Treemap.anordnen(branchen.map { e -> e.value.sumOf { it.gewicht } }, w, h)
    val kacheln = ArrayList<Kachel>()
    val gruppen = ArrayList<Gruppe>()
    branchen.forEachIndexed { i, (branche, mitglieder) ->
        val g = flaechen[i]
        val titel = when {
            branche.isBlank() || branche == "GLOBAL" -> S.heatmapOhneBranche
            nachRegion && branche == "KRYPTO" -> S.ptGroup("CRYPTO")
            nachRegion -> S.regionName(branche)
            else -> S.industryName(branche)
        }
        val kurz = when {
            branche.isBlank() || branche == "GLOBAL" -> titel
            nachRegion && branche == "KRYPTO" -> titel
            nachRegion -> S.regionShort(branche)
            else -> S.industryShort(branche)
        }
        // Die Kopfzeile mit dem Namen der Branche nur, wo ein Name ganz Platz hat
        val gesetzt = kopf(titel, kurz, g.w, g.h)
        val oben = gesetzt?.let { it.size.height + 2 * rand } ?: 0f
        val innen = Treemap.Rechteck(g.x + rand, g.y + rand + oben, (g.w - 2 * rand).coerceAtLeast(0f), (g.h - 2 * rand - oben).coerceAtLeast(0f))
        gruppen += Gruppe(branche, titel, kurz, g, gesetzt, rand + oben)
        Treemap.anordnen(mitglieder.map { it.gewicht }, innen.w, innen.h, innen.x, innen.y).forEachIndexed { k, r -> kacheln += Kachel(mitglieder[k], r, i) }
    }
    return kacheln to gruppen
}

// Heatmap-Farben: im hellen und im Sepia-Modus dieselben kraeftigen Gruen- und Rottoene wie im dunklen,
// neutral ein mittleres Grau statt der hellen Flaeche, damit die Kacheln nicht blass werden.
internal class HeatFarben(val hoch: Color, val runter: Color, val neutral: Color)

@Composable
internal fun heatFarben(): HeatFarben {
    val signal = LocalSignal.current
    return if (signal.dark) HeatFarben(signal.up, signal.down, MaterialTheme.colorScheme.surfaceContainerHighest)
    else HeatFarben(Color(0xFF02CA50), Color(0xFFFF3D47), if (signal.sepia) Color(0xFF7D7366) else Color(0xFF7A8088))
}

// Farbe nach Aenderung: neutral um null, dann zunehmend gruen oder rot, ab voll Prozent voll (wie im Widget).
private fun kachelFarbe(aenderung: Double?, hoch: Color, runter: Color, neutral: Color, voll: Double = 3.0): Color =
    Color(HeatmapBeschriftung.farbe(aenderung, voll, hoch.toArgb(), runter.toArgb(), neutral.toArgb()))

// kgv: dritte Zeile unter der Aenderung, nur wo sie ganz hineinpasst
private class Beschriftung(val name: TextLayoutResult?, val prozent: TextLayoutResult?, val kgv: TextLayoutResult? = null)

@Composable
private fun HeatmapFlaeche(
    werte: List<AppState.HeatmapWert>, gruppiert: String, voll: Double, onOpen: (String) -> Unit,
    onGruppe: (String, String) -> Unit, modifier: Modifier, state: AppState, onZoom: (Boolean) -> Unit,
) {
    // Langes Druecken auf eine Kachel: Menue mit Watchlist und Widget an der Fingerstelle
    var menue by remember { mutableStateOf<Pair<String, Offset>?>(null) }
    val haptik = androidx.compose.ui.platform.LocalHapticFeedback.current
    val signal = LocalSignal.current
    val messer = rememberTextMeasurer()
    val dichte = LocalDensity.current
    val heat = heatFarben()
    val kopfFarbe = MaterialTheme.colorScheme.onSurfaceVariant
    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val kopf = with(dichte) { 16.dp.toPx() }
        val rand = with(dichte) { 1.5.dp.toPx() }
        val luecke = with(dichte) { 1.dp.toPx() }
        // Kandidaten fuer den Namen einer Gruppe, bevorzugte zuerst; gemessen wird erst, wenn die Folge gelesen wird
        fun namensKandidaten(titel: String, kurz: String, platz: Float): Sequence<HeatmapGruppenName.Kandidat<TextLayoutResult>> = sequence {
            if (platz <= 0f) return@sequence
            fun einzeilig(t: String, form: HeatmapGruppenName.Form, gr: androidx.compose.ui.unit.TextUnit) =
                messer.measure(t, TextStyle(fontSize = gr, fontWeight = FontWeight.SemiBold), maxLines = 1, softWrap = false).let {
                    HeatmapGruppenName.Kandidat(form, gr.value, it.size.width.toFloat(), it.size.height.toFloat(), 1, it)
                }
            val groessen = listOf(10.sp, 9.sp, 8.sp)
            for (gr in groessen) yield(einzeilig(titel, HeatmapGruppenName.Form.VOLL, gr))
            if (HeatmapGruppenName.kurzNoetig(titel, kurz)) for (gr in groessen) yield(einzeilig(kurz, HeatmapGruppenName.Form.KURZ, gr))
            val zwei = messer.measure(
                titel, TextStyle(fontSize = 8.sp, fontWeight = FontWeight.SemiBold, lineHeight = 9.sp, hyphens = Hyphens.None),
                maxLines = 2, constraints = Constraints(maxWidth = platz.toInt()),
            )
            if (!zwei.hasVisualOverflow && zwei.lineCount <= 2) {
                yield(HeatmapGruppenName.Kandidat(HeatmapGruppenName.Form.ZWEIZEILIG, 8f, zwei.size.width.toFloat(), zwei.size.height.toFloat(), 2, zwei))
            }
        }
        // Zoom (5.94): mit zwei Fingern zoomen und verschieben, doppelt tippen zoomt hinein oder zurueck. Bildschirm =
        // Flaeche mal zoom plus versatz; vergroessert wird nur die Lage der Kacheln, die Schrift bleibt gleich gross.
        // [0.78] Der Zoom haengt an den gezeigten Werten, nicht an ihren Kursen: Ein neuer Kursstand oder die Rueckkehr von
        // einer Aktienseite laesst den Ausschnitt stehen. Gesten und Mausrad mit Strg: kartenZoom.
        val wertSchluessel = remember(werte) { werte.map { it.symbol } }
        val kz = remember(wertSchluessel, gruppiert) { KartenZoom() }
        var zoom by kz::zoom
        var versatz by kz::versatz
        fun begrenzt(o: Offset, s: Float) = Offset(o.x.coerceIn(w - w * s, 0f), o.y.coerceIn(h - h * s, 0f))
        val stufe = ZOOM_STUFEN.last { it <= zoom + 1e-3f }
        val (kacheln, gruppen) = remember(werte, gruppiert, w, h, stufe) {
            // Das Layout steht im Massstab der Zoomstufe: Kopfzeile und Rand in echten Pixeln, Gruppenname: volle Bezeichnung, sonst kurze Form, sonst zweizeilig; sonst ohne Kopf
            anordnen(werte, gruppiert, w * stufe, h * stufe, rand) { titel, kurz, gw, gh ->
                HeatmapGruppenName.waehlen(namensKandidaten(titel, kurz, gw - 8 * dichte.density), gw, gh, 8 * dichte.density, kopf)?.inhalt
            }
        }
        // Beschriftung nach Groesse der Kachel (HeatmapBeschriftung): ungezoomt das Kuerzel mit Prozent, gezoomt der volle
        // Name, wo er ganz hineinpasst. Nichts wird gekuerzt oder kleiner gesetzt.
        val namensStil = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        val kleinStil = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        val prozentStil = TextStyle(fontSize = 11.sp)
        val namensBlock = namensStil.copy(textAlign = TextAlign.Center, lineHeight = 15.sp)
        val gezoomt = stufe > 1.01f
        // Die Seite erfaehrt, ob hineingezoomt ist (Wischsperre); beim Verlassen der Flaeche gilt wieder ungezoomt
        val zoomMeldung by rememberUpdatedState(onZoom)
        LaunchedEffect(zoom > 1.01f) { zoomMeldung(zoom > 1.01f) }
        DisposableEffect(Unit) { onDispose { zoomMeldung(false) } }
        val beschriftungen = remember(kacheln, dichte, stufe) {
            val innen = with(dichte) { 8.dp.toPx() }
            val prozentMin = with(dichte) { 28.dp.toPx() } to with(dichte) { 24.dp.toPx() }
            val nameMin = with(dichte) { 52.dp.toPx() } to with(dichte) { 36.dp.toPx() }
            fun zeile(stil: TextStyle, t: String) = messer.measure(t, stil, maxLines = 1, softWrap = false)
            fun masz(l: TextLayoutResult) = HeatmapBeschriftung.Text(l.size.width.toFloat(), l.size.height.toFloat())
            // Bezugsbreiten: ein ueblich langes Kuerzel und die ueblich lange Prozentzahl; gleich grosse Kacheln werden gleich beschriftet
            val bezugGross = zeile(namensStil, HeatmapBeschriftung.BEZUG_KUERZEL).size.width.toFloat()
            val bezugKlein = zeile(kleinStil, HeatmapBeschriftung.BEZUG_KUERZEL).size.width.toFloat()
            val bezugProzent = zeile(prozentStil, Fmt.signedPct(1.0, 2)).size.width.toFloat()
            kacheln.map { k ->
                val r = k.r
                val platz = r.w - innen
                // Zahlenkuerzel (000660, 7203) ersetzt der Name, passt er nicht, sein erstes Wort; ohne Namen bleibt die Kachel leer
                val kandidaten = HeatmapBeschriftung.kandidaten(k.wert.symbol, k.wert.name)
                if (kandidaten.isEmpty()) return@map Beschriftung(null, null)
                val kurz = kandidaten.firstOrNull { zeile(namensStil, it).size.width <= platz }
                    ?: kandidaten.firstOrNull { zeile(kleinStil, it).size.width <= platz } ?: kandidaten.first()
                val gross = zeile(namensStil, kurz)
                val klein = zeile(kleinStil, kurz)
                val prozente = if (r.w >= prozentMin.first && r.h >= prozentMin.second) {
                    listOfNotNull(
                        k.wert.aenderung?.let { Fmt.signedPct(it, 2) },
                        k.wert.aenderung?.let { Fmt.signedPct(it, 1) },
                    ).map { zeile(prozentStil, it) }
                } else emptyList()
                val block = if (gezoomt && platz > 0 && r.w >= nameMin.first && r.h >= nameMin.second) {
                    messer.measure(k.wert.name, namensBlock, maxLines = 3, constraints = Constraints(maxWidth = platz.toInt()))
                        .takeIf { !it.hasVisualOverflow }
                } else null
                val nameWort = if (block != null) {
                    k.wert.name.split(' ').filter { it.isNotBlank() }.maxOfOrNull { zeile(namensStil, it).size.width.toFloat() } ?: 0f
                } else 0f
                val wahl = HeatmapBeschriftung.waehlen(
                    r.w, r.h, innen,
                    HeatmapBeschriftung.Masse(
                        masz(gross), masz(klein), prozente.map { masz(it) }, block?.let { masz(it) }, nameWort,
                        bezugGross, bezugKlein, bezugProzent,
                    ),
                    gezoomt,
                )
                // [0.78] KGV unter der Aenderung, wenn die Kachel dafuer reicht und der Wert eines hat
                fun mitKgv(name: TextLayoutResult?, prozent: TextLayoutResult): Beschriftung {
                    val zeile = HeatmapBeschriftung.kgvWert(k.wert.kgv)?.let { zeile(prozentStil, S.heatmapKgv(Fmt.num(it, 1))) }
                    val belegt = (name?.size?.height ?: 0) + prozent.size.height
                    return Beschriftung(name, prozent, zeile?.takeIf { HeatmapBeschriftung.zusatzPasst(r.w, r.h, innen, belegt.toFloat(), masz(it)) })
                }
                when (wahl.stufe) {
                    HeatmapBeschriftung.Stufe.NAME_PROZENT -> mitKgv(block, prozente[wahl.prozent])
                    HeatmapBeschriftung.Stufe.KUERZEL_PROZENT -> mitKgv(gross, prozente[wahl.prozent])
                    HeatmapBeschriftung.Stufe.KUERZEL -> Beschriftung(klein, null)
                    HeatmapBeschriftung.Stufe.NICHTS -> Beschriftung(null, null)
                }
            }
        }
        // Lage auf dem Bildschirm: Gruppe waechst mit zoom / stufe, Rand und Kopfzeile bleiben gleich gross
        fun gruppenRect(g: Gruppe) = HeatmapZoomLage.flaeche(g.r, zoom / stufe, versatz.x, versatz.y).let { Treemap.Rechteck(it.x, it.y, it.w, it.h) }
        fun kachelRect(t: Kachel): Treemap.Rechteck {
            val k = zoom / stufe
            return if (t.g < 0) HeatmapZoomLage.kachel(t.r, Treemap.Rechteck(0f, 0f, w * stufe, h * stufe), 0f, 0f, k, versatz.x, versatz.y)
            else gruppen[t.g].let { HeatmapZoomLage.kachel(t.r, it.r, rand, it.oben, k, versatz.x, versatz.y) }
        }
        val padX = with(dichte) { 4.dp.toPx() }
        // Oben links der Kopfzeile auf dem Bildschirm oder null, wenn der Name im Ausschnitt nicht ganz Platz hat
        fun kopfLage(g: Gruppe, size: Size): Pair<Float, Float>? =
            g.kopf?.let { HeatmapZoomLage.kopfPosition(gruppenRect(g), it.size.width.toFloat(), it.size.height.toFloat(), rand, padX, size.width, size.height) }
        // Tippflaeche der Kopfzeile einer Gruppe, mindestens 28 dp hoch
        val kopfTipp = with(dichte) { 28.dp.toPx() }
        Canvas(
            Modifier.fillMaxSize().clipToBounds().graphicsLayer()
                // Ein Finger bei Zoom 1 gehoert dem Pager und dem Tippen; zwei Finger oder ein Zoom darueber der Flaeche
                .kartenZoom(kz, MAX_ZOOM)
                .pointerInput(kacheln, gruppen) {
                    detectTapGestures(
                        onLongPress = { p ->
                            kacheln.firstOrNull { (p.x to p.y) in kachelRect(it) }?.let {
                                haptik.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                menue = it.wert.symbol to p
                            }
                        },
                        onDoubleTap = { p ->
                            if (zoom > 1.01f) { zoom = 1f; versatz = Offset.Zero } else {
                                val neu = 2.5f
                                versatz = begrenzt(p - p * neu, neu)
                                zoom = neu
                            }
                        },
                    ) { p ->
                        // Erst die Kopfzeilen der Gruppen (Grossansicht), dann die Kacheln (Aktie)
                        val g = gruppen.firstOrNull { g ->
                            val gz = gruppenRect(g)
                            g.kopf != null && kopfLage(g, Size(w, h)) != null && p.x in gz.x..(gz.x + gz.w) && p.y >= gz.y && p.y <= gz.y + minOf(maxOf(kopfTipp, g.kopf.size.height + 2 * rand), gz.h)
                        }
                        if (g != null) onGruppe(g.schluessel, g.name)
                        else kacheln.firstOrNull { (p.x to p.y) in kachelRect(it) }?.let { onOpen(it.wert.symbol) }
                    }
                },
        ) {
            gruppen.forEach { g ->
                val t = g.kopf ?: return@forEach
                val lage = kopfLage(g, size) ?: return@forEach
                drawText(t, color = kopfFarbe, topLeft = Offset(lage.first, lage.second))
            }
            kacheln.forEachIndexed { i, k ->
                val r = kachelRect(k)
                if (r.w <= 2 * luecke || r.h <= 2 * luecke) return@forEachIndexed
                if (r.x > size.width || r.y > size.height || r.x + r.w < 0 || r.y + r.h < 0) return@forEachIndexed
                val farbe = kachelFarbe(k.wert.aenderung, heat.hoch, heat.runter, heat.neutral, voll)
                drawRoundRect(farbe, Offset(r.x + luecke, r.y + luecke), Size(r.w - 2 * luecke, r.h - 2 * luecke), CornerRadius(3 * density))
                val b = beschriftungen.getOrNull(i) ?: return@forEachIndexed
                val schrift = aufFarbe(farbe)
                val name = b.name ?: return@forEachIndexed
                val prozent = b.prozent
                val kgv = b.kgv
                val hoehe = name.size.height + (prozent?.size?.height ?: 0) + (kgv?.size?.height ?: 0)
                val y0 = r.y + (r.h - hoehe) / 2
                drawText(name, color = schrift, topLeft = Offset(r.x + (r.w - name.size.width) / 2, y0))
                if (prozent != null) drawText(prozent, color = schrift.copy(alpha = 0.9f), topLeft = Offset(r.x + (r.w - prozent.size.width) / 2, y0 + name.size.height))
                if (prozent != null && kgv != null) drawText(kgv, color = schrift.copy(alpha = 0.75f), topLeft = Offset(r.x + (r.w - kgv.size.width) / 2, y0 + name.size.height + prozent.size.height))
            }
        }
        menue?.let { (symbol, p) ->
            Box(Modifier.offset { androidx.compose.ui.unit.IntOffset(p.x.toInt(), p.y.toInt()) }) { AktieMenue(state, symbol, true) { menue = null } }
        }
        if (zoom > 1.01f) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(Radius.chip))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f))
                    .clickable(role = Role.Button) { zoom = 1f; versatz = Offset.Zero }.padding(horizontal = 12.dp, vertical = 6.dp),
            ) { Text(S.heatmapZoomZurueck, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false) }
        }
    }
}

// Farbskala zum Zeitraum, am Tag von −3 % bis +3 %.
@Composable
private fun Legende(zeitraum: Int, modifier: Modifier) {
    val signal = LocalSignal.current
    val heat = heatFarben()
    val voll = HeatmapBeschriftung.VOLL_PROZENT.getOrElse(zeitraum) { 3.0 }
    Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        listOf(-1.0, -2.0 / 3, -1.0 / 3, 0.0, 1.0 / 3, 2.0 / 3, 1.0).map { it * voll }.forEach { v ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(44.dp)) {
                Box(Modifier.size(width = 40.dp, height = 10.dp).clip(RoundedCornerShape(3.dp)).background(kachelFarbe(v, heat.hoch, heat.runter, heat.neutral, voll)))
                Spacer(Modifier.height(3.dp))
                Text(
                    if (v == 0.0) "0" else Fmt.signedPct(v, 0), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                )
            }
        }
    }
}
