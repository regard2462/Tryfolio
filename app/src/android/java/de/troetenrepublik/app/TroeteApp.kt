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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class TroeteApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Plattform.init(this)
        container = AppContainer(this)
        runCatching { de.troetenrepublik.app.plattform.CopyPruefung.planen(this) }
        runCatching { de.troetenrepublik.app.plattform.NewsPruefung.planen(this) }
        // Einmal am Tag nach einer neuen Fassung suchen
        runCatching { de.troetenrepublik.app.plattform.UpdatePruefung.planen(this) }
        // Widgets im Hintergrund aktualisieren, solange welche auf dem Startbildschirm liegen
        runCatching { de.troetenrepublik.app.plattform.Widgets.planen(this) }
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
    val initialSettings = runCatching { runBlocking { settings.flow.first() } }.getOrDefault(de.troetenrepublik.app.data.Settings())

    val store = Store(app.filesDir, scope)
    val api = MarketApi()
    val market = MarketRepository(api, scope, java.io.File(app.filesDir, "zweitnotiz.json"))
    val notifier: Notifier
    val state: AppState

    init {
        I18n.lang = initialSettings.sprache
        notifier = Notifier(app)
        val kontingent = de.troetenrepublik.app.market.Abrufkontingent(java.io.File(app.filesDir, "abrufe.json"))
        state = AppState(store, market, settings, scope, notifier, de.troetenrepublik.app.market.NewsRepository(api, kontingent),
            de.troetenrepublik.app.market.CopyRepository(api, java.io.File(app.filesDir, "copytrading")),
            uebersetzer = de.troetenrepublik.app.market.Uebersetzer(api, kontingent, java.io.File(app.filesDir, "uebersetzungen.json")))
    }
}
