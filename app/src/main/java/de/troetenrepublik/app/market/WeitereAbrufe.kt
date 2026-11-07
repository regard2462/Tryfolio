package de.troetenrepublik.app.market

import de.troetenrepublik.app.engine.Ersatzquellen
import de.troetenrepublik.app.engine.VxKurve
import de.troetenrepublik.app.engine.WeitereQuellen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

// Abrufe der weiteren Quellen (seit 5.92, Rechenteil in engine/WeitereQuellen.kt): CBOE, Drewry, AAR, WSTS ohne
// Schluessel, CourtListener mit eigenem Token und Bluesky mit eigenem App-Passwort. Alle Abrufe melden sich offen mit
// Name und Fassung der App und senden nichts ueber die Person. Ergebnisse bleiben nur im Speicher.
class WeitereAbrufe(private val api: MarketApi, private val kontingent: () -> Abrufkontingent) {

    companion object {
        const val COURTLISTENER = Abrufkontingent.COURTLISTENER
        const val BLUESKY = Abrufkontingent.BLUESKY
        const val BLUESKY_ANMELDUNG = Abrufkontingent.BLUESKY_ANMELDUNG

        // Meldungen fuer die Anzeige unter Zugaenge
        const val ABGELEHNT = "abgelehnt"
        const val LIMIT = "limit"
    }

    private val kopf = mapOf("User-Agent" to Ersatzabrufe.KENNUNG)

    private class Speicher<T>(val dauer: Long) {
        val sperre = Mutex()
        @Volatile var stand: Pair<Long, T>? = null
    }

    private suspend fun <T> holen(s: Speicher<T>, neu: Boolean, laden: suspend () -> T?): T? = s.sperre.withLock {
        val now = System.currentTimeMillis()
        s.stand?.takeIf { !neu && now - it.first < s.dauer }?.let { return@withLock it.second }
        val wert = runCatching { laden() }.getOrNull()
        if (wert != null) { s.stand = now to wert; wert } else s.stand?.second
    }

    // ------------------------------------------------------------ Ohne Schluessel

    private val putCall = Speicher<WeitereQuellen.PutCall>(3 * 3_600_000L)
    private val vx = Speicher<VxKurve.Stand>(3 * 3_600_000L)
    private val wci = Speicher<WeitereQuellen.Wci>(12 * 3_600_000L)
    private val bahn = Speicher<WeitereQuellen.Bahnwoche>(12 * 3_600_000L)
    private val chips = Speicher<WeitereQuellen.Halbleiter>(24 * 3_600_000L)

    // Put/Call des letzten vollstaendigen Handelstags (eine Anfrage, keine Suche ueber Tage ohne Datei).
    suspend fun putCall(neu: Boolean = false): WeitereQuellen.PutCall? = holen(putCall, neu) {
        val tag = WeitereQuellen.cboeTag(LocalDateTime.now(ZoneId.of("America/New_York")))
        val a = api.anfrage(WeitereQuellen.cboeAdresse(tag), kopf, max = 200_000)
        a.takeIf { it.ok }?.text?.let { WeitereQuellen.putCallLesen(it, tag) ?: Quellen.unerwartet("stimmung", "cboe") }
    }

    // VX-Terminkurve der Cboe aus der Abrechnungsdatei des Tages; bleibt sie leer oder fehlt sie (Feiertag),
    // hoechstens ein weiterer Versuch am vorherigen US-Handelstag.
    suspend fun vx(neu: Boolean = false): VxKurve.Stand? = holen(vx, neu) {
        val heute = WeitereQuellen.cboeTag(LocalDateTime.now(ZoneId.of("America/New_York")))
        val a = api.anfrage(VxKurve.adresse(heute), kopf, max = 200_000)
        a.takeIf { it.ok }?.text?.let { VxKurve.lesen(it, heute) } ?: run {
            var tag = heute.minusDays(1)
            while (!WeitereQuellen.usHandelstag(tag)) tag = tag.minusDays(1)
            api.anfrage(VxKurve.adresse(tag), kopf, max = 200_000).takeIf { it.ok }?.text?.let { VxKurve.lesen(it, tag) }
        }
    }

    suspend fun wci(neu: Boolean = false): WeitereQuellen.Wci? = holen(wci, neu) {
        val a = api.anfrage(WeitereQuellen.DREWRY_SEITE, kopf + ("Accept" to "text/html"), max = 1_500_000)
        a.takeIf { it.ok }?.text?.let { WeitereQuellen.wciLesen(it) ?: Quellen.unerwartet("fruehsignale", "drewry") }
    }

