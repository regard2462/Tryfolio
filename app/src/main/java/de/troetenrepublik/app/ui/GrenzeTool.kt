package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.Achsen
import de.troetenrepublik.app.engine.Effizienzgrenze
import de.troetenrepublik.app.engine.RisikoDiagramme
import de.troetenrepublik.app.i18n.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot

// Werkzeug „Effizienzgrenze“ (0.78): Schwankung gegen Rendite fuer die Werte des Depots in anderer Gewichtung, mit
// dem heutigen Depot, der geringsten Schwankung, der hoechsten Sharpe-Quote und der Gleichgewichtung als Punkten.
// Rechnung in engine/Effizienzgrenze.kt. Ein Rueckblick, keine Empfehlung; es gibt nichts zu uebernehmen.

private const val GRENZE_JAHR_MS = 365L * 86_400_000L
// Die Kursreihe muss den Zeitraum bis auf diesen Anteil abdecken
private const val GRENZE_LUECKE = 0.1
// Gewichte darunter stehen nicht in der Liste
private const val GRENZE_MIN_GEWICHT = 0.0005

// Eingerechnete Werte (Symbol, Name) in der Reihenfolge der Gewichte, das Ergebnis und die Zahl aller Positionen
private class GrenzeStand(val werte: List<Pair<String, String>>, val ergebnis: Effizienzgrenze.Ergebnis?, val alle: Int)

private suspend fun grenzeLaden(state: AppState, gruppen: List<Triple<String, String, Double>>, jahre: Int): GrenzeStand {
    val auswahl = gruppen.take(Effizienzgrenze.MAX_WERTE)
    val reihen = coroutineScope { auswahl.map { g -> async { state.dailyCloses(g.first) } }.awaitAll() }
    val dauer = jahre * GRENZE_JAHR_MS
    val since = System.currentTimeMillis() - dauer
    // Nur Werte, deren Kurse fast den ganzen Zeitraum abdecken; sonst schrumpft er fuer alle auf den juengsten Wert
    val mit = auswahl.indices.filter { i -> reihen[i].firstOrNull()?.let { it.t <= since + (dauer * GRENZE_LUECKE).toLong() } == true }
    val e = if (mit.size < Effizienzgrenze.MIN_WERTE) null else withContext(Dispatchers.Default) {
        Effizienzgrenze.berechnen(mit.map { auswahl[it].third }, mit.map { reihen[it] }, since)
    }
    return GrenzeStand(mit.map { auswahl[it].first to auswahl[it].second }, e, gruppen.size)
}

