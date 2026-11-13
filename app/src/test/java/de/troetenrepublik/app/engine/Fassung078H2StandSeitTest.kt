package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

// „Hoechster Stand seit …“ und „tiefster Stand seit …“: letzter frueherer Wert, der mindestens so hoch (tief) war; nur
// bei mindestens einem Jahr Abstand; Rekord nur bei vollstaendigen Reihen.
class Fassung078H2StandSeitTest {

    private val tag = 86_400_000L
    private fun ms(d: String) = LocalDate.parse(d).toEpochDay() * tag
    private fun p(d: String, v: Double) = ms(d) to v

    @Test fun hoechsterStandSeit() {
        val r = listOf(p("2020-01-01", 50.0), p("2021-03-10", 80.0), p("2022-06-01", 60.0), p("2023-06-01", 65.0), p("2024-06-01", 70.0), p("2024-06-02", 75.0))
        // 75 wurde zuletzt am 10.03.2021 erreicht (80)
        assertEquals(StandSeit.Ergebnis(StandSeit.Art.HOCH, ms("2021-03-10")), StandSeit.pruefen(r))
    }

    @Test fun tiefsterStandSeit() {
        val r = listOf(p("2020-01-01", 50.0), p("2021-03-10", 20.0), p("2022-06-01", 60.0), p("2024-06-01", 40.0), p("2024-06-02", 30.0))
        assertEquals(StandSeit.Ergebnis(StandSeit.Art.TIEF, ms("2021-03-10")), StandSeit.pruefen(r))
    }

    @Test fun unterEinemJahrKeineAussage() {
        // Der letzte hoehere Wert liegt nur elf Monate zurueck
        val r = listOf(p("2020-01-01", 50.0), p("2023-07-10", 80.0), p("2024-06-01", 70.0), p("2024-06-02", 75.0))
        assertNull(StandSeit.pruefen(r))
        // Die ganze Reihe ist kuerzer als ein Jahr
        assertNull(StandSeit.pruefen(listOf(p("2024-01-01", 10.0), p("2024-06-02", 75.0)), vollstaendig = true))
        assertNull(StandSeit.pruefen(listOf(p("2024-06-02", 75.0))))
        assertNull(StandSeit.pruefen(listOf(p("2020-01-01", 1.0), p("2024-06-02", Double.NaN))))
    }

    @Test fun genauEinJahrZaehlt() {
        val r = listOf(p("2020-01-01", 90.0), p("2023-06-03", 80.0), p("2024-06-01", 70.0), p("2024-06-02", 75.0))
        // 2024 ist ein Schaltjahr: vom 03.06.2023 bis zum 02.06.2024 sind es 365 Tage
        assertEquals(StandSeit.Ergebnis(StandSeit.Art.HOCH, ms("2023-06-03")), StandSeit.pruefen(r))
    }

    @Test fun rekordNurBeiVollstaendigerReihe() {
        val r = listOf(p("2015-01-01", 50.0), p("2021-03-10", 60.0), p("2024-06-01", 70.0), p("2024-06-02", 75.0))
        assertNull(StandSeit.pruefen(r))
        assertEquals(StandSeit.Ergebnis(StandSeit.Art.HOCH, ms("2015-01-01"), rekord = true), StandSeit.pruefen(r, vollstaendig = true))
    }

    @Test fun gleicheWerteDavorZaehlenZumHeutigenStand() {
        // Seit drei Tagen 75: die Richtung ergibt der Wert davor (70), gesucht wird vor dem Plateau
        val r = listOf(p("2020-01-01", 50.0), p("2021-03-10", 80.0), p("2024-05-30", 70.0), p("2024-05-31", 75.0), p("2024-06-01", 75.0), p("2024-06-02", 75.0))
        assertEquals(StandSeit.Ergebnis(StandSeit.Art.HOCH, ms("2021-03-10")), StandSeit.pruefen(r))
        // Nur gleiche Werte: keine Richtung
        assertNull(StandSeit.pruefen(listOf(p("2020-01-01", 5.0), p("2024-06-02", 5.0)), vollstaendig = true))
    }

    @Test fun heutigerWertHinterDerReihe() {
        val r = listOf(p("2020-01-01", 50.0), p("2021-03-10", 20.0), p("2024-06-01", 40.0))
        assertEquals(StandSeit.Ergebnis(StandSeit.Art.TIEF, ms("2021-03-10")), StandSeit.mitWert(r, ms("2024-06-02"), 30.0))
        // 45 liegt ueber dem Vortag: zuletzt so hoch am 01.01.2020 (50)
        assertEquals(StandSeit.Ergebnis(StandSeit.Art.HOCH, ms("2020-01-01")), StandSeit.mitWert(r, ms("2024-06-02"), 45.0))
    }

    @Test fun rangInDerReihe() {
        assertEquals(75, StandSeit.rang(listOf(1L to 1.0, 2L to 2.0, 3L to 9.0, 4L to 3.0)))
        assertEquals(100, StandSeit.rang(listOf(1L to 1.0, 2L to 2.0)))
        assertNull(StandSeit.rang(listOf(1L to 1.0)))
    }

    @Test fun marktphaseOrdnetDenLetztenSchlussEin() {
        // Drei Jahre Tageskurse: Hoch von 200 nach 100 Tagen, danach um 150, am Ende 180
        val start = LocalDate.parse("2021-01-01").toEpochDay()
        val kurse = (0 until 1100).map { i ->
            (start + i) * tag to when {
                i == 100 -> 200.0
                i == 1099 -> 180.0
                else -> 150.0 + i % 5
            }
        }
        val ph = Marktphase.phase("sp500", kurse)!!
        assertEquals(StandSeit.Ergebnis(StandSeit.Art.HOCH, (start + 100) * tag), ph.standSeit)
    }
}
