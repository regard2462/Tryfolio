package de.troetenrepublik.app.i18n

// Texte der Halbjahresregel im Abschnitt „Muster der Vergangenheit“, 0.78. Ein Schluessel je Text, Platzhalter {0}, {1}
// fuer Werte.
internal object HalbjahrTexte {

    fun text(deutsch: Boolean, key: String, w: List<String> = emptyList()): String {
        var t = (if (deutsch) DE else EN)[key] ?: return ""
        w.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
        return t
    }

    // Alle Schluessel einer Sprache (der Test vergleicht beide Sprachen)
    fun schluessel(deutsch: Boolean): Set<String> = (if (deutsch) DE else EN).keys

    private val DE = mapOf(
        "name" to "{0} von November bis April",
        "satz" to "Mai bis Oktober {0} · Winter vorn in {1} von {2} Jahren seit {3}",
        "winter" to "Nov bis Apr",
        "sommer" to "Mai bis Okt",
        "proJahr" to "Rendite pro Jahr",
        "halten" to "Durchgehend investiert",
        "regel" to "Nur November bis April",
        "fazit.halten" to "Durchgehend investiert brachte mehr.",
        "fazit.regel" to "Nur im Winterhalbjahr investiert brachte mehr.",
        "grundlage" to "{0} · im Sommer ohne Zinsen · ohne Kosten und Steuern · Regel nach Bouman und Jacobsen (2002)",
    )

    private val EN = mapOf(
        "name" to "{0} from November to April",
        "satz" to "May to October {0} · winter ahead in {1} of {2} years since {3}",
        "winter" to "Nov to Apr",
        "sommer" to "May to Oct",
        "proJahr" to "Return per year",
        "halten" to "Invested throughout",
        "regel" to "November to April only",
        "fazit.halten" to "Staying invested throughout returned more.",
        "fazit.regel" to "Being invested only in the winter half returned more.",
        "grundlage" to "{0} · no interest in summer · no costs or taxes · rule after Bouman and Jacobsen (2002)",
    )
}
