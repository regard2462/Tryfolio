package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.EuInsider
import de.troetenrepublik.app.market.InsiderQuellen
import de.troetenrepublik.app.market.InsiderWelt
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 5.93: Australien (ASX) nur ueber den PDF-Leser (asxLesen) und die Zuordnung importierter Meldungen; holen
// liefert ohne Netzabruf eine leere Liste.
class Fassung593AsxTest {

    // Netz-Fake: zaehlt jeden Aufruf und liefert nichts
    private object ZaehlNetz : InsiderQuellen.Netz {
        var aufrufe = 0
        override suspend fun text(url: String, kopf: Map<String, String>, json: String?): String? { aufrufe++; return null }
        override suspend fun bytes(url: String): ByteArray? { aufrufe++; return null }
    }

    @Test fun holenAustralienOhneAbruf() = runBlocking {
        val e = InsiderWelt.holen(ZaehlNetz, "BHP.AX", "AU000000BHP4", "BHP Group", java.time.LocalDate.of(2026, 10, 23))
        assertNotNull(e)
        assertEquals("AU", e!!.first)
        assertTrue(e.second.isEmpty())
        assertEquals(0, ZaehlNetz.aufrufe)
    }

    @Test fun zuordnungBleibt() {
        assertEquals("AU" to "BHP", InsiderWelt.ort("BHP.AX", ""))
        val g = EuInsider.Geschaeft(e = "BHP Group Limited", isin = "", p = "X", d = "2026-10-21")
        assertTrue(InsiderWelt.passt(g, "AU", "BHP", "", "BHP Group Ltd"))
    }
}
