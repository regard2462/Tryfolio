package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Tabelle der Grossaktionaere im Aufbau eines Proxy Statements mit einer Aktiengattung: Fussnotenzeichen hinter den Zahlen,
// Leerspalte, „*“ fuer weniger als 1 %, eigene Tabelle fuer die Fussnoten. Dazu die Hinweise zum Stimmrecht.
class StrukturEignerTabelleTest {

    private val zelle = "style=\"text-align: right\""

    private fun proxy(): String =
        "<p><b>Security Ownership of Certain Beneficial Owners&#160;and&#160;Management</b></p>" +
            "<p>The following table shows the beneficial ownership of the common stock by each person known to own more than 5% and by all current directors " +
            "and executive officers as a group.</p>" +
            "<table><tr><td><b>Name of Beneficial Owner</b></td><td $zelle><b>Shares&#160;of&#160;Common<br/>Stock&#160;Beneficially<br/>Owned<sup>(1)</sup></b></td>" +
            "<td>&#160;&#160;&#160;</td><td $zelle><b>Percent of<br/>Common&#160;Stock<br/>Outstanding</b></td></tr>" +
            "<tr><td><b>The&#160;Beispiel&#160;Group</b></td><td $zelle>1,415,826,462<sup>(2)</sup></td><td>&#160;</td><td $zelle>9.63%</td></tr>" +
            "<tr><td><b>Muster,&#160;Inc.</b></td><td $zelle>1,043,713,019<sup>(3)</sup></td><td>&#160;</td><td $zelle>7.10%</td></tr>" +
            "<tr><td>Kate&#160;Probe</td><td $zelle>175,408<sup>(4)</sup></td><td>&#160;</td><td $zelle>*</td></tr>" +
            "<tr><td>Tim&#160;Beispiel</td><td $zelle>3,280,295<sup>(5)</sup></td><td>&#160;</td><td $zelle>*</td></tr>" +
            "<tr><td>All current directors and executive officers as a group (12 persons)</td><td $zelle>9,079,765<sup>(6)</sup></td><td>&#160;</td><td $zelle>*</td></tr></table>" +
            "<table><tr><td>(1)</td><td>Represents shares held, and RSUs that will vest within 60&#160;days.</td></tr>" +
            "<tr><td>(2)</td><td>Based on a Schedule 13G/A filed with the SEC.</td></tr></table>" +
            "<table><tr><td>*</td><td>Represents less than 1% of the issued and outstanding shares.</td></tr></table>"

    @Test
    fun fussnotenHinterDenZahlen() {
        val e = IpoProspekt.eigner(proxy())
        assertNotNull(e)
        assertEquals(listOf("The Beispiel Group" to 9.63, "Muster, Inc." to 7.10), e!!.halter.map { it.name to it.anteil })
        assertTrue(e.halter.all { it.gesellschaft })
        assertFalse(e.stimmrechte)
        // Die Gruppe hat nur „*“ (unter 1 %): kein Wert
        assertNull(e.gruppe)
        val z = IpoProspekt.eignerSpalten(proxy())
        assertNotNull(z)
        assertEquals(listOf("The Beispiel Group", "Muster, Inc."), z!!.filter { !it.gruppe }.map { it.name })
        assertEquals(9.63, z.first().spalten.single().second, 1e-9)
    }

    @Test
    fun fussnoteAmProzentwertUndVollerWortlaut() {
        val html = "<p>Principal Stockholders. Beneficial ownership before the offering.</p>" +
            "<table><tr><td>Name of Beneficial Owner</td><td>Shares</td><td>Percent</td></tr>" +
            "<tr><td>Alpha Capital LP</td><td>4,000,000 (1)</td><td>40.0%(2)</td></tr>" +
            "<tr><td>Beta Holdings Ltd.</td><td>2,500,000</td><td>25.0 % (3)</td></tr>" +
            "<tr><td>Jane Doe</td><td>50,000</td><td>less than 1%</td></tr></table>"
        val e = IpoProspekt.eigner(html)!!
        assertEquals(listOf("Alpha Capital LP" to 40.0, "Beta Holdings Ltd." to 25.0), e.halter.map { it.name to it.anteil })
    }

    @Test
    fun fussnoteAlleinIstKeinWert() {
        // Eine Zelle nur mit „(1)“ zaehlt weiter nicht als Angabe der Zeile
        val html = "<p>beneficial ownership</p><table><tr><td>Name</td><td>Shares</td><td>Percent</td></tr>" +
            "<tr><td>Alpha Fund LP</td><td>(1)</td><td>40.0%</td></tr><tr><td>Beta Fund LP</td><td>2,000</td><td>10.0%</td></tr>" +
            "<tr><td>All directors as a group (2 persons)</td><td>3,000</td><td>*</td></tr></table>"
        val e = IpoProspekt.eigner(html)!!
        assertEquals(listOf("Alpha Fund LP" to 40.0, "Beta Fund LP" to 10.0), e.halter.map { it.name to it.anteil })
    }

    @Test
    fun stimmrechtDeckblattBoilerplateUndPlaene() {
        // Marktwert auf dem Deckblatt: „voting and non-voting stock“ ist keine Gattung ohne Stimme
        val deck = "The aggregate market value of the voting and non-voting stock held by non-affiliates of the Registrant was approximately \$3 trillion."
        assertFalse(Struktur.Flag.OHNE_STIMME in StrukturRegeln.hinweise(deck))
        assertFalse(Struktur.Flag.OHNE_STIMME in StrukturRegeln.hinweise("The aggregate market value of the voting and&#160;non-voting common equity held by non-affiliates was \$1 billion."))
        // Planbedingung: kein Stimmrecht aus nicht ausgegebenen Einheiten
        val plan = "A Non-Employee Director shall have no rights as a shareholder and no voting rights with respect to the Restricted Stock Units until such Shares have been issued."
        assertFalse(Struktur.Flag.OHNE_STIMME in StrukturRegeln.hinweise(plan))
        // Vorzugsaktien, zugelassen und nicht ausgegeben
        val vorzug = "The Company has 10,000,000 authorized shares of non-voting preferred stock, none of which are issued."
        assertFalse(Struktur.Flag.OHNE_STIMME in StrukturRegeln.hinweise(vorzug))
        // Normalfall: eine Stimme je Aktie, ohne Hinweis auf eine Gattung ohne Stimme
        val proxy = "Voting Each share of the common stock has one vote on each matter. $plan"
        assertEquals(setOf(Struktur.Flag.EINE_STIMME), StrukturRegeln.hinweise(proxy))
        // Klare Aussagen bleiben: Gattung ohne Stimme, auch neben dem Marktwertsatz
        val klar = "The Class C common stock is non-voting. $deck"
        assertEquals(setOf(Struktur.Flag.OHNE_STIMME), StrukturRegeln.hinweise(klar))
        assertTrue(Struktur.Flag.OHNE_STIMME in StrukturRegeln.hinweise("Holders of our non-voting preferred shares are listed in Frankfurt."))
    }
}
