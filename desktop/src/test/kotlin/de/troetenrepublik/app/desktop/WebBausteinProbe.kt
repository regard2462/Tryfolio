package de.troetenrepublik.app.desktop

import de.troetenrepublik.app.plattform.Plattform
import de.troetenrepublik.app.plattform.WebBaustein
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

// Laedt Chromium (einmal, etwa 170 MB unter Windows) und liest einen aktuellen MarketWatch-Artikel ohne Fenster und
// ohne Ton. Laeuft nur mit TROETE_WEBBAUSTEIN=<Ordner>, dort bleibt Chromium liegen, und nur allein gestartet.
class WebBausteinProbe {

    @Test
    fun marketWatchOhneFenster() {
        val ordner = System.getenv("TROETE_WEBBAUSTEIN")
        assumeTrue("nur mit TROETE_WEBBAUSTEIN", !ordner.isNullOrBlank())
        // Der Offscreen-Browser braucht AWT, die uebrigen Desktop-Tests laufen headless.
        System.setProperty("java.awt.headless", "false")
        Thread.setDefaultUncaughtExceptionHandler { th, e -> println("Ausnahme in ${th.name}: $e " + e.stackTrace.take(6).joinToString(" | ")) }
        Plattform.init(File(ordner!!))
        val start = System.currentTimeMillis()
        val ok = runBlocking { WebBaustein.installieren { } }
        println("Chromium bereit: $ok nach ${(System.currentTimeMillis() - start) / 1000} s")
        assertTrue("installiert", ok && WebBaustein.installiert)
        val feed = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI("https://feeds.content.dowjones.io/public/rss/mw_topstories")).build(),
            HttpResponse.BodyHandlers.ofString(),
        ).body()
        val links = Regex("<link>(https://www\\.marketwatch\\.com/story/[^<]+)</link>").findAll(feed).map { it.groupValues[1] }.take(3).toList()
        assertTrue("Links im Feed", links.isNotEmpty())
        var gelesen = 0
        var captcha = 0
        for (l in links) {
            val t0 = System.currentTimeMillis()
            val absaetze = runBlocking { WebBaustein.lesen(l) }.orEmpty()
            println("${absaetze.size} Absaetze in ${(System.currentTimeMillis() - t0) / 1000} s: ${l.substringAfter("/story/").take(60)}")
            absaetze.take(2).forEach { println("  " + it.take(120)) }
            if (absaetze.size >= 3) gelesen++
            if (WebBaustein.bestaetigungNoetig) captcha++
        }
        WebBaustein.beenden()
        println("gelesen $gelesen, Bestaetigung verlangt $captcha von ${links.size}")
        // Text oder eine erkannte Bestaetigungsseite, die nur ein Mensch im Fenster loest.
        assertTrue("Text oder erkannte Bestaetigung", gelesen + captcha == links.size)
    }
}
