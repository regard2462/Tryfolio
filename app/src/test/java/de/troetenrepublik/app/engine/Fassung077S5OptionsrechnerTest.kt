package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Optionsanalyse.Kontrakt
import de.troetenrepublik.app.engine.Optionsrechner.Art
import de.troetenrepublik.app.engine.Optionsrechner.Bein
import de.troetenrepublik.app.engine.Optionsrechner.Vorlage
import de.troetenrepublik.app.engine.OptionsrechnerPlan.Erwartung
import de.troetenrepublik.app.engine.OptionsrechnerPlan.Gewinn
import de.troetenrepublik.app.engine.OptionsrechnerPlan.Plan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

// Optionsrechner: Erwartung je Vorlage, freies Verfallsdatum, Gewinnbereich, Kaufplan und Preisnaehe des Scheins.
class Fassung077S5OptionsrechnerTest {
    private val tag = 86_400L
    // 15.01.2027, 12:00 UTC (Winterzeit in Berlin)
    private val tagNull = 20_833L
    private val jetztS = tagNull * tag + 43_200L

    private fun verfallIn(tage: Int) = (tagNull + tage) * tag

    private fun kette(tage: Int, iv: Double): List<Kontrakt> = listOf(90.0, 95.0, 100.0, 105.0, 110.0).flatMap { k ->
        listOf(true, false).map { call -> Kontrakt(call, k, verfallIn(tage), 3.0, 2.9, 3.1, 10, 10, iv) }
    }

    private val ketten = kette(30, 0.50) + kette(60, 0.40) + kette(120, 0.35)

    @Test
    fun jedeVorlageGehoertGenauEinerErwartung() {
        for (v in Vorlage.entries.filter { it != Vorlage.EIGENE }) {
            assertNotNull(v.name, OptionsrechnerPlan.erwartung(v))
            assertEquals(1, Erwartung.entries.count { v in OptionsrechnerPlan.vorlagen(it) })
        }
        assertEquals(null, OptionsrechnerPlan.erwartung(Vorlage.EIGENE))
        for (e in Erwartung.entries) assertTrue(e.name, OptionsrechnerPlan.vorlagen(e).isNotEmpty())
        assertEquals(Vorlage.LONG_CALL, OptionsrechnerPlan.vorlagen(Erwartung.STEIGT).first())
    }

    @Test
    fun varianzWirdZwischenDenTerminenLinearVerbunden() {
        // 45 Tage liegen mitten zwischen 30 und 60 Tagen
        val t1 = 30 * tag / (365.0 * tag); val t2 = 60 * tag / (365.0 * tag); val t = 45 * tag / (365.0 * tag)
        val w = 0.25 * t1 + (0.16 * t2 - 0.25 * t1) * (t - t1) / (t2 - t1)
        val erwartet = sqrt(w / t)
        val iv = OptionsrechnerPlan.ivFrei(ketten, true, 100.0, verfallIn(45), jetztS)!!
        // Die Kontrakte laufen bis Mitternacht, jetzt ist Mittag: Abweichung durch die halbe Tagesstunde bleibt klein
        assertEquals(erwartet, iv, 0.01)
        assertTrue(iv < 0.50 && iv > 0.40)
        // Ausserhalb: naechster Termin
        assertEquals(0.50, OptionsrechnerPlan.ivFrei(ketten, true, 100.0, verfallIn(10), jetztS)!!, 1e-9)
        assertEquals(0.35, OptionsrechnerPlan.ivFrei(ketten, false, 95.0, verfallIn(200), jetztS)!!, 1e-9)
        // Basispreis ohne Kontrakt
        assertEquals(null, OptionsrechnerPlan.ivFrei(ketten, true, 77.0, verfallIn(45), jetztS))
    }

    @Test
    fun freiesDatumNimmtBasispreiseVomNaechstenTermin() {
        // 50 Tage liegen naeher an 60 als an 30; der 60-Tage-Termin hat nur Basispreise 90 bis 110 ohne 100
        val ohne100 = ketten.filter { !(it.verfall == verfallIn(60) && it.strike == 100.0) }
        val frei = OptionsrechnerPlan.frei(100.0, ohne100, verfallIn(50), jetztS)
        assertEquals(8, frei.size)
        assertTrue(frei.none { it.strike == 100.0 })
        assertTrue(frei.all { it.verfall == verfallIn(50) && it.letzter > 0 && it.iv in 0.3..0.55 })
        // Der Preis ist Black-Scholes mit der Volatilitaet des Kontrakts
        val k = frei.first { it.call && it.strike == 105.0 }
        val bs = Pricing.blackScholes(100.0, 105.0, Optionsrechner.tage(verfallIn(50), jetztS) / 365.0, k.iv, true).price
        assertEquals(bs, k.letzter, 1e-9)
        // Ohne Termine nichts
        assertTrue(OptionsrechnerPlan.frei(100.0, emptyList(), verfallIn(50), jetztS).isEmpty())
    }

