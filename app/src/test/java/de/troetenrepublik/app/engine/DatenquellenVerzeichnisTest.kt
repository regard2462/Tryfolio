package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Verzeichnis je Datenquelle: Lizenz, Links und Rueckfall stehen zentral in Quellenbuendel.ARTEN.
class DatenquellenVerzeichnisTest {

    @Test
    fun linksSindHttpsUndOhneDoppelte() {
        for (a in Quellenbuendel.ARTEN) {
            assertTrue(a.id, a.bedingungen.all { it.startsWith("https://") })
            assertEquals(a.id, a.bedingungen.size, a.bedingungen.distinct().size)
        }
    }

    @Test
    fun jedeLizenzUndJederAusfallHatTextInBeidenSprachen() {
        for (a in Quellenbuendel.ARTEN) {
            for (s in listOf(De, En)) {
                assertTrue(a.id, s.dqLizenz(a.lizenz).isNotBlank())
                assertTrue(a.id, s.dqAusfall(a.ersatz, a.ersatzSchluessel).isNotBlank())
                assertFalse(a.id, s.dqAusfall(a.ersatz, a.ersatzSchluessel).contains("—"))
            }
        }
    }

    @Test
    fun ersatzSchluesselNurMitErsatz() {
        for (a in Quellenbuendel.ARTEN) assertTrue(a.id, !a.ersatzSchluessel || a.ersatz.isNotEmpty())
    }
}
