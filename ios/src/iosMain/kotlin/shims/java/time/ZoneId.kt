package java.time

import java.time.format.TextStyle
import java.time.temporal.ChronoField
import java.time.temporal.Temporal
import java.time.temporal.TemporalAccessor
import java.time.temporal.TemporalAdjuster
import java.time.temporal.TemporalField
import java.time.temporal.nichtUnterstuetzt
import java.time.zone.ZoneRules
import java.time.zone.ZoneRulesException
import java.util.Locale

/** Ersatz fuer java.time.ZoneId: feste Versaetze (ZoneOffset) oder IANA-Zonen ueber NSTimeZone. */
abstract class ZoneId internal constructor() {

    abstract val id: String
    abstract val rules: ZoneRules

    /** Zone mit festem Versatz als ZoneOffset, sonst die Zone selbst. */
    open fun normalized(): ZoneId = if (rules.isFixedOffset()) rules.getOffset(Instant.EPOCH) else this

    /** Anzeigename; hier die Kennung (Java nutzt Zonennamen wie "Mitteleuropäische Zeit"). */
    open fun getDisplayName(style: TextStyle, locale: Locale): String = id

    override fun equals(other: Any?): Boolean = other is ZoneId && other !is ZoneOffset && other.id == id
    override fun hashCode(): Int = id.hashCode()
    override fun toString(): String = id

    companion object {
        private val regionen = java.util.concurrent.ConcurrentHashMap<String, ZoneRegion>()
        private var standard: ZoneId? = null

        val SHORT_IDS: Map<String, String> = mapOf(
            "ACT" to "Australia/Darwin", "AET" to "Australia/Sydney", "AGT" to "America/Argentina/Buenos_Aires",
            "ART" to "Africa/Cairo", "AST" to "America/Anchorage", "BET" to "America/Sao_Paulo", "BST" to "Asia/Dhaka",
            "CAT" to "Africa/Harare", "CNT" to "America/St_Johns", "CST" to "America/Chicago", "CTT" to "Asia/Shanghai",
            "EAT" to "Africa/Addis_Ababa", "ECT" to "Europe/Paris", "IET" to "America/Indiana/Indianapolis",
            "IST" to "Asia/Kolkata", "JST" to "Asia/Tokyo", "MIT" to "Pacific/Apia", "NET" to "Asia/Yerevan",
            "NST" to "Pacific/Auckland", "PLT" to "Asia/Karachi", "PNT" to "America/Phoenix", "PRT" to "America/Puerto_Rico",
            "PST" to "America/Los_Angeles", "SST" to "Pacific/Guadalcanal", "VST" to "Asia/Ho_Chi_Minh",
            "EST" to "-05:00", "MST" to "-07:00", "HST" to "-10:00",
        )

        /** Zone des Geraets (NSTimeZone.localTimeZone), einmal bestimmt. */
        fun systemDefault(): ZoneId = standard ?: run {
            val z = runCatching { of(jsStandardZone()) }.getOrElse { ZoneOffset.UTC }
            standard = z
            z
        }

        fun of(zoneId: String, aliasMap: Map<String, String>): ZoneId = of(aliasMap[zoneId] ?: zoneId)

        fun of(zoneId: String): ZoneId {
            if (zoneId.length <= 1 || zoneId.startsWith("+") || zoneId.startsWith("-")) return ZoneOffset.of(zoneId)
            for (vorsilbe in listOf("UTC", "GMT", "UT")) {
                if (zoneId == vorsilbe) return region(vorsilbe, ZoneOffset.UTC.rules)
                if (zoneId.startsWith(vorsilbe) && zoneId.length > vorsilbe.length &&
                    (zoneId[vorsilbe.length] == '+' || zoneId[vorsilbe.length] == '-')) {
                    val offset = ZoneOffset.of(zoneId.substring(vorsilbe.length))
                    return if (offset.totalSeconds == 0) region(vorsilbe, offset.rules) else region(vorsilbe + offset.id, offset.rules)
                }
            }
            if (!Regex("[A-Za-z][A-Za-z0-9~/._+-]+").matches(zoneId)) throw DateTimeException("Invalid ID for region-based ZoneId, invalid format: $zoneId")
            regionen[zoneId]?.let { return it }
            if (!jsZoneBekannt(zoneId)) throw ZoneRulesException("Unknown time-zone ID: $zoneId")
            return ZoneRegion(zoneId, ZoneRules.intl(zoneId)).also { regionen[zoneId] = it }
        }

        private fun region(id: String, rules: ZoneRules): ZoneId = regionen.getOrPut(id) { ZoneRegion(id, rules) }

        fun ofOffset(prefix: String, offset: ZoneOffset): ZoneId = when (prefix) {
            "" -> offset
            "UTC", "GMT", "UT" -> if (offset.totalSeconds == 0) region(prefix, offset.rules) else region(prefix + offset.id, offset.rules)
            else -> throw IllegalArgumentException("prefix should be GMT, UTC or UT, is: $prefix")
        }

        fun from(temporal: TemporalAccessor): ZoneId = when (temporal) {
            is ZoneId -> temporal
            is ZonedDateTime -> temporal.zone
            is OffsetDateTime -> temporal.offset
            else -> throw DateTimeException("Unable to obtain ZoneId from TemporalAccessor: $temporal")
        }

        fun getAvailableZoneIds(): Set<String> = jsZonenListe().split(',').filter { it.isNotEmpty() }.toSet() + setOf("UTC", "GMT")
    }
}

