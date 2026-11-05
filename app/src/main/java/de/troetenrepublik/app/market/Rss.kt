package de.troetenrepublik.app.market

import java.nio.charset.Charset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

// Nachrichtenfeeds: Kategorien, fest eingebaute Quellen, ein kleiner Leser fuer RSS 2.0 und Atom und das Zusammenfuehren
// der Schlagzeilen. Die App liest Feeds fuer ihren Nutzer, mit Quelle und Link zum Artikel. Yahoo Finance: Feed nur
// unveraendert, mit Quelle und Link zum Artikel, ohne Werbung. CNBC: persoenlicher, nicht kommerzieller Gebrauch.
// MarketWatch: Artikel als Originalseite in der Web-Ansicht (Android) oder im Fenster des eingebetteten Browsers am
// Rechner (WebBaustein), Text nur auf Tipp aus der geladenen Seite.
// tagesschau.de: privater Gebrauch, hoechstens 60 Abrufe je Stunde (deshalb fuenf Minuten Abstand). Gemeinfrei als
// Werke der US-Regierung: Federal Reserve, Bureau of Economic Analysis. Frei mit Quellenangabe: EZB, EU-Kommission,
// Destatis, Schweizerische Nationalbank (nicht kommerziell), Bank of England (persoenlicher, nicht kommerzieller
// Gebrauch). Dazu: BBC News (Einbinden ausdruecklich erwuenscht, Quelle „BBC News“), n-tv (Meldungen unveraendert),
// Sueddeutsche Zeitung (einfache RSS-Nutzung erlaubt, Bilder nicht), Handelsblatt (Lesemodus nur auf Antippen), New
// York Times (RSS fuer den persoenlichen Reader), finanznachrichten.de (Ad-hoc-Meldungen), The Defiant und Decrypt
// (Krypto, Auszuege mit Quelle), BIZ (Reden der Notenbanken, nicht kommerziell mit Quelle), BaFin, EIA (gemeinfrei)
// und Google News (Schlagzeilen mit Link zum Herausgeber). Blogs, deren Feed den ganzen Beitrag enthaelt, liest die
// App aus dem Feed; die Seite wird dafuer nicht abgerufen.
// Deutsche Bundesbank: ihr Server nutzt die Telekom Security TLS ECC Root 2020, die Android erst ab 16 kennt; die App
// bringt genau diese Wurzel nur fuer www.bundesbank.de mit (res/xml/network_security_config.xml), alle anderen Seiten
// pruefen weiter nur gegen die Systemwurzeln.
object Rss {

    // Themen der Feeds; bei tagesschau.de, Yahoo und CNBC sind sie die Unterkategorien eines Anbieters.
    enum class Kategorie { MAERKTE, UNTERNEHMEN, KONJUNKTUR, ZENTRALBANKEN, ROHSTOFFE_DEVISEN, KRYPTO, TRUMP, BRIEFE, BLOGS, EIGENE, GEMERKT }

    // Reiter der Nachrichtenseite, zwischen denen man waagerecht wischt. Notenbanken und Behoerden sind je ein Reiter
    // mit den einzelnen Haeusern als Unterkategorien.
    // WICHTIG (5.94): nur die wichtigen Schlagzeilen aller Quellen, eine je Geschichte.
    // PRESSE (5.94): Zeitungen, Sender und Krypto-Medien, je Feed eine Unterkategorie.
    enum class Anbieter { ALLE, WICHTIG, TRUMP, YAHOO, GOOGLE, TAGESSCHAU, PRESSE, CNBC, MARKETWATCH, GUARDIAN, FINNHUB, ALPHAVANTAGE, NOTENBANKEN, BEHOERDEN, BRIEFE, EIGENE, GEMERKT }

    // Art einer Quelle: RSS oder eine Schnittstelle mit eigenem Schluessel (siehe NachrichtenApis).
    enum class Art(val gruppe: String) {
        RSS(""), GUARDIAN(Abrufkontingent.GUARDIAN), FINNHUB(Abrufkontingent.FINNHUB), ALPHAVANTAGE(Abrufkontingent.ALPHAVANTAGE),
        // Feed des Archivs der Truth-Social-Beitraege.
        TRUTH(""),
        // Uebersichtsseite mit Briefen, gelesen nach Briefquellen.Regel.
        SEITE(""),
    }

