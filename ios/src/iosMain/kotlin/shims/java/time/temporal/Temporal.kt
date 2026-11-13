package java.time.temporal

import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime

/* Ersatz fuer java.time.temporal: Felder, Zugriff auf Felder und die Anpasser aus TemporalAdjusters. */

open class UnsupportedTemporalTypeException(message: String?, cause: Throwable? = null) : DateTimeException(message, cause)

interface TemporalField {
    fun isDateBased(): Boolean
    fun isTimeBased(): Boolean
}

enum class ChronoField(private val anzeige: String, val minimum: Long, val maximum: Long, private val datum: Boolean, private val zeit: Boolean) : TemporalField {
    NANO_OF_SECOND("NanoOfSecond", 0, 999_999_999, false, true),
    NANO_OF_DAY("NanoOfDay", 0, 86_400L * 1_000_000_000L - 1, false, true),
    MICRO_OF_SECOND("MicroOfSecond", 0, 999_999, false, true),
    MICRO_OF_DAY("MicroOfDay", 0, 86_400L * 1_000_000L - 1, false, true),
    MILLI_OF_SECOND("MilliOfSecond", 0, 999, false, true),
    MILLI_OF_DAY("MilliOfDay", 0, 86_400L * 1000L - 1, false, true),
    SECOND_OF_MINUTE("SecondOfMinute", 0, 59, false, true),
    SECOND_OF_DAY("SecondOfDay", 0, 86_400L - 1, false, true),
    MINUTE_OF_HOUR("MinuteOfHour", 0, 59, false, true),
    MINUTE_OF_DAY("MinuteOfDay", 0, 24 * 60 - 1, false, true),
    HOUR_OF_AMPM("HourOfAmPm", 0, 11, false, true),
    CLOCK_HOUR_OF_AMPM("ClockHourOfAmPm", 1, 12, false, true),
    HOUR_OF_DAY("HourOfDay", 0, 23, false, true),
    CLOCK_HOUR_OF_DAY("ClockHourOfDay", 1, 24, false, true),
    AMPM_OF_DAY("AmPmOfDay", 0, 1, false, true),
    DAY_OF_WEEK("DayOfWeek", 1, 7, true, false),
    DAY_OF_MONTH("DayOfMonth", 1, 31, true, false),
    DAY_OF_YEAR("DayOfYear", 1, 366, true, false),
    EPOCH_DAY("EpochDay", -365_243_219_162L, 365_241_780_471L, true, false),
    MONTH_OF_YEAR("MonthOfYear", 1, 12, true, false),
    PROLEPTIC_MONTH("ProlepticMonth", -999_999_999L * 12, 999_999_999L * 12 + 11, true, false),
    YEAR_OF_ERA("YearOfEra", 1, 1_000_000_000L, true, false),
    YEAR("Year", -999_999_999L, 999_999_999L, true, false),
    ERA("Era", 0, 1, true, false),
    INSTANT_SECONDS("InstantSeconds", Long.MIN_VALUE, Long.MAX_VALUE, false, false),
    OFFSET_SECONDS("OffsetSeconds", -18 * 3600L, 18 * 3600L, false, false);

    override fun isDateBased(): Boolean = datum
    override fun isTimeBased(): Boolean = zeit

    fun isValidValue(value: Long): Boolean = value in minimum..maximum

    fun checkValidValue(value: Long): Long {
        if (!isValidValue(value)) throw DateTimeException("Invalid value for $anzeige (valid values $minimum - $maximum): $value")
        return value
    }

    fun checkValidIntValue(value: Long): Int = checkValidValue(value).toInt()

    override fun toString(): String = anzeige
}

interface TemporalAccessor {
    fun isSupported(field: TemporalField): Boolean
    fun getLong(field: TemporalField): Long
    fun get(field: TemporalField): Int {
        val v = getLong(field)
        if (v < Int.MIN_VALUE || v > Int.MAX_VALUE) throw UnsupportedTemporalTypeException("Invalid field $field for get() method, use getLong() instead")
        return v.toInt()
    }
}

interface Temporal : TemporalAccessor

