package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.Regionen
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.NasdaqKalender
import de.troetenrepublik.app.market.Quartalszahlen
import de.troetenrepublik.app.market.Symbols
import de.troetenrepublik.app.market.ohneAbbruch
import de.troetenrepublik.app.plattform.horizontalScrollMaus
import de.troetenrepublik.app.plattform.mausWischen
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

private val NEW_YORK: ZoneId = ZoneId.of("America/New_York")

// Dividendentermine, Aktiensplits und Wirtschaftstermine aus den Kalendern der Nasdaq (5.95): die Wochen als
// wischbare Reiter, je Handelstag die Termine. Bei den Dividenden zuerst nur bekannte Werte (Depot, Merkliste und
// Verzeichnis der App), auf Wunsch alle. Dazu der Abschnitt Wirtschaftstermine fuer den Reiter Ausblick und der
// Abschnitt Ex-Tage fuer den Reiter Bewegungen.

// Ex-Tage der Woche: Vorwoche bis in zwei Wochen, je Handelstag die Dividenden und darunter die am selben Tag
// wirksamen Splits. Rechts der Betrag je Aktie, darunter die Summe eines Jahres laut Quelle.
@Composable
fun DividendenPage(state: AppState, onBack: () -> Unit, onOpen: (String) -> Unit) {
    // Wahl Bekannte oder Alle bleibt gespeichert und gilt auch fuer die Vorschau im Reiter Bewegungen (0.68)
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val alle = settings.dividendenAlle
    // Heute in New York; wechselt um Mitternacht dort, auch wenn die Seite offen bleibt
    var heute by remember { mutableStateOf(LocalDate.now(NEW_YORK)) }
    LaunchedEffect(Unit) {
        while (true) {
            val jetzt = ZonedDateTime.now(NEW_YORK)
            val mitternacht = jetzt.toLocalDate().plusDays(1).atStartOfDay(NEW_YORK)
            delay(mitternacht.toInstant().toEpochMilli() - jetzt.toInstant().toEpochMilli() + 1_000)
            heute = LocalDate.now(NEW_YORK)
        }
    }
    val versaetze = remember { (-1..2).toList() }
    val wochen = remember(heute) { versaetze.map { Quartalszahlen.woche(heute, it) } }
    val pager = rememberPagerState(initialPage = versaetze.indexOf(0)) { versaetze.size }
    val listen = remember { versaetze.map { LazyListState() } }
    // Geladene Wochen bleiben, auch wenn der Pager eine Seite ausser Sicht verwirft; die Splits je Seite einmal
    val geladen = remember(wochen) { wochen.map { mutableStateOf<Pair<Map<LocalDate, List<NasdaqKalender.Dividende>?>, List<NasdaqKalender.Split>?>?>(null) } }
    val scope = rememberCoroutineScope()
    val dismiss = rememberSwipeDownDismiss(onBack)
    val konto by state.account.collectAsStateWithLifecycle()
    val eigeneSymbole = remember(konto) { (konto.positions.filter { !it.isDerivative }.map { it.symbol } + konto.watchlist).toSet() }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .sheetDismiss(dismiss) { listen.getOrNull(pager.currentPage)?.canScrollBackward != true },
    ) {
        ScreenHeader(S.nkDivTitel, null, onBack)
        MitlaufendeReiter(
            anzahl = wochen.size,
            pager = pager,
            titel = { i -> wochenText(wochen[i]) },
            modifier = Modifier.padding(vertical = 12.dp),
        ) { i -> scope.launch { pager.animateScrollToPage(i) } }
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().mausWischen(pager)) { seite ->
            DividendenWoche(state, wochen[seite], heute, alle, { v -> scope.launch { state.settings.update { it.copy(dividendenAlle = v) } } }, eigeneSymbole, geladen[seite], listen[seite], onOpen)
        }
    }
}

