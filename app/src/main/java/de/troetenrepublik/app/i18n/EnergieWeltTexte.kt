package de.troetenrepublik.app.i18n

// Texte zu Oel und Erdgas je Land (ui/EnergieWeltKarte.kt, engine/EnergieWelt.kt). Ein Schluessel je Text, Platzhalter {0},
// {1} ... fuer Werte. Endung .OEL oder .GAS je nach Seite.
internal object EnergieWeltTexte {

    fun text(deutsch: Boolean, key: String, w: List<String> = emptyList()): String {
        var t = (if (deutsch) DE else EN)[key] ?: return ""
        w.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
        return t
    }

    fun schluessel(deutsch: Boolean): Set<String> = (if (deutsch) DE else EN).keys

    private val DE = mapOf(
        "titel.OEL" to "Öl nach Land", "titel.GAS" to "Erdgas nach Land",
        "kern.OEL" to "{0} fördert am meisten Öl: {1} Mio. Barrel am Tag ({2} der Welt). Die zehn größten Förderer liefern {3}.",
        "kern.GAS" to "{0} fördert am meisten Erdgas: {1} Mrd. m³ im Jahr ({2} der Welt). Die zehn größten Förderer liefern {3}.",
        "f.OEL" to "Förderung {0}, Mio. Barrel am Tag", "f.GAS" to "Förderung {0}, Mrd. m³",
        "v.OEL" to "Verbrauch {0}, Mio. Barrel am Tag", "v.GAS" to "Verbrauch {0}, Mrd. m³",
        "alleF" to "Alle Förderländer", "alleV" to "Alle Verbrauchsländer", "alleX" to "Alle Länder mit Fackeln", "laender" to "{0} Länder",
        "netto.titel" to "Ausfuhr und Einfuhr {0}", "netto.unter" to "Förderung minus Verbrauch",
        "netto.aus" to "Größte Ausfuhrländer", "netto.ein" to "Größte Einfuhrländer",
        "fackel.titel" to "Gasfackeln {0}, Mrd. m³, Schätzung", "fackel.unter" to "Aus Satellitenbildern geschätzt, keine Meldung der Betreiber",
        "info.OEL" to "Jahreswerte je Land der U.S. Energy Information Administration (International Energy Data, Stand {0}) und Schätzungen zu Gasfackeln der Earth Observation Group (Colorado School of Mines).\n\n• Öl: Rohöl und andere Flüssigkeiten, Förderung und Verbrauch\n• Anteil: an der Summe aller Länder der Datei\n• Gasfackeln: abgefackeltes Begleitgas an Ölfeldern, aus Wärmebildern der Satelliten (VIIRS Nightfire) geschätzt, nicht gemessen",
        "info.GAS" to "Jahreswerte je Land der U.S. Energy Information Administration (International Energy Data, Stand {0}) und Schätzungen zu Gasfackeln der Earth Observation Group (Colorado School of Mines).\n\n• Erdgas: trockenes Erdgas, Förderung und Verbrauch\n• Anteil: an der Summe aller Länder der Datei\n• Gasfackeln: abgefackeltes Begleitgas an Förderstellen, aus Wärmebildern der Satelliten (VIIRS Nightfire) geschätzt, nicht gemessen",
        "quelle.eia" to "U.S. Energy Information Administration (International Energy Data)",
        "quelle.eog" to "Earth Observation Group, Colorado School of Mines (CC BY-NC 4.0)",
    )

    private val EN = mapOf(
        "titel.OEL" to "Oil by country", "titel.GAS" to "Natural gas by country",
        "kern.OEL" to "{0} produces the most oil: {1} million barrels a day ({2} of the world). The ten largest producers supply {3}.",
        "kern.GAS" to "{0} produces the most natural gas: {1} bcm a year ({2} of the world). The ten largest producers supply {3}.",
        "f.OEL" to "Production {0}, million barrels a day", "f.GAS" to "Production {0}, bcm",
        "v.OEL" to "Consumption {0}, million barrels a day", "v.GAS" to "Consumption {0}, bcm",
        "alleF" to "All producing countries", "alleV" to "All consuming countries", "alleX" to "All countries with flares", "laender" to "{0} countries",
        "netto.titel" to "Exports and imports {0}", "netto.unter" to "Production minus consumption",
        "netto.aus" to "Largest net exporters", "netto.ein" to "Largest net importers",
        "fackel.titel" to "Gas flaring {0}, bcm, estimate", "fackel.unter" to "Estimated from satellite images, not reported by operators",
        "info.OEL" to "Annual figures by country from the U.S. Energy Information Administration (International Energy Data, as of {0}) and gas flaring estimates from the Earth Observation Group (Colorado School of Mines).\n\n• Oil: crude oil and other liquids, production and consumption\n• Share: of the sum of all countries in the file\n• Gas flaring: associated gas burned off at oil fields, estimated from satellite heat images (VIIRS Nightfire), not measured",
        "info.GAS" to "Annual figures by country from the U.S. Energy Information Administration (International Energy Data, as of {0}) and gas flaring estimates from the Earth Observation Group (Colorado School of Mines).\n\n• Natural gas: dry natural gas, production and consumption\n• Share: of the sum of all countries in the file\n• Gas flaring: associated gas burned off at production sites, estimated from satellite heat images (VIIRS Nightfire), not measured",
        "quelle.eia" to "U.S. Energy Information Administration (International Energy Data)",
        "quelle.eog" to "Earth Observation Group, Colorado School of Mines (CC BY-NC 4.0)",
    )
}
