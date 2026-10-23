package de.troetenrepublik.app.market

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URLEncoder
import java.time.Instant

// Prognosemaerkte: Polymarket und Kalshi. Beide Boersen veroeffentlichen ihre Maerkte, Kurse und
// Kursverlaeufe ueber offene Schnittstellen ohne Anmeldung. Ein Kurs ist der Preis eines Anteils, der bei Eintreten
// 1 Dollar zahlt, und damit die vom Markt geschaetzte Wahrscheinlichkeit.
// Polymarket: Gamma-Schnittstelle gamma-api.polymarket.com fuer Ereignisse, Suche und Maerkte, CLOB fuer Verlaeufe
// (clob.polymarket.com/prices-history).
// Kalshi: Suche der Webseite (/v1/search/series, nicht offiziell dokumentiert) fuer Trends, Kategorien und Volltext,
// dazu die dokumentierte Handelsschnittstelle /trade-api/v2 fuer Ereignisse, Maerkte und Kerzen.
// Dieses Objekt enthaelt nur Adressen und das Einlesen; das Laden macht PrognoseRepository.
object Prognose {
    enum class Quelle(val anzeige: String) { POLYMARKET("Polymarket"), KALSHI("Kalshi") }

    // Eine Seite eines Marktes (Ja/Nein oder zwei Mannschaften): Kurs, Kaufkurs (Brief), Verkaufskurs (Geld).
    data class Seite(val name: String, val preis: Double, val kauf: Double, val verkauf: Double, val token: String = "")

    // Ein Markt mit zwei Seiten; bei Ereignissen mit mehreren Ausgaengen ist jeder Ausgang ein eigener Markt.
    data class Markt(
        val quelle: Quelle,
        val id: String,
        val titel: String,
        val frage: String,
        val seiten: List<Seite>,
        // Veraenderung der Wahrscheinlichkeit der ersten Seite in 24 Stunden, in Prozentpunkten.
        val aenderung: Double?,
        val volumen: Double,
        val ende: Long,
        val offen: Boolean,
        // Bei aufgeloesten Maerkten die Seite, die gewonnen hat (0 oder 1); bei 50/50 null mit auszahlung.
        val gewinner: Int? = null,
        // Auszahlung je Anteil der ersten Seite nach der Aufloesung (1, 0 oder 0,5); null, solange offen.
        val auszahlung: Double? = null,
        val serie: String = "",
        val regeln: String = "",
    ) {
        val chance: Double get() = seiten.firstOrNull()?.preis ?: 0.0
    }

    data class Ereignis(
        val quelle: Quelle,
        val id: String,
        val titel: String,
        val untertitel: String,
        val kategorie: String,
        val bild: String,
        val beschreibung: String,
        val volumen: Double,
        val volumen24h: Double,
        val ende: Long,
        val maerkte: List<Markt>,
        val link: String,
        val serie: String = "",
        // Nummer des Ereignisses bei Polymarket (fuer die Abfrage grosser Wetten).
        val nummer: String = "",
    ) {
        val binaer: Boolean get() = maerkte.size == 1
    }

    // Eine grosse Wette bei Polymarket: Markt, gekaufte oder verkaufte Seite, Einsatz in Dollar (Anteile mal Preis).
    data class GrosseWette(
        val titel: String,
        val ausgang: String,
        val kauf: Boolean,
        val betrag: Double,
        val preis: Double,
        val zeit: Long,
        val ereignis: String,
        val bild: String,
        // Schluessel aus THEMEN, leer, solange das Thema nicht bekannt ist.
        val thema: String = "",
    )

    // Zeitraeume der grossen Wetten. Je laenger der Zeitraum, desto hoeher die Untergrenze, damit wenige Abfragen reichen.
    enum class WettZeit(val stunden: Int, val mindestens: Int) { TAG(24, 10_000), WOCHE(7 * 24, 50_000), MONAT(30 * 24, 200_000) }

    // Themen der grossen Wetten in der Reihenfolge der Kategorien bei Polymarket, dahinter Kultur und Sonstiges.
    val THEMEN = listOf(
        Kategorie("economy", "Wirtschaft", "Economy"), Kategorie("finance", "Finanzen", "Finance"),
        Kategorie("geopolitics", "Geopolitik", "Geopolitics"), Kategorie("politics", "Politik", "Politics"),
        Kategorie("crypto", "Krypto", "Crypto"), Kategorie("elections", "Wahlen", "Elections"),
        Kategorie("tech", "Technik", "Tech"), Kategorie("world", "Welt", "World"), Kategorie("sports", "Sport", "Sports"),
        Kategorie("culture", "Kultur", "Culture"), Kategorie("other", "Sonstiges", "Other"),
    )

