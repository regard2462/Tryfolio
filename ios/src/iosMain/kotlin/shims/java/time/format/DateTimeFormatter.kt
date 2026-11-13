package java.time.format

import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoField
import java.time.temporal.TemporalAccessor
import java.time.temporal.TemporalField
import java.time.temporal.UnsupportedTemporalTypeException
import java.util.Locale

open class DateTimeParseException(message: String?, val parsedString: CharSequence, val errorIndex: Int, cause: Throwable? = null) :
    DateTimeException(message, cause)

enum class ResolverStyle { STRICT, SMART, LENIENT }

enum class SignStyle { NORMAL, ALWAYS, NEVER, NOT_NEGATIVE, EXCEEDS_PAD }

/**
 * Ersatz fuer java.time.format.DateTimeFormatter: Muster wie in Java (ofPattern) und die ISO-/RFC-Formate.
 * Musterbuchstaben: G u y D M L d E a h K k H m s S V O X x Z, Literale in '...', optionale Teile in [...].
 * Nicht unterstuetzt (IllegalArgumentException): z v Q q w W Y e c F g B n N A p.
 */
class DateTimeFormatter private constructor(
    private val teile: List<Teil>,
    val locale: Locale,
    val resolverStyle: ResolverStyle,
    private val grossKleinEgal: Boolean,
    private val name: String,
) {

    fun withLocale(locale: Locale): DateTimeFormatter =
        if (locale == this.locale) this else DateTimeFormatter(teile, locale, resolverStyle, grossKleinEgal, name)

    fun withResolverStyle(resolverStyle: ResolverStyle): DateTimeFormatter =
        if (resolverStyle == this.resolverStyle) this else DateTimeFormatter(teile, locale, resolverStyle, grossKleinEgal, name)

    fun format(temporal: TemporalAccessor): String {
        val sb = StringBuilder(32)
        formatTo(temporal, sb)
        return sb.toString()
    }

    fun formatTo(temporal: TemporalAccessor, appendable: Appendable) {
        val ctx = Ausgabe(temporal, locale)
        val sb = StringBuilder()
        for (t in teile) t.ausgeben(ctx, sb)
        appendable.append(sb)
    }

    /** Text lesen und die Felder aufloesen (Datum, Uhrzeit, Versatz, Zone). */
    fun parse(text: CharSequence?): TemporalAccessor {
        if (text == null) throw NullPointerException("text")
        val roh = lesen(text)
        try {
            return roh.aufloesen(resolverStyle)
        } catch (e: DateTimeParseException) {
            throw e
        } catch (e: DateTimeException) {
            throw DateTimeParseException("Text '$text' could not be parsed: ${e.message}", text, 0, e)
        }
    }

    internal fun <T> parseZu(text: CharSequence?, typ: String, umwandeln: (TemporalAccessor) -> T): T {
        if (text == null) throw NullPointerException("text")
        val geparst = parse(text)
        try {
            return umwandeln(geparst)
        } catch (e: DateTimeException) {
            throw DateTimeParseException("Text '$text' could not be parsed: ${e.message}", text, 0, e)
        }
    }

    private fun lesen(text: CharSequence): Geparst {
        val ctx = Eingabe(text, locale, grossKleinEgal)
        val pos = Teil.alleLesen(teile, ctx, 0)
        if (pos < 0) {
            val i = pos.inv()
            throw DateTimeParseException("Text '$text' could not be parsed at index $i", text, i)
        }
        if (pos < text.length) {
            throw DateTimeParseException("Text '$text' could not be parsed, unparsed text found at index $pos", text, pos)
        }
        return ctx.ergebnis
    }

    override fun toString(): String = name

    companion object {
        fun ofPattern(pattern: String): DateTimeFormatter = ofPattern(pattern, Locale.getDefault())

        fun ofPattern(pattern: String, locale: Locale): DateTimeFormatter =
            DateTimeFormatter(Muster.zerlegen(pattern), locale, ResolverStyle.SMART, false, pattern)

        private fun iso(name: String, ci: Boolean, stil: ResolverStyle, vararg t: Teil) =
            DateTimeFormatter(t.toList(), Locale.ROOT, stil, ci, name)

        private val DATUM = listOf(
            Teil.Zahl(ChronoField.YEAR, 4, 10, SignStyle.EXCEEDS_PAD), Teil.Literal("-"),
            Teil.Zahl(ChronoField.MONTH_OF_YEAR, 2, 2, SignStyle.NOT_NEGATIVE), Teil.Literal("-"),
            Teil.Zahl(ChronoField.DAY_OF_MONTH, 2, 2, SignStyle.NOT_NEGATIVE),
        )
        private val ZEIT = listOf(
            Teil.Zahl(ChronoField.HOUR_OF_DAY, 2, 2, SignStyle.NOT_NEGATIVE), Teil.Literal(":"),
            Teil.Zahl(ChronoField.MINUTE_OF_HOUR, 2, 2, SignStyle.NOT_NEGATIVE),
            Teil.Optional(listOf(
                Teil.Literal(":"), Teil.Zahl(ChronoField.SECOND_OF_MINUTE, 2, 2, SignStyle.NOT_NEGATIVE),
                Teil.Optional(listOf(Teil.Bruch(0, 9, true))),
            )),
        )
        private val DATUM_ZEIT = DATUM + Teil.Literal("T") + ZEIT
        private val VERSATZ_ID = Teil.Versatz("+HH:MM:ss", "Z")
        private val REGION = listOf(Teil.Literal("["), Teil.GrossKlein(false), Teil.ZonenId(nurRegion = true), Teil.Literal("]"))

        val ISO_LOCAL_DATE: DateTimeFormatter = iso("ISO_LOCAL_DATE", false, ResolverStyle.STRICT, *DATUM.toTypedArray())
        val ISO_LOCAL_TIME: DateTimeFormatter = iso("ISO_LOCAL_TIME", false, ResolverStyle.STRICT, *ZEIT.toTypedArray())
        val ISO_LOCAL_DATE_TIME: DateTimeFormatter = iso("ISO_LOCAL_DATE_TIME", true, ResolverStyle.STRICT, *DATUM_ZEIT.toTypedArray())
        val ISO_OFFSET_DATE_TIME: DateTimeFormatter =
            iso("ISO_OFFSET_DATE_TIME", true, ResolverStyle.STRICT, *(DATUM_ZEIT + VERSATZ_ID).toTypedArray())
        val ISO_ZONED_DATE_TIME: DateTimeFormatter =
            iso("ISO_ZONED_DATE_TIME", true, ResolverStyle.STRICT, *(DATUM_ZEIT + VERSATZ_ID + Teil.Optional(REGION)).toTypedArray())
        val ISO_DATE_TIME: DateTimeFormatter =
            iso("ISO_DATE_TIME", true, ResolverStyle.STRICT, *(DATUM_ZEIT + Teil.Optional(listOf(VERSATZ_ID, Teil.Optional(REGION)))).toTypedArray())
        val ISO_OFFSET_DATE: DateTimeFormatter = iso("ISO_OFFSET_DATE", true, ResolverStyle.STRICT, *(DATUM + VERSATZ_ID).toTypedArray())
        val ISO_DATE: DateTimeFormatter = iso("ISO_DATE", true, ResolverStyle.STRICT, *(DATUM + Teil.Optional(listOf(VERSATZ_ID))).toTypedArray())
        val ISO_OFFSET_TIME: DateTimeFormatter = iso("ISO_OFFSET_TIME", true, ResolverStyle.STRICT, *(ZEIT + VERSATZ_ID).toTypedArray())
        val ISO_TIME: DateTimeFormatter = iso("ISO_TIME", true, ResolverStyle.STRICT, *(ZEIT + Teil.Optional(listOf(VERSATZ_ID))).toTypedArray())
        val ISO_INSTANT: DateTimeFormatter = iso("ISO_INSTANT", true, ResolverStyle.STRICT, Teil.Zeitpunkt)
        val BASIC_ISO_DATE: DateTimeFormatter = iso(
            "BASIC_ISO_DATE", true, ResolverStyle.STRICT,
            Teil.Zahl(ChronoField.YEAR, 4, 4, SignStyle.NOT_NEGATIVE),
            Teil.Zahl(ChronoField.MONTH_OF_YEAR, 2, 2, SignStyle.NOT_NEGATIVE),
            Teil.Zahl(ChronoField.DAY_OF_MONTH, 2, 2, SignStyle.NOT_NEGATIVE),
            Teil.Optional(listOf(Teil.Versatz("+HHMMss", "Z"))),
        )
        val RFC_1123_DATE_TIME: DateTimeFormatter = iso("RFC_1123_DATE_TIME", true, ResolverStyle.SMART, Teil.Rfc1123)

        /** Wie der Leser von YearMonth.parse: uuuu-MM. */
        internal val ISO_YEAR_MONTH: DateTimeFormatter = iso(
            "ISO_YEAR_MONTH", false, ResolverStyle.SMART,
            Teil.Zahl(ChronoField.YEAR, 4, 10, SignStyle.EXCEEDS_PAD), Teil.Literal("-"),
            Teil.Zahl(ChronoField.MONTH_OF_YEAR, 2, 2, SignStyle.NOT_NEGATIVE),
        )
    }
}

