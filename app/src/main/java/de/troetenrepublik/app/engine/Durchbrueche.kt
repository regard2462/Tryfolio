package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

// Durchbrueche je Thema (Medizin, KI, Quanten, Batterie, ...): Schlagzeilen aus der Google-News-Suche je Thema und aus
// frei lesbaren Wissenschaftsfeeds. Dieses Objekt kennt kein Netz: Themen mit Suchbegriffen und Boersenzuordnung,
// Filter gegen Kurs- und Analystenmeldungen, Zuordnung der Feedmeldungen zu Themen, Dubletten und Sortierung.
object Durchbrueche {

    const val GUELTIG_MS = 3_600_000L
    const val TAGE = 14
    const val JE_THEMA = 5
    // Zuletzt: aeltere Treffer eines Themas ohne neue Meldung, hoechstens ZULETZT_JE, nicht aelter als ZULETZT_TAGE
    const val ZULETZT_TAGE = 365
    const val ZULETZT_JE = 3
    // Je Thema im Speicher; die Ansicht zeigt davon hoechstens JE_THEMA
    private const val IM_SPEICHER = 8
    private const val TAG_MS = 86_400_000L

    // Ein Thema. etfs: Fonds, ueber die man das Thema anlegen kann (Detailseite per Kuerzel). englisch und deutsch: Suche
    // bei Google News. stichwoerter: Regelteile (am Wortanfang), mit denen Feedmeldungen dem Thema zugeordnet werden.
    // ohne: Titel mit diesen Woertern gehoeren nicht zum Thema.
    class Thema(
        val id: String,
        val de: String,
        val en: String,
        val etfs: List<String>,
        val englisch: String,
        val deutsch: String,
        val stichwoerter: List<String>,
        val ohne: Regex? = null,
        val frei: Regex? = null,
    ) {
        val muster: Regex = Regex("\\b(" + stichwoerter.joinToString("|") + ")", RegexOption.IGNORE_CASE)
        val stichwortMuster: List<Regex> = stichwoerter.map { Regex("\\b($it)", RegexOption.IGNORE_CASE) }
        fun name(deutsch: Boolean) = if (deutsch) de else en
    }

    private fun suche(begriffe: String, signal: String) = "($begriffe) ($signal)"

    // Die Suche fragt nur nach Zeichen echter Durchbrueche; der Filter in filtern() entscheidet danach streng.
    private const val SIGNAL_EN = "breakthrough OR \"world's first\" OR \"first-ever\" OR approves OR approved OR approval OR record OR \"phase 3\" OR milestone OR \"for the first time\""
    private const val SIGNAL_DE = "Durchbruch OR erstmals OR Zulassung OR zugelassen OR Rekord OR Meilenstein OR \"Phase-3\""

