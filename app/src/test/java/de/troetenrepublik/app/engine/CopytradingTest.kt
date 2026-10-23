package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.market.Investoren
import de.troetenrepublik.app.market.Strategien
import de.troetenrepublik.app.ui.CopyRouten
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset

// Copytrading: mitgelieferte 13F-Daten, Musterportfolios und ihre Kennzahlen, Kuerzel fuer Yahoo.
class CopytradingTest {

    // Datei aus assets/, egal ob der Test im Android- oder im Desktop-Modul laeuft.
    private fun asset(name: String): String =
        listOf("src/main/assets/$name", "../app/src/main/assets/$name").map { File(it) }.first { it.exists() }.readText()

    @Test
    fun bundledInvestorsAreComplete() {
        val alle = Investoren.lesen(asset("copytrading/investoren.json"))
        assertEquals(29, alle.size)
        assertEquals(alle.size, alle.map { it.id }.toSet().size)
        for (inv in alle) {
            val q = inv.aktuell!!
            assertTrue(inv.id, q.positionen.isNotEmpty() && q.summe > 0)
            assertTrue(inv.id, inv.quartale.zipWithNext().all { (a, b) -> a.stichtag > b.stichtag })
            assertTrue(inv.id, inv.stilDe.isNotBlank() && inv.stilEn.isNotBlank())
            assertTrue(inv.id, inv.cik.length == 10)
            // Gewichte zum Nachbilden: nur Aktien mit Kuerzel, Summe 1
            val w = Investoren.gewichte(inv, 10)
            assertTrue(inv.id, w.size in 1..10)
            assertEquals(1.0, w.sumOf { it.second }, 1e-9)
        }
        val buffett = alle.first { it.id == "buffett" }
        assertEquals("2026-06-30", buffett.aktuell!!.stichtag)
        assertTrue(buffett.aktuell!!.positionen.any { it.kuerzel == "AAPL" })
        // Vollstaendig erfasste Meldungen: Summe der Positionen = gemeldeter Gesamtwert
        assertEquals(buffett.aktuell!!.summe, buffett.aktuell!!.positionen.sumOf { it.wert }, 1.0)
        // Bridgewater ist nur mit den groessten Positionen erfasst: Anteile beziehen sich auf den Gesamtwert
        val dalio = alle.first { it.id == "dalio" }
        assertTrue(dalio.aktuell!!.positionen.sumOf { it.wert } < dalio.aktuell!!.summe)
    }

    @Test
    fun quarterChangesAreClassified() {
        val p = { c: String, s: Double -> Investoren.Position(c, c, c, s * 10, s) }
        val inv = Investoren.Investor(
            "x", "X", "X", "0000000001", "", "",
            listOf(
                Investoren.Quartal("2026-06-30", "2026-08-14", 1000.0, listOf(p("NEU", 10.0), p("MEHR", 40.0), p("WENIGER", 20.0), p("GLEICH", 30.0))),
                Investoren.Quartal("2026-03-31", "2026-05-15", 1000.0, listOf(p("MEHR", 20.0), p("WENIGER", 50.0), p("GLEICH", 30.0), p("WEG", 5.0))),
            ),
        )
        val z = Investoren.zeilen(inv).associateBy { it.position.cusip }
        assertEquals(Investoren.Wandel.NEU, z.getValue("NEU").wandel)
        assertEquals(Investoren.Wandel.ERHOEHT, z.getValue("MEHR").wandel)
        assertEquals(Investoren.Wandel.VERRINGERT, z.getValue("WENIGER").wandel)
        assertEquals(Investoren.Wandel.GLEICH, z.getValue("GLEICH").wandel)
        assertEquals(Investoren.Wandel.VERKAUFT, z.getValue("WEG").wandel)
        assertEquals(0.4, z.getValue("MEHR").anteil, 1e-9)
        assertEquals(listOf("MEHR", "GLEICH", "WENIGER", "NEU", "WEG"), Investoren.zeilen(inv).map { it.position.cusip })
    }

