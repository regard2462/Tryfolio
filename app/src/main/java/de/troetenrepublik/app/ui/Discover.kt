package de.troetenrepublik.app.ui

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.style.TextAlign
import de.troetenrepublik.app.data.Candle
import kotlinx.coroutines.launch
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
import androidx.compose.material.icons.filled.OilBarrel
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Public
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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableIntStateOf
import de.troetenrepublik.app.engine.MoverGrund
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
import de.troetenrepublik.app.i18n.r2
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.market.Catalog
import de.troetenrepublik.app.market.Region
import de.troetenrepublik.app.market.Rss
import de.troetenrepublik.app.market.SecInsider
import de.troetenrepublik.app.ipoSperrfristen
import de.troetenrepublik.app.market.SecIpo
import de.troetenrepublik.app.market.IpoKalender
import de.troetenrepublik.app.market.NasdaqKalender
import de.troetenrepublik.app.market.ohneAbbruch
import de.troetenrepublik.app.engine.GleicheFirma
import de.troetenrepublik.app.engine.IpoBewertung
import de.troetenrepublik.app.engine.IpoBlatt
import de.troetenrepublik.app.engine.IpoForschung
import de.troetenrepublik.app.engine.IpoProspekt
import de.troetenrepublik.app.engine.IpoUebersicht
import de.troetenrepublik.app.engine.LaenderZusatz
import de.troetenrepublik.app.engine.TrendsErklaerung
import androidx.compose.foundation.verticalScroll
import de.troetenrepublik.app.market.Symbols
import de.troetenrepublik.app.data.WidgetVorgabe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import de.troetenrepublik.app.engine.Suchgruppen
import java.text.Collator
import kotlin.math.abs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

// Navigation

// Entdecken-Seiten der Suche: Top Movers, IPOs und die Filterseiten fuer Aktien, ETFs und Derivate.
@Composable
fun DiscoverSeite(route: String, state: AppState, onBack: () -> Unit, onOpen: (String) -> Unit, oeffnen: (String) -> Unit = {}) {
    when {
        route == "discover/movers" -> MoversPage(state, onBack = onBack, onOpen = onOpen)
        route == "discover/ipos" -> IposPage(state, onBack = onBack, onOpen = onOpen)
        route == "discover/earnings" -> QuartalszahlenPage(state, onBack = onBack, onOpen = onOpen)
        route == "discover/dividends" -> DividendenPage(state, onBack = onBack, onOpen = onOpen)
        route == "discover/economy" -> WirtschaftPage(state, onBack = onBack)
        route == KRYPTO_ROUTE -> KryptoEntdeckenPage(state, onBack = onBack, onOpen = onOpen, oeffnen = oeffnen)
        route.startsWith("discover/filter/") -> {
            val kind = runCatching { DiscoverKind.valueOf(route.removePrefix("discover/filter/")) }.getOrDefault(DiscoverKind.STOCKS)
            FilterPage(state, kind, onBack = onBack, onOpen = onOpen)
        }
    }
}

// DERIVATIVES: alle Basiswerte fuer Hebelprodukte; INDICES, COMMODITIES und CURRENCIES: eigene Einstiege der Suche.
enum class DiscoverKind { STOCKS, ETFS, DERIVATIVES, INDICES, COMMODITIES, CURRENCIES }

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
    // Rohstoffgruppe oder Gruppe des Waehrungspaars (Suchgruppen)
    val group: String = "",
)

fun discoverItems(kind: DiscoverKind, d: Catalog.Data, lang: Lang): List<DiscoverItem> = when (kind) {
    DiscoverKind.STOCKS -> d.aktien.map { it.toItem() }
    DiscoverKind.ETFS -> d.etfs.map {
        DiscoverItem(it.s, it.n, subtitle = it.a, industry = it.i, region = it.r, indices = listOfNotNull(it.x), assetClass = "ETF", isin = it.isin)
    }
    DiscoverKind.DERIVATIVES -> d.basiswerte.map {
        DiscoverItem(it.s, if (lang == Lang.DE) it.nameDe else it.nameEn, country = it.c, region = it.r.ifBlank { "GLOBAL" }, assetClass = it.k)
    } + d.aktien.map { it.toItem() }
    DiscoverKind.INDICES -> (d.basiswerte.filter { it.k == "INDEX" }.map { u ->
        val region = Suchgruppen.indexRegion(u.c, u.r)
        DiscoverItem(u.s, if (lang == Lang.DE) u.nameDe else u.nameEn, subtitle = indexUnterzeile(u.c, region), country = u.c, region = region, assetClass = "INDEX")
    } + Suchgruppen.INDIZES.map { w ->
        val region = Suchgruppen.indexRegion(w.land)
        DiscoverItem(w.symbol, if (lang == Lang.DE) w.de else w.en, subtitle = indexUnterzeile(w.land, region), country = w.land, region = region, assetClass = "INDEX")
    }).distinctBy { it.symbol }
    DiscoverKind.COMMODITIES -> (d.basiswerte.filter { it.k == "ROHSTOFF" }.map { it.s to (if (lang == Lang.DE) it.nameDe else it.nameEn) } +
        Suchgruppen.ROHSTOFFE.map { it.symbol to (if (lang == Lang.DE) it.de else it.en) }).distinctBy { it.first }.map { (s, n) ->
        val g = Suchgruppen.rohstoffGruppe(s)
        DiscoverItem(s, n, subtitle = if (g.isEmpty()) "" else S.suGruppe(g), assetClass = "ROHSTOFF", group = g)
    }
    // Paare zum Euro zuerst, in der Reihenfolge der Gruppen
    DiscoverKind.CURRENCIES -> (d.basiswerte.filter { it.k == "WAEHRUNG" }.map { it.s to (if (lang == Lang.DE) it.nameDe else it.nameEn) } +
        Suchgruppen.WAEHRUNGEN.map { it.symbol to it.de }).distinctBy { it.first }.map { (s, n) ->
        val g = Suchgruppen.waehrungsGruppe(s)
        DiscoverItem(s, n, subtitle = if (g.isEmpty()) "" else S.suGruppe(g), assetClass = "WAEHRUNG", group = g)
    }.sortedBy { Suchgruppen.WAEHRUNGS_GRUPPEN.indexOf(it.group).let { i -> if (i < 0) Int.MAX_VALUE else i } }
}

// Unterzeile eines Index: sein Land, bei Indizes ueber mehrere Laender die Region.
private fun indexUnterzeile(land: String, region: String): String =
    if (land.length == 2 && land != "EU") S.countryLabel(land) else S.regionName(region)

private fun Catalog.Stock.toItem() = DiscoverItem(s, n, industry = i, country = c, region = Region.ofCountry(c), indices = x, assetClass = "AKTIE")

// Gewaehlte Filter; innerhalb einer Art gilt „oder“, zwischen den Arten „und“.
data class DiscoverFilters(
    val industries: Set<String> = emptySet(),
    val countries: Set<String> = emptySet(),
    val regions: Set<String> = emptySet(),
    val indices: Set<String> = emptySet(),
    val classes: Set<String> = emptySet(),
    val groups: Set<String> = emptySet(),
) {
    val active: Boolean get() = industries.isNotEmpty() || countries.isNotEmpty() || regions.isNotEmpty() || indices.isNotEmpty() || classes.isNotEmpty() || groups.isNotEmpty()

    fun matches(i: DiscoverItem): Boolean =
        (industries.isEmpty() || i.industry in industries) &&
            (countries.isEmpty() || i.country in countries) &&
            (regions.isEmpty() || i.region in regions) &&
            (indices.isEmpty() || i.indices.any { it in indices }) &&
            (classes.isEmpty() || i.assetClass in classes) &&
            (groups.isEmpty() || i.group in groups)
}

