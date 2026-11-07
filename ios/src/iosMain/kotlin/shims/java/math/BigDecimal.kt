package java.math

/* Ersatz fuer java.math.BigDecimal (Dezimalziffern als Text, ohne Rechenoperationen) und RoundingMode. */

enum class RoundingMode { UP, DOWN, CEILING, FLOOR, HALF_UP, HALF_DOWN, HALF_EVEN, UNNECESSARY }

/**
 * Wert = Vorzeichen * Ziffern * 10^-scale, wie in Java. valueOf(Double) geht wie Java ueber Double.toString
 * (kuerzeste Ziffernfolge, die den Wert genau trifft), daher valueOf(0.1) = 0.1 und valueOf(100.0) = 100.0.
 */
class BigDecimal private constructor(
    /** -1, 0 oder 1 */
    private val vz: Int,
    /** Ziffern des Betrags ohne fuehrende Nullen; "0" bei null */
    private val ziffern: String,
    private val skala: Int,
) : Number(), Comparable<BigDecimal> {

    constructor(value: String) : this(lesen(value))
    constructor(value: Int) : this(ausLong(value.toLong(), 0))
    constructor(value: Long) : this(ausLong(value, 0))

    private constructor(b: BigDecimal) : this(b.vz, b.ziffern, b.skala)

    fun scale(): Int = skala
    fun signum(): Int = vz
    fun precision(): Int = ziffern.length

    fun negate(): BigDecimal = if (vz == 0) this else BigDecimal(-vz, ziffern, skala)
    fun abs(): BigDecimal = if (vz < 0) negate() else this

    fun setScale(newScale: Int): BigDecimal = setScale(newScale, RoundingMode.UNNECESSARY)

    fun setScale(newScale: Int, roundingMode: RoundingMode): BigDecimal {
        if (newScale == skala) return this
        if (newScale > skala) {
            if (vz == 0) return BigDecimal(0, "0", newScale)
            return BigDecimal(vz, ziffern + "0".repeat(newScale - skala), newScale)
        }
        val weg = skala - newScale
        val behalten: String
        val gestrichen: String
        if (ziffern.length > weg) {
            behalten = ziffern.substring(0, ziffern.length - weg)
            gestrichen = ziffern.substring(ziffern.length - weg)
        } else {
            behalten = "0"
            gestrichen = ziffern.padStart(weg, '0')
        }
        val rest = gestrichen.any { it != '0' }
        val vergleichHalb = when {
            gestrichen[0] > '5' -> 1
            gestrichen[0] < '5' -> -1
            gestrichen.substring(1).any { it != '0' } -> 1
            else -> 0
        }
        val positiv = vz > 0
        val aufrunden = rest && when (roundingMode) {
            RoundingMode.UP -> true
            RoundingMode.DOWN -> false
            RoundingMode.CEILING -> positiv
            RoundingMode.FLOOR -> !positiv
            RoundingMode.HALF_UP -> vergleichHalb >= 0
            RoundingMode.HALF_DOWN -> vergleichHalb > 0
            RoundingMode.HALF_EVEN -> vergleichHalb > 0 || (vergleichHalb == 0 && (behalten.last() - '0') % 2 == 1)
            RoundingMode.UNNECESSARY -> throw ArithmeticException("Rounding necessary")
        }
        val neu = ohneFuehrendeNullen(if (aufrunden) plusEins(behalten) else behalten)
        return BigDecimal(if (neu == "0") 0 else vz, neu, newScale)
    }

    /** Wie Java 8+: null wird zu 0 mit Skala 0. */
    fun stripTrailingZeros(): BigDecimal {
        if (vz == 0) return ZERO
        val ohne = ziffern.trimEnd('0')
        return if (ohne.length == ziffern.length) this else BigDecimal(vz, ohne, skala - (ziffern.length - ohne.length))
    }

    fun toPlainString(): String {
        val sb = StringBuilder()
        if (vz < 0) sb.append('-')
        when {
            skala == 0 -> sb.append(ziffern)
            skala < 0 -> if (vz == 0) sb.append('0') else sb.append(ziffern).append("0".repeat(-skala))
            ziffern.length > skala -> sb.append(ziffern, 0, ziffern.length - skala).append('.').append(ziffern, ziffern.length - skala, ziffern.length)
            else -> sb.append("0.").append("0".repeat(skala - ziffern.length)).append(ziffern)
        }
        return sb.toString()
    }

    /** Wie Java: wissenschaftlich, wenn die Skala negativ ist oder der Exponent unter -6 laege. */
    override fun toString(): String {
        val angepasst = -skala.toLong() + (ziffern.length - 1)
        if (skala >= 0 && angepasst >= -6) return toPlainString()
        val sb = StringBuilder()
        if (vz < 0) sb.append('-')
        sb.append(ziffern[0])
        if (ziffern.length > 1) sb.append('.').append(ziffern, 1, ziffern.length)
        sb.append('E')
        if (angepasst >= 0) sb.append('+')
        sb.append(angepasst)
        return sb.toString()
    }

    fun toEngineeringString(): String = toString()

    override fun toDouble(): Double = toString().toDouble()
    override fun toFloat(): Float = toDouble().toFloat()
    override fun toLong(): Long = setScale(0, RoundingMode.DOWN).let { g -> if (g.vz == 0) 0L else (g.vz * (g.ziffern.toLongOrNull() ?: g.ziffern.takeLast(18).toLong())) }
    override fun toInt(): Int = toLong().toInt()
    override fun toShort(): Short = toLong().toShort()
    override fun toByte(): Byte = toLong().toByte()

    fun longValueExact(): Long {
        val g = stripTrailingZeros()
        if (g.skala > 0) throw ArithmeticException("Rounding necessary")
        return g.toPlainString().toLongOrNull() ?: throw ArithmeticException("Overflow")
    }

    fun intValueExact(): Int {
        val l = longValueExact()
        if (l < Int.MIN_VALUE || l > Int.MAX_VALUE) throw ArithmeticException("Overflow")
        return l.toInt()
    }

    override fun compareTo(other: BigDecimal): Int {
        if (vz != other.vz) return vz.compareTo(other.vz)
        if (vz == 0) return 0
        val s = maxOf(skala, other.skala)
        val a = ziffern + "0".repeat(s - skala)
        val b = other.ziffern + "0".repeat(s - other.skala)
        val betrag = if (a.length != b.length) a.length.compareTo(b.length) else a.compareTo(b).let { if (it < 0) -1 else if (it > 0) 1 else 0 }
        return betrag * vz
    }

    /** Wie Java: gleich nur bei gleichem Wert und gleicher Skala (2.0 != 2.00). */
    override fun equals(other: Any?): Boolean = other is BigDecimal && other.vz == vz && other.ziffern == ziffern && other.skala == skala

    override fun hashCode(): Int = 31 * (vz * ziffern.hashCode()) + skala

    fun max(other: BigDecimal): BigDecimal = if (compareTo(other) >= 0) this else other
    fun min(other: BigDecimal): BigDecimal = if (compareTo(other) <= 0) this else other

    companion object {
        val ZERO = BigDecimal(0, "0", 0)
        val ONE = BigDecimal(1, "1", 0)
        val TWO = BigDecimal(1, "2", 0)
        val TEN = BigDecimal(1, "10", 0)

        const val ROUND_UP = 0
        const val ROUND_DOWN = 1
        const val ROUND_CEILING = 2
        const val ROUND_FLOOR = 3
        const val ROUND_HALF_UP = 4
        const val ROUND_HALF_DOWN = 5
        const val ROUND_HALF_EVEN = 6
        const val ROUND_UNNECESSARY = 7

        private fun ohneFuehrendeNullen(s: String): String = s.trimStart('0').ifEmpty { "0" }

        private fun plusEins(s: String): String {
            val z = s.toCharArray()
            var i = z.size - 1
            while (i >= 0) {
                if (z[i] == '9') { z[i] = '0'; i-- } else { z[i] = z[i] + 1; return z.concatToString() }
            }
            return "1" + z.concatToString()
        }

        private fun ausLong(v: Long, skala: Int): BigDecimal {
            if (v == 0L) return BigDecimal(0, "0", skala)
            val text = if (v == Long.MIN_VALUE) "9223372036854775808" else kotlin.math.abs(v).toString()
            return BigDecimal(if (v < 0) -1 else 1, text, skala)
        }

        /** Java-Schreibweise: [+-]Ziffern[.Ziffern][(e|E)[+-]Ziffern]. */
        private fun lesen(text: String): BigDecimal {
            val m = Regex("([+-]?)([0-9]*)(?:\\.([0-9]*))?(?:[eE]([+-]?[0-9]+))?").matchEntire(text)
            val ganz = m?.groupValues?.get(2).orEmpty()
            val bruch = m?.groupValues?.get(3).orEmpty()
            if (m == null || (ganz.isEmpty() && bruch.isEmpty())) throw NumberFormatException("Character array is missing \"exponent\" mark 'e' or 'E'.")
            val exp = m.groupValues[4].takeIf { it.isNotEmpty() }?.let { it.toIntOrNull() ?: throw NumberFormatException("Exponent overflow.") } ?: 0
            val roh = ohneFuehrendeNullen(ganz + bruch)
            val skala = bruch.length - exp
            val negativ = m.groupValues[1] == "-"
            val vz = when {
                roh == "0" -> 0
                negativ -> -1
                else -> 1
            }
            return BigDecimal(vz, roh, skala)
        }

        fun valueOf(unscaledVal: Long): BigDecimal = ausLong(unscaledVal, 0)
        fun valueOf(unscaledVal: Long, scale: Int): BigDecimal = ausLong(unscaledVal, scale)

        /** Wie Java: new BigDecimal(Double.toString(d)). */
        fun valueOf(`val`: Double): BigDecimal {
            if (`val`.isNaN() || `val`.isInfinite()) throw NumberFormatException("Infinite or NaN")
            val negativ = `val` < 0
            if (`val` == 0.0) return BigDecimal(0, "0", 1)
            // kuerzeste Ziffern und Exponent: Wert = 0.D * 10^dezExp
            val (d, exp10) = kuerzesteZiffern(kotlin.math.abs(`val`))
            val dezExp = exp10 + d.length
            val vz = if (negativ) -1 else 1
            return if (dezExp in -2..7) {
                // Java schreibt einfach: mindestens eine Nachkommastelle
                val ganz = if (dezExp > 0) (if (dezExp >= d.length) d + "0".repeat(dezExp - d.length) else d.substring(0, dezExp)) else "0"
                var bruch = if (dezExp > 0) (if (dezExp >= d.length) "" else d.substring(dezExp)) else "0".repeat(-dezExp) + d
                if (bruch.isEmpty()) bruch = "0"
                BigDecimal(vz, ohneFuehrendeNullen(ganz + bruch), bruch.length)
            } else {
                // wissenschaftlich: d.ddd E (dezExp - 1)
                val rest = if (d.length > 1) d.substring(1) else "0"
                BigDecimal(vz, ohneFuehrendeNullen(d.substring(0, 1) + rest), rest.length - (dezExp - 1))
            }
        }

        /** Ziffern ohne Nullen am Ende und Zehnerexponent, so dass Wert = Ziffern * 10^exp (aus Double.toString, kuerzeste Ziffernfolge wie in Java). */
        private fun kuerzesteZiffern(x: Double): Pair<String, Int> {
            val s = x.toString()
            val eStelle = s.indexOfFirst { it == 'e' || it == 'E' }
            val mantisse = if (eStelle >= 0) s.substring(0, eStelle) else s
            var exp = if (eStelle >= 0) s.substring(eStelle + 1).removePrefix("+").toInt() else 0
            val punkt = mantisse.indexOf('.')
            var ziffern = if (punkt >= 0) mantisse.removeRange(punkt, punkt + 1) else mantisse
            if (punkt >= 0) exp -= mantisse.length - punkt - 1
            ziffern = ziffern.trimStart('0')
            val ohne = ziffern.trimEnd('0')
            exp += ziffern.length - ohne.length
            return ohne to exp
        }
    }
}
