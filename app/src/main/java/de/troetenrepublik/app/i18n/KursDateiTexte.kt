package de.troetenrepublik.app.i18n

import de.troetenrepublik.app.data.Lang

// Texte der eigenen Kursdateien (Einlesen, Liste, Quelle). Ein Schluessel je Text, Platzhalter {0} fuer Werte.
object KursDateiTexte {

    fun text(deutsch: Boolean, key: String, w: List<String> = emptyList()): String {
        var t = (if (deutsch) DE else EN)[key] ?: return ""
        w.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
        return t
    }

    // In der Sprache der App.
    fun t(key: String, vararg w: String): String = text(I18n.lang == Lang.DE, key, w.toList())

    // Alle Schluessel einer Sprache (der Test vergleicht beide Sprachen)
    fun schluessel(deutsch: Boolean): Set<String> = (if (deutsch) DE else EN).keys

    private val DE = mapOf(
        "titel" to "Eigene Kursdateien",
        "formate" to "Stooq-Textdateien, ZIP und CSV (Yahoo, Investing, eigene Tabellen)",
        "einlesen" to "Kursdatei einlesen",
        "symbol" to "Symbol",
        "von" to "Von",
        "bis" to "Bis",
        "tage" to "Tage",
        "tageZahl" to "{0} Tage",
        "reihen" to "Reihen",
        "reihenZahl" to "{0} Reihen",
        "reihenZahl1" to "1 Reihe",
        "zeitraum" to "{0} bis {1}",
        "uebersprungen" to "{0} Zeilen übersprungen",
        "uebersprungen1" to "1 Zeile übersprungen",
        "speichern" to "Speichern",
        "alleSpeichern" to "Alle speichern",
        "gespeichert" to "{0} Reihen gespeichert",
        "gespeichert1" to "1 Reihe gespeichert",
        "nichtGespeichert" to "Nicht gespeichert",
        "nichtErkannt" to "Keine Kurse erkannt",
        "unlesbar" to "Datei nicht lesbar",
        "zuGross" to "Datei über 200 MB, nichts gelesen",
        "zuViel" to "{0} Reihen über der Grenze von 2.000 nicht übernommen",
        "loeschen" to "Löschen",
        "quelle" to "Eigene Datei",
    )

    private val EN = mapOf(
        "titel" to "Own price files",
        "formate" to "Stooq text files, ZIP and CSV (Yahoo, Investing, own tables)",
        "einlesen" to "Read price file",
        "symbol" to "Symbol",
        "von" to "From",
        "bis" to "To",
        "tage" to "Days",
        "tageZahl" to "{0} days",
        "reihen" to "Series",
        "reihenZahl" to "{0} series",
        "reihenZahl1" to "1 series",
        "zeitraum" to "{0} to {1}",
        "uebersprungen" to "{0} rows skipped",
        "uebersprungen1" to "1 row skipped",
        "speichern" to "Save",
        "alleSpeichern" to "Save all",
        "gespeichert" to "{0} series saved",
        "gespeichert1" to "1 series saved",
        "nichtGespeichert" to "Not saved",
        "nichtErkannt" to "No prices recognized",
        "unlesbar" to "File not readable",
        "zuGross" to "File over 200 MB, nothing read",
        "zuViel" to "{0} series over the limit of 2,000 not taken over",
        "loeschen" to "Delete",
        "quelle" to "Own file",
    )
}
