package de.troetenrepublik.app.market

import de.troetenrepublik.app.data.Candle
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate

// Warnzeichen und Uebernahmen aus den Meldungen eines Emittenten bei der SEC [0.77]: Die Liste data.sec.gov/submissions
// nennt je 8-K die Items (4.01 Pruefer, 4.02 Korrektur, 5.02 Fuehrung, 3.01 Notierung, 1.03 Insolvenz, 2.06
// Abschreibung, 2.04 vorzeitig faellige Schuld, 1.01 Vereinbarung, 2.01 Vollzug). Dazu kommen die Formulare zu
// Fusionen und Angeboten (425, DEFM14A, SC TO-T, SC 14D9). Hier steht nur Lesen und Erkennen als reine Funktionen;
// der Abruf steht in AchtKAbruf.
object AchtK {
    const val MONATE = 24
    const val VORLAUF_TAGE = 60

    // Arten der Warnzeichen mit dem Item der 8-K; schwer: Korrektur, Delisting und Insolvenz.
    enum class Art(val item: String, val schwer: Boolean) {
        PRUEFER("4.01", false), KORREKTUR("4.02", true), FUEHRUNG("5.02", false), DELISTING("3.01", true),
        INSOLVENZ("1.03", true), ABSCHREIBUNG("2.06", false), VERBINDLICHKEIT("2.04", false),
    }

    // CEO oder CFO im Abgang; KEINE: gelesen, aber weder CEO noch CFO betroffen; UNBEKANNT: Dokument nicht lesbar
    // oder Rolle nicht sicher zu bestimmen.
    enum class Rolle { KEINE, CEO, CFO, CEO_CFO, UNBEKANNT }

    data class Meldung(val accession: String, val form: String, val eingang: String, val items: Set<String>, val datei: String)

    // Einordnung eines Items 3.01 (Notierung).
    enum class Unterart { ANGEDROHT, REGEL, BESCHLOSSEN, WECHSEL, ENTWARNUNG }

    // Grund einer Notierungsmeldung.
    enum class Grund { BERICHT, KURS, EIGENKAPITAL, AUSSCHUSS, VERSAMMLUNG }

    // Was in der Meldung steht: Abgang (5.02), Pruefer (4.01).
    enum class Ereignis { RUECKTRITT, ENTLASSUNG, ABGANG, PRUEFER_RUECKTRITT, PRUEFER_ENTLASSUNG, PRUEFER_NEU }

    // Aus dem Text der Meldung gelesene Angaben, je nach Art nur ein Teil davon. berichte: "10-K;2024", "10-Q;09/2024".
    // frist: ISO-Datum oder "180d". wirksam: ISO-Datum.
    @Serializable
    data class Detail(
        val unterart: Unterart? = null,
        val grund: Grund? = null,
        val berichte: List<String> = emptyList(),
        val frist: String = "",
        val person: String = "",
        val ereignis: Ereignis? = null,
        val sofort: Boolean = false,
        val wirksam: String = "",
        val nachfolge: Boolean? = null,
        val pruefer: String = "",
        val neuerPruefer: String = "",
        val streit: Boolean = false,
        val maengel: Boolean = false,
        val jahrVon: Int = 0,
        val jahrBis: Int = 0,
        val kapitel: Int = 0,
        val betrag: Double = 0.0,
    )

    data class Warnzeichen(val art: Art, val tag: String, val rolle: Rolle, val accession: String, val datei: String, val detail: Detail? = null)

    enum class VorgangsArt { ANGEKUENDIGT, ANGEBOT, ABGESCHLOSSEN }

    // gruppe: laufende Nummer des Vorgangs (Ankuendigung, Angebot und Vollzug eines Geschaefts haben dieselbe).
    data class Vorgang(
        val art: VorgangsArt, val tag: String, val gegenseite: String?, val preis: Double?,
        val accession: String, val datei: String, val gruppe: Int,
    )

    // Gelesene Angaben eines Dokuments zu einer Vereinbarung oder einem Vollzug.
    @Serializable
    data class DealDoc(val vereinbarung: Boolean, val abschluss: Boolean, val gegenseite: String?, val preis: Double?)

    private val json = Json { ignoreUnknownKeys = true }

