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
import de.troetenrepublik.app.market.Rss
import de.troetenrepublik.app.market.SecInsider
import de.troetenrepublik.app.market.SecIpo
import de.troetenrepublik.app.market.IpoKalender
import de.troetenrepublik.app.market.NasdaqKalender
import de.troetenrepublik.app.market.ohneAbbruch
import de.troetenrepublik.app.engine.GleicheFirma
import de.troetenrepublik.app.engine.IpoBewertung
import de.troetenrepublik.app.engine.TrendsErklaerung
import androidx.compose.material3.TextButton
import androidx.compose.foundation.verticalScroll
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
        route == "discover/earnings" -> QuartalszahlenPage(state, onBack = onBack, onOpen = onOpen)
        route == "discover/dividends" -> DividendenPage(state, onBack = onBack, onOpen = onOpen)
        route == "discover/economy" -> WirtschaftPage(state, onBack = onBack)
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
    // Alle Katalogaktien und die groessten Bewegungen des US-Markts laut Yahoo (seit 5.92), je 20 Gewinner und Verlierer
    val list = rememberBewegungen(state)
    val best = remember(list) { list.filter { it.third.changePct > 0 }.sortedByDescending { it.third.changePct }.take(20) }
    val worst = remember(list) { list.filter { it.third.changePct < 0 }.sortedBy { it.third.changePct }.take(20) }
    val liste = rememberLazyListState()
    val dismiss = rememberSwipeDownDismiss(onBack)

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(dismiss) { !liste.canScrollBackward }, state = liste) {
        item { DiscoverTitle(S.topMovers, null, onBack) }
        item { SectionHeader(S.bestPerformers) }
        if (best.isEmpty()) item { EmptyHint(S.loading) }
        items(best, key = { "b-" + it.first }) { (s, n, q) ->
            AssetRow(symbol = s, name = n, change = q.changePct, changeText = Fmt.pct(abs(q.changePct)), onClick = { onOpen(s) })
        }
        item { SectionHeader(S.worstPerformers) }
        if (worst.isEmpty()) item { EmptyHint(S.loading) }
        items(worst, key = { "w-" + it.first }) { (s, n, q) ->
            AssetRow(symbol = s, name = n, change = q.changePct, changeText = Fmt.pct(abs(q.changePct)), onClick = { onOpen(s) })
        }
        item { Spacer(Modifier.height(60.dp)) }
    }
}

// IPOs (5.94 neu, 0.68 Reiter zum Wischen): Pillenleiste mit Anzahl ueber wischbaren Seiten, Termine aus dem
// IPO-Kalender der Nasdaq, mit Finnhub-Schluessel ergaenzt, und je Angebot ein Detailblatt mit Zeitleiste, Eckdaten,
// Einschaetzung, Branche, Diskussion und Prospekten. Der Katalog liefert die handgeschriebenen Texte und die
// bekannten Boersengaenge der letzten Jahre.

private enum class IpoReiter { DEMNAECHST, EINGEREICHT, NEU, ZURUECK, BEKANNT }

// Eingereichte, neu gelistete und zurueckgezogene Angebote der letzten 75 Tage; angekuendigte immer.
private const val IPO_TAGE = 75
private const val IPO_TAG_MS = 86_400_000L

// Gleiche Firma im Kalender und im Katalog: gleicher Namensstamm oder der Katalogname als Anfang („Anthropic, PBC“).
private fun ipoGleich(kalender: String, katalog: String): Boolean {
    val a = GleicheFirma.stamm(kalender)
    val b = GleicheFirma.stamm(katalog)
    return b.length >= 3 && (a == b || a.startsWith("$b "))
}

private fun ipoDollar(v: Double): String =
    if (I18n.lang == Lang.DE) Fmt.compact(v).replace(' ', '\u00A0') + " $" else "$" + Fmt.compact(v).replace(' ', '\u00A0')