// ================================================================ Ausgabe und Eingabe

internal class Ausgabe(val temporal: TemporalAccessor, val locale: Locale) {
    /** In einem optionalen Teil: fehlende Felder brechen nur diesen Teil ab. */
    var optional = 0

    class Fehlt : RuntimeException()

    fun wert(feld: TemporalField): Long {
        if (!temporal.isSupported(feld)) {
            if (optional > 0) throw Fehlt()
            throw UnsupportedTemporalTypeException("Unsupported field: $feld")
        }
        return temporal.getLong(feld)
    }

    fun versatz(): Int? = when {
        temporal.isSupported(ChronoField.OFFSET_SECONDS) -> temporal.getLong(ChronoField.OFFSET_SECONDS).toInt()
        optional > 0 -> throw Fehlt()
        else -> throw DateTimeException("Unable to extract value: $temporal")
    }
}

internal class Eingabe(val text: CharSequence, val locale: Locale, var grossKleinEgal: Boolean) {
    var ergebnis = Geparst()

    fun gleich(pos: Int, s: String): Boolean {
        if (pos + s.length > text.length) return false
        for (i in s.indices) {
            val a = text[pos + i]
            val b = s[i]
            if (a != b && !(grossKleinEgal && (a.uppercaseChar() == b.uppercaseChar() || a.lowercaseChar() == b.lowercaseChar()))) return false
        }
        return true
    }

    /** Feld setzen; widerspricht es einem schon gelesenen Wert, schlaegt das Lesen fehl. */
    fun setze(feld: ChronoField, wert: Long, fehlerPos: Int, neuePos: Int): Int {
        val alt = ergebnis.felder.put(feld, wert)
        return if (alt != null && alt != wert) fehlerPos.inv() else neuePos
    }
}

// ================================================================ Bausteine

internal sealed class Teil {
    abstract fun ausgeben(ctx: Ausgabe, sb: StringBuilder)

    /** Neue Position, oder bei Fehler die Fehlerstelle als inv(). [reserviert]: Ziffern fuer folgende feste Zahlen. */
    abstract fun lesen(ctx: Eingabe, pos: Int, reserviert: Int): Int

    /** Feste Breite in Ziffern (fuer das Lesen angrenzender Zahlen), sonst -1. */
    open val festeBreite: Int get() = -1

    companion object {
        fun alleLesen(teile: List<Teil>, ctx: Eingabe, start: Int): Int {
            var pos = start
            for ((i, t) in teile.withIndex()) {
                var reserviert = 0
                if (t is Zahl && t.min != t.max) {
                    var k = i + 1
                    while (k < teile.size && teile[k].festeBreite > 0) { reserviert += teile[k].festeBreite; k++ }
                }
                pos = t.lesen(ctx, pos, reserviert)
                if (pos < 0) return pos
            }
            return pos
        }

        private val GRENZEN = LongArray(19).also { var p = 1L; for (i in it.indices) { it[i] = p; p *= 10 } }
        fun zehnHoch(n: Int): Long = GRENZEN[n]
    }

    class Literal(val text: String) : Teil() {
        override fun ausgeben(ctx: Ausgabe, sb: StringBuilder) { sb.append(text) }
        override fun lesen(ctx: Eingabe, pos: Int, reserviert: Int): Int =
            if (ctx.gleich(pos, text)) pos + text.length else pos.inv()
    }

    class GrossKlein(val egal: Boolean) : Teil() {
        override fun ausgeben(ctx: Ausgabe, sb: StringBuilder) {}
        override fun lesen(ctx: Eingabe, pos: Int, reserviert: Int): Int { ctx.grossKleinEgal = egal; return pos }
    }

