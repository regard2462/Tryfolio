package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.engine.Erklaertext

// Stufenskala eines Erklaertexts (0.78): die Stufen als Felder nebeneinander, links der Grenze getoent, die Grenze als
// Strich, darunter die Bedeutung der beiden Enden. Jedes Feld ist so breit wie seine Beschriftung plus Rand, der Rest
// der Breite verteilt sich gleichmaessig. Passen nicht alle Felder in eine Zeile, folgt eine weitere; nichts wird
// gekuerzt oder verkleinert.
@Composable
internal fun ErklaerSkala(s: Erklaertext.Skala, modifier: Modifier = Modifier) {
    val messer = rememberTextMeasurer()
    val dichte = LocalDensity.current
    val schema = MaterialTheme.colorScheme
    val stufenStil = MaterialTheme.typography.labelMedium
    val randStil = MaterialTheme.typography.labelSmall
    val gesprochen = remember(s) { listOfNotNull(s.links, s.stufen.joinToString(", "), s.rechts).joinToString(", ") }
    BoxWithConstraints(modifier.fillMaxWidth().padding(vertical = 8.dp).semantics { contentDescription = gesprochen }) {
        val breite = constraints.maxWidth
        val plan = remember(s, breite, stufenStil, randStil, dichte) {
            with(dichte) {
                skalaPlan(s, breite, messer, stufenStil, randStil, rand = 6.dp.roundToPx(), hoch = 5.dp.roundToPx(), spalt = 2.dp.roundToPx(), abstand = 6.dp.roundToPx())
            }
        }
        val feld = schema.surfaceContainerHighest
        val getoent = schema.primary.copy(alpha = 0.22f)
        val strich = schema.primary
        val schrift = schema.onSurface
        val leise = schema.onSurfaceVariant
        Canvas(Modifier.fillMaxWidth().height(with(dichte) { plan.hoehe.toDp() })) {
            val ecke = CornerRadius(6.dp.toPx())
            for (f in plan.felder) {
                drawRoundRect(if (f.index <= s.grenze) getoent else feld, Offset(f.x, f.y), Size(f.breite, f.hoehe), ecke)
                drawText(f.text, schrift, Offset(f.x + (f.breite - f.text.size.width) / 2f, f.y + (f.hoehe - f.text.size.height) / 2f))
            }
            plan.grenze?.let { g ->
                drawLine(strich, Offset(g.x, g.y - 3.dp.toPx()), Offset(g.x, g.y + g.hoehe + 3.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
            plan.links?.let { drawText(it.text, leise, Offset(it.x, it.y)) }
            plan.rechts?.let { drawText(it.text, leise, Offset(it.x, it.y)) }
        }
    }
}

internal class SkalaFeld(val index: Int, val text: TextLayoutResult, val x: Float, val y: Float, val breite: Float, val hoehe: Float)
internal class SkalaRand(val text: TextLayoutResult, val x: Float, val y: Float)
internal class SkalaStrich(val x: Float, val y: Float, val hoehe: Float)
internal class SkalaPlan(val felder: List<SkalaFeld>, val grenze: SkalaStrich?, val links: SkalaRand?, val rechts: SkalaRand?, val hoehe: Float)

// Aufbau in Pixeln: Felder zeilenweise, dann die beiden Randtexte (nebeneinander, wenn sie passen, sonst untereinander).
internal fun skalaPlan(
    s: Erklaertext.Skala, breite: Int, messer: TextMeasurer, stufenStil: TextStyle, randStil: TextStyle,
    rand: Int, hoch: Int, spalt: Int, abstand: Int,
): SkalaPlan {
    val innen = (breite - 2 * rand).coerceAtLeast(1)
    val texte = s.stufen.map { messer.measure(it, stufenStil, constraints = Constraints(maxWidth = innen)) }
    // Zeilen fuellen, solange die Mindestbreiten passen
    val zeilen = ArrayList<MutableList<Int>>()
    var summe = 0
    for (i in texte.indices) {
        val b = texte[i].size.width + 2 * rand
        if (zeilen.isEmpty() || summe + spalt + b > breite) { zeilen.add(mutableListOf(i)); summe = b } else { zeilen.last().add(i); summe += spalt + b }
    }
    val felder = ArrayList<SkalaFeld>(texte.size)
    var grenze: SkalaStrich? = null
    var y = 3f
    for (zeile in zeilen) {
        val hoehe = (zeile.maxOf { texte[it].size.height } + 2 * hoch).toFloat()
        val mindest = zeile.sumOf { texte[it].size.width + 2 * rand } + spalt * (zeile.size - 1)
        val mehr = ((breite - mindest).coerceAtLeast(0)).toFloat() / zeile.size
        var x = 0f
        for (i in zeile) {
            val b = texte[i].size.width + 2 * rand + mehr
            felder.add(SkalaFeld(i, texte[i], x, y, b, hoehe))
            if (i == s.grenze) grenze = SkalaStrich(x + b + spalt / 2f, y, hoehe)
            x += b + spalt
        }
        y += hoehe + spalt
    }
    y += abstand - spalt
    val l = s.links?.let { messer.measure(it, randStil, constraints = Constraints(maxWidth = breite.coerceAtLeast(1))) }
    val r = s.rechts?.let { messer.measure(it, randStil, constraints = Constraints(maxWidth = breite.coerceAtLeast(1))) }
    var links: SkalaRand? = null
    var rechts: SkalaRand? = null
    if (l != null && r != null && l.size.width + r.size.width + 2 * rand > breite) {
        links = SkalaRand(l, 0f, y); y += l.size.height
        rechts = SkalaRand(r, (breite - r.size.width).toFloat(), y); y += r.size.height
    } else {
        if (l != null) links = SkalaRand(l, 0f, y)
        if (r != null) rechts = SkalaRand(r, (breite - r.size.width).toFloat(), y)
        y += maxOf(l?.size?.height ?: 0, r?.size?.height ?: 0)
    }
    return SkalaPlan(felder, grenze, links, rechts, y + 3f)
}
