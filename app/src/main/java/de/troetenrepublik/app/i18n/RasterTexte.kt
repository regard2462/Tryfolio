package de.troetenrepublik.app.i18n

// Texte des Monatsrasters (Jahr mal Monat). Ein Schluessel je Text, Platzhalter {0}, {1} fuer Werte.
internal object RasterTexte {

    fun text(deutsch: Boolean, key: String, w: List<String> = emptyList()): String {
        var t = (if (deutsch) DE else EN)[key] ?: return ""
        w.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
        return t
    }

    // Alle Schluessel einer Sprache (der Test vergleicht beide Sprachen)
    fun schluessel(deutsch: Boolean): Set<String> = (if (deutsch) DE else EN).keys

    private val DE = mapOf(
        "titel" to "Monatsraster",
        "kern" to "{0}: {1}",
        "jahr" to "Jahr",
        "info" to "Rendite je Kalendermonat, rechts die Rendite des ganzen Jahres. Ein Monat vergleicht den letzten Kurs mit dem letzten Kurs des Vormonats. " +
            "Das laufende Jahr zählt bis heute. Ohne Januar in den Daten fehlt die Jahresrendite.",
    )

    private val EN = mapOf(
        "titel" to "Monthly grid",
        "kern" to "{0}: {1}",
        "jahr" to "Year",
        "info" to "Return per calendar month, with the return of the whole year on the right. A month compares the last price with the last price of the month before. " +
            "The current year counts up to today. Without January in the data the yearly return is missing.",
    )
}
