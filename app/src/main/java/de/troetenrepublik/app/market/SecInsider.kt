package de.troetenrepublik.app.market

import de.troetenrepublik.app.engine.InsiderTrend
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Insidergeschaefte an US-Boersen (seit 5.6): Meldungen nach Form 4 bei der SEC. Officers, Directors und Aktionaere
// ueber 10 % melden Kaeufe und Verkaeufe von Aktien ihres Unternehmens. Gezaehlt werden nur Kaeufe (P) und Verkaeufe (S)
// an der Boerse; je Meldung und Richtung zusammengefasst.
// Der App liegt ein Stand bei (tools/sec/holen.py aus den Form-345-Datensaetzen der SEC, assets/copytrading/sec.json).
// Neue Meldungen holt die App nur, wenn unter Zugaenge eine Kontaktadresse fuer die SEC eingetragen ist: die SEC
// liefert ihr Archiv nur an Abrufe mit Kontaktangabe in der Kennung.
object SecInsider {
    const val QUELLE = "U.S. Securities and Exchange Commission (EDGAR, Form 4)"

    @Serializable
    data class Geschaeft(
        // Kennung der Meldung (Accession Number).
        val a: String,
        // Boersenkuerzel und Name des Emittenten, CIK.
        val k: String,
        val e: String,
        val c: String = "",
        // Meldende Person, Rolle (S Spitze, F Fuehrung, A Aufsicht/Director, N 10 %-Aktionaer oder anderes), Titel.
        val p: String,
        val r: String = "N",
        val t: String = "",
        // Kauf (true) oder Verkauf.
        val kauf: Boolean,
        // Tag des Geschaefts und der Meldung, JJJJ-MM-TT.
        val d: String,
        val f: String = "",
        // Stueck, Durchschnittspreis und Wert in US-Dollar, Bestand danach.
        val n: Double = 0.0,
        val preis: Double = 0.0,
        val wert: Double = 0.0,
        val nach: Double? = null,
        // Verkauf aus einem vorab festgelegten Plan (Rule 10b5-1).
        val plan: Boolean = false,
    ) {
        val rolle: InsiderTrend.Rolle
            get() = when (r) {
                "S" -> InsiderTrend.Rolle.SPITZE
                "F" -> InsiderTrend.Rolle.FUEHRUNG
                "A" -> InsiderTrend.Rolle.AUFSICHT
                else -> InsiderTrend.Rolle.NAHE
            }
    }

    @Serializable
    data class Datei(
        val stand: String = "",
        val quelle: String = QUELLE,
        val geschaefte: List<Geschaeft> = emptyList(),
        val abgerufen: Long = 0L,
    )

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun lesen(text: String): Datei? = runCatching { json.decodeFromString(Datei.serializer(), text) }.getOrNull()

    fun schreiben(d: Datei): String = json.encodeToString(Datei.serializer(), d)

    // Rolle aus den Angaben der Meldung: CEO, CFO, President, Chair zur Spitze, andere Officers, Directors, sonst nahe.
    fun rolle(director: Boolean, officer: Boolean, titel: String): String {
        val t = titel.lowercase()
        val spitze = listOf("chief executive", "ceo", "chief financial", "cfo", "president", "chair", "principal executive", "principal financial")
        return when {
            officer && spitze.any { it in t } -> "S"
            officer -> "F"
            director && spitze.any { it in t } -> "S"
            director -> "A"
            else -> "N"
        }
    }

    // ------------------------------------------------------------ Form 4 (XML) lesen

    private fun wert(xml: String, tag: String): String =
        Regex("<$tag>\\s*(?:<value>)?\\s*([^<]*?)\\s*(?:</value>)?\\s*(?:<footnoteId[^>]*/>\\s*)*</$tag>", RegexOption.DOT_MATCHES_ALL)
            .find(xml)?.groupValues?.get(1)?.trim().orEmpty()

    private fun ja(s: String) = s == "1" || s.equals("true", true)

    private fun text(s: String) = s.replace("&amp;", "&").replace("&apos;", "'").replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").trim()

