package de.troetenrepublik.app.market

import de.troetenrepublik.app.data.Candle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.ZoneOffset

// FINRA-Schnittstelle (5.94) nur mit eigenem Zugang des Nutzers: Die Bedingungen erlauben Abrufe ausschliesslich mit
// den Zugangsdaten, die FINRA ausgibt (API Terms 3.4). Ein kostenloses Konto mit Public Credential reicht. Die App
// holt damit ein Token (OAuth, Client Credentials) und fragt Leerverkaeufe und den Handel ueber alternative
// Handelssysteme (ATS, oft Dark Pools genannt) ab.
object Finra {

    const val TOKEN = "https://ews.fip.finra.org/fip/rest/ews/oauth2/access_token?grant_type=client_credentials"
    const val KONSOLE = "https://developer.finra.org/"
    const val LEER_SEITE = "https://www.finra.org/finra-data/browse-catalog/equity-short-interest"
    const val ATS_SEITE = "https://otctransparency.finra.org/otctransparency/"

    // Wochensummen je Wert ueber alle ATS und je ATS (Spezifikation „ATS Reporting API, File Download 0.4“)
    const val ATS_WERT = "ATS_W_SMBL"
    const val ATS_WERT_PLATZ = "ATS_W_SMBL_FIRM"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    fun tokenKopf(id: String, secret: String): Map<String, String> =
        mapOf("Authorization" to "Basic " + kotlin.io.encoding.Base64.encode("${id.trim()}:${secret.trim()}".encodeToByteArray()))

    // Token und Gueltigkeit in Sekunden.
    fun tokenLesen(text: String): Pair<String, Long>? = runCatching {
        val o = json.parseToJsonElement(text) as? JsonObject ?: return null
        val t = (o["access_token"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: return null
        t to ((o["expires_in"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 1_800L)
    }.getOrNull()

    // Nur Zeichen, die in US-Kuerzeln vorkommen, damit nichts anderes in die Anfrage gelangt.
    fun kuerzel(symbol: String): String = symbol.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' || it == '.' }.take(12)

    fun atsKoerper(symbol: String, typ: String, von: String, bis: String) =
        """{"limit":500,"compareFilters":[{"compareType":"EQUAL","fieldName":"issueSymbolIdentifier","fieldValue":"${kuerzel(symbol)}"},{"compareType":"EQUAL","fieldName":"summaryTypeCode","fieldValue":"$typ"}],"dateRangeFilters":[{"fieldName":"weekStartDate","startDate":"$von","endDate":"$bis"}]}"""

    data class AtsWoche(val woche: String, val aktien: Double, val abschluesse: Long)
    data class AtsPlatz(val name: String, val aktien: Double)

    // Felder ohne Ruecksicht auf Gross- und Kleinschreibung: Die Beispiele der Spezifikation schreiben sie mal so, mal so.
    private fun zeilen(text: String): List<Map<String, String>> = runCatching {
        (json.parseToJsonElement(text) as? JsonArray).orEmpty().mapNotNull { e ->
            (e as? JsonObject)?.entries?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.takeIf { !it.content.equals("null", true) || it.isString }?.let { k.lowercase() to it.content } }?.toMap()
        }
    }.getOrDefault(emptyList())

    fun atsWochenLesen(text: String): List<AtsWoche> = zeilen(text).mapNotNull { z ->
        val w = z["weekstartdate"]?.take(10) ?: return@mapNotNull null
        Triple(w, z["totalweeklysharequantity"]?.toDoubleOrNull() ?: return@mapNotNull null, z["totalweeklytradecount"]?.toDoubleOrNull()?.toLong() ?: 0L)
    }.groupBy { it.first }.map { (w, l) -> AtsWoche(w, l.sumOf { it.second }, l.sumOf { it.third }) }.sortedBy { it.woche }

    fun atsPlaetzeLesen(text: String, woche: String): List<AtsPlatz> = zeilen(text).filter { it["weekstartdate"]?.take(10) == woche }.mapNotNull { z ->
        val n = z["marketparticipantname"]?.trim()?.takeIf { it.isNotEmpty() && it != "null" } ?: return@mapNotNull null
        n to (z["totalweeklysharequantity"]?.toDoubleOrNull() ?: return@mapNotNull null)
    }.groupBy { it.first }.map { (n, l) -> AtsPlatz(n, l.sumOf { it.second }) }.sortedByDescending { it.aktien }

    // Anteil des ATS-Handels am gesamten Umsatz der Woche (Tageskerzen mit Umsatz, Montag bis Freitag). Nur Wochen,
    // fuer die an mindestens drei Tagen ein Umsatz vorliegt.
    fun anteile(wochen: List<AtsWoche>, kerzen: List<Candle>): List<Pair<String, Double>> {
        val umsatz = kerzen.filter { it.volume.isFinite() && it.volume > 0 }
            .groupBy { java.time.Instant.ofEpochMilli(it.t).atZone(ZoneOffset.UTC).toLocalDate().let { d -> d.minusDays((d.dayOfWeek.value - 1).toLong()) } }
        return wochen.mapNotNull { w ->
            val montag = runCatching { LocalDate.parse(w.woche) }.getOrNull() ?: return@mapNotNull null
            val tage = umsatz[montag] ?: return@mapNotNull null
            if (tage.size < 3) return@mapNotNull null
            val gesamt = tage.sumOf { it.volume }
            if (gesamt <= 0 || w.aktien > gesamt * 1.5) null else w.woche to w.aktien / gesamt
        }
    }
}
