package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Derivative
import de.troetenrepublik.app.data.DerivativeKind
import de.troetenrepublik.app.data.Position
import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.engine.TradeCalendar.Art
import de.troetenrepublik.app.i18n.KalenderTexte
import de.troetenrepublik.app.market.NasdaqKalender
import de.troetenrepublik.app.market.Quartalszahlen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

// Handelskalender: weitere Markierungen (Zahlen, Ex-Tag, Kongress, Analysten, Verfall, Kaeufe, Makro, Feiertage),
// Auswahl der drei Markierungen und gemerkte Auswahl.
class Fassung079KalenderMehrTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val tag = LocalDate.of(2026, 11, 12)
    private fun ts(y: Int, m: Int, d: Int, h: Int = 12) = LocalDateTime.of(y, m, d, h, 0).atZone(zone).toInstant().toEpochMilli()
    private fun ms(d: LocalDate) = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun buy(id: String, t: Long, via: String = "", tag: String = "BUY") =
        Transaction(id, t, TxType.BUY, "Wert", qty = 2.0, price = 10.0, symbol = id, amount = -20.0, via = via, tag = tag)

    @Test
    fun hiddenArtsAreStoredAndRead() {
        var text = ""
        text = Art.schalten(text, Art.KONGRESS, an = false)
        text = Art.schalten(text, Art.MAKRO, an = false)
        assertEquals(setOf(Art.KONGRESS, Art.MAKRO), Art.lesen(text))
        text = Art.schalten(text, Art.KONGRESS, an = true)
        assertEquals(setOf(Art.MAKRO), Art.lesen(text))
        assertEquals(setOf(Art.MAKRO), Art.lesen("$text,unbekannt,"))
        assertTrue(Art.lesen("").isEmpty())
    }

    @Test
    fun legendHasAnEvenNumberOfEntries() {
        assertEquals(0, Art.entries.size % 2)
    }

    @Test
    fun tileShowsAtMostThreeMarksInPriorityOrder() {
        val d = TradeCalendar.tage(
            emptyList(), emptyList(), zone,
            insider = listOf(TradeCalendar.Insider("A", "A", tag, true, 1.0, "USD", "P", "", "S", "US")),
            zusatz = TradeCalendar.Zusatz(
                zahlen = listOf(TradeCalendar.Zahlen("A", "A", tag, false)),
                kongress = listOf(TradeCalendar.Kongress("A", "A", tag, true, 1.0, "P", true)),
                kaeufe = listOf(buy("A", ts(2026, 11, 12))),
                exDiv = listOf(TradeCalendar.ExDiv("A", "A", tag, false)),
            ),
        ).getValue(tag)
        assertEquals(listOf(Art.RUECKBLICK, Art.ZAHLEN, Art.INSIDER), TradeCalendar.marken(d, rueckblick = true, aus = emptySet()))
        assertEquals(listOf(Art.ZAHLEN, Art.INSIDER, Art.KONGRESS), TradeCalendar.marken(d, rueckblick = false, aus = emptySet()))
        // Ausgeblendete Arten rutschen nicht nach vorn, die naechste wichtige Art rueckt auf
        assertEquals(listOf(Art.INSIDER, Art.KONGRESS, Art.EXDIV), TradeCalendar.marken(d, rueckblick = false, aus = setOf(Art.ZAHLEN)))
        assertEquals(listOf(Art.RUECKBLICK), TradeCalendar.marken(null, rueckblick = true, aus = emptySet()))
        assertTrue(TradeCalendar.marken(null, rueckblick = false, aus = emptySet()).isEmpty())
    }

    @Test
    fun dividendsKeepTheirOrderBetweenExDateAndAnalysts() {
        val erhalten = TradeCalendar.Dividende("k", "n", "s", ts(2026, 11, 12), 1.0, 0.0, 1.0, 1.0, erwartet = false)
        val erwartet = erhalten.copy(erwartet = true)
        val t = TradeCalendar.tage(emptyList(), listOf(erwartet), zone).getValue(tag)
        assertEquals(listOf(Art.DIV_ERWARTET), t.arten(false))
        val t2 = TradeCalendar.tage(listOf(Transaction("d", erhalten.ts, TxType.DIVIDEND, "n", symbol = "s", key = "k", amount = 1.0)), emptyList(), zone).getValue(tag)
        assertEquals(listOf(Art.DIV_ERHALTEN), t2.arten(false))
    }

    @Test
    fun purchasesAndSavingsPlanExecutionsCountButImportsAndSalesDoNot() {
        assertTrue(TradeCalendar.istKauf(buy("A", 1L)))
        assertTrue(TradeCalendar.istKauf(buy("A", 1L, via = "PLAN")))
        assertFalse(TradeCalendar.istKauf(buy("A", 1L, via = "IMPORT")))
        assertFalse(TradeCalendar.istKauf(buy("A", 1L, tag = "TRANSFER_IN")))
        assertFalse(TradeCalendar.istKauf(Transaction("s", 1L, TxType.SELL, "x", qty = 1.0, price = 1.0)))
    }

    @Test
    fun purchaseDayFollowsTheZoneOfTheDevice() {
        val spaet = ts(2026, 11, 12, 23)
        val t = TradeCalendar.tage(emptyList(), emptyList(), zone, zusatz = TradeCalendar.Zusatz(kaeufe = listOf(buy("A", spaet)))).keys
        assertEquals(setOf(tag), t)
    }

    @Test
    fun earningsResultComesBeforeDateAndOnlyHeldStocks() {
        val gehalten = mapOf("AAPL" to "Apple")
        val u = mapOf(
            "AAPL" to listOf(NasdaqKalender.Ueberraschung(tag.toEpochDay(), "Sep", 1.64, 1.60, 2.5)),
            "MSFT" to listOf(NasdaqKalender.Ueberraschung(tag.toEpochDay(), "Sep", 3.0, 2.9, 3.0)),
        )
        val termine = listOf(
            Quartalszahlen.Termin("AAPL", "Apple", tag.toEpochDay(), Quartalszahlen.Zeit.OFFEN, epsErwartet = 1.6),
            Quartalszahlen.Termin("AAPL", "Apple", tag.plusDays(90).toEpochDay(), Quartalszahlen.Zeit.OFFEN, epsErwartet = 1.8, geschaetzt = true),
            Quartalszahlen.Termin("NVDA", "Nvidia", tag.toEpochDay(), Quartalszahlen.Zeit.OFFEN),
        )
        val z = TradeCalendar.zahlen(termine, u, gehalten)
        assertEquals(2, z.size)
        assertEquals(1.64, z[0].eps!!, 0.0)
        assertEquals(tag, z[0].datum)
        assertNull(z[1].eps)
        assertTrue(z[1].geschaetzt)
        assertEquals(1.8, z[1].epsErwartet!!, 0.0)
    }

    @Test
    fun exDateIsTheLastOneBeforeEachPaymentWithinSixtyDays() {
        val zahltag = LocalDate.of(2026, 11, 12)
        val erhalten = TradeCalendar.Dividende("k", "Name", "SYM", ms(zahltag), 1.0, 0.0, 1.0, 1.0, erwartet = false)
        val historie = mapOf("k" to listOf(ms(LocalDate.of(2026, 8, 12)), ms(LocalDate.of(2026, 11, 9)), ms(LocalDate.of(2026, 11, 20))))
        val ex = TradeCalendar.exTage(listOf(erhalten), historie, listOf(Triple("SYM", "Name", ms(LocalDate.of(2027, 2, 10)))))
        assertEquals(listOf(LocalDate.of(2026, 11, 9), LocalDate.of(2027, 2, 10)), ex.map { it.datum })
        assertEquals(listOf(false, true), ex.map { it.erwartet })
        // Weiter als 60 Tage vor der Zahlung zaehlt nicht
        val fern = TradeCalendar.exTage(listOf(erhalten), mapOf("k" to listOf(ms(LocalDate.of(2026, 8, 12)))), emptyList())
        assertTrue(fern.isEmpty())
    }

    @Test
    fun congressTradesOnlyForHeldStocksOnTheDayOfTheTrade() {
        val h = listOf(
            de.troetenrepublik.app.engine.KongressAuswertung.Handel("k:a", "Person A", 'H', "AAPL", true, ms(tag), ms(tag.plusDays(20)), 15_000.0),
            de.troetenrepublik.app.engine.KongressAuswertung.Handel("n:b", "Person B", 'S', "MSFT", false, ms(tag), ms(tag), 15_000.0),
        )
        val k = TradeCalendar.kongress(h, mapOf("AAPL" to "Apple"))
        assertEquals(1, k.size)
        assertEquals(tag, k.single().datum)
        assertTrue(k.single().haus)
    }

    @Test
    fun analystMovesKeepOnlyRealUpgradesAndDowngrades() {
        val u = listOf(
            Analystenbilanz.Urteil("H1", ts(2026, 11, 12), "Buy", "Hold", "up"),
            Analystenbilanz.Urteil("H2", ts(2026, 11, 12), "Hold", "Buy", "down"),
            Analystenbilanz.Urteil("H3", ts(2026, 11, 12), "Buy", "Buy", "main"),
            Analystenbilanz.Urteil("H4", ts(2026, 11, 12), "Buy", "Buy", "reit"),
        )
        val s = TradeCalendar.stufungen("AAPL", "Apple", u, zone)
        assertEquals(listOf("H1", "H2"), s.map { it.haus })
        assertEquals(listOf(true, false), s.map { it.hoch })
    }

    @Test
    fun expiryOfOwnAndSoldOptionsButNotKnockedOutOrOpenEnded() {
        fun pos(id: String, expiry: Long, qty: Double, verkauft: Boolean = false, ko: Long = 0L) = Position(
            key = id, name = "Produkt $id", symbol = id, qty = qty, avgPrice = 1.0, openedAt = 0L,
            derivative = Derivative(id = id, underlying = "AAPL", underlyingName = "Apple", kind = DerivativeKind.CALL, expiry = expiry, verkauft = verkauft, knockedOutAt = ko),
        )
        val liste = listOf(
            pos("a", ts(2026, 12, 18), 3.0),
            pos("b", ts(2026, 12, 18), -2.0, verkauft = true),
            pos("c", ts(2026, 12, 18), 1.0, ko = 5L),
            pos("d", 0L, 1.0),
            Position("AAPL", "Apple", "AAPL", 1.0, 1.0, 0L),
        )
        val v = TradeCalendar.verfaelle(liste, zone)
        assertEquals(listOf("a", "b"), v.map { it.kennung })
        assertEquals(LocalDate.of(2026, 12, 18), v[0].datum)
        assertTrue(v[1].verkauft)
        assertEquals(2.0, v[1].stueck, 0.0)
    }

    private fun termin(name: String, land: String) = NasdaqKalender.Wirtschaftstermin(tag.toEpochDay(), 14 * 60, land, name, null, null, null)

    @Test
    fun onlyBigMacroDatesCount() {
        assertEquals(TradeCalendar.MakroArt.FED, TradeCalendar.makroArt(termin("Fed Interest Rate Decision", "United States")))
        assertEquals(TradeCalendar.MakroArt.EZB, TradeCalendar.makroArt(termin("ECB Interest Rate Decision", "Euro Zone")))
        assertEquals(TradeCalendar.MakroArt.CPI, TradeCalendar.makroArt(termin("CPI (YoY)", "United States")))
        assertEquals(TradeCalendar.MakroArt.CPI, TradeCalendar.makroArt(termin("Consumer Price Index", "United States")))
        assertNull(TradeCalendar.makroArt(termin("FOMC Minutes", "United States")))
        assertNull(TradeCalendar.makroArt(termin("PPI (MoM)", "United States")))
        assertNull(TradeCalendar.makroArt(termin("CPI (YoY)", "Germany")))
        assertNull(TradeCalendar.makroArt(termin("Interest Rate Decision", "Japan")))
    }

    @Test
    fun macroDatesAreLaidIntoDaysAndDeduplicated() {
        val m = TradeCalendar.makro(listOf(termin("CPI (YoY)", "United States"), termin("CPI (YoY)", "United States"), termin("PPI", "United States")))
        assertEquals(1, m.size)
        val tage = TradeCalendar.mitMakro(emptyMap(), m)
        assertEquals(listOf(Art.MAKRO), tage.getValue(tag).arten(false))
        assertEquals(emptyMap<LocalDate, TradeCalendar.Tag>(), TradeCalendar.mitMakro(emptyMap(), emptyList()))
    }

    @Test
    fun holidayOnlyWhenEveryExchangeOfTheHoldingsIsClosed() {
        val thanksgiving = LocalDate.of(2026, 11, 26)
        assertTrue(TradeCalendar.feiertag(thanksgiving, setOf("US")))
        assertFalse(TradeCalendar.feiertag(thanksgiving, setOf("US", "DE")))
        assertFalse(TradeCalendar.feiertag(thanksgiving, emptySet()))
        assertFalse(TradeCalendar.feiertag(thanksgiving, setOf("")))
        // Wochenenden sind keine Feiertage
        assertFalse(TradeCalendar.feiertag(LocalDate.of(2026, 11, 28), setOf("US")))
        assertFalse(TradeCalendar.feiertag(LocalDate.of(2026, 11, 25), setOf("US")))
    }

    @Test
    fun textsExistInBothLanguages() {
        assertEquals(KalenderTexte.schluessel(true), KalenderTexte.schluessel(false))
        for (k in KalenderTexte.schluessel(true)) {
            assertTrue(k, KalenderTexte.text(true, k).isNotBlank())
            assertTrue(k, KalenderTexte.text(false, k).isNotBlank())
        }
        assertFalse(KalenderTexte.text(true, "insider").contains("—"))
    }
}
