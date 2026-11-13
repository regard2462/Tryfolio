package de.troetenrepublik.app.market

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Produktrueckrufe der US-Verbraucherschutzbehoerde CPSC (saferproducts.gov, Recall-Schnittstelle, ohne Schluessel)
// bei Herstellern von Konsumgueter. Bei neueren Rueckrufen ist das Herstellerfeld oft leer; die Firma steht dann nur
// im Titel („Peloton Recalls ..."). Die App sucht deshalb im Herstellerfeld und im Titel und prueft jeden Treffer
// gegen den Firmennamen. Handelsketten (Feld Retailers) zaehlen nicht.
object Produktrueckrufe {
    const val BASIS = "https://www.saferproducts.gov/RestWebServices/Recall"

    private val branche = Regex(
        "household|personal products|furnishing|appliance|leisure|toy|recreational|apparel manufacturing|footwear|consumer electronics|tools|lawn|housewares",
        RegexOption.IGNORE_CASE,
    )

    // Ob die Branche einer Aktie zu Konsumguetern passt, die die CPSC betreut (Yahoo-Branche).
    fun betroffen(industrie: String): Boolean = branche.containsMatchIn(industrie)

    data class Rueckruf(val id: String, val tag: String, val titel: String, val produkt: String, val gefahr: String, val adresse: String)

    private val ALIAS = mapOf(
        "MATTEL" to listOf("FISHER PRICE"),
        "NEWELL BRANDS" to listOf("GRACO", "SUNBEAM", "CROCK POT", "YANKEE CANDLE"),
        "STANLEY BLACK AND DECKER" to listOf("DEWALT", "BLACK AND DECKER", "CRAFTSMAN"),
        "WHIRLPOOL" to listOf("KITCHENAID", "MAYTAG", "JENN AIR"),
    )

    fun namen(firma: String): List<String> = RegisterName.namen(firma, ALIAS)

    // Suchbegriffe fuer Hersteller und Titel: das erste Wort jedes Namens, bei kurzem ersten Wort die ersten zwei (ohne
    // „AND“, weil die Behoerde „&“ schreibt).
    fun suchbegriffe(namen: List<String>): List<String> = namen.map { n ->
        val w = n.split(' ')
        if (w[0].length >= 4) w[0] else w.filter { it != "AND" }.take(2).joinToString(" ")
    }.distinct()

    private fun begriff(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    fun herstellerAdresse(suche: String, seit: String) = "$BASIS?format=json&RecallDateStart=$seit&Manufacturer=${begriff(suche)}"

    fun titelAdresse(suche: String, seit: String) = "$BASIS?format=json&RecallDateStart=$seit&RecallTitle=${begriff(suche)}"

    private fun JsonObject.text(k: String) = this[k]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }.orEmpty()

    private fun namenAus(o: JsonObject, feld: String): List<String> =
        o[feld]?.let { runCatching { it.jsonArray.map { x -> x.jsonObject.text("Name") } }.getOrNull() }.orEmpty()

    // Firmenname ohne „, of <Ort>“; „a division of X“ bleibt stehen, RegisterName.passt erkennt die Mutter.
    fun bereinigt(name: String): String = name.substringBefore(", of ").substringBefore("\n").trim()

    private val TITEL_FIRMA = Regex("^(.*?)\\s+(?:Voluntarily\\s+)?(?:Recalls?|Recalled|Announces?)\\b")

    // Firmenname vor „Recalls“ im Titel; leer, wenn der Titel anders aufgebaut ist.
    fun titelFirma(titel: String): String = TITEL_FIRMA.find(titel.trim())?.groupValues?.get(1)?.trim().orEmpty()

    // Titel nennt die Firma: gleich oder die Firma im Titel ist der Anfang des Namens („Peloton“ fuer „Peloton Interactive“).
    private fun titelPasst(titel: String, namen: List<String>): Boolean {
        val t = Fda.normal(titelFirma(titel))
        if (t.length < 4) return false
        return namen.any { it == t || (t.length >= 5 && it.startsWith("$t ")) }
    }

    // Gefahr in Stichworten: der Teil des Titels nach „Due to“, sonst der erste Satz des Gefahrentexts.
    fun gefahr(titel: String, gefahrText: String): String {
        val i = titel.indexOf(" Due to ", ignoreCase = true)
        if (i >= 0) return titel.substring(i + 8).trim()
        return gefahrText.trim().substringBefore(". ").take(120)
    }

    // Rueckrufe aus einer Antwort, die zur Firma passen und seit dem Stichtag veroeffentlicht sind.
    fun lesen(text: String, namen: List<String>, seit: String): List<Rueckruf> {
        val liste = runCatching { Json.parseToJsonElement(text).jsonArray }.getOrNull() ?: return emptyList()
        return liste.mapNotNull { e ->
            val o = runCatching { e.jsonObject }.getOrNull() ?: return@mapNotNull null
            val tag = o.text("RecallDate").take(10)
            if (tag < seit) return@mapNotNull null
            val titel = o.text("Title")
            val firmen = (namenAus(o, "Manufacturers") + namenAus(o, "Importers")).map { bereinigt(it) }
            if (firmen.none { RegisterName.passt(it, namen) } && !titelPasst(titel, namen)) return@mapNotNull null
            val p = o["Products"]?.let { runCatching { it.jsonArray.firstOrNull()?.jsonObject }.getOrNull() }
            val gefahrText = o["Hazards"]?.let { runCatching { it.jsonArray.firstOrNull()?.jsonObject?.text("Name") }.getOrNull() }.orEmpty()
            Rueckruf(
                o.text("RecallID").ifBlank { o.text("RecallNumber") }, tag, titel, p?.text("Name").orEmpty().ifBlank { titel },
                gefahr(titel, gefahrText), o.text("URL"),
            )
        }
    }
}
