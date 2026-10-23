package de.troetenrepublik.app.ui

import androidx.compose.foundation.clickable
import de.troetenrepublik.app.plattform.horizontalScrollMaus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.roundToInt

// Reiterleiste ueber einem Pager. Die weisse Pille wandert beim Wischen anteilig zum neuen Reiter
// und passt ihre Breite an. Die Schrift liegt in zwei Ebenen, hell auf den grauen Pillen und dunkel
// in der weissen Pille, auf deren Form beschnitten. So bleibt jeder Buchstabe lesbar, auch wenn die
// Pille halb ueber einem Reiter steht. Die Leiste rollt mit, sodass der Reiter mit seinem linken
// Nachbarn im Bild steht.
@Composable
fun MitlaufendeReiter(
    anzahl: Int,
    pager: PagerState,
    titel: (Int) -> String,
    modifier: Modifier = Modifier,
    zusatz: (Int) -> String? = { null },
    onClick: (Int) -> Unit,
) = MitlaufendeReiter(anzahl, { pager.currentPage + pager.currentPageOffsetFraction }, titel, modifier, zusatz, onClick = onClick)

// Fuer jeden stand mit Bruchteil, etwa aus einer Wischwahl oder rememberGleitStand.
// klein: kleinere Pillen fuer eine zweite Leiste unter einer Ueberschrift.
@Composable
fun MitlaufendeReiter(
    anzahl: Int,
    stand: () -> Float,
    titel: (Int) -> String,
    modifier: Modifier = Modifier,
    zusatz: (Int) -> String? = { null },
    klein: Boolean = false,
    onClick: (Int) -> Unit,
) {
    val aktuell = rememberUpdatedState(stand)
    val scroll = rememberScrollState()
    // Lage und Breite jeder Pille in Pixeln, relativ zum Inhalt der Leiste
    val lagen = remember { mutableStateMapOf<Int, Triple<Float, Float, Float>>() }
    val grau = MaterialTheme.colorScheme.surfaceContainerHigh
    val weiss = MaterialTheme.colorScheme.onSurface
    val schriftAus = MaterialTheme.colorScheme.onSurface
    val schriftAn = MaterialTheme.colorScheme.surface
    val radius = with(LocalDensity.current) { Radius.chip.toPx() }

    fun stand(): Float = aktuell.value().coerceIn(0f, (anzahl - 1).coerceAtLeast(0).toFloat())
    val schrift = if (klein) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleSmall
    val innen = if (klein) Modifier.padding(horizontal = 14.dp, vertical = 8.dp) else Modifier.padding(horizontal = 18.dp, vertical = 11.dp)

    // Nur in Zeichen-Lambdas lesen.
    fun pille(): Rect? {
        if (lagen.isEmpty()) return null
        val f = stand()
        val i = floor(f).toInt().coerceIn(0, anzahl - 1)
        val j = (i + 1).coerceAtMost(anzahl - 1)
        val t = f - i
        val a = lagen[i] ?: return null
        val b = lagen[j] ?: a
        val x = a.first + (b.first - a.first) * t
        val w = a.second + (b.second - a.second) * t
        return Rect(Offset(x, 0f), Size(w, a.third))
    }

    LaunchedEffect(anzahl) {
        snapshotFlow { stand() to lagen.toMap() }.collect { (f, l) ->
            if (l.isEmpty()) return@collect
            val i = floor(f).toInt().coerceIn(0, anzahl - 1)
            val j = (i + 1).coerceAtMost(anzahl - 1)
            val t = f - i
            fun ziel(k: Int): Float = l[(k - 1).coerceAtLeast(0)]?.first ?: 0f
            val z = ziel(i) + (ziel(j) - ziel(i)) * t
            scroll.scrollTo(z.roundToInt().coerceIn(0, scroll.maxValue))
        }
    }

    val texte = List(anzahl) { k -> zusatz(k)?.let { "${titel(k)}  $it" } ?: titel(k) }
    Box(modifier.fillMaxWidth().horizontalScrollMaus(scroll)) {
        // Ebene 1: graue Pillen mit heller Schrift, antippbar
        Row(
            Modifier
                .padding(horizontal = 20.dp)
                .drawBehind { lagen.forEach { (_, l) -> drawRoundRect(grau, Offset(l.first, 0f), Size(l.second, l.third), CornerRadius(radius, radius)) } },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            texte.forEachIndexed { k, text ->
                Box(
                    Modifier
                        .onPlaced { c ->
                            val neu = Triple(c.positionInParent().x, c.size.width.toFloat(), c.size.height.toFloat())
                            if (lagen[k] != neu) lagen[k] = neu
                        }
                        .clip(RoundedCornerShape(Radius.chip))
                        .clickable(role = Role.Tab) { onClick(k) }
                        .then(innen),
                ) {
                    Text(text, style = schrift, color = schriftAus)
                }
            }
        }
        // Ebene 2: weisse Pille mit dunkler Schrift, auf die Pille beschnitten, gleiche Anordnung wie Ebene 1
        Row(
            Modifier
                .clearAndSetSemantics { }
                .padding(horizontal = 20.dp)
                .drawWithContent {
                    val p = pille() ?: return@drawWithContent
                    val form = Path().apply { addRoundRect(RoundRect(p, CornerRadius(radius, radius))) }
                    clipPath(form) {
                        drawRect(weiss, p.topLeft, p.size)
                        this@drawWithContent.drawContent()
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            texte.forEach { text ->
                Box(innen) {
                    Text(text, style = schrift, color = schriftAn)
                }
            }
        }
    }
}
