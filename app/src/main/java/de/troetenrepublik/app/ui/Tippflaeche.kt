package de.troetenrepublik.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import de.troetenrepublik.app.engine.Tippmass
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Tippbestaetigung der ganzen App: eine gleichmaessige, halb durchsichtige Flaeche in den Grenzen des Elements, die
// kurz ein- und ausblendet. Keine wandernde Welle. Das Thema setzt sie als LocalIndication, damit folgt ihr jedes
// clickable, selectable und toggleable; wer davor clip(Form) setzt, bekommt die Flaeche in dieser Form.

// Farbe und Deckkraft, vom Thema gesetzt (hell: dunkle Flaeche, dunkel: helle Flaeche).
data class TippFarbe(val farbe: Color, val gedrueckt: Float, val fokus: Float, val schwebend: Float)

val LocalTippFarbe = staticCompositionLocalOf { TippFarbe(Color.White, 0.14f, 0.06f, 0.06f) }

enum class TippForm {
    // Ganze Flaeche des Elements
    RECHTECK,
    // Textknopf ohne eigene Flaeche: abgerundetes Feld knapp um die Schrift
    TEXT,
    // Symbolknopf: Kreis um die Mitte
    KREIS,
}

// links, rechts und voll gelten nur fuer TEXT: links und rechts sind der Abstand, den der Knopf selbst schon zwischen
// Rand und Schrift hat; voll belegt die ganze Hoehe (grosse Schrift, zwei Zeilen). ueber gilt nur fuer RECHTECK: so weit
// reicht die Flaeche seitlich ueber das Element hinaus.
data class Tippflaeche(
    val form: TippForm = TippForm.RECHTECK, val links: Dp = TIPP_TEXT_RAND, val rechts: Dp = links, val voll: Boolean = false,
    val ueber: Dp = 0.dp,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = TippKnoten(interactionSource, form, links, rechts, voll, ueber)
}

// So weit steht die Flaeche eines Textknopfs seitlich ueber die Schrift hinaus.
val TIPP_TEXT_RAND = 10.dp
private val TIPP_TEXT_ECKE = 10.dp
private val TIPP_TEXT_MIN = 28.dp
private val TIPP_TEXT_MAX = 36.dp
private val TIPP_TEXT_EINZEILIG = 56.dp
private val TIPP_KREIS = 20.dp
private const val TIPP_EIN = 60
private const val TIPP_HALT = 70L
private const val TIPP_AUS = 160

