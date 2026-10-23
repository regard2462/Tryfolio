package de.troetenrepublik.app.plattform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sun.net.httpserver.HttpServer
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.ui.ActionLine
import de.troetenrepublik.app.ui.LocalSignal
import de.troetenrepublik.app.ui.ScreenHeader
import de.troetenrepublik.app.ui.rememberSwipeDownDismiss
import de.troetenrepublik.app.ui.sheetDismiss
import java.net.InetAddress
import java.net.InetSocketAddress

// Finanzwissen auf dem Desktop: Die Lern-Bibliothek ist eine Web-App (assets/finanzwissen). Ohne eingebettete
// Web-Ansicht oeffnet sie im Browser; ein Webserver nur auf 127.0.0.1 liefert die Dateien aus der App.
@Composable
fun KnowledgeScreen(onBack: () -> Unit) {
    val dark = LocalSignal.current.dark
    val adresse = remember(dark, I18n.lang) {
        Finanzwissen.adresse(theme = if (dark) "dunkel" else "hell", lang = I18n.lang.code)
    }
    var geoeffnet by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { geoeffnet = adresse != null && Browser.oeffnen(adresse) }
    val down = rememberSwipeDownDismiss(onBack)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(down)) {
        ScreenHeader(S.knowledge, null, onBack)
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(S.knowledgeSubtitle, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
            when (geoeffnet) {
                true -> Text(S.knowledgeInBrowser, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                false -> Text(S.knowledgeBrowserFailed(adresse ?: "-"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                null -> {}
            }
        }
        if (adresse != null) ActionLine(S.knowledgeOpenAgain) { geoeffnet = Browser.oeffnen(adresse) }
    }
}

// Webserver fuer die Lern-Bibliothek, gestartet beim ersten Oeffnen.
internal object Finanzwissen {
    private var server: HttpServer? = null

    private val typen = mapOf(
        "html" to "text/html; charset=utf-8", "js" to "text/javascript; charset=utf-8", "css" to "text/css; charset=utf-8",
        "json" to "application/json; charset=utf-8", "svg" to "image/svg+xml", "png" to "image/png", "jpg" to "image/jpeg",
        "woff2" to "font/woff2", "woff" to "font/woff", "ico" to "image/x-icon", "txt" to "text/plain; charset=utf-8",
    )

    @Synchronized
    fun adresse(theme: String, lang: String): String? {
        val s = server ?: runCatching { starten() }.getOrNull() ?: return null
        val region = de.troetenrepublik.app.engine.Regionen.aktuell.lowercase()
        return "http://127.0.0.1:${s.address.port}/finanzwissen/index.html?theme=$theme&lang=$lang&region=$region&umgebung=browser"
    }

    private fun starten(): HttpServer {
        val s = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        s.createContext("/finanzwissen/") { ex ->
            try {
                val pfad = ex.requestURI.path.removePrefix("/")
                val bytes = if (ex.requestMethod != "GET" || pfad.contains("..") || pfad.contains('\\')) null
                    else javaClass.classLoader.getResourceAsStream(pfad)?.use { it.readBytes() }
                if (bytes == null) {
                    ex.sendResponseHeaders(404, -1)
                } else {
                    ex.responseHeaders.add("Content-Type", typen[pfad.substringAfterLast('.', "").lowercase()] ?: "application/octet-stream")
                    ex.responseHeaders.add("Cache-Control", "no-cache")
                    ex.sendResponseHeaders(200, bytes.size.toLong())
                    ex.responseBody.write(bytes)
                }
            } finally {
                ex.close()
            }
        }
        s.executor = null
        s.start()
        server = s
        return s
    }

    fun stoppen() {
        server?.stop(0)
        server = null
    }
}
