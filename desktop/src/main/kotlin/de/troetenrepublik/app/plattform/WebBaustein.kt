package de.troetenrepublik.app.plattform

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
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

// Eingebetteter Browser fuer Seiten, die nur ein echter Browser lesen darf: MarketWatch prueft jeden Abruf per
// Skript und sperrt alle anderen. Am Rechner gibt es keine Web-Ansicht wie auf Android, deshalb laedt die App bei
// Bedarf einmalig Chromium (JCEF ueber jcefmaven, Fassung 152) von Maven Central in ihren Datenordner.
// Gelesen wird ohne Fenster im Hintergrund; ein Skript sammelt die Absaetze wie auf Android und meldet sie ueber die
// Konsole an die App.
// Verlangt die Seite eine Bestaetigung (Captcha von DataDome), liest die App nichts und meldet bestaetigungNoetig. Die
// Person oeffnet dann die Seite in einem Fenster (bestaetigen) und bestaetigt selbst; das Cookie der Seite bleibt im
// Profil des Browsers, danach liest die App wieder ohne Fenster.
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

    // Hosts, auf die der eingebettete Browser navigieren darf (MarketWatch und seine Bestaetigungsseite).
    private val ERLAUBT = listOf("marketwatch.com", "captcha-delivery.com", "datadome.co")

    private fun host(link: String): String? = runCatching { java.net.URI(link.trim()).host?.lowercase() }.getOrNull()

    private fun erlaubt(link: String): Boolean {
        if (!link.startsWith("https://", ignoreCase = true)) return false
        val h = host(link) ?: return false
        return ERLAUBT.any { h == it || h.endsWith(".$it") }
    }

    // Seiten, die hier nur der eingebettete Browser lesen kann. Nie mit verbundenem echten Konto, weil Chromium ohne
    // Sandbox im Prozess der App laeuft; die Seite oeffnet dann im Browser des Systems.
    fun noetig(link: String): Boolean = !de.troetenrepublik.app.market.broker.Netzschutz.brokerVerbunden &&
        erlaubt(link) && host(link)?.let { it == "marketwatch.com" || it.endsWith(".marketwatch.com") } == true

    val installiert: Boolean get() = File(ordner, "install.lock").isFile

    // Beim letzten Lesen zeigte die Seite eine Bestaetigung (Captcha) statt des Artikels.
    @Volatile
    var bestaetigungNoetig: Boolean = false
        private set

    private fun bauer(fortschritt: ((Float) -> Unit)? = null) = CefAppBuilder().apply {
        setInstallDir(ordner)
        setMirrors(listOf(SPIEGEL))
        // Lesen ohne Fenster und ohne Ton; eigener Speicher fuer die Cookies der gelesenen Seiten
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

    // Letzte Meldung je Kennung: Absaetze und ob die Seite eine Bestaetigung verlangt.
    private val meldungen = ConcurrentHashMap<String, Pair<List<String>, Boolean>>()

    @Synchronized
    private fun client(): CefClient {
        client?.let { return it }
        val a = bauer().build()
        val c = a.createClient()
        c.addDisplayHandler(object : CefDisplayHandlerAdapter() {
            override fun onConsoleMessage(browser: CefBrowser?, level: CefSettings.LogSeverity?, message: String?, source: String?, line: Int): Boolean {
                val m = message ?: return false
                if (DIAGNOSE) System.err.println("[WebBaustein] " + m.take(400))
                val i = m.indexOf(':')
                if (!m.startsWith(KENNUNG) || i < 0) return false
                runCatching {
                    val o = Json.parseToJsonElement(m.substring(i + 1)) as JsonObject
                    val p = (o["p"] as JsonArray).mapNotNull { (it as? JsonPrimitive)?.content }
                    val captcha = (o["c"] as? JsonPrimitive)?.booleanOrNull ?: false
                    meldungen[m.substring(0, i)] = p to captcha
                }
                return true
            }
        })
        // Nur die erlaubten Seiten: keine Navigation anderswohin, keine neuen Fenster, keine Downloads
        c.addRequestHandler(object : org.cef.handler.CefRequestHandlerAdapter() {
            override fun onBeforeBrowse(browser: CefBrowser?, frame: org.cef.browser.CefFrame?, request: org.cef.network.CefRequest?, userGesture: Boolean, isRedirect: Boolean): Boolean {
                val url = request?.url ?: return true
                if (frame?.isMain != true) return !(url.startsWith("https://", true) || url.startsWith("about:", true))
                return !erlaubt(url)
            }
        })
        c.addLifeSpanHandler(object : org.cef.handler.CefLifeSpanHandlerAdapter() {
            override fun onBeforePopup(browser: CefBrowser?, frame: org.cef.browser.CefFrame?, targetUrl: String?, targetFrameName: String?): Boolean = true
        })
        c.addDownloadHandler(object : org.cef.handler.CefDownloadHandlerAdapter() {
            override fun onBeforeDownload(browser: CefBrowser?, item: org.cef.callback.CefDownloadItem?, suggestedName: String?, callback: org.cef.callback.CefBeforeDownloadCallback?): Boolean = true
        })
        app = a
        client = c
        return c
    }

    // Absaetze der Seite link mit dem eingebetteten Browser; null, wenn Chromium fehlt, die Seite keinen Text zeigt oder
    // eine Bestaetigung verlangt (bestaetigungNoetig). Ausgelesen wird alle halbe Sekunde, bis genug Text da ist,
    // hoechstens 25 Sekunden.
    suspend fun lesen(link: String): List<String>? {
        if (!installiert) return null
        val c = withContext(Dispatchers.IO) { runCatching { client() }.getOrNull() } ?: return null
        val kennung = KENNUNG + System.nanoTime()
        val browser = withContext(Dispatchers.Swing) {
            c.createBrowser(link, true, false).also { b ->
                b.uiComponent.setSize(1280, 1800)
                b.createImmediately()
            }
        }
        try {
            var captchaMal = 0
            var zuletzt = -1
            var gleich = 0
            repeat(50) {
                delay(500)
                withContext(Dispatchers.Swing) { browser.executeJavaScript(SKRIPT.replace("KENNUNG", kennung), link, 0) }
                val (l, captcha) = meldungen[kennung] ?: (emptyList<String>() to false)
                // Genug Text und seit anderthalb Sekunden keine neuen Absaetze (die Seite laedt den Artikel in Teilen)
                if (l.size == zuletzt) gleich++ else gleich = 0
                zuletzt = l.size
                if (l.size >= 2 && l.sumOf { it.length } > 300 && (gleich >= 3 || l.size >= 60)) {
                    bestaetigungNoetig = false
                    return l
                }
                // Bestaetigungsseite: drei Sekunden lang ohne Text, dann aufgeben und die Person fragen
                if (captcha) captchaMal++ else captchaMal = 0
                if (captchaMal >= 6) {
                    bestaetigungNoetig = true
                    return null
                }
            }
            return meldungen[kennung]?.first?.takeIf { it.size >= 2 }
        } finally {
            meldungen.remove(kennung)
            withContext(Dispatchers.Swing) { runCatching { browser.close(true) } }
        }
    }

    // Seite link in einem Fenster des eingebetteten Browsers zeigen, damit die Person die Bestaetigung selbst erledigt
    // und den Artikel dort liest. Kehrt zurueck, wenn das Fenster geschlossen ist.
    suspend fun bestaetigen(link: String, titel: String): Boolean {
        if (!installiert) return false
        val c = withContext(Dispatchers.IO) { runCatching { client() }.getOrNull() } ?: return false
        val zu = CompletableDeferred<Unit>()
        withContext(Dispatchers.Swing) {
            // Offscreen wie beim Lesen: Die Zeichenflaeche reicht Maus und Tastatur an Chromium weiter, denn der
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
                    zu.complete(Unit)
                }
            })
            f.isVisible = true
        }
        zu.await()
        bestaetigungNoetig = false
        return true
    }

    // Beim Beenden der App: Chromium sauber schliessen.
    fun beenden() {
        runCatching { client?.dispose() }
        runCatching { app?.dispose() }
        client = null
        app = null
    }

    private const val KENNUNG = "TROETE_TEXT_"


    // Konsolenmeldungen der Seiten mitschreiben (nur zur Fehlersuche, Umgebungsvariable TROETE_WEBDIAG).
    private val DIAGNOSE = !System.getenv("TROETE_WEBDIAG").isNullOrBlank()

    // Absaetze des Artikels wie auf Android (EXTRACT_SCRIPT), gemeldet als „Kennung:JSON“ ueber die Konsole; „c“ ist wahr,
    // wenn die Seite statt des Artikels eine Bestaetigung (Captcha von DataDome) zeigt.
    private const val SKRIPT = """
(function(){
  function txt(el){ return (el.innerText || '').replace(/\s+/g,' ').trim(); }
  var captcha = !!document.querySelector('iframe[src*="captcha-delivery"]');
  var roots = ['article', '[class*="caas-body"]', '[class*="article-body"]', '[class*="article__body"]', '[class*="articleBody"]', '[itemprop="articleBody"]', 'main'];
  var root = null; var best = 1;
  for (var i = 0; i < roots.length; i++) {
    var els = document.querySelectorAll(roots[i]);
    for (var k = 0; k < els.length; k++) { var n = els[k].querySelectorAll('p').length; if (n > best) { best = n; root = els[k]; } }
  }
  if (!root) root = document.body;
  var out = [];
  if (root) {
    var ps = Array.prototype.slice.call(root.querySelectorAll('p')).map(txt).filter(function(t){ return t.length > 50 && !/cookie|Cookies|consent|Einwilligung|privacy/i.test(t); });
    var seen = {};
    for (var j = 0; j < ps.length && out.length < 60; j++) { if (!seen[ps[j]]) { seen[ps[j]] = 1; out.push(ps[j]); } }
  }
  console.log('KENNUNG:' + JSON.stringify({p: out, c: captcha}));
})()
"""
}
