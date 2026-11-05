package de.troetenrepublik.app.plattform

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import de.troetenrepublik.app.engine.SelbstabrufDatei
import de.troetenrepublik.app.i18n.S
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONObject

// Seite einer Quelle im eigenen Browser: Die Person bedient die Seite selbst, ein Download-
// Listener reicht geladene Dateien unveraendert an den Registerdaten-Import. blob:-Adressen liest eine Bruecke im
// Seitenkontext, data:-Adressen dekodiert die Engine. Die Cookies der Sitzung gehoeren zum Download.

// Auf Android hat die App fuer die Quellen einen eigenen Browser.
fun quellenImBrowser(): Boolean = true

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun QuellenBrowser(
    adresse: String,
    endungen: List<String>,
    muster: String,
    modifier: Modifier,
    onDatei: (name: String, daten: ByteArray) -> Unit,
    onFehler: () -> Unit,
    onSchliessen: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val datei by rememberUpdatedState(onDatei)
    val fehler by rememberUpdatedState(onFehler)
    val schliessen by rememberUpdatedState(onSchliessen)
    val json = remember { Json { ignoreUnknownKeys = true } }
    // Rueckrufe der Bruecke kommen aus einem Nebenthread und gehen zuerst auf den Hauptthread
    val handler = remember { Handler(Looper.getMainLooper()) }
    var web by remember(adresse) { mutableStateOf<WebView?>(null) }

    // Zurueck geht zuerst eine Ebene in der Seite zurueck, erst dann schliesst der Bildschirm.
    BackHandler(enabled = true) {
        val w = web
        if (w != null && w.canGoBack()) w.goBack() else schliessen()
    }

    Column(modifier) {
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // Fremde Seiten: kein Zugriff auf Dateien und Inhalte des Geraets, kein Standort
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.setGeolocationEnabled(false)
                    // Allgemeine Browser-Kennung, keine Angaben zum Geraet oder Nutzer
                    settings.userAgentString = QUELLEN_KENNUNG
                    // Cookies erlauben, damit Pruefungen der Seite loesbar sind und beim Download mitgehen
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    val bruecke = BlobBruecke(
                        UUID.randomUUID().toString(),
                        { name, daten -> handler.post { datei(name, daten) } },
                        { handler.post { fehler() } },
                    )
                    addJavascriptInterface(bruecke, "TroeteDatei")
                    webViewClient = object : WebViewClient() {
                        // Nur Webseiten oeffnen, keine Schemata wie intent:, market:, file: oder javascript:
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val schema = request.url.scheme?.lowercase()
                            return schema != "https" && schema != "http"
                        }

                        // Stuerzt der Seitenprozess ab, endet nur die Ansicht, nicht die App
                        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                            (view.parent as? ViewGroup)?.removeView(view)
                            view.destroy()
                            web = null
                            fehler()
                            return true
                        }
                    }
                    setDownloadListener { url, _, inhalt, _, _ ->
                        val name = SelbstabrufDatei.dateiname(inhalt, url)
                        when {
                            url.startsWith("data:", true) -> {
                                val bytes = SelbstabrufDatei.dataUrl(url)
                                if (bytes != null) datei(name, bytes) else fehler()
                            }
                            // blob: liegt im Seitenkontext; das Skript liest die Adresse und meldet die Bytes Base64-kodiert
                            url.startsWith("blob:", true) -> {
                                bruecke.wartet = true
                                evaluateJavascript(blobSkript(url, bruecke.token, name), null)
                            }
                            url.startsWith("http", true) -> {
                                val referer = this.url
                                scope.launch {
                                    try {
                                        val bytes = withContext(Dispatchers.IO) { quelldateiLaden(url, referer) }
                                        datei(name, bytes)
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        fehler()
                                    }
                                }
                            }
                            // alle anderen Schemata bleiben unberuehrt
                        }
                    }
                    loadUrl(adresse)
                    web = this
                }
            },
        )
        // HTML-Quellen: die Web-Ansicht kann ihre Seite nicht speichern, deshalb liest die Schaltflaeche das aktuelle HTML
        if ("html" in endungen) TextButton(onClick = {
            val w = web ?: run { fehler(); return@TextButton }
            w.evaluateJavascript("document.documentElement.outerHTML") { v ->
                val text = if (v == null || v == "null") null else runCatching { json.decodeFromString<String>(v) }.getOrNull()
                if (text.isNullOrBlank()) fehler() else datei("seite.html", text.toByteArray(Charsets.UTF_8))
            }
        }) { Text(S.saSeiteUebernehmen) }
    }

    DisposableEffect(adresse) {
        onDispose {
            val w = web
            web = null
            w?.stopLoading()
            (w?.parent as? ViewGroup)?.removeView(w)
            w?.destroy()
        }
    }
}

