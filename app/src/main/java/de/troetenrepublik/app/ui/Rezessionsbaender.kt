package de.troetenrepublik.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import de.troetenrepublik.app.engine.RezessionNyFed
import de.troetenrepublik.app.engine.Rezessionen
import de.troetenrepublik.app.i18n.S

// Rezessionsbaender (gemeinsamer Baustein fuer alle Langzeitcharts): gedaempfte senkrechte Flaechen fuer die Monate einer
// US-Rezession (NBER-Datierung aus der Datei der New York Fed, engine/Rezessionen). Nur in Charts, die mindestens zehn
// Jahre zeigen. Beschriftet wird direkt am Chart: das breiteste Band mit „Rezession“; beginnt der Chart vor den Daten
// (1959), steht am ersten Band, ab wann sie reichen.

// Nennung nach den Bedingungen der New York Fed (Jahr des Datenstands)
internal fun nyFedQuelle(): QuelleLink = QuelleLink(
    "© " + (RezessionNyFed.datei?.let { RezessionNyFed.jahr(it.stand) } ?: java.time.LocalDate.now().year) + " Federal Reserve Bank of New York",
    RezessionNyFed.SEITE,
)

// Meldet die Quelle an den Sammler der Seite, wenn ein Chart von von bis bis (ms) Baender zeigt
@Composable
internal fun RezessionsQuelleMelden(von: Long, bis: Long) {
    if (Rezessionen.lang(von, bis) && Rezessionen.imBereich(von, bis).isNotEmpty()) QuellenMelden(nyFedQuelle())
}

internal class RezessionsStil(val flaeche: Color, val schrift: Color, val text: TextStyle)

@Composable
internal fun rezessionsStil(): RezessionsStil = RezessionsStil(
    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f),
    MaterialTheme.colorScheme.onSurfaceVariant,
    MaterialTheme.typography.labelSmall,
)

// Baender zwischen von und bis (ms); x bildet eine Zeit in ms auf die Breite ab, oben und unten begrenzen die Flaeche.
// beschriftungY: obere Kante der Beschriftung, null ohne Beschriftung.
internal fun DrawScope.rezessionsBaender(
    von: Long, bis: Long, x: (Long) -> Float, oben: Float, unten: Float, stil: RezessionsStil, messer: TextMeasurer?,
    beschriftungY: Float?, spannen: List<Rezessionen.Spanne> = Rezessionen.alle, abMs: Long? = Rezessionen.abMs,
) {
    if (!Rezessionen.lang(von, bis) || unten <= oben) return
    val liste = Rezessionen.imBereich(von, bis, spannen)
    if (liste.isEmpty()) return
    val min = 1.5f * density
    val lagen = liste.map { s ->
        val a = x(s.von).coerceIn(0f, size.width)
        val b = x(s.bis).coerceIn(0f, size.width)
        val breite = maxOf(b - a, min)
        drawRect(stil.flaeche, Offset(a, oben), Size(breite, unten - oben))
        a to breite
    }
    if (messer == null || beschriftungY == null) return
    val vorDaten = abMs != null && von < abMs
    val text = if (vorDaten) S.rzBandAb(java.time.Instant.ofEpochMilli(liste.first().von).atZone(java.time.ZoneOffset.UTC).year) else S.rzBand
    val t = messer.measure(text, stil.text.copy(color = stil.schrift))
    val (a, breite) = if (vorDaten) lagen.first() else lagen.maxBy { it.second }
    val links = if (vorDaten) a else a + breite / 2 - t.size.width / 2f
    drawText(t, topLeft = Offset(links.coerceIn(0f, (size.width - t.size.width).coerceAtLeast(0f)), beschriftungY))
}
