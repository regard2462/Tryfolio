package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import java.time.LocalDate
import java.time.YearMonth

// [Globus Wirtschaft] Markt im Laenderblatt: Leitzins (BIS), Rendite der zehnjaehrigen Staatsanleihe (EZB, FRED),
// Waehrung und Laender-ETF (Yahoo). Hier steht alles, was ohne Netz gelesen und gerechnet wird.
object LaenderMarkt {
    // Euroraum (Stand 2026). Die BIS fuehrt den Leitzins unter XM, die EZB-Zinsreihen der Einzelstaaten enden vor dem Euro.
    val EURO = setOf("AT", "BE", "BG", "HR", "CY", "EE", "FI", "FR", "DE", "GR", "IE", "IT", "LV", "LT", "LU", "MT", "NL", "PT", "SK", "SI", "ES")
    const val EURORAUM = "XM"

    // Leitzins aelter als so viele Tage: Datum dazuschreiben
    const val ZINS_ALT_TAGE = 60L

    // ---- Leitzins (BIS WS_CBPOL, CSV: FREQ,REF_AREA,TIME_PERIOD,OBS_VALUE)
    const val BIS_ADRESSE = "https://stats.bis.org/api/v2/data/dataflow/BIS/WS_CBPOL/1.0/D?lastNObservations=1&detail=dataonly&format=csv"

    data class Zins(val datum: LocalDate, val wert: Double)

    fun zinsLesen(csv: String): Map<String, Zins> {
        val aus = HashMap<String, Zins>()
        for (z in csvZeilen(csv)) {
            val land = z["REF_AREA"] ?: continue
            val tp = z["TIME_PERIOD"] ?: continue
            val datum = runCatching { LocalDate.parse(tp) }.getOrNull() ?: continue
            val wert = z["OBS_VALUE"]?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: continue
            val alt = aus[land]
            if (alt == null || alt.datum < datum) aus[land] = Zins(datum, wert)
        }
        return aus
    }

    // Leitzins eines Landes (Kuerzel alpha-2); Euroländer nehmen den Wert des Euroraums.
    fun zinsFuer(a2: String, alle: Map<String, Zins>): Zins? = alle[if (a2 in EURO) EURORAUM else a2]

    // ---- Rendite zehnjaehriger Staatsanleihen
    data class Rendite(val monat: YearMonth?, val wert: Double)

    // EZB, Datensatz IRS, ein Wert je Euroland und Monat; REF_AREA ist alpha-2.
    const val EZB_ADRESSE = "https://data-api.ecb.europa.eu/service/data/IRS/M..L.L40.CI.0000.EUR.N.Z?lastNObservations=1&detail=dataonly&format=csvdata"

    fun ezbLesen(csv: String): Map<String, Rendite> {
        val aus = HashMap<String, Rendite>()
        for (z in csvZeilen(csv)) {
            val land = z["REF_AREA"] ?: continue
            val tp = z["TIME_PERIOD"] ?: continue
            val monat = runCatching { YearMonth.parse(tp) }.getOrNull() ?: continue
            val wert = z["OBS_VALUE"]?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: continue
            val alt = aus[land]
            if (alt == null || (alt.monat != null && alt.monat < monat)) aus[land] = Rendite(monat, wert)
        }
        return aus
    }

    // Verkleinert die EZB-Antwort auf die drei gebrauchten Spalten fuer die Speicherung.
    fun ezbKuerzen(csv: String): String? {
        val l = ezbLesen(csv)
        if (l.isEmpty()) return null
        return "REF_AREA,TIME_PERIOD,OBS_VALUE\n" + l.entries.joinToString("\n") { "${it.key},${it.value.monat},${it.value.wert}" }
    }

    // Laender ausser dem Euroraum, fuer die FRED die OECD-Reihe IRLTLT01<A2>M156N fuehrt (Stand 06.11.2026 geprueft).
    // Reihen mit altem Ende (Island, Russland) oder ohne Reihe (Tuerkei, Brasilien, Indien, China ...) fehlen.
    val FRED_LAENDER = setOf("AU", "CA", "CH", "CL", "CZ", "DK", "GB", "HU", "IL", "JP", "KR", "MX", "NO", "NZ", "PL", "SE", "ZA")

    fun fredAdresse(a2: String, ab: LocalDate): String =
        "https://fred.stlouisfed.org/graph/fredgraph.csv?id=IRLTLT01${a2}M156N&cosd=$ab"

