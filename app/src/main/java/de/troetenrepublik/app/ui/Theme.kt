package de.troetenrepublik.app.ui

import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.troetenrepublik.app.engine.Erscheinung
import de.troetenrepublik.app.engine.GlobusPalette

// Signalfarben fuer Kursbewegungen, unabhaengig vom Material-Schema.
data class Signal(
    val up: Color,
    val down: Color,
    val neutral: Color,
    // Die Depotkurve bleibt neutral, nur Zahlen werden eingefaerbt.
    val line: Color,
    // Faerbt die Kurve nach Richtung.
    val coloredLine: Boolean = false,
    // Blau nur fuer Antippbares wie "Preisalarme >".
    val link: Color = Tr.linkDunkel,
    val dark: Boolean = true,
    // Sepia ist ein helles Design (dark = false) mit warmen Farben.
    val sepia: Boolean = false,
    // Gelb fuer Warnungen, Alarme und Hinweise (Ampel gelb, wichtige Meldung, Dividende), getrennt von Gewinn und Verlust.
    val warn: Color = Tr.warnDunkel,
    // Rand von Eingabefeldern und inaktive Seitenpunkte, mit mindestens 3:1 zum Grund.
    val rand: Color = Tr.randDunkel,
    // Farben des Globus und der Kartenausschnitte je Erscheinungsbild.
    val globus: GlobusPalette = GlobusPalette.DUNKEL,
) {
    fun lineFor(v: Double): Color = if (coloredLine && v != 0.0) forChange(v) else line

    fun forChange(v: Double): Color = when {
        v > 0 -> up
        v < 0 -> down
        else -> neutral
    }
}

// Gruen #02CA50, Rot #FF3D47, Sekundaertext #BCC2CA, Grundflaeche reines Schwarz.
// Helles Design: reines Weiss, Flaechen in den hellen Graustufen derselben Leiter. Gruen, Rot, Link und Gelb sind dort
// dunkler, damit Schrift auf Weiss und auf den Kartenflaechen mindestens 4,5:1 erreicht (WCAG).
private object Tr {
    val green = Color(0xFF02CA50)
    val red = Color(0xFFFF3D47)
    val greenHell = Color(Erscheinung.HELL_GRUEN)
    val redHell = Color(Erscheinung.HELL_ROT)
    val greenSepia = Color(Erscheinung.SEPIA_GRUEN)
    val redSepia = Color(Erscheinung.SEPIA_ROT)
    val linkSepia = Color(Erscheinung.SEPIA_LINK)
    val warnSepia = Color(Erscheinung.SEPIA_WARN)
    val randSepia = Color(Erscheinung.SEPIA_RAND)
    val sepiaText = Color(Erscheinung.SEPIA_TEXT)
    val sepiaText2 = Color(Erscheinung.SEPIA_TEXT2)
    val sepiaGrund = Color(Erscheinung.SEPIA_GRUND)
    val sepiaFlaeche = Color(Erscheinung.SEPIA_FLAECHE)
    val sepiaHoch = Color(Erscheinung.SEPIA_FLAECHE_HOCH)
    val sepiaHoechst = Color(Erscheinung.SEPIA_FLAECHE_HOECHST)
    val linkDunkel = Color(0xFF5B95FF)
    val linkHell = Color(0xFF1A5BE0)
    val warnDunkel = Color(0xFFF5C33B)
    val warnHell = Color(Erscheinung.HELL_WARN)
    val randDunkel = Color(0xFF6B6F74)
    val randHell = Color(0xFF8A8D91)
    val secondary = Color(0xFFBCC2CA)
    val gray1 = Color(0xFFF2F3F4)
    val gray2 = Color(0xFFE6E7E9)
    val gray3 = Color(0xFFB5B7BA)
    val gray4 = Color(0xFF93969A)
    val gray5 = Color(0xFF5F6368)
    val gray6 = Color(0xFF4A4C4F)
    val gray8 = Color(0xFF2E2F30)
    val gray9 = Color(0xFF202020)
    val elevated = Color(0xFF151515)
}

