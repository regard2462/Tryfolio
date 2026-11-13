package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Fassung 0.77 V2: Die mitgelieferte Messung des Aktienfinder-Rueckblicks entstand mit den heutigen Gewichten,
// der Hinweis auf fruehere Gewichtung (Bedingung in der Aktienfinder-Ansicht: passtZuGewichten) entfaellt.
class Fassung077V2RueckblickNeuTest {

    private fun datei(): FinderRueckblickMessung.Datei {
        val f = listOf(File("src/main/assets/indikatoren/finder_rueckblick.json"), File("../app/src/main/assets/indikatoren/finder_rueckblick.json")).first { it.exists() }
        assertTrue("Messung fehlt", f.isFile)
        return FinderRueckblickMessung.lesen(f.readText()).also { assertNotNull(it) }!!
    }

    @Test fun gebuendelteMessungTraegtDieHeutigenGewichte() {
        val d = datei()
        assertEquals(FinderRueckblick.aktuelleGewichte(), d.gewichte)
        assertTrue(d.passtZuGewichten(FinderRueckblick.aktuelleGewichte()))
    }

    @Test fun gebuendelteMessungIstVollstaendig() {
        val d = datei()
        assertTrue(d.stichtage > 100)
        assertTrue(d.werte > 100)
        listOf(3, 6, 12).forEach { assertNotNull("Horizont $it", d.horizont(it)) }
        assertTrue(d.pruefzeit.isNotEmpty())
        assertTrue(d.halten.isNotEmpty())
        assertNotNull(d.haltenHeute)
    }
}