    // Schlagworte je Thema. Traegt ein Ereignis mehrere, entscheidet die Reihenfolge hier: das genauere Thema zuerst.
    private val THEMA_WORTE = listOf(
        "sports" to setOf("sports", "esports", "games"),
        "crypto" to setOf("crypto", "bitcoin", "ethereum", "crypto-prices"),
        "elections" to setOf("elections", "midterms", "global-elections", "us-presidential-election"),
        "geopolitics" to setOf("geopolitics", "ukraine", "middle-east", "china", "israel"),
        "economy" to setOf("economy", "fed", "economic-policy", "fed-rates", "inflation", "tariffs"),
        "finance" to setOf("finance", "business", "stocks", "earnings", "ipos", "corporate-financials"),
        "politics" to setOf("politics", "us-politics", "trump"),
        "tech" to setOf("tech", "ai", "science"),
        "culture" to setOf("culture", "pop-culture", "entertainment", "movies", "music", "awards"),
        "world" to setOf("world"),
    )

    fun thema(schlagworte: List<String>): String {
        val s = schlagworte.map { it.lowercase() }.toSet()
        return THEMA_WORTE.firstOrNull { (_, worte) -> worte.any { it in s } }?.first ?: "other"
    }

    // Schlagworte (slug) eines Ereignisses aus /events/slug/<slug>.
    fun polySchlagworte(text: String): List<String> = runCatching {
        json.parseToJsonElement(text).obj()?.get("tags").arr()?.mapNotNull { it.obj()?.get("slug").text() }
    }.getOrNull().orEmpty()

    // Eine Seite der Liste und, wenn es weitergeht, der Wert fuer die naechste Seite (Versatz oder Cursor).
    data class Seitenliste(val ereignisse: List<Ereignis>, val weiter: String?)

    data class Kategorie(val schluessel: String, val de: String, val en: String)

    // Die erste Kategorie ist vorausgewaehlt.
    val POLY_KATEGORIEN = listOf(
        Kategorie("economy", "Wirtschaft", "Economy"), Kategorie("finance", "Finanzen", "Finance"), Kategorie("geopolitics", "Geopolitik", "Geopolitics"),
        Kategorie("politics", "Politik", "Politics"), Kategorie("", "Trends", "Trending"), Kategorie("crypto", "Krypto", "Crypto"),
        Kategorie("elections", "Wahlen", "Elections"), Kategorie("tech", "Technik", "Tech"), Kategorie("world", "Welt", "World"),
        Kategorie("sports", "Sport", "Sports"),
    )
    val KALSHI_KATEGORIEN = listOf(
        Kategorie("Economics", "Wirtschaft", "Economics"), Kategorie("Financials", "Finanzen", "Financials"),
        Kategorie("Politics", "Politik", "Politics"), Kategorie("", "Trends", "Trending"), Kategorie("Elections", "Wahlen", "Elections"), Kategorie("Crypto", "Krypto", "Crypto"),
        Kategorie("Companies", "Unternehmen", "Companies"), Kategorie("Climate and Weather", "Klima und Wetter", "Climate and weather"),
        Kategorie("Science and Technology", "Wissenschaft und Technik", "Science and technology"),
        Kategorie("Entertainment", "Unterhaltung", "Entertainment"), Kategorie("Health", "Gesundheit", "Health"), Kategorie("Sports", "Sport", "Sports"),
    )

    // Zeitraeume des Verlaufs und ihre Abfrage bei beiden Boersen.
    enum class Bereich(val sekunden: Long, val polyIntervall: String, val polyFidelity: Int, val kalshiMinuten: Int) {
        TAG(86_400L, "1d", 10, 60), WOCHE(7 * 86_400L, "1w", 60, 60), MONAT(30 * 86_400L, "1m", 360, 1440), MAX(3 * 365 * 86_400L, "max", 1440, 1440)
    }

