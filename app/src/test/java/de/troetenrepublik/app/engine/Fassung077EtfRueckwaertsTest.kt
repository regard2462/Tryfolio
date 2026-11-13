package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Paket 077ac3: Rueckwaertssuche "In welchen ETFs steckt die Aktie" aus etf-rueckwaerts.json.
class Fassung077EtfRueckwaertsTest {
    private fun asset(): File =
        listOf("src/main/assets/etf-rueckwaerts.json", "../app/src/main/assets/etf-rueckwaerts.json").map { File(it) }.first { it.exists() }

    private val probe = """{"stand":"2026-11-11","tiefe":10,"etfs":[["A.DE","Anbieter Eins"],["B.DE","Anbieter Zwei"],["C.DE","Anbieter Drei"]],
        |"aktien":{"AAA":[[0,5.5],[2,9.1],[1,7.0]],"BRK-B":[[1,3.2]],"LEER":[[7,1.0]]}}""".trimMargin()

    @Test fun sortiertNachGewichtGroessteZuerst() {
        val d = EtfRueckwaerts.lesen(probe)!!
        val t = EtfRueckwaerts.finden(d, "AAA")
        assertEquals(listOf("C.DE", "B.DE", "A.DE"), t.map { it.etf })
        assertEquals(9.1, t.first().gewicht, 1e-9)
        assertEquals("Anbieter Drei", t.first().name)
    }

    @Test fun klasseMitPunktFindetBindestrich() {
        val d = EtfRueckwaerts.lesen(probe)!!
        assertEquals(1, EtfRueckwaerts.finden(d, "BRK.B").size)
        assertEquals(1, EtfRueckwaerts.finden(d, "brk-b").size)
    }

    @Test fun unbekannteAktieUndUngueltigerEtfIndexGebenNichts() {
        val d = EtfRueckwaerts.lesen(probe)!!
        assertTrue(EtfRueckwaerts.finden(d, "ZZZ").isEmpty())
        assertTrue(EtfRueckwaerts.finden(d, "LEER").isEmpty())
    }

    @Test fun ungueltigeDateiGibtNull() {
        assertNull(EtfRueckwaerts.lesen("{}"))
        assertNull(EtfRueckwaerts.lesen("kaputt"))
        assertNull(EtfRueckwaerts.lesen("""{"stand":"gestern","etfs":[],"aktien":{}}"""))
    }

    @Test fun mitgeliefertesBuendelLiestSichUndIstKlein() {
        val f = asset()
        assertTrue("unter 1 MB", f.length() < 1_000_000)
        val d = EtfRueckwaerts.lesen(f.readText(Charsets.UTF_8))
        assertNotNull(d)
        d!!
        assertEquals(10, d.tiefe)
        assertTrue(d.anzahlAktien > 300)
        for (k in listOf("AAPL", "NVDA", "MSFT")) {
            val t = EtfRueckwaerts.finden(d, k)
            assertTrue("$k in mindestens zehn ETFs", t.size >= 10)
            assertEquals(t.sortedByDescending { it.gewicht }.map { it.etf }, t.map { it.etf })
            assertTrue(t.all { it.gewicht in 0.01..100.0 && it.name.isNotBlank() })
        }
        // Wert aus dem Katalog der Heimatboerse
        assertTrue(EtfRueckwaerts.finden(d, "SAP.DE").isNotEmpty())
    }

    @Test fun dateiIstImArchivDieselbe() {
        val im = listOf(File("../daten/etf-rueckwaerts.json"), File("daten/etf-rueckwaerts.json")).first { it.exists() }
        assertEquals(asset().readText(Charsets.UTF_8), im.readText(Charsets.UTF_8))
        assertTrue("etf-rueckwaerts.json" in Alter.dateien())
        assertEquals(1, Datenbuendel.SCHEMA["etf-rueckwaerts.json"])
    }
}
