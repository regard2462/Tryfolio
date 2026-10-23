package de.troetenrepublik.app.plattform

import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

// Systemmitteilungen auf dem Desktop: unter Windows als Meldung am Infobereich der Taskleiste, unter Linux ueber
// notify-send (libnotify), sonst ueber den Infobereich, wenn die Oberflaeche einen hat. Nur solange die App laeuft.
// Gleiche Bausteine wie auf Android (Kanaele, enabled), damit AppState unveraendert bleibt.
class Notifier(private val onKlick: () -> Unit = {}) {

    companion object {
        const val CHANNEL_TRADES = "trades"
        const val CHANNEL_ALERTS = "alerts"
        const val CHANNEL_CASH = "cash"
        const val CHANNEL_UPDATES = "updates"
        const val CHANNEL_NEWS = "news"
        const val CHANNEL_RUECKBLICK = "rueckblick"
        const val CHANNEL_COPY = "copytrading"
    }

    @Volatile var enabled: Boolean = true

    private val windows = System.getProperty("os.name").orEmpty().startsWith("Windows")
    private var tray: TrayIcon? = null

    // Adresse der letzten Meldung mit Link; ein Klick auf die Meldung oeffnet sie im Browser.
    @Volatile private var link: String? = null

    // Seite der App zur letzten Meldung; ein Klick oeffnet sie ueber beiZiel.
    @Volatile private var ziel: String? = null

    // Oeffnet eine Seite der App; setzt AppState.
    @Volatile var beiZiel: (String) -> Unit = {}

    fun refreshChannelNames() {}

    fun canNotify(): Boolean = true

    fun notify(channel: String, title: String, text: String, link: String? = null, ziel: String? = null) {
        if (!enabled) return
        this.link = link
        this.ziel = ziel
        if (!windows && notifySend(title, text)) return
        runCatching { trayIcon()?.displayMessage(title, text, if (channel == CHANNEL_ALERTS) TrayIcon.MessageType.WARNING else TrayIcon.MessageType.INFO) }
    }

    private fun notifySend(title: String, text: String): Boolean = runCatching {
        ProcessBuilder("notify-send", "--app-name=Tryfolio", title, text).redirectErrorStream(true).start().waitFor() == 0
    }.getOrDefault(false)

    @Synchronized
    private fun trayIcon(): TrayIcon? {
        tray?.let { return it }
        if (!SystemTray.isSupported()) return null
        val bild: BufferedImage = javaClass.classLoader.getResourceAsStream("troete.png")?.use { ImageIO.read(it) } ?: return null
        val icon = TrayIcon(bild, "Tryfolio").apply {
            isImageAutoSize = true
            addActionListener {
                val l = link
                val z = ziel
                when {
                    l != null -> { link = null; Browser.oeffnen(l) }
                    z != null -> { ziel = null; beiZiel(z); onKlick() }
                    else -> onKlick()
                }
            }
        }
        SystemTray.getSystemTray().add(icon)
        tray = icon
        return icon
    }

    // Beim Beenden das Symbol wieder entfernen.
    fun schliessen() {
        tray?.let { t -> runCatching { SystemTray.getSystemTray().remove(t) } }
        tray = null
    }
}
