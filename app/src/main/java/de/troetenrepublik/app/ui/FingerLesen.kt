package de.troetenrepublik.app.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs

// Gemeinsame Geste fuer Diagramme, bei denen der Finger Zeitpunkt und Wert abliest. Ein Finger zeigt sofort die Stelle,
// waagerechtes Ziehen liest mit und verbraucht die Bewegung, damit weder der Pager der Reiter noch die Liste mitgeht.
// Senkrechtes Wischen gibt die Auswahl frei, die Liste scrollt dann wie ueberall. Beim Loslassen kommt null.
// onLesen: Stelle in Pixeln der Flaeche und deren Groesse.
@Composable
internal fun Modifier.fingerLesen(vararg schluessel: Any?, onLesen: (Offset?, IntSize) -> Unit): Modifier {
    val aktuell = rememberUpdatedState(onLesen)
    return pointerInput(*schluessel) {
        awaitEachGesture {
            val start = awaitFirstDown(requireUnconsumed = false)
            val slop = viewConfiguration.touchSlop * 0.6f
            aktuell.value(start.position, size)
            var horizontal: Boolean? = null
            while (true) {
                val ev = awaitPointerEvent()
                val c = ev.changes.firstOrNull { it.id == start.id } ?: break
                if (!c.pressed) break
                val d = c.position - start.position
                if (horizontal == null && (abs(d.x) > slop || abs(d.y) > slop)) horizontal = abs(d.x) >= abs(d.y)
                if (horizontal == false) break
                if (horizontal == true) c.consume()
                aktuell.value(c.position, size)
            }
            aktuell.value(null, size)
        }
    }
}
