package de.troetenrepublik.app.engine

import de.troetenrepublik.app.engine.Optionsanalyse.Kontrakt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun <T : Any> assertNotNull(v: T?): T = v ?: throw AssertionError("null")

class Fassung078DTest {
    private val jetzt = 1_800_000_000L
    private val tag = 86_400L

    private fun k(call: Boolean, strike: Double, tage: Int, oi: Long, iv: Double, geld: Double = 1.0, brief: Double = 1.1) =
        Kontrakt(call, strike, jetzt + tage * tag + 3600, 1.05, geld, brief, 10, oi, iv)

    @Test fun ebenenWaendeUndGex() {
        val kontrakte = listOf(
            k(true, 100.0, 30, 500, 0.3), k(true, 105.0, 30, 2000, 0.3), k(true, 110.0, 30, 800, 0.3),
            k(false, 90.0, 30, 3000, 0.3), k(false, 95.0, 30, 400, 0.3), k(false, 100.0, 30, 100, 0.3),
        )
        val e = assertNotNull(OptionsEbenen.berechnen(100.0, kontrakte, jetzt, 100.0))
        assertEquals(105.0, e.callWand!!, 0.0)
        assertEquals(90.0, e.putWand!!, 0.0)
        assertEquals(5, e.strikes.size)
        // Calls zaehlen positiv, Puts negativ
        assertTrue(e.strikes.first { it.strike == 105.0 }.gexNetto > 0)
        assertTrue(e.strikes.first { it.strike == 90.0 }.gexNetto < 0)
        assertEquals(e.kumuliert.last(), e.nettoGex, 1e-9)
        // Beitrag eines Calls: OI x Gamma x Kurs^2 x 0,01
        val g = Pricing.blackScholes(100.0, 105.0, 30 * 86_400.0 / (365.0 * 86_400.0) + 3600 / (365.0 * 86_400.0), 0.3, true).gamma
        assertEquals(2000 * g * 100.0 * 100.0 * 0.01, e.strikes.first { it.strike == 105.0 }.gexCalls, 1e-6)
    }

    @Test fun nullpunktInterpoliert() {
        assertEquals(95.0, OptionsEbenen.nullpunkt(listOf(90.0, 100.0), listOf(-1.0, 1.0))!!, 1e-9)
        assertNull(OptionsEbenen.nullpunkt(listOf(90.0, 100.0), listOf(1.0, 2.0)))
    }

    @Test fun ebenenOhneOffeneKontrakteLeer() {
        assertNull(OptionsEbenen.berechnen(100.0, listOf(k(true, 100.0, 30, 0, 0.3)), jetzt, null))
    }

    @Test fun iv30Interpolation() {
        val s = listOf(OptionsVola.Punkt(15, 0.20), OptionsVola.Punkt(45, 0.30))
        val iv = assertNotNull(OptionsVola.iv30(s))
        val w = (0.04 * 15 + (0.09 * 45 - 0.04 * 15) * 0.5)
        assertEquals(Math.sqrt(w / 30), iv, 1e-9)
        assertEquals(0.25, OptionsVola.iv30(listOf(OptionsVola.Punkt(35, 0.25)))!!, 1e-12)
        assertNull(OptionsVola.iv30(listOf(OptionsVola.Punkt(80, 0.25))))
        assertNull(OptionsVola.iv30(emptyList()))
    }

    @Test fun realisierteVolatilitaet() {
        assertNull(OptionsVola.realisiert(List(30) { 100.0 }))
        // Abwechselnd +1 % und -1 % ergibt etwa 1 % Tagesschwankung
        val kurse = ArrayList<Double>(); var p = 100.0
        repeat(31) { i -> kurse += p; p *= if (i % 2 == 0) 1.01 else 1 / 1.01 }
        val rv = assertNotNull(OptionsVola.realisiert(kurse))
        assertEquals(0.01 * Math.sqrt(252.0), rv, 0.01)
    }

    @Test fun putAufschlagUndStruktur() {
        // Puts mit steigender Vola nach unten: Aufschlag positiv
        val verfall = 30
        val puts = listOf(80.0 to 0.40, 85.0 to 0.36, 90.0 to 0.32, 95.0 to 0.29, 100.0 to 0.27, 105.0 to 0.26)
            .map { (s, iv) -> k(false, s, verfall, 100, iv) }
        val calls = listOf(100.0 to 0.27).map { (s, iv) -> k(true, s, verfall, 100, iv) }
        val v = assertNotNull(OptionsVola.berechnen(100.0, puts + calls, emptyList(), jetzt))
        assertEquals(0.27, v.iv30!!, 1e-9)
        assertTrue(v.putAufschlag!! > 0.0)
        assertEquals(1, v.struktur.size)
        assertNull(v.rv30)
        assertNull(v.verhaeltnis)
    }

