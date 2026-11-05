package java.nio.charset

/**
 * Ersatz fuer java.nio.charset.Charset: nur Name und Aliasse. Kodieren und Dekodieren liegt ausserhalb
 * (de.troetenrepublik.app.web.jvm). Bekannt sind die Zeichensaetze, die Feeds und Dateien der App benutzen.
 */
open class Charset protected constructor(
    private val canonicalName: String,
    private val aliasSet: Set<String>,
) : Comparable<Charset> {

    fun name(): String = canonicalName
    fun displayName(): String = canonicalName
    fun aliases(): Set<String> = aliasSet
    fun isRegistered(): Boolean = true
    open fun canEncode(): Boolean = true

    override fun compareTo(other: Charset): Int = canonicalName.compareTo(other.canonicalName, ignoreCase = true)
    override fun equals(other: Any?): Boolean = other is Charset && other.canonicalName == canonicalName
    override fun hashCode(): Int = canonicalName.hashCode()
    override fun toString(): String = name()

    private class Bekannt(name: String, aliases: Set<String>) : Charset(name, aliases)

    companion object {
        private val alle: List<Charset> = listOf(
            Bekannt("UTF-8", setOf("unicode-1-1-utf-8", "UTF8")),
            Bekannt("ISO-8859-1", setOf("iso-ir-100", "ISO_8859_1", "latin1", "l1", "IBM819", "cp819", "csISOLatin1",
                "819", "IBM-819", "ISO8859_1", "ISO_8859-1:1987", "ISO_8859-1", "8859_1", "ISO8859-1")),
            Bekannt("US-ASCII", setOf("iso-ir-6", "ANSI_X3.4-1986", "ISO_646.irv:1991", "ASCII", "iso_646.irv:1983",
                "ANSI_X3.4-1968", "ISO646-US", "default", "cp367", "csASCII", "ascii7", "646", "us", "IBM367")),
            Bekannt("UTF-16", setOf("UTF_16", "utf16", "unicode", "UnicodeBig")),
            Bekannt("UTF-16BE", setOf("UTF_16BE", "ISO-10646-UCS-2", "X-UTF-16BE", "UnicodeBigUnmarked")),
            Bekannt("UTF-16LE", setOf("UnicodeLittleUnmarked", "UTF_16LE", "X-UTF-16LE")),
            Bekannt("windows-1252", setOf("cp1252", "cp5348", "ibm-1252", "ibm1252")),
        )

        private val nachName: Map<String, Charset> = buildMap {
            for (c in alle) {
                put(c.name().lowercase(), c)
                for (a in c.aliases()) put(a.lowercase(), c)
            }
        }

        /** Gueltiger Name nach Java: Buchstaben, Ziffern und - + : _ . , nicht mit einem Satzzeichen am Anfang. */
        private fun pruefeName(name: String) {
            if (name.isEmpty()) throw IllegalCharsetNameException(name)
            for ((i, c) in name.withIndex()) {
                val ok = c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' ||
                    (i > 0 && (c == '-' || c == '+' || c == ':' || c == '_' || c == '.'))
                if (!ok) throw IllegalCharsetNameException(name)
            }
        }

        fun forName(charsetName: String): Charset {
            pruefeName(charsetName)
            return nachName[charsetName.lowercase()] ?: throw UnsupportedCharsetException(charsetName)
        }

        fun isSupported(charsetName: String): Boolean {
            pruefeName(charsetName)
            return nachName.containsKey(charsetName.lowercase())
        }

        fun defaultCharset(): Charset = alle[0]

        fun availableCharsets(): Map<String, Charset> = alle.sortedBy { it.name().lowercase() }.associateBy { it.name() }
    }
}

open class IllegalCharsetNameException(val charsetName: String) : IllegalArgumentException(charsetName)

open class UnsupportedCharsetException(val charsetName: String) : IllegalArgumentException(charsetName)
