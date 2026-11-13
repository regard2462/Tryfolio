package de.troetenrepublik.app.market

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URLEncoder
import java.time.Instant

// Zwei weitere Quellen der Prognosemaerkte, beide ohne Echtgeld und deshalb nur zur Anzeige (keine Uebungswetten):
// Manifold: Maerkte mit Spielgeld (Mana). Oeffentliche Schnittstelle api.manifold.markets/v0, Lesen ohne Schluessel
// (search-markets, market/<id>, bets fuer den Verlauf). Gelesen werden nur Maerkte mit Ja/Nein.
// Metaculus: Gemeinschaftsprognose ohne Geldeinsatz. Die Schnittstelle www.metaculus.com/api verlangt bei jeder
// Anfrage das Token eines eigenen Kontos (Kopfzeile Authorization: Token …) und gibt die Gemeinschaftsprognose nur
// fuer Konten aus, die Metaculus dafuer freigeschaltet hat; sonst kommt null zurueck (hier als „verborgen“ gezaehlt).
// Dieses Objekt enthaelt nur Adressen und das Einlesen. Namen und Kennungen von Nutzern liest es nicht.
object PrognoseWeitere {
    const val MANIFOLD = "https://api.manifold.markets/v0"
    const val MANIFOLD_SEITE = "https://manifold.markets"
    const val METACULUS = "https://www.metaculus.com/api"
    const val METACULUS_SEITE = "https://www.metaculus.com"
    const val METACULUS_TOKEN_SEITE = "https://www.metaculus.com/accounts/settings/account/#api-access"

    // Die erste Kategorie ist die des Widgets; Schluessel sind die Themen (topicSlug) von Manifold.
    val MANIFOLD_KATEGORIEN = listOf(
        Prognose.Kategorie("economics-default", "Wirtschaft", "Economics"), Prognose.Kategorie("finance", "Finanzen", "Finance"),
        Prognose.Kategorie("stocks", "Aktien", "Stocks"), Prognose.Kategorie("business", "Unternehmen", "Business"),
        Prognose.Kategorie("geopolitics", "Geopolitik", "Geopolitics"), Prognose.Kategorie("politics-default", "Politik", "Politics"),
        Prognose.Kategorie("us-politics", "US-Politik", "US politics"), Prognose.Kategorie("world-default", "Welt", "World"),
        Prognose.Kategorie("technology-default", "Technik", "Tech"), Prognose.Kategorie("ai", "KI", "AI"),
        Prognose.Kategorie("science-default", "Wissenschaft", "Science"), Prognose.Kategorie("crypto-speculation", "Krypto", "Crypto"),
        Prognose.Kategorie("", "Trends", "Trending"), Prognose.Kategorie("sports-default", "Sport", "Sports"),
    )

    // Kategorien (slug) von Metaculus.
    val METACULUS_KATEGORIEN = listOf(
        Prognose.Kategorie("economy-business", "Wirtschaft", "Economy"), Prognose.Kategorie("geopolitics", "Geopolitik", "Geopolitics"),
        Prognose.Kategorie("politics", "Politik", "Politics"), Prognose.Kategorie("elections", "Wahlen", "Elections"),
        Prognose.Kategorie("artificial-intelligence", "KI", "AI"), Prognose.Kategorie("technology", "Technik", "Tech"),
        Prognose.Kategorie("health-pandemics", "Gesundheit", "Health"), Prognose.Kategorie("nuclear", "Atomwaffen und Kernkraft", "Nuclear"),
        Prognose.Kategorie("environment-climate", "Klima und Umwelt", "Climate"), Prognose.Kategorie("cryptocurrencies", "Krypto", "Crypto"),
        Prognose.Kategorie("", "Trends", "Trending"),
    )

    private val json = Json { ignoreUnknownKeys = true }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonElement?.arr(): JsonArray? = this as? JsonArray
    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
    private fun JsonElement?.zahl(): Double? = text()?.toDoubleOrNull()
    private fun JsonElement?.wahr(): Boolean? = text()?.toBooleanStrictOrNull()
    private fun zeit(iso: String?): Long = iso?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
    private fun wurzel(text: String): JsonElement? = runCatching { json.parseToJsonElement(text) }.getOrNull()

    private fun jaNein(p: Double): List<Prognose.Seite> {
        val q = p.coerceIn(0.0, 1.0)
        return listOf(Prognose.Seite("Yes", q, q, q), Prognose.Seite("No", 1.0 - q, 1.0 - q, 1.0 - q))
    }

    // Schlagworte in der Form der Auswahl „Wichtigste“: klein, ohne den Zusatz „-default“.
    private fun wort(s: String): String = s.lowercase().removeSuffix("-default")

