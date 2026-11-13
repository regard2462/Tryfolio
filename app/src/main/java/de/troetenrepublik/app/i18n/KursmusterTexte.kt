package de.troetenrepublik.app.i18n

// Texte der Ansicht „Kursmuster“ im Aktienfinder, 0.78. Ein Schluessel je Text, Platzhalter {0}, {1} ... fuer Werte.
internal object KursmusterTexte {

    fun text(deutsch: Boolean, key: String, w: List<String>): String {
        var t = (if (deutsch) DE else EN)[key] ?: return ""
        w.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
        return HinweisTexte.einsetzen(deutsch, t)
    }

    // Alle Schluessel einer Sprache (der Test vergleicht beide Sprachen)
    fun schluessel(deutsch: Boolean): Set<String> = (if (deutsch) DE else EN).keys

    private val DE = mapOf(
        "ansicht" to "Kursmuster",
        "muster.HOCH20" to "Neues 20-Tage-Hoch",
        "muster.FLAGGE" to "Bullenflagge",
        "muster.UEBER_SCHNITT20" to "Zurück über 20-Tage-Durchschnitt",
        "muster.RSI_UEBERVERKAUFT" to "RSI überverkauft",
        "muster.RSI_UMKEHR" to "RSI-Umkehr",
        "regel.HOCH20" to "Schluss über allen Schlüssen der 20 Handelstage davor",
        "regel.FLAGGE" to "Über 10 % Anstieg in 10 Handelstagen, danach 5 Schlüsse in höchstens 5 % Spanne",
        "regel.UEBER_SCHNITT20" to "Schluss am Vortag unter dem 20-Tage-Durchschnitt, jetzt darüber",
        "regel.RSI_UEBERVERKAUFT" to "RSI über 14 Tage unter 30",
        "regel.RSI_UMKEHR" to "RSI am Vortag unter 30, Schluss jetzt höher",
        "zahl.HOCH20" to "{0} über altem Hoch",
        "zahl.FLAGGE" to "Anstieg {0}, Spanne {1}",
        "zahl.UEBER_SCHNITT20" to "{0} über Durchschnitt",
        "zahl.RSI_UEBERVERKAUFT" to "RSI {0}",
        "zahl.RSI_UMKEHR" to "RSI {0}",
        "kursAb" to "Kurs ab {0}",
        "kursJeder" to "Jeder Kurs",
        "volumenAb" to "Volumen ab {0}",
        "volumenJedes" to "Jedes Volumen",
        "suchen" to "Kursmuster suchen",
        "neu" to "Neu suchen",
        "laedt" to "Lädt {0} von {1}",
        "kern" to "{0} Treffer in {1} Werten",
        "ohneKurse" to "{0} ohne Kurse",
        "keine" to "Kein Wert zeigt dieses Muster.",
        "ohneAntwort" to "Keine Kurse erhalten.",
        "rueckblick" to "20 Handelstage danach: {0} im Schnitt, alle Werte {1} ({2} Fälle im letzten Jahr)",
        "rueck.titel" to "20 Handelstage danach ({0} Fälle im letzten Jahr)",
        "rueck.muster" to "Nach dem Muster",
        "rueck.alle" to "Alle Werte",
        "mehr" to "{0} weitere",
        "weniger" to "Weniger",
        "erklaerung" to "• Grundlage: Tagesschlusskurse eines Jahres. Während des Handels zählt der letzte Kurs als Schluss\n" +
            "• Geprüft: die {0} größten Werte des Markts nach Börsenwert, die den Filter bestehen, dazu Depot und Watchlist ohne Filter\n" +
            "• Volumen: mittlere Stückzahl je Tag über drei Monate\n" +
            "• Rückblick: erster Tag eines Musters im letzten Jahr, Rendite über 20 Handelstage gegen alle geprüften Werte am selben Tag\n" +
            "• Der Rückblick ist {hk}: ohne Kosten und Dividenden, nur mit heute gelisteten Werten, ein Jahr ist wenig\n" +
            "• Ein Muster ist keine Empfehlung",
    )

    private val EN = mapOf(
        "ansicht" to "Price patterns",
        "muster.HOCH20" to "New 20-day high",
        "muster.FLAGGE" to "Bull flag",
        "muster.UEBER_SCHNITT20" to "Back above 20-day average",
        "muster.RSI_UEBERVERKAUFT" to "RSI oversold",
        "muster.RSI_UMKEHR" to "RSI reversal",
        "regel.HOCH20" to "Close above every close of the 20 trading days before",
        "regel.FLAGGE" to "More than 10 % up in 10 trading days, then 5 closes within a range of 5 % or less",
        "regel.UEBER_SCHNITT20" to "Close below the 20-day average the day before, above it now",
        "regel.RSI_UEBERVERKAUFT" to "14-day RSI below 30",
        "regel.RSI_UMKEHR" to "RSI below 30 the day before, close higher now",
        "zahl.HOCH20" to "{0} above old high",
        "zahl.FLAGGE" to "Rise {0}, range {1}",
        "zahl.UEBER_SCHNITT20" to "{0} above average",
        "zahl.RSI_UEBERVERKAUFT" to "RSI {0}",
        "zahl.RSI_UMKEHR" to "RSI {0}",
        "kursAb" to "Price from {0}",
        "kursJeder" to "Any price",
        "volumenAb" to "Volume from {0}",
        "volumenJedes" to "Any volume",
        "suchen" to "Search price patterns",
        "neu" to "Search again",
        "laedt" to "Loading {0} of {1}",
        "kern" to "{0} hits in {1} stocks",
        "ohneKurse" to "{0} without prices",
        "keine" to "No stock shows this pattern.",
        "ohneAntwort" to "No prices received.",
        "rueckblick" to "20 trading days later: {0} on average, all stocks {1} ({2} cases in the last year)",
        "rueck.titel" to "20 trading days later ({0} cases in the last year)",
        "rueck.muster" to "After the pattern",
        "rueck.alle" to "All stocks",
        "mehr" to "{0} more",
        "weniger" to "Less",
        "erklaerung" to "• Basis: daily closing prices of one year. During trading the latest price counts as the close\n" +
            "• Checked: the {0} largest stocks of the market by market value that pass the filter, plus portfolio and watchlist without filter\n" +
            "• Volume: average shares per day over three months\n" +
            "• Look-back: first day of a pattern in the last year, return over 20 trading days against all checked stocks on the same day\n" +
            "• The look-back is {hk}: without costs and dividends, only stocks listed today, one year is little\n" +
            "• A pattern is no recommendation",
    )
}