    suspend fun bahn(neu: Boolean = false): WeitereQuellen.Bahnwoche? = holen(bahn, neu) {
        val a = api.anfrage(WeitereQuellen.AAR_FEED, kopf + ("Accept" to "application/rss+xml, application/xml"), max = 2_000_000)
        a.takeIf { it.ok }?.text?.let { WeitereQuellen.aarLesen(it) ?: Quellen.unerwartet("fruehsignale", "aar") }
    }

    // Halbleiterumsaetze: Seite der WSTS, dann die dort verlinkte Datei (gut 150 KB).
    suspend fun halbleiter(neu: Boolean = false): WeitereQuellen.Halbleiter? = holen(chips, neu) {
        val seite = api.anfrage(WeitereQuellen.WSTS_SEITE, kopf + ("Accept" to "text/html"), max = 1_500_000).takeIf { it.ok }?.text ?: return@holen null
        val link = WeitereQuellen.wstsLink(seite) ?: Quellen.unerwartet("fruehsignale", "wsts link")
        val bytes = api.datei(link, 3_000_000, kopf) ?: return@holen null
        withContext(Dispatchers.Default) { WeitereQuellen.wstsAusBlaettern(RegisterImport.xlsx(bytes)) } ?: Quellen.unerwartet("fruehsignale", "wsts xlsx")
    }

    // ------------------------------------------------------------ CourtListener (eigener Token)

    // Meldung des Dienstes zum Token: ABGELEHNT, LIMIT oder null.
    @Volatile var clMeldung: String? = null
        private set

    // Ein Geschaeft mit dem Namen der Person, soweit er schon bekannt ist.
    data class Richter(val geschaefte: List<WeitereQuellen.RichterGeschaeft>, val namen: Map<Long, String>, val stand: Long)

    private val richter = Speicher<Richter>(12 * 3_600_000L)
    private val namen = ConcurrentHashMap<Long, String>()

    private suspend fun cl(url: String, token: String): String? {
        val k = kontingent()
        if (!k.erlaubt(COURTLISTENER)) { clMeldung = LIMIT; return null }
        k.buchen(COURTLISTENER)
        val a = api.anfrage(url, kopf + ("Authorization" to "Token $token") + ("Accept" to "application/json"), max = 6_000_000)
        clMeldung = when (a.code) {
            401, 403 -> ABGELEHNT
            429 -> { k.erschoepft(COURTLISTENER); LIMIT }
            else -> if (a.ok) null else clMeldung
        }
        return a.takeIf { it.ok }?.text
    }

    // Zuletzt erfasste Erklaerungen mit Geschaeften und die Namen der ersten drei Personen (ein Abruf je Name, die App
    // bleibt so unter vier Abrufen je Minute). Weitere Namen kommen bei spaeteren Aufrufen dazu.
    suspend fun richter(token: String, neu: Boolean = false): Richter? {
        if (token.isBlank()) return null
        val r = holen(richter, neu) {
            val text = cl(WeitereQuellen.clErklaerungenAdresse(), token) ?: return@holen null
            val liste = withContext(Dispatchers.Default) { WeitereQuellen.clErklaerungenLesen(text) }
            Richter(liste, emptyMap(), System.currentTimeMillis())
        } ?: return null
        val offen = r.geschaefte.map { it.person }.distinct().filter { !namen.containsKey(it) }.take(3)
        for (id in offen) {
            val n = cl(WeitereQuellen.clPersonAdresse(id), token)?.let(WeitereQuellen::clNameLesen) ?: break
            namen[id] = n
        }
        return r.copy(namen = r.geschaefte.map { it.person }.distinct().mapNotNull { id -> namen[id]?.let { id to it } }.toMap())
    }

    // ------------------------------------------------------------ Bluesky (eigenes App-Passwort)

    @Volatile var bskyMeldung: String? = null
        private set

    private val bskySperre = Mutex()
    @Volatile private var sitzung: Triple<String, Long, WeitereQuellen.BskySitzung>? = null
    private val bskyCache = ConcurrentHashMap<String, Pair<Long, WeitereQuellen.BlueskyWoche>>()

