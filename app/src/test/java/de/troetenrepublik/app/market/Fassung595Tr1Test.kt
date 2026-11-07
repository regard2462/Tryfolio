package de.troetenrepublik.app.market

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

// Tests der Fassung 5.95 zu den TR-1-Stimmrechtsmeldungen: vier echte Formulare (Div-Gestaltung im
// template, Word-Gestaltung, NSM-Ablage), Schwellableitung mit UK- und Nicht-UK-Stufen, Wert- und
// Datumsleser, Titelerkennung und die Firmenseitenliste von Investegate.
class Fassung595Tr1Test {

    // Legal & General, Div-Gestaltung im template
    private val lg = """<html><body><h1 id="main-title">Holding(s) in Company</h1><template shadowrootmode="open"><p>TR-1: Standard form for notification of major holdings</p><div><p>1. Issuer Details</p></div><div><div><p>ISIN</p></div><div><p>GB0005603997</p></div><div><p>Issuer Name</p></div><div><p>LEGAL &amp; GENERAL GROUP PLC</p></div><div><p>UK or Non-UK Issuer</p></div><div><p>UK</p></div></div><div><p>2. Reason for Notification</p></div><div><p>An acquisition or disposal of voting rights; An acquisition or disposal of financial instruments</p></div><div><p>3. Details of person subject to the notification obligation</p></div><div><div><p>Name</p></div><div><p>BlackRock, Inc.</p></div><div><p>City of registered office (if applicable)</p></div><div><p>Wilmington</p></div><div><p>Country of registered office (if applicable)</p></div><div><p>USA</p></div></div><div><p>4. Details of the shareholder</p></div><div><div><p>Full name of shareholder(s) if different from the person(s) subject to the notification obligation, above</p></div><div><p>&#160;</p></div><div><p>City of registered office (if applicable)</p></div><div><p>&#160;</p></div><div><p>Country of registered office (if applicable)</p></div><div><p>&#160;</p></div></div><div><p>5. Date on which the threshold was crossed or reached</p></div><div><p>26-Aug-2026</p></div><div><p>6. Date on which Issuer notified</p></div><div><p>27-Aug-2026</p></div><div><p>7. Total positions of person(s) subject to the notification obligation</p></div><table><tbody><tr><td><p></p></td><td><p>% of voting rights attached to shares (total of 8.A)</p></td><td><p>% of voting rights through financial instruments (total of 8.B 1 + 8.B 2)</p></td><td><p>Total of both in % (8.A + 8.B)</p></td><td><p>Total number of voting rights held in issuer</p></td></tr><tr><td><p>Resulting situation on the date on which threshold was crossed or reached</p></td><td><p>8.080000</p></td><td><p>1.950000</p></td><td><p>10.030000</p></td><td><p>555161820</p></td></tr><tr><td><p>Position of previous notification (if applicable)</p></td><td><p>&#160; 5.050000</p></td><td><p>&#160; 0.030000</p></td><td><p>&#160; 5.070000</p></td><td><p><br /></p></td></tr></tbody></table><div><p></template></body></html>"""

