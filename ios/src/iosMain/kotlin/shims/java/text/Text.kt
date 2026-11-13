package java.text

import java.util.Date
import platform.Foundation.compare
import platform.Foundation.create
import platform.Foundation.decomposedStringWithCanonicalMapping
import platform.Foundation.decomposedStringWithCompatibilityMapping
import platform.Foundation.precomposedStringWithCanonicalMapping
import platform.Foundation.precomposedStringWithCompatibilityMapping
import java.util.Locale

/* Ersatz fuer java.text: Normalizer und Collator ueber NSString, SimpleDateFormat fuer RSS-Zeiten. */

open class ParseException(message: String?, val errorOffset: Int) : Exception(message)

object Normalizer {
    enum class Form { NFD, NFC, NFKD, NFKC }

    fun normalize(src: CharSequence, form: Form): String = jsNormalisieren(src.toString(), form.name)

    fun isNormalized(src: CharSequence, form: Form): Boolean = normalize(src, form) == src.toString()
}

private fun jsNormalisieren(s: String, form: String): String {
    val ns = platform.Foundation.NSString.create(string = s)
    return when (form) {
        "NFD" -> ns.decomposedStringWithCanonicalMapping
        "NFKD" -> ns.decomposedStringWithCompatibilityMapping
        "NFKC" -> ns.precomposedStringWithCompatibilityMapping
        else -> ns.precomposedStringWithCanonicalMapping
    }
}

/**
 * Ersatz fuer java.text.Collator ueber den Textvergleich von iOS (NSString.compare mit Sprache). Staerken wie in Java: PRIMARY (nur Grundbuchstaben),
 * SECONDARY (mit Akzenten), TERTIARY (mit Gross/Klein, Vorgabe), IDENTICAL.
 */
abstract class Collator protected constructor() : Comparator<Any?> {

    var strength: Int = TERTIARY
        set(value) {
            require(value in PRIMARY..IDENTICAL) { "Incorrect comparison level." }
            field = value
        }

    var decomposition: Int = NO_DECOMPOSITION

    abstract fun compare(source: String?, target: String?): Int

    override fun compare(a: Any?, b: Any?): Int = compare(a as String?, b as String?)

    fun equals(source: String?, target: String?): Boolean = compare(source, target) == 0

    companion object {
        const val PRIMARY = 0
        const val SECONDARY = 1
        const val TERTIARY = 2
        const val IDENTICAL = 3
        const val NO_DECOMPOSITION = 0
        const val CANONICAL_DECOMPOSITION = 1
        const val FULL_DECOMPOSITION = 2

        fun getInstance(): Collator = getInstance(Locale.getDefault())

        fun getInstance(desiredLocale: Locale): Collator = IntlCollator(desiredLocale.sprachKennungFuerIntl())

        fun getAvailableLocales(): Array<Locale> = Locale.getAvailableLocales()
    }
}

private fun Locale.sprachKennungFuerIntl(): String = if (language.isEmpty()) "en" else toLanguageTag()

private class IntlCollator(private val sprache: String) : Collator() {
    override fun compare(source: String?, target: String?): Int {
        if (source == null || target == null) throw NullPointerException()
        val empfindlich = when (strength) {
            PRIMARY -> "base"
            SECONDARY -> "accent"
            else -> "variant"
        }
        val c = jsVergleichen(sprache, empfindlich, source, target)
        if (c == 0 && strength == IDENTICAL && source != target) return if (source < target) -1 else 1
        return c
    }
}

private fun jsVergleichen(sprache: String, empfindlich: String, a: String, b: String): Int {
    val optionen: ULong = when (empfindlich) {
        "base" -> platform.Foundation.NSCaseInsensitiveSearch or platform.Foundation.NSDiacriticInsensitiveSearch
        "accent" -> platform.Foundation.NSCaseInsensitiveSearch
        else -> 0uL
    }
    val ns = platform.Foundation.NSString.create(string = a)
    val r = ns.compare(b, optionen, platform.Foundation.NSMakeRange(0uL, ns.length), platform.Foundation.NSLocale(localeIdentifier = sprache))
    return if (r < 0) -1 else if (r > 0) 1 else 0
}

