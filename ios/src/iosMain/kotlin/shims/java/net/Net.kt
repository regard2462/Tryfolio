package java.net

import java.io.UnsupportedEncodingException
import java.nio.charset.Charset

/* Ersatz fuer java.net.URLEncoder, URLDecoder und URI. */

/** Bytes eines Texts wie String.getBytes: UTF-8 (einzelne Surrogate als '?'), ISO-8859-1/US-ASCII ('?' ausserhalb). */
internal fun textBytes(s: String, charset: Charset): ByteArray = when (charset.name()) {
    "ISO-8859-1", "windows-1252" -> ByteArray(s.length) { i -> s[i].code.let { if (it < 256) it.toByte() else '?'.code.toByte() } }
    "US-ASCII" -> ByteArray(s.length) { i -> s[i].code.let { if (it < 128) it.toByte() else '?'.code.toByte() } }
    "UTF-16BE", "UTF-16" -> {
        val vorsatz = if (charset.name() == "UTF-16") 2 else 0
        ByteArray(vorsatz + s.length * 2) { i ->
            if (i < vorsatz) (if (i == 0) 0xFE else 0xFF).toByte()
            else { val k = i - vorsatz; (s[k / 2].code shr (if (k % 2 == 0) 8 else 0)).toByte() }
        }
    }
    "UTF-16LE" -> ByteArray(s.length * 2) { i -> (s[i / 2].code shr (if (i % 2 == 0) 0 else 8)).toByte() }
    else -> utf8(s)
}

private fun utf8(s: String): ByteArray {
    val out = ArrayList<Byte>(s.length)
    var i = 0
    while (i < s.length) {
        val c = s[i].code
        when {
            c < 0x80 -> out += c.toByte()
            c < 0x800 -> { out += (0xC0 or (c shr 6)).toByte(); out += (0x80 or (c and 0x3F)).toByte() }
            c in 0xD800..0xDBFF && i + 1 < s.length && s[i + 1].code in 0xDC00..0xDFFF -> {
                val cp = 0x10000 + ((c - 0xD800) shl 10) + (s[i + 1].code - 0xDC00)
                out += (0xF0 or (cp shr 18)).toByte()
                out += (0x80 or ((cp shr 12) and 0x3F)).toByte()
                out += (0x80 or ((cp shr 6) and 0x3F)).toByte()
                out += (0x80 or (cp and 0x3F)).toByte()
                i++
            }
            c in 0xD800..0xDFFF -> out += '?'.code.toByte()
            else -> {
                out += (0xE0 or (c shr 12)).toByte()
                out += (0x80 or ((c shr 6) and 0x3F)).toByte()
                out += (0x80 or (c and 0x3F)).toByte()
            }
        }
        i++
    }
    return out.toByteArray()
}

/** Bytes als Text: UTF-8 (ungueltige Folgen als U+FFFD), ISO-8859-1, US-ASCII, UTF-16. */
internal fun bytesText(b: ByteArray, charset: Charset): String = when (charset.name()) {
    "ISO-8859-1", "windows-1252" -> buildString(b.size) { for (x in b) append((x.toInt() and 0xFF).toChar()) }
    "US-ASCII" -> buildString(b.size) { for (x in b) append(if (x >= 0) x.toInt().toChar() else '�') }
    "UTF-16BE", "UTF-16LE", "UTF-16" -> {
        var start = 0
        var gross = charset.name() != "UTF-16LE"
        if (charset.name() == "UTF-16" && b.size >= 2) {
            if (b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte()) start = 2
            else if (b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte()) { start = 2; gross = false }
        }
        buildString {
            var i = start
            while (i + 1 < b.size) {
                val x = b[i].toInt() and 0xFF
                val y = b[i + 1].toInt() and 0xFF
                append((if (gross) (x shl 8) or y else (y shl 8) or x).toChar())
                i += 2
            }
        }
    }
    else -> b.decodeToString()
}

private fun zeichensatz(enc: String): Charset =
    try { Charset.forName(enc) } catch (e: IllegalArgumentException) { throw UnsupportedEncodingException(enc) }

