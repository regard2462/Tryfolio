package de.troetenrepublik.app.i18n

import de.troetenrepublik.app.data.Lang

// Texte zu Strom in Europa (Länderblatt und Zugang für den ENTSO-E-Token). Ein Schlüssel je Text, Platzhalter {0} für Werte.
object StromTexte {

    fun text(deutsch: Boolean, key: String, w: List<String> = emptyList()): String {
        var t = (if (deutsch) DE else EN)[key] ?: return ""
        w.forEachIndexed { i, v -> t = t.replace("{$i}", v) }
        return t
    }

    // In der Sprache der App.
    fun t(key: String, vararg w: String): String = text(I18n.lang == Lang.DE, key, w.toList())

    // Alle Schlüssel einer Sprache (der Test vergleicht beide Sprachen)
    fun schluessel(deutsch: Boolean): Set<String> = (if (deutsch) DE else EN).keys

    private val DE = mapOf(
        "titel" to "Strom",
        "preis.heute" to "Großhandelspreis heute",
        "preis.am" to "Großhandelspreis am {0}",
        "morgen" to "Großhandelspreis morgen",
        "vortag" to "Zum Vortag",
        "schnitt" to "Zum 30-Tage-Schnitt",
        "spanne" to "Tiefster bis höchster Wert des Tages",
        "spanne.wert" to "{0} bis {1} €/MWh",
        "verlauf" to "Tagesmittel, letzte 30 Tage",
        "einheit" to "€/MWh",
        "ohne" to "Strompreise mit ENTSO-E-Token sichtbar",
        "keine" to "Für dieses Land liegen keine Preise vor",
        "stand" to "Stand {0}",
        "info.titel" to "Großhandelspreis Strom",
        "info.preis" to "Day-Ahead-Preis: Preis im Großhandel für die Lieferung am Folgetag, an der Strombörse für die Gebotszone gebildet. Angegeben ist das Mittel aller Zeitscheiben eines Tages in Euro je Megawattstunde. Hat ein Land mehrere Zonen, zeigt die App eine, im Titel genannt. Der Preis auf der Stromrechnung enthält zusätzlich Netzentgelte, Steuern und Abgaben.",
        "m.schluessel" to "ENTSO-E hat den Token abgelehnt. Bitte unter Zugänge prüfen.",
        "m.abgelehnt" to "Die Quelle hat den Abruf abgelehnt. Die App zeigt den letzten Stand.",
        "m.limit" to "Abruflimit erreicht. Die App zeigt den letzten Stand.",
        "m.netz" to "Keine Verbindung zur Quelle. Die App zeigt den letzten Stand.",
        "m.keine" to "Die Quelle liefert gerade keine Daten.",
        "erz.titel" to "Stromerzeugung Deutschland",
        "erz.erneuerbar" to "Erneuerbare, letzte 24 Stunden",
        "erz.verbrauch" to "Stromverbrauch zuletzt",
        "erz.verbrauch24" to "Stromverbrauch, Mittel 24 Stunden",
        "erz.verlauf" to "Anteil Erneuerbare je Stunde, 7 Tage",
        "erz.bis" to "Daten bis {0}",
        "erz.info.titel" to "Erzeugung nach Quelle",
        "erz.info" to "Mittlere Leistung der letzten 24 Stunden je Quelle und ihr Anteil an der gesamten Erzeugung in Deutschland. Der Verbrauch ist die Netzlast. SMARD erhält die Daten von ENTSO-E, die Bundesnetzagentur prüft sie.",
        "gw" to "{0} GW",
        "q.pv" to "Photovoltaik",
        "q.windOn" to "Wind an Land",
        "q.windOff" to "Wind auf See",
        "q.wasser" to "Wasserkraft",
        "q.biomasse" to "Biomasse",
        "q.sonstErneuerbar" to "Sonstige Erneuerbare",
        "q.gas" to "Erdgas",
        "q.braunkohle" to "Braunkohle",
        "q.steinkohle" to "Steinkohle",
        "q.pumpspeicher" to "Pumpspeicher",
        "q.sonstKonventionell" to "Sonstige Konventionelle",
        "quelle.entsoe" to "ENTSO-E Transparency Platform",
        "quelle.smard" to "Bundesnetzagentur | SMARD.de (CC BY 4.0)",
        "zg.name" to "ENTSO-E (Strompreise Europa)",
        "zg.zweck" to "Großhandelspreise für Strom in 26 Ländern Europas: Tageswert und 30 Tage Verlauf im Länderblatt.",
        "zg.punkte" to "Kostenlose Anmeldung auf der Transparency Platform, den Zugang zur Schnittstelle fragst du beim Support an\n" +
            "Der Token steht danach unter „My Account Settings“\n" +
            "Der Token geht nur an web-api.tp.entsoe.eu\n" +
            "Die App ruft je Land höchstens alle 3 Stunden ab, bis zu 20 je Minute und 600 am Tag (die Plattform erlaubt 400 je Minute)\n" +
            "Die Daten stammen von den Übertragungsnetzbetreibern und werden mit Quellenangabe gezeigt",
        "zg.feld" to "Token",
        "zg.holen" to "Anmeldung bei ENTSO-E",
        "zg.anleitung" to "Anleitung zur Schnittstelle",
        "zg.stand" to "Abrufe in den letzten 24 Stunden: {0} von {1}",
    )