/**
 * Ersatz fuer java.text.SimpleDateFormat: Lesen nachsichtig wie in Java (Namen ohne Ruecksicht auf Gross/Klein,
 * kurz oder lang; Zahlen beliebig lang; Zonen als +hhmm, GMT+hh:mm, UTC oder US-Kuerzel). Ohne Zone im Text gilt
 * die Standardzone. Musterbuchstaben: G y M d E a H k K h m s S z Z X und Literale.
 */
open class SimpleDateFormat(private val muster: String, private val locale: Locale) {

    constructor(pattern: String) : this(pattern, Locale.getDefault())

    var isLenient: Boolean = true

    fun toPattern(): String = muster

    private class Stueck(val zeichen: Char, val anzahl: Int, val text: String?)

    private val stuecke: List<Stueck> = zerlegen(muster)

    private fun zerlegen(m: String): List<Stueck> {
        val out = ArrayList<Stueck>()
        var i = 0
        while (i < m.length) {
            val c = m[i]
            when {
                c in 'A'..'Z' || c in 'a'..'z' -> {
                    var n = 1
                    while (i + n < m.length && m[i + n] == c) n++
                    if (c !in "GyYMLdEuaHkKhmsSzZX") throw IllegalArgumentException("Illegal pattern character '$c'")
                    out += Stueck(c, n, null)
                    i += n
                }
                c == '\'' -> {
                    val sb = StringBuilder()
                    i++
                    if (i < m.length && m[i] == '\'') { out += Stueck(' ', 0, "'"); i++; continue }
                    while (i < m.length) {
                        if (m[i] == '\'') {
                            if (i + 1 < m.length && m[i + 1] == '\'') { sb.append('\''); i += 2; continue }
                            i++
                            break
                        }
                        sb.append(m[i++])
                    }
                    out += Stueck(' ', 0, sb.toString())
                }
                else -> { out += Stueck(' ', 0, c.toString()); i++ }
            }
        }
        return out
    }

    private fun namen(feld: Char): List<Pair<String, Int>> {
        return when (feld) {
            'M', 'L' -> (1..12).flatMap { m -> listOf(java.time.format.TextStyle.FULL, java.time.format.TextStyle.SHORT, java.time.format.TextStyle.SHORT_STANDALONE).map { java.time.Month.of(m).getDisplayName(it, locale) to m } }
            'E' -> (1..7).flatMap { d -> listOf(java.time.format.TextStyle.FULL, java.time.format.TextStyle.SHORT, java.time.format.TextStyle.SHORT_STANDALONE).map { java.time.DayOfWeek.of(d).getDisplayName(it, locale) to d } }
            'a' -> listOf("AM" to 0, "PM" to 1)
            'G' -> listOf("AD" to 1, "BC" to 0, "n. Chr." to 1, "v. Chr." to 0)
            else -> emptyList()
        }.sortedByDescending { it.first.length }
    }

    private val ZONEN = mapOf(
        "GMT" to 0, "UTC" to 0, "UT" to 0, "Z" to 0,
        "EST" to -5 * 3600, "EDT" to -4 * 3600, "CST" to -6 * 3600, "CDT" to -5 * 3600,
        "MST" to -7 * 3600, "MDT" to -6 * 3600, "PST" to -8 * 3600, "PDT" to -7 * 3600,
        "CET" to 3600, "CEST" to 7200, "MEZ" to 3600, "MESZ" to 7200, "BST" to 3600, "WET" to 0, "WEST" to 3600,
    )