    // ------------------------------------------------------------ Liste der Meldungen

    fun submissionsAdresse(cik: String) = SecFormen.submissionsAdresse(cik)

    fun name(text: String): String? = runCatching { json.parseToJsonElement(text).jsonObject["name"]?.jsonPrimitive?.contentOrNull }.getOrNull()

    // Alle Meldungen der Liste „recent“ mit Formular, Items, Eingangstag und Hauptdokument.
    fun meldungen(text: String): List<Meldung> = runCatching {
        val r = json.parseToJsonElement(text).jsonObject["filings"]?.jsonObject?.get("recent")?.jsonObject ?: return emptyList()
        fun spalte(k: String) = r[k]?.jsonArray?.map { it.jsonPrimitive.contentOrNull.orEmpty() }.orEmpty()
        val formen = spalte("form"); val nummern = spalte("accessionNumber"); val tage = spalte("filingDate")
        val items = spalte("items"); val dateien = spalte("primaryDocument")
        formen.indices.mapNotNull { i ->
            val nr = nummern.getOrNull(i)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Meldung(
                nr, formen[i], tage.getOrNull(i).orEmpty(),
                items.getOrNull(i).orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
                dateien.getOrNull(i).orEmpty().substringAfterLast('/'),
            )
        }
    }.getOrDefault(emptyList())

    fun grenze(heute: LocalDate): String = heute.minusMonths(MONATE.toLong()).toString()

    // ------------------------------------------------------------ Warnzeichen

    // Arten, die eine 8-K meldet. Beim Vollzug einer Uebernahme (2.01 mit Kontrollwechsel oder Notierungsende) sind
    // Delisting-Hinweis und Abgang der Fuehrung die Folge des Geschaefts und kein Warnzeichen.
    fun arten(m: Meldung): List<Art> {
        if (m.form != "8-K") return emptyList()
        val vollzug = "2.01" in m.items && ("5.01" in m.items || "3.01" in m.items)
        return Art.entries.filter { it.item in m.items }.filter { !(vollzug && (it == Art.DELISTING || it == Art.FUEHRUNG)) }
    }

    // 8-K mit Item 5.02 der letzten 24 Monate, deren Dokument noch nicht gelesen ist, neueste zuerst.
    fun fuehrungsKandidaten(liste: List<Meldung>, heute: LocalDate, schon: Set<String>, hoechstens: Int): List<Meldung> {
        val ab = grenze(heute)
        return liste.filter { it.eingang >= ab && Art.FUEHRUNG in arten(it) && it.accession !in schon }
            .sortedByDescending { it.eingang }.take(hoechstens)
    }

    // Warnzeichen der letzten 24 Monate, neueste zuerst. 5.02 zaehlt nur mit gelesener Rolle (CEO oder CFO) oder, wenn
    // das Dokument nicht lesbar war (UNBEKANNT), als Wechsel in der Fuehrung ohne Rolle; ungelesene und nicht
    // betroffene Meldungen fehlen.
    fun warnzeichen(liste: List<Meldung>, heute: LocalDate, rollen: Map<String, Rolle>, details: Map<String, Detail> = emptyMap()): List<Warnzeichen> {
        val ab = grenze(heute)
        val alle = liste.filter { it.eingang >= ab }.sortedByDescending { it.eingang }.flatMap { m ->
            arten(m).mapNotNull { art ->
                if (art != Art.FUEHRUNG) return@mapNotNull Warnzeichen(art, m.eingang, Rolle.KEINE, m.accession, m.datei, details[detailKey(m.accession, art)])
                val r = rollen[m.accession] ?: return@mapNotNull null
                if (r == Rolle.KEINE) null else Warnzeichen(art, m.eingang, r, m.accession, m.datei, details[detailKey(m.accession, art)])
            }
        }
        return ohneFolgemeldungen(alle)
    }

