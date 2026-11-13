package de.troetenrepublik.app.ui

import kotlinx.coroutines.flow.first

import androidx.compose.runtime.snapshotFlow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.ChartSprung
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.plattform.KnowledgeScreen
import de.troetenrepublik.app.plattform.ZurueckGeste
import kotlinx.coroutines.CancellationException

// Keine Tab-Leiste: Portfolio, Cash und Profil liegen nebeneinander auf der Startseite (siehe HomeScreen).
// Alle Unterseiten liegen in einem eigenen Stapel darueber, kommen von unten und lassen sich nach unten wegwischen.
// Die Startseite bleibt aufgebaut, damit sie beim Wegwischen sofort da ist und die Seite dem Finger folgt.
// Der Stapel reicht ueber den ganzen Bildschirm; jede Seite haelt die Systemleisten selbst frei (SeitenRahmen).
@Composable
fun AppNav(state: AppState) {
    // Der Sperrbildschirm liegt ueber der App; Seiten und Eingaben darunter bleiben erhalten
    val gesperrt by state.gesperrt.collectAsStateWithLifecycle()
    // Webseiten oeffnen im Betrachter der App (AppLinks, 0.68)
    AppLinks { androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        // Gesperrt bleibt die App aufgebaut, ist aber fuer Vorlesen und Tastatur unerreichbar
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize().then(if (gesperrt) Modifier.clearAndSetSemantics {}.focusProperties { canFocus = false } else Modifier),
        ) { AppNavInhalt(state) }
        if (gesperrt) SperrBildschirm(state)
    } }
}

@Composable
private fun AppNavInhalt(state: AppState) {
    val stapel = rememberSeitenstapel()
    // Blaetter der Startseite kommen nach einer aus ihnen geoeffneten Seite wieder
    CompositionLocalProvider(LocalEbene provides stapel.heimEbene) { AppNavStapel(state, stapel) }
}

