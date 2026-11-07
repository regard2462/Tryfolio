package de.troetenrepublik.app.ios

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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.window.ComposeUIViewController
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.Appearance
import de.troetenrepublik.app.data.Settings
import de.troetenrepublik.app.data.SettingsStore
import de.troetenrepublik.app.data.Store
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.market.CopyRepository
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.MarketRepository
import de.troetenrepublik.app.market.NewsRepository
import de.troetenrepublik.app.plattform.Browser
import de.troetenrepublik.app.plattform.Notifier
import de.troetenrepublik.app.plattform.Plattform
import de.troetenrepublik.app.plattform.ZurueckVerteiler
import de.troetenrepublik.app.ui.AppNav
import de.troetenrepublik.app.ui.EinrichtungSeite
import de.troetenrepublik.app.ui.GuideScreen
import de.troetenrepublik.app.ui.TroeteTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import platform.UIKit.UIViewController
import java.io.File

// Tryfolio fuer iPhone und iPad: dieselbe Oberflaeche wie auf Android. Entspricht TroeteApp und MainActivity der
// Android-App und Main.kt am Desktop. Die Swift-Huelle in iosApp/ zeigt den Bildschirm aus MainViewController().
// Zurueck: die Wischgeste vom linken Rand.

private val container: IosContainer by lazy {
    Plattform.init(Plattform.standardOrdner())
    IosContainer().also { it.state.start() }
}

@OptIn(ExperimentalComposeUiApi::class)
fun MainViewController(): UIViewController = ComposeUIViewController(configure = { enableBackGesture = true }) {
    IosApp(container)
}

// Wie AppContainer auf Android, Dateien im Datenordner der App.
class IosContainer(ordner: File = Plattform.daten) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsStore(PreferenceDataStoreFactory.createWithPath { File(ordner, "settings.preferences_pb").path.toPath() })

    // Sprache und Erscheinungsbild muessen vor dem ersten Bild feststehen, daher einmal blockierend lesen.
    val initialSettings = runCatching { runBlocking { settings.flow.first() } }.getOrDefault(Settings())

    val store = Store(ordner, scope)
    val api = MarketApi()
    val market = MarketRepository(api, scope, File(ordner, "zweitnotiz.json"))
    val notifier = Notifier()
    val state: AppState

    init {
        I18n.lang = initialSettings.sprache
        val kontingent = de.troetenrepublik.app.market.Abrufkontingent(File(ordner, "abrufe.json"))
        state = AppState(
            store, market, settings, scope, notifier, NewsRepository(api, kontingent), CopyRepository(api, File(ordner, "copytrading")),
            uebersetzer = de.troetenrepublik.app.market.Uebersetzer(api, kontingent, File(ordner, "uebersetzungen.json")),
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun IosApp(c: IosContainer) {
    val settings by c.state.settingsFlow.collectAsState()
    val account by c.state.account.collectAsState()
    val system = isSystemInDarkTheme()
    val dark = when (settings.appearance) {
        Appearance.DARK -> true
        Appearance.LIGHT -> false
        Appearance.SYSTEM -> system
    }

    // Wie auf Android: Kurse nur abfragen, solange die App zu sehen ist. Beim Wechsel in den Hintergrund sichern,
    // denn iOS beendet Apps im Hintergrund ohne Vorwarnung.
    val lebenszyklus = LocalLifecycleOwner.current
    DisposableEffect(lebenszyklus) {
        val beobachter = LifecycleEventObserver { _, ereignis ->
            when (ereignis) {
                Lifecycle.Event.ON_RESUME -> c.state.resume()
                Lifecycle.Event.ON_PAUSE -> {
                    c.state.pause()
                    c.scope.launch { runCatching { c.state.sichern() } }
                }
                else -> {}
            }
        }
        lebenszyklus.lifecycle.addObserver(beobachter)
        onDispose { lebenszyklus.lifecycle.removeObserver(beobachter) }
    }

    // Bestehende Depots, etwa nach einem Import, ueberspringen die Einrichtung.
    val alreadyUsed = account.positions.isNotEmpty() || account.transactions.size > 1 || account.plans.isNotEmpty()
    LaunchedEffect(alreadyUsed, settings.onboarded) {
        if (alreadyUsed && !settings.onboarded) c.settings.update { it.copy(onboarded = true) }
    }
    LaunchedEffect(settings.sprache, settings.onboarded) {
        if (settings.onboarded) I18n.lang = settings.sprache
    }

    // Randgeste: an den zuletzt angemeldeten Empfaenger, wie Escape am Desktop
    BackHandler(enabled = true) { ZurueckVerteiler.zurueck() }

    CompositionLocalProvider(LocalUriHandler provides Browser) {
        TroeteTheme(dark = dark, coloredChart = settings.coloredChart) {
            Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
                Box(Modifier.fillMaxSize()) {
                    key(if (settings.onboarded) I18n.lang else null) {
                        when {
                            !settings.onboarded && !alreadyUsed -> EinrichtungSeite(c.state, onLanguage = { c.state.setLanguage(it, auto = it == de.troetenrepublik.app.data.Lang.system()) })
                            settings.guidePending -> GuideScreen(initial = settings.ownerName, state = c.state) {
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
