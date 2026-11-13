package de.troetenrepublik.app.plattform

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import de.troetenrepublik.app.HeatmapQuelle
import de.troetenrepublik.app.TroeteApp
import de.troetenrepublik.app.data.Appearance
import de.troetenrepublik.app.engine.Erscheinung
import de.troetenrepublik.app.data.WidgetDaten
import de.troetenrepublik.app.engine.WidgetWahl
import de.troetenrepublik.app.data.WidgetVorgabe
import de.troetenrepublik.app.market.Rss
import de.troetenrepublik.app.ui.UNIVERSEN
import de.troetenrepublik.app.ui.universumName
import de.troetenrepublik.app.engine.GlobusModus
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.ui.FloatingPill
import de.troetenrepublik.app.ui.HEATMAP_ZEITRAEUME
import de.troetenrepublik.app.ui.heatmapIndizes
import de.troetenrepublik.app.ui.heatmapQuelleTitel
import de.troetenrepublik.app.ui.NamenText
import de.troetenrepublik.app.ui.Radius
import de.troetenrepublik.app.ui.SectionHeader
import de.troetenrepublik.app.ui.ToggleRow
import de.troetenrepublik.app.ui.TroeteTheme

// Einstellung eines Widgets (seit 5.4 fuer alle ausser dem Schnellzugriff): Inhalt je nach Art, dazu Grund und
// Schrift. Jede Wahl gilt sofort; „Fertig“ zeichnet das Widget neu und bestaetigt es dem Startbildschirm.
// Bei gesperrter App zeigt die Seite nichts aus dem Depot, nur die Darstellung.
class WidgetEinstellung : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Ohne Bestaetigung legt der Startbildschirm ein neues Widget nicht an
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val state = (application as TroeteApp).container.state
        val art = Widgets.art(this, id)
        val c = this
        // Zurueck ohne „Fertig“: die Einstellungen gelten trotzdem, das Widget wird angelegt bzw. bleibt
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = fertig(id)
        })

        setContent {
            val settings by state.settingsFlow.collectAsStateWithLifecycle()
            val gesperrt by state.gesperrt.collectAsStateWithLifecycle()
            val dark = Erscheinung.dunkel(settings.appearance, isSystemInDarkTheme())
            val sepia = Erscheinung.sepia(settings.appearance)
            // Stand der Einstellungen; jede Aenderung wird sofort abgelegt
            var quelle by remember { mutableStateOf(Widgets.quelle(c, id)) }
            var einzel by remember { mutableStateOf(Widgets.einzel(c, id)) }
            var liste by remember { mutableStateOf(Widgets.liste(c, id)) }
            var name by remember { mutableStateOf(Widgets.name(c, id)) }
            var sortiert by remember { mutableStateOf(Widgets.sortiert(c, id)) }
            var betrag by remember { mutableStateOf(Widgets.betrag(c, id)) }
            var nurProzent by remember { mutableStateOf(Widgets.nurProzent(c, id)) }
            var zeitraum by remember { mutableStateOf(Widgets.zeitraum(c, id)) }
            var barometer by remember { mutableStateOf(Widgets.barometer(c, id)) }
            var globusModus by remember { mutableStateOf(Widgets.globusModus(c, id)) }
            var globusMitte by remember { mutableStateOf(Widgets.globusMitte(c, id)) }
            var grund by remember { mutableStateOf(Widgets.grund(c, id)) }
            var untertitel by remember { mutableStateOf(Widgets.untertitel(c, id)) }
            var dunkel by remember { mutableStateOf(Widgets.dunkleSchrift(c, id)) }
            var newsArt by remember { mutableStateOf(Widgets.newsArt(c, id)) }
            var variante by remember { mutableStateOf(Widgets.variante(c, id)) }
            var heatmapQuelle by remember { mutableStateOf(Widgets.heatmapQuelle(c, id)) }
            var heatmapZeit by remember { mutableStateOf(Widgets.heatmapZeitraum(c, id)) }
            var schnell by remember { mutableStateOf(Widgets.schnellZiele(c, id)) }
            var suche by remember { mutableStateOf("") }

            TroeteTheme(dark = dark, coloredChart = settings.coloredChart, sepia = sepia) {
                Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
                    Column(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(S.widgetEinstellung, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp))
                        if (gesperrt) Hinweis(S.widgetEinstellungGesperrt)
                        val auswahl = if (!gesperrt && (art == Widgets.ART_EINZEL || art == Widgets.ART_WERTE || art == Widgets.ART_SIGNALE || art == Widgets.ART_VOLATILITAET || art == Widgets.ART_WARNZEICHEN)) state.widgetEinzelAuswahl() else emptyList()

                        if (!gesperrt) when (art) {
                            Widgets.ART_DEPOT -> {
                                SectionHeader(S.widgetZeitraum)
                                Wahl(
                                    listOf(WidgetDaten.ZEIT_HEUTE to S.today, WidgetDaten.ZEIT_JAHR to S.widgetZeitJahr, WidgetDaten.ZEIT_KAUF to S.widgetZeitKauf),
                                    zeitraum,
                                ) { zeitraum = it; Widgets.zeitraumSetzen(c, id, it) }
                                ToggleRow(S.widgetNurProzent, nurProzent) { nurProzent = it; Widgets.nurProzentSetzen(c, id, it) }
                            }
                            Widgets.ART_MOVER -> {
                                SectionHeader(S.widgetAnzeige)
                                ToggleRow(S.widgetNurProzent, nurProzent) { nurProzent = it; Widgets.nurProzentSetzen(c, id, it) }
                            }
                            Widgets.ART_MAERKTE -> {
                                SectionHeader(S.widgetAnzeige)
                                ToggleRow(S.widgetBetrag, betrag) { betrag = it; Widgets.betragSetzen(c, id, it) }
                            }
                            Widgets.ART_STIMMUNG -> {
                                SectionHeader(S.widgetBarometer)
                                // Alle Barometer der App in deren Reihenfolge, zuletzt "Alle"
                                Wahl(
                                    WidgetWahl.STIMMUNG_WAHL.map { it to if (it == WidgetDaten.STIMMUNG_ALLE) S.widgetAlle else S.sbKachel(it) },
                                    barometer,
                                ) { barometer = it; Widgets.barometerSetzen(c, id, it) }
                            }
                            Widgets.ART_GLOBUS -> {
                                SectionHeader(S.widgetGlobusEinfaerbung)
                                Wahl(
                                    listOf(
                                        GlobusModus.BOERSE to S.globusBoerse, GlobusModus.WACHSTUM to S.globusWachstum,
                                        GlobusModus.AUSBLICK to S.globusAusblick, GlobusModus.INFLATION to S.globusInflation,
                                    ).map { it.first.name to it.second },
                                    globusModus.name,
                                ) { n -> globusModus = GlobusModus.valueOf(n); Widgets.globusModusSetzen(c, id, globusModus) }
                                SectionHeader(S.widgetGlobusMitte)
                                Wahl(
                                    listOf("europa" to S.widgetMitteEuropa, "amerika" to S.widgetMitteAmerika, "asien" to S.widgetMitteAsien),
                                    globusMitte,
                                ) { globusMitte = it; Widgets.globusMitteSetzen(c, id, it) }
                            }
                            Widgets.ART_NEWS -> {
                                SectionHeader(S.widgetNewsInhalt)
                                val feeds = remember(settings.newsFeedKategorien) { state.newsFeeds(settings) }
                                Wahl(
                                    listOf(WidgetDaten.NEWS_WICHTIG to S.widgetNewsWichtig, WidgetDaten.NEWS_ALLE to S.widgetNewsAlle, WidgetDaten.NEWS_GEMERKT to S.newsAnbieter(Rss.Anbieter.GEMERKT)) +
                                        feeds.map { WidgetDaten.newsFeed(it.kennung) to S.widgetNewsFeed(it.name) } +
                                        listOfNotNull(newsArt.takeIf { it.startsWith(WidgetDaten.NEWS_ANBIETER) }?.let { a ->
                                            a to (runCatching { S.newsAnbieter(Rss.Anbieter.valueOf(a.removePrefix(WidgetDaten.NEWS_ANBIETER))) }.getOrDefault(a))
                                        }) +
                                        (WidgetDaten.NEWS_DEPOT to S.widgetNewsDepot),
                                    newsArt,
                                ) { newsArt = it; Widgets.newsArtSetzen(c, id, it) }
                            }
                            Widgets.ART_COPYTRADING, Widgets.ART_ORDERS, Widgets.ART_PROGNOSE, Widgets.ART_TERMINE, Widgets.ART_AKTIENFINDER -> {
                                SectionHeader(S.widgetSeite)
                                val optionen: List<Pair<String, String>> = when (art) {
                                    Widgets.ART_COPYTRADING -> listOf(
                                        WidgetVorgabe.COPY_GEFOLGT to S.copyFollowing, WidgetVorgabe.COPY_POLITIK to S.copyPolitics + " · " + S.copyCongress,
                                        WidgetVorgabe.COPY_SENAT to S.copyPolitics + " · " + S.copySenate, WidgetVorgabe.COPY_REGIERUNG to S.copyPolitics + " · " + S.copyGovernment,
                                        WidgetVorgabe.COPY_VORSTAENDE to S.copyExecutives, WidgetVorgabe.COPY_INVESTOREN to S.copyInvestors, WidgetVorgabe.COPY_STRATEGIEN to S.copyStrategies,
                                    )
                                    Widgets.ART_ORDERS -> listOf("" to S.orders + ", " + S.savingsPlans, WidgetVorgabe.ORDERS_ORDERS to S.orders, WidgetVorgabe.ORDERS_PLAENE to S.savingsPlans)
                                    Widgets.ART_PROGNOSE -> listOf(
                                        WidgetVorgabe.PM_POLYMARKET to "Polymarket", WidgetVorgabe.PM_KALSHI to "Kalshi", WidgetVorgabe.PM_MANIFOLD to "Manifold", WidgetVorgabe.PM_METACULUS to "Metaculus", WidgetVorgabe.PM_GROSS to S.pmGross, WidgetVorgabe.PM_WETTEN to S.pmMyBets,
                                    )
                                    Widgets.ART_TERMINE -> listOf(
                                        WidgetVorgabe.TERMIN_DIVIDENDEN to S.nkDivTitel, WidgetVorgabe.TERMIN_WIRTSCHAFT to S.nkWiTitel,
                                        WidgetVorgabe.TERMIN_QUARTALE to S.qzTitel, WidgetVorgabe.TERMIN_IPO to S.ipos,
                                    )
                                    else -> UNIVERSEN.map { it to universumName(it) }
                                }
                                val standard = when (art) {
                                    Widgets.ART_COPYTRADING -> WidgetVorgabe.COPY_GEFOLGT
                                    Widgets.ART_PROGNOSE -> WidgetVorgabe.PM_POLYMARKET
                                    Widgets.ART_TERMINE -> WidgetVorgabe.TERMIN_DIVIDENDEN
                                    Widgets.ART_AKTIENFINDER -> ""
                                    else -> ""
                                }
                                val gewaehlt = variante ?: standard
                                // Ohne Wahl zeigt der Aktienfinder das zuletzt geladene Universum
                                Wahl(
                                    if (art == Widgets.ART_AKTIENFINDER) listOf("" to S.widgetAktuellesUniversum) + optionen else optionen,
                                    gewaehlt,
                                ) { k -> variante = k.takeIf { it.isNotBlank() }; Widgets.varianteSetzen(c, id, variante) }
                            }
                            // Seit 0.78: der Wert, dessen Signale oder Volatilitaet das Widget zeigt (ohne Wahl der Standardwert)
                            Widgets.ART_SIGNALE, Widgets.ART_VOLATILITAET, Widgets.ART_WARNZEICHEN -> {
                                SectionHeader(S.widgetEinzelWahl)
                                if (auswahl.isEmpty()) Hinweis(S.widgetEinstellungLeer)
                                WertWahl(state, auswahl, variante ?: state.widgetEinzelStandard()) { w -> variante = w; Widgets.varianteSetzen(c, id, w) }
                            }
                            Widgets.ART_INDEXBEWEGER -> {
                                SectionHeader(S.widgetQuelle)
                                val quellen = remember { listOf(HeatmapQuelle.MARKT) + heatmapIndizes() }
                                Wahl(quellen.map { it to heatmapQuelleTitel(it) }, variante ?: HeatmapQuelle.MARKT) { q -> variante = q; Widgets.varianteSetzen(c, id, q) }
                            }
                            // Seit 0.77: die Branche, deren beste Aktien das Widget zeigt (ohne Wahl die staerkste des Aktienfinders)
                            Widgets.ART_BRANCHE -> {
                                SectionHeader(S.afAnsichtBranchen)
                                val branchen = remember {
                                    de.troetenrepublik.app.AktienfinderSpeicher.daten.map { it.branche }.filter { it.isNotBlank() }.distinct().map { it to S.industryName(it) }.sortedBy { it.second }
                                }
                                val gewaehlt = variante.orEmpty()
                                val optionen = listOf("" to S.widgetBrancheBeste) + branchen + listOfNotNull(gewaehlt.takeIf { g -> g.isNotBlank() && branchen.none { it.first == g } }?.let { it to S.industryName(it) })
                                Wahl(optionen, gewaehlt) { k -> variante = k.takeIf { it.isNotBlank() }; Widgets.varianteSetzen(c, id, variante) }
                            }
                            Widgets.ART_HEATMAP -> {
                                SectionHeader(S.widgetQuelle)
                                val quellen = remember { listOf(HeatmapQuelle.MARKT, HeatmapQuelle.DEPOT, HeatmapQuelle.WATCHLIST) + heatmapIndizes() }
                                Wahl(quellen.map { it to heatmapQuelleTitel(it) }, heatmapQuelle) { heatmapQuelle = it; Widgets.heatmapQuelleSetzen(c, id, it) }
                                SectionHeader(S.widgetZeitraum)
                                Wahl(HEATMAP_ZEITRAEUME.mapIndexed { i, r -> i.toString() to S.rangeCaption(r) }, heatmapZeit.toString()) {
                                    heatmapZeit = it.toIntOrNull() ?: 0; Widgets.heatmapZeitraumSetzen(c, id, heatmapZeit)
                                }
                            }
                            Widgets.ART_SCHNELL -> {
                                SectionHeader(S.widgetSchnellWahl)
                                Hinweis(S.widgetSchnellHinweis)
                                val namen = state.widgetDaten()?.schnellNamen.orEmpty()
                                WidgetDaten.SCHNELL_AUSWAHL.forEach { ziel ->
                                    val an = ziel in schnell
                                    fun umschalten() {
                                        // Immer genau vier Kacheln: eine neue Wahl verdraengt die aelteste, Abwaehlen allein geht nicht
                                        if (an) return
                                        Widgets.schnellZieleSetzen(c, id, (schnell + ziel).takeLast(4))
                                        schnell = Widgets.schnellZiele(c, id)
                                    }
                                    Row(
                                        Modifier.fillMaxWidth().clickable { umschalten() }.padding(horizontal = 12.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Checkbox(checked = an, onCheckedChange = { umschalten() })
                                        NamenText(namen[ziel] ?: ziel, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                            Widgets.ART_EINZEL -> {
                                SectionHeader(S.widgetEinzelWahl)
                                if (auswahl.isEmpty()) Hinweis(S.widgetEinstellungLeer)
                                // Suche im Katalog (5.94): jede Aktie, nicht nur Depot und Watchlist
                                OutlinedTextField(
                                    value = suche,
                                    onValueChange = { suche = it.take(40) },
                                    label = { Text(S.widgetEinzelSuche) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(Radius.field),
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                                )
                                val gewaehlt = einzel ?: state.widgetDaten()?.einzelStandard.orEmpty()
                                val lokal = remember(suche) {
                                    if (suche.isBlank()) emptyList()
                                    else de.troetenrepublik.app.market.Symbols.search(suche).take(12).map { it.symbol to it.name.ifBlank { it.symbol } }
                                }
                                // Wie die Suche der App (5.95): nach dem Katalog auch die Yahoo-Suche, sonst fehlen Werte
                                // ausserhalb des Katalogs (etwa Ferrexpo, FXPO.L)
                                var online by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
                                LaunchedEffect(suche) {
                                    online = emptyList()
                                    if (suche.trim().length < 2) return@LaunchedEffect
                                    kotlinx.coroutines.delay(350)
                                    val bekannt = lokal.map { it.first }.toSet()
                                    online = runCatching { state.market.search(suche.trim()) }
                                        .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                                        .getOrDefault(emptyList())
                                        .distinctBy { it.symbol }.filter { it.symbol !in bekannt }.take(12)
                                        .map { it.symbol to it.name.ifBlank { it.symbol } }
                                }
                                val treffer = lokal + online
                                // Ein gewaehlter Wert ausserhalb von Depot und Watchlist bleibt sichtbar
                                val ausserhalb = listOfNotNull(
                                    gewaehlt.takeIf { g -> g.isNotBlank() && auswahl.none { it.first == g } && treffer.none { it.first == g } }
                                        ?.let { g -> g to (de.troetenrepublik.app.market.Symbols.find(g)?.name ?: g) },
                                )
                                Wahl(if (suche.isBlank()) ausserhalb + auswahl else treffer, gewaehlt) { einzel = it; Widgets.einzelSetzen(c, id, it) }
                                ToggleRow(S.widgetBetrag, betrag) { betrag = it; Widgets.betragSetzen(c, id, it) }
                            }
                            Widgets.ART_WERTE -> {
                                SectionHeader(S.widgetQuelle)
                                Wahl(
                                    listOf(Widgets.QUELLE_WATCHLIST to S.widgetQuelleWatchlist, Widgets.QUELLE_DEPOT to S.widgetQuelleDepot, Widgets.QUELLE_LISTE to S.widgetQuelleListe),
                                    quelle,
                                ) { quelle = it; Widgets.quelleSetzen(c, id, it) }
                                if (quelle == Widgets.QUELLE_LISTE) {
                                    OutlinedTextField(
                                        value = name,
                                        onValueChange = { name = it.take(40); Widgets.nameSetzen(c, id, name) },
                                        label = { Text(S.widgetListenName) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(Radius.field),
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                                    )
                                    Hinweis(S.widgetListeWahl)
                                    if (auswahl.isEmpty()) Hinweis(S.widgetEinstellungLeer)
                                    auswahl.forEach { (symbol, titel) ->
                                        val an = symbol in liste
                                        Row(
                                            Modifier.fillMaxWidth().clickable {
                                                liste = if (an) liste - symbol else liste + symbol
                                                Widgets.listeSetzen(c, id, liste)
                                            }.padding(horizontal = 12.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Checkbox(checked = an, onCheckedChange = {
                                                liste = if (an) liste - symbol else liste + symbol
                                                Widgets.listeSetzen(c, id, liste)
                                            })
                                            NamenText(titel, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                                SectionHeader(S.widgetAnzeige)
                                ToggleRow(S.widgetSortiert, sortiert, null) { sortiert = it; Widgets.sortiertSetzen(c, id, it) }
                                ToggleRow(S.widgetBetrag, betrag) { betrag = it; Widgets.betragSetzen(c, id, it) }
                            }
                        }

                        SectionHeader(S.widgetDarstellung)
                        Hinweis(S.widgetGrund)
                        Wahl(
                            listOf(Widgets.GRUND_VOLL to S.widgetGrundVoll, Widgets.GRUND_HALB to S.widgetGrundHalb, Widgets.GRUND_LEER to S.widgetGrundLeer),
                            grund,
                        ) { grund = it; Widgets.grundSetzen(c, id, it) }
                        Hinweis(S.widgetSchrift)
                        Wahl(listOf("hell" to S.widgetSchriftHell, "dunkel" to S.widgetSchriftDunkel), if (dunkel) "dunkel" else "hell") {
                            dunkel = it == "dunkel"; Widgets.dunkleSchriftSetzen(c, id, dunkel)
                        }
                        if (art != Widgets.ART_SCHNELL) ToggleRow(S.widgetUntertitel, untertitel) { untertitel = it; Widgets.untertitelSetzen(c, id, it) }

                        Spacer(Modifier.height(16.dp))
                        FloatingPill(label = S.done, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) { fertig(id) }
                        Spacer(Modifier.height(40.dp))
                    }
                }
            }
        }
    }

    @Composable
    private fun Hinweis(text: String) {
        Text(
            text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }

    // Wert waehlen: Suche im Katalog und bei Yahoo wie beim Einzelwert, darunter Depot und Watchlist.
    @Composable
    private fun WertWahl(state: de.troetenrepublik.app.AppState, auswahl: List<Pair<String, String>>, gewaehlt: String, onWahl: (String) -> Unit) {
        var suche by remember { mutableStateOf("") }
        OutlinedTextField(
            value = suche,
            onValueChange = { suche = it.take(40) },
            label = { Text(S.widgetEinzelSuche) },
            singleLine = true,
            shape = RoundedCornerShape(Radius.field),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )
        val lokal = remember(suche) {
            if (suche.isBlank()) emptyList()
            else de.troetenrepublik.app.market.Symbols.search(suche).take(12).map { it.symbol to it.name.ifBlank { it.symbol } }
        }
        var online by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
        LaunchedEffect(suche) {
            online = emptyList()
            if (suche.trim().length < 2) return@LaunchedEffect
            kotlinx.coroutines.delay(350)
            val bekannt = lokal.map { it.first }.toSet()
            online = runCatching { state.market.search(suche.trim()) }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                .getOrDefault(emptyList())
                .distinctBy { it.symbol }.filter { it.symbol !in bekannt }.take(12)
                .map { it.symbol to it.name.ifBlank { it.symbol } }
        }
        val treffer = lokal + online
        val ausserhalb = listOfNotNull(
            gewaehlt.takeIf { g -> g.isNotBlank() && auswahl.none { it.first == g } && treffer.none { it.first == g } }
                ?.let { g -> g to (de.troetenrepublik.app.market.Symbols.find(g)?.name ?: g) },
        )
        Wahl(if (suche.isBlank()) ausserhalb + auswahl else treffer, gewaehlt, onWahl)
    }

    // Eine Auswahl aus mehreren Moeglichkeiten (Schluessel, Text).
    @Composable
    private fun Wahl(optionen: List<Pair<String, String>>, gewaehlt: String, onWahl: (String) -> Unit) {
        optionen.forEach { (schluessel, text) ->
            Row(
                Modifier.fillMaxWidth().clickable { onWahl(schluessel) }.padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = schluessel == gewaehlt, onClick = { onWahl(schluessel) })
                NamenText(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    // Widget neu zeichnen, Kurse und Verlauf nachladen und die Einstellung bestaetigen.
    private fun fertig(id: Int) {
        Widgets.zeichnen(this)
        runCatching { (application as TroeteApp).container.state.widgetsNachladen() }
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        finish()
    }
}
