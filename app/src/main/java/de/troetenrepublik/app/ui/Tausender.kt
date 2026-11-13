package de.troetenrepublik.app.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

// Ziffern mit Tausendertrenner anzeigen, im Feld steht weiter nur die Ziffernfolge ("10000" erscheint als "10.000").

// Ziffernfolge mit trenner vor jeder Dreiergruppe von rechts
internal fun tausenderGruppiert(ziffern: String, trenner: Char): String {
    val n = ziffern.length
    val sb = StringBuilder(n + n / 3)
    ziffern.forEachIndexed { i, c ->
        if (i > 0 && (n - i) % 3 == 0) sb.append(trenner)
        sb.append(c)
    }
    return sb.toString()
}

// Cursorstellen zwischen Ziffernfolge und gruppierter Anzeige
internal class TausenderAbbildung(private val n: Int, private val angezeigt: String, private val trenner: Char) : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int {
        val o = offset.coerceIn(0, n)
        var trennerDavor = 0
        for (i in 1 until o) if ((n - i) % 3 == 0) trennerDavor++
        return o + trennerDavor
    }

    override fun transformedToOriginal(offset: Int): Int {
        val t = offset.coerceIn(0, angezeigt.length)
        return t - angezeigt.take(t).count { it == trenner }
    }
}

internal class TausenderTransformation(private val trenner: Char) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val ziffern = text.text
        val angezeigt = tausenderGruppiert(ziffern, trenner)
        return TransformedText(AnnotatedString(angezeigt), TausenderAbbildung(ziffern.length, angezeigt, trenner))
    }
}

// Trenner der gewaehlten Sprache, wie ihn Fmt.num setzt
internal fun tausenderTrenner(): Char = Fmt.num(1000.0, 0).first { !it.isDigit() }
