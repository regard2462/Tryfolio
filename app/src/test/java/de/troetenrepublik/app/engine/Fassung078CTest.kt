package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Optionsanalyse.Kontrakt
import de.troetenrepublik.app.engine.Optionsrechner.Art
import de.troetenrepublik.app.engine.Optionsrechner.Bein
import de.troetenrepublik.app.engine.Optionsrechner.Leg
import de.troetenrepublik.app.engine.Optionsrechner.Vorlage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 0.78, Paket q78c: Optionsrechner und erwartete Bewegung je Verfall. Alle Zahlen sind erfunden.
class Fassung078CTest {

    private val jetzt = 1_700_000_000L
    private val v30 = jetzt + 30 * 86_400L
    private val v60 = jetzt + 60 * 86_400L

    private fun kontrakt(call: Boolean, strike: Double, verfall: Long, mitte: Double, iv: Double = 0.3) =
        Kontrakt(call, strike, verfall, mitte, mitte - 0.05, mitte + 0.05, 100, 1000, iv)

    // Kette um den Kurs 100 mit Basispreisen 80 bis 120 in Zweierschritten, Preise nach Black-Scholes.
    private fun kette(verfall: Long, tage: Int): List<Kontrakt> = (40..60).flatMap { i ->
        val k = i * 2.0
        val t = tage / 365.0
        listOf(true, false).map { c -> kontrakt(c, k, verfall, Pricing.blackScholes(100.0, k, t, 0.3, c).price.coerceAtLeast(0.05)) }
    }

    private fun bein(art: Art, menge: Int, strike: Double, preis: Double) = Bein(art, menge, strike, preis, 0.3)

    @Test fun longCallBreakEvenIstBasisPlusPraemie() {
        val b = listOf(bein(Art.CALL, 1, 100.0, 4.0))
        assertEquals(listOf(104.0), Optionsrechner.breakEven(b))
        val (g, v) = Optionsrechner.grenzen(b)
        assertNull(g.wert)
        assertEquals(-400.0, v.wert!!, 1e-9)
    }

    @Test fun bullCallSpreadHatBegrenztenGewinnUndVerlust() {
        val b = listOf(bein(Art.CALL, 1, 100.0, 5.0), bein(Art.CALL, -1, 110.0, 2.0))
        assertEquals(listOf(103.0), Optionsrechner.breakEven(b))
        val (g, v) = Optionsrechner.grenzen(b)
        assertEquals(700.0, g.wert!!, 1e-9)
        assertEquals(-300.0, v.wert!!, 1e-9)
        assertEquals(300.0, Optionsrechner.auswerten(b, 100.0, emptyList(), v30, jetzt).einsatz, 1e-9)
    }

    @Test fun ironCondorHatZweiBreakEvenUndFestenVerlust() {
        val b = listOf(
            bein(Art.PUT, 1, 80.0, 0.5), bein(Art.PUT, -1, 90.0, 1.5),
            bein(Art.CALL, -1, 110.0, 1.5), bein(Art.CALL, 1, 120.0, 0.5),
        )
        assertEquals(listOf(88.0, 112.0), Optionsrechner.breakEven(b))
        val (g, v) = Optionsrechner.grenzen(b)
        assertEquals(200.0, g.wert!!, 1e-9)
        assertEquals(-800.0, v.wert!!, 1e-9)
    }

    @Test fun straddleHatZweiBreakEvenUndUnbegrenztenGewinn() {
        val b = listOf(bein(Art.CALL, 1, 100.0, 3.0), bein(Art.PUT, 1, 100.0, 3.0))
        assertEquals(listOf(94.0, 106.0), Optionsrechner.breakEven(b))
        assertNull(Optionsrechner.grenzen(b).first.wert)
        assertEquals(-600.0, Optionsrechner.grenzen(b).second.wert!!, 1e-9)
    }

    @Test fun coveredCallHatBegrenzteBeine() {
        val b = listOf(bein(Art.AKTIE, 1, 0.0, 100.0), bein(Art.CALL, -1, 105.0, 2.0))
        assertEquals(listOf(98.0), Optionsrechner.breakEven(b))
        val (g, v) = Optionsrechner.grenzen(b)
        assertEquals(700.0, g.wert!!, 1e-9)
        assertEquals(-9800.0, v.wert!!, 1e-9)
    }

