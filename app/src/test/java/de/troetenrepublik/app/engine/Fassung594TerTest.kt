package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

// Fassung 5.94: Warnung bei gestiegenen laufenden Fondskosten.
class Fassung594TerTest {

    private val tag = 86_400_000L

    @Test fun abgleichMerktNurEchteAenderungen() {
        val a = DepotCheck.terAbgleich(null, 0.20, 0L)
        assertEquals(DepotCheck.TerStand(0.20, 0L), a)
        // Rundungsrauschen bleibt unbeachtet
        assertSame(a, DepotCheck.terAbgleich(a, 0.203, tag))
        val b = DepotCheck.terAbgleich(a, 0.25, 10 * tag)
        assertEquals(DepotCheck.TerStand(0.25, 10 * tag, 0.20), b)
        assertEquals(0.20, DepotCheck.terGestiegen(b, 20 * tag)!!, 0.0)
        // Nach einem Jahr oder bei einer Senkung keine Warnung
        assertNull(DepotCheck.terGestiegen(b, 400 * tag))
        assertNull(DepotCheck.terGestiegen(DepotCheck.terAbgleich(b, 0.15, 30 * tag), 31 * tag))
    }

    @Test fun befundImDepotCheck() {
        fun etf(name: String, ter: Double, vorher: Double?) = DepotCheck.Wert(name, 1000.0, DepotCheck.Art.ETF, "EUR", null, 0.0, ter, vorher)
        val l = DepotCheck.pruefen(listOf(etf("Welt", 0.25, 0.20), etf("Europa", 0.10, null)), 0.0, 0.0, 0.0, 0, 0, mitKonto = false)
        val b = l.single { it.regel == DepotCheck.Regel.TER_GESTIEGEN }
        assertEquals("Welt", b.name)
        assertEquals(0.0005, b.anteil, 1e-12)
        assertEquals(DepotCheck.Stufe.ACHTUNG, b.stufe)
        val ohne = DepotCheck.pruefen(listOf(etf("Europa", 0.10, null)), 0.0, 0.0, 0.0, 0, 0, mitKonto = false)
        assertEquals(0, ohne.count { it.regel == DepotCheck.Regel.TER_GESTIEGEN })
    }
}
