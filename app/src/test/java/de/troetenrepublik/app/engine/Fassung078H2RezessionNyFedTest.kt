package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

// Rezessionswahrscheinlichkeit der New York Fed: heutiger Wert, Verlauf wie gesehen, Trefferbilanz „ueber X % lag sie vor
// N von M Rezessionen, Fehlalarme K“, Stand auf dem Geraet.
class Fassung078H2RezessionNyFedTest {

    private fun asset(name: String) = listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }
    private fun m(j: Int, mo: Int) = RezessionNyFed.monat(j, mo)

    // Kleine Reihe ab 2000-01: p je Monat, Rezessionen 2003-01..2003-06 und 2006-01..2006-03, NBER bekannt bis 2008-06
    private fun reihe(): RezessionNyFed.Reihe {
        val von = m(2000, 1)
        val p = MutableList<Double?>(m(2009, 6) - von + 1) { 10.0 }
        for (i in 0 until 12) p[i] = null
        fun setze(a: Int, b: Int, w: Double) { for (k in a..b) p[k - von] = w }
        // vor 2003-01 gesehen (Monate 2002-01..2002-12 zeigen p[2003-01..2003-12]): 2003-03 bis 2003-05 ueber 30
        setze(m(2003, 3), m(2003, 5), 45.0)
        // vor 2006-01: hoechstens 25 (kein Treffer)
        setze(m(2006, 4), m(2006, 8), 25.0)
        // Fehlalarm: gesehen 2004-03..2004-04 (p[2005-03..2005-04]), keine Rezession bis 2005-04 + 12
        setze(m(2005, 3), m(2005, 4), 35.0)
        // Offen: gesehen 2008-01 (p[2009-01]); NBER reicht nur bis 2008-06
        setze(m(2009, 1), m(2009, 1), 50.0)
        return RezessionNyFed.Reihe(von, p, listOf(m(2003, 1) to m(2003, 6), m(2006, 1) to m(2006, 3)), m(2008, 6), m(2008, 6))
    }

    @Test fun trefferbilanzBeispiel() {
        val b = RezessionNyFed.bilanz(reihe(), 30.0)
        assertNotNull(b)
        b!!
        assertEquals(1, b.treffer)
        assertEquals(2, b.von)
        assertEquals(1, b.fehlalarme)
        assertEquals(1, b.offen)
        assertEquals(2003, b.abJahr)
        // Mit Schwelle 20: auch 2006 getroffen (25 % in 2005-04..2005-08 gesehen); die Phase 2004 liegt mehr als RUHE Monate
        // davor und bleibt ein Fehlalarm
        val c = RezessionNyFed.bilanz(reihe(), 20.0)!!
        assertEquals(2, c.treffer)
        assertEquals(1, c.fehlalarme)
        assertEquals(1, c.offen)
    }

    @Test fun aktuellUndVerlaufWieGesehen() {
        val r = reihe()
        assertEquals(m(2009, 6) to 10.0, RezessionNyFed.aktuell(r))
        val g = RezessionNyFed.gesehen(r)
        // erster gesehener Wert: im Januar 2000 der Wert fuer Januar 2001
        assertEquals(RezessionNyFed.ms(m(2000, 1)), g.first().first)
        assertEquals(10.0, g.first().second, 1e-9)
        // im Maerz 2002 sah man 45 % fuer Maerz 2003
        assertEquals(45.0, g.first { it.first == RezessionNyFed.ms(m(2002, 3)) }.second, 1e-9)
        assertEquals(RezessionNyFed.ms(m(2008, 6)), g.last().first)
        assertEquals(0, RezessionNyFed.stufe(12.4))
        assertEquals(0, RezessionNyFed.stufe(30.0))
        assertEquals(1, RezessionNyFed.stufe(30.1))
        assertEquals(2, RezessionNyFed.stufe(40.0))
    }

    @Test fun mitgelieferteDateiBilanz() {
        // Aus der Datei mit Stand 10/2026 gerechnet (aendert sich mit neuen Daten): ueber 30 % vor 8 von 9 Rezessionen seit
        // 1960 (nicht vor 1960), Fehlalarme 1966, 1966/67 und 2022 bis 2025
        val r = RezessionNyFed.lesen(asset(RezessionNyFed.DATEI).readText())!!
        val b = RezessionNyFed.bilanz(r)!!
        assertEquals(30.0, b.schwelle, 0.0)
        assertEquals(8, b.treffer)
        assertEquals(9, b.von)
        assertEquals(3, b.fehlalarme)
        assertEquals(0, b.offen)
        assertEquals(1960, b.abJahr)
        // Heute: Wert fuer Oktober 2027
        assertEquals(m(2027, 10) to 12.4, RezessionNyFed.aktuell(r))
    }

    @Test fun standAufDemGeraet() {
        val r = reihe()
        val zurueck = RezessionNyFed.lesen(RezessionNyFed.schreiben(r))
        assertNotNull(zurueck)
        zurueck!!
        assertEquals(r.von, zurueck.von)
        assertEquals(r.p, zurueck.p)
        assertEquals(r.nber, zurueck.nber)
        assertEquals(r.bisNber, zurueck.bisNber)
        assertEquals(r.stand, zurueck.stand)
        assertNull(RezessionNyFed.lesen("kaputt"))
    }
}