    // CSV: Kopf "observation_date,<Reihe>" (aeltere Dateien "DATE"), danach "2026-09-01,5.2421"; fehlende Werte stehen als ".".
    // Der juengste Wert mit Monat; null ohne Wert.
    fun fredLesen(csv: String): Rendite? {
        var best: Rendite? = null
        for (zeile in csv.lineSequence().drop(1)) {
            val t = zeile.trim().split(',')
            if (t.size < 2) continue
            val monat = runCatching { YearMonth.from(LocalDate.parse(t[0].trim())) }.getOrNull() ?: continue
            val wert = t[1].trim().toDoubleOrNull()?.takeIf { it.isFinite() } ?: continue
            if (best == null || best.monat!! <= monat) best = Rendite(monat, wert)
        }
        return best
    }

    fun fredKuerzen(csv: String): String? = fredLesen(csv)?.let { "observation_date,wert\n${it.monat}-01,${it.wert}" }

    // Alter, ab dem eine FRED-Reihe als nicht mehr gepflegt gilt: Monate
    const val FRED_MAX_MONATE = 8L

    // ---- Waehrung (Yahoo EUR<Code>=X: Einheiten der Waehrung je Euro)
    fun kursSymbol(code: String) = "EUR$code=X"

    // Vergleichswaehrung der Waehrungsangaben im Laenderblatt (Kachel und Abschnitt Markt): die Heimatwaehrung des
    // Nutzers, wie im Abschnitt "Wert der Waehrung". null, wenn das Land die Heimatwaehrung selbst nutzt oder keine
    // Waehrung bekannt ist: Dann steht nur der Name der Waehrung da.
    fun vergleichsCode(landCode: String?, heimatCode: String?): String? {
        val heimat = heimatCode ?: "EUR"
        return if (landCode == null || landCode == heimat) null else heimat
    }

    // Kurs der Landeswaehrung in Einheiten je Einheit der Vergleichswaehrung, je gemeinsamem Tag aus den Euro-Kursen
    // beider Waehrungen (Einheiten je Euro). null fuer eine Reihe heisst: Diese Waehrung ist der Euro.
    fun kreuzkurs(landJeEuro: List<Candle>?, gegenJeEuro: List<Candle>?): List<Pair<Long, Double>> {
        val tag = 86_400_000L
        if (gegenJeEuro == null) return landJeEuro.orEmpty().filter { it.close > 0 }.map { it.t to it.close }
        if (landJeEuro == null) return gegenJeEuro.filter { it.close > 0 }.map { it.t to 1.0 / it.close }
        val gegen = gegenJeEuro.filter { it.close > 0 }.associate { Math.floorDiv(it.t, tag) to it.close }
        return landJeEuro.filter { it.close > 0 }.mapNotNull { k -> gegen[Math.floorDiv(k.t, tag)]?.let { k.t to k.close / it } }
    }

    // Veraenderung der Landeswaehrung ueber eine Reihe aus kreuzkurs in Prozent: positiv, wenn sie zugelegt hat (der Kurs
    // in Einheiten je Vergleichswaehrung also gefallen ist). null bei zu kurzer Reihe.
    fun kreuzVeraenderung(reihe: List<Pair<Long, Double>>, mindestTage: Int = 300): Double? {
        val erste = reihe.firstOrNull() ?: return null
        val letzte = reihe.last()
        if (erste.second <= 0 || letzte.second <= 0 || letzte.first - erste.first < mindestTage * 86_400_000L) return null
        return (erste.second / letzte.second - 1) * 100
    }

    // Veraenderung der Landeswaehrung gegenueber dem Euro ueber die Reihe in Prozent: positiv, wenn die Waehrung zum Euro
    // zugelegt hat (der Kurs in Einheiten je Euro also gefallen ist). null bei zu kurzer Reihe.
    fun waehrungVeraenderung(kerzen: List<Candle>, mindestTage: Int = 300): Double? {
        val erste = kerzen.firstOrNull() ?: return null
        val letzte = kerzen.last()
        if (erste.close <= 0 || letzte.close <= 0 || letzte.t - erste.t < mindestTage * 86_400_000L) return null
        return (erste.close / letzte.close - 1) * 100
    }

    // Kursveraenderung eines Papiers ueber die Reihe in Prozent
    fun kursVeraenderung(kerzen: List<Candle>, mindestTage: Int = 300): Double? {
        val erste = kerzen.firstOrNull() ?: return null
        val letzte = kerzen.last()
        if (erste.close <= 0 || letzte.close <= 0 || letzte.t - erste.t < mindestTage * 86_400_000L) return null
        return (letzte.close / erste.close - 1) * 100
    }

