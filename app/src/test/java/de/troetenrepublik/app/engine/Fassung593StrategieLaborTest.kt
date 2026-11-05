package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.PI
import kotlin.random.Random

// Fassung 5.93: leichte Simulation, abbrechbare Lotterie und eigener Zeitraum im Strategie-Labor.
class Fassung593StrategieLaborTest {

    private val tag = 86_400_000L
    private val beginn = 1_500_000_000_000L

    // Kerzen aus Schlusskursen; Eroeffnung = Schluss des Vortags.
    private fun kerzen(schluss: List<Double>): List<Candle> = schluss.mapIndexed { i, c ->
        val o = if (i == 0) c else schluss[i - 1]
        Candle(beginn + i * tag, o, maxOf(o, c) * 1.002, minOf(o, c) * 0.998, c)
    }

    // Zufallsbewegung mit festem Seed, t in Millisekunden im Tagesabstand
    private fun zufall(seed: Int, n: Int = 1500): List<Candle> {
        val r = Random(seed)
        var p = 100.0
        return kerzen((0 until n).map { p *= 1.0 + 0.012 * gauss(r); p })
    }

    private fun gauss(r: Random): Double {
        // Box-Muller
        val u = r.nextDouble().coerceAtLeast(1e-12)
        val v = r.nextDouble()
        return kotlin.math.sqrt(-2 * kotlin.math.ln(u)) * kotlin.math.cos(2 * PI * v)
    }

    @Test fun simulierenLeicht() {
        val k = zufall(1)
        val d = StrategieLabor.Daten(k)
        val z = StrategieLabor.ziel(d, StrategieLabor.Regel(StrategieLabor.Art.SMA, 200))
        val voll = StrategieLabor.simulieren(d, z, 0, k.size, StrategieLabor.Kosten(20.0))!!
        val leicht = StrategieLabor.simulieren(d, z, 0, k.size, StrategieLabor.Kosten(20.0), leicht = true)!!
        assertTrue(leicht.verlauf.isEmpty())
        assertTrue(leicht.renditen.isEmpty())
        assertEquals(voll.endwert, leicht.endwert, 0.0)
        assertEquals(voll.sharpe, leicht.sharpe, 0.0)
        assertEquals(voll.trades, leicht.trades)
        assertEquals(voll.treffer, leicht.treffer)
    }

    @Test fun lotterieRechnetDieBesteVoll() {
        val k = zufall(2)
        val l = StrategieLabor.lotterie(k, 0, StrategieLabor.Kosten(20.0), StrategieLabor.Familie.DURCHSCHNITT)!!
        assertTrue(l.besteLern.renditen.isNotEmpty())
        assertEquals(l.besteLern.renditen.size, l.besteLern.verlauf.size)
        assertEquals(StrategieLabor.varianten(StrategieLabor.Familie.DURCHSCHNITT).size, l.anzahl)
        assertTrue(l.dsr in 0.0..1.0)
    }

    @Test fun lotterieAbbruch() {
        val k = zufall(3)
        var aufrufe = 0
        try {
            StrategieLabor.lotterie(k, 0, StrategieLabor.Kosten(20.0), StrategieLabor.Familie.DURCHSCHNITT) {
                aufrufe++
                if (aufrufe == 5) throw CancellationException("Testabbruch")
            }
            fail("lotterie haette abbrechen muessen")
        } catch (_: CancellationException) {
            assertEquals(5, aufrufe)
        }
        var zaehler = 0
        StrategieLabor.lotterie(k, 0, StrategieLabor.Kosten(20.0), StrategieLabor.Familie.DURCHSCHNITT) { zaehler++ }
        assertEquals(StrategieLabor.varianten(StrategieLabor.Familie.DURCHSCHNITT).size, zaehler)
    }

    @Test fun lotterieWenigeKerzen() {
        val k = zufall(4)
        assertNull(StrategieLabor.lotterie(k, k.size - 50, StrategieLabor.Kosten(20.0), StrategieLabor.Familie.DURCHSCHNITT))
    }

    @Test fun ausschnittGrenzen() {
        val k = zufall(5)
        val a = StrategieLabor.ausschnitt(k, k[100].t, k[999].t)!!
        assertEquals(1000, a.first.size)
        assertEquals(k[999].t, a.first.last().t)
        assertEquals(100, a.second)
        assertNull(StrategieLabor.ausschnitt(k, k[100].t, k.first().t - 1))
        assertNull(StrategieLabor.ausschnitt(k, k.last().t + 1, k.last().t))
    }
}
