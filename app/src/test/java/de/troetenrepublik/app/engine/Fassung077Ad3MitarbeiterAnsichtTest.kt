package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// [0.77] Mitarbeiterbalken: aktueller Wert als letzter Balken, Veraenderung, Veraenderung.
class Fassung077Ad3MitarbeiterAnsichtTest {

    private fun reihe(vararg p: Pair<Int, Long>) = p.map { MitarbeiterReihe.Punkt(it.first, it.second) }

    // Wikidata fuehrt fuer Apple 147.000 (2017), 132.000 (2018) und 164.000 (2022); die Zeile darueber nennt den neueren Wert aus dem Bericht
    private val apple = reihe(2017 to 147_000L, 2018 to 132_000L, 2022 to 164_000L)

    private fun jahre(r: List<MitarbeiterReihe.Punkt>) = r.map { it.jahr }

    @Test fun aelterReiheBekommtDenAktuellenWertAlsLetztenBalken() {
        val r = MitarbeiterAnsicht.mitAktuell(apple, 166_000L, 2025)
        assertEquals(listOf(2017, 2018, 2022, 2025), jahre(r))
        assertEquals(166_000L, r.last().anzahl)
    }

    @Test fun gleichesJahrMitAnderemWertNimmtDenAktuellen() {
        val r = MitarbeiterAnsicht.mitAktuell(apple, 166_000L, 2022)
        assertEquals(3, r.size)
        assertEquals(166_000L, r.last().anzahl)
        assertEquals(2022, r.last().jahr)
    }

    @Test fun neuereReiheOderFehlendeAngabenBleibenUnveraendert() {
        assertEquals(jahre(apple), jahre(MitarbeiterAnsicht.mitAktuell(apple, 150_000L, 2020)))
        assertEquals(3, MitarbeiterAnsicht.mitAktuell(apple, 166_000L, null).size)
        assertEquals(3, MitarbeiterAnsicht.mitAktuell(apple, 0L, 2025).size)
        assertEquals(164_000L, MitarbeiterAnsicht.mitAktuell(apple, 150_000L, 2020).last().anzahl)
        assertEquals(164_000L, MitarbeiterAnsicht.mitAktuell(apple, 164_000L, 2022).last().anzahl)
    }

    @Test fun veraenderungVomErstenZumLetztenWert() {
        assertEquals(11.56, MitarbeiterAnsicht.veraenderung(apple)!!, 0.01)
        assertEquals(-10.2, MitarbeiterAnsicht.veraenderung(reihe(2010 to 1000L, 2020 to 898L))!!, 0.01)
        assertNull(MitarbeiterAnsicht.veraenderung(reihe(2020 to 5L)))
        assertNull(MitarbeiterAnsicht.veraenderung(reihe(2019 to 0L, 2020 to 5L)))
    }
}
