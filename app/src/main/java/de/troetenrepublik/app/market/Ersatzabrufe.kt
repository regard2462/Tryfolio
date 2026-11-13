package de.troetenrepublik.app.market

import de.troetenrepublik.app.engine.Ersatzquellen
import de.troetenrepublik.app.engine.Fruehindikatoren
import de.troetenrepublik.app.engine.KongressBestand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

// Abrufe der Ersatzquellen (seit 5.92, Rechenteil in engine/Ersatzquellen.kt): CAPE von shillerdata.com, Margin-
// Kredite und Monatsreihen von FRED, Lieferketten-Index der New York Fed, Erwaehnungen auf Hacker News und Mastodon
// und neuere Senatsmeldungen ueber einen offenen Spiegel. Alle Abrufe laufen mit einer gewoehnlichen Chrome-Kennung,
// ohne etwas ueber die Person. Was nicht antwortet, behaelt den letzten Stand.
class Ersatzabrufe(private val api: MarketApi) {

    companion object {
        // Kennung aller Abrufe der App (seit 0.69 die Chrome-Kennung wie bei Yahoo), nichts ueber die Person.
        val KENNUNG: String get() = MarketApi.BROWSER_UA
    }

    private val kopf = mapOf("User-Agent" to KENNUNG)

    private class Speicher<T>(val dauer: Long) {
        val sperre = Mutex()
        @Volatile var stand: Pair<Long, T>? = null
    }

    // Monatliche Daten: ein bis drei Tage reichen.
    private val cape = Speicher<Ersatzquellen.Cape?>(3 * 86_400_000L)
    // FRED-Reihen (Margin, Fracht, Bahn, Halbleiter) bleiben nicht im Speicher: Die Bedingungen von FRED verbieten es.
    // Nur der GSCPI der New York Fed wird zwoelf Stunden gehalten.
    private val gscpi = Speicher<Ersatzquellen.Signal?>(12 * 3_600_000L)
    private val netz = ConcurrentHashMap<String, Pair<Long, Netz?>>()
    private val netzSperre = Mutex()

    // Gespeicherten Stand nehmen, solange er frisch ist; sonst laden. Ein leeres Ergebnis behaelt den letzten Stand.
    private suspend fun <T> holen(s: Speicher<T>, neu: Boolean, leer: (T) -> Boolean, laden: suspend () -> T): T? = s.sperre.withLock {
        val now = System.currentTimeMillis()
        s.stand?.takeIf { !neu && now - it.first < s.dauer }?.let { return@withLock it.second }
        val wert = runCatching { laden() }.getOrNull()
        if (wert != null && !leer(wert)) { s.stand = now to wert; wert } else s.stand?.second ?: wert
    }

    private fun heute() = LocalDate.now(ZoneOffset.UTC)

    private suspend fun fred(reihe: String, von: LocalDate): List<Pair<Long, Double>> =
        FredAbruf.geteilt(Ersatzquellen.fredAdresse(reihe, von)) {
            api.anfrage(Ersatzquellen.fredAdresse(reihe, von), kopf + ("Accept" to "text/csv")).takeIf { it.ok }?.text
                ?.let(Fruehindikatoren::fredLesen).orEmpty()
        }

    // Shiller-KGV: erst die Seite, dann die verlinkte Datei (gut 1,5 MB).
    suspend fun cape(neu: Boolean = false): Ersatzquellen.Cape? = holen(cape, neu, { it == null }) {
        val seite = api.anfrage(Ersatzquellen.SHILLER_SEITE, kopf + ("Accept" to "text/html"), max = 2_000_000).takeIf { it.ok }?.text ?: return@holen null
        val link = Ersatzquellen.shillerLink(seite) ?: Quellen.unerwartet("uswirtschaft", "shiller link")
        val bytes = api.datei(link, 8_000_000, kopf) ?: return@holen null
        withContext(Dispatchers.Default) { Ersatzquellen.capeLesen(bytes) } ?: Quellen.unerwartet("uswirtschaft", "shiller xls")
    }

    // Margin-Kredite seit 1997 (Quartale, gut 100 Zeilen): das juengste Wachstum und der Verlauf mit den Hochs von 2000 und 2021.
    suspend fun margin(neu: Boolean = false): Ersatzquellen.Margin? =
        runCatching { Ersatzquellen.margin(fred(Ersatzquellen.MARGIN_REIHE, LocalDate.of(1997, 1, 1))) }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()

