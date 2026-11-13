package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Appearance
import kotlin.math.pow

// Erscheinungsbild ohne Oberflaeche: Wahl von Dunkel, Hell und Sepia und die Farbzahlen (ARGB) von Hell und Sepia. Das
// Theme (ui/Theme.kt) liest die Zahlen von hier, der Test rechnet die Kontraste nach.
object Erscheinung {
    // Dunkel gilt fuer Dunkel und, bei System, wenn das Geraet dunkel ist. Hell und Sepia sind helle Designs.
    fun dunkel(a: Appearance, systemDunkel: Boolean): Boolean = when (a) {
        Appearance.DARK -> true
        Appearance.LIGHT, Appearance.SEPIA -> false
        Appearance.SYSTEM -> systemDunkel
    }

    fun sepia(a: Appearance): Boolean = a == Appearance.SEPIA

    // Helles Design: reines Weiss, Flaechen in hellen Graustufen
    const val HELL_GRUND = 0xFFFFFFFF.toInt()
    const val HELL_FLAECHE = 0xFFF2F3F4.toInt()
    const val HELL_TEXT = 0xFF000000.toInt()
    const val HELL_TEXT2 = 0xFF5F6368.toInt()
    const val HELL_GRUEN = 0xFF187233.toInt()
    const val HELL_ROT = 0xFFC41F2B.toInt()
    const val HELL_LINK = 0xFF1A5BE0.toInt()
    const val HELL_WARN = 0xFF8F6400.toInt()
    const val HELL_RAND = 0xFF8A8D91.toInt()

    // Sepia: warmes Papier, braune Schrift, gedaempfte Signalfarben
    const val SEPIA_GRUND = 0xFFF4ECD8.toInt()
    const val SEPIA_FLAECHE = 0xFFEBE0C6.toInt()
    const val SEPIA_FLAECHE_NIEDRIG = 0xFFF0E7D0.toInt()
    const val SEPIA_FLAECHE_HOCH = 0xFFDDCFAF.toInt()
    const val SEPIA_FLAECHE_HOECHST = 0xFFD2C29E.toInt()
    const val SEPIA_TEXT = 0xFF3B2F22.toInt()
    const val SEPIA_TEXT2 = 0xFF65543D.toInt()
    const val SEPIA_AKZENT = 0xFF5B3A1E.toInt()
    const val SEPIA_GRUEN = 0xFF2A6430.toInt()
    const val SEPIA_ROT = 0xFF9C2822.toInt()
    const val SEPIA_LINK = 0xFF1F4E8C.toInt()
    const val SEPIA_WARN = 0xFF7A5200.toInt()
    const val SEPIA_RAND = 0xFF85734F.toInt()

    private fun linear(k: Int): Double {
        val c = k / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    // Relative Leuchtdichte nach WCAG
    fun leuchtdichte(argb: Int): Double =
        0.2126 * linear((argb shr 16) and 0xFF) + 0.7152 * linear((argb shr 8) and 0xFF) + 0.0722 * linear(argb and 0xFF)

    // Kontrastverhaeltnis nach WCAG, 1 bis 21
    fun kontrast(a: Int, b: Int): Double {
        val x = leuchtdichte(a); val y = leuchtdichte(b)
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }
}