// Farben der drei Designs fuer die Vorschaukarten unter Erscheinungsbild, aus denselben Werten wie das Theme.
internal class DesignFarben(val grund: Color, val flaeche: Color, val text: Color, val text2: Color, val up: Color, val down: Color)

internal val DESIGN_DUNKEL = DesignFarben(Color(0xFF000000), Tr.elevated, Color(0xFFFFFFFF), Tr.secondary, Tr.green, Tr.red)
internal val DESIGN_HELL = DesignFarben(Color(Erscheinung.HELL_GRUND), Color(Erscheinung.HELL_FLAECHE), Color(Erscheinung.HELL_TEXT), Color(Erscheinung.HELL_TEXT2), Tr.greenHell, Tr.redHell)
internal val DESIGN_SEPIA = DesignFarben(Tr.sepiaGrund, Tr.sepiaFlaeche, Tr.sepiaText, Tr.sepiaText2, Tr.greenSepia, Tr.redSepia)

val LocalSignal = staticCompositionLocalOf {
    Signal(Tr.green, Tr.red, Tr.secondary, Color(0xFFFFFFFF))
}

// Farben fuer Kategorien (Positionen, Linien, Anteile), ohne Gruen und Rot, damit nichts wie Gewinn oder Verlust
// aussieht. Jede Farbe hat auf Weiss und auf Schwarz mindestens 3:1.
object Kategorie {
    // Im Sepia-Modus warme, gedaempfte Toene derselben Reihenfolge; das Theme setzt den Schalter.
    var sepia by androidx.compose.runtime.mutableStateOf(false)

    private val farbenNormal = listOf(
        Color(0xFF2F7DE1), Color(0xFFB7791F), Color(0xFF9F57E8), Color(0xFF0E8A8A),
        Color(0xFFD9468F), Color(0xFF7C8A00), Color(0xFF6B7280),
    )
    private val farbenSepia = listOf(
        Color(0xFF4A6A8F), Color(0xFF9A6418), Color(0xFF7E5A8C), Color(0xFF3F7A72),
        Color(0xFFA84E6A), Color(0xFF6B7020), Color(0xFF7A6E5E),
    )
    val farben: List<Color> get() = if (sepia) farbenSepia else farbenNormal

    fun farbe(i: Int): Color = farben[Math.floorMod(i, farben.size)]

    // Zweiseitige Skala ohne Gewinn-/Verlustbedeutung, etwa fuer die Korrelation: Blau und Ocker.
    val blau = Color(0xFF2F7DE1)
    val ocker = Color(0xFFB7791F)

    // Hellere, frohere Toene fuer Analytics (seit 5.92): Himmelblau, Orange, Lavendel, Tuerkis, Rosa, Gold, Indigo,
    // Schiefer. Gleiche Regel: kein Gruen und Rot, auf Weiss und auf Schwarz mindestens 3:1.
    private val frohNormal = listOf(
        Color(0xFF3D8BF0), Color(0xFFDB7432), Color(0xFF9B6DE8), Color(0xFF1E9E9E),
        Color(0xFFE4588F), Color(0xFFB8860B), Color(0xFF5B7CF0), Color(0xFF7F8BA0),
    )
    private val frohSepia = listOf(
        Color(0xFF4F75A0), Color(0xFFB3622A), Color(0xFF86609A), Color(0xFF3E827A),
        Color(0xFFB0566F), Color(0xFF9A7414), Color(0xFF5A6698), Color(0xFF857868),
    )
    val froh: List<Color> get() = if (sepia) frohSepia else frohNormal
}

