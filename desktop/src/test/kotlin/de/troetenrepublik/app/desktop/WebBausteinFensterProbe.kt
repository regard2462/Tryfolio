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

// Artikel im Fenster mit einem Menschen: das Fenster zeigt einen MarketWatch-Artikel, die Person bedient die Seite selbst.
// Ist sie geladen, liest die App den Text aus dem Fenster. Laeuft nur allein, mit Testfilter auf WebBausteinFensterProbe
// und TROETE_WEBFENSTER=<Ordner, in dem Chromium liegt>.
class WebBausteinFensterProbe {

    @Test
    fun artikelImFenster() {
        val ordner = System.getenv("TROETE_WEBFENSTER")
        assumeTrue("nur mit TROETE_WEBFENSTER", !ordner.isNullOrBlank())
        System.setProperty("java.awt.headless", "false")
        Plattform.init(File(ordner!!))
        assertTrue("Chromium vorhanden", WebBaustein.installiert)
        val feed = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI("https://feeds.content.dowjones.io/public/rss/mw_topstories")).build(),
            HttpResponse.BodyHandlers.ofString(),
        ).body()
        val links = Regex("<link>(https://www\\.marketwatch\\.com/story/[^<]+)</link>").findAll(feed).map { it.groupValues[1] }.take(4).toList()
        assertTrue("Links im Feed", links.isNotEmpty())
        println("Fenster offen: ${links.first()}")
        runBlocking { WebBaustein.artikelOeffnen(links.first(), "MarketWatch") }
        // Bis 120 Sekunden alle 500 ms warten, bis die Seite im Fenster geladen ist
        val ende = System.currentTimeMillis() + 120_000
        while (!WebBaustein.artikelGeladen && System.currentTimeMillis() < ende) Thread.sleep(500)
        val absaetze = runBlocking { WebBaustein.artikelText() }.orEmpty()
        println("${absaetze.size} Absaetze: ${links.first().substringAfter("/story/").take(60)}")
        absaetze.take(2).forEach { println("  " + it.take(120)) }
        WebBaustein.beenden()
        assertTrue("Text aus dem Fenster", absaetze.isNotEmpty())
    }
}
