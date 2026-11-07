package java.time

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.time.temporal.Temporal
import java.time.temporal.TemporalAccessor
import java.time.temporal.TemporalAdjuster
import java.time.temporal.TemporalField
import java.time.temporal.nichtUnterstuetzt

/** Ersatz fuer java.time.YearMonth. */
class YearMonth private constructor(private val j: Int, private val m: Int) : Temporal, TemporalAdjuster, Comparable<YearMonth> {

    val year: Int get() = j
    val monthValue: Int get() = m
    val month: Month get() = Month.of(m)

    fun isLeapYear(): Boolean = istSchaltjahr(j.toLong())
    fun lengthOfMonth(): Int = monatsLaenge(m, isLeapYear())
    fun lengthOfYear(): Int = if (isLeapYear()) 366 else 365
    fun isValidDay(dayOfMonth: Int): Boolean = dayOfMonth in 1..lengthOfMonth()

    fun atDay(dayOfMonth: Int): LocalDate = LocalDate.of(j, m, dayOfMonth)
    fun atEndOfMonth(): LocalDate = LocalDate.of(j, m, lengthOfMonth())

    private fun mit(jahr: Int, monat: Int): YearMonth = if (jahr == j && monat == m) this else YearMonth(jahr, monat)

    fun plusMonths(monthsToAdd: Long): YearMonth {
        if (monthsToAdd == 0L) return this
        val neu = j * 12L + (m - 1) + monthsToAdd
        val jahr = ChronoField.YEAR.checkValidIntValue(floorDiv(neu, 12L))
        return mit(jahr, floorMod(neu, 12L).toInt() + 1)
    }

    fun minusMonths(monthsToSubtract: Long): YearMonth = plusMonths(-monthsToSubtract)

    fun plusYears(yearsToAdd: Long): YearMonth =
        if (yearsToAdd == 0L) this else mit(ChronoField.YEAR.checkValidIntValue(j + yearsToAdd), m)

    fun minusYears(yearsToSubtract: Long): YearMonth = plusYears(-yearsToSubtract)

    fun withMonth(month: Int): YearMonth {
        ChronoField.MONTH_OF_YEAR.checkValidValue(month.toLong())
        return mit(j, month)
    }

    fun withYear(year: Int): YearMonth {
        ChronoField.YEAR.checkValidValue(year.toLong())
        return mit(year, m)
    }

    fun with(adjuster: TemporalAdjuster): YearMonth = adjuster.adjustInto(this) as YearMonth

    override fun adjustInto(temporal: Temporal): Temporal = when (temporal) {
        is YearMonth -> this
        is LocalDate -> LocalDate.gueltigOderVorher(j, m, temporal.dayOfMonth)
        is LocalDateTime -> temporal.with(LocalDate.gueltigOderVorher(j, m, temporal.dayOfMonth))
        is ZonedDateTime -> temporal.with(LocalDate.gueltigOderVorher(j, m, temporal.dayOfMonth))
        else -> throw DateTimeException("Unsupported temporal: $temporal")
    }

    override fun compareTo(other: YearMonth): Int {
        val c = j - other.j
        return if (c != 0) c else m - other.m
    }

    fun isAfter(other: YearMonth): Boolean = compareTo(other) > 0
    fun isBefore(other: YearMonth): Boolean = compareTo(other) < 0

    override fun equals(other: Any?): Boolean = other is YearMonth && other.j == j && other.m == m
    override fun hashCode(): Int = j xor (m shl 27)

    /** Wie Java: 2026-10, Jahre unter 1000 vierstellig, ohne Plus ab 10000. */
    override fun toString(): String = isoJahr(j, false) + "-" + stellen(m, 2)

    fun format(formatter: DateTimeFormatter): String = formatter.format(this)

    override fun isSupported(field: TemporalField): Boolean =
        field == ChronoField.YEAR || field == ChronoField.MONTH_OF_YEAR || field == ChronoField.PROLEPTIC_MONTH ||
            field == ChronoField.YEAR_OF_ERA || field == ChronoField.ERA

    override fun getLong(field: TemporalField): Long = when (field) {
        ChronoField.MONTH_OF_YEAR -> m.toLong()
        ChronoField.PROLEPTIC_MONTH -> j * 12L + m - 1
        ChronoField.YEAR_OF_ERA -> (if (j < 1) 1 - j else j).toLong()
        ChronoField.YEAR -> j.toLong()
        ChronoField.ERA -> (if (j < 1) 0 else 1).toLong()
        else -> nichtUnterstuetzt(field)
    }

