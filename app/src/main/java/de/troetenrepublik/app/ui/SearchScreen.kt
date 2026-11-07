package de.troetenrepublik.app.ui

import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.i18n.I18n
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import de.troetenrepublik.app.plattform.horizontalScrollMaus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.data.Instrument
import de.troetenrepublik.app.engine.Suchverlauf
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.Symbols
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.abs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

// Entdecken und Suchen. Das Eingabefeld liegt unten am Daumen, darueber die Vorschlaege: beliebte Werte, dann nach
// Gattung sortiert. Groesste Bewegungen und Boersengaenge stehen auf der Seite „Maerkte“.
@Composable
fun SearchScreen(state: AppState, onOpen: (String) -> Unit, onBack: () -> Unit, onDiscover: (String) -> Unit = {}) {
    // Beim Oeffnen eines Werts schliesst die Tastatur
    val tastatur = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val fokus = androidx.compose.ui.platform.LocalFocusManager.current
    val oeffnen = onOpen
    @Suppress("NAME_SHADOWING")
    val onOpen: (String) -> Unit = { s -> fokus.clearFocus(); tastatur?.hide(); oeffnen(s) }
    // Ebenso beim Wechsel zu Maerkten und Listen, sonst bleibt die Tastatur ueber der naechsten Seite offen (0.69)
    val entdecken = onDiscover
    @Suppress("NAME_SHADOWING")
    val onDiscover: (String) -> Unit = { r -> fokus.clearFocus(); tastatur?.hide(); entdecken(r) }
    val quotes by state.quotes.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    // Suchverlauf (0.68): Anzeige mischt zuletzt und oft gesuchte Werte, siehe Suchverlauf.rangfolge
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val verlauf = remember(settings.suchverlauf) { Suchverlauf.rangfolge(Suchverlauf.lesen(settings.suchverlauf), System.currentTimeMillis()) }
    // Beim ersten Oeffnen steht der Cursor gleich im Suchfeld und die Tastatur ist offen; nach der Rueckkehr von einem
    // Wert bleibt sie zu
    val feldFokus = remember { androidx.compose.ui.focus.FocusRequester() }
    var fokussiert by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (fokussiert) return@LaunchedEffect
        fokussiert = true
        runCatching { feldFokus.requestFocus(); tastatur?.show() }
    }
    var remote by remember { mutableStateOf<List<MarketApi.SearchHit>>(emptyList()) }

    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val local = remember(query) { Symbols.search(query) }

    LaunchedEffect(Unit) { state.market.track(Symbols.all.map { it.symbol }) }

    // Kuerzlich gesucht (0.68): Kurse der Verlaufswerte nachladen
    LaunchedEffect(verlauf) { if (verlauf.isNotEmpty()) state.market.track(verlauf.map { it.symbol }) }

    // Gleiche Aktie an verschiedenen Plaetzen (gleicher Name, passender Euro-Kurs) teilt sich das Logo
    LaunchedEffect(remote, quotes) {
        if (remote.isEmpty()) return@LaunchedEffect
        val treffer = local.map { it.symbol to it.name } + remote.map { it.symbol to it.name }
        de.troetenrepublik.app.market.Gleichwerte.gruppen(treffer) { quotes[it]?.price }
            .forEach { de.troetenrepublik.app.market.Gleichwerte.verbinden(it) }
    }

    LaunchedEffect(query) {
        remote = emptyList(); searchError = false; searching = query.trim().length >= 2
        listState.scrollToItem(0)
        if (!searching) return@LaunchedEffect
        try {
            delay(350)
            val hits = state.market.search(query.trim())
            val known = local.map { it.symbol }.toSet()
            remote = hits.distinctBy { it.symbol }.filter { it.symbol !in known }.take(12)
            if (remote.isNotEmpty()) state.market.track(remote.map { it.symbol })
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { searchError = true }
        finally { searching = false }
    }

    // Entdecken: Kurse fuer den ganzen Katalog einmal gesammelt, daraus „Beliebt“
    val catalog = rememberCatalog()
    val stocks = catalog?.aktien.orEmpty()
    val snapshot = rememberSnapshot(state, remember(catalog) {
        catalog?.let { c -> c.aktien.map { it.s } + c.etfs.map { it.s } + c.basiswerte.map { it.s } }.orEmpty()
    })
    val popular = remember(snapshot, stocks) { popularStocks(snapshot, stocks) }
    // Vorschau „Aktien“: die drei beliebtesten Werte (wie „Beliebt“, ohne die deutschen Zusatzplaetze)
    val topStocks = remember(popular, stocks) {
        popular.take(3).mapNotNull { (symbol, _) -> stocks.firstOrNull { it.s == symbol } }
    }

    // Nach unten wischen schliesst die Suche, wie die Wertpapierseite
    val dismiss = rememberSwipeDownDismiss(onBack)
    Box(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(Modifier.fillMaxWidth().sheetDismiss(dismiss) { !listState.canScrollBackward }, state = listState) {

            item {
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, S.back) }
                }
                Text(S.search, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = 20.dp, top = 4.dp))
            }

            if (query.isBlank()) {

                // Kuerzlich gesucht (0.68): zuletzt und oft geoeffnete Treffer, siehe Suchverlauf.rangfolge
                if (verlauf.isNotEmpty()) {
                    item { SectionHeader(S.svKuerzlich, trailing = S.svLoeschen, onTrailingClick = { state.suchverlaufLeeren() }) }
                    items(verlauf, key = { "verlauf-" + it.symbol }) { e ->
                        val q = quotes[e.symbol]
                        val anzeige = e.name.ifBlank { Symbols.name(e.symbol) }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                AssetRow(
                                    state = state,
                                    symbol = e.symbol,
                                    name = anzeige,
                                    subtitle = e.symbol,
                                    change = q?.changePct,
                                    changeText = q?.let { Fmt.pct(abs(it.changePct)) },
                                    onClick = { state.suchverlaufMerken(e.symbol, e.name); onOpen(e.symbol) },
                                )
                            }
                            IconButton(onClick = { state.suchverlaufEntfernen(e.symbol) }, modifier = Modifier.padding(end = 8.dp)) {
                                Icon(Icons.Filled.Close, S.svEntfernen(anzeige), modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // Groesste Bewegungen, Boersengaenge, Stimmung und Heatmap auf einer eigenen Seite
                item { NavRow(S.maerkteTitel, S.maerkteSucheZeile) { onDiscover(MAERKTE_ROUTE) } }

                // Beliebt, siehe popularStocks
                item {
                    SectionHeader(S.popular)
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        popular.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                pair.forEach { (symbol, name) -> Box(Modifier.weight(1f)) { PopularChip(Instrument(symbol, name)) { onOpen(symbol) } } }
                            }
                        }
                    }
                }

                if (catalog != null) {
                    item { SectionHeader(S.stocks, chevron = true, onClick = { onDiscover("discover/filter/STOCKS") }) }
                    val shownStocks = topStocks.ifEmpty { stocks.take(3) }
                    items(shownStocks, key = { "stock-" + it.s }) { st ->
                        val q = snapshot[st.s]
                        AssetRow(state = state, symbol = st.s, name = st.n, change = q?.changePct, changeText = q?.let { Fmt.pct(abs(it.changePct)) }, onClick = { onOpen(st.s) })
                    }
                    item { SectionHeader(S.etfs, chevron = true, onClick = { onDiscover("discover/filter/ETFS") }) }
                    items(catalog.etfs.take(3), key = { "etf-" + it.s }) { e ->
                        val q = snapshot[e.s]
                        AssetRow(state = state, symbol = e.s, name = e.n, subtitle = e.a.ifBlank { null }, change = q?.changePct, changeText = q?.let { Fmt.pct(abs(it.changePct)) }, onClick = { onOpen(e.s) })
                    }
                    item { SectionHeader(S.derivatives, chevron = true, onClick = { onDiscover("discover/filter/DERIVATIVES") }) }
                    items(catalog.basiswerte.take(3), key = { "und-" + it.s }) { u ->
                        val q = snapshot[u.s]
                        AssetRow(
                            state = state,
                            symbol = u.s, name = if (I18n.lang == Lang.DE) u.nameDe else u.nameEn,
                            change = q?.changePct, changeText = q?.let { Fmt.pct(abs(it.changePct)) }, onClick = { onOpen(u.s) },
                        )
                    }
                } else {
                    groupSection(S.stocks, Symbols.dax + Symbols.usa, quotes, state, onOpen)
                    groupSection(S.etfs, Symbols.etfs, quotes, state, onOpen)
                }
                groupSection(S.crypto, Symbols.crypto, quotes, state, onOpen)

            } else {

                if (local.isEmpty() && remote.isEmpty()) {
                    item { EmptyHint(if (searching) S.searching else if (searchError) S.searchUnavailable else S.noResults) }
                }
                if (local.isNotEmpty()) {
                    item { SectionHeader(S.results) }
                    items(local, key = { it.symbol }) { inst ->
                        val q = quotes[inst.symbol]
                        AssetRow(
                            state = state,
                            symbol = inst.symbol,
                            name = inst.name,
                            subtitle = typeLabel(inst.type) + if (inst.isin.isNotBlank()) " · ${inst.isin}" else "",
                            change = q?.changePct,
                            changeText = q?.let { Fmt.pct(abs(it.changePct)) },
                            onClick = { state.suchverlaufMerken(inst.symbol, inst.name); onOpen(inst.symbol) },
                        )
                    }
                }
                if (remote.isNotEmpty()) {
                    item { SectionHeader(S.otherVenues) }
                    items(remote, key = { it.symbol }) { hit ->
                        val q = quotes[hit.symbol]
                        AssetRow(
                            state = state,
                            symbol = hit.symbol,
                            name = hit.name,
                            subtitle = "${hit.symbol} · ${hit.exchange}",
                            change = q?.changePct,
                            changeText = q?.let { Fmt.pct(abs(it.changePct)) },
                            onClick = { state.suchverlaufMerken(hit.symbol, hit.name); onOpen(hit.symbol) },
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(120.dp)) }
        }

        // Suchfeld unten, in Daumenreichweite
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).height(58.dp).clip(RoundedCornerShape(Radius.chip))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(S.searchPlaceholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.merge(MaterialTheme.typography.bodyLarge).copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.fillMaxWidth().focusRequester(feldFokus),
                    )
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, S.clear, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.groupSection(
    title: String,
    items: List<Instrument>,
    quotes: Map<String, de.troetenrepublik.app.data.Quote>,
    state: AppState,
    onOpen: (String) -> Unit,
) {
    item { SectionHeader(title) }
    items(items.take(6), key = { "$title-${it.symbol}" }) { inst ->
        val q = quotes[inst.symbol]
        AssetRow(
            state = state,
            symbol = inst.symbol,
            name = inst.name,
            subtitle = if (inst.type == AssetType.CRYPTO) inst.symbol.substringBefore('-') else S.sectorName(inst.sector),
            change = q?.changePct,
            changeText = q?.let { Fmt.pct(abs(it.changePct)) },
            onClick = { onOpen(inst.symbol) },
        )
    }
}

@Composable
private fun PopularChip(inst: Instrument, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(Radius.chip)).background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InstrumentLogo(inst.symbol, inst.name, size = 26)
        Spacer(Modifier.width(10.dp))
        NamenText(inst.name, style = MaterialTheme.typography.titleSmall)
    }
}
