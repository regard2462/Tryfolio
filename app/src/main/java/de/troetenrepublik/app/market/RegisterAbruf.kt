package de.troetenrepublik.app.market

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

// Holt die offenen US-Register einer Firma (klinische Studien, Fahrzeugrueckrufe, Produktrueckrufe) und merkt sich
// jedes Ergebnis einen Tag im Speicher. Ein Ergebnis ohne Treffer ist ein gueltiges Ergebnis (leer) und wird gemerkt,
// null heisst: keine einzige Antwort, spaeter noch einmal versuchen. Die Seite laesst leere Abschnitte weg.
class RegisterAbruf(private val api: MarketApi) {

    private val gemerkt = ConcurrentHashMap<String, Pair<Long, Any>>()

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> merken(schluessel: String, laden: suspend () -> T?): T? {
        gemerkt[schluessel]?.takeIf { System.currentTimeMillis() - it.first < TAG_MS }?.let { return it.second as T }
        val v = runCatching { laden() }.getOrNull()
        if (v != null) gemerkt[schluessel] = System.currentTimeMillis() to v
        return v
    }

    private fun heute() = LocalDate.now(ZoneOffset.UTC)

    private suspend fun text(url: String): String? = api.anfrage(url).takeIf { it.ok }?.text

    // Klinische Studien: je Name zwei Abrufe (laufend, zuletzt beendet), hoechstens drei Namen.
    suspend fun studien(firma: String): Studien.Ergebnis? = merken("studien|$firma") {
        val namen = Studien.namen(firma).take(3)
        if (namen.isEmpty()) return@merken null
        val h = heute()
        val von = h.minusYears(1).toString()
        val bis = h.toString()
        coroutineScope {
            val suchen = RegisterName.suchbegriffe(namen)
            val a = suchen.map { s -> async { text(Studien.laufendAdresse(s))?.let { Studien.lesen(it, namen) } } }
            val b = suchen.map { s -> async { text(Studien.fertigAdresse(s, von, bis))?.let { Studien.lesen(it, namen) } } }
            val la = a.awaitAll()
            val fe = b.awaitAll()
            if (la.all { it == null } && fe.all { it == null }) null
            else Studien.Ergebnis(la.filterNotNull().flatten().distinctBy { it.nct }, fe.filterNotNull().flatten().distinctBy { it.nct })
        }
    }

    private class NhtsaAlle(val rueckrufe: List<Fahrzeugrueckrufe.Rueckruf>, val untersuchungen: List<Fahrzeugrueckrufe.Untersuchung>)

    // Alle Rueckrufe der letzten 24 Monate und die juengsten Untersuchungen aller Hersteller, einmal je Tag. Die Listen
    // sind nach Datum sortiert und nicht filterbar; es wird in Vierergruppen geholt, bis die Seite hinter dem Stichtag endet.
    private suspend fun nhtsaAlle(): NhtsaAlle? = merken("nhtsa|alle") {
        val seit = heute().minusMonths(24).toString()
        val rueck = ArrayList<Fahrzeugrueckrufe.Rueckruf>()
        var seite = 0
        var fertig = false
        while (!fertig && seite < Fahrzeugrueckrufe.MAX_SEITEN_RUECKRUFE) {
            val gruppe = coroutineScope {
                (seite until seite + 4).map { s -> async { text(Fahrzeugrueckrufe.rueckrufAdresse(s * 100))?.let { Fahrzeugrueckrufe.rueckrufeLesen(it) } } }.awaitAll()
            }
            // Eine fehlende Seite macht die Liste unvollstaendig: nichts merken
            if (gruppe.any { it == null }) return@merken null
            gruppe.filterNotNull().forEach { rueck.addAll(it) }
            fertig = gruppe.any { it!!.isEmpty() || it.last().tag < seit }
            seite += 4
        }
        val unter = coroutineScope {
            (0 until Fahrzeugrueckrufe.SEITEN_UNTERSUCHUNGEN).map { s -> async { text(Fahrzeugrueckrufe.untersuchungAdresse(s * 100))?.let { Fahrzeugrueckrufe.untersuchungenLesen(it) } } }.awaitAll()
        }
        if (unter.any { it == null }) return@merken null
        NhtsaAlle(rueck.filter { it.tag >= seit }, unter.filterNotNull().flatten())
    }

    suspend fun fahrzeuge(firma: String): Fahrzeugrueckrufe.Ergebnis? {
        val namen = Fahrzeugrueckrufe.namen(firma)
        if (namen.isEmpty()) return null
        val alle = nhtsaAlle() ?: return null
        val seit = heute().minusMonths(24).toString()
        return Fahrzeugrueckrufe.Ergebnis(
            Fahrzeugrueckrufe.rueckrufeFuer(alle.rueckrufe, namen, seit),
            Fahrzeugrueckrufe.untersuchungenFuer(alle.untersuchungen, namen),
        )
    }

    // Produktrueckrufe der letzten 24 Monate: je Suchbegriff ein Abruf im Herstellerfeld und einer im Titel.
    suspend fun produkte(firma: String): List<Produktrueckrufe.Rueckruf>? = merken("cpsc|$firma") {
        val namen = Produktrueckrufe.namen(firma).take(3)
        if (namen.isEmpty()) return@merken null
        val seit = heute().minusMonths(24).toString()
        coroutineScope {
            val suchen = Produktrueckrufe.suchbegriffe(namen)
            val a = suchen.flatMap { s ->
                listOf(Produktrueckrufe.herstellerAdresse(s, seit), Produktrueckrufe.titelAdresse(s, seit)).map { u ->
                    async { text(u)?.let { Produktrueckrufe.lesen(it, namen, seit) } }
                }
            }.awaitAll()
            if (a.all { it == null }) null
            else a.filterNotNull().flatten().distinctBy { it.id }.sortedByDescending { it.tag }
        }
    }

    private companion object {
        const val TAG_MS = 24 * 3_600_000L
    }
}
