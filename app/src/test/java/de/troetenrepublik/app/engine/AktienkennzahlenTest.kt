package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp
import kotlin.math.sin

class AktienkennzahlenTest {

    private val tag = 86_400_000L
    private val start = 1_500_000_000_000L

    private fun reihe(tage: Int, kurs: (Int) -> Double) = (0 until tage).map { i -> start + i * tag }.let { ts ->
        ts.mapIndexed { i, t -> Candle(t, kurs(i), kurs(i), kurs(i), kurs(i)) }
    }

    @Test
    fun gleichmaessigesWachstum() {
        // 8 % pro Jahr, taeglich verzinst, ueber sechs Jahre
        val r = 0.08
        val k = reihe(6 * 365 + 2) { i -> 100 * exp(kotlin.math.ln(1 + r) * i / 365.25) }
        val e = Aktienkennzahlen.berechnen(k)!!
        assertEquals(0.08, e.rendite1!!, 0.002)
        assertEquals(0.08, e.rendite5!!, 0.002)
        assertNull(e.rendite10)
        assertEquals(0.0, e.maxRueckgang, 1e-9)
        assertEquals(0L, e.laengsterRueckgangTage)
        assertFalse(e.laufend)
        assertEquals(0.0, e.schwankung!!, 1e-6)
    }

    @Test
    fun rueckgaengeUndErholung() {
        // 100 -> 50 (Tag 100) -> wieder 100 an Tag 300 -> 120
        val k = reihe(400) { i ->
            when {
                i <= 100 -> 100 - i * 0.5
                i <= 300 -> 50 + (i - 100) * 0.25
                else -> 100 + (i - 300) * 0.2
            }
        }
        val e = Aktienkennzahlen.berechnen(k)!!
        assertEquals(-0.5, e.maxRueckgang, 1e-9)
        assertEquals(start + 100 * tag, e.maxRueckgangTag)
        assertEquals(300L, e.laengsterRueckgangTage)
        assertFalse(e.laufend)
    }

    @Test
    fun laufenderRueckgang() {
        val k = reihe(200) { i -> if (i < 20) 100.0 + i else 119.0 - (i - 19) * 0.1 }
        val e = Aktienkennzahlen.berechnen(k)!!
        assertTrue(e.laufend)
        assertEquals(180L, e.laengsterRueckgangTage)
    }

    @Test
    fun betaZumIndex() {
        // Aktie schwankt doppelt so stark wie der Index
        val index = reihe(3 * 365) { i -> 1000 * (1 + 0.05 * sin(i / 9.0)) }
        val aktie = reihe(3 * 365) { i -> 50 * (1 + 0.10 * sin(i / 9.0)) }
        val e = Aktienkennzahlen.berechnen(aktie, index)!!
        assertNotNull(e.beta)
        assertEquals(2.0, e.beta!!, 0.25)
        assertEquals(1.0, e.korrelation!!, 0.02)
    }

    @Test
    fun zuWenigKurse() {
        assertNull(Aktienkennzahlen.berechnen(reihe(30) { 10.0 }))
    }

    @Test
    fun passenderIndex() {
        assertEquals("S&P 500", Aktienkennzahlen.index("AAPL", "United States")!!.second)
        assertEquals("DAX", Aktienkennzahlen.index("SAP.DE", null)!!.second)
        assertEquals("DAX", Aktienkennzahlen.index("SAP", "Germany")!!.second)
        assertEquals("MSCI World", Aktienkennzahlen.index("XYZ.XX", null)!!.second)
        assertNull(Aktienkennzahlen.index("^GDAXI", null))
        assertNull(Aktienkennzahlen.index("BTC-EUR", null))
    }

    @Test
    fun branchenVeraenderung() {
        val k = reihe(120) { i -> 100.0 + i }
        assertEquals((219.0 / 218.0 - 1) * 100, Branchentrends.veraenderung(k, 1)!!, 1e-9)
        assertEquals((219.0 / 212.0 - 1) * 100, Branchentrends.veraenderung(k, 7)!!, 1e-9)
        assertEquals((219.0 / 189.0 - 1) * 100, Branchentrends.veraenderung(k, 30)!!, 1e-9)
        assertNull(Branchentrends.veraenderung(k.takeLast(40), 91))
        assertNull(Branchentrends.veraenderung(k.take(1), 1))
    }

    @Test
    fun wikipediaInteresse() {
        val a = List(53) { 100 } + List(7) { 150 }
        val w = Interesse.berechnen(a)!!
        assertEquals(150.0, w.proTag, 1e-9)
        assertEquals(50.0, w.veraenderung, 1e-9)
        assertNull(Interesse.berechnen(List(20) { 5 }))
        assertNull(Interesse.berechnen(List(53) { 0 } + List(7) { 3 }))
    }
}
