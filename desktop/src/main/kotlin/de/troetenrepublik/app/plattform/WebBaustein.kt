package de.troetenrepublik.app.plattform

import de.troetenrepublik.app.engine.WissenQuellen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.friwi.jcefmaven.CefAppBuilder
import me.friwi.jcefmaven.EnumProgress
import org.cef.CefApp
import org.cef.CefClient
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.handler.CefDisplayHandlerAdapter
import java.awt.BorderLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.swing.JFrame
import javax.swing.WindowConstants

// Eingebetteter Browser fuer MarketWatch-Artikel und das Finanzwissen am Rechner. Am Rechner gibt es keine Web-Ansicht
// wie auf Android, deshalb laedt die App bei Bedarf einmalig Chromium (JCEF ueber jcefmaven, Fassung 152) von Maven
// Central in ihren Datenordner.
// Ein MarketWatch-Artikel oeffnet als Originalseite in einem sichtbaren Fenster (artikelOeffnen); was die Seite dort
// zeigt, sieht und bedient die Person selbst. Ist die Seite geladen, liest „Nur Text anzeigen“ auf Tipp den sichtbaren
// Text aus dieser Seite (artikelText), ohne neuen Abruf.
// Im Fenster des Finanzwissens meldet die Seite angetippte Quellen ueber die Konsole (wissenQuelle).
object WebBaustein {

    // Nur Maven Central (keine weiteren Spiegel); die Datei kommt so, wie jcefmaven sie veroeffentlicht.
    private const val SPIEGEL = "https://repo.maven.apache.org/maven2/me/friwi/jcef-natives-{platform}/{tag}/jcef-natives-{platform}-{tag}.jar"

    private val ordner: File get() = File(Plattform.daten, "browser")

    // Etwa so viel laedt die App beim ersten Mal (Megabyte, je nach System).
    val groesseMb: Int
        get() {
            val os = System.getProperty("os.name").orEmpty()
            return if (os.startsWith("Windows")) 170 else if (os.startsWith("Mac")) 140 else 150
        }

    // Hosts im Netz, auf die der eingebettete Browser navigieren darf (MarketWatch und die Hosts seiner
    // Bestaetigungsseite). Das Finanzwissen laeuft ueber den lokalen Server der App (lokal).
    private val ERLAUBT = listOf("marketwatch.com", "captcha-delivery.com", "datadome.co")

    private fun host(link: String): String? = runCatching { java.net.URI(link.trim()).host?.lowercase() }.getOrNull()

    private fun erlaubt(link: String): Boolean {
        if (!link.startsWith("https://", ignoreCase = true)) return false
        val h = host(link) ?: return false
        return ERLAUBT.any { h == it || h.endsWith(".$it") }
    }

    // Finanzwissen vom Webserver der App, nur auf 127.0.0.1 (Fenster seit 5.93).
    private fun lokal(link: String): Boolean = runCatching {
        val u = java.net.URI(link.trim())
        u.scheme == "http" && u.host == "127.0.0.1" && u.port == Finanzwissen.port && u.path.orEmpty().startsWith("/finanzwissen/")
    }.getOrDefault(false)

    // MarketWatch-Artikel, die am Rechner im Fenster des eingebetteten Browsers oeffnen.
    fun noetig(link: String): Boolean =
        erlaubt(link) && host(link)?.let { it == "marketwatch.com" || it.endsWith(".marketwatch.com") } == true

    val installiert: Boolean get() = File(ordner, "install.lock").isFile

    private fun bauer(fortschritt: ((Float) -> Unit)? = null) = CefAppBuilder().apply {
        setInstallDir(ordner)
        setMirrors(listOf(SPIEGEL))
        // Zeichnen in die Flaeche des App-Fensters (Offscreen, siehe neuesFenster) und ohne Ton; eigener Speicher fuer
        // Cookies wie in einem Browser
        cefSettings.windowless_rendering_enabled = true
        cefSettings.log_severity = CefSettings.LogSeverity.LOGSEVERITY_DISABLE
        cefSettings.root_cache_path = File(ordner, "profil").absolutePath
        cefSettings.cache_path = File(ordner, "profil/standard").absolutePath
        addJcefArgs("--mute-audio", "--autoplay-policy=user-gesture-required")
        if (fortschritt != null) {
            setProgressHandler { stufe, prozent ->
                if (stufe == EnumProgress.DOWNLOADING && prozent >= 0) fortschritt((prozent / 100f).coerceIn(0f, 1f))
                if (stufe == EnumProgress.EXTRACTING || stufe == EnumProgress.INSTALL) fortschritt(1f)
            }
        }
    }

    // Chromium laden und entpacken; fortschritt von 0 bis 1. Gibt an, ob es geklappt hat.
    suspend fun installieren(fortschritt: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        runCatching { bauer(fortschritt).install(); installiert }.getOrDefault(false)
    }

    private var app: CefApp? = null
    private var client: CefClient? = null

    // Letzte Meldung je Kennung: Absaetze der Seite.
    private val meldungen = ConcurrentHashMap<String, List<String>>()

