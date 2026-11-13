package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

// Handelskalender: geschlossene Positionen und Dividenden je Tag, Summen des Monats.
class TradeCalendarTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private fun ts(y: Int, m: Int, d: Int, h: Int = 12) = LocalDateTime.of(y, m, d, h, 0).atZone(zone).toInstant().toEpochMilli()

    private fun verkauf(id: String, t: Long, qty: Double, price: Double, realized: Double, fee: Double = 1.0, tax: Double = 0.0) =
        Transaction(id, t, TxType.SELL, "Wert $id", qty = qty, price = price, realized = realized, fee = fee, tax = tax, symbol = id, amount = qty * price - tax)

    @Test
    fun dayResultIsRealizedOverCostOfClosedShares() {
        // 4 Stueck zu 240 verkauft, eingekauft zu 231: 36 EUR Gewinn auf 924 EUR Einsatz
        val sap = verkauf("SAP", ts(2026, 9, 13), 4.0, 240.0, 36.0, tax = 9.5)
        // Am selben Tag 10 Stueck zu 50 verkauft, eingekauft zu 55: 50 EUR Verlust auf 550 EUR Einsatz
        val b = verkauf("B", ts(2026, 9, 13, 16), 10.0, 50.0, -50.0)
        val tage = TradeCalendar.tage(listOf(sap, b), emptyList(), zone)
        val tag = tage.getValue(LocalDate.of(2026, 9, 13))
        assertEquals(2, tag.trades.size)
        assertEquals(-14.0, tag.realisiert, 1e-9)
        assertEquals(924.0 + 550.0, tag.einsatz, 1e-9)
        assertEquals(-14.0 / 1474.0 * 100.0, tag.prozent, 1e-9)
        assertEquals(36.0 / 924.0 * 100.0, tag.trades.first().prozent, 1e-9)
        assertEquals(2.0, tag.gebuehren, 1e-9)
        assertEquals(9.5, tag.steuern, 1e-9)
    }

    @Test
    fun knockoutCountsOnKnockoutDayNotOnRefund() {
        // Knock-out am Freitag, Rueckzahlung zwei Tage spaeter gebucht
        val ko = Transaction(
            "ko", ts(2026, 10, 4), TxType.KNOCKOUT, "Long 220", qty = 100.0, price = 0.001, realized = -83.86,
            closedAt = ts(2026, 10, 2, 15), symbol = "AAPL",
        )
        val tage = TradeCalendar.tage(listOf(ko), emptyList(), zone)
        assertTrue(tage.containsKey(LocalDate.of(2026, 10, 2)))
        assertFalse(tage.containsKey(LocalDate.of(2026, 10, 4)))
        // Einsatz = Erloes minus Ergebnis = 0,10 + 83,86
        assertEquals(83.96, tage.getValue(LocalDate.of(2026, 10, 2)).einsatz, 1e-9)
        assertEquals(-99.88, tage.getValue(LocalDate.of(2026, 10, 2)).prozent, 0.01)
    }

    @Test
    fun buysAndOtherBookingsDoNotCount() {
        val kauf = Transaction("k", ts(2026, 9, 1), TxType.BUY, "Kauf", qty = 1.0, price = 100.0)
        val einzahlung = Transaction("e", ts(2026, 9, 1), TxType.DEPOSIT, "Einzahlung", amount = 1000.0)
        assertTrue(TradeCalendar.tage(listOf(kauf, einzahlung), emptyList(), zone).isEmpty())
    }

    @Test
    fun dividendsReceivedAndExpectedStandOnTheirDay() {
        // Gebuchte Dividende: Betrag nach Steuern, Steuer daneben
        val div = Transaction("d", ts(2026, 9, 17), TxType.DIVIDEND, "NVIDIA", amount = 1.29, tax = 0.46, qty = 8.0, price = 0.2187, symbol = "NVDA")
        val erwartet = TradeCalendar.Dividende("NVDA", "NVIDIA", "NVDA", ts(2026, 11, 13), 2.77, 0.0, 8.0, 0.0, erwartet = true)
        val tage = TradeCalendar.tage(listOf(div), listOf(erwartet), zone)
        val sep = tage.getValue(LocalDate.of(2026, 9, 17))
        assertEquals(1.29, sep.dividendenErhalten, 1e-9)
        assertEquals(1.75, sep.dividenden.single().brutto, 1e-9)
        assertFalse(sep.hatTrades)
        val nov = TradeCalendar.monat(tage, YearMonth.of(2026, 11))
        assertEquals(2.77, nov.dividendenErwartet, 1e-9)
        assertEquals(0.0, nov.dividendenErhalten, 1e-9)
    }

    @Test
    fun monthSumsCountWinsAndLosses() {
        val a = verkauf("A", ts(2026, 10, 1), 1.0, 110.0, 10.0)
        val b = verkauf("B", ts(2026, 10, 8), 1.0, 90.0, -10.0)
        val c = verkauf("C", ts(2026, 10, 8, 18), 1.0, 105.0, 5.0)
        val anderer = verkauf("D", ts(2026, 11, 2), 1.0, 100.0, 20.0)
        val tage = TradeCalendar.tage(listOf(a, b, c, anderer), emptyList(), zone)
        val m = TradeCalendar.monat(tage, YearMonth.of(2026, 10))
        assertEquals(3, m.trades)
        assertEquals(2, m.gewinne)
        assertEquals(1, m.verluste)
        assertEquals(5.0, m.realisiert, 1e-9)
    }

    @Test
    fun colourStrengthGrowsWithResultButStaysVisible() {
        assertEquals(0.25f, TradeCalendar.staerke(0.0, 100.0), 1e-6f)
        assertEquals(1f, TradeCalendar.staerke(-100.0, 100.0), 1e-6f)
        val mitte = TradeCalendar.staerke(25.0, 100.0)
        assertTrue(mitte > 0.25f && mitte < 1f)
        assertEquals(0.25f, TradeCalendar.staerke(5.0, 0.0), 1e-6f)
    }

    @Test
    fun monthRangeReachesFromFirstBookingToLastExpectedDividend() {
        val erste = verkauf("A", ts(2026, 3, 4), 1.0, 1.0, 0.0)
        val erwartet = TradeCalendar.Dividende("X", "X", "X", ts(2027, 5, 20), 1.0, 0.0, 1.0, 0.0, erwartet = true)
        val r = TradeCalendar.spanne(listOf(erste), listOf(erwartet), YearMonth.of(2026, 10), zone)
        assertEquals(YearMonth.of(2026, 3), r.start)
        assertEquals(YearMonth.of(2027, 5), r.endInclusive)
        val leer = TradeCalendar.spanne(emptyList(), emptyList(), YearMonth.of(2026, 10), zone)
        assertEquals(YearMonth.of(2026, 10), leer.start)
        assertEquals(YearMonth.of(2026, 10), leer.endInclusive)
    }
}
