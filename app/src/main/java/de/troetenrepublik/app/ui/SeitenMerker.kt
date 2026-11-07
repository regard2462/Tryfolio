package de.troetenrepublik.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

// Zustand der Karten einer Wertpapierseite, der das Herausscrollen ueberdauert (0.68). Die Seite ist eine LazyColumn:
// Verlaesst eine Karte das Bild, verwirft Compose ihren remember-Zustand. Ohne Merker beginnt sie beim Zurueckscrollen
// leer, laedt neu und springt dann auf ihre volle Hoehe. Die Seite haelt einen Merker je Wert.
@Stable
internal class SeitenMerker {
    private val zustaende = HashMap<String, MutableState<*>>()

    @Suppress("UNCHECKED_CAST")
    fun <T> zustand(schluessel: String, start: () -> T): MutableState<T> =
        zustaende.getOrPut(schluessel) { mutableStateOf(start()) } as MutableState<T>
}

// Zustand aus dem Merker der Seite; ohne Merker gilt er wie remember(schluessel) nur, solange die Karte im Bild ist.
@Composable
internal fun <T> gemerkt(merker: SeitenMerker?, schluessel: String, start: () -> T): MutableState<T> =
    remember(merker, schluessel) { merker?.zustand(schluessel, start) ?: mutableStateOf(start()) }
