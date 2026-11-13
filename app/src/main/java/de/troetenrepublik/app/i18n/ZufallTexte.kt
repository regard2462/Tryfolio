package de.troetenrepublik.app.i18n

// Texte zu Zufallspfaden, vielen Startzeitpunkten und dem Sparplan im Strategietest, 0.78. Abruf ueber S.st wie die
// Schluessel aus StrategieTexte; Platzhalter {0}, {1} ... fuer Werte.
internal object ZufallTexte {

    fun text(deutsch: Boolean, key: String, w: List<String>): String {
        var t = (if (deutsch) DE else EN)[key] ?: return ""
        w.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
        return HinweisTexte.einsetzen(deutsch, t)
    }

    fun schluessel(deutsch: Boolean): Set<String> = (if (deutsch) DE else EN).keys

    private val DE = mapOf(
        // Zufallspfade
        "zp.titel" to "Zufallspfade",
        "zp.verschoben" to "Echte Kurse, verschobene Signale",
        "zp.EINFACH" to "Einfache Ziehung",
        "zp.BLOCK" to "Block-Bootstrap",
        "zp.UMKEHR" to "Zufällige Trendumkehr",
        "zp.r.alle" to "Der Renditevorsprung hält in allen drei Verfahren",
        "zp.r.keines" to "Der Renditevorsprung verschwindet in allen drei Verfahren",
        "zp.r.teils" to "Der Renditevorsprung verschwindet {0}",
        "zp.r.ohne" to "Kein Renditevorsprung, weder echt noch auf Zufallspfaden",
        "zp.r.nurPfade" to "Kein Renditevorsprung in der echten Geschichte, auf Zufallspfaden in {0} von 3 Verfahren",
        "zp.d.alle" to "Der kleinere Rückgang hält in allen drei Verfahren",
        "zp.d.keines" to "Der kleinere Rückgang verschwindet in allen drei Verfahren",
        "zp.d.teils" to "Der kleinere Rückgang verschwindet {0}",
        "zp.d.ohne" to "Kein kleinerer Rückgang, weder echt noch auf Zufallspfaden",
        "zp.d.nurPfade" to "Kein kleinerer Rückgang in der echten Geschichte, auf Zufallspfaden in {0} von 3 Verfahren",
        "zp.ohne.EINFACH" to "ohne zeitliche Ordnung",
        "zp.ohne.BLOCK" to "bei neu gereihten Abschnitten",
        "zp.ohne.UMKEHR" to "ohne Trend",
        "zp.und" to " und ",
        "zp.achse.r" to "Rendite pro Jahr der Pfade, Punkt: echte Geschichte. Rechts: Anteil der Pfade darunter",
        "zp.achse.d" to "Größter Rückgang der Pfade, Punkt: echte Geschichte. Rechts: Anteil der Pfade mit größerem Rückgang",
        "zp.anteile" to "Anteile je Verfahren",
        "zp.pfade" to "Pfade",
        "zp.mind" to "Rendite mindestens wie echt",
        "zp.hoechst" to "Rückgang höchstens wie echt",
        "zp.beides" to "Beides",
        "zp.vorn.r" to "Mehr Rendite als Halten",
        "zp.vorn.d" to "Kleinerer Rückgang als Halten",
        "zp.text" to "Zufallspfade sind neue Kursverläufe aus den echten Tagen des Zeitraums. Regel und Kaufen und Halten laufen auf jedem Pfad mit denselben Kosten. Zufallspfade sind keine Prognose.\n" +
            "• Einfache Ziehung: Tage einzeln neu gezogen, erhält die Verteilung der Tagesrenditen, zerstört jede zeitliche Ordnung\n" +
            "• Block-Bootstrap (stationär nach Politis und Romano): Abschnitte zufälliger Länge von im Mittel 10 bis 120 Handelstagen neu gereiht, erhält Trends und Schwankungshäufung in den Abschnitten, zerstört den langen Verlauf\n" +
            "• Zufällige Trendumkehr: Reihenfolge bleibt, abschnittsweise sind Steigen und Fallen vertauscht, erhält die Größe der Bewegungen, zerstört die Trendrichtung\n" +
            "• Ein Vorsprung hält, wenn die Regel auf mindestens der Hälfte der Pfade vor Kaufen und Halten liegt",
        // Viele Startzeitpunkte
        "sf.titel" to "Viele Startzeitpunkte",
        "sf.kern" to "Regel lag in {0} von {1} Startzeitpunkten vorn",
        "sf.kern.halten" to "{0} Startzeitpunkte",
        "sf.rendite" to "Rendite pro Jahr",
        "sf.rueckgang" to "Größter Rückgang",
        "sf.median.r" to "Rendite, Median",
        "sf.min.r" to "Rendite, schlechtester Start",
        "sf.median.d" to "Rückgang, Median",
        "sf.max.d" to "Rückgang, größter",
        "sf.text" to "Jeder Monatsanfang der Kursgeschichte ist ein eigener Start, gehalten wird die gewählte Dauer.\n" +
            "• Kasten: mittlere Hälfte der Ergebnisse, Strich darin: Median, Linien: bis zum kleinsten und größten Wert\n" +
            "• Einmalanlage von 10.000 € je Start, mit dem Schalter „Sparplan“ eine Monatsrate und die geldgewichtete Rendite, ein Ausweichwert zählt dort als Bargeld\n" +
            "• Die Fenster überlappen sich, benachbarte Starts fallen ähnlich aus\n" +
            "• Die ersten 250 Handelstage dienen den Indikatoren zum Einschwingen\n" +
            "• {hist}",
        // Sparplan
        "sp.schalter" to "Sparplan",
        "sp.sparen" to "Stures Sparen",
        "sp.eingezahlt" to "Eingezahlt",
        "sp.geld" to "geldgewichtet",
        "sp.bereinigt" to "um Einzahlungen bereinigt",
        "sp.kaeufe" to "Käufe",
        "sp.verkaeufe" to "Verkäufe",
        "sp.text" to "Sparplan mit {0} am ersten Handelstag jedes Monats, {hk}.\n" +
            "• Signal an: Die Rate wird gekauft\n" +
            "• Signal aus: Die Position wird verkauft, die Raten sammeln sich als Bargeld (auch bei einem Ausweichwert) und werden beim nächsten Einstieg mit investiert\n" +
            "• Stures Sparen kauft jede Rate\n" +
            "• Rendite pro Jahr ist geldgewichtet (interner Zinsfuß)\n" +
            "• Stopp, Zeit und Ziel aus „Ausstieg“ gelten im Sparplan nicht",
        // Ueberanpassung, Mindestlaufzeit, beste von vielen Varianten
        "pbo.zeile" to "Overfitting-Risiko",
        "pbo.GERING" to "gering: Die Auswahl hält meist auch in unbekannten Abschnitten",
        "pbo.ERHEBLICH" to "erheblich: Die Auswahl hält oft nicht",
        "pbo.HOCH" to "hoch: Die im In-Sample beste Variante landet im Out-of-Sample meist unter dem Median",
        "pbo.text" to "Wie oft die im In-Sample beste Variante im Out-of-Sample schlechter abschneidet als die mittlere Variante (Probability of Backtest Overfitting nach Bailey, Borwein, López de Prado und Zhu).\n" +
            "• Der Zeitraum wird in {0} gleich lange Abschnitte geteilt\n" +
            "• Jede Hälfte der Abschnitte ist einmal In-Sample, der Rest Out-of-Sample: {1} Kombinationen\n" +
            "• In-Sample gewinnt die Variante mit der höchsten Sharpe-Quote, Out-of-Sample zählt ihr Rang unter allen Varianten\n" +
            "• Die Zahl ist der Anteil der Kombinationen, in denen sie dort in der unteren Hälfte landet\n" +
            "• Um 50 % heißt: Die Auswahl ist so gut wie Raten",
        "spa.titel" to "Beste von vielen Varianten",
        "spa.nein" to "Die beste von {0} Varianten ist nach dem Test nicht von Zufall zu unterscheiden (p {1})",
        "spa.ja" to "Die beste von {0} Varianten schlägt Kaufen und Halten nach dem Test mehr als zufällig (p {1})",
        "spa.text" to "Test auf überlegene Vorhersagekraft nach Hansen (SPA-Test): prüft, ob irgendeine der Varianten Kaufen und Halten im ganzen Zeitraum schlägt, und rechnet ein, dass die beste aus vielen gewählt wurde.\n" +
            "• Verglichen wird die tägliche Rendite jeder Variante mit der von Kaufen und Halten, nach Kosten\n" +
            "• Die Zufallsverteilung entsteht aus {0} stationären Block-Bootstraps (Politis und Romano) mit im Mittel {1} Handelstagen je Block, so bleiben Trends und Schwankungshäufung erhalten\n" +
            "• p ist der Anteil der Ziehungen mit mindestens so großem Vorsprung wie echt, unter 0,05 gilt der Vorsprung als mehr als Zufall\n" +
            "• Klar unterlegene Varianten verwässern den Test nicht, sie gehen ohne Gewicht ein",
        "mt.titel" to "Mindestlaufzeit",
        "mt.satz" to "Für diese Sharpe-Quote bräuchte es {0} Jahre Daten, vorhanden sind {1}",
        "mt.viel" to "Für diese Sharpe-Quote bräuchte es über 100 Jahre Daten, vorhanden sind {0}",
        "mt.keine" to "Sharpe-Quote nicht über null: Keine Laufzeit belegt einen Vorteil, vorhanden sind {0} Jahre",
        "mt.text" to "Wie viele Jahre Kurse nötig wären, damit diese Sharpe-Quote mit 95 % Sicherheit über null liegt (Minimum Track Record Length nach Bailey und López de Prado).\n" +
            "• Sharpe-Quote der Regel im Zeitraum: {0} pro Jahr\n" +
            "• Schiefe und Wölbung der Tagesrenditen gehen ein, seltene große Verluste verlängern die Laufzeit\n" +
            "• Sind weniger Jahre vorhanden als nötig, kann das Ergebnis Zufall sein\n" +
            "• Gemessen gegen null, nicht gegen Kaufen und Halten",
        "zp.spanne.r" to "Rendite, mittlere 90 % der Pfade",
        "zp.spanne.d" to "Rückgang, mittlere 90 % der Pfade",
        "zp.bis" to " bis ",
    )