    // Ein fest eingebauter oder selbst eingetragener Feed. adresse kann {lang} enthalten (Region und Sprache von Yahoo).
    // nurSprache: Bundesbank-Eintraege kommen gemischt; es zaehlen nur Links unter /de/ bzw. /en/press/press-releases/
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
        // Der Feed enthaelt den ganzen Beitrag (content:encoded bzw. Atom content); die App zeigt ihn ohne Seitenabruf.
        val volltext: Boolean = false,
        // Der Feed liefert nur Titel: der Anriss kommt aus den ersten Saetzen der Seite (EZB, SNB, Fed).
        val anrissVonSeite: Boolean = false,
        // Google News: Herausgeber aus <source>, Titel ohne angehaengten Herausgeber, kein Anriss.
        val google: Boolean = false,
        // Abruf mit MarketApi.BROWSER_UA, sonst mit der Kennung der App.
        val browserKennung: Boolean = false,
    ) {
        // Unterkategorie im Reiter des Anbieters: das Thema, bei Haeusern, Presse und eigenen Feeds der Feed selbst.
        val unter: String
            get() = if (anbieter == Anbieter.NOTENBANKEN || anbieter == Anbieter.BEHOERDEN || anbieter == Anbieter.EIGENE || anbieter == Anbieter.PRESSE) schluessel else kategorie.name
    }

    // Yahoo-Schlagzeilen zu mehreren Kuerzeln; symbole mit Komma getrennt.
    fun yahoo(symbole: List<String>, deutsch: Boolean): String =
        "https://feeds.finance.yahoo.com/rss/2.0/headline?s=" + symbole.joinToString(",") { enc(it) } +
            if (deutsch) "&region=DE&lang=de-DE" else "&region=US&lang=en-US"

    // Google News: Wirtschaftsschlagzeilen bzw. Suche nach einem Begriff, in der Sprache der App.
    fun googleNews(deutsch: Boolean): String = "https://news.google.com/rss/headlines/section/topic/BUSINESS?" + googleSprache(deutsch)

    fun googleSuche(begriff: String, deutsch: Boolean): String = "https://news.google.com/rss/search?q=" + enc(begriff) + "&" + googleSprache(deutsch)

    private fun googleSprache(deutsch: Boolean) = if (deutsch) "hl=de&gl=DE&ceid=DE:de" else "hl=en-US&gl=US&ceid=US:en"

    // Fest eingebaute Quellen, nach Anbietern geordnet. Die Yahoo-Feeds buendeln je Thema die wichtigsten Werte.
    fun eingebaut(deutsch: Boolean): List<Quelle> = listOf(
        Quelle("tagesschau-boerse", "tagesschau.de", "https://www.tagesschau.de/wirtschaft/finanzen/index~rss2.xml", Kategorie.MAERKTE, Anbieter.TAGESSCHAU, abstandMs = 5 * 60_000L),
        Quelle("tagesschau-wirtschaft", "tagesschau.de", "https://www.tagesschau.de/wirtschaft/index~rss2.xml", Kategorie.KONJUNKTUR, Anbieter.TAGESSCHAU, abstandMs = 5 * 60_000L),
        Quelle("yahoo-maerkte", "Yahoo Finance", yahoo(listOf("^GDAXI", "^STOXX50E", "^GSPC", "^IXIC", "^N225"), deutsch), Kategorie.MAERKTE, Anbieter.YAHOO, sammelt = true),
        Quelle("yahoo-unternehmen", "Yahoo Finance", yahoo(listOf("AAPL", "MSFT", "NVDA", "AMZN", "GOOGL", "META", "TSLA", "SAP.DE", "SIE.DE", "ALV.DE", "ASML.AS", "MC.PA", "NOVO-B.CO"), deutsch), Kategorie.UNTERNEHMEN, Anbieter.YAHOO, sammelt = true),
        Quelle("yahoo-rohstoffe", "Yahoo Finance", yahoo(listOf("GC=F", "CL=F", "SI=F", "EURUSD=X", "^TNX"), deutsch), Kategorie.ROHSTOFFE_DEVISEN, Anbieter.YAHOO, sammelt = true),
        Quelle("yahoo-krypto", "Yahoo Finance", yahoo(listOf("BTC-USD", "ETH-USD", "SOL-USD", "XRP-USD"), deutsch), Kategorie.KRYPTO, Anbieter.YAHOO, sammelt = true),
        Quelle("google-wirtschaft", "Google News", googleNews(deutsch), Kategorie.MAERKTE, Anbieter.GOOGLE, abstandMs = 10 * 60_000L, google = true),
        Quelle("bbc", "BBC News", "https://feeds.bbci.co.uk/news/business/rss.xml", Kategorie.KONJUNKTUR, Anbieter.PRESSE, kurz = "BBC"),
        Quelle("ntv", "n-tv", "https://www.n-tv.de/wirtschaft/rss", Kategorie.MAERKTE, Anbieter.PRESSE),
        Quelle("handelsblatt", "Handelsblatt", "https://www.handelsblatt.com/contentexport/feed/finanzen", Kategorie.MAERKTE, Anbieter.PRESSE),
        Quelle("sz", "Süddeutsche Zeitung", "https://rss.sueddeutsche.de/rss/Wirtschaft", Kategorie.KONJUNKTUR, Anbieter.PRESSE, kurz = "SZ"),
        Quelle("nyt", "The New York Times", "https://rss.nytimes.com/services/xml/rss/nyt/Business.xml", Kategorie.UNTERNEHMEN, Anbieter.PRESSE, kurz = "NYT"),
        Quelle("fn-adhoc", "finanznachrichten.de", "https://www.finanznachrichten.de/rss-aktien-adhoc", Kategorie.UNTERNEHMEN, Anbieter.PRESSE, kurz = "Ad-hoc"),
        Quelle("defiant", "The Defiant", "https://thedefiant.io/api/feed", Kategorie.KRYPTO, Anbieter.PRESSE),
        Quelle("decrypt", "Decrypt", "https://decrypt.co/feed", Kategorie.KRYPTO, Anbieter.PRESSE),
        // derStandard.at: nur Schlagzeile, Anriss und Link, hoechstens ein Drittel jeder Ansicht, keine Logos.
        // DER AKTIONAER: Lesemodus nur auf Antippen. Nasdaq: ohne Lesemodus.
        Quelle("derstandard", "derStandard.at", "https://www.derstandard.at/rss/wirtschaft", Kategorie.KONJUNKTUR, Anbieter.PRESSE),
        Quelle("aktionaer", "DER AKTIONÄR", "https://www.deraktionaer.de/aktionaer-news.rss", Kategorie.UNTERNEHMEN, Anbieter.PRESSE, abstandMs = 30 * 60_000L),
        Quelle("nasdaq-maerkte", "Nasdaq", "https://www.nasdaq.com/feed/rssoutbound?category=Markets", Kategorie.MAERKTE, Anbieter.PRESSE, abstandMs = 10 * 60_000L, browserKennung = true),
        Quelle("cnbc-maerkte", "CNBC", "https://www.cnbc.com/id/15839069/device/rss/rss.html", Kategorie.MAERKTE, Anbieter.CNBC),
        Quelle("cnbc-top", "CNBC", "https://www.cnbc.com/id/100003114/device/rss/rss.html", Kategorie.UNTERNEHMEN, Anbieter.CNBC),
        Quelle("cnbc-wirtschaft", "CNBC", "https://www.cnbc.com/id/20910258/device/rss/rss.html", Kategorie.KONJUNKTUR, Anbieter.CNBC),
        // Beitraege von Donald Trump ueber das Archiv „Trump's Truth“
        Quelle("truth", "Truth Social", TruthArchiv.adresse(100), Kategorie.TRUMP, Anbieter.TRUMP, abstandMs = 15 * 60_000L, art = Art.TRUTH),
        Quelle("marketwatch", "MarketWatch", "https://feeds.content.dowjones.io/public/rss/mw_topstories", Kategorie.MAERKTE, Anbieter.MARKETWATCH, nurMitWebAnsicht = true),
        Quelle("ezb", if (deutsch) "EZB" else "ECB", "https://www.ecb.europa.eu/rss/press.html", Kategorie.ZENTRALBANKEN, Anbieter.NOTENBANKEN, anrissVonSeite = true),
        Quelle("fed", "Federal Reserve", "https://www.federalreserve.gov/feeds/press_all.xml", Kategorie.ZENTRALBANKEN, Anbieter.NOTENBANKEN, kurz = "Fed", anrissVonSeite = true),
        Quelle("boe", "Bank of England", "https://www.bankofengland.co.uk/rss/news", Kategorie.ZENTRALBANKEN, Anbieter.NOTENBANKEN),
        // Pressemitteilungen der Bundesbank: deutsch aus dem Feed 633286 (deutsche und englische Eintraege gemischt),
        // englisch aus „Latest“ 633306 (einen englischen Pressefeed gibt es nicht); gefiltert in NewsRepository
        Quelle("bbk", "Deutsche Bundesbank", "https://www.bundesbank.de/service/rss/" + (if (deutsch) "de/633286" else "en/633306") + "/feed.rss", Kategorie.ZENTRALBANKEN, Anbieter.NOTENBANKEN, kurz = "Bundesbank", nurSprache = true),
        Quelle("snb", if (deutsch) "Schweizerische Nationalbank" else "Swiss National Bank", "https://www.snb.ch/public/rss/" + (if (deutsch) "de" else "en") + "/news", Kategorie.ZENTRALBANKEN, Anbieter.NOTENBANKEN, kurz = "SNB", anrissVonSeite = true),
        Quelle("bis", if (deutsch) "BIZ (Reden der Notenbanken)" else "BIS (central bankers' speeches)", "https://www.bis.org/doclist/cbspeeches.rss", Kategorie.ZENTRALBANKEN, Anbieter.NOTENBANKEN,
            kurz = if (deutsch) "BIZ" else "BIS", abstandMs = 15 * 60_000L),
        Quelle("destatis", "Destatis", "https://www.destatis.de/SiteGlobals/Functions/RSSFeed/DE/RSSNewsfeed/Aktuell.xml", Kategorie.KONJUNKTUR, Anbieter.BEHOERDEN),
        Quelle("eu-kommission", if (deutsch) "EU-Kommission" else "European Commission", "https://ec.europa.eu/commission/presscorner/api/rss?language=" + if (deutsch) "de" else "en", Kategorie.KONJUNKTUR, Anbieter.BEHOERDEN),
        Quelle("bea", "US Bureau of Economic Analysis", "https://apps.bea.gov/rss/rss.xml", Kategorie.KONJUNKTUR, Anbieter.BEHOERDEN, kurz = "BEA", abstandMs = 15 * 60_000L),
        Quelle("bafin", "BaFin", "https://www.bafin.de/DE/service/rss/_function/RSS_Presse.xml?nn=150166", Kategorie.KONJUNKTUR, Anbieter.BEHOERDEN, abstandMs = 15 * 60_000L),
        Quelle("bafin-massnahmen", if (deutsch) "BaFin Maßnahmen" else "BaFin measures", "https://www.bafin.de/DE/service/rss/_function/RSS_Massnahmen.xml?nn=150166", Kategorie.UNTERNEHMEN,
            Anbieter.BEHOERDEN, abstandMs = 30 * 60_000L),
        Quelle("eia", "US Energy Information Administration", "https://www.eia.gov/rss/todayinenergy.xml", Kategorie.ROHSTOFFE_DEVISEN, Anbieter.BEHOERDEN, kurz = "EIA", abstandMs = 30 * 60_000L),
    ) + briefeUndBlogs()

    // Briefe von Investoren und Unternehmen und Blogs zu Wirtschaft und Geldanlage, englisch und deutsch. Briefe
    // kommen von Uebersichtsseiten (Briefquellen), Blogs als Feed; beides wird hoechstens einmal je Stunde abgefragt.
    private fun briefeUndBlogs(): List<Quelle> {
        fun brief(s: String, name: String, url: String, regel: Briefquellen.Regel) =
            Quelle("brief-$s", name, url, Kategorie.BRIEFE, Anbieter.BRIEFE, abstandMs = 6 * 3_600_000L, art = Art.SEITE, regel = regel)
        fun blog(s: String, name: String, url: String, volltext: Boolean = false) = Quelle("blog-$s", name, url, Kategorie.BLOGS, Anbieter.BRIEFE, abstandMs = 60 * 60_000L, volltext = volltext)
        val aktionaere = "Brief an die Aktionäre {jahr}" to "Letter to shareholders {jahr}"
        return listOf(
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
            blog("bu", "Bank Underground (Bank of England)", "https://bankunderground.co.uk/feed/", volltext = true),
            blog("feds", "FEDS Notes (Federal Reserve)", "https://www.federalreserve.gov/feeds/feds_notes.xml"),
            blog("damodaran", "Musings on Markets (Aswath Damodaran)", "https://aswathdamodaran.blogspot.com/feeds/posts/default?max-results=10", volltext = true),
            blog("awocs", "A Wealth of Common Sense", "https://awealthofcommonsense.com/feed/"),
            blog("odad", "Of Dollars And Data", "https://ofdollarsanddata.com/feed/", volltext = true),
            blog("klement", "Klement on Investing", "https://klementoninvesting.substack.com/feed", volltext = true),
            blog("ifo", "ifo Institut", "https://www.ifo.de/pressemitteilungen/rss.xml"),
            blog("iw", "IW Köln", "https://www.iwkoeln.de/rss/iw-kurzberichte-deutschsprachig.xml"),
            blog("diw", "DIW Berlin", "https://www.diw.de/de/rss_publications.xml"),
            blog("makronom", "Makronom", "https://makronom.de/feed"),
            blog("kommer", "Gerd Kommer", "https://gerd-kommer.de/feed/", volltext = true),
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

    // Seiten, deren Bedingungen das Lesen des Artikeltexts ausdruecklich erlauben (frei mit Quelle oder gemeinfrei): der
    // Text steht sofort da. Bei allen anderen laedt zuerst die Originalseite wie in einem Browser, „Nur Text anzeigen“
    // wechselt dann zum Text (NewsSheet).
    private val TEXT_ERLAUBT = listOf(
        "ecb.europa.eu", "federalreserve.gov", "snb.ch", "bankofengland.co.uk", "destatis.de", "ec.europa.eu", "commission.europa.eu",
        "bea.gov", "bis.org", "eia.gov",
    )

    fun textErlaubt(link: String): Boolean = host(link).let { h -> TEXT_ERLAUBT.any { h == it || h.endsWith(".$it") } }

    // Seiten ohne automatisches Auslesen: zuerst die Originalseite, Auslesen nur auf Antippen.
    private val OHNE_LESEMODUS = listOf("handelsblatt.com", "deraktionaer.de", "marketwatch.com")

    // quelle: Herausgeber bzw. Quellentext; Google-News-Links fuehren erst nach einer Weiterleitung zur Zeitung.
    fun ohneLesemodus(link: String, quelle: String = ""): Boolean = host(link).let { h ->
        OHNE_LESEMODUS.any { h == it || h.endsWith(".$it") } ||
            (h == "news.google.com" && OHNE_LESEMODUS_NAMEN.any { quelle.contains(it, ignoreCase = true) })
    }

    private val OHNE_LESEMODUS_NAMEN = listOf("Handelsblatt", "DER AKTIONÄR", "MarketWatch")

    // Quellen, deren Eintraege in jeder Ansicht hoechstens ein Drittel ausmachen duerfen.
    val DRITTEL = setOf("derstandard")

    // Abruf einer Quelle: nur die neuesten Eintraege einer DRITTEL-Quelle behalten, mindestens einen.
    fun begrenzt(q: Quelle, liste: List<Eintrag>): List<Eintrag> {
        if (q.schluessel !in DRITTEL || liste.isEmpty()) return liste
        return liste.take(maxOf(1, liste.size / 3))
    }

    // STANDARD-Artikel: Schluessel einer DRITTEL-Quelle, Angabe in quellenText, herausgeber oder quelle oder
    // der Rechner des Links.
    fun istStandard(e: Eintrag): Boolean = e.quellen.any { it in DRITTEL } ||
        listOf(e.quellenText, e.herausgeber, e.quelle).any { it.contains("derStandard", true) || it.contains("DER STANDARD", true) } ||
        host(e.link).let { it == "derstandard.at" || it.endsWith(".derstandard.at") }

    // Fertige Anzeigeliste: STANDARD-Artikel hoechstens so viele wie die Haelfte der uebrigen, also ein Drittel
    // der Liste; die neuesten bleiben, die Reihenfolge sonst gleich. Ohne andere Eintraege bleibt kein
    // STANDARD-Artikel.
    fun drittel(liste: List<Eintrag>): List<Eintrag> {
        var uebrig = liste.count { !istStandard(it) } / 2
        return liste.filter { e -> if (istStandard(e)) { if (uebrig > 0) { uebrig--; true } else false } else true }
    }

    private fun google(link: String) = host(link) == "news.google.com"

    // Nur Schlagzeile, Anriss und Link, kein Lesemodus, auch nicht auf Antippen: derStandard.at und Nasdaq.
    private val KEIN_LESEMODUS = listOf("derstandard.at", "derstandard.de", "nasdaq.com")
    private val KEIN_LESEMODUS_NAMEN = listOf("derStandard.at", "derStandard", "DER STANDARD", "Nasdaq")

    fun keinLesemodus(link: String, quelle: String = ""): Boolean = host(link).let { h ->
        KEIN_LESEMODUS.any { h == it || h.endsWith(".$it") } ||
            KEIN_LESEMODUS_NAMEN.any { quelle.contains(it, ignoreCase = true) }
    }

    // Eintraege nur fuer die Nachrichtenliste; Widgets, Mitteilungen und Rueckblick lassen sie weg.
    val NUR_ANSICHT = setOf("derstandard", "nasdaq-maerkte")

    fun nurAnsicht(e: Eintrag): Boolean = e.quellen.any { it in NUR_ANSICHT } || keinLesemodus(e.link, e.quellenText)

    private fun host(link: String): String = runCatching { java.net.URI(link).host.orEmpty().lowercase() }.getOrDefault("")

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
        // Ganzer Beitrag aus dem Feed (Quelle.volltext), nur im Speicher.
        val absaetze: List<String> = emptyList(),
        // Die Zusammenfassung endet mitten im Text (Anriss von der Seite); die Oberflaeche zeigt dort die Blende.
        val zusammenfassungGekuerzt: Boolean = false,
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
        // Manche Feeds (Handelsblatt, BIZ) geben UTF-8 an und liefern Windows-1252: dann ueberwiegen die Ersatzzeichen.
        // Ein abgeschnittenes Ende oder ein verirrtes Byte kippt den Feed nicht, ein zufaellig gueltiges Paar („ß“ vor
        // „““) haelt die Umschaltung nicht auf.
        if (cs == Charsets.UTF_8) {
            val utf = String(bytes, Charsets.UTF_8)
            // Ersatzzeichen, die die Quelle selbst schickt (EF BF BD), sind gueltig
            val echt = (0 until bytes.size - 2).count { bytes[it] == 0xEF.toByte() && bytes[it + 1] == 0xBF.toByte() && bytes[it + 2] == 0xBD.toByte() }
            val kaputt = utf.count { it == ERSATZ } - echt
            val gut = utf.count { it.code >= 0x80 && it != ERSATZ } + echt
            return if (kaputt > gut) String(bytes, Charset.forName("windows-1252")) else utf
        }
        return String(bytes, cs)
    }

    private const val ERSATZ = '\uFFFD'

    // Absaetze eines ganzen Beitrags aus dem Feed (HTML in CDATA oder als Entitaeten). Ohne <p> (Blogger) trennen
    // Zeilenumbrueche und Bloecke.
    fun feedAbsaetze(roh: String): List<String> {
        var html = cdata(roh)
        if (!html.contains('<') && html.contains("&lt;")) {
            html = html.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
        }
        html = html.replace(Regex("<(script|style|figure|iframe)\\b[^>]*>.*?</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
        // Die Absatz-Tags tragen den Text nur, wenn sie den groessten Teil abdecken (sonst Fliesstext in div/br)
        val p = Regex("""<(p|li|h[2-4])\b[^>]*>(.*?)</\1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(html)
            .map { klartext(Sonderleser.entitaeten(it.groupValues[2].replace(Regex("<[^>]+>"), " "))) }.filter { it.length >= 3 }.toList()
        val gesamt = klartext(Sonderleser.entitaeten(html.replace(Regex("<[^>]+>"), " "))).length
        val zeilen = if (p.sumOf { it.length } >= 200 && p.sumOf { it.length } >= gesamt * 0.6) p
        else html.replace(Regex("(?i)<br\\s*/?>|</(div|p|li|h[1-6])>"), "\n").split('\n')
            .map { klartext(Sonderleser.entitaeten(it.replace(Regex("<[^>]+>"), " "))) }.filter { it.length >= 3 }
        // Sehr lange Beitraege liest der Leser von der Seite, statt sie still abzuschneiden
        return if (zeilen.size > MAX_ABSAETZE) emptyList() else zeilen
    }

    private const val MAX_ABSAETZE = 400

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
        // Geschuetzte Leerzeichen (auch aus &#160; und &nbsp;) werden normale Leerzeichen
        t = t.replace('\u00A0', ' ')
        return t.replace(Regex("\\s+"), " ").trim()
    }

    // Zeitangabe aus RSS (RFC 1123, auch mit einstelliger Tageszahl oder „GMT“) oder Atom (ISO 8601); 0 = unbekannt.
    fun zeit(s: String?): Long {
        var t = s?.let { klartext(it) }?.takeIf { it.isNotBlank() } ?: return 0L
        // US-Zeitzonen als Kuerzel (BEA: „08:30:00 EDT“) kennt RFC 1123 nicht
        t = t.replace(Regex(" (EDT|EST|CDT|CST|PDT|PST|UT|UTC|Z)$")) { m ->
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
    // Relative Links (etwa beim DIW „/de/diw_01...html“) gegen die Adresse des Feeds aufloesen (5.94); ohne
    // Adresse oder bei kaputtem Link bleibt er, wie er ist.
    fun absolut(link: String, basis: String): String {
        if (link.isBlank() || basis.isBlank() || Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(link)) return link
        return runCatching { java.net.URI(basis).resolve(link.replace(" ", "%20")).toString() }.getOrDefault(link)
    }

    // Link eines Atom-Eintrags (5.96): rel="alternate" oder ohne rel, sonst der erste mit Adresse. Blogger stellt
    // Kommentar- und Bearbeitungslinks vor den Link zum Beitrag.
    private val atomLinkRx = Regex("<link(?=[\\s/>])[^>]*>", RegexOption.IGNORE_CASE)
    private val hrefRx = Regex("href=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    private val relRx = Regex("rel=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)

    private fun atomLink(b: String): String {
        val links = atomLinkRx.findAll(b).mapNotNull { m ->
            val href = hrefRx.find(m.value)?.groupValues?.get(1) ?: return@mapNotNull null
            relRx.find(m.value)?.groupValues?.get(1)?.lowercase() to href
        }.toList()
        return (links.firstOrNull { it.first == null || it.first == "alternate" } ?: links.firstOrNull())?.second.orEmpty()
    }

    // basis (5.94): Adresse des Feeds fuer relative Links.
    // google: Herausgeber aus <source>, ohne Anriss; volltext: ganzer Beitrag aus content:encoded bzw. Atom content.
    fun lesen(
        xml: String, quelle: String, kategorie: Kategorie, herausgeberAusLink: Boolean = false, schluessel: String = "", basis: String = "",
        google: Boolean = false, volltext: Boolean = false,
    ): List<Eintrag> {
        val rss = itemRx.findAll(xml).map { it.groupValues[1] }.toList()
        val bloecke = rss.ifEmpty { entryRx.findAll(xml).map { it.groupValues[1] }.toList() }
        val atom = rss.isEmpty()
        return bloecke.mapNotNull { b ->
            // Ohne vorangestelltes Datum wie bei der SNB, das steht ohnehin daneben
            val titel0 = feld(b, "title")?.let { klartext(it).replace(Regex("^\\d{4}-\\d{2}-\\d{2}\\s*[-–]\\s*"), "") }?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            // Google haengt den Herausgeber an den Titel („… - WELT“)
            val googleQuelle = if (google) feld(b, "source")?.let { klartext(it) }.orEmpty() else ""
            val titel = if (googleQuelle.isNotBlank()) titel0.removeSuffix(" - $googleQuelle").trim() else titel0
            val link = if (atom) atomLink(b) else feld(b, "link")?.let { cdata(it) }.orEmpty()
            val sauber = absolut(klartext(link).trim(), basis).substringBefore("?.tsrc").substringBefore("?utm_").replace(Regex("(?<!:)//"), "/")
            val zeit = zeit(feld(b, if (atom) "updated" else "pubDate") ?: feld(b, "published") ?: feld(b, "dc:date"))
            val text = if (google) "" else (feld(b, "description") ?: feld(b, "summary") ?: feld(b, "content"))?.let { klartext(it) }.orEmpty()
            val voll = if (!volltext) emptyList() else (feld(b, "content:encoded") ?: if (atom) feld(b, "content") else null)?.let { feedAbsaetze(it) }.orEmpty()
            Eintrag(
                titel = titel,
                link = sauber,
                zeit = zeit,
                // Behoerdenfeeds wiederholen oft den Titel als Beschreibung
                zusammenfassung = text.takeIf { it != titel }.orEmpty().take(400),
                herausgeber = if (googleQuelle.isNotBlank()) googleQuelle else if (herausgeberAusLink) domain(sauber) else quelle,
                absaetze = voll,
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
                // Der direkte Link geht vor der Google-Weiterleitung
                link = if (google(alt.link) && !google(f.link)) f.link else alt.link,
                kategorien = alt.kategorien + f.kategorien,
                quellen = alt.quellen + f.quellen,
                zusammenfassung = alt.zusammenfassung.ifBlank { f.zusammenfassung },
                zusammenfassungGekuerzt = if (alt.zusammenfassung.isNotBlank()) alt.zusammenfassungGekuerzt else f.zusammenfassungGekuerzt,
                absaetze = alt.absaetze.ifEmpty { f.absaetze },
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
    // Quellen mit Drittel-Begrenzung bilden keine eigene Unterkategorie.
    fun unterkategorien(a: Anbieter, quellen: List<Quelle>): List<Quelle> =
        quellen.filter { it.anbieter == a && it.schluessel !in DRITTEL }.distinctBy { it.unter }

    // Wichtige Schlagzeilen

    // Kennungen der wichtigen Schlagzeilen, siehe Wichtig. depot: Namen und Kuerzel aus Depot und Merkliste.
    fun wichtige(liste: List<Eintrag>, depot: List<String> = emptyList(), nurEnglisch: Boolean = false): Set<String> = Wichtig.wichtige(liste, depot, nurEnglisch)

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

    // Gemerkte Guardian-Artikel 24 Stunden nach dem Merken entfernen (Bedingungen der Content API). Gemerkte
    // Eintraege ohne Lesemodus (Host und Quellentext) fallen weg.
    fun gemerktGueltig(liste: List<Eintrag>, jetzt: Long): List<Eintrag> =
        liste.filter { e -> !keinLesemodus(e.link, e.quellenText) && (!NachrichtenApis.istGuardian(e.link) || (e.gemerktAm > 0 && jetzt - e.gemerktAm < 24 * 3_600_000L)) }.distinctBy { it.id }

    // Schlagzeile merken oder, wenn schon gemerkt, wieder entfernen; neueste Merkung zuerst.
    fun umschalten(gemerkt: List<Eintrag>, e: Eintrag): List<Eintrag> =
        if (gemerkt.any { it.id == e.id }) gemerkt.filter { it.id != e.id }
        else (listOf(e.copy(herausgeber = "", quelle = e.quellenText, kategorien = setOf(Kategorie.GEMERKT), quellen = emptySet(), gemerktAm = System.currentTimeMillis())) + gemerkt).take(MAX_GEMERKT)

    // Eingetragene Adresse sieht nach einem Feed aus (http oder https).
    fun adresseGueltig(url: String): Boolean = Regex("^https?://[^\\s/]+\\.[^\\s]+$", RegexOption.IGNORE_CASE).matches(url.trim())

    private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")
}