    // Ein Abgang, mehrere Meldungen (Ankuendigung, spaeter Nachfolge): gleiche Rolle und gleicher Tag der Wirkung
    // zaehlen einmal. Es bleibt die erste Meldung, Name und Nachfolge kommen aus den spaeteren dazu.
    internal fun ohneFolgemeldungen(liste: List<Warnzeichen>): List<Warnzeichen> {
        fun schluessel(w: Warnzeichen): String? =
            w.detail?.wirksam?.takeIf { w.art == Art.FUEHRUNG && it.isNotEmpty() }?.let { w.rolle.name + "|" + it }
        val gruppen = liste.filter { schluessel(it) != null }.groupBy { schluessel(it)!! }.filterValues { it.size > 1 }
        if (gruppen.isEmpty()) return liste
        return liste.mapNotNull { w ->
            val g = gruppen[schluessel(w)] ?: return@mapNotNull w
            val erste = g.minBy { it.tag }
            if (w !== erste) return@mapNotNull null
            val d = w.detail!!
            w.copy(detail = d.copy(
                person = d.person.ifEmpty { g.firstNotNullOfOrNull { it.detail?.person?.takeIf { p -> p.isNotEmpty() } }.orEmpty() },
                nachfolge = if (g.any { it.detail?.nachfolge == true }) true else d.nachfolge,
            ))
        }
    }

    fun detailKey(accession: String, art: Art): String = accession + "|" + art.name

    // Einordnung eines Warnzeichens fuer das Zaehlen im Kopf, die Waage und das Widget: Boersenwechsel und Entwarnung
    // zu Item 3.01 sind keine Warnzeichen. Ungelesene Meldungen zaehlen wie gemeldet.
    fun zaehlt(w: Warnzeichen): Boolean =
        !(w.art == Art.DELISTING && (w.detail?.unterart == Unterart.WECHSEL || w.detail?.unterart == Unterart.ENTWARNUNG))

    fun anzahl(liste: List<Warnzeichen>): Int = liste.count { zaehlt(it) }

    // Schwere Warnzeichen (Korrektur, Delisting-Hinweis, Insolvenz) ohne Boersenwechsel und Entwarnung.
    fun schwereAnzahl(liste: List<Warnzeichen>): Int = liste.count { it.art.schwer && zaehlt(it) }

    // ------------------------------------------------------------ Text einer Meldung

    private val QUOTES = Regex("[\u201C\u201D\u201E\u00AB\u00BB\uFFFD]")
    private val APOSTROPH = Regex("[\u2018\u2019]")

    // Sichtbarer Text eines 8-K-Dokuments in einer Zeile: ohne die versteckten XBRL-Angaben, Entitaeten aufgeloest,
    // typografische Anfuehrungszeichen als gerade.
    fun klartext(html: String): String {
        val ohne = html.replace(Regex("(?is)<ix:header>.*?</ix:header>"), " ")
        return InsiderQuellen.htmlZeilen(ohne).joinToString(" ")
            .replace(QUOTES, "\"").replace(APOSTROPH, "'").replace(Char(0xA0), ' ').replace(Regex("\\s+"), " ").trim()
    }

    // Text eines Items bis zum naechsten Item oder zur Unterschrift. Die Ueberschrift steht vor einem Grossbuchstaben
    // ("Item 5.02 Departure of ..."), Verweise im Text sind klein weiter ("Item 5.02 of this Current Report"); ohne
    // Ueberschrift gilt der ganze Text.
    fun abschnitt(text: String, item: String): String {
        val kopf = Regex("(?i)\\bItem\\s+" + Regex.escape(item) + "\\.?(?:\\([a-z]\\))?\\s+(\\S)")
        val start = kopf.findAll(text).firstOrNull { it.groupValues[1][0].isUpperCase() }?.range?.first ?: return text
        val rest = text.substring(start + 5)
        val ende = Regex("(?i)\\bItem\\s+\\d\\.\\d\\d\\.?(?:\\([a-z]\\))?\\s+(\\S)|\\bSIGNATURES?\\b").findAll(rest)
            .firstOrNull { it.groupValues[1].isEmpty() || it.groupValues[1][0].isUpperCase() }?.range?.first ?: rest.length
        return text.substring(start, start + 5 + ende)
    }

    // Titel des Items 5.02 (in Varianten): enthaelt „Departure“ und gehoert nicht zum Inhalt.
    internal val TITEL_502 = Regex("(?i)item\\s+5\\.02\\.?\\s+departure of directors or certain officers;.*?compensatory arrangements of certain officers\\.?")

    // ------------------------------------------------------------ Fuehrung: CEO oder CFO

