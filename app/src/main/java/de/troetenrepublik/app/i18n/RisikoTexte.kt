package de.troetenrepublik.app.i18n

// Texte der Risikoregeln, der Positionsgroesse mit Grenzen und des Verhaeltnisses von Chance zu Risiko, 0.78.
// Ein Schluessel je Text, Platzhalter {0}, {1} ... fuer Werte.
internal object RisikoTexte {

    fun text(deutsch: Boolean, key: String, w: List<String>): String {
        var t = (if (deutsch) DE else EN)[key] ?: return ""
        w.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
        return t
    }

    // Alle Schluessel einer Sprache (der Test vergleicht beide Sprachen)
    fun schluessel(deutsch: Boolean): Set<String> = (if (deutsch) DE else EN).keys

    private val DE = mapOf(
        "regel.VERLUST_GESCHAEFT" to "Höchstverlust je Geschäft",
        "regel.VERLUST_TAG" to "Höchstverlust je Tag",
        "regel.GESCHAEFTE_TAG" to "Neue Geschäfte je Tag höchstens",
        "regel.CHANCE_RISIKO" to "Chance zu Risiko mindestens",
        "verstoss.VERLUST_GESCHAEFT" to "Verlust bis zum Stopp {0} des Depots, eigene Grenze {1}",
        "verstoss.VERLUST_TAG" to "Verlust heute {0} des Depots, eigene Grenze {1}",
        "verstoss.GESCHAEFTE_TAG" to "{0}. Geschäft heute, eigene Grenze {1}",
        "verstoss.CHANCE_RISIKO" to "Chance zu Risiko {0}, eigene Untergrenze {1}",
        "heute" to "Heute",
        "heuteWert" to "Geschäfte {0} · Verlust {1}",
        "crv" to "Chance zu Risiko",
        "quote" to "Trefferquote ab {0}",
        "crvZeile" to "Chance zu Risiko {0} · Trefferquote ab {1}",
        "quoteHinweis" to "Trefferquote, ab der sich Gewinne und Verluste ausgleichen: 1 ÷ (1 + Chance zu Risiko), ohne Kosten.",
        "zielDanach" to "Ziel nach dem Kauf",
        "anteil" to "Höchster Anteil am Depot",
        "ziel" to "Ziel",
        "zielMal" to "{0} × Risiko",
        "grenze.RISIKO" to "Begrenzt durch Höchstverlust",
        "grenze.ANTEIL" to "Begrenzt durch Anteil am Depot",
        "grenze.BARGELD" to "Begrenzt durch Guthaben",
        "grenze.PUFFER" to "Begrenzt durch Guthaben nach Puffer {0}",
        "zuViel.ANTEIL" to "Der höchste Anteil am Depot ist erreicht.",
        "zuViel.BARGELD" to "Das Guthaben reicht nicht für ein Stück.",
    )

    private val EN = mapOf(
        "regel.VERLUST_GESCHAEFT" to "Maximum loss per trade",
        "regel.VERLUST_TAG" to "Maximum loss per day",
        "regel.GESCHAEFTE_TAG" to "New trades per day at most",
        "regel.CHANCE_RISIKO" to "Reward to risk at least",
        "verstoss.VERLUST_GESCHAEFT" to "Loss down to the stop {0} of the portfolio, own limit {1}",
        "verstoss.VERLUST_TAG" to "Loss today {0} of the portfolio, own limit {1}",
        "verstoss.GESCHAEFTE_TAG" to "Trade no. {0} today, own limit {1}",
        "verstoss.CHANCE_RISIKO" to "Reward to risk {0}, own minimum {1}",
        "heute" to "Today",
        "heuteWert" to "Trades {0} · loss {1}",
        "crv" to "Reward to risk",
        "quote" to "Hit rate from {0}",
        "crvZeile" to "Reward to risk {0} · hit rate from {1}",
        "quoteHinweis" to "Hit rate at which gains and losses balance: 1 ÷ (1 + reward to risk), without costs.",
        "zielDanach" to "Target after the buy",
        "anteil" to "Largest share of the portfolio",
        "ziel" to "Target",
        "zielMal" to "{0} × risk",
        "grenze.RISIKO" to "Limited by maximum loss",
        "grenze.ANTEIL" to "Limited by share of the portfolio",
        "grenze.BARGELD" to "Limited by cash",
        "grenze.PUFFER" to "Limited by cash after buffer {0}",
        "zuViel.ANTEIL" to "The largest share of the portfolio is reached.",
        "zuViel.BARGELD" to "The cash is not enough for one unit.",
    )
}
