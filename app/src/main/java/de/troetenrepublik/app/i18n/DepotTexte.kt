package de.troetenrepublik.app.i18n

// Texte zu Depot, Analytics und den Depot-Werkzeugen, 0.78. Ein Schluessel je Text, Platzhalter {0}, {1} ... fuer Werte.
internal object DepotTexte {

    fun text(deutsch: Boolean, key: String, w: List<String>): String {
        var t = (if (deutsch) DE else EN)[key] ?: return ""
        w.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
        return t
    }

    // Alle Schluessel einer Sprache (der Test vergleicht beide Sprachen)
    fun schluessel(deutsch: Boolean): Set<String> = (if (deutsch) DE else EN).keys

    private val DE = mapOf(
        // Werkzeugliste: Seiten des Depot-Checks als eigene Zeilen, Optionen
        "wz.DIVERSIFICATION" to "Regionen, Branchen, Währungen",
        "wz.REBALANCE" to "Käufe und Verkäufe bis zum Zielgewicht",
        "wz.CRASH" to "Dein Depot in den Krisen seit 2000",
        "wz.SCENARIO" to "Zinsen, Dollar, Rohstoffe durchgerechnet",
        "wz.optionen" to "Optionen verkaufen, auffällige Kontrakte, Rechner",
        // Depotverlauf
        "dv.zeitgewichtet" to "Pro Jahr (zeitgewichtet)",
        "dv.geldgewichtet" to "Rendite pro Jahr (geldgewichtet)",
        "dv.alle" to "Alle in der Aktivität",
        // Anlegerprofil
        "ap.zuVerkaeufenUnter" to "Gewinnfaktor und Durchschnitt im Depotverlauf",
        // Streuung
        "st.globus" to "Mein Depot im Globus",
        "st.globusUnter" to "Karte, Vergleich mit dem Weltindex, Land je Aktie",
        "st.etfEuro" to "Kosten deiner ETFs pro Jahr, etwa",
        // Praesidialdokumente
        "pd.info" to "Executive Orders, Proklamationen und Memoranden des US-Präsidenten aus den letzten zwölf Monaten, aus dem Federal Register. Ein Bezug zu einem Wertpapier steht nur da, wenn der Titel die Firma oder ein Erzeugnis der Branche nennt.",
        // Vermoegensaufbau
        "wl.prozentStart" to "Anteil am Startwert",
        // Dividenden
        "div.vorSteuern" to "vor Steuern {0}",
        "div.termineUnter" to "Ex-Tage und Zahltage aller Werte",
        // Was waere, wenn
        "sz.umrechnung" to "nur Umrechnung",
        "sz.crash" to "Währungsschock im Crash-Test",
        "sz.crashUnter" to "Alle Fremdwährungen, Stärke wählbar",
    )

    private val EN = mapOf(
        "wz.DIVERSIFICATION" to "Regions, industries, currencies",
        "wz.REBALANCE" to "Buys and sells to reach target weights",
        "wz.CRASH" to "Your portfolio in the crises since 2000",
        "wz.SCENARIO" to "Rates, dollar, commodities worked through",
        "wz.optionen" to "Selling options, unusual contracts, calculator",
        "dv.zeitgewichtet" to "Per year (time-weighted)",
        "dv.geldgewichtet" to "Return per year (money-weighted)",
        "dv.alle" to "All in activity",
        "ap.zuVerkaeufenUnter" to "Profit factor and averages in the portfolio history",
        "st.globus" to "My portfolio on the globe",
        "st.globusUnter" to "Map, comparison with the world index, country per stock",
        "st.etfEuro" to "Costs of your ETFs per year, about",
        "pd.info" to "Executive orders, proclamations and memoranda of the US president from the last twelve months, from the Federal Register. A link to a security appears only when the title names the company or a product of its industry.",
        "wl.prozentStart" to "Share of the start value",
        "div.vorSteuern" to "before taxes {0}",
        "div.termineUnter" to "Ex-dates and payment dates of all stocks",
        "sz.umrechnung" to "conversion only",
        "sz.crash" to "Currency shock in the crash test",
        "sz.crashUnter" to "All foreign currencies, strength adjustable",
    )
}
