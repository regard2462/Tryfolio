package de.troetenrepublik.app.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Appearance
import de.troetenrepublik.app.data.Settings
import de.troetenrepublik.app.data.SettingsStore
import de.troetenrepublik.app.data.Store
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.MarketRepository
import de.troetenrepublik.app.market.NewsRepository
import de.troetenrepublik.app.market.CopyRepository
import de.troetenrepublik.app.plattform.Browser
import de.troetenrepublik.app.plattform.Notifier
import de.troetenrepublik.app.plattform.PagerTasten
import de.troetenrepublik.app.plattform.Plattform
import de.troetenrepublik.app.plattform.ZurueckVerteiler
import de.troetenrepublik.app.plattform.hauptfenster
import de.troetenrepublik.app.ui.AppNav
import de.troetenrepublik.app.ui.GuideScreen
import de.troetenrepublik.app.ui.EinrichtungSeite
import de.troetenrepublik.app.ui.TroeteTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.awt.Dimension
import java.io.File

// Tryfolio fuer Windows und Linux: dieselbe Oberflaeche wie auf Android in einem Fenster in Telefonform.
// Entspricht TroeteApp (Container) und MainActivity (Einrichtung, Kurzanleitung, Navigation) der Android-App.
// Zurueck: Escape oder die Zurueck-Taste der Maus.
fun main(args: Array<String>) {
    if (args.firstOrNull() == "--selbsttest") {
        kotlin.system.exitProcess(Selbsttest.laufen(args.getOrNull(1)))
    }
    Plattform.init(Plattform.standardOrdner())
    val c = DesktopContainer()
    c.state.start()
    application {
        val fenster = rememberWindowState(size = DpSize(420.dp, 900.dp), position = WindowPosition(Alignment.Center))
        val symbol = remember { BitmapPainter(org.jetbrains.skia.Image.makeFromEncoded(Plattform.asset("troete.png")).toComposeImageBitmap()) }
        Window(
            onCloseRequest = {
                c.beenden()
                de.troetenrepublik.app.plattform.WebBaustein.beenden()
                exitApplication()
            },
            title = "Tryfolio",
            icon = symbol,
            state = fenster,
            // Escape erst, wenn die Oberflaeche die Taste nicht nimmt. Offene Blaetter und Dialoge schliessen sich selbst.
            // Pfeiltasten links und rechts blaettern in Reitern, Widgets und Kategorien, Textfelder nehmen sie vorher.
            onKeyEvent = { e ->
                e.type == KeyEventType.KeyDown && when (e.key) {
                    Key.Escape -> ZurueckVerteiler.zurueck()
                    Key.DirectionLeft -> PagerTasten.blaettern(-1)
                    Key.DirectionRight -> PagerTasten.blaettern(1)
                    else -> false
                }
            },
        ) {
            DisposableEffect(Unit) {
                hauptfenster = window
                window.minimumSize = Dimension(360, 600)
                onDispose { hauptfenster = null }
            }
            // Wie auf Android: Kurse nur abfragen, solange die App zu sehen ist
            LaunchedEffect(fenster.isMinimized) {
                if (fenster.isMinimized) c.state.pause() else c.state.resume()
                c.lebenszyklus.zustand(if (fenster.isMinimized) Lifecycle.State.CREATED else Lifecycle.State.RESUMED)
            }
            DesktopApp(c)
        }
    }
}

// Wie AppContainer auf Android, Dateien im Datenordner der Plattform.
class DesktopContainer(ordner: File = Plattform.daten) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsStore(PreferenceDataStoreFactory.create { File(ordner, "settings.preferences_pb") })

    // Sprache und Erscheinungsbild muessen vor dem ersten Bild feststehen, daher einmal blockierend lesen.
    val initialSettings = runCatching { runBlocking { settings.flow.first() } }.getOrDefault(Settings())

    val store = Store(ordner, scope)
    val api = MarketApi()
    val market = MarketRepository(api, scope, File(ordner, "zweitnotiz.json"))
    val notifier = Notifier { hauptfenster?.toFront() }
    val lebenszyklus = Lebenszyklus()
    val state: AppState

    init {
        I18n.lang = initialSettings.sprache
        val kontingent = de.troetenrepublik.app.market.Abrufkontingent(File(ordner, "abrufe.json"))
        state = AppState(
            store, market, settings, scope, notifier, NewsRepository(api, kontingent), CopyRepository(api, File(ordner, "copytrading")),
            uebersetzer = de.troetenrepublik.app.market.Uebersetzer(api, kontingent, File(ordner, "uebersetzungen.json")),
        )
    }

    fun beenden() {
        // Ausstehende Aenderungen (Kanal auf Daemon-Threads) noch schreiben, sonst fehlt der letzte Trade
        kotlinx.coroutines.runBlocking { kotlinx.coroutines.withTimeoutOrNull(3_000L) { state.sichern() } }
        state.pause()
        notifier.schliessen()
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DesktopApp(c: DesktopContainer) {
    val settings by c.state.settingsFlow.collectAsState()
    val account by c.state.account.collectAsState()
    val system = isSystemInDarkTheme()
    val dark = when (settings.appearance) {
        Appearance.DARK -> true
        Appearance.LIGHT -> false
        Appearance.SYSTEM -> system
    }

    // Bestehende Depots, etwa nach einem Import, ueberspringen die Einrichtung.
    val alreadyUsed = account.positions.isNotEmpty() || account.transactions.size > 1 || account.plans.isNotEmpty()
    LaunchedEffect(alreadyUsed, settings.onboarded) {
        if (alreadyUsed && !settings.onboarded) c.settings.update { it.copy(onboarded = true) }
    }
    // Android baut beim Sprachwechsel die Activity neu auf, hier die Oberflaeche. Waehrend der Einrichtung nicht,
    // damit Eingaben erhalten bleiben.
    LaunchedEffect(settings.sprache, settings.onboarded) {
        if (settings.onboarded) I18n.lang = settings.sprache
    }

    CompositionLocalProvider(LocalUriHandler provides Browser, LocalLifecycleOwner provides c.lebenszyklus) {
        TroeteTheme(dark = dark, coloredChart = settings.coloredChart) {
            Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
                Box(
                    Modifier.fillMaxSize().onPointerEvent(PointerEventType.Press) { e ->
                        if (e.buttons.isBackPressed) ZurueckVerteiler.zurueck()
                    },
                ) {
                    key(if (settings.onboarded) I18n.lang else null) {
                        when {
                            !settings.onboarded && !alreadyUsed -> EinrichtungSeite(c.state, onLanguage = { c.state.setLanguage(it, auto = it == de.troetenrepublik.app.data.Lang.system()) })
                            settings.guidePending -> GuideScreen(initial = settings.ownerName, kachelRaster = settings.kachelRaster, onKachelRaster = { v -> c.scope.launch { c.settings.update { it.copy(kachelRaster = v) } } }, state = c.state) {
                                c.scope.launch { c.settings.update { it.copy(guidePending = false) } }
                            }
                            else -> AppNav(c.state)
                        }
                    }
                }
            }
        }
    }
}

// Lebenszyklus fuer collectAsStateWithLifecycle: aktiv, solange das Fenster offen ist, CREATED, solange es
// minimiert ist. Entspricht onStart und onStop auf Android.
class Lebenszyklus : LifecycleOwner {
    private val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
    override val lifecycle: Lifecycle get() = registry

    fun zustand(z: Lifecycle.State) {
        registry.currentState = z
    }
}
