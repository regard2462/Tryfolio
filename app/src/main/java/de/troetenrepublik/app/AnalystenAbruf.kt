package de.troetenrepublik.app

import de.troetenrepublik.app.engine.Aktienkennzahlen
import de.troetenrepublik.app.engine.Analystenbilanz
import de.troetenrepublik.app.market.Gleichwerte
import de.troetenrepublik.app.market.MarketApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

// Analystenurteile einer Aktie mit Trefferbilanz (5.92). quelle ist die Notierung, von der die Urteile stammen,
// indexName der Vergleichsindex der Bilanz (null ohne Index oder ohne Urteilsverlauf).
data class AnalystenDaten(
    val verlauf: MarketApi.Analystenverlauf,
    val bilanz: Analystenbilanz.Ergebnis?,
    val indexName: String?,
    val quelle: String,
)

private val analystenSpeicher = ConcurrentHashMap<String, Pair<Long, AnalystenDaten>>()

// Schon geholte Analystendaten ohne neuen Abruf, etwa fuer den Aktienfinder; null, solange keine da sind.
fun analystenGemerkt(symbol: String): AnalystenDaten? = analystenSpeicher[symbol]?.second

// Zwoelf Stunden zwischengespeichert. Fuehrt Yahoo den Verlauf nur bei einer anderen Notierung derselben Aktie (meist
// beim Heimatplatz), kommen Urteile und Kurse von dort. land: Sitzland laut Profil fuer den Vergleichsindex.
suspend fun AppState.analystenDaten(symbol: String, land: String?): AnalystenDaten? {
    val jetzt = System.currentTimeMillis()
    analystenSpeicher[symbol]?.takeIf { jetzt - it.first < 12 * 3_600_000L }?.let { return it.second }
    val kandidaten = listOf(symbol) + (Gleichwerte.familien.value[symbol].orEmpty() - symbol).take(3)
    var bestes: Pair<String, MarketApi.Analystenverlauf>? = null
    for (s in kandidaten) {
        val v = runCatching { market.analysten(s) }.getOrNull() ?: continue
        if (bestes == null || v.urteile.size > bestes.second.urteile.size) bestes = s to v
        if (v.urteile.isNotEmpty()) break
    }
    val (quelle, verlauf) = bestes ?: return analystenSpeicher[symbol]?.second
    var indexName: String? = null
    val bilanz = if (verlauf.urteile.isEmpty()) null else {
        val kurse = dailyCloses(quelle)
        val idx = Aktienkennzahlen.index(quelle, land)
        val index = idx?.let { dailyCloses(it.first).takeIf { k -> k.size > 60 } }
        if (index != null) indexName = idx.second
        val kurs = quotes.value[quelle]?.nativePrice ?: 0.0
        if (kurse.size < 60) null
        else withContext(Dispatchers.Default) { Analystenbilanz.auswerten(verlauf.urteile, kurse, index, kurs, jetzt) }
    }
    val aus = AnalystenDaten(verlauf, bilanz, indexName, quelle)
    analystenSpeicher[symbol] = jetzt to aus
    return aus
}
