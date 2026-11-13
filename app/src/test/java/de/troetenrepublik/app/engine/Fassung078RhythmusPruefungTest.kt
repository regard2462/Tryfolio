package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// 16-Wochen-Rhythmus (0.78): Rang der Wochen 13 bis 15, Urteil ueber beide Haelften und die Lage heute.
class Fassung078RhythmusPruefungTest {
    // Wochenschluesse ab dem Anker: je Woche ein Kurs am Freitag; aenderung(stelle, nummer) als Faktor
    private fun reihe(wochen: Int, aenderung: (Int, Long) -> Double): List<Pair<Long, Double>> {
        var kurs = 100.0
        val aus = ArrayList<Pair<Long, Double>>()
        for (n in 0 until wochen.toLong()) {
            val tag = Rhythmus16.ANKER + n * 7 + 4
            kurs *= aenderung(Rhythmus16.woche(tag), n)
            aus += tag to kurs
        }
        return aus
    }

    @Test fun rangDesFensters() {
        val flach = MutableList(16) { 1.0 }
        assertEquals(1, Rhythmus16.rang(flach))
        val stark = flach.toMutableList().also { it[12] = 1.2; it[13] = 1.2; it[14] = 1.2 }
        assertEquals(1, Rhythmus16.rang(stark))
        // Wochen 3 bis 5 sind staerker: die Fenster ab 1 bis 5 enthalten mindestens eine davon und liegen vor 13 bis 15
        val anders = flach.toMutableList().also { it[2] = 1.3; it[3] = 1.3; it[4] = 1.3 }
        assertEquals(6, Rhythmus16.rang(anders))
    }

    @Test fun belegtNurWennBeideHaelftenStimmen() {
        val heute = Rhythmus16.ANKER + 2200L * 7
        // In jeder Runde steigen die Wochen 13 bis 15, sonst nichts
        val immer = Rhythmus16.pruefen(reihe(2200) { s, _ -> if (s in 13..15) 1.01 else 1.0 }, heute)!!
        assertEquals(Rhythmus16.Urteil.BELEGT, immer.urteil)
        assertEquals(1, immer.rang)
        // Nur in der ersten Haelfte: danach steigen die Wochen 5 bis 7
        val halb = Rhythmus16.pruefen(reihe(2200) { s, n -> if (n < 1100) (if (s in 13..15) 1.01 else 1.0) else (if (s in 5..7) 1.01 else 1.0) }, heute)!!
        assertEquals(Rhythmus16.Urteil.NICHT_BELEGT, halb.urteil)
        assertEquals(1, halb.rangFrueh)
        // Zu kurze Reihe
        assertNull(Rhythmus16.pruefen(reihe(300) { _, _ -> 1.0 }, Rhythmus16.ANKER + 300L * 7))
    }

    @Test fun beginnDerWochen13Bis15() {
        // Woche 1: die Wochen 13 bis 15 beginnen zwoelf Wochen spaeter
        assertEquals(Rhythmus16.ANKER + 12 * 7, Rhythmus16.beginnGuteWochen(Rhythmus16.ANKER + 2))
        // Mitten in Woche 14: der laufende Abschnitt begann am Montag der Woche 13
        assertEquals(Rhythmus16.ANKER + 12 * 7, Rhythmus16.beginnGuteWochen(Rhythmus16.ANKER + 13 * 7 + 3))
        // Woche 16: der naechste Abschnitt liegt im folgenden Zyklus
        assertEquals(Rhythmus16.ANKER + 28 * 7, Rhythmus16.beginnGuteWochen(Rhythmus16.ANKER + 15 * 7))
    }

    // Regel im Strategietest: gekauft wird zur ersten Eroeffnung der Woche 13, verkauft zur ersten der Woche 16
    @Test fun regelImStrategietest() {
        val tage = (0L until 16 * 7 * 12).filter { it % 7 < 5 }
        val kerzen = tage.map { de.troetenrepublik.app.data.Candle((Rhythmus16.ANKER + it) * 86_400_000L + 7 * 3_600_000L, 100.0, 100.0, 100.0, 100.0) }
        val d = StrategieLabor.Daten(kerzen)
        val regel = StrategieLabor.REGELN.single { it.art == StrategieLabor.Art.RHYTHMUS }
        val z = StrategieLabor.ziel(d, regel)
        for (i in 1 until kerzen.size) assertEquals("Tag ${tage[i]}", Rhythmus16.woche(Rhythmus16.ANKER + tage[i]) in Rhythmus16.INVESTIERT, z[i - 1])
        val e = StrategieLabor.simulieren(d, z, 0, kerzen.size, StrategieLabor.Kosten(0.0, 0.0))!!
        assertEquals(12, e.trades)
        assertEquals(3.0 / 16, e.imMarkt, 1e-9)
    }
}
