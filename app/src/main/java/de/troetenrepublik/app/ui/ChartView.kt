package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Range
import de.troetenrepublik.app.i18n.S
import kotlin.math.abs

// Position eines Punkts auf der Zeitachse. Mit domain (Handelsfenster des Tages) steht der Punkt nach seiner Uhrzeit,
// ohne domain gleichmaessig verteilt. startFraction laesst die Linie spaeter beginnen (Depotkurve ab dem ersten Kauf).
private fun xAt(candles: List<Candle>, i: Int, width: Float, startFraction: Float, domain: Pair<Long, Long>?): Float {
    val n = candles.size
    if (domain != null && domain.second > domain.first) {
        val span = (domain.second - domain.first).toFloat()
        return ((candles[i].t - domain.first).coerceIn(0L, domain.second - domain.first) / span) * width * (1f - RAND_RECHTS)
    }
    // Die Linie endet mit etwas Abstand vor dem rechten Rand, auch beim Tag (Handelsschluss)
    val ende = width * (1f - RAND_RECHTS)
    val x0 = width * startFraction.coerceIn(0f, 0.9f)
    return if (n == 1) ende else x0 + (ende - x0) * i / (n - 1).toFloat()
}

// Anteil der Breite, den die Linie rechts frei laesst.
private const val RAND_RECHTS = 0.07f

private fun xPositions(candles: List<Candle>, width: Float, startFraction: Float, domain: Pair<Long, Long>?): FloatArray =
    FloatArray(candles.size) { xAt(candles, it, width, startFraction, domain) }

// Zeitachse des Tagescharts vom ersten Kurs des Tages bis zum Ende des Handelsfensters. Die Achse haengt nicht an der
// Uhrzeit des Aufbaus, sonst verschoebe sich bei jedem Bild jeder Punkt unter dem Finger und das Ziehen braeche ab.
// Liegen die Kurse ganz ausserhalb des Fensters (Yahoo meldet schon den naechsten Handelstag), stehen die Punkte
// gleichmaessig verteilt.
fun dayAxis(range: Range, dayStart: Long, dayEnd: Long, candles: List<Candle>): Pair<Long, Long>? {
    if (range != Range.D1 || dayStart <= 0L || dayEnd <= dayStart || candles.size < 2) return null
    if (candles.last().t < dayStart || candles.first().t > dayEnd) return null
    val start = maxOf(dayStart, candles.first().t)
    return if (dayEnd > start) start to dayEnd else null
}

// Index des Punkts, dessen x-Position x am naechsten liegt (xs aufsteigend); -1 ohne Punkte.
internal fun nearestX(xs: FloatArray, x: Float): Int {
    val n = xs.size
    if (n == 0) return -1
    var lo = 0
    var hi = n
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (xs[mid] < x) lo = mid + 1 else hi = mid
    }
    if (lo == n) return n - 1
    if (lo == 0) return 0
    return if (x - xs[lo - 1] <= xs[lo] - x) lo - 1 else lo
}

// Index des Punkts, der zeitlich t am naechsten liegt (Punkte nach Zeit sortiert); -1 ohne Punkte.
internal fun nearestIndex(candles: List<Candle>, t: Long): Int {
    val n = candles.size
    if (n == 0) return -1
    var lo = 0
    var hi = n
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (candles[mid].t < t) lo = mid + 1 else hi = mid
    }
    if (lo == n) return n - 1
    if (lo == 0) return 0
    return if (t - candles[lo - 1].t <= candles[lo].t - t) lo - 1 else lo
}

// Abstand zweier Kurse im Tageschart, ab dem die Linie als Stufe gezeichnet wird.
private const val LUECKE_MS = 20 * 60_000L

// Merkt sich die x-Positionen der Punkte, solange Daten, Breite und Achse gleich bleiben.
private class XCache {
    private var list: List<Candle>? = null
    private var width = -1f
    private var start = 0f
    private var domain: Pair<Long, Long>? = null
    private var xs = FloatArray(0)