    // ITV, Word-Gestaltung mit Ankreuzfeldern
    private val itv = """<html><body><p> TR-1: Standard form for notification of major holdings </p><p> &#160; </p><table><tbody><tr><td colspan="7"><p> NOTIFICATION OF MAJOR HOLDINGS (to be sent to the relevant issuer and to the FCA in Microsoft Word format if possible)i </p></td></tr><tr><td colspan="7"><p> &#160; </p></td></tr><tr><td colspan="3"><p> 1a. Identity of the issuer or the underlying issuer of existing shares to which voting rights are attachedii: </p></td><td colspan="4"><p> ITV PLC </p></td></tr><tr><td colspan="7"><p> 1b. Please indicate if the issuer is a non-UK issuer &#160;(please mark with an &#8220;X&#8221; if appropriate) </p></td></tr><tr><td colspan="6"><p> Non-UK issuer </p></td><td><p> &#160; </p></td></tr><tr><td colspan="7"><p> 2. Reason for the notification (please mark the appropriate box or boxes with an &#8220;X&#8221;) </p></td></tr><tr><td colspan="6"><p> An acquisition or disposal of voting rights </p></td><td><p> &#160; </p></td></tr><tr><td colspan="6"><p> An acquisition or disposal of financial instruments </p></td><td><p> &#160; </p></td></tr><tr><td colspan="6"><p> An event changing the breakdown of voting rights </p></td><td><p> X </p></td></tr><tr><td colspan="6"><p> Other (please specify)iii: </p></td><td><p> &#160; </p></td></tr><tr><td colspan="7"><p> 3. Details of person subject to the notification obligationiv </p></td></tr><tr><td colspan="3"><p> Name </p></td><td colspan="4"><p> Artemis Investment Management LLP </p></td></tr><tr><td colspan="3"><p> City and country of registered office (if applicable) </p></td><td colspan="4"><p> London, United Kingdom </p></td></tr><tr><td colspan="7"><p> 4. Full name of shareholder(s) (if different from 3.)v </p></td></tr><tr><td colspan="3"><p> Name </p></td><td colspan="4"><p> &#160; </p></td></tr><tr><td colspan="3"><p> City and country of registered office (if applicable) </p></td><td colspan="4"><p> &#160; </p></td></tr><tr><td colspan="3"><p> 5. Date on which the threshold was crossed or reachedvi: </p></td><td colspan="4"><p> 26/10/2026 </p></td></tr><tr><td colspan="3"><p> 6. Date on which issuer notified (DD/MM/YYYY): </p></td><td colspan="4"><p> 27/10/2026 </p></td></tr><tr><td colspan="7"><p> 7. Total positions of person(s) subject to the notification obligation </p></td></tr><tr><td><p> &#160; </p></td><td><p> % of voting rights attached to shares (total of 8. A) </p></td><td colspan="2"><p> % of voting rights through financial instruments <br />(total of 8.B 1 + 8.B 2) </p></td><td><p> Total of both in % (8.A + 8.B) </p></td><td colspan="2"><p> Total number of voting rights held in issuervii </p></td></tr><tr><td><p> Resulting situation on the date on which threshold was crossed or reached </p></td><td><p> 6.73668% </p></td><td colspan="2"><p> &#160; </p></td><td><p> 6.73668% </p></td><td colspan="2"><p> 247,828,577.00 </p></td></tr><tr><td><p> Position of previous notification (if </p><p> applicable) </p></td><td><p> 5.136482% </p></td><td colspan="2"><p> &#160; </p></td><td><p> 5.136482% </p></td><td colspan="2"><p> &#160; </p></td></tr><tr><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr></tbody></table><p> &#160; </p><p> &#160; </p><p> &#160; </p><p> &#160; </p><p> &#160; </p><table><tbody><tr><td colspan="5"><p> </body></html>"""

    // Mobico, NSM-Ablage mit Tabelle im Abschnitt 4
    private val mob = """<html><body><p>TR-1: Standard form for notification of major holdings</p><div><p>1. Issuer Details</p></div><div><div><p>ISIN</p></div><div><p>GB0006215205</p></div><div><p>Issuer Name</p></div><div><p>MOBICO GROUP PLC</p></div><div><p>UK or Non-UK Issuer</p></div><div><p>UK</p></div></div><div><p>2. Reason for Notification</p></div><div><p>An acquisition or disposal of financial instruments</p></div><div><p>3. Details of person subject to the notification obligation</p></div><div><div><p>Name</p></div><div><p>Barclays PLC</p></div><div><p>City of registered office (if applicable)</p></div><div><p>London</p></div><div><p>Country of registered office (if applicable)</p></div><div><p>United Kingdom</p></div></div><div><p>4. Details of the shareholder</p></div><div><table><tbody><tr><td><p>Name</p></td><td><p>City of registered office</p></td><td><p>Country of registered office</p></td></tr><tr><td><p>Barclays Capital Securities Limited</p></td><td><p>London</p></td><td><p>United Kingdom</p></td></tr></tbody></table></div><div><p>5. Date on which the threshold was crossed or reached</p></div><div><p>30-Oct-2026</p></div><div><p>6. Date on which Issuer notified</p></div><div><p>03-Nov-2026</p></div><div><p>7. Total positions of person(s) subject to the notification obligation</p></div><table><tbody><tr><td><p></p></td><td><p>% of voting rights attached to shares (total of 8.A)</p></td><td><p>% of voting rights through financial instruments (total of 8.B 1 + 8.B 2)</p></td><td><p>Total of both in % (8.A + 8.B)</p></td><td><p>Total number of voting rights held in issuer</p></td></tr><tr><td><p>Resulting situation on the date on which threshold was crossed or reached</p></td><td><p>2.120000</p></td><td><p>4.990000</p></td><td><p>7.110000</p></td><td><p>43674421</p></td></tr><tr><td><p>Position of previous notification (if applicable)</p></td><td><p>1.120000</p></td><td><p>5.030000</p></td><td><p>6.150000</p></td><td><p><br></p></td></tr></tbody></table><div><p></body></html>"""

