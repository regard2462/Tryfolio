package de.troetenrepublik.app.plattform

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Zurueck-Geste mit Vorschau; onBack bekommt den Fortschritt der Geste (0 bis 1). Das collect auf dem
// Fortschritt steht beim Aufrufer (AppNav); Lint sieht es durch die Weitergabe nicht.
@Suppress("NoCollectCallFound")
@Composable
fun ZurueckGeste(enabled: Boolean, onBack: suspend (Flow<Float>) -> Unit) {
    PredictiveBackHandler(enabled = enabled) { fortschritt -> onBack(fortschritt.map { it.progress }) }
}

// Zurueck ohne Vorschau (Taste oder Geste).
@Composable
fun ZurueckTaste(enabled: Boolean = true, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}

// Loest das Zurueck aus wie die Taste des Systems, fuer einen Pfeil auf Seiten ohne eigenen Rueckweg (Profil).
@Composable
fun rememberZurueckAusloesen(): () -> Unit {
    val besitzer = LocalOnBackPressedDispatcherOwner.current
    return remember(besitzer) { { besitzer?.onBackPressedDispatcher?.onBackPressed() } }
}
