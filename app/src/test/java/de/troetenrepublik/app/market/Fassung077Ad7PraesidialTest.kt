package de.troetenrepublik.app.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Praesidialdokumente aus dem Federal Register: Lesen der Antwort (gekuerzter Ausschnitt) und Zuordnung zu Werten.
class Fassung077Ad7PraesidialTest {
    private val antwort = """{"description":"Documents of type Presidential Document","count":6,"total_pages":1,"results":[
{"title":"Anti-Communism Week, 2026","document_number":"2026-22787","publication_date":"2026-11-06","signing_date":"2026-11-03","subtype":"Proclamation","html_url":"https://www.federalregister.gov/documents/2026/11/06/2026-22787/anti-communism-week-2026","abstract":null},
{"title":"Delegation of Authority Under Section 8335 of the National Defense Authorization Act for Fiscal Year 2026","document_number":"2026-22386","publication_date":"2026-11-02","signing_date":"2026-10-28","subtype":"Memorandum","html_url":"https://www.federalregister.gov/documents/2026/11/02/2026-22386/delegation-of-authority-under-section-8335-of-the-national-defense-authorization-act-for-fiscal-year","abstract":null},
{"title":"Enhancing Program Integrity and Interagency Coordination in the Administration of the H-1B Nonimmigrant Visa Program","document_number":"2026-19555","publication_date":"2026-09-23","signing_date":"2026-09-18","subtype":"Executive Order","html_url":"https://www.federalregister.gov/documents/2026/09/23/2026-19555/enhancing-program-integrity-and-interagency-coordination-in-the-administration-of-the-h-1b","abstract":null},
{"title":"Further Adjusting the Tariff Regimes for Imports of Aluminum, Steel, and Copper Into the United States","document_number":"2026-11314","publication_date":"2026-06-04","signing_date":"2026-06-01","subtype":"Proclamation","html_url":"https://www.federalregister.gov/documents/2026/06/04/2026-11314/further-adjusting-the-tariff-regimes-for-imports-of-aluminum-steel-and-copper-into-the-united-states","abstract":null},
{"title":"Adjusting Imports of Semiconductors, Semiconductor Manufacturing Equipment, and Their Derivative Products Into the United States","document_number":"2026-01052","publication_date":"2026-01-20","signing_date":"2026-01-14","subtype":"Proclamation","html_url":"https://www.federalregister.gov/documents/2026/01/20/2026-01052/adjusting-imports-of-semiconductors-semiconductor-manufacturing-equipment-and-their-derivative","abstract":null},
{"title":"Continuation of the National Emergency With Respect to Iran","document_number":"2026-22685","publication_date":"2026-11-05","signing_date":"2026-11-03","subtype":"Notice","html_url":"https://www.federalregister.gov/documents/2026/11/05/2026-22685/continuation-of-the-national-emergency-with-respect-to-iran","abstract":null}
]}"""

    private val dokumente = Praesidialdokumente.lesen(antwort)
    private fun mit(teil: String) = dokumente.first { it.titel.contains(teil) }

    @Test fun lesenNimmtNurErlasseProklamationenUndMemoranden() {
        assertEquals(5, dokumente.size)
        assertTrue(dokumente.none { it.titel.startsWith("Continuation") })
    }

    @Test fun neuesteZuerstMitUnterzeichnungstag() {
        assertEquals(listOf("2026-11-03", "2026-10-28", "2026-09-18", "2026-06-01", "2026-01-14"), dokumente.map { it.tag })
        assertEquals(Praesidialdokumente.Art.PROKLAMATION, dokumente.first().art)
        assertEquals(Praesidialdokumente.Art.MEMORANDUM, dokumente[1].art)
        assertEquals(Praesidialdokumente.Art.ERLASS, dokumente[2].art)
        assertEquals("", dokumente.first().kurzfassung)
        assertTrue(dokumente.all { it.adresse.startsWith("https://www.federalregister.gov/documents/") })
    }

    @Test fun fremdeAdressenUndLeereTitelFallenWeg() {
        val text = """{"results":[{"title":"X","document_number":"1","publication_date":"2026-01-01","signing_date":null,"subtype":"Proclamation","html_url":"https://example.org/a","abstract":null},
{"title":" ","document_number":"2","publication_date":"2026-01-01","signing_date":null,"subtype":"Proclamation","html_url":"https://www.federalregister.gov/documents/b","abstract":null}]}"""
        assertTrue(Praesidialdokumente.lesen(text).isEmpty())
        assertTrue(Praesidialdokumente.lesen("").isEmpty())
    }

