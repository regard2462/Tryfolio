package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import de.troetenrepublik.app.plattform.horizontalScrollMaus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

// Bausteine der Werkzeuge in Analytics: Regler mit Wertanzeige, Liniendiagramm mit mehreren
// Linien, Faecherdiagramm fuer Simulationen, Anteilsbalken und die Gleichlauf-Tabelle.

// Regler mit Bezeichnung links und dem Wert rechts darueber.
@Composable
fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.titleSmall)
        }
        val steps = if (step > 0f) (((range.endInclusive - range.start) / step).roundToInt() - 1).coerceAtLeast(0) else 0
        ToolSlider(
            value = value,
            onChange = { v -> onChange(if (step > 0f) (Math.round(v / step) * step).coerceIn(range.start, range.endInclusive) else v) },
            range = range,
            steps = steps,
        )
    }
}

// Regler wie die Rechner im Finanzwissen: duenne Spur, runder Knopf in der Textfarbe.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolSlider(value: Float, onChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>, modifier: Modifier = Modifier, steps: Int = 0) {
    val active = MaterialTheme.colorScheme.onSurface
    val inactive = MaterialTheme.colorScheme.surfaceContainerHighest
    Slider(
        value = value.coerceIn(range.start, range.endInclusive),
        onValueChange = onChange,
        modifier = modifier,
        steps = steps,
        valueRange = range,
        thumb = { Box(Modifier.size(22.dp).shadow(2.dp, CircleShape).background(active, CircleShape)) },
        track = { st ->
            val span = (st.valueRange.endInclusive - st.valueRange.start).takeIf { it > 0f } ?: 1f
            val f = ((st.value - st.valueRange.start) / span).coerceIn(0f, 1f)
            Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                val r = CornerRadius(size.height / 2, size.height / 2)
                drawRoundRect(inactive, cornerRadius = r)
                if (f > 0f) drawRoundRect(active, size = Size(size.width * f, size.height), cornerRadius = r)
            }
        },
    )
}

// Zeitraum in einem Werkzeug, als Zeitleiste wie beim Chart.
@Composable
fun <T> ToolZeit(label: String, options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp))
    ZeitLeiste(options.map { it.first }, selected = options.indexOfFirst { it.second == selected },
        onSelect = { onSelect(options[it].second) }, modifier = Modifier.padding(horizontal = 12.dp))
}

// Auswahl aus Pillen mit Bezeichnung darueber (waagerecht blaetterbar).
@Composable
fun <T> ToolChoice(label: String?, options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    if (label != null) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 6.dp))
    }
    Row(
        Modifier.fillMaxWidth().horizontalScrollMaus(rememberScrollState()).padding(horizontal = 20.dp).padding(top = if (label == null) 8.dp else 0.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (l, v) -> Chip(l, v == selected) { onSelect(v) } }
    }
}

@Composable
fun ToolNote(text: String) {
    AufklappText(text, Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
}

@Composable
fun ChartLegend(items: List<Pair<String, Color>>) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        items.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// Mehrere Linien ueber dieselben Punkte (gleichmaessig verteilt wie die Depotkurve), dazu eine
// gepunktete Nulllinie. Ziehen mit dem Finger waehlt einen Punkt (selected, gemeldet ueber onScrub).
@Composable
fun LinesChart(
    lines: List<Pair<List<Double>, Color>>,
    modifier: Modifier = Modifier,
    selected: Int? = null,
    onScrub: (Int?) -> Unit = {},
    baseline: Double? = 0.0,
) {
    val report by rememberUpdatedState(onScrub)
    val n = lines.maxOfOrNull { it.first.size } ?: 0
    // Die Geste liest die Punktzahl ueber einen Zustand: Neue Daten brechen das Ziehen nicht ab.
    val count by rememberUpdatedState(n)
    val dotColor = MaterialTheme.colorScheme.onSurfaceVariant
    val background = MaterialTheme.colorScheme.background
    if (n < 2) { Box(modifier); return }
    val all = lines.flatMap { it.first }.filter { it.isFinite() }
    if (all.isEmpty()) { Box(modifier); return }
    var min = all.min()
    var max = all.max()
    if (baseline != null) { min = minOf(min, baseline); max = maxOf(max, baseline) }
    if (max - min < 1e-9) { max += 1.0; min -= 1.0 }
    val lo = min
    val span = max - min
    Canvas(
        modifier.pointerInput(Unit) {
            fun index(x: Float) = ((x / size.width.coerceAtLeast(1)) * (count - 1)).roundToInt().coerceIn(0, (count - 1).coerceAtLeast(0))
            detectHorizontalDragGestures(
                onDragStart = { report(index(it.x)) },
                onDragEnd = { report(null) },
                onDragCancel = { report(null) },
                onHorizontalDrag = { change, _ -> report(index(change.position.x)) },
            )
        },
    ) {
        val padV = size.height * 0.08f
        fun x(i: Int) = size.width * i / (n - 1).toFloat()
        fun y(v: Double) = (padV + (size.height - 2 * padV) * (1.0 - (v - lo) / span)).toFloat()
        if (baseline != null) {
            val yb = y(baseline)
            drawLine(dotColor, Offset(0f, yb), Offset(size.width, yb), strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 2.dp.toPx())))
        }
        for ((values, color) in lines.asReversed()) {
            if (values.size < 2) continue
            val path = Path()
            var started = false
            values.forEachIndexed { i, v ->
                if (!v.isFinite()) return@forEachIndexed
                if (!started) { path.moveTo(x(i), y(v)); started = true } else path.lineTo(x(i), y(v))
            }
            drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (selected != null && selected in 0 until n) {
            val xs = x(selected)
            drawLine(dotColor.copy(alpha = 0.5f), Offset(xs, 0f), Offset(xs, size.height), strokeWidth = 1.dp.toPx())
            for ((values, color) in lines) {
                val v = values.getOrNull(selected) ?: continue
                if (!v.isFinite()) continue
                drawCircle(background, radius = 6.dp.toPx(), center = Offset(xs, y(v)))
                drawCircle(color, radius = 4.dp.toPx(), center = Offset(xs, y(v)))
            }
        }
    }
}