    class Zahl(val feld: ChronoField, val min: Int, val max: Int, val vorzeichen: SignStyle) : Teil() {
        override val festeBreite: Int get() = if (min == max && vorzeichen == SignStyle.NOT_NEGATIVE) min else -1

        override fun ausgeben(ctx: Ausgabe, sb: StringBuilder) {
            val wert = ctx.wert(feld)
            val ziffern = if (wert == Long.MIN_VALUE) "9223372036854775808" else kotlin.math.abs(wert).toString()
            if (ziffern.length > max) throw DateTimeException("Field $feld cannot be printed as the value $wert exceeds the maximum print width of $max")
            if (wert >= 0) {
                when (vorzeichen) {
                    SignStyle.EXCEEDS_PAD -> if (min < 19 && wert >= zehnHoch(min)) sb.append('+')
                    SignStyle.ALWAYS -> sb.append('+')
                    else -> {}
                }
            } else {
                when (vorzeichen) {
                    SignStyle.NORMAL, SignStyle.EXCEEDS_PAD, SignStyle.ALWAYS -> sb.append('-')
                    SignStyle.NOT_NEGATIVE -> throw DateTimeException("Field $feld cannot be printed as the value $wert cannot be negative according to the SignStyle")
                    SignStyle.NEVER -> {}
                }
            }
            repeat(min - ziffern.length) { sb.append('0') }
            sb.append(ziffern)
        }

        private fun vorzeichenErlaubt(positiv: Boolean): Boolean = when (vorzeichen) {
            SignStyle.NORMAL -> !positiv
            SignStyle.ALWAYS, SignStyle.EXCEEDS_PAD -> true
            else -> false
        }

        override fun lesen(ctx: Eingabe, pos: Int, reserviert: Int): Int {
            val text = ctx.text
            if (pos >= text.length) return pos.inv()
            var p = pos
            var negativ = false
            val zeichen = text[p]
            if (zeichen == '+' || zeichen == '-') {
                if (!vorzeichenErlaubt(zeichen == '+')) return pos.inv()
                negativ = zeichen == '-'
                p++
            } else if (vorzeichen == SignStyle.ALWAYS) return pos.inv()
            var lauf = 0
            while (p + lauf < text.length && text[p + lauf] in '0'..'9') lauf++
            // angrenzende Zahlen fester Breite (yyyyMMdd): deren Ziffern bleiben fuer sie
            val stellen = maxOf(min, minOf(max, lauf - reserviert))
            if (lauf < stellen) return p.inv()
            var wert = 0L
            for (i in 0 until stellen) {
                if (i >= 18 && wert > (Long.MAX_VALUE - 9) / 10) return p.inv()
                wert = wert * 10 + (text[p + i] - '0')
            }
            if (negativ) {
                if (wert == 0L) return (p - 1).inv()
                wert = -wert
            } else if (vorzeichen == SignStyle.EXCEEDS_PAD) {
                if (zeichen == '+' && stellen <= min) return (p - 1).inv()
                if (zeichen != '+' && stellen > min) return p.inv()
            }
            return ctx.setze(feld, wert, pos, p + stellen)
        }
    }

    /** Zweistelliges Jahr (yy/uu), Basis 2000 wie in Java. */
    class Reduziert(val feld: ChronoField) : Teil() {
        override val festeBreite: Int get() = 2
        override fun ausgeben(ctx: Ausgabe, sb: StringBuilder) {
            val wert = kotlin.math.abs(ctx.wert(feld)) % 100
            sb.append(wert.toString().padStart(2, '0'))
        }
        override fun lesen(ctx: Eingabe, pos: Int, reserviert: Int): Int {
            val text = ctx.text
            if (pos + 2 > text.length || text[pos] !in '0'..'9' || text[pos + 1] !in '0'..'9') return pos.inv()
            val wert = (text[pos] - '0') * 10 + (text[pos + 1] - '0')
            return ctx.setze(feld, 2000L + wert, pos, pos + 2)
        }
    }

    class Text(val feld: ChronoField, val stil: TextStyle) : Teil() {
        private fun name(wert: Int, locale: Locale): String = when (feld) {
            ChronoField.MONTH_OF_YEAR -> Namen.monat(wert, stil, locale)
            ChronoField.DAY_OF_WEEK -> Namen.wochentag(wert, stil, locale)
            ChronoField.AMPM_OF_DAY -> Namen.ampm(wert, stil, locale)
            ChronoField.ERA -> Namen.aera(wert, stil, locale)
            else -> wert.toString()
        }

        private val werte: IntRange get() = when (feld) {
            ChronoField.MONTH_OF_YEAR -> 1..12
            ChronoField.DAY_OF_WEEK -> 1..7
            else -> 0..1
        }

        override fun ausgeben(ctx: Ausgabe, sb: StringBuilder) {
            sb.append(name(ctx.wert(feld).toInt(), ctx.locale))
        }

        override fun lesen(ctx: Eingabe, pos: Int, reserviert: Int): Int {
            val kandidaten = werte.map { it to name(it, ctx.locale) }.sortedByDescending { it.second.length }
            for ((wert, n) in kandidaten) {
                if (n.isNotEmpty() && ctx.gleich(pos, n)) return ctx.setze(feld, wert.toLong(), pos, pos + n.length)
            }
            return pos.inv()
        }
    }

    /** Sekundenbruchteil (NANO_OF_SECOND), [min] bis [max] Ziffern, mit oder ohne Dezimalpunkt. */
    class Bruch(val min: Int, val max: Int, val punkt: Boolean) : Teil() {
        override val festeBreite: Int get() = if (!punkt && min == max) min else -1

        override fun ausgeben(ctx: Ausgabe, sb: StringBuilder) {
            val nano = ctx.wert(ChronoField.NANO_OF_SECOND).toInt()
            val ziffern = nano.toString().padStart(9, '0').trimEnd('0')
            if (ziffern.isEmpty()) {
                if (min > 0) {
                    if (punkt) sb.append('.')
                    repeat(min) { sb.append('0') }
                }
            } else {
                val n = minOf(maxOf(ziffern.length, min), max)
                if (punkt) sb.append('.')
                sb.append(ziffern.padEnd(n, '0').substring(0, n))
            }
        }