    // Manifold

    fun manifoldListe(thema: String, versatz: Int, n: Int = 15): String =
        "$MANIFOLD/search-markets?term=&sort=score&filter=open&contractType=BINARY&limit=$n&offset=$versatz" +
            if (thema.isNotBlank()) "&topicSlug=${enc(thema)}" else ""

    fun manifoldSuche(q: String): String = "$MANIFOLD/search-markets?term=${enc(q)}&sort=score&filter=open&contractType=BINARY&limit=20"
    fun manifoldMarkt(id: String): String = "$MANIFOLD/market/${enc(id)}"

    // Handel eines Marktes, neueste zuerst, hoechstens 1.000 je Abruf; vor: Kennung des aeltesten schon gelesenen.
    fun manifoldHandel(id: String, vor: String? = null): String =
        "$MANIFOLD/bets?contractId=${enc(id)}&limit=1000" + (vor?.takeIf { it.isNotBlank() }?.let { "&before=${enc(it)}" } ?: "")

    private fun manifoldMarktAus(m: JsonObject): Prognose.Markt? {
        if (m["outcomeType"].text() != "BINARY") return null
        val id = m["id"].text() ?: return null
        val p = m["probability"].zahl() ?: return null
        val frage = m["question"].text().orEmpty()
        val aufgeloest = m["isResolved"].wahr() == true
        val auszahlung = if (!aufgeloest) null else when (m["resolution"].text()) {
            "YES" -> 1.0
            "NO" -> 0.0
            "MKT" -> m["resolutionProbability"].zahl()
            else -> null
        }
        return Prognose.Markt(
            quelle = Prognose.Quelle.MANIFOLD, id = id, titel = frage, frage = frage, seiten = jaNein(p), aenderung = null,
            volumen = m["volume"].zahl() ?: 0.0, ende = m["closeTime"].zahl()?.toLong() ?: 0L, offen = !aufgeloest,
            gewinner = when (auszahlung) { 1.0 -> 0; 0.0 -> 1; else -> null }, auszahlung = auszahlung,
            regeln = m["textDescription"].text().orEmpty(),
        )
    }

    // Ein Markt als Ereignis mit einem Ausgang. thema: das abgefragte Thema, wenn der Markt selbst keine nennt.
    private fun manifoldEreignisAus(m: JsonObject, thema: String): Prognose.Ereignis? {
        val markt = manifoldMarktAus(m) ?: return null
        val themen = m["groupSlugs"].arr()?.mapNotNull { it.text() }.orEmpty()
        val link = m["url"].text()?.takeIf { it.startsWith("$MANIFOLD_SEITE/") } ?: MANIFOLD_SEITE
        return Prognose.Ereignis(
            quelle = Prognose.Quelle.MANIFOLD, id = markt.id, titel = markt.titel, untertitel = "",
            kategorie = thema.ifBlank { themen.firstOrNull { t -> MANIFOLD_KATEGORIEN.any { it.schluessel == t } }.orEmpty() },
            bild = m["coverImageUrl"].text().orEmpty(), beschreibung = markt.regeln, volumen = markt.volumen, volumen24h = m["volume24Hours"].zahl() ?: 0.0,
            ende = markt.ende, maerkte = listOf(markt), link = link,
            schlagworte = (themen + thema).filter { it.isNotBlank() }.map { wort(it) }.distinct(),
            teilnehmer = m["uniqueBettorCount"].zahl()?.toInt() ?: 0,
        )
    }

    fun manifoldListeLesen(text: String, thema: String = ""): List<Prognose.Ereignis> =
        wurzel(text).arr()?.mapNotNull { it.obj()?.let { m -> manifoldEreignisAus(m, thema) } }.orEmpty()

    fun manifoldEreignisText(text: String): Prognose.Ereignis? = wurzel(text).obj()?.let { manifoldEreignisAus(it, "") }
    fun manifoldMarktText(text: String): Prognose.Markt? = wurzel(text).obj()?.let { manifoldMarktAus(it) }

    // Verlauf aus dem Handel: Wahrscheinlichkeit nach jedem Geschaeft, aelteste zuerst. Wer gehandelt hat, bleibt weg.
    fun manifoldVerlaufLesen(text: String): List<Pair<Long, Double>> =
        wurzel(text).arr()?.mapNotNull { b ->
            val o = b.obj() ?: return@mapNotNull null
            if (o["isRedemption"].wahr() == true) return@mapNotNull null
            val t = o["createdTime"].zahl() ?: return@mapNotNull null
            val p = o["probAfter"].zahl() ?: return@mapNotNull null
            t.toLong() to p
        }.orEmpty().sortedBy { it.first }