    const val POLY = "https://gamma-api.polymarket.com"
    const val KALSHI = "https://api.elections.kalshi.com"

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    // Adressen

    fun polyListe(tag: String, versatz: Int, n: Int = 15): String =
        "$POLY/events?active=true&closed=false&archived=false&order=volume24hr&ascending=false&limit=$n&offset=$versatz" +
            if (tag.isNotBlank()) "&tag_slug=${enc(tag)}" else ""

    fun polySuche(q: String): String = "$POLY/public-search?q=${enc(q)}&limit_per_type=20&events_status=active"
    fun polyEreignis(slug: String): String = "$POLY/events/slug/${enc(slug)}"
    fun polyMarkt(id: String): String = "$POLY/markets/${enc(id)}"
    fun polyVerlauf(token: String, b: Bereich): String =
        "https://clob.polymarket.com/prices-history?market=${enc(token)}&interval=${b.polyIntervall}&fidelity=${b.polyFidelity}"

    // Kursverlauf einer Seite (Token) zwischen zwei Zeitpunkten in Sekunden, fidelity in Minuten.
    fun polyVerlaufZwischen(token: String, vonSek: Long, bisSek: Long, fidelity: Int): String =
        "https://clob.polymarket.com/prices-history?market=${enc(token)}&startTs=$vonSek&endTs=$bisSek&fidelity=$fidelity"

    fun kalshiListe(kategorie: String, cursor: String?, n: Int = 15): String =
        "$KALSHI/v1/search/series?order_by=trending&page_size=$n" +
            (if (kategorie.isNotBlank()) "&category=${enc(kategorie)}" else "") +
            (if (!cursor.isNullOrBlank()) "&cursor=${enc(cursor)}" else "")

    fun kalshiSuche(q: String): String = "$KALSHI/v1/search/series?query=${enc(q)}&order_by=querymatch&page_size=20"
    fun kalshiEreignis(ticker: String): String = "$KALSHI/trade-api/v2/events/${enc(ticker)}?with_nested_markets=true"
    fun kalshiMarkt(ticker: String): String = "$KALSHI/trade-api/v2/markets/${enc(ticker)}"
    fun kalshiVerlauf(serie: String, ticker: String, von: Long, bis: Long, minuten: Int): String =
        "$KALSHI/trade-api/v2/series/${enc(serie)}/markets/${enc(ticker)}/candlesticks?start_ts=$von&end_ts=$bis&period_interval=$minuten"

    // Bild einer Kalshi-Serie. Kalshi legt es unter einer festen Adresse ab (so nennt es auch
    // /trade-api/v2/events/<ticker>/metadata); die Suche liefert selbst keine Bilder.
    fun kalshiBild(serie: String): String = if (serie.isBlank()) "" else "https://assets.kalshi.com/series-images-webp/${enc(serie)}.webp"

    fun polyLink(slug: String): String = "https://polymarket.com/event/$slug"
    fun kalshiLink(serie: String): String = "https://kalshi.com/markets/${serie.lowercase()}"

    // Hilfen

    private val json = Json { ignoreUnknownKeys = true }

    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonElement?.arr(): JsonArray? = this as? JsonArray
    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
    private fun JsonElement?.zahl(): Double? = text()?.toDoubleOrNull()
    private fun JsonElement?.wahr(): Boolean? = text()?.toBooleanStrictOrNull()

    private fun zeit(iso: String?): Long = iso?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L

    // Polymarket liefert Listen als JSON-Text in einem Feld, also als String statt als Array.
    private fun liste(e: JsonElement?): List<String> {
        val t = e.text() ?: return e.arr()?.mapNotNull { it.text() } ?: emptyList()
        return runCatching { json.parseToJsonElement(t).arr()?.mapNotNull { it.text() } }.getOrNull() ?: emptyList()
    }

    private fun innen(p: Double): Double = p.coerceIn(0.001, 0.999)

    // Polymarket

