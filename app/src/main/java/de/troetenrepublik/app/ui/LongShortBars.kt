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
// Balken ueber das Ende hinaus. Ein Tipp auf einen Balken fuehrt zu den Long- bzw. Short-Produkten.
@Composable
fun LongShortBars(longPct: Double, caption: String, onLong: () -> Unit, onShort: () -> Unit) {
    val long = longPct.coerceIn(0.0, 100.0).roundToInt()
    val short = 100 - long
    val green = LocalSignal.current.up
    val shape = RoundedCornerShape(10.dp)
    val style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Mindestbreite, damit „Long 100 %“ bzw. die Zahl immer Platz hat
            val w = (maxWidth * (long / 100f)).coerceAtLeast(112.dp)
            Row(
                Modifier.width(w).height(40.dp).clip(shape).background(green).clickable(onClick = onLong).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(S.longLabel, style = style, color = Color.Black)
                Spacer(Modifier.weight(1f))
                Text(Fmt.pct(long.toDouble(), 0), style = style, color = Color.Black)
            }
        }
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val w = (maxWidth * (short / 100f)).coerceAtLeast(40.dp)
            // Der Balken und die Schrift, die ueber ihn hinauslaufen kann, sind zusammen antippbar
            Box(Modifier.height(40.dp).clip(shape).clickable(onClick = onShort)) {
                Box(Modifier.width(w).height(40.dp).clip(shape).background(green.copy(alpha = 0.2f)))
                Text(
                    "${S.shortLabel}  ${Fmt.pct(short.toDouble(), 0)}",
                    style = style, color = green,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp, end = 14.dp),
                )
            }
        }
        Text(
            caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
