package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.FirmaFaktenTexte
import de.troetenrepublik.app.market.Behoerden
import de.troetenrepublik.app.market.FirmaEmissionen
import de.troetenrepublik.app.market.FirmaEntlassungen
import de.troetenrepublik.app.market.FirmaName
import de.troetenrepublik.app.market.FirmaPatente
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// Firmenbausteine Entlassungen, Patente und Emissionen: Namensabgleich, Rechenteile, Leser mit kurzen eigenen Auszuegen.
class Fassung079FirmaFaktenTest {

    @Test
    fun namensabgleichNurEindeutig() {
        val apple = FirmaName.schluessel(listOf("Apple"))
        assertTrue(FirmaName.passt("Apple Inc.", apple))
        assertFalse(FirmaName.passt("Applied Materials, Inc.", apple))
        assertFalse(FirmaName.passt("Apple Hospitality REIT", apple))
        val amazon = FirmaName.schluessel(listOf("Amazon"))
        assertTrue(FirmaName.passt("Amazon.com Services LLC", amazon))
        val exxon = FirmaName.schluessel(listOf("Exxon Mobil"))
        assertTrue(FirmaName.passt("ExxonMobil Corporation", exxon))
        val chevron = FirmaName.schluessel(listOf("Chevron"))
        assertTrue(FirmaName.passt("Chevron U.S.A. Inc.", chevron))
        assertFalse(FirmaName.passt("Chevron Phillips Chemical", chevron))
        assertEquals("", FirmaName.schluessel("AB"))
    }

    private fun e(d: String, s: String, n: Int, t: String = "") = Behoerden.Entlassung(d, s, n, "Ort", "", t)

    @Test
    fun entlassungenZwoelfMonateUndQuartale() {
        val heute = LocalDate.of(2026, 11, 13)
        val liste = listOf(
            e("2026-10-01", "CA", 100, "A"), e("2026-10-01", "CA", 100, "A"), e("2026-08-15", "TX", 50, "S"),
            e("2026-02-01", "WA", 10), e("2025-10-01", "NY", 999), e("2026-12-01", "CA", 5),
        )
        val a = FirmaEntlassungen.auswerten(liste, heute)!!
        // Doppelte und Meldungen nach heute zaehlen nicht, die Meldung vom 01.10.2025 liegt ausserhalb der zwoelf Monate
        assertEquals(3, a.meldungen)
        assertEquals(160, a.stellen)
        assertEquals(1, a.schliessungen)
        assertEquals("2026-10-01", a.liste.first().d)
        assertEquals(FirmaEntlassungen.QUARTALE, a.quartale.size)
        val q4 = a.quartale.last()
        assertEquals(2026, q4.jahr); assertEquals(4, q4.q); assertEquals(100, q4.stellen)
        assertEquals(50, a.quartale[a.quartale.size - 2].stellen)
        assertEquals(2025, a.quartale.first().jahr)
        assertNull(FirmaEntlassungen.auswerten(listOf(e("2024-01-01", "CA", 5)), heute))
    }

    private val emissionen = """{"format":1,"stand":"2026-11-13","jahr0":2012,"jahre":3,"firmen":[
        {"n":"Exxon Mobil Corporation","e":[100,160,150],"z":[1,2,2],"s":[["Baytown","TX","Baytown",120,100],["Beaumont","TX","Beaumont",30,60]]},
        {"n":"ExxonMobil Corp.","e":[0,0,50],"z":[0,0,1],"s":[["Joliet","IL","Joliet",50,100]]},
        {"n":"Tote Firma","e":[10,10,0],"z":[1,1,0],"s":[]}]}"""