        override fun lesen(ctx: Eingabe, pos: Int, reserviert: Int): Int {
            val text = ctx.text
            var p = pos
            if (punkt) {
                if (p >= text.length || text[p] != '.') return if (min > 0) pos.inv() else pos
                p++
            }
            var n = 0
            var wert = 0L
            while (n < max && p + n < text.length && text[p + n] in '0'..'9') {
                wert = wert * 10 + (text[p + n] - '0')
                n++
            }
            if (n < min || (punkt && n == 0)) return (p + n).inv()
            val nano = wert * zehnHoch(9 - n)
            return ctx.setze(ChronoField.NANO_OF_SECOND, nano, pos, p + n)
        }
    }

    /** Versatz nach Java-Muster ("+HH:MM:ss" usw.), [ohne] fuer Versatz 0. */
    class Versatz(val muster: String, val ohne: String) : Teil() {
        private val typ = listOf("+HH", "+HHmm", "+HH:mm", "+HHMM", "+HH:MM", "+HHMMss", "+HH:MM:ss", "+HHMMSS", "+HH:MM:SS", "+HHmmss", "+HH:mm:ss")
            .indexOf(muster).also { require(it >= 0) { "Invalid zone offset pattern: $muster" } }
        private val doppelpunkt = muster.contains(':')

        override fun ausgeben(ctx: Ausgabe, sb: StringBuilder) {
            val gesamt = ctx.versatz() ?: return
            sb.append(text(gesamt))
        }

        fun text(gesamt: Int): String {
            if (gesamt == 0) return ohne
            val h = kotlin.math.abs((gesamt / 3600) % 100)
            val m = kotlin.math.abs((gesamt / 60) % 60)
            val s = kotlin.math.abs(gesamt % 60)
            val sb = StringBuilder(if (gesamt < 0) "-" else "+")
            sb.append(h.toString().padStart(2, '0'))
            var summe = h
            if ((typ in 3..8) || (typ >= 9 && s > 0) || (typ >= 1 && m > 0)) {
                if (doppelpunkt) sb.append(':')
                sb.append(m.toString().padStart(2, '0'))
                summe += m
                if (typ == 7 || typ == 8 || (typ >= 5 && s > 0)) {
                    if (doppelpunkt) sb.append(':')
                    sb.append(s.toString().padStart(2, '0'))
                    summe += s
                }
            }
            return if (summe == 0) ohne else sb.toString()
        }

        override fun lesen(ctx: Eingabe, pos: Int, reserviert: Int): Int {
            val text = ctx.text
            if (ohne.isNotEmpty() && ctx.gleich(pos, ohne)) return ctx.setze(ChronoField.OFFSET_SECONDS, 0, pos, pos + ohne.length)
            if (pos >= text.length) return pos.inv()
            val zeichen = text[pos]
            if (zeichen != '+' && zeichen != '-') return if (ohne.isEmpty()) ctx.setze(ChronoField.OFFSET_SECONDS, 0, pos, pos) else pos.inv()
            val vz = if (zeichen == '-') -1 else 1
            var p = pos + 1
            fun zwei(mitTrenner: Boolean): Int {
                var q = p
                if (mitTrenner) { if (q >= text.length || text[q] != ':') return -1; q++ }
                if (q + 2 > text.length || text[q] !in '0'..'9' || text[q + 1] !in '0'..'9') return -1
                p = q + 2
                return (text[q] - '0') * 10 + (text[q + 1] - '0')
            }
            val h = zwei(false)
            if (h < 0 || h > 18) return pos.inv()
            var m = 0
            var s = 0
            val minutenPflicht = typ in 3..8
            val minutenMoeglich = typ >= 1
            if (minutenMoeglich) {
                val vorher = p
                val v = zwei(doppelpunkt)
                if (v >= 0) m = v else { p = vorher; if (minutenPflicht) return pos.inv() }
                if (v >= 0 && typ >= 5) {
                    val vor2 = p
                    val w = zwei(doppelpunkt)
                    if (w >= 0) s = w else { p = vor2; if (typ == 7 || typ == 8) return pos.inv() }
                }
            }
            if (m > 59 || s > 59) return pos.inv()
            val gesamt = vz * (h * 3600 + m * 60 + s)
            if (kotlin.math.abs(gesamt) > 18 * 3600) return pos.inv()
            return ctx.setze(ChronoField.OFFSET_SECONDS, gesamt.toLong(), pos, p)
        }
    }

    /** Lokalisierter Versatz (O, ZZZZ): GMT, GMT+2, GMT+02:00. */
    class VersatzGmt(val voll: Boolean) : Teil() {
        override fun ausgeben(ctx: Ausgabe, sb: StringBuilder) {
            val gesamt = ctx.versatz() ?: return
            sb.append("GMT")
            if (gesamt == 0) return
            val h = kotlin.math.abs(gesamt / 3600)
            val m = kotlin.math.abs((gesamt / 60) % 60)
            val s = kotlin.math.abs(gesamt % 60)
            sb.append(if (gesamt < 0) '-' else '+')
            if (voll) {
                sb.append(h.toString().padStart(2, '0')).append(':').append(m.toString().padStart(2, '0'))
                if (s != 0) sb.append(':').append(s.toString().padStart(2, '0'))
            } else {
                sb.append(h)
                if (m != 0 || s != 0) {
                    sb.append(':').append(m.toString().padStart(2, '0'))
                    if (s != 0) sb.append(':').append(s.toString().padStart(2, '0'))
                }
            }
        }

        override fun lesen(ctx: Eingabe, pos: Int, reserviert: Int): Int {
            if (!ctx.gleich(pos, "GMT")) return pos.inv()
            var p = pos + 3
            val text = ctx.text
            if (p >= text.length || (text[p] != '+' && text[p] != '-')) return ctx.setze(ChronoField.OFFSET_SECONDS, 0, pos, p)
            val vz = if (text[p] == '-') -1 else 1
            p++
            var h = 0
            var n = 0
            while (n < 2 && p < text.length && text[p] in '0'..'9') { h = h * 10 + (text[p] - '0'); p++; n++ }
            if (n == 0 || (voll && n != 2)) return pos.inv()
            var m = 0
            var s = 0
            fun zweiNachDoppelpunkt(q: Int): Int =
                if (q + 3 <= text.length && text[q] == ':' && text[q + 1] in '0'..'9' && text[q + 2] in '0'..'9')
                    (text[q + 1] - '0') * 10 + (text[q + 2] - '0') else -1
            val mm = zweiNachDoppelpunkt(p)
            if (mm >= 0) {
                m = mm
                p += 3
                val ss = zweiNachDoppelpunkt(p)
                if (ss >= 0) { s = ss; p += 3 }
            } else if (voll) return pos.inv()
            if (h > 18 || m > 59 || s > 59) return pos.inv()
            return ctx.setze(ChronoField.OFFSET_SECONDS, (vz * (h * 3600 + m * 60 + s)).toLong(), pos, p)
        }
    }

