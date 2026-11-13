package de.troetenrepublik.app.market

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

// Abruf der Verguetung je Firma [0.77]: hoechstens vier kleine Abfragen bei data.sec.gov mit der SEC-Kennung, die
// erste (Durchschnitt der uebrigen Vorstaende) entscheidet, ob die Firma die Tabelle ueberhaupt meldet. Ergebnisse
// bleiben zwoelf Stunden im Speicher, auch „keine Daten“; Netzfehler werden nicht gemerkt.
object VerguetungAbruf {
    private val api by lazy { MarketApi() }
    private val sperre = Mutex()
    private val speicher = ConcurrentHashMap<String, Pair<Long, Verguetung.Ergebnis?>>()
    private const val GUELTIG_MS = 12 * 3_600_000L

    private class Fehler : Exception()

    // Reihe eines Begriffs; leer bei 404 (Begriff nicht gemeldet), Fehler sonst.
    private suspend fun reihe(cik: String, begriff: String, kopf: Map<String, String>): List<Verguetung.Punkt> {
        delay(200)
        val a = api.anfrage(Verguetung.adresse(cik, begriff), kopf, max = 1_000_000)
        return when {
            a.ok -> Verguetung.reihe(a.text.orEmpty())
            a.code == 404 -> emptyList()
            else -> throw Fehler()
        }
    }

    // null: keine Daten oder Abruf gescheitert.
    suspend fun laden(cik: String, kennung: String, jetzt: Long = System.currentTimeMillis()): Verguetung.Ergebnis? = sperre.withLock {
        val bekannt = speicher[cik]
        if (bekannt != null && jetzt - bekannt.first < GUELTIG_MS) return@withLock bekannt.second
        val kopf = mapOf("User-Agent" to kennung, "Accept" to "application/json")
        val ergebnis = try {
            val uebrige = reihe(cik, Verguetung.UEBRIGE, kopf)
            if (uebrige.isEmpty()) null
            else {
                val ceo = reihe(cik, Verguetung.CEO, kopf)
                if (ceo.isEmpty()) null
                else Verguetung.auswerten(ceo, reihe(cik, Verguetung.ZUGEFLOSSEN, kopf), uebrige, reihe(cik, Verguetung.RENDITE, kopf))
            }
        } catch (e: Fehler) {
            return@withLock null
        }
        speicher[cik] = jetzt to ergebnis
        ergebnis
    }
}
