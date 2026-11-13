package de.troetenrepublik.app.engine

import de.troetenrepublik.app.market.Aktualisierung
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// Alterung der App: Baudatum, Stufen nach 12 und 24 Monaten, zentrale Uhr.
class AlterungAppTest {

    private fun tag(s: String): Long = LocalDate.parse(s).toEpochDay() * 86_400_000L + 3_600_000L

    @After fun uhrZurueck() { Uhr.vorstellenTage(0) }

    @Test fun baudatumHatDieForm() {
        assertTrue(Regex("\\d{4}-\\d{2}-\\d{2}").matches(Aktualisierung.BAUDATUM))
        // Nie in der Zukunft: das Baudatum liegt nicht nach dem Tag, an dem die Fassung geprueft wird
        assertTrue(LocalDate.parse(Aktualisierung.BAUDATUM).toEpochDay() <= Uhr.heute().toEpochDay() + 1)
    }

    @Test fun alterInMonaten() {
        assertEquals(0, Aktualisierung.alterMonate(tag("2026-11-09"), "2026-11-09"))
        assertEquals(0, Aktualisierung.alterMonate(tag("2026-12-08"), "2026-11-09"))
        assertEquals(1, Aktualisierung.alterMonate(tag("2026-12-09"), "2026-11-09"))
        assertEquals(12, Aktualisierung.alterMonate(tag("2027-11-09"), "2026-11-09"))
        assertEquals(11, Aktualisierung.alterMonate(tag("2027-11-08"), "2026-11-09"))
        assertEquals(36, Aktualisierung.alterMonate(tag("2029-11-30"), "2026-11-09"))
        // Verstellte Uhr oder kaputtes Datum: kein Alter
        assertEquals(0, Aktualisierung.alterMonate(tag("2020-01-01"), "2026-11-09"))
        assertEquals(0, Aktualisierung.alterMonate(tag("2030-01-01"), "kaputt"))
    }

    @Test fun stufen() {
        assertEquals(0, Aktualisierung.alterStufe(0))
        assertEquals(0, Aktualisierung.alterStufe(11))
        assertEquals(12, Aktualisierung.alterStufe(12))
        assertEquals(12, Aktualisierung.alterStufe(23))
        assertEquals(24, Aktualisierung.alterStufe(24))
        assertEquals(24, Aktualisierung.alterStufe(60))
    }

    @Test fun uhrLaesstSichVorstellen() {
        val heute = Uhr.heute()
        Uhr.vorstellenTage(365)
        assertEquals(heute.toEpochDay() + 365, Uhr.heute().toEpochDay())
        val monate = Aktualisierung.alterMonate(Uhr.jetzt())
        assertTrue(Aktualisierung.alterStufe(monate) >= 12)
        Uhr.vorstellenTage(0)
        assertEquals(heute, Uhr.heute())
    }
}
