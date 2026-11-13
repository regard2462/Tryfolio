package de.troetenrepublik.app.i18n

// Texte zu Rohstoffen auf der Wertpapierseite (0.78): Fuer und Wider (engine/RohstoffWaage.kt), Signale aus dem Kurs,
// Optionshandel ueber den passenden ETF, Terminkurve und Oel weltweit. Ein Schluessel je Text, Platzhalter {0}, {1} ...
// fuer Werte. Schluessel der Waage: p.<Treiber>.<0 stuetzt, 1 belastet, 2 neutral> fuer die Aussage, m.<Treiber> fuer die
// Messung, w.<Treiber> fuer den Wirkungsweg.
internal object RohstoffLageTexte {

    fun text(deutsch: Boolean, key: String, w: List<String>): String {
        var t = (if (deutsch) DE else EN)[key] ?: return ""
        w.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
        return t
    }

    private val DE = mapOf(
        "k.dafuer" to "{0} dafür", "k.dagegen" to "{0} dagegen", "neutral" to "Neutral", "saldo" to "Saldo",
        "info" to "Jeder Punkt stammt aus einer Messgröße mit fester Schwelle.\n\nGewicht:\n• dreifach: Haupttreiber (Realzins bei Gold, Lager bei Öl, Gas und Kupfer)\n• doppelt: Dollar, Spekulanten, Trend, Terminkurve, OPEC+\n• einfach: alle übrigen\n\nNeutrale Punkte zählen nicht.",

        "p.REALZINS.0" to "Realzins fällt", "p.REALZINS.1" to "Realzins steigt", "p.REALZINS.2" to "Realzins kaum verändert",
        "p.INFLATION.0" to "Inflationserwartung steigt", "p.INFLATION.1" to "Inflationserwartung fällt", "p.INFLATION.2" to "Inflationserwartung kaum verändert",
        "p.DOLLAR.0" to "Dollar schwächer", "p.DOLLAR.1" to "Dollar stärker", "p.DOLLAR.2" to "Dollar kaum verändert",
        "p.POSITIONIERUNG.0" to "Spekulanten kaum investiert", "p.POSITIONIERUNG.1" to "Spekulanten einseitig auf steigende Preise", "p.POSITIONIERUNG.2" to "Spekulanten ohne Extrem",
        "p.TREND.0" to "Kurs über der 200-Tage-Linie", "p.TREND.1" to "Kurs unter der 200-Tage-Linie", "p.TREND.2" to "Kurs an der 200-Tage-Linie",
        "p.SAISON.0" to "{0} meist stark", "p.SAISON.1" to "{0} meist schwach", "p.SAISON.2" to "{0} ohne klares Muster",
        "p.LAGER.0" to "US-Lager knapp", "p.LAGER.1" to "US-Lager reichlich", "p.LAGER.2" to "US-Lager üblich",
        "p.BOERSENLAGER.0" to "Börsenlager schrumpfen", "p.BOERSENLAGER.1" to "Börsenlager wachsen", "p.BOERSENLAGER.2" to "Börsenlager kaum verändert",
        "p.TERMINKURVE.0" to "Nahe Lieferung teurer", "p.TERMINKURVE.1" to "Spätere Lieferung viel teurer", "p.TERMINKURVE.2" to "Terminkurve flach",
        "p.ENSO.0" to "{0} treibt den Preis eher hoch", "p.ENSO.1" to "{0} drückt den Preis eher",
        "p.KONJUNKTUR.0" to "US-Rezession unwahrscheinlich", "p.KONJUNKTUR.1" to "US-Rezessionsgefahr erhöht", "p.KONJUNKTUR.2" to "US-Rezessionsgefahr mittel",
        "p.OPEC.0" to "OPEC+ fördert weniger", "p.OPEC.1" to "OPEC+ fördert mehr", "p.OPEC.2" to "OPEC+ fördert etwa gleich viel",
        "p.WELTLAGER.0" to "OECD-Lager kleiner als vor einem Jahr", "p.WELTLAGER.1" to "OECD-Lager größer als vor einem Jahr", "p.WELTLAGER.2" to "OECD-Lager wie vor einem Jahr",

        "m.REALZINS" to "{0} · {1} Punkte in 3 Monaten", "m.INFLATION" to "{0} · {1} Punkte in 3 Monaten",
        "m.DOLLAR" to "Index {0} · {1} in 3 Monaten",
        "m.POSITIONIERUNG" to "Netto {0} der Kontrakte · Rang {1} von 100 in 3 Jahren",
        "m.TREND" to "{0} zum Schnitt", "m.SAISON" to "Im Mittel {0} · {1} von {2} Jahren im Plus",
        "m.LAGER" to "{0} zum Fünfjahresschnitt", "m.BOERSENLAGER" to "{0} zum Vormonat",
        "m.TERMINKURVE" to "Spanne {0} aufs Jahr", "m.ENSO" to "Index {0}",
        "m.KONJUNKTUR" to "{0} Wahrscheinlichkeit, New York Fed",
        "m.OPEC" to "{0} Mio. Barrel am Tag · {1} zum Vorjahr", "m.WELTLAGER" to "{0} Mio. Barrel · {1} zum Vorjahr",

        "w.REALZINS" to "Edelmetalle zahlen keine Zinsen. Fällt der Zins nach Inflation, kostet das Halten weniger.",
        "w.INFLATION" to "Steigt die erwartete Inflation, suchen Anleger Schutz in Edelmetallen.",
        "w.DOLLAR" to "Rohstoffe werden in Dollar gehandelt. Ein schwächerer Dollar macht sie für Käufer außerhalb der USA billiger.",
        "w.POSITIONIERUNG" to "Sind fast alle Spekulanten schon dabei, fehlen neue Käufer und ein Rückschlag fällt heftiger aus. Sind kaum welche dabei, ist Platz für Käufe.",
        "w.TREND" to "Ein Kurs über dem Schnitt der letzten 200 Handelstage zeigt einen Aufwärtstrend, darunter einen Abwärtstrend.",
        "w.SAISON" to "Monatsrenditen dieses Kalendermonats in der Kursgeschichte des Werts.",
        "w.LAGER" to "Lager unter dem Fünfjahresschnitt derselben Woche zeigen ein knappes Angebot, Lager darüber ein reichliches.",
        "w.BOERSENLAGER" to "Sinkende Bestände in den Lagerhäusern der Metallbörsen zeigen, dass mehr verbraucht als geliefert wird.",
        "w.TERMINKURVE" to "Ist die nahe Lieferung teurer als die spätere (Backwardation), ist die Ware jetzt knapp. Ist die spätere viel teurer (Contango), ist reichlich da.",
        "w.ENSO" to "El Niño und La Niña verschieben Regen und Temperatur in den Anbaugebieten und den Heizbedarf im Winter.",
        "w.KONJUNKTUR" to "In einer Rezession sinkt der Verbrauch von Öl und Industriemetallen.",
        "w.OPEC" to "Mehr Rohöl der OPEC+ vergrößert das Angebot, Kürzungen verknappen es.",
        "w.WELTLAGER" to "Volle Lager in den Industrieländern dämpfen den Preis, sinkende Lager stützen ihn.",

        "s.trend" to "200-Tage-Linie", "s.hoch" to "Zum Jahreshoch", "s.tief" to "Zum Jahrestief",
        "s.durchbruch" to "Ausbruch, 20 Tage", "s.oben" to "nach oben", "s.unten" to "nach unten",
        "s.vola" to "Schwankung, 20 Tage", "s.volaJahr" to "Schwankung, Jahr", "s.position" to "Spekulanten, CFTC", "s.rang" to "Rang {0} von 100",

        "o.titel" to "Optionshandel",
        "o.auf.PHYSISCH" to "Optionen auf {0}, einen {1}-ETF mit Barren im Tresor. Preise in US-Dollar je ETF-Anteil.",
        "o.auf.TERMIN" to "Optionen auf {0}, einen {1}-ETF auf Terminkontrakte. Preise in US-Dollar je ETF-Anteil.",
        "n.GOLD" to "Gold", "n.SILBER" to "Silber", "n.PLATIN" to "Platin", "n.PALLADIUM" to "Palladium", "n.OEL" to "Öl", "n.GAS" to "Erdgas",
        "n.KUPFER" to "Kupfer", "n.WEIZEN" to "Weizen", "n.MAIS" to "Mais", "n.ZUCKER" to "Zucker", "n.KORB" to "Rohstoff",

        "t.titel" to "Terminkurve", "t.lieferung" to "Lieferung", "t.preis" to "Preis", "t.abstand" to "zum Kurs", "t.jahr" to "aufs Jahr",
        "t.kern.0" to "Spätere Lieferung teurer (Contango)", "t.kern.1" to "Nahe Lieferung teurer (Backwardation)", "t.kern.2" to "Kurve flach",
        "t.info" to "Preise der nächsten Terminkontrakte. Contango: Die spätere Lieferung kostet mehr, Produkte auf Terminkontrakte verlieren beim Wechsel in den nächsten Kontrakt. Backwardation: Die nahe Lieferung kostet mehr, die Ware ist jetzt knapp.",

        "ow.titel" to "Öl weltweit", "ow.unter" to "Millionen Barrel am Tag · {0} · Änderung zum Vorjahr",
        "ow.foerderung" to "Förderung", "ow.verbrauch" to "Verbrauch", "ow.opecplus" to "Rohöl der OPEC+", "ow.frei" to "Freie Kapazität der OPEC",
        "ow.ausfall" to "Ungeplante Ausfälle", "ow.lager" to "OECD-Lager, Mio. Barrel",
        "ow.prognoseF" to "Förderung {0}, Prognose", "ow.prognoseV" to "Verbrauch {0}, Prognose",
        "ow.jeLand.F" to "Förderung je Land", "ow.jeLand.V" to "Verbrauch je Land", "ow.jeLand.O" to "Rohöl der OPEC+ je Land",
        "ow.jeLandUnter" to "{0} Regionen · {1} Länder", "ow.laender" to "{0} Länder", "ow.rest" to "Übrige",
        "ow.vorgabe" to "Vorgabe {0} Mio. Barrel/Tag, Förderung {1}",
        "ow.ueber" to "{0} Tsd. darüber",
        "ow.unterVorgabe" to "{0} Tsd. darunter",
        "ow.gleichVorgabe" to "auf der Vorgabe",
        "ow.r.NORDAMERIKA" to "Nordamerika", "ow.r.SUEDAMERIKA" to "Mittel- und Südamerika", "ow.r.EUROPA" to "Europa", "ow.r.EURASIEN" to "Eurasien",
        "ow.r.NAHOST" to "Naher Osten", "ow.r.AFRIKA" to "Afrika", "ow.r.ASIEN" to "Asien und Ozeanien",
        "ow.info" to "Monatswerte der U.S. Energy Information Administration (Short-Term Energy Outlook, Stand {0}).\n\n• Förderung: Rohöl, Kondensat, Flüssiggase, Biokraftstoffe und Raffineriegewinne\n• Rohöl der OPEC+: nur Rohöl\n• Lager: kommerzielle Bestände der OECD am Monatsende\n\nDie jüngsten Monate sind Schätzungen der EIA.",
    )

