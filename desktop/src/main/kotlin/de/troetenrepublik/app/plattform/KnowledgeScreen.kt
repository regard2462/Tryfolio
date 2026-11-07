package de.troetenrepublik.app.plattform

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import de.troetenrepublik.app.ui.Chip
import de.troetenrepublik.app.ui.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import de.troetenrepublik.app.ui.WissenQuelleBlatt
import de.troetenrepublik.app.ui.rememberSwipeDownDismiss
import de.troetenrepublik.app.ui.sheetDismiss
import java.net.InetAddress
import java.net.InetSocketAddress

// Finanzwissen auf dem Desktop: Die Lern-Bibliothek ist eine Web-App (assets/finanzwissen), die ein Webserver nur auf
// 127.0.0.1 aus der App liefert. Seit 5.93 waehlbar: im Standardbrowser oder in einem eigenen Fenster der App mit dem
// eingebetteten Browser (Chromium, einmal zu laden). Die Wahl bleibt gespeichert.
// Im Fenster angetippte Quellen zeigt die App im Hauptfenster im NewsSheet (WissenQuelleBlatt).
@Composable
fun KnowledgeScreen(onBack: () -> Unit) {
    val dark = LocalSignal.current.dark
    val adresse = remember(dark, I18n.lang) {
        Finanzwissen.adresse(theme = if (dark) "dunkel" else "hell", lang = I18n.lang.code)
    }
    var inApp by remember { mutableStateOf(Finanzwissen.inApp) }
    // null: noch nicht geoeffnet
    var geoeffnet by remember { mutableStateOf<Boolean?>(null) }
    var bausteinDa by remember { mutableStateOf(WebBaustein.installiert) }
    var fortschritt by remember { mutableStateOf<Float?>(null) }
    var ladeFehler by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    suspend fun oeffnen() {
        val a = adresse ?: run { geoeffnet = false; return }
        geoeffnet = if (inApp) WebBaustein.fenster("$a&fenster=1", S.knowledge) else withContext(Dispatchers.IO) { Browser.oeffnen(a) }
    }
    // Beim Betreten wie zuletzt gewaehlt; in der App nur, wenn der eingebettete Browser schon da ist
    LaunchedEffect(Unit) { if (!inApp || bausteinDa) oeffnen() }
    val down = rememberSwipeDownDismiss(onBack)
    // Angetippte Quelle aus dem Fenster: Link und Titel; das Hauptfenster kommt dafuer nach vorn.
    var quelle by remember { mutableStateOf<Pair<String, String>?>(null) }
    DisposableEffect(Unit) {
        WebBaustein.wissenQuelle = { link, titel -> quelle = link to titel; hauptfenster?.toFront() }
        onDispose { WebBaustein.wissenQuelle = null }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).sheetDismiss(down)) {
        ScreenHeader(S.knowledge, null, onBack)
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(S.knowledgeSubtitle, style = MaterialTheme.typography.bodyLarge)
            Text(S.knowledgeOeffnenIn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(S.knowledgeImBrowser, !inApp) {
                    inApp = false; Finanzwissen.inApp = false; geoeffnet = null
                    scope.launch { oeffnen() }
                }
                Chip(S.knowledgeInApp, inApp) {
                    inApp = true; Finanzwissen.inApp = true; geoeffnet = null
                    if (bausteinDa) scope.launch { oeffnen() }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (inApp && !bausteinDa) {
                // Eingebetteter Browser fehlt: erst nach Tipp laden
                Text(if (ladeFehler) S.webBausteinFehler else S.knowledgeBausteinAngebot(WebBaustein.groesseMb),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val f = fortschritt
                if (f == null) {
                    Text(
                        S.webBausteinLaden, style = MaterialTheme.typography.labelLarge, color = LocalSignal.current.link,
                        modifier = Modifier.clickable {
                            fortschritt = 0f
                            ladeFehler = false
                            scope.launch {
                                val ok = WebBaustein.installieren { fortschritt = it }
                                fortschritt = null
                                if (ok) { bausteinDa = true; oeffnen() } else ladeFehler = true
                            }
                        }.padding(vertical = 10.dp),
                    )
                } else {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurface,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    Text(S.webBausteinLaedt(Fmt.pct(f * 100.0, 0)), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            } else when (geoeffnet) {
                true -> Text(if (inApp) S.knowledgeInAppOffen else S.knowledgeInBrowser, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                false -> Text(if (inApp) S.webBausteinFehler else S.knowledgeBrowserFailed(adresse ?: "-"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                null -> {}
            }
        }
        if (adresse != null && (!inApp || bausteinDa)) ActionLine(if (inApp) S.knowledgeInAppNochmal else S.knowledgeOpenAgain) { scope.launch { oeffnen() } }
    }

    quelle?.let { (link, titel) -> WissenQuelleBlatt(link, titel) { quelle = null } }
}

// Webserver fuer die Lern-Bibliothek, gestartet beim ersten Oeffnen.
internal object Finanzwissen {
    private var server: HttpServer? = null

    // Port des laufenden Webservers, null ohne Server.
    val port: Int? get() = server?.address?.port

    // Gespeicherte Wahl: in einem Fenster der App statt im Standardbrowser.
    private val wahlDatei get() = java.io.File(Plattform.daten, "finanzwissen-ansicht.txt")
    var inApp: Boolean
        get() = runCatching { wahlDatei.readText().trim() == "app" }.getOrDefault(false)
        set(v) { runCatching { wahlDatei.writeText(if (v) "app" else "browser") } }

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
