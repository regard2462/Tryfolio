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
