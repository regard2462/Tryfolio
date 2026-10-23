package de.troetenrepublik.app.ui

import de.troetenrepublik.app.plattform.mausWischen
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import de.troetenrepublik.app.plattform.horizontalScrollMaus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.HomeRepairService
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.OilBarrel
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.market.Catalog
import de.troetenrepublik.app.market.Region
import de.troetenrepublik.app.market.Symbols
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import kotlin.math.abs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

// Navigation

// Entdecken-Seiten der Suche: Top Movers, IPOs und die Filterseiten fuer Aktien, ETFs und Derivate.
@Composable
fun DiscoverSeite(route: String, state: AppState, onBack: () -> Unit, onOpen: (String) -> Unit) {
    when {
        route == "discover/movers" -> MoversPage(state, onBack = onBack, onOpen = onOpen)
        route == "discover/ipos" -> IposPage(state, onBack = onBack, onOpen = onOpen)
        route.startsWith("discover/filter/") -> {
            val kind = runCatching { DiscoverKind.valueOf(route.removePrefix("discover/filter/")) }.getOrDefault(DiscoverKind.STOCKS)
            FilterPage(state, kind, onBack = onBack, onOpen = onOpen)
        }
    }
}

enum class DiscoverKind { STOCKS, ETFS, DERIVATIVES }

// Daten

// Katalog im Hintergrund laden; bis dahin null.
@Composable
fun rememberCatalog(): Catalog.Data? {
    var data by remember { mutableStateOf(Catalog.loaded()) }
    LaunchedEffect(Unit) {
        if (data == null) data = withContext(Dispatchers.IO) { Catalog.get() }
    }
    return data
}

// Kurse fuer eine lange Liste: einmal gesammelt abgefragt; laufend beobachtete Werte sind frischer.
@Composable
fun rememberSnapshot(state: AppState, symbols: List<String>): Map<String, Quote> {
    val live by state.quotes.collectAsStateWithLifecycle()
    var snap by remember { mutableStateOf(emptyMap<String, Quote>()) }
    LaunchedEffect(symbols) {
        snap = if (symbols.isEmpty()) emptyMap() else state.market.snapshot(symbols)
    }
    return remember(snap, live, symbols) {
        if (live.isEmpty()) snap else snap + symbols.mapNotNull { s -> live[s]?.let { s to it } }
    }
}

// Handelsumsatz des Tages in Euro (Kurs mal Stueckzahl).
private fun Quote.turnover(): Double = if (price.isFinite() && volume.isFinite()) price * volume else 0.0

// Beliebtheit: durchschnittlicher Handelsumsatz der letzten zehn Tage in Euro. Mit dem Tagesumsatz staenden
// vormittags nur asiatische Werte vorn, deren Handelstag schon vorbei ist.
fun Quote.popularity(): Double {
    val v = if (avgVolume10 > 0) avgVolume10 else volume
    return if (price.isFinite() && v.isFinite()) price * v else 0.0
}

// Kandidaten fuer „Beliebt“ und die Vorschau „Aktien“.
private fun Catalog.Stock.popularCandidate(): Boolean = c == "US" || c == "DE"

// Groesste Bewegungen des Tages unter den Katalogaktien. Nur Werte mit nennenswertem Umsatz, damit
// kein Kurs von gestern oder ein Einzelgeschaeft in einem Nebenwert die Liste bestimmt.
fun movers(quotes: Map<String, Quote>, stocks: List<Catalog.Stock>): List<Pair<Catalog.Stock, Quote>> =
    stocks.mapNotNull { s -> quotes[s.s]?.takeIf { it.prevClose > 0 && it.price > 0 && it.turnover() >= 2_000_000.0 }?.let { s to it } }

