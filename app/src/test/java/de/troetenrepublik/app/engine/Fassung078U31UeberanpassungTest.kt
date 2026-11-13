package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import de.troetenrepublik.app.engine.StrategieLabor.Kosten
import de.troetenrepublik.app.i18n.De
import de.troetenrepublik.app.i18n.En
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

// Fassung 0.78: Wahrscheinlichkeit der Ueberanpassung, Mindestlaufzeit und SPA-Test im Strategietest, an kleinen
// nachrechenbaren Beispielen.
class Fassung078U31UeberanpassungTest {

    // ------------------------------------------------------------ PBO

    private fun bw(r: DoubleArray, s: Int) = Ueberanpassung.blockwerte(r, s)

    @Test fun pboZweiVariantenVonHand() {
        // Variante a gewinnt im ersten Abschnitt und verliert im zweiten, b umgekehrt: jede Auswahl kippt
        val a = doubleArrayOf(0.01, 0.02, 0.01, 0.02, -0.01, -0.02, -0.01, -0.02)
        val b = doubleArrayOf(0.0, 0.001, 0.0, 0.001, 0.01, 0.02, 0.01, 0.02)
        val p = Ueberanpassung.pbo(listOf(bw(a, 2), bw(b, 2)))!!
        assertEquals(2, p.kombinationen)
        assertEquals(2, p.varianten)
        assertEquals(1.0, p.wahrscheinlichkeit, 0.0)
        // Rang 1 von 2: w = 1/3, Logit ln(1/2)
        for (l in p.logits) assertEquals(-ln(2.0), l, 1e-12)
        // Eine Variante ist in beiden Abschnitten besser: keine Ueberanpassung, Rang 2 von 2, Logit ln 2
        val gut = doubleArrayOf(0.01, 0.02, 0.01, 0.02, 0.01, 0.02, 0.01, 0.02)
        val schwach = doubleArrayOf(0.0, 0.001, 0.0, 0.001, 0.0, 0.001, 0.0, 0.001)
        val q = Ueberanpassung.pbo(listOf(schwach, gut).map { bw(it, 2) })!!
        assertEquals(0.0, q.wahrscheinlichkeit, 0.0)
        for (l in q.logits) assertEquals(ln(2.0), l, 1e-12)
    }

    @Test fun blockwerteUndKombinationen() {
        val r = doubleArrayOf(0.01, 0.02, 0.01, 0.02, 0.03, 0.0, 0.03)
        val w = bw(r, 2)
        // Grenzen bei 7 / 2 = 3
        assertEquals(listOf(3, 4), w.anzahl.toList())
        assertEquals(0.04, w.summe[0], 1e-12)
        assertEquals(0.08, w.summe[1], 1e-12)
        assertEquals(0.0006, w.quadrate[0], 1e-12)
        // C(4, 2) = 6 Kombinationen bei vier Abschnitten
        val x = DoubleArray(40) { (it % 5) * 0.001 }
        val y = DoubleArray(40) { (it % 3) * 0.001 }
        val z = DoubleArray(40) { (it % 7) * 0.001 - 0.002 }
        val p = Ueberanpassung.pbo(listOf(x, y, z).map { bw(it, 4) })!!
        assertEquals(6, p.kombinationen)
        assertEquals(6, p.logits.size)
        assertTrue(p.wahrscheinlichkeit in 0.0..1.0)
        assertEquals(p.logits.count { it <= 0.0 } / 6.0, p.wahrscheinlichkeit, 1e-12)
        // Ungerade Abschnittszahl, eine Variante oder unterschiedliche Abschnitte: kein Ergebnis
        assertNull(Ueberanpassung.pbo(listOf(x, y).map { bw(it, 3) }))
        assertNull(Ueberanpassung.pbo(listOf(bw(x, 4))))
        assertNull(Ueberanpassung.pbo(listOf(bw(x, 4), bw(y, 2))))
    }

    @Test fun abschnittszahl() {
        assertEquals(12, Ueberanpassung.bloecke(1000))
        assertEquals(12, Ueberanpassung.bloecke(252))
        assertEquals(10, Ueberanpassung.bloecke(240))
        assertEquals(4, Ueberanpassung.bloecke(100))
        assertNull(Ueberanpassung.bloecke(80))
        assertEquals(Ueberanpassung.Stufe.GERING, Ueberanpassung.stufe(0.1))
        assertEquals(Ueberanpassung.Stufe.ERHEBLICH, Ueberanpassung.stufe(0.5))
        assertEquals(Ueberanpassung.Stufe.HOCH, Ueberanpassung.stufe(0.51))
    }

    // ------------------------------------------------------------ Mindestlaufzeit

