package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

// Fassung 5.91: Strategie-Labor und Backtest-Lotterie mit kuenstlichen Kursreihen (steigend, seitwaerts, Crash, Zufall).
class Fassung591StrategieTest {

    private val tag = 86_400_000L
    private val beginn = 1_500_000_000_000L

    // Kerzen aus Schlusskursen; Eroeffnung = Schluss des Vortags.
    private fun kerzen(schluss: List<Double>): List<Candle> = schluss.mapIndexed { i, c ->
        val o = if (i == 0) c else schluss[i - 1]
        Candle(beginn + i * tag, o, maxOf(o, c) * 1.002, minOf(o, c) * 0.998, c)
    }

    private fun steigend(n: Int = 2520) = kerzen((0 until n).map { 100.0 * 1.0004.pow(it) })

    // Schwingung um 100 mit etwas Rauschen.
    private fun seitwaerts(n: Int = 2520): List<Candle> {
        val r = Random(3)
        return kerzen((0 until n).map { 100.0 + 10.0 * sin(2 * PI * it / 40.0) + 1.5 * r.nextGaussian() })
    }

    // 1000 Tage Anstieg, 120 Tage Absturz um 55 %, danach langsame Erholung.
    private fun crash(): List<Candle> {
        val k = ArrayList<Double>()
        var p = 100.0
        repeat(1000) { p *= 1.0006; k += p }
        repeat(120) { p *= 0.45.pow(1.0 / 120); k += p }
        repeat(900) { p *= 1.0003; k += p }
        return kerzen(k)
    }

    private fun zufall(seed: Int, n: Int = 2520): List<Candle> {
        val r = Random(seed)
        var p = 100.0
        return kerzen((0 until n).map { p *= 1.0 + 0.012 * r.nextGaussian(); p })
    }

    private fun Random.nextGaussian(): Double {
        // Box-Muller
        val u = nextDouble().coerceAtLeast(1e-12)
        val v = nextDouble()
        return kotlin.math.sqrt(-2 * kotlin.math.ln(u)) * kotlin.math.cos(2 * PI * v)
    }

    private fun bericht(k: List<Candle>, art: StrategieLabor.Art, kosten: StrategieLabor.Kosten = StrategieLabor.Kosten(20.0)) =
        StrategieLabor.pruefen(k, 0, kosten).first { it.regel.art == art }

    @Test fun haltenOhneKosten() {
        val k = steigend()
        val b = bericht(k, StrategieLabor.Art.HALTEN, StrategieLabor.Kosten(0.0, 0.0))
        assertEquals(StrategieLabor.START * k.last().close / k.first().open, b.gesamt.endwert, 1e-6)
        assertEquals(1, b.gesamt.trades)
        assertEquals(1.0, b.gesamt.imMarkt, 1e-12)
        assertEquals(0.0, b.gesamt.maxRueckgang, 1e-12)
        assertEquals(1.0, b.gesamt.treffer!!, 1e-12)
        // 0,04 % je Handelstag, auf 365,25 Kalendertage gerechnet
        assertTrue(b.gesamt.renditeJahr!! > 0.10)
    }

    @Test fun steigendHaltenGewinnt() {
        val berichte = StrategieLabor.pruefen(steigend(), 0, StrategieLabor.Kosten(20.0))
        assertEquals(StrategieLabor.REGELN.size, berichte.size)
        val halten = berichte.first { it.regel.art == StrategieLabor.Art.HALTEN }
        berichte.filter { it.regel.art != StrategieLabor.Art.HALTEN }.forEach {
            assertTrue("${it.regel} schlaegt Halten", it.gesamt.endwert < halten.gesamt.endwert)
        }
        // RSI unter 30 kommt in einem glatten Anstieg nie vor: kein Trade, Startkapital bleibt
        val rsi = berichte.first { it.regel.art == StrategieLabor.Art.RSI }
        assertEquals(0, rsi.gesamt.trades)
        assertEquals(StrategieLabor.START, rsi.gesamt.endwert, 1e-9)
        assertEquals(null, rsi.gesamt.treffer)
    }

    @Test fun crashTrendfolgerVerliertWeniger() {
        val k = crash()
        val halten = bericht(k, StrategieLabor.Art.HALTEN)
        val sma = bericht(k, StrategieLabor.Art.SMA)
        val kreuz = bericht(k, StrategieLabor.Art.KREUZ)
        assertTrue(halten.gesamt.maxRueckgang > 0.5)
        assertTrue(sma.gesamt.maxRueckgang < halten.gesamt.maxRueckgang)
        assertTrue(kreuz.gesamt.maxRueckgang < halten.gesamt.maxRueckgang)
        assertTrue(sma.gesamt.imMarkt < 1.0)
    }

    @Test fun seitwaertsKostenFressen() {
        val k = seitwaerts()
        val ohne = bericht(k, StrategieLabor.Art.BOLLINGER, StrategieLabor.Kosten(0.0, 0.0))
        val mit = bericht(k, StrategieLabor.Art.BOLLINGER, StrategieLabor.Kosten(200.0, 1.0))
        assertTrue(ohne.gesamt.trades > 20)
        assertEquals(ohne.gesamt.trades, mit.gesamt.trades)
        assertTrue(mit.gesamt.endwert < ohne.gesamt.endwert)
        val rsi = bericht(k, StrategieLabor.Art.RSI)
        assertTrue(rsi.gesamt.trades > 5)
        val tr = rsi.gesamt.treffer
        assertNotNull(tr)
        assertTrue(tr!! in 0.0..1.0)
    }

