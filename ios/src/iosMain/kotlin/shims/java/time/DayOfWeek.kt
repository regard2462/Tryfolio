package java.time

import java.time.format.Namen
import java.time.format.TextStyle
import java.time.temporal.ChronoField
import java.time.temporal.TemporalAccessor
import java.time.temporal.TemporalField
import java.time.temporal.nichtUnterstuetzt
import java.util.Locale

enum class DayOfWeek : TemporalAccessor {
    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY;

    /** 1 (Montag) bis 7 (Sonntag). */
    val value: Int get() = ordinal + 1

    fun getDisplayName(style: TextStyle, locale: Locale): String = Namen.wochentag(value, style, locale)

    fun plus(days: Long): DayOfWeek = entries[floorMod(ordinal + (days % 7).toInt() + 7, 7)]
    fun minus(days: Long): DayOfWeek = plus(-(days % 7))

    override fun isSupported(field: TemporalField): Boolean = field == ChronoField.DAY_OF_WEEK
    override fun getLong(field: TemporalField): Long = if (field == ChronoField.DAY_OF_WEEK) value.toLong() else nichtUnterstuetzt(field)

    companion object {
        fun of(dayOfWeek: Int): DayOfWeek {
            if (dayOfWeek < 1 || dayOfWeek > 7) throw DateTimeException("Invalid value for DayOfWeek: $dayOfWeek")
            return entries[dayOfWeek - 1]
        }

        fun from(temporal: TemporalAccessor): DayOfWeek =
            if (temporal is DayOfWeek) temporal else of(temporal.get(ChronoField.DAY_OF_WEEK))
    }
}

enum class Month : TemporalAccessor {
    JANUARY, FEBRUARY, MARCH, APRIL, MAY, JUNE, JULY, AUGUST, SEPTEMBER, OCTOBER, NOVEMBER, DECEMBER;

    /** 1 (Januar) bis 12 (Dezember). */
    val value: Int get() = ordinal + 1

    fun getDisplayName(style: TextStyle, locale: Locale): String = Namen.monat(value, style, locale)

    fun plus(months: Long): Month = entries[floorMod(ordinal + (months % 12).toInt() + 12, 12)]
    fun minus(months: Long): Month = plus(-(months % 12))

    fun length(leapYear: Boolean): Int = monatsLaenge(value, leapYear)
    fun minLength(): Int = monatsLaenge(value, false)
    fun maxLength(): Int = monatsLaenge(value, true)

    /** Tag im Jahr, an dem der Monat beginnt. */
    fun firstDayOfYear(leapYear: Boolean): Int {
        val schalt = if (leapYear) 1 else 0
        return when (this) {
            JANUARY -> 1
            FEBRUARY -> 32
            MARCH -> 60 + schalt
            APRIL -> 91 + schalt
            MAY -> 121 + schalt
            JUNE -> 152 + schalt
            JULY -> 182 + schalt
            AUGUST -> 213 + schalt
            SEPTEMBER -> 244 + schalt
            OCTOBER -> 274 + schalt
            NOVEMBER -> 305 + schalt
            DECEMBER -> 335 + schalt
        }
    }

    fun firstMonthOfQuarter(): Month = entries[(ordinal / 3) * 3]

    override fun isSupported(field: TemporalField): Boolean = field == ChronoField.MONTH_OF_YEAR
    override fun getLong(field: TemporalField): Long = if (field == ChronoField.MONTH_OF_YEAR) value.toLong() else nichtUnterstuetzt(field)

    companion object {
        fun of(month: Int): Month {
            if (month < 1 || month > 12) throw DateTimeException("Invalid value for MonthOfYear: $month")
            return entries[month - 1]
        }

        fun from(temporal: TemporalAccessor): Month =
            if (temporal is Month) temporal else of(temporal.get(ChronoField.MONTH_OF_YEAR))
    }
}
