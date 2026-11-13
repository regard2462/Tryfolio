package java.time

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.time.temporal.Temporal
import java.time.temporal.TemporalAccessor
import java.time.temporal.TemporalAdjuster
import java.time.temporal.TemporalField
import java.time.temporal.nichtUnterstuetzt

/** Ersatz fuer java.time.LocalTime. */
class LocalTime private constructor(
    private val h: Int,
    private val mi: Int,
    private val s: Int,
    private val n: Int,
) : Temporal, TemporalAdjuster, Comparable<LocalTime> {

    val hour: Int get() = h
    val minute: Int get() = mi
    val second: Int get() = s
    val nano: Int get() = n

    fun toSecondOfDay(): Int = h * 3600 + mi * 60 + s
    fun toNanoOfDay(): Long = toSecondOfDay() * NANOS_JE_SEKUNDE + n

    fun plusHours(hoursToAdd: Long): LocalTime {
        if (hoursToAdd == 0L) return this
        val neu = ((hoursToAdd % 24).toInt() + h + 24) % 24
        return of(neu, mi, s, n)
    }

    fun plusMinutes(minutesToAdd: Long): LocalTime {
        if (minutesToAdd == 0L) return this
        val alt = h * 60 + mi
        val neu = ((minutesToAdd % 1440).toInt() + alt + 1440) % 1440
        return if (alt == neu) this else of(neu / 60, neu % 60, s, n)
    }

    fun plusSeconds(secondstoAdd: Long): LocalTime {
        if (secondstoAdd == 0L) return this
        val alt = toSecondOfDay()
        val neu = ((secondstoAdd % SEKUNDEN_JE_TAG).toInt() + alt + SEKUNDEN_JE_TAG.toInt()) % SEKUNDEN_JE_TAG.toInt()
        return if (alt == neu) this else of(neu / 3600, (neu / 60) % 60, neu % 60, n)
    }

    fun plusNanos(nanosToAdd: Long): LocalTime {
        if (nanosToAdd == 0L) return this
        val alt = toNanoOfDay()
        val neu = ((nanosToAdd % NANOS_JE_TAG) + alt + NANOS_JE_TAG) % NANOS_JE_TAG
        return if (alt == neu) this else ofNanoOfDay(neu)
    }

    fun minusHours(hours: Long): LocalTime = plusHours(-(hours % 24))
    fun minusMinutes(minutes: Long): LocalTime = plusMinutes(-(minutes % 1440))
    fun minusSeconds(seconds: Long): LocalTime = plusSeconds(-(seconds % SEKUNDEN_JE_TAG))
    fun minusNanos(nanos: Long): LocalTime = plusNanos(-(nanos % NANOS_JE_TAG))

    fun withHour(hour: Int): LocalTime = of(hour, mi, s, n)
    fun withMinute(minute: Int): LocalTime = of(h, minute, s, n)
    fun withSecond(second: Int): LocalTime = of(h, mi, second, n)
    fun withNano(nanoOfSecond: Int): LocalTime = of(h, mi, s, nanoOfSecond)

    fun atDate(date: LocalDate): LocalDateTime = LocalDateTime.of(date, this)

    override fun adjustInto(temporal: Temporal): Temporal = when (temporal) {
        is LocalTime -> this
        is LocalDateTime -> temporal.with(this)
        is ZonedDateTime -> temporal.with(this)
        is OffsetDateTime -> temporal.with(this)
        else -> throw DateTimeException("Unsupported temporal: $temporal")
    }

    override fun compareTo(other: LocalTime): Int {
        var c = h.compareTo(other.h)
        if (c == 0) {
            c = mi.compareTo(other.mi)
            if (c == 0) {
                c = s.compareTo(other.s)
                if (c == 0) c = n.compareTo(other.n)
            }
        }
        return c
    }

    fun isAfter(other: LocalTime): Boolean = compareTo(other) > 0
    fun isBefore(other: LocalTime): Boolean = compareTo(other) < 0

    override fun equals(other: Any?): Boolean = other is LocalTime && other.h == h && other.mi == mi && other.s == s && other.n == n

    override fun hashCode(): Int {
        val nod = toNanoOfDay()
        return (nod xor (nod ushr 32)).toInt()
    }

    /** Wie Java: HH:mm, Sekunden und Bruchteile nur, wenn vorhanden (Bruch in Dreiergruppen). */
    override fun toString(): String {
        val sb = StringBuilder(18)
        sb.append(stellen(h, 2)).append(':').append(stellen(mi, 2))
        if (s > 0 || n > 0) {
            sb.append(':').append(stellen(s, 2))
            if (n > 0) {
                sb.append('.')
                when {
                    n % 1_000_000 == 0 -> sb.append(stellen(n / 1_000_000, 3))
                    n % 1000 == 0 -> sb.append(stellen(n / 1000, 6))
                    else -> sb.append(stellen(n, 9))
                }
            }
        }
        return sb.toString()
    }

    fun format(formatter: DateTimeFormatter): String = formatter.format(this)

    override fun isSupported(field: TemporalField): Boolean = field is ChronoField && field.isTimeBased()

    override fun getLong(field: TemporalField): Long = zeitFeld(this, field) ?: nichtUnterstuetzt(field)

    companion object {
        private val STUNDEN = Array(24) { LocalTime(it, 0, 0, 0) }

        val MIDNIGHT: LocalTime = STUNDEN[0]
        val MIN: LocalTime = STUNDEN[0]
        val NOON: LocalTime = STUNDEN[12]
        val MAX: LocalTime = LocalTime(23, 59, 59, 999_999_999)

        fun of(hour: Int, minute: Int): LocalTime = of(hour, minute, 0, 0)
        fun of(hour: Int, minute: Int, second: Int): LocalTime = of(hour, minute, second, 0)

        fun of(hour: Int, minute: Int, second: Int, nanoOfSecond: Int): LocalTime {
            ChronoField.HOUR_OF_DAY.checkValidValue(hour.toLong())
            ChronoField.MINUTE_OF_HOUR.checkValidValue(minute.toLong())
            ChronoField.SECOND_OF_MINUTE.checkValidValue(second.toLong())
            ChronoField.NANO_OF_SECOND.checkValidValue(nanoOfSecond.toLong())
            if (minute == 0 && second == 0 && nanoOfSecond == 0) return STUNDEN[hour]
            return LocalTime(hour, minute, second, nanoOfSecond)
        }

        fun ofSecondOfDay(secondOfDay: Long): LocalTime {
            ChronoField.SECOND_OF_DAY.checkValidValue(secondOfDay)
            val sek = secondOfDay.toInt()
            return of(sek / 3600, (sek / 60) % 60, sek % 60, 0)
        }

        fun ofNanoOfDay(nanoOfDay: Long): LocalTime {
            ChronoField.NANO_OF_DAY.checkValidValue(nanoOfDay)
            val sek = nanoOfDay / NANOS_JE_SEKUNDE
            val rest = (nanoOfDay % NANOS_JE_SEKUNDE).toInt()
            return of((sek / 3600).toInt(), ((sek / 60) % 60).toInt(), (sek % 60).toInt(), rest)
        }

        fun now(): LocalTime = now(ZoneId.systemDefault())
        fun now(zone: ZoneId): LocalTime = Instant.now().atZone(zone).toLocalTime()

        fun from(temporal: TemporalAccessor): LocalTime = when (temporal) {
            is LocalTime -> temporal
            is LocalDateTime -> temporal.toLocalTime()
            is ZonedDateTime -> temporal.toLocalTime()
            is OffsetDateTime -> temporal.toLocalTime()
            else -> if (temporal.isSupported(ChronoField.NANO_OF_DAY)) ofNanoOfDay(temporal.getLong(ChronoField.NANO_OF_DAY))
            else throw DateTimeException("Unable to obtain LocalTime from TemporalAccessor: $temporal")
        }

        fun parse(text: CharSequence?): LocalTime = parse(text, DateTimeFormatter.ISO_LOCAL_TIME)
        fun parse(text: CharSequence?, formatter: DateTimeFormatter): LocalTime = formatter.parseZu(text, "LocalTime") { from(it) }
    }
}

