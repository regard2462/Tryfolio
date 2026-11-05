package de.troetenrepublik.app.market

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Weitere Meldungen bei der SEC (seit 5.91): Form 144 kuendigt den Verkauf von Aktien aus Beteiligungen der Fuehrung
// oder grosser Aktionaere an (meist kurz vor dem Verkauf), Schedule 13D und 13G melden Anteile ab 5 %. 13D heisst:
// der Halter will Einfluss nehmen (Aktivist), 13G: er haelt die Aktien nur als Anlage. Beide gibt es seit dem
// 18.12.2024 als XML. Abgerufen wird wie bei Form 4 nur mit Kontaktadresse unter Zugaenge, je geoeffneter Aktie.
object SecFormen {
    @Serializable
    data class Verkauf(
        // Kennung der Meldung, Person, Beziehung zum Emittenten.
        val a: String,
        val p: String,
        val rolle: String = "",
        // Stueck, geschaetzter Marktwert in Dollar, geplanter Tag und Tag der Meldung (JJJJ-MM-TT).
        val n: Double = 0.0,
        val wert: Double = 0.0,
        val tag: String = "",
        val gemeldet: String = "",
        val klasse: String = "",
    )

    @Serializable
    data class Halter(
        val a: String,
        val name: String,
        // Anteil in Prozent und Stueck laut Meldung.
        val anteil: Double = 0.0,
        val n: Double = 0.0,
        // 13D (aktiv) oder 13G (passiv), Aenderungsmeldung, Tag des Ereignisses und der Meldung.
        val aktiv: Boolean = false,
        val aenderung: Boolean = false,
        val tag: String = "",
        val gemeldet: String = "",
    )

    @Serializable
    data class Stand(
        val abgerufen: Long = 0L,
        val verkaeufe: List<Verkauf> = emptyList(),
        val halter: List<Halter> = emptyList(),
        val gelesen: List<String> = emptyList(),
    )

    @Serializable
    data class Datei(val werte: Map<String, Stand> = emptyMap())

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun lesen(text: String): Datei? = runCatching { json.decodeFromString(Datei.serializer(), text) }.getOrNull()

    fun schreiben(d: Datei): String = json.encodeToString(Datei.serializer(), d)

    // ------------------------------------------------------------ Liste der Meldungen eines Emittenten

    fun submissionsAdresse(cik: String) = "https://data.sec.gov/submissions/CIK" + cik.trimStart('0').padStart(10, '0') + ".json"

    // Zuordnung Kuerzel zu CIK fuer Werte ohne Form-4-Meldung im Stand.
    const val KUERZEL = "https://www.sec.gov/files/company_tickers.json"

    fun cikAusKuerzeln(text: String, symbol: String): String? =
        Regex("\"cik_str\":\\s*(\\d+),\\s*\"ticker\":\\s*\"${Regex.escape(symbol.uppercase().replace('-', '.'))}\"").find(text)?.groupValues?.get(1)
            ?: Regex("\"cik_str\":\\s*(\\d+),\\s*\"ticker\":\\s*\"${Regex.escape(symbol.uppercase())}\"").find(text)?.groupValues?.get(1)

    data class Eintrag(val accession: String, val form: String, val eingang: String, val datei: String)

    // Form 144 und Schedule 13D/13G im XML-Format aus der Liste data.sec.gov/submissions; die Hauptdatei steht dort
    // mit Darstellungsordner („xslF144X01/primary_doc.xml“), das XML selbst ohne.
    fun eintraege(text: String): List<Eintrag> = runCatching {
        val r = json.parseToJsonElement(text).jsonObject["filings"]?.jsonObject?.get("recent")?.jsonObject ?: return emptyList()
        fun spalte(k: String) = r[k]?.jsonArray?.map { it.jsonPrimitive.contentOrNull.orEmpty() }.orEmpty()
        val formen = spalte("form"); val nummern = spalte("accessionNumber"); val tage = spalte("filingDate"); val dateien = spalte("primaryDocument")
        formen.indices.mapNotNull { i ->
            val f = formen[i]
            if (f != "144" && f != "144/A" && !f.startsWith("SCHEDULE 13D") && !f.startsWith("SCHEDULE 13G")) return@mapNotNull null
            val d = dateien.getOrNull(i).orEmpty().substringAfterLast('/')
            if (!d.endsWith(".xml", true)) return@mapNotNull null
            Eintrag(nummern.getOrNull(i) ?: return@mapNotNull null, f, tage.getOrNull(i).orEmpty(), d)
        }
    }.getOrDefault(emptyList())

    // ------------------------------------------------------------ XML lesen

