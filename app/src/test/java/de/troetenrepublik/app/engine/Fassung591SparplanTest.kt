package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.PlanInterval
import de.troetenrepublik.app.data.SavingsPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class Fassung591SparplanTest {
    private val zone = ZoneId.systemDefault()
    private fun at(date: String, hour: Int = 12): Long = LocalDate.parse(date).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun waehlbareTageJeIntervall() {
        assertEquals(listOf(2, 9, 16, 23), SparplanVorgabe.tage(PlanInterval.MONTHLY))
        assertEquals(listOf(2, 16), SparplanVorgabe.tage(PlanInterval.QUARTERLY))
        assertTrue(SparplanVorgabe.tage(PlanInterval.WEEKLY).isEmpty())
        assertTrue(SparplanVorgabe.tage(PlanInterval.BIWEEKLY).isEmpty())
    }

    @Test fun vorgabeIstWaehlbar() {
        PlanInterval.entries.filter { SparplanVorgabe.tage(it).isNotEmpty() }.forEach {
            assertTrue(SparplanVorgabe.TAG in SparplanVorgabe.tage(it))
        }
        assertEquals(-1, SparplanVorgabe.UHRZEIT)
    }

    @Test fun tagNachIntervallwechsel() {
        // Der 9. ist vierteljaehrlich nicht waehlbar, der 16. schon
        assertEquals(2, SparplanVorgabe.tagFuer(PlanInterval.QUARTERLY, 9))
        assertEquals(16, SparplanVorgabe.tagFuer(PlanInterval.QUARTERLY, 16))
        assertEquals(23, SparplanVorgabe.tagFuer(PlanInterval.MONTHLY, 23))
        assertEquals(2, SparplanVorgabe.tagFuer(PlanInterval.WEEKLY, 16))
    }

    @Test fun planMitVorgabenLaeuftAmZweiten() {
        val plan = SavingsPlan("p", "SAP.DE", "SAP", 50.0, SparplanVorgabe.TAG, createdAt = at("2026-02-10"), uhrzeit = SparplanVorgabe.UHRZEIT)
        val naechste = Trading.nextExecution(plan, at("2026-02-20"))
        assertEquals(LocalDate.parse("2026-03-02"), java.time.Instant.ofEpochMilli(naechste).atZone(zone).toLocalDate())
    }
}
