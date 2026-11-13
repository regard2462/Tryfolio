package de.troetenrepublik.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import de.troetenrepublik.app.engine.GlobusAnsicht
import de.troetenrepublik.app.engine.GlobusKarte
import de.troetenrepublik.app.engine.GlobusLand
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.sqrt

// [Globus Gesten 0.70] Laendernamen auf dem Globus ab einer Zoomstufe. Anker und Ausdehnung je Land werden einmal aus
// dem groessten Ring gerechnet, die Textmessungen bleiben je Name und Sprache gespeichert, damit das Drehen fluessig bleibt.
// Grosse Laender kommen zuerst, ein Name erscheint nur, wenn er in das Land passt und keinen schon gesetzten ueberdeckt.
internal class GlobusNamen(karte: GlobusKarte, private val deutsch: Boolean) {
    private class Anker(val land: GlobusLand, val x: Float, val y: Float, val z: Float, val lon: Double, val lat: Double, val halbWinkel: Float)

    private val anker: List<Anker> = karte.laender.mapNotNull { anker(it) }.sortedByDescending { it.halbWinkel }
    private val fuellung = HashMap<String, TextLayoutResult>()
    private val umriss = HashMap<String, TextLayoutResult>()
    private val belegt = FloatArray(anker.size * 4 + 4)
    private var cacheDichte = 0f
    private var cacheSchrift = 0

    private fun anker(l: GlobusLand): Anker? {
        // Hauptflaeche: der Ring mit der groessten Ausdehnung (nicht mit den meisten Punkten), damit der Name bei Laendern
        // mit Insel- oder Uebersee-Teilen (Frankreich, USA, Norwegen) auf dem Festland steht
        var beste: Anker? = null
        for (ring in l.ringe) {
            val ank = ringAnker(l, ring.vek) ?: continue
            if (beste == null || ank.halbWinkel > beste.halbWinkel) beste = ank
        }
        return beste
    }

    private fun ringAnker(l: GlobusLand, v: FloatArray): Anker? {
        if (v.size < 9) return null
        var sx = 0.0; var sy = 0.0; var sz = 0.0
        var i = 0
        while (i < v.size) { sx += v[i]; sy += v[i + 1]; sz += v[i + 2]; i += 3 }
        val len = sqrt(sx * sx + sy * sy + sz * sz)
        if (len < 1e-6) return null
        val mx = sx / len; val my = sy / len; val mz = sz / len
        // Quadratischer Mittelwert des Abstands zur Mitte, als Mass fuer die Ausdehnung
        var q = 0.0; var n = 0
        i = 0
        while (i < v.size) {
            val d = (mx * v[i] + my * v[i + 1] + mz * v[i + 2]).coerceIn(-1.0, 1.0)
            val w = acos(d)
            q += w * w; n++
            i += 3
        }
        val halb = (sqrt(q / n) * 1.5).toFloat()
        return Anker(l, mx.toFloat(), my.toFloat(), mz.toFloat(), Math.toDegrees(atan2(my, mx)), Math.toDegrees(asin(mz.coerceIn(-1.0, 1.0))), halb)
    }

    private fun layout(messer: TextMeasurer, cache: HashMap<String, TextLayoutResult>, l: GlobusLand, stil: TextStyle, stufe: Int): TextLayoutResult {
        val key = l.a3 + (if (deutsch) "d" else "e") + stufe
        return cache.getOrPut(key) { messer.measure(l.name(deutsch), stil, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip) }
    }

    // ab dieser Zoomstufe erscheinen Namen
    fun zeichnen(s: DrawScope, messer: TextMeasurer, a: GlobusAnsicht, zoom: Float, r: Float, schrift: Color = Color.White, schriftRand: Color = Color(0xE6101014)) {
        if (zoom < ZOOM_AB) return
        val dichte = s.density
        // Wechselt das Fenster auf einen Bildschirm mit anderer Dichte, gelten die gemessenen Texte nicht mehr
        // Ebenso beim Wechsel des Erscheinungsbilds: die Farbe steckt in der Messung
        val schriftKey = schrift.hashCode() * 31 + schriftRand.hashCode()
        if (dichte != cacheDichte || schriftKey != cacheSchrift) { fuellung.clear(); umriss.clear(); cacheDichte = dichte; cacheSchrift = schriftKey }
        val stufe = if (zoom < 2.5f) 0 else if (zoom < 4f) 1 else 2
        val groesse = (10f + stufe * 1.5f).sp
        // [0.71] Fette weisse Schrift mit dunklem, zur Schriftgroesse passendem Umriss: Der feste drei-Pixel-Rand
        // verschloss bei kleinen Ziffern die Innenraeume und machte die Namen zu einem weissen Klecks.
        val randBreite = with(s) { groesse.toPx() * 0.22f }
        val fuell = TextStyle(fontSize = groesse, color = schrift, fontWeight = FontWeight.Bold)
        val randStil = TextStyle(
            fontSize = groesse, color = schriftRand, fontWeight = FontWeight.Bold,
            drawStyle = Stroke(width = randBreite, join = androidx.compose.ui.graphics.StrokeJoin.Round),
        )
        var nb = 0
        for (an in anker) {
            // Sichtbare Seite, nicht zu nahe am Rand der Kugel (dort ist alles gestaucht)
            val tiefe = a.tiefe(an.x, an.y, an.z)
            if (tiefe < 0.35f) continue
            // Breite des Landes im Bild, am Rand der Kugel verkuerzt
            val breite = 2f * r * sin(an.halbWinkel.coerceAtMost(1.4f)) * (0.4f + 0.6f * tiefe)
            if (breite < 24f * dichte) continue
            val f = layout(messer, fuellung, an.land, fuell, stufe)
            if (f.size.width > breite) continue
            val px = a.px(an.x, an.y, an.z)
            val py = a.py(an.x, an.y, an.z)
            val w = f.size.width.toFloat(); val h = f.size.height.toFloat()
            val x0 = px - w / 2f; val y0 = py - h / 2f
            val luft = 3f * dichte
            var frei = true
            var k = 0
            while (k < nb) {
                if (x0 - luft < belegt[k * 4 + 2] && x0 + w + luft > belegt[k * 4] && y0 - luft < belegt[k * 4 + 3] && y0 + h + luft > belegt[k * 4 + 1]) { frei = false; break }
                k++
            }
            if (!frei) continue
            belegt[nb * 4] = x0; belegt[nb * 4 + 1] = y0; belegt[nb * 4 + 2] = x0 + w; belegt[nb * 4 + 3] = y0 + h
            nb++
            // [0.76] Zeichenart bei beiden Aufrufen ausdruecklich setzen: Auf Android teilen sich die Texte einen Pinsel,
            // sonst zeichnet die weisse Fuellung mit dem dicken Strich des Umrisses und die Namen werden zum Klecks.
            s.drawText(layout(messer, umriss, an.land, randStil, stufe), topLeft = Offset(x0, y0), drawStyle = Stroke(width = randBreite, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            s.drawText(f, topLeft = Offset(x0, y0), drawStyle = Fill)
        }
    }

    companion object {
        const val ZOOM_AB = 1.6f
    }
}
