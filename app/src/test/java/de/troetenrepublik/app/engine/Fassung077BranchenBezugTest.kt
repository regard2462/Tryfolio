package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Branchen (0.77): Der aufgeklappte Verlauf „Aus 1.000 € waeren …“ endet auf demselben Wert wie die Prozentzahl der Zeile.
class Fassung077BranchenBezugTest {
    private val tag = 86_400_000L
    private fun kerze(t: Long, c: Double) = Candle(t, c, c, c, c, 0.0)

    @Test
    fun verlaufEndetWieDieProzentzahl() {
        // Tagesschluesse: vor sieben Tagen 100, heute 100,71
        val taeglich = listOf(kerze(0, 99.0), kerze(1 * tag, 100.0), kerze(2 * tag, 101.2), kerze(6 * tag, 100.9), kerze(8 * tag, 100.71))
        val prozent = Branchentrends.veraenderung(taeglich, 7)!!
        assertEquals(0.71, prozent, 1e-9)
        // Intraday beginnt erst am zweiten Tag ueber dem Bezugskurs und endet auf dem juengsten Kurs
        val intraday = listOf(kerze(1 * tag + 1, 101.2), kerze(4 * tag, 100.4), kerze(8 * tag, 100.71))
        val bezug = Branchentrends.bezugsVerhaeltnis(taeglich, 7)!! * intraday.last().close
        val verlauf = Verlaufsdarstellung.verlauf(intraday, Verlaufsdarstellung.beginn(intraday, 7), bezug)
        assertEquals(1000 * (1 + prozent / 100), verlauf.last().second, 1e-6)
    }

    @Test
    fun einTagNimmtDenVortagesschluss() {
        val taeglich = listOf(kerze(0, 50.0), kerze(tag, 51.0))
        assertEquals(Verlaufsdarstellung.vortagesschluss(taeglich, 102.0)!!, Branchentrends.bezugsVerhaeltnis(taeglich, 1)!! * 102.0, 1e-9)
    }

    @Test
    fun ohneKurseKeinBezug() {
        assertNull(Branchentrends.bezugsVerhaeltnis(emptyList(), 7))
    }
}