    // Landeswaehrung je Land (alpha-2): ISO-4217-Code. EUR heisst: Euro, kein Kurs.
    val WAEHRUNG: Map<String, String> = mapOf(
        "AF" to "AFN", "AL" to "ALL", "DZ" to "DZD", "AO" to "AOA", "AR" to "ARS", "AM" to "AMD", "AU" to "AUD", "AZ" to "AZN",
        "BS" to "BSD", "BH" to "BHD", "BD" to "BDT", "BB" to "BBD", "BY" to "BYN", "BZ" to "BZD", "BM" to "BMD", "BT" to "BTN",
        "BO" to "BOB", "BA" to "BAM", "BW" to "BWP", "BR" to "BRL", "BN" to "BND", "BI" to "BIF", "KH" to "KHR", "CM" to "XAF",
        "CA" to "CAD", "CV" to "CVE", "KY" to "KYD", "TD" to "XAF", "CL" to "CLP", "CN" to "CNY", "CO" to "COP", "KM" to "KMF",
        "CG" to "XAF", "CD" to "CDF", "CR" to "CRC", "CI" to "XOF", "CU" to "CUP", "CZ" to "CZK", "DK" to "DKK", "DJ" to "DJF",
        "DO" to "DOP", "EC" to "USD", "EG" to "EGP", "SV" to "USD", "GQ" to "XAF", "ER" to "ERN", "ET" to "ETB", "FJ" to "FJD",
        "GA" to "XAF", "GM" to "GMD", "GE" to "GEL", "GH" to "GHS", "GT" to "GTQ", "GN" to "GNF", "GW" to "XOF", "GY" to "GYD",
        "HT" to "HTG", "HN" to "HNL", "HK" to "HKD", "HU" to "HUF", "IS" to "ISK", "IN" to "INR", "ID" to "IDR", "IR" to "IRR",
        "IQ" to "IQD", "IL" to "ILS", "JM" to "JMD", "JP" to "JPY", "JO" to "JOD", "KZ" to "KZT", "KE" to "KES", "KW" to "KWD",
        "KG" to "KGS", "LA" to "LAK", "LB" to "LBP", "LS" to "LSL", "LR" to "LRD", "LY" to "LYD", "MO" to "MOP", "MG" to "MGA",
        "MW" to "MWK", "MY" to "MYR", "MV" to "MVR", "ML" to "XOF", "MR" to "MRU", "MU" to "MUR", "MX" to "MXN", "MD" to "MDL",
        "MN" to "MNT", "MA" to "MAD", "MZ" to "MZN", "MM" to "MMK", "NA" to "NAD", "NP" to "NPR", "NZ" to "NZD", "NI" to "NIO",
        "NE" to "XOF", "NG" to "NGN", "NO" to "NOK", "OM" to "OMR", "PK" to "PKR", "PA" to "USD", "PG" to "PGK", "PY" to "PYG",
        "PE" to "PEN", "PH" to "PHP", "PL" to "PLN", "QA" to "QAR", "RO" to "RON", "RU" to "RUB", "RW" to "RWF", "WS" to "WST",
        "ST" to "STN", "SA" to "SAR", "SN" to "XOF", "RS" to "RSD", "SC" to "SCR", "SL" to "SLE", "SG" to "SGD", "SB" to "SBD",
        "SO" to "SOS", "ZA" to "ZAR", "KR" to "KRW", "SS" to "SSP", "LK" to "LKR", "SD" to "SDG", "SR" to "SRD", "SZ" to "SZL",
        "SE" to "SEK", "CH" to "CHF", "SY" to "SYP", "TW" to "TWD", "TJ" to "TJS", "TZ" to "TZS", "TH" to "THB", "TG" to "XOF",
        "TO" to "TOP", "TT" to "TTD", "TN" to "TND", "TR" to "TRY", "TM" to "TMT", "UG" to "UGX", "UA" to "UAH", "AE" to "AED",
        "GB" to "GBP", "US" to "USD", "UY" to "UYU", "UZ" to "UZS", "VU" to "VUV", "VE" to "VES", "VN" to "VND", "YE" to "YER",
        "ZM" to "ZMW", "ZW" to "ZWG", "BJ" to "XOF", "BF" to "XOF", "CF" to "XAF", "MK" to "MKD", "KP" to "KPW",
        "GI" to "GIP", "GG" to "GBP", "JE" to "GBP", "IM" to "GBP", "FO" to "DKK", "GL" to "DKK",
        "PR" to "USD", "GU" to "USD", "AS" to "USD", "VI" to "USD", "MP" to "USD", "TC" to "USD", "VG" to "USD", "PW" to "USD",
        "MH" to "USD", "FM" to "USD", "TL" to "USD", "NC" to "XPF", "PF" to "XPF", "WF" to "XPF", "AW" to "AWG", "CW" to "ANG",
        "SX" to "ANG", "AI" to "XCD", "AG" to "XCD", "DM" to "XCD", "GD" to "XCD", "KN" to "XCD", "LC" to "XCD", "VC" to "XCD",
        "MS" to "XCD", "CK" to "NZD", "NU" to "NZD", "PN" to "NZD", "TV" to "AUD", "KI" to "AUD", "NR" to "AUD", "NF" to "AUD",
        "PS" to "ILS", "LI" to "CHF", "UM" to "USD", "HM" to "AUD", "EH" to "MAD", "SH" to "SHP", "FK" to "FKP", "BG" to "EUR", "HR" to "EUR", "ME" to "EUR", "XK" to "EUR", "AD" to "EUR", "MC" to "EUR",
        "SM" to "EUR", "VA" to "EUR", "AX" to "EUR", "BL" to "EUR", "MF" to "EUR", "PM" to "EUR",
    ) + EURO.associateWith { "EUR" }

