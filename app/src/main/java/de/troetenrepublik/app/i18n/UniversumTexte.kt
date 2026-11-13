package de.troetenrepublik.app.i18n

// Texte zum zweiten Universum (Nasdaq-100) im Rueckblick des Aktienfinders, 0.78. Platzhalter {0}, {1} fuer Werte.
internal object UniversumTexte {

    fun text(deutsch: Boolean, key: String, w: List<String> = emptyList()): String {
        var t = (if (deutsch) DE else EN)[key] ?: return ""
        w.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
        return HinweisTexte.einsetzen(deutsch, t)
    }

    fun schluessel(deutsch: Boolean): Set<String> = (if (deutsch) DE else EN).keys

    private val DE = mapOf(
        "wahl" to "Rückblick mit den Mitgliedern von damals",
        "kern.vor" to "Top 20 im Schnitt {0} Prozentpunkte Rendite pro Jahr vor dem MSCI World",
        "kern.hinter" to "Top 20 im Schnitt {0} Prozentpunkte Rendite pro Jahr hinter dem MSCI World",
        "zeitraum" to "{0} bis {1}, zwölf Monate nach jedem Monatsende, im Schnitt {2} bewertete Aktien",
        "top20" to "Top 20 des Finders",
        "alle" to "Alle Mitglieder",
        "treffer" to "Stichtage vor dem MSCI World",
        "trefferHinweis" to "{0} Stichtage",
        "abdeckung" to "Mitglieder mit Kursen",
        "abdeckungHinweis" to "Schnitt aller Stichtage, der Rest hat keine Kurse mehr",
        "hinweis" to "Bewertet nur mit Daten von damals, Mitglieder des Nasdaq-100 am jeweiligen Tag. {histPrognose}",
    )

    private val EN = mapOf(
        "wahl" to "Look-back with the members of the time",
        "kern.vor" to "Top 20 on average {0} percentage points of return per year ahead of the MSCI World",
        "kern.hinter" to "Top 20 on average {0} percentage points of return per year behind the MSCI World",
        "zeitraum" to "{0} to {1}, twelve months after each month end, {2} stocks rated on average",
        "top20" to "Finder's top 20",
        "alle" to "All members",
        "treffer" to "Dates ahead of the MSCI World",
        "trefferHinweis" to "{0} dates",
        "abdeckung" to "Members with prices",
        "abdeckungHinweis" to "Average of all dates, the rest no longer has prices",
        "hinweis" to "Rated only with data of the time, members of the Nasdaq-100 on each day. {histPrognose}",
    )
}
