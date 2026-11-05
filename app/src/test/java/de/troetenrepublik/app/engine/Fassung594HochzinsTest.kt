package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Hochzins-Spread als Fruehwarnzeichen (5.94): ICE-BofA-Reihe von FRED, Ampel nach Hoehe und Anstieg ueber das Jahrestief.
class Fassung594HochzinsTest {

    private val tag = 86_400_000L
    private fun reihe(vararg w: Double) = w.mapIndexed { i, x -> i * 7 * tag to x }

    @Test
    fun ampel() {
        assertNull(Fruehwarnung.hochzins(emptyList()))
        assertEquals(Fruehwarnung.Ampel.GRUEN, Fruehwarnung.hochzins(reihe(3.2, 3.1, 3.3))!!.ampel)
        assertEquals(Fruehwarnung.Ampel.GELB, Fruehwarnung.hochzins(reihe(3.0, 3.4, 4.1))!!.ampel)
        assertEquals(Fruehwarnung.Ampel.GELB, Fruehwarnung.hochzins(reihe(4.0, 4.6))!!.ampel)
        assertEquals(Fruehwarnung.Ampel.ROT, Fruehwarnung.hochzins(reihe(3.0, 4.0, 5.1))!!.ampel)
        assertEquals(Fruehwarnung.Ampel.ROT, Fruehwarnung.hochzins(reihe(5.8, 6.2))!!.ampel)
        assertEquals(6.2, Fruehwarnung.hochzins(reihe(5.8, 6.2))!!.wert, 1e-9)
    }

    @Test
    fun adresseUndCsv() {
        assertTrue(Fruehwarnung.hochzinsAdresse(java.time.LocalDate.of(2024, 11, 3)).endsWith("id=BAMLH0A0HYM2&cosd=2024-11-03"))
        val r = Fruehindikatoren.fredLesen("observation_date,BAMLH0A0HYM2\n2026-10-29,2.95\n2026-10-30,\n2026-10-31,3.01\n")
        assertEquals(listOf(2.95, 3.01), r.map { it.second })
        assertTrue(Fruehwarnung.HOCHZINS in Fruehwarnung.ALLE)
    }
}