enum class FilterDim { CLASS, INDUSTRY, COUNTRY, REGION, INDEX, GROUP }

fun dimsFor(kind: DiscoverKind): List<FilterDim> = when (kind) {
    // Ein Index zeigt alle seine Werte aus dem Katalog
    DiscoverKind.STOCKS -> listOf(FilterDim.INDEX, FilterDim.INDUSTRY, FilterDim.COUNTRY)
    DiscoverKind.ETFS -> listOf(FilterDim.INDUSTRY, FilterDim.REGION, FilterDim.INDEX)
    DiscoverKind.DERIVATIVES -> listOf(FilterDim.CLASS, FilterDim.INDUSTRY, FilterDim.COUNTRY, FilterDim.REGION)
    // Wenige feste Gruppen: stehen als Pillen direkt in der Zeile (pillenFilter)
    DiscoverKind.INDICES, DiscoverKind.COMMODITIES, DiscoverKind.CURRENCIES -> emptyList()
}

// Unterfilter, dessen Optionen ohne Auswahlblatt als Pillen in der Zeile stehen, mit den Optionen in fester Reihenfolge.
fun pillenFilter(kind: DiscoverKind): Pair<FilterDim, List<String>>? = when (kind) {
    DiscoverKind.INDICES -> FilterDim.REGION to Suchgruppen.REGIONEN
    DiscoverKind.COMMODITIES -> FilterDim.GROUP to Suchgruppen.ROHSTOFF_GRUPPEN
    DiscoverKind.CURRENCIES -> FilterDim.GROUP to Suchgruppen.WAEHRUNGS_GRUPPEN
    else -> null
}

private fun kindTitle(kind: DiscoverKind): String = when (kind) {
    DiscoverKind.STOCKS -> S.stocks; DiscoverKind.ETFS -> S.etfs; DiscoverKind.DERIVATIVES -> S.derivatives
    DiscoverKind.INDICES -> S.indices; DiscoverKind.COMMODITIES -> S.assetClassName("ROHSTOFF"); DiscoverKind.CURRENCIES -> S.assetClassName("WAEHRUNG")
}

private fun DiscoverFilters.get(dim: FilterDim): Set<String> = when (dim) {
    FilterDim.CLASS -> classes; FilterDim.INDUSTRY -> industries; FilterDim.COUNTRY -> countries
    FilterDim.REGION -> regions; FilterDim.INDEX -> indices; FilterDim.GROUP -> groups
}

private fun DiscoverFilters.with(dim: FilterDim, v: Set<String>): DiscoverFilters = when (dim) {
    FilterDim.CLASS -> copy(classes = v); FilterDim.INDUSTRY -> copy(industries = v); FilterDim.COUNTRY -> copy(countries = v)
    FilterDim.REGION -> copy(regions = v); FilterDim.INDEX -> copy(indices = v); FilterDim.GROUP -> copy(groups = v)
}

private fun DiscoverItem.values(dim: FilterDim): List<String> = when (dim) {
    FilterDim.CLASS -> listOf(assetClass); FilterDim.INDUSTRY -> listOf(industry); FilterDim.COUNTRY -> listOf(country)
    FilterDim.REGION -> listOf(region); FilterDim.INDEX -> indices; FilterDim.GROUP -> listOf(group)
}.filter { it.isNotBlank() }

private fun dimTitle(dim: FilterDim): String = when (dim) {
    FilterDim.CLASS -> S.assetClass; FilterDim.INDUSTRY -> S.industries; FilterDim.COUNTRY -> S.countries
    FilterDim.REGION -> S.regions; FilterDim.INDEX -> S.indices; FilterDim.GROUP -> S.suGruppeTitel
}

private fun optionLabel(dim: FilterDim, key: String): String = when (dim) {
    FilterDim.CLASS -> S.assetClassName(key); FilterDim.INDUSTRY -> S.industryName(key); FilterDim.COUNTRY -> S.countryLabel(key)
    FilterDim.REGION -> S.regionName(key); FilterDim.INDEX -> Catalog.indexName(key); FilterDim.GROUP -> S.suGruppe(key)
}

// Top Movers

@Composable
fun MoversPage(state: AppState, onBack: () -> Unit, onOpen: (String) -> Unit) {
    // Alle Katalogaktien und die groessten Bewegungen des US-Markts laut Yahoo (seit 5.92), je 20 Gewinner und Verlierer
    val list = rememberBewegungen(state)
    // Zeitraum von einem Tag bis fuenf Jahre und der Filter RVOL ab 2 (q78f). Laengere Zeitraeume gelten fuer die 200
    // groessten Werte der Liste, damit die Abfrage der Kurse klein bleibt.
    var zeit by rememberSaveable { mutableIntStateOf(0) }
    var nurRvol by rememberSaveable { mutableStateOf(false) }
    val tage = MOVER_TAGE.getOrElse(zeit) { 1 }
    val gross = remember(list) { list.sortedByDescending { it.third.marketCap }.take(200) }
    var lang by remember(tage, gross) { mutableStateOf<Map<String, Double>?>(null) }
    LaunchedEffect(tage, gross) { if (tage > 1 && gross.isNotEmpty()) lang = runCatching { ZeitraumKurse.aenderungen(state, gross.map { it.first }, tage) }.getOrDefault(emptyMap()) }
    val eintraege = remember(list, gross, lang, tage, nurRvol) {
        val basis = if (tage <= 1) list.map { MoverEintrag(it.first, it.second, it.third, it.third.sitzungPct) }
        else gross.mapNotNull { (s, n, q) -> lang?.get(s)?.let { MoverEintrag(s, n, q, it) } }
        if (nurRvol) basis.filter { (MoverGrund.rvol(it.q) ?: 0.0) >= MoverGrund.RVOL_FILTER } else basis
    }
    val best = remember(eintraege) { eintraege.filter { it.aenderung > 0 }.sortedByDescending { it.aenderung }.take(20) }
    val worst = remember(eintraege) { eintraege.filter { it.aenderung < 0 }.sortedBy { it.aenderung }.take(20) }
    val hinweise = rememberMoverHinweise(state, remember(best, worst) { (best + worst).map { it.symbol to it.name } })
    val laedt = list.isEmpty() || (tage > 1 && lang == null)
    val liste = rememberLazyListState()
    val dismiss = rememberSwipeDownDismiss(onBack)

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { !liste.canScrollBackward }, state = liste) {
        item { DiscoverTitle(S.topMovers, null, onBack) }
        item {
            ZeitLeiste(MOVER_TAGE.indices.map { S.moverZeit(it) }, selected = zeit, onSelect = { zeit = it }, modifier = Modifier.padding(horizontal = 12.dp))
            Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) { Chip(S.moverRvolFilter, active = nurRvol) { nurRvol = !nurRvol } }
        }
        item { SectionHeader(S.bestPerformers) }
        if (best.isEmpty()) item { EmptyHint(if (laedt) S.loading else S.heatmapLeer) }
        items(best, key = { "b-" + it.symbol }) { MoverZeile(state, it, hinweise, onOpen) }
        item { SectionHeader(S.worstPerformers) }
        if (worst.isEmpty()) item { EmptyHint(if (laedt) S.loading else S.heatmapLeer) }
        items(worst, key = { "w-" + it.symbol }) { MoverZeile(state, it, hinweise, onOpen) }
        item { Spacer(Modifier.height(60.dp)) }
    }
}

