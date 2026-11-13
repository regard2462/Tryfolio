package de.troetenrepublik.app.i18n

import de.troetenrepublik.app.data.Lang

// Texte des Abschnitts „Standorte“ auf der Wertpapierseite und der Ansicht auf dem Globus.
// Ein Schluessel je Text, Platzhalter {0}, {1} fuer Werte.
internal object StandorteTexte {

    fun text(deutsch: Boolean, key: String, w: List<String> = emptyList()): String {
        var t = (if (deutsch) DE else EN)[key] ?: return ""
        w.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
        return t
    }

    fun t(key: String, vararg w: String): String = text(I18n.lang == Lang.DE, key, w.toList())

    fun schluessel(deutsch: Boolean): Set<String> = (if (deutsch) DE else EN).keys

    private val DE = mapOf(
        "titel" to "Standorte",
        "toechter.1" to "Töchter in 1 Land",
        "toechter.n" to "Töchter in {0} Ländern",
        "standorte.n" to "Standorte in {0}",
        "nurSitz" to "Sitz in {0}",
        "risiko" to "{0} der Töchter in Ländern mit hohem Risiko",
        "steuer.1" to "1 Tochter in einem nicht kooperativen Steuergebiet",
        "steuer.n" to "{0} Töchter in nicht kooperativen Steuergebieten",
        "globus" to "Auf dem Globus zeigen",
        "globusTitel" to "Standorte von {0}",
        "sitz" to "Sitz",
        "legendeNormal" to "Töchter oder Standort",
        "hoch" to "hohes Risiko",
        "steuerGebiet" to "Steuergebiet",
        "nurStandort" to "nur als Standort genannt",
        "toechterZeile" to "{0} Töchter",
        "bericht" to "Jahresbericht {0}",
        "info" to "Töchter nach Rechtsordnung laut Exhibit 21 des Jahresberichts (SEC), GLEIF und Wikidata. Je Land zählt die höchste Zahl der Quellen, damit keine Firma doppelt gezählt wird.\n\nStandorte sind Länder, die Item 2 (Properties) des Jahresberichts nennt.\n\nHohes Risiko gilt für Länder, auf die mindestens eins zutrifft: Regierungsführung laut Weltbank im untersten Viertel, Krieg im Land bei unterdurchschnittlicher Regierungsführung, Reisehinweis der USA der Stufe 4, mindestens drei Sanktionsregime von EU und USA.\n\nNicht kooperative Steuergebiete: Liste des Rates der EU vom 9. Oktober 2026.",
        "quelleSec" to "SEC EDGAR",
        "quelleGleif" to "GLEIF (CC0)",
        "quelleWikidata" to "Wikidata (CC0)",
        "quelleSteuer" to "Rat der EU, Liste nicht kooperativer Steuergebiete (9. Oktober 2026)",
    )

    private val EN = mapOf(
        "titel" to "Locations",
        "toechter.1" to "Subsidiaries in 1 country",
        "toechter.n" to "Subsidiaries in {0} countries",
        "standorte.n" to "Sites in {0}",
        "nurSitz" to "Headquartered in {0}",
        "risiko" to "{0} of subsidiaries in high-risk countries",
        "steuer.1" to "1 subsidiary in a non-cooperative tax jurisdiction",
        "steuer.n" to "{0} subsidiaries in non-cooperative tax jurisdictions",
        "globus" to "Show on globe",
        "globusTitel" to "Locations of {0}",
        "sitz" to "Headquarters",
        "legendeNormal" to "Subsidiaries or site",
        "hoch" to "high risk",
        "steuerGebiet" to "tax jurisdiction",
        "nurStandort" to "named as a site only",
        "toechterZeile" to "{0} subsidiaries",
        "bericht" to "Annual report {0}",
        "info" to "Subsidiaries by jurisdiction according to Exhibit 21 of the annual report (SEC), GLEIF and Wikidata. Per country the highest count of the sources applies, so no company is counted twice.\n\nSites are countries named in Item 2 (Properties) of the annual report.\n\nHigh risk applies to countries where at least one of these holds: governance according to the World Bank in the bottom quarter, war in the country with below-median governance, US travel advisory level 4, at least three sanction regimes of the EU and the US.\n\nNon-cooperative tax jurisdictions: list of the Council of the EU of 9 October 2026.",
        "quelleSec" to "SEC EDGAR",
        "quelleGleif" to "GLEIF (CC0)",
        "quelleWikidata" to "Wikidata (CC0)",
        "quelleSteuer" to "Council of the EU, list of non-cooperative tax jurisdictions (9 October 2026)",
    )
}
