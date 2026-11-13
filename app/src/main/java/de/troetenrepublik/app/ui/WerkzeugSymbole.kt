package de.troetenrepublik.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Symbole der Werkzeugliste: je Werkzeug eine kleine Grafik aus dem Werkzeug selbst, als Grundformen in einem Raster von
// 30 x 30 abgelegt. Hauptformen in der Schriftfarbe, Achsen und Nebenteile gedaempft; ohne Feld und ohne Signalfarben.

internal const val SYMBOL_RASTER = 30f
// Strichstaerke und Strichmuster in Rastereinheiten
internal const val SYMBOL_STRICH = 2f
private const val STRICH_AN = 2.5f
private const val STRICH_AUS = 3f

internal enum class SymbolTon { HAUPT, NEBEN }

internal sealed class SymbolForm {
    abstract val ton: SymbolTon

    // Linienzug durch die Punkte (x, y abwechselnd), runde Enden
    data class Linie(val punkte: List<Float>, override val ton: SymbolTon, val gestrichelt: Boolean = false) : SymbolForm()
    // Kubische Kurve vom ersten zum letzten Punkt, dazwischen die beiden Stuetzpunkte
    data class Kurve(val punkte: List<Float>, override val ton: SymbolTon) : SymbolForm()
    data class Rechteck(val x: Float, val y: Float, val breite: Float, val hoehe: Float, val radius: Float, override val ton: SymbolTon) : SymbolForm()
    data class Kreis(val x: Float, val y: Float, val radius: Float, override val ton: SymbolTon) : SymbolForm()
    // Gefuelltes Vieleck (Dreieck, Flaeche unter einer Linie)
    data class Flaeche(val punkte: List<Float>, override val ton: SymbolTon, val deckkraft: Float = 1f) : SymbolForm()

    // Aeusserste Ausdehnung als links, oben, rechts, unten; Striche samt halber Strichstaerke
    fun rahmen(): List<Float> = when (this) {
        is Linie -> punktRahmen(punkte, SYMBOL_STRICH / 2f)
        is Kurve -> punktRahmen(punkte, SYMBOL_STRICH / 2f)
        is Flaeche -> punktRahmen(punkte, 0f)
        is Rechteck -> listOf(x, y, x + breite, y + hoehe)
        is Kreis -> listOf(x - radius, y - radius, x + radius, y + radius)
    }
}

private fun punktRahmen(p: List<Float>, rand: Float): List<Float> {
    val xs = p.filterIndexed { i, _ -> i % 2 == 0 }
    val ys = p.filterIndexed { i, _ -> i % 2 == 1 }
    return listOf(xs.min() - rand, ys.min() - rand, xs.max() + rand, ys.max() + rand)
}

private val H = SymbolTon.HAUPT
private val N = SymbolTon.NEBEN

private fun linie(vararg p: Float) = SymbolForm.Linie(p.toList(), H)
private fun neben(vararg p: Float) = SymbolForm.Linie(p.toList(), N)
private fun strichel(ton: SymbolTon, vararg p: Float) = SymbolForm.Linie(p.toList(), ton, gestrichelt = true)
private fun kurve(vararg p: Float) = SymbolForm.Kurve(p.toList(), H)
private fun kreis(x: Float, y: Float, r: Float, ton: SymbolTon = H) = SymbolForm.Kreis(x, y, r, ton)
private fun rechteck(x: Float, y: Float, b: Float, h: Float, r: Float, ton: SymbolTon = H) = SymbolForm.Rechteck(x, y, b, h, r, ton)
private fun flaeche(vararg p: Float) = SymbolForm.Flaeche(p.toList(), H)

// Grundlinie der Diagramm-Symbole
private val ACHSE = neben(2f, 26f, 28f, 26f)

