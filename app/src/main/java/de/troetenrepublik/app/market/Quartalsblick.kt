package de.troetenrepublik.app.market

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.ZoneOffset

// Erwartungen zu den naechsten Quartalszahlen aus Yahoo quoteSummary (5.97). Das Profil ruft die Module
// earningsTrend, earningsHistory und calendarEvents ohnehin ab; gelesen wird daraus quoteSummary.result[0].
object Quartalsblick {

    // Schaetzungen eines Werts fuer das laufende Quartal (earningsTrend, period "0q"); wachstum in Prozent
    // (Yahoo liefert Anteile, 0.12 = 12 %).
    @kotlinx.serialization.Serializable
    data class Spanne(val mittel: Double?, val tief: Double?, val hoch: Double?, val analysten: Int?, val vorjahr: Double?, val wachstum: Double?)

    // Ein gemeldetes Quartal (earningsHistory.history): ende als "yyyy-MM-dd", abweichung in Prozent.
    @kotlinx.serialization.Serializable
    data class Quartal(val ende: String, val ist: Double?, val erwartet: Double?, val abweichung: Double?)

    @kotlinx.serialization.Serializable
    data class Daten(
        val quartalsende: String?,        // trend 0q endDate (Text "yyyy-MM-dd")
        val gewinn: Spanne?,              // 0q earningsEstimate: avg, low, high, numberOfAnalysts, yearAgoEps, growth
        val umsatz: Spanne?,              // 0q revenueEstimate: avg, low, high, numberOfAnalysts, yearAgoRevenue, growth
        val gewinnWaehrung: String?,      // earningsEstimate.earningsCurrency, sonst null
        val umsatzWaehrung: String?,      // revenueEstimate.revenueCurrency, sonst null
        val gewinnJetzt: Double?,         // 0q epsTrend.current
        val gewinnVor7: Double?,          // 0q epsTrend.7daysAgo
        val gewinnVor30: Double?,         // 0q epsTrend.30daysAgo
        val gewinnVor90: Double?,         // 0q epsTrend.90daysAgo
        val hoch30: Int?,                 // 0q epsRevisions.upLast30days
        val runter30: Int?,               // 0q epsRevisions.downLast30days
        val verlauf: List<Quartal>,       // earningsHistory, nach ende aufsteigend (aelteste zuerst)
        val termin: Long?,                // calendarEvents.earnings.earningsDate[0] in ms
        val terminBis: Long?,             // earningsDate[1] in ms, falls vorhanden (Spanne bei unsicherem Termin)
        val geschaetzt: Boolean?,         // calendarEvents.earnings.isEarningsDateEstimate, sonst null
        val konferenz: Long?,             // calendarEvents.earnings.earningsCallDate[0] in ms
        val umsatzTief: Double?,          // calendarEvents.earnings.revenueLow (Ersatz, wenn earningsTrend fehlt)
        val umsatzHoch: Double?,          // revenueHigh
        val umsatzMittel: Double?,        // revenueAverage
        val gewinnTief: Double?,          // earningsLow
        val gewinnHoch: Double?,          // earningsHigh
        val gewinnMittel: Double?,        // earningsAverage
    )

    private val json = Json { ignoreUnknownKeys = true }

    // Zahl aus {"raw":1.23,"fmt":..} oder aus einer blanken Zahl; leeres Objekt, fehlender Schluessel, null und
    // NaN geben null.
    private fun zahl(e: JsonElement?): Double? = when (e) {
        null, is JsonNull -> null
        is JsonObject -> (e["raw"] as? JsonPrimitive)?.content?.toDoubleOrNull()
        is JsonPrimitive -> e.content.toDoubleOrNull()
        else -> null
    }?.takeIf { it.isFinite() }

    private fun ganze(e: JsonElement?): Int? = zahl(e)?.toInt()

    // Text oder null; steht er als Objekt da, zaehlt fmt.
    private fun text(e: JsonElement?): String? = when (e) {
        is JsonObject -> (e["fmt"] as? JsonPrimitive)?.content
        is JsonPrimitive -> if (e is JsonNull) null else e.content
        else -> null
    }?.takeIf { it.isNotEmpty() }

    // true/false oder null.
    private fun schalter(e: JsonElement?): Boolean? = when (text(e)?.lowercase()) {
        "true" -> true
        "false" -> false
        else -> null
    }

    // Yahoo nennt Termine in Sekunden, die App in Millisekunden.
    private fun ms(e: JsonElement?): Long? = zahl(e)?.let { (it * 1000.0).toLong() }

    // Quartalsende "yyyy-MM-dd" aus quarter: fmt, sonst raw in Sekunden als UTC-Datum.
    private fun quartalsende(e: JsonElement?): String? =
        (e as? JsonObject)?.get("fmt")?.let { text(it) } ?: zahl(e)?.let {
            Instant.ofEpochMilli((it * 1000.0).toLong()).atZone(ZoneOffset.UTC).toLocalDate().toString()
        }

