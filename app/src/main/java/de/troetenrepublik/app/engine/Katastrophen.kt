package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate

// Naturkatastrophen: Ereignisse mit Alarmstufe Orange oder Rot aus dem GeoJSON von GDACS (EU-Kommission JRC, UN OCHA).
// Gespeichert werden sechs Stunden.
object Katastrophen {
    const val ADRESSE = "https://www.gdacs.org/gdacsapi/api/events/geteventlist/EVENTS4APP"
    const val GUELTIG_MS = 6 * 3_600_000L
    const val TAGE = 30

    // typ: EQ, TC, FL, WF, VO, DR; rot: Alarmstufe Rot (sonst Orange); von und bis als JJJJ-MM-TT; laender: ISO alpha-3
    @Serializable
    class Ereignis(val typ: String, val rot: Boolean, val von: String, val bis: String, val laender: List<String>)

    @Serializable
    class Stand(val abgerufen: Long, val ereignisse: List<Ereignis>)

    fun lesen(text: String, jetzt: Long): Stand? = runCatching {
        val liste = Laenderrisiken.json.parseToJsonElement(text).jsonObject["features"] as? JsonArray ?: return null
        val aus = ArrayList<Ereignis>()
        for (f in liste) {
            val p = (f as? JsonObject)?.get("properties") as? JsonObject ?: continue
            val stufe = p["alertlevel"]?.jsonPrimitive?.contentOrNull ?: continue
            if (stufe != "Orange" && stufe != "Red") continue
            val typ = p["eventtype"]?.jsonPrimitive?.contentOrNull ?: continue
            val von = p["fromdate"]?.jsonPrimitive?.contentOrNull?.take(10) ?: continue
            val bis = p["todate"]?.jsonPrimitive?.contentOrNull?.take(10) ?: von
            val codes = LinkedHashSet<String>()
            p["iso3"]?.jsonPrimitive?.contentOrNull?.takeIf { it.length == 3 }?.let { codes += it }
            (p["affectedcountries"] as? JsonArray)?.forEach { c ->
                (c as? JsonObject)?.get("iso3")?.jsonPrimitive?.contentOrNull?.takeIf { it.length == 3 }?.let { codes += it }
            }
            if (codes.isNotEmpty()) aus += Ereignis(typ, stufe == "Red", von, bis, codes.toList())
        }
        // Eine leere Liste ist ein gueltiger Stand (keine Orange/Rot-Ereignisse), nur ein fremdes Format ergibt null.
        Stand(jetzt, aus)
    }.getOrNull()

    // Ereignisse des Landes, die in den letzten 30 Tagen noch liefen; Rot zuerst, dann neueste.
    fun fuer(s: Stand, a3: String, heute: LocalDate): List<Ereignis> {
        val grenze = heute.minusDays(TAGE.toLong()).toString()
        return s.ereignisse.filter { a3 in it.laender && it.bis >= grenze }
            .sortedWith(compareByDescending<Ereignis> { it.rot }.thenByDescending { it.von })
    }
}
