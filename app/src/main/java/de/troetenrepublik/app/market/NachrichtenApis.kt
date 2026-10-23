package de.troetenrepublik.app.market

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Nachrichten und Uebersetzung mit eigenem Schluessel: The Guardian (Content API), Finnhub (Market News),
// Alpha Vantage (News & Sentiment) und DeepL fuer die Prediction Markets. Den Schluessel holt sich jede Person
// selbst beim Anbieter, die App schickt ihn nur an diesen Anbieter. Bedingungen, die die App einhaelt:
// Guardian Open Platform: nicht kommerziell, hoechstens 500 Abrufe am Tag und einer je Sekunde. Ueberschrift,
// Autorenzeile, Link zum Original und das Logo Powered by The Guardian bleiben, Inhalte hoechstens 24 Stunden.
// Die Website selbst liest die App nicht aus, das untersagen die allgemeinen Bedingungen des Guardian.
// Finnhub: nur fuer den persoenlichen Gebrauch, 60 Abrufe je Minute, keine Weitergabe der Daten.
// Alpha Vantage: 25 Abrufe am Tag mit kostenlosem Schluessel. News & Sentiment gilt als Premium, bei Ablehnung
// zeigt die App die Meldung des Dienstes.
// DeepL API Free: 500.000 Zeichen im Monat.
object NachrichtenApis {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonElement?.arr(): JsonArray? = this as? JsonArray
    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    // Schlagzeilen oder eine Meldung des Anbieters, etwa zu Schluessel, Limit oder Premium.
    data class Ergebnis(val eintraege: List<Rss.Eintrag>, val meldung: String? = null)

    // The Guardian

    const val GUARDIAN = "https://content.guardianapis.com"
    const val GUARDIAN_LOGO_HELL = "https://static.guim.co.uk/sys-images/Guardian/Pix/pictures/2010/03/01/poweredbyguardianBLACK.png"
    const val GUARDIAN_LOGO_DUNKEL = "https://static.guim.co.uk/sys-images/Guardian/Pix/pictures/2010/03/01/poweredbyguardianWHITE.png"
    // Die Content API erlaubt hoechstens 24 Stunden, eine Stunde bleibt als Reserve.
    const val GUARDIAN_HALTBAR_MS = 23 * 3_600_000L

    // rubrik als section oder tag als Schlagwort.
    fun guardianListe(schluessel: String, rubrik: String? = null, tag: String? = null, n: Int = 30): String =
        "$GUARDIAN/search?order-by=newest&page-size=$n&show-fields=trailText,byline" +
            (rubrik?.let { "&section=${enc(it)}" } ?: "") + (tag?.let { "&tag=${enc(it)}" } ?: "") + "&api-key=${enc(schluessel)}"

    // Artikel mit Text, abgeleitet aus dem Link auf theguardian.com.
    fun guardianArtikel(link: String, schluessel: String): String? {
        val pfad = runCatching { java.net.URI(link) }.getOrNull()?.takeIf { it.host?.endsWith("theguardian.com") == true }?.path?.trim('/')
            ?.takeIf { it.isNotBlank() } ?: return null
        return "$GUARDIAN/$pfad?show-fields=body,byline,headline&api-key=${enc(schluessel)}"
    }

    // Guardian-Seiten liest die App nicht selbst aus.
    fun istGuardian(link: String): Boolean = Rss.domain(link).endsWith("theguardian.com")

    fun guardianLesen(text: String, quelle: String, kategorie: Rss.Kategorie, schluessel: String): Ergebnis {
        val w = runCatching { json.parseToJsonElement(text).obj() }.getOrNull() ?: return Ergebnis(emptyList(), "?")
        val r = w["response"].obj()
        if (r == null || r["status"].text() != "ok") return Ergebnis(emptyList(), (r?.get("message") ?: w["message"]).text() ?: "?")
        val liste = r["results"].arr().orEmpty().mapNotNull { x ->
            val o = x.obj() ?: return@mapNotNull null
            val titel = o["webTitle"].text()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val link = o["webUrl"].text().orEmpty()
            val f = o["fields"].obj()
            Rss.Eintrag(
                titel = titel, link = link, zeit = Rss.zeit(o["webPublicationDate"].text()),
                zusammenfassung = f?.get("trailText").text()?.let { Rss.klartext(it) }.orEmpty().take(400),
                herausgeber = "theguardian.com", quelle = quelle, kategorien = setOf(kategorie),
                quellen = setOf(schluessel), autor = f?.get("byline").text().orEmpty(),
            )
        }
        return Ergebnis(liste)
    }

    // Absaetze und Autorenzeile eines Artikels der Content API.
    data class Artikel(val titel: String, val autor: String, val absaetze: List<String>)