// Faecherdiagramm einer Simulation je Jahr: aussen der Bereich outer (z. B. 10 bis 90 %), innen
// inner (25 bis 75 %), dazu die mittlere Linie und gestrichelt reference (Eingezahltes).
// Links oben steht der hoechste Wert der Achse, unten Anfang und Ende der Zeitachse.
@Composable
fun FanChart(
    median: DoubleArray,
    outer: Pair<DoubleArray, DoubleArray>?,
    inner: Pair<DoubleArray, DoubleArray>?,
    reference: DoubleArray?,
    color: Color,
    startLabel: String,
    endLabel: String,
    modifier: Modifier = Modifier,
    selected: Int? = null,
    onScrub: (Int?) -> Unit = {},
) {
    val report by rememberUpdatedState(onScrub)
    val n = median.size
    val count by rememberUpdatedState(n)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val refColor = MaterialTheme.colorScheme.onSurfaceVariant
    val background = MaterialTheme.colorScheme.background
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor)
    if (n < 2) { Box(modifier); return }
    val top = maxOf(
        outer?.second?.maxOrNull() ?: 0.0, median.maxOrNull() ?: 0.0, reference?.maxOrNull() ?: 0.0, 1.0,
    ) * 1.05
    Canvas(
        modifier.pointerInput(Unit) {
            fun index(x: Float) = ((x / size.width.coerceAtLeast(1)) * (count - 1)).roundToInt().coerceIn(0, (count - 1).coerceAtLeast(0))
            detectHorizontalDragGestures(
                onDragStart = { report(index(it.x)) },
                onDragEnd = { report(null) },
                onDragCancel = { report(null) },
                onHorizontalDrag = { change, _ -> report(index(change.position.x)) },
            )
        },
    ) {
        val labelH = 18.dp.toPx()
        val h = size.height - labelH
        fun x(i: Int) = size.width * i / (n - 1).toFloat()
        fun y(v: Double) = (h - (v.coerceAtLeast(0.0) / top) * (h - 4.dp.toPx())).toFloat()
        fun band(lowV: DoubleArray, highV: DoubleArray, alpha: Float) {
            val p = Path()
            p.moveTo(x(0), y(highV[0]))
            for (i in 1 until n) p.lineTo(x(i), y(highV[i]))
            for (i in n - 1 downTo 0) p.lineTo(x(i), y(lowV[i]))
            p.close()
            drawPath(p, color.copy(alpha = alpha))
        }
        // Ohne inneren Bereich traegt der aeussere allein und darf kraeftiger sein
        outer?.let { band(it.first, it.second, if (inner == null) 0.30f else 0.20f) }
        inner?.let { band(it.first, it.second, 0.30f) }
        reference?.let { r ->
            val p = Path()
            p.moveTo(x(0), y(r[0]))
            for (i in 1 until n) p.lineTo(x(i), y(r[i]))
            drawPath(p, refColor, style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))
        }
        val line = Path()
        line.moveTo(x(0), y(median[0]))
        for (i in 1 until n) line.lineTo(x(i), y(median[i]))
        drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(labelColor.copy(alpha = 0.4f), Offset(0f, h), Offset(size.width, h), strokeWidth = 1.dp.toPx())
        val topText = measurer.measure(Fmt.compactEuro(top / 1.05), labelStyle)
        drawText(topText, topLeft = Offset(0f, 0f))
        val a = measurer.measure(startLabel, labelStyle)
        drawText(a, topLeft = Offset(0f, h + 2.dp.toPx()))
        val b = measurer.measure(endLabel, labelStyle)
        drawText(b, topLeft = Offset(size.width - b.size.width, h + 2.dp.toPx()))
        if (selected != null && selected in 0 until n) {
            val xs = x(selected)
            drawLine(labelColor.copy(alpha = 0.5f), Offset(xs, 0f), Offset(xs, h), strokeWidth = 1.dp.toPx())
            drawCircle(background, radius = 6.dp.toPx(), center = Offset(xs, y(median[selected])))
            drawCircle(color, radius = 4.dp.toPx(), center = Offset(xs, y(median[selected])))
        }
    }
}

