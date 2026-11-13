package de.troetenrepublik.app.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// [Globus Boersen 0.73] Selbstabruf: Die Seiten stehen hinter einer Pruefung und waren nicht automatisch abrufbar; die Auszuege
// bilden die uebliche Seitenstruktur nach und sind keine echten Antworten. Die Pruefung der echten Seiten steht aus.
class IndexSelbstTest {
    private val heute = LocalDate.of(2026, 11, 9)

    @Test(timeout = 20_000)
    fun zeileMitNameWertUndProzent() {
        val html = "<div class=\"idx\"><span>TUNINDEX</span> <b>18 764,43</b> <i>+0,23%</i></div>"
        val s = IndexSelbst.zeile(html, Regex("TUNINDEX"))
        assertNotNull(s)
        assertEquals(0.23, s!!.prozent, 1e-9)
        assertEquals(18764.43, s.wert!!, 1e-6)
    }

    @Test(timeout = 20_000)
    fun zeileMitNegativemProzentUndDatum() {
        val html = "<td>All Share Index (LASI)</td><td>12,345.67</td><td>-1.25 %</td><td>09.11.2026</td>"
        val s = IndexSelbst.zeile(html, Regex("LASI"))!!
        assertEquals(-1.25, s.prozent, 1e-9)
        assertEquals(12345.67, s.wert!!, 1e-6)
    }

    @Test(timeout = 20_000)
    fun zeileOhneProzentIstKeinWert() = assertNull(IndexSelbst.zeile("<p>LASI 12,345.67</p>", Regex("LASI")))

    @Test(timeout = 20_000)
    fun stooqVerlaufstabelle() {
        val html = """
            <table><tr><th>No.</th><th>Date</th><th>Open</th><th>High</th><th>Low</th><th>Close</th><th>Change</th><th>Volume</th></tr>
            <tr><td>2</td><td>6 Nov 2026</td><td>2.80</td><td>2.85</td><td>2.78</td><td>2,830,000.50</td><td>+1.0%</td><td>0</td></tr>
            <tr><td>1</td><td>9 Nov 2026</td><td>2.83</td><td>2.95</td><td>2.80</td><td>2,858,300.51</td><td>+1.0%</td><td>0</td></tr></table>
        """.trimIndent()
        val s = IndexSelbst.verlauf(html)!!
        assertEquals(2858300.51, s.wert!!, 1e-2)
        assertEquals(1.0, s.prozent, 1e-3)
        assertEquals(LocalDate.of(2026, 11, 9), s.datum)
    }

    @Test(timeout = 20_000)
    fun lesenSetztHerkunftUndAbrufdatum() {
        val q = IndexSelbst.fuer("TN")!!
        val w = IndexSelbst.lesen(q, "<span>TUNINDEX</span> 18 764,43 +0,23%", 42L, heute)!!
        assertEquals("selbst:ilboursa", w.quelle)
        assertEquals(heute, w.kursDatum)
        assertEquals(42L, w.abruf)
    }

    @Test(timeout = 20_000)
    fun unsinnigeProzenteWerdenAbgewiesen() = assertNull(IndexSelbst.lesen(IndexSelbst.fuer("TN")!!, "TUNINDEX 18 764,43 +87,23%", 1L, heute))

    @Test(timeout = 20_000)
    fun jedeQuelleHatAdresseUndLeser() {
        for (q in IndexSelbst.QUELLEN) {
            assertTrue(q.id, q.adresse.startsWith("https://"))
            assertTrue(q.id, q.stooq || q.namen != null)
        }
        assertEquals(IndexSelbst.QUELLEN.size, IndexSelbst.QUELLEN.map { it.id }.toSet().size)
    }
}