    // Schaetzungen eines Werts (avg, low, high, numberOfAnalysts, growth); nur vorjahr unterscheidet sich
    // (yearAgoEps, yearAgoRevenue). Null, wenn alle Felder fehlen.
    private fun spanne(o: JsonObject?, vorjahr: String): Spanne? {
        val s = Spanne(
            zahl(o?.get("avg")),
            zahl(o?.get("low")),
            zahl(o?.get("high")),
            ganze(o?.get("numberOfAnalysts")),
            zahl(o?.get(vorjahr)),
            zahl(o?.get("growth"))?.times(100.0),
        )
        return if (s.mittel == null && s.tief == null && s.hoch == null && s.analysten == null && s.vorjahr == null && s.wachstum == null) null else s
    }

    // r ist quoteSummary.result[0]; null, wenn nichts Brauchbares drin steht.
    fun lesen(r: JsonObject): Daten? = runCatching {
        val q0 = ((r["earningsTrend"] as? JsonObject)?.get("trend") as? JsonArray)
            ?.mapNotNull { it as? JsonObject }?.firstOrNull { text(it["period"]) == "0q" }
        val gewinnSch = q0?.get("earningsEstimate") as? JsonObject
        val umsatzSch = q0?.get("revenueEstimate") as? JsonObject
        val epsTrend = q0?.get("epsTrend") as? JsonObject
        val epsRevision = q0?.get("epsRevisions") as? JsonObject
        val verlauf = ((r["earningsHistory"] as? JsonObject)?.get("history") as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
            .mapNotNull { h ->
                val ende = quartalsende(h["quarter"]) ?: return@mapNotNull null
                val ist = zahl(h["epsActual"])
                val erwartet = zahl(h["epsEstimate"])
                if (ist == null && erwartet == null) return@mapNotNull null
                // Gerechnet wie ueberall in der App; der fertige Wert der Quelle nur, wenn sich nichts rechnen laesst
                val abweichung = de.troetenrepublik.app.engine.Gewinnueberraschung.abweichung(ist, erwartet)
                    ?: zahl(h["surprisePercent"])?.times(100.0)
                Quartal(ende, ist, erwartet, abweichung)
            }
            .sortedBy { it.ende }
        val cal = (r["calendarEvents"] as? JsonObject)?.get("earnings") as? JsonObject
        val termine = (cal?.get("earningsDate") as? JsonArray).orEmpty()
        val d = Daten(
            quartalsende = q0?.let { text(it["endDate"]) },
            gewinn = spanne(gewinnSch, "yearAgoEps"),
            umsatz = spanne(umsatzSch, "yearAgoRevenue"),
            gewinnWaehrung = text(gewinnSch?.get("earningsCurrency")),
            umsatzWaehrung = text(umsatzSch?.get("revenueCurrency")),
            gewinnJetzt = zahl(epsTrend?.get("current")),
            gewinnVor7 = zahl(epsTrend?.get("7daysAgo")),
            gewinnVor30 = zahl(epsTrend?.get("30daysAgo")),
            gewinnVor90 = zahl(epsTrend?.get("90daysAgo")),
            hoch30 = ganze(epsRevision?.get("upLast30days")),
            runter30 = ganze(epsRevision?.get("downLast30days")),
            verlauf = verlauf,
            termin = termine.firstOrNull()?.let(::ms),
            terminBis = termine.getOrNull(1)?.let(::ms),
            geschaetzt = schalter(cal?.get("isEarningsDateEstimate")),
            konferenz = (cal?.get("earningsCallDate") as? JsonArray).orEmpty().firstOrNull()?.let(::ms),
            umsatzTief = zahl(cal?.get("revenueLow")),
            umsatzHoch = zahl(cal?.get("revenueHigh")),
            umsatzMittel = zahl(cal?.get("revenueAverage")),
            gewinnTief = zahl(cal?.get("earningsLow")),
            gewinnHoch = zahl(cal?.get("earningsHigh")),
            gewinnMittel = zahl(cal?.get("earningsAverage")),
        )
        if (leer(d)) null else d
    }.getOrNull()

    // Ganze Antwort {"quoteSummary":{"result":[r],...}}; null bei Fehler.
    fun lesenText(body: String): Daten? = runCatching {
        val o = json.parseToJsonElement(body) as? JsonObject ?: return null
        val r = ((o["quoteSummary"] as? JsonObject)?.get("result") as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        lesen(r)
    }.getOrNull()

    // Nichts Brauchbares: alle Felder null und der Verlauf leer.
    private fun leer(d: Daten): Boolean = d.quartalsende == null && d.gewinn == null && d.umsatz == null &&
        d.gewinnWaehrung == null && d.umsatzWaehrung == null && d.gewinnJetzt == null && d.gewinnVor7 == null &&
        d.gewinnVor30 == null && d.gewinnVor90 == null && d.hoch30 == null && d.runter30 == null &&
        d.verlauf.isEmpty() && d.termin == null && d.terminBis == null && d.geschaetzt == null && d.konferenz == null &&
        d.umsatzTief == null && d.umsatzHoch == null && d.umsatzMittel == null && d.gewinnTief == null &&
        d.gewinnHoch == null && d.gewinnMittel == null
}