    @Test fun ohneSigniertenTagGiltDieVeroeffentlichung() {
        val text = """{"results":[{"title":"Test","document_number":"1","publication_date":"2026-01-05","signing_date":null,"subtype":"Executive Order","html_url":"https://www.federalregister.gov/documents/a","abstract":null}]}"""
        assertEquals("2026-01-05", Praesidialdokumente.lesen(text).single().tag)
    }

    @Test fun adresseMitFilternUndTag() {
        val a = Praesidialdokumente.adresse("2025-11-12")
        assertTrue(a.startsWith("https://www.federalregister.gov/api/v1/documents.json?"))
        assertTrue(a.contains("conditions[type][]=PRESDOCU"))
        assertTrue(a.contains("conditions[presidential_document_type][]=executive_order"))
        assertTrue(a.contains("conditions[publication_date][gte]=2025-11-12"))
    }

    @Test fun zollAufStahlAluminiumKupferNenntDreiErzeugnisse() {
        val t = Praesidialdokumente.themen(mit("Aluminum, Steel")).map { it.id }.toSet()
        assertEquals(setOf("STAHL", "ALUMINIUM", "KUPFER"), t)
    }

    @Test fun stahlwertBekommtDenBezugAndereBranchenNicht() {
        val d = mit("Aluminum, Steel")
        val stahl = Praesidialdokumente.bezug(d, "Nucor", "Steel", "BERGBAU")
        assertNotNull(stahl)
        assertEquals(listOf("STAHL"), stahl!!.themen.map { it.id })
        assertNull(Praesidialdokumente.bezug(d, "Newmont", "Gold", "BERGBAU"))
        assertNull(Praesidialdokumente.bezug(d, "Apple", "Consumer Electronics", "COMPUTER"))
    }

    // Die Kategorie Bergbau der App ist zu grob (Gold, Kupfer, Stahl): ohne Yahoo-Branche kein Bezug zu Stahl.
    @Test fun grobeKategorieOhneBrancheGibtKeinenStahlBezug() {
        assertNull(Praesidialdokumente.bezug(mit("Aluminum, Steel"), "Nucor", "", "BERGBAU"))
    }

    @Test fun halbleiterNachKatalogkategorieWennBrancheFehlt() {
        val d = mit("Semiconductors")
        assertEquals(listOf("HALBLEITER"), Praesidialdokumente.bezug(d, "Micron Technology", "", "HALBLEITER")!!.themen.map { it.id })
        assertNotNull(Praesidialdokumente.bezug(d, "Micron Technology", "Semiconductors", ""))
        assertNull(Praesidialdokumente.bezug(d, "Micron Technology", "", "SOFTWARE"))
    }

    // Einzelwort-Namen treffen gewoehnliche Woerter: „Visa“ im Titel zur Einreise hat nichts mit dem Zahlungsdienst zu tun.
    @Test fun einzelwortNameTrifftKeineGewoehnlichenWoerter() {
        val d = mit("H-1B")
        assertNull(Praesidialdokumente.bezug(d, "Visa", "Credit Services", "BANKEN"))
        assertTrue(Praesidialdokumente.firmen(d, listOf("Visa", "Next")).isEmpty())
    }

    @Test fun firmaMitMehrerenNamensteilenImTitel() {
        val d = Praesidialdokumente.Dokument("1", "2026-05-01", Praesidialdokumente.Art.MEMORANDUM, "Letter Concerning Micron Technology Investments", "https://www.federalregister.gov/documents/x")
        assertEquals(listOf("Micron Technology"), Praesidialdokumente.firmen(d, listOf("Micron Technology", "Intel", "Texas Instruments")))
        assertTrue(Praesidialdokumente.bezug(d, "Micron Technology", "Semiconductors", "HALBLEITER")!!.firma)
    }

    @Test fun ohneBezugBleibtDasDokumentNurInDerListe() {
        val d = mit("Anti-Communism")
        assertTrue(Praesidialdokumente.themen(d).isEmpty())
        assertTrue(Praesidialdokumente.fuerWert(dokumente, "2025-11-12", "Nucor", "Steel", "BERGBAU").none { it.first === d })
    }

    @Test fun fuerWertNurLetzteZwoelfMonate() {
        val alle = Praesidialdokumente.fuerWert(dokumente, "2025-11-12", "Nucor", "Steel", "BERGBAU")
        assertEquals(1, alle.size)
        assertTrue(Praesidialdokumente.fuerWert(dokumente, "2026-07-01", "Nucor", "Steel", "BERGBAU").isEmpty())
    }
}
