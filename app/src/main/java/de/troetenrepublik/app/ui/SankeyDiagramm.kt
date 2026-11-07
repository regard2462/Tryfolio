package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.troetenrepublik.app.engine.Sankey
import de.troetenrepublik.app.plattform.horizontalScrollMaus

// Sankey-Diagramm (5.94): Knoten als schmale Balken, Baender halb durchsichtig in der Farbe ihres Ziels (Gewinne
// gruen, Kosten und Verluste rot, sonst grau). Beschriftung aussen: die erste Spalte links, die letzte rechts, die
// Spalten dazwischen rechts neben dem Knoten. Die Raender richten sich nach den Beschriftungen, die Spalten stehen so
// weit auseinander, dass jeder Name ganz neben seinen Knoten passt. Reicht die Breite dafuer nicht, laesst sich das
// Diagramm waagerecht schieben; Namen werden weder gekuerzt noch kleiner.
@Composable
internal fun SankeyDiagramm(
    d: Sankey.Diagramm, name: (Sankey.Knoten) -> String, betrag: (Double) -> String,
    modifier: Modifier = Modifier, hoehe: Dp = 260.dp,
) {
    val signal = LocalSignal.current
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    val schrift = MaterialTheme.colorScheme.onSurface
    val messer = rememberTextMeasurer()
    val dichte = LocalDensity.current
    fun farbe(a: Sankey.Art): Color = when (a) {
        Sankey.Art.PLUS -> signal.up
        Sankey.Art.MINUS -> signal.down
        Sankey.Art.NEUTRAL -> neutral
    }
    BoxWithConstraints(modifier.fillMaxWidth().height(hoehe)) {
        val verfuegbar = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val aussen = with(dichte) { 12.dp.toPx() }
        val abstand = with(dichte) { 4.dp.toPx() }
        val knotenBreite = with(dichte) { 8.dp.toPx() }
        val luecke = with(dichte) { 10.dp.toPx() }
        val namensStil = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = schrift)
        val zahlStil = TextStyle(fontSize = 10.sp, color = neutral)
        // Spalten und Werte haengen nicht von der Breite ab: einmal anordnen, Namen und Betraege ungekuerzt messen
        val vorab = remember(d, h) { Sankey.anordnen(d, 1_000f, h, knotenBreite, luecke) }
        val namen = vorab.plaetze.map { name(it.knoten) }
        val betraege = vorab.plaetze.map { betrag(it.wert) }
        val texte: Map<String, Pair<TextLayoutResult, TextLayoutResult>> = remember(vorab, namen, betraege, namensStil, zahlStil, messer) {
            vorab.plaetze.mapIndexed { i, p -> p.knoten.id to (messer.measure(namen[i], namensStil, maxLines = 1) to messer.measure(betraege[i], zahlStil, maxLines = 1)) }.toMap()
        }
        fun textBreite(id: String): Float = texte[id]?.let { maxOf(it.first.size.width, it.second.size.width).toFloat() } ?: 0f
        val spalten = vorab.plaetze.map { it.knoten.spalte }.distinct().sorted()
        val erste = spalten.firstOrNull() ?: 0
        val letzte = spalten.lastOrNull() ?: 0
        fun breitester(nur: (Int) -> Boolean): Float = vorab.plaetze.filter { nur(it.knoten.spalte) }.maxOfOrNull { textBreite(it.knoten.id) } ?: 0f
        val links = breitester { it == erste } + abstand + 2 * dichte.density
        val rechts = breitester { it == letzte && it != erste } + abstand + 2 * dichte.density
        val mitte = breitester { it != erste && it != letzte }
        val n = spalten.size
        // Abstand von Knoten zu Knoten: Knoten, Name daneben und etwas Luft vor dem naechsten Knoten
        val innenMin = if (n >= 3) knotenBreite + (n - 1) * (knotenBreite + abstand + mitte + 8 * dichte.density) else knotenBreite * 2 + 60 * dichte.density
        val gesamt = maxOf(verfuegbar, 2 * aussen + links + rechts + innenMin)
        val innen = gesamt - 2 * aussen - links - rechts
        val lage = remember(d, innen, h) { Sankey.anordnen(d, innen, h, knotenBreite, luecke) }
        val schieben = gesamt > verfuegbar + 0.5f
        val breiteDp = with(dichte) { gesamt.toDp() }
        val scroll = rememberScrollState()
        // Das senkrechte Mausrad bleibt bei der Seite, geschoben wird mit Ziehen oder waagerechtem Rad
        Box(if (schieben) Modifier.fillMaxWidth().nestedScroll(WischenBehalten).horizontalScrollMaus(scroll, senkrechtesRad = false) else Modifier) {
            Canvas(Modifier.width(breiteDp).height(hoehe)) {
                val x0 = aussen + links
                for (b in lage.baender) {
                    val ziel = if (b.nach.knoten.art != Sankey.Art.NEUTRAL) b.nach.knoten.art else b.von.knoten.art
                    val xa = x0 + b.von.x + knotenBreite
                    val xb = x0 + b.nach.x
                    val m = (xa + xb) / 2
                    val pfad = Path().apply {
                        moveTo(xa, b.y0)
                        cubicTo(m, b.y0, m, b.z0, xb, b.z0)
                        lineTo(xb, b.z1)
                        cubicTo(m, b.z1, m, b.y1, xa, b.y1)
                        close()
                    }
                    drawPath(pfad, farbe(ziel).copy(alpha = 0.28f))
                }
                // Beschriftungen je Spalte von oben nach unten, ohne Ueberlappung
                val unten = HashMap<Int, Float>()
                for (p in lage.plaetze) {
                    drawRoundRect(farbe(p.knoten.art), Offset(x0 + p.x, p.y), Size(knotenBreite, p.h.coerceAtLeast(1.5f)), CornerRadius(2 * density))
                    val (nt, zt) = texte[p.knoten.id] ?: continue
                    val th = nt.size.height + zt.size.height.toFloat()
                    var ty = (p.y + p.h / 2 - th / 2).coerceAtLeast(unten[p.knoten.spalte] ?: 0f)
                    if (ty + th > size.height) ty = size.height - th
                    if (ty < (unten[p.knoten.spalte] ?: 0f) - 1f) continue
                    unten[p.knoten.spalte] = ty + th + 2 * density
                    // Linke Spalte rechtsbuendig an den Knoten, alle anderen rechts daneben
                    val linksBuendig = p.knoten.spalte == erste
                    val nx = if (linksBuendig) x0 + p.x - abstand - nt.size.width else x0 + p.x + knotenBreite + abstand
                    val zx = if (linksBuendig) x0 + p.x - abstand - zt.size.width else nx
                    drawText(nt, topLeft = Offset(nx, ty))
                    drawText(zt, topLeft = Offset(zx, ty + nt.size.height))
                }
            }
        }
    }
}
