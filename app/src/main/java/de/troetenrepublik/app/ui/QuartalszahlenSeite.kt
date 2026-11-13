package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import kotlinx.coroutines.launch
import de.troetenrepublik.app.plattform.mausWischen
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.Zahlenzeit
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.NasdaqKalender
import de.troetenrepublik.app.market.ohneAbbruch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import de.troetenrepublik.app.market.Quartalszahlen
import de.troetenrepublik.app.market.Symbols
import de.troetenrepublik.app.plattform.horizontalScrollMaus
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val NEW_YORK: ZoneId = ZoneId.of("America/New_York")

// Mittag des Tages als Zeitpunkt fuer die Datumsformate.
internal fun mittag(tag: LocalDate): Long = tag.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 12 * 3_600_000L

internal fun wochentag(tag: LocalDate): String =
    tag.format(DateTimeFormatter.ofPattern("EEEE", if (I18n.lang == Lang.DE) Locale.GERMAN else Locale.ENGLISH))

internal fun wochenText(tage: List<LocalDate>): String = Fmt.dayMonthNum(mittag(tage.first())) + "–" + Fmt.dayMonthNum(mittag(tage.last()))

// Uhrzeit eines Termins: Zeitpunkt oder Fenster am Heimatplatz, bezogen auf den Kalendertag in New York.
internal fun uhrzeitVon(e: Quartalszahlen.Termin): Zahlenzeit.Uhrzeit =
    Zahlenzeit.berechnen(LocalDate.ofEpochDay(e.tag), e.zeit, e.zeitpunkt, Quartalszahlen.platz(e.symbol))

// "HH:mm" in der Ortszeit des Nutzers, mit Hinweis, wenn der Tag ein anderer ist; null ohne Zeitpunkt.
private fun zeitText(ms: Long?, tag: LocalDate, zone: ZoneId): String? =
    ms?.let { val o = Zahlenzeit.ort(it, tag, zone); S.qzZeitTag(Zahlenzeit.hhmm(o), o.tagVersatz) }

// Kurzform der Uhrzeit fuer Zeilen und Gruppenueberschriften; null, wenn es keine Uhrzeit gibt.
internal fun uhrKurz(u: Zahlenzeit.Uhrzeit, tag: LocalDate, zone: ZoneId = ZoneId.systemDefault()): String? =
    S.qzUhrKurz(u.art.ordinal, zeitText(u.von, tag, zone), zeitText(u.bis, tag, zone))

// Lange Fassung mit Art der Uhrzeit, fuer die Einzelansicht eines Termins.
internal fun uhrLang(u: Zahlenzeit.Uhrzeit, tag: LocalDate, zone: ZoneId = ZoneId.systemDefault()): String =
    S.qzUhrLang(u.art.ordinal, zeitText(u.von, tag, zone), zeitText(u.bis, tag, zone))