    fun parse(source: String): Date {
        var p = 0
        var jahr: Long? = null
        var monat = 1L
        var tag = 1L
        var stunde = 0L
        var minute = 0L
        var sekunde = 0L
        var milli = 0L
        var ampm: Int? = null
        var halbtag = false
        var versatz: Int? = null
        var bc = false
        fun fehler(): Nothing = throw ParseException("Unparseable date: \"$source\"", p)
        for ((index, s) in stuecke.withIndex()) {
            if (s.text != null) {
                if (s.text.isBlank()) {
                    // Leerraum im Muster: beliebig viel Leerraum im Text
                    if (p < source.length && !source[p].isWhitespace()) fehler()
                    while (p < source.length && source[p].isWhitespace()) p++
                } else {
                    if (!source.startsWith(s.text, p)) fehler()
                    p += s.text.length
                }
                continue
            }
            val naechste = stuecke.getOrNull(index + 1)
            val naechsteIstZahl = naechste != null && naechste.text == null &&
                (naechste.zeichen in "yYudHkKhmsS" || (naechste.zeichen in "ML" && naechste.anzahl <= 2))
            fun zahl(): Long {
                val start = p
                if (p < source.length && (source[p] == '-' || source[p] == '+') && s.zeichen == 'y') p++
                val maxStellen = if (naechsteIstZahl) s.anzahl else Int.MAX_VALUE
                var n = 0
                while (p < source.length && source[p].isDigit() && n < maxStellen) { p++; n++ }
                if (n == 0) { p = start; fehler() }
                return source.substring(start, p).toLong()
            }
            fun text(): Int {
                for ((n, w) in namen(s.zeichen)) {
                    if (n.isNotEmpty() && source.regionMatches(p, n, 0, n.length, ignoreCase = true)) { p += n.length; return w }
                }
                fehler()
            }
            when (s.zeichen) {
                'y', 'Y', 'u' -> {
                    val start = p
                    val w = zahl()
                    jahr = if (s.anzahl <= 2 && p - start == 2 && w >= 0) {
                        // zweistellig: innerhalb von 80 Jahren vor und 20 nach heute
                        val heute = java.time.Year.now().value
                        val basis = heute - 80
                        var j = (basis / 100) * 100 + w
                        if (j < basis) j += 100
                        j.toLong()
                    } else w
                }
                'M', 'L' -> monat = if (s.anzahl >= 3) text().toLong() else zahl()
                'd' -> tag = zahl()
                'E' -> text()
                'a' -> ampm = text()
                'G' -> bc = text() == 0
                'H' -> stunde = zahl()
                'k' -> stunde = zahl().let { if (it == 24L) 0L else it }
                'K' -> { stunde = zahl(); halbtag = true }
                'h' -> { stunde = zahl().let { if (it == 12L) 0L else it }; halbtag = true }
                'm' -> minute = zahl()
                's' -> sekunde = zahl()
                'S' -> milli = zahl()
                'z', 'Z', 'X' -> versatz = zone(source, p).also { p = it.second }.first ?: fehler()
                else -> fehler()
            }
        }
        if (halbtag && ampm == 1) stunde += 12
        val j = jahr ?: 1970L
        val jahrIso = if (bc) 1 - j else j
        // nachsichtig wie Calendar: Ueberlaeufe wandern in die naechste Einheit
        val datum = java.time.LocalDate.of(jahrIso.toInt(), 1, 1).plusMonths(monat - 1).plusDays(tag - 1)
        val ortszeit = datum.atStartOfDay().plusSeconds(stunde * 3600 + minute * 60 + sekunde).plusNanos(milli * 1_000_000)
        val ms = if (versatz != null) ortszeit.toInstant(java.time.ZoneOffset.ofTotalSeconds(versatz!!)).toEpochMilli()
        else ortszeit.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        return Date(ms)
    }