    val THEMEN: List<Thema> = listOf(
        Thema(
            "krebs", "Krebs und Immuntherapie", "Cancer and immunotherapy", listOf("XBI", "IBB"),
            suche("cancer OR tumor OR oncology OR leukemia OR lymphoma OR melanoma OR \"CAR-T\" OR \"cancer vaccine\"", SIGNAL_EN),
            suche("Krebs OR Tumor OR Onkologie OR Leukämie OR Krebsimpfung OR \"CAR-T\"", SIGNAL_DE),
            listOf("cancer", "tumou?r", "oncolog", "leuk[a]?emi", "lymphoma", "melanoma", "car-t", "carcinoma", "metasta", "immunotherap", "krebs", "onkolog", "leukämie", "leukaemie", "tumor", "immuntherap", "lymphom"),
        ),
        Thema(
            "genetik", "Genetik und Genom", "Genetics and genome", listOf("ARKG", "IDNA"),
            suche("CRISPR OR \"gene therapy\" OR \"gene editing\" OR \"base editing\" OR genome OR \"gene-edited\"", SIGNAL_EN),
            suche("CRISPR OR Gentherapie OR Genom OR Genschere OR Geneditierung", SIGNAL_DE),
            listOf("crispr", "gene therap", "gene edit", "gene[- ]edit", "base edit", "prime edit", "genom", "genetic", "genetisch", "dna sequenc", "gentherap", "genschere", "erbkrankheit"),
        ),
        Thema(
            "medizin", "Medizin", "Medicine", listOf("XBI", "IBB", "ARKG"),
            suche("vaccine OR antibiotic OR Alzheimer OR obesity OR diabetes OR \"rare disease\" OR \"FDA approves\"", SIGNAL_EN),
            suche("Impfstoff OR Antibiotikum OR Alzheimer OR Adipositas OR Diabetes OR \"seltene Erkrankung\" OR Medikament", SIGNAL_DE),
            listOf("vaccin", "mrna", "drug", "antibiotic", "alzheimer", "diabetes", "clinical trial", "therap", "patients", "obesity", "fda", "ema\\b", "dementia", "parkinson", "impfstoff", "medikament", "\\w*implantat", "patient", "klinisch", "arznei", "wirkstoff", "alzheimer", "abnehm", "malaria", "european commission", "eu-kommission", "hiv", "hepatitis"),
        ),
        Thema(
            "ki", "Künstliche Intelligenz", "Artificial intelligence", listOf("AIQ", "IGV"),
            suche("\"AI\" OR \"artificial intelligence\" OR \"language model\"", "breakthrough OR \"world's first\" OR \"first-ever\" OR solves OR proves"),
            suche("KI OR \"Künstliche Intelligenz\"", "Durchbruch OR erstmals OR löst OR Rekord"),
            listOf("artificial intelligence", "ai\\b", "machine learning", "neural net", "language model", "llm", "chatbot", "deep learning", "generative", "ki\\b", "künstliche intelligenz", "deepmind", "alphafold", "openai", "mathematical olympiad", "mathematik-olympiade"),
            Regex("\\b(films?|movies?|games?|music|album|ads?|advertis\\w*|marketing|fashion)\\b", RegexOption.IGNORE_CASE),
        ),
        Thema(
            "quanten", "Quantentechnologie", "Quantum technology", listOf("QTUM"),
            suche("quantum OR qubit", SIGNAL_EN),
            suche("Quantencomputer OR Quantentechnologie OR Qubit OR Quanten", SIGNAL_DE),
            listOf("quantum", "qubit", "quanten", "willow"),
        ),
        Thema(
            "halbleiter", "Halbleiter und Chips", "Semiconductors and chips", listOf("SMH", "SOXX"),
            suche("semiconductor OR chip OR transistor OR lithography OR photonic", SIGNAL_EN) + " -obituary",
            suche("Halbleiter OR Chip OR Transistor OR Lithografie OR Photonik", SIGNAL_DE),
            listOf("semiconductor", "transistor", "wafer", "lithograph", "photonic", "chips?\\b", "chipmaker", "nanometer", "\\d+ ?nm\\b", "silicon", "halbleiter", "nanometer", "euv", "asml", "tsmc", "mikrochip"),
        ),
        Thema(
            "batterie", "Batterie und Speicher", "Batteries and storage", listOf("LIT", "BATT"),
            suche("battery OR \"solid-state\" OR \"sodium-ion\" OR \"energy storage\"", SIGNAL_EN) + " -fire -fires -recall",
            suche("Batterie OR Akku OR Festkörperbatterie OR \"Natrium-Ionen\" OR Energiespeicher", SIGNAL_DE),
            listOf("batter", "solid-state", "sodium-ion", "lithium", "anode", "cathode", "electrolyte", "supercapacitor", "energy storage", "akku", "festkörper", "natrium-ion", "energiespeicher"),
            Regex("\\b(fires?|blaze|recalls?|brand|brände|brennt|explod\\w*|explosion|braves|iphones?|watch|smartwatch)\\b", RegexOption.IGNORE_CASE),
        ),
        Thema(
            "energie", "Energie", "Energy", listOf("ICLN", "TAN", "URA"),
            suche("solar OR perovskite OR hydrogen OR \"nuclear reactor\" OR \"small modular reactor\" OR geothermal", SIGNAL_EN),
            suche("Solarzellen OR Perowskit OR Wasserstoff OR Kernreaktor OR Geothermie", SIGNAL_DE),
            listOf("solar", "photovoltaic", "perovskite", "hydrogen", "nuclear", "reactor", "geothermal", "electrolyzer", "renewable", "wasserstoff", "smr\\b", "solarzelle", "perowskit", "geotherm", "kraftwerk", "kernreaktor", "kernkraft"),
            Regex("first solar|mamamoo|solar (system|eclipse|flare|storm|wind)|hydrogen bomb", RegexOption.IGNORE_CASE),
        ),
        Thema(
            "fusion", "Kernfusion", "Nuclear fusion", listOf("URA", "NLR"),
            suche("\"nuclear fusion\" OR \"fusion energy\" OR tokamak OR stellarator OR \"fusion reactor\" OR \"fusion power\"", SIGNAL_EN),
            suche("Kernfusion OR Fusionsreaktor OR Tokamak OR Stellarator OR Fusionsenergie", SIGNAL_DE),
            listOf("nuclear fusion", "fusion (energy|power|reactor|plant|device)", "tokamak", "stellarator", "plasma", "iter\\b", "kernfusion", "fusionsreaktor", "fusionsenergie", "fusionsexperiment", "fusionskraftwerk", "wendelstein"),
            Regex("\\b(cuisine|restaurant|music|jazz|food|bbq)\\b", RegexOption.IGNORE_CASE),
        ),
        Thema(
            "raumfahrt", "Raumfahrt", "Space", listOf("UFO", "ARKX"),
            suche("rocket OR spacecraft OR satellite OR starship OR \"space station\" OR lunar", SIGNAL_EN),
            suche("Rakete OR Raumfahrt OR Satellit OR Raumsonde OR Mondmission OR Starship", SIGNAL_DE),
            listOf("rocket", "spacecraft", "satellite", "orbit", "lunar", "mars\\b", "starship", "space station", "spaceflight", "launch vehicle", "raumfahrt", "nasa", "esa\\b", "spacex", "planet", "asteroid", "moon", "comet", "telescope", "galax", "mond\\b", "sonde", "rakete", "satellit"),
        ),
        Thema(
            "robotik", "Robotik und Automatisierung", "Robotics and automation", listOf("BOTZ", "ROBO"),
            suche("robot OR humanoid OR robotics OR \"self-driving\" OR drone", SIGNAL_EN),
            suche("Roboter OR Humanoide OR Robotik OR Drohne OR \"autonomes Fahren\"", SIGNAL_DE),
            listOf("robot", "humanoid", "drone", "autonomous", "self-driving", "exoskeleton", "automation", "roboter", "robotaxi", "waymo", "fahrerlos", "chirurg", "operiert"),
        ),
        Thema(
            "materialien", "Materialien", "Materials", listOf("XLB", "REMX"),
            suche("\"new material\" OR graphene OR superconductor OR nanomaterial OR metamaterial OR \"carbon nanotube\"", SIGNAL_EN),
            suche("Graphen OR Supraleiter OR Nanomaterial OR \"neues Material\" OR Metamaterial", SIGNAL_DE),
            listOf("graphene", "superconduct", "nanomaterial", "nanotech", "metamaterial", "alloy", "polymer", "new material", "ceramic", "2d material", "nanotube", "aerogel", "crystal", "graphen", "supraleit", "perovskit", "metamaterial"),
        ),
        Thema(
            "klima", "Klima und Landwirtschaft", "Climate and agriculture", listOf("MOO", "PHO"),
            suche("\"carbon capture\" OR \"direct air capture\" OR desalination OR \"gene-edited\" OR \"lab-grown meat\" OR fertilizer", SIGNAL_EN),
            suche("CO2-Abscheidung OR Entsalzung OR Gentechnik OR Laborfleisch OR Dünger OR Klimatechnik", SIGNAL_DE),
            listOf("carbon capture", "direct air capture", "desalination", "crop", "agricultur", "farming", "fertili[sz]er", "drought", "cultivated meat", "lab-grown", "soil", "water purification", "co2", "climeworks", "abscheidung", "laborfleisch", "kultiviertes fleisch", "landwirtschaft"),
            Regex("\\b(court|lawsuit|ruling|ruled)\\b", RegexOption.IGNORE_CASE),
        ),
        Thema(
            "kommunikation", "Kommunikation und Netze", "Communication and networks", listOf("IYZ", "FIVG"),
            suche("\"6G\" OR \"satellite internet\" OR \"optical fiber\" OR \"laser communication\" OR \"quantum network\"", SIGNAL_EN),
            suche("\"6G\" OR Satelliteninternet OR Glasfaser OR Mobilfunk OR \"Wi-Fi\"", SIGNAL_DE),
            listOf("6g\\b", "5g\\b", "wireless", "broadband", "optical fib", "laser communication", "satellite internet", "starlink", "wi-fi", "glasfaser", "mobilfunk", "satellit", "smartphone", "terabit", "tbit", "tbps", "fiber"),
        ),
        // Themen der uebrigen Zeilen in Maerkte, Trends (0.79): gleiche Quelle wie oben (Google-News-Suche je Thema und
        // Stichwoerter fuer die Wissenschaftsfeeds), eigene Ausnahmen je Thema. frei: Woerter, die hier das Thema selbst sind
        // und beim Filtern nicht als Produkt- oder Spielmeldung zaehlen.
        Thema(
            "software", "Software", "Software", listOf("IGV"),
            suche("\"open-source\" OR compiler OR \"programming language\" OR \"formal verification\" OR \"operating system\" OR \"cloud computing\"", SIGNAL_EN),
            suche("Open-Source OR Programmiersprache OR Betriebssystem OR Cloud-Computing OR \"formale Verifikation\"", SIGNAL_DE),
            listOf("open[- ]source", "compiler", "programming language", "formal verification", "operating system", "software", "programmiersprache", "betriebssystem", "linux"),
            Regex("\\b(games?|gaming|movies?|film|music)\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(apps?|saas|crm|erp)\\b", RegexOption.IGNORE_CASE),
        ),
        Thema(
            "cyber", "Cybersicherheit", "Cybersecurity", listOf("CIBR"),
            suche("cybersecurity OR ransomware OR \"post-quantum cryptography\" OR encryption OR \"zero-trust\" OR passkeys", SIGNAL_EN),
            suche("Cybersicherheit OR IT-Sicherheit OR Ransomware OR Verschlüsselung OR \"Post-Quanten-Kryptografie\"", SIGNAL_DE),
            listOf("cyber", "ransomware", "encryption", "post-quantum", "cryptograph", "zero-trust", "passkey", "verschlüsselung", "it-sicherheit", "kryptograf", "hacker"),
        ),
        Thema(
            "spiele", "Spiele", "Gaming", listOf("ESPO"),
            suche("\"video game\" OR \"game engine\" OR \"cloud gaming\" OR esports OR console", SIGNAL_EN),
            suche("Videospiel OR Spielekonsole OR \"Cloud-Gaming\" OR Spiel-Engine OR eSports", SIGNAL_DE),
            listOf("video games?", "gaming", "game engine", "esports", "console", "playstation", "xbox", "nintendo", "steam\\b", "videospiel", "spielekonsole", "spiele-engine"),
            Regex("\\b(trailers?|gamescom|release dates?|dlc|reviews?|olympi\\w*|football|soccer|nba|nfl|fifa)\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(games?|gaming|esports?|consoles?)\\b", RegexOption.IGNORE_CASE),
        ),
        Thema(
            "konsum", "Elektroautos und Konsumgüter", "Electric vehicles and consumer goods", listOf("XLY"),
            suche("\"electric vehicle\" OR \"electric car\" OR \"EV charging\" OR \"fast charging\" OR \"smart glasses\"", SIGNAL_EN),
            suche("Elektroauto OR Schnellladen OR Ladeinfrastruktur OR \"smarte Brille\" OR Reichweite", SIGNAL_DE),
            listOf("electric (vehicle|car)", "ev\\b", "fast charging", "ev charging", "smart glasses", "elektroauto", "e-auto", "schnellladen", "ladeinfrastruktur", "reichweite"),
            Regex("\\b(recalls?|rückruf\\w*|crash\\w*|unfall|unfälle)\\b", RegexOption.IGNORE_CASE),
        ),
        Thema(
            "basiskonsum", "Lebensmittel und Ernährung", "Food and nutrition", listOf("XLP"),
            suche("\"plant-based\" OR \"precision fermentation\" OR \"alternative protein\" OR \"food waste\" OR sweetener OR \"sugar substitute\"", SIGNAL_EN),
            suche("Fleischersatz OR Präzisionsfermentation OR Lebensmitteltechnologie OR Lebensmittelverschwendung OR Süßstoff", SIGNAL_DE),
            listOf("plant-based", "precision fermentation", "alternative protein", "food waste", "sweetener", "sugar substitute", "food technology", "fleischersatz", "präzisionsfermentation", "lebensmitteltechnologie", "süßstoff", "lebensmittelverschwendung"),
            Regex("\\b(recalls?|outbreak|salmonella|listeria|rückruf\\w*)\\b", RegexOption.IGNORE_CASE),
        ),
        Thema(
            "abnehmen", "Abnehmmittel", "Weight-loss drugs", listOf("PPH", "XBI"),
            suche("GLP-1 OR semaglutide OR tirzepatide OR retatrutide OR orforglipron OR \"weight-loss drug\" OR obesity", SIGNAL_EN),
            suche("Abnehmspritze OR GLP-1 OR Semaglutid OR Tirzepatid OR Adipositas OR Abnehmmittel", SIGNAL_DE),
            listOf("glp-1", "semaglutide", "tirzepatide", "retatrutide", "orforglipron", "wegovy", "zepbound", "mounjaro", "ozempic", "weight[- ]loss", "obesity", "abnehm", "adipositas", "semaglutid", "tirzepatid"),
        ),
        Thema(
            "medizintechnik", "Medizintechnik", "Medical devices", listOf("IHI"),
            suche("\"medical device\" OR implant OR \"surgical robot\" OR pacemaker OR prosthesis OR \"brain-computer interface\"", SIGNAL_EN),
            suche("Medizintechnik OR Implantat OR Operationsroboter OR Herzschrittmacher OR Prothese OR \"Gehirn-Computer-Schnittstelle\"", SIGNAL_DE),
            listOf("medical device", "implant", "surgical robot", "pacemaker", "prosthe", "brain-computer", "neurostimulat", "dialysis", "insulin pump", "catheter", "medizintechnik", "herzschrittmacher", "prothese", "operationsroboter", "dialyse"),
        ),
        Thema(
            "banken", "Banken und Zahlungsverkehr", "Banking and payments", listOf("KBE"),
            suche("\"digital euro\" OR \"instant payments\" OR \"tokenized deposits\" OR CBDC OR \"payment system\" OR \"open banking\"", SIGNAL_EN),
            suche("\"digitaler Euro\" OR Echtzeitüberweisung OR Zahlungsverkehr OR Zahlungssystem OR \"Open Banking\"", SIGNAL_DE),
            listOf("open banking", "digital euro", "instant payments?", "real-time payments?", "tokenized (deposits?|bonds?)", "payment (systems?|rails?)", "cbdc", "central bank digital", "digitaler euro", "echtzeitüberweisung", "zahlungsverkehr", "zahlungssystem"),
        ),
        Thema(
            "versicherungen", "Versicherung und Risiko", "Insurance and risk", listOf("KIE"),
            suche("insurance OR reinsurance OR \"parametric insurance\" OR \"catastrophe bond\" OR \"cyber insurance\"", SIGNAL_EN),
            suche("Versicherung OR Rückversicherung OR \"parametrische Versicherung\" OR Katastrophenanleihe OR Cyberversicherung", SIGNAL_DE),
            listOf("insurance", "insurer", "reinsur", "parametric", "catastrophe bond", "cat bond", "versicher", "rückversicher", "katastrophenanleihe"),
            Regex("\\b(medicaid|medicare|obamacare|premiums?|prämien|krankenkasse\\w*)\\b", RegexOption.IGNORE_CASE),
        ),
        Thema(
            "krypto", "Krypto und Blockchain", "Crypto and blockchain", listOf("BLOK"),
            suche("bitcoin OR ethereum OR blockchain OR stablecoin OR \"smart contract\" OR \"zero-knowledge\"", SIGNAL_EN),
            suche("Bitcoin OR Ethereum OR Blockchain OR Stablecoin OR \"Smart Contract\" OR Kryptowährung", SIGNAL_DE),
            listOf("bitcoin", "ethereum", "blockchain", "stablecoin", "smart contract", "zero-knowledge", "layer[- ]2", "solana", "krypto", "kryptowährung"),
            Regex("\\b(prices?|kurs\\w*|preis\\w*|rally|surges?|crash\\w*)\\b|\\$\\s?\\d", RegexOption.IGNORE_CASE),
        ),
        Thema(
            "oel", "Öl und Gas", "Oil and gas", listOf("XLE"),
            suche("\"oil and gas\" OR LNG OR \"offshore drilling\" OR \"oil discovery\" OR \"gas discovery\" OR \"enhanced oil recovery\"", SIGNAL_EN),
            suche("Erdgas OR Erdöl OR LNG OR Offshore-Bohrung OR Gasfund OR Ölfund", SIGNAL_DE),
            listOf("oil\\b", "oil (and|&) gas", "lng\\b", "offshore drilling", "oil discovery", "gas discovery", "oilfield", "oil field", "natural gas", "crude", "fracking", "erdöl", "erdgas", "ölfund", "gasfund", "bohrung"),
            Regex("\\b(prices?|opec\\+?|ölpreis\\w*|gaspreis\\w*|spritpreis\\w*|benzinpreis\\w*)\\b", RegexOption.IGNORE_CASE),
        ),
        Thema(
            "solar", "Solar", "Solar", listOf("TAN"),
            suche("\"solar cell\" OR perovskite OR \"solar panel\" OR \"tandem cell\" OR photovoltaic OR \"solar module\"", SIGNAL_EN),
            suche("Solarzelle OR Perowskit OR Photovoltaik OR Solarmodul OR Solarpanel", SIGNAL_DE),
            listOf("solar (cell|panel|module|farm|power|energy)", "photovoltaic", "perovskite", "tandem cell", "solarzelle", "solarmodul", "photovoltaik", "perowskit", "solarpanel"),
            Regex("first solar|mamamoo|solar (system|eclipse|flare|storm|wind)", RegexOption.IGNORE_CASE),
        ),
        Thema(
            "wasserstoff", "Wasserstoff", "Hydrogen", listOf("ICLN"),
            suche("\"green hydrogen\" OR electrolyzer OR \"fuel cell\" OR \"hydrogen production\" OR \"hydrogen storage\"", SIGNAL_EN),
            suche("\"grüner Wasserstoff\" OR Elektrolyseur OR Brennstoffzelle OR Wasserstoffspeicher OR Wasserstoffproduktion", SIGNAL_DE),
            listOf("hydrogen", "electrolyz", "electrolys", "fuel cell", "wasserstoff", "elektrolyse", "brennstoffzelle"),
            Regex("hydrogen bomb|hydrogen peroxide|hindenburg|wasserstoffbombe", RegexOption.IGNORE_CASE),
        ),
        Thema(
            "versorger", "Strom und Netze", "Power and grids", listOf("XLU"),
            suche("\"power grid\" OR \"grid storage\" OR \"transmission line\" OR \"smart grid\" OR \"electricity grid\"", SIGNAL_EN),
            suche("Stromnetz OR Netzspeicher OR Übertragungsnetz OR \"Smart Grid\" OR Stromversorgung", SIGNAL_DE),
            listOf("power grid", "smart grid", "grid storage", "grid[- ]scale", "transmission line", "electricity grid", "utility-scale", "stromnetz", "übertragungsnetz", "netzspeicher", "stromversorgung"),
            Regex("\\b(outage|blackout|strompreis\\w*|electricity prices?|power prices?)\\b", RegexOption.IGNORE_CASE),
        ),
        Thema(
            "industrie", "Industrie und Fertigung", "Industry and manufacturing", listOf("XLI"),
            suche("\"3D printing\" OR \"additive manufacturing\" OR \"industrial robot\" OR \"smart factory\" OR \"green steel\" OR \"green cement\"", SIGNAL_EN),
            suche("\"3D-Druck\" OR \"additive Fertigung\" OR Industrieroboter OR \"grüner Stahl\" OR Fertigungstechnik", SIGNAL_DE),
            listOf("3d[- ]print", "additive manufactur", "industrial robot", "smart factory", "green steel", "green cement", "3d-druck", "additive fertigung", "industrieroboter", "grüner stahl", "fertigungstechnik", "industrie 4\\.0"),
        ),
        Thema(
            "ruestung", "Rüstung und Luftfahrt", "Aerospace and defense", listOf("ITA"),
            suche("hypersonic OR \"missile defense\" OR \"fighter jet\" OR \"combat drone\" OR \"laser weapon\" OR \"electric aircraft\" OR \"sustainable aviation fuel\"", SIGNAL_EN),
            suche("Hyperschall OR Raketenabwehr OR Kampfjet OR Kampfdrohne OR Laserwaffe OR Luftverteidigung OR Elektroflugzeug", SIGNAL_DE),
            listOf("hypersonic", "missile defen[cs]e", "fighter (jet|aircraft)", "combat drone", "laser weapon", "air defen[cs]e", "supersonic", "electric aircraft", "evtol", "aviation fuel", "hyperschall", "raketenabwehr", "kampfjet", "luftverteidigung", "laserwaffe", "kampfdrohne", "elektroflugzeug"),
            Regex("\\b(killed|attacks?|invasion|ceasefire|casualties|air ?show|getötet|angriff\\w*)\\b", RegexOption.IGNORE_CASE),
        ),
        Thema(
            "metalle", "Metalle und Bergbau", "Metals and mining", listOf("XME"),
            suche("\"critical minerals\" OR \"rare earth\" OR copper OR \"mineral discovery\" OR \"metal recycling\" OR \"urban mining\"", SIGNAL_EN),
            suche("\"Seltene Erden\" OR \"kritische Rohstoffe\" OR Kupfer OR Rohstofffund OR Bergbau OR Metallrecycling", SIGNAL_DE),
            listOf("rare earth", "critical minerals?", "copper", "mining", "metal recycling", "nickel", "cobalt", "titanium", "seltene erden", "kritische rohstoffe", "kupfer", "rohstofffund", "bergbau", "metallrecycling"),
            Regex("\\b(prices?|preis\\w*|export bans?|exportstopp)\\b", RegexOption.IGNORE_CASE),
        ),
        Thema(
            "goldminen", "Gold und Goldminen", "Gold and gold mining", listOf("GDX"),
            suche("\"gold discovery\" OR \"gold mine\" OR \"gold deposit\" OR \"gold recovery\" OR \"gold mining\"", SIGNAL_EN),
            suche("Goldfund OR Goldmine OR Goldvorkommen OR Goldabbau OR Goldgewinnung", SIGNAL_DE),
            listOf("gold (discovery|deposit|mine|mining|find|nugget|recovery)", "goldfund", "goldmine", "goldvorkommen", "goldabbau", "goldgewinnung"),
            Regex("\\bg/t\\b|\\b(intersects?|drill\\w*|bohrergebnis\\w*|gold prices?|goldpreis\\w*|ounces?|oz|medal\\w*|olympi\\w*|medaille\\w*)\\b", RegexOption.IGNORE_CASE),
        ),
        Thema(
            "immobilien", "Immobilien und Bauen", "Real estate and construction", listOf("XLRE"),
            suche("\"3D-printed home\" OR \"mass timber\" OR \"modular construction\" OR \"construction robot\" OR \"low-carbon concrete\" OR \"green cement\"", SIGNAL_EN),
            suche("\"3D-gedruckt\" OR Holzbau OR \"modulares Bauen\" OR Bautechnik OR \"grüner Zement\" OR Gebäudetechnik", SIGNAL_DE),
            listOf("3d[- ]printed (house|home|building)", "mass timber", "cross-laminated", "modular (construction|home)", "construction robot", "green cement", "low-carbon (cement|concrete)", "concrete", "holzbau", "bautechnik", "zement", "beton", "gebäudetechnik"),
            Regex("\\b(mortgages?|home prices?|house prices?|rents?|immobilienpreis\\w*|mietpreis\\w*|hypothek\\w*)\\b", RegexOption.IGNORE_CASE),
        ),
    )

