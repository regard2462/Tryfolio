package de.troetenrepublik.app.plattform

import androidx.compose.ui.platform.UriHandler
import java.awt.Desktop
import java.net.URI

// Adressen im Standardbrowser oeffnen; unter Linux notfalls mit xdg-open.
object Browser : UriHandler {
    // In Tests (troete.browser=aus) oeffnet sich nichts; die Adressen landen nur hier.
    private val aus = System.getProperty("troete.browser") == "aus"
    val zuletzt = mutableListOf<String>()

    fun oeffnen(adresse: String): Boolean {
        val uri = runCatching { URI(adresse) }.getOrNull() ?: return false
        if (uri.scheme != "http" && uri.scheme != "https") return false
        zuletzt += adresse
        if (aus) return true
        val awt = runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri); true
            } else false
        }.getOrDefault(false)
        if (awt) return true
        return runCatching { ProcessBuilder("xdg-open", uri.toString()).start(); true }.getOrDefault(false)
    }

    override fun openUri(uri: String) {
        if (!oeffnen(uri)) throw IllegalStateException("Browser nicht verfuegbar")
    }
}
