package de.troetenrepublik.app.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Zuordnung über den Firmennamen und Lesen der SEC-Listen und der ECHO-Antworten (selbst formulierte Ausschnitte).
class FirmaVerfahrenTest {

    @Test fun parteiPasstNurBeiEindeutigemNamen() {
        assertTrue(Verfahren.passt("Goldman Sachs Group, Inc.", "Goldman Sachs & Co. LLC"))
        assertTrue(Verfahren.passt("Goldman Sachs Group, Inc.", "Goldman Sachs Asset Management, L.P."))
        assertTrue(Verfahren.passt("Visa Inc.", "Visa Inc."))
        assertTrue(Verfahren.passt("Visa Inc.", "Visa USA Inc."))
        assertFalse(Verfahren.passt("Visa Inc.", "Visalia Dairy LLC"))
        assertFalse(Verfahren.passt("Visa Inc.", "Visa Consulting Partners Mexico"))
        assertFalse(Verfahren.passt("Apple Inc.", "Apple Valley Foods"))
        assertFalse(Verfahren.passt("Target Corporation", "Target Corporation"))
        assertFalse(Verfahren.passt("Goldman Sachs Group, Inc.", "Sachs Goldman Trading"))
    }

    @Test fun anlageImUmweltregister() {
        assertTrue(Verfahren.anlagePasst("Exxon Mobil Corp", "EXXONMOBIL BAYTOWN REFINERY"))
        assertTrue(Verfahren.anlagePasst("Exxon Mobil Corp", "EXXON MOBIL PORT ALLEN LUBRICANTS PLANT"))
        assertFalse(Verfahren.anlagePasst("Exxon Mobil Corp", "7-ELEVEN 34648"))
        assertFalse(Verfahren.anlagePasst("Exxon Mobil Corp", "EXXONMOBILITY SERVICES"))
        assertFalse(Verfahren.anlagePasst("Apple Inc.", "APPLE STORE"))
        assertFalse(Verfahren.anlagePasst("Target Corp", "TARGET STORE T-0123"))
    }

    private val html = """
        <table><thead><tr><th>Date</th><th>Respondents</th></tr></thead><tbody>
        <tr><td headers="a" class="x"><time datetime="2024-09-25T19:43:26Z" class="datetime">Sept. 25, 2024</time></td><td headers="b"><div class='release-view__respondents'><a href='https://www.sec.gov/files/litigation/admin/2024/34-1.pdf'>The Goldman Sachs Group, Inc.</a></div><div>Release No. 34-1</div></td></tr>
        <tr><td><time datetime="2012-01-10T10:00:00Z" class="datetime">Jan. 10, 2012</time></td><td><div class='release-view__respondents'><a href='/enforcement-litigation/litigation-releases/lr-1'>Goldman Sachs &amp; Co. LLC and Beispiel Person</a></div></td></tr>
        <tr><td><time datetime="2023-05-01T10:00:00Z" class="datetime">May 1, 2023</time></td><td><div class='release-view__respondents'><a href='/enforcement-litigation/litigation-releases/lr-2'>Fabrice Beispiel and Goldman Sachs</a></div></td></tr>
        <tr><td><time datetime="2023-06-01T10:00:00Z" class="datetime">June 1, 2023</time></td><td><div class='release-view__respondents'><a href='/enforcement-litigation/litigation-releases/lr-3'>Sachs Dairy</a></div></td></tr>
        </tbody></table>"""

    @Test fun secListe() {
        val z = Verfahren.secZeilen(html)
        assertEquals(4, z.size)
        assertEquals("https://www.sec.gov/enforcement-litigation/litigation-releases/lr-1", z[1].adresse)
        assertEquals("Goldman Sachs & Co. LLC and Beispiel Person", z[1].name)
        val e = Verfahren.secEintraege(Verfahren.Art.SEC_VERFAHREN, "Goldman Sachs Group, Inc.", z, 2016)
        // Die Meldung von 2012 liegt vor dem Stichjahr, "Sachs Dairy" gehört nicht zur Firma
        assertEquals(listOf(2024, 2023), e.map { it.jahr })
        assertEquals("SEC", e[0].stelle)
        assertEquals("https://www.sec.gov/files/litigation/admin/2024/34-1.pdf", e[0].link)
    }