    fun polyMarktLesen(m: JsonObject): Markt? {
        val namen = liste(m["outcomes"])
        val preise = liste(m["outcomePrices"]).map { it.toDoubleOrNull() ?: 0.0 }
        val tokens = liste(m["clobTokenIds"])
        if (namen.size != 2 || preise.size != 2) return null
        val bid = m["bestBid"].zahl()
        val ask = m["bestAsk"].zahl()
        val geschlossen = m["closed"].wahr() == true
        val p0 = preise[0]
        val kauf0 = ask?.takeIf { it > 0 } ?: innen(p0 + 0.005)
        val verkauf0 = bid?.takeIf { it > 0 } ?: innen(p0 - 0.005)
        val seiten = listOf(
            Seite(namen[0], p0, kauf0, verkauf0, tokens.getOrElse(0) { "" }),
            Seite(namen[1], preise[1], innen(1.0 - verkauf0), innen(1.0 - kauf0), tokens.getOrElse(1) { "" }),
        )
        val aufgeloest = geschlossen && (preise.all { it <= 0.001 || it >= 0.999 } || m["umaResolutionStatus"].text() == "resolved")
        val auszahlung = if (aufgeloest) p0.let { if (it >= 0.999) 1.0 else if (it <= 0.001) 0.0 else it } else null
        return Markt(
            quelle = Quelle.POLYMARKET,
            id = m["id"].text() ?: return null,
            titel = m["groupItemTitle"].text().orEmpty().ifBlank { m["question"].text().orEmpty() },
            frage = m["question"].text().orEmpty(),
            seiten = seiten,
            aenderung = m["oneDayPriceChange"].zahl()?.let { it * 100.0 },
            volumen = m["volumeNum"].zahl() ?: m["volume"].zahl() ?: 0.0,
            ende = zeit(m["endDate"].text()),
            offen = !geschlossen && m["active"].wahr() != false,
            gewinner = when (auszahlung) { 1.0 -> 0; 0.0 -> 1; else -> null },
            auszahlung = auszahlung,
            regeln = m["description"].text().orEmpty(),
        )
    }

    fun polyEreignisLesen(e: JsonObject, nurOffen: Boolean = true): Ereignis? {
        val slug = e["slug"].text() ?: return null
        val maerkte = (e["markets"].arr() ?: JsonArray(emptyList())).mapNotNull { it.obj() }
            .filter { m -> m["archived"].wahr() != true }
            .mapNotNull { polyMarktLesen(it) }
            .filter { !nurOffen || it.offen }
            .sortedByDescending { it.chance }
        if (maerkte.isEmpty()) return null
        val tags = e["tags"].arr()?.mapNotNull { it.obj()?.get("label").text() }.orEmpty()
        return Ereignis(
            quelle = Quelle.POLYMARKET,
            id = slug,
            titel = e["title"].text().orEmpty(),
            untertitel = "",
            kategorie = tags.firstOrNull { t -> POLY_KATEGORIEN.any { it.en.equals(t, true) } } ?: tags.firstOrNull().orEmpty(),
            bild = e["icon"].text() ?: e["image"].text().orEmpty(),
            beschreibung = e["description"].text().orEmpty(),
            volumen = e["volume"].zahl() ?: maerkte.sumOf { it.volumen },
            volumen24h = e["volume24hr"].zahl() ?: 0.0,
            ende = zeit(e["endDate"].text()).takeIf { it > 0 } ?: maerkte.minOf { it.ende },
            maerkte = maerkte,
            link = polyLink(slug),
            nummer = e["id"].text().orEmpty(),
        )
    }

    const val POLY_DATEN = "https://data-api.polymarket.com/v2/trades"

    // Groesste Wetten ueber alle Maerkte (ab mindestens Dollar) oder eines Ereignisses (nummer).
    fun grosseWettenAdresse(mindestens: Int, nummer: String? = null, cursor: String? = null): String =
        "$POLY_DATEN?filter_type=CASH&filter_amount=$mindestens&limit=500" +
            (nummer?.takeIf { it.all(Char::isDigit) && it.isNotEmpty() }?.let { "&event_id=$it" } ?: "") +
            (cursor?.takeIf { it.isNotBlank() }?.let { "&cursor=${enc(it)}" } ?: "")

    // Naechste Seite der Abfrage, null am Ende.
    fun grosseWettenWeiter(text: String): String? = runCatching {
        val p = json.parseToJsonElement(text).obj()?.get("pagination").obj()
        if (p?.get("has_more").wahr() == true) p?.get("next_cursor").text() else null
    }.getOrNull()