/** Zeitfelder fuer LocalTime und die zusammengesetzten Typen. */
internal fun zeitFeld(t: LocalTime, field: TemporalField): Long? = when (field) {
    ChronoField.NANO_OF_SECOND -> t.nano.toLong()
    ChronoField.NANO_OF_DAY -> t.toNanoOfDay()
    ChronoField.MICRO_OF_SECOND -> (t.nano / 1000).toLong()
    ChronoField.MICRO_OF_DAY -> t.toNanoOfDay() / 1000
    ChronoField.MILLI_OF_SECOND -> (t.nano / 1_000_000).toLong()
    ChronoField.MILLI_OF_DAY -> t.toNanoOfDay() / 1_000_000
    ChronoField.SECOND_OF_MINUTE -> t.second.toLong()
    ChronoField.SECOND_OF_DAY -> t.toSecondOfDay().toLong()
    ChronoField.MINUTE_OF_HOUR -> t.minute.toLong()
    ChronoField.MINUTE_OF_DAY -> (t.hour * 60 + t.minute).toLong()
    ChronoField.HOUR_OF_AMPM -> (t.hour % 12).toLong()
    ChronoField.CLOCK_HOUR_OF_AMPM -> (if (t.hour % 12 == 0) 12 else t.hour % 12).toLong()
    ChronoField.HOUR_OF_DAY -> t.hour.toLong()
    ChronoField.CLOCK_HOUR_OF_DAY -> (if (t.hour == 0) 24 else t.hour).toLong()
    ChronoField.AMPM_OF_DAY -> (t.hour / 12).toLong()
    else -> null
}
