package de.troetenrepublik.app.ui

import de.troetenrepublik.app.plattform.mausWischen
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import de.troetenrepublik.app.plattform.horizontalScrollMaus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.MITTEILUNG_ALLE
import de.troetenrepublik.app.data.MITTEILUNG_AUS
import de.troetenrepublik.app.data.MITTEILUNG_EIGENE
import de.troetenrepublik.app.data.MITTEILUNG_EIGENE_WICHTIGE
import de.troetenrepublik.app.data.MITTEILUNG_QUELLEN
import de.troetenrepublik.app.data.MITTEILUNG_WICHTIGE
import de.troetenrepublik.app.engine.EigenerFeed
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.i18n.WichtigTexte
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.Rss
import de.troetenrepublik.app.market.NachrichtenApis
import de.troetenrepublik.app.market.Symbols
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// Schlagzeilen aus freien Feeds. Die Reiter sind die Anbieter, man wischt zwischen ihnen. Anbieter mit mehreren
// Feeds haben Unterkategorien. Die Reiter nennen die Zahl neuer Schlagzeilen seit dem letzten Besuch. Wichtige
// Meldungen (Rss.wichtige) tragen links eine Markierung und ihre Uhrzeit. Die Seite laedt beim Oeffnen neu und danach
// jede Minute. Nachrichten zu einzelnen Werten stehen auf deren Wertpapierseite.
@Composable
fun NewsScreen(
    state: AppState, onBack: () -> Unit, onZugaenge: () -> Unit = {}, onRueckblick: () -> Unit = {},
    // Seite einer Aktie aus der Einordnung (5.94)
    onOpen: (String) -> Unit = {},
    // Aus einer Mitteilung: diese Schlagzeile gleich oeffnen (WidgetZiel.nachrichtKennung)
    startKennung: String? = null,
    onMitteilungen: () -> Unit = {},
    // Verweise auf andere Seiten (Reiter „Trump“: Truth-Seite, Praesidialdokumente)
    oeffnen: (String) -> Unit = {},
) {
    val stand by state.news.stand.collectAsStateWithLifecycle()
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val signal = LocalSignal.current

    // Was vor diesem Besuch ungelesen war, behaelt seinen Punkt, bis die Seite geschlossen wird.
    val gelesenBis = remember { settings.newsGelesenBis }
    var suche by rememberSaveable { mutableStateOf<String?>(null) }
    var quellenBlatt by remember { mutableStateOf(false) }
    // Blatt eines eigenen Feeds: null zu, "" neu anlegen, sonst Kennung (5.96)
    var feedEditor by remember { mutableStateOf<String?>(null) }
    var offen by remember { mutableStateOf<Rss.Eintrag?>(null) }
    // Schlagzeile aus der Mitteilung oeffnen, sobald sie geladen ist (einmal)
    var startErledigt by rememberSaveable(startKennung) { mutableStateOf(startKennung == null) }
    LaunchedEffect(stand.eintraege, startKennung) {
        if (startErledigt) return@LaunchedEffect
        stand.eintraege.firstOrNull { de.troetenrepublik.app.data.WidgetZiel.nachrichtKennung(it.id) == startKennung }?.let {
            offen = it
            startErledigt = true
        }
    }
    val gemerkt = remember(settings.newsGemerkt) { state.newsGemerkt(settings) }
    val gemerktIds = remember(gemerkt) { gemerkt.mapTo(HashSet()) { it.id } }
    val quellen = remember(settings.newsAus, settings.newsEigene, settings.sprache, settings.newsFeedKategorien, settings.newsNurEnglisch) { state.newsQuellen(settings) }
    val feeds = remember(settings.newsFeedKategorien) { state.newsFeeds(settings) }
    // Zugaenge der eigenen Feeds bilden keinen Anbieter-Reiter
    val tabQuellen = remember(quellen) { quellen.filterNot { EigenerFeed.istZugang(it) } }

    LaunchedEffect(Unit) {
        state.newsGelesen()
        runCatching { state.newsLaden(neu = stand.geladenAm < System.currentTimeMillis() - 60_000L) }
        while (true) {
            delay(60_000L)
            runCatching { state.newsLaden() }
        }
    }

    // Anbieter mit mindestens einem eingeschalteten Feed, „Gemerkt“ nur mit Gemerktem. Nach den eigenen Feeds
    // „Wichtigste“, „Depot“, „Alle“ und „Gemerkt“, danach die Anbieter (5.95).
    val vorne = listOf(Rss.Anbieter.WICHTIG, Rss.Anbieter.DEPOT, Rss.Anbieter.ALLE, Rss.Anbieter.GEMERKT)
    val reiter = (vorne + Rss.Anbieter.entries.filter { it !in vorne }).filter { a ->
        when (a) {
            Rss.Anbieter.ALLE, Rss.Anbieter.WICHTIG, Rss.Anbieter.DEPOT -> true
            Rss.Anbieter.GEMERKT -> gemerkt.isNotEmpty()
            // Dienste mit eigenem Schluessel stehen immer da. Ohne Schluessel zeigt der Reiter, wie man ihn eintraegt.
            Rss.Anbieter.GUARDIAN, Rss.Anbieter.FINNHUB, Rss.Anbieter.ALPHAVANTAGE -> true
            else -> tabQuellen.any { it.anbieter == a }
        }
    }
    var gewaehlt by rememberSaveable { mutableStateOf(Rss.Anbieter.WICHTIG.name) }
    // Eigene Feeds stehen als eigene Reiter ganz vorne, in der Reihenfolge des Anlegens (5.97); geoeffnet wird der gewaehlte Reiter, anfangs „Wichtigste“
    val gesamt = reiter.size + feeds.size
    val name: (Int) -> String = { i -> feeds.getOrNull(i)?.let { "feed:" + it.kennung } ?: reiter.getOrNull(i - feeds.size)?.name ?: i.toString() }
    val pager = rememberPagerState(initialPage = (0 until gesamt).firstOrNull { name(it) == gewaehlt } ?: 0) { gesamt }
    // Der gewaehlte Reiter bleibt, auch wenn Reiter dazukommen oder wegfallen.
    // Der Sammler liest immer die aktuelle Reiterliste, auch nach Anlegen oder Loeschen eines eigenen Feeds.
    val namen = remember(reiter, feeds) { List(gesamt) { name(it) } }
    val namenAktuell = androidx.compose.runtime.rememberUpdatedState(namen)
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { i -> namenAktuell.value.getOrNull(i)?.let { gewaehlt = it } } }
    LaunchedEffect(namen) {
        val i = namen.indexOf(gewaehlt).coerceAtLeast(0)
        if (i != pager.currentPage) pager.scrollToPage(i)
    }
    // null = alle Feeds des Anbieters
    val unterWahl = remember { mutableStateMapOf<Rss.Anbieter, String>() }

    val zone = remember { ZoneId.systemDefault() }
    val heute = LocalDate.now(zone)
    // Wichtige Schlagzeilen: Punkte je Ereignis, Zuschlag fuer Werte aus Depot und Merkliste.
    val konto by state.account.collectAsStateWithLifecycle()
    val depot = remember(konto.positions, konto.watchlist) {
        de.troetenrepublik.app.market.Wichtig.depotBegriffe(
            konto.positions.map { it.name } + konto.watchlist.mapNotNull { de.troetenrepublik.app.market.Symbols.find(it)?.name },
            konto.positions.map { it.symbol } + konto.watchlist,
        )
    }
    var wichtige by remember { mutableStateOf<Set<String>>(emptySet()) }
    // Themen des Reiters „Wichtigste“: je Geschichte eine Zeile, die Kategorien im Wechsel (engine/WichtigThemen)
    var themen by remember { mutableStateOf<List<de.troetenrepublik.app.engine.WichtigThemen.Thema>>(emptyList()) }
    // Erst nach der ersten Auswahl zeigt der Reiter „Wichtigste“ einen leeren Hinweis, vorher „Lädt …“
    var wichtigeFertig by remember { mutableStateOf(false) }
    val nurEnglisch = settings.wichtigNurEnglisch
    // Unterkategorie „Durchbrüche“ im Reiter „Wichtigste“: Meldungen der Themen aus Maerkte, Trends, eingeklappt
    var durchbruchMeldungen by remember { mutableStateOf(state.durchbruecheStand()) }
    var durchbruchOffen by rememberSaveable { mutableStateOf(false) }
    var durchbruchGruppe by rememberSaveable { mutableStateOf<String?>(null) }
    val deutschAbruf = settings.sprache == de.troetenrepublik.app.data.Lang.DE
    LaunchedEffect(deutschAbruf) {
        de.troetenrepublik.app.market.ohneAbbruch { state.durchbrueche() }.getOrNull()?.let { durchbruchMeldungen = it }
    }
    val durchbruchTeil = remember(durchbruchMeldungen) { de.troetenrepublik.app.engine.WichtigThemen.durchbrueche(durchbruchMeldungen, System.currentTimeMillis()) }
    LaunchedEffect(stand.eintraege, depot, nurEnglisch) {
        val neu = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            de.troetenrepublik.app.engine.WichtigThemen.themen(stand.eintraege, depot, nurEnglisch)
        }
        themen = neu
        wichtige = neu.mapTo(HashSet()) { it.kopf.id }
        if (stand.eintraege.isNotEmpty()) wichtigeFertig = true
    }
    // Eintraege je eigenem Feed aus allen Schlagzeilen (5.96)
    val feedEintraege = remember(stand.eintraege, feeds, quellen) { feeds.associate { it.kennung to EigenerFeed.eintraege(stand.eintraege, it, quellen) } }
    // Jeder Reiter hat seine eigene Liste. Nach unten wegwischen, wenn die Liste oben steht.
    val listen = remember(gesamt) { List(gesamt) { LazyListState() } }
    val dismiss = rememberSwipeDownDismiss(onBack)
    // Art der Schlagzeile und Mindestbewegung der genannten Aktie (q78f)
    var artWahl by rememberSaveable { mutableStateOf<String?>(null) }
    var kursAb by rememberSaveable { mutableStateOf(0) }
    // Reiter „Depot“ (0.77): Nachrichten je Wert aus dem Depot, erst geladen, wenn der Reiter offen ist
    val depotPositionen = remember(konto.positions) { konto.positions.filter { !it.isDerivative && !it.isKnockedOut } }
    val depotNur = remember(depotPositionen) {
        de.troetenrepublik.app.market.Wichtig.depotBegriffe(depotPositionen.map { it.name }, depotPositionen.map { it.symbol })
    }
    var depotJeWert by remember { mutableStateOf<List<Rss.Eintrag>>(emptyList()) }
    var depotGeladen by remember { mutableStateOf(false) }
    val depotOffen = namen.getOrNull(pager.currentPage) == Rss.Anbieter.DEPOT.name
    LaunchedEffect(depotOffen, depotPositionen) {
        if (!depotOffen) return@LaunchedEffect
        depotJeWert = if (depotPositionen.isEmpty()) emptyList() else runCatching { state.depotNachrichten() }.getOrDefault(emptyList())
        depotGeladen = true
    }
    val depotStufenSymbole = remember(depotPositionen) {
        depotPositionen.map { it.symbol }.distinct().filter { Symbols.typeOf(it) == de.troetenrepublik.app.data.AssetType.STOCK }.take(30)
    }
    // Aeltere Schlagzeilen der Arten Zahlen, Analyst, Uebernahme und Kapitalmassnahme (0.77), je Art einmal geholt
    val archivArt = remember(artWahl) {
        artWahl?.let { runCatching { de.troetenrepublik.app.engine.NachrichtArt.Art.valueOf(it) }.getOrNull() }
            ?.takeIf { it in de.troetenrepublik.app.engine.NachrichtenListe.ARCHIV_ARTEN }
    }
    var archiv by remember { mutableStateOf<Map<de.troetenrepublik.app.engine.NachrichtArt.Art, List<Rss.Eintrag>>>(emptyMap()) }
    LaunchedEffect(archivArt, settings.sprache) {
        val a = archivArt ?: return@LaunchedEffect
        archiv = archiv + (a to runCatching { state.newsArchiv(a) }.getOrDefault(emptyList()))
    }
    val archivLaedt = archivArt != null && archivArt !in archiv
    val archivListe = archivArt?.let { archiv[it] }.orEmpty()
    val infos = rememberNewsInfos(stand.eintraege, remember(archivListe, depotJeWert) { archivListe + depotJeWert })
    val infoKurse = rememberSnapshot(state, remember(infos) { infos.values.mapNotNull { it.symbol }.distinct() })

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .sheetDismiss(dismiss) { listen.getOrNull(pager.currentPage)?.canScrollBackward != true },
    ) {
        ScreenHeader(S.news, null, onBack, trailing = {
            // Eigenen Feed anlegen (5.96)
            IconButton(onClick = { feedEditor = "" }) { Icon(Icons.Filled.Add, S.feedErstellen) }
            // Gesammelte Tages- und Wochenrueckblicke.
            IconButton(onClick = onRueckblick) { Icon(ZeitungIcon, S.rbTitel) }
            IconButton(onClick = { suche = if (suche == null) "" else null }) {
                Icon(if (suche == null) Icons.Filled.Search else Icons.Filled.Close, S.newsSearch)
            }
            IconButton(onClick = { scope.launch { runCatching { state.newsLaden(neu = true) } } }) {
                if (stand.laedt) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.Refresh, S.refreshNow)
            }
            IconButton(onClick = { quellenBlatt = true }) { Icon(Icons.Filled.Tune, S.newsSources) }
        })
        // Gespeicherter Stand vom letzten Start: seine Zeit steht dort, bis der Abruf neue Schlagzeilen bringt oder
        // ausbleibt. Die Zeile hat immer dieselbe Hoehe, damit nichts springt.
        Text(
            if (stand.gespeichert) standText(stand.geladenAm) else "",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp).height(16.dp),
        )
        if (suche != null) {
            OutlinedTextField(
                value = suche.orEmpty(),
                onValueChange = { suche = it.take(60) },
                placeholder = { Text(S.newsSearchHint) },
                singleLine = true,
                shape = RoundedCornerShape(Radius.field),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }
        // Langes Druecken auf einen Reiter legt das News-Widget mit diesem Inhalt an (nur Android)
        val wm = rememberReiterWidgetMenue(state) { i ->
            val feed = feeds.getOrNull(i)
            if (feed != null) de.troetenrepublik.app.data.WidgetVorgabe.news(null, feed.kennung)
            else reiter.getOrNull(i - feeds.size)?.let { de.troetenrepublik.app.data.WidgetVorgabe.news(it.name) }
        }
        MitlaufendeReiter(
            anzahl = gesamt,
            pager = pager,
            langDruecken = wm.langDruecken,
            menue = { i -> wm.menue(i) },
            titel = { i -> feeds.getOrNull(i)?.name ?: reiter.getOrNull(i - feeds.size)?.let { S.newsAnbieter(it) } ?: "" },
            modifier = Modifier.padding(vertical = 12.dp),
            zusatz = { i ->
                val neu = if (i < feeds.size) {
                    feeds.getOrNull(i)?.let { feedEintraege[it.kennung] }.orEmpty().count { it.zeit > gelesenBis }
                } else {
                    val a = reiter[i - feeds.size]
                    when (a) {
                        Rss.Anbieter.GEMERKT -> 0
                        Rss.Anbieter.WICHTIG -> stand.eintraege.count { it.id in wichtige && it.zeit > gelesenBis }
                        Rss.Anbieter.DEPOT -> 0
                        else -> Rss.filter(stand.eintraege, a, tabQuellen).count { it.zeit > gelesenBis }
                    }
                }
                neu.takeIf { it > 0 && gelesenBis > 0 }?.toString()
            },
        ) { i -> scope.launch { pager.animateScrollToPage(i) } }
        NewsArtenLeiste(
            artWahl?.let { runCatching { de.troetenrepublik.app.engine.NachrichtArt.Art.valueOf(it) }.getOrNull() }, kursAb,
            onArt = { artWahl = it?.name }, onKurs = { kursAb = it },
        )
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().mausWischen(pager), beyondViewportPageCount = 1, key = { name(it) }) { seite ->
            val feed = feeds.getOrNull(seite)
            val anbieter: Rss.Anbieter? = if (feed != null) null else reiter.getOrElse(seite - feeds.size) { Rss.Anbieter.ALLE }
            val unterkategorien = remember(anbieter, tabQuellen) {
                if (anbieter == null || anbieter == Rss.Anbieter.ALLE || anbieter == Rss.Anbieter.WICHTIG || anbieter == Rss.Anbieter.DEPOT || anbieter == Rss.Anbieter.GEMERKT) emptyList() else Rss.unterkategorien(anbieter, tabQuellen)
            }
            val unter = anbieter?.let { a -> unterWahl[a]?.takeIf { u -> unterkategorien.any { it.unter == u } } }
            val basis = when {
                feed != null -> feedEintraege[feed.kennung].orEmpty()
                anbieter == Rss.Anbieter.GEMERKT -> gemerkt
                // Nur die wichtigsten (5.94): die markierten Schlagzeilen aus „Alle“
                anbieter == Rss.Anbieter.WICHTIG -> Rss.filter(stand.eintraege, Rss.Anbieter.ALLE, tabQuellen).filter { it.id in wichtige }
                // Depot: Schlagzeilen, die einen Depotwert nennen, dazu die Nachrichten je Wert und die passenden aelteren
                anbieter == Rss.Anbieter.DEPOT -> remember(stand.eintraege, depotJeWert, depotNur, archivListe) {
                    de.troetenrepublik.app.engine.NachrichtenListe.depotListe(
                        stand.eintraege,
                        depotJeWert + archivListe.filter { de.troetenrepublik.app.engine.NachrichtenListe.imDepot(it.titel, depotNur) },
                        depotNur, System.currentTimeMillis(),
                    )
                }
                // Alle mit den aelteren Schlagzeilen der gewaehlten Art
                anbieter == Rss.Anbieter.ALLE && archivListe.isNotEmpty() -> remember(stand.eintraege, archivListe, tabQuellen) {
                    Rss.zusammen(
                        listOf(Rss.filter(stand.eintraege, Rss.Anbieter.ALLE, tabQuellen), archivListe),
                        System.currentTimeMillis(), de.troetenrepublik.app.engine.NachrichtenListe.ARCHIV_TAGE,
                    )
                }
                else -> Rss.filter(stand.eintraege, anbieter ?: Rss.Anbieter.ALLE, tabQuellen, unter)
            }
            // 5.95: derStandard nimmt hoechstens ein Drittel jeder Ansicht ein, die Grenze greift nach der Suche (Rss.drittel)
            val gezeigtRoh = remember(basis, suche) { Rss.drittel(Rss.suche(basis, suche.orEmpty())) }
            val gezeigt = remember(gezeigtRoh, artWahl, kursAb, infos, infoKurse) {
                newsFilter(gezeigtRoh, artWahl?.let { runCatching { de.troetenrepublik.app.engine.NachrichtArt.Art.valueOf(it) }.getOrNull() }, kursAb, infos, infoKurse)
            }
            val nachTag = remember(gezeigt) { gezeigt.groupBy { e -> if (e.zeit > 0) Instant.ofEpochMilli(e.zeit).atZone(zone).toLocalDate() else null } }
            // „Wichtigste“: die gezeigten Schlagzeilen als Themen, in der Reihenfolge der Kategorien im Wechsel
            val themenZeilen = remember(themen, gezeigt, anbieter) {
                if (anbieter != Rss.Anbieter.WICHTIG) emptyList()
                else gezeigt.mapTo(HashSet()) { it.id }.let { ids -> themen.filter { it.kopf.id in ids } }
            }
            val deutschSeite = settings.sprache == de.troetenrepublik.app.data.Lang.DE
            LazyColumn(Modifier.fillMaxSize(), state = listen.getOrElse(seite) { rememberLazyListState() }) {
                // Kopf des eigenen Feeds: Quellennamen und Stift zum Bearbeiten (5.96)
                if (feed != null) {
                    item(key = "feed-kopf") {
                        FeedKopf(feed, quellen, settings.sprache == de.troetenrepublik.app.data.Lang.DE) { feedEditor = feed.kennung }
                    }
                }
                // Nur wenn der Anbieter mehr als einen Feed hat. Eigene Feeds haben keine Unterkategorien.
                if (unterkategorien.size > 1 && anbieter != null) {
                    item(key = "unter") {
                        Row(
                            Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Chip(S.all, active = unter == null) { unterWahl.remove(anbieter) }
                            unterkategorien.forEach { q ->
                                Chip(unterName(q), active = unter == q.unter) { unterWahl[anbieter] = q.unter }
                            }
                        }
                    }
                }
                // Dienste mit eigenem Schluessel: Hinweis ohne Schluessel, Meldung des Anbieters, Verbrauch.
                val dienst = when (anbieter) {
                    Rss.Anbieter.GUARDIAN -> Rss.Art.GUARDIAN
                    Rss.Anbieter.FINNHUB -> Rss.Art.FINNHUB
                    Rss.Anbieter.ALPHAVANTAGE -> Rss.Art.ALPHAVANTAGE
                    else -> null
                }
                if (dienst != null && anbieter != null) {
                    val mitSchluessel = tabQuellen.any { it.anbieter == anbieter }
                    item(key = "dienst") {
                        DienstHinweis(state, dienst, S.newsAnbieter(anbieter), mitSchluessel, stand.meldungen[dienst.gruppe], onZugaenge)
                    }
                    if (!mitSchluessel) return@LazyColumn
                }
                if (anbieter == Rss.Anbieter.DEPOT && depotStufenSymbole.isNotEmpty()) {
                    item(key = "depot-stufen") { DepotStufen(state, depotStufenSymbole, onOpen) }
                }
                if (anbieter == Rss.Anbieter.TRUMP) {
                    item(key = "trump-verweise") { TrumpVerweise(oeffnen) }
                    // Wo seine Beitraege stehen: Erklaerung hinter dem Info-Symbol
                    item(key = "trump-hinweis") {
                        var info by remember { mutableStateOf(false) }
                        Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.End) {
                            IconButton(onClick = { info = true }) {
                                Icon(Icons.Outlined.Info, S.infoErklaerung, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            }
                        }
                        if (info) MarktInfoBlatt(S.newsAnbieter(Rss.Anbieter.TRUMP), S.newsTrumpHinweis) { info = false }
                    }
                }
                // Durchbrüche als Unterkategorie von „Wichtigste“, nur ohne Suche und ohne Filter der Schlagzeilen
                if (anbieter == Rss.Anbieter.WICHTIG && durchbruchTeil != null && suche.isNullOrBlank() && artWahl == null && kursAb == 0) {
                    wichtigDurchbrueche(
                        durchbruchTeil, deutschSeite, System.currentTimeMillis(), durchbruchOffen, durchbruchGruppe,
                        onOffen = { durchbruchOffen = !durchbruchOffen },
                        onGruppe = { g -> durchbruchGruppe = if (durchbruchGruppe == g) null else g },
                        onArtikel = { offen = it },
                        onThema = { t -> oeffnen(de.troetenrepublik.app.engine.WichtigThemen.wegZumThema(t)) },
                    )
                }
                if (gezeigt.isEmpty()) {
                    item {
                        EmptyHint(
                            when {
                                feed != null && stand.geladenAm == 0L -> S.newsLoading
                                feed != null && !suche.isNullOrBlank() -> S.newsEmptySearch
                                feed != null -> S.feedLeer
                                anbieter == Rss.Anbieter.GEMERKT -> S.newsEmptySaved
                                stand.geladenAm == 0L -> S.newsLoading
                                anbieter == Rss.Anbieter.WICHTIG && !wichtigeFertig -> S.newsLoading
                                anbieter == Rss.Anbieter.DEPOT && depotPositionen.isEmpty() -> S.newsDepotLeer
                                anbieter == Rss.Anbieter.DEPOT && !depotGeladen -> S.newsLoading
                                archivLaedt && (anbieter == Rss.Anbieter.ALLE || anbieter == Rss.Anbieter.DEPOT) -> S.newsLoading
                                !suche.isNullOrBlank() -> S.newsEmptySearch
                                else -> S.newsEmpty
                            },
                        )
                    }
                }
                if (anbieter == Rss.Anbieter.WICHTIG) {
                    itemsIndexed(themenZeilen, key = { _, th -> "t-${th.kopf.id}" }, contentType = { _, _ -> "thema" }) { i, th ->
                        if (i > 0) Trennlinie()
                        NewsZeile(
                            th.kopf,
                            art = infos[th.kopf.id]?.arten?.firstOrNull(),
                            artSymbol = infos[th.kopf.id]?.symbol,
                            artAenderung = infos[th.kopf.id]?.symbol?.let { s -> infoKurse[s]?.heutePct(System.currentTimeMillis()) },
                            wichtig = true,
                            gemerkt = th.kopf.id in gemerktIds,
                            linkFarbe = signal.link,
                            onMerken = { state.newsMerken(th.kopf) },
                            // Farbige Kategorie nur auf Wunsch (Einstellung, 0.79); Standard schlicht wie vorher
                            etikett = if (settings.newsWichtigFarbig) WichtigTexte.kat(deutschSeite, th.kat) to de.troetenrepublik.app.ui.Kategorie.farbe(th.kat.ordinal) else null,
                            mitDatum = true,
                        ) { offen = th.kopf }
                    }
                }
                if (anbieter != Rss.Anbieter.WICHTIG) nachTag.forEach { (tag, eintraege) ->
                    item(key = "tag-$tag") {
                        Text(
                            when {
                                tag == null -> "–"
                                tag == heute -> S.today
                                tag == heute.minusDays(1) -> S.newsYesterday
                                else -> Fmt.date(tag.atStartOfDay(zone).toInstant().toEpochMilli())
                            },
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 18.dp, bottom = 4.dp),
                        )
                    }
                    // Duenne Linie zwischen den Nachrichten eines Tages (5.92)
                    itemsIndexed(eintraege, key = { _, it -> "n-${it.id}" }) { i, e ->
                        if (i > 0) Trennlinie()
                        val info = infos[e.id]
                        NewsZeile(
                            e,
                            art = info?.arten?.firstOrNull(),
                            artSymbol = info?.symbol,
                            artAenderung = info?.symbol?.let { s -> infoKurse[s]?.heutePct(System.currentTimeMillis()) },
                            wichtig = e.id in wichtige,
                            gemerkt = e.id in gemerktIds,
                            linkFarbe = signal.link,
                            onMerken = { state.newsMerken(e) },
                        ) { offen = e }
                    }
                }
                if (archivLaedt && gezeigt.isNotEmpty() && (anbieter == Rss.Anbieter.ALLE || anbieter == Rss.Anbieter.DEPOT)) {
                    item(key = "archiv-laedt") {
                        Text(
                            S.newsLoading, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        )
                    }
                }
                // Logo des Guardian (Bedingung der Open Platform: auf derselben Seite wie dessen Inhalte), klein ganz am Ende
                if (gezeigt.any { NachrichtenApis.istGuardian(it.link) }) {
                    item(key = "guardian-logo") { GuardianLogo(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) }
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }

    offen?.let { e -> NachrichtBlatt(state, e, onOpen, symbole = listOfNotNull(infos[e.id]?.symbol)) { offen = null } }
    // Mitteilungen aus dem Blatt: das Blatt wartet unter der Seite und steht nach dem Zurueck wieder da
    if (quellenBlatt) WiederkehrBlatt("quellen") { w -> QuellenBlatt(state, onMitteilungen = { w.verlassen(); onMitteilungen() }, onFeedNeu = { quellenBlatt = false; feedEditor = "" }, onDismiss = { quellenBlatt = false }) }
    // Nach dem Speichern springt die Seite ueber LaunchedEffect(namen) zum neuen Reiter (5.96)
    feedEditor?.let { k ->
        NewsFeedBlatt(state, feeds.firstOrNull { it.kennung == k }, onGespeichert = { kennung -> gewaehlt = "feed:$kennung" }, onDismiss = { feedEditor = null })
    }
}

