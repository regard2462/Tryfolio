package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.data.Position
import de.troetenrepublik.app.data.Transaction
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.engine.Tagesrueckblick.Bestand
import de.troetenrepublik.app.market.Rss
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

// Rueckblick fuer vergangene Tage, Bestand aus den Buchungen und der Handelskalender nach einem Import.
class RueckblickVergangenheitTest {

    private val zone = ZoneOffset.UTC
    private val start = LocalDate.of(2026, 10, 12).toEpochDay()   // Montag
    private fun um(tag: Long, stunde: Int = 12) = tag * 86_400_000L + stunde * 3_600_000L
    private fun kerze(tag: Long, close: Double) = Candle(um(tag, 14), close, close, close, close)

    private fun buchung(id: String, tag: Long, typ: TxType, qty: Double, symbol: String = "AAA") =
        Transaction(id, um(tag), typ, symbol, qty = qty, price = 100.0, symbol = symbol, key = symbol)

    @Test
    fun bestandAnEinemVergangenenTagAusDenBuchungen() {
        val konto = Account(
            positions = listOf(Position("AAA", "Alpha", "AAA", 8.0, 100.0, um(start))),
            transactions = listOf(
                buchung("1", start, TxType.BUY, 10.0),
                buchung("2", start + 2, TxType.BUY, 5.0),
                buchung("3", start + 4, TxType.SELL, 7.0),
            ),
        )
        fun stueck(tag: Long) = Tagesrueckblick.bestandAm(konto, tag, zone).firstOrNull()?.stueck
        assertNull(stueck(start - 1))
        assertEquals(10.0, stueck(start)!!, 1e-9)
        assertEquals(10.0, stueck(start + 1)!!, 1e-9)
        assertEquals(15.0, stueck(start + 2)!!, 1e-9)
        assertEquals(15.0, stueck(start + 3)!!, 1e-9)
        assertEquals(8.0, stueck(start + 4)!!, 1e-9)
        assertEquals(8.0, stueck(start + 9)!!, 1e-9)
    }

    @Test
    fun werteOhneKursZaehlenNichtImBestand() {
        val konto = Account(
            positions = listOf(Position("AAA", "Alpha", "AAA", 1.0, 100.0, um(start))),
            transactions = listOf(
                buchung("1", start, TxType.BUY, 1.0),
                buchung("2", start, TxType.SELL, 3.0, symbol = "~DE000XX00001").copy(key = "DE000XX00001"),
            ),
        )
        assertEquals(listOf("AAA"), Tagesrueckblick.bestandAm(konto, start, zone).map { it.symbol })
    }

    @Test
    fun rueckblickFuerEinenVergangenenTagMitFestenKursen() {
        val tag = start + 3
        val kurse = mapOf("AAA" to listOf(kerze(tag - 2, 98.0), kerze(tag - 1, 100.0), kerze(tag, 110.0), kerze(tag + 1, 90.0)))
        val dax = Tagesrueckblick.MAERKTE.first { it.first == "DAX" }.third
        val sp = Tagesrueckblick.MAERKTE.first { it.first == "SP500" }.third
        val sektor = Branchentrends.BRANCHEN.first().symbol
        val markt = mapOf(
            dax to listOf(kerze(tag - 1, 200.0), kerze(tag, 202.0)),
            // Feiertag: kein Schluss am Tag selbst
            sp to listOf(kerze(tag - 1, 5000.0), kerze(tag + 1, 5100.0)),
            sektor to listOf(kerze(tag - 1, 50.0), kerze(tag, 49.0)),
        )
        val nachrichten = listOf(Rss.Eintrag("Alpha liefert Rekordzahlen", "https://x.example/a", um(tag, 15), quelle = "Feed"))
        val r = Tagesrueckblick.vergangenheit(tag, um(tag + 5), listOf(Bestand("AAA", "Alpha", 10.0)), kurse, markt, nachrichten, zone)!!
        assertTrue(r.nachtraeglich)
        assertEquals(tag, r.tag)
        assertEquals(1100.0, r.depotWert, 1e-9)
        assertEquals(100.0, r.depotAenderung, 1e-9)
        assertEquals(10.0, r.depotProzent!!, 1e-9)
        assertEquals(listOf("DAX"), r.markt.map { it.schluessel })
        assertEquals(1.0, r.markt.single().aenderung, 1e-9)
        assertEquals(listOf(sektor), r.branchen.map { it.schluessel })
        assertEquals("AAA", r.bewegungen.single().symbol)
        assertEquals(100.0, r.bewegungen.single().betrag, 1e-9)
    }

