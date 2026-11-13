package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.data.Lang
import de.troetenrepublik.app.engine.Zufallsdepots
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.i18n.FinderZufallTexte
import kotlin.math.max
import kotlin.math.min

// Zufallsdepots im Rueckblick des Aktienfinders: Kernzeile „besser als x % der Zufallsdepots“, darunter die Verteilung
// der Zufallsdepots mit der Auswahl des Finders als Marke. Die Zahlen stehen in der mitgelieferten Zusammenfassung.

private fun zfText(key: String, vararg w: String): String = FinderZufallTexte.text(I18n.lang == Lang.DE, key, w.toList())

@Composable
internal fun FinderZufallAbschnitt(v: Zufallsdepots.Verteilung?) {
    if (v == null || v.quantile.size < 3) return
    val signal = LocalSignal.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val farbe = when {
        v.besserAls >= 0.9 -> signal.up
        v.besserAls <= 0.5 -> signal.down
        else -> MaterialTheme.colorScheme.onSurface
    }
    var info by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(zfText("kern", Fmt.pct(v.besserAls * 100, 0)), style = MaterialTheme.typography.titleMedium, color = farbe, modifier = Modifier.weight(1f))
            IconButton(onClick = { info = true }) {
                Icon(Icons.Outlined.Info, S.infoErklaerung, tint = grau, modifier = Modifier.size(20.dp))
            }
        }
        ZufallVerteilung(v, farbe)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val klein = MaterialTheme.typography.labelSmall
            Text(Fmt.signedPct(v.quantile.first() * 100, 1), style = klein, color = grau, maxLines = 1, softWrap = false)
            Text(zfText("mitte", Fmt.signedPct(v.median * 100, 1)), style = klein, color = grau, maxLines = 1, softWrap = false)
            Text(Fmt.signedPct(v.quantile.last() * 100, 1), style = klein, color = grau, maxLines = 1, softWrap = false)
        }
    }
    if (info) MarktInfoBlatt(
        zfText("titel"),
        zfText("info", Fmt.num(v.anzahl.toDouble(), 0), v.groesse.toString(), v.monate.toString(), Fmt.signedPct(v.quantil(0.05) * 100, 1), Fmt.signedPct(v.quantil(0.95) * 100, 1)),
    ) { info = false }
}

// Verteilung aus den Stufen: je Stufe gleich viele Zufallsdepots, schmale Stufen sind hoch (dicht). Die Auswahl des
// Finders als senkrechte Marke mit Beschriftung.
@Composable
private fun ZufallVerteilung(v: Zufallsdepots.Verteilung, farbe: androidx.compose.ui.graphics.Color) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val saeule = Kategorie.blau.copy(alpha = 0.55f)
    val messer = rememberTextMeasurer()
    val stil = MaterialTheme.typography.labelSmall.copy(color = farbe)
    val marke = zfText("finder", Fmt.signedPct(v.auswahl * 100, 1))
    Canvas(Modifier.fillMaxWidth().height(86.dp).padding(top = 6.dp)) {
        val q = v.quantile
        val lo = q.first()
        val hi = q.last()
        val spanne = (hi - lo).takeIf { it > 1e-9 } ?: return@Canvas
        val kopf = 18.dp.toPx()
        val boden = size.height - 1.dp.toPx()
        val hoehe = boden - kopf
        fun x(r: Double) = ((r - lo) / spanne).coerceIn(0.0, 1.0).toFloat() * size.width
        // Dichte je Stufe; die aeussersten Stufen sind lang und flach
        val dichte = (1 until q.size).map { i -> 1.0 / max(q[i] - q[i - 1], spanne / 400) }
        val hoechste = dichte.max()
        for (i in 1 until q.size) {
            val a = x(q[i - 1])
            val b = x(q[i])
            val h = (dichte[i - 1] / hoechste).toFloat() * hoehe
            drawRect(saeule, Offset(a, boden - h), Size(max(b - a - 0.5.dp.toPx(), 0.5.dp.toPx()), h))
        }
        drawLine(grau.copy(alpha = 0.6f), Offset(0f, boden), Offset(size.width, boden), strokeWidth = 1.dp.toPx())
        // Marke der Auswahl
        val mx = x(v.auswahl)
        drawLine(farbe, Offset(mx, kopf - 2.dp.toPx()), Offset(mx, boden), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        val text = messer.measure(marke, stil, maxLines = 1, softWrap = false)
        val tx = min(max(mx - text.size.width / 2f, 0f), max(size.width - text.size.width, 0f))
        drawText(text, topLeft = Offset(tx, 0f))
    }
}