    // Empfaenger fuer angetippte Quellen im Fenster des Finanzwissens (Link, Titel); ohne Empfaenger oeffnet der Standardbrowser.
    @Volatile var wissenQuelle: ((String, String) -> Unit)? = null

    @Synchronized
    private fun client(): CefClient {
        client?.let { return it }
        val a = bauer().build()
        val c = a.createClient()
        c.addDisplayHandler(object : CefDisplayHandlerAdapter() {
            override fun onConsoleMessage(browser: CefBrowser?, level: CefSettings.LogSeverity?, message: String?, source: String?, line: Int): Boolean {
                val m = message ?: return false
                if (DIAGNOSE) System.err.println("[WebBaustein] " + m.take(400))
                if (m.startsWith(QUELLE)) {
                    if (browser != null && browser === wissen?.second) {
                        val o = runCatching { Json.parseToJsonElement(m.removePrefix(QUELLE)) as JsonObject }.getOrNull()
                        val u = (o?.get("u") as? JsonPrimitive)?.content
                        val t = (o?.get("t") as? JsonPrimitive)?.content
                        val link = WissenQuellen.adresse(u)
                        if (link != null) {
                            val ziel = wissenQuelle
                            if (ziel != null) javax.swing.SwingUtilities.invokeLater { ziel(link, WissenQuellen.titel(t)) } else Thread { Browser.oeffnen(link) }.start()
                        }
                    }
                    return true
                }
                val i = m.indexOf(':')
                if (!m.startsWith(KENNUNG) || i < 0) return false
                runCatching {
                    val o = Json.parseToJsonElement(m.substring(i + 1)) as JsonObject
                    val p = (o["p"] as JsonArray).mapNotNull { (it as? JsonPrimitive)?.content }
                    meldungen[m.substring(0, i)] = p
                }
                return true
            }
        })
        // Ladezustand des Artikelfensters fuer artikelGeladen
        c.addLoadHandler(object : org.cef.handler.CefLoadHandlerAdapter() {
            override fun onLoadingStateChange(browser: CefBrowser?, isLoading: Boolean, canGoBack: Boolean, canGoForward: Boolean) {
                // Das Fenster des Finanzwissens zaehlt nicht; solange artikel noch nicht gesetzt ist, gilt die Meldung dem
                // gerade entstehenden Artikelfenster.
                if (browser == null || browser === wissen?.second) return
                if (artikel == null || browser === artikel?.second) artikelLaedt = isLoading
            }
        })
        // Nur die erlaubten Seiten: keine Navigation anderswohin, keine neuen Fenster, keine Downloads. Ein Klick auf
        // einen Link zu einer anderen Seite oeffnet diese im Standardbrowser.
        c.addRequestHandler(object : org.cef.handler.CefRequestHandlerAdapter() {
            override fun onBeforeBrowse(browser: CefBrowser?, frame: org.cef.browser.CefFrame?, request: org.cef.network.CefRequest?, userGesture: Boolean, isRedirect: Boolean): Boolean {
                val url = request?.url ?: return true
                if (frame?.isMain != true) return !(url.startsWith("https://", true) || url.startsWith("about:", true) || lokal(url))
                if (erlaubt(url) || lokal(url)) return false
                if (userGesture && url.startsWith("https://", true)) Thread { Browser.oeffnen(url) }.start()
                return true
            }
        })
        c.addLifeSpanHandler(object : org.cef.handler.CefLifeSpanHandlerAdapter() {
            // Links mit neuem Fenster (target="_blank", etwa YouTube im Finanzwissen) oeffnen im Standardbrowser
            override fun onBeforePopup(browser: CefBrowser?, frame: org.cef.browser.CefFrame?, targetUrl: String?, targetFrameName: String?): Boolean {
                val u = targetUrl ?: return true
                if (u.startsWith("https://", true) && !erlaubt(u) && !lokal(u)) Thread { Browser.oeffnen(u) }.start()
                return true
            }
        })
        c.addDownloadHandler(object : org.cef.handler.CefDownloadHandlerAdapter() {
            override fun onBeforeDownload(browser: CefBrowser?, item: org.cef.callback.CefDownloadItem?, suggestedName: String?, callback: org.cef.callback.CefBeforeDownloadCallback?): Boolean = true
        })
        app = a
        client = c
        return c
    }

    // Das offene Fenster des Finanzwissens mit seinem Browser; null, wenn keines offen ist.
    @Volatile private var wissen: Pair<JFrame, CefBrowser>? = null

    // Seite link in einem eigenen Fenster der App (Finanzwissen, seit 5.93); kehrt zurueck, sobald das Fenster offen ist.
    // Ist es schon offen, kommt es nach vorn und laedt die Adresse neu (etwa nach einem Wechsel des Themas).
    suspend fun fenster(link: String, titel: String): Boolean {
        if (!installiert) return false
        val c = withContext(Dispatchers.IO) { runCatching { client() }.getOrNull() } ?: return false
        withContext(Dispatchers.Swing) {
            val offen = wissen
            if (offen != null) {
                offen.second.loadURL(link)
                offen.first.toFront()
            } else {
                wissen = neuesFenster(c, link, titel) { wissen = null }
            }
        }
        return true
    }

