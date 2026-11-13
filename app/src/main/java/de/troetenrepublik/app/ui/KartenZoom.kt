package de.troetenrepublik.app.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import de.troetenrepublik.app.engine.HeatmapZoomLage

// [0.78] Zoom und Versatz einer Heatmap, gemeinsam fuer die Heatmap der Maerkte und die Unternehmens-Heatmap im
// Laenderblatt. Bildschirm = Flaeche mal zoom plus versatz; vergroessert wird nur die Lage der Kacheln, nie die Schrift.
@Stable
class KartenZoom {
    var zoom by mutableFloatStateOf(1f)
    var versatz by mutableStateOf(Offset.Zero)

    val vergroessert: Boolean get() = zoom > 1.01f

    fun zurueck() { zoom = 1f; versatz = Offset.Zero }

    internal fun ausschnitt() = HeatmapZoomLage.Ausschnitt(zoom, versatz.x, versatz.y)

    internal fun setzen(a: HeatmapZoomLage.Ausschnitt) { zoom = a.zoom; versatz = Offset(a.vx, a.vy) }
}

// Zwei Finger vergroessern und verschieben; ist schon vergroessert, verschiebt auch ein Finger. Ein Finger bei Zoom 1
// bleibt unberuehrt und gehoert dem Rollen der Seite, dem Wischen und dem Tippen. Am Rechner zoomt das Mausrad mit Strg.
fun Modifier.kartenZoom(z: KartenZoom, max: Float): Modifier = this
    .pointerInput(z, max) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val ev = awaitPointerEvent()
                val finger = ev.changes.count { it.pressed }
                // Beim Loslassen hat das Ereignis keinen gedrueckten Finger mehr; die Mitte ist dann unbestimmt
                val mitte = ev.calculateCentroid(useCurrent = true)
                if (HeatmapZoomLage.gehoertKarte(finger, z.ausschnitt()) && mitte.isSpecified) {
                    val pan = ev.calculatePan()
                    z.setzen(HeatmapZoomLage.zoomen(z.ausschnitt(), mitte.x, mitte.y, ev.calculateZoom(), pan.x, pan.y, size.width.toFloat(), size.height.toFloat(), max))
                    ev.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            } while (ev.changes.any { it.pressed })
        }
    }
    .pointerInput(z, max) {
        awaitPointerEventScope {
            while (true) {
                val ev = awaitPointerEvent()
                if (ev.type != PointerEventType.Scroll || !ev.keyboardModifiers.isCtrlPressed) continue
                val c = ev.changes.firstOrNull() ?: continue
                z.setzen(HeatmapZoomLage.rad(z.ausschnitt(), c.position.x, c.position.y, c.scrollDelta.y, size.width.toFloat(), size.height.toFloat(), max))
                ev.changes.forEach { it.consume() }
            }
        }
    }
