package de.troetenrepublik.app.i18n

// Texte der Depot-Zusaetze ab 0.78: Rueckgangstabelle und Kennzahlen gegen den Index in der Rendite des Depots. Ein
// Schluessel je Text, Platzhalter {0}, {1} fuer Werte.
internal object DepotZusatzTexte {

    fun text(deutsch: Boolean, key: String, w: List<String> = emptyList()): String {
        var t = (if (deutsch) DE else EN)[key] ?: return ""
        w.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
        return t
    }

    // Alle Schluessel einer Sprache (der Test vergleicht beide Sprachen)
    fun schluessel(deutsch: Boolean): Set<String> = (if (deutsch) DE else EN).keys

    private val DE = mapOf(
        "tage" to "{0} Tage",
        // Rueckgaenge
        "rg.titel" to "Größte Rückgänge",
        "rg.info" to "Die fünf tiefsten Rückgänge der zeitgewichteten Depotkurve.\n\n• Beginn: letzter Höchststand davor.\n• Tief: tiefster Stand.\n• Erholt: Tag, an dem der alte Höchststand wieder erreicht war.\n• Tage: vom Höchststand bis zur Erholung, sonst bis heute.\n\nEin- und Auszahlungen verschieben die Kurve nicht.",
        "rg.leer" to "Kein Rückgang ab 1 %.",
        "rg.ab" to "ab {0}",
        "rg.tief" to "Tief {0}",
        "rg.erholt" to "Erholt {0}",
        "rg.offen" to "nicht erholt",
        // Gegen den Index
        "ix.titel" to "Aufwärts- und Abwärtsanteil, Omega",
        "ix.info" to "Monatsrenditen des Depots gegen den MSCI World (ETF in Euro).\n\n• Aufwärtsanteil: mittlere Depotrendite in steigenden Indexmonaten geteilt durch die des Index. Über 100 % steigt das Depot stärker mit.\n• Abwärtsanteil: dasselbe für fallende Indexmonate. Unter 100 % fällt das Depot weniger.\n• Omega: Summe der Gewinne geteilt durch die Summe der Verluste je Monat. Über 1 überwiegen die Gewinne.\n• Gewinn zu Schmerz: Summe aller Monatsrenditen geteilt durch die Summe der Verlustmonate.\n\nWenige Monate ergeben unsichere Werte.",
        "ix.leer" to "Noch zu wenige Monate.",
        "ix.auf" to "Aufwärtsanteil",
        "ix.ab" to "Abwärtsanteil",
        "ix.monateAuf" to "{0} steigende Monate im {1}",
        "ix.monateAb" to "{0} fallende Monate im {1}",
        "ix.omega" to "Omega",
        "ix.omegaHinweis" to "Gewinne zu Verlusten je Monat",
        "ix.schmerz" to "Gewinn zu Schmerz",
        "ix.schmerzHinweis" to "Gesamtrendite zu Verlustmonaten",
    )

    private val EN = mapOf(
        "tage" to "{0} days",
        "rg.titel" to "Largest drawdowns",
        "rg.info" to "The five deepest drawdowns of the time-weighted portfolio curve.\n\n• Start: last high before the drop.\n• Low: lowest level.\n• Recovered: day the old high was reached again.\n• Days: from the high to recovery, otherwise until today.\n\nDeposits and withdrawals do not shift the curve.",
        "rg.leer" to "No drawdown of 1% or more.",
        "rg.ab" to "from {0}",
        "rg.tief" to "Low {0}",
        "rg.erholt" to "Recovered {0}",
        "rg.offen" to "not recovered",
        "ix.titel" to "Up and down capture, Omega",
        "ix.info" to "Monthly portfolio returns against the MSCI World (ETF in euros).\n\n• Up capture: mean portfolio return in rising index months divided by that of the index. Above 100% the portfolio rises more.\n• Down capture: the same for falling index months. Below 100% the portfolio falls less.\n• Omega: sum of gains divided by sum of losses per month. Above 1 gains outweigh losses.\n• Gain to pain: sum of all monthly returns divided by the sum of the losing months.\n\nFew months give uncertain values.",
        "ix.leer" to "Too few months so far.",
        "ix.auf" to "Up capture",
        "ix.ab" to "Down capture",
        "ix.monateAuf" to "{0} rising months in the {1}",
        "ix.monateAb" to "{0} falling months in the {1}",
        "ix.omega" to "Omega",
        "ix.omegaHinweis" to "Gains to losses per month",
        "ix.schmerz" to "Gain to pain",
        "ix.schmerzHinweis" to "Total return to losing months",
    )
}