    internal val CEO = Regex("(?i)chief executive officer|chief executive|\\bceo\\b|principal executive officer")
    internal val CFO = Regex("(?i)chief financial officer|chief financial|\\bcfo\\b|principal financial officer")

    // Abgang: zurueckgetreten, abgetreten, in den Ruhestand, entlassen, ausgeschieden. Die Hauptwoerter stehen ohne
    // „termination“ als Hauptwort, damit Abfindungsklauseln nicht zaehlen.
    internal val ABGANG = Regex(
        "(?i)\\b(resign(s|ed|ing|ation)?|step(s|ped|ping)? down|retire(s|d|ment)?|depart(s|ed|ure)?|" +
            "(will|to|has|have|had) (leave|left)|terminated (his|her|\\w+'s) employment|employment (was|has been|had been) terminated|" +
            "(was|were|been|is|are) (terminated|removed|dismissed|fired|ousted|replaced)|ceas(e|ed|es|ing) (to serve|to be|serving)|" +
            "no longer (serve|serving|be|employed)|separat(ed|ion) from the company)\\b",
    )
    internal val RENTE = Regex("(?i)retirement (plan|savings|benefit|account|age)s?")

    // Formel zur Amtsdauer bei einer Berufung ("until his or her earlier death, resignation, retirement,
    // disqualification or removal"): nennt Abgangswoerter, meldet aber keinen Abgang.
    private val AMTSDAUER = Regex(
        "(?i)\\buntil\\s+(?:(?:his|her|their|his or her|such \\w+'s)\\s+)?(?:(?:earlier|prior)\\s+)?" +
            "(?:death|resignation|retirement|disqualification|removal|incapacity)" +
            "(?:\\s*,?\\s*(?:or\\s+)?(?:death|resignation|retirement|disqualification|removal|incapacity))*",
    )

    // Text des Items 5.02 ohne die Ueberschrift und ohne Formeln zur Amtsdauer.
    internal fun text502(klartext: String): String = abschnitt(klartext, "5.02").replace(TITEL_502, " ").replace(AMTSDAUER, " ")

    // Ruecktritt nur vom Sitz im Board ("resigned as a Class II director", "resigned from the Board"): kein Abgang aus
    // dem Amt, auch wenn der Satz die Person als CEO oder CFO vorstellt.
    private val NUR_BOARD = Regex(
        "(?i)^\\W*(?:(?:from\\s+)?(?:his|her|their)\\s+(?:position|role|seat)\\s+)?" +
            "(?:as\\s+(?:an?\\s+)?(?:Class\\s+[IV]+\\s+)?(?:director|member of the Board)|from\\s+the\\s+Board)\\b",
    )

    internal fun nurBoard(satz: String): Boolean {
        val m = ABGANG.findAll(satz).firstOrNull { RENTE.find(satz, it.range.first)?.range?.first != it.range.first } ?: return false
        val nach = satz.substring(m.range.last + 1)
        return NUR_BOARD.containsMatchIn(nach) && !CEO.containsMatchIn(nach) && !CFO.containsMatchIn(nach)
    }

    // Klauseln, die einen Abgang nur als Bedingung nennen (Abfindung, Vergütung bei Beendigung).
    internal val BEDINGUNG = Regex(
        "(?i)\\b(in the event (of|that)|if (he|she|the|his|her|such|a|any)\\b|should (he|she)\\b|in case of|" +
            "upon (his|her|a|any|the)( \\w+){0,2} (termination|resignation|retirement|departure|death)|following (a|any) (change|termination))",
    )

    private val ABKUERZUNGEN = listOf("Mr.", "Mrs.", "Ms.", "Dr.", "Inc.", "Corp.", "Co.", "Ltd.", "U.S.", "No.", "Jr.", "Sr.", "St.", "Messrs.", "M.D.", "Ph.D.", "L.L.C.", "L.P.")

