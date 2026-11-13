package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.engine.AusblickStaerke
import de.troetenrepublik.app.engine.AusblickStaerke.Staerke
import de.troetenrepublik.app.engine.WarnRueckblick

// [Ausblick 0.77] Staerke eines Signals als kleine Saeule aus vier Balken: so viele gefuellt, wie die Stufe sagt.
// Gemessene Staerke gefuellt, gesetzte nur als Umriss.
@Composable
internal fun StaerkeBalken(s: Staerke, modifier: Modifier = Modifier) {
    val farbe = MaterialTheme.colorScheme.onSurface
    val leer = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
    Canvas(modifier.width(20.dp).height(12.dp)) {
        val n = AusblickStaerke.STUFEN
        val luecke = 2.dp.toPx()
        val breite = (size.width - luecke * (n - 1)) / n
        val strich = 1.2.dp.toPx()
        for (i in 0 until n) {
            val h = size.height * (i + 1) / n
            val ort = Offset(i * (breite + luecke), size.height - h)
            val ecke = CornerRadius(1.dp.toPx())
            when {
                i >= s.stufe -> drawRoundRect(leer, ort, Size(breite, h), ecke)
                s.gemessen -> drawRoundRect(farbe, ort, Size(breite, h), ecke)
                else -> drawRoundRect(
                    farbe, Offset(ort.x + strich / 2, ort.y + strich / 2), Size(breite - strich, h - strich), ecke, style = Stroke(strich),
                )
            }
        }
    }
}

private val signalCache = HashMap<String, Staerke>()
private val blasenCache = HashMap<String, Staerke>()

// Staerke eines Eintrags des Ausblicks aus den mitgelieferten Rueckblicken (einmal je Schluessel gerechnet).
internal fun ausblickStaerke(schluessel: String): Staerke =
    signalCache.getOrPut(schluessel) {
        AusblickStaerke.fuerSignal(schluessel, RUECKBLICK, WarnRueckblick.datei, de.troetenrepublik.app.nyFedRezessionBekannt())
    }

internal fun blasenStaerke(schluessel: String): Staerke =
    blasenCache.getOrPut(schluessel) { AusblickStaerke.fuerBlase(schluessel, WarnRueckblick.datei) }
