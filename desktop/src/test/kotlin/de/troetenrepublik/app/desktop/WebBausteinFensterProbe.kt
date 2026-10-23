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

// Bestaetigung im Fenster mit einem Menschen: das Fenster zeigt einen MarketWatch-Artikel, die Person loest dort
// das Captcha und schliesst das Fenster. Danach liest die App drei Artikel ohne Fenster. Laeuft nur allein, mit
// Testfilter auf WebBausteinFensterProbe und TROETE_WEBFENSTER=<Ordner mit Chromium aus WebBausteinProbe>.
class WebBausteinFensterProbe {

    @Test
    fun bestaetigenUndDanachLesen() {
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
        assertTrue("Links im Feed", links.size >= 2)
        println("Fenster offen: ${links.first()}")
        runBlocking { WebBaustein.bestaetigen(links.first(), "MarketWatch: bitte bestätigen, dann dieses Fenster schließen") }
        println("Fenster geschlossen")
        var gelesen = 0
        for (l in links.drop(1)) {
            val absaetze = runBlocking { WebBaustein.lesen(l) }.orEmpty()
            println("${absaetze.size} Absaetze, Bestaetigung verlangt: ${WebBaustein.bestaetigungNoetig}: ${l.substringAfter("/story/").take(60)}")
            absaetze.take(2).forEach { println("  " + it.take(120)) }
            if (absaetze.size >= 3) gelesen++
        }
        WebBaustein.beenden()
        println("gelesen $gelesen von ${links.size - 1}")
        assertTrue("nach der Bestaetigung mindestens ein Artikel gelesen", gelesen >= 1)
    }
}
