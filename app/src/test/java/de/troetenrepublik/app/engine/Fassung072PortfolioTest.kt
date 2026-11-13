package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

// [0.72] Risikomaße aus der Monte-Carlo-Verteilung, Sparplan-Rueckblick, Klumpen-Kennzahlen und die Waage aus
// Finder-Zahlen.
class Fassung072PortfolioTest {

    @Test
    fun varEsOhneSchwankungIstSicher() {
        // Ohne Schwankung wachsen alle Verlaefe gleich: Rendite 6 %, Verlustanteile entsprechend negativ (Gewinn)
        val r = PortfolioTools.varEs(10_000.0, 6.0, 0.0, 1, paths = 50)!!
        assertEquals(0.06, r.medianAnteil, 1e-9)
        assertEquals(-r.medianAnteil, r.varAnteil, 1e-9)
        assertEquals(r.varAnteil, r.esAnteil, 1e-9)
    }

    @Test
    fun varEsOrdnungVerlust() {
        // Erwarteter Verlust (ES) ist mindestens so gross wie die Verlustgrenze (VaR), beide ueber dem Median
        val r = PortfolioTools.varEs(10_000.0, 5.0, 18.0, 1, paths = 3000)!!
        assertTrue("ES ${r.esAnteil} >= VaR ${r.varAnteil}", r.esAnteil >= r.varAnteil)
        assertTrue("VaR ${r.varAnteil} >= Median ${r.medianAnteil}", r.varAnteil >= r.medianAnteil)
        // Bei 18 % Schwankung in einem Jahr liegt die 95-%-Grenze ueber 5 % Verlust
        assertTrue(r.varAnteil > 0.05)
        // Der mittlere Verlauf liegt nahe der Rendite
        assertTrue(r.medianAnteil in 0.02..0.09)
    }

    private fun candle(t: Long, close: Double) = Candle(t, close, close, close, close, 0.0)

    private fun ts(y: Int, m: Int, d: Int) = java.time.LocalDate.of(y, m, d).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun klumpenKennzahlen() {
        val gleich = PortfolioTools.concentration(List(10) { 100.0 })!!
        assertEquals(1.0, gleich.top10, 1e-9)
        assertEquals(1000.0, gleich.hhi, 1e-9)
        assertEquals(10.0, gleich.effective, 1e-9)
        val geklumpt = PortfolioTools.concentration(listOf(98.0, 1.0, 1.0))!!
        assertEquals(1.0, geklumpt.top10, 1e-9)
        assertEquals(9606.0, geklumpt.hhi, 1e-6)
        assertTrue(geklumpt.top3 > gleich.top3)
    }

    private fun finderDaten(
        kgv: Double?, kgvErwartet: Double?, dividende: Double?, note: Double?, kursziel: Double?,
        hoch52: Double?, umsatz: Double?, gewinn: Double?, roe: Double?, ausschuettung: Double?, beta: Double?,
        marge: Double? = null, analysten: Int = 12,
    ) = Aktienfinder.Daten(
        symbol = "TEST.DE", name = "Test", kgv = kgv, kgvErwartet = kgvErwartet, dividende = dividende, note = note,
        kursziel = kursziel, boersenwert = 5e9, marge = marge, analysten = analysten, mitDetails = true,
        werte = listOfNotNull(
            hoch52?.let { FinderVorlagen.Feld.HOCH52 to it },
            umsatz?.let { FinderVorlagen.Feld.UMSATZ_QUARTAL to it },
            gewinn?.let { FinderVorlagen.Feld.GEWINN_QUARTAL to it },
            roe?.let { FinderVorlagen.Feld.ROE to it },
            ausschuettung?.let { FinderVorlagen.Feld.AUSSCHUETTUNG to it },
            beta?.let { FinderVorlagen.Feld.BETA to it },
            FinderVorlagen.Feld.KURS to 100.0,
        ).toMap(),
    )

    @Test
    fun waageAusFinderZahlenPositiv() {
        val d = finderDaten(8.0, 8.0, 4.0, 1.8, 30.0, -2.0, 20.0, 25.0, 25.0, 40.0, 1.0, marge = 25.0)
        val w = BullBear.ausFinderDaten(d, 0L)
        assertTrue("Waage erwartet, nicht null", w != null)
        // Nur Punkte dafuer, keine dagegen: voll auf der Bull-Seite
        assertEquals(100, w!!.wert)
    }

    @Test
    fun waageAusFinderZahlenNegativ() {
        val d = finderDaten(80.0, 70.0, 0.5, 4.2, -20.0, -60.0, -10.0, -20.0, 2.0, 120.0, 2.0, marge = -10.0)
        val w = BullBear.ausFinderDaten(d, 0L)
        assertTrue(w != null)
        assertTrue("negativ, war ${w!!.wert}", w.wert < 0)
    }