    fun thema(id: String): Thema? = THEMEN.firstOrNull { it.id == id }

    // Feeds mit Wissenschaftsmeldungen, frei lesbar, nur Titel, Link, Datum und Quelle werden uebernommen.
    class Feed(val name: String, val adresse: String)

    val FEEDS = listOf(
        Feed("ScienceDaily", "https://www.sciencedaily.com/rss/all.xml"),
        Feed("Phys.org", "https://phys.org/rss-feed/breaking/"),
        Feed("Medical Xpress", "https://medicalxpress.com/rss-feed/breaking/"),
        Feed("MIT Technology Review", "https://www.technologyreview.com/feed/"),
    )

    // Suche bei Google News: Begriffe des Themas, nur die letzten 14 Tage, ohne Boersenwoerter.
    fun suchbegriff(t: Thema, deutsch: Boolean): String = (if (deutsch) t.deutsch else t.englisch) + " -stock -shares -ETF when:${TAGE}d"

    // Weite Suche fuer Themen ohne neue Meldung: dieselben Begriffe ueber ein Jahr, fuer „Zuletzt“.
    fun suchbegriffWeit(t: Thema, deutsch: Boolean): String = (if (deutsch) t.deutsch else t.englisch) + " -stock -shares -ETF when:${ZULETZT_TAGE}d"

