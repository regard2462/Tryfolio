package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.i18n.MarktLaenderTexte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Angleichungen zwischen Maerkte, Globus und Laenderblatt: eine Regel fuer gestoerte Engpaesse, eine Schwelle der
// Rezessionswahrscheinlichkeit, eine Faerbung der Rezessionszeichen, ein relatives Volumen, Waehrung gegen die Heimatwaehrung.
class Fassung078U5MaerkteVerweiseTest {
    private val tag = 86_400_000L

    @Test
    fun engpassGiltNurMitRueckgangUndGenugDurchfahrtenAlsGestoert() {
        fun lage(n: Double, vn: Double?) = Engpaesse.Lage(n, 0.0, 0.0, vn, null, null)
        assertTrue(Engpaesse.gestoert(lage(39.0, 50.0)))
        assertFalse(Engpaesse.gestoert(lage(41.0, 50.0)))
        assertFalse(Engpaesse.gestoert(lage(60.0, 50.0)))
        assertFalse(Engpaesse.gestoert(lage(4.0, 9.0)))
        assertFalse(Engpaesse.gestoert(lage(4.0, null)))
    }

    @Test
    fun rezessionswahrscheinlichkeitFaerbtAbDerSchwelle() {
        assertEquals(0, RezessionNyFed.stufe(RezessionNyFed.SCHWELLE))
        assertEquals(1, RezessionNyFed.stufe(RezessionNyFed.SCHWELLE + 0.1))
        assertEquals(1, RezessionNyFed.stufe(39.9))
        assertEquals(2, RezessionNyFed.stufe(40.0))
    }

    @Test
    fun rezessionszeichenOhneDatenUndSahmFarbe() {
        assertNull(WeltRezession.zinskurveAnzeige(emptyList(), null))
        assertNull(WeltRezession.sahmAnzeige(emptyList(), null))
        assertEquals(Fruehwarnung.Ampel.GRUEN, Fruehwarnung.sahmAmpel(0.2))
        assertEquals(Fruehwarnung.Ampel.GELB, Fruehwarnung.sahmAmpel(0.3))
        assertEquals(Fruehwarnung.Ampel.ROT, Fruehwarnung.sahmAmpel(0.5))
    }

    @Test
    fun relativesVolumenRechnetMitZehnTagen() {
        assertEquals(2.5, RelativesVolumen.wert(5e6, 2e6)!!, 1e-9)
        assertNull(RelativesVolumen.wert(5e6, 0.0))
        assertNull(RelativesVolumen.wert(0.0, 2e6))
        assertNull(RelativesVolumen.schnitt(List(9) { 10.0 }))
        // nur die letzten zehn Tage mit Umsatz zaehlen
        assertEquals(20.0, RelativesVolumen.schnitt(List(40) { 100.0 } + List(10) { 20.0 } + 0.0)!!, 1e-9)
        val kerzen = List(40) { Candle(it * tag, 1.0, 1.0, 1.0, 1.0, 100.0) } + List(10) { Candle((40 + it) * tag, 1.0, 1.0, 1.0, 1.0, 20.0) }
        assertEquals(20.0, Alarmarten.normalVolumen(kerzen)!!, 1e-9)
        assertNull(Radar.volumen(249.0, 100.0))
        assertEquals(0.1, Radar.volumen(250.0, 100.0)!!, 1e-9)
    }

    @Test
    fun waehrungGegenHeimatwaehrung() {
        assertEquals("EUR", LaenderMarkt.vergleichsCode("USD", null))
        assertEquals("CHF", LaenderMarkt.vergleichsCode("USD", "CHF"))
        assertNull(LaenderMarkt.vergleichsCode("CHF", "CHF"))
        assertNull(LaenderMarkt.vergleichsCode(null, "CHF"))
        fun reihe(a: Double, b: Double) = listOf(Candle(0L, a, a, a, a), Candle(365 * tag, b, b, b, b))
        // Land 1,0 -> 1,2 je Euro, Heimat 1,0 -> 0,96 je Euro: Kurs 1,0 -> 1,25 je Heimatwaehrung, das Land verliert 20 %
        val kreuz = LaenderMarkt.kreuzkurs(reihe(1.0, 1.2), reihe(1.0, 0.96))
        assertEquals(1.25, kreuz.last().second, 1e-9)
        assertEquals(-20.0, LaenderMarkt.kreuzVeraenderung(kreuz)!!, 1e-9)
        // Land ist der Euro: Kehrwert der Heimatreihe
        assertEquals(1 / 0.96, LaenderMarkt.kreuzkurs(null, reihe(1.0, 0.96)).last().second, 1e-9)
        assertNull(LaenderMarkt.kreuzVeraenderung(kreuz.take(1)))
    }

    @Test
    fun verweisTexteInBeidenSprachen() {
        for (k in listOf("vw.eng.keiner", "vw.vix.rang", "vw.rh.kosten", "vw.zu", "vw.zu.jahr", "vw.kurs", "vw.info.kurs", "vw.info.jahr")) {
            for (de in listOf(true, false)) {
                val t = MarktLaenderTexte.text(de, k, listOf("A", "B", "C"))
                assertTrue(k, t.isNotBlank() && t != k && !t.contains("{"))
            }
        }
        assertTrue(MarktLaenderTexte.text(true, "rh.besser", emptyList()).startsWith("Ohne Kosten"))
    }
}
