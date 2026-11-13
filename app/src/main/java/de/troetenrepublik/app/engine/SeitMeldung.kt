package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle

// Bewegung des passenden Marktpreises seit der Veroeffentlichung einer Meldung (0.68).
// Ersetzt die Schaetzung "Im Preis?", wenn es aus der Geschichte keine gibt.
object SeitMeldung {
    // MIT: in Richtung der Meldung gelaufen, GEGEN: dagegen, KAUM: Bewegung unter der Schwelle, OFFEN: Meldung ohne klare Richtung
    enum class Lauf { MIT, GEGEN, KAUM, OFFEN }

    // markt: Kuerzel bei Yahoo; ab: Tag der Meldung (Tage seit 1970); bewegung: Anteil (0.012 = 1,2 %), mit vergleich gegen diesen Index;
    // tage: Handelstage seit der Meldung; bewegung und lauf null: seit der Meldung gab es noch keinen Handelstag
    data class Ergebnis(val markt: String, val ab: Long, val bewegung: Double?, val tage: Int, val lauf: Lauf?)

    const val SCHWELLE = 0.01

    private const val TAG_MS = 86_400_000L

    // Welcher Marktpreis passt zur Meldung; null, wenn keiner passt
    fun markt(schluessel: String, getreide: String? = null): String? = when (schluessel) {
        Fruehindikatoren.ROHOEL, Fruehindikatoren.CUSHING -> "CL=F"
        Fruehindikatoren.BENZIN -> "RB=F"
        Fruehindikatoren.DESTILLAT -> "HO=F"
        Fruehindikatoren.GAS_US -> "NG=F"
        Fruehindikatoren.GAS_EU -> Fruehindikatoren.TTF
        Fruehindikatoren.DUERRE -> Fruehindikatoren.getreide(getreide)
        Fruehindikatoren.FLUGGAESTE -> Fruehindikatoren.AIRLINES
        else -> null
    }

    // Index, gegen den verglichen wird, damit der Gesamtmarkt nicht als Signal gilt
    fun vergleich(schluessel: String): String? =
        if (schluessel == Fruehindikatoren.FLUGGAESTE) Fruehindikatoren.BREITER_MARKT else null

    // Tag der Veroeffentlichung aus dem Stichtag.
    // Feiertage koennen die Meldung um einen Tag verschieben, der Tag ist daher ungefaehr.
    fun meldetag(schluessel: String, stand: Long): Long = when (schluessel) {
        Fruehindikatoren.ROHOEL, Fruehindikatoren.BENZIN, Fruehindikatoren.DESTILLAT,
        Fruehindikatoren.CUSHING, Fruehindikatoren.SPR -> stand + 5
        Fruehindikatoren.GAS_US -> stand + 6
        Fruehindikatoren.ERSTANTRAEGE -> stand + 5
        Fruehindikatoren.DUERRE -> stand + 2
        else -> stand + 1
    }

    // Lief die Bewegung in Richtung der Meldung?
    fun einordnen(bewegung: Double, richtung: Int, schwelle: Double = SCHWELLE): Lauf = when {
        richtung == 0 -> Lauf.OFFEN
        kotlin.math.abs(bewegung) < schwelle -> Lauf.KAUM
        (bewegung > 0) == (richtung > 0) -> Lauf.MIT
        else -> Lauf.GEGEN
    }

    // Basis ist der letzte Schluss vor dem Meldetag, damit die Reaktion am Meldetag mitzaehlt
    fun berechnen(markt: String, kurse: List<Candle>, index: List<Candle>?, ab: Long, richtung: Int): Ergebnis? {
        if (kurse.isEmpty()) return null
        var i = kurse.size - 1
        while (i >= 0 && kurse[i].t.floorDiv(TAG_MS) >= ab) i--
        if (i < 0) return null
        // Noch kein Handelstag am oder nach dem Meldetag
        if (i == kurse.size - 1) return Ergebnis(markt, ab, null, 0, null)
        val (bewegung, tage) = Eingepreist.seit(kurse, index, kurse[i].t) ?: return null
        return Ergebnis(markt, ab, bewegung, tage, einordnen(bewegung, richtung))
    }
}
