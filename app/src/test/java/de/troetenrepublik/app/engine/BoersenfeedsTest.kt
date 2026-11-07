package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Leser der Boersenseiten mit gekuerzten echten Antworten (06.11.2026) und die Zuordnung ueber den Namen.
class BoersenfeedsTest {
    @Test
    fun colomboNurStammaktien() {
        val t = """{"reqTradeSummery":[{"id":204,"name":"ABANS ELECTRICALS PLC","symbol":"ABAN.N0000","price":1011.25,"marketCap":5.1680538E9,"status":0},{"id":1845,"name":"ABANS FINANCE PLC","symbol":"AFSL.N0000","price":83.9,"marketCap":2.1E9},{"id":5,"name":"AITKEN SPENCE PLC","symbol":"AEL.X0000","price":10.0,"marketCap":1.0E9}]}"""
        val l = Boersenfeeds.colombo(t)
        assertEquals(2, l.size)
        assertEquals("ABANS ELECTRICALS PLC", l[0].name)
        assertEquals(5.1680538E9, l[0].wert, 1.0)
        assertEquals("LKR", l[0].waehrung)
        assertEquals("LK", l[0].land)
        assertTrue(Boersenfeeds.colombo("kaputt").isEmpty())
    }

    @Test
    fun karatschiTabelleOhneBezugsrechte() {
        val h = """<table class="tbl" id="screenerTable"><thead><tr><th>SYMBOL</th></tr></thead><tbody class="tbl__body"><tr><td data-order="786"><a class="tbl__symbol" href="/company/786" data-title="786 Investments Limited"><strong>786</strong></a></td><td>0813</td><td>ALLSHR</td><td class="right" data-order="398890729.98" data-filter="398890729.98">398.9M</td><td class="right" data-order="19.98" data-filter="19.98">19.98</td></tr><tr><td data-order="786R"><a class="tbl__symbol" href="/company/786R" data-title="786 Investment (Right)"><strong>786R</strong></a></td><td>0813</td><td></td><td class="right" data-order="12327154.97" data-filter="12327154.97">12.3M</td><td class="right" data-order="2.47">2.47</td></tr><tr><td data-order="AABS"><a class="tbl__symbol" href="/company/AABS" data-title="Al-Abbas Sugar Mills Limited"><strong>AABS</strong></a></td><td>0826</td><td>ALLSHR</td><td class="right" data-order="21000000000" data-filter="x">21B</td><td class="right" data-order="100">100</td></tr>"""
        val l = Boersenfeeds.karatschi(h)
        assertEquals(listOf("786 Investments Limited", "Al-Abbas Sugar Mills Limited"), l.map { it.name })
        assertEquals(398890729.98, l[0].wert, 0.01)
        assertEquals("PKR", l[0].waehrung)
    }

    @Test
    fun casablancaAusDemQuelltext() {
        val h = """<html><script type="application/json" data-drupal-selector="drupal-settings-json">{"boursenova":{"capitalisation":{"palmares":[{"label":{"fr":"MANAGEM","en":"MANAGEM"},"sector":{"fr":"Mines","en":"Mining"},"cap":176665025640,"variation":3.4},{"label":{"fr":"ATTIJARIWAFA BANK","en":"ATTIJARIWAFA BANK"},"sector":{"fr":"Banques","en":"Banks                                             "},"cap":149436826769.4,"variation":3.67}]}}}</script></html>"""
        val l = Boersenfeeds.casablanca(h)
        assertEquals(2, l.size)
        assertEquals("MANAGEM", l[0].name)
        assertEquals(176665025640.0, l[0].wert, 1.0)
        assertEquals("BERGBAU", l[0].branche)
        assertEquals("BANKEN", l[1].branche)
        assertEquals("MAD", l[1].waehrung)
        assertTrue(Boersenfeeds.casablanca("<html></html>").isEmpty())
    }

