package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeltAusblickTest {
    private fun daten(): Imf.Datensatz {
        fun r(vararg p: Pair<Int, Double>) = mapOf(*p)
        val wachstum = mapOf(
            Imf.WELT to r(2020 to -2.7, 2021 to 6.5, 2022 to 3.6, 2023 to 3.5, 2024 to 3.3, 2025 to 3.5, 2026 to 3.1, 2027 to 3.4, 2028 to 3.2, 2029 to 3.0),
            "IND" to r(2026 to 6.0, 2027 to 6.5, 2028 to 6.5), "USA" to r(2026 to 2.0, 2027 to 2.0, 2028 to 2.0),
            // DEU und JPN zaehlen in der Rangfolge der Wirtschaftsleistung mit, ohne Prognose fuer die schnellsten Wachser
            "DEU" to r(2026 to 1.0), "JPN" to r(2026 to 0.5),
            "XXX" to r(2026 to 9.0, 2027 to 9.0, 2028 to 9.0),
        )
        val bip = mapOf(
            "IND" to r(2026 to 4000.0, 2028 to 5000.0), "USA" to r(2026 to 30000.0, 2028 to 33000.0),
            "DEU" to r(2026 to 4700.0, 2028 to 4800.0), "JPN" to r(2026 to 4400.0, 2028 to 4300.0), "XXX" to r(2026 to 10.0, 2028 to 20.0),
        )
        val anteil = mapOf(
            "DEU" to r(2026 to 3.0, 2028 to 2.8), "FRA" to r(2026 to 2.0, 2028 to 1.9), "IND" to r(2026 to 8.0, 2028 to 9.0), "CHN" to r(2026 to 19.0, 2028 to 20.0),
        )
        return Imf.Datensatz(0, mapOf(Imf.WACHSTUM to wachstum, Imf.BIP to bip, Imf.WELT_ANTEIL to anteil), Imf.STAND)
    }

    @Test fun balkenMitPrognose() {
        val b = WeltAusblick.balken(daten(), 2026)
        assertEquals(2021, b.first().jahr)
        assertEquals(2029, b.last().jahr)
        assertTrue(!b.first { it.jahr == 2025 }.prognose)
        assertTrue(b.first { it.jahr == 2026 }.prognose)
    }

    @Test fun zuwachsMitZinseszins() {
        val z = WeltAusblick.zuwachs(daten(), 2026, 2028)!!
        assertEquals(1.034 * 1.032 - 1.0, z.gesamt / 100.0, 1e-9)
        assertEquals(null, WeltAusblick.zuwachs(daten(), 2026, 2035))
    }

    @Test fun anteileUndEuroraum() {
        val d = daten()
        assertEquals(5.0, WeltAusblick.anteil(d, WeltAusblick.EURO, 2026)!!, 1e-9)
        val v = WeltAusblick.verschiebung(d, listOf(2026, 2028))
        assertEquals(setOf("CHN", "IND", WeltAusblick.EURO), v.map { it.raum }.toSet())
        assertEquals(1.0, v.first { it.raum == "IND" }.aenderung, 1e-9)
    }

    @Test fun aufsteiger() {
        val d = daten()
        val s = WeltAusblick.schnellste(d, 2026, 2028)
        assertEquals(listOf("IND", "USA"), s.map { it.code })
        val p = WeltAusblick.platzgewinner(d, 2026, 2028)
        assertEquals("IND", p.first().code)
        assertEquals(4, p.first().von)
        assertEquals(2, p.first().bis)
    }

    @Test fun beitraegeZumWeltwachstum() {
        val d = daten()
        val (liste, rest) = WeltAusblick.beitraege(d, 2026, 2028)!!
        // Nur Indien hat Wachstum und Anteil in allen Prognosejahren
        assertEquals(listOf("IND"), liste.map { it.raum })
        assertEquals(6.5 * 8.0 / 100.0, liste.first().punkte, 1e-9)
        assertEquals(null, liste.first().frueher)
        val welt = WeltAusblick.zuwachs(d, 2026, 2028)!!.proJahr
        assertEquals(welt, liste.sumOf { it.punkte } + rest, 1e-9)
        assertEquals(null, WeltAusblick.beitraege(d, 2028, 2028))
    }

    @Test fun beitragVergleichtMitDenLetztenFuenfJahren() {
        fun r(vararg p: Pair<Int, Double>) = mapOf(*p)
        val d = Imf.Datensatz(
            0,
            mapOf(
                Imf.WACHSTUM to mapOf(
                    Imf.WELT to r(2026 to 3.0, 2027 to 3.0),
                    "CHN" to r(2021 to 6.0, 2022 to 6.0, 2023 to 6.0, 2024 to 6.0, 2025 to 6.0, 2027 to 4.0),
                ),
                Imf.WELT_ANTEIL to mapOf("CHN" to r(2026 to 20.0)),
            ),
            Imf.STAND,
        )
        val b = WeltAusblick.beitraege(d, 2026, 2027)!!.first.first()
        assertEquals(4.0, b.schnitt, 1e-9)
        assertEquals(6.0, b.frueher!!, 1e-9)
        assertEquals(-2.0, b.aenderung!!, 1e-9)
    }

    @Test fun begruendungVerschwindetNachSiebenMonatenOderMitNeuemAusblick() {
        fun d(j: Int, m: Int, t: Int) = java.time.LocalDate.of(j, m, t)
        assertTrue(WeltAusblick.begruendungGilt(d(2026, 11, 9), 2031))
        assertTrue(WeltAusblick.begruendungGilt(d(2027, 4, 30), 2031))
        assertTrue(!WeltAusblick.begruendungGilt(d(2027, 5, 1), 2031))
        // Ein spaeterer Prognosehorizont zeigt einen neueren Ausblick an
        assertTrue(!WeltAusblick.begruendungGilt(d(2026, 11, 9), 2032))
    }
}