    @Test
    fun emissionenLesenZuordnenAuswerten() {
        val d = FirmaEmissionen.lesen(emissionen)!!
        // Gleicher Schluessel (ExxonMobil) wird addiert
        assertEquals(2, d.firmen.size)
        val f = FirmaEmissionen.fuer(d, listOf("Exxon Mobil"))!!
        assertEquals(200.0, f.tonnen.last(), 0.0)
        assertEquals(160.0, f.tonnen[1], 0.0)
        assertNull(FirmaEmissionen.fuer(d, listOf("Shell")))
        val s = FirmaEmissionen.auswerten(d, f)!!
        assertEquals(2014, s.jahr)
        assertEquals(25.0, s.aenderung!!, 0.001)
        assertEquals(3, s.reihe.size)
        assertEquals("Baytown", s.standorte.first().name)
        // Keine Meldung im letzten Jahr: kein Stand
        assertNull(FirmaEmissionen.auswerten(d, FirmaEmissionen.fuer(d, listOf("Tote Firma"))!!))
        assertNull(FirmaEmissionen.lesen("""{"format":2}"""))
        assertNull(FirmaEmissionen.lesen("kaputt"))
    }

    private val emissionenEu = """{"format":1,"gebiet":"EU","stand":"2026-11-14","jahr0":2013,"jahre":3,"firmen":[
        {"y":["RWE.DE"],"n":"RWE","e":[300,200,100],"z":[3,2,2],"s":[["Kraftwerk A","DE","",70,80,90],["Kraftwerk B","NL","",30,20,10]]},
        {"y":["EXAMPLE"],"n":"Beispiel","e":[0,0,50],"z":[0,0,1],"s":[]}]}"""

    private val emissionenMitKuerzel = """{"format":1,"stand":"2026-11-13","jahr0":2012,"jahre":2,"firmen":[
        {"n":"Shell Petroleum Inc","y":["SHEL.L"],"e":[40,50],"z":[1,1],"s":[["Norco","LA","Norco",50,100]]},
        {"n":"SHELL PETROLEUM INC","y":["SHEL.L"],"e":[0,10],"z":[0,1],"s":[["Deer Park","TX","Deer Park",10,100]]}]}"""

    @Test
    fun emissionenEuKuerzelUndGemeinsamesJahr() {
        val eu = FirmaEmissionen.lesen(emissionenEu)!!
        assertEquals("EU", eu.gebiet)
        // Zuordnung ueber das Kuerzel, nicht ueber den Namen
        assertNull(FirmaEmissionen.fuer(eu, listOf("RWE")))
        val rwe = FirmaEmissionen.fuer(eu, listOf("RWE"), "RWE.DE")!!
        assertNull(FirmaEmissionen.fuer(eu, listOf("RWE"), "SIE.DE"))
        val allein = FirmaEmissionen.auswerten(eu, rwe)!!
        assertEquals(2015, allein.jahr)
        assertEquals(listOf("EU"), allein.gebiete)
        assertEquals("Kraftwerk A", allein.standorte.first().name)
        assertEquals(70.0, allein.standorte.first().tonnen, 0.0)
        assertEquals(2, allein.anlagen)
        assertEquals(-50.0, allein.aenderung!!, 0.001)
        // Firma mit Kuerzel ohne Namen ist ueber das Kuerzel zu finden
        assertNotNull(FirmaEmissionen.fuer(eu, emptyList(), "EXAMPLE"))
        // Kuerzel mit gleicher Firma aus der US-Datei: Namensvarianten werden addiert, beide Gebiete rechnen im gemeinsamen Jahr
        val us = FirmaEmissionen.lesen(emissionenMitKuerzel)!!
        val shell = FirmaEmissionen.fuer(us, emptyList(), "SHEL.L")!!
        assertEquals(60.0, shell.tonnen.last(), 0.0)
        assertEquals(2, shell.standorte.size)
        val teile = listOf(FirmaEmissionen.Teil(us, shell), FirmaEmissionen.Teil(eu, rwe))
        val beide = FirmaEmissionen.auswerten(teile)!!
        // US-Datei endet 2013, EU 2015: gerechnet wird 2013, EU-Standorte mit den Tonnen von vor zwei Jahren
        assertEquals(2013, beide.jahr)
        assertEquals(60.0 + 300.0, beide.tonnen, 0.0)
        assertEquals(listOf("US", "EU"), beide.gebiete)
        assertEquals(4, beide.standorte.size)
        assertEquals("Kraftwerk A", beide.standorte.first().name)
        assertEquals(90.0, beide.standorte.first().tonnen, 0.0)
        assertEquals(listOf(2013), beide.reihe.map { it.first })
        // Kein Vorjahr, wenn nicht alle Dateien es abdecken
        assertNull(beide.aenderung)
        assertEquals(1, FirmaEmissionen.teile("RWE.DE", emptyList(), listOf(eu, us, null)).size)
    }

