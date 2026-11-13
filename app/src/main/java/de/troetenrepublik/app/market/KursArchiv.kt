package de.troetenrepublik.app.market

import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.data.Series
import de.troetenrepublik.app.engine.Splits

// [0.78] Letzter bekannter Stand fuer Werte, deren Kursquelle nicht (mehr) liefert: ausgesetzte und nicht mehr gehandelte
// Werte und Werte einer Boerse, deren Schnittstelle gerade nicht antwortet. Reine Regeln ohne Netz.
object Kursarchiv {
    // Ab so vielen Tagen ohne neuen Kurs gilt ein Wert als ohne laufenden Kurs.
    const val VERALTET_TAGE = 10
    private const val TAG_MS = 86_400_000L

    // kursZeitMs: Zeitpunkt des letzten Kurses laut Quelle, 0 unbekannt (dann nie veraltet).
    fun veraltet(kursZeitMs: Long, jetztMs: Long): Boolean = kursZeitMs > 0 && jetztMs - kursZeitMs > VERALTET_TAGE * TAG_MS

    // Kuerzel, unter dem die allgemeine Kursabfrage den letzten Stand eines Werts einer weiteren Kursquelle fuehrt
    // (Moskauer Boerse: Kennung mit Endung .ME); null fuer jedes andere Kuerzel.
    fun ersatzSymbol(symbol: String): String? {
        val (praefix, kennung) = KursQuellen.teile(symbol) ?: return null
        return ersatzSymbol(praefix, kennung)
    }

    fun ersatzSymbol(praefix: String, kennung: String): String? = when (praefix) {
        KursQuellen.MOEX -> "$kennung.ME"
        else -> null
    }

    // Tage, die ein Zeitraum der Wertpapierseite bis zum letzten Stand zurueckreicht. Tages- und Wochenchart zeigen ohne
    // laufenden Kurs die letzten Handelstage als Tageskurse.
    fun tage(range: String): Long = when (range) {
        "1d" -> 7; "5d" -> 14; "1mo" -> 31; "3mo" -> 92; "6mo" -> 183; "1y" -> 366; "2y" -> 731; "5y" -> 1827; "6y" -> 2192
        else -> 3653
    }

    // Fenster eines Zeitraums, das am letzten Stand endet: erster und letzter Tag (Tage seit 1970).
    fun fensterTage(range: String, letzterMs: Long): Pair<Long, Long> {
        val bis = letzterMs.floorDiv(TAG_MS)
        return (bis - tage(range)) to bis
    }

    // Dasselbe in Sekunden fuer die Kursabfrage, das Ende einen Tag hinter dem letzten Stand.
    fun fensterSekunden(range: String, letzterMs: Long): Pair<Long, Long> {
        val (von, bis) = fensterTage(range, letzterMs)
        return von * 86_400L to (bis + 1) * 86_400L
    }

    // Aeltere Kurse gibt es nur als Tages-, Wochen- oder Monatskurse: Intraday-Abstaende werden Tageskurse.
    fun intervall(interval: String): String = when (interval) {
        "1wk", "5d" -> "1wk"
        "1mo", "3mo" -> "1mo"
        else -> "1d"
    }

    // Kurs des Ersatzkuerzels als Kurs des Werts: unter dessen Kuerzel, Markt geschlossen. Ohne Zeitpunkt oder Preis kein Stand.
    fun alsStand(symbol: String, q: Quote?): Quote? =
        q?.takeIf { it.price > 0 && it.marketTime > 0 }?.copy(symbol = symbol, marketState = "CLOSED")
}

// Kursquelle aus dem letzten bekannten Stand: Kommt hinter die Boerse selbst in die Kette (KursQuellenKette) und liefert,
// wenn diese nicht antwortet. kurseAbruf: allgemeine Kursabfrage zu Kuerzeln; kerzenAbruf: Kerzen zwischen zwei Zeitpunkten
// in Sekunden mit Abstand ("1d", "1wk", "1mo").
class ArchivQuelle(
    override val praefix: String,
    override val waehrung: String,
    private val kurseAbruf: suspend (List<String>) -> Map<String, Quote>,
    private val kerzenAbruf: suspend (String, Long, Long, String) -> Series,
) : KursQuelle {
    // Zeitpunkt des letzten Stands je Kennung, fuer die Fenster der Kerzen
    private val letzter = java.util.concurrent.ConcurrentHashMap<String, Long>()

    override suspend fun kurse(kennungen: List<String>): Map<String, Quote> {
        val ersatz = kennungen.mapNotNull { k -> Kursarchiv.ersatzSymbol(praefix, k)?.let { it to k } }.toMap()
        if (ersatz.isEmpty()) return emptyMap()
        val q = kurseAbruf(ersatz.keys.toList())
        val aus = HashMap<String, Quote>()
        for ((s, k) in ersatz) Kursarchiv.alsStand(KursQuellen.symbol(praefix, k), q[s])?.let { aus[it.symbol] = it; letzter[k] = it.marketTime }
        return aus
    }

    private suspend fun letzterStand(kennung: String): Long? = letzter[kennung] ?: kurse(listOf(kennung)).values.firstOrNull()?.marketTime

    override suspend fun kerzen(kennung: String, range: String, interval: String): Series {
        val s = Kursarchiv.ersatzSymbol(praefix, kennung) ?: return Series(emptyList())
        val ende = letzterStand(kennung) ?: return Series(emptyList())
        val (von, bis) = Kursarchiv.fensterSekunden(range, ende)
        return Series(kerzenAbruf(s, von, bis, Kursarchiv.intervall(interval)).candles)
    }

    override suspend fun kerzenZwischen(kennung: String, vonMs: Long, bisMs: Long, interval: String): Series {
        val s = Kursarchiv.ersatzSymbol(praefix, kennung) ?: return Series(emptyList())
        return Series(kerzenAbruf(s, vonMs / 1000L, bisMs / 1000L, Kursarchiv.intervall(interval)).candles.filter { it.t in vonMs..bisMs })
    }

    override suspend fun dividenden(kennung: String): List<MarketApi.DividendEvent> = emptyList()

    override suspend fun splits(kennung: String): List<Splits.Split> = emptyList()

    override suspend fun suche(text: String): List<MarketApi.SearchHit> = emptyList()

    // Wechselkurs der Handelswaehrung aus der allgemeinen Kursabfrage (Einheiten je Euro)
    override suspend fun wechselkurse(): Map<String, Double> {
        val paar = "EUR$waehrung=X"
        val k = kurseAbruf(listOf(paar))[paar]?.price?.takeIf { it.isFinite() && it > 0 } ?: return emptyMap()
        return mapOf(waehrung to k)
    }
}
