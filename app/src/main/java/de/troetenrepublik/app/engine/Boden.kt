package de.troetenrepublik.app.engine

// Bodenzeichen (0.78), das Gegenstueck zum Blasen-Detektor: Messgroessen, die an frueheren Tiefpunkten des
// Aktienmarkts zusammenkamen. Bewertung tief, Renditeabstand der Aktien zu Anleihen hoch, Index stark gefallen,
// Schwankung hoch. Keines nennt den Tag des Tiefs; nach grossen Einbruechen blieben sie oft Monate erfuellt.
// Daten wie beim Blasen-Detektor: KGV des S&P 500 (SPY), Rendite zehnjaehriger US-Anleihen, S&P 500, VIX.
object Boden {

    const val KGV = "kgv"
    const val PRAEMIE = "praemie"
    const val RUECKGANG = "rueckgang"
    const val SCHWANKUNG = "schwankung"

    // Reihenfolge der Anzeige
    val ALLE = listOf(KGV, PRAEMIE, RUECKGANG, SCHWANKUNG)

    enum class Lage { ERFUELLT, NAHE, FERN }

    // zusatz: beim Rueckgang die Veraenderung ueber zwei Jahre in Prozent.
    data class Zeichen(val schluessel: String, val wert: Double, val lage: Lage, val zusatz: Double? = null)

    // Schwellen: erfuellt und nahe je Zeichen.
    const val KGV_ERFUELLT = 10.0
    const val KGV_NAHE = 14.0
    const val PRAEMIE_ERFUELLT = 5.0
    const val PRAEMIE_NAHE = 3.0
    const val JAHR_ERFUELLT = -30.0
    const val ZWEI_JAHRE_ERFUELLT = -33.0
    const val RUECKGANG_NAHE = -20.0
    const val VIX_ERFUELLT = 30.0
    const val VIX_NAHE = 20.0

    // Kurs-Gewinn-Verhaeltnis des S&P 500: unter 10 erfuellt (an den Tiefs von 1974 und 1982 einstellig), unter 14 nahe.
    fun kgv(pe: Double?): Zeichen? = pe?.takeIf { it.isFinite() && it > 0 }?.let {
        Zeichen(KGV, it, if (it < KGV_ERFUELLT) Lage.ERFUELLT else if (it < KGV_NAHE) Lage.NAHE else Lage.FERN)
    }

    // Gewinnrendite der Aktien minus Rendite zehnjaehriger US-Anleihen in Prozentpunkten (Blasen.praemie): ab 5 erfuellt,
    // ab 3 nahe.
    fun praemie(punkte: Double?): Zeichen? = punkte?.takeIf { it.isFinite() }?.let {
        Zeichen(PRAEMIE, it, if (it >= PRAEMIE_ERFUELLT) Lage.ERFUELLT else if (it >= PRAEMIE_NAHE) Lage.NAHE else Lage.FERN)
    }

    // Veraenderung des letzten Kurses gegen den Kurs vor tage Tagen in Prozent. kurse: (Zeit in ms, Schluss), aelteste
    // zuerst. Null, wenn die Reihe nicht so weit zurueckreicht (der Vergleichskurs darf hoechstens zehn Tage aelter sein).
    fun veraenderung(kurse: List<Pair<Long, Double>>, tage: Int): Double? {
        val letzter = kurse.lastOrNull()?.takeIf { it.second > 0 } ?: return null
        val ziel = letzter.first - tage * 86_400_000L
        val alt = kurse.lastOrNull { it.first <= ziel }?.takeIf { ziel - it.first <= 10 * 86_400_000L && it.second > 0 } ?: return null
        return (letzter.second / alt.second - 1) * 100
    }

    // Rueckgang des Index: erfuellt bei mindestens 30 % Minus in zwoelf Monaten oder rund einem Drittel ueber zwei Jahre,
    // nahe ab 20 % Minus in einem der beiden Zeitraeume. Null ohne den Zwoelfmonatswert.
    fun rueckgang(jahr: Double?, zweiJahre: Double?): Zeichen? {
        val a = jahr?.takeIf { it.isFinite() } ?: return null
        val b = zweiJahre?.takeIf { it.isFinite() }
        val lage = when {
            a <= JAHR_ERFUELLT || (b != null && b <= ZWEI_JAHRE_ERFUELLT) -> Lage.ERFUELLT
            a <= RUECKGANG_NAHE || (b != null && b <= RUECKGANG_NAHE) -> Lage.NAHE
            else -> Lage.FERN
        }
        return Zeichen(RUECKGANG, a, lage, b)
    }

    // Schwankung: VIX ab 30 erfuellt, ab 20 nahe (dieselben Schwellen wie das Fruehwarnzeichen).
    fun schwankung(vix: Double?): Zeichen? = vix?.takeIf { it.isFinite() && it > 0 }?.let {
        Zeichen(SCHWANKUNG, it, if (it >= VIX_ERFUELLT) Lage.ERFUELLT else if (it >= VIX_NAHE) Lage.NAHE else Lage.FERN)
    }

    enum class Stufe { KEINE, EINZELNE, VIELE }

    const val MINDESTENS = 3

    // Gesamtbild: ab drei erfuellten Zeichen viele, ab einem erfuellten oder zwei nahen einzelne, sonst keine.
    // Null bei weniger als drei Zeichen.
    fun gesamt(liste: List<Zeichen>): Stufe? {
        if (liste.size < MINDESTENS) return null
        val erfuellt = liste.count { it.lage == Lage.ERFUELLT }
        val nahe = liste.count { it.lage == Lage.NAHE }
        return when {
            erfuellt >= 3 -> Stufe.VIELE
            erfuellt >= 1 || nahe >= 2 -> Stufe.EINZELNE
            else -> Stufe.KEINE
        }
    }

    // Zeichen in der Reihenfolge der Anzeige aus den geladenen Groessen; was fehlt, fehlt.
    fun zeichen(pe: Double?, praemie: Double?, index: List<Pair<Long, Double>>, vix: Double?): List<Zeichen> = listOfNotNull(
        kgv(pe), praemie(praemie), rueckgang(veraenderung(index, 365), veraenderung(index, 730)), schwankung(vix),
    )
}
