package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle

// Reaktion des Marktes nach einem Beitrag des Praesidenten (0.76): Kursaenderung von SPY und den zum Text passenden
// Sektor-ETFs in den Stunden danach, aus Kerzen mit weniger als einer Stunde Abstand. Als Preis zu einem Zeitpunkt
// gilt der Beginn der Kerze, die den Zeitpunkt enthaelt. Ausserhalb der Handelszeit fehlt der Wert. Reine
// Funktionen ohne Netz; die Reaktion ist zeitlich nach dem Beitrag, nicht dessen Folge.
object PotusReaktion {

    const val MARKT = "SPY"

    // Abstaende nach dem Beitrag in Stunden.
    val STUNDEN = listOf(1, 3)

    private const val STUNDE = 3_600_000L

    // Hoechstens so viele Sektor-ETFs je Beitrag.
    const val MAX_ETFS = 2

    private val REGELN: List<Pair<Regex, String>> = listOf(
        Regex("""\b(steel|aluminum|aluminium|stahl|metals?|mining|copper|kupfer)\b""", RegexOption.IGNORE_CASE) to "XME",
        Regex("""\b(tariffs?|zoll|zölle|zoelle|manufactur\w*|factor(y|ies)|industr\w*)\b""", RegexOption.IGNORE_CASE) to "XLI",
        Regex("""\b(oil|drill\w*|energy|lng|petrol\w*|pipeline|gas|öl|oel|energie)\b""", RegexOption.IGNORE_CASE) to "XLE",
        Regex("""\b(chips?|semiconductors?|nvidia|intel|tsmc|halbleiter)\b""", RegexOption.IGNORE_CASE) to "SMH",
        Regex("""\b(software|microsoft|oracle|cloud|ai|artificial intelligence|openai)\b""", RegexOption.IGNORE_CASE) to "IGV",
        Regex("""\b(google|alphabet|meta|facebook|netflix|disney|tiktok|media|hollywood|movies?|film\w*)\b""", RegexOption.IGNORE_CASE) to "XLC",
        Regex("""\b(banks?|fed|federal reserve|interest rates?|powell|zinsen?|notenbank)\b""", RegexOption.IGNORE_CASE) to "KBE",
        Regex("""\b(pharma\w*|drugs?|medicine|medicare|medicaid|obamacare|healthcare|medikamente?)\b""", RegexOption.IGNORE_CASE) to "PPH",
        Regex("""\b(defen[sc]e|military|army|navy|missiles?|nato|weapons?|rüstung|ruestung|boeing|lockheed)\b""", RegexOption.IGNORE_CASE) to "ITA",
        Regex("""\b(gold)\b""", RegexOption.IGNORE_CASE) to "GDX",
        Regex("""\b(cars?|autos?|vehicles?|tesla|ford|general motors|autos)\b""", RegexOption.IGNORE_CASE) to "XLY",
        Regex("""\b(solar|wind|windmills?|green new deal|clean energy)\b""", RegexOption.IGNORE_CASE) to "ICLN",
        Regex("""\b(nuclear|electricity|utilities|power plants?|atom\w*|strom)\b""", RegexOption.IGNORE_CASE) to "XLU",
        Regex("""\b(housing|mortgages?|real estate|immobilien\w*|hypothek\w*)\b""", RegexOption.IGNORE_CASE) to "XLRE",
    )

    // Sektor-ETFs, die zum Text passen: die mit den meisten Treffern zuerst, bei Gleichstand in der Reihenfolge der Regeln.
    fun etfs(text: String, max: Int = MAX_ETFS): List<String> =
        REGELN.mapIndexedNotNull { i, (rx, etf) ->
            val n = rx.findAll(text).count()
            if (n > 0) Triple(etf, n, i) else null
        }.sortedWith(compareByDescending<Triple<String, Int, Int>> { it.second }.thenBy { it.third }).take(max).map { it.first }

    // Abstand der Kerzen in Millisekunden: der kleinste positive Abstand zwischen zwei Kerzen; 0 ohne zwei Kerzen.
    fun abstand(kerzen: List<Candle>): Long {
        var m = Long.MAX_VALUE
        for (i in 1 until kerzen.size) {
            val d = kerzen[i].t - kerzen[i - 1].t
            if (d > 0 && d < m) m = d
        }
        return if (m == Long.MAX_VALUE) 0L else m
    }

    // Beginn der Kerze, die zeit enthaelt (aufsteigend sortierte Kerzen); null, wenn keine Kerze die Zeit enthaelt.
    fun preisAm(kerzen: List<Candle>, zeit: Long, abstand: Long = abstand(kerzen)): Double? {
        if (kerzen.isEmpty() || abstand <= 0) return null
        var lo = 0
        var hi = kerzen.size - 1
        var treffer = -1
        while (lo <= hi) {
            val mitte = (lo + hi) ushr 1
            if (kerzen[mitte].t <= zeit) { treffer = mitte; lo = mitte + 1 } else hi = mitte - 1
        }
        if (treffer < 0) return null
        val k = kerzen[treffer]
        return if (zeit < k.t + abstand && k.open > 0) k.open else null
    }

    // Aenderung (0,01 = 1 %) vom Beitrag bis stunden Stunden danach; null, wenn einer der Zeitpunkte ausserhalb der Kerzen liegt.
    fun aenderung(kerzen: List<Candle>, zeit: Long, stunden: Int): Double? {
        val iv = abstand(kerzen)
        if (iv <= 0 || iv >= STUNDE) return null
        val a = preisAm(kerzen, zeit, iv) ?: return null
        val b = preisAm(kerzen, zeit + stunden * STUNDE, iv) ?: return null
        return b / a - 1
    }

    // Eine Zeile je Symbol: Aenderung je Abstand aus STUNDEN, null ohne Wert.
    data class Zeile(val symbol: String, val werte: List<Double?>)

    // Zeilen fuer SPY und die ETFs des Textes; null, wenn nirgends ein Wert vorliegt.
    fun zeilen(text: String, zeit: Long, kerzen: (String) -> List<Candle>?): List<Zeile>? {
        val z = (listOf(MARKT) + etfs(text)).map { s ->
            val k = kerzen(s)
            Zeile(s, STUNDEN.map { h -> k?.let { aenderung(it, zeit, h) } })
        }
        return if (z.any { r -> r.werte.any { it != null } }) z else null
    }
}
