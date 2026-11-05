package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// Fassung 5.94: Korrekturen an Fruehwarnung, Blasen-Detektor und Schlagzeilen aus der Fachpruefung.
class Fassung594MakroTest {

    private fun monat(j: Int, m: Int) = LocalDate.of(j, m, 1).toEpochDay() * 86_400_000L

    @Test fun sahmRechnetNachKalendermonaten() {
        // Januar 2025 bis Dezember 2026, Oktober 2025 fehlt
        val quote = (0 until 24).mapNotNull { i ->
            val j = 2025 + i / 12
            val m = i % 12 + 1
            if (j == 2025 && m == 10) null else monat(j, m) to (if (i < 21) 4.0 else 4.0 + (i - 20) * 0.3)
        }
        val z = Fruehwarnung.sahm(quote)!!
        // Dezember 2026: (4,3 + 4,6 + 4,9) / 3 - 4,0
        assertEquals(0.6, z.wert, 1e-9)
        // Der Schnitt fuer November 2025 nutzt nur September und November, nicht August
        val nov = z.verlauf.firstOrNull { it.first == monat(2025, 11) }
        assertTrue(nov == null || kotlin.math.abs(nov.second) < 1e-9)
    }

    @Test fun stimmungBei75IstNochGier() {
        assertEquals(Fruehwarnung.Ampel.GELB, Blasen.stimmung(75.0)!!.ampel)
        assertEquals(Fruehwarnung.Ampel.ROT, Blasen.stimmung(76.0)!!.ampel)
    }

    @Test fun buffettMitQuartalsBipVonFred() {
        val csv = "observation_date,GDP\n2026-01-01,32500.1\n2026-04-01,32800.0\n2026-07-01,33010.0\n"
        val (t, dollar) = Blasen.bipFredLesen(csv)!!
        assertEquals(LocalDate.of(2026, 7, 1).toEpochDay(), t)
        assertEquals(33_010e9, dollar, 1.0)
        val b = Blasen.buffett(75_517.0, dollar, t)!!
        assertEquals(75_517e9 / 33_010e9 * 100, b.wert, 1e-9)
        assertEquals(t, b.stand)
    }

    @Test fun rezessionUndHormusNurMitVerb() {
        assertTrue(NachrichtEinordnung.einordnen("US avoids recession as hiring holds up", emptyList()).isEmpty())
        assertTrue(NachrichtEinordnung.einordnen("Strait of Hormuz reopens to tankers", emptyList()).isEmpty())
        assertTrue(NachrichtEinordnung.einordnen("Germany slides into recession", emptyList()).isNotEmpty())
        assertTrue(NachrichtEinordnung.einordnen("Iran threatens to close the Strait of Hormuz", emptyList()).isNotEmpty())
    }

    @Test
    fun firmaMitMehrerenWoertern() {
        val katalog = listOf("NOVO-B.CO" to "Novo Nordisk A/S", "AAPL" to "Apple Inc.")
        assertTrue(NachrichtEinordnung.firmenIn("Novo Nordisk issues profit warning", katalog).any { it.first == "NOVO-B.CO" })
        assertTrue(NachrichtEinordnung.firmenIn("Apple beats estimates", katalog).any { it.first == "AAPL" })
    }
}
