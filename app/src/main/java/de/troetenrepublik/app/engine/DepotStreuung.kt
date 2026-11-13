package de.troetenrepublik.app.engine

// [Globus Depot 0.70] Laenderstreuung des Depots: Anteile mit und ohne ETFs und eine kleine Bilanz (Zahl der Laender,
// Regionen, Industrie gegen Schwellenlaender, Heimat, Vergleich mit dem Weltindex, Konzentration).
object DepotStreuung {
    enum class Region { NORDAMERIKA, EUROPA, ASIEN_PAZIFIK, LATEINAMERIKA, NAHOST_AFRIKA, UEBRIGE }

    enum class Konzentration { GERING, MAESSIG, HOCH, SEHR_HOCH }

    private fun satz(s: String) = s.split(' ').toSet()

    private val NORDAMERIKA = satz("US CA BM GL PM")
    private val LATEINAMERIKA = satz("MX BR AR CL CO PE UY PY BO EC VE CR PA GT HN SV NI DO CU JM TT BS BB BZ HT GY SR KY VG AW CW AI AG DM GD KN LC VC TC PR VI SX MS GP MQ")
    private val ASIEN_PAZIFIK = satz("CN HK MO TW JP KR KP IN PK BD LK NP BT MV AF MM TH VN LA KH MY SG ID PH BN TL MN KZ UZ KG TJ TM AU NZ FJ PG WS TO VU SB NC PF")
    private val NAHOST_AFRIKA = satz(
        "SA AE QA KW BH OM IL JO LB IR IQ SY YE PS TR EG DZ AO BJ BW BF BI CM CV CF TD KM CG CD CI DJ GQ ER SZ ET GA GM GH GN GW KE LS LR LY MG MW ML MR MU MA MZ NA NE NG RW ST SN SC SL SO ZA SS SD TZ TG TN UG ZM ZW RE YT",
    )
    private val EUROPA = satz(
        "GB IE FR DE NL BE LU CH AT LI IT ES PT SE NO DK FI IS PL CZ SK HU SI HR RS BA ME MK AL XK BG RO MD UA BY RU EE LV LT GR CY MT AM GE AZ JE GG IM GI FO AD MC SM VA",
    )

    fun region(a2: String): Region = when (a2) {
        in NORDAMERIKA -> Region.NORDAMERIKA
        in EUROPA -> Region.EUROPA
        in ASIEN_PAZIFIK -> Region.ASIEN_PAZIFIK
        in LATEINAMERIKA -> Region.LATEINAMERIKA
        in NAHOST_AFRIKA -> Region.NAHOST_AFRIKA
        else -> Region.UEBRIGE
    }

    // Industrielaender nach MSCI (Developed Markets) plus Luxemburg und Island; alles andere zaehlt als Schwellen- und Frontierland
    private val INDUSTRIE = satz("US CA JP GB FR DE CH AU NL SE DK ES IT HK SG FI BE NO IL IE NZ AT PT LU IS")
    fun industrieland(a2: String) = a2 in INDUSTRIE

    // Anteil jedes Landes (alpha-3) an der Weltwirtschaftsleistung in laufenden US-Dollar: BIP des Landes durch die Summe
    // der BIP aller Laender des Datensatzes (IWF-Reihe NGDPD) im Jahr. Laender ohne Wert fehlen in der Antwort.
    fun bipAnteile(d: Imf.Datensatz, jahr: Int): Map<String, Double> {
        val liste = d.werte[Imf.BIP].orEmpty().filterKeys { it != Imf.WELT }
            .mapNotNull { (a3, reihe) -> reihe[jahr]?.takeIf { it > 0 && it.isFinite() }?.let { a3 to it } }
        val summe = liste.sumOf { it.second }
        if (!(summe > 0)) return emptyMap()
        return liste.associate { it.first to it.second / summe }
    }

    // Waehrung je Land (alpha-2 zu ISO-4217); Euro-Raum samt Kleinstaaten und franzoesischen Gebieten, Bulgarien seit 2026.
    private fun waehrungsTafel(vararg gruppen: Pair<String, String>): Map<String, String> =
        gruppen.flatMap { (code, laender) -> laender.split(' ').filter { it.isNotEmpty() }.map { it to code } }.toMap()

