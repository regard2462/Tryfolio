package de.troetenrepublik.app.market

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

// Behoerdendaten zu US-Firmen fuer die Seite einer Aktie.
// Mitgeliefert (tools/washington): Lobbying aus den LD-2-Meldungen beim Clerk of the House und Entlassungsmeldungen
// nach dem WARN Act aus dem WARN Feed (CC BY 4.0).
// Zur Laufzeit ohne Anmeldung: Regierungsauftraege von USAspending.gov; Leerverkaeufe von FINRA nur mit eigenem
// FINRA-Zugang (seit 5.94, siehe Finra.kt);
// Wahlkampfspenden von der FEC (gemeinsamer DEMO_KEY oder eigener Schluessel von api.data.gov).
// Patente nur mit eigenem Schluessel fuer das Open Data Portal des USPTO.
object Behoerden {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonElement?.arr(): JsonArray? = this as? JsonArray
    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
    private fun JsonElement?.zahl(): Double? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.let { it.doubleOrNull ?: it.content.replace(",", "").toDoubleOrNull() }

    // Lobbying (mitgeliefert)

    @Serializable
    data class Quartal(val j: Int, val q: Int, val b: Long, val f: Int = 0)

    // Thema (Kennung der LD-2-Meldung) oder Gesetz mit der Zahl der Meldungen, die es nennen.
    @Serializable
    data class Nennung(val c: String, val n: Int = 0)

    @Serializable
    data class Lobbying(
        val quartale: List<Quartal> = emptyList(),
        val themen: List<Nennung> = emptyList(),
        val gesetze: List<Nennung> = emptyList(),
        val beispiele: List<String> = emptyList(),
    ) {
        val letztesJahr: Long get() = quartale.takeLast(4).sumOf { it.b }
    }

    @Serializable
    data class LobbyingDatei(val format: Int, val stand: String = "", val firmen: Map<String, Lobbying> = emptyMap())

    fun lobbyingLesen(text: String): LobbyingDatei? = runCatching { json.decodeFromString(LobbyingDatei.serializer(), text) }.getOrNull()?.takeIf { it.format == 1 }

