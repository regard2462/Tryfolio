package de.troetenrepublik.app.market

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.data.Series
import de.troetenrepublik.app.engine.Splits
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

// [0.73] Kursquelle Moskauer Boerse ueber die oeffentliche ISS-Schnittstelle (iss.moex.com, frei, ohne Anmeldung, Kurse 15
// Minuten verzoegert): Aktien am Handelsplatz TQBR in Rubel. Kerzen: candles.json (Intervalle 1, 10, 60 Minuten, Tag,
// Woche, Monat), Dividenden und Splits aus den Statistikbloecken, Rubelkurs aus dem Fixing der Zentralbank (MOEX, sonst cbr.ru).
class MoexQuelle(private val abruf: suspend (String) -> String?) : KursQuelle {
    override val praefix = KursQuellen.MOEX
    override val waehrung = "RUB"

    private var stand: Pair<Long, Map<String, Quote>>? = null

    // Letzte erfolgreiche Kerzen je Zeitraum: Bricht ein Abruf ab, gilt dieser Stand statt einer leeren Seite
    private val letzteKerzen = HashMap<String, Series>()

    // Die Verbindung bricht oft nach dem Zeitlimit ab, der naechste Versuch antwortet sofort: bis zu drei Versuche mit kurzer
    // Pause, erst dann Fehlschlag (null). Ein Fehlschlag wird nirgends zwischengespeichert.
    private suspend fun holen(url: String): String? {
        repeat(VERSUCHE) { i ->
            abruf(url)?.let { misserfolge = 0; return it }
            if (i < VERSUCHE - 1) delay(PAUSE_MS)
        }
        misserfolge++
        return null
    }

    // Abrufe in Folge, die auch nach den Wiederholversuchen scheiterten
    @Volatile private var misserfolge = 0

    override val antwortetNicht: Boolean get() = misserfolge >= AUSFALL_AB

    override suspend fun kurse(kennungen: List<String>): Map<String, Quote> {
        val jetzt = System.currentTimeMillis()
        val alle = stand?.takeIf { jetzt - it.first < KURS_GUELTIG_MS }?.second
            ?: holen(Iss.KURSE_ADRESSE)?.let { Iss.kurse(it, jetzt) }?.takeIf { it.isNotEmpty() }?.also { stand = jetzt to it }
            ?: stand?.second
            ?: return emptyMap()
        return kennungen.mapNotNull { k -> alle[k]?.let { KursQuellen.symbol(praefix, k) to it.copy(symbol = KursQuellen.symbol(praefix, k)) } }.toMap()
    }

    override suspend fun kerzen(kennung: String, range: String, interval: String): Series {
        val jetzt = System.currentTimeMillis()
        val tage = Iss.tage(range)
        val schluessel = "$kennung|$range|$interval"
        val serie = seiten(kennung, jetzt - tage * TAG_MS, jetzt, interval)
        if (serie.isEmpty()) return letzteKerzen[schluessel] ?: Series(emptyList())
        // Tages- und Wochenchart: nur die letzten Handelstage, nicht alles seit dem Fenster
        val letzteTage = when (range) { "1d" -> 1; "5d" -> 5; else -> 0 }
        val kerzen = if (letzteTage == 0) serie else {
            val tageListe = serie.map { Iss.tagMsk(it.t) }.distinct().takeLast(letzteTage).toSet()
            serie.filter { Iss.tagMsk(it.t) in tageListe }
        }
        return mitFenster(kerzen, Iss.intraday(interval)).also { if (kerzen.isNotEmpty()) letzteKerzen[schluessel] = it }
    }

    override suspend fun kerzenZwischen(kennung: String, vonMs: Long, bisMs: Long, interval: String): Series {
        // Seit 1970 abgefragt (gesamte Historie): nur die juengsten 25 Jahre, die Boerse beginnt 1997
        val von = maxOf(vonMs, bisMs - 25 * 366 * TAG_MS)
        val kerzen = seiten(kennung, von, bisMs, interval).filter { it.t in vonMs..bisMs }
        return mitFenster(kerzen, Iss.intraday(interval))
    }

