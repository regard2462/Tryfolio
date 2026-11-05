package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 5.96: Linien im Strategie-Labor ab- und wieder anwaehlen.
class Fassung596LaborTest {

    // Dummy ohne Aussagekraft; gezaehlt wird nur die Regel
    private fun bericht(regel: StrategieLabor.Regel): StrategieLabor.Bericht {
        val e = StrategieLabor.Ergebnis(
            endwert = 10_000.0, renditeJahr = null, maxRueckgang = 0.0, trades = 0, treffer = null, imMarkt = 1.0,
            verlauf = listOf(0L to 10_000.0, 1L to 10_000.0), renditen = emptyList(), sharpe = 0.0, jahre = 1.0,
        )
        return StrategieLabor.Bericht(regel, e, e, e)
    }

    // Zweimal umschalten ergibt die Ausgangsmenge, andere Regeln bleiben unberuehrt
    @Test
    fun umschaltenAusUndWiederEin() {
        val a = StrategieLabor.REGELN.first()
        val andere = StrategieLabor.REGELN.last()
        val einmal = StrategieLabor.umschalten(setOf(andere), a)
        assertEquals(setOf(a, andere), einmal)
        assertEquals(setOf(andere), StrategieLabor.umschalten(einmal, a))
    }

    // Ordnung der Liste bleibt, Ausgeblendete fallen weg; alle weg oder keiner
    @Test
    fun sichtbareFolgtDerListe() {
        val liste = StrategieLabor.REGELN.map { bericht(it) }
        val weg = StrategieLabor.REGELN.take(2).toSet()
        assertEquals(liste.drop(2), StrategieLabor.sichtbare(liste, weg))
        assertTrue(StrategieLabor.sichtbare(liste, StrategieLabor.REGELN.toSet()).isEmpty())
        assertEquals(liste, StrategieLabor.sichtbare(liste, emptySet()))
    }
}
