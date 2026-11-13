package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Optionsanalyse.Kontrakt
import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Kennzahl Vol/OI: Ohne offene Kontrakte gibt es keinen Faktor, die Zeile gilt als neu und steht hinter denen mit Faktor.
class Fassung077V4VolOiTest {
    private val jetzt = 1_800_000_000L
    private val tag = 86_400L

    private fun k(strike: Double, umsatz: Long, offen: Long) =
        Kontrakt(true, strike, jetzt + 20 * tag, 2.0, 1.95, 2.05, umsatz, offen, 0.3)

    @Test fun neuerKontraktOhneFaktor() {
        val kette = listOf(k(105.0, 550, 0), k(110.0, 900, 100), k(100.0, 50, 500), k(95.0, 50, 500))
        val w = AuffaelligeKontrakte.auswerten(100.0, kette, 100, AuffaelligeKontrakte.Art.ALLE, jetzt)
        assertNotNull(w)
        val z = w!!.zeilen
        assertEquals(listOf(110.0, 105.0), z.map { it.kontrakt.strike })
        assertFalse(z[0].neu)
        assertEquals(9.0, z[0].verhaeltnis, 1e-9)
        assertTrue(z[1].neu)
        assertEquals(0.0, z[1].verhaeltnis, 1e-9)
    }

    @Test fun neueKontrakteNachVolumen() {
        val kette = listOf(k(105.0, 300, 0), k(106.0, 800, 0), k(107.0, 400, 100), k(100.0, 50, 500))
        val z = AuffaelligeKontrakte.auswerten(100.0, kette, 100, AuffaelligeKontrakte.Art.ALLE, jetzt)!!.zeilen
        assertEquals(listOf(107.0, 106.0, 105.0), z.map { it.kontrakt.strike })
    }

    @Test fun beschriftungNeu() {
        assertEquals("neu", De.copyNew)
        assertEquals("new", En.copyNew)
    }

    @Test fun gamma() {
        assertEquals("Gamma: 28 von Nasdaq, 31 eigene Rechnung", De.opGexMischung(28, 31))
        assertEquals("Gamma: 28 from Nasdaq, 31 own calculation", En.opGexMischung(28, 31))
        assertFalse(De.opNaeherung.contains("Näherung"))
        assertFalse(En.opNaeherung.contains("Approximation"))
    }
}
