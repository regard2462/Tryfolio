package de.troetenrepublik.app.engine

// FRED (Federal Reserve Bank of St. Louis): Die Bedingungen verbieten Speichern und Zwischenspeichern der Daten und
// verlangen eine Quellenangabe. Abrufe von FRED und ALFRED bleiben deshalb nur fuer die Dauer der offenen Ansicht im
// Speicher; hier stehen die Erkennung solcher Adressen und die Zitierung je Reihe.
object FredRegeln {
    const val SEITE = "https://fred.stlouisfed.org"
    const val ANBIETER = "Federal Reserve Bank of St. Louis"

    fun serienSeite(id: String) = "$SEITE/series/$id"

    // true fuer Adressen von FRED, ALFRED und der FRED-Schnittstelle
    fun istFred(url: String): Boolean {
        val u = url.lowercase()
        return listOf("fred.stlouisfed.org", "alfred.stlouisfed.org", "api.stlouisfed.org").any { u.startsWith("https://$it/") || u.startsWith("http://$it/") }
    }

    // Kennung der ersten Reihe einer FRED-Adresse (fredgraph.csv?id=..., series/...); null ohne Reihe
    fun reiheAus(url: String): String? {
        Regex("[?&]id=([A-Za-z0-9_]+)").find(url)?.let { return it.groupValues[1] }
        return Regex("/series/([A-Za-z0-9_]+)").find(url)?.groupValues?.get(1)
    }

    private val FED = "Board of Governors of the Federal Reserve System (US)"
    private val OECD = "Organisation for Economic Co-operation and Development"

    // Eigentuemer der Reihen, soweit bekannt; sonst steht nur die Kennung in der Zitierung
    private val EIGENTUEMER = mapOf(
        "STLFSI4" to ANBIETER,
        "T10Y3M" to ANBIETER,
        "T10YIE" to ANBIETER,
        "SAHMREALTIME" to ANBIETER,
        "BAMLH0A0HYM2" to "ICE Data Indices, LLC",
        "UMCSENT" to "University of Michigan",
        "ICSA" to "U.S. Employment and Training Administration",
        "GDP" to "U.S. Bureau of Economic Analysis",
        "GDPNOW" to "Federal Reserve Bank of Atlanta",
        "CPIAUCNS" to "U.S. Bureau of Labor Statistics",
        "DFF" to FED, "DGS10" to FED, "DGS1" to FED, "DFII10" to FED, "IPG3344S" to FED,
        "BOGZ1FL663067003Q" to FED,
        "TSIFRGHT" to "U.S. Bureau of Transportation Statistics",
    )

    fun eigentuemer(id: String): String? =
        EIGENTUEMER[id] ?: OECD.takeIf { Regex("IRLTLT01[A-Z]{2}M156N").matches(id) }

    // „<Eigentuemer>, <Reihe>, retrieved from FRED, Federal Reserve Bank of St. Louis“ (deutsch: „abgerufen ueber FRED“)
    fun zitat(id: String, deutsch: Boolean): String =
        (eigentuemer(id)?.let { "$it, " } ?: "") + id + ", " + (if (deutsch) "abgerufen über FRED" else "retrieved from FRED") + ", " + ANBIETER
}