    // Englische Ordnungszahl: 1st, 2nd, 3rd, 4th, 11th bis 13th, 21st, 101st, 111th.
    fun ordinal(n: Int): String {
        val z = n % 100
        val s = if (z in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
        return "$n$s"
    }

    // Nummer des Kongresses zu einem Jahr: der 119. gilt 2025 und 2026, der 120. 2027 und 2028 usw.
    fun kongressNummer(jahr: Int): Int = (jahr - 1789) / 2 + 1

    // Gesetz „H.R. 1234“ oder „S. 56“ zur Seite auf congress.gov (119. Kongress fuer 2025 und 2026).
    fun gesetzAdresse(g: String, jahr: Int): String? {
        val m = Regex("^(H\\.R\\.|S\\.|H\\.Res\\.|S\\.Res\\.) (\\d+)$").find(g.trim()) ?: return null
        val art = when (m.groupValues[1]) { "H.R." -> "house-bill"; "S." -> "senate-bill"; "H.Res." -> "house-resolution"; else -> "senate-resolution" }
        return "https://www.congress.gov/bill/${ordinal(kongressNummer(jahr))}-congress/$art/${m.groupValues[2]}"
    }

    // Entlassungen (mitgeliefert)

    @Serializable
    // t: S Schliessung, A Abbau, leer ohne Angabe des Staates
    data class Entlassung(val d: String, val s: String = "", val n: Int = 0, val o: String = "", val w: String = "", val t: String = "")

    // Seite der Arbeitsbehoerde eines Bundesstaates mit den WARN-Meldungen; null, wo keine bekannt ist
    fun warnSeite(staat: String): String? = WARN_SEITEN[staat.uppercase()]

    private val WARN_SEITEN = mapOf(
        "CA" to "https://edd.ca.gov/en/jobs_and_training/Layoff_Services_WARN",
        "TX" to "https://www.twc.texas.gov/businesses/worker-adjustment-and-retraining-notification-act-warn-notices",
        "NY" to "https://dol.ny.gov/warn-notices",
        "WA" to "https://esd.wa.gov/about-employees/WARN",
        "NJ" to "https://www.nj.gov/labor/employer-services/warn/",
        "IL" to "https://www2.illinois.gov/dceo/WorkforceDevelopment/warn/Pages/default.aspx",
        "FL" to "https://floridajobs.org/workforce-resources/worker-adjustment-and-retraining-notification-(warn)",
        "OH" to "https://jfs.ohio.gov/warn/index.stm",
        "MI" to "https://www.michigan.gov/leo/bureaus-agencies/wd/data-public-notices/warn-notices",
        "OR" to "https://ccwd.hecc.oregon.gov/Layoff/WARN",
        "CT" to "https://dolpublicdocumentlibrary.ct.gov/CsblrCategory?prefix=%2Frapid_response%2Fwarn_documents",
        "DE" to "https://joblink.delaware.gov/search/warn_lookups/new",
        "CO" to "https://cdle.colorado.gov/employers/layoff-separations/layoff-warn-list",
        "TN" to "https://www.tn.gov/workforce/general-resources/major-publications0/major-publications-redirect/reports.html",
        "VA" to "https://www.vec.virginia.gov/warn-notices",
        "MO" to "https://jobs.mo.gov/warn/",
        "IA" to "https://workforce.iowa.gov/employers/business-resources/warn",
        "IN" to "https://www.in.gov/dwd/warn-notices/current-warn-notices/",
        "WI" to "https://dwd.wisconsin.gov/dislocatedworker/warn/",
        "GA" to "https://www.tcsg.edu/warn-public-view/",
        "MD" to "https://www.dllr.state.md.us/employment/warn.shtml",
    )

    @Serializable
    data class EntlassungDatei(val format: Int, val stand: String = "", val neueste: String = "", val firmen: Map<String, List<Entlassung>> = emptyMap())

    fun entlassungenLesen(text: String): EntlassungDatei? = runCatching { json.decodeFromString(EntlassungDatei.serializer(), text) }.getOrNull()?.takeIf { it.format == 1 }

    // Regierungsauftraege (USAspending.gov)

    const val USA = "https://api.usaspending.gov/api/v2"

    private fun filter(name: String, von: String, bis: String) =
        """{"recipient_search_text":[${Json.encodeToString(kotlinx.serialization.serializer<String>(), name)}],"award_type_codes":["A","B","C","D"],"time_period":[{"start_date":"$von","end_date":"$bis"}]}"""

    // Summen je Empfaenger in einem Zeitraum; die Suche trifft jeden Namen, der mit dem Wort beginnt, deshalb zaehlt
    // danach nur, was passtEmpfaenger bestaetigt.
    fun auftraegeEmpfaengerKoerper(name: String, von: String, bis: String) =
        """{"filters":${filter(name, von, bis)},"category":"recipient","limit":100,"page":1}"""

    fun auftraegeEmpfaengerLesen(text: String): List<Pair<String, Double>> = runCatching {
        json.parseToJsonElement(text).jsonObject["results"].arr().orEmpty().mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            (o["name"].text() ?: return@mapNotNull null) to (o["amount"].zahl() ?: 0.0)
        }
    }.getOrDefault(emptyList())

    private val EMPFAENGER_ZUSATZ = setOf("INC", "CORP", "CORPORATION", "CO", "COMPANY", "LLC", "LTD", "LP", "THE", "USA", "US", "HOLDINGS", "GROUP", "INCORPORATED")

    // Empfaenger gehoert zur Firma: gleicher Stamm oder Stamm mit nur Rechtsformen dahinter. Toechter unter eigenem
    // Namen fehlen damit, fremde Firmen mit aehnlichem Namen aber auch.
    fun passtEmpfaenger(name: String, stamm: String): Boolean {
        val s = stamm(name)
        if (s == stamm) return true
        if (!s.startsWith("$stamm ")) return false
        return s.removePrefix("$stamm ").split(" ").all { it in EMPFAENGER_ZUSATZ }
    }