    @Test
    fun freiesDatumRechnetImRechnerWieSonst() {
        val frei = OptionsrechnerPlan.frei(100.0, ketten, verfallIn(45), jetztS)
        val liste = ketten + frei
        val legs = Optionsrechner.vorlage(Vorlage.LONG_CALL, 100.0, liste, verfallIn(45))!!
        val beine = Optionsrechner.beine(100.0, liste, verfallIn(45), legs, jetztS)!!
        assertEquals(1, beine.size)
        assertTrue(beine[0].preis > 0 && beine[0].iv in 0.40..0.50)
        val e = Optionsrechner.auswerten(beine, 100.0, liste, verfallIn(45), jetztS)
        assertEquals(1, e.breakEven.size)
        assertTrue(e.chance!! in 0.0..1.0)
    }

    @Test
    fun datumsgrenzenBisZweiJahreNachDemLetztenTermin() {
        val (von, bis) = OptionsrechnerPlan.datumsgrenzen(listOf(verfallIn(30), verfallIn(120)), jetztS)
        assertEquals((tagNull + 1) * tag, von)
        assertEquals(verfallIn(120) + 730 * tag, bis)
    }

    private fun bein(art: Art, menge: Int, strike: Double, preis: Double, iv: Double = 0.5) = Bein(art, menge, strike, preis, iv)

    @Test
    fun gewinnbereichJeStrategie() {
        val longCall = listOf(bein(Art.CALL, 1, 30.0, 2.08))
        assertEquals(32.08, (OptionsrechnerPlan.gewinnbereich(longCall) as Gewinn.Ueber).a, 1e-6)
        val longPut = listOf(bein(Art.PUT, 1, 30.0, 2.0))
        assertEquals(28.0, (OptionsrechnerPlan.gewinnbereich(longPut) as Gewinn.Unter).a, 1e-6)
        val bull = listOf(bein(Art.CALL, 1, 30.0, 2.08), bein(Art.CALL, -1, 35.0, 0.5))
        assertEquals(31.58, (OptionsrechnerPlan.gewinnbereich(bull) as Gewinn.Ueber).a, 1e-6)
        val covered = listOf(bein(Art.AKTIE, 1, 0.0, 30.33, 0.0), bein(Art.CALL, -1, 35.0, 2.10))
        assertEquals(28.23, (OptionsrechnerPlan.gewinnbereich(covered) as Gewinn.Ueber).a, 1e-6)
        val straddle = listOf(bein(Art.CALL, 1, 30.0, 2.0), bein(Art.PUT, 1, 30.0, 1.5))
        val aus = OptionsrechnerPlan.gewinnbereich(straddle) as Gewinn.Ausserhalb
        assertEquals(26.5, aus.a, 1e-6); assertEquals(33.5, aus.b, 1e-6)
        val fly = listOf(bein(Art.CALL, 1, 25.0, 6.0), bein(Art.CALL, -2, 30.0, 3.0), bein(Art.CALL, 1, 35.0, 1.0))
        val zw = OptionsrechnerPlan.gewinnbereich(fly) as Gewinn.Zwischen
        assertEquals(26.0, zw.a, 1e-6); assertEquals(34.0, zw.b, 1e-6)
        val condor = listOf(bein(Art.PUT, 1, 20.0, 0.5), bein(Art.PUT, -1, 25.0, 1.5), bein(Art.CALL, -1, 35.0, 1.5), bein(Art.CALL, 1, 40.0, 0.5))
        assertTrue(OptionsrechnerPlan.gewinnbereich(condor) is Gewinn.Zwischen)
    }

