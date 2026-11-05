package de.troetenrepublik.app.market

import de.troetenrepublik.app.engine.Regionen
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt

// Schluesselfreie Kalender der Nasdaq (5.95) fuer Boersengaenge, Quartalszahlen, Dividenden, Splits und
// Wirtschaftstermine. Geholt wird erst beim Oeffnen der Ansicht, Zwischenspeicher und Pause nach Fehlern liegen in
// NasdaqAbruf. Hier nur reine Funktionen, alles ohne Netz pruefbar.
object NasdaqKalender {

    // Abrufe mit MarketApi.BROWSER_UA.
    val KOPF: Map<String, String> = mapOf("User-Agent" to MarketApi.BROWSER_UA, "Accept" to "application/json, text/plain, */*")

    // Seiten der Quelle fuer Menschen
    const val SEITE_IPO = "https://www.nasdaq.com/market-activity/ipos"
    const val SEITE_ZAHLEN = "https://www.nasdaq.com/market-activity/earnings"
    const val SEITE_DIVIDENDEN = "https://www.nasdaq.com/market-activity/dividends"
    const val SEITE_SPLITS = "https://www.nasdaq.com/market-activity/stock-splits"
    const val SEITE_WIRTSCHAFT = "https://www.nasdaq.com/market-activity/economic-calendar"

    // ------------------------------------------------------------ Adressen und Hilfen

    // Datum zweistellig selbst zusammensetzen (kein String.format, auch fuer iOS)
    private fun z2(n: Int) = n.toString().padStart(2, '0')
    private fun tagText(t: LocalDate) = "${t.year}-${z2(t.monthValue)}-${z2(t.dayOfMonth)}"

    fun ipoAdresse(monat: LocalDate) = "https://api.nasdaq.com/api/ipo/calendar?date=${monat.year}-${z2(monat.monthValue)}"
    fun zahlenAdresse(tag: LocalDate) = "https://api.nasdaq.com/api/calendar/earnings?date=" + tagText(tag)
    fun dividendenAdresse(tag: LocalDate) = "https://api.nasdaq.com/api/calendar/dividends?date=" + tagText(tag)
    fun splitsAdresse(tag: LocalDate) = "https://api.nasdaq.com/api/calendar/splits?date=" + tagText(tag)

    // Der Endpunkt liefert die Termine des Vortags, darum fuer Tag T mit T+1 abfragen
    fun wirtschaftAdresse(tag: LocalDate) = "https://api.nasdaq.com/api/calendar/economicevents?date=" + tagText(tag.plusDays(1))

    // Haltbarkeit der IPO-Monate und der Splits
    const val MONAT_HALTBAR_MS = 6 * 3_600_000L

    // Haltbarkeit einer Tagesantwort: 24 Stunden nur, wenn nach dem Ende des Tages in New York geholt (Beginn des
    // Folgetags), sonst sechs Stunden
    fun haltbarMs(tag: LocalDate, abgerufenMs: Long): Long =
        if (abgerufenMs >= tag.plusDays(1).atStartOfDay(ZoneId.of("America/New_York")).toInstant().toEpochMilli()) 24 * 3_600_000L else 6 * 3_600_000L

    private val json = Json { ignoreUnknownKeys = true }

    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject

    // data-Objekt einer Antwort; data null zaehlt nur als leer, wenn status.rCode fehlt oder in 200..299
    // liegt (gueltiges JSON ohne Daten, etwa Feiertage), mit Fehler-rCode als null (Fehlschlag);
    // auch null bei unlesbarem Text, Nicht-Objekt oder fehlendem Schluessel data
    private fun daten(text: String): JsonObject? {
        val wurzel = runCatching { json.parseToJsonElement(text) }.getOrNull().obj() ?: return null
        return when (val d = wurzel["data"]) {
            null -> null
            is JsonNull -> {
                val rc = (wurzel["status"].obj()?.get("rCode") as? JsonPrimitive)?.content?.toIntOrNull()
                if (rc != null && rc !in 200..299) null else JsonObject(emptyMap())
            }
            is JsonObject -> d
            else -> null
        }
    }

