package de.troetenrepublik.app.i18n

// Texte der GDPNow-Zeile im Ausblick, 0.78. Ein Schluessel je Text, Platzhalter {0}, {1} fuer Werte.
internal object GdpNowTexte {

    fun text(deutsch: Boolean, key: String, w: List<String> = emptyList()): String {
        var t = (if (deutsch) DE else EN)[key] ?: return ""
        w.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
        return t
    }

    // Alle Schluessel einer Sprache (der Test vergleicht beide Sprachen)
    fun schluessel(deutsch: Boolean): Set<String> = (if (deutsch) DE else EN).keys

    private val DE = mapOf(
        "name" to "US-Wirtschaft {0} (GDPNow)",
        "quartal" to "Q{0} {1}",
        "satz.0" to "Schrumpft laut laufender Schätzung, aufs Jahr gerechnet",
        "satz.1" to "Wächst kaum laut laufender Schätzung, aufs Jahr gerechnet",
        "satz.2" to "Wächst laut laufender Schätzung, aufs Jahr gerechnet",
        "stand" to "Stand {0}",
        "vorquartal" to "{0} zuletzt {1}",
        "info.titel" to "GDPNow",
        "info" to "Nowcast: Die Federal Reserve Bank of Atlanta rechnet aus den schon veröffentlichten Monatsdaten (Konsum, Industrie, Bau, Außenhandel) hoch, " +
            "wie stark das US-Bruttoinlandsprodukt im laufenden Quartal wächst.\n\n" +
            "• Wert in Prozent zum Vorquartal, aufs Jahr gerechnet und um Preise bereinigt.\n" +
            "• Die Schätzung ändert sich mit jeder neuen Zahl. Die Linie zeigt ihren Verlauf für das laufende Quartal.\n" +
            "• Reine Modellrechnung, keine Prognose der Notenbank. Das amtliche Ergebnis folgt rund einen Monat nach Quartalsende.",
    )

    private val EN = mapOf(
        "name" to "US economy {0} (GDPNow)",
        "quartal" to "Q{0} {1}",
        "satz.0" to "Shrinking on the running estimate, annualized",
        "satz.1" to "Barely growing on the running estimate, annualized",
        "satz.2" to "Growing on the running estimate, annualized",
        "stand" to "As of {0}",
        "vorquartal" to "{0} last at {1}",
        "info.titel" to "GDPNow",
        "info" to "Nowcast: the Federal Reserve Bank of Atlanta projects from the monthly data already published (consumption, industry, construction, trade) " +
            "how fast US gross domestic product is growing in the current quarter.\n\n" +
            "• Value in percent on the previous quarter, annualized and adjusted for prices.\n" +
            "• The estimate changes with every new figure. The line shows its path for the current quarter.\n" +
            "• A pure model calculation, no forecast by the central bank. The official result follows about a month after the quarter ends.",
    )
}