    // Eine Abfrage liefert hoechstens 500 Kerzen; weitere Seiten ueber start
    private suspend fun seiten(kennung: String, vonMs: Long, bisMs: Long, interval: String): List<Candle> {
        val iss = Iss.intervall(interval)
        val aus = ArrayList<Candle>()
        var start = 0
        repeat(MAX_SEITEN) {
            val text = holen(Iss.kerzenAdresse(kennung, iss, Iss.datum(vonMs), Iss.datum(bisMs + TAG_MS), start)) ?: return aus
            val seite = Iss.kerzen(text)
            aus += seite
            if (seite.size < Iss.SEITE) return aus
            start += seite.size
        }
        return aus
    }

    // Handelsfenster des letzten Tages in Moskauer Zeit: Morgensitzung ab 6:50, Hauptsitzung 9:50 bis 18:50, Abendsitzung bis 23:50.
    // Das Fenster reicht so weit, wie an dem Tag Kerzen liegen.
    private fun mitFenster(kerzen: List<Candle>, intraday: Boolean): Series {
        if (kerzen.isEmpty() || !intraday) return Series(kerzen)
        val tag = Iss.tagMsk(kerzen.last().t)
        val tagBeginn = tag * TAG_MS - MSK_MS
        fun ab(h: Int, m: Int) = tagBeginn + (h * 60 + m) * 60_000L
        val amTag = kerzen.filter { Iss.tagMsk(it.t) == tag }
        val morgen = amTag.any { it.t < ab(9, 50) }
        val abend = amTag.any { it.t >= ab(19, 5) }
        return Series(kerzen, if (morgen) ab(6, 50) else ab(9, 50), if (abend) ab(23, 50) else ab(18, 50))
    }

    // Die Schnittstelle fuehrt keine Dividenden: ohne Angaben (sauber leer)
    override suspend fun dividenden(kennung: String): List<MarketApi.DividendEvent> = emptyList()

    override suspend fun splits(kennung: String): List<Splits.Split> =
        holen(Iss.SPLITS_ADRESSE)?.let { Iss.splits(it, kennung) }.orEmpty()

    override suspend fun suche(text: String): List<MarketApi.SearchHit> {
        val q = text.trim()
        if (q.length < 2) return emptyList()
        return holen(Iss.sucheAdresse(q))?.let { Iss.suche(it) }.orEmpty()
    }

    override suspend fun wechselkurse(): Map<String, Double> {
        holen(Iss.FIXING_ADRESSE)?.let { Iss.eurFixing(it) }?.let { return mapOf(waehrung to it) }
        holen(Iss.ZENTRALBANK_ADRESSE)?.let { Iss.eurZentralbank(it) }?.let { return mapOf(waehrung to it) }
        return emptyMap()
    }

    private companion object {
        const val KURS_GUELTIG_MS = 30_000L
        const val TAG_MS = 86_400_000L
        const val MSK_MS = 3 * 3_600_000L
        const val MAX_SEITEN = 16
        const val VERSUCHE = 3
        const val AUSFALL_AB = 3
        const val PAUSE_MS = 400L
    }
}

// Adressen und Leser der ISS-Antworten; reine Funktionen ohne Netz.
object Iss {
    private const val BASIS = "https://iss.moex.com/iss"
    const val SEITE = 500
    const val KURSE_ADRESSE = "$BASIS/engines/stock/markets/shares/boards/TQBR/securities.json?iss.meta=off&iss.only=securities,marketdata" +
        "&securities.columns=SECID,SHORTNAME,SECNAME,PREVPRICE,LOTSIZE,CURRENCYID" +
        "&marketdata.columns=SECID,LAST,OPEN,HIGH,LOW,VOLTODAY,LASTCHANGEPRCNT,ISSUECAPITALIZATION,SYSTIME,TRADINGSTATUS"
    const val SPLITS_ADRESSE = "$BASIS/statistics/engines/stock/splits.json?iss.meta=off"
    const val FIXING_ADRESSE = "$BASIS/statistics/engines/currency/markets/selt/rates.json?iss.meta=off"
    const val ZENTRALBANK_ADRESSE = "https://www.cbr.ru/scripts/XML_daily.asp"

