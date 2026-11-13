package de.troetenrepublik.app.market

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.DayOfWeek
import java.time.LocalDate

// Quartalszahlen der Woche (5.94). Seit 5.95 schluesselfrei von der Nasdaq (NasdaqKalender.zahlenLesen, je Tag ein
// Abruf, mit Boersenwert, Schaetzungen und Vorjahr), mit dem Schluessel des Nutzers ergaenzt um Finnhub
// (/calendar/earnings, je Woche ein Abruf, mit Zeitpunkt, erwartetem und tatsaechlichem Gewinn je Aktie und Umsatz),
// ersatzweise Alpha Vantage (EARNINGS_CALENDAR, drei Monate voraus in einer CSV, nur kommende Termine). Depot und
// Merkliste ausserhalb der USA kommen mit dem Termin aus dem Kursabruf dazu (eigene).
object Quartalszahlen {

    const val FINNHUB = "finnhub"
    const val ALPHAVANTAGE = "alphavantage"

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
    fun finnhubAdresse(von: LocalDate, bis: LocalDate, schluessel: String) =
        "https://finnhub.io/api/v1/calendar/earnings?from=$von&to=$bis&token=" + enc(schluessel)
    fun alphaAdresse(schluessel: String) =
        "https://www.alphavantage.co/query?function=EARNINGS_CALENDAR&horizon=3month&apikey=" + enc(schluessel)

    // Seiten der Quellen fuer Menschen
    fun seite(anbieter: String?) = if (anbieter == ALPHAVANTAGE) "https://www.alphavantage.co/documentation/#earnings-calendar"
    else "https://finnhub.io/docs/api/earnings-calendar"

    // Anbieter nach den hinterlegten Schluesseln: Finnhub vor Alpha Vantage, ohne Schluessel null.
    fun anbieter(finnhub: String, alpha: String): String? = when {
        finnhub.isNotBlank() -> FINNHUB
        alpha.isNotBlank() -> ALPHAVANTAGE
        else -> null
    }

    // Bekannt ist ein Wert ab diesem erwarteten Quartalsumsatz in Dollar (etwa vier Milliarden im Jahr) oder ab
    // BEKANNT_WERT Boersenwert, ausserdem alles aus dem Verzeichnis der App; Depot und Merkliste stehen immer da.
    const val BEKANNT_AB = 1e9
    const val BEKANNT_WERT = 10e9
    // Hoechstens so viele bekannte Werte je Tag und Zeitpunkt, die groessten zuerst.
    const val JE_GRUPPE = 8

    enum class Zeit { VOR, HANDEL, NACH, OFFEN }

