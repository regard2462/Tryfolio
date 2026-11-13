package de.troetenrepublik.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

// Selbstreparatur der Datenquellen (seit 5.92), Rechenteil: Aendert eine Bezugsquelle Adresse, Kopfzeilen oder
// Feldnamen, kann ein signiertes Buendel (quellen.json + quellen.json.sig) die eingebauten Werte ohne neue Fassung
// ersetzen. Angenommen wird es nur mit gueltiger Ed25519-Signatur, mit Hosts aus der festen Liste je Quelle und mit
// hoeherer version. Fehlende Schluessel nehmen den eingebauten Wert. Dazu die Gesundheitszaehler je Quelle.
object Quellenbuendel {

    // Quelle mit den Host-Endungen, die ein Buendel fuer sie nennen darf. Die Liste steht fest im Code; ein Buendel
    // kann keine neuen Hosts und keine neuen Quellen hinzufuegen. Eigene Schluessel-Dienste wie DeepL stehen nicht
    // darin und laufen unveraendert.
    // Zusatz fuer die Seite Datenquellen (zentral hier gepflegt): Lizenz in Kurzform, Links zu den Bedingungen und die
    // Quellen, auf die die App bei Ausfall zurueckgreift (Namen, ersatzSchluessel: nur mit eigenem Schluessel des Nutzers).
    // Ohne Rueckfall bleibt der zuletzt gespeicherte Stand sichtbar.
    data class Art(
        val id: String,
        val hosts: List<String>,
        val lizenz: Lizenz = Lizenz.ANBIETER,
        val bedingungen: List<String> = emptyList(),
        val ersatz: List<String> = emptyList(),
        val ersatzSchluessel: Boolean = false,
    )

    // Kurzform der Bedingung: ANBIETER heisst, die Nutzung richtet sich nach den Bedingungen des Anbieters, GEMISCHT, dass
    // die Gruppe mehrere Anbieter mit eigenen Bedingungen umfasst.
    enum class Lizenz { GEMEINFREI, QUELLENANGABE, CC_BY_SA_CC0, ANBIETER, GEMISCHT, NUTZER }