    /** Zonenkennung (VV, oder [Region] in ISO_ZONED_DATE_TIME). */
    class ZonenId(val nurRegion: Boolean) : Teil() {
        override fun ausgeben(ctx: Ausgabe, sb: StringBuilder) {
            val zone: ZoneId? = when (val t = ctx.temporal) {
                is ZonedDateTime -> t.zone
                is OffsetDateTime -> t.offset
                is ZoneId -> t
                else -> null
            }
            if (zone == null || (nurRegion && zone is ZoneOffset)) {
                if (ctx.optional > 0) throw Ausgabe.Fehlt()
                throw DateTimeException("Unable to extract ZoneId from temporal ${ctx.temporal}")
            }
            sb.append(zone.id)
        }

        override fun lesen(ctx: Eingabe, pos: Int, reserviert: Int): Int {
            val text = ctx.text
            var p = pos
            while (p < text.length && (text[p].isLetterOrDigit() || text[p] in "~/._+-:")) p++
            if (p == pos) return pos.inv()
            // laengste gueltige Kennung
            var ende = p
            while (ende > pos) {
                val kandidat = text.substring(pos, ende)
                val zone = runCatching { ZoneId.of(kandidat) }.getOrNull()
                if (zone != null) {
                    ctx.ergebnis.zone = zone
                    return ende
                }
                ende--
            }
            return pos.inv()
        }
    }

    class Optional(val teile: List<Teil>) : Teil() {
        override fun ausgeben(ctx: Ausgabe, sb: StringBuilder) {
            val teil = StringBuilder()
            ctx.optional++
            try {
                for (t in teile) t.ausgeben(ctx, teil)
                sb.append(teil)
            } catch (_: Ausgabe.Fehlt) {
            } finally {
                ctx.optional--
            }
        }

        override fun lesen(ctx: Eingabe, pos: Int, reserviert: Int): Int {
            val gesichert = ctx.ergebnis.kopie()
            val gk = ctx.grossKleinEgal
            val r = alleLesen(teile, ctx, pos)
            if (r < 0) {
                ctx.ergebnis = gesichert
                ctx.grossKleinEgal = gk
                return pos
            }
            ctx.grossKleinEgal = gk
            return r
        }
    }

    /** ISO_INSTANT: 2026-10-15T12:00:00Z mit Bruchteilen in Dreiergruppen. */
    object Zeitpunkt : Teil() {
        override fun ausgeben(ctx: Ausgabe, sb: StringBuilder) {
            val sek = ctx.wert(ChronoField.INSTANT_SECONDS)
            val nano = if (ctx.temporal.isSupported(ChronoField.NANO_OF_SECOND)) ctx.temporal.getLong(ChronoField.NANO_OF_SECOND).toInt() else 0
            val ldt = LocalDateTime.ofEpochSecond(sek, 0, ZoneOffset.UTC)
            sb.append(ldt.toString())
            if (ldt.second == 0) sb.append(":00")
            if (nano > 0) {
                sb.append('.')
                var rest = nano
                var teiler = 100_000_000
                var i = 0
                while (rest > 0 || i % 3 != 0) {
                    val ziffer = rest / teiler
                    sb.append('0' + ziffer)
                    rest -= ziffer * teiler
                    teiler /= 10
                    i++
                }
            }
            sb.append('Z')
        }

        private val LESER = DATUM_ZEIT_STRENG

        override fun lesen(ctx: Eingabe, pos: Int, reserviert: Int): Int {
            val innen = Eingabe(ctx.text, ctx.locale, true)
            val r = alleLesen(LESER, innen, pos)
            if (r < 0) return r
            val f = innen.ergebnis.felder
            var tage = 0L
            var stunde = f[ChronoField.HOUR_OF_DAY]!!.toInt()
            val minute = f[ChronoField.MINUTE_OF_HOUR]!!.toInt()
            var sekunde = f[ChronoField.SECOND_OF_MINUTE]!!.toInt()
            val nano = f[ChronoField.NANO_OF_SECOND] ?: 0L
            if (stunde == 24 && minute == 0 && sekunde == 0 && nano == 0L) { stunde = 0; tage = 1 }
            else if (stunde == 23 && minute == 59 && sekunde == 60) sekunde = 59
            val versatz = f[ChronoField.OFFSET_SECONDS]!!.toInt()
            val sek = try {
                val datum = LocalDate.of(f[ChronoField.YEAR]!!.toInt(), f[ChronoField.MONTH_OF_YEAR]!!.toInt(), f[ChronoField.DAY_OF_MONTH]!!.toInt())
                LocalDateTime.of(datum.plusDays(tage), LocalTime.of(stunde, minute, sekunde)).toEpochSecond(ZoneOffset.ofTotalSeconds(versatz))
            } catch (e: DateTimeException) {
                return pos.inv()
            }
            val a = ctx.setze(ChronoField.INSTANT_SECONDS, sek, pos, r)
            if (a < 0) return a
            return ctx.setze(ChronoField.NANO_OF_SECOND, nano, pos, r)
        }
    }

    /** RFC 1123 wie DateTimeFormatter.RFC_1123_DATE_TIME (nachsichtig beim Lesen, Gross/Klein egal). */
    object Rfc1123 : Teil() {
        private val TAGE = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        private val MONATE = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

        override fun ausgeben(ctx: Ausgabe, sb: StringBuilder) {
            val tag = ctx.wert(ChronoField.DAY_OF_WEEK).toInt()
            sb.append(TAGE[tag - 1]).append(", ")
            sb.append(ctx.wert(ChronoField.DAY_OF_MONTH)).append(' ')
            sb.append(MONATE[ctx.wert(ChronoField.MONTH_OF_YEAR).toInt() - 1]).append(' ')
            val jahr = ctx.wert(ChronoField.YEAR)
            if (jahr < 0 || jahr > 9999) throw DateTimeException("Field Year cannot be printed as the value $jahr exceeds the maximum print width of 4")
            sb.append(jahr.toString().padStart(4, '0')).append(' ')
            sb.append(ctx.wert(ChronoField.HOUR_OF_DAY).toString().padStart(2, '0')).append(':')
            sb.append(ctx.wert(ChronoField.MINUTE_OF_HOUR).toString().padStart(2, '0')).append(':')
            sb.append(ctx.wert(ChronoField.SECOND_OF_MINUTE).toString().padStart(2, '0')).append(' ')
            val v = ctx.versatz() ?: 0
            sb.append(Versatz("+HHMM", "GMT").text(v))
        }

