package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Account
import de.troetenrepublik.app.data.AssetType
import de.troetenrepublik.app.data.Backup
import de.troetenrepublik.app.data.Position
import de.troetenrepublik.app.data.Quote
import de.troetenrepublik.app.data.SavingsPlan
import de.troetenrepublik.app.data.TaxState
import de.troetenrepublik.app.data.TxType
import de.troetenrepublik.app.market.Symbols
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

// Art des Wertpapiers an der Position, Zeitpunkt der Uebernahme an der Buchung, uebersprungene Sparplantermine.
class DepotArtTest {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private fun at(date: String, hour: Int = 12): Long = LocalDate.parse(date).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private val now = at("2026-03-04")

    @Test fun alteDateiOhneNeueFelderBleibtLesbar() {
        val text = """{"app":"troetenrepublik","format":1,"account":{"cash":10.0,
            "positions":[{"key":"A","name":"A","symbol":"A","qty":1.0,"avgPrice":2.0,"openedAt":5}],
            "transactions":[{"id":"t","ts":1,"type":"BUY","title":"A","qty":1.0,"price":2.0,"symbol":"A"}]}}"""
        val p = (Backup.parse(text) as Backup.Outcome.Ok).payload.account
        assertEquals("", p.positions.single().art)
        assertEquals(0L, p.transactions.single().importedAt)
        // und das neue Feld bleibt beim Speichern und Lesen erhalten
        val neu = Account(cash = 1.0, positions = listOf(Position("B", "B", "B", 1.0, 1.0, 1L, art = "ETF")))
        val zurueck = (Backup.parse(Backup.export(neu, de.troetenrepublik.app.data.Settings(), now)) as Backup.Outcome.Ok).payload.account
        assertEquals("ETF", zurueck.positions.single().art)
    }

    @Test fun etfAusDerSucheBekommtTeilfreistellungUndKeinenAktienTopf() {
        Symbols.merkeArt("ZZETFA.XX", AssetType.ETF)
        val kauf = Trading.buy(Account(cash = 10_000.0), "ZZETFA.XX", "MSCI World UCITS", "ZZETFA.XX", 10.0, 100.0, now = at("2025-01-02"), fee = 0.0)
        val pos = kauf.account.positions.single()
        assertEquals("ETF", pos.art)
        assertEquals(Tax.Kind.GENERAL, Trading.taxKind(pos))
        // Gewinn 1.000, davon 30 Prozent frei: 700 steuerpflichtig, 184,6 Euro Steuer (ohne Freibetrag)
        val konto = kauf.account.copy(tax = TaxState(year = 2026))
        val verkauf = Trading.sell(konto, "ZZETFA.XX", 10.0, 200.0, now, fee = 0.0, allowance = 0.0)
        val tax = verkauf.account.transactions.single { it.type == TxType.SELL }.tax
        assertTrue("Steuer $tax", tax in 184.0..185.0)
    }

    @Test fun gespeicherteArtGiltAuchOhneHinweis() {
        // Die Art steht an der Position: auch nach einem Neustart ohne Katalog und Suche
        val pos = Position("ZZETFB.XX", "ETF", "ZZETFB.XX", 1.0, 1.0, 1L, art = "ETF")
        assertEquals(AssetType.ETF, Trading.artOf(pos))
        assertEquals(0.30, Trading.teilfreistellung(pos.symbol, "MSCI World", Trading.artOf(pos)), 1e-9)
    }

    @Test fun artWirdEinmaligNachbestimmtUndGemerkt() {
        val ohne = Position("ZZNEU.XX", "N", "ZZNEU.XX", 1.0, 1.0, 1L)
        val konto = Account(positions = listOf(ohne, Position("ZZKRYP.XX", "K", "ZZKRYP.XX", 1.0, 1.0, 1L, art = "CRYPTO")))
        // Ohne Katalog und Hinweis bleibt es leer
        assertEquals("", Trading.artNachbestimmen(konto).positions.first().art)
        // Die gespeicherte Art wird fuer Orders und Handelszeiten gemerkt
        assertEquals(AssetType.CRYPTO, Symbols.typeOf("ZZKRYP.XX"))
        // Sobald die Kursantwort die Art nennt, wird sie gespeichert
        Symbols.merkeArt("ZZNEU.XX", AssetType.ETF)
        assertEquals("ETF", Trading.artNachbestimmen(konto).positions.first().art)
    }

    @Test fun uebernahmeNachDemSplitWirdNichtVervielfacht() {
        val tag = 86_400_000L
        val d0 = LocalDate.of(2026, 5, 4).toEpochDay() * tag
        val d1 = d0 + tag
        // 10 Stueck in der App gekauft, nach dem 10:1-Split 5 Stueck (heutige Einheiten) mit altem Kaufdatum uebernommen
        val gekauft = Trading.buy(Account(cash = 100_000.0), "ZZSPLIT", "S", "ZZSPLIT", 10.0, 50.0, now = d0 + 3_600_000L, fee = 0.0).account
        val konto = Trading.importPosition(gekauft, "ZZSPLIT", "S", 5.0, 5.0, d0 + 7_200_000L, now = d1 + 36_000_000L).account
        assertEquals(15.0, konto.positions.single().qty, 1e-9)
        val nach = Splits.anwenden(konto, "ZZSPLIT", "S", Splits.Split(d1, 10.0, 1.0), "sp")
        // 10 alte Stueck mal 10, dazu die 5 neuen
        assertEquals(105.0, nach.positions.single().qty, 1e-9)
    }

    @Test fun uebersprungeneSparplanTermineStehenImVerlauf() {
        val plan = SavingsPlan("p", "SAP.DE", "SAP", 100.0, 2, lastRun = at("2026-01-02", 9), createdAt = at("2026-01-01"))
        val jetzt = at("2026-04-20")
        // Februar, Maerz und April waren faellig: einer wird gekauft, zwei bleiben uebrig
        assertEquals(2, Trading.verpassteTermine(plan, jetzt))
        val q = mapOf("SAP.DE" to Quote("SAP.DE", price = 100.0, prevClose = 100.0, updatedAt = jetzt))
        val acc = Trading.runPlans(Account(cash = 10_000.0, plans = listOf(plan)), q, jetzt)
        val vermerk = acc.transactions.single { it.type == TxType.PLAN }
        assertEquals("PLAN_SKIPPED", vermerk.tag)
        assertEquals(2.0, vermerk.extra, 1e-9)
        // Ein regulaerer Termin bringt keinen Vermerk
        val regulaer = plan.copy(lastRun = at("2026-04-02", 9))
        assertEquals(0, Trading.verpassteTermine(regulaer, at("2026-05-02", 10)))
    }
}
