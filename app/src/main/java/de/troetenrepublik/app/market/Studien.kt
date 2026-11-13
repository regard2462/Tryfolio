package de.troetenrepublik.app.market

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Klinische Studien aus ClinicalTrials.gov (API v2, U.S. National Library of Medicine, ohne Schluessel) bei Pharma-,
// Biotech- und Medizintechnik-Aktien: laufende Studien nach Phase, die naechsten Abschlussdaten der spaeten Phasen und
// die zuletzt beendeten. Zugeordnet wird ueber den Sponsor der Studie (Hauptsponsor).
object Studien {
    const val BASIS = "https://clinicaltrials.gov/api/v2/studies"
    const val SEITE = "https://clinicaltrials.gov/study/"

    // phase: hoechste Phase der Studie (0 ohne Phase, 1 bis 4); status: Rohwert der Quelle.
    data class Studie(
        val nct: String, val titel: String, val phase: Int, val status: String, val abschluss: String,
        val mittel: String, val gebiet: String, val grund: String,
    )

    data class Ergebnis(val laufend: List<Studie>, val fertig: List<Studie>) {
        val leer get() = laufend.isEmpty() && fertig.isEmpty()
    }

    // Studiennamen bekannter Toechter und Namen, unter denen die Firma als Sponsor fuehrt.
    private val ALIAS = mapOf(
        "PFIZER" to listOf("SEAGEN"),
        "JOHNSON AND JOHNSON" to listOf("JANSSEN"),
        "ABBVIE" to listOf("ALLERGAN"),
        "BRISTOL MYERS SQUIBB" to listOf("CELGENE"),
        "ROCHE" to listOf("GENENTECH", "HOFFMANN LA ROCHE"),
        "SANOFI" to listOf("GENZYME"),
        "MODERNA" to listOf("MODERNATX"),
        "VIATRIS" to listOf("MYLAN"),
        "GLAXOSMITHKLINE" to listOf("GSK"),
        "GSK" to listOf("GLAXOSMITHKLINE"),
        "MERCK KGAA" to listOf("EMD SERONO"),
    )

    fun namen(firma: String): List<String> = RegisterName.namen(firma, ALIAS)

    fun passtSponsor(sponsor: String, namen: List<String>) = RegisterName.passt(sponsor, namen)

    private val LAUFEND = "RECRUITING%7CACTIVE_NOT_RECRUITING%7CNOT_YET_RECRUITING%7CENROLLING_BY_INVITATION"
    private const val FELDER = "NCTId,BriefTitle,Phase,OverallStatus,PrimaryCompletionDate,CompletionDate,LeadSponsorName,InterventionName,InterventionType,Condition,WhyStopped"

    private fun begriff(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    // Laufende, noch nicht begonnene und nicht mehr aufnehmende Studien des Sponsors (eine Seite mit bis zu 1000).
    fun laufendAdresse(suche: String) =
        "$BASIS?query.lead=${begriff(suche)}&filter.overallStatus=$LAUFEND&pageSize=1000&fields=$FELDER&format=json"

    // Abgeschlossene und abgebrochene Studien der Phasen 2 und 3 mit Enddatum im Zeitraum, die juengsten zuerst.
    fun fertigAdresse(suche: String, von: String, bis: String) =
        "$BASIS?query.lead=${begriff(suche)}&filter.overallStatus=COMPLETED%7CTERMINATED" +
            "&filter.advanced=AREA%5BCompletionDate%5DRANGE%5B$von,$bis%5D%20AND%20(AREA%5BPhase%5DPHASE2%20OR%20AREA%5BPhase%5DPHASE3)" +
            "&sort=CompletionDate:desc&pageSize=40&fields=$FELDER&format=json"

    private fun JsonObject.obj(k: String): JsonObject? = this[k]?.let { runCatching { it.jsonObject }.getOrNull() }
    private fun JsonObject.text(k: String) = this[k]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }.orEmpty()

    // Hoechste Phase aus den Phasen der Quelle ("PHASE1", "PHASE2" ...); EARLY_PHASE1 zaehlt als 1, NA als 0.
    fun hoechstePhase(phasen: List<String>): Int =
        phasen.maxOfOrNull { p ->
            when (p) {
                "EARLY_PHASE1", "PHASE1" -> 1; "PHASE2" -> 2; "PHASE3" -> 3; "PHASE4" -> 4; else -> 0
            }
        } ?: 0