    fun guardianArtikelLesen(text: String): Artikel? {
        val c = runCatching { json.parseToJsonElement(text).obj()?.get("response").obj()?.get("content").obj() }.getOrNull() ?: return null
        val f = c["fields"].obj() ?: return null
        val body = f["body"].text().orEmpty()
        val absaetze = Regex("<p[^>]*>(.*?)</p>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).findAll(body)
            .map { Rss.klartext(it.groupValues[1]) }.filter { it.isNotBlank() }.toList()
        if (absaetze.isEmpty()) return null
        return Artikel(f["headline"].text() ?: c["webTitle"].text().orEmpty(), f["byline"].text().orEmpty(), absaetze)
    }

    // Finnhub

    // Kategorien: general, forex, crypto, merger.
    fun finnhubListe(schluessel: String, kategorie: String): String =
        "https://finnhub.io/api/v1/news?category=${enc(kategorie)}&token=${enc(schluessel)}"

    // Finnhub nennt manche Herausgeber nur mit Namen, hier ihre Domain fuer das Logo.
    private val FINNHUB_HAEUSER = mapOf(
        "yahoo" to "finance.yahoo.com", "cnbc" to "cnbc.com", "marketwatch" to "marketwatch.com", "reuters" to "reuters.com",
        "bloomberg" to "bloomberg.com", "seekingalpha" to "seekingalpha.com", "benzinga" to "benzinga.com",
        "businessinsider" to "businessinsider.com", "motleyfool" to "fool.com", "zacks" to "zacks.com",
        "investing.com" to "investing.com", "fintel" to "fintel.io", "barrons" to "barrons.com", "wsj" to "wsj.com",
        "forbes" to "forbes.com", "thestreet" to "thestreet.com", "investopedia" to "investopedia.com", "coindesk" to "coindesk.com",
        "cointelegraph" to "cointelegraph.com", "financialtimes" to "ft.com", "ft" to "ft.com", "theguardian" to "theguardian.com",
    )

    fun finnhubLesen(text: String, quelle: String, kategorie: Rss.Kategorie, schluessel: String): Ergebnis {
        val e = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return Ergebnis(emptyList(), "?")
        e.obj()?.let { o -> return Ergebnis(emptyList(), o["error"].text() ?: "?") }
        val liste = e.arr().orEmpty().mapNotNull { x ->
            val o = x.obj() ?: return@mapNotNull null
            val titel = o["headline"].text()?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val link = o["url"].text().orEmpty()
            val name = o["source"].text().orEmpty()
            val host = Rss.domain(link)
            val haus = if (host.isNotBlank() && !host.endsWith("finnhub.io")) host
                else FINNHUB_HAEUSER[name.lowercase().replace(" ", "")] ?: name
            Rss.Eintrag(
                titel = titel, link = link, zeit = (o["datetime"].text()?.toLongOrNull() ?: 0L) * 1000L,
                zusammenfassung = o["summary"].text().orEmpty().take(400).takeIf { it != titel }.orEmpty(),
                herausgeber = haus, quelle = quelle, kategorien = setOf(kategorie), quellen = setOf(schluessel),
            )
        }
        return Ergebnis(liste)
    }

    // Alpha Vantage

    fun alphaVantageListe(schluessel: String, thema: String = "financial_markets", n: Int = 50): String =
        "https://www.alphavantage.co/query?function=NEWS_SENTIMENT&topics=${enc(thema)}&sort=LATEST&limit=$n&apikey=${enc(schluessel)}"

    // Format 20261014T205308 in UTC.
    fun alphaVantageZeit(s: String?): Long = runCatching {
        java.time.LocalDateTime.parse(s, java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
            .toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
    }.getOrDefault(0L)

    fun alphaVantageLesen(text: String, quelle: String, kategorie: Rss.Kategorie, schluessel: String): Ergebnis {
        val o = runCatching { json.parseToJsonElement(text).obj() }.getOrNull() ?: return Ergebnis(emptyList(), "?")
        val feed = o["feed"].arr() ?: return Ergebnis(emptyList(), (o["Information"] ?: o["Note"] ?: o["Error Message"]).text() ?: "?")
        val liste = feed.mapNotNull { x ->
            val f = x.obj() ?: return@mapNotNull null
            val titel = f["title"].text()?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val link = f["url"].text().orEmpty()
            val domain = f["source_domain"].text().orEmpty().removePrefix("www.").takeIf { it.contains('.') && !it.contains(' ') } ?: Rss.domain(link)
            Rss.Eintrag(
                titel = titel, link = link, zeit = alphaVantageZeit(f["time_published"].text()),
                zusammenfassung = f["summary"].text().orEmpty().take(400),
                herausgeber = domain.ifBlank { f["source"].text().orEmpty() }, quelle = quelle,
                kategorien = setOf(kategorie), quellen = setOf(schluessel),
            )
        }
        return Ergebnis(liste)
    }

    // DeepL

    // Kostenlose Schluessel enden auf :fx und gehoeren zu api-free.deepl.com.
    fun deeplAdresse(schluessel: String): String =
        if (schluessel.trim().endsWith(":fx")) "https://api-free.deepl.com/v2/translate" else "https://api.deepl.com/v2/translate"

    fun deeplKopf(schluessel: String): Map<String, String> = mapOf("Authorization" to "DeepL-Auth-Key ${schluessel.trim()}")

    fun deeplFormular(texte: List<String>, ziel: String = "DE"): List<Pair<String, String>> =
        texte.map { "text" to it } + listOf("source_lang" to "EN", "target_lang" to ziel)

    // In der Reihenfolge der Texte, null bei unlesbarer Antwort.
    fun deeplLesen(text: String): List<String>? = runCatching {
        json.parseToJsonElement(text).obj()?.get("translations").arr()?.map { it.obj()?.get("text").text().orEmpty() }
    }.getOrNull()
}