    fun saetze(text: String, beiSemikolon: Boolean = true): List<String> {
        var s = text
        ABKUERZUNGEN.forEach { a -> s = s.replace(a, a.replace('.', '\u0001')) }
        val aus = ArrayList<String>()
        var start = 0
        var i = 0
        while (i < s.length) {
            val c = s[i]
            // Ein einzelner Grossbuchstabe vor dem Punkt ist ein Namenskuerzel ("David L. Calhoun"), kein Satzende
            val kuerzel = c == '.' && i >= 1 && s[i - 1].isUpperCase() && (i == 1 || s[i - 2] == ' ')
            if ((c == '.' || (c == ';' && beiSemikolon)) && !kuerzel && (i + 1 == s.length || s[i + 1] == ' ')) {
                aus += s.substring(start, i + 1).replace('\u0001', '.').trim()
                start = i + 1
            }
            i++
        }
        if (start < s.length) aus += s.substring(start).replace('\u0001', '.').trim()
        return aus.filter { it.isNotEmpty() }
    }

    // Nach der Rolle steht oft die Einheit: "Chief Executive Officer of Boeing Defense, Space & Security" oder
    // "Chief Executive Officer, Boeing Commercial Airplanes" meint den Chef einer Sparte. Die Rolle gilt fuer die Firma,
    // wenn keine Einheit folgt oder die Einheit die Firma selbst ist ("of the Company", Name des Emittenten).
    private val ROLLE_FOLGE = Regex("^\\s*(?:of|at|,)\\s+(?!effective\\b|as of\\b|and\\b|who\\b|but\\b|which\\b|\\d)(?:the\\s+)?([A-Z][^,.;]{1,70})")
    private val FIRMA_WORTE = setOf("company", "corporation", "registrant", "issuer")
    private val NAMENSFUELL = setOf("the", "company", "corporation", "inc", "corp", "co", "ltd", "plc")

    private fun kern(name: String): List<String> = Firmenname.worte(name).filter { it !in NAMENSFUELL }

    internal fun rolleGiltFuerFirma(satz: String, muster: Regex, emittent: String): Boolean {
        val eigen = kern(emittent)
        return muster.findAll(satz).any { m ->
            val x = ROLLE_FOLGE.find(satz.substring(m.range.last + 1))?.groupValues?.get(1)?.trim() ?: return@any true
            x.lowercase() in FIRMA_WORTE || (eigen.isNotEmpty() && kern(x) == eigen)
        }
    }

    // Antritt: ein Satz ueber eine Ernennung nennt die Rolle des Neuen, nicht eines Abgangs.
    internal val ANTRITT = Regex("(?i)\\b(appoint\\w*|elect\\w*|named|succe\\w+|replac\\w+|join\\w*|promot\\w*)\\b")

    // Rolle aus dem Text des Items 5.02: Ein Satz mit Abgang und CEO oder CFO der Firma nennt die Rolle sicher; die Chefs
    // von Sparten zaehlen nicht. Steht die Rolle nur in einem Nachbarsatz des Abgangs, ist es
    // nicht sicher (UNBEKANNT). Ohne Abgang oder ohne Rolle im Item ist weder CEO noch CFO betroffen.
    fun rolleLesen(html: String, emittent: String = ""): Rolle {
        val t = klartext(html)
        if (t.isBlank()) return Rolle.UNBEKANNT
        val s = text502(t)
        val liste = saetze(s)
        var ceo = false
        var cfo = false
        var rolleImSatz = false
        var unsicher = false
        liste.forEachIndexed { i, satz ->
            val abgang = ABGANG.containsMatchIn(satz.replace(RENTE, " ")) && !BEDINGUNG.containsMatchIn(satz) && !nurBoard(satz)
            if (!abgang) return@forEachIndexed
            if (CEO.containsMatchIn(satz) || CFO.containsMatchIn(satz)) rolleImSatz = true
            if (rolleGiltFuerFirma(satz, CEO, emittent)) ceo = true
            if (rolleGiltFuerFirma(satz, CFO, emittent)) cfo = true
            // Die Rolle steht im Nachbarsatz (etwa "Mr. Smith has served as CFO since 2019"), nicht in einer Ernennung
            if (listOf(i - 1, i + 1).mapNotNull { liste.getOrNull(it) }.any { (CEO.containsMatchIn(it) || CFO.containsMatchIn(it)) && !ANTRITT.containsMatchIn(it) }) unsicher = true
        }
        return when {
            ceo && cfo -> Rolle.CEO_CFO
            ceo -> Rolle.CEO
            cfo -> Rolle.CFO
            unsicher && !rolleImSatz -> Rolle.UNBEKANNT
            else -> Rolle.KEINE
        }
    }