    @Test
    fun patenteFacettenNamenFelderKern() {
        val facetten = FirmaPatente.facettenLesen(
            """{"count":900,"facets":[{"applicationMetaData.firstApplicantName":[{"value":"Applied Materials, Inc.","count":500},{"value":"Apple Inc.","count":300},{"value":"Apple Hospitality REIT, Inc.","count":2}]}]}""",
        )
        assertEquals(3, facetten.size)
        assertEquals("Applied Materials, Inc.", facetten.first().first)
        assertEquals(listOf("Apple Inc."), FirmaPatente.eigeneNamen(facetten, FirmaName.schluessel(listOf("Apple"))))
        assertEquals(42, FirmaPatente.zahlLesen("""{"count":42,"patentFileWrapperDataBag":[]}"""))
        val felder = FirmaPatente.felderLesen(
            """{"count":3,"patentFileWrapperDataBag":[
            {"applicationMetaData":{"cpcClassificationBag":["H01L29/66325","H01L27/0623","G06F3/01"]}},
            {"applicationMetaData":{"cpcClassificationBag":["H01L 21/8249"]}},
            {"applicationMetaData":{"cpcClassificationBag":["G06F9/44","H04N5/225"]}}]}""",
        )!!
        assertEquals(3, felder.patente)
        assertEquals("G06F" to 2, felder.klassen[0])
        assertEquals("H01L" to 2, felder.klassen[1])
        val kern = FirmaPatente.kern(listOf(2024 to 100, 2025 to 108), 2025)!!
        assertEquals(8.0, kern.aenderung!!, 0.001)
        assertNull(FirmaPatente.kern(listOf(2024 to 100), 2025))
        assertEquals(10.0, FirmaPatente.jeMilliarde(100, 10e9)!!, 1e-9)
        assertNull(FirmaPatente.jeMilliarde(100, null))
        assertEquals(listOf("Exxon", "Mobil"), FirmaPatente.suchwoerter("Exxon Mobil Corporation"))
        val url = FirmaPatente.jahrAdresse(listOf("Apple Inc."), 2025)
        assertTrue(url.contains("rangeFilters=") && url.contains("2025-01-01"))
        assertNotNull(FirmaPatente.namenAdresse(listOf("Apple"), 2016, 2026))
    }

    @Test
    fun texteBeideSprachen() {
        assertEquals(FirmaFaktenTexte.schluessel(true), FirmaFaktenTexte.schluessel(false))
        for (k in FirmaFaktenTexte.schluessel(true)) {
            assertTrue(k, FirmaFaktenTexte.text(true, k).isNotBlank())
            assertTrue(k, FirmaFaktenTexte.text(false, k).isNotBlank())
        }
        assertEquals("Halbleiterbauelemente", FirmaFaktenTexte.cpcName(true, "H01L"))
        assertEquals("Semiconductor devices", FirmaFaktenTexte.cpcName(false, "H01L"))
        assertEquals("Elektrotechnik", FirmaFaktenTexte.cpcName(true, "H99Z"))
        assertEquals("in den USA", FirmaFaktenTexte.text(true, "emi.g.us"))
        assertEquals("in den USA und der EU", FirmaFaktenTexte.text(true, "emi.g.useu"))
        assertEquals("in the US and the EU", FirmaFaktenTexte.text(false, "emi.g.useu"))
        assertEquals("nur gemeldete Anlagen in der EU", FirmaFaktenTexte.text(true, "emi.unten", listOf("in der EU")))
    }
}