    // Anmeldung mit Handle und App-Passwort; die Sitzung gilt hier 90 Minuten (Bluesky: etwa zwei Stunden).
    private suspend fun anmelden(handle: String, passwort: String, neu: Boolean): WeitereQuellen.BskySitzung? {
        val schluessel = "$handle|${passwort.hashCode()}"
        sitzung?.takeIf { !neu && it.first == schluessel && System.currentTimeMillis() - it.second < 90 * 60_000L }?.let { return it.third }
        val k = kontingent()
        if (!k.erlaubt(BLUESKY_ANMELDUNG)) { bskyMeldung = LIMIT; return null }
        k.buchen(BLUESKY_ANMELDUNG)
        val koerper = kotlinx.serialization.json.JsonObject(mapOf(
            "identifier" to kotlinx.serialization.json.JsonPrimitive(handle),
            "password" to kotlinx.serialization.json.JsonPrimitive(passwort),
        )).toString()
        val a = api.anfrage(WeitereQuellen.BSKY_ANMELDUNG, kopf + ("Accept" to "application/json"), json = koerper, max = 200_000)
        if (a.code == 401 || a.code == 400) { bskyMeldung = ABGELEHNT; sitzung = null; return null }
        if (a.code == 429) { bskyMeldung = LIMIT; return null }
        val s = a.takeIf { it.ok }?.text?.let(WeitereQuellen::sitzungLesen) ?: return null
        bskyMeldung = null
        sitzung = Triple(schluessel, System.currentTimeMillis(), s)
        return s
    }

    // Beitraege mit dem Namen in einem Zeitraum, hoechstens drei Seiten zu 100. Null bei Fehler, -1 nach abgelaufener Sitzung.
    private suspend fun zaehlen(s: WeitereQuellen.BskySitzung, begriff: String, von: Instant, bis: Instant): Pair<Int, Boolean>? {
        var n = 0
        var cursor: String? = null
        repeat(3) {
            val k = kontingent()
            if (!k.erlaubt(BLUESKY)) { kotlinx.coroutines.delay(k.warten(BLUESKY).coerceAtMost(1_000L)); if (!k.erlaubt(BLUESKY)) return null }
            k.buchen(BLUESKY)
            val a = api.anfrage(WeitereQuellen.suchAdresse(s.pds, begriff, von, bis, cursor), kopf + ("Authorization" to "Bearer ${s.zugriff}"), max = 2_000_000)
            if (a.code == 400 || a.code == 401) return -1 to false
            val seite = a.takeIf { it.ok }?.text?.let(WeitereQuellen::suchseiteLesen) ?: return null
            n += seite.beitraege
            cursor = seite.cursor ?: return n to false
        }
        return n to (cursor != null)
    }

    // Beitraege zu einem Unternehmen in dieser und der Vorwoche, sechs Stunden je Name gespeichert. Null ohne Zugang.
    suspend fun bluesky(handle: String, passwort: String, name: String): WeitereQuellen.BlueskyWoche? {
        val h = WeitereQuellen.handleBereinigt(handle) ?: return null
        if (!WeitereQuellen.appPasswortGueltig(passwort)) return null
        val begriff = Ersatzquellen.suchname(name).takeIf { it.length >= 3 } ?: return null
        val now = System.currentTimeMillis()
        bskyCache[begriff]?.takeIf { now - it.first < 6 * 3_600_000L }?.let { return it.second }
        return bskySperre.withLock {
            bskyCache[begriff]?.takeIf { now - it.first < 6 * 3_600_000L }?.let { return@withLock it.second }
            val jetzt = Instant.ofEpochMilli(now)
            val vor7 = jetzt.minusSeconds(7 * 86_400L)
            val vor14 = jetzt.minusSeconds(14 * 86_400L)
            var s = anmelden(h, passwort.trim(), neu = false) ?: return@withLock null
            var woche = zaehlen(s, begriff, vor7, jetzt) ?: return@withLock null
            if (woche.first < 0) {
                s = anmelden(h, passwort.trim(), neu = true) ?: return@withLock null
                woche = zaehlen(s, begriff, vor7, jetzt)?.takeIf { it.first >= 0 } ?: return@withLock null
            }
            val vorher = zaehlen(s, begriff, vor14, vor7)?.takeIf { it.first >= 0 } ?: return@withLock null
            val aus = WeitereQuellen.BlueskyWoche(woche.first, vorher.first, woche.second || vorher.second)
            bskyCache[begriff] = now to aus
            aus
        }
    }

    // Neuer Zugang: alte Sitzung und gespeicherte Zahlen verwerfen.
    fun blueskyVergessen() {
        sitzung = null
        bskyCache.clear()
        bskyMeldung = null
    }
}