    // ------------------------------------------------------------ Uebernahmen

    private val MERGER_FORMEN = setOf("425", "DEFM14A", "DEFM14C")

    private fun angebotsForm(form: String): Boolean {
        val f = form.uppercase()
        if (f.endsWith("/A")) return false
        return f.startsWith("SC TO-T") || f.startsWith("SCHEDULE TO-T") || f == "SC 14D9" || f == "SCHEDULE 14D-9"
    }

    private fun mergerForm(form: String): Boolean = form.uppercase() in MERGER_FORMEN

    // 8-K, deren Dokument gelesen werden soll: Vollzug (2.01) zuerst, dann Vereinbarungen (1.01) nahe an Fusionsformularen,
    // dann 1.01 ohne Kredit-Items (2.03, 3.02). 1.01 mit 2.03 allein ist meist ein Kreditvertrag und bleibt ungelesen.
    fun dealKandidaten(liste: List<Meldung>, heute: LocalDate, schon: Set<String>, hoechstens: Int): List<Meldung> {
        val ab = grenze(heute)
        val formTage = liste.filter { it.eingang >= ab && (mergerForm(it.form) || angebotsForm(it.form) || it.form.uppercase().startsWith("SC14D9C")) }
            .mapNotNull { runCatching { LocalDate.parse(it.eingang) }.getOrNull() }
        fun nahe(tag: String): Boolean {
            val d = runCatching { LocalDate.parse(tag) }.getOrNull() ?: return false
            return formTage.any { kotlin.math.abs(it.toEpochDay() - d.toEpochDay()) <= 10 }
        }
        fun rang(m: Meldung): Int = when {
            "2.01" in m.items -> 0
            "1.01" in m.items && nahe(m.eingang) -> 1
            "1.01" in m.items && "2.03" !in m.items && "3.02" !in m.items -> 2
            else -> -1
        }
        return liste.filter { it.form == "8-K" && it.eingang >= ab && it.accession !in schon && rang(it) >= 0 }
            .sortedWith(compareBy<Meldung> { rang(it) }.thenByDescending { it.eingang }).take(hoechstens)
    }

    // Vereinbarung: der Text nennt einen Fusions-, Kauf- oder Angebotsvertrag. Kreditvertraege reden von „acquisitions“
    // nur als erlaubtem Zweck und haben diese Begriffe nicht.
    private val VEREINBARUNG = Regex(
        "(?i)agreement and plan of merger|merger agreement|plan of merger|agreement and plan of reorganization|business combination agreement|" +
            "\\b(stock|share|equity|asset|membership interest|unit)s? purchase agreement|transaction agreement|tender offer|offer to purchase|" +
            "to be acquired by|will merge with|merge with and into|\\bmerger sub\\b",
    )

    private val KERN = Regex(
        "(?i)\\b(merger|acquisition|acquire[sd]?|tender offer|the offer|offer consideration|business combination|transaction agreement|purchase agreement|purchaser)\\b",
    )

    // Preis je Aktie: nur ein einziger Dollarbetrag mit „per share“ oder „in cash“ nach Wendungen wie „right to receive“
    // oder „in exchange for“. Bei mehreren Betraegen oder bedingten Zusatzrechten (CVR) steht kein Preis.
    private val PREIS = Regex(
        "(?i)(?:right to receive|in exchange for|for|price of|consideration of|receive|payable)\\s+\\$\\s?(\\d{1,4}(?:,\\d{3})*(?:\\.\\d{1,4})?)\\s*(?:per\\s+(?:common\\s+|ordinary\\s+)?(?:share|ads)\\b|in cash)",
    )

    fun preisLesen(abschnitt: String): Double? {
        if (Regex("(?i)contingent value right|\\bCVRs?\\b").containsMatchIn(abschnitt)) return null
        val betraege = PREIS.findAll(abschnitt).mapNotNull { it.groupValues[1].replace(",", "").toDoubleOrNull() }.filter { it > 0 }.toSet()
        return betraege.singleOrNull()
    }

