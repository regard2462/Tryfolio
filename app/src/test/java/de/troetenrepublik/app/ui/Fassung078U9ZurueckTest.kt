package de.troetenrepublik.app.ui

import de.troetenrepublik.app.engine.Blattzustand
import de.troetenrepublik.app.engine.Tagesrueckblick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Zurueck fuehrt auf das Blatt, aus dem die Seite geoeffnet wurde.
class Fassung078U9ZurueckTest {
    @Test
    fun blattWartetUnterDerSeiteUndKommtSofortWieder() {
        val w = Wiederkehr(false)
        assertFalse(w.wartet)
        w.verlassen()
        assertTrue(w.wartet)
        assertTrue(w.seiteKommt)
        assertFalse(w.sofort)
        w.seiteLiegt()
        assertTrue(w.wartet)
        assertFalse(w.seiteKommt)
        w.zurueck()
        assertFalse(w.wartet)
        assertTrue(w.sofort)
    }

    @Test
    fun wiederhergestelltesBlattErwartetKeineSeite() {
        val w = Wiederkehr(true)
        assertTrue(w.wartet)
        assertFalse(w.seiteKommt)
        w.zurueck()
        assertFalse(w.wartet)
        assertTrue(w.sofort)
    }

    @Test
    fun geschlossenesBlattGleitetBeimNaechstenMalWiederHerein() {
        val w = Wiederkehr(false)
        w.verlassen()
        w.seiteLiegt()
        w.zurueck()
        w.vergessen()
        assertFalse(w.wartet)
        assertFalse(w.sofort)
        assertFalse(w.seiteKommt)
    }

    @Test
    fun zurueckOhneWartenAendertNichts() {
        val w = Wiederkehr(false)
        w.zurueck()
        assertFalse(w.sofort)
        assertFalse(w.wartet)
    }

    @Test
    fun zweitesVerlassenNachDemWiederkommen() {
        val w = Wiederkehr(false)
        w.verlassen(); w.seiteLiegt(); w.zurueck()
        w.verlassen()
        assertTrue(w.wartet)
        assertFalse(w.sofort)
        assertTrue(w.seiteKommt)
    }

    @Test
    fun beinHinUndZurueck() {
        val text = Blattzustand.beinText("CALL", -2, 187.5)
        assertEquals(Triple("CALL", -2, 187.5), Blattzustand.beinLesen(text))
        assertEquals(Triple("AKTIE", 100, 0.0), Blattzustand.beinLesen(Blattzustand.beinText("AKTIE", 100, 0.0)))
    }

    @Test
    fun unlesbaresBeinFaelltWeg() {
        assertNull(Blattzustand.beinLesen(""))
        assertNull(Blattzustand.beinLesen("CALL|x|100.0"))
        assertNull(Blattzustand.beinLesen("CALL|1"))
        assertNull(Blattzustand.beinLesen("CALL|1|NaN"))
        assertNull(Blattzustand.beinLesen("|1|100.0"))
    }

    @Test
    fun nachrichtHinUndZurueck() {
        val n = Tagesrueckblick.Nachricht("Titel | mit Strich", "Quelle", "https://example.org/a", 1_762_948_800_000L)
        assertEquals(n, Blattzustand.nachrichtLesen(Blattzustand.nachrichtFelder(n)))
        assertTrue(Blattzustand.nachrichtFelder(null).isEmpty())
        assertNull(Blattzustand.nachrichtLesen(emptyList()))
        assertNull(Blattzustand.nachrichtLesen(listOf("a", "b", "c", "keine Zahl")))
    }
}
