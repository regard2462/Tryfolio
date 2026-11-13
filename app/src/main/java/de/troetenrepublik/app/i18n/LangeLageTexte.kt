package de.troetenrepublik.app.i18n

import de.troetenrepublik.app.data.Lang

// Texte der langen Reihen zur Lage: Unsicherheitsindizes (Geopolitical Risk, Economic Policy Uncertainty), Konflikte seit 1946,
// KGV der Branche und Risikopraemie der Aktien. Ein Schluessel je Text, Platzhalter {0}, {1}, {2} fuer Werte.
internal object LangeLageTexte {

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
        // Politische Unsicherheit (Weltlage)
        "u.titel" to "Politische Unsicherheit",
        "u.info" to "Beide Indizes zählen, wie oft Zeitungen ein Thema nennen. Sie zeigen die Aufmerksamkeit der Medien und sagen nichts über den Ausgang.\n\n" +
            "Geopolitisches Risiko (GPR): Anteil der Artikel, die Kriegsgefahr, Drohungen und Handlungen nennen. 100 ist der Durchschnitt der Jahre 1985 bis 2019.\n\n" +
            "Wirtschaftspolitik (EPU): Anteil der Artikel, die Wirtschaft, Politik und Unsicherheit nennen, für die USA und weltweit. Der Durchschnitt von 1985 bis 2009 liegt bei rund 100.\n\n" +
            "Aktienmarkt in den Nachrichten (EMV): Anteil der US-Artikel, die Schwankungen am Aktienmarkt nennen.\n\n" +
            "Der Balken zeigt, in wie viel Prozent der Monate der Reihe der Wert niedriger lag als zuletzt (Reihen ab 1985, weltweit ab 1997). Stufen: bis 25 niedrig, bis 75 normal, bis 95 erhöht, darüber hoch.",
        "u.quellen" to "Quellen:\n{0}",
        "u.gpr" to "Geopolitisches Risiko",
        "u.epu" to "Wirtschaftspolitik USA",
        "u.gl" to "Wirtschaftspolitik weltweit",
        "u.emv" to "Aktienmarkt in den Nachrichten",
        "u.zeile" to "{0}: {1}",
        "u.stufe.NIEDRIG" to "niedrig",
        "u.stufe.NORMAL" to "normal",
        "u.stufe.ERHOEHT" to "erhöht",
        "u.stufe.HOCH" to "hoch",
        "u.stand" to "Stand {0}",
        "u.seit" to "Höchster Stand seit",
        "u.rekord" to "Rekord seit {0}",
        "u.c.gpr" to "Geopolitisches Risiko seit 1900",
        "u.c.td" to "Drohungen und Handlungen seit 1985",
        "u.c.epu" to "Wirtschaftspolitik USA seit 1985",
        "u.c.gl" to "Wirtschaftspolitik weltweit seit 1997",
        "u.c.emv" to "Aktienmarkt in den Nachrichten seit 1985",
        "u.n.gpr" to "GPR",
        "u.n.droh" to "Drohungen",
        "u.n.hand" to "Handlungen",
        "u.n.epu" to "EPU USA",
        "u.n.gl" to "EPU Welt",
        "u.n.emv" to "EMV",
        // Geopolitisches Risiko je Land
        "g.titel" to "Geopolitisches Risiko seit 1900",
        "g.letztes" to "Letztes Jahr",
        "g.hoechst" to "Höchstes Jahr",
        "g.mittel" to "Mittel seit {0}",
        "g.zeile" to "{0}: {1}",
        "g.sp" to "Geopolitisches Risiko",
        "g.sp.zeile" to "{0}: {1}, höher als in {2} % der Monate seit 1985",
        "g.info" to "Anteil der Zeitungsartikel, die das Land zusammen mit Kriegsgefahr, Drohungen und Handlungen nennen (Geopolitical Risk Index je Land). Der Wert zeigt die Aufmerksamkeit der Medien und sagt nichts über den Ausgang. Bis 1984 beruht er auf drei Zeitungen, danach auf zehn. Die Jahreswerte sind Mittel der Monate.",
        "g.sp.info" to "Monatswerte des Index je Land ab 1985. Der Anteil der Zeitungsartikel, die das Land zusammen mit Kriegsgefahr, Drohungen und Handlungen nennen. Der Wert zeigt die Aufmerksamkeit der Medien und sagt nichts über den Ausgang.",
        // Konflikte seit 1946
        "k.titel" to "Konflikte seit {0}",
        "k.jahre" to "Jahre mit Konflikt",
        "k.jahreWert" to "{0} von {1}",
        "k.krieg" to "Kriegsjahre",
        "k.kriegUnter" to "ab 1.000 Tote",
        "k.aktiv" to "Aktiv seit",
        "k.aktivUnter" to "{0} Jahre ohne Pause",
        "k.zuletzt" to "Zuletzt",
        "k.zuletztUnter" to "seit {0} Jahren ruhig",
        "k.laengste" to "Längste Serie",
        "k.laengsteWert" to "{0} Jahre",
        "k.laengsteUnter" to "{0} bis {1}",
        "k.leg1" to "25 bis 999 Tote",
        "k.leg2" to "ab 1.000 Tote",
        "k.band" to "Konflikte je Jahr",
        "k.bandMax" to "höchstens {0}",
        "k.info" to "Bewaffnete Konflikte, an denen mindestens eine Regierung beteiligt ist und die im Jahr mindestens 25 Gefechtstote fordern (UCDP/PRIO Armed Conflict Dataset). Die Höhe der Säule ist die Zahl der Konflikte im Jahr, die Farbe die schwerste Stufe: 25 bis 999 Gefechtstote oder ab 1.000 (Krieg). Ein Konflikt zählt für jedes Land, in dem gekämpft wird oder das angegriffen wird. Aufgelöste Staaten laufen unter ihrem heutigen Nachfolger (Sowjetunion unter Russland, Jugoslawien unter Serbien, Südvietnam unter Vietnam, Südjemen unter Jemen).",
        // KGV der Branche
        "b.titel" to "KGV gegen die Branche",
        "b.kern" to "Branche {0}",
        "b.unten" to "US-Branche, Stand Ende {0}",
        "b.lage.GUENSTIGER" to "günstiger",
        "b.lage.VERGLEICHBAR" to "vergleichbar",
        "b.lage.TEURER" to "teurer",
        "b.verlauf" to "Branchen-KGV seit {0}",
        "b.n.branche" to "Branche",
        "b.n.markt" to "Markt",
        "b.n.aktie" to "Aktie",
        "b.mittel" to "Mittel {0} bis {1}",
        "b.firmen" to "Firmen in der Branche (USA)",
        "b.info" to "Vergleich des KGV der Aktie mit dem Durchschnitt der US-Firmen derselben Branche. Branchen-KGV: Marktwert aller Firmen der Branche geteilt durch ihren Gewinn der letzten zwölf Monate, nur Firmen mit Gewinn. Stand jeweils Ende des Jahres. Bis 10 % Abstand gilt das KGV als vergleichbar. Die Branche folgt der Zuordnung der Wertpapierseite zu den Branchen von Damodaran und umfasst nur US-Firmen. Bei Aktien aus anderen Ländern ist der Abstand ein Anhaltspunkt.\n\nQuelle: Aswath Damodaran, NYU Stern (https://pages.stern.nyu.edu/~adamodar/), Stand {0}.",
        // Risikopraemie
        "p.titel" to "Risikoprämie der Aktien",
        "p.wert" to "{0} %",
        "p.unter" to "Ende {0}, niedriger als in {1} % der Jahre seit {2}",
        "p.mittel" to "Mittel seit {0}",
        "p.n.praemie" to "Prämie",
        "p.n.zins" to "Zins",
        "p.verlauf" to "Prämie und Zins der 10-jährigen US-Anleihe",
        "p.info" to "Die implizite Risikoprämie ist die Rendite, die der Kurs des S&P 500 bei den erwarteten Zahlungen an die Aktionäre über dem Zins der 10-jährigen US-Staatsanleihe verspricht. Je niedriger sie ist, desto teurer sind Aktien im Verhältnis zu Gewinnen und Zinsen. Jahreswerte jeweils zum Jahresende, Berechnung nach dem Free-Cashflow-to-Equity-Modell von Aswath Damodaran (NYU Stern, https://pages.stern.nyu.edu/~adamodar/), Stand {0}.",
        "p.quelle" to "Aswath Damodaran, NYU Stern",
    )

    private val EN = mapOf(
        "u.titel" to "Political uncertainty",
        "u.info" to "Both indices count how often newspapers mention a topic. They show media attention and say nothing about the outcome.\n\n" +
            "Geopolitical risk (GPR): share of articles that mention war risk, threats and acts. 100 is the average of 1985 to 2019.\n\n" +
            "Economic policy (EPU): share of articles that mention the economy, policy and uncertainty, for the US and worldwide. The average of 1985 to 2009 is about 100.\n\n" +
            "Stock market in the news (EMV): share of US articles that mention stock market swings.\n\n" +
            "The bar shows the share of months in the series in which the value was lower than the latest (series from 1985, global from 1997). Levels: up to 25 low, up to 75 normal, up to 95 elevated, above that high.",
        "u.quellen" to "Sources:\n{0}",
        "u.gpr" to "Geopolitical risk",
        "u.epu" to "US economic policy",
        "u.gl" to "Global economic policy",
        "u.emv" to "Stock market in the news",
        "u.zeile" to "{0}: {1}",
        "u.stufe.NIEDRIG" to "low",
        "u.stufe.NORMAL" to "normal",
        "u.stufe.ERHOEHT" to "elevated",
        "u.stufe.HOCH" to "high",
        "u.stand" to "As of {0}",
        "u.seit" to "Highest since",
        "u.rekord" to "Record since {0}",
        "u.c.gpr" to "Geopolitical risk since 1900",
        "u.c.td" to "Threats and acts since 1985",
        "u.c.epu" to "US economic policy since 1985",
        "u.c.gl" to "Global economic policy since 1997",
        "u.c.emv" to "Stock market in the news since 1985",
        "u.n.gpr" to "GPR",
        "u.n.droh" to "Threats",
        "u.n.hand" to "Acts",
        "u.n.epu" to "EPU US",
        "u.n.gl" to "EPU global",
        "u.n.emv" to "EMV",
        "g.titel" to "Geopolitical risk since 1900",
        "g.letztes" to "Last year",
        "g.hoechst" to "Highest year",
        "g.mittel" to "Average since {0}",
        "g.zeile" to "{0}: {1}",
        "g.sp" to "Geopolitical risk",
        "g.sp.zeile" to "{0}: {1}, higher than in {2}% of months since 1985",
        "g.info" to "Share of newspaper articles that mention the country together with war risk, threats and acts (country Geopolitical Risk Index). The value shows media attention and says nothing about the outcome. Until 1984 it rests on three newspapers, afterwards on ten. Yearly values are averages of the months.",
        "g.sp.info" to "Monthly values of the country index from 1985. The share of newspaper articles that mention the country together with war risk, threats and acts. The value shows media attention and says nothing about the outcome.",
        "k.titel" to "Conflicts since {0}",
        "k.jahre" to "Years with conflict",
        "k.jahreWert" to "{0} of {1}",
        "k.krieg" to "War years",
        "k.kriegUnter" to "1,000 or more deaths",
        "k.aktiv" to "Active since",
        "k.aktivUnter" to "{0} years without a break",
        "k.zuletzt" to "Last",
        "k.zuletztUnter" to "quiet for {0} years",
        "k.laengste" to "Longest run",
        "k.laengsteWert" to "{0} years",
        "k.laengsteUnter" to "{0} to {1}",
        "k.leg1" to "25 to 999 deaths",
        "k.leg2" to "1,000 or more deaths",
        "k.band" to "Conflicts per year",
        "k.bandMax" to "up to {0}",
        "k.info" to "Armed conflicts with at least one government involved and at least 25 battle deaths in the year (UCDP/PRIO Armed Conflict Dataset). The height of a column is the number of conflicts in the year, the color the most severe level: 25 to 999 battle deaths or 1,000 and more (war). A conflict counts for every country where fighting takes place or that is attacked. Dissolved states appear under their present successor (Soviet Union under Russia, Yugoslavia under Serbia, South Vietnam under Vietnam, South Yemen under Yemen).",
        "b.titel" to "P/E against the industry",
        "b.kern" to "Industry {0}",
        "b.unten" to "US industry, as of end of {0}",
        "b.lage.GUENSTIGER" to "cheaper",
        "b.lage.VERGLEICHBAR" to "comparable",
        "b.lage.TEURER" to "pricier",
        "b.verlauf" to "Industry P/E since {0}",
        "b.n.branche" to "Industry",
        "b.n.markt" to "Market",
        "b.n.aktie" to "Stock",
        "b.mittel" to "Average {0} to {1}",
        "b.firmen" to "Companies in the industry (US)",
        "b.info" to "Compares the P/E of the stock with the average of US companies in the same industry. Industry P/E: market value of all companies in the industry divided by their earnings of the last twelve months, profitable companies only. As of the end of each year. Up to 10% apart counts as comparable. The industry follows the mapping of the security page to Damodaran's industries and covers US companies only. For stocks from other countries the gap is a rough guide.\n\nSource: Aswath Damodaran, NYU Stern (https://pages.stern.nyu.edu/~adamodar/), as of {0}.",
        "p.titel" to "Equity risk premium",
        "p.wert" to "{0}%",
        "p.unter" to "End of {0}, lower than in {1}% of years since {2}",
        "p.mittel" to "Average since {0}",
        "p.n.praemie" to "Premium",
        "p.n.zins" to "Yield",
        "p.verlauf" to "Premium and yield of the 10-year US bond",
        "p.info" to "The implied risk premium is the return that the price of the S&P 500 promises, given the expected payments to shareholders, above the yield of the 10-year US Treasury bond. The lower it is, the pricier stocks are relative to earnings and rates. Yearly values at year end, computed with the free cash flow to equity model of Aswath Damodaran (NYU Stern, https://pages.stern.nyu.edu/~adamodar/), as of {0}.",
        "p.quelle" to "Aswath Damodaran, NYU Stern",
    )
}