    @Test
    fun kaufbarSindNurGekaufteTeile() {
        val jetztMs = jetztS * 1000
        val verfall = verfallIn(30)
        val call = listOf(bein(Art.CALL, 2, 100.0, 5.0))
        val einzeln = OptionsrechnerPlan.plan(call, verfall, jetztMs, true) as Plan.Kaufbar
        assertEquals(200, einzeln.teile.single().stueck)
        assertEquals(verfall, einzeln.teile.single().verfall)
        val straddle = listOf(bein(Art.CALL, 1, 100.0, 5.0), bein(Art.PUT, 1, 100.0, 4.0))
        assertEquals(listOf(100, 100), (OptionsrechnerPlan.plan(straddle, verfall, jetztMs, true) as Plan.Kaufbar).teile.map { it.stueck })
        val mitAktie = listOf(bein(Art.AKTIE, 1, 0.0, 100.0, 0.0), bein(Art.CALL, 1, 100.0, 5.0))
        val teile = (OptionsrechnerPlan.plan(mitAktie, verfall, jetztMs, true) as Plan.Kaufbar).teile
        assertEquals(Art.AKTIE, teile[0].art); assertEquals(100, teile[0].stueck)
        // Verkaufte Optionen fuehrt das Depot als Stillhalterposition (0.77), verkaufte Aktien nicht
        val spread = listOf(bein(Art.CALL, 1, 100.0, 5.0), bein(Art.CALL, -1, 110.0, 2.0))
        assertEquals(listOf(100, -100), (OptionsrechnerPlan.plan(spread, verfall, jetztMs, true) as Plan.Kaufbar).teile.map { it.stueck })
        val covered = listOf(bein(Art.AKTIE, 1, 0.0, 100.0, 0.0), bein(Art.CALL, -1, 110.0, 2.0))
        assertTrue((OptionsrechnerPlan.plan(covered, verfall, jetztMs, true) as Plan.Kaufbar).teile[1].verkauft)
        val leer = listOf(bein(Art.AKTIE, -1, 0.0, 100.0, 0.0))
        assertEquals(Plan.Leerverkauf, OptionsrechnerPlan.plan(leer, verfall, jetztMs, true))
        // Kein Knopf: Verfall vorbei, fehlender Preis, Waehrung passt nicht, keine Teile
        assertEquals(Plan.Keiner, OptionsrechnerPlan.plan(call, verfallIn(-2), jetztMs, true))
        assertEquals(Plan.Keiner, OptionsrechnerPlan.plan(listOf(bein(Art.CALL, 1, 100.0, 0.0)), verfall, jetztMs, true))
        assertEquals(Plan.Keiner, OptionsrechnerPlan.plan(call, verfall, jetztMs, false))
        assertEquals(Plan.Keiner, OptionsrechnerPlan.plan(null, verfall, jetztMs, true))
        assertEquals(Plan.Keiner, OptionsrechnerPlan.plan(emptyList(), verfall, jetztMs, true))
    }

    @Test
    fun optionsscheinTraegtBasispreisVerfallUndVolatilitaet() {
        val teil = OptionsrechnerPlan.Teil(Art.PUT, 100, 95.0, verfallIn(30), 0.43, 3.0)
        val d = OptionsrechnerPlan.optionsschein("SMCI", "Super Micro", "USD", teil, jetztS * 1000)
        assertEquals(de.troetenrepublik.app.data.DerivativeKind.PUT, d.kind)
        assertEquals(95.0, d.strike, 0.0)
        assertEquals(0.43, d.vol, 0.0)
        assertEquals(1.0, d.ratio, 0.0)
        assertEquals("USD", d.ccy)
        assertTrue(d.expiry > verfallIn(30) * 1000 && d.expiry < verfallIn(31) * 1000)
        assertTrue(Derivatives.tradable(d, jetztS * 1000))
    }

    @Test
    fun scheinpreisLiegtNahBeimRechnerpreis() {
        // Rechnerpreis: Black-Scholes mit ganzen Resttagen; Scheinpreis: bis 17:30 Uhr am Verfallstag, in der App
        for ((tage, call, strike) in listOf(Triple(30, true, 100.0), Triple(30, false, 95.0), Triple(60, true, 105.0), Triple(90, false, 100.0))) {
            val iv = 0.40
            val verfall = verfallIn(tage)
            val rechner = Pricing.blackScholes(100.0, strike, Optionsrechner.tage(verfall, jetztS) / 365.0, iv, call).price
            val teil = OptionsrechnerPlan.Teil(if (call) Art.CALL else Art.PUT, 100, strike, verfall, iv, rechner)
            val d = OptionsrechnerPlan.optionsschein("X", "X", "USD", teil, jetztS * 1000)
            val schein = Pricing.price(d, 100.0, jetztS * 1000, 1.0)
            val abw = abs(schein / rechner - 1)
            println("Preisabweichung $tage Tage ${if (call) "Call" else "Put"} $strike: ${"%.2f".format(abw * 100)} %")
            assertTrue("Abweichung $abw", abw < 0.05)
        }
    }

    @Test
    fun scheinStartetZumPreisDesKontraktsAuchBeiAbweichenderVolatilitaet() {
        // Der Kontrakt kostet 6,83, seine gemeldete Volatilitaet ergaebe einen anderen Preis
        for ((call, strike, preis) in listOf(Triple(true, 355.0, 6.83), Triple(false, 340.0, 2.55), Triple(true, 375.0, 1.24))) {
            val teil = OptionsrechnerPlan.Teil(if (call) Art.CALL else Art.PUT, 100, strike, verfallIn(15), 0.60, preis)
            val d = OptionsrechnerPlan.optionsschein("X", "X", "USD", teil, jetztS * 1000, 355.63)
            val schein = Pricing.price(d, 355.63, jetztS * 1000, 1.0)
            assertEquals(preis, schein, 0.01)
        }
        // Ohne Kurs oder mit unmoeglichem Preis bleibt die gemeldete Volatilitaet
        val teil = OptionsrechnerPlan.Teil(Art.CALL, 100, 300.0, verfallIn(15), 0.33, 1.0)
        assertEquals(0.33, OptionsrechnerPlan.optionsschein("X", "X", "USD", teil, jetztS * 1000).vol, 0.0)
        assertEquals(0.33, OptionsrechnerPlan.optionsschein("X", "X", "USD", teil, jetztS * 1000, 355.63).vol, 0.0)
    }
}