    // Kennung des aeltesten Geschaefts der Seite fuer den naechsten Abruf; null, wenn die Seite nicht voll war.
    fun manifoldHandelWeiter(text: String, voll: Int = 1000): String? {
        val l = wurzel(text).arr() ?: return null
        return if (l.size < voll) null else l.lastOrNull().obj()?.get("id").text()
    }

    // Hoechstens max Punkte in gleichen Abstaenden, der letzte bleibt.
    fun ausduennen(punkte: List<Pair<Long, Double>>, max: Int = 400): List<Pair<Long, Double>> {
        if (punkte.size <= max || max < 2) return punkte
        val schritt = (punkte.size - 1).toDouble() / (max - 1)
        return (0 until max).map { punkte[Math.round(it * schritt).toInt().coerceAtMost(punkte.size - 1)] }
    }

    // Metaculus

    private val TOKEN = Regex("^[A-Za-z0-9_-]{16,128}$")

    // Nur Zeichen eines Tokens; so gelangt nichts anderes in die Kopfzeile.
    fun tokenGueltig(s: String): Boolean = TOKEN.matches(s.trim())
    fun metaculusKopf(token: String): Map<String, String>? = if (tokenGueltig(token)) mapOf("Authorization" to "Token ${token.trim()}") else null

    // Offene Fragen mit Ja/Nein, Auswahl oder Fragengruppe, die gefragtesten zuerst.
    fun metaculusListe(kategorie: String, versatz: Int, n: Int = 20, suche: String = ""): String =
        "$METACULUS/posts/?statuses=open&forecast_type=binary&forecast_type=multiple_choice&forecast_type=group_of_questions" +
            "&with_cp=true&order_by=-hotness&limit=$n&offset=$versatz" +
            (if (kategorie.isNotBlank()) "&categories=${enc(kategorie)}" else "") +
            (if (suche.isNotBlank()) "&search=${enc(suche)}" else "")

    fun metaculusBeitrag(id: String): String? =
        if (id.isNotEmpty() && id.length <= 12 && id.all(Char::isDigit)) "$METACULUS/posts/$id/?with_cp=true&include_cp_history=true" else null

    fun metaculusLink(id: String): String = "$METACULUS_SEITE/questions/$id/"

    private fun gemeinschaft(q: JsonObject): JsonObject? = q["aggregations"].obj()?.get("recency_weighted").obj()

    // Maerkte einer Frage: Ja/Nein als ein Markt (Mitte der Gemeinschaftsprognose), eine Auswahl als ein Markt je
    // Option. null, wenn die Quelle die Prognose nicht ausgibt; leer bei anderen Arten (Zahl, Datum).
    private fun metaculusMaerkte(q: JsonObject, einzeln: Boolean, beitrag: String): List<Prognose.Markt>? {
        val id = q["id"].text() ?: return emptyList()
        val titel = q["label"].text().orEmpty().ifBlank { q["title"].text().orEmpty() }
        val frage = q["title"].text().orEmpty()
        val ende = zeit(q["scheduled_close_time"].text())
        val status = q["status"].text().orEmpty()
        val offen = status.isBlank() || status == "open"
        val ergebnis = q["resolution"].text().orEmpty().lowercase()
        val regeln = listOfNotNull(q["description"].text(), q["resolution_criteria"].text(), q["fine_print"].text()).filter { it.isNotBlank() }.joinToString("\n\n")
        val stand = gemeinschaft(q)?.get("latest").obj()
        return when (q["type"].text()) {
            "binary" -> {
                val p = stand?.get("centers").arr()?.firstOrNull().zahl() ?: return null
                listOf(
                    Prognose.Markt(
                        quelle = Prognose.Quelle.METACULUS, id = id, titel = titel, frage = frage, seiten = jaNein(p), aenderung = null, volumen = 0.0,
                        ende = ende, offen = offen, gewinner = when (ergebnis) { "yes" -> 0; "no" -> 1; else -> null },
                        auszahlung = when (ergebnis) { "yes" -> 1.0; "no" -> 0.0; else -> null }, serie = beitrag, regeln = regeln,
                    ),
                )
            }
            "multiple_choice" -> if (!einzeln) emptyList() else {
                val optionen = q["options"].arr()?.mapNotNull { it.text() }.orEmpty()
                val werte = stand?.get("forecast_values").arr()?.map { it.zahl() }
                if (optionen.isEmpty()) emptyList()
                else if (werte == null || werte.size != optionen.size || werte.any { it == null }) null
                else optionen.mapIndexed { i, o ->
                    Prognose.Markt(
                        quelle = Prognose.Quelle.METACULUS, id = "$id-$i", titel = o, frage = frage, seiten = jaNein(werte[i] ?: 0.0), aenderung = null,
                        volumen = 0.0, ende = ende, offen = offen, gewinner = if (ergebnis.isBlank()) null else if (ergebnis == o.lowercase()) 0 else 1,
                        serie = beitrag, regeln = regeln,
                    )
                }
            }
            else -> emptyList()
        }
    }

