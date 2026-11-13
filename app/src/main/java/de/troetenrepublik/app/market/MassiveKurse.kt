package de.troetenrepublik.app.market

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.data.Series
import de.troetenrepublik.app.engine.Splits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Rueckfallquelle fuer Kurse von US-Werten: Massive (frueher Polygon.io) mit dem eigenen Schluessel des Nutzers. Der
// kostenlose Tarif liefert Tageskerzen nach Handelsende, zwei Jahre Verlauf und fuenf Abrufe je Minute; laufende Kurse
// und Intraday-Kerzen gibt es dort nicht. Der Schluessel geht nur als Kopfzeile an den Anbieter, nie in eine Adresse.
// Hier stehen Adressen und Leser als reine Funktionen, darunter die Quelle mit Kontingent und Zwischenspeicher.
object MassiveKurse {
    const val NAME = "Massive"
    const val SEITE = "https://massive.com"
    const val BASIS = "https://api.massive.com"
    const val WAEHRUNG = "USD"

    // Verlauf im kostenlosen Tarif: zwei Jahre. Die App fragt einen Tag weniger ab.
    const val VERLAUF_TAGE = 729L
    private const val TAG = 86_400_000L

    // Abstaende, die sich aus Tageskerzen bilden lassen; Intraday-Kerzen hat der Tarif nicht
    val ABSTAENDE = setOf("1d", "1wk", "1mo")

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val US = Regex("[A-Z]{1,5}(-[A-Z])?")

    // Kuerzel beim Anbieter fuer ein Kuerzel der App; null fuer alles ausser US-Werten ohne Boersenzusatz (Indizes,
    // Waehrungen, Krypto, Terminkontrakte, Werte anderer Boersen). Aktiengattungen: BRK-B wird BRK.B.
    fun ticker(symbol: String): String? = symbol.takeIf { US.matches(it) }?.replace('-', '.')

    fun kopf(schluessel: String): Map<String, String> = mapOf("Authorization" to "Bearer $schluessel")

    private fun tag(epochTag: Long): String = java.time.LocalDate.ofEpochDay(epochTag).toString()

    // Tageskerzen zwischen zwei Tagen (Tage seit 1970), um Splits bereinigt, aelteste zuerst
    fun kerzenAdresse(ticker: String, vonTag: Long, bisTag: Long): String =
        "$BASIS/v2/aggs/ticker/$ticker/range/1/day/${tag(vonTag)}/${tag(bisTag)}?adjusted=true&sort=asc&limit=5000"

    // Ausschuettungen ab einem Ex-Tag, aelteste zuerst
    fun dividendenAdresse(ticker: String, abTag: Long): String =
        "$BASIS/stocks/v1/dividends?ticker=$ticker&ex_dividend_date.gte=${tag(abTag)}&limit=1000&sort=ex_dividend_date.asc"