    /** Zone ab [start]: Versatz in Sekunden (oder null) und neue Position. */
    private fun zone(s: String, start: Int): Pair<Int?, Int> {
        var p = start
        fun versatzZahl(): Int? {
            if (p >= s.length || (s[p] != '+' && s[p] != '-')) return null
            val vz = if (s[p] == '-') -1 else 1
            p++
            val anfang = p
            while (p < s.length && s[p].isDigit()) p++
            val ziffern = s.substring(anfang, p)
            var h: Int
            var m = 0
            when (ziffern.length) {
                1, 2 -> {
                    h = ziffern.toInt()
                    if (p < s.length && s[p] == ':') {
                        val a = p + 1
                        var e = a
                        while (e < s.length && s[e].isDigit() && e - a < 2) e++
                        if (e - a == 2) { m = s.substring(a, e).toInt(); p = e }
                    }
                }
                4 -> { h = ziffern.substring(0, 2).toInt(); m = ziffern.substring(2).toInt() }
                else -> return null
            }
            if (h > 23 || m > 59) return null
            return vz * (h * 3600 + m * 60)
        }
        versatzZahl()?.let { return it to p }
        p = start
        for ((name, wert) in ZONEN.entries.sortedByDescending { it.key.length }) {
            if (s.regionMatches(p, name, 0, name.length, ignoreCase = false)) {
                p += name.length
                if (name == "GMT" || name == "UTC" || name == "UT") {
                    val vor = p
                    versatzZahl()?.let { return it to p }
                    p = vor
                }
                return wert to p
            }
        }
        return null to start
    }

    /** Formatieren in der Standardzone; Muster wie bei DateTimeFormatter (gaengige Buchstaben). */
    fun format(date: Date): String {
        val z = java.time.Instant.ofEpochMilli(date.time).atZone(java.time.ZoneId.systemDefault())
        val sb = StringBuilder()
        for (s in stuecke) {
            if (s.text != null) { sb.append(s.text); continue }
            val n = s.anzahl
            fun zahl(v: Int) = sb.append(v.toString().padStart(n, '0'))
            when (s.zeichen) {
                'y', 'Y', 'u' -> if (n == 2) sb.append((z.year % 100).toString().padStart(2, '0')) else zahl(z.year)
                'M', 'L' -> when {
                    n >= 4 -> sb.append(z.month.getDisplayName(java.time.format.TextStyle.FULL, locale))
                    n == 3 -> sb.append(z.month.getDisplayName(java.time.format.TextStyle.SHORT, locale))
                    else -> zahl(z.monthValue)
                }
                'd' -> zahl(z.dayOfMonth)
                'E' -> sb.append(z.dayOfWeek.getDisplayName(if (n >= 4) java.time.format.TextStyle.FULL else java.time.format.TextStyle.SHORT, locale))
                'a' -> sb.append(if (z.hour < 12) "AM" else "PM")
                'G' -> sb.append(if (z.year >= 1) "AD" else "BC")
                'H' -> zahl(z.hour)
                'k' -> zahl(if (z.hour == 0) 24 else z.hour)
                'K' -> zahl(z.hour % 12)
                'h' -> zahl(if (z.hour % 12 == 0) 12 else z.hour % 12)
                'm' -> zahl(z.minute)
                's' -> zahl(z.second)
                'S' -> zahl(z.nano / 1_000_000)
                'Z', 'X', 'z' -> {
                    val o = z.offset.totalSeconds
                    if (s.zeichen == 'X' && o == 0) sb.append('Z')
                    else {
                        val b = kotlin.math.abs(o)
                        if (s.zeichen == 'z') sb.append("GMT")
                        sb.append(if (o < 0) '-' else '+').append((b / 3600).toString().padStart(2, '0'))
                        if (s.zeichen == 'z' || (s.zeichen == 'X' && n == 3)) sb.append(':')
                        sb.append((b / 60 % 60).toString().padStart(2, '0'))
                    }
                }
            }
        }
        return sb.toString()
    }
}
