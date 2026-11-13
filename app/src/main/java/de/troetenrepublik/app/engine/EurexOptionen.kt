package de.troetenrepublik.app.engine

// Ersatz fuer Aktien ohne US-Optionen [0.76]: Aktienoptionen an der Eurex (Deutschland, Schweiz und weitere). Die
// Produktseite zur ISIN nennt das Produktkuerzel, der Snapshot-Bericht der Eurex (Textdatei, einmal am Tag mit
// neuem Link) fuehrt je Produkt Umsatz und offene Kontrakte nach Calls und Puts. Hier wird nur gelesen und gerechnet.
object EurexOptionen {

    // Seite mit den Links der Berichte (der juengste steht am Ende)
    const val BERICHTE_SEITE = "https://www.eurex.com/ex-en/data/statistics/snapshot-summary-report"

    // Die Seite ist knapp 150 KB gross, die Links stehen im hinteren Drittel
    const val SEITE_MAX = 300_000

    // Der Optionsteil des Berichts ist etwa 90 KB gross und steht am Anfang
    const val BERICHT_MAX = 220_000

    // Produktseite zur ISIN der Aktie; nur der Anfang mit der Beschreibung wird gelesen.
    fun produktUrl(isin: String): String = "https://www.eurex.com/ex-en/markets/productSearch/product!" + isin.filter { it.isLetterOrDigit() }
    const val PRODUKT_MAX = 8_000

    // Kuerzel der Standard-Aktienoption aus der Beschreibung der Produktseite ("Trade Nestle (NESN) on Eurex. ... ISIN CH0038863350.").
    // null, wenn die Seite zu einer anderen ISIN gehoert oder kein Kuerzel nennt.
    fun produktCode(seite: String, isin: String): String? {
        val m = Regex("\\(([A-Z0-9]{2,6})\\) on Eurex[^\"]*?ISIN\\s+([A-Z0-9]{12})").find(seite) ?: return null
        return m.groupValues[1].takeIf { m.groupValues[2] == isin.uppercase() }
    }

    // Adresse des juengsten Berichts aus der Seite der Berichte; null, wenn keiner verlinkt ist.
    fun berichtUrl(seite: String): String? =
        Regex("https://www\\.eurex\\.com/resource/blob/\\d+/[0-9a-f]+/data/sr(\\d{8})\\.txt").findAll(seite)
            .maxByOrNull { it.groupValues[1] }?.value

    private val ZEILE = Regex("^(\\S+)\\s+\\d+\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+\\d+\\s+\\d+\\s*$", RegexOption.MULTILINE)

    // Alle Produkte des Optionsteils: Kuerzel zu Zahlen. Leer, wenn der Text kein Bericht ist.
    fun lesen(bericht: String): Map<String, OptionsZahlen> {
        val start = bericht.indexOf("per Product (Options)")
        if (start < 0) return emptyMap()
        val ende = bericht.indexOf("SUM OF OPTIONS PRODUCTS", start).let { if (it < 0) bericht.length else it }
        val aus = HashMap<String, OptionsZahlen>()
        for (m in ZEILE.findAll(bericht.substring(start, ende))) {
            val g = m.groupValues
            aus[g[1]] = OptionsZahlen(g[2].toLong(), g[3].toLong(), g[4].toLong(), g[5].toLong())
        }
        return aus
    }
}
