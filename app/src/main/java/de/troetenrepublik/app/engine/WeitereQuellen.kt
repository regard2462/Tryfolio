package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Fruehwarnung.Ampel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

// Weitere Quellen: Daten, deren Bedingungen den Abruf fuer den eigenen,
// nicht kommerziellen Gebrauch auf dem Geraet zulassen. Put/Call-Verhaeltnis der CBOE, Containerfrachtraten von Drewry,
// Wochenzahlen der US-Bahnen (AAR), weltweite Halbleiterumsaetze (WSTS), mit eigenem Token die Finanzerklaerungen der
// US-Bundesrichter (CourtListener) und mit eigenem App-Passwort die Suche bei Bluesky. Hier stehen Adressen und Leser,
// die Abrufe in market/WeitereAbrufe.kt. Nichts davon liegt der App bei und nichts wird dauerhaft gespeichert.
object WeitereQuellen {

    private fun JsonObject.text(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.zahl(k: String): Long? = (this[k] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

    // Text einer HTML-Seite ohne Skripte, Stile und Tags, Leerraum zusammengefasst.
    fun htmlText(html: String): String =
        html.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ").replace(Regex("<[^>]+>"), " ")
            .let { de.troetenrepublik.app.market.InsiderQuellen.entitaeten(it) }
            .replace(' ', ' ').replace(Regex("\\s+"), " ").trim()

    private fun ganzzahl(s: String): Long? = s.replace(",", "").trim().toLongOrNull()

    // ------------------------------------------------------------ CBOE Put/Call

    // Tagesstatistik der Optionsboerse CBOE als JSON. Die Bedingungen erlauben, eine Kopie fuer den persoenlichen, nicht
    // kommerziellen Gebrauch zu laden, aber nicht, sie in einem Datenbestand zu sammeln: deshalb nur der letzte Tag und
    // nur im Speicher.
    fun cboeAdresse(tag: LocalDate) = "https://cdn.cboe.com/data/us/options/market_statistics/daily/${tag}_daily_options"
    const val CBOE_SEITE = "https://www.cboe.com/us/options/market_statistics/daily/"

    // Ostersonntag nach der gregorianischen Osterformel (Meeus/Jones/Butcher).
    fun ostern(jahr: Int): LocalDate {
        val a = jahr % 19
        val b = jahr / 100
        val c = jahr % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val monat = (h + l - 7 * m + 114) / 31
        val tag = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(jahr, monat, tag)
    }

    // Feiertage der US-Boersen: am Samstag frei am Freitag davor, am Sonntag am Montag danach (Neujahr am Samstag
    // bleibt ohne Ersatztag).
    fun usFeiertag(d: LocalDate): Boolean {
        val j = d.year
        fun nter(monat: Month, n: Int, tag: DayOfWeek) = LocalDate.of(j, monat, 1).with(TemporalAdjusters.dayOfWeekInMonth(n, tag))
        fun fest(m: Month, t: Int, ersatzFreitag: Boolean = true): LocalDate {
            val x = LocalDate.of(j, m, t)
            return when (x.dayOfWeek) {
                DayOfWeek.SATURDAY -> if (ersatzFreitag) x.minusDays(1) else x
                DayOfWeek.SUNDAY -> x.plusDays(1)
                else -> x
            }
        }
        val tage = buildList {
            add(fest(Month.JANUARY, 1, ersatzFreitag = false))
            add(nter(Month.JANUARY, 3, DayOfWeek.MONDAY))
            add(nter(Month.FEBRUARY, 3, DayOfWeek.MONDAY))
            add(ostern(j).minusDays(2))
            add(LocalDate.of(j, Month.MAY, 31).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
            if (j >= 2022) add(fest(Month.JUNE, 19))
            add(fest(Month.JULY, 4))
            add(nter(Month.SEPTEMBER, 1, DayOfWeek.MONDAY))
            add(nter(Month.NOVEMBER, 4, DayOfWeek.THURSDAY))
            add(fest(Month.DECEMBER, 25))
        }
        return d in tage
    }

    fun usHandelstag(d: LocalDate) = d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY && !usFeiertag(d)

    // Tag, dessen Statistik sicher vorliegt: der letzte Handelstag vor heute, ab 22 Uhr New Yorker Zeit auch heute.
    // So fragt die App keinen Tag ohne Datei ab (die CBOE antwortet dann mit 403).
    fun cboeTag(jetztNewYork: LocalDateTime): LocalDate {
        var d = if (jetztNewYork.hour >= 22) jetztNewYork.toLocalDate() else jetztNewYork.toLocalDate().minusDays(1)
        while (!usHandelstag(d)) d = d.minusDays(1)
        return d
    }

    // Verhaeltnis gehandelter Verkaufs- zu Kaufoptionen: alle Optionen, nur Aktien, nur Indizes.
    data class PutCall(val tag: Long, val gesamt: Double?, val aktien: Double?, val index: Double?)

    fun putCallLesen(json: String, tag: LocalDate): PutCall? = runCatching {
        val o = Json.parseToJsonElement(json) as? JsonObject ?: return null
        val werte = (o["ratios"] as? JsonArray).orEmpty().mapNotNull { e ->
            val x = e as? JsonObject ?: return@mapNotNull null
            val name = x.text("name")?.trim()?.uppercase() ?: return@mapNotNull null
            val v = x.text("value")?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 } ?: return@mapNotNull null
            name to v
        }.toMap()
        val p = PutCall(tag.toEpochDay(), werte["TOTAL PUT/CALL RATIO"], werte["EQUITY PUT/CALL RATIO"], werte["INDEX PUT/CALL RATIO"])
        p.takeIf { it.gesamt != null || it.aktien != null }
    }.getOrNull()

    // Einordnung des Verhaeltnisses bei Aktienoptionen: 0 sorglos (unter 0,5: viel mehr Kauf- als Verkaufsoptionen),
    // 1 ueblich, 2 vorsichtig (ab 0,8: viele Absicherungen). Ueber die Jahre lag es meist zwischen 0,5 und 0,8.
    fun putCallLage(aktien: Double): Int = when {
        aktien < 0.5 -> 0
        aktien >= 0.8 -> 2
        else -> 1
    }

    // ------------------------------------------------------------ Drewry World Container Index

    // Wochenwert des Containerindex. Die Bedingungen erlauben, Auszuege fuer den eigenen Gebrauch zu laden; robots.txt
    // verlangt zehn Sekunden Abstand, die App fragt hoechstens alle zwoelf Stunden.
    const val DREWRY_SEITE = "https://www.drewry.co.uk/supply-chain-advisors/supply-chain-expertise/world-container-index-assessed-by-drewry"

    // Dollar je 40-Fuss-Container und Veraenderung zur Vorwoche in %.
    data class Wci(val tag: Long, val dollar: Double, val woche: Double)

    private val STEIGT = listOf("increased", "rose", "grew", "climbed", "jumped", "surged", "soared", "inched up", "edged up", "went up", "rallied", "spiked", "gained")
    private val FAELLT = listOf("decreased", "fell", "dropped", "declined", "slipped", "eased", "inched down", "edged down", "went down", "plunged", "plummeted", "slumped", "tumbled", "dipped", "lost", "retreated", "softened", "slid")

    private val MONATE = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    // Tag der Bewertung und Wert aus dem Text der Seite („Our detailed assessment for Thursday, 22 Oct 2026 The Drewry
    // World Container Index (WCI) ... increased 2% to $4,585 per 40ft container“). Null bei anderem Aufbau.
    fun wciLesen(html: String): Wci? =
        runCatching { wciRoh(html) }.getOrNull()?.takeIf { it.dollar in 100.0..100_000.0 && kotlin.math.abs(it.woche) < 100 }

    private fun wciRoh(html: String): Wci? {
        val t = htmlText(html)
        val kopf = Regex("(?i)assessment for \\w+,?\\s+(\\d{1,2})\\s+([A-Za-z]{3,9})\\.?\\s+(\\d{4})").find(t) ?: return null
        val monat = MONATE.indexOf(kopf.groupValues[2].take(3).lowercase()) + 1
        if (monat == 0) return null
        val tag = LocalDate.of(kopf.groupValues[3].toInt(), monat, kopf.groupValues[1].toInt())
        val rest = t.substring(kopf.range.last).take(800)
        val index = rest.indexOf("World Container Index", ignoreCase = true).takeIf { it >= 0 } ?: return null
        val satz = rest.substring(index).take(400)
        val verben = (STEIGT + FAELLT).joinToString("|") { it.replace(" ", "\\s+") }
        // Die erste Angabe nach dem Namen gilt (danach folgen die einzelnen Routen)
        val bewegt = Regex("(?i)($verben)\\s+(?:by\\s+)?(\\d+(?:\\.\\d+)?)\\s*%\\s+to\\s+(?:US)?\\$\\s?([\\d,]+(?:\\.\\d+)?)").find(satz)
        val gleich = Regex("(?i)(?:remained|was|stayed|held)\\s+(?:stable|unchanged|flat|steady)\\s+at\\s+(?:US)?\\$\\s?([\\d,]+(?:\\.\\d+)?)").find(satz)
        if (gleich != null && (bewegt == null || gleich.range.first < bewegt.range.first)) {
            return Wci(tag.toEpochDay(), gleich.groupValues[1].replace(",", "").toDouble(), 0.0)
        }
        if (bewegt != null) {
            val verb = bewegt.groupValues[1].lowercase().replace(Regex("\\s+"), " ")
            val pct = bewegt.groupValues[2].toDouble() * (if (verb in FAELLT) -1 else 1)
            return Wci(tag.toEpochDay(), bewegt.groupValues[3].replace(",", "").toDouble(), pct)
        }
        return null
    }

    // Ampel nach der Bewegung zur Vorwoche: unter 5 % gruen, ab 5 % gelb, ab 15 % rot (starke Spruenge zeigen Engpaesse
    // oder einbrechende Nachfrage).
    fun wciAmpel(woche: Double): Ampel = when {
        kotlin.math.abs(woche) >= 15 -> Ampel.ROT
        kotlin.math.abs(woche) >= 5 -> Ampel.GELB
        else -> Ampel.GRUEN
    }

    // ------------------------------------------------------------ AAR: Wochenzahlen der US-Bahnen

    // Pressemitteilungen des Bahnverbands als RSS. Keine Nutzungsbedingungen, robots.txt laesst den Feed zu.
    const val AAR_FEED = "https://www.aar.org/aar_news/weekly-rail-traffic-data/feed/"
    const val AAR_SEITE = "https://www.aar.org/data-center/rail-traffic-data/"

    // Woche (Ende als Tag seit 1970), Einheiten gesamt und Veraenderung zur Vorjahreswoche in %: gesamt, Gueterwagen,
    // Container und Auflieger.
    data class Bahnwoche(val ende: Long, val einheiten: Long, val gesamt: Double, val wagen: Double?, val container: Double?)

    private fun prozent(richtung: String, wert: String): Double? = wert.toDoubleOrNull()?.let { if (richtung.equals("down", true)) -it else it }

    private val MONATSNAMEN = listOf("january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december")

    // Juengste Woche aus dem Feed; null, wenn kein Beitrag passt.
    fun aarLesen(rss: String): Bahnwoche? = runCatching {
        Regex("(?s)<item>(.*?)</item>").findAll(rss).firstNotNullOfOrNull { item ->
            val roh = item.groupValues[1]
            val titel = Regex("(?s)<title>(.*?)</title>").find(roh)?.groupValues?.get(1).orEmpty()
            val ende = Regex("(?i)week ending (\\w+) (\\d{1,2}),? (\\d{4})").find(titel)?.let { m ->
                val monat = MONATSNAMEN.indexOf(m.groupValues[1].lowercase()) + 1
                if (monat == 0) null else LocalDate.of(m.groupValues[3].toInt(), monat, m.groupValues[2].toInt())
            } ?: return@firstNotNullOfOrNull null
            val inhalt = Regex("(?s)<content:encoded>(.*?)</content:encoded>").find(roh)?.groupValues?.get(1)
                ?: Regex("(?s)<description>(.*?)</description>").find(roh)?.groupValues?.get(1).orEmpty()
            val t = htmlText(inhalt.replace("<![CDATA[", "").replace("]]>", ""))
            val gesamt = Regex("(?i)total U\\.S\\. weekly rail traffic was ([\\d,]+) carloads and intermodal units,? (up|down) ([\\d.]+) percent").find(t)
                ?: return@firstNotNullOfOrNull null
            val wagen = Regex("(?i)Total carloads for the week ending [^.]*? were ([\\d,]+) carloads,? (up|down) ([\\d.]+) percent").find(t)
            val container = Regex("(?i)weekly intermodal volume was ([\\d,]+) containers and trailers,? (up|down) ([\\d.]+) percent").find(t)
            Bahnwoche(
                ende.toEpochDay(), ganzzahl(gesamt.groupValues[1]) ?: return@firstNotNullOfOrNull null,
                prozent(gesamt.groupValues[2], gesamt.groupValues[3]) ?: return@firstNotNullOfOrNull null,
                wagen?.let { prozent(it.groupValues[2], it.groupValues[3]) },
                container?.let { prozent(it.groupValues[2], it.groupValues[3]) },
            )
        }
    }.getOrNull()

    // Wie die Monatsreihen: ab 3 % weniger als in der Vorjahreswoche rot, weniger gelb.
    fun vorjahrAmpel(pct: Double): Ampel = when {
        pct <= -3 -> Ampel.ROT
        pct < 0 -> Ampel.GELB
        else -> Ampel.GRUEN
    }

    // ------------------------------------------------------------ WSTS: weltweite Halbleiterumsaetze

    // Die WSTS bietet die Monatszahlen seit 1986 als freie Datei ohne Anmeldung an. Die Adresse der Datei aendert sich
    // jeden Monat und steht auf dieser Seite.
    const val WSTS_SEITE = "https://www.wsts.org/67/Historical-Billings-Report"

    fun wstsLink(html: String): String? =
        Regex("(?i)href=\"([^\"]*WSTS-Historical-Billings-Report[^\"]*\\.xlsx)\"").find(html)?.groupValues?.get(1)
            ?.replace("&amp;", "&")
            ?.let { if (it.startsWith("http")) it else "https://www.wsts.org" + (if (it.startsWith("/")) it else "/$it") }

    // Weltweiter Umsatz im juengsten Monat (Milliarden Dollar) und Veraenderung zum Vorjahresmonat in %.
    data class Halbleiter(val jahr: Int, val monat: Int, val milliarden: Double, val vorjahr: Double) {
        val stand: Long get() = YearMonth.of(jahr, monat).atEndOfMonth().toEpochDay()
    }

    // Blatt „Monthly Data“: je Jahr eine Zeile mit der Jahreszahl, darunter Regionen und „Worldwide“ mit Januar bis
    // Dezember in den Spalten 1 bis 12 (Tausend Dollar). Fehlende Monate sind leer.
    fun wstsAusBlaettern(blaetter: Map<String, List<List<String>>>): Halbleiter? {
        val blatt = blaetter.entries.firstOrNull { it.key.contains("Monthly", true) }?.value ?: blaetter.values.firstOrNull() ?: return null
        val welt = HashMap<Int, List<Double?>>()
        var jahr: Int? = null
        for (z in blatt) {
            val a = z.firstOrNull()?.trim().orEmpty()
            when {
                Regex("^(19|20)\\d{2}$").matches(a) -> jahr = a.toInt()
                a.equals("Worldwide", true) && jahr != null ->
                    welt[jahr!!] = (1..12).map { i -> z.getOrNull(i)?.trim()?.toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() } }
            }
        }
        val letztesJahr = welt.keys.filter { j -> welt[j]!!.any { it != null } }.maxOrNull() ?: return null
        val monate = welt[letztesJahr]!!
        val m = monate.indexOfLast { it != null }
        val wert = monate[m] ?: return null
        val vorher = welt[letztesJahr - 1]?.getOrNull(m) ?: return null
        return Halbleiter(letztesJahr, m + 1, wert / 1_000_000, (wert / vorher - 1) * 100)
    }

    // ------------------------------------------------------------ CourtListener: Finanzerklaerungen der Bundesrichter

    // REST-Schnittstelle des Free Law Project. Nur mit eigenem kostenlosem Token; ohne Zahlung gelten 5 Abrufe je Minute,
    // 50 je Stunde und 125 am Tag. Die Erklaerungen sind nach US-Recht nur nicht kommerziell nutzbar.
    const val CL_API = "https://www.courtlistener.com/api/rest/v4/"
    const val CL_TOKEN_SEITE = "https://www.courtlistener.com/help/api/rest/"
    const val CL_SEITE = "https://www.courtlistener.com/financial-disclosures/"
    const val JEFS_SEITE = "https://pub.jefs.uscourts.gov/"

    // Zuletzt erfasste Erklaerungen mit ihren Anlagen, nur die Felder, die die App braucht.
    fun clErklaerungenAdresse(): String = CL_API + "financial-disclosures/?order_by=-date_created&fields=" +
        listOf(
            "id", "year", "person", "investments", "investments__description", "investments__transaction_during_reporting_period",
            "investments__transaction_date", "investments__transaction_value_code", "investments__redacted",
        ).joinToString(",")

    fun clPersonAdresse(id: Long) = CL_API + "people/$id/?fields=id,name_first,name_middle,name_last,name_suffix"

    fun clPersonId(url: String): Long? = Regex("/people/(\\d+)/?").find(url)?.groupValues?.get(1)?.toLongOrNull()
        ?: url.trim().toLongOrNull()

    // Ein gemeldetes Geschaeft: Person (Kennung), Jahr der Erklaerung, Anlage im Wortlaut, Art (K Kauf, V Verkauf,
    // S Sonstiges), Tag (JJJJ-MM-TT oder leer) und Wertcode der Spanne.
    data class RichterGeschaeft(val person: Long, val jahr: Int, val erklaerung: Long, val anlage: String, val art: String, val tag: String, val code: String)

    fun clArt(text: String): String {
        val t = text.trim().lowercase()
        return when {
            t.startsWith("buy") || t.startsWith("purchase") || t.startsWith("bought") -> "K"
            t.startsWith("sold") || t.startsWith("sale") || t.startsWith("sell") -> "V"
            else -> "S"
        }
    }

    fun clErklaerungenLesen(json: String): List<RichterGeschaeft> = runCatching {
        val o = Json.parseToJsonElement(json) as? JsonObject ?: return emptyList()
        (o["results"] as? JsonArray).orEmpty().flatMap { e ->
            val d = e as? JsonObject ?: return@flatMap emptyList()
            val person = clPersonId(d.text("person").orEmpty()) ?: return@flatMap emptyList()
            val jahr = d.zahl("year")?.toInt() ?: 0
            val id = d.zahl("id") ?: 0L
            (d["investments"] as? JsonArray).orEmpty().mapNotNull { a ->
                val x = a as? JsonObject ?: return@mapNotNull null
                if (x.text("redacted") == "true") return@mapNotNull null
                val geschaeft = x.text("transaction_during_reporting_period")?.trim().orEmpty()
                if (geschaeft.isEmpty() || geschaeft == "-1") return@mapNotNull null
                val anlage = x.text("description")?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                // Jahr 1750 steht bei CourtListener fuer ein unbekanntes Datum
                val tag = x.text("transaction_date")?.takeIf { Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(it) && !it.startsWith("1750") }.orEmpty()
                RichterGeschaeft(person, jahr, id, anlage, clArt(geschaeft), tag, x.text("transaction_value_code")?.trim().orEmpty())
            }
        }
    }.getOrDefault(emptyList())

    fun clNameLesen(json: String): String? = runCatching {
        val o = Json.parseToJsonElement(json) as? JsonObject ?: return null
        listOf("name_first", "name_middle", "name_last", "name_suffix").mapNotNull { o.text(it)?.trim()?.takeIf { s -> s.isNotEmpty() } }
            .joinToString(" ").takeIf { it.isNotEmpty() }
    }.getOrNull()

    // Wertspanne eines Codes in Dollar (von, bis; bis null: offen nach oben). Null bei unbekanntem Code.
    fun clSpanne(code: String): Pair<Long, Long?>? = when (code.trim().uppercase()) {
        "J" -> 1L to 15_000L
        "K" -> 15_001L to 50_000L
        "L" -> 50_001L to 100_000L
        "M" -> 100_001L to 250_000L
        "N" -> 250_001L to 500_000L
        "O" -> 500_001L to 1_000_000L
        "P1" -> 1_000_001L to 5_000_000L
        "P2" -> 5_000_001L to 25_000_000L
        "P3" -> 25_000_001L to 50_000_000L
        "P4" -> 50_000_001L to null
        else -> null
    }

    // ------------------------------------------------------------ Bluesky

    // Die Suche bei Bluesky verlangt eine Anmeldung. Fuer Drittanwendungen gibt Bluesky App-Passwoerter aus (Einstellungen,
    // Datenschutz und Sicherheit, App-Passwoerter); das Hauptpasswort nimmt die App nie an. App-Passwoerter haben immer
    // die Form xxxx-xxxx-xxxx-xxxx.
    const val BSKY_ANMELDUNG = "https://bsky.social/xrpc/com.atproto.server.createSession"
    const val BSKY_PASSWOERTER = "https://bsky.app/settings/app-passwords"

    fun appPasswortGueltig(s: String): Boolean = Regex("^[a-z0-9]{4}-[a-z0-9]{4}-[a-z0-9]{4}-[a-z0-9]{4}$").matches(s.trim())

    // Handle („name.bsky.social“, eigene Domain) oder DID; ohne fuehrendes @, keine E-Mail-Adresse.
    fun handleBereinigt(s: String): String? {
        val t = s.trim().removePrefix("@").lowercase()
        if (t.isEmpty() || t.contains('@') || t.length > 253) return null
        return t.takeIf {
            Regex("^did:plc:[a-z2-7]{24}$").matches(it) ||
                Regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$").matches(it)
        }
    }

    // Sitzung: Zugriffstoken, Erneuerungstoken und der Server (PDS), ueber den die Abrufe laufen.
    data class BskySitzung(val zugriff: String, val erneuern: String, val pds: String)

    fun sitzungLesen(json: String): BskySitzung? = runCatching {
        val o = Json.parseToJsonElement(json) as? JsonObject ?: return null
        val zugriff = o.text("accessJwt")?.takeIf { it.isNotBlank() } ?: return null
        val pds = ((o["didDoc"] as? JsonObject)?.get("service") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .firstOrNull { it.text("id")?.endsWith("#atproto_pds") == true }?.text("serviceEndpoint")
            ?.trimEnd('/')?.takeIf { Regex("^https://[a-z0-9.-]+(:\\d+)?$").matches(it) }
            ?: "https://bsky.social"
        BskySitzung(zugriff, o.text("refreshJwt").orEmpty(), pds)
    }.getOrNull()

    fun suchAdresse(pds: String, begriff: String, seit: Instant, bis: Instant, cursor: String? = null): String =
        "$pds/xrpc/app.bsky.feed.searchPosts?q=" + java.net.URLEncoder.encode("\"$begriff\"", "UTF-8") +
            "&sort=latest&limit=100&since=$seit&until=$bis" + (cursor?.let { "&cursor=" + java.net.URLEncoder.encode(it, "UTF-8") } ?: "")

    // Eine Seite der Suche: Zahl der Beitraege und der Zeiger auf die naechste Seite.
    data class Suchseite(val beitraege: Int, val cursor: String?)

    fun suchseiteLesen(json: String): Suchseite? = runCatching {
        val o = Json.parseToJsonElement(json) as? JsonObject ?: return null
        val posts = o["posts"] as? JsonArray ?: return null
        Suchseite(posts.size, o.text("cursor")?.takeIf { it.isNotBlank() && posts.isNotEmpty() })
    }.getOrNull()

    // Beitraege in den letzten sieben Tagen und in den sieben Tagen davor. gekappt: mehr als gezaehlt (die App liest
    // hoechstens drei Seiten je Woche).
    data class BlueskyWoche(val woche: Int, val vorwoche: Int, val gekappt: Boolean) {
        val veraenderung: Double? get() = if (vorwoche > 0 && !gekappt) (woche.toDouble() / vorwoche - 1) * 100 else null
    }
}
