package de.troetenrepublik.app.market

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

// Klagen, Urteile und Behoerdenentscheidungen zu einer Firma. Quellen:
//  - CourtListener (Free Law Project, REST API v4, nur mit eigenem Token): Akten der US-Bundesgerichte (RECAP) und
//    Urteile, in denen die Firma Partei ist. Rolle (Klaeger, Beklagter) aus der Reihenfolge im Aktennamen.
//  - EU-Gerichte: Urteile des EuGH und des EuG ueber das SPARQL-Angebot des Amts fuer Veroeffentlichungen (CELLAR,
//    Weiterverwendung nach Beschluss 2011/833/EU). Parteien stehen im Titel des Urteils.
//  - UK: Faelle der Wettbewerbsbehoerde CMA ueber die Suche von GOV.UK (Open Government Licence).
//  - US: Pressemitteilungen des Justizministeriums (offene Schnittstelle) mit Firmenname im Titel und Vergleichs- oder
//    Anklagewort; Summen stehen nur, wenn der Titel sie nennt.
// Zugeordnet wird nur ueber den Firmennamen und nur, wenn er eindeutig ist; Unsicheres bleibt weg.
object Klagen {
    enum class Quelle { COURTLISTENER, EU, CMA, DOJ }
    enum class Art { VERFAHREN, URTEIL, BEHOERDE }
    enum class Rolle { KLAEGER, BEKLAGTER, UNBEKANNT }
    enum class Gebiet { WERTPAPIER, KARTELL, FUSION, SCHUTZRECHTE, PRODUKT, ARBEIT, UMWELT, VERBRAUCHER, BETRUG, SONSTIGE }
    enum class Stand { OFFEN, ABGESCHLOSSEN, UNBEKANNT }

    // datum (ISO) leer, wo die Quelle keines nennt; betrag in Dollar nur aus dem Titel einer Pressemitteilung
    data class Fall(
        val quelle: Quelle, val art: Art, val titel: String, val stelle: String, val gebiet: Gebiet, val rolle: Rolle,
        val datum: String, val stand: Stand, val betrag: Double?, val link: String,
    ) {
        val jahr: Int get() = datum.take(4).toIntOrNull() ?: 0
    }

    // mehr: Die Suche hatte weitere Seiten, die nicht gelesen wurden (Zahlen gelten als Mindestwert)
    data class Ergebnis(val faelle: List<Fall>, val mehr: Boolean = false) {
        val leer: Boolean get() = faelle.isEmpty()

        // Offene Akten der Bundesgerichte
        val offeneBund: Int get() = faelle.count { it.quelle == Quelle.COURTLISTENER && it.art == Art.VERFAHREN && it.stand == Stand.OFFEN }

        // Offene Faelle mit Rolle der Firma: wichtigste zuerst (offen, Betrag, neuestes Datum)
        val sortiert: List<Fall>
            get() = faelle.sortedWith(
                compareBy<Fall> { if (it.stand == Stand.OFFEN) 0 else 1 }.thenByDescending { it.betrag ?: -1.0 }.thenByDescending { it.datum },
            )

        // Summe der Betraege aus Pressemitteilungen der letzten fuenf Jahre
        fun strafen(heute: LocalDate): Double {
            val ab = heute.minusYears(5).toString()
            return faelle.filter { it.quelle == Quelle.DOJ && it.datum > ab }.sumOf { it.betrag ?: 0.0 }
        }
    }

    fun zusammen(a: Ergebnis?, b: Ergebnis?): Ergebnis? {
        if (a == null) return b
        if (b == null) return a
        return Ergebnis((a.faelle + b.faelle).distinctBy { it.link.ifBlank { it.titel } }, a.mehr || b.mehr)
    }

    // Neue Verfahren je Jahr: die letzten zehn Jahre bis zum laufenden, auch mit null. zusatz: Jahre weiterer Verfahren
    // (zum Beispiel aus den SEC-Listen).
    fun neuJeJahr(faelle: List<Fall>, zusatz: List<Int>, heute: LocalDate): List<Pair<Int, Int>> {
        val jahre = faelle.filter { it.art != Art.URTEIL }.map { it.jahr } + zusatz
        return (heute.year - 9..heute.year).map { j -> j to jahre.count { it == j } }
    }