    fun kerzenAdresse(kennung: String, intervall: Int, von: String, bis: String, start: Int): String =
        "$BASIS/engines/stock/markets/shares/boards/TQBR/securities/$kennung/candles.json?iss.meta=off&interval=$intervall&from=$von&till=$bis&start=$start"

    fun sucheAdresse(q: String) = "$BASIS/securities.json?iss.meta=off&limit=20&securities.columns=secid,shortname,name,isin,is_traded,primary_boardid,group&q=" +
        java.net.URLEncoder.encode(q, "UTF-8")

    private val json = Json { ignoreUnknownKeys = true }

    // Zeilen eines Blocks als Spalte-zu-Wert; leer bei fehlendem Block
    fun tabelle(text: String, block: String): List<Map<String, JsonElement>> = runCatching {
        val b = json.parseToJsonElement(text).jsonObject[block] as? JsonObject ?: return@runCatching emptyList()
        val spalten = (b["columns"] as? JsonArray)?.map { (it as? JsonPrimitive)?.contentOrNull.orEmpty() } ?: return@runCatching emptyList()
        (b["data"] as? JsonArray).orEmpty().mapNotNull { z ->
            val a = z as? JsonArray ?: return@mapNotNull null
            spalten.indices.associate { i -> spalten[i] to (a.getOrNull(i) ?: JsonNull) }
        }
    }.getOrDefault(emptyList())

    private fun JsonElement?.zahl(): Double? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.doubleOrNull?.takeIf { it.isFinite() }
    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

    // "2026-11-09 18:39:55" in Moskauer Zeit (UTC+3, ohne Sommerzeit) in Millisekunden seit 1970
    fun moskauMs(text: String?): Long? {
        if (text == null || text.length < 10) return null
        val tag = runCatching { java.time.LocalDate.parse(text.substring(0, 10)).toEpochDay() }.getOrNull() ?: return null
        val z = if (text.length >= 19) text.substring(11, 19).split(':').mapNotNull { it.toIntOrNull() } else emptyList()
        val s = if (z.size == 3) z[0] * 3600 + z[1] * 60 + z[2] else 0
        return tag * 86_400_000L + s * 1000L - 3 * 3_600_000L
    }

    // Tag seit 1970 in Moskauer Zeit
    fun tagMsk(ms: Long): Long = (ms + 3 * 3_600_000L).floorDiv(86_400_000L)

    fun datum(ms: Long): String = java.time.LocalDate.ofEpochDay(tagMsk(ms)).toString()

    // Yahoo-Zeitraum in Tagen, mit Reserve fuer Wochenenden und Feiertage
    fun tage(range: String): Long = when (range) {
        "1d" -> 6; "5d" -> 9; "1mo" -> 32; "3mo" -> 95; "6mo" -> 185; "1y" -> 370; "2y" -> 735; "5y" -> 1830; "6y" -> 2200
        else -> 3660
    }

    // Yahoo-Intervall in ISS-Minuten bzw. Kennung: 1 Minute, 10 Minuten, 60 Minuten, 24 Tag, 7 Woche, 31 Monat
    fun intervall(interval: String): Int = when (interval) {
        "1m", "2m", "5m", "15m" -> 10
        "30m", "60m", "1h", "90m" -> 60
        "1wk", "5d" -> 7
        "1mo", "3mo" -> 31
        else -> 24
    }

    fun intraday(interval: String): Boolean = intervall(interval) in setOf(1, 10, 60)

