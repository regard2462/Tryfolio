package de.troetenrepublik.app

import android.Manifest
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.LocaleList
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import de.troetenrepublik.app.data.Appearance
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.ui.AppNav
import de.troetenrepublik.app.ui.GuideScreen
import de.troetenrepublik.app.ui.EinrichtungSeite
import de.troetenrepublik.app.ui.TroeteTheme
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {

    private var shake: ShakeDetector? = null

    // Sprache, mit der dieser Activity-Kontext gebaut wurde.
    private var appliedLang: Lang = Lang.DE


    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // Datumswaehler, Dialoge und Blaetter haben eigene Fenster und nehmen ihre Ressourcen vom Activity-Kontext.
    // Deshalb wird die Sprache hier gesetzt. I18n.lang steht beim Start schon fest (AppContainer).
    override fun attachBaseContext(newBase: Context) {
        appliedLang = I18n.lang
        val conf = Configuration(newBase.resources.configuration).apply { setLocales(LocaleList(I18n.locale)) }
        super.attachBaseContext(newBase.createConfigurationContext(conf))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as TroeteApp).container
        container.state.start()
        // Aus einem Widget geoeffnet: dessen Seite zeigen
        if (savedInstanceState == null) container.state.springeZu(intent?.getStringExtra(de.troetenrepublik.app.plattform.Widgets.ZIEL))

        // Fensterhintergrund passend zum gewaehlten Erscheinungsbild, damit beim Start nichts blitzt.
        val startDark = when (container.initialSettings.appearance) {
            Appearance.DARK -> true
            Appearance.LIGHT -> false
            Appearance.SYSTEM -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        window.setBackgroundDrawable(ColorDrawable(if (startDark) android.graphics.Color.BLACK else android.graphics.Color.WHITE))

        if (Build.VERSION.SDK_INT >= 33 && !container.notifier.canNotify()) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val settings by container.state.settingsFlow.collectAsStateWithLifecycle()
            val account by container.state.account.collectAsStateWithLifecycle()
            val system = isSystemInDarkTheme()
            val dark = when (settings.appearance) {
                Appearance.DARK -> true
                Appearance.LIGHT -> false
                Appearance.SYSTEM -> system
            }
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                )
                window.setBackgroundDrawable(ColorDrawable(if (dark) android.graphics.Color.BLACK else android.graphics.Color.WHITE))
            }

            // Bestehende Depots ueberspringen die Einrichtung.
            val alreadyUsed = account.positions.isNotEmpty() || account.transactions.size > 1 || account.plans.isNotEmpty()
            LaunchedEffect(alreadyUsed, settings.onboarded) {
                if (alreadyUsed && !settings.onboarded) container.settings.update { it.copy(onboarded = true) }
            }
            // Nach einem Sprachwechsel die Activity mit passendem Kontext neu aufbauen (siehe attachBaseContext),
            // aber nicht waehrend der Einrichtung, damit Eingaben erhalten bleiben.
            LaunchedEffect(settings.sprache, settings.onboarded) {
                if (settings.onboarded && settings.sprache != appliedLang) {
                    I18n.lang = settings.sprache
                    recreate()
                }
            }

            // Links nur als Webadresse oeffnen
            val links = androidx.compose.runtime.remember { de.troetenrepublik.app.plattform.WebLinks(this@MainActivity) }
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalUriHandler provides links) {
            TroeteTheme(dark = dark, coloredChart = settings.coloredChart) {
                // Surface setzt die Textfarbe; ohne sie stuende Schrift schwarz auf schwarzem Grund.
                Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
                    when {
                        // Sprachwahl in der Einrichtung sofort speichern, sonst gilt nach dem Neustart wieder Deutsch
                        !settings.onboarded && !alreadyUsed -> EinrichtungSeite(container.state, onLanguage = { container.state.setLanguage(it, auto = it == Lang.system()) })
                        settings.guidePending -> GuideScreen(initial = settings.ownerName, systemInsets = true, kachelRaster = settings.kachelRaster, onKachelRaster = { v -> lifecycleScope.launch { container.settings.update { it.copy(kachelRaster = v) } } }, state = container.state) {
                            lifecycleScope.launch { container.settings.update { it.copy(guidePending = false) } }
                        }
                        else -> AppNav(container.state)
                    }
                }
            }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        (application as TroeteApp).container.state.springeZu(intent.getStringExtra(de.troetenrepublik.app.plattform.Widgets.ZIEL))
    }

    override fun onStart() {
        super.onStart()
        val c = (application as TroeteApp).container
        c.state.resume()
        // Schuetteln blendet Betraege aus.
        shake = ShakeDetector(this) {
            val s = c.state.settingsFlow.value
            if (s.shakeToHide) lifecycleScope.launch { c.settings.update { it.copy(hideBalances = !it.hideBalances) } }
        }.also { it.start() }
    }

    override fun onStop() {
        super.onStop()
        val c = (application as TroeteApp).container
        c.state.pause()
        shake?.stop(); shake = null
    }
}

private class ShakeDetector(private val context: Context, private val onShake: () -> Unit) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private var last = 0L

    fun start() {
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() { manager?.unregisterListener(this) }

    override fun onSensorChanged(event: SensorEvent) {
        val g = sqrt(event.values[0] * event.values[0] + event.values[1] * event.values[1] + event.values[2] * event.values[2]) / SensorManager.GRAVITY_EARTH
        if (g > 2.7f) {
            val now = System.currentTimeMillis()
            if (now - last > 1_200) { last = now; onShake() }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
