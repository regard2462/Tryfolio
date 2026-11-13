package de.troetenrepublik.app.market

import de.troetenrepublik.app.market.IndexStand.Wert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// [Globus Boersen 0.73] Altersprüfung und Zusammenfuehren der Quellen
class IndexStandTest {
    private val mo = LocalDate.of(2026, 11, 9) // Montag
    private fun w(land: String, quelle: String, tage: Long, p: Double = 1.0) = Wert(land, quelle, p, 100.0, mo.minusDays(tage), 0L)

    @Test(timeout = 20_000)
    fun handelstageZaehlenNurWerktage() {
        // Freitag bis Montag: ein Handelstag
        assertEquals(1, IndexStand.handelstageSeit(LocalDate.of(2026, 11, 6), mo))
        assertEquals(0, IndexStand.handelstageSeit(mo, mo))
        // Montag bis Montag eine Woche spaeter: fuenf
        assertEquals(5, IndexStand.handelstageSeit(mo, mo.plusDays(7)))
    }

    @Test(timeout = 20_000)
    fun vierHandelstageSindNochFrisch() {
        val freitag = LocalDate.of(2026, 11, 13)
        assertTrue(IndexStand.frisch(Wert("DE", "yahoo", 1.0, null, mo, 0L), freitag))
        assertFalse(IndexStand.frisch(Wert("DE", "yahoo", 1.0, null, mo.minusDays(1), 0L), freitag))
    }

    @Test(timeout = 20_000)
    fun georgienArtigeFrozeneReiheWirdGrau() {
        val alt = Wert("GE", "direkt:gse", 0.0, 292.92, LocalDate.of(2026, 7, 23), 0L)
        assertFalse(IndexStand.frisch(alt, mo))
        assertTrue(IndexStand.frischeProzente(mapOf("GE" to alt), mo).isEmpty())
        // der alte Stand bleibt fuer das Laenderblatt erhalten
        assertEquals(alt, IndexStand.zusammenfuehren(mapOf("GE" to mapOf(alt.quelle to alt)), mo)["GE"])
    }

    @Test(timeout = 20_000)
    fun yahooVorDirektVorSelbst() {
        val m = mapOf("yahoo" to w("X", "yahoo", 0), "direkt:a" to w("X", "direkt:a", 0, 2.0), "selbst:b" to w("X", "selbst:b", 0, 3.0))
        assertEquals("yahoo", IndexStand.beste(m.values, mo)!!.quelle)
        assertEquals("direkt:a", IndexStand.beste(m.values.filter { it.quelle != "yahoo" }, mo)!!.quelle)
    }

    @Test(timeout = 20_000)
    fun beiZweiDirektquellenGiltDieErsteEintragung() {
        // bgdx steht vor wien-rs, auch wenn die Wiener Boerse juenger abgerufen wurde
        val m = listOf(w("RS", "direkt:wien-rs", 0, 0.5).copy(abruf = 99L), w("RS", "direkt:bgdx", 0, -0.07))
        assertEquals("direkt:bgdx", IndexStand.beste(m, mo)!!.quelle)
    }

    @Test(timeout = 20_000)
    fun frischerDirektwertSchlaegtAltenYahooWert() {
        val m = listOf(w("X", "yahoo", 10, 5.0), w("X", "direkt:a", 0, 2.0))
        assertEquals(2.0, IndexStand.beste(m, mo)!!.prozent, 1e-9)
    }

    @Test(timeout = 20_000)
    fun echterIndexSchlaegtLaenderEtf() {
        val m = listOf(w("TN", "yahoo-etf", 0, 5.0), w("TN", "selbst:ilboursa", 0, 2.0))
        assertEquals("selbst:ilboursa", IndexStand.beste(m, mo)!!.quelle)
        // ist der Selbstabruf alt, gilt der ETF
        assertEquals("yahoo-etf", IndexStand.beste(listOf(w("TN", "yahoo-etf", 0), w("TN", "selbst:ilboursa", 9)), mo)!!.quelle)
    }

    @Test(timeout = 20_000)
    fun ohneFrischenWertGiltDerJuengste() {
        val m = listOf(w("X", "yahoo", 12), w("X", "direkt:a", 9))
        assertEquals("direkt:a", IndexStand.beste(m, mo)!!.quelle)
        assertNull(IndexStand.beste(emptyList(), mo))
    }

    @Test(timeout = 20_000)
    fun zwischenspeicherHin() {
        val alle = mapOf("DE" to mapOf("yahoo" to Wert("DE", "yahoo", -0.5, 26000.0, mo, 123L)), "LK" to mapOf("direkt:cse-lk" to Wert("LK", "direkt:cse-lk", 1.37, null, mo, 456L)))
        val zurueck = IndexStand.lesen(IndexStand.schreiben(alle))
        assertEquals(alle, zurueck)
        assertTrue(IndexStand.lesen("kaputt;;\n;;;;;").isEmpty())
    }

    @Test(timeout = 20_000)
    fun laenderOhneBoerseListe() {
        assertEquals(38, IndexStand.OHNE_BOERSE.size)
        assertTrue("LI" in IndexStand.OHNE_BOERSE)
        assertFalse("NG" in IndexStand.OHNE_BOERSE)
    }
}