    @Test fun lernUndPruefzeitraum() {
        val k = crash()
        val b = bericht(k, StrategieLabor.Art.SMA)
        assertEquals(b.gesamt.verlauf.size, b.lern.verlauf.size + b.pruef.verlauf.size)
        assertEquals(StrategieLabor.teilung(0, k.size), b.lern.verlauf.size)
        assertEquals(k[StrategieLabor.teilung(0, k.size)].t, b.pruef.verlauf.first().first)
        // ab verschiebt den Beginn
        val ab = StrategieLabor.abIndex(k, k[500].t)
        assertEquals(500, ab)
        val spaet = StrategieLabor.pruefen(k, ab, StrategieLabor.Kosten(20.0)).first()
        assertEquals(k.size - 500, spaet.gesamt.verlauf.size)
    }

    @Test fun keinBlickInDieZukunft() {
        val a = crash()
        // Ab Kerze 1500 voellig andere Kurse: die Lage bis 1499 darf sich nicht aendern
        val b = a.take(1500) + a.drop(1500).map { it.copy(open = it.open * 3, high = it.high * 3, low = it.low * 3, close = it.close * 3) }
        for (r in StrategieLabor.REGELN) {
            val za = StrategieLabor.ziel(StrategieLabor.Daten(a), r)
            val zb = StrategieLabor.ziel(StrategieLabor.Daten(b), r)
            for (i in 0 until 1500) assertEquals("$r Tag $i", za[i], zb[i])
        }
    }

    @Test fun normalverteilung() {
        assertEquals(1.959964, StrategieLabor.normInv(0.975), 1e-5)
        assertEquals(0.0, StrategieLabor.normInv(0.5), 1e-9)
        assertEquals(-2.326348, StrategieLabor.normInv(0.01), 1e-5)
        // Das erwartete Maximum waechst mit der Zahl der Versuche
        val v = 0.0004
        assertTrue(StrategieLabor.erwartetesMaximum(10, v) < StrategieLabor.erwartetesMaximum(100, v))
        assertTrue(StrategieLabor.erwartetesMaximum(100, v) < StrategieLabor.erwartetesMaximum(1000, v))
        assertEquals(0.0, StrategieLabor.erwartetesMaximum(1, v), 0.0)
        // Normalverteilte Renditen: Schiefe 0, Woelbung 3
        val r = Random(7)
        val x = (0 until 20_000).map { r.nextGaussian() }
        assertEquals(0.0, StrategieLabor.schiefe(x), 0.06)
        assertEquals(3.0, StrategieLabor.woelbung(x), 0.12)
        // Sharpe gleich sr0 ergibt 50 %
        assertEquals(0.5, StrategieLabor.deflatedSharpe(0.05, 0.05, 1000, 0.0, 3.0), 1e-6)
    }

    @Test fun zufallsreiheDeflatedSharpeKlein() {
        val werte = (1..16).map { seed ->
            val l = StrategieLabor.lotterie(zufall(seed), 0, StrategieLabor.Kosten(20.0), StrategieLabor.Familie.ALLE)!!
            assertEquals(StrategieLabor.varianten(StrategieLabor.Familie.ALLE).size, l.anzahl)
            assertTrue(l.sr0 > 0)
            // Ohne Abschlag fuer die vielen Versuche (Vergleich mit Sharpe 0)
            val naiv = StrategieLabor.deflatedSharpe(
                l.besteLern.sharpe, 0.0, l.besteLern.renditen.size,
                StrategieLabor.schiefe(l.besteLern.renditen), StrategieLabor.woelbung(l.besteLern.renditen),
            )
            l.dsr to naiv
        }
        val dsr = werte.map { it.first }
        val naiv = werte.map { it.second }
        // Die beste Variante sieht ohne Abschlag fast immer echt aus, mit Abschlag nur selten
        assertTrue("naiv $naiv", naiv.count { it > 0.95 } >= 10)
        assertTrue("DSR $dsr", dsr.count { it > 0.95 } <= 2)
        assertTrue("Mittel ${dsr.average()}", dsr.average() < 0.65)
        assertTrue(naiv.average() > dsr.average() + 0.25)
    }

    // Lange Trends mit Wechsel alle 120 Tage: Durchschnittsregeln koennen hier wirklich etwas.
    @Test fun trendreiheDeflatedSharpeHoch() {
        val r = Random(11)
        var p = 100.0
        val k = kerzen((0 until 2520).map { i ->
            val drift = if ((i / 120) % 2 == 0) 0.005 else -0.005
            p *= 1.0 + drift + 0.006 * r.nextGaussian()
            p
        })
        val l = StrategieLabor.lotterie(k, 0, StrategieLabor.Kosten(20.0), StrategieLabor.Familie.DURCHSCHNITT)!!
        assertTrue("DSR ${l.dsr}", l.dsr > 0.95)
        assertTrue(l.bestePruef.endwert > l.haltenPruef.endwert)
    }

    @Test fun variantenZahl() {
        assertEquals(50, StrategieLabor.varianten(StrategieLabor.Familie.DURCHSCHNITT).size)
        assertEquals(147, StrategieLabor.varianten(StrategieLabor.Familie.RSI).size)
        StrategieLabor.varianten(StrategieLabor.Familie.KREUZ).forEach { assertTrue(it.b >= it.a + 10) }
    }
}