    // Partei mit Beschreibung und Kurzname in Klammern: „Hewlett Packard Enterprise Company, a Delaware corporation
    // („HPE“ or „Parent“)“. Die Beschreibung darf eine Klammer enthalten („( Aktieselskab )“).
    private val PARTEI = Regex(
        "([A-Z][\\w&.'\\-/]*(?: (?:[A-Z0-9&][\\w&.'\\-/]*|of|and|de|van|von|the|for))*(?:,\\s+(?:Inc|LLC|L\\.L\\.C|Ltd|Corp|Co|LP|L\\.P|LLP)\\.?)?),\\s+(?:a|an)\\s+" +
            "((?:[^()]|\\([^()]{0,40}\\)){3,220}?)" +
            "\\(\\s*(?:the\\s+)?\"\\s*([A-Za-z][A-Za-z ]{1,30}?)\\s*\"\\s*(?:or\\s+(?:the\\s+)?\"\\s*([A-Za-z][A-Za-z ]{1,30}?)\\s*\"\\s*)?\\)",
    )
    private val HAUPT_ROLLEN = setOf("parent", "buyer", "acquirer", "acquiror", "acquiring company", "seller", "target")

    // Gegenseite des Geschaefts: die Partei mit der Rolle Parent, Buyer, Acquirer, Seller oder Target, die nicht der
    // Emittent selbst ist. Nur wenn es genau eine solche gibt; sonst null.
    fun gegenseiteLesen(abschnitt: String, emittent: String): String? {
        val eigen = Firmenname.worte(emittent)
        val namen = PARTEI.findAll(abschnitt).mapNotNull { m ->
            val roh = m.groupValues[1].trim().trimEnd(',', '.', ' ').let { if (it.endsWith(" Inc") || it.endsWith(" Corp") || it.endsWith(" Co")) "$it." else it }
            val rollen = listOf(m.groupValues[3], m.groupValues[4]).map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            if (rollen.none { it in HAUPT_ROLLEN }) return@mapNotNull null
            if (roh.length < 3 || roh.length > 60 || roh.first().isLowerCase() || roh.startsWith("The ")) return@mapNotNull null
            val w = Firmenname.worte(roh)
            if (w.isEmpty() || (eigen.isNotEmpty() && (w == eigen || (w.size >= 2 && eigen.take(w.size) == w) || (eigen.size >= 2 && w.take(eigen.size) == eigen)))) return@mapNotNull null
            roh
        }.toSet()
        return namen.singleOrNull()
    }

    private val KOPFZEILE_201 = Regex("(?i)item\\s+2\\.01\\s+completion of acquisition or disposition of assets\\.?")

    // Angaben eines 8-K-Dokuments. item: 1.01 (Vereinbarung) oder 2.01 (Vollzug). Beim Vollzug zaehlt jeder Text mit
    // Begriffen eines Kaufs oder einer Fusion; die Kopfzeile des Items nennt „Acquisition“ immer und wird entfernt.
    fun dealLesen(html: String, item: String, emittent: String): DealDoc {
        val t = klartext(html)
        val s = abschnitt(t, item)
        val ohneKopf = s.replace(KOPFZEILE_201, " ")
        val vereinbarung = item == "1.01" && VEREINBARUNG.containsMatchIn(s)
        val abschluss = item == "2.01" && (KERN.containsMatchIn(ohneKopf) || VEREINBARUNG.containsMatchIn(ohneKopf))
        if (!vereinbarung && !abschluss) return DealDoc(false, false, null, null)
        return DealDoc(vereinbarung, abschluss, gegenseiteLesen(s, emittent), preisLesen(s))
    }

