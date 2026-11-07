package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Zahlenzeit
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.IpoKalender
import de.troetenrepublik.app.market.ohneAbbruch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import de.troetenrepublik.app.plattform.mausWischen
import kotlinx.coroutines.launch
import kotlin.math.abs

// Route der Seite „Maerkte“ (Kachel der Startseite, Suche, Widget „Schnellzugriff“).
const val MAERKTE_ROUTE = "maerkte"

// Reiter der Seite in dieser Reihenfolge.
private enum class MaerkteReiter { HEATMAP, GLOBUS, TRENDS, BEWEGUNGEN, STIMMUNG, AUSBLICK }

// Seite „Maerkte“ (seit 5.91): was am Markt los ist, unabhaengig vom eigenen Depot. Reiter Heatmap, Trends (Branchen,
// Yahoo, Reddit), Bewegungen (groesste Bewegungen, Boersengaenge und Dividenden), Stimmung (Fear & Greed, Marktphase,
// Fruehwarnzeichen, Marktkurven, Terminmaerkte) und Ausblick (was der Markt erwartet, Blasen-Detektor, seit 5.92).
@Composable
fun MaerkteScreen(state: AppState, onBack: () -> Unit, onOpen: (String) -> Unit, oeffnen: (String) -> Unit, startReiter: String? = null, startEbene: String? = null) {
    val reiter = MaerkteReiter.entries
    var gewaehlt by rememberSaveable { mutableIntStateOf(reiter.indexOfFirst { it.name.equals(startReiter, ignoreCase = true) }.coerceAtLeast(0)) }
    val pager = rememberPagerState(initialPage = gewaehlt.coerceIn(0, reiter.size - 1)) { reiter.size }
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { gewaehlt = it } }
    val scope = rememberCoroutineScope()
    val listen = remember { List(reiter.size) { LazyListState() } }
    val trends = remember { TrendsLage() }
    val dismiss = rememberSwipeDownDismiss(onBack)

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .sheetDismiss(dismiss) {
                when (reiter.getOrNull(pager.currentPage)) {
                    MaerkteReiter.TRENDS -> trends.index == 0 && trends.versatz == 0
                    MaerkteReiter.HEATMAP, null -> true
                    MaerkteReiter.GLOBUS -> !listen[pager.currentPage].canScrollBackward
                    else -> !listen[pager.currentPage].canScrollBackward
                }
            },
    ) {
        ScreenHeader(S.maerkteTitel, null, onBack)
        MitlaufendeReiter(
            anzahl = reiter.size,
            pager = pager,
            titel = { i ->
                when (reiter[i]) {
                    MaerkteReiter.BEWEGUNGEN -> S.maerkteBewegungen
                    MaerkteReiter.STIMMUNG -> S.widgetStimmung
                    MaerkteReiter.AUSBLICK -> S.maerkteAusblick
                    MaerkteReiter.TRENDS -> S.trendsTitel
                    MaerkteReiter.HEATMAP -> S.heatmap
                    MaerkteReiter.GLOBUS -> S.globusTitel
                }
            },
            modifier = Modifier.padding(vertical = 12.dp),
        ) { i -> scope.launch { pager.animateScrollToPage(i) } }
        HorizontalPager(
            state = pager, beyondViewportPageCount = 1, verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth().weight(1f).mausWischen(pager),
        ) { seite ->
            when (reiter[seite]) {
                MaerkteReiter.BEWEGUNGEN -> BewegungenReiter(state, listen[seite], onOpen, oeffnen)
                MaerkteReiter.STIMMUNG -> LazyColumn(Modifier.fillMaxSize(), state = listen[seite]) {
                    item(key = "stimmung") { StimmungsAbschnitt(state, onOpen) }
                    item { Spacer(Modifier.height(40.dp)) }
                }
                MaerkteReiter.AUSBLICK -> AusblickReiter(state, listen[seite], oeffnen)
                MaerkteReiter.TRENDS -> TrendsSeite(state, onOpen, trends, merken = true, mitStimmung = false)
                MaerkteReiter.HEATMAP -> HeatmapReiter(state, onOpen)
                MaerkteReiter.GLOBUS -> GlobusReiter(
                    state, listen[seite], aktiv = pager.currentPage == seite, onOpen = onOpen,
                    zuStimmung = { scope.launch { pager.animateScrollToPage(reiter.indexOf(MaerkteReiter.STIMMUNG)) } },
                    startEbene = startEbene,
                )
            }
        }
    }
}

