package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Regionen
import de.troetenrepublik.app.engine.Waehrungsfolge
import de.troetenrepublik.app.i18n.S

// Name eines Eintrags der Waehrungsfolge: Originalwaehrung oder „US-Dollar (USD)“.
fun waehrungsFolgeName(eintrag: String): String =
    if (eintrag == Waehrungsfolge.ORIGINAL) S.wfOriginal else "${S.waehrungName(eintrag)} ($eintrag)"

// Kurzform fuer die Zeile im Profil, etwa „EUR · USD · Original“.
fun waehrungsFolgeText(folge: List<String>, basis: String): String =
    Waehrungsfolge.wirksam(folge, basis).joinToString(" · ") { if (it == Waehrungsfolge.ORIGINAL) S.wfOriginalKurz else it }

// Blatt „Waehrungen beim Antippen“ (seit 5.92): oben die gewaehlten Eintraege in ihrer Reihenfolge zum Verschieben und
// Entfernen, darunter alle weiteren zum Hinzufuegen. Jede Aenderung gilt sofort.
@Composable
internal fun ColumnScope.WaehrungsFolgeAuswahl(state: AppState) {
    val folge = Waehrungsfolge.wirksam(Fmt.folge, Fmt.basis)
    val eigen = Fmt.folge.any { it != Waehrungsfolge.ORIGINAL }
    val echte = folge.count { it != Waehrungsfolge.ORIGINAL }
    Text(S.wfTitel, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 12.dp))

    Text(S.wfReihenfolge, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp))
    // Sortieren per Ziehen nach langem Druecken; die Zeilen dazwischen ruecken waehrend des Ziehens nach
    var zieht by remember { mutableStateOf<Int?>(null) }
    var versatz by remember { mutableStateOf(0f) }
    val zeile = with(LocalDensity.current) { 52.dp.toPx() }
    val ziel = zieht?.let { (it + (versatz / zeile).roundToInt()).coerceIn(0, folge.lastIndex) }
    folge.forEachIndexed { i, e ->
        val gezogen = zieht == i
        val schub = when {
            zieht == null || ziel == null || gezogen -> 0f
            i > zieht!! && i <= ziel -> -zeile
            i < zieht!! && i >= ziel -> zeile
            else -> 0f
        }
        Row(
            Modifier.fillMaxWidth().height(52.dp).zIndex(if (gezogen) 1f else 0f)
                .graphicsLayer { translationY = if (gezogen) versatz else schub }
                .background(if (gezogen) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
                .pointerInput(i, folge) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { zieht = i; versatz = 0f },
                        onDrag = { c, d -> c.consume(); versatz += d.y },
                        onDragEnd = {
                            val z = (i + (versatz / zeile).roundToInt()).coerceIn(0, folge.lastIndex)
                            val richtung = if (z > i) 1 else -1
                            var neu = folge
                            for (k in 0 until abs(z - i)) neu = Waehrungsfolge.verschieben(neu, i + richtung * k, richtung)
                            zieht = null; versatz = 0f
                            if (neu != folge) state.waehrungFolgeSetzen(neu)
                        },
                        onDragCancel = { zieht = null; versatz = 0f },
                    )
                }
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${i + 1}.", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(28.dp))
            Text(waehrungsFolgeName(e), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            // Griff: drei Linien, zeigt, dass sich die Zeile ziehen laesst
            val linie = MaterialTheme.colorScheme.onSurfaceVariant
            Canvas(Modifier.size(width = 16.dp, height = 12.dp)) {
                for (k in 0..2) drawLine(linie, Offset(0f, size.height * k / 2f), Offset(size.width, size.height * k / 2f), strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
            }
            // Die letzte echte Waehrung bleibt
            IconButton(onClick = { state.waehrungFolgeSetzen(Waehrungsfolge.umschalten(folge, e)) }, enabled = e == Waehrungsfolge.ORIGINAL || echte > 1) {
                Icon(Icons.Filled.Close, S.newsRemove, modifier = Modifier.size(20.dp))
            }
        }
    }

    val voll = folge.size >= Waehrungsfolge.MAX
    Text(
        if (voll) S.wfVoll(Waehrungsfolge.MAX) else S.add,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 4.dp),
    )
    if (!voll) {
        val weitere = (listOf(Waehrungsfolge.ORIGINAL, Fmt.basis) + Regionen.WAEHRUNGEN).distinct().filter { it !in folge }
        Column(Modifier.fillMaxWidth()) {
            weitere.forEach { e ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card))
                        .clickable { state.waehrungFolgeSetzen(Waehrungsfolge.umschalten(folge, e)) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(waehrungsFolgeName(e), style = MaterialTheme.typography.bodyLarge)
                        if (e == Waehrungsfolge.ORIGINAL) Text(S.wfOriginalHinweis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(12.dp))
                    PlusGlyph(size = 15.dp, color = MaterialTheme.colorScheme.onSurfaceVariant, stroke = 2.dp)
                }
            }
        }
    }
    if (eigen) {
        Text(
            S.wfVorgabe, style = MaterialTheme.typography.labelLarge, color = LocalSignal.current.link,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                .textKlick(8.dp) { state.waehrungFolgeSetzen(emptyList()) }.padding(8.dp),
        )
    }
}