    val ARTEN: List<Art> = listOf(
        Art("yahoo", listOf("yahoo.com"), Lizenz.ANBIETER, listOf("https://legal.yahoo.com/us/en/yahoo/terms/otos/index.html"), ersatz = listOf("Massive", "Tiingo", "Twelve Data"), ersatzSchluessel = true),
        Art("sec", listOf("sec.gov"), Lizenz.GEMEINFREI, listOf("https://www.sec.gov/privacy")),
        // Konzernmutter und Toechter (Level 2) aus dem Verzeichnis der Rechtstraeger-Kennungen, CC0
        Art("gleif", listOf("api.gleif.org", "gleif.org"), Lizenz.GEMEINFREI, listOf("https://www.gleif.org/en/meta/lei-data-terms-of-use")),
        Art("ezb", listOf("ecb.europa.eu"), Lizenz.QUELLENANGABE, listOf("https://www.ecb.europa.eu/services/disclaimer/html/index.en.html")),
        // Liste nicht kooperativer Steuergebiete (Ratsschlussfolgerungen), nur als Beleglink in den Standorten
        Art("eurat", listOf("consilium.europa.eu"), Lizenz.QUELLENANGABE, listOf("https://www.consilium.europa.eu/en/about-site/copyright/")),
        Art("wikipedia", listOf("wikipedia.org", "wikidata.org", "wikimedia.org"), Lizenz.CC_BY_SA_CC0, listOf("https://foundation.wikimedia.org/wiki/Policy:Terms_of_Use")),
        // Berichtsdichte der Spannungsgebiete und Ton der Berichte je Firma (GDELT DOC API): frei nutzbar mit Zitat
        Art("gdelt", listOf("gdeltproject.org"), Lizenz.QUELLENANGABE, listOf("https://www.gdeltproject.org/about.html")),
        Art("polymarket", listOf("polymarket.com"), Lizenz.ANBIETER, listOf("https://polymarket.com/tos")),
        Art("kalshi", listOf("kalshi.com"), Lizenz.ANBIETER, listOf("https://kalshi.com/legal/terms")),
        // Hacker News (Algolia) und Mastodon als offene Ersatzsignale seit 5.92, Bluesky mit eigenem App-Passwort (Anmeldung
        // bei bsky.social, Abrufe ueber den Server des Kontos)
        Art("erwaehnungen", listOf("reddit.com", "apewisdom.io", "tradestie.com", "hn.algolia.com", "mastodon.social", "bsky.social", "bsky.network", "bsky.app"), Lizenz.GEMISCHT, listOf("https://www.redditinc.com/policies/data-api-terms"), ersatz = listOf("Hacker News", "Mastodon")),
        // Dazu die Mitgliederliste von unitedstates/congress-legislators (5.96)
        Art("kongress", listOf("house.gov", "senate.gov", "congress.gov", "unitedstates.github.io"), Lizenz.GEMEINFREI, listOf("https://www.usa.gov/government-works", "https://github.com/unitedstates/congress-legislators"), ersatz = listOf("GitHub")),
        Art("usbehoerden", listOf("usaspending.gov", "fec.gov", "uspto.gov", "oge.gov", "whitehouse.gov", "fda.gov", "finra.org", "texas.gov", "courtlistener.com"), Lizenz.GEMISCHT, listOf("https://www.usa.gov/government-works")),
        // Ersatzquellen 5.92: Lieferketten-Index der New York Fed, Shiller-Daten (Seite und Speicherdienst der Datei)
        Art("uswirtschaft", listOf("treasury.gov", "bls.gov", "bea.gov", "federalreserve.gov", "stlouisfed.org", "cftc.gov", "newyorkfed.org", "shillerdata.com", "img1.wsimg.com"), Lizenz.GEMISCHT, listOf("https://www.usa.gov/government-works", "https://fred.stlouisfed.org/docs/api/terms_of_use.html")),
        Art("bafin", listOf("bafin.de"), Lizenz.ANBIETER, listOf("https://www.bafin.de/DE/service/impressum/impressum_node.html")),
        // Kalender der Nasdaq seit 5.95; die laengste passende Host-Endung gewinnt, darum landen die Kalender nicht
        // bei den europaeischen Registern unter nasdaq.com
        Art("nasdaq", listOf("api.nasdaq.com"), Lizenz.ANBIETER, listOf("https://www.nasdaq.com/legal")),
        Art("europa", listOf(
            "amf-france.org", "afm.nl", "fca.org.uk", "fi.se", "1info.it", "cnb.cz", "mnb.hu", "luxse.com", "cse.com.cy",
            "nasdaq.com", "data.gouv.fr", "consob.it", "investegate.co.uk",
        ), Lizenz.GEMISCHT, listOf("https://www.afm.nl/en/over-de-afm/disclaimer", "https://www.etalab.gouv.fr/licence-ouverte-open-licence/")),
        Art("welt", listOf("hkex.com.hk", "cse.lk", "mcd.om", "nseindia.com", "bseindia.com", "sedi.ca", "ser-ag.com", "boursakuwait.com.kw"), Lizenz.GEMISCHT, listOf("https://www.hkex.com.hk/Global/Exchange/Disclaimer?sc_lang=en", "https://www.ser-ag.com/en/legal.html")),
        // Put/Call-Verhaeltnis der Cboe seit 5.92
        Art("stimmung", listOf("alternative.me", "cnn.io", "cnn.com", "aaii.com", "cboe.com"), Lizenz.GEMISCHT, listOf("https://www.cboe.com/terms/", "https://www.cnn.com/terms", "https://www.aaii.com/terms", "https://alternative.me/crypto/api/")),
        Art("truth", listOf("trumpstruth.org"), Lizenz.ANBIETER, listOf("https://trumpstruth.org/")),
        Art("nachrichten", listOf("guardianapis.com", "finnhub.io", "alphavantage.co", "eodhd.com"), Lizenz.NUTZER, listOf("https://open-platform.theguardian.com/access/", "https://finnhub.io/terms-of-service")),
        // Zweite Kursquelle mit eigenem Schluessel (frueher polygon.io)
        Art("kursrueckfall", listOf("massive.com", "polygon.io", "tiingo.com", "twelvedata.com"), Lizenz.NUTZER, listOf("https://massive.com/legal/terms", "https://twelvedata.com/terms")),
        // Was Investoren schreiben (5.92): Briefe von den Seiten der Absender (engine/Investorenbriefe.kt)
        Art("investorenbriefe", listOf(
            "tweedyfunds.com", "southeasternasset.com", "weitzinvestments.com", "brownadvisory.com", "montaka.com",
            "generationim.com", "polencapital.com", "oaktreecapital.com", "fairfax.ca", "himcap.com",
        ), Lizenz.ANBIETER),
        // Dazu der offene Spiegel der Senatsmeldungen (5.92); jsDelivr als Ausweichweg fuer dieselben Dateien
        Art("github", listOf("api.github.com", "raw.githubusercontent.com", "cdn.jsdelivr.net"), Lizenz.ANBIETER, listOf("https://docs.github.com/en/site-policy/github-terms/github-terms-of-service", "https://www.jsdelivr.com/terms"), ersatz = listOf("jsDelivr")),
        // Fruehe Signale (5.92): EIA-Lager, TSA-Fluggaeste, Drought Monitor, EU-Gasspeicher (GIE AGSI+)
        // Dazu Containerfracht (Drewry), Wochenzahlen der US-Bahnen (AAR) und Halbleiterumsaetze (WSTS) seit 5.92
        // Strom in Europa: Day-Ahead-Preise der ENTSO-E Transparency Platform (mit Token der Person) und SMARD der Bundesnetzagentur
        Art("strom", listOf("web-api.tp.entsoe.eu", "smard.de"), Lizenz.GEMISCHT, listOf("https://www.smard.de/en/datennutzung", "https://www.entsoe.eu/data/transparency-platform/")),
        // Rechtsstreit und Wirtschaftspruefer: Form-AP-Datei des PCAOB (Nutzung mit Quellenangabe) und Umweltstrafen aus EPA ECHO
        Art("pcaob", listOf("assets.pcaobus.org", "pcaobus.org"), Lizenz.QUELLENANGABE, listOf("https://pcaobus.org/privacypolicy")),
        Art("epa", listOf("echodata.epa.gov", "echo.epa.gov"), Lizenz.GEMEINFREI, listOf("https://www.epa.gov/web-policies-and-procedures/epa-disclaimers")),
        // Direkte Emissionen je Firma: gepruefte Emissionen des EU-Emissionshandels aus dem Unionsregister der EU-Kommission
        // (Wiederverwendung nach Beschluss 2011/833/EU, CC BY 4.0), mitgeliefert als firmen/emissionen-eu.json
        Art("emissionshandel", listOf("climate.ec.europa.eu"), Lizenz.QUELLENANGABE, listOf("https://commission.europa.eu/legal-notice_en", "https://eur-lex.europa.eu/eli/dec/2011/833/oj")),
        // Staatsgeld: Zuschlaege aus TED (Wiederverwendung nach Beschluss 2011/833/EU), Contracts Finder (Open Government Licence v3.0);
        // der EU-Haushalt (Financial Transparency System, CC BY 4.0) ist mitgeliefert als firmen/staatsgeld-eu.json
        Art("staatsgeld", listOf("api.ted.europa.eu", "ted.europa.eu", "contractsfinder.service.gov.uk"), Lizenz.QUELLENANGABE, listOf("https://ted.europa.eu/en/legal-notice", "https://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/", "https://eur-lex.europa.eu/eli/dec/2011/833/oj", "https://commission.europa.eu/legal-notice_en")),
        // OSHA-Inspektionen und -Strafen ueber das Open Data Portal des Arbeitsministeriums (mit eigenem Schluessel); US-Regierungsdaten
        Art("osha", listOf("apiprod.dol.gov", "api.dol.gov", "enforcedata.dol.gov"), Lizenz.GEMEINFREI, listOf("https://www.dataportal.dol.gov/terms-of-use", "https://www.usa.gov/government-works")),
        // Klagen, Urteile und Behoerdenentscheidungen: Justizministerium (US-Regierungsdaten), Urteile des EuGH und EuG aus CELLAR
        // (Beschluss 2011/833/EU), CMA-Faelle ueber GOV.UK (Open Government Licence v3.0)
        Art("klagen", listOf("justice.gov", "publications.europa.eu", "eur-lex.europa.eu", "gov.uk"), Lizenz.GEMISCHT, listOf(
            "https://www.justice.gov/legalpolicies", "https://eur-lex.europa.eu/eli/dec/2011/833/oj",
            "https://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/",
        )),
        Art("fruehsignale", listOf("eia.gov", "tsa.gov", "usdmdataservices.unl.edu", "agsi.gie.eu", "drewry.co.uk", "aar.org", "wsts.org"), Lizenz.GEMISCHT, listOf("https://www.eia.gov/about/copyrights_reuse.php", "https://droughtmonitor.unl.edu/About/Terms.aspx")),
    )

