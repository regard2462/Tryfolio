package java.time

import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.knownTimeZoneNames
import platform.Foundation.localTimeZone
import platform.Foundation.timeZoneForSecondsFromGMT
import platform.Foundation.timeZoneWithName

/*
 * Gemeinsame Hilfen fuer den java.time-Ersatz: Ganzzahl-Arithmetik wie Math.floorDiv/floorMod, Kalenderregeln
 * und die Anbindung an NSTimeZone fuer die Zeitzonen (Versatz zu einem Zeitpunkt).
 */

open class DateTimeException(message: String?, cause: Throwable? = null) : RuntimeException(message, cause)

internal fun floorDiv(a: Long, b: Long): Long {
    var q = a / b
    if (a % b != 0L && ((a xor b) < 0L)) q--
    return q
}

internal fun floorMod(a: Long, b: Long): Long = a - floorDiv(a, b) * b

internal fun floorDiv(a: Int, b: Int): Int {
    var q = a / b
    if (a % b != 0 && ((a xor b) < 0)) q--
    return q
}

internal fun floorMod(a: Int, b: Int): Int = a - floorDiv(a, b) * b

internal fun addExact(a: Long, b: Long): Long {
    val r = a + b
    if (((a xor r) and (b xor r)) < 0) throw ArithmeticException("long overflow")
    return r
}

internal fun multiplyExact(a: Long, b: Long): Long {
    val r = a * b
    if (a != 0L && (r / a != b || (a == -1L && b == Long.MIN_VALUE))) throw ArithmeticException("long overflow")
    return r
}

internal fun toIntExact(v: Long): Int {
    if (v < Int.MIN_VALUE || v > Int.MAX_VALUE) throw ArithmeticException("integer overflow")
    return v.toInt()
}

internal fun istSchaltjahr(jahr: Long): Boolean = (jahr and 3L) == 0L && (jahr % 100L != 0L || jahr % 400L == 0L)

internal fun monatsLaenge(monat: Int, schaltjahr: Boolean): Int = when (monat) {
    2 -> if (schaltjahr) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
}

internal const val SEKUNDEN_JE_TAG = 86_400L
internal const val NANOS_JE_SEKUNDE = 1_000_000_000L
internal const val NANOS_JE_TAG = SEKUNDEN_JE_TAG * NANOS_JE_SEKUNDE
internal const val TAGE_0000_BIS_1970 = 146_097L * 5L - (30L * 365L + 7L)

/** Zahl mit fuehrenden Nullen auf [breite] Stellen. */
internal fun stellen(wert: Long, breite: Int): String {
    val s = kotlin.math.abs(wert).toString().padStart(breite, '0')
    return if (wert < 0) "-$s" else s
}

internal fun stellen(wert: Int, breite: Int): String = stellen(wert.toLong(), breite)

/** Jahr wie in LocalDate.toString: vierstellig, ab 10000 mit Plus, negativ mit Minus. */
internal fun isoJahr(jahr: Int, plusAb10000: Boolean): String {
    val betrag = kotlin.math.abs(jahr)
    return when {
        betrag < 1000 -> (if (jahr < 0) "-" else "") + betrag.toString().padStart(4, '0')
        jahr > 9999 && plusAb10000 -> "+$jahr"
        else -> jahr.toString()
    }
}

// ------------------------------------------------------------ Foundation

internal fun jsJetztMs(): Double = de.troetenrepublik.app.ios.jetztMs().toDouble()

internal fun jsZoneBekannt(id: String): Boolean = platform.Foundation.NSTimeZone.timeZoneWithName(id) != null

internal fun jsStandardZone(): String = platform.Foundation.NSTimeZone.localTimeZone.name

internal fun jsZonenListe(): String = platform.Foundation.NSTimeZone.knownTimeZoneNames.joinToString(",")

/** Versatz der Zone [zone] zur UTC in Sekunden zum Zeitpunkt [ms] (Millisekunden seit 1970), aus den Zonendaten von iOS. */
internal fun jsVersatzSekunden(zone: String, ms: Double): Int {
    val z = zonen.getOrPut(zone) { platform.Foundation.NSTimeZone.timeZoneWithName(zone) ?: platform.Foundation.NSTimeZone.timeZoneForSecondsFromGMT(0) }
    return z.secondsFromGMTForDate(platform.Foundation.NSDate.dateWithTimeIntervalSince1970(kotlin.math.floor(ms / 1000.0))).toInt()
}

private val zonen = java.util.concurrent.ConcurrentHashMap<String, platform.Foundation.NSTimeZone>()
