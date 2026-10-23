package de.troetenrepublik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Richtungsdreieck mit weichen Ecken fuer steigende und fallende Werte.
// Der Umriss ist ein Pfad, dessen drei Ecken mit kubischen Bezierstuecken verrundet sind, die Spitze staerker als
// die Basisecken. Die Koordinaten stehen in Einheiten eines Zeichenfelds 6 x 6; sichtbar ist x 0,3 bis 5,7 und
// y 1,8 bis 6,0, also ein Verhaeltnis von 9 : 7.
@Composable
fun DirectionTriangle(
    change: Double,
    modifier: Modifier = Modifier,
    width: Dp = 9.dp,
    color: Color? = null,
) {
    if (change == 0.0 || !change.isFinite()) return
    val signal = LocalSignal.current
    val tint = color ?: signal.forChange(change)
    val up = change > 0
    Canvas(modifier.size(width = width, height = width * TRIANGLE_ASPECT)) {
        drawPath(trianglePath(size.width, size.height, up), tint)
    }
}

// Hoehe im Verhaeltnis zur Breite: 4,2 / 5,4.
private const val TRIANGLE_ASPECT = 0.7778f

private fun trianglePath(w: Float, h: Float, up: Boolean): Path {
    val sx = w / 5.4f
    val sy = h / 4.2f
    fun px(x: Float) = (x - 0.3f) * sx
    fun py(y: Float) = (y - 1.8f) * sy
    return Path().apply {
        if (up) {
            moveTo(px(0.552972f), py(5.99999f))
            cubicTo(px(0.357777f), py(5.99999f), px(0.236376f), py(5.80702f), px(0.335157f), py(5.65378f))
            lineTo(px(2.72778f), py(1.94192f))
            cubicTo(px(2.84976f), py(1.75268f), px(3.15033f), py(1.75268f), px(3.27232f), py(1.94192f))
            lineTo(px(5.66494f), py(5.65378f))
            cubicTo(px(5.76372f), py(5.80702f), px(5.64232f), py(5.99999f), px(5.44713f), py(5.99999f))
            lineTo(px(0.552972f), py(5.99999f))
        } else {
            moveTo(px(0.552972f), py(1.79999f))
            cubicTo(px(0.357777f), py(1.79999f), px(0.236376f), py(1.99295f), px(0.335157f), py(2.1462f))
            lineTo(px(2.72778f), py(5.85806f))
            cubicTo(px(2.84976f), py(6.0473f), px(3.15033f), py(6.0473f), px(3.27232f), py(5.85806f))
            lineTo(px(5.66494f), py(2.1462f))
            cubicTo(px(5.76372f), py(1.99295f), px(5.64232f), py(1.79999f), px(5.44713f), py(1.79999f))
            lineTo(px(0.552972f), py(1.79999f))
        }
        close()
    }
}

// Zahl mit vorangestelltem Richtungsdreieck, so erscheinen alle Veraenderungen. Bei Null steht die Zahl ohne
// Dreieck, damit die Zeile nicht springt.
@Composable
fun ChangeValue(
    change: Double,
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleSmall,
    colored: Boolean = true,
    triangleWidth: Dp = 9.dp,
) {
    val signal = LocalSignal.current
    val tint = if (colored) signal.forChange(change) else MaterialTheme.colorScheme.onSurface
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (change != 0.0 && change.isFinite()) {
            DirectionTriangle(change, width = triangleWidth, color = tint)
            Spacer(Modifier.width(triangleWidth * 0.6f))
        }
        Text(text, style = style, color = tint, maxLines = 1)
    }
}

// Chevron nach unten fuer Auswahlzeilen ("Seit Kauf ⌄"). Gezeichnet statt als Zeichen gesetzt,
// damit Strichstaerke und Groesse zur Schrift passen.
@Composable
fun ChevronDown(
    modifier: Modifier = Modifier,
    width: Dp = 11.dp,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    stroke: Dp = 1.6.dp,
) {
    Canvas(modifier.size(width = width, height = width * 0.62f)) {
        val w = size.width
        val h = size.height
        val inset = stroke.toPx() / 2f
        val path = Path().apply {
            moveTo(inset, inset)
            lineTo(w / 2f, h - inset)
            lineTo(w - inset, inset)
        }
        drawPath(path, color, style = Stroke(width = stroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// Chevron nach rechts fuer Abschnittsueberschriften, die auf mehr verweisen.
@Composable
fun ChevronRight(
    modifier: Modifier = Modifier,
    height: Dp = 13.dp,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    stroke: Dp = 1.9.dp,
) {
    Canvas(modifier.size(width = height * 0.62f, height = height)) {
        val w = size.width
        val h = size.height
        val inset = stroke.toPx() / 2f
        val path = Path().apply {
            moveTo(inset, inset)
            lineTo(w - inset, h / 2f)
            lineTo(inset, h - inset)
        }
        drawPath(path, color, style = Stroke(width = stroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// Haken fuer die ausgewaehlte Zeile in einem Auswahlblatt.
@Composable
fun CheckMark(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    color: Color = MaterialTheme.colorScheme.onSurface,
    stroke: Dp = 2.dp,
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.10f, h * 0.54f)
            lineTo(w * 0.38f, h * 0.82f)
            lineTo(w * 0.92f, h * 0.20f)
        }
        drawPath(path, color, style = Stroke(width = stroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// Minuszeichen fuer den Hebel-Waehler.
@Composable
fun MinusGlyph(modifier: Modifier = Modifier, width: Dp = 18.dp, color: Color, stroke: Dp = 2.4.dp) {
    Canvas(modifier.size(width = width, height = stroke)) {
        drawLine(
            color,
            start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f),
            end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2f),
            strokeWidth = stroke.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

// Pluszeichen fuer Kauf- und Hinzufuegen-Knoepfe.
@Composable
fun PlusGlyph(modifier: Modifier = Modifier, size: Dp = 18.dp, color: Color, stroke: Dp = 2.4.dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = stroke.toPx()
        drawLine(
            color,
            start = androidx.compose.ui.geometry.Offset(sw / 2f, h / 2f),
            end = androidx.compose.ui.geometry.Offset(w - sw / 2f, h / 2f),
            strokeWidth = sw,
            cap = StrokeCap.Round,
        )
        drawLine(
            color,
            start = androidx.compose.ui.geometry.Offset(w / 2f, sw / 2f),
            end = androidx.compose.ui.geometry.Offset(w / 2f, h - sw / 2f),
            strokeWidth = sw,
            cap = StrokeCap.Round,
        )
    }
}