    fun kurse(text: String, jetzt: Long): Map<String, Quote> {
        val wp = tabelle(text, "securities").associateBy { it["SECID"].text() }
        val aus = HashMap<String, Quote>()
        for (m in tabelle(text, "marketdata")) {
            val id = m["SECID"].text() ?: continue
            val s = wp[id] ?: continue
            val waehrung = s["CURRENCYID"].text()
            if (waehrung != null && waehrung != "SUR" && waehrung != "RUB") continue
            val vortag = s["PREVPRICE"].zahl() ?: 0.0
            val preis = (m["LAST"].zahl()?.takeIf { it > 0 } ?: vortag).takeIf { it > 0 } ?: continue
            val bezug = if (vortag > 0) vortag else m["LASTCHANGEPRCNT"].zahl()?.let { preis / (1 + it / 100.0) } ?: preis
            aus[id] = Quote(
                symbol = id, name = s["LATNAME"].text()?.takeIf { it.isNotBlank() } ?: s["SECNAME"].text() ?: s["SHORTNAME"].text() ?: id, price = preis, prevClose = bezug,
                open = m["OPEN"].zahl() ?: 0.0, dayHigh = m["HIGH"].zahl() ?: 0.0, dayLow = m["LOW"].zahl() ?: 0.0,
                marketCap = m["ISSUECAPITALIZATION"].zahl() ?: 0.0, volume = m["VOLTODAY"].zahl() ?: 0.0,
                currency = "RUB", nativeCurrency = "RUB", nativePrice = preis, exchange = "MOEX",
                marketState = if (m["TRADINGSTATUS"].text() == "T") "REGULAR" else "CLOSED",
                updatedAt = jetzt, regularPrice = preis, marketTime = moskauMs(m["SYSTIME"].text()) ?: 0L,
            )
        }
        return aus
    }

    fun kerzen(text: String): List<Candle> = tabelle(text, "candles").mapNotNull { z ->
        val t = moskauMs(z["begin"].text()) ?: return@mapNotNull null
        val c = z["close"].zahl()?.takeIf { it > 0 } ?: return@mapNotNull null
        Candle(t, z["open"].zahl() ?: c, z["high"].zahl() ?: c, z["low"].zahl() ?: c, c, z["volume"].zahl() ?: 0.0)
    }.sortedBy { it.t }

    // Splits: before Aktien werden zu after (1:10 heisst aus einer werden zehn)
    fun splits(text: String, kennung: String): List<Splits.Split> = tabelle(text, "splits").mapNotNull { z ->
        if (z["secid"].text() != kennung) return@mapNotNull null
        val vorher = z["before"].zahl()?.takeIf { it > 0 } ?: return@mapNotNull null
        val nachher = z["after"].zahl()?.takeIf { it > 0 } ?: return@mapNotNull null
        val tag = runCatching { java.time.LocalDate.parse(z["tradedate"].text()!!.take(10)).toEpochDay() }.getOrNull() ?: return@mapNotNull null
        Splits.Split(tag * 86_400_000L, nachher, vorher)
    }.sortedBy { it.t }

    // Suche: nur gehandelte Aktien am Hauptplatz TQBR
    fun suche(text: String): List<MarketApi.SearchHit> = tabelle(text, "securities").mapNotNull { z ->
        val id = z["secid"].text() ?: return@mapNotNull null
        if (z["group"].text() != "stock_shares" || z["primary_boardid"].text() != "TQBR") return@mapNotNull null
        if (z["is_traded"].zahl() != 1.0) return@mapNotNull null
        val symbol = KursQuellen.symbol(KursQuellen.MOEX, id)
        if (!KursQuellen.istExtern(symbol)) return@mapNotNull null
        MarketApi.SearchHit(symbol, z["name"].text() ?: z["shortname"].text() ?: id, "EQUITY", de.troetenrepublik.app.i18n.S.boerseMoskau)
    }

    // Fixing der Zentralbank im Block cbrf: Rubel je Euro
    fun eurFixing(text: String): Double? = tabelle(text, "cbrf").firstNotNullOfOrNull { it["CBRF_EUR_LAST"].zahl()?.takeIf { v -> v > 0 } }

    // Tageskurse der Zentralbank (XML, Dezimalkomma): Rubel je Euro
    fun eurZentralbank(text: String): Double? {
        val i = text.indexOf("<CharCode>EUR</CharCode>")
        if (i < 0) return null
        val rest = text.substring(i)
        val nenner = Regex("<Nominal>\\s*([0-9]+)\\s*</Nominal>").find(rest)?.groupValues?.get(1)?.toDoubleOrNull() ?: 1.0
        val wert = Regex("<Value>\\s*([0-9]+(?:[.,][0-9]+)?)\\s*</Value>").find(rest)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull() ?: return null
        return (wert / nenner).takeIf { it > 0 }
    }
}
