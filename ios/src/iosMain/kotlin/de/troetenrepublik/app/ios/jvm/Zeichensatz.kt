package de.troetenrepublik.app.ios.jvm

/** Bytes -> Text in den Zeichensaetzen, die Feeds und Dateien der App benutzen; unbekannte als UTF-8. */
fun dekodieren(bytes: ByteArray, charset: java.nio.charset.Charset): String = when (charset.name().uppercase()) {
    "WINDOWS-1252", "CP1252" -> {
        val sb = StringBuilder(bytes.size)
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            sb.append(if (c in 0x80..0x9F) CP1252[c - 0x80] else c.toChar())
        }
        sb.toString()
    }
    "ISO-8859-1", "ISO_8859_1", "LATIN1", "US-ASCII" -> {
        val sb = StringBuilder(bytes.size)
        for (b in bytes) sb.append((b.toInt() and 0xFF).toChar())
        sb.toString()
    }
    "UTF-16BE" -> utf16(bytes, true)
    "UTF-16LE" -> utf16(bytes, false)
    "UTF-16" -> when {
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> utf16(bytes.copyOfRange(2, bytes.size), true)
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> utf16(bytes.copyOfRange(2, bytes.size), false)
        else -> utf16(bytes, true)
    }
    else -> bytes.decodeToString()
}

// Zeichen 0x80 bis 0x9F in Windows-1252 (Euro, Anfuehrungszeichen, Striche); sonst wie ISO-8859-1
private const val CP1252 = "\u20AC\u0081\u201A\u0192\u201E\u2026\u2020\u2021\u02C6\u2030\u0160\u2039\u0152\u008D\u017D\u008F" +
    "\u0090\u2018\u2019\u201C\u201D\u2022\u2013\u2014\u02DC\u2122\u0161\u203A\u0153\u009D\u017E\u0178"

private fun utf16(bytes: ByteArray, gross: Boolean): String {
    val sb = StringBuilder(bytes.size / 2)
    var i = 0
    while (i + 1 < bytes.size) {
        val a = bytes[i].toInt() and 0xFF
        val b = bytes[i + 1].toInt() and 0xFF
        sb.append((if (gross) (a shl 8) or b else (b shl 8) or a).toChar())
        i += 2
    }
    return sb.toString()
}

/** Text -> Bytes; ISO-8859-1 mit ? fuer Zeichen ausserhalb, sonst UTF-8. */
fun kodieren(text: String, charset: java.nio.charset.Charset): ByteArray = when (charset.name().uppercase()) {
    "ISO-8859-1", "ISO_8859_1", "LATIN1", "US-ASCII" -> ByteArray(text.length) { i -> text[i].code.let { if (it < 256) it.toByte() else '?'.code.toByte() } }
    "UTF-16BE" -> ByteArray(text.length * 2) { i -> (text[i / 2].code shr (if (i % 2 == 0) 8 else 0)).toByte() }
    "UTF-16LE" -> ByteArray(text.length * 2) { i -> (text[i / 2].code shr (if (i % 2 == 0) 0 else 8)).toByte() }
    else -> text.encodeToByteArray()
}
