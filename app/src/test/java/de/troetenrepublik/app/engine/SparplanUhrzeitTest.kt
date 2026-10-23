package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.PlanInterval
import de.troetenrepublik.app.data.SavingsPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SparplanUhrzeitTest {
    private val zone = ZoneId.systemDefault()
    private fun at(date: String, hour: Int = 12, minute: Int = 0): Long =
        LocalDate.parse(date).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private val monatlich = SavingsPlan("p", "SAP.DE", "SAP", 50.0, 2, createdAt = at("2026-02-10"), uhrzeit = 14 * 60)

    @Test fun amAusfuehrungstagErstAbDerUhrzeit() {
        assertFalse(Trading.planDue(monatlich, at("2026-03-02", 13, 59)))
        assertTrue(Trading.planDue(monatlich, at("2026-03-02", 14, 0)))
        assertTrue(Trading.planDue(monatlich, at("2026-03-02", 21, 30)))
    }

    @Test fun verpassterTerminWirdSofortNachgeholt() {
        // Am 2. lief die App nicht: am 3. auch vor 14 Uhr faellig.
        assertTrue(Trading.planDue(monatlich, at("2026-03-03", 8, 0)))
    }

    @Test fun ohneUhrzeitWieBisher() {
        assertTrue(Trading.planDue(monatlich.copy(uhrzeit = -1), at("2026-03-02", 0, 5)))
    }

    @Test fun naechsteAusfuehrungZeigtDieUhrzeit() {
        assertEquals(at("2026-03-02", 14, 0), Trading.nextExecution(monatlich, at("2026-02-20", 9, 0)))
        // Heute vor der Uhrzeit: heute zur Uhrzeit.
        assertEquals(at("2026-03-02", 14, 0), Trading.nextExecution(monatlich, at("2026-03-02", 9, 0)))
        // Heute nach der Uhrzeit, noch nicht gelaufen: sofort.
        val spaet = at("2026-03-02", 16, 0)
        assertEquals(spaet, Trading.nextExecution(monatlich, spaet))
    }

    @Test fun woechentlichZurUhrzeit() {
        val w = SavingsPlan("w", "SAP.DE", "SAP", 25.0, 2, lastRun = at("2026-03-02", 10, 0), createdAt = at("2026-02-01"),
            interval = PlanInterval.WEEKLY, uhrzeit = 10 * 60)
        assertFalse(Trading.planDue(w, at("2026-03-09", 9, 59)))
        assertTrue(Trading.planDue(w, at("2026-03-09", 10, 0)))
        assertEquals(at("2026-03-09", 10, 0), Trading.nextExecution(w, at("2026-03-05", 18, 0)))
    }
}