    @Test fun secSucheCodiertDenNamen() {
        assertEquals("https://www.sec.gov/enforcement-litigation/litigation-releases?populate=Goldman+Sachs", Verfahren.secSuche(Verfahren.SEC_URTEILE, "Goldman Sachs Group, Inc."))
    }

    private val kennung = """{"Results":{"Message":"Success","QueryRows":"904","QueryID":"46"}}"""
    private val fehler = """{"Results":{"Error":{"ErrorMessage":"Rows Returned would be 313712. Queryset Limit would be exceeded"}}}"""
    private val anlagen = """{"Results":{"Message":"Working","Facilities":[
        {"FacName":"EXXONMOBIL BAYTOWN REFINERY","FacCity":"BAYTOWN","FacState":"TX","FacTotalPenalties":"${'$'}2,500,000","FacPenaltyCount":"2","FacDateLastPenalty":"11/06/2024","DfrUrl":"https://echo.epa.gov/detailed-facility-report?fid = 1100"},
        {"FacName":"EXXON MOBIL BULK TERMINAL","FacCity":"SAN FRANCISCO","FacState":"CA","FacTotalPenalties":"${'$'}0","FacPenaltyCount":null,"FacDateLastPenalty":null,"DfrUrl":"x"},
        {"FacName":"7-ELEVEN 34648","FacCity":"FORT WORTH","FacState":"TX","FacTotalPenalties":"${'$'}9,000","FacPenaltyCount":"1","FacDateLastPenalty":"01/02/2024","DfrUrl":"y"},
        {"FacName":"EXXONMOBIL BAYTOWN REFINERY","FacCity":"BAYTOWN","FacState":"TX","FacTotalPenalties":"${'$'}2,500,000","FacPenaltyCount":"2","FacDateLastPenalty":"11/06/2024","DfrUrl":"z"}
        ]}}"""

    @Test fun echoAntworten() {
        assertEquals("46" to 904, Verfahren.echoKennung(kennung))
        assertNull(Verfahren.echoKennung(fehler))
        val a = Verfahren.echoAnlagen(anlagen)
        assertEquals(4, a.size)
        assertEquals(2_500_000.0, a[0].betrag, 0.0)
        assertEquals("https://echo.epa.gov/detailed-facility-report?fid=1100", a[0].adresse)
        val e = Verfahren.echoEintraege("Exxon Mobil Corp", a)
        // nur die Anlage mit Strafe und passendem Namen, die zweite Schreibweise derselben Anlage fällt weg
        assertEquals(1, e.size)
        assertEquals("EPA, TX", e[0].stelle)
        assertEquals(2024, e[0].jahr)
        assertEquals(2_500_000.0, e[0].betrag!!, 0.0)
    }

    @Test fun echoBegriffe() {
        assertEquals(listOf("Exxon Mobil", "ExxonMobil"), Verfahren.echoBegriffe("Exxon Mobil Corp"))
        assertEquals(listOf("Visa"), Verfahren.echoBegriffe("Visa Inc."))
    }

    @Test fun ergebnisSortiertNachBetragDannJahr() {
        val e = Verfahren.Ergebnis(
            listOf(
                Verfahren.Eintrag(Verfahren.Art.BERICHT, "Alt", "", 2019, null, ""),
                Verfahren.Eintrag(Verfahren.Art.UMWELT, "Klein", "EPA", 2021, 1_000.0, ""),
                Verfahren.Eintrag(Verfahren.Art.BERICHT, "Neu", "", 2024, null, ""),
                Verfahren.Eintrag(Verfahren.Art.UMWELT, "Gross", "EPA", 2020, 5_000_000.0, ""),
            ),
            null, null, null,
        )
        assertEquals(listOf("Gross", "Klein", "Neu", "Alt"), e.sortiert.map { it.titel })
        assertFalse(e.leer)
        assertTrue(Verfahren.Ergebnis(emptyList(), null, null, null).leer)
    }
}
