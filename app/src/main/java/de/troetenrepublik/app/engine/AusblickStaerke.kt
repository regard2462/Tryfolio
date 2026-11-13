package de.troetenrepublik.app.engine

import kotlin.math.roundToInt

// [Ausblick 0.77] Staerke je Signal im Ausblick: wie viel Auswirkung ein Signal in der Vergangenheit hatte. Gemessen
// wird aus den mitgelieferten Rueckblicken der App (TendenzRueckblick fuer Lager und Duerre, WarnRueckblick fuer
// Erstantraege und Blasenzeichen), jeweils als Vorsprung der Trefferquote vor der Grundquote. Signale ohne Rueckblick
// (Polymarket, Zinsfutures, Ersatzquellen) bekommen eine gesetzte Stufe aus dem Quellengewicht von Ausblickgesamt.
object AusblickStaerke {
    // Vier Stufen, 0 heisst kein Vorsprung gemessen.
    const val STUFEN = 4

    // Zeitraeume des Tendenz-Rueckblicks zaehlen erst mit so vielen nicht ueberlappenden Faellen.
    const val MIN_UNABHAENGIG = TendenzRueckblick.MIN_UNABHAENGIG

    // Einbrueche ab 20 % im Warn-Rueckblick: darunter keine Messung, bis SICHER_EINBRUECHE hoechstens Stufe 2.
    const val MIN_EINBRUECHE = 5
    const val SICHER_EINBRUECHE = 10
    const val DECKEL = 2

    // Staerkstes Quellengewicht in Ausblickgesamt, entspricht der obersten Stufe bei gesetzten Werten.
    const val GEWICHT_MAX = 1.2

    // gemessen: aus einem Rueckblick der App, sonst gesetzt.
    data class Staerke(val stufe: Int, val gemessen: Boolean)

    // Vorsprung der Trefferquote (Anteil, 0,05 = 5 Prozentpunkte) auf die vier Stufen.
    fun stufe(vorsprung: Double): Int = when {
        !(vorsprung > 0) -> 0
        vorsprung < 0.05 -> 1
        vorsprung < 0.15 -> 2
        vorsprung < 0.30 -> 3
        else -> 4
    }

    // Tendenz-Rueckblick: mittlerer Vorsprung (Trefferquote minus Grundquote) ueber alle Zeitraeume der Gruppe „alle“
    // mit genug unabhaengigen Faellen. Ohne Beleg (kein Zeitraum belegt) hoechstens Stufe 2. null ohne geprueftes Signal.
    fun ausTendenz(signale: List<TendenzRueckblick.Signal?>): Staerke? {
        val z = signale.filterNotNull().filter { it.status == TendenzRueckblick.GEPRUEFT }
            .flatMap { it.gruppen["alle"].orEmpty() }.filter { it.unabhaengig >= MIN_UNABHAENGIG }
        if (z.isEmpty()) return null
        val s = stufe(z.map { it.treffer - it.grund }.average())
        return Staerke(if (z.any { it.belegt }) s else minOf(s, DECKEL), true)
    }

    // Warn-Rueckblick: Anteil der Einbrueche ab 20 %, vor denen das Zeichen im Jahr davor warnte, minus Anteil der
    // Tage mit Warnung. Mit weniger als zehn Einbruechen hoechstens Stufe 2, mit weniger als fuenf keine Messung.
    fun ausWarnung(b: WarnRueckblick.Bilanz?): Staerke? {
        if (b == null || b.auswertbar < MIN_EINBRUECHE) return null
        val s = stufe(b.treffer.toDouble() / b.auswertbar - b.zeitAnteil)
        return Staerke(if (b.auswertbar < SICHER_EINBRUECHE) minOf(s, DECKEL) else s, true)
    }

    // Eigene Messung gegen Einbrueche: Trefferquote minus Grundquote (Anteil), mit denselben Grenzen fuer die Fallzahl wie
    // beim Warn-Rueckblick.
    fun ausMessung(treffer: Int, von: Int, grund: Double): Staerke? {
        if (von < MIN_EINBRUECHE) return null
        val s = stufe(treffer.toDouble() / von - grund)
        return Staerke(if (von < SICHER_EINBRUECHE) minOf(s, DECKEL) else s, true)
    }

    // Stufe aus dem gesetzten Quellengewicht (mindestens 1).
    fun gesetzt(gewicht: Double): Staerke = Staerke((gewicht / GEWICHT_MAX * STUFEN).roundToInt().coerceIn(1, STUFEN), false)

    // Eintrag des Ausblicks (Schluessel wie in Ausblickgesamt). Lager, Fluggaeste und Duerre aus dem Tendenz-Rueckblick (Duerre ueber
    // alle Getreide), Erstantraege aus dem Warn-Rueckblick des S&P 500, die Rezessionswahrscheinlichkeit der New York Fed
    // gegen die Einbrueche des S&P 500 (NyFedMessung), der Rest gesetzt.
    fun fuerSignal(
        schluessel: String, tendenz: TendenzRueckblick.Datei?, warn: WarnRueckblick.Datei?, nyFed: RezessionNyFed.Reihe? = null,
    ): Staerke {
        val gemessen = when (schluessel) {
            Fruehindikatoren.DUERRE -> ausTendenz(tendenz?.signale.orEmpty().filterKeys { it.startsWith(Fruehindikatoren.DUERRE + ":") }.values.toList())
            Fruehindikatoren.ROHOEL, Fruehindikatoren.CUSHING, Fruehindikatoren.BENZIN, Fruehindikatoren.DESTILLAT, Fruehindikatoren.GAS_US,
            Fruehindikatoren.GAS_EU, Fruehindikatoren.FLUGGAESTE ->
                ausTendenz(listOf(tendenz?.signale?.get(schluessel)))
            Fruehindikatoren.ERSTANTRAEGE -> ausWarnung(WarnRueckblick.bilanz(warn, INDEX, WarnRueckblick.ERSTANTRAEGE))
            NyFedMessung.SCHLUESSEL ->
                nyFed?.let { NyFedMessung.einbrueche(it, NyFedMessung.hochs(warn, INDEX)) }?.let { ausMessung(it.treffer, it.von, it.grund) }
            else -> null
        }
        return gemessen ?: gesetzt(Ausblickgesamt.quelle(schluessel).basis)
    }

    // Blasenzeichen (Schluessel aus Blasen): Warn-Rueckblick des S&P 500, die Trendzeile heisst dort „hitze“.
    fun fuerBlase(schluessel: String, warn: WarnRueckblick.Datei?): Staerke {
        val id = when (schluessel) {
            Blasen.CAPE, Blasen.KGV, Blasen.BUFFETT, Blasen.PRAEMIE, Blasen.MARGIN -> schluessel
            Blasen.TREND -> WarnRueckblick.HITZE
            else -> null
        }
        return id?.let { ausWarnung(WarnRueckblick.bilanz(warn, INDEX, it)) } ?: gesetzt(Ausblickgesamt.quelle(schluessel).basis)
    }

    private const val INDEX = "sp500"
}
