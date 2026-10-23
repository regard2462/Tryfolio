package de.troetenrepublik.app.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.abs

// Seiten, die wie ein Blatt ueber der vorherigen liegen, lassen sich nach unten wegwischen. Die Seite folgt dem
// Finger Bild fuer Bild, die Seite darunter liegt fertig im Seitenstapel. Beim Loslassen laeuft sie mit der
// Fingergeschwindigkeit aus, ganz hinaus oder zurueck. Ohne LocalSeite bewegt sich nichts, der Wisch ruft nur fallback.
class SwipeDownDismiss internal constructor(
    private val seite: Seitenstapel.Eintrag?,
    private val fallback: () -> Unit,
) {
    private var started = false
    private var ohneStapel = 0f

    val aktiv: Boolean get() = started

    private val hoehe: Float get() = seite?.hoehe ?: 2000f

    // delta in Pixeln, positiv = nach unten. Gibt den verbrauchten Anteil zurueck.
    fun drag(delta: Float): Float {
        val s = seite
        val jetzt = s?.y ?: ohneStapel
        val next = (jetzt + delta).coerceIn(0f, hoehe)
        val consumed = next - jetzt
        if (consumed == 0f) return 0f
        if (s != null) {
            // Ein Finger faengt die hinein- oder zurueckgleitende Seite wieder ein.
            s.halten()
            s.y = next
        } else ohneStapel = next
        started = true
        return consumed
    }

    // velocityY in Pixeln pro Sekunde, positiv = nach unten. fertig laeuft, wenn alles vorbei ist.
    fun release(velocityY: Float, fertig: () -> Unit = {}) {
        val s = seite
        val lage = s?.y ?: ohneStapel
        if (!started && lage <= 0f) { fertig(); return }
        started = false
        val close = lage / hoehe > CLOSE_FRACTION || (velocityY > CLOSE_VELOCITY && lage > MIN_OFFSET)
        if (s == null) {
            ohneStapel = 0f
            if (close) fallback()
            fertig()
            return
        }
        if (close) s.schliessen(velocityY, fertig) else s.zurueck(velocityY, fertig)
    }

    private companion object {
        const val CLOSE_FRACTION = 0.22f
        const val CLOSE_VELOCITY = 1200f
        const val MIN_OFFSET = 24f
    }
}

@Composable
fun rememberSwipeDownDismiss(onDismiss: () -> Unit): SwipeDownDismiss {
    val dismiss by rememberUpdatedState(onDismiss)
    val seite = LocalSeite.current
    return remember(seite) { SwipeDownDismiss(seite) { dismiss() } }
}

// Die Geste gehoert der Seite, sobald der Finger nach unten zieht und der Inhalt oben steht. Scrollt der Inhalt im
// selben Zug bis nach oben, uebernimmt sie ab dort. Waagerechte Bewegungen und Tippen bleiben unberuehrt.
// Gemessen wird in Fensterkoordinaten, weil die Seite mitgleitet und Seitenkoordinaten die Bewegung verfaelschen.
@Composable
fun Modifier.sheetDismiss(dismiss: SwipeDownDismiss, amOberenRand: () -> Boolean = { true }): Modifier {
    val oben by rememberUpdatedState(amOberenRand)
    val lage = remember { arrayOfNulls<LayoutCoordinates>(1) }
    return this
        .onGloballyPositioned { lage[0] = it }
        .pointerInput(dismiss) {
            val slop = viewConfiguration.touchSlop
            awaitEachGesture {
                val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val id = first.id
                fun fenster(p: Offset): Offset = lage[0]?.takeIf { it.isAttached }?.localToWindow(p) ?: p
                val tracker = VelocityTracker()
                val start = fenster(first.position)
                var letzte = start
                tracker.addPosition(first.uptimeMillis, start)
                // 0 = offen, 1 = gehoert der Seite, -1 = gehoert dem Inhalt
                var modus = 0
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    val ch = ev.changes.firstOrNull { it.id == id } ?: break
                    val pos = fenster(ch.position)
                    tracker.addPosition(ch.uptimeMillis, pos)
                    if (!ch.pressed) {
                        if (modus == 1) {
                            ch.consume()
                            dismiss.release(tracker.calculateVelocity().y)
                        }
                        break
                    }
                    if (ev.changes.count { it.pressed } > 1) {
                        if (modus == 1) dismiss.release(0f)
                        break
                    }
                    val dy = pos.y - letzte.y
                    letzte = pos
                    when (modus) {
                        1 -> { ch.consume(); dismiss.drag(dy) }
                        0 -> {
                            val gesamt = pos - start
                            if (abs(gesamt.x) > slop || abs(gesamt.y) > slop) {
                                modus = if (gesamt.y > 0 && gesamt.y > abs(gesamt.x) * 1.2f && oben() && !ch.isConsumed) 1 else -1
                                if (modus == 1) { ch.consume(); dismiss.drag(gesamt.y - slop) }
                                else if (abs(gesamt.x) > abs(gesamt.y)) break // waagerecht: gehoert dem Inhalt
                            }
                        }
                        else -> {
                            // Erreicht der Inhalt den oberen Rand und zieht der Finger weiter, uebernimmt die Seite.
                            if (dy > 0f && oben()) { modus = 1; ch.consume(); dismiss.drag(dy) }
                        }
                    }
                }
            }
        }
}
