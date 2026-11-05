package de.troetenrepublik.app.market

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

// Laedt die Nachrichtenfeeds und haelt die zusammengefuehrten Schlagzeilen. Jeder Feed wird hoechstens
// alle zwei Minuten abgefragt, hoechstens vier gleichzeitig; schlaegt ein Feed fehl, bleiben seine letzten
// Schlagzeilen stehen.
class NewsRepository(private val api: MarketApi, val kontingent: Abrufkontingent = Abrufkontingent(null)) {

    // Stand der Nachrichten: Schlagzeilen, Zeitpunkt des letzten Ladens, ob gerade geladen wird, Quellen ohne Antwort.
    data class Stand(
        val eintraege: List<Rss.Eintrag> = emptyList(),
        val geladenAm: Long = 0L,
        val laedt: Boolean = false,
        val fehler: List<String> = emptyList(),
        // Meldungen der Anbieter mit eigenem Schluessel je Gruppe (falscher Schluessel, Limit, Premium).
        val meldungen: Map<String, String> = emptyMap(),
    )

    private val _stand = MutableStateFlow(Stand())
    val stand: StateFlow<Stand> = _stand.asStateFlow()

    private val cache = ConcurrentHashMap<String, Pair<Long, List<Rss.Eintrag>>>()
    private val lock = Mutex()
    private val gleichzeitig = Semaphore(4)
    private val meldungen = ConcurrentHashMap<String, String>()
    private val gruppenSperren = ConcurrentHashMap<String, Mutex>()

    data class Auftrag(val quellen: List<Rss.Quelle>, val deutsch: Boolean, val ohneAbruf: Set<String> = emptySet()) {
        fun abrufen(q: Rss.Quelle): Boolean = q.schluessel !in ohneAbruf
    }

    // Alle Feeds des Auftrags laden; neu ignoriert den Zwischenspeicher (Ziehen zum Aktualisieren).
    suspend fun laden(auftrag: Auftrag, neu: Boolean = false) {
        ladenGesperrt(auftrag, neu)
        anreichern(auftrag)
    }

    private suspend fun ladenGesperrt(auftrag: Auftrag, neu: Boolean) = lock.withLock {
        _stand.value = _stand.value.copy(laedt = true)
        val now = System.currentTimeMillis()
        val fehler = java.util.Collections.synchronizedList(mutableListOf<String>())
        try {
            val listen = coroutineScope {
                auftrag.quellen.map { q ->
                    async {
                        // Quellen ohne Abruf: nur der Zwischenspeicher
                        val liste = if (!auftrag.abrufen(q)) mitAnriss(cache[q.adresse]?.second.orEmpty())
                        else if (q.art.gruppe.isNotEmpty()) holenApi(q, neu, now)
                        else holen(q.adresse, neu, now, q.abstandMs, q.browserKennung) { xml -> lesen(q, xml, auftrag.deutsch) }?.let { if (q.anrissVonSeite) mitAnriss(it) else it }
                            ?: run { fehler += q.name; mitAnriss(cache[q.adresse]?.second.orEmpty()) }
                        // Begrenzung je Quelle, bevor alle Listen zusammengefuehrt werden
                        Rss.begrenzt(q, liste)
                    }
                }.awaitAll()
            }
            val alle = withContext(Dispatchers.Default) { Rss.zusammen(listen, now) }
            _stand.value = Stand(alle, now, false, fehler.distinct(), meldungen.toMap())
        } catch (e: CancellationException) {
            _stand.value = _stand.value.copy(laedt = false)
            throw e
        }
    }

