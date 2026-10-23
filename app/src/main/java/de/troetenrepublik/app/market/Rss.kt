package de.troetenrepublik.app.market

import java.nio.charset.Charset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

// Nachrichtenfeeds: Kategorien, fest eingebaute Quellen, ein kleiner Leser fuer RSS 2.0 und Atom und das Zusammenfuehren
// der Schlagzeilen. Fest eingebaut sind nur Quellen, deren Bedingungen das Lesen in einer nicht kommerziellen App mit
// Quellenangabe erlauben. Fuer den persoenlichen, nicht kommerziellen Gebrauch mit Quellenangabe: Yahoo Finance, CNBC,
// MarketWatch, tagesschau.de (hoechstens 60 Abrufe je Stunde, deshalb fuenf Minuten Abstand). Gemeinfrei als Werke der
// US-Regierung: Federal Reserve, Bureau of Economic Analysis. Frei mit Quellenangabe: EZB, EU-Kommission, Destatis,
// Schweizerische Nationalbank (nicht kommerziell), Bank of England (persoenlicher, nicht kommerzieller Gebrauch).
// Nicht eingebaut: Reuters (keine oeffentlichen Feeds, Weitergabe untersagt), FinancialJuice und Cointelegraph
// (Bedingungen untersagen Sammeln und Anzeigen), Deutsche Bundesbank (Stammzertifikat ihres Servers fehlt in Android 15).
object Rss {

    // Themen der Feeds; bei tagesschau.de, Yahoo und CNBC sind sie die Unterkategorien eines Anbieters.
    enum class Kategorie { MAERKTE, UNTERNEHMEN, KONJUNKTUR, ZENTRALBANKEN, ROHSTOFFE_DEVISEN, KRYPTO, TRUMP, BRIEFE, BLOGS, EIGENE, GEMERKT }

    // Reiter der Nachrichtenseite, zwischen denen man waagerecht wischt. Notenbanken und Behoerden sind je ein Reiter
    // mit den einzelnen Haeusern als Unterkategorien.
    enum class Anbieter { ALLE, TRUMP, YAHOO, TAGESSCHAU, CNBC, MARKETWATCH, GUARDIAN, FINNHUB, ALPHAVANTAGE, NOTENBANKEN, BEHOERDEN, BRIEFE, EIGENE, GEMERKT }

    // Art einer Quelle: RSS oder eine Schnittstelle mit eigenem Schluessel (siehe NachrichtenApis).
    enum class Art(val gruppe: String) {
        RSS(""), GUARDIAN(Abrufkontingent.GUARDIAN), FINNHUB(Abrufkontingent.FINNHUB), ALPHAVANTAGE(Abrufkontingent.ALPHAVANTAGE),
        // Feed des Archivs der Truth-Social-Beitraege.
        TRUTH(""),
        // Uebersichtsseite mit Briefen, gelesen nach Briefquellen.Regel.
        SEITE(""),
    }

    // Ein fest eingebauter oder selbst eingetragener Feed. adresse kann {lang} enthalten (Region und Sprache von Yahoo).
    // nurSprache: Bundesbank-Eintraege kommen deutsch und englisch gemischt; es zaehlt nur der Link mit diesem Pfadteil
    // ("/de/" oder "/en/"), ersetzt durch die Sprache der App.
    data class Quelle(
        val schluessel: String,
        val name: String,
        val adresse: String,
        val kategorie: Kategorie,
        val anbieter: Anbieter,
        // Kurzname als Unterkategorie bei Notenbanken, Behoerden und eigenen Feeds („EZB“, „Fed“).
        val kurz: String = name,
        val nurSprache: Boolean = false,
        // Mindestabstand zwischen zwei Abrufen dieses Feeds.
        val abstandMs: Long = 2 * 60_000L,
        // Herausgeber aus der Domain des Links (Yahoo sammelt Artikel anderer Seiten).
        val sammelt: Boolean = false,
        val art: Art = Art.RSS,
        // Artikel nur mit Skript lesbar. Die Quelle gibt es deshalb nur, wo die App eine Web-Ansicht hat (Android).
        val nurMitWebAnsicht: Boolean = false,
        // Wie die Briefe auf der Uebersichtsseite zu finden sind (nur Art.SEITE).
        val regel: Briefquellen.Regel? = null,
    ) {
        // Unterkategorie im Reiter des Anbieters: das Thema, bei Haeusern und eigenen Feeds der Feed selbst.
        val unter: String
            get() = if (anbieter == Anbieter.NOTENBANKEN || anbieter == Anbieter.BEHOERDEN || anbieter == Anbieter.EIGENE) schluessel else kategorie.name
    }