    private val EN = mapOf(
        "zp.titel" to "Random paths",
        "zp.verschoben" to "Real prices, shifted signals",
        "zp.EINFACH" to "Simple resampling",
        "zp.BLOCK" to "Block resampling",
        "zp.UMKEHR" to "Random trend reversal",
        "zp.r.alle" to "The return lead holds in all three methods",
        "zp.r.keines" to "The return lead disappears in all three methods",
        "zp.r.teils" to "The return lead disappears {0}",
        "zp.r.ohne" to "No return lead, neither in real history nor on random paths",
        "zp.r.nurPfade" to "No return lead in real history, on random paths in {0} of 3 methods",
        "zp.d.alle" to "The smaller drawdown holds in all three methods",
        "zp.d.keines" to "The smaller drawdown disappears in all three methods",
        "zp.d.teils" to "The smaller drawdown disappears {0}",
        "zp.d.ohne" to "No smaller drawdown, neither in real history nor on random paths",
        "zp.d.nurPfade" to "No smaller drawdown in real history, on random paths in {0} of 3 methods",
        "zp.ohne.EINFACH" to "without any order in time",
        "zp.ohne.BLOCK" to "with reordered stretches",
        "zp.ohne.UMKEHR" to "without the trend",
        "zp.und" to " and ",
        "zp.achse.r" to "Return per year of the paths, dot: real history. Right: share of paths below it",
        "zp.achse.d" to "Maximum drawdown of the paths, dot: real history. Right: share of paths with a larger drawdown",
        "zp.anteile" to "Shares per method",
        "zp.pfade" to "Paths",
        "zp.mind" to "Return at least as in real history",
        "zp.hoechst" to "Drawdown at most as in real history",
        "zp.beides" to "Both",
        "zp.vorn.r" to "More return than holding",
        "zp.vorn.d" to "Smaller drawdown than holding",
        "zp.text" to "Random paths are new price histories built from the real days of the period. The rule and buy and hold run on every path with the same costs. Random paths are not a forecast.\n" +
            "• Simple resampling: days drawn one by one, keeps the distribution of daily returns, destroys any order in time\n" +
            "• Block resampling (stationary after Politis and Romano): stretches of random length, 10 to 120 trading days on average in a new order, keeps trends and volatility clustering within the stretches, destroys the long course\n" +
            "• Random trend reversal: the order stays, rising and falling are swapped stretch by stretch, keeps the size of the moves, destroys the trend direction\n" +
            "• A lead holds when the rule is ahead of buy and hold on at least half of the paths",
        "sf.titel" to "Many start dates",
        "sf.kern" to "The rule was ahead for {0} of {1} start dates",
        "sf.kern.halten" to "{0} start dates",
        "sf.rendite" to "Return per year",
        "sf.rueckgang" to "Maximum drawdown",
        "sf.median.r" to "Return, median",
        "sf.min.r" to "Return, worst start",
        "sf.median.d" to "Drawdown, median",
        "sf.max.d" to "Drawdown, largest",
        "sf.text" to "Every start of a month in the price history is a start of its own, held for the chosen period.\n" +
            "• Box: middle half of the results, line inside: median, whiskers: down to the smallest and up to the largest value\n" +
            "• Lump sum of €10,000 per start, with the “Savings plan” switch a monthly amount and the money-weighted return, a fallback asset counts as cash there\n" +
            "• The windows overlap, neighboring starts turn out similar\n" +
            "• The first 250 trading days let the indicators settle\n" +
            "• {hist}",
        "sp.schalter" to "Savings plan",
        "sp.sparen" to "Plain saving",
        "sp.eingezahlt" to "Paid in",
        "sp.geld" to "money-weighted",
        "sp.bereinigt" to "adjusted for deposits",
        "sp.kaeufe" to "Buys",
        "sp.verkaeufe" to "Sales",
        "sp.text" to "Savings plan with {0} on the first trading day of each month, {hk}.\n" +
            "• Signal on: the amount is invested\n" +
            "• Signal off: the position is sold, the amounts pile up as cash (also with a fallback asset) and are invested with the next entry\n" +
            "• Plain saving buys every amount\n" +
            "• Return per year is money-weighted (internal rate of return)\n" +
            "• Stop, time and target from “Exit” do not apply in the savings plan",
        "pbo.zeile" to "Overfitting risk",
        "pbo.GERING" to "low: the choice mostly holds in unseen stretches too",
        "pbo.ERHEBLICH" to "considerable: the choice often does not hold",
        "pbo.HOCH" to "high: the best in-sample variant mostly lands below the median out-of-sample",
        "pbo.text" to "How often the best in-sample variant does worse than the middle variant out-of-sample (probability of backtest overfitting after Bailey, Borwein, López de Prado and Zhu).\n" +
            "• The period is split into {0} stretches of equal length\n" +
            "• Every half of the stretches is in-sample once, the rest out-of-sample: {1} combinations\n" +
            "• In-sample the variant with the highest Sharpe ratio wins, out-of-sample its rank among all variants counts\n" +
            "• The figure is the share of combinations in which it lands in the lower half there\n" +
            "• Around 50% means the choice is as good as guessing",
        "spa.titel" to "Best of many variants",
        "spa.nein" to "After the test, the best of {0} variants cannot be told apart from chance (p {1})",
        "spa.ja" to "After the test, the best of {0} variants beats buy and hold by more than chance (p {1})",
        "spa.text" to "Test for superior predictive ability after Hansen (SPA test): checks whether any of the variants beats buy and hold over the whole period, taking into account that the best was picked from many.\n" +
            "• The daily return of each variant is compared with that of buy and hold, after costs\n" +
            "• The chance distribution comes from {0} stationary block resamples (Politis and Romano) with {1} trading days per block on average, which keeps trends and volatility clustering\n" +
            "• p is the share of resamples with a lead at least as large as the real one, below 0.05 the lead counts as more than chance\n" +
            "• Clearly inferior variants do not dilute the test, they enter without weight",
        "mt.titel" to "Minimum track record",
        "mt.satz" to "This Sharpe ratio would need {0} years of data, {1} are available",
        "mt.viel" to "This Sharpe ratio would need more than 100 years of data, {0} are available",
        "mt.keine" to "Sharpe ratio not above zero: no track record shows an edge, {0} years are available",
        "mt.text" to "How many years of prices would be needed for this Sharpe ratio to be above zero with 95% confidence (minimum track record length after Bailey and López de Prado).\n" +
            "• Sharpe ratio of the rule in the period: {0} per year\n" +
            "• Skewness and kurtosis of the daily returns count, rare large losses lengthen the record\n" +
            "• With fewer years available than needed, the result may be chance\n" +
            "• Measured against zero, not against buy and hold",
        "zp.spanne.r" to "Return, middle 90% of paths",
        "zp.spanne.d" to "Drawdown, middle 90% of paths",
        "zp.bis" to " to ",
    )
}