    @Test fun mindestlaufzeitNachFormel() {
        // Normalverteilt, Sharpe 0,1 je Tag: 1 + (1 + 2/4 x 0,01) x (1,6449 / 0,1)^2 = 272,9
        val z = StrategieLabor.normInv(0.95)
        assertEquals(1.0 + 1.005 * (z / 0.1) * (z / 0.1), Ueberanpassung.mindestlaufzeit(0.1, 0.0, 3.0)!!, 1e-9)
        assertEquals(272.91, Ueberanpassung.mindestlaufzeit(0.1, 0.0, 3.0)!!, 0.01)
        // Gegen eine Schwelle sr0
        assertEquals(1.0 + 1.005 * (z / 0.05) * (z / 0.05), Ueberanpassung.mindestlaufzeit(0.1, 0.0, 3.0, sr0 = 0.05)!!, 1e-9)
        // Linksschiefe und dicke Raender verlaengern
        assertTrue(Ueberanpassung.mindestlaufzeit(0.1, -1.0, 3.0)!! > Ueberanpassung.mindestlaufzeit(0.1, 0.0, 3.0)!!)
        assertTrue(Ueberanpassung.mindestlaufzeit(0.1, 0.0, 10.0)!! > Ueberanpassung.mindestlaufzeit(0.1, 0.0, 3.0)!!)
        assertNull(Ueberanpassung.mindestlaufzeit(0.0, 0.0, 3.0))
        assertNull(Ueberanpassung.mindestlaufzeit(-0.1, 0.0, 3.0))
    }

    @Test fun laufzeitEinesErgebnisses() {
        val r = List(504) { if (it % 2 == 0) 0.002 else 0.0 }
        val sharpe = StrategieLabor.sharpe(r)
        val e = StrategieLabor.Ergebnis(
            endwert = 10_000.0, renditeJahr = 0.1, maxRueckgang = 0.0, trades = 1, treffer = 1.0, imMarkt = 1.0,
            verlauf = emptyList(), renditen = r, sharpe = sharpe, jahre = 2.0,
        )
        val l = Ueberanpassung.laufzeit(e)!!
        assertEquals(2.0, l.vorhandenJahre, 0.0)
        val n = Ueberanpassung.mindestlaufzeit(sharpe, StrategieLabor.schiefe(r), StrategieLabor.woelbung(r))!!
        assertEquals(n / 252.0, l.noetigJahre!!, 1e-12)
        assertEquals(sharpe * sqrt(252.0), l.sharpeJahr, 1e-12)
        // Ohne Rendite keine Laufzeit
        assertNull(Ueberanpassung.laufzeit(e.copy(sharpe = -0.1))!!.noetigJahre)
        assertNull(Ueberanpassung.laufzeit(e.copy(renditen = r.take(10))))
    }

    // ------------------------------------------------------------ SPA

    @Test fun ziehungenDeckenJedenTag() {
        val z = Ueberanpassung.ziehungen(5000, 50, 20, 3L)
        for (b in 0 until 50) {
            assertEquals(5000, z.laenge[b].sum())
            assertTrue(z.anfang[b].all { it in 0 until 5000 })
        }
        val mittel = z.laenge.flatMap { it.toList() }.average()
        assertTrue("mittel $mittel", mittel in 17.0..22.0)
        assertTrue(z.anfang[7].contentEquals(Ueberanpassung.ziehungen(5000, 50, 20, 3L).anfang[7]))
        // Mittlere Laenge 1: jeder Tag einzeln
        assertTrue(Ueberanpassung.ziehungen(100, 3, 1, 3L).laenge.all { l -> l.all { it == 1 } })
    }

    @Test fun ziehungsmittelWieEinzelnGezogen() {
        val rng = PortfolioTools.Rng(4L)
        val d = DoubleArray(300) { rng.gaussian() }
        val z = Ueberanpassung.ziehungen(300, 20, 10, 5L)
        val zeile = Ueberanpassung.spaZeile(d, z)
        assertEquals(d.average(), zeile.mittel, 1e-12)
        for (b in 0 until 20) assertEquals(Ueberanpassung.indizes(z, b).map { d[it] }.average(), zeile.ziehung[b], 1e-12)
        // Bloecke laufen im Kreis ueber das Ende hinaus
        assertTrue((0 until 20).any { b -> Ueberanpassung.indizes(z, b).toList().zipWithNext().any { (x, y) -> x == 299 && y == 0 } })
    }