    // Kaeufe und Verkaeufe einer Form-4-Meldung, je Richtung zusammengefasst; leer bei anderen Geschaeften.
    fun form4(xml: String, accession: String, eingang: String = ""): List<Geschaeft> {
        if (!xml.contains("<ownershipDocument")) return emptyList()
        val symbol = text(wert(xml, "issuerTradingSymbol")).uppercase()
        if (symbol.isBlank() || symbol == "NONE") return emptyList()
        val name = text(wert(xml, "issuerName"))
        val cik = wert(xml, "issuerCik").trimStart('0')
        val eigner = Regex("<reportingOwner>.*?</reportingOwner>", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { it.value }.toList()
        val erster = eigner.firstOrNull() ?: return emptyList()
        val person = text(wert(erster, "rptOwnerName"))
        val titel = text(wert(erster, "officerTitle"))
        // Bei mehreren Meldenden gilt die hoechste Rolle
        val rollen = eigner.map { rolle(ja(wert(it, "isDirector")), ja(wert(it, "isOfficer")), wert(it, "officerTitle")) }
        val r = listOf("S", "F", "A", "N").first { it in rollen }
        val plan = ja(wert(xml, "aff10b5One"))
        val geschaefte = Regex("<nonDerivativeTransaction>.*?</nonDerivativeTransaction>", RegexOption.DOT_MATCHES_ALL).findAll(xml).mapNotNull { m ->
            val g = m.value
            val code = wert(g, "transactionCode")
            if (code != "P" && code != "S") return@mapNotNull null
            val stueck = wert(g, "transactionShares").toDoubleOrNull() ?: return@mapNotNull null
            val preis = wert(g, "transactionPricePerShare").toDoubleOrNull() ?: 0.0
            Triple(code == "P", wert(g, "transactionDate").take(10), Triple(stueck, preis, wert(g, "sharesOwnedFollowingTransaction").toDoubleOrNull()))
        }.toList()
        return geschaefte.groupBy { it.first }.map { (kauf, g) ->
            val n = g.sumOf { it.third.first }
            val w = g.sumOf { it.third.first * it.third.second }
            Geschaeft(
                a = accession, k = symbol, e = name, c = cik, p = person, r = r, t = titel, kauf = kauf,
                d = g.maxOf { it.second }, f = eingang, n = n, preis = if (n > 0) w / n else 0.0, wert = w,
                nach = g.lastOrNull()?.third?.third, plan = plan && !kauf,
            )
        }
    }

    // ------------------------------------------------------------ Auswertung

    // Neue und bekannte Meldungen vereinen (je Meldung und Richtung eine Zeile), nur ab abTag, neueste zuerst.
    fun vereinen(alt: List<Geschaeft>, neu: List<Geschaeft>, abTag: String): List<Geschaeft> {
        val m = LinkedHashMap<String, Geschaeft>()
        (alt + neu).forEach { m[it.a + "|" + it.kauf] = it }
        return m.values.filter { it.d >= abTag }.sortedWith(compareByDescending<Geschaeft> { it.d }.thenByDescending { it.f }.thenByDescending { it.a })
    }

    fun trend(liste: List<Geschaeft>, abTag: String): InsiderTrend.Ergebnis? =
        InsiderTrend.auswerten(liste.filter { it.wert > 0 }.map { InsiderTrend.Geschaeft(it.p, it.rolle, it.kauf, it.wert, it.d, it.plan) }, abTag)

    data class Firma(val kuerzel: String, val name: String, val kaeufe: Double, val verkaeufe: Double, val anzahlKaeufe: Int, val anzahlVerkaeufe: Int) {
        val netto: Double get() = kaeufe - verkaeufe
    }

    // Je Firma ab abTag, staerkste Nettokaeufe zuerst.
    fun firmen(liste: List<Geschaeft>, abTag: String): List<Firma> =
        liste.filter { it.d >= abTag && it.wert > 0 }.groupBy { it.k }.map { (k, g) ->
            Firma(k, g.first().e, g.filter { it.kauf }.sumOf { it.wert }, g.filter { !it.kauf }.sumOf { it.wert }, g.count { it.kauf }, g.count { !it.kauf })
        }.sortedByDescending { it.netto }

    // Kennung fuer die SEC: App, Fassung und die eingetragene Kontaktadresse. Ohne Adresse null (dann kein Abruf).
    fun kennung(kontakt: String): String? {
        val k = kontakt.trim()
        if (!Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(k)) return null
        return "Tryfolio/" + Aktualisierung.FASSUNG + " " + k
    }

    // Neueste Form-4-Meldungen (Atom, 100 je Seite) und Archivadresse einer Meldung.
    const val NEUESTE = "https://www.sec.gov/cgi-bin/browse-edgar?action=getcurrent&type=4&owner=include&count=100&output=atom"

    fun archivAdresse(cik: String, accession: String, datei: String): String =
        "https://www.sec.gov/Archives/edgar/data/${cik.trimStart('0')}/${accession.replace("-", "")}/$datei"

    fun verzeichnisAdresse(cik: String, accession: String): String =
        "https://www.sec.gov/Archives/edgar/data/${cik.trimStart('0')}/${accession.replace("-", "")}/index.json"

    // Eintraege des Atom-Feeds: Accession Number und CIK (aus der Adresse des Eintrags).
    fun neuesteLesen(atom: String): List<Pair<String, String>> =
        Regex("/Archives/edgar/data/(\\d+)/\\d+/(\\d{10}-\\d{2}-\\d{6})-index\\.htm").findAll(atom)
            .map { it.groupValues[2] to it.groupValues[1] }.distinct().toList()

    // XML-Datei der Meldung im Verzeichnis (index.json): die erste .xml ausser der Gliederung.
    fun xmlDatei(indexJson: String): String? =
        Regex("\"name\"\\s*:\\s*\"([^\"]+\\.xml)\"").findAll(indexJson).map { it.groupValues[1] }.firstOrNull { !it.contains("index", true) }
}