// „Beliebt“: die US- und deutschen Werte mit dem hoechsten durchschnittlichen Handelsumsatz der
// letzten zehn Tage, dazu mindestens zwei deutsche. Ohne Kurse bleibt die feste Auswahl.
fun popularStocks(quotes: Map<String, Quote>, stocks: List<Catalog.Stock>): List<Pair<String, String>> {
    val ranked = stocks.filter { it.popularCandidate() }
        .mapNotNull { s -> quotes[s.s]?.popularity()?.takeIf { it > 0 }?.let { s to it } }.sortedByDescending { it.second }
    if (ranked.size < 20) return Symbols.popular.map { it.symbol to it.name }
    val out = mutableListOf<Catalog.Stock>()
    val seen = mutableSetOf<String>()
    for ((s, _) in ranked) {
        // Gattungen desselben Unternehmens (Alphabet A und C) nur einmal
        if (seen.add(s.n.substringBefore(" (").lowercase())) out += s
        if (out.size >= 6) break
    }
    ranked.map { it.first }.filter { it.c == "DE" && it !in out && seen.add(it.n.substringBefore(" (").lowercase()) }.take(2).forEach { out += it }
    // Stehen die deutschen Werte schon oben, fuellen die naechsten nach Umsatz auf acht auf
    for ((s, _) in ranked) {
        if (out.size >= 8) break
        if (s !in out && seen.add(s.n.substringBefore(" (").lowercase())) out += s
    }
    return out.map { it.s to it.n }
}

// Ein Eintrag der Filterseite, gleich ob Aktie, ETF oder Basiswert.
data class DiscoverItem(
    val symbol: String,
    val name: String,
    val subtitle: String = "",
    val industry: String = "",
    val country: String = "",
    val region: String = "",
    val indices: List<String> = emptyList(),
    val assetClass: String = "",
    val isin: String = "",
)

fun discoverItems(kind: DiscoverKind, d: Catalog.Data, lang: Lang): List<DiscoverItem> = when (kind) {
    DiscoverKind.STOCKS -> d.aktien.map { it.toItem() }
    DiscoverKind.ETFS -> d.etfs.map {
        DiscoverItem(it.s, it.n, subtitle = it.a, industry = it.i, region = it.r, indices = listOfNotNull(it.x), assetClass = "ETF", isin = it.isin)
    }
    DiscoverKind.DERIVATIVES -> d.basiswerte.map {
        DiscoverItem(it.s, if (lang == Lang.DE) it.nameDe else it.nameEn, country = it.c, region = it.r.ifBlank { "GLOBAL" }, assetClass = it.k)
    } + d.aktien.map { it.toItem() }
}

private fun Catalog.Stock.toItem() = DiscoverItem(s, n, industry = i, country = c, region = Region.ofCountry(c), indices = x, assetClass = "AKTIE")

// Gewaehlte Filter; innerhalb einer Art gilt „oder“, zwischen den Arten „und“.
data class DiscoverFilters(
    val industries: Set<String> = emptySet(),
    val countries: Set<String> = emptySet(),
    val regions: Set<String> = emptySet(),
    val indices: Set<String> = emptySet(),
    val classes: Set<String> = emptySet(),
) {
    val active: Boolean get() = industries.isNotEmpty() || countries.isNotEmpty() || regions.isNotEmpty() || indices.isNotEmpty() || classes.isNotEmpty()

    fun matches(i: DiscoverItem): Boolean =
        (industries.isEmpty() || i.industry in industries) &&
            (countries.isEmpty() || i.country in countries) &&
            (regions.isEmpty() || i.region in regions) &&
            (indices.isEmpty() || i.indices.any { it in indices }) &&
            (classes.isEmpty() || i.assetClass in classes)
}

enum class FilterDim { CLASS, INDUSTRY, COUNTRY, REGION, INDEX }

fun dimsFor(kind: DiscoverKind): List<FilterDim> = when (kind) {
    // Ein Index zeigt alle seine Werte aus dem Katalog
    DiscoverKind.STOCKS -> listOf(FilterDim.INDEX, FilterDim.INDUSTRY, FilterDim.COUNTRY)
    DiscoverKind.ETFS -> listOf(FilterDim.INDUSTRY, FilterDim.REGION, FilterDim.INDEX)
    DiscoverKind.DERIVATIVES -> listOf(FilterDim.CLASS, FilterDim.INDUSTRY, FilterDim.COUNTRY, FilterDim.REGION)
}

