package de.troetenrepublik.app.i18n

// Texte zum Abstand von Indexfonds zu ihrem Index (Wertpapierseite und Werkzeug „Markt schlagen“), 0.78.
// Ein Schluessel je Text, Platzhalter {0}, {1} fuer Werte.
internal object AbstandTexte {

    fun text(deutsch: Boolean, key: String, w: List<String> = emptyList()): String {
        var t = (if (deutsch) DE else EN)[key] ?: return ""
        w.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
        return t
    }

    // Alle Schluessel einer Sprache (der Test vergleicht beide Sprachen)
    fun schluessel(deutsch: Boolean): Set<String> = (if (deutsch) DE else EN).keys

    private val DE = mapOf(
        "titel" to "Abstand zum Index",
        "kern" to "{0} Prozentpunkte",
        "kern.unter" to "Rendite des Fonds minus Index je Jahr, nach Kosten, Mittel {0} bis {1}",
        "kern.unter.ein" to "Rendite des Fonds minus Index, nach Kosten, Geschäftsjahr {0}",
        "balken" to "Je Geschäftsjahr, nach Kosten",
        "vor" to "Vor Kosten",
        "kosten" to "Kosten des Fonds laut SEC-Meldung",
        "ter" to "Laufende Kosten heute",
        "median" to "Median der Indexfonds {0}",
        "punkte" to "{0} Punkte",
        "info" to "Rendite des Fonds minus Rendite seines Index im Geschäftsjahr, in Prozentpunkten.\n\n" +
            "• Ein Fonds, der den Index genau trifft, liegt um seine Kosten zurück.\n" +
            "• Kleinerer Abstand als die Kosten: Erträge aus Wertpapierleihe oder niedrigere Quellensteuer, als der Index ansetzt.\n" +
            "• Größerer Abstand: Handelskosten, Barbestand oder ein nur teilweise nachgebildeter Index.\n\n" +
            "Letztes Geschäftsjahr bis {0}. Gemeldet als „{1}“.\n\n" +
            "Angaben des Fonds an die US-Börsenaufsicht SEC (Form N-CEN), ungeprüft. Das Formular nennt den Index nicht. Stand {2}.",
        "tool.titel" to "Indexfonds nach Kosten hinter dem Index",
        "tool.satz" to "der US-Indexfonds lagen {0} nach Kosten hinter ihrem Index ({1} Fonds). Median nach Kosten {2}, vor Kosten {3} Prozentpunkte.",
        "tool.bild" to "Verteilung des Abstands zum Index nach Kosten, Anteil der Fonds je Klasse in Prozentpunkten",
        "tool.achse" to "Abstand in Prozentpunkten",
        "quelle" to "SEC, Form N-CEN Data Sets",
    )

    private val EN = mapOf(
        "titel" to "Gap to the index",
        "kern" to "{0} percentage points",
        "kern.unter" to "Fund return minus index per year, after costs, average {0} to {1}",
        "kern.unter.ein" to "Fund return minus index, after costs, fiscal year {0}",
        "balken" to "By fiscal year, after costs",
        "vor" to "Before costs",
        "kosten" to "Fund costs as reported to the SEC",
        "ter" to "Ongoing costs today",
        "median" to "Median of index funds {0}",
        "punkte" to "{0} points",
        "info" to "Fund return minus the return of its index in the fiscal year, in percentage points.\n\n" +
            "• A fund that matches the index exactly trails it by its costs.\n" +
            "• Smaller gap than the costs: income from securities lending or lower withholding tax than the index assumes.\n" +
            "• Larger gap: trading costs, cash holdings or an index that is only partly replicated.\n\n" +
            "Latest fiscal year to {0}. Reported as \"{1}\".\n\n" +
            "Reported by the fund to the US Securities and Exchange Commission (Form N-CEN), unaudited. The form does not name the index. As of {2}.",
        "tool.titel" to "Index funds trail the index after costs",
        "tool.satz" to "of US index funds trailed their index after costs in {0} ({1} funds). Median after costs {2}, before costs {3} percentage points.",
        "tool.bild" to "Distribution of the gap to the index after costs, share of funds per class in percentage points",
        "tool.achse" to "Gap in percentage points",
        "quelle" to "SEC, Form N-CEN Data Sets",
    )
}
