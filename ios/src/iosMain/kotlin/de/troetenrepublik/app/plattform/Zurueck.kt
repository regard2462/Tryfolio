package de.troetenrepublik.app.plattform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

// Zurueck auf iOS: die Wischgeste vom linken Rand (MainViewController.kt leitet sie hierher). Wie auf Android kommt
// der zuletzt angemeldete eingeschaltete Empfaenger zuerst dran.
object ZurueckVerteiler {
    internal class Empfaenger(var enabled: Boolean, val aktion: () -> Unit)

    private val liste = mutableListOf<Empfaenger>()

    internal fun anmelden(e: Empfaenger) { liste += e }
    internal fun abmelden(e: Empfaenger) { liste -= e }

    // true, wenn jemand das Zurueck genommen hat.
    fun zurueck(): Boolean {
        val e = liste.lastOrNull { it.enabled } ?: return false
        e.aktion()
        return true
    }

    // Gibt es gerade jemanden, der das Zurueck nimmt.
    val aktiv: Boolean get() = liste.any { it.enabled }
}

@Composable
private fun Anmeldung(enabled: Boolean, aktion: () -> Unit) {
    val a by rememberUpdatedState(aktion)
    val e = remember { ZurueckVerteiler.Empfaenger(enabled) { a() } }
    SideEffect { e.enabled = enabled }
    DisposableEffect(e) {
        ZurueckVerteiler.anmelden(e)
        onDispose { ZurueckVerteiler.abmelden(e) }
    }
}

// Zurueck mit Vorschau; ohne Fortschritt der Geste laeuft die Seite wie mit dem Pfeil hinaus.
@Composable
fun ZurueckGeste(enabled: Boolean, onBack: suspend (Flow<Float>) -> Unit) {
    val scope = rememberCoroutineScope()
    val back by rememberUpdatedState(onBack)
    Anmeldung(enabled) { scope.launch { back(emptyFlow()) } }
}

// Zurueck ohne Vorschau.
@Composable
fun ZurueckTaste(enabled: Boolean = true, onBack: () -> Unit) {
    Anmeldung(enabled, onBack)
}

// Loest das Zurueck aus wie die Randgeste, fuer einen Pfeil auf Seiten ohne eigenen Rueckweg (Profil).
@Composable
fun rememberZurueckAusloesen(): () -> Unit = remember<() -> Unit> { { ZurueckVerteiler.zurueck() } }