/** Zone mit Kennung (Europe/Berlin, UTC, GMT+01:00). */
internal class ZoneRegion(override val id: String, override val rules: ZoneRules) : ZoneId()

/** Ersatz fuer java.time.ZoneOffset: fester Versatz zu UTC, -18:00 bis +18:00. */
class ZoneOffset private constructor(val totalSeconds: Int) : ZoneId(), TemporalAccessor, TemporalAdjuster, Comparable<ZoneOffset> {

    override val id: String = kennung(totalSeconds)

    private var regeln: ZoneRules? = null
    override val rules: ZoneRules get() = regeln ?: ZoneRules.of(this).also { regeln = it }

    override fun normalized(): ZoneId = this

    /** Wie Java: absteigend nach Versatz (+10:00 vor +09:00). */
    override fun compareTo(other: ZoneOffset): Int = other.totalSeconds - totalSeconds

    override fun equals(other: Any?): Boolean = other is ZoneOffset && other.totalSeconds == totalSeconds
    override fun hashCode(): Int = totalSeconds
    override fun toString(): String = id

    override fun isSupported(field: TemporalField): Boolean = field == ChronoField.OFFSET_SECONDS
    override fun getLong(field: TemporalField): Long = if (field == ChronoField.OFFSET_SECONDS) totalSeconds.toLong() else nichtUnterstuetzt(field)

    override fun adjustInto(temporal: Temporal): Temporal = when (temporal) {
        is OffsetDateTime -> temporal.withOffsetSameLocal(this)
        else -> throw DateTimeException("Unsupported temporal: $temporal")
    }

