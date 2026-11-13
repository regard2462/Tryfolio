package de.troetenrepublik.app

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import de.troetenrepublik.app.data.SettingsStore
import de.troetenrepublik.app.data.Store
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.MarketRepository
import de.troetenrepublik.app.plattform.Notifier
import de.troetenrepublik.app.plattform.Plattform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class TroeteApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Plattform.init(this)
        // Vor jedem Widget-Zeichnen: alte Stimmungs-Widgets ohne Wahl behalten die Aktien, neue zeigen die Gesamtstimmung
        runCatching { de.troetenrepublik.app.plattform.Widgets.barometerAltbestand(this) }
        container = AppContainer(this)
        runCatching { de.troetenrepublik.app.plattform.CopyPruefung.planen(this) }
        runCatching { de.troetenrepublik.app.plattform.NewsPruefung.planen(this) }
        // Tagesrueckblick zur gewaehlten Uhrzeit: beim Start nur planen, wenn noch nichts geplant ist (ein laufender
        // Auftrag startet die App selbst), bei jeder Aenderung neu planen
        container.scope.launch {
            var erster = true
            container.settings.flow.map { it.tagesrueckblick to it.rueckblickMinute }.distinctUntilChanged().collect { (an, ab) ->
                runCatching { de.troetenrepublik.app.plattform.RueckblickPlanung.planen(this@TroeteApp, an, ab, ersetzen = !erster) }
                erster = false
            }
        }
        // Einmal am Tag nach einer neuen Fassung suchen
        runCatching { de.troetenrepublik.app.plattform.UpdatePruefung.planen(this) }
        // Widgets im Hintergrund aktualisieren, solange welche auf dem Startbildschirm liegen
        runCatching { de.troetenrepublik.app.plattform.Widgets.planen(this) }
        // Aktienfinder im Hintergrund vorladen
        runCatching { de.troetenrepublik.app.plattform.FinderVorladen.planen(this) }
    }
}

// Name und Ort nicht aendern, sonst gehen bestehende Einstellungen verloren.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    "settings",
    // Bei beschaedigter Datei gelten die Vorgaben, statt dass die App bei jedem Start abstuerzt.
    corruptionHandler = androidx.datastore.core.handlers.ReplaceFileCorruptionHandler { androidx.datastore.preferences.core.emptyPreferences() },
)

class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsStore(app.dataStore)

    // Sprache und Erscheinungsbild muessen vor dem ersten Bild feststehen, daher hier einmal blockierend lesen.
    val initialSettings = runCatching { runBlocking { settings.startEinstellungen() } }.getOrDefault(de.troetenrepublik.app.data.Settings())

    val store = Store(app.filesDir, scope)
    val api = MarketApi()
    val market = MarketRepository(api, scope, java.io.File(app.filesDir, "zweitnotiz.json"), java.io.File(app.filesDir, "stand"))
    val notifier: Notifier
    val state: AppState

    init {
        I18n.lang = initialSettings.sprache
        notifier = Notifier(app)
        val kontingent = de.troetenrepublik.app.market.Abrufkontingent(java.io.File(app.filesDir, "abrufe.json"))
        state = AppState(store, market, settings, scope, notifier, de.troetenrepublik.app.market.NewsRepository(api, kontingent, de.troetenrepublik.app.market.SichereStandDatei(java.io.File(app.filesDir, "stand/news.txt"))),
            de.troetenrepublik.app.market.CopyRepository(api, java.io.File(app.filesDir, "copytrading")),
            uebersetzer = de.troetenrepublik.app.market.Uebersetzer(api, kontingent, java.io.File(app.filesDir, "uebersetzungen.json")))
    }
}