    // Quelle mit eigenem Schluessel holen: hoechstens so oft, wie ihr Abstand und das Kontingent der Gruppe
    // es erlauben; Abrufe einer Gruppe nacheinander mit Mindestabstand. Inhalte des Guardian verfallen nach 23 Stunden.
    // Meldet der Anbieter einen Fehler, bleibt die Meldung fuer die Anzeige stehen.
    private suspend fun holenApi(q: Rss.Quelle, neu: Boolean, now: Long): List<Rss.Eintrag> {
        val gruppe = q.art.gruppe
        cache[q.adresse]?.let { (t, liste) ->
            if (q.art == Rss.Art.GUARDIAN && now - t > NachrichtenApis.GUARDIAN_HALTBAR_MS) cache.remove(q.adresse)
            else if (now - t < (if (neu) minOf(q.abstandMs, maxOf(60_000L, q.abstandMs / 4)) else q.abstandMs)) return liste
        }
        val alt = cache[q.adresse]?.second.orEmpty()
        return gruppenSperren.getOrPut(gruppe) { Mutex() }.withLock {
            val w = kontingent.warten(gruppe)
            if (w in 1..20_000) kotlinx.coroutines.delay(w)
            if (!kontingent.erlaubt(gruppe)) {
                meldungen[gruppe] = LIMIT
                return@withLock alt
            }
            kontingent.buchen(gruppe)
            val a = gleichzeitig.withPermit { api.anfrage(q.adresse) }
            if (a.code == 0 || a.text == null) return@withLock alt
            if (a.code == 401 || a.code == 403) { meldungen[gruppe] = SCHLUESSEL; return@withLock alt }
            if (a.code == 429) { meldungen[gruppe] = LIMIT; return@withLock alt }
            val e = withContext(Dispatchers.Default) {
                when (q.art) {
                    Rss.Art.GUARDIAN -> NachrichtenApis.guardianLesen(a.text, q.name, q.kategorie, q.schluessel)
                    Rss.Art.FINNHUB -> NachrichtenApis.finnhubLesen(a.text, q.name, q.kategorie, q.schluessel)
                    Rss.Art.ALPHAVANTAGE -> NachrichtenApis.alphaVantageLesen(a.text, q.name, q.kategorie, q.schluessel)
                    Rss.Art.RSS, Rss.Art.TRUTH, Rss.Art.SEITE -> NachrichtenApis.Ergebnis(emptyList())
                }
            }
            if (e.meldung != null) {
                meldungen[gruppe] = e.meldung
                // Alpha Vantage meldet ein erreichtes Tageslimit im Text; dann bis morgen nicht mehr fragen
                if (q.art == Rss.Art.ALPHAVANTAGE && Regex("(?i)rate limit|requests per day").containsMatchIn(e.meldung)) kontingent.erschoepft(gruppe)
                return@withLock alt
            }
            meldungen.remove(gruppe)
            cache[q.adresse] = now to e.eintraege
            e.eintraege
        }
    }

    // Anrisse von der Seite (EZB, SNB, Fed): je Link einmal gelesen, leer, wenn die Seite keinen Text hat. Fehlversuche
    // (keine Verbindung, 429, 5xx) frühestens nach zehn Minuten erneut.
    private val anrisse = ConcurrentHashMap<String, Anriss.Ergebnis>()
    private val anrissFehler = ConcurrentHashMap<String, Long>()
    private val anrissLaeuft: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private fun mitAnriss(liste: List<Rss.Eintrag>): List<Rss.Eintrag> = liste.map { e ->
        if (e.zusammenfassung.isNotBlank()) e
        else anrisse[e.link]?.takeIf { it.text.isNotBlank() }?.let { e.copy(zusammenfassung = it.text, zusammenfassungGekuerzt = it.gekuerzt) } ?: e
    }

    // Nach dem Laden: fehlende Anrisse der neuesten Eintraege (sichtbare 7 Tage, auch ohne Datum) von der Seite lesen, hoechstens acht je
    // Durchgang, und die Liste danach ergaenzen. Laeuft ausserhalb der Sperre, die Schlagzeilen stehen schon da.
    private suspend fun anreichern(auftrag: Auftrag) {
        val now = System.currentTimeMillis()
        val offen = auftrag.quellen.filter { it.anrissVonSeite }.flatMap { cache[it.adresse]?.second.orEmpty() }
            .filter { e ->
                e.zusammenfassung.isBlank() && e.link.startsWith("https://") && (e.zeit == 0L || e.zeit in (now - 7 * 86_400_000L)..(now + 3_600_000L)) &&
                    !anrisse.containsKey(e.link) && (anrissFehler[e.link]?.let { now - it > 10 * 60_000L } ?: true)
            }
            .sortedByDescending { it.zeit }.take(8)
            .filter { anrissLaeuft.add(it.link) }
        if (offen.isEmpty()) return
        var neu = false
        try {
            coroutineScope {
                offen.map { e ->
                    async {
                        val a = gleichzeitig.withPermit { api.anfrage(e.link, kopf = mapOf("Accept" to "text/html,application/xhtml+xml")) }
                        if (a.code == 0 || a.code == 429 || a.code >= 500) anrissFehler[e.link] = now
                        else {
                            val t = if (a.ok) withContext(Dispatchers.Default) { runCatching { Anriss.lesen(a.text.orEmpty(), e.titel) }.getOrDefault(Anriss.Ergebnis("")) } else Anriss.Ergebnis("")
                            anrisse[e.link] = t
                            anrissFehler.remove(e.link)
                            if (t.text.isNotBlank()) neu = true
                        }
                    }
                }.awaitAll()
            }
        } finally {
            offen.forEach { anrissLaeuft.remove(it.link) }
        }
        if (neu) lock.withLock { _stand.value = _stand.value.copy(eintraege = mitAnriss(_stand.value.eintraege)) }
    }

