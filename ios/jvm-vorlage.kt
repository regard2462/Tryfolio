// Erzeugt beim Bauen der iOS-App aus ios/jvm-vorlage.kt, je Paket des gemeinsamen Codes eine Datei:
// Teile der Kotlin- und Java-Standardbibliothek, die es nur auf der JVM gibt und die der Code ohne Import benutzt.
@file:Suppress("unused", "NOTHING_TO_INLINE", "FunctionName", "UNUSED_PARAMETER")

package PAKET

import kotlin.math.pow

internal object System {
    fun currentTimeMillis(): Long = de.troetenrepublik.app.ios.jetztMs()
    fun nanoTime(): Long = de.troetenrepublik.app.ios.uhrNs()
    fun getProperty(name: String): String? = null
    fun getProperty(name: String, standard: String): String = standard
    fun getenv(name: String): String? = null
    fun lineSeparator(): String = "\n"
}

internal object Math {
    const val PI: Double = kotlin.math.PI
    const val E: Double = kotlin.math.E
    fun round(a: Double): Long = if (a.isNaN()) 0L else kotlin.math.floor(a + 0.5).toLong()
    fun round(a: Float): Int = if (a.isNaN()) 0 else kotlin.math.floor(a + 0.5f).toInt()
    fun floorDiv(a: Long, b: Long): Long = a.floorDiv(b)
    fun floorDiv(a: Int, b: Int): Int = a.floorDiv(b)
    fun floorMod(a: Long, b: Long): Long = a.mod(b)
    fun floorMod(a: Int, b: Int): Int = a.mod(b)
    fun sqrt(a: Double): Double = kotlin.math.sqrt(a)
    fun cbrt(a: Double): Double = kotlin.math.cbrt(a)
    fun sin(a: Double): Double = kotlin.math.sin(a)
    fun cos(a: Double): Double = kotlin.math.cos(a)
    fun tan(a: Double): Double = kotlin.math.tan(a)
    fun atan(a: Double): Double = kotlin.math.atan(a)
    fun atan2(y: Double, x: Double): Double = kotlin.math.atan2(y, x)
    fun pow(a: Double, b: Double): Double = a.pow(b)
    fun exp(a: Double): Double = kotlin.math.exp(a)
    fun log(a: Double): Double = kotlin.math.ln(a)
    fun log10(a: Double): Double = kotlin.math.log10(a)
    fun hypot(a: Double, b: Double): Double = kotlin.math.hypot(a, b)
    fun abs(a: Double): Double = kotlin.math.abs(a)
    fun abs(a: Int): Int = kotlin.math.abs(a)
    fun abs(a: Long): Long = kotlin.math.abs(a)
    fun max(a: Double, b: Double): Double = kotlin.math.max(a, b)
    fun max(a: Int, b: Int): Int = kotlin.math.max(a, b)
    fun max(a: Long, b: Long): Long = kotlin.math.max(a, b)
    fun min(a: Double, b: Double): Double = kotlin.math.min(a, b)
    fun min(a: Int, b: Int): Int = kotlin.math.min(a, b)
    fun min(a: Long, b: Long): Long = kotlin.math.min(a, b)
    fun floor(a: Double): Double = kotlin.math.floor(a)
    fun ceil(a: Double): Double = kotlin.math.ceil(a)
    fun signum(a: Double): Double = kotlin.math.sign(a)
    fun random(): Double = kotlin.random.Random.nextDouble()
    fun rint(a: Double): Double = kotlin.math.round(a)
}

internal object Thread {
    fun sleep(millis: Long) { platform.posix.usleep((millis.coerceAtLeast(0L) * 1000L).toUInt()) }
}

// Eine gemeinsame Sperre fuer alle synchronized-Bloecke: grob, aber ohne neue Verklemmungen durch die Reihenfolge
internal inline fun <R> synchronized(lock: Any, block: () -> R): R = de.troetenrepublik.app.ios.Sperre.mit(block)

internal typealias Volatile = kotlin.concurrent.Volatile

// Ohne Wirkung: Die zwei Stellen im gemeinsamen Code (Kursabfrage starten und anhalten) laufen auf dem Hauptthread
internal annotation class Synchronized

internal object Charsets {
    val UTF_8: java.nio.charset.Charset = java.nio.charset.Charset.forName("UTF-8")
    val ISO_8859_1: java.nio.charset.Charset = java.nio.charset.Charset.forName("ISO-8859-1")
    val US_ASCII: java.nio.charset.Charset = java.nio.charset.Charset.forName("US-ASCII")
    val UTF_16: java.nio.charset.Charset = java.nio.charset.Charset.forName("UTF-16")
    val UTF_16BE: java.nio.charset.Charset = java.nio.charset.Charset.forName("UTF-16BE")
    val UTF_16LE: java.nio.charset.Charset = java.nio.charset.Charset.forName("UTF-16LE")
}

