package de.troetenrepublik.app.i18n

// Texte zu Signalwert und Ausweichwert im Strategietest und zur Momentum-Rotation, 0.78. Ein Schluessel je Text,
// Platzhalter {0}, {1} ... fuer Werte.
internal object TaktikTexte {

    fun text(deutsch: Boolean, key: String, w: List<String>): String {
        var t = (if (deutsch) DE else EN)[key] ?: return ""
        w.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
        return HinweisTexte.einsetzen(deutsch, t)
    }

    // Alle Schluessel einer Sprache (der Test vergleicht beide Sprachen)
    fun schluessel(deutsch: Boolean): Set<String> = (if (deutsch) DE else EN).keys

    private val DE = mapOf(
        // Strategietest
        "tk.titel" to "Signalwert und Ausweichwert",
        "tk.kurz" to "Signal: {0} · Signal aus: {1}",
        "tk.signal" to "Signal von",
        "tk.gleich" to "Gleicher Wert",
        "tk.ausweich" to "Bei Signal aus",
        "tk.bargeld" to "Bargeld",
        "tk.info" to "• Signal von: Die Regel rechnet auf den Kursen dieses Werts, gekauft und verkauft wird der gezeigte Wert. Beispiel: Signal vom Index, Handel im gehebelten ETF.\n" +
            "• Bei Signal aus: Das Geld liegt in diesem Wert statt in Bargeld. Der Wechsel geschieht zur selben Eröffnung und kostet Spread und Ordergebühr.\n" +
            "• Gerechnet wird an den Tagen, an denen alle gewählten Werte Kurse haben. Geschäfte, Trefferquote und Zeit im Markt zählen den gehandelten Wert.",
        "tk.andere" to "Nur ohne Signalwert und Ausweichwert.",
        "tk.zuwenig" to "Zu wenige gemeinsame Tageskurse mit {0}.",
        "wahl.suche" to "Wert suchen",
        "wahl.vorschlaege" to "Vorschläge",
        // Rotation
        "rot.name" to "Rotation",
        "rot.kern.titel" to "Rotation gegen Kaufen und Halten",
        "rot.kern.zahl" to "{0} pro Jahr",
        "rot.kern.satz" to "{0} bis {1}, {hk}, nach Kosten.",
        "rot.werte" to "Werte",
        "rot.werte.kurz" to "{0} Werte",
        "rot.hinzu" to "Wert hinzufügen",
        "rot.depot" to "Depot und Watchlist",
        "rot.etfs" to "ETF-Auswahl",
        "rot.entfernen" to "{0} entfernen",
        "rot.regel" to "Regel",
        "rot.regel.kurz" to "{0} · beste {1} · {2}",
        "rot.monate" to "{0} Monate",
        "rot.monat1" to "1 Monat",
        "rot.m" to "{0}M",
        "rot.GLEICH" to "gleich gewichtet",
        "rot.SCHWANKUNG" to "invers zur Schwankung",
        "rot.rueckblick" to "Rückblick",
        "rot.plaetze" to "Werte im Depot",
        "rot.gewichtung" to "Gewichtung",
        "rot.absolut" to "Absolutes Momentum",
        "rot.ausweich" to "Ausweichwert",
        "rot.heute" to "Auswahl seit {0}",
        "rot.rang" to "Rangliste",
        "rot.rang.info" to "Rendite im Rückblick am jüngsten Monatsende, beste zuerst. Die obersten Werte kommen ins Depot.",
        "rot.andere" to "Andere Rückblicke",
        "rot.andere.info" to "Liegt nur der gewählte Rückblick vorn, ist das vermutlich Zufall.",
        "rot.index" to "Vergleichsindex",
        "rot.zuwenig" to "Zu wenige gemeinsame Tageskurse für eine Rotation.",
        "rot.zuwenigWerte" to "Eine Rotation braucht mindestens zwei Werte mit Kursen.",
        "rot.ohne" to "Ohne Kurse: {0}",
        "rot.start" to "• Gemeinsame Kurse ab {0}, kürzeste Reihe: {1}.",
        "rot.methode" to "{hist}\n" +
            "• Am letzten Handelstag jedes Monats zählt die Rendite im Rückblick: Die besten Werte bleiben im Depot, gehandelt wird zur Eröffnung des nächsten Handelstags.\n" +
            "• Absolutes Momentum: Ein Wert bleibt nur, wenn seine Rendite über der des Ausweichwerts liegt, ohne Ausweichwert über null. Freie Plätze gehen in den Ausweichwert, sonst in Bargeld.\n" +
            "• Invers zur Schwankung: Ruhigere Werte bekommen mehr Gewicht (Tagesschwankung der letzten 63 Handelstage).\n" +
            "• Start mit 10.000 €, {0} Spread je Kauf und je Verkauf, 1 € je Order. Umschichtungen unter 1 % des Depotwerts entfallen.\n" +
            "• Kaufen und Halten kauft alle Werte am ersten Tag zu gleichen Teilen.\n" +
            "• Tageskurse in Euro, Ausschüttungen wieder angelegt (soweit abrufbar), ohne Steuern. Ein Geschäft läuft vom ersten Kauf bis zum vollständigen Verkauf eines Werts.",
        // Vergleichstabelle, Aussagekraft und Abschnittszeilen (Strategietest und Rotation)
        "vt.info.titel" to "Lernzeit und Prüfzeit",
        "vt.lern" to "Rendite p. a. Lernzeit",
        "vt.lern.hint" to "bis {0}",
        "vt.pruef" to "Rendite p. a. Prüfzeit",
        "vt.pruef.hint" to "ab {0}",
        "vt.aussage.gesamt" to "Aussagekraft gesamt",
        "vt.fakten" to "{0} Geschäfte in {1} Jahren",
        "vt.treffer" to "Anteil der Geschäfte mit Gewinn nach Kosten.",
        "vt.kz.kurz" to "Trefferquote {0}, Kaufen und Halten {1}",
        "vt.rang.kurz" to "Vorn: {0} {1}",
        "vt.andere.kurz" to "Dieselbe Rotation mit jedem Rückblick",
        "vt.beste" to "Beste Variante",
        "vt.gesamt" to "p. a. gesamt",
        "vt.pruefzeit" to "p. a. Prüfzeit",
        "pa.anderewerte" to "Dieselbe Regel auf Depot und Watchlist",
        "pa.lotterie" to "Beste von vielen Varianten, geprüft",
        "pa.zufall" to "Regel gegen zufällig verschobene Tage",
        "pa.startfenster" to "Viele Startzeitpunkte im Vergleich",
        "pa.parameter" to "Alle Varianten einer Regel im Überblick",
        "pa.wf" to "Das Fenster wandert durch die Geschichte",
    )

