package java.time

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.time.temporal.Temporal
import java.time.temporal.TemporalAccessor
import java.time.temporal.TemporalAdjuster
import java.time.temporal.TemporalField
import java.time.temporal.nichtUnterstuetzt

/** Ersatz fuer java.time.LocalDate (ISO-Kalender, proleptisch gregorianisch), Rechenwege wie im JDK. */
class LocalDate private constructor(
    /** Jahr, Monat 1-12, Tag 1-31 */
    private val j: Int,
    private val m: Int,
    private val t: Int,
) : Temporal, TemporalAdjuster, Comparable<LocalDate> {

    val year: Int get() = j
    val monthValue: Int get() = m
    val month: Month get() = Month.of(m)
    val dayOfMonth: Int get() = t
    val dayOfYear: Int get() = month.firstDayOfYear(isLeapYear()) + t - 1
    val dayOfWeek: DayOfWeek get() = DayOfWeek.entries[floorMod(toEpochDay() + 3, 7L).toInt()]
    val era: Int get() = if (j >= 1) 1 else 0

    fun isLeapYear(): Boolean = istSchaltjahr(j.toLong())
    fun lengthOfMonth(): Int = monatsLaenge(m, isLeapYear())
    fun lengthOfYear(): Int = if (isLeapYear()) 366 else 365

    fun toEpochDay(): Long {
        val y = j.toLong()
        val mo = m.toLong()
        var total = 365L * y
        total += if (y >= 0) (y + 3) / 4 - (y + 99) / 100 + (y + 399) / 400 else -(y / -4 - y / -100 + y / -400)
        total += (367 * mo - 362) / 12
        total += t - 1
        if (mo > 2) {
            total--
            if (!isLeapYear()) total--
        }
        return total - TAGE_0000_BIS_1970
    }

    // ------------------------------------------------------------ Rechnen

    fun plusDays(daysToAdd: Long): LocalDate = if (daysToAdd == 0L) this else ofEpochDay(addExact(toEpochDay(), daysToAdd))
    fun minusDays(daysToSubtract: Long): LocalDate = if (daysToSubtract == Long.MIN_VALUE) plusDays(Long.MAX_VALUE).plusDays(1) else plusDays(-daysToSubtract)
    fun plusWeeks(weeksToAdd: Long): LocalDate = plusDays(multiplyExact(weeksToAdd, 7))
    fun minusWeeks(weeksToSubtract: Long): LocalDate = plusDays(multiplyExact(-weeksToSubtract, 7))

    fun plusMonths(monthsToAdd: Long): LocalDate {
        if (monthsToAdd == 0L) return this
        val zaehler = j * 12L + (m - 1)
        val neu = zaehler + monthsToAdd
        val jahr = ChronoField.YEAR.checkValidIntValue(floorDiv(neu, 12L))
        val monat = floorMod(neu, 12L).toInt() + 1
        return gueltigOderVorher(jahr, monat, t)
    }

    fun minusMonths(monthsToSubtract: Long): LocalDate = plusMonths(-monthsToSubtract)

    fun plusYears(yearsToAdd: Long): LocalDate {
        if (yearsToAdd == 0L) return this
        val jahr = ChronoField.YEAR.checkValidIntValue(j + yearsToAdd)
        return gueltigOderVorher(jahr, m, t)
    }

    fun minusYears(yearsToSubtract: Long): LocalDate = plusYears(-yearsToSubtract)

    fun withDayOfMonth(dayOfMonth: Int): LocalDate = if (dayOfMonth == t) this else of(j, m, dayOfMonth)
    fun withMonth(month: Int): LocalDate = if (month == m) this else {
        ChronoField.MONTH_OF_YEAR.checkValidValue(month.toLong())
        gueltigOderVorher(j, month, t)
    }
    fun withYear(year: Int): LocalDate = if (year == j) this else {
        ChronoField.YEAR.checkValidValue(year.toLong())
        gueltigOderVorher(year, m, t)
    }
    fun withDayOfYear(dayOfYear: Int): LocalDate = if (this.dayOfYear == dayOfYear) this else ofYearDay(j, dayOfYear)

    fun with(adjuster: TemporalAdjuster): LocalDate =
        if (adjuster is LocalDate) adjuster else adjuster.adjustInto(this) as LocalDate

    override fun adjustInto(temporal: Temporal): Temporal = when (temporal) {
        is LocalDate -> this
        is LocalDateTime -> temporal.with(this)
        is ZonedDateTime -> temporal.with(this)
        is OffsetDateTime -> temporal.with(this)
        else -> throw DateTimeException("Unsupported temporal: $temporal")
    }

    // ------------------------------------------------------------ Verbinden

    fun atStartOfDay(): LocalDateTime = LocalDateTime.of(this, LocalTime.MIDNIGHT)

    /**
     * Beginn des Tages in [zone]: Mitternacht, oder wenn Mitternacht in eine Luecke der Sommerzeit faellt,
     * der erste Zeitpunkt danach (wie Java).
     */
    fun atStartOfDay(zone: ZoneId): ZonedDateTime {
        val mitternacht = atStartOfDay()
        if (zone !is ZoneOffset) {
            val lokal = mitternacht.toEpochSecond(ZoneOffset.UTC)
            val lage = zone.rules.lage(lokal)
            if (lage.gueltig.isEmpty()) {
                val wechsel = zone.rules.wechselZwischen(lokal - lage.nachher, lokal - lage.vorher, lage.vorher)
                return ZonedDateTime.ofInstant(Instant.ofEpochSecond(wechsel), zone)
            }
        }
        return ZonedDateTime.of(mitternacht, zone)
    }

    fun atTime(time: LocalTime): LocalDateTime = LocalDateTime.of(this, time)
    fun atTime(hour: Int, minute: Int): LocalDateTime = atTime(LocalTime.of(hour, minute))
    fun atTime(hour: Int, minute: Int, second: Int): LocalDateTime = atTime(LocalTime.of(hour, minute, second))
    fun atTime(hour: Int, minute: Int, second: Int, nanoOfSecond: Int): LocalDateTime = atTime(LocalTime.of(hour, minute, second, nanoOfSecond))

    // ------------------------------------------------------------ Vergleichen

    override fun compareTo(other: LocalDate): Int {
        var c = j - other.j
        if (c == 0) {
            c = m - other.m
            if (c == 0) c = t - other.t
        }
        return c
    }

    fun isAfter(other: LocalDate): Boolean = compareTo(other) > 0
    fun isBefore(other: LocalDate): Boolean = compareTo(other) < 0
    fun isEqual(other: LocalDate): Boolean = compareTo(other) == 0

    override fun equals(other: Any?): Boolean = other is LocalDate && other.j == j && other.m == m && other.t == t
    override fun hashCode(): Int = (j and -0x800) xor ((j shl 11) + (m shl 6) + t)

    override fun toString(): String = isoJahr(j, true) + "-" + stellen(m, 2) + "-" + stellen(t, 2)

    fun format(formatter: DateTimeFormatter): String = formatter.format(this)

    // ------------------------------------------------------------ Felder

    override fun isSupported(field: TemporalField): Boolean = field is ChronoField && field.isDateBased()

    override fun getLong(field: TemporalField): Long = when (field) {
        ChronoField.DAY_OF_WEEK -> dayOfWeek.value.toLong()
        ChronoField.DAY_OF_MONTH -> t.toLong()
        ChronoField.DAY_OF_YEAR -> dayOfYear.toLong()
        ChronoField.EPOCH_DAY -> toEpochDay()
        ChronoField.MONTH_OF_YEAR -> m.toLong()
        ChronoField.PROLEPTIC_MONTH -> j * 12L + m - 1
        ChronoField.YEAR_OF_ERA -> (if (j >= 1) j else 1 - j).toLong()
        ChronoField.YEAR -> j.toLong()
        ChronoField.ERA -> era.toLong()
        else -> nichtUnterstuetzt(field)
    }

    companion object {
        val MIN: LocalDate = LocalDate(-999_999_999, 1, 1)
        val MAX: LocalDate = LocalDate(999_999_999, 12, 31)
        val EPOCH: LocalDate = LocalDate(1970, 1, 1)

        fun of(year: Int, month: Int, dayOfMonth: Int): LocalDate {
            ChronoField.YEAR.checkValidValue(year.toLong())
            ChronoField.MONTH_OF_YEAR.checkValidValue(month.toLong())
            ChronoField.DAY_OF_MONTH.checkValidValue(dayOfMonth.toLong())
            if (dayOfMonth > 28) {
                val laenge = monatsLaenge(month, istSchaltjahr(year.toLong()))
                if (dayOfMonth > laenge) {
                    if (dayOfMonth == 29) throw DateTimeException("Invalid date 'February 29' as '$year' is not a leap year")
                    throw DateTimeException("Invalid date '${Month.of(month).name} $dayOfMonth'")
                }
            }
            return LocalDate(year, month, dayOfMonth)
        }

        fun of(year: Int, month: Month, dayOfMonth: Int): LocalDate = of(year, month.value, dayOfMonth)

        fun ofYearDay(year: Int, dayOfYear: Int): LocalDate {
            ChronoField.YEAR.checkValidValue(year.toLong())
            ChronoField.DAY_OF_YEAR.checkValidValue(dayOfYear.toLong())
            val schalt = istSchaltjahr(year.toLong())
            if (dayOfYear == 366 && !schalt) throw DateTimeException("Invalid date 'DayOfYear 366' as '$year' is not a leap year")
            var monat = Month.of((dayOfYear - 1) / 31 + 1)
            val ende = monat.firstDayOfYear(schalt) + monat.length(schalt) - 1
            if (dayOfYear > ende) monat = monat.plus(1)
            val tag = dayOfYear - monat.firstDayOfYear(schalt) + 1
            return LocalDate(year, monat.value, tag)
        }

        fun ofEpochDay(epochDay: Long): LocalDate {
            ChronoField.EPOCH_DAY.checkValidValue(epochDay)
            var zeroDay = epochDay + TAGE_0000_BIS_1970
            zeroDay -= 60
            var adjust = 0L
            if (zeroDay < 0) {
                val adjustCycles = (zeroDay + 1) / 146_097L - 1
                adjust = adjustCycles * 400
                zeroDay += -adjustCycles * 146_097L
            }
            var yearEst = (400 * zeroDay + 591) / 146_097L
            var doyEst = zeroDay - (365 * yearEst + yearEst / 4 - yearEst / 100 + yearEst / 400)
            if (doyEst < 0) {
                yearEst--
                doyEst = zeroDay - (365 * yearEst + yearEst / 4 - yearEst / 100 + yearEst / 400)
            }
            yearEst += adjust
            val marchDoy0 = doyEst.toInt()
            val marchMonth0 = (marchDoy0 * 5 + 2) / 153
            val month = (marchMonth0 + 2) % 12 + 1
            val dom = marchDoy0 - (marchMonth0 * 306 + 5) / 10 + 1
            yearEst += marchMonth0 / 10
            return LocalDate(ChronoField.YEAR.checkValidIntValue(yearEst), month, dom)
        }

        /** Tag im Monat auf den letzten gueltigen Tag begrenzt (wie plusMonths in Java). */
        internal fun gueltigOderVorher(year: Int, month: Int, day: Int): LocalDate {
            val tag = minOf(day, monatsLaenge(month, istSchaltjahr(year.toLong())))
            return LocalDate(year, month, tag)
        }

        fun now(): LocalDate = now(ZoneId.systemDefault())

        fun now(zone: ZoneId): LocalDate = Instant.now().atZone(zone).toLocalDate()

        fun ofInstant(instant: Instant, zone: ZoneId): LocalDate = instant.atZone(zone).toLocalDate()

        fun from(temporal: TemporalAccessor): LocalDate = when (temporal) {
            is LocalDate -> temporal
            is LocalDateTime -> temporal.toLocalDate()
            is ZonedDateTime -> temporal.toLocalDate()
            is OffsetDateTime -> temporal.toLocalDate()
            else -> if (temporal.isSupported(ChronoField.EPOCH_DAY)) ofEpochDay(temporal.getLong(ChronoField.EPOCH_DAY))
            else throw DateTimeException("Unable to obtain LocalDate from TemporalAccessor: $temporal")
        }

        fun parse(text: CharSequence?): LocalDate = parse(text, DateTimeFormatter.ISO_LOCAL_DATE)

        fun parse(text: CharSequence?, formatter: DateTimeFormatter): LocalDate = formatter.parseZu(text, "LocalDate") { from(it) }
    }
}
