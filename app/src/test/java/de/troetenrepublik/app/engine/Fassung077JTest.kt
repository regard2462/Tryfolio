package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.S
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

// 0.77 (Paket J): Kongress und Hoch-/Abstufungen als Punkte der Waage, gleiche Richtung wie in der Signale-Zeile.
class Fassung077JTest {

    private val jetzt = 1_800_000_000_000L

    private fun kennzahlen(f: BullBear.Fakten) = BullBear.auswerten(f, emptyList(), jetzt).mapNotNull { it.kennzahl }

    @Test fun kongressKaufenIstDafuerVerkaufenDagegen() {
        val kauft = BullBear.auswerten(BullBear.Fakten(kongress = 5 to 1), emptyList(), jetzt).single()
        assertEquals(BullBear.Kennzahl.KONGRESS_KAUFT, kauft.kennzahl)
        assertEquals(BullBear.Seite.BULL, kauft.seite)
        assertEquals(listOf(5.0, 1.0), kauft.werte)
        val verkauft = BullBear.auswerten(BullBear.Fakten(kongress = 1 to 7), emptyList(), jetzt).single()
        assertEquals(BullBear.Kennzahl.KONGRESS_VERKAUFT, verkauft.kennzahl)
        assertEquals(BullBear.Seite.BEAR, verkauft.seite)
    }

    @Test fun stufungenFolgenDerGroesserenSeite() {
        assertEquals(listOf(BullBear.Kennzahl.STUFEN_HOCH), kennzahlen(BullBear.Fakten(stufen = 3 to 1)))
        assertEquals(listOf(BullBear.Kennzahl.STUFEN_AB), kennzahlen(BullBear.Fakten(stufen = 0 to 1)))
    }

    @Test fun gleichstandUndFehlendeDatenGebenKeinenPunkt() {
        assertTrue(kennzahlen(BullBear.Fakten(kongress = 2 to 2, stufen = 1 to 1)).isEmpty())
        assertTrue(kennzahlen(BullBear.Fakten()).isEmpty())
    }

    @Test fun insiderStehtSchonInDerWaageUndKommtNichtDoppelDazu() {
        val k = kennzahlen(BullBear.Fakten(insider = -80, kongress = 1 to 7, stufen = 0 to 1))
        assertEquals(1, k.count { it == BullBear.Kennzahl.INSIDER_VERKAUFEN })
        assertEquals(3, k.size)
    }

    @Test fun waageZaehltDieNeuenPunkteMit() {
        val ohne = BullBear.waage(BullBear.auswerten(BullBear.Fakten(umsatzWachstum = 0.2), emptyList(), jetzt))!!
        val mit = BullBear.waage(BullBear.auswerten(BullBear.Fakten(umsatzWachstum = 0.2, kongress = 1 to 7, stufen = 0 to 2), emptyList(), jetzt))!!
        assertTrue(mit.wert < ohne.wert)
    }

    @Test fun texteSindDaFuerDeutschUndEnglisch() {
        val w = listOf("1", "7")
        listOf("KONGRESS_KAUFT", "KONGRESS_VERKAUFT", "STUFEN_HOCH", "STUFEN_AB").forEach { k ->
            assertTrue(k, S.bbKennzahl(k, w).isNotBlank() && S.bbKennzahl(k, w) != k)
            assertNotNull(S.bbWarum(k))
            assertTrue(k, S.bbWarum(k).isNotBlank())
        }
        assertTrue(S.sgHochAbKurz(0, 1).startsWith("0 ") && S.sgHochAbKurz(0, 1).contains(" 1 "))
    }

    @Test fun trendZeileNenntRangUndAenderung() {
        assertTrue(S.sgTrendWert(12, "+35 %").contains("12") && S.sgTrendWert(12, "+35 %").contains("+35 %"))
        assertTrue(S.sgTrendWert(12, null).contains("12") && !S.sgTrendWert(12, null).contains("·"))
        assertTrue(S.sgTrends.isNotBlank())
    }
}
