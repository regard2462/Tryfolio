package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

// Baerenmaerkte: Rueckgaenge ab 20 % mit Beginn, Tiefe, Dauer bis zum Tief und bis zur Erholung, aus den Ereignissen des
// Warnzeichen-Rueckblicks; die mitgelieferte Datei fuer den S&P 500 seit 1928.
class Fassung078H2BaerenTest {

    private fun asset(name: String) = listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }

    private fun datei(tage: LongArray, kurs: DoubleArray) = WarnRueckblick.Datei(
        "2026-01-01", "2026-01-01", 0.0, emptyList(), emptyList(),
        listOf(WarnRueckblick.IndexErgebnis("sp500", tage.first().toInt(), tage.last().toInt(), WarnRueckblick.ereignisse(tage, kurs), emptyList(), emptyList(), emptyList())),
    )

    @Test fun nurRueckgaengeAbZwanzigProzent() {
        // Hoch 100 an Tag 0, Tief 70 an Tag 60 (-30 %), erholt an Tag 200; Hoch 120 an Tag 300, Tief 105 an Tag 330
        // (-12,5 %, zaehlt nicht), erholt an Tag 360; Hoch 130 an Tag 400, Tief 91 an Tag 500 (-30 %), noch nicht erholt
        val punkte = listOf(0L to 100.0, 60L to 70.0, 200L to 100.0, 300L to 120.0, 330L to 105.0, 360L to 120.0, 400L to 130.0, 500L to 91.0, 520L to 100.0)
        val b = Baerenmaerkte.aus(datei(punkte.map { it.first }.toLongArray(), punkte.map { it.second }.toDoubleArray()), "sp500")!!
        assertEquals(2, b.liste.size)
        assertEquals(Baerenmaerkte.Baer(0, 30.0, 60, 200), b.liste[0].copy(tiefe = Math.round(b.liste[0].tiefe).toDouble()))
        assertEquals(400, b.liste[1].hoch)
        assertEquals(100, b.liste[1].bisTief)
        assertNull(b.liste[1].bisErholt)
        assertEquals(30.0, b.mittelTiefe, 1e-9)
        assertEquals(80, b.mittelBisTief)
        // Mittel der Erholung nur aus abgeschlossenen Rueckgaengen
        assertEquals(200, b.mittelBisErholt)
        assertNull(Baerenmaerkte.aus(null, "sp500"))
        assertNull(Baerenmaerkte.aus(datei(longArrayOf(0, 10), doubleArrayOf(100.0, 95.0)), "sp500"))
        assertEquals(2, Baerenmaerkte.monate(61))
        assertEquals(1, Baerenmaerkte.monate(5))
    }

    @Test fun mitgelieferteDateiSeit1928() {
        val d = WarnRueckblick.lesen(asset("indikatoren/warnzeichen_rueckblick.json").readText())
        val b = Baerenmaerkte.aus(d, "sp500")!!
        assertEquals(1928, b.vonJahr)
        assertTrue(b.liste.size >= 12)
        assertTrue(b.liste.all { it.tiefe >= Baerenmaerkte.AB && it.bisTief > 0 && (it.bisErholt == null || it.bisErholt!! > it.bisTief) })
        assertTrue(b.liste.zipWithNext().all { (x, y) -> y.hoch > x.hoch })
        // Der Rueckgang ab September 1929: mehr als 80 %, Erholung erst nach ueber 20 Jahren
        val erster = b.liste.first()
        assertEquals(LocalDate.of(1929, 9, 16), LocalDate.ofEpochDay(erster.hoch.toLong()))
        assertTrue(erster.tiefe > 80)
        assertTrue(erster.bisErholt!! > 20 * 365)
        assertEquals(b.liste.map { it.tiefe }.average(), b.mittelTiefe, 1e-9)
    }
}
