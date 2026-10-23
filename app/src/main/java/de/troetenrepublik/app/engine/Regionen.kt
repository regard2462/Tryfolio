package de.troetenrepublik.app.engine

import java.time.LocalTime
import java.time.ZoneId

// Land des Nutzers mit Zeitzone, Waehrung und Handelszeit der wichtigsten Boerse, vorgeschlagen nach der Zeitzone.
// Deutschland: LS Exchange 7:30 bis 23:00, Hebelprodukte 8:00 bis 22:00. Sonst die Hauptsitzung der Heimatboerse
// in Ortszeit, ohne Mittagspausen und Feiertage.
object Regionen {

    data class Land(
        val code: String,
        val de: String,
        val en: String,
        val zone: String,
        val waehrung: String,
        val oeffnet: LocalTime,
        val schliesst: LocalTime,
        val boerse: String,
    ) {
        fun name(deutsch: Boolean) = if (deutsch) de else en
        val zoneId: ZoneId get() = ZoneId.of(zone)
    }

    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)

    val DE = Land("DE", "Deutschland", "Germany", "Europe/Berlin", "EUR", t(7, 30), t(23), "LS Exchange")

    val LAENDER: List<Land> = listOf(
        DE,
        Land("AT", "Österreich", "Austria", "Europe/Vienna", "EUR", t(9), t(17, 30), "Wiener Börse"),
        Land("CH", "Schweiz", "Switzerland", "Europe/Zurich", "CHF", t(9), t(17, 30), "SIX Swiss Exchange"),
        Land("US", "USA", "United States", "America/New_York", "USD", t(9, 30), t(16), "NYSE, Nasdaq"),
        Land("GB", "Großbritannien", "United Kingdom", "Europe/London", "GBP", t(8), t(16, 30), "London Stock Exchange"),
        Land("FR", "Frankreich", "France", "Europe/Paris", "EUR", t(9), t(17, 30), "Euronext Paris"),
        Land("NL", "Niederlande", "Netherlands", "Europe/Amsterdam", "EUR", t(9), t(17, 30), "Euronext Amsterdam"),
        Land("BE", "Belgien", "Belgium", "Europe/Brussels", "EUR", t(9), t(17, 30), "Euronext Brussels"),
        Land("LU", "Luxemburg", "Luxembourg", "Europe/Luxembourg", "EUR", t(9), t(17, 30), "Euronext"),
        Land("IT", "Italien", "Italy", "Europe/Rome", "EUR", t(9), t(17, 30), "Borsa Italiana"),
        Land("ES", "Spanien", "Spain", "Europe/Madrid", "EUR", t(9), t(17, 30), "Bolsa de Madrid"),
        Land("PT", "Portugal", "Portugal", "Europe/Lisbon", "EUR", t(8), t(16, 30), "Euronext Lisbon"),
        Land("IE", "Irland", "Ireland", "Europe/Dublin", "EUR", t(8), t(16, 30), "Euronext Dublin"),
        Land("SE", "Schweden", "Sweden", "Europe/Stockholm", "SEK", t(9), t(17, 30), "Nasdaq Stockholm"),
        Land("DK", "Dänemark", "Denmark", "Europe/Copenhagen", "DKK", t(9), t(17), "Nasdaq Copenhagen"),
        Land("NO", "Norwegen", "Norway", "Europe/Oslo", "NOK", t(9), t(16, 20), "Oslo Børs"),
        Land("FI", "Finnland", "Finland", "Europe/Helsinki", "EUR", t(10), t(18, 30), "Nasdaq Helsinki"),
        Land("PL", "Polen", "Poland", "Europe/Warsaw", "PLN", t(9), t(17), "GPW Warschau"),
        Land("CZ", "Tschechien", "Czechia", "Europe/Prague", "CZK", t(9), t(16, 20), "Prager Börse"),
        Land("HU", "Ungarn", "Hungary", "Europe/Budapest", "HUF", t(9), t(17), "Budapester Börse"),
        Land("CA", "Kanada", "Canada", "America/Toronto", "CAD", t(9, 30), t(16), "Toronto Stock Exchange"),
        Land("MX", "Mexiko", "Mexico", "America/Mexico_City", "MXN", t(8, 30), t(15), "Bolsa Mexicana"),
        Land("BR", "Brasilien", "Brazil", "America/Sao_Paulo", "BRL", t(10), t(17), "B3"),
        Land("JP", "Japan", "Japan", "Asia/Tokyo", "JPY", t(9), t(15, 30), "Tokyo Stock Exchange"),
        Land("CN", "China", "China", "Asia/Shanghai", "CNY", t(9, 30), t(15), "Shanghai, Shenzhen"),
        Land("HK", "Hongkong", "Hong Kong", "Asia/Hong_Kong", "HKD", t(9, 30), t(16), "HKEX"),
        Land("TW", "Taiwan", "Taiwan", "Asia/Taipei", "TWD", t(9), t(13, 30), "Taiwan Stock Exchange"),
        Land("KR", "Südkorea", "South Korea", "Asia/Seoul", "KRW", t(9), t(15, 30), "Korea Exchange"),
        Land("IN", "Indien", "India", "Asia/Kolkata", "INR", t(9, 15), t(15, 30), "NSE, BSE"),
        Land("SG", "Singapur", "Singapore", "Asia/Singapore", "SGD", t(9), t(17), "SGX"),
        Land("IL", "Israel", "Israel", "Asia/Jerusalem", "ILS", t(9, 59), t(17, 25), "Tel Aviv Stock Exchange"),
        Land("ZA", "Südafrika", "South Africa", "Africa/Johannesburg", "ZAR", t(9), t(17), "JSE"),
        Land("AU", "Australien", "Australia", "Australia/Sydney", "AUD", t(10), t(16), "ASX"),
        Land("NZ", "Neuseeland", "New Zealand", "Pacific/Auckland", "NZD", t(10), t(16, 45), "NZX"),
    )

    // Waehrungen zur Wahl: Euro und alle, fuer die die App Wechselkurse laedt.
    val WAEHRUNGEN: List<String> = listOf(
        "EUR", "USD", "CHF", "GBP", "SEK", "DKK", "NOK", "PLN", "CZK", "HUF", "CAD", "MXN", "BRL",
        "JPY", "CNY", "HKD", "TWD", "KRW", "INR", "SGD", "ILS", "ZAR", "AUD", "NZD",
    )

    fun land(code: String): Land = LAENDER.firstOrNull { it.code == code } ?: DE

    // Land aus den Einstellungen fuer Stellen ohne Zugriff darauf, etwa das Finanzwissen.
    @Volatile var aktuell: String = DE.code

    // Dieselbe Zone, sonst ein Land, dessen Zonen so beginnen (US-Zonen ausser Toronto, Mexiko und Sao Paulo),
    // sonst das Land der Systemsprache, sonst Deutschland.
    fun vorschlag(zone: String = ZoneId.systemDefault().id, landDerSprache: String = java.util.Locale.getDefault().country): String {
        LAENDER.firstOrNull { it.zone == zone }?.let { return it.code }
        ZONEN[zone]?.let { return it }
        if (zone.startsWith("America/") && US_ZONEN.any { zone.startsWith(it) }) return "US"
        if (zone.startsWith("Australia/")) return "AU"
        if (zone.startsWith("Canada/")) return "CA"
        return LAENDER.firstOrNull { it.code == landDerSprache.uppercase() }?.code ?: DE.code
    }

    private val US_ZONEN = listOf(
        "America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles", "America/Phoenix", "America/Anchorage",
        "America/Detroit", "America/Indiana", "America/Kentucky", "America/Boise", "America/North_Dakota", "America/Juneau",
        "America/Adak", "America/Menominee", "America/Nome", "America/Sitka", "America/Yakutat", "America/Metlakatla",
    )

    private val ZONEN = mapOf(
        "Pacific/Honolulu" to "US", "US/Eastern" to "US", "US/Central" to "US", "US/Mountain" to "US", "US/Pacific" to "US",
        "America/Vancouver" to "CA", "America/Edmonton" to "CA", "America/Winnipeg" to "CA", "America/Halifax" to "CA",
        "America/St_Johns" to "CA", "America/Regina" to "CA", "America/Montreal" to "CA",
        "Europe/Busingen" to "DE", "Asia/Calcutta" to "IN", "Asia/Chongqing" to "CN", "Asia/Harbin" to "CN", "Asia/Urumqi" to "CN",
        "Asia/Macau" to "HK", "Europe/Belfast" to "GB", "Asia/Tel_Aviv" to "IL", "America/Monterrey" to "MX", "America/Cancun" to "MX",
        "America/Tijuana" to "MX", "America/Fortaleza" to "BR", "America/Recife" to "BR", "America/Manaus" to "BR",
    )
}
