package de.troetenrepublik.app.market

import de.troetenrepublik.app.engine.Boersenlinks
import de.troetenrepublik.app.engine.MarktSchlagen
import de.troetenrepublik.app.i18n.MarktTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// 0.78: LuSE Sambia als direkte Quelle (selbst geschriebene Ausschnitte der Seitenstruktur), Aserbaidschan-Link,
// Fama/French in "Markt schlagen". Katar ist keine direkte Quelle.
class Fassung078U56Test {
    private fun kopf(zahl: String, punkte: String, datum: String = "10/11/2026") =
        "<div>Market Data as of $datum</div><table><tr><th>LASI Current Value</th><th>Change</th><th>% Change</th></tr>" +
            "<tr><td>LASI Current Value $zahl Change $punkte % Change 0</td></tr></table>"

    @Test(timeout = 20_000)
    fun luseRueckgangAusDemMarktbericht() {
        val html = "<p>The LuSE All Share Index (LASI) closed at 24,613.67, 0.05% drop from the previous session. Market capitalization stood at K1.</p>" +
            "<div>Market Data as of 10/11/2026</div>"
        val s = BoersenDirekt.luse(html)
        assertNotNull(s)
        assertEquals(-0.05, s!!.prozent, 1e-9)
        assertEquals(24613.67, s.wert!!, 1e-6)
        assertEquals(LocalDate.of(2026, 11, 10), s.datum)
    }

    @Test(timeout = 20_000)
    fun luseAnstiegUndAndereWoerterFuerRueckgang() {
        val rauf = BoersenDirekt.luse("<p>The LuSE All Share Index (LASI) closed at 24,700.10, 0.35% gain from the previous session.</p>")!!
        assertEquals(0.35, rauf.prozent, 1e-9)
        assertEquals(24700.10, rauf.wert!!, 1e-6)
        assertNull(rauf.datum)
        for (w in listOf("fall", "decline", "down")) {
            assertEquals(w, -1.2, BoersenDirekt.luse("<p>The LuSE All Share Index (LASI) closed at 24,000.00, 1.2% $w from the previous session.</p>")!!.prozent, 1e-9)
        }
    }

    @Test(timeout = 20_000)
    fun luseRueckfallAufDieKopfzeileMitPunkten() {
        val minus = BoersenDirekt.luse(kopf("24613.67", "-12.68"))!!
        assertEquals(24613.67, minus.wert!!, 1e-6)
        assertEquals((24613.67 / (24613.67 + 12.68) - 1) * 100, minus.prozent, 1e-9)
        assertEquals(LocalDate.of(2026, 11, 10), minus.datum)
        val plus = BoersenDirekt.luse(kopf("24613.67", "12.68"))!!
        assertTrue(plus.prozent > 0)
    }

    @Test(timeout = 20_000)
    fun luseOhneTrefferOderMitPruefseiteGibtKeinenWert() {
        assertNull(BoersenDirekt.luse("<html><head><title>Just a moment...</title></head><body>Checking your browser before accessing the site.</body></html>"))
        assertNull(BoersenDirekt.luse(""))
        assertNull(BoersenDirekt.luse("<p>The LuSE All Share Index (LASI) was not published today.</p>"))
    }

    @Test(timeout = 20_000)
    fun luseIstDirekteQuelleUndSelbstabrufBleibt() {
        val q = BoersenDirekt.QUELLEN.first { it.id == "luse" }
        assertEquals(listOf("ZM"), q.laender)
        assertEquals("https://luse.co.zm/trading/market-data/", q.url)
        assertEquals(500_000, q.max)
        assertEquals("luse", BoersenDirekt.laender()["ZM"]!!.id)
        assertNotNull(IndexSelbst.fuer("ZM"))
        assertNotNull(Boersenlinks.fuer("ZM"))
    }

    @Test(timeout = 20_000)
    fun katarIstKeineDirekteQuelle() {
        assertNull(BoersenDirekt.laender()["QA"])
        assertNotNull(Boersenlinks.fuer("QA"))
    }

    @Test(timeout = 20_000)
    fun aserbaidschanZeigtAufBfb() {
        val l = Boersenlinks.fuer("AZ")!!
        assertTrue(l.adresse.startsWith("https://www.bfb.az/"))
        assertFalse(l.selbst)
    }

    @Test(timeout = 20_000)
    fun famaFrenchMitDoiImBausteinFonds() {
        val q = MarktSchlagen.FAMA_FRENCH_2010
        assertEquals("https://doi.org/10.1111/j.1540-6261.2010.01598.x", q.url)
        assertEquals(2010, q.jahr)
        assertTrue(q in MarktSchlagen.quellen(MarktSchlagen.Baustein.FONDS))
        assertTrue(q in MarktSchlagen.QUELLEN && q in MarktSchlagen.AUSSAGEQUELLEN)
        for (d in listOf(true, false)) assertTrue(MarktTexte.text(d, "fonds.info").contains("2010"))
        assertEquals(MarktTexte.schluessel(true), MarktTexte.schluessel(false))
    }
}