    private val EN = mapOf(
        "k.dafuer" to "{0} for", "k.dagegen" to "{0} against", "neutral" to "Neutral", "saldo" to "Net balance",
        "info" to "Each point comes from one measured figure with a fixed threshold.\n\nWeight:\n• triple: main driver (real yield for gold, stocks for oil, gas and copper)\n• double: dollar, speculators, trend, futures curve, OPEC+\n• single: all others\n\nNeutral points do not count.",

        "p.REALZINS.0" to "Real yield falling", "p.REALZINS.1" to "Real yield rising", "p.REALZINS.2" to "Real yield little changed",
        "p.INFLATION.0" to "Inflation expectations rising", "p.INFLATION.1" to "Inflation expectations falling", "p.INFLATION.2" to "Inflation expectations little changed",
        "p.DOLLAR.0" to "Dollar weaker", "p.DOLLAR.1" to "Dollar stronger", "p.DOLLAR.2" to "Dollar little changed",
        "p.POSITIONIERUNG.0" to "Speculators barely invested", "p.POSITIONIERUNG.1" to "Speculators crowded into long bets", "p.POSITIONIERUNG.2" to "Speculators not at an extreme",
        "p.TREND.0" to "Price above the 200-day line", "p.TREND.1" to "Price below the 200-day line", "p.TREND.2" to "Price at the 200-day line",
        "p.SAISON.0" to "{0} usually strong", "p.SAISON.1" to "{0} usually weak", "p.SAISON.2" to "{0} without a clear pattern",
        "p.LAGER.0" to "US stocks tight", "p.LAGER.1" to "US stocks ample", "p.LAGER.2" to "US stocks normal",
        "p.BOERSENLAGER.0" to "Exchange stocks shrinking", "p.BOERSENLAGER.1" to "Exchange stocks growing", "p.BOERSENLAGER.2" to "Exchange stocks little changed",
        "p.TERMINKURVE.0" to "Near delivery costs more", "p.TERMINKURVE.1" to "Later delivery costs much more", "p.TERMINKURVE.2" to "Futures curve flat",
        "p.ENSO.0" to "{0} tends to lift the price", "p.ENSO.1" to "{0} tends to weigh on the price",
        "p.KONJUNKTUR.0" to "US recession unlikely", "p.KONJUNKTUR.1" to "US recession risk elevated", "p.KONJUNKTUR.2" to "US recession risk moderate",
        "p.OPEC.0" to "OPEC+ pumping less", "p.OPEC.1" to "OPEC+ pumping more", "p.OPEC.2" to "OPEC+ output about flat",
        "p.WELTLAGER.0" to "OECD stocks lower than a year ago", "p.WELTLAGER.1" to "OECD stocks higher than a year ago", "p.WELTLAGER.2" to "OECD stocks as a year ago",

        "m.REALZINS" to "{0} · {1} points in 3 months", "m.INFLATION" to "{0} · {1} points in 3 months",
        "m.DOLLAR" to "Index {0} · {1} in 3 months",
        "m.POSITIONIERUNG" to "Net {0} of contracts · rank {1} of 100 in 3 years",
        "m.TREND" to "{0} to the average", "m.SAISON" to "Average {0} · up in {1} of {2} years",
        "m.LAGER" to "{0} to the five-year average", "m.BOERSENLAGER" to "{0} on the previous month",
        "m.TERMINKURVE" to "Spread {0} annualized", "m.ENSO" to "Index {0}",
        "m.KONJUNKTUR" to "{0} probability, New York Fed",
        "m.OPEC" to "{0} million barrels a day · {1} year on year", "m.WELTLAGER" to "{0} million barrels · {1} year on year",

        "w.REALZINS" to "Precious metals pay no interest. When the yield after inflation falls, holding them costs less.",
        "w.INFLATION" to "When expected inflation rises, investors seek protection in precious metals.",
        "w.DOLLAR" to "Commodities trade in dollars. A weaker dollar makes them cheaper for buyers outside the US.",
        "w.POSITIONIERUNG" to "When nearly all speculators are already long, new buyers are missing and a setback hits harder. When few are in, there is room for buying.",
        "w.TREND" to "A price above the average of the last 200 trading days shows an uptrend, below it a downtrend.",
        "w.SAISON" to "Monthly returns of this calendar month in the price history of the instrument.",
        "w.LAGER" to "Stocks below the five-year average of the same week show tight supply, stocks above it ample supply.",
        "w.BOERSENLAGER" to "Falling stocks in the warehouses of the metal exchanges show that more is used than delivered.",
        "w.TERMINKURVE" to "When near delivery costs more than later delivery (backwardation), supply is tight now. When later delivery costs much more (contango), supply is ample.",
        "w.ENSO" to "El Niño and La Niña shift rain and temperature in growing regions and heating demand in winter.",
        "w.KONJUNKTUR" to "In a recession, use of oil and industrial metals falls.",
        "w.OPEC" to "More OPEC+ crude adds supply, cuts tighten it.",
        "w.WELTLAGER" to "Full stocks in industrial countries dampen the price, falling stocks support it.",

        "s.trend" to "200-day line", "s.hoch" to "To the 52-week high", "s.tief" to "To the 52-week low",
        "s.durchbruch" to "Breakout, 20 days", "s.oben" to "upwards", "s.unten" to "downwards",
        "s.vola" to "Volatility, 20 days", "s.volaJahr" to "Volatility, year", "s.position" to "Speculators, CFTC", "s.rang" to "Rank {0} of 100",

        "o.titel" to "Options trading",
        "o.auf.PHYSISCH" to "Options on {0}, a {1} ETF backed by bars in a vault. Prices in US dollars per ETF share.",
        "o.auf.TERMIN" to "Options on {0}, a {1} ETF holding futures. Prices in US dollars per ETF share.",
        "n.GOLD" to "gold", "n.SILBER" to "silver", "n.PLATIN" to "platinum", "n.PALLADIUM" to "palladium", "n.OEL" to "oil", "n.GAS" to "natural gas",
        "n.KUPFER" to "copper", "n.WEIZEN" to "wheat", "n.MAIS" to "corn", "n.ZUCKER" to "sugar", "n.KORB" to "commodity",

        "t.titel" to "Futures curve", "t.lieferung" to "Delivery", "t.preis" to "Price", "t.abstand" to "to price", "t.jahr" to "annualized",
        "t.kern.0" to "Later delivery costs more (contango)", "t.kern.1" to "Near delivery costs more (backwardation)", "t.kern.2" to "Curve flat",
        "t.info" to "Prices of the next futures contracts. Contango: later delivery costs more, products on futures lose when rolling into the next contract. Backwardation: near delivery costs more, supply is tight now.",

        "ow.titel" to "Oil worldwide", "ow.unter" to "Million barrels a day · {0} · change year on year",
        "ow.foerderung" to "Production", "ow.verbrauch" to "Consumption", "ow.opecplus" to "OPEC+ crude", "ow.frei" to "OPEC spare capacity",
        "ow.ausfall" to "Unplanned outages", "ow.lager" to "OECD stocks, million barrels",
        "ow.prognoseF" to "Production {0}, forecast", "ow.prognoseV" to "Consumption {0}, forecast",
        "ow.jeLand.F" to "Production by country", "ow.jeLand.V" to "Consumption by country", "ow.jeLand.O" to "OPEC+ crude by country",
        "ow.jeLandUnter" to "{0} regions · {1} countries", "ow.laender" to "{0} countries", "ow.rest" to "Others",
        "ow.vorgabe" to "Target {0} mb/d, output {1}",
        "ow.ueber" to "{0} kb/d above",
        "ow.unterVorgabe" to "{0} kb/d below",
        "ow.gleichVorgabe" to "on target",
        "ow.r.NORDAMERIKA" to "North America", "ow.r.SUEDAMERIKA" to "Central and South America", "ow.r.EUROPA" to "Europe", "ow.r.EURASIEN" to "Eurasia",
        "ow.r.NAHOST" to "Middle East", "ow.r.AFRIKA" to "Africa", "ow.r.ASIEN" to "Asia and Oceania",
        "ow.info" to "Monthly figures of the U.S. Energy Information Administration (Short-Term Energy Outlook, as of {0}).\n\n• Production: crude oil, condensate, natural gas liquids, biofuels and refinery gains\n• OPEC+ crude: crude oil only\n• Stocks: commercial inventories of the OECD at month end\n\nThe latest months are EIA estimates.",
    )

    // Alle Schluessel beider Sprachen (der Test fordert, dass keine Sprache einen Schluessel auslaesst)
    fun schluessel(deutsch: Boolean): Set<String> = (if (deutsch) DE else EN).keys
}
