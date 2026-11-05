package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Kennzahlvergleich.Richtung
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

// Fassung 5.91: Reverse DCF und Kennzahlenvergleich.
class Fassung591BewertungTest {

    @Test fun reverseDcfBisektion() {
        // KGV 30, 8 % Rendite, Ziel-KGV 15: geschlossen (30 · 1,08^10 / 15)^(1/10) - 1
        val e = ReverseDcf.wachstum(30.0, 8.0, 15.0)!!
        val geschlossen = ((30.0 * 1.08.pow(10) / 15.0).pow(0.1) - 1.0) * 100.0
        assertEquals(geschlossen, e.wachstum, 1e-9)
        assertFalse(e.ausserhalb)
        // Rueckprobe mit dem fairen Wert: bei diesem Wachstum ist der faire Wert genau der Kurs
        val f = Analysis.fairValue(eps = 2.0, price = 60.0, growth = e.wachstum, years = 10, exitPe = 15.0, required = 8.0)!!
        assertEquals(60.0, f.fair, 1e-6)
        // KGV gleich Ziel-KGV: Wachstum gleich Renditeerwartung
        assertEquals(8.0, ReverseDcf.wachstum(15.0, 8.0, 15.0)!!.wachstum, 1e-9)
        // Hoehere Renditeerwartung verlangt mehr Wachstum
        assertTrue(ReverseDcf.wachstum(30.0, 12.0, 15.0)!!.wachstum > e.wachstum)
    }

    @Test fun reverseDcfGrenzfaelle() {
        // Negativer oder fehlender Gewinn: nicht anzeigen
        assertNull(ReverseDcf.wachstum(-12.0, 8.0, 15.0))
        assertNull(ReverseDcf.wachstum(0.0, 8.0, 15.0))
        assertNull(ReverseDcf.wachstum(Double.NaN, 8.0, 15.0))
        assertNull(ReverseDcf.wachstum(20.0, 8.0, 0.0))
        // Extrem hohes KGV: Loesung am Rand
        val hoch = ReverseDcf.wachstum(1e9, 8.0, 15.0)!!
        assertTrue(hoch.ausserhalb)
        assertEquals(ReverseDcf.MAX_WACHSTUM, hoch.wachstum, 1e-9)
        val tief = ReverseDcf.wachstum(1e-9, 8.0, 15.0)!!
        assertTrue(tief.ausserhalb)
    }

    @Test fun bisherUndBranche() {
        assertEquals(10.0, ReverseDcf.bisher(listOf(100.0, 110.0, 121.0))!!, 1e-9)
        assertNull(ReverseDcf.bisher(listOf(-5.0, 10.0)))
        assertNull(ReverseDcf.bisher(listOf(10.0)))
        assertEquals(20.0, ReverseDcf.branchenKgv(listOf(10.0, 15.0, 20.0, 25.0, 30.0, 500.0, -3.0))!!, 1e-12)
        assertNull(ReverseDcf.branchenKgv(listOf(10.0, 15.0, 20.0)))
    }

    @Test fun besterJeZeile() {
        // Bewertung: kleiner ist besser, negative KGVs zaehlen nicht
        assertEquals(1, Kennzahlvergleich.bester(listOf(25.0, 12.0, -4.0, null), Richtung.NIEDRIG))
        // Marge: groesser ist besser, auch negative Werte zaehlen
        assertEquals(0, Kennzahlvergleich.bester(listOf(-1.0, -5.0), Richtung.HOCH))
        // Gleichstand oder nur ein Wert: nichts hervorheben
        assertNull(Kennzahlvergleich.bester(listOf(10.0, 10.0), Richtung.NIEDRIG))
        assertNull(Kennzahlvergleich.bester(listOf(10.0, null), Richtung.NIEDRIG))
        assertEquals(25.0, Kennzahlvergleich.abstandKursziel(100.0, 125.0)!!, 1e-12)
        assertNull(Kennzahlvergleich.abstandKursziel(0.0, 125.0))
        assertNull(Kennzahlvergleich.oderNull(0.0))
    }
}
