package de.troetenrepublik.app.market

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.data.Series
import de.troetenrepublik.app.engine.Splits
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Zweiter Rueckfall fuer Kurse von US-Werten: Twelve Data mit dem eigenen Schluessel der Person. Der kostenlose Tarif
// (Basic) erlaubt 8 Abrufe je Minute und 800 am Tag; ob er Werte ausserhalb der USA liefert, ist offen, deshalb fragt
// die App dort nur US-Symbole ab. Der Schluessel geht nur als Kopfzeile an den Anbieter. Fehler kommen oft als Antwort
// mit Status 200 und status "error" im Text; sie werden wie ein HTTP-Fehler behandelt. Dividenden und Splits gehoeren
// nicht zu dieser Quelle.
object TwelveDataKurse {
    const val NAME = "Twelve Data"
    const val SEITE = "https://twelvedata.com"
    const val ANMELDUNG = "https://twelvedata.com/register"
    const val BASIS = "https://api.twelvedata.com"
    const val WAEHRUNG = "USD"

    // 5.000 Tageskerzen je Abruf (rund zwanzig Jahre), ein Abruf zaehlt einen Credit
    const val AUSGABE = 5_000
    const val VERLAUF_TAGE = 7_300L
    private const val TAG = 86_400_000L
    private const val KERZE_VERSATZ = 5 * 3_600_000L

    val ABSTAENDE = MassiveKurse.ABSTAENDE

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val US = Regex("[A-Z]{1,5}(-[A-Z])?")

    // Kuerzel beim Anbieter; null fuer alles ausser US-Werten ohne Boersenzusatz. BRK-B wird BRK.B.
    fun ticker(symbol: String): String? = symbol.takeIf { US.matches(it) }?.replace('-', '.')

    fun kopf(schluessel: String): Map<String, String> = mapOf("Authorization" to "apikey $schluessel")

    fun reiheAdresse(ticker: String): String =
        "$BASIS/time_series?symbol=$ticker&interval=1day&outputsize=$AUSGABE&order=ASC&adjust=splits"

    fun quoteAdresse(ticker: String): String = "$BASIS/quote?symbol=$ticker"

    private fun JsonObject.zahl(k: String): Double? = (this[k] as? JsonPrimitive)?.content?.toDoubleOrNull()?.takeIf { it.isFinite() }
    private fun JsonObject.text(k: String): String? = (this[k] as? JsonPrimitive)?.content

    private fun objekt(text: String?): JsonObject? = text?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() }

    // Fehlercode aus einer Antwort mit Status 200: {"code":401,"message":"...","status":"error"}; null, wenn kein Fehler.
    // Fehlt der Code, gilt 500 (Stoerung).
    fun fehlerCode(text: String?): Int? {
        val o = objekt(text) ?: return null
        if (o.text("status") != "error") return null
        return o.zahl("code")?.toInt() ?: 500
    }

    // Tageskerzen aus der Antwort von time_series (values mit datetime, open, high, low, close, volume; Zahlen als
    // Text), aelteste zuerst; null bei Fehler oder anderer Form, leer, wenn der Anbieter den Wert kennt, aber nichts liefert
    fun kerzenLesen(text: String): List<Candle>? {
        val o = objekt(text) ?: return null
        if (fehlerCode(text) != null) return null
        val v = o["values"] ?: return emptyList()
        if (v !is JsonArray) return null
        return v.mapNotNull { e ->
            val k = e as? JsonObject ?: return@mapNotNull null
            val t = k.text("datetime")?.take(10)?.let(MassiveKurse::tagAus) ?: return@mapNotNull null
            val c = k.zahl("close")?.takeIf { it > 0 } ?: return@mapNotNull null
            Candle(t * TAG + KERZE_VERSATZ, k.zahl("open") ?: c, k.zahl("high") ?: c, k.zahl("low") ?: c, c, k.zahl("volume") ?: 0.0)
        }.sortedBy { it.t }
    }

    // Kurs aus der Antwort von quote; null bei Fehler, ohne Kurs oder bei anderer Form. Kurszeit: timestamp, sonst
    // der Handelsschluss des Tags in datetime.
    fun quoteLesen(symbol: String, text: String, jetzt: Long): Quote? {
        val o = objekt(text) ?: return null
        if (fehlerCode(text) != null) return null
        val p = o.zahl("close")?.takeIf { it > 0 } ?: return null
        val vor = o.zahl("previous_close")?.takeIf { it > 0 } ?: p
        val spanne = o["fifty_two_week"] as? JsonObject
        val zeit = o.zahl("timestamp")?.toLong()?.takeIf { it > 0 }?.let { it * 1000L }
            ?: o.text("datetime")?.take(10)?.let(MassiveKurse::tagAus)?.let { it * TAG + 21 * 3_600_000L }
            ?: jetzt
        return Quote(
            symbol = symbol, name = o.text("name")?.takeIf { it.isNotBlank() } ?: Symbols.name(symbol), price = p, prevClose = vor,
            open = o.zahl("open") ?: p, dayHigh = o.zahl("high") ?: p, dayLow = o.zahl("low") ?: p,
            weekHigh52 = spanne?.zahl("high") ?: p, weekLow52 = spanne?.zahl("low") ?: p, volume = o.zahl("volume") ?: 0.0,
            nativeCurrency = WAEHRUNG, nativePrice = p, exchange = NAME, marketState = "CLOSED", updatedAt = jetzt,
            regularPrice = p, marketTime = zeit,
        )
    }

    fun kursAus(symbol: String, kerzen: List<Candle>, jetzt: Long): Quote? =
        MassiveKurse.kursAus(symbol, kerzen, jetzt)?.copy(exchange = NAME)

    fun zeitraumTage(range: String): Long? = TiingoKurse.zeitraumTage(range)
}

