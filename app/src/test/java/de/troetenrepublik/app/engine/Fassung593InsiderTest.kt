package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class Fassung593InsiderTest {
    @Test fun abTagEinJahr() {
        assertEquals("2024-03-01", InsiderBetraege.abTag(LocalDate.of(2025, 3, 1)))
    }

    @Test fun abTagNeunzigTage() {
        assertEquals("2024-12-01", InsiderBetraege.abTag(LocalDate.of(2025, 3, 1), 90))
    }

    @Test fun abTagUeberSchaltjahr() {
        assertEquals("2023-03-02", InsiderBetraege.abTag(LocalDate.of(2024, 3, 1)))
    }

    @Test fun summeNurInZielwaehrung() {
        assertEquals(12.5, InsiderBetraege.summe(listOf(10.0 to "EUR", 5.0 to "USD", 2.5 to "EUR"), "EUR"), 1e-9)
    }

    @Test fun summeLeer() {
        assertEquals(0.0, InsiderBetraege.summe(emptyList(), "EUR"), 1e-9)
    }

    @Test fun summeOhnePassendeWaehrung() {
        assertEquals(0.0, InsiderBetraege.summe(listOf(5.0 to "USD"), "EUR"), 1e-9)
    }
}
