package de.troetenrepublik.app.i18n

// Uebersetzungstabellen fuer Sektoren, Branchen und Laender, wie Yahoo Finance sie liefert.
// Unbekannte Schluessel bleiben englisch.
object Sectors {

    val de: Map<String, String> = mapOf(
        // Sektoren
        "Technology" to "Technologie", "Healthcare" to "Gesundheit", "Financial Services" to "Finanzdienstleistungen",
        "Consumer Cyclical" to "Zyklischer Konsum", "Consumer Defensive" to "Basiskonsum", "Industrials" to "Industrie",
        "Communication Services" to "Kommunikation", "Energy" to "Energie", "Basic Materials" to "Grundstoffe",
        "Real Estate" to "Immobilien", "Utilities" to "Versorger",
        // Branchen (Auswahl der haeufigsten)
        "Software - Application" to "Anwendungssoftware", "Software - Infrastructure" to "Infrastruktursoftware",
        "Semiconductors" to "Halbleiter", "Semiconductor Equipment & Materials" to "Halbleiterausrüstung",
        "Consumer Electronics" to "Unterhaltungselektronik", "Computer Hardware" to "Computerhardware",
        "Information Technology Services" to "IT-Dienstleistungen", "Electronic Components" to "Elektronische Bauteile",
        "Communication Equipment" to "Kommunikationstechnik", "Scientific & Technical Instruments" to "Mess- und Prüftechnik",
        "Internet Content & Information" to "Internetinhalte & Information", "Internet Retail" to "Onlinehandel",
        "Entertainment" to "Unterhaltung", "Telecom Services" to "Telekommunikation", "Advertising Agencies" to "Werbung",
        "Electronic Gaming & Multimedia" to "Spiele & Multimedia", "Publishing" to "Verlage", "Broadcasting" to "Rundfunk",
        "Auto Manufacturers" to "Automobilhersteller", "Auto Parts" to "Automobilzulieferer", "Auto & Truck Dealerships" to "Autohandel",
        "Footwear & Accessories" to "Schuhe & Accessoires", "Apparel Retail" to "Bekleidungshandel", "Apparel Manufacturing" to "Bekleidungsherstellung",
        "Restaurants" to "Gastronomie", "Specialty Retail" to "Fachhandel", "Home Improvement Retail" to "Baumärkte",
        "Luxury Goods" to "Luxusgüter", "Leisure" to "Freizeit", "Lodging" to "Hotels", "Travel Services" to "Reisedienstleistungen",
        "Residential Construction" to "Wohnungsbau", "Furnishings, Fixtures & Appliances" to "Einrichtung & Haushaltsgeräte",
        "Beverages - Non-Alcoholic" to "Alkoholfreie Getränke", "Beverages - Brewers" to "Brauereien", "Beverages - Wineries & Distilleries" to "Wein & Spirituosen",
        "Household & Personal Products" to "Haushalts- & Körperpflegeprodukte", "Packaged Foods" to "Verpackte Lebensmittel",
        "Discount Stores" to "Discounter", "Grocery Stores" to "Lebensmittelhandel", "Tobacco" to "Tabak", "Confectioners" to "Süßwaren",
        "Drug Manufacturers - General" to "Pharmahersteller", "Drug Manufacturers - Specialty & Generic" to "Spezial- & Generikahersteller",
        "Biotechnology" to "Biotechnologie", "Medical Devices" to "Medizintechnik", "Medical Instruments & Supplies" to "Medizinische Instrumente",
        "Diagnostics & Research" to "Diagnostik & Forschung", "Healthcare Plans" to "Krankenversicherer", "Medical Care Facilities" to "Kliniken & Pflege",
        "Banks - Diversified" to "Universalbanken", "Banks - Regional" to "Regionalbanken", "Credit Services" to "Kreditdienstleister",
        "Capital Markets" to "Kapitalmärkte", "Asset Management" to "Vermögensverwaltung", "Insurance - Diversified" to "Versicherungen",
        "Insurance - Life" to "Lebensversicherung", "Insurance - Property & Casualty" to "Sachversicherung", "Insurance - Reinsurance" to "Rückversicherung",
        "Financial Data & Stock Exchanges" to "Finanzdaten & Börsen", "Insurance Brokers" to "Versicherungsmakler",
        "Aerospace & Defense" to "Luftfahrt & Verteidigung", "Specialty Industrial Machinery" to "Maschinenbau", "Industrial Distribution" to "Industriegroßhandel",
        "Integrated Freight & Logistics" to "Logistik", "Airlines" to "Fluggesellschaften", "Railroads" to "Bahn", "Farm & Heavy Construction Machinery" to "Land- & Baumaschinen",
        "Electrical Equipment & Parts" to "Elektrotechnik", "Building Products & Equipment" to "Bauprodukte", "Engineering & Construction" to "Bau & Ingenieurwesen",
        "Conglomerates" to "Mischkonzerne", "Staffing & Employment Services" to "Personaldienstleister", "Waste Management" to "Entsorgung",
        "Rental & Leasing Services" to "Vermietung & Leasing", "Security & Protection Services" to "Sicherheitsdienste",
        "Oil & Gas Integrated" to "Öl & Gas (integriert)", "Oil & Gas E&P" to "Öl- & Gasförderung", "Oil & Gas Midstream" to "Öl & Gas (Transport)",
        "Oil & Gas Refining & Marketing" to "Raffinerien", "Oil & Gas Equipment & Services" to "Ölfeldausrüstung", "Uranium" to "Uran", "Solar" to "Solar",
        "Chemicals" to "Chemie", "Specialty Chemicals" to "Spezialchemie", "Steel" to "Stahl", "Gold" to "Gold", "Copper" to "Kupfer",
        "Other Industrial Metals & Mining" to "Industriemetalle & Bergbau", "Other Precious Metals & Mining" to "Edelmetalle & Bergbau", "Agricultural Inputs" to "Agrarchemie",
        "Building Materials" to "Baustoffe", "Paper & Paper Products" to "Papier", "Packaging & Containers" to "Verpackungen",
        "Utilities - Diversified" to "Versorger", "Utilities - Renewable" to "Erneuerbare Energien", "Utilities - Regulated Electric" to "Stromversorger",
        "Utilities - Regulated Gas" to "Gasversorger", "Utilities - Independent Power Producers" to "Unabhängige Stromerzeuger",
        "REIT - Residential" to "Wohnimmobilien-REIT", "REIT - Office" to "Büroimmobilien-REIT", "REIT - Retail" to "Handelsimmobilien-REIT",
        "REIT - Industrial" to "Logistikimmobilien-REIT", "Real Estate Services" to "Immobiliendienstleistungen", "Real Estate - Development" to "Projektentwicklung",
        // Eigene Schluessel fuer ETFs und Sonstiges
        "World" to "Welt", "Emerging Markets" to "Schwellenländer", "USA" to "USA", "Germany" to "Deutschland", "Clean Energy" to "Saubere Energie",
        "Crypto" to "Krypto", "Index" to "Index",
    )

