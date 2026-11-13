package de.troetenrepublik.app.i18n

import de.troetenrepublik.app.engine.RegelGlocke

// Texte zu Regel-Glocke, ATR-Abstand, Stopp mit Ziel und der Auswertung der eigenen Geschaefte, 0.78. Ein Schluessel je
// Text, Platzhalter {0}, {1} ... fuer Werte.
internal object StrategieTexte {

    fun text(deutsch: Boolean, key: String, w: List<String>): String {
        var t = (if (deutsch) DE else EN)[key] ?: return ZufallTexte.text(deutsch, key, w)
        w.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
        return t
    }

    // Alle Schluessel einer Sprache (der Test vergleicht beide Sprachen)
    fun schluessel(deutsch: Boolean): Set<String> = (if (deutsch) DE else EN).keys

    // Name einer Glocke auf eine Regel des Strategietests; null, wenn art keine solche Glocke ist.
    fun glocke(s: Strings, deutsch: Boolean, art: String): String? =
        RegelGlocke.teile(art)?.let { text(deutsch, "rg.art", listOf(s.slRegelKurz(it.art, it.a, it.b, it.n))) }

    // Text der Mitteilung und der Buchung zu einem Signal; art: die Art der Glocke aus der Buchung.
    fun signal(s: Strings, deutsch: Boolean, tag: String, art: String, kurs: String): String {
        val regel = RegelGlocke.teile(art)?.let { s.slRegelKurz(it.art, it.a, it.b, it.n) } ?: art
        return text(deutsch, if (tag == RegelGlocke.TAG_KAUF) "rg.kauf" else "rg.verkauf", listOf(regel, kurs))
    }

    private val DE = mapOf(
        // Glocke auf eine Regel
        "rg.setzen" to "Glocke auf diese Regel",
        "rg.gesetzt" to "Glocke gesetzt",
        "rg.art" to "Regel {0}",
        "rg.kauf" to "Kaufsignal: {0} · Kurs {1}",
        "rg.verkauf" to "Verkaufssignal: {0} · Kurs {1}",
        // Abstand aus der ATR
        "atr.stopp" to "Stopp bei {0} ATR",
        "atr.hinweis" to "mittlere Tagesspanne über 20 Tage, {0} unter dem Kurs",
        "atr.mal" to "{0} ATR",
        "op.vorbereiten" to "Order vorbereiten",
        "op.stoppDanach" to "Stopp nach dem Kauf",
        // Stopp mit Ziel
        "zl.ziel" to "Ziel",
        "zl.eingeben" to "Zielpreis eingeben",
        "zl.falsch" to "Ziel nur über dem Kurs",
        "zl.mitZiel" to "Verknüpft mit Ziel {0}",
        "zl.mitStopp" to "Verknüpft mit Stopp {0}",
        "zl.grund" to "Verknüpfte Order ausgeführt",
        // Auswertung der eigenen Geschäfte
        "vk.hint" to "{0} von {1} mit Gewinn",
        "an.wechsel" to "von {0} auf {1}",
        "vk.faktor.info" to "Summe der Gewinne geteilt durch die Summe der Verluste. Über 1 überwiegen die Gewinne.",
        "vk.gewinn" to "Ø Gewinn",
        "vk.verlust" to "Ø Verlust",
        "vk.je" to "Ø Ergebnis je Verkauf",
        "vk.steuer" to "Ergebnisse vor Steuern.",
        "ga.titel" to "Haltedauer und Auslöser",
        "ga.unter" to "Ø {0} gehalten · längste Verlustserie {1}",
        "ga.unterOhne" to "Längste Verlustserie {0}",
        "ga.dauer" to "Ergebnis nach Haltedauer",
        "ga.WOCHE" to "Unter 1 Woche",
        "ga.MONAT" to "Bis 1 Monat",
        "ga.LAENGER" to "Länger",
        "ga.ausloeser" to "Ergebnis nach Auslöser",
        "ga.HAND" to "Von Hand",
        "ga.STOPP" to "Stopp",
        "ga.LIMIT" to "Limit",
        "ga.TRAILING" to "Trailing-Stopp",
        "ga.ORDER" to "Order",
        "ga.ENDE" to "Knock-out oder Verfall",
        "ga.zeile" to "{0} Verkäufe · {1} mit Gewinn",
        "ga.zeile1" to "1 Verkauf · {1} mit Gewinn",
        "ga.verkaeufe" to "{0} Verkäufe",
        "ga.verkauf1" to "1 Verkauf",
        "ga.letzte" to "Letzte Verkäufe",
        "ga.gehalten" to "{0} gehalten · {1}",
        // Handelsstile im Strategietest
        "hs.zeile" to "Handelsstil",
        "hs.BUY_AND_HOLD" to "Kaufen und Halten",
        "hs.POSITION" to "Positionstrading",
        "hs.SWING" to "Swing Trading",
        "hs.DAYTRADING" to "Daytrading",
        "hs.SCALPING" to "Scalping",
        "hs.dauer.BUY_AND_HOLD" to "Jahre",
        "hs.dauer.POSITION" to "Wochen bis Monate",
        "hs.dauer.SWING" to "Tage bis Wochen",
        "hs.dauer.DAYTRADING" to "Minuten bis Stunden",
        "hs.dauer.SCALPING" to "Sekunden bis Minuten",
        "hs.gruppe" to "{0} · {1}",
        "hs.erklaerung" to "Handelsstile nach Haltedauer. Die Zeile „Handelsstil“ folgt der mittleren Haltedauer im Test: bis 30 Handelstage Swing Trading, bis 252 Positionstrading, darüber Kaufen und Halten.\n" +
            "• Kaufen und Halten: über Jahre halten, ohne Signale\n" +
            "• Positionstrading: einem Trend über Wochen bis Monate folgen (Trendfolge), etwa mit Durchschnitt, MACD oder Ausbruch\n" +
            "• Swing Trading: einzelne Kursschwünge über Tage bis Wochen handeln, etwa Rückschläge mit RSI oder Bollinger-Bändern\n" +
            "• Daytrading: Kauf und Verkauf am selben Tag\n" +
            "• Scalping: viele Geschäfte über Sekunden bis Minuten mit kleinen Gewinnen\n" +
            "Daytrading und Scalping brauchen Kurse innerhalb des Tages. Der Test rechnet mit Tageskursen und kann beide nicht prüfen.",
    )

