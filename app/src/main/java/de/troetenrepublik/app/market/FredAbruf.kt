package de.troetenrepublik.app.market

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

// Abrufe von FRED und ALFRED: Die Bedingungen verbieten Speichern und Zwischenspeichern. Gleichzeitige Anfragen mit
// demselben Schluessel (mehrere Teile einer Ansicht bauen sich zugleich auf) teilen sich einen Abruf; nach seinem Ende
// bleibt nichts zurueck, die naechste Anfrage ruft neu ab.
object FredAbruf {
    private val laufend = HashMap<String, CompletableDeferred<Any?>>()

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> geteilt(schluessel: String, laden: suspend () -> T): T {
        while (true) {
            val (d, selbst) = synchronized(laufend) {
                laufend[schluessel]?.let { it to false } ?: (CompletableDeferred<Any?>().also { laufend[schluessel] = it } to true)
            }
            if (selbst) {
                try {
                    val e = laden()
                    d.complete(e)
                    return e
                } catch (t: Throwable) {
                    d.completeExceptionally(t)
                    throw t
                } finally {
                    synchronized(laufend) { if (laufend[schluessel] === d) laufend.remove(schluessel) }
                }
            }
            val r = runCatching { d.await() }
            if (r.isSuccess) return r.getOrThrow() as T
            val f = r.exceptionOrNull()
            // Brach der erste Abruf ab, versucht dieser selbst; jeder andere Fehler gilt auch hier
            if (f is CancellationException && currentCoroutineContext().isActive) continue
            throw f!!
        }
    }

    // Zahl der gerade laufenden geteilten Abrufe (fuer Tests: nach dem Ende ist es null)
    internal fun laufende(): Int = synchronized(laufend) { laufend.size }
}
