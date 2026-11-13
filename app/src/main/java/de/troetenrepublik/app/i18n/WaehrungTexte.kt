package de.troetenrepublik.app.i18n

// Texte des Abschnitts Wechselkurse im Werkzeug Boersengeschichte. Ein Schluessel je Text, Platzhalter {0}, {1} fuer Werte.
internal object WaehrungTexte {

    fun text(deutsch: Boolean, key: String, w: List<String> = emptyList()): String {
        var t = (if (deutsch) DE else EN)[key] ?: return ""
        w.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
        return t
    }

    // Alle Schluessel einer Sprache (der Test vergleicht beide Sprachen)
    fun schluessel(deutsch: Boolean): Set<String> = (if (deutsch) DE else EN).keys

    private val DE = mapOf(
        "titel" to "Wechselkurse",
        "kurz" to "US-Dollar in {0}: {1}, seit {2} {3}",
        "EUR" to "Euro",
        "GBP" to "Pfund",
        "JPY" to "Yen",
        "CHF" to "Franken",
        "CAD" to "Kanada-Dollar",
        "aktuell" to "Aktuell",
        "seit" to "US-Dollar seit {0}",
        "hoch" to "Höchster Stand",
        "tief" to "Tiefster Stand",
        "info" to "Monatsmittel der Wechselkurse der Federal Reserve (H.10), nominal. Steigt die Linie, ist der Dollar mehr wert.",
        "info.eur" to "Euro bis Dezember 1998: D-Mark, umgerechnet mit dem festen Kurs von 1,95583.",
    )

    private val EN = mapOf(
        "titel" to "Exchange rates",
        "kurz" to "US dollar in {0}: {1}, since {2} {3}",
        "EUR" to "Euro",
        "GBP" to "Pound",
        "JPY" to "Yen",
        "CHF" to "Franc",
        "CAD" to "Canadian dollar",
        "aktuell" to "Latest",
        "seit" to "US dollar since {0}",
        "hoch" to "Highest",
        "tief" to "Lowest",
        "info" to "Monthly averages of the Federal Reserve exchange rates (H.10), nominal. A rising line means the dollar is worth more.",
        "info.eur" to "Euro before January 1999: German mark, converted at the fixed rate of 1.95583.",
    )
}
