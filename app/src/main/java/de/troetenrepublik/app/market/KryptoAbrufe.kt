package de.troetenrepublik.app.market

import de.troetenrepublik.app.engine.KryptoHintergrund
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

// Abrufe zum Hintergrund der Kryptowerte (0.78, Rechenteil in engine/KryptoHintergrund.kt). Alles ohne Schluessel und
// ohne Angaben zur Person;
// Ergebnisse bleiben nur im Arbeitsspeicher. Nach einem Fehlschlag bleibt der letzte Stand sichtbar, ein neuer Versuch
// folgt fruehestens nach zehn Minuten.
class KryptoAbrufe(private val api: MarketApi) {

    private val kopf = mapOf("User-Agent" to MarketApi.BROWSER_UA, "Accept" to "application/json")

    private class Speicher<T>(val dauer: Long) {
        val sperre = Mutex()
        @Volatile var stand: Pair<Long, T>? = null
        @Volatile var fehler = 0L
    }

    private suspend fun <T> holen(s: Speicher<T>, laden: suspend () -> T?): T? = s.sperre.withLock {
        val now = System.currentTimeMillis()
        s.stand?.takeIf { now - it.first < s.dauer }?.let { return@withLock it.second }
        if (now - s.fehler < PAUSE) return@withLock s.stand?.second
        val wert = try { laden() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
        if (wert != null) { s.stand = now to wert; wert } else { s.fehler = now; s.stand?.second }
    }

    private suspend fun text(url: String, max: Int): String? = api.anfrage(url, kopf, max = max).takeIf { it.ok }?.text

    // ------------------------------------------------------------ Finanzierungssatz

    private val finanzierung = ConcurrentHashMap<String, Speicher<KryptoHintergrund.Finanzierung>>()

    // Finanzierungssatz und offene Kontrakte zu einem Wert, eine Stunde gespeichert. Erst die erste Boerse (Stand und
    // Verlauf, zwei Abrufe), ohne Ergebnis die zweite. Null, wenn keine der beiden den Kontrakt fuehrt.
    suspend fun finanzierung(symbol: String): KryptoHintergrund.Finanzierung? {
        val basis = KryptoHintergrund.basis(symbol) ?: return null
        return holen(finanzierung.getOrPut(basis) { Speicher(3_600_000L) }) { kraken(basis) ?: deribit(basis) }
    }

    private suspend fun kraken(basis: String): KryptoHintergrund.Finanzierung? {
        val stand = text(KryptoHintergrund.krakenStandAdresse(basis), 200_000)?.let(KryptoHintergrund::krakenStand) ?: return null
        val stunden = text(KryptoHintergrund.krakenVerlaufAdresse(basis), 4_000_000)
            ?.let { withContext(Dispatchers.Default) { KryptoHintergrund.krakenVerlauf(it) } }.orEmpty()
        return KryptoHintergrund.finanzierung(KryptoHintergrund.KRAKEN, stand, stunden)
    }

    private suspend fun deribit(basis: String): KryptoHintergrund.Finanzierung? {
        val stand = text(KryptoHintergrund.deribitStandAdresse(basis), 200_000)?.let { KryptoHintergrund.deribitStand(it, basis) } ?: return null
        val bis = System.currentTimeMillis()
        val stunden = text(KryptoHintergrund.deribitVerlaufAdresse(basis, bis - 42 * 86_400_000L, bis), 1_000_000)
            ?.let { withContext(Dispatchers.Default) { KryptoHintergrund.deribitVerlauf(it) } }.orEmpty()
        return KryptoHintergrund.finanzierung(KryptoHintergrund.DERIBIT, stand, stunden)
    }

    // ------------------------------------------------------------ Stablecoins und gebundenes Kapital

    private val stable = Speicher<List<Pair<Long, Double>>>(12 * 3_600_000L)
    private val tvl = ConcurrentHashMap<String, Speicher<List<Pair<Long, Double>>>>()

    // Umlauf aller Stablecoins je Tag ueber ein Jahr, zwoelf Stunden gespeichert (die Quelle rechnet einmal am Tag).
    suspend fun stablecoins(): List<Pair<Long, Double>>? = holen(stable) {
        text(KryptoHintergrund.STABLE_ADRESSE, 6_000_000)?.let { withContext(Dispatchers.Default) { KryptoHintergrund.stableLesen(it) } }?.takeIf { it.size >= 2 }
    }

    // Gebundenes Kapital je Tag ueber ein Jahr: gesamt (kette = null) oder fuer eine Kette.
    suspend fun tvl(kette: String? = null): List<Pair<Long, Double>>? = holen(tvl.getOrPut(kette.orEmpty()) { Speicher(12 * 3_600_000L) }) {
        text(kette?.let(KryptoHintergrund::tvlAdresse) ?: KryptoHintergrund.TVL_ADRESSE, 2_000_000)
            ?.let { withContext(Dispatchers.Default) { KryptoHintergrund.tvlLesen(it) } }?.takeIf { it.size >= 2 }
    }

    private companion object {
        const val PAUSE = 10 * 60_000L
    }
}
