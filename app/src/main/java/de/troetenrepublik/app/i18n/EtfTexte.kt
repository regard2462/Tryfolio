package de.troetenrepublik.app.i18n

import de.troetenrepublik.app.data.Lang

// Texte der Streuung in der ETF-Durchschau, 0.78. Ein Schluessel je Text, Platzhalter {0}, {1} ... fuer Werte.
internal object EtfTexte {

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
        "stufe.BREIT" to "Breit gestreut",
        "stufe.KONZENTRIERT" to "Konzentriert",
        "stufe.STARK" to "Stark konzentriert",
        "top" to "{0} größte Positionen",
        "top.eine" to "Größte Position",
        "rest" to "Übrige Positionen",
        "uebrige" to "Übrige",
        "ueber" to "über {0} ETFs",
        "anzahl" to "Positionen",
        "wirksam" to "Wirksame Zahl der Positionen",
        "laender" to "Länder",
        "risiko" to "Hohes Landrisiko",
        "weitere" to "{0} weitere",
        "waehrungen" to "Währungen",
        "hinweis.stufe" to "Ab {0} in den genannten größten Positionen gilt ein Fonds hier als konzentriert, ab {1} als stark konzentriert.",
        "hinweis.wirksam" to "Wirksame Zahl: so viele gleich große Positionen ergäben dieselbe Streuung.",
        "hinweis.index" to "Länder nach dem abgebildeten Index, Währungen nach den Ländern.",
        "hinweis.gezeigt" to "Länder nach Sitz oder Börse der gezeigten Positionen, als Anteil am Fonds.",
        "hinweis.risiko" to "Landrisiko hoch: unter {0} von 100 Punkten.",
    )

    private val EN = mapOf(
        "stufe.BREIT" to "Broadly diversified",
        "stufe.KONZENTRIERT" to "Concentrated",
        "stufe.STARK" to "Highly concentrated",
        "top" to "{0} largest holdings",
        "top.eine" to "Largest holding",
        "rest" to "Other holdings",
        "uebrige" to "Other",
        "ueber" to "via {0} ETFs",
        "anzahl" to "Holdings",
        "wirksam" to "Effective number of holdings",
        "laender" to "Countries",
        "risiko" to "High country risk",
        "weitere" to "{0} more",
        "waehrungen" to "Currencies",
        "hinweis.stufe" to "From {0} in the largest holdings listed, a fund counts as concentrated here, from {1} as highly concentrated.",
        "hinweis.wirksam" to "Effective number: this many equally sized holdings would give the same spread.",
        "hinweis.index" to "Countries by the index tracked, currencies by country.",
        "hinweis.gezeigt" to "Countries by domicile or exchange of the holdings shown, as a share of the fund.",
        "hinweis.risiko" to "High country risk: below {0} of 100 points.",
    )
}