// Preis oder Spanne der Quelle („14.00-16.00“) in der Schreibweise der Sprache.
private fun ipoPreisText(preis: String): String? =
    preis.split('-', '–').mapNotNull { IpoKalender.zahl(it) }.takeIf { it.isNotEmpty() }
        ?.joinToString("–") { Fmt.num(it, 2) }?.let { if (I18n.lang == Lang.DE) "$it $" else "$$it" }

// Ein einzelner Preis in der Schreibweise der Sprache.
private fun ipoPreisEinzeln(v: Double): String =
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
            // Grosse Angebote zuerst, damit bekannte Namen nicht zwischen kleinen Mantelgesellschaften verschwinden
            IpoReiter.EINGEREICHT to aktuell.filter { it.status == IpoKalender.Status.EINGEREICHT }.sortedByDescending { it.volumen ?: 0.0 },
            IpoReiter.NEU to aktuell.filter { it.status == IpoKalender.Status.PREIS }.sortedByDescending { it.tag ?: 0L },
            IpoReiter.ZURUECK to aktuell.filter { it.status == IpoKalender.Status.ZURUECK }.sortedByDescending { it.tag ?: 0L },
        )
    }
    // Katalogtexte zu Angeboten im Kalender; geplante ohne Eintrag dort stehen unter „Bekannte“, ohne alten Termin
    val katalogText = remember(angebote, ipos) {
        buildMap { for (k in ipos.bevorstehend) angebote.orEmpty().firstOrNull { ipoGleich(it.name, k.name) }?.let { put(it.deal, k) } }
    }
    val geplant = remember(angebote, ipos) { ipos.bevorstehend.filter { k -> angebote.orEmpty().none { ipoGleich(it.name, k.name) } } }
    val symbols = remember(gruppen, ipos) {
        (gruppen[IpoReiter.NEU].orEmpty().mapNotNull { it.symbol } + ipos.abgeschlossen.map { it.s }).distinct()
    }
    val quotes = rememberSnapshot(state, symbols)
    val dismiss = rememberSwipeDownDismiss(onBack)
    var auswahl by remember { mutableStateOf<IpoKalender.Angebot?>(null) }
    var katalogAuswahl by remember { mutableStateOf<Catalog.IpoUpcoming?>(null) }
    var reiterWahl by rememberSaveable { mutableStateOf<Int?>(null) }
    // Startreiter, solange der Nutzer nichts gewaehlt hat
    val start = when {
        gruppen[IpoReiter.DEMNAECHST].orEmpty().isNotEmpty() -> IpoReiter.DEMNAECHST
        geladen && angebote == null -> IpoReiter.BEKANNT
        else -> IpoReiter.EINGEREICHT
    }
    val de = I18n.lang == Lang.DE
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val mitKontakt = SecInsider.kennung(settings.secKontakt) != null
    val ohneKalender = geladen && angebote == null
    // Ohne Kalender (keine Antwort der Nasdaq, kein Finnhub-Schluessel): die Prospekte aus dem Tagesindex der SEC
    var secListe by remember { mutableStateOf<List<SecIpo.Eintrag>?>(null) }
    LaunchedEffect(mitKontakt, ohneKalender) { secListe = null; if (mitKontakt && ohneKalender) secListe = runCatching { state.secIpos() }.getOrDefault(emptyList()) }

    val pager = rememberPagerState(initialPage = reiterWahl ?: IpoReiter.EINGEREICHT.ordinal) { IpoReiter.entries.size }
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
        MitlaufendeReiter(
            anzahl = IpoReiter.entries.size,
            pager = pager,
            titel = { i ->
                val r = IpoReiter.entries[i]
                val n = if (r == IpoReiter.BEKANNT) geplant.size + ipos.abgeschlossen.size else gruppen[r].orEmpty().size
                // Ohne Kalender keine Zahl: „0“ hiesse, es gaebe keine Boersengaenge
                val zahl = if (r != IpoReiter.BEKANNT && (!geladen || ohneKalender)) "" else " · $n"
                S.ipoReiter(i) + zahl
            },
            modifier = Modifier.padding(vertical = 12.dp),
        ) { i ->
            if (!geladen) nutzerVorLaden = true
            scope.launch { pager.animateScrollToPage(i) }
        }
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().mausWischen(pager)) { seite ->
            IpoReiterSeite(
                r = IpoReiter.entries[seite], liste = listen[seite], geladen = geladen, ohneKalender = ohneKalender,
                gruppe = gruppen[IpoReiter.entries[seite]].orEmpty(), geplant = geplant, ipos = ipos, quotes = quotes,
                secListe = secListe, mitKontakt = mitKontakt, mitFinnhub = settings.keyFinnhub.isNotBlank(),
                onAngebot = { auswahl = it }, onKatalog = { katalogAuswahl = it }, onOpen = onOpen,
            )
        }
    }

    auswahl?.let { a ->
        val kurs = a.symbol?.let { quotes[it] }?.takeIf { it.nativeCurrency.equals("USD", true) }
        IpoDetail(state, a, katalogText[a.deal], mitKontakt, kurs, onOpen = { auswahl = null; onOpen(it) }) { auswahl = null }
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
                if (ipo.quelle.isNotBlank()) {
                    Text("${S.source}: ${ipo.quelle}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
                }
            }
        }
    }
}