    companion object {
        private const val MAX_SEKUNDEN = 18 * 3600
        private val zwischenspeicher = java.util.concurrent.ConcurrentHashMap<Int, ZoneOffset>()

        val UTC: ZoneOffset = ZoneOffset(0)
        val MIN: ZoneOffset = ZoneOffset(-MAX_SEKUNDEN)
        val MAX: ZoneOffset = ZoneOffset(MAX_SEKUNDEN)

        private fun kennung(sekunden: Int): String {
            if (sekunden == 0) return "Z"
            val betrag = kotlin.math.abs(sekunden)
            val h = betrag / 3600
            val m = (betrag / 60) % 60
            val s = betrag % 60
            val sb = StringBuilder(if (sekunden < 0) "-" else "+")
            sb.append(stellen(h, 2)).append(':').append(stellen(m, 2))
            if (s != 0) sb.append(':').append(stellen(s, 2))
            return sb.toString()
        }

        fun ofTotalSeconds(totalSeconds: Int): ZoneOffset {
            if (totalSeconds < -MAX_SEKUNDEN || totalSeconds > MAX_SEKUNDEN) throw DateTimeException("Zone offset not in valid range: -18:00 to +18:00")
            if (totalSeconds == 0) return UTC
            if (totalSeconds % (15 * 60) == 0) return zwischenspeicher.getOrPut(totalSeconds) { ZoneOffset(totalSeconds) }
            return ZoneOffset(totalSeconds)
        }

        fun ofHours(hours: Int): ZoneOffset = ofHoursMinutesSeconds(hours, 0, 0)
        fun ofHoursMinutes(hours: Int, minutes: Int): ZoneOffset = ofHoursMinutesSeconds(hours, minutes, 0)

        fun ofHoursMinutesSeconds(hours: Int, minutes: Int, seconds: Int): ZoneOffset {
            if (hours < -18 || hours > 18) throw DateTimeException("Zone offset hours not in valid range: value $hours is not in the range -18 to 18")
            if (hours > 0) {
                if (minutes < 0 || seconds < 0) throw DateTimeException("Zone offset minutes and seconds must be positive because hours is positive")
            } else if (hours < 0) {
                if (minutes > 0 || seconds > 0) throw DateTimeException("Zone offset minutes and seconds must be negative because hours is negative")
            } else if ((minutes > 0 && seconds < 0) || (minutes < 0 && seconds > 0)) {
                throw DateTimeException("Zone offset minutes and seconds must have the same sign")
            }
            if (minutes < -59 || minutes > 59) throw DateTimeException("Zone offset minutes not in valid range: value $minutes is not in the range -59 to 59")
            if (seconds < -59 || seconds > 59) throw DateTimeException("Zone offset seconds not in valid range: value $seconds is not in the range -59 to 59")
            if (kotlin.math.abs(hours) == 18 && (minutes or seconds) != 0) throw DateTimeException("Zone offset not in valid range: -18:00 to +18:00")
            return ofTotalSeconds(hours * 3600 + minutes * 60 + seconds)
        }

        /** Z, +h, +hh, +hh:mm, +hhmm, +hh:mm:ss, +hhmmss (wie Java). */
        fun of(offsetId: String): ZoneOffset {
            if (offsetId == "Z") return UTC
            val ungueltig = { DateTimeException("Invalid ID for ZoneOffset, invalid format: $offsetId") }
            fun zahl(von: Int, mitDoppelpunkt: Boolean): Int {
                if (mitDoppelpunkt && offsetId[von - 1] != ':') throw ungueltig()
                val a = offsetId[von]
                val b = offsetId[von + 1]
                if (a !in '0'..'9' || b !in '0'..'9') throw ungueltig()
                return (a - '0') * 10 + (b - '0')
            }
            val h: Int
            val m: Int
            val s: Int
            when (offsetId.length) {
                2 -> { if (offsetId[1] !in '0'..'9') throw ungueltig(); h = offsetId[1] - '0'; m = 0; s = 0 }
                3 -> { h = zahl(1, false); m = 0; s = 0 }
                5 -> { h = zahl(1, false); m = zahl(3, false); s = 0 }
                6 -> { h = zahl(1, false); m = zahl(4, true); s = 0 }
                7 -> { h = zahl(1, false); m = zahl(3, false); s = zahl(5, false) }
                9 -> { h = zahl(1, false); m = zahl(4, true); s = zahl(7, true) }
                else -> throw ungueltig()
            }
            val vorzeichen = offsetId[0]
            if (vorzeichen != '+' && vorzeichen != '-') throw DateTimeException("Invalid ID for ZoneOffset, plus/minus not found when expected: $offsetId")
            return if (vorzeichen == '-') ofHoursMinutesSeconds(-h, -m, -s) else ofHoursMinutesSeconds(h, m, s)
        }

        fun from(temporal: TemporalAccessor): ZoneOffset = when (temporal) {
            is ZoneOffset -> temporal
            else -> if (temporal.isSupported(ChronoField.OFFSET_SECONDS)) ofTotalSeconds(temporal.get(ChronoField.OFFSET_SECONDS))
            else throw DateTimeException("Unable to obtain ZoneOffset from TemporalAccessor: $temporal")
        }
    }
}