    // Eine Meldung wie sie aus einem Abruf kommt. thema leer bei den Feeds (Zuordnung nach Stichwoertern).
    class Roh(val thema: String?, val titel: String, val link: String, val zeit: Long, val quelle: String)

    @Serializable
    class Meldung(val thema: String, val titel: String, val link: String, val zeit: Long, val quelle: String)

    class Gruppe(val thema: Thema, val meldungen: List<Meldung>)

    // Filter [Durchbrueche 0.73]
    // Ein Durchbruch ist ein eingetretenes Ereignis: Zulassung eines Mittels, erste Anwendung, bestaetigter Rekord, Phase-3-
    // Erfolg, Erstflug oder erster Betrieb, Fachjournal mit messbarem Ergebnis. Ankuendigungen, Plaene, Meinungen, Kurse,
    // Finanzierung, Produktwerbung, Tarif- und Sportmeldungen und Statusvergaben der Behoerden zaehlen nicht. Die Entscheidung
    // fallen in zwei Stufen: erst die Ausschluesse (gelten immer), dann ein Zeichen fuer ein eingetretenes Ereignis.

    private fun rx(s: String) = Regex(s, RegexOption.IGNORE_CASE)

    // Kurse, Analysten, Finanzierung, Unternehmensmeldungen. Geldbetraege zaehlen nur mit Finanzierungswort.
    private val LAERM = rx(
        """\b(stocks?|etfs?|nasdaq|nyse|dow jones|s&p|wall street|analysts?|upgrades?|downgrades?|earnings|dividends?|buy rating|price targets?|to buy|should you|""" +
            """investors?|investing|investments?|market (size|share|to reach|forecast|report|cap|value)|cagr|rall(y|ies)|soars?|plunges?|valuation|ipo|goes public|""" +
            """funding|seed round|series [a-e]|short sellers?|bullish|bearish|penny|revenues?|guidance|quarterly|q[1-4] (results|20\d\d)|record high|all-time high|""" +
            """share price|shares (rise|rises|fall|falls|jump|jumps|surge|surges|slump|slumps|soar|soars|tumble|tumbles|climb|climbs|hit|hits|drop|drops|gain|gains)|""" +
            """raises?\b.{0,25}\b(million|billion|funding|capital)|raised\b.{0,25}\b(million|billion)|secures?\b.{0,40}\b(million|billion|funding|financing|investment)|""" +
            """invests?|invested|acquires?|acquisition|merger|buyout|takeover|deal|ceo|cfo|chief executive|appoints?|appointed|steps down|lays? off|layoffs|job cuts|""" +
            """cuts? [\d,.]+ jobs|jobs|aktien?|\w*aktien?|kursziel|analysten?|dax|börse|anleger|kurs|kurssprung|kursrally|rekordhoch|übernimmt|übernahme|stellenabbau|""" +
            """streicht|finanzierung|court|lawsuit|sues|obituary|nachruf)\b""",
    )

    // Routine-Finanzmeldungen (Zahlen, Beteiligungen, Kapitalmassnahmen, Zinsen). Gilt zusaetzlich zu laerm() und zu den
    // Ausschluessen des Themas, aber nur fuer die Wirtschaftsthemen in WIRTSCHAFT; Medizin, KI, Raumfahrt u. a. bleiben unberuehrt.
    private val ROUTINE = rx(
        """\b(net (profit|income|loss)|profits?|earnings|revenues?|turnover|quarter(ly)?|first (three|six|nine) months|dividends?|shares|stakes?|puts|call options?|""" +
            """calls (rise|rises|jump|jumps|surge|surges)|price targets?|rights issue|bond (sale|issue|offering)|rate (hike|hikes|cut|cuts|decision)|interest rates?|ipo|""" +
            """gewinn\w*|umsatz\w*|quartal\w*|ergebnis\w*|dividende\w*|aktie\w*|anteil\w*|kursziel\w*|kapitalerhöhung\w*|anleihe\w*|leitzins\w*|zinsentscheid\w*|""" +
            """zinserhöhung\w*|zinssenkung\w*|zinsen)\b""",
    )

    // Themen, bei denen Routine-Finanzmeldungen kein Durchbruch sind
    private val WIRTSCHAFT = setOf(
        "banken", "versicherungen", "krypto", "oel", "metalle", "goldminen", "immobilien", "industrie", "versorger", "konsum", "basiskonsum",
    )

    // Boersen- und Finanzportale
    private val LAERM_QUELLEN = rx(
        "motley fool|zacks|seeking alpha|benzinga|marketbeat|investopedia|investorplace|simply wall st|wall st\\.|barron|tipranks|thestreet|gurufocus|insider monkey|" +
            "stocktwits|finanzen\\.|boerse\\.de|börse|der aktionär|wallstreet-online|aktiencheck|investor's business|24/7 wall",
    )

    // Pressemitteilungsdienste: dort stehen Zulassungen zuerst, sonst nur Eigenwerbung
    private val PRESSE_QUELLEN = rx("globenewswire|prnewswire|pr newswire|business wire|einpresswire|ein presswire|ein news|accesswire|newsfile|cision")

    // Fragen, Ratgeber, Meinung, Veranstaltungen
    private val MEINUNG = rx(
        """\?\s*["”']?\s*$|\b(what is|what are|what to know|need to know|explained|explainer|guide|tips|best|top \d+|ranked|opinion|commentary|editorial|podcast|weekly|""" +
            """roundup|newsletter|the download|roundtables?|webinar|conference|summit|expo|keynote|meet the|how (to|does|do)|here'?s|ratgeber|tipps?|meinung|kommentar|""" +
            """gastbeitrag|wie funktioniert|das müssen sie wissen|das sollten sie wissen|hintergrund|interview|umfrage|survey|poll|experts?|experten?|expertin)\b""",
    )

    // Ankuendigungen, Plaene, Aussagen Dritter, Erwartungen
    private val SPEKULATION = rx(
        """\b(says?|said|claims?|predicts?|expects?|expected|anticipated|pending|awaits?|forecasts?|plans? (to|for)|planned|planning|aims? to|hopes?|wants? to|vows?|promises?|""" +
            """pledges?|will|set to|poised|eyeing|next year|coming (soon|next)|years? away|decades? away|""" +
            """remains? (years|decades|far|elusive)|announces? plans|bets? on|looks to|ready to|""" +
            """sagt|erwartet|prognostiziert|prognose|plant\b(?=.{0,60}\b(den|die|das|dem|ein|eine|einen|bis|für|mit|neue\w*|ab)\b)|geplant|will|wollen|soll|sollen|kündigt\b.{0,40}\ban|ankündig\w*|verspricht|hofft|hoffnung|bald|in zukunft|""" +
            """noch viele fragen)\b|\b(by|in|until|bis|ab) (20(2[7-9]|[3-9]\d))\b""",
    )

    // Hinweis auf Unsicheres: zaehlt nur vor dem Zeichen fuer das Ereignis
    private val HEDGE = rx(
        """\b(could|might|would|should|maybe|perhaps|suggests?|appears?|seems?|promising|possibly|likely|könnte|könnten|dürfte|womöglich|vielleicht|möglicherweise|""" +
            """angeblich|vermutlich|vielversprechend)\b|(?-i:\bmay\b)""",
    )

