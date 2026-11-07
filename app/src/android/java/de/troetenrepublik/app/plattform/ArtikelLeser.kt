package de.troetenrepublik.app.plattform

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import de.troetenrepublik.app.engine.Lesemodus
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

// Laedt die Artikelseite in einer Web-Ansicht und liest die Absaetze aus (siehe NewsSheet). Ausgelesen wird schon
// waehrend des Ladens und dann alle halbe Sekunde, bis textDa gilt, nach dem Laden noch kurz fuer nachgeladene Teile.
// Danach meldet onEnde das Ende. Mit auslesen = false zeigt die Ansicht die Seite nur.
// onGeladen meldet den Ladezustand, leseAnfrage liest auf Tipp nur den sichtbaren Text aus der geladenen Seite.
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun ArtikelLeser(
    adresse: String,
    wikipedia: Boolean,
    auslesen: Boolean,
    modifier: Modifier,
    textDa: () -> Boolean,
    onAbsaetze: (List<String>) -> Unit,
    onEnde: () -> Unit,
    // Ladezustand der Seite: false beim Start einer Navigation, true am Ladeende (nur mit Web-Ansicht).
    onGeladen: (Boolean) -> Unit = {},
    // Jede Erhoehung liest einmal den sichtbaren Text aus der schon geladenen Seite (kein neuer Abruf).
    leseAnfrage: Int = 0,
    // Ergebnis einer leseAnfrage, leer, wenn nichts Lesbares zu sehen ist.
    onLesemodus: (List<String>) -> Unit = {},
) {
    val json = remember { Json { ignoreUnknownKeys = true } }
    var web by remember(adresse) { mutableStateOf<WebView?>(null) }
    var pageDone by remember(adresse) { mutableStateOf(false) }
    // Zaehler der Navigationen, damit eine laufende Auslese ihr Ergebnis bei neuer Navigation verwirft.
    var navigation by remember(adresse) { mutableStateOf(0) }
    // Zuletzt abgearbeitete leseAnfrage: beim Neueintritt in die Komposition laeuft die alte nicht erneut; bei neuer
    // Adresse beginnt die Zaehlung beim aktuellen Stand.
    var erledigt by remember(adresse) { mutableStateOf(leseAnfrage) }
    val aufnehmen by rememberUpdatedState(onAbsaetze)
    val ende by rememberUpdatedState(onEnde)
    val fertig by rememberUpdatedState(textDa)
    val geladenMelden by rememberUpdatedState(onGeladen)
    val lesemodusMelden by rememberUpdatedState(onLesemodus)

    fun extract(view: WebView) {
        if (!auslesen) return
        view.evaluateJavascript(if (wikipedia) WIKI_SCRIPT else EXTRACT_SCRIPT) { value ->
            runCatching {
                val inner = json.decodeFromString<String>(value)
                aufnehmen(json.decodeFromString<List<String>>(inner))
            }
        }
    }

    // Nur der sichtbare Text der geladenen Seite, unabhaengig von auslesen und ohne neuen Abruf.
    LaunchedEffect(leseAnfrage) {
        if (leseAnfrage <= erledigt) return@LaunchedEffect
        erledigt = leseAnfrage
        val view = web
        if (view == null) { lesemodusMelden(emptyList()); return@LaunchedEffect }
        val stand = navigation
        view.evaluateJavascript(if (wikipedia) WIKI_SCRIPT else SICHTBAR_SCRIPT) { value ->
            // Neue Navigation inzwischen: Ergebnis verwerfen, nichts melden.
            if (stand != navigation) return@evaluateJavascript
            runCatching {
                val inner = json.decodeFromString<String>(value)
                lesemodusMelden(json.decodeFromString<List<String>>(inner))
            }.onFailure { lesemodusMelden(emptyList()) }
        }
    }

    // Verlaesst die Ansicht die Komposition, ist keine Seite mehr geladen.
    DisposableEffect(Unit) {
        onDispose { geladenMelden(false) }
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
                // Senkrechtes Scrollen in der Seite soll das Blatt nicht mitziehen
                setOnTouchListener { v, e ->
                    if (e.actionMasked == MotionEvent.ACTION_DOWN) v.parent?.requestDisallowInterceptTouchEvent(true)
                    false
                }
                webViewClient = object : WebViewClient() {
                    // Nur Webseiten oeffnen, keine Schemata wie intent:, market:, file:, content: oder javascript:
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val schema = request.url.scheme?.lowercase()
                        return schema != "https" && schema != "http"
                    }

                    // Stuerzt der Seitenprozess ab, endet nur die Ansicht, nicht die App; laufende Auslese und
                    // Ladezustand werden verworfen.
                    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                        (view.parent as? ViewGroup)?.removeView(view)
                        view.destroy()
                        web = null
                        navigation++
                        geladenMelden(false)
                        ende()
                        return true
                    }

                    // Neue Navigation: laufende Auslese verwerfen, erst am Ladeende ist die Seite wieder lesbar.
                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                        navigation++
                        geladenMelden(false)
                    }

                    override fun onPageCommitVisible(view: WebView, url: String?) {
                        extract(view)
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        // Google-News-Links laden erst eine Zwischenseite (news.google.com, in der EU oft
                        // consent.google.com); erst die Zielseite gilt als geladen.
                        if (!Lesemodus.zwischenseite(url)) { pageDone = true; geladenMelden(true) }
                        extract(view)
                    }
                }
                loadUrl(adresse)
                web = this
            }
        },
        // Nach dem Schliessen laufen Skripte, Netz und Ton der Seite nicht weiter. runCatching, weil
        // onRenderProcessGone die Ansicht schon zerstoert haben kann.
        onRelease = { v -> runCatching { v.stopLoading(); v.destroy() } },
    )
}

