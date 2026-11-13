package de.troetenrepublik.app.i18n

// Texte des Werkzeugs „Neue Analystenstufen“, 0.78. Ein Schluessel je Text, Platzhalter {0}, {1} ... fuer Werte.
internal object StufenTexte {

    fun text(deutsch: Boolean, key: String, w: List<String>): String {
        var t = (if (deutsch) DE else EN)[key] ?: return ""
        w.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
        return t
    }

    // Alle Schluessel einer Sprache (der Test vergleicht beide Sprachen)
    fun schluessel(deutsch: Boolean): Set<String> = (if (deutsch) DE else EN).keys

    private val DE = mapOf(
        "tage" to "{0} Tage",
        "jahr" to "1 Jahr",
        "alle" to "Alle",
        "depot" to "Depot",
        "watchlist" to "Watchlist",
        "suche" to "Aktie suchen",
        "leer" to "Keine Hoch- oder Abstufungen im Zeitraum",
        "ohne" to "Keine Stufen im Zeitraum",
        "laedt" to "Lädt {0} von {1}",
        "abbruch" to "Quelle antwortet nicht, {0} von {1} geladen",
        "ziel" to "Kursziel",
        "zielMittel" to "Kursziel im Mittel {0}",
    )

    private val EN = mapOf(
        "tage" to "{0} days",
        "jahr" to "1 year",
        "alle" to "All",
        "depot" to "Portfolio",
        "watchlist" to "Watchlist",
        "suche" to "Search stock",
        "leer" to "No upgrades or downgrades in this period",
        "ohne" to "No ratings in this period",
        "laedt" to "Loading {0} of {1}",
        "abbruch" to "Source not responding, {0} of {1} loaded",
        "ziel" to "Price target",
        "zielMittel" to "Average target {0}",
    )
}