    // Hosts, unter denen Buendel und Projekt-Links liegen duerfen.
    val PROJEKT_HOSTS = listOf("github.com", "raw.githubusercontent.com", "github.io", "cdn.jsdelivr.net", "codeberg.org", "codeberg.page", "buymeacoffee.com")

    // Namen der Projekt-Links, die ein Buendel ersetzen darf.
    val LINK_NAMEN = setOf("repo", "releases", "releasesApi", "issues", "spenden", "lizenz")

    // Kopfzeilen, die ein Buendel nur bei Yahoo setzen darf.
    private val FESTE_KOEPFE = setOf("user-agent", "referer", "origin")

    // Kopfzeilen, die ein Buendel nie setzt (Anmeldung, Cookies, Verbindung, Schluessel).
    private val KOPF_GESPERRT = setOf(
        "host", "cookie", "cookie2", "authorization", "proxy-authorization", "connection", "content-length", "content-type",
        "transfer-encoding", "te", "upgrade", "x-api-key", "x-finnhub-token", "apca-api-key-id", "apca-api-secret-key",
    )

    // Einstellungen je Quelle. Hosts ohne Schema, Pfade mit fuehrendem Schraegstrich.
    data class Quelle(
        // Abschalter: Abrufe enden sofort wie ohne Netz, die App zeigt den zuletzt gespeicherten Stand
        val aus: Boolean = false,
        // Ersatzhosts je Host, der Reihe nach, wenn ein Abruf scheitert
        val ersatz: Map<String, List<String>> = emptyMap(),
        // Host, der immer durch einen anderen ersetzt wird (Umzug)
        val umzug: Map<String, String> = emptyMap(),
        // Pfadanfang, der durch einen anderen ersetzt wird (neue Schnittstellenfassung)
        val pfade: Map<String, String> = emptyMap(),
        // Kopfzeilen, die gelten, wenn der Abruf sie nicht selbst setzt
        val kopf: Map<String, String> = emptyMap(),
        // Feld- und Schluesselnamen fuer Leser, die das zulassen
        val felder: Map<String, String> = emptyMap(),
        // 0: Zeitlimit des Abrufs bleibt
        val zeitlimitMs: Int = 0,
        // Mindestabstand zwischen zwei Abrufen dieser Quelle
        val abstandMs: Int = 0,
        // Pause nach 403/429, wenn die Antwort keine Wartezeit nennt
        val pauseMs: Int = 60_000,
    )

