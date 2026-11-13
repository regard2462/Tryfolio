package de.troetenrepublik.app.i18n

import de.troetenrepublik.app.data.Lang

// Texte der vollstaendigen Durchsicht 0.79 (Teil C: Halter und Globus). Ein Schluessel je Text, Platzhalter {0}, {1}.
internal object Durchsicht079CTexte {

    fun text(deutsch: Boolean, key: String, w: List<String> = emptyList()): String {
        var t = (if (deutsch) DE else EN)[key] ?: return ""
        w.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
        return t
    }

    // Alle Schluessel einer Sprache (der Test vergleicht beide Sprachen)
    fun schluessel(deutsch: Boolean): Set<String> = (if (deutsch) DE else EN).keys

    private val DE = mapOf(
        // Anteilsbalken der Halter: Insider und der uebrige Streubesitz (Institutionen kommen aus kernHalter)
        "hl.insider" to "Insider {0}",
        "hl.rest" to "Übrige {0}",
    )

    private val EN = mapOf(
        "hl.insider" to "Insiders {0}",
        "hl.rest" to "Other {0}",
    )
}

// Kurzzugriff in der aktuellen Sprache
internal fun dc(key: String, vararg w: String): String = Durchsicht079CTexte.text(I18n.lang == Lang.DE, key, w.toList())
