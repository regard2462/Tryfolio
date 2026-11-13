package de.troetenrepublik.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Zustand der Karten einer Wertpapierseite, der das Herausscrollen ueberdauert (0.68). Die Seite ist eine LazyColumn:
// Verlaesst eine Karte das Bild, verwirft Compose ihren remember-Zustand. Ohne Merker beginnt sie beim Zurueckscrollen
// leer, laedt neu und springt dann auf ihre volle Hoehe. Die Seite haelt einen Merker je Wert.
@Stable
internal class SeitenMerker {
    private val zustaende = HashMap<String, MutableState<*>>()

    @Suppress("UNCHECKED_CAST")
    fun <T> zustand(schluessel: String, start: () -> T): MutableState<T> =
        zustaende.getOrPut(schluessel) { mutableStateOf(start()) } as MutableState<T>

    // Bereich der ganzen Seite: Abrufe darin laufen weiter, wenn die ladende Karte aus dem Bild scrollt
    var bereich: CoroutineScope? = null
    private val laeufe = HashMap<String, Job>()

    // Startet einen Abruf einmal je Schluessel im Bereich der Seite. Ein laufender oder fertiger Abruf startet nicht neu,
    // ein abgebrochener schon.
    fun starte(schluessel: String, block: suspend () -> Unit) {
        val b = bereich ?: return
        val alt = laeufe[schluessel]
        if (alt != null && (alt.isActive || (alt.isCompleted && !alt.isCancelled))) return
        laeufe[schluessel] = b.launch { block() }
    }

    private val ergebnisse = HashMap<String, Pair<List<Any?>, Any?>>()

    // Gerechnetes Ergebnis, das das Herausscrollen ueberdauert: Es wird nur neu gerechnet, wenn sich die Eingaben aendern
    // (wie remember(eingaben), aber ohne den Verlust beim Verwerfen eines Eintrags der LazyColumn).
    @Suppress("UNCHECKED_CAST")
    fun <T> gerechnet(schluessel: String, eingaben: List<Any?>, rechne: () -> T): T {
        val alt = ergebnisse[schluessel]
        if (alt != null && alt.first == eingaben) return alt.second as T
        val neu = rechne()
        ergebnisse[schluessel] = eingaben to neu
        return neu
    }
}

// Zustand aus dem Merker der Seite; ohne Merker gilt er wie remember(schluessel) nur, solange die Karte im Bild ist.
@Composable
internal fun <T> gemerkt(merker: SeitenMerker?, schluessel: String, start: () -> T): MutableState<T> =
    remember(merker, schluessel) { merker?.zustand(schluessel, start) ?: mutableStateOf(start()) }

// Schluessel, der nur dieselbe Instanz gelten laesst: fuer grosse Listen als Eingabe von SeitenMerker.gerechnet, damit nicht
// jede Pruefung alle Elemente vergleicht.
internal class Ident(private val objekt: Any?) {
    override fun equals(other: Any?): Boolean = other is Ident && other.objekt === objekt
    // Verglichen wird nur mit equals (Liste der Eingaben); ein fester Wert haelt den Vertrag und laeuft auf jeder Plattform.
    override fun hashCode(): Int = 0
}
