package de.troetenrepublik.app.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 5.93: Boersenprospekte und Preisfestsetzungen aus dem Tagesindex der SEC (form.idx).
class Fassung593IpoTest {

    private val idx = """
        Description:           Daily Index of EDGAR Dissemination Feed by Form Type
        Last Data Received:    October 27, 2026

        Form Type   Company Name                                                  CIK         Date Filed  File Name
        ---------------------------------------------------------------------------------------------------------------------------------------------
        S-1         BEISPIEL HOLDINGS CORP.                                       1000001     20261027    edgar/data/1000001/0001000001-26-000030.txt
        S-1/A       MUSTER INC                                                    1000002     20261027    edgar/data/1000002/0001000002-26-000031.txt
        F-1         TECH AG                                                       1000003     20261027    edgar/data/1000003/0001000003-26-000032.txt
        F-1/A       TECH AG                                                       1000003     20261027    edgar/data/1000003/0001000003-26-000033.txt
        424B4       PREIS CORP                                                    1000004     20261027    edgar/data/1000004/0001000004-26-000034.txt
        424B3       ANDERE CORP                                                   1000005     20261027    edgar/data/1000005/0001000005-26-000035.txt
        4           NEUE PERSON                                                   1999999     20261027    edgar/data/1999999/0001999999-26-000036.txt
        8-K         BEISPIEL HOLDINGS CORP.                                       1000001     20261027    edgar/data/1000001/0001000001-26-000037.txt
        S-1MEF      KLEIN GMBH                                                    1000006     20261027    edgar/data/1000006/0001000006-26-000038.txt
        S-11        IMMOBILIEN TRUST                                              1000007     20261027    edgar/data/1000007/0001000007-26-000039.txt
        S-1         BEISPIEL HOLDINGS CORP.                                       1000001     20261027    edgar/data/1000001/0001000001-26-000030.txt
        S-1         SPAETER INC                                                   1000008     20261026    edgar/data/1000008/0001000008-26-000040.txt
    """.trimIndent()

    @Test
    fun nurDieFuenfFormtypen() {
        val e = SecIpo.lesen(idx)
        // Die doppelte S-1-Zeile fehlt, ebenso 424B3, 4, 8-K, S-1MEF und S-11
        assertEquals(6, e.size)
        assertEquals(setOf("S-1", "S-1/A", "F-1", "F-1/A", "424B4"), e.map { it.formular }.toSet())
        assertTrue(e.none { it.datei.endsWith("000035.txt") || it.datei.endsWith("000036.txt") || it.datei.endsWith("000037.txt") || it.datei.endsWith("000038.txt") || it.datei.endsWith("000039.txt") })
    }

    @Test
    fun artZuordnung() {
        val e = SecIpo.lesen(idx)
        assertEquals(SecIpo.Art.PROSPEKT, e.first { it.formular == "S-1" }.art)
        assertEquals(SecIpo.Art.AENDERUNG, e.first { it.formular == "S-1/A" }.art)
        assertEquals(SecIpo.Art.PROSPEKT, e.first { it.formular == "F-1" }.art)
        assertEquals(SecIpo.Art.AENDERUNG, e.first { it.formular == "F-1/A" }.art)
        assertEquals(SecIpo.Art.PREIS, e.first { it.formular == "424B4" }.art)
    }

    @Test
    fun nameTagUndLink() {
        val e = SecIpo.lesen(idx).first { it.formular == "S-1" && it.cik == "1000001" }
        assertEquals("BEISPIEL HOLDINGS CORP.", e.name)
        assertEquals("2026-10-27", e.tag)
        assertEquals("https://www.sec.gov/Archives/edgar/data/1000001/0001000001-26-000030.txt", e.link)
    }

    @Test
    fun doppelteZeileNurEinmal() {
        assertEquals(1, SecIpo.lesen(idx).count { it.datei == "edgar/data/1000001/0001000001-26-000030.txt" })
    }

    @Test
    fun neuesterTagZuerst() {
        val e = SecIpo.lesen(idx)
        assertEquals("2026-10-26", e.last().tag)
        assertTrue(e.zipWithNext().all { (a, b) -> a.tag >= b.tag })
    }

    @Test
    fun leereUndFehlerseiten() {
        assertTrue(SecIpo.lesen("").isEmpty())
        assertTrue(SecIpo.lesen("<html>Not Found</html>").isEmpty())
    }
}
