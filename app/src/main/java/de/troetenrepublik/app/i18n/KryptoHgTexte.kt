package de.troetenrepublik.app.i18n

// Texte zum Hintergrund der Kryptowerte, 0.78: Finanzierungssatz, offene Kontrakte, Stablecoins, gebundenes Kapital.
// Ein Schluessel je Text, Platzhalter {0}, {1} fuer Werte.
internal object KryptoHgTexte {

    fun text(deutsch: Boolean, key: String, w: List<String> = emptyList()): String {
        var t = (if (deutsch) DE else EN)[key] ?: return ""
        w.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
        return t
    }

    // Alle Schluessel einer Sprache (der Test vergleicht beide Sprachen)
    fun schluessel(deutsch: Boolean): Set<String> = (if (deutsch) DE else EN).keys

    private val DE = mapOf(
        "fin.titel" to "Finanzierungssatz",
        "fin.satz8h" to "{0} je 8 Std.",
        "fin.jahr" to "{0} p. a.",
        "fin.offen" to "Offene Kontrakte",
        "fin.offen.kern" to "Offene Kontrakte {0}",
        "fin.verlauf" to "Tagesmittel, aufs Jahr gerechnet · sechs Wochen",
        "fin.kontrakt" to "Unbefristeter Kontrakt bei {0}",
        "fin.info" to "Unbefristete Kontrakte (Perpetual Futures) haben kein Verfallsdatum. Der Finanzierungssatz hält ihren Preis am Kassakurs.\n" +
            "• Positiver Satz: Der Kontrakt notiert über dem Kassakurs. Käufer (Long) zahlen an Verkäufer (Short).\n" +
            "• Negativer Satz: Der Kontrakt notiert unter dem Kassakurs. Verkäufer zahlen an Käufer.\n" +
            "• Hoher positiver Satz: starke Nachfrage nach Kaufpositionen mit Hebel. Halten kostet laufend Geld. Fällt der Kurs, werden mehr dieser Positionen zwangsweise geschlossen.\n" +
            "• Aufs Jahr gerechnet: Satz je Stunde mal 8.760, ohne Zinseszins. 0,01 % je 8 Stunden entsprechen rund 11 % im Jahr.\n" +
            "• Offene Kontrakte (Open Interest): Wert aller Kontrakte, die an dieser Börse noch nicht geschlossen sind. Steigen sie mit dem Kurs, kommt neues Geld in den Markt. Fallen sie, werden Positionen aufgelöst.\n" +
            "• Die Zahlen stammen von einer einzelnen Börse.",
        "st.titel" to "Stablecoins im Umlauf",
        "st.info" to "Stablecoins sind Kryptowerte, die an eine Währung gebunden sind, meist an den Dollar.\n" +
            "• Der Umlauf zeigt, wie viel Geld im Kryptomarkt bereitliegt.\n" +
            "• Wachsender Umlauf: Geld fließt in den Markt.\n" +
            "• Schrumpfender Umlauf: Geld wird in Währungen zurückgetauscht.\n" +
            "• Gezählt sind alle Stablecoins, umgerechnet in Dollar.",
        "tvl.titel" to "Gebundenes Kapital (TVL)",
        "tvl.kette" to "Gebundenes Kapital auf {0} (TVL)",
        "tvl.info" to "TVL (Total Value Locked) ist der Wert aller Kryptowerte, die in dezentralen Finanzanwendungen (DeFi) hinterlegt sind: Kreditplattformen, Handelsplätze, Staking.\n" +
            "• Der Wert steigt, wenn mehr hinterlegt wird oder die Kurse der hinterlegten Werte steigen.\n" +
            "• Je Kette: wie stark die Kette für solche Anwendungen genutzt wird.\n" +
            "• Fällt der Wert bei stabilen Kursen, wird Kapital abgezogen.",
        "tage30" to "{0} in 30 Tagen",
        "hg.fin" to "Finanzierung {0} p. a.",
        "hg.tvl" to "TVL {0} in 30 Tagen",
        "jahr" to "Verlauf über ein Jahr",
    )

    private val EN = mapOf(
        "fin.titel" to "Funding rate",
        "fin.satz8h" to "{0} per 8 h",
        "fin.jahr" to "{0} p.a.",
        "fin.offen" to "Open interest",
        "fin.offen.kern" to "Open interest {0}",
        "fin.verlauf" to "Daily average, annualized · six weeks",
        "fin.kontrakt" to "Perpetual contract at {0}",
        "fin.info" to "Perpetual futures have no expiry date. The funding rate keeps their price close to the spot price.\n" +
            "• Positive rate: the contract trades above spot. Buyers (long) pay sellers (short).\n" +
            "• Negative rate: the contract trades below spot. Sellers pay buyers.\n" +
            "• High positive rate: strong demand for leveraged long positions. Holding them costs money continuously. If the price falls, more of these positions are liquidated.\n" +
            "• Annualized: hourly rate times 8,760, without compounding. 0.01% per 8 hours is about 11% a year.\n" +
            "• Open interest: value of all contracts on this exchange that are not yet closed. Rising with the price, new money enters the market. Falling, positions are being closed.\n" +
            "• The figures come from a single exchange.",
        "st.titel" to "Stablecoins in circulation",
        "st.info" to "Stablecoins are crypto assets pegged to a currency, mostly the dollar.\n" +
            "• The supply shows how much money is ready to be used in the crypto market.\n" +
            "• Growing supply: money flows into the market.\n" +
            "• Shrinking supply: money is exchanged back into currencies.\n" +
            "• All stablecoins are counted, converted to dollars.",
        "tvl.titel" to "Total value locked (TVL)",
        "tvl.kette" to "Total value locked on {0} (TVL)",
        "tvl.info" to "TVL (total value locked) is the value of all crypto assets deposited in decentralized finance (DeFi) applications: lending platforms, exchanges, staking.\n" +
            "• The value rises when more is deposited or the prices of the deposited assets rise.\n" +
            "• Per chain: how heavily the chain is used for such applications.\n" +
            "• A decline while prices are stable means capital is being withdrawn.",
        "tage30" to "{0} in 30 days",
        "hg.fin" to "Funding {0} p.a.",
        "hg.tvl" to "TVL {0} in 30 days",
        "jahr" to "One-year history",
    )
}
