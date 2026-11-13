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
        // Stueck nur aus direkt gehaltenen Zeilen (seit 5.94); null bei aelteren Staenden ohne diese Angabe.
        val nd: Double? = null,
        // Ausuebungspreis je Aktie einer Option, die am selben Tag ausgeuebt (Code M) und verkauft wurde (seit 0.79); null ohne
        // Ausuebung am Tag des Verkaufs oder bei Zuteilungen ohne Preis. Nur bei Verkaeufen.
        val ap: Double? = null,
    ) {
        private val nDirekt: Double get() = nd ?: n

        // Anteil am eigenen direkten Bestand (siehe InsiderTrend.Geschaeft.anteil); null ohne Bestandsangabe.
        val anteil: Double?
            get() {
                val b = nach ?: return null
                val m = nDirekt
                if (m <= 0) return null
                return if (kauf) (if (b > 0) (m / b).coerceAtMost(1.0) else null) else m / (b + m)
            }

        // Bestand vor dem Geschaeft, aus Bestand danach und Stueckzahl.
        val vor: Double? get() = nach?.let { if (kauf) (it - nDirekt).coerceAtLeast(0.0) else it + nDirekt }

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
        // Spanne ohne Daten zwischen mitgeliefertem Stand und nachgeholtem Zeitraum (JJJJ-MM-TT), leer ohne Luecke.
        val lueckeVon: String = "",
        val lueckeBis: String = "",
        // Je Kuerzel der Stand der Meldungen dieses Werts (seit 0.79), wenn die App sie einzeln geholt hat (SEC-Liste des Emittenten oder Nasdaq).
        val standJe: Map<String, Nq> = emptyMap(),
    ) {
        fun luecke(): de.troetenrepublik.app.engine.Luecke.Spanne? = de.troetenrepublik.app.engine.Luecke.Spanne(lueckeVon, lueckeBis).takeIf { !it.leer }

        // Stand und Luecke, die fuer ein Kuerzel gelten: Hat die App die Meldungen dieses Werts einzeln geholt, reichen die Daten dieses
        // Werts bis zu deren Stand, und die Luecke des Gesamtstands ist fuer ihn geschlossen (ausser die Antwort war gekuerzt).
        fun standFuer(symbol: String): Pair<String, de.troetenrepublik.app.engine.Luecke.Spanne?> {
            val q = standJe[symbol.trim().uppercase()] ?: return stand to luecke()
            if (q.stand <= stand) return stand to luecke()
            val l = de.troetenrepublik.app.engine.Luecke.Spanne(q.lueckeVon, q.lueckeBis).takeIf { !it.leer }
            return q.stand to l
        }
    }

    // Einzelabruf fuer ein Kuerzel: Tag (JJJJ-MM-TT) und, wenn die Antwort nicht bis zum Stand zurueckreichte, die Luecke.
    @Serializable
    data class Nq(val stand: String = "", val lueckeVon: String = "", val lueckeBis: String = "")

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun lesen(text: String): Datei? = runCatching { json.decodeFromString(Datei.serializer(), text) }.getOrNull()

    fun schreiben(d: Datei): String = json.encodeToString(Datei.serializer(), d)

    // Rolle aus den Angaben der Meldung: CEO, CFO, President, Chair zur Spitze, andere Officers, Directors, sonst nahe.
    // Vizepraesidenten und Stellvertreter zaehlen nicht zur Spitze (17 CFR 240.16a-1(f) trennt sie vom President).
    private val CEO_CFO = Regex("chief executive|\\bceo\\b|chief financial|\\bcfo\\b|principal executive|principal financial")
    private val VIZE = Regex("\\bvice\\b|\\bvp\\b|\\bsvp\\b|\\bevp\\b|deputy|assistant|co-president")
    private val PRAESIDENT = Regex("\\bpresident\\b|\\bchair(man|woman|person)?\\b")

    fun rolle(director: Boolean, officer: Boolean, titel: String): String {
        val t = titel.lowercase()
        val spitze = CEO_CFO.containsMatchIn(t) || (!VIZE.containsMatchIn(t) && PRAESIDENT.containsMatchIn(t))
        return when {
            (officer || director) && spitze -> "S"
            officer -> "F"
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

    private data class Zeile(val kauf: Boolean, val tag: String, val stueck: Double, val preis: Double, val nach: Double?, val direkt: Boolean)

    // Kaeufe und Verkaeufe einer Form-4-Meldung, je Richtung zusammengefasst; leer bei anderen Geschaeften.
    fun form4(xml: String, accession: String, eingang: String = ""): List<Geschaeft> {
        if (!xml.contains("<ownershipDocument")) return emptyList()
        val symbol = text(wert(xml, "issuerTradingSymbol")).uppercase()
        if (symbol.isBlank() || symbol == "NONE") return emptyList()
        val name = text(wert(xml, "issuerName"))
        val cik = wert(xml, "issuerCik").trimStart('0')
        val eigner = Regex("<reportingOwner>.*?</reportingOwner>", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { it.value }.toList()
        if (eigner.isEmpty()) return emptyList()
        // Bei mehreren Meldenden zaehlt die Person mit der hoechsten Rolle, Name und Titel kommen von ihr
        val rollen = eigner.map { rolle(ja(wert(it, "isDirector")), ja(wert(it, "isOfficer")), wert(it, "officerTitle")) }
        val r = listOf("S", "F", "A", "N").first { it in rollen }
        val erster = eigner[rollen.indexOf(r)]
        val person = text(wert(erster, "rptOwnerName"))
        val titel = text(wert(erster, "officerTitle"))
        val plan = ja(wert(xml, "aff10b5One"))
        val geschaefte = Regex("<nonDerivativeTransaction>.*?</nonDerivativeTransaction>", RegexOption.DOT_MATCHES_ALL).findAll(xml).mapNotNull { m ->
            val g = m.value
            val code = wert(g, "transactionCode")
            if (code != "P" && code != "S") return@mapNotNull null
            val stueck = wert(g, "transactionShares").toDoubleOrNull() ?: return@mapNotNull null
            val preis = wert(g, "transactionPricePerShare").toDoubleOrNull() ?: 0.0
            // D direkt, I indirekt (etwa ueber einen Trust); jede Zeile hat ihren eigenen Bestand danach
            val direkt = wert(g, "directOrIndirectOwnership") != "I"
            Zeile(code == "P", wert(g, "transactionDate").take(10), stueck, preis, wert(g, "sharesOwnedFollowingTransaction").toDoubleOrNull(), direkt)
        }.toList()
        // Ausuebungen von Optionen (Code M) je Tag mit dem Ausuebungspreis, gewichtet nach Stueck
        val ausuebung = HashMap<String, Pair<Double, Double>>()
        Regex("<derivativeTransaction>.*?</derivativeTransaction>", RegexOption.DOT_MATCHES_ALL).findAll(xml).forEach { m ->
            val g = m.value
            if (wert(g, "transactionCode") != "M") return@forEach
            val stueck = wert(g, "transactionShares").toDoubleOrNull() ?: return@forEach
            val preis = wert(g, "conversionOrExercisePrice").toDoubleOrNull()?.takeIf { it > 0 } ?: return@forEach
            val tag = wert(g, "transactionDate").take(10)
            val alt = ausuebung[tag] ?: (0.0 to 0.0)
            ausuebung[tag] = (alt.first + stueck) to (alt.second + stueck * preis)
        }
        return geschaefte.groupBy { it.kauf }.map { (kauf, g) ->
            val n = g.sumOf { it.stueck }
            val w = g.sumOf { it.stueck * it.preis }
            val direkt = g.filter { it.direkt }
            Geschaeft(
                a = accession, k = symbol, e = name, c = cik, p = person, r = r, t = titel, kauf = kauf,
                d = g.maxOf { it.tag }, f = eingang, n = n, preis = if (n > 0) w / n else 0.0, wert = w,
                nach = direkt.lastOrNull { it.nach != null }?.nach, plan = plan && !kauf, nd = direkt.sumOf { it.stueck },
                ap = if (kauf) null else ausuebung[g.maxOf { it.tag }]?.let { (st, sum) -> if (st > 0) sum / st else null },
            )
        }
    }

    // ------------------------------------------------------------ Auswertung

    // Neue und bekannte Meldungen vereinen (je Meldung und Richtung eine Zeile), nur ab abTag, neueste zuerst.
    fun vereinen(alt: List<Geschaeft>, neu: List<Geschaeft>, abTag: String): List<Geschaeft> {
        val m = LinkedHashMap<String, Geschaeft>()
        (alt + neu).forEach { m[it.a + "|" + it.kauf] = it }
        return bereinigen(m.values.filter { it.d >= abTag }.sortedWith(compareByDescending<Geschaeft> { it.d }.thenByDescending { it.f }.thenByDescending { it.a }))
    }

    // Zwei Fehler der Meldungen selbst ausgleichen:
    // 1. Im Feld fuer den Preis je Aktie steht der Gesamterloes (Preis ueber 10.000 Dollar, geteilt durch die Stueckzahl
    //    ein ueblicher Kurs): der Wert ist dann der gemeldete Betrag, nicht Stueck mal Betrag.
    // 2. Dasselbe Geschaeft von mehreren gemeinsam Meldenden (gleicher Wert, Tag, Richtung, Stueck, Preis, Bestand danach):
    //    es zaehlt einmal; es bleibt die Meldung der Person mit Amt, sonst die erste.
    internal fun bereinigen(liste: List<Geschaeft>): List<Geschaeft> {
        // Zeilen der Nasdaq (Kennung nq-) weichen einer Meldung der SEC zu derselben Person, demselben Tag und derselben Richtung
        fun person(g: Geschaeft) = Triple(g.k + "|" + NasdaqInsider.personSchluessel(g.p), g.d, g.kauf)
        val sec = liste.filter { !it.a.startsWith(NasdaqInsider.KENNUNG) }.mapTo(HashSet()) { person(it) }
        val ohneDoppelte = liste.filter { !it.a.startsWith(NasdaqInsider.KENNUNG) || person(it) !in sec }
        val gerade = ohneDoppelte.map { g ->
            if (g.preis > 10_000.0 && g.n > 0.0 && g.preis / g.n < 1_000.0 && !g.k.startsWith("BRK")) g.copy(wert = g.preis, preis = g.preis / g.n) else g
        }
        fun schluessel(g: Geschaeft) = listOf(g.k, g.d, g.kauf, g.n, g.preis, g.nach).joinToString("|")
        val gruppen = gerade.filter { it.wert > 0 && it.nach != null }.groupBy(::schluessel).filterValues { v -> v.size > 1 && v.map { it.p.trim().lowercase() }.distinct().size > 1 }
        if (gruppen.isEmpty()) return gerade
        val bleibt = gruppen.mapValues { (_, v) -> v.firstOrNull { it.r != "N" } ?: v.first() }
        return gerade.filter { g -> if (g.wert > 0 && g.nach != null) bleibt[schluessel(g)]?.let { it === g } ?: true else true }
    }

    fun trend(liste: List<Geschaeft>, abTag: String): InsiderTrend.Ergebnis? =
        InsiderTrend.auswerten(liste.filter { it.wert > 0 }.map { InsiderTrend.Geschaeft(it.p, it.rolle, it.kauf, it.wert, it.d, it.plan, it.anteil) }, abTag)

    // Bestand einer Person nach ihrer letzten Meldung: Stueck (nur direkt gehalten) und Wert zum Kurs kurs.
    data class Bestand(val person: String, val rolle: String, val titel: String, val stueck: Double, val tag: String)

    // Je Person der Bestand nach dem juengsten Geschaeft mit Bestandsangabe, groesste zuerst.
    fun bestaende(liste: List<Geschaeft>): List<Bestand> =
        liste.filter { it.nach != null }.groupBy { it.p.trim().lowercase() }.mapNotNull { (_, g) ->
            val j = g.maxWithOrNull(compareBy<Geschaeft> { it.d }.thenBy { it.f }.thenBy { it.a }) ?: return@mapNotNull null
            Bestand(j.p, j.r, j.t, j.nach ?: 0.0, j.d)
        }.filter { it.stueck > 0 }.sortedByDescending { it.stueck }

    data class Firma(val kuerzel: String, val name: String, val kaeufe: Double, val verkaeufe: Double, val anzahlKaeufe: Int, val anzahlVerkaeufe: Int) {
        val netto: Double get() = kaeufe - verkaeufe
    }

    // Je Firma ab abTag, staerkste Nettokaeufe zuerst.
    fun firmen(liste: List<Geschaeft>, abTag: String): List<Firma> =
        liste.filter { it.d >= abTag && it.wert > 0 }.groupBy { it.k }.map { (k, g) ->
            Firma(k, g.first().e, g.filter { it.kauf }.sumOf { it.wert }, g.filter { !it.kauf }.sumOf { it.wert }, g.count { it.kauf }, g.count { !it.kauf })
        }.sortedByDescending { it.netto }

    // Kontaktangabe ohne Bezug zu einer Person: zwoelf Zufallszeichen an einer fuer Beispiele reservierten Domain. Wird
    // einmal je Installation erzeugt und vorbelegt, wenn der Nutzer keine eigene Adresse eintraegt.
    fun zufallsKontakt(): String = buildString { repeat(12) { append("abcdefghijklmnopqrstuvwxyz0123456789".random()) } } + "@example.org"

    // Kennung fuer die SEC: App, Fassung und die eingetragene Kontaktadresse. Ohne Adresse null (dann kein Abruf).
    fun kennung(kontakt: String): String? {
        val k = kontakt.trim()
        if (!Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(k)) return null
        return "Tryfolio/" + Aktualisierung.FASSUNG + " " + k
    }

    // Tagesverzeichnis der SEC mit allen Meldungen eines Tages nach Formular (form.JJJJMMTT.idx im Quartalsordner).
    // Die robots.txt der SEC erlaubt /Archives, sperrt aber /cgi-bin.
    fun tagesIndex(tag: java.time.LocalDate): String =
        "https://www.sec.gov/Archives/edgar/daily-index/${tag.year}/QTR${(tag.monthValue - 1) / 3 + 1}/form." +
            tag.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE) + ".idx"

    // Die letzten n Handelstage bis heute, neueste zuerst; Wochenenden und Feiertage der US-Boersen fallen weg.
    fun letzteHandelstage(heute: java.time.LocalDate, n: Int = 3): List<java.time.LocalDate> =
        generateSequence(heute) { it.minusDays(1) }.take(maxOf(15, n * 2 + 7)).filter { de.troetenrepublik.app.engine.WeitereQuellen.usHandelstag(it) }.take(n).toList()

    // Form-4-Eintraege eines Tagesverzeichnisses: Accession Number und CIK aus dem Dateinamen, ohne 4/A und andere
    // Formulare. Jede Meldung steht je Beteiligtem einmal im Verzeichnis, sie zaehlt nur einmal.
    fun tagesIndexLesen(idx: String): List<Pair<String, String>> {
        val datei = Regex("edgar/data/(\\d+)/(\\d{10}-\\d{2}-\\d{6})\\.txt")
        return idx.lineSequence().filter { it.startsWith("4 ") }
            .mapNotNull { z -> datei.find(z)?.let { it.groupValues[2] to it.groupValues[1] } }
            .distinctBy { it.first }.toList()
    }

    // Hoechstens so viele Meldungen laedt ein Abruf nach (je Meldung zwei Abrufe mit Pause).
    const val ABRUF_BUDGET = 150

    // Emittenten (CIK), deren neue Meldungen ein Tagesabruf liest: wer in den letzten 180 Tagen einen Kauf an der Boerse
    // hatte (Grundlage der Kaeufer-Listen und Cluster), dazu der geoeffnete Wert. Ein Tagesverzeichnis fuehrt rund 1.700
    // Form-4-Zeilen; alle zu lesen waere weder noetig noch moeglich.
    fun gesuchteCiks(liste: List<Geschaeft>, symbol: String?, heute: String): Set<String> {
        val ab = runCatching { java.time.LocalDate.parse(heute).minusDays(180).toString() }.getOrDefault("")
        return liste.filter { it.c.isNotBlank() && ((it.kauf && it.d >= ab) || (symbol != null && it.k.equals(symbol, true))) }.mapTo(HashSet()) { it.c.trimStart('0') }
    }

    // Tageweises Vorruecken: Tage von alt nach neu, je Tag die Meldungen der gesuchten Emittenten, die noch fehlen. Ein Tag
    // zaehlt als erfasst, wenn alle seine gesuchten Meldungen in dieses Abrufbudget passen (der erste Tag immer, damit der Stand
    // vorrueckt). Danach endet das Vorruecken; der naechste Abruf macht dort weiter.
    class Vorlauf(private val budget: Int = ABRUF_BUDGET) {
        val abrufen = LinkedHashMap<String, String>()
        // Erster und letzter erfasster Tag (JJJJ-MM-TT), null ohne erfassten Tag
        var ab: String? = null
            private set
        var bis: String? = null
            private set

        // false, wenn der Tag nicht mehr ins Budget passt (Abruf endet davor)
        fun tag(tag: String, eintraege: List<Pair<String, String>>, gesucht: Set<String>, bekannt: Set<String>): Boolean {
            val noetig = eintraege.filter { (a, cik) -> cik.trimStart('0') in gesucht && a !in bekannt && a !in abrufen }
            if (abrufen.isNotEmpty() && abrufen.size + noetig.size > budget) return false
            noetig.forEach { (a, cik) -> abrufen[a] = cik }
            if (ab == null) ab = tag
            bis = tag
            return true
        }
    }

    // Archivadresse einer Meldung.
    fun archivAdresse(cik: String, accession: String, datei: String): String =
        "https://www.sec.gov/Archives/edgar/data/${cik.trimStart('0')}/${accession.replace("-", "")}/$datei"

    fun verzeichnisAdresse(cik: String, accession: String): String =
        "https://www.sec.gov/Archives/edgar/data/${cik.trimStart('0')}/${accession.replace("-", "")}/index.json"

    // Accession Number und CIK aus Links auf Meldungsseiten im Archiv.
    fun neuesteLesen(atom: String): List<Pair<String, String>> =
        Regex("/Archives/edgar/data/(\\d+)/\\d+/(\\d{10}-\\d{2}-\\d{6})-index\\.htm").findAll(atom)
            .map { it.groupValues[2] to it.groupValues[1] }.distinct().toList()

    // XML-Datei der Meldung im Verzeichnis (index.json): die erste .xml ausser der Gliederung.
    fun xmlDatei(indexJson: String): String? =
        Regex("\"name\"\\s*:\\s*\"([^\"]+\\.xml)\"").findAll(indexJson).map { it.groupValues[1] }.firstOrNull { !it.contains("index", true) }
}
