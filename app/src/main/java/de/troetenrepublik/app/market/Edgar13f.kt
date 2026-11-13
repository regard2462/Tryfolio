package de.troetenrepublik.app.market

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// 13F-Meldungen beliebiger Melder bei EDGAR [0.77]: Suche ueber die Volltextsuche (Formular 13F-HR), die beiden
// juengsten Meldungen aus der Liste bei data.sec.gov und die Bestandstabelle (infotable.xml) der Meldung. Alle Abrufe
// gehen an *.sec.gov und tragen die SEC-Kennung (SecInsider.kennung).
object Edgar13f {
    data class Melder(val name: String, val cik: String, val eingang: String)

    data class Meldung(val accession: String, val stichtag: String, val eingang: String)

    private val json = Json { ignoreUnknownKeys = true }

    // Treffer der Volltextsuche auf Formular 13F-HR im Zeitraum von ab bis bis (JJJJ-MM-TT).
    fun suchAdresse(name: String, ab: String, bis: String): String =
        "https://efts.sec.gov/LATEST/search-index?q=" + java.net.URLEncoder.encode("\"" + name.trim().replace("\"", "") + "\"", "UTF-8") +
            "&forms=13F-HR&dateRange=custom&startdt=$ab&enddt=$bis"

    private val CIK_ZUSATZ = Regex("\\s*\\(CIK\\s*\\d+\\)\\s*$")
    // Kuerzel der eigenen Aktie hinter dem Namen: "RENAISSANCERE HOLDINGS LTD  (RNR, RNR-PF)"
    private val KUERZEL_ZUSATZ = Regex("\\s{2,}\\([A-Z0-9][A-Z0-9, .\\-]*\\)\\s*$")

    // Je Melder (CIK) ein Treffer, in der Reihenfolge der Suche; der Name ohne Zusatz „(CIK …)“.
    private val KURZFORMEN = mapOf(
        "LLC" to "LLC", "LP" to "LP", "LLP" to "LLP", "PLC" to "plc", "INC" to "Inc", "INC." to "Inc.", "LTD" to "Ltd", "LTD." to "Ltd.",
        "CO" to "Co", "CO." to "Co.", "CORP" to "Corp", "CORP." to "Corp.", "SA" to "SA", "AG" to "AG", "NV" to "NV", "SE" to "SE",
        "II" to "II", "III" to "III", "IV" to "IV", "USA" to "USA", "US" to "US", "UK" to "UK", "OF" to "of", "AND" to "and", "THE" to "The",
        "JPMORGAN" to "JPMorgan", "BLACKROCK" to "BlackRock", "UBS" to "UBS", "AQR" to "AQR", "ING" to "ING", "AXA" to "AXA", "ST" to "St", "ST." to "St.",
    )
    private const val VOKALE = "AEIOUY"

    // EDGAR fuehrt viele Namen nur in Grossbuchstaben: "RENAISSANCE TECHNOLOGIES LLC" wird "Renaissance Technologies LLC".
    // Namen mit Kleinbuchstaben bleiben, wie sie sind.
    fun lesbar(name: String): String {
        if (name.any { it.isLowerCase() }) return name
        return name.split(' ').filter { it.isNotEmpty() }.mapIndexed { i, w ->
            val kurz = KURZFORMEN[w.trimEnd(',')]
            when {
                kurz != null -> (if (i == 0 && kurz[0].isLowerCase()) kurz.replaceFirstChar { it.uppercase() } else kurz) + w.drop(w.trimEnd(',').length)
                w.length <= 1 || w.any { it.isDigit() } || w.contains('&') -> w
                // Abkuerzungen ohne Vokal bleiben gross: FMR, HSBC, BNP
                w.trimEnd(',', '.').let { k -> k.all { it.isLetter() } && k.none { it in VOKALE } } -> w
                else -> w.split('-').joinToString("-") { t -> t.lowercase().replaceFirstChar { it.uppercase() } }
            }
        }.joinToString(" ")
    }