@Composable
private fun AppNavStapel(state: AppState, stapel: Seitenstapel) {
    val zustaende = rememberSaveableStateHolder()
    val snackbar = remember { SnackbarHostState() }
    val toast by state.toast.collectAsStateWithLifecycle()
    val saveError by state.store.error.collectAsStateWithLifecycle()
    var transfer by rememberSaveable { mutableStateOf<String?>(null) }
    val sperrArt by state.sperrArt.collectAsStateWithLifecycle()
    de.troetenrepublik.app.plattform.VorschauSchutz(sperrArt != null)
    // Screenshots in der ganzen App sperren, wenn im Profil eingestellt
    val einstellungen by state.settingsFlow.collectAsStateWithLifecycle()
    de.troetenrepublik.app.plattform.Bildschutz(einstellungen.screenshots == de.troetenrepublik.app.data.Bildschirmfotos.GESPERRT)
    var analytics by rememberSaveable { mutableStateOf(false) }
    // Seite und Werkzeug von Analytics, damit das Blatt nach einer daraus geoeffneten Seite genauso wiederkommt
    var analyticsSeite by rememberSaveable { mutableStateOf(0) }
    var analyticsWerkzeug by rememberSaveable { mutableStateOf<String?>(null) }
    val analyticsRueck = rememberWiederkehr()

    LaunchedEffect(toast) {
        toast?.let { snackbar.showSnackbar(it); state.clearToast() }
    }
    // [0.78] Abgleich mit dem Flex-Bericht: Ablauf, Vorschau und Bestaetigung
    FlexAbgleichDialog(state) { stapel.oeffnen("zugaenge") }
    LaunchedEffect(saveError) {
        saveError?.let { snackbar.showSnackbar(S.error(it, "")) }
    }

    // „derivat:<id>“ oeffnet die Derivateseite (Optionen aus dem Copytrading)
    val openInstrument: (String) -> Unit = { if (it.startsWith(SEITEN_PRAEFIX)) stapel.oeffnen(it.removePrefix(SEITEN_PRAEFIX)) else if (it.startsWith(WIRKUNG_PRAEFIX)) stapel.oeffnen("wirkung/" + it.removePrefix(WIRKUNG_PRAEFIX)) else if (it.startsWith(DERIVAT_ROUTE)) stapel.oeffnen("derivative/" + it.removePrefix(DERIVAT_ROUTE)) else stapel.oeffnen("instrument/$it") }
    val openDerivative: (String) -> Unit = { stapel.oeffnen("derivative/$it") }

    // Tipp auf ein Widget: die Seite oeffnen, mit App-Sperre erst nach dem Entsperren
    val sprungziel by state.sprungziel.collectAsStateWithLifecycle()
    val gesperrt by state.gesperrt.collectAsStateWithLifecycle()
    LaunchedEffect(sprungziel, gesperrt) {
        val z = sprungziel ?: return@LaunchedEffect
        if (gesperrt) return@LaunchedEffect
        state.sprungzielErledigt()
        analytics = false
        analyticsRueck.vergessen()
        // Analytics ist ein Blatt ueber der Startseite, alles andere eine Seite des Stapels
        // Ein Widget-Tipp beginnt frisch: offene Seiten schliessen, sonst bleibt z. B. ein Laenderblatt (eigenes Fenster) ueber dem Ziel
        val oben = stapel.oberste()
        stapel.offen.filter { it !== oben || it.route != z }.forEach { stapel.schliessen(it) }
        if (z == de.troetenrepublik.app.data.WidgetZiel.ANALYTICS) analytics = true
        // analytics/<Werkzeug>: Analytics mit geoeffnetem Werkzeug (Haus-Rangliste, Portfolio Performance)
        else if (z.startsWith(de.troetenrepublik.app.data.WidgetZiel.ANALYTICS + "/")) { analytics = true; analyticsSeite = 2; analyticsWerkzeug = z.substringAfter('/') }
        else if (z.startsWith("copytrading/")) { CopyStart.setzen(z); stapel.oeffnen("copytrading") }
        else if (oben?.route != z) stapel.oeffnen(z)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar, Modifier.navigationBarsPadding()) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().onSizeChanged { stapel.hoehe = it.height.toFloat() }) {
            // Die Startseite bleibt immer aufgebaut; verdeckt wird sie nicht gezeichnet. Sie haelt die Systemleisten
            // frei und schneidet ab, was darueber hinausragt: Die Kopfzeile scrollt unter der Statusleiste weg, statt
            // in sie hinein.
            val startSichtbar = stapel.sichtbar(-1)
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (startSichtbar) 1f else 0f }
                    .then(if (stapel.leer) Modifier else Modifier.clearAndSetSemantics {})
                    .systemBarsPadding()
                    .clipToBounds(),
            ) {
                HomeScreen(
                    state = state,
                    onOpen = openInstrument,
                    onOpenDerivative = openDerivative,
                    onOpenActivity = { stapel.oeffnen("activity") },
                    onSearch = { stapel.oeffnen("search") },
                    onOpenOrders = { stapel.oeffnen("orders") },
                    onOpenImport = { stapel.oeffnen("import") },
                    onOpenNews = { stapel.oeffnen("news") },
                    onOpenCopy = { stapel.oeffnen(CopyRouten.START) },
                    onOpenPredict = { stapel.oeffnen(PmRouten.START) },
                    onOpenKnowledge = { stapel.oeffnen("knowledge") },
                    onOpenAnalytics = { analytics = true },
                    onOpenMaerkte = { stapel.oeffnen(MAERKTE_ROUTE) },
                    onOpenFinder = { stapel.oeffnen(AKTIENFINDER_ROUTE) },
                    onTransfer = { transfer = it },
                    onProfileNavigate = { dest ->
                        // Vom Profil aus wie vom Depot aus: Analytics als Blatt, alles andere als Seite von unten
                        when (dest) {
                            "analytics" -> analytics = true
                            else -> stapel.oeffnen(dest)
                        }
                    },
                )
            }

            // Zurueck (Taste oder Geste des Systems): die oberste Seite gleitet hinaus. Mit Vorschau folgt sie dem
            // Fortschritt der Geste; ohne Vorschau laeuft sie wie mit dem Pfeil hinaus. Angemeldet nach der Startseite
            // und vor den Seiten: Seiten mit eigenem Zurueck (Import, Kurzanleitung, Finanzwissen) gehen vor, das
            // Zurueck der Startseite (vom Profil zur Herkunft) kommt erst bei leerem Stapel an die Reihe.
            ZurueckGeste(enabled = !stapel.leer) { fortschritt ->
                val e = stapel.oberste() ?: return@ZurueckGeste
                try {
                    fortschritt.collect { stapel.vorschau(e, it) }
                    stapel.schliessen(e)
                } catch (abbruch: CancellationException) {
                    stapel.vorschauAbbrechen(e)
                }
            }

            // Unter einer Seite aus einem Blatt der Startseite liegt das Bild des Blatts fertig bereit und wird erst
            // gezeichnet, wenn die Seite sich bewegt. So folgt die Seite schon beim Ansetzen der Geste dem Finger.
            if (stapel.heimBilder.isNotEmpty()) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (stapel.aufgedeckt(-1)) 1f else 0f }) {
                    stapel.heimBilder.forEach { (k, bild) -> key(k) { bild() } }
                }
            }

            val oberste = stapel.oberste()
            stapel.eintraege.forEachIndexed { index, eintrag ->
                key(eintrag.id) {
                    zustaende.SaveableStateProvider(eintrag.id) {
                        SeitenRahmen(
                            eintrag = eintrag,
                            ebene = remember(eintrag) { stapel.ebene(eintrag) },
                            sichtbar = stapel.sichtbar(index),
                            oben = eintrag === oberste,
                            grund = MaterialTheme.colorScheme.background,
                        ) {
                            SeitenInhalt(
                                route = eintrag.route,
                                state = state,
                                oeffnen = { stapel.oeffnen(it) },
                                zurueck = { stapel.schliessen(eintrag) },
                                openInstrument = openInstrument,
                                openDerivative = openDerivative,
                                einzahlen = { transfer = "deposit" },
                            )
                        }
                    }
                    // Gespeicherter Zustand einer Seite (Scrolllage, Eingaben) endet mit ihr
                    DisposableEffect(Unit) {
                        onDispose { if (stapel.eintraege.none { it.id == eintrag.id }) zustaende.removeState(eintrag.id) }
                    }
                }
            }
        }
    }

    transfer?.let { initial ->
        TransferSheet(state = state, onDismiss = { transfer = null }, initial = initial.ifBlank { null })
    }
    // Dringende Hinweise des Quellen-Buendels (seit 5.92)
    QuellenHinweisDialog()
    WatchlistenWahlDialog(state)
    val einstellungenAlter by state.settingsFlow.collectAsStateWithLifecycle()
    AppAlterDialog(!de.troetenrepublik.app.plattform.Plattform.aktualisierbar || einstellungenAlter.updateHinweis)
    if (analytics) {
        WiederkehrBlatt("analytics", analyticsRueck) { AnalyticsSheet(
            state,
            onDismiss = { analytics = false; analyticsSeite = 0; analyticsWerkzeug = null; analyticsRueck.vergessen() },
            onOpen = { analyticsRueck.verlassen(); openInstrument(it) },
            seite = analyticsSeite, onSeite = { analyticsSeite = it },
            werkzeug = analyticsWerkzeug, onWerkzeug = { analyticsWerkzeug = it },
            sofort = analyticsRueck.sofort,
        ) }
        BlattBild(analyticsRueck, "analytics") { AnalyticsBild(state, analyticsSeite, analyticsWerkzeug) }
    }
}

