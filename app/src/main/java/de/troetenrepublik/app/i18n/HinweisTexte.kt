package de.troetenrepublik.app.i18n

// Kernzeilen der Hinweise unter Werkzeugen und Ansichten, 0.78. Die Methode steht hinter dem Info-Symbol.
internal object HinweisTexte {

    fun text(deutsch: Boolean, key: String): String = (if (deutsch) DE else EN)[key] ?: ""

    // Setzt die zentralen Kurztexte in eigene Texte ein: {Hk} und {hk} (Anfang gross oder klein) fuer
    // „historisch und hypothetisch“, {hist}, {histPrognose}, {modell} fuer die ganzen Hinweissaetze.
    fun einsetzen(deutsch: Boolean, t: String): String {
        if (t.indexOf('{') < 0) return t
        val k = text(deutsch, "kurz")
        return t.replace("{Hk}", k).replace("{hk}", k.replaceFirstChar { it.lowercaseChar() })
            .replace("{hist}", text(deutsch, "hist")).replace("{histPrognose}", text(deutsch, "histPrognose")).replace("{modell}", text(deutsch, "modell"))
    }

    // Alle Schluessel einer Sprache (der Test vergleicht beide Sprachen)
    fun schluessel(deutsch: Boolean): Set<String> = (if (deutsch) DE else EN).keys

    private val DE = mapOf(
        "titel" to "Methode",
        "kurz" to "Historisch und hypothetisch",
        "hist" to "Historisch und hypothetisch, nicht tatsächlich erzielt.",
        "modell" to "Modellrechnung, historisch und hypothetisch.",
        "annahmen" to "Rechnung auf Annahmen, keine Vorhersage.",
        "histPrognose" to "Historisch und hypothetisch, keine Prognose.",
        "zufall" to "Zufallspfade aus den echten Tagen des Zeitraums, keine Prognose.",
        "gleichlauf" to "Je niedriger der Gleichlauf, desto mehr bringt die Streuung.",
    )

    private val EN = mapOf(
        "titel" to "Method",
        "kurz" to "Historical and hypothetical",
        "hist" to "Historical and hypothetical, not actually achieved.",
        "modell" to "Model calculation, historical and hypothetical.",
        "annahmen" to "Based on assumptions, not a forecast.",
        "histPrognose" to "Historical and hypothetical, not a forecast.",
        "zufall" to "Random paths from the real days of the period, not a forecast.",
        "gleichlauf" to "The lower the correlation, the more diversification helps.",
    )
}