    private val OHNE_WIRKSTOFF = Regex("(?i)^(placebo|standard of care|saline|sham|vehicle|best supportive|control|usual care)")

    // Das geprueft Mittel: erste Intervention, die kein Placebo oder Vergleich ist (Arzneimittel, Biologikum und
    // Gentherapie vor Geraet und Eingriff).
    fun mittel(interventionen: List<Pair<String, String>>): String {
        val echt = interventionen.filter { !OHNE_WIRKSTOFF.containsMatchIn(it.second.trim()) }
        val bevorzugt = echt.firstOrNull { it.first in setOf("DRUG", "BIOLOGICAL", "GENETIC", "COMBINATION_PRODUCT") }
        return (bevorzugt ?: echt.firstOrNull())?.second?.trim().orEmpty()
    }

    // Studien eines Sponsors aus der Antwort; nur Studien, deren Hauptsponsor zu den Namen passt.
    fun lesen(text: String, namen: List<String>): List<Studie> {
        val liste = runCatching { Json.parseToJsonElement(text).jsonObject["studies"]?.jsonArray }.getOrNull() ?: return emptyList()
        return liste.mapNotNull { e ->
            val p = runCatching { e.jsonObject.obj("protocolSection") }.getOrNull() ?: return@mapNotNull null
            val sponsor = p.obj("sponsorCollaboratorsModule")?.obj("leadSponsor")?.text("name").orEmpty()
            if (!passtSponsor(sponsor, namen)) return@mapNotNull null
            val id = p.obj("identificationModule")
            val status = p.obj("statusModule")
            val phasen = p.obj("designModule")?.get("phases")?.let { runCatching { it.jsonArray.map { x -> x.jsonPrimitive.content } }.getOrNull() }.orEmpty()
            val iv = p.obj("armsInterventionsModule")?.get("interventions")?.let { runCatching { it.jsonArray }.getOrNull() }.orEmpty()
                .map { x -> x.jsonObject.text("type") to x.jsonObject.text("name") }
            val gebiet = p.obj("conditionsModule")?.get("conditions")?.let { runCatching { it.jsonArray.map { x -> x.jsonPrimitive.content } }.getOrNull() }
                ?.firstOrNull().orEmpty()
            val nct = id?.text("nctId").orEmpty()
            if (nct.isBlank()) return@mapNotNull null
            Studie(
                nct, id?.text("briefTitle").orEmpty(), hoechstePhase(phasen), status?.text("overallStatus").orEmpty(),
                status?.obj("primaryCompletionDateStruct")?.text("date").orEmpty().ifBlank { status?.obj("completionDateStruct")?.text("date").orEmpty() },
                mittel(iv), gebiet, status?.text("whyStopped").orEmpty(),
            )
        }
    }

    // Anzahl laufender Studien je Phase 1, 2, 3 (hoechste Phase), dazu alle anderen (Phase 4 und ohne Phase).
    fun jePhase(laufend: List<Studie>): Triple<Int, Int, Int> =
        Triple(laufend.count { it.phase == 1 }, laufend.count { it.phase == 2 }, laufend.count { it.phase == 3 })

    // Datum der Quelle ("2027-12" oder "2027-12-31") als vergleichbarer Tag; Monate zaehlen als Monatsende.
    fun tag(datum: String): String = when (datum.length) {
        7 -> "$datum-31"
        else -> datum
    }

    // Die naechsten Abschlussdaten der spaeten Phasen (3, auch 2/3) ab heute, mit Mittel; ohne Spaetphase die naechsten
    // Abschlussdaten ueberhaupt (Medizintechnik hat meist keine Phase).
    fun naechste(laufend: List<Studie>, heute: String, max: Int = 6): List<Studie> {
        val kommend = laufend.filter { it.abschluss.isNotBlank() && tag(it.abschluss) >= heute }
        val spaet = kommend.filter { it.phase == 3 }
        return (if (spaet.isNotEmpty()) spaet else kommend).sortedBy { tag(it.abschluss) }.take(max)
    }

    // Zuletzt beendete Studien (abgeschlossen oder abgebrochen), die juengsten zuerst; nur schon vergangene Enddaten.
    fun beendet(fertig: List<Studie>, heute: String, max: Int = 6): List<Studie> =
        fertig.filter { it.abschluss.isNotBlank() && tag(it.abschluss) <= heute }
            .distinctBy { it.nct }.sortedByDescending { tag(it.abschluss) }.take(max)
}
