package de.troetenrepublik.app.market

import de.troetenrepublik.app.engine.Fruehwarnung
import de.troetenrepublik.app.engine.Quellenbuendel
import de.troetenrepublik.app.engine.Stimmungsbarometer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

// Gesundheitscheck der wichtigsten Leser gegen die echten Quellen, nur lokal und nur mit dem Schalter:
//   gradlew :app:testDebugUnitTest --tests '*Fassung592QuellenLive*' -Dtryfolio.live=true --rerun-tasks --info
// Je Quelle eine Zeile HEALTH|quelle|OK|BLOCKED|DRIFT|Hinweis. DRIFT (Antwort unlesbar) laesst den Test scheitern,
// BLOCKED (403/429, kein Netz) nicht: das kann am Rechner oder Netz liegen. Gesendet wird nur die offene Kennung der
// App, keine persoenlichen Daten.
class Fassung592QuellenLiveTest {

    private val api = MarketApi()

    // Antwort ohne Erfolg mit HTTP-Status (0 ohne Verbindung)
    private class HttpFehler(val code: Int) : java.io.IOException("http $code")

    private fun probe(id: String, name: String, block: suspend () -> Boolean): Quellenbuendel.Befund {
        val vorher = Quellen.gesundheit.value[id]?.fehler ?: 0
        var art: Quellenbuendel.Fehler? = null
        var hinweisCode = 0
        val ok = runBlocking {
            try {
                block()
            } catch (e: UnerwarteteAntwort) {
                art = Quellenbuendel.Fehler.FORM
                false
            } catch (e: HttpFehler) {
                // Auch 404 zaehlt hier als gesperrt: die Adresse ist nicht oeffentlich (etwa ein privates Repo)
                art = when {
                    e.code == 0 -> Quellenbuendel.Fehler.NETZ
                    e.code >= 500 -> Quellenbuendel.Fehler.SERVER
                    else -> Quellenbuendel.Fehler.GESPERRT
                }
                hinweisCode = e.code
                false
            } catch (e: Exception) {
                false
            }
        }
        val g = Quellen.gesundheit.value[id]
        // Fehlerart aus dem Abfang, wenn er bei diesem Lauf einen Fehler gezaehlt hat
        if (art == null && g != null && g.fehler > vorher) art = g.art
        val befund = Quellenbuendel.befund(ok, art)
        val hinweis = if (ok) "" else listOfNotNull(art?.name, (hinweisCode.takeIf { it > 0 } ?: g?.code?.takeIf { it > 0 })?.toString()).joinToString(" ")
        println("HEALTH|$name|$befund|$hinweis")
        return befund
    }

    private suspend fun text(url: String): String {
        val a = api.anfrage(url, mapOf("Accept" to "application/json, text/csv, */*"))
        if (!a.ok) throw HttpFehler(a.code)
        return a.text.orEmpty()
    }

    @Test fun gesundheitscheck() {
        assumeTrue(System.getProperty("tryfolio.live") == "true")
        Quellen.fuerTest(null)
        val befunde = listOf(
            probe("yahoo", "yahoo-chart") { api.chartQuote("AAPL") != null },
            probe("yahoo", "yahoo-quote") { api.quotesBatchOnly(listOf("AAPL", "SAP.DE")).isNotEmpty() },
            probe("yahoo", "yahoo-news") { api.news("AAPL", "en").isNotEmpty() },
            probe("ezb", "ezb-einlagesatz") { api.ecbDepositRate() != null },
            probe("wikipedia", "wikipedia") { api.wikiSummary("de", "Apple") != null },
            probe("erwaehnungen", "apewisdom") { api.apeWisdom().isNotEmpty() },
            probe("stimmung", "krypto-angst-gier") {
                Stimmungsbarometer.kryptoLesen(text(Stimmungsbarometer.KRYPTO)) ?: Quellen.unerwartet("stimmung", "fng")
                true
            },
            probe("uswirtschaft", "treasury") {
                val jahr = java.time.LocalDate.now().year
                Fruehwarnung.treasuryLesen(text(Fruehwarnung.treasuryAdresse(jahr))).ifEmpty { Quellen.unerwartet("uswirtschaft", "csv") }
                true
            },
            probe("polymarket", "polymarket") {
                val a = Json.parseToJsonElement(text(Prognose.POLY + "/events?limit=1&closed=false")) as? JsonArray
                if (a == null || a.isEmpty()) Quellen.unerwartet("polymarket", "events")
                true
            },
            probe("github", "quellen-buendel") {
                Quellenbuendel.lesen(text(Quellenbuendel.ADRESSEN.first())) ?: Quellen.unerwartet("github", "quellen.json")
                true
            },
        )
        assertTrue("DRIFT bei mindestens einer Quelle", befunde.none { it == Quellenbuendel.Befund.DRIFT })
    }
}
