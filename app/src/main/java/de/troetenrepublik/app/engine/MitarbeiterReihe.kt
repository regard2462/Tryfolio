package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

// Zahl der Beschaeftigten einer Firma ueber die Jahre aus Wikidata (CC0, Eigenschaft P1128 mit Stichtag P585). Die SEC
// fuehrt die Zahl nicht als XBRL-Angabe, deshalb kommt die Reihe von dort. Ein Eintrag gilt nur, wenn sein Kuerzel an
// der Boerse (P414 mit P249) zum Symbol der Aktie passt; so landet keine Zahl einer gleichnamigen anderen Firma.
object MitarbeiterReihe {

    class Punkt(val jahr: Int, val anzahl: Long)

    private val json = Json { ignoreUnknownKeys = true }

    private fun norm(s: String) = s.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }

    // Kuerzel der Aktie ohne Handelsplatz-Endung ("SAP.DE" -> "SAP"); "BRK-B" und "BRK.B" werden gleich
    fun kuerzel(symbol: String): Set<String> {
        val s = symbol.trim()
        val ohne = if (s.contains('.') && s.substringAfterLast('.').length >= 2) s.substringBeforeLast('.') else s
        return setOf(norm(s), norm(ohne)).filter { it.isNotEmpty() }.toSet()
    }

    private fun liste(o: JsonObject?, k: String): List<JsonObject> = (o?.get(k) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

    private fun wert(snak: JsonObject?): JsonObject? = (snak?.get("datavalue") as? JsonObject)?.get("value") as? JsonObject

    private fun kuerzelDes(claims: JsonObject): List<String> {
        val aus = ArrayList<String>()
        for (st in liste(claims, "P414")) {
            val q = (st["qualifiers"] as? JsonObject)?.get("P249") as? JsonArray ?: continue
            q.forEach { s -> ((s as? JsonObject)?.get("datavalue") as? JsonObject)?.get("value")?.jsonPrimitive?.contentOrNull?.let { aus += norm(it) } }
        }
        for (st in liste(claims, "P249")) {
            ((st["mainsnak"] as? JsonObject)?.get("datavalue") as? JsonObject)?.get("value")?.jsonPrimitive?.contentOrNull?.let { aus += norm(it) }
        }
        return aus
    }

    // Antwort von wbgetentities (props=claims): Reihe des ersten Eintrags der Reihenfolge, dessen Kuerzel zum Symbol passt.
    // Je Jahr zaehlt die letzte Angabe; ohne mindestens zwei Jahre null.
    fun lesen(antwort: String, reihenfolge: List<String>, symbol: String): List<Punkt>? = runCatching {
        val e = json.parseToJsonElement(antwort) as JsonObject
        val eintraege = e["entities"] as JsonObject
        val gesucht = kuerzel(symbol)
        for (id in reihenfolge) {
            val claims = (eintraege[id] as? JsonObject)?.get("claims") as? JsonObject ?: continue
            if (kuerzelDes(claims).none { it in gesucht }) continue
            val punkte = HashMap<Int, Long>()
            for (st in liste(claims, "P1128")) {
                val menge = wert(st["mainsnak"] as? JsonObject)?.get("amount")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.toLong() ?: continue
                if (menge <= 0) continue
                val zeit = (st["qualifiers"] as? JsonObject)?.get("P585") as? JsonArray
                val z = wert(zeit?.firstOrNull() as? JsonObject)?.get("time")?.jsonPrimitive?.contentOrNull
                val jahr = z?.let { Regex("^[+-]?(\\d{4})").find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: continue
                punkte[jahr] = menge
            }
            val reihe = punkte.map { Punkt(it.key, it.value) }.sortedBy { it.jahr }
            return@runCatching reihe.takeIf { it.size >= 2 }
        }
        null
    }.getOrNull()
}
