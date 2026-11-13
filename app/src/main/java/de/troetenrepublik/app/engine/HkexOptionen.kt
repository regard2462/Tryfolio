package de.troetenrepublik.app.engine

// Ersatz fuer Aktien aus Hongkong ohne US-Optionen [0.76]: der Tagesbericht der HKEX zu Aktienoptionen (frei abrufbar,
// eine CSV je Handelstag). Der Anfang der Datei fasst je Basiswert Umsatz und offene Kontrakte nach Calls und Puts
// zusammen. Die Datei eines Tages erscheint nach Handelsschluss; an Wochenenden und Feiertagen gibt es keine.
object HkexOptionen {

    // Zahl der Kalendertage ab heute, die rueckwaerts versucht werden
    const val TAGE = 5

    // Mindestumsatz (Calls plus Puts), ab dem die Verteilung gezeigt wird
    const val MINDESTUMSATZ = 10L

    private val SYMBOL = Regex("^0*([0-9]{1,5})\\.HK$")

    // Fuenfstelliger Aktiencode der HKEX zum Yahoo-Kuerzel (0700.HK wird 00700); null fuer andere Kuerzel.
    fun code(yahooSymbol: String): String? {
        val m = SYMBOL.matchEntire(yahooSymbol.uppercase()) ?: return null
        return m.groupValues[1].padStart(5, '0')
    }

    // Adresse des Tagesberichts, jahr vierstellig
    fun url(jahr: Int, monat: Int, tag: Int): String {
        fun z(n: Int) = if (n < 10) "0$n" else "$n"
        return "https://www.hkex.com.hk/eng/stat/dmstat/dayrpt/dqe${z(jahr % 100)}${z(monat)}${z(tag)}.csv"
    }

    // Umsatz des Tages als Paar (Calls, Puts) aus der Zusammenfassung am Dateianfang; null, wenn der Basiswert
    // dort fehlt oder der Text keine Zusammenfassung ist. Die Zeile heisst "TCH,TENCENT HOLDINGS LTD,(00700),106948,70399,36549,...".
    fun lesen(csv: String, code: String): Pair<Long, Long>? {
        val m = Regex("\\(" + code + "\\),([0-9]+),([0-9]+),([0-9]+)").find(csv) ?: return null
        val calls = m.groupValues[2].toLongOrNull() ?: return null
        val puts = m.groupValues[3].toLongOrNull() ?: return null
        return calls to puts
    }
}
