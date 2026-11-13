package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.DepotRegeln
import de.troetenrepublik.app.data.These
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

// Fassung 5.91: Anlage-These (Prueftermine) und eigene Depotregeln (Warnungen beim Kauf, Depot-Check).
class Fassung591TheseTest {

    private fun ms(j: Int, m: Int, t: Int): Long = LocalDate.of(j, m, t).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test fun vorschlagInDreiMonaten() {
        val v = Anlagethese.vorschlag(ms(2026, 10, 26))
        assertEquals(ms(2027, 1, 26), v)
        // Monatsende: 30. November statt 31.
        assertEquals(ms(2026, 11, 30), Anlagethese.vorschlag(ms(2026, 8, 31)))
    }

    @Test fun faelligeTermine() {
        val jetzt = ms(2026, 10, 26)
        val thesen = mapOf(
            "AAA" to These(pruefAm = ms(2026, 10, 1)),
            "BBB" to These(pruefAm = ms(2026, 12, 1)),
            "CCC" to These(pruefAm = ms(2026, 10, 20), gemeldetAm = ms(2026, 10, 21)),
            "DDD" to These(pruefAm = ms(2026, 9, 1)),
            "EEE" to These(pruefAm = 0L),
        )
        // DDD ist verkauft, BBB noch nicht faellig, CCC schon gemeldet, EEE ohne Termin
        assertEquals(listOf("AAA"), Anlagethese.faellig(thesen, setOf("AAA", "BBB", "CCC", "EEE"), jetzt))
        assertTrue(Anlagethese.offen(thesen.getValue("CCC"), jetzt))
        // Bestaetigen: neuer Termin in drei Monaten, Erinnerung wieder frei
        val neu = Anlagethese.bestaetigen(thesen.getValue("CCC"), jetzt)
        assertEquals(ms(2027, 1, 26), neu.pruefAm)
        assertEquals(0L, neu.gemeldetAm)
        assertTrue(Anlagethese.faellig(mapOf("CCC" to neu), setOf("CCC"), jetzt).isEmpty())
        assertEquals(listOf("CCC"), Anlagethese.faellig(mapOf("CCC" to neu), setOf("CCC"), ms(2027, 1, 27)))
    }

    @Test fun kaufWarnungen() {
        val r = DepotRegeln(maxPosition = 20.0, keineHebel = true, minBargeld = 10.0)
        // Depot 10.000 EUR, davon 2.000 Guthaben, Position bisher 1.000
        val ok = Anlagethese.kaufPruefen(r, 0.0, 10_000.0, 2_000.0, 1_000.0, 500.0, hebel = false)
        assertTrue(ok.isEmpty())
        val zuViel = Anlagethese.kaufPruefen(r, 0.0, 10_000.0, 2_000.0, 1_000.0, 1_500.0, hebel = false)
        // Position 2.500 von 9.999 = 25 %, Guthaben 499 = 5 %
        assertEquals(listOf(Anlagethese.Verstoss.POSITION, Anlagethese.Verstoss.BARGELD), zuViel.map { it.verstoss })
        assertEquals(25.0, zuViel[0].ist, 0.01)
        assertEquals(4.99, zuViel[1].ist, 0.01)
        val hebel = Anlagethese.kaufPruefen(r, 0.0, 10_000.0, 2_000.0, 0.0, 100.0, hebel = true)
        assertEquals(listOf(Anlagethese.Verstoss.HEBEL), hebel.map { it.verstoss })
        // Grenze aus der These
        val these = Anlagethese.kaufPruefen(DepotRegeln(), 10.0, 10_000.0, 2_000.0, 500.0, 600.0, hebel = false)
        assertEquals(listOf(Anlagethese.Verstoss.THESE), these.map { it.verstoss })
        // Ohne Regeln keine Warnung
        assertTrue(Anlagethese.kaufPruefen(DepotRegeln(), 0.0, 10_000.0, 2_000.0, 1_000.0, 1_999.0, hebel = true).isEmpty())
        assertTrue(!DepotRegeln().aktiv)
    }

    @Test fun depotPruefen() {
        val r = DepotRegeln(maxPosition = 30.0, keineHebel = true, minBargeld = 5.0)
        val posten = listOf(
            Anlagethese.Posten("Gross", 4_000.0, false),
            Anlagethese.Posten("Klein", 1_500.0, false),
            Anlagethese.Posten("Turbo", 500.0, true),
        )
        val b = Anlagethese.depotPruefen(r, posten, 100.0)
        assertEquals(listOf(Anlagethese.Verstoss.POSITION, Anlagethese.Verstoss.HEBEL, Anlagethese.Verstoss.BARGELD), b.map { it.verstoss })
        assertEquals("Gross", b[0].name)
        assertEquals(4_000.0 / 6_100.0 * 100, b[0].ist, 1e-9)
        assertEquals("Turbo", b[1].name)
        assertTrue(Anlagethese.depotPruefen(DepotRegeln(), posten, 100.0).isEmpty())
        assertTrue(Anlagethese.depotPruefen(r, emptyList(), 0.0).isEmpty())
    }
}
