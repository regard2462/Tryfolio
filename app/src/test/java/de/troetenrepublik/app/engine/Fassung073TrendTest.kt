package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.Aktienfinder.Bereich
import de.troetenrepublik.app.engine.Aktienfinder.Kennzahl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// [0.73] RSI und Abstand zur 50-Tage-Linie im Kurstrend: gerechnet und gemessen, Gewicht 0
class Fassung073TrendTest {

    private fun daten(symbol: String, jahr: Double, l200: Double, rsi: Double? = null, l50: Double? = null) = Aktienfinder.Daten(
        symbol = symbol, name = symbol, jahr = jahr, linie200 = l200,
        werte = listOfNotNull(rsi?.let { FinderVorlagen.Feld.RSI to it }, l50?.let { FinderVorlagen.Feld.LINIE50 to it }).toMap(),
    )

    @Test
    fun kennzahlenOhneGewichtAendernDenKurstrendNicht() {
        val ohne = listOf(daten("A", 10.0, 5.0), daten("B", 30.0, -4.0), daten("C", -10.0, 8.0))
        val mit = listOf(daten("A", 10.0, 5.0, 95.0, 20.0), daten("B", 30.0, -4.0, 5.0, -20.0), daten("C", -10.0, 8.0, 50.0, 0.0))
        val a = Aktienfinder.bewerten(ohne)
        val b = Aktienfinder.bewerten(mit)
        for (i in a.indices) {
            assertEquals(a[i].bereiche[Bereich.TREND]!!, b[i].bereiche[Bereich.TREND]!!, 1e-9)
            assertEquals(a[i].gesamt, b[i].gesamt, 1e-9)
        }
        // Gerechnet sind sie trotzdem, fuer Anzeige und Messung
        assertEquals(95.0, b[0].kennzahlen.getValue(Kennzahl.RSI), 1e-9)
        assertEquals(100.0, b[0].kennzahlen.getValue(Kennzahl.LINIE50), 1e-9)
        assertEquals(0.0, b[1].kennzahlen.getValue(Kennzahl.LINIE50), 1e-9)
        // Und sie stehen nie unter den Gruenden
        assertTrue(b.flatMap { it.gruende }.none { it.kennzahl == Kennzahl.RSI || it.kennzahl == Kennzahl.LINIE50 })
    }

    @Test
    fun nurKennzahlenOhneGewichtGebenKeinenBereich() {
        val e = Aktienfinder.bewerten(listOf(daten("A", Double.NaN, 0.0).copy(jahr = null, linie200 = null, werte = mapOf(FinderVorlagen.Feld.RSI to 80.0)))).single()
        assertNull(e.bereiche[Bereich.TREND])
        assertFalse(e.gesamt.isNaN())
        assertTrue(e.gruende.none { it.beitrag.isNaN() })
    }

    @Test
    fun rsiPunkteSindDerWertSelbst() {
        assertEquals(70.0, Aktienfinder.rsiPunkte(70.0), 1e-9)
        assertEquals(100.0, Aktienfinder.rsiPunkte(130.0), 1e-9)
    }

    private fun stichtag(n: Int, kz: Kennzahl, gleichlaufend: Boolean): FinderRueckblickMessung.Stichtag {
        val werte = (0 until n).map { i ->
            FinderRueckblickMessung.Wert("S$i", emptyMap(), mapOf(12 to (if (gleichlaufend) i else n - i) / 100.0), mapOf(kz to i.toDouble()))
        }
        return FinderRueckblickMessung.Stichtag(18_000L, werte, mapOf(12 to 0.05), mapOf(12 to 0.05), n)
    }

    @Test
    fun einzelmessungDerKennzahlen() {
        assertEquals(1.0, FinderRueckblickMessung.kennzahlIc(stichtag(40, Kennzahl.RSI, true), Kennzahl.RSI, 12)!!, 1e-9)
        assertEquals(-1.0, FinderRueckblickMessung.kennzahlIc(stichtag(40, Kennzahl.RSI, false), Kennzahl.RSI, 12)!!, 1e-9)
        // Andere Kennzahl: keine Werte, keine Messung; zu wenige Werte ebenso
        assertNull(FinderRueckblickMessung.kennzahlIc(stichtag(40, Kennzahl.RSI, true), Kennzahl.LINIE50, 12))
        assertNull(FinderRueckblickMessung.kennzahlIc(stichtag(10, Kennzahl.RSI, true), Kennzahl.RSI, 12))
        val st = (0 until 5).map { stichtag(40, Kennzahl.RSI, true).copy(tag = 18_000L + it * 30) }
        val w = FinderRueckblickMessung.kennzahlWert(st, Kennzahl.RSI, 12)!!
        assertEquals("RSI", w.bereich)
        assertEquals(1.0, w.ic, 1e-9)
        assertEquals(5, w.stichtage)
    }

    private fun kerzen(n: Int, start: Long, preis: (Int) -> Double) =
        (0 until n).map { i -> val c = preis(i); Candle(start + i * 86_400_000L, c, c, c, c, 0.0) }

    @Test
    fun rueckblickRechnetRsiUndLinieNurAusKursenBisZumStichtag() {
        val start = 1_500_000_000_000L
        val steigend = kerzen(300, start) { 100.0 + it * 0.3 + (if (it % 2 == 0) 0.5 else -0.5) }
        val stichtag = FinderRueckblick.tag(steigend.last().t)
        val d1 = FinderRueckblick.datenZum(FinderRueckblick.Eingaben("X", "X", "BAU", steigend), stichtag, null)!!
        // Danach bricht der Kurs ein: die Werte am Stichtag aendern sich nicht
        val spaeter = steigend + kerzen(100, steigend.last().t + 86_400_000L) { 20.0 }
        val d2 = FinderRueckblick.datenZum(FinderRueckblick.Eingaben("X", "X", "BAU", spaeter), stichtag, null)!!
        val rsi = d1.werte[FinderVorlagen.Feld.RSI]
        assertNotNull(rsi)
        assertTrue("RSI in steigender Reihe ueber 50, war $rsi", rsi!! > 50.0)
        assertNotNull(d1.werte[FinderVorlagen.Feld.LINIE50])
        assertEquals(d1.werte[FinderVorlagen.Feld.RSI]!!, d2.werte[FinderVorlagen.Feld.RSI]!!, 1e-9)
        assertEquals(d1.werte[FinderVorlagen.Feld.LINIE50]!!, d2.werte[FinderVorlagen.Feld.LINIE50]!!, 1e-9)
        assertEquals(d1.linie200!!, d2.linie200!!, 1e-9)
    }
}