    @Test fun ivHistorieRangUndPerzentil() {
        var text: String? = null
        for (i in 0 until 30) text = IvHistorie.merken(text, 20_000 + i, 0.20 + i * 0.01)
        val l = IvHistorie.lesen(text)
        assertEquals(30, l.size)
        val lage = assertNotNull(IvHistorie.lage(l, 0.30))
        assertEquals(30, lage.tage)
        assertEquals(20_000, lage.seit)
        assertEquals(0.10 / 0.29, lage.rang!!, 1e-6)
        assertEquals(10.0 / 30, lage.perzentil!!, 1e-9)
    }

    @Test fun ivHistorieKurzNurSeitUndGrenze() {
        var text: String? = null
        for (i in 0 until 5) text = IvHistorie.merken(text, 20_000 + i, 0.25)
        val lage = assertNotNull(IvHistorie.lage(IvHistorie.lesen(text), 0.25))
        assertNull(lage.rang); assertNull(lage.perzentil); assertEquals(20_000, lage.seit)
        // Derselbe Tag ersetzt den Wert, mehr als 400 Tage fallen vorn weg
        assertEquals(5, IvHistorie.lesen(IvHistorie.merken(text, 20_004, 0.5)).size)
        var voll: String? = null
        for (i in 0 until 450) voll = IvHistorie.merken(voll, 20_000 + i, 0.3)
        val z = IvHistorie.lesen(voll)
        assertEquals(400, z.size)
        assertEquals(20_050, z.first().first)
        assertTrue(IvHistorie.lesen("kaputt 1:x :5 20000:300").size == 1)
        assertNull(IvHistorie.lage(emptyList(), 0.3))
    }

    @Test fun stillhalterWahlNachAbstandUndLaufzeit() {
        val kontrakte = listOf(30, 60).flatMap { t ->
            listOf(
                k(true, 103.0, t, 100, 0.3, 2.0, 2.2), k(true, 105.0, t, 100, 0.3, 1.5, 1.6), k(true, 110.0, t, 100, 0.3, 0.6, 0.7),
                k(false, 90.0, t, 100, 0.3, 0.8, 0.9), k(false, 95.0, t, 100, 0.3, 1.7, 1.8), k(false, 97.0, t, 100, 0.3, 2.5, 2.6),
            )
        }
        val e = StillhalterListe.waehlen(100.0, kontrakte, 5, 30, jetzt, jetzt + 10 * tag)
        val c = assertNotNull(e.call); val p = assertNotNull(e.put)
        assertEquals(105.0, c.strike, 0.0); assertEquals(95.0, p.strike, 0.0)
        assertEquals(1.5, c.praemie, 0.0)
        assertEquals(1.5 / 100.0 * 365 / c.tage, c.renditeJahr, 1e-9)
        assertEquals(1.7 / 95.0 * 365 / p.tage, p.renditeJahr, 1e-9)
        assertEquals(0.1 / 1.55, c.spread!!, 1e-9)
        assertEquals(true, c.zahlenVorVerfall)
        assertTrue(c.ausserhalb!! in 0.5..0.99)
        // Zahlen nach dem Verfall oder unbekannt
        assertEquals(false, StillhalterListe.waehlen(100.0, kontrakte, 5, 30, jetzt, jetzt + 90 * tag).call!!.zahlenVorVerfall)
        assertNull(StillhalterListe.waehlen(100.0, kontrakte, 5, 30, jetzt, null).call!!.zahlenVorVerfall)
        // Laufzeit 60 waehlt den zweiten Verfall; 45 liegt zu weit von beiden... 30 Tage Abstand ist erlaubt bis 15
        assertEquals(60, StillhalterListe.waehlen(100.0, kontrakte, 5, 60, jetzt, null).call!!.tage)
        // Gewuenschter Abstand 15 % findet Basispreise im Bereich 7,5 bis 22,5 % (110 und 90)
        assertEquals(110.0, StillhalterListe.waehlen(100.0, kontrakte, 15, 30, jetzt, null).call!!.strike, 0.0)
        assertEquals(90.0, StillhalterListe.waehlen(100.0, kontrakte, 15, 30, jetzt, null).put!!.strike, 0.0)
        assertNull(StillhalterListe.waehlen(100.0, emptyList(), 5, 30, jetzt, null).call)
    }

    @Test fun ausuebungGemeinsamMitStillhalter() {
        val a = assertNotNull(Optionsanalyse.ausuebung(100.0, 105.0, 0.3, true, 30 / 365.0))
        assertTrue(a in 0.0..0.5)
        val b = assertNotNull(Optionsanalyse.ausuebung(100.0, 105.0, 0.3, false, 30 / 365.0))
        assertEquals(1.0, a + b, 1e-6)
        assertNull(Optionsanalyse.ausuebung(100.0, 105.0, 0.0, true, 0.1))
    }
}