    // Namen und Wallets der Wettenden bleiben weg.
    fun grosseWettenLesen(text: String): List<GrosseWette> = runCatching {
        val wurzel = json.parseToJsonElement(text)
        (wurzel.obj()?.get("data").arr() ?: wurzel.arr() ?: return emptyList()).mapNotNull { it.obj() }.mapNotNull { o ->
            val menge = o["size"].zahl() ?: return@mapNotNull null
            val preis = o["price"].zahl() ?: return@mapNotNull null
            GrosseWette(
                titel = o["title"].text().orEmpty(),
                ausgang = o["outcome"].text().orEmpty(),
                kauf = o["side"].text().equals("BUY", true),
                betrag = menge * preis,
                preis = preis,
                zeit = (o["timestamp"].zahl() ?: 0.0).toLong() * 1000L,
                ereignis = o["event_slug"].text() ?: o["eventSlug"].text().orEmpty(),
                bild = o["icon"].text().orEmpty(),
            )
        }
    }.getOrDefault(emptyList())

    // Eigene Positionen bei Polymarket, gelesen ueber die oeffentliche Adresse. Ohne Schluessel und ohne Anmeldung:
    // die App kann damit nichts kaufen, verkaufen oder abheben.
    data class EchtePosition(
        val titel: String,
        val ausgang: String,
        val anteile: Double,
        val einstand: Double,
        val kurs: Double,
        val wert: Double,
        val gewinn: Double,
        val ereignis: String,
        // Markt entschieden, Gewinn kann bei Polymarket eingeloest werden.
        val einloesbar: Boolean,
    )

    private val ADRESSE = Regex("^0x[0-9a-fA-F]{40}$")
    private val SCHLUESSEL = Regex("^(0x)?[0-9a-fA-F]{64}$")

    fun adresseGueltig(s: String): Boolean = ADRESSE.matches(s.trim())

    // Sieht nach privatem Schluessel oder Seed-Woertern aus. So etwas speichert die App nie.
    fun geheimnisVerdacht(s: String): Boolean {
        val t = s.trim()
        return SCHLUESSEL.matches(t) || t.split(' ', '\n', '\t').count { it.isNotBlank() } >= 12
    }

    // Adresse der Abfrage, null bei ungueltiger Adresse; so gelangt nichts anderes in die Anfrage.
    fun positionenAdresse(adresse: String): String? =
        if (adresseGueltig(adresse)) "https://data-api.polymarket.com/positions?user=${adresse.trim().lowercase()}&sizeThreshold=0.1&limit=200" else null

    fun positionenLesen(text: String): List<EchtePosition> = runCatching {
        json.parseToJsonElement(text).arr().orEmpty().mapNotNull { it.obj() }.mapNotNull { o ->
            val anteile = o["size"].zahl() ?: return@mapNotNull null
            EchtePosition(
                titel = o["title"].text().orEmpty(),
                ausgang = o["outcome"].text().orEmpty(),
                anteile = anteile,
                einstand = o["avgPrice"].zahl() ?: 0.0,
                kurs = o["curPrice"].zahl() ?: 0.0,
                wert = o["currentValue"].zahl() ?: 0.0,
                gewinn = o["cashPnl"].zahl() ?: 0.0,
                ereignis = o["eventSlug"].text().orEmpty(),
                einloesbar = o["redeemable"].wahr() == true,
            )
        }
    }.getOrDefault(emptyList())

    // Liste der Ereignisse (/events) oder Treffer der Suche (/public-search, Feld events).
    fun polyListeLesen(text: String): List<Ereignis> {
        val wurzel = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return emptyList()
        val liste = wurzel.arr() ?: wurzel.obj()?.get("events").arr() ?: return emptyList()
        return liste.mapNotNull { it.obj()?.let { e -> if (e["closed"].wahr() == true) null else polyEreignisLesen(e) } }
    }

    fun polyEreignisText(text: String): Ereignis? =
        runCatching { json.parseToJsonElement(text).obj() }.getOrNull()?.let { polyEreignisLesen(it, nurOffen = false) }

    fun polyMarktText(text: String): Markt? =
        runCatching { json.parseToJsonElement(text).obj() }.getOrNull()?.let { polyMarktLesen(it) }

