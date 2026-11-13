package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// [0.77] Mitarbeiterbalken auf echter Jahresachse: Plaetze, letzte 15 Jahre, Jahreszahlen ohne Beruehrung.
class Fassung077S3MitarbeiterAchseTest {

    private fun reihe(vararg jahre: Int) = jahre.map { MitarbeiterReihe.Punkt(it, 1000L) }

    private val apple = reihe(2015, 2016, 2017, 2018, 2020, 2021, 2022, 2026)

    @Test fun einPlatzJeJahrMitLuecken() {
        assertEquals(12, MitarbeiterAnsicht.plaetze(apple))
        assertEquals(0, MitarbeiterAnsicht.platz(apple, 2015))
        assertEquals(5, MitarbeiterAnsicht.platz(apple, 2020))
        assertEquals(11, MitarbeiterAnsicht.platz(apple, 2026))
    }

    @Test fun nurDieLetztenFuenfzehnJahre() {
        val lang = reihe(*(2000..2026 step 2).toList().toIntArray())
        val r = MitarbeiterAnsicht.letzte(lang)
        assertEquals(2012, r.first().jahr)
        assertEquals(2026, r.last().jahr)
        assertEquals(15, MitarbeiterAnsicht.plaetze(reihe(2012, 2026)))
        assertEquals(apple.map { it.jahr }, MitarbeiterAnsicht.letzte(apple).map { it.jahr })
        assertEquals(listOf(2026), MitarbeiterAnsicht.letzte(reihe(2011, 2026)).map { it.jahr })
    }

    @Test fun ersterUndLetzterImmerDabei() {
        for (plaetze in 2..15) for (breite in listOf(10f, 21f, 40f)) {
            val m = MitarbeiterAnsicht.marken(plaetze, breite, 26f)
            assertEquals(0, m.first())
            assertEquals(plaetze - 1, m.last())
        }
    }

    @Test fun breitGenugJedesJahr() {
        assertEquals((0 until 12).toList(), MitarbeiterAnsicht.marken(12, 40f, 26f))
    }

    @Test fun engHatGleichmaessigenAbstandOhneBeruehrung() {
        // 15 Plaetze je 21 Einheiten, Zahl 26 breit: mindestens jeder zweite Platz
        val m = MitarbeiterAnsicht.marken(15, 21f, 26f)
        assertEquals(listOf(0, 2, 4, 6, 8, 10, 12, 14), m)
        assertTrue(m.zipWithNext().all { (a, b) -> (b - a) * 21f >= 26f })
    }
}