internal val WERKZEUG_SYMBOLE: Map<String, List<SymbolForm>> = mapOf(
    // Kurs mit Endpunkt ueber der Achse
    WZ_VERLAUF to listOf(
        ACHSE,
        linie(2f, 22f, 8f, 17f, 13f, 19f, 19f, 10f, 24f, 12f, 28f, 5f),
        kreis(28f, 5f, 2.4f),
    ),
    // Drei Regeln mit Ampelpunkt
    "CHECK" to listOf(
        kreis(6f, 8f, 3f), linie(13f, 8f, 28f, 8f),
        kreis(6f, 15f, 3f), linie(13f, 15f, 24f, 15f),
        kreis(6f, 22f, 3f), linie(13f, 22f, 27f, 22f),
    ),
    // Kacheln nach Gewicht
    "DIVERSIFICATION" to listOf(
        rechteck(2f, 3f, 14f, 24f, 2f),
        rechteck(18f, 3f, 10f, 12f, 2f, N),
        rechteck(18f, 17f, 5f, 10f, 1.5f),
        rechteck(25f, 17f, 3f, 10f, 1.2f, N),
    ),
    // Zwei Saeulen an der Ziellinie, Pfeil nach unten und nach oben
    "REBALANCE" to listOf(
        rechteck(4f, 6f, 7f, 20f, 1.5f, N),
        rechteck(19f, 16f, 7f, 10f, 1.5f, N),
        strichel(H, 1f, 13f, 29f, 13f),
        linie(7.5f, 2f, 7.5f, 9f), linie(5f, 6.5f, 7.5f, 9f, 10f, 6.5f),
        linie(22.5f, 21f, 22.5f, 14f), linie(20f, 16.5f, 22.5f, 14f, 25f, 16.5f),
    ),
    // Einbruch mit Tiefpunkt und Erholung
    "CRASH" to listOf(
        ACHSE,
        linie(2f, 6f, 9f, 8f),
        linie(9f, 8f, 15f, 23f),
        linie(15f, 23f, 20f, 17f, 24f, 18f, 28f, 11f),
        kreis(15f, 23f, 2.4f),
    ),
    // Verlauf bis heute, danach zwei moegliche Wege
    "SCENARIO" to listOf(
        linie(2f, 17f, 12f, 17f),
        strichel(H, 12f, 17f, 28f, 6f),
        strichel(H, 12f, 17f, 28f, 26f),
        kreis(12f, 17f, 2.6f),
    ),
    // Monatssaeulen mit zwei grossen Ausschuettungen
    "DIVIDENDS" to listOf(
        ACHSE,
        rechteck(3f, 19f, 3f, 5f, 1f, N),
        rechteck(8f, 10f, 3f, 14f, 1f),
        rechteck(13f, 20f, 3f, 4f, 1f, N),
        rechteck(18f, 13f, 3f, 11f, 1f),
        rechteck(23f, 18f, 3f, 6f, 1f, N),
    ),
    // Depot gegen Vergleichslinie
    "PERFORMANCE" to listOf(
        ACHSE,
        strichel(N, 2f, 21f, 10f, 18f, 18f, 15f, 28f, 12f),
        linie(2f, 22f, 9f, 15f, 15f, 17f, 21f, 8f, 28f, 4f),
    ),
    // Einzahlungen als Flaeche, Vermoegen als Kurve darueber
    "WEALTH" to listOf(
        SymbolForm.Flaeche(listOf(2f, 26f, 2f, 22f, 28f, 16f, 28f, 26f), N, deckkraft = 0.55f),
        linie(2f, 22f, 28f, 16f),
        kurve(2f, 22f, 12f, 21f, 22f, 16f, 28f, 3f),
    ),
    // Kurve mit den Kaufpunkten der Rate
    "SPARPLAN" to listOf(
        ACHSE,
        kurve(3f, 21f, 11f, 20f, 20f, 15f, 27f, 6f),
        kreis(3f, 21f, 2.2f), kreis(11f, 19f, 2.2f), kreis(19f, 14f, 2.2f), kreis(27f, 6f, 2.2f),
    ),
    // Anstieg bis zur Ziellinie, danach der Faecher der Entnahme: mittlerer Verlauf und die beiden Raender
    "WITHDRAWAL" to listOf(
        ACHSE,
        neben(2f, 8f, 13f, 8f),
        neben(13f, 8f, 27f, 4f), neben(13f, 8f, 27f, 23f),
        linie(2f, 24f, 7f, 19f, 13f, 8f, 20f, 11f, 27f, 14f),
    ),
    // Saeulen an der Linie des Markts, eine darueber
    "MARKT" to listOf(
        ACHSE,
        linie(2f, 9f, 28f, 9f),
        rechteck(3f, 15f, 3.5f, 9f, 1f, N),
        rechteck(8.5f, 12f, 3.5f, 12f, 1f, N),
        rechteck(14f, 17f, 3.5f, 7f, 1f, N),
        rechteck(19.5f, 3f, 3.5f, 21f, 1f),
        rechteck(25f, 14f, 3.5f, 10f, 1f, N),
    ),
    // Rendite je Hebel: Hoecker der Kurve mit Gipfelpunkt, dazu die senkrechte Linie des gewaehlten Hebels
    "HEBEL" to listOf(
        ACHSE,
        neben(20f, 4f, 20f, 26f),
        kurve(3f, 24f, 9f, 3f, 17f, 2f, 27f, 19f),
        kreis(13.5f, 7.3f, 2.4f),
    ),
    // Effizienzgrenze ueber Schwankung und Rendite, das Depot als Punkt darunter
    "GRENZE" to listOf(
        neben(3f, 2f, 3f, 27f, 28f, 27f),
        kurve(8f, 18f, 8f, 9f, 16f, 5f, 28f, 4f),
        kreis(19f, 16f, 2.6f),
        kreis(13f, 22f, 1.8f, N), kreis(24f, 21f, 1.8f, N),
    ),
    // Drei Mischungen vom selben Startpunkt, die beste mit Endpunkt
    "BACKTEST" to listOf(
        ACHSE,
        neben(3f, 21f, 10f, 20f, 17f, 21f, 27f, 17f),
        neben(3f, 21f, 10f, 18f, 17f, 15f, 27f, 12f),
        linie(3f, 21f, 10f, 16f, 16f, 13f, 21f, 9f, 27f, 4f),
        kreis(3f, 21f, 2.2f, N),
        kreis(27f, 4f, 2.4f),
    ),
    // Langer Verlauf mit tiefem Einbruch ueber einer Zeitachse mit Marken
    "LANGSICHT" to listOf(
        ACHSE,
        neben(2f, 26f, 2f, 29f), neben(15f, 26f, 15f, 29f), neben(28f, 26f, 28f, 29f),
        linie(2f, 22f, 7f, 18f, 10f, 13f, 14f, 21f, 19f, 14f, 23f, 9f, 28f, 3f),
    ),
    // Drei Kurse, der staerkste hervorgehoben, darunter die Monatsmarken
    "ROTATION" to listOf(
        ACHSE,
        neben(8f, 24f, 8f, 28f), neben(15f, 24f, 15f, 28f), neben(22f, 24f, 22f, 28f),
        neben(2f, 14f, 9f, 17f, 16f, 13f, 22f, 18f, 28f, 20f),
        neben(2f, 21f, 9f, 20f, 16f, 22f, 22f, 15f, 28f, 13f),
        linie(2f, 19f, 8f, 12f, 15f, 9f, 22f, 6f, 28f, 3f),
        kreis(28f, 3f, 2.4f),
    ),
    // Balken der Faktorladungen an der Nulllinie, je mit Spanne
    "FAKTOREN" to listOf(
        neben(12f, 3f, 12f, 27f),
        rechteck(12f, 5f, 12f, 4f, 1f),
        rechteck(5f, 13f, 7f, 4f, 1f),
        rechteck(12f, 21f, 7f, 4f, 1f),
        neben(21f, 7f, 28f, 7f), neben(2f, 15f, 8f, 15f), neben(16f, 23f, 23f, 23f),
    ),
    // Punktraster mit drei Treffern
    "FINDER" to listOf(
        kreis(4f, 5f, 2f, N), kreis(11f, 5f, 2f, N), kreis(18f, 5f, 2f, N), kreis(25f, 5f, 2f, N),
        kreis(4f, 12f, 2f, N), kreis(18f, 12f, 2f, N), kreis(25f, 12f, 2f, N),
        kreis(4f, 19f, 2f, N), kreis(11f, 19f, 2f, N), kreis(25f, 19f, 2f, N),
        kreis(11f, 26f, 2f, N), kreis(18f, 26f, 2f, N), kreis(25f, 26f, 2f, N),
        kreis(11f, 12f, 2.8f), kreis(18f, 19f, 2.8f), kreis(4f, 26f, 2.8f),
    ),
    // Hochstufung und Abstufung mit je zwei Textzeilen
    "STUFEN" to listOf(
        flaeche(8f, 4f, 14f, 13f, 2f, 13f),
        linie(18f, 6f, 28f, 6f), linie(18f, 11f, 25f, 11f),
        flaeche(8f, 26f, 14f, 17f, 2f, 17f),
        linie(18f, 19f, 28f, 19f), linie(18f, 24f, 24f, 24f),
    ),
    // Rangbalken, der kuerzeste gedaempft
    "HAEUSER" to listOf(
        rechteck(2f, 4f, 26f, 5f, 2f),
        rechteck(2f, 12.5f, 19f, 5f, 2f),
        rechteck(2f, 21f, 11f, 5f, 2f, N),
    ),
    // Auszahlung einer verkauften Option: Verlust bis zum Knick, darueber gedeckelt
    "STILLHALTER" to listOf(
        strichel(N, 2f, 17f, 28f, 17f),
        linie(3f, 27f, 14f, 11f),
        linie(14f, 11f, 28f, 11f),
    ),
    // Volumensaeulen mit einem Ausreisser
    "AUFFAELLIG" to listOf(
        ACHSE,
        rechteck(3f, 19f, 3.5f, 5f, 1f, N),
        rechteck(8.5f, 17f, 3.5f, 7f, 1f, N),
        rechteck(14f, 4f, 3.5f, 20f, 1f),
        rechteck(19.5f, 18f, 3.5f, 6f, 1f, N),
        rechteck(25f, 20f, 3.5f, 4f, 1f, N),
    ),
    // Auszahlung einer gekauften Option mit Knick am Basispreis
    "OPTIONS" to listOf(
        strichel(N, 2f, 17f, 28f, 17f),
        linie(2f, 22f, 13f, 22f),
        linie(13f, 22f, 27f, 4f),
        kreis(13f, 22f, 2.4f),
    ),
)

