package de.troetenrepublik.app.engine

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Geteilte Tabelle „The Offering“, Gattungen mit einer Aktie, Market Standoff, gestaffelte Freigabe und das Lesen
// grosser Prospekte als Strom. Textauszuege selbst formuliert, Zahlen erfunden.
class Fassung091StaffelTest {

    private fun zeile(label: String, wert: String) = "<tr><td><p>$label</p></td><td>&#160;</td><td><p>$wert</p></td></tr>"

    private val kopf = "<p><b>The Offering</b></p>"
    private val umbruch = "</table><p>14</p><p><a href=\"#toc\">Table of Contents</a></p><table>"

    private val erste = zeile("Class A common stock offered by us", "80,000,000 shares") +
        zeile("Option to purchase additional shares", "up to 12,000,000 additional shares of Class A common stock") +
        zeile("Class A common stock to be outstanding after this offering", "700,000,000 shares")
    private val zweite = zeile("Class N common stock to be outstanding after this offering", "600,000,000 shares") +
        zeile("Class T common stock to be outstanding after this offering", "1 share") +
        zeile("Class F common stock to be outstanding after this offering", "1 share") +
        zeile("Class E common stock to be outstanding after this offering", "None") +
        zeile("Class A, Class N, Class T, Class E and Class F common stock to be outstanding after this offering", "1,300,000,002 shares")

    // ------------------------------------------------------------ Geteilte Tabelle

    @Test
    fun geteilteTabelleWirdZusammengelesen() {
        val html = "<html><body>$kopf<table>$erste$umbruch$zweite</table><p>Risk Factors</p></body></html>"
        val a = IpoProspekt.angebot(html)!!
        assertEquals(80_000_000L, a.angeboten)
        assertEquals(1_300_000_002L, a.danach)
        assertEquals(80_000_000.0 / 1_300_000_002.0 * 100.0, a.frei!!, 1e-9)
        assertEquals(12_000_000L, a.mehrzuteilung)
        assertEquals(listOf("A", "N", "T", "F", "E"), a.gattungen.map { it.name })
        assertEquals(listOf(700_000_000L, 600_000_000L, 1L, 1L, 0L), a.gattungen.map { it.aktien })
        // Dasselbe in einer Tabelle ergibt dasselbe
        val ganz = IpoProspekt.angebot("<html><body>$kopf<table>$erste$zweite</table></body></html>")!!
        assertEquals(a, ganz)
    }

    @Test
    fun ersteTabelleAlleinReichtNichtFuerDenAnteil() {
        val a = IpoProspekt.angebot("<html><body>$kopf<table>$erste</table></body></html>")!!
        assertEquals(80_000_000L, a.angeboten)
        assertNull(a.frei)
    }

    @Test
    fun folgetabelleNachNeuerUeberschriftZaehltNicht() {
        val fremd = "</table><p>Summary Consolidated Financial Data</p><p>The following table sets out our results for the periods shown.</p><table>" +
            zeile("Common stock to be outstanding after this offering", "5,000,000 shares")
        val a = IpoProspekt.angebot("<html><body>$kopf<table>$erste$fremd</table></body></html>")!!
        assertNull(a.danach)
    }

    @Test
    fun summenzeileMitMehrerenGattungenVorDenEinzelzeilen() {
        val summeZuerst = zeile("Class A, Class N and Class F common stock to be outstanding after this offering", "1,300,000,000 shares") +
            zeile("Class A common stock to be outstanding after this offering", "700,000,000 shares") +
            zeile("Class N common stock to be outstanding after this offering", "600,000,000 shares")
        val a = IpoProspekt.angebot("<html><body>$kopf<table>" + zeile("Class A common stock offered by us", "80,000,000 shares") + summeZuerst + "</table></body></html>")!!
        assertEquals(1_300_000_000L, a.danach)
    }

    // ------------------------------------------------------------ Sperrfrist

    @Test
    fun marketStandoffZaehltAlsSperrfrist() {
        val t = "<p>Our stockholders are subject to market standoff provisions for 180 days after the date of this prospectus.</p>"
        assertEquals(180, IpoProspekt.sperrfrist(t))
        assertTrue(IpoProspekt.sperrstufen(t).isEmpty())
    }

    @Test
    fun teilfreigabeIstKeineEinfacheSperrfrist() {
        // Grundfrist plus ein Termin mit „up to 20%“: nicht eindeutig, keine Zahl
        val t = "<p>Holders of substantially all of our stock are subject to lock-up and market standoff agreements for 180 days after the date of this prospectus. " +
            "In addition, subject to the lock-up agreements, on or after the date that is 195 days after the date of this prospectus, up to 20% of the shares held by each holder may be sold.</p>"
        assertNull(IpoProspekt.sperrfrist(t))
        assertTrue(IpoProspekt.sperrstufen(t).isEmpty())
        // Der Termin allein ist auch keine Sperrfrist
        val nur = "<p>Subject to the lock-up agreements, on or after the date that is 195 days after the date of this prospectus, up to 20% of the shares may be sold.</p>"
        assertNull(IpoProspekt.sperrfrist(nur))
    }