    // Strafen je Jahr: Betraege mit Jahr, die letzten zehn Jahre
    fun strafenJeJahr(betraege: List<Pair<Int, Double>>, heute: LocalDate): List<Pair<Int, Double>> =
        (heute.year - 9..heute.year).map { j -> j to betraege.filter { it.first == j }.sumOf { it.second } }

    // ------------------------------------------------------------ Namen

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // Der Name muss sich selbst eindeutig zuordnen lassen (nicht zu kurz, kein allgemeines Wort)
    fun eindeutig(firma: String): Boolean = Verfahren.betriebPasst(firma, Verfahren.kurzname(firma))

    // Suchname: Kurzname, nur Buchstaben, Ziffern, Leerzeichen, & . und -; null, wenn er nicht eindeutig ist
    fun suchname(firma: String): String? {
        if (!eindeutig(firma)) return null
        val n = Verfahren.kurzname(firma).filter { it.isLetterOrDigit() || it == ' ' || it == '&' || it == '.' || it == '-' }.trim()
            .let { if (it.startsWith("the ", ignoreCase = true)) it.drop(4).trim() else it }
        return n.takeIf { it.length >= 3 }
    }

    // Woerter eines Titels in Kleinbuchstaben ohne Akzente, ohne „'s“, „&“ und „and“
    internal fun woerter(s: String): List<String> {
        val ohne = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return ohne.lowercase().replace(Regex("[’']s\\b"), "").replace('&', ' ').split(Regex("[^a-z0-9]+"))
            .filter { it.isNotEmpty() && it != "and" }
    }

    private val ZUSATZ = setOf("inc", "corp", "corporation", "co", "company", "llc", "ltd", "limited", "lp", "plc", "usa", "us", "group", "holdings", "na", "sa", "nv", "ag", "se")

    // Position des Firmennamens als zusammenhaengende Wortfolge im Titel, -1 ohne Treffer. Mit Ende der Folge.
    private fun fundstelle(firma: String, titel: List<String>): Pair<Int, Int>? {
        val f = woerter(Verfahren.kurzname(firma))
        if (f.isEmpty() || f.size > titel.size) return null
        for (i in 0..titel.size - f.size) if (titel.subList(i, i + f.size) == f) return i to i + f.size
        return null
    }

    // Titel einer Behoerde (CMA): Name steht als Folge von Woertern darin
    fun titelNennt(firma: String, titel: String): Boolean = eindeutig(firma) && fundstelle(firma, woerter(titel)) != null

    // Seite einer Akte („Boeing Co.“, „Boeing Co. and Beispiel Person“): eine der Parteien passt eindeutig zur Firma
    private val PARTEIEN = Regex("\\s+and\\s+|;|\\s+et\\s+al\\.?|,\\s*et\\s+al\\.?|,\\s*individually.*")

    private fun seiteTrifft(firma: String, seite: String): Boolean {
        val s = seite.trim().trim(',', '.', ' ')
        if (s.isEmpty()) return false
        return Verfahren.passt(firma, s) || s.split(PARTEIEN).any { it.isNotBlank() && Verfahren.passt(firma, it.trim().trim(',', '.', ' ')) }
    }

    private val VERSUS = Regex("\\s+vs?\\.?\\s+", RegexOption.IGNORE_CASE)

    private val SAMMEL = Regex(
        "\\b(securities|shareholder|stockholder|derivative|antitrust|litigation|privacy|data breach|products|product|patent|consumer|erisa|employment|wage|" +
            "marketing|sales practices|opioid|mdl|class action|merger)\\b.*", RegexOption.IGNORE_CASE,
    )

