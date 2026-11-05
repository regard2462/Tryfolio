package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle

// Branchentrends ueber US-Branchen-ETFs. US-ETFs, weil es fast fuer jede Branche einen liquiden gibt und die
// Kurse frei abrufbar sind; fuer die Branche weltweit stehen sie nur ungefaehr.
object Branchentrends {

    // Art der Branche fuer die Sektorrotation: defensiv (gefragt, wenn Anleger Sicherheit suchen), zyklisch (laeuft
    // mit der Konjunktur), Wachstum (Technik, lebt von kuenftigen Gewinnen), Rohstoffe, zinsabhaengig.
    enum class Art { DEFENSIV, ZYKLISCH, WACHSTUM, ROHSTOFF, ZINS }

    data class Branche(val symbol: String, val de: String, val en: String, val art: Art = Art.ZYKLISCH)

    val BRANCHEN = listOf(
        Branche("SMH", "Halbleiter", "Semiconductors", Art.WACHSTUM),
        Branche("IGV", "Software", "Software", Art.WACHSTUM),
        Branche("XLC", "Kommunikation und Medien", "Communication services", Art.WACHSTUM),
        Branche("XLY", "Konsum (zyklisch)", "Consumer discretionary", Art.ZYKLISCH),
        Branche("XLP", "Basiskonsum", "Consumer staples", Art.DEFENSIV),
        Branche("PPH", "Pharma", "Pharmaceuticals", Art.DEFENSIV),
        Branche("XBI", "Biotechnologie", "Biotechnology", Art.WACHSTUM),
        Branche("IHI", "Medizintechnik", "Medical devices", Art.DEFENSIV),
        Branche("KBE", "Banken", "Banks", Art.ZYKLISCH),
        Branche("KIE", "Versicherungen", "Insurance", Art.DEFENSIV),
        Branche("XLE", "Öl und Gas", "Oil and gas", Art.ROHSTOFF),
        Branche("ICLN", "Saubere Energie", "Clean energy", Art.ZINS),
        Branche("XLU", "Versorger", "Utilities", Art.DEFENSIV),
        Branche("XLI", "Industrie", "Industrials", Art.ZYKLISCH),
        Branche("ITA", "Rüstung und Luftfahrt", "Aerospace and defense", Art.ZYKLISCH),
        Branche("XME", "Metalle und Bergbau", "Metals and mining", Art.ROHSTOFF),
        Branche("GDX", "Goldminen", "Gold miners", Art.ROHSTOFF),
        Branche("XLRE", "Immobilien", "Real estate", Art.ZINS),
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

    // Wie sich ein Betrag in der Branche entwickelt haette: der Kursverlauf ueber tage auf start umgerechnet, aelteste
    // zuerst. Leer, wenn die Kurse nicht so weit zurueckreichen (siehe veraenderung).
    fun depot(kurse: List<Candle>, tage: Int, start: Double = 1000.0): List<Pair<Long, Double>> {
        val k = kurse.filter { it.close.isFinite() && it.close > 0 }.sortedBy { it.t }
        if (k.size < 2) return emptyList()
        val ende = k.last()
        val ab = if (tage <= 1) k.size - 2 else {
            val ziel = ende.t - tage * TAG
            if (k.first().t > ziel + 3 * TAG) return emptyList()
            k.indexOfLast { it.t <= ziel }.coerceAtLeast(0)
        }
        val basis = k[ab].close
        return k.subList(ab, k.size).map { it.t to it.close / basis * start }
    }

    // Grund einer Sektorrotation, aus der Art der Branchen, in die Geld fliesst, und dem Umfeld.
    enum class Grund { SICHERHEIT, RISIKOFREUDE, INFLATION, ABSICHERUNG, ZINSSENKUNG, ZINSANSTIEG, UNKLAR }

    // nach: Branchen, die zuletzt deutlich besser liefen als zuvor (Geld fliesst hinein), aus: die deutlich schwaecheren.
    data class Rotation(val nach: List<Branche>, val aus: List<Branche>, val grund: Grund)

    // Sektorrotation: je Branche die Rendite des letzten Monats gegen den Schnitt aller Branchen (relative Staerke) und
    // wie sehr sie sich gegenueber den zwei Monaten davor verbessert hat. In die drei Branchen mit der groessten
    // Verbesserung und positiver Staerke fliesst Geld, aus den drei schwaechsten heraus; unter 1,5 Prozentpunkten zaehlt
    // es nicht. vix und zins: Veraenderung des VIX (Punkte) und der Rendite zehnjaehriger US-Anleihen (Prozentpunkte)
    // ueber einen Monat, null wenn unbekannt. Null ohne genug Kurse.
    fun rotation(kurse: Map<String, List<Candle>>, vix: Double? = null, zins: Double? = null): Rotation? {
        val werte = BRANCHEN.mapNotNull { b ->
            val k = kurse[b.symbol] ?: return@mapNotNull null
            val m1 = veraenderung(k, 30) ?: return@mapNotNull null
            val m3 = veraenderung(k, 91) ?: return@mapNotNull null
            Triple(b, m1, m3)
        }
        if (werte.size < 6) return null
        val s1 = werte.map { it.second }.average()
        val s3 = werte.map { it.third }.average()
        // Relative Staerke im letzten Monat und Verbesserung gegenueber dem Monatsschnitt der zwei Monate davor
        val wechsel = werte.map { (b, m1, m3) ->
            val r1 = m1 - s1
            val r3 = m3 - s3
            Triple(b, r1, r1 - (r3 - r1) / 2)
        }
        val nach = wechsel.filter { it.second > 0 && it.third >= 1.5 }.sortedByDescending { it.third }.take(3).map { it.first }
        val aus = wechsel.filter { it.second < 0 && it.third <= -1.5 }.sortedBy { it.third }.take(3).map { it.first }
        if (nach.isEmpty() && aus.isEmpty()) return Rotation(emptyList(), emptyList(), Grund.UNKLAR)
        return Rotation(nach, aus, grund(nach, aus, vix, zins))
    }

    // Heuristik fuer das Warum: wohin das Geld ueberwiegend fliesst und woher, dazu VIX und Zinsen.
    fun grund(nach: List<Branche>, aus: List<Branche>, vix: Double?, zins: Double?): Grund {
        fun anteil(l: List<Branche>, vararg arten: Art) = if (l.isEmpty()) 0.0 else l.count { it.art in arten }.toDouble() / l.size
        val angst = (vix ?: 0.0) >= 2.0
        val zinsHoch = (zins ?: 0.0) >= 0.2
        val zinsRunter = (zins ?: 0.0) <= -0.2
        return when {
            nach.any { it.symbol == "GDX" } && angst -> Grund.ABSICHERUNG
            anteil(nach, Art.DEFENSIV) >= 0.5 && anteil(aus, Art.WACHSTUM, Art.ZYKLISCH, Art.ROHSTOFF) >= 0.5 -> Grund.SICHERHEIT
            anteil(nach, Art.DEFENSIV) >= 0.5 && angst -> Grund.SICHERHEIT
            anteil(nach, Art.ROHSTOFF) >= 0.5 -> if (angst) Grund.ABSICHERUNG else Grund.INFLATION
            anteil(nach, Art.ZINS) >= 0.5 && !zinsHoch -> Grund.ZINSSENKUNG
            anteil(nach, Art.ZINS, Art.DEFENSIV) >= 0.5 && zinsRunter -> Grund.ZINSSENKUNG
            nach.any { it.symbol == "KBE" || it.symbol == "KIE" } && zinsHoch -> Grund.ZINSANSTIEG
            anteil(nach, Art.WACHSTUM, Art.ZYKLISCH) >= 0.5 && anteil(aus, Art.DEFENSIV, Art.ZINS, Art.ROHSTOFF) >= 0.5 -> Grund.RISIKOFREUDE
            anteil(nach, Art.WACHSTUM, Art.ZYKLISCH) >= 0.67 && !angst -> Grund.RISIKOFREUDE
            else -> Grund.UNKLAR
        }
    }
}
