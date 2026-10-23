package de.troetenrepublik.app.market

import de.troetenrepublik.app.market.EuInsider.Geschaeft
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Insidergeschaefte weiterer Laender (seit 5.9): Frankreich (AMF, Base des decisions et informations financieres),
// Niederlande (AFM, Register Transacties leidinggevenden) und Grossbritannien (FCA, National Storage Mechanism). Alle
// ohne Anmeldung. Dieselben Leser dienen der App (neue Meldungen der letzten Tage) und dem Werkzeug, das den
// mitgelieferten Stand baut (tools/insider, Desktop-Aufgabe insiderBuendel).
object InsiderQuellen {
    // Abruf von Text und Bytes; die App und das Werkzeug geben MarketApi hinein.
    interface Netz {
        suspend fun text(url: String, kopf: Map<String, String> = emptyMap(), json: String? = null): String?
        suspend fun bytes(url: String): ByteArray?
    }

    fun netz(api: MarketApi) = object : Netz {
        override suspend fun text(url: String, kopf: Map<String, String>, json: String?) =
            api.anfrage(url, kopf, json = json).takeIf { it.ok }?.text
        override suspend fun bytes(url: String) = api.datei(url)
    }

    // ------------------------------------------------------------ Hilfen

    private val MONATE = mapOf(
        "jan" to 1, "january" to 1, "janvier" to 1, "januari" to 1, "feb" to 2, "february" to 2, "fevrier" to 2, "février" to 2, "februari" to 2,
        "mar" to 3, "march" to 3, "mars" to 3, "mrt" to 3, "maart" to 3, "apr" to 4, "april" to 4, "avril" to 4,
        "may" to 5, "mai" to 5, "mei" to 5, "jun" to 6, "june" to 6, "juin" to 6, "juni" to 6,
        "jul" to 7, "july" to 7, "juillet" to 7, "juli" to 7, "aug" to 8, "august" to 8, "aout" to 8, "août" to 8, "augustus" to 8,
        "sep" to 9, "sept" to 9, "september" to 9, "septembre" to 9, "oct" to 10, "october" to 10, "octobre" to 10, "okt" to 10, "oktober" to 10,
        "nov" to 11, "november" to 11, "novembre" to 11, "dec" to 12, "december" to 12, "decembre" to 12, "décembre" to 12,
    )

    private fun iso(j: String, m: Int, t: Int) = "$j-${m.toString().padStart(2, '0')}-${t.toString().padStart(2, '0')}"

    // Datum als JJJJ-MM-TT aus „21 octobre 2026“, „22 October 2026“, „20 oct 2026“, „22/10/2026“ oder „2026-10-22“; sonst leer.
    fun tag(text: String): String {
        val s = text.trim().lowercase()
        // Alle Schreibweisen suchen und die frueheste Fundstelle nehmen (Fusszeilen mit Formulardaten stehen spaeter)
        val treffer = mutableListOf<Pair<Int, String>>()
        Regex("(\\d{4})-(\\d{2})-(\\d{2})").find(s)?.let { treffer += it.range.first to it.value }
        Regex("(\\d{1,2})[/.](\\d{1,2})[/.](\\d{4})").find(s)?.let { m -> m.destructured.let { (t, mo, j) -> treffer += m.range.first to iso(j, mo.toInt(), t.toInt()) } }
        Regex("(\\d{1,2})\\s?(?:st|nd|rd|th|er)?\\s+([a-zéû]+)\\.?,?\\s+(\\d{4})").findAll(s).firstOrNull { MONATE.containsKey(it.groupValues[2]) }?.let { m ->
            m.destructured.let { (t, mo, j) -> treffer += m.range.first to iso(j, MONATE.getValue(mo), t.toInt()) }
        }
        Regex("([a-z]+)\\s+(\\d{1,2}),?\\s+(\\d{4})").findAll(s).firstOrNull { MONATE.containsKey(it.groupValues[1]) }?.let { m ->
            m.destructured.let { (mo, t, j) -> treffer += m.range.first to iso(j, MONATE.getValue(mo), t.toInt()) }
        }
        return treffer.minByOrNull { it.first }?.second.orEmpty()
    }

    // Zahl mit Tausenderpunkt und Dezimalkomma (1.624,00) oder umgekehrt (1,624.00, 13 771.0000).
    fun zahl(text: String): Double? {
        var s = text.replace(Char(0xA0), ' ').replace(Regex("[^0-9,.\\-]"), "")
        if (s.isEmpty()) return null
        val komma = s.lastIndexOf(',')
        val punkt = s.lastIndexOf('.')
        s = when {
            komma >= 0 && punkt >= 0 -> if (komma > punkt) s.replace(".", "").replace(',', '.') else s.replace(",", "")
            komma >= 0 -> if (s.length - komma - 1 == 3 && s.count { it == ',' } >= 1 && !s.startsWith("0,")) s.replace(",", "") else s.replace(',', '.')
            punkt >= 0 && s.count { it == '.' } > 1 -> s.replace(".", "")
            else -> s
        }
        return s.toDoubleOrNull()
    }