// Schrift auf einer Farbflaeche: Schwarz oder Weiss, je nachdem was mehr Kontrast hat. Die Grenze 0,18 ist die
// relative Leuchtdichte, bei der beide gleich gut lesbar sind.
fun aufFarbe(flaeche: Color): Color = if (flaeche.luminance() > 0.18f) Color.Black else Color.White

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),
    secondary = Tr.gray9,
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Tr.elevated,
    onSurfaceVariant = Tr.secondary,
    outline = Tr.gray8,
    outlineVariant = Tr.gray9,
    error = Tr.red,
    surfaceContainer = Tr.elevated,
    surfaceContainerHigh = Tr.gray9,
    surfaceContainerHighest = Tr.gray8,
    surfaceContainerLow = Color(0xFF0D0D0D),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF000000),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Tr.gray1,
    onSecondary = Color(0xFF000000),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Tr.gray1,
    onSurfaceVariant = Tr.gray5,
    outline = Tr.gray2,
    outlineVariant = Tr.gray1,
    error = Tr.redHell,
    surfaceContainer = Tr.gray1,
    surfaceContainerHigh = Tr.gray2,
    surfaceContainerHighest = Tr.gray3,
    surfaceContainerLow = Color(0xFFF8F8F9),
)

// Sepia: Papierton, braune Schrift, Linien und Flaechen in gedaempftem Braun. Hell wie Hell, nur warm.
private val SepiaColors = lightColorScheme(
    primary = Color(Erscheinung.SEPIA_AKZENT),
    onPrimary = Tr.sepiaGrund,
    secondary = Tr.sepiaFlaeche,
    onSecondary = Tr.sepiaText,
    background = Tr.sepiaGrund,
    onBackground = Tr.sepiaText,
    surface = Tr.sepiaGrund,
    onSurface = Tr.sepiaText,
    surfaceVariant = Tr.sepiaFlaeche,
    onSurfaceVariant = Tr.sepiaText2,
    outline = Tr.sepiaHoch,
    outlineVariant = Tr.sepiaFlaeche,
    error = Tr.redSepia,
    surfaceContainer = Tr.sepiaFlaeche,
    surfaceContainerHigh = Tr.sepiaHoch,
    surfaceContainerHighest = Tr.sepiaHoechst,
    surfaceContainerLow = Color(Erscheinung.SEPIA_FLAECHE_NIEDRIG),
)