        override fun lesen(ctx: Eingabe, pos: Int, reserviert: Int): Int {
            val text = ctx.text
            var p = pos
            fun zahl(maxStellen: Int): Long? {
                var n = 0
                var w = 0L
                while (n < maxStellen && p < text.length && text[p] in '0'..'9') { w = w * 10 + (text[p] - '0'); p++; n++ }
                return if (n == 0) null else w
            }
            fun literal(s: String): Boolean = if (ctx.gleich(p, s)) { p += s.length; true } else false
            // optional: Wochentag und Komma
            for ((i, t) in TAGE.withIndex()) {
                if (ctx.gleich(p, t) && ctx.gleich(p + 3, ", ")) {
                    val r = ctx.setze(ChronoField.DAY_OF_WEEK, (i + 1).toLong(), p, p + 5)
                    if (r < 0) return r
                    p += 5
                    break
                }
            }
            val tag = zahl(9) ?: return p.inv()
            if (!literal(" ")) return p.inv()
            val monat = MONATE.indexOfFirst { ctx.gleich(p, it) }
            if (monat < 0) return p.inv()
            p += 3
            if (!literal(" ")) return p.inv()
            val jahr = zahl(9) ?: return p.inv()
            if (!literal(" ")) return p.inv()
            val stunde = zahl(9) ?: return p.inv()
            if (!literal(":")) return p.inv()
            val minute = zahl(9) ?: return p.inv()
            var sekunde = 0L
            var mitSekunde = false
            if (literal(":")) { sekunde = zahl(9) ?: return p.inv(); mitSekunde = true }
            if (!literal(" ")) return p.inv()
            val versatzStart = p
            val versatz: Long
            if (literal("GMT")) versatz = 0
            else {
                if (p >= text.length || (text[p] != '+' && text[p] != '-')) return p.inv()
                val vz = if (text[p] == '-') -1 else 1
                p++
                val start = p
                val ziffern = zahl(9) ?: return p.inv()
                val anzahl = p - start
                var h: Long
                var m = 0L
                if (anzahl <= 2) {
                    h = ziffern
                    if (literal(":")) m = zahl(2) ?: return p.inv()
                } else if (anzahl == 4) { h = ziffern / 100; m = ziffern % 100 }
                else if (anzahl == 6) { h = ziffern / 10000; m = ziffern / 100 % 100 }
                else return versatzStart.inv()
                if (h > 18 || m > 59) return versatzStart.inv()
                versatz = vz * (h * 3600 + m * 60)
            }
            val felder = listOf(
                ChronoField.DAY_OF_MONTH to tag, ChronoField.MONTH_OF_YEAR to (monat + 1).toLong(), ChronoField.YEAR to jahr,
                ChronoField.HOUR_OF_DAY to stunde, ChronoField.MINUTE_OF_HOUR to minute, ChronoField.OFFSET_SECONDS to versatz,
            ) + (if (mitSekunde) listOf(ChronoField.SECOND_OF_MINUTE to sekunde) else emptyList())
            for ((f, w) in felder) {
                val r = ctx.setze(f, w, pos, p)
                if (r < 0) return r
            }
            return p
        }
    }
}

/** Datum T Zeit mit Sekunden und Versatz, fuer ISO_INSTANT. */
private val DATUM_ZEIT_STRENG: List<Teil> = listOf(
    Teil.Zahl(ChronoField.YEAR, 4, 10, SignStyle.EXCEEDS_PAD), Teil.Literal("-"),
    Teil.Zahl(ChronoField.MONTH_OF_YEAR, 2, 2, SignStyle.NOT_NEGATIVE), Teil.Literal("-"),
    Teil.Zahl(ChronoField.DAY_OF_MONTH, 2, 2, SignStyle.NOT_NEGATIVE), Teil.Literal("T"),
    Teil.Zahl(ChronoField.HOUR_OF_DAY, 2, 2, SignStyle.NOT_NEGATIVE), Teil.Literal(":"),
    Teil.Zahl(ChronoField.MINUTE_OF_HOUR, 2, 2, SignStyle.NOT_NEGATIVE), Teil.Literal(":"),
    Teil.Zahl(ChronoField.SECOND_OF_MINUTE, 2, 2, SignStyle.NOT_NEGATIVE),
    Teil.Bruch(0, 9, true),
    Teil.Versatz("+HH:MM:ss", "Z"),
)

// ================================================================ Muster zerlegen

internal object Muster {
    fun zerlegen(muster: String): List<Teil> {
        val stapel = ArrayList<ArrayList<Teil>>()
        stapel.add(ArrayList())
        var i = 0
        while (i < muster.length) {
            val c = muster[i]
            when {
                c in 'A'..'Z' || c in 'a'..'z' -> {
                    var n = 1
                    while (i + n < muster.length && muster[i + n] == c) n++
                    stapel.last() += feld(c, n, muster)
                    i += n
                }
                c == '\'' -> {
                    val start = i++
                    val sb = StringBuilder()
                    var zu = false
                    while (i < muster.length) {
                        if (muster[i] == '\'') {
                            if (i + 1 < muster.length && muster[i + 1] == '\'') { sb.append('\''); i += 2; continue }
                            zu = true
                            i++
                            break
                        }
                        sb.append(muster[i++])
                    }
                    if (!zu) throw IllegalArgumentException("Pattern ends with an incomplete string literal: $muster")
                    if (i - start == 2) stapel.last() += Teil.Literal("'") else if (sb.isNotEmpty()) stapel.last() += Teil.Literal(sb.toString())
                }
                c == '[' -> { stapel.add(ArrayList()); i++ }
                c == ']' -> {
                    if (stapel.size == 1) throw IllegalArgumentException("Pattern invalid as it contains ] without previous [")
                    val innen = stapel.removeAt(stapel.size - 1)
                    stapel.last() += Teil.Optional(innen)
                    i++
                }
                c == '{' || c == '}' || c == '#' -> throw IllegalArgumentException("Pattern includes reserved character: '$c'")
                else -> { stapel.last() += Teil.Literal(c.toString()); i++ }
            }
        }
        while (stapel.size > 1) {
            val innen = stapel.removeAt(stapel.size - 1)
            stapel.last() += Teil.Optional(innen)
        }
        return stapel[0]
    }

