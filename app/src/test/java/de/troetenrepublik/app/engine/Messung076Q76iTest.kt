package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Range
import de.troetenrepublik.app.data.Series
import de.troetenrepublik.app.market.MarketApi
import de.troetenrepublik.app.market.MarketRepository
import de.troetenrepublik.app.market.NewsRepository
import de.troetenrepublik.app.market.Rss
import de.troetenrepublik.app.market.SerieCodec
import de.troetenrepublik.app.market.SichereStandDatei
import de.troetenrepublik.app.market.StandSpeicher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

// Zeitmessung des gespeicherten Stands bei kaltem Start (neue Instanzen auf Dateien, die ein frueherer Lauf geschrieben
// hat). Die Netzzeiten stehen im Bericht (curl, 11.11.2026), weil der Test-Prozess keine Verbindung bekommt. Laeuft
// nur mit der Umgebungsvariable MESSUNG_Q76I=<Ausgabedatei>.
class Messung076Q76iTest {
    private fun <T> zeit(f: () -> T): Pair<T, Double> { val t0 = System.nanoTime(); val r = f(); return r to (System.nanoTime() - t0) / 1_000_000.0 }

    private fun serie(n: Int) = Series(List(n) { Candle(1_700_000_000_000L + it * 120_000L, 100.0 + it * 0.013, 101.2 + it * 0.013, 99.1 + it * 0.013, 100.37 + it * 0.013, 1_234_567.0 + it) }, 1_700_000_000_000L, 1_700_000_000_000L + n * 120_000L)

    @Test fun messen() {
        val ziel = System.getenv("MESSUNG_Q76I")
        assumeTrue(!ziel.isNullOrBlank())
        val dir = File(System.getProperty("java.io.tmpdir"), "q76i-messung-" + System.nanoTime()).also { it.mkdirs() }
        val out = StringBuilder()
        fun z(s: String) { out.appendLine(s) }
        val punkte = mapOf(Range.D1 to 195, Range.W1 to 170, Range.M1 to 300, Range.M3 to 63, Range.M6 to 126, Range.Y1 to 252, Range.MAX to 520)
        // Vorbereitung wie ein frueherer Lauf der App: 40 Werte, alle sieben Zeitraeume
        val kerzen = StandSpeicher(List(8) { SichereStandDatei(File(dir, "kerzen$it.txt")) })
        val jetzt = System.currentTimeMillis()
        for (s in 1..40) for ((r, n) in punkte) kerzen.schreiben("SYM$s|${r.name}", SerieCodec.kodieren(serie(n)), jetzt - 3_600_000L)
        kerzen.sichern()
        val j = Json { ignoreUnknownKeys = true; allowSpecialFloatingPointValues = true }
        val news = List(400) { Rss.Eintrag("Titel $it der Schlagzeile mit etwas Laenge", "https://example.com/artikel/$it", jetzt - it * 60_000L, "Zusammenfassung ".repeat(12), "example.com", "Feed", setOf(Rss.Kategorie.MAERKTE), setOf("a")) }
        val nd = StandSpeicher(listOf(SichereStandDatei(File(dir, "news.txt"))))
        nd.schreiben("alle", j.encodeToString(news), jetzt - 3_600_000L); nd.sichern()
        runBlocking {
            val (repo, tStart) = zeit { MarketRepository(MarketApi(), CoroutineScope(Job()), null, dir) }
            z("Repository-Start mit Stand\t${"%.1f".format(tStart)} ms")
            for ((r, n) in punkte) {
                val (g, t) = zeit { runBlocking { repo.serieGespeichert("SYM7", r) } }
                z("Chart ${r.name} aus Stand, kalt (${g?.wert?.candles?.size} von $n Punkten)\t${"%.1f".format(t)} ms")
            }
            val (_, tZweit) = zeit { runBlocking { repo.serieGespeichert("SYM8", Range.MAX) } }
            z("Chart MAX aus Stand, anderer Wert (gleiche Datei meist schon gelesen)\t${"%.1f".format(tZweit)} ms")
            val nr = NewsRepository(MarketApi(), standDatei = SichereStandDatei(File(dir, "news.txt")))
            val (_, tNews) = zeit { runBlocking { nr.ausStand() } }
            z("News-Liste aus Stand, kalt (${nr.stand.value.eintraege.size} Meldungen)\t${"%.1f".format(tNews)} ms")
            z("Dateien\t${dir.listFiles()!!.sumOf { it.length() } / 1024} KiB auf der Platte in ${dir.listFiles()!!.size} Dateien (40 Werte x 7 Zeitraeume plus 400 Meldungen)")
        }
        File(ziel).writeText(out.toString())
    }
}
