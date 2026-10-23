package de.troetenrepublik.app.market

import de.troetenrepublik.app.market.EuInsider.Geschaeft
import de.troetenrepublik.app.market.InsiderQuellen.Netz
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Insidergeschaefte ausserhalb Europas und der USA (seit 5.9). Die Schweiz (SIX Exchange Regulation) liefert alle
// Meldungen in einem Abruf und laeuft wie die europaeischen Register ueber EuInsider. Indien (NSE), Hongkong (HKEX)
// und Australien (ASX) holt die App nur fuer die gerade geoeffnete Aktie, auf dem Geraet und fuer den eigenen
// Gebrauch: ihre Bedingungen erlauben kein Sammeln und keine Weitergabe, deshalb liegt davon nichts im Programm.
// Selbst heruntergeladene Listen (BSE, umgewandelte SEDI-Berichte) lassen sich importieren (RegisterImport).
object InsiderWelt {
    private fun JsonObject.s(k: String) = this[k]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }.orEmpty()

    // ------------------------------------------------------------ Schweiz (SIX Exchange Regulation)

    fun sixAdresse(von: String, bis: String, seite: Int) =
        "https://www.ser-ag.com/sheldon/management_transactions/v1/overview.json?pageSize=1000&pageNumber=$seite&sortAttribute=byDate" +
            "&fromDate=${von.replace("-", "")}&toDate=${bis.replace("-", "")}"

    const val SIX_SEITE = "https://www.ser-ag.com/en/resources/notifications-market-participants/management-transactions.html"

    // Doppelt kodiertes UTF-8 („AusÃ¼bung“) zurueckwandeln.
    private fun utf8(s: String): String =
        if (s.contains('Ã') || s.contains('Â')) runCatching { String(s.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8) }.getOrDefault(s).takeIf { !it.contains(Char(0xFFFD)) } ?: s else s

    // Meldungen der SIX ohne Namen (Schweizer Meldungen sind anonym): Funktion, Kauf oder Verkauf, Menge, Betrag in CHF.
    // Korrigierte Meldungen fallen weg.
    fun sixLesen(text: String): Pair<Int, List<Geschaeft>> = runCatching {
        val o = Json.parseToJsonElement(text).jsonObject
        val liste = o["itemList"]?.jsonArray.orEmpty().map { it.jsonObject }
        val korrigiert = liste.map { it.s("correcteeId") }.filter { it.isNotEmpty() }.toSet()
        val aus = liste.filter { it.s("notificationId") !in korrigiert }.mapNotNull { m ->
            val isin = m.s("ISIN").ifBlank { return@mapNotNull null }
            val n = m.s("transactionSize").toDoubleOrNull() ?: return@mapNotNull null
            val bedingung = utf8(m.s("transactionConditions")).replace(Regex("\\s+"), " ").trim()
            val programm = Regex("(?i)ausübung|exercise|zuteilung|allocation|grant|vesting|plan|programm|programme|lohn|salary|compensation|vergütung|geschenk|gift|erbe|inherit|abschlag|discount|mitarbeiter|employee|rsu|psu|to cover|\\btax|steuer|net settlement|exersale|sperrfrist|restricted|bonus|tantieme|honorar").containsMatchIn(bedingung)
            val art = when (m.s("buySellIndicator")) { "1" -> if (programm) "S" else "K"; "2" -> if (programm) "S" else "V"; else -> "S" }
            val nahe = m.s("obligorRelatedPartyInd").isNotEmpty()
            val r = if (nahe) "N" else when (m.s("obligorFunctionCode")) { "1" -> "S"; "2" -> "A"; else -> "F" }
            val typ = m.s("securityTypeCode")
            val tag = m.s("transactionDate").let { if (it.length == 8) "${it.take(4)}-${it.substring(4, 6)}-${it.substring(6)}" else "" }
            Geschaeft(
                e = utf8(m.s("notificationSubmitter")), isin = isin, p = "", r = r, t = bedingung,
                i = if (typ == "6" || typ == "7") "Aktie" else utf8(m.s("securityDescription")).ifBlank { "Sonstiges" },
                art = art, d = tag, v = tag, n = n, preis = m.s("transactionAmountPerSecurityCHF").toDoubleOrNull() ?: 0.0,
                wert = m.s("transactionAmountCHF").toDoubleOrNull() ?: 0.0, w = "CHF", id = m.s("notificationId"), q = SIX_SEITE,
            )
        }
        (o["totalCount"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0) to aus
    }.getOrDefault(0 to emptyList())

    suspend fun sixHolen(netz: Netz, von: String, bis: String): List<Geschaeft>? {
        val aus = mutableListOf<Geschaeft>()
        var seite = 0
        while (seite < 20) {
            val text = netz.text(sixAdresse(von, bis, seite), mapOf("Accept" to "application/json")) ?: return if (seite == 0) null else aus
            val (total, liste) = sixLesen(text)
            aus += liste
            seite++
            if (liste.isEmpty() || seite * 1000 >= total) break
        }
        return aus
    }

    // ------------------------------------------------------------ Indien (NSE)

    fun nseListe(von: String, bis: String): String {
        fun d(iso: String) = iso.split('-').let { (j, m, t) -> "$t-$m-$j" }
        return "https://www.nseindia.com/api/corporates-pit-gg?index=equities&from_date=${d(von)}&to_date=${d(bis)}"
    }

    val NSE_KOPF = mapOf("Accept" to "application/json", "Referer" to "https://www.nseindia.com/companies-listing/corporate-filings-insider-trading")

    // Meldungen eines Symbols aus der Liste: Kennung und XBRL-Datei.
    fun nseListeLesen(text: String, symbol: String): List<Pair<String, String>> = runCatching {
        val e = Json.parseToJsonElement(text)
        val liste = runCatching { e.jsonArray }.getOrNull() ?: e.jsonObject["data"]?.jsonArray.orEmpty()
        liste.map { it.jsonObject }.filter { it.s("symbol").equals(symbol, true) }
            .mapNotNull { m -> m.s("xmlFileName").takeIf { it.startsWith("http") }?.let { m.s("appId").ifBlank { it } to it } }
    }.getOrDefault(emptyList())

    // Geschaefte einer XBRL-Meldung (Vorlage PIT der SEBI): je Abschnitt Person, Kategorie, Art, Menge, Wert, Datum.
    fun nseXmlLesen(xml: String, id: String): List<Geschaeft> {
        fun feld(name: String, ctx: String) = Regex("<[a-z-]+:$name contextRef=\"$ctx\"[^>]*>([^<]*)<").find(xml)?.groupValues?.get(1)?.let { InsiderQuellen.entitaeten(it).replace(Regex("\\s+"), " ").trim() }.orEmpty()
        val isin = feld("ISINCode", "MainI")
        val firma = feld("NameOfTheCompany", "MainI")
        val symbol = feld("Symbol", "MainI")
        val kontexte = Regex("contextRef=\"(Disclosure\\d+)\"").findAll(xml).map { it.groupValues[1] }.distinct().toList()
        return kontexte.mapNotNull { c ->
            val n = feld("SecuritiesAcquiredOrDisposedNumberOfSecurity", c).toDoubleOrNull() ?: return@mapNotNull null
            if (n <= 0) return@mapNotNull null
            val wert = feld("SecuritiesAcquiredOrDisposedValueOfSecurity", c).toDoubleOrNull() ?: 0.0
            val typ = feld("SecuritiesAcquiredOrDisposedTransactionType", c)
            val modus = feld("ModeOfAcquisitionOrDisposal", c)
            val markt = Regex("(?i)market (purchase|sale)|open market|on market").containsMatchIn(modus)
            val person = feld("NameOfThePerson", c)
            // Mitarbeiter- und Wohlfahrtstrusts kaufen fuer Mitarbeiterplaene, das ist keine Entscheidung der Fuehrung
            val planTrust = Regex("(?i)trust").containsMatchIn(person) && Regex("(?i)esop|esos|employee|welfare|benefit|stock option|incentive").containsMatchIn(person)
            val art = when {
                !markt || planTrust -> "S"
                typ.equals("Buy", true) || typ.equals("Acquisition", true) -> "K"
                typ.equals("Sell", true) || typ.equals("Disposal", true) -> "V"
                else -> "S"
            }
            val kategorie = feld("CategoryOfPerson", c)
            // Gruender mit Vorstandsamt („Promoter and Director“) und Schluesselpersonal zaehlen zur Spitze
            val r = when {
                Regex("(?i)\\bkmp\\b|key managerial|managing director|whole[- ]?time|\\bceo\\b|\\bcfo\\b").containsMatchIn(kategorie) -> "S"
                Regex("(?i)promoter").containsMatchIn(kategorie) && Regex("(?i)director").containsMatchIn(kategorie) -> "S"
                Regex("(?i)immediate relative|promoter").containsMatchIn(kategorie) -> "N"
                Regex("(?i)director").containsMatchIn(kategorie) -> "A"
                else -> "F"
            }
            val instrument = feld("TypeOfInstrument", c)
            Geschaeft(
                e = firma, isin = isin, p = person, r = r, t = kategorie,
                i = if (instrument.equals("Equity", true) || instrument.contains("Share", true)) "Aktie" else instrument,
                art = art, d = feld("DateOfAllotmentAdviceOrAcquisitionOfSharesOrSaleOfSharesSpecifyFromDate", c).take(10),
                v = feld("DateOfFiling", "MainI").take(10), n = n, preis = if (n > 0) wert / n else 0.0, wert = wert, w = "INR",
                o = feld("ExchangeOnWhichTheTradeWasExecuted", c), id = "$id/$c", q = "https://www.nseindia.com/get-quotes/equity?symbol=$symbol", c = symbol,
            )
        }
    }

    suspend fun nseHolen(netz: Netz, symbol: String, von: String, bis: String): List<Geschaeft>? {
        val liste = netz.text(nseListe(von, bis), NSE_KOPF)?.let { nseListeLesen(it, symbol) } ?: return null
        return liste.take(40).flatMap { (id, url) -> netz.text(url)?.let { nseXmlLesen(it, id) }.orEmpty() }
    }

    // ------------------------------------------------------------ Hongkong (HKEX, Disclosure of Interests)

    private fun hkTag(iso: String) = iso.split('-').let { (j, m, t) -> "$t/$m/$j" }

    fun hkSuche(code: String, von: String, bis: String) =
        "https://di.hkex.com.hk/di/NSSrchCorpList.aspx?sa1=cl&scsd=${hkTag(von)}&sced=${hkTag(bis)}&sc=$code&src=MAIN&lang=EN&g_lang=en"

    // Adresse der Liste aller Meldungen aus dem Link der Suchseite.
    fun hkListeAdresse(suche: String): String? =
        Regex("href=\"(NSAllFormList\\.aspx\\?[^\"]+)\"").find(suche)?.groupValues?.get(1)?.let { "https://di.hkex.com.hk/di/" + InsiderQuellen.entitaeten(it) }

    // Zeilen der Liste: Formularnummer, Name, Grundcode, Menge, Durchschnittspreis, Bestand, Anteil, Datum.
    fun hkListeLesen(html: String, isin: String, firma: String, code: String = ""): List<Geschaeft> {
        val aus = mutableListOf<Geschaeft>()
        for (zeile in Regex("<tr[^>]*>(.*?)</tr>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).findAll(html)) {
            val zellen = Regex("<td[^>]*>(.*?)</td>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).findAll(zeile.groupValues[1])
                .map { InsiderQuellen.entitaeten(it.groupValues[1].replace(Regex("<[^>]+>"), " ")).replace(Regex("\\s+"), " ").trim() }.toList()
            if (zellen.size < 8 || !Regex("^[A-Z]{2}\\d{8}[A-Z]\\d+").containsMatchIn(zellen[0])) continue
            val code = Regex("\\d{4}").find(zellen[2])?.value.orEmpty()
            val n = InsiderQuellen.zahl(zellen[3].substringBefore('(')) ?: continue
            val preis = InsiderQuellen.zahl(zellen[4].replace(Regex("[A-Za-z]"), "")) ?: 0.0
            // 1101 Kauf an der Boerse, 1201 Verkauf an der Boerse; alles andere (Zuteilung, Optionen, Schwellen) Sonstiges
            val art = when { code == "1101" && preis > 0 -> "K"; code == "1201" && preis > 0 -> "V"; else -> "S" }
            val waehrung = Regex("\\b(HKD|RMB|CNY|USD)\\b").find(zellen[4])?.value?.replace("RMB", "CNY") ?: "HKD"
            aus.add(Geschaeft(
                e = firma, isin = isin, p = zellen[1], r = if (zellen[0].startsWith("DA") || zellen[0].startsWith("CE")) "A" else "N",
                t = if (zellen[0].startsWith("DA") || zellen[0].startsWith("CE")) "Director" else "Substantial shareholder", i = "Aktie",
                art = art, d = InsiderQuellen.tag(zellen[7]), v = InsiderQuellen.tag(zellen[7]), n = n, preis = preis, wert = n * preis, w = waehrung,
                id = zellen[0], q = "https://di.hkex.com.hk/di/NSForm3A.aspx?fn=${zellen[0]}", c = code,
            ))
        }
        return aus
    }

    // Alle Seiten einer Meldungsliste.
    private suspend fun hkSeiten(netz: Netz, liste: String, isin: String, firma: String, code: String, max: Int, pause: Long = 0): List<Geschaeft> {
        val aus = mutableListOf<Geschaeft>()
        for (seite in 1..max) {
            val html = netz.text(if (seite == 1) liste else "$liste&pg=$seite") ?: break
            val neu = hkListeLesen(html, isin, firma, code).filter { g -> aus.none { it.id == g.id && it.n == g.n } }
            if (neu.isEmpty()) break
            aus += neu
            if (pause > 0) kotlinx.coroutines.delay(pause)
        }
        return aus
    }

    suspend fun hkHolen(netz: Netz, code: String, isin: String, firma: String, von: String, bis: String): List<Geschaeft>? {
        val suche = netz.text(hkSuche(code, von, bis)) ?: return null
        val liste = hkListeAdresse(suche) ?: return emptyList()
        return hkSeiten(netz, liste, isin, firma, code, 5)
    }

    // ------------------------------------------------------------ Australien (ASX, Appendix 3Y)

    // Marktliste der Meldungen zu Wertpapierbestaenden, neueste zuerst, 1000 je Seite (etwa zwei Wochen).
    fun asxListe(seite: Int) = "https://asx.api.markitdigital.com/asx-research/1.0/markets/announcements?itemsPerPage=1000&page=$seite&announcementTypes=SECURITY%20HOLDER%20DETAILS"
    fun asxPdf(schluessel: String) = "https://cdn-api.markitdigital.com/apiman-gateway/ASX/asx-research/1.0/file/$schluessel"

    data class AsxMeldung(val schluessel: String, val tag: String, val firma: String, val isin: String, val symbol: String = "")

    fun asxListeLesen(text: String): List<AsxMeldung> = runCatching {
        val o = Json.parseToJsonElement(text).jsonObject
        val daten = o["data"]?.jsonObject
        val items = daten?.get("items")?.jsonArray ?: o["items"]?.jsonArray ?: return emptyList()
        val name = daten?.s("displayName").orEmpty()
        items.map { it.jsonObject }.filter { m ->
            val typen = (m["announcementTypes"]?.let { t -> runCatching { t.jsonArray.map { it.jsonPrimitive.contentOrNull.orEmpty() } }.getOrNull() }.orEmpty() +
                m.s("announcementType") + m.s("headline")).joinToString(" ")
            Regex("(?i)change of director'?s interest|appendix 3y").containsMatchIn(typen)
        }.mapNotNull { m ->
            val firma = m["companyInfo"]?.let { runCatching { it.jsonArray.firstOrNull()?.jsonObject }.getOrNull() }
            AsxMeldung(m.s("documentKey").ifBlank { return@mapNotNull null }, m.s("date").take(10), firma?.s("displayName") ?: name, firma?.s("isin").orEmpty(), m.s("symbol"))
        }
    }.getOrDefault(emptyList())

    // Aktienzahl in einem Abschnitt: Zahl vor „Ordinary“, „Shares“, „Fully paid“ oder „FPO“, sonst die groesste Zahl
    // (Aufzaehlungen wie „1.“ sind kleiner als jede Menge).
    private fun aktien(text: String): Double {
        val mitEinheit = Regex("(\\d[\\d,]*)\\s*(?:fully paid\\s*)?(?:ordinary|shares|fpo\\b|ord\\b)", RegexOption.IGNORE_CASE).findAll(text)
            .mapNotNull { InsiderQuellen.zahl(it.groupValues[1]) }.toList()
        val zahlen = Regex("(?<![\\d.$])\\d{1,3}(?:,\\d{3})+(?![\\d.])|(?<![\\d.,$])\\d{2,}(?![\\d.,])").findAll(text)
            .mapNotNull { InsiderQuellen.zahl(it.value) }.toList()
        // Nummerierte Zeilen („1. 360,768 2. 13,267“, „(a) …“) sind mehrere Halter: zusammenzaehlen
        val nummeriert = Regex("(^|\\s)(\\d\\.|\\(?[a-hA-H]\\)|[A-H]\\.)\\s").findAll(text).count() >= 2
        return when {
            nummeriert && mitEinheit.size >= 2 -> mitEinheit.sum()
            nummeriert && zahlen.size >= 2 -> zahlen.sum()
            mitEinheit.isNotEmpty() -> mitEinheit.first()
            else -> zahlen.maxOrNull() ?: 0.0
        }
    }

    private val ZUSATZ = setOf("de", "van", "von", "der", "den", "da", "di", "du", "le", "la", "bin", "al", "dos", "del", "y", "and")

    // Zerfallene Namen zusammenfuegen („M r Pete Meyers“, „Christoph er“): ein kleingeschriebenes Stueck gehoert zum
    // Wort davor, ausser es ist ein Namenszusatz wie „de“ oder „van“.
    private fun ganz(name: String): String {
        val aus = mutableListOf<String>()
        for (w in name.split(' ').filter { it.isNotEmpty() }) {
            if (aus.isNotEmpty() && w.first().isLowerCase() && w.all { it.isLetter() } && w !in ZUSATZ && aus.last().last().isLetter()) aus[aus.lastIndex] = aus.last() + w
            else aus += w
        }
        return aus.joinToString(" ")
    }

    // Bezeichnung als Muster, das auch Woerter mit verirrten Leerzeichen trifft („Name o f Director“).
    private fun bezeichnung(text: String) = text.split('|').joinToString("|") { alt ->
        alt.trim().map { c -> if (c == ' ') "\\s*" else Regex.escape(c.toString()) + "\\s?" }.joinToString("")
    }

    // Geschaeft aus dem Formular Appendix 3Y. Der Text zerfaellt je nach Erzeuger in Woerter oder verrutschte Spalten;
    // gelesen wird deshalb der zusammengefuegte Text abschnittsweise zwischen den Bezeichnungen. Nur eindeutige Kaeufe
    // und Verkaeufe an der Boerse zaehlen, alles andere (Optionen, Zuteilungen, Plaene) ist Sonstiges.
    fun asxLesen(zeilen: List<String>, m: AsxMeldung): Geschaeft? {
        // Erst die Fusszeilen des Formulars entfernen („+ See chapter 19 …“, „For personal use only“, „Appendix 3Y … Page 2“,
        // Fassung 01/01/2011), auch mit zerfallenen Buchstaben; dann zerfallene Zahlen zusammenfuegen („8 0,000“, „202 6“)
        val fuss = listOf("for personal use only", "see chapter 19 for defined terms").joinToString("|") { bezeichnung(it) }
        // Typografische Bindestriche (‐ ‑ – —) wie normale behandeln
        val t = zeilen.joinToString(" ").replace(Char(0xA0), ' ').replace(Regex("[\u2010\u2011\u2012\u2013\u2014]"), "-").replace(Regex("\\s+"), " ")
            .replace(Regex("(?i)\\+?\\s*(?:$fuss)\\.?"), " ")
            .replace(Regex("(?i)" + bezeichnung("appendix 3y") + ".{0,70}?" + bezeichnung("page") + "\\s*\\d+"), " ")
            .replace(Regex("\\b0?1\\s?/\\s?0?1\\s?/\\s?(?:2\\s?0\\s?)?1\\s?1\\b"), " ")
            .replace(Regex("\\s+"), " ")
            // Registernummern nach dem Firmennamen („EMC Gold Corp. 625 522 250“, „ABN 86 620 283 561“) sind keine Mengen
            .replace(Regex("(?i)\\b(abn|acn|arbn|limited|ltd|corp\\.?|inc\\.?|plc)\\s*:?\\s*\\d{2,3} \\d{3} \\d{3}(?: \\d{3})?\\b"), "$1")
            // zerfallene Zahlen mit Nachkommastellen („$ 17 , 9 08.47“)
            .replace(Regex("(?<=\\d) (?=\\d+\\.\\d)"), "")
            .replace(Regex("(?<=\\d) (?=\\d+(?![./)\\d]))"), "").replace(Regex("(?<=\\d) ?, ?(?=\\d{3}(?!\\d))"), ",")
        fun stelle(muster: String, ab: Int = 0) = Regex(bezeichnung(muster), RegexOption.IGNORE_CASE).find(t, ab)?.range
        fun zwischen(von: String, bis: String): String {
            val a = stelle(von) ?: return ""
            val b = stelle(bis, a.last + 1)?.first ?: minOf(t.length, a.last + 400)
            return t.substring(a.last + 1, maxOf(a.last + 1, b)).trim()
        }
        val name = zwischen("name of director", "date of last notice|date of appointment|date of this notice|part 1|direct or indirect|date of change").take(80).trim()
            .let { ganz(it) }
        val datum = InsiderQuellen.tag(zwischen("date of change", "no. of securities held prior|no of securities held prior")).ifEmpty { m.tag }
        // „Class“ kann auch im Bestand stehen („Class B Perf Rights“); die Zeile kommt nach dem Bestand vor der Aenderung
        val klasse = run {
            val nach = stelle("no. of securities held prior|no of securities held prior")?.last ?: 0
            val a = Regex("(?i)\\bclass\\b").findAll(t, nach).lastOrNull { k -> stelle("number acquired", nach)?.let { k.range.first < it.first } ?: true }
            val b = stelle("number acquired", nach)?.first
            (if (a != null && b != null && b > a.range.last) t.substring(a.range.last + 1, b).trim() else zwischen("class", "number acquired"))
                .substringBefore("For personal use only").trim()
        }
        val wertText = zwischen("value/consideration|value / consideration", "no. of securities held after|no of securities held after")
        // Mengen; stehen die Spalten durcheinander, hilft der Satz unter Value/Consideration („750,000 … shares acquired at“)
        fun ausSatz(verb: String) = Regex("(\\d[\\d,]*)\\s*(?:fully paid\\s*)?(?:ordinary\\s*)?shares?\\s*(?:were\\s*)?(?:$verb)", RegexOption.IGNORE_CASE)
            .find(wertText)?.groupValues?.get(1)?.let { InsiderQuellen.zahl(it) } ?: 0.0
        val gekauft = aktien(zwischen("number acquired", "number disposed")).takeIf { it > 0 } ?: ausSatz("acquired|purchased|bought|issued")
        val verkauft = aktien(zwischen("number disposed", "value/consideration|value / consideration")).takeIf { it > 0 } ?: ausSatz("disposed|sold")
        // Betraege mit Einordnung: je Aktie („$0.0073 per share“, „average of $1.13“, „$3.99 per ordinary stapled security“),
        // Summe („Total consideration: $32,475“, „$37,500 in total“) oder offen
        class Betrag(val wert: Double, val je: Boolean, val gesamt: Boolean)
        val betraege = Regex("(?i)(total[^$\\d]{0,30}|average[^$\\d]{0,25}|avg\\.?[^$\\d]{0,15}|vwap[^$\\d]{0,15}|@\\s*)?(?:aud\\s*)?\\$\\s?(\\d[\\d,]*(?:\\.\\d+)?)\\s*" +
            "(per\\s+(?:[a-z]+\\s+){0,3}?(?:shares?|security|securities|units?|ord|fpo|rights?|options?)\\b|each|/\\s?share|average price|in total)?").findAll(wertText).mapNotNull { r ->
            // Komma vor drei Ziffern trennt Tausender („$76,500“), sonst ist es ein Dezimalkomma („$ 896,70“)
            val w = r.groupValues[2].replace(Regex(",(?=\\d{3}(?!\\d))"), "").replace(",", ".").toDoubleOrNull() ?: return@mapNotNull null
            val vor = r.groupValues[1].lowercase()
            val nach = r.groupValues[3].lowercase()
            val gesamt = vor.startsWith("total") || nach == "in total"
            Betrag(w, je = !gesamt && (vor.startsWith("average") || vor.startsWith("avg") || vor.startsWith("vwap") || vor.startsWith("@") || nach.isNotEmpty()), gesamt = gesamt)
        }.filter { it.wert > 0 }.toList()
        // Ohne Dollarzeichen („11,250“) die erste groessere Zahl als Summe
        val ohneZeichen = Regex("(?<![\\d.,])\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?(?![\\d,])|(?<![\\d.,])\\d{3,}(?:\\.\\d+)?(?![\\d,%])").find(wertText)?.value?.let { InsiderQuellen.zahl(it) } ?: 0.0
        // Gegenleistung aus Bezuegen oder Plaenen („funded from sacrificed directors' fees“, „issued as incentives“)
        val bezuege = Regex("(?i)sacrific|directors'?\\s*fees|in lieu of|nil cash consideration|as incentives?|incentive (plan|scheme)").containsMatchIn(wertText)
        val natur = zwischen("nature of change", "part 2").replace(Regex("(?i)example:.*?buy\\s*-\\s*back"), "")
        val markt = Regex("(?i)on\\s*-?\\s*market|off\\s*-?\\s*market trade|market purchase|market sale").containsMatchIn(natur) &&
            !Regex("(?i)exercise|vesting|issue of|dividend reinvestment|\\bdrp\\b|placement|entitlement|share plan|conversion|takeover").containsMatchIn(natur)
        val aktie = !Regex("(?i)option|right|warrant|convertible|note|unit").containsMatchIn(klasse) || Regex("(?i)ordinary|fully paid|\\bfpo\\b").containsMatchIn(klasse)
        // Ausdruecklicher Kauf oder Verkauf an der Boerse geht vor Planbegriffen weiter hinten in gemischten Meldungen
        val boerseKauf = Regex("(?i)on\\s*-?\\s*market\\s*(purchase|acquisition|buy)").containsMatchIn(natur + " " + wertText)
        val boerseVerkauf = Regex("(?i)on\\s*-?\\s*market\\s*(disposal|sale|sell)").containsMatchIn(natur + " " + wertText)
        val (art, n) = when {
            bezuege -> "S" to maxOf(gekauft, verkauft)
            aktie && boerseVerkauf && !boerseKauf && verkauft > 0 -> "V" to verkauft
            aktie && boerseKauf && !boerseVerkauf && gekauft > 0 -> "K" to gekauft
            markt && aktie && gekauft > 0 && verkauft == 0.0 -> "K" to gekauft
            markt && aktie && verkauft > 0 && gekauft == 0.0 -> "V" to verkauft
            else -> "S" to maxOf(gekauft, verkauft)
        }
        if (name.isBlank() || n <= 0) return null
        // Bei Sonstigem (Zuteilung, Plan, Platzierung) ist der Betrag meist ein Schaetzwert und wird nicht gezeigt
        // Betrag: die Summe, sonst Preis je Aktie mal Menge, sonst ein offener Betrag, der als Summe oder Preis je Aktie
        // gelesen wird, je nachdem, was einen glaubwuerdigen Kurs ergibt (ASX-Werte kosten zwischen 0,001 und einigen hundert Dollar)
        fun glaubhaft(p: Double) = p in 0.0009..600.0
        val gesamt = betraege.filter { it.gesamt }
        val je = betraege.filter { it.je }
        val roh = when {
            gesamt.isNotEmpty() -> gesamt.first().wert
            je.isNotEmpty() -> je.first().wert * n
            betraege.isNotEmpty() -> betraege.first().wert.let { b -> if (!glaubhaft(b / n) && glaubhaft(b)) b * n else b }
            else -> ohneZeichen
        }
        // Passt die Summe nicht zu den genannten Preisen je Aktie (mehrere Teilgeschaefte), zaehlt der mittlere Preis
        val geprueft = if (gesamt.isNotEmpty() && je.isNotEmpty() && n > 0 && (roh / n < je.minOf { it.wert } * 0.5 || roh / n > je.maxOf { it.wert } * 2))
            je.map { it.wert }.average() * n else roh
        // Mengen ueber zwei Milliarden und Betraege ueber 500 Mio. Dollar stammen aus verrutschten Spalten
        if (n > 2e9) return null
        val wert = if (art == "S" || n <= 0 || !glaubhaft(geprueft / n) || geprueft > 5e8) 0.0 else geprueft
        return Geschaeft(
            // Gesperrte Schrift („S i t e M i n d e r“) zusammenziehen
            e = m.firma.ifBlank { zwischen("name of entity", "abn|arbn|acn|a c n|we (the entity)").take(80).trim(' ', ':', '-') }
                .replace(Regex("(?<!\\S)(?:\\p{L} ){3,}\\p{L}(?!\\S)")) { it.value.replace(" ", "") }, isin = m.isin, p = name, r = "A", t = "Director",
            i = if (aktie) "Aktie" else klasse.take(60), art = art, d = datum, v = m.tag,
            n = n, preis = if (n > 0 && wert > 0) wert / n else 0.0, wert = wert, w = "AUD", id = m.schluessel, q = asxPdf(m.schluessel), c = m.symbol,
        )
    }

    @Volatile private var asxCache: Triple<Long, String, List<AsxMeldung>>? = null

    // Meldungen Appendix 3Y aller Firmen seit von; fuer zwoelf Stunden zwischengespeichert, damit mehrere Aktien
    // dieselbe Liste nutzen.
    private suspend fun asxMarkt(netz: Netz, von: String): List<AsxMeldung>? {
        asxCache?.takeIf { System.currentTimeMillis() - it.first < 12 * 3_600_000L && it.second <= von }?.let { return it.third }
        val aus = mutableListOf<AsxMeldung>()
        for (seite in 0 until 12) {
            val text = netz.text(asxListe(seite), mapOf("Accept" to "application/json")) ?: return if (seite == 0) null else aus
            aus += asxListeLesen(text)
            val letzter = Regex("\"date\":\"(\\d{4}-\\d{2}-\\d{2})").findAll(text).lastOrNull()?.groupValues?.get(1) ?: break
            if (letzter < von) break
        }
        asxCache = Triple(System.currentTimeMillis(), von, aus)
        return aus
    }

    suspend fun asxHolen(netz: Netz, symbol: String, isin: String, firma: String, von: String): List<Geschaeft>? {
        val liste = asxMarkt(netz, von)?.filter { it.symbol.equals(symbol, true) } ?: return null
        return liste.filter { it.tag >= von }.take(15).mapNotNull { m ->
            netz.bytes(asxPdf(m.schluessel))?.let { asxLesen(PdfText.zeilen(it), m.copy(isin = m.isin.ifBlank { isin }, firma = m.firma.ifBlank { firma })) }
        }
    }

    // ------------------------------------------------------------ Kanada (SEDI, nur Link)

    const val SEDI = "https://www.sedi.ca/sedi/SVTItdController?locale=en_CA"

    // ------------------------------------------------------------ Zuordnung ueber das Yahoo-Symbol

    // Land und Kennung an der Heimatboerse fuer Aktien, die je Aktie geholt werden: IN (NSE-Symbol), HK (Code), AU (ASX-Code).
    // ADRs und OTC-Kuerzel grosser Werte, deren Insider an der Heimatboerse melden.
    private val ADR = mapOf(
        "TCEHY" to ("HK" to "700"), "TCTZF" to ("HK" to "700"), "BABA" to ("HK" to "9988"), "BABAF" to ("HK" to "9988"),
        "JD" to ("HK" to "9618"), "BIDU" to ("HK" to "9888"), "NTES" to ("HK" to "9999"), "BYDDY" to ("HK" to "1211"),
        "BYDDF" to ("HK" to "1211"), "XIACY" to ("HK" to "1810"), "XIACF" to ("HK" to "1810"), "MPNGY" to ("HK" to "3690"),
        "MPNGF" to ("HK" to "3690"), "LI" to ("HK" to "2015"), "NIO" to ("HK" to "9866"), "XPEV" to ("HK" to "9868"),
        "AAGIY" to ("HK" to "1299"), "LNVGY" to ("HK" to "992"), "GELYY" to ("HK" to "175"), "ANPDY" to ("HK" to "2020"),
        "INFY" to ("IN" to "INFY"), "WIT" to ("IN" to "WIPRO"), "HDB" to ("IN" to "HDFCBANK"), "IBN" to ("IN" to "ICICIBANK"),
        "RDY" to ("IN" to "DRREDDY"), "TTM" to ("IN" to "TATAMOTORS"),
        "BHP" to ("AU" to "BHP"), "WDS" to ("AU" to "WDS"), "JHX" to ("AU" to "JHX"),
    )

    fun boerse(symbol: String): Pair<String, String>? {
        val s = symbol.uppercase()
        ADR[s]?.let { return it }
        return when {
            s.endsWith(".NS") -> "IN" to s.removeSuffix(".NS")
            s.endsWith(".BO") -> "IN" to s.removeSuffix(".BO")
            s.endsWith(".HK") -> "HK" to s.removeSuffix(".HK").trimStart('0').ifEmpty { "0" }
            s.endsWith(".AX") -> "AU" to s.removeSuffix(".AX")
            s.endsWith(".TO") || s.endsWith(".V") || s.endsWith(".NE") || s.endsWith(".CN") -> "CA" to s.substringBefore('.')
            else -> null
        }
    }

    // Neue Meldungen einer Aktie seit ab (ohne mitgelieferten Stand: die letzten 90 Tage, in Hongkong ein Jahr).
    // Ein BSE-Code (Symbol mit .BO) hat keinen Abruf; dafuer gibt es nur den mitgelieferten Stand.
    suspend fun holen(netz: Netz, symbol: String, isin: String, firma: String, heute: java.time.LocalDate, ab: java.time.LocalDate? = null): Pair<String, List<Geschaeft>>? {
        val (land, code) = boerse(symbol) ?: return null
        val bis = heute.toString()
        val liste = when (land) {
            "IN" -> if (code.all { it.isDigit() }) emptyList() else nseHolen(netz, code, (ab ?: heute.minusDays(90)).toString(), bis)
            "HK" -> hkHolen(netz, code, isin, firma, (ab ?: heute.minusDays(365)).toString(), bis)
            "AU" -> asxHolen(netz, code, isin, firma, (ab ?: heute.minusDays(90)).toString())
            else -> emptyList()
        } ?: return null
        return land to liste.map { if (it.c.isBlank()) it.copy(c = code) else it }
    }

    // Firmennamen vergleichbar machen („Karur Vysya Bank Ltd“ und „Karur Vysya Bank Limited“).
    fun name(s: String): String = s.lowercase().replace("&", " and ").replace(Regex("[^a-z0-9 ]"), " ")
        .split(' ').filter { it.isNotBlank() && it !in setOf("ltd", "limited", "the", "co", "company", "corp", "corporation", "inc", "plc", "holdings", "holding", "group") }
        .joinToString(" ")

    // Gehoert ein Geschaeft aus dem Landesstand zu dieser Aktie: ueber die Kennung, die ISIN oder in Indien den Namen.
    fun passt(g: Geschaeft, land: String, code: String, isin: String, firma: String): Boolean = when {
        g.c.isNotBlank() && g.c.equals(code, true) -> true
        isin.length == 12 && g.isin == isin -> true
        (land == "IN" || land == "CA") && firma.isNotBlank() -> name(g.e).let { it.isNotEmpty() && it == name(firma) }
        else -> false
    }
}
