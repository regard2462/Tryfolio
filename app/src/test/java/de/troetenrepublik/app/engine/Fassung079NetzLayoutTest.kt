package de.troetenrepublik.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

// Anordnung der Knoten: keine Ueberlappung (Name nie verdeckt), fester Zufallskeim (gleiche Eingabe, gleiches Bild), bekannte
// Plaetze bleiben beim Erweitern nahezu stehen.
class Fassung079NetzLayoutTest {

    private fun kaesten(n: Int) = (0 until n).map { NetzLayout.Kasten("k$it", 90f + (it * 37 % 70), 28f + (it * 13 % 30)) }

    private fun stern(n: Int) = (1 until n).map { "k0" to "k$it" }

    private fun ketteMitQuerkanten(n: Int) = (1 until n).map { "k${it - 1}" to "k$it" } + (2 until n step 3).map { "k0" to "k$it" }

    private fun keinerUeberlappt(k: List<NetzLayout.Kasten>, p: Map<String, NetzLayout.Punkt>, rand: Float = 0f): Boolean {
        for (i in k.indices) for (j in i + 1 until k.size) {
            val a = k[i]; val b = k[j]; val pa = p.getValue(a.id); val pb = p.getValue(b.id)
            val ox = (a.breite + b.breite) / 2f + rand - abs(pa.x - pb.x)
            val oy = (a.hoehe + b.hoehe) / 2f + rand - abs(pa.y - pb.y)
            if (ox > 0f && oy > 0f) return false
        }
        return true
    }

    @Test fun kleineGraphenOhneUeberlappung() {
        for (n in listOf(1, 2, 3, 5, 8, 12, 16)) {
            val k = kaesten(n)
            val p = NetzLayout.berechnen(k, stern(n), mitte = "k0")
            assertEquals(n, p.size)
            assertTrue("Stern $n", keinerUeberlappt(k, p))
            val q = NetzLayout.berechnen(k, ketteMitQuerkanten(n))
            assertTrue("Kette $n", keinerUeberlappt(k, q))
        }
    }

    @Test fun mindestluckeWirdEingehalten() {
        val k = kaesten(10)
        val p = NetzLayout.berechnen(k, stern(10), mitte = "k0", abstand = 16f)
        assertTrue(keinerUeberlappt(k, p, 15f))
    }

    @Test fun ohneKantenAuchKeineUeberlappung() {
        val k = kaesten(14)
        assertTrue(keinerUeberlappt(k, NetzLayout.berechnen(k, emptyList())))
    }

    @Test fun mitteBleibtAmUrsprung() {
        val k = kaesten(6)
        val p = NetzLayout.berechnen(k, stern(6), mitte = "k0")
        assertEquals(0f, p.getValue("k0").x, 1e-3f)
        assertEquals(0f, p.getValue("k0").y, 1e-3f)
    }

    @Test fun gleicheEingabeGleichesBild() {
        val k = kaesten(9)
        val a = NetzLayout.berechnen(k, ketteMitQuerkanten(9), mitte = "k0")
        val b = NetzLayout.berechnen(k, ketteMitQuerkanten(9), mitte = "k0")
        for (id in a.keys) { assertEquals(a.getValue(id).x, b.getValue(id).x, 0f); assertEquals(a.getValue(id).y, b.getValue(id).y, 0f) }
        // Anderer Keim, anderes Bild (die Zahlen sind nicht an eine Plattform gebunden)
        val c = NetzLayout.berechnen(k, ketteMitQuerkanten(9), mitte = "k0", keim = 99)
        assertTrue(a.keys.any { abs(a.getValue(it).x - c.getValue(it).x) > 0.5f || abs(a.getValue(it).y - c.getValue(it).y) > 0.5f })
    }

    @Test fun beimErweiternBleibenBekannteKnotenNahe() {
        val k = kaesten(6)
        val vorher = NetzLayout.berechnen(k, stern(6), mitte = "k0")
        val mehr = kaesten(10)
        val nachher = NetzLayout.berechnen(mehr, stern(10), fest = vorher, mitte = "k0")
        assertTrue(keinerUeberlappt(mehr, nachher))
        var summe = 0.0
        for (i in 0 until 6) {
            val a = vorher.getValue("k$i"); val b = nachher.getValue("k$i")
            summe += sqrt(((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y)).toDouble())
        }
        // Im Mittel nur um wenige Kastenmasse verschoben
        assertTrue("mittlere Verschiebung ${summe / 6}", summe / 6 < 400.0)
    }

    @Test fun grosserGraphBleibtFreiVonUeberlappung() {
        val k = kaesten(40)
        val p = NetzLayout.berechnen(k, ketteMitQuerkanten(40), mitte = "k0")
        assertTrue(keinerUeberlappt(k, p))
    }

    // Kante zwischen zwei aeusseren Knoten laeuft nicht quer durch den Kasten in der Mitte
    @Test fun kanteZwischenAeusserenKnotenLaeuftNichtDurchDieMitte() {
        for (n in listOf(4, 5, 7)) {
            val k = kaesten(n)
            val kanten = stern(n) + listOf("k1" to "k2")
            val p = NetzLayout.berechnen(k, kanten, mitte = "k0")
            val a = p.getValue("k1"); val b = p.getValue("k2"); val m = p.getValue("k0")
            for (s in 0..100) {
                val t = s / 100f
                val px = a.x + (b.x - a.x) * t; val py = a.y + (b.y - a.y) * t
                val innen = abs(px - m.x) < k[0].breite / 2f && abs(py - m.y) < k[0].hoehe / 2f
                assertFalse("n=$n t=$t", innen)
            }
        }
    }

    @Test fun leerUndEinzeln() {
        assertTrue(NetzLayout.berechnen(emptyList(), emptyList()).isEmpty())
        val p = NetzLayout.berechnen(listOf(NetzLayout.Kasten("a", 50f, 20f)), emptyList(), mitte = "a")
        assertEquals(1, p.size)
        assertFalse(NetzLayout.ueberlappen(NetzLayout.Kasten("a", 50f, 20f), NetzLayout.Punkt(0f, 0f), NetzLayout.Kasten("b", 50f, 20f), NetzLayout.Punkt(60f, 0f)))
        assertTrue(NetzLayout.ueberlappen(NetzLayout.Kasten("a", 50f, 20f), NetzLayout.Punkt(0f, 0f), NetzLayout.Kasten("b", 50f, 20f), NetzLayout.Punkt(40f, 10f)))
    }

    @Test fun bundelGraphNvidiaAusschnittOhneUeberlappung() {
        val g = NetzTestHilfe.buendel.graph
        val a = NetzAusschnitt.start(g, "NVDA", NetzAusschnitt.ALLE, NetzTestHilfe.HEUTE)
        val ids = a.sichtbar.toList()
        val k = ids.map { NetzLayout.Kasten(it, 60f + (g.knoten.getValue(it).name.length * 7), 30f) }
        val pairs = g.kanten.filter { it.von in a.sichtbar && it.nach in a.sichtbar }.map { it.von to it.nach }
        val p = NetzLayout.berechnen(k, pairs, mitte = "NVDA")
        assertTrue(keinerUeberlappt(k, p))
    }
}
