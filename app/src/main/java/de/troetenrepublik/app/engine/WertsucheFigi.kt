package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

// ISIN und WKN ueber die freie Kennungsabbildung von OpenFIGI (ohne Anmeldung) auf Yahoo-Kuerzel abbilden [0.73].
// Yahoo findet die meisten ISIN selbst, aber keine WKN und manche ISIN nicht; hier wird nur gelesen und gerechnet.
object WertsucheFigi {

    // Eine Notierung aus der Antwort: Kuerzel der Boerse, deren Kennung (Bloomberg) und Art.
    data class Eintrag(val ticker: String, val boerse: String, val name: String, val art: String)

    // Koerper der Anfrage fuer eine Kennung; idTyp ist ID_ISIN oder ID_WERTPAPIER (WKN).
    fun anfrage(wkn: Boolean, kennung: String): String =
        "[{\"idType\":\"" + (if (wkn) "ID_WERTPAPIER" else "ID_ISIN") + "\",\"idValue\":\"" + kennung.filter { it.isLetterOrDigit() } + "\"}]"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // Notierungen der ersten Abfrage; leer bei Fehlern, Warnungen („No identifier found“) und unlesbarer Antwort.
    fun lesen(antwort: String): List<Eintrag> {
        val wurzel = runCatching { json.parseToJsonElement(antwort).jsonArray }.getOrNull() ?: return emptyList()
        val daten = (wurzel.firstOrNull() as? JsonObject)?.get("data") as? JsonArray ?: return emptyList()
        return daten.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            fun feld(k: String) = o[k]?.runCatching { jsonPrimitive.contentOrNull }?.getOrNull().orEmpty()
            val t = feld("ticker")
            val b = feld("exchCode")
            if (t.isEmpty() || b.isEmpty()) null else Eintrag(t, b, feld("name"), feld("securityType"))
        }
    }

    // Boersen (Bloomberg-Kennung) und der Zusatz des Yahoo-Kuerzels; US-Plaetze haben keinen.
    private val ZUSATZ = mapOf(
        "US" to "", "UN" to "", "UW" to "", "UQ" to "", "UA" to "", "UR" to "", "UP" to "", "UV" to "", "UF" to "",
        "GY" to ".DE", "GR" to ".DE", "GF" to ".F", "GS" to ".SG", "GM" to ".MU", "GH" to ".HM", "GD" to ".DU", "GB" to ".BE", "GI" to ".HA",
        "LN" to ".L", "FP" to ".PA", "NA" to ".AS", "BB" to ".BR", "IM" to ".MI", "SM" to ".MC", "PL" to ".LS",
        "SW" to ".SW", "SE" to ".SW", "AV" to ".VI", "SS" to ".ST", "DC" to ".CO", "NO" to ".OL", "FH" to ".HE", "ID" to ".IR",
        "PW" to ".WA", "CT" to ".TO", "CV" to ".V", "AU" to ".AX", "NZ" to ".NZ", "JT" to ".T", "HK" to ".HK", "KS" to ".KS",
        "TT" to ".TW", "SP" to ".SI", "IN" to ".NS", "IB" to ".BO", "BZ" to ".SA", "MM" to ".MX", "SJ" to ".JO", "TI" to ".IS",
    )

    // Yahoo-Kuerzel zu einem Eintrag, null fuer Boersen ohne Zuordnung (Quotierungen in Fremdwaehrung, Handelsplattformen).
    fun yahooKuerzel(e: Eintrag): String? {
        val z = ZUSATZ[e.boerse] ?: return null
        var t = e.ticker.trim().uppercase().replace('/', '-').replace(' ', '-')
        if (t.isEmpty()) return null
        if (z == ".HK" && t.all { it.isDigit() }) t = t.padStart(4, '0')
        return t + z
    }

    // Kandidaten fuer die Bestaetigung bei Yahoo: Heimatboerse des Nutzers zuerst, dann die Hauptboerse des ISIN-Landes,
    // dann US, dann die uebrigen in der Reihenfolge der Antwort. Ohne Doppelte, hoechstens max.
    fun kandidaten(eintraege: List<Eintrag>, kennung: String, land: String, max: Int = 5): List<String> {
        val heimat = Wertsuche.heimatZusatz(land)
        val haupt = if (kennung.length >= 2 && kennung[0].isLetter()) Wertsuche.heimatZusatz(kennung.substring(0, 2)) else null
        val alle = eintraege.mapNotNull { yahooKuerzel(it) }.distinct()
        fun rang(s: String): Int {
            val z = Wertsuche.zusatz(s)
            return when {
                heimat != null && z == heimat -> 0
                haupt != null && z == haupt -> 1
                z.isEmpty() -> 2
                else -> 3
            }
        }
        return alle.sortedBy { rang(it) }.take(max)
    }
}