    companion object {
        fun of(year: Int, month: Int): YearMonth {
            ChronoField.YEAR.checkValidValue(year.toLong())
            ChronoField.MONTH_OF_YEAR.checkValidValue(month.toLong())
            return YearMonth(year, month)
        }

        fun of(year: Int, month: Month): YearMonth = of(year, month.value)

        fun now(): YearMonth = now(ZoneId.systemDefault())
        fun now(zone: ZoneId): YearMonth = from(LocalDate.now(zone))

        fun from(temporal: TemporalAccessor): YearMonth {
            if (temporal is YearMonth) return temporal
            try {
                return of(temporal.get(ChronoField.YEAR), temporal.get(ChronoField.MONTH_OF_YEAR))
            } catch (e: DateTimeException) {
                throw DateTimeException("Unable to obtain YearMonth from TemporalAccessor: $temporal", e)
            }
        }

        /** "2026-10" (wie Java: Jahr mit vier Stellen, laengere mit Vorzeichen). */
        fun parse(text: CharSequence?): YearMonth = parse(text, DateTimeFormatter.ISO_YEAR_MONTH)
        fun parse(text: CharSequence?, formatter: DateTimeFormatter): YearMonth = formatter.parseZu(text, "YearMonth") { from(it) }
    }
}

/** Ersatz fuer java.time.Year. */
class Year private constructor(private val j: Int) : Temporal, TemporalAdjuster, Comparable<Year> {

    val value: Int get() = j

    fun isLeap(): Boolean = istSchaltjahr(j.toLong())
    fun length(): Int = if (isLeap()) 366 else 365

    fun atDay(dayOfYear: Int): LocalDate = LocalDate.ofYearDay(j, dayOfYear)
    fun atMonth(month: Int): YearMonth = YearMonth.of(j, month)
    fun atMonth(month: Month): YearMonth = YearMonth.of(j, month)

    fun plusYears(yearsToAdd: Long): Year = if (yearsToAdd == 0L) this else of(ChronoField.YEAR.checkValidIntValue(j + yearsToAdd))
    fun minusYears(yearsToSubtract: Long): Year = plusYears(-yearsToSubtract)

    override fun adjustInto(temporal: Temporal): Temporal = when (temporal) {
        is Year -> this
        is LocalDate -> temporal.withYear(j)
        is LocalDateTime -> temporal.withYear(j)
        is ZonedDateTime -> temporal.withYear(j)
        is YearMonth -> temporal.withYear(j)
        else -> throw DateTimeException("Unsupported temporal: $temporal")
    }

    override fun compareTo(other: Year): Int = j - other.j
    fun isAfter(other: Year): Boolean = j > other.j
    fun isBefore(other: Year): Boolean = j < other.j

    override fun equals(other: Any?): Boolean = other is Year && other.j == j
    override fun hashCode(): Int = j
    override fun toString(): String = j.toString()

    fun format(formatter: DateTimeFormatter): String = formatter.format(this)

    override fun isSupported(field: TemporalField): Boolean = field == ChronoField.YEAR || field == ChronoField.YEAR_OF_ERA || field == ChronoField.ERA

    override fun getLong(field: TemporalField): Long = when (field) {
        ChronoField.YEAR_OF_ERA -> (if (j < 1) 1 - j else j).toLong()
        ChronoField.YEAR -> j.toLong()
        ChronoField.ERA -> (if (j < 1) 0 else 1).toLong()
        else -> nichtUnterstuetzt(field)
    }

    companion object {
        const val MIN_VALUE: Int = -999_999_999
        const val MAX_VALUE: Int = 999_999_999

        fun of(isoYear: Int): Year {
            ChronoField.YEAR.checkValidValue(isoYear.toLong())
            return Year(isoYear)
        }

        fun isLeap(year: Long): Boolean = istSchaltjahr(year)

        fun now(): Year = now(ZoneId.systemDefault())
        fun now(zone: ZoneId): Year = of(LocalDate.now(zone).year)

        fun from(temporal: TemporalAccessor): Year {
            if (temporal is Year) return temporal
            try {
                return of(temporal.get(ChronoField.YEAR))
            } catch (e: DateTimeException) {
                throw DateTimeException("Unable to obtain Year from TemporalAccessor: $temporal", e)
            }
        }

        /** Wie Java: mindestens vier Ziffern, mehr als vier nur mit Vorzeichen. */
        fun parse(text: CharSequence?): Year {
            if (text == null) throw NullPointerException("text")
            val s = text.toString()
            val m = Regex("([+-]?)([0-9]{4,10})").matchEntire(s)
            if (m == null || (m.groupValues[2].length > 4 && m.groupValues[1].isEmpty()) || (m.groupValues[1] == "+" && m.groupValues[2].length <= 4))
                throw java.time.format.DateTimeParseException("Text '$s' could not be parsed at index 0", s, 0)
            return of(ChronoField.YEAR.checkValidIntValue(s.toLong()))
        }
    }
}
