package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

// Eine Linie des Vergleichs: Name fuer die Legende, Punkte (Zeit in ms, Wert), Farbe.
data class Linie(val name: String, val punkte: List<Pair<Long, Double>>, val farbe: Color)

// Marke auf der ersten Linie: Kauf (Dreieck nach oben) oder Verkauf (nach unten), mit Text fuer das Fadenkreuz.
data class Marke(val t: Long, val text: String, val kauf: Boolean)

// Farben fuer weitere Linien, gut unterscheidbar auf dunklem und hellem Grund.
val VERGLEICHS_FARBEN = listOf(
    Color(0xFF4C8DFF), Color(0xFFF5A623), Color(0xFFB57BFF), Color(0xFF2EC4B6), Color(0xFFFF6B9A), Color(0xFF9BC53D),
)

// Linienvergleich fuer Portfolioverlaeufe und Wahrscheinlichkeiten: mehrere Linien mit Legende,
// Kauf- und Verkaufsmarken auf der ersten Linie, Fadenkreuz beim Ziehen (Finger und Maus) mit den Werten aller
// Linien und den Marken des Tages. format beschriftet Werte (etwa „+12,3 %“ oder „63 %“); yMin/yMax fest
// vorgeben, sonst aus den Daten.
@Composable
fun VergleichsChart(
    linien: List<Linie>,
    format: (Double) -> String,
    modifier: Modifier = Modifier,
    hoehe: Dp = 200.dp,
    yMin: Double? = null,
    yMax: Double? = null,
    marken: List<Marke> = emptyList(),
    zeit: (Long) -> String = { Fmt.date(it) },
    nullLinie: Double? = null,
    markenHinweis: String = "",
    // Handelszeit-Achse: Punkte an Wochenenden fallen weg, und jeder Zeitpunkt der Linien bekommt gleich viel
    // Platz. So stehen Naechte und Wochenenden nicht als flache Strecken im Bild.
    handelszeit: Boolean = false,
    // Erklaerung zum Zeitpunkt unter dem Fadenkreuz; ersetzt beim Ziehen die Texte der Marken in der Naehe.
    erklaerung: (@Composable (Long) -> Unit)? = null,
) {
    val sichtbar = remember(linien, handelszeit) {
        val l = linien.filter { it.punkte.size >= 2 }
        if (!handelszeit) l else l.map { x -> x.copy(punkte = x.punkte.filter { !wochenende(it.first) }) }.filter { it.punkte.size >= 2 }
    }
    if (sichtbar.isEmpty()) return
    val t0 = sichtbar.minOf { it.punkte.first().first }
    val t1 = sichtbar.maxOf { it.punkte.last().first }.coerceAtLeast(t0 + 1)
    // Achse: Anteil 0..1 je Zeitpunkt und umgekehrt; bei der Handelszeit-Achse nach der Reihenfolge aller Zeitpunkte
    val stempel = remember(sichtbar, handelszeit) { if (handelszeit) sichtbar.flatMap { l -> l.punkte.map { it.first } }.distinct().sorted().toLongArray() else null }
    fun anteil(t: Long): Double {
        val s = stempel
        if (s == null || s.size < 2) return (t - t0).toDouble() / (t1 - t0)
        val i = java.util.Arrays.binarySearch(s, t).let { if (it >= 0) it else (-it - 2).coerceAtLeast(0) }
        return i.toDouble() / (s.size - 1)
    }
    fun zeitpunkt(f: Float): Long {
        val s = stempel
        if (s == null || s.size < 2) return t0 + (f.coerceIn(0f, 1f) * (t1 - t0)).toLong()
        return s[Math.round(f.coerceIn(0f, 1f) * (s.size - 1))]
    }
    val alle = sichtbar.flatMap { l -> l.punkte.map { it.second } }
    var lo = yMin ?: alle.min()
    var hi = yMax ?: alle.max()
    if (nullLinie != null && yMin == null) { lo = minOf(lo, nullLinie); hi = maxOf(hi, nullLinie) }
    if (hi - lo < 1e-9) { hi += 1.0; lo -= 1.0 }
    val rand = (hi - lo) * 0.06
    if (yMin == null) lo -= rand
    if (yMax == null) hi += rand

    var fadenX by remember { mutableStateOf<Float?>(null) }
    val textMesser = rememberTextMeasurer()
    val achse = MaterialTheme.colorScheme.onSurfaceVariant
    val gitter = MaterialTheme.colorScheme.surfaceContainerHigh
    val auf = LocalSignal.current.up
    val ab = LocalSignal.current.down
    val achsenStil = TextStyle(fontSize = 10.sp, color = achse)

    Column(modifier.fillMaxWidth()) {
        // Werte am Fadenkreuz oder die letzten Werte
        val breiteRef = remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
        val tAuswahl: Long? = fadenX?.let { x -> zeitpunkt(x / breiteRef.value) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                tAuswahl?.let { zeit(it) } ?: zeit(t1),
                style = MaterialTheme.typography.bodySmall, color = achse,
            )
        }
        Box(Modifier.fillMaxWidth().height(hoehe).padding(horizontal = 20.dp, vertical = 6.dp)) {
            Canvas(
                Modifier.fillMaxWidth().height(hoehe)
                    .pointerInput(t0, t1) {
                        awaitEachGesture {
                            val start = awaitFirstDown(requireUnconsumed = false)
                            breiteRef.value = size.width * (1f - RECHTER_RAND)
                            fadenX = start.position.x
                            var horizontal: Boolean? = null
                            while (true) {
                                val ev = awaitPointerEvent()
                                val c = ev.changes.firstOrNull { it.id == start.id } ?: break
                                if (!c.pressed) break
                                val d = c.position - start.position
                                if (horizontal == null && (abs(d.x) > 8f || abs(d.y) > 8f)) horizontal = abs(d.x) >= abs(d.y)
                                if (horizontal == false) break
                                if (horizontal == true) c.consume()
                                fadenX = c.position.x
                            }
                            fadenX = null
                        }
                    },
            ) {
                val w = size.width
                val h = size.height - 16.dp.toPx()
                // Die Linien enden vor dem rechten Rand; dort stehen die Werte der Gitterlinien
                val wLinie = w * (1f - RECHTER_RAND)
                breiteRef.value = wLinie
                fun x(t: Long) = (anteil(t) * wLinie).toFloat()
                fun y(v: Double) = (h - (v - lo) / (hi - lo) * h).toFloat()
                // Gitter mit drei Werten
                for (k in 0..2) {
                    val v = lo + (hi - lo) * (0.1 + 0.4 * k)
                    val yy = y(v)
                    drawLine(gitter, Offset(0f, yy), Offset(w, yy), strokeWidth = 1f)
                    val txt = textMesser.measure(format(v), achsenStil)
                    drawText(txt, topLeft = Offset(w - txt.size.width, yy - txt.size.height - 2f))
                }
                nullLinie?.let { n ->
                    if (n in lo..hi) drawLine(achse.copy(alpha = 0.5f), Offset(0f, y(n)), Offset(w, y(n)), strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                }
                // Zeitachse: Anfang, Mitte, Ende
                listOf(t0, zeitpunkt(0.5f), t1).forEachIndexed { i, t ->
                    val txt = textMesser.measure(zeit(t), achsenStil)
                    val xx = when (i) { 0 -> 0f; 1 -> x(t) - txt.size.width / 2f; else -> w - txt.size.width }
                    drawText(txt, topLeft = Offset(xx, h + 3.dp.toPx()))
                }
                // Linien, die erste zuletzt (oben)
                sichtbar.reversed().forEachIndexed { idx, l ->
                    val p = Path()
                    l.punkte.forEachIndexed { i, (t, v) -> if (i == 0) p.moveTo(x(t), y(v)) else p.lineTo(x(t), y(v)) }
                    val erste = idx == sichtbar.size - 1
                    drawPath(p, l.farbe, style = Stroke(width = (if (erste) 2.5 else 1.8).dp.toPx()))
                }
                val erste = sichtbar.first()
                marken.forEach { m ->
                    if (m.t < t0 || m.t > t1) return@forEach
                    val v = wertAm(erste.punkte, m.t) ?: return@forEach
                    val xx = x(m.t)
                    val yy = y(v)
                    val s = 5.dp.toPx()
                    val dreieck = Path().apply {
                        if (m.kauf) { moveTo(xx, yy - s * 2.2f); lineTo(xx - s, yy - s * 0.6f); lineTo(xx + s, yy - s * 0.6f) }
                        else { moveTo(xx, yy + s * 2.2f); lineTo(xx - s, yy + s * 0.6f); lineTo(xx + s, yy + s * 0.6f) }
                        close()
                    }
                    drawPath(dreieck, if (m.kauf) auf else ab)
                }
                fadenX?.let { fx ->
                    val xx = fx.coerceIn(0f, wLinie)
                    drawLine(achse, Offset(xx, 0f), Offset(xx, h), strokeWidth = 1.dp.toPx())
                    val t = zeitpunkt(xx / wLinie)
                    sichtbar.forEach { l -> wertAm(l.punkte, t)?.let { v -> drawCircle(l.farbe, 4.dp.toPx(), Offset(xx, y(v))) } }
                }
            }
        }
        // Legende mit den Werten am Fadenkreuz bzw. am Ende
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            sichtbar.forEach { l ->
                val v = if (tAuswahl != null) wertAm(l.punkte, tAuswahl) else l.punkte.last().second
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(l.farbe))
                    Spacer(Modifier.width(8.dp))
                    NamenText(l.name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text(v?.let(format) ?: "–", style = MaterialTheme.typography.bodySmall)
                }
            }
            // Marken in der Naehe des Fadenkreuzes, die zwei naechsten; fester Platz, damit beim Ziehen nichts springt
            if (marken.isNotEmpty() || erklaerung != null) {
                Column(Modifier.fillMaxWidth().heightIn(min = if (erklaerung != null) 58.dp else 38.dp).padding(top = 4.dp)) {
                    if (tAuswahl == null) {
                        Text(markenHinweis, style = MaterialTheme.typography.bodySmall, color = achse)
                    } else if (erklaerung != null) {
                        erklaerung(tAuswahl)
                    } else {
                        // die zwei naechsten Marken, solange sie hoechstens ein Zwoelftel des Zeitraums entfernt sind
                        // (bei fuenf Jahren rund fuenf Monate, so steht bei Quartalsmarken immer das naechste Quartal da)
                        val spanne = (t1 - t0) / 12
                        marken.filter { abs(it.t - tAuswahl) <= spanne }.sortedBy { abs(it.t - tAuswahl) }.take(2).forEach { m ->
                            // Vollstaendig mit Umbruch, damit alle Positionen der Marke zu sehen sind
                            Text(m.text, style = MaterialTheme.typography.bodySmall, color = if (m.kauf) auf else ab, modifier = Modifier.padding(bottom = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

// Samstag oder Sonntag in der Zeitzone der Boersen der App (Frankfurt).
fun wochenende(t: Long): Boolean =
    java.time.Instant.ofEpochMilli(t).atZone(de.troetenrepublik.app.engine.MarketHours.zone).dayOfWeek.value >= 6

// Wert einer Reihe zum Zeitpunkt t: der letzte Punkt davor (Stufen), vor dem ersten Punkt null.
fun wertAm(punkte: List<Pair<Long, Double>>, t: Long): Double? {
    if (punkte.isEmpty() || t < punkte.first().first) return null
    var lo = 0
    var hi = punkte.size - 1
    while (lo < hi) {
        val mid = (lo + hi + 1) / 2
        if (punkte[mid].first <= t) lo = mid else hi = mid - 1
    }
    return punkte[lo].second
}

// Anteil der Breite, den die Linien rechts frei lassen.
private const val RECHTER_RAND = 0.07f
