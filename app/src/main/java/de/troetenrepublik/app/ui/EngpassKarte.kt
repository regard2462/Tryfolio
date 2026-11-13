package de.troetenrepublik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.troetenrepublik.app.AppState
import de.troetenrepublik.app.engine.EngpassAusschnitt
import de.troetenrepublik.app.engine.Engpaesse
import de.troetenrepublik.app.engine.GlobusAnsicht
import de.troetenrepublik.app.engine.GlobusFarbe
import de.troetenrepublik.app.engine.GlobusKarte
import de.troetenrepublik.app.engine.GlobusPfad
import de.troetenrepublik.app.engine.GlobusZeichner
import de.troetenrepublik.app.i18n.I18n
import de.troetenrepublik.app.market.ohneAbbruch

// [Engpaesse 0.76] Ausschnitt der Weltkarte um einen Engpass im Stil des Globus: dunkles Meer, dunkle Landflaechen,
// Laendergrenzen in derselben Staerke, der Engpass als Punkt in seiner Farbe, Namen der Laender im Bild.

private class KartenPfad(val pfad: Path) : GlobusPfad {
    override fun nach(x: Float, y: Float) = pfad.moveTo(x, y)
    override fun linie(x: Float, y: Float) = pfad.lineTo(x, y)
    override fun schliessen() = pfad.close()
}

// engpass: Lage des Engpasses; abw: Veraenderung der Durchfahrten gegen das Vorjahr in Prozent (faerbt den Punkt, null grau).
// karte: Umrisse des Globus (AppState.globusKarte()); solange sie fehlt, steht das leere Meer.
@Composable
fun EngpassKarte(engpass: Engpaesse.Stelle, karte: GlobusKarte?, modifier: Modifier = Modifier, abw: Double? = null) {
    val signal = LocalSignal.current
    val deutsch = I18n.locale.language == "de"
    val messer = rememberTextMeasurer(cacheSize = 64)
    val gp = signal.globus
    val punkt = Color(GlobusFarbe.engpass(abw, signal.up.toArgb(), signal.down.toArgb(), gp))
    Box(
        modifier.fillMaxWidth().aspectRatio(EngpassAusschnitt.SEITENVERHAELTNIS).clip(RoundedCornerShape(Radius.card))
            .background(Color(gp.kugel))
            .drawWithCache {
                val w = size.width; val h = size.height
                val dichte = density
                val flaechen = ArrayList<Path>()
                var grenzen: Path? = null
                val namen = ArrayList<Triple<TextLayoutResult, TextLayoutResult, FloatArray>>()
                val marke = Offset(w / 2f, h / 2f)
                val randBreite = 11.sp.toPx() * 0.22f
                if (karte != null && w > 0f && h > 0f) {
                    val a = GlobusAnsicht()
                    val rahmen = EngpassAusschnitt.ansicht(engpass, w, h, a)
                    for (l in karte.laender) {
                        if (!EngpassAusschnitt.beruehrt(l, rahmen)) continue
                        val p = Path().also { it.fillType = PathFillType.EvenOdd }
                        if (GlobusZeichner.flaeche(l, a, KartenPfad(p), 0.5f)) flaechen += p
                    }
                    grenzen = Path().also { GlobusZeichner.linien(karte.linien, a, KartenPfad(it), 0.5f) }

                    // Namen: Anrainer fett und weiss, uebrige Laender zurueckhaltend; Umriss und Fuellung getrennt
                    val groesse = 11.sp
                    val kandidaten = EngpassAusschnitt.namen(karte, engpass, a, w, h, rahmen)
                    val layouts = kandidaten.map { n ->
                        val text = n.land.name(deutsch)
                        val farbe = if (n.vorrang) Color(gp.schrift) else Color(gp.punktOhne)
                        val fuell = TextStyle(fontSize = groesse, color = farbe, fontWeight = FontWeight.Bold)
                        val rand = TextStyle(fontSize = groesse, color = Color(gp.schriftRand), fontWeight = FontWeight.Bold, drawStyle = Stroke(width = randBreite, join = StrokeJoin.Round))
                        messer.measure(text, fuell, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip) to
                            messer.measure(text, rand, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
                    }
                    val marker = 11f * dichte
                    val plaetze = kandidaten.mapIndexed { i, n ->
                        EngpassAusschnitt.Platz(n.x, n.y, layouts[i].first.size.width.toFloat(), layouts[i].first.size.height.toFloat(), n.vorrang)
                    }
                    val sperre = listOf(floatArrayOf(marke.x - marker, marke.y - marker, marke.x + marker, marke.y + marker))
                    val gesetzt = EngpassAusschnitt.platzieren(plaetze, sperre, w, h, 3f * dichte)
                    for (i in kandidaten.indices) gesetzt[i]?.let { namen += Triple(layouts[i].first, layouts[i].second, it) }
                }
                onDrawBehind {
                    for (p in flaechen) drawPath(p, Color(gp.gedimmt))
                    grenzen?.let {
                        drawPath(
                            it, Color(gp.grenze).copy(alpha = 0.75f),
                            style = Stroke(width = 0.6f * dichte, cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )
                    }
                    // Punkt des Engpasses wie im Globus: Ring, gefuellter Punkt, weisser Rand
                    drawCircle(punkt.copy(alpha = 0.3f), 11f * dichte, marke)
                    drawCircle(punkt, 6f * dichte, marke)
                    drawCircle(Color.White.copy(alpha = 0.85f), 6f * dichte, marke, style = Stroke(width = 1.2f * dichte))
                    // Zeichenart bei beiden Aufrufen ausdruecklich setzen, sonst teilen sich die Texte auf Android einen Pinsel
                    for ((fuell, rand, pos) in namen) {
                        val o = Offset(pos[0], pos[1])
                        drawText(rand, topLeft = o, drawStyle = Stroke(width = randBreite, join = StrokeJoin.Round))
                        drawText(fuell, topLeft = o, drawStyle = Fill)
                    }
                }
            },
    )
}

// Blatt zu einem Engpass fuer andere Seiten (Ausblick, Lage): laedt Umrisse und Lage selbst und oeffnet das Blatt.
// onOpen wie beim Blatt: oeffnet ein Papier oder eine Seite der App (die Seite schliesst das Blatt vorher selbst).
@Composable
fun EngpassBlattOeffnen(state: AppState, id: String, onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    var karte by remember { mutableStateOf<GlobusKarte?>(null) }
    var uebersicht by remember { mutableStateOf<Engpaesse.Uebersicht?>(null) }
    LaunchedEffect(id) {
        karte = ohneAbbruch { state.globusKarte() }.getOrNull()
        uebersicht = ohneAbbruch { state.engpassGespeichert() }.getOrNull()
        ohneAbbruch { state.engpassUebersicht() }.getOrNull()?.let { uebersicht = it }
    }
    // Nach dem geoeffneten Papier steht das Blatt wieder da
    WiederkehrBlatt("engpass-$id") { w -> EngpassBlatt(state, id, uebersicht, karte, { s -> w.verlassen(); onOpen(s) }, onDismiss) }
}
