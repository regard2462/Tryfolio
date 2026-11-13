package de.troetenrepublik.app.i18n

// Texte der Stueckliste beim Umschichten und unter dem Vorschlagsdepot, 0.78. Platzhalter {0}, {1} fuer Werte.
internal object StuecklisteTexte {

    fun text(deutsch: Boolean, key: String, w: List<String> = emptyList()): String {
        var t = (if (deutsch) DE else EN)[key] ?: return ""
        w.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
        return t
    }

    fun schluessel(deutsch: Boolean): Set<String> = (if (deutsch) DE else EN).keys

    private val DE = mapOf(
        "titel" to "Stückliste",
        "info" to "Die Aufträge in Euro als Stückzahlen zum aktuellen Kurs.\n\n• Verkäufe und Käufe werden auf ganze Stücke abgerundet.\n• Übriges Geld geht Stück für Stück an den Wert, dem am meisten zum Zielbetrag fehlt, solange ein weiteres Stück näher ans Ziel führt.\n• Was danach übrig bleibt, steht als Restbetrag da.\n• Mit Bruchstücken wird jeder Betrag genau getroffen.\n\nOrdergebühren und Spread sind nicht eingerechnet.",
        "bruch" to "Bruchstücke",
        "betrag" to "Anlagebetrag",
        "kauf" to "{0} kaufen",
        "verkauf" to "{0} verkaufen",
        "je" to "{0} je Stück · {1}",
        "summeKauf" to "Käufe",
        "summeVerkauf" to "Verkäufe",
        "rest" to "Restbetrag",
        "restHinweis" to "reicht für kein weiteres passendes Stück",
        "leer" to "Kein ganzes Stück passt in die Beträge.",
        "ohneKurs" to "Keine Kurse geladen.",
        "fehlend" to "{0} Werte ohne Kurs fehlen.",
    )

    private val EN = mapOf(
        "titel" to "Share list",
        "info" to "The orders in euros as share counts at the current price.\n\n• Sales and purchases are rounded down to whole shares.\n• Remaining money goes share by share to the holding furthest below its target amount, as long as one more share brings it closer to the target.\n• What is left after that is shown as remaining cash.\n• With fractional shares every amount is met exactly.\n\nOrder fees and spread are not included.",
        "bruch" to "Fractional shares",
        "betrag" to "Amount to invest",
        "kauf" to "Buy {0}",
        "verkauf" to "Sell {0}",
        "je" to "{0} per share · {1}",
        "summeKauf" to "Purchases",
        "summeVerkauf" to "Sales",
        "rest" to "Remaining cash",
        "restHinweis" to "not enough for another fitting share",
        "leer" to "No whole share fits the amounts.",
        "ohneKurs" to "No prices loaded.",
        "fehlend" to "{0} holdings without a price are missing.",
    )
}