    @Test
    fun mehrereStufenMitAnteilWerdenAlsFreigabezeitpunkteGelesen() {
        val t = "<p>Subject to the lock-up agreements, on or after the date that is 90 days after the date of this prospectus, up to 25% of the shares held by each holder may be sold.</p>" +
            "<p>Subject to the lock-up agreements, on or after the date that is 120 days after the date of this prospectus, up to 25% of the shares held by each holder may be sold.</p>"
        assertNull(IpoProspekt.sperrfrist(t))
        assertEquals(listOf(IpoProspekt.Stufe(90, 25.0), IpoProspekt.Stufe(120, 25.0)), IpoProspekt.sperrstufen(t))
        val aus = IpoProspekt.lesen(t)
        assertEquals(2, aus.stufen.size)
        assertNull(aus.sperrfristTage)
    }

    @Test
    fun kursschwelleOhneAnteilWirdWeggelassen() {
        val t = "<p>Under the lock-up agreements, shares are released if the closing price of our common stock is at least 25% above the IPO price, but not before 150 days after the date of this prospectus.</p>"
        assertNull(IpoProspekt.sperrfrist(t))
        assertTrue(IpoProspekt.sperrstufen(t).isEmpty())
    }

    // ------------------------------------------------------------ Lesen als Strom

    private fun fuell(n: Int): String {
        val b = StringBuilder()
        var i = 0
        while (b.length < n) { b.append("<p>Filler paragraph number ").append(i++).append(" about nothing relevant, with umlauts äöü and a dash — here.</p>\n") }
        return b.toString()
    }

    private val eigner = "<p><b>PRINCIPAL SHAREHOLDERS</b></p><p>beneficial ownership before this offering</p>" +
        "<table><tr><td>Name of Beneficial Owner</td><td>Shares</td><td>Percent Before Offering</td><td>Percent After Offering</td></tr>" +
        "<tr><td>Entities affiliated with Alpha Capital(1)</td><td>4,000,000</td><td>40.0%</td><td>30.0%</td></tr>" +
        "<tr><td>Beta Holdings Ltd.&nbsp;(2)</td><td>2,500,000</td><td>25.0</td><td>18.8</td></tr></table>"

    private fun gross(): String = "<html><body>" + fuell(2_500_000) + "<p>Table of Contents</p>$kopf<table>$erste$umbruch$zweite</table>" + fuell(2_500_000) +
        "<p>Lock-up agreements. Our officers and directors agreed under market standoff agreements not to sell for 180 days after the date of this prospectus.</p>" +
        fuell(2_500_000) + eigner + fuell(2_000_000) + "</body></html>"

    @Test
    fun grosserProspektWirdVerdichtetUndGleichGelesen() {
        val html = gross()
        val kurz = IpoProspekt.verdichten(ByteArrayInputStream(html.toByteArray(Charsets.UTF_8)), block = 100_000, rueck = 64_000)
        assertTrue("verdichtet: ${kurz.length}", kurz.length < html.length / 4)
        val ganz = IpoProspekt.lesen(html)
        val aus = IpoProspekt.lesen(kurz)
        assertEquals(ganz.angebot, aus.angebot)
        assertEquals(1_300_000_002L, aus.angebot!!.danach)
        assertEquals(180, aus.sperrfristTage)
        assertEquals(ganz.eigner, aus.eigner)
        assertEquals(listOf("Alpha Capital", "Beta Holdings Ltd."), aus.eigner!!.halter.map { it.name })
    }

    @Test
    fun blockgrenzenUndMehrbyteZeichenStoerenNicht() {
        val html = gross()
        val bytes = html.toByteArray(Charsets.UTF_8)
        val a = IpoProspekt.verdichten(ByteArrayInputStream(bytes), block = 33_333, rueck = 64_000)
        val b = IpoProspekt.verdichten(ByteArrayInputStream(bytes), block = 400_000, rueck = 64_000)
        assertEquals(IpoProspekt.lesen(b), IpoProspekt.lesen(a))
        assertEquals(180, IpoProspekt.lesen(a).sperrfristTage)
        // Ein Block, der mitten in einem Zeichen endet, ergibt keine Ersatzzeichen im Behaltenen
        assertTrue(!a.contains('�'))
    }

    @Test
    fun ohneFundstellenBleibtNichtsUebrig() {
        assertEquals("", IpoProspekt.verdichten(ByteArrayInputStream(fuell(300_000).toByteArray()), block = 50_000))
        assertEquals("", IpoProspekt.verdichten(ByteArrayInputStream(ByteArray(0))))
    }
}
