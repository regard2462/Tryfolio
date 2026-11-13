package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Waage [0.77]: erwarteter Gewinnanstieg aus KGV und erwartetem KGV (Kennzahl GEWINNE_STEIGEN, auch bei hohem KGV
// heute) und schwere Warnzeichen aus den 8-K-Meldungen.
class Fassung077Ad4BullBearTest {

    private val jetzt = 1_800_000_000_000L

    private fun punkt(f: BullBear.Fakten, k: BullBear.Kennzahl) = BullBear.auswerten(f, emptyList(), jetzt).firstOrNull { it.kennzahl == k }

    @Test fun hohesKgvMitNiedrigemErwartetemKgvIstDafuer() {
        val p = punkt(BullBear.Fakten(kgv = 40.0, kgvErwartet = 25.0), BullBear.Kennzahl.GEWINNE_STEIGEN)!!
        assertEquals(BullBear.Seite.BULL, p.seite)
        assertEquals(1.0, p.gewicht, 0.0)
        assertEquals(60.0, p.werte[2], 1e-9)
    }

    @Test fun schwelleLiegtBeiFuenfundzwanzigProzentGewinn() {
        // KGV 40 auf 32: Gewinn +25 %; knapp darunter fehlt der Punkt
        assertTrue(punkt(BullBear.Fakten(kgv = 40.0, kgvErwartet = 32.0), BullBear.Kennzahl.GEWINNE_STEIGEN) != null)
        assertEquals(null, punkt(BullBear.Fakten(kgv = 40.0, kgvErwartet = 33.0), BullBear.Kennzahl.GEWINNE_STEIGEN))
        assertEquals(null, punkt(BullBear.Fakten(kgv = 0.0, kgvErwartet = 10.0), BullBear.Kennzahl.GEWINNE_STEIGEN))
        assertEquals(null, punkt(BullBear.Fakten(kgv = 40.0, kgvErwartet = 0.0), BullBear.Kennzahl.GEWINNE_STEIGEN))
    }

    @Test fun textNenntProzentOderVerdreifacht() {
        val de = De.bbKennzahl("GEWINNE_STEIGEN", listOf("40,0", "25,0", "+60 %"))
        assertEquals("Analysten erwarten +60 % Gewinn in zwölf Monaten (KGV 40,0 → erwartet 25,0)", de)
        assertTrue(De.bbKennzahl("GEWINNE_STEIGEN", listOf("80,0", "8,0", "")).contains("mehr als verdreifachten Gewinn"))
        assertEquals("Analysts expect +60% earnings in twelve months (P/E 40.0 → forward 25.0)", En.bbKennzahl("GEWINNE_STEIGEN", listOf("40.0", "25.0", "+60%")))
        assertTrue(En.bbKennzahl("GEWINNE_STEIGEN", listOf("80.0", "8.0", "")).contains("more than triple"))
        // Ein Satz je Aussage: die Zeile fuer niedrig bewertet traegt eine andere Zahl und bleibt eigen
        assertFalse(De.bbKennzahl("BEWERTUNG_NIEDRIG", listOf("9,0")).contains("Gewinn"))
    }

    @Test fun schwereWarnzeichenSindEinPunktDagegen() {
        val p = punkt(BullBear.Fakten(warnzeichen = 2), BullBear.Kennzahl.WARNZEICHEN)!!
        assertEquals(BullBear.Seite.BEAR, p.seite)
        assertEquals(2.0, p.gewicht, 0.0)
        assertEquals(null, punkt(BullBear.Fakten(warnzeichen = 0), BullBear.Kennzahl.WARNZEICHEN))
        assertEquals(null, punkt(BullBear.Fakten(), BullBear.Kennzahl.WARNZEICHEN))
        val mit = BullBear.waage(BullBear.auswerten(BullBear.Fakten(umsatzWachstum = 0.2, warnzeichen = 1), emptyList(), jetzt))!!
        val ohne = BullBear.waage(BullBear.auswerten(BullBear.Fakten(umsatzWachstum = 0.2), emptyList(), jetzt))!!
        assertTrue(mit.wert < ohne.wert)
        assertTrue(De.bbKennzahl("WARNZEICHEN", listOf("1")).startsWith("Ein schweres"))
    }
}