    // Yahoo-Schlagzeilen zu mehreren Kuerzeln; symbole mit Komma getrennt.
    fun yahoo(symbole: List<String>, deutsch: Boolean): String =
        "https://feeds.finance.yahoo.com/rss/2.0/headline?s=" + symbole.joinToString(",") { enc(it) } +
            if (deutsch) "&region=DE&lang=de-DE" else "&region=US&lang=en-US"

    // Fest eingebaute Quellen, nach Anbietern geordnet. Die Yahoo-Feeds buendeln je Thema die wichtigsten Werte.
    fun eingebaut(deutsch: Boolean): List<Quelle> = listOf(
        Quelle("tagesschau-boerse", "tagesschau.de", "https://www.tagesschau.de/wirtschaft/finanzen/index~rss2.xml", Kategorie.MAERKTE, Anbieter.TAGESSCHAU, abstandMs = 5 * 60_000L),
        Quelle("tagesschau-wirtschaft", "tagesschau.de", "https://www.tagesschau.de/wirtschaft/index~rss2.xml", Kategorie.KONJUNKTUR, Anbieter.TAGESSCHAU, abstandMs = 5 * 60_000L),
        Quelle("yahoo-maerkte", "Yahoo Finance", yahoo(listOf("^GDAXI", "^STOXX50E", "^GSPC", "^IXIC", "^N225"), deutsch), Kategorie.MAERKTE, Anbieter.YAHOO, sammelt = true),
        Quelle("yahoo-unternehmen", "Yahoo Finance", yahoo(listOf("AAPL", "MSFT", "NVDA", "AMZN", "GOOGL", "META", "TSLA", "SAP.DE", "SIE.DE", "ALV.DE", "ASML.AS", "MC.PA", "NOVO-B.CO"), deutsch), Kategorie.UNTERNEHMEN, Anbieter.YAHOO, sammelt = true),
        Quelle("yahoo-rohstoffe", "Yahoo Finance", yahoo(listOf("GC=F", "CL=F", "SI=F", "EURUSD=X", "^TNX"), deutsch), Kategorie.ROHSTOFFE_DEVISEN, Anbieter.YAHOO, sammelt = true),
        Quelle("yahoo-krypto", "Yahoo Finance", yahoo(listOf("BTC-USD", "ETH-USD", "SOL-USD", "XRP-USD"), deutsch), Kategorie.KRYPTO, Anbieter.YAHOO, sammelt = true),
        Quelle("cnbc-maerkte", "CNBC", "https://www.cnbc.com/id/15839069/device/rss/rss.html", Kategorie.MAERKTE, Anbieter.CNBC),
        Quelle("cnbc-top", "CNBC", "https://www.cnbc.com/id/100003114/device/rss/rss.html", Kategorie.UNTERNEHMEN, Anbieter.CNBC),
        Quelle("cnbc-wirtschaft", "CNBC", "https://www.cnbc.com/id/20910258/device/rss/rss.html", Kategorie.KONJUNKTUR, Anbieter.CNBC),
        // Beitraege von Donald Trump ueber das Archiv „Trump's Truth“
        Quelle("truth", "Truth Social", TruthArchiv.adresse(400), Kategorie.TRUMP, Anbieter.TRUMP, abstandMs = 5 * 60_000L, art = Art.TRUTH),
        Quelle("marketwatch", "MarketWatch", "https://feeds.content.dowjones.io/public/rss/mw_topstories", Kategorie.MAERKTE, Anbieter.MARKETWATCH, nurMitWebAnsicht = true),
        Quelle("ezb", if (deutsch) "EZB" else "ECB", "https://www.ecb.europa.eu/rss/press.html", Kategorie.ZENTRALBANKEN, Anbieter.NOTENBANKEN),
        Quelle("fed", "Federal Reserve", "https://www.federalreserve.gov/feeds/press_all.xml", Kategorie.ZENTRALBANKEN, Anbieter.NOTENBANKEN, kurz = "Fed"),
        Quelle("boe", "Bank of England", "https://www.bankofengland.co.uk/rss/news", Kategorie.ZENTRALBANKEN, Anbieter.NOTENBANKEN),
        Quelle("snb", if (deutsch) "Schweizerische Nationalbank" else "Swiss National Bank", "https://www.snb.ch/public/" + (if (deutsch) "de" else "en") + "/rss/news", Kategorie.ZENTRALBANKEN, Anbieter.NOTENBANKEN, kurz = "SNB"),
        Quelle("destatis", "Destatis", "https://www.destatis.de/SiteGlobals/Functions/RSSFeed/DE/RSSNewsfeed/Aktuell.xml", Kategorie.KONJUNKTUR, Anbieter.BEHOERDEN),
        Quelle("eu-kommission", if (deutsch) "EU-Kommission" else "European Commission", "https://ec.europa.eu/commission/presscorner/api/rss?language=" + if (deutsch) "de" else "en", Kategorie.KONJUNKTUR, Anbieter.BEHOERDEN),
        Quelle("bea", "US Bureau of Economic Analysis", "https://apps.bea.gov/rss/rss.xml", Kategorie.KONJUNKTUR, Anbieter.BEHOERDEN, kurz = "BEA", abstandMs = 15 * 60_000L),
    ) + briefeUndBlogs()

