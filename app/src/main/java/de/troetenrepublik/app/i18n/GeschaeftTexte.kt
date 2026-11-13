package de.troetenrepublik.app.i18n

import de.troetenrepublik.app.data.Lang

// Texte der Zeilen zu Kaeufen und Verkaeufen von Personen (Kurs seit dem Geschaeft, Rest im Bestand, Gewinn), 0.79.
// Ein Schluessel je Text, Platzhalter {0}, {1} fuer Werte.
internal object GeschaeftTexte {

    fun text(deutsch: Boolean, key: String, w: List<String> = emptyList()): String {
        var t = (if (deutsch) DE else EN)[key] ?: return ""
        w.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
        return t
    }

    // In der Sprache der App
    fun t(key: String, vararg w: String): String = text(I18n.lang == Lang.DE, key, w.toList())

    // Alle Schluessel einer Sprache (der Test vergleicht beide Sprachen)
    fun schluessel(deutsch: Boolean): Set<String> = (if (deutsch) DE else EN).keys

    private val DE = mapOf(
        "seit.verkauf" to "seit Verkauf",
        "seit.kauf" to "seit Kauf",
        "verkauf.am" to "{0} · Kurs {1}",
        "kauf.am" to "{0} · Kurs {1}",
        "erloes" to "Erlös {0}",
        "kaufbetrag" to "Kauf {0}",
        "kauf.datum" to "Kauf am {0}",
        "kaeufe.bis" to "{0} Käufe, zuletzt am {1}",
        "quartal.bis" to "Quartal bis {0}",
        "quartale.bis" to "{0} Quartale, zuletzt bis {1}",
        "anteil.verkauft" to "{0} verkauft",
        "rest" to "Rest {0}",
        "wert.heute" to "heute {0}",
        "gewinn" to "Gewinn beim Verkauf",
        "verlust" to "Verlust beim Verkauf",
        "farben" to "Grün: Der Kurs ist seit dem Geschäft so gelaufen, dass es für die Person gut war (nach einem Verkauf gefallen, nach einem Kauf gestiegen). Rot: gegenteilig. Die Zahl ist der Kursabstand heute gegen den Kurs des Geschäfts.",
        "gewinnBeleg" to "Gewinn nur, wo die Meldung einen Einstandspreis nennt (Kauf am Markt oder Ausübungspreis einer Option am selben Tag). Bei Aktien aus Zuteilungen oder frühen Käufen nennt die Meldung keinen Einstand.",
    )

    private val EN = mapOf(
        "seit.verkauf" to "since sale",
        "seit.kauf" to "since purchase",
        "verkauf.am" to "{0} · price {1}",
        "kauf.am" to "{0} · price {1}",
        "erloes" to "Proceeds {0}",
        "kaufbetrag" to "Bought {0}",
        "kauf.datum" to "Bought on {0}",
        "kaeufe.bis" to "{0} purchases, latest on {1}",
        "quartal.bis" to "Quarter to {0}",
        "quartale.bis" to "{0} quarters, latest to {1}",
        "anteil.verkauft" to "{0} sold",
        "rest" to "{0} left",
        "wert.heute" to "worth {0} now",
        "gewinn" to "Gain on sale",
        "verlust" to "Loss on sale",
        "farben" to "Green: the price moved since the trade in a way that was good for the person (fallen after a sale, risen after a purchase). Red: the opposite. The number is today's price against the price of the trade.",
        "gewinnBeleg" to "Gain only where the filing gives a cost basis (open-market purchase or the exercise price of an option on the same day). For shares from grants or early purchases the filing gives no cost basis.",
    )
}
