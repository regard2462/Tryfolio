package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Sanktionen: Zahl sanktionierter Personen und Organisationen je Land (OpenSanctions, CC BY-NC 4.0, eine Woche gespeichert)
// und die gebuendelte Liste der Laenderregime der EU (sanctionsmap.eu) und der Laenderprogramme der USA (OFAC).
object Sanktionen {
    const val OS_ADRESSE = "https://data.opensanctions.org/datasets/latest/sanctions/statistics.json"
    const val OS_GUELTIG_MS = 7 * 24 * 3_600_000L

    // Schluessel: ISO alpha-2 in Grossbuchstaben
    @Serializable
    class OsStand(val abgerufen: Long, val laender: Map<String, Int>)

    // statistics.json: {"targets": {"countries": [{"code": "ru", "count": 23434, "label": "Russia"}, ...]}}
    fun osLesen(text: String, jetzt: Long): OsStand? = runCatching {
        val liste = Laenderrisiken.json.parseToJsonElement(text).jsonObject["targets"]?.jsonObject?.get("countries") as? JsonArray ?: return null
        val aus = HashMap<String, Int>()
        for (e in liste) {
            val o = e as? JsonObject ?: continue
            val code = o["code"]?.jsonPrimitive?.contentOrNull?.uppercase()?.takeIf { it.length == 2 } ?: continue
            val n = o["count"]?.jsonPrimitive?.intOrNull ?: continue
            if (n > 0) aus[code] = n
        }
        if (aus.isEmpty()) null else OsStand(jetzt, aus)
    }.getOrNull()

    class Regime(val de: String, val en: String, val bis: Int)

    // eu: Land (alpha-2) -> Regime; us: Land (alpha-2) -> Programmnamen laut OFAC; stand: JJJJ-MM-TT
    class Liste(val stand: String, val eu: Map<String, List<Regime>>, val us: Map<String, List<String>>)

    fun listeLesen(text: String): Liste? = runCatching {
        val w = Laenderrisiken.json.parseToJsonElement(text).jsonObject
        val eu = w["eu"]!!.jsonObject.mapValues { (_, v) ->
            v.jsonArray.map { r ->
                val a = r.jsonArray
                Regime(a[0].jsonPrimitive.content, a[1].jsonPrimitive.content, a[2].jsonPrimitive.intOrNull ?: 0)
            }
        }
        val us = w["us"]!!.jsonObject.mapValues { (_, v) -> v.jsonArray.map { it.jsonPrimitive.content } }
        Liste(w["stand"]!!.jsonPrimitive.content, eu, us)
    }.getOrNull()
}