    @Test fun nackterCallHatUnbegrenztenVerlust() {
        val b = listOf(bein(Art.CALL, -1, 100.0, 4.0))
        assertNull(Optionsrechner.grenzen(b).second.wert)
        assertEquals(400.0, Optionsrechner.grenzen(b).first.wert!!, 1e-9)
    }

    @Test fun datumKurveLiegtVorVerfallAmZeitwert() {
        val b = listOf(bein(Art.CALL, 1, 100.0, 4.0))
        val verfall = Optionsrechner.guvVerfall(b, 100.0)
        val heute = Optionsrechner.guvDatum(b, 100.0, 30 / 365.0)
        assertEquals(-400.0, verfall, 1e-9)
        assertTrue(heute > verfall)
        // am Verfall deckt sich die Datumskurve mit der Auszahlung
        assertEquals(Optionsrechner.guvVerfall(b, 110.0), Optionsrechner.guvDatum(b, 110.0, 0.0), 1e-9)
    }

    @Test fun chanceStimmtMitDerLognormalverteilung() {
        // Short Put weit aus dem Geld: hohe Gewinnchance, Long Call am Geld: unter der Haelfte
        val sp = listOf(bein(Art.PUT, -1, 80.0, 0.8))
        val lc = listOf(bein(Art.CALL, 1, 100.0, 4.0))
        val c1 = Optionsrechner.chance(sp, 100.0, 30, 0.3)
        val c2 = Optionsrechner.chance(lc, 100.0, 30, 0.3)
        assertTrue(c1 > 0.9)
        assertTrue(c2 < 0.5 && c2 > 0.2)
        // Straddle plus Gegenstueck decken alles ab: Chance auf Gewinn mit Verlustzone zwischen den Break-even
        val st = listOf(bein(Art.CALL, 1, 100.0, 3.0), bein(Art.PUT, 1, 100.0, 3.0))
        val c3 = Optionsrechner.chance(st, 100.0, 30, 0.3)
        assertTrue(c3 in 0.2..0.5)
    }

    @Test fun chanceOhneNullstelleIstEinsOderNull() {
        val gewinn = listOf(bein(Art.PUT, -1, 50.0, 60.0))
        assertTrue(Optionsrechner.breakEven(gewinn).isEmpty())
        assertEquals(1.0, Optionsrechner.chance(gewinn, 100.0, 30, 0.3), 1e-9)
        val verlust = listOf(bein(Art.PUT, 1, 50.0, 60.0))
        assertEquals(0.0, Optionsrechner.chance(verlust, 100.0, 30, 0.3), 1e-9)
    }

    @Test fun bandUmschliesstDenKurs() {
        val (lo, hi) = Optionsrechner.band(100.0, 30, 0.3)
        assertTrue(lo < 100.0 && hi > 100.0)
        // ungefaehr 100 * (1 -+ 0,3 * wurzel(30/365))
        assertEquals(100 * (1 + 0.3 * Math.sqrt(30 / 365.0)), hi, 1.5)
    }

    @Test fun vorlagenLiefernDieRichtigeBeinzahl() {
        val k = kette(v30, 30)
        val erwartet = mapOf(
            Vorlage.LONG_CALL to 1, Vorlage.LONG_PUT to 1, Vorlage.COVERED_CALL to 2, Vorlage.CASH_SECURED_PUT to 1,
            Vorlage.BULL_CALL_SPREAD to 2, Vorlage.BEAR_PUT_SPREAD to 2, Vorlage.STRADDLE to 2, Vorlage.STRANGLE to 2,
            Vorlage.IRON_CONDOR to 4, Vorlage.BUTTERFLY to 3,
        )
        for ((v, n) in erwartet) {
            val legs = Optionsrechner.vorlage(v, 100.0, k, v30)
            assertNotNull(v.name, legs)
            assertEquals(v.name, n, legs!!.size)
            assertNotNull(v.name, Optionsrechner.beine(100.0, k, v30, legs, jetzt))
        }
        assertNull(Optionsrechner.vorlage(Vorlage.EIGENE, 100.0, k, v30))
    }