// IPOs (5.94 neu, 0.68 Reiter zum Wischen): Pillenleiste mit Anzahl ueber wischbaren Seiten, Termine aus dem
// IPO-Kalender der Nasdaq, mit Finnhub-Schluessel ergaenzt, und je Angebot ein Detailblatt mit Zeitleiste, Eckdaten,
// Einschaetzung, Branche, Diskussion und Prospekten. Der Katalog liefert die handgeschriebenen Texte und die
// bekannten Boersengaenge der letzten Jahre.

// SPERRFRISTEN (0.78): Sperrfristen, die bald enden, ui/IpoSperrfristen.kt
private enum class IpoReiter { UEBERSICHT, DEMNAECHST, EINGEREICHT, NEU, ZURUECK, BEKANNT, SPERRFRISTEN }

// Eingereichte, neu gelistete und zurueckgezogene Angebote der letzten 75 Tage; angekuendigte immer.
private const val IPO_TAGE = 75
internal const val IPO_TAG_MS = 86_400_000L

// Gleiche Firma im Kalender und im Katalog: gleicher Namensstamm oder der Katalogname als Anfang („Acme, Inc.“).
private fun ipoGleich(kalender: String, katalog: String): Boolean {
    val a = GleicheFirma.stamm(kalender)
    val b = GleicheFirma.stamm(katalog)
    return b.length >= 3 && (a == b || a.startsWith("$b "))
}

internal fun ipoDollar(v: Double): String =
    if (I18n.lang == Lang.DE) Fmt.compact(v).replace(' ', '\u00A0') + " $" else "$" + Fmt.compact(v).replace(' ', '\u00A0')

// Preis oder Spanne der Quelle („14.00-16.00“) in der Schreibweise der Sprache.
internal fun ipoPreisText(preis: String): String? =
    preis.split('-', '–').mapNotNull { IpoKalender.zahl(it) }.takeIf { it.isNotEmpty() }
        ?.joinToString(" " + r2("bis") + " ") { Fmt.num(it, 2) }?.let { if (I18n.lang == Lang.DE) "$it $" else "$$it" }

// Ein einzelner Preis in der Schreibweise der Sprache.
internal fun ipoPreisEinzeln(v: Double): String =
    Fmt.num(v, 2).let { if (I18n.lang == Lang.DE) "$it $" else "$$it" }

internal fun boerseKurz(b: String): String = when {
    b.contains("nasdaq", true) -> "Nasdaq"
    b.contains("american", true) -> "NYSE American"
    b.contains("nyse", true) || b.contains("new york", true) -> "NYSE"
    else -> b
}

