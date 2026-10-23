package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Stimmungsbarometer auf einer Skala von 0 (extreme Angst) bis 100 (extreme Gier). Keine Empfehlung.
// Aktien: Fear & Greed Index von CNN Business, sieben Teilindikatoren, US-Markt.
// Krypto: Crypto Fear & Greed Index von alternative.me. Die Quellenangabe direkt bei den Daten ist Bedingung.
// Reddit: Stimmung auf r/wallstreetbets laut Tradestie, nach Kommentaren gewichtet. Mit eigenem Reddit-Zugang
// kommen die deutschen Subreddits nach Wortregeln dazu.
object Stimmungsbarometer {

    const val CNN = "https://production.dataviz.cnn.io/index/fearandgreed/graphdata"
    // CNN antwortet nur, wenn diese Seite als Herkunft der Anfrage angegeben ist.
    const val CNN_SEITE = "https://www.cnn.com/markets/fear-and-greed"
    const val KRYPTO = "https://api.alternative.me/fng/?limit=31"
    const val KRYPTO_SEITE = "https://alternative.me/crypto/fear-and-greed-index/"
    const val WSB = "https://tradestie.com/api/v1/apps/reddit"
    const val WSB_SEITE = "https://tradestie.com/apps/reddit/top-reddit-stocks/"

    // Einteilung wie bei CNN.
    enum class Stufe { EXTREME_ANGST, ANGST, NEUTRAL, GIER, EXTREME_GIER }

    fun stufe(wert: Double): Stufe = when {
        wert < 25 -> Stufe.EXTREME_ANGST
        wert < 45 -> Stufe.ANGST
        wert <= 55 -> Stufe.NEUTRAL
        wert <= 75 -> Stufe.GIER
        else -> Stufe.EXTREME_GIER
    }

    // Aus der Bezeichnung des Anbieters, etwa „extreme fear“. Null, wenn unbekannt.
    fun stufe(text: String): Stufe? = when (text.trim().lowercase()) {
        "extreme fear" -> Stufe.EXTREME_ANGST
        "fear" -> Stufe.ANGST
        "neutral" -> Stufe.NEUTRAL
        "greed" -> Stufe.GIER
        "extreme greed" -> Stufe.EXTREME_GIER
        else -> null
    }

    // Teilindikator des Aktienindex, etwa Momentum. schluessel wie bei CNN.
    data class Teil(val schluessel: String, val wert: Double, val stufe: Stufe)

    // Vergleichswerte: Vortag, vor einer Woche, einem Monat, einem Jahr, null wenn unbekannt.
    // Verlauf: Zeit in Millisekunden und Wert, aelteste zuerst.
    data class Barometer(
        val wert: Double,
        val stufe: Stufe,
        val vortag: Double? = null,
        val woche: Double? = null,
        val monat: Double? = null,
        val jahr: Double? = null,
        val verlauf: List<Pair<Long, Double>> = emptyList(),
        val teile: List<Teil> = emptyList(),
        val stand: Long = 0L,
    )

    data class WsbWert(val ticker: String, val steigend: Boolean, val punkte: Double, val kommentare: Int)

    // Anteil der Kommentare mit steigender Stimmung, 0 bis 100.
    data class RedditBarometer(val barometer: Barometer, val werte: List<WsbWert>)

