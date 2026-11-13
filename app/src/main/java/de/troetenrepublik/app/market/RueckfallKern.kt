package de.troetenrepublik.app.market

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Gemeinsamer Kern der Kursquellen mit Schluessel der Person (Tiingo, Twelve Data), im Muster von MassiveQuelle:
// stumm ohne Schluessel, Schluessel nur als Kopfzeile, Kontingent ueber das Abrufkontingent (ist es aufgebraucht,
// antwortet die Quelle nicht, statt zu warten), Antworten sechs Stunden im Arbeitsspeicher. 401 heisst Schluessel
// abgelehnt, 429 Pause, 400/403/404 der Anbieter kennt den Wert nicht. abruf: Adresse und Kopfzeilen, Antwort mit Status.
abstract class RueckfallKern<D : Any>(
    private val abruf: suspend (String, Map<String, String>) -> MarketApi.Antwort,
    private val schluessel: () -> String,
    private val kontingent: () -> Abrufkontingent?,
    protected val uhr: () -> Long,
    private val gruppe: String,
) {
    companion object {
        const val TAG = 86_400_000L
        const val HALTEN_MS = 6 * 3_600_000L
        const val NETZ_PAUSE_MS = 5 * 60_000L
        const val LIMIT_PAUSE_MS = 60_000L
    }

    protected class Eintrag<T>(val zeit: Long, val wert: T)

    // Antwort eines Abrufs: text bei Erfolg; fest = der Anbieter hat geantwortet, aber ohne Daten fuer diese Anfrage
    protected class Ergebnis(val text: String?, val fest: Boolean)

    protected val sperre = Mutex()
    private val reihen = HashMap<String, Eintrag<D>>()
    private val eigenes by lazy { Abrufkontingent(null, uhr) }
    private var gemerktFuer = ""
    private var abgelehnt = ""
    private var pauseBis = 0L

    // Letzte Meldung fuer die Seite Zugaenge: NewsRepository.SCHLUESSEL, NewsRepository.LIMIT oder null
    @Volatile var meldung: String? = null
        protected set

    // Kopfzeilen mit dem Schluessel
    protected abstract fun kopf(schluessel: String): Map<String, String>

    // Status der Antwort; Anbieter, die Fehler in einer Antwort mit Status 200 melden, ueberschreiben das
    protected open fun status(a: MarketApi.Antwort): Int = a.code

    // Beim Wechsel des Schluessels: weitere Zwischenspeicher der Unterklasse leeren
    protected open fun verwerfen() {}

    // Gilt nur unter der Sperre. Ein anderer oder entfernter Schluessel verwirft alles Gemerkte.
    protected fun schluesselJetzt(): String? {
        val s = schluessel().trim()
        if (s != gemerktFuer) {
            reihen.clear(); verwerfen(); gemerktFuer = s; pauseBis = 0L; meldung = null
        }
        return s.takeIf { it.isNotEmpty() && it != abgelehnt }
    }

    // Gilt nur unter der Sperre
    protected suspend fun holen(adresse: String, s: String): Ergebnis {
        val jetzt = uhr()
        if (jetzt < pauseBis) return Ergebnis(null, false)
        val k = kontingent() ?: eigenes
        if (!k.erlaubt(gruppe)) return Ergebnis(null, false)
        k.buchen(gruppe)
        val a = try { abruf(adresse, kopf(s)) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        val c = if (a == null) 0 else status(a)
        return when {
            a == null || c == 0 -> { pauseBis = jetzt + NETZ_PAUSE_MS; Ergebnis(null, false) }
            c == 401 -> { abgelehnt = s; meldung = NewsRepository.SCHLUESSEL; Ergebnis(null, false) }
            c == 429 -> { pauseBis = jetzt + LIMIT_PAUSE_MS; meldung = NewsRepository.LIMIT; Ergebnis(null, false) }
            c == 400 || c == 403 || c == 404 -> { meldung = null; Ergebnis(null, true) }
            c in 200..299 && a.text != null -> { meldung = null; Ergebnis(a.text, false) }
            else -> { pauseBis = jetzt + NETZ_PAUSE_MS; Ergebnis(null, false) }
        }
    }

    // Ein Abruf unter der Sperre mit gueltigem Schluessel; null ohne Schluessel
    protected suspend fun <T> mitSchluessel(block: suspend (String) -> T?): T? = sperre.withLock {
        val s = schluesselJetzt() ?: return@withLock null
        block(s)
    }

    // Die Daten zu einem Kuerzel der Quelle: aus dem Speicher, solange sie frisch sind, sonst geladen. null ohne
    // Schluessel, ohne Kontingent oder ohne Antwort.
    protected suspend fun daten(ticker: String, laden: suspend (String) -> D?): D? = sperre.withLock {
        val s = schluesselJetzt() ?: return@withLock null
        val jetzt = uhr()
        reihen[ticker]?.takeIf { jetzt - it.zeit < HALTEN_MS }?.let { return@withLock it.wert }
        laden(s)?.also { reihen[ticker] = Eintrag(jetzt, it) }
    }
}
