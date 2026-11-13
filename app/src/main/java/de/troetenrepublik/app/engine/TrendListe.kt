package de.troetenrepublik.app.engine

// Gemeinsame Liste „Branchen und Themen“ im Reiter Trends: Branchen (US-Branchen-ETFs), Themenkoerbe (Aktienlisten) und
// Themen der Durchbrueche in einer Liste. Was dasselbe meint, steht in einer Zeile und zeigt die Kennzahlen aller
// Seiten: Kurs der Branche, Median und Breite des Korbs, Zahl neuer Durchbrueche.
object TrendListe {

    // Eine Zeile. branche: Kuerzel des Branchen-ETF, korb: Schluessel des Themenkorbs, durchbrueche: Kennungen der
    // Themen. etf: Themen-ETF einer Zeile ohne Branche; er liefert Logo, KGV, Dividende, Kurs und Verlauf wie ein
    // Branchen-ETF. Ohne passenden, liquiden ETF bleibt er leer (Abnehmmittel, Wasserstoff), dann steht der Median des
    // Korbs als Kurs. de/en nur, wo keiner der Namen der Teile fuer die ganze Zeile passt.
    class Eintrag(
        val id: String,
        val branche: String? = null,
        val korb: String? = null,
        val durchbrueche: List<String> = emptyList(),
        val de: String = "",
        val en: String = "",
        val etf: String? = null,
    ) {
        // Fonds der Zeile: Branchen-ETF, sonst Themen-ETF
        val fonds: String? get() = branche ?: etf
        val teile: Int get() = (if (branche != null) 1 else 0) + (if (korb != null) 1 else 0) + durchbrueche.size
    }

    // Zuordnung Branche, Korb, Durchbruch-Thema. Jede Branche, jeder Korb und jedes Thema steht genau einmal; seit 0.79
    // hat jede Zeile mindestens ein Thema der Durchbrueche.
    val EINTRAEGE: List<Eintrag> = listOf(
        Eintrag("halbleiter", branche = "SMH", korb = "HALBLEITER", durchbrueche = listOf("halbleiter")),
        Eintrag("ki", korb = "KI", durchbrueche = listOf("ki"), etf = "AIQ"),
        Eintrag("software", branche = "IGV", durchbrueche = listOf("software")),
        Eintrag("cyber", korb = "CYBER", durchbrueche = listOf("cyber"), etf = "CIBR"),
        Eintrag("quanten", durchbrueche = listOf("quanten"), etf = "QTUM"),
        Eintrag("kommunikation", branche = "XLC", durchbrueche = listOf("kommunikation")),
        Eintrag("spiele", korb = "SPIELE", durchbrueche = listOf("spiele"), etf = "ESPO"),
        Eintrag("robotik", korb = "ROBOTIK", durchbrueche = listOf("robotik"), etf = "BOTZ"),
        Eintrag("konsum", branche = "XLY", durchbrueche = listOf("konsum")),
        Eintrag("basiskonsum", branche = "XLP", durchbrueche = listOf("basiskonsum")),
        Eintrag("pharma", branche = "PPH", durchbrueche = listOf("medizin"), de = "Pharma und Medizin", en = "Pharma and medicine"),
        Eintrag("abnehmen", korb = "ABNEHMEN", durchbrueche = listOf("abnehmen")),
        Eintrag("biotech", branche = "XBI", durchbrueche = listOf("krebs", "genetik")),
        Eintrag("medizintechnik", branche = "IHI", durchbrueche = listOf("medizintechnik")),
        Eintrag("banken", branche = "KBE", durchbrueche = listOf("banken")),
        Eintrag("versicherungen", branche = "KIE", durchbrueche = listOf("versicherungen")),
        Eintrag("krypto", korb = "KRYPTO", durchbrueche = listOf("krypto"), etf = "BLOK"),
        Eintrag("oel", branche = "XLE", durchbrueche = listOf("oel")),
        Eintrag("energie", branche = "ICLN", durchbrueche = listOf("energie")),
        Eintrag("solar", korb = "SOLAR", durchbrueche = listOf("solar"), etf = "TAN"),
        Eintrag("wasserstoff", korb = "WASSERSTOFF", durchbrueche = listOf("wasserstoff")),
        Eintrag("kernenergie", korb = "KERNENERGIE", durchbrueche = listOf("fusion"), etf = "URA", de = "Kernenergie und Fusion", en = "Nuclear energy and fusion"),
        Eintrag("batterie", durchbrueche = listOf("batterie"), etf = "LIT"),
        Eintrag("versorger", branche = "XLU", durchbrueche = listOf("versorger")),
        Eintrag("industrie", branche = "XLI", durchbrueche = listOf("industrie")),
        Eintrag("ruestung", branche = "ITA", korb = "VERTEIDIGUNG", durchbrueche = listOf("ruestung")),
        Eintrag("raumfahrt", korb = "WELTRAUM", durchbrueche = listOf("raumfahrt"), etf = "UFO"),
        Eintrag("metalle", branche = "XME", durchbrueche = listOf("metalle")),
        Eintrag("goldminen", branche = "GDX", durchbrueche = listOf("goldminen")),
        Eintrag("materialien", durchbrueche = listOf("materialien"), etf = "XLB"),
        Eintrag("klima", durchbrueche = listOf("klima"), etf = "MOO"),
        Eintrag("immobilien", branche = "XLRE", durchbrueche = listOf("immobilien")),
    )

