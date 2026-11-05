package de.troetenrepublik.app.market

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Wie runCatching, reicht aber einen Abbruch des Aufrufers weiter, damit er nicht als Fehler gezaehlt wird
inline fun <T> ohneAbbruch(block: () -> T): Result<T> =
    runCatching(block).onFailure { if (it is CancellationException) throw it }

// Abrufe der Nasdaq: Zwischenspeicher je Adresse als unveraenderliche Map, frische Treffer werden ohne
// Lock gelesen und kehren ohne Warten zurueck. Pause nach Fehlschlaegen fuer alle Adressen
// (15 Minuten, je weiterem Fehlschlag verdoppelt, hoechstens vier Stunden) und Mindestabstand zwischen
// Abrufen. Abbruch des Aufrufers wird weitergereicht, zaehlt nicht als Fehlschlag und setzt keine Pause.
class NasdaqAbruf(
    private val netz: suspend (String) -> MarketApi.Antwort,
    private val uhr: () -> Long = { System.currentTimeMillis() },
    private val abstandMs: Long = 250L,
) {
    @Volatile private var speicher: Map<String, Pair<Long, Any>> = emptyMap()
    private val lock = Mutex()
    @Volatile private var pauseBis = 0L
    private var fehler = 0
    private var letzterAbruf = 0L

    // true, solange nach Fehlern keine Anfrage geht
    fun pausiert(): Boolean = uhr() < pauseBis

    suspend fun <T : Any> holen(adresse: String, haltbar: (abgerufenMs: Long) -> Long, lesen: (String) -> T?): T? {
        // frische Antwort direkt zurueckgeben, ohne auf laufende Abrufe zu warten
        @Suppress("UNCHECKED_CAST")
        speicher[adresse]?.takeIf { uhr() - it.first < haltbar(it.first) }?.let { return it.second as T }
        return lock.withLock {
            // ein anderer Aufruf kann inzwischen gespeichert haben
            @Suppress("UNCHECKED_CAST")
            val frisch = speicher[adresse]?.takeIf { uhr() - it.first < haltbar(it.first) }?.second as T?
            if (frisch != null) return@withLock frisch
            @Suppress("UNCHECKED_CAST")
            val alter = speicher[adresse]?.second as T?
            // in der Pause kein neuer Abruf, nur der alte Stand (auch abgelaufen) oder null
            if (uhr() < pauseBis) return@withLock alter
            if (abstandMs > 0) {
                val warten = abstandMs - (uhr() - letzterAbruf)
                if (warten > 0) delay(warten)
            }
            val abgerufen = uhr()
            letzterAbruf = abgerufen
            val antwort = try {
                netz(adresse)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                MarketApi.Antwort(0, null)
            }
            val gelesen = if (antwort.ok) antwort.text?.let(lesen) else null
            if (gelesen != null) {
                fehler = 0
                speicher = speicher + (adresse to (abgerufen to gelesen))
                return@withLock gelesen
            }
            fehler += 1
            pauseBis = uhr() + (15L shl (fehler - 1).coerceAtMost(4)) * 60_000L
            alter
        }
    }
}
