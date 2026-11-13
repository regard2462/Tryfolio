package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Rss
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DurchbruecheTest {

    private val jetzt = Rss.zeit("Sat, 07 Nov 2026 00:00:00 GMT")
    private val gestern = Rss.zeit("Fri, 06 Nov 2026 12:00:00 GMT")

    // Stichprobe: realistische Schlagzeilen, wie Google News sie je Thema liefert (Titel ohne Herausgeber, Herausgeber
    // getrennt). thema = Thema der Suche, die den Treffer liefern wuerde; null = Feed. bericht = Beispiel aus dem
    // Pruefbericht. Die Stichprobe wurde vor den Regeln geschrieben und danach nicht mehr an sie angepasst.
    private class K(val thema: String?, val titel: String, val quelle: String, val bericht: Boolean = false)

    // Eingetretene Ereignisse: Zulassung, erste Anwendung, bestaetigter Rekord, erfolgreiche Phase-3-Studie, Erstflug,
    // erster kommerzieller Betrieb, Fachjournal mit messbarem Ergebnis
    private val echtEn = listOf(
        K("genetik", "FDA approves first gene therapy that could be one-time cure for hemophilia", "Reuters", true),
        K("genetik", "FDA approves \$4.25 million gene therapy for rare childhood disease", "STAT", true),
        K("medizin", "European Commission approves Alzheimer's drug donanemab for EU market", "Reuters", true),
        K("quanten", "Google's Willow chip solves computation in five minutes that would take septillion years", "The Verge", true),
        K("raumfahrt", "Researchers share first-ever images of a planet forming", "BBC", true),
        K("raumfahrt", "SpaceX catches Starship booster with giant mechanical arms", "BBC", true),
        K("ki", "DeepMind AI wins gold at International Mathematical Olympiad", "BBC", true),
        K("genetik", "Gene therapy restores hearing in children born deaf", "Reuters", true),
        K("krebs", "FDA approves amivantamab for recurrent or metastatic HNSCC", "Oncology Nursing News"),
        K("krebs", "Genmab's Phase 3 trial met primary endpoints of overall survival and response rate in head and neck cancer", "Oncodaily"),
        K("krebs", "Chinese drug doubles lung cancer survival window in phase 3 trial", "South China Morning Post"),
        K("krebs", "Merck and Moderna personalized cancer vaccine meets primary endpoint in melanoma Phase 3 trial", "BioSpace"),
        K("genetik", "FDA approves first-ever plasmid DNA-based immunotherapy", "Drug Discovery News"),
        K("genetik", "Doctors treat baby with first personalized gene-editing therapy", "AP News"),
        K("medizin", "FDA approves first drug offering a functional cure for chronic hepatitis B", "ScienceAlert"),
        K("medizin", "FDA approves lenacapavir, first twice-yearly injection to prevent HIV", "AP News"),
        K("medizin", "Pharma Inc. announces FDA approval of first treatment for rare muscle disease", "GlobeNewswire"),
        K("quanten", "NVision unveils first manufacturable molecular qubit, validated in peer-reviewed Nature paper", "EurekAlert!"),
        K("quanten", "First observation of quantum spins shifting a centimeter-scale object in the lab", "Science X"),
        K("quanten", "Scientists overcome a hidden obstacle to more powerful quantum computers", "Science Daily"),
        K("halbleiter", "Semiconductor breakthrough overcomes major challenge in capturing solar energy", "EurekAlert!"),
        K("fusion", "China's fusion experiment holds plasma for 1,066 seconds in world record", "Reuters"),
        K("fusion", "Wendelstein 7-X sets new record for fusion triple product in long plasma", "Phys.org"),
        K("energie", "NRC approves NuScale small modular reactor design", "World Nuclear News"),
        K("energie", "Perovskite-silicon tandem solar cell reaches record 34.9% efficiency", "pv magazine"),
        K("batterie", "CATL unveils first mass-produced sodium-ion battery for passenger cars", "Reuters"),
        K("raumfahrt", "India becomes first country to land near the Moon's south pole", "Reuters"),
        K("raumfahrt", "NASA's DART spacecraft successfully changes asteroid's orbit in first planetary defense test", "Space.com"),
        K("robotik", "Waymo opens first fully driverless robotaxi service to the public", "CNBC"),
        K("robotik", "Robot performs first autonomous laparoscopic surgery on a living animal", "IEEE Spectrum"),
        K("materialien", "Researchers create first functional graphene semiconductor, published in Nature", "Nature"),
        K("klima", "Climeworks switches on world's largest direct air capture plant in Iceland", "Reuters"),
        K("kommunikation", "NICT sets world record of 402 terabits per second over standard optical fiber", "Phys.org"),
        K("kommunikation", "T-Mobile and Starlink launch satellite texting service for unmodified phones", "The Verge"),
    )

    // Alles andere: Ankuendigungen, Plaene, Meinungen, Kurse, Finanzierung, Produktwerbung, Statusvergaben, Tarif, Verfahrensschritte
    private val unechtEn = listOf(
        K("krebs", "Biotech X receives FDA Breakthrough Therapy Designation for lung cancer drug", "GlobeNewswire", true),
        K("krebs", "2026 Breakthrough Prize winners announced for cancer research", "Reuters", true),
        K("ki", "CEO says breakthrough in AI coming next year", "CNBC", true),
        K("quanten", "Researchers say quantum breakthrough remains years away", "Financial Times", true),
        K("medizin", "FDA approves label update for existing diabetes drug", "Fierce Pharma", true),
        K("medizin", "FDA clears new fitness tracker feature for irregular heartbeat", "The Verge", true),
        K("ki", "Startup unveils world's first AI-powered CRM for small businesses", "PR Newswire", true),
        K("batterie", "Tesla sets new record for battery electric vehicle deliveries in Q3", "Reuters", true),
        K("halbleiter", "Nvidia shares hit record high as AI chip demand soars", "Barron's"),
        K("quanten", "Quint Computing secures 9.1 million euro seed round to scale quantum error correction", "Quantum Computing Report"),
        K("quanten", "IonQ reports record revenue and raises full year guidance", "SSBCrack"),
        K("ki", "Grimes to star in the first-ever AI-generated film coming to theaters", "Billboard"),
        K("fusion", "The world's first fusion cuisine is hundreds of years old", "CNN"),
        K("energie", "First Solar celebrates ribbon cutting at new Gaffney facility", "FOX Carolina News"),
        K("halbleiter", "VanEck launches Europe's first China semiconductor ETF", "Yahoo Finance UK"),
        K("batterie", "Sunset Park blaze kills 6 as lithium-ion battery fire crisis rages on", "Streetsblog"),
        K("robotik", "Tesla Optimus will be producing 1 million robots a year by 2030, Musk says", "Electrek"),
        K("quanten", "Analysts predict quantum computing could reach commercial scale by 2030", "Forbes"),
        K("batterie", "What is a solid-state battery and when will it arrive in electric cars?", "Wired"),
        K("medizin", "FDA accepts application for Alzheimer's drug, decision expected in March 2027", "Fierce Biotech"),
        K("krebs", "Moderna completes enrollment in Phase 3 trial of cancer vaccine", "Fierce Biotech"),
        K("medizin", "Novo Nordisk to cut 9,000 jobs as obesity drug price war bites", "Reuters"),
        K("batterie", "Samsung unveils new foldable phone with record-breaking battery capacity", "GSMArena"),
        K("energie", "Breakthrough Energy Ventures invests \$50 million in geothermal startup", "Bloomberg"),
        K("krebs", "Scientists say new mRNA vaccine shows promise against pancreatic cancer in mice", "ScienceDaily"),
        K("raumfahrt", "Musk says Starship will land humans on Mars by 2030", "Reuters"),
        K("medizin", "Meet the startup that wants to cure aging", "MIT Technology Review"),
        K("krebs", "Pfizer's \$43 billion Seagen deal reshapes cancer drug pipeline", "Reuters"),
        K("halbleiter", "Breakthrough in wage talks at chip plant averts strike", "Reuters"),
        K("genetik", "Gene therapy firm lays off 30% of staff after trial setback", "Fierce Biotech"),
        K("raumfahrt", "NASA delays Artemis III launch to 2028", "SpaceNews"),
        K("medizin", "Biotech announces positive topline data in phase 2 trial for obesity drug", "BioSpace"),
        K("robotik", "Waymo robotaxis stall during San Francisco power outage", "The Verge"),
        K("halbleiter", "AI chip startup raises \$400 million at \$4 billion valuation", "TechCrunch"),
        K("halbleiter", "Nvidia unveils new Blackwell chip at GTC conference", "CNBC"),
        K("ki", "Why the next big breakthrough in medicine might come from AI, experts say", "The Guardian"),
        K("medizin", "Lilly's orforglipron could become best-selling obesity pill, analysts say", "Reuters"),
        K("batterie", "Ford recalls 100,000 EVs over battery fire risk", "AP News"),
        K("genetik", "FDA places clinical hold on CRISPR therapy after patient death", "STAT"),
    )

    private val echtDe = listOf(
        K("medizin", "Erster Mensch mit Gehirnimplantat spricht wieder fließend", "Spiegel", true),
        K("krebs", "EU-Kommission genehmigt Krebsmedikament von Roche", "Handelsblatt", true),
        K("krebs", "Neue Therapie heilt Leukämie bei Kindern", "Tagesschau", true),
        K("materialien", "Forscher lösen jahrzehntealtes Rätsel der Supraleitung", "Spiegel", true),
        K("fusion", "China startet weltweit größtes Fusionsexperiment, Plasma hält 1000 Sekunden", "Heise", true),
        K("medizin", "EU-Kommission lässt ersten Chikungunya-Impfstoff zu", "Tagesschau"),
        K("medizin", "Lecanemab in der EU zugelassen: Erstes Alzheimer-Medikament gegen die Krankheitsursache", "Deutsches Ärzteblatt"),
        K("genetik", "Erste Gentherapie gegen Sichelzellkrankheit in der EU zugelassen", "Deutsches Ärzteblatt"),
        K("genetik", "Genschere CRISPR: Erstmals Patientin mit Sichelzellanämie dauerhaft geheilt", "Spiegel"),
        K("quanten", "Quantencomputer löst erstmals Aufgabe, an der klassische Rechner scheitern", "Heise"),
        K("quanten", "Forscher bauen Quantencomputer mit mehr als 6000 Qubits", "Golem"),
        K("raumfahrt", "SpaceX fängt Starship-Booster erstmals mit Roboterarmen auf", "Tagesschau"),
        K("raumfahrt", "Indische Sonde landet erfolgreich am Südpol des Mondes", "Deutsche Welle"),
        K("raumfahrt", "Nasa-Sonde Dart lenkt erstmals einen Asteroiden ab", "Spiegel"),
        K("ki", "KI von Google DeepMind holt Goldmedaille bei der Mathematik-Olympiade", "Heise"),
        K("ki", "AlphaFold-Datenbank enthält jetzt 200 Millionen Proteinstrukturen", "Heise"),
        K("batterie", "CATL stellt erste serienreife Natrium-Ionen-Batterie für Pkw vor", "Golem"),
        K("energie", "Solarzelle aus Perowskit und Silizium erreicht Rekordwirkungsgrad von 34,9 Prozent", "pv magazine"),
        K("energie", "Fervo nimmt erstes Geothermie-Kraftwerk der neuen Generation in Betrieb", "Handelsblatt"),
        K("halbleiter", "TSMC startet Serienfertigung von 2-Nanometer-Chips", "Heise"),
        K("halbleiter", "ASML liefert erstes High-NA-EUV-System aus", "Golem"),
        K("robotik", "Robotaxi: Waymo bietet fahrerlosen Dienst für alle Nutzer an", "Tagesschau"),
        K("robotik", "Roboter operiert erstmals selbstständig am lebenden Schwein", "Spiegel"),
        K("klima", "Climeworks nimmt weltgrößte Anlage zur CO2-Abscheidung aus der Luft in Betrieb", "Tagesschau"),
        K("klima", "Singapur lässt erstmals Laborfleisch zum Verkauf zu", "Deutsche Welle"),
        K("kommunikation", "Glasfaser-Rekord: 402 Terabit pro Sekunde über Standardfaser übertragen", "Golem"),
        K("kommunikation", "Erste Videotelefonie per Satellit direkt aufs Smartphone gelungen", "Heise"),
        K("materialien", "Erstmals funktionierenden Halbleiter aus Graphen hergestellt", "Spiegel"),
        K("medizin", "Retatrutid senkt Körpergewicht um 28 Prozent in Phase-3-Studie", "Deutsches Ärzteblatt"),
        K("krebs", "Phase-III-Studie: Neue Immuntherapie verlängert Überleben bei Lungenkrebs deutlich", "Deutsches Ärzteblatt"),
        K("medizin", "WHO empfiehlt erstmals Malaria-Impfstoff für Kinder", "Tagesschau"),
        K("genetik", "Gentherapie lässt gehörlose Kinder erstmals hören", "Spiegel"),
        K("genetik", "Genschere heilt Erbkrankheit bei Baby: Personalisierte Therapie in sechs Monaten entwickelt", "Spiegel"),
    )

    private val unechtDe = listOf(
        K("ki", "Durchbruch bei Verhandlungen über neues KI-Gesetz im Bundestag", "Tagesschau", true),
        K("batterie", "Rekord bei Neuzulassungen von Elektroautos im Oktober", "Handelsblatt", true),
        K("robotik", "Erstmals zugelassen: Neues Taxi-Unternehmen setzt auf autonomes Fahren in Leipzig", "LVZ", true),
        K("robotik", "Weltweit erste Wasserstoff-Tankstelle für Drohnen eröffnet", "Golem", true),
        K("halbleiter", "Durchbruch bei Tarifverhandlungen im Chip-Werk", "Tagesschau", true),
        K("halbleiter", "Nvidia-Aktie springt auf Rekordhoch nach KI-Chip-Ankündigung", "finanzen.net"),
        K("quanten", "Experte: Quantencomputer könnten Verschlüsselung bald knacken", "Focus"),
        K("krebs", "Biontech-Aktie: Analysten heben Kursziel nach Krebsimpfstoff-Daten an", "Der Aktionär"),
        K("batterie", "Start-up für Festkörperbatterien sammelt 80 Millionen Euro ein", "Handelsblatt"),
        K("energie", "Ratgeber: Wann lohnt sich eine Solaranlage mit Speicher?", "Stiftung Warentest"),
        K("fusion", "Wie funktioniert eigentlich Kernfusion? Das müssen Sie wissen", "Spiegel"),
        K("ki", "SAP stellt neue KI-Strategie vor und setzt auf Agenten", "Handelsblatt"),
        K("quanten", "Was Anleger über Quantenaktien wissen sollten", "Focus"),
        K("batterie", "Elektroauto brennt in Tiefgarage: Lithium-Akku als Ursache", "Bild"),
        K("kommunikation", "Merz verspricht Durchbruch beim Mobilfunkausbau bis 2030", "Tagesschau"),
        K("kommunikation", "Telekom und Vodafone einigen sich auf neue Glasfaser-Kooperation", "Handelsblatt"),
        K("klima", "Rekord bei Weizenernte in Deutschland erwartet", "Agrarheute"),
        K("raumfahrt", "Isar Aerospace: Zweiter Teststart der Rakete verschoben", "Spiegel"),
        K("raumfahrt", "Musk kündigt Mars-Flug für 2028 an", "Spiegel"),
        K("medizin", "Studie: Kaffee senkt womöglich das Demenzrisiko", "Focus"),
        K("krebs", "Impfstoff gegen Krebs: Hoffnung für Patienten, aber noch viele Fragen offen", "Tagesschau"),
        K("medizin", "Novartis streicht 500 Stellen in der Forschung", "Handelsblatt"),
        K("medizin", "Hersteller beantragt Zulassung für Biosimilar von Adalimumab", "Pharmazeutische Zeitung"),
        K("krebs", "Uniklinik eröffnet neues Krebszentrum mit Spenden von 20 Millionen Euro", "Süddeutsche"),
        K("krebs", "Mäuse-Studie: Neuer Wirkstoff stoppt Tumorwachstum", "Focus"),
        K("robotik", "Deutsche Bahn testet erstmals Drohnen zur Gleisinspektion", "Golem"),
        K("fusion", "Kernfusion: Bundesregierung plant Förderprogramm mit einer Milliarde Euro", "Tagesschau"),
        K("energie", "Siemens-Energy-Aktie: Kursziel angehoben, Wasserstoff-Fantasie", "Der Aktionär"),
        K("batterie", "BMW zeigt neues Elektro-Modell mit Rekordreichweite auf der IAA", "Auto Motor und Sport"),
        K("halbleiter", "Chipfabrik in Dresden: Grundsteinlegung mit dem Kanzler", "MDR"),
        K("kommunikation", "Bundesnetzagentur: Mobilfunkabdeckung steigt auf 96 Prozent", "Heise"),
        K("energie", "Wasserstoffzüge: Streit um Kosten, Betreiber halten Verträge für gescheitert", "Spiegel"),
        K("ki", "KI-Gesetz: EU-Kommission verschiebt Fristen", "Tagesschau"),
        K("genetik", "Forscher warnen vor Risiken der Genschere", "Focus"),
        K("medizin", "Pharmakonzern kündigt Phase-3-Studie für Alzheimer-Mittel an", "Handelsblatt"),
    )

    // Zweite, getrennte Stichprobe: nach der ersten Fassung der Regeln geschrieben, an den Regeln nicht mehr verändert
    private val echtEnNeu = listOf(
        K("medizin", "FDA approves first oral GLP-1 pill for weight loss", "Reuters"),
        K("fusion", "German fusion experiment sets new record for sustained high-performance plasma", "Phys.org"),
        K("genetik", "EU approves first CRISPR therapy for sickle cell disease", "Reuters"),
        K("quanten", "Chinese scientists achieve room-temperature quantum memory lasting one hour", "South China Morning Post"),
        K("raumfahrt", "Boeing Starliner completes first crewed flight to the space station", "Space.com"),
        K("medizin", "WHO prequalifies first tuberculosis vaccine in a century", "Reuters"),
        K("medizin", "Alzheimer's blood test approved by FDA for first time", "AP News"),
        K("batterie", "Solid-state battery completes 1,000 cycles in independent lab test, study shows", "Nature Energy"),
    )
    private val unechtEnNeu = listOf(
        K("genetik", "Why the FDA's new approval pathway could speed up gene therapies", "STAT"),
        K("krebs", "Moderna shares jump after cancer vaccine data", "Reuters"),
        K("quanten", "Startup claims world's first quantum-safe smartphone", "TechCrunch"),
        K("raumfahrt", "SpaceX plans record 30 Starship launches next year", "SpaceNews"),
        K("genetik", "FDA grants fast track designation to Alzheimer's gene therapy", "Fierce Biotech"),
        K("halbleiter", "Intel to cut 15,000 jobs as chip foundry losses mount", "Bloomberg"),
        K("quanten", "Quantum computing conference opens in Boston with record attendance", "The Quantum Insider"),
    )
    private val echtDeNeu = listOf(
        K("medizin", "Zulassung erteilt: Erstes Medikament gegen Schuppenflechte als Tablette", "Deutsches Ärzteblatt"),
        K("raumfahrt", "Forscher entdecken erstmals Wasser auf dem Mond im Sonnenlicht", "Spiegel"),
        K("materialien", "Physiker erzeugen Rekord-Magnetfeld von 45 Tesla", "Heise"),
        K("krebs", "Erste Patientin mit Bauchspeicheldrüsenkrebs nach Impfung seit drei Jahren krebsfrei", "Tagesschau"),
        K("medizin", "Europäische Arzneimittelagentur lässt neuen Alzheimer-Antikörper zu", "Deutsche Welle"),
        K("energie", "Neuer Weltrekord: Solarzelle wandelt 35 Prozent des Lichts in Strom um", "pv magazine"),
        K("raumfahrt", "Rakete von Isar Aerospace erreicht erstmals die Umlaufbahn", "Spiegel"),
        K("genetik", "Studie in Nature: Gentherapie stellt Sehkraft bei Kindern wieder her", "Spiegel"),
    )
    private val unechtDeNeu = listOf(
        K("raumfahrt", "SpaceX will 2027 erstmals Menschen zum Mars schicken", "Spiegel"),
        K("energie", "Aktie von Rheinmetall erreicht Rekord", "Handelsblatt"),
        K("quanten", "Bundesregierung kündigt neue Förderung für Quantencomputer an", "Tagesschau"),
        K("energie", "Weltweit erste Wasserstoff-Sauna eröffnet in Berlin", "Berliner Zeitung"),
        K("energie", "Ratgeber Solarmodule: Diese Fehler sollten Sie vermeiden", "Stiftung Warentest"),
        K("medizin", "Pharmakonzern meldet Rekordumsatz im dritten Quartal", "Handelsblatt"),
        K("fusion", "Wie realistisch ist ein Durchbruch bei der Kernfusion bis 2035?", "Spiegel"),
    )

    private fun roh(liste: List<K>, zeit: Long = gestern) =
        liste.mapIndexed { i, k -> Durchbrueche.Roh(k.thema, k.titel, "https://example.org/a$i", zeit - i * 60_000L, k.quelle) }

    // Anteil richtig eingeordneter Titel; der Ablauf ist der der App (durchbruch() und danach filtern())
    private fun trefferquote(echt: List<K>, unecht: List<K>): Triple<Int, Int, List<String>> {
        val fehler = ArrayList<String>()
        val durch = Durchbrueche.filtern(roh(echt + unecht), jetzt).map { it.titel }.toSet()
        var richtig = 0
        for (k in echt) if (k.titel in durch) richtig++ else fehler += "FEHLT: ${k.titel} [${k.quelle}]"
        for (k in unecht) if (k.titel !in durch) richtig++ else fehler += "ZUVIEL: ${k.titel} [${k.quelle}]"
        return Triple(richtig, echt.size + unecht.size, fehler)
    }

    @Test
    fun stichprobeIstBreitGenug() {
        assertTrue(echtEn.size + unechtEn.size >= 60 && echtDe.size + unechtDe.size >= 60)
        assertTrue(echtEn.size >= 30 && unechtEn.size >= 30 && echtDe.size >= 30 && unechtDe.size >= 30)
        assertTrue((echtEn + unechtEn + echtDe + unechtDe).count { it.bericht } >= 20)
    }

    @Test
    fun trefferquoteAufDerStichprobe() {
        for ((name, echt, unecht) in listOf(Triple("EN", echtEn, unechtEn), Triple("DE", echtDe, unechtDe))) {
            val (richtig, alle, fehler) = trefferquote(echt, unecht)
            println("Trefferquote $name: $richtig von $alle (${100 * richtig / alle} %)")
            fehler.forEach { println("  $name $it") }
            assertTrue("Quote $name: $richtig von $alle\n" + fehler.joinToString("\n"), richtig.toDouble() / alle >= 0.9)
        }
    }

    @Test
    fun trefferquoteAufDerZweitenStichprobe() {
        var richtig = 0
        var alle = 0
        for ((name, echt, unecht) in listOf(Triple("EN", echtEnNeu, unechtEnNeu), Triple("DE", echtDeNeu, unechtDeNeu))) {
            val (r, a, fehler) = trefferquote(echt, unecht)
            println("Trefferquote neue Stichprobe $name: $r von $a (${100 * r / a} %)")
            fehler.forEach { println("  $name $it") }
            richtig += r; alle += a
        }
        assertTrue("neue Stichprobe: $richtig von $alle", richtig.toDouble() / alle >= 0.8)
    }

    @Test
    fun beispieleAusDemPruefberichtSindAlleRichtig() {
        val bericht = (echtEn + unechtEn + echtDe + unechtDe).filter { it.bericht }
        val echt = (echtEn + echtDe).filter { it.bericht }
        val durch = Durchbrueche.filtern(roh(bericht), jetzt).map { it.titel }.toSet()
        for (k in bericht) assertEquals(k.titel, k in echt, k.titel in durch)
    }

    @Test
    fun keineEchtenSindNurWegenEinesZeichensDrin() {
        // Teilmenge der Gegenfaelle einzeln ohne Thema der Suche: die Funktion allein entscheidet schon
        for (k in unechtEn + unechtDe) assertFalse(k.titel, Durchbrueche.durchbruch(k.titel, k.quelle))
    }

    // Ganzer Weg: Google-News-Rohtext (Herausgeber im Titel und in <source>) bis zum Filter
    private fun googleXml(liste: List<K>, zeit: String): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><rss version=\"2.0\"><channel><title>Google News</title>")
        liste.forEachIndexed { i, k ->
            val titel = k.titel.replace("&", "&amp;")
            append("<item><title>").append(titel).append(" - ").append(k.quelle.replace("&", "&amp;")).append("</title>")
            append("<link>https://news.google.com/rss/articles/CBMi").append(i).append("</link>")
            append("<guid isPermaLink=\"false\">CBMi").append(i).append("</guid>")
            append("<pubDate>").append(zeit).append("</pubDate>")
            append("<description>&lt;a href=\"https://example.org/\"&gt;").append(titel).append("&lt;/a&gt;</description>")
            append("<source url=\"https://example.org\">").append(k.quelle.replace("&", "&amp;")).append("</source></item>")
        }
        append("</channel></rss>")
    }

    @Test
    fun vomRssRohtextBisZumFilter() {
        for ((echt, unecht) in listOf(echtEn to unechtEn, echtDe to unechtDe)) {
            val alle = echt + unecht
            val eintraege = Rss.lesen(googleXml(alle, "Fri, 06 Nov 2026 12:00:00 GMT"), "Google News", Rss.Kategorie.UNTERNEHMEN, google = true)
            assertEquals(alle.size, eintraege.size)
            // Herausgeber aus <source>, Titel ohne Anhang
            assertEquals(alle[0].titel, eintraege[0].titel)
            assertEquals(alle[0].quelle, eintraege[0].herausgeber)
            val rohe = eintraege.mapIndexed { i, e -> Durchbrueche.Roh(alle[i].thema, e.titel, e.link, e.zeit, e.herausgeber) }
            val durch = Durchbrueche.filtern(rohe, jetzt).map { it.titel }.toSet()
            val richtig = echt.count { it.titel in durch } + unecht.count { it.titel !in durch }
            assertTrue("$richtig von ${alle.size}", richtig.toDouble() / alle.size >= 0.9)
            // dieselbe Entscheidung wie ohne Umweg ueber den Rohtext
            val direkt = Durchbrueche.filtern(roh(alle), jetzt).map { it.titel }.toSet()
            assertEquals(direkt, durch)
        }
    }

    @Test
    fun pressemitteilungsquelleSperrtZulassungenNicht() {
        val z = K("medizin", "Pharma Inc. announces FDA approval of first treatment for rare muscle disease", "Business Wire")
        assertTrue(Durchbrueche.filtern(roh(listOf(z)), jetzt).isNotEmpty())
        val k = K("medizin", "Pharma Inc. announces new CEO and board changes after FDA meeting", "Business Wire")
        assertTrue(Durchbrueche.filtern(roh(listOf(k)), jetzt).isEmpty())
    }

    @Test
    fun suchtreffernMussDasThemaPassen() {
        // Zeichen vorhanden, aber der Titel nennt das Thema nicht
        val k = listOf(K("quanten", "Company opens world's first flagship store in Paris", "Le Monde"))
        assertTrue(Durchbrueche.filtern(roh(k), jetzt).isEmpty())
    }

    @Test
    fun dublettenBleibenEinmalUndDieNeuesteGewinnt() {
        val titel = echtEn.first { it.titel.startsWith("NVision") }.titel
        val a = Durchbrueche.Roh("quanten", titel, "https://example.org/x1", gestern, "EurekAlert!")
        val b = Durchbrueche.Roh("quanten", titel.replace("unveils", "presents"), "https://example.org/x2", gestern + 3_600_000L, "The Quantum Insider")
        val q = Durchbrueche.filtern(listOf(a, b), jetzt)
        assertEquals(1, q.size)
        assertEquals("https://example.org/x2", q[0].link)
    }

    @Test
    fun feedmeldungenBekommenEinThemaNachStichwoertern() {
        val feed = listOf(
            K(null, "NASA's Perseverance Mars rover breaks historic record after traveling 28 miles", "ScienceDaily"),
            K(null, "Scientists overcome a hidden obstacle to more powerful quantum computers", "Science Daily"),
        )
        val liste = Durchbrueche.filtern(roh(feed), jetzt)
        assertEquals(2, liste.size)
        assertEquals("raumfahrt", liste.first { it.titel.startsWith("NASA's Perseverance") }.thema)
        assertEquals("raumfahrt", Durchbrueche.zuordnen("Starship reaches orbit again")?.id)
        assertEquals("fusion", Durchbrueche.zuordnen("Tokamak reaches new plasma record")?.id)
        assertEquals("krebs", Durchbrueche.zuordnen("New CAR-T therapy clears leukemia")?.id)
        assertNotNull(Durchbrueche.zuordnen("Scientists build a new quantum sensor"))
        assertNull(Durchbrueche.zuordnen("Don't miss: Meteor showers light up the night sky"))
    }

    @Test
    fun themenOhneNeuesStehenMitInhaltTrotzdemDa() {
        val inhalt = Durchbrueche.lesenInhalt(
            """{"v":1,"themen":[{"id":"krebs","e":[{"j":"2027","je":"2027","bis":"2027-12","de":"a","en":"a","u":"https://x.org","q":"X"}]},{"id":"klima","r":[{"j":"2022","de":"b","en":"b"}]}]}""",
        )
        assertNotNull(inhalt)
        val meldungen = Durchbrueche.filtern(roh(echtEn), jetzt)
        val blocks = Durchbrueche.ansicht(inhalt, meldungen, jetzt)
        val ids = blocks.map { it.thema.id }
        // Themen mit Neuem zuerst
        assertTrue(ids.indexOf("quanten") < ids.indexOf("klima"))
        assertTrue("klima" in ids)
        assertEquals("2027", blocks.first { it.thema.id == "krebs" }.inhalt!!.e[0].marke(true))
        // Nach 20 Tagen ist alles Neue zu alt, nur Inhalt bleibt
        val spaet = Durchbrueche.ansicht(inhalt, meldungen, jetzt + 20 * 86_400_000L)
        assertTrue(spaet.all { it.neu.isEmpty() })
        // Seit 0.79 (u68) bleiben Themen mit aelteren Meldungen unter „Zuletzt“ stehen; die uebrigen nur mit Inhalt
        assertTrue(spaet.map { it.thema.id }.containsAll(setOf("krebs", "klima")))
        assertTrue(spaet.filter { it.thema.id !in setOf("krebs", "klima") }.all { it.zuletzt.isNotEmpty() })
    }

    @Test
    fun abgelaufeneErwarteteEintraegeVerschwinden() {
        val inhalt = Durchbrueche.lesenInhalt(
            """{"v":2,"stand":"2026-11","themen":[{"id":"krebs","e":[
                {"j":"Mär 2027","bis":"2027-03","de":"spaet","en":"late","u":"https://x.org/2"},
                {"j":"Jan 2027","bis":"2027-01","de":"frueh","en":"early","u":"https://x.org/1"},
                {"j":"Dez 2026","bis":"2026-12","de":"bald","en":"soon","u":"https://x.org/0"}]}]}""",
        )!!
        val ende2026 = Rss.zeit("Wed, 02 Dec 2026 00:00:00 GMT")
        val b = Durchbrueche.ansicht(inhalt, emptyList(), ende2026).first()
        assertEquals(listOf("bald", "frueh", "spaet"), b.erwartet(ende2026).map { it.de })
        val februar = Rss.zeit("Mon, 01 Feb 2027 00:00:00 GMT")
        assertEquals(listOf("spaet"), b.erwartet(februar).map { it.de })
        assertTrue(b.erwartet(Rss.zeit("Sat, 03 Apr 2027 00:00:00 GMT")).isEmpty())
        // Eintrag ohne Datum zaehlt als nicht abgelaufen nicht: er faellt weg, damit nichts Altes stehen bleibt
        val ohne = Durchbrueche.Eintrag(j = "2027", de = "x", en = "x")
        assertFalse(Durchbrueche.erwartetNoch(ohne, jetzt))
    }

    @Test
    fun angekuendigteStehenVorDenPlaenen() {
        val inhalt = Durchbrueche.lesenInhalt(
            """{"v":3,"stand":"2026-11","themen":[{"id":"krebs","e":[
                {"j":"Jan 2027","bis":"2027-01","art":"hersteller","de":"plan","en":"plan","u":"https://x.org/1"},
                {"j":"Mär 2027","bis":"2027-03","art":"angekuendigt","de":"termin","en":"date","u":"https://x.org/2"},
                {"j":"Dez 2026","bis":"2026-12","art":"register","de":"studie","en":"study","u":"https://x.org/0"}]}]}""",
        )!!
        val b = Durchbrueche.ansicht(inhalt, emptyList(), jetzt).first()
        assertEquals(listOf("termin", "studie", "plan"), b.erwartet(jetzt).map { it.de })
    }

    @Test
    fun gebuendelterInhaltIstGueltigUndVollstaendig() {
        val text = File(listOf(File("src/main/assets"), File("../app/src/main/assets")).first { it.exists() }, Durchbrueche.INHALT_DATEI).readText(Charsets.UTF_8)
        val inhalt = Durchbrueche.lesenInhalt(text)
        assertNotNull(inhalt)
        inhalt!!
        assertTrue(inhalt.stand.matches(Regex("\\d{4}-\\d{2}(-\\d{2})?")))
        val standMonat = inhalt.stand.take(7)
        // Gepflegten Inhalt haben die Themen der ersten Fassung; die Themen der uebrigen Trend-Zeilen leben von Meldungen
        val gepflegt = inhalt.themen.map { it.id }.toSet()
        assertTrue(Durchbrueche.THEMEN.map { it.id }.toSet().containsAll(gepflegt))
        assertTrue(Durchbrueche.THEMEN.take(14).all { it.id in gepflegt })
        val strich = { s: String -> s.any { it == '–' || it == '—' } }
        for (t in inhalt.themen) {
            assertTrue("${t.id} Rückblick ${t.r.size}", t.r.size in 6..10)
            assertTrue("${t.id} Erwartet ${t.e.size}", t.e.size in 3..6)
            assertTrue("${t.id} Entwicklung ${t.w.size}", t.w.size in 3..6)
            val links = (t.e + t.w + t.r).map { it.u }
            assertEquals("${t.id} doppelte Links", links.size, links.toSet().size)
            for (e in t.e) {
                assertTrue("${t.id} bis: ${e.de}", e.bis.matches(Regex("\\d{4}-(0[1-9]|1[0-2])")))
                assertTrue("${t.id} bis nach Stand: ${e.de}", e.bis > standMonat)
                assertTrue("${t.id} Marke: ${e.de}", e.j.isNotBlank() && e.je.isNotBlank())
            }
            val arten = setOf("angekuendigt", "hersteller", "betreiber", "register")
            for (e in t.e) assertTrue("${t.id} Art: ${e.de}", e.art in arten)
            // angekündigte zuerst, danach nach Datum
            assertEquals("${t.id} Reihenfolge", t.e.sortedWith(compareBy({ it.art != "angekuendigt" }, { it.bis })).map { it.de }, t.e.map { it.de })
            if (t.id != "halbleiter" && t.id != "materialien") assertTrue("${t.id} ohne angekündigten Termin", t.e.any { it.art == "angekuendigt" })
            for (e in t.e.filter { it.art == "register" }) assertTrue("${t.id} Register: ${e.de}", e.de.contains("Phase") && e.de.lowercase().contains("erwartet"))
            for (e in t.w) assertTrue("${t.id} Stand fehlt: ${e.de}", e.s.isNotBlank() && e.se.isNotBlank())
            for (e in t.r) {
                assertTrue("${t.id} Jahr: ${e.de}", e.j.matches(Regex("\\d{4}")))
                assertTrue("${t.id} Jahr ${e.j}", e.j.toInt() <= standMonat.take(4).toInt())
            }
        }
        val alle = inhalt.themen.flatMap { it.e + it.w + it.r } + inhalt.beschleunigung.eintraege
        for (e in alle) {
            assertTrue(e.de, e.de.isNotBlank() && e.en.isNotBlank())
            assertTrue(e.u, e.u.startsWith("https://"))
            assertFalse(e.de, strich(e.de))
            assertFalse(e.en, strich(e.en))
            assertFalse(e.de, e.de.contains("…") || e.en.contains("…"))
            assertTrue(e.q, e.q.isNotBlank())
        }
        val reihen = inhalt.beschleunigung.reihen
        assertTrue(reihen.size >= 6)
        for (r in reihen) {
            assertTrue(r.id, r.p.size >= 6 && r.p.all { it.size == 2 && it[1] > 0 })
            assertEquals("${r.id} Endjahr", r.p.last()[0].toInt(), r.endjahr())
            assertTrue("${r.id} Endjahr fehlt", r.ende > 0)
            assertEquals(r.id, r.p.map { it[0] }.sorted(), r.p.map { it[0] })
            assertTrue(r.id, r.u.startsWith("https://") && r.q.isNotBlank() && r.lz.isNotBlank())
            assertTrue(r.id, r.wd.isNotBlank() && r.we.isNotBlank() && r.de.isNotBlank() && r.en.isNotBlank())
            assertFalse(r.id, strich(r.de + r.en + r.wd + r.we + r.hd + r.he))
            assertNotNull(r.id, r.verdopplung())
        }
        assertEquals(inhalt.beschleunigung.reihen.map { it.id }.size, inhalt.beschleunigung.reihen.map { it.id }.toSet().size)
        assertTrue(inhalt.beschleunigung.eintraege.size >= 4)
    }

    @Test
    fun verdopplungszeitWirdAusDenDatenGerechnet() {
        // Alle zwei Jahre halbiert
        val fall = Durchbrueche.Reihe("a", "a", "a", p = (0..8).map { listOf(2000.0 + it * 2, 1024.0 / Math.pow(2.0, it.toDouble())) })
        val f = fall.verdopplung()!!
        assertTrue(f.faellt)
        assertEquals(2.0, f.jahre, 0.01)
        // Alle sechs Monate verdoppelt
        val steigt = Durchbrueche.Reihe("b", "b", "b", p = (0..9).map { listOf(2020.0 + it * 0.5, Math.pow(2.0, it.toDouble())) })
        val s = steigt.verdopplung()!!
        assertFalse(s.faellt)
        assertEquals(0.5, s.jahre, 0.01)
        assertEquals(6, s.monate)
        assertNull(Durchbrueche.Reihe("c", "c", "c", p = listOf(listOf(2020.0, 5.0), listOf(2021.0, 5.0), listOf(2022.0, 5.0))).verdopplung())
        assertNull(Durchbrueche.Reihe("d", "d", "d", p = listOf(listOf(2020.0, 5.0))).verdopplung())
    }

    @Test
    fun alterInMinutenStundenTagen() {
        assertEquals(Durchbrueche.Einheit.MINUTEN, Durchbrueche.alter(jetzt - 5 * 60_000L, jetzt).einheit)
        val h = Durchbrueche.alter(jetzt - 3 * 3_600_000L - 100, jetzt)
        assertEquals(3L, h.wert)
        assertEquals(Durchbrueche.Einheit.STUNDEN, h.einheit)
        assertEquals(Durchbrueche.Einheit.TAGE, Durchbrueche.alter(jetzt - 2 * 86_400_000L, jetzt).einheit)
    }

    @Test
    fun speicherKreislauf() {
        val liste = Durchbrueche.filtern(roh(echtEn), jetzt)
        val text = Durchbrueche.schreiben(Durchbrueche.Stand(jetzt, liste))
        val zurueck = Durchbrueche.lesenGespeichert(text)
        assertNotNull(zurueck)
        assertEquals(liste.size, zurueck!!.liste.size)
        assertTrue(Durchbrueche.gueltig(zurueck.abgerufen, jetzt + 1000))
        assertFalse(Durchbrueche.gueltig(zurueck.abgerufen, jetzt + Durchbrueche.GUELTIG_MS))
    }

    @Test
    fun teilausfallVerdraengtVorhandeneMeldungenNicht() {
        val alt = Durchbrueche.filtern(roh(echtEn.filter { it.thema == "krebs" }), jetzt)
        val neu = Durchbrueche.filtern(roh(echtEn.filter { it.thema == "krebs" }.take(1), gestern + 3_600_000L), jetzt)
        assertTrue(alt.size > neu.size)
        // Eine Suche des Themas scheiterte: die alten Meldungen bleiben, neue kommen hinzu, keine doppelt
        val vereint = Durchbrueche.zusammenfuehren(neu, alt, setOf("krebs"), jetzt)
        assertEquals(alt.map { it.titel }.toSet(), vereint.map { it.titel }.toSet())
        // Ohne Ausfall zaehlt nur die neue Abfrage
        assertEquals(neu.map { it.titel }, Durchbrueche.zusammenfuehren(neu, alt, emptySet(), jetzt).map { it.titel })
    }

    @Test
    fun suchadresseHatZeitfensterUndKeineBoersenwoerter() {
        val s = Durchbrueche.suchbegriff(Durchbrueche.THEMEN[0], true)
        assertTrue(s.endsWith("when:14d"))
        assertTrue(s.contains("-stock"))
    }
}
