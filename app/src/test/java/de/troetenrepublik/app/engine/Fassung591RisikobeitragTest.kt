package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.exp
import kotlin.math.sqrt

// Fassung 5.91: Risikobeitrag je Position und Vorschlag mit gleichem Risikoanteil.
class Fassung591RisikobeitragTest {
    private val tag = 86_400_000L

    // Kursreihe aus Log-Renditen, ein Kurs je Woche (seit 5.94 rechnet der Risikobeitrag mit Wochen), Start 100.
    private fun reihe(renditen: List<Double>): List<Candle> {
        var p = 100.0
        val aus = arrayListOf(Candle(7 * tag, p, p, p, p))
        renditen.forEachIndexed { i, r -> p *= exp(r); aus += Candle((i + 2) * 7 * tag, p, p, p, p) }
        return aus
    }

    @Test fun zweiUnkorrelierteWerte() {
        // Muster mit Mittel 0, die sich ueber je vier Tage aufheben: Kovarianz genau 0, Schwankung 1 % und 2 %
        val a = List(200) { if (it % 2 == 0) 0.01 else -0.01 }
        val b = List(200) { if (it % 4 < 2) 0.02 else -0.02 }
        val e = Risikobeitrag.berechnen(listOf(5_000.0, 5_000.0), listOf(reihe(a), reihe(b)), 0L)
        assertNotNull(e)
        // Gleiche Gewichte: Varianzbeitraege 0,5² · 1² zu 0,5² · 2², also 20 % zu 80 %
        assertEquals(0.5, e!!.gewichte[0], 1e-12)
        assertEquals(0.2, e.risiko[0], 1e-9)
        assertEquals(0.8, e.risiko[1], 1e-9)
        assertEquals(1.0, e.risiko.sum(), 1e-12)
        // Gleicher Risikoanteil: Gewichte umgekehrt zur Schwankung, 2/3 zu 1/3
        assertEquals(2.0 / 3.0, e.paritaet!![0], 1e-6)
        assertEquals(1.0 / 3.0, e.paritaet[1], 1e-6)
        // Depotschwankung pro Jahr: Wurzel aus (0,25 · 1 + 0,25 · 4) · Wochenvarianz · 52
        val wochenVar = 0.0001 * 200 / 199
        assertEquals(sqrt(1.25 * wochenVar * 52), e.schwankung, 1e-9)
        assertEquals(1, Risikobeitrag.auffaellig(e))
    }

    @Test fun anteileUndParitaetMitMatrix() {
        // Kleiner Anteil, grosse Schwankung: 10 % Gewicht mit 80 % Schwankung neben 90 % mit 15 %
        val cov = arrayOf(doubleArrayOf(0.64, 0.0), doubleArrayOf(0.0, 0.0225))
        val w = doubleArrayOf(0.1, 0.9)
        val a = Risikobeitrag.anteile(w, cov)!!
        val v1 = 0.01 * 0.64
        val v2 = 0.81 * 0.0225
        assertEquals(v1 / (v1 + v2), a[0], 1e-12)
        // Korrelierte Werte: Beitraege summieren sich trotzdem zu 1, Paritaet ergibt gleiche Anteile
        val k = arrayOf(doubleArrayOf(0.04, 0.012, 0.0), doubleArrayOf(0.012, 0.09, 0.02), doubleArrayOf(0.0, 0.02, 0.16))
        val p = Risikobeitrag.risikoparitaet(k)!!
        assertEquals(1.0, p.sum(), 1e-12)
        val rp = Risikobeitrag.anteile(p, k)!!
        rp.forEach { assertEquals(1.0 / 3.0, it, 1e-8) }
    }

    @Test fun zuWenigDaten() {
        val kurz = reihe(List(20) { 0.01 })
        assertNull(Risikobeitrag.berechnen(listOf(1.0, 1.0), listOf(kurz, kurz), 0L))
        // Ohne Schwankung keine Paritaet
        assertNull(Risikobeitrag.risikoparitaet(arrayOf(doubleArrayOf(0.0, 0.0), doubleArrayOf(0.0, 0.04))))
        // Nur gemeinsame Tage zaehlen: zweite Reihe beginnt spaeter
        val a = reihe(List(200) { if (it % 2 == 0) 0.01 else -0.01 })
        val b = a.drop(180)
        assertNull(Risikobeitrag.renditen(listOf(a, b), 0L))
    }
}
