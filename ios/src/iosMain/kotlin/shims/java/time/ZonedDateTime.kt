package java.time

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.time.temporal.Temporal
import java.time.temporal.TemporalAccessor
import java.time.temporal.TemporalAdjuster
import java.time.temporal.TemporalField
import java.time.temporal.nichtUnterstuetzt

/**
 * Ersatz fuer java.time.ZonedDateTime: Ortszeit, Versatz und Zone. Rechnen mit Tagen, Wochen, Monaten und Jahren
 * geht ueber die Ortszeit (mit Sommerzeit-Regeln wie in Java), mit Stunden und kleineren Einheiten ueber den Zeitpunkt.
 */
class ZonedDateTime private constructor(
    private val ortszeit: LocalDateTime,
    val offset: ZoneOffset,
    val zone: ZoneId,
) : Temporal, Comparable<ZonedDateTime> {

    fun toLocalDateTime(): LocalDateTime = ortszeit
    fun toLocalDate(): LocalDate = ortszeit.toLocalDate()
    fun toLocalTime(): LocalTime = ortszeit.toLocalTime()
    fun toOffsetDateTime(): OffsetDateTime = OffsetDateTime.of(ortszeit, offset)

    val year: Int get() = ortszeit.year
    val monthValue: Int get() = ortszeit.monthValue
    val month: Month get() = ortszeit.month
    val dayOfMonth: Int get() = ortszeit.dayOfMonth
    val dayOfYear: Int get() = ortszeit.dayOfYear
    val dayOfWeek: DayOfWeek get() = ortszeit.dayOfWeek
    val hour: Int get() = ortszeit.hour
    val minute: Int get() = ortszeit.minute
    val second: Int get() = ortszeit.second
    val nano: Int get() = ortszeit.nano

    fun toEpochSecond(): Long = ortszeit.toEpochSecond(offset)
    fun toInstant(): Instant = Instant.ofEpochSecond(toEpochSecond(), ortszeit.nano.toLong())

    /** Neue Ortszeit in derselben Zone; der bisherige Versatz bleibt, wo er gueltig ist (Doppelung im Herbst). */
    private fun ortsNeu(neu: LocalDateTime): ZonedDateTime = if (neu == ortszeit) this else ofLocal(neu, zone, offset)

    private fun zeitpunktNeu(neu: LocalDateTime): ZonedDateTime = ofInstant(neu, offset, zone)

    fun with(adjuster: TemporalAdjuster): ZonedDateTime = when (adjuster) {
        is LocalDate -> ortsNeu(LocalDateTime.of(adjuster, ortszeit.toLocalTime()))
        is LocalTime -> ortsNeu(LocalDateTime.of(ortszeit.toLocalDate(), adjuster))
        is LocalDateTime -> ortsNeu(adjuster)
        is OffsetDateTime -> ofLocal(adjuster.toLocalDateTime(), zone, adjuster.offset)
        is Instant -> ofInstant(adjuster, zone)
        is ZoneOffset -> if (adjuster != offset && zone.rules.isValidOffset(ortszeit, adjuster)) ZonedDateTime(ortszeit, adjuster, zone) else this
        else -> adjuster.adjustInto(this) as ZonedDateTime
    }

    fun withYear(year: Int): ZonedDateTime = ortsNeu(ortszeit.withYear(year))
    fun withMonth(month: Int): ZonedDateTime = ortsNeu(ortszeit.withMonth(month))
    fun withDayOfMonth(dayOfMonth: Int): ZonedDateTime = ortsNeu(ortszeit.withDayOfMonth(dayOfMonth))
    fun withDayOfYear(dayOfYear: Int): ZonedDateTime = ortsNeu(ortszeit.withDayOfYear(dayOfYear))
    fun withHour(hour: Int): ZonedDateTime = ortsNeu(ortszeit.withHour(hour))
    fun withMinute(minute: Int): ZonedDateTime = ortsNeu(ortszeit.withMinute(minute))
    fun withSecond(second: Int): ZonedDateTime = ortsNeu(ortszeit.withSecond(second))
    fun withNano(nanoOfSecond: Int): ZonedDateTime = ortsNeu(ortszeit.withNano(nanoOfSecond))

    fun withZoneSameInstant(zone: ZoneId): ZonedDateTime = if (zone == this.zone) this else ofInstant(ortszeit, offset, zone)
    fun withZoneSameLocal(zone: ZoneId): ZonedDateTime = if (zone == this.zone) this else ofLocal(ortszeit, zone, offset)
    fun withFixedOffsetZone(): ZonedDateTime = if (zone == offset) this else ZonedDateTime(ortszeit, offset, offset)

    fun withEarlierOffsetAtOverlap(): ZonedDateTime {
        val gueltig = zone.rules.getValidOffsets(ortszeit)
        return if (gueltig.size == 2 && gueltig[0] != offset) ZonedDateTime(ortszeit, gueltig[0], zone) else this
    }

    fun withLaterOffsetAtOverlap(): ZonedDateTime {
        val gueltig = zone.rules.getValidOffsets(ortszeit)
        return if (gueltig.size == 2 && gueltig[1] != offset) ZonedDateTime(ortszeit, gueltig[1], zone) else this
    }

    fun plusYears(years: Long): ZonedDateTime = ortsNeu(ortszeit.plusYears(years))
    fun plusMonths(months: Long): ZonedDateTime = ortsNeu(ortszeit.plusMonths(months))
    fun plusWeeks(weeks: Long): ZonedDateTime = ortsNeu(ortszeit.plusWeeks(weeks))
    fun plusDays(days: Long): ZonedDateTime = ortsNeu(ortszeit.plusDays(days))
    fun plusHours(hours: Long): ZonedDateTime = zeitpunktNeu(ortszeit.plusHours(hours))
    fun plusMinutes(minutes: Long): ZonedDateTime = zeitpunktNeu(ortszeit.plusMinutes(minutes))
    fun plusSeconds(seconds: Long): ZonedDateTime = zeitpunktNeu(ortszeit.plusSeconds(seconds))
    fun plusNanos(nanos: Long): ZonedDateTime = zeitpunktNeu(ortszeit.plusNanos(nanos))

    fun minusYears(years: Long): ZonedDateTime = plusYears(-years)
    fun minusMonths(months: Long): ZonedDateTime = plusMonths(-months)
    fun minusWeeks(weeks: Long): ZonedDateTime = plusWeeks(-weeks)
    fun minusDays(days: Long): ZonedDateTime = plusDays(-days)
    fun minusHours(hours: Long): ZonedDateTime = plusHours(-hours)
    fun minusMinutes(minutes: Long): ZonedDateTime = plusMinutes(-minutes)
    fun minusSeconds(seconds: Long): ZonedDateTime = plusSeconds(-seconds)
    fun minusNanos(nanos: Long): ZonedDateTime = plusNanos(-nanos)

    /** Wie Java: zuerst der Zeitpunkt, dann Ortszeit, dann Zone. */
    override fun compareTo(other: ZonedDateTime): Int {
        var c = toEpochSecond().compareTo(other.toEpochSecond())
        if (c == 0) {
            c = nano - other.nano
            if (c == 0) {
                c = ortszeit.compareTo(other.ortszeit)
                if (c == 0) c = zone.id.compareTo(other.zone.id)
            }
        }
        return c
    }

    fun isBefore(other: ZonedDateTime): Boolean = toEpochSecond() < other.toEpochSecond() || (toEpochSecond() == other.toEpochSecond() && nano < other.nano)
    fun isAfter(other: ZonedDateTime): Boolean = toEpochSecond() > other.toEpochSecond() || (toEpochSecond() == other.toEpochSecond() && nano > other.nano)
    fun isEqual(other: ZonedDateTime): Boolean = toEpochSecond() == other.toEpochSecond() && nano == other.nano

    override fun equals(other: Any?): Boolean =
        other is ZonedDateTime && other.ortszeit == ortszeit && other.offset == offset && other.zone == zone

    override fun hashCode(): Int = ortszeit.hashCode() xor offset.hashCode() xor ((zone.hashCode() shl 3) or (zone.hashCode() ushr 29))

    override fun toString(): String {
        val s = ortszeit.toString() + offset.toString()
        return if (offset !== zone && offset != zone) "$s[$zone]" else s
    }

    fun format(formatter: DateTimeFormatter): String = formatter.format(this)

    override fun isSupported(field: TemporalField): Boolean =
        field is ChronoField && (field.isDateBased() || field.isTimeBased() || field == ChronoField.INSTANT_SECONDS || field == ChronoField.OFFSET_SECONDS)

    override fun getLong(field: TemporalField): Long = when (field) {
        ChronoField.INSTANT_SECONDS -> toEpochSecond()
        ChronoField.OFFSET_SECONDS -> offset.totalSeconds.toLong()
        else -> if (field is ChronoField && (field.isDateBased() || field.isTimeBased())) ortszeit.getLong(field) else nichtUnterstuetzt(field)
    }

    companion object {
        fun ofInstant(instant: Instant, zone: ZoneId): ZonedDateTime = erzeuge(instant.epochSecond, instant.nano, zone)

        fun ofInstant(localDateTime: LocalDateTime, offset: ZoneOffset, zone: ZoneId): ZonedDateTime =
            if (zone.rules.isValidOffset(localDateTime, offset)) ZonedDateTime(localDateTime, offset, zone)
            else erzeuge(localDateTime.toEpochSecond(offset), localDateTime.nano, zone)

        private fun erzeuge(epochSecond: Long, nanoOfSecond: Int, zone: ZoneId): ZonedDateTime {
            val offset = ZoneOffset.ofTotalSeconds(zone.rules.versatz(epochSecond))
            return ZonedDateTime(LocalDateTime.ofEpochSecond(epochSecond, nanoOfSecond, offset), offset, zone)
        }

        fun of(localDateTime: LocalDateTime, zone: ZoneId): ZonedDateTime = ofLocal(localDateTime, zone, null)

        fun of(date: LocalDate, time: LocalTime, zone: ZoneId): ZonedDateTime = of(LocalDateTime.of(date, time), zone)

        fun of(year: Int, month: Int, dayOfMonth: Int, hour: Int, minute: Int, second: Int, nanoOfSecond: Int, zone: ZoneId): ZonedDateTime =
            of(LocalDateTime.of(year, month, dayOfMonth, hour, minute, second, nanoOfSecond), zone)

        /**
         * Ortszeit in [zone] wie Java: genau ein gueltiger Versatz wird genommen; in einer Luecke (Fruehjahr) rueckt
         * die Uhrzeit um die Laenge der Luecke vor; bei Doppelung (Herbst) gilt [preferredOffset], wenn gueltig,
         * sonst der fruehere Versatz.
         */
        fun ofLocal(localDateTime: LocalDateTime, zone: ZoneId, preferredOffset: ZoneOffset?): ZonedDateTime {
            if (zone is ZoneOffset) return ZonedDateTime(localDateTime, zone, zone)
            val rules = zone.rules
            if (rules.isFixedOffset()) {
                val o = rules.getOffset(Instant.EPOCH)
                return ZonedDateTime(localDateTime, o, zone)
            }
            val lage = rules.lage(localDateTime.toEpochSecond(ZoneOffset.UTC))
            return when (lage.gueltig.size) {
                1 -> ZonedDateTime(localDateTime, ZoneOffset.ofTotalSeconds(lage.gueltig[0]), zone)
                0 -> ZonedDateTime(localDateTime.plusSeconds((lage.nachher - lage.vorher).toLong()), ZoneOffset.ofTotalSeconds(lage.nachher), zone)
                else -> {
                    val o = if (preferredOffset != null && preferredOffset.totalSeconds in lage.gueltig) preferredOffset
                    else ZoneOffset.ofTotalSeconds(lage.gueltig[0])
                    ZonedDateTime(localDateTime, o, zone)
                }
            }
        }

        fun ofStrict(localDateTime: LocalDateTime, offset: ZoneOffset, zone: ZoneId): ZonedDateTime {
            if (!zone.rules.isValidOffset(localDateTime, offset)) throw DateTimeException("ZoneOffset '$offset' is not valid for LocalDateTime '$localDateTime' in zone '$zone'")
            return ZonedDateTime(localDateTime, offset, zone)
        }

        fun now(): ZonedDateTime = now(ZoneId.systemDefault())
        fun now(zone: ZoneId): ZonedDateTime = ofInstant(Instant.now(), zone)

        fun from(temporal: TemporalAccessor): ZonedDateTime {
            if (temporal is ZonedDateTime) return temporal
            if (temporal is OffsetDateTime) return temporal.toZonedDateTime()
            try {
                val zone = (temporal as? java.time.format.Geparst)?.zoneOderVersatz() ?: ZoneId.from(temporal)
                return if (temporal.isSupported(ChronoField.INSTANT_SECONDS)) {
                    erzeuge(temporal.getLong(ChronoField.INSTANT_SECONDS), temporal.get(ChronoField.NANO_OF_SECOND), zone)
                } else of(LocalDateTime.from(temporal), zone)
            } catch (e: DateTimeException) {
                throw DateTimeException("Unable to obtain ZonedDateTime from TemporalAccessor: $temporal", e)
            }
        }

        fun parse(text: CharSequence?): ZonedDateTime = parse(text, DateTimeFormatter.ISO_ZONED_DATE_TIME)
        fun parse(text: CharSequence?, formatter: DateTimeFormatter): ZonedDateTime = formatter.parseZu(text, "ZonedDateTime") { from(it) }
    }
}