    // Ein Beitrag als Ereignis und ob die Quelle seine Prognose verbirgt (dann ohne Ereignis).
    private fun metaculusBeitragAus(p: JsonObject): Pair<Prognose.Ereignis?, Boolean> {
        val id = p["id"].text() ?: return null to false
        val einzel = p["question"].obj()
        val gruppe = p["group_of_questions"].obj()
        val fragen = einzel?.let { listOf(it) } ?: gruppe?.get("questions").arr()?.mapNotNull { it.obj() }.orEmpty()
        val teile = fragen.map { metaculusMaerkte(it, einzeln = einzel != null, beitrag = id) }
        val maerkte = teile.filterNotNull().flatten().sortedByDescending { it.chance }
        if (maerkte.isEmpty()) return null to teile.any { it == null }
        val kategorien = p["projects"].obj()?.get("category").arr()?.mapNotNull { it.obj() }.orEmpty()
        val slugs = kategorien.mapNotNull { it["slug"].text() }
        val regeln = listOfNotNull(gruppe?.get("description").text(), gruppe?.get("resolution_criteria").text()).filter { it.isNotBlank() }.joinToString("\n\n")
        return Prognose.Ereignis(
            quelle = Prognose.Quelle.METACULUS, id = id, titel = p["title"].text().orEmpty(), untertitel = "",
            kategorie = slugs.firstOrNull { s -> METACULUS_KATEGORIEN.any { it.schluessel == s } } ?: kategorien.firstOrNull()?.get("name").text().orEmpty(),
            bild = "", beschreibung = regeln.ifBlank { maerkte.first().regeln }, volumen = 0.0, volumen24h = 0.0,
            ende = zeit(p["scheduled_close_time"].text()).takeIf { it > 0 } ?: maerkte.filter { it.ende > 0 }.minOfOrNull { it.ende } ?: 0L,
            maerkte = maerkte, link = metaculusLink(id),
            // Der slug und seine Teile („sports-entertainment“ zaehlt als Sport und als Unterhaltung)
            schlagworte = slugs.flatMap { listOf(it) + it.split('-') }.map { it.lowercase() }.filter { it.isNotBlank() }.distinct(),
            teilnehmer = p["nr_forecasters"].zahl()?.toInt() ?: 0,
        ) to false
    }

    // Liste der Beitraege (/posts/): results und next. versatz und n fuer die naechste Seite.
    fun metaculusListeLesen(text: String, versatz: Int = 0, n: Int = 20): Prognose.Seitenliste? {
        val w = wurzel(text).obj() ?: return null
        val ergebnisse = w["results"].arr() ?: return null
        val gelesen = ergebnisse.mapNotNull { it.obj() }.map { metaculusBeitragAus(it) }
        return Prognose.Seitenliste(
            gelesen.mapNotNull { it.first }, if (w["next"].text().isNullOrBlank()) null else (versatz + n).toString(), gelesen.count { it.second },
        )
    }

    fun metaculusEreignisText(text: String): Prognose.Ereignis? = wurzel(text).obj()?.let { metaculusBeitragAus(it).first }

    // Verlauf der Gemeinschaftsprognose einer Ja/Nein-Frage aus dem Beitrag: Beginn jedes Abschnitts und seine Mitte.
    fun metaculusVerlaufLesen(text: String, frage: String): List<Pair<Long, Double>> {
        val p = wurzel(text).obj() ?: return emptyList()
        val fragen = p["question"].obj()?.let { listOf(it) } ?: p["group_of_questions"].obj()?.get("questions").arr()?.mapNotNull { it.obj() }.orEmpty()
        val q = fragen.firstOrNull { it["id"].text() == frage } ?: return emptyList()
        val g = gemeinschaft(q) ?: return emptyList()
        return (g["history"].arr().orEmpty() + listOfNotNull(g["latest"])).mapNotNull { h ->
            val o = h.obj() ?: return@mapNotNull null
            val t = o["start_time"].zahl() ?: return@mapNotNull null
            val v = o["centers"].arr()?.firstOrNull().zahl() ?: return@mapNotNull null
            (t * 1000).toLong() to v
        }.distinctBy { it.first }.sortedBy { it.first }
    }
}