    // Das offene Artikelfenster mit seinem Browser; null, wenn keines offen ist.
    @Volatile private var artikel: Pair<JFrame, CefBrowser>? = null

    // Wahr, solange die Seite im Artikelfenster laedt (Lade-Handler in client()).
    @Volatile
    private var artikelLaedt = false

    // Die Seite im Artikelfenster ist fertig geladen.
    val artikelGeladen: Boolean get() = artikel != null && !artikelLaedt

    // Das Artikelfenster ist offen.
    val artikelOffen: Boolean get() = artikel != null

    // MarketWatch-Artikel link in einem sichtbaren Fenster; ist es schon offen, laedt es die Adresse und kommt nach vorn.
    // Kehrt zurueck, sobald das Fenster offen ist; false ohne Chromium.
    suspend fun artikelOeffnen(link: String, titel: String): Boolean {
        if (!installiert) return false
        val c = withContext(Dispatchers.IO) { runCatching { client() }.getOrNull() } ?: return false
        withContext(Dispatchers.Swing) {
            artikelLaedt = true
            val offen = artikel
            if (offen != null) {
                offen.second.loadURL(link)
                offen.first.toFront()
            } else {
                artikel = neuesFenster(c, link, titel) { artikel = null; artikelLaedt = false }
            }
        }
        return true
    }

    // Absaetze der Seite im Artikelfenster, gelesen auf Tipp aus der geladenen Seite; null ohne Fenster oder solange sie
    // laedt, leer, wenn sie keinen Text zeigt.
    suspend fun artikelText(): List<String>? {
        val b = artikel?.second ?: return null
        if (artikelLaedt) return null
        val kennung = KENNUNG + System.nanoTime()
        withContext(Dispatchers.Swing) { b.executeJavaScript(SKRIPT.replace("KENNUNG", kennung), b.url, 0) }
        try {
            repeat(25) {
                delay(200)
                meldungen.remove(kennung)?.let { return it }
            }
            return emptyList()
        } finally {
            meldungen.remove(kennung)
        }
    }

    private fun neuesFenster(c: CefClient, link: String, titel: String, onZu: () -> Unit): Pair<JFrame, CefBrowser> {
        // Offscreen: Die Zeichenflaeche reicht Maus und Tastatur an Chromium weiter, denn der
        // Fenster-Browser von JCEF nimmt in der App keine Klicks an.
        val b = c.createBrowser(link, true, false)
        val f = JFrame(titel)
        f.defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
        f.contentPane.add(b.uiComponent, BorderLayout.CENTER)
        f.setSize(1100, 900)
        f.setLocationRelativeTo(runCatching { hauptfenster }.getOrNull())
        f.addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent?) {
                runCatching { b.close(true) }
                onZu()
            }
        })
        f.isVisible = true
        return f to b
    }

    // Beim Beenden der App: Chromium sauber schliessen.
    fun beenden() {
        runCatching { client?.dispose() }
        runCatching { app?.dispose() }
        client = null
        app = null
    }

    private const val KENNUNG = "TROETE_TEXT_"

    // Meldung der Seite beim Tipp auf eine Quelle.
    private const val QUELLE = "TROETE_QUELLE:"


    // Konsolenmeldungen der Seiten mitschreiben (nur zur Fehlersuche, Umgebungsvariable TROETE_WEBDIAG).
    private val DIAGNOSE = !System.getenv("TROETE_WEBDIAG").isNullOrBlank()

    // Absaetze der geladenen Seite wie auf Android (EXTRACT_SCRIPT), gemeldet als „Kennung:JSON“ ueber die Konsole.
    private const val SKRIPT = """
(function(){
  function txt(el){ return (el.innerText || '').replace(/\s+/g,' ').trim(); }
  var roots = ['article', '[class*="caas-body"]', '[class*="article-body"]', '[class*="article__body"]', '[class*="articleBody"]', '[itemprop="articleBody"]', 'main'];
  var root = null; var best = 1;
  for (var i = 0; i < roots.length; i++) {
    var els = document.querySelectorAll(roots[i]);
    for (var k = 0; k < els.length; k++) { var n = els[k].querySelectorAll('p, [data-testid^="paragraph-"]').length; if (n > best) { best = n; root = els[k]; } }
  }
  if (!root) root = document.body;
  var out = [];
  if (root) {
    var ps = Array.prototype.slice.call(root.querySelectorAll('p, [data-testid^="paragraph-"]')).map(txt).filter(function(t){ return t.length > 50 && !/cookie|Cookies|consent|Einwilligung|privacy|Our Standards:|opens new tab|newsletter keeps you informed|Sign up here/i.test(t); });
    var seen = {};
    for (var j = 0; j < ps.length && out.length < 60; j++) { if (!seen[ps[j]]) { seen[ps[j]] = 1; out.push(ps[j]); } }
  }
  console.log('KENNUNG:' + JSON.stringify({p: out}));
})()
"""
}
