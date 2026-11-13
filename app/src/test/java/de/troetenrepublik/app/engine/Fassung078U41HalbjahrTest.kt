package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.HalbjahrTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

// Halbjahresregel: Winter (November bis April) gegen Sommer (Mai bis Oktober) und der Vergleich gegen durchgehend investiert.
class Fassung078U41HalbjahrTest {
    private fun asset(name: String) = listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }

    // Monatsende als (Jahr * 12 + Monat - 1, Kurs)
    private fun ende(jahr: Int, monat: Int, kurs: Double) = (jahr * 12 + monat - 1) to kurs

    @Test
    fun jahreAusMonatsenden() {
        val enden = listOf(
            ende(2000, 10, 100.0), ende(2001, 4, 110.0), ende(2001, 10, 99.0),
            ende(2002, 4, 99.0), ende(2002, 10, 118.8),
            // Ohne April faellt das Jahr weg
            ende(2003, 10, 120.0),
        )
        val j = Halbjahr.jahre(enden)
        assertEquals(2, j.size)
        assertEquals(2000, j[0].jahr)
        assertEquals(10.0, j[0].winter, 1e-9)
        assertEquals(-10.0, j[0].sommer, 1e-9)
        assertEquals(0.0, j[1].winter, 1e-9)
        assertEquals(20.0, j[1].sommer, 1e-9)
        assertTrue(Halbjahr.jahre(listOf(ende(2000, 10, 100.0), ende(2001, 4, 110.0))).isEmpty())
    }

    @Test
    fun auswertungUndVergleich() {
        val j = listOf(Halbjahr.Jahr(2000, 10.0, -10.0), Halbjahr.Jahr(2001, 0.0, 20.0))
        val x = Halbjahr.auswerten("sp500", j)!!
        assertEquals(2000, x.vonJahr)
        assertEquals(2002, x.bisJahr)
        assertEquals(Kursmuster.Wert(5.0, 1, 2), x.winter)
        assertEquals(Kursmuster.Wert(5.0, 1, 2), x.sommer)
        assertEquals(1, x.winterVorn)
        assertEquals(2, x.jahre)
        // Nur Winter: 1,10 * 1,00 ueber zwei Jahre. Durchgehend: 1,10 * 0,90 * 1,00 * 1,20 = 1,188
        assertEquals(Math.round((Math.sqrt(1.10) - 1) * 10000) / 100.0, x.regelJahr, 1e-9)
        assertEquals(Math.round((Math.sqrt(1.188) - 1) * 10000) / 100.0, x.haltenJahr, 1e-9)
        assertTrue(x.haltenJahr > x.regelJahr)
        assertNull(Halbjahr.auswerten("sp500", emptyList()))
        assertEquals(0.0, Halbjahr.proJahr(listOf(10.0), 0), 0.0)
        assertEquals(-100.0, Halbjahr.proJahr(listOf(-100.0), 1), 0.0)
        assertEquals(21.0, Halbjahr.proJahr(listOf(10.0, 10.0), 1), 1e-9)
    }

    @Test
    fun ausTagesreihe() {
        // Ein Schluss am letzten Tag jedes Monats von Oktober 2000 bis November 2002: jeden Wintermonat +1 %, sonst flach
        var kurs = 100.0
        val tage = ArrayList<Long>()
        val kurse = ArrayList<Double>()
        var d = LocalDate.of(2000, 10, 31)
        while (d <= LocalDate.of(2002, 11, 30)) {
            if (d != LocalDate.of(2000, 10, 31) && Halbjahr.imWinter(d.monthValue)) kurs *= 1.01
            tage += d.toEpochDay()
            kurse += kurs
            d = d.plusDays(1).plusMonths(1).withDayOfMonth(1).minusDays(1)
        }
        val x = Halbjahr.rechnen("dax", tage.toLongArray(), kurse.toDoubleArray())!!
        assertEquals(2000, x.vonJahr)
        assertEquals(2002, x.bisJahr)
        assertEquals(2, x.jahre)
        assertEquals(2, x.winterVorn)
        assertEquals(6.15, x.winter.mittel, 1e-9)
        assertEquals(0.0, x.sommer.mittel, 1e-9)
        assertEquals(x.regelJahr, x.haltenJahr, 1e-9)
        assertTrue(Halbjahr.imWinter(11) && Halbjahr.imWinter(1) && Halbjahr.imWinter(4))
        assertFalse(Halbjahr.imWinter(5) || Halbjahr.imWinter(10))
    }

    @Test
    fun mitgelieferteDatei() {
        val d = Halbjahr.lesen(asset(Halbjahr.DATEI).readText(Charsets.UTF_8))
        assertNotNull(d)
        val sp = Halbjahr.index(d, "sp500")!!
        val dax = Halbjahr.index(d, "dax")!!
        assertEquals(1928, sp.vonJahr)
        assertEquals(1988, dax.vonJahr)
        for (x in listOf(sp, dax)) {
            assertEquals(x.bisJahr - x.vonJahr, x.jahre)
            assertTrue(x.winterVorn in 0..x.jahre && x.winter.positiv in 0..x.jahre && x.sommer.positiv in 0..x.jahre)
            assertTrue(x.regelJahr > -100 && x.haltenJahr > -100)
        }
        // Passt zum Stand der Saisonmuster aus denselben Reihen
        val muster = Kursmuster.lesen(asset(Kursmuster.DATEI).readText(Charsets.UTF_8))!!
        assertEquals(muster.stand, d!!.stand)
        assertEquals(d, Halbjahr.lesen(Halbjahr.schreiben(d)))
        assertNull(Halbjahr.lesen("{}"))
        assertNull(Halbjahr.index(d, "nikkei"))
    }

    @Test
    fun texteInBeidenSprachen() {
        assertEquals(HalbjahrTexte.schluessel(true), HalbjahrTexte.schluessel(false))
        for (k in HalbjahrTexte.schluessel(true)) for (de in listOf(true, false)) {
            val t = HalbjahrTexte.text(de, k, listOf("1", "2", "3", "4"))
            assertTrue(k, t.isNotBlank())
            assertFalse(k, t.contains('{') || t.contains('–') || t.contains('—') || t.contains('…') || t.contains('?'))
        }
        assertEquals("DAX von November bis April", HalbjahrTexte.text(true, "name", listOf("DAX")))
    }
}
