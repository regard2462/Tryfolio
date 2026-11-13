package de.troetenrepublik.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

// Aenderung der letzten Sitzung fuer Marktuebersichten: vor Handelsbeginn der letzte Schluss gegen den Schluss davor.
class QuoteSitzungTest {

    @Test(timeout = 20_000)
    fun vorHandelsbeginnZaehltDieLetzteSitzung() {
        // Tagesaenderung steht vor Handelsbeginn auf null (Bezug ist der letzte Schluss)
        val q = Quote("^OMX", price = 3309.85, prevClose = 3309.85, regularPrice = 3309.85, schlussDavor = 3267.88, marketState = "PREPRE")
        assertEquals(0.0, q.changePct, 1e-9)
        assertEquals((3309.85 / 3267.88 - 1.0) * 100.0, q.sitzungPct, 1e-9)
    }

    @Test(timeout = 20_000)
    fun imHandelUndDanachGiltDieTagesaenderung() {
        for (zustand in listOf("REGULAR", "POST", "POSTPOST", "CLOSED", "")) {
            val q = Quote("X", price = 102.0, prevClose = 100.0, regularPrice = 102.0, schlussDavor = 100.0, marketState = zustand)
            assertEquals(q.changePct, q.sitzungPct, 1e-9)
        }
    }

    @Test(timeout = 20_000)
    fun ohneSchlussDavorOderMitZweitnotizBleibtDieTagesaenderung() {
        val ohne = Quote("X", price = 50.0, prevClose = 50.0, regularPrice = 50.0, marketState = "PRE")
        assertEquals(0.0, ohne.sitzungPct, 1e-9)
        val zweit = Quote("X", price = 51.0, prevClose = 50.0, regularPrice = 50.0, schlussDavor = 49.0, marketState = "PRE", secondaryExchange = "XETRA")
        assertEquals(zweit.changePct, zweit.sitzungPct, 1e-9)
    }

    @Test(timeout = 20_000)
    fun umrechnungInEuroAendertDieSitzungNicht() {
        // price und prevClose werden in Euro umgerechnet, regularPrice und schlussDavor bleiben in Handelswaehrung
        val q = Quote("X", price = 90.0, prevClose = 90.0, regularPrice = 100.0, schlussDavor = 80.0, marketState = "PRE")
        assertEquals(25.0, q.sitzungPct, 1e-9)
    }

    // [0.77] Neben einer Meldung steht nur die Bewegung von heute
    @Test(timeout = 20_000)
    fun bewegungVonHeute() {
        val jetzt = 1_800_000_000_000L
        val std = 3_600_000L
        // Vor Handelsbeginn mit vorboerslichem Kurs: gestern minus 5,5 %, heute plus 3,6 %
        val vor = Quote("NVDA", price = 103.6, prevClose = 100.0, regularPrice = 100.0, schlussDavor = 105.8, marketState = "PRE", marketTime = jetzt - 60_000L)
        assertEquals(3.6, vor.heutePct(jetzt)!!, 1e-9)
        assertEquals((100.0 / 105.8 - 1.0) * 100.0, vor.sitzungPct, 1e-9)
        // Vor Handelsbeginn ohne vorboerslichen Kurs: heute noch keine Bewegung
        assertEquals(null, Quote("X", price = 100.0, prevClose = 100.0, regularPrice = 100.0, schlussDavor = 105.8, marketState = "PRE", marketTime = jetzt - 15 * std).heutePct(jetzt))
        // Im Handel und am selben Abend
        assertEquals(2.0, Quote("X", price = 102.0, prevClose = 100.0, marketState = "REGULAR", marketTime = jetzt - 60_000L).heutePct(jetzt)!!, 1e-9)
        assertEquals(2.0, Quote("X", price = 102.0, prevClose = 100.0, marketState = "CLOSED", marketTime = jetzt - 5 * std).heutePct(jetzt)!!, 1e-9)
        // Wochenende oder alter Kurs
        assertEquals(null, Quote("X", price = 102.0, prevClose = 100.0, marketState = "CLOSED", marketTime = jetzt - 20 * std).heutePct(jetzt))
        // Zweitnotiz in Deutschland von heute
        assertEquals(1.0, Quote("X", price = 101.0, prevClose = 100.0, marketState = "PRE", secondaryExchange = "XETRA", marketTime = jetzt - std).heutePct(jetzt)!!, 1e-9)
        // Ohne Vortagesschluss
        assertEquals(null, Quote("X", price = 101.0, prevClose = 0.0).heutePct(jetzt))
    }
}