    private val EN = mapOf(
        "tk.titel" to "Signal security and fallback",
        "tk.kurz" to "Signal: {0} · signal off: {1}",
        "tk.signal" to "Signal from",
        "tk.gleich" to "Same security",
        "tk.ausweich" to "When the signal is off",
        "tk.bargeld" to "Cash",
        "tk.info" to "• Signal from: the rule runs on the prices of this security, the security shown is bought and sold. Example: signal from the index, trading in the leveraged ETF.\n" +
            "• When the signal is off: the money sits in this security instead of cash. The switch happens at the same open and costs spread and order fee.\n" +
            "• Only days on which all chosen securities have prices count. Trades, hit rate and time in the market refer to the traded security.",
        "tk.andere" to "Only without signal security and fallback.",
        "tk.zuwenig" to "Too few shared daily prices with {0}.",
        "wahl.suche" to "Search security",
        "wahl.vorschlaege" to "Suggestions",
        "rot.name" to "Rotation",
        "rot.kern.titel" to "Rotation against buy and hold",
        "rot.kern.zahl" to "{0} per year",
        "rot.kern.satz" to "{0} to {1}, {hk}, after costs.",
        "rot.werte" to "Securities",
        "rot.werte.kurz" to "{0} securities",
        "rot.hinzu" to "Add security",
        "rot.depot" to "Portfolio and watchlist",
        "rot.etfs" to "ETF selection",
        "rot.entfernen" to "Remove {0}",
        "rot.regel" to "Rule",
        "rot.regel.kurz" to "{0} · top {1} · {2}",
        "rot.monate" to "{0} months",
        "rot.monat1" to "1 month",
        "rot.m" to "{0}M",
        "rot.GLEICH" to "equally weighted",
        "rot.SCHWANKUNG" to "inverse to volatility",
        "rot.rueckblick" to "Lookback",
        "rot.plaetze" to "Securities held",
        "rot.gewichtung" to "Weighting",
        "rot.absolut" to "Absolute momentum",
        "rot.ausweich" to "Fallback",
        "rot.heute" to "Selection since {0}",
        "rot.rang" to "Ranking",
        "rot.rang.info" to "Return over the lookback at the latest month end, best first. The top securities are held.",
        "rot.andere" to "Other lookbacks",
        "rot.andere.info" to "If only the chosen lookback is ahead, that is probably chance.",
        "rot.index" to "Benchmark index",
        "rot.zuwenig" to "Too few shared daily prices for a rotation.",
        "rot.zuwenigWerte" to "A rotation needs at least two securities with prices.",
        "rot.ohne" to "Without prices: {0}",
        "rot.start" to "• Shared prices from {0}, shortest series: {1}.",
        "rot.methode" to "{hist}\n" +
            "• On the last trading day of each month the return over the lookback counts: the best securities stay in the portfolio, trades happen at the open of the next trading day.\n" +
            "• Absolute momentum: a security only stays if its return is above that of the fallback, without a fallback above zero. Free slots go to the fallback, otherwise to cash.\n" +
            "• Inverse to volatility: calmer securities get more weight (daily volatility of the last 63 trading days).\n" +
            "• Start with €10,000, {0} spread per buy and per sale, €1 per order. Shifts below 1% of the portfolio value are skipped.\n" +
            "• Buy and hold buys all securities in equal parts on the first day.\n" +
            "• Daily prices in euros, distributions reinvested where available, without taxes. A trade runs from the first buy to the complete sale of a security.",
        "vt.info.titel" to "Learning and test period",
        "vt.lern" to "Return p. a. learning",
        "vt.lern.hint" to "until {0}",
        "vt.pruef" to "Return p. a. test",
        "vt.pruef.hint" to "from {0}",
        "vt.aussage.gesamt" to "Reliability overall",
        "vt.fakten" to "{0} trades in {1} years",
        "vt.treffer" to "Share of trades with a profit after costs.",
        "vt.kz.kurz" to "Hit rate {0}, buy and hold {1}",
        "vt.rang.kurz" to "Ahead: {0} {1}",
        "vt.andere.kurz" to "Same rotation with each lookback",
        "vt.beste" to "Best variant",
        "vt.gesamt" to "p. a. overall",
        "vt.pruefzeit" to "p. a. test",
        "pa.anderewerte" to "Same rule on portfolio and watchlist",
        "pa.lotterie" to "Best of many variants, tested",
        "pa.zufall" to "Rule against randomly shifted days",
        "pa.startfenster" to "Many start dates compared",
        "pa.parameter" to "All variants of a rule at a glance",
        "pa.wf" to "The window moves through history",
    )
}
