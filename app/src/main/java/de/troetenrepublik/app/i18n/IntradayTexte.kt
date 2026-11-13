package de.troetenrepublik.app.i18n

import de.troetenrepublik.app.data.Lang

// Texte des Intraday-Rueckblicks, 0.78. Ein Schluessel je Text, Platzhalter {0}, {1} fuer Werte.
internal object IntradayTexte {

    fun text(deutsch: Boolean, key: String, w: List<String> = emptyList()): String {
        var t = (if (deutsch) DE else EN)[key] ?: return ""
        w.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
        return HinweisTexte.einsetzen(deutsch, t)
    }

    // Text in der Sprache der App
    fun t(key: String, vararg w: String): String = text(I18n.lang == Lang.DE, key, w.toList())

    // Alle Schluessel einer Sprache (der Test vergleicht beide Sprachen)
    fun schluessel(deutsch: Boolean): Set<String> = (if (deutsch) DE else EN).keys

    private val DE = mapOf(
        "titel" to "Intraday-Rückblick",
        "unter" to "Ausbruch am Morgen und Rest des Tages auf 5-Minuten-Kerzen",
        "zuwenig" to "Zu wenige 5-Minuten-Kerzen für diesen Wert.",
        "tage" to "{0} Handelstage bis {1}",
        // Teil 1
        "orb.titel" to "Ausbruch am Morgen",
        "strat.info" to "Die Regel kauft, wenn der Kurs am Morgen aus seiner Eröffnungsspanne ausbricht. Eröffnungsspanne ist der Bereich zwischen Hoch und Tief der ersten Minuten nach Börsenbeginn. Schließt eine 5-Minuten-Kerze darüber, wird gekauft (bei Short darunter, dann wird verkauft). Verkauft wird am Stopp, am Ziel oder kurz vor Handelsschluss. Gezeigt ist, was das früher gebracht hätte.",
        "orb.kern" to "{0} je Geschäft",
        "orb.keine" to "Kein Geschäft",
        "orb.satz" to "Nach Kosten, {0} Geschäfte in {1} Handelstagen",
        "orb.unsicher" to "Wenige Geschäfte, Ergebnis unsicher",
        "orb.balken" to "Ergebnis je Geschäft",
        "wahl.spanne" to "Eröffnungsspanne",
        "wahl.min" to "Erste {0} min",
        "wahl.stopp" to "Stopp",
        "stopp.MITTE" to "Mitte der Spanne",
        "stopp.GEGENSEITE" to "Tief der Spanne",
        "wahl.ziel" to "Ziel",
        "wahl.r" to "{0}× Risiko",
        "wahl.richtung" to "Richtung",
        "wahl.long" to "Long",
        "wahl.beide" to "Long und Short",
        "z.treffer" to "Trefferquote",
        "z.noetig" to "nötig {0} bei Ziel {1}× Risiko",
        "z.noetigKurz" to "Marke: nötige Trefferquote {0}",
        "z.enden" to "Ausgang der Geschäfte",
        "enden.ziel" to "Ziel",
        "enden.stopp" to "Stopp",
        "enden.schluss" to "Schluss",
        "r.titel" to "Ziel",
        "r.info" to "Das Risiko je Geschäft ist der Abstand zwischen Einstieg und Stopp. „2× Risiko“ heißt: Der angestrebte Gewinn ist doppelt so groß wie dieser Abstand.",
        "z.summe" to "Summe der Geschäfte",
        "z.morgens" to "Morgens kaufen, abends verkaufen",
        "z.halten" to "Halten über den Zeitraum",
        "z.einsatz" to "je 10.000 € Einsatz",
        "orb.note" to "{hist}\n" +
            "• Spanne: Hoch und Tief der ersten Minuten der Hauptsitzung am Hauptplatz\n" +
            "• Signal: erste abgeschlossene 5-Minuten-Kerze mit Schluss über dem Spannenhoch (Short: unter dem Spannentief), bis 2,5 Stunden vor Schluss, ein Geschäft je Tag\n" +
            "• Einstieg zur Eröffnung der nächsten Kerze, Ausstieg am Stopp, am Ziel oder 10 Minuten vor Schluss\n" +
            "• Trifft eine Kerze Stopp und Ziel, zählt der Stopp\n" +
            "• Stopp „Spannentief“ ist bei Short das Spannenhoch\n" +
            "• Stopp näher als 0,1 % am Einstieg: kein Geschäft\n" +
            "• Risiko: Abstand von Einstieg und Stopp\n" +
            "• Nötige Trefferquote: 1 ÷ (1 + Ziel als Vielfaches des Risikos)\n" +
            "• Kosten je Kauf und je Verkauf: {0} Spread (aus den Einstellungen) und 1 € Ordergebühr bei 10.000 € Einsatz\n" +
            "• Aussagekraft wie im Strategietest: unter 10 Geschäften oder 2 Jahren zu gering, unter 30 Geschäften oder 5 Jahren begrenzt\n" +
            "• Kurse: 5-Minuten-Kerzen der letzten rund 60 Tage, verkürzte Handelstage enden mit ihrer letzten Kerze",
        // Teil 2
        "rest.titel" to "Rest des Tages",
        "rest.kern" to "Ziel in {0} der Fälle",
        "rest.satz" to "Nötig {0} bei Ziel {1}× Risiko. Im Mittel {2} nach Kosten.",
        "rest.kennung" to "Wiederholung vergangener Kerzen, keine Prognose",
        "rest.ziel" to "Ziel erreicht",
        "rest.stopp" to "Stopp erreicht",
        "rest.offen" to "Offen bis Schluss",
        "rest.mittel" to "Mittleres Ergebnis nach Kosten",
        "rest.keine" to "Zu dieser Uhrzeit bleibt zu wenig Handelszeit.",
        "wahl.einstieg" to "Einstieg",
        "wahl.jetzt" to "Jetzt",
        "graf.ziel" to "Ziel",
        "graf.stopp" to "Stopp",
        "rest.note" to "• {0} Wiederholungen des restlichen Handelstags aus {1} Handelstagen\n" +
            "• Jeder 5-Minuten-Schritt zieht eine vergangene Kerze derselben Tageszeit (plus/minus zwei Kerzen)\n" +
            "• Kauf zur gewählten Uhrzeit, Stopp vor Ziel geprüft, sonst Verkauf 10 Minuten vor Schluss\n" +
            "• Fächer: mittlerer Pfad, darum die Hälfte und 90 % der Pfade\n" +
            "• Fester Startwert des Zufalls: gleiche Eingaben geben gleiche Zahlen\n" +
            "• Zusammenhänge zwischen aufeinanderfolgenden Kerzen gehen verloren",
    )

