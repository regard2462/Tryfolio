package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.engine.Monatsraster
import de.troetenrepublik.app.engine.Risiko
import de.troetenrepublik.app.engine.RisikoDiagramme
import de.troetenrepublik.app.i18n.S
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

// [0.73] Aufklappbare Abschnitte mit Unterwasser-Diagramm, Renditeverteilung und rollierenden Kennzahlen, fuer das
// Depot (Performance-Werkzeug) und den einzelnen Wert (Rendite und Risiko), dazu die Zeichnungen.

private const val TAG_MS = 86_400_000L

// Abschnitt mit Ueberschrift, Info-Symbol (Erklaerung im Blatt) und Pfeil; zu beim Oeffnen. Der Inhalt entsteht erst beim Aufklappen.
// Schluessel des Abschnitts, den ein Verweis geoeffnet sehen will (siehe AufklappAbschnitt).
internal var aufklappWunsch by mutableStateOf<String?>(null)

@Composable
internal fun AufklappAbschnitt(schluessel: String, titel: String, info: String?, kern: Pair<String, Color>? = null, inhalt: @Composable () -> Unit) {
    var offen by rememberSaveable(schluessel) { mutableStateOf(false) }
    var infoOffen by remember { mutableStateOf(false) }
    // Ein Verweis von einer anderen Seite oeffnet den Abschnitt und holt ihn ins Bild
    val insBild = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    val gewuenscht = aufklappWunsch == schluessel
    LaunchedEffect(gewuenscht) {
        if (!gewuenscht) return@LaunchedEffect
        offen = true
        kotlinx.coroutines.delay(400)
        runCatching { insBild.bringIntoView() }
        aufklappWunsch = null
    }
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().bringIntoViewRequester(insBild).clickable(role = Role.Button) { offen = !offen }.heightIn(min = 48.dp).padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(titel, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        // Kernzahl eingeklappt: die Zahl dahinter sieht man ohne Aufklappen
        if (kern != null && !offen) Text(kern.first, style = MaterialTheme.typography.bodyMedium, color = kern.second, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 8.dp))
        if (info != null) IconButton(onClick = { infoOffen = true }) {
            Icon(Icons.Outlined.Info, S.infoErklaerung, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        AufklappPfeil(offen, Modifier.padding(end = 12.dp))
    }
    if (infoOffen && info != null) MarktInfoBlatt(titel, info) { infoOffen = false }
    if (offen) Column(Modifier.fillMaxWidth()) { inhalt() }
}

@Composable
internal fun LeerZustand(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
}

// Die drei Abschnitte zu einer Reihe (Zeitpunkt, Wert). unterwasser ersetzt den ersten Abschnitt, wenn der Aufrufer ihn selbst zeigt.
@Composable
internal fun RisikoAbschnitte(schluessel: String, reihe: List<Pair<Long, Double>>, handelstage: Double = 252.0, monatsraster: Boolean = true, unterwasser: (@Composable () -> Unit)? = null) {
    val signal = LocalSignal.current
    // Kernzahlen der zugeklappten Zeilen: groesster Rueckgang, VaR 95 %, Rendite der letzten 12 Monate
    val kernen = remember(reihe, signal) {
        val rueck = RisikoDiagramme.unterwasser(reihe).takeIf { it.size >= 20 }?.minOfOrNull { it.second }
            ?.let { Fmt.signedPct(it, 1) to signal.down }
        val va = Risiko.kennzahlen(reihe)?.var95?.let { "VaR 95 % −" + Fmt.pct(it * 100, 1) to signal.down }
        val roll = RisikoDiagramme.rollierendeRendite(reihe).lastOrNull()?.second?.let { Fmt.signedPct(it * 100, 1) to signal.forChange(it) }
        Triple(rueck, va, roll)
    }
    AufklappAbschnitt("$schluessel-unterwasser", S.rdUnterwasser, S.rdUnterwasserInfo, kernen.first) {
        if (unterwasser != null) unterwasser() else UnterwasserInhalt(reihe)
    }
    AufklappAbschnitt("$schluessel-verteilung", S.rdVerteilung, S.rdVerteilungInfo, kernen.second) { VerteilungInhalt(reihe) }
    AufklappAbschnitt("$schluessel-rollierend", S.rdRollierend, S.rdRollierendInfo, kernen.third) { RollierendInhalt(reihe, handelstage) }
    // Raster Jahr mal Monat; ohne Monatswerte erscheint nichts. Das Depot zeigt sein Raster selbst (Performance-Werkzeug).
    val raster = remember(reihe) { if (monatsraster) Monatsraster.aus(reihe, java.time.ZoneId.systemDefault()) else null }
    if (raster != null) {
        val kern = rasterKern(raster)?.let { (jahr, r) -> rasterText("kern", jahr.toString(), Fmt.signedPct(r * 100.0, 1)) to signal.forChange(r) }
        AufklappAbschnitt("$schluessel-raster", rasterText("titel"), rasterText("info"), kern) { RasterTabelle(raster) }
    }
}

@Composable
private fun UnterwasserInhalt(reihe: List<Pair<Long, Double>>) {
    val dd = remember(reihe) { RisikoDiagramme.unterwasser(reihe) }
    if (dd.size < 20) { LeerZustand(S.rdLeerUnterwasser); return }
    VerlaufDiagramm(dd, LocalSignal.current.down, { Fmt.signedPct(it, 1) }, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), fuellen = true)
}