    // Vorgaenge der letzten 24 Monate, neueste zuerst. docs enthaelt die gelesenen 8-K (Accession zu Angaben). Formulare
    // und Meldungen eines Geschaefts liegen hoechstens 150 Tage auseinander und bilden einen Vorgang mit gemeinsamer
    // Gegenseite und gemeinsamem Preis, je Art hoechstens einmal (die erste).
    fun vorgaenge(liste: List<Meldung>, docs: Map<String, DealDoc>, heute: LocalDate): List<Vorgang> {
        val ab = grenze(heute)
        class Roh(val art: VorgangsArt, val ausDoc: Boolean, val m: Meldung, val doc: DealDoc?)
        val roh = ArrayList<Roh>()
        for (m in liste) {
            if (m.eingang < ab) continue
            when {
                m.form == "8-K" -> {
                    val d = docs[m.accession] ?: continue
                    if ("2.01" in m.items && d.abschluss) roh += Roh(VorgangsArt.ABGESCHLOSSEN, true, m, d)
                    else if ("1.01" in m.items && d.vereinbarung) roh += Roh(VorgangsArt.ANGEKUENDIGT, true, m, d)
                }
                mergerForm(m.form) -> roh += Roh(VorgangsArt.ANGEKUENDIGT, false, m, null)
                angebotsForm(m.form) -> roh += Roh(VorgangsArt.ANGEBOT, false, m, null)
            }
        }
        val sortiert = roh.sortedBy { it.m.eingang }
        val gruppen = ArrayList<MutableList<Roh>>()
        var letzter: LocalDate? = null
        for (r in sortiert) {
            val d = runCatching { LocalDate.parse(r.m.eingang) }.getOrNull() ?: continue
            if (letzter == null || d.toEpochDay() - letzter.toEpochDay() > 150) gruppen.add(ArrayList())
            gruppen.last().add(r)
            letzter = d
        }
        val aus = ArrayList<Vorgang>()
        gruppen.forEachIndexed { nr, g ->
            val info = g.firstOrNull { it.doc != null && (it.doc.gegenseite != null || it.doc.preis != null) }?.doc
            fun erste(art: VorgangsArt): Roh? =
                if (art == VorgangsArt.ANGEKUENDIGT) g.firstOrNull { it.art == art && it.ausDoc } ?: g.firstOrNull { it.art == art }
                else g.firstOrNull { it.art == art }
            for (art in VorgangsArt.entries) {
                val r = erste(art) ?: continue
                // Ein Fusionsformular nach dem Angebot ist keine Ankuendigung mehr
                if (art == VorgangsArt.ANGEKUENDIGT && !r.ausDoc && (erste(VorgangsArt.ANGEBOT)?.m?.eingang ?: "9999") < r.m.eingang) continue
                val d = r.doc?.takeIf { it.gegenseite != null || it.preis != null } ?: info
                aus += Vorgang(art, r.m.eingang, d?.gegenseite, d?.preis, r.m.accession, r.m.datei, nr)
            }
        }
        return aus.sortedWith(compareByDescending<Vorgang> { it.tag }.thenByDescending { it.art.ordinal })
    }

    // ------------------------------------------------------------ Insider und Kurs

    data class Davor(val verkaeufer: Int, val summe: Double)

    // Verkaeufe von Insidern in den 60 Tagen bis zum Meldetag: Zahl der Personen und Summe in Dollar; null ohne Verkauf.
    fun insiderDavor(geschaefte: List<SecInsider.Geschaeft>, symbol: String, tag: String): Davor? {
        val ende = runCatching { LocalDate.parse(tag) }.getOrNull() ?: return null
        val von = ende.minusDays(VORLAUF_TAGE.toLong()).toString()
        val v = geschaefte.filter { !it.kauf && it.wert > 0 && it.k.equals(symbol, true) && it.d >= von && it.d <= tag }
        if (v.isEmpty()) return null
        return Davor(v.map { it.p.trim().lowercase() }.distinct().size, v.sumOf { it.wert })
    }

    // Kursbewegung in Prozent zum Schluss des Vortags: am Meldetag (oder dem ersten Handelstag danach) und fuenf
    // Handelstage danach. Je Wert null ohne Kurs.
    data class Reaktion(val tag0: Double, val tag5: Double?)

    fun reaktion(kerzen: List<Candle>, tag: String): Reaktion? {
        val tagD = runCatching { LocalDate.parse(tag) }.getOrNull() ?: return null
        val soll = tagD.toEpochDay()
        val tage = kerzen.map { Math.floorDiv(it.t, 86_400_000L) }
        val i = tage.indexOfFirst { it >= soll }
        if (i < 1 || tage[i] - soll > 4) return null
        val vor = kerzen[i - 1].close
        if (vor <= 0) return null
        val t0 = (kerzen[i].close / vor - 1) * 100
        val t5 = kerzen.getOrNull(i + 5)?.let { (it.close / vor - 1) * 100 }
        return Reaktion(t0, t5)
    }
}
