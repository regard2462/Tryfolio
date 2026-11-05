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

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss)) {
        ScreenHeader(S.heatmap, null, onBack)
        // Die weisse Pille gleitet beim Wischen mit, die Leiste rollt mit
        MitlaufendeReiter(
            quellen.size, pager,
            titel = { i -> heatmapQuelleTitel(quellen[i]) },
            modifier = Modifier.padding(vertical = 6.dp),
        ) { scope.launch { pager.animateScrollToPage(it) } }
        GruppenPillen(gruppiert) { gruppiert = it }
        ZeitraumWahl(zeitraum) { zeitraum = it }
        HorizontalPager(state = pager, beyondViewportPageCount = 1, modifier = Modifier.fillMaxWidth().weight(1f).mausWischen(pager), key = { quellen.getOrElse(it) { "$it" } }) { i ->
            HeatmapSeite(state, quellen[i], gruppiert, zeitraum, onOpen)
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

// Heatmap als Reiter der Seite Maerkte, mit einem Pager wie Copytrading; die Nachbarquelle ist vorgeladen.
// An der ersten Quelle nimmt der Pager der Seite einen Wisch nach rechts (zurueck zu den Trends).
@Composable
fun HeatmapReiter(state: AppState, onOpen: (String) -> Unit) {
    val quellen = heatmapQuellen(state)
    var gemerkt by rememberSaveable { mutableStateOf(HeatmapQuelle.MARKT) }
    var gruppiert by rememberSaveable { mutableStateOf(HeatmapGruppe.KEINE) }
    var zeitraum by rememberSaveable { mutableIntStateOf(0) }
    val pager = rememberPagerState(initialPage = quellen.indexOf(gemerkt).coerceAtLeast(0)) { quellen.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager, quellen) { snapshotFlow { pager.currentPage }.collect { i -> quellen.getOrNull(i)?.let { gemerkt = it } } }
    Column(Modifier.fillMaxSize()) {
        MitlaufendeReiter(quellen.size, pager, titel = { i -> heatmapQuelleTitel(quellen[i]) }, modifier = Modifier.padding(vertical = 6.dp)) {
            scope.launch { pager.animateScrollToPage(it) }
        }
        GruppenPillen(gruppiert) { gruppiert = it }
        ZeitraumWahl(zeitraum) { zeitraum = it }
        HorizontalPager(state = pager, beyondViewportPageCount = 1, modifier = Modifier.fillMaxWidth().weight(1f).mausWischen(pager), key = { quellen.getOrElse(it) { "$it" } }) { i ->
            HeatmapSeite(state, quellen[i], gruppiert, zeitraum, onOpen)
        }
        Legende(zeitraum, Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 20.dp))
    }
}

// Eine Quelle der Heatmap: Zaehler und Kacheln; alle zwei Minuten neu, solange die Seite zu sehen ist. Laengere
// Zeitraeume rechnet AppState aus Tagesschlusskursen und merkt sie sich eine halbe Stunde.
@Composable
private fun HeatmapSeite(state: AppState, quelle: String, gruppiert: String, zeitraum: Int, onOpen: (String) -> Unit) {
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
                Text("‹ " + S.heatmapAlle, style = MaterialTheme.typography.labelLarge, color = LocalSignal.current.link, maxLines = 1, softWrap = false)
                Spacer(Modifier.width(10.dp))
                AuslaufText(titel, maxLines = 1, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            }
        }
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
                else -> HeatmapFlaeche(
                    liste, if (fokus != null) HeatmapGruppe.KEINE else gruppiert, HeatmapBeschriftung.VOLL_PROZENT.getOrElse(zeitraum) { 3.0 }, onOpen,
                    onGruppe = { schluessel, titel -> fokus = schluessel to titel }, modifier = Modifier.fillMaxSize(),
                )
            }
        }
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

private class Kachel(val wert: AppState.HeatmapWert, val r: Treemap.Rechteck)
private class Gruppe(val schluessel: String, val name: String, val r: Treemap.Rechteck, val kopf: TextLayoutResult?)

// Flaechen fuer alle Werte; gruppiert erst die Branchen oder Regionen, darin die Werte unter einer Kopfzeile. kopf
// liefert zu Name, Breite und Hoehe der Gruppe die gesetzte Kopfzeile oder null, wenn sie nicht ganz hineinpasst.
private fun anordnen(
    werte: List<AppState.HeatmapWert>, gruppiert: String, w: Float, h: Float, rand: Float,
    kopf: (String, Float, Float) -> TextLayoutResult?,
): Pair<List<Kachel>, List<Gruppe>> {
    if (gruppiert == HeatmapGruppe.KEINE) return Treemap.anordnen(werte.map { it.gewicht }, w, h).mapIndexed { i, r -> Kachel(werte[i], r) } to emptyList()
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
        // Die Kopfzeile mit dem Namen der Branche nur, wo der ganze Name Platz hat
        val gesetzt = kopf(titel, g.w, g.h)
        val oben = gesetzt?.let { it.size.height + 2 * rand } ?: 0f
        val innen = Treemap.Rechteck(g.x + rand, g.y + rand + oben, (g.w - 2 * rand).coerceAtLeast(0f), (g.h - 2 * rand - oben).coerceAtLeast(0f))
        gruppen += Gruppe(branche, titel, g, gesetzt)
        Treemap.anordnen(mitglieder.map { it.gewicht }, innen.w, innen.h, innen.x, innen.y).forEachIndexed { k, r -> kacheln += Kachel(mitglieder[k], r) }
    }
    return kacheln to gruppen
}

