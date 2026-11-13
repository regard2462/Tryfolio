package de.troetenrepublik.app.engine

// Darstellung der Mitarbeiterzahlen als Balken (0.77): die Reihe aus Wikidata um den aktuellen Wert der Zeile
// "Mitarbeitende" ergaenzen, damit Grafik und Zeile dieselbe Zahl zeigen, dazu Veraenderung und Jahresachse.
object MitarbeiterAnsicht {
    // Reihe mit dem aktuellen Wert (Yahoo, aus dem letzten Geschaeftsbericht) und dessen Jahr. Ist die Reihe aelter, kommt der
    // Wert als letzter Balken dazu; steht fuer dasselbe Jahr schon ein anderer Wert, gilt der aktuelle, damit die Zeile
    // darueber nicht von der Grafik abweicht. Ist die Reihe neuer oder fehlt das Jahr, bleibt sie unveraendert.
    fun mitAktuell(reihe: List<MitarbeiterReihe.Punkt>, aktuell: Long, jahr: Int?): List<MitarbeiterReihe.Punkt> {
        if (aktuell <= 0 || jahr == null || reihe.isEmpty()) return reihe
        val letzte = reihe.last()
        return when {
            jahr > letzte.jahr -> reihe + MitarbeiterReihe.Punkt(jahr, aktuell)
            jahr == letzte.jahr && letzte.anzahl != aktuell -> reihe.dropLast(1) + MitarbeiterReihe.Punkt(jahr, aktuell)
            else -> reihe
        }
    }

    // Veraenderung vom ersten zum letzten Wert in Prozent; null ohne zwei Werte oder mit Null am Anfang
    fun veraenderung(reihe: List<MitarbeiterReihe.Punkt>): Double? {
        if (reihe.size < 2) return null
        val erste = reihe.first().anzahl
        if (erste <= 0) return null
        return (reihe.last().anzahl.toDouble() / erste - 1.0) * 100.0
    }

    // So viele Jahre zeigt die Grafik hoechstens (die juengsten)
    const val MAX_JAHRE = 15

    // Die Reihe auf die letzten MAX_JAHRE Jahre kuerzen, gerechnet vom juengsten Jahr aus
    fun letzte(reihe: List<MitarbeiterReihe.Punkt>): List<MitarbeiterReihe.Punkt> {
        if (reihe.isEmpty()) return reihe
        val grenze = reihe.maxOf { it.jahr } - MAX_JAHRE
        return reihe.filter { it.jahr > grenze }
    }

    // Plaetze auf der Jahresachse: ein Platz je Jahr vom ersten bis zum letzten Jahr
    fun plaetze(reihe: List<MitarbeiterReihe.Punkt>): Int =
        if (reihe.isEmpty()) 0 else reihe.maxOf { it.jahr } - reihe.minOf { it.jahr } + 1

    // Platz (ab 0) eines Jahres auf der Achse
    fun platz(reihe: List<MitarbeiterReihe.Punkt>, jahr: Int): Int = jahr - reihe.minOf { it.jahr }

    // Plaetze, unter denen eine Jahreszahl steht: erster und letzter immer, dazwischen im gleichmaessigen Abstand so
    // viele, dass sich Zahlen und Nachbarn nicht beruehren (Abstand der Mitten mindestens markenBreite plus Luft).
    // Vom ersten Platz aus gezaehlt; ein Platz zu nahe am letzten faellt weg.
    fun marken(plaetze: Int, spalte: Float, markenBreite: Float, luft: Float = 0.3f): List<Int> {
        if (plaetze <= 0) return emptyList()
        if (plaetze == 1) return listOf(0)
        val noetig = markenBreite * (1f + luft)
        val schritt = if (spalte <= 0f) plaetze else kotlin.math.ceil(noetig / spalte).toInt().coerceAtLeast(1)
        val letzter = plaetze - 1
        val aus = ArrayList<Int>()
        var i = 0
        while (i < letzter) {
            if (letzter - i >= schritt) aus.add(i)
            i += schritt
        }
        // Erster Platz bleibt auch dann, wenn er dem letzten zu nahe kaeme (nur bei sehr breiten Zahlen)
        if (aus.isEmpty() || aus.first() != 0) aus.add(0, 0)
        aus.add(letzter)
        return aus
    }
}