private fun DiscoverFilters.get(dim: FilterDim): Set<String> = when (dim) {
    FilterDim.CLASS -> classes; FilterDim.INDUSTRY -> industries; FilterDim.COUNTRY -> countries
    FilterDim.REGION -> regions; FilterDim.INDEX -> indices
}

private fun DiscoverFilters.with(dim: FilterDim, v: Set<String>): DiscoverFilters = when (dim) {
    FilterDim.CLASS -> copy(classes = v); FilterDim.INDUSTRY -> copy(industries = v); FilterDim.COUNTRY -> copy(countries = v)
    FilterDim.REGION -> copy(regions = v); FilterDim.INDEX -> copy(indices = v)
}

private fun DiscoverItem.values(dim: FilterDim): List<String> = when (dim) {
    FilterDim.CLASS -> listOf(assetClass); FilterDim.INDUSTRY -> listOf(industry); FilterDim.COUNTRY -> listOf(country)
    FilterDim.REGION -> listOf(region); FilterDim.INDEX -> indices
}.filter { it.isNotBlank() }

private fun dimTitle(dim: FilterDim): String = when (dim) {
    FilterDim.CLASS -> S.assetClass; FilterDim.INDUSTRY -> S.industries; FilterDim.COUNTRY -> S.countries
    FilterDim.REGION -> S.regions; FilterDim.INDEX -> S.indices
}

private fun optionLabel(dim: FilterDim, key: String): String = when (dim) {
    FilterDim.CLASS -> S.assetClassName(key); FilterDim.INDUSTRY -> S.industryName(key); FilterDim.COUNTRY -> S.countryLabel(key)
    FilterDim.REGION -> S.regionName(key); FilterDim.INDEX -> Catalog.indexName(key)
}

// Top Movers

@Composable
fun MoversPage(state: AppState, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val catalog = rememberCatalog()
    val symbols = remember(catalog) { catalog?.aktien?.map { it.s }.orEmpty() }
    val quotes = rememberSnapshot(state, symbols)
    val list = remember(quotes, catalog) { movers(quotes, catalog?.aktien.orEmpty()) }
    val best = remember(list) { list.filter { it.second.changePct > 0 }.sortedByDescending { it.second.changePct }.take(5) }
    val worst = remember(list) { list.filter { it.second.changePct < 0 }.sortedBy { it.second.changePct }.take(5) }
    val liste = rememberLazyListState()
    val dismiss = rememberSwipeDownDismiss(onBack)

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { !liste.canScrollBackward }, state = liste) {
        item { DiscoverTitle(S.topMoversTitle, null, onBack) }
        item { SectionHeader(S.bestPerformers) }
        if (best.isEmpty()) item { EmptyHint(S.loading) }
        items(best, key = { "b-" + it.first.s }) { (s, q) ->
            AssetRow(symbol = s.s, name = s.n, change = q.changePct, changeText = Fmt.pct(abs(q.changePct)), onClick = { onOpen(s.s) })
        }
        item { SectionHeader(S.worstPerformers) }
        if (worst.isEmpty()) item { EmptyHint(S.loading) }
        items(worst, key = { "w-" + it.first.s }) { (s, q) ->
            AssetRow(symbol = s.s, name = s.n, change = q.changePct, changeText = Fmt.pct(abs(q.changePct)), onClick = { onOpen(s.s) })
        }
        item { Spacer(Modifier.height(60.dp)) }
    }
}

