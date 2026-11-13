package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// [0.77] Haus-Rangliste: gesicherter Trefferanteil (Wilson) und die vier Reihenfolgen.
class Fassung077S3HaeuserSortierungTest {

    private fun haus(name: String, urteile: Int, quote: Double, vorsprung: Double) =
        Haeuserliste.Haus(name, urteile, urteile / 2, urteile - urteile / 2, quote, null, null, null, vorsprung, 10, "2026-10")

    private val cleveland = haus("Cleveland Research", 39, 0.65, 4.0)
    private val stephens = haus("Stephens & Co.", 164, 0.58, 2.5)
    private val klein = haus("Klein", 20, 0.60, 6.0)
    private val alle = listOf(cleveland, stephens, klein)

    @Test fun wilsonUntereGrenze() {
        assertEquals(0.493, Haeuserliste.gesichert(0.65, 39), 0.002)
        assertEquals(0.503, Haeuserliste.gesichert(0.58, 164), 0.002)
        assertEquals(0.0, Haeuserliste.gesichert(0.9, 0), 0.0)
    }

    @Test fun mehrUrteileHebenDenGesichertenWert() {
        assertTrue(Haeuserliste.gesichert(0.6, 400) > Haeuserliste.gesichert(0.6, 40))
        assertTrue(Haeuserliste.gesichert(0.6, 40) < 0.6)
    }

    @Test fun reihenfolgen() {
        fun namen(s: Haeuserliste.Sortierung) = Haeuserliste.sortiert(alle, s).map { it.name }
        assertEquals(listOf("Cleveland Research", "Klein", "Stephens & Co."), namen(Haeuserliste.Sortierung.TREFFER))
        assertEquals(listOf("Stephens & Co.", "Cleveland Research", "Klein"), namen(Haeuserliste.Sortierung.GESICHERT))
        assertEquals(listOf("Klein", "Cleveland Research", "Stephens & Co."), namen(Haeuserliste.Sortierung.VORSPRUNG))
        assertEquals(listOf("Stephens & Co.", "Cleveland Research", "Klein"), namen(Haeuserliste.Sortierung.URTEILE))
    }

    @Test fun gemerkterNameUnbekanntIstTrefferanteil() {
        assertEquals(Haeuserliste.Sortierung.TREFFER, Haeuserliste.Sortierung.von(""))
        assertEquals(Haeuserliste.Sortierung.TREFFER, Haeuserliste.Sortierung.von("x"))
        assertEquals(Haeuserliste.Sortierung.GESICHERT, Haeuserliste.Sortierung.von("GESICHERT"))
    }
}