    // Ein Termin. tag: Tag seit 1970 (Datum in New York bzw. am Heimatplatz), wert: Boersenwert in Dollar,
    // eps*: Gewinn je Aktie in Dollar, abweichung: Ist gegen Erwartung in Prozent, umsatz: erwarteter Quartalsumsatz
    // in Dollar, eigen: aus Depot oder Merkliste. zeitpunkt: genauer Zeitpunkt der Veroeffentlichung (ms seit 1970),
    // nur wenn die Quelle eine Uhrzeit nennt; geschaetzt: die Quelle nennt den Tag nur als Schaetzung (5.97).
    data class Termin(
        val symbol: String,
        val name: String,
        val tag: Long,
        val zeit: Zeit,
        val wert: Double? = null,
        val epsErwartet: Double? = null,
        val schaetzungen: Int? = null,
        val epsVorjahr: Double? = null,
        val eps: Double? = null,
        val abweichung: Double? = null,
        val quartal: String? = null,
        val eigen: Boolean = false,
        val umsatz: Double? = null,
        val zeitpunkt: Long? = null,
        val geschaetzt: Boolean = false,
        // 0.76: gemeldeter Umsatz in Dollar (nur Finnhub), fuer die Ueberraschung gegen umsatz
        val umsatzIst: Double? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim()
        ?.takeIf { it.isNotEmpty() && it != "--" && it != "N/A" && it != "None" }

    // Zahl aus „1234.5“, „-5.97“ oder „($0.12)“ (Klammern: negativ); null bei „--“ oder leer.
    fun zahl(s: String?): Double? {
        val t = s?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val negativ = t.startsWith("(") && t.endsWith(")")
        val v = t.removePrefix("(").removeSuffix(")").replace(Regex("[$,\\s]"), "").toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
        return if (negativ) -v else v
    }

    // US-Kuerzel in der Schreibweise der Kursquelle: „BRK.B“ und „BRK/B“ werden „BRK-B“.
    fun symbol(us: String): String = us.trim().uppercase().replace('.', '-').replace('/', '-')

    private val RECHTSFORM = Regex(
        "(?i)[,\\s]+(inc|incorporated|corp|corporation|co|company|ltd|limited|plc|n\\.?v|s\\.?a\\.?b\\.?( de c\\.?v)?|s\\.?a|s\\.?e|ag|se|a/s|lp|llc|" +
            "class [a-c]|common stock|ordinary shares|american depositary shares|ads)\\.?$",
    )

    // Firmenname ohne Rechtsform am Ende („Advanced Micro Devices, Inc.“ wird „Advanced Micro Devices“). Endet der
    // Rest auf „&“ oder „and“, gehoerte das Wort zum Namen („Merck & Co.“, „Eli Lilly and Company“).
    fun name(n: String): String {
        var s = n.trim()
        while (true) {
            val k = s.replace(RECHTSFORM, "").trim().trimEnd(',')
            if (k == s || k.length < 2 || Regex("(?i)(&|\\band|\\bund)$").containsMatchIn(k)) return s
            s = k
        }
    }

    // Antwort von Finnhub fuer einen Zeitraum; null, wenn sie nicht lesbar ist (Fehlermeldung, Sperre). Finnhub nennt
    // keinen Namen: der kommt aus dem Verzeichnis der App, sonst steht das Kuerzel da.
    fun finnhubLesen(text: String): List<Termin>? = runCatching {
        val o = json.parseToJsonElement(text) as? JsonObject ?: return null
        if (!o.containsKey("earningsCalendar")) return null
        (o["earningsCalendar"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { z ->
            val s = symbol(z["symbol"].text() ?: return@mapNotNull null)
            val tag = z["date"].text()?.let { runCatching { LocalDate.parse(it).toEpochDay() }.getOrNull() } ?: return@mapNotNull null
            val q = z["quarter"].text()?.toDoubleOrNull()?.toInt()
            val j = z["year"].text()?.toDoubleOrNull()?.toInt()
            Termin(
                symbol = s,
                name = Symbols.find(s)?.name ?: s,
                tag = tag,
                zeit = when (z["hour"].text()?.lowercase()) {
                    "bmo" -> Zeit.VOR
                    "amc" -> Zeit.NACH
                    "dmh" -> Zeit.HANDEL
                    else -> Zeit.OFFEN
                },
                epsErwartet = zahl(z["epsEstimate"].text()),
                eps = zahl(z["epsActual"].text()),
                quartal = if (q != null && j != null) "Q$q $j" else null,
                umsatz = zahl(z["revenueEstimate"].text())?.takeIf { it > 0 },
                umsatzIst = zahl(z["revenueActual"].text())?.takeIf { it > 0 },
            )
        }.distinctBy { it.symbol to it.tag }
    }.getOrNull()

    // Eine CSV-Zeile mit Feldern in Anfuehrungszeichen (Kommas im Namen).
    private fun felder(z: String): List<String> {
        val aus = ArrayList<String>()
        val f = StringBuilder()
        var inZitat = false
        var i = 0
        while (i < z.length) {
            val c = z[i]
            when {
                c == '"' && inZitat && i + 1 < z.length && z[i + 1] == '"' -> { f.append('"'); i++ }
                c == '"' -> inZitat = !inZitat
                c == ',' && !inZitat -> { aus += f.toString(); f.clear() }
                else -> f.append(c)
            }
            i++
        }
        aus += f.toString()
        return aus.map { it.trim() }
    }

    // CSV von Alpha Vantage (symbol, name, reportDate, fiscalDateEnding, estimate, currency, timeOfTheDay); null, wenn
    // statt der CSV eine Meldung kommt (etwa das Tageskontingent). Gewinn je Aktie nur in Dollar.
    fun alphaLesen(csv: String): List<Termin>? {
        val z = csv.trimStart('\uFEFF').lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val kopf = z.firstOrNull()?.let(::felder)?.map { it.lowercase() } ?: return null
        val sp = { k: String -> kopf.indexOf(k) }
        if (sp("symbol") < 0 || sp("reportdate") < 0) return null
        return z.drop(1).mapNotNull { zeile ->
            val f = felder(zeile)
            fun w(k: String) = f.getOrNull(sp(k))?.takeIf { it.isNotEmpty() }
            val s = symbol(w("symbol") ?: return@mapNotNull null)
            val tag = w("reportdate")?.let { runCatching { LocalDate.parse(it).toEpochDay() }.getOrNull() } ?: return@mapNotNull null
            val usd = w("currency")?.equals("USD", true) != false
            Termin(
                symbol = s,
                name = name(w("name") ?: s),
                tag = tag,
                zeit = when (w("timeoftheday")?.lowercase()) {
                    "pre-market" -> Zeit.VOR
                    "post-market" -> Zeit.NACH
                    else -> Zeit.OFFEN
                },
                epsErwartet = if (usd) zahl(w("estimate")) else null,
                quartal = w("fiscaldateending"),
            )
        }.distinctBy { it.symbol to it.tag }
    }

    private val GATTUNG = Regex("\\s*\\(([A-C]|class [A-C])\\)$", RegexOption.IGNORE_CASE)

    // Name ohne Kennzeichen der Aktiengattung am Ende („Fox (B)“ wird „Fox“).
    fun ohneGattung(name: String): String = name.replace(GATTUNG, "").trim()

    // Mehrere Gattungen einer Firma (FOX und FOXA, GOOG und GOOGL) melden dieselben Zahlen: gleicher Name und gleicher
    // Boersenwert (bei Finnhub gleicher erwarteter Umsatz) am selben Tag zaehlen einmal. Behalten wird die Gattung aus
    // Depot oder Merkliste, sonst die mit den meisten Schaetzungen, sonst das kuerzere Kuerzel. Ohne diese Zahl oder
    // bei anderem Wert bleiben gleichnamige Firmen getrennt. Erst nach dem Markieren der eigenen Werte aufrufen.
    fun ohneDoppelteGattungen(liste: List<Termin>): List<Termin> =
        liste.groupBy { t ->
            val g = t.wert ?: t.umsatz
            if (g == null) t.symbol else ohneGattung(t.name).lowercase() + "|" + Math.round(g / 1e6)
        }
            .values.map { g -> g.maxWith(compareBy<Termin> { it.eigen }.thenBy { it.schaetzungen ?: 0 }.thenByDescending { it.symbol.length }) }

    // Handelstage Montag bis Freitag einer Woche. versatz 0 ist die laufende Woche, am Wochenende die kommende.
    fun woche(heute: LocalDate, versatz: Int): List<LocalDate> {
        val basis = if (heute.dayOfWeek >= DayOfWeek.SATURDAY) heute.plusDays(8L - heute.dayOfWeek.value) else heute
        val montag = basis.minusDays(basis.dayOfWeek.value - 1L).plusWeeks(versatz.toLong())
        return (0L..4L).map { montag.plusDays(it) }
    }

    // Heimatplatz nach der Endung des Kuerzels: Zeitzone und Hauptsitzung. Deutsche Plaetze mit der Xetra-Sitzung,
    // ohne Endung die USA; unbekannte Endungen null.
    private val ENDUNG = mapOf(
        "" to "US", "DE" to "DE", "F" to "DE", "BE" to "DE", "MU" to "DE", "SG" to "DE", "DU" to "DE", "HM" to "DE", "HA" to "DE",
        "VI" to "AT", "SW" to "CH", "L" to "GB", "IL" to "GB", "PA" to "FR", "AS" to "NL", "BR" to "BE", "MI" to "IT", "MC" to "ES",
        "LS" to "PT", "IR" to "IE", "ST" to "SE", "CO" to "DK", "OL" to "NO", "HE" to "FI", "WA" to "PL", "PR" to "CZ", "BD" to "HU",
        "TO" to "CA", "V" to "CA", "NE" to "CA", "MX" to "MX", "SA" to "BR", "T" to "JP", "SS" to "CN", "SZ" to "CN", "HK" to "HK",
        "TW" to "TW", "TWO" to "TW", "KS" to "KR", "KQ" to "KR", "NS" to "IN", "BO" to "IN", "SI" to "SG", "TA" to "IL", "JO" to "ZA",
        "AX" to "AU", "NZ" to "NZ",
    )

    data class Platz(val zone: java.time.ZoneId, val oeffnet: java.time.LocalTime, val schliesst: java.time.LocalTime)

    fun platz(symbol: String): Platz? {
        val endung = symbol.substringAfterLast('.', "").uppercase()
        val code = ENDUNG[endung] ?: return null
        if (code == "DE") return Platz(java.time.ZoneId.of("Europe/Berlin"), java.time.LocalTime.of(9, 0), java.time.LocalTime.of(17, 30))
        val l = de.troetenrepublik.app.engine.Regionen.land(code).takeIf { it.code == code } ?: return null
        return Platz(l.zoneId, l.oeffnet, l.schliesst)
    }

    // Zeitpunkt aus der Uhrzeit am Heimatplatz gegen dessen Hauptsitzung: davor vor Boersenstart, ab Schluss nach
    // Boersenschluss, dazwischen waehrend des Handels. Genau Mitternacht heisst bei der Quelle meist „Uhrzeit unbekannt“.
    fun zeitAus(ort: java.time.LocalTime, oeffnet: java.time.LocalTime, schliesst: java.time.LocalTime): Zeit = when {
        ort == java.time.LocalTime.MIDNIGHT -> Zeit.OFFEN
        ort < oeffnet -> Zeit.VOR
        ort >= schliesst -> Zeit.NACH
        else -> Zeit.HANDEL
    }

    private fun gross(t: Termin) = (t.umsatz ?: 0.0) >= BEKANNT_AB || (t.wert ?: 0.0) >= BEKANNT_WERT || Symbols.find(t.symbol) != null

    // Die bekannten Werte eines Tages: eigene immer, sonst die grossen (gross), je Zeitpunkt die groessten JE_GRUPPE.
    fun bekannte(liste: List<Termin>): List<Termin> =
        liste.groupBy { it.zeit }.values.flatMap { g ->
            val eigene = g.filter { it.eigen }
            eigene + ordnen(g.filter { !it.eigen && gross(it) }).take((JE_GRUPPE - eigene.size).coerceAtLeast(0))
        }.let(::ordnen)

    // Reihenfolge innerhalb eines Tages: nach Zeitpunkt, darin eigene zuerst, dann nach Boersenwert und erwartetem Umsatz.
    fun ordnen(liste: List<Termin>): List<Termin> =
        liste.sortedWith(
            compareBy<Termin> { it.zeit.ordinal }.thenByDescending { it.eigen }.thenByDescending { it.wert ?: -1.0 }.thenByDescending { it.umsatz ?: -1.0 },
        )

    // Vorschau eines Tages (0.68): aus allen oder den bekannten Werten die anzahl groessten (eigene zuerst), nach Zeitpunkt geordnet
    fun vorschau(liste: List<Termin>, alle: Boolean, anzahl: Int = 5): List<Termin> =
        ordnen((if (alle) liste else bekannte(liste)).sortedWith(compareByDescending<Termin> { it.eigen }.thenByDescending { it.wert ?: -1.0 }).take(anzahl))
}