// Inhalt einer Seite des Stapels nach ihrer Route (z. B. "instrument/AAPL").
@Composable
private fun SeitenInhalt(
    route: String,
    state: AppState,
    oeffnen: (String) -> Unit,
    zurueck: () -> Unit,
    openInstrument: (String) -> Unit,
    openDerivative: (String) -> Unit,
    einzahlen: () -> Unit,
) {
    if (braucht13107(route)) {
        val s by state.settingsFlow.collectAsStateWithLifecycle()
        if (!s.ok13107) { Bestaetigung13107(state, zurueck); return }
    }
    when {
        route == "search" -> SearchScreen(state = state, onOpen = openInstrument, onBack = zurueck, onDiscover = oeffnen)
        // activity/handel: Aktivitaet mit dem Filter Handel
        route == "activity" || route == AKTIVITAET_HANDEL -> ActivityScreen(state = state, onOpen = openInstrument, onBack = zurueck, onOpenDerivative = openDerivative, onDeposit = einzahlen, startFilter = if (route == AKTIVITAET_HANDEL) 1 else 0)
        route == "plans" -> OrdersScreen(state, onBack = zurueck, onOpen = openInstrument, onOpenDerivative = openDerivative, onSearch = { oeffnen("search") }, startSeite = 1)
        route == "orders" -> OrdersScreen(state, onBack = zurueck, onOpen = openInstrument, onOpenDerivative = openDerivative, onSearch = { oeffnen("search") })
        route == "alerts" -> MitteilungenScreen(state, onBack = zurueck, oeffnen = oeffnen)
        route == "tax" -> TaxScreen(state, onBack = zurueck)
        route == "documents" -> DocumentsScreen(state, onBack = zurueck)
        route == "import" -> ImportScreen(state, onBack = zurueck, onOpen = openInstrument)
        route == "knowledge" -> {
            LaunchedEffect(Unit) { runCatching { state.lernbeispieleLaden() } }
            KnowledgeScreen(onBack = zurueck)
        }
        // depotverlauf/verkaeufe: Depotverlauf am Abschnitt „Abgeschlossene Verkaeufe“
        route == "depotverlauf" || route == DEPOTVERLAUF_VERKAEUFE -> DepotVerlaufScreen(state, onBack = zurueck, onOpen = openInstrument, startAbschnitt = if (route == DEPOTVERLAUF_VERKAEUFE) "markt" else null)
        route == "news" || route.startsWith("nachricht/") -> NewsScreen(
            state, onBack = zurueck, onZugaenge = { oeffnen("zugaenge") }, onRueckblick = { oeffnen("rueckblick") },
            onOpen = { oeffnen("instrument/$it") }, startKennung = route.substringAfter("nachricht/", "").ifBlank { null },
            onMitteilungen = { oeffnen(MITTEILUNGEN) }, oeffnen = oeffnen,
        )
        route == "zugaenge" -> ZugaengeScreen(state, onBack = zurueck, oeffnen = oeffnen)
        route == Anstossen.ROUTE -> AnstossenScreen(state, onBack = zurueck, oeffnen = oeffnen)
        route == "sperre" -> SperreScreen(state, onBack = zurueck)
        route == "screenshots" -> ScreenshotsScreen(state, onBack = zurueck)
        route == ERSCHEINUNGSBILD -> ErscheinungsbildScreen(state, onBack = zurueck)
        route == "widgets" -> WidgetsScreen(state, onBack = zurueck)
        route == "rueckblick" -> RueckblickScreen(state, onBack = zurueck, onOpen = openInstrument)
        route == MITTEILUNGEN -> MitteilungenScreen(state, onBack = zurueck, oeffnen = oeffnen)
        // Heatmap allein: Ziel der Widgets „Schnellzugriff“ und „Heatmap“ (heatmap/<Quelle>/<Zeitraum>)
        route == "heatmap" || route.startsWith("heatmap/") -> {
            val teile = route.split('/')
            HeatmapScreen(
                state, onBack = zurueck, onOpen = openInstrument,
                startQuelle = teile.getOrNull(1)?.ifBlank { null }, startZeitraum = teile.getOrNull(2)?.toIntOrNull() ?: 0,
            )
        }
        route == MAERKTE_ROUTE -> MaerkteScreen(state, onBack = zurueck, onOpen = openInstrument, oeffnen = oeffnen)
        // Ziele der Kategorie-Widgets: maerkte/<Reiter> (trends, bewegungen, ausblick)
        route.startsWith("$MAERKTE_ROUTE/") -> MaerkteScreen(state, onBack = zurueck, onOpen = openInstrument, oeffnen = oeffnen, startReiter = route.substringAfter('/').substringBefore('/'), startAbschnitt = route.substringAfter('/').substringAfter('/', "").ifBlank { null })
        // Ziele der Widgets „Stimmung“ (Reiter Stimmung) und „Globus“ (Reiter Globus)
        route == "marktphase" -> MaerkteScreen(state, onBack = zurueck, onOpen = openInstrument, oeffnen = oeffnen, startReiter = "stimmung")
        route == "globus/engpaesse" -> MaerkteScreen(state, onBack = zurueck, onOpen = openInstrument, oeffnen = oeffnen, startReiter = "globus", startEbene = "ENGPAESSE")
        route == "globus" -> MaerkteScreen(state, onBack = zurueck, onOpen = openInstrument, oeffnen = oeffnen, startReiter = "globus")
        // [0.77] globus/<Ebene>[/<Abschnitt>]: Ebene des Globus (wachstum, inflation, engpaesse, ...), Abschnitt darunter (weltlage)
        route.startsWith("globus/") -> route.split('/').let { t ->
            MaerkteScreen(state, onBack = zurueck, onOpen = openInstrument, oeffnen = oeffnen, startReiter = "globus", startEbene = t.getOrNull(1), startAbschnitt = t.getOrNull(2))
        }
        // [0.73] Laenderblatt eines Landes (alpha-2), Lage, Wirkungsnetz
        route.startsWith("land/") -> MaerkteScreen(state, onBack = zurueck, onOpen = openInstrument, oeffnen = oeffnen, startReiter = "globus", startLand = route.removePrefix("land/"))
        route == "lagebild" -> MaerkteScreen(state, onBack = zurueck, onOpen = openInstrument, oeffnen = oeffnen, startReiter = "lage")
        route == "wirkung" -> WirkungsnetzScreen(state, null, onBack = zurueck, onOpen = openInstrument, oeffnen = oeffnen)
        route.startsWith("wirkung/") -> WirkungsnetzScreen(state, route.removePrefix("wirkung/"), onBack = zurueck, onOpen = openInstrument, oeffnen = oeffnen)
        route == "netz" -> VerflechtungenScreen(state, null, onBack = zurueck, onOpen = openInstrument)
        route.startsWith("netz/") -> VerflechtungenScreen(state, route.removePrefix("netz/"), onBack = zurueck, onOpen = openInstrument)
        route == AKTIENFINDER_ROUTE -> AktienfinderScreen(state, onBack = zurueck, onOpen = openInstrument)
        route == RADAR_ROUTE -> { remember(route) { finderRadarOeffnen(); route }; AktienfinderScreen(state, onBack = zurueck, onOpen = openInstrument) }
        route.startsWith("chart/") -> ChartSprung.lesen(route).let { (kuerzel, muster) -> ProfiChartScreen(state, kuerzel, onBack = zurueck, sprung = muster) }
        route.startsWith("kennzahlvergleich/") -> KennzahlVergleichScreen(state, route.removePrefix("kennzahlvergleich/"), onBack = zurueck, onOpen = openInstrument)
        route == CopyRouten.START -> CopytradingScreen(state, onBack = zurueck, oeffnen = oeffnen)
        route == PmRouten.START -> PrognoseScreen(state, onBack = zurueck, oeffnen = oeffnen)
        route.startsWith("pm-e/") -> {
            val (quelle, id) = PmRouten.teile(route)
            PrognoseEreignisScreen(state, quelle, id, onBack = zurueck)
        }
        route.startsWith("copy-k/") -> KongressPersonScreen(state, CopyRouten.schluessel(route), onBack = zurueck, onOpen = openInstrument)
        route.startsWith("copy-i/") -> InvestorScreen(state, route.removePrefix("copy-i/"), onBack = zurueck, onOpen = openInstrument)
        route.startsWith("copy-n/") -> SenatorScreen(state, CopyRouten.schluessel(route), onBack = zurueck, onOpen = openInstrument)
        route.startsWith("copy-r/") -> RegierungScreen(state, route.removePrefix("copy-r/"), onBack = zurueck, onOpen = openInstrument, oeffnen = oeffnen)
        route.startsWith("copy-o/") -> OgePersonScreen(state, route.removePrefix("copy-o/"), onBack = zurueck, onOpen = openInstrument)
        route == "selbstabruf" -> SelbstabrufScreen(state, onBack = zurueck)
        route == DATENQUELLEN -> DatenquellenScreen(onBack = zurueck)
        route == LIZENZEN -> LizenzenScreen(onBack = zurueck)
        route == PROFIL_ERWEITERT || route == PROFIL_ERWEITERT_ZINS -> ProfilErweitertScreen(state, onBack = zurueck, zuZins = route == PROFIL_ERWEITERT_ZINS, oeffnen = oeffnen)
        route == CopyRouten.TRUTH -> TruthScreen(state, onBack = zurueck)
        route.startsWith("copy-v/") -> PortfolioVergleichScreen(state, route.removePrefix("copy-v/"), onBack = zurueck)
        route == CopyRouten.GLOCKEN -> MitteilungenScreen(state, onBack = zurueck, zuGlocken = true, oeffnen = oeffnen)
        route == CopyRouten.KONGRESS_KAUFT -> KongressKauftScreen(state, onBack = zurueck, onOpen = openInstrument)
        route == CopyRouten.RANGLISTE -> RanglisteScreen(state, onBack = zurueck, oeffnen = oeffnen)
        route == CopyRouten.KONGRESS_FLOW -> KongressFlowScreen(state, onBack = zurueck)
        route == CopyRouten.WIRKT_DAS -> WirktDasScreen(state, onBack = zurueck, oeffnen = oeffnen)
        route == CopyRouten.KONVERGENZ -> KonvergenzScreen(state, onBack = zurueck, oeffnen = oeffnen)
        route.startsWith("copy-ue/") -> CopyUebersichtScreen(state, route.removePrefix("copy-ue/"), onBack = zurueck, onOpen = openInstrument)
        route.startsWith("copy-s/") -> StrategieScreen(state, route.removePrefix("copy-s/"), onBack = zurueck, onOpen = openInstrument)
        route == "guide" -> {
            val settings by state.settingsFlow.collectAsStateWithLifecycle()
            GuideScreen(initial = settings.ownerName, state = state) { zurueck() }
        }
        route.startsWith("instrument/") -> InstrumentScreen(state = state, symbol = route.removePrefix("instrument/"), onBack = zurueck, onOpenDerivative = openDerivative, onOpenRoute = oeffnen)
        route.startsWith("derivate/") -> {
            // derivate/L/SYMBOL oder derivate/S/SYMBOL: Derivate-Uebersicht, Long bzw. Short voreingestellt
            val rest = route.removePrefix("derivate/")
            DerivativesPage(state, rest.substringAfter('/'), onBack = zurueck, onPick = openDerivative, initialLong = rest.substringBefore('/') != "S")
        }
        route.startsWith("derivative/") -> {
            val id = route.removePrefix("derivative/")
            DerivativeScreen(
                state = state, id = id, onBack = zurueck,
                onOpenUnderlying = { state.findDerivative(id)?.let { d -> openInstrument(d.underlying) } },
                onOpenDerivative = openDerivative,
            )
        }
        route.startsWith("discover/") -> DiscoverSeite(route, state, onBack = zurueck, onOpen = openInstrument, oeffnen = oeffnen)
    }
}
