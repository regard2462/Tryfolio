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
    data class Art(val id: String, val hosts: List<String>)

    val ARTEN: List<Art> = listOf(
        Art("yahoo", listOf("yahoo.com")),
        Art("sec", listOf("sec.gov")),
        Art("ezb", listOf("ecb.europa.eu")),
        Art("wikipedia", listOf("wikipedia.org", "wikidata.org", "wikimedia.org")),
        Art("polymarket", listOf("polymarket.com")),
        Art("kalshi", listOf("kalshi.com")),
        // Hacker News (Algolia) und Mastodon als offene Ersatzsignale seit 5.92, Bluesky mit eigenem App-Passwort (Anmeldung
        // bei bsky.social, Abrufe ueber den Server des Kontos)
        Art("erwaehnungen", listOf("reddit.com", "apewisdom.io", "tradestie.com", "hn.algolia.com", "mastodon.social", "bsky.social", "bsky.network", "bsky.app")),
        // Dazu die Mitgliederliste von unitedstates/congress-legislators (5.96)
        Art("kongress", listOf("house.gov", "senate.gov", "congress.gov", "unitedstates.github.io")),
        Art("usbehoerden", listOf("usaspending.gov", "fec.gov", "uspto.gov", "oge.gov", "whitehouse.gov", "fda.gov", "finra.org", "texas.gov", "courtlistener.com")),
        // Ersatzquellen 5.92: Lieferketten-Index der New York Fed, Shiller-Daten (Seite und Speicherdienst der Datei)
        Art("uswirtschaft", listOf("treasury.gov", "bls.gov", "bea.gov", "federalreserve.gov", "stlouisfed.org", "cftc.gov", "newyorkfed.org", "shillerdata.com", "img1.wsimg.com")),
        Art("bafin", listOf("bafin.de")),
        // Kalender der Nasdaq seit 5.95; die laengste passende Host-Endung gewinnt, darum landen die Kalender nicht
        // bei den europaeischen Registern unter nasdaq.com
        Art("nasdaq", listOf("api.nasdaq.com")),
        Art("europa", listOf(
            "amf-france.org", "afm.nl", "fca.org.uk", "fi.se", "1info.it", "cnb.cz", "mnb.hu", "luxse.com", "cse.com.cy",
            "nasdaq.com", "data.gouv.fr", "consob.it", "investegate.co.uk",
        )),
        Art("welt", listOf("hkex.com.hk", "cse.lk", "mcd.om", "nseindia.com", "bseindia.com", "sedi.ca", "ser-ag.com", "boursakuwait.com.kw")),
        // Put/Call-Verhaeltnis der Cboe seit 5.92
        Art("stimmung", listOf("alternative.me", "cnn.io", "cnn.com", "aaii.com", "cboe.com")),
        Art("truth", listOf("trumpstruth.org")),
        Art("nachrichten", listOf("guardianapis.com", "finnhub.io", "alphavantage.co", "eodhd.com")),
        // Was Investoren schreiben (5.92): Briefe von den Seiten der Absender (engine/Investorenbriefe.kt)
        Art("investorenbriefe", listOf(
            "givernycapital.com", "tweedyfunds.com", "southeasternasset.com", "weitzinvestments.com", "brownadvisory.com", "montaka.com",
            "generationim.com", "polencapital.com", "oaktreecapital.com", "mklgroup.com", "markel.com", "fairfax.ca", "himcap.com",
        )),
        // Dazu der offene Spiegel der Senatsmeldungen (5.92); jsDelivr als Ausweichweg fuer dieselben Dateien
        Art("github", listOf("api.github.com", "raw.githubusercontent.com", "cdn.jsdelivr.net")),
        // Fruehe Signale (5.92): EIA-Lager, TSA-Fluggaeste, Drought Monitor, EU-Gasspeicher (GIE AGSI+)
        // Dazu Containerfracht (Drewry), Wochenzahlen der US-Bahnen (AAR) und Halbleiterumsaetze (WSTS) seit 5.92
        Art("fruehsignale", listOf("eia.gov", "tsa.gov", "usdmdataservices.unl.edu", "agsi.gie.eu", "drewry.co.uk", "aar.org", "wsts.org")),
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
    data class Hinweis(val id: String, val stufe: String, val titel: String, val text: String, val link: String, val titelEn: String, val textEn: String) {
        val dringend: Boolean get() = stufe == "dringend" || stufe == "urgent"
    }

    data class Buendel(
        val version: Long,
        val adressen: List<String>,
        val links: Map<String, String>,
        val quellen: Map<String, Quelle>,
        val hinweise: List<Hinweis>,
        val einstellungen: Map<String, Double>,
    ) {
        fun quelle(id: String): Quelle = quellen[id] ?: Quelle()
    }

    // Wo das Buendel liegt: im App-Repo auf main, als Ersatz dieselbe Datei ueber jsDelivr. Beides antwortet erst, wenn
    // das Repo oeffentlich ist; bis dahin gilt das Eingebaute.
    val ADRESSEN = listOf(
        "https://raw.githubusercontent.com/regard2462/Tryfolio/main/quellen.json",
        "https://cdn.jsdelivr.net/gh/regard2462/Tryfolio@main/quellen.json",
    )

    val EINGEBAUT = Buendel(
        version = 1,
        adressen = ADRESSEN,
        links = mapOf(
            "repo" to "https://github.com/regard2462/Tryfolio",
            "releases" to "https://github.com/regard2462/Tryfolio/releases",
            "releasesApi" to "https://api.github.com/repositories/1444103610/releases/latest",
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
                )
            }
        } ?: standard.hinweise
        val einstellungen = (o["tuning"] as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.let { k to it } }?.toMap()
            ?: standard.einstellungen
        Buendel(version, adressen, links, quellen, hinweise, einstellungen)
    }.getOrNull()

    // ------------------------------------------------------------ Pruefen

    enum class Grund { SIGNATUR, FORM, HOST, VERSION }

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

    // Ein Buendel gilt nur mit gueltiger Signatur ueber genau diese Bytes, erlaubten Hosts und hoeherer version.
    fun pruefen(
        bytes: ByteArray, signatur: String, schluessel: ByteArray, aktiveVersion: Long, sha512: (ByteArray) -> ByteArray,
        standard: Buendel = EINGEBAUT,
    ): Ergebnis {
        val sig = signaturLesen(signatur) ?: return Ergebnis.Abgelehnt(Grund.SIGNATUR)
        if (!Ed25519.pruefen(schluessel, bytes, sig, sha512)) return Ergebnis.Abgelehnt(Grund.SIGNATUR)
        val b = lesen(bytes.toString(Charsets.UTF_8), standard) ?: return Ergebnis.Abgelehnt(Grund.FORM)
        if (!hostsErlaubt(b)) return Ergebnis.Abgelehnt(Grund.HOST)
        if (b.version <= aktiveVersion) return Ergebnis.Abgelehnt(Grund.VERSION)
        return Ergebnis.Angenommen(b)
    }

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