    @Test fun vorlagenSindImmerAmGeldAngelegt() {
        val k = kette(v30, 30)
        val st = Optionsrechner.vorlage(Vorlage.STRADDLE, 100.0, k, v30)!!
        assertTrue(st.all { it.strike == 100.0 })
        val ic = Optionsrechner.vorlage(Vorlage.IRON_CONDOR, 100.0, k, v30)!!
        val puts = ic.filter { it.art == Art.PUT }
        val calls = ic.filter { it.art == Art.CALL }
        assertTrue(puts.all { it.strike < 100.0 } && calls.all { it.strike > 100.0 })
        // Butterfly: Fluegel gleich weit vom Koerper, Koerper doppelt verkauft
        val bf = Optionsrechner.vorlage(Vorlage.BUTTERFLY, 100.0, k, v30)!!
        assertEquals(-2, bf[1].menge)
        assertEquals(bf[1].strike - bf[0].strike, bf[2].strike - bf[1].strike, 1e-9)
    }

    @Test fun preisIstMitteAusGeldUndBriefSonstLetzter() {
        val mitMarkt = Kontrakt(true, 100.0, v30, 3.0, 4.0, 4.2, 10, 10, 0.3)
        val ohneMarkt = Kontrakt(false, 100.0, v30, 2.5, 0.0, 0.0, 10, 10, 0.3)
        val legs = listOf(Leg(Art.CALL, 1, 100.0), Leg(Art.PUT, 1, 100.0))
        val b = Optionsrechner.beine(100.0, listOf(mitMarkt, ohneMarkt), v30, legs, jetzt)!!
        assertEquals(4.1, b[0].preis, 1e-9)
        assertEquals(2.5, b[1].preis, 1e-9)
    }

    @Test fun fehlenderBasispreisGibtNull() {
        val k = kette(v30, 30)
        assertNull(Optionsrechner.beine(100.0, k, v30, listOf(Leg(Art.CALL, 1, 101.0)), jetzt))
    }

    @Test fun griechenDerGesamtpositionSummieren() {
        val b = listOf(bein(Art.AKTIE, 1, 0.0, 100.0), bein(Art.CALL, -1, 100.0, 4.0))
        val g = Optionsrechner.griechen(b, 100.0, 30 / 365.0)
        // Aktie 100 Delta, Short Call ATM etwa minus 53: gedeckter Call etwa 47
        assertTrue(g.delta in 40.0..55.0)
        assertTrue(g.gamma < 0 && g.vega < 0 && g.theta > 0)
    }

    @Test fun bewegungJeVerfallIstDerStraddle() {
        val k = kette(v30, 30) + kette(v60, 60)
        val l = Optionsrechner.bewegungen(100.0, k, jetzt)
        assertEquals(2, l.size)
        assertEquals(30, l[0].tage)
        assertTrue(l[1].betrag > l[0].betrag)
        assertEquals(l[0].betrag / 100.0 * 100, l[0].prozent, 1e-9)
        assertEquals(100.0 - l[0].betrag, l[0].von, 1e-9)
        // ATM-Straddle bei 30 % Volatilitaet und 30 Tagen etwa 0,8 * 0,3 * wurzel(30/365) * 100
        assertEquals(6.9, l[0].betrag, 1.0)
    }

    @Test fun bewegungUebergehtVerfallOhnePreise() {
        val leer = listOf(Kontrakt(true, 100.0, v30, 0.0, 0.0, 0.0, 0, 0, 0.0), Kontrakt(false, 100.0, v30, 0.0, 0.0, 0.0, 0, 0, 0.0))
        assertTrue(Optionsrechner.bewegungen(100.0, leer, jetzt).isEmpty())
    }

    @Test fun auswertenFasstZusammen() {
        val k = kette(v30, 30)
        val legs = Optionsrechner.vorlage(Vorlage.BULL_CALL_SPREAD, 100.0, k, v30)!!
        val b = Optionsrechner.beine(100.0, k, v30, legs, jetzt)!!
        val e = Optionsrechner.auswerten(b, 100.0, k, v30, jetzt)
        assertEquals(1, e.breakEven.size)
        assertNotNull(e.maxGewinn.wert)
        assertNotNull(e.maxVerlust.wert)
        assertTrue(e.einsatz > 0)
        assertTrue(e.chance!! in 0.2..0.6)
        assertNotNull(e.band)
    }
}
