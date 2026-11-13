package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Qualitaet.Punkt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Piotroski-Punkt „Keine neuen Aktien ausgegeben“ (0.77): gezaehlt werden die ausgegebenen Aktien. Gibt die Firma eigene
// Aktien aus dem Bestand ab, steigen die Aktien im Umlauf, ohne dass neue Aktien entstehen.
class Fassung077AktienzahlTest {
    private fun jahr(ende: String, umlauf: Double?, ausgegeben: Double?) = Qualitaet.Jahr(
        ende, nettogewinn = 10.0, bilanzsumme = 100.0, cashflow = 12.0, aktien = umlauf, aktienAusgegeben = ausgegeben,
    )

    private fun punkt(v: Qualitaet.Jahr, j: Qualitaet.Jahr) = Qualitaet.piotroski(listOf(v, j))!!.pruefungen.first { it.punkt == Punkt.KEINE_NEUEN_AKTIEN }

    @Test
    fun eigeneAktienAbgegebenIstKeineAusgabe() {
        // Umlauf 4.441,3 Mio. auf 4.443,5 Mio., ausgegeben unveraendert 4.465 Mio.
        val p = punkt(jahr("2024-12-31", 4_441_340_468.0, 4_465_000_000.0), jahr("2025-12-31", 4_443_479_341.0, 4_465_000_000.0))
        assertEquals(true, p.erfuellt)
        assertEquals(4_465_000_000.0, p.jetzt!!, 0.0)
        assertEquals(4_465_000_000.0, p.vorher!!, 0.0)
    }

    @Test
    fun mehrAusgegebeneAktienKostenDenPunkt() {
        val p = punkt(jahr("2024-12-31", 90.0, 100.0), jahr("2025-12-31", 90.0, 104.0))
        assertEquals(false, p.erfuellt)
    }

    @Test
    fun ohneAngabeZaehlenDieAktienImUmlauf() {
        assertEquals(false, punkt(jahr("2024-12-31", 100.0, null), jahr("2025-12-31", 110.0, null)).erfuellt)
        assertEquals(true, punkt(jahr("2024-12-31", 100.0, null), jahr("2025-12-31", 100.0, null)).erfuellt)
        // Angabe nur in einem Jahr: nicht mischen, Umlauf gegen Umlauf
        val p = punkt(jahr("2024-12-31", 100.0, null), jahr("2025-12-31", 99.0, 120.0))
        assertEquals(true, p.erfuellt)
        assertEquals(99.0, p.jetzt!!, 0.0)
        assertNull(punkt(jahr("2024-12-31", null, null), jahr("2025-12-31", 99.0, 120.0)).erfuellt)
    }

    @Test
    fun kleinerAnstiegZaehltAlsUnveraendert() {
        // 4.441,3 Mio. auf 4.443,5 Mio. sind 0,05 %
        assertEquals(true, punkt(jahr("2024-12-31", 4_441_340_468.0, null), jahr("2025-12-31", 4_443_479_341.0, null)).erfuellt)
        assertEquals(true, punkt(jahr("2024-12-31", 90.0, 1000.0), jahr("2025-12-31", 90.0, 1004.0)).erfuellt)
        assertEquals(false, punkt(jahr("2024-12-31", 90.0, 1000.0), jahr("2025-12-31", 90.0, 1006.0)).erfuellt)
    }

    @Test
    fun antwortLiestBeideAktienzahlen() {
        fun reihe(typ: String, a: Double, b: Double) = "{\"meta\":{\"type\":[\"$typ\"]},\"$typ\":[" +
            "{\"asOfDate\":\"2024-12-31\",\"reportedValue\":{\"raw\":$a}},{\"asOfDate\":\"2025-12-31\",\"reportedValue\":{\"raw\":$b}}]}"
        val text = "{\"timeseries\":{\"result\":[" + reihe("annualOrdinarySharesNumber", 4441340468.0, 4443479341.0) + "," +
            reihe("annualShareIssued", 4465000000.0, 4465000000.0) + "]}}"
        val jahre = Qualitaet.lesen(text)
        assertEquals(4_443_479_341.0, jahre.last().aktien!!, 0.0)
        assertEquals(4_465_000_000.0, jahre.last().aktienAusgegeben!!, 0.0)
    }
}
