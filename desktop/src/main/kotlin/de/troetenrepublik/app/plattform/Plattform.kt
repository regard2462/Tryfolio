package de.troetenrepublik.app.plattform

import java.io.File
import java.util.Properties

// Was Windows und Linux anders erledigen als Android. Gegenstueck zu Plattform.kt im Android-Quellsatz;
// beide haben dieselben oeffentlichen Teile.
object Plattform {
    // Ordner fuer Depot, Einstellungen und Zwischenspeicher.
    lateinit var daten: File
        private set

    // Einmal beim Start (Main.kt).
    fun init(ordner: File) {
        ordner.mkdirs()
        // Nur der eigene Benutzer darf den Datenordner lesen: Depot, Einstellungen, Tresor
        runCatching {
            java.nio.file.Files.setPosixFilePermissions(ordner.toPath(), java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"))
        }.onFailure {
            ordner.setReadable(false, false); ordner.setReadable(true, true)
            ordner.setWritable(false, false); ordner.setWritable(true, true)
            ordner.setExecutable(false, false); ordner.setExecutable(true, true)
        }
        daten = ordner
    }

    // Datenordner nach Art des Systems: Windows %APPDATA%\Tryfolio, macOS ~/Library/Application Support/Tryfolio,
    // Linux $XDG_DATA_HOME/tryfolio (sonst ~/.local/share/tryfolio). Gibt es nur den Ordner aus der Zeit vor der
    // Umbenennung (Troetenrepublik), wird er umbenannt; klappt das nicht, bleibt die App beim alten Ordner. Die
    // Systemeigenschaft troete.daten setzt einen anderen Ordner (Tests).
    fun standardOrdner(): File {
        System.getProperty("troete.daten")?.takeIf { it.isNotBlank() }?.let { return File(it) }
        val home = System.getProperty("user.home")
        val os = System.getProperty("os.name").orEmpty()
        val (basis, neu, alt) = if (os.startsWith("Windows")) {
            Triple(System.getenv("APPDATA") ?: "$home\\AppData\\Roaming", "Tryfolio", "Troetenrepublik")
        } else if (os.startsWith("Mac")) {
            Triple("$home/Library/Application Support", "Tryfolio", "Troetenrepublik")
        } else {
            Triple(System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.local/share", "tryfolio", "troetenrepublik")
        }
        val ordner = File(basis, neu)
        val vorher = File(basis, alt)
        if (!ordner.exists() && vorher.isDirectory && !runCatching { vorher.renameTo(ordner) }.getOrDefault(false)) return vorher
        return ordner
    }

    // Seiten lassen sich nicht in die App einbetten; sie oeffnen im Browser.
    val webAnsicht: Boolean = false

    // Windows oder Linux (Maus und Tastatur statt Finger).
    val desktop: Boolean = true

    // Kein Bewegungssensor.
    val schuetteln: Boolean = false

    // Neue Fassungen kommen als Paket; die App fragt danach.
    val aktualisierbar: Boolean = true

    // Es gibt Mitteilungen (im Browser nicht).
    val mitteilungen: Boolean = true

    // Kurse kommen live aus dem Netz (im Browser aus einem mitgelieferten Stand).
    val kursabruf: Boolean = true

    // Datei aus assets/ der App, die hier im Klassenpfad liegt.
    fun asset(name: String): ByteArray =
        (Plattform::class.java.classLoader.getResourceAsStream(name) ?: error("Ressource fehlt: $name")).use { it.readBytes() }

    // Am Desktop gibt es keine Akku-Optimierung und keine Widgets.
    fun appEinstellungenOeffnen() {}

    private val logoDatei by lazy { File(daten, "logo_looks.properties") }
    private val logoLooks by lazy { Properties().apply { runCatching { logoDatei.inputStream().use { load(it) } } } }

    @Synchronized
    fun logoLooksLaden(): Map<String, String> = logoLooks.stringPropertyNames().associateWith { logoLooks.getProperty(it) }

    @Synchronized
    fun logoLookSpeichern(symbol: String, wert: String) {
        if (logoLooks.getProperty(symbol) == wert) return
        logoLooks.setProperty(symbol, wert)
        runCatching { SichereDatei(logoDatei).schreiben(java.io.ByteArrayOutputStream().also { logoLooks.store(it, null) }.toByteArray()) }
    }
}