    private val EN = mapOf(
        "titel" to "Electricity",
        "preis.heute" to "Wholesale price today",
        "preis.am" to "Wholesale price on {0}",
        "morgen" to "Wholesale price tomorrow",
        "vortag" to "Vs yesterday",
        "schnitt" to "Vs 30-day average",
        "spanne" to "Lowest to highest value of the day",
        "spanne.wert" to "{0} to {1} €/MWh",
        "verlauf" to "Daily average, last 30 days",
        "einheit" to "€/MWh",
        "ohne" to "Power prices visible with an ENTSO-E token",
        "keine" to "No prices available for this country",
        "stand" to "As of {0}",
        "info.titel" to "Wholesale electricity price",
        "info.preis" to "Day-ahead price: the wholesale price for delivery on the next day, set on the power exchange for the bidding zone. Shown is the average of all time slices of a day in euros per megawatt hour. If a country has several zones, the app shows one, named in the title. The price on a power bill also includes grid fees, taxes and levies.",
        "m.schluessel" to "ENTSO-E rejected the token. Please check it under Access.",
        "m.abgelehnt" to "The source rejected the request. The app shows the last data.",
        "m.limit" to "Request limit reached. The app shows the last data.",
        "m.netz" to "No connection to the source. The app shows the last data.",
        "m.keine" to "The source has no data right now.",
        "erz.titel" to "Power generation Germany",
        "erz.erneuerbar" to "Renewables, last 24 hours",
        "erz.verbrauch" to "Power consumption, latest",
        "erz.verbrauch24" to "Power consumption, 24-hour average",
        "erz.verlauf" to "Renewable share per hour, 7 days",
        "erz.bis" to "Data until {0}",
        "erz.info.titel" to "Generation by source",
        "erz.info" to "Average output of the last 24 hours per source and its share of total generation in Germany. Consumption is the grid load. SMARD receives the data from ENTSO-E, the Bundesnetzagentur checks it.",
        "gw" to "{0} GW",
        "q.pv" to "Solar",
        "q.windOn" to "Onshore wind",
        "q.windOff" to "Offshore wind",
        "q.wasser" to "Hydro",
        "q.biomasse" to "Biomass",
        "q.sonstErneuerbar" to "Other renewables",
        "q.gas" to "Natural gas",
        "q.braunkohle" to "Lignite",
        "q.steinkohle" to "Hard coal",
        "q.pumpspeicher" to "Pumped storage",
        "q.sonstKonventionell" to "Other conventional",
        "quelle.entsoe" to "ENTSO-E Transparency Platform",
        "quelle.smard" to "Bundesnetzagentur | SMARD.de (CC BY 4.0)",
        "zg.name" to "ENTSO-E (European power prices)",
        "zg.zweck" to "Wholesale power prices in 26 European countries: latest value and 30-day history on the country sheet.",
        "zg.punkte" to "Free registration on the Transparency Platform, you request access to the interface from support\n" +
            "The token is then under “My Account Settings”\n" +
            "The token goes only to web-api.tp.entsoe.eu\n" +
            "The app requests each country at most every 3 hours, up to 20 a minute and 600 a day (the platform allows 400 a minute)\n" +
            "The data comes from the transmission system operators and is shown with attribution",
        "zg.feld" to "Token",
        "zg.holen" to "Register at ENTSO-E",
        "zg.anleitung" to "Guide to the interface",
        "zg.stand" to "Requests in the last 24 hours: {0} of {1}",
    )
}