    @Test
    fun readsNewestFilingFromSecSubmissions() {
        val json = """{"cik":"1067983","filings":{"recent":{"form":["4","13F-HR","13F-HR","13F-NT"],
            "reportDate":["","2026-06-30","2026-03-31","2026-09-30"],"filingDate":["2026-09-01","2026-08-14","2026-05-15","2026-11-14"]}}}"""
        assertEquals("2026-06-30" to "2026-08-14", Investoren.neuesteMeldung(json))
        assertNull(Investoren.neuesteMeldung("kaputt"))
    }

    @Test
    fun strategiesUseCatalogEtfsAndAddUp() {
        val katalog = asset("katalog.json")
        for (s in Strategien.alle) {
            assertEquals(s.id, 1.0, s.anteile.sumOf { it.second }, 1e-9)
            for ((sym, _) in s.anteile) assertTrue("${s.id}: $sym", katalog.contains("\"s\":\"$sym\""))
            assertTrue(s.textDe.isNotBlank() && s.textEn.isNotBlank())
        }
        assertEquals(Strategien.alle.size, Strategien.alle.map { it.id }.toSet().size)
    }

    private fun tag(s: String) = LocalDate.parse(s).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun strategyFiguresFromPrices() {
        // Zwei ETFs ueber zwei Jahre: A steigt gleichmaessig um 10 % im Jahr, B bleibt gleich und faellt einmal um 20 %
        val start = LocalDate.parse("2024-01-01")
        val tage = (0 until 731).map { start.plusDays(it.toLong()) }
        val a = tage.mapIndexed { i, d -> Candle(tag(d.toString()), 0.0, 0.0, 0.0, Math.pow(1.10, i / 365.0)) }
        val b = tage.mapIndexed { i, d -> Candle(tag(d.toString()), 0.0, 0.0, 0.0, if (i in 300..400) 0.8 else 1.0) }
        val nurA = Strategien.kennzahlen(mapOf("A" to a), listOf("A" to 1.0), tag("2026-01-01"))!!
        assertEquals(0.10, nurA.renditePa, 0.002)
        assertEquals(0.0, nurA.maxVerlust, 1e-9)
        val mix = Strategien.kennzahlen(mapOf("A" to a, "B" to b), listOf("A" to 0.5, "B" to 0.5), tag("2026-01-01"))!!
        assertTrue(mix.maxVerlust < -0.08 && mix.maxVerlust > -0.12)
        assertEquals(listOf(2024, 2025), mix.jahre.map { it.first })
        assertTrue(mix.verlauf.size > 100)
        // Fehlt ein ETF, gibt es keine Kennzahlen
        assertNull(Strategien.kennzahlen(mapOf("A" to a), listOf("A" to 0.5, "B" to 0.5), tag("2026-01-01")))
    }

    @Test
    fun tickersBecomeYahooSymbols() {
        assertEquals("BRK-B", CopyRouten.symbol("BRK.B"))
        assertEquals("BF-B", CopyRouten.symbol("BF/B"))
        assertEquals("NESN.SW", CopyRouten.symbol("NESN.SW"))
        assertEquals("SSPG.L", CopyRouten.symbol("SSPG.L"))
        assertEquals("AAPL", CopyRouten.symbol(" AAPL "))
        val schluessel = "Pelosi|Nancy|CA"
        assertEquals(schluessel, CopyRouten.schluessel(CopyRouten.person(schluessel)))
        assertEquals("Apple Inc.", CopyRouten.name(null, "Apple Inc."))
        assertEquals("Warner Bros Discovery INC", CopyRouten.name(null, "WARNER BROS DISCOVERY INC"))
        // Zusaetze zur Papierart fallen weg, die Aktienklasse bleibt
        assertEquals("Alphabet Inc. Class A", CopyRouten.name(null, "Alphabet Inc. - Class A Common Stock"))
        assertEquals("Intel Corporation", CopyRouten.name(null, "Intel Corporation - Common Stock"))
        assertEquals("AllianceBernstein Holding L.P.", CopyRouten.name(null, "AllianceBernstein Holding L.P. Units"))
    }
}