    private val ENTITAETEN = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ", "pound" to "£", "euro" to "€", "rsquo" to "'", "lsquo" to "'", "ldquo" to "\"", "rdquo" to "\"", "ndash" to "-", "mdash" to "-")

    fun entitaeten(s: String): String = Regex("&(#x[0-9a-fA-F]+|#\\d+|[a-zA-Z]+);").replace(s) { m ->
        val k = m.groupValues[1]
        when {
            k.startsWith("#x") || k.startsWith("#X") -> k.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
            k.startsWith("#") -> k.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
            else -> ENTITAETEN[k.lowercase()] ?: m.value
        }
    }

    // Sichtbarer Text einer Seite als Zeilen; Tabellenzellen werden eigene Zeilen.
    fun htmlZeilen(html: String): List<String> {
        var s = html.replace(Regex("(?is)<(script|style|head)\\b.*?</\\1>"), " ")
        s = s.replace(Regex("(?i)<(br|/p|/tr|/td|/th|/div|/li|/h\\d|/table)\\b[^>]*>"), "\n")
        s = entitaeten(s.replace(Regex("<[^>]+>"), " "))
        return s.split('\n').map { it.replace(Char(0xA0), ' ').replace(Regex("\\s+"), " ").trim() }.filter { it.isNotEmpty() }
    }

    private val ZUSATZ = setOf("de", "van", "von", "der", "den", "da", "di", "du", "le", "la", "bin", "al", "dos", "del", "y", "and", "of", "for", "to", "with", "in", "on", "the", "under")
    private val KURZWORT = setOf("at", "by", "an", "as", "is", "or", "of", "to", "in", "on", "a", "i", "o", "e", "et", "en", "el", "ii", "jr", "sr", "no", "mr", "ms", "dr", "pa", "co", "af", "zu", "op", "ap", "ul", "ud", "do", "da", "di")

    // Zerfallene Woerter zusammenfuegen („M ark Carpenter“, „C hief“): ein Stueck aus einem Buchstaben vor einem
    // kleingeschriebenen Rest gehoert zusammen; Namenszusaetze und kurze Woerter wie „de“, „of“ bleiben.
    fun ganz(text: String): String {
        val aus = mutableListOf<String>()
        for (w in text.split(' ').filter { it.isNotEmpty() }) {
            val vorher = aus.lastOrNull()
            // Letztes Stueck des vorigen Worts (nach einem Bindestrich, „Non-Ex ecutive“) ist hoechstens zwei Buchstaben lang
            val stueck = vorher?.substringAfterLast('-')
            if (vorher != null && stueck != null && stueck.length in 1..2 && stueck[0].isUpperCase() && stueck.all { it.isLetter() } &&
                w.first().isLowerCase() && w.all { it.isLetter() || it == '-' } && w !in ZUSATZ && stueck.lowercase() !in ZUSATZ)
                aus[aus.lastIndex] = vorher + w
            // Abgetrennte Endung eines Namens („Geoffr ey“): kurzes Kleinbuchstabenstueck, das kein eigenes Wort ist
            else if (vorher != null && vorher.length >= 3 && vorher[0].isUpperCase() && vorher.all { it.isLetter() } && vorher.last().isLowerCase() &&
                w.length in 1..2 && w.all { it.isLetter() && it.isLowerCase() } && w !in KURZWORT && w !in ZUSATZ)
                aus[aus.lastIndex] = vorher + w
            else aus += w
        }
        return aus.joinToString(" ")
    }

    // Waehrung, wenn die Meldung keine nennt: nach dem Land der ISIN (Londoner Notierungen in Pfund).
    private fun waehrungZurIsin(isin: String) = when (isin.take(2)) {
        "ES", "FR", "DE", "NL", "IT", "BE", "PT", "AT", "FI", "LU" -> "EUR"
        "US" -> "USD"
        "CH" -> "CHF"
        else -> "GBP"
    }

    private fun waehrungZumOrt(text: String): String {
        val t = text.lowercase()
        return when {
            Regex("\\basx\\b|australian securities exchange|\\bxasx\\b").containsMatchIn(t) -> "AUD"
            Regex("\\bnyse\\b|\\bnasdaq\\b|\\botc\\b|\\bxnys\\b|\\bxnas\\b|new york stock exchange").containsMatchIn(t) -> "USD"
            Regex("\\btsx\\b|toronto stock exchange|\\bxtse\\b").containsMatchIn(t) -> "CAD"
            Regex("\\bjse\\b|johannesburg|\\bxjse\\b").containsMatchIn(t) -> "ZAR"
            Regex("\\bhkex\\b|hong kong (stock )?exchange|\\bxhkg\\b").containsMatchIn(t) -> "HKD"
            Regex("euronext|\\bxpar\\b|\\bxams\\b|\\bxbru\\b|\\bxlis\\b|\\bxmad\\b|\\bxetra\\b|frankfurt|\\bxdub\\b|bolsa de madrid").containsMatchIn(t) -> "EUR"
            Regex("six swiss|\\bxswx\\b").containsMatchIn(t) -> "CHF"
            else -> ""
        }
    }

    // Aktien erkennen; Optionen, Anleihen, Zuteilungsrechte usw. bleiben „Sonstiges“.
    fun istAktie(instrument: String): Boolean {
        val s = instrument.lowercase()
        if (Regex("option|warrant|right|droit|recht|bond|obligation|note|award|unit|future|swap|cfd|certificat|certificate|psu|rsu|performance|restricted|fcpe|fonds|fund|sicav|\\bfie\\b|actionnariat|épargne|epargne").containsMatchIn(s)) return false
        return Regex("share|action|aandeel|aandelen|aktie|stock|equity|ordinary|depositary|adr|gdr|stapled").containsMatchIn(s)
    }

    private fun waehrung(s: String): String {
        val t = s.lowercase()
        return when {
            "euro" in t || "eur" in t || "€" in t -> "EUR"
            // Dollarzeichen anderer Laender („C$2.59“, „A$1.20“, „HK$“)
            Regex("(?<![a-z])(c|ca|can)\\$|\\bcad\\b|canadian dollar").containsMatchIn(t) -> "CAD"
            Regex("(?<![a-z])(a|au)\\$|\\baud\\b|australian dollar").containsMatchIn(t) -> "AUD"
            Regex("(?<![a-z])hk\\$|\\bhkd\\b|hong kong dollar").containsMatchIn(t) -> "HKD"
            Regex("(?<![a-z])s\\$|\\bsgd\\b|singapore dollar").containsMatchIn(t) -> "SGD"
            "usd" in t || "dollar" in t || "$" in t -> "USD"
            "gbp" in t || "£" in t || "pence" in t || "gbx" in t || Regex("\\dp\\b").containsMatchIn(t) -> "GBP"
            "chf" in t -> "CHF"
            "sek" in t -> "SEK"
            "nok" in t -> "NOK"
            "dkk" in t -> "DKK"
            else -> Regex("\\b[A-Z]{3}\\b").find(s)?.value.orEmpty()
        }
    }

    // Rolle aus der Funktion: S Spitze (CEO, CFO, Vorstand, Vorsitz), A Aufsicht, N nahestehend, sonst F.
    fun rolle(funktion: String, nahestehend: Boolean = false): String {
        val f = funktion.lowercase()
        if (nahestehend || Regex("closely associated|pca\\b|étroitement|etroitement|personne liée|personne morale liée|nauw gelieerd|spouse|wife|husband|partner of|connected person").containsMatchIn(f)) return "N"
        if (Regex("non[- ]?exec|supervisory|surveillance|raad van commissarissen|commissaris|administrat|independent|board member|member of the board|ned\\b|conseil d'administration|membre du ca\\b|\\bca\\b|membre du conseil|\\bcs\\b").containsMatchIn(f) &&
            !Regex("chief|ceo|cfo|managing director|président-directeur|directeur général").containsMatchIn(f)) return "A"
        if (Regex("chief executive|\\bceo\\b|chief financial|\\bcfo\\b|finance director|financial director|président[- ]directeur|\\bpdg\\b|directeur général|directeur general|directeur financier|management board|executive board|raad van bestuur|bestuurder|managing director|executive chair|chairman|chair\\b|président|voorzitter|gérant|gerant").containsMatchIn(f)) return "S"
        return "F"
    }

    private fun art(text: String): String {
        val t = text.lowercase()
        return when {
            // Verkauf und sofortiger Rueckkauf ins steuerfreie Depot (Bed and ISA/SIPP) ist eine Umbuchung
            Regex("bed\\s*(and|&)\\s*(isa|sipp|breakfast)|bed-and-").containsMatchIn(t) -> "S"
            // Verkauf und Kauf in derselben Meldung (Umschichtung ins ISA, Rueckkauf) ist keine Richtung
            Regex("\\bsale\\b|\\bsold\\b|disposal").containsMatchIn(t) && Regex("purchase|bought|repurchase|acquisition").containsMatchIn(t) -> "S"
            Regex("acquisition|achat|purchase|buy|bought|verwerving|aankoop|kauf").containsMatchIn(t) &&
                !Regex("grant|award|attribution|exercise|exercice|vesting|allotment|toekenning|uitoefening|subscription|souscription|dividend|scrip|gratuit|free|incentive plan|\\bsip\\b|partnership share|matching share|sharesave|\\bsaye\\b|share plan|employee|scheme|allowance|to cover|tax|ltip|psp|deferred|bonus|release|ownership plan|monthly share|partnership|in lieu|\\bplan\\b|reward|\\b(baye|espp|cspp|esop|drip)\\b|rights acquired|reinvest|\\bfees?\\b|with the company").containsMatchIn(t) -> "K"
            Regex("cession|sale|sold|sell|disposal|disposition|vervreemding|verkoop|verkauf").containsMatchIn(t) &&
                !Regex("exercise|exercice|uitoefening|tax|to cover|vesting|vested|release|ltip|scheme|share plan|award|\\bplan\\b|tender offer|\\b(baye|espp|cspp|esop)\\b").containsMatchIn(t) -> "V"
            else -> "S"
        }
    }

    // ------------------------------------------------------------ Frankreich (AMF, BDIF)

    data class AmfMeldung(val numero: String, val publiziert: String, val pdf: String, val firma: String)

    fun amfSuche(von: String, bis: String, ab: Int = 0, anzahl: Int = 200): String =
        "https://bdif.amf-france.org/back/api/v1/informations?TypesInformation=DD&DateDebut=${von}T00:00:00.000Z&DateFin=${bis}T23:59:59.000Z&From=$ab&Size=$anzahl"

    fun amfPdf(pfad: String) = "https://bdif.amf-france.org/back/api/v1/documents/$pfad"

    fun amfSeite(numero: String) = "https://bdif.amf-france.org/fr/details/$numero"

    private fun JsonObject.s(k: String) = this[k]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }.orEmpty()

    fun amfSucheLesen(text: String): Pair<Int, List<AmfMeldung>> = runCatching {
        val o = Json.parseToJsonElement(text).jsonObject
        val total = o["total"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
        val liste = o["result"]?.jsonArray.orEmpty().mapNotNull { e ->
            val m = e.jsonObject
            val pdf = m["documents"]?.jsonArray?.firstOrNull()?.jsonObject?.s("path") ?: return@mapNotNull null
            val firma = m["societes"]?.jsonArray?.firstOrNull { it.jsonObject.s("role") == "SocieteConcernee" }?.jsonObject?.s("raisonSociale")
                ?: m["societes"]?.jsonArray?.firstOrNull()?.jsonObject?.s("raisonSociale").orEmpty()
            AmfMeldung(m.s("numero"), m.s("datePublication").take(10), pdf, firma)
        }
        total to liste
    }.getOrDefault(0 to emptyList())

    // Geschaefte aus dem Text einer Meldung (franzoesischer Teil der Vorlage der AMF).
    // Zeichen 0x80 bis 0x9F aus PDFs mit WinAnsi-Kodierung („’“, „–“), die als Steuerzeichen ankommen.
    private val WIN1252 = runCatching { charset("windows-1252") }.getOrNull()

    fun winAnsi(text: String): String {
        if (WIN1252 == null || text.none { it.code in 0x80..0x9F }) return text
        return buildString { for (c in text) if (c.code in 0x80..0x9F) append(String(byteArrayOf(c.code.toByte()), WIN1252)) else append(c) }
    }

    // Preis ohne Waehrungswort; Tausender koennen mit Leerzeichen getrennt sein („1 000.0000 Euro“)
    private fun preisText(p: String) = p.replace(Regex("[^0-9,. ]"), " ").trim().substringBefore("  ")

    fun amfLesen(zeilen: List<String>, m: AmfMeldung): List<Geschaeft> {
        val ende = zeilen.indexOfFirst { it.startsWith("Individual notification") }.takeIf { it > 0 } ?: zeilen.size
        val z = zeilen.subList(0, ende).map { winAnsi(it).trim() }
        // Wert zu einer Bezeichnung: hinter dem Doppelpunkt in derselben Zeile oder in der naechsten Zeile
        fun wert(i: Int): String {
            if (i < 0 || i >= z.size) return ""
            z[i].substringAfter(" :", "").ifBlank { z[i].substringAfter(":", "") }.trim().takeIf { it.isNotEmpty() }?.let { return it }
            return z.getOrNull(i + 1).orEmpty()
        }
        fun wert(block: List<String>, prefix: String): String {
            val i = block.indexOfFirst { it.startsWith(prefix) }
            if (i < 0) return ""
            block[i].substringAfter(" :", "").ifBlank { block[i].substringAfter(":", "") }.trim().takeIf { it.isNotEmpty() }?.let { return it }
            return block.getOrNull(i + 1).orEmpty()
        }
        val personZeile = wert(z.indexOfFirst { it.contains("ETROITEMENT LIEE") })
        val nahe = Regex("(?i)li[ée]e? [àa]|personne (morale|physique) li[ée]e|étroitement").containsMatchIn(personZeile)
        // „Name, Funktion“; bei Nahestehenden „Firma, personne morale liée à M. X, Funktion“
        // Name vor dem ersten Komma; die Funktion ist der Rest, ohne den Bezug „personne morale liée à …“
        val person = personZeile.substringBefore(",").trim()
        val funktion = personZeile.substringAfter(",", "").trim()
        val fuer = Regex("(?i)li[ée]e? [àa] (?:M\\.|Mme|Monsieur|Madame)?\\s*([^,]+)").find(personZeile)?.groupValues?.get(1)?.trim().orEmpty()
        val iEmittent = z.indexOfFirst { it.startsWith("COORDONNEES DE L") }
        val emittent = if (iEmittent >= 0) wert(z.drop(iEmittent), "NOM") else m.firma
        val isinKopf = z.firstNotNullOfOrNull { Regex("^([A-Z]{2}[A-Z0-9]{9}\\d) - ").find(it)?.groupValues?.get(1) }.orEmpty()
        val aus = mutableListOf<Geschaeft>()
        var i = 0
        while (i < z.size) {
            if (!z[i].startsWith("DATE DE LA TRANSACTION")) { i++; continue }
            val bis = (i + 1 until z.size).firstOrNull { z[it].startsWith("DATE DE LA TRANSACTION") || z[it].startsWith("DATE DE RECEPTION") } ?: z.size
            val block = z.subList(i, minOf(z.size, bis + 1))
            val datum = tag(wert(i))
            val nature = wert(block, "NATURE DE LA TRANSACTION")
            val instrument = wert(block, "DESCRIPTION DE L")
            val isin = Regex("[A-Z]{2}[A-Z0-9]{9}\\d").find(wert(block, "CODE D"))?.value ?: isinKopf
            val programm = block.any { it.contains("GRATUITES OU DE PERFORMANCES") && it.contains("OUI") } ||
                block.indexOfFirst { it.contains("GRATUITES OU DE PERFORMANCES") }.let { k -> k >= 0 && block.getOrNull(k + 1) == "OUI" }
            val ag = block.indexOfFirst { it.startsWith("INFORMATIONS AGREGEES") }
            val preis: Double
            val n: Double
            val w: String
            if (ag >= 0) {
                val r = block.drop(ag)
                val p = wert(r, "PRIX")
                preis = zahl(preisText(p)) ?: 0.0
                w = waehrung(p)
                n = zahl(wert(r, "VOLUME")) ?: 0.0
            } else {
                val ps = block.indices.filter { block[it].startsWith("PRIX UNITAIRE") }.map { k -> wert(block.drop(k), "PRIX UNITAIRE") }
                val vs = block.indices.filter { block[it].startsWith("VOLUME") }.map { k -> zahl(wert(block.drop(k), "VOLUME")) ?: 0.0 }
                n = vs.sum()
                val summe = ps.zip(vs).sumOf { (p, v) -> (zahl(preisText(p)) ?: 0.0) * v }
                preis = if (n > 0) summe / n else 0.0
                w = ps.firstOrNull()?.let { waehrung(it) }.orEmpty()
            }
            if (datum.isNotEmpty() && isin.isNotEmpty() && n > 0) {
                aus.add(Geschaeft(
                    e = emittent.ifBlank { m.firma }, isin = isin, p = person, fuer = fuer, r = rolle(funktion, nahe), t = funktion,
                    i = if (istAktie(instrument)) "Aktie" else instrument, art = if (programm || !istAktie(instrument)) "S" else art(nature), d = datum, v = m.publiziert,
                    n = n, preis = preis, wert = n * preis, w = w, o = wert(block, "LIEU DE LA TRANSACTION"), id = m.numero, q = amfSeite(m.numero),
                ))
            }
            i = if (bis > i) bis else i + 1
        }
        return aus
    }

    // Neue Meldungen der AMF im Zeitraum: Liste, dann je unbekannter Meldung das PDF.
    suspend fun amfHolen(netz: Netz, von: String, bis: String, bekannt: Set<String>, max: Int = 400, pause: Long = 0): List<Geschaeft> {
        val meldungen = mutableListOf<AmfMeldung>()
        var ab = 0
        while (ab < 10_000) {
            val (total, liste) = netz.text(amfSuche(von, bis, ab, 500), mapOf("Accept" to "application/json"))?.let { amfSucheLesen(it) } ?: break
            meldungen += liste
            ab += 500
            if (ab >= total || liste.isEmpty()) break
        }
        val aus = mutableListOf<Geschaeft>()
        for (m in meldungen.filter { it.numero !in bekannt }.distinctBy { it.numero }.take(max)) {
            val pdf = netz.bytes(amfPdf(m.pdf)) ?: continue
            aus += amfLesen(PdfText.zeilen(pdf), m).ifEmpty { listOf(leer(m.numero)) }
            if (pause > 0) kotlinx.coroutines.delay(pause)
        }
        return aus
    }

    // Platzhalter fuer gelesene Meldungen ohne Geschaeft (z. B. Optionen ohne Stueckzahl), damit sie nicht erneut geholt werden.
    fun leer(id: String) = Geschaeft(e = "", isin = "", p = "", d = "", id = id)

    // ------------------------------------------------------------ Niederlande (AFM)

    private const val AFM = "https://www.afm.nl/en/sector/registers/meldingenregisters/transacties-leidinggevenden-mar19-"

    fun afmListe(seite: Int) = "$AFM?page=$seite"
    fun afmDetail(id: String) = "$AFM/details?id=$id"

    // Meldungen einer Listenseite: Kennung und Tag des Geschaefts, neueste zuerst.
    fun afmListeLesen(html: String): List<Pair<String, String>> =
        Regex("details\\?id=([0-9A-Fa-f-]+)\"[^>]*>\\s*([^<]+)</a>").findAll(html).map { it.groupValues[1] to tag(it.groupValues[2]) }.toList()

    fun afmDetailLesen(html: String, id: String): List<Geschaeft> {
        fun feld(label: String) = Regex("detail-list__label\">\\s*" + Regex.escape(label) + "\\s*</span>\\s*<span class=\"cc-em--detail-list__value\">\\s*<span>(.*?)</span>", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1)?.let { entitaeten(it.replace(Regex("<[^>]+>"), "")).trim() }.orEmpty()
        val person = feld("Notifiable")
        val emittent = feld("Issuing institution")
        val funktion = feld("Position/Status")
        val fuer = feld("Closely affiliated").ifBlank { feld("Closely associated with") }.ifBlank { feld("Closely associated") }
        val datum = tag(feld("Transaction"))
        val tabelle = html.substringAfter(">Transactions</h2>", "").substringBefore(">Aggregated information</h2>")
        val aus = mutableListOf<Geschaeft>()
        for (zeile in Regex("<tr>(.*?)</tr>", RegexOption.DOT_MATCHES_ALL).findAll(tabelle)) {
            val zellen = Regex("<td>\\s*(?:<span class=\"cc-mobile-title\">[^<]*</span>)?(.*?)</td>", RegexOption.DOT_MATCHES_ALL).findAll(zeile.groupValues[1])
                .map { entitaeten(it.groupValues[1].replace(Regex("<[^>]+>"), "")).trim() }.toList()
            if (zellen.size < 9) continue
            val (instrument, isin, kategorie, typ) = zellen
            val preis = zahl(zellen[6]) ?: 0.0
            val n = zahl(zellen[7]) ?: 0.0
            if (n <= 0) continue
            aus.add(Geschaeft(
                e = emittent, isin = isin, p = person, fuer = fuer, r = rolle(funktion, fuer.isNotBlank()), t = funktion,
                i = if (istAktie(instrument) || instrument.lowercase().contains("aandeel")) "Aktie" else instrument,
                art = if (zellen[4].equals("Ja", true) || preis <= 0 || !(istAktie(instrument) || instrument.lowercase().contains("aandeel"))) "S" else art("$kategorie $typ"), d = datum, v = tag(id.take(8).let { if (it.length == 8) "${it.take(4)}-${it.substring(4, 6)}-${it.substring(6)}" else "" }),
                n = n, preis = preis, wert = n * preis, w = zellen[8].trim().take(3).uppercase(), o = zellen[5], id = id, q = afmDetail(id),
            ))
        }
        return aus
    }

    // Neue Meldungen der AFM: Listenseiten, bis das Datum vor dem Beginn liegt (mit einer Seite Ueberlappung), dann Details.
    suspend fun afmHolen(netz: Netz, von: String, bekannt: Set<String>, max: Int = 400, pause: Long = 0): List<Geschaeft> {
        val ids = mutableListOf<String>()
        var seite = 1
        var vorbei = 0
        while (seite <= 400 && vorbei < 2) {
            val liste = netz.text(afmListe(seite))?.let { afmListeLesen(it) }.orEmpty()
            if (liste.isEmpty()) break
            ids += liste.filter { it.second.isEmpty() || it.second >= von }.map { it.first }
            if (liste.all { it.second.isNotEmpty() && it.second < von }) vorbei++
            seite++
            if (pause > 0) kotlinx.coroutines.delay(pause)
        }
        val aus = mutableListOf<Geschaeft>()
        for (id in ids.distinct().filter { it !in bekannt }.take(max)) {
            val html = netz.text(afmDetail(id)) ?: continue
            aus += afmDetailLesen(html, id).ifEmpty { listOf(leer(id)) }
            if (pause > 0) kotlinx.coroutines.delay(pause)
        }
        return aus
    }

    // ------------------------------------------------------------ Grossbritannien (FCA, National Storage Mechanism)

    data class NsmMeldung(val id: String, val firma: String, val publiziert: String, val link: String)

    const val NSM_SUCHE = "https://api.data.fca.org.uk/search?index=nsm-search"

    fun nsmKoerper(von: String, bis: String, ab: Int = 0, anzahl: Int = 2000) =
        "{\"from\":$ab,\"size\":$anzahl,\"sort\":\"publication_date\",\"sortorder\":\"desc\",\"criteriaObj\":{\"criteria\":[{\"name\":\"type_code\",\"value\":[\"DSH\"]}]," +
            "\"dateCriteria\":[{\"name\":\"publication_date\",\"value\":{\"from\":\"${von}T00:00:00Z\",\"to\":\"${bis}T23:59:59Z\"}}]}}"

    fun nsmDokument(link: String) = "https://data.fca.org.uk/artefacts/$link"

    fun nsmSucheLesen(text: String): Pair<Int, List<NsmMeldung>> = runCatching {
        val o = Json.parseToJsonElement(text).jsonObject
        val hits = o["hits"]?.jsonObject
        val total = hits?.get("total")?.let { t -> runCatching { t.jsonObject.s("value").toInt() }.getOrNull() ?: runCatching { t.jsonPrimitive.content.toInt() }.getOrNull() } ?: 0
        total to hits?.get("hits")?.jsonArray.orEmpty().mapNotNull { h ->
            val s = h.jsonObject["_source"]?.jsonObject ?: return@mapNotNull null
            val link = s.s("download_link").ifBlank { return@mapNotNull null }
            NsmMeldung(s.s("disclosure_id").ifBlank { link }, s.s("company"), s.s("publication_date").take(10), link)
        }
    }.getOrDefault(0 to emptyList())

    // Geschaefte aus einer Meldung nach der Vorlage von Art. 19 MAR (Abschnitte Name, Position, ISIN, Art, Preis und
    // Menge, Datum); die Gestaltung waehlt jede Firma selbst, deshalb tolerant.
    fun nsmLesen(html: String, m: NsmMeldung): List<Geschaeft> {
        val z = htmlZeilen(html)
        val aus = mutableListOf<Geschaeft>()
        // Jede Meldung beginnt mit „Details of the person ... / Details of PDMR“
        val starts = z.indices.filter { Regex("(?i)^details of (the )?(pdmr|person discharging)").containsMatchIn(z[it]) }
        val einleitung = z.subList(0, starts.firstOrNull() ?: 0).joinToString(" ")
        // Einleitungen zu Mitarbeiterplaenen (SIP mit Matching Shares) oder Bed and ISA gelten fuer alle Bloecke
        val planEinleitung = Regex("(?i)share incentive plan|\\bsip\\b|\\b(baye|espp|cspp)\\b|matching shares?|partnership shares?|sharesave|\\bsaye\\b|bed\\s*(and|&)\\s*(isa|sipp)").containsMatchIn(einleitung)
        val steuerVerkauf = Regex("(?i)\\b(cover|satisfy|meet|settle|fund|pay)\\b[^.]{0,90}\\btax|sell[- ]to[- ]cover").containsMatchIn(einleitung)
        for ((k, s) in starts.withIndex()) {
            val block = z.subList(s, starts.getOrNull(k + 1) ?: z.size)
            fun nach(muster: String, ab: Int = 0): String {
                val i = (ab until block.size).firstOrNull { Regex(muster, RegexOption.IGNORE_CASE).containsMatchIn(block[it]) } ?: return ""
                // Wert in derselben Zeile hinter einem Doppelpunkt oder in der naechsten Zeile, die kein Buchstabe a) bis h) ist
                block[i].substringAfter(':', "").trim().takeIf { it.length > 1 }?.let { return it }
                return (i + 1 until minOf(block.size, i + 4)).map { block[it] }.firstOrNull { !Regex("^[a-h]\\)$|^\\|$|^\\d\\.$").matches(it) }.orEmpty()
            }
            // Die Person steht vor dem Emittenten; fehlt die Bezeichnung „Name“, die erste Zeile nach „a)“
            val emittentAb = block.indexOfFirst { Regex("(?i)^details of the issuer|^reason for the notification").containsMatchIn(it) }.takeIf { it > 0 } ?: block.size
            // Bezeichnungen: „Name“, „Names“, „Name(s)“, „Name: …“, „Natural person:“, „Full name“
            // Laengere Bezeichnungen zuerst („Name of natural person or legal person“ vor „Name“)
            val etikett = Regex("(?i)^(name of [^:|]*|name\\(s\\)|full names?|names?|natural person|legal person)\\s*:?\\s*")
            val leer = Regex("(?i)^[a-h]\\)$|^\\|$|^\\d\\.?$|^details|^\\(?'?pca'?\\)?$|^\\(s\\):?$|^position\\s*/?\\s*status|^reasons? for")
            // Getrennte Felder „First Name | Ian | Last Name | Wilcock“
            fun feldNach(muster: String) = (0 until emittentAb).firstOrNull { Regex(muster, RegexOption.IGNORE_CASE).matches(block[it].trim()) }
                ?.let { i -> (i + 1 until minOf(emittentAb, i + 3)).map { block[it] }.firstOrNull { !leer.containsMatchIn(it) } }
            val vorname = feldNach("^first names?:?$")
            val nachname = feldNach("^(last|sur|family) ?names?:?$")
            val person = ganz(run {
                if (vorname != null && nachname != null) return@run "$vorname $nachname"
                val i = (0 until emittentAb).firstOrNull { etikett.containsMatchIn(block[it]) }
                if (i != null) {
                    block[i].replace(etikett, "").trim().takeIf { it.length > 1 }
                        ?: (i + 1 until minOf(emittentAb, i + 4)).map { block[it] }.firstOrNull { !leer.containsMatchIn(it) && !etikett.matches(it) }.orEmpty()
                } else block.subList(1, emittentAb).firstOrNull { !leer.containsMatchIn(it) }.orEmpty()
            }.replace(Regex("(?i)^this notification relates to\\s+(mr|mrs|ms|dr|sir)?\\.?\\s*"), ""))
            // Steht der Abschnitt „Reason for the notification“ vor dem Personenblock, die Funktion davor suchen
            val funktion = ganz(nach("^position\\s*/\\s*status|^position$").replace(Regex("^\\d\\.\\s*"), "")).ifBlank {
                val vorher = z.subList(maxOf(if (k > 0) starts[k - 1] else 0, s - 40), s)
                val i = vorher.indexOfLast { Regex("(?i)^position\\s*/\\s*status|^position$").containsMatchIn(it) }
                if (i < 0) "" else vorher.drop(i + 1).firstOrNull { !Regex("^[a-h]\\)$|^\\|$|^\\d\\.$").matches(it) }.orEmpty()
            }
            val emittentI = block.indexOfFirst { Regex("(?i)^details of the issuer").containsMatchIn(it) }
            val emittent = if (emittentI >= 0) nach("^name$", emittentI) else m.firma
            // ISIN als eigenes Wort (nicht aus der LEI), bevorzugt hinter „ISIN“
            val text = block.joinToString(" ")
            val isin = Regex("(?i)ISIN[^A-Z0-9]{0,20}([A-Z]{2}[A-Z0-9]{9}\\d)\\b").find(text)?.groupValues?.get(1)
                ?: Regex("\\b[A-Z]{2}[A-Z0-9]{9}\\d\\b").findAll(text).map { it.value }
                    .firstOrNull { Regex("^(GB|JE|GG|IM|IE|US|NL|LU|BM|KY|CA|AU|ZA|DE|FR|CH)").containsMatchIn(it) }.orEmpty()
            val beschreibung = nach("^description of the financial instrument|^description of the instrument")
            val vorArt = block.subList(0, block.indexOfFirst { Regex("(?i)^nature of the transaction").containsMatchIn(it) }.takeIf { it > 0 } ?: block.size)
            val instrument = if (Regex("(?i)ordinary\\b.{0,25}\\bshares?|ordinary stock|\\bshares of|common (?:shares?|stock)|depositary").containsMatchIn(vorArt.joinToString(" ")) ||
                vorArt.any { Regex("(?i)^(ordinary )?shares?$").matches(it.trim()) }) "Aktie" else beschreibung
            // Die Art kann ueber mehrere Zeilen gehen („1. Sale … 2. Purchase into an ISA“): alles bis zur Preistabelle
            val natureAb = block.indexOfFirst { Regex("(?i)^nature of the transaction").containsMatchIn(it) }
            val pv = block.indexOfFirst { Regex("(?i)price\\(s\\) and volume\\(s\\)").containsMatchIn(it) }
            val nature = if (natureAb >= 0 && pv > natureAb) (listOf(block[natureAb].substringAfter(':', "")) + block.subList(natureAb + 1, pv))
                .filter { !Regex("^[a-h]\\)$|^\\|$").matches(it.trim()) }.joinToString(" ").trim() else nach("^nature of the transaction")
            val datum = tag(nach("^date of the transaction"))
            val ort = nach("^place of the transaction")
            // Irische Werte: in London in Pence, sonst (Euronext Dublin, ausserboerslich) in Euro
            // Handelsplatz ausserhalb Londons („on the Australian Securities Exchange“, „NYSE“) bestimmt die Waehrung ohne Angabe
            val isinW = waehrungZumOrt("$ort $nature").ifEmpty {
                if (isin.startsWith("IE") && !Regex("(?i)london|xlon|\\blse\\b|\\baim\\b").containsMatchIn(ort)) "EUR" else waehrungZurIsin(isin)
            }
            // Preise und Mengen: Zeilen zwischen „Price(s) and volume(s)“ und dem Summenabschnitt („Aggregated information“,
            // „Aggregate information“, „Aggregated volume:“, „Aggregated price(s) and volume(s)“); der Summenabschnitt reicht bis zum Datum
            val agg = block.indexOfFirst { Regex("(?i)^-?\\s*aggregated?\\s+(information|volume|price)").containsMatchIn(it) }.takeIf { it > pv } ?: block.size
            val aggEnde = (agg + 1 until minOf(block.size, agg + 14)).firstOrNull { Regex("(?i)^date of the transaction").containsMatchIn(block[it]) } ?: minOf(block.size, agg + 14)
            var n = 0.0
            var wert = 0.0
            var roh = 0.0
            var w = ""
            if (pv >= 0) {
                // Aufzaehlungen („1.“, „2)“, „ii)“) und Prozentangaben sind keine Preise oder Mengen
                // Saetze ueber den Bestand danach („total holding following the above purchase is 58,000 shares“) gehoeren nicht dazu
                val zahlen = block.subList(pv + 1, agg).filter { !Regex("(?i)^price|^volume|^\\|$|^[a-h]\\)$|holding|following|relates to|representing|interest in").containsMatchIn(it) }
                    .map { it.replace(Regex("(?i)^\\(?(\\d{1,2}|[ivx]{1,4})[.)](?:\\s+|$)"), "").replace(Regex("\\d+(\\.\\d+)?\\s?%"), "") }
                    // Fliesstext „50,000 shares at 209.5 pence each“, „33,200 Ordinary Shares purchased at a price of 301p“: Preis vor die Menge stellen
                    .map { it.replace(Regex("(?i)(\\d[\\d,]*)\\s+(?:ordinary\\s+)?shares?\\s+(?:(?:were\\s+)?(?:purchased|sold|acquired|bought|disposed of)\\s+)?(?:at|@)\\s+(?:an? (?:average )?price of\\s+)?([£$€]?\\s?\\d[\\d,]*\\.?\\d*\\s?(?:pence|p|gbp|usd|eur)?)(?![a-z])"), "$2 $1") }
                    // Zerteilte Zahlen („2 50“, „1 5 September“) vor dem Entfernen der Daten zusammenfuegen
                    .map { it.replace(Regex("(\\d) \\.(\\d)"), "$1.$2").replace(Regex("(?<![\\d.,])(\\d{1,3}) (\\d{3})(?=\\.\\d)"), "$1$2").replace(Regex("(?<![\\d.,£$€])(\\d) (\\d{1,3})(?![\\d,.])"), "$1$2") }
                    // Datumsangaben in Tabellen mit Datumsspalte („13 October 2026“, „11 September“, „2026-10-13“, „13/10/2026“)
                    .map { it.replace(Regex("(?i)\\b\\d{1,2}(st|nd|rd|th)?\\s+(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\.?(\\s+\\d{4})?\\b|\\b\\d{4}-\\d{2}-\\d{2}\\b|\\b\\d{1,2}/\\d{1,2}/\\d{2,4}\\b"), "") }
                    .filter { Regex("\\d").containsMatchIn(it) }
                // Paare aus Preis und Menge, in einer Zeile („1,700p 60,000“) oder in zwei
                // „p“ zaehlt nur ohne folgenden Buchstaben als Pence („£5.160 per Share“ ist kein Pence-Preis)
                // „GBX 7.00“: vorangestellte Pence-Einheit wie „7.00p“ behandeln
                val werte = zahlen.map { it.replace(Regex("(?i)\\bgbx\\s*(\\d[\\d,]*\\.?\\d*)"), "$1p").replace(Regex("(?i)\\b(usd|eur|gbp)\\s*(\\d[\\d,]*\\.?\\d*)"), "$2 $1") }
                    .flatMap { Regex("((?:\\b(?:c|ca|a|au|hk|s|us)?\\$|[£$€])?\\s?\\d[\\d,]*\\.?\\d*\\s?(?:pence|gbp|gbx|usd|eur|p)?(?![a-z]))", RegexOption.IGNORE_CASE).findAll(it).map { r -> r.value.trim() } }
                    // Summenspalte („8.20 USD | 6097 | 49 995.40“): Wert gleich Preis mal Menge davor faellt weg
                    .let { l ->
                        fun z(t: String) = zahl(t.replace(Regex("[^0-9,.]"), ""))
                        l.filterIndexed { i, x ->
                            if (i < 2) return@filterIndexed true
                            val a = z(l[i - 2]); val b = z(l[i - 1]); val c = z(x)
                            a == null || b == null || c == null || c <= 0 || listOf(a * b, a * b / 100).none { kotlin.math.abs(it - c) <= c * 0.002 }
                        }
                    }
                // Preise tragen eine Waehrung oder Einheit oder Nachkommastellen; Mengen sind ganze Zahlen (auch mit Tausenderkomma)
                fun preisartig(t: String) = Regex("(?i)[£$€]|\\d\\s?(p|pence|gbx|gbp|usd|eur)$").containsMatchIn(t) || Regex("^\\d+\\.\\d+$").matches(t.replace(",", ""))
                val preise = werte.filter { preisartig(it) }
                val mengen = werte.filter { !preisartig(it) }
                // Spaltenweise Tabellen („£0.0179 | £0.0179 | 138,997 | 278,288“) erst alle Preise, dann alle Mengen
                // (auch gemischt: „32p | 31.3p | 32.5p | 34p | 49,472 | … | 35.3p | 8,180“); sonst Paare, bei „Volume | Price“ Menge zuerst
                val wechselnd = werte.chunked(2).all { it.size == 2 && preisartig(it[0]) != preisartig(it[1]) }
                val spaltenweise = preise.size >= 2 && preise.size == mengen.size && !wechselnd
                // Pence und Dollar je ADS in einer Tabelle lassen sich nicht zusammenrechnen
                val gemischt = preise.any { it.contains('$') } && preise.any { p -> p.contains('£') || Regex("(?i)\\d\\s?(p|pence)$").containsMatchIn(p) }
                val paare = when {
                    gemischt -> emptyList()
                    spaltenweise -> preise.zip(mengen)
                    else -> werte.chunked(2).filter { it.size == 2 }.map { if (!preisartig(it[0]) && preisartig(it[1])) it[1] to it[0] else it[0] to it[1] }
                }
                // Waehrungscode im Preis- oder Summenabschnitt („ZAR 60.908“), sonst aus dem Preis oder der ISIN
                // Ein eigenes Feld „Currency“ davor („AUD - Australian Dollar“) zaehlt ebenso
                val waehrungAb = block.indexOfFirst { Regex("(?i)^currency\\b").containsMatchIn(it) }.takeIf { it in 0 until pv } ?: pv
                Regex("\\b(ZAR|AUD|USD|EUR|CHF|SEK|NOK|DKK|CAD|HKD|JPY|GBP)(?![A-Za-z])").find(block.subList(waehrungAb, aggEnde).joinToString(" "))?.let { w = it.value }
                // Kopfzeile „Price (pence)“ oder „Price(s) - pence“: Preise ohne Einheit sind Pence
                // Ebenso, wenn der Summenabschnitt den Preis in Pence nennt („Price (GBP) | 97.57p“ zu „97.90“)
                val penceKopf = Regex("(?i)\\bpence\\b|\\(p\\)|\\bgbx\\b|price(\\(s\\))?\\s*[-–]?\\s*p$").containsMatchIn(block.subList(pv, agg).takeWhile { !Regex("\\d").containsMatchIn(it) }.joinToString(" ")) ||
                    block.subList(agg, aggEnde).any { Regex("(?i)\\d\\s?(p|pence)\\b").containsMatchIn(it) && !Regex("(?i)shares? of|of £?\\d+(\\.\\d+)?\\s?p(ence)? each|\\d\\s?p shares|nominal|par value").containsMatchIn(it) }
                for ((p, menge) in paare) {
                    // „4.900“ mit Punkt als Tausendertrennzeichen, wenn die Summe „4,900“ nennt
                    val v = (zahl(menge) ?: break).let { x ->
                        if (Regex("^\\d{1,3}\\.\\d{3}$").matches(menge.trim()) && block.subList(agg, aggEnde).any { l -> Regex("\\d[\\d,]*").findAll(l).any { zahl(it.value) == x * 1000 } }) x * 1000 else x
                    }
                    var preis = zahl(p.replace(Regex("[^0-9,.]"), "")) ?: break
                    // „£31.12p“: Pfundzeichen und Pence zugleich, das Pfundzeichen gilt
                    if (p.contains('£') && Regex("(?i)\\d\\s?p$").containsMatchIn(p)) { if (w.isEmpty()) w = "GBP" }
                    else if (Regex("(?i)\\d\\s?(p|pence|gbx)$").containsMatchIn(p)) { preis /= 100; if (w.isEmpty()) w = "GBP" }
                    // Ohne Einheit und ueber 150: an der Londoner Boerse fast immer Pence („550.86“)
                    else if (!Regex("(?i)[£$€]|gbp|usd|eur").containsMatchIn(p) && (w.isEmpty() || w == "GBP") && (penceKopf || preis > 150 && isinW == "GBP")) { preis /= 100; w = "GBP" }
                    else if (w.isEmpty()) w = waehrung(p).ifEmpty { isinW }
                    n += v
                    wert += preis * v
                    roh += (zahl(p.replace(Regex("[^0-9,.]"), "")) ?: preis) * v
                }
                // „15.20 pence“ bei einer Summe von 100,137.60 fuer 6,588 Stueck: die Einheit ist falsch beschriftet, es sind Pfund
                // Umgekehrt „61.45“ ohne Einheit bei einer Summe von £19,690.42 fuer 32,043 Stueck: Pence
                val summen = Regex("\\d[\\d,]*\\.?\\d*").findAll(block.subList(agg, aggEnde).joinToString(" ")).mapNotNull { zahl(it.value) }
                    .filter { s -> kotlin.math.abs(s - n) > 0.5 && paare.none { (p, _) -> zahl(p.replace(Regex("[^0-9,.]"), "")) == s } }.toList()
                fun trifft(x: Double) = x > 0 && summen.any { kotlin.math.abs(it - x) <= x * 0.005 }
                if (roh > wert * 50 && trifft(roh) && !trifft(wert)) wert = roh
                else if (roh == wert && (w.isEmpty() || w == "GBP") && trifft(wert / 100) && !trifft(wert)) { wert /= 100; w = "GBP" }
            }
            // Ohne Namensfeld steht dort die Funktion; der Name dann aus der Einleitung („Mat Hart (Business Development Director“)
            val name = if (person.equals(funktion, true) && funktion.isNotBlank())
                Regex("([A-Z][\\w'’-]+(?: [A-Z][\\w'’-]+){1,3}),?\\s*\\(?" + Regex.escape(funktion)).find(einleitung)?.groupValues?.get(1).orEmpty()
            else person
            if (name.isBlank() || datum.isEmpty() || isin.isEmpty() || n <= 0) continue
            // Unplausible Paare aus frei gestalteten Tabellen (Preis ueber 2.000 je Aktie, Geschaeft ueber 500 Mio.) gelten
            // als nicht lesbar
            if (n > 0 && (wert / n > 2_000 || wert / n < 0.00001 || wert > 500_000_000)) wert = 0.0
            // Ohne lesbaren Betrag oder bei Plaenen, Zuteilungen usw. ist es Sonstiges; dann zeigt die App keinen Betrag
            // Verkaeufe zur Begleichung von Steuern („sold shares … to cover associated income taxes“) stehen oft nur in der Einleitung
            val art = if (wert <= 0) "S" else art(nature).let { if (it == "V" && steuerVerkauf || planEinleitung) "S" else it }
            aus.add(Geschaeft(
                e = emittent.ifBlank { m.firma }, isin = isin, p = name.replace(Regex("^\\(?\\d+[.)]\\s*"), ""), r = rolle(funktion), t = funktion,
                i = if (istAktie(instrument)) "Aktie" else instrument, art = art, d = datum, v = m.publiziert,
                n = n, preis = if (art == "S" || n <= 0) 0.0 else wert / n, wert = if (art == "S") 0.0 else wert, w = w.ifEmpty { isinW }, o = ort, id = m.id, q = nsmDokument(m.link),
            ))
        }
        // Verkauf und Kauf derselben Person am selben Tag (Umschichtung ins ISA, getrennte Bloecke) ist keine Richtung
        // ebenso gleiche Stueckzahl zwischen Person und nahestehender Gesellschaft (Umbuchung)
        val paar = aus.groupBy { it.p to it.d }.filterValues { g -> g.any { it.art == "K" } && g.any { it.art == "V" } }.keys
        val umbuchung = aus.groupBy { it.d to it.n }.filterValues { g -> g.any { it.art == "K" } && g.any { it.art == "V" } }.keys
        return aus.map { if (((it.p to it.d) in paar || (it.d to it.n) in umbuchung) && it.art in setOf("K", "V")) it.copy(art = "S", preis = 0.0, wert = 0.0) else it }
    }

    suspend fun nsmHolen(netz: Netz, von: String, bis: String, bekannt: Set<String>, max: Int = 400, pause: Long = 0): List<Geschaeft> {
        val meldungen = mutableListOf<NsmMeldung>()
        var ab = 0
        while (true) {
            val (total, liste) = netz.text(NSM_SUCHE, mapOf("Accept" to "application/json"), json = nsmKoerper(von, bis, ab))?.let { nsmSucheLesen(it) } ?: break
            meldungen += liste
            ab += 2000
            if (ab >= total || liste.isEmpty() || ab >= 10_000) break
        }
        val aus = mutableListOf<Geschaeft>()
        for (m in meldungen.filter { it.id !in bekannt }.distinctBy { it.id }.take(max)) {
            val html = netz.text(nsmDokument(m.link)) ?: continue
            aus += nsmLesen(html, m).ifEmpty { listOf(leer(m.id)) }
            if (pause > 0) kotlinx.coroutines.delay(pause)
        }
        return aus
    }
}