    // Hinweis an die Nutzer; stufe „dringend“ erscheint als Dialog, sonst als Karte auf der Seite Datenquellen.
    // minApp/maxApp: nur fuer Fassungen ab minApp bzw. bis maxApp (leer: ohne Grenze), damit eine dringende Mitteilung gezielt
    // die betroffenen Fassungen erreicht.
    data class Hinweis(
        val id: String, val stufe: String, val titel: String, val text: String, val link: String, val titelEn: String, val textEn: String,
        val minApp: String = "", val maxApp: String = "",
    ) {
        val dringend: Boolean get() = stufe == "dringend" || stufe == "urgent"

        // Gilt der Hinweis fuer diese Fassung der App?
        fun gilt(fassung: String): Boolean =
            (minApp.isEmpty() || !de.troetenrepublik.app.market.Aktualisierung.neuer(minApp, fassung)) &&
                (maxApp.isEmpty() || !de.troetenrepublik.app.market.Aktualisierung.neuer(fassung, maxApp))
    }

    data class Buendel(
        val version: Long,
        val adressen: List<String>,
        val links: Map<String, String>,
        val quellen: Map<String, Quelle>,
        val hinweise: List<Hinweis>,
        val einstellungen: Map<String, Double>,
        // Kleinste Fassung der App, die dieses Buendel liest; aeltere Fassungen lassen es liegen und behalten das letzte.
        val minApp: String = "",
        // Neuer Ausgangspunkt fuer die mitlaufende Chrome-Kennung: Hauptfassung browserChrome gilt ab dem Tag browserAb.
        val browserChrome: Int = 0,
        val browserAb: String = "",
    ) {
        fun quelle(id: String): Quelle = quellen[id] ?: Quelle()
    }

    // Wo das Buendel liegt: im App-Repo auf main, als Ersatz dieselbe Datei ueber jsDelivr. Beides antwortet erst, wenn
    // das Repo oeffentlich ist; bis dahin gilt das Eingebaute.
    // Der dritte Ort (GitHub Pages) ist vorbereitet: er wird nur gefragt, wenn die ersten beiden nicht antworten.
    val ADRESSEN = listOf(
        "https://raw.githubusercontent.com/regard2462/Tryfolio/main/quellen.json",
        "https://cdn.jsdelivr.net/gh/regard2462/Tryfolio@main/quellen.json",
        "https://regard2462.github.io/Tryfolio/quellen.json",
    )

    val EINGEBAUT = Buendel(
        version = 1,
        adressen = ADRESSEN,
        links = mapOf(
            "repo" to "https://github.com/regard2462/Tryfolio",
            "releases" to "https://github.com/regard2462/Tryfolio/releases",
            "releasesApi" to "https://api.github.com/repositories/1457404599/releases/latest",
            "issues" to "https://github.com/regard2462/Tryfolio/issues",
            "spenden" to "https://buymeacoffee.com/regard2462",
            "lizenz" to "https://github.com/regard2462/Tryfolio/blob/main/LICENSE",
        ),
        quellen = mapOf(
            // Beide Yahoo-Hosts liefern dieselben Schnittstellen
            "yahoo" to Quelle(
                ersatz = mapOf(
                    "query1.finance.yahoo.com" to listOf("query2.finance.yahoo.com"),
                    "query2.finance.yahoo.com" to listOf("query1.finance.yahoo.com"),
                ),
                felder = mapOf("kennzahlen" to "trailingPE,priceToBook,epsTrailingTwelveMonths,epsForward,bookValue,trailingAnnualDividendYield"),
            ),
        ),
        hinweise = emptyList(),
        einstellungen = emptyMap(),
    )