@Composable
internal fun GrenzeTool(state: AppState) {
    val valuation = depotBewertung(state)
    // Je Symbol eine Zeile, die groessten zuerst; Derivate bleiben aussen vor
    val gruppen = valuation.positions.filter { !it.position.isDerivative && it.value > 0 }.groupBy { it.position.symbol }
        .map { (s, l) -> Triple(s, l.first().position.name, l.sumOf { it.value }) }.sortedByDescending { it.third }
    if (gruppen.size < Effizienzgrenze.MIN_WERTE) { ToolNote(hgText("g.zuWenig")); return }
    var jahre by rememberSaveable { mutableIntStateOf(3) }
    ToolZeit(S.widgetZeitraum, listOf(1, 3, 5).map { S.toolYearsKurz(it) to it }, jahre) { jahre = it }
    var stand by remember { mutableStateOf<GrenzeStand?>(null) }
    // Die Reihenfolge nach Wert darf wechseln, ohne dass neu gerechnet wird
    val schluessel = gruppen.map { it.first }.sorted().joinToString("|")
    LaunchedEffect(schluessel, jahre) {
        stand = null
        stand = grenzeLaden(state, gruppen, jahre)
    }
    val st = stand ?: run { ToolNote(S.toolLoading); return }
    val e = st.ergebnis ?: run { ToolNote(hgText("g.zuWenig")); return }
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    fun prozent(v: Double) = Fmt.pct(v * 100, 1)
    fun rendite(v: Double) = Fmt.signedPct(v * 100, 1)

    // Kernaussage
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp)) {
        Text(hgText("g.kern", prozent(e.depot.schwankung), rendite(e.depot.rendite)), style = MaterialTheme.typography.titleMedium)
        val g = e.gleicheRendite
        val zweite = when {
            g == null -> hgText("g.kern.minimal", prozent(e.minimal.schwankung), rendite(e.minimal.rendite))
            e.depot.schwankung - g.schwankung < 0.0005 -> hgText("g.kern.aufGrenze")
            else -> hgText("g.kern.gleiche", prozent(g.schwankung))
        }
        Text(zweite, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 2.dp))
    }

    val punkte = listOfNotNull(
        GrenzePunkt("g.depot", e.depot, MaterialTheme.colorScheme.onSurface),
        GrenzePunkt("g.minimal", e.minimal, Kategorie.blau),
        e.sharpe?.let { GrenzePunkt("g.sharpe", it, Kategorie.ocker) },
        GrenzePunkt("g.gleich", e.gleich, Kategorie.farbe(2)),
    )
    GrenzeDiagramm(e, punkte, st.werte.map { it.first })
    // Kurzer Hinweis (historisch, keine Empfehlung) in einer Zeile; Ueberanpassung und Methode hinter dem Info-Symbol
    InfoZeile(hgText("g.hinweis"), hgText("g.titel"), hgText("g.warnung") + "\n\n" + hgText("g.erklaerung"), Modifier.padding(top = 4.dp))

    // Gewichte je Punkt, eingeklappt
    punkte.forEach { p ->
        var offen by rememberSaveable(p.schluessel) { mutableStateOf(false) }
        AufklappZeile(hgText(p.schluessel), hgText("g.punkt.unter", prozent(p.punkt.schwankung), rendite(p.punkt.rendite)), offen) { offen = !offen }
        if (offen) {
            st.werte.indices.sortedByDescending { p.punkt.gewichte[it] }.filter { p.punkt.gewichte[it] >= GRENZE_MIN_GEWICHT }.forEach { i ->
                ShareBar(st.werte[i].second, Fmt.pct(p.punkt.gewichte[i] * 100, 1), p.punkt.gewichte[i], p.farbe)
            }
        }
    }
    if (st.werte.size < st.alle) {
        Text(
            hgText("g.auswahl", Fmt.num(st.werte.size.toDouble(), 0), Fmt.num(st.alle.toDouble(), 0)), style = MaterialTheme.typography.bodySmall, color = grau,
            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp),
        )
    }
}

private class GrenzePunkt(val schluessel: String, val punkt: Effizienzgrenze.Punkt, val farbe: Color)

// Lage aller Elemente im Diagramm (Pixel): wo die Beschriftungen stehen und welche Linien sie zum Punkt ziehen
private class GrenzeLage(
    val x: (Double) -> Float, val y: (Double) -> Float, val kopf: Float, val boden: Float,
    val marken: List<RisikoDiagramme.Marke>, val plaetze: Map<Int, Pair<Float, Float>>, val verbindungen: List<Triple<Int, Offset, Offset>>,
    val grauOhneName: Boolean,
)