    // SSP, Div-Gestaltung mit Kommentarzeile
    private val ssp = """<html><body><p>TR-1: Standard form for notification of major holdings </p><div><p>1. Issuer Details </p></div><div><p>ISIN </p></div><div><p>GB00BGBN7C04 </p></div><div><p>Issuer Name </p></div><div><p>SSP GROUP PLC </p></div><div><p>UK or Non-UK Issuer </p></div><div><p>UK </p></div><div><p>2. Reason for Notification </p></div><div><p>An acquisition or disposal of voting rights; Other </p></div><div><p>Comments </p></div><div><p>HSBC Bank plc's shareholding has fallen below 9% (HSBC Group's holding remains above 9%). </p></div><div><p>3. Details of person subject to the notification obligation </p></div><div><p>Name </p></div><div><p>HSBC Holdings plc </p></div><div><p>City of registered office (if applicable) </p></div><div><p>London </p></div><div><p>Country of registered office (if applicable) </p></div><div><p>United Kingdom </p></div><div><p>4. Details of the shareholder </p></div><div><table><tbody><tr><td><p>Name </p></td><td><p>City of registered office </p></td><td><p>Country of registered office </p></td></tr><tr><td><p>HSBC Bank plc </p></td><td><p>London </p></td><td><p>United Kingdom </p></td></tr><tr><td><p>HSBC Global Asset Management (UK) Limited </p></td><td><p>London </p></td><td><p>United Kingdom </p></td></tr></tbody></table></div><div><p>5. Date on which the threshold was crossed or reached </p></div><div><p>30-Oct-2026 </p></div><div><p>6. Date on which Issuer notified </p></div><div><p>03-Nov-2026 </p></div><div><p>7. Total positions of person(s) subject to the notification obligation </p></div><table><tbody><tr><td><p>&nbsp;</p></td><td><p>% of voting rights attached to shares (total of 8.A) </p></td><td><p>% of voting rights through financial instruments (total of 8.B 1 + 8.B 2) </p></td><td><p>Total of both in % (8.A + 8.B) </p></td><td><p>Total number of voting rights held in issuer </p></td></tr><tr><td><p>Resulting situation on the date on which threshold was crossed or reached </p></td><td><p>9.526000 </p></td><td><p>0.044000 </p></td><td><p>9.570000 </p></td><td><p>71585553 </p></td></tr><tr><td><p>Position of previous notification (if applicable) </p></td><td><p>9.674000 </p></td><td><p>0.044000 </p></td><td><p>9.718000 </p></td><td><p>&nbsp;</p></td></tr></tbody></table><div><p></body></html>"""

    // Mittag UTC eines Tages in ms
    private fun mittag(jahr: Int, monat: Int, tag: Int): Long =
        LocalDate.of(jahr, monat, tag).toEpochDay() * 86_400_000L + 43_200_000L

