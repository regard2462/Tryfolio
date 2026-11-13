package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Stueckliste.Posten
import de.troetenrepublik.app.i18n.StuecklisteTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 0.78 U40: Stueckliste aus Auftraegen in Euro (ganze Stuecke mit Restbetrag, Bruchstuecke).
class Fassung078U40StuecklisteTest {

    private fun Stueckliste.Ergebnis.stuecke(k: String) = zeilen.firstOrNull { it.key == k }?.stuecke

    @Test
    fun ganzeStueckeMitRest() {
        val e = Stueckliste.rechnen(listOf(Posten("A", 120.0, 500.0), Posten("B", 45.0, 300.0), Posten("C", 150.0, 200.0)), false)
        // Abgerundet: A 4 (480), B 6 (270), C 1 (150), Rest 100. Luecken: A 20, B 30, C 50; nur B ist bezahlbar und
        // fuehrt naeher ans Ziel (30 > 22,5)
        assertEquals(4.0, e.stuecke("A")!!, 1e-9)
        assertEquals(7.0, e.stuecke("B")!!, 1e-9)
        assertEquals(1.0, e.stuecke("C")!!, 1e-9)
        assertEquals(1000.0, e.budget, 1e-9)
        assertEquals(1000.0 - 480.0 - 315.0 - 150.0, e.rest, 1e-9)
        assertEquals(e.budget, e.zeilen.sumOf { it.betrag } + e.rest, 1e-9)
    }

    @Test
    fun keinStueckOhneNutzen() {
        // 90 EUR fuer ein Stueck zu 100: die Luecke (90) ist groesser als ein halbes Stueck, aber das Geld reicht nicht
        val e = Stueckliste.rechnen(listOf(Posten("A", 100.0, 90.0)), false)
        assertTrue(e.zeilen.isEmpty())
        assertEquals(90.0, e.rest, 1e-9)
        // 240 EUR, Stueck zu 100: zwei Stuecke, das dritte waere weiter vom Ziel weg und nicht bezahlbar
        val f = Stueckliste.rechnen(listOf(Posten("A", 100.0, 240.0)), false)
        assertEquals(2.0, f.stuecke("A")!!, 1e-9)
        assertEquals(40.0, f.rest, 1e-9)
    }

    @Test
    fun umschichtenVerkaufDecktKauf() {
        // Verkauf 500 gewuenscht, ganze Stuecke bringen nur 420; fuer die Kaeufe bleiben 420 statt 500
        val e = Stueckliste.rechnen(listOf(Posten("V", 140.0, -500.0, bestand = 10.0), Posten("K", 50.0, 300.0), Posten("L", 80.0, 200.0)), false)
        assertEquals(-3.0, e.stuecke("V")!!, 1e-9)
        assertEquals(420.0, e.budget, 1e-9)
        val gekauft = e.zeilen.filter { it.stuecke > 0 }.sumOf { it.betrag }
        assertTrue(gekauft <= 420.0 + 1e-9)
        assertEquals(420.0, gekauft + e.rest, 1e-9)
        // K 6 (300), L 2 (160) waeren 460: ein Stueck weniger dort, wo am wenigsten fehlt (K, Luecke 0)
        assertEquals(5.0, e.stuecke("K")!!, 1e-9)
        assertEquals(2.0, e.stuecke("L")!!, 1e-9)
        assertEquals(10.0, e.rest, 1e-9)
    }

    @Test
    fun verkaufHoechstensBestand() {
        val e = Stueckliste.rechnen(listOf(Posten("V", 10.0, -500.0, bestand = 12.5)), false)
        assertEquals(-12.0, e.stuecke("V")!!, 1e-9)
        val b = Stueckliste.rechnen(listOf(Posten("V", 10.0, -500.0, bestand = 12.5)), true)
        assertEquals(-12.5, b.stuecke("V")!!, 1e-9)
    }

    @Test
    fun bruchstueckeTreffenGenau() {
        val e = Stueckliste.rechnen(listOf(Posten("A", 120.0, 500.0), Posten("B", 45.0, 300.0), Posten("V", 80.0, -800.0)), true)
        assertEquals(500.0 / 120.0, e.stuecke("A")!!, 1e-9)
        assertEquals(300.0 / 45.0, e.stuecke("B")!!, 1e-9)
        assertEquals(-10.0, e.stuecke("V")!!, 1e-9)
        assertEquals(0.0, e.rest, 1e-9)
    }

    @Test
    fun ausGewichten() {
        val e = Stueckliste.ausGewichten(listOf(Triple("A", 200.0, 60.0), Triple("B", 30.0, 40.0), Triple("X", 0.0, 10.0)), 1_100.0, false)
        // X ohne Kurs faellt weg, sein Anteil (100 EUR) wird nicht angelegt: A 600, B 400
        assertEquals(3.0, e.stuecke("A")!!, 1e-9)
        assertEquals(13.0, e.stuecke("B")!!, 1e-9)
        assertNull(e.stuecke("X"))
        assertEquals(10.0, e.rest, 1e-9)
        assertTrue(Stueckliste.ausGewichten(emptyList(), 1000.0, false).zeilen.isEmpty())
        assertTrue(Stueckliste.ausGewichten(listOf(Triple("A", 10.0, 1.0)), 0.0, false).zeilen.isEmpty())
    }

    @Test
    fun texteInBeidenSprachen() {
        assertEquals(StuecklisteTexte.schluessel(true), StuecklisteTexte.schluessel(false))
        for (k in StuecklisteTexte.schluessel(true)) for (d in listOf(true, false)) {
            val text = StuecklisteTexte.text(d, k, listOf("1", "2"))
            assertTrue(k, text.isNotBlank())
            assertFalse(k, text.contains("{"))
            listOf("—", "–", "…").forEach { assertFalse("$k: $it", text.contains(it)) }
        }
    }
}