// Farbe nach Aenderung: neutral um null, dann zunehmend gruen oder rot, ab voll Prozent voll (wie im Widget).
private fun kachelFarbe(aenderung: Double?, hoch: Color, runter: Color, neutral: Color, voll: Double = 3.0): Color =
    Color(HeatmapBeschriftung.farbe(aenderung, voll, hoch.toArgb(), runter.toArgb(), neutral.toArgb()))

private class Beschriftung(val name: TextLayoutResult?, val prozent: TextLayoutResult?)

@Composable
private fun HeatmapFlaeche(
    werte: List<AppState.HeatmapWert>, gruppiert: String, voll: Double, onOpen: (String) -> Unit,
    onGruppe: (String, String) -> Unit, modifier: Modifier,
) {
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
        val (kacheln, gruppen) = remember(werte, gruppiert, w, h) {
            // Gruppennamen ganz: erst einzeilig in kleiner werdender Schrift, dann auf zwei Zeilen; sonst ohne Kopf
            anordnen(werte, gruppiert, w, h, rand) { titel, gw, gh ->
                val platz = gw - 8 * dichte.density
                if (gw < kopf * 2 || gh < kopf * 3 || platz <= 0) return@anordnen null
                listOf(10.sp, 9.sp, 8.sp).firstNotNullOfOrNull { gr ->
                    messer.measure(titel, TextStyle(fontSize = gr, fontWeight = FontWeight.SemiBold), maxLines = 1, softWrap = false)
                        .takeIf { it.size.width <= platz }
                } ?: if (gh < kopf * 5) null else messer.measure(
                    titel, TextStyle(fontSize = 8.sp, fontWeight = FontWeight.SemiBold, lineHeight = 9.sp, hyphens = Hyphens.None),
                    maxLines = 2, constraints = Constraints(maxWidth = platz.toInt()),
                ).takeIf { !it.hasVisualOverflow && it.lineCount <= 2 }
            }
        }
        // Zoom (5.94): mit zwei Fingern zoomen und verschieben, doppelt tippen zoomt hinein oder zurueck. Bildschirm =
        // Flaeche mal zoom plus versatz; vergroessert wird nur die Lage der Kacheln, die Schrift bleibt gleich gross.
        var zoom by remember(werte, gruppiert) { mutableFloatStateOf(1f) }
        var versatz by remember(werte, gruppiert) { mutableStateOf(Offset.Zero) }
        fun begrenzt(o: Offset, s: Float) = Offset(o.x.coerceIn(w - w * s, 0f), o.y.coerceIn(h - h * s, 0f))
        val stufe = ZOOM_STUFEN.last { it <= zoom + 1e-3f }
        // Beschriftung nach Groesse der Kachel (HeatmapBeschriftung): ungezoomt das Kuerzel mit Prozent, gezoomt der volle
        // Name, wo er ganz hineinpasst. Nichts wird gekuerzt oder kleiner gesetzt.
        val namensStil = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        val kleinStil = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        val prozentStil = TextStyle(fontSize = 11.sp)
        val namensBlock = namensStil.copy(textAlign = TextAlign.Center, lineHeight = 15.sp)
        val gezoomt = stufe > 1.01f
        val beschriftungen = remember(kacheln, dichte, stufe) {
            val innen = with(dichte) { 8.dp.toPx() }
            val prozentMin = with(dichte) { 28.dp.toPx() } to with(dichte) { 24.dp.toPx() }
            val nameMin = with(dichte) { 52.dp.toPx() } to with(dichte) { 36.dp.toPx() }
            fun zeile(stil: TextStyle, t: String) = messer.measure(t, stil, maxLines = 1, softWrap = false)
            fun masz(l: TextLayoutResult) = HeatmapBeschriftung.Text(l.size.width.toFloat(), l.size.height.toFloat())
            kacheln.map { k ->
                val r = Treemap.Rechteck(k.r.x, k.r.y, k.r.w * stufe, k.r.h * stufe)
                val platz = r.w - innen
                val kurz = HeatmapBeschriftung.kuerzel(k.wert.symbol)
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
                    HeatmapBeschriftung.Masse(masz(gross), masz(klein), prozente.map { masz(it) }, block?.let { masz(it) }, nameWort),
                    gezoomt,
                )
                when (wahl.stufe) {
                    HeatmapBeschriftung.Stufe.NAME_PROZENT -> Beschriftung(block, prozente[wahl.prozent])
                    HeatmapBeschriftung.Stufe.KUERZEL_PROZENT -> Beschriftung(gross, prozente[wahl.prozent])
                    HeatmapBeschriftung.Stufe.KUERZEL -> Beschriftung(klein, null)
                    HeatmapBeschriftung.Stufe.NICHTS -> Beschriftung(null, null)
                }
            }
        }
        // Tippflaeche der Kopfzeile einer Gruppe, mindestens 28 dp hoch
        val kopfTipp = with(dichte) { 28.dp.toPx() }
        Canvas(
            Modifier.fillMaxSize().clipToBounds().graphicsLayer()
                // Mit denselben Keys wie zoom und versatz, sonst schreibt die Geste nach Antippen einer Gruppe in alte Werte
                .pointerInput(w, h, werte, gruppiert) {
                    // Ein Finger bei Zoom 1 gehoert dem Pager und dem Tippen; zwei Finger oder ein Zoom darueber der Flaeche
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val ev = awaitPointerEvent()
                            val finger = ev.changes.count { it.pressed }
                            // Beim Loslassen hat das Ereignis keinen gedrueckten Finger mehr; die Mitte ist dann
                            // Offset.Unspecified (NaN) und machte den Versatz zu NaN: die Flaeche wurde schwarz (5.95)
                            val mitte = ev.calculateCentroid(useCurrent = true)
                            if ((finger >= 2 || zoom > 1.01f) && mitte.isSpecified) {
                                val neu = (zoom * ev.calculateZoom()).coerceIn(1f, MAX_ZOOM)
                                val k = neu / zoom
                                versatz = begrenzt(mitte - (mitte - versatz) * k + ev.calculatePan(), neu)
                                zoom = neu
                                ev.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (ev.changes.any { it.pressed })
                    }
                }
                .pointerInput(kacheln, gruppen) {
                    detectTapGestures(
                        onDoubleTap = { p ->
                            if (zoom > 1.01f) { zoom = 1f; versatz = Offset.Zero } else {
                                val neu = 2.5f
                                versatz = begrenzt(p - p * neu, neu)
                                zoom = neu
                            }
                        },
                    ) { p ->
                        val welt = (p - versatz) / zoom
                        // Erst die Kopfzeilen der Gruppen (Grossansicht), dann die Kacheln (Aktie)
                        val g = gruppen.firstOrNull { g ->
                            g.kopf != null && welt.x in g.r.x..(g.r.x + g.r.w) && p.y >= g.r.y * zoom + versatz.y && p.y <= g.r.y * zoom + versatz.y + maxOf(kopfTipp, g.kopf.size.height + 2 * rand)
                        }
                        if (g != null) onGruppe(g.schluessel, g.name)
                        else kacheln.firstOrNull { (welt.x to welt.y) in it.r }?.let { onOpen(it.wert.symbol) }
                    }
                },
        ) {
            gruppen.forEach { g ->
                g.kopf?.let { t -> drawText(t, color = kopfFarbe, topLeft = Offset(g.r.x * zoom + versatz.x + 4 * density, g.r.y * zoom + versatz.y + rand)) }
            }
            kacheln.forEachIndexed { i, k ->
                val r = Treemap.Rechteck(k.r.x * zoom + versatz.x, k.r.y * zoom + versatz.y, k.r.w * zoom, k.r.h * zoom)
                if (r.w <= 2 * luecke || r.h <= 2 * luecke) return@forEachIndexed
                if (r.x > size.width || r.y > size.height || r.x + r.w < 0 || r.y + r.h < 0) return@forEachIndexed
                val farbe = kachelFarbe(k.wert.aenderung, signal.up, signal.down, neutral, voll)
                drawRoundRect(farbe, Offset(r.x + luecke, r.y + luecke), Size(r.w - 2 * luecke, r.h - 2 * luecke), CornerRadius(3 * density))
                val b = beschriftungen.getOrNull(i) ?: return@forEachIndexed
                val schrift = aufFarbe(farbe)
                val name = b.name ?: return@forEachIndexed
                val prozent = b.prozent
                val hoehe = name.size.height + (prozent?.size?.height ?: 0)
                val y0 = r.y + (r.h - hoehe) / 2
                drawText(name, color = schrift, topLeft = Offset(r.x + (r.w - name.size.width) / 2, y0))
                if (prozent != null) drawText(prozent, color = schrift.copy(alpha = 0.9f), topLeft = Offset(r.x + (r.w - prozent.size.width) / 2, y0 + name.size.height))
            }
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
    val neutral = MaterialTheme.colorScheme.surfaceContainerHighest
    val voll = HeatmapBeschriftung.VOLL_PROZENT.getOrElse(zeitraum) { 3.0 }
    Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        listOf(-1.0, -2.0 / 3, -1.0 / 3, 0.0, 1.0 / 3, 2.0 / 3, 1.0).map { it * voll }.forEach { v ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(44.dp)) {
                Box(Modifier.size(width = 40.dp, height = 10.dp).clip(RoundedCornerShape(3.dp)).background(kachelFarbe(v, signal.up, signal.down, neutral, voll)))
                Spacer(Modifier.height(3.dp))
                Text(
                    if (v == 0.0) "0" else Fmt.signedPct(v, 0), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                )
            }
        }
    }
}