    @Test
    fun waageRechnetMitNettomargeNichtMitBruttomarge() {
        // Nettomarge negativ (Verlust), Bruttomarge 60 %: Verlust zaehlt dagegen, keine Margenpunkte dafuer
        val d = Aktienfinder.Daten(
            symbol = "V", name = "V", kgv = 10.0, marge = -5.0, mitDetails = true,
            werte = mapOf(FinderVorlagen.Feld.BRUTTOMARGE to 60.0),
        )
        assertEquals(-100, BullBear.ausFinderDaten(d, 0L)!!.wert)
    }

    @Test
    fun waageNurMitGeladenemProfil() {
        val d = Aktienfinder.Daten(symbol = "V", name = "V", kgv = 10.0, marge = 25.0)
        assertNull(BullBear.ausFinderDaten(d, 0L))
        assertEquals(100, BullBear.ausFinderDaten(d.copy(mitDetails = true), 0L)!!.wert)
        // Ohne Profil keine Zahl im Sortierfeld
        assertNull(FinderVorlagen.wert(d, FinderVorlagen.Feld.WAAGE))
    }

    @Test
    fun waageNutztKasseSchuldenUndCashflow() {
        // Mehr Kasse als Schulden und ein freier Cashflow von 4 % des Boersenwerts: beides dafuer
        val d = Aktienfinder.Daten(
            symbol = "K", name = "K", kgv = 15.0, boersenwert = 10e9, kasse = 2e9, schulden = 1e9, freierCashflow = 0.4e9, mitDetails = true,
        )
        assertEquals(100, BullBear.ausFinderDaten(d, 0L)!!.wert)
        // Hohe Nettoschulden: dagegen
        val schwer = d.copy(kasse = 1e9, schulden = 8e9, freierCashflow = -1e9)
        assertTrue(BullBear.ausFinderDaten(schwer, 0L)!!.wert < 0)
    }

    @Test
    fun waageBrauchtEchteAnalystenzahl() {
        // Ein Einzelkursziel (ein Analyst) zaehlt nicht; ab drei Analysten schon
        val d = Aktienfinder.Daten(symbol = "A", name = "A", kgv = 25.0, note = 1.5, kursziel = 40.0, mitDetails = true, analysten = 1)
        assertNull(BullBear.ausFinderDaten(d, 0L))
        assertEquals(100, BullBear.ausFinderDaten(d.copy(analysten = 5), 0L)!!.wert)
    }

    @Test
    fun waageErkenntZyklischeBrancheAusSektor() {
        val d = Aktienfinder.Daten(symbol = "Z", name = "Z", kgv = 10.0, sektor = "Energy", mitDetails = true)
        assertTrue(BullBear.ausFinderDaten(d, 0L)!!.wert < 0)
    }

    @Test
    fun bipAnteilSummiertSichZuEins() {
        val d = Imf.Datensatz(
            werte = mapOf(Imf.BIP to mapOf("USA" to mapOf(2026 to 30_000.0), "DEU" to mapOf(2026 to 5_000.0), "CHN" to mapOf(2026 to 20_000.0), Imf.WELT to mapOf(2026 to 99_999.0))),
        )
        val a = DepotStreuung.bipAnteile(d, 2026)
        assertEquals(1.0, a.values.sum(), 1e-9)
        assertEquals(30_000.0 / 55_000.0, a.getValue("USA"), 1e-9)
        // Ein Land ohne Wert im Jahr fehlt
        assertNull(DepotStreuung.bipAnteile(d, 2025)["USA"])
    }

    @Test
    fun waehrungenLoestEtfNachLaendernAuf() {
        // Ein Welt-ETF in Euro: 70 % USA, 20 % Japan, 10 % Deutschland; dazu ein Euro-Wert
        val welt = mapOf("US" to 0.7, "JP" to 0.2, "DE" to 0.1)
        val w = DepotStreuung.waehrungen(listOf(Triple("ETF", 1000.0, "EUR"), Triple("SAP", 500.0, "EUR"), Triple("AAPL", 300.0, "USD"))) { s -> if (s == "ETF") welt else null }
        assertEquals(1000.0 * 0.7 + 300.0, w.getValue("USD"), 1e-9)
        assertEquals(200.0, w.getValue("JPY"), 1e-9)
        assertEquals(100.0 + 500.0, w.getValue("EUR"), 1e-9)
        // Euro-Land und Waehrung eines Kleinstaats
        assertEquals("EUR", DepotStreuung.waehrungVon("BG"))
        assertEquals("CHF", DepotStreuung.waehrungVon("LI"))
    }

    @Test
    fun waehrungenRestBleibtBeiHandelswaehrung() {
        // Verteilung deckt nur 60 % ab, der Rest bleibt bei der Handelswaehrung
        val w = DepotStreuung.waehrungen(listOf(Triple("ETF", 100.0, "EUR"))) { mapOf("US" to 0.6) }
        assertEquals(60.0, w.getValue("USD"), 1e-9)
        assertEquals(40.0, w.getValue("EUR"), 1e-9)
    }

    @Test
    fun waageOhneZahlenNull() {
        assertNull(BullBear.ausFinderDaten(Aktienfinder.Daten(symbol = "X", name = "X"), 0L))
    }
}