    // Rolle der Firma aus dem Aktennamen: links Klaeger, rechts Beklagter, „In re X Securities Litigation“ als Beklagte.
    // Null, wenn die Firma nicht eindeutig vorkommt.
    fun rolle(firma: String, name: String): Rolle? {
        val n = name.replace(' ', ' ').trim()
        val teile = n.split(VERSUS, limit = 2)
        if (teile.size == 2) return when {
            seiteTrifft(firma, teile[0]) -> Rolle.KLAEGER
            seiteTrifft(firma, teile[1]) -> Rolle.BEKLAGTER
            else -> null
        }
        val r = Regex("^in re:?\\s+", RegexOption.IGNORE_CASE).find(n) ?: return null
        val rest = n.substring(r.range.last + 1).replace(SAMMEL, "").trim().trim(',', '.', ' ')
        return if (seiteTrifft(firma, rest)) Rolle.BEKLAGTER else null
    }

    // Sachgebiet aus Text (Art der Klage, Aktenname, Gegenstand, Abteilung)
    fun gebiet(text: String): Gebiet {
        val t = text.lowercase()
        fun hat(vararg w: String) = w.any { t.contains(it) }
        return when {
            hat("securit") -> Gebiet.WERTPAPIER
            hat("antitrust", "sherman", "clayton", "price fixing", "price-fixing", "cartel", "competition", "state aid", "dominant position", "restrictive practices") -> Gebiet.KARTELL
            hat("merger", "concentration") -> Gebiet.FUSION
            hat("patent", "copyright", "trademark", "trade mark", "lanham") -> Gebiet.SCHUTZRECHTE
            hat("product liab", "products liab", "prod. liab", "recall", "opioid", "talc", "misbrand", "adulterat", "medical device", "personal inj") -> Gebiet.PRODUKT
            hat("employ", "labor", "wage", "flsa", "erisa", "discriminat", "title vii", "workers") -> Gebiet.ARBEIT
            hat("environment", "clean air", "clean water", "cercla", "superfund", "pollution", "emission") -> Gebiet.UMWELT
            hat("consumer", "privacy", "data breach", "tcpa", "truth in lending", "fair credit", "debt collection", "deceptive", "false advertising") -> Gebiet.VERBRAUCHER
            hat("false claims", "fcpa", "foreign corrupt", "corrupt", "bribe", "bribery", "fraud") -> Gebiet.BETRUG
            else -> Gebiet.SONSTIGE
        }
    }

    private fun JsonObject.text(k: String): String? = this[k]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.trim()?.takeIf { it.isNotEmpty() }

    private fun u(s: String) = URLEncoder.encode(s, "UTF-8")

    // ------------------------------------------------------------ CourtListener

    const val CL_BASIS = "https://www.courtlistener.com"
    const val CL_SUCHE = "$CL_BASIS/api/rest/v4/search/"

    // Suchadresse: Aktenname mit dem Firmennamen als Wortfolge, neueste zuerst. urteile: Urteile statt Akten. Null bei
    // nicht eindeutigem Namen.
    fun clAdresse(firma: String, urteile: Boolean): String? {
        val n = suchname(firma) ?: return null
        return CL_SUCHE + "?type=" + (if (urteile) "o" else "r") + "&q=" + u("caseName:\"$n\"") + "&order_by=" + u("dateFiled desc")
    }

    // Webseite der Suche in den Gerichtsakten (ohne Token zum Oeffnen im Browser)
    fun clWeb(firma: String): String = CL_BASIS + "/?type=r&q=" + u("caseName:\"" + Verfahren.kurzname(firma) + "\"")

    class ClSeite(val faelle: List<Fall>, val weiter: String?)

