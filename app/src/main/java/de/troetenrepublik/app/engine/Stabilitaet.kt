package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Bewertungsblick.Ampel

// Stabilitaet der Bilanz aus Schulden, Kasse, Eigenkapital, freiem Cashflow und EBITDA: Nettoschulden zu Eigenkapital,
// Jahre bis zur Tilgung aus dem freien Cashflow und Nettoschulden zu EBITDA, je mit Ampel. Reine Rechnung, alle
// Betraege in derselben Waehrung.
object Stabilitaet {

    // Nettoschulden zu Eigenkapital in Prozent: bis GUT sehr gut, ueber KRITISCH kritisch.
    const val GEARING_GUT = 20.0
    const val GEARING_KRITISCH = 100.0

    // Nettoschulden durch freien Cashflow in Jahren: unter GUT sehr gut, ab KRITISCH kritisch.
    const val TILGUNG_GUT = 2.0
    const val TILGUNG_KRITISCH = 5.0

    // Nettoschulden zu EBITDA: ab diesem Vielfachen kritisch.
    const val EBITDA_KRITISCH = 3.0

    enum class Kennzahl { GEARING, TILGUNG, EBITDA }

    // WERT: die Kennzahl steht in wert. KEINE_NETTOSCHULDEN: die Kasse deckt die Schulden. NENNER_NEGATIV: Eigenkapital,
    // freier Cashflow oder EBITDA sind nicht positiv, die Schulden lassen sich daran nicht messen.
    enum class Lage { WERT, KEINE_NETTOSCHULDEN, NENNER_NEGATIV }

    data class Zeile(val kennzahl: Kennzahl, val lage: Lage, val wert: Double, val ampel: Ampel)

    fun gearingAmpel(prozent: Double): Ampel = when {
        prozent <= GEARING_GUT -> Ampel.GRUEN
        prozent <= GEARING_KRITISCH -> Ampel.GELB
        else -> Ampel.ROT
    }

    fun tilgungAmpel(jahre: Double): Ampel = when {
        jahre < TILGUNG_GUT -> Ampel.GRUEN
        jahre < TILGUNG_KRITISCH -> Ampel.GELB
        else -> Ampel.ROT
    }

    fun ebitdaAmpel(vielfaches: Double): Ampel = if (vielfaches >= EBITDA_KRITISCH) Ampel.ROT else Ampel.GRUEN

    private fun zahl(v: Double?): Double? = v?.takeIf { it.isFinite() }

    // Schulden minus Kasse; null ohne Schulden (fehlende Angabe). Eine fehlende Kasse zaehlt als null.
    fun nettoschulden(schulden: Double?, kasse: Double?): Double? {
        val s = zahl(schulden)?.takeIf { it > 0 } ?: return null
        return s - (zahl(kasse)?.takeIf { it > 0 } ?: 0.0)
    }

    private fun zeile(k: Kennzahl, netto: Double, nenner: Double?, faktor: Double, ampel: (Double) -> Ampel): Zeile? {
        if (netto <= 0) return Zeile(k, Lage.KEINE_NETTOSCHULDEN, 0.0, Ampel.GRUEN)
        // 0 heisst bei den Quelldaten: keine Angabe
        val n = zahl(nenner)?.takeIf { it != 0.0 } ?: return null
        if (n < 0) return Zeile(k, Lage.NENNER_NEGATIV, 0.0, Ampel.ROT)
        val w = netto / n * faktor
        return Zeile(k, Lage.WERT, w, ampel(w))
    }

    // Die erste kritische Zeile; null, wenn keine rot ist.
    fun kritisch(zeilen: List<Zeile>): Zeile? = zeilen.firstOrNull { it.ampel == Ampel.ROT }

    // Ampel „Bilanz“ der Wertpapierseite: Steht im Blatt dahinter eine kritische Zeile, zeigt die Seite hoechstens
    // Gelb. Die Rechnung aus Piotroski und Altman (Bewertungsblick.bilanz) bleibt, wie sie ist.
    fun seitenAmpel(bilanz: Ampel, zeilen: List<Zeile>): Ampel =
        if (bilanz == Ampel.GRUEN && kritisch(zeilen) != null) Ampel.GELB else bilanz

    // Die drei Zeilen, soweit die Angaben reichen. Leer ohne Schulden.
    fun berechne(schulden: Double?, kasse: Double?, eigenkapital: Double?, freierCashflow: Double?, ebitda: Double?): List<Zeile> {
        val netto = nettoschulden(schulden, kasse) ?: return emptyList()
        return listOfNotNull(
            zeile(Kennzahl.GEARING, netto, eigenkapital, 100.0, ::gearingAmpel),
            zeile(Kennzahl.TILGUNG, netto, freierCashflow, 1.0, ::tilgungAmpel),
            zeile(Kennzahl.EBITDA, netto, ebitda, 1.0, ::ebitdaAmpel),
        )
    }
}
