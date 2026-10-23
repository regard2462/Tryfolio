package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Trendverlauf.Archiv
import de.troetenrepublik.app.engine.Trendverlauf.Messung
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Verlauf der Reddit-Erwaehnungen: Tage mitschreiben, Woche bis Jahr zusammenzaehlen, Kuerzel zuordnen.
class TrendverlaufTest {

    private val q = "all-stocks"

    @Test
    fun vortagKommtGleichMitUndHeuteZaehltDieGroessteZahl() {
        var a = Trendverlauf.eintragen(Archiv(), q, 100, listOf(Messung("GME", "GameStop", 50, 30)))
        assertEquals(mapOf(99L to mapOf("GME" to 30), 100L to mapOf("GME" to 50)), a.tage[q])
        // Spaeter am selben Tag: weniger Erwaehnungen aendern nichts, mehr ersetzen; der Vortag bleibt
        a = Trendverlauf.eintragen(a, q, 100, listOf(Messung("GME", "GameStop", 40, 99)))
        assertEquals(50, a.tage[q]!![100]!!["GME"])
        assertEquals(30, a.tage[q]!![99]!!["GME"])
        a = Trendverlauf.eintragen(a, q, 100, listOf(Messung("GME", "GameStop", 70, null)))
        assertEquals(70, a.tage[q]!![100]!!["GME"])
        assertEquals("GameStop", a.namen["GME"])
        assertEquals(100 * 86_400_000L, a.abgerufen[q])
    }

    @Test
    fun kryptoOhneEndungUndAlteTageFallenWeg() {
        var a = Trendverlauf.eintragen(Archiv(), "all-crypto", 1, listOf(Messung("BTC.X", "Bitcoin", 5, null)))
        assertEquals(mapOf("BTC" to 5), a.tage["all-crypto"]!![1])
        a = Trendverlauf.eintragen(a, "all-crypto", 1 + Trendverlauf.AUFBEWAHREN_TAGE.toLong(), listOf(Messung("ETH.X", "Ethereum", 3, null)))
        assertFalse(a.tage["all-crypto"]!!.containsKey(1))
        // Namen ohne Eintrag fallen mit weg
        assertNull(a.namen["BTC"])
        assertEquals("Ethereum", a.namen["ETH"])
    }

    @Test
    fun summenRanglisteReiheSpitze() {
        var a = Archiv()
        for (t in 1L..10L) a = Trendverlauf.eintragen(a, q, t, listOf(Messung("AAA", "A", t.toInt(), null), Messung("BBB", "B", if (t == 4L) 100 else 1, null)))
        assertEquals(mapOf("AAA" to 8 + 9 + 10, "BBB" to 3), Trendverlauf.summen(a, q, 3, 10))
        assertEquals(listOf("BBB" to 109, "AAA" to 55), Trendverlauf.rangliste(a, q, 30, 10))
        assertEquals(listOf(0, 0, 1, 2, 3), Trendverlauf.reihe(a, q, "AAA", 5, 3))
        assertEquals(4L to 100, Trendverlauf.spitze(a, q, "BBB", 30, 10))
        assertNull(Trendverlauf.spitze(a, q, "CCC", 30, 10))
        assertEquals(1L, Trendverlauf.seit(a, q))
        assertNull(Trendverlauf.seit(a, "wallstreetbets"))
    }

    @Test
    fun buendelnHaeltDenJuengstenBlockRechts() {
        assertEquals(listOf(3, 7 * 2), Trendverlauf.buendeln(List(10) { if (it < 3) 1 else 2 }, 7))
        assertEquals(List(52) { 7 }, Trendverlauf.buendeln(List(364) { 1 }, 7))
        assertEquals(listOf(1, 2), Trendverlauf.buendeln(listOf(1, 2), 1))
    }

    @Test
    fun kuerzelDerApp() {
        val namen = mapOf("SAP" to "SAP SE", "RHM" to "Rheinmetall")
        assertEquals("AAPL", Trendverlauf.kuerzelFuer("AAPL", "Apple", namen))
        assertEquals("BTC", Trendverlauf.kuerzelFuer("BTC-EUR", "Bitcoin", namen))
        assertEquals("SAP", Trendverlauf.kuerzelFuer("SAP.DE", "SAP", namen))
        // Gleiches Kuerzel, andere Firma
        assertNull(Trendverlauf.kuerzelFuer("RHM.DE", "Siemens Healthineers", namen))
        assertNull(Trendverlauf.kuerzelFuer("ALV.DE", "Allianz", namen))
        // Indizes, Waehrungen und Aktiengattungen nicht
        assertNull(Trendverlauf.kuerzelFuer("^GDAXI", "DAX", namen))
        assertNull(Trendverlauf.kuerzelFuer("EURUSD=X", "EUR/USD", namen))
        assertNull(Trendverlauf.kuerzelFuer("BRK-B", "Berkshire Hathaway", namen))
        assertTrue(Trendverlauf.normal(" btc.x ") == "BTC")
    }
}