    private fun zuViele(c: Char): Nothing = throw IllegalArgumentException("Too many pattern letters: $c")

    private fun feld(c: Char, n: Int, muster: String): Teil = when (c) {
        'u', 'y' -> {
            val f = if (c == 'u') ChronoField.YEAR else ChronoField.YEAR_OF_ERA
            when {
                n == 2 -> Teil.Reduziert(f)
                n < 4 -> Teil.Zahl(f, n, 19, SignStyle.NORMAL)
                else -> Teil.Zahl(f, n, 19, SignStyle.EXCEEDS_PAD)
            }
        }
        'M', 'L' -> when (n) {
            1 -> Teil.Zahl(ChronoField.MONTH_OF_YEAR, 1, 19, SignStyle.NORMAL)
            2 -> Teil.Zahl(ChronoField.MONTH_OF_YEAR, 2, 2, SignStyle.NOT_NEGATIVE)
            3 -> Teil.Text(ChronoField.MONTH_OF_YEAR, if (c == 'L') TextStyle.SHORT_STANDALONE else TextStyle.SHORT)
            4 -> Teil.Text(ChronoField.MONTH_OF_YEAR, if (c == 'L') TextStyle.FULL_STANDALONE else TextStyle.FULL)
            5 -> Teil.Text(ChronoField.MONTH_OF_YEAR, if (c == 'L') TextStyle.NARROW_STANDALONE else TextStyle.NARROW)
            else -> zuViele(c)
        }
        'd', 'h', 'H', 'k', 'K', 'm', 's' -> {
            val f = when (c) {
                'd' -> ChronoField.DAY_OF_MONTH
                'h' -> ChronoField.CLOCK_HOUR_OF_AMPM
                'H' -> ChronoField.HOUR_OF_DAY
                'k' -> ChronoField.CLOCK_HOUR_OF_DAY
                'K' -> ChronoField.HOUR_OF_AMPM
                'm' -> ChronoField.MINUTE_OF_HOUR
                else -> ChronoField.SECOND_OF_MINUTE
            }
            when (n) {
                1 -> Teil.Zahl(f, 1, 19, SignStyle.NORMAL)
                2 -> Teil.Zahl(f, 2, 2, SignStyle.NOT_NEGATIVE)
                else -> zuViele(c)
            }
        }
        'D' -> when (n) {
            1 -> Teil.Zahl(ChronoField.DAY_OF_YEAR, 1, 19, SignStyle.NORMAL)
            2 -> Teil.Zahl(ChronoField.DAY_OF_YEAR, 2, 3, SignStyle.NOT_NEGATIVE)
            3 -> Teil.Zahl(ChronoField.DAY_OF_YEAR, 3, 3, SignStyle.NOT_NEGATIVE)
            else -> zuViele(c)
        }
        'E' -> when (n) {
            1, 2, 3 -> Teil.Text(ChronoField.DAY_OF_WEEK, TextStyle.SHORT)
            4 -> Teil.Text(ChronoField.DAY_OF_WEEK, TextStyle.FULL)
            5 -> Teil.Text(ChronoField.DAY_OF_WEEK, TextStyle.NARROW)
            else -> zuViele(c)
        }
        'a' -> if (n == 1) Teil.Text(ChronoField.AMPM_OF_DAY, TextStyle.SHORT) else zuViele(c)
        'G' -> when (n) {
            1, 2, 3 -> Teil.Text(ChronoField.ERA, TextStyle.SHORT)
            4 -> Teil.Text(ChronoField.ERA, TextStyle.FULL)
            5 -> Teil.Text(ChronoField.ERA, TextStyle.NARROW)
            else -> zuViele(c)
        }
        'S' -> Teil.Bruch(n, n, false)
        'V' -> if (n == 2) Teil.ZonenId(nurRegion = false) else throw IllegalArgumentException("Pattern letter count must be 2: $c")
        'Z' -> when (n) {
            1, 2, 3 -> Teil.Versatz("+HHMM", "+0000")
            4 -> Teil.VersatzGmt(true)
            5 -> Teil.Versatz("+HH:MM:ss", "Z")
            else -> zuViele(c)
        }
        'X', 'x' -> {
            val ohne = if (c == 'X') "Z" else null
            when (n) {
                1 -> Teil.Versatz("+HHmm", ohne ?: "+00")
                2 -> Teil.Versatz("+HHMM", ohne ?: "+0000")
                3 -> Teil.Versatz("+HH:MM", ohne ?: "+00:00")
                4 -> Teil.Versatz("+HHMMss", ohne ?: "+0000")
                5 -> Teil.Versatz("+HH:MM:ss", ohne ?: "+00:00")
                else -> zuViele(c)
            }
        }
        'O' -> when (n) {
            1 -> Teil.VersatzGmt(false)
            4 -> Teil.VersatzGmt(true)
            else -> throw IllegalArgumentException("Pattern letter count must be 1 or 4: $c")
        }
        'z', 'v', 'Q', 'q', 'w', 'W', 'Y', 'e', 'c', 'F', 'g', 'B', 'n', 'N', 'A', 'p' ->
            throw IllegalArgumentException("Pattern letter '$c' is not supported by this java.time replacement: $muster")
        else -> throw IllegalArgumentException("Unknown pattern letter: $c")
    }
}

// ================================================================ Ergebnis des Lesens

/** Gelesene Felder; nach [aufloesen] mit Datum, Uhrzeit, Versatz und Zone. */
internal class Geparst : TemporalAccessor {
    val felder = HashMap<ChronoField, Long>()
    var zone: ZoneId? = null
    var datum: LocalDate? = null
    var zeit: LocalTime? = null

    fun kopie(): Geparst = Geparst().also { it.felder.putAll(felder); it.zone = zone }

    fun zoneOderVersatz(): ZoneId? = zone ?: felder[ChronoField.OFFSET_SECONDS]?.let { ZoneOffset.ofTotalSeconds(it.toInt()) }

