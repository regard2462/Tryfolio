package de.troetenrepublik.app.plattform

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlinx.coroutines.launch
import kotlin.math.abs

// Waagerechte Zeile (Kacheln, Reiter, Chips), die sich auch mit der Maus bedienen laesst. Compose scrollt sie sonst
// nur mit dem Finger oder mit waagerechtem Rad. Hier kommen dazu: Ziehen mit gedrueckter Maustaste (mit Ausgleiten
// nach dem Loslassen) und das normale senkrechte Mausrad, solange die Zeile in diese Richtung noch Platz hat; am
// Rand geht das Rad an die Seite zurueck. Gegenstueck auf Android unter demselben Namen.
fun Modifier.horizontalScrollMaus(state: ScrollState): Modifier = composed {
    val scope = rememberCoroutineScope()
    val slop = LocalViewConfiguration.current.touchSlop
    this.horizontalScroll(state).pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.type != PointerType.Mouse) return@awaitEachGesture
            var gesamtX = 0f
            var gesamtY = 0f
            var zieht = false
            val tempo = VelocityTracker()
            tempo.addPosition(down.uptimeMillis, down.position)
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val c = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!c.pressed) {
                    if (zieht) {
                        c.consume()
                        val v = tempo.calculateVelocity().x
                        if (abs(v) > 300f) scope.launch {
                            state.scroll {
                                var letzter = 0f
                                AnimationState(0f, -v).animateDecay(exponentialDecay()) {
                                    scrollBy(value - letzter)
                                    letzter = value
                                }
                            }
                        }
                    }
                    break
                }
                if (c.isConsumed && !zieht) break
                val d = c.positionChange()
                tempo.addPosition(c.uptimeMillis, c.position)
                if (!zieht) {
                    gesamtX += d.x
                    gesamtY += d.y
                    if (abs(gesamtY) > slop && abs(gesamtY) > abs(gesamtX)) break
                    if (abs(gesamtX) > slop && abs(gesamtX) > abs(gesamtY) * 1.2f) zieht = true else continue
                }
                c.consume()
                state.dispatchRawDelta(-d.x)
            }
        }
    }.pointerInput(state) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                if (event.type != PointerEventType.Scroll) continue
                val c = event.changes.firstOrNull() ?: continue
                if (c.isConsumed) continue
                val d = c.scrollDelta
                val schritt = if (abs(d.x) > abs(d.y)) d.x else d.y
                if (schritt == 0f) continue
                val vor = schritt > 0f && state.canScrollForward
                val zurueck = schritt < 0f && state.canScrollBackward
                if (vor || zurueck) {
                    c.consume()
                    scope.launch { state.scrollBy(schritt * RAD_FAKTOR) }
                }
            }
        }
    }
}

private const val RAD_FAKTOR = 6f
