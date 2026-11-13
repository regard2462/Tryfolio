package de.troetenrepublik.app.i18n

import de.troetenrepublik.app.data.Lang

// Texte des Bausteins „Staatsgeld“ im Block „Unternehmen“. Ein Schluessel je Text, Platzhalter {0}, {1} fuer Werte.
internal object StaatsgeldTexte {

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
        "titel" to "Staatsgeld",
        "kern.zwoelf" to "{0} in 12 Monaten",
        "kern.keine" to "Nichts in den letzten 12 Monaten",
        "kern.anteil" to "{0} vom Umsatz",
        "kern.anteil.klein" to "unter 0,1 % vom Umsatz",
        "kern.unten" to "Aufträge {0}, Zuschüsse {1}",
        "kern.unten.auftraege" to "Aufträge {0}",
        "kern.unten.zuschuesse" to "Zuschüsse {0}",
        "verlauf" to "Pro Jahr",
        "auftraege" to "Aufträge",
        "zuschuesse" to "Zuschüsse und Zahlungen",
        "kredite" to "Kredite",
        "kredite.hinweis" to "Nennwert, nicht in der Summe",
        "q.USA" to "USA",
        "q.USA.hinweis" to "USAspending",
        "q.TED" to "EU, öffentliche Aufträge",
        "q.TED.hinweis" to "TED",
        "q.UK" to "Großbritannien",
        "q.UK.hinweis" to "Contracts Finder",
        "q.EU" to "EU-Haushalt {0}",
        "q.EU.hinweis" to "Förderung der EU-Kommission",
        "auftraggeber" to "Größte Auftraggeber",
        "programme" to "Größte Programme",
        "groesste" to "Größte Aufträge",
        "info" to "USA: zugesagte Beträge laut USAspending (Aufträge, Zuschüsse, Direktzahlungen) für die Muttergesellschaft samt Töchtern. Kredite zählen nicht in die Summe.\n" +
            "TED (EU) und Contracts Finder (Großbritannien): nur Zuschläge, die die Firma allein bekam.\n" +
            "EU-Haushalt: Zuschüsse und Zahlungen der Kommission nach Jahr der Zusage, ohne Aufträge, die TED schon zählt.\n" +
            "Die Beträge sind zugesagt und fallen oft über mehrere Jahre als Umsatz an. Der Anteil am Umsatz erscheint nur, wenn er höchstens 100 % beträgt. Fremdwährungen sind zum heutigen Kurs umgerechnet. Bei Töchtern unter eigenem Namen kann die Summe zu niedrig sein.",
        "q.usa" to "USAspending.gov",
        "q.ted" to "TED, Tenders Electronic Daily (Europäische Union)",
        "q.uk" to "Contracts Finder, Open Government Licence v3.0",
        "q.eu" to "Financial Transparency System, Europäische Kommission, CC BY 4.0",
    )

    private val EN = mapOf(
        "titel" to "Government money",
        "kern.zwoelf" to "{0} in 12 months",
        "kern.keine" to "Nothing in the last 12 months",
        "kern.anteil" to "{0} of revenue",
        "kern.anteil.klein" to "under 0.1% of revenue",
        "kern.unten" to "Contracts {0}, grants {1}",
        "kern.unten.auftraege" to "Contracts {0}",
        "kern.unten.zuschuesse" to "Grants {0}",
        "verlauf" to "Per year",
        "auftraege" to "Contracts",
        "zuschuesse" to "Grants and payments",
        "kredite" to "Loans",
        "kredite.hinweis" to "Face value, not in the total",
        "q.USA" to "USA",
        "q.USA.hinweis" to "USAspending",
        "q.TED" to "EU, public contracts",
        "q.TED.hinweis" to "TED",
        "q.UK" to "United Kingdom",
        "q.UK.hinweis" to "Contracts Finder",
        "q.EU" to "EU budget {0}",
        "q.EU.hinweis" to "Funding by the European Commission",
        "auftraggeber" to "Largest buyers",
        "programme" to "Largest programs",
        "groesste" to "Largest contracts",
        "info" to "USA: committed amounts per USAspending (contracts, grants, direct payments) for the parent company and its subsidiaries. Loans are not part of the total.\n" +
            "TED (EU) and Contracts Finder (UK): only awards the company won alone.\n" +
            "EU budget: grants and payments of the Commission by year of commitment, without contracts that TED already counts.\n" +
            "Amounts are committed and often turn into revenue over several years. The share of revenue is shown only when it is at most 100%. Foreign currencies are converted at today's rate. Subsidiaries under their own name can make the total too low.",
        "q.usa" to "USAspending.gov",
        "q.ted" to "TED, Tenders Electronic Daily (European Union)",
        "q.uk" to "Contracts Finder, Open Government Licence v3.0",
        "q.eu" to "Financial Transparency System, European Commission, CC BY 4.0",
    )
}
