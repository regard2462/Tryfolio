package de.troetenrepublik.app.ui

import de.troetenrepublik.app.engine.DepotStreuung
import de.troetenrepublik.app.engine.Kategorie
import de.troetenrepublik.app.engine.Kategorien
import de.troetenrepublik.app.engine.KategorienStand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Umschichten (Ziele verteilen), Kurznamen in Kennzahlzeilen, Regionen der Anteilsbalken.
class Fassung078U64Test {

    @Test fun zielVerteiltDenRestImVerhaeltnis() {
        val neu = Kategorien.verteilen(mapOf("a" to 50.0, "b" to 30.0, "c" to 20.0), "a", 70.0)
        assertEquals(70.0, neu.getValue("a"), 1e-9)
        assertEquals(18.0, neu.getValue("b"), 1e-9)
        assertEquals(12.0, neu.getValue("c"), 1e-9)
        assertEquals(100.0, neu.values.sum(), 1e-9)
    }

    @Test fun zielVerteiltGleichWennRestNull() {
        val neu = Kategorien.verteilen(mapOf("a" to 100.0, "b" to 0.0, "c" to 0.0), "a", 40.0)
        assertEquals(30.0, neu.getValue("b"), 1e-9)
        assertEquals(30.0, neu.getValue("c"), 1e-9)
    }

    @Test fun zielGrenzenUndEinzelnerEintrag() {
        assertEquals(100.0, Kategorien.verteilen(mapOf("a" to 10.0, "b" to 90.0), "a", 250.0).getValue("a"), 0.0)
        assertEquals(0.0, Kategorien.verteilen(mapOf("a" to 10.0, "b" to 90.0), "a", -5.0).getValue("a"), 0.0)
        assertEquals(60.0, Kategorien.verteilen(mapOf("a" to 10.0), "a", 60.0).getValue("a"), 0.0)
        assertEquals(0.0, Kategorien.verteilen(mapOf("a" to 10.0, "b" to 90.0), "a", Double.NaN).getValue("a"), 0.0)
    }

    @Test fun kategorienZielSetztMarkeUndSumme() {
        val s = KategorienStand(listOf(Kategorie("k1", "ETFs", 80.0), Kategorie("k2", "Aktien", 20.0)), vorgeschlagen = true)
        assertFalse(s.zieleGesetzt)
        val n = Kategorien.zielVerteilen(s, "k2", 35.0)
        assertTrue(n.zieleGesetzt)
        assertEquals(35.0, n.kategorien.first { it.id == "k2" }.ziel, 1e-9)
        assertEquals(65.0, n.kategorien.first { it.id == "k1" }.ziel, 1e-9)
        assertEquals(100.0, Kategorien.summeZiele(n), 1e-9)
        assertTrue(Kategorien.zielSetzen(s, "k1", 50.0).zieleGesetzt)
    }

    @Test fun kurznamenOhneZusaetze() {
        assertEquals("Vanguard FTSE All-World", kurzerFondsname("Vanguard FTSE All-World UCITS ETF USD Accumulation"))
        val k = namensKuerzungen("Vanguard FTSE All-World UCITS ETF USD Accumulation")
        assertEquals("Vanguard FTSE All-World UCITS ETF USD Accumulation", k.first())
        assertTrue("Vanguard FTSE All-World" in k)
    }

    @Test fun regionenDerBerichteGleichFarbeWieStreuung() {
        assertEquals(DepotStreuung.Region.NORDAMERIKA, AnteilsFarben.regionVon("Vereinigte Staaten"))
        assertEquals(DepotStreuung.Region.EUROPA, AnteilsFarben.regionVon("EMEA"))
        assertEquals(DepotStreuung.Region.ASIEN_PAZIFIK, AnteilsFarben.regionVon("Asien-Pazifik"))
        assertEquals(DepotStreuung.Region.EUROPA, AnteilsFarben.regionVon("", "DE"))
        assertEquals(DepotStreuung.Region.ASIEN_PAZIFIK, AnteilsFarben.regionVon("", "JP"))
        assertNull(AnteilsFarben.regionVon("Sonstige"))
    }
}
