package de.troetenrepublik.app.market

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

// Erteilte US-Patente einer Firma aus dem Open Data Portal des USPTO (mit eigenem Schluessel, siehe Zugaenge): Zahl je Jahr,
// Technikfelder aus den CPC-Klassen der letzten Patente, Patente je Milliarde Dollar Forschungsausgaben.
// Ablauf: 1. Anmeldernamen der Firma ueber die Facette firstApplicantName finden und mit dem Namensvergleich nur eindeutige
// behalten, 2. je Jahr die erteilten Patente dieser Namen zaehlen, 3. die CPC-Klassen der letzten 100 Patente auszaehlen.
// Gezaehlt wird, wer als erster Anmelder genannt ist; Patente unter fruheren Firmennamen fehlen.
object FirmaPatente {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private const val FELD = "applicationMetaData.firstApplicantName"
    private const val DATUM = "applicationMetaData.grantDate"
    private const val CPC = "applicationMetaData.cpcClassificationBag"

    // Zehn Kalenderjahre fuer den Verlauf, das laufende ist unvollstaendig und zaehlt nicht
    const val JAHRE = 10

    // Stichprobe fuer die Technikfelder
    const val STICHPROBE = 100

    // Hoechstens so viele Anmeldernamen je Firma
    private const val MAX_NAMEN = 6

    private fun u(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    private fun phrase(s: String) = "\"" + s.replace("\\", "").replace("\"", "") + "\""

    // Suchwoerter fuer die Facette: die ersten beiden Woerter des Firmennamens ohne Rechtsform
    fun suchwoerter(name: String): List<String> =
        Firmenname.bereinigt(name).split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() }.take(2)

    fun namenAdresse(suchwoerter: List<String>, vonJahr: Int, bisJahr: Int): String? {
        if (suchwoerter.isEmpty()) return null
        val q = "$FELD:(" + suchwoerter.joinToString(" AND ") + ")"
        return Behoerden.USPTO + "?q=" + u(q) + "&rangeFilters=" + u("$DATUM $vonJahr-01-01:$bisJahr-12-31") + "&facets=" + u(FELD) + "&limit=1"
    }

    // Namen aus der Facette (Wert und Zahl), groesste zuerst; die Antwort fuehrt die Facetten als Liste von Objekten je Feld
    fun facettenLesen(text: String): List<Pair<String, Int>> = runCatching {
        val wurzel = json.parseToJsonElement(text) as? JsonObject ?: return emptyList()
        val f = wurzel["facets"]
        val objekte: List<JsonObject> = when (f) {
            is JsonArray -> f.mapNotNull { it as? JsonObject }
            is JsonObject -> listOf(f)
            else -> emptyList()
        }
        objekte.flatMap { o ->
            o.entries.filter { it.key.endsWith("firstApplicantName") }.flatMap { (it.value as? JsonArray).orEmpty() }
        }.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val name = (o["value"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return@mapNotNull null
            name to ((o["count"] as? JsonPrimitive)?.doubleOrNull?.toInt() ?: 0)
        }.sortedByDescending { it.second }
    }.getOrDefault(emptyList())

    // Namen, die eindeutig zur Firma gehoeren
    fun eigeneNamen(facette: List<Pair<String, Int>>, firma: List<String>): List<String> =
        facette.filter { FirmaName.passt(it.first, firma) }.map { it.first }.take(MAX_NAMEN)

    private fun namenQuery(namen: List<String>) = "$FELD:(" + namen.joinToString(" OR ") { phrase(it) } + ")"

    fun jahrAdresse(namen: List<String>, jahr: Int): String =
        Behoerden.USPTO + "?q=" + u(namenQuery(namen)) + "&rangeFilters=" + u("$DATUM $jahr-01-01:$jahr-12-31") + "&limit=1"

    fun zahlLesen(text: String): Int? = runCatching {
        ((json.parseToJsonElement(text) as? JsonObject)?.get("count") as? JsonPrimitive)?.doubleOrNull?.toInt()
    }.getOrNull()

    fun felderAdresse(namen: List<String>, vonJahr: Int, bisJahr: Int): String =
        Behoerden.USPTO + "?q=" + u(namenQuery(namen)) + "&rangeFilters=" + u("$DATUM $vonJahr-01-01:$bisJahr-12-31") +
            "&sort=" + u("$DATUM desc") + "&fields=" + u(CPC) + "&limit=$STICHPROBE"

    // Klassen der Stichprobe: Zahl der Patente und je CPC-Unterklasse (vier Zeichen, etwa H01L) die Zahl der Patente, die sie tragen
    class Felder(val patente: Int, val klassen: List<Pair<String, Int>>)

    private fun JsonElement?.arr(): JsonArray? = this as? JsonArray

    fun felderLesen(text: String): Felder? = runCatching {
        val wurzel = json.parseToJsonElement(text) as? JsonObject ?: return null
        val liste = wurzel["patentFileWrapperDataBag"].arr().orEmpty()
        if (liste.isEmpty()) return null
        val zaehler = HashMap<String, Int>()
        var mit = 0
        for (p in liste) {
            val meta = (p as? JsonObject)?.get("applicationMetaData") as? JsonObject ?: continue
            val klassen = meta["cpcClassificationBag"].arr().orEmpty().mapNotNull { (it as? JsonPrimitive)?.content?.replace(" ", "")?.take(4) }
                .filter { it.length == 4 && it[0].isLetter() && it[1].isDigit() }.toSet()
            if (klassen.isEmpty()) continue
            mit++
            klassen.forEach { zaehler[it] = (zaehler[it] ?: 0) + 1 }
        }
        if (mit == 0) null else Felder(mit, zaehler.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { it.key to it.value })
    }.getOrNull()

    // Kernzahlen: das letzte volle Jahr und das davor
    class Kern(val jahr: Int, val anzahl: Int, val vorjahr: Int?, val aenderung: Double?)

    fun kern(jahre: List<Pair<Int, Int>>, letztesJahr: Int): Kern? {
        val a = jahre.firstOrNull { it.first == letztesJahr }?.second?.takeIf { it > 0 } ?: return null
        val v = jahre.firstOrNull { it.first == letztesJahr - 1 }?.second?.takeIf { it > 0 }
        return Kern(letztesJahr, a, v, v?.let { (a.toDouble() / it - 1) * 100 })
    }

    // Patente je Milliarde Dollar Forschungsausgaben; null ohne Ausgaben
    fun jeMilliarde(patente: Int, forschung: Double?): Double? = forschung?.takeIf { it > 0 }?.let { patente / (it / 1e9) }
}