internal fun werkzeugFormen(schluessel: String): List<SymbolForm> = WERKZEUG_SYMBOLE[schluessel].orEmpty()

// Zeichnet das Symbol eines Werkzeugs; das Raster wird auf die Groesse gestreckt.
@Composable
internal fun WerkzeugSymbol(schluessel: String, modifier: Modifier = Modifier, groesse: Dp = 30.dp) {
    val formen = werkzeugFormen(schluessel)
    val haupt = MaterialTheme.colorScheme.onSurface
    val nebenFarbe = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
    androidx.compose.foundation.layout.Spacer(
        modifier.size(groesse).drawWithCache {
            val m = size.minDimension / SYMBOL_RASTER
            val strich = Stroke(SYMBOL_STRICH * m, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val gestrichelt = Stroke(
                SYMBOL_STRICH * m, cap = StrokeCap.Round, join = StrokeJoin.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(STRICH_AN * m, STRICH_AUS * m)),
            )
            fun zug(p: List<Float>, zu: Boolean) = Path().apply {
                moveTo(p[0] * m, p[1] * m)
                var i = 2
                while (i + 1 < p.size) { lineTo(p[i] * m, p[i + 1] * m); i += 2 }
                if (zu) close()
            }
            // Pfade einmal je Groesse bauen, nicht je Bild
            val pfade = formen.map { f ->
                when (f) {
                    is SymbolForm.Linie -> zug(f.punkte, false)
                    is SymbolForm.Flaeche -> zug(f.punkte, true)
                    is SymbolForm.Kurve -> Path().apply {
                        val p = f.punkte
                        moveTo(p[0] * m, p[1] * m)
                        cubicTo(p[2] * m, p[3] * m, p[4] * m, p[5] * m, p[6] * m, p[7] * m)
                    }
                    else -> null
                }
            }
            onDrawBehind {
                formen.forEachIndexed { i, f ->
                    val farbe = if (f.ton == SymbolTon.HAUPT) haupt else nebenFarbe
                    when (f) {
                        is SymbolForm.Linie -> drawPath(pfade[i]!!, farbe, style = if (f.gestrichelt) gestrichelt else strich)
                        is SymbolForm.Kurve -> drawPath(pfade[i]!!, farbe, style = strich)
                        is SymbolForm.Flaeche -> drawPath(pfade[i]!!, farbe, alpha = f.deckkraft)
                        is SymbolForm.Rechteck -> drawRoundRect(
                            farbe, Offset(f.x * m, f.y * m), Size(f.breite * m, f.hoehe * m), CornerRadius(f.radius * m, f.radius * m),
                        )
                        is SymbolForm.Kreis -> drawCircle(farbe, f.radius * m, Offset(f.x * m, f.y * m))
                    }
                }
            }
        },
    )
}
