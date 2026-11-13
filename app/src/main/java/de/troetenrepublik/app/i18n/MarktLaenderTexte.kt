package de.troetenrepublik.app.i18n

// Texte zu Phasenzaehler (pz), Kurs und Umsatz (ku), 16-Wochen-Rhythmus (rh), Schuldentragfaehigkeit im Laenderblatt
// (sl) und Verweisen zwischen Maerkte, Globus und Laenderblatt (vw), 0.78. Ein Schluessel je Text, Platzhalter {0},
// {1} ... fuer Werte.
internal object MarktLaenderTexte {

    fun text(deutsch: Boolean, key: String, w: List<String>): String {
        var t = (if (deutsch) DE else EN)[key] ?: return ""
        w.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
        return t
    }

    // Alle Schluessel einer Sprache (der Test vergleicht beide Sprachen)
    fun schluessel(deutsch: Boolean): Set<String> = (if (deutsch) DE else EN).keys

    private val DE = mapOf(
        "pz.titel" to "Phasenzähler (USA)", "pz.zahl" to "{0} von {1} positiv",
        "pz.lage.VOLL" to "voll investiert", "pz.lage.TEILWEISE" to "teilweise investiert", "pz.lage.ABWARTEND" to "abwartend",
        "pz.lage.NICHT" to "nicht investiert",
        "pz.RELATIVZINS.1" to "Relativzins gestiegen", "pz.RELATIVZINS.0" to "Relativzins gesunken",
        "pz.ZINSSTRUKTUR.1" to "Zinsstruktur 10 J − 1 J normal", "pz.ZINSSTRUKTUR.0" to "Zinsstruktur 10 J − 1 J invers",
        "pz.RENDITE.1" to "Anleiherendite gesunken", "pz.RENDITE.0" to "Anleiherendite gestiegen",
        "pz.DOLLAR.1" to "Dollar stärker zum Euro", "pz.DOLLAR.0" to "Dollar schwächer zum Euro",
        "pz.ROHSTOFFE.1" to "Rohstoffe billiger", "pz.ROHSTOFFE.0" to "Rohstoffe teurer",
        "pz.OEL.1" to "Öl billiger", "pz.OEL.0" to "Öl teurer",
        "pz.SAISON.1" to "Saison günstig", "pz.SAISON.0" to "Saison ungünstig",
        "pz.vorjahr" to "Vorjahr {0}, Stand {1}", "pz.jahr" to "zum Vorjahr, Stand {0}", "pz.stand" to "Stand {0}",
        "pz.saison" to "günstig von November bis April",
        "pz.regel" to "Über zwei Drittel positiv: voll investiert. Ab der Hälfte: teilweise. Über zwei Drittel negativ: nicht investiert.\n" +
            "Relativzins: Rendite zehnjähriger US-Anleihen minus Jahresänderung der Rohstoffe. Zinsstruktur: zehn Jahre minus ein Jahr.\n" +
            "Rohstoffe: Mittel aus Öl, Gas, Gold, Kupfer, Weizen, Mais und Zucker.\nZählung nach festen Regeln, keine Empfehlung.",

        "ku.titel" to "Kurs und Umsatz",
        "ku.info" to "Regel nach Kostolany: Richtung des Index über drei Monate gegen den Börsenumsatz.\n\n" +
            "• Kurs fällt, Umsatz hoch: Kaufzone. Viele verkaufen in Eile.\n" +
            "• Kurs steigt, Umsatz niedrig: früher Aufschwung. Wenige sind dabei.\n" +
            "• Kurs steigt seit über einem Jahr, Umsatz hoch: Verkaufszone. Fast alle sind dabei.\n" +
            "• Kurs fällt, Umsatz niedrig: früher Abschwung.\n\n" +
            "Kurs: ab 2 % Änderung in drei Monaten. Umsatz: Mittel der letzten drei Monate, ab 10 % Abstand zum Mittel des Jahres davor.",
        "ku.zone.KAUFZONE" to "Kaufzone", "ku.zone.FRUEHER_AUFSCHWUNG" to "früher Aufschwung", "ku.zone.VERKAUFSZONE" to "Verkaufszone",
        "ku.zone.FRUEHER_ABSCHWUNG" to "früher Abschwung", "ku.zone.KEINE" to "keine Zone",
        "ku.kurs" to "Kurs in 3 Monaten", "ku.kursLang" to "Kurs in 12 Monaten", "ku.umsatz" to "Umsatz zum Vorjahresmittel", "ku.stand" to "Stand",

        "rh.titel" to "16-Wochen-Rhythmus",
        "rk.heute" to "Woche des Zyklus heute", "rk.beginn" to "Wochen 13 bis 15 ab", "rk.ende" to "Wochen 13 bis 15 bis",
        "rk.rang" to "Rang der Wochen 13 bis 15", "rk.rang.frueh" to "Rang in der ersten Hälfte", "rk.rang.spaet" to "Rang in der zweiten Hälfte",
        "rk.von" to "{0} von 16",
        "rk.regel" to "Zyklus gezählt ab Montag, 26.10.1987 (Woche 1). Rang unter allen 16 Fenstern aus drei Wochen des Zyklus, 1 ist das stärkste, ohne Kosten gerechnet. Ein Rang weit vorn nur in einer Hälfte spricht für Zufall.",
        "rh.strategie" to "Nur Wochen 13 bis 15, pro Jahr", "rh.halten" to "Kaufen und Halten, pro Jahr",
        "rh.besser" to "Ohne Kosten: Nur in den Wochen 13 bis 15 investiert brachte mehr als Kaufen und Halten.",
        "rh.schlechter" to "Ohne Kosten: Nur in den Wochen 13 bis 15 investiert brachte weniger als Kaufen und Halten.",
        "rh.zeit" to "Zeit im Markt", "rh.plus" to "Wochen 13 bis 15 im Plus",
        "rh.beste" to "Stärkste Woche ({0})", "rh.schwaechste" to "Schwächste Woche ({0})",
        "rh.achse" to "Gesamte Kursänderung je Woche des Zyklus",
        "rh.regel" to "DAX, {0} bis {1}. Zyklus aus 16 Wochen, gezählt ab Montag, 26.10.1987 (Woche 1). Ohne Kosten und ohne Zinsen in der Wartezeit.",

        "sl.titel" to "Schuldentragfähigkeit",
        "sl.SCHULDEN_EINNAHMEN" to "Schulden zu Einnahmen", "sl.ZINS_EINNAHMEN" to "Zinsen zu Einnahmen",
        "sl.ZINS_WACHSTUM" to "Zins minus Wachstum", "sl.RESERVEN" to "Reserven zu Schulden",
        "sl.KURZFRISTIG" to "Kurzfristige Auslandsschulden zu Reserven",
        "sl.info.SCHULDEN_EINNAHMEN" to "Staatsschulden {0} des BIP (Gesamtstaat) gegen Einnahmen des Zentralstaats {1} des BIP ({2}).\n\n" +
            "In Bundesstaaten fällt der Wert höher aus, weil Länder und Gemeinden eigene Einnahmen haben.",
        "sl.info.ZINS_EINNAHMEN" to "Anteil der Einnahmen des Zentralstaats, der für Zinsen ausgegeben wird ({2}).",
        "sl.info.ZINS_WACHSTUM" to "Zins auf die Schulden {0} (Zinsausgaben durch Schulden), nominales Wachstum {1} ({2}).\n\n" +
            "Liegt der Zins über dem Wachstum, steigt die Schuldenquote, solange der Haushalt vor Zinsen keinen Überschuss hat.",
        "sl.info.RESERVEN" to "Währungsreserven mit Gold {0} des BIP gegen Staatsschulden {1} des BIP ({2}).\n\n" +
            "Reserven zählen vor allem bei Schulden in fremder Währung.",
        "sl.info.KURZFRISTIG" to "Auslandsschulden, die binnen eines Jahres fällig werden, in Prozent der Währungsreserven ({2}).\n\n" +
            "Über 100 % decken die Reserven sie nicht (Regel von Guidotti und Greenspan).",
        "sl.ampel.drittel" to "Ampel: Drittel im Vergleich aller Länder mit Wert.",
        "sl.hinweis" to "Einnahmen und Zinsen: Zentralstaat.", "sl.stand" to "Stand {0}",

        "vw.eng.keiner" to "Keiner gestört",
        "vw.vix.rang" to "Rang {0} von 100 unter den Schlusskursen des letzten Jahres. 100: so ruhig wie nie. 0: so unruhig wie nie.",
        "vw.rh.kosten" to "DAX, mit Spread und Gebühr",
        "vw.zu" to "{0} zu {1}", "vw.zu.jahr" to "{0} zu {1}, 1 Jahr", "vw.kurs" to "1 {0} = {1} {2}",
        "vw.info.kurs" to "Wechselkurs der Landeswährung in Einheiten je {0}.",
        "vw.info.jahr" to "Wertveränderung der Landeswährung gegenüber {0} in den letzten zwölf Monaten.\n\n" +
            "• Positiv heißt: Die Währung hat zu {0} zugelegt.\n• Eine schwache Währung verteuert Importe und Auslandsschulden und hilft Exporteuren.",
    )

