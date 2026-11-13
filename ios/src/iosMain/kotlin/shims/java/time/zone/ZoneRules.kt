package java.time.zone

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.floorDiv
import java.time.jsVersatzSekunden

open class ZoneRulesException(message: String?, cause: Throwable? = null) : DateTimeException(message, cause)

/**
 * Ersatz fuer java.time.zone.ZoneRules. Feste Versaetze oder eine IANA-Zone, deren Versatz NSTimeZone
 * liefert. Je Kalendertag (UTC) wird der Versatz einmal bestimmt und gemerkt; aendert er sich an einem Tag,
 * wird die Sekunde des Wechsels gesucht. Annahme: hoechstens ein Wechsel je Tag.
 */
class ZoneRules private constructor(private val fest: ZoneOffset?, private val zone: String?) {

    /** Versatz vor und nach einem Wechsel an einem Tag; [wechsel] = erste Sekunde mit [nachher], sonst Long.MIN_VALUE. */
    private class Tag(val vorher: Int, val wechsel: Long, val nachher: Int)

    private val tage = java.util.concurrent.ConcurrentHashMap<Long, Tag>()
    private val tagesBeginn = java.util.concurrent.ConcurrentHashMap<Long, Int>()

    private fun jsVersatz(sekunde: Long): Int {
        val ms = (sekunde.coerceIn(-8_640_000_000_000L, 8_640_000_000_000L) * 1000L).toDouble()
        return jsVersatzSekunden(zone!!, ms)
    }

    private fun versatzAmTagesBeginn(tag: Long): Int = tagesBeginn.getOrPut(tag) { jsVersatz(tag * 86_400L) }

    private fun tag(tag: Long): Tag = tage.getOrPut(tag) {
        val a = versatzAmTagesBeginn(tag)
        val b = versatzAmTagesBeginn(tag + 1)
        if (a == b) Tag(a, Long.MIN_VALUE, a)
        else Tag(a, wechselZwischen(tag * 86_400L, (tag + 1) * 86_400L, a), b)
    }

    /** Versatz in Sekunden zur Epochensekunde [sekunde]. */
    internal fun versatz(sekunde: Long): Int {
        fest?.let { return it.totalSeconds }
        val t = tag(floorDiv(sekunde, 86_400L))
        return if (t.wechsel == Long.MIN_VALUE || sekunde < t.wechsel) t.vorher else t.nachher
    }

    /**
     * Erste Sekunde in (lo, hi], deren Versatz nicht mehr [vorher] ist (Halbierung); lo hat noch [vorher].
     */
    internal fun wechselZwischen(lo: Long, hi: Long, vorher: Int): Long {
        if (fest != null) return hi
        var a = lo
        var b = hi
        while (b - a > 1) {
            val mitte = a + (b - a) / 2
            if (jsVersatz(mitte) == vorher) a = mitte else b = mitte
        }
        return b
    }

    /** Gueltige Versaetze fuer eine Ortszeit (Sekunden seit 1970 als waere sie UTC). */
    internal class Lage(val gueltig: List<Int>, val vorher: Int, val nachher: Int)

    internal fun lage(lokal: Long): Lage {
        fest?.let { return Lage(listOf(it.totalSeconds), it.totalSeconds, it.totalSeconds) }
        val vorher = versatz(lokal - 86_400L)
        val nachher = versatz(lokal + 86_400L)
        if (vorher == nachher) {
            val v = versatz(lokal - vorher)
            return if (v == vorher) Lage(listOf(vorher), vorher, nachher) else Lage(listOf(v), v, v)
        }
        val gueltig = listOf(vorher, nachher).filter { versatz(lokal - it) == it }
        return Lage(gueltig, vorher, nachher)
    }

    fun isFixedOffset(): Boolean = fest != null

    fun getOffset(instant: Instant): ZoneOffset = fest ?: ZoneOffset.ofTotalSeconds(versatz(instant.epochSecond))

    /** Versatz fuer eine Ortszeit; in einer Luecke der Versatz davor, bei Doppelung der fruehere (wie Java). */
    fun getOffset(localDateTime: LocalDateTime): ZoneOffset {
        if (fest != null) return fest
        val l = lage(localDateTime.toEpochSecond(ZoneOffset.UTC))
        return ZoneOffset.ofTotalSeconds(if (l.gueltig.isEmpty()) l.vorher else l.gueltig[0])
    }

    fun getValidOffsets(localDateTime: LocalDateTime): List<ZoneOffset> {
        if (fest != null) return listOf(fest)
        return lage(localDateTime.toEpochSecond(ZoneOffset.UTC)).gueltig.map { ZoneOffset.ofTotalSeconds(it) }
    }

    fun isValidOffset(localDateTime: LocalDateTime, offset: ZoneOffset): Boolean = offset in getValidOffsets(localDateTime)

    /** Hat [instant] einen anderen Versatz als die Normalzeit (Winter) dieses Jahres? Grob: Vergleich mit Januar und Juli. */
    fun isDaylightSavings(instant: Instant): Boolean {
        if (fest != null) return false
        val jahr = instant.atZone(ZoneOffset.UTC).year
        val januar = versatz(java.time.LocalDate.of(jahr, 1, 15).toEpochDay() * 86_400L)
        val juli = versatz(java.time.LocalDate.of(jahr, 7, 15).toEpochDay() * 86_400L)
        return versatz(instant.epochSecond) > minOf(januar, juli)
    }

    fun getStandardOffset(instant: Instant): ZoneOffset {
        if (fest != null) return fest
        val jahr = instant.atZone(ZoneOffset.UTC).year
        val januar = versatz(java.time.LocalDate.of(jahr, 1, 15).toEpochDay() * 86_400L)
        val juli = versatz(java.time.LocalDate.of(jahr, 7, 15).toEpochDay() * 86_400L)
        return ZoneOffset.ofTotalSeconds(minOf(januar, juli))
    }

    override fun equals(other: Any?): Boolean = other is ZoneRules && other.fest == fest && other.zone == zone
    override fun hashCode(): Int = (fest?.hashCode() ?: 0) * 31 + (zone?.hashCode() ?: 0)
    override fun toString(): String = if (fest != null) "ZoneRules[currentStandardOffset=$fest]" else "ZoneRules[$zone]"

    companion object {
        fun of(offset: ZoneOffset): ZoneRules = ZoneRules(offset, null)
        internal fun intl(zone: String): ZoneRules = ZoneRules(null, zone)
    }
}