    @Test fun spaStatistikNachFormel() {
        val rng = PortfolioTools.Rng(8L)
        val n = 500
        val z = Ueberanpassung.ziehungen(n, 200, 20, 2L)
        val zeilen = List(3) { k -> Ueberanpassung.spaZeile(DoubleArray(n) { 0.0005 * (k - 1) + 0.01 * rng.gaussian() }, z) }
        val s = Ueberanpassung.spa(zeilen, n)!!
        val wn = sqrt(n.toDouble())
        var t = 0.0
        for (x in zeilen) {
            val omega = sqrt(x.ziehung.sumOf { (wn * (it - x.mittel)) * (wn * (it - x.mittel)) } / x.ziehung.size)
            t = max(t, wn * x.mittel / omega)
        }
        assertEquals(t, s.statistik, 1e-9)
        assertEquals(3, s.varianten)
        assertEquals(200, s.ziehungen)
        assertTrue(s.p in 0.0..1.0)
        // Ohne Streuung kein Test
        assertNull(Ueberanpassung.spa(listOf(Ueberanpassung.spaZeile(DoubleArray(n) { 0.001 }, z)), n))
    }

    @Test fun spaErkenntEchtenVorsprung() {
        val rng = PortfolioTools.Rng(6L)
        val n = 2000
        val z = Ueberanpassung.ziehungen(n, 300, 20, 9L)
        // t-Wert der guten Variante rund 9, die anderen klar schlechter als Halten
        val gut = DoubleArray(n) { 0.002 + 0.01 * rng.gaussian() }
        val schlecht = List(10) { DoubleArray(n) { -0.001 + 0.01 * rng.gaussian() } }
        val s = Ueberanpassung.spa((schlecht + listOf(gut)).map { Ueberanpassung.spaZeile(it, z) }, n)!!
        assertTrue("statistik ${s.statistik}", s.statistik > 5.0)
        assertTrue("p ${s.p}", s.p < 0.01)
        // Alle Varianten schlechter als Halten: Statistik null, p = 1
        val t = Ueberanpassung.spa(schlecht.map { Ueberanpassung.spaZeile(it, z) }, n)!!
        assertEquals(0.0, t.statistik, 0.0)
        assertEquals(1.0, t.p, 0.0)
    }

    // ------------------------------------------------------------ Strategietest

    private fun zufall(seed: Long, n: Int): List<Candle> {
        val rng = PortfolioTools.Rng(seed)
        var c = 100.0
        return List(n) { i ->
            val o = c
            c *= exp(0.0003 + 0.012 * rng.gaussian())
            Candle((10_959L + (i / 5) * 7L + i % 5) * 86_400_000L, o, maxOf(o, c) * 1.003, minOf(o, c) * 0.997, c)
        }
    }

    @Test fun pruefungImStrategietest() {
        val k = zufall(1L, 1600)
        val p = Ueberanpassung.pruefen(k, 300, Kosten(10.0), StrategieLabor.Familie.DURCHSCHNITT)!!
        val varianten = StrategieLabor.varianten(StrategieLabor.Familie.DURCHSCHNITT).size
        val pbo = p.pbo!!
        assertEquals(varianten, pbo.varianten)
        assertEquals(12, pbo.bloecke)
        assertEquals(924, pbo.kombinationen)
        val spa = p.spa!!
        assertEquals(varianten, spa.varianten)
        assertEquals(Ueberanpassung.ZIEHUNGEN, spa.ziehungen)
        // Wiederholbar
        val nochmal = Ueberanpassung.pruefen(k, 300, Kosten(10.0), StrategieLabor.Familie.DURCHSCHNITT)!!
        assertEquals(pbo.wahrscheinlichkeit, nochmal.pbo!!.wahrscheinlichkeit, 0.0)
        assertEquals(spa.p, nochmal.spa!!.p, 0.0)
        assertNull(Ueberanpassung.pruefen(k, k.size - 50, Kosten(10.0), StrategieLabor.Familie.DURCHSCHNITT))
    }

    @Test fun rechenzeitAllerVarianten() {
        val k = zufall(2L, 10_080)
        val t = System.nanoTime()
        assertNotNull(Ueberanpassung.pruefen(k, 0, Kosten(10.0), StrategieLabor.Familie.ALLE))
        val ms = (System.nanoTime() - t) / 1_000_000
        println("Ueberanpassung 10080 Tage, alle Varianten: $ms ms")
        assertTrue("$ms ms", ms < 60_000)
    }

    @Test fun texteDerPruefungen() {
        for (s in listOf(De, En)) for (k in listOf("pbo.zeile", "pbo.text", "spa.ja", "spa.nein", "spa.text", "mt.satz", "mt.text", "zp.spanne.r")) assertTrue(k, s.st(k).isNotBlank())
        for (st in Ueberanpassung.Stufe.entries) for (s in listOf(De, En)) assertTrue(s.st("pbo." + st.name).isNotBlank())
        assertEquals("Für diese Sharpe-Quote bräuchte es 14,2 Jahre Daten, vorhanden sind 10,0", De.st("mt.satz", listOf("14,2", "10,0")))
        assertEquals("Die beste von 580 Varianten ist nach dem Test nicht von Zufall zu unterscheiden (p = 0,43)", De.st("spa.nein", listOf("580", "= 0,43")))
    }
}