    fun auftraegeListeKoerper(name: String, von: String, bis: String) =
        """{"filters":${filter(name, von, bis)},"fields":["Award ID","Recipient Name","Award Amount","Awarding Agency","Start Date","Description","generated_internal_id"],"sort":"Award Amount","order":"desc","limit":8,"page":1}"""

    // Zuschuesse (02 bis 05) und Kredite (07, 08) an einen Empfaenger (Verflechtungen): die Schnittstelle verlangt je Gruppe der
    // Vergabearten eine eigene Anfrage. Gelesen wird wie bei den Auftraegen (auftraegeListeLesen, passtEmpfaenger).
    val ZUSCHUSS_GRUPPEN = listOf(listOf("02", "03", "04", "05"), listOf("07", "08"))

    fun zuschussListeKoerper(name: String, codes: List<String>, von: String, bis: String) =
        """{"filters":{"recipient_search_text":[${Json.encodeToString(kotlinx.serialization.serializer<String>(), name)}],"award_type_codes":[${codes.joinToString(",") { "\"$it\"" }}],"time_period":[{"start_date":"$von","end_date":"$bis"}]},"fields":["Award ID","Recipient Name","Award Amount","Awarding Agency","Start Date","Description","generated_internal_id"],"sort":"Award Amount","order":"desc","limit":20,"page":1}"""

    data class Auftrag(val id: String, val empfaenger: String, val betrag: Double, val behoerde: String, val beginn: String, val beschreibung: String, val intern: String)

