package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Optionsanalyse.Kontrakt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// 0.77: Kette ohne offene Kontrakte und ohne Geld und Brief wird aus der Kette der Nasdaq gefuellt; ohne Ersatz
// meldet sie nichts als auffaellig.
class Fassung077KetteTest {

    private val antwort = """{"data":{"totalRecord":2,"lastTrade":"LAST TRADE: $348.51 (AS OF NOV 10, 2026)","table":{"rows":[
        {"expirygroup":"November 13, 2026","expiryDate":null,"c_Last":null,"strike":null,"drillDownURL":null},
        {"expirygroup":"","expiryDate":"Nov 13","c_Last":"5.49","c_Change":"-2.16","c_Bid":"5.40","c_Ask":"5.70","c_Volume":"1,843","c_Openinterest":"6,974",
         "strike":"345.00","p_Last":"2.05","p_Change":"0.10","p_Bid":"2.01","p_Ask":"2.10","p_Volume":"8152","p_Openinterest":"4670",
         "drillDownURL":"/market-activity/stocks/aapl/option-chain/call-put-options/aapl--261113c00345000"},
        {"expirygroup":"","expiryDate":"Nov 13","c_Last":"--","c_Change":"--","c_Bid":"--","c_Ask":"--","c_Volume":"--","c_Openinterest":"--",
         "strike":"347.50","p_Last":"3.00","p_Change":"--","p_Bid":"2.90","p_Ask":"3.10","p_Volume":"12","p_Openinterest":"40",
         "drillDownURL":"/market-activity/stocks/aapl/option-chain/call-put-options/aapl--261113c00347500"}]}},
        "message":null,"status":{"rCode":200,"bCodeMessage":null,"developerMessage":null}}"""

    // 13.11.2026 00:00 UTC
    private val verfall = 1_794_528_000L
    private val jetzt = verfall - 2 * 86_400L - 15 * 3_600L

    @Test
    fun adresseUndLesen() {
        assertEquals(
            "https://api.nasdaq.com/api/quote/AAPL/option-chain?assetclass=stocks&limit=4000&fromdate=2026-11-13&todate=2026-12-18" +
                "&excode=oprac&callput=callput&money=all&type=all",
            NasdaqKette.adresse("AAPL", false, verfall, verfall + 35 * 86_400L),
        )
        val m = NasdaqKette.lesen(antwort)!!
        assertEquals(4, m.size)
        val c = m[NasdaqGriechen.schluessel(verfall, 345.0, true)]!!
        assertEquals(5.49, c.letzter, 1e-9); assertEquals(5.40, c.geld, 1e-9); assertEquals(5.70, c.brief, 1e-9)
        assertEquals(1843L, c.umsatz); assertEquals(6974L, c.offen)
        val p = m[NasdaqGriechen.schluessel(verfall, 345.0, false)]!!
        assertEquals(2.01, p.geld, 1e-9); assertEquals(4670L, p.offen)
        // Striche heissen: nichts genannt
        val leer = m[NasdaqGriechen.schluessel(verfall, 347.5, true)]!!
        assertEquals(0.0, leer.geld, 0.0); assertEquals(0L, leer.offen)
        // Falsche Anlageklasse: lesbar, aber leer; Unlesbares null
        assertEquals(0, NasdaqKette.lesen("""{"data":null,"status":{"rCode":400}}""")!!.size)
        assertNull(NasdaqKette.lesen("<html>"))
    }

    private fun k(call: Boolean, strike: Double, umsatz: Long, offen: Long, geld: Double = 0.0, brief: Double = 0.0, letzter: Double = 5.0, iv: Double = 1e-5) =
        Kontrakt(call, strike, verfall, letzter, geld, brief, umsatz, offen, iv)

    @Test
    fun lueckeErkennen() {
        val leer = listOf(k(true, 345.0, 2000, 0), k(false, 345.0, 8000, 0), k(true, 350.0, 9000, 0), k(false, 340.0, 500, 3))
        assertTrue(Optionsanalyse.ohneOffene(leer))
        assertTrue(NasdaqKette.luecke(leer))
        val voll = listOf(k(true, 345.0, 2000, 900, 5.4, 5.7), k(false, 345.0, 8000, 4000, 2.0, 2.1), k(true, 350.0, 9000, 0, 2.5, 2.7))
        assertFalse(Optionsanalyse.ohneOffene(voll))
        assertFalse(NasdaqKette.luecke(voll))
        // Offene Kontrakte da, Geld und Brief fehlen (Wochenende)
        assertTrue(NasdaqKette.luecke(voll.map { it.copy(geld = 0.0, brief = 0.0) }))
        assertFalse(NasdaqKette.luecke(emptyList()))
    }

    @Test
    fun ohneOffeneNichtsAuffaellig() {
        val leer = listOf(k(true, 345.0, 20_000, 0), k(false, 345.0, 80_000, 0), k(true, 350.0, 90_000, 0))
        assertTrue(Optionsanalyse.auswerten(348.5, leer, jetzt).auffaellig.isEmpty())
        assertNull(AuffaelligeKontrakte.auswerten(348.5, leer, 100, AuffaelligeKontrakte.Art.ALLE, jetzt))
        // Mit offenen Kontrakten zaehlt ein neuer Kontrakt ohne Bestand weiter
        val voll = listOf(k(true, 345.0, 20_000, 9000, 5.4, 5.7), k(false, 345.0, 80_000, 40_000, 2.0, 2.1), k(true, 350.0, 90_000, 0, 2.5, 2.7))
        assertNotNull(AuffaelligeKontrakte.auswerten(348.5, voll, 100, AuffaelligeKontrakte.Art.ALLE, jetzt))
    }

    @Test
    fun ergaenzenUndVolatilitaet() {
        val kette = listOf(k(true, 345.0, 2000, 0), k(false, 345.0, 8000, 0), k(true, 400.0, 10, 0))
        val neu = NasdaqKette.ergaenzen(348.51, kette, NasdaqKette.lesen(antwort)!!, jetzt)
        assertEquals(6974L, neu[0].offen); assertEquals(5.40, neu[0].geld, 1e-9); assertEquals(5.70, neu[0].brief, 1e-9)
        // Umsatz und letzter Kurs der Kette bleiben
        assertEquals(2000L, neu[0].umsatz); assertEquals(5.0, neu[0].letzter, 1e-9)
        assertTrue(neu[0].iv in 0.1..0.8)
        assertTrue(neu[1].iv in 0.1..0.8)
        // Ohne Gegenstueck bleibt der Kontrakt
        assertEquals(0L, neu[2].offen); assertEquals(1e-5, neu[2].iv, 0.0)
        assertFalse(NasdaqKette.luecke(neu))
        // Eigene Rechnung trifft die Volatilitaet, aus der der Preis stammt
        val jahre = (verfall + 21 * 3_600L - jetzt) / (365.0 * 86_400L)
        val preis = Pricing.blackScholes(348.51, 350.0, jahre, 0.32, true).price
        assertEquals(0.32, NasdaqKette.iv(348.51, k(true, 350.0, 1, 1, preis - 0.01, preis + 0.01), jetzt)!!, 0.002)
        // Unter dem inneren Wert und ohne Kurse keine Zahl
        assertNull(NasdaqKette.iv(348.51, k(true, 300.0, 1, 1, 40.0, 41.0), jetzt))
        assertNull(NasdaqKette.iv(348.51, k(true, 350.0, 1, 1), jetzt))
    }
}