    // Briefe von Investoren und Unternehmen und Blogs zu Wirtschaft und Geldanlage, englisch und deutsch. Briefe
    // kommen von Uebersichtsseiten (Briefquellen), Blogs als Feed; beides wird hoechstens einmal je Stunde abgefragt.
    private fun briefeUndBlogs(): List<Quelle> {
        fun brief(s: String, name: String, url: String, regel: Briefquellen.Regel) =
            Quelle("brief-$s", name, url, Kategorie.BRIEFE, Anbieter.BRIEFE, abstandMs = 6 * 3_600_000L, art = Art.SEITE, regel = regel)
        fun blog(s: String, name: String, url: String) = Quelle("blog-$s", name, url, Kategorie.BLOGS, Anbieter.BRIEFE, abstandMs = 60 * 60_000L)
        val aktionaere = "Brief an die Aktionäre {jahr}" to "Letter to shareholders {jahr}"
        return listOf(
            brief("berkshire", "Berkshire Hathaway", "https://www.berkshirehathaway.com/letters/gealetters.html",
                Briefquellen.Regel("href=\"([^\"]*?(\\d{4})ltr\\.pdf)\"", 1, "https://www.berkshirehathaway.com/letters/", aktionaere.first, aktionaere.second, jahr = 2)),
            brief("buffett", "Warren Buffett", "https://www.berkshirehathaway.com/letters/letters.html",
                Briefquellen.Regel("href=\"([^\"]*?(\\d{4})ltr\\.pdf)\"", 1, "https://www.berkshirehathaway.com/letters/", "Brief an die Aktionäre von Berkshire Hathaway {jahr}", "Berkshire Hathaway letter to shareholders {jahr}", jahr = 2)),
            brief("oaktree", "Oaktree (Howard Marks)", "https://www.oaktreecapital.com/insights/memos",
                Briefquellen.Regel("datetime=\">?(\\d{4}-\\d{2}-\\d{2})T[^\"]*\"[^>]*>[^<]*</time>\\s*<a class=\"oc-title-link\" href=\"(/insights/memo/[^\"]+)\">([^<]+)</a>", 2,
                    "https://www.oaktreecapital.com", "Memo: {titel}", "Memo: {titel}", titel = 3, datum = 1, anzahl = 3)),
            brief("markel", "Markel (Tom Gayner)", "https://www.mklgroup.com/who-we-are/shareholder-letters",
                Briefquellen.Regel("(https://content\\.markel\\.com/api/public/content/shareholder-letter-(\\d{4}))", 1, "https://www.mklgroup.com", aktionaere.first, aktionaere.second, jahr = 2)),
            brief("fairfax", "Fairfax (Prem Watsa)", "https://www.fairfax.ca/investors/",
                Briefquellen.Regel("href=\"([^\"]*Shareholders?-Letter-(\\d{4})[^\"]*\\.pdf)\"", 1, "https://www.fairfax.ca", aktionaere.first, aktionaere.second, jahr = 2)),
            brief("jpm", "JPMorgan Chase (Jamie Dimon)", "https://www.jpmorganchase.com/ir/annual-report",
                Briefquellen.Regel("(/ir/annual-report/(\\d{4})/ar-ceo-letters)", 1, "https://www.jpmorganchase.com", aktionaere.first, aktionaere.second, jahr = 2)),
            brief("amazon", "Amazon (Andy Jassy)", "https://www.aboutamazon.com/about-us/shareholder-letters",
                Briefquellen.Regel("href=\"([^\"]*?(\\d{4})-letter-to-shareholders)\"", 1, "https://www.aboutamazon.com", aktionaere.first, aktionaere.second, jahr = 2)),
            brief("gmo", "GMO", "https://www.gmo.com/americas/research-library/",
                Briefquellen.Regel("href=\"([^\"]*_gmoquarterlyletter/)\"", 1, "https://www.gmo.com", "Quarterly Letter: {titel}", "Quarterly Letter: {titel}")),
            brief("himcap", "Himalaya Capital (Li Lu)", "https://www.himcap.com/publications",
                Briefquellen.Regel("href=\"([^\"]*\\.pdf)\"", 1, "https://www.himcap.com", "{titel}", "{titel}", anzahl = 2, ohne = "Chinese")),
            blog("fred", "FRED Blog (Federal Reserve St. Louis)", "https://fred.stlouisfed.org/blog/rss"),
            blog("bu", "Bank Underground (Bank of England)", "https://bankunderground.co.uk/feed/"),
            blog("feds", "FEDS Notes (Federal Reserve)", "https://www.federalreserve.gov/feeds/feds_notes.xml"),
            blog("damodaran", "Musings on Markets (Aswath Damodaran)", "https://aswathdamodaran.blogspot.com/feeds/posts/default?max-results=10"),
            blog("awocs", "A Wealth of Common Sense", "https://awealthofcommonsense.com/feed/"),
            blog("odad", "Of Dollars And Data", "https://ofdollarsanddata.com/feed/"),
            blog("klement", "Klement on Investing", "https://klementoninvesting.substack.com/feed"),
            blog("ifo", "ifo Institut", "https://www.ifo.de/rss"),
            blog("iw", "IW Köln", "https://www.iwkoeln.de/rss/iw-kurzberichte-deutschsprachig.xml"),
            blog("diw", "DIW Berlin", "https://www.diw.de/de/rss_publications.xml"),
            blog("makronom", "Makronom", "https://makronom.de/feed"),
            blog("kommer", "Gerd Kommer", "https://gerd-kommer.de/feed/"),
        )
    }