private fun ipoDatum(tag: Long): String = Fmt.date(tag * IPO_TAG_MS + 12 * 3_600_000L)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IposPage(state: AppState, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val catalog = rememberCatalog()
    val ipos = catalog?.ipos ?: Catalog.Ipos()
    var angebote by remember { mutableStateOf<List<IpoKalender.Angebot>?>(null) }
    var geladen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { angebote = ohneAbbruch { state.ipoKalender() }.getOrNull(); geladen = true }
    val heute = remember { java.time.LocalDate.now().toEpochDay() }
    val aktuell = remember(angebote) {
        angebote.orEmpty().filter { it.status == IpoKalender.Status.ANGEKUENDIGT || (it.tag ?: 0L) >= heute - IPO_TAGE }
    }
    val gruppen = remember(aktuell) {
        mapOf(
            IpoReiter.DEMNAECHST to aktuell.filter { it.status == IpoKalender.Status.ANGEKUENDIGT }.sortedBy { it.tag ?: Long.MAX_VALUE },
            // Grosse Angebote zuerst, Mantelgesellschaften zuletzt, damit bekannte Namen nicht zwischen ihnen verschwinden
            IpoReiter.EINGEREICHT to IpoUebersicht.grossZuerst(aktuell.filter { it.status == IpoKalender.Status.EINGEREICHT }),
            IpoReiter.NEU to aktuell.filter { it.status == IpoKalender.Status.PREIS }.sortedByDescending { it.tag ?: 0L },
            IpoReiter.ZURUECK to aktuell.filter { it.status == IpoKalender.Status.ZURUECK }.sortedByDescending { it.tag ?: 0L },
        )
    }
    // Katalogtexte zu Angeboten im Kalender; geplante ohne Eintrag dort stehen unter „Bekannte“, ohne alten Termin
    val katalogText = remember(angebote, ipos) {
        buildMap { for (k in ipos.bevorstehend) angebote.orEmpty().firstOrNull { ipoGleich(it.name, k.name) }?.let { put(it.deal, k) } }
    }
    val geplant = remember(angebote, ipos) { ipos.bevorstehend.filter { k -> angebote.orEmpty().none { ipoGleich(it.name, k.name) } } }
    // Kopfzahlen und grosse Angebote der Uebersicht
    val kopf = remember(aktuell, heute) { IpoUebersicht.kopf(aktuell, heute) }
    val grosse = remember(aktuell) { IpoUebersicht.grosse(aktuell) }
    val symbols = remember(gruppen, ipos) {
        (gruppen[IpoReiter.NEU].orEmpty().mapNotNull { it.symbol } + ipos.abgeschlossen.map { it.s }).distinct()
    }
    val quotes = rememberSnapshot(state, symbols)
    // Ergebnis des ersten Handelstags der neu gelisteten Angebote: Schluss gegen Ausgabepreis (q78f)
    var ersterTag by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    LaunchedEffect(gruppen) {
        val neu = gruppen[IpoReiter.NEU].orEmpty().filter { it.symbol != null && it.tag != null && it.tag < heute && it.preisZahl != null }
        if (neu.isEmpty()) return@LaunchedEffect
        val kerzen = neu.mapNotNull { it.symbol }.chunked(20).fold(emptyMap<String, List<de.troetenrepublik.app.data.Candle>>()) { acc, c -> acc + runCatching { state.schlusskurse(c, "6mo") }.getOrDefault(emptyMap()) }
        ersterTag = neu.mapNotNull { a -> de.troetenrepublik.app.engine.ErsterTag.ergebnis(kerzen[a.symbol].orEmpty(), a.tag!!, a.preisZahl)?.let { a.deal to it } }.toMap()
    }
    val dismiss = rememberSwipeDownDismiss(onBack)
    var auswahl by remember { mutableStateOf<IpoKalender.Angebot?>(null) }
    var katalogAuswahl by remember { mutableStateOf<Catalog.IpoUpcoming?>(null) }
    var reiterWahl by rememberSaveable { mutableStateOf<Int?>(null) }
    // Startreiter, solange der Nutzer nichts gewaehlt hat
    val start = if (geladen && angebote == null) IpoReiter.BEKANNT else IpoReiter.UEBERSICHT
    val de = I18n.lang == Lang.DE
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val mitKontakt = SecInsider.kennung(settings.secKontakt) != null
    val ohneKalender = geladen && angebote == null
    // Ohne Kalender (keine Antwort der Nasdaq, kein Finnhub-Schluessel): die Prospekte aus dem Tagesindex der SEC
    var secListe by remember { mutableStateOf<List<SecIpo.Eintrag>?>(null) }
    LaunchedEffect(mitKontakt, ohneKalender) { secListe = null; if (mitKontakt && ohneKalender) secListe = runCatching { state.secIpos() }.getOrDefault(emptyList()) }

    val pager = rememberPagerState(initialPage = reiterWahl ?: IpoReiter.UEBERSICHT.ordinal) { IpoReiter.entries.size }
    // Sperrfristen: die Prospekte werden erst gelesen, wenn der Reiter offen ist; null: noch nicht geladen
    var sperrfristen by remember { mutableStateOf<List<de.troetenrepublik.app.engine.IpoSperrfristen.Eintrag>?>(null) }
    var sperrGeladen by remember { mutableStateOf(false) }
    val sperrOffen = pager.settledPage == IpoReiter.SPERRFRISTEN.ordinal
    LaunchedEffect(sperrOffen, mitKontakt) {
        if (sperrOffen && mitKontakt && !sperrGeladen) {
            ohneAbbruch { state.ipoSperrfristen { sperrfristen = it } }.getOrNull()?.let { sperrfristen = it }
            sperrGeladen = true
        }
    }
    val listen = remember { IpoReiter.entries.map { LazyListState() } }
    val scope = rememberCoroutineScope()
    // Hat der Nutzer vor dem Laden getippt oder gewischt, bleibt seine Seite stehen
    var nutzerVorLaden by remember { mutableStateOf(false) }
    val geladenJetzt by rememberUpdatedState(geladen)
    LaunchedEffect(pager) {
        pager.interactionSource.interactions.collect { if (it is DragInteraction.Start && !geladenJetzt) nutzerVorLaden = true }
    }
    LaunchedEffect(start, reiterWahl, geladen, nutzerVorLaden) {
        if (reiterWahl == null) {
            if (nutzerVorLaden) {
                if (geladen) reiterWahl = pager.currentPage
            } else {
                pager.scrollToPage(start.ordinal)
                if (geladen) reiterWahl = start.ordinal
            }
        }
    }
    // Steht der Startreiter fest, gilt jede ruhende Seite als Wahl des Nutzers
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { if (geladenJetzt && reiterWahl != null) reiterWahl = it }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .sheetDismiss(dismiss) { listen.getOrNull(pager.currentPage)?.canScrollBackward != true },
    ) {
        DiscoverTitle(S.ipos, null, onBack)
        val wm = rememberReiterWidgetMenue(state) { WidgetVorgabe.termine(WidgetVorgabe.TERMIN_IPO) }
        MitlaufendeReiter(
            anzahl = IpoReiter.entries.size,
            pager = pager,
            langDruecken = wm.langDruecken,
            menue = { i -> wm.menue(i) },
            titel = { i ->
                val r = IpoReiter.entries[i]
                val n = if (r == IpoReiter.BEKANNT) geplant.size + ipos.abgeschlossen.size else gruppen[r].orEmpty().size
                // Ohne Kalender keine Zahl: „0“ hiesse, es gaebe keine Boersengaenge
                val zahl = if (r == IpoReiter.SPERRFRISTEN) (if (sperrGeladen) " · " + sperrfristen.orEmpty().size else "")
                else if (r == IpoReiter.UEBERSICHT || (r != IpoReiter.BEKANNT && (!geladen || ohneKalender))) "" else " · $n"
                S.ipoReiter(i) + zahl
            },
            modifier = Modifier.padding(vertical = 12.dp),
        ) { i ->
            if (!geladen) nutzerVorLaden = true
            scope.launch { pager.animateScrollToPage(i) }
        }
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().mausWischen(pager)) { seite ->
            if (IpoReiter.entries[seite] == IpoReiter.SPERRFRISTEN) IpoSperrfristSeite(listen[seite], sperrfristen, sperrGeladen, mitKontakt) { auswahl = it }
            else IpoReiterSeite(
                r = IpoReiter.entries[seite], liste = listen[seite], geladen = geladen, ohneKalender = ohneKalender,
                gruppe = gruppen[IpoReiter.entries[seite]].orEmpty(), geplant = geplant, ipos = ipos, quotes = quotes, ersterTag = ersterTag,
                secListe = secListe, mitKontakt = mitKontakt, mitFinnhub = settings.keyFinnhub.isNotBlank(),
                kopf = kopf, grosse = grosse,
                onReiter = { i -> scope.launch { pager.animateScrollToPage(IpoReiter.DEMNAECHST.ordinal + i) } },
                onAngebot = { auswahl = it }, onKatalog = { katalogAuswahl = it }, onOpen = onOpen,
            )
        }
    }

    auswahl?.let { a ->
        val kurs = a.symbol?.let { quotes[it] }?.takeIf { it.nativeCurrency.equals("USD", true) }
        // Nach dem geoeffneten Papier steht das Blatt wieder da
        WiederkehrBlatt("ipo-" + a.deal) { w -> IpoDetail(state, a, katalogText[a.deal], mitKontakt, kurs, aktuell, ersterTag, onOpen = { w.verlassen(); onOpen(it) }) { auswahl = null } }
    }

    katalogAuswahl?.let { ipo ->
        ModalBottomSheet(
            onDismissRequest = { katalogAuswahl = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
        ) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState())) {
                Text(S.ipoCardTitle(ipo.name), style = MaterialTheme.typography.headlineSmall)
                Text(S.ipoKatalogGeplant(isoDate(ipos.stand) ?: ipos.stand), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                Text(if (de) ipo.textDe else ipo.textEn, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}

// Eine Seite der Boersengaenge: Einleitung, dann der Inhalt des Reiters mit eigener Liste.
@Composable
private fun IpoReiterSeite(
    r: IpoReiter, liste: LazyListState, geladen: Boolean, ohneKalender: Boolean,
    gruppe: List<IpoKalender.Angebot>, geplant: List<Catalog.IpoUpcoming>, ipos: Catalog.Ipos, quotes: Map<String, Quote>, ersterTag: Map<String, Double>,
    secListe: List<SecIpo.Eintrag>?, mitKontakt: Boolean, mitFinnhub: Boolean,
    kopf: IpoUebersicht.Kopf, grosse: List<IpoKalender.Angebot>, onReiter: (Int) -> Unit,
    onAngebot: (IpoKalender.Angebot) -> Unit, onKatalog: (Catalog.IpoUpcoming) -> Unit, onOpen: (String) -> Unit,
) {
    val uri = appLinks()
    // Mehr als acht Zeilen: die ersten stehen da, der Rest ist eingeklappt mit Zusammenfassung (Zustand je Reiter gemerkt)
    var restOffen by rememberSaveable(r.name) { mutableStateOf(false) }
    val sichtbar = remember(gruppe) { gruppe.take(IpoUebersicht.ZEILEN_OFFEN) }
    val rest = remember(gruppe) { gruppe.drop(IpoUebersicht.ZEILEN_OFFEN) }
    val restKern = remember(rest, I18n.lang) {
        val spacs = rest.count { it.spac }
        val summe = IpoUebersicht.volumenSumme(rest)
        val teile = listOfNotNull(
            if (spacs > 0) KernTeil(S.ipoDavonSpac(spacs)) else null,
            if (summe > 0) KernTeil(S.ipoVolumen + " " + ipoDollar(summe)) else null,
        )
        if (teile.isEmpty()) null else KernZeile(teile)
    }
    val seitFuer = { a: IpoKalender.Angebot ->
        val q = a.symbol?.let { quotes[it] }
        if (a.status == IpoKalender.Status.PREIS && q != null && q.nativeCurrency.equals("USD", true)) IpoBewertung.seitAusgabe(a.preisZahl, q.nativePrice) else null
    }
    LazyColumn(Modifier.fillMaxSize(), state = liste) {
        if (r == IpoReiter.BEKANNT) {
            if (geplant.isNotEmpty()) {
                item { SectionHeader(S.ipoKatalogGeplant(isoDate(ipos.stand) ?: ipos.stand)) }
                items(geplant, key = { "plan:" + it.name }) { k ->
                    AssetRow(symbol = k.name, name = k.name, subtitle = null, onClick = { onKatalog(k) })
                }
            }
            if (ipos.abgeschlossen.isNotEmpty()) {
                item { SectionHeader(S.completed) }
                items(ipos.abgeschlossen, key = { it.s }) { ipo ->
                    val q = quotes[ipo.s]
                    val since = if (q != null && q.nativeCurrency.equals(ipo.waehrung, true)) IpoBewertung.seitAusgabe(ipo.preis, q.nativePrice) else null
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
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }
        } else if (!geladen) {
            item { EmptyHint(S.loading) }
        } else if (ohneKalender) {
            item { EmptyHint(S.nkIpoFehlt) }
            item { SectionHeader(S.secIpoTitel) }
            when {
                !mitKontakt -> item { EmptyHint(S.secIpoKontakt) }
                secListe == null -> item { EmptyHint(S.loading) }
                secListe.isEmpty() -> item { EmptyHint(S.secIpoLeer) }
                else -> items(secListe, key = { it.datei }) { e ->
                    val formText = (when (e.art) {
                        SecIpo.Art.PROSPEKT -> S.secIpoProspekt
                        SecIpo.Art.AENDERUNG -> S.secIpoAenderung
                        SecIpo.Art.PREIS -> S.secIpoPreis
                    }) + " (" + e.formular + ")"
                    Column(Modifier.fillMaxWidth().clickable { runCatching { uri.openUri(e.link) } }.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Text(e.name, style = MaterialTheme.typography.titleMedium)
                        Text(listOf(formText, isoDate(e.tag) ?: e.tag).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else if (r == IpoReiter.UEBERSICHT) {
            item(key = "kopf", contentType = "ipo-kopf") { IpoKopf(kopf, onReiter) }
            if (grosse.isEmpty()) item(key = "grosse-leer") { EmptyHint(S.ipoReiterLeer) }
            else {
                item(key = "grosse-titel", contentType = "ipo-titel") { SectionHeader(S.ipoGrosse) }
                items(grosse, key = { "g-" + it.deal }, contentType = { "ipo" }) { a -> IpoZeile(a, null, null) { onAngebot(a) } }
            }
        } else {
            if (gruppe.isEmpty()) item { EmptyHint(S.ipoReiterLeer) }
            items(sichtbar, key = { it.deal }, contentType = { "ipo" }) { a ->
                IpoZeile(a, seitFuer(a), ersterTag[a.deal]) { onAngebot(a) }
            }
            if (rest.isNotEmpty()) {
                item(key = "rest-kopf", contentType = "kern-kopf") {
                    KernKopf(S.ipoWeitere(rest.size), restKern, restOffen, gross = false) { restOffen = !restOffen }
                }
                if (restOffen) items(rest, key = { it.deal }, contentType = { "ipo" }) { a ->
                    IpoZeile(a, seitFuer(a), ersterTag[a.deal]) { onAngebot(a) }
                }
            }
        }
        if (r != IpoReiter.BEKANNT && !ohneKalender && mitFinnhub) item { QuellenAnhang(listOf(FINNHUB_QUELLE)) }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

// Drei Schritte nebeneinander: Eingereicht, Preis fest, Handel. Erreichte Schritte gefuellt, das Datum steht beim
// Schritt, zu dem es gehoert, darunter der Abstand zu heute. Zurueckgezogen: der zweite Schritt in Rot, der dritte
// bleibt offen.
@Composable
private fun IpoZeitleiste(zl: IpoBewertung.Zeitleiste, tag: Long?, heute: Long, modifier: Modifier = Modifier) {
    val voll = MaterialTheme.colorScheme.onSurface
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val blass = MaterialTheme.colorScheme.outlineVariant
    Row(modifier.fillMaxWidth()) {
        for (i in 0..2) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(2.dp).background(if (i == 0) Color.Transparent else if (i <= zl.erreicht) voll else blass))
                    val rot = zl.zurueck && i == 1
                    val kreis = Modifier.size(12.dp).clip(CircleShape)
                    when {
                        rot -> Box(kreis.background(LocalSignal.current.down))
                        i <= zl.erreicht -> Box(kreis.background(voll))
                        else -> Box(kreis.border(1.5.dp, grau, CircleShape))
                    }
                    Box(Modifier.weight(1f).height(2.dp).background(if (i == 2) Color.Transparent else if (i + 1 <= zl.erreicht) voll else blass))
                }
                Text(
                    if (zl.zurueck && i == 1) S.ipoSchrittZurueck else S.ipoSchritt(i),
                    style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
                if (tag != null && i == zl.datumBei) {
                    Text(
                        ipoDatum(tag), style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                    IpoBlatt.tageBis(tag, heute)?.let {
                        Text(
                            ipoTageText(it), style = MaterialTheme.typography.bodySmall, color = grau, maxLines = 1, softWrap = false,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

// Detailblatt eines Angebots, aufgebaut wie die Seite eines Wertpapiers: Kopf mit Logo, Name und grossem Wert, nach der
// Erstnotiz der Kursverlauf mit der Linie des Ausgabepreises, dann Zeitleiste, Eckdaten, Groesse gegen die anderen
// Angebote (alle) und der erste Handelstag der juengsten Boersengaenge (ersterTag je Angebot, von der Liste berechnet);
// danach die Texte. Prospekte nur mit Kontaktadresse: CIK aus der Kuerzelliste der SEC oder ueber den Namen aus dem
// Tagesindex der letzten Tage; sonst ein Link auf die Volltextsuche der SEC. kurs: Kurs in Dollar nach der Erstnotiz,
// sonst null.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IpoDetail(
    state: AppState, a: IpoKalender.Angebot, katalog: Catalog.IpoUpcoming?, mitKontakt: Boolean, kurs: Quote?,
    alle: List<IpoKalender.Angebot>, ersterTag: Map<String, Double>,
    onOpen: (String) -> Unit, onDismiss: () -> Unit,
) {
    val uri = appLinks()
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val signal = LocalSignal.current
    var cik by remember(a.deal) { mutableStateOf<String?>(null) }
    LaunchedEffect(a.deal, mitKontakt) {
        if (mitKontakt) cik = runCatching {
            state.ipoCik(a.symbol) ?: state.secIpos().firstOrNull { ipoGleich(a.name, it.name) }?.cik
                ?: GleicheFirma.stamm(a.name).takeIf { it.length >= 3 }?.let { stamm -> state.ipoCikNachName(a.name) { GleicheFirma.stamm(it) == stamm } }
        }.getOrNull()
    }
    var doks by remember(a.deal) { mutableStateOf<List<IpoKalender.Dokument>?>(null) }
    LaunchedEffect(cik, mitKontakt) { val c = cik; if (c != null && mitKontakt) doks = runCatching { state.ipoDokumente(c) }.getOrNull() }
    // Juengster Bericht der Presse zu einem anstehenden oder eingereichten Angebot
    var bericht by remember(a.deal) { mutableStateOf<LaenderZusatz.Meldung?>(null) }
    LaunchedEffect(a.deal, a.status) {
        if (a.status == IpoKalender.Status.ANGEKUENDIGT || a.status == IpoKalender.Status.EINGEREICHT) bericht = ohneAbbruch { state.ipoBericht(a.name) }.getOrNull()
    }
    var netz by remember(a.deal) { mutableStateOf<de.troetenrepublik.app.market.Ersatzabrufe.Netz?>(null) }
    LaunchedEffect(a.deal) { netz = state.netzErwaehnungen(a.name) }
    // Tageskurse seit der Erstnotiz
    var verlauf by remember(a.deal) { mutableStateOf<List<Candle>>(emptyList()) }
    LaunchedEffect(a.symbol) {
        val s = a.symbol
        if (s != null && a.status == IpoKalender.Status.PREIS) verlauf = runCatching { state.kurseNativ(s) }.getOrDefault(emptyList())
    }
    val kerzen = remember(verlauf, a.tag) { IpoBlatt.kerzenAb(verlauf, a.tag) }
    var scrub by remember(a.deal) { mutableStateOf<Candle?>(null) }
    val newsStand by state.news.stand.collectAsStateWithLifecycle()
    // Schlagzeilen der App mit dem ersten Wort des Namens (mindestens vier Buchstaben, sonst zu viele Zufallstreffer)
    val wort = remember(a.name) { GleicheFirma.stamm(a.name).split(' ').firstOrNull()?.takeIf { it.length >= 4 } }
    val schlagzeilen = remember(newsStand.eintraege, wort) {
        if (wort == null) emptyList() else {
            val m = Regex("\\b" + Regex.escape(wort) + "\\b", RegexOption.IGNORE_CASE)
            Rss.drittel(newsStand.eintraege.filter { m.containsMatchIn(it.titel) }.distinctBy { it.titel }.take(5))
        }
    }
    // Der Bericht steht als erste Schlagzeile, nicht unter dem Kopf
    val nachrichten = remember(bericht, schlagzeilen) {
        val b = bericht?.let { m -> Rss.Eintrag(titel = m.titel, link = m.link, zeit = m.zeit, herausgeber = m.quelle, quelle = "Google News") }
        listOfNotNull(b) + schlagzeilen.filter { it.titel != b?.titel }
    }
    var offeneNachricht by remember { mutableStateOf<Rss.Eintrag?>(null) }
    val heute = remember { java.time.LocalDate.now().toEpochDay() }
    val de = I18n.lang == Lang.DE
    val punkte = remember(a) { IpoBewertung.punkte(a) }
    val branche = remember(a) { IpoBewertung.branche(a.name, null) }
    val zeitleiste = remember(a, heute) { IpoBewertung.zeitleiste(a.status, a.tag, heute) }
    val spanne = remember(a) { IpoBewertung.spanne(a.preis)?.takeIf { it.first < it.second } }
    val gestartet = a.status == IpoKalender.Status.PREIS
    val kopfwert = remember(a, kurs?.nativePrice) { IpoBlatt.kopfwert(a, kurs?.nativePrice) }
    val ausgabe = a.preisZahl?.takeIf { gestartet && it > 0 }
    val eigenerTag = if (gestartet) ersterTag[a.deal] else null
    val mitVergleich = a.status != IpoKalender.Status.ZURUECK
    val groesse = remember(a, alle) { if (mitVergleich) IpoBlatt.groesse(a, alle) else null }
    val ersteTage = remember(a, alle, ersterTag) { if (mitVergleich) IpoBlatt.ersteTage(a, alle, ersterTag) else null }
    // Kurs anderer Boersengaenge derselben Art nach dem Start
    var fristen by remember(a.deal) { mutableStateOf<List<IpoBlatt.Frist>>(emptyList()) }
    LaunchedEffect(a.deal, mitVergleich) { if (mitVergleich) fristen = ohneAbbruch { state.ipoVerlauf(a.spac) }.getOrDefault(emptyList()) }
    val forschung = mitVergleich && !a.spac
    // Anteilseigner, Banken und Sperrfrist aus dem Prospekt
    var prospekt by remember(a.deal) { mutableStateOf<IpoProspekt.Auszug?>(null) }
    LaunchedEffect(doks, mitKontakt) { val d = doks; if (d != null && mitKontakt) prospekt = ohneAbbruch { state.ipoProspekt(d) }.getOrNull() }
    var halterOffen by rememberSaveable(a.deal) { mutableStateOf(false) }
    var bankenOffen by rememberSaveable(a.deal) { mutableStateOf(false) }
    val sperrfrist = prospekt?.sperrfristTage
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = blattZustandHier(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        LazyColumn(Modifier.fillMaxWidth()) {
            item(key = "kopf", contentType = "kopf") {
                // Der Wert am Fadenkreuz wird erst hier gelesen: Beim Ziehen entsteht nur dieser Kopf neu.
                val gewaehlt = scrub
                Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IpoLogo(a, 48)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.name, style = MaterialTheme.typography.headlineSmall)
                            PunktZeile(
                                listOfNotNull(a.symbol, a.boerse?.let(::boerseKurz), if (a.spac) "SPAC" else null),
                                Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    if (kopfwert != null) {
                        val alsKurs = kopfwert.art == IpoBlatt.WertArt.KURS
                        val wert = if (alsKurs && gewaehlt != null) gewaehlt.close else kopfwert.wert
                        val seit = if (alsKurs) IpoBewertung.seitAusgabe(ausgabe, wert) else null
                        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.Bottom) {
                            Text(ipoPreisEinzeln(wert), style = MaterialTheme.typography.displayMedium, maxLines = 1, softWrap = false)
                            if (seit != null) {
                                Spacer(Modifier.width(12.dp))
                                ChangeValue(
                                    change = seit, text = Fmt.pct(abs(seit), 1), style = MaterialTheme.typography.titleSmall,
                                    triangleWidth = 10.dp, modifier = Modifier.padding(vertical = 8.dp),
                                )
                            }
                        }
                        val unter = when {
                            alsKurs && gewaehlt != null -> Fmt.date(gewaehlt.t)
                            alsKurs -> ausgabe?.let { ipoText("wert.KURS", ipoPreisEinzeln(it)) }
                            else -> ipoText("wert." + kopfwert.art.name)
                        }
                        if (unter != null) Text(unter, style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(top = 2.dp))
                    }
                    if (gestartet && a.symbol != null) {
                        TextButton(onClick = { onOpen(a.symbol) }, contentPadding = PaddingValues(0.dp)) { Text(S.nwZurAktie) }
                    }
                }
            }
            // Kursverlauf seit der Erstnotiz mit der Linie des Ausgabepreises
            if (gestartet && kurs != null && kerzen.size >= 2) item(key = "chart", contentType = "chart") {
                val linien = remember(ausgabe, de, grau) { listOfNotNull(ausgabe?.let { KursLinie(it, S.ipoAusgabe(ipoPreisEinzeln(it)), grau) }) }
                Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(210.dp)) {
                    PriceChart(
                        candles = kerzen, modifier = Modifier.fillMaxWidth().height(210.dp),
                        baseline = ausgabe, onScrub = { scrub = it }, linien = linien,
                    )
                    ChartCrosshair(
                        candles = kerzen, selected = scrub, color = signal.line, baseline = ausgabe,
                        modifier = Modifier.fillMaxWidth().height(210.dp), linien = linien,
                    )
                }
            }
            item(key = "zeitleiste") { IpoZeitleiste(zeitleiste, a.tag, heute, Modifier.padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 4.dp)) }
            if (spanne != null || a.aktien != null || a.volumen != null || eigenerTag != null || sperrfrist != null) {
                item { SectionHeader(S.ipoEckdaten) }
                if (spanne != null) item(key = "spanne") { IpoSpanne(spanne.first, spanne.second) }
                eigenerTag?.let { item { KeyValueRow(ipoText("ersterTag"), Fmt.signedPct(it, 1), valueColor = signal.forChange(it)) } }
                a.aktien?.let { item { KeyValueRow(S.ipoAktien, Fmt.compact(it.toDouble()).replace(' ', ' ')) } }
                a.volumen?.let { item { KeyValueRow(if (IpoUebersicht.platzhalter(a)) S.ipoVolumenProspekt else S.ipoVolumen, ipoDollar(it)) } }
                sperrfrist?.let { tage ->
                    // Die Frist laeuft ab dem Prospekt, also ab der Preisfestsetzung
                    val bis = a.tag?.takeIf { gestartet }?.let { ipoText("sperrfrist.bis", ipoDatum(it + tage)) }
                    item { KeyValueRow(ipoText("sperrfrist"), ipoText("sperrfrist.tage", Fmt.num(tage.toDouble(), 0)), hint = bis) }
                }
            } else if (kopfwert == null) {
                item { SectionHeader(S.ipoEckdaten) }
                item { EmptyHint(S.ipoKeineEckdaten) }
            }
            prospekt?.let { p ->
                val ende = { t: Int -> a.tag?.takeIf { gestartet }?.let { ipoDatum(it + t) } }
                ipoAngebot(p, ende)
                ipoStufen(p, ende)
            }
            prospekt?.eigner?.let { e ->
                val groesster = e.halter.first().anteil
                // Fehlen nur ein oder zwei Zeilen, stehen alle da
                val sichtbar = if (e.halter.size <= IpoProspekt.HALTER_OFFEN + 2) e.halter else e.halter.take(IpoProspekt.HALTER_OFFEN)
                val rest = e.halter.drop(sichtbar.size)
                item { SectionHeader(ipoText("eigner.titel")) }
                if (e.stimmrechte) item {
                    Text(ipoText("eigner.stimmrechte"), style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 2.dp))
                }
                items(sichtbar, key = { "h-" + it.name }, contentType = { "halter" }) { IpoHalterZeile(it, groesster) }
                if (rest.isNotEmpty()) {
                    item(key = "halter-rest") {
                        KernKopf(S.ipoWeitere(rest.size), KernZeile(listOf(KernTeil(ipoText("eigner.bis", Fmt.pct(rest.first().anteil, 1))))), halterOffen, gross = false) { halterOffen = !halterOffen }
                    }
                    if (halterOffen) items(rest, key = { "h-" + it.name }, contentType = { "halter" }) { IpoHalterZeile(it, groesster) }
                }
                e.gruppe?.let { g -> item { KeyValueRow(ipoText("eigner.gruppe"), Fmt.pct(g, 1)) } }
            }
            prospekt?.banken?.takeIf { it.isNotEmpty() }?.let { banken ->
                val mitZahl = banken.all { it.aktien != null }
                val summe = if (mitZahl) banken.sumOf { it.aktien ?: 0L } else 0L
                val groesste = banken.maxOf { it.aktien ?: 0L }.coerceAtLeast(1L)
                val sichtbar = if (banken.size <= IpoProspekt.BANKEN_OFFEN + 2) banken else banken.take(IpoProspekt.BANKEN_OFFEN)
                val rest = banken.drop(sichtbar.size)
                item { SectionHeader(ipoText("banken.titel")) }
                if (mitZahl) item {
                    Text(ipoText("banken.anteil"), style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 2.dp))
                }
                items(sichtbar, key = { "b-" + it.name }, contentType = { "bank" }) { IpoBankZeile(it, summe, groesste) }
                if (rest.isNotEmpty()) {
                    item(key = "banken-rest") { KernKopf(S.ipoWeitere(rest.size), null, bankenOffen, gross = false) { bankenOffen = !bankenOffen } }
                    if (bankenOffen) items(rest, key = { "b-" + it.name }, contentType = { "bank" }) { IpoBankZeile(it, summe, groesste) }
                }
            }
            if (groesse != null) {
                item { SectionHeader(ipoText("groesse.titel")) }
                item(key = "groesse") { IpoGroesseBalken(groesse) }
                item { KeyValueRow(ipoText("groesse.rang"), ipoText("von", Fmt.num(groesse.rang.toDouble(), 0), Fmt.num(groesse.anzahl.toDouble(), 0))) }
            }
            if (ersteTage != null) {
                item { SectionHeader(ipoText("erste.titel")) }
                item(key = "erste") { IpoErsteTageBalken(ersteTage) }
                item {
                    KeyValueRow(
                        ipoText("erste.plus"), ipoText("von", Fmt.num(ersteTage.imPlus.toDouble(), 0), Fmt.num(ersteTage.werte.size.toDouble(), 0)),
                        hint = ipoText("erste.zeitraum", Fmt.num(IPO_TAGE.toDouble(), 0)),
                    )
                }
                item { KeyValueRow(ipoText("erste.median"), Fmt.signedPct(ersteTage.median, 1), valueColor = signal.forChange(ersteTage.median)) }
            }
            if (fristen.isNotEmpty() || forschung) {
                item { SectionHeader(ipoText("verlauf.titel")) }
                item {
                    Text(ipoText("verlauf.spalte"), style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 2.dp))
                }
                items(fristen, key = { "frist-" + it.tage }, contentType = { "frist" }) { IpoFristZeile(it, ipoFristZusatz(it)) }
                if (fristen.isNotEmpty()) item(key = "frist-grundlage") {
                    Text(
                        ipoText(if (a.spac) "verlauf.grundlageSpac" else "verlauf.grundlage", Fmt.num(IpoBlatt.RUECKBLICK_MONATE.toDouble(), 0)),
                        style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 2.dp, bottom = 8.dp),
                    )
                }
                // Langfristig aus der Forschung (ohne Mantelgesellschaften)
                if (forschung) {
                    item(key = "frist-forschung", contentType = "frist") {
                        val f = IpoForschung.dreiJahre
                        val jahre = ipoText("verlauf.jahre", IpoForschung.DREI_VON.toString(), IpoForschung.DREI_BIS.toString())
                        IpoFristZeile(f, ipoText("verlauf.dividenden", ipoFristZusatz(f, jahre)))
                    }
                    item(key = "frist-jahr") {
                        val jahre = ipoText("verlauf.jahre", IpoForschung.JAHR_VON.toString(), IpoForschung.JAHR_BIS.toString())
                        KeyValueRow(
                            ipoText("verlauf.jahr"), Fmt.signedPunkte(IpoForschung.JAHR_IPO - IpoForschung.JAHR_VERGLEICH, 1), valueColor = signal.down,
                            hint = ipoText("verlauf.gegen", Fmt.pct(IpoForschung.JAHR_IPO, 1), Fmt.pct(IpoForschung.JAHR_VERGLEICH, 1)) + ", " +
                                ipoText("verlauf.anzahlJahre", ipoAnzahl(IpoForschung.JAHR_ANZAHL), jahre),
                        )
                    }
                }
            }
            if (punkte.isNotEmpty()) item { SectionHeader(S.ipoEinschaetzung) }
            items(punkte, key = { "p" + it.merkmal.name }) { p ->
                val wert = when (p.merkmal) {
                    IpoBewertung.Merkmal.GROESSE -> p.wert?.let(::ipoDollar)
                    IpoBewertung.Merkmal.SPAC -> null
                }
                // Ampel mit kurzer Beschriftung, die Erklaerung steht hinter dem Info-Symbol
                var erklaerung by remember { mutableStateOf(false) }
                Row(
                    Modifier.fillMaxWidth().clickable { erklaerung = true }.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AmpelPunkt(fwAmpelZeichen(p.ampel), fwAmpelFarbe(p.ampel))
                    Spacer(Modifier.width(10.dp))
                    Text(S.ipoPunktKurz(p.merkmal.ordinal), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Outlined.Info, S.infoErklaerung, tint = grau, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.weight(1f))
                    if (wert != null) Text(wert, style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false)
                }
                if (erklaerung) MarktInfoBlatt(S.ipoPunktKurz(p.merkmal.ordinal), S.ipoPunktErklaerung(p.merkmal.ordinal)) { erklaerung = false }
            }
            branche?.let { b ->
                item { SectionHeader(S.ipoBrancheTitel) }
                item { Text(S.ipoBranche(b.ordinal), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 20.dp)) }
                item { Box(Modifier.padding(top = 4.dp)) { Text(S.ipoBrancheText(b.ordinal), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp)) } }
            }
            val eigen = katalog?.let { if (de) it.textDe else it.textEn }?.takeIf { it.isNotBlank() }
            if (eigen != null) {
                item { SectionHeader(S.ipoUeber) }
                item { Text(eigen, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp)) }
            }
            item { SectionHeader(S.ipoDiskussion) }
            // Hacker News nur bei KI- und Software-Firmen (5.97)
            netz?.hn?.takeIf { TrendsErklaerung.hackerNewsBeiIpo(branche) }?.let { hn ->
                item { KeyValueRow("Hacker News", S.ersMastodonKurz(hn.woche)) }
            }
            netz?.mastodon?.takeIf { it.isNotEmpty() }?.let { m ->
                item { KeyValueRow("Mastodon", S.ersMastodonKurz(m.sumOf { it.beitraege })) }
            }
            item { Zwischenkopf(S.ipoSchlagzeilen) }
            if (nachrichten.isEmpty()) {
                item { Text(S.ipoKeineSchlagzeilen, style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp)) }
            } else nachrichten.forEachIndexed { i, n ->
                item(key = "n" + n.link + n.titel) {
                    Column {
                        if (i > 0) Trennlinie()
                        Column(Modifier.fillMaxWidth().clickable { offeneNachricht = n }.padding(horizontal = 20.dp, vertical = 10.dp)) {
                            Text(n.titel, style = MaterialTheme.typography.bodyMedium)
                            PunktZeile(listOf(n.herausgeber.ifBlank { n.quelle }, Fmt.date(n.zeit)).filter { it.isNotBlank() })
                        }
                    }
                }
            }
            val c = cik
            if (c != null) {
                item { SectionHeader(S.ipoDokumente) }
                val d = doks
                when {
                    !mitKontakt -> item { Text(S.secIpoKontakt, style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp)) }
                    d == null -> item { EmptyHint(S.loading) }
                    else -> d.forEachIndexed { i, dok ->
                        item(key = "d" + dok.link) {
                            Column {
                                if (i > 0) Trennlinie()
                                Row(
                                    Modifier.fillMaxWidth().clickable { runCatching { uri.openUri(dok.link) } }.padding(horizontal = 20.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(S.ipoFormular(dok.formular), style = MaterialTheme.typography.bodyMedium, color = LocalSignal.current.link, modifier = Modifier.weight(1f))
                                    Text(
                                        isoDate(dok.tag) ?: dok.tag, style = MaterialTheme.typography.bodySmall, color = grau,
                                        maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 12.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                item { QuellenZeile(S.ipoEdgarLink, Modifier.padding(top = 4.dp), onClick = { runCatching { uri.openUri(IpoKalender.edgarSeite(c)) } }) }
            } else {
                item { QuellenZeile(S.ipoEdgarSuche, Modifier.padding(top = 4.dp), onClick = { runCatching { uri.openUri(IpoKalender.edgarSuche(a.name)) } }) }
            }
            val quellen = listOfNotNull(
                FINNHUB_QUELLE.takeIf { state.settingsFlow.value.keyFinnhub.isNotBlank() },
                if (forschung) QuelleLink(ipoText("quelle.ritter", isoDate(IpoForschung.STAND) ?: IpoForschung.STAND), IpoForschung.LINK) else null,
            )
            if (quellen.isNotEmpty()) item { QuellenAnhang(quellen) }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
    offeneNachricht?.let { NachrichtBlatt(state, it, onDismiss = { offeneNachricht = null }, onOpen = onOpen) }
}

// "2026-06-12" in der Schreibweise der Sprache.
internal fun isoDate(iso: String): String? = runCatching {
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

    // Indizes, Rohstoffe, Waehrungen: Gruppen als Pillen, nur die mit Werten
    val pillen = remember(kind, all) {
        pillenFilter(kind)?.let { (dim, optionen) -> dim to optionen.filter { o -> all.any { o in it.values(dim) } } }
    }
    // Reihenfolge: meistgehandelt zuerst; ohne Kurse die Reihenfolge des Katalogs (bekannte Werte vorn). Die Listen der
    // Indizes, Rohstoffe und Waehrungen sind schon nach Bekanntheit und Gruppe geordnet.
    val ordered = remember(all, quotes) {
        if (quotes.size < 10 || pillen != null) all
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
            item { DiscoverTitle(if (pillen != null) kindTitle(kind) else S.filterTitle, null, onBack) }
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
                    // Der Titel nennt die Gattung schon, die Pillen waehlen die Gruppen (mehrere moeglich)
                    val p = pillen
                    if (p != null) {
                        val gewaehlt = filters.get(p.first)
                        p.second.forEach { o ->
                            FilterChipPill(optionLabel(p.first, o), selected = o in gewaehlt, dropdown = false) {
                                filters = filters.with(p.first, if (o in gewaehlt) gewaehlt - o else gewaehlt + o)
                            }
                        }
                    } else FilterChipPill(kindTitle(kind), selected = true, dropdown = false) {}
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
                    state = state,
                    // Ist genau eine Gruppe gewaehlt, wiederholt die Unterzeile sie nicht in jeder Zeile
                    symbol = it.symbol, name = it.name, subtitle = it.subtitle.ifBlank { null }.takeUnless { pillen?.first == FilterDim.GROUP && filters.groups.size == 1 },
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
internal fun FilterChipPill(label: String, selected: Boolean, dropdown: Boolean, onClick: () -> Unit) {
    val fg = auswahlChipSchrift(selected)
    Row(
        Modifier.auswahlChip(selected, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
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
            // Flagge nur bei einem Land; Regionen und Indizes ohne Land bekommen das gezeichnete Globussymbol
            val flag: String? = when (dim) {
                FilterDim.COUNTRY -> flagOf(key)
                FilterDim.INDEX -> Catalog.indexCountry(key).let { if (it.isEmpty()) null else flagOf(it) }
                else -> when (key) {
                    "DEUTSCHLAND" -> flagOf("DE"); "NORDAMERIKA" -> flagOf("US"); "EUROPA" -> flagOf("EU")
                    else -> null
                }
            }
            if (flag == null) Box(
                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Public, null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(22.dp))
            } else
            // Das Flaggenzeichen ist groesser als der Kreis, der eine runde Flagge aus der Mitte schneidet
            Box(Modifier.size(40.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                Text(flag, fontSize = 62.sp, lineHeight = 62.sp, maxLines = 1, softWrap = false, modifier = Modifier.wrapContentSize(unbounded = true))
            }
        }
    }
}

// Flaggenzeichen aus dem Laendercode (zwei Regionalbuchstaben); ohne gueltigen Code null, dann steht das gezeichnete Globussymbol.
fun flagOf(iso: String): String? {
    if (iso.length != 2 || !iso.all { it in 'A'..'Z' || it in 'a'..'z' }) return null
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
    "PHARMA" -> Icons.Filled.Medication; "MEDIEN" -> ZeitungIcon; "ROHSTOFFE" -> Icons.Filled.Diamond
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
internal fun DiscoverTitle(title: String, intro: String?, onBack: () -> Unit) {
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

private val FINNHUB_QUELLE = QuelleLink("Finnhub", "https://finnhub.io")
