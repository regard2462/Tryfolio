package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.engine.StrategieLabor
import de.troetenrepublik.app.i18n.S
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// Diagramme des Strategietests: Parameter-Landschaft als Heatmap, Verteilung der Zufallsvarianten als Histogramm
// und die Kapitalkurve des Walk-Forward. Alles mit Canvas gezeichnet, Beschriftung direkt an den Achsen.

private fun proJahr(v: Double?): String = v?.let { Fmt.signedPct(it * 100, 1) } ?: "–"

// Ein Raster aus Zellen mit Achsenbeschriftung. xs laufen nach rechts, ys nach unten; ein Streifen hat nur eine Zeile.
// farbe liefert die Zellfarbe, ring markiert eine Zelle mit Ring, punkt die angetippte. Ein Tipp oder Ziehen waehlt
// eine Zelle (onWahl), ohne das Scrollen der Seite zu blockieren.
@Composable
private fun Heatfeld(
    xs: List<Int>, ys: List<Int>,
    xTicks: List<Int>, yTicks: List<Int>, xTitel: String, yTitel: String?,
    farbe: (Int, Int) -> Color,
    ring: Pair<Int, Int>?, punkt: Pair<Int, Int>?,
    onWahl: (Int, Int) -> Unit,
    beschreibung: String,
    zeilenFaktor: Float = 0.8f,
    modifier: Modifier = Modifier,
) {
    val messer = rememberTextMeasurer()
    val achse = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val konturFarbe = MaterialTheme.colorScheme.onSurface
    val waehlen by rememberUpdatedState(onWahl)
    val randLinks = if (yTitel == null) 4.dp else 38.dp
    val randUnten = 34.dp
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val zellB = (maxWidth - randLinks) / xs.size
        val zellH: Dp = if (ys.size == 1) 28.dp else zellB * zeilenFaktor
        Canvas(
            Modifier.fillMaxWidth().height(zellH * ys.size + randUnten)
                .semantics { contentDescription = beschreibung }
                .pointerInput(xs, ys, randLinks) {
                    val links = randLinks.toPx()
                    val b = (size.width - links) / xs.size
                    val h = if (ys.size == 1) 28.dp.toPx() else b * zeilenFaktor
                    fun wahl(p: Offset) {
                        val i = ((p.x - links) / b).toInt()
                        val j = (p.y / h).toInt()
                        if (i in xs.indices && j in ys.indices) waehlen(xs[i], ys[j])
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        wahl(down.position)
                        // Weiter lesen, solange der Finger liegt und die Seite die Bewegung nicht uebernimmt
                        do {
                            val ereignis = awaitPointerEvent()
                            val c = ereignis.changes.firstOrNull() ?: break
                            if (c.isConsumed) break
                            wahl(c.position)
                        } while (ereignis.changes.any { it.pressed })
                    }
                },
        ) {
            val links = randLinks.toPx()
            val b = (size.width - links) / xs.size
            val h = zellH.toPx()
            val spalt = 1.dp.toPx()
            for (j in ys.indices) for (i in xs.indices) {
                drawRoundRect(
                    farbe(xs[i], ys[j]),
                    topLeft = Offset(links + i * b + spalt / 2, j * h + spalt / 2),
                    size = Size(b - spalt, h - spalt),
                    cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
                )
            }
            ring?.let { (x, y) ->
                val i = xs.indexOf(x)
                val j = ys.indexOf(y)
                if (i >= 0 && j >= 0) drawRoundRect(
                    konturFarbe, topLeft = Offset(links + i * b, j * h), size = Size(b, h),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()), style = Stroke(2.dp.toPx()),
                )
            }
            punkt?.let { (x, y) ->
                val i = xs.indexOf(x)
                val j = ys.indexOf(y)
                if (i >= 0 && j >= 0) drawCircle(konturFarbe, radius = min(b, h) * 0.22f, center = Offset(links + (i + 0.5f) * b, (j + 0.5f) * h))
            }
            val unten = h * ys.size
            for (v in xTicks) {
                val i = xs.indexOf(v)
                if (i < 0) continue
                val t = messer.measure(v.toString(), achse)
                val x = (links + (i + 0.5f) * b - t.size.width / 2f).coerceIn(links, size.width - t.size.width)
                drawText(t, topLeft = Offset(x, unten + 2.dp.toPx()))
            }
            val tx = messer.measure(xTitel, achse)
            drawText(tx, topLeft = Offset(links + (size.width - links - tx.size.width) / 2f, unten + 2.dp.toPx() + tx.size.height))
            if (yTitel != null) {
                for (v in yTicks) {
                    val j = ys.indexOf(v)
                    if (j < 0) continue
                    val t = messer.measure(v.toString(), achse)
                    val y = ((j + 0.5f) * h - t.size.height / 2f).coerceIn(0f, unten - t.size.height)
                    drawText(t, topLeft = Offset(links - 3.dp.toPx() - t.size.width, y))
                }
                val ty = messer.measure(yTitel, achse)
                val mx = ty.size.height / 2f
                val my = unten / 2f
                rotate(-90f, pivot = Offset(mx, my)) {
                    drawText(ty, topLeft = Offset(mx - ty.size.width / 2f, my - ty.size.height / 2f))
                }
            }
        }
    }
}

