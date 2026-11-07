package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Test

// Fassung 5.96: Anzeigeform der Derivate-Karte in allen acht Kombinationen aus geladen, mitDaten, erwartet.
class Fassung596DerivateTest {

    @Test
    fun laedtOhneErwartung() {
        assertEquals(DerivateAnzeige.Zeigen.NUR_ZEILE, DerivateAnzeige.zeigen(false, false, false))
    }

    @Test
    fun laedtMitErwartung() {
        assertEquals(DerivateAnzeige.Zeigen.PLATZHALTER, DerivateAnzeige.zeigen(false, false, true))
    }

    @Test
    fun datenBeimLadenOhneErwartung() {
        assertEquals(DerivateAnzeige.Zeigen.BALKEN, DerivateAnzeige.zeigen(false, true, false))
    }

    @Test
    fun datenBeimLadenMitErwartung() {
        assertEquals(DerivateAnzeige.Zeigen.BALKEN, DerivateAnzeige.zeigen(false, true, true))
    }

    @Test
    fun geladenOhneDatenOhneErwartung() {
        assertEquals(DerivateAnzeige.Zeigen.NUR_ZEILE, DerivateAnzeige.zeigen(true, false, false))
    }

    @Test
    fun geladenOhneDatenMitErwartung() {
        assertEquals(DerivateAnzeige.Zeigen.NUR_ZEILE, DerivateAnzeige.zeigen(true, false, true))
    }

    @Test
    fun geladenMitDatenOhneErwartung() {
        assertEquals(DerivateAnzeige.Zeigen.BALKEN, DerivateAnzeige.zeigen(true, true, false))
    }

    @Test
    fun geladenMitDatenMitErwartung() {
        assertEquals(DerivateAnzeige.Zeigen.BALKEN, DerivateAnzeige.zeigen(true, true, true))
    }
}
