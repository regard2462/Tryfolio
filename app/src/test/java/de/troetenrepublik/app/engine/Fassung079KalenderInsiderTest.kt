package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.TradeCalendar.Aktie
import de.troetenrepublik.app.market.Bafin
import de.troetenrepublik.app.market.EuInsider
import de.troetenrepublik.app.market.SecInsider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

// Handelskalender: Insider-Geschaefte der gehaltenen Aktien als vierte Markierung.
class Fassung079KalenderInsiderTest {

    private fun sec(k: String, d: String, kauf: Boolean, wert: Double = 100_000.0, a: String = "$k$d$kauf") =
        SecInsider.Geschaeft(a = a, k = k, e = "Firma $k", p = "Person A", kauf = kauf, d = d, wert = wert)

    private fun eu(isin: String, d: String, art: String) =
        EuInsider.Geschaeft(e = "Firma", isin = isin, p = "Person B", art = art, d = d, wert = 5_000.0, w = "SEK")

    private fun bafin(isin: String, d: String, art: String) =
        Bafin.Geschaeft(id = "$isin$d$art", isin = isin, e = "Firma", p = "Person C", art = art, d = d, vol = 2_000.0, w = "EUR")

    private val us = Aktie("AAPL", "Apple", "US0378331005", us = true)
    private val de = Aktie("SAP.DE", "SAP", "DE0007164600", us = false)

    @Test
    fun onlyHeldStocksAppear() {
        val liste = TradeCalendar.insiderFuerDepot(
            listOf(us), listOf(sec("AAPL", "2026-11-02", true), sec("MSFT", "2026-11-02", true)), emptyMap(), emptyList(),
        )
        assertEquals(listOf("AAPL"), liste.map { it.symbol })
    }

    @Test
    fun noInsiderWithoutHoldings() {
        assertTrue(TradeCalendar.insiderFuerDepot(emptyList(), listOf(sec("AAPL", "2026-11-02", true)), emptyMap(), emptyList()).isEmpty())
    }

    @Test
    fun buyAndSaleOnSameDayGiveBothDirections() {
        val liste = TradeCalendar.insiderFuerDepot(
            listOf(us), listOf(sec("AAPL", "2026-11-02", true), sec("AAPL", "2026-11-02", false), sec("AAPL", "2026-11-03", false)), emptyMap(), emptyList(),
        )
        val tage = TradeCalendar.tage(emptyList(), emptyList(), ZoneId.of("Pacific/Auckland"), liste)
        val montag = tage.getValue(LocalDate.of(2026, 11, 2))
        assertTrue(montag.insiderKauf)
        assertTrue(montag.insiderVerkauf)
        assertEquals(2, montag.insider.size)
        val dienstag = tage.getValue(LocalDate.of(2026, 11, 3))
        assertFalse(dienstag.insiderKauf)
        assertTrue(dienstag.insiderVerkauf)
    }

    @Test
    fun dayIsTheCalendarDateOfTheFilingInEveryZone() {
        val liste = TradeCalendar.insiderFuerDepot(listOf(us), listOf(sec("AAPL", "2026-11-02", true)), emptyMap(), emptyList())
        for (zone in listOf("UTC", "Pacific/Honolulu", "Asia/Tokyo", "Europe/Berlin")) {
            val tage = TradeCalendar.tage(emptyList(), emptyList(), ZoneId.of(zone), liste)
            assertEquals(setOf(LocalDate.of(2026, 11, 2)), tage.keys)
        }
    }

    @Test
    fun onlyRealBuysAndSalesFromRegisters() {
        val liste = TradeCalendar.insiderFuerDepot(
            listOf(de),
            emptyList(),
            emptyMap(),
            listOf(bafin("DE0007164600", "2026-11-02", "Kauf"), bafin("DE0007164600", "2026-11-03", "Sonstiges"), bafin("DE0000000000", "2026-11-04", "Kauf")),
        )
        assertEquals(listOf(LocalDate.of(2026, 11, 2)), liste.map { it.datum })
        assertTrue(liste.single().kauf)
        assertEquals("DE", liste.single().quelle)
    }

    @Test
    fun registerOfOtherCountriesMatchByIsinAndSkipOthers() {
        val se = Aktie("VOLV-B.ST", "Volvo", "SE0000115446", us = false)
        val liste = TradeCalendar.insiderFuerDepot(
            listOf(se), emptyList(),
            mapOf(
                "SE" to listOf(eu("SE0000115446", "2026-10-30", "K"), eu("SE0000115446", "2026-10-31", "S"), eu("SE0000108656", "2026-10-30", "K")),
                "FR" to emptyList(),
            ),
            emptyList(),
        )
        assertEquals(listOf(LocalDate.of(2026, 10, 30)), liste.map { it.datum })
        assertEquals("SEK", liste.single().waehrung)
    }

    @Test
    fun usSecFilingsOnlyForUsStocks() {
        val kein = Aktie("AAPL", "Apple", "", us = false)
        assertTrue(TradeCalendar.insiderFuerDepot(listOf(kein), listOf(sec("AAPL", "2026-11-02", true)), emptyMap(), emptyList()).isEmpty())
    }

    @Test
    fun insiderOnlyDaysDoNotChangeMonthTotals() {
        val liste = TradeCalendar.insiderFuerDepot(listOf(us), listOf(sec("AAPL", "2026-11-02", true)), emptyMap(), emptyList())
        val tage = TradeCalendar.tage(emptyList(), emptyList(), ZoneId.of("UTC"), liste)
        val monat = TradeCalendar.monat(tage, java.time.YearMonth.of(2026, 11))
        assertEquals(0, monat.trades)
        assertEquals(0.0, monat.realisiert, 0.0)
        assertFalse(tage.getValue(LocalDate.of(2026, 11, 2)).hatTrades)
    }

    @Test
    fun invalidDatesAreSkipped() {
        val liste = TradeCalendar.insiderFuerDepot(listOf(us), listOf(sec("AAPL", "kaputt", true)), emptyMap(), emptyList())
        assertTrue(liste.isEmpty())
    }
}
