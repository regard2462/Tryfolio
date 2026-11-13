package de.troetenrepublik.app.i18n

// Texte zum Beneish-M-Score im Blatt „Bilanz“, 0.78. Ein Schluessel je Text, Platzhalter {0}, {1} fuer Werte.
internal object BeneishTexte {

    fun text(deutsch: Boolean, key: String, w: List<String> = emptyList()): String {
        var t = (if (deutsch) DE else EN)[key] ?: return ""
        w.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
        return t
    }

    // Alle Schluessel einer Sprache (der Test vergleicht beide Sprachen)
    fun schluessel(deutsch: Boolean): Set<String> = (if (deutsch) DE else EN).keys

    private val DE = mapOf(
        "titel" to "Beneish-M-Score",
        "auffaellig" to "auffällig",
        "unauffaellig" to "unauffällig",
        "satz" to "Geschäftsjahr bis {0}. Über {1} auffällig. Statistischer Hinweis, kein Befund.",
        "teile" to "Jahr gegen Vorjahr, 1,00 = unverändert",
        "DSRI" to "Forderungen je Umsatz",
        "GMI" to "Bruttomarge, Vorjahr durch Jahr",
        "AQI" to "Übriges Vermögen je Bilanzsumme",
        "SGI" to "Umsatz",
        "DEPI" to "Abschreibungsquote, Vorjahr durch Jahr",
        "SGAI" to "Vertrieb und Verwaltung je Umsatz",
        "TATA" to "Abgrenzungen je Bilanzsumme",
        "LVGI" to "Schulden je Bilanzsumme",
        "info" to "Der Beneish-M-Score vergleicht acht Kennzahlen des letzten Geschäftsjahrs mit dem Vorjahr: Forderungen, Bruttomarge, übriges Vermögen neben Umlaufvermögen und Sachanlagen, Umsatzwachstum, Abschreibungen, Vertriebs- und Verwaltungskosten, Schulden und Abgrenzungen (Gewinn, hinter dem noch kein Geld steht).\n• Über {0} ähnelt das Zahlenbild dem von Firmen, die in der Studie von Beneish (1999) ihre Gewinne geschönt hatten\n• Die Schwelle erkannte dort die Hälfte bis drei Viertel dieser Firmen und stufte 7 bis 14 % der übrigen falsch als auffällig ein\n\nGrenzen: Ein Wert über der Schwelle ist ein statistischer Hinweis, kein Befund. Starkes Wachstum, Übernahmen oder ein Umbau des Geschäfts heben ihn auch ohne geschönte Zahlen. Die Abschreibungen enthalten hier auch die auf immaterielle Werte. Für Banken, Versicherer und andere Finanzwerte passt der Wert nicht.",
    )

    private val EN = mapOf(
        "titel" to "Beneish M-Score",
        "auffaellig" to "flagged",
        "unauffaellig" to "not flagged",
        "satz" to "Fiscal year to {0}. Flagged above {1}. A statistical hint, not a finding.",
        "teile" to "Year against prior year, 1.00 = unchanged",
        "DSRI" to "Receivables to revenue",
        "GMI" to "Gross margin, prior year over year",
        "AQI" to "Other assets to total assets",
        "SGI" to "Revenue",
        "DEPI" to "Depreciation rate, prior year over year",
        "SGAI" to "Selling and administration to revenue",
        "TATA" to "Accruals to total assets",
        "LVGI" to "Debt to total assets",
        "info" to "The Beneish M-Score compares eight ratios of the latest fiscal year with the prior year: receivables, gross margin, other assets besides current assets and property, revenue growth, depreciation, selling and administrative costs, debt and accruals (profit not yet backed by cash).\n• Above {0} the figures resemble those of companies that had inflated their earnings in the study by Beneish (1999)\n• There the threshold caught half to three quarters of those companies and wrongly flagged 7 to 14% of the others\n\nLimits: a value above the threshold is a statistical hint, not a finding. Strong growth, acquisitions or a change of business raise it without any inflated figures. Depreciation here includes amortization of intangibles. The score does not fit banks, insurers or other financials.",
    )
}