    @Test
    fun ohneSchlussAmTagBleibtDerWertOhneAenderung() {
        val tag = start + 1
        val kurse = mapOf("AAA" to listOf(kerze(tag - 1, 100.0), kerze(tag + 1, 120.0)))
        val r = Tagesrueckblick.vergangenheit(tag, um(tag + 5), listOf(Bestand("AAA", "Alpha", 2.0)), kurse, emptyMap(), zone = zone)!!
        assertEquals(200.0, r.depotWert, 1e-9)
        assertEquals(0.0, r.depotAenderung, 1e-9)
    }

    @Test
    fun ohneKurseKeinRueckblick() {
        assertNull(Tagesrueckblick.vergangenheit(start, um(start + 5), listOf(Bestand("AAA", "Alpha", 2.0)), emptyMap(), emptyMap(), zone = zone))
        // Letzter Kurs viel zu alt: nicht notiert
        val alt = mapOf("AAA" to listOf(kerze(start - 40, 100.0)))
        assertNull(Tagesrueckblick.vergangenheit(start, um(start + 5), listOf(Bestand("AAA", "Alpha", 2.0)), alt, emptyMap(), zone = zone))
    }

    @Test
    fun nachtraeglicheRueckblickeVerdraengenKeineNeuerenUndBleibenRueckwaertskompatibel() {
        val neu = Tagesrueckblick.Rueckblick(tag = start + 500, erstellt = 1L)
        val alt = Tagesrueckblick.Rueckblick(tag = start, erstellt = 2L, nachtraeglich = true)
        val a = Tagesrueckblick.eintragen(Tagesrueckblick.Archiv(listOf(neu)), alt)
        assertEquals(listOf(neu.tag), a.tage.map { it.tag })
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val ohneFeld = json.decodeFromString(Tagesrueckblick.Rueckblick.serializer(), "{\"tag\":5,\"erstellt\":1}")
        assertFalse(ohneFeld.nachtraeglich)
    }

    @Test
    fun zeitraumReichtBisZumTag() {
        val jetzt = um(start + 20)
        assertEquals("1mo", Tagesrueckblick.abrufZeitraum(start, jetzt))
        assertEquals("1y", Tagesrueckblick.abrufZeitraum(start - 200, jetzt))
        assertNull(Tagesrueckblick.abrufZeitraum(start - 4000, jetzt))
    }

    // Import mit Buchungen aus der Vergangenheit

    private val kopf = "\"datetime\",\"date\",\"account_type\",\"category\",\"type\",\"asset_class\",\"name\",\"symbol\",\"shares\",\"price\",\"amount\",\"fee\",\"tax\",\"currency\",\"original_amount\",\"original_currency\",\"fx_rate\",\"description\",\"transaction_id\",\"counterparty_name\",\"counterparty_iban\",\"payment_reference\",\"mcc_code\""
    private fun z(t: String, kat: String, typ: String, name: String, sym: String, stueck: String, preis: String, betrag: String, id: String) =
        "\"$t\",\"${t.take(10)}\",\"DEFAULT\",\"$kat\",\"$typ\",\"STOCK\",\"$name\",\"$sym\",\"$stueck\",\"$preis\",\"$betrag\",\"\",\"\",\"EUR\",\"\",\"\",\"\",\"\",\"$id\",\"\",\"\",\"\",\"\""