// Zeile mit Bezeichnung, Wert und einem Anteilsbalken darunter (share von 0 bis 1).
@Composable
fun ShareBar(label: String, value: String, share: Double, color: Color, onClick: (() -> Unit)? = null) {
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clip(RoundedCornerShape(Radius.card)).clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(Modifier.fillMaxWidth().padding(top = 5.dp).height(6.dp)) {
            val r = CornerRadius(size.height / 2, size.height / 2)
            drawRoundRect(track, cornerRadius = r)
            val w = (size.width * share.coerceIn(0.0, 1.0)).toFloat()
            if (w > 0f) drawRoundRect(color, size = Size(w.coerceAtLeast(size.height), size.height), cornerRadius = r)
        }
    }
}

// Gleichlauf-Tabelle: je Paar die Korrelation als Zahl und Farbe (Rot = gleichlaeufig, Gruen =
// gegenlaeufig, grau = kein Zusammenhang). labels stehen links und oben.
@Composable
fun CorrelationGrid(labels: List<String>, value: (Int, Int) -> Double?, modifier: Modifier = Modifier) {
    val signal = LocalSignal.current
    val empty = MaterialTheme.colorScheme.surfaceContainerHigh
    val textColor = MaterialTheme.colorScheme.onSurface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val cellStyle = MaterialTheme.typography.labelSmall.copy(color = textColor)
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor)
    val n = labels.size
    if (n < 2) return
    Canvas(modifier) {
        val labelW = 52.dp.toPx()
        val labelH = 20.dp.toPx()
        val cell = minOf((size.width - labelW) / n, (size.height - labelH) / n)
        val gap = 2.dp.toPx()
        for (j in 0 until n) {
            val t = measurer.measure(labels[j].take(5), labelStyle)
            drawText(t, topLeft = Offset(labelW + j * cell + (cell - t.size.width) / 2, 0f))
        }
        for (i in 0 until n) {
            val t = measurer.measure(labels[i].take(6), labelStyle)
            drawText(t, topLeft = Offset(0f, labelH + i * cell + (cell - t.size.height) / 2))
            for (j in 0 until n) {
                val left = labelW + j * cell
                val topY = labelH + i * cell
                val c = if (i == j) null else value(i, j)
                val fill = when {
                    c == null -> empty
                    c >= 0 -> lerp(empty, signal.down, c.toFloat().coerceIn(0f, 1f) * 0.85f)
                    else -> lerp(empty, signal.up, (-c).toFloat().coerceIn(0f, 1f) * 0.85f)
                }
                drawRoundRect(fill, topLeft = Offset(left + gap / 2, topY + gap / 2), size = Size(cell - gap, cell - gap),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()))
                if (c != null) {
                    val text = measurer.measure(Fmt.num(c, 1), cellStyle)
                    drawText(text, topLeft = Offset(left + (cell - text.size.width) / 2, topY + (cell - text.size.height) / 2))
                }
            }
        }
    }
}

// Zwei waagerechte Balken fuer Verluste oder Gewinne (Anteil, -1 bis +1), laengster Balken bei full.
@Composable
fun ChangeBar(label: String, change: Double, valueText: String, color: Color, full: Double = 0.8) {
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(110.dp))
        Canvas(Modifier.weight(1f).height(10.dp)) {
            val r = CornerRadius(size.height / 2, size.height / 2)
            drawRoundRect(track, cornerRadius = r)
            val w = (size.width * (abs(change) / full).coerceIn(0.0, 1.0)).toFloat()
            if (w > 0f) drawRoundRect(color, size = Size(w.coerceAtLeast(size.height), size.height), cornerRadius = r)
        }
        Spacer(Modifier.width(10.dp))
        Text(valueText, style = MaterialTheme.typography.titleSmall, color = color, modifier = Modifier.width(72.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}
