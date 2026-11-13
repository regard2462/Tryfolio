package de.troetenrepublik.app.engine

// ETF-Durchschau (seit 5.91): was in einem ETF steckt (groesste Positionen, Branchen, Aktien/Anleihen/Bargeld) und wie
// viel vom Depot ueber alle ETFs zusammen und direkt in einer einzelnen Firma steckt. Yahoo nennt nur die zehn
// groessten Positionen eines Fonds; der Rest bleibt unbekannt, die Summen sind deshalb Untergrenzen.
object Durchschau {
    // anteil als Anteil am Fonds (0,058 = 5,8 %).
    data class Position(val symbol: String, val name: String, val anteil: Double)

    data class Bestand(
        val positionen: List<Position>,
        // Yahoo-Sektorname (wie in Sectors.kt, etwa „Technology“) und Anteil.
        val branchen: List<Pair<String, Double>>,
        val aktien: Double,
        val anleihen: Double,
        val bar: Double,
        // Laufende Kosten (TER) in Prozent pro Jahr, seit 5.94; null ohne Angabe
        val ter: Double? = null,
    )

    // Yahoo-Schluessel der Sektorgewichte in die Namen, die die App sonst verwendet.
    private val SEKTOREN = mapOf(
        "realestate" to "Real Estate", "consumer_cyclical" to "Consumer Cyclical", "basic_materials" to "Basic Materials",
        "consumer_defensive" to "Consumer Defensive", "technology" to "Technology", "communication_services" to "Communication Services",
        "financial_services" to "Financial Services", "utilities" to "Utilities", "industrials" to "Industrials",
        "energy" to "Energy", "healthcare" to "Healthcare",
    )

    fun sektor(schluessel: String): String = SEKTOREN[schluessel] ?: schluessel

    // Wie viel vom Depot in einer Firma steckt: direkt gehalten und ueber ETFs (Gewicht des ETFs mal Anteil der Firma
    // im ETF).
    data class Wirkung(val symbol: String, val name: String, val direkt: Double, val ueberEtfs: Double, val etfs: List<String>) {
        val gesamt: Double get() = direkt + ueberEtfs
    }

    // gewichte: Anteil jeder Position am Depot (Symbol zu Anteil, Summe 1). bestaende: bekannte ETF-Bestaende.
    // namen: Anzeigenamen gehaltener Werte. Nur Firmen, die in mindestens einem ETF stecken; groesste zuerst.
    fun wirkung(gewichte: Map<String, Double>, bestaende: Map<String, Bestand>, namen: Map<String, String> = emptyMap()): List<Wirkung> {
        val ueber = HashMap<String, Double>()
        val quellen = HashMap<String, MutableList<String>>()
        val name = HashMap<String, String>()
        for ((etf, b) in bestaende) {
            val g = gewichte[etf] ?: continue
            for (p in b.positionen) {
                val s = gleich(p.symbol)
                ueber[s] = (ueber[s] ?: 0.0) + g * p.anteil
                quellen.getOrPut(s) { mutableListOf() }.add(etf)
                name.putIfAbsent(s, p.name)
            }
        }
        val direkt = gewichte.entries.groupBy({ gleich(it.key) }, { it.value }).mapValues { it.value.sum() }
        return ueber.map { (s, u) -> Wirkung(s, name[s] ?: namen[s] ?: s, direkt[s] ?: 0.0, u, quellen[s].orEmpty()) }
            .sortedByDescending { it.gesamt }
    }

    // Kuerzel ohne Boersenendung, damit etwa SAP.DE im Depot und SAP im ETF zusammenfallen. Deutsche Kuerzel fuer
    // US-Werte (APC.DE fuer Apple) passen nicht zusammen.
    fun gleich(symbol: String): String = symbol.uppercase().substringBefore('.')
}