    val countriesDe: Map<String, String> = mapOf(
        "United States" to "Vereinigte Staaten", "Germany" to "Deutschland", "United Kingdom" to "Vereinigtes Königreich", "France" to "Frankreich",
        "Netherlands" to "Niederlande", "Switzerland" to "Schweiz", "Austria" to "Österreich", "Denmark" to "Dänemark", "Sweden" to "Schweden",
        "Norway" to "Norwegen", "Finland" to "Finnland", "Spain" to "Spanien", "Italy" to "Italien", "Belgium" to "Belgien", "Ireland" to "Irland",
        "Luxembourg" to "Luxemburg", "Japan" to "Japan", "China" to "China", "Hong Kong" to "Hongkong", "Taiwan" to "Taiwan", "South Korea" to "Südkorea",
        "India" to "Indien", "Canada" to "Kanada", "Australia" to "Australien", "Brazil" to "Brasilien", "Mexico" to "Mexiko", "Israel" to "Israel",
        "Singapore" to "Singapur", "Poland" to "Polen", "Czech Republic" to "Tschechien", "Portugal" to "Portugal", "Greece" to "Griechenland",
        "Cayman Islands" to "Kaimaninseln", "Bermuda" to "Bermuda", "Jersey" to "Jersey", "Saudi Arabia" to "Saudi-Arabien", "United Arab Emirates" to "Vereinigte Arabische Emirate",
        "South Africa" to "Südafrika", "Russia" to "Russland", "Turkey" to "Türkei", "Argentina" to "Argentinien", "Chile" to "Chile",
    )
}