private class Verteilung(val h: RisikoDiagramme.Histogramm?, val var95: Double?, val es95: Double?)

@Composable
private fun VerteilungInhalt(reihe: List<Pair<Long, Double>>) {
    val v = remember(reihe) {
        val k = Risiko.kennzahlen(reihe)
        val h = RisikoDiagramme.histogramm(Risiko.tagesrenditen(reihe), einschliessen = listOfNotNull(k?.var95?.let { -it }, k?.es95?.let { -it }))
        Verteilung(h, k?.var95, k?.es95)
    }
    val h = v.h
    if (h == null) { LeerZustand(S.rdLeerVerteilung); return }
    HistogrammDiagramm(h, v.var95, v.es95, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
    val signal = LocalSignal.current
    v.var95?.let { KeyValueRow(S.pvMetric("var"), "−" + Fmt.pct(it * 100, 1), signal.down) }
    v.es95?.let { KeyValueRow(S.pvMetric("es"), "−" + Fmt.pct(it * 100, 1), signal.down) }
}

private class Rollierend(val rendite: List<Pair<Long, Double>>, val schwankung: List<Pair<Long, Double>>)

@Composable
private fun RollierendInhalt(reihe: List<Pair<Long, Double>>, handelstage: Double) {
    val r = remember(reihe, handelstage) {
        Rollierend(
            RisikoDiagramme.rollierendeRendite(reihe).map { it.first to it.second * 100 },
            RisikoDiagramme.rollierendeSchwankung(reihe, proJahr = handelstage).map { it.first to it.second * 100 },
        )
    }
    val farbe = MaterialTheme.colorScheme.primary
    Zwischenkopf(S.rdRoll12)
    if (r.rendite.size < 2) LeerZustand(S.rdLeerRoll12)
    else VerlaufDiagramm(r.rendite, farbe, { Fmt.signedPct(it, 1) }, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
    Zwischenkopf(S.rdRoll60)
    if (r.schwankung.size < 2) LeerZustand(S.rdLeerRoll60)
    else VerlaufDiagramm(r.schwankung, farbe, { Fmt.pct(it, 1) }, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
}

// Linie ueber die Zeit (Zeitachse linear, Luecken ueber lueckeMs reissen die Linie ab) mit Werten an der linken Achse und
// Daten unten. Ziehen mit dem Finger zeigt Tag und Wert in der Zeile darueber (ohne Finger der letzte Punkt).
// linien: waagrechte Linien (Wert, Farbe), etwa Schwellen; sie liegen immer im gezeigten Bereich.
// Achsenbeschriftung: ab 20 Jahren Spanne nur das vierstellige Jahr (sonst waere 1881 ein "81")
private fun achsenDatum(t: Long, spanne: Long): String =
    if (spanne > 20 * 365 * TAG_MS) Fmt.date(t).takeLast(4) else Fmt.shortDate(t)

@Composable
internal fun VerlaufDiagramm(
    punkte: List<Pair<Long, Double>>, farbe: Color, format: (Double) -> String, modifier: Modifier = Modifier,
    fuellen: Boolean = false, hoehe: Dp = 150.dp, lueckeMs: Long = 14 * TAG_MS, linien: List<Pair<Double, Color>> = emptyList(),
) {
    val n = punkte.size
    if (n < 2) return
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val hintergrund = MaterialTheme.colorScheme.background
    val stil = MaterialTheme.typography.labelSmall.copy(color = grau)
    val messer = rememberTextMeasurer()
    val dichte = LocalDensity.current
    var gewaehlt by remember(punkte) { mutableStateOf<Int?>(null) }
    val gezeigt = (gewaehlt ?: (n - 1)).coerceIn(0, n - 1)
    val tMin = punkte.first().first
    val tMax = punkte.last().first
    val bereich = remember(punkte, linien) {
        var lo = minOf(punkte.minOf { it.second }, linien.minOfOrNull { it.first } ?: 0.0, 0.0)
        var hi = maxOf(punkte.maxOf { it.second }, linien.maxOfOrNull { it.first } ?: 0.0, 0.0)
        if (hi - lo < 1e-9) { hi += 1.0; lo -= 1.0 }
        lo to hi
    }
    val yMin = bereich.first
    val yMax = bereich.second
    val ticks = remember(bereich) { listOf(yMax, yMin) + (if (yMin < 0 && yMax > 0) listOf(0.0) else emptyList()) }
    val tickTexte = remember(ticks, stil) { ticks.map { messer.measure(format(it), stil) } }
    val rand = with(dichte) { 8.dp.toPx() }
    val gutter = tickTexte.maxOf { it.size.width } + rand
    Column(modifier) {
        Text(
            Fmt.date(punkte[gezeigt].first) + " · " + format(punkte[gezeigt].second),
            style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(bottom = 4.dp),
        )
        Canvas(
            Modifier.fillMaxWidth().height(hoehe).pointerInput(punkte, gutter) {
                fun index(x: Float): Int {
                    val b = (size.width - gutter).coerceAtLeast(1f)
                    val t = tMin + ((x - gutter) / b).coerceIn(0f, 1f).toDouble() * (tMax - tMin)
                    var lo = 0
                    var hi = n - 1
                    while (lo < hi) { val mid = (lo + hi) / 2; if (punkte[mid].first < t) lo = mid + 1 else hi = mid }
                    return if (lo > 0 && abs(punkte[lo - 1].first - t) <= abs(punkte[lo].first - t)) lo - 1 else lo
                }
                detectHorizontalDragGestures(
                    onDragStart = { gewaehlt = index(it.x) },
                    onDragEnd = { gewaehlt = null },
                    onDragCancel = { gewaehlt = null },
                    onHorizontalDrag = { change, _ -> gewaehlt = index(change.position.x) },
                )
            },
        ) {
            val unten = 18.dp.toPx()
            val oben = 6.dp.toPx()
            val b = size.width - gutter
            val h = size.height - unten - oben
            val spanneT = (tMax - tMin).coerceAtLeast(1L).toFloat()
            fun x(t: Long) = gutter + (t - tMin).toFloat() / spanneT * b
            fun y(v: Double) = oben + ((yMax - v) / (yMax - yMin)).toFloat() * h
            ticks.forEachIndexed { i, tk ->
                drawLine(grau.copy(alpha = if (tk == 0.0) 0.45f else 0.15f), Offset(gutter, y(tk)), Offset(size.width, y(tk)), strokeWidth = 1.dp.toPx())
                val t = tickTexte[i]
                drawText(t, topLeft = Offset(gutter - rand / 2 - t.size.width, (y(tk) - t.size.height / 2f).coerceIn(0f, size.height - unten - t.size.height)))
            }
            for ((wert, f) in linien) drawLine(f, Offset(gutter, y(wert)), Offset(size.width, y(wert)), strokeWidth = 1.dp.toPx())
            val pfad = Path()
            var anfang = 0
            fun fuelle(von: Int, bis: Int) {
                if (!fuellen || bis <= von) return
                val f = Path()
                f.moveTo(x(punkte[von].first), y(0.0))
                for (i in von..bis) f.lineTo(x(punkte[i].first), y(punkte[i].second))
                f.lineTo(x(punkte[bis].first), y(0.0))
                f.close()
                drawPath(f, farbe.copy(alpha = 0.18f))
            }
            for (i in 0 until n) {
                val neu = i == 0 || punkte[i].first - punkte[i - 1].first > lueckeMs
                if (neu) { if (i > 0) fuelle(anfang, i - 1); anfang = i; pfad.moveTo(x(punkte[i].first), y(punkte[i].second)) }
                else pfad.lineTo(x(punkte[i].first), y(punkte[i].second))
            }
            fuelle(anfang, n - 1)
            drawPath(pfad, farbe, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            val a = messer.measure(achsenDatum(tMin, tMax - tMin), stil)
            drawText(a, topLeft = Offset(gutter, size.height - unten + 3.dp.toPx()))
            val e = messer.measure(achsenDatum(tMax, tMax - tMin), stil)
            drawText(e, topLeft = Offset(size.width - e.size.width, size.height - unten + 3.dp.toPx()))
            gewaehlt?.let { gi ->
                val xs = x(punkte[gi.coerceIn(0, n - 1)].first)
                val ys = y(punkte[gi.coerceIn(0, n - 1)].second)
                drawLine(grau.copy(alpha = 0.5f), Offset(xs, oben), Offset(xs, oben + h), strokeWidth = 1.dp.toPx())
                drawCircle(hintergrund, radius = 6.dp.toPx(), center = Offset(xs, ys))
                drawCircle(farbe, radius = 4.dp.toPx(), center = Offset(xs, ys))
            }
        }
    }
}

// Stellen fuer Prozentwerte an den Klassenraendern: 0,25 Prozentpunkte Breite brauchen zwei, 0,5 eine.
private fun stellen(breiteProzent: Double): Int = when {
    abs(breiteProzent - kotlin.math.round(breiteProzent)) < 1e-9 -> 0
    abs(breiteProzent * 10 - kotlin.math.round(breiteProzent * 10)) < 1e-9 -> 1
    else -> 2
}

// Balken je Klasse (Anteil der Tage), VaR und ES als senkrechte Linien mit Beschriftung oben. Ziehen waehlt eine Klasse.
@Composable
internal fun HistogrammDiagramm(h: RisikoDiagramme.Histogramm, var95: Double?, es95: Double?, modifier: Modifier = Modifier, hoehe: Dp = 170.dp) {
    val signal = LocalSignal.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val stil = MaterialTheme.typography.labelSmall.copy(color = grau)
    val marke = MaterialTheme.typography.labelSmall.copy(color = signal.down)
    val messer = rememberTextMeasurer()
    val dichte = LocalDensity.current
    val m = h.anzahl.size
    val groesste = h.anzahl.max().coerceAtLeast(1)
    var gewaehlt by remember(h) { mutableStateOf<Int?>(null) }
    val st = stellen(h.breite * 100)
    val kLo = (h.untergrenze / h.breite).roundToInt()
    val obenText = remember(h, stil) { messer.measure(Fmt.pct(groesste.toDouble() / h.n * 100, if (groesste * 20 < h.n) 1 else 0), stil) }
    val nullText = remember(stil) { messer.measure(Fmt.pct(0.0, 0), stil) }
    val gutter = maxOf(obenText.size.width, nullText.size.width) + with(dichte) { 8.dp.toPx() }
    fun klasseText(i: Int): String {
        val anteil = Fmt.pct(h.anzahl[i].toDouble() / h.n * 100, 1)
        return when {
            i == 0 && h.unterlauf > 0 -> S.rdKlasseBis(Fmt.signedPct(h.rand(1) * 100, st), h.anzahl[i], anteil)
            i == m - 1 && h.ueberlauf > 0 -> S.rdKlasseAb(Fmt.signedPct(h.rand(i) * 100, st), h.anzahl[i], anteil)
            else -> S.rdKlasse(Fmt.signedPct(h.rand(i) * 100, st), Fmt.signedPct(h.rand(i + 1) * 100, st), h.anzahl[i], anteil)
        }
    }
    Column(modifier) {
        Text(gewaehlt?.let { klasseText(it.coerceIn(0, m - 1)) } ?: S.rdTage(h.n), style = MaterialTheme.typography.bodySmall, color = grau, modifier = Modifier.padding(bottom = 4.dp))
        Canvas(
            Modifier.fillMaxWidth().height(hoehe).pointerInput(h, gutter) {
                fun klasse(x: Float) = (((x - gutter) / (size.width - gutter).coerceAtLeast(1f)) * m).toInt().coerceIn(0, m - 1)
                detectHorizontalDragGestures(
                    onDragStart = { gewaehlt = klasse(it.x) },
                    onDragEnd = { gewaehlt = null },
                    onDragCancel = { gewaehlt = null },
                    onHorizontalDrag = { change, _ -> gewaehlt = klasse(change.position.x) },
                )
            },
        ) {
            val kopf = 16.dp.toPx()
            val fuss = 18.dp.toPx()
            val b = size.width - gutter
            val boden = size.height - fuss
            val hoeheBalken = boden - kopf
            val breiteKlasse = b / m
            fun x(v: Double) = gutter + ((v - h.untergrenze) / (h.obergrenze - h.untergrenze)).toFloat() * b
            for (i in 0 until m) {
                val mitte = (h.rand(i) + h.rand(i + 1)) / 2
                val tail = var95 != null && h.rand(i + 1) <= -var95 + 1e-12
                val grundFarbe = if (mitte < 0) signal.down else signal.up
                val alpha = if (gewaehlt == i) 1f else if (tail) 0.85f else 0.45f
                val bh = h.anzahl[i].toFloat() / groesste * hoeheBalken
                if (bh > 0f) drawRect(grundFarbe.copy(alpha = alpha), Offset(gutter + i * breiteKlasse + 0.5.dp.toPx(), boden - bh), Size((breiteKlasse - 1.dp.toPx()).coerceAtLeast(1f), bh))
            }
            drawLine(grau.copy(alpha = 0.45f), Offset(gutter, boden), Offset(size.width, boden), strokeWidth = 1.dp.toPx())
            drawText(obenText, topLeft = Offset(gutter - 4.dp.toPx() - obenText.size.width, kopf))
            drawText(nullText, topLeft = Offset(gutter - 4.dp.toPx() - nullText.size.width, boden - nullText.size.height))
            // Achsenwerte an Klassenraendern, mit genug Abstand, die Null immer dabei
            val schritt = ceil(56.dp.toPx() / breiteKlasse).toInt().coerceAtLeast(1)
            for (j in 0..m) {
                if (Math.floorMod(kLo + j, schritt) != 0) continue
                val t = messer.measure(Fmt.signedPct(h.rand(j) * 100, st), stil)
                drawText(t, topLeft = Offset((gutter + j * breiteKlasse - t.size.width / 2f).coerceIn(gutter - t.size.width / 2f, size.width - t.size.width), boden + 3.dp.toPx()))
            }
            // ES gestrichelt links der Linie beschriftet, VaR durchgezogen rechts davon
            es95?.let { es ->
                val xs = x(-es)
                drawLine(signal.down, Offset(xs, kopf - 2.dp.toPx()), Offset(xs, boden), strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                val t = messer.measure("ES " + Fmt.signedPct(-es * 100, 1), marke)
                drawText(t, topLeft = Offset((xs - 4.dp.toPx() - t.size.width).coerceAtLeast(gutter), 0f))
            }
            var95?.let { v ->
                val xs = x(-v)
                drawLine(signal.down, Offset(xs, kopf - 2.dp.toPx()), Offset(xs, boden), strokeWidth = 1.5.dp.toPx())
                val t = messer.measure("VaR " + Fmt.signedPct(-v * 100, 1), marke)
                drawText(t, topLeft = Offset((xs + 4.dp.toPx()).coerceAtMost(size.width - t.size.width), 0f))
            }
        }
    }
}
