package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.troetenrepublik.app.engine.HeatmapBeschriftung
import de.troetenrepublik.app.engine.HeatmapZoomLage
import de.troetenrepublik.app.engine.Treemap
import de.troetenrepublik.app.i18n.S

// Eine Firma der Unternehmens-Heatmap. kuerzel: Boersenkuerzel oder null (dann steht nur der Name, wo er ganz passt);
// name: voller Name, nie gekuerzt; kgv: null ohne Angabe oder bei Verlust.
internal class FirmaKachel(val kuerzel: String?, val name: String, val wert: Double, val aenderung: Double?, val kgv: Double?, val onClick: (() -> Unit)?)

private const val FIRMEN_VOLL = 3.0
private const val FIRMEN_MAX_ZOOM = 8f
private val FIRMEN_STUFEN = listOf(1f, 1.5f, 2f, 3f, 4f, 6f, 8f)

private class FirmenText(val titel: TextLayoutResult?, val prozent: TextLayoutResult?, val kgv: TextLayoutResult?)

// Unternehmens-Heatmap eines Landes: Flaeche nach Boersenwert, Farbe nach Tagesaenderung (Gewinn und Verlust ueber
// LocalSignal). In jeder Kachel steht der volle Name, wo er ganz hineinpasst, sonst das Kuerzel, sonst nichts; darunter die
// Aenderung und das KGV, je nur ganz. Zwei Finger vergroessern und verschieben (am Rechner das Mausrad mit Strg), ein
// Doppeltipp setzt zurueck, ein Tipp oeffnet das Papier. Ein Finger bei Zoom 1 rollt weiter die Seite.
@Composable
internal fun FirmenTreemap(kacheln: List<FirmaKachel>, hoehe: Dp = 220.dp) {
    if (kacheln.size < 2) return
    val signal = LocalSignal.current
    val heat = heatFarben()
    val messer = rememberTextMeasurer()
    val dichte = LocalDensity.current
    val beschreibung = kacheln.joinToString(", ") { it.name }
    // Der Zoom haengt an den gezeigten Firmen, nicht an ihren Kursen
    val schluessel = remember(kacheln) { kacheln.map { it.name } }
    val kz = remember(schluessel) { KartenZoom() }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(hoehe).clip(RoundedCornerShape(10.dp)).semantics { contentDescription = beschreibung }) {
        val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
        val rechtecke = remember(schluessel, kacheln.map { it.wert }, w, h) { Treemap.anordnen(kacheln.map { it.wert }, w, h) }
        val titelStil = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        val namensBlock = titelStil.copy(textAlign = TextAlign.Center, lineHeight = 14.sp, hyphens = Hyphens.None)
        val prozentStil = TextStyle(fontSize = 11.sp)
        val stufe = HeatmapZoomLage.stufe(kz.zoom, FIRMEN_STUFEN)
        val innen = with(dichte) { 8.dp.toPx() }
        // Beschriftung je Zoomstufe: Beim Vergroessern bekommen kleine Kacheln ihre Zeilen, sobald sie ganz hineinpassen
        val texte = remember(kacheln, rechtecke, stufe, dichte) {
            fun zeile(stil: TextStyle, t: String) = messer.measure(t, stil, maxLines = 1, softWrap = false)
            fun masz(l: TextLayoutResult) = HeatmapBeschriftung.Text(l.size.width.toFloat(), l.size.height.toFloat())
            val bezugKuerzel = zeile(titelStil, HeatmapBeschriftung.BEZUG_KUERZEL).size.width.toFloat()
            val bezugProzent = zeile(prozentStil, Fmt.signedPct(1.0, 1)).size.width.toFloat()
            kacheln.mapIndexed { i, k ->
                val r = rechtecke[i]
                val bw = r.w * stufe; val bh = r.h * stufe
                val platz = (bw - innen).toInt()
                if (platz <= 0 || bh <= innen) return@mapIndexed FirmenText(null, null, null)
                // Voller Name auf hoechstens drei Zeilen, kein Wort getrennt, nichts abgeschnitten
                val name = messer.measure(k.name, namensBlock, maxLines = 3, constraints = Constraints(maxWidth = platz))
                    .takeIf { l -> !l.hasVisualOverflow && k.name.split(' ').filter { it.isNotBlank() }.all { zeile(titelStil, it).size.width <= platz } }
                val kuerzel = k.kuerzel?.takeIf { it.isNotBlank() }?.let { zeile(titelStil, it) }
                val prozent = k.aenderung?.let { zeile(prozentStil, Fmt.signedPct(it, 1)) }
                val kgv = HeatmapBeschriftung.kgvWert(k.kgv)?.let { zeile(prozentStil, S.heatmapKgv(Fmt.num(it, 1))) }
                val z = HeatmapBeschriftung.firmenZeilen(bw, bh, innen, name?.let { masz(it) }, kuerzel?.let { masz(it) }, prozent?.let { masz(it) }, kgv?.let { masz(it) }, bezugKuerzel, bezugProzent)
                val titel = when (z.titel) {
                    HeatmapBeschriftung.Titel.NAME -> name
                    HeatmapBeschriftung.Titel.KUERZEL -> kuerzel
                    HeatmapBeschriftung.Titel.NICHTS -> null
                }
                FirmenText(titel, prozent.takeIf { z.prozent }, kgv.takeIf { z.kgv })
            }
        }
        fun lage(r: Treemap.Rechteck) = Treemap.Rechteck(r.x * kz.zoom + kz.versatz.x, r.y * kz.zoom + kz.versatz.y, r.w * kz.zoom, r.h * kz.zoom)
        val vergroessert = kz.vergroessert
        val aktuell by rememberUpdatedState(kacheln)
        Canvas(
            Modifier.fillMaxSize().kartenZoom(kz, FIRMEN_MAX_ZOOM)
                // Der Doppeltipp zaehlt nur vergroessert; sonst oeffnet der Tipp ohne Wartezeit
                .pointerInput(rechtecke, vergroessert) {
                    detectTapGestures(onDoubleTap = if (vergroessert) ({ kz.zurueck() }) else null) { p ->
                        val i = rechtecke.indexOfFirst { r -> r.w > 0f && (p.x to p.y) in lage(r) }
                        if (i >= 0) aktuell.getOrNull(i)?.onClick?.invoke()
                    }
                },
        ) {
            val luecke = 1f * density
            rechtecke.forEachIndexed { i, basis ->
                val r = lage(basis)
                if (r.w <= 2 * luecke || r.h <= 2 * luecke) return@forEachIndexed
                if (r.x > size.width || r.y > size.height || r.x + r.w < 0 || r.y + r.h < 0) return@forEachIndexed
                val farbe = Color(HeatmapBeschriftung.farbe(kacheln[i].aenderung, FIRMEN_VOLL, heat.hoch.toArgb(), heat.runter.toArgb(), heat.neutral.toArgb()))
                drawRoundRect(farbe, Offset(r.x + luecke, r.y + luecke), Size(r.w - 2 * luecke, r.h - 2 * luecke), CornerRadius(3f * density))
                val t = texte.getOrNull(i) ?: return@forEachIndexed
                val titel = t.titel ?: return@forEachIndexed
                val schrift = aufFarbe(farbe)
                val gesamt = titel.size.height + (t.prozent?.size?.height ?: 0) + (t.kgv?.size?.height ?: 0)
                var y = r.y + (r.h - gesamt) / 2
                drawText(titel, color = schrift, topLeft = Offset(r.x + (r.w - titel.size.width) / 2, y))
                y += titel.size.height
                t.prozent?.let { drawText(it, color = schrift.copy(alpha = 0.9f), topLeft = Offset(r.x + (r.w - it.size.width) / 2, y)); y += it.size.height }
                t.kgv?.let { drawText(it, color = schrift.copy(alpha = 0.75f), topLeft = Offset(r.x + (r.w - it.size.width) / 2, y)) }
            }
        }
        if (vergroessert) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(Radius.chip))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f))
                    .clickable(role = Role.Button) { kz.zurueck() }.padding(horizontal = 12.dp, vertical = 6.dp),
            ) { Text(S.heatmapZoomZurueck, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false) }
        }
    }
}
