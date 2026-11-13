package de.troetenrepublik.app.engine

import kotlin.math.abs
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class Fassung078HTest {
    @Test fun beitragIstAnteilMalAenderung() {
        val e = Beitraege.Eintrag("A", "A", 0.05, -2.0)
        assertEquals(-0.1, e.beitrag, 1e-9)
    }

    @Test fun gewichteWerdenNormiert() {
        val n = Beitraege.normiert(listOf(2.0, 6.0, 0.0, -1.0))
        assertEquals(0.25, n[0], 1e-9)
        assertEquals(0.75, n[1], 1e-9)
        assertEquals(0.0, n[2], 1e-9)
        assertEquals(0.0, n[3], 1e-9)
    }

    @Test fun groessteJeRichtung() {
        val l = listOf(
            Beitraege.Eintrag("A", "A", 0.1, 1.0), Beitraege.Eintrag("B", "B", 0.1, 3.0), Beitraege.Eintrag("C", "C", 0.1, -2.0),
            Beitraege.Eintrag("D", "D", 0.1, -0.1), Beitraege.Eintrag("E", "E", 0.1, 0.0),
        )
        val g = Beitraege.groesste(l, 1)
        assertEquals(listOf("B", "C"), g.map { it.symbol })
    }

    @Test fun indexSchluessel() {
        assertEquals("DAX", Beitraege.indexSchluessel("^GDAXI"))
        assertNull(Beitraege.indexSchluessel("^XYZ"))
    }

    @Test fun risikoKonstanterKursHatKeineSchwankung() {
        val r = Risikokennzahlen.berechne(List(60) { 100.0 })!!
        assertEquals(0.0, r.volatilitaet, 1e-9)
        assertEquals(0.0, r.maxRueckgang, 1e-9)
    }

    @Test fun risikoRueckgangUndSchwankung() {
        val k = List(40) { 100.0 } + List(10) { 100.0 - it * 2.0 } + List(10) { 82.0 + it }
        val r = Risikokennzahlen.berechne(k)!!
        assertEquals(-18.0, r.maxRueckgang, 1e-9)
        assertTrue(r.volatilitaet > 0)
        assertNull(Risikokennzahlen.berechne(List(10) { 1.0 }))
    }

    @Test fun kursspanneLage() {
        val l = KurszielLage.lage(100.0, 80.0, 140.0, 110.0)!!
        assertTrue(l.tiefPos < l.kursPos && l.kursPos < l.medianPos!! && l.medianPos!! < l.hochPos)
        val unter = KurszielLage.lage(50.0, 80.0, 140.0, null)!!
        assertTrue(unter.kursPos < unter.tiefPos)
        assertNull(KurszielLage.lage(0.0, 80.0, 140.0, null))
        assertNull(KurszielLage.lage(100.0, 150.0, 140.0, null))
    }

    @Test fun stufenInLetztenTagen() {
        val jetzt = 100L * 86_400_000L
        val u = listOf(
            Analystenbilanz.Urteil("a", jetzt - 5 * 86_400_000L, "Buy", "Hold", "up"),
            Analystenbilanz.Urteil("b", jetzt - 50 * 86_400_000L, "Sell", "Hold", "down"),
            Analystenbilanz.Urteil("c", jetzt - 200 * 86_400_000L, "Buy", "Hold", "up"),
            Analystenbilanz.Urteil("d", jetzt - 10 * 86_400_000L, "Hold", "Hold", "main"),
        )
        assertEquals(1 to 1, KurszielLage.stufen(u, jetzt, 90))
    }

    @Test fun abweichungMitGleichemZusatz() {
        val d = Abweichung.diff("3.4%", "3.2%")!!
        assertEquals(0.2, d.betrag, 1e-9)
        assertEquals("%", d.zusatz)
        assertEquals(1, d.nachkommastellen)
        val m = Abweichung.diff("$1.5B", "$1.8B")!!
        assertEquals(-0.3, m.betrag, 1e-9)
        assertEquals("B", m.zusatz)
        assertEquals(0.0, Abweichung.diff("2.0%", "2.0%")!!.betrag, 1e-12)
        assertEquals(-5.0, Abweichung.diff("245K", "250K")!!.betrag, 1e-9)
        assertEquals(1200.0 - 1000.0, Abweichung.diff("1,200", "1,000")!!.betrag, 1e-9)
    }

    @Test fun abweichungOhneVergleichbaresIstNull() {
        assertNull(Abweichung.diff("3.4%", "250K"))
        assertNull(Abweichung.diff(null, "1"))
        assertNull(Abweichung.diff("n/a", "1"))
    }

    @Test fun abweichungNegativeZahlen() {
        assertEquals(-0.3, Abweichung.diff("-0.5%", "-0.2%")!!.betrag, 1e-9)
        assertTrue(abs(Abweichung.diff("0.30%", "0.3%")!!.betrag) < 1e-9)
    }

    @Test fun watchBreite() {
        val b = WatchBreite.berechne(listOf(1.0, -2.0, 0.0, null, 3.0))
        assertEquals(2, b.steigen)
        assertEquals(1, b.fallen)
        assertEquals(1, b.gleich)
        assertEquals(0.5, b.mittel!!, 1e-9)
        assertNull(WatchBreite.berechne(listOf(null)).mittel)
    }

    @Test fun paare() {
        assertEquals(listOf("a" to "b", "c" to "d", "a" to "c"), Paare.bilden(listOf("a", "b", "c", "d", "e")))
        assertEquals(listOf("a" to "b"), Paare.bilden(listOf("a", "b")))
        assertTrue(Paare.bilden(listOf("a")).isEmpty())
    }

    @Test fun fuehrungOhneAufsicht() {
        assertTrue(InsiderFuehrung.aktiv(InsiderFuehrung.ROLLEN))
        assertTrue(!InsiderFuehrung.aktiv(emptySet()))
        assertTrue(InsiderTrend.Rolle.AUFSICHT !in InsiderFuehrung.ROLLEN)
    }

    @Test fun kennzahlvergleichRisikoOhneHervorhebung() {
        val w = Kennzahlvergleich.Werte(beta = 1.2, volatilitaet = 30.0, maxRueckgang = -20.0)
        assertEquals(1.2, w.wert(Kennzahlvergleich.Zeile.BETA)!!, 1e-9)
        assertTrue(!Kennzahlvergleich.Zeile.BETA.hervorheben)
        assertTrue(Kennzahlvergleich.Zeile.KGV.hervorheben)
    }
}
