package de.troetenrepublik.app.engine

import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Bewaffnete Konflikte nach dem UCDP/PRIO Armed Conflict Dataset (CC BY 4.0), gebuendelt in assets/globus/konflikte.json:
// {"jahr": 2025, "v": "26.1", "k": {"MMR": [["Government of Myanmar (Burma)", "KNU", 1], ...]}}. Schluessel ISO alpha-3.
object Konflikte {
    const val DATEI = "globus/konflikte.json"

    // stufe: 1 = 25 bis 999 Gefechtstote im Jahr, 2 = ab 1000
    class Konflikt(val seiteA: String, val seiteB: String, val stufe: Int)
    class Stand(val jahr: Int, val version: String, val nachLand: Map<String, List<Konflikt>>)

    fun lesen(text: String): Stand? = runCatching {
        val w = Laenderrisiken.json.parseToJsonElement(text).jsonObject
        val k = w["k"]!!.jsonObject.mapValues { (_, v) ->
            v.jsonArray.map { e ->
                val a = e.jsonArray
                Konflikt(a[0].jsonPrimitive.content, a[1].jsonPrimitive.content, a[2].jsonPrimitive.intOrNull ?: 1)
            }
        }
        Stand(w["jahr"]!!.jsonPrimitive.int, w["v"]?.jsonPrimitive?.content.orEmpty(), k)
    }.getOrNull()

    // Schwerere zuerst
    fun fuer(s: Stand, land: GlobusLand): List<Konflikt> = s.nachLand[land.a3].orEmpty().sortedByDescending { it.stufe }

    // "Government of X" wird zu "Regierung X" (de) bzw. bleibt (en); Kurzformen der Gruppen bleiben stehen.
    fun seite(text: String, deutsch: Boolean): String =
        if (deutsch) text.replace("Government of ", "Regierung ") else text
}
