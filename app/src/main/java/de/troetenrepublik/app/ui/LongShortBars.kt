package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.i18n.S
import kotlin.math.roundToInt

// Long/Short-Verteilung: zwei Balken, deren Laenge dem Anteil entspricht. Die Short-Schrift laeuft bei kurzen
// Balken ueber das Ende hinaus. Ein Tipp auf einen Balken fuehrt zu den Long- bzw. Short-Produkten. Ohne Anteil
// (longPct = null) sind beide Balken halb lang und tragen einen Strich statt der Zahl (5.94).
@Composable
fun LongShortBars(longPct: Double?, onLong: () -> Unit, onShort: () -> Unit) {
    val long = longPct?.coerceIn(0.0, 100.0)?.roundToInt()
    val short = long?.let { 100 - it }
    val green = LocalSignal.current.up
    val rot = LocalSignal.current.down
    val shape = RoundedCornerShape(10.dp)
    val style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Mindestbreite, damit „Long 100 %“ bzw. die Zahl immer Platz hat
            val w = if (long == null) maxWidth * 0.5f else (maxWidth * (long / 100f)).coerceAtLeast(112.dp)
            Row(
                Modifier.width(w).height(40.dp).clip(shape).background(green).clickable(onClick = onLong).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(S.long, style = style, color = aufFarbe(green))
                Spacer(Modifier.weight(1f))
                Text(long?.let { Fmt.pct(it.toDouble(), 0) } ?: "–", style = style, color = aufFarbe(green))
            }
        }
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val w = if (short == null) maxWidth * 0.5f else (maxWidth * (short / 100f)).coerceAtLeast(40.dp)
            // Der Balken und die Schrift, die ueber ihn hinauslaufen kann, sind zusammen antippbar
            Box(Modifier.height(40.dp).clip(shape).clickable(onClick = onShort)) {
                Box(Modifier.width(w).height(40.dp).clip(shape).background(rot.copy(alpha = 0.3f)))
                Text(
                    "${S.short}  ${short?.let { Fmt.pct(it.toDouble(), 0) } ?: "–"}",
                    maxLines = 1, softWrap = false,
                    style = style, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp, end = 14.dp),
                )
            }
        }
    }
}

// Platzhalter in der Hoehe der Balken (zwei Balken je 40 dp, Abstand 10 dp), ohne Zahlen,
// solange die Optionsdaten laden. Ein Tipp fuehrt wie die Karte zu den Long-Produkten.
@Composable
fun LongShortPlatzhalter(onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val grau = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Box(Modifier.fillMaxWidth(0.6f).height(40.dp).clip(shape).background(grau).clickable(onClick = onClick))
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth(0.4f).height(40.dp).clip(shape).background(grau).clickable(onClick = onClick))
    }
}