    // Verfahrensschritte, Statusvergaben, Rueckschlaege, Sicherheit
    private val STATUS = rx(
        """\b(completes? (enrol\w*|dosing|recruitment)|enrol\w*|doses? first|first (patients?|participants?|subjects?) (dosed|enrolled)|first patient dosed|submits?|submitted|""" +
            """submission|accepted for review|accepts? (the )?(application|bla|nda|filing)|priority review|decision (date|expected)|pdufa|files? (for|a|an)|filing|bla|nda|ind|""" +
            """clinical hold|designation|fast track|orphan drug|rmat|breakthrough (therapy|device|designation|prize|energy|ventures|initiative|fund)|""" +
            """grants? (priority|fast|orphan|designation|status|funding)|topline|phase[- ]?(1|2|i{1,2})[ab]?|phase 1/2|delays?|delayed|postpones?|verschoben|verschiebt|""" +
            """scrapp?s?|halts?|pauses|cancels?|cancelled|setback|fails?|failed|failure|rückschlag|gescheitert|gestoppt|abgesagt|dies|death|dead|fatal|crash|recalls?|""" +
            """safety (concerns|risk)|warns?|warnt|investigat\w*|probe|fire|fires|blaze|brände|brennt|explod\w+|beantragt|antrag|zulassungsantrag|seeks approval)\b""",
    )

    // Produkte, Handel, Unterhaltung, Sport, Wetter, oeffentliche Betreiber, Eroeffnungen
    private val PRODUKT = rx(
        """\b((new|latest) (smartphone|phone)s?|foldables?|iphones?|galaxy z|watch|smartwatch|laptops?|headphones?|earbuds|tv|handy|fitness|trackers?|wearables?|apps?|crm|erp|saas|""" +
            """ai-powered|ai-driven|ai-first|ai-native|flagship|retailers?|supermarkt|restaurants?|cuisine|recipes?|fashion|taxi|tarif\w*|abo|subscriptions?|marketing|""" +
            """advertis\w*|e-commerce|loyalty|rewards|merch|movies?|albums?|concerts?|festival|gaming|celebrity|singer|grimes|star in|box office|sprinters?|marathon|""" +
            """runners?|swimmers?|olympic\w*|league|cup|striker|fifa|nba|nfl|tennis|golf|athletes?|esports?|tickets?|deliveries|registrations|neuzulassungen|sales|""" +
            """shipments|\w*absatz|verkaufszahlen|auslieferungen|\w*umsatz|\w*ernte|hottest|warmest|rainfall|iaa|ifa|ces|mwc|messe|ribbon|groundbreaking|grundsteinlegung|""" +
            """spatenstich|eröffnet|eröffnung|inaugurat\w+|bahn|gleis\w*|stadt|gemeinde|kommune|polizei|bundeswehr|ministerium|landkreis|airline|council|county)\b|""" +
            """\bopens?\b.{0,40}\b(store|shop|plant|facility|factory|office|center|centre|campus|station|tankstelle|werk|fabrik)\b|\bgames?\b(?!-)|(?<!thin )(?<!thin-)\bfilms?\b""",
    )

    // Politik, Verhandlungen, Tarife
    private val POLITIK = rx(
        """\b(gesetz\w*|bundestag|bundesregierung|regierung|koalition|haushalt|verhandl\w+|negotiations?|talks|strike|streik|wages?|tarif\w*|lohn|merz|scholz|election|""" +
            """förder\w*|subsid\w*|tariffs?|zölle?|sanctions?|verordnung|fristen|deadline)\b""",
    )

    // Tierversuche und Labormodelle: kein Durchbruch fuer Menschen
    private val TIER = rx("""\b(mice|mouse|rats?|monkeys?|zebrafish|preclinical|in vitro|mäuse|maus|ratten|tierversuch\w*|fruit flies|worms?)\b""")

    // Zeichen fuer ein eingetretenes Ereignis

    // Zulassung durch eine Behoerde (nicht: Etikett, Erweiterung, Nachahmer)
    private val ZULASSUNG_EN = rx(
        """\b(fda|ema|mhra|nmpa|pmda|health canada|tga|swissmedic|ce mark|nrc|faa|european commission|eu commission|eu regulators?|who|ecb|european central bank)\b.{0,50}\b(approv\w+|authori[sz]\w+|prequalif\w+|licen[sc]\w+)\b|""" +
            """\b(approv\w+|authori[sz]\w+|licen[sc]ed)\b.{0,40}\bby (the )?(fda|ema|mhra|nmpa|nrc|faa|european commission|ecb|european central bank)\b|\b(fda|ema|eu)\b.{0,10}\bapproval\b""",
    )
    private val ZULASSUNG_AUSNAHME = rx(
        """\b(label\w*|update|updated|expanded|expansion|supplemental|feature|formulation|packaging|generic|biosimilars?|interchangeab\w+|pathway|framework|guidance|rules?|policy|programs?|standards?|dosing|extended|new strength|autoinjector|prefilled|flavou?rs?|trial)\b""",
    )
    private val ZULASSUNG_DE = rx("""\b(zugelassen|genehmigt|lässt\b.{0,50}\bzu|erteilt(e)? (die )?zulassung|erhält (die )?(eu-)?zulassung|zulassung (erteilt|für))""")
    private val ARZNEI_DE = rx("""\w*(medikament|arzneimittel|wirkstoff|therapie|impfstoff|mittel|präparat|behandlung|antikörper|pille|spritze|krebs|alzheimer|gentherapie|immuntherapie)\w*""")
    private val ZULASSUNG_DE_AUSNAHME = rx("""\b(biosimilars?|generik\w*|nachahmer\w*|beantragt|antrag)\b""")

    private val ERSTMALS = rx(
        """\bworld'?s first\b|\bworld[- ]first\b|\bfirst[- ]ever\b|\bfor the first time\b|\bfirst\b(?! (solar|trust|quarter|half|week|day|days|look|lady|minister|round|time buyers?|impressions?|responders?|aid|class|name|place|choice|steps?|things?|hand|republic|\d))|""" +
            """\bweltweit erste\w*|\berstmals\b|\bzum ersten mal\b|\berstmalig\b|\berste[rsnm]?\b(?! (hilfe|quartal|halbjahr|woche|tage?|runde|liga|klasse|eindruck|eindrücke|blick|schritte?|mal|platz|plätze|spiel|bank|\d))""",
    )
    private val REKORD = rx(
        """\bworld[- ]records?\b|\brecord[- ](breaking|setting|efficiency|speed|high-energy|capacity|density|power|yield|distance)\b|""" +
            """\b(sets?|setting|breaks?|broke|breaking|smash\w*|shatters?|achieves?|reach\w*|hits?|holds?)\s+(a |an |the |new |another |historic |all-time |world )*records?\b|""" +
            """\b\w*rekord\w*\b""",
    )
    private val DURCHBRUCH = rx("""\b(breakthrough|durchbruch|durchbrüche)\b""")
    private val PHASE3 = rx("""\bphase[- ]?(3|iii)\b""")
    private val POSITIV = rx(
        """\b(met|meets|hit|hits|achiev\w+|positive|success\w*|improv\w+|doubles?|halves|reduc\w+|extends?|prolongs?|superior|wins?|lowers?|cuts?|senkt|verlängert|verbessert|""" +
            """erreicht\w*|wirksam|erfolg\w*|positiv\w*|überleben\w*|überlebens\w*|survival)\b""",
    )
    private val PHASE3_START = rx("""\b(begins?|starts?|launch\w*|enrol\w*|recruit\w*|plans?|kündigt|startet|beginn\w*|plant|design|designed|rekrutier\w*|aufnahme)\b""")
    private val ENDPUNKT = rx(
        """\bprimary (end ?points?)\b.{0,30}\b(met|achiev\w+|reached)\b|\b(met|meets|achiev\w+|reached) (its |the |both |all )?primary (end ?points?)\b|\b(doubles?|halves|extends?|improves?) (overall )?survival\b""",
    )
    private val JOURNAL_TITEL = rx("""\b(published in|in|via) (nature|science|cell|nejm|the lancet|lancet|jama|pnas)\b|\bpeer-reviewed (nature|science) paper\b|\bnature paper\b""")
    private val JOURNAL_QUELLE = rx("""^(nature( [a-z]+)?|science( [a-z ]+)?|cell|the lancet|lancet( [a-z]+)?|nejm|the new england journal of medicine|jama|pnas)$""")
    private val ERGEBNIS = rx("""\b(shows?|reveals?|demonstrates?|achiev\w+|reduces?|improves?|enables?|overcomes?|discover\w*|restor\w+|cures?|eliminates?|first|erstmals)\b""")
    private val GELUNGEN = rx(
        """\bsuccessful(ly)? (landing|launch|test|flight|docking|catch|demonstrat\w*|treat\w*|cure\w*)\b|\bsuccessfully (landed|launched|tested|docked|caught|demonstrated|treated|cured|changes?|altered)\b|""" +
            """\berfolgreich (gelandet|gestartet|getestet)\b""",
    )
    // Konkrete Leistungen ohne Wort wie „erstes“ oder „Rekord“
    private val LEISTUNG = rx(
        """\b(catches|caught)\b.{0,30}\b(booster|starship|rocket)\b|\bwins? (a )?gold\b|\bgold medal\b|\brestores? (hearing|sight|vision|movement|speech|the ability)\b|\bcures?\b|\bcured\b|""" +
            """\bheals?\b|\breverses\b|\beliminates\b|\bsolves?\b.{0,40}\b(problem|computation|puzzle|mystery|equation|task)\b|\bholds? plasma\b|\bchanges? (an )?asteroid|""" +
            """\blandet erfolgreich\b|\bheilt\b|\bgeheilt\b|\blös(t|en)\b.{0,40}\b(rätsel|aufgabe|problem)\b|\bholt\b.{0,25}\bgold\w*|\bgoldmedaille\b|\bfängt\b.{0,40}\bauf\b|""" +
            """\bplasma hält\b|\bhält plasma\b|\b(launch\w*|starts?|begins?|switch\w* on)\b.{0,40}\bsatellite\b.{0,30}\b(service|texting|messaging|internet|connectivity|calls?)\b|\b(builds?|built|assembles?|demonstrates?|bauen|baut|gebaut)\b.{0,50}\b\d[\d.,]{3,}\s?-?(qubits|atoms|atome)\b|\b(contains?|enthält|umfasst|covers?|now has|jetzt)\b.{0,30}\b\d[\d.,]*\s?(million|millionen|billion|milliarden)\b.{0,30}\b\w*(structures|strukturen|proteins|proteine|genomes|genome|species|arten|galaxies|galaxien|stars|sterne|compounds|stoffe|materials|materialien)\b|\bgelungen\b|\bgelingt\b|\bgeglückt\b|\blässt .{0,25}\b(hören|sehen|gehen|sprechen)\b|\bspricht wieder\b""",
    )
    // Inbetriebnahme: nur mit Zusatz, dass es der erste, groesste oder ein oeffentlicher Betrieb ist
    private val BETRIEB = rx(
        """\bin betrieb( genommen)?\b|\bnimmt\b.{0,50}\bin betrieb\b|\bgoes? (online|live)\b|\bbegins? (commercial )?operations?\b|\bswitch(es|ed)? on\b|\bcommercial operations?\b|""" +
            """\b(opens?|launch\w*|begins?|starts?|rolls? out)\b.{0,50}\b(commercial|public|robotaxi|driverless)\b|\bfahrerlos\w*.{0,30}\b(dienst|betrieb|service|angebot)\b""",
    )
    private val BETRIEB_MASSSTAB = rx("""\b(first|world'?s (largest|first|biggest)|largest|fully|erste\w*|weltgrößte\w*|größte\w*|weltweit|public|öffentlich\w*|alle nutzer|alle kunden|for everyone)\b""")
    private val SERIE = rx("""\bserienfertigung\b|\bmass[- ]production\b|\bmass-produced\b|\bserienreif\w*|\bfirst commercial\b|\berster kommerzieller\b""")