    fun waehrungsCode(a2: String): String? = WAEHRUNG[a2]

    // ---- Laender-ETF (Yahoo, Notierung in US-Dollar)
    data class Etf(val symbol: String, val name: String)

    val ETF: Map<String, List<Etf>> = mapOf(
        "DE" to listOf(Etf("EWG", "iShares MSCI Germany")), "JP" to listOf(Etf("EWJ", "iShares MSCI Japan")),
        "IN" to listOf(Etf("INDA", "iShares MSCI India")), "GB" to listOf(Etf("EWU", "iShares MSCI United Kingdom")),
        "FR" to listOf(Etf("EWQ", "iShares MSCI France")), "IT" to listOf(Etf("EWI", "iShares MSCI Italy")),
        "ES" to listOf(Etf("EWP", "iShares MSCI Spain")), "CH" to listOf(Etf("EWL", "iShares MSCI Switzerland")),
        "NL" to listOf(Etf("EWN", "iShares MSCI Netherlands")), "SE" to listOf(Etf("EWD", "iShares MSCI Sweden")),
        "DK" to listOf(Etf("EDEN", "iShares MSCI Denmark")), "NO" to listOf(Etf("ENOR", "iShares MSCI Norway")),
        "FI" to listOf(Etf("EFNL", "iShares MSCI Finland")), "IE" to listOf(Etf("EIRL", "iShares MSCI Ireland")),
        "BE" to listOf(Etf("EWK", "iShares MSCI Belgium")), "AT" to listOf(Etf("EWO", "iShares MSCI Austria")),
        "PL" to listOf(Etf("EPOL", "iShares MSCI Poland")), "TR" to listOf(Etf("TUR", "iShares MSCI Turkey")),
        "GR" to listOf(Etf("GREK", "Global X MSCI Greece")), "CA" to listOf(Etf("EWC", "iShares MSCI Canada")),
        "AU" to listOf(Etf("EWA", "iShares MSCI Australia")), "NZ" to listOf(Etf("ENZL", "iShares MSCI New Zealand")),
        "BR" to listOf(Etf("EWZ", "iShares MSCI Brazil")), "MX" to listOf(Etf("EWW", "iShares MSCI Mexico")),
        "CL" to listOf(Etf("ECH", "iShares MSCI Chile")), "AR" to listOf(Etf("ARGT", "Global X MSCI Argentina")),
        "PE" to listOf(Etf("EPU", "iShares MSCI Peru and Global Exposure")),
        "CN" to listOf(Etf("MCHI", "iShares MSCI China"), Etf("FXI", "iShares China Large-Cap")),
        "HK" to listOf(Etf("EWH", "iShares MSCI Hong Kong")), "TW" to listOf(Etf("EWT", "iShares MSCI Taiwan")),
        "KR" to listOf(Etf("EWY", "iShares MSCI South Korea")), "SG" to listOf(Etf("EWS", "iShares MSCI Singapore")),
        "MY" to listOf(Etf("EWM", "iShares MSCI Malaysia")), "TH" to listOf(Etf("THD", "iShares MSCI Thailand")),
        "ID" to listOf(Etf("EIDO", "iShares MSCI Indonesia")), "PH" to listOf(Etf("EPHE", "iShares MSCI Philippines")),
        "VN" to listOf(Etf("VNM", "VanEck Vietnam")), "ZA" to listOf(Etf("EZA", "iShares MSCI South Africa")),
        "SA" to listOf(Etf("KSA", "iShares MSCI Saudi Arabia")), "AE" to listOf(Etf("UAE", "iShares MSCI UAE")),
        "QA" to listOf(Etf("QAT", "iShares MSCI Qatar")), "IL" to listOf(Etf("EIS", "iShares MSCI Israel")),
        "US" to listOf(Etf("SPY", "SPDR S&P 500")),
    )