    // Reihenfolge wie bei CNN.
    val TEILE = listOf(
        "market_momentum_sp500", "stock_price_strength", "stock_price_breadth", "put_call_options",
        "market_volatility_vix", "junk_bond_demand", "safe_haven_demand",
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
    private fun JsonElement?.zahl(): Double? = text()?.toDoubleOrNull()?.takeIf { it.isFinite() }

    // CNN liefert die Zeit in UTC (+00:00).
    private fun zeitIso(s: String?): Long = s?.let { BrokerExport.zeit(it) } ?: 0L

    // Null, wenn die Antwort keinen Indexwert enthaelt.
    fun cnnLesen(text: String): Barometer? = runCatching {
        val w = json.parseToJsonElement(text).obj() ?: return null
        val fg = w["fear_and_greed"].obj() ?: return null
        val wert = fg["score"].zahl() ?: return null
        val verlauf = (w["fear_and_greed_historical"].obj()?.get("data") as? JsonArray).orEmpty().mapNotNull { p ->
            val o = p.obj() ?: return@mapNotNull null
            val x = o["x"].zahl()?.toLong() ?: return@mapNotNull null
            val y = o["y"].zahl() ?: return@mapNotNull null
            x to y
        }.sortedBy { it.first }
        val teile = TEILE.mapNotNull { k ->
            val o = w[k].obj() ?: return@mapNotNull null
            val s = o["score"].zahl() ?: return@mapNotNull null
            Teil(k, s, o["rating"].text()?.let(::stufe) ?: stufe(s))
        }
        Barometer(
            wert = wert, stufe = fg["rating"].text()?.let(::stufe) ?: stufe(wert),
            vortag = fg["previous_close"].zahl(), woche = fg["previous_1_week"].zahl(), monat = fg["previous_1_month"].zahl(),
            jahr = fg["previous_1_year"].zahl(), verlauf = verlauf, teile = teile, stand = zeitIso(fg["timestamp"].text()),
        )
    }.getOrNull()

    // alternative.me liefert die neuesten zuerst, Zeit in Sekunden.
    fun kryptoLesen(text: String): Barometer? = runCatching {
        val daten = (json.parseToJsonElement(text).obj()?.get("data") as? JsonArray).orEmpty().mapNotNull { p ->
            val o = p.obj() ?: return@mapNotNull null
            val t = o["timestamp"].zahl()?.toLong() ?: return@mapNotNull null
            val v = o["value"].zahl() ?: return@mapNotNull null
            Triple(t * 1000, v, o["value_classification"].text())
        }.sortedByDescending { it.first }
        val jetzt = daten.firstOrNull() ?: return null
        Barometer(
            wert = jetzt.second, stufe = jetzt.third?.let(::stufe) ?: stufe(jetzt.second),
            vortag = daten.getOrNull(1)?.second, woche = daten.getOrNull(7)?.second, monat = daten.getOrNull(30)?.second,
            verlauf = daten.map { it.first to it.second }.reversed(), stand = jetzt.first,
        )
    }.getOrNull()

    // Die meistbesprochenen Werte auf r/wallstreetbets mit Stimmung.
    fun wsbLesen(text: String): List<WsbWert> = runCatching {
        (json.parseToJsonElement(text) as? JsonArray).orEmpty().mapNotNull { p ->
            val o = p.obj() ?: return@mapNotNull null
            val t = o["ticker"].text()?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val s = o["sentiment"].text()?.lowercase() ?: return@mapNotNull null
            WsbWert(t, s.startsWith("bull"), o["sentiment_score"].zahl() ?: 0.0, o["no_of_comments"].zahl()?.toInt() ?: 0)
        }
    }.getOrDefault(emptyList())

    // Anteil der Kommentare zu Werten mit steigender Stimmung, 0 bis 100. Null ohne Kommentare.
    fun wsbBarometer(werte: List<WsbWert>, jetzt: Long): RedditBarometer? {
        val alle = werte.sumOf { it.kommentare }
        if (alle <= 0) return null
        val wert = 100.0 * werte.filter { it.steigend }.sumOf { it.kommentare } / alle
        return RedditBarometer(Barometer(wert, stufe(wert), stand = jetzt), werte.sortedByDescending { it.kommentare })
    }

    // Anteil der Bullen unter Bullen und Baeren, 0 bis 100.
    data class AaiiBarometer(val barometer: Barometer, val woche: Fruehwarnung.Umfrage)

    // wochen: neueste zuerst.
    fun aaiiBarometer(wochen: List<Fruehwarnung.Umfrage>): AaiiBarometer? {
        fun anteil(u: Fruehwarnung.Umfrage) = (u.bullisch + u.baerisch).takeIf { it > 0 }?.let { 100.0 * u.bullisch / it }
        val neu = wochen.firstOrNull() ?: return null
        val wert = anteil(neu) ?: return null
        return AaiiBarometer(
            Barometer(wert, stufe(wert), vortag = wochen.getOrNull(1)?.let(::anteil), monat = wochen.getOrNull(4)?.let(::anteil),
                verlauf = wochen.mapNotNull { u -> anteil(u)?.let { u.zeit to it } }.reversed(), stand = neu.zeit),
            neu,
        )
    }

    // Aus deutschen Subreddits. Null bei weniger als mindestens Beitraegen mit Richtung.
    fun beitragsBarometer(s: RedditAuswertung.Stimmung, jetzt: Long, mindestens: Int = 5): Barometer? {
        if (s.mitRichtung < mindestens) return null
        val wert = 100.0 * s.steigend / s.mitRichtung
        return Barometer(wert, stufe(wert), stand = jetzt)
    }
}
