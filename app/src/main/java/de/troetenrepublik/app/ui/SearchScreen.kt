package de.troetenrepublik.app.ui

import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.i18n.I18n
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.data.Instrument
import de.troetenrepublik.app.engine.Suchverlauf
import de.troetenrepublik.app.engine.Wertsuche
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.Symbols
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.abs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

// Entdecken und Suchen. Das Eingabefeld liegt unten am Daumen, darueber die Vorschlaege: groesste Bewegungen, beliebte
// Werte, dann nach Gattung sortiert. Boersengaenge und Stimmung stehen auf der Seite „Maerkte“.
@Composable
fun SearchScreen(state: AppState, onOpen: (String) -> Unit, onBack: () -> Unit, onDiscover: (String) -> Unit = {}) {
    // Beim Oeffnen eines Werts schliesst die Tastatur
    val tastatur = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val fokus = androidx.compose.ui.platform.LocalFocusManager.current
    val oeffnen = onOpen
    @Suppress("NAME_SHADOWING")
    val onOpen: (String) -> Unit = { s -> fokus.clearFocus(); tastatur?.hide(); oeffnen(s) }
    // Aus den Vorschlaegen geoeffnete Werte kommen ebenfalls in den Verlauf
    val oeffnenMerken: (String) -> Unit = { s -> state.suchverlaufMerken(s, Symbols.name(s)); onOpen(s) }
    // Ebenso beim Wechsel zu Maerkten und Listen, sonst bleibt die Tastatur ueber der naechsten Seite offen (0.69)
    val entdecken = onDiscover
    @Suppress("NAME_SHADOWING")
    val onDiscover: (String) -> Unit = { r -> fokus.clearFocus(); tastatur?.hide(); entdecken(r) }
    val quotes by state.quotes.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    // Suchverlauf (0.68): Anzeige mischt zuletzt und oft gesuchte Werte, siehe Suchverlauf.rangfolge
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val verlaufAlle = remember(settings.suchverlauf) { Suchverlauf.lesen(settings.suchverlauf) }
    var verlaufOffen by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val verlauf = remember(verlaufAlle, verlaufOffen, settings.suchverlaufAus) {
        if (settings.suchverlaufAus) emptyList()
        else Suchverlauf.rangfolge(verlaufAlle, System.currentTimeMillis(), if (verlaufOffen) Suchverlauf.MAX_AUFGEKLAPPT else Suchverlauf.MAX_ANGEZEIGT)
    }
    val konto by state.account.collectAsStateWithLifecycle()
    // Eingabe lesen: ISIN, WKN oder Freitext, siehe Wertsuche.auswerten
    val eingabe = remember(query) { Wertsuche.auswerten(query) }
    // Die Tastatur oeffnet sich erst, wenn das Suchfeld angetippt wird
    var remote by remember { mutableStateOf<List<MarketApi.SearchHit>>(emptyList()) }

    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // Treffer ohne Netz, schon ab dem ersten Zeichen: eigene Werte (Verlauf, Depot, Watchlist), dann Katalog
    val lokal = remember(eingabe, verlaufAlle, konto.positions, konto.watchlist) {
        val eigene = Wertsuche.eigene(
            eingabe.begriff, verlaufAlle,
            konto.positions.filter { !it.isDerivative }.map { it.symbol to it.name },
            konto.watchlist.map { it to (Symbols.find(it)?.name ?: quotes[it]?.name ?: it) },
        )
        val katalog = Wertsuche.katalog(eingabe, Symbols.alle()).map {
            Wertsuche.Vorschlag(it.symbol, it.name, Wertsuche.Herkunft.KATALOG, 0, typeLabel(it.type) + if (it.isin.isNotBlank()) " · ${it.isin}" else "")
        }
        Wertsuche.mischen(eigene, katalog, emptyList())
    }
    val lokalSymbole = remember(lokal) { lokal.map { it.symbol }.toSet() }
    val abrufe = remember { Wertsuche.Abruffolge() }

    LaunchedEffect(Unit) { state.market.track(Symbols.all.map { it.symbol }) }

    // Kuerzlich gesucht (0.68): Kurse der Verlaufswerte nachladen
    LaunchedEffect(verlauf) { if (verlauf.isNotEmpty()) state.market.track(verlauf.map { it.symbol }) }

    // Kurse der angezeigten Treffer nachladen
    LaunchedEffect(lokal) { if (lokal.isNotEmpty()) state.market.track(lokal.take(12).map { it.symbol }) }

    // Gleiche Aktie an verschiedenen Plaetzen (gleicher Name, passender Euro-Kurs) teilt sich das Logo
    LaunchedEffect(remote, quotes) {
        if (remote.isEmpty()) return@LaunchedEffect
        val treffer = lokal.map { it.symbol to it.name } + remote.map { it.symbol to it.name }
        de.troetenrepublik.app.market.Gleichwerte.gruppen(treffer) { quotes[it]?.price }
            .forEach { de.troetenrepublik.app.market.Gleichwerte.verbinden(it) }
    }

    // Netzsuche mit Entprellung. Jeder Lauf hat eine Nummer; eine verspaetete Antwort schreibt nichts mehr, wenn
    // inzwischen ein neuerer Lauf begonnen hat.
    LaunchedEffect(eingabe.begriff) {
        val nr = abrufe.neu()
        remote = emptyList(); searchError = false
        val art = eingabe.art
        // Anfang oder falsche Pruefziffer einer ISIN gehen nicht ins Netz (Katalog und Hinweis genuegen)
        val netz = art == Wertsuche.Art.ISIN || art == Wertsuche.Art.WKN || (art == Wertsuche.Art.TEXT && eingabe.begriff.length >= 2)
        searching = netz
        listState.scrollToItem(0)
        if (!netz) return@LaunchedEffect
        try {
            delay(if (art == Wertsuche.Art.TEXT) 350 else 120)
            val hits = when (art) {
                Wertsuche.Art.ISIN -> state.market.kennungSuche(eingabe.kennung, false, settings.land)
                Wertsuche.Art.WKN -> state.market.kennungSuche(eingabe.kennung, true, settings.land)
                else -> {
                    val hauptabfrage = state.market.search(Wertsuche.netzBegriff(eingabe.begriff))
                    val kuerzel = Wertsuche.kuerzelVariante(eingabe.begriff)?.let { k -> runCatching { state.market.search(k) }.getOrDefault(emptyList()) }.orEmpty()
                    kuerzel + hauptabfrage
                }
            }
            if (!abrufe.aktuell(nr)) return@LaunchedEffect
            remote = ((if (art == Wertsuche.Art.TEXT) state.firmenListeSuche(query) else emptyList()) + hits).distinctBy { it.symbol }.take(12)
            if (remote.isNotEmpty()) state.market.track(remote.map { it.symbol })
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { if (abrufe.aktuell(nr)) searchError = true }
        finally { if (abrufe.aktuell(nr)) searching = false }
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

    // Vorschau der Einstiege Indizes, Rohstoffe und Waehrungen: je die drei ersten Basiswerte des Katalogs
    val einstiege = remember(catalog) {
        listOf("INDEX" to DiscoverKind.INDICES, "ROHSTOFF" to DiscoverKind.COMMODITIES, "WAEHRUNG" to DiscoverKind.CURRENCIES).map { (klasse, kind) ->
            Triple(klasse, kind, catalog?.basiswerte.orEmpty().filter { it.k == klasse }.take(3))
        }
    }
    // Groesste Bewegungen des Tages (gleicher Datenweg wie die Seite „Maerkte“): die fuenf mit dem groessten Ausschlag
    val bewegungenAlle = rememberBewegungen(state)
    val bewegungen = remember(bewegungenAlle) { bewegungenAlle.sortedByDescending { abs(it.third.sitzungPct) }.take(5) }

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
                    item { AufklappKopf(S.svVerlauf, "", verlaufOffen) { verlaufOffen = !verlaufOffen } }
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
                    if (verlaufOffen) item {
                        // Zwei kleine Knoepfe in einer Zeile: Verlauf leeren oder ganz ausschalten
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            VerlaufKnopf(S.svLoeschen, Modifier.weight(1f)) { state.suchverlaufLeeren(); verlaufOffen = false }
                            VerlaufKnopf(S.svAus, Modifier.weight(1f)) { state.suchverlaufAusSetzen(true); verlaufOffen = false }
                        }
                    }
                }

                // Groesste Bewegungen, Boersengaenge, Stimmung und Heatmap auf einer eigenen Seite
                item { NavRow(S.maerkteTitel) { onDiscover(MAERKTE_ROUTE) } }

                // Groesste Bewegungen mit Prozent, die ganze Liste auf der Seite dahinter
                if (bewegungen.isNotEmpty()) {
                    item(key = "bewegungen-kopf") { SectionHeader(S.topMovers, chevron = true, onClick = { onDiscover("discover/movers") }) }
                    bewegungen.forEachIndexed { i, (symbol, name, q) ->
                        item(key = "bew-$symbol") {
                            Column {
                                if (i > 0) Trennlinie()
                                AssetRow(state = state, symbol = symbol, name = name, change = q.sitzungPct, changeText = Fmt.pct(abs(q.sitzungPct)), onClick = { oeffnenMerken(symbol) })
                            }
                        }
                    }
                    if (bewegungenAlle.size > bewegungen.size) item(key = "alle-bewegungen") { NavRow(S.bbAlle(bewegungenAlle.size)) { onDiscover("discover/movers") } }
                }

                // Beliebt, siehe popularStocks
                item(key = "beliebt") {
                    SectionHeader(S.popular)
                    BeliebtBlock(state, popular, snapshot, oeffnenMerken)
                }

                if (catalog != null) {
                    item { SectionHeader(S.stocks, chevron = true, onClick = { onDiscover("discover/filter/STOCKS") }) }
                    val shownStocks = topStocks.ifEmpty { stocks.take(3) }
                    items(shownStocks, key = { "stock-" + it.s }) { st ->
                        val q = snapshot[st.s]
                        AssetRow(state = state, symbol = st.s, name = st.n, change = q?.changePct, changeText = q?.let { Fmt.pct(abs(it.changePct)) }, onClick = { oeffnenMerken(st.s) })
                    }
                    if (stocks.size > shownStocks.size) item(key = "alle-aktien") { NavRow(S.bbAlle(stocks.size)) { onDiscover("discover/filter/STOCKS") } }
                    item { SectionHeader(S.etfs, chevron = true, onClick = { onDiscover("discover/filter/ETFS") }) }
                    items(catalog.etfs.take(3), key = { "etf-" + it.s }) { e ->
                        val q = snapshot[e.s]
                        AssetRow(state = state, symbol = e.s, name = e.n, subtitle = e.a.ifBlank { null }, change = q?.changePct, changeText = q?.let { Fmt.pct(abs(it.changePct)) }, onClick = { oeffnenMerken(e.s) })
                    }
                    if (catalog.etfs.size > 3) item(key = "alle-etfs") { NavRow(S.bbAlle(catalog.etfs.size)) { onDiscover("discover/filter/ETFS") } }
                    einstiege.forEach { (klasse, kind, werte) ->
                        item(key = "kopf-$klasse") { SectionHeader(S.assetClassName(klasse), chevron = true, onClick = { onDiscover("discover/filter/${kind.name}") }) }
                        items(werte, key = { "und-" + it.s }) { u ->
                            val q = snapshot[u.s]
                            AssetRow(
                                state = state,
                                symbol = u.s, name = if (I18n.lang == Lang.DE) u.nameDe else u.nameEn,
                                change = q?.changePct, changeText = q?.let { Fmt.pct(abs(it.changePct)) }, onClick = { oeffnenMerken(u.s) },
                            )
                        }
                        val gesamt = catalog.basiswerte.count { it.k == klasse }
                        if (gesamt > werte.size) item(key = "alle-$klasse") { NavRow(S.bbAlle(gesamt)) { onDiscover("discover/filter/${kind.name}") } }
                    }
                } else {
                    groupSection(S.stocks, Symbols.dax + Symbols.usa, quotes, state, oeffnenMerken)
                    groupSection(S.etfs, Symbols.etfs, quotes, state, oeffnenMerken)
                }
                // Krypto: Vorschau, die Listen zum Entdecken stehen auf der eigenen Seite
                item(key = "kopf-krypto") { SectionHeader(S.crypto, chevron = true, onClick = { onDiscover(KRYPTO_ROUTE) }) }
                items(Symbols.crypto.take(3), key = { "krypto-" + it.symbol }) { inst ->
                    val q = quotes[inst.symbol]
                    AssetRow(
                        state = state, symbol = inst.symbol, name = inst.name, subtitle = inst.symbol.substringBefore('-'),
                        change = q?.changePct, changeText = q?.let { Fmt.pct(abs(it.changePct)) }, onClick = { oeffnenMerken(inst.symbol) },
                    )
                }
                if (Symbols.crypto.size > 3) item(key = "alle-krypto") { NavRow(S.bbAlle(Symbols.crypto.size)) { onDiscover(KRYPTO_ROUTE) } }

            } else {

                val fern = remote.filter { it.symbol !in lokalSymbole }
                if (lokal.isEmpty() && fern.isEmpty()) {
                    item {
                        EmptyHint(
                            when {
                                searching -> S.searching
                                eingabe.art == Wertsuche.Art.ISIN_PRUEFZIFFER -> S.isinPruefziffer
                                eingabe.art == Wertsuche.Art.ISIN_TEIL -> S.isinUnvollstaendig
                                searchError -> S.searchUnavailable
                                else -> S.noResults
                            },
                        )
                    }
                }
                if (lokal.isNotEmpty()) {
                    item { SectionHeader(S.results) }
                    items(lokal, key = { it.symbol }) { v ->
                        val q = quotes[v.symbol]
                        AssetRow(
                            state = state,
                            symbol = v.symbol,
                            name = v.name.ifBlank { Symbols.name(v.symbol) },
                            subtitle = v.unterzeile.ifBlank { v.symbol },
                            change = q?.changePct,
                            changeText = q?.let { Fmt.pct(abs(it.changePct)) },
                            onClick = { state.suchverlaufMerken(v.symbol, v.name); onOpen(v.symbol) },
                        )
                    }
                }
                if (fern.isNotEmpty()) {
                    item { SectionHeader(if (lokal.isEmpty()) S.results else S.otherVenues) }
                    items(fern, key = { it.symbol }) { hit ->
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
                        modifier = Modifier.fillMaxWidth(),
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

// Kleiner Knopf mit Rand fuer die Verlauf-Aktionen
@Composable
private fun VerlaufKnopf(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error, maxLines = 1, softWrap = false)
    }
}
