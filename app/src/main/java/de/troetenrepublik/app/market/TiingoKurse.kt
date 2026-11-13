package de.troetenrepublik.app.market

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.data.Series
import de.troetenrepublik.app.engine.Splits
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs

// Rueckfallquelle fuer Kurse von US-Werten: Tiingo mit dem eigenen Schluessel der Person. Der kostenlose Tarif (Starter)
// liefert Tageskerzen mit rund 30 Jahren Verlauf, Splits und Dividenden in derselben Antwort; 50 Abrufe je Stunde und
// 1.000 am Tag. Laufende Kurse und Intraday-Kerzen holt die App dort nicht. Der Schluessel geht nur als Kopfzeile an den
// Anbieter, nie in eine Adresse. Hier stehen Adressen und Leser als reine Funktionen, darunter die Quelle.
object TiingoKurse {
    const val NAME = "Tiingo"
    const val SEITE = "https://www.tiingo.com"
    const val ANMELDUNG = "https://www.tiingo.com/account/api/token"
    const val BASIS = "https://api.tiingo.com"
    const val WAEHRUNG = "USD"

    // Verlauf je Abruf: zwanzig Jahre (rund ein Megabyte je Wert; der Tarif begrenzt die Datenmenge im Monat)
    const val VERLAUF_TAGE = 7_300L
    private const val TAG = 86_400_000L

    // Beginn der Kerze: New Yorker Mitternacht (05:00 UTC), damit das Datum in jeder Zeitzone stimmt
    private const val KERZE_VERSATZ = 5 * 3_600_000L

    val ABSTAENDE = MassiveKurse.ABSTAENDE

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val US = Regex("[A-Z]{1,5}(-[A-Z])?")

    // Kuerzel beim Anbieter; null fuer alles ausser US-Werten ohne Boersenzusatz. Tiingo schreibt Gattungen mit
    // Bindestrich wie die App (BRK-B).
    fun ticker(symbol: String): String? = symbol.takeIf { US.matches(it) }

    fun kopf(schluessel: String): Map<String, String> = mapOf("Authorization" to "Token $schluessel")

    private fun tag(epochTag: Long): String = java.time.LocalDate.ofEpochDay(epochTag).toString()

    // Tageskerzen mit Splits und Dividenden zwischen zwei Tagen (Tage seit 1970), aelteste zuerst
    fun kerzenAdresse(ticker: String, vonTag: Long, bisTag: Long): String =
        "$BASIS/tiingo/daily/$ticker/prices?startDate=${tag(vonTag)}&endDate=${tag(bisTag)}"

    // Eine Zeile der Antwort
    class Zeile(val tag: Long, val open: Double, val high: Double, val low: Double, val close: Double, val volume: Double, val dividende: Double, val split: Double)

    // Alles aus einer Antwort: Kerzen um Splits bereinigt (Kurse durch das Splitverhaeltnis spaeterer Splits, Stueckzahl
    // mal), Ausschuettungen auf heutige Stueckzahl gerechnet, Splits mit Zaehler und Nenner
    class Reihe(val kerzen: List<Candle>, val dividenden: List<MarketApi.DividendEvent>, val splits: List<Splits.Split>)

    private fun JsonObject.zahl(k: String): Double? = (this[k] as? JsonPrimitive)?.content?.toDoubleOrNull()?.takeIf { it.isFinite() }
    private fun JsonObject.text(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content

    // null, wenn die Antwort keine Liste ist (Fehlermeldung als Objekt, Seite); leere Reihe, wenn der Anbieter den Wert
    // kennt, aber nichts liefert
    fun lesen(text: String): Reihe? {
        val a = runCatching { json.parseToJsonElement(text) as? JsonArray }.getOrNull() ?: return null
        val zeilen = a.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val t = o.text("date")?.take(10)?.let(MassiveKurse::tagAus) ?: return@mapNotNull null
            val c = o.zahl("close")?.takeIf { it > 0 } ?: return@mapNotNull null
            Zeile(
                t, o.zahl("open") ?: c, o.zahl("high") ?: c, o.zahl("low") ?: c, c, o.zahl("volume") ?: 0.0,
                o.zahl("divCash")?.takeIf { it > 0 } ?: 0.0, o.zahl("splitFactor")?.takeIf { it > 0 } ?: 1.0,
            )
        }.sortedBy { it.tag }
        // Faktor aller Splits nach dem Tag der Zeile: Tiingo liefert unbereinigte Kurse
        val danach = DoubleArray(zeilen.size)
        var f = 1.0
        for (i in zeilen.indices.reversed()) { danach[i] = f; f *= zeilen[i].split }
        val kerzen = zeilen.mapIndexed { i, z ->
            val d = danach[i]
            Candle(z.tag * TAG + KERZE_VERSATZ, z.open / d, z.high / d, z.low / d, z.close / d, z.volume * d)
        }
        val dividenden = zeilen.mapIndexedNotNull { i, z ->
            if (z.dividende > 0) MarketApi.DividendEvent(z.tag * TAG, z.dividende / danach[i]) else null
        }
        val splits = zeilen.mapNotNull { z ->
            val s = z.split
            when {
                abs(s - 1.0) < 1e-9 -> null
                s > 1.0 -> Splits.Split(z.tag * TAG, s, 1.0)
                else -> Splits.Split(z.tag * TAG, 1.0, 1.0 / s)
            }
        }
        return Reihe(kerzen, dividenden, splits)
    }