    @Test
    fun brvmZeilenMitLandAusDemSymbol() {
        val h = """<tbody>
 <tr><td>ABJC</td><td>SERVAIR ABIDJAN  COTE D'IVOIRE</td><td class="text-right">10 912 000</td><td class="text-right">3 400</td><td class="text-right">7 409 180 100</td><td class="text-right">37 100 800 000</td><td class="text-right">0,18</td> </tr>
 <tr><td>BICB</td><td>BANQUE INTERNATIONALE POUR L’INDUSTRIE ET LE COMMERCE DU BENIN</td><td class="text-right">57 759 800</td><td class="text-right">8 100</td><td class="text-right">0</td><td class="text-right">467 854 380 000</td><td class="text-right">2,29</td> </tr>
 <tr><td>SNTS</td><td>SONATEL SENEGAL</td><td class="text-right">100 000 000</td><td class="text-right">25 000</td><td class="text-right">1</td><td class="text-right">2 500 000 000 000</td><td class="text-right">12,0</td> </tr>"""
        val l = Boersenfeeds.brvm(h)
        assertEquals(3, l.size)
        assertEquals(listOf("CI", "BJ", "SN"), l.map { it.land })
        assertEquals(37100800000.0, l[0].wert, 1.0)
        assertEquals("XOF", l[2].waehrung)
    }

    @Test
    fun ugandaNurAktien() {
        val t = """[{"type":"EQUITY","stock":"AIRTEL UGANDA","price":188,"market_cap":7520000000000,"volume":14000,"deals":5},{"type":"BOND","stock":"X","price":1,"market_cap":5},{"type":"EQUITY","stock":"BATU","price":11500,"market_cap":564420000000,"volume":0,"deals":0}]"""
        val l = Boersenfeeds.uganda(t)
        assertEquals(listOf("AIRTEL UGANDA", "BATU"), l.map { it.name })
        assertEquals(7.52E12, l[0].wert, 1.0)
        assertEquals("UGX", l[0].waehrung)
    }

    @Test
    fun dhakaUndChittagongFirmenseiten() {
        val dse = """<div><span>Market cap</span></div><div class="mt-1 text-[15px] font-medium tnum tracking-tight" style="color:var(--text-primary)">BDT 3,851.487 mn</div></div>"""
        assertEquals(3.851487E9, Boersenfeeds.dhaka(dse)!!, 1.0)
        assertNull(Boersenfeeds.dhaka("<html>ohne Wert</html>"))
        val cse = """<tr bgcolor="#F1F1F1"> <td height="25" style="padding-left:5px;">Market Capital in BDT (mn) </td> <td height="25">15370.573 </td> </tr>"""
        assertEquals(1.5370573E10, Boersenfeeds.chittagong(cse)!!, 10.0)
        assertNull(Boersenfeeds.chittagong("nichts"))
    }

    @Test
    fun namenZuordnungRobust() {
        assertTrue(Boersenfeeds.namenGleich("Commercial Bank of Ceylon", "COMMERCIAL BANK OF CEYLON PLC"))
        assertTrue(Boersenfeeds.namenGleich("Hatton National Bank", "HATTON NATIONAL BANK PLC"))
        assertTrue(Boersenfeeds.namenGleich("Dialog", "DIALOG AXIATA PLC"))
        assertTrue(Boersenfeeds.namenGleich("Allied Bank Limited", "Allied Bank Limited"))
        assertTrue(Boersenfeeds.namenGleich("Attijariwafa Bank", "ATTIJARIWAFA BANK"))
        assertFalse(Boersenfeeds.namenGleich("Commercial Bank of Ceylon", "Commercial Credit and Finance"))
        assertFalse(Boersenfeeds.namenGleich("Hatton National Bank", "Nations Trust Bank"))
        assertFalse(Boersenfeeds.namenGleich("Bank", "Bank of Africa"))
        assertEquals("Attijariwafa Bank", Boersenfeeds.schoen("ATTIJARIWAFA BANK"))
        assertEquals("Dialog", Boersenfeeds.schoen("Dialog"))
    }

    @Test
    fun speichernUndLadenGleich() {
        val l = listOf(Boersenfeeds.Notierung("A B", "AB", 12345.0, "LKR", "LK"), Boersenfeeds.Notierung("C", "C", 99.0, "MAD", "MA", "BANKEN"))
        val z = Boersenfeeds.laden(Boersenfeeds.speichern(l))
        assertEquals(l, z)
        assertNotNull(Boersenfeeds.quelleFuer("LK"))
        assertEquals("CI", Boersenfeeds.BRVM.laender.first())
        assertNull(Boersenfeeds.quelleFuer("DE"))
    }
}