// Schwankung (waagerecht) gegen Rendite pro Jahr (senkrecht), beide Achsen auf dem Bereich der Punkte: die Grenze als
// Linie, die einzelnen Werte als kleine graue Punkte mit Kuerzel (wo Platz ist, sonst eine Zeile unter dem Bild), die
// vier Punkte farbig mit Bezeichnung am Punkt. Die vier Bezeichnungen weichen bei Bedarf weiter aus und bekommen dann
// eine feine Linie zum Punkt.
@Composable
private fun GrenzeDiagramm(e: Effizienzgrenze.Ergebnis, punkte: List<GrenzePunkt>, kuerzel: List<String>) {
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val hintergrund = MaterialTheme.colorScheme.background
    val stil = MaterialTheme.typography.labelSmall.copy(color = grau)
    val messer = rememberTextMeasurer()
    val dichte = LocalDensity.current
    val alle = remember(e) { e.grenze + e.einzeln + listOfNotNull(e.depot, e.minimal, e.sharpe, e.gleich) }
    val xB = remember(alle) { Achsen.bereich(alle.minOf { it.schwankung }, alle.maxOf { it.schwankung }, 4, 0.01) }
    val yB = remember(alle) { Achsen.bereich(alle.minOf { it.rendite }, alle.maxOf { it.rendite }, 4, 0.01) }
    val yTexte = remember(yB, stil) { yB.ticks.map { messer.measure(Fmt.signedPct(it * 100, 0), stil) } }
    val xTexte = remember(xB, stil) { xB.ticks.map { messer.measure(Fmt.pct(it * 100, 0), stil) } }
    val xTitel = remember(stil) { messer.measure(S.toolVolatility, stil) }
    val yTitel = remember(stil) { messer.measure(S.rsRendite, stil) }
    val namen = punkte.map { hgText(it.schluessel) }
    val labels = remember(namen, punkte.map { it.farbe }) {
        punkte.mapIndexed { i, p -> messer.measure(namen[i], stil.copy(color = p.farbe)) }
    }
    val kuerzelStil = stil.copy(color = grau.copy(alpha = 0.85f))
    val kuerzelTexte = remember(kuerzel, kuerzelStil) { kuerzel.map { messer.measure(it, kuerzelStil) } }
    val links = yTexte.maxOf { it.size.width } + with(dichte) { 8.dp.toPx() }
    val hoehe = 280.dp
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp)) {
        val breite = with(dichte) { maxWidth.toPx() }
        val hoehePx = with(dichte) { hoehe.toPx() }
        val lage = remember(breite, hoehePx, e, xB, yB, labels, kuerzelTexte, links, dichte) {
            with(dichte) {
                val kopf = 18.dp.toPx()
                val boden = hoehePx - 34.dp.toPx()
                val b = breite - links
                val x = { v: Double -> links + ((v - xB.von) / (xB.bis - xB.von)).toFloat() * b }
                val y = { v: Double -> kopf + ((yB.bis - v) / (yB.bis - yB.von)).toFloat() * (boden - kopf) }
                val radius = 5.5.dp.toPx()
                val klein = 3.dp.toPx()
                val n = punkte.size
                val marken = punkte.mapIndexed { i, p ->
                    RisikoDiagramme.Marke(x(p.punkt.schwankung), y(p.punkt.rendite), radius, labels[i].size.width.toFloat() to labels[i].size.height.toFloat())
                } + e.einzeln.mapIndexed { i, p ->
                    RisikoDiagramme.Marke(x(p.schwankung), y(p.rendite), klein, kuerzelTexte[i].size.width.toFloat() to kuerzelTexte[i].size.height.toFloat())
                }
                // Die Linie der Grenze bleibt frei: gesperrte Felder in kurzem Abstand entlang der Linie
                val frei = 2.dp.toPx()
                val gesperrt = ArrayList<RisikoDiagramme.Rechteck>()
                for (i in 1 until e.grenze.size) {
                    val a = Offset(x(e.grenze[i - 1].schwankung), y(e.grenze[i - 1].rendite))
                    val c = Offset(x(e.grenze[i].schwankung), y(e.grenze[i].rendite))
                    val teile = ((c - a).getDistance() / (2 * frei)).toInt().coerceIn(1, 200)
                    for (k in 0..teile) {
                        val m = a + (c - a) * (k / teile.toFloat())
                        gesperrt += RisikoDiagramme.Rechteck(m.x - frei, m.y - frei, m.x + frei, m.y + frei)
                    }
                }
                val flaeche = RisikoDiagramme.Rechteck(links, kopf, breite, boden)
                val abstand = 3.dp.toPx()
                // Erst die vier Punkte (mit Ausweichen), dann die grauen Kuerzel in den Luecken
                val farbige = RisikoDiagramme.beschriftungen(
                    marken, (0 until n).toList(), flaeche, gesperrt, abstand = abstand, fern = listOf(12.dp.toPx(), 26.dp.toPx(), 44.dp.toPx()),
                )
                fun feld(i: Int, o: Pair<Float, Float>): RisikoDiagramme.Rechteck {
                    val t = marken[i].text ?: (0f to 0f)
                    return RisikoDiagramme.Rechteck(o.first, o.second, o.first + t.first, o.second + t.second)
                }
                val belegt = gesperrt + farbige.map { (i, o) -> feld(i, o) }
                val graue = RisikoDiagramme.beschriftungen(marken, (n until marken.size).toList(), flaeche, belegt, abstand = abstand)
                val verbindungen = farbige.mapNotNull { (i, o) ->
                    val m = marken[i]
                    val r = feld(i, o)
                    val nx = m.x.coerceIn(r.l, r.r)
                    val ny = m.y.coerceIn(r.o, r.u)
                    val d = hypot(nx - m.x, ny - m.y)
                    if (d <= m.radius + abstand + 2.dp.toPx()) null
                    else Triple(i, Offset(m.x + (nx - m.x) / d * (m.radius + 1.dp.toPx()), m.y + (ny - m.y) / d * (m.radius + 1.dp.toPx())), Offset(nx, ny))
                }
                GrenzeLage(x, y, kopf, boden, marken, farbige + graue, verbindungen, e.einzeln.indices.any { (n + it) !in graue })
            }
        }
        Column {
            Canvas(Modifier.fillMaxWidth().height(hoehe)) {
                val x = lage.x
                val y = lage.y
                val boden = lage.boden
                val gewaehlteY = Achsen.ohneUeberlappung(
                    yB.ticks.map { y(it) }, yB.ticks.indices.sortedBy { if (abs(yB.ticks[it]) < 1e-9) 0 else 1 },
                    yTexte.maxOf { it.size.height }.toFloat(), 2.dp.toPx(),
                )
                yB.ticks.forEachIndexed { i, tk ->
                    drawLine(grau.copy(alpha = if (abs(tk) < 1e-9) 0.45f else 0.15f), Offset(links, y(tk)), Offset(size.width, y(tk)), strokeWidth = 1.dp.toPx())
                    if (i in gewaehlteY) {
                        val t = yTexte[i]
                        drawText(t, topLeft = Offset(links - 4.dp.toPx() - t.size.width, (y(tk) - t.size.height / 2f).coerceIn(0f, boden - t.size.height)))
                    }
                }
                drawLine(grau.copy(alpha = 0.45f), Offset(links, boden), Offset(size.width, boden), strokeWidth = 1.dp.toPx())
                xB.ticks.forEachIndexed { i, tk ->
                    val t = xTexte[i]
                    drawText(t, topLeft = Offset((x(tk) - t.size.width / 2f).coerceIn(links, size.width - t.size.width), boden + 3.dp.toPx()))
                }
                drawText(xTitel, topLeft = Offset(size.width - xTitel.size.width, size.height - xTitel.size.height))
                drawText(yTitel, topLeft = Offset(links, 0f))

                // Einzelne Werte
                e.einzeln.forEach { drawCircle(grau.copy(alpha = 0.5f), 3.dp.toPx(), Offset(x(it.schwankung), y(it.rendite))) }
                // Grenze
                if (e.grenze.size >= 2) {
                    val pfad = Path().apply { e.grenze.forEachIndexed { i, p -> if (i == 0) moveTo(x(p.schwankung), y(p.rendite)) else lineTo(x(p.schwankung), y(p.rendite)) } }
                    drawPath(pfad, grau, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                // Linien von weit gerueckten Bezeichnungen zu ihrem Punkt, in der Farbe des Punkts
                lage.verbindungen.forEach { (i, von, bis) -> drawLine(punkte[i].farbe.copy(alpha = 0.7f), von, bis, strokeWidth = 1.dp.toPx()) }
                val radius = 5.5.dp.toPx()
                // Das Depot zuletzt, damit es obenauf liegt
                for (i in punkte.indices.reversed()) {
                    val c = Offset(lage.marken[i].x, lage.marken[i].y)
                    drawCircle(hintergrund, radius + 1.5.dp.toPx(), c)
                    drawCircle(punkte[i].farbe, radius, c)
                }
                lage.plaetze.forEach { (i, o) ->
                    drawText(if (i < punkte.size) labels[i] else kuerzelTexte[i - punkte.size], topLeft = Offset(o.first, o.second))
                }
            }
            if (lage.grauOhneName) Text(hgText("g.legendeGrau"), style = MaterialTheme.typography.labelSmall, color = grau, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