    // Eine Seite der Suche; die Adresse der naechsten nur, wenn sie zu CourtListener gehoert
    fun clLesen(text: String, firma: String, urteile: Boolean): ClSeite = runCatching {
        val w = json.parseToJsonElement(text).jsonObject
        val weiter = w.text("next")?.takeIf { it.startsWith("$CL_BASIS/api/") }
        val faelle = w["results"]!!.jsonArray.mapNotNull { e ->
            val o = e.jsonObject
            val name = o.text("caseName") ?: return@mapNotNull null
            val wer = rolle(firma, name) ?: return@mapNotNull null
            val datum = o.text("dateFiled")?.take(10).orEmpty()
            val ende = o.text("dateTerminated")
            val pfad = o.text("docket_absolute_url") ?: o.text("absolute_url").orEmpty()
            val link = if (pfad.startsWith("/")) CL_BASIS + pfad else pfad
            val stelle = o.text("court_citation_string") ?: o.text("court").orEmpty()
            val sach = gebiet(listOfNotNull(o.text("suitNature"), o.text("cause"), name).joinToString(" "))
            Fall(
                Quelle.COURTLISTENER, if (urteile) Art.URTEIL else Art.VERFAHREN, name, stelle, sach, wer, datum,
                if (urteile) Stand.ABGESCHLOSSEN else if (ende == null) Stand.OFFEN else Stand.ABGESCHLOSSEN, null, link,
            )
        }.distinctBy { it.link.ifBlank { it.titel + it.datum } }
        ClSeite(faelle, weiter)
    }.getOrDefault(ClSeite(emptyList(), null))

    // ------------------------------------------------------------ EU-Gerichte (CELLAR)

    const val EU_SPARQL = "https://publications.europa.eu/webapi/rdf/sparql"

    // Urteile des EuGH und des EuG (CELEX 6JJJJCJ und 6JJJJTJ) mit dem Namen im Titel, neueste zuerst
    fun euAdresse(firma: String, ab: LocalDate): String? {
        val n = suchname(firma)?.lowercase()?.filter { it.isLetterOrDigit() || it == ' ' || it == '&' || it == '.' || it == '-' } ?: return null
        val q = "PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>\n" +
            "SELECT ?celex ?t ?d WHERE {\n" +
            " ?w cdm:resource_legal_id_celex ?celex .\n ?w cdm:work_date_document ?d .\n" +
            " ?e cdm:expression_belongs_to_work ?w .\n" +
            " ?e cdm:expression_uses_language <http://publications.europa.eu/resource/authority/language/ENG> .\n" +
            " ?e cdm:expression_title ?t .\n" +
            " FILTER(CONTAINS(LCASE(STR(?t)), \"$n\"))\n" +
            " FILTER(REGEX(STR(?celex), \"^6[0-9]{4}(CJ|TJ)[0-9]+\$\"))\n" +
            " FILTER(?d >= \"$ab\"^^<http://www.w3.org/2001/XMLSchema#date>)\n" +
            "} ORDER BY DESC(?d) LIMIT 80"
        return EU_SPARQL + "?query=" + u(q)
    }

    private val CELEX = Regex("^6\\d{4}(CJ|TJ)\\d+$")

    // Urteile, in denen die Firma Partei ist und die ein Sachgebiet der Firma betreffen (Wettbewerb, Schutzrechte, Produkte,
    // Umwelt, Arbeit, Verbraucher). Titel: „Judgment ... .#Parteien.#Gegenstand – ...#Case ...“
    fun euLesen(text: String, firma: String): List<Fall> = runCatching {
        json.parseToJsonElement(text).jsonObject["results"]!!.jsonObject["bindings"]!!.jsonArray.mapNotNull { e ->
            val o = e.jsonObject
            val celex = o["celex"]?.jsonObject?.text("value") ?: return@mapNotNull null
            if (!CELEX.matches(celex)) return@mapNotNull null
            val titel = (o["t"]?.jsonObject?.text("value") ?: return@mapNotNull null).replace(' ', ' ')
            val teile = titel.split('#')
            if (teile.size < 3) return@mapNotNull null
            val parteien = teile[1].trim().trimEnd('.').trim()
            val wer = rolle(firma, parteien) ?: return@mapNotNull null
            val sach = gebiet(teile[2])
            if (sach == Gebiet.SONSTIGE || sach == Gebiet.BETRUG) return@mapNotNull null
            val datum = o["d"]?.jsonObject?.text("value")?.take(10).orEmpty()
            Fall(
                Quelle.EU, Art.URTEIL, parteien, if (celex.contains("CJ")) "EuGH" else "EuG", sach, wer, datum, Stand.ABGESCHLOSSEN, null,
                "https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:$celex",
            )
        }.distinctBy { it.link }
    }.getOrDefault(emptyList())

    // ------------------------------------------------------------ CMA (GOV.UK)

