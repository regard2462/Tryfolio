package de.troetenrepublik.app.engine

import de.troetenrepublik.app.AktienfinderSpeicher
import de.troetenrepublik.app.FINDER_REGIONEN
import de.troetenrepublik.app.FinderLauf
import de.troetenrepublik.app.finderAktienIm
import de.troetenrepublik.app.market.Catalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 5.93: Regionen des Aktienfinders aus dem Katalog (Land und Region) und der Anfangszustand des Hintergrund-Auftrags.
class Fassung593FinderRegionenTest {

    private fun a(s: String, c: String, x: List<String> = emptyList()) = Catalog.Stock(s = s, n = "Aktie $s", c = c, x = x)

    @Test fun regionenFilter() {
        val d = Catalog.Data(aktien = listOf(
            a("US1", "US"), a("FR1", "FR"), a("DE1", "DE"), a("GB1", "GB"), a("CH1", "CH"), a("JP1", "JP"),
            a("CA1", "CA"), a("HK1", "HK"), a("CN1", "CN"), a("DAX1", "DE", listOf("DAX")),
        ))
        val sym = { code: String -> finderAktienIm(code, d).map { it.s } }

        // DAX1 ist ebenfalls eine deutsche Aktie und gehoert dazu
        assertEquals(listOf("DE1", "DAX1"), sym("DEUTSCHLAND"))
        assertEquals(listOf("GB1"), sym("GROSSBRITANNIEN"))
        assertEquals(listOf("CH1"), sym("SCHWEIZ"))
        assertEquals(listOf("JP1"), sym("JAPAN"))
        assertEquals(listOf("CA1"), sym("KANADA"))
        // Asien enthaelt Japan und Hongkong, nicht China (Schwellenland) und nicht die USA
        val asien = sym("ASIEN")
        assertTrue(asien.containsAll(listOf("JP1", "HK1")))
        assertFalse(asien.contains("CN1"))
        assertFalse(asien.contains("US1"))
        val schwellen = sym("SCHWELLEN")
        assertTrue(schwellen.contains("CN1"))
        assertFalse(schwellen.contains("JP1"))
        val europa = sym("EUROPA")
        assertTrue(europa.containsAll(listOf("DE1", "GB1", "CH1", "FR1")))
        assertFalse(europa.contains("US1"))
        assertEquals(listOf("DAX1"), sym("DAX"))
    }

    @Test fun keineDoppeltenSymbole() {
        val d = Catalog.Data(aktien = listOf(a("DE1", "DE"), a("DE1", "DE"), a("GB1", "GB")))
        assertEquals(listOf("DE1", "GB1"), finderAktienIm("ALLE", d).map { it.s })
    }

    @Test fun regionenListe() {
        for (code in listOf("DEUTSCHLAND", "GROSSBRITANNIEN", "SCHWEIZ", "JAPAN", "KANADA", "ASIEN", "SCHWELLEN")) {
            assertTrue("fehlender Code $code", code in FINDER_REGIONEN)
        }
        assertFalse("SP500", "SP500" in FINDER_REGIONEN)
    }

    @Test fun uebernehmenAusSpeicher() {
        AktienfinderSpeicher.universum = "ALLE"
        AktienfinderSpeicher.zeit = 42L
        FinderLauf.uebernehmen("ALLE")
        assertEquals("ALLE", FinderLauf.zustand.value.universum)
        assertEquals(42L, FinderLauf.zustand.value.stand)
        // Ein Markt ohne Treffer im Speicher aendert nichts, ein schon aktueller auch nicht
        FinderLauf.uebernehmen("JAPAN")
        assertEquals("ALLE", FinderLauf.zustand.value.universum)
        FinderLauf.uebernehmen("ALLE")
        assertEquals("ALLE", FinderLauf.zustand.value.universum)
        assertEquals(42L, FinderLauf.zustand.value.stand)
    }

    @Test fun anfangszustand() {
        assertNull(FinderLauf.zustand.value.laedt)
        assertNull(FinderLauf.zustand.value.details)
        assertFalse(FinderLauf.zustand.value.bilanzAktiv)
        assertFalse(FinderLauf.aktiv)
    }
}
