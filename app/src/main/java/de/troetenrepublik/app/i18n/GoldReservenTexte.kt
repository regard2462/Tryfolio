package de.troetenrepublik.app.i18n

// Texte zu den Goldreserven der Zentralbanken (ui/GoldReservenKarte.kt, engine/GoldReserven.kt). Ein Schluessel je Text,
// Platzhalter {0}, {1} ... fuer Werte.
internal object GoldReservenTexte {

    fun text(deutsch: Boolean, key: String, w: List<String> = emptyList()): String {
        var t = (if (deutsch) DE else EN)[key] ?: return ""
        w.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
        return t
    }

    fun schluessel(deutsch: Boolean): Set<String> = (if (deutsch) DE else EN).keys

    private val DE = mapOf(
        "titel" to "Goldreserven der Zentralbanken",
        "kern" to "{0} hält am meisten Gold: {1} t ({2} der gemeldeten Bestände). Die zehn größten Halter besitzen zusammen {3} t ({4}).",
        "rang" to "Größte Halter, Tonnen",
        "alle" to "Alle Länder ({0})", "weitere" to "{0} weitere",
        "netto.titel" to "Käufe und Verkäufe in 12 Monaten", "netto.unter" to "Bestand gegenüber 12 Monaten davor, Tonnen",
        "netto.kauf" to "Größte Käufer", "netto.verkauf" to "Größte Verkäufer",
        "info" to "Goldbestand der Zentralbanken in Feinunzen, gemeldet an den Internationalen Währungsfonds (IWF, International Reserves and Foreign Currency Liquidity). Stand {0}.\n\n• Nur meldende Länder ({1}), nicht jedes Land der Welt\n• Tonnen aus Feinunzen umgerechnet (1 t = 32.150,75 Unzen)\n• Anteil: an der Summe aller meldenden Länder\n• Käufe und Verkäufe: Bestand gegenüber 12 Monaten davor, nur Länder mit beiden Werten\n• Stand: Monat, in dem die meisten Länder zuletzt gemeldet haben, einzelne Länder weichen ab",
    )

    private val EN = mapOf(
        "titel" to "Central bank gold reserves",
        "kern" to "{0} holds the most gold: {1} t ({2} of reported holdings). The ten largest holders own {3} t together ({4}).",
        "rang" to "Largest holders, tonnes",
        "alle" to "All countries ({0})", "weitere" to "{0} more",
        "netto.titel" to "Purchases and sales in 12 months", "netto.unter" to "Holdings compared with 12 months earlier, tonnes",
        "netto.kauf" to "Largest buyers", "netto.verkauf" to "Largest sellers",
        "info" to "Central bank gold holdings in troy ounces, reported to the International Monetary Fund (IMF, International Reserves and Foreign Currency Liquidity). As of {0}.\n\n• Reporting countries only ({1}), not every country in the world\n• Tonnes converted from troy ounces (1 t = 32,150.75 oz)\n• Share: of the sum of all reporting countries\n• Purchases and sales: holdings compared with 12 months earlier, only countries with both values\n• As of: the month in which most countries last reported, a few countries differ",
    )
}
