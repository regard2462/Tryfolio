package de.troetenrepublik.app.market

// Verbraucherbeschwerden der US-Behoerde CFPB bei Banken und Finanzdienstleistern: nur ein Link auf die oeffentliche
// Suchseite der Beschwerdedatenbank, die App ruft nichts ab (die Schnittstelle sperrt automatische Abrufe).
object Verbraucherbeschwerden {
    const val SUCHSEITE = "https://www.consumerfinance.gov/data-research/consumer-complaints/search/"

    private val branche = Regex("banks|credit services|mortgage finance|consumer lending", RegexOption.IGNORE_CASE)

    // Ob die Branche einer Aktie zur CFPB passt (Yahoo-Branche).
    fun betroffen(industrie: String): Boolean = branche.containsMatchIn(industrie)

    // Suchseite mit dem geglaetteten Firmennamen als Suchwort; ohne Namen keine Adresse.
    fun adresse(firma: String): String? {
        val n = Fda.normal(firma)
        if (n.isBlank()) return null
        return SUCHSEITE + "?searchField=company&searchText=" + java.net.URLEncoder.encode(n, "UTF-8") + "&from=0&size=25"
    }
}