    // Lieferketten, Fracht, Bahn und Halbleiter, alle gleichzeitig.
    suspend fun signale(neu: Boolean = false): Map<String, Ersatzquellen.Signal> =
        coroutineScope {
            val fredTeile = Ersatzquellen.FRED_SIGNALE.map { (k, reihe) ->
                async { runCatching { Ersatzquellen.jahresvergleich(k, fred(reihe, heute().minusYears(3))) }.getOrNull() }
            }
            val gscpiTeil = async {
                holen(gscpi, neu, { it == null }) {
                    runCatching {
                        val bytes = api.datei(Ersatzquellen.GSCPI_DATEI, 4_000_000, kopf) ?: return@runCatching null
                        val reihe = withContext(Dispatchers.Default) { Ersatzquellen.gscpiLesen(bytes, heute()) }
                        if (reihe.isEmpty()) Quellen.unerwartet("uswirtschaft", "gscpi")
                        Ersatzquellen.lieferketten(reihe)
                    }.getOrNull()
                }
            }
            (fredTeile.awaitAll() + gscpiTeil.await()).filterNotNull().associateBy { it.schluessel }
        }

    // Erwaehnungen eines Unternehmens: Hacker News (sieben Tage und die vier Wochen davor) und der Hashtag auf
    // mastodon.social (sieben Tage). Sechs Stunden je Name gespeichert.
    data class Netz(val begriff: String, val hn: Ersatzquellen.HackerNews?, val hashtag: String?, val mastodon: List<Ersatzquellen.TagNutzung>)

    suspend fun netz(name: String): Netz? {
        val begriff = Ersatzquellen.suchname(name).takeIf { it.length >= 3 } ?: return null
        val now = System.currentTimeMillis()
        netz[begriff]?.takeIf { now - it.first < 6 * 3_600_000L }?.let { return it.second }
        return netzSperre.withLock {
            netz[begriff]?.takeIf { now - it.first < 6 * 3_600_000L }?.let { return@withLock it.second }
            val sek = now / 1000
            val tag = Ersatzquellen.hashtag(name)
            val aus = coroutineScope {
                val woche = async { api.anfrage(Ersatzquellen.hnAdresse(begriff, sek - 7 * 86_400L), kopf).takeIf { it.ok }?.text?.let(Ersatzquellen::hnTreffer) }
                val fuenf = async { api.anfrage(Ersatzquellen.hnAdresse(begriff, sek - 35 * 86_400L), kopf).takeIf { it.ok }?.text?.let(Ersatzquellen::hnTreffer) }
                val masto = async { tag?.let { t -> api.anfrage(Ersatzquellen.mastodonAdresse(t), kopf).takeIf { it.ok }?.text?.let(Ersatzquellen::mastodonVerlauf) }.orEmpty() }
                val w = woche.await()
                val f = fuenf.await()
                Netz(begriff, if (w != null && f != null) Ersatzquellen.hackerNews(w, f) else null, tag, masto.await())
            }
            val leer = aus.hn == null && aus.mastodon.isEmpty()
            if (!leer) netz[begriff] = now to aus
            aus.takeUnless { leer }
        }
    }

    // Neue Senatsmeldungen aus dem offenen Spiegel, die in bekannt fehlen; null ohne Antwort.
    suspend fun senat(bekannt: List<Politik.SenatMeldung>): List<Politik.SenatMeldung>? {
        val a = api.anfrage(SenatSpiegel.ADRESSE, kopf, max = SenatSpiegel.MAX_BYTES)
        val text = a.takeIf { it.ok }?.text ?: return null
        return withContext(Dispatchers.Default) { SenatSpiegel.neue(text, bekannt) } ?: run { Quellen.drift("github"); null }
    }

    // Aktuelle Kongressmitglieder und Ausschuesse von unitedstates/congress-legislators (5.96, CC0). null ohne Antwort
    // oder bei unlesbarer Liste; die Ausschuesse sind null, wenn nur sie fehlen.
    suspend fun kongressMitglieder(heute: String): Pair<List<KongressBestand.Mitglied>, Map<String, List<List<String>>>?>? {
        val a = api.anfrage(KongressBestand.AKTUELL, kopf, max = KongressBestand.MAX_BYTES)
        val text = a.takeIf { it.ok }?.text ?: return null
        val liste = withContext(Dispatchers.Default) { KongressBestand.ausLegislators(text, heute) } ?: run { Quellen.drift("kongress"); return null }
        val ausschuesse = coroutineScope {
            val m = async { api.anfrage(KongressBestand.MITGLIEDSCHAFTEN, kopf, max = 4_000_000).takeIf { it.ok }?.text }
            val c = async { api.anfrage(KongressBestand.AUSSCHUESSE, kopf, max = 2_000_000).takeIf { it.ok }?.text }
            val mt = m.await()
            val ct = c.await()
            if (mt != null && ct != null) withContext(Dispatchers.Default) { KongressBestand.ausschuesse(mt, ct) } else null
        }
        return liste to ausschuesse
    }
}