    // Mittleres Zeichen: zaehlt nur bei Forschungsquellen
    private val MITTEL = rx("""\b(overcom\w+|solve[sd]?|cracks?|cracked|cures?|cured|milestone|meilenstein|gelöst|rätsel)\b""")
    private val FORSCHUNG = rx(
        """\b(science ?daily|eurekalert|phys\.org|science x|medical xpress|scitechdaily|newswise|mit news|mit technology review|nature|science|cell|the lancet|quanta|ieee spectrum|""" +
            """ars technica|new scientist|sciencealert|live science)\b""",
    )
    private val FORSCHER = rx("""\b(scientists|researchers|physicists|chemists|engineers|doctors|surgeons|study|forscher\w*|wissenschaftler\w*|studie|ärzte)\b""")

    fun laerm(titel: String, quelle: String): Boolean = LAERM.containsMatchIn(titel) || (quelle.isNotBlank() && LAERM_QUELLEN.containsMatchIn(quelle))

    // Ausschluss: Meinung, Plaene, Verfahrensschritte, Produkte, Politik (gilt immer)
    fun rauschen(titel: String): Boolean =
        MEINUNG.containsMatchIn(titel) || SPEKULATION.containsMatchIn(titel) || STATUS.containsMatchIn(titel) || PRODUKT.containsMatchIn(titel) || POLITIK.containsMatchIn(titel)

    private fun zulassung(titel: String): Int {
        ZULASSUNG_EN.find(titel)?.let { if (!ZULASSUNG_AUSNAHME.containsMatchIn(titel)) return it.range.first }
        ZULASSUNG_DE.find(titel)?.let { if (ARZNEI_DE.containsMatchIn(titel) && !ZULASSUNG_DE_AUSNAHME.containsMatchIn(titel)) return it.range.first }
        return -1
    }

    // Zulassung, Phase-3-Studie oder erreichter Endpunkt
    private fun arzneimittel(titel: String): Boolean = zulassung(titel) >= 0 || PHASE3.containsMatchIn(titel) || ENDPUNKT.containsMatchIn(titel)

    private fun phase3Erfolg(titel: String): Int {
        val m = PHASE3.find(titel) ?: return -1
        return if (POSITIV.containsMatchIn(titel) && !PHASE3_START.containsMatchIn(titel)) m.range.first else -1
    }

    // Stelle des ersten Zeichens fuer ein eingetretenes Ereignis, -1 ohne Zeichen
    private fun zeichen(titel: String, quelle: String): Int {
        val forschung = quelle.isNotBlank() && FORSCHUNG.containsMatchIn(quelle)
        val journal = quelle.isNotBlank() && JOURNAL_QUELLE.matches(quelle.trim())
        val stellen = ArrayList<Int>()
        zulassung(titel).takeIf { it >= 0 }?.let { stellen += it }
        phase3Erfolg(titel).takeIf { it >= 0 }?.let { stellen += it }
        for (r in listOf(ERSTMALS, REKORD, ENDPUNKT, GELUNGEN, LEISTUNG, JOURNAL_TITEL, SERIE)) r.find(titel)?.let { stellen += it.range.first }
        BETRIEB.find(titel)?.let { if (BETRIEB_MASSSTAB.containsMatchIn(titel)) stellen += it.range.first }
        // „Durchbruch“ zaehlt nur, wenn Forschung ihn meldet
        DURCHBRUCH.find(titel)?.let { if (forschung || journal || FORSCHER.containsMatchIn(titel)) stellen += it.range.first }
        if (journal && ERGEBNIS.containsMatchIn(titel)) stellen += 0
        if (forschung) MITTEL.find(titel)?.let { stellen += it.range.first }
        return stellen.minOrNull() ?: -1
    }

    // Ist es ein echter Durchbruch? Kein Ausschluss, ein Zeichen fuer ein eingetretenes Ereignis, davor keine Vorsicht.
    fun durchbruch(titel: String, quelle: String): Boolean {
        if (laerm(titel, quelle) || rauschen(titel)) return false
        val stelle = zeichen(titel, quelle)
        if (stelle < 0) return false
        // Tierversuche nur bei Zulassung
        if (TIER.containsMatchIn(titel) && zulassung(titel) < 0) return false
        // Pressemitteilungsdienste: nur Zulassung und Studienerfolg
        if (quelle.isNotBlank() && PRESSE_QUELLEN.containsMatchIn(quelle)) {
            return zulassung(titel) >= 0 || phase3Erfolg(titel) >= 0 || ENDPUNKT.containsMatchIn(titel)
        }
        // „could“, „may“ und Verwandtes vor dem Zeichen heisst Vermutung; dahinter ist es ein Nebensatz zum Ereignis
        return HEDGE.find(titel)?.let { it.range.first > stelle } ?: true
    }

    // Thema einer Feedmeldung: das mit den meisten verschiedenen Stichwoertern im Titel, bei Gleichstand das frueher
    // aufgefuehrte. null, wenn keines passt.
    fun zuordnen(titel: String): Thema? {
        var bestes: Thema? = null
        var treffer = 0
        for (t in THEMEN) {
            if (t.ohne?.containsMatchIn(titel) == true) continue
            val n = t.stichwortMuster.count { it.containsMatchIn(titel) }
            if (n > treffer) { treffer = n; bestes = t }
        }
        return bestes
    }

    // Dubletten

    private val STOPP = setOf(
        "the", "and", "for", "with", "from", "that", "this", "into", "over", "after", "about", "new", "how", "why", "what", "are", "has", "have",
        "der", "die", "das", "und", "mit", "von", "für", "den", "dem", "ein", "eine", "einer", "zur", "zum", "bei", "auf", "als", "ist", "sind",
    )

    internal fun woerter(titel: String): Set<String> =
        de.troetenrepublik.app.engine.Muster.re("[\\p{L}\\p{N}]{3,}").findAll(titel.lowercase()).map { it.value }.filter { it !in STOPP }.toSet()

