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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.MITTEILUNG_ALLE
import de.troetenrepublik.app.data.MITTEILUNG_AUS
import de.troetenrepublik.app.data.MITTEILUNG_WICHTIGE
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.Rss
import de.troetenrepublik.app.market.NachrichtenApis
import androidx.compose.ui.graphics.luminance
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
fun NewsScreen(state: AppState, onBack: () -> Unit, onZugaenge: () -> Unit = {}, onRueckblick: () -> Unit = {}) {
    val stand by state.news.stand.collectAsStateWithLifecycle()
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val signal = LocalSignal.current

    // Was vor diesem Besuch ungelesen war, behaelt seinen Punkt, bis die Seite geschlossen wird.
    val gelesenBis = remember { settings.newsGelesenBis }
    var suche by rememberSaveable { mutableStateOf<String?>(null) }
    var quellenBlatt by remember { mutableStateOf(false) }
    var offen by remember { mutableStateOf<Rss.Eintrag?>(null) }
    val gemerkt = remember(settings.newsGemerkt) { state.newsGemerkt(settings) }
    val gemerktIds = remember(gemerkt) { gemerkt.mapTo(HashSet()) { it.id } }
    val quellen = remember(settings.newsAus, settings.newsEigene, settings.sprache) { state.newsQuellen(settings) }

    LaunchedEffect(Unit) {
        state.newsGelesen()
        runCatching { state.newsLaden(neu = stand.geladenAm < System.currentTimeMillis() - 60_000L) }
        while (true) {
            delay(60_000L)
            runCatching { state.newsLaden() }
        }
    }

    // Anbieter mit mindestens einem eingeschalteten Feed, „Gemerkt“ nur mit Gemerktem.
    val reiter = Rss.Anbieter.entries.filter { a ->
        when (a) {
            Rss.Anbieter.ALLE -> true
            Rss.Anbieter.GEMERKT -> gemerkt.isNotEmpty()
            // Dienste mit eigenem Schluessel stehen immer da. Ohne Schluessel zeigt der Reiter, wie man ihn eintraegt.
            Rss.Anbieter.GUARDIAN, Rss.Anbieter.FINNHUB, Rss.Anbieter.ALPHAVANTAGE -> true
            else -> quellen.any { it.anbieter == a }
        }
    }
    var gewaehlt by rememberSaveable { mutableStateOf(Rss.Anbieter.ALLE.name) }
    val pager = rememberPagerState(initialPage = reiter.indexOfFirst { it.name == gewaehlt }.coerceAtLeast(0)) { reiter.size }
    // Der gewaehlte Reiter bleibt, auch wenn Reiter dazukommen oder wegfallen.
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { i -> reiter.getOrNull(i)?.let { gewaehlt = it.name } } }
    LaunchedEffect(reiter.size) {
        val i = reiter.indexOfFirst { it.name == gewaehlt }.coerceAtLeast(0)
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
    LaunchedEffect(stand.eintraege, depot) {
        wichtige = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { Rss.wichtige(stand.eintraege, depot) }
    }
    // Jeder Reiter hat seine eigene Liste. Nach unten wegwischen, wenn die Liste oben steht.
    val listen = remember(reiter.size) { List(reiter.size) { LazyListState() } }
    val dismiss = rememberSwipeDownDismiss(onBack)

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .sheetDismiss(dismiss) { listen.getOrNull(pager.currentPage)?.canScrollBackward != true },
    ) {
        ScreenHeader(S.newsPageTitle, null, onBack, trailing = {
            // Gesammelte Tages- und Wochenrueckblicke.
            IconButton(onClick = onRueckblick) { Icon(Icons.Filled.History, S.rbTitel) }
            IconButton(onClick = { suche = if (suche == null) "" else null }) {
                Icon(if (suche == null) Icons.Filled.Search else Icons.Filled.Close, S.newsSearch)
            }
            IconButton(onClick = { scope.launch { runCatching { state.newsLaden(neu = true) } } }) {
                if (stand.laedt) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.Refresh, S.refreshNow)
            }
            IconButton(onClick = { quellenBlatt = true }) { Icon(Icons.Filled.Tune, S.newsSources) }
        })
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
        MitlaufendeReiter(
            anzahl = reiter.size,
            pager = pager,
            titel = { i -> S.newsAnbieter(reiter[i]) },
            modifier = Modifier.padding(vertical = 12.dp),
            zusatz = { i ->
                val a = reiter[i]
                val neu = if (a == Rss.Anbieter.GEMERKT) 0 else Rss.filter(stand.eintraege, a, quellen).count { it.zeit > gelesenBis }
                neu.takeIf { it > 0 && gelesenBis > 0 }?.toString()
            },
        ) { i -> scope.launch { pager.animateScrollToPage(i) } }
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().mausWischen(pager), beyondViewportPageCount = 1, key = { reiter.getOrNull(it)?.name ?: it }) { seite ->
            val anbieter = reiter.getOrElse(seite) { Rss.Anbieter.ALLE }
            val unterkategorien = remember(anbieter, quellen) {
                if (anbieter == Rss.Anbieter.ALLE || anbieter == Rss.Anbieter.GEMERKT) emptyList() else Rss.unterkategorien(anbieter, quellen)
            }
            val unter = unterWahl[anbieter]?.takeIf { u -> unterkategorien.any { it.unter == u } }
            val basis = if (anbieter == Rss.Anbieter.GEMERKT) gemerkt else Rss.filter(stand.eintraege, anbieter, quellen, unter)
            val gezeigt = remember(basis, suche) { Rss.suche(basis, suche.orEmpty()) }
            val nachTag = remember(gezeigt) { gezeigt.groupBy { e -> if (e.zeit > 0) Instant.ofEpochMilli(e.zeit).atZone(zone).toLocalDate() else null } }
            LazyColumn(Modifier.fillMaxSize(), state = listen.getOrElse(seite) { rememberLazyListState() }) {
                // Nur wenn der Anbieter mehr als einen Feed hat.
                if (unterkategorien.size > 1) {
                    item(key = "unter") {
                        Row(
                            Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Chip(S.newsAll, active = unter == null) { unterWahl.remove(anbieter) }
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
                if (dienst != null) {
                    val mitSchluessel = quellen.any { it.anbieter == anbieter }
                    item(key = "dienst") {
                        DienstHinweis(state, dienst, S.newsAnbieter(anbieter), mitSchluessel, stand.meldungen[dienst.gruppe], onZugaenge)
                    }
                    if (!mitSchluessel) return@LazyColumn
                }
                if (anbieter == Rss.Anbieter.TRUMP) {
                    item(key = "trump-hinweis") {
                        Text(
                            S.newsTrumpHinweis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                        )
                    }
                }
                if (gezeigt.any { NachrichtenApis.istGuardian(it.link) } && anbieter != Rss.Anbieter.GUARDIAN) {
                    item(key = "guardian-logo") { GuardianLogo(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) }
                }
                if (gezeigt.isEmpty()) {
                    item {
                        EmptyHint(
                            when {
                                anbieter == Rss.Anbieter.GEMERKT -> S.newsEmptySaved
                                stand.geladenAm == 0L -> S.newsLoading
                                !suche.isNullOrBlank() -> S.newsEmptySearch
                                else -> S.newsEmpty
                            },
                        )
                    }
                }
                nachTag.forEach { (tag, eintraege) ->
                    item(key = "tag-$tag") {
                        Text(
                            when {
                                tag == null -> "–"
                                tag == heute -> S.newsToday
                                tag == heute.minusDays(1) -> S.newsYesterday
                                else -> Fmt.date(tag.atStartOfDay(zone).toInstant().toEpochMilli())
                            },
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 18.dp, bottom = 4.dp),
                        )
                    }
                    items(eintraege, key = { "n-${it.id}" }) { e ->
                        NewsZeile(
                            e,
                            wichtig = e.id in wichtige,
                            gemerkt = e.id in gemerktIds,
                            linkFarbe = signal.link,
                            onMerken = { state.newsMerken(e) },
                        ) { offen = e }
                    }
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }

    offen?.let { e ->
        if (NachrichtenApis.istGuardian(e.link)) GuardianBlatt(state, e) { offen = null }
        else if (Rss.Kategorie.TRUMP in e.kategorien) NewsSheet(
            MarketApi.NewsItem(title = e.titel, link = e.link, published = e.zeit, summary = "", source = e.quellenText),
            onDismiss = { offen = null },
            text = e.zusammenfassung.lines().filter { it.isNotBlank() },
            fuss = if (e.themen.isNotEmpty()) { { Text(S.truthRelevant(e.themen.map { S.truthThema(it) }), style = MaterialTheme.typography.labelMedium, color = WICHTIG_FARBE) } } else null,
        )
        else NewsSheet(
            MarketApi.NewsItem(title = e.titel, link = e.link, published = e.zeit, summary = e.zusammenfassung, source = e.quellenText),
            onDismiss = { offen = null },
            ohneText = if (Rss.nurImBrowser(e.link)) S.newsBrowserOnly else null,
        )
    }
    if (quellenBlatt) QuellenBlatt(state, onDismiss = { quellenBlatt = false })
}

// Ohne Schluessel Erklaerung und Weg zu den Zugaengen, sonst Verbrauch, Meldungen des Anbieters und beim Guardian
// das vorgeschriebene Logo.
@Composable
private fun DienstHinweis(state: AppState, dienst: Rss.Art, name: String, mitSchluessel: Boolean, meldung: String?, onZugaenge: () -> Unit) {
    Column(
        Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
    ) {
        if (dienst == Rss.Art.GUARDIAN) GuardianLogo(Modifier.padding(bottom = 8.dp))
        if (!mitSchluessel) {
            Text(S.newsKeyMissing(name), style = MaterialTheme.typography.bodyMedium)
            Text(S.zgTerms(dienst.gruppe), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp))
            Text(
                S.newsKeyEnter, style = MaterialTheme.typography.labelLarge, color = LocalSignal.current.link,
                modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(Radius.chip)).clickable(onClick = onZugaenge).padding(vertical = 6.dp),
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
    de.troetenrepublik.app.plattform.LogoBild(
        if (dunkel) NachrichtenApis.GUARDIAN_LOGO_DUNKEL else NachrichtenApis.GUARDIAN_LOGO_HELL, "Powered by The Guardian",
        modifier.height(24.dp).width(140.dp), androidx.compose.ui.layout.ContentScale.Fit, onSuccess = {}, onError = {},
    )
}

// Text ueber die Content API mit Autorenzeile, Copyright und Logo. Ohne Schluessel oder Kontingent nur Ueberschrift,
// Anriss und der Weg in den Browser, die Website liest die App nicht aus.
@Composable
private fun GuardianBlatt(state: AppState, e: Rss.Eintrag, onDismiss: () -> Unit) {
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

// Das Thema oder bei Haeusern und eigenen Feeds deren Kurzname.
@Composable
private fun unterName(q: Rss.Quelle): String =
    if (q.unter == q.schluessel) q.kurz else S.newsCategory(q.kategorie)

private val WICHTIG_FARBE = androidx.compose.ui.graphics.Color(0xFFF5A623)

// Besonders wichtige Meldungen tragen links einen schmalen Balken und ueber dem Titel ihre Uhrzeit,
// in der ersten Stunde relativ.
@Composable
private fun NewsZeile(
    e: Rss.Eintrag,
    wichtig: Boolean,
    gemerkt: Boolean,
    linkFarbe: androidx.compose.ui.graphics.Color,
    onMerken: () -> Unit,
    onClick: () -> Unit,
) {
    val jetzt = System.currentTimeMillis()
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
            if (wichtig && e.zeit > 0) {
                Text(
                    if (jetzt - e.zeit < 3_600_000L) Fmt.relative(e.zeit, jetzt) else Fmt.time(e.zeit),
                    style = MaterialTheme.typography.labelSmall,
                    color = WICHTIG_FARBE,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            Text(e.titel, style = MaterialTheme.typography.titleSmall, lineHeight = 22.sp)
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
        IconButton(onClick = onMerken) {
            Icon(
                if (gemerkt) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                if (gemerkt) S.newsUnsave else S.newsSave,
                tint = if (gemerkt) linkFarbe else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun QuellenLogo(domain: String, name: String) {
    Box(
        Modifier.size(18.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1).uppercase(), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuellenBlatt(state: AppState, onDismiss: () -> Unit) {
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val deutsch = settings.sprache == de.troetenrepublik.app.data.Lang.DE
    var dialog by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
            Text(S.newsSourcesTitle, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp))
            AufklappText(S.newsSourcesHint, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            // Nur die markierten (Vorgabe), alle oder keine.
            SectionHeader(S.newsNotifyTitle)
            if (!settings.notifications) Text(S.newsNotifyOffGlobal, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 4.dp))
            Column(Modifier.padding(horizontal = 8.dp)) {
                listOf(MITTEILUNG_WICHTIGE, MITTEILUNG_ALLE, MITTEILUNG_AUS).forEach { stufe ->
                    OptionRow(S.newsNotifyOption(stufe), S.newsNotifyOptionHint(stufe), settings.newsMitteilung == stufe) { state.newsMitteilungSetzen(stufe) }
                }
            }
            AufklappText(S.newsNotifyHint, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            SectionHeader(S.newsImportantTitle)
            AufklappText(S.newsImportantExplain, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            // Nach Anbietern geordnet wie die Reiter der Seite.
            Rss.eingebaut(deutsch).filter { Rss.hierLesbar(it) }.groupBy { it.anbieter }.forEach { (anbieter, feeds) ->
                SectionHeader(S.newsAnbieter(anbieter))
                feeds.forEach { q ->
                    QuellenSchalter(unterName(q), q.name, q.schluessel !in settings.quellenAus) { state.newsQuelleSchalten(q.schluessel, it) }
                }
            }
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
            ActionLineWide(S.newsAddFeed) { dialog = true }
        }
    }
    if (dialog) FeedDialog(state, onDismiss = { dialog = false })
}

@Composable
private fun QuellenSchalter(titel: String, unter: String, an: Boolean, onChange: (Boolean) -> Unit) =
    ToggleRow(titel, an, hint = unter, onChange = onChange)

// Eingetragen wird erst, wenn unter der Adresse ein Feed antwortet.
@Composable
private fun FeedDialog(state: AppState, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    var meldung by remember { mutableStateOf<String?>(null) }
    var prueft by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(Radius.card),
        title = { Text(S.newsAddFeed) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text(S.newsFeedName) }, singleLine = true, shape = RoundedCornerShape(Radius.field))
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = url, onValueChange = { url = it.trim().take(400) }, label = { Text(S.newsFeedUrl) }, singleLine = true,
                    shape = RoundedCornerShape(Radius.field), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                meldung?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(enabled = !prueft, onClick = {
                if (!Rss.adresseGueltig(url)) { meldung = S.newsFeedInvalid; return@TextButton }
                prueft = true
                meldung = S.newsFeedChecking
                scope.launch {
                    val n = state.newsFeedHinzufuegen(name, url)
                    prueft = false
                    if (n > 0) { state.showToast(S.newsFeedAdded(n)); onDismiss() } else meldung = S.newsFeedNone
                }
            }) { Text(S.add) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.cancel) } },
    )
}