    private val EN = mapOf(
        "rg.setzen" to "Bell for this rule",
        "rg.gesetzt" to "Bell set",
        "rg.art" to "Rule {0}",
        "rg.kauf" to "Buy signal: {0} · price {1}",
        "rg.verkauf" to "Sell signal: {0} · price {1}",
        "atr.stopp" to "Stop at {0} ATR",
        "atr.hinweis" to "average daily range over 20 days, {0} below the price",
        "atr.mal" to "{0} ATR",
        "op.vorbereiten" to "Prepare order",
        "op.stoppDanach" to "Stop after the buy",
        "zl.ziel" to "Target",
        "zl.eingeben" to "Enter target price",
        "zl.falsch" to "Target only above the price",
        "zl.mitZiel" to "Linked to target {0}",
        "zl.mitStopp" to "Linked to stop {0}",
        "zl.grund" to "Linked order executed",
        "vk.hint" to "{0} of {1} with a gain",
        "an.wechsel" to "from {0} to {1}",
        "vk.faktor.info" to "Total gains divided by total losses. Above 1 the gains outweigh the losses.",
        "vk.gewinn" to "Avg. gain",
        "vk.verlust" to "Avg. loss",
        "vk.je" to "Avg. result per sale",
        "vk.steuer" to "Results are before tax.",
        "ga.titel" to "Holding period and trigger",
        "ga.unter" to "Held {0} on average · longest losing streak {1}",
        "ga.unterOhne" to "Longest losing streak {0}",
        "ga.dauer" to "Result by holding period",
        "ga.WOCHE" to "Under 1 week",
        "ga.MONAT" to "Up to 1 month",
        "ga.LAENGER" to "Longer",
        "ga.ausloeser" to "Result by trigger",
        "ga.HAND" to "Manual",
        "ga.STOPP" to "Stop",
        "ga.LIMIT" to "Limit",
        "ga.TRAILING" to "Trailing stop",
        "ga.ORDER" to "Order",
        "ga.ENDE" to "Knock-out or expiry",
        "ga.zeile" to "{0} sales · {1} with a gain",
        "ga.zeile1" to "1 sale · {1} with a gain",
        "ga.verkaeufe" to "{0} sales",
        "ga.verkauf1" to "1 sale",
        "ga.letzte" to "Latest sales",
        "ga.gehalten" to "held {0} · {1}",
        "hs.zeile" to "Trading style",
        "hs.BUY_AND_HOLD" to "Buy and hold",
        "hs.POSITION" to "Position trading",
        "hs.SWING" to "Swing trading",
        "hs.DAYTRADING" to "Day trading",
        "hs.SCALPING" to "Scalping",
        "hs.dauer.BUY_AND_HOLD" to "Years",
        "hs.dauer.POSITION" to "Weeks to months",
        "hs.dauer.SWING" to "Days to weeks",
        "hs.dauer.DAYTRADING" to "Minutes to hours",
        "hs.dauer.SCALPING" to "Seconds to minutes",
        "hs.gruppe" to "{0} · {1}",
        "hs.erklaerung" to "Trading styles by holding period. The “Trading style” row follows the average holding period in the test: up to 30 trading days swing trading, up to 252 position trading, above that buy and hold.\n" +
            "• Buy and hold: buy and hold for years, without signals\n" +
            "• Position trading: follow a trend for weeks to months (trend following), for example with an average, MACD or a breakout\n" +
            "• Swing trading: trade single price swings over days to weeks, for example pullbacks with RSI or Bollinger Bands\n" +
            "• Day trading: buy and sell on the same day\n" +
            "• Scalping: many trades over seconds to minutes with small gains\n" +
            "Day trading and scalping need prices within the day. The test uses daily prices and cannot check either.",
    )
}