    // Zwei Titel zur selben Geschichte: mindestens drei gemeinsame Woerter und sechs Zehntel des kuerzeren Titels.
    internal fun aehnlich(a: Set<String>, b: Set<String>): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        val gemeinsam = a.count { it in b }
        return gemeinsam >= 3 && gemeinsam.toDouble() / minOf(a.size, b.size) >= 0.6
    }

    // Auswahl und Ordnung

    // Aus allen Abrufen die brauchbaren Meldungen: nicht aelter als 14 Tage, ohne Laerm, ohne Dubletten (die neueste
    // einer Geschichte bleibt), je Thema hoechstens acht, neueste zuerst.
    fun filtern(rohe: List<Roh>, jetzt: Long): List<Meldung> =
        auswaehlen(rohe, jetzt, jetzt - TAGE * TAG_MS, Long.MAX_VALUE, emptyList(), IM_SPEICHER)

    // Zuletzt: dieselben Filter, aber Treffer zwischen 14 und 365 Tagen Alter; Dubletten der neuen Meldungen fallen weg.
    // Je Thema hoechstens drei, neueste zuerst.
    fun aeltere(rohe: List<Roh>, jetzt: Long, neu: List<Meldung>): List<Meldung> =
        auswaehlen(rohe, jetzt, jetzt - ZULETZT_TAGE * TAG_MS, jetzt - TAGE * TAG_MS, neu, ZULETZT_JE)

    // Themen ohne neue Meldung in der Liste (nur diese fragen die weite Suche ab)
    fun ohneNeue(neu: List<Meldung>): List<Thema> = THEMEN.filter { t -> neu.none { it.thema == t.id } }

    // von: aelteste erlaubte Zeit, bis: erste nicht mehr erlaubte Zeit
    private fun auswaehlen(rohe: List<Roh>, jetzt: Long, von: Long, bis: Long, vorhanden: List<Meldung>, je: Int): List<Meldung> {
        val brauchbar = ArrayList<Meldung>()
        for (r in rohe) {
            val titel = r.titel.trim()
            if (titel.isBlank() || !r.link.startsWith("http") || r.zeit <= 0 || r.zeit < von || r.zeit >= bis || r.zeit > jetzt + 3_600_000L) continue
            val thema = (if (r.thema != null) thema(r.thema) else zuordnen(titel)) ?: continue
            // Nur echte Durchbrueche: hartes Zeichen, kein Rauschen. Woerter, die das Thema selbst sind (Spiele, Apps),
            // zaehlen dabei nicht als Produktmeldung.
            if (!durchbruch(thema.frei?.replace(titel, "thema") ?: titel, r.quelle)) continue
            if (thema.ohne?.containsMatchIn(titel) == true) continue
            if (thema.id in WIRTSCHAFT && ROUTINE.containsMatchIn(titel)) continue
            // Auch Suchtreffer muessen zum Thema passen (Google bringt Randtreffer wie Firmennamen)
            // (Zulassungen und Phase-3-Meldungen nennen das Fachwort oft nicht im Titel und bleiben)
            if (r.thema != null && !thema.muster.containsMatchIn(titel) && !arzneimittel(titel)) continue
            brauchbar += Meldung(thema.id, titel, r.link, minOf(r.zeit, jetzt), r.quelle)
        }
        // Dubletten zaehlen je Zeile der Trendliste: dieselbe Geschichte darf unter Energie und unter Solar stehen, aber
        // nicht zweimal in derselben Zeile
        val gesehen = HashMap<String, ArrayList<Pair<String, Set<String>>>>()
        fun zeile(thema: String) = TrendListe.zuDurchbruch(thema)?.id ?: thema
        for (m in vorhanden) gesehen.getOrPut(zeile(m.thema)) { ArrayList() }.add(m.titel.lowercase() to woerter(m.titel))
        val ergebnis = ArrayList<Meldung>()
        for (m in brauchbar.sortedByDescending { it.zeit }) {
            val w = woerter(m.titel)
            val liste = gesehen.getOrPut(zeile(m.thema)) { ArrayList() }
            if (liste.any { (t, ws) -> t == m.titel.lowercase() || aehnlich(w, ws) }) continue
            liste += m.titel.lowercase() to w
            ergebnis += m
        }
        return ergebnis.groupBy { it.thema }.values.flatMap { it.take(je) }.sortedByDescending { it.zeit }
    }

    // Die Ansicht: je Thema bis zu fuenf Meldungen der letzten 14 Tage; Themen ohne Meldung fehlen, das Thema mit der
    // frischesten Meldung steht oben.
    fun gruppieren(meldungen: List<Meldung>, jetzt: Long): List<Gruppe> {
        val grenze = jetzt - TAGE * TAG_MS
        return meldungen.filter { it.zeit >= grenze }.groupBy { it.thema }
            .mapNotNull { (id, liste) -> thema(id)?.let { t -> Gruppe(t, liste.sortedByDescending { it.zeit }.take(JE_THEMA)) } }
            .sortedByDescending { it.meldungen.first().zeit }
    }

    // Zuletzt je Thema: Meldungen zwischen 14 und 365 Tagen Alter, hoechstens drei, neueste zuerst. Themen ohne solche fehlen.
    fun zuletzt(meldungen: List<Meldung>, jetzt: Long): Map<String, List<Meldung>> {
        val neuGrenze = jetzt - TAGE * TAG_MS
        val altGrenze = jetzt - ZULETZT_TAGE * TAG_MS
        return meldungen.filter { it.zeit < neuGrenze && it.zeit >= altGrenze }.groupBy { it.thema }
            .mapValues { (_, l) -> l.sortedByDescending { it.zeit }.take(ZULETZT_JE) }
    }

    // Nach einem Teilausfall: Themen, deren Suche scheiterte, behalten ihre bisherigen Meldungen (ohne Dubletten der neuen),
    // die uebrigen Themen zeigen nur die neue Abfrage. Je Thema hoechstens IM_SPEICHER, neueste zuerst.
    fun zusammenfuehren(neu: List<Meldung>, alt: List<Meldung>, gescheitert: Set<String>, jetzt: Long): List<Meldung> {
        val grenze = jetzt - ZULETZT_TAGE * TAG_MS
        val neueWoerter = neu.map { it.thema to woerter(it.titel) }
        val ergaenzt = alt.filter { a ->
            a.thema in gescheitert && a.zeit >= grenze && neu.none { n -> n.titel.equals(a.titel, true) } &&
                neueWoerter.none { (t, w) -> t == a.thema && aehnlich(woerter(a.titel), w) }
        }
        return (neu + ergaenzt).groupBy { it.thema }.values.flatMap { l -> l.sortedByDescending { it.zeit }.take(IM_SPEICHER) }.sortedByDescending { it.zeit }
    }

    // Kuratierter Inhalt [Durchbrueche 0.73]: assets/durchbrueche/inhalt.json, deutsch und englisch, je Eintrag Link.
    // j/je: Jahr oder Zeitangabe (deutsch/englisch), s/se: Stand, de/en: Text, u: Link, q: Name der Quelle,
    // bis: letzter Monat (JJJJ-MM), in dem ein erwarteter Eintrag noch gilt; art: angekuendigt (fester, oeffentlicher Termin),
    // hersteller oder betreiber (Fahrplan laut Hersteller bzw. Betreiber), register (Studienende laut Register).
    const val INHALT_DATEI = "durchbrueche/inhalt.json"

    // Tage seit 1970-01-01 fuer ein Datum im Kalender (ohne java.time)
    private fun tage(jahr: Int, monat: Int, tag: Int): Long {
        val y = if (monat <= 2) jahr - 1 else jahr
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val doy = (153 * (monat + (if (monat > 2) -3 else 9)) + 2) / 5 + tag - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097L + doe - 719_468L
    }

    // Ende des Monats aus „JJJJ-MM“ (Beginn des Folgemonats, UTC), null bei ungueltiger Angabe
    internal fun monatsEnde(bis: String): Long? {
        val m = Regex("^(\\d{4})-(0[1-9]|1[0-2])$").matchEntire(bis) ?: return null
        val j = m.groupValues[1].toInt()
        val mo = m.groupValues[2].toInt()
        return if (mo == 12) tage(j + 1, 1, 1) * TAG_MS else tage(j, mo + 1, 1) * TAG_MS
    }

    const val ART_ANGEKUENDIGT = "angekuendigt"

    // Ein erwarteter Eintrag gilt, bis sein letzter Monat zu Ende ist; ohne gueltiges Datum gilt er nicht.
    fun erwartetNoch(e: Eintrag, jetzt: Long): Boolean = monatsEnde(e.bis)?.let { jetzt < it } ?: false

    @Serializable
    class Eintrag(
        val j: String = "", val je: String = "", val s: String = "", val se: String = "",
        val de: String = "", val en: String = "", val u: String = "", val q: String = "", val bis: String = "", val art: String = "",
    ) {
        fun text(deutsch: Boolean) = if (deutsch) de else en
        // Jahr oder Datum bzw. Stand als Marke vor dem Text
        fun marke(deutsch: Boolean): String = when {
            j.isNotEmpty() -> if (deutsch) j else je.ifEmpty { j }
            else -> if (deutsch) s else se.ifEmpty { s }
        }
    }

    @Serializable
    class ThemaInhalt(val id: String, val e: List<Eintrag> = emptyList(), val w: List<Eintrag> = emptyList(), val r: List<Eintrag> = emptyList())

    // Halbierungs- oder Verdopplungszeit einer Reihe: faellt = Wert sinkt, jahre = Zeit bis zur Halbierung bzw. Verdopplung
    class Verdopplung(val faellt: Boolean, val jahre: Double, val monate: Int)

    // Eine Kennzahlreihe fuer die Kurve: p = Paare Jahr und Wert, ed/ee Einheit, hd/he Datenhinweis, lz Lizenz der Daten,
    // wd/we ein Satz, warum sich die Entwicklung selbst antreibt
    @Serializable
    class Reihe(
        val id: String, val de: String, val en: String, val ed: String = "", val ee: String = "",
        val p: List<List<Double>> = emptyList(), val u: String = "", val q: String = "", val lz: String = "", val ende: Int = 0,
        val hd: String = "", val he: String = "", val wd: String = "", val we: String = "",
    ) {
        fun name(deutsch: Boolean) = if (deutsch) de else en
        fun einheit(deutsch: Boolean) = if (deutsch) ed else ee
        fun hinweis(deutsch: Boolean) = if (deutsch) hd else he
        fun warum(deutsch: Boolean) = if (deutsch) wd else we
        // Letztes Jahr der Reihe
        fun endjahr(): Int = if (ende > 0) ende else p.lastOrNull()?.firstOrNull()?.toInt() ?: 0

        // Gerade durch den Logarithmus der Werte (kleinste Quadrate): daraus die Zeit, in der sich der Wert halbiert oder
        // verdoppelt. null bei weniger als zwei Punkten oder ohne Trend.
        fun verdopplung(): Verdopplung? {
            val ps = p.filter { it.size >= 2 && it[1] > 0 }
            if (ps.size < 2) return null
            val n = ps.size.toDouble()
            val mx = ps.sumOf { it[0] } / n
            val my = ps.sumOf { ln(it[1]) } / n
            val sxx = ps.sumOf { (it[0] - mx) * (it[0] - mx) }
            if (sxx <= 0.0) return null
            val b = ps.sumOf { (it[0] - mx) * (ln(it[1]) - my) } / sxx
            if (abs(b) < 0.001) return null
            val jahre = ln(2.0) / abs(b)
            return Verdopplung(b < 0, jahre, (jahre * 12).roundToInt().coerceAtLeast(1))
        }
    }

    @Serializable
    class Beschleunigung(val reihen: List<Reihe> = emptyList(), val eintraege: List<Eintrag> = emptyList())

    @Serializable
    class Inhalt(val v: Int = 1, val stand: String = "", val themen: List<ThemaInhalt> = emptyList(), val beschleunigung: Beschleunigung = Beschleunigung())

    fun lesenInhalt(text: String): Inhalt? = runCatching { json.decodeFromString(Inhalt.serializer(), text) }.getOrNull()

    // Alterung der Lagebilder (w) nach dem Stand der Datei: ab neun Monaten mit Stand in der Marke, ab 24 Monaten fallen sie weg.
    // Erwartetes (e) endet ueber bis, Rueckblick (r) traegt sein Jahr.
    internal fun gealtert(t: ThemaInhalt, stand: String, jetzt: Long): ThemaInhalt {
        if (t.w.isEmpty() || de.troetenrepublik.app.engine.Alter.tag(stand) == null) return t
        val z = de.troetenrepublik.app.engine.Alter.zustand(stand, de.troetenrepublik.app.engine.Alter.LAGEBILD, jetzt)
        if (z == de.troetenrepublik.app.engine.Alter.Zustand.FRISCH) return t
        if (de.troetenrepublik.app.engine.Alter.ausgeblendet(stand, de.troetenrepublik.app.engine.Alter.LAGEBILD, jetzt)) return ThemaInhalt(t.id, t.e, emptyList(), t.r)
        val marke = de.troetenrepublik.app.engine.Alter.marke(stand)
        val w = t.w.map { e ->
            if (e.s.contains("Stand")) e
            else Eintrag(e.j, e.je, e.s.ifEmpty { "–" } + " · Stand $marke", e.se.ifEmpty { e.s.ifEmpty { "–" } } + " · as of $marke", e.de, e.en, e.u, e.q, e.bis)
        }
        return ThemaInhalt(t.id, t.e, w, t.r)
    }

    // Ein Thema der Ansicht: neue Durchbrueche (14 Tage, hoechstens fuenf) und der kuratierte Inhalt
    class Block(val thema: Thema, val neu: List<Meldung>, val inhalt: ThemaInhalt?, private val jetzt: Long = 0L, val zuletzt: List<Meldung> = emptyList()) {
        // Erwartete Eintraege, die noch nicht abgelaufen sind, nach Ablaufmonat geordnet
        fun erwartet(zeit: Long = jetzt): List<Eintrag> = inhalt?.e.orEmpty().filter { erwartetNoch(it, zeit) }.sortedWith(compareBy({ it.art != ART_ANGEKUENDIGT }, { it.bis }))
        val hatInhalt get() = inhalt != null && (erwartet().isNotEmpty() || inhalt.w.isNotEmpty() || inhalt.r.isNotEmpty())
    }

    // Themen mit neuen Meldungen zuerst (frischeste oben), danach die uebrigen in fester Reihenfolge. Themen ohne Meldung
    // und ohne Inhalt fehlen.
    fun ansicht(inhalt: Inhalt?, meldungen: List<Meldung>, jetzt: Long): List<Block> {
        val neu = gruppieren(meldungen, jetzt).associateBy { it.thema.id }
        val alt = zuletzt(meldungen, jetzt)
        fun thema(id: String) = inhalt?.let { i -> i.themen.firstOrNull { it.id == id }?.let { gealtert(it, i.stand, jetzt) } }
        val mit = neu.values.sortedByDescending { it.meldungen.first().zeit }.map { Block(it.thema, it.meldungen, thema(it.thema.id), jetzt, alt[it.thema.id].orEmpty()) }
        val ohneNeu = THEMEN.filter { it.id !in neu }.map { t -> Block(t, emptyList(), thema(t.id), jetzt, alt[t.id].orEmpty()) }
        return (mit + ohneNeu).filter { it.neu.isNotEmpty() || it.hatInhalt || it.zuletzt.isNotEmpty() }
    }

    // Fuer die Trendliste: wie ansicht(), dazu jedes Thema ohne Meldung und ohne gepflegten Inhalt als leerer Block. So hat
    // jede Zeile ihren Teil Durchbrueche und zeigt „Keine neuen Durchbrueche“, solange die Abfrage nichts bringt.
    fun trendBloecke(inhalt: Inhalt?, meldungen: List<Meldung>, jetzt: Long): List<Block> {
        val da = ansicht(inhalt, meldungen, jetzt)
        val ids = da.map { it.thema.id }.toSet()
        return da + THEMEN.filter { it.id !in ids }.map { Block(it, emptyList(), null, jetzt) }
    }

    // Datum einer Meldung: Jahr, Monat, Tag (UTC), ohne java.time
    class Datum(val jahr: Int, val monat: Int, val tag: Int)

    fun datum(zeit: Long): Datum {
        val z = Math.floorDiv(zeit, TAG_MS) + 719_468L
        val era = Math.floorDiv(z, 146_097L)
        val doe = z - era * 146_097L
        val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val tag = (doy - (153 * mp + 2) / 5 + 1).toInt()
        val monat = (if (mp < 10) mp + 3 else mp - 9).toInt()
        val jahr = (yoe + era * 400 + if (monat <= 2) 1 else 0).toInt()
        return Datum(jahr, monat, tag)
    }

    // Aus dem laufenden Jahr mit Tag und Monat, aeltere mit Monat und Jahr; so steht in einer Liste nicht „26. August“
    // neben „August 2026“ (0.79)
    fun datumMitTag(zeit: Long, jetzt: Long): Boolean = datum(zeit).jahr == datum(jetzt).jahr

    // Branche eines Themas: die Fonds des Themas, die zugleich Branchen-ETF der Branchenliste sind (Kuerzel). Themen ohne
    // solchen Fonds gehoeren zu keiner Branche.
    fun branchen(t: Thema): List<String> = t.etfs.filter { s -> Branchentrends.BRANCHEN.any { it.symbol == s } }

    // Zeile eines Themas in der aufgeklappten Branche: die juengste Meldung, sonst der naechste erwartete Eintrag
    class BrancheZeile(val thema: Thema, val neu: Int, val meldung: Meldung?, val erwartet: Eintrag?)

    // Themen der Branche in der Reihenfolge der Ansicht; Themen ohne Meldung und ohne erwarteten Eintrag fehlen
    fun brancheZeilen(symbol: String, bloecke: List<Block>): List<BrancheZeile> = bloecke
        .filter { symbol in branchen(it.thema) }
        .mapNotNull { b ->
            val m = b.neu.firstOrNull()
            val e = if (m == null) b.erwartet().firstOrNull() else null
            if (m == null && e == null) null else BrancheZeile(b.thema, b.neu.size, m, e)
        }

    // Alter fuer die Zeile: Minuten, Stunden oder Tage.
    enum class Einheit { MINUTEN, STUNDEN, TAGE }

    class Alter(val wert: Long, val einheit: Einheit)

    fun alter(zeit: Long, jetzt: Long): Alter {
        val d = (jetzt - zeit).coerceAtLeast(0)
        return when {
            d < 3_600_000L -> Alter((d / 60_000L).coerceAtLeast(1), Einheit.MINUTEN)
            d < TAG_MS -> Alter(d / 3_600_000L, Einheit.STUNDEN)
            else -> Alter(d / TAG_MS, Einheit.TAGE)
        }
    }

    // Zwischenspeicher

    @Serializable
    class Stand(val abgerufen: Long, val liste: List<Meldung>)

    private val json = Json { ignoreUnknownKeys = true }

    fun lesenGespeichert(text: String): Stand? = runCatching { json.decodeFromString(Stand.serializer(), text) }.getOrNull()
    fun schreiben(s: Stand): String = json.encodeToString(Stand.serializer(), s)
    fun gueltig(abgerufen: Long, jetzt: Long): Boolean = jetzt >= abgerufen && jetzt - abgerufen < GUELTIG_MS
}
