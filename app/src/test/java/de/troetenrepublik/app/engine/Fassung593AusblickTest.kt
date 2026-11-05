package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import de.troetenrepublik.app.market.Markterwartung
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 5.93: Schwellen der Ausblickdeutung und die Deutungssaetze zu Polymarket-Themen und zu den Kennzahlen der
// Lager und Wirtschaftsdaten in beiden Sprachen.
class Fassung593AusblickTest {

    @Test
    fun zinsRichtung() {
        assertNull(Ausblickdeutung.zinsRichtung(null))
        assertEquals(-1, Ausblickdeutung.zinsRichtung(-2.0))
        assertEquals(-1, Ausblickdeutung.zinsRichtung(-0.75))
        assertEquals(0, Ausblickdeutung.zinsRichtung(-0.5))
        assertEquals(0, Ausblickdeutung.zinsRichtung(0.0))
        assertEquals(0, Ausblickdeutung.zinsRichtung(0.74))
        assertEquals(1, Ausblickdeutung.zinsRichtung(0.75))
    }

    @Test
    fun rezessionStufe() {
        assertNull(Ausblickdeutung.rezessionStufe(null))
        assertNull(Ausblickdeutung.rezessionStufe(Double.NaN))
        assertEquals(0, Ausblickdeutung.rezessionStufe(0.1))
        assertEquals(1, Ausblickdeutung.rezessionStufe(0.25))
        assertEquals(1, Ausblickdeutung.rezessionStufe(0.49))
        assertEquals(2, Ausblickdeutung.rezessionStufe(0.5))
    }

    @Test
    fun meFallsZuAllenThemen() {
        for (t in Markterwartung.THEMEN) {
            assertTrue(t.schluessel, De.meFalls(t.schluessel).isNotEmpty())
            assertTrue(t.schluessel, En.meFalls(t.schluessel).isNotEmpty())
        }
        assertEquals("", De.meFalls("xyz"))
        assertEquals("", En.meFalls("xyz"))
    }

    @Test
    fun fiDeutungZuAllenSchluesseln() {
        for (k in listOf(
            Fruehindikatoren.ROHOEL, Fruehindikatoren.CUSHING, Fruehindikatoren.BENZIN, Fruehindikatoren.DESTILLAT,
            Fruehindikatoren.SPR, Fruehindikatoren.GAS_US, Fruehindikatoren.GAS_EU, Fruehindikatoren.ERSTANTRAEGE,
            Fruehindikatoren.FLUGGAESTE, Fruehindikatoren.DUERRE,
        )) {
            assertTrue(k, De.fiDeutung(k).isNotEmpty())
            assertTrue(k, En.fiDeutung(k).isNotEmpty())
        }
        assertEquals("", De.fiDeutung("xyz"))
        assertEquals("", En.fiDeutung("xyz"))
    }

    @Test
    fun meKernSaetze() {
        assertEquals("", De.meKern(null, null))
        assertTrue(De.meKern(-1, 0).contains("senkungen", ignoreCase = true))
        assertTrue(De.meKern(-1, 0).contains("unwahrscheinlich"))
    }
}