// IPOs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IposPage(state: AppState, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val catalog = rememberCatalog()
    val ipos = catalog?.ipos ?: Catalog.Ipos()
    val symbols = remember(ipos) { ipos.abgeschlossen.map { it.s } }
    val quotes = rememberSnapshot(state, symbols)
    val liste = rememberLazyListState()
    val dismiss = rememberSwipeDownDismiss(onBack)
    var detail by remember { mutableStateOf<Catalog.IpoUpcoming?>(null) }
    val de = I18n.lang == Lang.DE

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { !liste.canScrollBackward }, state = liste) {
        item { DiscoverTitle(S.ipos, S.iposIntro, onBack) }
        if (ipos.bevorstehend.isNotEmpty()) {
            item {
                SectionHeader(S.upcoming)
                val pager = rememberPagerState { ipos.bevorstehend.size }
                HorizontalPager(
                    state = pager,
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    pageSpacing = 12.dp,
                    modifier = Modifier.fillMaxWidth().mausWischen(pager),
                ) { page ->
                    val ipo = ipos.bevorstehend[page]
                    IpoCard(ipo, page, if (de) ipo.periodDe else ipo.periodEn) { detail = ipo }
                }
            }
        }
        if (ipos.abgeschlossen.isNotEmpty()) {
            item { SectionHeader(S.completed) }
            items(ipos.abgeschlossen, key = { it.s }) { ipo ->
                val q = quotes[ipo.s]
                val since = ipo.preis?.takeIf { it > 0 && q != null && q.nativePrice > 0 && q.nativeCurrency.equals(ipo.waehrung, true) }
                    ?.let { (q!!.nativePrice / it - 1.0) * 100.0 }
                // Zwei kurze Zeilen statt einer abgeschnittenen: Erstnotiz, darunter die Entwicklung seit Ausgabe
                val sub = listOfNotNull(
                    isoDate(ipo.datum)?.let { S.ipoListed(it) },
                    since?.let { S.ipoSinceIssue(Fmt.signedPct(it, 1)) },
                ).joinToString("\n")
                AssetRow(
                    symbol = ipo.s, name = ipo.name, subtitle = sub.ifBlank { null }, subtitleLines = 2,
                    change = q?.changePct, changeText = q?.let { Fmt.pct(abs(it.changePct)) },
                    onClick = { onOpen(ipo.s) },
                )
            }
        }
        item {
            Text(
                S.ipoAsOf(isoDate(ipos.stand) ?: ipos.stand),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            )
            Spacer(Modifier.height(40.dp))
        }
    }

    detail?.let { ipo ->
        ModalBottomSheet(
            onDismissRequest = { detail = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
        ) {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                Text(S.ipoCardTitle(ipo.name), style = MaterialTheme.typography.headlineSmall)
                Text(if (de) ipo.periodDe else ipo.periodEn, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                Text(if (de) ipo.textDe else ipo.textEn, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp))
                if (ipo.quelle.isNotBlank()) {
                    Text("${S.source}: ${ipo.quelle}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun IpoCard(ipo: Catalog.IpoUpcoming, index: Int, period: String, onClick: () -> Unit) {
    val verlaeufe = listOf(
        listOf(Color(0xFF2B2B2E), Color(0xFF3A2F2A)),
        listOf(Color(0xFFB65E86), Color(0xFF6E62B6)),
        listOf(Color(0xFF1F4F8F), Color(0xFF2DA8A0)),
        listOf(Color(0xFF3B3B7A), Color(0xFF9B4DCA)),
        listOf(Color(0xFF5A3E1E), Color(0xFFB7792B)),
    )
    val farben = verlaeufe[index % verlaeufe.size]
    Column(
        Modifier
            .fillMaxWidth()
            .height(250.dp)
            .clip(RoundedCornerShape(Radius.card))
            .background(Brush.linearGradient(farben))
            .clickable(onClick = onClick)
            .padding(28.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Text(ipo.name.take(1).uppercase(), color = Color.White, style = MaterialTheme.typography.titleLarge) }
        Spacer(Modifier.weight(1f))
        Text(S.ipoCardTitle(ipo.name), color = Color.White, style = MaterialTheme.typography.headlineSmall)
        Text(period, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 6.dp))
    }
}

// "2026-06-12" in der Schreibweise der Sprache.
private fun isoDate(iso: String): String? = runCatching {
    java.time.LocalDate.parse(iso).atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
}.getOrNull()?.let { Fmt.date(it) }

// Filterseite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterPage(state: AppState, kind: DiscoverKind, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val catalog = rememberCatalog()
    val lang = I18n.lang
    val all = remember(catalog, kind, lang) { catalog?.let { discoverItems(kind, it, lang) }.orEmpty() }
    val quotes = rememberSnapshot(state, remember(all) { all.map { it.symbol } })
    var filters by remember { mutableStateOf(DiscoverFilters()) }
    var query by rememberSaveable { mutableStateOf("") }
    var sheet by remember { mutableStateOf<FilterDim?>(null) }
    val dismiss = rememberSwipeDownDismiss(onBack)
    val listState = rememberLazyListState()

    // Reihenfolge: meistgehandelt zuerst; ohne Kurse die Reihenfolge des Katalogs (bekannte Werte vorn)
    val ordered = remember(all, quotes) {
        if (quotes.size < 10) all
        else all.withIndex().sortedWith(compareByDescending<IndexedValue<DiscoverItem>> {
            if (kind == DiscoverKind.DERIVATIVES && it.value.assetClass != "AKTIE") Double.MAX_VALUE else quotes[it.value.symbol]?.popularity() ?: 0.0
        }.thenBy { it.index }).map { it.value }
    }
    val q = query.trim().lowercase()
    val shown = remember(ordered, filters, q) {
        ordered.filter { filters.matches(it) && (q.isEmpty() || it.name.lowercase().contains(q) || it.symbol.lowercase().contains(q) || it.isin.lowercase().startsWith(q) || it.subtitle.lowercase().contains(q)) }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        LazyColumn(Modifier.fillMaxSize().sheetDismiss(dismiss) { !listState.canScrollBackward }, state = listState) {
            item { DiscoverTitle(S.filterTitle, null, onBack) }
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (filters.active) {
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(Radius.chip)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { filters = DiscoverFilters() },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Close, S.resetFilters, modifier = Modifier.size(20.dp)) }
                    }
                    FilterChipPill(
                        when (kind) { DiscoverKind.STOCKS -> S.stocks; DiscoverKind.ETFS -> S.etfs; DiscoverKind.DERIVATIVES -> S.derivatives },
                        selected = true, dropdown = false,
                    ) {}
                    dimsFor(kind).forEach { dim ->
                        val n = filters.get(dim).size
                        FilterChipPill(dimTitle(dim) + if (n > 0) " · $n" else "", selected = n > 0, dropdown = true) { sheet = dim }
                    }
                }
            }
            item {
                Text(
                    S.resultCount(shown.size), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 6.dp),
                )
            }
            if (catalog == null) item { EmptyHint(S.loading) }
            else if (shown.isEmpty()) item { EmptyHint(S.noFilterResults) }
            items(shown, key = { it.symbol }) { it ->
                val quote = quotes[it.symbol]
                AssetRow(
                    symbol = it.symbol, name = it.name, subtitle = it.subtitle.ifBlank { null },
                    change = quote?.changePct, changeText = quote?.let { qq -> Fmt.pct(abs(qq.changePct)) },
                    onClick = { onOpen(it.symbol) },
                )
            }
            item { Spacer(Modifier.height(120.dp)) }
        }

        // Suchfeld unten, in Daumenreichweite: sucht innerhalb der gewaehlten Filter
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp)
                .height(58.dp).clip(RoundedCornerShape(Radius.chip)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(S.filterSearchPlaceholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = LocalTextStyle.current.merge(MaterialTheme.typography.bodyLarge).copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { query = "" }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, S.clear, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    sheet?.let { dim ->
        FilterSheet(
            dim = dim,
            items = all,
            filters = filters,
            onDismiss = { sheet = null },
            onApply = { chosen -> filters = filters.with(dim, chosen); sheet = null },
        )
    }
}

// Filterpille: ausgewaehlt hell mit dunkler Schrift, sonst dunkel; mit Pfeil, wenn sie ein Auswahlblatt oeffnet.
@Composable
private fun FilterChipPill(label: String, selected: Boolean, dropdown: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.height(48.dp).clip(RoundedCornerShape(Radius.chip)).background(bg).clickable(onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = fg, maxLines = 1)
        if (dropdown) {
            Spacer(Modifier.width(10.dp))
            ChevronDown(width = 12.dp, color = fg)
        }
    }
}