// Die Quelle selbst: Kurs ueber quote (ein Credit, fuenf Minuten im Speicher), Kerzen ueber einen Abruf von time_series
// je Wert. Kontingent 8 je Minute und 800 am Tag; ist es aufgebraucht, antwortet die Quelle nicht.
class TwelveDataQuelle(
    abruf: suspend (String, Map<String, String>) -> MarketApi.Antwort,
    schluessel: () -> String,
    kontingent: () -> Abrufkontingent? = { null },
    uhr: () -> Long = System::currentTimeMillis,
) : RueckfallKern<List<Candle>>(abruf, schluessel, kontingent, uhr, Abrufkontingent.TWELVEDATA), KursQuelle {
    override val praefix: String = ""
    override val waehrung: String = TwelveDataKurse.WAEHRUNG

    private companion object {
        const val QUOTE_HALTEN_MS = 5 * 60_000L
    }

    private val quotes = HashMap<String, Eintrag<Quote>>()

    override fun kopf(schluessel: String): Map<String, String> = TwelveDataKurse.kopf(schluessel)

    // Fehler in einer Antwort mit Status 200 zaehlen wie der HTTP-Status im Text
    override fun status(a: MarketApi.Antwort): Int = (if (a.code in 200..299) TwelveDataKurse.fehlerCode(a.text) else null) ?: a.code

    override fun verwerfen() { quotes.clear() }

    private suspend fun tagesreihe(symbol: String): List<Candle>? {
        val t = TwelveDataKurse.ticker(symbol) ?: return null
        return daten(t) { s ->
            val e = holen(TwelveDataKurse.reiheAdresse(t), s)
            if (e.fest) emptyList() else e.text?.let(TwelveDataKurse::kerzenLesen)
        }
    }

    private suspend fun quote(symbol: String): Quote? {
        val t = TwelveDataKurse.ticker(symbol) ?: return null
        return mitSchluessel { s ->
            val jetzt = uhr()
            quotes[t]?.takeIf { jetzt - it.zeit < QUOTE_HALTEN_MS }?.let { return@mitSchluessel it.wert }
            val e = holen(TwelveDataKurse.quoteAdresse(t), s)
            if (e.fest) null else e.text?.let { TwelveDataKurse.quoteLesen(symbol, it, jetzt) }?.also { quotes[t] = Eintrag(jetzt, it) }
        }
    }

    // Der laufende Kurs aus quote; ohne ihn der Schluss aus den Tageskerzen
    override suspend fun kurse(kennungen: List<String>): Map<String, Quote> {
        val aus = HashMap<String, Quote>()
        for (k in kennungen) {
            val q = quote(k) ?: tagesreihe(k)?.let { TwelveDataKurse.kursAus(k, it, uhr()) }
            if (q != null) aus[k] = q
        }
        return aus
    }

    override suspend fun kerzen(kennung: String, range: String, interval: String): Series {
        val tage = TwelveDataKurse.zeitraumTage(range)?.takeIf { interval in TwelveDataKurse.ABSTAENDE } ?: return Series(emptyList())
        val ab = uhr() - tage * TAG
        return Series(MassiveKurse.verdichten(tagesreihe(kennung).orEmpty().filter { it.t >= ab }, interval))
    }

    override suspend fun kerzenZwischen(kennung: String, vonMs: Long, bisMs: Long, interval: String): Series {
        if (interval !in TwelveDataKurse.ABSTAENDE || vonMs < uhr() - TwelveDataKurse.VERLAUF_TAGE * TAG || bisMs < vonMs) return Series(emptyList())
        return Series(MassiveKurse.verdichten(tagesreihe(kennung).orEmpty().filter { it.t in vonMs..bisMs }, interval))
    }

    override suspend fun dividenden(kennung: String): List<MarketApi.DividendEvent> = emptyList()

    override suspend fun splits(kennung: String): List<Splits.Split> = emptyList()

    override suspend fun suche(text: String): List<MarketApi.SearchHit> = emptyList()

    override suspend fun wechselkurse(): Map<String, Double> = emptyMap()
}