    fun get(candles: List<Candle>, w: Float, startFraction: Float, d: Pair<Long, Long>?): FloatArray {
        if (candles !== list || w != width || startFraction != start || d != domain) {
            xs = xPositions(candles, w, startFraction, d)
            list = candles; width = w; start = startFraction; domain = d
        }
        return xs
    }
}

// Kurschart: Linie ohne Flaechenfuellung, dazu eine gepunktete Nulllinie auf dem
// Startwert. Beim Ziehen mit dem Finger wandert ein Fadenkreuz mit; der gewaehlte Punkt geht ueber
// onScrub nach oben, damit die Kopfzeile ihn anzeigen kann.
@Composable
fun PriceChart(
    candles: List<Candle>,
    modifier: Modifier = Modifier,
    lineColor: Color? = null,
    baseline: Double? = null,
    onScrub: (Candle?) -> Unit = {},
    emptyLabel: String? = null,
    startFraction: Float = 0f,
    domain: Pair<Long, Long>? = null,
    strokeWidth: Dp = 3.dp,
    // Weitere Linien unter dem Kurs, je Punkt der Kurse ein Wert oder null.
    overlays: List<Pair<List<Double?>, Color>> = emptyList(),
) {
    val reportScrub by rememberUpdatedState(onScrub)
    // Die Geste liest Kurse und Achse ueber diese Zustaende. Mit den Daten als Schluessel finge sie bei jedem neuen
    // Kurs von vorn an und das Fadenkreuz bliebe mitten im Ziehen stehen.
    val currentCandles by rememberUpdatedState(candles)
    val currentStart by rememberUpdatedState(startFraction)
    val currentDomain by rememberUpdatedState(domain)
    val signal = LocalSignal.current
    val values = remember(candles) { candles.map { it.close } }
    val color = lineColor ?: signal.line
    val base = baseline ?: values.firstOrNull() ?: 0.0
    val dotColor = MaterialTheme.colorScheme.onSurfaceVariant

    if (values.size < 2) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                emptyLabel ?: S.noHistory,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val extra = remember(overlays) { overlays.flatMap { o -> o.first.filterNotNull() } }
    val rawMin = minOf(values.min(), base, extra.minOrNull() ?: base)
    val rawMax = maxOf(values.max(), base, extra.maxOrNull() ?: base)
    val flatPadding = if (rawMax - rawMin < 1e-9) maxOf(abs(rawMax) * 0.01, 1.0) else 0.0
    val min = rawMin - flatPadding
    val max = rawMax + flatPadding
    val span = max - min

    // Verschwindet der Chart (neue Daten, Seite geschlossen), endet auch das Ziehen.
    DisposableEffect(Unit) { onDispose { reportScrub(null) } }

    Canvas(
        modifier = modifier.semantics { contentDescription = "Chart" }
            .pointerInput(Unit) {
                val cache = XCache()
                fun report(x: Float) {
                    val list = currentCandles
                    if (size.width <= 0 || list.isEmpty()) return
                    val i = nearestX(cache.get(list, size.width.toFloat(), currentStart, currentDomain), x)
                    if (i >= 0) reportScrub(list[i])
                }
                detectHorizontalDragGestures(
                    onDragStart = { report(it.x) },
                    onDragEnd = { reportScrub(null) },
                    onDragCancel = { reportScrub(null) },
                    onHorizontalDrag = { change, _ -> report(change.position.x) },
                )
            }
    ) {
        val w = size.width
        val h = size.height
        val padV = h * 0.10f
        val xs = xPositions(candles, w, startFraction, domain)
        fun yAt(v: Double) = (padV + (h - 2 * padV) * (1.0 - (v - min) / span)).toFloat()

        // Gepunktete Nulllinie auf dem Startwert
        if (base in min..max) {
            val y = yAt(base)
            drawLine(
                color = dotColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
                // Punktmuster der Nulllinie: kurzer Punkt, etwas laengere Luecke.
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 2.dp.toPx())),
            )
        }

        // Indikatorlinien zuerst, damit der Kurs oben liegt; Luecken (noch kein Wert) unterbrechen die Linie
        for ((reihe, farbe) in overlays) {
            val p = Path()
            var offen = false
            for (i in reihe.indices) {
                val v = reihe[i]
                if (v == null || i >= xs.size) { offen = false; continue }
                if (offen) p.lineTo(xs[i], yAt(v)) else { p.moveTo(xs[i], yAt(v)); offen = true }
            }
            drawPath(p, farbe, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        // Auf der Zeitachse des Tages haelt der Kurs ueber eine Handelsluecke (wenige Umsaetze an der Zweitnotiz nach
        // Schluss des Heimatplatzes) seinen Wert und springt erst beim naechsten Umsatz; eine Schraege ueber Stunden
        // zeichnete einen Verlauf, den es nie gab.
        val line = Path().apply {
            moveTo(xs[0], yAt(values[0]))
            for (i in 1 until values.size) {
                if (domain != null && candles[i].t - candles[i - 1].t > LUECKE_MS) lineTo(xs[i], yAt(values[i - 1]))
                lineTo(xs[i], yAt(values[i]))
            }
        }
        drawPath(
            line,
            color,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Bevel),
        )
    }
}