// Eine Seite der Boersengaenge: Einleitung, dann der Inhalt des Reiters mit eigener Liste.
@Composable
private fun IpoReiterSeite(
    r: IpoReiter, liste: LazyListState, geladen: Boolean, ohneKalender: Boolean,
    gruppe: List<IpoKalender.Angebot>, geplant: List<Catalog.IpoUpcoming>, ipos: Catalog.Ipos, quotes: Map<String, Quote>,
    secListe: List<SecIpo.Eintrag>?, mitKontakt: Boolean, mitFinnhub: Boolean,
    onAngebot: (IpoKalender.Angebot) -> Unit, onKatalog: (Catalog.IpoUpcoming) -> Unit, onOpen: (String) -> Unit,
) {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    LazyColumn(Modifier.fillMaxSize(), state = liste) {
        item {
            Text(
                S.iposIntro, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
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
                        AuslaufText(e.name, maxLines = 2, style = MaterialTheme.typography.titleMedium)
                        Text(listOf(formText, isoDate(e.tag) ?: e.tag).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            if (gruppe.isEmpty()) item { EmptyHint(S.ipoReiterLeer) }
            items(gruppe, key = { it.deal }, contentType = { "ipo" }) { a ->
                val q = a.symbol?.let { quotes[it] }
                val seit = if (a.status == IpoKalender.Status.PREIS && q != null && q.nativeCurrency.equals("USD", true))
                    IpoBewertung.seitAusgabe(a.preisZahl, q.nativePrice) else null
                IpoZeile(a, seit) { onAngebot(a) }
            }
        }
        if (r != IpoReiter.BEKANNT && !ohneKalender) item {
            QuellenZeile("${S.source}: ${S.nkIpoQuelle(mitFinnhub)}", Modifier.padding(top = 8.dp), onClick = { runCatching { uri.openUri(NasdaqKalender.SEITE_IPO) } })
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

// Eine Zeile: Name und Volumen, darunter Kuerzel, Boerse und Datum bzw. die Entwicklung seit Ausgabe.
@Composable
private fun IpoZeile(a: IpoKalender.Angebot, seit: Double?, onClick: () -> Unit) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val datum = a.tag?.let { S.ipoStatusTag(a.status.ordinal, ipoDatum(it)) }
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AuslaufText(a.name, maxLines = 1, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            a.volumen?.let {
                Spacer(Modifier.width(10.dp))
                Text(ipoDollar(it), style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            val links = listOfNotNull(a.symbol, a.boerse?.let(::boerseKurz), if (a.spac) "SPAC" else null, if (seit != null) datum else null)
            PunktZeile(links, Modifier.weight(1f), color = grau)
            Spacer(Modifier.width(10.dp))
            if (seit != null) Text(
                S.ipoSinceIssue(Fmt.signedPct(seit, 1)), style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false,
                color = if (seit >= 0) LocalSignal.current.up else LocalSignal.current.down,
            ) else if (datum != null) Text(datum, style = MaterialTheme.typography.bodySmall, color = grau, maxLines = 1, softWrap = false)
        }
    }
}

// Drei Schritte nebeneinander: Eingereicht, Preis fest, Handel. Erreichte Schritte gefuellt, das Datum steht beim
// Schritt, zu dem es gehoert. Zurueckgezogen: der zweite Schritt in Rot, der dritte bleibt offen.
@Composable
private fun IpoZeitleiste(zl: IpoBewertung.Zeitleiste, tag: Long?, modifier: Modifier = Modifier) {
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
                if (tag != null && i == zl.datumBei) Text(
                    ipoDatum(tag), style = MaterialTheme.typography.bodySmall, color = grau, maxLines = 1, softWrap = false,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// Eine Kachel der Eckdaten: Titel klein, Wert gross, darunter optional weiterer Inhalt.
@Composable
private fun IpoKachel(
    titel: String, wert: String, modifier: Modifier = Modifier, wertFarbe: Color = Color.Unspecified,
    inhalt: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.clip(RoundedCornerShape(Radius.chip)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(14.dp),
    ) {
        Text(titel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(wert, style = MaterialTheme.typography.titleMedium, color = wertFarbe, maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 2.dp))
        inhalt?.invoke()
    }
}

// Balken der Preisspanne mit Marke an der Lage des Preises, darunter kleinster Preis, Mitte und hoechster Preis.
@Composable
private fun IpoSpannenBalken(von: Double, bis: Double, lage: Double?, modifier: Modifier = Modifier) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val stil = MaterialTheme.typography.bodySmall
    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(12.dp), contentAlignment = Alignment.CenterStart) {
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.outlineVariant))
            if (lage != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(lage.toFloat().coerceAtLeast(0.001f)))
                Box(Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface))
                Spacer(Modifier.weight((1f - lage.toFloat()).coerceAtLeast(0.001f)))
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(ipoPreisEinzeln(von), style = stil, color = grau, maxLines = 1, softWrap = false, modifier = Modifier.align(Alignment.CenterStart))
            Text(S.ipoSpanneMitte, style = stil, color = grau, maxLines = 1, softWrap = false, modifier = Modifier.align(Alignment.Center))
            Text(ipoPreisEinzeln(bis), style = stil, color = grau, maxLines = 1, softWrap = false, modifier = Modifier.align(Alignment.CenterEnd))
        }
    }
}

// Detailblatt eines Angebots. Prospekte nur mit Kontaktadresse: CIK aus der Kuerzelliste der SEC oder ueber den Namen
// aus dem Tagesindex der letzten Tage; sonst ein Link auf die Volltextsuche der SEC. kurs: Kurs in Dollar nach der
// Erstnotiz, sonst null.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IpoDetail(
    state: AppState, a: IpoKalender.Angebot, katalog: Catalog.IpoUpcoming?, mitKontakt: Boolean, kurs: Quote?,
    onOpen: (String) -> Unit, onDismiss: () -> Unit,
) {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    var cik by remember(a.deal) { mutableStateOf<String?>(null) }
    LaunchedEffect(a.deal, mitKontakt) {
        if (mitKontakt) cik = runCatching { state.ipoCik(a.symbol) ?: state.secIpos().firstOrNull { ipoGleich(a.name, it.name) }?.cik }.getOrNull()
    }
    var doks by remember(a.deal) { mutableStateOf<List<IpoKalender.Dokument>?>(null) }
    LaunchedEffect(cik, mitKontakt) { val c = cik; if (c != null && mitKontakt) doks = runCatching { state.ipoDokumente(c) }.getOrNull() }
    var netz by remember(a.deal) { mutableStateOf<de.troetenrepublik.app.market.Ersatzabrufe.Netz?>(null) }
    LaunchedEffect(a.deal) { netz = state.netzErwaehnungen(a.name) }
    // Tageskurse fuer den kleinen Verlauf seit der Erstnotiz
    var verlauf by remember(a.deal) { mutableStateOf<List<Candle>>(emptyList()) }
    LaunchedEffect(a.symbol) {
        val s = a.symbol
        if (s != null && a.status == IpoKalender.Status.PREIS) verlauf = runCatching { state.kurseNativ(s) }.getOrDefault(emptyList())
    }
    val werte = remember(verlauf, a.tag) { IpoBewertung.verlaufAb(verlauf.map { it.t to it.close }, a.tag) }
    val newsStand by state.news.stand.collectAsStateWithLifecycle()
    // Schlagzeilen der App mit dem ersten Wort des Namens (mindestens vier Buchstaben, sonst zu viele Zufallstreffer)
    val wort = remember(a.name) { GleicheFirma.stamm(a.name).split(' ').firstOrNull()?.takeIf { it.length >= 4 } }
    val schlagzeilen = remember(newsStand.eintraege, wort) {
        if (wort == null) emptyList() else {
            val m = Regex("\\b" + Regex.escape(wort) + "\\b", RegexOption.IGNORE_CASE)
            Rss.drittel(newsStand.eintraege.filter { m.containsMatchIn(it.titel) }.distinctBy { it.titel }.take(5))
        }
    }
    var offeneNachricht by remember { mutableStateOf<Rss.Eintrag?>(null) }
    val heute = remember { java.time.LocalDate.now().toEpochDay() }
    val de = I18n.lang == Lang.DE
    val punkte = remember(a) { IpoBewertung.punkte(a) }
    val branche = remember(a) { IpoBewertung.branche(a.name, null) }
    val zeitleiste = remember(a, heute) { IpoBewertung.zeitleiste(a.status, a.tag, heute) }
    val spanne = remember(a) { IpoBewertung.spanne(a.preis)?.takeIf { it.first < it.second } }
    val preisText = a.preis?.let(::ipoPreisText)
    val seit = if (a.status == IpoKalender.Status.PREIS && kurs != null) IpoBewertung.seitAusgabe(a.preisZahl, kurs.nativePrice) else null
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        LazyColumn(Modifier.fillMaxWidth()) {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(a.name, style = MaterialTheme.typography.headlineSmall)
                    PunktZeile(
                        listOfNotNull(a.symbol, a.boerse?.let(::boerseKurz), if (a.spac) "SPAC" else null),
                        Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium,
                    )
                    if (a.status == IpoKalender.Status.PREIS && a.symbol != null) {
                        TextButton(onClick = { onOpen(a.symbol) }, contentPadding = PaddingValues(0.dp)) { Text(S.nwZurAktie) }
                    }
                }
            }
            item { IpoZeitleiste(zeitleiste, a.tag, Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) }
            item { SectionHeader(S.ipoEckdaten) }
            item {
                if (preisText == null && a.aktien == null && a.volumen == null) EmptyHint(S.ipoKeineEckdaten)
                else Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (preisText != null) IpoKachel(
                        if (spanne != null) S.ipoPreisSpanne else S.ipoPreis, preisText, Modifier.fillMaxWidth(),
                    ) {
                        if (spanne != null) IpoSpannenBalken(
                            spanne.first, spanne.second, IpoBewertung.lage(a.preisZahl, spanne.first, spanne.second),
                            Modifier.padding(top = 10.dp),
                        )
                    }
                    if (a.aktien != null || a.volumen != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        a.aktien?.let { IpoKachel(S.ipoAktien, Fmt.compact(it.toDouble()).replace(' ', '\u00A0'), Modifier.weight(1f)) }
                        a.volumen?.let { IpoKachel(S.ipoVolumen, ipoDollar(it), Modifier.weight(1f)) }
                    }
                    val ausgabe = a.preisZahl
                    if (seit != null && kurs != null && ausgabe != null) {
                        val farbe = if (seit >= 0) LocalSignal.current.up else LocalSignal.current.down
                        IpoKachel(S.ipoSeitAusgabeTitel, Fmt.signedPct(seit, 1), Modifier.fillMaxWidth(), farbe) {
                            PunktZeile(
                                listOf(S.ipoKursJetzt(ipoPreisEinzeln(kurs.nativePrice)), S.ipoAusgabe(ipoPreisEinzeln(ausgabe))),
                                Modifier.padding(top = 4.dp),
                            )
                            if (werte.size >= 2) LinesChart(
                                lines = listOf(werte to farbe),
                                modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp),
                                baseline = ausgabe, scrubben = false,
                            )
                        }
                    }
                }
            }
            item { SectionHeader(S.ipoEinschaetzung) }
            items(punkte, key = { "p" + it.merkmal.name }) { p ->
                val wert = when (p.merkmal) {
                    IpoBewertung.Merkmal.GROESSE -> p.wert?.let(::ipoDollar)
                    IpoBewertung.Merkmal.SPAC -> null
                }
                // Ampel mit kurzer Beschriftung, darunter die Erklaerung
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AmpelPunkt(fwAmpelZeichen(p.ampel), fwAmpelFarbe(p.ampel))
                        Spacer(Modifier.width(10.dp))
                        Text(S.ipoPunktKurz(p.merkmal.ordinal), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        if (wert != null) {
                            Spacer(Modifier.width(10.dp))
                            Text(wert, style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false)
                        }
                    }
                    Text(
                        S.ipoPunktErklaerung(p.merkmal.ordinal), style = MaterialTheme.typography.bodySmall, color = grau,
                        modifier = Modifier.padding(start = 48.dp, end = 20.dp, bottom = 10.dp),
                    )
                }
            }
            item { Box(Modifier.padding(vertical = 6.dp)) { Text(S.ipoRitter, style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp)) } }
            branche?.let { b ->
                item { SectionHeader(S.ipoBrancheTitel) }
                item { Text(S.ipoBranche(b.ordinal), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 20.dp)) }
                item { Box(Modifier.padding(top = 4.dp)) { Text(S.ipoBrancheText(b.ordinal), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp)) } }
            }
            val eigen = katalog?.let { if (de) it.textDe else it.textEn }?.takeIf { it.isNotBlank() }
            if (eigen != null) {
                item { SectionHeader(S.ipoUeber) }
                item { Text(eigen, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp)) }
                katalog?.quelle?.takeIf { it.isNotBlank() }?.let { q ->
                    item { Text("${S.source}: $q", style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 6.dp)) }
                }
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
            if (schlagzeilen.isEmpty()) {
                item { Text(S.ipoKeineSchlagzeilen, style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(horizontal = 20.dp)) }
            } else schlagzeilen.forEachIndexed { i, n ->
                item(key = "n" + n.link + n.titel) {
                    Column {
                        if (i > 0) Trennlinie()
                        Column(Modifier.fillMaxWidth().clickable { offeneNachricht = n }.padding(horizontal = 20.dp, vertical = 10.dp)) {
                            AuslaufText(n.titel, maxLines = 3, style = MaterialTheme.typography.bodyMedium, flaeche = MaterialTheme.colorScheme.surfaceContainer)
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
                    else -> d.take(12).forEachIndexed { i, dok ->
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
            item { QuellenZeile("${S.source}: ${S.nkIpoQuelle(state.settingsFlow.value.keyFinnhub.isNotBlank())}", Modifier.padding(top = 8.dp), onClick = { runCatching { uri.openUri(NasdaqKalender.SEITE_IPO) } }) }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
    offeneNachricht?.let { NachrichtBlatt(state, it, onDismiss = { offeneNachricht = null }, onOpen = onOpen) }
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
