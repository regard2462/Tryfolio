package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Enso
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.ohneAbbruch
import kotlin.math.abs

// [Engpaesse] Weltlage unter der Ebene „Engpaesse“: ENSO (NOAA) und die Spannungsgebiete. Aufklappbar; die Daten werden
// erst geladen, wenn der Kasten offen ist.
@Composable
internal fun WeltlageKasten(state: AppState, onOpen: (String) -> Unit) {
    var offen by rememberSaveable { mutableStateOf(true) }
    Row(
        Modifier.fillMaxWidth().clickable { offen = !offen }.padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(S.weltlageTitel, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        AufklappPfeil(offen)
    }
    if (!offen) return
    EnsoAbschnitt(state, onOpen)
    SpannungsAbschnitt(state, onOpen)
}

private fun vorzeichen(v: Double): String = (if (v > 0) "+" else if (v < 0) "−" else "") + Fmt.num(abs(v), 2)

@Composable
private fun EnsoAbschnitt(state: AppState, onOpen: (String) -> Unit) {
    var status by remember { mutableStateOf<Enso.Status?>(null) }
    var index by remember { mutableStateOf<Enso.Index?>(null) }
    var fertig by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(Unit) {
        status = ohneAbbruch { state.ensoStatus() }.getOrNull()
        index = ohneAbbruch { state.ensoIndex() }.getOrNull()
        fertig = true
    }
    if (!fertig) {
        Text(S.loading, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        return
    }
    val letzter = index?.punkte?.lastOrNull()
    if (status == null && letzter == null) return
    Zwischenkopf(S.ensoTitel)
    status?.let { st ->
        KeyValueRow(S.ensoStatus, S.ensoZustand(st.zustand), onClick = { info = S.ensoStatus to S.ensoStatusInfo })
    }
    if (letzter != null) {
        KeyValueRow(S.ensoIndex, vorzeichen(letzter.wert) + " (" + letzter.saison + " " + letzter.jahr + ")", onClick = { info = S.ensoIndex to S.ensoIndexInfo })
        KeyValueRow(S.ensoStaerke, S.ensoStaerkeText(Enso.staerke(letzter.wert)), onClick = { info = S.ensoStaerke to S.ensoStaerkeInfo })
        val punkte = index?.punkte.orEmpty()
        if (punkte.size >= 6) IndexLinie(punkte.map { it.wert }, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).height(64.dp))
    }
    KeyValueRow(S.ensoFolgenNino, "", onClick = { info = S.ensoFolgenNino to S.ensoFolgenNinoInfo })
    KeyValueRow(S.ensoFolgenNina, "", onClick = { info = S.ensoFolgenNina to S.ensoFolgenNinaInfo })
    WerteListe(Enso.WERTE, onOpen)
    Text(S.ensoQuelle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
    info?.let { (titel, text) -> MarktInfoBlatt(titel, text) { info = null } }
}

// Dreimonatswerte des Index: Nulllinie, gestrichelt die Schwellen von plus und minus 0,5 Grad
@Composable
private fun IndexLinie(werte: List<Double>, modifier: Modifier) {
    val farbe = MaterialTheme.colorScheme.onSurface
    val grau = MaterialTheme.colorScheme.outline
    Canvas(modifier) {
        val hi = maxOf(werte.max(), 1.0)
        val lo = minOf(werte.min(), -1.0)
        fun y(v: Double) = size.height * (1f - ((v - lo) / (hi - lo)).toFloat())
        fun x(i: Int) = size.width * i / (werte.size - 1)
        drawLine(grau, Offset(0f, y(0.0)), Offset(size.width, y(0.0)), strokeWidth = 1.2f * density)
        for (s in listOf(Enso.SCHWELLE, -Enso.SCHWELLE)) drawLine(grau, Offset(0f, y(s)), Offset(size.width, y(s)), strokeWidth = 1f * density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        val p = Path()
        werte.forEachIndexed { i, v -> if (i == 0) p.moveTo(x(i), y(v)) else p.lineTo(x(i), y(v)) }
        drawPath(p, farbe, style = Stroke(width = 2f * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
