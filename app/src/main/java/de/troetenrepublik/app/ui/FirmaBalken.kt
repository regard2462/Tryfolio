package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

// Balken je Zeitraum (Jahr oder Quartal) auf einer Skala ab null, Bezeichnung unter dem Balken, so viele wie ohne Beruehrung
// passen; die Zahl steht ueber dem Balken (wo sie in die Spalte passt, erster und letzter immer), der juengste in der Akzentfarbe. Ein Tipp oder Ziehen zeigt Name und Wert des
// Balkens unter dem Finger. Gleiche Gestaltung wie MitarbeiterBalken, mit freien Bezeichnungen statt Jahreszahlen.
@Composable
internal fun FirmaBalken(werte: List<Pair<String, Double>>, format: (Double) -> String) {
    val n = werte.size
    if (n < 2) return
    val akzent = MaterialTheme.colorScheme.primary
    val blass = akzent.copy(alpha = 0.35f)
    val grund = MaterialTheme.colorScheme.outlineVariant
    val schrift = MaterialTheme.colorScheme.onSurfaceVariant
    val text = MaterialTheme.colorScheme.onSurface
    val klein = MaterialTheme.typography.labelSmall
    val messer = rememberTextMeasurer()
    var gewaehlt by remember(werte) { mutableStateOf<Int?>(null) }
    val hoechster = remember(werte) { werte.maxOf { it.second }.coerceAtLeast(1e-9) }
    Canvas(
        Modifier.fillMaxWidth().height(132.dp).padding(horizontal = 20.dp, vertical = 4.dp).fingerLesen(werte) { p, groesse ->
            gewaehlt = p?.let { (it.x / (groesse.width.toFloat() / n)).toInt().coerceIn(0, n - 1) }
        },
    ) {
        val oben = 18.dp.toPx()
        val unten = 18.dp.toPx()
        val boden = size.height - unten
        val spalte = size.width / n
        val breite = (spalte * 0.62f).coerceAtMost(30.dp.toPx())
        fun y(v: Double) = boden - (boden - oben) * (v / hoechster).toFloat()
        fun mitte(i: Int) = spalte * i + spalte / 2
        werte.forEachIndexed { i, (_, v) ->
            val yv = minOf(y(v), boden - 2.dp.toPx())
            val farbe = when {
                i == gewaehlt -> if (i == n - 1) akzent else akzent.copy(alpha = 0.75f)
                i == n - 1 -> akzent
                else -> blass
            }
            if (v > 0) drawRoundRect(farbe, Offset(mitte(i) - breite / 2, yv), Size(breite, boden - yv), CornerRadius(2.dp.toPx(), 2.dp.toPx()))
        }
        drawLine(grund, Offset(0f, boden), Offset(size.width, boden), strokeWidth = 1.dp.toPx())
        // Bezeichnungen: jede k-te, vom letzten Balken rueckwaerts, damit die letzte immer steht
        val bezBreite = werte.maxOf { messer.measure(it.first, klein).size.width } + 6.dp.toPx()
        val schritt = ceil(bezBreite / spalte).toInt().coerceAtLeast(1)
        for (i in n - 1 downTo 0 step schritt) {
            beschriften(messer, werte[i].first, klein.copy(color = if (i == n - 1) text else schrift), mitte(i), boden + 3.dp.toPx())
        }
        val g = gewaehlt
        if (g == null) {
            // Zahl ueber jedem Balken, dessen Zahl in die Spalte passt (so beruehren sich Nachbarn nie); erster und letzter
            // Wert stehen immer da, auch wenn die Zahl breiter ist als die Spalte.
            for (i in 0 until n) {
                val v = werte[i].second
                if (v <= 0) continue
                val t = format(v)
                val passt = messer.measure(t, klein).size.width <= spalte - 2.dp.toPx()
                if (passt || i == 0 || i == n - 1) beschriften(messer, t, klein.copy(color = text), mitte(i), y(v) - 17.dp.toPx())
            }
        } else {
            drawLine(schrift, Offset(mitte(g), oben), Offset(mitte(g), boden), strokeWidth = 1f)
            beschriften(messer, werte[g].first + " · " + format(werte[g].second), klein.copy(color = schrift), mitte(g), 0f)
        }
    }
}

// Text um die Mitte herum, nie ueber den Rand der Zeichenflaeche hinaus
private fun DrawScope.beschriften(messer: TextMeasurer, text: String, stil: TextStyle, mitteX: Float, oben: Float) {
    val m = messer.measure(text, stil)
    drawText(m, topLeft = Offset((mitteX - m.size.width / 2).coerceIn(0f, (size.width - m.size.width).coerceAtLeast(0f)), oben))
}
