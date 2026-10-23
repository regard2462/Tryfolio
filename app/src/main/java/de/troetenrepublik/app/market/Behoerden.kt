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
// Mitgeliefert (tools/washington): Lobbying aus den LD-2-Meldungen beim Clerk of the House (LDA.gov sperrt Abrufe von
// ausserhalb der USA) und Entlassungsmeldungen nach dem WARN Act aus dem WARN Feed (CC BY 4.0).
// Zur Laufzeit, alle ohne Anmeldung: Regierungsauftraege von USAspending.gov, Leerverkaeufe von FINRA,
// Wahlkampfspenden von der FEC (gemeinsamer DEMO_KEY oder eigener Schluessel von api.data.gov), Entlassungen in Texas
// von data.texas.gov. Patente nur mit eigenem Schluessel fuer das Open Data Portal des USPTO.
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

    // Gesetz „H.R. 1234“ oder „S. 56“ zur Seite auf congress.gov (119. Kongress fuer 2025 und 2026).
    fun gesetzAdresse(g: String, jahr: Int): String? {
        val m = Regex("^(H\\.R\\.|S\\.|H\\.Res\\.|S\\.Res\\.) (\\d+)$").find(g.trim()) ?: return null
        val art = when (m.groupValues[1]) { "H.R." -> "house-bill"; "S." -> "senate-bill"; "H.Res." -> "house-resolution"; else -> "senate-resolution" }
        val kongress = (jahr - 1789) / 2 + 1
        return "https://www.congress.gov/bill/${kongress}th-congress/$art/${m.groupValues[2]}"
    }

    // Entlassungen (mitgeliefert und Texas)

    @Serializable
    data class Entlassung(val d: String, val s: String = "", val n: Int = 0, val o: String = "", val w: String = "")

    @Serializable
    data class EntlassungDatei(val format: Int, val stand: String = "", val neueste: String = "", val firmen: Map<String, List<Entlassung>> = emptyMap())

    fun entlassungenLesen(text: String): EntlassungDatei? = runCatching { json.decodeFromString(EntlassungDatei.serializer(), text) }.getOrNull()?.takeIf { it.format == 1 }

    const val TEXAS = "https://data.texas.gov/resource/8w53-c4f6.json"

    // Name nur aus Buchstaben, Ziffern, Leerzeichen und wenigen Zeichen: er wird Teil einer SoQL-Abfrage.
    fun texasAdresse(stamm: String): String? {
        val s = stamm.uppercase().replace(Regex("[^A-Z0-9 &.-]"), "").trim().takeIf { it.length >= 3 } ?: return null
        val wo = "upper(job_site_name) like '%${s.replace("'", "")}%'"
        return "$TEXAS?\$where=" + java.net.URLEncoder.encode(wo, "UTF-8") + "&\$order=notice_date%20DESC&\$limit=20"
    }

    fun texasLesen(text: String): List<Entlassung> = runCatching {
        json.parseToJsonElement(text).arr().orEmpty().mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val d = o["notice_date"].text()?.take(10) ?: return@mapNotNull null
            Entlassung(d, "TX", o["total_layoff_number"].zahl()?.toInt() ?: 0, o["city_name"].text().orEmpty(), o["layoff_date"].text()?.take(10).orEmpty())
        }
    }.getOrDefault(emptyList())

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
        """{"limit":50,"compareFilters":[{"compareType":"EQUAL","fieldName":"symbolCode","fieldValue":"$symbol"}],"dateRangeFilters":[{"fieldName":"settlementDate","startDate":"$von","endDate":"$bis"}]}"""

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

    // Patente (USPTO Open Data Portal, nur mit eigenem Schluessel)

    const val USPTO = "https://api.uspto.gov/api/v1/patent/applications/search"

    fun patenteAdresse(name: String, von: String, bis: String): String {
        val q = "applicationMetaData.firstApplicantName:\"${name.replace("\"", "")}*\""
        return USPTO + "?q=" + java.net.URLEncoder.encode(q, "UTF-8") +
            "&rangeFilters=" + java.net.URLEncoder.encode("applicationMetaData.grantDate $von:$bis", "UTF-8") +
            "&sort=" + java.net.URLEncoder.encode("applicationMetaData.grantDate desc", "UTF-8") + "&limit=8"
    }

    data class Patent(val nummer: String, val titel: String, val erteilt: String)

    data class Patente(val anzahl: Int, val neueste: List<Patent>)

    fun patenteLesen(text: String): Patente? = runCatching {
        val o = json.parseToJsonElement(text).jsonObject
        val n = o["count"].zahl()?.toInt() ?: return null
        Patente(n, o["patentFileWrapperDataBag"].arr().orEmpty().mapNotNull { e ->
            val m = e.obj()?.get("applicationMetaData").obj() ?: return@mapNotNull null
            Patent(m["patentNumber"].text().orEmpty(), m["inventionTitle"].text().orEmpty(), m["grantDate"].text().orEmpty())
        })
    }.getOrNull()

    // Name einer Firma ohne Rechtsform und Klammern, fuer Suchen bei Behoerden.
    private val ZUSATZ = setOf("INC", "CORP", "CORPORATION", "CO", "COMPANY", "LLC", "LTD", "PLC", "HOLDINGS", "GROUP", "THE")

    fun stamm(name: String): String {
        val w = name.uppercase().replace(Regex("\\([^)]*\\)"), " ").replace(Regex("[.,]"), " ").trim().split(Regex("\\s+")).toMutableList()
        while (w.size > 1 && w.last() in ZUSATZ) w.removeAt(w.size - 1)
        if (w.size > 1 && w.first() == "THE") w.removeAt(0)
        return w.joinToString(" ")
    }
}
