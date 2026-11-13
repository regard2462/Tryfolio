package de.troetenrepublik.app.market

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate

// Boersengaenge (5.94): angekuendigte, eingereichte, festgesetzte und zurueckgezogene Angebote. Seit 5.95 vor allem
// aus dem Kalender der Nasdaq (NasdaqKalender.ipoLesen, schluesselfrei), mit dem Schluessel des Nutzers ergaenzt um
// Finnhub. Finnhub erlaubt die Anzeige fuer den Nutzer selbst, nicht die Weitergabe; die App zeigt die Daten nur auf
// dem Geraet. Ohne beides bleiben die Prospekte aus dem Tagesindex der SEC (SecIpo).
object IpoKalender {

    fun finnhubAdresse(von: LocalDate, bis: LocalDate, schluessel: String) =
        "https://finnhub.io/api/v1/calendar/ipo?from=$von&to=$bis&token=" + java.net.URLEncoder.encode(schluessel, "UTF-8")
    const val SEITE = "https://finnhub.io/docs/api/ipo-calendar"

    // Prospekte einer Firma bei der SEC fuer Menschen (ohne Kontaktadresse nur als Link).
    fun edgarSeite(cik: String) = "https://www.sec.gov/cgi-bin/browse-edgar?action=getcompany&CIK=$cik&type=&dateb=&owner=include&count=40"

    // Volltextsuche der SEC nach dem Firmennamen in Prospekten, fuer Angebote ohne bekannte CIK.
    fun edgarSuche(name: String) = "https://www.sec.gov/edgar/search/#/q=" +
        java.net.URLEncoder.encode("\"" + name.trim() + "\"", "UTF-8").replace("+", "%20") + "&forms=S-1,F-1,424B4"

    // Firmensuche der SEC nach dem Namen (Atom), fuer Angebote ohne Kuerzel.
    fun firmenSuche(name: String) = "https://www.sec.gov/cgi-bin/browse-edgar?action=getcompany&company=" +
        java.net.URLEncoder.encode(name.trim(), "UTF-8") + "&type=&dateb=&owner=include&count=10&output=atom"

    private val SUCHE_CIK = Regex("<cik>\\s*(\\d+)\\s*</cik>")
    private val SUCHE_NAME = Regex("<conformed-name>([^<]+)</conformed-name>")

    // CIK aus der Antwort der Firmensuche: nur wenn sie genau eine Firma nennt und deren Name passt (gleich: Vergleich
    // zweier Firmennamen). Bei mehreren Treffern nennt die Antwort keine Namen, dann null.
    fun cikAusSuche(atom: String, gleich: (String) -> Boolean): String? {
        val name = SUCHE_NAME.findAll(atom).map { it.groupValues[1].trim() }.toList().singleOrNull() ?: return null
        if (!gleich(name.replace("&amp;", "&"))) return null
        return SUCHE_CIK.findAll(atom).map { it.groupValues[1].trimStart('0') }.distinct().toList().singleOrNull()?.takeIf { it.isNotEmpty() }
    }

    enum class Status { ANGEKUENDIGT, EINGEREICHT, PREIS, ZURUECK }

