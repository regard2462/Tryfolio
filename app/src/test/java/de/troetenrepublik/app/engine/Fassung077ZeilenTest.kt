package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Zeilen aus Bezeichnung und Wert (0.77): Der Wert bleibt rechts neben der Bezeichnung, auch wenn beides nicht in eine
// Zeile passt. Dazu ein Suchtest, damit kein Baustein wieder einen eigenen Rueckfall "Wert darunter" bekommt.
class Fassung077ZeilenTest {
    @Test
    fun passtBeidesStehtEsNebeneinander() {
        assertEquals(ZeilenLayout.Art.NEBEN, ZeilenLayout.art(200, 100, 320, 12))
        assertEquals(ZeilenLayout.Art.NEBEN, ZeilenLayout.art(208, 100, 320, 12))
    }

    @Test
    fun langeBezeichnungBrichtUmUndDerWertBleibtRechts() {
        // "erwartet laut Yahoo Finance, 13 Analysten" mit "2,30 C$" bei 320 dp
        assertEquals(ZeilenLayout.Art.BEZEICHNUNG_BRICHT, ZeilenLayout.art(290, 60, 320, 12))
        // "Spanne der Schaetzungen" mit "4,10 Mrd. C$ bis 4,17 Mrd. C$"
        assertEquals(ZeilenLayout.Art.BEZEICHNUNG_BRICHT, ZeilenLayout.art(170, 200, 320, 12))
        assertTrue(ZeilenLayout.wertRechts(170, 200, 320, 12))
        // Ein Wert mit drei Vierteln der Zeile bleibt rechts, die Bezeichnung bricht um (0.79)
        assertEquals(ZeilenLayout.Art.BEZEICHNUNG_BRICHT, ZeilenLayout.art(120, 230, 320, 12))
    }

    @Test
    fun nurEinSehrBreiterWertStehtDarunter() {
        assertEquals(ZeilenLayout.Art.WERT_DARUNTER, ZeilenLayout.art(120, 260, 320, 12))
        assertFalse(ZeilenLayout.wertRechts(120, 260, 320, 12))
    }

    // Jeder Baustein, der Bezeichnung und Wert misst und danach verzweigt, muss die gemeinsame Regel benutzen.
    @Test
    fun keinBausteinMitEigenemRueckfall() {
        val wurzel = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "app/src/main/java").isDirectory }
        val ui = File(wurzel, "app/src/main/java/de/troetenrepublik/app/ui")
        val verstoesse = ui.walkTopDown().filter { it.extension == "kt" }.filter { f ->
            val t = f.readText()
            (Regex("val \\(?nebeneinander").containsMatchIn(t) && !t.contains("ZeilenLayout.art(")) ||
                Regex("fun \\w*(InEigeneZeile|WertDarunter|wertUnten)\\w*\\(").containsMatchIn(t)
        }.map { it.name }.toList()
        assertEquals(emptyList<String>(), verstoesse)
    }
}