object URLEncoder {
    private const val HEX = "0123456789ABCDEF"

    private fun bleibt(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '.' || c == '-' || c == '*' || c == '_'

    @Deprecated("Wie in Java: besser mit Zeichensatz", ReplaceWith("encode(s, \"UTF-8\")"))
    fun encode(s: String): String = encode(s, Charset.forName("UTF-8"))

    /** Wie Java: Buchstaben, Ziffern und . - * _ bleiben, Leerzeichen wird +, alles andere %XX der Bytes. */
    fun encode(s: String, enc: String): String = encode(s, zeichensatz(enc))

    fun encode(s: String, charset: Charset): String {
        var geaendert = false
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                bleibt(c) -> { sb.append(c); i++ }
                c == ' ' -> { sb.append('+'); geaendert = true; i++ }
                else -> {
                    val start = i
                    while (i < s.length && !bleibt(s[i]) && s[i] != ' ') i++
                    for (b in textBytes(s.substring(start, i), charset)) {
                        val v = b.toInt() and 0xFF
                        sb.append('%').append(HEX[v shr 4]).append(HEX[v and 0xF])
                    }
                    geaendert = true
                }
            }
        }
        return if (geaendert) sb.toString() else s
    }
}

object URLDecoder {
    @Deprecated("Wie in Java: besser mit Zeichensatz", ReplaceWith("decode(s, \"UTF-8\")"))
    fun decode(s: String): String = decode(s, Charset.forName("UTF-8"))

    /** Wie Java: + wird Leerzeichen, %XX-Folgen werden als Bytes im Zeichensatz gelesen. */
    fun decode(s: String, enc: String): String {
        if (enc.isEmpty()) throw UnsupportedEncodingException("URLDecoder: empty string enc parameter")
        return decode(s, zeichensatz(enc))
    }

    fun decode(s: String, charset: Charset): String {
        var geaendert = false
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when (c) {
                '+' -> { sb.append(' '); i++; geaendert = true }
                '%' -> {
                    val bytes = ArrayList<Byte>()
                    while (i < s.length && s[i] == '%') {
                        if (i + 2 >= s.length) throw IllegalArgumentException("URLDecoder: Incomplete trailing escape (%) pattern")
                        val h = s[i + 1].digitToIntOrNull(16)
                        val l = s[i + 2].digitToIntOrNull(16)
                        if (h == null || l == null) throw IllegalArgumentException("URLDecoder: Illegal hex characters in escape (%) pattern - Error at index 0 in: \"${s.substring(i + 1, minOf(i + 3, s.length))}\"")
                        bytes += ((h shl 4) or l).toByte()
                        i += 3
                    }
                    sb.append(bytesText(bytes.toByteArray(), charset))
                    geaendert = true
                }
                else -> { sb.append(c); i++ }
            }
        }
        return if (geaendert) sb.toString() else s
    }
}

open class URISyntaxException(val input: String, val reason: String, val index: Int = -1) :
    Exception(if (index > -1) "$reason at index $index: $input" else "$reason: $input")

/**
 * Ersatz fuer java.net.URI: Zerlegen nach RFC 2396 wie im JDK (mit dessen Fehlern bei unerlaubten Zeichen).
 * Abweichung: [host] und [path] sind nie null, sondern "", wo Java null liefert (kein Rechner, undurchsichtige URI);
 * so passt der App-Code `URI(link).host.removePrefix(...)` wie auf der JVM in runCatching.
 */
class URI(str: String) : Comparable<URI> {

    private val eingabe = str

    var scheme: String? = null
        private set
    var rawSchemeSpecificPart: String = ""
        private set
    var rawAuthority: String? = null
        private set
    var rawUserInfo: String? = null
        private set
    private var hostRoh: String? = null
    var port: Int = -1
        private set
    private var pfadRoh: String? = null
    var rawQuery: String? = null
        private set
    var rawFragment: String? = null
        private set