    private val EN = mapOf(
        "titel" to "Intraday review",
        "unter" to "Morning breakout and rest of the day on 5-minute candles",
        "zuwenig" to "Too few 5-minute candles for this security.",
        "tage" to "{0} trading days to {1}",
        "orb.titel" to "Morning breakout",
        "strat.info" to "The rule buys when the price breaks out of its opening range in the morning. The opening range is the band between the high and low of the first minutes after the market opens. If a 5-minute candle closes above it, the rule buys (for short, below it, and sells). It exits at the stop, at the target or shortly before the close. This shows what that would have earned in the past.",
        "orb.kern" to "{0} per trade",
        "orb.keine" to "No trade",
        "orb.satz" to "After costs, {0} trades in {1} trading days",
        "orb.unsicher" to "Few trades, result uncertain",
        "orb.balken" to "Result per trade",
        "wahl.spanne" to "Opening range",
        "wahl.min" to "First {0} min",
        "wahl.stopp" to "Stop",
        "stopp.MITTE" to "Middle of the range",
        "stopp.GEGENSEITE" to "Low of the range",
        "wahl.ziel" to "Target",
        "wahl.r" to "{0}× risk",
        "wahl.richtung" to "Direction",
        "wahl.long" to "Long",
        "wahl.beide" to "Long and short",
        "z.treffer" to "Win rate",
        "z.noetig" to "required {0} at target {1}× risk",
        "z.noetigKurz" to "Mark: required win rate {0}",
        "z.enden" to "How trades ended",
        "enden.ziel" to "Target",
        "enden.stopp" to "Stop",
        "enden.schluss" to "Close",
        "r.titel" to "Target",
        "r.info" to "The risk per trade is the distance between entry and stop. “2× risk” means the intended gain is twice that distance.",
        "z.summe" to "Sum of trades",
        "z.morgens" to "Buy in the morning, sell in the evening",
        "z.halten" to "Hold over the period",
        "z.einsatz" to "per €10,000 stake",
        "orb.note" to "{hist}\n" +
            "• Range: high and low of the first minutes of the main session on the home exchange\n" +
            "• Signal: first completed 5-minute candle closing above the range high (short: below the range low), up to 2.5 hours before the close, one trade per day\n" +
            "• Entry at the open of the next candle, exit at the stop, at the target or 10 minutes before the close\n" +
            "• If a candle hits stop and target, the stop counts\n" +
            "• The “range low” stop is the range high for shorts\n" +
            "• Stop closer than 0.1% to the entry: no trade\n" +
            "• Risk: distance between entry and stop\n" +
            "• Required win rate: 1 ÷ (1 + target as a multiple of the risk)\n" +
            "• Costs per buy and per sell: {0} spread (from the settings) and a €1 order fee on a €10,000 stake\n" +
            "• Reliability as in the strategy test: too low under 10 trades or 2 years, limited under 30 trades or 5 years\n" +
            "• Prices: 5-minute candles of roughly the last 60 days, shortened trading days end with their last candle",
        "rest.titel" to "Rest of the day",
        "rest.kern" to "Target in {0} of cases",
        "rest.satz" to "Required {0} at target {1}× risk. On average {2} after costs.",
        "rest.kennung" to "Replay of past candles, no forecast",
        "rest.ziel" to "Target reached",
        "rest.stopp" to "Stop reached",
        "rest.offen" to "Open until the close",
        "rest.mittel" to "Average result after costs",
        "rest.keine" to "Too little trading time is left at this time of day.",
        "wahl.einstieg" to "Entry",
        "wahl.jetzt" to "Now",
        "graf.ziel" to "Target",
        "graf.stopp" to "Stop",
        "rest.note" to "• {0} replays of the rest of the trading day from {1} trading days\n" +
            "• Each 5-minute step draws a past candle from the same time of day (plus or minus two candles)\n" +
            "• Buy at the chosen time, stop checked before target, otherwise sell 10 minutes before the close\n" +
            "• Fan: median path, around it half and 90% of the paths\n" +
            "• Fixed random seed: the same inputs give the same figures\n" +
            "• Links between consecutive candles are lost",
    )
}
