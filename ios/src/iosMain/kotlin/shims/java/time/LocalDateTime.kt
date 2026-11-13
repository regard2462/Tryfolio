package java.time

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.time.temporal.Temporal
import java.time.temporal.TemporalAccessor
import java.time.temporal.TemporalAdjuster
import java.time.temporal.TemporalField
import java.time.temporal.nichtUnterstuetzt

/** Ersatz fuer java.time.LocalDateTime. */
class LocalDateTime private constructor(private val datum: LocalDate, private val zeit: LocalTime) :
    Temporal, TemporalAdjuster, Comparable<LocalDateTime> {

    fun toLocalDate(): LocalDate = datum
    fun toLocalTime(): LocalTime = zeit

    val year: Int get() = datum.year
    val monthValue: Int get() = datum.monthValue
    val month: Month get() = datum.month
    val dayOfMonth: Int get() = datum.dayOfMonth
    val dayOfYear: Int get() = datum.dayOfYear
    val dayOfWeek: DayOfWeek get() = datum.dayOfWeek
    val hour: Int get() = zeit.hour
    val minute: Int get() = zeit.minute
    val second: Int get() = zeit.second
    val nano: Int get() = zeit.nano

    fun atZone(zone: ZoneId): ZonedDateTime = ZonedDateTime.of(this, zone)
    fun atOffset(offset: ZoneOffset): OffsetDateTime = OffsetDateTime.of(this, offset)

    fun toEpochSecond(offset: ZoneOffset): Long = datum.toEpochDay() * SEKUNDEN_JE_TAG + zeit.toSecondOfDay() - offset.totalSeconds
    fun toInstant(offset: ZoneOffset): Instant = Instant.ofEpochSecond(toEpochSecond(offset), zeit.nano.toLong())

    private fun mit(d: LocalDate, t: LocalTime): LocalDateTime = if (d === datum && t === zeit) this else LocalDateTime(d, t)

    fun with(adjuster: TemporalAdjuster): LocalDateTime = when (adjuster) {
        is LocalDate -> mit(adjuster, zeit)
        is LocalTime -> mit(datum, adjuster)
        is LocalDateTime -> adjuster
        else -> adjuster.adjustInto(this) as LocalDateTime
    }

    override fun adjustInto(temporal: Temporal): Temporal = when (temporal) {
        is LocalDateTime -> this
        is ZonedDateTime -> temporal.with(toLocalDate()).with(toLocalTime())
        is OffsetDateTime -> temporal.with(toLocalDate()).with(toLocalTime())
        else -> throw DateTimeException("Unsupported temporal: $temporal")
    }

    fun withYear(year: Int): LocalDateTime = mit(datum.withYear(year), zeit)
    fun withMonth(month: Int): LocalDateTime = mit(datum.withMonth(month), zeit)
    fun withDayOfMonth(dayOfMonth: Int): LocalDateTime = mit(datum.withDayOfMonth(dayOfMonth), zeit)
    fun withDayOfYear(dayOfYear: Int): LocalDateTime = mit(datum.withDayOfYear(dayOfYear), zeit)
    fun withHour(hour: Int): LocalDateTime = mit(datum, zeit.withHour(hour))
    fun withMinute(minute: Int): LocalDateTime = mit(datum, zeit.withMinute(minute))
    fun withSecond(second: Int): LocalDateTime = mit(datum, zeit.withSecond(second))
    fun withNano(nanoOfSecond: Int): LocalDateTime = mit(datum, zeit.withNano(nanoOfSecond))

    fun plusYears(years: Long): LocalDateTime = mit(datum.plusYears(years), zeit)
    fun plusMonths(months: Long): LocalDateTime = mit(datum.plusMonths(months), zeit)
    fun plusWeeks(weeks: Long): LocalDateTime = mit(datum.plusWeeks(weeks), zeit)
    fun plusDays(days: Long): LocalDateTime = mit(datum.plusDays(days), zeit)
    fun plusHours(hours: Long): LocalDateTime = plusNanosGesamt(multiplyExact(hours, 3600L * NANOS_JE_SEKUNDE))
    fun plusMinutes(minutes: Long): LocalDateTime = plusNanosGesamt(multiplyExact(minutes, 60L * NANOS_JE_SEKUNDE))
    fun plusSeconds(seconds: Long): LocalDateTime = plusSekunden(seconds, 0)
    fun plusNanos(nanos: Long): LocalDateTime = plusNanosGesamt(nanos)

    fun minusYears(years: Long): LocalDateTime = plusYears(-years)
    fun minusMonths(months: Long): LocalDateTime = plusMonths(-months)
    fun minusWeeks(weeks: Long): LocalDateTime = plusWeeks(-weeks)
    fun minusDays(days: Long): LocalDateTime = plusDays(-days)
    fun minusHours(hours: Long): LocalDateTime = plusHours(-hours)
    fun minusMinutes(minutes: Long): LocalDateTime = plusMinutes(-minutes)
    fun minusSeconds(seconds: Long): LocalDateTime = plusSeconds(-seconds)
    fun minusNanos(nanos: Long): LocalDateTime = plusNanos(-nanos)

    private fun plusNanosGesamt(nanos: Long): LocalDateTime {
        if (nanos == 0L) return this
        return plusSekunden(floorDiv(nanos, NANOS_JE_SEKUNDE), floorMod(nanos, NANOS_JE_SEKUNDE))
    }

    private fun plusSekunden(sekunden: Long, nanos: Long): LocalDateTime {
        if (sekunden == 0L && nanos == 0L) return this
        val tage = floorDiv(sekunden, SEKUNDEN_JE_TAG)
        val restSek = floorMod(sekunden, SEKUNDEN_JE_TAG)
        var nod = zeit.toNanoOfDay() + restSek * NANOS_JE_SEKUNDE + nanos
        val extraTage = floorDiv(nod, NANOS_JE_TAG)
        nod = floorMod(nod, NANOS_JE_TAG)
        val t = if (nod == zeit.toNanoOfDay()) zeit else LocalTime.ofNanoOfDay(nod)
        return mit(datum.plusDays(tage + extraTage), t)
    }

    override fun compareTo(other: LocalDateTime): Int {
        val c = datum.compareTo(other.datum)
        return if (c != 0) c else zeit.compareTo(other.zeit)
    }

    fun isAfter(other: LocalDateTime): Boolean = compareTo(other) > 0
    fun isBefore(other: LocalDateTime): Boolean = compareTo(other) < 0
    fun isEqual(other: LocalDateTime): Boolean = compareTo(other) == 0

    override fun equals(other: Any?): Boolean = other is LocalDateTime && other.datum == datum && other.zeit == zeit
    override fun hashCode(): Int = datum.hashCode() xor zeit.hashCode()
    override fun toString(): String = "${datum}T$zeit"

    fun format(formatter: DateTimeFormatter): String = formatter.format(this)

    override fun isSupported(field: TemporalField): Boolean = field is ChronoField && (field.isDateBased() || field.isTimeBased())

    override fun getLong(field: TemporalField): Long = when {
        field is ChronoField && field.isTimeBased() -> zeitFeld(zeit, field) ?: nichtUnterstuetzt(field)
        field is ChronoField && field.isDateBased() -> datum.getLong(field)
        else -> nichtUnterstuetzt(field)
    }

    companion object {
        val MIN: LocalDateTime = LocalDateTime(LocalDate.MIN, LocalTime.MIN)
        val MAX: LocalDateTime = LocalDateTime(LocalDate.MAX, LocalTime.MAX)

        fun of(date: LocalDate, time: LocalTime): LocalDateTime = LocalDateTime(date, time)
        fun of(year: Int, month: Int, dayOfMonth: Int, hour: Int, minute: Int): LocalDateTime =
            LocalDateTime(LocalDate.of(year, month, dayOfMonth), LocalTime.of(hour, minute))
        fun of(year: Int, month: Int, dayOfMonth: Int, hour: Int, minute: Int, second: Int): LocalDateTime =
            LocalDateTime(LocalDate.of(year, month, dayOfMonth), LocalTime.of(hour, minute, second))
        fun of(year: Int, month: Int, dayOfMonth: Int, hour: Int, minute: Int, second: Int, nanoOfSecond: Int): LocalDateTime =
            LocalDateTime(LocalDate.of(year, month, dayOfMonth), LocalTime.of(hour, minute, second, nanoOfSecond))
        fun of(year: Int, month: Month, dayOfMonth: Int, hour: Int, minute: Int): LocalDateTime = of(year, month.value, dayOfMonth, hour, minute)
        fun of(year: Int, month: Month, dayOfMonth: Int, hour: Int, minute: Int, second: Int): LocalDateTime = of(year, month.value, dayOfMonth, hour, minute, second)

        fun ofEpochSecond(epochSecond: Long, nanoOfSecond: Int, offset: ZoneOffset): LocalDateTime {
            ChronoField.NANO_OF_SECOND.checkValidValue(nanoOfSecond.toLong())
            val lokal = epochSecond + offset.totalSeconds
            val tag = floorDiv(lokal, SEKUNDEN_JE_TAG)
            val sek = floorMod(lokal, SEKUNDEN_JE_TAG)
            return LocalDateTime(LocalDate.ofEpochDay(tag), LocalTime.ofNanoOfDay(sek * NANOS_JE_SEKUNDE + nanoOfSecond))
        }

        fun ofInstant(instant: Instant, zone: ZoneId): LocalDateTime {
            val offset = zone.rules.getOffset(instant)
            return ofEpochSecond(instant.epochSecond, instant.nano, offset)
        }

        fun now(): LocalDateTime = now(ZoneId.systemDefault())
        fun now(zone: ZoneId): LocalDateTime = ofInstant(Instant.now(), zone)

        fun from(temporal: TemporalAccessor): LocalDateTime = when (temporal) {
            is LocalDateTime -> temporal
            is ZonedDateTime -> temporal.toLocalDateTime()
            is OffsetDateTime -> temporal.toLocalDateTime()
            else -> try {
                LocalDateTime(LocalDate.from(temporal), LocalTime.from(temporal))
            } catch (e: DateTimeException) {
                throw DateTimeException("Unable to obtain LocalDateTime from TemporalAccessor: $temporal", e)
            }
        }

        fun parse(text: CharSequence?): LocalDateTime = parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        fun parse(text: CharSequence?, formatter: DateTimeFormatter): LocalDateTime = formatter.parseZu(text, "LocalDateTime") { from(it) }
    }
}