    // Verlauf aus prices-history: Zeit in Millisekunden, Kurs 0 bis 1.
    fun polyVerlaufLesen(text: String): List<Pair<Long, Double>> {
        val h = runCatching { json.parseToJsonElement(text).obj()?.get("history").arr() }.getOrNull() ?: return emptyList()
        return h.mapNotNull { p ->
            val o = p.obj() ?: return@mapNotNull null
            val t = o["t"].zahl() ?: return@mapNotNull null
            val v = o["p"].zahl() ?: return@mapNotNull null
            (t * 1000).toLong() to v
        }
    }

    // Kalshi

    private fun kalshiPreis(bid: Double?, ask: Double?, last: Double?): Double = when {
        bid != null && ask != null && bid > 0 && ask > 0 && ask >= bid && ask - bid <= 0.1 -> (bid + ask) / 2
        last != null && last > 0 -> last
        bid != null && ask != null && ask > 0 -> (bid + ask) / 2
        else -> 0.0
    }

    private fun kalshiMarktAus(
        ticker: String, titel: String, frage: String, bid: Double?, ask: Double?, last: Double?, vorher: Double?,
        volumen: Double, ende: Long, status: String, ergebnis: String, serie: String, regeln: String, nein: String,
    ): Markt {
        val p = kalshiPreis(bid, ask, last)
        val kauf = ask?.takeIf { it > 0 } ?: innen(p + 0.01)
        val verkauf = bid?.takeIf { it > 0 } ?: innen(p - 0.01)
        val aufgeloest = ergebnis == "yes" || ergebnis == "no"
        return Markt(
            quelle = Quelle.KALSHI, id = ticker, titel = titel, frage = frage,
            seiten = listOf(Seite("Yes", p, kauf, verkauf), Seite(nein.ifBlank { "No" }, 1.0 - p, innen(1.0 - verkauf), innen(1.0 - kauf))),
            aenderung = if (vorher != null && vorher > 0 && last != null && last > 0) (last - vorher) * 100.0 else null,
            volumen = volumen, ende = ende,
            offen = status.isBlank() || status == "active" || status == "open" || status == "initialized",
            gewinner = when (ergebnis) { "yes" -> 0; "no" -> 1; else -> null },
            auszahlung = when (ergebnis) { "yes" -> 1.0; "no" -> 0.0; else -> null },
            serie = serie, regeln = regeln,
        )
    }

    // Ein Treffer der Kalshi-Suche (Trends, Kategorie oder Volltext).
    private fun kalshiTreffer(x: JsonObject): Ereignis? {
        val ticker = x["event_ticker"].text() ?: return null
        val serie = x["series_ticker"].text().orEmpty()
        val titel = x["event_title"].text().orEmpty().ifBlank { x["series_title"].text().orEmpty() }
        val maerkte = (x["markets"].arr() ?: return null).mapNotNull { it.obj() }.mapNotNull { m ->
            val t = m["ticker"].text() ?: return@mapNotNull null
            if (m["result"].text().orEmpty().isNotBlank()) return@mapNotNull null
            kalshiMarktAus(
                ticker = t,
                titel = m["yes_subtitle"].text().orEmpty().ifBlank { m["title"].text().orEmpty() }.ifBlank { titel },
                frage = m["title"].text().orEmpty().ifBlank { titel },
                bid = m["yes_bid_dollars"].zahl(), ask = m["yes_ask_dollars"].zahl(), last = m["last_price_dollars"].zahl(),
                vorher = m["previous_price_dollars"].zahl(), volumen = m["volume"].zahl() ?: 0.0,
                ende = zeit(m["close_ts"].text()), status = "", ergebnis = "", serie = serie, regeln = "", nein = m["no_subtitle"].text().orEmpty(),
            )
        }.sortedByDescending { it.chance }
        if (maerkte.isEmpty()) return null
        return Ereignis(
            quelle = Quelle.KALSHI, id = ticker, titel = titel,
            untertitel = x["event_subtitle"].text().orEmpty().takeIf { it != titel }.orEmpty(),
            kategorie = x["category"].text().orEmpty(), bild = kalshiBild(serie), beschreibung = "",
            volumen = x["total_volume"].zahl() ?: maerkte.sumOf { it.volumen },
            volumen24h = x["recent_volume"].zahl() ?: 0.0,
            ende = maerkte.filter { it.ende > 0 }.minOfOrNull { it.ende } ?: 0L,
            maerkte = maerkte, link = kalshiLink(serie), serie = serie,
        )
    }