// Auswahlblatt einer Filterart: Liste mit Kaestchen, darueber die Trefferzahl, unten „Anwenden“.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    dim: FilterDim,
    items: List<DiscoverItem>,
    filters: DiscoverFilters,
    onDismiss: () -> Unit,
    onApply: (Set<String>) -> Unit,
) {
    var chosen by remember(dim) { mutableStateOf(filters.get(dim)) }
    // Angeboten wird, was es unter den uebrigen Filtern gibt, alphabetisch nach der angezeigten Bezeichnung
    val others = filters.with(dim, emptySet())
    val base = remember(items, others) { items.filter { others.matches(it) } }
    val collator = remember(I18n.lang) { Collator.getInstance(I18n.locale) }
    val options = remember(base, dim, I18n.lang) {
        base.flatMap { it.values(dim) }.distinct().sortedWith { a, b -> collator.compare(optionLabel(dim, a), optionLabel(dim, b)) }
    }
    val count = remember(base, chosen) { base.count { chosen.isEmpty() || it.values(dim).any { v -> v in chosen } } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Box(Modifier.fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxWidth()) {
                item {
                    Text(dimTitle(dim), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = 24.dp, top = 4.dp))
                    Text(
                        S.resultCount(count), style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 24.dp, top = 6.dp, bottom = 12.dp),
                    )
                }
                items(options, key = { it }) { key ->
                    val on = key in chosen
                    Row(
                        Modifier.fillMaxWidth().clickable { chosen = if (on) chosen - key else chosen + key }.padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OptionIcon(dim, key)
                        Spacer(Modifier.width(18.dp))
                        Text(optionLabel(dim, key), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Box(
                            Modifier.size(24.dp).clip(RoundedCornerShape(6.dp))
                                .background(if (on) MaterialTheme.colorScheme.onSurface else Color.Transparent)
                                .border(1.6.dp, if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center,
                        ) { if (on) CheckMark(size = 14.dp, color = MaterialTheme.colorScheme.surface) }
                    }
                }
                item { Spacer(Modifier.height(110.dp)) }
            }
            Row(
                Modifier.align(Alignment.BottomEnd).padding(24.dp).height(58.dp).clip(RoundedCornerShape(Radius.chip))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable { onApply(chosen) }.padding(horizontal = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(S.apply, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(28.dp))
                CheckMark(size = 16.dp)
            }
        }
    }
}

