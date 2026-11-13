package de.troetenrepublik.app.engine

// Darstellung der Firmenlogos ohne Oberflaeche: farbig wie geliefert oder schwarz-weiss. Die Oberflaeche
// (ui/Logo.kt) setzt die Art in Farbfilter um.
object LogoDarstellung {

    enum class Art {
        // Logo unveraendert
        ORIGINAL,
        // Logo mit eigener Flaeche (oder noch nicht untersucht) als Graustufen
        GRAUSTUFEN,
        // Freistehendes Zeichen einfarbig in der Textfarbe des Erscheinungsbilds
        EINFARBIG,
    }

    // eigeneFlaeche und frei sind die Ergebnisse der Logo-Analyse; sind beide falsch, ist das Logo noch nicht untersucht.
    fun art(farbig: Boolean, eigeneFlaeche: Boolean, frei: Boolean): Art = when {
        farbig -> Art.ORIGINAL
        frei && !eigeneFlaeche -> Art.EINFARBIG
        else -> Art.GRAUSTUFEN
    }

    // Helle Flaeche hinter einem dunklen freistehenden Zeichen im dunklen Erscheinungsbild gibt es nur farbig;
    // einfarbig steht das Zeichen in der Textfarbe und braucht keine Flaeche.
    fun flaecheHinterDunklemZeichen(art: Art, dunkel: Boolean): Boolean = art == Art.ORIGINAL && dunkel

    // Eckenradius als Anteil der Kantenlaenge: 0,5 ist ein Kreis, 0,22 das abgerundete Quadrat.
    fun eckenAnteil(rund: Boolean): Float = if (rund) 0.5f else 0.22f

    // Groesse des Zeichens als Anteil der Kantenlaenge, wenn es frei steht oder auf einer dezenten Flaeche sitzt.
    // Im Kreis bleibt mehr Rand, damit nichts am Rand abgeschnitten wird.
    fun zeichenAnteil(rund: Boolean, aufFlaeche: Boolean): Float = when {
        aufFlaeche -> if (rund) 0.6f else 0.68f
        else -> if (rund) 0.72f else 0.92f
    }

    // Zeichen auf der Flaeche bei Hintergrund behalten (Darstellung wie in 0.78), rund wie eckig.
    const val ZEICHEN_AUF_FLAECHE = 0.68f

    // Logos mit eigener Flaeche fuellen die Form ganz aus.
    const val FLAECHE_VERGROESSERUNG = 1.14f

    // Trennlinien bleiben bei Aus unsichtbar, der Platz bleibt gleich.
    fun linieSichtbar(an: Boolean): Boolean = an
}
