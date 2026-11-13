package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Boersenwerte der Moskauer Boerse ueber die oeffentliche ISS-Schnittstelle (iss.moex.com, ohne Anmeldung, 15 Minuten
// verzoegert): Marktdaten des Handelsplatzes TQBR mit ISSUECAPITALIZATION in Rubel. Ein Abruf liefert alle Aktien.
object Moex {
    const val ADRESSE = "https://iss.moex.com/iss/engines/stock/markets/shares/boards/TQBR/securities.json?iss.meta=off&iss.only=marketdata&marketdata.columns=SECID,ISSUECAPITALIZATION"
    const val WAEHRUNG = "RUB"
    const val GUELTIG_MS = 24 * 3_600_000L

    private val json = Json { ignoreUnknownKeys = true }

    // [0.73] Tageskerzen eines Werts (Handelsplatz TQBR): Schlusskurs je Handelstag in Rubel, aelteste zuerst.
    // tag: Tag seit 1970 (Epochentag).
    class Kerze(val tag: Long, val schluss: Double)

    const val KERZEN_GUELTIG_MS = 3_600_000L

    // von im Format JJJJ-MM-TT; interval=24 sind Tageskerzen
    fun kerzenAdresse(kennung: String, von: String): String =
        "https://iss.moex.com/iss/engines/stock/markets/shares/boards/TQBR/securities/$kennung/candles.json?iss.meta=off&interval=24&from=$von"

    // Antwort: {"candles":{"columns":["open","close","high","low","value","volume","begin","end"],"data":[[..,"2025-11-10 00:00:00",..]]}}
    fun kerzen(text: String): List<Kerze> = runCatching {
        val k = json.parseToJsonElement(text).jsonObject["candles"]!!.jsonObject
        val spalten = k["columns"]!!.jsonArray.map { it.jsonPrimitive.content }
        val iSchluss = spalten.indexOf("close")
        val iBeginn = spalten.indexOf("begin")
        if (iSchluss < 0 || iBeginn < 0) return@runCatching emptyList()
        k["data"]!!.jsonArray.mapNotNull { z ->
            val a = z as? JsonArray ?: return@mapNotNull null
            val c = a.getOrNull(iSchluss)?.takeIf { it !is JsonNull }?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val t = a.getOrNull(iBeginn)?.takeIf { it !is JsonNull }?.jsonPrimitive?.content?.take(10) ?: return@mapNotNull null
            val tag = runCatching { java.time.LocalDate.parse(t).toEpochDay() }.getOrNull() ?: return@mapNotNull null
            if (c > 0) Kerze(tag, c) else null
        }.sortedBy { it.tag }
    }.getOrDefault(emptyList())

    // Antwort: {"marketdata":{"columns":["SECID","ISSUECAPITALIZATION"],"data":[["GAZP",3.1E12], ...]}}; Werte ohne Zahl fehlen.
    fun lesen(text: String): Map<String, Double> = runCatching {
        val daten = json.parseToJsonElement(text).jsonObject["marketdata"]!!.jsonObject["data"]!!.jsonArray
        val aus = HashMap<String, Double>()
        for (z in daten) {
            val a = z as? JsonArray ?: continue
            val id = a.getOrNull(0)?.jsonPrimitive?.content ?: continue
            val v = a.getOrNull(1)?.takeIf { it !is JsonNull }?.jsonPrimitive?.doubleOrNull ?: continue
            if (v > 0) aus[id] = v
        }
        aus
    }.getOrDefault(emptyMap())
}