// Rendite pro Jahr aller getesteten Varianten einer Familie gegen Kaufen und Halten, je Variante eine Zelle. Gruen
// steht fuer einen Vorsprung, Rot fuer Rueckstand, die Staerke fuer den Abstand. Der Ring zeigt die Variante, die der
// Zufallstest im Lernzeitraum gewaehlt hat, der Punkt die angetippte Zelle.
@Composable
internal fun ParameterKarte(l: StrategieLabor.Lotterie, familie: StrategieLabor.Familie, pruefzeitraum: Boolean) {
    val signal = LocalSignal.current
    val leer = MaterialTheme.colorScheme.surfaceContainerHigh
    val halten = (if (pruefzeitraum) l.haltenPruef else l.haltenLern).renditeJahr
    val nach = remember(l) { l.zellen.associateBy { it.regel } }
    var angetippt by remember(l, familie) { mutableStateOf<StrategieLabor.Regel?>(null) }
    val aktiv = angetippt ?: l.beste
    fun vorsprung(r: StrategieLabor.Regel): Double? {
        val z = nach[r] ?: return null
        val v = (if (pruefzeitraum) z.pruefJahr else z.lernJahr) ?: return null
        return halten?.let { v - it }
    }
    fun farbe(r: StrategieLabor.Regel): Color {
        val v = vorsprung(r) ?: return leer
        val kraft = (0.15 + 0.6 * min(abs(v) / 0.10, 1.0)).toFloat()
        return (if (v >= 0) signal.up else signal.down).copy(alpha = kraft)
    }
    val z = nach[aktiv]
    val wert = z?.let { if (pruefzeitraum) it.pruefJahr else it.lernJahr }
    VergleichTabelle(
        listOf(VergleichSpalte(S.slDieRegel, regelFarbe(aktiv)), VergleichSpalte(S.slHaltenKurz, regelFarbe(StrategieLabor.REGELN.first()))),
        listOf(VergleichZeile(regelKurzName(aktiv), listOf(proJahr(wert), proJahr(halten)), renditeFarben(wert, halten))),
    )
    val beschreibung = S.slParam
    when (familie) {
        StrategieLabor.Familie.KREUZ -> {
            val xs = (5..100 step 5).toList()
            val ys = (20..250 step 10).toList()
            Heatfeld(
                xs, ys, listOf(5, 25, 50, 75, 100), listOf(20, 50, 100, 150, 200, 250), S.slParamKurz, S.slParamLang,
                { x, y -> farbe(StrategieLabor.Regel(StrategieLabor.Art.KREUZ, x, y)) },
                ring = (l.beste.takeIf { it.art == StrategieLabor.Art.KREUZ })?.let { it.a to it.b },
                punkt = aktiv.takeIf { it.art == StrategieLabor.Art.KREUZ && angetippt != null }?.let { it.a to it.b },
                onWahl = { x, y -> angetippt = StrategieLabor.Regel(StrategieLabor.Art.KREUZ, x, y) },
                beschreibung = beschreibung, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        StrategieLabor.Familie.DURCHSCHNITT -> {
            val xs = (5..250 step 5).toList()
            Heatfeld(
                xs, listOf(0), listOf(5, 50, 100, 150, 200, 250), emptyList(), S.slParamLaenge, null,
                { x, _ -> farbe(StrategieLabor.Regel(StrategieLabor.Art.SMA, x)) },
                ring = (l.beste.takeIf { it.art == StrategieLabor.Art.SMA })?.let { it.a to 0 },
                punkt = aktiv.takeIf { it.art == StrategieLabor.Art.SMA && angetippt != null }?.let { it.a to 0 },
                onWahl = { x, _ -> angetippt = StrategieLabor.Regel(StrategieLabor.Art.SMA, x) },
                beschreibung = beschreibung, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        StrategieLabor.Familie.RSI -> {
            val xs = (10..40 step 5).toList()
            val ys = (60..90 step 5).toList()
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (n in listOf(7, 14, 21)) {
                    Column(Modifier.weight(1f)) {
                        Text(S.slParamTage(n), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Heatfeld(
                            xs, ys, listOf(10, 25, 40), listOf(60, 75, 90), S.slParamKauf, S.slParamVerkauf,
                            { x, y -> farbe(StrategieLabor.Regel(StrategieLabor.Art.RSI, x, y, n)) },
                            ring = (l.beste.takeIf { it.art == StrategieLabor.Art.RSI && it.n == n })?.let { it.a to it.b },
                            punkt = aktiv.takeIf { it.art == StrategieLabor.Art.RSI && it.n == n && angetippt != null }?.let { it.a to it.b },
                            onWahl = { x, y -> angetippt = StrategieLabor.Regel(StrategieLabor.Art.RSI, x, y, n) },
                            beschreibung = beschreibung, zeilenFaktor = 1.3f,
                        )
                    }
                }
            }
        }
        StrategieLabor.Familie.ALLE -> Unit
    }
    if (familie != StrategieLabor.Familie.ALLE) {
        val (nLern, nPruef) = StrategieLabor.nachbarVorsprung(l)
        KeyValueRow(S.slNachbarnLern, proJahr(nLern))
        KeyValueRow(S.slNachbarnPruef, proJahr(nPruef))
    }
}

// Markierung im Histogramm: senkrechte Linie mit Beschriftung am oberen Rand.
internal class HistMarke(val wert: Double, val text: String, val farbe: Color)

// Verteilung von Werten als 30 Saeulen mit senkrechten Linien fuer Vergleichswerte. Links und rechts unter der Achse
// stehen kleinster und groesster Wert, bei Null liegt eine zarte Linie. Beschriftung der Linien direkt am Rand oben.
@Composable
internal fun Histogramm(
    werte: DoubleArray, marken: List<HistMarke>, format: (Double) -> String,
    beschreibung: String, modifier: Modifier = Modifier,
) {
    if (werte.isEmpty()) return
    val messer = rememberTextMeasurer()
    val achse = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val saeule = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    val nullLinie = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val grenzen = (werte.toList() + marken.map { it.wert }).filter { it.isFinite() }
    var lo = grenzen.min()
    var hi = grenzen.max()
    if (hi - lo < 1e-9) { lo -= 0.01; hi += 0.01 }
    val behaelter = 30
    val zaehler = IntArray(behaelter)
    for (w in werte) {
        if (!w.isFinite()) continue
        zaehler[(((w - lo) / (hi - lo)) * behaelter).toInt().coerceIn(0, behaelter - 1)]++
    }
    val hoechster = max(zaehler.max(), 1)
    Canvas(modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = beschreibung }) {
        val kopf = 36.dp.toPx()
        val fuss = 18.dp.toPx()
        val flaeche = size.height - kopf - fuss
        val b = size.width / behaelter
        val spalt = 1.dp.toPx()
        for (i in 0 until behaelter) {
            val h = flaeche * zaehler[i] / hoechster
            if (h > 0f) drawRect(saeule, topLeft = Offset(i * b + spalt / 2, kopf + flaeche - h), size = Size(b - spalt, h))
        }
        fun x(v: Double) = (((v - lo) / (hi - lo)) * size.width).toFloat()
        if (lo < 0.0 && hi > 0.0) {
            drawLine(nullLinie, Offset(x(0.0), kopf), Offset(x(0.0), kopf + flaeche), strokeWidth = 1.dp.toPx())
        }
        val achsenY = kopf + flaeche + 3.dp.toPx()
        val tl = messer.measure(format(lo), achse)
        val th = messer.measure(format(hi), achse)
        drawText(tl, topLeft = Offset(0f, achsenY))
        drawText(th, topLeft = Offset(size.width - th.size.width, achsenY))
        if (lo < 0.0 && hi > 0.0) {
            val t0 = messer.measure(format(0.0), achse)
            val x0 = (x(0.0) - t0.size.width / 2f)
            if (x0 > tl.size.width + 8.dp.toPx() && x0 + t0.size.width < size.width - th.size.width - 8.dp.toPx()) drawText(t0, topLeft = Offset(x0, achsenY))
        }
        // Linien von links nach rechts; liegt eine Beschriftung auf der vorigen, rutscht sie eine Zeile tiefer
        var rechtsEnde = -1e9f
        for (m in marken.sortedBy { it.wert }) {
            val xm = x(m.wert)
            drawLine(m.farbe, Offset(xm, kopf - 4.dp.toPx()), Offset(xm, kopf + flaeche), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            val t = messer.measure(m.text, achse.copy(color = m.farbe))
            val links = (xm - t.size.width / 2f).coerceIn(0f, size.width - t.size.width)
            val zweiteZeile = links < rechtsEnde + 4.dp.toPx()
            drawText(t, topLeft = Offset(links, if (zweiteZeile) 2.dp.toPx() + t.size.height else 2.dp.toPx()))
            if (!zweiteZeile) rechtsEnde = links + t.size.width
        }
    }
}
// Kapitalkurve des Walk-Forward gegen Kaufen und Halten, auf logarithmischer Achse (lange Zeitraeume). Senkrechte
// Haarlinien an den Fenstergrenzen, am Ende der Linien steht ihr Endwert. Ziehen mit dem Finger waehlt einen Punkt
// (selected, gemeldet ueber onScrub); beide Reihen haben dieselben Zeitpunkte.
@Composable
internal fun WalkForwardChart(
    zeiten: List<Long>, regel: List<Double>, halten: List<Double>, grenzen: List<Long>,
    regelFarbe: Color, haltenFarbe: Color, regelText: String, haltenText: String, basis: Double,
    selected: Int?, onScrub: (Int?) -> Unit, beschreibung: String, modifier: Modifier = Modifier,
) {
    val n = zeiten.size
    if (n < 2 || regel.size != n || halten.size != n) return
    val messer = rememberTextMeasurer()
    val achse = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val linie = MaterialTheme.colorScheme.onSurfaceVariant
    val hintergrund = MaterialTheme.colorScheme.background
    val melden by rememberUpdatedState(onScrub)
    val alle = (regel + halten + basis).filter { it > 0 && it.isFinite() }
    val lo = kotlin.math.ln(alle.min())
    val hi = kotlin.math.ln(alle.max()).let { if (it - lo < 1e-6) lo + 1.0 else it }
    val t0 = zeiten.first()
    val t1 = zeiten.last()
    Canvas(
        modifier.fillMaxWidth().height(190.dp).semantics { contentDescription = beschreibung }
            .pointerInput(n) {
                detectHorizontalDragGestures(
                    onDragStart = { melden(((it.x / size.width.coerceAtLeast(1)) * (n - 1)).toInt().coerceIn(0, n - 1)) },
                    onDragEnd = { melden(null) },
                    onDragCancel = { melden(null) },
                    onHorizontalDrag = { c, _ -> melden(((c.position.x / size.width.coerceAtLeast(1)) * (n - 1)).toInt().coerceIn(0, n - 1)) },
                )
            }
            .pointerInput(n) {
                detectTapGestures(onTap = { melden(((it.x / size.width.coerceAtLeast(1)) * (n - 1)).toInt().coerceIn(0, n - 1)) })
            },
    ) {
        val fuss = 18.dp.toPx()
        val kopf = 8.dp.toPx()
        val hoehe = size.height - fuss - kopf
        fun x(i: Int) = size.width * (zeiten[i] - t0).toFloat() / max(t1 - t0, 1L).toFloat()
        fun xt(t: Long) = size.width * (t - t0).toFloat() / max(t1 - t0, 1L).toFloat()
        fun y(v: Double) = (kopf + hoehe * (1.0 - (kotlin.math.ln(max(v, 1e-9)) - lo) / (hi - lo))).toFloat()
        for (g in grenzen) {
            if (g <= t0 || g >= t1) continue
            drawLine(linie.copy(alpha = 0.18f), Offset(xt(g), kopf), Offset(xt(g), kopf + hoehe), strokeWidth = 1.dp.toPx())
        }
        drawLine(linie.copy(alpha = 0.45f), Offset(0f, y(basis)), Offset(size.width, y(basis)), strokeWidth = 1.dp.toPx())
        fun zeichne(werte: List<Double>, farbe: Color) {
            val pfad = Path()
            werte.forEachIndexed { i, v -> if (i == 0) pfad.moveTo(x(i), y(v)) else pfad.lineTo(x(i), y(v)) }
            drawPath(pfad, farbe, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        zeichne(halten, haltenFarbe)
        zeichne(regel, regelFarbe)
        // Beschriftung am rechten Ende: die hoehere Linie oben, bei Nachbarschaft auseinandergeschoben
        val yr = y(regel.last())
        val yh = y(halten.last())
        val tr = messer.measure(regelText, achse.copy(color = regelFarbe))
        val th = messer.measure(haltenText, achse.copy(color = haltenFarbe))
        val abstand = tr.size.height.toFloat() + 2.dp.toPx()
        var yRegel = yr - tr.size.height - 2.dp.toPx()
        var yHalten = yh - th.size.height - 2.dp.toPx()
        if (abs(yRegel - yHalten) < abstand) {
            if (yr <= yh) { yRegel = min(yRegel, yHalten - abstand) } else { yHalten = min(yHalten, yRegel - abstand) }
        }
        yRegel = yRegel.coerceIn(0f, kopf + hoehe - tr.size.height)
        yHalten = yHalten.coerceIn(0f, kopf + hoehe - th.size.height)
        drawText(tr, topLeft = Offset(size.width - tr.size.width, yRegel))
        drawText(th, topLeft = Offset(size.width - th.size.width, yHalten))
        // Zeitachse: Anfang und Ende
        val ta = messer.measure(Fmt.date(t0), achse)
        val te = messer.measure(Fmt.date(t1), achse)
        drawText(ta, topLeft = Offset(0f, size.height - ta.size.height))
        drawText(te, topLeft = Offset(size.width - te.size.width, size.height - te.size.height))
        if (selected != null && selected in 0 until n) {
            val xs = x(selected)
            drawLine(linie.copy(alpha = 0.5f), Offset(xs, kopf), Offset(xs, kopf + hoehe), strokeWidth = 1.dp.toPx())
            for ((werte, farbe) in listOf(halten to haltenFarbe, regel to regelFarbe)) {
                drawCircle(hintergrund, radius = 6.dp.toPx(), center = Offset(xs, y(werte[selected])))
                drawCircle(farbe, radius = 4.dp.toPx(), center = Offset(xs, y(werte[selected])))
            }
        }
    }
}
