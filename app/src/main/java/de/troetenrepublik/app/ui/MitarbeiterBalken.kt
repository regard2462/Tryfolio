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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.engine.MitarbeiterAnsicht
import de.troetenrepublik.app.engine.MitarbeiterReihe

// Mitarbeiterzahl je Jahr als Balken auf einer Skala ab null und einer echten Jahresachse (ein Platz je Jahr, fehlende
// Jahre bleiben Luecke, hoechstens die letzten 15 Jahre): erstes und letztes Jahr unter den Balken, dazwischen so viele
// Jahreszahlen, wie ohne Beruehrung passen; erster und letzter Wert am Balken, der juengste Balken in der Akzentfarbe.
// Ein Tipp oder Ziehen zeigt Jahr und Wert des Balkens unter dem Finger, wie bei den Verlaeufen der Nachbarkarten.
@Composable
internal fun MitarbeiterBalken(alle: List<MitarbeiterReihe.Punkt>) {
    val reihe = remember(alle) { MitarbeiterAnsicht.letzte(alle) }
    val n = reihe.size
    if (n < 2) return
    val plaetze = remember(reihe) { MitarbeiterAnsicht.plaetze(reihe) }
    val plaetzeJe = remember(reihe) { reihe.map { MitarbeiterAnsicht.platz(reihe, it.jahr) } }
    val akzent = MaterialTheme.colorScheme.primary
    val blass = akzent.copy(alpha = 0.35f)
    val grund = MaterialTheme.colorScheme.outlineVariant
    val schrift = MaterialTheme.colorScheme.onSurfaceVariant
    val text = MaterialTheme.colorScheme.onSurface
    val klein = MaterialTheme.typography.labelSmall
    val messer = rememberTextMeasurer()
    var gewaehlt by remember(reihe) { mutableStateOf<Int?>(null) }
    val hoechster = remember(reihe) { reihe.maxOf { it.anzahl }.toDouble().coerceAtLeast(1.0) }
    Canvas(
        Modifier.fillMaxWidth().height(132.dp).padding(vertical = 4.dp).fingerLesen(reihe) { p, groesse ->
            gewaehlt = p?.let { plaetzeJe.indexOf((it.x / (groesse.width.toFloat() / plaetze)).toInt().coerceIn(0, plaetze - 1)).takeIf { i -> i >= 0 } }
        },
    ) {
        val oben = 18.dp.toPx()
        val unten = 18.dp.toPx()
        val boden = size.height - unten
        val spalte = size.width / plaetze
        val breite = (spalte * 0.62f).coerceAtMost(30.dp.toPx())
        fun y(v: Long) = boden - (boden - oben) * (v / hoechster).toFloat()
        fun mitte(i: Int) = spalte * plaetzeJe[i] + spalte / 2
        reihe.forEachIndexed { i, pt ->
            val yv = minOf(y(pt.anzahl), boden - 2.dp.toPx())
            val farbe = when {
                i == gewaehlt -> if (i == n - 1) akzent else akzent.copy(alpha = 0.75f)
                i == n - 1 -> akzent
                else -> blass
            }
            drawRoundRect(farbe, Offset(mitte(i) - breite / 2, yv), Size(breite, boden - yv), CornerRadius(2.dp.toPx(), 2.dp.toPx()))
        }
        drawLine(grund, Offset(0f, boden), Offset(size.width, boden), strokeWidth = 1.dp.toPx())
        val markeBreite = messer.measure("0000", klein).size.width.toFloat()
        MitarbeiterAnsicht.marken(plaetze, spalte, markeBreite).forEach { platz ->
            val jahr = reihe.first().jahr + platz
            beschriften(messer, jahr.toString(), klein.copy(color = if (platz == plaetze - 1) text else schrift), spalte * platz + spalte / 2, boden + 3.dp.toPx())
        }
        val g = gewaehlt
        if (g == null) {
            for (i in listOf(0, n - 1)) {
                val pt = reihe[i]
                beschriften(messer, Fmt.num(pt.anzahl.toDouble(), 0), klein.copy(color = text), mitte(i), y(pt.anzahl) - 17.dp.toPx())
            }
        } else {
            val pt = reihe[g]
            drawLine(schrift, Offset(mitte(g), oben), Offset(mitte(g), boden), strokeWidth = 1f)
            beschriften(messer, pt.jahr.toString() + " · " + Fmt.num(pt.anzahl.toDouble(), 0), klein.copy(color = schrift), mitte(g), 0f)
        }
    }
}

// Text um die Mitte herum, nie ueber den Rand der Zeichenflaeche hinaus
private fun DrawScope.beschriften(messer: TextMeasurer, text: String, stil: TextStyle, mitteX: Float, oben: Float) {
    val m = messer.measure(text, stil)
    drawText(m, topLeft = Offset((mitteX - m.size.width / 2).coerceIn(0f, (size.width - m.size.width).coerceAtLeast(0f)), oben))
}
