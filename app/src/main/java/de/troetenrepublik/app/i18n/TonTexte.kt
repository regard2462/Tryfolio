package de.troetenrepublik.app.i18n

import de.troetenrepublik.app.data.Lang

// Texte des Abschnitts „Ton der Berichte“ auf der Wertpapierseite (GDELT). Ein Schluessel je Text, Platzhalter {0}, {1}.
internal object TonTexte {

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
        "titel" to "Ton der Berichte",
        "kern.DEUTLICH_NEGATIVER" to "deutlich negativer als zuvor",
        "kern.NEGATIVER" to "negativer als zuvor",
        "kern.UNVERAENDERT" to "wie zuvor",
        "kern.POSITIVER" to "positiver als zuvor",
        "kern.DEUTLICH_POSITIVER" to "deutlich positiver als zuvor",
        "unten" to "Letzte 30 Tage {0}, davor {1}",
        "verlauf" to "Wochenmittel, null ist neutral, hinterlegt die letzten 30 Tage",
        "info" to "Der Ton zeigt, wie negativ oder positiv Artikel formuliert sind. GDELT bewertet jeden Artikel englischsprachiger Medien, hier zählen die Artikel der letzten drei Monate, die die Firma zusammen mit Begriffen wie Aktie, Gewinn oder CEO nennen. Null ist neutral, Nachrichten liegen meist darunter. Verglichen wird der Schnitt der letzten 30 Tage mit dem der gut zwei Monate davor. Eine Änderung gilt erst ab {0} Tonpunkten und nur, wenn sie über dem Rauschen der Tageswerte liegt, deutlich ab {1}. Der Ton sagt nichts über den Kurs.\n\nQuelle: GDELT Project (https://www.gdeltproject.org), Abruf {2}.",
        "quelle" to "GDELT Project",
    )

    private val EN = mapOf(
        "titel" to "Tone of coverage",
        "kern.DEUTLICH_NEGATIVER" to "much more negative than before",
        "kern.NEGATIVER" to "more negative than before",
        "kern.UNVERAENDERT" to "same as before",
        "kern.POSITIVER" to "more positive than before",
        "kern.DEUTLICH_POSITIVER" to "much more positive than before",
        "unten" to "Last 30 days {0}, before that {1}",
        "verlauf" to "Weekly average, zero is neutral, shaded the last 30 days",
        "info" to "The tone shows how negative or positive articles are worded. GDELT scores every article of English-language media, here the articles of the last three months that mention the company together with terms such as stock, earnings or CEO. Zero is neutral, news usually sits below it. The average of the last 30 days is compared with that of the roughly two months before. A change counts from {0} tone points and only if it stands out from the noise of the daily values, as large from {1}. The tone says nothing about the price.\n\nSource: GDELT Project (https://www.gdeltproject.org), retrieved {2}.",
        "quelle" to "GDELT Project",
    )
}
