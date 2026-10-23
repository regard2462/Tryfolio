package de.troetenrepublik.app.ui

import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Signalfarben fuer Kursbewegungen, unabhaengig vom Material-Schema.
data class Signal(
    val up: Color,
    val down: Color,
    val neutral: Color,
    // Die Depotkurve bleibt neutral, nur Zahlen werden eingefaerbt.
    val line: Color,
    // Faerbt die Kurve nach Richtung.
    val coloredLine: Boolean = false,
    // Blau fuer Links wie "Preisalarme >".
    val link: Color = Color(0xFF246DFF),
    val dark: Boolean = true,
) {
    fun lineFor(v: Double): Color = if (coloredLine && v != 0.0) forChange(v) else line

    fun forChange(v: Double): Color = when {
        v > 0 -> up
        v < 0 -> down
        else -> neutral
    }
}

// Gruen #02CA50, Rot #FF4034, Sekundaertext #BCC2CA, Grundflaeche reines Schwarz.
// Helles Design: reines Weiss, Flaechen in den hellen Graustufen derselben Leiter.
private object Tr {
    val green = Color(0xFF02CA50)
    val red = Color(0xFFFF4034)
    val secondary = Color(0xFFBCC2CA)
    val gray1 = Color(0xFFF2F3F4)
    val gray2 = Color(0xFFE6E7E9)
    val gray3 = Color(0xFFB5B7BA)
    val gray4 = Color(0xFF93969A)
    val gray5 = Color(0xFF6B6F74)
    val gray6 = Color(0xFF4A4C4F)
    val gray8 = Color(0xFF2E2F30)
    val gray9 = Color(0xFF202020)
    val elevated = Color(0xFF151515)
}

val LocalSignal = staticCompositionLocalOf {
    Signal(Tr.green, Tr.red, Tr.secondary, Color(0xFFFFFFFF))
}

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
    error = Tr.red,
    surfaceContainer = Tr.gray1,
    surfaceContainerHigh = Tr.gray2,
    surfaceContainerHighest = Tr.gray3,
    surfaceContainerLow = Color(0xFFF8F8F9),
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

// Ueberschriften gross, Werte fett, Zusatzangaben klein und grau.
private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = Sans, fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.4).sp),
    displayMedium = TextStyle(fontFamily = Sans, fontSize = 33.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
    displaySmall = TextStyle(fontFamily = Sans, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
    headlineLarge = TextStyle(fontFamily = Sans, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontFamily = Sans, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontFamily = Sans, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = Sans, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = Sans, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontFamily = Sans, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = Sans, fontSize = 17.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontSize = 15.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontFamily = Sans, fontSize = 15.sp, fontWeight = FontWeight.Bold),
    labelSmall = TextStyle(fontFamily = Sans, fontSize = 13.sp, fontWeight = FontWeight.Medium),
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TroeteTheme(dark: Boolean = true, coloredChart: Boolean = false, content: @Composable () -> Unit) {
    // Gruen und Rot sind in beiden Designs gleich, nur Neutralton und Kurvenfarbe wechseln.
    val base = if (dark)
        Signal(Tr.green, Tr.red, Tr.secondary, Color(0xFFFFFFFF), dark = true)
    else
        Signal(Tr.green, Tr.red, Tr.gray5, Color(0xFF000000), dark = false)
    // Ansichtmodus: Kurve neutral oder in Gruen und Rot.
    val signal = base.copy(coloredLine = coloredChart)

    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
    ) {
        // Alles Antippbare hellt sich kurz auf, im hellen Design dunkelt es ab. Der Standardwert von Material ist auf
        // Schwarz kaum zu sehen.
        val ripple = RippleConfiguration(
            color = if (dark) Color.White else Color.Black,
            rippleAlpha = RippleAlpha(
                draggedAlpha = if (dark) 0.16f else 0.12f,
                focusedAlpha = if (dark) 0.16f else 0.12f,
                hoveredAlpha = if (dark) 0.08f else 0.06f,
                pressedAlpha = if (dark) 0.24f else 0.16f,
            ),
        )
        CompositionLocalProvider(LocalSignal provides signal, LocalRippleConfiguration provides ripple, content = content)
    }
}
