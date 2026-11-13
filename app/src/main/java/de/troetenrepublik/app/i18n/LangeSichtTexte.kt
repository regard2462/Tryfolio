package de.troetenrepublik.app.i18n

// Texte des Werkzeugs „Börsengeschichte“. Ein Schluessel je Text, Platzhalter {0}, {1} fuer Werte.
internal object LangeSichtTexte {

    fun text(deutsch: Boolean, key: String, w: List<String> = emptyList()): String {
        var t = (if (deutsch) DE else EN)[key] ?: return ""
        w.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
        return HinweisTexte.einsetzen(deutsch, t)
    }

    // Alle Schluessel einer Sprache (der Test vergleicht beide Sprachen)
    fun schluessel(deutsch: Boolean): Set<String> = (if (deutsch) DE else EN).keys

    private val DE = mapOf(
        "aktien" to "Aktien",
        "aktien.usa" to "USA",
        "aktien.welt" to "Welt ohne USA",
        "mischung" to "Mischung",
        "v.A100" to "{0} Aktien",
        "v.GOLD10" to "Mit {0} Gold",
        "v.VIER" to "Permanent Portfolio",
        "v.ALLWEATHER" to "All Weather",
        "v.GOLDEN" to "Golden Butterfly",
        "n.ROHSTOFFE" to "Rohstoffe",
        "n.AKTIEN_KLEIN_WERT" to "Small-Cap-Value USA",
        "nae.kurz" to "Näherungen in dieser Mischung",
        "nae.ROH" to "Rohstoffe angenähert: Preisindex plus Geldmarktzins, ohne Rollertrag. Voll besicherte Terminkontrakte bringen etwa die Preisänderung plus den Zins der Sicherheit. Preisindex ist der Gesamtindex der Rohstoffpreise der Weltbank (Spotpreise), Zins sind Schatzwechsel mit drei Monaten.",
        "nae.MITTEL" to "Mittlere Anleihen angenähert: je zur Hälfte lange Anleihen und Geldmarkt, eine eigene Reihe gibt es nicht.",
        "nae.KURZ" to "Kurze Anleihen angenähert durch den US-Geldmarkt (Schatzwechsel).",
        "nae.KLEIN" to "Small-Cap-Value: kleine US-Unternehmen mit niedrigem Kurs im Verhältnis zum Buchwert (Portfolio SMALL HiBM aus den sechs Portfolios nach Größe und Buchwert zu Kurs), wertgewichtet. Die Daten kommen per Abruf von der Fama/French Data Library, die Mischung erscheint nach dem Laden.",
        "n.AKTIEN_USA" to "Aktien USA",
        "n.AKTIEN_WELT" to "Aktien Welt ohne USA",
        "n.ANLEIHEN" to "US-Staatsanleihen",
        "n.GELDMARKT" to "US-Geldmarkt",
        "n.GOLD" to "Gold",
        "kern" to "real pro Jahr, {0} bis {1}",
        "ende" to "Aus {0} wurden real (Kaufkraft von heute)",
        "rueckgang" to "Tiefster Rückgang, real",
        "rueckgang.erholt" to "{0} bis {1}, wieder erreicht {2} nach {3}",
        "rueckgang.offen" to "{0} bis {1}, bis {2} nicht wieder erreicht",
        "halte.titel" to "Haltedauern",
        "halte.kurz" to "{0}: realer Verlust in {1} der Zeiträume",
        "halte.wahl" to "Haltedauer",
        "halte.min" to "Schlechtester Zeitraum",
        "halte.q1" to "Schlechtere 25 % enden unter",
        "halte.median" to "Mitte aller Zeiträume",
        "halte.q3" to "Beste 25 % enden über",
        "halte.max" to "Bester Zeitraum",
        "halte.verlust" to "Zeiträume mit realem Verlust",
        "halte.faelle" to "{0} Zeiträume, real pro Jahr",
        "hinweis" to "Rückrechnung, {hk}. Real heißt nach Abzug der Inflation. Die Anteile werden am Ende " +
            "jedes Jahres wiederhergestellt, ohne Kosten und Steuern. Rückgang und Haltedauern zählen Jahresendstände, Einbrüche " +
            "innerhalb eines Jahres fehlen. Aktien, Anleihen und Geldmarkt der USA in US-Dollar. Welt ohne USA ist das Mittel " +
            "aus bis zu {0} Industrieländern, jedes in eigener Währung. Gold in US-Dollar, jeweils Werte zum Jahresende.",
        "leer" to "Für diese Mischung fehlen gemeinsame Jahre.",
        "leer.mehr" to "Für diese Mischungen fehlen gemeinsame Jahre.",
        "t.erholt" to "Jahre bis wieder erreicht",
        "t.offen" to "offen",
        "t.verlust" to "Realer Verlust nach {0}",
        "t.zeitraum" to "{0} bis {1}",
        "halte.kurz.mehr" to "{0}: realer Verlust, {1}",
    )