    val host: String get() = hostRoh ?: ""
    val rawPath: String get() = pfadRoh ?: ""
    val path: String get() = pfadRoh?.let { entschluesseln(it) } ?: ""
    val schemeSpecificPart: String get() = entschluesseln(rawSchemeSpecificPart)
    val authority: String? get() = rawAuthority?.let { entschluesseln(it) }
    val userInfo: String? get() = rawUserInfo?.let { entschluesseln(it) }
    val query: String? get() = rawQuery?.let { entschluesseln(it) }
    val fragment: String? get() = rawFragment?.let { entschluesseln(it) }

    val isAbsolute: Boolean get() = scheme != null
    val isOpaque: Boolean get() = pfadRoh == null

    init {
        Leser(str).lesen()
    }

    override fun toString(): String = eingabe
    fun toASCIIString(): String = eingabe

    /** Relativen Verweis gegen diese Adresse aufloesen (RFC 3986, Abschnitt 5.2). */
    fun resolve(str: String): URI = resolve(URI(str))

    fun resolve(ref: URI): URI {
        if (ref.scheme != null || isOpaque) return ref
        val autoritaet: String?
        val pfad: String
        val anfrage: String?
        if (ref.rawAuthority != null) {
            autoritaet = ref.rawAuthority
            pfad = punkteWeg(ref.rawPath)
            anfrage = ref.rawQuery
        } else {
            autoritaet = rawAuthority
            if (ref.rawPath.isEmpty()) {
                pfad = rawPath
                anfrage = ref.rawQuery ?: rawQuery
            } else {
                pfad = if (ref.rawPath.startsWith("/")) punkteWeg(ref.rawPath) else {
                    val basis = when {
                        rawAuthority != null && rawPath.isEmpty() -> "/"
                        else -> rawPath.substring(0, rawPath.lastIndexOf('/') + 1)
                    }
                    punkteWeg(basis + ref.rawPath)
                }
                anfrage = ref.rawQuery
            }
        }
        val sb = StringBuilder()
        scheme?.let { sb.append(it).append(':') }
        autoritaet?.let { sb.append("//").append(it) }
        sb.append(pfad)
        anfrage?.let { sb.append('?').append(it) }
        ref.rawFragment?.let { sb.append('#').append(it) }
        return URI(sb.toString())
    }

    private fun punkteWeg(pfad: String): String {
        val teile = ArrayList<String>()
        val stuecke = pfad.split('/')
        for ((i, t) in stuecke.withIndex()) {
            val letztes = i == stuecke.size - 1
            when (t) {
                "." -> if (letztes) teile.add("")
                ".." -> { if (teile.size > 1) teile.removeAt(teile.size - 1); if (letztes) teile.add("") }
                else -> teile.add(t)
            }
        }
        val aus = teile.joinToString("/")
        return if (pfad.startsWith("/") && !aus.startsWith("/")) "/$aus" else aus
    }

    override fun equals(other: Any?): Boolean = other is URI && other.eingabe == eingabe
    override fun hashCode(): Int = eingabe.hashCode()
    override fun compareTo(other: URI): Int = eingabe.compareTo(other.eingabe)