// Zeichen vor einer Filteroption: Symbol der Branche oder Anlageklasse, Flagge des Landes, der Region oder des Index.
@Composable
private fun OptionIcon(dim: FilterDim, key: String) {
    when (dim) {
        FilterDim.INDUSTRY, FilterDim.CLASS -> Box(
            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (dim == FilterDim.CLASS) classIcon(key) else industryIcon(key), null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(22.dp))
        }
        else -> {
            val flag = when (dim) {
                FilterDim.COUNTRY -> flagOf(key)
                FilterDim.INDEX -> Catalog.indexCountry(key).let { if (it.isEmpty()) "🌍" else flagOf(it) }
                else -> when (key) {
                    "DEUTSCHLAND" -> flagOf("DE"); "NORDAMERIKA" -> flagOf("US"); "EUROPA" -> flagOf("EU")
                    "ASIEN_PAZIFIK" -> "🌏"; "SCHWELLENLAENDER" -> "🌎"; else -> "🌍"
                }
            }
            // Das Flaggenzeichen ist groesser als der Kreis, der eine runde Flagge aus der Mitte schneidet
            Box(Modifier.size(40.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                Text(flag, fontSize = 62.sp, lineHeight = 62.sp, maxLines = 1, softWrap = false, modifier = Modifier.wrapContentSize(unbounded = true))
            }
        }
    }
}

