package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.RezessionNyFed.FENSTER
import de.troetenrepublik.app.engine.RezessionNyFed.Reihe
import de.troetenrepublik.app.engine.RezessionNyFed.VORLAUF

// Rezessionswahrscheinlichkeit der New York Fed als Eingang des Gesamtausblicks [0.78]: Richtung fuer Aktien aus dem
// heutigen Wert und die gemessene Staerke. Gemessen wird gegen die Einbrueche des S&P 500 ab 20 % (Hochs aus dem
// Warn-Rueckblick) und gegen die Rezessionen des NBER: Ein Ereignis zaehlt als Treffer, wenn der Wert, wie man ihn sah, in
// den zwoelf Monaten vor seinem Monat mindestens einmal ueber der Schwelle lag. Die Grundquote ist der Anteil aller
// Monate, fuer die dasselbe gilt; der Vorsprung der Trefferquote davor ergibt die Stufe (AusblickStaerke).
object NyFedMessung {

    const val SCHLUESSEL = "nyfed"

    // grund: Grundquote als Anteil (0 bis 1). abJahr: Jahr des ersten gezaehlten Ereignisses.
    data class Messung(val schwelle: Double, val treffer: Int, val von: Int, val grund: Double, val abJahr: Int)

    // Lag der gesehene Wert in den FENSTER Monaten vor Monat k mindestens einmal ueber der Schwelle? null, wenn dem
    // Fenster Werte fehlen.
    fun zeigte(r: Reihe, k: Int, schwelle: Double): Boolean? {
        var ueber = false
        for (i in k - FENSTER until k) {
            val v = r.wert(i + VORLAUF) ?: return null
            if (v > schwelle) ueber = true
        }
        return ueber
    }

    // Anteil der Monate mit vollem Fenster, vor denen der Wert ueber der Schwelle lag; null ohne solchen Monat.
    fun grundquote(r: Reihe, schwelle: Double): Double? {
        var n = 0
        var ja = 0
        for (k in r.von..r.bis - VORLAUF + 1) {
            val z = zeigte(r, k, schwelle) ?: continue
            n++
            if (z) ja++
        }
        return if (n == 0) null else ja.toDouble() / n
    }

    private fun messen(r: Reihe, monate: List<Int>, schwelle: Double): Messung? {
        val grund = grundquote(r, schwelle) ?: return null
        var treffer = 0
        var von = 0
        var erstes = Int.MAX_VALUE
        for (k in monate) {
            val z = zeigte(r, k, schwelle) ?: continue
            von++
            erstes = minOf(erstes, k)
            if (z) treffer++
        }
        return if (von == 0) null else Messung(schwelle, treffer, von, grund, RezessionNyFed.jahr(erstes))
    }

    // Gegen Einbrueche eines Index: hochs sind die Tage der Hochs (Tage seit 1970).
    fun einbrueche(r: Reihe, hochs: List<Int>, schwelle: Double = RezessionNyFed.SCHWELLE): Messung? =
        messen(r, hochs.map { val d = java.time.LocalDate.ofEpochDay(it.toLong()); RezessionNyFed.monat(d.year, d.monthValue) }, schwelle)

    // Gegen die Rezessionen des NBER (Monat des Beginns)
    fun rezessionen(r: Reihe, schwelle: Double = RezessionNyFed.SCHWELLE): Messung? = messen(r, r.nber.map { it.first }, schwelle)

    // Hochs der Einbrueche ab 20 % eines Index im Warn-Rueckblick
    fun hochs(warn: WarnRueckblick.Datei?, index: String = "sp500"): List<Int> =
        warn?.indizes?.firstOrNull { it.id == index }?.ereignisse.orEmpty().filter { it.stufe == 20 }.map { it.hoch }

    // Richtung fuer Aktien: ueber der Schwelle der Messung spricht der Wert gegen Aktien. Darunter bleibt er neutral, weil
    // nur die Warnung gemessen ist.
    fun lage(wert: Double, schwelle: Double = RezessionNyFed.SCHWELLE): Tendenz.Lage =
        Tendenz.Lage(if (wert > schwelle) Tendenz.Richtung.BEARISH else Tendenz.Richtung.NEUTRAL, Tendenz.AKTIEN)
}
