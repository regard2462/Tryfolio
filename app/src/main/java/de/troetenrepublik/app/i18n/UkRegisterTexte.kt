package de.troetenrepublik.app.i18n

// Texte zum Register der finanziellen Interessen des britischen Unterhauses, 0.78. Ein Schluessel je Text,
// Platzhalter {0}, {1} fuer Werte.
internal object UkRegisterTexte {

    fun text(deutsch: Boolean, key: String, w: List<String> = emptyList()): String {
        var t = (if (deutsch) DE else EN)[key] ?: return ""
        w.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
        return t
    }

    // Alle Schluessel einer Sprache (der Test vergleicht beide Sprachen)
    fun schluessel(deutsch: Boolean): Set<String> = (if (deutsch) DE else EN).keys

    private val DE = mapOf(
        "titel" to "Britisches Unterhaus: gemeldete Beteiligungen",
        "kern.anzahl" to "{0} Beteiligungen",
        "kern.personen" to "{0} Abgeordnete",
        "kern.zuletzt" to "Zuletzt gemeldet {0}",
        "suche" to "Person oder Firma suchen",
        "schwelle.prozent" to "über {0} des Kapitals",
        "schwelle.wert" to "Wert über {0}",
        "seit" to "seit {0}",
        "beendet" to "beendet {0}",
        "leer" to "Keine Treffer",
        "aktie.titel" to "Britische Abgeordnete mit Beteiligung",
        "aktie.kern.1" to "1 Abgeordneter",
        "aktie.kern.n" to "{0} Abgeordnete",
        "info" to "Mitglieder des britischen Unterhauses melden Beteiligungen an Unternehmen im Register der finanziellen Interessen.\n" +
            "• Das Register nennt Bestände. Einzelne Käufe und Verkäufe, Stückzahl und genauer Wert stehen nicht darin.\n" +
            "• Gemeldet wird ab einer Schwelle: Anteil am Kapital oder Wert. Sie steht bei jedem Eintrag. Kleinere Bestände fehlen.\n" +
            "• Bestände, die gemeinsam mit Ehepartner oder Kindern oder für sie gehalten werden, zählen mit.\n" +
            "• Viele Einträge betreffen Firmen, die an keiner Börse notiert sind.",
        "info.aktie" to "Mitglieder des britischen Unterhauses melden Beteiligungen an Unternehmen im Register der finanziellen Interessen.\n" +
            "• Das Register nennt Bestände. Einzelne Käufe und Verkäufe, Stückzahl und genauer Wert stehen nicht darin.\n" +
            "• Gemeldet wird ab einer Schwelle: Anteil am Kapital oder Wert. Kleinere Bestände fehlen.\n" +
            "• Zugeordnet wird über den Firmennamen samt Rechtsform. Einträge mit anderer Schreibweise fehlen hier.",
    )

    private val EN = mapOf(
        "titel" to "UK House of Commons: registered shareholdings",
        "kern.anzahl" to "{0} shareholdings",
        "kern.personen" to "{0} MPs",
        "kern.zuletzt" to "Last registered {0}",
        "suche" to "Search person or company",
        "schwelle.prozent" to "over {0} of share capital",
        "schwelle.wert" to "valued over {0}",
        "seit" to "since {0}",
        "beendet" to "ended {0}",
        "leer" to "No matches",
        "aktie.titel" to "UK MPs with a registered shareholding",
        "aktie.kern.1" to "1 MP",
        "aktie.kern.n" to "{0} MPs",
        "info" to "Members of the UK House of Commons register shareholdings in the Register of Members' Financial Interests.\n" +
            "• The register lists holdings. Individual purchases and sales, number of shares and exact value are not included.\n" +
            "• Registration starts at a threshold: share of capital or value. It is shown with each entry. Smaller holdings are missing.\n" +
            "• Holdings held jointly with or on behalf of a spouse, partner or children count as well.\n" +
            "• Many entries concern companies that are not listed on any exchange.",
        "info.aktie" to "Members of the UK House of Commons register shareholdings in the Register of Members' Financial Interests.\n" +
            "• The register lists holdings. Individual purchases and sales, number of shares and exact value are not included.\n" +
            "• Registration starts at a threshold: share of capital or value. Smaller holdings are missing.\n" +
            "• Matching uses the company name including its legal form. Entries spelled differently are missing here.",
    )
}
