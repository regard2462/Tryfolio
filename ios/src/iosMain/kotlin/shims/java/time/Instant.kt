package java.time

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.time.temporal.Temporal
import java.time.temporal.TemporalAccessor
import java.time.temporal.TemporalAdjuster
import java.time.temporal.TemporalField
import java.time.temporal.nichtUnterstuetzt

/** Ersatz fuer java.time.Instant: Sekunden seit 1970-01-01T00:00Z und Nanosekunden (0 bis 999.999.999). */
class Instant private constructor(private val sek: Long, private val ns: Int) : Temporal, TemporalAdjuster, Comparable<Instant> {

    val epochSecond: Long get() = sek
    val nano: Int get() = ns

    fun toEpochMilli(): Long = addExact(multiplyExact(sek, 1000L), (ns / 1_000_000).toLong())

    fun plusSeconds(secondsToAdd: Long): Instant = plus(secondsToAdd, 0)
    fun plusMillis(millisToAdd: Long): Instant = plus(millisToAdd / 1000, (millisToAdd % 1000) * 1_000_000)
    fun plusNanos(nanosToAdd: Long): Instant = plus(0, nanosToAdd)
    fun minusSeconds(secondsToSubtract: Long): Instant = plusSeconds(-secondsToSubtract)
    fun minusMillis(millisToSubtract: Long): Instant = plusMillis(-millisToSubtract)
    fun minusNanos(nanosToSubtract: Long): Instant = plusNanos(-nanosToSubtract)

    private fun plus(sekunden: Long, nanos: Long): Instant {
        if (sekunden == 0L && nanos == 0L) return this
        val s = addExact(addExact(sek, sekunden), nanos / NANOS_JE_SEKUNDE)
        return ofEpochSecond(s, ns + nanos % NANOS_JE_SEKUNDE)
    }

    fun atZone(zone: ZoneId): ZonedDateTime = ZonedDateTime.ofInstant(this, zone)
    fun atOffset(offset: ZoneOffset): OffsetDateTime = OffsetDateTime.ofInstant(this, offset)

    override fun adjustInto(temporal: Temporal): Temporal = when (temporal) {
        is Instant -> this
        else -> throw DateTimeException("Unsupported temporal: $temporal")
    }

    override fun compareTo(other: Instant): Int {
        val c = sek.compareTo(other.sek)
        return if (c != 0) c else ns - other.ns
    }

    fun isAfter(otherInstant: Instant): Boolean = compareTo(otherInstant) > 0
    fun isBefore(otherInstant: Instant): Boolean = compareTo(otherInstant) < 0

    override fun equals(other: Any?): Boolean = other is Instant && other.sek == sek && other.ns == ns
    override fun hashCode(): Int = (sek xor (sek ushr 32)).toInt() + 51 * ns

    /** ISO-8601 in UTC wie DateTimeFormatter.ISO_INSTANT, z. B. 2026-10-15T12:00:00Z. */
    override fun toString(): String = DateTimeFormatter.ISO_INSTANT.format(this)

    override fun isSupported(field: TemporalField): Boolean = field == ChronoField.INSTANT_SECONDS || field == ChronoField.NANO_OF_SECOND ||
        field == ChronoField.MICRO_OF_SECOND || field == ChronoField.MILLI_OF_SECOND

    override fun getLong(field: TemporalField): Long = when (field) {
        ChronoField.INSTANT_SECONDS -> sek
        ChronoField.NANO_OF_SECOND -> ns.toLong()
        ChronoField.MICRO_OF_SECOND -> (ns / 1000).toLong()
        ChronoField.MILLI_OF_SECOND -> (ns / 1_000_000).toLong()
        else -> nichtUnterstuetzt(field)
    }

    companion object {
        private const val MIN_SEKUNDE = -31_557_014_167_219_200L
        private const val MAX_SEKUNDE = 31_556_889_864_403_199L

        val EPOCH: Instant = Instant(0, 0)
        val MIN: Instant = Instant(MIN_SEKUNDE, 0)
        val MAX: Instant = Instant(MAX_SEKUNDE, 999_999_999)

        private fun erzeuge(sekunden: Long, nanos: Int): Instant {
            if (sekunden == 0L && nanos == 0) return EPOCH
            if (sekunden < MIN_SEKUNDE || sekunden > MAX_SEKUNDE) throw DateTimeException("Instant exceeds minimum or maximum instant")
            return Instant(sekunden, nanos)
        }

        fun ofEpochSecond(epochSecond: Long): Instant = erzeuge(epochSecond, 0)

        fun ofEpochSecond(epochSecond: Long, nanoAdjustment: Long): Instant {
            val s = addExact(epochSecond, floorDiv(nanoAdjustment, NANOS_JE_SEKUNDE))
            return erzeuge(s, floorMod(nanoAdjustment, NANOS_JE_SEKUNDE).toInt())
        }

        fun ofEpochMilli(epochMilli: Long): Instant =
            erzeuge(floorDiv(epochMilli, 1000L), (floorMod(epochMilli, 1000L) * 1_000_000L).toInt())

        /** Jetzt, aus Date.now() (Millisekunden). */
        fun now(): Instant = ofEpochMilli(jsJetztMs().toLong())

        fun from(temporal: TemporalAccessor): Instant = when (temporal) {
            is Instant -> temporal
            else -> try {
                ofEpochSecond(temporal.getLong(ChronoField.INSTANT_SECONDS), temporal.getLong(ChronoField.NANO_OF_SECOND))
            } catch (e: DateTimeException) {
                throw DateTimeException("Unable to obtain Instant from TemporalAccessor: $temporal", e)
            }
        }

        /** ISO-8601 wie DateTimeFormatter.ISO_INSTANT: 2026-10-15T12:00:00Z, auch mit Bruchteilen oder Versatz (+02:00). */
        fun parse(text: CharSequence?): Instant = DateTimeFormatter.ISO_INSTANT.parseZu(text, "Instant") { from(it) }
    }
}