// Absaetze eines Wikipedia-Artikels, auch aus eingeklappten Abschnitten der mobilen Seite (deshalb textContent
// statt innerText). Ohne style-Elemente (die Lautschrift im ersten Absatz traegt eines), Fussnoten und weiche Trennstriche.
private const val WIKI_SCRIPT = """
(function(){
  var root = document.querySelector('#mw-content-text') || document.body;
  var ps = Array.prototype.slice.call(root.querySelectorAll('p, [data-testid^="paragraph-"]')).map(function(el){
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
    for (var k = 0; k < els.length; k++) { var n = els[k].querySelectorAll('p, [data-testid^="paragraph-"]').length; if (n > best) { best = n; root = els[k]; } }
  }
  if (!root) root = document.body;
  var ps = Array.prototype.slice.call(root.querySelectorAll('p, [data-testid^="paragraph-"]')).map(txt).filter(function(t){ return t.length > 50 && !/cookie|Cookies|consent|Einwilligung|privacy|Our Standards:|opens new tab|newsletter keeps you informed|Sign up here/i.test(t); });
  var seen = {}; var out = [];
  for (var j = 0; j < ps.length && out.length < 60; j++) { if (!seen[ps[j]]) { seen[ps[j]] = 1; out.push(ps[j]); } }
  return JSON.stringify(out);
})()
"""

