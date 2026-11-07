package de.troetenrepublik.app.ios.jvm

import kotlin.math.abs
import kotlin.math.pow

// Fester Nachkommateil wie Java: kuerzeste Ziffernfolge des Werts, dann kaufmaennisch gerundet
private fun fest(v: Double, p: Int): String = java.math.BigDecimal.valueOf(v).setScale(p, java.math.RoundingMode.HALF_UP).toPlainString()

// Wissenschaftliche Schreibweise mit p Nachkommastellen, etwa 1.50e+3 (Trenner und Exponent setzt formatieren)
private fun exponent(v: Double, p: Int): String {
    if (v == 0.0) return "0" + (if (p > 0) "." + "0".repeat(p) else "") + "e+0"
    var e = kotlin.math.floor(kotlin.math.log10(v)).toInt()
    var m = fest(v / 10.0.pow(e), p)
    if (m.startsWith("10")) { e++; m = fest(v / 10.0.pow(e), p) }
    return m + "e" + (if (e < 0) "-" else "+") + kotlin.math.abs(e)
}

/** Deutsch schreibt 1.234,56, Englisch 1,234.56. */
private fun deutsch(locale: java.util.Locale?): Boolean =
    (locale ?: java.util.Locale.getDefault()).language.lowercase().startsWith("de")

private fun gruppieren(ziffern: String, trenner: Char): String {
    val sb = StringBuilder()
    val n = ziffern.length
    for (i in 0 until n) {
        if (i > 0 && (n - i) % 3 == 0) sb.append(trenner)
        sb.append(ziffern[i])
    }
    return sb.toString()
}

private fun zahlDouble(a: Any?): Double = when (a) {
    is Double -> a
    is Float -> a.toDouble()
    is Number -> a.toDouble()
    else -> throw IllegalArgumentException("Zahl erwartet: $a")
}

private fun hex(a: Any?): String = when (a) {
    is Byte -> (a.toInt() and 0xFF).toString(16)
    is Short -> (a.toInt() and 0xFFFF).toString(16)
    is Int -> a.toUInt().toString(16)
    is Long -> a.toULong().toString(16)
    is Char -> a.code.toString(16)
    else -> throw IllegalArgumentException("Ganzzahl erwartet: $a")
}

/**
 * String.format wie in Java fuer die Muster, die die App benutzt: %d, %s, %f, %x, %e, %c, %b, %n, %% mit den Schaltern
 * - + 0 , ( und Leerzeichen, Breite und Nachkommastellen. Dezimal- und Tausendertrenner nach [locale].
 */
fun formatieren(locale: java.util.Locale?, muster: String, args: Array<out Any?>): String {
    val sb = StringBuilder()
    var i = 0
    var naechstes = 0
    val de = deutsch(locale)
    val komma = if (de) ',' else '.'
    val tausend = if (de) '.' else ','
    while (i < muster.length) {
        val c = muster[i]
        if (c != '%') { sb.append(c); i++; continue }
        var j = i + 1
        // Argumentnummer 1$ usw.
        var index: Int? = null
        val nr = Regex("^(\\d+)\\$").find(muster.substring(j))
        if (nr != null) { index = nr.groupValues[1].toInt() - 1; j += nr.value.length }
        val schalter = StringBuilder()
        while (j < muster.length && muster[j] in "-#+ 0,(") { schalter.append(muster[j]); j++ }
        var breite = 0
        while (j < muster.length && muster[j].isDigit()) { breite = breite * 10 + (muster[j] - '0'); j++ }
        var stellen = -1
        if (j < muster.length && muster[j] == '.') {
            j++
            stellen = 0
            while (j < muster.length && muster[j].isDigit()) { stellen = stellen * 10 + (muster[j] - '0'); j++ }
        }
        if (j >= muster.length) { sb.append(muster.substring(i)); break }
        val art = muster[j]
        i = j + 1
        if (art == '%') { sb.append('%'); continue }
        if (art == 'n') { sb.append('\n'); continue }
        val arg = if (index != null) args.getOrNull(index) else args.getOrNull(naechstes++)
        val links = '-' in schalter
        val nullen = '0' in schalter
        val gruppe = ',' in schalter
        var text: String = when (art) {
            'd' -> {
                val v = when (arg) {
                    is Byte, is Short, is Int, is Long -> (arg as Number).toLong()
                    else -> throw IllegalArgumentException("Ganzzahl erwartet: $arg")
                }
                val ziffern = abs(v).toString().let { if (v == Long.MIN_VALUE) it.removePrefix("-") else it }
                val g = if (gruppe) gruppieren(ziffern, tausend) else ziffern
                vorzeichen(v < 0, g, schalter)
            }
            'x', 'X' -> hex(arg).let { if (art == 'X') it.uppercase() else it }
            'f' -> {
                val v = zahlDouble(arg)
                if (v.isNaN()) "NaN" else if (v.isInfinite()) (if (v > 0) "Infinity" else "-Infinity") else {
                    val roh = fest(abs(v), if (stellen < 0) 6 else stellen)
                    val ganz = roh.substringBefore('.')
                    val bruch = roh.substringAfter('.', "")
                    val g = if (gruppe) gruppieren(ganz, tausend) else ganz
                    val zahl = if (bruch.isEmpty()) g else g + komma + bruch
                    vorzeichen(v < 0 || (v == 0.0 && 1.0 / v < 0 && roh.any { it in '1'..'9' }), zahl, schalter)
                }
            }
            'e', 'E' -> {
                val v = zahlDouble(arg)
                val roh = exponent(abs(v), if (stellen < 0) 6 else stellen).replace('.', komma)
                val mant = roh.substringBefore('e')
                val exp = roh.substringAfter('e').toInt()
                val t = mant + "e" + (if (exp < 0) "-" else "+") + abs(exp).toString().padStart(2, '0')
                vorzeichen(v < 0, if (art == 'E') t.uppercase() else t, schalter)
            }
            's', 'S' -> (arg?.toString() ?: "null").let { if (stellen >= 0) it.take(stellen) else it }.let { if (art == 'S') it.uppercase() else it }
            'c' -> when (arg) { is Char -> arg.toString(); is Int -> arg.toChar().toString(); else -> arg.toString() }
            'b', 'B' -> (if (arg is Boolean) arg.toString() else (arg != null).toString())
            else -> throw IllegalArgumentException("Format nicht unterstuetzt: %$art")
        }
        if (text.length < breite) {
            text = when {
                links -> text.padEnd(breite)
                nullen && art in "dfxXe" -> {
                    val v = text.takeWhile { it == '-' || it == '+' || it == ' ' || it == '(' }
                    v + text.substring(v.length).padStart(breite - v.length, '0')
                }
                else -> text.padStart(breite)
            }
        }
        sb.append(text)
    }
    return sb.toString()
}

private fun vorzeichen(negativ: Boolean, zahl: String, schalter: CharSequence): String = when {
    negativ && '(' in schalter -> "($zahl)"
    negativ -> "-$zahl"
    '+' in schalter -> "+$zahl"
    ' ' in schalter -> " $zahl"
    else -> zahl
}