    // ------------------------------------------------------------ Hosts

    // Gehoert host zur Endung e (gleich oder Unterdomain)?
    fun passt(host: String, e: String): Boolean {
        val h = host.lowercase().trimEnd('.')
        return h == e || h.endsWith(".$e")
    }

    // Quelle eines Hosts: die laengste passende Endung gewinnt (ecb.europa.eu vor einer allgemeineren).
    fun quelleZu(host: String): String? =
        ARTEN.flatMap { a -> a.hosts.filter { passt(host, it) }.map { it.length to a.id } }.maxByOrNull { it.first }?.second

    private val HOSTNAME = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")

    fun hostErlaubt(id: String, host: String): Boolean {
        val art = ARTEN.firstOrNull { it.id == id } ?: return false
        return HOSTNAME.matches(host) && art.hosts.any { passt(host, it) }
    }

    // https-Adresse auf einem Projekt-Host?
    fun projektAdresse(url: String): Boolean {
        val m = Regex("^https://([^/:?#]+)(/[^\\s]*)?$").find(url) ?: return false
        val host = m.groupValues[1].lowercase()
        return HOSTNAME.matches(host) && PROJEKT_HOSTS.any { passt(host, it) }
    }

    // Alle Hosts und Adressen des Buendels auf den festen Listen?
    fun hostsErlaubt(b: Buendel): Boolean {
        if (b.adressen.any { !projektAdresse(it) }) return false
        if (b.links.any { (k, v) -> k !in LINK_NAMEN || !projektAdresse(v) }) return false
        return b.quellen.all { (id, q) ->
            q.ersatz.all { (h, liste) -> hostErlaubt(id, h) && liste.all { hostErlaubt(id, it) } } &&
                q.umzug.all { (a, z) -> hostErlaubt(id, a) && hostErlaubt(id, z) }
        }
    }

    // ------------------------------------------------------------ Kopfzeilen und Pfade

    private val KOPF_NAME = Regex("^[A-Za-z0-9!#$%&'*+.^_`|~-]{1,64}$")

    // Nur druckbares ASCII, nicht leer, hoechstens 400 Zeichen, kein gesperrter Name; sonst null (Standard gilt).
    fun kopfBereinigt(name: String, wert: String): Pair<String, String>? {
        if (!KOPF_NAME.matches(name) || name.lowercase() in KOPF_GESPERRT) return null
        val w = wert.trim()
        if (w.isEmpty() || w.length > 400 || w.any { it.code < 0x20 || it.code > 0x7e }) return null
        return name to w
    }

    // Pfad beginnt mit /, ohne Abfrage, Anker, Leerzeichen oder Ruecksprung.
    fun pfadErlaubt(p: String): Boolean =
        p.startsWith("/") && p.length <= 200 && !p.contains("..") && !p.contains("//") && p.none { it == '?' || it == '#' || it.code <= 0x20 || it.code > 0x7e }

    // ------------------------------------------------------------ Lesen

    private val json = Json { ignoreUnknownKeys = true; isLenient = false }

