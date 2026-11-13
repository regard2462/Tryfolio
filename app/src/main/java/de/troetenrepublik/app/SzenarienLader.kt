package de.troetenrepublik.app

import de.troetenrepublik.app.engine.Empfindlichkeit.Faktor
import de.troetenrepublik.app.engine.ExchangeRates
import de.troetenrepublik.app.engine.Szenarien
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

// [Szenarien 0.74] Messungen fuer die Rechenbeispiele "Was waere, wenn": je Position die belegten Steigungen gegen die
// Einflussgroessen, gerechnet aus den Tageskerzen der Kursquelle (zwischengespeichert wie die Kerzen selbst). Das Ergebnis
// bleibt fuer die Sitzung gemerkt, damit das Blatt beim erneuten Oeffnen sofort steht.
object SzenarienMerk {
    // Hoechstens so viele Positionen (nach Marktwert) werden gemessen; der Rest zaehlt ohne belegten Zusammenhang
    const val MAX_POSITIONEN = 20
    private const val GUELTIG_MS = 6L * 3_600_000L
    private val sperre = Mutex()
    private val speicher = HashMap<String, Pair<Long, Szenarien.Messung>>()

    suspend fun gemerkt(symbol: String, jetzt: Long): Szenarien.Messung? = sperre.withLock {
        speicher[symbol]?.takeIf { jetzt - it.first < GUELTIG_MS }?.second
    }

    suspend fun merken(symbol: String, jetzt: Long, m: Szenarien.Messung) = sperre.withLock { speicher[symbol] = jetzt to m }
}

// Messungen der angegebenen Positionen (symbols nach Marktwert absteigend). fortschritt(erledigt, gesamt) wird nach jeder
// Position gerufen. Positionen mit zu kurzer Reihe fehlen im Ergebnis.
suspend fun AppState.szenarienMessungen(symbols: List<String>, fortschritt: (Int, Int) -> Unit): Map<String, Szenarien.Messung> = coroutineScope {
    val wahl = symbols.distinct().take(SzenarienMerk.MAX_POSITIONEN)
    val jetzt = System.currentTimeMillis()
    val aus = HashMap<String, Szenarien.Messung>()
    val offen = ArrayList<String>()
    for (s in wahl) SzenarienMerk.gemerkt(s, jetzt)?.let { aus[s] = it } ?: run { offen += s }
    fortschritt(aus.size, wahl.size)
    if (offen.isEmpty()) return@coroutineScope aus

    val markt = async { kurseNativ(Faktor.MARKT.symbol) }
    val groessen = Szenarien.GROESSEN.map { f -> f to async { kurseNativ(f.symbol) } }
    val m = markt.await()
    val reihen = groessen.mapNotNull { (f, a) -> a.await().takeIf { it.size >= 200 }?.let { f to it } }.toMap()
    // Handelswaehrung je Position und je Waehrung einmal der Verlauf des Wechselkurses zum Dollar
    val waehrungen = runCatching { market.snapshot(offen) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
        .getOrDefault(emptyMap()).mapValues { it.value.nativeCurrency }
    val fxSperre = Mutex()
    val fxReihen = HashMap<String, List<de.troetenrepublik.app.data.Candle>>()
    suspend fun wechselkurs(haupt: String): List<de.troetenrepublik.app.data.Candle> = fxSperre.withLock {
        fxReihen.getOrPut(haupt) { kurseNativ(haupt + "USD=X") }
    }
    var erledigt = aus.size
    val gleichzeitig = Semaphore(3)
    val mess = Mutex()
    offen.map { s ->
        async {
            val messung = gleichzeitig.withPermit {
                val w = kurseNativ(s)
                // Werte in anderer Waehrung: der Wechselkurs zum Dollar geht als weitere Reihe in die Schaetzung; ohne ihn
                // gibt es keine Messung. Der Dollar selbst zaehlt dort nicht als Groesse (er liefe mit dem Wechselkurs gleich).
                val haupt = waehrungen[s]?.takeIf { it.isNotBlank() }?.let { ExchangeRates.hauptwaehrung(it) }
                val fx = if (haupt == null || haupt == "USD") null else wechselkurs(haupt).takeIf { it.size >= 200 }
                if (haupt == null || (haupt != "USD" && fx == null) || w.size < 200 || m.size < 200) null else withContext(Dispatchers.Default) {
                    Szenarien.messen(w, m, if (fx == null) reihen else reihen - Faktor.DOLLAR, fx)
                }
            }
            mess.withLock {
                if (messung != null) aus[s] = messung
                erledigt++
                fortschritt(erledigt, wahl.size)
            }
            if (messung != null) SzenarienMerk.merken(s, jetzt, messung)
        }
    }.awaitAll()
    aus
}