    // Ein Angebot im Kalender. deal: Schluessel aus dem Namen, preis: Text der Quelle (Einzelpreis oder Spanne
    // „14.00-16.00“), volumen in Dollar, tag: Tag seit 1970 (erwartet, eingereicht, festgesetzt oder zurueckgezogen).
    data class Angebot(
        val deal: String,
        val symbol: String?,
        val name: String,
        val boerse: String?,
        val preis: String?,
        val aktien: Long?,
        val volumen: Double?,
        val tag: Long?,
        val status: Status,
    ) {
        // Mantelgesellschaft: Name mit „Acquisition“, Einheiten mit fuenfstelligem Kuerzel auf U (auch eingereicht, noch
        // ohne Preis) oder vierstelliges Kuerzel auf U zu 10 Dollar
        val spac: Boolean get() = Regex("(?i)\\bacquisitions?\\b|\\bSPAC\\b|\\bblank[ -]check\\b").containsMatchIn(name) ||
            symbol?.let { it.length >= 5 && it.endsWith("U") } == true ||
            (symbol?.let { it.length == 4 && it.endsWith("U") } == true && preisZahl == 10.0)

        // Mitte der Spanne oder der Einzelpreis
        val preisZahl: Double? get() = preis?.split('-', '–')?.mapNotNull { zahl(it) }?.takeIf { it.isNotEmpty() }?.average()
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun zahl(s: String): Double? = s.replace(Regex("[$,\\s]"), "").removeSuffix("USD").toDoubleOrNull()?.takeIf { it.isFinite() }

    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim()?.takeIf { it.isNotEmpty() && it != "--" }
    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject

    private fun status(s: String?): Status? = when (s?.lowercase()) {
        "expected" -> Status.ANGEKUENDIGT
        "filed" -> Status.EINGEREICHT
        "priced" -> Status.PREIS
        "withdrawn" -> Status.ZURUECK
        else -> null
    }

    // Antwort von /calendar/ipo; null, wenn sie nicht lesbar ist (Fehlermeldung, Sperre), leer ohne Eintraege.
    fun finnhubLesen(text: String): List<Angebot>? = runCatching {
        val o = json.parseToJsonElement(text).obj() ?: return null
        if (!o.containsKey("ipoCalendar")) return null
        val zeilen = (o["ipoCalendar"] as? JsonArray).orEmpty().mapNotNull { it.obj() }
        zusammen(zeilen.mapNotNull { z ->
            val name = z["name"].text() ?: return@mapNotNull null
            val st = status(z["status"].text()) ?: return@mapNotNull null
            Angebot(
                deal = name.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim(),
                symbol = z["symbol"].text()?.uppercase(),
                name = name,
                boerse = z["exchange"].text(),
                preis = z["price"].text(),
                aktien = z["numberOfShares"].text()?.let(::zahl)?.toLong()?.takeIf { it > 0 },
                volumen = z["totalSharesValue"].text()?.let(::zahl)?.takeIf { it > 0 },
                tag = z["date"].text()?.let { runCatching { LocalDate.parse(it).toEpochDay() }.getOrNull() },
                status = st,
            )
        })
    }.getOrNull()

    // Je Angebot der neueste Stand (festgesetzt vor zurueckgezogen vor angekuendigt vor eingereicht).
    fun zusammen(liste: List<Angebot>): List<Angebot> {
        val rang = mapOf(Status.PREIS to 3, Status.ZURUECK to 2, Status.ANGEKUENDIGT to 1, Status.EINGEREICHT to 0)
        return liste.groupBy { it.deal }.values.map { g ->
            g.maxWith(compareBy<Angebot> { rang.getValue(it.status) }.thenBy { it.tag ?: 0L })
        }
    }

    // ------------------------------------------------------------ Prospekte bei der SEC

    // Eine Einreichung: Formular, Tag (JJJJ-MM-TT) und Link aufs Dokument.
    data class Dokument(val formular: String, val tag: String, val link: String)

    // Formulare rund um den Boersengang: Prospekt und Aenderungen, endgueltiger Prospekt, Wirksamkeit, Ruecknahme.
    val FORMULARE = setOf("S-1", "S-1/A", "F-1", "F-1/A", "424B4", "424B1", "EFFECT", "RW", "S-1MEF")

    fun submissions(cik: String) = "https://data.sec.gov/submissions/CIK" + cik.padStart(10, '0') + ".json"

    fun dokumenteLesen(cik: String, text: String): List<Dokument> = runCatching {
        val r = json.parseToJsonElement(text).obj()?.get("filings").obj()?.get("recent").obj() ?: return emptyList()
        fun spalte(k: String) = (r[k] as? JsonArray).orEmpty().map { it.text().orEmpty() }
        val form = spalte("form")
        val tag = spalte("filingDate")
        val nr = spalte("accessionNumber")
        val datei = spalte("primaryDocument")
        form.indices.mapNotNull { i ->
            val f = form[i]
            if (f !in FORMULARE) return@mapNotNull null
            val n = nr.getOrNull(i)?.replace("-", "")?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val d = datei.getOrNull(i).orEmpty()
            val link = if (d.isEmpty()) "https://www.sec.gov/Archives/edgar/data/$cik/$n/" else "https://www.sec.gov/Archives/edgar/data/$cik/$n/$d"
            Dokument(f, tag.getOrNull(i).orEmpty(), link)
        }.sortedByDescending { it.tag }
    }.getOrDefault(emptyList())
}
