package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Abhaengigkeiten.Eingabe
import de.troetenrepublik.app.engine.Abhaengigkeiten.Zeilentyp
import de.troetenrepublik.app.engine.Einflussverzeichnis.Wertbild
import de.troetenrepublik.app.engine.Kernaussagen.Typ
import de.troetenrepublik.app.ui.landKurz
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

// Abhaengigkeiten (0.77): Ein Rohstoff, den eine Firma selbst sucht oder foerdert, ist keine Abhaengigkeit vom groessten
// Foerderland. Dazu die kurzen Laendernamen fuer Werte in Zeilen.
class Fassung077ErkunderTest {
    private fun datei(name: String): String =
        listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }.readText()

    private val index by lazy { Einflussverzeichnis.aufbauen(datei(WeltDaten.EINFLUSS), datei(WeltDaten.ROHSTOFFE)) }
    private val heute = LocalDate.of(2026, 11, 11).toEpochDay()

    private fun treffer(w: Wertbild) = Rohstoffprofil.fuer(index, w).treffer.associateBy { it.id }
    private fun aussagen(w: Wertbild) = Kernaussagen.ableiten(index, Abhaengigkeiten.karte(index, Eingabe(w), heute), null)

    private val erkunder = "Grizzly Discoveries Inc. engages in the acquisition, exploration, and development of mineral properties in Canada. " +
        "It explores for gold, silver, copper, cobalt, and other precious and base metal deposits."

    @Test(timeout = 20_000)
    fun erkunderHaengtNichtAmFoerderland() {
        // Unabhaengig davon, wie der Katalog die Branche nennt
        for (w in listOf(
            Wertbild("GZD.V", "ROHSTOFFE", "CA", "CA", industrie = "Other Industrial Metals & Mining", beschreibung = erkunder),
            Wertbild("GZD.V", "BERGBAU", "CA", "CA", beschreibung = erkunder),
            Wertbild("GZD.V", null, "CA", "CA", beschreibung = erkunder),
        )) {
            val t = treffer(w)
            assertTrue("Kobalt fehlt bei ${w.branche}", "KOBALT" in t)
            assertEquals(Rohstoffprofil.ERKUNDUNG, t.getValue("KOBALT").verwendung)
            assertTrue(t.getValue("KOBALT").selbst)
            assertTrue(t.getValue("KUPFER").selbst)
            val a = aussagen(w)
            val eigen = a.single { it.typ == Typ.FOERDERT }
            assertTrue("KOBALT" in eigen.liste.map { it.schluessel })
            assertTrue(eigen.liste.size <= Kernaussagen.MAX_EIGENE)
            // Keine Aussage ueber den Kongo und keine Zaehlung als kritischer Rohstoff
            assertTrue(a.none { it.typ == Typ.ROHSTOFFE && it.land == "CD" })
            assertTrue(a.none { it.typ == Typ.KRITISCH })
        }
    }

    @Test(timeout = 20_000)
    fun weitereBeschreibungenVonFoerderern() {
        val texte = listOf(
            "The company is a junior mining company focused on cobalt and nickel projects in Ontario.",
            "An exploration-stage company that holds interests in mineral claims prospective for cobalt.",
            "Engages in the acquisition and exploration of mineral properties. Its flagship project hosts cobalt and copper.",
        )
        for (text in texte) {
            val t = treffer(Wertbild("X.V", null, "CA", "CA", beschreibung = text))
            assertTrue(text, t.getValue("KOBALT").selbst)
        }
        // Foerderer mit eigenem Profil galten schon als Foerderung und zaehlen genauso
        assertTrue(treffer(Wertbild("X.PROBE", "BERGBAU", "CA", "CA", beschreibung = "Operates a cobalt mine and a cobalt refinery.")).getValue("KOBALT").selbst)
    }

    @Test(timeout = 20_000)
    fun abnehmerBleibenAbhaengig() {
        // Ein Akkuhersteller kauft Kobalt ein: Die Abhaengigkeit vom Kongo bleibt
        val akku = Wertbild("AKKU", null, "US", "US", beschreibung = "The company makes cathode powders that contain cobalt.")
        val t = treffer(akku)
        assertFalse(t.getValue("KOBALT").selbst)
        val a = aussagen(akku)
        assertTrue(a.none { it.typ == Typ.FOERDERT })
        assertTrue(a.any { it.typ == Typ.ROHSTOFFE && it.land == "CD" && it.liste.any { p -> p.schluessel == "KOBALT" } })
        // Ein Autobauer ebenso
        val auto = Wertbild("AUTO", "FAHRZEUGE", "DE", "DE")
        assertTrue(treffer(auto).values.none { it.selbst })
        assertTrue(aussagen(auto).none { it.typ == Typ.FOERDERT })
        // Zulieferer des Bergbaus sind keine Foerderer
        val bagger = Wertbild("BAGGER", "MASCHINEN", "US", "US", beschreibung = "Manufactures excavators and haul trucks for mining companies and uses cobalt alloys.")
        assertFalse(treffer(bagger).getValue("KOBALT").selbst)
    }

    @Test(timeout = 20_000)
    fun zeileOhneFoerderlandBeiEigenerSuche() {
        val w = Wertbild("GZD.V", "BERGBAU", "CA", "CA", beschreibung = erkunder)
        val z = Abhaengigkeiten.karte(index, Eingabe(w), heute).filter { it.typ == Zeilentyp.ROHSTOFF }
        assertTrue(z.isNotEmpty())
        assertTrue(z.first { it.schluessel == "KOBALT" }.rohstoff!!.selbst)
    }

    // ---------------------------------------------------------------- jede Branche: Rolle des Rohstoffs
    private fun rolle(w: Wertbild, id: String) = treffer(w)[id]?.rolle

    @Test(timeout = 20_000)
    fun eigenesErzeugnisInJederBranche() {
        val faelle = listOf(
            // Chemie: stellt Kobalt- und Nickelsalze her
            Wertbild("CHEM", "CHEMIE", "FI", "FI", beschreibung = "The company produces nickel, cobalt and copper chemicals for industrial customers.") to "KOBALT",
            // Rueckgewinnung
            Wertbild("RECY", "UMWELT", "US", "US", beschreibung = "It operates plants for the recycling of lithium, cobalt and nickel from spent batteries.") to "KOBALT",
            // Lizenzgesellschaft
            Wertbild("ROY", "FINANZEN", "CA", "CA", beschreibung = "The company holds a portfolio of cobalt and nickel royalties and streams.") to "KOBALT",
            // Mischkonzern mit eigener Raffinerie
            Wertbild("KONZ", "MASCHINEN", "JP", "JP", beschreibung = "Its metals segment operates a cobalt refinery and sells electrolytic cobalt.") to "KOBALT",
            // Handelshaus mit eigener Gewinnung
            Wertbild("HANDEL", null, "CH", "CH", beschreibung = "The group engages in the production and marketing of copper, cobalt, zinc and nickel.") to "KOBALT",
        )
        for ((w, id) in faelle) {
            assertEquals(w.symbol, Rohstoffprofil.Rolle.SELBST, rolle(w, id))
            val a = aussagen(w)
            assertTrue(w.symbol, a.any { it.typ == Typ.FOERDERT && it.liste.any { p -> p.schluessel == id } })
            assertTrue(w.symbol, a.none { it.typ == Typ.ROHSTOFFE && it.land == "CD" })
            // Kein Bedarf: zaehlt weder im Embargo-Szenario noch in der Rohstoffbindung
            assertTrue(w.symbol, Einflussverzeichnis.rohstoffeFuerWert(index, w).none { it.id == id })
        }
        // Aluminiumhuette: Die Zahlen messen die Huettenerzeugung, die Firma ist selbst Erzeuger
        val alu = Wertbild("ALU", "ROHSTOFFE", "NO", "NO", industrie = "Aluminum")
        assertEquals(Rohstoffprofil.Rolle.SELBST, rolle(alu, "ALUMINIUM"))
        assertTrue(aussagen(alu).none { it.typ == Typ.ROHSTOFFE && it.liste.any { p -> p.schluessel == "ALUMINIUM" } })
        // Kupferhuette: Die Zahlen messen die Minen, die Huette kauft Konzentrat und bleibt abhaengig
        val huette = treffer(Wertbild("X.PROBE", null, "DE", "DE", beschreibung = "Operates blast furnaces and a steel mill."))
        assertEquals(Rohstoffprofil.Rolle.EINSATZ, huette.getValue("EISENERZ").rolle)
    }

    @Test(timeout = 20_000)
    fun blosseNennungIstKeineAbhaengigkeit() {
        // Bank, Fonds, Berater: Der Rohstoff kommt nur vor
        val texte = listOf(
            "The bank finances infrastructure and advises clients in the cobalt market.",
            "The fund invests in companies exposed to battery metals such as cobalt.",
            "Provides market research on cobalt prices to institutional clients.",
        )
        for (text in texte) {
            val w = Wertbild("NENN", "FINANZEN", "GB", "GB", beschreibung = text)
            assertEquals(text, Rohstoffprofil.Rolle.GENANNT, rolle(w, "KOBALT"))
            val a = aussagen(w)
            assertTrue(text, a.none { it.typ == Typ.ROHSTOFFE || it.typ == Typ.KRITISCH || it.typ == Typ.FOERDERT })
            assertTrue(Einflussverzeichnis.rohstoffeFuerWert(index, w).isEmpty())
        }
    }

    @Test(timeout = 20_000)
    fun einsatzstoffBleibtAbhaengigkeit() {
        // Verwendung aus der Beschreibung bekannt
        val zelle = Wertbild("ZELLE", null, "KR", "KR", beschreibung = "The company manufactures lithium-ion battery cells for electric vehicles.")
        assertEquals(Rohstoffprofil.Rolle.EINSATZ, rolle(zelle, "LITHIUM"))
        // Verwendung aus der Branche bekannt, in der Beschreibung genannt
        val auto = Wertbild("AUTO2", "FAHRZEUGE", "DE", "DE", beschreibung = "The group builds cars; battery materials include cobalt.")
        assertEquals(Rohstoffprofil.Rolle.EINSATZ, rolle(auto, "KOBALT"))
        // Bei der SEC im Rohstoffabschnitt genannt
        val sec = Rohstoffprofil.fuer(index, Wertbild("SECX", "FINANZEN", "US", "US"), Rohstoffprofil.SecAngaben("", listOf("KOBALT"))).treffer.single()
        assertEquals(Rohstoffprofil.Rolle.EINSATZ, sec.rolle)
        // Ein anderes Erzeugnis hinter dem Rohstoff ist kein eigenes Erzeugnis
        assertFalse(Rohstoffprofil.erzeugt("the company produces cobalt alloys.", 21..26))
        assertTrue(Rohstoffprofil.eingesetzt("the company produces cobalt alloys.", 21..26))
        assertTrue(Rohstoffprofil.eingesetzt("its raw materials include steel; it purchases nickel, cobalt and chromium.", 54..59))
        assertFalse(Rohstoffprofil.eingesetzt("advises clients in the cobalt market.", 23..28))
        val legierung = Wertbild("LEG", null, "US", "US", beschreibung = "The company produces cobalt alloys for turbine blades.")
        assertEquals(Rohstoffprofil.EINSATZSTOFF, treffer(legierung).getValue("KOBALT").verwendung)
        assertEquals(Rohstoffprofil.Rolle.EINSATZ, rolle(legierung, "KOBALT"))
        assertFalse(Rohstoffprofil.erzeugt("produces electric vehicles using cobalt.", 33..38))
        assertTrue(Rohstoffprofil.erzeugt("the company produces nickel, cobalt and copper.", 29..34))
        assertTrue(Rohstoffprofil.erzeugt("designs tools with steel and mines cobalt in ontario.", 35..40))
    }

    // ---------------------------------------------------------------- Befunde der Stichprobe (274 Aktien, 11.11.2026)
    private val BERGBAU_INDUSTRIE = "Other Industrial Metals & Mining"

    @Test(timeout = 20_000)
    fun foerdererMitProduktwoertern() {
        // Foerderer Seltener Erden, der auch Magnete baut
        val erden = treffer(Wertbild("SE.PROBE", "BERGBAU", "US", "US", industrie = BERGBAU_INDUSTRIE, beschreibung = "Produces rare earth materials. It owns and operates a rare earth mine and manufactures NdFeB permanent magnets."))
        assertEquals(setOf("SELTENE_ERDEN"), erden.keys)
        assertTrue(erden.getValue("SELTENE_ERDEN").selbst)
        // Lithiumfoerderer, der seine Abnehmer nennt
        val lithium = treffer(Wertbild("LI.PROBE", null, "BR", "BR", industrie = BERGBAU_INDUSTRIE, beschreibung = "Engages in the exploration and development of lithium deposits in Brazil. It serves lithium-ion battery supply chain for electric vehicle industries."))
        assertEquals(setOf("LITHIUM"), lithium.keys)
        assertTrue(lithium.getValue("LITHIUM").selbst)
        // Erkunder mit Raffinerie: keine Rohstoffe aus der Lieferkette der Abnehmer
        val raffinerie = treffer(Wertbild("RA.PROBE", null, "CA", "CA", industrie = BERGBAU_INDUSTRIE, beschreibung = "Engages in the acquisition and exploration of resource properties. The company explores cobalt, copper, and silver deposits. It also operates a hydrometallurgical cobalt refinery for producing battery materials for the electric vehicle supply chain."))
        assertEquals(setOf("KOBALT", "KUPFER", "SILBER"), raffinerie.keys)
        assertTrue(raffinerie.values.all { it.selbst })
        // Mineralsande: Titandioxid ist das eigene Erzeugnis
        val sande = treffer(Wertbild("MS.PROBE", null, "AU", "AU", industrie = BERGBAU_INDUSTRIE, beschreibung = "Engages in the mining and processing of mineral sands. It produces zircon and titanium dioxide products of rutile and ilmenite."))
        assertTrue(sande.getValue("TITAN").selbst)
        // Kupferfoerderer, der die Verwendung seines Metalls beschreibt
        val kupfer = treffer(Wertbild("CU.PROBE", null, "CL", "CL", industrie = "Copper", beschreibung = "Mines copper, which is used in wind turbines, solar panels and electric motors."))
        assertEquals(setOf("KUPFER"), kupfer.keys)
        // Ein Stahlwerk steht im Katalog unter Bergbau, kauft Zink aber ein
        val stahl = treffer(Wertbild("ST.PROBE", "BERGBAU", "DE", "DE", industrie = "Steel", beschreibung = "Operates blast furnaces and produces galvanized sheet using zinc."))
        assertEquals(Rohstoffprofil.Rolle.EINSATZ, stahl.getValue("ZINK").rolle)
        assertEquals(Rohstoffprofil.Rolle.EINSATZ, stahl.getValue("EISENERZ").rolle)
        // Rechenzentren fuer Kryptowaehrungen sind kein Bergbau
        assertFalse(Rohstoffprofil.bergbau(Wertbild("BT.PROBE", null, "US", "US"), "", "is a bitcoin mining company with copper busbars in its data centers."))
        // Phosphat aus eigenen Minen
        assertTrue(treffer(Wertbild("PH.PROBE", "CHEMIE", "US", "US", beschreibung = "Owns and operates phosphate rock mines and produces phosphate fertilizers.")).getValue("PHOSPHAT").selbst)
    }

    @Test(timeout = 20_000)
    fun produktwoerterOhneHersteller() {
        fun ids(text: String, industrie: String? = null) = treffer(Wertbild("X.PROBE", null, "US", "US", industrie = industrie, beschreibung = text)).keys
        // Batteriehersteller ist kein Fahrzeugbauer
        val zellen = ids("The company offers automotive batteries, which include lithium-ion battery cells and packs for use in electric vehicle batteries.")
        assertTrue("LITHIUM" in zellen)
        assertFalse("SELTENE_ERDEN" in zellen)
        // Fahrzeugbauer bleibt
        assertTrue("SELTENE_ERDEN" in ids("Designs, manufactures and sells electric vehicles."))
        // Lichtleiter-Sensoren in Medizingeraeten sind keine Glasfaserfertigung
        assertFalse("GERMANIUM" in ids("Offers a pressure guidewire and fiber optic sensor solutions used in medical devices."))
        assertTrue("GERMANIUM" in ids("Manufactures optical fiber and fiber optic cables for telecom networks."))
        // Netzbetreiber
        assertFalse("GOLD" in ids("The wholesale segment provides interconnection services and broadband access."))
        assertFalse("GOLD" in ids("Provides mobile connectivity solutions to communications service providers."))
        assertTrue("GOLD" in ids("Designs and manufactures electronic connectors and sensors."))
        // Karbonat- und Festoxidzellen brauchen kein Platin
        assertFalse("PLATIN" in ids("Engages in the production of high temperature fuel cells based on carbonate fuel cell technology."))
        assertTrue("PLATIN" in ids("Develops hydrogen fuel cells for heavy trucks."))
        // Vermieter und Fluggesellschaften bauen nichts
        val vermieter = ids("The leasing segment owns, leases, manages, and sells aircraft and aircraft engines, including 47 commercial aircraft.")
        assertFalse("TITAN" in vermieter)
        assertFalse("NICKEL" in vermieter)
        assertFalse("TITAN" in ids("Operates a fleet of 300 commercial aircraft."))
        assertTrue("NICKEL" in ids("Designs and produces commercial and defense aircraft engines."))
        assertFalse("NICKEL" in ids("Sells tooling systems used in the manufacture of airframes, aero engines, trucks and automobiles."))
        // Farbenhersteller: Die Laenderzahlen fuer Titan messen Titanschwamm und gelten fuer Pigment nicht
        assertFalse("TITAN" in ids("Manufactures paints and coatings based on titanium dioxide pigments."))
    }

    @Test(timeout = 20_000)
    fun unternehmenNurBeiEigenenAngaben() {
        // Die Aussage nennt Aluminium aus der Industrie; die Firmenangabe (Lithium) steht nicht in der Liste
        val w = Wertbild("QX.PROBE", "FAHRZEUGE", "US", "US", industrie = "Auto Parts", beschreibung = "Develops solid-state lithium-metal battery technology.")
        val a = aussagen(w).firstOrNull { it.typ == Typ.ROHSTOFFE }
        if (a != null && a.liste.none { it.schluessel == "LITHIUM" }) assertFalse(a.firma)
    }

    private fun mit(industrie: String, text: String, branche: String? = null) =
        treffer(Wertbild("X.PROBE", branche, "US", "US", industrie = industrie, beschreibung = text))

    @Test(timeout = 20_000)
    fun duengerMitEigenemBergbauGewinntPhosphatSelbst() {
        val integriert = "Acme Ltd. provides crop inputs and services. The company operates through three segments: Retail, Potash, and Phosphate. " +
            "The Potash segment includes mining and processing of potash. The Phosphate segment manufactures and sells solid and liquid phosphate fertilizers, phosphate feed, and purified phosphoric acid."
        val t = mit("Agricultural Inputs", integriert).getValue("PHOSPHAT")
        assertTrue(t.selbst)
        assertEquals(Rohstoffprofil.ERZEUGUNG, t.verwendung)
        // Ohne eigenen Bergbau bleibt Phosphat ein Einsatzstoff
        val kauf = "Beta ASA produces nitrogen fertilizers and NPK compounds. The company sells phosphate fertilizers and crop nutrition products to farmers."
        assertFalse(mit("Agricultural Inputs", kauf).getValue("PHOSPHAT").selbst)
    }

    @Test(timeout = 20_000)
    fun ausgangsstoffDerVerarbeitungIstKeinErzeugnis() {
        val amg = "AMG Test N.V. develops, produces, and sells energy storage materials. The company extracts spodumene; and produces technical-grade lithium carbonate. " +
            "It also produces high-purity natural graphite, as well as antimony trioxide from base forms of antimony metal."
        val t = mit("Other Industrial Metals & Mining", amg)
        assertFalse("Antimon aus Zukauf", t.getValue("ANTIMON").selbst)
        assertTrue(t.getValue("ANTIMON").einsatz)
        assertTrue(t.getValue("LITHIUM").selbst)
        // Wer das Metall selbst abbaut, bleibt Erzeuger
        val grube = "Gamma Corp. operates an antimony mine and produces antimony metal. It also makes antimony trioxide from antimony metal."
        assertTrue(mit("Other Industrial Metals & Mining", grube).getValue("ANTIMON").selbst)
    }

    @Test(timeout = 20_000)
    fun autoTeileTrennenHaendlerReifenbauerUndSoftware() {
        val reifen = "Tyreco Corp. develops, manufactures, distributes, and sells tires and related products in the Americas. It operates 750 retail outlets."
        val haendler = "Partsmart, Inc. provides automotive aftermarket parts. The company offers batteries, brakes, belts and hoses, and wiper blades through its stores and online."
        val software = "Selfdrive Innovation, Inc. engages in the self-driving technology business. It focuses on developing a platform that brings self-driving hardware, software, and data services together."
        val bauteile = "Wireco Inc. designs and manufactures wiring harnesses and electrical components for vehicles."
        val r = mit("Auto Parts", reifen)
        assertEquals(setOf("EISENERZ", "ZINK"), r.keys)
        assertTrue(mit("Auto Parts", haendler).isEmpty())
        assertTrue(mit("Auto Parts", software).isEmpty())
        assertTrue("KUPFER" in mit("Auto Parts", bauteile))
        // Ohne Beschreibung gilt die Industrie wie bisher
        assertTrue("KUPFER" in mit("Auto Parts", ""))
    }

    @Test(timeout = 20_000)
    fun lizenzhalterSuchenNichtSelbst() {
        val texte = listOf(
            "Rollo Corp. operates as a royalty and stream company focused on precious metals. It also manages its portfolio of gold, silver, and copper interests.",
            "Strimo Ltd. operates as a precious metal streaming company. It explores for gold, silver, and cobalt deposits in Canada and Peru.",
            "Aurex Inc. acquires and manages precious metal streams, royalties, and related interests. The financing covers gold, silver, nickel, and lithium projects.",
        )
        for (text in texte) {
            val w = Wertbild("L.PROBE", null, "CA", "CA", industrie = "Gold", beschreibung = text)
            val t = treffer(w)
            assertTrue(text, t.isNotEmpty())
            assertTrue(text, t.values.all { it.verwendung == Rohstoffprofil.LIZENZ && it.selbst })
            val a = aussagen(w)
            assertEquals(text, 2.0, a.single { it.typ == Typ.FOERDERT }.zahl, 0.0)
            assertTrue(a.none { it.typ == Typ.ROHSTOFFE })
        }
        // Nur genannte Metalle: Die Industrie "Gold" liefert keine Zeile dazu
        assertTrue("PLATIN" !in treffer(Wertbild("L.PROBE", null, "CA", "CA", industrie = "Gold", beschreibung = texte[0])))
        // Eine Mine mit Abgaben an Lizenzgeber ist keine Lizenzfirma, "Upstream Corporation" ebenso nicht
        val mine = Wertbild("M.PROBE", null, "CA", "CA", industrie = "Gold", beschreibung = "Digger Inc. operates a gold mine in Ontario and pays royalties to the land owner.")
        assertEquals(Rohstoffprofil.FOERDERUNG, treffer(mine).getValue("GOLD").verwendung)
        val alu = Wertbild("U.PROBE", null, "US", "US", industrie = "Aluminum", beschreibung = "Smelt Inc. produces aluminum. It was formerly known as Smelt Upstream Corporation.")
        assertTrue(treffer(alu).values.none { it.verwendung == Rohstoffprofil.LIZENZ })
    }

    @Test
    fun kurzeLaendernamen() {
        assertEquals("DR Kongo", landKurz("Demokratische Republik Kongo"))
        assertEquals("USA", landKurz("Vereinigte Staaten"))
        assertEquals("DR Congo", landKurz("Democratic Republic of the Congo"))
        assertEquals("China", landKurz("China"))
    }
}