    // lg: Div-Gestaltung im template, Schwelle 10 steigend
    @Test
    fun lgFormular() {
        val m = Tr1.lesen(lg)!!
        assertEquals("BlackRock, Inc.", m.halter)
        assertEquals("Wilmington, USA", m.sitz)
        assertEquals(setOf(Tr1.Grund.STIMMRECHTE, Tr1.Grund.INSTRUMENTE), m.gruende)
        assertEquals(mittag(2026, 8, 26), m.stichtag)
        assertEquals(mittag(2026, 8, 27), m.gemeldet)
        assertEquals(8.08, m.nachher.aktien!!, 1e-9)
        assertEquals(1.95, m.nachher.instrumente!!, 1e-9)
        assertEquals(10.03, m.nachher.gesamt!!, 1e-9)
        assertEquals(5.05, m.vorher!!.aktien!!, 1e-9)
        assertEquals(0.03, m.vorher.instrumente!!, 1e-9)
        assertEquals(5.07, m.vorher.gesamt!!, 1e-9)
        assertEquals(555161820L, m.stimmrechte)
        assertFalse(m.nichtUk)
        assertEquals(Tr1.Schwelle(10, true), m.schwelle)
    }

    // itv: Word-Gestaltung mit Ankreuzfeldern, Schwelle 6 steigend
    @Test
    fun itvFormular() {
        val m = Tr1.lesen(itv)!!
        assertEquals("Artemis Investment Management LLP", m.halter)
        assertEquals("London, United Kingdom", m.sitz)
        assertEquals(setOf(Tr1.Grund.AUFTEILUNG), m.gruende)
        assertEquals(mittag(2026, 10, 26), m.stichtag)
        assertEquals(mittag(2026, 10, 27), m.gemeldet)
        assertEquals(6.73668, m.nachher.aktien!!, 1e-9)
        assertNull(m.nachher.instrumente)
        assertEquals(6.73668, m.nachher.gesamt!!, 1e-9)
        assertEquals(5.136482, m.vorher!!.aktien!!, 1e-9)
        assertNull(m.vorher.instrumente)
        assertEquals(5.136482, m.vorher.gesamt!!, 1e-9)
        assertEquals(247828577L, m.stimmrechte)
        assertFalse(m.nichtUk)
        assertEquals(Tr1.Schwelle(6, true), m.schwelle)
    }

    // mob: NSM-Ablage mit Tabelle im Abschnitt 4, Schwelle 7 steigend
    @Test
    fun mobFormular() {
        val m = Tr1.lesen(mob)!!
        assertEquals("Barclays PLC", m.halter)
        assertEquals("London, United Kingdom", m.sitz)
        assertEquals(setOf(Tr1.Grund.INSTRUMENTE), m.gruende)
        assertEquals(mittag(2026, 10, 30), m.stichtag)
        assertEquals(mittag(2026, 11, 3), m.gemeldet)
        assertEquals(2.12, m.nachher.aktien!!, 1e-9)
        assertEquals(4.99, m.nachher.instrumente!!, 1e-9)
        assertEquals(7.11, m.nachher.gesamt!!, 1e-9)
        assertEquals(1.12, m.vorher!!.aktien!!, 1e-9)
        assertEquals(5.03, m.vorher.instrumente!!, 1e-9)
        assertEquals(6.15, m.vorher.gesamt!!, 1e-9)
        assertEquals(43674421L, m.stimmrechte)
        assertFalse(m.nichtUk)
        assertEquals(Tr1.Schwelle(7, true), m.schwelle)
    }

    // ssp: Kommentar statt Schwelle, keine Stufe zwischen 9.526 und 9.718
    @Test
    fun sspFormular() {
        val m = Tr1.lesen(ssp)!!
        assertEquals("HSBC Holdings plc", m.halter)
        assertEquals(setOf(Tr1.Grund.STIMMRECHTE, Tr1.Grund.ANDERES), m.gruende)
        assertTrue(m.hinweis.startsWith("HSBC Bank plc's shareholding has fallen below 9%"))
        assertEquals(9.526, m.nachher.aktien!!, 1e-9)
        assertEquals(0.044, m.nachher.instrumente!!, 1e-9)
        assertEquals(9.57, m.nachher.gesamt!!, 1e-9)
        assertEquals(9.674, m.vorher!!.aktien!!, 1e-9)
        assertEquals(0.044, m.vorher.instrumente!!, 1e-9)
        assertEquals(9.718, m.vorher.gesamt!!, 1e-9)
        assertEquals(71585553L, m.stimmrechte)
        assertNull(m.schwelle)
    }