    private inner class Leser(val s: String) {
        val n = s.length

        fun fehler(grund: String, i: Int): Nothing = throw URISyntaxException(s, grund, i)
        fun erwartet(was: String, i: Int): Nothing = fehler("Expected $was", i)

        /** Erstes Zeichen in [stop] ab [von] vor [bis]; sonst bis. */
        fun suche(von: Int, bis: Int, stop: String): Int {
            var i = von
            while (i < bis) { if (s[i] in stop) return i; i++ }
            return bis
        }

        /** Laenge eines gueltigen Zeichens an [i] in der Klasse [klasse] (mit %XX und Nicht-ASCII, wenn [maskiert]), sonst 0. */
        fun zeichen(i: Int, klasse: String, maskiert: Boolean): Int {
            val c = s[i]
            if (c.code < 128) {
                if (c in klasse || (c in 'a'..'z') && klasse.contains('a') || (c in 'A'..'Z') && klasse.contains('A') || (c in '0'..'9') && klasse.contains('0')) return 1
                if (maskiert && c == '%') {
                    if (i + 2 < n && s[i + 1].digitToIntOrNull(16) != null && s[i + 2].digitToIntOrNull(16) != null) return 3
                    return 0
                }
                return 0
            }
            if (maskiert && !c.isWhitespace() && !c.isISOControl() && c != ' ' && c.category != CharCategory.SPACE_SEPARATOR &&
                c.category != CharCategory.LINE_SEPARATOR && c.category != CharCategory.PARAGRAPH_SEPARATOR) return 1
            return 0
        }

        fun lauf(von: Int, bis: Int, klasse: String, maskiert: Boolean): Int {
            var i = von
            while (i < bis) {
                val l = zeichen(i, klasse, maskiert)
                if (l == 0) break
                i += l
            }
            return i
        }

        fun pruefe(von: Int, bis: Int, klasse: String, maskiert: Boolean, was: String) {
            val i = lauf(von, bis, klasse, maskiert)
            if (i < bis) {
                if (s[i] == '%') fehler("Malformed escape pair", i)
                fehler("Illegal character in $was", i)
            }
        }

        fun lesen() {
            if (n == 0) { pfadRoh = ""; rawSchemeSpecificPart = ""; return }
            val p0 = suche(0, n, "/?#:")
            var p = 0
            if (p0 < n && s[p0] == ':') {
                if (p0 == 0) erwartet("scheme name", 0)
                if (s[0] !in 'a'..'z' && s[0] !in 'A'..'Z') fehler("Illegal character in scheme name", 0)
                pruefe(1, p0, SCHEMA, false, "scheme name")
                scheme = s.substring(0, p0)
                p = p0 + 1
                if (p < n && s[p] == '/') {
                    p = hierarchisch(p)
                } else {
                    val q = suche(p, n, "#")
                    if (q == p) erwartet("scheme-specific part", p)
                    pruefe(p, q, URIC, true, "opaque part")
                    rawSchemeSpecificPart = s.substring(p, q)
                    p = q
                }
            } else {
                p = hierarchisch(0)
            }
            if (p < n && s[p] == '#') {
                pruefe(p + 1, n, URIC, true, "fragment")
                rawFragment = s.substring(p + 1)
                p = n
            }
            if (p < n) fehler("end of URI", p)
        }

        fun hierarchisch(start: Int): Int {
            var p = start
            if (p + 1 < n && s[p] == '/' && s[p + 1] == '/') {
                p += 2
                val q = suche(p, n, "/?#")
                if (q > p) p = autoritaet(p, q)
                else if (q >= n) erwartet("authority", p)
            }
            val q = suche(p, n, "?#")
            pruefe(p, q, PFAD, true, "path")
            pfadRoh = s.substring(p, q)
            p = q
            if (p < n && s[p] == '?') {
                p++
                val r = suche(p, n, "#")
                pruefe(p, r, URIC, true, "query")
                rawQuery = s.substring(p, r)
                p = r
            }
            rawSchemeSpecificPart = s.substring(if (scheme != null) scheme!!.length + 1 else 0, p)
            return p
        }

        fun autoritaet(von: Int, bis: Int): Int {
            val serverZeichen = lauf(von, bis, SERVER, true) == bis
            val registerZeichen = lauf(von, bis, REG_NAME, true) == bis
            if (registerZeichen && !serverZeichen) {
                rawAuthority = s.substring(von, bis)
                return bis
            }
            var q = von
            var fehlerServer: URISyntaxException? = null
            if (serverZeichen) {
                try {
                    q = server(von, bis)
                    if (q < bis) erwartet("end of authority", q)
                    rawAuthority = s.substring(von, bis)
                } catch (e: URISyntaxException) {
                    rawUserInfo = null; hostRoh = null; port = -1
                    fehlerServer = e
                    q = von
                }
            }
            if (q < bis) {
                if (registerZeichen) rawAuthority = s.substring(von, bis)
                else if (fehlerServer != null) throw fehlerServer
                else fehler("Illegal character in authority", q)
            }
            return bis
        }

        fun server(von: Int, bis: Int): Int {
            var p = von
            val at = suche(p, bis, "/?#@")
            if (at < bis && s[at] == '@') {
                pruefe(p, at, USERINFO, true, "user info")
                rawUserInfo = s.substring(p, at)
                p = at + 1
            }
            if (p < bis && s[p] == '[') {
                val zu = suche(p, bis, "]")
                if (zu >= bis) fehler("Expected closing bracket for IPv6 address", p)
                val innen = s.substring(p + 1, zu)
                if (innen.isEmpty() || !innen.all { it.isLetterOrDigit() || it == ':' || it == '.' || it == '%' }) fehler("Malformed IPv6 address", p)
                hostRoh = s.substring(p, zu + 1)
                p = zu + 1
            } else {
                p = rechner(p, bis)
            }
            if (p < bis && s[p] == ':') {
                p++
                val q = lauf(p, bis, "0", false)
                if (q > p) {
                    port = s.substring(p, q).toIntOrNull() ?: fehler("Malformed port number", p)
                    p = q
                }
            }
            if (p < bis) erwartet("port number", p)
            return p
        }

        fun ziffernOderBuchstabe(c: Char) = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9'

        fun rechner(von: Int, bis: Int): Int {
            var p = von
            var letzteMarke = -1
            while (p < bis) {
                if (!ziffernOderBuchstabe(s[p])) break
                letzteMarke = p
                var q = p
                while (q < bis && (ziffernOderBuchstabe(s[q]) || s[q] == '-')) q++
                if (s[q - 1] == '-') fehler("Illegal character in hostname", q - 1)
                p = q
                if (p < bis && s[p] == '.') p++ else break
            }
            if (p < bis && s[p] != ':') fehler("Illegal character in hostname", p)
            if (letzteMarke < 0) erwartet("hostname", von)
            if (letzteMarke > von && s[letzteMarke] !in 'a'..'z' && s[letzteMarke] !in 'A'..'Z') {
                // IPv4-Adresse?
                val kandidat = s.substring(von, p)
                val teile = kandidat.split('.')
                if (teile.size == 4 && teile.all { t -> t.isNotEmpty() && t.all { it.isDigit() } && t.toInt() <= 255 }) {
                    hostRoh = kandidat
                    return p
                }
                fehler("Illegal character in hostname", letzteMarke)
            }
            hostRoh = s.substring(von, p)
            return p
        }
    }