    // Inhalt des ersten Elements mit diesem Namen, auch mit Namensraum-Vorsatz („ns1:issuerName“).
    private fun feld(xml: String, name: String): String =
        Regex("<(?:[A-Za-z0-9]+:)?$name(?:\\s[^>]*)?>\\s*([^<]*?)\\s*</(?:[A-Za-z0-9]+:)?$name>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)
            ?.let { InsiderQuellen.entitaeten(it).replace(Regex("\\s+"), " ").trim() }.orEmpty()

    private fun bloecke(xml: String, name: String): List<String> =
        Regex("<(?:[A-Za-z0-9]+:)?$name(?:\\s[^>]*)?>(.*?)</(?:[A-Za-z0-9]+:)?$name>", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { it.groupValues[1] }.toList()

    // Datum MM/TT/JJJJ oder JJJJ-MM-TT als JJJJ-MM-TT.
    fun tag(s: String): String {
        Regex("(\\d{4})-(\\d{2})-(\\d{2})").find(s)?.let { return it.value }
        return Regex("(\\d{1,2})/(\\d{1,2})/(\\d{4})").find(s)?.destructured?.let { (m, d, j) -> "$j-${m.padStart(2, '0')}-${d.padStart(2, '0')}" }.orEmpty()
    }

    private fun zahl(s: String) = s.replace(",", "").replace("$", "").trim().toDoubleOrNull() ?: 0.0

    // Form 144: Person, fuer deren Konto verkauft wird, Beziehung, je Wertpapierklasse Stueck, Marktwert und
    // ungefaehrer Verkaufstag.
    fun form144(xml: String, accession: String, eingang: String = ""): List<Verkauf> {
        val person = feld(xml, "nameOfPersonForWhoseAccountTheSecuritiesAreToBeSold")
        if (person.isBlank()) return emptyList()
        val rolle = bloecke(xml, "relationshipToIssuer").map { InsiderQuellen.entitaeten(it).trim() }.filter { it.isNotBlank() }.distinct().joinToString(", ")
            .ifBlank { feld(xml, "relationshipsToIssuer") }
        val gemeldet = tag(feld(xml, "noticeDate")).ifBlank { eingang }
        return bloecke(xml, "securitiesInformation").mapNotNull { b ->
            val n = zahl(feld(b, "noOfUnitsSold"))
            if (n <= 0) return@mapNotNull null
            Verkauf(accession, person, rolle, n, zahl(feld(b, "aggregateMarketValue")), tag(feld(b, "approxSaleDate")), gemeldet, feld(b, "securitiesClassTitle"))
        }
    }

    // Schedule 13D/13G: je meldender Person Name, Anteil in Prozent und Stueck. Die Bloecke heissen je nach Formular
    // verschieden, deshalb wird ab jedem Namen bis zum naechsten gelesen.
    fun schedule13(xml: String, form: String, accession: String, eingang: String = ""): List<Halter> {
        val aktiv = form.contains("13D", true) || Regex("<(?:[A-Za-z0-9]+:)?submissionType>\\s*SCHEDULE 13D").containsMatchIn(xml)
        val aenderung = form.endsWith("/A") || Regex("<(?:[A-Za-z0-9]+:)?submissionType>[^<]*/A").containsMatchIn(xml) || feld(xml, "amendmentNo").isNotBlank()
        val tag = tag(feld(xml, "dateOfEvent").ifBlank { feld(xml, "eventDateRequiresFilingThisStatement") })
        val namen = Regex("<(?:[A-Za-z0-9]+:)?reportingPersonName>\\s*([^<]+?)\\s*</").findAll(xml).toList()
        return namen.mapIndexedNotNull { i, m ->
            val ende = namen.getOrNull(i + 1)?.range?.first ?: xml.length
            val teil = xml.substring(m.range.first, ende)
            val anteil = listOf("classPercent", "percentOfClass", "percentOwned").firstNotNullOfOrNull { feld(teil, it).replace("%", "").trim().toDoubleOrNull() } ?: return@mapIndexedNotNull null
            val n = listOf("aggregateAmountOwned", "reportingPersonBeneficiallyOwnedAggregateNumberOfShares", "aggregateAmount").firstNotNullOfOrNull { feld(teil, it).takeIf { s -> s.isNotBlank() } }?.let { zahl(it) } ?: 0.0
            Halter(accession, InsiderQuellen.entitaeten(m.groupValues[1]).trim(), anteil, n, aktiv, aenderung, tag, eingang)
        }
    }

    // ------------------------------------------------------------ Auswertung

    // Angekuendigte Verkaeufe der letzten tage Tage, neueste zuerst.
    fun angekuendigt(s: Stand, abTag: String): List<Verkauf> =
        s.verkaeufe.filter { it.gemeldet >= abTag || it.tag >= abTag }.distinctBy { "${it.a}|${it.klasse}|${it.n}" }
            .sortedWith(compareByDescending<Verkauf> { it.gemeldet }.thenByDescending { it.tag })

    // Je Halter die juengste Meldung; Halter unter 5 % (nach einem Verkauf) bleiben mit ihrem letzten Stand drin,
    // groesste zuerst.
    fun grossaktionaere(s: Stand): List<Halter> =
        s.halter.groupBy { it.name.lowercase().replace(Regex("[^a-z0-9]"), "") }
            .mapNotNull { (_, l) -> l.maxWithOrNull(compareBy<Halter> { it.gemeldet }.thenBy { it.tag }.thenBy { it.a }) }
            .sortedByDescending { it.anteil }
}
