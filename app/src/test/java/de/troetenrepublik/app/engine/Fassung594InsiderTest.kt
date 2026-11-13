package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Kongress
import de.troetenrepublik.app.market.MarFormular
import de.troetenrepublik.app.market.SecInsider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 5.94: Korrekturen an Insider- und Kongressdaten aus der Fachpruefung.
class Fassung594InsiderTest {

    private fun zeile(code: String, stueck: Int, preis: Double, nach: Int, form: String) = """
        <nonDerivativeTransaction>
            <transactionDate><value>2026-09-15</value></transactionDate>
            <transactionCoding><transactionCode>$code</transactionCode></transactionCoding>
            <transactionAmounts><transactionShares><value>$stueck</value></transactionShares><transactionPricePerShare><value>$preis</value></transactionPricePerShare></transactionAmounts>
            <postTransactionAmounts><sharesOwnedFollowingTransaction><value>$nach</value></sharesOwnedFollowingTransaction></postTransactionAmounts>
            <ownershipNature><directOrIndirectOwnership><value>$form</value></directOrIndirectOwnership></ownershipNature>
        </nonDerivativeTransaction>"""

    private fun meldung(eigner: String, zeilen: String) = """<?xml version="1.0"?>
<ownershipDocument>
    <issuer><issuerCik>0000000001</issuerCik><issuerName>EXAMPLE CORP</issuerName><issuerTradingSymbol>EXM</issuerTradingSymbol></issuer>
    $eigner
    <nonDerivativeTable>$zeilen</nonDerivativeTable>
</ownershipDocument>"""

    private fun eigner(name: String, director: Int, officer: Int, titel: String) = """
    <reportingOwner>
        <reportingOwnerId><rptOwnerName>$name</rptOwnerName></reportingOwnerId>
        <reportingOwnerRelationship><isDirector>$director</isDirector><isOfficer>$officer</isOfficer><officerTitle>$titel</officerTitle></reportingOwnerRelationship>
    </reportingOwner>"""

    @Test fun bestandDanachNurAusDirektenZeilen() {
        val xml = meldung(eigner("Doe Jane", 0, 1, "Chief Executive Officer"),
            zeile("P", 1000, 10.0, 5000, "D") + zeile("P", 500, 10.0, 200000, "I"))
        val g = SecInsider.form4(xml, "0000000001-26-000001").single()
        assertEquals(1500.0, g.n, 1e-9)
        assertEquals(15000.0, g.wert, 1e-9)
        assertEquals(5000.0, g.nach!!, 1e-9)
        // Anteil am direkten Bestand: 1.000 von 5.000
        assertEquals(0.2, g.anteil!!, 1e-9)
        assertEquals(4000.0, g.vor!!, 1e-9)
    }

    @Test fun vizepraesidentenSindKeineSpitze() {
        assertEquals("F", SecInsider.rolle(director = false, officer = true, titel = "Executive Vice President"))
        assertEquals("F", SecInsider.rolle(director = false, officer = true, titel = "SVP, Controller"))
        assertEquals("A", SecInsider.rolle(director = true, officer = false, titel = "Vice Chairman"))
        assertEquals("S", SecInsider.rolle(director = false, officer = true, titel = "President"))
        assertEquals("S", SecInsider.rolle(director = false, officer = true, titel = "EVP and Chief Financial Officer"))
        assertEquals("S", SecInsider.rolle(director = true, officer = false, titel = "Chairman of the Board"))
    }

    @Test fun gemeinschaftsmeldungNimmtNameDerHoechstenRolle() {
        val xml = meldung(eigner("Example Fund LP", 0, 0, "") + eigner("Roe Richard", 1, 0, ""), zeile("S", 100, 20.0, 900, "D"))
        val g = SecInsider.form4(xml, "0000000001-26-000002").single()
        assertEquals("Roe Richard", g.p)
        assertEquals("A", g.r)
    }

    private fun geschaeft(wert: String, typ: String, art: Kongress.Art, von: Long, bis: Long?) =
        Kongress.Geschaeft("", wert, "EXM", typ, art, 1_000L, 2_000L, von, bis)

    @Test fun gekauftePutsZaehlenNichtAlsKauf() {
        val p = Kongress.posten(listOf(
            geschaeft("Example Corp - Put Options", "OP", Kongress.Art.KAUF, 15_001, 50_000),
            geschaeft("Example Corp - Call Options", "OP", Kongress.Art.KAUF, 1_001, 15_000),
        )).single()
        assertEquals(8_000.5, p.kaeufe, 1e-9)
    }

    @Test fun oeffentlichAbEinreichung() {
        val g = geschaeft("Example Corp", "ST", Kongress.Art.KAUF, 1_001, 15_000)
        assertEquals(2_000L, g.oeffentlich)
        assertEquals(5_000L, g.copy(eingereicht = 5_000L).oeffentlich)
    }

    @Test fun waehrungFuerLiechtensteinUndRumaenien() {
        assertEquals("CHF", MarFormular.waehrungZurIsin("LI0315487269"))
        assertEquals("RON", MarFormular.waehrungZurIsin("ROSNPPACNOR9"))
        assertTrue(MarFormular.waehrungZurIsin("BG1100003166") == "EUR")
    }
}