// Quartalszahlen der Woche (5.94): die Wochen als wischbare Reiter (Vorwoche bis in zwei Wochen), je Handelstag die
// Termine, geteilt nach vor Boersenstart, waehrend des Handels, nach Boersenschluss und ohne Uhrzeit. Seit 5.95 kommt
// der Kalender schluesselfrei von der Nasdaq, mit Schluessel von Finnhub oder Alpha Vantage ergaenzt. Zuerst nur
// bekannte Werte (Depot, Merkliste und die groessten), auf Wunsch alle. Rechts der erwartete Gewinn je Aktie, nach den
// Zahlen der tatsaechliche, gefaerbt nach der Abweichung.
@Composable
fun QuartalszahlenPage(state: AppState, onBack: () -> Unit, onOpen: (String) -> Unit) {
    // Wahl Bekannte oder Alle bleibt gespeichert und gilt auch fuer die Vorschau im Reiter Bewegungen (0.68)
    val einstellungen by state.settingsFlow.collectAsStateWithLifecycle()
    val alle = einstellungen.quartalszahlenAlle
    // Heute in New York; wechselt um Mitternacht dort, auch wenn die Seite offen bleibt
    var heute by remember { mutableStateOf(LocalDate.now(NEW_YORK)) }
    LaunchedEffect(Unit) {
        while (true) {
            val jetzt = java.time.ZonedDateTime.now(NEW_YORK)
            val mitternacht = jetzt.toLocalDate().plusDays(1).atStartOfDay(NEW_YORK)
            kotlinx.coroutines.delay(mitternacht.toInstant().toEpochMilli() - jetzt.toInstant().toEpochMilli() + 1_000)
            heute = LocalDate.now(NEW_YORK)
        }
    }
    val versaetze = remember { (-1..2).toList() }
    val wochen = remember(heute) { versaetze.map { Quartalszahlen.woche(heute, it) } }
    val pager = rememberPagerState(initialPage = versaetze.indexOf(0)) { versaetze.size }
    val listen = remember { versaetze.map { LazyListState() } }
    // Geladene Wochen bleiben, auch wenn der Pager eine Seite ausser Sicht verwirft
    val geladen = remember(wochen) { wochen.map { mutableStateOf<Pair<Map<LocalDate, List<Quartalszahlen.Termin>?>, List<Quartalszahlen.Termin>>?>(null) } }
    val scope = rememberCoroutineScope()
    val dismiss = rememberSwipeDownDismiss(onBack)
    val konto by state.account.collectAsStateWithLifecycle()
    val eigeneSymbole = remember(konto) { (konto.positions.filter { !it.isDerivative }.map { it.symbol } + konto.watchlist).toSet() }
    // Ein Tipp auf einen Termin zeigt erst die Uebersicht, von dort geht es zur Aktie (5.97)
    var blatt by remember { mutableStateOf<Quartalszahlen.Termin?>(null) }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .sheetDismiss(dismiss) { listen.getOrNull(pager.currentPage)?.canScrollBackward != true },
    ) {
        ScreenHeader(S.qzTitel, null, onBack)
        val wm = rememberReiterWidgetMenue(state) { de.troetenrepublik.app.data.WidgetVorgabe.termine(de.troetenrepublik.app.data.WidgetVorgabe.TERMIN_QUARTALE) }
        MitlaufendeReiter(
            anzahl = wochen.size,
            pager = pager,
            langDruecken = wm.langDruecken,
            menue = { i -> wm.menue(i) },
            titel = { i -> wochenText(wochen[i]) },
            modifier = Modifier.padding(vertical = 12.dp),
        ) { i -> scope.launch { pager.animateScrollToPage(i) } }
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().mausWischen(pager)) { seite ->
            WochenSeite(state, wochen[seite], heute, alle, { v -> scope.launch { state.settings.update { it.copy(quartalszahlenAlle = v) } } }, eigeneSymbole, geladen[seite], listen[seite]) { blatt = it }
        }
    }
    blatt?.let { QuartalsUeberblickBlatt(state, it, eigeneSymbole, onDismiss = { blatt = null }, onOpen = onOpen) }
}