// RSI unter dem Kurschart: Linie von 0 bis 100 auf derselben Zeitachse, gestrichelt die Grenzen 30 und 70.
@Composable
fun RsiPanel(
    candles: List<Candle>,
    rsi: List<Double?>,
    color: Color,
    modifier: Modifier = Modifier,
    startFraction: Float = 0f,
    domain: Pair<Long, Long>? = null,
    // Gestrichelte Grenzen (RSI 30 und 70, Stochastik 20 und 80).
    grenzen: List<Double> = listOf(30.0, 70.0),
) {
    val hilfslinie = MaterialTheme.colorScheme.onSurfaceVariant
    if (candles.size < 2) return
    Canvas(modifier.semantics { contentDescription = "RSI" }) {
        val w = size.width
        val h = size.height
        val xs = xPositions(candles, w, startFraction, domain)
        fun y(v: Double) = (h * (1.0 - v / 100.0)).toFloat()
        for (g in grenzen) {
            drawLine(
                hilfslinie, Offset(0f, y(g)), Offset(w, y(g)), strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
            )
        }
        val p = Path()
        var offen = false
        for (i in rsi.indices) {
            val v = rsi[i]
            if (v == null || i >= xs.size) { offen = false; continue }
            if (offen) p.lineTo(xs[i], y(v)) else { p.moveTo(xs[i], y(v)); offen = true }
        }
        drawPath(p, color, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// Linie unter dem Kurschart ohne feste Skala: von ihrem kleinsten bis zum groessten Wert im
// gezeigten Zeitraum, auf derselben Zeitachse wie der Kurs.
@Composable
fun LinienPanel(
    candles: List<Candle>,
    reihe: List<Double?>,
    color: Color,
    modifier: Modifier = Modifier,
    startFraction: Float = 0f,
    domain: Pair<Long, Long>? = null,
    beschreibung: String = "",
) {
    if (candles.size < 2) return
    val werte = reihe.filterNotNull()
    if (werte.size < 2) return
    val lo = werte.min()
    val hi = werte.max()
    val spanne = (hi - lo).takeIf { it > 0 } ?: 1.0
    Canvas(modifier.semantics { contentDescription = beschreibung }) {
        val w = size.width
        val h = size.height
        val xs = xPositions(candles, w, startFraction, domain)
        fun y(v: Double) = (h * (1.0 - (v - lo) / spanne)).toFloat()
        val p = Path()
        var offen = false
        for (i in reihe.indices) {
            val v = reihe[i]
            if (v == null || i >= xs.size) { offen = false; continue }
            if (offen) p.lineTo(xs[i], y(v)) else { p.moveTo(xs[i], y(v)); offen = true }
        }
        drawPath(p, color, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// MACD unter dem Kurschart: Nulllinie, Abstand zur Signallinie als Balken (gruen darueber, rot darunter),
// MACD-Linie und Signallinie auf derselben Zeitachse.
@Composable
fun MacdPanel(
    candles: List<Candle>,
    linie: List<Double?>,
    signalLinie: List<Double?>,
    histogramm: List<Double?>,
    modifier: Modifier = Modifier,
    startFraction: Float = 0f,
    domain: Pair<Long, Long>? = null,
) {
    val hilfslinie = MaterialTheme.colorScheme.onSurfaceVariant
    val signal = LocalSignal.current
    if (candles.size < 2) return
    val alle = (linie + signalLinie + histogramm).filterNotNull()
    if (alle.isEmpty()) return
    val grenze = alle.maxOf { abs(it) }.takeIf { it > 0 } ?: 1.0
    Canvas(modifier.semantics { contentDescription = "MACD" }) {
        val w = size.width
        val h = size.height
        val xs = xPositions(candles, w, startFraction, domain)
        fun y(v: Double) = (h / 2 * (1.0 - v / grenze)).toFloat()
        drawLine(
            hilfslinie, Offset(0f, y(0.0)), Offset(w, y(0.0)), strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
        )
        val breite = (w / candles.size * 0.6f).coerceIn(1f, 6.dp.toPx())
        for (i in histogramm.indices) {
            val v = histogramm[i] ?: continue
            if (i >= xs.size) break
            drawLine(
                (if (v >= 0) signal.up else signal.down).copy(alpha = 0.45f),
                Offset(xs[i], y(0.0)), Offset(xs[i], y(v)), strokeWidth = breite,
            )
        }
        for ((reihe, farbe) in listOf(linie to MACD_FARBE, signalLinie to MACD_SIGNAL_FARBE)) {
            val p = Path()
            var offen = false
            for (i in reihe.indices) {
                val v = reihe[i]
                if (v == null || i >= xs.size) { offen = false; continue }
                if (offen) p.lineTo(xs[i], y(v)) else { p.moveTo(xs[i], y(v)); offen = true }
            }
            drawPath(p, farbe, style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

// Eigener Neuaufbau-Bereich fuer alles, was dem Fadenkreuz folgt (Kopfzahl, Datum, Fadenkreuz). Liest
// content den gewaehlten Punkt, entsteht beim Ziehen nur dieser Teil neu und nicht die ganze Seite.
@Composable
fun FollowsScrub(content: @Composable () -> Unit) = content()

// Fadenkreuz-Overlay an der ausgewaehlten Stelle.
@Composable
fun ChartCrosshair(
    candles: List<Candle>,
    selected: Candle?,
    color: Color,
    baseline: Double? = null,
    modifier: Modifier = Modifier,
    startFraction: Float = 0f,
    domain: Pair<Long, Long>? = null,
    // Dieselben Indikatorlinien wie im Chart, damit der Punkt auf der Linie sitzt.
    overlays: List<Pair<List<Double?>, Color>> = emptyList(),
) {
    val background = MaterialTheme.colorScheme.background
    if (selected == null || candles.size < 2) return
    val extra = remember(overlays) { overlays.flatMap { o -> o.first.filterNotNull() } }
    val values = remember(candles) { candles.map { it.close } }
    // Kommen waehrend des Ziehens neue Kurse, steht der gewaehlte Punkt eventuell nicht mehr in der
    // Reihe (der Live-Punkt traegt dann eine neue Uhrzeit): dann der zeitlich naechste.
    val idx = remember(selected, candles) {
        candles.indexOfFirst { it.t == selected.t }.takeIf { it >= 0 } ?: nearestIndex(candles, selected.t).coerceAtLeast(0)
    }
    val base = baseline ?: values.first()
    val rawMin = minOf(values.min(), base, extra.minOrNull() ?: base)
    val rawMax = maxOf(values.max(), base, extra.maxOrNull() ?: base)
    val flatPadding = if (rawMax - rawMin < 1e-9) maxOf(abs(rawMax) * 0.01, 1.0) else 0.0
    val min = rawMin - flatPadding
    val span = rawMax + flatPadding - min

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val padV = h * 0.10f
        val x = xAt(candles, idx, w, startFraction, domain)
        val y = (padV + (h - 2 * padV) * (1.0 - (values[idx] - min) / span)).toFloat()
        drawLine(color.copy(alpha = 0.4f), Offset(x, 0f), Offset(x, h), strokeWidth = 1.dp.toPx())
        drawCircle(background, radius = 7.dp.toPx(), center = Offset(x, y))
        drawCircle(color, radius = 4.5.dp.toPx(), center = Offset(x, y))
    }
}

// Balkendiagramm fuer die Finanzen: je Periode ein Balken, negative Werte nach unten.
@Composable
fun BarChart(values: List<Double>, labels: List<String>, color: Color, modifier: Modifier = Modifier, secondary: List<Double>? = null, secondaryColor: Color = Color.Gray) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall.copy(color = labelColor)
    Canvas(modifier) {
        val n = values.size
        if (n == 0) return@Canvas
        val all = values + (secondary ?: emptyList())
        val maxV = all.maxOrNull()?.coerceAtLeast(0.0) ?: 0.0
        val minV = all.minOrNull()?.coerceAtMost(0.0) ?: 0.0
        val span = (maxV - minV).takeIf { it > 0 } ?: 1.0
        val labelH = 18.dp.toPx()
        val h = size.height - labelH
        val zeroY = (h * (maxV / span)).toFloat()
        val slot = size.width / n
        val groups = if (secondary != null) 2 else 1
        val barW = slot * 0.6f / groups
        for (i in 0 until n) {
            fun bar(v: Double, k: Int, c: Color) {
                val top = (h * ((maxV - maxOf(v, 0.0)) / span)).toFloat()
                val bottom = (h * ((maxV - minOf(v, 0.0)) / span)).toFloat()
                val x = slot * i + slot * 0.2f + k * barW
                drawRoundRect(c, Offset(x, top), androidx.compose.ui.geometry.Size(barW - 2.dp.toPx(), (bottom - top).coerceAtLeast(2.dp.toPx())), androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
            }
            bar(values[i], 0, color)
            if (secondary != null) bar(secondary.getOrElse(i) { 0.0 }, 1, secondaryColor)
            val label = labels.getOrNull(i) ?: ""
            val measured = textMeasurer.measure(label, style)
            drawText(measured, topLeft = Offset(slot * i + slot / 2 - measured.size.width / 2f, size.height - labelH + 2.dp.toPx()))
        }
        drawLine(labelColor.copy(alpha = 0.5f), Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 1.dp.toPx())
    }
}

// Zeitraum-Auswahl als Beschriftungen: der aktive Zeitraum weiss und fett, die uebrigen grau.
@Composable
fun RangeSelector(
    ranges: List<Range>,
    selected: Range?,
    onSelect: (Range) -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ranges.forEach { r ->
            val active = r == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(Radius.chip))
                    .selectable(active, role = Role.Tab) { onSelect(r) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    S.rangeLabel(r),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

// Zeitleiste im Stil des Charts fuer beliebige Zeitraeume, die gewaehlte Beschriftung weiss und fett.
@Composable
fun ZeitLeiste(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        // Nur ein Zeitraum zur Wahl: keine Leiste, nur was rechts steht (etwa der Kalender)
        if (labels.size <= 1) {
            Spacer(Modifier.weight(1f))
            trailing?.invoke()
            return@Row
        }
        labels.forEachIndexed { i, l ->
            val active = i == selected
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(Radius.chip)).selectable(active, role = Role.Tab) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    l, style = MaterialTheme.typography.labelMedium, maxLines = 1,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

// Filter-Pille wie in der Derivateliste: aktiv = gefuellt (Akzentfarbe), sonst dunkelgrau.
@Composable
fun FilterPill(
    label: String,
    active: Boolean = false,
    trailing: String? = null,
    chevron: Boolean = false,
    accent: Color? = null,
    onClick: () -> Unit,
) {
    val fill = accent ?: MaterialTheme.colorScheme.onSurface
    val content = if (active) (if (accent != null) Color.Black else MaterialTheme.colorScheme.surface) else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .clip(RoundedCornerShape(Radius.chip))
            .background(if (active) fill else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(start = 18.dp, end = if (chevron) 14.dp else 18.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (trailing != null) "$label  $trailing" else label,
            style = MaterialTheme.typography.titleSmall,
            color = content,
        )
        if (chevron) {
            Spacer(Modifier.width(7.dp))
            ChevronDown(width = 11.dp, color = content)
        }
    }
}