// Unter einer Nachricht (5.94): fuer welche Aktie oder welchen Markt sie eher bullish oder bearish ist, eine genannte
// Aktie zum Antippen. Ohne klares Signal nichts.
@Composable
private fun einordnungFuss(titel: String, onOpen: (String) -> Unit): (@Composable () -> Unit)? {
    val katalog = remember { de.troetenrepublik.app.market.Catalog.get().aktien.map { it.s to it.n } }
    val liste = remember(titel, katalog) { de.troetenrepublik.app.engine.NachrichtEinordnung.einordnen(titel, katalog) }
    if (liste.isEmpty()) return null
    return {
        Column(Modifier.fillMaxWidth()) {
            Text(S.nwEinordnung, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            liste.forEach { e ->
                Row(
                    Modifier.fillMaxWidth().then(if (e.symbol != null) Modifier.clickable { onOpen(e.symbol) } else Modifier).padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TendenzZeile(e.lage, Modifier.weight(1f))
                    if (e.symbol != null) {
                        Text(S.nwZurAktie, style = MaterialTheme.typography.labelMedium, color = LocalSignal.current.link)
                        Spacer(Modifier.width(6.dp))
                        ChevronRight()
                    }
                }
            }
        }
    }
}

// Ohne Schluessel Erklaerung und Weg zu den Zugaengen, sonst Meldungen des Anbieters und beim Guardian der Pflichthinweis.
@Composable
private fun DienstHinweis(state: AppState, dienst: Rss.Art, name: String, mitSchluessel: Boolean, meldung: String?, onZugaenge: () -> Unit) {
    Column(
        Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
    ) {
        if (!mitSchluessel) {
            Text(S.newsKeyMissing(name), style = MaterialTheme.typography.bodyMedium)
            AufklappText(S.zgTerms(dienst.gruppe), Modifier.padding(top = 6.dp), flaeche = MaterialTheme.colorScheme.surfaceContainer)
            Text(
                S.newsKeyEnter, style = MaterialTheme.typography.labelLarge, color = LocalSignal.current.link,
                modifier = Modifier.padding(top = 8.dp).textKnopf(onClick = onZugaenge),
            )
        } else {
            // Ohne Abrufzaehler. Der Hinweis zum Guardian ist Pflicht.
            if (dienst == Rss.Art.GUARDIAN) Text(S.newsGuardianNote, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (meldung != null) Text(dienstMeldung(meldung), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp))
        }
    }
}

// „Powered by The Guardian“ ist neben Inhalten der Content API vorgeschrieben.
@Composable
fun GuardianLogo(modifier: Modifier = Modifier) {
    val dunkel = MaterialTheme.colorScheme.background.luminance() < 0.5f
    if (Vorfuehrung.aktiv) {
        Text("Powered by The Guardian", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
        return
    }
    de.troetenrepublik.app.plattform.LogoBild(
        if (dunkel) NachrichtenApis.GUARDIAN_LOGO_DUNKEL else NachrichtenApis.GUARDIAN_LOGO_HELL, "Powered by The Guardian",
        modifier.height(24.dp).width(140.dp), androidx.compose.ui.layout.ContentScale.Fit, onSuccess = {}, onError = {},
    )
}

// Blatt zu einer Schlagzeile wie auf der Nachrichtenseite (Guardian, Truth Social, Volltext aus dem Feed, sonst die Seite); auch fuer den Tagesrueckblick.
@Composable
internal fun NachrichtBlatt(state: AppState, e: Rss.Eintrag, onOpen: (String) -> Unit, symbole: List<String> = emptyList(), ohneSymbol: String? = null, onDismiss: () -> Unit) {
    // Wertpapierseite aus dem Blatt: das Blatt wartet unter der Seite und steht nach dem Zurueck wieder mit demselben Artikel da
    WiederkehrBlatt("nachricht-" + e.id) { rueck -> NachrichtBlattInnen(state, e, onOpen, symbole, ohneSymbol, rueck, onDismiss) }
}

@Composable
private fun NachrichtBlattInnen(state: AppState, e: Rss.Eintrag, onOpen: (String) -> Unit, symbole: List<String>, ohneSymbol: String?, rueck: Wiederkehr, onDismiss: () -> Unit) {
    val schliessen = onDismiss
    @Suppress("NAME_SHADOWING")
    val onDismiss: () -> Unit = { rueck.vergessen(); schliessen() }
    val zurAktie: (String) -> Unit = { s -> rueck.verlassen(); onOpen(s) }
    if (NachrichtenApis.istGuardian(e.link)) GuardianBlatt(state, e, symbole, zurAktie) { onDismiss() }
    else if (Rss.Kategorie.TRUMP in e.kategorien) NewsSheet(
        MarketApi.NewsItem(title = e.titel, link = e.link, published = e.zeit, summary = "", source = e.quellenText),
        onDismiss = { onDismiss() },
        symbole = symbole, onOpenSymbol = zurAktie, ohneSymbol = ohneSymbol,
        text = e.zusammenfassung.lines().filter { it.isNotBlank() },
        fuss = if (e.themen.isNotEmpty()) { { Text(S.truthRelevant(e.themen.map { S.truthThema(it) }), style = MaterialTheme.typography.labelMedium, color = LocalSignal.current.warn) } } else null,
    )
    else NewsSheet(
        MarketApi.NewsItem(title = e.titel, link = e.link, published = e.zeit, summary = e.zusammenfassung, source = e.quellenText, summaryGekuerzt = e.zusammenfassungGekuerzt),
        onDismiss = { onDismiss() },
        symbole = symbole, onOpenSymbol = zurAktie, ohneSymbol = ohneSymbol,
        // Ganzer Beitrag aus dem Feed: ohne Seitenabruf
        text = e.absaetze.takeIf { it.isNotEmpty() },
        ohneText = if (Rss.nurImBrowser(e.link)) S.newsBrowserOnly else null,
        // Artikel der STANDARD-Quelle: Zeile unter dem Text, daneben die Einordnung
        fuss = einordnungFuss(e.titel) { s -> zurAktie(s) }.let { einordnung ->
            if (Rss.istStandard(e)) {
                {
                    Text(S.q5StandardArtikel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    if (einordnung != null) einordnung()
                }
            } else einordnung
        },
    )
}

// Text ueber die Content API mit Autorenzeile, Copyright und Logo. Ohne Schluessel oder Kontingent nur Ueberschrift,
// Anriss und der Weg in den Browser, die Website liest die App nicht aus.
@Composable
private fun GuardianBlatt(state: AppState, e: Rss.Eintrag, symbole: List<String>, onOpenSymbol: (String) -> Unit, onDismiss: () -> Unit) {
    var artikel by remember(e.link) { mutableStateOf<NachrichtenApis.Artikel?>(null) }
    var fertig by remember(e.link) { mutableStateOf(false) }
    LaunchedEffect(e.link) {
        artikel = runCatching { state.guardianArtikel(e.link) }.getOrNull()
        fertig = true
    }
    val a = artikel
    val autor = a?.autor?.ifBlank { null } ?: e.autor.ifBlank { null }
    NewsSheet(
        MarketApi.NewsItem(
            title = a?.titel?.ifBlank { null } ?: e.titel, link = e.link, published = e.zeit, summary = e.zusammenfassung,
            source = listOfNotNull(autor, "The Guardian").joinToString(" · "),
        ),
        onDismiss = onDismiss,
        symbole = symbole, onOpenSymbol = onOpenSymbol,
        text = a?.absaetze ?: emptyList(),
        textLaedt = !fertig,
        ohneText = S.newsGuardianNoText,
        fuss = {
            Column {
                Text("© Guardian News & Media Limited " + java.time.Year.now().value, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                GuardianLogo(Modifier.padding(top = 8.dp))
            }
        },
    )
}

// Rubrik eines Feeds aus seiner Adresse („Dealbook“, „Markets“), fuer Feeds desselben Hauses mit gleicher Kategorie.
internal fun feedRubrik(adresse: String): String =
    adresse.substringBefore('?').trimEnd('/').substringAfterLast('/').substringBeforeLast('.').replaceFirstChar { it.uppercase() }

// Das Thema oder bei Haeusern und eigenen Feeds deren Kurzname.
@Composable
private fun unterName(q: Rss.Quelle): String =
    if (q.unter == q.schluessel) q.kurz else S.newsCategory(q.kategorie)


// Besonders wichtige Meldungen tragen links einen schmalen Balken und ueber dem Titel ihre Uhrzeit,
// in der ersten Stunde relativ.
@Composable
private fun NewsZeile(
    e: Rss.Eintrag,
    art: de.troetenrepublik.app.engine.NachrichtArt.Art?,
    artSymbol: String?,
    artAenderung: Double?,
    wichtig: Boolean,
    gemerkt: Boolean,
    linkFarbe: androidx.compose.ui.graphics.Color,
    onMerken: () -> Unit,
    // Kategorie als kleines farbiges Etikett vor der Uhrzeit (Reiter „Wichtigste“)
    etikett: Pair<String, androidx.compose.ui.graphics.Color>? = null,
    // Uhrzeit auch bei einer unwichtigen Zeile, mit Datum, wenn sie nicht von heute ist
    zeitZeigen: Boolean = wichtig,
    mitDatum: Boolean = false,
    onClick: () -> Unit,
) {
    val jetzt = System.currentTimeMillis()
    // Wichtige Meldungen in der Warnfarbe, getrennt von Gewinn und Verlust
    val WICHTIG_FARBE = LocalSignal.current.warn
    Row(
        Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min).clickable(onClick = onClick)
            .padding(start = 10.dp, end = 6.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(3.dp).fillMaxHeight().padding(vertical = 2.dp).clip(RoundedCornerShape(2.dp))
                .background(if (wichtig) WICHTIG_FARBE else androidx.compose.ui.graphics.Color.Transparent),
        )
        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f)) {
            if (etikett != null || (zeitZeigen && e.zeit > 0)) {
                Row(Modifier.padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (etikett != null) {
                        Text(
                            etikett.first, style = MaterialTheme.typography.labelSmall, color = etikett.second, maxLines = 1, softWrap = false,
                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(etikett.second.copy(alpha = 0.16f)).padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    if (zeitZeigen && e.zeit > 0) {
                        val heuteZeit = Fmt.dayMonth(e.zeit) == Fmt.dayMonth(jetzt)
                        Text(
                            when {
                                jetzt - e.zeit < 3_600_000L -> Fmt.relative(e.zeit, jetzt)
                                mitDatum && !heuteZeit -> Fmt.dayMonth(e.zeit) + " " + Fmt.time(e.zeit)
                                else -> Fmt.time(e.zeit)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (wichtig) WICHTIG_FARBE else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, softWrap = false,
                        )
                    }
                }
            }
            Text(e.titel, style = MaterialTheme.typography.titleSmall.schlagzeile(), lineHeight = 22.sp)
            // Artikel der STANDARD-Quelle: Hinweis unter dem Titel
            if (Rss.istStandard(e)) Text(S.q5StandardArtikel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
            NewsArtZeile(art, artSymbol, artAenderung, Modifier.padding(top = 3.dp))
            // Finanziell relevante Beitraege von Trump: Themen und genannte Unternehmen.
            if (e.themen.isNotEmpty()) {
                Text(
                    S.truthRelevant(e.themen.map { S.truthThema(it) }), style = MaterialTheme.typography.labelSmall,
                    color = WICHTIG_FARBE, modifier = Modifier.padding(top = 3.dp),
                )
            }
            // Autorenzeile, beim Guardian vorgeschrieben.
            if (e.autor.isNotBlank()) Text(e.autor, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(10.dp))
        QuellenLogo(Rss.domain(e), e.herausgeber.ifBlank { e.quelle })
        if (!Rss.nurAnsicht(e)) IconButton(onClick = onMerken) {
            Icon(
                if (gemerkt) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                if (gemerkt) S.newsUnsave else S.newsSave,
                tint = if (gemerkt) linkFarbe else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// Symbol der eigenen Domain der Quelle: erst das grosse Apple-Symbol, dann das Favicon; auch fuer die uebergeordnete
// Domain (feeds.example.com zu example.com). Ohne Logo bleibt der Kreis mit dem Anfangsbuchstaben.
internal fun quellenLogoUrls(domain: String): List<String> {
    val d = domain.trim().lowercase().removePrefix("www.")
    if (d.isEmpty() || !d.contains('.') || d.contains(' ') || d.contains('/')) return emptyList()
    val hosts = buildList {
        add(d)
        val teile = d.split('.')
        if (teile.size > 2 && teile.last().length > 2) add(teile.takeLast(2).joinToString("."))
    }.distinct()
    return hosts.flatMap { listOf("https://$it/apple-touch-icon.png", "https://$it/favicon.ico") }
}

@Composable
internal fun QuellenLogo(domain: String, name: String, groesse: androidx.compose.ui.unit.Dp = 18.dp) {
    val urls = remember(domain) { if (Vorfuehrung.aktiv) emptyList() else quellenLogoUrls(domain) }
    var index by remember(urls) { mutableStateOf(0) }
    var geladen by remember(urls) { mutableStateOf(false) }
    // Darstellung wie bei den Wertpapier-Logos: Schwarz-weiss mit eigener Flaeche ueber LogoSw, ohne Hintergrund nur das
    // Zeichen (LogoTinte), farbig im Sepia-Modus warm getoent
    val farbig = LogoAnzeige.farbig
    val url = urls.getOrNull(index)
    val nurZeichen = !farbig && LogoAnzeige.hintergrundEntfernen && url != null
    var sw by remember(url, farbig) { mutableStateOf(if (!farbig && url != null) LogoSw.gemerkt(url) else null) }
    var maske by remember(url, nurZeichen) { mutableStateOf(if (nurZeichen) LogoTinte.gemerkt(url!!) else null) }
    var maskeFehlt by remember(url, nurZeichen) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val dunkel = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val sepia = farbig && LocalSignal.current.sepia
    val ohneKreis = nurZeichen && !maskeFehlt
    Box(
        Modifier.size(groesse).then(if (ohneKreis) Modifier else Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)),
        contentAlignment = Alignment.Center,
    ) {
        if (!geladen && !ohneKreis) Text(name.take(1).uppercase(), fontSize = (groesse.value * 10f / 18f).sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val m = maske
        val bildSw = sw
        if (ohneKreis && m != null) {
            androidx.compose.foundation.Image(
                m, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (dunkel) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black),
            )
        } else if (!ohneKreis && bildSw != null) {
            androidx.compose.foundation.Image(bildSw, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        } else if (url != null) {
            de.troetenrepublik.app.plattform.LogoBild(
                url = url, contentDescription = null,
                modifier = Modifier.fillMaxSize().then(
                    when {
                        // unsichtbar laden, bis die Maske gerechnet ist
                        ohneKreis -> Modifier.graphicsLayer { alpha = 0f }
                        !farbig -> Modifier.graphicsLayer {
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(tintenMatrix(dunkel))
                            compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                        }
                        sepia -> Modifier.graphicsLayer {
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(sepiaMatrix())
                            compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                        }
                        else -> Modifier
                    },
                ),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                onSuccess = { bild ->
                    geladen = true
                    val look = if (!farbig && bild != null) LogoLooks.analyze(bild) else null
                    if (ohneKreis) {
                        if (bild == null) maskeFehlt = true
                        else scope.launch { LogoTinte.holen(url, bild, look).let { if (it != null) maske = it else maskeFehlt = true } }
                    } else if (bild != null && look != null && look.fill) {
                        scope.launch { LogoSw.holen(url, bild)?.let { sw = it } }
                    }
                },
                onError = { index += 1 },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuellenBlatt(state: AppState, onMitteilungen: () -> Unit, onFeedNeu: () -> Unit, onDismiss: () -> Unit) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val deutsch = settings.sprache == de.troetenrepublik.app.data.Lang.DE
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = blattZustandHier(),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
            // Erklaerung hinter dem Info-Symbol am Titel
            var info by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(S.newsSources, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { info = true }) {
                    Icon(androidx.compose.material.icons.Icons.Outlined.Info, S.infoErklaerung, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
            if (info) MarktInfoBlatt(S.newsSources, S.newsSourcesHint + "\n• " + S.feedBuendel) { info = false }
            NavRow(S.notifications, S.newsNotifyOption(settings.newsMitteilung).takeIf { settings.newsMitteilung in listOf(MITTEILUNG_WICHTIGE, MITTEILUNG_ALLE, MITTEILUNG_AUS) } ?: S.feedMeldeOption(settings.newsMitteilung), onClick = onMitteilungen)
            MarktInfoKopf(S.newsImportantTitle, S.newsImportantExplain)
            ToggleRow(S.newsWichtigFarbig, settings.newsWichtigFarbig) { state.newsWichtigFarbigSetzen(it) }
            if (!deutsch) ToggleRow(S.newsWichtigEnglisch, settings.newsWichtigEnglisch, hint = S.newsWichtigEnglischHinweis) { state.newsWichtigEnglischSetzen(it) }
            if (!deutsch) ToggleRow(S.newsNurEnglisch, settings.newsNurEnglisch) { state.newsNurEnglischSetzen(it) }
            // Nach Anbietern geordnet wie die Reiter der Seite.
            Rss.nurEnglisch(Rss.eingebaut(deutsch), settings.nurEnglischeQuellen).filter { Rss.hierLesbar(it) }.groupBy { it.anbieter }.forEach { (anbieter, feeds) ->
                SectionHeader(S.newsAnbieter(anbieter))
                // Mehrere Feeds desselben Hauses tragen denselben Titel: darunter steht dann die Kategorie
                val namen = ArrayList<String>()
                for (q in feeds) namen += unterName(q)
                feeds.forEachIndexed { i, q ->
                    val doppelt = feeds.indices.count { namen[it] == namen[i] && feeds[it].kategorie == q.kategorie } > 1
                    val unter = when {
                        namen.count { it == namen[i] } <= 1 -> q.name
                        doppelt -> S.newsCategory(q.kategorie) + " · " + feedRubrik(q.adresse)
                        else -> S.newsCategory(q.kategorie)
                    }
                    QuellenSchalter(namen[i], unter, q.schluessel !in settings.quellenAus) { state.newsQuelleSchalten(q.schluessel, it) }
                }
            }
            // Eigene Feeds legt man im Feed-Blatt an und verwaltet sie dort (Stift im Reiter des Feeds). Adressen, die
            // frueher hier eingetragen wurden, bleiben Quellen der App und lassen sich hier entfernen.
            SectionHeader(S.newsOwnFeeds)
            settings.eigeneFeeds.forEach { (name, url) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name.ifBlank { Rss.domain(url) }, style = MaterialTheme.typography.titleSmall)
                        Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { state.newsFeedEntfernen(url) }) { Text(S.newsRemove, color = MaterialTheme.colorScheme.error) }
                }
            }
            NavRow(S.feedErstellen, onClick = onFeedNeu)
        }
    }
}

@Composable
private fun QuellenSchalter(titel: String, unter: String, an: Boolean, onChange: (Boolean) -> Unit) =
    // Derselbe Name steht nicht zweimal untereinander
    ToggleRow(titel, an, hint = unter.takeIf { !it.equals(titel, ignoreCase = true) }, onChange = onChange)

// Dezente Trennlinie zwischen Listenzeilen (News, Insider, Laender u. a.); 1,5 dp stark (0.78, vorher 1 dp).
// Mit Trennlinien Aus bleibt der Platz, die Linie ist unsichtbar. Parameter wie HorizontalDivider, Vorgabe ist die Listenlinie.
@Composable
internal fun Trennlinie(
    modifier: Modifier = Modifier.padding(horizontal = 20.dp),
    thickness: androidx.compose.ui.unit.Dp = 1.5.dp,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
) {
    val farbe = if (!de.troetenrepublik.app.engine.LogoDarstellung.linieSichtbar(LogoAnzeige.trennlinien)) androidx.compose.ui.graphics.Color.Transparent
        else if (color == androidx.compose.ui.graphics.Color.Unspecified) MaterialTheme.colorScheme.outline.copy(alpha = 0.5f) else color
    androidx.compose.material3.HorizontalDivider(modifier, thickness = thickness, color = farbe)
}