    const val CMA_BASIS = "https://www.gov.uk"

    fun cmaAdresse(firma: String): String? {
        val n = suchname(firma) ?: return null
        return "$CMA_BASIS/api/search.json?filter_document_type=cma_case&count=50&q=" + u(n) +
            listOf("title", "link", "public_timestamp", "case_state", "case_type", "outcome_type", "opened_date").joinToString("") { "&fields=$it" }
    }

    // Feld als Liste (erster Eintrag) oder als einzelner Wert
    private fun JsonObject.erster(k: String): String? = runCatching {
        when (val w = this[k]) {
            is JsonArray -> w.firstOrNull()?.jsonPrimitive?.contentOrNull
            is JsonPrimitive -> w.contentOrNull
            else -> null
        }
    }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    // Faelle der CMA, deren Titel den Firmennamen nennt. Fusionen mit Freigabe entfallen (keine Beanstandung).
    fun cmaLesen(text: String, firma: String): List<Fall> = runCatching {
        json.parseToJsonElement(text).jsonObject["results"]!!.jsonArray.mapNotNull { e ->
            val o = e.jsonObject
            val titel = o.text("title") ?: return@mapNotNull null
            if (!titelNennt(firma, titel)) return@mapNotNull null
            val typ = o.erster("case_type").orEmpty()
            val ausgang = o.erster("outcome_type").orEmpty()
            if (typ == "mergers" && ausgang.contains("clearance")) return@mapNotNull null
            val gebiet = when {
                typ == "mergers" -> Gebiet.FUSION
                typ == "consumer-enforcement" -> Gebiet.VERBRAUCHER
                typ in setOf("ca98-and-civil-cartels", "criminal-cartels", "digital-markets-unit", "markets", "competition-disqualification") -> Gebiet.KARTELL
                else -> Gebiet.SONSTIGE
            }
            val stand = when (o.erster("case_state")) {
                "open" -> Stand.OFFEN
                "closed" -> Stand.ABGESCHLOSSEN
                else -> Stand.UNBEKANNT
            }
            val datum = (o.text("opened_date") ?: o.text("public_timestamp"))?.take(10).orEmpty()
            val pfad = o.text("link") ?: return@mapNotNull null
            Fall(Quelle.CMA, Art.BEHOERDE, titel.trim(), "CMA", gebiet, Rolle.UNBEKANNT, datum, stand, null, if (pfad.startsWith("/")) CMA_BASIS + pfad else pfad)
        }.distinctBy { it.link }
    }.getOrDefault(emptyList())

    // ------------------------------------------------------------ Justizministerium

    const val DOJ_BASIS = "https://www.justice.gov/api/v1/press_releases.json"

    fun dojAdresse(firma: String): String? {
        val n = suchname(firma) ?: return null
        return DOJ_BASIS + "?parameters%5Btitle%5D=" + u(n) + "&pagesize=50&sort=date&direction=DESC&fields=title,date,url,component"
    }

    // Wort nach dem Firmennamen: nur Verben und Verbindungswoerter einer Mitteilung ueber die Firma (nie „Engineer“,
    // „Executive“, „Supplier“ und andere Rollen von Personen oder fremden Firmen)
    private val NACH = setOf(
        "agrees", "agree", "agreed", "pay", "pays", "paid", "plead", "pleads", "pleaded", "admits", "admit", "settle", "settles", "settled",
        "resolve", "resolves", "charged", "indicted", "convicted", "sentenced", "fined", "sued", "sues", "ordered", "enters", "entered",
        "forfeit", "forfeits", "subsidiary", "to", "will", "must", "for", "over", "in", "of", "with",
    )

    private val ANLASS = setOf(
        "agrees", "agree", "agreed", "pay", "pays", "paid", "plead", "pleads", "pleaded", "guilty", "settle", "settles", "settled", "settlement",
        "resolve", "resolves", "resolution", "charged", "charges", "indicted", "indictment", "convicted", "sentenced", "fine", "fined",
        "penalty", "penalties", "sues", "sued", "lawsuit", "complaint", "decree", "prosecution", "forfeit", "forfeiture", "admits",
    )

