package de.troetenrepublik.app.i18n

// Texte zur Leitzins-Historie im Laenderblatt, 0.78. Ein Schluessel je Text, Platzhalter {0}, {1} fuer Werte.
internal object LeitzinsTexte {

    fun text(deutsch: Boolean, key: String, w: List<String> = emptyList()): String {
        var t = (if (deutsch) DE else EN)[key] ?: return ""
        w.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
        return t
    }

    // Alle Schluessel einer Sprache (der Test vergleicht beide Sprachen)
    fun schluessel(deutsch: Boolean): Set<String> = (if (deutsch) DE else EN).keys

    private val DE = mapOf(
        "titel" to "Leitzins",
        "seit" to "seit {0}",
        "verlauf" to "Verlauf seit {0}",
        "spanne" to "Spanne seit {0}",
        "spanne.wert" to "{0} bis {1}",
        "schritte" to "Letzte Zinsschritte",
        "schritt" to "{0} auf {1}",
        "info" to "Zinssatz, zu dem die Zentralbank den Banken Geld leiht oder Einlagen verzinst. Er steuert Kreditzinsen, Sparzinsen und den Wechselkurs.\n\n" +
            "• Erhöhung: Kredite werden teurer, das bremst Nachfrage und Inflation.\n" +
            "• Senkung: Kredite werden billiger, das stützt Konjunktur und Kurse.\n\n" +
            "In Euroländern gilt seit dem Beitritt der Satz der Europäischen Zentralbank, davor der Satz der eigenen Zentralbank.\n\n" +
            "Quelle: Bank für Internationalen Zahlungsausgleich (BIS), Central bank policy rates. Monatswerte, Zinsschritte aus den Tageswerten. " +
            "Deutsche Bezeichnungen von der App, keine amtliche Übersetzung der BIS. Die BIS übernimmt dafür keine Gewähr. Stand {0}.",
        "quelle" to "BIZ (BIS), Leitzinsen der Zentralbanken",
    )

    private val EN = mapOf(
        "titel" to "Policy rate",
        "seit" to "since {0}",
        "verlauf" to "History since {0}",
        "spanne" to "Range since {0}",
        "spanne.wert" to "{0} to {1}",
        "schritte" to "Latest rate moves",
        "schritt" to "{0} to {1}",
        "info" to "Interest rate at which the central bank lends to banks or pays on their deposits. It drives lending rates, savings rates and the exchange rate.\n\n" +
            "• Increase: credit gets more expensive, which slows demand and inflation.\n" +
            "• Cut: credit gets cheaper, which supports the economy and asset prices.\n\n" +
            "In euro area countries the rate of the European Central Bank applies since accession, before that the rate of the national central bank.\n\n" +
            "Source: Bank for International Settlements (BIS), Central bank policy rates. Monthly values, rate moves from the daily values. As of {0}.",
        "quelle" to "BIS, central bank policy rates",
    )
}
