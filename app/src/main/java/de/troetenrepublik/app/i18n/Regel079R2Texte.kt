package de.troetenrepublik.app.i18n

import de.troetenrepublik.app.data.Lang

// Texte der Regelpruefung 0.79, Teil 2 (Derivate, Karten und Blaetter von Discover bis Halter). Ein Schluessel je Text,
// Platzhalter {0}, {1} fuer Werte.
internal object Regel079R2Texte {

    fun text(deutsch: Boolean, key: String, w: List<String> = emptyList()): String {
        var t = (if (deutsch) DE else EN)[key] ?: return ""
        w.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
        return t
    }

    // Alle Schluessel einer Sprache (der Test vergleicht beide Sprachen)
    fun schluessel(deutsch: Boolean): Set<String> = (if (deutsch) DE else EN).keys

    private val DE = mapOf(
        // Optionskennzahlen der Derivateseite
        "opt.innerer" to "Was die Option beim sofortigen Ausüben wert wäre: Abstand zwischen Kurs und Basispreis, im Geld positiv, sonst null.",
        "opt.zeitwert" to "Der Teil des Preises über dem inneren Wert. Er schmilzt bis zur Fälligkeit auf null.",
        "opt.aufgeld" to "Aufpreis der Option gegenüber dem direkten Kauf des Basiswerts, in Prozent.",
        "opt.schwelle" to "Kurs des Basiswerts bei Fälligkeit, ab dem die Option den Kaufpreis wieder einspielt.",
        "opt.delta" to "So stark ändert sich der Optionspreis, wenn der Basiswert um 1 steigt. 0,5 heißt: halb so viel wie der Basiswert.",
        "opt.gamma" to "So stark ändert sich das Delta, wenn der Basiswert um 1 steigt.",
        "opt.vega" to "So viel ändert sich der Preis, wenn die erwartete Schwankung um 1 Prozentpunkt steigt.",
        "opt.theta" to "Wertverlust pro Tag allein durch die Zeit.",
        // Termine
        "past.mitDividende" to "Vergangene Termine, Dividenden je Aktie",
        // Wachstum im Jahr und im Folgejahr
        "rang.danach" to "danach",
        // 13F-Gesamtbild eines Wertpapiers
        "g13.neu" to "Neu",
        "g13.weg" to "Ausgestiegen",
        "g13.auf" to "Aufgestockt",
        "g13.ab" to "Abgebaut",
        // Vorzeichenwechsel des Gewinns, kurz in der Spalte der Veraenderung
        "wechsel.aus" to "Aus Verlust",
        // Spanne von bis
        "bis" to "bis",
        "wechsel.in" to "In Verlust",
    )

    private val EN = mapOf(
        "opt.innerer" to "What the option would be worth if exercised right now: gap between price and strike, positive in the money, otherwise zero.",
        "opt.zeitwert" to "The part of the price above the intrinsic value. It melts to zero by expiry.",
        "opt.aufgeld" to "Extra cost of the option compared with buying the underlying directly, in percent.",
        "opt.schwelle" to "Price of the underlying at expiry from which the option earns back its purchase price.",
        "opt.delta" to "How much the option price moves when the underlying rises by 1. 0.5 means half as much as the underlying.",
        "opt.gamma" to "How much the delta changes when the underlying rises by 1.",
        "opt.vega" to "How much the price changes when expected volatility rises by 1 percentage point.",
        "opt.theta" to "Value lost per day from the passing of time alone.",
        "past.mitDividende" to "Past events, dividends per share",
        "rang.danach" to "then",
        "g13.neu" to "New",
        "g13.weg" to "Exited",
        "g13.auf" to "Increased",
        "g13.ab" to "Reduced",
        "wechsel.aus" to "From loss",
        "bis" to "to",
        "wechsel.in" to "Into loss",
    )
}

// Kurzzugriff in der aktuellen Sprache
internal fun r2(key: String, vararg w: String): String = Regel079R2Texte.text(I18n.lang == Lang.DE, key, w.toList())
