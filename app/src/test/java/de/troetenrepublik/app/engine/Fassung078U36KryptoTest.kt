package de.troetenrepublik.app.engine

import de.troetenrepublik.app.i18n.KryptoHgTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Hintergrund der Kryptowerte (0.78): Leser fuer Finanzierungssatz, offene Kontrakte, Stablecoins und gebundenes
// Kapital. Die Ausschnitte unter resources/krypto haben den Aufbau der Antworten, die Werte sind erfunden.
class Fassung078U36KryptoTest {

    private fun datei(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("krypto/$name")!!.readBytes().toString(Charsets.UTF_8)

    private val tag = 86_400_000L

    @Test
    fun basisAusDemSymbol() {
        assertEquals("BTC", KryptoHintergrund.basis("BTC-EUR"))
        assertEquals("UNI", KryptoHintergrund.basis("UNI7083-EUR"))
        assertEquals("POL", KryptoHintergrund.basis("pol28321-eur"))
        assertNull(KryptoHintergrund.basis("AAPL"))
        assertNull(KryptoHintergrund.basis("BRK-B"))
        assertNull(KryptoHintergrund.basis("^GDAXI"))
    }

    @Test
    fun kontrakteUndAdressen() {
        assertEquals("PF_XBTUSD", KryptoHintergrund.krakenKontrakt("BTC"))
        assertEquals("PF_SOLUSD", KryptoHintergrund.krakenKontrakt("SOL"))
        assertEquals("ETH-PERPETUAL", KryptoHintergrund.deribitKontrakt("ETH"))
        assertEquals("SOL_USDC-PERPETUAL", KryptoHintergrund.deribitKontrakt("SOL"))
        assertTrue(KryptoHintergrund.krakenStandAdresse("BTC").endsWith("/v3/tickers/PF_XBTUSD"))
        assertTrue(KryptoHintergrund.deribitVerlaufAdresse("BTC", 1, 2).endsWith("instrument_name=BTC-PERPETUAL&start_timestamp=1&end_timestamp=2"))
    }

    @Test
    fun krakenStand() {
        val s = KryptoHintergrund.krakenStand(datei("kraken_stand.json"))!!
        assertEquals(0.0000125, s.jeStunde, 1e-12)
        assertEquals(2000.0 * 50000.5, s.offenDollar!!, 1e-3)
        assertEquals(0.01, KryptoHintergrund.satz8h(s.jeStunde), 1e-9)
        assertEquals(10.95, KryptoHintergrund.jahr(s.jeStunde), 1e-9)
        // Kontrakte mit Verfall haben keinen Finanzierungssatz
        assertNull(KryptoHintergrund.krakenStand(datei("kraken_termin.json")))
        assertNull(KryptoHintergrund.krakenStand("{\"result\":\"error\",\"error\":\"contractNotFound\"}"))
        assertNull(KryptoHintergrund.krakenStand("kein json"))
    }

    @Test
    fun krakenVerlaufAlsTagesmittel() {
        val stunden = KryptoHintergrund.krakenVerlauf(datei("kraken_verlauf.json"))
        assertEquals(37, stunden.size)
        val t = KryptoHintergrund.tagesmittel(stunden)
        // Der Wert vom Dezember liegt ausserhalb der sechs Wochen
        assertEquals(2, t.size)
        assertEquals(8.76, t[0].second, 1e-9)
        assertEquals(17.52, t[1].second, 1e-9)
        assertEquals(tag, t[1].first - t[0].first)
        assertTrue(KryptoHintergrund.tagesmittel(emptyList()).isEmpty())
    }

    @Test
    fun deribitStandUndVerlauf() {
        val btc = KryptoHintergrund.deribitStand(datei("deribit_stand.json"), "BTC")!!
        assertEquals(0.00001, btc.jeStunde, 1e-12)
        assertEquals(600_000_000.0, btc.offenDollar!!, 1e-3)
        val sol = KryptoHintergrund.deribitStand(datei("deribit_stand_usdc.json"), "SOL")!!
        assertEquals(-0.000005, sol.jeStunde, 1e-12)
        assertEquals(8_000_000.0, sol.offenDollar!!, 1e-3)
        assertNull(KryptoHintergrund.deribitStand("{\"error\":{\"code\":-32602}}", "BTC"))
        val f = KryptoHintergrund.finanzierung(KryptoHintergrund.DERIBIT, btc, KryptoHintergrund.deribitVerlauf(datei("deribit_verlauf.json")))
        assertEquals(0.008, f.satz8h, 1e-9)
        assertEquals(8.76, f.jahr, 1e-9)
        assertEquals(1, f.verlauf.size)
        assertEquals(17.52, f.verlauf[0].second, 1e-9)
    }

    @Test
    fun stablecoinsUndGebundenesKapital() {
        val s = KryptoHintergrund.stableLesen(datei("stable.json"))
        assertEquals(listOf(120.0, 132.0), s.map { it.second })
        assertEquals(1_770_000_000_000L, s[0].first)
        assertEquals(10.0, KryptoHintergrund.veraenderung(s)!!, 1e-9)
        val t = KryptoHintergrund.tvlLesen(datei("tvl.json"))
        assertEquals(listOf(200.0, 250.0), t.map { it.second })
        assertEquals(25.0, KryptoHintergrund.veraenderung(t)!!, 1e-9)
        // Ohne Wert vor 30 Tagen keine Veraenderung
        assertNull(KryptoHintergrund.veraenderung(t.takeLast(1)))
        assertNull(KryptoHintergrund.veraenderung(t, 60))
        assertTrue(KryptoHintergrund.stableLesen("{}").isEmpty())
        assertTrue(KryptoHintergrund.tvlLesen("kaputt").isEmpty())
        assertEquals(1, KryptoHintergrund.tvlLesen(datei("tvl.json"), tage = 1).size)
    }

    @Test
    fun ketteNurFuerWerteMitEigenerKette() {
        assertEquals("Ethereum", KryptoHintergrund.kette("ETH"))
        assertEquals("BSC", KryptoHintergrund.kette("BNB"))
        assertNull(KryptoHintergrund.kette("LINK"))
        assertNull(KryptoHintergrund.kette(null))
        assertEquals("https://api.llama.fi/v2/historicalChainTvl/Solana", KryptoHintergrund.tvlAdresse("Solana"))
    }

    @Test
    fun texteInBeidenSprachen() {
        assertEquals(KryptoHgTexte.schluessel(true), KryptoHgTexte.schluessel(false))
        for (k in KryptoHgTexte.schluessel(true)) {
            assertTrue(k, KryptoHgTexte.text(true, k).isNotBlank() && KryptoHgTexte.text(false, k).isNotBlank())
        }
        assertEquals("+0,010 % je 8 Std.", KryptoHgTexte.text(true, "fin.satz8h", listOf("+0,010 %")))
    }
}