    // Schwellableitung: Erstmeldung, Unterschreiten, Nicht-UK-Stufen, unten-Angabe, keine Stufe
    @Test
    fun schwellenAbleitung() {
        assertEquals(Tr1.Schwelle(3, true), Tr1.schwelle(null, Tr1.Stand(null, null, 3.2), false))
        assertEquals(Tr1.Schwelle(3, false), Tr1.schwelle(Tr1.Stand(null, null, 3.4), Tr1.Stand(null, null, 2.9), false))
        assertEquals(Tr1.Schwelle(5, true), Tr1.schwelle(Tr1.Stand(null, null, 4.98), Tr1.Stand(null, null, 5.01), false))
        assertEquals(Tr1.Schwelle(10, true), Tr1.schwelle(Tr1.Stand(null, null, 9.0), Tr1.Stand(null, null, 11.2), true))
        assertEquals(Tr1.Schwelle(3, false), Tr1.schwelle(null, Tr1.Stand(null, null, null, 3.0), false))
        assertNull(Tr1.schwelle(Tr1.Stand(null, null, 5.2), Tr1.Stand(null, null, 5.4), false))
    }

    // Prozent-, unten-, Anzahl- und Datumsleser
    @Test
    fun werteLeser() {
        assertEquals(8.08, Tr1.prozent("8.080000")!!, 1e-9)
        assertEquals(6.73668, Tr1.prozent("6.73668%")!!, 1e-9)
        assertNull(Tr1.prozent("N/A"))
        assertNull(Tr1.prozent("Below 3%"))
        assertEquals(3.0, Tr1.unterWert("Below 3%")!!, 1e-9)
        assertEquals(247828577L, Tr1.anzahl("247,828,577.00"))
        assertEquals(mittag(2026, 8, 26), Tr1.tag("26-Aug-2026"))
        assertEquals(mittag(2026, 8, 27), Tr1.tag("27th August 2026"))
        assertEquals(mittag(2026, 10, 26), Tr1.tag("26/10/2026"))
        assertEquals(mittag(2026, 8, 26), Tr1.tag("August 26, 2026"))
        assertNull(Tr1.tag("31/02/2026"))
    }

    // Titel: nur Stimmrechtsmeldungen, nicht Eigengeschaefte oder Stimmrechtszahlen
    @Test
    fun titelErkennen() {
        assertTrue(Tr1.istStimmrechte("Holding(s) in Company"))
        assertTrue(Tr1.istStimmrechte("TR1 Notification of major interest in shares"))
        assertTrue(Tr1.istStimmrechte("Notification of Major Holdings"))
        assertFalse(Tr1.istStimmrechte("Total Voting Rights"))
        assertFalse(Tr1.istStimmrechte("Director/PDMR Shareholding"))
        assertFalse(Tr1.istStimmrechte("Transaction in Own Shares"))
    }

    // Firmenseite: alle Meldungen, Dealing-Filter und Stimmrechtsfilter
    @Test
    fun firmenseiteLesen() {
        val html = """
            <html><body><h1>ITV PLC News</h1><table><tbody>
            <tr>
                <td>03 Nov 2026</td>
                <td>03:30 PM</td>
                <td><div class="text-center"><a class="regulatory source-RNS" title="supplier: Regulatory News Service (Regulatory)" href="https://www.investegate.co.uk/source/RNS">RNS</a></div></td>
                <td><a class="announcement-link" href="https://www.investegate.co.uk/announcement/rns/itv/director-pdmr-shareholding/9856567">Director/PDMR Shareholding</a></td>
            </tr>
            <tr>
                <td>03 Nov 2026</td>
                <td>03:31 PM</td>
                <td><div class="text-center"><a class="regulatory source-RNS" title="supplier: Regulatory News Service (Regulatory)" href="https://www.investegate.co.uk/source/RNS">RNS</a></div></td>
                <td><a class="announcement-link" href="https://www.investegate.co.uk/announcement/rns/itv/holding-s-in-company/9856568">Holding(s) in Company</a></td>
            </tr>
            <tr>
                <td>03 Nov 2026</td>
                <td>03:32 PM</td>
                <td><div class="text-center"><a class="regulatory source-RNS" title="supplier: Regulatory News Service (Regulatory)" href="https://www.investegate.co.uk/source/RNS">RNS</a></div></td>
                <td><a class="announcement-link" href="https://www.investegate.co.uk/announcement/rns/itv/total-voting-rights/9856569">Total Voting Rights</a></td>
            </tr>
            </tbody></table></body></html>
        """.trimIndent()
        val alle = Investegate.firmaAlleLesen(html, "ITV")
        assertEquals(3, alle.size)
        val dealing = Investegate.firmaLesen(html, "ITV")
        assertEquals(1, dealing.size)
        assertEquals("Director/PDMR Shareholding", dealing[0].titel)
        assertEquals(listOf("Holding(s) in Company"), alle.filter { Tr1.istStimmrechte(it.titel) }.map { it.titel })
    }

