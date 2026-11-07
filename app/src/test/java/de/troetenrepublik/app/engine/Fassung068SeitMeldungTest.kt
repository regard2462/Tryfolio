// Tests zur Bewegung seit der Meldung (0.68): Markt, Meldetag, Einordnung und Rechnung.
package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class Fassung068SeitMeldungTest {
    private val eps = 1e-9

    private fun k(tag: LocalDate, close: Double) =
        Candle(tag.toEpochDay() * 86_400_000L + 72_000_000L, close, close, close, close)

    private val d2 = LocalDate.of(2026, 11, 2)
    private val d3 = LocalDate.of(2026, 11, 3)
    private val d4 = LocalDate.of(2026, 11, 4)
    private val d5 = LocalDate.of(2026, 11, 5)

    @Test
    fun marktZuordnung() {
        assertEquals("CL=F", SeitMeldung.markt("rohoel"))
        assertEquals("NG=F", SeitMeldung.markt("gas_us"))
        assertEquals("TTF=F", SeitMeldung.markt("gas_eu"))
        assertEquals("ZC=F", SeitMeldung.markt("duerre"))
        assertEquals("ZW=F", SeitMeldung.markt("duerre", "ZW=F"))
        assertEquals("JETS", SeitMeldung.markt("fluggaeste"))
        assertNull(SeitMeldung.markt("erstantraege"))
        assertNull(SeitMeldung.markt("spr"))
    }

    @Test
    fun vergleichIndex() {
        assertEquals("SPY", SeitMeldung.vergleich("fluggaeste"))
        assertNull(SeitMeldung.vergleich("rohoel"))
    }

    @Test
    fun meldetagRechnung() {
        assertEquals(
            LocalDate.of(2026, 11, 4).toEpochDay(),
            SeitMeldung.meldetag("rohoel", LocalDate.of(2026, 10, 30).toEpochDay())
        )
        assertEquals(
            LocalDate.of(2026, 10, 29).toEpochDay(),
            SeitMeldung.meldetag("gas_us", LocalDate.of(2026, 10, 23).toEpochDay())
        )
        val stand = LocalDate.of(2026, 10, 27).toEpochDay()
        assertEquals(stand + 2, SeitMeldung.meldetag("duerre", stand))
        assertEquals(stand + 1, SeitMeldung.meldetag("gas_eu", stand))
    }

    @Test
    fun einordnenFaelle() {
        assertEquals(SeitMeldung.Lauf.MIT, SeitMeldung.einordnen(0.02, 1))
        assertEquals(SeitMeldung.Lauf.GEGEN, SeitMeldung.einordnen(-0.02, 1))
        assertEquals(SeitMeldung.Lauf.KAUM, SeitMeldung.einordnen(0.005, 1))
        assertEquals(SeitMeldung.Lauf.MIT, SeitMeldung.einordnen(-0.02, -1))
        assertEquals(SeitMeldung.Lauf.OFFEN, SeitMeldung.einordnen(0.03, 0))
    }

    @Test
    fun berechnenMitBasisVorMeldetag() {
        val kurse = listOf(k(d2, 100.0), k(d3, 101.0), k(d4, 103.0), k(d5, 104.0))
        val e = SeitMeldung.berechnen("CL=F", kurse, null, d4.toEpochDay(), 1)
        assertNotNull(e)
        assertEquals(104.0 / 101.0 - 1, e!!.bewegung!!, eps)
        assertEquals(2, e.tage)
        assertEquals(SeitMeldung.Lauf.MIT, e.lauf)
        assertEquals("CL=F", e.markt)
        assertEquals(d4.toEpochDay(), e.ab)
    }

    @Test
    fun berechnenOhneHandelstag() {
        val kurse = listOf(k(d2, 100.0), k(d3, 101.0))
        val e = SeitMeldung.berechnen("CL=F", kurse, null, d4.toEpochDay(), 1)
        assertNotNull(e)
        assertNull(e!!.bewegung)
        assertNull(e.lauf)
        assertEquals(0, e.tage)
    }

    @Test
    fun berechnenLeer() {
        assertNull(SeitMeldung.berechnen("CL=F", emptyList(), null, d4.toEpochDay(), 1))
    }

    @Test
    fun berechnenNurKerzenAbMeldetag() {
        val kurse = listOf(k(d4, 103.0), k(d5, 104.0))
        assertNull(SeitMeldung.berechnen("CL=F", kurse, null, d4.toEpochDay(), 1))
    }

    @Test
    fun berechnenMitIndex() {
        val kurse = listOf(k(d2, 100.0), k(d3, 101.0), k(d4, 103.0), k(d5, 104.0))
        val index = listOf(k(d2, 100.0), k(d3, 100.0), k(d4, 102.0), k(d5, 102.0))
        val e = SeitMeldung.berechnen("JETS", kurse, index, d4.toEpochDay(), 1)
        assertNotNull(e)
        assertEquals((104.0 / 101.0) / (102.0 / 100.0) - 1, e!!.bewegung!!, eps)
    }
}
