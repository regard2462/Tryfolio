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

    // [Globus Gesamtbild 0.73] Konflikte, die im Land stattfinden oder in denen das Land angegriffen wird. Steht ein Land nur als
    // eine von mehreren Regierungen auf der angreifenden Seite (Einsatz in einem fremden Land, etwa USA und Grossbritannien im
    // Jemen), bekommt es den Konflikt nicht zugeschrieben; die Daten fuehren ihn unter jedem beteiligten Land.
    fun imLand(s: Stand, land: GlobusLand): List<Konflikt> {
        val eigene = Laenderrisiken.schluessel(land).toSet()
        return fuer(s, land).filterNot { k ->
            val regierungen = regierungen(k.seiteA)
            regierungen.size >= 2 && regierungen.any { Laenderrisiken.norm(it) in eigene || Laenderrisiken.norm(it.substringBefore('(')) in eigene }
        }
    }

    // "Government of A, Government of B" ergibt [A, B]; Seiten ohne Regierung (Gruppen) ergeben eine leere Liste.
    private fun regierungen(seite: String): List<String> =
        if (!seite.startsWith("Government of ")) emptyList()
        else seite.removePrefix("Government of ").split(", Government of ").map { it.trim() }

    // "Government of X" wird zu "Regierung X" (de) bzw. bleibt (en); Kurzformen der Gruppen bleiben stehen.
    fun seite(text: String, deutsch: Boolean): String =
        if (deutsch) text.replace("Government of ", "Regierung ") else text

    // [Laenderblatt 0.70] Seite eines Konflikts in der App-Sprache und kurz: "Government of Russia (Soviet Union)" wird zu
    // "Russland" (Landesname der Karte), unbekannte Regierungen zu "Regierung X"; Gruppen bleiben stehen.
    fun seiteAnzeige(text: String, karte: GlobusKarte?, deutsch: Boolean): String {
        val rest = text.removePrefix("Government of ").trim()
        if (rest == text) return text
        val ohneKlammer = rest.substringBefore('(').trim()
        val gesucht = setOf(Laenderrisiken.norm(rest), Laenderrisiken.norm(ohneKlammer))
        val land = karte?.laender?.firstOrNull { l -> Laenderrisiken.schluessel(l).any { it in gesucht } }
        if (land != null) return land.name(deutsch)
        return (if (deutsch) "Regierung " else "") + ohneKlammer
    }
}
