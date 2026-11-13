package de.troetenrepublik.app.engine

// Zeile aus Bezeichnung und Wert (0.77): Der Wert steht rechts in der Zeile der Bezeichnung und bricht nicht um.
// Passt beides nicht in eine Zeile, bricht die Bezeichnung links um und der Wert bleibt rechts oben. Erst ein Wert,
// der der Bezeichnung nicht mehr einen Mindestplatz (ein Fuenftel der Zeile) laesst, steht unter ihr.
object ZeilenLayout {
    enum class Art { NEBEN, BEZEICHNUNG_BRICHT, WERT_DARUNTER }

    const val BEZEICHNUNG_MIN = 0.2

    fun art(bezeichnung: Int, wert: Int, breite: Int, abstand: Int): Art = when {
        bezeichnung + abstand + wert <= breite -> Art.NEBEN
        wert + abstand + minOf(bezeichnung, (breite * BEZEICHNUNG_MIN).toInt()) <= breite -> Art.BEZEICHNUNG_BRICHT
        else -> Art.WERT_DARUNTER
    }

    // Der Wert steht rechts neben der Bezeichnung
    fun wertRechts(bezeichnung: Int, wert: Int, breite: Int, abstand: Int): Boolean = art(bezeichnung, wert, breite, abstand) != Art.WERT_DARUNTER
}
