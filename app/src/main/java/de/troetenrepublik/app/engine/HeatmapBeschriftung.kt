package de.troetenrepublik.app.engine

import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.roundToInt

// Beschriftung und Farben der Heatmap-Kacheln; gleiche Regeln in der App und im Widget.
object HeatmapBeschriftung {
    // Ab dieser Aenderung (Prozent) ist eine Kachel voll eingefaerbt, je Zeitraum der Heatmap (wie
    // Branchentrends.ZEITRAEUME): am Tag 3 %, im Jahr 30 %.
    val VOLL_PROZENT: List<Double> = listOf(3.0, 6.0, 10.0, 15.0, 20.0, 30.0)

    // Was in einer Kachel steht.
    enum class Stufe { NICHTS, KUERZEL, KUERZEL_PROZENT, NAME_PROZENT }

    // Masse eines gesetzten Texts in Pixeln.
    data class Text(val breite: Float, val hoehe: Float)

    // kuerzelGross steht ueber der Prozentzahl, kuerzelKlein allein in kleinen Kacheln; prozent: die Fassungen der
    // Prozentzahl, bevorzugte zuerst (zwei, dann eine Nachkommastelle); name: der volle Name umbrochen auf hoechstens
    // drei Zeilen (null, wenn er so nicht ganz passt), nameWort: Breite seines breitesten Worts.
    data class Masse(
        val kuerzelGross: Text,
        val kuerzelKlein: Text,
        val prozent: List<Text>,
        val name: Text? = null,
        val nameWort: Float = 0f,
    )

    // prozent = Index in Masse.prozent, -1 ohne Prozentzahl.
    data class Wahl(val stufe: Stufe, val prozent: Int = -1)

    // Kuerzel ohne Boersenendung: SAP.DE wird SAP, BTC-EUR wird BTC.
    fun kuerzel(symbol: String): String = symbol.substringBefore('.').substringBefore('-').ifBlank { symbol }

    // Was in eine Kachel von breite x hoehe passt; innen ist der Rand je Achse zusammen. Den vollen Namen gibt es nur
    // bei gezoomter Karte, sonst Kuerzel mit Prozent, dann das kleine Kuerzel allein, sonst nichts. Kein Text wird
    // gekuerzt oder kleiner gesetzt.
    fun waehlen(breite: Float, hoehe: Float, innen: Float, m: Masse, gezoomt: Boolean): Wahl {
        val pb = breite - innen
        val ph = hoehe - innen
        if (pb <= 0f || ph <= 0f) return Wahl(Stufe.NICHTS)
        val name = m.name
        if (gezoomt && name != null && name.breite <= pb && m.nameWort <= pb) {
            val i = m.prozent.indexOfFirst { it.breite <= pb && name.hoehe + it.hoehe <= ph }
            if (i >= 0) return Wahl(Stufe.NAME_PROZENT, i)
        }
        if (m.kuerzelGross.breite <= pb) {
            val i = m.prozent.indexOfFirst { it.breite <= pb && m.kuerzelGross.hoehe + it.hoehe <= ph }
            if (i >= 0) return Wahl(Stufe.KUERZEL_PROZENT, i)
        }
        if (m.kuerzelKlein.breite <= pb && m.kuerzelKlein.hoehe <= ph) return Wahl(Stufe.KUERZEL)
        return Wahl(Stufe.NICHTS)
    }

    // Wie stark eine Kachel eingefaerbt ist: 0 neutral (ohne Kurs oder unter 0,05 %), sonst 0,3 bis 1, positiv fuer
    // gestiegen, negativ fuer gefallen; ab voll Prozent ganz.
    fun anteil(aenderung: Double?, voll: Double): Float {
        if (aenderung == null || !aenderung.isFinite() || abs(aenderung) < 0.05) return 0f
        val v = if (voll <= 0.0) 3.0 else voll
        val staerke = (abs(aenderung) / v).coerceIn(0.0, 1.0)
        val a = (0.3 + 0.7 * staerke).toFloat()
        return if (aenderung > 0) a else -a
    }

    // Kanal 0..255 als linearer Wert 0..1.
    private fun linear(c: Int): Double {
        val x = c / 255.0
        return if (x <= 0.04045) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4)
    }

    // Linearer Wert zurueck zum Kanal 0..255.
    private fun gamma(c: Double): Int {
        val x = c.coerceIn(0.0, 1.0)
        val g = if (x <= 0.0031308) 12.92 * x else 1.055 * x.pow(1.0 / 2.4) - 0.055
        return (g.coerceIn(0.0, 1.0) * 255.0).roundToInt()
    }

    // ARGB nach Oklab (L, A, B).
    private fun zuOklab(argb: Int): DoubleArray {
        val r = linear((argb shr 16) and 255)
        val g = linear((argb shr 8) and 255)
        val b = linear(argb and 255)
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return doubleArrayOf(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
        )
    }

    // Zwei Farben (ARGB) mischen wie lerp in Compose: im Farbraum Oklab, Deckkraft linear. t wird auf 0..1 begrenzt.
    fun mischen(von: Int, nach: Int, t: Float): Int {
        if (t <= 0f) return von
        if (t >= 1f) return nach
        val a = zuOklab(von)
        val b = zuOklab(nach)
        val u = t.toDouble()
        val gl = a[0] + (b[0] - a[0]) * u
        val ga = a[1] + (b[1] - a[1]) * u
        val gb = a[2] + (b[2] - a[2]) * u
        val l = (gl + 0.3963377774 * ga + 0.2158037573 * gb).pow(3)
        val m = (gl - 0.1055613458 * ga - 0.0638541728 * gb).pow(3)
        val s = (gl - 0.0894841775 * ga - 1.2914855480 * gb).pow(3)
        val r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
        val g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
        val bl = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
        val a0 = (von ushr 24) and 255
        val a1 = (nach ushr 24) and 255
        val alpha = (a0 + (a1 - a0) * u).roundToInt().coerceIn(0, 255)
        return (alpha shl 24) or (gamma(r) shl 16) or (gamma(g) shl 8) or gamma(bl)
    }

    // Farbe einer Kachel (ARGB): neutral um null, dann zunehmend hoch oder runter.
    fun farbe(aenderung: Double?, voll: Double, hoch: Int, runter: Int, neutral: Int): Int {
        val a = anteil(aenderung, voll)
        if (a == 0f) return neutral
        return mischen(neutral, if (a > 0) hoch else runter, abs(a))
    }

    // Relative Helligkeit 0 bis 1 (wie luminance in Compose), fuer helle oder dunkle Schrift auf der Kachel.
    fun helligkeit(argb: Int): Double {
        val r = linear((argb shr 16) and 255)
        val g = linear((argb shr 8) and 255)
        val b = linear(argb and 255)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    // Dunkle Schrift auf hellen Kacheln, wie aufFarbe in der App.
    fun dunkleSchrift(argb: Int): Boolean = helligkeit(argb) > 0.18
}
