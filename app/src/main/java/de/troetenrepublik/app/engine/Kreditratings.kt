package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Kreditratings aus der englischen Wikipedia, "List of countries by credit rating" (CC BY-SA 4.0): je Agentur eine Tabelle
// mit Land, Note, Ausblick und Datum. Gelesen wird der Wikitext der ganzen Seite in einem Abruf (rund 230 KB).
object Kreditratings {
    const val ADRESSE = "https://en.wikipedia.org/w/api.php?action=parse&page=List_of_countries_by_credit_rating&prop=wikitext&format=json&formatversion=2"
    const val GUELTIG_MS = 7 * 24 * 3_600_000L

    @Serializable
    class Note(val agentur: String, val note: String, val ausblick: String, val jahr: Int)

    // Schluessel: Landesname nach Laenderrisiken.norm
    @Serializable
    class Stand(val abgerufen: Long, val laender: Map<String, List<Note>>)

    // Antwort der MediaWiki-API: {"parse": {"wikitext": "..."}}
    fun wikitext(antwort: String): String? = runCatching {
        Laenderrisiken.json.parseToJsonElement(antwort).jsonObject["parse"]?.jsonObject?.get("wikitext")?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    private val AGENTUREN = listOf("Standard & Poor's" to "S&P", "Fitch" to "Fitch", "Moody's" to "Moody's", "DBRS Morningstar" to "DBRS Morningstar", "Scope Ratings" to "Scope")
    private val KOPF = Regex("""^(={2,3})\s*([^=\n]+?)\s*\1\s*$""", RegexOption.MULTILINE)
    private val FLAGGE = Regex("""\{\{\s*[Ff]lag\s*\|\s*([^}|]+)""")
    private val NOTE = Regex("""\{\{\s*Percentage bar\s*\|[^|]*\|\s*c=[^|]*\|(.+?)\|\s*color""", RegexOption.IGNORE_CASE)
    private val AUSBLICK = Regex("""\b(Stable|Positive|Negative|Developing|Evolving)\b""")
    private val JAHR = Regex("""\b((?:19|20)\d{2})\b""")

    fun lesen(wikitext: String, jetzt: Long): Stand? {
        val koepfe = KOPF.findAll(wikitext).toList()
        val aus = HashMap<String, MutableList<Note>>()
        for ((i, k) in koepfe.withIndex()) {
            val agentur = AGENTUREN.firstOrNull { it.first == k.groupValues[2] }?.second ?: continue
            val ende = koepfe.getOrNull(i + 1)?.range?.first ?: wikitext.length
            for (zeile in wikitext.substring(k.range.last + 1, ende).split("\n|-")) {
                val z = zeile.substringBefore("<ref")
                val land = FLAGGE.find(z)?.groupValues?.get(1)?.trim() ?: continue
                val n = NOTE.find(z) ?: continue
                val note = n.groupValues[1].replace(Regex("<[^>]+>"), "").trim().replace(Regex("""\s*\((high|low)\)"""), " ($1)")
                if (note.isEmpty()) continue
                val rest = z.substring(n.range.last + 1)
                val a = AUSBLICK.find(rest)
                val jahr = JAHR.find(if (a != null) rest.substring(a.range.last + 1) else rest)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                aus.getOrPut(Laenderrisiken.norm(land)) { ArrayList() } += Note(agentur, note, a?.value.orEmpty(), jahr)
            }
        }
        return if (aus.isEmpty()) null else Stand(jetzt, aus)
    }

    // Noten des Landes in der Reihenfolge der Agenturen.
    fun fuer(s: Stand, land: GlobusLand): List<Note> {
        val alle = Laenderrisiken.schluessel(land).flatMap { s.laender[it].orEmpty() }
        return AGENTUREN.mapNotNull { (_, kurz) -> alle.firstOrNull { it.agentur == kurz } }
    }
}