fun interface TemporalAdjuster {
    fun adjustInto(temporal: Temporal): Temporal
}

/** Feld nicht vorhanden: wie Java eine UnsupportedTemporalTypeException. */
internal fun nichtUnterstuetzt(field: TemporalField): Nothing = throw UnsupportedTemporalTypeException("Unsupported field: $field")

object TemporalAdjusters {

    /** Anpasser fuer Datumswerte: wendet [f] auf das Datum an und behaelt Uhrzeit und Zone. */
    private fun datum(f: (LocalDate) -> LocalDate): TemporalAdjuster = TemporalAdjuster { t ->
        when (t) {
            is LocalDate -> f(t)
            is LocalDateTime -> t.with(f(t.toLocalDate()))
            is ZonedDateTime -> t.with(f(t.toLocalDate()))
            is OffsetDateTime -> t.with(f(t.toLocalDate()))
            else -> throw DateTimeException("Unsupported temporal: $t")
        }
    }

    fun ofDateAdjuster(dateBasedAdjuster: (LocalDate) -> LocalDate): TemporalAdjuster = datum(dateBasedAdjuster)

    fun firstDayOfMonth(): TemporalAdjuster = datum { it.withDayOfMonth(1) }
    fun lastDayOfMonth(): TemporalAdjuster = datum { it.withDayOfMonth(it.lengthOfMonth()) }
    fun firstDayOfNextMonth(): TemporalAdjuster = datum { it.withDayOfMonth(1).plusMonths(1) }
    fun firstDayOfYear(): TemporalAdjuster = datum { it.withDayOfYear(1) }
    fun lastDayOfYear(): TemporalAdjuster = datum { it.withDayOfYear(it.lengthOfYear()) }
    fun firstDayOfNextYear(): TemporalAdjuster = datum { LocalDate.of(it.year + 1, 1, 1) }

    fun firstInMonth(dayOfWeek: DayOfWeek): TemporalAdjuster = dayOfWeekInMonth(1, dayOfWeek)
    fun lastInMonth(dayOfWeek: DayOfWeek): TemporalAdjuster = dayOfWeekInMonth(-1, dayOfWeek)

    /** Wie Java: ordinal >= 0 zaehlt vom Monatsanfang (0 = letzter im Vormonat), < 0 vom Monatsende. */
    fun dayOfWeekInMonth(ordinal: Int, dayOfWeek: DayOfWeek): TemporalAdjuster {
        val dow = dayOfWeek.value
        return if (ordinal >= 0) datum { d ->
            val erster = d.withDayOfMonth(1)
            val diff = ((dow - erster.dayOfWeek.value) + 7) % 7
            erster.plusDays(diff.toLong() + (ordinal - 1L) * 7L)
        } else datum { d ->
            val letzter = d.withDayOfMonth(d.lengthOfMonth())
            var diff = dow - letzter.dayOfWeek.value
            diff = if (diff == 0) 0 else if (diff > 0) diff - 7 else diff
            letzter.plusDays(diff.toLong() - (-ordinal - 1L) * 7L)
        }
    }

    fun next(dayOfWeek: DayOfWeek): TemporalAdjuster = datum { d ->
        val diff = d.dayOfWeek.value - dayOfWeek.value
        d.plusDays((if (diff >= 0) 7 - diff else -diff).toLong())
    }

    fun nextOrSame(dayOfWeek: DayOfWeek): TemporalAdjuster = datum { d ->
        val diff = d.dayOfWeek.value - dayOfWeek.value
        if (diff == 0) d else d.plusDays((if (diff >= 0) 7 - diff else -diff).toLong())
    }

    fun previous(dayOfWeek: DayOfWeek): TemporalAdjuster = datum { d ->
        val diff = dayOfWeek.value - d.dayOfWeek.value
        d.minusDays((if (diff >= 0) 7 - diff else -diff).toLong())
    }

    fun previousOrSame(dayOfWeek: DayOfWeek): TemporalAdjuster = datum { d ->
        val diff = dayOfWeek.value - d.dayOfWeek.value
        if (diff == 0) d else d.minusDays((if (diff >= 0) 7 - diff else -diff).toLong())
    }
}