    // Quellen mit eigenem Schluessel; ohne Schluessel fehlt der Anbieter. Abstaende nach den Kontingenten
    // (Abrufkontingent.REGELN): Guardian zwei Abfragen je 15 Minuten (hoechstens 192 am Tag, Grenze 450),
    // Finnhub vier Kategorien je 10 Minuten, Alpha Vantage eine Abfrage je Stunde (Grenze 22 am Tag).
    fun mitSchluessel(guardian: String, finnhub: String, alphaVantage: String): List<Quelle> = buildList {
        if (guardian.isNotBlank()) {
            add(Quelle("guardian-maerkte", "The Guardian", NachrichtenApis.guardianListe(guardian, tag = "business/stock-markets"), Kategorie.MAERKTE, Anbieter.GUARDIAN, abstandMs = 15 * 60_000L, art = Art.GUARDIAN))
            add(Quelle("guardian-wirtschaft", "The Guardian", NachrichtenApis.guardianListe(guardian, rubrik = "business"), Kategorie.KONJUNKTUR, Anbieter.GUARDIAN, abstandMs = 15 * 60_000L, art = Art.GUARDIAN))
        }
        if (finnhub.isNotBlank()) {
            add(Quelle("finnhub-maerkte", "Finnhub", NachrichtenApis.finnhubListe(finnhub, "general"), Kategorie.MAERKTE, Anbieter.FINNHUB, abstandMs = 10 * 60_000L, art = Art.FINNHUB))
            add(Quelle("finnhub-uebernahmen", "Finnhub", NachrichtenApis.finnhubListe(finnhub, "merger"), Kategorie.UNTERNEHMEN, Anbieter.FINNHUB, abstandMs = 10 * 60_000L, art = Art.FINNHUB))
            add(Quelle("finnhub-devisen", "Finnhub", NachrichtenApis.finnhubListe(finnhub, "forex"), Kategorie.ROHSTOFFE_DEVISEN, Anbieter.FINNHUB, abstandMs = 10 * 60_000L, art = Art.FINNHUB))
            add(Quelle("finnhub-krypto", "Finnhub", NachrichtenApis.finnhubListe(finnhub, "crypto"), Kategorie.KRYPTO, Anbieter.FINNHUB, abstandMs = 10 * 60_000L, art = Art.FINNHUB))
        }
        if (alphaVantage.isNotBlank()) {
            add(Quelle("alphavantage", "Alpha Vantage", NachrichtenApis.alphaVantageListe(alphaVantage), Kategorie.MAERKTE, Anbieter.ALPHAVANTAGE, abstandMs = 60 * 60_000L, art = Art.ALPHAVANTAGE))
        }
    }

