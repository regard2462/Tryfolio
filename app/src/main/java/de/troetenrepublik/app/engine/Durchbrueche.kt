package de.troetenrepublik.app.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Durchbrueche je Thema (Medizin, KI, Quanten, Batterie, ...): Schlagzeilen aus der Google-News-Suche je Thema und aus
// frei lesbaren Wissenschaftsfeeds. Dieses Objekt kennt kein Netz: Themen mit Suchbegriffen und Boersenzuordnung,
// Filter gegen Kurs- und Analystenmeldungen, Zuordnung der Feedmeldungen zu Themen, Dubletten und Sortierung.
object Durchbrueche {

    const val GUELTIG_MS = 3_600_000L
    const val TAGE = 14
    const val JE_THEMA = 5
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
    ) {
        val muster: Regex = Regex("\\b(" + stichwoerter.joinToString("|") + ")", RegexOption.IGNORE_CASE)
        val stichwortMuster: List<Regex> = stichwoerter.map { Regex("\\b($it)", RegexOption.IGNORE_CASE) }
        fun name(deutsch: Boolean) = if (deutsch) de else en
    }

    private fun suche(begriffe: String, signal: String) = "($begriffe) ($signal)"

    val THEMEN: List<Thema> = listOf(
        Thema(
            "medizin", "Medizin und Biotech", "Medicine and biotech", listOf("XBI", "IBB", "ARKG"),
            suche(
                "\"cancer vaccine\" OR \"gene therapy\" OR \"FDA approves\" OR \"FDA approval\" OR CRISPR OR mRNA OR \"phase 3\" OR \"first-in-class\"",
                "drug OR treatment OR therapy OR vaccine OR patients",
            ),
            suche(
                "Krebsimpfung OR Durchbruch OR Zulassung OR Gentherapie OR Impfstoff OR Wirkstoff OR \"Phase-3\"",
                "Medikament OR Therapie OR Krebs OR Patienten OR Studie",
            ),
            listOf("cancer", "tumou?r", "vaccin", "gene therap", "crispr", "mrna", "drug", "antibiotic", "alzheimer", "diabetes", "clinical trial", "immunotherap", "therap", "patients", "obesity", "fda"),
        ),
        Thema(
            "ki", "Künstliche Intelligenz", "Artificial intelligence", listOf("AIQ", "IGV"),
            suche("\"AI\" OR \"artificial intelligence\"", "breakthrough OR \"new model\" OR unveils OR first OR outperforms OR solves"),
            suche("KI OR \"Künstliche Intelligenz\"", "Durchbruch OR \"neues Modell\" OR erstmals OR Forscher OR vorgestellt"),
            listOf("artificial intelligence", "ai\\b", "machine learning", "neural net", "language model", "llm", "chatbot", "deep learning", "generative"),
        ),
        Thema(
            "quanten", "Quantentechnologie", "Quantum technology", listOf("QTUM"),
            suche("quantum OR qubit", "breakthrough OR milestone OR \"error correction\" OR record OR first OR advantage"),
            suche("Quantencomputer OR Quantentechnologie OR Qubit OR Quanten", "Durchbruch OR Meilenstein OR Rekord OR erstmals"),
            listOf("quantum", "qubit"),
        ),
        Thema(
            "halbleiter", "Halbleiter und Chips", "Semiconductors and chips", listOf("SMH", "SOXX"),
            suche("semiconductor OR chip OR transistor OR lithography OR photonic", "breakthrough OR first OR milestone OR unveils OR record") + " -obituary",
            suche("Halbleiter OR Chip OR Transistor OR Lithografie OR Photonik", "Durchbruch OR erstmals OR Meilenstein OR vorgestellt OR Rekord"),
            listOf("semiconductor", "transistor", "wafer", "lithograph", "photonic", "chips?\\b", "chipmaker", "nanometer", "\\d+ ?nm\\b", "silicon"),
        ),
        Thema(
            "batterie", "Batterie und Speicher", "Batteries and storage", listOf("LIT", "BATT"),
            suche("battery OR \"solid-state\" OR \"sodium-ion\" OR \"energy storage\"", "breakthrough OR record OR unveils OR new OR \"energy density\"") + " -fire -fires -recall",
            suche("Batterie OR Akku OR Festkörperbatterie OR \"Natrium-Ionen\" OR Energiespeicher", "Durchbruch OR Rekord OR Forscher OR Reichweite"),
            listOf("batter", "solid-state", "sodium-ion", "lithium", "anode", "cathode", "electrolyte", "supercapacitor", "energy storage", "akku"),
            Regex("\\b(fires?|blaze|recalls?|brand|brände|brennt|explod\\w*|explosion)\\b", RegexOption.IGNORE_CASE),
        ),
        Thema(
            "energie", "Energie", "Energy", listOf("ICLN", "TAN", "URA"),
            suche("fusion OR solar OR perovskite OR hydrogen OR \"nuclear reactor\" OR geothermal", "breakthrough OR record OR milestone OR first OR efficiency"),
            suche("Kernfusion OR Solarzellen OR Perowskit OR Wasserstoff OR Kernreaktor OR Geothermie", "Durchbruch OR Rekord OR Meilenstein OR erstmals OR Wirkungsgrad"),
            listOf("fusion", "solar", "photovoltaic", "perovskite", "hydrogen", "nuclear", "reactor", "geothermal", "electrolyzer", "renewable", "wasserstoff", "kernfusion"),
        ),
        Thema(
            "raumfahrt", "Raumfahrt", "Space", listOf("UFO", "ARKX"),
            suche("rocket OR spacecraft OR satellite OR starship OR \"space station\" OR lunar", "breakthrough OR first OR milestone OR record OR successful"),
            suche("Rakete OR Raumfahrt OR Satellit OR Raumsonde OR Mondmission OR Starship", "Durchbruch OR erstmals OR Meilenstein OR Rekord OR erfolgreich"),
            listOf("rocket", "spacecraft", "satellite", "orbit", "lunar", "mars\\b", "starship", "space station", "spaceflight", "launch vehicle", "raumfahrt"),
        ),
        Thema(
            "robotik", "Robotik und Automatisierung", "Robotics and automation", listOf("BOTZ", "ROBO"),
            suche("robot OR humanoid OR robotics OR \"self-driving\" OR drone", "breakthrough OR unveils OR first OR milestone OR demonstrates"),
            suche("Roboter OR Humanoide OR Robotik OR Drohne OR \"autonomes Fahren\"", "Durchbruch OR vorgestellt OR erstmals OR Meilenstein"),
            listOf("robot", "humanoid", "drone", "autonomous", "self-driving", "exoskeleton", "automation", "roboter"),
        ),
        Thema(
            "materialien", "Materialien", "Materials", listOf("XLB", "REMX"),
            suche("\"new material\" OR graphene OR superconductor OR nanomaterial OR metamaterial OR \"carbon nanotube\"", "breakthrough OR discovery OR record OR scientists OR researchers"),
            suche("Graphen OR Supraleiter OR Nanomaterial OR \"neues Material\" OR Metamaterial", "Durchbruch OR Forscher OR Entdeckung OR Rekord"),
            listOf("graphene", "superconduct", "nanomaterial", "nanotech", "metamaterial", "alloy", "polymer", "new material", "ceramic", "2d material", "nanotube", "aerogel", "crystal"),
        ),
        Thema(
            "klima", "Klima und Landwirtschaft", "Climate and agriculture", listOf("MOO", "PHO"),
            suche("\"carbon capture\" OR \"direct air capture\" OR desalination OR \"gene-edited\" OR \"lab-grown meat\" OR fertilizer", "breakthrough OR first OR new OR scientists OR record"),
            suche("CO2-Abscheidung OR Entsalzung OR Gentechnik OR Laborfleisch OR Dünger OR Klimatechnik", "Durchbruch OR Forscher OR erstmals OR neue"),
            listOf("carbon capture", "direct air capture", "desalination", "crop", "agricultur", "farming", "fertili[sz]er", "drought", "cultivated meat", "lab-grown", "soil", "water purification"),
            Regex("\\b(court|lawsuit|ruling|ruled)\\b", RegexOption.IGNORE_CASE),
        ),
        Thema(
            "kommunikation", "Kommunikation und Netze", "Communication and networks", listOf("IYZ", "FIVG"),
            suche("\"6G\" OR \"satellite internet\" OR \"optical fiber\" OR \"laser communication\" OR \"quantum network\"", "breakthrough OR record OR first OR milestone OR speed"),
            suche("\"6G\" OR Satelliteninternet OR Glasfaser OR Mobilfunk OR \"Wi-Fi\"", "Durchbruch OR Rekord OR erstmals OR Meilenstein OR Geschwindigkeit"),
            listOf("6g\\b", "5g\\b", "wireless", "broadband", "optical fib", "laser communication", "satellite internet", "starlink", "wi-fi", "glasfaser", "mobilfunk"),
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

    // Eine Meldung wie sie aus einem Abruf kommt. thema leer bei den Feeds (Zuordnung nach Stichwoertern).
    class Roh(val thema: String?, val titel: String, val link: String, val zeit: Long, val quelle: String)

    @Serializable
    class Meldung(val thema: String, val titel: String, val link: String, val zeit: Long, val quelle: String)

    class Gruppe(val thema: Thema, val meldungen: List<Meldung>)

    // Filter

    // Kurs-, Analysten-, Finanzierungs- und Marktforschungsmeldungen sind keine Durchbrueche.
    private val LAERM = Regex(
        "\\b(stocks?|shares?|share price|price targets?|etfs?|nasdaq|nyse|dow jones|s&p|wall street|analysts?|upgrades?|downgrades?|earnings|dividends?|" +
            "buy rating|to buy|should you|investors?|investing|investment|market (size|share|to reach|forecast|report)|cagr|rallies|rally|soars?|plunges?|" +
            "valuation|ipo|goes public|secures|raises|funding|series [a-e]|short sellers?|bullish|bearish|penny|" +
            "aktien?|kursziel|analysten?|dax|börse|anleger|kurs|kurssprung|kursrally|" +
            "court|lawsuit|sues|obituary|nachruf)\\b|\\$\\s?\\d+(\\.\\d+)? ?(m|b|million|billion)\\b",
        RegexOption.IGNORE_CASE,
    )

    private val LAERM_QUELLEN = Regex(
        "motley fool|zacks|seeking alpha|benzinga|marketbeat|investopedia|investorplace|simply wall st|wall st\\.|barron|tipranks|thestreet|gurufocus|insider monkey|" +
            "stocktwits|finanzen\\.|boerse\\.de|börse|der aktionär|wallstreet-online|aktiencheck|investor's business",
        RegexOption.IGNORE_CASE,
    )

    // Hinweis auf etwas Neues oder Erreichtes; fuer die Suchtreffer von Google, die auch Randthemen bringen.
    private val SIGNAL = Regex(
        "\\b(breakthrough|first|new|approv\\w*|clear(s|ed)|unveil\\w*|discover\\w*|invent\\w*|develop\\w*|record|milestone|achiev\\w*|demonstrat\\w*|solve[sd]?|trial|study|" +
            "scientists|researchers|cure[sd]?|launch\\w*|succe\\w*|world's|crack\\w*|overcome|durchbruch|erstmals?|neu\\w*|zugelassen|zulassung|studie|entwickel\\w*|" +
            "forscher\\w*|forschende\\w*|gelingt|rekord|meilenstein|entdeck\\w*|vorgestellt|erfolg\\w*)\\b",
        RegexOption.IGNORE_CASE,
    )

    fun laerm(titel: String, quelle: String): Boolean = LAERM.containsMatchIn(titel) || (quelle.isNotBlank() && LAERM_QUELLEN.containsMatchIn(quelle))

    fun signal(titel: String): Boolean = SIGNAL.containsMatchIn(titel)

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
        Regex("[\\p{L}\\p{N}]{3,}").findAll(titel.lowercase()).map { it.value }.filter { it !in STOPP }.toSet()

    // Zwei Titel zur selben Geschichte: mindestens drei gemeinsame Woerter und sechs Zehntel des kuerzeren Titels.
    internal fun aehnlich(a: Set<String>, b: Set<String>): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        val gemeinsam = a.count { it in b }
        return gemeinsam >= 3 && gemeinsam.toDouble() / minOf(a.size, b.size) >= 0.6
    }

    // Auswahl und Ordnung

    // Aus allen Abrufen die brauchbaren Meldungen: nicht aelter als 14 Tage, ohne Laerm, ohne Dubletten (die neueste
    // einer Geschichte bleibt), je Thema hoechstens acht, neueste zuerst.
    fun filtern(rohe: List<Roh>, jetzt: Long): List<Meldung> {
        val grenze = jetzt - TAGE * TAG_MS
        val brauchbar = ArrayList<Meldung>()
        for (r in rohe) {
            val titel = r.titel.trim()
            if (titel.isBlank() || !r.link.startsWith("http") || r.zeit <= 0 || r.zeit < grenze || r.zeit > jetzt + 3_600_000L) continue
            if (laerm(titel, r.quelle)) continue
            val thema = if (r.thema != null) thema(r.thema) else zuordnen(titel)
            if (thema == null) continue
            if (thema.ohne?.containsMatchIn(titel) == true) continue
            // Suchtreffer brauchen ein Zeichen fuer Neues; Feeds bringen nur Forschungsmeldungen
            if (r.thema != null && !signal(titel)) continue
            brauchbar += Meldung(thema.id, titel, r.link, minOf(r.zeit, jetzt), r.quelle)
        }
        val gesehen = ArrayList<Pair<String, Set<String>>>()
        val ergebnis = ArrayList<Meldung>()
        for (m in brauchbar.sortedByDescending { it.zeit }) {
            val w = woerter(m.titel)
            if (gesehen.any { (t, ws) -> t == m.titel.lowercase() || aehnlich(w, ws) }) continue
            gesehen += m.titel.lowercase() to w
            ergebnis += m
        }
        return ergebnis.groupBy { it.thema }.values.flatMap { it.take(IM_SPEICHER) }.sortedByDescending { it.zeit }
    }

    // Die Ansicht: je Thema bis zu fuenf Meldungen der letzten 14 Tage; Themen ohne Meldung fehlen, das Thema mit der
    // frischesten Meldung steht oben.
    fun gruppieren(meldungen: List<Meldung>, jetzt: Long): List<Gruppe> {
        val grenze = jetzt - TAGE * TAG_MS
        return meldungen.filter { it.zeit >= grenze }.groupBy { it.thema }
            .mapNotNull { (id, liste) -> thema(id)?.let { t -> Gruppe(t, liste.sortedByDescending { it.zeit }.take(JE_THEMA)) } }
            .sortedByDescending { it.meldungen.first().zeit }
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