    // wort: Suchwort; Melder, deren Name es enthaelt, stehen vorn (die Volltextsuche findet auch Meldungen, die es nur nennen)
    fun treffer(suchJson: String, hoechstens: Int = 12, wort: String = ""): List<Melder> = runCatching {
        val hits = json.parseToJsonElement(suchJson).jsonObject["hits"]!!.jsonObject["hits"]!!.jsonArray
        val aus = LinkedHashMap<String, Melder>()
        for (h in hits) {
            val q = h.jsonObject["_source"]?.jsonObject ?: continue
            val cik = q["ciks"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content?.trimStart('0').orEmpty()
            val anzeige = q["display_names"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content.orEmpty()
            if (cik.isEmpty() || anzeige.isBlank()) continue
            val eingang = q["file_date"]?.jsonPrimitive?.content.orEmpty()
            val alt = aus[cik]
            if (alt == null || eingang > alt.eingang) aus[cik] = Melder(lesbar(anzeige.replace(CIK_ZUSATZ, "").replace(KUERZEL_ZUSATZ, "").trim()), cik, eingang)
        }
        val w = wort.trim()
        aus.values.sortedByDescending { w.isNotEmpty() && it.name.contains(w, ignoreCase = true) }.take(hoechstens)
    }.getOrDefault(emptyList())

    // 13F-HR (ohne Berichtigungen) aus der Liste der Meldungen, neuester Stichtag zuerst.
    fun meldungen(submissionsJson: String): List<Meldung> = runCatching {
        val r = json.parseToJsonElement(submissionsJson).jsonObject["filings"]!!.jsonObject["recent"]!!.jsonObject
        fun liste(n: String) = (r[n] as JsonArray).map { (it as JsonPrimitive).content }
        val form = liste("form")
        val nummer = liste("accessionNumber")
        val stichtag = liste("reportDate")
        val eingang = liste("filingDate")
        form.indices.filter { form[it] == "13F-HR" }.map { Meldung(nummer[it], stichtag[it], eingang[it]) }
            .sortedWith(compareByDescending<Meldung> { it.stichtag }.thenByDescending { it.eingang })
    }.getOrDefault(emptyList())

    fun name(submissionsJson: String): String? = runCatching {
        (json.parseToJsonElement(submissionsJson) as JsonObject)["name"]!!.jsonPrimitive.content.takeIf { it.isNotBlank() }
    }.getOrNull()

    // Bestandstabelle der Meldung im Verzeichnis (index.json): die XML-Datei, die nicht die Kopfdatei ist.
    fun tabellenDatei(indexJson: String): String? {
        val dateien = Regex("\"name\"\\s*:\\s*\"([^\"]+\\.xml)\"").findAll(indexJson).map { it.groupValues[1] }
            .filter { !it.contains("primary_doc", true) && !it.contains("index", true) }.toList()
        return dateien.firstOrNull { it.contains("info", true) || it.contains("table", true) } ?: dateien.firstOrNull()
    }

    private fun feld(x: String, tag: String): String =
        Regex("<(?:\\w+:)?$tag>\\s*([^<]*?)\\s*</(?:\\w+:)?$tag>").find(x)?.groupValues?.get(1)?.trim().orEmpty()

    private fun text(s: String) = s.replace("&amp;", "&").replace("&apos;", "'").replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").trim()

    // Positionen der Tabelle, je CUSIP und Art zusammengefasst. Bis zum Stichtag 2022-09-30 steht der Wert in Tausend
    // Dollar, danach in Dollar. kuerzel ordnet eine CUSIP einem Kuerzel zu (null: keine sichere Zuordnung).
    fun positionen(xml: String, stichtag: String, kuerzel: (String) -> String?): List<Investoren.Position> {
        val faktor = if (stichtag.isNotBlank() && stichtag <= "2022-09-30") 1000.0 else 1.0
        val aus = LinkedHashMap<String, Investoren.Position>()
        for (m in Regex("<(?:\\w+:)?infoTable>(.*?)</(?:\\w+:)?infoTable>", RegexOption.DOT_MATCHES_ALL).findAll(xml)) {
            val z = m.groupValues[1]
            val cusip = feld(z, "cusip").uppercase()
            val wert = (feld(z, "value").toDoubleOrNull() ?: continue) * faktor
            val stueck = feld(z, "sshPrnamt").toDoubleOrNull() ?: 0.0
            val einheit = feld(z, "sshPrnamtType")
            val pc = feld(z, "putCall").lowercase()
            val art = when { pc == "call" -> "call"; pc == "put" -> "put"; einheit.equals("PRN", true) -> "anleihe"; else -> "aktie" }
            val name = text(feld(z, "nameOfIssuer"))
            if (cusip.isEmpty() || name.isEmpty() || wert <= 0) continue
            val schluessel = cusip + art
            val alt = aus[schluessel]
            aus[schluessel] = if (alt == null) Investoren.Position(name, kuerzel(cusip), cusip, wert, stueck, art)
            else alt.copy(wert = alt.wert + wert, stueck = alt.stueck + stueck)
        }
        return aus.values.sortedByDescending { it.wert }
    }

    fun quartal(stichtag: String, eingang: String, positionen: List<Investoren.Position>): Investoren.Quartal =
        Investoren.Quartal(stichtag, eingang, positionen.filter { it.art == "aktie" || it.art == "anleihe" }.sumOf { it.wert }, positionen)

    // Kennung eines gesuchten Melders in der App; die Zahl ist die CIK.
    fun id(cik: String) = "x" + cik.trimStart('0')

    fun investor(name: String, cik: String, quartale: List<Investoren.Quartal>): Investoren.Investor =
        Investoren.Investor(id(cik), name, name, cik.padStart(10, '0'), "", "", quartale)

    // Frueher gespeicherte Namen in Grossbuchstaben werden beim Lesen lesbar
    fun lesen(text: String): List<Investoren.Investor> = Investoren.lesen(text).map { it.copy(name = lesbar(it.name), firma = lesbar(it.firma)) }

    fun schreiben(liste: List<Investoren.Investor>): String =
        json.encodeToString(Investoren.Datei.serializer(), Investoren.Datei("", liste))
}