    fun kalshiListeLesen(text: String): Seitenliste {
        val wurzel = runCatching { json.parseToJsonElement(text).obj() }.getOrNull() ?: return Seitenliste(emptyList(), null)
        val treffer = wurzel["current_page"].arr()?.mapNotNull { it.obj()?.let { x -> kalshiTreffer(x) } }.orEmpty()
        return Seitenliste(treffer, wurzel["next_cursor"].text()?.takeIf { it.isNotBlank() })
    }

    private fun kalshiV2Markt(m: JsonObject, serie: String, eventTitel: String): Markt? {
        val t = m["ticker"].text() ?: return null
        return kalshiMarktAus(
            ticker = t,
            titel = m["yes_sub_title"].text().orEmpty().ifBlank { m["title"].text().orEmpty() }.ifBlank { eventTitel },
            frage = m["title"].text().orEmpty().ifBlank { eventTitel },
            bid = m["yes_bid_dollars"].zahl(), ask = m["yes_ask_dollars"].zahl(), last = m["last_price_dollars"].zahl(),
            vorher = m["previous_price_dollars"].zahl(),
            volumen = m["volume_fp"].zahl() ?: m["volume"].zahl() ?: 0.0,
            ende = zeit(m["close_time"].text()),
            status = m["status"].text().orEmpty(), ergebnis = m["result"].text().orEmpty(),
            serie = serie.ifBlank { t.substringBefore('-') },
            regeln = listOfNotNull(m["rules_primary"].text(), m["rules_secondary"].text()).filter { it.isNotBlank() }.joinToString("\n\n"),
            nein = m["no_sub_title"].text().orEmpty(),
        )
    }

    // Ereignis mit allen Maerkten (/trade-api/v2/events/<ticker>?with_nested_markets=true).
    fun kalshiEreignisText(text: String): Ereignis? {
        val w = runCatching { json.parseToJsonElement(text).obj() }.getOrNull() ?: return null
        val e = w["event"].obj() ?: return null
        val ticker = e["event_ticker"].text() ?: return null
        val serie = e["series_ticker"].text().orEmpty()
        val titel = e["title"].text().orEmpty()
        val roh = (e["markets"].arr() ?: w["markets"].arr() ?: JsonArray(emptyList())).mapNotNull { it.obj() }
        val maerkte = roh.mapNotNull { kalshiV2Markt(it, serie, titel) }.sortedByDescending { it.chance }
        if (maerkte.isEmpty()) return null
        return Ereignis(
            quelle = Quelle.KALSHI, id = ticker, titel = titel, untertitel = e["sub_title"].text().orEmpty(),
            kategorie = e["category"].text().orEmpty(), bild = kalshiBild(serie),
            beschreibung = maerkte.firstOrNull()?.regeln.orEmpty(),
            volumen = maerkte.sumOf { it.volumen }, volumen24h = 0.0,
            ende = maerkte.filter { it.ende > 0 }.minOfOrNull { it.ende } ?: 0L,
            maerkte = maerkte, link = kalshiLink(serie), serie = serie,
        )
    }

    // Einzelner Markt (/trade-api/v2/markets/<ticker>), fuer Kurs und Aufloesung gemerkter Wetten.
    fun kalshiMarktText(text: String): Markt? {
        val m = runCatching { json.parseToJsonElement(text).obj()?.get("market").obj() }.getOrNull() ?: return null
        return kalshiV2Markt(m, "", m["title"].text().orEmpty())
    }

    // Kerzen (candlesticks): Schlusskurs, sonst Mitte aus Geld und Brief.
    fun kalshiVerlaufLesen(text: String): List<Pair<Long, Double>> {
        val k = runCatching { json.parseToJsonElement(text).obj()?.get("candlesticks").arr() }.getOrNull() ?: return emptyList()
        return k.mapNotNull { c ->
            val o = c.obj() ?: return@mapNotNull null
            val t = o["end_period_ts"].zahl() ?: return@mapNotNull null
            val preis = o["price"].obj()?.get("close_dollars").zahl()
            val bid = o["yes_bid"].obj()?.get("close_dollars").zahl()
            val ask = o["yes_ask"].obj()?.get("close_dollars").zahl()
            val v = preis?.takeIf { it > 0 } ?: if (bid != null && ask != null && ask > 0) (bid + ask) / 2 else null
            v?.let { (t * 1000).toLong() to it }
        }
    }
}
