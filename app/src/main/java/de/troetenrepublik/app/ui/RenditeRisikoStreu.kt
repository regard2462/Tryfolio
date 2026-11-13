package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.data.ValuedPosition
import de.troetenrepublik.app.engine.HeatmapBeschriftung
import de.troetenrepublik.app.engine.Performanceanalyse
import de.troetenrepublik.app.engine.RisikoDiagramme
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Portfolioverlauf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

// [0.73] Rendite-Risiko-Streudiagramm der Positionen: Schwankung waagrecht, Rendite pro Jahr senkrecht, Punktgroesse
// nach Gewicht im Depot; das Depot und der Vergleichsindex (MSCI World) als eigene Marken.

private val STREU_INDEX = Portfolioverlauf.MASSSTAEBE.first()
private const val JAHR_MS = 365L * 86_400_000L

// Wie viele Positionen eine Beschriftung tragen (die groessten).
private const val BESCHRIFTET = 8

private class StreuPunkt(val name: String, val label: String, val schwankung: Double, val rendite: Double, val gewicht: Double?, val art: Art) {
    enum class Art { POSITION, DEPOT, INDEX }
}

private class StreuDaten(val punkte: List<StreuPunkt>, val fehlend: Int)

@Composable
internal fun RenditeRisikoStreuung(state: AppState, items: List<ValuedPosition>) {
    AufklappAbschnitt("streuung-rendite-risiko", S.rsTitel, S.rsInfo(STREU_INDEX.name)) { StreuInhalt(state, items) }
}