    private val VOR_FREMD = setOf("from", "at", "by", "to", "defrauding", "defrauded", "bribing", "bribed", "victimizing", "former")

    private val VERGLEICH = setOf("agrees", "agree", "agreed", "pay", "pays", "paid", "pleads", "pleaded", "guilty", "settle", "settles", "settled", "settlement", "resolve", "resolves", "sentenced", "fined")

    private fun entitaeten(s: String) = s.replace("&amp;", "&").replace("&#039;", "'").replace("&quot;", "\"").replace("&#x27;", "'").replace("&nbsp;", " ").trim()

    // Mitteilung über die Firma als Beschuldigte oder Vergleichspartei: Name im Titel, danach ein Verb oder Verbindungswort
    // (Zusaetze wie „Inc.“ dazwischen sind erlaubt), im Titel ein Wort zu Vergleich, Strafe oder Anklage
    fun dojPasst(firma: String, titel: String): Boolean {
        if (!eindeutig(firma)) return false
        val t = woerter(titel)
        val (start, ende) = fundstelle(firma, t) ?: return false
        if (t.none { it in ANLASS }) return false
        // „Kickbacks from X“, „Employee at X“, „Defrauding X“: die Firma ist Geschaedigte oder Arbeitgeber, nicht Partei
        if (start > 0 && t[start - 1] in VOR_FREMD) return false
        var i = ende
        while (i < t.size && t[i] in ZUSATZ) i++
        return i >= t.size || t[i] in NACH
    }

    private val BETRAG = Regex("""\$\s?(\d[\d,]*(?:\.\d+)?)(?:\s*(billion|million|thousand)|(bn|mn|[bmk])(?![a-z]))?""", RegexOption.IGNORE_CASE)

    // Hoechster Dollarbetrag im Titel („$2.2 Billion“, „$18 Million“, „$29.5M“, „$50,000“); null ohne Betrag
    fun betragAusTitel(titel: String): Double? = BETRAG.findAll(titel).mapNotNull { m ->
        val zahl = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return@mapNotNull null
        val einheit = m.groupValues[2].ifEmpty { m.groupValues[3] }.lowercase()
        zahl * when (einheit) { "billion", "bn", "b" -> 1e9; "million", "mn", "m" -> 1e6; "thousand", "k" -> 1e3; else -> 1.0 }
    }.maxOrNull()

    fun dojLesen(text: String, firma: String, ab: LocalDate): List<Fall> = runCatching {
        val gesehen = HashSet<String>()
        json.parseToJsonElement(text).jsonObject["results"]!!.jsonArray.mapNotNull { e ->
            val o = e.jsonObject
            val titel = entitaeten(o.text("title") ?: return@mapNotNull null)
            if (!dojPasst(firma, titel)) return@mapNotNull null
            val sek = o.text("date")?.toLongOrNull() ?: return@mapNotNull null
            val datum = LocalDate.ofEpochDay(sek / 86_400L)
            if (datum < ab) return@mapNotNull null
            // Dieselbe Mitteilung erscheint oft bei mehreren Staatsanwaltschaften
            if (!gesehen.add(woerter(titel).joinToString(" "))) return@mapNotNull null
            val abt = runCatching { o["component"]!!.jsonArray.joinToString(" ") { it.jsonObject.text("name").orEmpty() } }.getOrDefault("")
            val link = o.text("url") ?: return@mapNotNull null
            val vergleich = woerter(titel).any { it in VERGLEICH }
            Fall(
                Quelle.DOJ, Art.BEHOERDE, titel, "DOJ", gebiet("$titel $abt"), Rolle.BEKLAGTER, datum.toString(),
                if (vergleich) Stand.ABGESCHLOSSEN else Stand.UNBEKANNT, betragAusTitel(titel), link,
            )
        }
    }.getOrDefault(emptyList())
}

// Abruf: EU-Gerichte, CMA und Justizministerium ohne Schluessel, CourtListener mit Token. Ergebnisse bleiben zwoelf Stunden
// im Speicher.
object KlagenAbruf {
    private val api by lazy { MarketApi() }
    private val sperre = Mutex()
    private val speicher = ConcurrentHashMap<String, Pair<Long, Klagen.Ergebnis>>()
    private const val GUELTIG_MS = 12 * 3_600_000L

