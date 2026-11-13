package de.troetenrepublik.app

import de.troetenrepublik.app.engine.Aktienfinder
import de.troetenrepublik.app.engine.FinderVorlagen.Feld
import de.troetenrepublik.app.engine.KursScan
import de.troetenrepublik.app.market.Catalog
import de.troetenrepublik.app.plattform.SichereDatei
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

// Scan nach Kursmustern im Aktienfinder (0.78). Geprueft werden die groessten liquiden Werte des geladenen Markts
// (hoechstens KursScan.HOECHSTENS), dazu Depot und Watchlist. Kurse und Volumen fuer den Filter stammen aus dem
// Sammelabruf, den der Aktienfinder schon gemacht hat. Die Tagesschlusskurse eines Jahres kommen ueber den
// vorhandenen Stapelabruf: 20 Werte je Abruf, nacheinander mit kurzer Pause, sechs Stunden gemerkt. Ein Scan ueber
// 400 Werte braucht damit hoechstens 20 Abrufe und fuer bis zu 100 eigene Werte 5 weitere; ein zweiter Scan innerhalb
// von sechs Stunden keinen. Nach drei leeren Antworten in Folge endet der Lauf. Das Ergebnis je Markt liegt auf dem
// Geraet in kursmuster-scan.json.
object KursScanLauf {

    data class Zustand(
        val universum: String? = null,
        val stand: KursScan.Stand? = null,
        val fertig: Int = 0,
        val gesamt: Int = 0,
        val laeuft: Boolean = false,
        val ohneAntwort: Boolean = false,
    )

    val zustand = MutableStateFlow(Zustand())

    private const val REIHEN_MS = 6 * 3_600_000L
    private const val PAUSE_MS = 150L
    private const val ABBRUCH = 3
    private const val JE_ABRUF = 20

    private var lauf: Job? = null
    private val reihen = ConcurrentHashMap<String, Pair<Long, Pair<List<Long>, List<Double>>>>()
    private val sperre = Mutex()
    @Volatile private var staende: Map<String, KursScan.Stand>? = null

    private fun datei(state: AppState) = SichereDatei(File(state.store.ordner, "kursmuster-scan.json"))

    private suspend fun gespeichert(state: AppState): Map<String, KursScan.Stand> {
        staende?.let { return it }
        return sperre.withLock {
            staende ?: withContext(Dispatchers.IO) {
                runCatching { KursScan.lesen(datei(state).lesen().toString(Charsets.UTF_8)) }.getOrDefault(emptyMap())
            }.also { staende = it }
        }
    }

    // Gespeichertes Ergebnis des Markts zeigen; ein laufender Scan bleibt stehen.
    suspend fun lesen(state: AppState, universum: String) {
        val alle = gespeichert(state)
        zustand.update { if (it.laeuft || (it.universum == universum && it.stand != null)) it else Zustand(universum, alle[universum]) }
    }

    fun starten(state: AppState, universum: String, daten: List<Aktienfinder.Daten>, minKurs: Double, minVolumen: Double) {
        if (lauf?.isActive == true || daten.isEmpty()) return
        lauf = state.hintergrundScope.launch {
            try {
                kern(state, universum, daten, minKurs, minVolumen)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            } finally {
                zustand.update { it.copy(laeuft = false) }
            }
        }
    }

    private suspend fun kern(state: AppState, universum: String, daten: List<Aktienfinder.Daten>, minKurs: Double, minVolumen: Double) {
        // Eigene Werte mit Namen: Positionen ohne Hebelprodukte, dann die Watchlist
        val konto = state.account.value
        val katalog = Catalog.loaded()?.aktien?.associateBy { it.s }.orEmpty()
        val eigene = LinkedHashMap<String, String>()
        konto.positions.filter { !it.isDerivative }.forEach { eigene[it.symbol] = it.name }
        konto.watchlist.forEach { s -> if (s !in eigene) eigene[s] = katalog[s]?.n ?: state.quotes.value[s]?.name?.ifBlank { null } ?: s }
        val nachSymbol = daten.associateBy { it.symbol }
        val werte = KursScan.auswahl(
            daten.map { KursScan.Kandidat(it.symbol, it.werte[Feld.KURS], it.werte[Feld.VOLUMEN_SCHNITT], it.boersenwert) },
            eigene.keys.toList(), minKurs, minVolumen,
        )
        val start = System.currentTimeMillis()
        val offen = werte.filter { s -> reihen[s]?.takeIf { start - it.first < REIHEN_MS } == null }
        var fertig = werte.size - offen.size
        zustand.update { Zustand(universum, it.stand?.takeIf { s -> s.universum == universum }, fertig, werte.size, laeuft = true) }
        var leer = 0
        var erhalten = 0
        for (teil in offen.chunked(JE_ABRUF)) {
            val antwort = state.schlusskurse(teil, "1y")
            val jetzt = System.currentTimeMillis()
            for ((s, kerzen) in antwort) {
                val sauber = kerzen.filter { it.close.isFinite() && it.close > 0 }
                reihen[s] = jetzt to (sauber.map { it.t } to sauber.map { it.close })
                erhalten++
            }
            if (antwort.isEmpty()) { if (++leer >= ABBRUCH) break } else leer = 0
            fertig += teil.size
            val f = fertig
            zustand.update { it.copy(fertig = f) }
            delay(PAUSE_MS)
        }
        // Kam fuer keinen offenen Wert etwas, ist das ein Ausfall; das letzte Ergebnis bleibt stehen
        if (offen.isNotEmpty() && erhalten == 0) {
            zustand.update { it.copy(laeuft = false, ohneAntwort = true) }
            return
        }
        val jetzt = System.currentTimeMillis()
        val stand = withContext(Dispatchers.Default) {
            val da = werte.mapNotNull { s -> reihen[s]?.takeIf { jetzt - it.first < REIHEN_MS }?.second?.takeIf { it.second.size >= KursScan.MIN_KURSE }?.let { s to it } }
            val kurse = state.quotes.value
            val treffer = da.flatMap { (s, r) ->
                KursScan.trefferVon(
                    s, nachSymbol[s]?.name ?: eigene[s] ?: s, r.second,
                    nachSymbol[s]?.werte?.get(Feld.KURS) ?: kurse[s]?.price?.takeIf { it > 0 },
                )
            }
            KursScan.Stand(
                universum, jetzt, minKurs, minVolumen, geprueft = da.size, ohneKurse = werte.size - da.size, treffer = treffer,
                bilanz = KursScan.rueckblick(da.map { it.second }).mapKeys { it.key.name },
            )
        }
        val alle = gespeichert(state) + (universum to stand)
        staende = alle
        withContext(Dispatchers.IO) { runCatching { datei(state).schreiben(KursScan.schreiben(alle, jetzt).toByteArray(Charsets.UTF_8)) } }
        zustand.value = Zustand(universum, stand)
    }
}
