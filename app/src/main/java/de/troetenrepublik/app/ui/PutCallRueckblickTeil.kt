package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.PutCallRueckblick

// Rueckblick in der Put/Call-Kachel (0.78): wo der heutige Wert in der Verteilung der Handelstage des Cboe-Archivs liegt.
// Eine Zeile mit dem Rang, darunter die Verteilung als Saeulen mit dem heutigen Wert als Marke. Ohne Archiv (kein Netz)
// steht nichts da.
@Composable
internal fun PutCallRueckblickTeil(state: AppState, heute: Double) {
    var archiv by remember { mutableStateOf<PutCallRueckblick.Archiv?>(null) }
    LaunchedEffect(Unit) { archiv = state.putCallArchiv() }
    val a = archiv ?: return
    val rang = remember(a, heute) { PutCallRueckblick.rang(a, heute) }
    val verteilung = remember(a) { PutCallRueckblick.verteilung(a) }
    val akzent = MaterialTheme.colorScheme.primary
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val bahn = MaterialTheme.colorScheme.surfaceContainerHighest
    val marke = MaterialTheme.colorScheme.onSurface
    val satz = forschungText("pc.rang", Fmt.pct(rang, 0), a.vonJahr.toString(), a.bisJahr.toString())
    Text(satz, style = MaterialTheme.typography.bodySmall, color = marke, modifier = Modifier.padding(top = 6.dp))
    val messer = rememberTextMeasurer()
    val klein = TextStyle(fontSize = 10.sp, color = grau)
    val max = verteilung.max().takeIf { it > 0 } ?: return
    val heuteText = Fmt.num(heute, 2)
    val medianText = forschungText("pc.median", Fmt.num(PutCallRueckblick.median(a), 2))
    Canvas(Modifier.fillMaxWidth().padding(top = 4.dp).height(84.dp).semantics { contentDescription = satz }) {
        val kopf = 14.dp.toPx()
        val fuss = 14.dp.toPx()
        val n = verteilung.size
        val breite = size.width / n
        val hoehe = size.height - kopf - fuss
        val luecke = 2.dp.toPx()
        val eigene = PutCallRueckblick.klasse(heute)
        for (i in 0 until n) {
            val h = (hoehe * (verteilung[i] / max)).toFloat().coerceAtLeast(1.dp.toPx())
            drawRoundRect(
                if (i == eigene) akzent else bahn,
                Offset(i * breite + luecke / 2, kopf + hoehe - h), Size(breite - luecke, h), CornerRadius(1.5.dp.toPx()),
            )
        }
        // Heutiger Wert ueber seiner Saeule, Achse mit drei Werten darunter
        val t = messer.measure(heuteText, klein.copy(color = akzent), maxLines = 1, softWrap = false)
        val x = ((eigene + 0.5f) * breite - t.size.width / 2f).coerceIn(0f, size.width - t.size.width)
        drawText(t, topLeft = Offset(x, 0f))
        for (w in listOf(0.5, 0.8)) {
            val z = messer.measure(Fmt.num(w, 1), klein, maxLines = 1, softWrap = false)
            val px = ((w - PutCallRueckblick.UNTEN) / PutCallRueckblick.SCHRITT).toFloat() * breite
            drawText(z, topLeft = Offset(px - z.size.width / 2f, size.height - fuss + 2.dp.toPx()))
        }
        val m = messer.measure(medianText, klein, maxLines = 1, softWrap = false)
        drawText(m, topLeft = Offset(size.width - m.size.width, size.height - fuss + 2.dp.toPx()))
    }
}