// Eckenradien an einer Stelle, damit Karten, Blaetter und Knoepfe nicht auseinanderlaufen.
object Radius {
    val chip = 100.dp        // Filterpillen, Aktionsknoepfe, Avatare, voll abgerundet
    val tile = 16.dp         // Schnellzugriffskacheln
    val card = 16.dp         // Kennzahlenkarten, Insights
    val sheet = 24.dp
    val logo = 100.dp        // Firmenzeichen sitzen in einem runden Feld
    val field = 16.dp        // Eingabefelder, Waehler
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

private val Sans = FontFamily.SansSerif

// Umbruch nur zwischen ganzen Woertern, ohne Silbentrennung: Fliesstext als Absatz, Ueberschriften als Titelzeile.
private fun TextStyle.fliess() = copy(hyphens = Hyphens.None, lineBreak = LineBreak.Paragraph)
private fun TextStyle.kopf() = copy(hyphens = Hyphens.None, lineBreak = LineBreak.Heading)
// Schlagzeilen in Listen: Umbruch nur an Wortgrenzen, ohne Silbentrennung und ohne ausgleichende Titelzeilen.
internal fun TextStyle.schlagzeile() = copy(hyphens = Hyphens.None, lineBreak = LineBreak.Paragraph)

// Ueberschriften gross, Werte fett, Zusatzangaben klein und grau.
private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = Sans, fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.4).sp).kopf(),
    displayMedium = TextStyle(fontFamily = Sans, fontSize = 33.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp).kopf(),
    displaySmall = TextStyle(fontFamily = Sans, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp).kopf(),
    headlineLarge = TextStyle(fontFamily = Sans, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp).kopf(),
    headlineMedium = TextStyle(fontFamily = Sans, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp).kopf(),
    headlineSmall = TextStyle(fontFamily = Sans, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp).kopf(),
    titleLarge = TextStyle(fontFamily = Sans, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp).kopf(),
    titleMedium = TextStyle(fontFamily = Sans, fontSize = 18.sp, fontWeight = FontWeight.SemiBold).kopf(),
    titleSmall = TextStyle(fontFamily = Sans, fontSize = 16.sp, fontWeight = FontWeight.SemiBold).kopf(),
    bodyLarge = TextStyle(fontFamily = Sans, fontSize = 17.sp).fliess(),
    bodyMedium = TextStyle(fontFamily = Sans, fontSize = 15.sp).fliess(),
    bodySmall = TextStyle(fontFamily = Sans, fontSize = 14.sp).fliess(),
    labelLarge = TextStyle(fontFamily = Sans, fontSize = 15.sp, fontWeight = FontWeight.SemiBold).fliess(),
    labelMedium = TextStyle(fontFamily = Sans, fontSize = 15.sp, fontWeight = FontWeight.Bold).fliess(),
    labelSmall = TextStyle(fontFamily = Sans, fontSize = 13.sp, fontWeight = FontWeight.Medium).fliess(),
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TroeteTheme(dark: Boolean = true, coloredChart: Boolean = false, sepia: Boolean = false, content: @Composable () -> Unit) {
    // Im hellen Design sind Gruen, Rot, Link, Gelb und Rand dunkler, damit sie auf Weiss lesbar bleiben.
    // Sepia gilt nur, wenn nicht dunkel gewaehlt ist.
    val papier = sepia && !dark
    androidx.compose.runtime.SideEffect { Kategorie.sepia = papier }
    val base = if (dark)
        Signal(Tr.green, Tr.red, Tr.secondary, Color(0xFFFFFFFF), dark = true, globus = GlobusPalette.DUNKEL)
    else if (papier)
        Signal(
            Tr.greenSepia, Tr.redSepia, Tr.sepiaText2, Tr.sepiaText,
            link = Tr.linkSepia, dark = false, sepia = true, warn = Tr.warnSepia, rand = Tr.randSepia, globus = GlobusPalette.SEPIA,
        )
    else
        Signal(
            Tr.greenHell, Tr.redHell, Tr.gray5, Color(0xFF000000),
            link = Tr.linkHell, dark = false, warn = Tr.warnHell, rand = Tr.randHell, globus = GlobusPalette.HELL,
        )
    // Ansichtmodus: Kurve neutral oder in Gruen und Rot.
    val signal = base.copy(coloredLine = coloredChart)

    MaterialTheme(
        colorScheme = if (dark) DarkColors else if (papier) SepiaColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
    ) {
        // Alles Antippbare hellt sich beim Druecken kurz und gleichmaessig auf, im hellen Design dunkelt es ab
        // (Tippflaeche.kt). Was in Material-Bausteinen noch eine eigene Welle zeichnet, bekommt dieselbe Farbe und Staerke.
        val tipp = if (dark) TippFarbe(Color.White, gedrueckt = 0.14f, fokus = 0.06f, schwebend = 0.06f)
        else TippFarbe(if (papier) Tr.sepiaText else Color.Black, gedrueckt = 0.10f, fokus = 0.05f, schwebend = 0.05f)
        val ripple = RippleConfiguration(
            color = tipp.farbe,
            rippleAlpha = RippleAlpha(draggedAlpha = tipp.gedrueckt, focusedAlpha = tipp.fokus, hoveredAlpha = tipp.schwebend, pressedAlpha = tipp.gedrueckt),
        )
        CompositionLocalProvider(
            LocalSignal provides signal, LocalRippleConfiguration provides ripple,
            LocalTippFarbe provides tipp, LocalIndication provides TIPP_STANDARD, content = content,
        )
    }
}

private val TIPP_STANDARD = Tippflaeche()