    fun auftraegeListeLesen(text: String): List<Auftrag> = runCatching {
        json.parseToJsonElement(text).jsonObject["results"].arr().orEmpty().mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            Auftrag(
                o["Award ID"].text().orEmpty(), o["Recipient Name"].text().orEmpty(), o["Award Amount"].zahl() ?: 0.0,
                o["Awarding Agency"].text().orEmpty(), o["Start Date"].text().orEmpty(), o["Description"].text().orEmpty(),
                o["generated_internal_id"].text().orEmpty(),
            )
        }
    }.getOrDefault(emptyList())

    fun auftragAdresse(intern: String) = "https://www.usaspending.gov/award/" + java.net.URLEncoder.encode(intern, "UTF-8")

    // Leerverkaeufe (FINRA)

    const val FINRA = "https://api.finra.org/data/group/otcMarket/name"

    fun leerVolumenKoerper(symbol: String, von: String, bis: String) =
        """{"limit":200,"compareFilters":[{"compareType":"EQUAL","fieldName":"securitiesInformationProcessorSymbolIdentifier","fieldValue":"$symbol"}],"dateRangeFilters":[{"fieldName":"tradeReportDate","startDate":"$von","endDate":"$bis"}]}"""

    fun leerBestandKoerper(symbol: String, von: String, bis: String) =
        """{"limit":100,"compareFilters":[{"compareType":"EQUAL","fieldName":"symbolCode","fieldValue":"$symbol"}],"dateRangeFilters":[{"fieldName":"settlementDate","startDate":"$von","endDate":"$bis"}]}"""

    // Ausserboerslich gemeldeter Umsatz eines Tags: davon leer verkauft und gesamt (ueber alle Meldestellen).
    data class LeerTag(val tag: String, val leer: Double, val gesamt: Double) {
        val anteil: Double get() = if (gesamt > 0) leer / gesamt else 0.0
    }

    fun leerVolumenLesen(text: String): List<LeerTag> = runCatching {
        json.parseToJsonElement(text).arr().orEmpty().mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val t = o["tradeReportDate"].text() ?: return@mapNotNull null
            Triple(t, o["shortParQuantity"].zahl() ?: 0.0, o["totalParQuantity"].zahl() ?: 0.0)
        }.groupBy { it.first }.map { (t, l) -> LeerTag(t, l.sumOf { it.second }, l.sumOf { it.third }) }.sortedBy { it.tag }
    }.getOrDefault(emptyList())

    // Leerverkaufsbestand zum Stichtag (zweimal im Monat): Aktien leer verkauft, Veraenderung, Tage zum Eindecken.
    // Seit 5.94 drei Jahre (bis 72 Stichtage) fuer den Verlauf.
    data class LeerBestand(val stichtag: String, val aktien: Long, val vorher: Long, val tageEindecken: Double?)

    fun leerBestandLesen(text: String): List<LeerBestand> = runCatching {
        json.parseToJsonElement(text).arr().orEmpty().mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            LeerBestand(
                o["settlementDate"].text() ?: return@mapNotNull null,
                o["currentShortPositionQuantity"].zahl()?.toLong() ?: return@mapNotNull null,
                o["previousShortPositionQuantity"].zahl()?.toLong() ?: 0L,
                o["daysToCoverQuantity"].zahl()?.takeIf { it < 999 },
            )
        }.sortedBy { it.stichtag }
    }.getOrDefault(emptyList())

    // Wahlkampfspenden (FEC)

    const val FEC = "https://api.open.fec.gov/v1"
    const val DEMO = "DEMO_KEY"

    fun pacSuche(stamm: String, schluessel: String) =
        "$FEC/committees/?q=${java.net.URLEncoder.encode(stamm, "UTF-8")}&committee_type=Q&organization_type=C&sort=-last_file_date&per_page=10&api_key=$schluessel"

    data class Pac(val id: String, val name: String, val firma: String)

    // Das PAC, dessen verbundene Firma zum Stamm passt; sonst das erste mit dem Stamm im Namen.
    fun pacLesen(text: String, passt: (String) -> Boolean): Pac? = runCatching {
        val l = json.parseToJsonElement(text).jsonObject["results"].arr().orEmpty().mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            Pac(o["committee_id"].text() ?: return@mapNotNull null, o["name"].text().orEmpty(), o["affiliated_committee_name"].text().orEmpty())
        }
        l.firstOrNull { it.firma.isNotBlank() && passt(it.firma) } ?: l.firstOrNull { passt(it.name) }
    }.getOrNull()

    fun pacSummen(id: String, zyklus: Int, schluessel: String) = "$FEC/committee/$id/totals/?cycle=$zyklus&api_key=$schluessel"

    data class PacSummen(val zyklus: Int, val einnahmen: Double, val anKandidaten: Double, val ausgaben: Double)

    fun pacSummenLesen(text: String): PacSummen? = runCatching {
        val o = json.parseToJsonElement(text).jsonObject["results"].arr()?.firstOrNull().obj() ?: return null
        PacSummen(
            o["cycle"].zahl()?.toInt() ?: 0, o["receipts"].zahl() ?: 0.0,
            o["fed_candidate_committee_contributions"].zahl() ?: 0.0, o["disbursements"].zahl() ?: 0.0,
        )
    }.getOrNull()

    fun pacEmpfaenger(id: String, zyklus: Int, schluessel: String) =
        "$FEC/schedules/schedule_b/by_recipient/?committee_id=$id&cycle=$zyklus&sort=-total&per_page=12&api_key=$schluessel"

    fun pacEmpfaengerLesen(text: String): List<Pair<String, Double>> = runCatching {
        json.parseToJsonElement(text).jsonObject["results"].arr().orEmpty().mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            (o["recipient_name"].text() ?: return@mapNotNull null) to (o["total"].zahl() ?: 0.0)
        }
    }.getOrDefault(emptyList())

    // Patente: Adresse des USPTO Open Data Portal (nur mit eigenem Schluessel), Abfragen in FirmaPatente.kt

    const val USPTO = "https://api.uspto.gov/api/v1/patent/applications/search"

    // Name einer Firma ohne Rechtsform und Klammern, fuer Suchen bei Behoerden.
    private val ZUSATZ = setOf("INC", "CORP", "CORPORATION", "CO", "COMPANY", "LLC", "LTD", "PLC", "HOLDINGS", "GROUP", "THE")

    fun stamm(name: String): String {
        val w = name.uppercase().replace(Regex("\\([^)]*\\)"), " ").replace(Regex("[.,]"), " ").trim().split(Regex("\\s+")).toMutableList()
        while (w.size > 1 && w.last() in ZUSATZ) w.removeAt(w.size - 1)
        if (w.size > 1 && w.first() == "THE") w.removeAt(0)
        return w.joinToString(" ")
    }
}
