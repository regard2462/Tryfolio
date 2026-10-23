package de.troetenrepublik.app.plattform

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.DisposableEffect
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

// Gleiten zur naechsten Seite.
private val GLEITEN = tween<Float>(durationMillis = 340, easing = FastOutSlowInEasing)

// Pfeiltasten links und rechts: Sie blaettern in dem Bereich mit Seiten (Reiter, Widgets, Kategorien), ueber
// dem zuletzt die Maus war; sonst in dem zuletzt erschienenen. Textfelder nehmen die Tasten vorher selbst.
object PagerTasten {
    private val sichtbar = ArrayList<Pair<PagerState, CoroutineScope>>()
    private var zuletzt: PagerState? = null

    internal fun anmelden(p: PagerState, scope: CoroutineScope) { sichtbar += p to scope }
    internal fun abmelden(p: PagerState) {
        sichtbar.removeAll { it.first === p }
        if (zuletzt === p) zuletzt = null
    }
    internal fun beruehrt(p: PagerState) { zuletzt = p }

    fun blaettern(richtung: Int): Boolean {
        val eintrag = sichtbar.lastOrNull { it.first === zuletzt } ?: sichtbar.lastOrNull() ?: return false
        val p = eintrag.first
        val ziel = (p.currentPage + richtung).coerceIn(0, p.pageCount - 1)
        if (ziel == p.currentPage) return false
        eintrag.second.launch { p.animateScrollToPage(ziel, animationSpec = GLEITEN) }
        return true
    }
}

// Seiten mit gedrueckter Maustaste ziehen wie mit dem Finger, denn Compose scrollt auf dem Desktop nur mit dem
// Mausrad. Was ein Kind schon genommen hat (etwa das Fadenkreuz im Chart), bleibt beim Kind; erst eine ueberwiegend
// waagerechte Bewegung gehoert dem Pager. Beim Loslassen gleitet er weiter, wenn ein Viertel des Wegs geschafft
// ist oder zuegig gezogen wurde.
fun Modifier.mausWischen(pager: PagerState): Modifier = composed {
    val scope = rememberCoroutineScope()
    val slop = LocalViewConfiguration.current.touchSlop
    DisposableEffect(pager) {
        PagerTasten.anmelden(pager, scope)
        onDispose { PagerTasten.abmelden(pager) }
    }
    pointerInput(pager) {
        // Wo die Maus ist, dort blaettern die Pfeiltasten
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                if (e.type == PointerEventType.Enter || e.type == PointerEventType.Move) PagerTasten.beruehrt(pager)
            }
        }
    }.pointerInput(pager) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.type != PointerType.Mouse) return@awaitEachGesture
            val start = pager.currentPage
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
                        val weg = pager.currentPage + pager.currentPageOffsetFraction - start
                        val ziel = when {
                            v < -300f -> start + 1
                            v > 300f -> start - 1
                            weg > 0.25f -> start + 1
                            weg < -0.25f -> start - 1
                            else -> start
                        }.coerceIn(0, pager.pageCount - 1)
                        scope.launch { pager.animateScrollToPage(ziel, animationSpec = GLEITEN) }
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
                pager.dispatchRawDelta(-d.x)
            }
        }
    }
}
