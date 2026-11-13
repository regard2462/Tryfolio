package de.troetenrepublik.app.market

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// [0.73] Angaben zu einer Firma aus Wikidata (CC0): Mitarbeiterzahl mit Jahr (P1128), Gruendung (P571), Sitz (P159),
// Eigentuemer (P127) und Muttergesellschaft (P749), Website (P856). Gesucht wird nach dem Namen; ein Treffer zaehlt nur, wenn
// der Eintrag wie eine Firma aussieht (keine Person, mindestens eine Firmenangabe). Fehlende Angaben bleiben leer.
object FirmaWikidata {
    @Serializable
    data class Angaben(
        val mitarbeiter: Long? = null, val mitarbeiterJahr: Int? = null, val gegruendet: Int? = null,
        val sitz: String = "", val eigentuemer: String = "", val mutter: String = "", val website: String = "",
        val kennung: String = "",
    ) {
        val leer: Boolean get() = mitarbeiter == null && gegruendet == null && sitz.isBlank() && eigentuemer.isBlank() && mutter.isBlank() && website.isBlank()
    }

    private val json = Json { ignoreUnknownKeys = true }

    // Rohangaben ohne Beschriftung: Verweise auf andere Eintraege stehen noch als Kennung
    class Roh(val kennung: String, val mitarbeiter: Long?, val jahr: Int?, val gegruendet: Int?, val sitz: String?, val eigentuemer: String?, val mutter: String?, val website: String)

    private val FIRMA_HINWEISE = listOf("P1128", "P452", "P414", "P159", "P749", "P127", "P2139", "P571")

    fun suchAdresse(name: String, sprache: String, enc: (String) -> String) =
        "https://www.wikidata.org/w/api.php?action=wbsearchentities&search=${enc(name)}&language=$sprache&uselang=$sprache&limit=5&format=json&type=item"

    fun eintraegeAdresse(ids: List<String>, props: String, sprachen: String? = null) =
        "https://www.wikidata.org/w/api.php?action=wbgetentities&ids=${ids.joinToString("|")}&props=$props" +
            (sprachen?.let { "&languages=$it" } ?: "") + "&format=json"

    // Kennungen der Suchtreffer in Trefferreihenfolge
    fun treffer(antwort: String): List<String> = runCatching {
        json.parseToJsonElement(antwort).jsonObject["search"]!!.jsonArray.mapNotNull { (it as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull }
    }.getOrDefault(emptyList())

    private fun wertDaten(anspruch: JsonObject?, prop: String): List<JsonObject> =
        ((anspruch?.get(prop) as? JsonArray).orEmpty()).mapNotNull { it as? JsonObject }

    private fun datenwert(c: JsonObject): JsonObject? = (c["mainsnak"] as? JsonObject)?.get("datavalue")?.let { (it as? JsonObject)?.get("value") as? JsonObject }

    private fun verweis(anspruch: JsonObject?, prop: String): String? =
        wertDaten(anspruch, prop).firstNotNullOfOrNull { datenwert(it)?.get("id")?.jsonPrimitive?.contentOrNull }

    // "+1998-09-04T00:00:00Z" -> 1998; nur ein Jahr gespeichert
    internal fun jahr(zeit: String?): Int? = zeit?.let { Regex("^[+-]?(\\d{1,4})").find(it)?.groupValues?.get(1)?.toIntOrNull() }

    // Antwort von wbgetentities (props=claims): erster Eintrag, der wie eine Firma aussieht
    fun ersteFirma(antwort: String, reihenfolge: List<String>): Roh? = runCatching {
        val eintraege = json.parseToJsonElement(antwort).jsonObject["entities"]!!.jsonObject
        for (id in reihenfolge) {
            val e = eintraege[id] as? JsonObject ?: continue
            val a = e["claims"] as? JsonObject ?: continue
            // Menschen sind keine Firmen
            if (wertDaten(a, "P31").any { datenwert(it)?.get("id")?.jsonPrimitive?.contentOrNull == "Q5" }) continue
            if (FIRMA_HINWEISE.none { a.containsKey(it) }) continue
            // Mitarbeiter: der Wert mit dem juengsten Stichtag (P585), sonst der letzte genannte
            val ma = wertDaten(a, "P1128").mapNotNull { c ->
                val v = datenwert(c)?.get("amount")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.toLong() ?: return@mapNotNull null
                val q = (c["qualifiers"] as? JsonObject)?.get("P585") as? JsonArray
                val z = q?.firstOrNull()?.let { (it as? JsonObject)?.get("datavalue") }?.let { ((it as? JsonObject)?.get("value") as? JsonObject)?.get("time")?.jsonPrimitive?.contentOrNull }
                Triple(v, jahr(z), z.orEmpty())
            }
            val neuester = ma.maxByOrNull { it.third } ?: ma.lastOrNull()
            val gegruendet = wertDaten(a, "P571").firstNotNullOfOrNull { jahr(datenwert(it)?.get("time")?.jsonPrimitive?.contentOrNull) }
            val web = wertDaten(a, "P856").firstNotNullOfOrNull { c ->
                ((c["mainsnak"] as? JsonObject)?.get("datavalue") as? JsonObject)?.get("value")?.jsonPrimitive?.contentOrNull
            }.orEmpty()
            return@runCatching Roh(id, neuester?.first, neuester?.second, gegruendet, verweis(a, "P159"), verweis(a, "P127"), verweis(a, "P749"), web)
        }
        null
    }.getOrNull()

    // Beschriftungen (props=labels) zu Kennungen, in der gewuenschten Sprache, sonst Englisch
    fun beschriftungen(antwort: String, sprache: String): Map<String, String> = runCatching {
        json.parseToJsonElement(antwort).jsonObject["entities"]!!.jsonObject.mapNotNull { (id, e) ->
            val l = (e as? JsonObject)?.get("labels") as? JsonObject ?: return@mapNotNull null
            val t = ((l[sprache] ?: l["en"]) as? JsonObject)?.get("value")?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            id to t
        }.toMap()
    }.getOrDefault(emptyMap())

    fun zusammen(r: Roh, namen: Map<String, String>) = Angaben(
        r.mitarbeiter, r.jahr, r.gegruendet, r.sitz?.let { namen[it] }.orEmpty(), r.eigentuemer?.let { namen[it] }.orEmpty(),
        r.mutter?.let { namen[it] }.orEmpty(), r.website, r.kennung,
    )

    // Ganzer Weg: Suche, Eintraege der Treffer, Beschriftungen der Verweise. get liefert den Antworttext oder null.
    suspend fun laden(name: String, sprache: String, enc: (String) -> String, get: suspend (String) -> String?): Angaben? {
        // Ohne Antwort (kein Netz) ist das ein Fehlschlag, kein "nichts gefunden": der Aufrufer speichert ihn nicht
        val ids = (get(suchAdresse(name, sprache, enc)) ?: error("Wikidata nicht erreichbar")).let { treffer(it) }.take(5)
        if (ids.isEmpty()) return null
        val e = get(eintraegeAdresse(ids, "claims")) ?: error("Wikidata nicht erreichbar")
        val roh = ersteFirma(e, ids) ?: return null
        val verweise = listOfNotNull(roh.sitz, roh.eigentuemer, roh.mutter).distinct()
        val namen = if (verweise.isEmpty()) emptyMap() else get(eintraegeAdresse(verweise, "labels", "$sprache|en"))?.let { beschriftungen(it, sprache) }.orEmpty()
        return zusammen(roh, namen).takeIf { !it.leer }
    }
}