    private val WAEHRUNG: Map<String, String> = waehrungsTafel(
        "EUR" to "AT BE CY DE EE ES FI FR GR HR IE IT LT LU LV MT NL PT SI SK BG AD MC SM VA ME XK GP MQ RE YT GF PM BL MF",
        "USD" to "US EC SV PA PR GU VI AS MP TL FM MH PW BQ TC VG IO ZW",
        "GBP" to "GB GG JE IM GI FK", "CHF" to "CH LI", "JPY" to "JP", "CNY" to "CN", "HKD" to "HK", "MOP" to "MO", "TWD" to "TW",
        "KRW" to "KR", "KPW" to "KP", "INR" to "IN", "PKR" to "PK", "BDT" to "BD", "LKR" to "LK", "NPR" to "NP", "BTN" to "BT",
        "MVR" to "MV", "AFN" to "AF", "MMK" to "MM", "THB" to "TH", "VND" to "VN", "LAK" to "LA", "KHR" to "KH", "MYR" to "MY",
        "SGD" to "SG", "IDR" to "ID", "PHP" to "PH", "BND" to "BN", "MNT" to "MN", "KZT" to "KZ", "UZS" to "UZ", "KGS" to "KG",
        "TJS" to "TJ", "TMT" to "TM", "AUD" to "AU KI NR TV", "NZD" to "NZ CK NU TK", "FJD" to "FJ", "PGK" to "PG", "WST" to "WS",
        "TOP" to "TO", "VUV" to "VU", "SBD" to "SB", "XPF" to "NC PF WF", "CAD" to "CA", "MXN" to "MX", "BRL" to "BR", "ARS" to "AR",
        "CLP" to "CL", "COP" to "CO", "PEN" to "PE", "UYU" to "UY", "PYG" to "PY", "BOB" to "BO", "VES" to "VE", "CRC" to "CR",
        "GTQ" to "GT", "HNL" to "HN", "NIO" to "NI", "DOP" to "DO", "CUP" to "CU", "JMD" to "JM", "TTD" to "TT", "BSD" to "BS",
        "BBD" to "BB", "BZD" to "BZ", "HTG" to "HT", "GYD" to "GY", "SRD" to "SR", "KYD" to "KY", "AWG" to "AW", "ANG" to "CW SX",
        "XCD" to "AG DM GD KN LC VC AI MS", "BMD" to "BM", "SEK" to "SE", "NOK" to "NO SJ", "DKK" to "DK FO GL", "ISK" to "IS",
        "PLN" to "PL", "CZK" to "CZ", "HUF" to "HU", "RON" to "RO", "RSD" to "RS", "BAM" to "BA", "MKD" to "MK", "ALL" to "AL",
        "MDL" to "MD", "UAH" to "UA", "BYN" to "BY", "RUB" to "RU", "GEL" to "GE", "AMD" to "AM", "AZN" to "AZ", "TRY" to "TR",
        "SAR" to "SA", "AED" to "AE", "QAR" to "QA", "KWD" to "KW", "BHD" to "BH", "OMR" to "OM", "ILS" to "IL PS", "JOD" to "JO",
        "LBP" to "LB", "IRR" to "IR", "IQD" to "IQ", "SYP" to "SY", "YER" to "YE", "EGP" to "EG", "DZD" to "DZ", "MAD" to "MA EH",
        "TND" to "TN", "LYD" to "LY", "ZAR" to "ZA", "LSL" to "LS", "SZL" to "SZ", "NAD" to "NA", "NGN" to "NG", "GHS" to "GH",
        "KES" to "KE", "ETB" to "ET", "TZS" to "TZ", "UGX" to "UG", "ZMW" to "ZM", "MZN" to "MZ", "AOA" to "AO", "BWP" to "BW",
        "MGA" to "MG", "MWK" to "MW", "MUR" to "MU", "SCR" to "SC", "RWF" to "RW", "BIF" to "BI", "CDF" to "CD", "SDG" to "SD",
        "SSP" to "SS", "SOS" to "SO", "ERN" to "ER", "DJF" to "DJ", "GMD" to "GM", "GNF" to "GN", "SLE" to "SL", "LRD" to "LR",
        "CVE" to "CV", "KMF" to "KM", "STN" to "ST", "MRU" to "MR",
        "XOF" to "BJ BF CI GW ML NE SN TG", "XAF" to "CM CF TD CG GQ GA",
    )

    fun waehrungVon(a2: String): String? = WAEHRUNG[a2]

    // Waehrungsanteile des Depots in Euro-Betraegen. posten: Symbol, Wert, Handelswaehrung. Fonds mit Laenderverteilung
    // (etf) werden nach der Waehrung ihrer Laender aufgeloest (ein Welt-ETF in Euro zaehlt anteilig als Dollar, Yen ...);
    // was die Verteilung nicht abdeckt oder keiner Waehrung zugeordnet ist, bleibt bei der Handelswaehrung des Fonds.
    // Alles andere zaehlt in seiner Handelswaehrung.
    fun waehrungen(posten: List<Triple<String, Double, String>>, etf: (String) -> Map<String, Double>?): Map<String, Double> {
        val aus = HashMap<String, Double>()
        for ((symbol, wert, handel) in posten) {
            val v = etf(symbol)?.takeIf { it.isNotEmpty() }
            if (v == null) { aus[handel] = (aus[handel] ?: 0.0) + wert; continue }
            var vergeben = 0.0
            for ((land, a) in v) {
                val w = WAEHRUNG[land] ?: continue
                aus[w] = (aus[w] ?: 0.0) + wert * a
                vergeben += a
            }
            val rest = (1.0 - vergeben).coerceAtLeast(0.0)
            if (rest > 0) aus[handel] = (aus[handel] ?: 0.0) + wert * rest
        }
        return aus
    }

