package de.troetenrepublik.app.market

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

// Fahrzeugrueckrufe und Maengeluntersuchungen der US-Verkehrssicherheitsbehoerde NHTSA (api.nhtsa.gov, ohne
// Schluessel) bei Autoherstellern und Zulieferern. Die Listen der Behoerde lassen sich nicht nach Hersteller filtern:
// Die App holt die Rueckrufe der letzten 24 Monate und die juengsten Untersuchungen einmal fuer alle Hersteller und
// sucht darin. Rueckrufe tragen den Herstellernamen; Untersuchungen nur einen Text, in dem der Hersteller oder seine
// Marken genannt sind.
object Fahrzeugrueckrufe {
    const val BASIS = "https://api.nhtsa.gov"
    const val MAX_SEITEN_RUECKRUFE = 28
    const val SEITEN_UNTERSUCHUNGEN = 3

    private val branche = Regex("auto manufacturers|auto parts|recreational vehicles|farm & heavy", RegexOption.IGNORE_CASE)

    // Ob die Branche einer Aktie zur NHTSA passt (Yahoo-Branche); Haendler, Vermieter und Spediteure nicht.
    fun betroffen(industrie: String): Boolean = branche.containsMatchIn(industrie)

    data class Rueckruf(val nummer: String, val hersteller: String, val tag: String, val fahrzeuge: Long, val thema: String)

    // art: PE Vorpruefung, EA Detailanalyse, RQ Nachpruefung eines Rueckrufs, DP Antrag, AQ Pruefung der Meldepflicht.
    data class Untersuchung(val nummer: String, val art: String, val offen: Boolean, val tag: String, val thema: String, val text: String)

    data class Ergebnis(val rueckrufe: List<Rueckruf>, val untersuchungen: List<Untersuchung>) {
        val leer get() = rueckrufe.isEmpty() && untersuchungen.isEmpty()
        val fahrzeuge get() = rueckrufe.sumOf { it.fahrzeuge }
    }

    // Namen, unter denen die Behoerde den Hersteller fuehrt, und Marken fuer die Untersuchungstexte. Der Firmenname
    // selbst steht immer dabei.
    private val ALIAS = mapOf(
        "FORD MOTOR" to listOf("FORD", "LINCOLN"),
        "GENERAL MOTORS" to listOf("CHEVROLET", "GMC", "CADILLAC", "BUICK"),
        "STELLANTIS" to listOf("STELLANTIS", "FCA", "CHRYSLER", "JEEP", "FIAT"),
        "TOYOTA MOTOR" to listOf("TOYOTA", "LEXUS"),
        "HONDA MOTOR" to listOf("HONDA", "ACURA"),
        "NISSAN MOTOR" to listOf("NISSAN", "INFINITI"),
        "HYUNDAI MOTOR" to listOf("HYUNDAI", "GENESIS MOTOR"),
        "KIA" to listOf("KIA"),
        "SUBARU" to listOf("SUBARU"),
        "MAZDA MOTOR" to listOf("MAZDA"),
        "VOLKSWAGEN" to listOf("VOLKSWAGEN", "AUDI", "BENTLEY"),
        "BAYERISCHE MOTOREN WERKE" to listOf("BMW"),
        "BMW" to listOf("BMW"),
        "MERCEDES BENZ" to listOf("MERCEDES BENZ"),
        "DAIMLER TRUCK" to listOf("DAIMLER TRUCKS NORTH AMERICA", "FREIGHTLINER", "WESTERN STAR"),
        "TESLA" to listOf("TESLA"),
        "RIVIAN" to listOf("RIVIAN"),
        "LUCID" to listOf("LUCID"),
        "HARLEY DAVIDSON" to listOf("HARLEY DAVIDSON"),
        "PACCAR" to listOf("PACCAR", "KENWORTH", "PETERBILT"),
        "GOODYEAR TIRE AND RUBBER" to listOf("GOODYEAR"),
        "POLARIS" to listOf("POLARIS"),
        "THOR INDUSTRIES" to listOf("THOR MOTOR COACH", "KEYSTONE RV", "JAYCO"),
        "WINNEBAGO" to listOf("WINNEBAGO"),
    )

