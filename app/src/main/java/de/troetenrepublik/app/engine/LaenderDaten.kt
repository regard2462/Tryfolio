package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Landesdaten aus Wikidata (CC0), gebuendelt in assets/globus/laenderdaten.json und von Hand geprueft: Hauptstadt,
// Staatsoberhaupt, Regierungschef, Regierungsform, Waehrung, Mitgliedschaften. Je Feld ein Text oder [englisch, deutsch].
// Dazu wiki-wirtschaft.json: je Land die Titel der Wikipedia-Artikel "Wirtschaft von ..." und des Laenderartikels.
object LaenderDaten {
    data class Eintrag(
        val hauptstadt: String?, val waehrung: String?, val oberhaupt: String?, val regierungschef: String?,
        // Grundform der Regierungsform (siehe Strings), ohne den Zusatz Bundesstaat
        val form: String?, val bundesstaat: Boolean, val mitglieder: List<String>,
    )

    data class Datei(val stand: String, val laender: Map<String, Eintrag>)

    // Mitgliedschaften, die das Blatt kennt, in dieser Reihenfolge
    val ORGANISATIONEN = listOf("EU", "EURO", "NATO", "G7", "G20", "OPEC", "BRICS", "OECD", "ASEAN", "MERCOSUR", "GOLF", "AU")

    private val json = Json { ignoreUnknownKeys = true }

    private fun text(e: JsonElement?, de: Boolean): String? = when (e) {
        is JsonPrimitive -> e.contentOrNull
        is JsonArray -> (if (de) e.getOrNull(1) else e.getOrNull(0))?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: e.getOrNull(0)?.jsonPrimitive?.contentOrNull
        else -> null
    }?.takeIf { it.isNotBlank() }

    fun lesen(text: String, de: Boolean): Datei? = runCatching {
        val w = json.parseToJsonElement(text).jsonObject
        val aus = HashMap<String, Eintrag>()
        for ((a2, v) in w["l"]!!.jsonObject) {
            val o = v as? JsonObject ?: continue
            val f = o["f"]?.jsonPrimitive?.contentOrNull
            aus[a2] = Eintrag(
                text(o["h"], de), text(o["w"], de), text(o["s"], de), text(o["r"], de),
                f?.substringBefore('+'), f?.endsWith("+B") == true,
                o["m"]?.jsonPrimitive?.contentOrNull?.split(',')?.filter { it in ORGANISATIONEN }.orEmpty(),
            )
        }
        Datei(w["stand"]?.jsonPrimitive?.contentOrNull.orEmpty(), aus)
    }.getOrNull()

    // Wikipedia-Titel: Sprache und Titel des Wirtschaftsartikels (deutsch, sonst englisch) und des Laenderartikels.
    data class Artikel(val sprache: String, val titel: String)
    data class WikiTitel(val wirtschaft: Artikel?, val land: Artikel?)

    fun wikiTitel(text: String, a2: String, de: Boolean): WikiTitel? = runCatching {
        val a = json.parseToJsonElement(text).jsonObject["t"]!!.jsonObject[a2] as? JsonArray ?: return null
        fun s(i: Int) = a.getOrNull(i)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        fun wahl(d: String?, e: String?): Artikel? = when {
            de && d != null -> Artikel("de", d)
            e != null -> Artikel("en", e)
            d != null -> Artikel("de", d)
            else -> null
        }
        WikiTitel(wahl(s(0), s(1)), wahl(s(2), s(3)))
    }.getOrNull()

    // Zusammenfassung der Wikipedia-REST-Schnittstelle: Auszug und Adresse des Artikels.
    data class Auszug(val text: String, val adresse: String)

    fun auszug(antwort: String): Auszug? = runCatching {
        val o = json.parseToJsonElement(antwort).jsonObject
        val t = o["extract"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val url = o["content_urls"]?.jsonObject?.get("desktop")?.jsonObject?.get("page")?.jsonPrimitive?.contentOrNull.orEmpty()
        Auszug(t, url)
    }.getOrNull()

    fun auszugAdresse(sprache: String, titel: String): String =
        "https://$sprache.wikipedia.org/api/rest_v1/page/summary/" + java.net.URLEncoder.encode(titel.replace(' ', '_'), "UTF-8").replace("+", "%20")

    fun artikelAdresse(sprache: String, titel: String): String =
        "https://$sprache.wikipedia.org/wiki/" + java.net.URLEncoder.encode(titel.replace(' ', '_'), "UTF-8").replace("+", "%20").replace("%3A", ":").replace("%2C", ",")

    // Kuerzt am Satzende auf hoechstens max Zeichen. Ein Punkt nach Abkuerzung oder Zahl (z. B. "Mrd.", "5.") zaehlt nicht
    // als Satzende. Ist schon der erste Satz laenger, wird am letzten Wortende vor max gekuerzt.
    fun kuerzen(text: String, max: Int = 600): String {
        if (text.length <= max) return text
        var ende = -1
        var i = 0
        while (i < text.length && i < max) {
            val c = text[i]
            if ((c == '.' || c == '!' || c == '?') && i + 1 < text.length && text[i + 1] == ' ' && i + 2 < text.length && text[i + 2].isUpperCase()) {
                val wort = text.substring(0, i).substringAfterLast(' ')
                val abkuerzung = c == '.' && (wort.length <= 3 || wort.all { it.isDigit() } || wort.endsWith("Mrd") || wort.endsWith("Mio") || wort.endsWith("Bio"))
                if (!abkuerzung) ende = i
            }
            i++
        }
        if (ende > 0) return text.substring(0, ende + 1)
        val schnitt = text.lastIndexOf(' ', max - 1).takeIf { it > 0 } ?: (max - 1)
        return text.substring(0, schnitt).trimEnd(',', ';', ':', ' ') + "\u2026"
    }

    // Wikipedia-Auszuege bleiben eine Woche gespeichert.
    const val GUELTIG_MS = 7 * 24 * 3_600_000L
}
