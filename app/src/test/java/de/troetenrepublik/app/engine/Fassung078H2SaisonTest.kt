package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

// Saisonmuster: Rendite je Monat aus Tageskursen, Mittel und Anteil positiver Monate je Kalendermonat; die mitgelieferte
// Auswertung fuer S&P 500 und DAX.
class Fassung078H2SaisonTest {

    private fun asset(name: String) = listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }

    // Drei Jahre, je Monat ein Schlusskurs am letzten Tag: Januar +10 %, Februar -5 %, alle anderen Monate unveraendert;
    // im dritten Jahr faellt auch der Januar (-10 %)
    private fun reihe(): Pair<LongArray, DoubleArray> {
        val tage = ArrayList<Long>()
        val kurs = ArrayList<Double>()
        var v = 100.0
        tage += LocalDate.of(2019, 12, 31).toEpochDay(); kurs += v
        for (j in 2020..2022) for (m in 1..12) {
            v *= when {
                m == 1 && j == 2022 -> 0.9
                m == 1 -> 1.1
                m == 2 -> 0.95
                else -> 1.0
            }
            val d = LocalDate.of(j, m, 1)
            tage += d.withDayOfMonth(d.lengthOfMonth()).toEpochDay(); kurs += v
        }
        // angebrochener Januar 2023
        tage += LocalDate.of(2023, 1, 5).toEpochDay(); kurs += v * 2
        return tage.toLongArray() to kurs.toDoubleArray()
    }

    @Test fun mittelUndPositiveMonate() {
        val (t, k) = reihe()
        val monate = Kursmuster.monatsRenditen(Kursmuster.monatsEnden(t, k))
        assertEquals(36, monate.size)
        val s = Kursmuster.saison(monate)
        assertEquals(12, s.size)
        // Januar: +10, +10, -10
        assertEquals(Kursmuster.Wert(3.33, 2, 3), s[0])
        assertEquals(Kursmuster.Wert(-5.0, 0, 3), s[1])
        // Unveraendert zaehlt nicht als Plus
        for (m in 2..11) assertEquals(Kursmuster.Wert(0.0, 0, 3), s[m])
    }

    @Test fun lueckeImMonatsverlaufZaehltNicht() {
        // Nach dem Maerz fehlt der April: der Mai hat keinen Vormonat
        val enden = listOf(2020 * 12 + 1 to 100.0, 2020 * 12 + 2 to 110.0, 2020 * 12 + 4 to 121.0, 2020 * 12 + 5 to 121.0)
        val m = Kursmuster.monatsRenditen(enden)
        assertEquals(listOf(2020 * 12 + 2, 2020 * 12 + 5), m.map { it.first })
        assertEquals(10.0, m[0].second, 1e-9)
    }

    @Test fun mitgelieferteDatei() {
        val d = Kursmuster.lesen(asset(Kursmuster.DATEI).readText())!!
        val sp = Kursmuster.index(d, "sp500")!!
        val dax = Kursmuster.index(d, "dax")!!
        assertEquals("1928-01", sp.monatVon)
        assertEquals("1988-01", dax.monatVon)
        assertTrue(dax.zyklus.isEmpty())
        for (x in listOf(sp, dax)) {
            assertEquals(12, x.monate.size)
            val von = x.monatVon.take(4).toInt() * 12 + x.monatVon.drop(5).toInt() - 1
            val bis = x.monatBis.take(4).toInt() * 12 + x.monatBis.drop(5).toInt() - 1
            // Jeder Monat der Spanne zaehlt in seinem Kalendermonat, hoechstens einzelne Monate fehlen
            for (m in 0..11) {
                val erwartet = (von..bis).count { it % 12 == m }
                assertTrue(x.id + " " + m, x.monate[m].anzahl in (erwartet - 1)..erwartet)
                assertTrue(x.monate[m].positiv in 0..x.monate[m].anzahl)
                assertTrue(x.monate[m].mittel in -5.0..5.0)
            }
        }
    }
}