    fun namen(firma: String): List<String> {
        val n = Fda.normal(firma)
        if (n.isBlank()) return emptyList()
        val kurz = n.split(' ').take(2).joinToString(" ")
        val erster = n.substringBefore(' ')
        return (listOf(n) + (ALIAS[n] ?: ALIAS[kurz] ?: ALIAS[erster]).orEmpty()).distinct()
    }

    fun rueckrufAdresse(offset: Int) = "$BASIS/recalls?max=100&sort=recall573Date&order=desc&offset=$offset"

    fun untersuchungAdresse(offset: Int) = "$BASIS/investigations?max=100&sort=openDate&order=desc&offset=$offset"

    fun rueckrufSeite(nummer: String) = "https://www.nhtsa.gov/recalls?nhtsaId=$nummer"

    fun untersuchungSeite(nummer: String) = "https://www.nhtsa.gov/?nhtsaId=$nummer"

    private fun JsonObject.text(k: String) = this[k]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }.orEmpty()

    private fun ergebnisse(text: String) = runCatching { Json.parseToJsonElement(text).jsonObject["results"]?.jsonArray }.getOrNull()

    // Alle Rueckrufe einer Seite der Liste, die juengsten zuerst; null, wenn die Antwort keine Liste ist.
    fun rueckrufeLesen(text: String): List<Rueckruf>? = ergebnisse(text)?.map { e ->
        val o = e.jsonObject
        Rueckruf(
            o.text("nhtsaCampaignNumber"), o.text("manufacturerName"), o.text("recall573Date").take(10),
            o["potaff"]?.let { runCatching { it.jsonPrimitive.longOrNull }.getOrNull() } ?: 0L, o.text("subject").trim(),
        )
    }

    private fun ohneTags(s: String) = s.replace(Regex("<[^>]*>"), " ").replace("&nbsp;", " ").replace("&amp;", "&").replace(Regex("\\s+"), " ").trim()

    // Untersuchungen einer Seite; text enthaelt Titel und Beschreibung in der Schreibweise von RegisterName.text.
    fun untersuchungenLesen(text: String): List<Untersuchung>? = ergebnisse(text)?.map { e ->
        val o = e.jsonObject
        val nummer = o.text("nhtsaId")
        val thema = o.text("subject").trim()
        Untersuchung(
            nummer, nummer.takeWhile { it.isLetter() }, o.text("status") == "O", o.text("openDate").take(10), thema,
            RegisterName.text(thema + " " + ohneTags(o.text("description"))),
        )
    }

    // Rueckrufe des Herstellers seit dem Stichtag: Name gleich, als Wortanfang oder als Wort im Herstellernamen.
    fun rueckrufeFuer(alle: List<Rueckruf>, namen: List<String>, seit: String): List<Rueckruf> =
        alle.filter { it.tag >= seit && RegisterName.passt(it.hersteller, namen, enthaelt = true) }.distinctBy { it.nummer }

    // Offene Untersuchungen, deren Text den Hersteller oder eine Marke als ganzes Wort nennt.
    fun untersuchungenFuer(alle: List<Untersuchung>, namen: List<String>): List<Untersuchung> =
        alle.filter { u -> u.offen && namen.any { n -> n.isNotBlank() && u.text.contains(" $n ") } }.distinctBy { it.nummer }

    // Die groessten Rueckrufe nach betroffenen Fahrzeugen.
    fun groesste(r: List<Rueckruf>, max: Int = 6): List<Rueckruf> = r.sortedWith(compareByDescending<Rueckruf> { it.fahrzeuge }.thenByDescending { it.tag }).take(max)
}
