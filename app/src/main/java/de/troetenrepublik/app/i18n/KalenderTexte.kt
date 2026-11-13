package de.troetenrepublik.app.i18n

import de.troetenrepublik.app.data.Lang

// Texte des Handelskalenders zu Insidern und weiteren Markierungen (0.79).
// Ein Schluessel je Text, Platzhalter {0}, {1} fuer Werte.
internal object KalenderTexte {

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
        "insider" to "Insider",
        "insider.kauf" to "Kauf",
        "insider.verkauf" to "Verkauf",
        "zahlen" to "Quartalszahlen",
        "zahlen.ergebnis" to "Ergebnis, Erwartung {0}",
        "zahlen.ergebnis.ohne" to "Ergebnis",
        "zahlen.erwartet" to "Termin, Erwartung {0}",
        "zahlen.erwartet.ohne" to "Termin",
        "zahlen.geschaetzt" to "Termin geschätzt",
        "zahlen.geschaetzt.erwartung" to "Termin geschätzt, Erwartung {0}",
        "kongress" to "Kongress",
        "kongress.haus" to "Repräsentantenhaus",
        "kongress.senat" to "Senat",
        "verfall" to "Verfall",
        "verfall.verkauft" to "verkaufte Option",
        "exdiv" to "Ex-Dividende",
        "exdiv.erhalten" to "Ex-Tag",
        "exdiv.erwartet" to "Ex-Tag erwartet",
        "analysten" to "Analysten",
        "analysten.hoch" to "Hochstufung",
        "analysten.runter" to "Abstufung",
        "kauf" to "Käufe",
        "kauf.stueck" to "Kauf · {0} Stk. zu {1}",
        "sparplan.stueck" to "Sparplan · {0} Stk. zu {1}",
        "makro" to "Makrotermine",
        "makro.legende" to "Fed, EZB, CPI",
        "feiertag" to "Börse geschlossen",
    )

    private val EN = mapOf(
        "insider" to "Insider",
        "insider.kauf" to "Buy",
        "insider.verkauf" to "Sale",
        "zahlen" to "Earnings",
        "zahlen.ergebnis" to "Result, expected {0}",
        "zahlen.ergebnis.ohne" to "Result",
        "zahlen.erwartet" to "Report, expected {0}",
        "zahlen.erwartet.ohne" to "Report",
        "zahlen.geschaetzt" to "Date estimated",
        "zahlen.geschaetzt.erwartung" to "Date estimated, expected {0}",
        "kongress" to "Congress",
        "kongress.haus" to "House",
        "kongress.senat" to "Senate",
        "verfall" to "Expiry",
        "verfall.verkauft" to "sold option",
        "exdiv" to "Ex-dividend",
        "exdiv.erhalten" to "Ex-date",
        "exdiv.erwartet" to "Ex-date expected",
        "analysten" to "Analysts",
        "analysten.hoch" to "Upgrade",
        "analysten.runter" to "Downgrade",
        "kauf" to "Buys",
        "kauf.stueck" to "Buy · {0} sh. at {1}",
        "sparplan.stueck" to "Savings plan · {0} sh. at {1}",
        "makro" to "Macro events",
        "makro.legende" to "Fed, ECB, CPI",
        "feiertag" to "Market closed",
    )
}