    // Text eines Guardian-Artikels ueber die Content API; null ohne Schluessel, ohne Kontingent oder bei Fehler.
    // Der Text bleibt nur im Speicher des Blatts.
    suspend fun guardianArtikel(link: String, schluessel: String): NachrichtenApis.Artikel? {
        if (schluessel.isBlank()) return null
        val url = NachrichtenApis.guardianArtikel(link, schluessel) ?: return null
        val gruppe = Abrufkontingent.GUARDIAN
        return gruppenSperren.getOrPut(gruppe) { Mutex() }.withLock {
            val w = kontingent.warten(gruppe)
            if (w in 1..20_000) kotlinx.coroutines.delay(w)
            if (!kontingent.erlaubt(gruppe)) return@withLock null
            kontingent.buchen(gruppe)
            val a = api.anfrage(url)
            if (!a.ok) null else withContext(Dispatchers.Default) { NachrichtenApis.guardianArtikelLesen(a.text!!) }
        }
    }

    // Pruefen, ob unter url ein Feed liegt; Zahl der Schlagzeilen, 0 ohne Feed.
    suspend fun pruefen(url: String): Int {
        val bytes = runCatching { api.feedBytes(url) }.getOrNull() ?: return 0
        return Rss.lesen(Rss.text(bytes), "", Rss.Kategorie.EIGENE, basis = url).size
    }

    // Ergebnis der Pruefung eines eigenen Zugangs (5.96): erreichbar = Antwort mit Inhalt, anzahl = gelesene Schlagzeilen, titel des Feeds.
    data class Pruefung(val erreichbar: Boolean, val anzahl: Int, val titel: String)

    // Zugang pruefen: Adresse mit der Kennung der App abrufen, hoechstens 30 Sekunden, und als Feed lesen.
    suspend fun pruefenFeed(url: String): Pruefung {
        val bytes = kotlinx.coroutines.withTimeoutOrNull(30_000L) { runCatching { api.feedBytes(url) }.getOrNull() } ?: return Pruefung(false, 0, "")
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val xml = Rss.text(bytes)
            val anzahl = runCatching { Rss.lesen(xml, "", Rss.Kategorie.EIGENE, basis = url).size }.getOrDefault(0)
            val titel = de.troetenrepublik.app.engine.EigenerFeed.feedTitel(xml)
            Pruefung(true, anzahl, titel)
        }
    }

    private fun lesen(q: Rss.Quelle, xml: String, deutsch: Boolean): List<Rss.Eintrag> {
        if (q.art == Rss.Art.TRUTH) return TruthNews.eintraege(xml, q)
        if (q.art == Rss.Art.SEITE) return Briefquellen.lesen(xml, q, deutsch)
        val liste = Rss.lesen(xml, q.name, q.kategorie, herausgeberAusLink = q.sammelt, schluessel = q.schluessel, basis = q.adresse, google = q.google, volltext = q.volltext)
        if (!q.nurSprache) return liste
        val teil = if (deutsch) "/de/" else "/en/press/press-releases/"
        return liste.filter { teil in it.link }
    }

    // Einen Feed holen und lesen, mit Zwischenspeicher; null, wenn er nicht antwortet. browser: Abruf mit
    // MarketApi.BROWSER_UA.
    private suspend fun holen(url: String, neu: Boolean, now: Long, abstand: Long, browser: Boolean = false, lesen: (String) -> List<Rss.Eintrag>): List<Rss.Eintrag>? {
        // Auch „neu“ fragt einen Feed nicht oefter ab als jede halbe Minute bzw. als seine Quelle es erlaubt
        cache[url]?.let { (t, liste) -> if (now - t < (if (neu) minOf(abstand, maxOf(30_000L, abstand - TTL_MS)) else abstand)) return liste }
        val bytes = gleichzeitig.withPermit { runCatching { api.feedBytes(url, kopf = if (browser) mapOf("User-Agent" to MarketApi.BROWSER_UA) else emptyMap()) }.getOrNull() } ?: return null
        val liste = withContext(Dispatchers.Default) { runCatching { lesen(Rss.text(bytes)) }.getOrDefault(emptyList()) }
        // Eine leere Antwort (etwa eine Fehlerseite) ersetzt keine vorhandenen Schlagzeilen
        if (liste.isEmpty() && cache[url] != null) return null
        cache[url] = now to liste
        return liste
    }

    companion object {
        // Meldungen fuer die Anzeige (die Oberflaeche uebersetzt sie).
        const val LIMIT = "#limit"
        const val SCHLUESSEL = "#schluessel"

        // Zwischenspeicher je Feed: zwei Minuten (Feeds aendern sich selten schneller; schont die Quellen).
        const val TTL_MS = 2 * 60_000L
    }
}