    private fun JsonObject.text(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    private fun JsonObject.zahl(k: String): Long? = (this[k] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

    private fun textMap(o: JsonObject?): Map<String, String>? {
        if (o == null) return null
        return o.mapValues { (_, v) -> (v as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null }
    }

    private fun textListe(e: Any?): List<String>? =
        (e as? JsonArray)?.map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: return null }

    // Eine Quelle aus dem Buendel ueber den eingebauten Werten; null bei falscher Form.
    private fun quelleLesen(o: JsonObject, standard: Quelle, id: String = ""): Quelle? {
        var q = standard
        o["off"]?.let { q = q.copy(aus = (it as? JsonPrimitive)?.booleanOrNull ?: return null) }
        o["fallbacks"]?.let { e ->
            val m = (e as? JsonObject) ?: return null
            q = q.copy(ersatz = m.entries.associate { (k, v) -> k.lowercase() to (textListe(v)?.map { it.lowercase() } ?: return null) })
        }
        o["moves"]?.let { q = q.copy(umzug = textMap(it as? JsonObject)?.mapKeys { (k, _) -> k.lowercase() }?.mapValues { (_, v) -> v.lowercase() } ?: return null) }
        o["paths"]?.let { e ->
            val m = textMap(e as? JsonObject) ?: return null
            if (m.any { (a, z) -> !pfadErlaubt(a) || !pfadErlaubt(z) }) return null
            q = q.copy(pfade = m)
        }
        o["headers"]?.let { e ->
            val m = textMap(e as? JsonObject) ?: return null
            // Ungueltige Kopfzeilen fallen weg, der eingebaute Wert bleibt
            // Kennung und Herkunft setzt ein Buendel nur bei Yahoo; bei den anderen Quellen bleibt die Kennung der App.
            q = q.copy(kopf = q.kopf + m.mapNotNull { (k, v) -> kopfBereinigt(k, v)?.takeIf { id == "yahoo" || k.lowercase() !in FESTE_KOEPFE } })
        }
        o["fields"]?.let { q = q.copy(felder = q.felder + (textMap(it as? JsonObject) ?: return null)) }
        fun grenze(k: String, max: Int): Int? = o[k]?.let { e -> ((e as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull ?: return null).coerceIn(0, max.toLong()).toInt() }
        if (o.containsKey("timeoutMs")) q = q.copy(zeitlimitMs = grenze("timeoutMs", 120_000) ?: return null)
        if (o.containsKey("gapMs")) q = q.copy(abstandMs = grenze("gapMs", 10_000) ?: return null)
        if (o.containsKey("pauseMs")) q = q.copy(pauseMs = grenze("pauseMs", 3_600_000) ?: return null)
        return q
    }

    // Buendel aus dem Text, mit den eingebauten Werten fuer fehlende Schluessel; null bei falscher Form.
    fun lesen(text: String, standard: Buendel = EINGEBAUT): Buendel? = runCatching {
        val o = json.parseToJsonElement(text) as? JsonObject ?: return null
        val version = o.zahl("version")?.takeIf { it > 0 } ?: return null
        val adressen = o["bundleUrls"]?.let { textListe(it) ?: return null }?.takeIf { it.isNotEmpty() } ?: standard.adressen
        val links = o["links"]?.let { standard.links + (textMap(it as? JsonObject) ?: return null) } ?: standard.links
        val quellen = standard.quellen.toMutableMap()
        (o["sources"] as? JsonObject)?.forEach { (id, e) ->
            // Unbekannte Quellen (aus einer spaeteren Fassung) zaehlen nicht
            if (ARTEN.none { it.id == id }) return@forEach
            quellen[id] = quelleLesen(e as? JsonObject ?: return null, standard.quelle(id), id) ?: return null
        }
        if (o.containsKey("sources") && o["sources"] !is JsonObject) return null
        val hinweise = o["notices"]?.let { e ->
            (e as? JsonArray ?: return null).map { n ->
                val h = n as? JsonObject ?: return null
                Hinweis(
                    id = h.text("id")?.takeIf { it.isNotBlank() } ?: return null,
                    stufe = h.text("level").orEmpty(),
                    titel = h.text("title").orEmpty().take(200),
                    text = h.text("body").orEmpty().take(2000),
                    link = h.text("url").orEmpty().takeIf { it.isEmpty() || it.startsWith("https://") }.orEmpty(),
                    titelEn = h.text("titleEn").orEmpty().take(200),
                    textEn = h.text("bodyEn").orEmpty().take(2000),
                    minApp = h.text("minApp").orEmpty().take(20),
                    maxApp = h.text("maxApp").orEmpty().take(20),
                )
            }
        } ?: standard.hinweise
        val einstellungen = (o["tuning"] as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.let { k to it } }?.toMap()
            ?: standard.einstellungen
        val minApp = o.text("minApp").orEmpty().take(20)
        val browser = o["browser"] as? JsonObject
        val chrome = browser?.zahl("chrome")?.toInt()?.takeIf { it in 100..999 } ?: 0
        val ab = browser?.text("ab")?.takeIf { Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(it) }.orEmpty()
        Buendel(version, adressen, links, quellen, hinweise, einstellungen, minApp, if (ab.isEmpty()) 0 else chrome, ab)
    }.getOrNull()

    // ------------------------------------------------------------ Pruefen

    enum class Grund { SIGNATUR, FORM, HOST, VERSION, FASSUNG }

    sealed class Ergebnis {
        data class Angenommen(val buendel: Buendel) : Ergebnis()
        data class Abgelehnt(val grund: Grund) : Ergebnis()
    }

    // Signatur als 128 Hex-Zeichen (Leerraum egal); null bei anderer Form.
    fun signaturLesen(text: String): ByteArray? {
        val h = text.filterNot { it.isWhitespace() }
        if (h.length != 128 || h.any { it !in "0123456789abcdefABCDEF" }) return null
        return ByteArray(64) { h.substring(2 * it, 2 * it + 2).toInt(16).toByte() }
    }

    fun hex(s: String): ByteArray = ByteArray(s.length / 2) { s.substring(2 * it, 2 * it + 2).toInt(16).toByte() }

    // Signatur gueltig mit einem der Schluessel (Hauptschluessel oder Ersatz-Hauptschluessel)?
    fun signaturGueltig(bytes: ByteArray, signatur: String, schluessel: List<ByteArray>, sha512: (ByteArray) -> ByteArray): Boolean {
        val sig = signaturLesen(signatur) ?: return false
        return schluessel.any { Ed25519.pruefen(it, bytes, sig, sha512) }
    }

    // Ein Buendel gilt nur mit gueltiger Signatur ueber genau diese Bytes (mit einem von mehreren Schluesseln), erlaubten
    // Hosts, hoeherer version und, wenn es eine Mindestfassung nennt, nur fuer diese oder neuere Fassungen der App.
    fun pruefen(
        bytes: ByteArray, signatur: String, schluessel: List<ByteArray>, aktiveVersion: Long, sha512: (ByteArray) -> ByteArray,
        standard: Buendel = EINGEBAUT, fassung: String = de.troetenrepublik.app.market.Aktualisierung.FASSUNG,
    ): Ergebnis {
        if (!signaturGueltig(bytes, signatur, schluessel, sha512)) return Ergebnis.Abgelehnt(Grund.SIGNATUR)
        val b = lesen(bytes.toString(Charsets.UTF_8), standard) ?: return Ergebnis.Abgelehnt(Grund.FORM)
        if (!hostsErlaubt(b)) return Ergebnis.Abgelehnt(Grund.HOST)
        if (b.minApp.isNotEmpty() && de.troetenrepublik.app.market.Aktualisierung.neuer(b.minApp, fassung)) return Ergebnis.Abgelehnt(Grund.FASSUNG)
        if (b.version <= aktiveVersion) return Ergebnis.Abgelehnt(Grund.VERSION)
        return Ergebnis.Angenommen(b)
    }

    fun pruefen(
        bytes: ByteArray, signatur: String, schluessel: ByteArray, aktiveVersion: Long, sha512: (ByteArray) -> ByteArray,
        standard: Buendel = EINGEBAUT,
    ): Ergebnis = pruefen(bytes, signatur, listOf(schluessel), aktiveVersion, sha512, standard)

    // ------------------------------------------------------------ Adressen umschreiben

    // Umzug und neue Pfade einer Quelle auf eine Adresse anwenden (Host ohne Schema, Pfad mit /).
    fun umschreiben(q: Quelle, host: String, pfad: String): Pair<String, String> {
        val h = q.umzug[host.lowercase()] ?: host
        val p = q.pfade.entries.filter { pfad.startsWith(it.key) }.maxByOrNull { it.key.length }?.let { (a, z) -> z + pfad.removePrefix(a) } ?: pfad
        return h to p
    }

    // Hosts in der Reihenfolge der Versuche: zuerst der zuletzt erfolgreiche Ersatz, wenn er frisch ist.
    fun versuche(q: Quelle, host: String, umweg: String?): List<String> {
        val liste = listOf(host) + q.ersatz[host.lowercase()].orEmpty().filter { it != host }
        return if (umweg != null && umweg in liste) listOf(umweg) + (liste - umweg) else liste
    }

    // ------------------------------------------------------------ Gesundheit

    enum class Fehler { LEER, FORM, GESPERRT, ZEIT, NETZ, SERVER, AUS }

    data class Gesundheit(
        val erfolge: Int = 0,
        val fehler: Int = 0,
        // Fehler seit dem letzten Erfolg
        val inFolge: Int = 0,
        val letzterErfolg: Long = 0,
        val letzterFehler: Long = 0,
        val art: Fehler? = null,
        val code: Int = 0,
        // Host, ueber den der letzte Erfolg kam, wenn es ein Ersatz war
        val umweg: String = "",
    )

    fun erfolg(g: Gesundheit, jetzt: Long, umweg: String = ""): Gesundheit =
        g.copy(erfolge = g.erfolge + 1, inFolge = 0, letzterErfolg = jetzt, umweg = umweg)

    fun fehler(g: Gesundheit, jetzt: Long, art: Fehler, code: Int = 0): Gesundheit =
        g.copy(fehler = g.fehler + 1, inFolge = g.inFolge + 1, letzterFehler = jetzt, art = art, code = code)

    // Ausgang einer Antwort: OK, NEUTRAL (etwa 404 fuer ein unbekanntes Kuerzel, zaehlt nicht) oder ein Fehler.
    enum class Ausgang { OK, NEUTRAL, LEER, GESPERRT, SERVER }

    fun einordnen(code: Int, leer: Boolean): Ausgang = when {
        code in 200..299 -> if (leer && code != 204) Ausgang.LEER else Ausgang.OK
        code == 403 || code == 429 || code == 451 -> Ausgang.GESPERRT
        code >= 500 -> Ausgang.SERVER
        else -> Ausgang.NEUTRAL
    }

    enum class Ampel { GRAU, GRUEN, GELB, ROT }

    // Grau: noch nie abgerufen oder abgeschaltet. Rot: mindestens drei Fehler in Folge und seit sechs Stunden kein Erfolg.
    fun ampel(g: Gesundheit?, aus: Boolean, jetzt: Long): Ampel = when {
        aus -> Ampel.GRAU
        g == null || (g.erfolge == 0 && g.fehler == 0) -> Ampel.GRAU
        g.inFolge == 0 -> Ampel.GRUEN
        g.inFolge >= 3 && (g.letzterErfolg <= 0 || jetzt - g.letzterErfolg > 6 * 3_600_000L) -> Ampel.ROT
        else -> Ampel.GELB
    }

    // Umweg ueber den Ersatz gilt 30 Minuten nach seinem letzten Erfolg, dann wird der Haupthost wieder zuerst versucht.
    fun umwegAktiv(g: Gesundheit?, jetzt: Long): String? =
        g?.umweg?.takeIf { it.isNotEmpty() && g.inFolge == 0 && jetzt - g.letzterErfolg < 30 * 60_000L }

    // Wartezeit mit Zufallsstreuung von plus/minus 25 Prozent; zufall zwischen 0 und 1.
    fun streuung(ms: Long, zufall: Double): Long = (ms * (0.75 + 0.5 * zufall.coerceIn(0.0, 1.0))).toLong()

    // Pause nach 403/429: Retry-After in Sekunden, wenn genannt, sonst standard; hoechstens eine Stunde.
    fun pause(retryAfter: String?, standardMs: Int): Long {
        val s = retryAfter?.trim()?.toLongOrNull()
        return ((s?.times(1000)) ?: standardMs.toLong()).coerceIn(0, 3_600_000)
    }

    // Befund eines Gesundheitschecks: OK, BLOCKED (gesperrt, kein Netz, Serverfehler) oder DRIFT (Antwort unlesbar).
    enum class Befund { OK, BLOCKED, DRIFT }

    fun befund(ok: Boolean, art: Fehler?): Befund = when {
        ok -> Befund.OK
        art == Fehler.GESPERRT || art == Fehler.NETZ || art == Fehler.ZEIT || art == Fehler.SERVER || art == Fehler.AUS -> Befund.BLOCKED
        else -> Befund.DRIFT
    }

    // ------------------------------------------------------------ Inhalte

    // Wann ein Inhalt (Wahlen, Ratings, Datenbuendel ...) zuletzt lesbar ankam und wie oft er seitdem unlesbar war.
    data class InhaltStand(val gueltig: Long = 0, val fehler: Long = 0, val inFolge: Int = 0)

    fun inhaltGelesen(s: InhaltStand?, ok: Boolean, jetzt: Long): InhaltStand {
        val a = s ?: InhaltStand()
        return if (ok) a.copy(gueltig = jetzt, inFolge = 0) else a.copy(fehler = jetzt, inFolge = a.inFolge + 1)
    }

    fun inhalteText(m: Map<String, InhaltStand>): String = m.entries.sortedBy { it.key }.joinToString("\n") { (k, v) -> "$k\t${v.gueltig}\t${v.fehler}\t${v.inFolge}" }

    fun inhalteLesen(text: String): Map<String, InhaltStand> = text.lines().mapNotNull { z ->
        val t = z.split('\t')
        if (t.size != 4 || t[0].isBlank()) return@mapNotNull null
        t[0] to InhaltStand(t[1].toLongOrNull() ?: return@mapNotNull null, t[2].toLongOrNull() ?: return@mapNotNull null, t[3].toIntOrNull() ?: return@mapNotNull null)
    }.toMap()

    // ------------------------------------------------------------ Gesundheit speichern

    fun gesundheitText(m: Map<String, Gesundheit>): String = JsonObject(m.mapValues { (_, g) ->
        JsonObject(buildMap {
            put("ok", JsonPrimitive(g.erfolge)); put("err", JsonPrimitive(g.fehler)); put("row", JsonPrimitive(g.inFolge))
            put("lastOk", JsonPrimitive(g.letzterErfolg)); put("lastErr", JsonPrimitive(g.letzterFehler))
            g.art?.let { put("kind", JsonPrimitive(it.name)) }
            put("code", JsonPrimitive(g.code)); put("via", JsonPrimitive(g.umweg))
        })
    }).toString()

    fun gesundheitLesen(text: String): Map<String, Gesundheit> = runCatching {
        val o = json.parseToJsonElement(text) as? JsonObject ?: return emptyMap()
        o.mapNotNull { (id, e) ->
            val g = e as? JsonObject ?: return@mapNotNull null
            fun z(k: String) = (g[k] as? JsonPrimitive)?.longOrNull ?: 0L
            id to Gesundheit(
                erfolge = z("ok").toInt(), fehler = z("err").toInt(), inFolge = z("row").toInt(),
                letzterErfolg = z("lastOk"), letzterFehler = z("lastErr"),
                art = g.text("kind")?.let { k -> Fehler.values().firstOrNull { it.name == k } },
                code = z("code").toInt(), umweg = g.text("via").orEmpty(),
            )
        }.toMap()
    }.getOrDefault(emptyMap())
}