private class TippKnoten(
    private val quelle: InteractionSource, private val form: TippForm, private val links: Dp, private val rechts: Dp, private val voll: Boolean,
    private val ueber: Dp,
) : Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {
    private val deckkraft = Animatable(0f)
    private var druecke = 0
    private var schwebt = false
    private var fokus = false
    private var ziel = 0f
    private var lauf: Job? = null

    override fun onAttach() {
        druecke = 0; schwebt = false; fokus = false; ziel = 0f
        coroutineScope.launch {
            deckkraft.snapTo(0f)
            quelle.interactions.collect { i ->
                when (i) {
                    is PressInteraction.Press -> druecke++
                    is PressInteraction.Release, is PressInteraction.Cancel -> druecke = (druecke - 1).coerceAtLeast(0)
                    is HoverInteraction.Enter -> schwebt = true
                    is HoverInteraction.Exit -> schwebt = false
                    is FocusInteraction.Focus -> fokus = true
                    is FocusInteraction.Unfocus -> fokus = false
                    else -> return@collect
                }
                wechseln()
            }
        }
    }

    private fun wechseln() {
        val f = currentValueOf(LocalTippFarbe)
        val vorher = ziel
        val neu = Tippmass.deckkraft(druecke > 0, fokus, schwebt, f.gedrueckt, f.fokus, f.schwebend)
        if (neu == vorher) return
        ziel = neu
        lauf?.cancel()
        lauf = coroutineScope.launch {
            if (neu < vorher) {
                // Ein kurzer Tipp meldet Druecken und Loslassen fast gleichzeitig: erst ganz einblenden und kurz
                // stehen lassen, sonst waere nichts zu sehen.
                if (deckkraft.value < vorher) {
                    deckkraft.animateTo(vorher, tween(TIPP_EIN))
                    delay(TIPP_HALT)
                }
                deckkraft.animateTo(neu, tween(TIPP_AUS))
            } else deckkraft.animateTo(neu, tween(TIPP_EIN))
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val a = deckkraft.value
        if (a <= 0f) return
        val farbe = currentValueOf(LocalTippFarbe).farbe
        when (form) {
            TippForm.RECHTECK -> {
                val u = ueber.toPx()
                if (u == 0f) drawRect(farbe, alpha = a) else drawRect(farbe, Offset(-u, 0f), Size(size.width + 2 * u, size.height), alpha = a)
            }
            TippForm.TEXT -> {
                val r = Tippmass.textFlaeche(
                    size.width, size.height, links.toPx(), rechts.toPx(),
                    TIPP_TEXT_RAND.toPx(), TIPP_TEXT_MIN.toPx(), TIPP_TEXT_MAX.toPx(), if (voll) 0f else TIPP_TEXT_EINZEILIG.toPx(),
                )
                val ecke = TIPP_TEXT_ECKE.toPx()
                drawRoundRect(farbe, Offset(r[0], r[1]), Size(r[2] - r[0], r[3] - r[1]), CornerRadius(ecke, ecke), alpha = a)
            }
            TippForm.KREIS -> drawCircle(farbe, Tippmass.kreisRadius(size.width, size.height, TIPP_KREIS.toPx()), alpha = a)
        }
    }
}

// Antippbare Schrift ohne eigene Flaeche (Link, Textknopf): Die Bestaetigung ist ein abgerundetes Feld knapp um die
// Schrift, auch wenn die Tippflaeche selbst groesser ist. links und rechts: Abstand, den der folgende padding-Aufruf
// zwischen Rand und Schrift legt (0.dp, wenn die Schrift am Rand der Tippflaeche steht).
fun Modifier.textKlick(
    links: Dp = TIPP_TEXT_RAND, rechts: Dp = links, voll: Boolean = false,
    enabled: Boolean = true, role: Role? = null, onClickLabel: String? = null, onClick: () -> Unit,
): Modifier = clickable(
    interactionSource = null, indication = Tippflaeche(TippForm.TEXT, links, rechts, voll),
    enabled = enabled, onClickLabel = onClickLabel, role = role, onClick = onClick,
)

// Zeile in einem seitlich eingerueckten Bereich (Spalte mit Rand, Karte, getoentes Feld): Die Bestaetigung reicht
// seitlich ueber die Zeile hinaus bis an den Bildschirmrand oder bis an die beschnittene Form der Karte, statt als
// Kasten zwischen den Raendern zu stehen. ueber ist der Rand des Bereichs; in beschnittenen Karten darf er groesser sein.
fun Modifier.zeilenKlick(
    ueber: Dp = 20.dp, enabled: Boolean = true, role: Role? = null, onClickLabel: String? = null, onClick: () -> Unit,
): Modifier = clickable(
    interactionSource = null, indication = Tippflaeche(ueber = ueber),
    enabled = enabled, onClickLabel = onClickLabel, role = role, onClick = onClick,
)

// Die drei Material-Knoepfe der App mit derselben Bestaetigung. Sie tragen die Namen der Material-Bausteine und
// gelten im Paket ui ohne Import; Dateien ausserhalb importieren sie von hier. Die Welle von Material ist in ihnen
// abgeschaltet, Groesse und Tippflaeche bleiben die von Material.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(), content: @Composable () -> Unit,
) {
    val quelle = remember { MutableInteractionSource() }
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        androidx.compose.material3.IconButton(
            onClick = onClick, modifier = modifier.indication(quelle, TIPP_SYMBOL), enabled = enabled,
            colors = colors, interactionSource = quelle, content = content,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ButtonDefaults.textShape, colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null, border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding, content: @Composable RowScope.() -> Unit,
) {
    val quelle = remember { MutableInteractionSource() }
    val anzeige = remember(contentPadding) {
        Tippflaeche(TippForm.TEXT, contentPadding.calculateLeftPadding(LayoutDirection.Ltr), contentPadding.calculateRightPadding(LayoutDirection.Ltr))
    }
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        androidx.compose.material3.TextButton(
            onClick = onClick, modifier = modifier.indication(quelle, anzeige), enabled = enabled, shape = shape, colors = colors,
            elevation = elevation, border = border, contentPadding = contentPadding, interactionSource = quelle, content = content,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownMenuItem(
    text: @Composable () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null, trailingIcon: @Composable (() -> Unit)? = null, enabled: Boolean = true,
    colors: MenuItemColors = MenuDefaults.itemColors(), contentPadding: PaddingValues = MenuDefaults.DropdownMenuItemContentPadding,
) {
    val quelle = remember { MutableInteractionSource() }
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        androidx.compose.material3.DropdownMenuItem(
            text = text, onClick = onClick, modifier = modifier.indication(quelle, TIPP_ZEILE), leadingIcon = leadingIcon,
            trailingIcon = trailingIcon, enabled = enabled, colors = colors, contentPadding = contentPadding, interactionSource = quelle,
        )
    }
}

private val TIPP_SYMBOL = Tippflaeche(TippForm.KREIS)
private val TIPP_ZEILE = Tippflaeche(TippForm.RECHTECK)