    @Test
    fun kalenderNachImportRechnetAbDerErstenBuchung() {
        val datei = listOf(
            kopf,
            z("2025-01-02T09:00:00Z", "CASH", "TRANSFER_INSTANT_INBOUND", "", "", "", "", "1000.00", "a"),
            z("2025-01-03T10:00:00Z", "TRADING", "BUY", "Beispiel AG", "DE0000000001", "10.0", "50.0", "-500.00", "b"),
            z("2025-01-08T10:00:00Z", "TRADING", "SELL", "Beispiel AG", "DE0000000001", "-4.0", "60.0", "240.00", "c"),
            z("2025-01-20T10:00:00Z", "TRADING", "SELL", "Beispiel AG", "DE0000000001", "-2.0", "45.0", "90.00", "d"),
        ).joinToString("\n")
        val jetzt = LocalDate.of(2026, 11, 6).toEpochDay() * 86_400_000L
        val e = TradeRepublicImport.konto(TradeRepublicImport.lesen(datei), mapOf("DE0000000001" to "BSP.DE"), Account(), jetzt)
        val buchungen = e.konto.transactions
        val tage = TradeCalendar.tage(buchungen.filter { TradeCalendar.schliesst(it) }, emptyList(), zone)

        // Die Tage stehen am Datum der Buchung, nicht am Importtag
        assertEquals(setOf(LocalDate.of(2025, 1, 8), LocalDate.of(2025, 1, 20)), tage.keys)
        assertEquals(40.0, tage.getValue(LocalDate.of(2025, 1, 8)).realisiert, 1e-9)
        assertEquals(20.0, tage.getValue(LocalDate.of(2025, 1, 8)).prozent, 1e-9)
        assertEquals(-10.0, tage.getValue(LocalDate.of(2025, 1, 20)).realisiert, 1e-9)
        assertEquals(YearMonth.of(2025, 1), TradeCalendar.spanne(buchungen, emptyList(), YearMonth.of(2026, 11), zone).start)

        // Rueckblicke lassen sich ab dem ersten Buchungstag erstellen, nur an Werktagen vor heute
        val erster = TradeCalendar.ersterTag(buchungen, zone)
        assertEquals(LocalDate.of(2025, 1, 2), erster)
        val heute = LocalDate.of(2026, 11, 6)
        assertFalse(TradeCalendar.rueckblickMoeglich(LocalDate.of(2025, 1, 1), erster, heute))
        assertTrue(TradeCalendar.rueckblickMoeglich(LocalDate.of(2025, 1, 2), erster, heute))
        assertFalse(TradeCalendar.rueckblickMoeglich(LocalDate.of(2025, 1, 4), erster, heute))
        assertTrue(TradeCalendar.rueckblickMoeglich(LocalDate.of(2025, 1, 6), erster, heute))
        assertFalse(TradeCalendar.rueckblickMoeglich(heute, erster, heute))
        assertFalse(TradeCalendar.rueckblickMoeglich(heute.minusDays(1), null, heute))

        // Bestand vor, zwischen und nach den Verkaeufen
        assertEquals(10.0, Tagesrueckblick.bestandAm(e.konto, LocalDate.of(2025, 1, 6).toEpochDay(), zone).single().stueck, 1e-9)
        assertEquals(6.0, Tagesrueckblick.bestandAm(e.konto, LocalDate.of(2025, 1, 10).toEpochDay(), zone).single().stueck, 1e-9)
        assertEquals(4.0, Tagesrueckblick.bestandAm(e.konto, LocalDate.of(2025, 1, 21).toEpochDay(), zone).single().stueck, 1e-9)
        assertTrue(Tagesrueckblick.bestandAm(e.konto, LocalDate.of(2025, 1, 2).toEpochDay(), zone).isEmpty())
    }

    // Split 1:10 am 15.03.2024; der Broker hat die 90 neuen Stuecke gebucht, die Kaeufe davor tragen die alte Zahl.
    @Test
    fun bestandVorEinemSplitIstBereinigt() {
        val splitTag = LocalDate.of(2024, 3, 15).toEpochDay()
        val kauf = LocalDate.of(2024, 1, 10).toEpochDay()
        val konto = Account(
            positions = listOf(Position("AAA", "Alpha", "AAA", 100.0, 5.0, um(kauf))),
            transactions = listOf(
                buchung("1", kauf, TxType.BUY, 10.0).copy(price = 50.0, via = "IMPORT_TR"),
                buchung("2", splitTag, TxType.BUY, 90.0).copy(price = 0.0, tag = "CORPORATE", via = "IMPORT_TR"),
                buchung("3", splitTag + 20, TxType.SELL, 20.0).copy(via = "IMPORT_TR"),
            ).sortedBy { it.ts }.let { it.dropLast(1) + it.last().copy() },
        )
        // Aktueller Bestand 100 wurde fuer den Test so gesetzt, dass er nach dem Verkauf 80 waere
        val heutig = konto.copy(positions = listOf(konto.positions.single().copy(qty = 80.0)))
        val split = mapOf("AAA" to listOf(Splits.Split(splitTag * 86_400_000L, 10.0, 1.0)))
        fun stueck(tag: Long, mit: Boolean) = Tagesrueckblick.bestandAm(heutig, tag, zone, if (mit) split else emptyMap()).firstOrNull()?.stueck
        // Ohne Splitdaten wie bisher: 10 Stueck vor dem Split
        assertEquals(10.0, stueck(kauf + 5, false)!!, 1e-9)
        // Mit Splitdaten 100 Stueck in neuen Einheiten, vor und nach dem Split gleich
        assertEquals(100.0, stueck(kauf + 5, true)!!, 1e-9)
        assertEquals(100.0, stueck(splitTag + 1, true)!!, 1e-9)
        assertEquals(80.0, stueck(splitTag + 25, true)!!, 1e-9)
        assertNull(stueck(kauf - 1, true))
        // Eine eigene SPLIT-Buchung am selben Tag zaehlt nur einmal
        val gebucht = heutig.copy(transactions = heutig.transactions + Transaction("s", splitTag * 86_400_000L, TxType.SPLIT, "Alpha", symbol = "AAA", key = "AAA", extra = 10.0))
        assertEquals(100.0, Tagesrueckblick.bestandAm(gebucht, kauf + 5, zone, split).single().stueck, 1e-9)
    }
}