internal fun String.format(vararg args: Any?): String = de.troetenrepublik.app.ios.jvm.formatieren(null, this, args)
internal fun String.format(locale: java.util.Locale?, vararg args: Any?): String = de.troetenrepublik.app.ios.jvm.formatieren(locale, this, args)
internal fun String.Companion.format(format: String, vararg args: Any?): String = de.troetenrepublik.app.ios.jvm.formatieren(null, format, args)
internal fun String.Companion.format(locale: java.util.Locale?, format: String, vararg args: Any?): String =
    de.troetenrepublik.app.ios.jvm.formatieren(locale, format, args)

internal fun String(bytes: ByteArray, charset: java.nio.charset.Charset): String = de.troetenrepublik.app.ios.jvm.dekodieren(bytes, charset)
internal fun String(bytes: ByteArray, offset: Int, length: Int, charset: java.nio.charset.Charset): String =
    de.troetenrepublik.app.ios.jvm.dekodieren(bytes.copyOfRange(offset, offset + length), charset)
internal fun String(bytes: ByteArray): String = bytes.decodeToString()
internal fun String(chars: CharArray): String = chars.concatToString()
internal fun String(chars: CharArray, offset: Int, length: Int): String = chars.concatToString(offset, offset + length)
internal fun String.toByteArray(charset: java.nio.charset.Charset = Charsets.UTF_8): ByteArray = de.troetenrepublik.app.ios.jvm.kodieren(this, charset)
internal fun ByteArray.toString(charset: java.nio.charset.Charset): String = de.troetenrepublik.app.ios.jvm.dekodieren(this, charset)

internal val kotlinx.coroutines.Dispatchers.IO: kotlinx.coroutines.CoroutineDispatcher get() = de.troetenrepublik.app.ios.ioDispatcher

internal object Character {
    fun toChars(codePoint: Int): CharArray =
        if (codePoint < 0x10000) charArrayOf(codePoint.toChar())
        else (codePoint - 0x10000).let { charArrayOf((0xD800 + (it shr 10)).toChar(), (0xDC00 + (it and 0x3FF)).toChar()) }
    fun isLetter(c: Char): Boolean = c.isLetter()
    fun isDigit(c: Char): Boolean = c.isDigit()
    fun isLetterOrDigit(c: Char): Boolean = c.isLetterOrDigit()
    fun isWhitespace(c: Char): Boolean = c.isWhitespace()
    fun isUpperCase(c: Char): Boolean = c.isUpperCase()
    fun isLowerCase(c: Char): Boolean = c.isLowerCase()
    fun toUpperCase(c: Char): Char = c.uppercaseChar()
    fun toLowerCase(c: Char): Char = c.lowercaseChar()
}

internal object Integer {
    const val MAX_VALUE: Int = Int.MAX_VALUE
    const val MIN_VALUE: Int = Int.MIN_VALUE
    fun toHexString(i: Int): String = i.toUInt().toString(16)
    fun parseInt(s: String): Int = s.toInt()
    fun parseInt(s: String, radix: Int): Int = s.toInt(radix)
    fun valueOf(s: String): Int = s.toInt()
}

internal fun <T : Comparable<T>> Iterable<T>.toSortedSet(): Set<T> = sorted().toCollection(LinkedHashSet())
internal fun <T> Iterable<T>.toSortedSet(comparator: Comparator<in T>): Set<T> = sortedWith(comparator).toCollection(LinkedHashSet())
internal fun <K : Comparable<K>, V> Map<out K, V>.toSortedMap(): MutableMap<K, V> = entries.sortedBy { it.key }.associateTo(LinkedHashMap()) { it.key to it.value }
internal fun <K, V> Map<out K, V>.toSortedMap(comparator: Comparator<in K>): MutableMap<K, V> =
    entries.sortedWith { a, b -> comparator.compare(a.key, b.key) }.associateTo(LinkedHashMap()) { it.key to it.value }
internal fun <K, V> MutableMap<K, V>.putIfAbsent(key: K, value: V): V? = get(key) ?: run { put(key, value); null }

internal fun charset(charsetName: String): java.nio.charset.Charset = java.nio.charset.Charset.forName(charsetName)

internal fun ByteArray.inputStream(): java.io.ByteArrayInputStream = java.io.ByteArrayInputStream(this)

internal fun <K, V> MutableMap<K, V>.merge(key: K, value: V, remappingFunction: (V, V) -> V?): V? {
    val alt = get(key)
    val neu = if (alt == null) value else remappingFunction(alt, value)
    if (neu == null) remove(key) else put(key, neu)
    return neu
}
