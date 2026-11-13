package de.troetenrepublik.app.engine

// Masse der Tippbestaetigung, getrennt vom Zeichnen. Alle Angaben in Pixeln.
object Tippmass {
    // Flaeche eines Textknopfs als links, oben, rechts, unten. innenLinks und innenRechts sind die Abstaende, die der
    // Knopf selbst schon zwischen Rand und Schrift hat; die Flaeche steht seitlich um rand ueber die Schrift hinaus,
    // auch ueber die Grenzen des Knopfs. Einzeilige Knoepfe (bis einzeiligBis hoch) bekommen eine Flaeche zwischen
    // minHoehe und maxHoehe, mittig; hoehere (mehrzeilige) werden in voller Hoehe belegt.
    fun textFlaeche(
        breite: Float, hoehe: Float, innenLinks: Float, innenRechts: Float,
        rand: Float, minHoehe: Float, maxHoehe: Float, einzeiligBis: Float,
    ): FloatArray {
        var links = innenLinks - rand
        var rechts = breite - (innenRechts - rand)
        if (rechts - links < 2 * rand) { links = 0f; rechts = breite }
        if (hoehe > einzeiligBis) return floatArrayOf(links, 0f, rechts, hoehe)
        val h = hoehe.coerceIn(minHoehe, maxHoehe)
        return floatArrayOf(links, (hoehe - h) / 2f, rechts, (hoehe + h) / 2f)
    }

    // Kreis um die Mitte eines Symbolknopfs, nie groesser als der Knopf.
    fun kreisRadius(breite: Float, hoehe: Float, hoechstens: Float): Float = minOf(breite / 2f, hoehe / 2f, hoechstens)

    // Druecken geht vor Fokus und Schweben.
    fun deckkraft(gedrueckt: Boolean, fokus: Boolean, schwebt: Boolean, beimDruecken: Float, beiFokus: Float, beimSchweben: Float): Float = when {
        gedrueckt -> beimDruecken
        fokus && schwebt -> maxOf(beiFokus, beimSchweben)
        fokus -> beiFokus
        schwebt -> beimSchweben
        else -> 0f
    }
}