// Nur Absaetze, die auf der Seite wirklich zu sehen sind; liegt ein grosses festes Element ueber der Seite und ist das
// Scrollen gesperrt (Bezahlschranke, Einwilligung), liefert es nichts. Was die Seite verbirgt, bleibt verborgen.
private const val SICHTBAR_SCRIPT = """
(function(){
  if (!document.body) return JSON.stringify([]);
  var bw = document.documentElement.clientWidth, bh = document.documentElement.clientHeight;
  var flaeche = bw * bh;
  function gesperrt(root){
    var ds = getComputedStyle(document.documentElement).overflowY;
    var bs = getComputedStyle(document.body).overflowY;
    if ((ds !== 'hidden' && ds !== 'clip') && (bs !== 'hidden' && bs !== 'clip')) return false;
    var alle = document.querySelectorAll('body *');
    for (var i = 0; i < alle.length; i++) {
      var s = getComputedStyle(alle[i]);
      if (s.position !== 'fixed' && s.position !== 'sticky') continue;
      // Fester Container um den Artikel selbst: der Scrollbereich der Seite, kein Overlay.
      if (root !== document.body && alle[i].contains(root)) continue;
      if (s.display === 'none' || s.visibility === 'hidden' || parseFloat(s.opacity) === 0) continue;
      var b = alle[i].getBoundingClientRect();
      var w = Math.max(0, Math.min(b.right, bw) - Math.max(b.left, 0));
      var h = Math.max(0, Math.min(b.bottom, bh) - Math.max(b.top, 0));
      if (w * h > flaeche * 0.4) return true;
    }
    return false;
  }
  function verdeckt(el, r){
    var x = Math.max(0, Math.min(r.left + r.width / 2, bw - 1));
    var y = r.top + Math.min(r.height / 2, 10);
    if (y < 0 || y >= bh) return false;
    var t = document.elementFromPoint(x, y);
    if (!t) return true;
    if (t === el || el.contains(t) || t.contains(el)) return false;
    // Feste oder klebende Leiste: nur grosse zaehlen, kleine laesst die Seite wegscrollen
    for (var o = t; o && o !== document.body; o = o.parentElement) {
      var s = getComputedStyle(o);
      if (s.position === 'fixed' || s.position === 'sticky') {
        var b = o.getBoundingClientRect();
        return b.width * b.height > flaeche * 0.4;
      }
    }
    return true;
  }
  function sichtbar(el){
    if (typeof el.checkVisibility === 'function') { if (!el.checkVisibility({checkOpacity: true, checkVisibilityCSS: true})) return false; }
    else if (el.getClientRects().length === 0) return false;
    var r = el.getBoundingClientRect();
    if (r.width < 1 || r.height < 1) return false;
    if (r.right <= 0 || r.left >= bw) return false;
    if (verdeckt(el, r)) return false;
    // Scrollt der Artikel im eigenen Behaelter, verdeckt body mit overflow hidden keinen Text.
    var scrollbar = false;
    var k = el;
    while (k) {
      var s = getComputedStyle(k);
      if (s.visibility === 'hidden' || s.visibility === 'collapse' || s.display === 'none' || parseFloat(s.opacity) === 0) return false;
      if (s.filter.indexOf('blur') >= 0) return false;
      if ((s.maskImage || s.webkitMaskImage || 'none') !== 'none') return false;
      var cp = (s.clipPath || 'none').split(' ').join('');
      if (cp !== 'none' && cp !== 'inset(0px)' && cp !== 'inset(0)' && cp !== 'inset(0%)') return false;
      if (k === el && (s.color === 'rgba(0, 0, 0, 0)' || s.color === 'transparent')) return false;
      if ((s.overflowY === 'auto' || s.overflowY === 'scroll') && k.scrollHeight > k.clientHeight + 1) scrollbar = true;
      var aussen = (k === document.body || k === document.documentElement);
      if (k !== el && !(aussen && scrollbar) && (s.overflowY === 'hidden' || s.overflowY === 'clip')) {
        var b = k.getBoundingClientRect();
        if (r.top < b.top - 1 || r.bottom > b.bottom + 1) return false;
      }
      k = k.parentElement;
    }
    return true;
  }
  var roots = ['article', '[class*="caas-body"]', '[class*="article-body"]', '[class*="article__body"]', '[class*="articleBody"]', '[itemprop="articleBody"]', 'main'];
  var root = null; var best = 1;
  for (var i = 0; i < roots.length; i++) {
    var els = document.querySelectorAll(roots[i]);
    for (var k = 0; k < els.length; k++) { var n = els[k].querySelectorAll('p, [data-testid^="paragraph-"]').length; if (n > best) { best = n; root = els[k]; } }
  }
  if (!root) root = document.body;
  if (gesperrt(root)) return JSON.stringify([]);
  var ps = Array.prototype.slice.call(root.querySelectorAll('p, [data-testid^="paragraph-"]')).filter(function(el){ return sichtbar(el); }).map(function(el){ return (el.innerText || '').replace(/\s+/g,' ').trim(); }).filter(function(t){ return t.length > 50 && !/cookie|Cookies|consent|Einwilligung|privacy|Our Standards:|opens new tab|newsletter keeps you informed|Sign up here/i.test(t); });
  var seen = {}; var out = [];
  for (var j = 0; j < ps.length && out.length < 60; j++) { if (!seen[ps[j]]) { seen[ps[j]] = 1; out.push(ps[j]); } }
  return JSON.stringify(out);
})()
"""
