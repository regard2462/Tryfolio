package de.troetenrepublik.app.market

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

// [Globus Boersen 0.73] Haelt den Stand des Leitindex je Land: Yahoo-Werte und Selbstabruf kommen von aussen (eintragen),
// die direkten Boersen holt der Lader im Hintergrund. Hoechstens vier Abrufe gleichzeitig, je Abruf ein kurzes Zeitlimit; eine
// gescheiterte Quelle wird einzeln nach wachsender Pause neu versucht, ohne die anderen aufzuhalten. Jeder Zustand steht
// sofort in staende und im Zwischenspeicher (mit Zeitpunkt des Abrufs und Datum des Kurses je Wert).
class IndexLader(
    private val scope: CoroutineScope,
    private val heute: () -> LocalDate,
    private val jetzt: () -> Long,
    private val lesen: suspend () -> String?,
    private val schreiben: suspend (String) -> Unit,
    private val holen: suspend (BoersenDirekt.Quelle) -> String?,
    private val quellen: List<BoersenDirekt.Quelle> = BoersenDirekt.QUELLEN,
    private val ersteMs: Long = 20_000L,
    private val gueltigMs: Long = 30 * 60_000L,
    private val parallel: Int = 4,
) {
    companion object {
        const val MAX_FEHLER = 5
        const val MIN_STUMM = 3
        const val MAX_PAUSE_MS = 600_000L
        private const val MAX_RUNDEN = 40
        // Einzelwerte ueber diese Tagesaenderung gelten als Lesefehler der Quelle
        const val MAX_PROZENT = 50.0
        private const val AUFBEWAHREN_MS = 60L * 24 * 3_600_000L
    }

    private class Plan(@Volatile var naechster: Long = 0L, @Volatile var fehler: Int = 0)

    private val alle = HashMap<String, HashMap<String, IndexStand.Wert>>()
    private val sperre = Mutex()
    @Volatile private var gelesen = false
    private val plaene = ConcurrentHashMap<String, Plan>()
    @Volatile private var job: Job? = null

    private val _staende = MutableStateFlow<Map<String, IndexStand.Wert>>(emptyMap())
    // Gueltiger Wert je Land, auch ein alter (er wird grau mit Datum gezeigt)
    val staende: StateFlow<Map<String, IndexStand.Wert>> = _staende

    private val _stumm = MutableStateFlow<Set<String>>(emptySet())
    // Laender, deren Quelle mehrfach nicht geantwortet hat (Netzfilter, VPN, Ausfall)
    val stumm: StateFlow<Set<String>> = _stumm

    private fun stummAktualisieren() {
        _stumm.value = quellen.filter { (plaene[it.id]?.fehler ?: 0) >= MIN_STUMM }.flatMap { it.laender }.toSet()
    }

    // Frische Tagesaenderungen je Land fuer die Einfaerbung
    fun prozente(): Map<String, Double> = IndexStand.frischeProzente(_staende.value, heute())

    suspend fun laden() = sperre.withLock { ladenOhneSperre() }

    private suspend fun ladenOhneSperre() {
        if (gelesen) return
        gelesen = true
        val text = runCatching { lesen() }.getOrNull().orEmpty()
        for ((land, werte) in IndexStand.lesen(text)) alle.getOrPut(land) { HashMap() }.putAll(werte)
        for (q in quellen) {
            val letzter = q.laender.flatMap { alle[it]?.get("direkt:" + q.id)?.let { w -> listOf(w.abruf) }.orEmpty() }.maxOrNull() ?: continue
            plaene.getOrPut(q.id) { Plan() }.naechster = letzter + gueltigMs
        }
        _staende.value = IndexStand.zusammenfuehren(alle, heute())
    }

    // Werte einer Quelle eintragen (Yahoo, Selbstabruf): ersetzt den Wert gleicher Herkunft je Land
    suspend fun eintragen(werte: Collection<IndexStand.Wert>) {
        if (werte.isEmpty()) return
        sperre.withLock {
            ladenOhneSperre()
            for (w in werte) alle.getOrPut(w.land) { HashMap() }[w.quelle] = w
            veroeffentlichen()
        }
    }

    private suspend fun veroeffentlichen() {
        val grenze = jetzt() - AUFBEWAHREN_MS
        for (m in alle.values) m.values.removeAll { it.abruf < grenze }
        alle.values.removeAll { it.isEmpty() }
        _staende.value = IndexStand.zusammenfuehren(alle, heute())
        runCatching { schreiben(IndexStand.schreiben(alle)) }
    }

    // Direkte Boersen im Hintergrund holen; laeuft schon ein Lauf, passiert nichts. Quellen, die noch gueltig sind, ruhen.
    fun anstossen() {
        if (job?.isActive == true) return
        job = scope.launch {
            laden()
            val jetzt0 = jetzt()
            for (p in plaene.values) if (p.fehler >= MAX_FEHLER && p.naechster <= jetzt0) p.fehler = MAX_FEHLER - 1
            val grenze = Semaphore(parallel)
            // Obergrenze der Runden: ein stehengebliebener Zeitgeber haelt den Lauf nie endlos
            var runden = 0
            while (runden++ < MAX_RUNDEN) {
                val t = jetzt()
                val faellig = quellen.filter { q -> plaene[q.id].let { it == null || (it.naechster <= t && it.fehler < MAX_FEHLER) } }
                if (faellig.isEmpty()) {
                    val warten = plaene.values.filter { it.fehler in 1 until MAX_FEHLER }.minOfOrNull { it.naechster } ?: break
                    delay((warten - t).coerceAtLeast(1L))
                    continue
                }
                coroutineScope { faellig.map { q -> async { grenze.withPermit { eine(q) } } }.awaitAll() }
            }
        }
    }

    private suspend fun eine(q: BoersenDirekt.Quelle) {
        val plan = plaene.getOrPut(q.id) { Plan() }
        val stand = try {
            withTimeoutOrNull(q.zeitMs) { holen(q) }?.let { text -> runCatching { q.parse(text) }.getOrNull() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        val t = jetzt()
        if (stand == null || !stand.prozent.isFinite() || kotlin.math.abs(stand.prozent) > MAX_PROZENT) {
            plan.fehler++
            plan.naechster = t + minOf(ersteMs shl (plan.fehler - 1).coerceAtMost(20), MAX_PAUSE_MS)
            stummAktualisieren()
            return
        }
        plan.fehler = 0
        plan.naechster = t + gueltigMs
        stummAktualisieren()
        val heute = heute()
        val datum = stand.datum?.takeIf { !it.isAfter(heute) } ?: heute
        eintragen(q.laender.map { IndexStand.Wert(it, "direkt:" + q.id, stand.prozent, stand.wert, datum, t) })
    }

    // Fuer Tests: Fehlversuche und naechster Versuch je Quelle
    internal fun fehlerVon(id: String): Int = plaene[id]?.fehler ?: 0

    internal fun warteJob(): Job? = job
}