// Allgemeine Browser-Kennung wie beim Herunterladen, keine Angaben zum Geraet oder Nutzer.
private const val QUELLEN_KENNUNG = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"

// Hoechstgroesse einer Datei, wie bei der Ordner-Beobachtung des Desktops.
private const val MAX_DATEI = 40L * 1024 * 1024

// Datei der Quelle mit der Browser-Kennung, den Cookies der Sitzung und der Seite als Referer laden.
private fun quelldateiLaden(url: String, referer: String?): ByteArray {
    val verb = URL(url).openConnection() as HttpURLConnection
    verb.connectTimeout = 30_000
    verb.readTimeout = 30_000
    verb.setRequestProperty("User-Agent", QUELLEN_KENNUNG)
    CookieManager.getInstance().getCookie(url)?.let { verb.setRequestProperty("Cookie", it) }
    if (referer != null) verb.setRequestProperty("Referer", referer)
    try {
        if (verb.responseCode !in 200..299) throw IOException("HTTP " + verb.responseCode)
        val aus = ByteArrayOutputStream()
        verb.inputStream.use { eingang ->
            val puffer = ByteArray(64 * 1024)
            while (true) {
                val n = eingang.read(puffer)
                if (n < 0) break
                aus.write(puffer, 0, n)
                if (aus.size() > MAX_DATEI) throw IOException("Datei zu gross")
            }
        }
        return aus.toByteArray()
    } finally {
        verb.disconnect()
    }
}

// Skript fuer blob:-Adressen: liest die Adresse wie die Seite selbst und meldet das Ergebnis als data:-URL an die Bruecke.
private fun blobSkript(url: String, token: String, name: String): String =
    "(function(){var x=new XMLHttpRequest();x.open('GET'," + JSONObject.quote(url) + ",true);x.responseType='blob';" +
        "x.onload=function(){var r=new FileReader();r.onloadend=function(){var s=String(r.result);" +
        "TroeteDatei.daten(" + JSONObject.quote(token) + "," + JSONObject.quote(name) + ",s.substring(s.indexOf(',')+1))};" +
        "r.readAsDataURL(x.response)};x.onerror=function(){TroeteDatei.fehler(" + JSONObject.quote(token) + ")};x.send()})()"

// Bruecke fuer blob:-Downloads. Token und wartet-Flag nehmen nur Aufrufe an, solange ein Abruf laeuft; einer fremden
// Seite ist es so nicht moeglich, der App Dateien unterzuschieben.
private class BlobBruecke(
    val token: String,
    private val an: (String, ByteArray) -> Unit,
    private val fehl: () -> Unit,
) {
    @Volatile var wartet = false

    @JavascriptInterface
    fun daten(t: String, name: String, base64: String) {
        if (t != token || !wartet) return
        wartet = false
        val bytes = runCatching { java.util.Base64.getMimeDecoder().decode(base64) }.getOrNull()
        if (bytes == null || bytes.isEmpty()) fehl() else an(name, bytes)
    }

    @JavascriptInterface
    fun fehler(t: String) {
        if (t != token || !wartet) return
        wartet = false
        fehl()
    }
}