    // Anteile des Depotwerts je Land. etf liefert fuer ETF-Symbole die Laenderverteilung (Summe hoechstens 1) oder null;
    // ohne etf bleiben ETFs unzuordenbar. Ein Posten mit Verteilung teilt seinen Wert, der Rest geht in unzuordenbar.
    fun anteile(posten: List<Triple<String, Double, Boolean>>, land: (String) -> String?, etf: ((String) -> Map<String, Double>?)?): GlobusEbenen.Anteile {
        val summe = posten.sumOf { kotlin.math.abs(it.second) }
        if (summe <= 0.0) return GlobusEbenen.Anteile(emptyMap(), 0.0, 0.0)
        val je = HashMap<String, Double>()
        var ohne = 0.0
        for ((symbol, wert, istEtf) in posten) {
            val w = kotlin.math.abs(wert)
            if (istEtf) {
                val v = if (etf != null) etf(symbol) else null
                if (v == null || v.isEmpty()) { ohne += w; continue }
                var vergeben = 0.0
                for ((l, a) in v) { je[l] = (je[l] ?: 0.0) + w * a; vergeben += w * a }
                ohne += (w - vergeben).coerceAtLeast(0.0)
            } else {
                val l = land(symbol)?.takeIf { it.isNotBlank() }
                if (l == null) ohne += w else je[l] = (je[l] ?: 0.0) + w
            }
        }
        return GlobusEbenen.Anteile(je.mapValues { it.value / summe }, ohne / summe, summe)
    }

    // Alle Anteile beziehen sich auf den zugeordneten Teil des Depots (Summe 1); zugeordnet: dessen Anteil am ganzen Depot
    class Bilanz(
        val zugeordnet: Double,
        val laender: Int,
        val groesstes: Pair<String, Double>,
        val regionen: Map<Region, Double>,
        val weltRegionen: Map<Region, Double>,
        val industrie: Double,
        val weltIndustrie: Double,
        val heim: String,
        val heimAnteil: Double,
        val weltHeim: Double,
        // Die groessten Laender mit Anteil im Depot und im Weltindex
        val top: List<Triple<String, Double, Double>>,
        // Kehrwert der Summe der quadrierten Anteile: so viele gleich grosse Laender waeren es
        val effektiv: Double,
        val konzentration: Konzentration,
    )

    // Gleiche Laenderzahl ist nicht gleiche Streuung: Der Kehrwert der Summe der quadrierten Anteile (effektive Laenderzahl)
    // misst, wie gleichmaessig verteilt wird. Unter 1,3: sehr hoch, unter 2: hoch, unter 4: maessig, sonst gering. Der Weltindex
    // liegt bei rund 2,2 (die USA haben ueber 60 Prozent).
    fun konzentration(effektiv: Double) = when {
        effektiv < 1.3 -> Konzentration.SEHR_HOCH
        effektiv < 2.0 -> Konzentration.HOCH
        effektiv < 4.0 -> Konzentration.MAESSIG
        else -> Konzentration.GERING
    }

    fun bilanz(a: GlobusEbenen.Anteile, welt: Map<String, Double>, heim: String, nTop: Int = 5): Bilanz? {
        val zu = a.nachLand.values.sum()
        if (a.leer || zu <= 0.0) return null
        val n = a.nachLand.mapValues { it.value / zu }
        val gross = n.maxByOrNull { it.value } ?: return null
        fun regionen(m: Map<String, Double>): Map<Region, Double> =
            m.entries.groupBy({ region(it.key) }, { it.value }).mapValues { it.value.sum() }
        val eff = 1.0 / n.values.sumOf { it * it }
        val top = n.entries.sortedByDescending { it.value }.take(nTop).map { Triple(it.key, it.value, welt[it.key] ?: 0.0) }
        return Bilanz(
            zu, n.size, gross.key to gross.value, regionen(n), regionen(welt),
            n.filterKeys { industrieland(it) }.values.sum(), welt.filterKeys { industrieland(it) }.values.sum(),
            heim, n[heim] ?: 0.0, welt[heim] ?: 0.0, top, eff, konzentration(eff),
        )
    }
}
