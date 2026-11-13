package de.troetenrepublik.app.market

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Auftragsbestand aus selbst formulierten Ausschnitten der companyconcept-Antwort (frei erfundene Zahlen).
class FirmaAuftragsbestandTest {
    private fun punkt(ende: String, wert: Long, form: String = "10-Q", filed: String = "2026-01-01", start: String? = null) =
        """{${if (start != null) "\"start\":\"$start\"," else ""}"end":"$ende","val":$wert,"accn":"0000000000-26-000001","form":"$form","filed":"$filed"}"""

    private fun antwort(vararg p: String, einheit: String = "USD") = """{"cik":1,"taxonomy":"us-gaap","tag":"X","units":{"$einheit":[${p.joinToString(",")}]}}"""

    private val bestand = antwort(
        punkt("2025-03-31", 100_000_000_000), punkt("2025-06-30", 110_000_000_000), punkt("2025-09-30", 120_000_000_000, "10-K"),
        punkt("2025-12-31", 130_000_000_000), punkt("2026-03-31", 150_000_000_000), punkt("2026-06-30", 160_000_000_000, "10-Q", "2026-08-01"),
        punkt("2026-06-30", 161_000_000_000, "10-Q/A", "2026-09-01"),
    )
    private val umsatz = antwort(
        punkt("2025-09-30", 90_000_000_000, "10-K", "2025-11-01", "2024-10-01"),
        punkt("2025-12-31", 20_000_000_000, "10-Q", "2026-02-01", "2025-10-01"),
    )

    @Test fun adresseMitZehnStellen() {
        assertEquals("https://data.sec.gov/api/xbrl/companyconcept/CIK0000789019/us-gaap/RevenueRemainingPerformanceObligation.json", Auftragsbestand.adresse("789019", Auftragsbestand.BEGRIFF))
    }

    @Test fun nurDollar() {
        assertTrue(Auftragsbestand.reihe(antwort(punkt("2025-03-31", 5), einheit = "EUR")).isEmpty())
        assertTrue(Auftragsbestand.reihe("kein json").isEmpty())
    }

    @Test fun juengsteMeldungJeStichtagZaehlt() {
        val s = Auftragsbestand.staende(Auftragsbestand.reihe(bestand))
        assertEquals(6, s.size)
        assertEquals(161_000_000_000.0, s.last().wert, 0.0)
    }

    @Test fun auswertung() {
        val e = Auftragsbestand.auswerten(Auftragsbestand.reihe(bestand), Auftragsbestand.reihe(umsatz), LocalDate.parse("2026-10-01"))
        assertNotNull(e)
        e!!
        assertEquals("2026-06-30", e.aktuell.ende)
        // Vorjahr: 30.06.2025
        assertEquals(110_000_000_000.0, e.vorjahr!!.wert, 0.0)
        assertEquals(161.0 / 110.0 * 100 - 100, e.aenderung!!, 1e-9)
        // Jahresumsatz: nur das volle Geschaeftsjahr bis 30.09.2025, nicht das Quartal
        assertEquals(90_000_000_000.0, e.umsatz!!, 0.0)
        assertEquals(2025, e.umsatzJahr)
        assertEquals(161.0 / 90.0, e.faktor!!, 1e-9)
        assertEquals(6, e.verlauf.size)
    }

    @Test fun ohneUmsatzKeinFaktor() {
        val e = Auftragsbestand.auswerten(Auftragsbestand.reihe(bestand), emptyList(), LocalDate.parse("2026-10-01"))!!
        assertNull(e.faktor)
        assertNull(e.umsatz)
    }

    @Test fun veralteteOderLeereAngabeEntfaellt() {
        assertNull(Auftragsbestand.auswerten(Auftragsbestand.reihe(bestand), emptyList(), LocalDate.parse("2028-06-01")))
        assertNull(Auftragsbestand.auswerten(emptyList(), emptyList(), LocalDate.parse("2026-10-01")))
        assertNull(Auftragsbestand.auswerten(Auftragsbestand.reihe(antwort(punkt("2026-06-30", 0))), emptyList(), LocalDate.parse("2026-10-01")))
    }

    @Test fun keinVorjahrOhneStandRundUmEinJahr() {
        val b = antwort(punkt("2026-03-31", 10), punkt("2026-06-30", 12))
        val e = Auftragsbestand.auswerten(Auftragsbestand.reihe(b), emptyList(), LocalDate.parse("2026-10-01"))!!
        assertNull(e.vorjahr)
        assertNull(e.aenderung)
    }
}
