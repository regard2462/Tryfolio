package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Positionierung an den Terminmaerkten (seit 5.8): Commitments of Traders der U.S. Commodity Futures Trading Commission
// (CFTC), woechentlich, gemeinfrei, ohne Anmeldung. Gezeigt wird die Nettoposition grosser Spekulanten (Non-Commercials)
// in Prozent der offenen Kontrakte und wo sie im Vergleich zu den letzten drei Jahren steht (COT-Index 0 bis 100).
object Positionierung {
    // Schluessel und CFTC-Code des Kontrakts.
    val MAERKTE = listOf(
        "sp500" to "13874A", "nasdaq" to "209742", "gold" to "088691", "oel" to "067651",
        "euro" to "099741", "anleihen" to "043602", "bitcoin" to "133741",
    )

    const val QUELLE = "U.S. Commodity Futures Trading Commission, Commitments of Traders (publicreporting.cftc.gov)"

    fun adresse(code: String): String =
        "https://publicreporting.cftc.gov/resource/6dca-aqww.json?cftc_contract_market_code=$code" +
            "&%24order=report_date_as_yyyy_mm_dd%20DESC&%24limit=160" +
            "&%24select=report_date_as_yyyy_mm_dd,open_interest_all,noncomm_positions_long_all,noncomm_positions_short_all"

    data class Woche(val tag: Long, val offen: Double, val lang: Double, val kurz: Double) {
        // Nettoposition der Spekulanten in Prozent der offenen Kontrakte.
        val anteil: Double get() = if (offen > 0) (lang - kurz) / offen * 100 else 0.0
    }

    // Wochen aus der Antwort, aelteste zuerst.
    fun lesen(text: String): List<Woche> = runCatching {
        Json.parseToJsonElement(text).jsonArray.mapNotNull { e ->
            val o = e.jsonObject
            fun z(k: String) = o[k]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
            val tag = o["report_date_as_yyyy_mm_dd"]?.jsonPrimitive?.contentOrNull?.take(10)
                ?.let { runCatching { java.time.LocalDate.parse(it).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull() }
                ?: return@mapNotNull null
            Woche(tag, z("open_interest_all") ?: return@mapNotNull null, z("noncomm_positions_long_all") ?: 0.0, z("noncomm_positions_short_all") ?: 0.0)
        }.sortedBy { it.tag }
    }.getOrDefault(emptyList())

    data class Lage(
        val schluessel: String,
        val anteil: Double,
        val vorwoche: Double,
        // 0: so pessimistisch wie nie in drei Jahren, 100: so optimistisch wie nie.
        val index: Int,
        val verlauf: List<Pair<Long, Double>>,
        val stand: Long,
    )

    fun lage(schluessel: String, wochen: List<Woche>): Lage? {
        if (wochen.size < 10) return null
        val werte = wochen.map { it.anteil }
        val jetzt = werte.last()
        val min = werte.min()
        val max = werte.max()
        val index = if (max > min) Math.round((jetzt - min) / (max - min) * 100).toInt() else 50
        return Lage(schluessel, jetzt, werte[werte.size - 2], index, wochen.map { it.tag to it.anteil }, wochen.last().tag)
    }
}