/** Ersatz fuer java.time.OffsetDateTime: Ortszeit mit festem Versatz. */
class OffsetDateTime private constructor(private val ortszeit: LocalDateTime, val offset: ZoneOffset) :
    Temporal, TemporalAdjuster, Comparable<OffsetDateTime> {

    override fun adjustInto(temporal: Temporal): Temporal = when (temporal) {
        is OffsetDateTime -> this
        is ZonedDateTime -> temporal.with(this)
        else -> throw DateTimeException("Unsupported temporal: $temporal")
    }

    fun toLocalDateTime(): LocalDateTime = ortszeit
    fun toLocalDate(): LocalDate = ortszeit.toLocalDate()
    fun toLocalTime(): LocalTime = ortszeit.toLocalTime()

    val year: Int get() = ortszeit.year
    val monthValue: Int get() = ortszeit.monthValue
    val month: Month get() = ortszeit.month
    val dayOfMonth: Int get() = ortszeit.dayOfMonth
    val dayOfYear: Int get() = ortszeit.dayOfYear
    val dayOfWeek: DayOfWeek get() = ortszeit.dayOfWeek
    val hour: Int get() = ortszeit.hour
    val minute: Int get() = ortszeit.minute
    val second: Int get() = ortszeit.second
    val nano: Int get() = ortszeit.nano

    fun toEpochSecond(): Long = ortszeit.toEpochSecond(offset)
    fun toInstant(): Instant = ortszeit.toInstant(offset)
    fun toZonedDateTime(): ZonedDateTime = ZonedDateTime.of(ortszeit, offset)
    fun atZoneSameInstant(zone: ZoneId): ZonedDateTime = ZonedDateTime.ofInstant(toInstant(), zone)
    fun atZoneSimilarLocal(zone: ZoneId): ZonedDateTime = ZonedDateTime.ofLocal(ortszeit, zone, offset)

    fun withOffsetSameLocal(offset: ZoneOffset): OffsetDateTime = if (offset == this.offset) this else OffsetDateTime(ortszeit, offset)
    fun withOffsetSameInstant(offset: ZoneOffset): OffsetDateTime =
        if (offset == this.offset) this else OffsetDateTime(ortszeit.plusSeconds((offset.totalSeconds - this.offset.totalSeconds).toLong()), offset)

    fun with(adjuster: TemporalAdjuster): OffsetDateTime = when (adjuster) {
        is LocalDate, is LocalTime, is LocalDateTime -> OffsetDateTime(ortszeit.with(adjuster), offset)
        is Instant -> ofInstant(adjuster, offset)
        is ZoneOffset -> withOffsetSameLocal(adjuster)
        is OffsetDateTime -> adjuster
        else -> adjuster.adjustInto(this) as OffsetDateTime
    }

    fun plusYears(years: Long): OffsetDateTime = OffsetDateTime(ortszeit.plusYears(years), offset)
    fun plusMonths(months: Long): OffsetDateTime = OffsetDateTime(ortszeit.plusMonths(months), offset)
    fun plusWeeks(weeks: Long): OffsetDateTime = OffsetDateTime(ortszeit.plusWeeks(weeks), offset)
    fun plusDays(days: Long): OffsetDateTime = OffsetDateTime(ortszeit.plusDays(days), offset)
    fun plusHours(hours: Long): OffsetDateTime = OffsetDateTime(ortszeit.plusHours(hours), offset)
    fun plusMinutes(minutes: Long): OffsetDateTime = OffsetDateTime(ortszeit.plusMinutes(minutes), offset)
    fun plusSeconds(seconds: Long): OffsetDateTime = OffsetDateTime(ortszeit.plusSeconds(seconds), offset)
    fun minusDays(days: Long): OffsetDateTime = plusDays(-days)
    fun minusHours(hours: Long): OffsetDateTime = plusHours(-hours)

    /** Wie Java: zuerst der Zeitpunkt, dann die Ortszeit. */
    override fun compareTo(other: OffsetDateTime): Int {
        if (offset == other.offset) return ortszeit.compareTo(other.ortszeit)
        var c = toEpochSecond().compareTo(other.toEpochSecond())
        if (c == 0) {
            c = nano - other.nano
            if (c == 0) c = ortszeit.compareTo(other.ortszeit)
        }
        return c
    }

    fun isBefore(other: OffsetDateTime): Boolean = toInstant().isBefore(other.toInstant())
    fun isAfter(other: OffsetDateTime): Boolean = toInstant().isAfter(other.toInstant())
    fun isEqual(other: OffsetDateTime): Boolean = toInstant() == other.toInstant()

    override fun equals(other: Any?): Boolean = other is OffsetDateTime && other.ortszeit == ortszeit && other.offset == offset
    override fun hashCode(): Int = ortszeit.hashCode() xor offset.hashCode()
    override fun toString(): String = ortszeit.toString() + offset.toString()

    fun format(formatter: DateTimeFormatter): String = formatter.format(this)

    override fun isSupported(field: TemporalField): Boolean =
        field is ChronoField && (field.isDateBased() || field.isTimeBased() || field == ChronoField.INSTANT_SECONDS || field == ChronoField.OFFSET_SECONDS)

    override fun getLong(field: TemporalField): Long = when (field) {
        ChronoField.INSTANT_SECONDS -> toEpochSecond()
        ChronoField.OFFSET_SECONDS -> offset.totalSeconds.toLong()
        else -> if (field is ChronoField && (field.isDateBased() || field.isTimeBased())) ortszeit.getLong(field) else nichtUnterstuetzt(field)
    }

    companion object {
        val MIN: OffsetDateTime = OffsetDateTime(LocalDateTime.MIN, ZoneOffset.MAX)
        val MAX: OffsetDateTime = OffsetDateTime(LocalDateTime.MAX, ZoneOffset.MIN)

        fun of(dateTime: LocalDateTime, offset: ZoneOffset): OffsetDateTime = OffsetDateTime(dateTime, offset)
        fun of(date: LocalDate, time: LocalTime, offset: ZoneOffset): OffsetDateTime = OffsetDateTime(LocalDateTime.of(date, time), offset)
        fun of(year: Int, month: Int, dayOfMonth: Int, hour: Int, minute: Int, second: Int, nanoOfSecond: Int, offset: ZoneOffset): OffsetDateTime =
            OffsetDateTime(LocalDateTime.of(year, month, dayOfMonth, hour, minute, second, nanoOfSecond), offset)

        fun ofInstant(instant: Instant, zone: ZoneId): OffsetDateTime {
            val offset = zone.rules.getOffset(instant)
            return OffsetDateTime(LocalDateTime.ofEpochSecond(instant.epochSecond, instant.nano, offset), offset)
        }

        fun now(): OffsetDateTime = now(ZoneId.systemDefault())
        fun now(zone: ZoneId): OffsetDateTime = ofInstant(Instant.now(), zone)

        fun from(temporal: TemporalAccessor): OffsetDateTime {
            if (temporal is OffsetDateTime) return temporal
            if (temporal is ZonedDateTime) return temporal.toOffsetDateTime()
            try {
                val offset = ZoneOffset.from(temporal)
                return OffsetDateTime(LocalDateTime.from(temporal), offset)
            } catch (e: DateTimeException) {
                throw DateTimeException("Unable to obtain OffsetDateTime from TemporalAccessor: $temporal", e)
            }
        }

        fun parse(text: CharSequence?): OffsetDateTime = parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        fun parse(text: CharSequence?, formatter: DateTimeFormatter): OffsetDateTime = formatter.parseZu(text, "OffsetDateTime") { from(it) }
    }
}