    // Auf Android ist jede Quelle in der App lesbar.
    fun hierLesbar(q: Quelle): Boolean = true

    // Artikel dieser Quelle lassen sich hier nur im Browser lesen.
    fun nurImBrowser(link: String): Boolean = !de.troetenrepublik.app.plattform.Plattform.webAnsicht && link.contains("marketwatch.com/")

    // Eigener Feed als Quelle (Anbieter „Eigene Feeds“).
    fun eigene(name: String, url: String): Quelle =
        Quelle("eigen:$url", name.ifBlank { domain(url) }, url, Kategorie.EIGENE, Anbieter.EIGENE)

    // Eine Schlagzeile. quellen: Schluessel aller Feeds, in denen sie steht; kategorien: deren Themen.
    data class Eintrag(
        val titel: String,
        val link: String,
        val zeit: Long,
        val zusammenfassung: String = "",
        // Herausgeber (bei Yahoo die Seite des Artikels, etwa reuters.com), sonst der Name der Quelle.
        val herausgeber: String = "",
        // Name des Feeds, aus dem die Schlagzeile kommt (Quellenangabe).
        val quelle: String = "",
        val kategorien: Set<Kategorie> = emptySet(),
        val quellen: Set<String> = emptySet(),
        val autor: String = "",
        val gemerktAm: Long = 0L,
        // Finanzielle Themen eines Truth-Social-Beitrags, siehe TruthNews.themen.
        val themen: List<String> = emptyList(),
    ) {
        val id: String get() = link.ifBlank { titel }

        // Quellenangabe fuer die Liste: Herausgeber und, wenn verschieden, der Feed („reuters.com · Yahoo Finance“).
        val quellenText: String
            get() = if (herausgeber.isBlank() || herausgeber.equals(quelle, true)) quelle
            else if (quelle.isBlank()) herausgeber else "$herausgeber · $quelle"
    }

    // Lesen

    // Rohdaten eines Feeds als Text. Die Kodierung steht in der XML-Kopfzeile; fehlt sie, gilt UTF-8. Nach dem
    // HTTP-Kopf allein kommen Umlaute mancher Behoerdenfeeds verstuemmelt an.
    fun text(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        val kopf = String(bytes, 0, minOf(bytes.size, 200), Charsets.ISO_8859_1)
        val name = Regex("encoding=[\"']([A-Za-z0-9._-]+)[\"']").find(kopf)?.groupValues?.get(1)
        val cs = name?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
        return String(bytes, cs)
    }