// Eine Woche: Einleitung, Wahl Bekannte oder Alle, dann die Handelstage mit Ex-Tagen und Splits.
@Composable
private fun DividendenWoche(
    state: AppState,
    tage: List<LocalDate>,
    heute: LocalDate,
    alle: Boolean,
    alleSetzen: (Boolean) -> Unit,
    eigeneSymbole: Set<String>,
    geladen: MutableState<Pair<Map<LocalDate, List<NasdaqKalender.Dividende>?>, List<NasdaqKalender.Split>?>?>,
    liste: LazyListState,
    onOpen: (String) -> Unit,
) {
    LaunchedEffect(tage) {
        if (geladen.value != null) return@LaunchedEffect
        val d = ohneAbbruch { state.nasdaqDividenden(tage) }.getOrElse { tage.associateWith { null } }
        val s = ohneAbbruch { state.nasdaqSplits() }.getOrNull()
        currentCoroutineContext().ensureActive()
        geladen.value = d to s
    }
    val daten = geladen.value?.first
    val splits = geladen.value?.second
    val jeTag = remember(daten, eigeneSymbole) {
        tage.associateWith { t ->
            daten?.get(t).orEmpty().map { if (it.symbol in eigeneSymbole) it.copy(eigen = true) else it }
        }
    }
    val uri = LocalUriHandler.current

    LazyColumn(Modifier.fillMaxSize(), state = liste) {
        item(key = "intro") {
            Text(
                S.nkDivIntro, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        item(key = "auswahl") {
            Row(
                Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilterChipPill(S.qzBekannte, selected = !alle, dropdown = false) { alleSetzen(false) }
                FilterChipPill(S.qzAlle, selected = alle, dropdown = false) { alleSetzen(true) }
            }
        }
        val d = daten
        if (d == null) {
            item(key = "lade") { EmptyHint(S.loading) }
        } else {
            // auch wenn alle Dividendentage fehlen, folgen darunter noch die Tage mit Splits
            if (d.values.all { it == null }) item(key = "fehlt") { EmptyHint(S.nkNichtVerfuegbar) }
            // Splits je Ausfuehrungstag; am Wochenende wirksame zaehlen zum folgenden Montag
            val splitsJeTag = mutableMapOf<LocalDate, MutableList<NasdaqKalender.Split>>()
            for (s in splits.orEmpty()) {
                var st = LocalDate.ofEpochDay(s.tag)
                if (st.dayOfWeek == DayOfWeek.SATURDAY) st = st.plusDays(2)
                if (st.dayOfWeek == DayOfWeek.SUNDAY) st = st.plusDays(1)
                splitsJeTag.getOrPut(st) { mutableListOf() }.add(s)
            }
            val alleFehlen = d.values.all { it == null }
            for (t in tage.filter { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }) {
                // ohne alle Dividendendaten nur die Tage mit Splits
                if (alleFehlen && splitsJeTag[t].isNullOrEmpty()) continue
                val gesamt = jeTag[t].orEmpty()
                val zeigen = if (alle) NasdaqKalender.dividendenOrdnen(gesamt) else NasdaqKalender.dividendenBekannte(gesamt)
                val fehlt = d[t] == null
                item(key = "tag-$t") { TagKopf(t, t == heute, zeigen.size, gesamt.size, fehlt) }
                if (zeigen.isEmpty() && !fehlt) item(key = "leer-$t") {
                    Text(
                        if (gesamt.isEmpty()) S.nkDivKeine else S.nkDivKeineBekannten(gesamt.size),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
                items(zeigen, key = { "d-$t-" + it.symbol + "-" + it.exTag }, contentType = { "dividende" }) { e ->
                    DividendenZeile(e) { onOpen(e.symbol) }
                }
                // Am selben Tag wirksame Splits, unabhaengig von der Wahl oben und von den Dividendendaten
                val tagSplits = splitsJeTag[t].orEmpty()
                if (tagSplits.isNotEmpty()) {
                    item(key = "splits-$t") {
                        Text(
                            S.nkSplits, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 2.dp),
                        )
                    }
                    items(tagSplits, key = { "s-$t-" + it.symbol + "-" + it.tag }, contentType = { "split" }) { s ->
                        SplitZeile(s, s.symbol in eigeneSymbole) { onOpen(s.symbol) }
                    }
                }
            }
        }
        item(key = "hinweis") { ToolNote(S.nkDivHinweis) }
        item(key = "quelle") {
            QuellenZeile("${S.source}: ${S.nkQuelle}", Modifier.padding(top = 8.dp), onClick = { runCatching { uri.openUri(NasdaqKalender.SEITE_DIVIDENDEN) } })
        }
        item(key = "ende") { Spacer(Modifier.height(40.dp)) }
    }
}

// Eine Dividende: Name aus dem eigenen Verzeichnis, sonst von der Quelle; darunter Kuerzel, Zahltag und Depot-Hinweis.
// Rechts der Betrag je Aktie, darunter die Summe eines Jahres laut Quelle.
@Composable
internal fun DividendenZeile(d: NasdaqKalender.Dividende, onClick: () -> Unit) {
    AssetRow(
        symbol = d.symbol,
        name = Symbols.find(d.symbol)?.name ?: d.name,
        subtitle = listOfNotNull(
            d.symbol,
            d.zahltag?.let { S.nkZahltag(Fmt.dayMonthNum(mittag(LocalDate.ofEpochDay(it)))) },
            if (d.eigen) S.qzEigen else null,
        ).joinToString(PUNKT),
        rightPlain = d.betrag?.let { Fmt.mitSymbol(Fmt.num(it, if (it < 0.10) 4 else 2), "$") },
        rightCaption = d.jeJahr?.let { S.nkJeJahr(Fmt.mitSymbol(Fmt.num(it, 2), "$")) },
        onClick = onClick,
    )
}

// Ein Split: rechts das Verhaeltnis, darunter in Worten, wie viele alte Aktien in wie viele neue uebergehen.
@Composable
internal fun SplitZeile(s: NasdaqKalender.Split, eigen: Boolean, onClick: () -> Unit) {
    AssetRow(
        symbol = s.symbol,
        name = Symbols.find(s.symbol)?.name ?: s.name,
        subtitle = listOfNotNull(
            s.symbol,
            if (s.zusammenlegung) S.nkZusammenlegung else S.nkSplit,
            if (eigen) S.qzEigen else null,
        ).joinToString(PUNKT),
        rightPlain = s.verhaeltnis,
        rightCaption = S.nkSplitText(s.neu, s.alt),
        onClick = onClick,
    )
}

// Wirtschaftstermine der Woche: Vorwoche bis naechste Woche, je Tag Montag bis Freitag. Zuerst nur wichtige Laender,
// auf Wunsch alle. Rechts der gemeldete Wert, darunter die Erwartung der Fachleute.
@Composable
fun WirtschaftPage(state: AppState, onBack: () -> Unit) {
    var wichtige by rememberSaveable { mutableStateOf(true) }
    // Heute in New York; wechselt um Mitternacht dort, auch wenn die Seite offen bleibt
    var heute by remember { mutableStateOf(LocalDate.now(NEW_YORK)) }
    LaunchedEffect(Unit) {
        while (true) {
            val jetzt = ZonedDateTime.now(NEW_YORK)
            val mitternacht = jetzt.toLocalDate().plusDays(1).atStartOfDay(NEW_YORK)
            delay(mitternacht.toInstant().toEpochMilli() - jetzt.toInstant().toEpochMilli() + 1_000)
            heute = LocalDate.now(NEW_YORK)
        }
    }
    val versaetze = remember { (-1..1).toList() }
    val wochen = remember(heute) { versaetze.map { Quartalszahlen.woche(heute, it) } }
    val pager = rememberPagerState(initialPage = versaetze.indexOf(0)) { versaetze.size }
    val listen = remember { versaetze.map { LazyListState() } }
    // Geladene Wochen bleiben, auch wenn der Pager eine Seite ausser Sicht verwirft
    val geladen = remember(wochen) { wochen.map { mutableStateOf<Map<LocalDate, List<NasdaqKalender.Wirtschaftstermin>?>?>(null) } }
    val scope = rememberCoroutineScope()
    val dismiss = rememberSwipeDownDismiss(onBack)

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .sheetDismiss(dismiss) { listen.getOrNull(pager.currentPage)?.canScrollBackward != true },
    ) {
        ScreenHeader(S.nkWiTitel, null, onBack)
        MitlaufendeReiter(
            anzahl = wochen.size,
            pager = pager,
            titel = { i -> wochenText(wochen[i]) },
            modifier = Modifier.padding(vertical = 12.dp),
        ) { i -> scope.launch { pager.animateScrollToPage(i) } }
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().mausWischen(pager)) { seite ->
            WirtschaftWoche(state, wochen[seite], heute, wichtige, { wichtige = it }, geladen[seite], listen[seite])
        }
    }
}

// Eine Woche: Einleitung, Wahl Wichtige Laender oder Alle, dann die Tage Montag bis Freitag.
@Composable
private fun WirtschaftWoche(
    state: AppState,
    tage: List<LocalDate>,
    heute: LocalDate,
    wichtige: Boolean,
    wichtigeSetzen: (Boolean) -> Unit,
    geladen: MutableState<Map<LocalDate, List<NasdaqKalender.Wirtschaftstermin>?>?>,
    liste: LazyListState,
) {
    LaunchedEffect(tage) {
        if (geladen.value != null) return@LaunchedEffect
        val d = ohneAbbruch { state.nasdaqWirtschaft(tage) }.getOrElse { tage.associateWith { null } }
        currentCoroutineContext().ensureActive()
        geladen.value = d
    }
    val daten = geladen.value
    val uri = LocalUriHandler.current

    LazyColumn(Modifier.fillMaxSize(), state = liste) {
        item(key = "intro") {
            Text(
                S.nkWiIntro, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        item(key = "auswahl") {
            Row(
                Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilterChipPill(S.nkWichtige, selected = wichtige, dropdown = false) { wichtigeSetzen(true) }
                FilterChipPill(S.qzAlle, selected = !wichtige, dropdown = false) { wichtigeSetzen(false) }
            }
        }
        val d = daten
        when {
            d == null -> item(key = "lade") { EmptyHint(S.loading) }
            d.values.all { it == null } -> item(key = "fehlt") { EmptyHint(S.nkNichtVerfuegbar) }
            else -> for (t in tage.filter { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }) {
                val gesamt = d[t].orEmpty()
                val zeigen = if (wichtige) NasdaqKalender.wirtschaftWichtige(gesamt) else gesamt
                val fehlt = d[t] == null
                item(key = "tag-$t") { TagKopf(t, t == heute, zeigen.size, gesamt.size, fehlt) }
                if (zeigen.isEmpty() && !fehlt) item(key = "leer-$t") {
                    Text(
                        S.qzKeine, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
                // Index im Schluessel: gleicher Name und gleiche Uhrzeit mit verschiedenen Werten kommen vor
                itemsIndexed(zeigen, key = { i, _ -> "w-$t-$i" }, contentType = { _, _ -> "termin" }) { _, e ->
                    WirtschaftZeile(e)
                }
            }
        }
        item(key = "hinweis") { ToolNote(S.nkWiHinweis) }
        item(key = "quelle") {
            QuellenZeile("${S.source}: ${S.nkQuelle}", Modifier.padding(top = 8.dp), onClick = { runCatching { uri.openUri(NasdaqKalender.SEITE_WIRTSCHAFT) } })
        }
        item(key = "ende") { Spacer(Modifier.height(40.dp)) }
    }
}

// Ein Wirtschaftstermin, eigene Zeile ohne Klick (AssetRow verlangte einen): Name wie bei der Quelle, darunter Uhrzeit,
// Land und Vorwert. Rechts der gemeldete Wert, darunter die Erwartung der Fachleute.
@Composable
internal fun WirtschaftZeile(t: NasdaqKalender.Wirtschaftstermin) {
    val deutsch = I18n.lang == Lang.DE
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Column(Modifier.weight(1f)) {
            AuslaufText(t.name, maxLines = 2, style = MaterialTheme.typography.titleSmall)
            PunktText(
                listOfNotNull(
                    NasdaqKalender.zeitpunkt(t)?.let(Fmt::time) ?: S.nkGanztags,
                    nkLandName(t.land),
                    t.vorher?.let { S.nkVorher(NasdaqKalender.wertText(it, deutsch)) },
                ).joinToString(PUNKT),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            t.ist?.let { ist ->
                Text(NasdaqKalender.wertText(ist, deutsch), style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
            }
            t.erwartet?.let { erwartet ->
                Text(
                    S.qzErwartet(NasdaqKalender.wertText(erwartet, deutsch)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false,
                )
            }
        }
    }
}

// Landesname in der Sprache der App; der Euroraum fehlt im Verzeichnis
internal fun nkLandName(en: String): String =
    Regionen.LAENDER.firstOrNull { it.en == en }?.name(I18n.lang == Lang.DE) ?: (if (en == "Euro Zone") S.nkEuroraum else en)

// Reiter „Ausblick“: die wichtigsten Termine des naechsten Handelstags, die ganze Woche auf eigener Seite. Ein
// einzelnes LazyColumn-item, darum alles in einer Column.
@Composable
internal fun WirtschaftAbschnitt(state: AppState, oeffnen: (String) -> Unit) {
    var tag by remember { mutableStateOf<LocalDate?>(null) }
    // null: wird noch geladen
    var termine by remember { mutableStateOf<List<NasdaqKalender.Wirtschaftstermin>?>(null) }
    var fehlt by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val heute = LocalDate.now(NEW_YORK)
        val t = Quartalszahlen.woche(heute, 0).first { it >= heute || it.dayOfWeek == DayOfWeek.FRIDAY }
        tag = t
        val l = ohneAbbruch { state.nasdaqWirtschaft(listOf(t))[t] }.getOrNull()
        currentCoroutineContext().ensureActive()
        if (l == null) { fehlt = true; return@LaunchedEffect }
        val w = NasdaqKalender.wirtschaftWichtige(l)
        // Bevorzugt Termine mit Erwartung, danach in Zeitreihenfolge (ganztaegige ohne Uhrzeit ans Ende)
        fun folgen(g: List<NasdaqKalender.Wirtschaftstermin>) = g.sortedBy { NasdaqKalender.zeitpunkt(it) ?: Long.MAX_VALUE }
        termine = (folgen(w.filter { it.erwartet != null }) + folgen(w.filter { it.erwartet == null })).take(6).let(::folgen)
    }
    val uri = LocalUriHandler.current
    Column {
        SectionHeader(S.nkWiTitel, chevron = true, onClick = { oeffnen("discover/economy") })
        val l = termine
        when {
            l == null && !fehlt -> EmptyHint(S.trLaedt)
            l == null -> EmptyHint(S.nkNichtVerfuegbar)
            l.isEmpty() -> EmptyHint(S.nkWiKeine)
            else -> {
                Text(
                    wochentag(tag!!) + " " + Fmt.dayMonthNum(mittag(tag!!)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                for (t in l) WirtschaftZeile(t)
            }
        }
        QuellenZeile("${S.source}: ${S.nkQuelle}", Modifier.padding(top = 8.dp), onClick = { runCatching { uri.openUri(NasdaqKalender.SEITE_WIRTSCHAFT) } })
    }
}

// Reiter „Bewegungen“: die Ex-Tage des naechsten Handelstags (bekannte oder alle, wie gewaehlt), die ganze Woche auf eigener Seite. Ein
// einzelnes LazyColumn-item, darum alles in einer Column.
@Composable
internal fun DividendenAbschnitt(state: AppState, tag: LocalDate, onOpen: (String) -> Unit, oeffnen: (String) -> Unit) {
    val konto by state.account.collectAsStateWithLifecycle()
    val eigeneSymbole = remember(konto) { (konto.positions.filter { !it.isDerivative }.map { it.symbol } + konto.watchlist).toSet() }
    var gesamt by remember { mutableStateOf<List<NasdaqKalender.Dividende>?>(null) }
    var fehlt by remember { mutableStateOf(false) }
    LaunchedEffect(tag) {
        val l = ohneAbbruch { state.nasdaqDividenden(listOf(tag))[tag] }.getOrNull()
        currentCoroutineContext().ensureActive()
        if (l == null) { fehlt = true; return@LaunchedEffect }
        gesamt = l
    }
    // Gleiche Wahl Bekannte oder Alle wie auf der Seite der Dividendentermine (0.68)
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val alle = settings.dividendenAlle
    // abgeleitet, damit auch spaeter auf die Merkliste gesetzte Werte weiterhin erkannt werden
    val zeigen = remember(gesamt, eigeneSymbole, alle) { gesamt?.let { l -> NasdaqKalender.dividendenVorschau(l.map { if (it.symbol in eigeneSymbole) it.copy(eigen = true) else it }, alle) } }
    Column {
        SectionHeader(S.nkDivTitel, chevron = true, onClick = { oeffnen("discover/dividends") })
        val g = gesamt
        when {
            g == null && !fehlt -> EmptyHint(S.trLaedt)
            g == null -> EmptyHint(S.nkNichtVerfuegbar)
            else -> {
                Text(
                    wochentag(tag) + " " + Fmt.dayMonthNum(mittag(tag)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                val b = zeigen.orEmpty()
                if (b.isEmpty()) {
                    if (g.isEmpty()) EmptyHint(S.nkDivKeine)
                    else Text(
                        S.nkDivKeineBekannten(g.size),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                } else for (d in b) DividendenZeile(d) { onOpen(d.symbol) }
            }
        }
    }
}
