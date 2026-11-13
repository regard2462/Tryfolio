package de.troetenrepublik.app.plattform

import de.troetenrepublik.app.ios.Dateien
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import java.io.File

// Was iPhone und iPad anders erledigen als Android. Gegenstueck zu Plattform.kt im Android-Quellsatz und im
// Desktop-Modul; alle haben dieselben oeffentlichen Teile.
object Plattform {
    // Ordner fuer Depot, Einstellungen und Zwischenspeicher (Application Support im Sandkasten der App).
    lateinit var daten: File
        private set

    // Einmal beim Start (MainViewController.kt).
    fun init(ordner: File) {
        ordner.mkdirs()
        daten = ordner
        // [Sicherheit 0.71] Depot, Einstellungen und Tresor bleiben aus iCloud- und Rechnersicherungen heraus: Das
        // Kennzeichen gilt fuer den Ordner und alles darunter. Wer ein Geraet wiederherstellt, beginnt mit leerer App;
        // Zugangsdaten liegen ohnehin verschluesselt im Schluesselbund.
        runCatching {
            NSURL.fileURLWithPath(ordner.absolutePath, true, null)
                .setResourceValue(true, forKey = platform.Foundation.NSURLIsExcludedFromBackupKey, error = null)
        }
    }

    // Library/Application Support/Tryfolio; nur die App kommt daran, iCloud- und Rechnersicherungen nehmen ihn mit.
    fun standardOrdner(): File {
        val basis = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).firstOrNull() as? String
            ?: (platform.Foundation.NSHomeDirectory() + "/Library/Application Support")
        return File(basis, "Tryfolio")
    }

    // Seiten oeffnen in Safari; eingebettet ist nur das Finanzwissen.
    val webAnsicht: Boolean = false

    // Telefon oder Tablet mit Fingerbedienung.
    val desktop: Boolean = false

    // Schuetteln: auf iOS ohne Wirkung.
    val schuetteln: Boolean = false

    // Neue Fassungen kommen als neue IPA ueber SideStore, AltStore oder Sideloadly; die App sucht nicht selbst danach.
    val aktualisierbar: Boolean = false

    // Mitteilungen ueber das Mitteilungszentrum, solange die App laeuft.
    val mitteilungen: Boolean = true

    // Kurse kommen live aus dem Netz.
    val kursabruf: Boolean = true

    // Datei aus assets/ der App, die als Ordner assets im App-Paket liegt.
    fun asset(name: String): ByteArray {
        val wurzel = NSBundle.mainBundle.resourcePath ?: error("Ressource fehlt: $name")
        if (name.contains("..")) error("Ressource fehlt: $name")
        return Dateien.lesen("$wurzel/assets/$name") ?: error("Ressource fehlt: $name")
    }

    // Oeffnet die Einstellungen der App in der Einstellungen-App (Mitteilungen erlauben).
    fun appEinstellungenOeffnen() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
    }

    private val logoDatei by lazy { File(daten, "logo_looks.json") }
    private val logoLooks: MutableMap<String, String> by lazy {
        runCatching { Json.decodeFromString<Map<String, String>>(logoDatei.readText()).toMutableMap() }.getOrDefault(LinkedHashMap())
    }

    fun logoLooksLaden(): Map<String, String> = de.troetenrepublik.app.ios.Sperre.mit { logoLooks.toMap() }

    fun logoLookSpeichern(symbol: String, wert: String) = de.troetenrepublik.app.ios.Sperre.mit {
        if (logoLooks[symbol] == wert) return@mit
        logoLooks[symbol] = wert
        runCatching { SichereDatei(logoDatei).schreiben(Json.encodeToString(logoLooks.toMap()).encodeToByteArray()) }
    }
}