    fun aufloesen(stil: ResolverStyle): Geparst {
        val streng = stil == ResolverStyle.STRICT
        val f = felder
        // Jahr aus Jahr der Aera
        val yoe = f[ChronoField.YEAR_OF_ERA]
        if (yoe != null) {
            ChronoField.YEAR_OF_ERA.checkValidValue(yoe)
            val aera = f[ChronoField.ERA]
            val jahr = when {
                aera != null -> if (ChronoField.ERA.checkValidValue(aera) == 1L) yoe else 1 - yoe
                !streng -> yoe
                else -> null
            }
            if (jahr != null) {
                val alt = f[ChronoField.YEAR]
                if (alt != null && alt != jahr) throw DateTimeException("Conflict found: Year $alt differs from Year $jahr")
                f[ChronoField.YEAR] = jahr
            }
        }
        // Datum
        val jahr = f[ChronoField.YEAR]
        val monat = f[ChronoField.MONTH_OF_YEAR]
        val tag = f[ChronoField.DAY_OF_MONTH]
        val tagImJahr = f[ChronoField.DAY_OF_YEAR]
        val epochenTag = f[ChronoField.EPOCH_DAY]
        datum = when {
            jahr != null && monat != null && tag != null -> {
                val y = ChronoField.YEAR.checkValidIntValue(jahr)
                val m = ChronoField.MONTH_OF_YEAR.checkValidIntValue(monat)
                var d = ChronoField.DAY_OF_MONTH.checkValidIntValue(tag)
                if (stil == ResolverStyle.SMART) {
                    if (m == 4 || m == 6 || m == 9 || m == 11) d = minOf(d, 30)
                    else if (m == 2) d = minOf(d, if (java.time.Year.isLeap(y.toLong())) 29 else 28)
                }
                LocalDate.of(y, m, d)
            }
            jahr != null && tagImJahr != null -> LocalDate.ofYearDay(ChronoField.YEAR.checkValidIntValue(jahr), ChronoField.DAY_OF_YEAR.checkValidIntValue(tagImJahr))
            epochenTag != null -> LocalDate.ofEpochDay(epochenTag)
            else -> null
        }
        // Uhrzeit
        var stunde = f[ChronoField.HOUR_OF_DAY]
        f[ChronoField.CLOCK_HOUR_OF_DAY]?.let { k ->
            ChronoField.CLOCK_HOUR_OF_DAY.checkValidValue(k)
            stunde = if (k == 24L) 0L else k
        }
        var halbtagStunde = f[ChronoField.HOUR_OF_AMPM]
        f[ChronoField.CLOCK_HOUR_OF_AMPM]?.let { h ->
            ChronoField.CLOCK_HOUR_OF_AMPM.checkValidValue(h)
            halbtagStunde = if (h == 12L) 0L else h
        }
        val ampm = f[ChronoField.AMPM_OF_DAY]
        if (stunde == null && ampm != null && halbtagStunde != null) {
            stunde = ChronoField.AMPM_OF_DAY.checkValidValue(ampm) * 12 + ChronoField.HOUR_OF_AMPM.checkValidValue(halbtagStunde!!)
        }
        var extraTag = 0L
        val h = stunde
        if (h != null) {
            val mi = f[ChronoField.MINUTE_OF_HOUR]
            val s = f[ChronoField.SECOND_OF_MINUTE]
            val n = f[ChronoField.NANO_OF_SECOND]
            if ((mi == null && (s != null || n != null)) || (mi != null && s == null && n != null)) {
                // Luecken in der Zeit: nicht aufloesbar
            } else if (stil == ResolverStyle.SMART && h == 24L && (mi ?: 0L) == 0L && (s ?: 0L) == 0L && (n ?: 0L) == 0L) {
                zeit = LocalTime.MIDNIGHT
                extraTag = 1
            } else {
                zeit = LocalTime.of(
                    ChronoField.HOUR_OF_DAY.checkValidIntValue(h),
                    ChronoField.MINUTE_OF_HOUR.checkValidIntValue(mi ?: 0L),
                    ChronoField.SECOND_OF_MINUTE.checkValidIntValue(s ?: 0L),
                    ChronoField.NANO_OF_SECOND.checkValidIntValue(n ?: 0L),
                )
            }
        }
        if (extraTag != 0L) datum = datum?.plusDays(extraTag)
        // Gegenprobe Wochentag
        val dow = f[ChronoField.DAY_OF_WEEK]
        val d = datum
        if (dow != null && d != null) {
            ChronoField.DAY_OF_WEEK.checkValidValue(dow)
            if (d.dayOfWeek.value.toLong() != dow) {
                throw DateTimeException("Conflict found: Field DayOfWeek ${d.dayOfWeek.value} differs from DayOfWeek $dow derived from $d")
            }
        }
        return this
    }

    private fun zeitpunkt(): Long? {
        f()[ChronoField.INSTANT_SECONDS]?.let { return it }
        val d = datum ?: return null
        val t = zeit ?: return null
        val versatz = felder[ChronoField.OFFSET_SECONDS]
        if (versatz != null) return LocalDateTime.of(d, t).toEpochSecond(ZoneOffset.ofTotalSeconds(versatz.toInt()))
        val z = zone ?: return null
        return LocalDateTime.of(d, t).atZone(z).toEpochSecond()
    }

    private fun f() = felder

    override fun isSupported(field: TemporalField): Boolean {
        if (field !is ChronoField) return false
        if (felder.containsKey(field)) return true
        if (datum?.isSupported(field) == true) return true
        if (zeit?.isSupported(field) == true) return true
        if (field == ChronoField.INSTANT_SECONDS) return zeitpunkt() != null
        if (field == ChronoField.OFFSET_SECONDS) return zone is ZoneOffset
        return false
    }

    override fun getLong(field: TemporalField): Long {
        val d = datum
        val t = zeit
        if (d != null && d.isSupported(field)) return d.getLong(field)
        if (t != null && t.isSupported(field)) return t.getLong(field)
        if (field == ChronoField.INSTANT_SECONDS) zeitpunkt()?.let { return it }
        felder[field as? ChronoField]?.let { return it }
        if (field == ChronoField.OFFSET_SECONDS) (zone as? ZoneOffset)?.let { return it.totalSeconds.toLong() }
        throw UnsupportedTemporalTypeException("Unsupported field: $field")
    }

    override fun toString(): String = "$felder,${zone ?: ""},${datum ?: ""}${if (zeit != null) "T$zeit" else ""}"
}
