package de.troetenrepublik.app.engine

import de.troetenrepublik.app.data.Candle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fassung 5.94: Kerzenmuster aus Tageskerzen.
class Fassung594KerzenTest {

    private data class K(val o: Double, val h: Double, val l: Double, val c: Double)

    // Zwoelf ruhige Tage mit Koerper 1, dann sechs Tage Rueckgang (abwaerts = true) oder Anstieg.
    private fun vorlauf(abwaerts: Boolean): MutableList<K> {
        val l = MutableList(12) { K(100.0, 101.5, 98.5, 101.0).let { k -> if (it % 2 == 0) k else K(101.0, 101.5, 98.5, 100.0) } }
        var p = 100.0
        repeat(6) {
            val q = if (abwaerts) p - 1 else p + 1
            l += K(p, maxOf(p, q) + 0.3, minOf(p, q) - 0.3, q)
            p = q
        }
        return l
    }

    private fun muster(l: List<K>): Kerzenmuster.Treffer? =
        Kerzenmuster.erkennen(l.map { it.o }, l.map { it.h }, l.map { it.l }, l.map { it.c }).last()

    @Test fun hammerNachRueckgang() {
        val l = vorlauf(true)
        // Koerper 0,5 oben, unterer Schatten 3, kein oberer
        l += K(94.0, 94.5, 91.0, 94.5)
        assertEquals("hammer", muster(l)?.muster)
        assertEquals(1, muster(l)?.richtung)
    }

    @Test fun gleicheFormNachAnstiegIstHaengenderMann() {
        val l = vorlauf(false)
        l += K(106.0, 106.5, 103.0, 106.5)
        assertEquals("haengender", muster(l)?.muster)
        assertEquals(-1, muster(l)?.richtung)
    }

    @Test fun sternschnuppeNachAnstieg() {
        val l = vorlauf(false)
        l += K(106.5, 109.5, 106.0, 106.0)
        assertEquals("sternschnuppe", muster(l)?.muster)
    }

    @Test fun verschlingen() {
        val l = vorlauf(true)
        // Vortag faellt 95 -> 94 (Teil des Vorlaufs), heute 93,8 -> 95,5
        l += K(93.8, 95.7, 93.6, 95.5)
        assertEquals("verschlingen_auf", muster(l)?.muster)
        val a = vorlauf(false)
        a += K(106.2, 106.4, 104.3, 104.5)
        assertEquals("verschlingen_ab", muster(a)?.muster)
    }

    @Test fun morgenstern() {
        val l = vorlauf(true)
        l += K(94.0, 94.2, 91.8, 92.0)   // langer Abwaertstag
        l += K(91.5, 91.9, 91.0, 91.6)   // kleiner Koerper unter dem Schluss davor
        l += K(91.8, 93.8, 91.7, 93.5)   // schliesst ueber der Mitte (93)
        assertEquals("morgenstern", muster(l)?.muster)
    }

    @Test fun dreiWeisseSoldaten() {
        val l = vorlauf(true)
        l += K(94.0, 95.1, 93.9, 95.0)
        l += K(94.5, 96.1, 94.4, 96.0)
        l += K(95.5, 97.1, 95.4, 97.0)
        assertEquals("soldaten", muster(l)?.muster)
    }

    @Test fun dojiOhneRichtung() {
        val l = vorlauf(true)
        l += K(94.0, 96.0, 92.0, 94.05)
        assertEquals("doji", muster(l)?.muster)
        assertEquals(0, muster(l)?.richtung)
    }

    @Test fun ohneVorlaufKeinUmkehrmuster() {
        // Hammerform nach Seitwaertsphase: kein Hammer
        val l = MutableList(18) { K(100.0, 101.5, 98.5, 101.0) }
        l += K(100.5, 101.0, 97.5, 101.0)
        assertNull(muster(l))
    }

    @Test fun ohneEroeffnungNichts() {
        val l = vorlauf(true)
        l += K(94.0, 94.5, 91.0, 94.5)
        val o = l.map { Double.NaN }
        assertNull(Kerzenmuster.erkennen(o, l.map { it.h }, l.map { it.l }, l.map { it.c }).last())
    }

    @Test fun deutungUndBilanz() {
        val l = vorlauf(true).also { it += K(94.0, 94.5, 91.0, 94.5) }
        val tage = l.indices.map { it.toLong() + 20000 }
        // Zu kurz fuer eine Deutung
        assertNull(Kerzenmuster.deutung(tage, l.map { it.o }, l.map { it.h }, l.map { it.l }, l.map { it.c }))
        // Mit laengerer Vorgeschichte: Hammer am letzten Tag
        val lang = MutableList(20) { K(100.0, 101.5, 98.5, 101.0).let { k -> if (it % 2 == 0) k else K(101.0, 101.5, 98.5, 100.0) } } + l
        val t2 = lang.indices.map { it.toLong() + 20000 }
        val d = Kerzenmuster.deutung(t2, lang.map { it.o }, lang.map { it.h }, lang.map { it.l }, lang.map { it.c })!!
        assertEquals("hammer", d.lage)
        assertEquals(0.0, d.wert, 0.0)
        assertEquals(t2.last(), d.kreuzTag)
        // Bilanz rechnet mit Eroeffnungskursen aus den Kerzen
        val kerzen = (0 until 400).map { i ->
            val c = 100 + 10 * kotlin.math.sin(i / 7.0)
            Candle(i * 86_400_000L, c - 0.5 * kotlin.math.cos(i / 3.0), c + 1, c - 1, c)
        }
        assertTrue(IndikatorTreffer.alle(kerzen, listOf("kerzen")).containsKey("kerzen"))
        val ohne = kerzen.map { it.copy(open = 0.0) }
        assertTrue(!IndikatorTreffer.alle(ohne, listOf("kerzen")).containsKey("kerzen"))
    }
}
