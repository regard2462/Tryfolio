package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.engine.LogoDarstellung
import de.troetenrepublik.app.i18n.S
import de.troetenrepublik.app.market.Symbols

// Beispielwerte fuer die Vorschau von Logofarbe, Form und Hintergrund: hoechstens drei verschiedene Werte aus dem eigenen
// Depot, dann aus den Watchlists. Aktien und ETFs kommen vor Kryptos, Indizes und Rohstoffen, weil nur sie ein Logo haben.
internal fun darstellungBeispiele(a: Account): List<Pair<String, String>> {
    data class W(val symbol: String, val name: String, val art: AssetType?)
    val depot = a.positions.filter { !it.isDerivative }.sortedByDescending { it.invested }
        .map { W(it.symbol, kurzName(it.symbol, it.name), it.artOderNull ?: Symbols.find(it.symbol)?.type) }
    val listen = a.watchlist.map { s -> W(s, Symbols.find(s)?.name ?: s, Symbols.find(s)?.type) }
    val alle = (depot + listen).distinctBy { it.symbol }.distinctBy { it.name }
    val (mitLogo, ohneLogo) = alle.partition { it.art == null || it.art == AssetType.STOCK || it.art == AssetType.ETF }
    return (mitLogo + ohneLogo).take(3).map { it.symbol to it.name }
}

// Ein Beispiel der Vorschau: ein echter Wert des Nutzers (symbol gesetzt) oder ein neutrales Zeichen (art 0 bis 2).
internal class DarstellungBeispiel(val symbol: String?, val name: String, val art: Int)

// Echte Werte, aufgefuellt auf drei mit neutralen Zeichen A, B, C.
@Composable
internal fun darstellungListe(echt: List<Pair<String, String>>): List<DarstellungBeispiel> {
    val w = echt.take(3).map { DarstellungBeispiel(it.first, it.second, 0) }
    return w + (0 until 3 - w.size).map { k -> DarstellungBeispiel(null, S.beispielName(k), k) }
}

@Composable
internal fun DarstellungLogo(b: DarstellungBeispiel, size: Int, farbig: Boolean, rund: Boolean, ohneHintergrund: Boolean) {
    if (b.symbol != null) InstrumentLogo(b.symbol, b.name, size = size, farbig = farbig, rund = rund, ohneHintergrund = ohneHintergrund)
    else NeutralesZeichen(b.art, size, farbig, rund, ohneHintergrund)
}

private val ZEICHEN_BLAU = Color(0xFF3B6FD4)
private val STREIFEN = listOf(Color(0xFFE8743B), Color(0xFF1FA39A), Color(0xFF7B5CD6))
private val DREIECK_GRUEN = Color(0xFF2E9E5B)
private val KREIS_GELB = Color(0xFFF0A92E)

// Selbst gezeichnete Zeichen ohne Bezug zu einer Marke. 0: weisser Buchstabe auf farbiger Flaeche, 1: drei bunte Streifen auf
// heller Flaeche, 2: Dreieck und Kreis auf dezentem Grund. Sie folgen Farbe, Form und Hintergrund wie die Logos:
// Schwarz-weiss ist Tinte (schwarz im hellen, weiss im dunklen Design), Entfernen laesst nur das Zeichen stehen.
@Composable
private fun NeutralesZeichen(art: Int, size: Int, farbig: Boolean, rund: Boolean, ohneHintergrund: Boolean) {
    val dunkel = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val tinte = if (dunkel) Color.White else Color.Black
    val grund = MaterialTheme.colorScheme.surfaceContainerHigh
    val flaeche: Color? = when {
        ohneHintergrund -> null
        art == 0 -> if (farbig) ZEICHEN_BLAU else Color.Black
        art == 1 -> Color.White
        else -> if (farbig) grund else Color(grund.luminance(), grund.luminance(), grund.luminance())
    }
    val form = RoundedCornerShape((size * LogoDarstellung.eckenAnteil(rund)).dp)
    val anteil = LogoDarstellung.zeichenAnteil(rund, flaeche != null)
    // Auf der weissen Flaeche bleibt die Tinte schwarz, sonst folgt sie dem Design
    val tintenFarbe = if (flaeche == Color.White) Color.Black else tinte
    Box(
        Modifier.size(size.dp).clip(form).then(if (flaeche != null) Modifier.background(flaeche) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (art == 0) {
            val buchstabe = if (flaeche != null) Color.White else if (farbig) ZEICHEN_BLAU else tinte
            Text("A", color = buchstabe, fontSize = (size * 0.5f).sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        } else {
            Canvas(Modifier.size((size * anteil).dp)) {
                val s = this.size.minDimension
                if (art == 1) {
                    val breite = s * 0.24f
                    val luecke = s * 0.14f
                    listOf(0.5f, 1f, 0.75f).forEachIndexed { k, h ->
                        drawRoundRect(
                            if (farbig) STREIFEN[k] else tintenFarbe,
                            Offset(k * (breite + luecke), s * (1f - h)), Size(breite, s * h), CornerRadius(breite * 0.3f),
                        )
                    }
                } else {
                    val dreieck = Path().apply {
                        moveTo(s * 0.30f, s * 0.10f); lineTo(0f, s * 0.90f); lineTo(s * 0.60f, s * 0.90f); close()
                    }
                    drawPath(dreieck, if (farbig) DREIECK_GRUEN else tintenFarbe)
                    drawCircle(if (farbig) KREIS_GELB else tintenFarbe, s * 0.22f, Offset(s * 0.78f, s * 0.68f))
                }
            }
        }
    }
}
