package de.troetenrepublik.app.market

// Buchstabe oder Ziffer wie \p{L} und \p{N} in Regex.
internal fun Char.istWortzeichen(): Boolean = isLetter() || when (category) {
    CharCategory.DECIMAL_DIGIT_NUMBER, CharCategory.LETTER_NUMBER, CharCategory.OTHER_NUMBER -> true
    else -> false
}

// Zusammenhaengende Folgen aus Buchstaben und Ziffern.
internal fun wortfolgen(t: String): List<String> {
    val ergebnis = ArrayList<String>()
    val sb = StringBuilder()
    for (c in t) {
        if (c.istWortzeichen()) sb.append(c) else if (sb.isNotEmpty()) { ergebnis.add(sb.toString()); sb.clear() }
    }
    if (sb.isNotEmpty()) ergebnis.add(sb.toString())
    return ergebnis
}

// Ob wort in t ohne Buchstabe oder Ziffer davor und dahinter vorkommt, ohne Unterschied in Gross- und Kleinschreibung.
internal fun alsGanzesWort(t: String, wort: String): Boolean {
    if (wort.isEmpty()) return false
    var i = t.indexOf(wort, 0, ignoreCase = true)
    while (i >= 0) {
        val ende = i + wort.length
        if ((i == 0 || !t[i - 1].istWortzeichen()) && (ende >= t.length || !t[ende].istWortzeichen())) return true
        i = t.indexOf(wort, i + 1, ignoreCase = true)
    }
    return false
}
