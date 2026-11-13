package de.troetenrepublik.app.i18n

// Texte zu den Zufallsdepots im Rueckblick des Aktienfinders, 0.78. Platzhalter {0}, {1} fuer Werte.
internal object FinderZufallTexte {

    fun text(deutsch: Boolean, key: String, w: List<String> = emptyList()): String {
        var t = (if (deutsch) DE else EN)[key] ?: return ""
        w.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
        return t
    }

    fun schluessel(deutsch: Boolean): Set<String> = (if (deutsch) DE else EN).keys

    private val DE = mapOf(
        "kern" to "Besser als {0} der Zufallsdepots",
        "titel" to "Zufallsdepots",
        "mitte" to "Mitte {0}",
        "finder" to "Finder {0}",
        "info" to "{0} Depots aus je {1} zufällig gezogenen Aktien desselben Index, gleich gewichtet und wie die Auswahl des Finders jeden Monat neu besetzt, über dieselben {2} Monate.\n\n• Die Säulen zeigen, wie sich die Rendite pro Jahr dieser Depots verteilt. 90 % liegen zwischen {3} und {4}.\n• Die Marke ist die Auswahl des Finders (20 Aktien mit den meisten Punkten) vor Kosten.\n• Liegt die Marke mitten in der Verteilung, erklärt schon der Index die Rendite.\n\nFester Startwert, das Ergebnis ist bei jedem Lauf gleich. Historisch und hypothetisch, keine Prognose.",
    )

    private val EN = mapOf(
        "kern" to "Better than {0} of random portfolios",
        "titel" to "Random portfolios",
        "mitte" to "Median {0}",
        "finder" to "Finder {0}",
        "info" to "{0} portfolios of {1} randomly drawn stocks each from the same index, equally weighted and redrawn every month like the finder's selection, over the same {2} months.\n\n• The columns show how the return per year of these portfolios is distributed. 90% lie between {3} and {4}.\n• The mark is the finder's selection (20 stocks with the most points) before costs.\n• If the mark sits in the middle of the distribution, the index alone explains the return.\n\nFixed seed, the result is the same on every run. Historical and hypothetical, not a forecast.",
    )
}
