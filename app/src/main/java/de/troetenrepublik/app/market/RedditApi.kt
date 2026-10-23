package de.troetenrepublik.app.market

import de.troetenrepublik.app.engine.RedditAuswertung.Beitrag
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Offizielle Reddit-API mit eigenem Schluessel. Jede Person legt unter reddit.com/prefs/apps eine eigene App an und
// traegt deren Client-ID ein (bei „script“ oder „web app“ auch das Secret). Reddit gibt neue Zugaenge nur auf Antrag.
// Die App meldet sich ohne Reddit-Konto nur fuer die App an („application only“) und liest nur: Suche in einigen
// Boersen-Subreddits und deren neueste Beitraege. Bedingungen, die sie einhaelt: nicht kommerziell, hoechstens
// 100 Abrufe je Minute (die App weniger, siehe Abrufkontingent), eine eigene Kennung im User-Agent ohne Angaben zur
// Person, Inhalte nur zwischengespeichert und mit Link zum Beitrag; Beitraege ab 18 zeigt sie nicht.
class RedditApi(
    private val anfrage: suspend (url: String, kopf: Map<String, String>, formular: List<Pair<String, String>>?) -> MarketApi.Antwort,
    private val kontingent: Abrufkontingent,
    private val uhr: () -> Long = System::currentTimeMillis,
) {
    constructor(api: MarketApi, kontingent: Abrufkontingent) :
        this({ url, kopf, formular -> api.anfrage(url, kopf, formular) }, kontingent)

    companion object {
        const val TOKEN = "https://www.reddit.com/api/v1/access_token"
        const val OAUTH = "https://oauth.reddit.com"
        // Seite zum Anlegen einer App bei Reddit.
        const val APPS = "https://www.reddit.com/prefs/apps"

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
        private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
        private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
        private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

        // Adresse der Suche nach frage in subs, neueste zuerst, im Zeitraum zeit (day, week, month, year).
        fun suchAdresse(subs: List<String>, frage: String, zeit: String = "month", anzahl: Int = 50): String =
            "$OAUTH/r/${subs.joinToString("+")}/search?q=${enc(frage)}&restrict_sr=1&sort=new&t=$zeit&limit=$anzahl&raw_json=1"

        fun neuAdresse(subs: List<String>, anzahl: Int = 100): String = "$OAUTH/r/${subs.joinToString("+")}/new?limit=$anzahl&raw_json=1"

        // Suchanfrage fuer einen Wert: Kuerzel (ohne Boersenzusatz) oder Name, jeweils in Anfuehrungszeichen.
        fun frage(symbol: String, name: String): String {
            val basis = symbol.substringBefore('.').substringBefore('-')
            val kurz = de.troetenrepublik.app.engine.RedditAuswertung.kurzname(name)
            return listOf(basis, kurz).filter { it.length >= 2 }.distinctBy { it.lowercase() }.joinToString(" OR ") { "\"$it\"" }
        }

        // Anmeldung als Formular: mit Secret als vertrauliche App, sonst als installierte App ohne Geraetekennung.
        fun anmeldeFormular(secret: String): List<Pair<String, String>> =
            if (secret.isNotBlank()) listOf("grant_type" to "client_credentials")
            else listOf("grant_type" to "https://oauth.reddit.com/grants/installed_client", "device_id" to "DO_NOT_TRACK_THIS_DEVICE")

        @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
        fun anmeldeKopf(id: String, secret: String): Map<String, String> =
            mapOf("Authorization" to "Basic " + kotlin.io.encoding.Base64.encode("${id.trim()}:${secret.trim()}".encodeToByteArray()))

        // Zugangsschluessel und Gueltigkeit in Sekunden aus der Antwort der Anmeldung.
        fun tokenLesen(text: String): Pair<String, Long>? = runCatching {
            val o = json.parseToJsonElement(text).obj() ?: return null
            val t = o["access_token"].text()?.takeIf { it.isNotBlank() } ?: return null
            t to (o["expires_in"].text()?.toLongOrNull() ?: 3600L)
        }.getOrNull()

        // Beitraege einer Liste (Suche oder neueste); ohne angeheftete Beitraege und ohne Beitraege ab 18.
        fun beitraegeLesen(text: String): List<Beitrag> = runCatching {
            val kinder = json.parseToJsonElement(text).obj()?.get("data").obj()?.get("children") as? JsonArray ?: return emptyList()
            kinder.mapNotNull { k ->
                val d = k.obj()?.get("data").obj() ?: return@mapNotNull null
                if (d["over_18"].text() == "true" || d["stickied"].text() == "true") return@mapNotNull null
                val titel = d["title"].text()?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                Beitrag(
                    id = d["id"].text().orEmpty(),
                    titel = titel,
                    text = d["selftext"].text().orEmpty().take(2_000),
                    sub = d["subreddit"].text().orEmpty(),
                    punkte = d["score"].text()?.toDoubleOrNull()?.toInt() ?: 0,
                    kommentare = d["num_comments"].text()?.toDoubleOrNull()?.toInt() ?: 0,
                    zeit = ((d["created_utc"].text()?.toDoubleOrNull() ?: 0.0) * 1000).toLong(),
                    link = "https://www.reddit.com" + d["permalink"].text().orEmpty(),
                )
            }
        }.getOrDefault(emptyList())
    }

    // Letzte Meldung (falscher Schluessel, Limit, Text von Reddit); null, wenn der letzte Abruf geklappt hat.
    @Volatile var meldung: String? = null
        private set

    @Volatile private var token: Pair<String, Long>? = null
    @Volatile private var tokenFuer: String = ""
    private val sperre = kotlinx.coroutines.sync.Mutex()

    private suspend fun anmelden(id: String, secret: String): String? {
        val schluessel = "$id:$secret"
        token?.takeIf { tokenFuer == schluessel && it.second > uhr() + 60_000L }?.let { return it.first }
        if (!platz()) return null
        val a = anfrage(TOKEN, anmeldeKopf(id, secret), anmeldeFormular(secret))
        if (a.code == 401 || a.code == 403) { meldung = NewsRepository.SCHLUESSEL; return null }
        if (a.code == 429) { kontingent.erschoepft(Abrufkontingent.REDDIT); meldung = NewsRepository.LIMIT; return null }
        if (a.code == 0) return null
        val t = a.text?.takeIf { a.ok }?.let(::tokenLesen)
        if (t == null) { meldung = a.text?.let { fehlertext(it) } ?: "HTTP ${a.code}"; return null }
        token = t.first to uhr() + t.second * 1000L
        tokenFuer = schluessel
        return t.first
    }

    // Den Mindestabstand abwarten, dann einen Abruf verbuchen; false, wenn ein Limit erreicht ist.
    private suspend fun platz(): Boolean {
        val w = kontingent.warten(Abrufkontingent.REDDIT)
        if (w > 0) kotlinx.coroutines.delay(w)
        if (!kontingent.erlaubt(Abrufkontingent.REDDIT)) { meldung = NewsRepository.LIMIT; return false }
        kontingent.buchen(Abrufkontingent.REDDIT)
        return true
    }

    private fun fehlertext(text: String): String? = runCatching {
        json.parseToJsonElement(text).obj()?.let { o -> o["message"].text() ?: o["error"].text() }
    }.getOrNull()

    // Eine Liste abrufen; leer ohne Schluessel, ohne Kontingent oder bei Fehlern (dann steht der Grund in meldung).
    suspend fun liste(url: String, id: String, secret: String): List<Beitrag>? {
        if (id.isBlank()) return null
        sperre.lock()
        try {
            val t = anmelden(id, secret) ?: return null
            if (!platz()) return null
            val a = anfrage(url, mapOf("Authorization" to "bearer $t"), null)
            return when {
                a.code == 0 -> null
                a.code == 401 -> { token = null; meldung = NewsRepository.SCHLUESSEL; null }
                a.code == 429 -> { kontingent.erschoepft(Abrufkontingent.REDDIT); meldung = NewsRepository.LIMIT; null }
                !a.ok -> { meldung = a.text?.let { fehlertext(it) } ?: "HTTP ${a.code}"; null }
                else -> { meldung = null; beitraegeLesen(a.text!!) }
            }
        } finally { sperre.unlock() }
    }
}