    // Abgewandelte Formulare: Ankreuztabelle ohne X, Abschnitt 7 ohne Tabelle mit N/A, vorher unter 3 %, Nicht-UK
    @Test
    fun abgewandelteFormulare() {
        val ohneKreuz = """<html><body><table><tbody>
<tr><td>2. Reason for the notification</td></tr>
<tr><td>An acquisition or disposal of voting rights</td><td>&#160;</td></tr>
<tr><td>An acquisition or disposal of financial instruments</td><td>&#160;</td></tr>
<tr><td>3. Details of person subject to the notification obligation</td></tr>
<tr><td>Name</td><td>Example Capital LLP</td></tr>
</tbody></table></body></html>"""
        val a = Tr1.lesen(ohneKreuz)!!
        assertEquals("Example Capital LLP", a.halter)
        assertTrue(a.gruende.isEmpty())

        val ohneTabelle = """<html><body>
<div><p>1. Issuer Details</p></div><div><p>UK or Non-UK Issuer</p></div><div><p>Non-UK</p></div>
<div><p>3. Details of person subject to the notification obligation</p></div><div><p>Name</p></div><div><p>Example Fund SA</p></div>
<div><p>5. Date on which the threshold was crossed or reached</p></div><div><p>June 5th, 2026</p></div>
<div><p>7. Total positions of person(s) subject to the notification obligation</p></div>
<div><p>Resulting situation on the date on which threshold was crossed or reached</p></div>
<div><p>5.200000</p></div><div><p>N/A</p></div><div><p>5.200000</p></div>
<div><p>8. Notified details of the resulting situation</p></div>
</body></html>"""
        val b = Tr1.lesen(ohneTabelle)!!
        assertEquals("Example Fund SA", b.halter)
        assertTrue(b.nichtUk)
        assertEquals(mittag(2026, 6, 5), b.stichtag)
        assertEquals(5.2, b.nachher.aktien!!, 1e-9)
        assertNull(b.nachher.instrumente)
        assertEquals(5.2, b.nachher.gesamt!!, 1e-9)
        assertNull(b.stimmrechte)
        assertNull(b.vorher)
        assertEquals(Tr1.Schwelle(5, true), b.schwelle)

        val vorherUnter = """<html><body><table><tbody>
<tr><td>7. Total positions of person(s) subject to the notification obligation</td></tr>
<tr><td></td><td>% of voting rights attached to shares</td><td>% of voting rights through financial instruments</td><td>Total of both in %</td><td>Total number of voting rights held in issuer</td></tr>
<tr><td>Resulting situation on the date on which threshold was crossed or reached</td><td>3.100000</td><td></td><td>3.100000</td><td>1,000,000</td></tr>
<tr><td>Position of previous notification (if applicable)</td><td>Below 3%</td><td></td><td>Below 3%</td><td></td></tr>
</tbody></table></body></html>"""
        val c = Tr1.lesen(vorherUnter)!!
        assertEquals(3.0, c.vorher!!.unter!!, 1e-9)
        assertNull(c.vorher!!.gesamt)
        assertEquals(1_000_000L, c.stimmrechte)
        assertEquals(Tr1.Schwelle(3, true), c.schwelle)
    }
}