    private fun JsonObject.zahl(k: String): Double? = (this[k] as? JsonPrimitive)?.content?.toDoubleOrNull()?.takeIf { it.isFinite() }
    private fun JsonObject.text(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun wurzel(text: String): JsonObject? {
        val o = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
        // OK und DELAYED sind Antworten mit Daten; alles andere (ERROR, NOT_AUTHORIZED) ist keine
        val status = o.text("status")
        return o.takeIf { status == null || status == "OK" || status == "DELAYED" }
    }

    // Tageskerzen aus der Antwort (results mit t, o, h, l, c, v), aelteste zuerst; null, wenn die Antwort keine der
    // erwarteten Form ist, leer, wenn der Anbieter den Wert kennt, aber keine Kerzen hat.
    fun kerzenLesen(text: String): List<Candle>? {
        val o = wurzel(text) ?: return null
        val r = o["results"] ?: return emptyList()
        if (r !is JsonArray) return null
        return r.mapNotNull { e ->
            val k = e as? JsonObject ?: return@mapNotNull null
            val t = k.zahl("t")?.toLong()?.takeIf { it > 0 } ?: return@mapNotNull null
            val c = k.zahl("c")?.takeIf { it > 0 } ?: return@mapNotNull null
            Candle(t, k.zahl("o") ?: c, k.zahl("h") ?: c, k.zahl("l") ?: c, c, k.zahl("v") ?: 0.0)
        }.sortedBy { it.t }
    }

    // Ausschuettungen in US-Dollar (Ex-Tag, Betrag je Stueck auf heutige Stueckzahl gerechnet); null bei unerwarteter Form
    fun dividendenLesen(text: String): List<MarketApi.DividendEvent>? {
        val o = wurzel(text) ?: return null
        val r = o["results"] ?: return emptyList()
        if (r !is JsonArray) return null
        return r.mapNotNull { e ->
            val d = e as? JsonObject ?: return@mapNotNull null
            if ((d.text("currency") ?: WAEHRUNG).uppercase() != WAEHRUNG) return@mapNotNull null
            val betrag = (d.zahl("split_adjusted_cash_amount") ?: d.zahl("cash_amount"))?.takeIf { it > 0 } ?: return@mapNotNull null
            val ex = d.text("ex_dividend_date")?.let(::tagAus) ?: return@mapNotNull null
            MarketApi.DividendEvent(ex * TAG, betrag)
        }.sortedBy { it.exDate }
    }

    // Tage seit 1970 aus JJJJ-MM-TT; null bei anderer Form
    internal fun tagAus(s: String): Long? {
        val t = s.split('-').mapNotNull { it.toIntOrNull() }
        if (t.size != 3 || s.length != 10) return null
        return runCatching { java.time.LocalDate.of(t[0], t[1], t[2]).toEpochDay() }.getOrNull()
    }

    // Kurs aus den Tageskerzen: Schluss des letzten Handelstags, Vortag aus der Kerze davor, Jahresspanne aus den
    // letzten 365 Tagen. Der Markt gilt als geschlossen, die Kurszeit ist der Handelsschluss dieses Tags (16 Uhr nach
    // dem Tagesbeginn der Kerze). exchange nennt die Quelle, daran erkennt die Wertpapierseite den Schlusskurs.
    fun kursAus(symbol: String, kerzen: List<Candle>, jetzt: Long): Quote? {
        val l = kerzen.lastOrNull()?.takeIf { it.close > 0 } ?: return null
        val vor = kerzen.getOrNull(kerzen.size - 2)?.close?.takeIf { it > 0 } ?: l.close
        val jahr = kerzen.filter { it.t >= l.t - 365 * TAG }
        return Quote(
            symbol = symbol, name = Symbols.name(symbol), price = l.close, prevClose = vor, open = l.open,
            dayHigh = l.high, dayLow = l.low, weekHigh52 = jahr.maxOf { it.high }, weekLow52 = jahr.minOf { it.low },
            volume = l.volume, nativeCurrency = WAEHRUNG, nativePrice = l.close, exchange = NAME, marketState = "CLOSED",
            updatedAt = jetzt, regularPrice = l.close, marketTime = l.t + 16 * 3_600_000L,
        )
    }

    // Tageskerzen zu Wochen (Montag bis Sonntag) oder Monaten verdichtet; jeder andere Abstand bleibt bei Tagen.
    fun verdichten(kerzen: List<Candle>, interval: String): List<Candle> {
        val schluessel: (Candle) -> Long = when (interval) {
            "1wk" -> { c -> Math.floorDiv(Math.floorDiv(c.t, TAG) + 3, 7L) }
            "1mo" -> { c -> java.time.LocalDate.ofEpochDay(Math.floorDiv(c.t, TAG)).let { it.year * 12L + it.monthValue } }
            else -> return kerzen
        }
        return kerzen.groupBy(schluessel).values.map { g ->
            Candle(g.first().t, g.first().open, g.maxOf { it.high }, g.minOf { it.low }, g.last().close, g.sumOf { it.volume })
        }.sortedBy { it.t }
    }

    // Tage, die ein Zeitraum der App zurueckreicht, wenn die Quelle ihn ganz abdeckt; null fuer Tages- und Wochenchart
    // und fuer alles ueber zwei Jahre (abgeschnittene Reihen verfaelschen Rechnungen).
    fun zeitraumTage(range: String): Long? = when (range) {
        "1mo" -> 31L; "3mo" -> 92L; "6mo" -> 183L; "ytd", "1y" -> 366L; "2y" -> VERLAUF_TAGE
        else -> null
    }
}

// Die Quelle selbst. Stumm ohne Schluessel und fuer jedes Kuerzel, das kein US-Wert ist. Je Wert ein Abruf der Tageskerzen
// von zwei Jahren; daraus entstehen Kurs und alle Zeitraeume, die Antwort bleibt sechs Stunden im Arbeitsspeicher (die
// Daten aendern sich einmal am Tag). Gezaehlt wird ueber das Abrufkontingent (vier je Minute, erlaubt sind fuenf); ist
// es aufgebraucht, antwortet die Quelle nicht, statt zu warten. abruf: Adresse und Kopfzeilen, Antwort mit Status.
class MassiveQuelle(
    private val abruf: suspend (String, Map<String, String>) -> MarketApi.Antwort,
    private val schluessel: () -> String,
    private val kontingent: () -> Abrufkontingent? = { null },
    private val uhr: () -> Long = System::currentTimeMillis,
) : KursQuelle {
    override val praefix: String = ""
    override val waehrung: String = MassiveKurse.WAEHRUNG

    private companion object {
        const val TAG = 86_400_000L
        const val HALTEN_MS = 6 * 3_600_000L
        const val NETZ_PAUSE_MS = 5 * 60_000L
        const val LIMIT_PAUSE_MS = 60_000L
    }

    private class Eintrag<T>(val zeit: Long, val wert: T)

    private val sperre = Mutex()
    private val eigenes by lazy { Abrufkontingent(null, uhr) }
    private val reihen = HashMap<String, Eintrag<List<Candle>>>()
    private val ausschuettungen = HashMap<String, Eintrag<List<MarketApi.DividendEvent>>>()
    private var gemerktFuer = ""
    private var abgelehnt = ""
    private var pauseBis = 0L

    // Letzte Meldung fuer die Seite Zugaenge: NewsRepository.SCHLUESSEL, NewsRepository.LIMIT oder null
    @Volatile var meldung: String? = null
        private set

    // Antwort eines Abrufs: text bei Erfolg; fest = der Anbieter hat geantwortet, aber ohne Daten fuer diese Anfrage
    // (unbekannter Wert, nicht im Tarif), das wird wie ein leeres Ergebnis gemerkt
    private class Ergebnis(val text: String?, val fest: Boolean)

    // Gilt nur unter der Sperre. Ein anderer oder entfernter Schluessel verwirft alles Gemerkte.
    private fun schluesselJetzt(): String? {
        val s = schluessel().trim()
        if (s != gemerktFuer) {
            reihen.clear(); ausschuettungen.clear(); gemerktFuer = s; pauseBis = 0L; meldung = null
        }
        return s.takeIf { it.isNotEmpty() && it != abgelehnt }
    }

    private suspend fun holen(adresse: String, s: String): Ergebnis {
        val jetzt = uhr()
        if (jetzt < pauseBis) return Ergebnis(null, false)
        val k = kontingent() ?: eigenes
        if (!k.erlaubt(Abrufkontingent.MASSIVE)) return Ergebnis(null, false)
        k.buchen(Abrufkontingent.MASSIVE)
        val a = try { abruf(adresse, MassiveKurse.kopf(s)) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        return when {
            a == null || a.code == 0 -> { pauseBis = jetzt + NETZ_PAUSE_MS; Ergebnis(null, false) }
            a.code == 401 -> { abgelehnt = s; meldung = NewsRepository.SCHLUESSEL; Ergebnis(null, false) }
            a.code == 429 -> { pauseBis = jetzt + LIMIT_PAUSE_MS; meldung = NewsRepository.LIMIT; Ergebnis(null, false) }
            a.code == 403 || a.code == 404 -> { meldung = null; Ergebnis(null, true) }
            a.ok -> { meldung = null; Ergebnis(a.text, false) }
            else -> { pauseBis = jetzt + NETZ_PAUSE_MS; Ergebnis(null, false) }
        }
    }

    // Tageskerzen der letzten zwei Jahre zu einem Kuerzel der App; null ohne Schluessel, ohne Entsprechung, ohne
    // Kontingent oder ohne Antwort. Eine leere Liste heisst: der Anbieter hat fuer diesen Wert nichts.
    private suspend fun tagesreihe(symbol: String): List<Candle>? {
        val t = MassiveKurse.ticker(symbol) ?: return null
        return sperre.withLock {
            val s = schluesselJetzt() ?: return@withLock null
            val jetzt = uhr()
            reihen[t]?.takeIf { jetzt - it.zeit < HALTEN_MS }?.let { return@withLock it.wert }
            val heute = Math.floorDiv(jetzt, TAG)
            val e = holen(MassiveKurse.kerzenAdresse(t, heute - MassiveKurse.VERLAUF_TAGE, heute), s)
            val kerzen: List<Candle>? = if (e.fest) emptyList() else e.text?.let(MassiveKurse::kerzenLesen)
            kerzen?.also { reihen[t] = Eintrag(jetzt, it) }
        }
    }

    override suspend fun kurse(kennungen: List<String>): Map<String, Quote> {
        val aus = HashMap<String, Quote>()
        for (k in kennungen) tagesreihe(k)?.let { MassiveKurse.kursAus(k, it, uhr()) }?.let { aus[k] = it }
        return aus
    }

    override suspend fun kerzen(kennung: String, range: String, interval: String): Series {
        val tage = MassiveKurse.zeitraumTage(range)?.takeIf { interval in MassiveKurse.ABSTAENDE } ?: return Series(emptyList())
        val reihe = tagesreihe(kennung).orEmpty()
        val ab = uhr() - tage * TAG
        return Series(MassiveKurse.verdichten(reihe.filter { it.t >= ab }, interval))
    }

    // Nur Fenster, die ganz im Verlauf des Tarifs liegen; sonst antwortet die Quelle nicht
    override suspend fun kerzenZwischen(kennung: String, vonMs: Long, bisMs: Long, interval: String): Series {
        if (interval !in MassiveKurse.ABSTAENDE || vonMs < uhr() - MassiveKurse.VERLAUF_TAGE * TAG || bisMs < vonMs) return Series(emptyList())
        return Series(MassiveKurse.verdichten(tagesreihe(kennung).orEmpty().filter { it.t in vonMs..bisMs }, interval))
    }

    // Ausschuettungen der letzten zwei Jahre
    override suspend fun dividenden(kennung: String): List<MarketApi.DividendEvent> {
        val t = MassiveKurse.ticker(kennung) ?: return emptyList()
        return sperre.withLock {
            val s = schluesselJetzt() ?: return@withLock emptyList()
            val jetzt = uhr()
            ausschuettungen[t]?.takeIf { jetzt - it.zeit < HALTEN_MS }?.let { return@withLock it.wert }
            val e = holen(MassiveKurse.dividendenAdresse(t, Math.floorDiv(jetzt, TAG) - MassiveKurse.VERLAUF_TAGE), s)
            val liste: List<MarketApi.DividendEvent>? = if (e.fest) emptyList() else e.text?.let(MassiveKurse::dividendenLesen)
            liste?.also { ausschuettungen[t] = Eintrag(jetzt, it) }.orEmpty()
        }
    }

    // Splits reichen im Tarif nur zwei Jahre zurueck; eine unvollstaendige Liste wuerde aeltere Kaeufe falsch umrechnen
    override suspend fun splits(kennung: String): List<Splits.Split> = emptyList()

    // Die Suche bleibt bei der Hauptquelle, sie wuerde das Kontingent der Kurse aufbrauchen
    override suspend fun suche(text: String): List<MarketApi.SearchHit> = emptyList()

    override suspend fun wechselkurse(): Map<String, Double> = emptyMap()
}
