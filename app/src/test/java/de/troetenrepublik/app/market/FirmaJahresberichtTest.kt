package de.troetenrepublik.app.market

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Lesen eines Jahresberichts im Strom und Auswerten: selbst formuliertes, stark verkuerztes HTML einer erfundenen Firma.
class FirmaJahresberichtTest {
    private fun lesen(html: String) = Jahresbericht.klartext(ByteArrayInputStream(html.toByteArray(Charsets.UTF_8)))

    private fun bericht(
        cams: String = """<div>Critical Audit Matters</div>
<div>The critical audit matters communicated below are matters arising from the current period audit that were communicated to the audit committee. The communication of critical audit matters does not alter in any way our opinion.</div>
<div>Revenue Recognition</div>
<div>Description of the Matter</div>
<div>The company recognizes revenue from long term contracts and this requires significant judgment about progress towards completion of each contract.</div>
<div>Inventory Valuation of Spare Parts</div>
<div>Description of the Matter</div>
<div>The company holds a large inventory of spare parts and estimates the net realizable value using judgments on demand and obsolescence.</div>""",
        zweifel: String = "",
        kontrollen: String = "Management concluded that internal control over financial reporting was effective. We did not identify any material weaknesses.",
    ) = """<html><head><title>x</title><style>.a{}</style></head><body>
<ix:header><ix:hidden>HIDDENTEXT 12345</ix:hidden></ix:header>
<div>Auditor Name <ix:nonNumeric name="dei:AuditorName" contextRef="c1">Muster &amp; Partner LLP</ix:nonNumeric></div>
<div>Auditor Firm ID <ix:nonNumeric name="dei:AuditorFirmId" contextRef="c1">9999</ix:nonNumeric></div>
<div>Auditor Location <ix:nonNumeric name="dei:AuditorLocation" contextRef="c1">Springfield, Ohio</ix:nonNumeric></div>
<div>Item 3.</div><div>Legal Proceedings</div><div>12</div><div>Item 4.</div>
<div>Item 3. Legal Proceedings</div>
<div>See Note 9.</div>
<div>Item 4. Mine Safety</div>
<div>Item 9A. Controls and Procedures</div>
<div>$kontrollen</div>
<div>Item 9B. Other Information</div>
<div>Report of Independent Registered Public Accounting Firm</div>
<div>Opinion on the Financial Statements</div>
<div>We have audited the accompanying balance sheets. $zweifel</div>
$cams
<div>/s/ Muster &amp; Partner LLP</div>
<div>We have served as the Company&#8217;s auditor since 2004.</div>
<div>Springfield, Ohio</div>
<div>Note 9 &#8211; Commitments and Contingencies</div>
<div>Legal Proceedings</div>
<div>Patent Dispute with Beta Corp</div>
<div>On March 3, 2019, Beta Corp filed a complaint against the Company in the United States District Court for the Eastern District of Texas alleging that certain products infringe patents. The Company denies the allegations and intends to defend itself vigorously in this matter. The trial date has not been set by the court.</div>
<div>Environmental Remediation Inquiry</div>
<div>In 2021 the Company received a request for information from the Environmental Protection Agency regarding emissions at one of its plants. The Company is cooperating with the request and cannot predict the outcome of the inquiry at this time.</div>
<div>Operating Leases</div>
<div>The Company leases office space under operating leases that expire at various dates through 2030 and which contain renewal options as described above.</div>
<div>Note 10 &#8211; Segments</div>
<div>Item 15. Exhibits</div>
</body></html>"""

    @Test fun klartextOhneVerstecktesUndMitEntitaeten() {
        val t = lesen(bericht())
        assertFalse(t.contains("HIDDENTEXT"))
        assertFalse(t.contains("<div"))
        assertTrue(t.contains("Company's auditor since 2004"))
        assertTrue(t.contains("Note 9 - Commitments and Contingencies"))
    }

    @Test fun pruefer() {
        val b = Jahresbericht.auswerten(lesen(bericht()), 2026)
        assertEquals("Muster & Partner LLP", b.pruefer)
        assertEquals("9999", b.pruefernr)
        assertEquals("Springfield, Ohio", b.ort)
        assertEquals(2004, b.seit)
        assertFalse(b.zweifel)
        assertFalse(b.schwaeche)
    }

    @Test fun criticalAuditMatters() {
        val b = Jahresbericht.auswerten(lesen(bericht()), 2026)
        assertEquals(listOf("Revenue Recognition", "Inventory Valuation of Spare Parts"), b.cams)
        val keine = bericht(cams = "<div>Critical Audit Matters</div><div>We determined that there are no critical audit matters.</div>")
        assertTrue(Jahresbericht.auswerten(lesen(keine), 2026).cams.isEmpty())
    }

    @Test fun zweifelAnDerFortfuehrung() {
        val ja = bericht(zweifel = "The Company has suffered recurring losses that raise substantial doubt about its ability to continue as a going concern.")
        assertTrue(Jahresbericht.auswerten(lesen(ja), 2026).zweifel)
        val nein = bericht(zweifel = "Management concluded there is no substantial doubt about the ability to continue as a going concern.")
        assertFalse(Jahresbericht.auswerten(lesen(nein), 2026).zweifel)
    }

    @Test fun wesentlicheSchwaeche() {
        val ja = bericht(kontrollen = "Management identified a material weakness in internal control over financial reporting related to revenue.")
        assertTrue(Jahresbericht.auswerten(lesen(ja), 2026).schwaeche)
        val nicht = bericht(kontrollen = "Internal control over financial reporting was not effective as of year end.")
        assertTrue(Jahresbericht.auswerten(lesen(nicht), 2026).schwaeche)
        val behoben = bericht(kontrollen = "The material weakness identified last year was remediated during the year and controls were effective.")
        assertFalse(Jahresbericht.auswerten(lesen(behoben), 2026).schwaeche)
    }

    @Test fun verfahrenAusDemAnhang() {
        val v = Jahresbericht.auswerten(lesen(bericht()), 2026).verfahren
        assertEquals(listOf("Patent Dispute with Beta Corp", "Environmental Remediation Inquiry"), v.map { it.titel })
        assertEquals(2019, v[0].jahr)
        assertEquals("U.S. District Court, District of Texas", v[0].stelle.replace("Eastern ", ""))
        assertEquals(2021, v[1].jahr)
        assertEquals("Environmental Protection Agency", v[1].stelle)
    }

    @Test fun zuGrossWirdGemeldet() {
        try {
            Jahresbericht.klartext(ByteArrayInputStream("<div>x</div>".repeat(100).toByteArray()), 50)
            assertTrue("erwartet: ZuGross", false)
        } catch (_: Jahresbericht.ZuGross) {
        }
    }

    @Test fun utf8UeberChunkGrenze() {
        val u = Utf8Strom(ByteArrayInputStream("aä€z".toByteArray(Charsets.UTF_8)))
        assertEquals(listOf('a'.code, 0xE4, 0x20AC, 'z'.code, -1), List(5) { u.lies() })
    }
}