    // Text wie in IpoKalender, zusaetzlich leer bei „N/A“ und „&nbsp;“
    private fun JsonElement?.text(): String? =
        (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim()
            ?.takeIf { it.isNotEmpty() && it != "--" && it != "N/A" && it != "&nbsp;" }

    // Zahl aus einem Feld, das JSON-Zahl oder Text sein kann
    private fun JsonElement?.zahlText(): String? =
        (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim()
            ?.takeIf { it.isNotEmpty() && it != "N/A" && it != "--" }

    // Datum „11/02/2026“ oder „1/16/2026“ (Monat/Tag/Jahr)
    fun datum(s: String?): LocalDate? {
        val teile = s?.trim()?.split('/') ?: return null
        if (teile.size != 3) return null
        val jahr = teile[2].toIntOrNull() ?: return null
        val monat = teile[0].toIntOrNull() ?: return null
        val tag = teile[1].toIntOrNull() ?: return null
        return runCatching { LocalDate.of(jahr, monat, tag) }.getOrNull()
    }

    // HTML-Entitaeten aufloesen, &nbsp; als Leerzeichen; leer ergibt null
    fun entitaeten(s: String?): String? = s
        ?.replace("&nbsp;", " ")?.replace("&#039;", "'")?.replace("&quot;", "\"")
        ?.replace("&lt;", "<")?.replace("&gt;", ">")?.replace("&amp;", "&")
        ?.trim()?.takeIf { it.isNotEmpty() }

    // ------------------------------------------------------------ Boersengaenge

    // Ein Abschnitt des IPO-Kalenders; pfad fuehrt zum Objekt mit rows, tagFeld ist sein Datumsfeld
    private fun ipoZeilen(data: JsonObject, pfad: List<String>, status: IpoKalender.Status, tagFeld: String): List<IpoKalender.Angebot> {
        var knoten: JsonElement? = data
        for (p in pfad) knoten = (knoten as? JsonObject)?.get(p)
        val zeilen = ((knoten as? JsonObject)?.get("rows") as? JsonArray).orEmpty()
        return zeilen.mapNotNull { e ->
            val z = e.obj() ?: return@mapNotNull null
            val name = z["companyName"].text() ?: return@mapNotNull null
            IpoKalender.Angebot(
                deal = name.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim(),
                symbol = z["proposedTickerSymbol"].text()?.uppercase(),
                name = name,
                boerse = z["proposedExchange"].text(),
                preis = z["proposedSharePrice"].text(),
                aktien = z["sharesOffered"].text()?.let(IpoKalender::zahl)?.toLong()?.takeIf { it > 0 },
                volumen = z["dollarValueOfSharesOffered"].text()?.let(IpoKalender::zahl)?.takeIf { it > 0 },
                tag = datum(z[tagFeld].text())?.toEpochDay(),
                status = status,
            )
        }
    }

    // Monat des IPO-Kalenders: priced, upcoming, filed, withdrawn; data null ergibt eine leere Liste
    fun ipoLesen(text: String): List<IpoKalender.Angebot>? = runCatching {
        val d = daten(text) ?: return null
        IpoKalender.zusammen(
            ipoZeilen(d, listOf("priced"), IpoKalender.Status.PREIS, "pricedDate") +
                ipoZeilen(d, listOf("upcoming", "upcomingTable"), IpoKalender.Status.ANGEKUENDIGT, "expectedPriceDate") +
                ipoZeilen(d, listOf("filed"), IpoKalender.Status.EINGEREICHT, "filedDate") +
                ipoZeilen(d, listOf("withdrawn"), IpoKalender.Status.ZURUECK, "withdrawDate"),
        )
    }.getOrNull()

    // Nasdaq und Finnhub zusammen: je deal der beste Stand nach der Regel von IpoKalender.zusammen, fehlende Felder
    // aus Eintraegen desselben deal mit gleichem Status (Nasdaq zuerst). Finnhub-Eintraege mit Kuerzel der Nasdaq
    // uebernehmen deren deal, damit Rechtsform-Varianten des Namens zusammenfallen.
    fun ipoZusammen(nasdaq: List<IpoKalender.Angebot>?, finnhub: List<IpoKalender.Angebot>?): List<IpoKalender.Angebot>? {
        if (nasdaq == null && finnhub == null) return null
        val dealJeSymbol = nasdaq.orEmpty().filter { it.symbol != null }.associate { it.symbol to it.deal }
        val angepasst = finnhub?.map { f -> f.symbol?.let { dealJeSymbol[it] }?.let { d -> f.copy(deal = d) } ?: f }
        val alle = nasdaq.orEmpty() + angepasst.orEmpty()
        val gruppen = alle.groupBy { it.deal }
        return IpoKalender.zusammen(alle).map { best ->
            gruppen.getValue(best.deal).filter { it.status == best.status }.fold(best) { r, k ->
                r.copy(
                    symbol = r.symbol ?: k.symbol,
                    boerse = r.boerse ?: k.boerse,
                    preis = r.preis ?: k.preis,
                    aktien = r.aktien ?: k.aktien,
                    volumen = r.volumen ?: k.volumen,
                )
            }
        }
    }

    // ------------------------------------------------------------ Quartalszahlen

    // Ein Tag des Ergebniskalenders; data null oder rows null (Wochenende) ergibt eine leere Liste
    fun zahlenLesen(text: String, tag: LocalDate): List<Quartalszahlen.Termin>? = runCatching {
        val d = daten(text) ?: return null
        val zeilen = (d["rows"] as? JsonArray).orEmpty()
        zeilen.mapNotNull { e ->
            val z = e.obj() ?: return@mapNotNull null
            val symbol = Quartalszahlen.symbol(z["symbol"].text() ?: return@mapNotNull null)
            Quartalszahlen.Termin(
                symbol = symbol,
                name = z["name"].text()?.let { Quartalszahlen.name(it) } ?: symbol,
                tag = tag.toEpochDay(),
                zeit = when (z["time"].text()) {
                    "time-pre-market" -> Quartalszahlen.Zeit.VOR
                    "time-after-hours" -> Quartalszahlen.Zeit.NACH
                    else -> Quartalszahlen.Zeit.OFFEN
                },
                wert = z["marketCap"].text()?.let(Quartalszahlen::zahl)?.takeIf { it > 0 },
                epsErwartet = z["epsForecast"].text()?.let(Quartalszahlen::zahl),
                schaetzungen = z["noOfEsts"].text()?.toIntOrNull(),
                epsVorjahr = z["lastYearEPS"].text()?.let(Quartalszahlen::zahl),
                eps = z["eps"].text()?.let(Quartalszahlen::zahl),
                abweichung = z["surprise"].text()?.let(Quartalszahlen::zahl),
                quartal = z["fiscalQuarterEnding"].text(),
            )
        }.distinctBy { it.symbol }
    }.getOrNull()

    // Nasdaq als Grundlage, fehlende Felder aus dem anderen (Zeitpunkt nur, wenn Nasdaq OFFEN hat), dazu die
    // Termine, die nur der andere kennt
    fun zahlenMischen(nasdaq: List<Quartalszahlen.Termin>?, andere: List<Quartalszahlen.Termin>?): List<Quartalszahlen.Termin>? {
        if (nasdaq == null) return andere
        if (andere == null) return nasdaq
        val eigene = nasdaq.map { it.symbol }.toHashSet()
        val vonAndere = andere.associateBy { it.symbol }
        return nasdaq.map { n ->
            val a = vonAndere[n.symbol] ?: return@map n
            n.copy(
                zeit = if (n.zeit == Quartalszahlen.Zeit.OFFEN) a.zeit else n.zeit,
                wert = n.wert ?: a.wert,
                epsErwartet = n.epsErwartet ?: a.epsErwartet,
                schaetzungen = n.schaetzungen ?: a.schaetzungen,
                epsVorjahr = n.epsVorjahr ?: a.epsVorjahr,
                eps = n.eps ?: a.eps,
                abweichung = n.abweichung ?: a.abweichung,
                quartal = n.quartal ?: a.quartal,
                umsatz = n.umsatz ?: a.umsatz,
            )
        } + andere.filter { it.symbol !in eigene }
    }

    // ------------------------------------------------------------ Dividenden

    // Eine Dividende; Tage seit 1970, betrag und jeJahr in Dollar je Aktie
    data class Dividende(
        val symbol: String,
        val name: String,
        val exTag: Long,
        val zahltag: Long?,
        val stichtag: Long?,
        val betrag: Double?,
        val jeJahr: Double?,
        val angekuendigt: Long?,
        val eigen: Boolean = false,
    )

    // Firmenname ohne „Common Stock“ u. ae. am Ende (doppelt schadet nicht)
    private fun dividendenName(n: String): String {
        val ende = Regex("(?i)[,\\s]+(common stock|ordinary shares|american depositary shares|depositary shares)$")
        var s = Quartalszahlen.name(n)
        while (true) {
            val k = s.replace(ende, "").trim()
            if (k == s) return s
            s = k
        }
    }

    // Ex-Tag-Kalender eines Tages; ohne symbol oder Ex-Tag weg, gleiche Dividende nur einmal; data null ergibt
    // eine leere Liste
    fun dividendenLesen(text: String): List<Dividende>? = runCatching {
        val d = daten(text) ?: return null
        val zeilen = (d["calendar"].obj()?.get("rows") as? JsonArray).orEmpty()
        zeilen.mapNotNull { e ->
            val z = e.obj() ?: return@mapNotNull null
            val symbol = Quartalszahlen.symbol(z["symbol"].text() ?: return@mapNotNull null)
            Dividende(
                symbol = symbol,
                name = z["companyName"].text()?.let { dividendenName(it) } ?: symbol,
                exTag = datum(z["dividend_Ex_Date"].text())?.toEpochDay() ?: return@mapNotNull null,
                zahltag = datum(z["payment_Date"].text())?.toEpochDay(),
                stichtag = datum(z["record_Date"].text())?.toEpochDay(),
                betrag = z["dividend_Rate"].zahlText()?.let(Quartalszahlen::zahl)?.takeIf { it > 0 },
                jeJahr = z["indicated_Annual_Dividend"].zahlText()?.let(Quartalszahlen::zahl)?.takeIf { it > 0 },
                angekuendigt = datum(z["announcement_Date"].text())?.toEpochDay(),
            )
        }.distinctBy { it.symbol to it.exTag }
    }.getOrNull()

    // Eigene Dividenden und Werte aus dem Verzeichnis der App, geordnet
    fun dividendenBekannte(liste: List<Dividende>): List<Dividende> =
        dividendenOrdnen(liste.filter { it.eigen || Symbols.find(it.symbol) != null })

    // eigene zuerst, dann Werte im Verzeichnis, dann Name
    fun dividendenOrdnen(liste: List<Dividende>): List<Dividende> = liste.sortedWith(
        compareBy<Dividende> { !it.eigen }.thenBy { Symbols.find(it.symbol) == null }.thenBy { it.name.lowercase() },
    )

    // Vorschau eines Tages (0.68): die ersten anzahl Eintraege der gewaehlten Liste, eigene zuerst
    fun dividendenVorschau(liste: List<Dividende>, alle: Boolean, anzahl: Int = 5): List<Dividende> =
        (if (alle) dividendenOrdnen(liste) else dividendenBekannte(liste)).take(anzahl)

    // ------------------------------------------------------------ Splits

    // Ein Split; neu und alt als kleinste ganze Zahlen (neue : alte Aktien)
    data class Split(val symbol: String, val name: String, val tag: Long, val neu: Int, val alt: Int) {
        val zusammenlegung: Boolean get() = neu < alt
        val verhaeltnis: String get() = "$neu:$alt"
    }

    private fun ggt(a: Int, b: Int): Int {
        var x = a
        var y = b
        while (y != 0) { val r = x % y; x = y; y = r }
        return x
    }

    // „8:1“, „4 : 1“ oder „1.5 : 1“ (wird 3:2); ohne gueltiges Verhaeltnis null
    private fun verhaeltnis(r: String?): Pair<Int, Int>? {
        val m = Regex("^(\\d+(?:\\.\\d{1,3})?)\\s*:\\s*(\\d+(?:\\.\\d{1,3})?)$").find(r?.trim() ?: return null) ?: return null
        val a = m.groupValues[1]
        val b = m.groupValues[2]
        val stellen = maxOf(a.substringAfter('.', "").length, b.substringAfter('.', "").length)
        val f = when (stellen) { 1 -> 10.0; 2 -> 100.0; 3 -> 1000.0; else -> 1.0 }
        val neu = (a.toDoubleOrNull() ?: return null) * f
        val alt = (b.toDoubleOrNull() ?: return null) * f
        if (neu <= 0.0 || alt <= 0.0) return null
        val n = neu.roundToInt()
        val g = alt.roundToInt()
        if (n <= 0 || g <= 0) return null
        val t = ggt(n, g)
        return if (t > 1) n / t to g / t else n to g
    }

    // Kuenftige Splits, unabhaengig vom Abfragetag; gleiches Kuerzel und Tag nur einmal
    fun splitsLesen(text: String): List<Split>? = runCatching {
        val d = daten(text) ?: return null
        val zeilen = (d["rows"] as? JsonArray).orEmpty()
        zeilen.mapNotNull { e ->
            val z = e.obj() ?: return@mapNotNull null
            val symbol = Quartalszahlen.symbol(z["symbol"].text() ?: return@mapNotNull null)
            val v = verhaeltnis(z["ratio"].text()) ?: return@mapNotNull null
            val tag = datum(z["executionDate"].text())?.toEpochDay() ?: return@mapNotNull null
            Split(symbol, z["name"].text()?.let { Quartalszahlen.name(it) } ?: symbol, tag, v.first, v.second)
        }.distinctBy { it.symbol to it.tag }
    }.getOrNull()

    // ------------------------------------------------------------ Wirtschaftstermine

    // Ein Termin; minute = Minuten seit Mitternacht in New York, null = ganztaegig oder ohne Uhrzeit
    data class Wirtschaftstermin(
        val tag: Long,
        val minute: Int?,
        val land: String,
        val name: String,
        val ist: String?,
        val erwartet: String?,
        val vorher: String?,
    )

    // Termine eines Tages; das Feld heisst gmt, enthaelt aber New Yorker Zeit, Uhrzeit nur bei Muster HH:MM;
    // gleicher Name und gleiche Zeit mit verschiedenen Werten kommen vor, darum distinct ueber alle Felder
    fun wirtschaftLesen(text: String, tag: LocalDate): List<Wirtschaftstermin>? = runCatching {
        val d = daten(text) ?: return null
        val zeilen = (d["rows"] as? JsonArray).orEmpty()
        zeilen.mapNotNull { e ->
            val z = e.obj() ?: return@mapNotNull null
            val land = entitaeten(z["country"].text()) ?: return@mapNotNull null
            val name = entitaeten(z["eventName"].text()) ?: return@mapNotNull null
            Wirtschaftstermin(
                tag = tag.toEpochDay(),
                minute = z["gmt"].text()?.let { t ->
                    Regex("^(\\d{1,2}):(\\d{2})$").find(t)?.let { m ->
                        val h = m.groupValues[1].toIntOrNull() ?: return@let null
                        val min = m.groupValues[2].toIntOrNull() ?: return@let null
                        if (h < 24 && min < 60) h * 60 + min else null
                    }
                },
                land = land,
                name = name,
                ist = entitaeten(z["actual"].text()),
                erwartet = entitaeten(z["consensus"].text()),
                vorher = entitaeten(z["previous"].text()),
            )
        }.distinct().sortedWith(compareBy<Wirtschaftstermin>({ it.minute != null }, { it.minute ?: 0 }, { it.land }, { it.name }))
    }.getOrNull()

    // Laender, deren Termine die App hervorhebt (den Euroraum gibt es in Regionen nicht)
    val WICHTIGE_LAENDER = setOf("United States", "Euro Zone", "Germany", "United Kingdom", "Japan", "China", "Switzerland", "France")

    // nur wichtige Laender, ohne Feiertage (ganztaegig und ohne jeden Wert)
    fun wirtschaftWichtige(liste: List<Wirtschaftstermin>): List<Wirtschaftstermin> =
        liste.filter { it.land in WICHTIGE_LAENDER && !(it.minute == null && it.ist == null && it.erwartet == null && it.vorher == null) }

    // Millisekunden des Termins nach New Yorker Uhr; null ohne Uhrzeit
    fun zeitpunkt(t: Wirtschaftstermin): Long? = t.minute?.let { m ->
        LocalDateTime.of(LocalDate.ofEpochDay(t.tag), LocalTime.of(m / 60, m % 60))
            .atZone(ZoneId.of("America/New_York")).toInstant().toEpochMilli()
    }

    // Zahlen der Quelle in deutscher Schreibweise: Punkt und Komma tauschen
    fun wertText(roh: String, deutsch: Boolean): String {
        if (!deutsch) return roh
        val sb = StringBuilder(roh.length)
        for (c in roh) sb.append(if (c == '.') ',' else if (c == ',') '.' else c)
        return sb.toString()
    }

    // ISO-Code des Landes aus dem Verzeichnis der App; Euroraum gibt es dort nicht
    fun landCode(land: String): String? =
        if (land == "Euro Zone") "EU" else Regionen.LAENDER.firstOrNull { it.en == land }?.code
}
