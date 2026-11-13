package de.troetenrepublik.app.market

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Konzernmutter eines Unternehmens aus dem Verzeichnis der Rechtstraeger-Kennungen (GLEIF, api.gleif.org, ohne Schluessel,
// CC0): ueber die ISIN zur LEI des Wertes, dann der Datensatz der obersten (sonst der direkten) Mutter. Meldet ein
// Unternehmen keine Mutter oder nennt es einen Ausnahmegrund, gibt es nichts zu zeigen.
object Gleif {
    const val BASIS = "https://api.gleif.org/api/v1"

    fun leiSuche(isin: String) = "$BASIS/lei-records?filter%5Bisin%5D=$isin"

    class Satz(val lei: String, val name: String, val land: String, val direkt: String?, val oberste: String?)

    @Serializable
    class Mutter(val lei: String, val name: String, val land: String)

    // mutter null: keine Mutter gemeldet; abgerufen in ms
    @Serializable
    class Eintrag(val abgerufen: Long = 0L, val isin: String = "", val mutter: Mutter? = null)

    @Serializable
    class Datei(val werte: Map<String, Eintrag> = emptyMap())

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    fun lesen(text: String): Datei? = runCatching { json.decodeFromString(Datei.serializer(), text) }.getOrNull()

    fun schreiben(d: Datei): String = json.encodeToString(Datei.serializer(), d)

    private fun satz(o: JsonObject): Satz? {
        val a = o["attributes"]?.jsonObject ?: return null
        val lei = a["lei"]?.jsonPrimitive?.contentOrNull ?: o["id"]?.jsonPrimitive?.contentOrNull ?: return null
        val e = a["entity"]?.jsonObject ?: return null
        val name = e["legalName"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull ?: return null
        val land = e["legalAddress"]?.jsonObject?.get("country")?.jsonPrimitive?.contentOrNull.orEmpty()
        val rel = o["relationships"]?.jsonObject
        fun link(k: String) = rel?.get(k)?.jsonObject?.get("links")?.jsonObject?.get("lei-record")?.jsonPrimitive?.contentOrNull
        return Satz(lei, name, land, link("direct-parent"), link("ultimate-parent"))
    }

    // Erster Datensatz einer Antwort: Liste (Suche) oder einzelner Datensatz (Mutter)
    fun satzLesen(text: String): Satz? = runCatching {
        val d: JsonElement = json.parseToJsonElement(text).jsonObject["data"] ?: return null
        val o = if (d is kotlinx.serialization.json.JsonArray) d.firstOrNull()?.jsonObject else d.jsonObject
        o?.let { satz(it) }
    }.getOrNull()

    // Mutter, die vom Wert selbst verschieden ist
    fun mutterVon(eigen: Satz, mutter: Satz?): Mutter? {
        if (mutter == null || mutter.lei == eigen.lei) return null
        if (SecBericht.nameKern(mutter.name) == SecBericht.nameKern(eigen.name)) return null
        return Mutter(mutter.lei, mutter.name, mutter.land)
    }
}
