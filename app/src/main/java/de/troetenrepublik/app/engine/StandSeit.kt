package de.troetenrepublik.app.engine

// „Hoechster Stand seit …“ und „tiefster Stand seit …“ fuer jede lange Reihe (Index, Zins, VIX, Rohstoffpreis, Kennzahl,
// Positionierung, Stimmung): Wann war die Reihe zuletzt mindestens so hoch (so tief) wie heute? Eine Aussage gibt es nur,
// wenn das mindestens ein Jahr her ist. Gab es in der ganzen Reihe keinen solchen Wert, ist der heutige ein Rekord; das
// zaehlt nur bei vollstaendigen Reihen (ganzes Archiv), nicht bei einem Ausschnitt der letzten Jahre.
object StandSeit {

    const val JAHR_MS = 365L * 86_400_000L

    enum class Art { HOCH, TIEF }

    // seit: Zeit (ms) des letzten frueheren Werts, der mindestens so hoch (tief) war; bei rekord der Beginn der Reihe
    data class Ergebnis(val art: Art, val seit: Long, val rekord: Boolean = false)

    // punkte: (Zeit in ms, Wert), aelteste zuerst; der letzte Punkt ist der heutige Wert.
    fun pruefen(punkte: List<Pair<Long, Double>>, vollstaendig: Boolean = false, minAbstand: Long = JAHR_MS): Ergebnis? {
        if (punkte.size < 2) return null
        val (t, v) = punkte.last()
        if (!v.isFinite()) return null
        if (t - punkte.first().first < minAbstand) return null
        // Die Richtung ergibt der letzte abweichende Vorwert (gleiche Werte direkt davor zaehlen zum heutigen Stand):
        // darueber kann es nur ein Hoch sein, darunter nur ein Tief
        var j = punkte.size - 2
        while (j >= 0 && punkte[j].second == v) j--
        if (j < 0) return null
        return if (punkte[j].second < v) suchen(punkte, j, t, Art.HOCH, vollstaendig, minAbstand) { it >= v }
        else suchen(punkte, j, t, Art.TIEF, vollstaendig, minAbstand) { it <= v }
    }

    private inline fun suchen(
        punkte: List<Pair<Long, Double>>, ab: Int, t: Long, art: Art, vollstaendig: Boolean, minAbstand: Long, erreicht: (Double) -> Boolean,
    ): Ergebnis? {
        for (i in ab downTo 0) {
            if (erreicht(punkte[i].second)) return if (t - punkte[i].first >= minAbstand) Ergebnis(art, punkte[i].first) else null
        }
        return if (vollstaendig) Ergebnis(art, punkte.first().first, rekord = true) else null
    }

    // Wie pruefen, fuer einen eigenen heutigen Wert hinter der Reihe (etwa der Kurs von jetzt zu einer Tagesreihe bis gestern)
    fun mitWert(reihe: List<Pair<Long, Double>>, jetzt: Long, wert: Double, vollstaendig: Boolean = false): Ergebnis? =
        pruefen(reihe.filter { it.first < jetzt } + (jetzt to wert), vollstaendig)

    // Rang des heutigen Werts in der ganzen Reihe: Anteil der Punkte (in Prozent, gerundet), die hoechstens so hoch waren
    fun rang(punkte: List<Pair<Long, Double>>): Int? {
        if (punkte.size < 2) return null
        val v = punkte.last().second
        return Math.round(punkte.count { it.second <= v } * 100.0 / punkte.size).toInt()
    }
}
