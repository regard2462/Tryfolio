package de.troetenrepublik.app.plattform

import android.annotation.SuppressLint
import android.content.Context

// Was Android und Desktop unterschiedlich erledigen. Das Gegenstueck fuer Windows und Linux liegt in
// desktop/src/main/kotlin unter demselben Namen; beide muessen dieselben oeffentlichen Teile haben.
@SuppressLint("StaticFieldLeak") // nur der Anwendungskontext, der so lange lebt wie die App
object Plattform {
    private lateinit var app: Context

    // Anwendungskontext fuer andere Plattformteile (Tresor).
    internal val kontext: Context get() = app

    // Einmal beim Start (TroeteApp).
    fun init(context: Context) {
        app = context.applicationContext
    }

    // Seiten lassen sich in der App zeigen (Web-Ansicht); sonst nur im Browser.
    val webAnsicht: Boolean = true

    // Windows oder Linux (Maus und Tastatur statt Finger).
    val desktop: Boolean = false

    // Bewegungssensor fuer „Schütteln blendet Beträge aus“.
    val schuetteln: Boolean = true

    // Neue Fassungen kommen als Paket; die App fragt danach.
    val aktualisierbar: Boolean = true

    // Es gibt Mitteilungen (im Browser nicht).
    val mitteilungen: Boolean = true

    // Kurse kommen live aus dem Netz (im Browser aus einem mitgelieferten Stand).
    val kursabruf: Boolean = true

    fun asset(name: String): ByteArray = app.assets.open(name).use { it.readBytes() }

    // App-Einstellungen des Systems (dort liegen Akku-Optimierung und Hintergrundnutzung).
    fun appEinstellungenOeffnen() {
        runCatching {
            app.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + app.packageName))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private val logoPrefs by lazy { app.getSharedPreferences("logo_looks", Context.MODE_PRIVATE) }

    // Gemerkte Darstellung der Logos (Symbol -> „F|Farbe“ oder „M|Farbe“).
    fun logoLooksLaden(): Map<String, String> =
        logoPrefs.all.mapNotNull { (symbol, v) -> (v as? String)?.let { symbol to it } }.toMap()

    fun logoLookSpeichern(symbol: String, wert: String) {
        logoPrefs.edit().putString(symbol, wert).apply()
    }
}