// Reiter „Bewegungen“: die acht Aktien mit der groessten Tagesaenderung, die Boersengaenge und die Dividenden des
// naechsten Handelstags, jeweils mit dem Weg zur ganzen Liste. Ohne Kurse die groessten Bewegungen unter den
// eingebauten Werten.
@Composable
private fun BewegungenReiter(state: AppState, liste: LazyListState, onOpen: (String) -> Unit, oeffnen: (String) -> Unit) {
    val quotes by state.quotes.collectAsStateWithLifecycle()
    val catalog = rememberCatalog()
    val ipos = catalog?.ipos?.abgeschlossen.orEmpty()
    val alle = rememberBewegungen(state)
    val ipoKurse = rememberSnapshot(state, remember(catalog) { catalog?.ipos?.abgeschlossen?.map { it.s }.orEmpty() })
    val bewegungen = remember(alle, quotes) {
        alle.ifEmpty {
            de.troetenrepublik.app.market.Symbols.all.mapNotNull { i -> quotes[i.symbol]?.takeIf { it.prevClose > 0 }?.let { Triple(i.symbol, i.name, it) } }
        }.sortedByDescending { abs(it.third.changePct) }.take(8)
    }

    // Die naechsten angekuendigten Boersengaenge aus dem Kalender der Nasdaq (5.95)
    var ipoKalender by remember { mutableStateOf<List<IpoKalender.Angebot>?>(null) }
    LaunchedEffect(Unit) { ipoKalender = ohneAbbruch { state.ipoKalender() }.getOrNull() }
    val demnaechst = remember(ipoKalender) {
        ipoKalender.orEmpty().filter { it.status == IpoKalender.Status.ANGEKUENDIGT }.sortedBy { it.tag ?: Long.MAX_VALUE }.take(3)
    }

    // Naechster Handelstag in New York (heute an Werktagen), fuer Quartalszahlen und Dividenden
    val tag = remember {
        val heute = java.time.LocalDate.now(java.time.ZoneId.of("America/New_York"))
        de.troetenrepublik.app.market.Quartalszahlen.woche(heute, 0).first { it >= heute || it.dayOfWeek == java.time.DayOfWeek.FRIDAY }
    }
    var zahlenTag by remember { mutableStateOf<Pair<java.time.LocalDate, List<de.troetenrepublik.app.market.Quartalszahlen.Termin>>?>(null) }
    var zahlenFehlt by remember { mutableStateOf(false) }
    // Ein Tipp auf einen Termin zeigt erst die Uebersicht (5.97); eigene Symbole dafuer wie auf der Seite der Zahlen
    var zahlenBlatt by remember { mutableStateOf<de.troetenrepublik.app.market.Quartalszahlen.Termin?>(null) }
    val stand by state.account.collectAsStateWithLifecycle()
    val eigeneSymbole = remember(stand) { (stand.positions.filter { !it.isDerivative }.map { it.symbol } + stand.watchlist).toSet() }
    // Gleiche Wahl Bekannte oder Alle wie auf der Seite der Quartalszahlen (0.68)
    val einstellungen by state.settingsFlow.collectAsStateWithLifecycle()
    val zahlenAlle = einstellungen.quartalszahlenAlle
    LaunchedEffect(tag) {
        val l = ohneAbbruch { state.quartalszahlen(listOf(tag))[tag] }.getOrNull()
        val zusatz = ohneAbbruch { state.eigeneZahlentermine(listOf(tag), l.orEmpty().map { it.symbol }.toSet()) }.getOrDefault(emptyList())
        currentCoroutineContext().ensureActive()
        if (l == null && zusatz.isEmpty()) { zahlenFehlt = true; return@LaunchedEffect }
        val konto = state.account.value
        val eigene = (konto.positions.filter { !it.isDerivative }.map { it.symbol } + konto.watchlist).toSet()
        // Alle Termine des Tages; welche gezeigt werden, folgt der Wahl Bekannte oder Alle (0.68)
        zahlenTag = tag to (
            de.troetenrepublik.app.market.Quartalszahlen.ohneDoppelteGattungen(l.orEmpty().map { if (it.symbol in eigene) it.copy(eigen = true) else it }) + zusatz
        )
    }

    LazyColumn(Modifier.fillMaxSize(), state = liste) {
        item(key = "bewegungen-kopf") { SectionHeader(S.topMovers, chevron = true, onClick = { oeffnen("discover/movers") }) }
        item(key = "bewegungen-deutung") { Text(S.maerkteBewegungenDeutung, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
        if (bewegungen.isEmpty()) item(key = "bewegungen-leer") { EmptyHint(S.trLaedt) }
        items(bewegungen, key = { "m-" + it.first }) { (symbol, name, q) ->
            AssetRow(
                state = state,
                symbol = symbol, name = name, subtitle = Fmt.price(q.price),
                change = q.changePct, changeText = Fmt.pct(abs(q.changePct)), onClick = { onOpen(symbol) },
            )
        }

        // Die naechsten angekuendigten Boersengaenge aus dem Kalender der Nasdaq (5.95), darunter die juengsten aus
        // dem Katalog mit Kurs
        if (demnaechst.isNotEmpty() || ipos.isNotEmpty()) {
            item(key = "ipos-kopf") { SectionHeader(S.ipos, chevron = true, onClick = { oeffnen("discover/ipos") }) }
            items(demnaechst, key = { "ik-" + it.deal }) { a ->
                val datum = a.tag?.let { Fmt.dayMonthNum(mittag(java.time.LocalDate.ofEpochDay(it))) }
                AssetRow(
                    symbol = a.symbol ?: a.name, name = a.name,
                    subtitle = listOfNotNull(a.symbol, a.boerse?.let(::boerseKurz), datum?.let { S.ipoStatusTag(a.status.ordinal, it) }).joinToString(PUNKT).ifBlank { null },
                    onClick = { oeffnen("discover/ipos") },
                )
            }
            items(ipos.take(5), key = { "i-" + it.s }) { ipo ->
                val q = ipoKurse[ipo.s] ?: quotes[ipo.s]
                AssetRow(
                    state = state,
                    symbol = ipo.s, name = ipo.name, subtitle = listOf(ipo.boerse, ipo.datum).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null },
                    change = q?.changePct, changeText = q?.let { Fmt.pct(abs(it.changePct)) }, onClick = { onOpen(ipo.s) },
                )
            }
        }

        // Quartalszahlen (5.94): die Werte des naechsten Handelstags (bekannte oder alle, wie gewaehlt), die ganze Woche auf eigener Seite
        item(key = "qz-kopf") { SectionHeader(S.qzTitel, chevron = true, onClick = { oeffnen("discover/earnings") }) }
        val qz = zahlenTag
        if (qz == null) item(key = "qz-lade") { EmptyHint(if (!zahlenFehlt) S.loading else S.qzFehlt) }
        if (qz != null) {
            item(key = "qz-tag") {
                Text(
                    wochentag(qz.first) + " " + Fmt.dayMonthNum(mittag(qz.first)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            // Die fuenf groessten des Tages aus den bekannten oder allen Werten (eigene zuerst), nach Zeitpunkt geordnet
            // und mit dessen Ueberschrift
            val fuenf = de.troetenrepublik.app.market.Quartalszahlen.vorschau(qz.second, zahlenAlle)
            if (fuenf.isEmpty()) item(key = "qz-leer") {
                if (qz.second.isEmpty()) EmptyHint(S.qzKeine)
                else Text(
                    S.qzKeineBekannten(qz.second.size),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            for (z in de.troetenrepublik.app.market.Quartalszahlen.Zeit.entries) {
                val g = fuenf.filter { it.zeit == z }
                if (g.isEmpty()) continue
                // Gleiche Uhrzeit aller Termine steht in der Gruppenueberschrift, sonst je Zeile (5.97)
                val gemeinsam = Zahlenzeit.gemeinsam(g.map { uhrzeitVon(it) })
                item(key = "qz-zeit-$z") {
                    PunktText(
                        listOfNotNull(S.qzZeit(z.ordinal), gemeinsam?.let { uhrKurz(it, qz.first) }).joinToString(PUNKT),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
                    )
                }
                items(g, key = { "qz-" + it.symbol }) { e ->
                    TerminZeile(e, uhr = if (gemeinsam == null) uhrKurz(uhrzeitVon(e), qz.first) else null) { zahlenBlatt = e }
                }
            }
        }

        // Dividenden (5.95): die Ex-Tage des naechsten Handelstags (bekannte oder alle, wie gewaehlt), die ganze Woche auf eigener Seite
        item(key = "dividenden") { DividendenAbschnitt(state, tag, onOpen, oeffnen) }
        item { Spacer(Modifier.height(40.dp)) }
    }
    zahlenBlatt?.let { QuartalsUeberblickBlatt(state, it, eigeneSymbole, onDismiss = { zahlenBlatt = null }, onOpen = onOpen) }
}

// Kandidaten fuer die groessten Bewegungen (seit 5.92): alle Aktien des Katalogs und die je 25 groessten Gewinner und
// Verlierer des US-Markts laut Yahoo, nur mit nennenswertem Umsatz (siehe movers). Symbol, Name, Kurs.
@Composable
internal fun rememberBewegungen(state: AppState): List<Triple<String, String, de.troetenrepublik.app.data.Quote>> {
    val catalog = rememberCatalog()
    var yahoo by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) { yahoo = runCatching { state.yahooBewegungen() }.getOrDefault(emptyList()) }
    val katalog = remember(catalog) { catalog?.aktien?.map { it.s }.orEmpty() }
    val snapshot = rememberSnapshot(state, remember(katalog, yahoo) { (katalog + yahoo).distinct() })
    return remember(snapshot, catalog, yahoo) {
        val stocks = catalog?.aktien.orEmpty()
        val bekannt = stocks.mapTo(HashSet()) { it.s }
        val ausKatalog = movers(snapshot, stocks).map { (st, q) -> Triple(st.s, st.n, q) }
        val extra = yahoo.filter { it !in bekannt }.mapNotNull { s ->
            snapshot[s]?.takeIf { it.prevClose > 0 && it.price > 0 && it.price * it.volume >= 2_000_000.0 }?.let { Triple(s, it.name.ifBlank { s }, it) }
        }
        ausKatalog + extra
    }
}