    // ---- Weltbank: welches Jahr ist das uebliche?
    // Das juengste Jahr, fuer das mindestens ein Siebtel der Laender der Reihe einen Wert hat.
    fun ueblichesJahr(r: Weltbank.Reihe): Int? {
        if (r.werte.isEmpty()) return null
        val je = r.werte.values.groupingBy { it.jahr }.eachCount()
        val schwelle = maxOf(1, r.werte.size / 7)
        return je.filterValues { it >= schwelle }.keys.maxOrNull() ?: je.keys.maxOrNull()
    }

    // Jahr zum Wert dazuschreiben: ausser es ist das uebliche Jahr der Reihe und hoechstens zwei Jahre alt.
    fun jahrZeigen(w: Weltbank.Wert, ueblich: Int?, aktuell: Int): Boolean = !(w.jahr == ueblich && w.jahr >= aktuell - 2)

    // ---- Ergebnis fuer ein Land
    data class EtfStand(val etf: Etf, val tag: Double?, val jahr: Double?)

    // gegen: Code der Vergleichswaehrung (Heimatwaehrung des Nutzers); kurs: Einheiten der Landeswaehrung je Einheit
    // davon; kursJahr: Veraenderung der Landeswaehrung zu ihr in Prozent
    data class Markt(
        val zins: Zins? = null,
        val rendite: Pair<Kennzahl, Rendite>? = null,
        val kurs: Double? = null,
        val kursJahr: Double? = null,
        val etfs: List<EtfStand> = emptyList(),
        val gegen: String = "EUR",
    )

    data class Struktur(val werte: Map<Kennzahl, Weltbank.Wert>, val ueblich: Map<Kennzahl, Int?>)

    // ---- Kennzahlen mit Infotext (Texte in Strings: globusWirtLabel, globusWirtInfo)
    enum class Kennzahl(val weltbank: String? = null) {
        WACHSTUM, INFLATION, ARBEITSLOS, SCHULDEN, LEISTUNG, HAUSHALT, BIP_KOPF, BIP_KOPF_KKP, WELT_ANTEIL, BEVOELKERUNG,
        AGRAR("NV.AGR.TOTL.ZS"), INDUSTRIE("NV.IND.TOTL.ZS"), VERARBEITUNG("NV.IND.MANF.ZS"), DIENSTE("NV.SRV.TOTL.ZS"),
        EXPORTE("NE.EXP.GNFS.ZS"), TREIBSTOFF("TX.VAL.FUEL.ZS.UN"), ROHSTOFF("NY.GDP.TOTL.RT.ZS"), HIGHTECH("TX.VAL.TECH.MF.ZS"),
        DIREKTINVEST("BX.KLT.DINV.WD.GD.ZS"), BOERSENWERT("CM.MKT.LCAP.GD.ZS"), FIRMEN("CM.MKT.LDOM.NO"), GINI("SI.POV.GINI"),
        LEITZINS, KREDITZINS("FR.INR.LEND"), REALZINS("FR.INR.RINR"), RENDITE_EZB, RENDITE_USA, RENDITE_FRED, WAEHRUNG, WAEHRUNG_JAHR, WAEHRUNG_EURO,
    }

    // Weltbank-Reihen zu den Zinsen: weltweiter Ersatz, wo die BIS keinen Leitzins fuehrt
    val ZINS_WELTBANK = listOf(Kennzahl.KREDITZINS, Kennzahl.REALZINS)
    val STRUKTUR = Kennzahl.entries.filter { it.weltbank != null && it !in ZINS_WELTBANK }

    // Zeilen einer CSV-Datei mit Kopfzeile als Spalte -> Wert
    private fun csvZeilen(csv: String): Sequence<Map<String, String>> {
        val zeilen = csv.lineSequence().map { it.trimEnd('\r') }.filter { it.isNotBlank() }.iterator()
        if (!zeilen.hasNext()) return emptySequence()
        val kopf = zeilen.next().split(',').map { it.trim().trim('"') }
        return zeilen.asSequence().map { z ->
            val t = z.split(',')
            kopf.indices.filter { it < t.size }.associate { kopf[it] to t[it].trim().trim('"') }
        }
    }
}