// Flaggenzeichen aus dem Laendercode (zwei Regionalbuchstaben).
fun flagOf(iso: String): String {
    if (iso.length != 2) return "🏳"
    val a = Character.toChars(0x1F1E6 + (iso[0].uppercaseChar() - 'A'))
    val b = Character.toChars(0x1F1E6 + (iso[1].uppercaseChar() - 'A'))
    return String(a) + String(b)
}

private fun industryIcon(key: String): ImageVector = when (key) {
    "ALKOHOL_TABAK" -> Icons.Filled.LocalBar; "BANKEN" -> Icons.Filled.AccountBalance; "BAU" -> Icons.Filled.Construction
    "BERGBAU" -> Icons.Filled.Terrain; "HOLDINGS" -> Icons.Filled.Workspaces; "BIOTECH" -> Icons.Filled.Biotech
    "CHEMIE" -> Icons.Filled.Science; "COMPUTER" -> Icons.Filled.Computer; "DIENSTLEISTUNGEN" -> Icons.Filled.HomeRepairService
    "ELEKTRO" -> Icons.Filled.ElectricalServices; "HALBLEITER" -> Icons.Filled.Memory; "VERSORGER" -> Icons.Filled.Bolt
    "FAHRZEUGE" -> Icons.Filled.DirectionsCar; "LUFTFAHRT" -> Icons.Filled.Flight; "FORST" -> Icons.Filled.Park
    "GASTGEWERBE" -> Icons.Filled.Hotel; "GESUNDHEIT" -> Icons.Filled.LocalHospital; "HANDEL" -> Icons.Filled.Timeline
    "IMMOBILIEN" -> Icons.Filled.Apartment; "SOFTWARE" -> Icons.Filled.Code; "EINZELHANDEL" -> Icons.Filled.ShoppingBag
    "KONSUM" -> Icons.Filled.ShoppingCart; "MASCHINEN" -> Icons.Filled.PrecisionManufacturing; "NAHRUNG" -> Icons.Filled.Restaurant
    "PHARMA" -> Icons.Filled.Medication; "MEDIEN" -> Icons.Filled.Newspaper; "ROHSTOFFE" -> Icons.Filled.Diamond
    "TELEKOM" -> Icons.Filled.Wifi; "TEXTIL" -> Icons.Filled.Checkroom; "UMWELT" -> Icons.Filled.Recycling
    "VERKEHR" -> Icons.Filled.LocalShipping; "VERSICHERUNG" -> Icons.Filled.Umbrella; "OEL_GAS" -> Icons.Filled.OilBarrel
    "SEKTORUEBERGREIFEND" -> Icons.Filled.Category; "LARGE_CAPS" -> Icons.Filled.Domain; "MID_CAPS" -> Icons.Filled.Business
    "SMALL_CAPS" -> Icons.Filled.Store; "UNTERNEHMENSANLEIHEN" -> Icons.Filled.Description; "STAATSANLEIHEN" -> Icons.Filled.AccountBalance
    "GELDMARKT" -> Icons.Filled.Euro
    else -> Icons.Filled.MoreHoriz
}

private fun classIcon(key: String): ImageVector = when (key) {
    "INDEX" -> Icons.Filled.ShowChart; "AKTIE" -> Icons.Filled.Timeline; "ROHSTOFF" -> Icons.Filled.Diamond
    "WAEHRUNG" -> Icons.Filled.Euro; "KRYPTO" -> Icons.Filled.CurrencyBitcoin
    else -> Icons.Filled.MoreHoriz
}

// Gemeinsam

// Kopf einer Entdecken-Seite: Pfeil, grosser Titel, optional Einleitung.
@Composable
private fun DiscoverTitle(title: String, intro: String?, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, S.back) }
    }
    Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = 20.dp, top = 4.dp))
    if (intro != null) {
        Text(
            intro, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 6.dp),
        )
    }
}
