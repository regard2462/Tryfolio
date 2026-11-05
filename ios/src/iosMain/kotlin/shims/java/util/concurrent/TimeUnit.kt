package java.util.concurrent

/** Ersatz fuer java.util.concurrent.TimeUnit: Umrechnungen wie in Java (bei Ueberlauf auf Long.MIN/MAX begrenzt). */
enum class TimeUnit(private val nanos: Long) {
    NANOSECONDS(1L),
    MICROSECONDS(1_000L),
    MILLISECONDS(1_000_000L),
    SECONDS(1_000_000_000L),
    MINUTES(60_000_000_000L),
    HOURS(3_600_000_000_000L),
    DAYS(86_400_000_000_000L);

    /** [d] in dieser Einheit nach [ziel] umrechnen. */
    private fun nach(d: Long, ziel: Long): Long = when {
        nanos == ziel -> d
        nanos > ziel -> {
            val faktor = nanos / ziel
            val grenze = Long.MAX_VALUE / faktor
            when {
                d > grenze -> Long.MAX_VALUE
                d < -grenze -> Long.MIN_VALUE
                else -> d * faktor
            }
        }
        else -> d / (ziel / nanos)
    }

    fun convert(sourceDuration: Long, sourceUnit: TimeUnit): Long = sourceUnit.nach(sourceDuration, nanos)

    fun toNanos(duration: Long): Long = nach(duration, NANOSECONDS.nanos)
    fun toMicros(duration: Long): Long = nach(duration, MICROSECONDS.nanos)
    fun toMillis(duration: Long): Long = nach(duration, MILLISECONDS.nanos)
    fun toSeconds(duration: Long): Long = nach(duration, SECONDS.nanos)
    fun toMinutes(duration: Long): Long = nach(duration, MINUTES.nanos)
    fun toHours(duration: Long): Long = nach(duration, HOURS.nanos)
    fun toDays(duration: Long): Long = nach(duration, DAYS.nanos)
}
