package de.troetenrepublik.app.market

import de.troetenrepublik.app.engine.UkRegister
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Abruf der gemeldeten Beteiligungen aus dem Register des britischen Unterhauses (0.78, Leser in engine/UkRegister.kt).
// Ohne Schluessel und ohne Angaben zur Person. Das ganze Register der Kategorie (rund 200 Eintraege, 20 je Seite)
// bleibt einen Tag im Arbeitsspeicher; nach einem Fehlschlag folgt ein neuer Versuch fruehestens nach zehn Minuten.
class UkRegisterAbruf(private val api: MarketApi) {

    private val kopf = mapOf("User-Agent" to MarketApi.BROWSER_UA, "Accept" to "application/json")
    private val sperre = Mutex()
    @Volatile private var stand: Pair<Long, List<UkRegister.Beteiligung>>? = null
    @Volatile private var fehler = 0L

    suspend fun beteiligungen(): List<UkRegister.Beteiligung>? = sperre.withLock {
        val now = System.currentTimeMillis()
        stand?.takeIf { now - it.first < DAUER }?.let { return@withLock it.second }
        if (now - fehler < PAUSE) return@withLock stand?.second
        val neu = try { laden() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
        if (neu != null) { stand = now to neu; neu } else { fehler = now; stand?.second }
    }

    // Seite fuer Seite; bricht eine Seite ab, gilt der Abruf als fehlgeschlagen (kein halbes Register).
    private suspend fun laden(): List<UkRegister.Beteiligung>? {
        val alle = ArrayList<UkRegister.Beteiligung>()
        var skip = 0
        var gesamt = Int.MAX_VALUE
        var seiten = 0
        while (skip < gesamt && seiten++ < SEITEN_MAX) {
            val seite = api.anfrage(UkRegister.adresse(skip), kopf, max = 1_000_000).takeIf { it.ok }?.text?.let(UkRegister::seiteLesen) ?: return null
            gesamt = seite.gesamt
            alle.addAll(seite.eintraege)
            if (seite.gelesen == 0) break
            skip += seite.gelesen
        }
        return UkRegister.sortiert(alle.distinctBy { it.id })
    }

    private companion object {
        const val DAUER = 24 * 3_600_000L
        const val PAUSE = 10 * 60_000L
        const val SEITEN_MAX = 25
    }
}