    fun eintrag(id: String): Eintrag? = EINTRAEGE.firstOrNull { it.id == id }

    // Themen-ETFs der Zeilen ohne Branche, fuer den Abruf von Kursen und Kennzahlen
    val THEMEN_ETFS: List<String> = EINTRAEGE.mapNotNull { it.etf }

    // Zeile, in der ein Thema der Durchbrueche steht
    fun zuDurchbruch(thema: String): Eintrag? = EINTRAEGE.firstOrNull { thema in it.durchbrueche }

    // Name der Zeile: eigener Name, sonst Branche, sonst das erste Thema. null bei einer Zeile nur aus einem Korb, dessen
    // Name aus den Texten der Oberflaeche kommt.
    fun name(e: Eintrag, deutsch: Boolean): String? {
        val eigen = if (deutsch) e.de else e.en
        if (eigen.isNotEmpty()) return eigen
        e.branche?.let { s -> Branchentrends.BRANCHEN.firstOrNull { it.symbol == s } }?.let { return if (deutsch) it.de else it.en }
        return e.durchbrueche.firstOrNull()?.let { Durchbrueche.thema(it) }?.name(deutsch)
    }

    // Eine Zeile mit ihren Daten. fonds: Kuerzel des ETF der Zeile (Branche oder Thema), nur mit Kurs. kurs: seine
    // Veraenderung im Zeitraum (Prozent), korb: Auswertung des Korbs, bloecke: Themen der Durchbrueche mit Meldungen
    // oder gepflegtem Inhalt.
    class Zeile(
        val eintrag: Eintrag,
        val branche: Branchentrends.Branche?,
        val fonds: String?,
        val kurs: Double?,
        val korb: Themenkoerbe.Ergebnis?,
        val bloecke: List<Durchbrueche.Block>,
    ) {
        // Zahl rechts in der Zeile: Kurs des ETF, ohne ETF der Median des Korbs
        val wert: Double? get() = kurs ?: korb?.median
        val neu: Int get() = bloecke.sumOf { it.neu.size }
        // Median des Korbs als eigene Angabe, wenn rechts schon der Kurs des ETF steht
        val korbMedian: Double? get() = if (kurs != null) korb?.median else null
        // Nur ein Teil: sein Inhalt steht beim Aufklappen direkt da, ohne Zwischenzeile
        val einTeil: Boolean get() = (if (fonds != null) 1 else 0) + (if (korb != null) 1 else 0) + bloecke.size == 1
    }

    // Die Liste. kurs: Veraenderung je ETF (Branchen und Themen). Zeilen mit Kurs oder Median zuerst, der staerkste Wert oben; danach Zeilen ohne Zahl, die mit den
    // meisten neuen Durchbruechen zuerst, sonst in der Reihenfolge der Tabelle. Teile ohne Daten fehlen in der Zeile,
    // Zeilen ganz ohne Daten fehlen in der Liste.
    fun zeilen(kurs: Map<String, Double>, koerbe: Map<String, Themenkoerbe.Ergebnis>, bloecke: List<Durchbrueche.Block>): List<Zeile> {
        val jeThema = bloecke.associateBy { it.thema.id }
        val alle = EINTRAEGE.mapNotNull { e ->
            val k = e.fonds?.let { kurs[it] }?.takeIf { it.isFinite() }
            val b = if (k != null) Branchentrends.BRANCHEN.firstOrNull { it.symbol == e.branche } else null
            val fonds = if (k != null) e.fonds else null
            val korb = e.korb?.let { koerbe[it] }?.takeIf { it.anzahl > 0 }
            val db = e.durchbrueche.mapNotNull { jeThema[it] }
            if (fonds == null && korb == null && db.isEmpty()) null else Zeile(e, b, fonds, k, korb, db)
        }
        val (mit, ohne) = alle.partition { it.wert != null }
        return mit.sortedByDescending { it.wert!! } + ohne.sortedByDescending { it.neu }
    }
}
