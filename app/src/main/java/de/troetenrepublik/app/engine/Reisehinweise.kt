package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

// Reisehinweise: Stufen des US-Aussenministeriums (Travel Advisories) und, falls die Schnittstelle ohne Pruefung antwortet,
// die Reisewarnungen des Auswaertigen Amts. Die US-Eintraege tragen Landeskuerzel, die nicht durchgehend ISO oder FIPS sind
// (etwa SR fuer die Schweiz); deshalb wird ueber den Landesnamen im Titel zugeordnet.
object Reisehinweise {
    const val US_ADRESSE = "https://cadataapi.state.gov/api/TravelAdvisories"
    const val AA_ADRESSE = "https://www.auswaertiges-amt.de/opendata/travelwarning"
    // Uebersicht der Reise- und Sicherheitshinweise; ein stabiles Muster je Land gibt es nicht.
    const val AA_SEITE = "https://www.auswaertiges-amt.de/de/service/laender"
    const val US_GUELTIG_MS = 24 * 3_600_000L

    @Serializable
    class UsStand(val abgerufen: Long, val stufen: Map<String, Int>)

    private val STUFE = Regex("""Level\s+(\d)""")

    // Titel: "Iran - Level 4: Do Not Travel", "Mexico Travel Advisory - Level 2: ..."
    fun usLesen(text: String, jetzt: Long): UsStand? = runCatching {
        val liste = Laenderrisiken.json.parseToJsonElement(text) as? JsonArray ?: return null
        val aus = HashMap<String, Int>()
        for (e in liste) {
            val titel = (e as? JsonObject)?.get("Title")?.jsonPrimitive?.contentOrNull ?: continue
            val stufe = STUFE.find(titel)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            val name = titel.substringBefore(" - Level").removeSuffix(" Travel Advisory")
            val k = Laenderrisiken.norm(name)
            if (k.isEmpty()) continue
            aus[k] = maxOf(aus[k] ?: 0, stufe)
        }
        if (aus.isEmpty()) null else UsStand(jetzt, aus)
    }.getOrNull()

    // Hoechste Stufe unter allen Schreibweisen des Landes (Palaestina: Gaza und Westjordanland).
    fun usStufe(s: UsStand, land: GlobusLand): Int? = Laenderrisiken.schluessel(land).mapNotNull { s.stufen[it] }.maxOrNull()

    @Serializable
    class AaStand(val abgerufen: Long, val warnung: Set<String>, val teilwarnung: Set<String>)

    // Antwort der OpenData-Schnittstelle: {"response": {"contentList": [...], "<id>": {"countryCode": "AF", "warning": true,
    // "partialWarning": false, ...}}}. Eine Pruefungsseite (HTML) ergibt null.
    fun aaLesen(text: String, jetzt: Long): AaStand? = runCatching {
        val antwort = (Laenderrisiken.json.parseToJsonElement(text) as? JsonObject)?.get("response") as? JsonObject ?: return null
        val voll = HashSet<String>()
        val teil = HashSet<String>()
        var gesehen = false
        for ((_, v) in antwort) {
            val o = v as? JsonObject ?: continue
            val code = (o["countryCode"] as? JsonPrimitive)?.contentOrNull?.uppercase() ?: continue
            gesehen = true
            if ((o["warning"] as? JsonPrimitive)?.booleanOrNull == true) voll += code
            else if ((o["partialWarning"] as? JsonPrimitive)?.booleanOrNull == true) teil += code
        }
        if (gesehen) AaStand(jetzt, voll, teil) else null
    }.getOrNull()
}
