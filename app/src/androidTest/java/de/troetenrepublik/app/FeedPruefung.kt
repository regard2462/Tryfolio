package de.troetenrepublik.app

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.troetenrepublik.app.engine.Durchbrueche
import de.troetenrepublik.app.market.Briefquellen
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.Rss
import de.troetenrepublik.app.market.TruthNews
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

// Prueflauf fuer alle fest eingebauten Nachrichtenquellen: jede Adresse einmal ueber den Abrufweg der App (MarketApi,
// Kennung wie im Betrieb), dann mit demselben Leser wie die Nachrichtenseite gelesen. Ergebnis je Quelle als Zeile mit
// HTTP-Status, Zeit, Zahl der Eintraege und Dateianfang in feedpruefung.tsv im Datenordner der App.
@RunWith(AndroidJUnit4::class)
class FeedPruefung {

    private data class Ziel(val schluessel: String, val name: String, val adresse: String, val lesen: (String) -> Int, val browser: Boolean)

    @Test
    fun alleFeeds(): Unit = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val api = MarketApi()
        val ziele = LinkedHashMap<String, Ziel>()
        for (deutsch in listOf(true, false)) {
            for (q in Rss.eingebaut(deutsch)) {
                if (q.art != Rss.Art.RSS && q.art != Rss.Art.TRUTH && q.art != Rss.Art.SEITE) continue
                val lesen: (String) -> Int = { xml ->
                    when (q.art) {
                        Rss.Art.TRUTH -> TruthNews.eintraege(xml, q).size
                        Rss.Art.SEITE -> Briefquellen.lesen(xml, q, deutsch).size
                        else -> Rss.lesen(xml, q.name, q.kategorie, herausgeberAusLink = q.sammelt, schluessel = q.schluessel, basis = q.adresse, google = q.google, volltext = q.volltext).size
                    }
                }
                ziele.putIfAbsent(q.adresse, Ziel(q.schluessel + (if (deutsch) "" else "/en"), q.name, q.adresse, lesen, q.browserKennung))
            }
        }
        for (f in Durchbrueche.FEEDS) {
            ziele.putIfAbsent(f.adresse, Ziel("durchbruch", f.name, f.adresse, { xml -> Rss.lesen(xml, f.name, Rss.Kategorie.EIGENE, basis = f.adresse).size }, false))
        }
        val tor = Semaphore(4)
        val zeilen = ziele.values.map { z ->
            async {
                tor.withPermit {
                    val t0 = System.currentTimeMillis()
                    val kopf = if (z.browser) mapOf("User-Agent" to MarketApi.BROWSER_UA) else emptyMap()
                    val a = withTimeoutOrNull(40_000L) { runCatching { api.anfrage(z.adresse, kopf + ("Accept" to "application/rss+xml, application/atom+xml, application/xml, text/xml;q=0.9, */*;q=0.8")) }.getOrNull() }
                    val ms = System.currentTimeMillis() - t0
                    val text = a?.text.orEmpty()
                    val anzahl = if (a?.ok == true) runCatching { z.lesen(Rss.text(text.encodeToByteArray())) }.getOrDefault(-1) else 0
                    val anfang = text.take(160).replace(Regex("\\s+"), " ")
                    listOf(z.schluessel, z.name, a?.code?.toString() ?: "zeit", ms.toString(), anzahl.toString(), a?.kopf?.get("content-type").orEmpty(), z.adresse, anfang).joinToString("\t")
                }
            }
        }.awaitAll()
        val datei = File(ctx.filesDir, "feedpruefung.tsv")
        datei.writeText("schluessel\tname\tstatus\tms\teintraege\ttyp\tadresse\tanfang\n" + zeilen.joinToString("\n") + "\n")
        zeilen.forEach { Log.i("FEEDTEST", it) }
        Log.i("FEEDTEST", "FERTIG ${zeilen.size}")
        Unit
    }
}