    private val EN = mapOf(
        "pz.titel" to "Phase counter (US)", "pz.zahl" to "{0} of {1} positive",
        "pz.lage.VOLL" to "fully invested", "pz.lage.TEILWEISE" to "partly invested", "pz.lage.ABWARTEND" to "on hold",
        "pz.lage.NICHT" to "not invested",
        "pz.RELATIVZINS.1" to "Relative rate up", "pz.RELATIVZINS.0" to "Relative rate down",
        "pz.ZINSSTRUKTUR.1" to "Yield curve 10y − 1y normal", "pz.ZINSSTRUKTUR.0" to "Yield curve 10y − 1y inverted",
        "pz.RENDITE.1" to "Bond yield down", "pz.RENDITE.0" to "Bond yield up",
        "pz.DOLLAR.1" to "Dollar stronger against the euro", "pz.DOLLAR.0" to "Dollar weaker against the euro",
        "pz.ROHSTOFFE.1" to "Commodities cheaper", "pz.ROHSTOFFE.0" to "Commodities dearer",
        "pz.OEL.1" to "Oil cheaper", "pz.OEL.0" to "Oil dearer",
        "pz.SAISON.1" to "Season favorable", "pz.SAISON.0" to "Season unfavorable",
        "pz.vorjahr" to "a year ago {0}, as of {1}", "pz.jahr" to "year on year, as of {0}", "pz.stand" to "as of {0}",
        "pz.saison" to "favorable from November to April",
        "pz.regel" to "More than two thirds positive: fully invested. Half or more: partly. More than two thirds negative: not invested.\n" +
            "Relative rate: ten-year US Treasury yield minus the yearly change in commodities. Yield curve: ten years minus one year.\n" +
            "Commodities: mean of oil, gas, gold, copper, wheat, corn and sugar.\nA count by fixed rules, not a recommendation.",

        "ku.titel" to "Price and volume",
        "ku.info" to "Kostolany's rule: direction of the index over three months against trading volume.\n\n" +
            "• Price falls, volume high: buy zone. Many sell in a hurry.\n" +
            "• Price rises, volume low: early upswing. Few take part.\n" +
            "• Price has risen for over a year, volume high: sell zone. Almost everyone takes part.\n" +
            "• Price falls, volume low: early downswing.\n\n" +
            "Price: a change of 2% or more in three months. Volume: mean of the last three months, 10% or more away from the mean of the year before.",
        "ku.zone.KAUFZONE" to "buy zone", "ku.zone.FRUEHER_AUFSCHWUNG" to "early upswing", "ku.zone.VERKAUFSZONE" to "sell zone",
        "ku.zone.FRUEHER_ABSCHWUNG" to "early downswing", "ku.zone.KEINE" to "no zone",
        "ku.kurs" to "Price in 3 months", "ku.kursLang" to "Price in 12 months", "ku.umsatz" to "Volume vs prior-year mean", "ku.stand" to "As of",

        "rh.titel" to "16-week rhythm",
        "rk.heute" to "Week of the cycle today", "rk.beginn" to "Weeks 13 to 15 from", "rk.ende" to "Weeks 13 to 15 until",
        "rk.rang" to "Rank of weeks 13 to 15", "rk.rang.frueh" to "Rank in the first half", "rk.rang.spaet" to "Rank in the second half",
        "rk.von" to "{0} of 16",
        "rk.regel" to "Cycle counted from Monday, 26 Oct 1987 (week 1). Rank among all 16 windows of three weeks in the cycle, 1 is the strongest, calculated without costs. A top rank in only one half points to chance.",
        "rh.besser" to "Without costs: being invested only in weeks 13 to 15 returned more than buy and hold.",
        "rh.schlechter" to "Without costs: being invested only in weeks 13 to 15 returned less than buy and hold.",
        "rh.strategie" to "Weeks 13 to 15 only, per year", "rh.halten" to "Buy and hold, per year",
        "rh.zeit" to "Time in the market", "rh.plus" to "Weeks 13 to 15 with a gain",
        "rh.beste" to "Strongest week ({0})", "rh.schwaechste" to "Weakest week ({0})",
        "rh.achse" to "Total price change by week of the cycle",
        "rh.regel" to "DAX, {0} to {1}. Cycle of 16 weeks, counted from Monday, 26 Oct 1987 (week 1). Without costs and without interest while waiting.",

        "sl.titel" to "Debt sustainability",
        "sl.SCHULDEN_EINNAHMEN" to "Debt to revenue", "sl.ZINS_EINNAHMEN" to "Interest to revenue",
        "sl.ZINS_WACHSTUM" to "Interest rate minus growth", "sl.RESERVEN" to "Reserves to debt",
        "sl.KURZFRISTIG" to "Short-term external debt to reserves",
        "sl.info.SCHULDEN_EINNAHMEN" to "Government debt of {0} of GDP (general government) against central government revenue of {1} of GDP ({2}).\n\n" +
            "In federal states the value comes out higher, because states and municipalities have revenue of their own.",
        "sl.info.ZINS_EINNAHMEN" to "Share of central government revenue spent on interest ({2}).",
        "sl.info.ZINS_WACHSTUM" to "Interest rate on the debt {0} (interest payments divided by debt), nominal growth {1} ({2}).\n\n" +
            "If the rate is above growth, the debt ratio rises as long as the budget before interest shows no surplus.",
        "sl.info.RESERVEN" to "Reserves including gold of {0} of GDP against government debt of {1} of GDP ({2}).\n\n" +
            "Reserves matter most for debt in foreign currency.",
        "sl.info.KURZFRISTIG" to "External debt due within one year, in percent of reserves ({2}).\n\n" +
            "Above 100% the reserves do not cover it (Guidotti-Greenspan rule).",
        "sl.ampel.drittel" to "Traffic light: third within all countries with a value.",
        "sl.hinweis" to "Revenue and interest: central government.", "sl.stand" to "As of {0}",

        "vw.eng.keiner" to "None disrupted",
        "vw.vix.rang" to "Rank {0} of 100 among the closing prices of the last year. 100: calmer than on any other day. 0: more turbulent than on any other day.",
        "vw.rh.kosten" to "DAX, with spread and fee",
        "vw.zu" to "{0} vs. {1}", "vw.zu.jahr" to "{0} against {1}, 1 year", "vw.kurs" to "1 {0} = {1} {2}",
        "vw.info.kurs" to "Exchange rate of the national currency in units per {0}.",
        "vw.info.jahr" to "Change in value of the national currency against {0} over the last twelve months.\n\n" +
            "• Positive means the currency has gained against {0}.\n• A weak currency makes imports and foreign debt dearer and helps exporters.",
    )
}