    private val itemRx = Regex("<item(?:\\s[^>]*)?>(.*?)</item>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val entryRx = Regex("<entry(?:\\s[^>]*)?>(.*?)</entry>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

    private fun feld(block: String, name: String): String? =
        Regex("<$name(?:\\s[^>]*)?>(.*?)</$name>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).find(block)?.groupValues?.get(1)

    private fun cdata(s: String): String = s.trim().removePrefix("<![CDATA[").removeSuffix("]]>").trim()

    // Text ohne Auszeichnung und mit aufgeloesten Entitaeten.
    fun klartext(s: String): String {
        var t = cdata(s).replace(Regex("<!\\[CDATA\\[|]]>"), "")
        t = t.replace(Regex("<[^>]+>"), " ")
        t = t.replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.takeIf { it in 1..0x10FFFF }?.let { String(Character.toChars(it)) } ?: m.value }
        t = t.replace(Regex("&#x([0-9a-fA-F]+);")) { m -> m.groupValues[1].toIntOrNull(16)?.takeIf { it in 1..0x10FFFF }?.let { String(Character.toChars(it)) } ?: m.value }
        t = t.replace("&nbsp;", " ").replace("&quot;", "\"").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
        // Als Entitaeten geschriebene Auszeichnung (&lt;b&gt;) erst nach dem Aufloesen entfernen; nur, was wie ein Tag aussieht
        t = t.replace(Regex("</?[A-Za-z][^<>]*>"), " ")
        return t.replace(Regex("\\s+"), " ").trim()
    }

    // Zeitangabe aus RSS (RFC 1123, auch mit einstelliger Tageszahl oder „GMT“) oder Atom (ISO 8601); 0 = unbekannt.
    fun zeit(s: String?): Long {
        var t = s?.let { klartext(it) }?.takeIf { it.isNotBlank() } ?: return 0L
        // US-Zeitzonen als Kuerzel (BEA: „08:30:00 EDT“) kennt RFC 1123 nicht
        t = t.replace(Regex(" (EDT|EST|CDT|CST|PDT|PST|UT|UTC)$")) { m ->
            " " + when (m.groupValues[1]) { "EDT" -> "-0400"; "EST", "CDT" -> "-0500"; "CST" -> "-0600"; "PDT" -> "-0700"; "PST" -> "-0800"; else -> "GMT" }
        }
        runCatching { return ZonedDateTime.parse(t, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
        runCatching { return java.time.OffsetDateTime.parse(t).toInstant().toEpochMilli() }
        runCatching { return java.time.Instant.parse(t).toEpochMilli() }
        return 0L
    }

    // Domain eines Links ohne „www.“, als Herausgeber.
    fun domain(link: String): String = runCatching { java.net.URI(link).host.orEmpty().removePrefix("www.") }.getOrDefault("")

    // Eintraege eines RSS- oder Atom-Feeds. quelle ist der Name des Feeds; herausgeberAusLink: Herausgeber aus
    // der Domain des Links (fuer Yahoo, das Artikel anderer Seiten sammelt), sonst der Name des Feeds.
    fun lesen(xml: String, quelle: String, kategorie: Kategorie, herausgeberAusLink: Boolean = false, schluessel: String = ""): List<Eintrag> {
        val rss = itemRx.findAll(xml).map { it.groupValues[1] }.toList()
        val bloecke = rss.ifEmpty { entryRx.findAll(xml).map { it.groupValues[1] }.toList() }
        val atom = rss.isEmpty()
        return bloecke.mapNotNull { b ->
            // Ohne vorangestelltes Datum wie bei der SNB, das steht ohnehin daneben
            val titel = feld(b, "title")?.let { klartext(it).replace(Regex("^\\d{4}-\\d{2}-\\d{2}\\s*[-–]\\s*"), "") }?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val link = if (atom) {
                Regex("<link[^>]*href=[\"']([^\"']+)[\"'][^>]*>", RegexOption.IGNORE_CASE).find(b)?.groupValues?.get(1).orEmpty()
            } else feld(b, "link")?.let { cdata(it) }.orEmpty()
            val sauber = klartext(link).substringBefore("?.tsrc").substringBefore("?utm_").replace(Regex("(?<!:)//"), "/")
            val zeit = zeit(feld(b, if (atom) "updated" else "pubDate") ?: feld(b, "published") ?: feld(b, "dc:date"))
            val text = (feld(b, "description") ?: feld(b, "summary") ?: feld(b, "content"))?.let { klartext(it) }.orEmpty()
            Eintrag(
                titel = titel,
                link = sauber,
                zeit = zeit,
                // Behoerdenfeeds wiederholen oft den Titel als Beschreibung
                zusammenfassung = text.takeIf { it != titel }.orEmpty().take(400),
                herausgeber = if (herausgeberAusLink) domain(sauber) else quelle,
                quelle = quelle,
                kategorien = setOf(kategorie),
                quellen = if (schluessel.isBlank()) emptySet() else setOf(schluessel),
            )
        }
    }

    // Zusammenfuehren

    // Gleiche Schlagzeile: gleicher Link oder gleicher Titel (Gross- und Kleinschreibung egal).
    private fun schluessel(e: Eintrag): String = e.link.ifBlank { e.titel.lowercase() }

    // Schlagzeilen aller Feeds zu einer Liste, neueste zuerst. Doppelte (gleicher Link oder gleicher Titel) werden
    // eins und tragen alle Kategorien und Feeds. Eintraege ohne Zeit stehen am Ende; aeltere als tage Tage fallen weg.
    fun zusammen(listen: List<List<Eintrag>>, now: Long, tage: Int = 7): List<Eintrag> {
        val grenze = now - tage * 86_400_000L
        val grenzeLang = now - 90 * 86_400_000L
        val nachLink = LinkedHashMap<String, Eintrag>()
        val nachTitel = HashMap<String, String>()
        for (liste in listen) for (e in liste) {
            if (e.zeit in 1 until (if (Kategorie.BRIEFE in e.kategorien || Kategorie.BLOGS in e.kategorien || Kategorie.TRUMP in e.kategorien) grenzeLang else grenze)) continue
            // Bis eine Stunde in der Zukunft (falsche Zeitzone der Quelle) gilt als jetzt; weiter vorn liegen dauerhafte
            // Eintraege mit Fantasiedatum (SNB: „Web-TV“), die gehoeren nicht in den Tagesstrom
            if (e.zeit > now + 3_600_000L) continue
            val f = if (e.zeit > now + 300_000L) e.copy(zeit = now) else e
            val k = nachTitel[f.titel.lowercase()] ?: schluessel(f)
            val alt = nachLink[k]
            nachLink[k] = if (alt == null) f else alt.copy(
                kategorien = alt.kategorien + f.kategorien,
                quellen = alt.quellen + f.quellen,
                zusammenfassung = alt.zusammenfassung.ifBlank { f.zusammenfassung },
                zeit = maxOf(alt.zeit, f.zeit),
            )
            nachTitel[f.titel.lowercase()] = k
        }
        return nachLink.values.sortedWith(compareByDescending<Eintrag> { it.zeit > 0 }.thenByDescending { it.zeit })
    }

    // Eintraege eines Anbieters aus den Feeds quellen; mit unter nur die einer Unterkategorie. ALLE zeigt alles,
    // GEMERKT gibt es hier nicht (die Liste fuehrt AppState).
    fun filter(liste: List<Eintrag>, a: Anbieter, quellen: List<Quelle>, unter: String? = null): List<Eintrag> {
        // Unter „Alle“: keine Briefe und Blogs, von Trump nur Beitraege mit Bedeutung fuer Wirtschaft, Maerkte und Weltlage
        if (a == Anbieter.ALLE) return liste.filter {
            Kategorie.BRIEFE !in it.kategorien && Kategorie.BLOGS !in it.kategorien && (Kategorie.TRUMP !in it.kategorien || it.themen.isNotEmpty())
        }
        val schluessel = quellen.filter { it.anbieter == a && (unter == null || it.unter == unter) }.mapTo(HashSet()) { it.schluessel }
        return liste.filter { e -> e.quellen.any { it in schluessel } }
    }

    // Unterkategorien eines Anbieters in der Reihenfolge der Feeds, je eine Quelle als Beispiel (fuer die Beschriftung).
    fun unterkategorien(a: Anbieter, quellen: List<Quelle>): List<Quelle> =
        quellen.filter { it.anbieter == a }.distinctBy { it.unter }

    // Wichtige Schlagzeilen

    // Kennungen der wichtigen Schlagzeilen, siehe Wichtig. depot: Namen und Kuerzel aus Depot und Merkliste.
    fun wichtige(liste: List<Eintrag>, depot: List<String> = emptyList()): Set<String> = Wichtig.wichtige(liste, depot)

    // Schlagzeilen fuer Mitteilungen: erschienen nach bis, hoechstens zwoelf Stunden alt und nicht mehr als fuenf
    // Minuten in der Zukunft; mit wichtige nur diese. Aelteste zuerst.
    fun zuMelden(liste: List<Eintrag>, bis: Long, now: Long, wichtige: Set<String>? = null): List<Eintrag> {
        val ab = maxOf(bis, now - 12 * 3_600_000L)
        return liste.filter { it.zeit > ab && it.zeit <= now + 5 * 60_000L && (wichtige == null || it.id in wichtige) }.sortedBy { it.zeit }
    }

    // Domain des Herausgebers fuer das Logo: der Herausgeber, wenn er eine Domain ist, sonst der Rechner des Links.
    fun domain(e: Eintrag): String {
        val h = e.herausgeber.trim().lowercase()
        if (h.contains('.') && !h.contains(' ')) return h.removePrefix("www.")
        return runCatching { java.net.URI(e.link).host?.removePrefix("www.") }.getOrNull().orEmpty()
    }

    // Eintraege mit einem Suchbegriff in Titel, Zusammenfassung oder Quelle.
    fun suche(liste: List<Eintrag>, begriff: String): List<Eintrag> {
        val q = begriff.trim()
        if (q.isEmpty()) return liste
        return liste.filter { e -> e.titel.contains(q, true) || e.zusammenfassung.contains(q, true) || e.quellenText.contains(q, true) }
    }

    // Gemerkt

    // Hoechstens so viele gemerkte Schlagzeilen; die aeltesten fallen heraus.
    const val MAX_GEMERKT = 200

    private fun ohneTrenner(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    // Gemerkte Schlagzeilen als Text: je Zeile Zeit, Link, Titel, Quelle, durch Tabulatoren getrennt.
    fun gemerktText(liste: List<Eintrag>): String =
        liste.take(MAX_GEMERKT).joinToString("\n") { e -> listOf(e.zeit.toString(), e.link, e.titel, e.quellenText, e.gemerktAm.toString()).joinToString("\t") { ohneTrenner(it) } }

    // Gemerkte Schlagzeilen aus dem Text von gemerktText; unlesbare Zeilen fallen weg.
    fun gemerktLesen(text: String): List<Eintrag> = text.lines().mapNotNull { z ->
        val t = z.split('\t')
        if (t.size < 4 || t[2].isBlank()) return@mapNotNull null
        Eintrag(titel = t[2], link = t[1], zeit = t[0].toLongOrNull() ?: 0L, quelle = t[3], kategorien = setOf(Kategorie.GEMERKT),
            gemerktAm = t.getOrNull(4)?.toLongOrNull() ?: 0L)
    }

    // Gemerkte Guardian-Artikel 24 Stunden nach dem Merken entfernen (Bedingungen der Content API).
    fun gemerktGueltig(liste: List<Eintrag>, jetzt: Long): List<Eintrag> =
        liste.filter { e -> !NachrichtenApis.istGuardian(e.link) || (e.gemerktAm > 0 && jetzt - e.gemerktAm < 24 * 3_600_000L) }.distinctBy { it.id }

    // Schlagzeile merken oder, wenn schon gemerkt, wieder entfernen; neueste Merkung zuerst.
    fun umschalten(gemerkt: List<Eintrag>, e: Eintrag): List<Eintrag> =
        if (gemerkt.any { it.id == e.id }) gemerkt.filter { it.id != e.id }
        else (listOf(e.copy(herausgeber = "", quelle = e.quellenText, kategorien = setOf(Kategorie.GEMERKT), quellen = emptySet(), gemerktAm = System.currentTimeMillis())) + gemerkt).take(MAX_GEMERKT)

    // Eingetragene Adresse sieht nach einem Feed aus (http oder https).
    fun adresseGueltig(url: String): Boolean = Regex("^https?://[^\\s/]+\\.[^\\s]+$", RegexOption.IGNORE_CASE).matches(url.trim())

    private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")
}
