package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

// Weltbank-API v2 (CC BY 4.0): je Kennzahl der juengste Wert jedes Landes (mrnev=1), Schluessel ISO alpha-3.
// Ein Abruf je Kennzahl liefert alle Laender (30 bis 65 KB). Gespeichert wird eine Woche.
object Weltbank {
    @Serializable
    data class Wert(val jahr: Int, val wert: Double)

    @Serializable
    data class Reihe(val kennung: String, val abgerufen: Long, val werte: Map<String, Wert>)

    const val GUELTIG_MS = 7 * 24 * 3_600_000L

    // quelle: 2 = World Development Indicators, 3 = Worldwide Governance Indicators
    fun adresse(kennung: String, quelle: Int = 2): String =
        "https://api.worldbank.org/v2/country/all/indicator/$kennung?format=json&per_page=20000&mrnev=1&source=$quelle"

    fun veraltet(r: Reihe?, jetzt: Long): Boolean = r == null || jetzt - r.abgerufen >= GUELTIG_MS || jetzt < r.abgerufen

    private val json = Json { ignoreUnknownKeys = true }

    // Antwort: [ {seite}, [ {countryiso3code, date, value}, ... ] ]. Aggregate (Regionen) haben ebenfalls einen Code,
    // sie bleiben drin; die Oberflaeche fragt nur Laender ab.
    fun lesen(kennung: String, text: String, jetzt: Long): Reihe? = runCatching {
        val wurzel = json.parseToJsonElement(text) as? JsonArray ?: return null
        val liste = wurzel.getOrNull(1) as? JsonArray ?: return null
        val aus = HashMap<String, Wert>()
        for (e in liste) {
            val o = e as? JsonObject ?: continue
            val code = o["countryiso3code"]?.jsonPrimitive?.contentOrNull?.takeIf { it.length == 3 } ?: continue
            val v = o["value"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.doubleOrNull ?: continue
            val j = o["date"]?.jsonPrimitive?.contentOrNull?.take(4)?.toIntOrNull() ?: continue
            val alt = aus[code]
            if (alt == null || alt.jahr < j) aus[code] = Wert(j, v)
        }
        if (aus.isEmpty()) null else Reihe(kennung, jetzt, aus)
    }.getOrNull()

    fun schreiben(r: Reihe): String = json.encodeToString(Reihe.serializer(), r)
    fun lesenGespeichert(text: String): Reihe? = runCatching { json.decodeFromString(Reihe.serializer(), text) }.getOrNull()
}