    private val EN = mapOf(
        "aktien" to "Stocks",
        "aktien.usa" to "USA",
        "aktien.welt" to "World ex USA",
        "mischung" to "Mix",
        "v.A100" to "{0} stocks",
        "v.GOLD10" to "With {0} gold",
        "v.VIER" to "Permanent Portfolio",
        "v.ALLWEATHER" to "All Weather",
        "v.GOLDEN" to "Golden Butterfly",
        "n.ROHSTOFFE" to "commodities",
        "n.AKTIEN_KLEIN_WERT" to "US small-cap value",
        "nae.kurz" to "Approximations in this mix",
        "nae.ROH" to "Commodities approximated: price index plus money market interest, without roll yield. Fully collateralized futures earn roughly the price change plus the interest on the collateral. The price index is the World Bank total commodity price index (spot prices), interest is the 3-month Treasury bill.",
        "nae.MITTEL" to "Intermediate bonds approximated: half long bonds and half money market, there is no series of their own.",
        "nae.KURZ" to "Short bonds approximated by the US money market (Treasury bills).",
        "nae.KLEIN" to "Small-cap value: small US companies with a low price relative to book value (portfolio SMALL HiBM from the six portfolios by size and book-to-market), value-weighted. The data come by retrieval from the Fama/French Data Library, the mix appears after loading.",
        "n.AKTIEN_USA" to "US stocks",
        "n.AKTIEN_WELT" to "World ex USA stocks",
        "n.ANLEIHEN" to "US government bonds",
        "n.GELDMARKT" to "US money market",
        "n.GOLD" to "gold",
        "kern" to "real per year, {0} to {1}",
        "ende" to "{0} grew in real terms (today's purchasing power) to",
        "rueckgang" to "Deepest decline, real",
        "rueckgang.erholt" to "{0} to {1}, regained {2} after {3}",
        "rueckgang.offen" to "{0} to {1}, not regained by {2}",
        "halte.titel" to "Holding periods",
        "halte.kurz" to "{0}: real loss in {1} of the periods",
        "halte.wahl" to "Holding period",
        "halte.min" to "Worst period",
        "halte.q1" to "Worst 25% end below",
        "halte.median" to "Middle of all periods",
        "halte.q3" to "Best 25% end above",
        "halte.max" to "Best period",
        "halte.verlust" to "Periods with a real loss",
        "halte.faelle" to "{0} periods, real per year",
        "hinweis" to "Back-calculation, {hk}. Real means after inflation. The weights are restored at the end " +
            "of each year, without costs and taxes. Decline and holding periods count year-end values, drops within a year are " +
            "missing. US stocks, bonds and money market in US dollars. World ex USA is the mean of up to {0} developed " +
            "countries, each in its own currency. Gold in US dollars, each at year end.",
        "leer" to "This mix has no years in common.",
        "leer.mehr" to "These mixes have no years in common.",
        "t.erholt" to "Years to recover",
        "t.offen" to "open",
        "t.verlust" to "Real loss after {0}",
        "t.zeitraum" to "{0} to {1}",
        "halte.kurz.mehr" to "{0}: real loss, {1}",
    )
}