    // Kurs aus den Tageskerzen wie bei Massive (Schluss des letzten Handelstags), Herkunft Tiingo
    fun kursAus(symbol: String, kerzen: List<Candle>, jetzt: Long): Quote? =
        MassiveKurse.kursAus(symbol, kerzen, jetzt)?.copy(exchange = NAME)

    // Tage, die ein Zeitraum der App zurueckreicht, wenn die Quelle ihn ganz abdeckt; null fuer Tages- und Wochenchart
    // und fuer alles ueber den Verlauf eines Abrufs
    fun zeitraumTage(range: String): Long? = when (range) {
        "5y" -> 1_830L; "10y" -> 3_660L
        else -> MassiveKurse.zeitraumTage(range)
    }
}

// Die Quelle selbst. Je Wert ein Abruf der Tageskerzen von zwanzig Jahren; daraus entstehen Kurs, alle Zeitraeume, Splits
// und Dividenden. Kontingent 50 je Stunde und 1.000 am Tag; ist es aufgebraucht, antwortet die Quelle nicht.
class TiingoQuelle(
    abruf: suspend (String, Map<String, String>) -> MarketApi.Antwort,
    schluessel: () -> String,
    kontingent: () -> Abrufkontingent? = { null },
    uhr: () -> Long = System::currentTimeMillis,
) : RueckfallKern<TiingoKurse.Reihe>(abruf, schluessel, kontingent, uhr, Abrufkontingent.TIINGO), KursQuelle {
    override val praefix: String = ""
    override val waehrung: String = TiingoKurse.WAEHRUNG

    override fun kopf(schluessel: String): Map<String, String> = TiingoKurse.kopf(schluessel)

    // Reihe der letzten zwanzig Jahre; null ohne Schluessel, ohne Entsprechung, ohne Kontingent oder ohne Antwort.
    // Leere Reihe: der Anbieter kennt den Wert nicht.
    private suspend fun reihe(symbol: String): TiingoKurse.Reihe? {
        val t = TiingoKurse.ticker(symbol) ?: return null
        return daten(t) { s ->
            val heute = Math.floorDiv(uhr(), TAG)
            val e = holen(TiingoKurse.kerzenAdresse(t, heute - TiingoKurse.VERLAUF_TAGE, heute), s)
            if (e.fest) TiingoKurse.Reihe(emptyList(), emptyList(), emptyList()) else e.text?.let(TiingoKurse::lesen)
        }
    }

    override suspend fun kurse(kennungen: List<String>): Map<String, Quote> {
        val aus = HashMap<String, Quote>()
        for (k in kennungen) reihe(k)?.let { TiingoKurse.kursAus(k, it.kerzen, uhr()) }?.let { aus[k] = it }
        return aus
    }

    override suspend fun kerzen(kennung: String, range: String, interval: String): Series {
        val tage = TiingoKurse.zeitraumTage(range)?.takeIf { interval in TiingoKurse.ABSTAENDE } ?: return Series(emptyList())
        val reihe = reihe(kennung)?.kerzen.orEmpty()
        val ab = uhr() - tage * TAG
        return Series(MassiveKurse.verdichten(reihe.filter { it.t >= ab }, interval))
    }

    // Nur Fenster, die ganz im Verlauf eines Abrufs liegen
    override suspend fun kerzenZwischen(kennung: String, vonMs: Long, bisMs: Long, interval: String): Series {
        if (interval !in TiingoKurse.ABSTAENDE || vonMs < uhr() - TiingoKurse.VERLAUF_TAGE * TAG || bisMs < vonMs) return Series(emptyList())
        return Series(MassiveKurse.verdichten(reihe(kennung)?.kerzen.orEmpty().filter { it.t in vonMs..bisMs }, interval))
    }

    override suspend fun dividenden(kennung: String): List<MarketApi.DividendEvent> = reihe(kennung)?.dividenden.orEmpty()

    override suspend fun splits(kennung: String): List<Splits.Split> = reihe(kennung)?.splits.orEmpty()

    // Wie splits, aber null ohne Schluessel oder Antwort (fuer den Rueckfall, wenn die Hauptquelle nichts liefert)
    suspend fun splitsOderNull(kennung: String): List<Splits.Split>? = reihe(kennung)?.splits

    // Die Suche bleibt bei der Hauptquelle, sie wuerde das Kontingent der Kurse aufbrauchen
    override suspend fun suche(text: String): List<MarketApi.SearchHit> = emptyList()

    override suspend fun wechselkurse(): Map<String, Double> = emptyMap()
}
