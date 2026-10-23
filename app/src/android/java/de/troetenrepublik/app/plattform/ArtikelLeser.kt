package de.troetenrepublik.app.plattform

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

// Laedt die Artikelseite in einer Web-Ansicht und liest die Absaetze aus (siehe NewsSheet). Ausgelesen wird schon
// waehrend des Ladens und dann alle halbe Sekunde, bis textDa gilt, nach dem Laden noch kurz fuer nachgeladene Teile.
// Danach meldet onEnde das Ende. Mit auslesen = false zeigt die Ansicht die Seite nur.
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ArtikelLeser(
    adresse: String,
    wikipedia: Boolean,
    auslesen: Boolean,
    modifier: Modifier,
    textDa: () -> Boolean,
    onAbsaetze: (List<String>) -> Unit,
    onEnde: () -> Unit,
) {
    val json = remember { Json { ignoreUnknownKeys = true } }
    var web by remember(adresse) { mutableStateOf<WebView?>(null) }
    var pageDone by remember(adresse) { mutableStateOf(false) }
    val aufnehmen by rememberUpdatedState(onAbsaetze)
    val ende by rememberUpdatedState(onEnde)
    val fertig by rememberUpdatedState(textDa)

    fun extract(view: WebView) {
        if (!auslesen) return
        view.evaluateJavascript(if (wikipedia) WIKI_SCRIPT else EXTRACT_SCRIPT) { value ->
            runCatching {
                val inner = json.decodeFromString<String>(value)
                aufnehmen(json.decodeFromString<List<String>>(inner))
            }
        }
    }

    LaunchedEffect(web, adresse) {
        val view = web ?: return@LaunchedEffect
        if (!auslesen) return@LaunchedEffect
        var nachEnde = 0
        repeat(60) {
            delay(500)
            if (fertig()) return@LaunchedEffect
            extract(view)
            if (pageDone && ++nachEnde >= 4) {
                ende()
                return@LaunchedEffect
            }
        }
        ende()
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                // Fremde Seiten: kein Zugriff auf Dateien und Inhalte des Geraets, kein Standort
                settings.allowContentAccess = false
                settings.allowFileAccess = false
                settings.setGeolocationEnabled(false)
                webViewClient = object : WebViewClient() {
                    // Nur Webseiten oeffnen, keine Schemata wie intent:, market:, file:, content: oder javascript:
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val schema = request.url.scheme?.lowercase()
                        return schema != "https" && schema != "http"
                    }

                    // Stuerzt der Seitenprozess ab, endet nur die Ansicht, nicht die App
                    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                        (view.parent as? ViewGroup)?.removeView(view)
                        view.destroy()
                        web = null
                        ende()
                        return true
                    }

                    override fun onPageCommitVisible(view: WebView, url: String?) {
                        extract(view)
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        pageDone = true
                        extract(view)
                    }
                }
                loadUrl(adresse)
                web = this
            }
        },
    )
}

// Absaetze eines Wikipedia-Artikels, auch aus eingeklappten Abschnitten der mobilen Seite (deshalb textContent
// statt innerText). Ohne style-Elemente (die Lautschrift im ersten Absatz traegt eines), Fussnoten und weiche Trennstriche.
private const val WIKI_SCRIPT = """
(function(){
  var root = document.querySelector('#mw-content-text') || document.body;
  var ps = Array.prototype.slice.call(root.querySelectorAll('p')).map(function(el){
    var c = el.cloneNode(true);
    Array.prototype.slice.call(c.querySelectorAll('style, script, sup, .mw-editsection, .noprint')).forEach(function(x){ x.parentNode.removeChild(x); });
    return (c.textContent || '').replace(/­/g, '').replace(/\[\d+\]/g, '').replace(/\s+/g, ' ').trim();
  }).filter(function(t){ return t.length > 50; });
  var seen = {}; var out = [];
  for (var j = 0; j < ps.length && out.length < 80; j++) { if (!seen[ps[j]]) { seen[ps[j]] = 1; out.push(ps[j]); } }
  return JSON.stringify(out);
})()
"""

// Absaetze aus der geladenen Seite: zuerst der Artikelbereich, sonst die ganze Seite.
private const val EXTRACT_SCRIPT = """
(function(){
  function txt(el){ return (el.innerText || '').replace(/\s+/g,' ').trim(); }
  var roots = ['article', '[class*="caas-body"]', '[class*="article-body"]', '[class*="article__body"]', '[class*="articleBody"]', '[itemprop="articleBody"]', 'main'];
  var root = null; var best = 1;
  for (var i = 0; i < roots.length; i++) {
    var els = document.querySelectorAll(roots[i]);
    for (var k = 0; k < els.length; k++) { var n = els[k].querySelectorAll('p').length; if (n > best) { best = n; root = els[k]; } }
  }
  if (!root) root = document.body;
  var ps = Array.prototype.slice.call(root.querySelectorAll('p')).map(txt).filter(function(t){ return t.length > 50 && !/cookie|Cookies|consent|Einwilligung|privacy/i.test(t); });
  var seen = {}; var out = [];
  for (var j = 0; j < ps.length && out.length < 60; j++) { if (!seen[ps[j]]) { seen[ps[j]] = 1; out.push(ps[j]); } }
  return JSON.stringify(out);
})()
"""
