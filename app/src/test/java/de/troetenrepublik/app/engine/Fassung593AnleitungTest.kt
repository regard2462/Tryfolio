package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import de.troetenrepublik.app.ui.GUIDE_FOTOS
import de.troetenrepublik.app.ui.UEBERBLICK_FOTOS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Kurzanleitung 5.93: zu jeder Seite und jedem Abschnitt des Ueberblicks gehoert ein Eintrag der Fotoliste (null: ohne
// Foto), und jedes Foto liegt in beiden Sprachen bei. Seit 0.78 gibt es nur noch die vier Bildseiten mit gezeichneter
// Bedienung, alles Weitere steht im Ueberblick.
class Fassung593AnleitungTest {
    @Test fun seitenUndFotos() {
        for (s in listOf(De, En)) {
            assertEquals(GUIDE_FOTOS.size, s.guidePages.size)
            assertEquals(GUIDE_FOTOS.size, s.guidePagesDesktop.size)
            assertEquals(UEBERBLICK_FOTOS.size, s.guideFunktionen(false, false).size)
        }
        assertEquals(listOf("start_raster", "suche", "kaufen", "profil"), GUIDE_FOTOS)
        assertTrue(listOf("aktienfinder", "wissen").all { it in UEBERBLICK_FOTOS })
    }

    @Test fun fotosLiegenBei() {
        for (sprache in listOf("de", "en")) for (name in (GUIDE_FOTOS + UEBERBLICK_FOTOS).filterNotNull().toSet()) {
            assertTrue("$sprache/$name", File(listOf(File("src/main/assets"), File("../app/src/main/assets")).first { it.exists() }, "anleitung/$sprache/$name.webp").isFile)
        }
    }
}