@Composable
private fun StreuInhalt(state: AppState, items: List<ValuedPosition>) {
    val fremd = LocalFremdesDepot.current
    val valuation = depotBewertung(state)
    val account = depotKonto(state)
    var jahre by rememberSaveable { mutableStateOf(3) }
    val gruppen = items.filter { !it.position.isDerivative && it.value > 0 }.groupBy { it.position.symbol }
        .map { (s, l) -> Triple(s, l.first().position.name, l.sumOf { it.value }) }.sortedByDescending { it.third }
    if (gruppen.size < 2) { LeerZustand(S.rsLeer); return }
    ToolZeit(S.widgetZeitraum, listOf(1, 3, 5).map { S.toolYearsKurz(it) to it }, jahre) { jahre = it }
    val schluessel = gruppen.joinToString("|") { it.first + ":" + it.third.toLong() } + "#" + jahre + "#" + account.transactions.size
    val daten by produceState<StreuDaten?>(null, schluessel) {
        value = null
        val jetzt = System.currentTimeMillis()
        val von = jetzt - jahre * JAHR_MS
        val reihen = coroutineScope { gruppen.map { g -> async { state.dailyCloses(g.first) } }.awaitAll() }
        val index = state.dailyCloses(STREU_INDEX.symbol)
        val depot = if (fremd != null) null else runCatching { depotWertReihe(state, valuation) }.getOrNull()
        value = withContext(Dispatchers.Default) {
            val gesamt = gruppen.sumOf { it.third }
            val punkte = ArrayList<StreuPunkt>()
            var fehlend = 0
            gruppen.forEachIndexed { i, g ->
                val rr = RisikoDiagramme.renditeRisiko(reihen[i].map { it.t to it.close }, von)
                if (rr == null) fehlend++
                else punkte += StreuPunkt(g.second, streuName(g.first, g.second), rr.schwankung, rr.rendite, g.third / gesamt, StreuPunkt.Art.POSITION)
            }
            depot?.let { d ->
                Performanceanalyse.berechnen(d, account.transactions, account.cash, von, jetzt)?.let { e ->
                    val v = e.volatilitaet
                    val r = e.ttwrorJahr
                    if (v != null && r != null) punkte += StreuPunkt(S.ptYourDepot, S.ptYourDepot, v, r, null, StreuPunkt.Art.DEPOT)
                }
            }
            RisikoDiagramme.renditeRisiko(index.map { it.t to it.close }, von)?.let {
                punkte += StreuPunkt(STREU_INDEX.name, STREU_INDEX.name, it.schwankung, it.rendite, null, StreuPunkt.Art.INDEX)
            }
            StreuDaten(punkte, fehlend)
        }
    }
    val d = daten
    if (d == null) { LeerZustand(S.toolLoading); return }
    if (d.punkte.count { it.art == StreuPunkt.Art.POSITION } < 2) { LeerZustand(S.rsLeer); return }
    StreuDiagramm(d.punkte, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
    if (d.fehlend > 0) LeerZustand(S.rsOhneVerlauf(d.fehlend))
}

// Kuerzel ohne Boersenendung und Praefix, wie in der Gleichlauf-Tabelle; bei einem Zahlenkuerzel der Name.
private fun streuName(symbol: String, name: String) =
    HeatmapBeschriftung.bezeichnung(symbol, name)?.removePrefix("^") ?: name

// Lage des Zeichenbereichs: Platz links fuer die Achsenwerte, oben fuer den Achsentitel, unten fuer Werte und Titel.
private class Rahmen(val groesse: Size, val links: Float, val oben: Float, val unten: Float, val xMax: Double, val yMin: Double, val yMax: Double) {
    val breite get() = groesse.width - links
    val hoehe get() = groesse.height - oben - unten
    fun x(v: Double) = links + (v / xMax).toFloat() * breite
    fun y(v: Double) = oben + ((yMax - v) / (yMax - yMin)).toFloat() * hoehe
}

@Composable
private fun StreuDiagramm(punkte: List<StreuPunkt>, modifier: Modifier = Modifier) {
    val signal = LocalSignal.current
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val text = MaterialTheme.colorScheme.onSurface
    val akzent = MaterialTheme.colorScheme.primary
    val hintergrund = MaterialTheme.colorScheme.background
    val stil = MaterialTheme.typography.labelSmall.copy(color = grau)
    val beschriftung = MaterialTheme.typography.labelSmall.copy(color = text)
    val messer = rememberTextMeasurer()
    val dichte = LocalDensity.current
    val sortiert = remember(punkte) { punkte.sortedWith(compareBy({ it.art != StreuPunkt.Art.POSITION }, { -(it.gewicht ?: 0.0) })) }
    val standard = sortiert.indexOfFirst { it.art == StreuPunkt.Art.DEPOT }.takeIf { it >= 0 } ?: 0
    var gewaehlt by remember(sortiert) { mutableStateOf<Int?>(null) }
    val gezeigt = sortiert[gewaehlt ?: standard]

    val xMax = (sortiert.maxOf { it.schwankung } * 1.1).coerceAtLeast(0.05)
    val yLo = minOf(0.0, sortiert.minOf { it.rendite }) * 1.1
    val yHi = maxOf(0.0, sortiert.maxOf { it.rendite }) * 1.1
    val spanne = (yHi - yLo).coerceAtLeast(0.1)
    val yMax = if (yHi - yLo < 0.1) yLo + spanne else yHi
    val yTicks = remember(yLo, yMax) { listOf(yMax, 0.0, yLo).distinctBy { Math.round(it * 1000) } }
    val yTexte = remember(yTicks, stil) { yTicks.map { messer.measure(Fmt.signedPct(it * 100, 0), stil) } }
    val xTicks = listOf(0.0, xMax / 2, xMax)
    val xTexte = remember(xMax, stil) { xTicks.map { messer.measure(Fmt.pct(it * 100, 0), stil) } }
    val links = yTexte.maxOf { it.size.width } + with(dichte) { 8.dp.toPx() }
    val kopf = with(dichte) { 16.dp.toPx() }
    val fuss = with(dichte) { 34.dp.toPx() }
    val rMin = with(dichte) { 5.dp.toPx() }
    val rMax = with(dichte) { 16.dp.toPx() }
    val rMarke = with(dichte) { 7.dp.toPx() }
    val wMax = sortiert.mapNotNull { it.gewicht }.maxOrNull()?.coerceAtLeast(1e-9) ?: 1.0
    val labels = remember(sortiert, beschriftung) { sortiert.map { messer.measure(it.label, beschriftung) } }
    val prioritaet = remember(sortiert) {
        val besondere = sortiert.indices.filter { sortiert[it].art != StreuPunkt.Art.POSITION }
        besondere + sortiert.indices.filter { sortiert[it].art == StreuPunkt.Art.POSITION }.take(BESCHRIFTET)
    }

    fun rahmen(g: Size) = Rahmen(g, links, kopf, fuss, xMax, yLo, yMax)
    fun marken(r: Rahmen) = sortiert.mapIndexed { i, p ->
        val radius = if (p.gewicht == null) rMarke else rMin + (rMax - rMin) * sqrt(p.gewicht / wMax).toFloat()
        RisikoDiagramme.Marke(r.x(p.schwankung), r.y(p.rendite), radius, labels[i].size.width.toFloat() to labels[i].size.height.toFloat())
    }

    Column(modifier) {
        Text(gezeigt.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 4.dp))
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Wertspalte(S.pvMetric("schwankung"), Fmt.pct(gezeigt.schwankung * 100, 1), text)
            Wertspalte(S.rsRendite, Fmt.signedPct(gezeigt.rendite * 100, 1), signal.forChange(gezeigt.rendite))
            Wertspalte(S.rskGewicht, gezeigt.gewicht?.let { Fmt.pct(it * 100, 1) } ?: "–", text)
        }
        Canvas(
            Modifier.fillMaxWidth().height(260.dp).pointerInput(sortiert, links) {
                detectTapGestures { p ->
                    gewaehlt = RisikoDiagramme.naechster(marken(rahmen(size.toSize())), p.x, p.y, 22.dp.toPx())
                }
            },
        ) {
            val r = rahmen(size)
            val boden = size.height - fuss
            yTicks.forEachIndexed { i, tk ->
                drawLine(grau.copy(alpha = if (tk == 0.0) 0.45f else 0.15f), Offset(links, r.y(tk)), Offset(size.width, r.y(tk)), strokeWidth = 1.dp.toPx())
                val t = yTexte[i]
                drawText(t, topLeft = Offset(links - 4.dp.toPx() - t.size.width, (r.y(tk) - t.size.height / 2f).coerceIn(0f, boden - t.size.height)))
            }
            drawLine(grau.copy(alpha = 0.45f), Offset(links, boden), Offset(size.width, boden), strokeWidth = 1.dp.toPx())
            xTicks.forEachIndexed { i, tk ->
                val t = xTexte[i]
                drawText(t, topLeft = Offset((r.x(tk) - t.size.width / 2f).coerceIn(links, size.width - t.size.width), boden + 3.dp.toPx()))
            }
            val xt = messer.measure(S.toolVolatility, stil)
            drawText(xt, topLeft = Offset(size.width - xt.size.width, size.height - xt.size.height))
            drawText(messer.measure(S.rsRendite, stil), topLeft = Offset(links, 0f))

            val mk = marken(r)
            // Grosse zuerst, damit kleine Punkte nicht verdeckt werden
            val reihenfolge = sortiert.indices.sortedByDescending { mk[it].radius }
            for (i in reihenfolge) {
                val p = sortiert[i]
                val m = mk[i]
                val c = Offset(m.x, m.y)
                when (p.art) {
                    StreuPunkt.Art.POSITION -> {
                        val f = signal.forChange(p.rendite)
                        drawCircle(f.copy(alpha = 0.55f), m.radius, c)
                        drawCircle(f, m.radius, c, style = Stroke(1.dp.toPx()))
                    }
                    StreuPunkt.Art.DEPOT -> {
                        val d = Path().apply { moveTo(c.x, c.y - m.radius); lineTo(c.x + m.radius, c.y); lineTo(c.x, c.y + m.radius); lineTo(c.x - m.radius, c.y); close() }
                        drawPath(d, akzent)
                        drawPath(d, hintergrund, style = Stroke(1.dp.toPx()))
                    }
                    StreuPunkt.Art.INDEX -> {
                        drawCircle(hintergrund, m.radius, c)
                        drawCircle(text, m.radius, c, style = Stroke(2.dp.toPx()))
                    }
                }
                if (gewaehlt == i) drawCircle(text, m.radius + 3.dp.toPx(), c, style = Stroke(1.5.dp.toPx()))
            }
            val plaetze = RisikoDiagramme.beschriftungen(mk, prioritaet, RisikoDiagramme.Rechteck(links, kopf, size.width, boden), abstand = 3.dp.toPx())
            plaetze.forEach { (i, o) -> drawText(labels[i], topLeft = Offset(o.first, o.second)) }
        }
    }
}

@Composable
private fun Wertspalte(label: String, wert: String, farbe: androidx.compose.ui.graphics.Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
        Text(wert, style = MaterialTheme.typography.bodyMedium, color = farbe, maxLines = 1, softWrap = false)
    }
}