    private suspend fun text(url: String, max: Int, lesezeitS: Int = 0): String? {
        val a = runCatching { api.anfrage(url, mapOf("Accept" to "application/json"), max = max, lesezeitS = lesezeitS) }.getOrNull() ?: return null
        return a.takeIf { it.ok }?.text
    }

    // Ohne Schluessel: EU, CMA, DOJ. Leeres Ergebnis, wenn nichts gefunden wurde oder der Name nicht eindeutig ist.
    // Die drei Stellen laufen gleichzeitig; teil meldet CMA und DOJ, sobald beide da sind, denn die EU-Abfrage braucht oft
    // ueber eine Minute.
    suspend fun laden(
        firma: String, jetzt: Long = System.currentTimeMillis(), heute: LocalDate = LocalDate.now(java.time.ZoneOffset.UTC),
        teil: (Klagen.Ergebnis) -> Unit = {},
    ): Klagen.Ergebnis {
        val n = Klagen.suchname(firma) ?: return Klagen.Ergebnis(emptyList())
        val schluessel = "frei|" + n.lowercase()
        speicher[schluessel]?.takeIf { jetzt - it.first < GUELTIG_MS }?.let { return it.second }
        return sperre.withLock {
            speicher[schluessel]?.takeIf { jetzt - it.first < GUELTIG_MS }?.let { return@withLock it.second }
            val ab = heute.minusYears(10)
            coroutineScope {
                val eu = async { Klagen.euAdresse(firma, ab)?.let { u -> text(u, 3_000_000, lesezeitS = 70)?.let { Klagen.euLesen(it, firma) } }.orEmpty() }
                val cma = async { Klagen.cmaAdresse(firma)?.let { u -> text(u, 3_000_000)?.let { Klagen.cmaLesen(it, firma) } }.orEmpty() }
                val doj = async { Klagen.dojAdresse(firma)?.let { u -> text(u, 3_000_000)?.let { Klagen.dojLesen(it, firma, ab) } }.orEmpty() }
                val schnell = cma.await() + doj.await()
                if (schnell.isNotEmpty()) teil(Klagen.Ergebnis(schnell))
                val e = Klagen.Ergebnis(schnell + eu.await())
                speicher[schluessel] = jetzt to e
                e
            }
        }
    }

    // Mit Token: bis zu zwei Seiten Akten und eine Seite Urteile (drei Abrufe, das Kontingent erlaubt vier je Minute).
    // holen liefert den Text einer CourtListener-Adresse oder null (Zugang abgelehnt, Limit, Netz).
    suspend fun gericht(
        firma: String, token: String, holen: suspend (String) -> String?,
        jetzt: Long = System.currentTimeMillis(),
    ): Klagen.Ergebnis? {
        val n = Klagen.suchname(firma) ?: return null
        if (token.isBlank()) return null
        val schluessel = "cl|" + n.lowercase() + "|" + token.hashCode()
        speicher[schluessel]?.takeIf { jetzt - it.first < GUELTIG_MS }?.let { return it.second }
        return sperre.withLock {
            speicher[schluessel]?.takeIf { jetzt - it.first < GUELTIG_MS }?.let { return@withLock it.second }
            val aus = ArrayList<Klagen.Fall>()
            var url = Klagen.clAdresse(firma, false)
            var mehr = false
            var seiten = 0
            while (url != null && seiten < 2) {
                val t = holen(url) ?: return@withLock null
                val s = Klagen.clLesen(t, firma, false)
                aus += s.faelle
                url = s.weiter
                seiten++
            }
            if (url != null) mehr = true
            Klagen.clAdresse(firma, true)?.let { u -> holen(u)?.let { aus += Klagen.clLesen(it, firma, true).faelle } }
            val e = Klagen.Ergebnis(aus.distinctBy { it.link.ifBlank { it.titel + it.datum } }, mehr)
            speicher[schluessel] = jetzt to e
            e
        }
    }
}
