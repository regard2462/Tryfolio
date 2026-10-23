package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle

// Branchentrends ueber US-Branchen-ETFs. US-ETFs, weil es fast fuer jede Branche einen liquiden gibt und die
// Kurse frei abrufbar sind; fuer die Branche weltweit stehen sie nur ungefaehr.
object Branchentrends {

    data class Branche(val symbol: String, val de: String, val en: String)

    val BRANCHEN = listOf(
        Branche("SMH", "Halbleiter", "Semiconductors"),
        Branche("IGV", "Software", "Software"),
        Branche("XLC", "Kommunikation und Medien", "Communication services"),
        Branche("XLY", "Konsum (zyklisch)", "Consumer discretionary"),
        Branche("XLP", "Basiskonsum", "Consumer staples"),
        Branche("PPH", "Pharma", "Pharmaceuticals"),
        Branche("XBI", "Biotechnologie", "Biotechnology"),
        Branche("IHI", "Medizintechnik", "Medical devices"),
        Branche("KBE", "Banken", "Banks"),
        Branche("KIE", "Versicherungen", "Insurance"),
        Branche("XLE", "Öl und Gas", "Oil and gas"),
        Branche("ICLN", "Saubere Energie", "Clean energy"),
        Branche("XLU", "Versorger", "Utilities"),
        Branche("XLI", "Industrie", "Industrials"),
        Branche("ITA", "Rüstung und Luftfahrt", "Aerospace and defense"),
        Branche("XME", "Metalle und Bergbau", "Metals and mining"),
        Branche("GDX", "Goldminen", "Gold miners"),
        Branche("XLRE", "Immobilien", "Real estate"),
    )

    // Zeitraeume in Kalendertagen; 1 heisst seit dem vorletzten Schlusskurs. Die Kurse reichen ein Jahr zurueck.
    val ZEITRAEUME = listOf(1, 7, 30, 91, 182, 365)

    private const val TAG = 86_400_000L

    // Veraenderung in Prozent ueber tage: vom letzten Kurs, der mindestens so alt ist, bis zum juengsten. Bei einem
    // Tag vom vorletzten zum letzten Kurs. Null, wenn die Kurse nicht so weit zurueckreichen.
    fun veraenderung(kurse: List<Candle>, tage: Int): Double? {
        val k = kurse.filter { it.close.isFinite() && it.close > 0 }.sortedBy { it.t }
        if (k.size < 2) return null
        val ende = k.last()
        val start = if (tage <= 1) k[k.size - 2] else {
            val ziel = ende.t - tage * TAG
            if (k.first().t > ziel + 3 * TAG) return null
            k.lastOrNull { it.t <= ziel } ?: k.first()
        }
        return (ende.close / start.close - 1) * 100
    }
}
