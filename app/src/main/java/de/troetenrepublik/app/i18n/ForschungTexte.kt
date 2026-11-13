package de.troetenrepublik.app.i18n

// Texte zu den Forschungsdaten, 0.78: fruehe Bitcoin-Kurse, Put/Call-Rueckblick, lange Reihen im Laenderblatt. Ein
// Schluessel je Text, Platzhalter {0}, {1} fuer Werte.
internal object ForschungTexte {

    fun text(deutsch: Boolean, key: String, w: List<String> = emptyList()): String {
        var t = (if (deutsch) DE else EN)[key] ?: return ""
        w.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
        return t
    }

    // Alle Schluessel einer Sprache (der Test vergleicht beide Sprachen)
    fun schluessel(deutsch: Boolean): Set<String> = (if (deutsch) DE else EN).keys

    private val DE = mapOf(
        "btc.hinweis" to "Kurse vor dem {0}: Coin Metrics Community Data, in Euro zum heutigen Kurs.",
        "pc.rang" to "Höher als an {0} der Handelstage {1} bis {2}",
        "pc.median" to "Median {0}",
        "lr.titel" to "Seit {0}",
        "lr.inflation" to "Inflation",
        "lr.proJahrAb" to "pro Jahr ab {0}",
        "lr.zins" to "Kurzfristiger Zins",
        "lr.anleihe" to "Staatsanleihen",
        "lr.mittelAb" to "Mittel ab {0}",
        "lr.preise" to "Preisniveau, {0} = 1",
        "lr.preisLinie" to "Preise",
        "lr.zinsen" to "Zinsen in Prozent",
        "lr.kurz" to "Kurzfristig",
        "lr.lang" to "Staatsanleihen",
        "lr.jahrhunderte" to "Inflation pro Jahr",
        "lr.zeitraum" to "{0} bis {1}",
        "lr.staerkstes" to "Stärkster Preisanstieg in einem Jahr ({0})",
        "lr.info" to "Verbraucherpreise als Jahreswerte. Inflation pro Jahr als geometrisches Mittel zwischen erstem und letztem Jahr. Zinsen als Mittel der Jahreswerte. Wechsel der Währung sind in der Preisreihe der Quelle verkettet.",
        "lr.quelle" to "Quelle: {0}. Zu zitieren als:",
        "lr.gerundet" to "Werte unverändert wie veröffentlicht.",
    )

    private val EN = mapOf(
        "btc.hinweis" to "Prices before {0}: Coin Metrics Community Data, in euros at today's rate.",
        "pc.rang" to "Higher than on {0} of trading days {1} to {2}",
        "pc.median" to "Median {0}",
        "lr.titel" to "Since {0}",
        "lr.inflation" to "Inflation",
        "lr.proJahrAb" to "per year from {0}",
        "lr.zins" to "Short-term rate",
        "lr.anleihe" to "Government bonds",
        "lr.mittelAb" to "average from {0}",
        "lr.preise" to "Price level, {0} = 1",
        "lr.preisLinie" to "Prices",
        "lr.zinsen" to "Interest rates in percent",
        "lr.kurz" to "Short-term",
        "lr.lang" to "Government bonds",
        "lr.jahrhunderte" to "Inflation per year",
        "lr.zeitraum" to "{0} to {1}",
        "lr.staerkstes" to "Largest price rise in one year ({0})",
        "lr.info" to "Consumer prices as annual figures. Inflation per year as the geometric mean between the first and the last year. Interest rates as the mean of the annual figures. Changes of currency are chained in the price series of the source.",
        "lr.quelle" to "Source: {0}. To be cited as:",
        "lr.gerundet" to "Figures unchanged as published.",
    )
}
