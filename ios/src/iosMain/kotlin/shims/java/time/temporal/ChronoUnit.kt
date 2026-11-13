package java.time.temporal

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime

/* Ersatz fuer java.time.temporal.ChronoUnit: die datumsbasierten Einheiten mit between() fuer Datumswerte. */

interface TemporalUnit {
    fun isDateBased(): Boolean
    fun isTimeBased(): Boolean
}

enum class ChronoUnit(private val anzeige: String) : TemporalUnit {
    DAYS("Days"),
    WEEKS("Weeks"),
    MONTHS("Months"),
    YEARS("Years"),
    DECADES("Decades"),
    CENTURIES("Centuries");

    override fun isDateBased(): Boolean = true
    override fun isTimeBased(): Boolean = false

    /** Ganze Einheiten von [temporal1Inclusive] bis [temporal2Exclusive], wie im JDK fuer Datumswerte. */
    fun between(temporal1Inclusive: Temporal, temporal2Exclusive: Temporal): Long {
        val a = datumVon(temporal1Inclusive)
        val b = datumVon(temporal2Exclusive)
        return when (this) {
            DAYS -> b.toEpochDay() - a.toEpochDay()
            WEEKS -> (b.toEpochDay() - a.toEpochDay()) / 7
            MONTHS -> monate(a, b)
            YEARS -> monate(a, b) / 12
            DECADES -> monate(a, b) / 120
            CENTURIES -> monate(a, b) / 1200
        }
    }

    override fun toString(): String = anzeige

    private companion object {
        fun datumVon(t: Temporal): LocalDate = when (t) {
            is LocalDate -> t
            is LocalDateTime -> t.toLocalDate()
            is ZonedDateTime -> t.toLocalDate()
            else -> throw UnsupportedTemporalTypeException("Unsupported temporal: $t")
        }

        fun monate(a: LocalDate, b: LocalDate): Long {
            val start = a.year * 12L + a.monthValue - 1
            val ende = b.year * 12L + b.monthValue - 1
            var n = ende - start
            if (n > 0 && b.dayOfMonth < a.dayOfMonth) n--
            else if (n < 0 && b.dayOfMonth > a.dayOfMonth) n++
            return n
        }
    }
}