// Eine Woche: Einleitung, Wahl Bekannte oder Alle, dann die Tage.
@Composable
private fun WochenSeite(
    state: AppState,
    tage: List<LocalDate>,
    heute: LocalDate,
    alle: Boolean,
    alleSetzen: (Boolean) -> Unit,
    eigeneSymbole: Set<String>,
    geladen: androidx.compose.runtime.MutableState<Pair<Map<LocalDate, List<Quartalszahlen.Termin>?>, List<Quartalszahlen.Termin>>?>,
    liste: LazyListState,
    waehlen: (Quartalszahlen.Termin) -> Unit,
) {
    LaunchedEffect(tage) {
        if (geladen.value != null) return@LaunchedEffect
        val d = ohneAbbruch { state.quartalszahlen(tage) }.getOrElse { tage.associateWith { null } }
        val e = ohneAbbruch { state.eigeneZahlentermine(tage, d.values.flatMap { it.orEmpty() }.map { it.symbol }.toSet()) }.getOrDefault(emptyList())
        currentCoroutineContext().ensureActive()
        geladen.value = d to e
    }
    val daten = geladen.value?.first
    val eigene = geladen.value?.second.orEmpty()
    val jeTag = remember(daten, eigene, eigeneSymbole) {
        tage.associateWith { t ->
            Quartalszahlen.ohneDoppelteGattungen(daten?.get(t).orEmpty().map { if (it.symbol in eigeneSymbole) it.copy(eigen = true) else it }) +
                eigene.filter { it.tag == t.toEpochDay() }
        }
    }
    val uri = appLinks()
    // Ergaenzt ein Schluessel des Nutzers den Kalender der Nasdaq?
    val settings by state.settingsFlow.collectAsStateWithLifecycle()
    val anbieter = Quartalszahlen.anbieter(settings.keyFinnhub, settings.keyAlphaVantage)

    LazyColumn(Modifier.fillMaxSize(), state = liste) {
        // Uhrzeiten am Heimatplatz, angegeben in der Zeitzone des Geraets (5.97)
        item(key = "zone") {
            Text(
                S.qzZeitzone(ZoneId.systemDefault().id), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        when {
            d == null -> item(key = "lade") { EmptyHint(S.loading) }
            d.values.all { it == null } && eigene.isEmpty() -> item(key = "fehlt") { EmptyHint(S.qzFehlt) }
            else -> for (t in tage) {
                val gesamt = jeTag[t].orEmpty()
                val zeigen = if (alle) Quartalszahlen.ordnen(gesamt) else Quartalszahlen.bekannte(gesamt)
                val fehlt = d[t] == null
                item(key = "tag-$t") { TagKopf(t, t == heute, zeigen.size, gesamt.size, fehlt) }
                if (zeigen.isEmpty() && !fehlt) item(key = "leer-$t") {
                    Text(
                        if (gesamt.isEmpty()) S.qzKeine else S.qzKeineBekannten(gesamt.size),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
                for (z in Quartalszahlen.Zeit.entries) {
                    val g = zeigen.filter { it.zeit == z }
                    if (g.isEmpty()) continue
                    // Haben alle Termine dieselbe Uhrzeit, steht sie in der Gruppenueberschrift, sonst je Zeile
                    val gemeinsam = Zahlenzeit.gemeinsam(g.map { uhrzeitVon(it) })
                    item(key = "zeit-$t-$z") {
                        PunktText(
                            listOfNotNull(S.qzZeit(z.ordinal), gemeinsam?.let { uhrKurz(it, t) }).joinToString(PUNKT),
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 2.dp),
                        )
                    }
                    items(g, key = { "z-$t-" + it.symbol }, contentType = { "termin" }) { e ->
                        TerminZeile(e, uhr = if (gemeinsam == null) uhrKurz(uhrzeitVon(e), t) else null) { waehlen(e) }
                    }
                }
            }
        }
        item(key = "hinweis") { HinweisInfo(S.nkQzHinweis) }
        item(key = "quelle") { QuellenAnhang(Quartalsquellen.liste(anbieter)) }
        item(key = "ende") { Spacer(Modifier.height(40.dp)) }
    }
}

// Kopf eines Tages: Wochentag und Datum, rechts wie viele Termine gezeigt sind.
@Composable
internal fun TagKopf(tag: LocalDate, heute: Boolean, gezeigt: Int, gesamt: Int, fehlt: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 24.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(wochentag(tag), style = MaterialTheme.typography.titleLarge, maxLines = 1, softWrap = false)
        Text(
            " " + Fmt.dayMonthNum(mittag(tag)) + if (heute) " · " + S.qzHeute else "",
            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false,
        )
        Spacer(Modifier.weight(1f))
        Text(
            when {
                fehlt -> S.qzTagFehlt
                gezeigt == gesamt -> gesamt.toString()
                else -> S.qzVon(gezeigt, gesamt)
            },
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false,
        )
    }
}

// Ein Termin: Name aus dem eigenen Verzeichnis, sonst von der Quelle; darunter Kuerzel, Uhrzeit, Boersenwert oder
// erwarteter Umsatz und Depot-Hinweis. Die Uhrzeit fehlt, wenn sie schon in der Gruppenueberschrift steht.
// Rechts vor den Zahlen der erwartete Gewinn je Aktie mit Vorjahr, danach der tatsaechliche mit der Erwartung.
@Composable
internal fun TerminZeile(e: Quartalszahlen.Termin, uhr: String? = null, onClick: () -> Unit) {
    val name = Quartalszahlen.ohneGattung(Symbols.find(e.symbol)?.name ?: e.name)
    val unter = listOfNotNull(
        e.symbol, uhr, if (e.geschaetzt) S.qzGeschaetzt else null,
        e.wert?.let { Fmt.dollarKompakt(it) } ?: e.umsatz?.let { S.qzUmsatz(Fmt.dollarKompakt(it)) }, if (e.eigen) S.qzEigen else null,
    ).joinToString(" · ")
    // Gewinn je Aktie immer mit zwei Stellen, auch unter einem Dollar
    fun usd(v: Double) = (if (v < 0) "−" else "") + Fmt.mitSymbol(Fmt.num(kotlin.math.abs(v), 2), "$")
    when {
        e.eps != null -> {
            val abw = de.troetenrepublik.app.engine.Gewinnueberraschung.abweichung(e.eps, e.epsErwartet) ?: e.abweichung ?: 0.0
            AssetRow(
                symbol = e.symbol, name = name, subtitle = unter, change = abw, changeText = usd(e.eps),
                rightCaption = e.epsErwartet?.let { S.qzErwartet(usd(it)) }, onClick = onClick,
            )
        }
        else -> AssetRow(
            symbol = e.symbol, name = name, subtitle = unter, rightPlain = e.epsErwartet?.let { usd(it) },
            rightCaption = e.epsVorjahr?.let { S.qzVorjahr(usd(it)) }, onClick = onClick,
        )
    }
}

// Quellen der Quartalstermine mit Nennungspflicht oder unklaren Bedingungen: die Zusatzanbieter, soweit sie genutzt werden.
internal object Quartalsquellen {
    fun liste(anbieter: String?): List<QuelleLink> = when (anbieter) {
        "finnhub" -> listOf(QuelleLink("Finnhub", "https://finnhub.io"))
        "alphavantage" -> listOf(QuelleLink("Alpha Vantage", "https://www.alphavantage.co"))
        else -> emptyList()
    }
}
