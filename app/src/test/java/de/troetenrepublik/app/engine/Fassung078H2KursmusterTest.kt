package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

// Praesidentschaftszyklus: Jahr im Zyklus als feste Regel, Jahresrenditen aus Tageskursen, Mittel und Anteil positiver
// Jahre je Zyklusjahr; die mitgelieferte Auswertung.
class Fassung078H2KursmusterTest {

    private fun asset(name: String) = listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }

    @Test fun wahljahreAlleVierJahre() {
        assertEquals(4, Kursmuster.zyklusJahr(1928))
        assertEquals(4, Kursmuster.zyklusJahr(2024))
        assertEquals(1, Kursmuster.zyklusJahr(2025))
        assertEquals(2, Kursmuster.zyklusJahr(2026))
        assertEquals(3, Kursmuster.zyklusJahr(2027))
        assertEquals(4, Kursmuster.zyklusJahr(2028))
    }

    // Tagesreihe: je Monat zwei Handelstage (10. und letzter Tag); der Schluss am Monatsende folgt der Liste jahresEnden
    // (Dezember des Jahres), dazwischen gleichmaessig. Beginn im Dezember vor dem ersten Jahr.
    private fun reihe(erstesJahr: Int, start: Double, jahresEnden: List<Double>, bisMonat: Int = 12): Pair<LongArray, DoubleArray> {
        val tage = ArrayList<Long>()
        val kurs = ArrayList<Double>()
        var vor = start
        tage += LocalDate.of(erstesJahr - 1, 12, 31).toEpochDay(); kurs += start
        jahresEnden.forEachIndexed { j, ende ->
            val letztes = j == jahresEnden.size - 1
            for (m in 1..(if (letztes) bisMonat else 12)) {
                val wert = vor + (ende - vor) * m / 12.0
                val d = LocalDate.of(erstesJahr + j, m, 1)
                tage += d.withDayOfMonth(10).toEpochDay(); kurs += wert * 0.5
                tage += d.withDayOfMonth(d.lengthOfMonth()).toEpochDay(); kurs += wert
            }
            vor = ende
        }
        return tage.toLongArray() to kurs.toDoubleArray()
    }

    @Test fun jahresrenditenNurAusVollenJahren() {
        // 2021 +10 %, 2022 -20 %, 2023 +50 %, 2024 laeuft bis Juni
        val (t, k) = reihe(2021, 100.0, listOf(110.0, 88.0, 132.0, 150.0), bisMonat = 6)
        val enden = Kursmuster.monatsEnden(t, k)
        // Der Juni 2024 ist der letzte Monat der Reihe und gilt als angebrochen
        assertEquals(2024 * 12 + 4, enden.last().first)
        val jahre = Kursmuster.jahresRenditen(enden)
        assertEquals(listOf(2021, 2022, 2023), jahre.map { it.first })
        assertEquals(10.0, jahre[0].second, 1e-9)
        assertEquals(-20.0, jahre[1].second, 1e-9)
        assertEquals(50.0, jahre[2].second, 1e-9)
    }

    @Test fun mittelUndPositiveJahreJeZyklusjahr() {
        // 2017 bis 2024: Zyklusjahre 1,2,3,4,1,2,3,4
        val (t, k) = reihe(2017, 100.0, listOf(120.0, 108.0, 129.6, 129.6, 142.56, 114.048, 136.8576, 150.54336), bisMonat = 12)
        // Ein weiterer Tag im Januar 2025, damit der Dezember 2024 als voller Monat zaehlt
        val t2 = t + LocalDate.of(2025, 1, 2).toEpochDay()
        val k2 = k + 151.0
        val x = Kursmuster.rechnen("sp500", t2, k2, mitZyklus = true)
        assertNotNull(x)
        x!!
        assertEquals(2017, x.vonJahr)
        assertEquals(2024, x.bisJahr)
        // Jahr 1: +20 % und +10 %; Jahr 2: -10 % und -20 %; Jahr 3: +20 % und +20 %; Jahr 4: 0 % und +10 %
        assertEquals(Kursmuster.Wert(15.0, 2, 2), x.zyklus[0])
        assertEquals(Kursmuster.Wert(-15.0, 0, 2), x.zyklus[1])
        assertEquals(Kursmuster.Wert(20.0, 2, 2), x.zyklus[2])
        assertEquals(Kursmuster.Wert(5.0, 1, 2), x.zyklus[3])
        assertEquals("2017-01", x.monatVon)
        assertEquals("2024-12", x.monatBis)
        assertTrue(Kursmuster.rechnen("dax", t2, k2, mitZyklus = false)!!.zyklus.isEmpty())
        // Kein volles Jahr: keine Auswertung
        val (kt, kk) = reihe(2024, 100.0, listOf(110.0), bisMonat = 6)
        assertNull(Kursmuster.rechnen("sp500", kt, kk, mitZyklus = true))
    }

    @Test fun mitgelieferteDatei() {
        val d = Kursmuster.lesen(asset(Kursmuster.DATEI).readText())
        assertNotNull(d)
        val sp = Kursmuster.index(d, "sp500")!!
        assertEquals(1928, sp.vonJahr)
        assertTrue(sp.bisJahr >= 2025)
        assertEquals(4, sp.zyklus.size)
        // Jedes volle Jahr zaehlt in genau einem Zyklusjahr
        assertEquals(sp.bisJahr - sp.vonJahr + 1, sp.zyklus.sumOf { it.anzahl })
        for (z in 1..4) assertEquals((sp.vonJahr..sp.bisJahr).count { Kursmuster.zyklusJahr(it) == z }, sp.zyklus[z - 1].anzahl)
        assertTrue(sp.zyklus.all { it.positiv in 0..it.anzahl && it.mittel in -30.0..30.0 })
        assertEquals(sp, Kursmuster.index(Kursmuster.lesen(Kursmuster.schreiben(d!!)), "sp500"))
        assertTrue(Datenbuendel.SCHEMA.containsKey(Kursmuster.DATEI))
        assertTrue(Kursmuster.DATEI in Alter.dateien())
        assertNull(Kursmuster.lesen("{}"))
    }
}
