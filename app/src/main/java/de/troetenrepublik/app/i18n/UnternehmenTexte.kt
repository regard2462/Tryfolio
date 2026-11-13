package de.troetenrepublik.app.i18n

import de.troetenrepublik.app.data.Lang

// Texte des eingeklappten Blocks „Unternehmen“ auf der Wertpapierseite: Titel, Zusammenfassungszeile und die Namen der
// Themen darin. Ein Schluessel je Text, Platzhalter {0}, {1} fuer Werte.
internal object UnternehmenTexte {

    fun text(deutsch: Boolean, key: String, w: List<String> = emptyList()): String {
        var t = (if (deutsch) DE else EN)[key] ?: return ""
        w.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
        return t
    }

    // In der Sprache der App
    fun t(key: String, vararg w: String): String = text(I18n.lang == Lang.DE, key, w.toList())

    // Zusammenfassungszeile: Anzahl und Namen der Themen mit Daten
    fun unter(deutsch: Boolean, themen: List<String>): String =
        text(deutsch, if (themen.size == 1) "unter.eins" else "unter.mehr", listOf(themen.size.toString(), themen.joinToString(", ")))

    fun unter(themen: List<String>): String = unter(I18n.lang == Lang.DE, themen)

    // Alle Schluessel einer Sprache (der Test vergleicht beide Sprachen)
    fun schluessel(deutsch: Boolean): Set<String> = (if (deutsch) DE else EN).keys

    private val DE = mapOf(
        "titel" to "Unternehmen",
        "unter.eins" to "{0} Thema: {1}",
        "unter.mehr" to "{0} Themen: {1}",
        "t.STANDORTE" to "Standorte",
        "t.GESCHICHTE" to "Geschichte",
        "t.AUFTRAG" to "Auftragsbestand",
        "t.PRUEFER" to "Prüfer",
        "t.RECHT" to "Rechtsstreit",
        "t.ENTLASSUNGEN" to "Entlassungen",
        "t.PATENTE" to "Patente",
        "t.EMISSIONEN" to "Emissionen",
        "t.STAATSGELD" to "Staatsgeld",
    )

    private val EN = mapOf(
        "titel" to "Company",
        "unter.eins" to "{0} topic: {1}",
        "unter.mehr" to "{0} topics: {1}",
        "t.STANDORTE" to "Locations",
        "t.GESCHICHTE" to "History",
        "t.AUFTRAG" to "Backlog",
        "t.PRUEFER" to "Auditor",
        "t.RECHT" to "Litigation",
        "t.ENTLASSUNGEN" to "Layoffs",
        "t.PATENTE" to "Patents",
        "t.EMISSIONEN" to "Emissions",
        "t.STAATSGELD" to "Government money",
    )
}