    companion object {
        private const val ZEICHEN = "-_.!~*'()"
        private const val RESERVIERT = ";/?:@&=+$,[]"
        private const val URIC = "aA0$ZEICHEN$RESERVIERT"
        private const val PFAD = "aA0$ZEICHEN:@&=+$,;/"
        private const val USERINFO = "aA0$ZEICHEN;:&=+$,"
        private const val REG_NAME = "aA0$ZEICHEN$,;:@&=+"
        private const val SERVER = "aA0$ZEICHEN;:&=+$,.:@[]"
        private const val SCHEMA = "aA0+-."

        fun create(str: String): URI = try { URI(str) } catch (e: URISyntaxException) { throw IllegalArgumentException(e.message, e) }

        /** %XX-Folgen als UTF-8 lesen (wie URI.getPath usw.). */
        internal fun entschluesseln(s: String): String {
            if (!s.contains('%')) return s
            val sb = StringBuilder(s.length)
            var i = 0
            while (i < s.length) {
                if (s[i] != '%') { sb.append(s[i++]); continue }
                val bytes = ArrayList<Byte>()
                while (i + 2 < s.length && s[i] == '%') {
                    val h = s[i + 1].digitToIntOrNull(16)
                    val l = s[i + 2].digitToIntOrNull(16)
                    if (h == null || l == null) break
                    bytes += ((h shl 4) or l).toByte()
                    i += 3
                }
                if (bytes.isEmpty()) { sb.append(s[i++]); continue }
                sb.append(bytes.toByteArray().decodeToString())
            }
            return sb.toString()
        }
    }
}
